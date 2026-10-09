package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class R2DeclarationWorkerBundleAuthorityTest {

    private val common = trustedCommon()
    private val rhino = trustedProfile(R2DeclarationProfile.RHINO)
    private val node = trustedProfile(R2DeclarationProfile.NODE)
    // A real bundle always embeds every record text plus its registry wrapper, so the stubs
    // are padded past the trusted byte totals that the manifest envelope check requires.
    private val commonBundle = stubBundle("common")

    @Test
    fun tiesTheManifestToTheTrustedDeclarationsAndVerifiesBundleBytes() {
        val manifest = R2DeclarationWorkerBundleAuthority.parseManifest(
            manifestBytes = manifestJson().toByteArray(StandardCharsets.UTF_8),
            common = common,
            rhino = rhino,
            node = node,
        )

        assertEquals(common.fingerprint, manifest.standardLibrariesFingerprint)
        assertEquals(
            listOf(R2DeclarationLayer.COMMON, R2DeclarationLayer.RHINO, R2DeclarationLayer.NODE),
            manifest.bundles.keys.toList(),
        )
        val bundle = requireNotNull(manifest.bundleForFile("declarations-common.js"))
        assertEquals(R2DeclarationLayer.COMMON, bundle.layer)
        assertEquals(1, bundle.fileCount)
        assertNull(bundle.profileFingerprint)
        assertEquals(7L, bundle.totalTokens)
        assertArrayEquals(
            commonBundle,
            R2DeclarationWorkerBundleAuthority.verifyBundleBytes(bundle, commonBundle),
        )
        assertEquals(
            rhino.profileFingerprint,
            requireNotNull(manifest.bundles[R2DeclarationLayer.RHINO]).profileFingerprint,
        )
        assertNull(manifest.bundleForFile("declarations-extra.js"))
    }

    @Test
    fun refusesBundleBytesThatDoNotMatchTheManifest() {
        val manifest = R2DeclarationWorkerBundleAuthority.parseManifest(
            manifestJson().toByteArray(StandardCharsets.UTF_8),
            common,
            rhino,
            node,
        )
        val bundle = requireNotNull(manifest.bundles[R2DeclarationLayer.COMMON])
        assertFails("Shorter bundle bytes were accepted.") {
            R2DeclarationWorkerBundleAuthority.verifyBundleBytes(bundle, commonBundle.copyOf(5))
        }
        val flipped = commonBundle.copyOf().also { bytes -> bytes[0] = (bytes[0] + 1).toByte() }
        assertFails("Bundle bytes with a different digest were accepted.") {
            R2DeclarationWorkerBundleAuthority.verifyBundleBytes(bundle, flipped)
        }
    }

    @Test
    fun refusesManifestsThatDriftFromTheTrustedDeclarations() {
        val valid = manifestJson()
        val otherDigest = R2Digests.sha256("other".toByteArray(StandardCharsets.UTF_8))
        val invalid = listOf(
            "layer fingerprint" to valid.replace(
                requireNotNull(rhino.layerFingerprints["rhino"]),
                otherDigest,
            ),
            "profile fingerprint" to valid.replace(rhino.profileFingerprint, otherDigest),
            "standard-library fingerprint" to valid.replace(common.fingerprint, otherDigest),
            "file count" to valid.replace("\"fileCount\": 1,\n      \"layerFingerprint\": \"${common.fingerprint}\"", "\"fileCount\": 2,\n      \"layerFingerprint\": \"${common.fingerprint}\""),
            "unknown key" to valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1,\n  \"extra\": true"),
            "schema" to valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
            "registry" to valid.replace("__autojs6DeclarationLayers", "__other"),
            "common profile fingerprint" to valid.replace("\"profileFingerprint\": null", "\"profileFingerprint\": \"$otherDigest\""),
            "fractional size" to valid.replace("\"size\": ${commonBundle.size}", "\"size\": ${commonBundle.size}.0"),
        )
        invalid.forEach { (label, json) ->
            assertFails("Manifest with a drifted $label was accepted.") {
                R2DeclarationWorkerBundleAuthority.parseManifest(
                    json.toByteArray(StandardCharsets.UTF_8),
                    common,
                    rhino,
                    node,
                )
            }
        }
    }

    private fun manifestJson(): String {
        val rhinoBundle = stubBundle("rhino")
        val nodeBundle = stubBundle("node")
        fun bundle(
            layer: String,
            file: String,
            bytes: ByteArray,
            trusted: R2TrustedDeclarations,
            layerFingerprint: String,
            profileFingerprint: String?,
        ): String {
            val files = trusted.files.filter { candidate ->
                if (layer == "common") {
                    !candidate.archivePath.removePrefix("declarations/").contains('/')
                } else {
                    candidate.archivePath.startsWith("declarations/$layer/")
                }
            }
            return "    \"$layer\": {\n" +
                "      \"file\": \"$file\",\n" +
                "      \"fileCount\": ${files.size},\n" +
                "      \"layerFingerprint\": \"$layerFingerprint\",\n" +
                "      \"profileFingerprint\": ${profileFingerprint?.let { "\"$it\"" } ?: "null"},\n" +
                "      \"sha256\": \"${R2Digests.sha256(bytes)}\",\n" +
                "      \"size\": ${bytes.size},\n" +
                "      \"totalBytes\": ${files.sumOf(R2TrustedDeclarationFile::size)},\n" +
                "      \"totalTokens\": 7\n" +
                "    }"
        }
        return "{\n  \"bundles\": {\n" +
            bundle("common", "declarations-common.js", commonBundle, common, common.fingerprint, null) + ",\n" +
            bundle("rhino", "declarations-rhino.js", rhinoBundle, rhino, requireNotNull(rhino.layerFingerprints["rhino"]), rhino.profileFingerprint) + ",\n" +
            bundle("node", "declarations-node.js", nodeBundle, node, requireNotNull(node.layerFingerprints["node"]), node.profileFingerprint) + "\n" +
            "  },\n" +
            "  \"declarationDistributionFingerprint\": \"${R2Digests.sha256("distribution".toByteArray())}\",\n" +
            "  \"globalRegistry\": \"__autojs6DeclarationLayers\",\n" +
            "  \"schemaVersion\": 1,\n" +
            "  \"standardLibrariesFingerprint\": \"${common.fingerprint}\"\n" +
            "}\n"
    }

    private fun stubBundle(layer: String): ByteArray =
        ("registry[\"$layer\"] = {}; // " + "x".repeat(128) + "\n")
            .toByteArray(StandardCharsets.UTF_8)

    private fun trustedCommon(): R2TrustedDeclarations {
        val text = "declare const es5: unique symbol;\n"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val file = R2TrustedDeclarationFile(
            archivePath = "declarations/lib.es5.d.ts",
            fingerprintPath = "lib/lib.es5.d.ts",
            size = bytes.size.toLong(),
            sha256 = R2Digests.sha256(bytes),
            text = text,
        )
        return R2TrustedDeclarations.create(
            fingerprint = R2TrustedDeclarations.fingerprint(listOf(file)),
            files = listOf(file),
        )
    }

    private fun trustedProfile(profile: R2DeclarationProfile): R2TrustedDeclarations {
        val engineText = "declare const engine: unique symbol;\n"
        val engineBytes = engineText.toByteArray(StandardCharsets.UTF_8)
        val engineEntryPoint = "layers/${profile.manifestValue}/index.d.ts"
        val engineFile = R2TrustedDeclarationFile(
            archivePath = "declarations/${profile.manifestValue}/index.d.ts",
            fingerprintPath = engineEntryPoint,
            size = engineBytes.size.toLong(),
            sha256 = R2Digests.sha256(engineBytes),
            text = engineText,
        )
        val engineFingerprint = R2TrustedDeclarations.layerFingerprint(
            profile.engineLayer,
            listOf(engineFile),
        )
        val profileFingerprint = R2TrustedDeclarations.calculateProfileFingerprint(
            profile = profile,
            engineEntryPoint = engineEntryPoint,
            layerFingerprints = linkedMapOf(
                R2DeclarationLayer.COMMON.manifestValue to common.fingerprint,
                profile.engineLayer.manifestValue to engineFingerprint,
            ),
        )
        return R2TrustedDeclarations.createProfile(
            profile = profile,
            commonFingerprint = common.fingerprint,
            commonFiles = common.files,
            engineFingerprint = engineFingerprint,
            engineFiles = listOf(engineFile),
            engineEntryPoint = engineEntryPoint,
            profileFingerprint = profileFingerprint,
        )
    }

    private fun assertFails(message: String, block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            return
        }
        fail(message)
    }
}
