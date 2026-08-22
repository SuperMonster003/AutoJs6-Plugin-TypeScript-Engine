package io.github.supermonster003.autojs6.plugin.typescript

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Entry
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class R2ProjectArchiveLoaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun callerBytesArePrivatelyStagedValidatedAndAdaptedThenDeleted() {
        val privateDirectory = temporaryFolder.newFolder("valid-staging")
        val fixture = fixture()
        val input = CloseRecordingInputStream(fixture.archiveBytes)

        R2ProjectArchiveLoader.load(
            input = input,
            privateDirectory = privateDirectory,
            authority = fixture.authority,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
        ).use { adapted ->
            assertFalse("Pure InputStream ownership unexpectedly transferred.", input.closed)
            assertTrue(adapted.snapshot is R2FileBackedProjectSnapshot)
            assertEquals(MAIN_SOURCE, adapted.snapshot.readText("/sources/main.ts"))
            assertEquals(
                DEFAULT_LIBRARY,
                adapted.snapshot.readText("/declarations/lib.d.ts"),
            )
            assertEquals(listOf("/sources/main.ts"), adapted.snapshot.rootNames)
            assertEquals("/sources/main.ts", adapted.snapshot.entryPath)
            assertEquals(fixture.authority.projectId, adapted.snapshot.acceptedProjectId)
            assertEquals(
                adapted.acceptedInputAuthority,
                adapted.snapshot.acceptedInputAuthority,
            )
            assertEquals(
                adapted.manifest.callerInventoryFingerprint,
                adapted.acceptedInputAuthority.callerInventoryFingerprint,
            )
            assertEquals(
                fixture.trustedDeclarations.profileFingerprint,
                adapted.acceptedInputAuthority.declarationProfileFingerprint,
            )
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun nodeRevisionTwoMetadataProducesAnExactFileBackedPackageFormatCatalog() {
        val privateDirectory = temporaryFolder.newFolder("node-revision-two-package-format")
        val packageMetadataEntries = rootPackageMetadataEntries()
        val fixture = fixture(
            packageMetadataEntries = packageMetadataEntries,
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        )

        val adapted = R2ProjectArchiveLoader.load(
            input = ByteArrayInputStream(fixture.archiveBytes),
            privateDirectory = privateDirectory,
            authority = fixture.authority,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint =
                fixture.authority.runtimeCapabilitiesFingerprint,
        )
        val snapshot = adapted.snapshot
        assertTrue(snapshot is R2FileBackedProjectSnapshot)
        assertEquals(fixture.authority.projectId, snapshot.acceptedProjectId)
        val catalog = requireNotNull(snapshot.nodePackageFormatCatalog)
        assertEquals(
            fixture.authority.packageMetadataAuthority?.fingerprint,
            catalog.packageMetadataInventoryFingerprint,
        )
        assertEquals(listOf("/"), catalog.records.map { record ->
            record.packageBoundaryPath
        })
        assertEquals(R2PackageTypeState.MODULE, catalog.records.single().typeState)
        assertArrayEquals(
            packageMetadataEntries.single().second,
            readAll(snapshot.openPackageMetadataFile(0)),
        )

        val cursorAtClose = snapshot.openPackageMetadataFile(0)
        adapted.close()
        assertThrows(IllegalStateException::class.java) {
            cursorAtClose.read(ByteArray(1), 0, 1)
        }
        cursorAtClose.close()
        assertThrows(IllegalStateException::class.java) {
            snapshot.openPackageMetadataFile(0)
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun nodeRevisionTwoFormalSchemaLoadsSixV2TargetSuffixesIntoIndependentCursors() {
        val privateDirectory = temporaryFolder.newFolder("node-revision-two-v2-targets")
        val dependencyRoot = "packages/pkg"
        val packageBytes = "{\"type\":\"module\"}\n".toByteArray(StandardCharsets.UTF_8)
        val targetEntries = listOf(
            "$dependencyRoot/a.ts",
            "$dependencyRoot/b.mts",
            "$dependencyRoot/c.cts",
            "$dependencyRoot/d.d.ts",
            "$dependencyRoot/e.d.mts",
            "$dependencyRoot/f.d.cts",
        ).map { path -> path to "export {};\n".toByteArray(StandardCharsets.UTF_8) }
        val fixture = fixture(
            dependencyRoots = listOf(dependencyRoot),
            packageMetadataEntries = listOf(
                "package.json" to packageBytes,
                "$dependencyRoot/package.json" to packageBytes,
            ),
            nodePackageTargetV2Entries = targetEntries,
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        )

        val adapted = R2ProjectArchiveLoader.load(
            input = ByteArrayInputStream(fixture.archiveBytes),
            privateDirectory = privateDirectory,
            authority = fixture.authority,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint =
                fixture.authority.runtimeCapabilitiesFingerprint,
        )
        val snapshot = adapted.snapshot
        assertTrue(snapshot is R2FileBackedProjectSnapshot)
        val targets = requireNotNull(adapted.manifest.nodePackageTargetV2Inventory)
        assertEquals(null, adapted.manifest.packageTargetInventory)
        assertEquals(
            fixture.authority.nodePackageTargetV2Authority?.fingerprint,
            targets.fingerprint,
        )
        assertEquals(targets, snapshot.nodePackageTargetV2Inventory)
        assertTrue(snapshot.nodePackageMetadataCatalog != null)
        targets.files.forEachIndexed { index, descriptor ->
            assertArrayEquals(
                targetEntries.toMap().getValue(descriptor.path),
                readAll(snapshot.openNodePackageTargetV2File(index)),
            )
        }

        val cursorAtClose = snapshot.openNodePackageTargetV2File(0)
        adapted.close()
        assertThrows(IllegalStateException::class.java) {
            cursorAtClose.read(ByteArray(1), 0, 1)
        }
        cursorAtClose.close()
        assertThrows(IllegalStateException::class.java) {
            snapshot.openNodePackageTargetV2File(0)
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun actualZipLoadsNodeRevisionTwoMtsAndCtsForBothFormalConditionalShapes() {
        listOf("mts", "cts").forEach { extension ->
            listOf(false, true).forEach { withMetadata ->
                val privateDirectory = temporaryFolder.newFolder(
                    "node2-$extension-${if (withMetadata) "metadata" else "base"}",
                )
                val entryPath = "sources/main.$extension"
                val sourceText = "export const entry = '$extension';\n"
                val fixture = fixture(
                    sourceBytes = sourceText.toByteArray(StandardCharsets.UTF_8),
                    entryPath = entryPath,
                    packageMetadataEntries =
                        if (withMetadata) rootPackageMetadataEntries() else emptyList(),
                    profileAuthority =
                        R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                )

                R2ProjectArchiveLoader.load(
                    input = ByteArrayInputStream(fixture.archiveBytes),
                    privateDirectory = privateDirectory,
                    authority = fixture.authority,
                    trustedDeclarations = fixture.trustedDeclarations,
                    trustedRuntimeCapabilitiesFingerprint =
                        fixture.authority.runtimeCapabilitiesFingerprint,
                ).use { adapted ->
                    assertTrue(adapted.snapshot is R2FileBackedProjectSnapshot)
                    assertEquals("/$entryPath", adapted.snapshot.entryPath)
                    assertEquals(listOf("/$entryPath"), adapted.snapshot.rootNames)
                    assertEquals(sourceText, adapted.snapshot.readText("/$entryPath"))
                    assertEquals(
                        withMetadata,
                        adapted.manifest.packageMetadataInventory != null,
                    )
                    assertEquals(
                        withMetadata,
                        adapted.snapshot.nodePackageFormatCatalog != null,
                    )
                }
                assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
            }
        }
    }

    @Test
    fun productionNodeRevisionTwoOutputStageRequiresExactAdaptedSnapshotIdentity() {
        val fixture = fixture(
            sourceBytes = "export const main = 1;\n".toByteArray(StandardCharsets.UTF_8),
            declaredAdditionalSourceEntries = listOf(
                "sources/module.mts" to
                    "export const module = 2;\n".toByteArray(StandardCharsets.UTF_8),
                "sources/common.cts" to
                    "export const common = 3;\n".toByteArray(StandardCharsets.UTF_8),
            ),
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        )
        val first = R2ProjectArchiveLoader.load(
            input = ByteArrayInputStream(fixture.archiveBytes),
            privateDirectory = temporaryFolder.newFolder("production-output-input-a"),
            authority = fixture.authority,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint =
                fixture.authority.runtimeCapabilitiesFingerprint,
        )
        val second = R2ProjectArchiveLoader.load(
            input = ByteArrayInputStream(fixture.archiveBytes),
            privateDirectory = temporaryFolder.newFolder("production-output-input-b"),
            authority = fixture.authority,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint =
                fixture.authority.runtimeCapabilitiesFingerprint,
        )
        try {
            assertTrue(first.snapshot !== second.snapshot)
            val compilation = nodeRevisionTwoCompilation(
                snapshot = first.snapshot,
                compilerVersion = fixture.authority.compilerVersion,
            )
            val unbound = R2ProjectCompilationOutcome(
                compilation = compilation,
                errorCode = null,
                errorMessage = null,
                elapsedMs = 1L,
            )
            R2PrivateOutputArchive.stage(
                privateDirectory = temporaryFolder.newFolder("production-output-success"),
                acceptedArchive = first,
                compilationOutcome = unbound.bindToExactSnapshot(first.snapshot),
            ).use { staged ->
                assertEquals(TypeScriptCompilerContract.OUTCOME_EMITTED, staged.summary.outcome)
                assertEquals(3, staged.summary.emittedModuleCount)
            }

            val equivalentButDifferentBinding = unbound.bindToExactSnapshot(second.snapshot)
            assertThrows(R2OutputArchiveException::class.java) {
                R2PrivateOutputArchive.stage(
                    privateDirectory = temporaryFolder.newFolder("production-output-drift"),
                    acceptedArchive = first,
                    compilationOutcome = equivalentButDifferentBinding,
                )
            }
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun fileBackedSourceKindAdmissionMatchesTheExactProfileFence() {
        listOf(
            R2CompilationProfileRegistry.PROVISIONAL_RHINO to "sources/main.mts",
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1 to
                "sources/main.cts",
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 to
                "sources/main.MTS",
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 to
                "sources/main.d.cts",
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 to
                "sources/node_modules/dependency/main.mts",
        ).forEachIndexed { index, (profileAuthority, entryPath) ->
            val privateDirectory = temporaryFolder.newFolder("source-kind-rejection-$index")
            val fixture = fixture(
                entryPath = entryPath,
                profileAuthority = profileAuthority,
            )

            val failure = assertThrows(R2ProjectArchiveException::class.java) {
                R2ProjectArchiveLoader.load(
                    input = ByteArrayInputStream(fixture.archiveBytes),
                    privateDirectory = privateDirectory,
                    authority = fixture.authority,
                    trustedDeclarations = fixture.trustedDeclarations,
                    trustedRuntimeCapabilitiesFingerprint =
                        fixture.authority.runtimeCapabilitiesFingerprint,
                )
            }
            assertEquals(TypeScriptCompilerContract.ERROR_ARCHIVE_PATH, failure.contractErrorCode)
            assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun actualZipUndeclaredMtsUsesNodeTwoIntegrityAndLegacyPathClassification() {
        listOf(
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 to
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1 to
                TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
        ).forEachIndexed { index, (profileAuthority, expectedError) ->
            val privateDirectory = temporaryFolder.newFolder("undeclared-mts-$index")
            val fixture = fixture(
                extraArchiveEntries = listOf(
                    "sources/z-extra.mts" to "export {};\n".toByteArray(),
                ),
                profileAuthority = profileAuthority,
            )

            val failure = assertThrows(R2ProjectArchiveException::class.java) {
                R2ProjectArchiveLoader.load(
                    input = ByteArrayInputStream(fixture.archiveBytes),
                    privateDirectory = privateDirectory,
                    authority = fixture.authority,
                    trustedDeclarations = fixture.trustedDeclarations,
                    trustedRuntimeCapabilitiesFingerprint =
                        fixture.authority.runtimeCapabilitiesFingerprint,
                )
            }
            assertEquals(expectedError, failure.contractErrorCode)
            assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun fileBackedArchiveMapsInvalidNodePackageTypeToInvalidRequest() {
        val privateDirectory = temporaryFolder.newFolder("invalid-node-package-type")
        val fixture = fixture(
            packageMetadataEntries = listOf(
                "package.json" to "{\"type\":\"esm\"}\n".toByteArray(),
            ),
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        )

        val failure = assertThrows(R2ProjectArchiveException::class.java) {
            R2ProjectArchiveLoader.load(
                input = ByteArrayInputStream(fixture.archiveBytes),
                privateDirectory = privateDirectory,
                authority = fixture.authority,
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint =
                    fixture.authority.runtimeCapabilitiesFingerprint,
            )
        }

        assertEquals(
            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            failure.contractErrorCode,
        )
        assertTrue(
            failure.message.orEmpty().contains(
                R2NodeModuleOutputPolicyCode.PACKAGE_TYPE_INVALID.contractValue,
            ),
        )
        assertTrue(failure.cause is R2NodeModuleOutputPolicyException)
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun fileBackedLoaderPreservesAcceptedEntryInsteadOfInferringTheFirstSortedRoot() {
        val privateDirectory = temporaryFolder.newFolder("explicit-entry-staging")
        val fixture = fixture(
            declaredAdditionalSourceEntries = listOf(
                "sources/helper.ts" to "export const helper = true;\n".toByteArray(),
            ),
        )

        R2ProjectArchiveLoader.load(
            input = ByteArrayInputStream(fixture.archiveBytes),
            privateDirectory = privateDirectory,
            authority = fixture.authority,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
        ).use { adapted ->
            assertEquals(
                listOf("/sources/helper.ts", "/sources/main.ts"),
                adapted.snapshot.rootNames,
            )
            assertEquals("/sources/main.ts", adapted.snapshot.entryPath)
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun declaredOrdinaryJsonAndJavaScriptRemainValidFileBackedPayloads() {
        val privateDirectory = temporaryFolder.newFolder("ordinary-declared-payloads")
        val jsonPath = "sources/z-config.json"
        val javaScriptPath = "sources/z-inert.js"
        val jsonBytes = "{\"mode\":\"inert\"}\n".toByteArray(StandardCharsets.UTF_8)
        val javaScriptBytes =
            "export const inert = true;\n".toByteArray(StandardCharsets.UTF_8)
        val fixture = fixture(
            declaredAdditionalSourceEntries = listOf(
                jsonPath to jsonBytes,
                javaScriptPath to javaScriptBytes,
            ),
        )

        R2ProjectArchiveLoader.load(
            input = ByteArrayInputStream(fixture.archiveBytes),
            privateDirectory = privateDirectory,
            authority = fixture.authority,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
        ).use { adapted ->
            assertTrue(adapted.snapshot is R2FileBackedProjectSnapshot)
            assertEquals(
                String(jsonBytes, StandardCharsets.UTF_8),
                adapted.snapshot.readText("/$jsonPath"),
            )
            assertEquals(
                String(javaScriptBytes, StandardCharsets.UTF_8),
                adapted.snapshot.readText("/$javaScriptPath"),
            )
            assertEquals(listOf("/sources/main.ts"), adapted.snapshot.rootNames)
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun undeclaredReservedFileBackedEntryUsesPathClassificationBeforeExactInventory() {
        val privateDirectory = temporaryFolder.newFolder("reserved-extra-entry")
        val fixture = fixture(
            extraArchiveEntries = listOf(
                "sources/z/Node_Modules/dependency/index.ts" to byteArrayOf(0),
            ),
        )

        val failure = assertThrows(R2ProjectArchiveException::class.java) {
            R2ProjectArchiveLoader.load(
                input = ByteArrayInputStream(fixture.archiveBytes),
                privateDirectory = privateDirectory,
                authority = fixture.authority,
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }

        assertEquals(TypeScriptCompilerContract.ERROR_ARCHIVE_PATH, failure.contractErrorCode)
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun undeclaredOrdinaryFileBackedEntryRemainsAnIntegrityFailure() {
        val privateDirectory = temporaryFolder.newFolder("ordinary-extra-entry")
        val fixture = fixture(
            extraArchiveEntries = listOf(
                "sources/z-extra.json" to byteArrayOf(0),
            ),
        )

        val failure = assertThrows(R2ProjectArchiveException::class.java) {
            R2ProjectArchiveLoader.load(
                input = ByteArrayInputStream(fixture.archiveBytes),
                privateDirectory = privateDirectory,
                authority = fixture.authority,
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }

        assertEquals(
            TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            failure.contractErrorCode,
        )
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun controlledRawConfigStaysFileBackedAndOutsideTheTextVfs() {
        val privateDirectory = temporaryFolder.newFolder("raw-config-file-backed")
        val rawConfig = ByteArray(TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES) {
                index ->
            (index * 31).toByte()
        }
        val fixture = fixture(rawConfigBytes = rawConfig)
        val adapted = R2ProjectArchiveLoader.load(
            input = ByteArrayInputStream(fixture.archiveBytes),
            privateDirectory = privateDirectory,
            authority = fixture.authority,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
        )
        val snapshot = adapted.snapshot

        assertTrue(snapshot is R2FileBackedProjectSnapshot)
        assertEquals(
            fixture.authority.rawConfigAuthority?.descriptor(),
            snapshot.rawConfigDescriptor,
        )
        assertFalse(snapshot.filePaths.contains("/tsconfig.json"))
        assertFalse(snapshot.fileInventory.any { file -> file.path == "/tsconfig.json" })
        assertFalse(snapshot.javaClass.declaredFields.any { field ->
            field.type == ByteArray::class.java
        })
        val cursor = requireNotNull(snapshot.openRawConfig())
        assertArrayEquals(rawConfig, readAll(cursor))

        val invalidatedCursor = requireNotNull(snapshot.openRawConfig())
        adapted.close()
        assertThrows(IllegalStateException::class.java) { snapshot.openRawConfig() }
        assertThrows(IllegalStateException::class.java) {
            invalidatedCursor.read(ByteArray(1), 0, 1)
        }
        invalidatedCursor.close()
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun controlledPackageMetadataStaysFileBackedOutsideTextVfsAndCleansUp() {
        val privateDirectory = temporaryFolder.newFolder("package-metadata-file-backed")
        val packageEntries = defaultPackageMetadataEntries()
        val expectedBytes = packageEntries.associate { (path, bytes) -> path to bytes.copyOf() }
        val fixture = fixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = packageEntries,
        )
        val adapted = R2ProjectArchiveLoader.load(
            input = ByteArrayInputStream(fixture.archiveBytes),
            privateDirectory = privateDirectory,
            authority = fixture.authority,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
        )
        val snapshot = adapted.snapshot

        assertTrue(snapshot is R2FileBackedProjectSnapshot)
        val inventory = requireNotNull(snapshot.packageMetadataInventory)
        assertEquals(DEFAULT_DEPENDENCY_ROOTS, inventory.dependencyRoots)
        assertEquals(expectedBytes.keys.sorted(), inventory.files.map { it.path })
        inventory.files.forEachIndexed { index, descriptor ->
            assertFalse(snapshot.filePaths.contains("/${descriptor.path}"))
            assertFalse(snapshot.fileInventory.any { file ->
                file.path == "/${descriptor.path}"
            })
            assertArrayEquals(
                expectedBytes.getValue(descriptor.path),
                readAll(snapshot.openPackageMetadataFile(index)),
            )
        }
        assertFalse(snapshot.javaClass.declaredFields.any { field ->
            field.type == ByteArray::class.java
        })

        val invalidatedCursor = snapshot.openPackageMetadataFile(0)
        adapted.close()
        assertThrows(IllegalStateException::class.java) {
            snapshot.openPackageMetadataFile(0)
        }
        assertThrows(IllegalStateException::class.java) {
            invalidatedCursor.read(ByteArray(1), 0, 1)
        }
        invalidatedCursor.close()
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun invalidFileBackedPackageJsonAndUndeclaredPackagePathAlwaysCleanUp() {
        val invalidPackageJson = listOf(
            "{\"name\":\"root\",\"scripts\":{\"build\":\"host command\"}}".toByteArray(),
            "{\"name\":\"root\",\"name\":\"duplicate\"}".toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28),
        )
        invalidPackageJson.forEachIndexed { index, rootPackageBytes ->
            val privateDirectory = temporaryFolder.newFolder("invalid-package-json-$index")
            val entries = defaultPackageMetadataEntries().map { (path, bytes) ->
                path to if (path == R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH) {
                    rootPackageBytes
                } else {
                    bytes
                }
            }
            val fixture = fixture(
                rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
                dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
                packageMetadataEntries = entries,
            )

            val failure = assertThrows(R2ProjectArchiveException::class.java) {
                R2ProjectArchiveLoader.load(
                    input = ByteArrayInputStream(fixture.archiveBytes),
                    privateDirectory = privateDirectory,
                    authority = fixture.authority,
                    trustedDeclarations = fixture.trustedDeclarations,
                    trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
                )
            }
            assertEquals(TypeScriptCompilerContract.ERROR_INVALID_REQUEST, failure.contractErrorCode)
            assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
        }

        val privateDirectory = temporaryFolder.newFolder("undeclared-package-path")
        val smuggled = fixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = defaultPackageMetadataEntries(),
            extraArchiveEntries = listOf(
                "packages/rogue/package.json" to "{}\n".toByteArray(),
            ),
        )
        val failure = assertThrows(R2ProjectArchiveException::class.java) {
            R2ProjectArchiveLoader.load(
                input = ByteArrayInputStream(smuggled.archiveBytes),
                privateDirectory = privateDirectory,
                authority = smuggled.authority,
                trustedDeclarations = smuggled.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }
        assertEquals(TypeScriptCompilerContract.ERROR_ARCHIVE_PATH, failure.contractErrorCode)
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun stagedArchiveHashMismatchFailsBeforeZipUseAndAlwaysCleansUp() {
        val privateDirectory = temporaryFolder.newFolder("hash-mismatch")
        val fixture = fixture()

        val failure = assertThrows(R2ProjectArchiveException::class.java) {
            R2ProjectArchiveLoader.load(
                input = ByteArrayInputStream(fixture.archiveBytes),
                privateDirectory = privateDirectory,
                authority = fixture.authority.copy(archiveSha256 = sha256("other")),
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }

        assertEquals(
            TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            failure.contractErrorCode,
        )
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun malformedZipMapsFrozenContainerFailureAndAlwaysCleansUp() {
        val privateDirectory = temporaryFolder.newFolder("bad-zip")
        val bytes = "not a ZIP container".toByteArray()
        val fixture = fixture()
        val authority = fixture.authority.copy(
            archiveSha256 = R2Digests.sha256(bytes),
        )

        val failure = assertThrows(R2ProjectArchiveException::class.java) {
            R2ProjectArchiveLoader.load(
                input = ByteArrayInputStream(bytes),
                privateDirectory = privateDirectory,
                authority = authority,
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }

        assertEquals(
            TypeScriptCompilerContract.ERROR_ARCHIVE_FORMAT,
            failure.contractErrorCode,
        )
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun frozenZipValidatorReceivesExactlyTheCapturedCallerBytes() {
        val fixture = fixture()
        val privateDirectory = temporaryFolder.newFolder("exact-capture")
        var capturedBytes = ByteArray(0)
        R2PrivateInputStaging.stage(
            input = ByteArrayInputStream(fixture.archiveBytes),
            privateDirectory = privateDirectory,
        ).use { staging ->
            staging.withImmutableReader { reader ->
                capturedBytes = reader.readAllBytes()
            }
        }

        assertArrayEquals(fixture.archiveBytes, capturedBytes)
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun sourceBeyondFormerMaterializationGateUsesFileBackedSnapshot() {
        val privateDirectory = temporaryFolder.newFolder("large-file-backed")
        val sourceBytes = ByteArray(2 * 1024 * 1024) { index ->
            if (index % 80 == 79) '\n'.code.toByte() else ' '.code.toByte()
        }
        val fixture = fixture(sourceBytes)

        val adapted = R2ProjectArchiveLoader.load(
            input = ByteArrayInputStream(fixture.archiveBytes),
            privateDirectory = privateDirectory,
            authority = fixture.authority,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
        )
        adapted.use {
            val snapshot = adapted.snapshot
            assertTrue(snapshot is R2FileBackedProjectSnapshot)
            assertTrue(snapshot.totalUtf8Bytes > 1024L * 1024L)
            val sourceIndex = snapshot.fileInventory.indexOfFirst { file ->
                file.path == "/sources/main.ts"
            }
            assertTrue(sourceIndex >= 0)
            assertEquals(
                sourceBytes.size.toLong(),
                snapshot.fileInventory[sourceIndex].utf8Bytes,
            )
            snapshot.openFile(sourceIndex).use { cursor ->
                val chunk = ByteArray(R2_PROJECT_FRAME_RAW_CHUNK_BYTES)
                assertEquals(chunk.size, cursor.read(chunk, 0, chunk.size))
                assertArrayEquals(sourceBytes.copyOf(chunk.size), chunk)
            }
        }
        adapted.close()
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun invalidUtf8InStreamingSpoolFailsAndDeletesPartialState() {
        val privateDirectory = temporaryFolder.newFolder("invalid-utf8-spool")
        val fixture = fixture(byteArrayOf(0xc3.toByte(), 0x28))

        val failure = assertThrows(R2ProjectArchiveException::class.java) {
            R2ProjectArchiveLoader.load(
                input = ByteArrayInputStream(fixture.archiveBytes),
                privateDirectory = privateDirectory,
                authority = fixture.authority,
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }

        assertEquals(
            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            failure.contractErrorCode,
        )
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun utf8SplitAcrossStreamingBoundariesKeepsExactUtf16Inventory() {
        val privateDirectory = temporaryFolder.newFolder("split-utf8-spool")
        val sourceText =
            "a".repeat(R2_PROJECT_FRAME_RAW_CHUNK_BYTES - 1) + "中" + "b"
        val sourceBytes = sourceText.toByteArray(StandardCharsets.UTF_8)
        val fixture = fixture(sourceBytes)

        R2ProjectArchiveLoader.load(
            input = ByteArrayInputStream(fixture.archiveBytes),
            privateDirectory = privateDirectory,
            authority = fixture.authority,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
        ).use { adapted ->
            val metadata = adapted.snapshot.fileInventory.single { file ->
                file.path == "/sources/main.ts"
            }
            assertEquals(sourceBytes.size.toLong(), metadata.utf8Bytes)
            assertEquals(sourceText.length, metadata.utf16CodeUnits)
            assertEquals(sourceText, adapted.snapshot.readText(metadata.path))
        }

        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun closingAdaptedArchiveInvalidatesSnapshotAndOpenCursorIdempotently() {
        val privateDirectory = temporaryFolder.newFolder("closed-file-backed")
        val fixture = fixture()
        val adapted = R2ProjectArchiveLoader.load(
            input = ByteArrayInputStream(fixture.archiveBytes),
            privateDirectory = privateDirectory,
            authority = fixture.authority,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
        )
        val sourceIndex = adapted.snapshot.fileInventory.indexOfFirst { file ->
            file.path == "/sources/main.ts"
        }
        assertTrue(sourceIndex >= 0)
        val cursor = adapted.snapshot.openFile(sourceIndex)

        adapted.close()

        assertThrows(IllegalStateException::class.java) {
            adapted.snapshot.openFile(sourceIndex)
        }
        assertThrows(IllegalStateException::class.java) {
            cursor.read(ByteArray(1), 0, 1)
        }
        cursor.close()
        adapted.close()
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun spoolCreationCleanupFailureIsTypedAndPreservesTheOriginalChain() {
        val privateDirectory = temporaryFolder.newFolder("spool-create-cleanup-failure")
        val fileOperations = CreationCleanupFailingFileOperations()
        try {
            val failure = assertThrows(R2PrivateInputCleanupUnprovenException::class.java) {
                R2PrivateProjectSpool.create(
                    privateDirectory = privateDirectory,
                    fileOperations = fileOperations,
                )
            }

            assertSame(fileOperations.creationFailure, failure.cause)
            val closeFailure = failure.suppressed.single()
            assertSame(fileOperations.closeFailure, closeFailure)
            assertSame(fileOperations.deleteFailure, closeFailure.suppressed.single())
        } finally {
            fileOperations.release()
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun sealReaderCleanupFailureIsTypedAndPreservesTheOriginalChain() {
        val privateDirectory = temporaryFolder.newFolder("spool-seal-cleanup-failure")
        val fileOperations = SealCleanupFailingFileOperations()
        val spool = R2PrivateProjectSpool.create(
            privateDirectory = privateDirectory,
            fileOperations = fileOperations,
        )
        try {
            val sourceBytes = MAIN_SOURCE.toByteArray(StandardCharsets.UTF_8)
            val sourceEntry = ZipArchiveV1Entry(
                path = "sources/main.ts",
                compressionMethod = COMPRESSION_STORED,
                compressedBytes = sourceBytes.size.toLong(),
                uncompressedBytes = sourceBytes.size.toLong(),
                crc32 = CRC32().apply { update(sourceBytes) }.value,
            )
            spool.begin(sourceEntry)
            spool.write(sourceBytes, 0, sourceBytes.size)
            spool.end(sourceEntry)
            spool.finishArchiveExtraction()

            val sourceRecord = R2FileBackedProjectRecord(
                metadata = R2ProjectFileMetadata(
                    path = "/sources/main.ts",
                    utf8Bytes = sourceBytes.size.toLong(),
                    utf16CodeUnits = MAIN_SOURCE.length,
                    sha256 = R2Digests.sha256(sourceBytes),
                ),
                offset = 0L,
            )
            val declarationRecord =
                spool.appendTrustedDeclaration(trustedDeclarations().files.single())

            val failure = assertThrows(R2PrivateInputCleanupUnprovenException::class.java) {
                spool.seal(
                    profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                    projectRecords = listOf(sourceRecord, declarationRecord),
                    rootNames = listOf("/sources/main.ts"),
                    limits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE,
                )
            }

            val reader = checkNotNull(fileOperations.reader)
            assertSame(reader.lengthFailure, failure.cause)
            assertSame(reader.closeFailure, failure.suppressed.single())
        } finally {
            fileOperations.releaseReader()
            spool.close()
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun projectSpoolDeletionRestoresWritableStateBeforeRemovingFallback() {
        val privateDirectory = temporaryFolder.newFolder("spool-read-only-delete")
        val file = File(privateDirectory, "fallback.spool")
        file.writeBytes(byteArrayOf(1))
        assertTrue(file.setReadOnly())

        object : R2PrivateProjectSpoolFileOperations {}.delete(
            file = file,
            restoreWritable = true,
        )

        assertFalse(file.exists())
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun ownershipPublicationFailureClosesTheSealedInputBeforeRethrowing() {
        val publicationFailure = IOException("Injected ownership publication failure.")
        val ownedInput = CloseRecordingCloseable()

        val failure = assertThrows(IOException::class.java) {
            publishR2PrivateInputOwnership<Unit>(ownedInput) {
                throw publicationFailure
            }
        }

        assertSame(publicationFailure, failure)
        assertTrue(ownedInput.closed)
    }

    @Test
    fun ownershipPublicationCleanupFailureIsTypedAndPreservesTheOriginalChain() {
        val publicationFailure = IOException("Injected ownership publication failure.")
        val cleanupFailure = IOException("Injected sealed input close failure.")
        val ownedInput = CloseRecordingCloseable(cleanupFailure)

        val failure = assertThrows(R2PrivateInputCleanupUnprovenException::class.java) {
            publishR2PrivateInputOwnership<Unit>(ownedInput) {
                throw publicationFailure
            }
        }

        assertSame(publicationFailure, failure.cause)
        assertSame(cleanupFailure, failure.suppressed.single())
        assertTrue(ownedInput.closed)
    }

    private fun nodeRevisionTwoCompilation(
        snapshot: R2ProjectInputSnapshot,
        compilerVersion: String,
    ): R2ProjectCompilation {
        val outputs = listOf(
            "/sources/main.ts" to "/outputs/main.js",
            "/sources/module.mts" to "/outputs/module.mjs",
            "/sources/common.cts" to "/outputs/common.cjs",
        ).flatMap { (sourcePath, modulePath) ->
            val moduleFile = modulePath.substringAfterLast('/')
            val moduleText =
                "export const value = 1;\n//# sourceMappingURL=$moduleFile.map\n"
            val sourceMapText =
                "{\"version\":3,\"file\":\"$moduleFile\",\"sourceRoot\":\"\"," +
                    "\"sources\":[\"../sources/${sourcePath.substringAfterLast('/')}\"]," +
                    "\"names\":[],\"mappings\":\"AAAA\"}"
            listOf(
                projectOutput(modulePath, moduleText, sourcePath),
                projectOutput("$modulePath.map", sourceMapText, sourcePath),
            )
        }
        val profile = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        return R2ProjectCompilation(
            compilerApi = "createIncrementalProgram",
            compilerVersion = compilerVersion,
            targetProfile = profile.profile.contractValue,
            profileSchemaRevision = profile.profileSchemaRevision,
            profileRevision = profile.profileRevision,
            configSchemaRevision = profile.configSchemaRevision,
            configKind = profile.configKind,
            effectiveOptions = profile.effectiveOptions,
            diagnostics = emptyList(),
            outputs = outputs,
            emitSkipped = false,
            nodeRevisionTwoResultAuthority =
                R2NodeRevisionTwoResultAuthority.derive(snapshot, outputs),
        )
    }

    private fun projectOutput(
        path: String,
        text: String,
        sourcePath: String,
    ): R2ProjectOutput = R2ProjectOutput(
        path = path,
        text = text,
        sha256 = R2Digests.sha256(text.toByteArray(StandardCharsets.UTF_8)),
        sourcePaths = listOf(sourcePath),
    )

    private fun fixture(
        sourceBytes: ByteArray = MAIN_SOURCE.toByteArray(StandardCharsets.UTF_8),
        entryPath: String = "sources/main.ts",
        rawConfigBytes: ByteArray? = null,
        declaredAdditionalSourceEntries: List<Pair<String, ByteArray>> = emptyList(),
        extraArchiveEntries: List<Pair<String, ByteArray>> = emptyList(),
        dependencyRoots: List<String> = emptyList(),
        packageMetadataEntries: List<Pair<String, ByteArray>> = emptyList(),
        nodePackageTargetV2Entries: List<Pair<String, ByteArray>> = emptyList(),
        profileAuthority: R2CompilationProfileAuthority =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO,
    ): Fixture {
        val projectId = "loader-project"
        val entrySourceUri = when (profileAuthority) {
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 ->
                R2GeneratedModuleMappingPolicy.originalSourceUri(
                    projectId = projectId,
                    sourcePath = "/$entryPath",
                )
            else -> "autojs://source/loader/main.ts"
        }
        val trustedDeclarations = when (profileAuthority.profile) {
            R2ProjectProfile.PROVISIONAL_RHINO -> trustedDeclarations()
            R2ProjectProfile.PROVISIONAL_NODE_NEXT ->
                profileDeclarations(R2DeclarationProfile.NODE)
        }
        val sourceEntries = buildList {
            add(entryPath to sourceBytes)
            addAll(declaredAdditionalSourceEntries)
        }.sortedBy { (path, _) -> path }
        val sourceFiles = sourceEntries.map { (path, bytes) ->
            R2ProjectArchiveFile(
                path = path,
                size = bytes.size.toLong(),
                sha256 = R2Digests.sha256(bytes),
            )
        }
        val sourceRootPaths = sourceFiles
            .map(R2ProjectArchiveFile::path)
            .filter { path ->
                R2ProjectSourceKindAdmission.isExecutableSourcePath(
                    path = path,
                    profileAuthority = profileAuthority,
                )
            }
        val rawConfigAuthority = rawConfigBytes?.let { bytes ->
            R2RawProjectConfigAuthority(
                size = bytes.size.toLong(),
                sha256 = R2Digests.sha256(bytes),
            )
        }
        val packageMetadataInventory = packageMetadataEntries
            .sortedBy { (path, _) -> path }
            .map { (path, bytes) ->
                R2PackageMetadataFileDescriptor(
                    path = path,
                    size = bytes.size.toLong(),
                    sha256 = R2Digests.sha256(bytes),
                )
            }
            .takeIf { files -> files.isNotEmpty() }
            ?.let { files -> R2PackageMetadataInventory.create(dependencyRoots, files) }
        val nodePackageTargetV2Inventory = nodePackageTargetV2Entries
            .sortedBy { (path, _) -> path }
            .map { (path, bytes) ->
                R2NodePackageTargetV2FileDescriptor(
                    path = path,
                    size = bytes.size.toLong(),
                    sha256 = R2Digests.sha256(bytes),
                )
            }
            .takeIf { files -> files.isNotEmpty() }
            ?.let { files ->
                R2NodePackageTargetV2Inventory.create(
                    packageMetadataInventory = requireNotNull(packageMetadataInventory),
                    files = files,
                )
            }
        val revision = R2InputArchiveSchemaV1.REVISION
        val configSchemaRevision =
            if (rawConfigAuthority == null) profileAuthority.configSchemaRevision
            else TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION
        val configKind =
            if (rawConfigAuthority == null) profileAuthority.configKind
            else TypeScriptR2Contract.CONFIG_KIND_TSCONFIG
        val configJson = rawConfigAuthority?.let { config ->
            "\"config\":{\"path\":\"${config.path}\",\"size\":${config.size}," +
                "\"sha256\":\"${config.sha256}\",\"policyRevision\":" +
                "${config.policyRevision},\"policyFingerprint\":" +
                "\"${config.policyFingerprint}\"},"
        }.orEmpty()
        val packageMetadataJson = packageMetadataInventory?.let { inventory ->
            val rootsJson = inventory.dependencyRoots.joinToString(",") { root ->
                "\"$root\""
            }
            val filesJson = inventory.files.joinToString(",") { file ->
                "{\"path\":\"${file.path}\",\"size\":${file.size}," +
                    "\"sha256\":\"${file.sha256}\"}"
            }
            "\"packageMetadata\":{\"schemaRevision\":" +
                "${R2PackageMetadataPolicy.SCHEMA_REVISION},\"policyRevision\":" +
                "${inventory.authority.policyRevision},\"policyFingerprint\":" +
                "\"${inventory.authority.policyFingerprint}\"," +
                "\"inventoryFingerprint\":\"${inventory.fingerprint}\"," +
                "\"dependencyRoots\":[$rootsJson],\"files\":[$filesJson]},"
        }.orEmpty()
        val nodePackageTargetV2Json = nodePackageTargetV2Inventory?.let { inventory ->
            val filesJson = inventory.files.joinToString(",") { file ->
                "{\"path\":\"${file.path}\",\"size\":${file.size}," +
                    "\"sha256\":\"${file.sha256}\"}"
            }
            "\"packageTargets\":{\"schemaRevision\":" +
                "${R2NodePackageTargetPolicyV2.SCHEMA_REVISION},\"policyRevision\":" +
                "${inventory.authority.policyRevision},\"policyFingerprint\":" +
                "\"${inventory.authority.policyFingerprint}\"," +
                "\"packageMetadataInventoryFingerprint\":" +
                "\"${inventory.packageMetadataInventoryFingerprint}\"," +
                "\"inventoryFingerprint\":\"${inventory.fingerprint}\"," +
                "\"files\":[$filesJson]},"
        }.orEmpty()
        val manifestText = """
            {"schema":"${R2InputArchiveSchemaV1.SCHEMA}",
            "revision":$revision,
            "inputQuotaRevision":${R2InputArchiveSchemaV1.INPUT_QUOTA_REVISION},
            "contractVersion":${TypeScriptCompilerContract.VERSION},
            "contractHash":"${TypeScriptCompilerContract.HASH}",
            "inputArchiveTransportVersion":${TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION},
            "requestId":"loader-request","projectId":"$projectId",
            "entrySourceUri":"$entrySourceUri",
            "entryPath":"$entryPath","targetProfile":"${profileAuthority.profile.contractValue}",
            "compilationMode":"development",
            "profileSchemaRevision":${profileAuthority.profileSchemaRevision},
            "profileRevision":${profileAuthority.profileRevision},
            "runtimeCapabilitySchemaRevision":${TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION},
            "runtimeCapabilitiesFingerprint":"${profileAuthority.runtimeCapabilitiesFingerprint}",
            "compilerPackage":"typescript","compilerVersion":"6.0.3",
            "compilerDistributionFingerprint":"$DISTRIBUTION_FINGERPRINT",
            "declarationsFingerprint":"${trustedDeclarations.fingerprint}",
            "declarationProfileFingerprint":"${trustedDeclarations.profileFingerprint}",
            "configSchemaRevision":$configSchemaRevision,
            "configKind":"$configKind",$configJson$packageMetadataJson$nodePackageTargetV2Json
            "callerInventoryFingerprint":"${
                R2InputArchiveSchemaV1.callerInventoryFingerprint(
                    sources = sourceFiles,
                    rawConfig = rawConfigAuthority?.descriptor(),
                    packageMetadata = packageMetadataInventory,
                    packageTargets = null,
                    nodePackageTargetsV2 = nodePackageTargetV2Inventory,
                )
            }",
            "rootPaths":[${sourceRootPaths.joinToString(",") { path -> "\"$path\"" }}],
            "files":[${
                sourceFiles.joinToString(",") { file ->
                    "{\"path\":\"${file.path}\",\"sha256\":\"${file.sha256}\"," +
                        "\"size\":${file.size}}"
                }
            }]}
        """.trimIndent().replace("\n", "")
        val manifestBytes = manifestText.toByteArray(StandardCharsets.UTF_8)
        val archiveEntries = buildList {
            add(R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH to manifestBytes)
            rawConfigBytes?.let { bytes ->
                add(R2ControlledTsConfigPolicy.CONFIG_PATH to bytes)
            }
            addAll(packageMetadataEntries)
            addAll(nodePackageTargetV2Entries)
            addAll(sourceEntries)
            addAll(extraArchiveEntries)
        }
        val archiveBytes = storedArchive(*archiveEntries.toTypedArray())
        val authority = R2ProjectArchiveAuthority(
            requestId = "loader-request",
            projectId = projectId,
            entrySourceUri = entrySourceUri,
            profile = profileAuthority.profile,
            profileAuthority = profileAuthority,
            compilationMode = TypeScriptCompilerContract.MODE_DEVELOPMENT,
            compilerVersion = "6.0.3",
            compilerDistributionFingerprint = DISTRIBUTION_FINGERPRINT,
            declarationsFingerprint = trustedDeclarations.fingerprint,
            inputArchiveSchemaRevision = R2InputArchiveSchemaV1.REVISION,
            declarationProfileFingerprint = trustedDeclarations.profileFingerprint,
            runtimeCapabilitiesFingerprint =
                profileAuthority.runtimeCapabilitiesFingerprint,
            archiveSha256 = R2Digests.sha256(archiveBytes),
            requestManifestSha256 = R2Digests.sha256(manifestBytes),
            rawConfigAuthority = rawConfigAuthority,
            packageMetadataAuthority = packageMetadataInventory?.authority,
            nodePackageTargetV2Authority = nodePackageTargetV2Inventory?.authority,
        )
        return Fixture(
            archiveBytes = archiveBytes,
            authority = authority,
            trustedDeclarations = trustedDeclarations,
        )
    }

    private fun trustedDeclarations(): R2TrustedDeclarations {
        val bytes = DEFAULT_LIBRARY.toByteArray(StandardCharsets.UTF_8)
        val file = R2TrustedDeclarationFile(
            archivePath = "declarations/lib.d.ts",
            fingerprintPath = "lib/lib.d.ts",
            size = bytes.size.toLong(),
            sha256 = R2Digests.sha256(bytes),
            text = DEFAULT_LIBRARY,
        )
        return R2TrustedDeclarations.create(
            fingerprint = R2TrustedDeclarations.fingerprint(listOf(file)),
            files = listOf(file),
        )
    }

    private fun profileDeclarations(
        profile: R2DeclarationProfile,
    ): R2TrustedDeclarations {
        val common = trustedDeclarations()
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

    private fun rootPackageMetadataEntries(): List<Pair<String, ByteArray>> = listOf(
        "package.json" to
            "{\"name\":\"root-project\",\"type\":\"module\"}\n".toByteArray(),
    )

    private fun defaultPackageMetadataEntries(): List<Pair<String, ByteArray>> = listOf(
        "package.json" to
            "{\"name\":\"root-project\",\"private\":true}\n".toByteArray(),
        "packages/@scope/beta/package.json" to
            "{\"name\":\"@scope/beta\",\"version\":\"1.0.0\"}\n".toByteArray(),
        "packages/alpha/package.json" to
            "{\"name\":\"alpha\",\"version\":\"1.0.0\"}\n".toByteArray(),
        "packages/alpha/nested/package.json" to
            "{\"name\":\"alpha-nested-boundary\"}\n".toByteArray(),
    )

    private fun storedArchive(
        vararg entries: Pair<String, ByteArray>,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val localOffsets = ArrayList<Int>(entries.size)
        entries.forEach { (path, data) ->
            val name = path.toByteArray(StandardCharsets.US_ASCII)
            val crc = CRC32().apply { update(data) }.value
            localOffsets += output.size()
            output.putU32(LOCAL_FILE_HEADER_SIGNATURE)
            output.putU16(VERSION_STORED)
            output.putU16(0)
            output.putU16(COMPRESSION_STORED)
            output.putU16(0)
            output.putU16(0)
            output.putU32(crc)
            output.putU32(data.size.toLong())
            output.putU32(data.size.toLong())
            output.putU16(name.size)
            output.putU16(0)
            output.write(name)
            output.write(data)
        }

        val centralOffset = output.size()
        entries.forEachIndexed { index, (path, data) ->
            val name = path.toByteArray(StandardCharsets.US_ASCII)
            val crc = CRC32().apply { update(data) }.value
            output.putU32(CENTRAL_DIRECTORY_SIGNATURE)
            output.putU16(VERSION_DEFLATED)
            output.putU16(VERSION_STORED)
            output.putU16(0)
            output.putU16(COMPRESSION_STORED)
            output.putU16(0)
            output.putU16(0)
            output.putU32(crc)
            output.putU32(data.size.toLong())
            output.putU32(data.size.toLong())
            output.putU16(name.size)
            output.putU16(0)
            output.putU16(0)
            output.putU16(0)
            output.putU16(0)
            output.putU32(0)
            output.putU32(localOffsets[index].toLong())
            output.write(name)
        }
        val centralSize = output.size() - centralOffset
        output.putU32(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
        output.putU16(0)
        output.putU16(0)
        output.putU16(entries.size)
        output.putU16(entries.size)
        output.putU32(centralSize.toLong())
        output.putU32(centralOffset.toLong())
        output.putU16(0)
        return output.toByteArray()
    }

    private fun readAll(cursor: R2ProjectFileCursor): ByteArray = cursor.use {
        val result = ByteArray(cursor.remainingBytes.toInt())
        var offset = 0
        while (offset < result.size) {
            val count = cursor.read(result, offset, result.size - offset)
            check(count > 0)
            offset += count
        }
        result
    }

    private fun ByteArrayOutputStream.putU16(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
    }

    private fun ByteArrayOutputStream.putU32(value: Long) {
        repeat(4) { index ->
            write((value ushr (index * 8)).toInt() and 0xff)
        }
    }

    private fun sha256(text: String): String =
        R2Digests.sha256(text.toByteArray(StandardCharsets.UTF_8))

    private fun RandomAccessFile.readAllBytes(): ByteArray {
        val byteCount = length()
        check(byteCount <= Int.MAX_VALUE)
        seek(0L)
        return ByteArray(byteCount.toInt()).also(::readFully)
    }

    private class CloseRecordingInputStream(
        bytes: ByteArray,
    ) : InputStream() {

        private val delegate = ByteArrayInputStream(bytes)
        var closed = false
            private set

        override fun read(): Int = delegate.read()

        override fun read(
            destination: ByteArray,
            offset: Int,
            length: Int,
        ): Int = delegate.read(destination, offset, length)

        override fun close() {
            closed = true
            delegate.close()
        }
    }

    private class CloseRecordingCloseable(
        private val closeFailure: Throwable? = null,
    ) : Closeable {

        var closed = false
            private set

        override fun close() {
            closed = true
            closeFailure?.let { throw it }
        }
    }

    private class CreationCleanupFailingFileOperations :
        R2PrivateProjectSpoolFileOperations {

        val creationFailure = IOException("Injected spool construction failure.")
        val closeFailure = IOException("Injected spool output close failure.")
        val deleteFailure = IOException("Injected spool delete failure.")
        private var file: File? = null
        private var output: CloseFailingFileOutputStream? = null

        override fun openOutput(file: File): FileOutputStream {
            this.file = file
            return CloseFailingFileOutputStream(file, closeFailure).also { output = it }
        }

        override fun afterOutputOpened() {
            throw creationFailure
        }

        override fun delete(
            file: File,
            restoreWritable: Boolean,
        ) {
            throw deleteFailure
        }

        fun release() {
            output?.allowClose()
            runCatching { output?.close() }
            file?.let { createdFile ->
                createdFile.setWritable(true, true)
                runCatching { createdFile.delete() }
            }
        }
    }

    private class CloseFailingFileOutputStream(
        file: File,
        private val injectedFailure: IOException,
    ) : FileOutputStream(file, false) {

        private var closeAllowed = false

        fun allowClose() {
            closeAllowed = true
        }

        override fun close() {
            if (!closeAllowed) throw injectedFailure
            super.close()
        }
    }

    private class SealCleanupFailingFileOperations :
        R2PrivateProjectSpoolFileOperations {

        var reader: LengthAndCloseFailingRandomAccessFile? = null
            private set

        override fun openReader(file: File): RandomAccessFile =
            LengthAndCloseFailingRandomAccessFile(file).also { reader = it }

        fun releaseReader() {
            reader?.allowClose()
            runCatching { reader?.close() }
        }
    }

    private class LengthAndCloseFailingRandomAccessFile(
        file: File,
    ) : RandomAccessFile(file, "r") {

        val lengthFailure = IOException("Injected spool reader length failure.")
        val closeFailure = IOException("Injected spool reader close failure.")
        private var closeAllowed = false

        fun allowClose() {
            closeAllowed = true
        }

        override fun length(): Long = throw lengthFailure

        override fun close() {
            if (!closeAllowed) throw closeFailure
            super.close()
        }
    }

    private data class Fixture(
        val archiveBytes: ByteArray,
        val authority: R2ProjectArchiveAuthority,
        val trustedDeclarations: R2TrustedDeclarations,
    )

    private companion object {
        const val MAIN_SOURCE = "export const answer = 42;\n"
        const val DEFAULT_LIBRARY = "interface Array<T> { length: number }\n"
        const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
        const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
        const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
        const val COMPRESSION_STORED = 0
        const val VERSION_STORED = 10
        const val VERSION_DEFLATED = 20
        val DISTRIBUTION_FINGERPRINT = R2Digests.sha256("distribution".toByteArray())
        val RUNTIME_FINGERPRINT =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.runtimeCapabilitiesFingerprint
        val DEFAULT_DEPENDENCY_ROOTS = listOf(
            "packages/@scope/beta",
            "packages/alpha",
        )
    }
}
