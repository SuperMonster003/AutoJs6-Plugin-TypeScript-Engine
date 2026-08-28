package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Limits
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class R2ProjectArchiveTest {

    @Test
    fun validManifestIsCrossBoundAndAdaptedWithOnlyTrustedDeclarations() {
        val fixture = validFixture()

        val adapted = R2ProjectArchiveAdapter.adapt(
            authority = fixture.authority,
            actualArchiveSha256 = fixture.authority.archiveSha256,
            archiveEntries = fixture.archiveEntries,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint =
                fixture.authority.runtimeCapabilitiesFingerprint,
        )

        assertEquals(
            listOf(
                "/sources/config.json",
                "/sources/helper.ts",
                "/sources/main.ts",
                DEFAULT_LIBRARY_VFS_PATH,
            ),
            adapted.snapshot.filePaths.toList(),
        )
        assertEquals(
            listOf("/sources/helper.ts", "/sources/main.ts"),
            adapted.snapshot.rootNames,
        )
        assertEquals(MAIN_SOURCE, adapted.snapshot.readText("/sources/main.ts"))
        assertEquals("/sources/main.ts", adapted.snapshot.entryPath)
        assertEquals(
            DEFAULT_LIBRARY,
            adapted.snapshot.readText(DEFAULT_LIBRARY_VFS_PATH),
        )
        assertEquals("sources/main.ts", adapted.manifest.entryPath)
        assertEquals(
            R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            adapted.manifest.profileAuthority,
        )
        assertEquals(
            adapted.manifest.profileAuthority,
            adapted.snapshot.profileAuthority,
        )
        assertEquals(
            fixture.authority.requestManifestSha256,
            adapted.requestManifestSha256,
        )
        assertEquals(fixture.authority.archiveSha256, adapted.archiveSha256)
        assertEquals(adapted.acceptedInputAuthority, adapted.snapshot.acceptedInputAuthority)
        assertEquals(
            adapted.manifest.callerInventoryFingerprint,
            adapted.acceptedInputAuthority.callerInventoryFingerprint,
        )
        assertEquals(
            fixture.trustedDeclarations.profileFingerprint,
            adapted.acceptedInputAuthority.declarationProfileFingerprint,
        )
        assertEquals(
            adapted.snapshot.fileInventory.size,
            adapted.acceptedInputAuthority.capacity.fileCount,
        )
        assertFalse(adapted.manifest.files.any { it.path.startsWith("declarations/") })
        assertEquals(null, adapted.manifest.rawConfigDescriptor)
        assertEquals(null, adapted.snapshot.rawConfigDescriptor)
        assertEquals(null, adapted.snapshot.openRawConfig())

        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (adapted.manifest.rootPaths as MutableList<String>).clear()
        }
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (adapted.manifest.files as MutableList<R2ProjectArchiveFile>).clear()
        }
    }

    @Test
    fun inputQuotaRevisionTwoRemainsReadableAndUnknownRevisionsAreRejected() {
        val currentMarker =
            "\"inputQuotaRevision\":${R2InputArchiveSchemaV1.INPUT_QUOTA_REVISION}"
        val legacyMarker =
            "\"inputQuotaRevision\":" +
                TypeScriptR2Contract.LEGACY_ORDINARY_COMPILATION_INPUT_QUOTA_REVISION
        val legacy = validFixture(
            mutateManifest = { manifest -> manifest.replace(currentMarker, legacyMarker) },
        )

        adapt(legacy).use { adapted ->
            assertEquals(
                TypeScriptR2Contract.LEGACY_ORDINARY_COMPILATION_INPUT_QUOTA_REVISION,
                adapted.manifest.inputQuotaRevision,
            )
            assertEquals(
                adapted.manifest.inputQuotaRevision,
                adapted.acceptedInputAuthority.inputQuotaRevision,
            )
        }

        val unknown = validFixture(
            mutateManifest = { manifest ->
                manifest.replace(currentMarker, "\"inputQuotaRevision\":4")
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH) {
            adapt(unknown)
        }
    }

    @Test
    fun acceptedEntryIdentityIsPreservedInsteadOfBeingInferredFromSortedRoots() {
        val helperEntry = "sources/helper.ts"
        val fixture = validFixture(
            mutateManifest = { manifest ->
                manifest.replace(
                    "\"entryPath\":\"sources/main.ts\"",
                    "\"entryPath\":\"$helperEntry\"",
                )
            },
        )

        adapt(fixture).use { adapted ->
            assertEquals(helperEntry, adapted.manifest.entryPath)
            assertEquals("/$helperEntry", adapted.snapshot.entryPath)
            assertEquals(
                listOf("/sources/helper.ts", "/sources/main.ts"),
                adapted.snapshot.rootNames,
            )
        }

        listOf("sources/missing.ts", "sources/config.json").forEach { invalidEntry ->
            val invalid = validFixture(
                mutateManifest = { manifest ->
                    manifest.replace(
                        "\"entryPath\":\"sources/main.ts\"",
                        "\"entryPath\":\"$invalidEntry\"",
                    )
                },
            )
            assertArchiveFailure(TypeScriptCompilerContract.ERROR_INVALID_REQUEST) {
                adapt(invalid)
            }
        }
    }

    @Test
    fun revision3RawConfigIsTripleBoundAndKeptOutsideTheTextVfs() {
        val rawConfig = "{\"compilerOptions\":{\"strict\":true}}\n"
            .toByteArray(StandardCharsets.UTF_8)
        val expectedRawConfig = rawConfig.copyOf()
        val fixture = validFixture(rawConfigBytes = rawConfig)

        val adapted = adapt(fixture)
        rawConfig.fill(0x5a)

        val expectedAuthority = requireNotNull(fixture.authority.rawConfigAuthority)
        assertEquals(expectedAuthority.descriptor(), adapted.manifest.rawConfigDescriptor)
        assertEquals(adapted.manifest.rawConfigDescriptor, adapted.snapshot.rawConfigDescriptor)
        assertFalse(adapted.snapshot.filePaths.contains("/${expectedAuthority.path}"))
        assertFalse(adapted.snapshot.fileInventory.any { file ->
            file.path == "/${expectedAuthority.path}"
        })
        assertArrayEquals(expectedRawConfig, readAll(requireNotNull(adapted.snapshot.openRawConfig())))
        adapted.close()
        assertFails<IllegalStateException> { adapted.snapshot.openRawConfig() }
    }

    @Test
    fun revision3RejectsRawConfigIdentityPolicyAndQuotaMutations() {
        val rawConfig = "{}\n".toByteArray(StandardCharsets.UTF_8)
        val valid = validFixture(rawConfigBytes = rawConfig)

        val changedActualBytes = LinkedHashMap(valid.archiveEntries).apply {
            put(R2ControlledTsConfigPolicy.CONFIG_PATH, "[]\n".toByteArray())
        }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            R2ProjectArchiveAdapter.adapt(
                authority = valid.authority,
                actualArchiveSha256 = valid.authority.archiveSha256,
                archiveEntries = changedActualBytes,
                trustedDeclarations = valid.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }

        val changedPolicy = validFixture(
            rawConfigBytes = rawConfig,
            mutateManifest = { manifest ->
                manifest.replace(
                    R2ControlledTsConfigPolicy.revision4Fingerprint,
                    "0".repeat(64),
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH) {
            adapt(changedPolicy)
        }

        val oversizedActualBytes = LinkedHashMap(valid.archiveEntries).apply {
            put(
                R2ControlledTsConfigPolicy.CONFIG_PATH,
                ByteArray(TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES + 1),
            )
        }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA) {
            R2ProjectArchiveAdapter.adapt(
                authority = valid.authority,
                actualArchiveSha256 = valid.authority.archiveSha256,
                archiveEntries = oversizedActualBytes,
                trustedDeclarations = valid.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }

        val inclusive = validFixture(
            rawConfigBytes = ByteArray(TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES),
        )
        adapt(inclusive).use { adapted ->
            assertEquals(
                TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES.toLong(),
                adapted.snapshot.rawConfigDescriptor?.size,
            )
        }
    }

    @Test
    fun revision3ArchiveSourceInventoryExcludesManifestAndHonorsConfigEntryBudget() {
        val rawConfig = ByteArray(0)
        val rawAuthority = R2RawProjectConfigAuthority(
            size = 0,
            sha256 = R2Digests.sha256(rawConfig),
        )
        val maximumFiles = buildList {
            repeat(R2ProvisionalRequestManifest.MAXIMUM_CONTROLLED_TSCONFIG_SOURCE_FILES - 1) {
                    index ->
                add(
                    R2ProjectArchiveFile(
                        path = "sources/${index.toString().padStart(4, '0')}",
                        size = 0,
                        sha256 = EMPTY_SHA256,
                    ),
                )
            }
            add(
                R2ProjectArchiveFile(
                    path = "sources/main.ts",
                    size = 0,
                    sha256 = EMPTY_SHA256,
                ),
            )
        }.sortedBy(R2ProjectArchiveFile::path)
        val maximumManifest = manifest(maximumFiles, rawAuthority)
            .toByteArray(StandardCharsets.UTF_8)
        val authority = validFixture(rawConfigBytes = rawConfig).authority.copy(
            requestManifestSha256 = R2Digests.sha256(maximumManifest),
            rawConfigAuthority = rawAuthority,
        )

        val parsed = R2ProvisionalRequestManifest.parse(maximumManifest, authority)
        assertEquals(
            TypeScriptCompilerContract.MAX_INPUT_FILES - 1,
            parsed.files.size,
        )

        val maximumWithoutConfig = maximumFiles +
            R2ProjectArchiveFile(
                path = "sources/zzzz",
                size = 0,
                sha256 = EMPTY_SHA256,
            )
        val maximumWithoutConfigManifest = manifest(maximumWithoutConfig)
            .toByteArray(StandardCharsets.UTF_8)
        val maximumWithoutConfigAuthority = validFixture().authority.copy(
            requestManifestSha256 = R2Digests.sha256(maximumWithoutConfigManifest),
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_INPUT_FILES,
            R2ProvisionalRequestManifest.parse(
                maximumWithoutConfigManifest,
                maximumWithoutConfigAuthority,
            ).files.size,
        )

        val beyondMaximum = maximumWithoutConfig +
            R2ProjectArchiveFile(
                path = "sources/zzzzz",
                size = 0,
                sha256 = EMPTY_SHA256,
            )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA) {
            R2ProvisionalRequestManifest.parse(
                manifest(beyondMaximum).toByteArray(StandardCharsets.UTF_8),
                maximumWithoutConfigAuthority,
            )
        }
    }

    @Test
    fun nodeDependencyLayerIsAuthenticatedReadableAndExcludedFromCallerCapacity() {
        val dependencyEntries = linkedMapOf(
            "dependencies/lockfiles/package-lock.json" to
                "{\"lockfileVersion\":3}".toByteArray(),
            "dependencies/node_modules/dayjs/package.json" to
                "{\"name\":\"dayjs\",\"scripts\":{\"test\":\"ignored\"}}"
                    .toByteArray(),
            "dependencies/node_modules/lodash/index.js" to
                "export const lodash = true;\n".toByteArray(),
            "dependencies/node_modules/zod/index.d.ts" to
                "export declare const zod: unique symbol;\n".toByteArray(),
        )
        val profile = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        val withoutDependencies = validFixture(profileAuthority = profile)
        val baselineCallerCapacity = adapt(withoutDependencies).use { baseline ->
            requireNotNull(baseline.snapshot.acceptedInputAuthority).callerCapacity
        }
        val fixture = validFixture(
            dependencyLayerEntries = dependencyEntries,
            profileAuthority = profile,
        )

        val adapted = adapt(fixture)
        try {
            val inventory = requireNotNull(adapted.manifest.dependencyLayerInventory)
            assertEquals(inventory, adapted.snapshot.dependencyLayerInventory)
            assertEquals("package-lock", inventory.identityKind)
            assertEquals(4, inventory.files.size)
            assertEquals(
                R2ProvisionalRequestManifest.parse(
                    withoutDependencies.archiveEntries.getValue(
                        R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH,
                    ),
                    withoutDependencies.authority,
                ).callerInventoryFingerprint,
                adapted.manifest.callerInventoryFingerprint,
            )
            assertEquals(
                baselineCallerCapacity,
                adapted.snapshot.acceptedInputAuthority?.callerCapacity,
            )
            inventory.files.forEachIndexed { index, descriptor ->
                assertFalse(adapted.snapshot.filePaths.contains("/${descriptor.path}"))
                assertArrayEquals(
                    dependencyEntries.getValue(descriptor.path),
                    readAll(adapted.snapshot.openDependencyLayerFile(index)),
                )
            }
        } finally {
            adapted.close()
        }
        assertFails<IllegalStateException> {
            adapted.snapshot.openDependencyLayerFile(0)
        }
    }

    @Test
    fun revision4TransportsRootUnscopedScopedAndNestedPackageMetadataOutsideTextVfs() {
        val packageEntries = defaultPackageMetadataEntries()
        val expectedBytes = packageEntries.mapValues { (_, bytes) -> bytes.copyOf() }
        val fixture = validFixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = packageEntries,
        )

        val adapted = adapt(fixture)
        val inventory = requireNotNull(adapted.manifest.packageMetadataInventory)
        assertEquals(inventory, adapted.snapshot.packageMetadataInventory)
        assertEquals(DEFAULT_DEPENDENCY_ROOTS, inventory.dependencyRoots)
        assertEquals(
            expectedBytes.keys.sorted(),
            inventory.files.map(R2PackageMetadataFileDescriptor::path),
        )
        inventory.files.forEachIndexed { index, file ->
            assertFalse(adapted.snapshot.filePaths.contains("/${file.path}"))
            assertFalse(adapted.snapshot.fileInventory.any { it.path == "/${file.path}" })
            assertArrayEquals(
                expectedBytes.getValue(file.path),
                readAll(adapted.snapshot.openPackageMetadataFile(index)),
            )
        }

        packageEntries.values.forEach { bytes -> bytes.fill(0x5a) }
        inventory.files.forEachIndexed { index, file ->
            assertArrayEquals(
                expectedBytes.getValue(file.path),
                readAll(adapted.snapshot.openPackageMetadataFile(index)),
            )
        }
        val invalidatedCursor = adapted.snapshot.openPackageMetadataFile(0)
        adapted.close()
        assertFails<IllegalStateException> { adapted.snapshot.openPackageMetadataFile(0) }
        assertFails<IllegalStateException> {
            invalidatedCursor.read(ByteArray(1), 0, 1)
        }
        invalidatedCursor.close()

        assertFails<IllegalArgumentException> {
            fixture.authority.copy(rawConfigAuthority = null)
        }
    }

    @Test
    fun revision4SourceBudgetReservesConfigAndEveryPackageMetadataFileButNotManifest() {
        val rawConfig = ByteArray(0)
        val rawAuthority = R2RawProjectConfigAuthority(
            size = 0,
            sha256 = R2Digests.sha256(rawConfig),
        )
        val packageInventory = R2PackageMetadataInventory.create(
            dependencyRoots = emptyList(),
            files = listOf(
                R2PackageMetadataFileDescriptor(
                    path = R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH,
                    size = 0,
                    sha256 = EMPTY_SHA256,
                ),
            ),
        )
        val maximumSourceFiles =
            TypeScriptCompilerContract.MAX_INPUT_FILES - 2
        val files = buildList {
            repeat(maximumSourceFiles - 1) { index ->
                add(
                    R2ProjectArchiveFile(
                        path = "sources/${index.toString().padStart(4, '0')}",
                        size = 0,
                        sha256 = EMPTY_SHA256,
                    ),
                )
            }
            add(
                R2ProjectArchiveFile(
                    path = "sources/main.ts",
                    size = 0,
                    sha256 = EMPTY_SHA256,
                ),
            )
        }.sortedBy(R2ProjectArchiveFile::path)
        val maximumManifest = manifest(files, rawAuthority, packageInventory)
            .toByteArray(StandardCharsets.UTF_8)
        val authority = validFixture(
            rawConfigBytes = rawConfig,
            packageMetadataEntries = mapOf(
                R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH to ByteArray(0),
            ),
        ).authority.copy(
            requestManifestSha256 = R2Digests.sha256(maximumManifest),
            rawConfigAuthority = rawAuthority,
            packageMetadataAuthority = packageInventory.authority,
        )

        assertEquals(
            maximumSourceFiles,
            R2ProvisionalRequestManifest.parse(maximumManifest, authority).files.size,
        )
        val beyondMaximum = files +
            R2ProjectArchiveFile(
                path = "sources/zzzz",
                size = 0,
                sha256 = EMPTY_SHA256,
            )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA) {
            R2ProvisionalRequestManifest.parse(
                manifest(beyondMaximum, rawAuthority, packageInventory)
                    .toByteArray(StandardCharsets.UTF_8),
                authority,
            )
        }
    }

    @Test
    fun revision4BindsPackageInventoryAndExactlyCoversPackageArchiveEntries() {
        val packageEntries = defaultPackageMetadataEntries()
        val valid = validFixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = packageEntries,
        )
        val packagePath = R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH

        val missing = LinkedHashMap(valid.archiveEntries).apply { remove(packagePath) }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(valid.copy(archiveEntries = missing))
        }

        val reservedExtra = LinkedHashMap(valid.archiveEntries).apply {
            put("packages/rogue/package.json", "{}\n".toByteArray())
        }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_PATH) {
            adapt(valid.copy(archiveEntries = reservedExtra))
        }

        val ordinaryExtra = LinkedHashMap(valid.archiveEntries).apply {
            put("sources/z-extra.json", "{}\n".toByteArray())
        }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(valid.copy(archiveEntries = ordinaryExtra))
        }

        val wrongSize = LinkedHashMap(valid.archiveEntries).apply {
            put(packagePath, getValue(packagePath) + ' '.code.toByte())
        }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(valid.copy(archiveEntries = wrongSize))
        }

        val wrongHash = LinkedHashMap(valid.archiveEntries).apply {
            val changed = getValue(packagePath).copyOf()
            changed[1] = if (changed[1] == 'x'.code.toByte()) {
                'y'.code.toByte()
            } else {
                'x'.code.toByte()
            }
            put(packagePath, changed)
        }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(valid.copy(archiveEntries = wrongHash))
        }

        val trustedFingerprint = requireNotNull(
            valid.authority.packageMetadataAuthority,
        ).fingerprint
        val manifestFingerprintMismatch = validFixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = defaultPackageMetadataEntries(),
            mutateManifest = { text ->
                text.replace(
                    "\"inventoryFingerprint\":\"$trustedFingerprint\"",
                    "\"inventoryFingerprint\":\"${"0".repeat(64)}\"",
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(manifestFingerprintMismatch)
        }

        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            R2ProjectArchiveAdapter.adapt(
                authority = valid.authority.copy(
                    packageMetadataAuthority = R2PackageMetadataAuthority("0".repeat(64)),
                ),
                actualArchiveSha256 = valid.authority.archiveSha256,
                archiveEntries = valid.archiveEntries,
                trustedDeclarations = valid.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }
    }

    @Test
    fun revision4RejectsPackageScriptsStrictJsonSmugglingAndInventoryAmbiguity() {
        val invalidPackageJson = listOf(
            "{\"name\":\"root\",\"scripts\":{\"build\":\"host command\"}}".toByteArray(),
            "{\"name\":\"root\",\"name\":\"duplicate\"}".toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28),
        )
        invalidPackageJson.forEach { rootPackageBytes ->
            val entries = defaultPackageMetadataEntries().apply {
                put(R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH, rootPackageBytes)
            }
            val fixture = validFixture(
                rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
                dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
                packageMetadataEntries = entries,
            )
            assertArchiveFailure(TypeScriptCompilerContract.ERROR_INVALID_REQUEST) {
                adapt(fixture)
            }
        }

        val caseFoldCollision = validFixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = defaultPackageMetadataEntries(),
            mutateManifest = { text ->
                text.replace(
                    "\"dependencyRoots\":[\"packages/@scope/beta\",\"packages/alpha\"]",
                    "\"dependencyRoots\":[\"packages/Alpha\",\"packages/alpha\"]",
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_PATH) {
            adapt(caseFoldCollision)
        }

        val rootMismatch = validFixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = defaultPackageMetadataEntries(),
            mutateManifest = { text ->
                text.replace(
                    "\"dependencyRoots\":[\"packages/@scope/beta\",\"packages/alpha\"]",
                    "\"dependencyRoots\":[\"packages/@scope/beta\"]",
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_PATH) {
            adapt(rootMismatch)
        }

        listOf<ByteArray?>(null, "{}\n".toByteArray()).forEach { rawConfig ->
            val legacy = validFixture(rawConfigBytes = rawConfig)
            listOf("package.json", "packages/dependency/package.json").forEach { path ->
                val smuggled = LinkedHashMap(legacy.archiveEntries).apply {
                    put(path, "{}\n".toByteArray())
                }
                assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_PATH) {
                    adapt(legacy.copy(archiveEntries = smuggled))
                }
            }
        }
    }

    @Test
    fun revision5TransportsExactPackageTargetsOutsideOrdinaryProjectInventory() {
        val packageMetadataEntries = defaultPackageMetadataEntries()
        val packageTargetEntries = defaultPackageTargetEntries()
        val expectedTargets = packageTargetEntries.mapValues { (_, bytes) -> bytes.copyOf() }
        val fixture = validFixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = packageMetadataEntries,
            packageTargetEntries = packageTargetEntries,
        )

        val adapted = adapt(fixture)
        val metadata = requireNotNull(adapted.manifest.packageMetadataInventory)
        val targets = requireNotNull(adapted.manifest.packageTargetInventory)
        assertEquals(targets, adapted.snapshot.packageTargetInventory)
        assertEquals(metadata.fingerprint, targets.packageMetadataInventoryFingerprint)
        assertEquals(expectedTargets.keys.sorted(), targets.files.map { file -> file.path })
        targets.files.forEachIndexed { index, file ->
            assertFalse(adapted.snapshot.filePaths.contains("/${file.path}"))
            assertFalse(adapted.snapshot.fileInventory.any { ordinary ->
                ordinary.path == "/${file.path}"
            })
            assertArrayEquals(
                expectedTargets.getValue(file.path),
                readAll(adapted.snapshot.openPackageTargetFile(index)),
            )
        }
        assertEquals(
            listOf("/sources/helper.ts", "/sources/main.ts"),
            adapted.snapshot.rootNames,
        )

        packageTargetEntries.values.forEach { bytes -> bytes.fill(0x5a) }
        targets.files.forEachIndexed { index, file ->
            assertArrayEquals(
                expectedTargets.getValue(file.path),
                readAll(adapted.snapshot.openPackageTargetFile(index)),
            )
        }
        val invalidatedCursor = adapted.snapshot.openPackageTargetFile(0)
        adapted.close()
        assertFails<IllegalStateException> { adapted.snapshot.openPackageTargetFile(0) }
        assertFails<IllegalStateException> {
            invalidatedCursor.read(ByteArray(1), 0, 1)
        }
        invalidatedCursor.close()
    }

    @Test
    fun revision5ExactlyBindsTargetSchemaPolicyMetadataInventoryAndArchiveBytes() {
        val valid = validFixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = defaultPackageMetadataEntries(),
            packageTargetEntries = defaultPackageTargetEntries(),
        )
        val targetAuthority = requireNotNull(valid.authority.packageTargetAuthority)
        val metadataAuthority = requireNotNull(valid.authority.packageMetadataAuthority)
        val targetPath = DEFAULT_PACKAGE_TARGET_PATH

        val missing = LinkedHashMap(valid.archiveEntries).apply { remove(targetPath) }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(valid.copy(archiveEntries = missing))
        }

        val wrongBytes = LinkedHashMap(valid.archiveEntries).apply {
            val changed = getValue(targetPath).copyOf()
            changed[0] = (changed[0].toInt() xor 1).toByte()
            put(targetPath, changed)
        }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(valid.copy(archiveEntries = wrongBytes))
        }

        val invalidUtf8 = LinkedHashMap(valid.archiveEntries).apply {
            val original = getValue(targetPath)
            put(targetPath, byteArrayOf(0xc3.toByte(), 0x28) + original.copyOfRange(2, original.size))
        }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(valid.copy(archiveEntries = invalidUtf8))
        }

        val exactShape = validFixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = defaultPackageMetadataEntries(),
            packageTargetEntries = defaultPackageTargetEntries(),
            mutateManifest = { text ->
                text.replace(
                    "\"packageTargets\":{",
                    "\"packageTargets\":{\"unexpected\":true,",
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_INVALID_REQUEST) {
            adapt(exactShape)
        }

        val schemaMismatch = validFixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = defaultPackageMetadataEntries(),
            packageTargetEntries = defaultPackageTargetEntries(),
            mutateManifest = { text ->
                text.replace(
                    "\"packageTargets\":{\"schemaRevision\":" +
                        R2PackageTargetPolicy.SCHEMA_REVISION,
                    "\"packageTargets\":{\"schemaRevision\":999",
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH) {
            adapt(schemaMismatch)
        }

        val policyMismatch = validFixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = defaultPackageMetadataEntries(),
            packageTargetEntries = defaultPackageTargetEntries(),
            mutateManifest = { text ->
                text.replace(
                    "\"policyFingerprint\":\"${targetAuthority.policyFingerprint}\"",
                    "\"policyFingerprint\":\"${"0".repeat(64)}\"",
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH) {
            adapt(policyMismatch)
        }

        val metadataBindingMismatch = validFixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = defaultPackageMetadataEntries(),
            packageTargetEntries = defaultPackageTargetEntries(),
            mutateManifest = { text ->
                text.replace(
                    "\"packageMetadataInventoryFingerprint\":" +
                        "\"${metadataAuthority.fingerprint}\"",
                    "\"packageMetadataInventoryFingerprint\":\"${"0".repeat(64)}\"",
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(metadataBindingMismatch)
        }

        val inventoryMismatch = validFixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = defaultPackageMetadataEntries(),
            packageTargetEntries = defaultPackageTargetEntries(),
            mutateManifest = { text ->
                text.replace(
                    "\"inventoryFingerprint\":\"${targetAuthority.fingerprint}\"",
                    "\"inventoryFingerprint\":\"${"0".repeat(64)}\"",
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(inventoryMismatch)
        }
    }

    @Test
    fun revision5RejectsTargetPathCaseQuotaAndUndeclaredPackageEntries() {
        val entries = linkedMapOf(
            "packages/alpha/a.d.ts" to "export declare const a: 1;\n".toByteArray(),
            "packages/alpha/b.ts" to "export const b = 2;\n".toByteArray(),
        )
        fun fixture(mutateManifest: (String) -> String) = validFixture(
            rawConfigBytes = "{}\n".toByteArray(StandardCharsets.UTF_8),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = defaultPackageMetadataEntries(),
            packageTargetEntries = entries,
            mutateManifest = mutateManifest,
        )

        val rootMismatch = fixture { text ->
            text.replace("packages/alpha/a.d.ts", "packages/alph/a.d.ts")
        }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_PATH) {
            adapt(rootMismatch)
        }

        val caseCollision = fixture { text ->
            text.replace("packages/alpha/a.d.ts", "packages/alpha/Index.d.ts")
                .replace("packages/alpha/b.ts", "packages/alpha/index.d.ts")
        }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_PATH) {
            adapt(caseCollision)
        }

        val oversized = fixture { text ->
            text.replace(
                "\"path\":\"packages/alpha/a.d.ts\",\"size\":" +
                    entries.getValue("packages/alpha/a.d.ts").size,
                "\"path\":\"packages/alpha/a.d.ts\",\"size\":" +
                    (R2PackageTargetPolicy.MAXIMUM_FILE_BYTES + 1L),
            )
        }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA) {
            adapt(oversized)
        }

        val valid = fixture { it }
        val undeclared = LinkedHashMap(valid.archiveEntries).apply {
            put("packages/alpha/rogue.ts", "export {};".toByteArray())
        }
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(valid.copy(archiveEntries = undeclared))
        }
    }

    @Test
    fun archiveAndManifestDigestsAreBoundToTheControlPlane() {
        val fixture = validFixture()

        val archiveFailure = assertArchiveFailure(
            TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
        ) {
            R2ProjectArchiveAdapter.adapt(
                authority = fixture.authority.copy(archiveSha256 = sha256("other archive")),
                actualArchiveSha256 = fixture.authority.archiveSha256,
                archiveEntries = fixture.archiveEntries,
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }
        assertTrue(archiveFailure.message.orEmpty().contains("Staged archive"))

        val manifestFailure = assertArchiveFailure(
            TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
        ) {
            R2ProjectArchiveAdapter.adapt(
                authority = fixture.authority.copy(
                    requestManifestSha256 = sha256("other manifest"),
                ),
                actualArchiveSha256 = fixture.authority.archiveSha256,
                archiveEntries = fixture.archiveEntries,
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }
        assertTrue(manifestFailure.message.orEmpty().contains("manifest SHA-256"))
    }

    @Test
    fun manifestControlFieldsMustExactlyMatchTrustedAuthority() {
        val mismatches = mapOf(
            "\"requestId\":\"request-1\"" to "\"requestId\":\"request-2\"",
            "\"projectId\":\"project-1\"" to "\"projectId\":\"project-2\"",
            "\"entrySourceUri\":\"autojs://source/project/main.ts\"" to
                "\"entrySourceUri\":\"autojs://source/project/other.ts\"",
            "\"targetProfile\":\"rhino\"" to "\"targetProfile\":\"node\"",
            "\"compilationMode\":\"development\"" to "\"compilationMode\":\"release\"",
            "\"compilerVersion\":\"6.0.3\"" to "\"compilerVersion\":\"6.0.2\"",
            "\"profileRevision\":2" to "\"profileRevision\":3",
            "\"configSchemaRevision\":1" to "\"configSchemaRevision\":2",
            "\"configKind\":\"none\"" to "\"configKind\":\"tsconfig\"",
        )

        mismatches.forEach { (expected, replacement) ->
            val fixture = validFixture(
                mutateManifest = { manifest -> manifest.replace(expected, replacement) },
            )
            assertFails<R2ProjectArchiveException> {
                R2ProjectArchiveAdapter.adapt(
                    authority = fixture.authority,
                    actualArchiveSha256 = fixture.authority.archiveSha256,
                    archiveEntries = fixture.archiveEntries,
                    trustedDeclarations = fixture.trustedDeclarations,
                    trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
                )
            }
        }
    }

    @Test
    fun manifestInventoryMustCoverEveryAndOnlyArchiveEntry() {
        val fixture = validFixture()
        val missing = LinkedHashMap(fixture.archiveEntries).apply {
            remove("sources/helper.ts")
        }
        val extra = LinkedHashMap(fixture.archiveEntries).apply {
            put("sources/extra.json", "{}".toByteArray())
        }
        val declarationShadow = LinkedHashMap(fixture.archiveEntries).apply {
            put("declarations/lib.d.ts", "caller controlled".toByteArray())
        }

        listOf(missing, extra, declarationShadow).forEach { entries ->
            assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
                R2ProjectArchiveAdapter.adapt(
                    authority = fixture.authority,
                    actualArchiveSha256 = fixture.authority.archiveSha256,
                    archiveEntries = entries,
                    trustedDeclarations = fixture.trustedDeclarations,
                    trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
                )
            }
        }
    }

    @Test
    fun actualFileSizeAndSha256AreRecomputed() {
        val sourcePath = "sources/main.ts"
        val wrongSize = validFixture(
            mutateManifest = { manifest ->
                manifest.replace(
                    "\"path\":\"$sourcePath\",\"sha256\":\"${sha256(MAIN_SOURCE)}\"," +
                        "\"size\":${MAIN_SOURCE.toByteArray().size}",
                    "\"path\":\"$sourcePath\",\"sha256\":\"${sha256(MAIN_SOURCE)}\"," +
                        "\"size\":1",
                ).withRecomputedInventoryFingerprint()
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(wrongSize)
        }

        val wrongHash = validFixture(
            mutateManifest = { manifest ->
                manifest.replace(
                    "\"path\":\"$sourcePath\",\"sha256\":\"${sha256(MAIN_SOURCE)}\"",
                    "\"path\":\"$sourcePath\",\"sha256\":\"${sha256("wrong")}\"",
                ).withRecomputedInventoryFingerprint()
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(wrongHash)
        }
    }

    @Test
    fun callerInventoryFingerprintIsIndependentlyRecomputed() {
        val fixture = validFixture(
            mutateManifest = { manifest ->
                manifest.replace(
                    Regex("\"callerInventoryFingerprint\":\"[0-9a-f]{64}\""),
                    "\"callerInventoryFingerprint\":\"${"0".repeat(64)}\"",
                )
            },
        )

        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            adapt(fixture)
        }
    }

    @Test
    fun malformedUtf8ProjectTextIsRejectedWithoutReplacement() {
        val original = validFixture()
        val invalidBytes = byteArrayOf(0xc3.toByte(), 0x28)
        val path = "sources/config.json"
        val replacementFile = R2ProjectArchiveFile(
            path = path,
            size = invalidBytes.size.toLong(),
            sha256 = R2Digests.sha256(invalidBytes),
        )
        val fixture = validFixture(
            fileOverrides = mapOf(path to invalidBytes),
            manifestFileOverrides = mapOf(path to replacementFile),
        )

        val failure = assertArchiveFailure(TypeScriptCompilerContract.ERROR_INVALID_REQUEST) {
            adapt(fixture)
        }
        assertTrue(failure.message.orEmpty().contains("valid UTF-8"))
        assertEquals(
            original.trustedDeclarations.fingerprint,
            fixture.trustedDeclarations.fingerprint,
        )
    }

    @Test
    fun rootsAndFilesMustBeSortedCanonicalAndComplete() {
        val cases = listOf<(String) -> String>(
            { it.replace(
                "\"rootPaths\":[\"sources/helper.ts\",\"sources/main.ts\"]",
                "\"rootPaths\":[\"sources/main.ts\",\"sources/helper.ts\"]",
            ) },
            { it.replace(
                "\"rootPaths\":[\"sources/helper.ts\",\"sources/main.ts\"]",
                "\"rootPaths\":[\"sources/main.ts\"]",
            ) },
            { it.replace("sources/helper.ts", "sources/Helper.ts") },
            { it.replace("sources/config.json", "../config.json") },
            { it.replace("sources/config.json", "declarations/caller.d.ts") },
        )

        cases.forEach { mutation ->
            val fixture = validFixture(mutateManifest = mutation)
            assertFails<R2ProjectArchiveException> { adapt(fixture) }
        }
    }

    @Test
    fun manifestAuthorityAcceptsSafeUtf8AndSpacePathsWhileCaseCollisionsStayClosed() {
        val authorities = listOf(
            R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
        )
        authorities.forEach { authority ->
            val spacePath = "sources/space directory/main file.ts"
            val spaceFixture = validFixture(
                fileOverrides = mapOf(
                    spacePath to
                        "export const spaced = true;\n".toByteArray(StandardCharsets.UTF_8),
                ),
                profileAuthority = authority,
            )
            adapt(spaceFixture).use { adapted ->
                assertTrue(adapted.manifest.rootPaths.contains(spacePath))
                assertTrue(adapted.snapshot.rootNames.contains("/$spacePath"))
                assertEquals(
                    "export const spaced = true;\n",
                    adapted.snapshot.readText("/$spacePath"),
                )
            }

            val unicodePath = "sources/中文目录/入口.ts"
            val unicodeFixture = validFixture(
                fileOverrides = mapOf(
                    unicodePath to
                        "export const unicodePath = true;\n"
                            .toByteArray(StandardCharsets.UTF_8),
                ),
                profileAuthority = authority,
            )
            adapt(unicodeFixture).use { adapted ->
                assertTrue(adapted.manifest.rootPaths.contains(unicodePath))
                assertTrue(adapted.snapshot.rootNames.contains("/$unicodePath"))
                assertEquals(
                    "export const unicodePath = true;\n",
                    adapted.snapshot.readText("/$unicodePath"),
                )
            }

            val collisionFailure = assertArchiveFailure(
                TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
            ) {
                adapt(
                    validFixture(
                        fileOverrides = linkedMapOf(
                            "sources/z-Case.ts" to
                                "export const upper = true;\n"
                                    .toByteArray(StandardCharsets.UTF_8),
                            "sources/z-case.ts" to
                                "export const lower = true;\n"
                                    .toByteArray(StandardCharsets.UTF_8),
                        ),
                        profileAuthority = authority,
                    ),
                )
            }
            assertTrue(collisionFailure.message.orEmpty().contains("ASCII case-fold collision"))
        }
    }

    @Test
    fun reservedPackageAndDeferredNodePathsAreRejectedByTheArchiveConsumer() {
        val forbiddenPaths = listOf(
            "sources/package.json",
            "sources/Package.Json",
            "sources/nested/package.json",
            "sources/node_modules/dependency/index.ts",
            "sources/Node_Modules/dependency/index.d.ts",
            "sources/main.mts",
            "sources/main.CTS",
        )

        forbiddenPaths.forEach { path ->
            val fixture = validFixture(
                fileOverrides = mapOf(path to "inert".toByteArray()),
            )
            assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_PATH) {
                adapt(fixture)
            }
        }
    }

    @Test
    fun ordinaryJsonJavaScriptTypeScriptAndDeclarationsRemainValidArchivePayloads() {
        val fixture = validFixture(
            fileOverrides = mapOf(
                "sources/z-inert.js" to "export const inert = true;\n".toByteArray(),
                "sources/z-types.d.ts" to "export interface Value {}\n".toByteArray(),
            ),
        )

        adapt(fixture).use { adapted ->
            assertTrue(adapted.snapshot.filePaths.contains("/sources/config.json"))
            assertTrue(adapted.snapshot.filePaths.contains("/sources/z-inert.js"))
            assertTrue(adapted.snapshot.filePaths.contains("/sources/z-types.d.ts"))
            assertTrue(adapted.snapshot.filePaths.contains("/sources/main.ts"))
            assertEquals(
                listOf("/sources/helper.ts", "/sources/main.ts"),
                adapted.snapshot.rootNames,
            )
        }
    }

    @Test
    fun archiveAuthorityFreezesTheExactRevisionAndPackageMetadataMatrix() {
        val controlledMetadata = validFixture(
            rawConfigBytes = "{}\n".toByteArray(),
            packageMetadataEntries = rootPackageMetadataEntries(),
        )
        val controlledTargets = validFixture(
            rawConfigBytes = "{}\n".toByteArray(),
            packageMetadataEntries = defaultPackageMetadataEntries(),
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageTargetEntries = defaultPackageTargetEntries(),
        )
        val metadataAuthority =
            requireNotNull(controlledMetadata.authority.packageMetadataAuthority)
        val targetAuthority =
            requireNotNull(controlledTargets.authority.packageTargetAuthority)
        val rawConfigAuthority =
            requireNotNull(controlledMetadata.authority.rawConfigAuthority)
        val rhinoConfigNone = validFixture().authority
        val nodeRevisionOne = rhinoConfigNone.copy(
            profile = R2ProjectProfile.PROVISIONAL_NODE_NEXT,
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
            runtimeCapabilitiesFingerprint =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1
                    .runtimeCapabilitiesFingerprint,
        )
        val nodeRevisionTwo = rhinoConfigNone.copy(
            projectId = "project-1",
            entrySourceUri = R2GeneratedModuleMappingPolicy.originalSourceUri(
                projectId = "project-1",
                sourcePath = "/sources/main.ts",
            ),
            profile = R2ProjectProfile.PROVISIONAL_NODE_NEXT,
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            runtimeCapabilitiesFingerprint =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
                    .runtimeCapabilitiesFingerprint,
        )

        assertEquals(null, nodeRevisionOne.packageMetadataAuthority)
        assertEquals(null, nodeRevisionTwo.packageMetadataAuthority)
        assertEquals(
            metadataAuthority,
            nodeRevisionTwo.copy(
                packageMetadataAuthority = metadataAuthority,
            ).packageMetadataAuthority,
        )

        assertFails<IllegalArgumentException> {
            rhinoConfigNone.copy(packageMetadataAuthority = metadataAuthority)
        }
        assertFails<IllegalArgumentException> {
            nodeRevisionOne.copy(packageMetadataAuthority = metadataAuthority)
        }
        assertFails<IllegalArgumentException> {
            nodeRevisionOne.copy(rawConfigAuthority = rawConfigAuthority)
        }
        assertEquals(
            rawConfigAuthority,
            nodeRevisionTwo.copy(
                rawConfigAuthority = rawConfigAuthority,
            ).rawConfigAuthority,
        )
        assertEquals(
            metadataAuthority,
            nodeRevisionTwo.copy(
                rawConfigAuthority = rawConfigAuthority,
                packageMetadataAuthority = metadataAuthority,
            ).packageMetadataAuthority,
        )
        assertFails<IllegalArgumentException> {
            nodeRevisionTwo.copy(
                packageMetadataAuthority = metadataAuthority,
                packageTargetAuthority = targetAuthority,
            )
        }
        assertFails<IllegalArgumentException> {
            nodeRevisionTwo.copy(
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2.copy(
                        profileRevision = 3,
                    ),
            )
        }
    }

    @Test
    fun schemaRevisionOneDoesNotEncodeNodePackageMetadataPresence() {
        val nodeRevisionOne = validFixture(
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
        )
        val nodeRevisionTwoWithoutMetadata = validFixture(
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        )
        val nodeRevisionTwoWithMetadata = validFixture(
            packageMetadataEntries = rootPackageMetadataEntries(),
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        )

        assertTrue(
            manifestText(nodeRevisionOne).contains(
                "\"revision\":${R2InputArchiveSchemaV1.REVISION}",
            ),
        )
        assertTrue(
            manifestText(nodeRevisionTwoWithoutMetadata).contains(
                "\"revision\":${R2InputArchiveSchemaV1.REVISION}",
            ),
        )
        assertTrue(
            manifestText(nodeRevisionTwoWithMetadata).contains(
                "\"revision\":${R2InputArchiveSchemaV1.REVISION}",
            ),
        )
        assertFalse(manifestText(nodeRevisionTwoWithMetadata).contains("\"config\":"))

        assertEquals(null, parseManifest(nodeRevisionOne).packageMetadataInventory)
        assertEquals(null, parseManifest(nodeRevisionTwoWithoutMetadata).packageMetadataInventory)
        val parsedMetadata =
            requireNotNull(parseManifest(nodeRevisionTwoWithMetadata).packageMetadataInventory)
        assertEquals(listOf("package.json"), parsedMetadata.files.map { file -> file.path })
        assertEquals(
            nodeRevisionTwoWithMetadata.authority.packageMetadataAuthority?.fingerprint,
            parsedMetadata.fingerprint,
        )
        adapt(nodeRevisionTwoWithMetadata).use { adapted ->
            val catalog = requireNotNull(adapted.snapshot.nodePackageFormatCatalog)
            assertEquals(parsedMetadata.fingerprint, catalog.packageMetadataInventoryFingerprint)
            assertEquals(listOf("/"), catalog.records.map { record ->
                record.packageBoundaryPath
            })
            assertEquals(R2PackageTypeState.MODULE, catalog.records.single().typeState)
        }
    }

    @Test
    fun nodeRevisionTwoV2TargetsUseSchemaRevisionOneAndKeepAnIndependentIdentity() {
        val targetEntries = defaultNodePackageTargetV2Entries()
        val fixture = validFixture(
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = defaultPackageMetadataEntries(),
            nodePackageTargetV2Entries = targetEntries,
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        )

        assertTrue(
            manifestText(fixture).contains(
                "\"revision\":${R2InputArchiveSchemaV1.REVISION}",
            ),
        )
        val manifest = parseManifest(fixture)
        val targets = requireNotNull(manifest.nodePackageTargetV2Inventory)
        assertEquals(null, manifest.packageTargetInventory)
        assertEquals(
            fixture.authority.nodePackageTargetV2Authority?.fingerprint,
            targets.fingerprint,
        )
        assertEquals(
            listOf(
                R2NodePackageTargetKind.EXECUTABLE_SOURCE,
                R2NodePackageTargetKind.EXECUTABLE_SOURCE,
                R2NodePackageTargetKind.EXECUTABLE_SOURCE,
                R2NodePackageTargetKind.DECLARATION,
                R2NodePackageTargetKind.DECLARATION,
                R2NodePackageTargetKind.DECLARATION,
            ),
            targets.files.map(R2NodePackageTargetV2FileDescriptor::kind),
        )

        adapt(fixture).use { adapted ->
            val adaptedTargets = requireNotNull(
                adapted.manifest.nodePackageTargetV2Inventory,
            )
            assertEquals(targets.canonicalInput, adaptedTargets.canonicalInput)
            assertEquals(targets.fingerprint, adaptedTargets.fingerprint)
            assertEquals(null, adapted.snapshot.packageTargetInventory)
            assertTrue(adaptedTargets === adapted.snapshot.nodePackageTargetV2Inventory)
            assertTrue(adapted.snapshot.nodePackageMetadataCatalog != null)
            adaptedTargets.files.forEachIndexed { index, descriptor ->
                assertArrayEquals(
                    targetEntries.getValue(descriptor.path),
                    readAll(adapted.snapshot.openNodePackageTargetV2File(index)),
                )
            }
        }

        val v2Authority = requireNotNull(fixture.authority.nodePackageTargetV2Authority)
        assertFails<IllegalArgumentException> {
            fixture.authority.copy(
                nodePackageTargetV2Authority = v2Authority.copy(
                    packageMetadataInventoryFingerprint = EMPTY_SHA256,
                ),
            )
        }
    }

    @Test
    fun nodeRevisionTwoV2TargetManifestFingerprintDriftFailsClosed() {
        val fixture = validFixture(
            dependencyRoots = DEFAULT_DEPENDENCY_ROOTS,
            packageMetadataEntries = defaultPackageMetadataEntries(),
            nodePackageTargetV2Entries = defaultNodePackageTargetV2Entries(),
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        )
        val expectedFingerprint = requireNotNull(
            fixture.authority.nodePackageTargetV2Authority,
        ).fingerprint
        val driftedFingerprint = sha256("drifted Node package-target v2 inventory")
        val originalManifest = manifestText(fixture)
        val driftedManifest = originalManifest.replace(
            "\"inventoryFingerprint\":\"$expectedFingerprint\"",
            "\"inventoryFingerprint\":\"$driftedFingerprint\"",
        ).toByteArray(StandardCharsets.UTF_8)
        assertFalse(originalManifest == driftedManifest.toString(StandardCharsets.UTF_8))
        val driftedEntries = LinkedHashMap(fixture.archiveEntries)
        driftedEntries[R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH] = driftedManifest
        val drifted = fixture.copy(
            authority = fixture.authority.copy(
                requestManifestSha256 = R2Digests.sha256(driftedManifest),
            ),
            archiveEntries = driftedEntries,
        )

        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            parseManifest(drifted)
        }
    }

    @Test
    fun nodeRevisionTwoManifestAndHeapAdapterAdmitMtsAndCtsInSchemaRevisionOne() {
        listOf("mts", "cts").forEach { extension ->
            listOf(false, true).forEach { withMetadata ->
                val entryPath = "sources/z-entry.$extension"
                val sourceText = "export const entry = '$extension';\n"
                val fixture = validFixture(
                    fileOverrides = mapOf(entryPath to sourceText.toByteArray()),
                    packageMetadataEntries =
                        if (withMetadata) rootPackageMetadataEntries() else emptyMap(),
                    profileAuthority =
                        R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                    entryPathOverride = entryPath,
                )

                val parsed = parseManifest(fixture)
                assertEquals(
                    R2InputArchiveSchemaV1.REVISION,
                    Regex("\"revision\":([0-9]+)")
                        .find(manifestText(fixture))
                        ?.groupValues
                        ?.get(1)
                        ?.toInt(),
                )
                assertEquals(entryPath, parsed.entryPath)
                assertTrue(parsed.rootPaths.contains(entryPath))
                assertEquals(
                    R2GeneratedModuleMappingPolicy.originalSourceUri(
                        projectId = fixture.authority.projectId,
                        sourcePath = "/$entryPath",
                    ),
                    parsed.entrySourceUri,
                )

                adapt(fixture).use { adapted ->
                    assertEquals("/$entryPath", adapted.snapshot.entryPath)
                    assertTrue(adapted.snapshot.rootNames.contains("/$entryPath"))
                    assertEquals(
                        fixture.authority.projectId,
                        adapted.snapshot.acceptedProjectId,
                    )
                    assertEquals(sourceText, adapted.snapshot.readText("/$entryPath"))
                }
            }
        }
    }

    @Test
    fun archiveSourceKindAdmissionIsExactRevisionLowercaseAndNonDeclaration() {
        listOf(
            R2CompilationProfileRegistry.PROVISIONAL_RHINO to "sources/z-entry.mts",
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1 to
                "sources/z-entry.cts",
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 to
                "sources/z-entry.MTS",
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 to
                "sources/z-entry.cTs",
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 to
                "sources/z-entry.d.mts",
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 to
                "sources/z-entry.d.cts",
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 to
                "sources/node_modules/dependency/z-entry.mts",
        ).forEach { (profileAuthority, entryPath) ->
            val fixture = validFixture(
                fileOverrides = mapOf(entryPath to "inert\n".toByteArray()),
                profileAuthority = profileAuthority,
                entryPathOverride = entryPath,
            )
            assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_PATH) {
                parseManifest(fixture)
            }
        }
    }

    @Test
    fun nodeRevisionTwoMtsEntryStillRequiresExactRootAndGeneratedUriIdentity() {
        val entryPath = "sources/z-entry.mts"
        val missingRoot = validFixture(
            fileOverrides = mapOf(entryPath to "export {};\n".toByteArray()),
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            entryPathOverride = entryPath,
            mutateManifest = { manifest ->
                manifest.replace(",\"$entryPath\"],\"files\"", "],\"files\"")
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_INVALID_REQUEST) {
            parseManifest(missingRoot)
        }

        val nonCanonicalUri = validFixture(
            fileOverrides = mapOf(entryPath to "export {};\n".toByteArray()),
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            entryPathOverride = entryPath,
            entrySourceUriOverride = "autojs://source/project/z-entry.mts",
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            parseManifest(nonCanonicalUri)
        }
    }

    @Test
    fun undeclaredMtsUsesNodeRevisionTwoInventoryIntegrityButLegacyPathFence() {
        val extraPath = "sources/z-extra.mts"
        listOf(
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 to
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1 to
                TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
        ).forEach { (profileAuthority, expectedError) ->
            val fixture = validFixture(profileAuthority = profileAuthority)
            val entries = fixture.archiveEntries + (extraPath to byteArrayOf(0))
            assertArchiveFailure(expectedError) {
                R2ProjectArchiveAdapter.adapt(
                    authority = fixture.authority,
                    actualArchiveSha256 = fixture.authority.archiveSha256,
                    archiveEntries = entries,
                    trustedDeclarations = fixture.trustedDeclarations,
                    trustedRuntimeCapabilitiesFingerprint =
                        fixture.authority.runtimeCapabilitiesFingerprint,
                )
            }
        }
    }

    @Test
    fun heapArchiveMapsInvalidNodePackageTypeToInvalidRequest() {
        val invalid = validFixture(
            packageMetadataEntries = linkedMapOf(
                "package.json" to "{\"type\":\"esm\"}\n".toByteArray(),
            ),
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        )

        val failure = assertArchiveFailure(TypeScriptCompilerContract.ERROR_INVALID_REQUEST) {
            adapt(invalid)
        }
        assertTrue(
            failure.message.orEmpty().contains(
                R2NodeModuleOutputPolicyCode.PACKAGE_TYPE_INVALID.contractValue,
            ),
        )
        assertTrue(failure.cause is R2NodeModuleOutputPolicyException)
    }

    @Test
    fun inputArchiveSchemaRejectsLegacyRevisionAndProfileDrift() {
        val metadataEntries = rootPackageMetadataEntries()
        val wrongRevision = validFixture(
            packageMetadataEntries = metadataEntries,
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            mutateManifest = { manifest ->
                manifest.replace(
                    "\"revision\":${R2InputArchiveSchemaV1.REVISION}",
                    "\"revision\":6",
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH) {
            parseManifest(wrongRevision)
        }

        val revisionSixWithoutMetadata = validFixture(
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            mutateManifest = { manifest ->
                manifest.replace(
                    "\"revision\":${R2InputArchiveSchemaV1.REVISION}",
                    "\"revision\":6",
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH) {
            parseManifest(revisionSixWithoutMetadata)
        }

        val derivedTypeField = validFixture(
            packageMetadataEntries = metadataEntries,
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            mutateManifest = { manifest ->
                manifest.replaceFirst(
                    "\"inventoryFingerprint\":",
                    "\"typeState\":\"module\",\"inventoryFingerprint\":",
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_INVALID_REQUEST) {
            parseManifest(derivedTypeField)
        }

        listOf(
            "\"profileRevision\":2" to "\"profileRevision\":1",
            "\"targetProfile\":\"node\"" to "\"targetProfile\":\"rhino\"",
        ).forEach { (expected, replacement) ->
            val drift = validFixture(
                packageMetadataEntries = metadataEntries,
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                mutateManifest = { manifest -> manifest.replace(expected, replacement) },
            )
            assertFails<R2ProjectArchiveException> { parseManifest(drift) }
        }

        val rhinoRevisionFourClaimingRevisionSix = validFixture(
            rawConfigBytes = "{}\n".toByteArray(),
            packageMetadataEntries = metadataEntries,
            mutateManifest = { manifest ->
                manifest.replace(
                    "\"revision\":${R2InputArchiveSchemaV1.REVISION}",
                    "\"revision\":6",
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH) {
            parseManifest(rhinoRevisionFourClaimingRevisionSix)
        }
    }

    @Test
    fun nodeRevisionTwoEntryUriUsesTheGeneratedMappingAuthority() {
        val exact = validFixture(
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        )
        assertEquals(
            R2GeneratedModuleMappingPolicy.originalSourceUri(
                projectId = exact.authority.projectId,
                sourcePath = "/sources/main.ts",
            ),
            parseManifest(exact).entrySourceUri,
        )

        val authorityAndManifestAgreeOnNonCanonicalUri = validFixture(
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            entrySourceUriOverride = "autojs://source/project/main.ts",
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            parseManifest(authorityAndManifestAgreeOnNonCanonicalUri)
        }

        val entryPathDrift = validFixture(
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            mutateManifest = { manifest ->
                manifest.replace(
                    "\"entryPath\":\"sources/main.ts\"",
                    "\"entryPath\":\"sources/helper.ts\"",
                )
            },
        )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            parseManifest(entryPathDrift)
        }
    }

    @Test
    fun localTrustedDeclarationFingerprintCannotBeSubstituted() {
        val fixture = validFixture()
        val otherText = "interface Array<T> { readonly length: number }\n"
        val otherBytes = otherText.toByteArray()
        val otherFile = R2TrustedDeclarationFile(
            archivePath = DEFAULT_LIBRARY_ARCHIVE_PATH,
            fingerprintPath = DEFAULT_LIBRARY_FINGERPRINT_PATH,
            size = otherBytes.size.toLong(),
            sha256 = R2Digests.sha256(otherBytes),
            text = otherText,
        )
        val otherDeclarations = R2TrustedDeclarations.create(
            R2TrustedDeclarations.fingerprint(listOf(otherFile)),
            listOf(otherFile),
        )

        assertArchiveFailure(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH) {
            R2ProjectArchiveAdapter.adapt(
                authority = fixture.authority,
                actualArchiveSha256 = fixture.authority.archiveSha256,
                archiveEntries = fixture.archiveEntries,
                trustedDeclarations = otherDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }
    }

    @Test
    fun nodeProfileRequiresItsExactTrustedDeclarationLayer() {
        val fixture = validFixture(
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO_REVISION_1,
        )
        val nodeRuntimeFingerprint =
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1
                .runtimeCapabilitiesFingerprint
        val nodeAuthority = fixture.authority.copy(
            profile = R2ProjectProfile.PROVISIONAL_NODE_NEXT,
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
            runtimeCapabilitiesFingerprint = nodeRuntimeFingerprint,
        )

        assertArchiveFailure(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH) {
            R2ProjectArchiveAdapter.adapt(
                authority = nodeAuthority,
                actualArchiveSha256 = nodeAuthority.archiveSha256,
                archiveEntries = fixture.archiveEntries,
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = nodeRuntimeFingerprint,
            )
        }

        val rhinoDeclarations = profileDeclarations(R2DeclarationProfile.RHINO)
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH) {
            R2ProjectArchiveAdapter.adapt(
                authority = nodeAuthority.copy(
                    declarationsFingerprint = rhinoDeclarations.fingerprint,
                    declarationProfileFingerprint = rhinoDeclarations.profileFingerprint,
                ),
                actualArchiveSha256 = nodeAuthority.archiveSha256,
                archiveEntries = fixture.archiveEntries,
                trustedDeclarations = rhinoDeclarations,
                trustedRuntimeCapabilitiesFingerprint = nodeRuntimeFingerprint,
            )
        }

        val nodeDeclarations = profileDeclarations(R2DeclarationProfile.NODE)
        val nodeManifest = fixture.archiveEntries
            .getValue(R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH)
            .toString(StandardCharsets.UTF_8)
            .replace("\"targetProfile\":\"rhino\"", "\"targetProfile\":\"node\"")
            .replace(
                fixture.authority.runtimeCapabilitiesFingerprint,
                nodeRuntimeFingerprint,
            )
            .replace(
                "\"declarationProfileFingerprint\":\"" +
                    fixture.trustedDeclarations.profileFingerprint + "\"",
                "\"declarationProfileFingerprint\":\"" +
                    nodeDeclarations.profileFingerprint + "\"",
            )
            .toByteArray(StandardCharsets.UTF_8)
        val exactNodeFixture = Fixture(
            authority = nodeAuthority.copy(
                declarationsFingerprint = nodeDeclarations.fingerprint,
                declarationProfileFingerprint = nodeDeclarations.profileFingerprint,
                requestManifestSha256 = R2Digests.sha256(nodeManifest),
            ),
            archiveEntries = LinkedHashMap(fixture.archiveEntries).apply {
                put(R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH, nodeManifest)
            },
            trustedDeclarations = nodeDeclarations,
        )
        adapt(exactNodeFixture).use { adapted ->
            assertEquals(
                R2ProjectProfile.PROVISIONAL_NODE_NEXT,
                adapted.snapshot.profile,
            )
            assertTrue(adapted.snapshot.filePaths.contains("/declarations/node/index.d.ts"))
        }

        // Rhino intentionally keeps the legacy common-only compatibility path.
        adapt(fixture).close()
    }

    @Test
    fun runtimeCapabilitiesMustMatchTheLocalTrustedFingerprint() {
        val fixture = validFixture()

        assertArchiveFailure(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH) {
            R2ProjectArchiveAdapter.adapt(
                authority = fixture.authority,
                actualArchiveSha256 = fixture.authority.archiveSha256,
                archiveEntries = fixture.archiveEntries,
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = sha256("other runtime"),
            )
        }
    }

    @Test
    fun archiveAuthorityRejectsProfileRuntimeSplitBrainAtConstruction() {
        val fixture = validFixture()

        assertFails<IllegalArgumentException> {
            fixture.authority.copy(
                runtimeCapabilitiesFingerprint = sha256("different profile runtime"),
            )
        }
    }

    @Test
    fun schemaAndContractFieldsUseStableContractMismatchErrors() {
        val fixture = validFixture(
            mutateManifest = { manifest ->
                manifest.replace(
                    "\"contractHash\":\"${TypeScriptCompilerContract.HASH}\"",
                    "\"contractHash\":\"${"0".repeat(64)}\"",
                )
            },
        )

        assertArchiveFailure(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH) {
            adapt(fixture)
        }
    }

    @Test
    fun legacyHeapAdapterRetainsItsPrivateMaterializationBudget() {
        val fixture = validFixture()
        val oversizedMaterializedEntry = fixture.archiveEntries + (
            "sources/extra.bin" to
                ByteArray(
                    ZipArchiveV1Limits.PROVISIONAL_MATERIALIZED_PROJECT
                        .maximumSingleFileBytes.toInt() + 1,
                )
            )
        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA) {
            R2ProjectArchiveAdapter.adapt(
                authority = fixture.authority,
                actualArchiveSha256 = fixture.authority.archiveSha256,
                archiveEntries = oversizedMaterializedEntry,
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }
    }

    @Test
    fun undeclaredReservedHeapEntryUsesPathClassificationBeforeExactInventory() {
        val fixture = validFixture()
        val entriesWithReservedPath = fixture.archiveEntries + (
            "sources/Node_Modules/dependency/index.ts" to byteArrayOf(0)
            )

        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_PATH) {
            R2ProjectArchiveAdapter.adapt(
                authority = fixture.authority,
                actualArchiveSha256 = fixture.authority.archiveSha256,
                archiveEntries = entriesWithReservedPath,
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }
    }

    @Test
    fun undeclaredOrdinaryHeapEntryRemainsAnIntegrityFailure() {
        val fixture = validFixture()
        val entriesWithOrdinaryExtra = fixture.archiveEntries + (
            "sources/z-extra.json" to byteArrayOf(0)
            )

        assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            R2ProjectArchiveAdapter.adapt(
                authority = fixture.authority,
                actualArchiveSha256 = fixture.authority.archiveSha256,
                archiveEntries = entriesWithOrdinaryExtra,
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }
    }

    @Test
    fun attackerControlledInventoryErrorsStayWithinTheContractBudget() {
        val fixture = validFixture()
        val entriesWithLongUnknownPath = fixture.archiveEntries + (
            "x".repeat(16 * 1024) to byteArrayOf(0)
            )

        val failure = assertArchiveFailure(TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY) {
            R2ProjectArchiveAdapter.adapt(
                authority = fixture.authority,
                actualArchiveSha256 = fixture.authority.archiveSha256,
                archiveEntries = entriesWithLongUnknownPath,
                trustedDeclarations = fixture.trustedDeclarations,
                trustedRuntimeCapabilitiesFingerprint = RUNTIME_FINGERPRINT,
            )
        }

        assertTrue(
            failure.message.orEmpty().length <=
                TypeScriptCompilerContract.MAX_ERROR_MESSAGE_CHARS,
        )
    }

    private fun adapt(fixture: Fixture): R2AdaptedProjectArchive =
        R2ProjectArchiveAdapter.adapt(
            authority = fixture.authority,
            actualArchiveSha256 = fixture.authority.archiveSha256,
            archiveEntries = fixture.archiveEntries,
            trustedDeclarations = fixture.trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint =
                fixture.authority.runtimeCapabilitiesFingerprint,
        )

    private fun parseManifest(fixture: Fixture): R2ProvisionalRequestManifest =
        R2ProvisionalRequestManifest.parse(
            bytes = fixture.archiveEntries.getValue(
                R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH,
            ),
            authority = fixture.authority,
        )

    private fun manifestText(fixture: Fixture): String =
        fixture.archiveEntries.getValue(
            R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH,
        ).toString(StandardCharsets.UTF_8)

    private fun validFixture(
        mutateManifest: (String) -> String = { it },
        fileOverrides: Map<String, ByteArray> = emptyMap(),
        manifestFileOverrides: Map<String, R2ProjectArchiveFile> = emptyMap(),
        rawConfigBytes: ByteArray? = null,
        dependencyRoots: List<String> = emptyList(),
        packageMetadataEntries: Map<String, ByteArray> = emptyMap(),
        dependencyLayerEntries: Map<String, ByteArray> = emptyMap(),
        packageTargetEntries: Map<String, ByteArray> = emptyMap(),
        nodePackageTargetV2Entries: Map<String, ByteArray> = emptyMap(),
        profileAuthority: R2CompilationProfileAuthority =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        entryPathOverride: String? = null,
        entrySourceUriOverride: String? = null,
    ): Fixture {
        val projectId = "project-1"
        val entryPath = entryPathOverride ?: "sources/main.ts"
        val entrySourceUri = entrySourceUriOverride ?: when (profileAuthority) {
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 ->
                R2GeneratedModuleMappingPolicy.originalSourceUri(
                    projectId = projectId,
                    sourcePath = "/$entryPath",
                )
            else -> "autojs://source/project/main.ts"
        }
        val sourceBytes = linkedMapOf(
            "sources/config.json" to CONFIG_JSON.toByteArray(),
            "sources/helper.ts" to HELPER_SOURCE.toByteArray(),
            "sources/main.ts" to MAIN_SOURCE.toByteArray(),
        ).apply {
            fileOverrides.forEach { (path, bytes) -> put(path, bytes) }
        }
        val files = sourceBytes.map { (path, bytes) ->
            manifestFileOverrides[path] ?: R2ProjectArchiveFile(
                path = path,
                size = bytes.size.toLong(),
                sha256 = R2Digests.sha256(bytes),
            )
        }
        val trustedDeclarations = when (profileAuthority.profile) {
            R2ProjectProfile.PROVISIONAL_RHINO -> trustedDeclarations()
            R2ProjectProfile.PROVISIONAL_NODE_NEXT ->
                profileDeclarations(R2DeclarationProfile.NODE)
        }
        val rawConfigAuthority = rawConfigBytes?.let { bytes ->
            R2RawProjectConfigAuthority(
                size = bytes.size.toLong(),
                sha256 = R2Digests.sha256(bytes),
            )
        }
        val packageMetadataInventory = packageMetadataEntries
            .takeIf { entries -> entries.isNotEmpty() }
            ?.entries
            ?.sortedBy { entry -> entry.key }
            ?.map { (path, bytes) ->
                R2PackageMetadataFileDescriptor(
                    path = path,
                    size = bytes.size.toLong(),
                    sha256 = R2Digests.sha256(bytes),
                )
            }
            ?.let { files -> R2PackageMetadataInventory.create(dependencyRoots, files) }
        val dependencyLayerInventory = dependencyLayerEntries
            .takeIf { entries -> entries.isNotEmpty() }
            ?.entries
            ?.sortedBy { entry -> entry.key }
            ?.map { (path, bytes) ->
                R2DependencyLayerFileDescriptor(
                    path = path,
                    size = bytes.size.toLong(),
                    sha256 = R2Digests.sha256(bytes),
                )
            }
            ?.let { files ->
                val lockfile = requireNotNull(
                    files.firstOrNull { file ->
                        file.path == "dependencies/lockfiles/package-lock.json"
                    },
                ) { "Focused dependency-layer fixtures require package-lock.json." }
                R2DependencyLayerInventory.create(
                    identityKind = R2DependencyLayerPolicy.IDENTITY_PACKAGE_LOCK,
                    identityPath = lockfile.path,
                    identityFingerprint = lockfile.sha256,
                    files = files,
                )
            }
        require(packageTargetEntries.isEmpty() || packageMetadataInventory != null)
        require(nodePackageTargetV2Entries.isEmpty() || packageMetadataInventory != null)
        require(packageTargetEntries.isEmpty() || nodePackageTargetV2Entries.isEmpty())
        val packageTargetInventory = packageTargetEntries
            .takeIf { entries -> entries.isNotEmpty() }
            ?.entries
            ?.sortedBy { entry -> entry.key }
            ?.map { (path, bytes) ->
                R2PackageTargetFileDescriptor(
                    path = path,
                    size = bytes.size.toLong(),
                    sha256 = R2Digests.sha256(bytes),
                )
            }
            ?.let { files ->
                R2PackageTargetInventory.create(
                    packageMetadataInventory = requireNotNull(packageMetadataInventory),
                    files = files,
                )
            }
        val nodePackageTargetV2Inventory = nodePackageTargetV2Entries
            .takeIf { entries -> entries.isNotEmpty() }
            ?.entries
            ?.sortedBy { entry -> entry.key }
            ?.map { (path, bytes) ->
                R2NodePackageTargetV2FileDescriptor(
                    path = path,
                    size = bytes.size.toLong(),
                    sha256 = R2Digests.sha256(bytes),
                )
            }
            ?.let { files ->
                R2NodePackageTargetV2Inventory.create(
                    packageMetadataInventory = requireNotNull(packageMetadataInventory),
                    files = files,
                )
            }
        val manifest = mutateManifest(
            manifest(
                files,
                rawConfigAuthority,
                packageMetadataInventory,
                packageTargetInventory,
                nodePackageTargetV2Inventory,
                dependencyLayerInventory = dependencyLayerInventory,
                profileAuthority = profileAuthority,
                projectId = projectId,
                entrySourceUri = entrySourceUri,
                entryPath = entryPath,
                declarationsFingerprint = trustedDeclarations.fingerprint,
                declarationProfileFingerprint = trustedDeclarations.profileFingerprint,
            ),
        )
            .toByteArray(StandardCharsets.UTF_8)
        val archiveEntries = linkedMapOf(
            R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH to manifest,
        ).apply {
            rawConfigBytes?.let { bytes ->
                put(R2ControlledTsConfigPolicy.CONFIG_PATH, bytes)
            }
            putAll(packageMetadataEntries)
            putAll(dependencyLayerEntries)
            putAll(packageTargetEntries)
            putAll(nodePackageTargetV2Entries)
            putAll(sourceBytes)
        }
        val archiveSha256 = sha256("opaque staged archive bytes")
        val authority = R2ProjectArchiveAuthority(
            requestId = "request-1",
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
            archiveSha256 = archiveSha256,
            requestManifestSha256 = R2Digests.sha256(manifest),
            rawConfigAuthority = rawConfigAuthority,
            packageMetadataAuthority = packageMetadataInventory?.authority,
            dependencyLayerAuthority = dependencyLayerInventory?.authority,
            packageTargetAuthority = packageTargetInventory?.authority,
            nodePackageTargetV2Authority = nodePackageTargetV2Inventory?.authority,
        )
        return Fixture(
            authority = authority,
            archiveEntries = archiveEntries,
            trustedDeclarations = trustedDeclarations,
        )
    }

    private fun defaultPackageMetadataEntries(): LinkedHashMap<String, ByteArray> =
        linkedMapOf(
            "package.json" to
                "{\"name\":\"root-project\",\"private\":true}\n".toByteArray(),
            "packages/@scope/beta/package.json" to
                "{\"name\":\"@scope/beta\",\"version\":\"1.0.0\"}\n".toByteArray(),
            "packages/alpha/package.json" to
                "{\"name\":\"alpha\",\"version\":\"1.0.0\"}\n".toByteArray(),
            "packages/alpha/nested/package.json" to
                "{\"name\":\"alpha-nested-boundary\"}\n".toByteArray(),
        )

    private fun rootPackageMetadataEntries(): LinkedHashMap<String, ByteArray> =
        linkedMapOf(
            "package.json" to
                "{\"name\":\"root-project\",\"type\":\"module\"}\n".toByteArray(),
        )

    private fun defaultPackageTargetEntries(): LinkedHashMap<String, ByteArray> =
        linkedMapOf(
            DEFAULT_PACKAGE_TARGET_PATH to
                "export declare const alpha: string;\n".toByteArray(),
            "packages/alpha/index.ts" to
                "export const alpha = 'controlled';\n".toByteArray(),
        )

    private fun defaultNodePackageTargetV2Entries(): LinkedHashMap<String, ByteArray> =
        linkedMapOf(
            "packages/alpha/a.ts" to "export const a = 1;\n".toByteArray(),
            "packages/alpha/b.mts" to "export const b = 2;\n".toByteArray(),
            "packages/alpha/c.cts" to "export const c = 3;\n".toByteArray(),
            "packages/alpha/d.d.ts" to "export declare const d: 4;\n".toByteArray(),
            "packages/alpha/e.d.mts" to "export declare const e: 5;\n".toByteArray(),
            "packages/alpha/f.d.cts" to "export declare const f: 6;\n".toByteArray(),
        )

    private fun trustedDeclarations(): R2TrustedDeclarations {
        val bytes = DEFAULT_LIBRARY.toByteArray(StandardCharsets.UTF_8)
        val file = R2TrustedDeclarationFile(
            archivePath = DEFAULT_LIBRARY_ARCHIVE_PATH,
            fingerprintPath = DEFAULT_LIBRARY_FINGERPRINT_PATH,
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

    private fun manifest(
        files: List<R2ProjectArchiveFile>,
        rawConfigAuthority: R2RawProjectConfigAuthority? = null,
        packageMetadataInventory: R2PackageMetadataInventory? = null,
        packageTargetInventory: R2PackageTargetInventory? = null,
        nodePackageTargetV2Inventory: R2NodePackageTargetV2Inventory? = null,
        dependencyLayerInventory: R2DependencyLayerInventory? = null,
        profileAuthority: R2CompilationProfileAuthority =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        projectId: String = "project-1",
        entrySourceUri: String = "autojs://source/project/main.ts",
        entryPath: String = "sources/main.ts",
        declarationsFingerprint: String = trustedDeclarations().fingerprint,
        declarationProfileFingerprint: String = declarationsFingerprint,
    ): String {
        val inventoryFingerprint = R2InputArchiveSchemaV1.callerInventoryFingerprint(
            sources = files,
            rawConfig = rawConfigAuthority?.descriptor(),
            packageMetadata = packageMetadataInventory,
            packageTargets = packageTargetInventory,
            nodePackageTargetsV2 = nodePackageTargetV2Inventory,
        )
        val fileJson = files.joinToString(separator = ",") { file ->
            "{\"path\":\"${file.path}\",\"sha256\":\"${file.sha256}\"," +
                "\"size\":${file.size}}"
        }
        val roots = files
            .map(R2ProjectArchiveFile::path)
            .filter { path ->
                R2ProjectSourceKindAdmission.isExecutableSourcePath(
                    path = path,
                    profileAuthority = profileAuthority,
                )
            }
        val rootJson = roots.joinToString(separator = ",") { path -> "\"$path\"" }
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
            val dependencyRootsJson = inventory.dependencyRoots.joinToString(",") { root ->
                "\"$root\""
            }
            val packageFilesJson = inventory.files.joinToString(",") { file ->
                "{\"path\":\"${file.path}\",\"size\":${file.size}," +
                    "\"sha256\":\"${file.sha256}\"}"
            }
            "\"packageMetadata\":{\"schemaRevision\":" +
                "${R2PackageMetadataPolicy.SCHEMA_REVISION},\"policyRevision\":" +
                "${inventory.authority.policyRevision},\"policyFingerprint\":" +
                "\"${inventory.authority.policyFingerprint}\"," +
                "\"inventoryFingerprint\":\"${inventory.fingerprint}\"," +
                "\"dependencyRoots\":[$dependencyRootsJson]," +
                "\"files\":[$packageFilesJson]},"
        }.orEmpty()
        val packageTargetJson = packageTargetInventory?.let { inventory ->
            val targetFilesJson = inventory.files.joinToString(",") { file ->
                "{\"path\":\"${file.path}\",\"size\":${file.size}," +
                    "\"sha256\":\"${file.sha256}\"}"
            }
            "\"packageTargets\":{\"schemaRevision\":" +
                "${R2PackageTargetPolicy.SCHEMA_REVISION},\"policyRevision\":" +
                "${inventory.authority.policyRevision},\"policyFingerprint\":" +
                "\"${inventory.authority.policyFingerprint}\"," +
                "\"packageMetadataInventoryFingerprint\":" +
                "\"${inventory.packageMetadataInventoryFingerprint}\"," +
                "\"inventoryFingerprint\":\"${inventory.fingerprint}\"," +
                "\"files\":[$targetFilesJson]},"
        }.orEmpty()
        val nodePackageTargetV2Json = nodePackageTargetV2Inventory?.let { inventory ->
            val targetFilesJson = inventory.files.joinToString(",") { file ->
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
                "\"files\":[$targetFilesJson]},"
        }.orEmpty()
        val dependencyLayerJson = dependencyLayerInventory?.let { inventory ->
            val dependencyFilesJson = inventory.files.joinToString(",") { file ->
                "{\"path\":\"${file.path}\",\"size\":${file.size}," +
                    "\"sha256\":\"${file.sha256}\"}"
            }
            "\"dependencyLayer\":{\"schemaRevision\":" +
                "${R2DependencyLayerPolicy.SCHEMA_REVISION},\"policyRevision\":" +
                "${inventory.authority.policyRevision},\"policyFingerprint\":" +
                "\"${inventory.authority.policyFingerprint}\",\"identityKind\":" +
                "\"${inventory.identityKind}\",\"identityPath\":\"${inventory.identityPath}\"," +
                "\"identityFingerprint\":\"${inventory.identityFingerprint}\"," +
                "\"inventoryFingerprint\":\"${inventory.inventoryFingerprint}\"," +
                "\"fingerprint\":\"${inventory.fingerprint}\"," +
                "\"fileCount\":${inventory.files.size},\"totalBytes\":${inventory.totalBytes}," +
                "\"totalPathBytes\":${inventory.totalPathBytes}," +
                "\"files\":[$dependencyFilesJson]},"
        }.orEmpty()
        return """
            {"schema":"${R2InputArchiveSchemaV1.SCHEMA}",
            "revision":$revision,
            "inputQuotaRevision":${R2InputArchiveSchemaV1.INPUT_QUOTA_REVISION},
            "contractVersion":${TypeScriptCompilerContract.VERSION},
            "contractHash":"${TypeScriptCompilerContract.HASH}",
            "inputArchiveTransportVersion":${TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION},
            "requestId":"request-1","projectId":"$projectId",
            "entrySourceUri":"$entrySourceUri",
            "entryPath":"$entryPath","targetProfile":"${profileAuthority.profile.contractValue}",
            "compilationMode":"development",
            "profileSchemaRevision":${profileAuthority.profileSchemaRevision},
            "profileRevision":${profileAuthority.profileRevision},
            "runtimeCapabilitySchemaRevision":${TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION},
            "runtimeCapabilitiesFingerprint":"${profileAuthority.runtimeCapabilitiesFingerprint}",
            "compilerPackage":"typescript","compilerVersion":"6.0.3",
            "compilerDistributionFingerprint":"$DISTRIBUTION_FINGERPRINT",
            "declarationsFingerprint":"$declarationsFingerprint",
            "declarationProfileFingerprint":"$declarationProfileFingerprint",
            "configSchemaRevision":$configSchemaRevision,
            "configKind":"$configKind",$configJson$packageMetadataJson$packageTargetJson$nodePackageTargetV2Json$dependencyLayerJson
            "callerInventoryFingerprint":"$inventoryFingerprint",
            "rootPaths":[$rootJson],"files":[$fileJson]}
        """.trimIndent().replace("\n", "")
    }

    private fun readAll(cursor: R2ProjectFileCursor): ByteArray = cursor.use {
        val bytes = ByteArray(cursor.remainingBytes.toInt())
        var offset = 0
        while (offset < bytes.size) {
            val count = cursor.read(bytes, offset, bytes.size - offset)
            check(count > 0)
            offset += count
        }
        bytes
    }

    private fun String.withRecomputedInventoryFingerprint(): String {
        val fileRegex =
            Regex("""\{"path":"([^"]+)","sha256":"([0-9a-f]{64})","size":([0-9]+)}""")
        val files = fileRegex.findAll(this).map { match ->
            R2ProjectArchiveFile(
                path = match.groupValues[1],
                sha256 = match.groupValues[2],
                size = match.groupValues[3].toLong(),
            )
        }.toList()
        return replace(
            Regex("\"callerInventoryFingerprint\":\"[0-9a-f]{64}\""),
            "\"callerInventoryFingerprint\":\"" +
                R2ProvisionalRequestManifest.inventoryFingerprint(files) +
                "\"",
        )
    }

    private fun sha256(text: String): String =
        R2Digests.sha256(text.toByteArray(StandardCharsets.UTF_8))

    private fun assertArchiveFailure(
        errorCode: String,
        block: () -> Unit,
    ): R2ProjectArchiveException {
        val failure = assertFails<R2ProjectArchiveException>(block)
        assertEquals(failure.message, errorCode, failure.contractErrorCode)
        return failure
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

    private data class Fixture(
        val authority: R2ProjectArchiveAuthority,
        val archiveEntries: Map<String, ByteArray>,
        val trustedDeclarations: R2TrustedDeclarations,
    )

    private companion object {
        const val EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        const val CONFIG_JSON = "{\"enabled\":true}\n"
        const val HELPER_SOURCE = "export const helper = 41;\n"
        const val MAIN_SOURCE =
            "import { helper } from \"./helper\";\nexport const answer = helper + 1;\n"
        const val DEFAULT_LIBRARY = "interface Array<T> { length: number }\n"
        const val DEFAULT_LIBRARY_ARCHIVE_PATH = "declarations/lib.d.ts"
        const val DEFAULT_LIBRARY_FINGERPRINT_PATH = "lib/lib.d.ts"
        const val DEFAULT_LIBRARY_VFS_PATH = "/declarations/lib.d.ts"
        const val DEFAULT_PACKAGE_TARGET_PATH = "packages/alpha/index.d.ts"
        val DISTRIBUTION_FINGERPRINT = R2Digests.sha256("distribution".toByteArray())
        val RUNTIME_FINGERPRINT =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.runtimeCapabilitiesFingerprint
        val DEFAULT_DEPENDENCY_ROOTS = listOf(
            "packages/@scope/beta",
            "packages/alpha",
        )
    }
}
