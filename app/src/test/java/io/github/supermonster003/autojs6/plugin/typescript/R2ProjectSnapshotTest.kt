package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class R2ProjectSnapshotTest {

    @Test
    fun validSnapshotProvidesOrderedReadOnlyFilesAndRoots() {
        val snapshot = R2ProjectSnapshot.create(
            files = validFiles(),
            rootNames = listOf(MAIN_SOURCE),
        )

        assertEquals(R2ProjectProfile.PROVISIONAL_RHINO, snapshot.profile)
        assertEquals(
            R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            snapshot.profileAuthority,
        )
        assertEquals(TypeScriptCompilerContract.PROFILE_RHINO, snapshot.profile.contractValue)
        assertEquals(
            listOf(DEFAULT_LIBRARY, MAIN_SOURCE, JSON_DEPENDENCY),
            snapshot.files.keys.toList(),
        )
        assertEquals("export const answer = 42;\n", snapshot.files[MAIN_SOURCE])
        assertEquals(listOf(MAIN_SOURCE), snapshot.rootNames)
        assertEquals(MAIN_SOURCE, snapshot.entryPath)
        assertFalse(snapshot.rootNames.contains(DEFAULT_LIBRARY))
        assertEquals(
            validFiles().sumOf { file -> file.text.toByteArray(Charsets.UTF_8).size }.toLong(),
            snapshot.totalUtf8Bytes,
        )
        assertEquals(
            validFiles().maxOf { file -> file.text.toByteArray(Charsets.UTF_8).size }.toLong(),
            snapshot.largestFileUtf8Bytes,
        )
    }

    @Test
    fun provisionalNodeNextSnapshotAndFramerBindTheNodeDeclarationLayer() {
        val nodeEntry = "/declarations/node/index.d.ts"
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                R2ProjectTextFile(nodeEntry, "declare const nodeProfile: true;\n"),
                R2ProjectTextFile(MAIN_SOURCE, "nodeProfile;\n"),
            ),
            rootNames = listOf(MAIN_SOURCE),
            profile = R2ProjectProfile.PROVISIONAL_NODE_NEXT,
        )

        assertEquals(R2ProjectProfile.PROVISIONAL_NODE_NEXT, snapshot.profile)
        assertEquals(
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT,
            snapshot.profileAuthority,
        )
        val firstFrame = R2ProjectInputFramer("node-next", snapshot).nextFrame()
        assertTrue(firstFrame.contains("\"targetProfile\":\"node\""))
        assertTrue(firstFrame.contains("\"configKind\":\"none\""))
    }

    @Test
    fun registeredNodeRevisionTwoSnapshotRetainsItsExactAuthorityWithoutChangingDefaults() {
        val nodeEntry = "/declarations/node/index.d.ts"
        val revisionTwo = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                R2ProjectTextFile(nodeEntry, "declare const nodeProfile: true;\n"),
                R2ProjectTextFile(MAIN_SOURCE, "nodeProfile;\n"),
            ),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority = revisionTwo,
            limits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
        )

        assertEquals(revisionTwo, snapshot.profileAuthority)
        val firstFrame = R2ProjectInputFramer("node-next-revision-two", snapshot).nextFrame()
        assertTrue(firstFrame.contains("\"targetProfile\":\"node\""))
        assertTrue(firstFrame.contains("\"profileRevision\":2"))

        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = listOf(
                    R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                    R2ProjectTextFile(nodeEntry, ""),
                    R2ProjectTextFile(MAIN_SOURCE, "export {};\n"),
                ),
                rootNames = listOf(MAIN_SOURCE),
                profileAuthority = revisionTwo.copy(configKind = "revision-drift"),
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
            )
        }
    }

    @Test
    fun nodeRevisionTwoHeapSnapshotAdmitsCompleteTsMtsCtsRootsWithEitherMetadataMode() {
        val revisionTwo = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        val moduleSource = "/sources/module.mts"
        val commonJsSource = "/sources/common.cts"
        val files = listOf(
            R2ProjectTextFile(DEFAULT_LIBRARY, ""),
            R2ProjectTextFile(MAIN_SOURCE, "export const main = true;\n"),
            R2ProjectTextFile(moduleSource, "export const module = true;\n"),
            R2ProjectTextFile(commonJsSource, "export const common = true;\n"),
        )
        val unsortedRoots = listOf(moduleSource, MAIN_SOURCE, commonJsSource)

        val withoutMetadata = R2ProjectSnapshot.create(
            files = files,
            rootNames = unsortedRoots,
            profileAuthority = revisionTwo,
            limits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
            entryPath = moduleSource,
        )
        assertEquals(listOf(commonJsSource, MAIN_SOURCE, moduleSource), withoutMetadata.rootNames)
        assertEquals(moduleSource, withoutMetadata.entryPath)
        assertEquals(null, withoutMetadata.packageMetadataInventory)
        assertEquals(null, withoutMetadata.nodePackageFormatCatalog)
        withoutMetadata.close()

        val packageBytes = "{\"type\":\"module\"}\n".toByteArray()
        val inventory = packageMetadataInventory(packageBytes)
        val withMetadata = R2ProjectSnapshot.create(
            files = files,
            rootNames = unsortedRoots,
            profileAuthority = revisionTwo,
            limits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
            entryPath = commonJsSource,
            packageMetadataInventory = inventory,
            packageMetadataBytes = listOf(packageBytes),
        )
        assertEquals(listOf(commonJsSource, MAIN_SOURCE, moduleSource), withMetadata.rootNames)
        assertEquals(commonJsSource, withMetadata.entryPath)
        assertEquals(inventory.fingerprint, withMetadata.packageMetadataInventory?.fingerprint)
        assertEquals(
            R2PackageTypeState.MODULE,
            withMetadata.nodePackageFormatCatalog?.records?.single()?.typeState,
        )
        withMetadata.close()
    }

    @Test
    fun nodeSourceKindsRemainProfileBoundCompleteAndDeclarationClosed() {
        val revisionTwo = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        val revisionOne = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1
        val moduleSource = "/sources/module.mts"
        val commonJsSource = "/sources/common.cts"
        val nodeFiles = listOf(
            R2ProjectTextFile(DEFAULT_LIBRARY, ""),
            R2ProjectTextFile(MAIN_SOURCE, "export {};\n"),
            R2ProjectTextFile(moduleSource, "export {};\n"),
            R2ProjectTextFile(commonJsSource, "export {};\n"),
        )

        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = nodeFiles,
                rootNames = listOf(MAIN_SOURCE, moduleSource),
                profileAuthority = revisionTwo,
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = nodeFiles,
                rootNames = listOf(MAIN_SOURCE, moduleSource, commonJsSource),
                profileAuthority = revisionTwo,
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
                entryPath = "/sources/missing.mts",
            )
        }
        listOf(moduleSource, commonJsSource).forEach { deferredPath ->
            assertFails<IllegalArgumentException> {
                R2ProjectSnapshot.create(
                    files = listOf(
                        R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                        R2ProjectTextFile(deferredPath, "export {};\n"),
                    ),
                    rootNames = listOf(deferredPath),
                    profileAuthority = revisionOne,
                    limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
                )
            }
        }
        listOf(
            "/sources/types.d.mts",
            "/sources/types.d.cts",
            "/declarations/types.d.mts",
            "/declarations/types.d.cts",
        ).forEach { declarationPath ->
            assertFails<IllegalArgumentException> {
                R2ProjectSnapshot.create(
                    files = listOf(
                        R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                        R2ProjectTextFile(MAIN_SOURCE, "export {};\n"),
                        R2ProjectTextFile(declarationPath, "export {};\n"),
                    ),
                    rootNames = listOf(MAIN_SOURCE),
                    profileAuthority = revisionTwo,
                    limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
                )
            }
        }
    }

    @Test
    fun nodeRevisionTwoHeapSnapshotDerivesImmutablePackageFormatIdentity() {
        val revisionTwo = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        val packageBytes = "{\"type\":\"module\"}\n".toByteArray()
        val inventory = packageMetadataInventory(packageBytes)
        val withoutMetadata = R2ProjectSnapshot.create(
            files = validFiles(),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority = revisionTwo,
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
        )
        assertEquals(null, withoutMetadata.packageMetadataInventory)
        assertEquals(null, withoutMetadata.nodePackageFormatCatalog)
        withoutMetadata.close()

        val snapshot = R2ProjectSnapshot.create(
            files = validFiles(),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority = revisionTwo,
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
            packageMetadataInventory = inventory,
            packageMetadataBytes = listOf(packageBytes),
        )
        val catalog = requireNotNull(snapshot.nodePackageFormatCatalog)
        assertEquals(inventory, snapshot.packageMetadataInventory)
        assertEquals(inventory.canonicalInput, catalog.packageMetadataInventoryCanonicalInput)
        assertEquals(inventory.fingerprint, catalog.packageMetadataInventoryFingerprint)
        assertEquals(R2PackageTypeState.MODULE, catalog.records.single().typeState)
        packageBytes.fill(0)
        assertEquals(R2PackageTypeState.MODULE, catalog.records.single().typeState)
        snapshot.close()
        assertEquals(catalog.fingerprint, snapshot.nodePackageFormatCatalog?.fingerprint)
        assertEquals(R2PackageTypeState.MODULE, snapshot.nodePackageFormatCatalog
            ?.records?.single()?.typeState)
        assertFails<IllegalStateException> { snapshot.openPackageMetadataFile(0) }
    }

    @Test
    fun nodeSnapshotRevisionMatrixRejectsUnverifiedPackageCombinations() {
        val packageBytes = "{}\n".toByteArray()
        val inventory = packageMetadataInventory(packageBytes)
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles(),
                rootNames = listOf(MAIN_SOURCE),
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
                packageMetadataInventory = inventory,
                packageMetadataBytes = listOf(packageBytes),
            )
        }
        val rawBytes = "{}".toByteArray()
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles(),
                rootNames = listOf(MAIN_SOURCE),
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
                rawConfigDescriptor = rawConfigDescriptor(rawBytes),
                rawConfigBytes = rawBytes,
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles(),
                rootNames = listOf(MAIN_SOURCE),
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
                packageMetadataInventory = inventory,
                packageMetadataBytes = emptyList(),
            )
        }
    }

    @Test
    fun projectFramerRejectsCrossProfileDeclarationLayersBeforeIngress() {
        val nodeEntry = "/declarations/node/index.d.ts"
        val rhinoEntry = "/declarations/rhino/index.d.ts"
        val rhinoWithNode = R2ProjectSnapshot.create(
            files = validFiles() + R2ProjectTextFile(nodeEntry, ""),
            rootNames = listOf(MAIN_SOURCE),
        )
        assertFails<IllegalArgumentException> {
            R2ProjectInputFramer("rhino-cross-layer", rhinoWithNode)
        }

        val nodeWithRhino = R2ProjectSnapshot.create(
            files = validFiles() +
                listOf(
                    R2ProjectTextFile(nodeEntry, ""),
                    R2ProjectTextFile(rhinoEntry, ""),
                ),
            rootNames = listOf(MAIN_SOURCE),
            profile = R2ProjectProfile.PROVISIONAL_NODE_NEXT,
        )
        assertFails<IllegalArgumentException> {
            R2ProjectInputFramer("node-cross-layer", nodeWithRhino)
        }

        val nodeWithoutEntry = R2ProjectSnapshot.create(
            files = validFiles(),
            rootNames = listOf(MAIN_SOURCE),
            profile = R2ProjectProfile.PROVISIONAL_NODE_NEXT,
        )
        assertFails<IllegalArgumentException> {
            R2ProjectInputFramer("node-missing-layer", nodeWithoutEntry)
        }
    }

    @Test
    fun acceptedEntryMustBeAnExistingExecutableSourceInTheCompleteRootInventory() {
        val helperSource = "/sources/helper.ts"
        val files = validFiles() +
            R2ProjectTextFile(helperSource, "export const helper = true;\n")
        val snapshot = R2ProjectSnapshot.create(
            files = files,
            rootNames = listOf(MAIN_SOURCE, helperSource),
            entryPath = helperSource,
        )

        assertEquals(helperSource, snapshot.entryPath)
        assertEquals(listOf(helperSource, MAIN_SOURCE), snapshot.rootNames)

        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = files,
                rootNames = listOf(MAIN_SOURCE, helperSource),
                entryPath = "/sources/missing.ts",
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = files +
                    R2ProjectTextFile("/sources/types.d.ts", "export interface T {}\n"),
                rootNames = listOf(MAIN_SOURCE, helperSource),
                entryPath = "/sources/types.d.ts",
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = files,
                rootNames = listOf(MAIN_SOURCE, helperSource),
                entryPath = JSON_DEPENDENCY,
            )
        }
    }

    @Test
    fun rejectsEscapeAndNonCanonicalPaths() {
        val invalidPaths = listOf(
            "sources/main.ts",
            "/sources/../main.ts",
            "/sources/./main.ts",
            "/sources//main.ts",
            "/sources/main.ts/",
            "/sources\\main.ts",
            "/outside/main.ts",
            "/declarations/not-a-declaration.ts",
        )

        invalidPaths.forEach { invalidPath ->
            assertFails<IllegalArgumentException> {
                R2ProjectSnapshot.create(
                    files = listOf(
                        R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                        R2ProjectTextFile(invalidPath, ""),
                    ),
                    rootNames = listOf(invalidPath),
                )
            }
        }
    }

    @Test
    fun rejectsReservedPackageAndDeferredNodePaths() {
        val invalidPaths = listOf(
            "/sources/package.json",
            "/sources/Package.Json",
            "/sources/nested/package.json",
            "/sources/node_modules/dependency/index.ts",
            "/sources/Node_Modules/dependency/index.d.ts",
            "/sources/main.mts",
            "/sources/main.CTS",
            "/declarations/Package.Json",
            "/declarations/Node_Modules/dependency/index.d.ts",
            "/declarations/main.d.MTS",
        )

        invalidPaths.forEach { invalidPath ->
            assertFails<IllegalArgumentException> {
                R2ProjectSnapshot.create(
                    files = validFiles() + R2ProjectTextFile(invalidPath, "inert"),
                    rootNames = listOf(MAIN_SOURCE),
                )
            }
        }
    }

    @Test
    fun ordinaryJsonJavaScriptTypeScriptAndDeclarationsRemainValidSnapshotPayloads() {
        val sourceDeclaration = "/sources/types.d.ts"
        val inertJavaScript = "/sources/inert.js"
        val snapshot = R2ProjectSnapshot.create(
            files = validFiles() +
                listOf(
                    R2ProjectTextFile(sourceDeclaration, "export interface Value {}\n"),
                    R2ProjectTextFile(inertJavaScript, "export const inert = true;\n"),
                ),
            rootNames = listOf(MAIN_SOURCE),
        )

        assertTrue(snapshot.files.containsKey(JSON_DEPENDENCY))
        assertTrue(snapshot.files.containsKey(inertJavaScript))
        assertTrue(snapshot.files.containsKey(MAIN_SOURCE))
        assertTrue(snapshot.files.containsKey(sourceDeclaration))
        assertEquals(listOf(MAIN_SOURCE), snapshot.rootNames)
    }

    @Test
    fun safeUnicodeProjectPathsRoundTripWhileControlsAndMalformedTextStayClosed() {
        val unicodeSource = "/sources/中文目录/入口😀.ts"
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                R2ProjectTextFile(unicodeSource, "export const 答案 = 42;\n"),
            ),
            rootNames = listOf(unicodeSource),
        )

        assertEquals(listOf(unicodeSource), snapshot.rootNames)
        assertEquals("export const 答案 = 42;\n", snapshot.readText(unicodeSource))

        listOf(
            "/sources/中文/\u0085control.ts",
            "/sources/中文/bad\ud800.ts",
            "/sources/中文/../escape.ts",
        ).forEach { invalidPath ->
            assertFails<IllegalArgumentException> {
                R2ProjectSnapshot.create(
                    files = listOf(
                        R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                        R2ProjectTextFile(invalidPath, ""),
                    ),
                    rootNames = listOf(invalidPath),
                )
            }
        }
    }

    @Test
    fun rejectsAsciiCaseFoldCollision() {
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = listOf(
                    R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                    R2ProjectTextFile(MAIN_SOURCE, ""),
                    R2ProjectTextFile("/sources/MAIN.ts", ""),
                ),
                rootNames = listOf(MAIN_SOURCE, "/sources/MAIN.ts"),
            )
        }
    }

    @Test
    fun rejectsPathsBeyondContractLengthAndDepth() {
        val overlyLongPath =
            "/sources/" + "a".repeat(TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS) + ".json"
        val overlyDeepPath =
            "/sources/" +
                List(TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH) { "directory" }
                    .joinToString("/") +
                ".json"

        listOf(overlyLongPath, overlyDeepPath).forEach { invalidPath ->
            assertFails<IllegalArgumentException> {
                R2ProjectSnapshot.create(
                    files = validFiles() + R2ProjectTextFile(invalidPath, ""),
                    rootNames = listOf(MAIN_SOURCE),
                )
            }
        }
    }

    @Test
    fun rejectsMissingInvalidAndUndeclaredRoots() {
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles(),
                rootNames = listOf("/sources/missing.ts"),
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles(),
                rootNames = listOf(JSON_DEPENDENCY),
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles() +
                    R2ProjectTextFile("/sources/types.d.ts", "declare const value: number;\n"),
                rootNames = listOf(MAIN_SOURCE, "/sources/types.d.ts"),
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles() +
                    R2ProjectTextFile("/sources/undeclared.ts", "export {};\n"),
                rootNames = listOf(MAIN_SOURCE),
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles().filterNot { file -> file.path == DEFAULT_LIBRARY },
                rootNames = listOf(MAIN_SOURCE),
            )
        }
    }

    @Test
    fun defensivelyCopiesMutableInputsAndExposesUnmodifiableViews() {
        val inputFiles = validFiles().toMutableList()
        val inputRoots = mutableListOf(MAIN_SOURCE)
        val snapshot = R2ProjectSnapshot.create(inputFiles, inputRoots)

        inputFiles.clear()
        inputRoots[0] = "/sources/replaced.ts"

        assertEquals(3, snapshot.files.size)
        assertEquals("export const answer = 42;\n", snapshot.files[MAIN_SOURCE])
        assertEquals(listOf(MAIN_SOURCE), snapshot.rootNames)
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (snapshot.files as MutableMap<String, String>)[MAIN_SOURCE] = "mutated"
        }
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (snapshot.rootNames as MutableList<String>).add("/sources/extra.ts")
        }
    }

    @Test
    fun heapRawConfigIsDefensivelyCopiedExcludedFromVfsAndZeroedOnClose() {
        val rawConfig = "{\"compilerOptions\":{}}\n".toByteArray()
        val expected = rawConfig.copyOf()
        val descriptor = R2RawProjectConfigDescriptor(
            path = R2ControlledTsConfigPolicy.CONFIG_PATH,
            size = rawConfig.size.toLong(),
            sha256 = R2Digests.sha256(rawConfig),
            policyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
            policyFingerprint = R2ControlledTsConfigPolicy.fingerprint,
        )
        val snapshot = R2ProjectSnapshot.create(
            files = validFiles(),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
            rawConfigDescriptor = descriptor,
            rawConfigBytes = rawConfig,
        )
        val rawConfigField = snapshot.javaClass.getDeclaredField("rawConfigBytes").apply {
            isAccessible = true
        }
        val ownedBytes = rawConfigField.get(snapshot) as ByteArray

        assertTrue(ownedBytes !== rawConfig)
        rawConfig.fill(0x5a)
        assertArrayEquals(expected, readAll(requireNotNull(snapshot.openRawConfig())))
        assertEquals(descriptor, snapshot.rawConfigDescriptor)
        assertFalse(snapshot.filePaths.contains("/${descriptor.path}"))
        assertEquals(
            validFiles().sumOf { file -> file.text.toByteArray().size }.toLong(),
            snapshot.totalUtf8Bytes,
        )
        val invalidatedCursor = requireNotNull(snapshot.openRawConfig())

        snapshot.close()

        assertTrue(ownedBytes.all { byte -> byte == 0.toByte() })
        assertEquals(null, rawConfigField.get(snapshot))
        assertFails<IllegalStateException> { snapshot.openRawConfig() }
        assertFails<IllegalStateException> {
            invalidatedCursor.read(ByteArray(1), 0, 1)
        }
        invalidatedCursor.close()
    }

    @Test
    fun heapPackageMetadataIsIndependentDefensivelyCopiedAndZeroedOnClose() {
        val rawConfig = "{}\n".toByteArray()
        val packageJson = "{\"name\":\"controlled-project\"}\n".toByteArray()
        val expectedPackageJson = packageJson.copyOf()
        val inventory = packageMetadataInventory(packageJson)
        val snapshot = R2ProjectSnapshot.create(
            files = validFiles(),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
            rawConfigDescriptor = rawConfigDescriptor(rawConfig),
            rawConfigBytes = rawConfig,
            packageMetadataInventory = inventory,
            packageMetadataBytes = listOf(packageJson),
        )
        val packageBytesField = snapshot.javaClass
            .getDeclaredField("packageMetadataBytes")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val ownedPackageBytes =
            (packageBytesField.get(snapshot) as ArrayList<ByteArray>).single()

        assertTrue(ownedPackageBytes !== packageJson)
        packageJson.fill(0x5a)
        assertArrayEquals(
            expectedPackageJson,
            readAll(snapshot.openPackageMetadataFile(0)),
        )
        assertEquals(inventory, snapshot.packageMetadataInventory)
        assertFalse(snapshot.filePaths.contains(PACKAGE_JSON_VFS))
        assertEquals(validFiles().size, snapshot.fileInventory.size)
        assertEquals(listOf(MAIN_SOURCE), snapshot.rootNames)
        assertEquals(
            validFiles().sumOf { file -> file.text.toByteArray().size }.toLong(),
            snapshot.totalUtf8Bytes,
        )
        assertFails<IndexOutOfBoundsException> {
            snapshot.openPackageMetadataFile(1)
        }
        val invalidatedCursor = snapshot.openPackageMetadataFile(0)

        snapshot.close()

        assertTrue(ownedPackageBytes.all { byte -> byte == 0.toByte() })
        assertEquals(null, packageBytesField.get(snapshot))
        assertFails<IllegalStateException> { snapshot.openPackageMetadataFile(0) }
        assertFails<IllegalStateException> {
            invalidatedCursor.read(ByteArray(1), 0, 1)
        }
        invalidatedCursor.close()
    }

    @Test
    fun heapPackageTargetsAreIndependentBudgetedAndInvalidatedOnClose() {
        val rawConfig = "{}\n".toByteArray()
        val rootPackage = "{\"name\":\"project\"}\n".toByteArray()
        val dependencyPackage = "{\"name\":\"dependency\"}\n".toByteArray()
        val targetBytes = "export declare const answer: number;\n".toByteArray()
        val expectedTargetBytes = targetBytes.copyOf()
        val metadata = packageMetadataInventory(
            dependencyRoot = PACKAGE_DEPENDENCY_ROOT,
            rootPackageBytes = rootPackage,
            dependencyPackageBytes = dependencyPackage,
        )
        val targets = R2PackageTargetInventory.create(
            packageMetadataInventory = metadata,
            files = listOf(
                R2PackageTargetFileDescriptor(
                    path = PACKAGE_TARGET_ARCHIVE,
                    size = targetBytes.size.toLong(),
                    sha256 = R2Digests.sha256(targetBytes),
                ),
            ),
        )
        val snapshot = R2ProjectSnapshot.create(
            files = validFiles(),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            limits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
            rawConfigDescriptor = rawConfigDescriptor(rawConfig),
            rawConfigBytes = rawConfig,
            packageMetadataInventory = metadata,
            packageMetadataBytes = listOf(rootPackage, dependencyPackage),
            packageTargetInventory = targets,
            packageTargetBytes = listOf(targetBytes),
        )
        val targetBytesField = snapshot.javaClass
            .getDeclaredField("packageTargetBytes")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val ownedTargetBytes =
            (targetBytesField.get(snapshot) as ArrayList<ByteArray>).single()

        assertTrue(ownedTargetBytes !== targetBytes)
        targetBytes.fill(0x5a)
        assertArrayEquals(expectedTargetBytes, readAll(snapshot.openPackageTargetFile(0)))
        assertEquals(targets, snapshot.packageTargetInventory)
        assertFalse(snapshot.filePaths.contains(PACKAGE_TARGET_VFS))
        assertEquals(validFiles().size, snapshot.fileInventory.size)
        assertEquals(listOf(MAIN_SOURCE), snapshot.rootNames)
        val admission = R2ProjectSnapshot.ordinaryCompilationAdmission(snapshot)
        val callerVfs = snapshot.fileInventory.filterNot { file ->
            file.path.startsWith("/declarations/")
        }
        assertEquals(callerVfs.size + 4, admission.capacity.fileCount)
        assertEquals(
            callerVfs.sumOf { file -> file.utf8Bytes } + rawConfig.size + rootPackage.size +
                dependencyPackage.size + expectedTargetBytes.size,
            admission.capacity.totalUtf8Bytes,
        )
        assertFails<IndexOutOfBoundsException> { snapshot.openPackageTargetFile(1) }
        val invalidatedCursor = snapshot.openPackageTargetFile(0)

        snapshot.close()

        assertTrue(ownedTargetBytes.all { byte -> byte == 0.toByte() })
        assertEquals(null, targetBytesField.get(snapshot))
        assertFails<IllegalStateException> { snapshot.openPackageTargetFile(0) }
        assertFails<IllegalStateException> {
            invalidatedCursor.read(ByteArray(1), 0, 1)
        }
        invalidatedCursor.close()
    }

    @Test
    fun nodeRevisionTwoHeapV2TargetsAreIndependentBudgetedAndInvalidatedOnClose() {
        val rootPackage = "{\"type\":\"module\"}\n".toByteArray()
        val dependencyPackage = "{\"type\":\"module\"}\n".toByteArray()
        val metadata = packageMetadataInventory(
            dependencyRoot = PACKAGE_DEPENDENCY_ROOT,
            rootPackageBytes = rootPackage,
            dependencyPackageBytes = dependencyPackage,
        )
        val targetBytes = listOf(
            "export const a = 1;\n",
            "export const b = 2;\n",
            "export const c = 3;\n",
            "export declare const d: 4;\n",
            "export declare const e: 5;\n",
            "export declare const f: 6;\n",
        ).map { text -> text.toByteArray() }
        val targetPaths = listOf(
            "$PACKAGE_DEPENDENCY_ROOT/a.ts",
            "$PACKAGE_DEPENDENCY_ROOT/b.mts",
            "$PACKAGE_DEPENDENCY_ROOT/c.cts",
            "$PACKAGE_DEPENDENCY_ROOT/d.d.ts",
            "$PACKAGE_DEPENDENCY_ROOT/e.d.mts",
            "$PACKAGE_DEPENDENCY_ROOT/f.d.cts",
        )
        val targets = R2NodePackageTargetV2Inventory.create(
            packageMetadataInventory = metadata,
            files = targetPaths.mapIndexed { index, path ->
                R2NodePackageTargetV2FileDescriptor(
                    path = path,
                    size = targetBytes[index].size.toLong(),
                    sha256 = R2Digests.sha256(targetBytes[index]),
                )
            },
        )
        val expectedTargetBytes = targetBytes.map(ByteArray::copyOf)
        val snapshot = R2ProjectSnapshot.create(
            files = validFiles(),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            limits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
            packageMetadataInventory = metadata,
            packageMetadataBytes = listOf(rootPackage, dependencyPackage),
            nodePackageTargetV2Inventory = targets,
            nodePackageTargetV2Bytes = targetBytes,
        )
        val targetBytesField = snapshot.javaClass
            .getDeclaredField("nodePackageTargetV2Bytes")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val ownedTargetBytes =
            targetBytesField.get(snapshot) as ArrayList<ByteArray>

        assertTrue(ownedTargetBytes.indices.all { index ->
            ownedTargetBytes[index] !== targetBytes[index]
        })
        targetBytes.forEach { bytes -> bytes.fill(0x5a) }
        expectedTargetBytes.forEachIndexed { index, expected ->
            assertArrayEquals(expected, readAll(snapshot.openNodePackageTargetV2File(index)))
        }
        assertEquals(targets, snapshot.nodePackageTargetV2Inventory)
        assertEquals(null, snapshot.packageTargetInventory)
        assertTrue(snapshot.nodePackageMetadataCatalog != null)
        assertTrue(targetPaths.none(snapshot.filePaths::contains))
        val admission = R2ProjectSnapshot.ordinaryCompilationAdmission(snapshot)
        val callerVfs = snapshot.fileInventory.filterNot { file ->
            file.path.startsWith("/declarations/")
        }
        assertEquals(
            callerVfs.size + metadata.files.size + targets.files.size,
            admission.capacity.fileCount,
        )
        assertEquals(
            callerVfs.sumOf { file -> file.utf8Bytes } +
                metadata.totalBytes + targets.totalBytes,
            admission.capacity.totalUtf8Bytes,
        )
        assertFails<IndexOutOfBoundsException> {
            snapshot.openNodePackageTargetV2File(targets.files.size)
        }
        val invalidatedCursor = snapshot.openNodePackageTargetV2File(0)

        snapshot.close()

        assertTrue(ownedTargetBytes.all { bytes ->
            bytes.all { byte -> byte == 0.toByte() }
        })
        assertEquals(null, targetBytesField.get(snapshot))
        assertFails<IllegalStateException> { snapshot.openNodePackageTargetV2File(0) }
        assertFails<IllegalStateException> {
            invalidatedCursor.read(ByteArray(1), 0, 1)
        }
        invalidatedCursor.close()
    }

    @Test
    fun nodeRevisionTwoHeapV2TargetsRejectMetadataIdentityDrift() {
        val rootPackage = "{}\n".toByteArray()
        val dependencyPackage = "{\"name\":\"dependency\"}\n".toByteArray()
        val metadata = packageMetadataInventory(
            dependencyRoot = PACKAGE_DEPENDENCY_ROOT,
            rootPackageBytes = rootPackage,
            dependencyPackageBytes = dependencyPackage,
        )
        val targetBytes = "export const value = 1;\n".toByteArray()
        val targets = R2NodePackageTargetV2Inventory.create(
            packageMetadataInventory = metadata,
            files = listOf(
                R2NodePackageTargetV2FileDescriptor(
                    path = "$PACKAGE_DEPENDENCY_ROOT/index.mts",
                    size = targetBytes.size.toLong(),
                    sha256 = R2Digests.sha256(targetBytes),
                ),
            ),
        )
        val driftedDependencyPackage = "{\"name\":\"drifted\"}\n".toByteArray()
        val driftedMetadata = packageMetadataInventory(
            dependencyRoot = PACKAGE_DEPENDENCY_ROOT,
            rootPackageBytes = rootPackage,
            dependencyPackageBytes = driftedDependencyPackage,
        )

        assertFails<R2PackageTargetPolicyException> {
            R2ProjectSnapshot.create(
                files = validFiles(),
                rootNames = listOf(MAIN_SOURCE),
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
                packageMetadataInventory = driftedMetadata,
                packageMetadataBytes = listOf(rootPackage, driftedDependencyPackage),
                nodePackageTargetV2Inventory = targets,
                nodePackageTargetV2Bytes = listOf(targetBytes),
            )
        }
    }

    @Test
    fun heapPackageTargetsRequireExactC5BindingAndValidatedBytes() {
        val rawConfig = "{}".toByteArray()
        val rootPackage = "{}".toByteArray()
        val dependencyPackage = "{\"name\":\"dependency\"}".toByteArray()
        val metadata = packageMetadataInventory(
            dependencyRoot = PACKAGE_DEPENDENCY_ROOT,
            rootPackageBytes = rootPackage,
            dependencyPackageBytes = dependencyPackage,
        )
        val targetBytes = "export declare const value: string;".toByteArray()
        val targets = R2PackageTargetInventory.create(
            packageMetadataInventory = metadata,
            files = listOf(
                R2PackageTargetFileDescriptor(
                    path = PACKAGE_TARGET_ARCHIVE,
                    size = targetBytes.size.toLong(),
                    sha256 = R2Digests.sha256(targetBytes),
                ),
            ),
        )
        fun create(
            acceptedMetadata: R2PackageMetadataInventory? = metadata,
            acceptedMetadataBytes: List<ByteArray> = listOf(rootPackage, dependencyPackage),
            acceptedTargetBytes: List<ByteArray> = listOf(targetBytes),
        ) = R2ProjectSnapshot.create(
            files = validFiles(),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
            rawConfigDescriptor = rawConfigDescriptor(rawConfig),
            rawConfigBytes = rawConfig,
            packageMetadataInventory = acceptedMetadata,
            packageMetadataBytes = acceptedMetadataBytes,
            packageTargetInventory = targets,
            packageTargetBytes = acceptedTargetBytes,
        )

        assertFails<IllegalArgumentException> {
            create(acceptedMetadata = null, acceptedMetadataBytes = emptyList())
        }
        assertFails<IllegalArgumentException> {
            create(acceptedTargetBytes = listOf("changed".toByteArray()))
        }
        assertFails<IllegalArgumentException> {
            create(acceptedTargetBytes = emptyList())
        }
    }

    @Test
    fun packageMetadataRequiresControlledConfigAndExactValidatedBytes() {
        val rawConfig = "{}".toByteArray()
        val packageJson = "{}".toByteArray()
        val scriptedPackage = "{\"scripts\":{}}".toByteArray()
        val inventory = packageMetadataInventory(packageJson)

        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles(),
                rootNames = listOf(MAIN_SOURCE),
                profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
                packageMetadataInventory = inventory,
                packageMetadataBytes = listOf(packageJson),
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles(),
                rootNames = listOf(MAIN_SOURCE),
                profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
                rawConfigDescriptor = rawConfigDescriptor(rawConfig),
                rawConfigBytes = rawConfig,
                packageMetadataInventory = inventory,
                packageMetadataBytes = listOf("{\"changed\":true}".toByteArray()),
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles(),
                rootNames = listOf(MAIN_SOURCE),
                profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
                rawConfigDescriptor = rawConfigDescriptor(rawConfig),
                rawConfigBytes = rawConfig,
                packageMetadataInventory = inventory,
                packageMetadataBytes = emptyList(),
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles(),
                rootNames = listOf(MAIN_SOURCE),
                profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
                rawConfigDescriptor = rawConfigDescriptor(rawConfig),
                rawConfigBytes = rawConfig,
                packageMetadataInventory = packageMetadataInventory(scriptedPackage),
                packageMetadataBytes = listOf(scriptedPackage),
            )
        }
    }

    @Test
    fun packageMetadataConsumesOrdinaryCapacityButNotProjectVfsInventory() {
        val rawConfig = "{}".toByteArray()
        val packageJson = "{}".toByteArray()
        val snapshot = R2ProjectSnapshot.create(
            files = validFiles(),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            limits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
            rawConfigDescriptor = rawConfigDescriptor(rawConfig),
            rawConfigBytes = rawConfig,
            packageMetadataInventory = packageMetadataInventory(packageJson),
            packageMetadataBytes = listOf(packageJson),
        )

        val admission = R2ProjectSnapshot.ordinaryCompilationAdmission(snapshot)
        val callerVfs = snapshot.fileInventory.filterNot { file ->
            file.path.startsWith("/declarations/")
        }
        assertEquals(callerVfs.size + 2, admission.capacity.fileCount)
        assertEquals(
            callerVfs.sumOf { file -> file.utf8Bytes } + rawConfig.size + packageJson.size,
            admission.capacity.totalUtf8Bytes,
        )
        assertEquals(validFiles().size, snapshot.fileInventory.size)
        assertFalse(snapshot.filePaths.contains(PACKAGE_JSON_VFS))
        snapshot.close()
    }

    @Test
    fun callerFilesAndRawConfigConsumeOrdinaryQuotaButTrustedDeclarationsDoNot() {
        val emptyRawConfig = ByteArray(0)
        val emptyDescriptor = rawConfigDescriptor(emptyRawConfig)
        val exactFileInventory = buildList {
            add(R2ProjectTextFile(DEFAULT_LIBRARY, ""))
            add(R2ProjectTextFile(MAIN_SOURCE, ""))
            repeat(TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES - 2) { index ->
                add(R2ProjectTextFile("/sources/$index.json", ""))
            }
        }
        val exactFileCount = R2ProjectSnapshot.create(
            files = exactFileInventory,
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            limits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE,
            rawConfigDescriptor = emptyDescriptor,
            rawConfigBytes = emptyRawConfig,
        )
        assertEquals(512, exactFileCount.fileInventory.size)
        assertTrue(R2ProjectSnapshot.ordinaryCompilationAdmission(exactFileCount).isEligible)

        val oneFileTooMany = R2ProjectSnapshot.create(
            files = exactFileInventory + R2ProjectTextFile("/sources/overflow.json", ""),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            limits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE,
            rawConfigDescriptor = emptyDescriptor,
            rawConfigBytes = emptyRawConfig,
        )
        assertEquals(
            R2ProjectInputCapacityDimension.FILE_COUNT,
            R2ProjectSnapshot.ordinaryCompilationAdmission(oneFileTooMany).exceededDimension,
        )

        val maximumRawConfig = ByteArray(TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES)
        val exactSourceBytes =
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES.toInt() -
                maximumRawConfig.size
        val trustedDeclaration = "declare const pluginOwned: number;\n"
        val exactBytes = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(DEFAULT_LIBRARY, trustedDeclaration),
                R2ProjectTextFile(MAIN_SOURCE, "a".repeat(exactSourceBytes)),
            ),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            limits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE,
            rawConfigDescriptor = rawConfigDescriptor(maximumRawConfig),
            rawConfigBytes = maximumRawConfig,
        )
        val exactByteAdmission = R2ProjectSnapshot.ordinaryCompilationAdmission(exactBytes)
        assertTrue(exactByteAdmission.isEligible)
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES,
            exactByteAdmission.capacity.totalUtf8Bytes,
        )
        assertEquals(
            exactSourceBytes + trustedDeclaration.toByteArray().size.toLong(),
            exactBytes.totalUtf8Bytes,
        )
        assertTrue(
            exactBytes.totalUtf8Bytes + maximumRawConfig.size >
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES,
        )

        val oneByteTooMany = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(DEFAULT_LIBRARY, trustedDeclaration),
                R2ProjectTextFile(MAIN_SOURCE, "a".repeat(exactSourceBytes)),
                R2ProjectTextFile(JSON_DEPENDENCY, "x"),
            ),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            limits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE,
            rawConfigDescriptor = rawConfigDescriptor(maximumRawConfig),
            rawConfigBytes = maximumRawConfig,
        )
        assertEquals(
            R2ProjectInputCapacityDimension.TOTAL_BYTES,
            R2ProjectSnapshot.ordinaryCompilationAdmission(oneByteTooMany).exceededDimension,
        )
        exactFileCount.close()
        oneFileTooMany.close()
        exactBytes.close()
        oneByteTooMany.close()
    }

    @Test
    fun enforcesFileCountSingleFileAndTotalUtf8ByteQuotas() {
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles(),
                rootNames = listOf(MAIN_SOURCE),
                limits = testLimits(maximumFiles = 2),
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = listOf(
                    R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                    R2ProjectTextFile(MAIN_SOURCE, "\u00e9\u00e9\u00e9"),
                ),
                rootNames = listOf(MAIN_SOURCE),
                limits = testLimits(maximumSingleFileBytes = 5),
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = listOf(
                    R2ProjectTextFile(DEFAULT_LIBRARY, "a"),
                    R2ProjectTextFile(MAIN_SOURCE, "bc"),
                    R2ProjectTextFile("/sources/second.ts", "de"),
                ),
                rootNames = listOf(MAIN_SOURCE, "/sources/second.ts"),
                limits = testLimits(maximumTotalBytes = 4),
            )
        }
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = validFiles(),
                rootNames = List(4) { MAIN_SOURCE },
                limits = testLimits(maximumFiles = 3),
            )
        }
    }

    @Test
    fun defaultAdmissionUsesTheProvisionalSingleMessageBudget() {
        assertEquals(
            256L * 1024L,
            R2ProjectSnapshotLimits.PROVISIONAL_WEB_MESSAGE.maximumSingleFileBytes,
        )
        assertEquals(
            512L * 1024L,
            R2ProjectSnapshotLimits.PROVISIONAL_WEB_MESSAGE.maximumTotalBytes,
        )
        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.create(
                files = listOf(
                    R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                    R2ProjectTextFile(MAIN_SOURCE, "a".repeat(256 * 1024 + 1)),
                ),
                rootNames = listOf(MAIN_SOURCE),
            )
        }
    }

    @Test
    fun archiveBudgetSnapshotCannotAccidentallyEnterTheSingleWebMessageProtocol() {
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                R2ProjectTextFile(MAIN_SOURCE, "a".repeat(256 * 1024 + 1)),
            ),
            rootNames = listOf(MAIN_SOURCE),
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
        )

        assertFails<IllegalArgumentException> {
            R2ProjectSnapshot.requireProvisionalWebMessageEligibility(snapshot)
        }
    }

    @Test
    fun acceptsSourceDeclarationFilesWithoutTreatingThemAsEmitRoots() {
        val sourceDeclaration = "/sources/types.d.ts"
        val snapshot = R2ProjectSnapshot.create(
            files = validFiles() +
                R2ProjectTextFile(sourceDeclaration, "export interface Value {}\n"),
            rootNames = listOf(MAIN_SOURCE),
        )

        assertTrue(snapshot.files.containsKey(sourceDeclaration))
        assertEquals(listOf(MAIN_SOURCE), snapshot.rootNames)
    }

    @Test
    fun canonicalizesRootOrderForTransportAndDiagnosticDeterminism() {
        val first = "/sources/a.ts"
        val second = "/sources/z.ts"
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                R2ProjectTextFile(second, "export const z = 1;\n"),
                R2ProjectTextFile(first, "export const a = 1;\n"),
            ),
            rootNames = listOf(second, first),
        )

        assertEquals(listOf(first, second), snapshot.rootNames)
    }

    @Test
    fun acceptsExactInclusiveQuotaBoundaries() {
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(DEFAULT_LIBRARY, "a"),
                R2ProjectTextFile(MAIN_SOURCE, "\u00e9"),
            ),
            rootNames = listOf(MAIN_SOURCE),
            limits = testLimits(
                maximumFiles = 2,
                maximumSingleFileBytes = 2,
                maximumTotalBytes = 3,
            ),
        )

        assertEquals(2, snapshot.files.size)
        assertTrue(snapshot.files.containsKey(DEFAULT_LIBRARY))
    }

    @Test
    fun fileBackedNativeCapacityRemainsSeparateFromOrdinaryCompilationAdmission() {
        val fileBacked = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE
        val ordinary = R2ProjectSnapshotLimits.ORDINARY_COMPILATION

        assertEquals(
            TypeScriptCompilerContract.MAX_INPUT_FILES - 1 +
                R2_TRUSTED_DECLARATION_MAXIMUM_FILES,
            fileBacked.maximumFiles,
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_INPUT_BYTES +
                R2_TRUSTED_DECLARATION_MAXIMUM_BYTES,
            fileBacked.maximumTotalBytes,
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES,
            ordinary.maximumFiles,
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
            ordinary.maximumSingleFileBytes,
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES,
            ordinary.maximumTotalBytes,
        )
        assertTrue(fileBacked.maximumFiles > ordinary.maximumFiles)
        assertTrue(fileBacked.maximumTotalBytes > ordinary.maximumTotalBytes)
    }

    @Test
    fun ordinaryCompilationAdmissionAcceptsItsExactInclusiveCapacity() {
        val admission = R2ProjectInputCapacity(
            fileCount = TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES,
            largestFileUtf8Bytes =
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
            totalUtf8Bytes = TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES,
        ).admissionAgainst(R2ProjectSnapshotLimits.ORDINARY_COMPILATION)

        assertTrue(admission.isEligible)
        assertEquals(null, admission.exceededDimension)
        assertEquals(
            R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
            admission.limits,
        )
    }

    @Test
    fun ordinaryCompilationAdmissionReportsTheFirstExceededCapacityDimension() {
        val ordinary = R2ProjectSnapshotLimits.ORDINARY_COMPILATION
        val allDimensionsExceeded = R2ProjectInputCapacity(
            fileCount = ordinary.maximumFiles + 1,
            largestFileUtf8Bytes = ordinary.maximumSingleFileBytes + 1,
            totalUtf8Bytes = ordinary.maximumTotalBytes + 1,
        )
        val byteDimensionsExceeded = allDimensionsExceeded.copy(
            fileCount = ordinary.maximumFiles,
        )
        val totalBytesExceeded = byteDimensionsExceeded.copy(
            largestFileUtf8Bytes = ordinary.maximumSingleFileBytes,
        )

        assertEquals(
            R2ProjectInputCapacityDimension.FILE_COUNT,
            allDimensionsExceeded.admissionAgainst(ordinary).exceededDimension,
        )
        assertEquals(
            R2ProjectInputCapacityDimension.SINGLE_FILE_BYTES,
            byteDimensionsExceeded.admissionAgainst(ordinary).exceededDimension,
        )
        assertEquals(
            R2ProjectInputCapacityDimension.TOTAL_BYTES,
            totalBytesExceeded.admissionAgainst(ordinary).exceededDimension,
        )
        assertFalse(totalBytesExceeded.admissionAgainst(ordinary).isEligible)
    }

    @Suppress("DEPRECATION")
    @Test
    fun provisionalWorkerNamesRemainExactCompatibilityAliases() {
        assertTrue(
            R2ProjectSnapshotLimits.PROVISIONAL_WORKER ===
                R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES,
            R2_PROVISIONAL_WORKER_MAXIMUM_FILES,
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
            R2_PROVISIONAL_WORKER_MAXIMUM_SINGLE_FILE_BYTES,
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES,
            R2_PROVISIONAL_WORKER_MAXIMUM_TOTAL_BYTES,
        )
    }

    private fun validFiles() = listOf(
        R2ProjectTextFile(DEFAULT_LIBRARY, "interface Array<T> { length: number }\n"),
        R2ProjectTextFile(MAIN_SOURCE, "export const answer = 42;\n"),
        R2ProjectTextFile(JSON_DEPENDENCY, "{\"enabled\":true}\n"),
    )

    private fun testLimits(
        maximumFiles: Int = 8,
        maximumSingleFileBytes: Long = 128,
        maximumTotalBytes: Long = 512,
    ) = R2ProjectSnapshotLimits(
        maximumFiles = maximumFiles,
        maximumSingleFileBytes = maximumSingleFileBytes,
        maximumTotalBytes = maximumTotalBytes,
    )

    private fun rawConfigDescriptor(bytes: ByteArray) = R2RawProjectConfigDescriptor(
        path = R2ControlledTsConfigPolicy.CONFIG_PATH,
        size = bytes.size.toLong(),
        sha256 = R2Digests.sha256(bytes),
        policyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
        policyFingerprint = R2ControlledTsConfigPolicy.fingerprint,
    )

    private fun packageMetadataInventory(bytes: ByteArray) =
        R2PackageMetadataInventory.create(
            dependencyRoots = emptyList(),
            files = listOf(
                R2PackageMetadataFileDescriptor(
                    path = PACKAGE_JSON_ARCHIVE,
                    size = bytes.size.toLong(),
                    sha256 = R2Digests.sha256(bytes),
                ),
            ),
        )

    private fun packageMetadataInventory(
        dependencyRoot: String,
        rootPackageBytes: ByteArray,
        dependencyPackageBytes: ByteArray,
    ) = R2PackageMetadataInventory.create(
        dependencyRoots = listOf(dependencyRoot),
        files = listOf(
            R2PackageMetadataFileDescriptor(
                path = PACKAGE_JSON_ARCHIVE,
                size = rootPackageBytes.size.toLong(),
                sha256 = R2Digests.sha256(rootPackageBytes),
            ),
            R2PackageMetadataFileDescriptor(
                path = "$dependencyRoot/package.json",
                size = dependencyPackageBytes.size.toLong(),
                sha256 = R2Digests.sha256(dependencyPackageBytes),
            ),
        ),
    )

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

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError(
                "Expected ${T::class.java.name}, got ${error::class.java.name}.",
                error,
            )
        }
        fail("Expected ${T::class.java.name}.")
        error("unreachable")
    }

    private companion object {
        const val DEFAULT_LIBRARY = "/declarations/lib.d.ts"
        const val MAIN_SOURCE = "/sources/main.ts"
        const val JSON_DEPENDENCY = "/sources/config.json"
        const val PACKAGE_JSON_ARCHIVE = "package.json"
        const val PACKAGE_JSON_VFS = "/package.json"
        const val PACKAGE_DEPENDENCY_ROOT = "packages/dependency"
        const val PACKAGE_TARGET_ARCHIVE = "$PACKAGE_DEPENDENCY_ROOT/index.d.ts"
        const val PACKAGE_TARGET_VFS = "/$PACKAGE_TARGET_ARCHIVE"
    }
}
