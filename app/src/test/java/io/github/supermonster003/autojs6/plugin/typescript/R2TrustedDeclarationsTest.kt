package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2TrustedDeclarationsTest {

    @Test
    fun validatesActualUtf8TextSizeHashAndAggregateFingerprint() {
        val nonAscii = declaration(
            archivePath = "declarations/lib.non-ascii.d.ts",
            fingerprintPath = "lib/lib.non-ascii.d.ts",
            text = "declare const café: string;\n",
        )
        assertTrue(nonAscii.size > nonAscii.text.length)

        val inventory = R2TrustedDeclarations.create(
            fingerprint = R2TrustedDeclarations.fingerprint(listOf(nonAscii)),
            files = listOf(nonAscii),
        )
        assertEquals(nonAscii.size, inventory.files.single().size)
        assertEquals(nonAscii.sha256, inventory.files.single().sha256)

        assertThrows(IllegalArgumentException::class.java) {
            R2TrustedDeclarations.create(
                fingerprint = ZERO_SHA256,
                files = listOf(nonAscii.copy(size = nonAscii.text.length.toLong())),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2TrustedDeclarations.create(
                fingerprint = ZERO_SHA256,
                files = listOf(nonAscii.copy(sha256 = ZERO_SHA256)),
            )
        }
    }

    @Test
    fun aggregateFingerprintIsStableSortedAndBindsPathsSizesAndHashes() {
        val alpha = alphaDeclaration()
        val beta = betaDeclaration()

        assertEquals(
            EXPECTED_AGGREGATE_FINGERPRINT,
            R2TrustedDeclarations.fingerprint(listOf(alpha, beta)),
        )
        assertEquals(
            EXPECTED_AGGREGATE_FINGERPRINT,
            R2TrustedDeclarations.fingerprint(listOf(beta, alpha)),
        )

        val inventory = R2TrustedDeclarations.create(
            fingerprint = EXPECTED_AGGREGATE_FINGERPRINT,
            files = listOf(beta, alpha),
        )
        assertEquals(EXPECTED_AGGREGATE_FINGERPRINT, inventory.fingerprint)
        assertEquals(
            listOf(beta.archivePath, alpha.archivePath),
            inventory.files.map(R2TrustedDeclarationFile::archivePath),
        )

        assertThrows(IllegalArgumentException::class.java) {
            R2TrustedDeclarations.create(
                fingerprint = ZERO_SHA256,
                files = listOf(alpha, beta),
            )
        }
    }

    @Test
    fun rejectsDuplicateAndAsciiCaseFoldCollidingPaths() {
        val alpha = alphaDeclaration()
        val beta = betaDeclaration()

        assertRejected(
            listOf(
                alpha,
                beta.copy(archivePath = alpha.archivePath),
            ),
        )
        assertRejected(
            listOf(
                alpha,
                beta.copy(archivePath = "declarations/LIB.ALPHA.D.TS"),
            ),
        )
        assertRejected(
            listOf(
                alpha,
                beta.copy(fingerprintPath = alpha.fingerprintPath),
            ),
        )
        assertRejected(
            listOf(
                alpha,
                beta.copy(fingerprintPath = "LIB/LIB.ALPHA.D.TS"),
            ),
        )
    }

    @Test
    fun rejectsNonCanonicalOrUnreservedDeclarationPaths() {
        val base = alphaDeclaration()
        val overlyDeep =
            "declarations/" +
                List(64) { "directory" }.joinToString("/") +
                "/alpha.d.ts"
        val overlyLong =
            "declarations/" + "a".repeat(4_096) + ".d.ts"
        val invalidArchivePaths = listOf(
            "",
            "/declarations/alpha.d.ts",
            "declarations\\alpha.d.ts",
            "declarations//alpha.d.ts",
            "declarations/./alpha.d.ts",
            "declarations/../alpha.d.ts",
            "other/alpha.d.ts",
            "declarations/alpha.ts",
            "declarations/café.d.ts",
            overlyDeep,
            overlyLong,
        )
        invalidArchivePaths.forEach { path ->
            assertInvalidFile(base.copy(archivePath = path))
        }

        val invalidFingerprintPaths = listOf(
            "",
            "/lib/lib.alpha.d.ts",
            "lib\\lib.alpha.d.ts",
            "lib//lib.alpha.d.ts",
            "lib/./lib.alpha.d.ts",
            "lib/../lib.alpha.d.ts",
            "lib/café.d.ts",
            "C:/lib.alpha.d.ts",
            List(65) { "directory" }.joinToString("/") + "/alpha.d.ts",
            "a".repeat(4_097),
        )
        invalidFingerprintPaths.forEach { path ->
            assertInvalidFile(base.copy(fingerprintPath = path))
        }
    }

    @Test
    fun rejectsFingerprintPathsThatDoNotDetermineTheInjectedVfsPath() {
        val base = alphaDeclaration()

        assertInvalidFile(base.copy(archivePath = "declarations/remapped.d.ts"))
        assertInvalidFile(base.copy(fingerprintPath = "other/lib.alpha.d.ts"))
    }

    @Test
    fun rejectsTrustedTextWithAnUnpairedSurrogate() {
        val malformedText = "\uD800"
        val replacementBytes = malformedText.toByteArray(StandardCharsets.UTF_8)
        val malformed = R2TrustedDeclarationFile(
            archivePath = "declarations/lib.malformed.d.ts",
            fingerprintPath = "lib/lib.malformed.d.ts",
            size = replacementBytes.size.toLong(),
            sha256 = R2Digests.sha256(replacementBytes),
            text = malformedText,
        )

        assertInvalidFile(malformed)
    }

    @Test
    fun defensivelyCopiesInputsAndExposesUnmodifiableViews() {
        val original = alphaDeclaration()
        val input = mutableListOf(original)
        val fingerprint = R2TrustedDeclarations.fingerprint(input)
        val inventory = R2TrustedDeclarations.create(fingerprint, input)

        input.clear()

        assertEquals(1, inventory.files.size)
        assertEquals(original, inventory.files.single())
        assertNotSame(original, inventory.files.single())
        assertSame(
            inventory.files.single(),
            inventory.filesByArchivePath[original.archivePath],
        )

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (inventory.files as MutableList<R2TrustedDeclarationFile>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (inventory.filesByArchivePath as MutableMap<String, R2TrustedDeclarationFile>)
                .clear()
        }
    }

    @Test
    fun rejectsEmptyInventoryAndNonLowercaseFingerprint() {
        assertThrows(IllegalArgumentException::class.java) {
            R2TrustedDeclarations.create(
                fingerprint = ZERO_SHA256,
                files = emptyList(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2TrustedDeclarations.create(
                fingerprint = EXPECTED_AGGREGATE_FINGERPRINT.uppercase(),
                files = listOf(alphaDeclaration(), betaDeclaration()),
            )
        }
    }

    @Test
    fun profileCompositionKeepsCommonCompatibilityIdentityAndSelectsOnlyItsEngineLayer() {
        val common = listOf(alphaDeclaration())
        val rhino = listOf(engineDeclaration(R2DeclarationLayer.RHINO, "index.d.ts"))
        val commonFingerprint = R2TrustedDeclarations.fingerprint(common)
        val rhinoFingerprint = R2TrustedDeclarations.layerFingerprint(
            R2DeclarationLayer.RHINO,
            rhino,
        )
        val layerFingerprints = linkedMapOf(
            R2DeclarationLayer.COMMON.manifestValue to commonFingerprint,
            R2DeclarationLayer.RHINO.manifestValue to rhinoFingerprint,
        )
        val profileFingerprint = R2TrustedDeclarations.calculateProfileFingerprint(
            R2DeclarationProfile.RHINO,
            rhino.single().fingerprintPath,
            layerFingerprints,
        )

        val declarations = R2TrustedDeclarations.createProfile(
            profile = R2DeclarationProfile.RHINO,
            commonFingerprint = commonFingerprint,
            commonFiles = common,
            engineFingerprint = rhinoFingerprint,
            engineFiles = rhino,
            engineEntryPoint = rhino.single().fingerprintPath,
            profileFingerprint = profileFingerprint,
        )

        assertEquals(commonFingerprint, declarations.fingerprint)
        assertEquals(R2DeclarationProfile.RHINO, declarations.profile)
        assertEquals(profileFingerprint, declarations.profileFingerprint)
        assertEquals(layerFingerprints, declarations.layerFingerprints)
        assertEquals(
            listOf("/declarations/rhino/index.d.ts"),
            declarations.compilerRootNames,
        )
        assertTrue(
            declarations.files.any { file ->
                file.archivePath == "declarations/rhino/index.d.ts"
            },
        )
        assertFalse(
            declarations.files.any { file ->
                file.archivePath.startsWith("declarations/node/")
            },
        )
    }

    @Test
    fun rhinoAndNodeProfileFingerprintsAreDomainSeparatedAndDoNotCrossSelectFiles() {
        val common = listOf(alphaDeclaration())
        val commonFingerprint = R2TrustedDeclarations.fingerprint(common)
        val rhino = listOf(engineDeclaration(R2DeclarationLayer.RHINO, "index.d.ts"))
        val node = listOf(engineDeclaration(R2DeclarationLayer.NODE, "index.d.ts"))
        val rhinoDeclarations = profileDeclarations(
            R2DeclarationProfile.RHINO,
            common,
            commonFingerprint,
            rhino,
        )
        val nodeDeclarations = profileDeclarations(
            R2DeclarationProfile.NODE,
            common,
            commonFingerprint,
            node,
        )

        assertFalse(
            R2Digests.equal(
                rhinoDeclarations.profileFingerprint,
                nodeDeclarations.profileFingerprint,
            ),
        )
        assertFalse(
            rhinoDeclarations.files.any { it.archivePath.startsWith("declarations/node/") },
        )
        assertFalse(
            nodeDeclarations.files.any { it.archivePath.startsWith("declarations/rhino/") },
        )
    }

    @Test
    fun profileCompositionRejectsWrongLayerPathCollisionTamperAndEntryPoint() {
        val common = listOf(alphaDeclaration())
        val commonFingerprint = R2TrustedDeclarations.fingerprint(common)
        val rhino = engineDeclaration(R2DeclarationLayer.RHINO, "index.d.ts")

        assertThrows(IllegalArgumentException::class.java) {
            profileDeclarations(
                R2DeclarationProfile.RHINO,
                common,
                commonFingerprint,
                listOf(
                    rhino,
                    rhino.copy(
                        archivePath = "declarations/rhino/INDEX.D.TS",
                        fingerprintPath = "layers/rhino/INDEX.D.TS",
                    ),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            val wrongLayer = rhino.copy(
                archivePath = "declarations/node/index.d.ts",
                fingerprintPath = "layers/node/index.d.ts",
            )
            val fingerprint = R2TrustedDeclarations.layerFingerprint(
                R2DeclarationLayer.RHINO,
                listOf(wrongLayer),
            )
            R2TrustedDeclarations.createProfile(
                profile = R2DeclarationProfile.RHINO,
                commonFingerprint = commonFingerprint,
                commonFiles = common,
                engineFingerprint = fingerprint,
                engineFiles = listOf(wrongLayer),
                engineEntryPoint = wrongLayer.fingerprintPath,
                profileFingerprint = ZERO_SHA256,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            profileDeclarations(
                R2DeclarationProfile.RHINO,
                common,
                commonFingerprint,
                listOf(rhino.copy(sha256 = ZERO_SHA256)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            val engineFingerprint = R2TrustedDeclarations.layerFingerprint(
                R2DeclarationLayer.RHINO,
                listOf(rhino),
            )
            R2TrustedDeclarations.createProfile(
                profile = R2DeclarationProfile.RHINO,
                commonFingerprint = commonFingerprint,
                commonFiles = common,
                engineFingerprint = engineFingerprint,
                engineFiles = listOf(rhino),
                engineEntryPoint = "layers/rhino/missing.d.ts",
                profileFingerprint = ZERO_SHA256,
            )
        }
    }

    @Test
    fun profileFingerprintChangesWithEntryPointAndEngineInventory() {
        val first = engineDeclaration(R2DeclarationLayer.RHINO, "index.d.ts")
        val second = engineDeclaration(
            R2DeclarationLayer.RHINO,
            "alternate.d.ts",
            "declare const alternate: true;\n",
        )
        val files = listOf(first, second)
        val engineFingerprint = R2TrustedDeclarations.layerFingerprint(
            R2DeclarationLayer.RHINO,
            files,
        )
        val commonFingerprint = R2TrustedDeclarations.fingerprint(listOf(alphaDeclaration()))
        val layers = linkedMapOf(
            R2DeclarationLayer.COMMON.manifestValue to commonFingerprint,
            R2DeclarationLayer.RHINO.manifestValue to engineFingerprint,
        )
        val firstEntryFingerprint = R2TrustedDeclarations.calculateProfileFingerprint(
            R2DeclarationProfile.RHINO,
            first.fingerprintPath,
            layers,
        )
        val secondEntryFingerprint = R2TrustedDeclarations.calculateProfileFingerprint(
            R2DeclarationProfile.RHINO,
            second.fingerprintPath,
            layers,
        )
        val changedEngineFingerprint = R2TrustedDeclarations.layerFingerprint(
            R2DeclarationLayer.RHINO,
            listOf(
                first,
                second.copy(
                    text = "declare const alternate: false;\n",
                    size = "declare const alternate: false;\n"
                        .toByteArray(StandardCharsets.UTF_8)
                        .size
                        .toLong(),
                    sha256 = R2Digests.sha256(
                        "declare const alternate: false;\n"
                            .toByteArray(StandardCharsets.UTF_8),
                    ),
                ),
            ),
        )
        val changedLayers = linkedMapOf(
            R2DeclarationLayer.COMMON.manifestValue to commonFingerprint,
            R2DeclarationLayer.RHINO.manifestValue to changedEngineFingerprint,
        )

        assertFalse(R2Digests.equal(firstEntryFingerprint, secondEntryFingerprint))
        assertFalse(
            R2Digests.equal(
                firstEntryFingerprint,
                R2TrustedDeclarations.calculateProfileFingerprint(
                    R2DeclarationProfile.RHINO,
                    first.fingerprintPath,
                    changedLayers,
                ),
            ),
        )
    }

    private fun assertRejected(files: List<R2TrustedDeclarationFile>) {
        val matchingFingerprint = R2TrustedDeclarations.fingerprint(files)
        assertThrows(IllegalArgumentException::class.java) {
            R2TrustedDeclarations.create(
                fingerprint = matchingFingerprint,
                files = files,
            )
        }
    }

    private fun assertInvalidFile(file: R2TrustedDeclarationFile) {
        assertRejected(listOf(file))
    }

    private fun alphaDeclaration() = declaration(
        archivePath = "declarations/lib.alpha.d.ts",
        fingerprintPath = "lib/lib.alpha.d.ts",
        text = "interface Alpha {}\n",
    )

    private fun betaDeclaration() = declaration(
        archivePath = "declarations/lib.beta.d.ts",
        fingerprintPath = "lib/lib.beta.d.ts",
        text = "interface Beta { value: string }\n",
    )

    private fun engineDeclaration(
        layer: R2DeclarationLayer,
        fileName: String,
        text: String = "declare const ${layer.manifestValue}Api: true;\n",
    ): R2TrustedDeclarationFile = declaration(
        archivePath = "declarations/${layer.manifestValue}/$fileName",
        fingerprintPath = "layers/${layer.manifestValue}/$fileName",
        text = text,
    )

    private fun profileDeclarations(
        profile: R2DeclarationProfile,
        common: List<R2TrustedDeclarationFile>,
        commonFingerprint: String,
        engine: List<R2TrustedDeclarationFile>,
    ): R2TrustedDeclarations {
        val engineFingerprint = R2TrustedDeclarations.layerFingerprint(
            profile.engineLayer,
            engine,
        )
        val layers = linkedMapOf(
            R2DeclarationLayer.COMMON.manifestValue to commonFingerprint,
            profile.engineLayer.manifestValue to engineFingerprint,
        )
        val entryPoint = engine.first().fingerprintPath
        val profileFingerprint = R2TrustedDeclarations.calculateProfileFingerprint(
            profile,
            entryPoint,
            layers,
        )
        return R2TrustedDeclarations.createProfile(
            profile = profile,
            commonFingerprint = commonFingerprint,
            commonFiles = common,
            engineFingerprint = engineFingerprint,
            engineFiles = engine,
            engineEntryPoint = entryPoint,
            profileFingerprint = profileFingerprint,
        )
    }

    private fun declaration(
        archivePath: String,
        fingerprintPath: String,
        text: String,
    ): R2TrustedDeclarationFile {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return R2TrustedDeclarationFile(
            archivePath = archivePath,
            fingerprintPath = fingerprintPath,
            size = bytes.size.toLong(),
            sha256 = R2Digests.sha256(bytes),
            text = text,
        )
    }

    private companion object {
        const val EXPECTED_AGGREGATE_FINGERPRINT =
            "84cbb04d114c6a793da2e8cd0e49f843d395f446e3a228ec957605c52c11f90e"
        val ZERO_SHA256 = "0".repeat(64)
    }
}
