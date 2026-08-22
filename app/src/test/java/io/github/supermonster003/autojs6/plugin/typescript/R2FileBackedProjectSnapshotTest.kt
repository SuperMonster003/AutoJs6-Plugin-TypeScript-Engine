package io.github.supermonster003.autojs6.plugin.typescript

import java.io.RandomAccessFile
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class R2FileBackedProjectSnapshotTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun constructorIndependentlyRejectsReservedRecordPathsWithAsciiCaseFold() {
        RESERVED_PATHS.forEachIndexed { index, path ->
            withReader("record-$index.spool") { reader ->
                assertThrows(IllegalArgumentException::class.java) {
                    snapshot(
                        reader = reader,
                        records = arrayListOf(record(path)),
                        rootNames = arrayListOf(VALID_ROOT),
                    )
                }
            }
        }
    }

    @Test
    fun constructorIndependentlyRejectsReservedRootPathsWithAsciiCaseFold() {
        RESERVED_PATHS.forEachIndexed { index, path ->
            withReader("root-$index.spool") { reader ->
                assertThrows(IllegalArgumentException::class.java) {
                    snapshot(
                        reader = reader,
                        records = arrayListOf(record(VALID_ROOT)),
                        rootNames = arrayListOf(path),
                    )
                }
            }
        }
    }

    @Test
    fun constructorOwnsRecordAndRootCollectionsAfterValidation() {
        val callerRecords = arrayListOf(record(VALID_ROOT))
        val callerRootNames = arrayListOf(VALID_ROOT)
        withReader("owned-collections.spool") { reader ->
            val snapshot = snapshot(
                reader = reader,
                records = callerRecords,
                rootNames = callerRootNames,
            )
            try {
                callerRecords.clear()
                callerRootNames.clear()

                assertEquals(listOf(VALID_ROOT), snapshot.fileInventory.map { file -> file.path })
                assertEquals(setOf(VALID_ROOT), snapshot.filePaths)
                assertEquals(listOf(VALID_ROOT), snapshot.rootNames)
                assertEquals(VALID_ROOT, snapshot.entryPath)
                snapshot.openFile(0).use { cursor ->
                    assertEquals(0L, cursor.remainingBytes)
                }
            } finally {
                snapshot.close()
            }
        }
    }

    @Test
    fun nodeRevisionTwoConstructorIndependentlyAdmitsCompleteTsMtsCtsRoots() {
        val moduleSource = "/sources/module.mts"
        val commonJsSource = "/sources/common.cts"
        withReader("node2-source-kinds.spool") { reader ->
            val snapshot = snapshot(
                reader = reader,
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                records = arrayListOf(
                    record(DEFAULT_LIBRARY),
                    record(VALID_ROOT),
                    record(moduleSource),
                    record(commonJsSource),
                ),
                rootNames = arrayListOf(moduleSource, VALID_ROOT, commonJsSource),
                entryPath = commonJsSource,
            )
            try {
                assertEquals(
                    listOf(moduleSource, VALID_ROOT, commonJsSource),
                    snapshot.rootNames,
                )
                assertEquals(commonJsSource, snapshot.entryPath)
                assertEquals(
                    setOf(DEFAULT_LIBRARY, VALID_ROOT, moduleSource, commonJsSource),
                    snapshot.filePaths,
                )
            } finally {
                snapshot.close()
            }
        }
    }

    @Test
    fun fileBackedConstructorKeepsNodeKindsRevisionAndDeclarationBound() {
        listOf("/sources/main.mts", "/sources/main.cts").forEachIndexed { index, path ->
            withReader("node1-source-kind-$index.spool") { reader ->
                assertThrows(IllegalArgumentException::class.java) {
                    snapshot(
                        reader = reader,
                        profileAuthority =
                            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
                        records = arrayListOf(record(DEFAULT_LIBRARY), record(path)),
                        rootNames = arrayListOf(path),
                    )
                }
            }
        }
        listOf(
            "/sources/types.d.mts",
            "/sources/types.d.cts",
            "/declarations/types.d.mts",
            "/declarations/types.d.cts",
        ).forEachIndexed { index, path ->
            withReader("node2-declaration-kind-$index.spool") { reader ->
                assertThrows(IllegalArgumentException::class.java) {
                    snapshot(
                        reader = reader,
                        profileAuthority =
                            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                        records = arrayListOf(
                            record(DEFAULT_LIBRARY),
                            record(VALID_ROOT),
                            record(path),
                        ),
                        rootNames = arrayListOf(VALID_ROOT),
                    )
                }
            }
        }
    }

    @Test
    fun constructorIndependentlyRejectsTamperedMissingAndWrongKindEntries() {
        val missingEntry = "/sources/missing.ts"
        val undeclaredEntry = "/sources/other.ts"
        val declarationEntry = "/sources/types.d.ts"
        listOf(
            Triple(arrayListOf(record(VALID_ROOT)), arrayListOf(VALID_ROOT), missingEntry),
            Triple(
                arrayListOf(record(VALID_ROOT), record(undeclaredEntry)),
                arrayListOf(VALID_ROOT),
                undeclaredEntry,
            ),
            Triple(
                arrayListOf(record(VALID_ROOT), record(declarationEntry)),
                arrayListOf(VALID_ROOT),
                declarationEntry,
            ),
        ).forEachIndexed { index, (records, roots, entryPath) ->
            withReader("invalid-entry-$index.spool") { reader ->
                assertThrows(IllegalArgumentException::class.java) {
                    snapshot(
                        reader = reader,
                        records = records,
                        rootNames = roots,
                        entryPath = entryPath,
                    )
                }
            }
        }
    }

    @Test
    fun constructorOwnsIndependentPackageMetadataRecordsAndCursors() {
        val packageBytes = "{\"name\":\"controlled\"}\n".toByteArray()
        val packageFile = temporaryFolder.newFile("package-record.spool")
        RandomAccessFile(packageFile, "rw").use { writer ->
            writer.write(packageBytes)
        }
        val inventory = packageMetadataInventory(packageBytes)
        val callerRecords = arrayListOf(
            R2FileBackedPackageMetadataRecord(
                metadata = inventory.files.single(),
                offset = 0L,
            ),
        )
        RandomAccessFile(packageFile, "r").let { reader ->
            val snapshot = snapshot(
                reader = reader,
                records = arrayListOf(record(VALID_ROOT)),
                rootNames = arrayListOf(VALID_ROOT),
                rawConfigRecord = emptyRawConfigRecord(packageBytes.size.toLong()),
                packageMetadataInventory = inventory,
                packageMetadataRecords = callerRecords,
            )
            callerRecords.clear()

            assertEquals(inventory, snapshot.packageMetadataInventory)
            assertEquals(listOf(VALID_ROOT), snapshot.fileInventory.map { it.path })
            assertFalse(snapshot.filePaths.contains(PACKAGE_JSON_VFS))
            assertEquals(listOf(VALID_ROOT), snapshot.rootNames)
            assertArrayEquals(packageBytes, readAll(snapshot.openPackageMetadataFile(0)))
            assertThrows(IndexOutOfBoundsException::class.java) {
                snapshot.openPackageMetadataFile(1)
            }

            val invalidatedCursor = snapshot.openPackageMetadataFile(0)
            snapshot.close()
            assertThrows(IllegalStateException::class.java) {
                snapshot.openPackageMetadataFile(0)
            }
            assertThrows(IllegalStateException::class.java) {
                invalidatedCursor.read(ByteArray(1), 0, 1)
            }
            invalidatedCursor.close()
        }
    }

    @Test
    fun nodeRevisionTwoFileBackedSnapshotRebuildsCatalogFromImmutableRecords() {
        val packageBytes = "{\"type\":\"module\"}\n".toByteArray()
        val packageFile = temporaryFolder.newFile("node2-package-record.spool")
        RandomAccessFile(packageFile, "rw").use { writer -> writer.write(packageBytes) }
        val inventory = packageMetadataInventory(packageBytes)
        val catalog = R2NodePackageFormatCatalog.create(inventory, listOf(packageBytes))
        val metadataCatalog = R2NodePackageMetadataCatalog.create(
            inventory,
            listOf(packageBytes),
        )
        RandomAccessFile(packageFile, "r").let { reader ->
            val snapshot = snapshot(
                reader = reader,
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                records = arrayListOf(record(VALID_ROOT)),
                rootNames = arrayListOf(VALID_ROOT),
                packageMetadataInventory = inventory,
                packageMetadataRecords = arrayListOf(
                    R2FileBackedPackageMetadataRecord(inventory.files.single(), 0L),
                ),
                nodePackageFormatCatalog = catalog,
                nodePackageMetadataCatalog = metadataCatalog,
            )
            assertEquals(catalog.fingerprint, snapshot.nodePackageFormatCatalog?.fingerprint)
            assertEquals(
                R2PackageTypeState.MODULE,
                snapshot.nodePackageFormatCatalog?.records?.single()?.typeState,
            )
            snapshot.close()
            assertEquals(catalog.fingerprint, snapshot.nodePackageFormatCatalog?.fingerprint)
            assertThrows(IllegalStateException::class.java) {
                snapshot.openPackageMetadataFile(0)
            }
        }

        RandomAccessFile(packageFile, "r").use { reader ->
            assertThrows(IllegalArgumentException::class.java) {
                snapshot(
                    reader = reader,
                    profileAuthority =
                        R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                    records = arrayListOf(record(VALID_ROOT)),
                    rootNames = arrayListOf(VALID_ROOT),
                    packageMetadataInventory = inventory,
                    packageMetadataRecords = arrayListOf(
                        R2FileBackedPackageMetadataRecord(inventory.files.single(), 0L),
                    ),
                    nodePackageFormatCatalog = null,
                )
            }
        }
    }

    @Test
    fun nodeRevisionTwoFileBackedV2TargetsOwnIndependentRecordsAndInvalidateCursors() {
        val rootPackage = "{\"type\":\"module\"}\n".toByteArray()
        val dependencyPackage = "{\"type\":\"module\"}\n".toByteArray()
        val targetBytes = "export const value = 1;\n".toByteArray()
        val targetPath = "$PACKAGE_DEPENDENCY_ROOT/index.mts"
        val packageFile = temporaryFolder.newFile("node2-v2-target-record.spool")
        RandomAccessFile(packageFile, "rw").use { writer ->
            writer.write(rootPackage)
            writer.write(dependencyPackage)
            writer.write(targetBytes)
        }
        val metadata = packageMetadataInventory(rootPackage, dependencyPackage)
        val metadataBytes = listOf(rootPackage, dependencyPackage)
        val formatCatalog = R2NodePackageFormatCatalog.create(metadata, metadataBytes)
        val metadataCatalog = R2NodePackageMetadataCatalog.create(metadata, metadataBytes)
        val targets = R2NodePackageTargetV2Inventory.create(
            packageMetadataInventory = metadata,
            files = listOf(
                R2NodePackageTargetV2FileDescriptor(
                    path = targetPath,
                    size = targetBytes.size.toLong(),
                    sha256 = R2Digests.sha256(targetBytes),
                ),
            ),
        )
        val targetRecords = arrayListOf(
            R2FileBackedNodePackageTargetV2Record(
                descriptor = targets.files.single(),
                offset = rootPackage.size.toLong() + dependencyPackage.size,
            ),
        )
        RandomAccessFile(packageFile, "r").let { reader ->
            val snapshot = snapshot(
                reader = reader,
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                records = arrayListOf(record(VALID_ROOT)),
                rootNames = arrayListOf(VALID_ROOT),
                packageMetadataInventory = metadata,
                packageMetadataRecords = arrayListOf(
                    R2FileBackedPackageMetadataRecord(metadata.files[0], 0L),
                    R2FileBackedPackageMetadataRecord(
                        metadata.files[1],
                        rootPackage.size.toLong(),
                    ),
                ),
                nodePackageFormatCatalog = formatCatalog,
                nodePackageMetadataCatalog = metadataCatalog,
                nodePackageTargetV2Inventory = targets,
                nodePackageTargetV2Records = targetRecords,
            )
            targetRecords.clear()

            assertEquals(targets, snapshot.nodePackageTargetV2Inventory)
            assertEquals(null, snapshot.packageTargetInventory)
            assertArrayEquals(targetBytes, readAll(snapshot.openNodePackageTargetV2File(0)))
            assertFalse(snapshot.filePaths.contains("/$targetPath"))
            assertThrows(IndexOutOfBoundsException::class.java) {
                snapshot.openNodePackageTargetV2File(1)
            }

            val invalidatedCursor = snapshot.openNodePackageTargetV2File(0)
            snapshot.close()
            assertThrows(IllegalStateException::class.java) {
                snapshot.openNodePackageTargetV2File(0)
            }
            assertThrows(IllegalStateException::class.java) {
                invalidatedCursor.read(ByteArray(1), 0, 1)
            }
            invalidatedCursor.close()
        }
    }

    @Test
    fun constructorOwnsIndependentPackageTargetRecordsAndInvalidatesCursors() {
        val rootPackage = "{\"name\":\"project\"}\n".toByteArray()
        val dependencyPackage = "{\"name\":\"dependency\"}\n".toByteArray()
        val targetBytes = "export declare const answer: number;\n".toByteArray()
        val packageFile = temporaryFolder.newFile("package-target-record.spool")
        RandomAccessFile(packageFile, "rw").use { writer ->
            writer.write(rootPackage)
            writer.write(dependencyPackage)
            writer.write(targetBytes)
        }
        val metadata = packageMetadataInventory(rootPackage, dependencyPackage)
        val targets = packageTargetInventory(metadata, targetBytes)
        val targetOffset = rootPackage.size.toLong() + dependencyPackage.size
        val callerTargetRecords = arrayListOf(
            R2FileBackedPackageTargetRecord(
                descriptor = targets.files.single(),
                offset = targetOffset,
            ),
        )
        RandomAccessFile(packageFile, "r").let { reader ->
            val snapshot = snapshot(
                reader = reader,
                records = arrayListOf(record(VALID_ROOT)),
                rootNames = arrayListOf(VALID_ROOT),
                rawConfigRecord = emptyRawConfigRecord(packageFile.length()),
                packageMetadataInventory = metadata,
                packageMetadataRecords = arrayListOf(
                    R2FileBackedPackageMetadataRecord(
                        metadata = metadata.files[0],
                        offset = 0L,
                    ),
                    R2FileBackedPackageMetadataRecord(
                        metadata = metadata.files[1],
                        offset = rootPackage.size.toLong(),
                    ),
                ),
                packageTargetInventory = targets,
                packageTargetRecords = callerTargetRecords,
            )
            callerTargetRecords.clear()

            assertEquals(targets, snapshot.packageTargetInventory)
            assertEquals(listOf(VALID_ROOT), snapshot.fileInventory.map { it.path })
            assertFalse(snapshot.filePaths.contains(PACKAGE_TARGET_VFS))
            assertEquals(listOf(VALID_ROOT), snapshot.rootNames)
            assertArrayEquals(targetBytes, readAll(snapshot.openPackageTargetFile(0)))
            assertThrows(IndexOutOfBoundsException::class.java) {
                snapshot.openPackageTargetFile(1)
            }

            val invalidatedCursor = snapshot.openPackageTargetFile(0)
            snapshot.close()
            assertThrows(IllegalStateException::class.java) {
                snapshot.openPackageTargetFile(0)
            }
            assertThrows(IllegalStateException::class.java) {
                invalidatedCursor.read(ByteArray(1), 0, 1)
            }
            invalidatedCursor.close()
        }
    }

    @Test
    fun constructorRejectsTamperedPackageTargetDigestAndC5Identity() {
        val rootPackage = "{}".toByteArray()
        val dependencyPackage = "{\"name\":\"dependency\"}".toByteArray()
        val acceptedTarget = "export declare const value: string;".toByteArray()
        val changedTarget = acceptedTarget.copyOf().apply { this[lastIndex] = ' '.code.toByte() }
        val metadata = packageMetadataInventory(rootPackage, dependencyPackage)
        val targets = packageTargetInventory(metadata, acceptedTarget)
        val packageFile = temporaryFolder.newFile("tampered-package-target.spool")
        RandomAccessFile(packageFile, "rw").use { writer ->
            writer.write(rootPackage)
            writer.write(dependencyPackage)
            writer.write(changedTarget)
        }
        val metadataRecords = arrayListOf(
            R2FileBackedPackageMetadataRecord(metadata.files[0], 0L),
            R2FileBackedPackageMetadataRecord(
                metadata.files[1],
                rootPackage.size.toLong(),
            ),
        )
        val targetRecords = arrayListOf(
            R2FileBackedPackageTargetRecord(
                targets.files.single(),
                rootPackage.size.toLong() + dependencyPackage.size,
            ),
        )

        RandomAccessFile(packageFile, "r").use { reader ->
            assertThrows(IllegalArgumentException::class.java) {
                snapshot(
                    reader = reader,
                    records = arrayListOf(record(VALID_ROOT)),
                    rootNames = arrayListOf(VALID_ROOT),
                    rawConfigRecord = emptyRawConfigRecord(packageFile.length()),
                    packageMetadataInventory = metadata,
                    packageMetadataRecords = metadataRecords,
                    packageTargetInventory = targets,
                    packageTargetRecords = targetRecords,
                )
            }
        }

        val otherRootPackage = "{\"name\":\"other\"}".toByteArray()
        val otherMetadata = packageMetadataInventory(otherRootPackage, dependencyPackage)
        RandomAccessFile(packageFile, "r").use { reader ->
            assertThrows(IllegalArgumentException::class.java) {
                snapshot(
                    reader = reader,
                    records = arrayListOf(record(VALID_ROOT)),
                    rootNames = arrayListOf(VALID_ROOT),
                    rawConfigRecord = emptyRawConfigRecord(packageFile.length()),
                    packageMetadataInventory = otherMetadata,
                    packageMetadataRecords = metadataRecords,
                    packageTargetInventory = targets,
                    packageTargetRecords = targetRecords,
                )
            }
        }

        val invalidUtf8 = byteArrayOf(0xc3.toByte(), 0x28)
        val invalidTargets = packageTargetInventory(metadata, invalidUtf8)
        val invalidFile = temporaryFolder.newFile("invalid-utf8-package-target.spool")
        RandomAccessFile(invalidFile, "rw").use { writer ->
            writer.write(rootPackage)
            writer.write(dependencyPackage)
            writer.write(invalidUtf8)
        }
        RandomAccessFile(invalidFile, "r").use { reader ->
            assertThrows(IllegalArgumentException::class.java) {
                snapshot(
                    reader = reader,
                    records = arrayListOf(record(VALID_ROOT)),
                    rootNames = arrayListOf(VALID_ROOT),
                    rawConfigRecord = emptyRawConfigRecord(invalidFile.length()),
                    packageMetadataInventory = metadata,
                    packageMetadataRecords = metadataRecords,
                    packageTargetInventory = invalidTargets,
                    packageTargetRecords = arrayListOf(
                        R2FileBackedPackageTargetRecord(
                            invalidTargets.files.single(),
                            rootPackage.size.toLong() + dependencyPackage.size,
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun constructorRejectsPackageMetadataWithoutConfigExactInventoryOrValidRange() {
        val packageBytes = "{}".toByteArray()
        val inventory = packageMetadataInventory(packageBytes)
        val packageFile = temporaryFolder.newFile("invalid-package-record.spool")
        RandomAccessFile(packageFile, "rw").use { writer -> writer.write(packageBytes) }

        RandomAccessFile(packageFile, "r").use { reader ->
            assertThrows(IllegalArgumentException::class.java) {
                snapshot(
                    reader = reader,
                    records = arrayListOf(record(VALID_ROOT)),
                    rootNames = arrayListOf(VALID_ROOT),
                    packageMetadataInventory = inventory,
                    packageMetadataRecords = arrayListOf(
                        R2FileBackedPackageMetadataRecord(
                            inventory.files.single(),
                            0L,
                        ),
                    ),
                )
            }
        }
        RandomAccessFile(packageFile, "r").use { reader ->
            assertThrows(IllegalArgumentException::class.java) {
                snapshot(
                    reader = reader,
                    records = arrayListOf(record(VALID_ROOT)),
                    rootNames = arrayListOf(VALID_ROOT),
                    rawConfigRecord = emptyRawConfigRecord(packageBytes.size.toLong()),
                    packageMetadataInventory = inventory,
                    packageMetadataRecords = arrayListOf(),
                )
            }
        }
        RandomAccessFile(packageFile, "r").use { reader ->
            assertThrows(IllegalArgumentException::class.java) {
                snapshot(
                    reader = reader,
                    records = arrayListOf(record(VALID_ROOT)),
                    rootNames = arrayListOf(VALID_ROOT),
                    rawConfigRecord = emptyRawConfigRecord(packageBytes.size.toLong()),
                    packageMetadataInventory = inventory,
                    packageMetadataRecords = arrayListOf(
                        R2FileBackedPackageMetadataRecord(
                            inventory.files.single(),
                            packageBytes.size.toLong() + 1L,
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun sealRejectsCombinedPackageMetadataCapacityAndRetainsCleanupOwnership() {
        val privateDirectory = temporaryFolder.newFolder("package-capacity-spool")
        val packageBytes = "{}".toByteArray()
        val inventory = packageMetadataInventory(packageBytes)

        R2PrivateProjectSpool.create(privateDirectory).use { spool ->
            spool.finishArchiveExtraction()
            assertThrows(IllegalArgumentException::class.java) {
                spool.seal(
                    profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                    projectRecords = listOf(record(VALID_ROOT)),
                    rootNames = listOf(VALID_ROOT),
                    limits = R2ProjectSnapshotLimits(
                        maximumFiles = 3,
                        maximumSingleFileBytes = packageBytes.size.toLong(),
                        maximumTotalBytes = packageBytes.size.toLong() - 1L,
                    ),
                    rawConfigRecord = emptyRawConfigRecord(0L),
                    packageMetadataInventory = inventory,
                    packageMetadataRecords = listOf(
                        R2FileBackedPackageMetadataRecord(
                            metadata = inventory.files.single(),
                            offset = 0L,
                        ),
                    ),
                )
            }
        }

        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun nodeRevisionTwoSealAdmitsTsMtsCtsAndRevisionOneStillRejectsThem() {
        val moduleSource = "/sources/module.mts"
        val commonJsSource = "/sources/common.cts"
        val records = listOf(
            record(DEFAULT_LIBRARY),
            record(VALID_ROOT),
            record(moduleSource),
            record(commonJsSource),
        )
        val roots = listOf(moduleSource, VALID_ROOT, commonJsSource)
        val privateDirectory = temporaryFolder.newFolder("node2-source-kind-seal")

        val snapshot = R2PrivateProjectSpool.create(privateDirectory).use { spool ->
            spool.finishArchiveExtraction()
            spool.seal(
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                projectRecords = records,
                rootNames = roots,
                limits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE,
                entryPath = moduleSource,
            )
        }
        snapshot.use {
            assertEquals(listOf(commonJsSource, VALID_ROOT, moduleSource), snapshot.rootNames)
            assertEquals(moduleSource, snapshot.entryPath)
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())

        listOf(moduleSource, commonJsSource).forEachIndexed { index, path ->
            val rejectedDirectory = temporaryFolder.newFolder("node1-source-kind-seal-$index")
            R2PrivateProjectSpool.create(rejectedDirectory).use { spool ->
                spool.finishArchiveExtraction()
                assertThrows(IllegalArgumentException::class.java) {
                    spool.seal(
                        profileAuthority =
                            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
                        projectRecords = listOf(record(DEFAULT_LIBRARY), record(path)),
                        rootNames = listOf(path),
                        limits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE,
                    )
                }
            }
            assertTrue(rejectedDirectory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun sealCountsPackageTargetsAgainstTheSharedCapacityBudget() {
        val privateDirectory = temporaryFolder.newFolder("package-target-capacity-spool")
        val emptyPackage = ByteArray(0)
        val targetBytes = "ab".toByteArray()
        val metadata = packageMetadataInventory(emptyPackage, emptyPackage)
        val targets = packageTargetInventory(metadata, targetBytes)

        R2PrivateProjectSpool.create(privateDirectory).use { spool ->
            spool.finishArchiveExtraction()
            assertThrows(IllegalArgumentException::class.java) {
                spool.seal(
                    profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                    projectRecords = listOf(record(VALID_ROOT)),
                    rootNames = listOf(VALID_ROOT),
                    limits = R2ProjectSnapshotLimits(
                        maximumFiles = 5,
                        maximumSingleFileBytes = targetBytes.size.toLong(),
                        maximumTotalBytes = targetBytes.size.toLong() - 1L,
                    ),
                    rawConfigRecord = emptyRawConfigRecord(0L),
                    packageMetadataInventory = metadata,
                    packageMetadataRecords = listOf(
                        R2FileBackedPackageMetadataRecord(metadata.files[0], 0L),
                        R2FileBackedPackageMetadataRecord(metadata.files[1], 0L),
                    ),
                    packageTargetInventory = targets,
                    packageTargetRecords = listOf(
                        R2FileBackedPackageTargetRecord(targets.files.single(), 0L),
                    ),
                )
            }
        }

        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    private fun snapshot(
        reader: RandomAccessFile,
        profileAuthority: R2CompilationProfileAuthority =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        records: ArrayList<R2FileBackedProjectRecord>,
        rootNames: ArrayList<String>,
        entryPath: String = rootNames.firstOrNull().orEmpty(),
        rawConfigRecord: R2FileBackedRawProjectConfigRecord? = null,
        packageMetadataInventory: R2PackageMetadataInventory? = null,
        packageMetadataRecords: ArrayList<R2FileBackedPackageMetadataRecord> = arrayListOf(),
        nodePackageFormatCatalog: R2NodePackageFormatCatalog? = null,
        nodePackageMetadataCatalog: R2NodePackageMetadataCatalog? = null,
        packageTargetInventory: R2PackageTargetInventory? = null,
        packageTargetRecords: ArrayList<R2FileBackedPackageTargetRecord> = arrayListOf(),
        nodePackageTargetV2Inventory: R2NodePackageTargetV2Inventory? = null,
        nodePackageTargetV2Records: ArrayList<R2FileBackedNodePackageTargetV2Record> = arrayListOf(),
    ): R2FileBackedProjectSnapshot = R2FileBackedProjectSnapshot(
        immutableReader = reader,
        fallbackFile = null,
        profileAuthority = profileAuthority,
        rawConfigRecord = rawConfigRecord,
        packageMetadataInventory = packageMetadataInventory,
        packageMetadataRecords = packageMetadataRecords,
        nodePackageFormatCatalog = nodePackageFormatCatalog,
        nodePackageMetadataCatalog = nodePackageMetadataCatalog,
        packageTargetInventory = packageTargetInventory,
        packageTargetRecords = packageTargetRecords,
        nodePackageTargetV2Inventory = nodePackageTargetV2Inventory,
        nodePackageTargetV2Records = nodePackageTargetV2Records,
        records = records,
        rootNames = rootNames,
        entryPath = entryPath,
        totalUtf8Bytes = 0L,
        largestFileUtf8Bytes = 0L,
    )

    private fun record(path: String) = R2FileBackedProjectRecord(
        metadata = R2ProjectFileMetadata(
            path = path,
            utf8Bytes = 0L,
            utf16CodeUnits = 0,
            sha256 = EMPTY_SHA256,
        ),
        offset = 0L,
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
        rootPackageBytes: ByteArray,
        dependencyPackageBytes: ByteArray,
    ) = R2PackageMetadataInventory.create(
        dependencyRoots = listOf(PACKAGE_DEPENDENCY_ROOT),
        files = listOf(
            R2PackageMetadataFileDescriptor(
                path = PACKAGE_JSON_ARCHIVE,
                size = rootPackageBytes.size.toLong(),
                sha256 = R2Digests.sha256(rootPackageBytes),
            ),
            R2PackageMetadataFileDescriptor(
                path = "$PACKAGE_DEPENDENCY_ROOT/package.json",
                size = dependencyPackageBytes.size.toLong(),
                sha256 = R2Digests.sha256(dependencyPackageBytes),
            ),
        ),
    )

    private fun packageTargetInventory(
        metadata: R2PackageMetadataInventory,
        bytes: ByteArray,
    ) = R2PackageTargetInventory.create(
        packageMetadataInventory = metadata,
        files = listOf(
            R2PackageTargetFileDescriptor(
                path = PACKAGE_TARGET_ARCHIVE,
                size = bytes.size.toLong(),
                sha256 = R2Digests.sha256(bytes),
            ),
        ),
    )

    private fun emptyRawConfigRecord(offset: Long) =
        R2FileBackedRawProjectConfigRecord(
            descriptor = R2RawProjectConfigDescriptor(
                path = R2ControlledTsConfigPolicy.CONFIG_PATH,
                size = 0L,
                sha256 = EMPTY_SHA256,
                policyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
                policyFingerprint = R2ControlledTsConfigPolicy.fingerprint,
            ),
            offset = offset,
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

    private inline fun withReader(
        name: String,
        block: (RandomAccessFile) -> Unit,
    ) {
        RandomAccessFile(temporaryFolder.newFile(name), "r").use(block)
    }

    private companion object {
        const val DEFAULT_LIBRARY = "/declarations/lib.d.ts"
        const val VALID_ROOT = "/sources/main.ts"
        const val PACKAGE_JSON_ARCHIVE = "package.json"
        const val PACKAGE_JSON_VFS = "/package.json"
        const val PACKAGE_DEPENDENCY_ROOT = "packages/dependency"
        const val PACKAGE_TARGET_ARCHIVE = "$PACKAGE_DEPENDENCY_ROOT/index.d.ts"
        const val PACKAGE_TARGET_VFS = "/$PACKAGE_TARGET_ARCHIVE"
        const val EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val RESERVED_PATHS = listOf(
            "/sources/package.json",
            "/sources/Package.Json",
            "/sources/node_modules/pkg/main.ts",
            "/sources/Node_Modules/pkg/main.ts",
            "/sources/main.mts",
            "/sources/main.MTS",
            "/sources/main.cts",
            "/sources/main.CTS",
            "/declarations/Package.Json",
            "/declarations/Node_Modules/pkg/main.d.ts",
            "/declarations/main.d.MTS",
        )
    }
}
