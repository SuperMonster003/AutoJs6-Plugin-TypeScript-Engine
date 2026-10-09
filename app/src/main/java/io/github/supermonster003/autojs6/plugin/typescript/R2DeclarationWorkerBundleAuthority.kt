package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Native verification of the declaration preload bundles before the Worker may import them
 * (startup-latency L2-7).
 *
 * The build generates `declarations-common.js`, `declarations-rhino.js` and
 * `declarations-node.js` plus `bundles.json` from the verified declaration distribution. At
 * runtime this authority ties `bundles.json` to the byte-verified trusted declaration inventory
 * (standard-library, layer and profile fingerprints, file counts and byte totals) and then
 * verifies each bundle's packaged bytes against the manifest digest on every request the asset
 * origin serves. The Worker therefore no longer re-encodes and re-hashes every record; it still
 * checks the record shapes and proves the inventory fingerprint before using a layer. A manifest
 * or bundle that does not verify is never served, and the first project then fails closed on its
 * inventory fingerprint exactly as before.
 */
internal object R2DeclarationWorkerBundleAuthority {

    const val ASSET_ROOT = "r2-declaration-worker-bundles"
    const val MANIFEST_ASSET = "$ASSET_ROOT/bundles.json"
    const val GLOBAL_REGISTRY = "__autojs6DeclarationLayers"
    const val SCHEMA_VERSION = 1

    val bundleFileNames: Map<R2DeclarationLayer, String> = linkedMapOf(
        R2DeclarationLayer.COMMON to "declarations-common.js",
        R2DeclarationLayer.RHINO to "declarations-rhino.js",
        R2DeclarationLayer.NODE to "declarations-node.js",
    )

    internal data class Bundle(
        val layer: R2DeclarationLayer,
        val file: String,
        val fileCount: Int,
        val layerFingerprint: String,
        val profileFingerprint: String?,
        val sha256: String,
        val size: Long,
        val totalBytes: Long,
        val totalTokens: Long,
    )

    internal class Manifest(
        bundles: Map<R2DeclarationLayer, Bundle>,
        val declarationDistributionFingerprint: String,
        val standardLibrariesFingerprint: String,
    ) {
        val bundles: Map<R2DeclarationLayer, Bundle> = LinkedHashMap(bundles)

        fun bundleForFile(fileName: String): Bundle? =
            bundles.values.firstOrNull { bundle -> bundle.file == fileName }
    }

    /**
     * Returns the verified bytes of one bundle asset. Throws when the manifest cannot be tied
     * to the trusted declarations or the packaged bytes do not match it; the caller answers the
     * Worker's request with a forbidden response in that case. Bytes are never cached: every
     * request re-reads and re-hashes the packaged asset (about 5 MB, tens of milliseconds on a
     * WebView I/O thread) so the Worker provably receives verified content.
     */
    fun openVerifiedBundle(context: Context, fileName: String): ByteArray {
        cacheContext = context.applicationContext ?: context
        val manifest = manifestCache.get(Unit)
        val bundle = manifest.bundleForFile(fileName)
            ?: throw IllegalArgumentException("Unknown declaration bundle: $fileName")
        val bytes = context.assets.open("$ASSET_ROOT/$fileName").use { input ->
            readBounded(input, bundle.size)
        }
        return verifyBundleBytes(bundle, bytes)
    }

    internal fun verifyBundleBytes(bundle: Bundle, bytes: ByteArray): ByteArray {
        check(bytes.size.toLong() == bundle.size) {
            "Declaration bundle ${bundle.layer.manifestValue} size does not match its manifest."
        }
        check(R2Digests.equal(bundle.sha256, R2Digests.sha256(bytes))) {
            "Declaration bundle ${bundle.layer.manifestValue} SHA-256 does not match its manifest."
        }
        return bytes
    }

    /**
     * Parses `bundles.json` strictly and ties every bundle to the trusted declaration inventory
     * the native host already verified byte by byte.
     */
    internal fun parseManifest(
        manifestBytes: ByteArray,
        common: R2TrustedDeclarations,
        rhino: R2TrustedDeclarations,
        node: R2TrustedDeclarations,
    ): Manifest {
        val root = R2StrictJson.parseObject(manifestBytes)
        root.requireExactKeys(MANIFEST_KEYS, "Declaration bundle manifest")
        check(root.requireInt("schemaVersion") == SCHEMA_VERSION) {
            "Declaration bundle manifest schema is not $SCHEMA_VERSION."
        }
        check(root.requireString("globalRegistry") == GLOBAL_REGISTRY) {
            "Declaration bundle manifest registry drifted."
        }
        val declarationDistributionFingerprint =
            root.requireLowerSha256("declarationDistributionFingerprint")
        val standardLibrariesFingerprint = root.requireLowerSha256("standardLibrariesFingerprint")
        check(R2Digests.equal(standardLibrariesFingerprint, common.fingerprint)) {
            "Declaration bundle manifest standard-library fingerprint does not match the " +
                "trusted declarations."
        }
        check(rhino.profile == R2DeclarationProfile.RHINO && node.profile == R2DeclarationProfile.NODE) {
            "Declaration bundle manifest must be tied to the Rhino and Node profiles."
        }
        val bundlesObject = root.requireObject("bundles")
        bundlesObject.requireExactKeys(
            bundleFileNames.keys.map(R2DeclarationLayer::manifestValue).toSet(),
            "Declaration bundle manifest bundles",
        )
        val bundles = LinkedHashMap<R2DeclarationLayer, Bundle>()
        bundleFileNames.forEach { (layer, expectedFile) ->
            val value = bundlesObject.requireObject(layer.manifestValue)
            value.requireExactKeys(BUNDLE_KEYS, "Declaration bundle ${layer.manifestValue}")
            val trusted = when (layer) {
                R2DeclarationLayer.COMMON -> common
                R2DeclarationLayer.RHINO -> rhino
                R2DeclarationLayer.NODE -> node
            }
            val layerFiles = trusted.files.filter { file ->
                when (layer) {
                    R2DeclarationLayer.COMMON -> !file.archivePath.removePrefix(ARCHIVE_ROOT).contains('/')
                    else -> file.archivePath.startsWith("$ARCHIVE_ROOT${layer.manifestValue}/")
                }
            }
            val expectedLayerFingerprint = requireNotNull(trusted.layerFingerprints[layer.manifestValue]) {
                "Trusted declarations do not carry the ${layer.manifestValue} layer fingerprint."
            }
            val expectedProfileFingerprint =
                if (layer == R2DeclarationLayer.COMMON) null else trusted.profileFingerprint
            val bundle = Bundle(
                layer = layer,
                file = value.requireString("file"),
                fileCount = value.requireInt("fileCount"),
                layerFingerprint = value.requireLowerSha256("layerFingerprint"),
                profileFingerprint = value["profileFingerprint"]?.let {
                    value.requireLowerSha256("profileFingerprint")
                },
                sha256 = value.requireLowerSha256("sha256"),
                size = value.requireLong("size"),
                totalBytes = value.requireLong("totalBytes"),
                totalTokens = value.requireLong("totalTokens"),
            )
            check(bundle.file == expectedFile) {
                "Declaration bundle ${layer.manifestValue} file name drifted."
            }
            check(
                bundle.fileCount == layerFiles.size &&
                    bundle.fileCount > 0 &&
                    bundle.totalBytes == layerFiles.sumOf(R2TrustedDeclarationFile::size),
            ) {
                "Declaration bundle ${layer.manifestValue} inventory does not match the trusted " +
                    "declarations."
            }
            check(bundle.size > bundle.totalBytes && bundle.size <= MAXIMUM_BUNDLE_BYTES) {
                "Declaration bundle ${layer.manifestValue} size is outside its envelope."
            }
            check(R2Digests.equal(bundle.layerFingerprint, expectedLayerFingerprint)) {
                "Declaration bundle ${layer.manifestValue} layer fingerprint does not match the " +
                    "trusted declarations."
            }
            check(
                if (expectedProfileFingerprint == null) {
                    bundle.profileFingerprint == null
                } else {
                    bundle.profileFingerprint != null &&
                        R2Digests.equal(bundle.profileFingerprint, expectedProfileFingerprint)
                },
            ) {
                "Declaration bundle ${layer.manifestValue} profile fingerprint does not match " +
                    "the trusted declarations."
            }
            bundles[layer] = bundle
        }
        return Manifest(
            bundles = bundles,
            declarationDistributionFingerprint = declarationDistributionFingerprint,
            standardLibrariesFingerprint = standardLibrariesFingerprint,
        )
    }

    /** Test-only reset so JVM tests can observe a fresh manifest load. */
    internal fun clearForTesting() = manifestCache.clearForTesting()

    private val manifestCache = R2ProcessLoadCache<Unit, Manifest> {
        loadManifestUncached(requireNotNull(cacheContext))
    }

    @Volatile
    private var cacheContext: Context? = null

    private fun loadManifestUncached(context: Context): Manifest {
        val bytes = context.assets.open(MANIFEST_ASSET).use { input ->
            readBounded(input, MAXIMUM_MANIFEST_BYTES)
        }
        return parseManifest(
            manifestBytes = bytes,
            common = R2TrustedDeclarations.loadTypeScriptStandardLibraries(context),
            rhino = R2TrustedDeclarations.loadForProfile(context, R2ProjectProfile.PROVISIONAL_RHINO),
            node = R2TrustedDeclarations.loadForProfile(context, R2ProjectProfile.PROVISIONAL_NODE_NEXT),
        )
    }

    private fun readBounded(input: InputStream, maximumBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            check(total <= maximumBytes) { "Declaration bundle asset exceeds its envelope." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun Map<String, Any?>.requireExactKeys(expected: Set<String>, label: String) {
        check(keys == expected) { "$label has unexpected keys: ${keys.sorted()}" }
    }

    private fun Map<String, Any?>.requireObject(key: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return this[key] as? Map<String, Any?> ?: error("Declaration bundle manifest $key is not an object.")
    }

    private fun Map<String, Any?>.requireString(key: String): String =
        this[key] as? String ?: error("Declaration bundle manifest $key is not a string.")

    private fun Map<String, Any?>.requireLong(key: String): Long {
        val token = this[key] as? R2StrictJson.NumberToken
            ?: error("Declaration bundle manifest $key is not a number.")
        check(NON_NEGATIVE_INTEGER_PATTERN.matches(token.source)) {
            "Declaration bundle manifest $key is not a canonical non-negative integer."
        }
        return token.source.toLongOrNull()
            ?: error("Declaration bundle manifest $key exceeds the integer range.")
    }

    private fun Map<String, Any?>.requireInt(key: String): Int {
        val value = requireLong(key)
        check(value <= Int.MAX_VALUE) { "Declaration bundle manifest $key exceeds the integer range." }
        return value.toInt()
    }

    private fun Map<String, Any?>.requireLowerSha256(key: String): String =
        R2Digests.requireLowerSha256(requireString(key), "Declaration bundle manifest $key")

    private const val ARCHIVE_ROOT = "declarations/"
    private const val MAXIMUM_MANIFEST_BYTES = 64L * 1024L
    private const val MAXIMUM_BUNDLE_BYTES = 16L * 1024L * 1024L
    private val NON_NEGATIVE_INTEGER_PATTERN = Regex("0|[1-9][0-9]*")
    private val MANIFEST_KEYS = setOf(
        "bundles",
        "declarationDistributionFingerprint",
        "globalRegistry",
        "schemaVersion",
        "standardLibrariesFingerprint",
    )
    private val BUNDLE_KEYS = setOf(
        "file",
        "fileCount",
        "layerFingerprint",
        "profileFingerprint",
        "sha256",
        "size",
        "totalBytes",
        "totalTokens",
    )
}
