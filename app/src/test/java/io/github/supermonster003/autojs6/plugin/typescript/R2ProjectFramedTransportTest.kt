package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class R2ProjectFramedTransportTest {

    @Test
    fun configNoneProjectStartCarriesFrozenInputAuthority() {
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile("/declarations/lib.d.ts", ""),
                R2ProjectTextFile("/sources/main.ts", "export {};\n"),
            ),
            rootNames = listOf("/sources/main.ts"),
        )
        val authority = snapshot.profileAuthority
        val framer = R2ProjectInputFramer("legacy-none", snapshot)

        val start = parseFrame(framer.nextFrame())
        assertEquals("projectInputStart", start["kind"])
        assertEquals(authority.configKind, start["configKind"])
        assertInputAuthority(start, snapshot)
        framer.release()
    }

    @Test
    fun ordinaryAndTestOnlyExpandedWorkerQuotaAuthoritiesStaySeparated() {
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile("/declarations/lib.d.ts", ""),
                R2ProjectTextFile("/sources/main.ts", "export {};\n"),
            ),
            rootNames = listOf("/sources/main.ts"),
        )
        val ordinary = R2ProjectInputFramer("ordinary-quota", snapshot)
        val expanded = R2ProjectInputFramer(
            requestId = "test-only-expanded-quota",
            snapshot = snapshot,
            workerInputQuotaAuthority = R2WorkerInputQuotaAuthority.TEST_ONLY_EXPANDED,
        )
        try {
            assertEquals(
                TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION,
                parseFrame(ordinary.nextFrame()).intValue("inputQuotaRevision"),
            )
            assertEquals(
                Int.MAX_VALUE,
                parseFrame(expanded.nextFrame()).intValue("inputQuotaRevision"),
            )
        } finally {
            ordinary.release()
            expanded.release()
            snapshot.close()
        }
    }

    @Test
    fun revisionTwoAcceptedAuthoritySelectsTheLegacyWorkerQuotaAutomatically() {
        val files = listOf(
            R2ProjectTextFile("/declarations/lib.d.ts", ""),
            R2ProjectTextFile("/sources/main.ts", "export {};\n"),
        )
        val roots = listOf("/sources/main.ts")
        val legacyAuthority = R2ProjectSnapshot.create(files = files, rootNames = roots).use {
            R2InputArchiveSchemaV1.compatibilityAuthority(it).copy(
                inputQuotaRevision =
                    TypeScriptR2Contract.LEGACY_ORDINARY_COMPILATION_INPUT_QUOTA_REVISION,
            )
        }
        val snapshot = R2ProjectSnapshot.create(
            files = files,
            rootNames = roots,
            limits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE,
            acceptedInputAuthority = legacyAuthority,
        )
        val framer = R2ProjectInputFramer("legacy-revision-two", snapshot)
        try {
            assertEquals(
                TypeScriptR2Contract.LEGACY_ORDINARY_COMPILATION_INPUT_QUOTA_REVISION,
                parseFrame(framer.nextFrame()).intValue("inputQuotaRevision"),
            )
        } finally {
            framer.release()
            snapshot.close()
        }
    }

    @Test
    fun projectScopedFramerRejectsMissingAcceptedInputAuthority() {
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile("/declarations/lib.d.ts", ""),
                R2ProjectTextFile("/sources/main.ts", "export {};\n"),
            ),
            rootNames = listOf("/sources/main.ts"),
            acceptedProjectId = "project-scoped",
        )
        try {
            assertThrows(IllegalArgumentException::class.java) {
                R2ProjectInputFramer("missing-authority", snapshot)
            }
        } finally {
            snapshot.close()
        }
    }

    @Test
    fun nodeRevisionTwoNoBoundaryUsesExactThirtyFieldStartAndNoConfigFrames() {
        val snapshot = nodeRevisionTwoSnapshot()
        val authority = snapshot.profileAuthority
        val resolverSnapshot = R2NodeResolutionV2SnapshotTargetInventory.create(
            profileAuthority = authority,
            files = snapshot.fileInventory,
        )
        val resolverAuthority = R2NodeResolutionV2Authority(
            snapshotTargets = resolverSnapshot,
            packageMetadata = null,
            packageFormat = null,
            packageTargets = null,
            profileAuthority = authority,
        )
        val framer = R2ProjectInputFramer("node2-no-boundary", snapshot)
        try {
            val encodedStart = framer.nextFrame()
            val start = parseFrame(encodedStart)
            assertEquals(
                INPUT_AUTHORITY_KEYS + setOf(
                    "kind",
                    "requestId",
                    "sequence",
                    "targetProfile",
                    "profileSchemaRevision",
                    "profileRevision",
                    "configSchemaRevision",
                    "configKind",
                    "entryPath",
                    "runtimeCapabilitiesFingerprint",
                    "fileCount",
                    "totalBytes",
                    "workerSpinMilliseconds",
                    "workerSpinPhase",
                    "nodeModuleOutputPolicySchemaRevision",
                    "nodeModuleOutputPolicyRevision",
                    "nodeModuleOutputPolicyFingerprint",
                    "resolverPolicyRevision",
                    "snapshotTargetInventoryFingerprint",
                    "targetUniverseFingerprint",
                ),
                start.keys,
            )
            assertEquals(30, start.size)
            assertInputAuthority(start, snapshot)
            assertEquals(
                INPUT_AUTHORITY_KEY_ORDER,
                start.keys.toList().take(INPUT_AUTHORITY_KEY_ORDER.size),
            )
            assertFails { framer.nextFrame() }
            framer.acknowledge(0)

            var sequence = 1
            val transportedPaths = ArrayList<String>()
            while (!framer.isComplete) {
                val frame = parseFrame(framer.nextFrame())
                assertFalse((frame["kind"] as String).startsWith("projectInputConfig"))
                if (frame["kind"] == "projectInputFileStart") {
                    transportedPaths += frame["path"] as String
                }
                framer.acknowledge(sequence++)
            }
            assertEquals(
                snapshot.fileInventory.map(R2ProjectFileMetadata::path),
                transportedPaths,
            )
            assertEquals(snapshot.fileInventory.size, framer.telemetryForTesting().openedCursorCount)
        } finally {
            snapshot.close()
        }
    }

    @Test
    fun nodeRevisionTwoFramesTheDependencyLayerAfterTheOrdinaryInventory() {
        val dependencyFiles = listOf(
            "dependencies/lockfiles/package-lock.json" to
                "{\"lockfileVersion\":3}".toByteArray(StandardCharsets.UTF_8),
            "dependencies/node_modules/dayjs/index.d.ts" to
                "declare function dayjs(): string; export = dayjs;\n"
                    .toByteArray(StandardCharsets.UTF_8),
            "dependencies/node_modules/dayjs/package.json" to
                "{\"name\":\"dayjs\",\"types\":\"index.d.ts\"}"
                    .toByteArray(StandardCharsets.UTF_8),
        )
        val snapshot = nodeRevisionTwoSnapshot(
            sourcePath = "/sources/main.mts",
            dependencyLayerFiles = dependencyFiles,
        )
        val framer = R2ProjectInputFramer("t2-2-dependency-framing", snapshot)
        try {
            val start = parseFrame(framer.nextFrame())
            assertEquals(
                R2DependencyResolverPolicy.POLICY_REVISION,
                start.intValue("dependencyResolverPolicyRevision"),
            )
            assertEquals(
                R2DependencyResolverPolicy.fingerprint,
                start["dependencyResolverPolicyFingerprint"],
            )
            assertEquals(
                dependencyFiles.size,
                start.intValue("dependencyLayerFileCount"),
            )
            assertEquals(3, start.intValue("fileCount"))
            framer.acknowledge(0)

            val paths = ArrayList<String>()
            var endFileCount: Int? = null
            var sequence = 1
            while (!framer.isComplete) {
                val frame = parseFrame(framer.nextFrame())
                when (frame["kind"]) {
                    "projectInputFileStart" -> paths += frame["path"] as String
                    "projectInputEnd" -> endFileCount = frame.intValue("fileCount")
                }
                framer.acknowledge(sequence++)
            }
            assertEquals(
                listOf(
                    "/declarations/lib.d.ts",
                    "/declarations/node/index.d.ts",
                    "/sources/main.mts",
                    "/dependency-lockfiles/package-lock.json",
                    "/node_modules/dayjs/index.d.ts",
                    "/node_modules/dayjs/package.json",
                ),
                paths,
            )
            assertEquals(3, endFileCount)
        } finally {
            framer.release()
            snapshot.close()
        }
    }

    @Test
    fun currentRhinoFramesTheDependencyResolverAuthorityAndLayer() {
        val dependencyFiles = listOf(
            "dependencies/lockfiles/package-lock.json" to
                "{\"lockfileVersion\":3}".toByteArray(StandardCharsets.UTF_8),
            "dependencies/node_modules/dual-runtime/index.d.ts" to
                "export declare const value: number;\n"
                    .toByteArray(StandardCharsets.UTF_8),
        )
        val snapshot = rawConfigSnapshot(
            rawConfig = "{}".toByteArray(StandardCharsets.UTF_8),
            dependencyLayerFiles = dependencyFiles,
        )
        val framer = R2ProjectInputFramer("t2-3-rhino-dependency-framing", snapshot)
        try {
            val start = parseFrame(framer.nextFrame())
            assertEquals(
                R2DependencyResolverPolicy.POLICY_REVISION,
                start.intValue("dependencyResolverPolicyRevision"),
            )
            assertEquals(
                R2DependencyResolverPolicy.fingerprint,
                start["dependencyResolverPolicyFingerprint"],
            )
            assertEquals(dependencyFiles.size, start.intValue("dependencyLayerFileCount"))
        } finally {
            snapshot.close()
        }
    }

    @Test
    fun nodeRevisionTwoControlledConfigUsesExactThirtyFourFieldStartAndConfigFirst() {
        val rawConfig =
            "{\"compilerOptions\":{\"target\":\"ES2022\"}}\n"
                .toByteArray(StandardCharsets.UTF_8)
        val snapshot = nodeRevisionTwoSnapshot(rawConfigBytes = rawConfig)
        val descriptor = requireNotNull(snapshot.rawConfigDescriptor)
        val framer = R2ProjectInputFramer("node2-controlled-config", snapshot)
        try {
            val start = parseFrame(framer.nextFrame())
            assertEquals(
                INPUT_AUTHORITY_KEYS + setOf(
                    "kind",
                    "requestId",
                    "sequence",
                    "targetProfile",
                    "profileSchemaRevision",
                    "profileRevision",
                    "configSchemaRevision",
                    "configKind",
                    "entryPath",
                    "runtimeCapabilitiesFingerprint",
                    "fileCount",
                    "totalBytes",
                    "workerSpinMilliseconds",
                    "workerSpinPhase",
                    "nodeModuleOutputPolicySchemaRevision",
                    "nodeModuleOutputPolicyRevision",
                    "nodeModuleOutputPolicyFingerprint",
                    "resolverPolicyRevision",
                    "snapshotTargetInventoryFingerprint",
                    "targetUniverseFingerprint",
                    "configByteLength",
                    "configSha256",
                    "configPolicyRevision",
                    "configPolicyFingerprint",
                ),
                start.keys,
            )
            assertEquals(34, start.size)
            assertInputAuthority(start, snapshot)
            assertEquals(TypeScriptR2Contract.CONFIG_KIND_TSCONFIG, start["configKind"])
            assertEquals(descriptor.size, start.longValue("configByteLength"))
            assertEquals(descriptor.sha256, start["configSha256"])
            assertEquals(descriptor.policyRevision, start.intValue("configPolicyRevision"))
            assertEquals(descriptor.policyFingerprint, start["configPolicyFingerprint"])
            assertEquals(
                snapshot.totalUtf8Bytes + descriptor.size,
                start.longValue("totalBytes"),
            )
            framer.acknowledge(0)

            val configStart = parseFrame(framer.nextFrame())
            assertEquals("projectInputConfigStart", configStart["kind"])
            assertEquals(descriptor.path, configStart["path"])
            framer.acknowledge(1)
            val configChunk = parseFrame(framer.nextFrame())
            assertEquals("projectInputConfigChunk", configChunk["kind"])
            assertArrayEquals(rawConfig, R2FramedBase64.decode(configChunk["data"] as String))
            framer.acknowledge(2)
            val configEnd = parseFrame(framer.nextFrame())
            assertEquals("projectInputConfigEnd", configEnd["kind"])
            framer.acknowledge(3)

            val firstOrdinaryFile = parseFrame(framer.nextFrame())
            assertEquals("projectInputFileStart", firstOrdinaryFile["kind"])
            assertEquals(0, firstOrdinaryFile.intValue("fileIndex"))
        } finally {
            framer.release()
            snapshot.close()
            rawConfig.fill(0)
        }
    }

    @Test
    fun nodeRevisionTwoMetadataUsesExactFortyFieldStartAndPrecedesOrdinaryFiles() {
        val rootPackage = "{\"type\":\"commonjs\"}".toByteArray(StandardCharsets.UTF_8)
        val dependencyPackage = "{\"type\":\"module\"}".toByteArray(StandardCharsets.UTF_8)
        val snapshot = nodeRevisionTwoSnapshot(
            dependencyRoots = listOf(PACKAGE_DEPENDENCY_ROOT),
            packageFiles = listOf(
                PACKAGE_JSON_ARCHIVE to rootPackage,
                DEPENDENCY_PACKAGE_JSON_ARCHIVE to dependencyPackage,
            ),
        )
        val authority = snapshot.profileAuthority
        val inventory = requireNotNull(snapshot.packageMetadataInventory)
        val catalog = requireNotNull(snapshot.nodePackageFormatCatalog)
        val resolverSnapshot = R2NodeResolutionV2SnapshotTargetInventory.create(
            profileAuthority = authority,
            files = snapshot.fileInventory,
        )
        val resolverAuthority = R2NodeResolutionV2Authority(
            snapshotTargets = resolverSnapshot,
            packageMetadata = requireNotNull(snapshot.nodePackageMetadataCatalog),
            packageFormat = catalog,
            packageTargets = null,
            profileAuthority = authority,
        )
        val framer = R2ProjectInputFramer("node2-package-format", snapshot)
        try {
            val encodedStart = framer.nextFrame()
            val start = parseFrame(encodedStart)
            assertEquals(
                INPUT_AUTHORITY_KEYS + setOf(
                    "kind",
                    "requestId",
                    "sequence",
                    "targetProfile",
                    "profileSchemaRevision",
                    "profileRevision",
                    "configSchemaRevision",
                    "configKind",
                    "entryPath",
                    "runtimeCapabilitiesFingerprint",
                    "packageMetadataSchemaRevision",
                    "packageMetadataPolicyRevision",
                    "packageMetadataPolicyFingerprint",
                    "packageMetadataCanonical",
                    "packageMetadataInventoryFingerprint",
                    "packageMetadataFileCount",
                    "packageMetadataByteLength",
                    "nodePackageFormatSchemaRevision",
                    "nodePackageFormatCatalogRevision",
                    "nodePackageFormatCatalogFingerprint",
                    "nodeModuleOutputPolicySchemaRevision",
                    "nodeModuleOutputPolicyRevision",
                    "nodeModuleOutputPolicyFingerprint",
                    "fileCount",
                    "totalBytes",
                    "workerSpinMilliseconds",
                    "workerSpinPhase",
                    "resolverPolicyRevision",
                    "snapshotTargetInventoryFingerprint",
                    "targetUniverseFingerprint",
                ),
                start.keys,
            )
            assertEquals(40, start.size)
            assertInputAuthority(start, snapshot)
            assertEquals(
                INPUT_AUTHORITY_KEY_ORDER,
                start.keys.toList().take(INPUT_AUTHORITY_KEY_ORDER.size),
            )
            assertFalse(start.containsKey("packageMetadataFingerprint"))
            assertFalse(start.containsKey("nodePackageFormatCatalogCanonical"))
            assertFalse(start.containsKey("nodePackageFormatRecords"))
            assertFalse(start.containsKey("packageTypeState"))
            assertFails { framer.nextFrame() }
            framer.acknowledge(0)

            var sequence = 1
            val transportedPaths = ArrayList<String>()
            val transportedIndexes = ArrayList<Int>()
            while (!framer.isComplete) {
                val frame = parseFrame(framer.nextFrame())
                assertFalse((frame["kind"] as String).startsWith("projectInputConfig"))
                if (frame["kind"] == "projectInputFileStart") {
                    transportedPaths += frame["path"] as String
                    transportedIndexes += frame.intValue("fileIndex")
                }
                framer.acknowledge(sequence++)
            }
            assertEquals(
                inventory.files.map { file -> R2PackageMetadataPolicy.toVfsPath(file.path) } +
                    snapshot.fileInventory.map(R2ProjectFileMetadata::path),
                transportedPaths,
            )
            assertEquals(transportedPaths.indices.toList(), transportedIndexes)
            assertEquals(
                inventory.files.size + snapshot.fileInventory.size,
                framer.telemetryForTesting().openedCursorCount,
            )
            assertEquals(
                framer.telemetryForTesting().openedCursorCount,
                framer.telemetryForTesting().closedCursorCount,
            )
            assertFalse(framer.telemetryForTesting().activeCursor)
        } finally {
            snapshot.close()
            rootPackage.fill(0)
            dependencyPackage.fill(0)
        }
    }

    @Test
    fun nodeRevisionTwoMetadataAndV2TargetsUseExactFortySevenFieldIdentityAndOrder() {
        val packageBytes = "{\"type\":\"module\"}".toByteArray(StandardCharsets.UTF_8)
        val targetFiles = listOf(
            "$PACKAGE_DEPENDENCY_ROOT/a.ts",
            "$PACKAGE_DEPENDENCY_ROOT/b.mts",
            "$PACKAGE_DEPENDENCY_ROOT/c.cts",
            "$PACKAGE_DEPENDENCY_ROOT/d.d.ts",
            "$PACKAGE_DEPENDENCY_ROOT/e.d.mts",
            "$PACKAGE_DEPENDENCY_ROOT/f.d.cts",
        ).map { path -> path to "export {};\n".toByteArray(StandardCharsets.UTF_8) }
        val snapshot = nodeRevisionTwoSnapshot(
            dependencyRoots = listOf(PACKAGE_DEPENDENCY_ROOT),
            packageFiles = listOf(
                PACKAGE_JSON_ARCHIVE to packageBytes,
                DEPENDENCY_PACKAGE_JSON_ARCHIVE to packageBytes,
            ),
            nodePackageTargetV2Files = targetFiles,
        )
        val targets = requireNotNull(snapshot.nodePackageTargetV2Inventory)
        val framer = R2ProjectInputFramer("node2-v2-targets", snapshot)
        try {
            val start = parseFrame(framer.nextFrame())
            assertEquals(
                INPUT_AUTHORITY_KEY_ORDER + listOf(
                    "kind",
                    "requestId",
                    "sequence",
                    "targetProfile",
                    "profileSchemaRevision",
                    "profileRevision",
                    "configSchemaRevision",
                    "configKind",
                    "entryPath",
                    "runtimeCapabilitiesFingerprint",
                    "packageMetadataSchemaRevision",
                    "packageMetadataPolicyRevision",
                    "packageMetadataPolicyFingerprint",
                    "packageMetadataCanonical",
                    "packageMetadataInventoryFingerprint",
                    "packageMetadataFileCount",
                    "packageMetadataByteLength",
                    "nodePackageFormatSchemaRevision",
                    "nodePackageFormatCatalogRevision",
                    "nodePackageFormatCatalogFingerprint",
                    "nodeModuleOutputPolicySchemaRevision",
                    "nodeModuleOutputPolicyRevision",
                    "nodeModuleOutputPolicyFingerprint",
                    "fileCount",
                    "totalBytes",
                    "workerSpinMilliseconds",
                    "workerSpinPhase",
                    "resolverPolicyRevision",
                    "snapshotTargetInventoryFingerprint",
                    "targetUniverseFingerprint",
                    "nodePackageTargetSchemaRevision",
                    "nodePackageTargetPolicyRevision",
                    "nodePackageTargetPolicyFingerprint",
                    "nodePackageTargetCanonical",
                    "nodePackageTargetFingerprint",
                    "nodePackageTargetFileCount",
                    "nodePackageTargetByteLength",
                ),
                start.keys.toList(),
            )
            assertEquals(47, start.size)
            assertInputAuthority(start, snapshot)
            assertEquals(
                R2NodePackageTargetPolicyV2.SCHEMA_REVISION,
                start.intValue("nodePackageTargetSchemaRevision"),
            )
            assertEquals(
                R2NodePackageTargetPolicyV2.POLICY_REVISION,
                start.intValue("nodePackageTargetPolicyRevision"),
            )
            assertEquals(
                R2NodePackageTargetPolicyV2.fingerprint,
                start["nodePackageTargetPolicyFingerprint"],
            )
            assertEquals(targets.canonicalInput, start["nodePackageTargetCanonical"])
            assertEquals(targets.fingerprint, start["nodePackageTargetFingerprint"])
            assertEquals(targets.files.size, start.intValue("nodePackageTargetFileCount"))
            assertEquals(targets.totalBytes, start.longValue("nodePackageTargetByteLength"))
            assertFalse(start.containsKey("packageTargetFingerprint"))
            framer.acknowledge(0)

            var sequence = 1
            val transportedPaths = ArrayList<String>()
            while (!framer.isComplete) {
                val frame = parseFrame(framer.nextFrame())
                if (frame["kind"] == "projectInputFileStart") {
                    transportedPaths += frame["path"] as String
                }
                framer.acknowledge(sequence++)
            }
            assertEquals(
                listOf(PACKAGE_JSON_VFS, DEPENDENCY_PACKAGE_JSON_VFS) +
                    targetFiles.map { (path, _) -> "/$path" } +
                    snapshot.fileInventory.map(R2ProjectFileMetadata::path),
                transportedPaths,
            )
            val telemetry = framer.telemetryForTesting()
            assertEquals(transportedPaths.size, telemetry.openedCursorCount)
            assertEquals(telemetry.openedCursorCount, telemetry.closedCursorCount)
            assertFalse(telemetry.activeCursor)
        } finally {
            snapshot.close()
            packageBytes.fill(0)
            targetFiles.forEach { (_, bytes) -> bytes.fill(0) }
        }
    }

    @Test
    fun nodeRevisionTwoMaximumLegalMetadataUsesTheBoundedStartOnlyEnvelope() {
        val dependencyRoots =
            (0 until R2PackageMetadataPolicy.MAXIMUM_DEPENDENCY_ROOTS).map { index ->
                "packages/p${index.toString().padStart(3, '0')}${"a".repeat(977)}"
            }
        val packageFiles = buildList {
            add(
                PACKAGE_JSON_ARCHIVE to
                    "{\"type\":\"module\"}".toByteArray(StandardCharsets.UTF_8),
            )
            dependencyRoots.forEachIndexed { index, root ->
                add(
                    "$root/package.json" to
                        "{\"type\":\"module\"}".toByteArray(StandardCharsets.UTF_8),
                )
                if (index < dependencyRoots.lastIndex) {
                    add(
                        "$root/z/package.json" to
                            "{\"type\":\"module\"}".toByteArray(StandardCharsets.UTF_8),
                    )
                }
            }
        }.sortedBy { packageFile -> packageFile.first }
        assertEquals(R2PackageMetadataPolicy.MAXIMUM_FILES, packageFiles.size)
        val snapshot = nodeRevisionTwoSnapshot(
            dependencyRoots = dependencyRoots,
            packageFiles = packageFiles,
        )
        val inventory = requireNotNull(snapshot.packageMetadataInventory)
        val framer = R2ProjectInputFramer("node2-maximum-metadata", snapshot)
        try {
            val encodedStart = framer.nextFrame()
            val encodedStartBytes = encodedStart.toByteArray(StandardCharsets.UTF_8).size
            assertTrue(encodedStartBytes > 3 * R2_PROJECT_MAXIMUM_FRAME_BYTES)
            assertTrue(encodedStartBytes <= R2_PROJECT_MAXIMUM_START_FRAME_BYTES)
            assertEquals(encodedStartBytes, framer.maximumEncodedFrameBytes)
            val start = parseFrame(encodedStart)
            assertEquals(
                INPUT_AUTHORITY_KEYS + setOf(
                    "kind",
                    "requestId",
                    "sequence",
                    "targetProfile",
                    "profileSchemaRevision",
                    "profileRevision",
                    "configSchemaRevision",
                    "configKind",
                    "entryPath",
                    "runtimeCapabilitiesFingerprint",
                    "packageMetadataSchemaRevision",
                    "packageMetadataPolicyRevision",
                    "packageMetadataPolicyFingerprint",
                    "packageMetadataCanonical",
                    "packageMetadataInventoryFingerprint",
                    "packageMetadataFileCount",
                    "packageMetadataByteLength",
                    "nodePackageFormatSchemaRevision",
                    "nodePackageFormatCatalogRevision",
                    "nodePackageFormatCatalogFingerprint",
                    "nodeModuleOutputPolicySchemaRevision",
                    "nodeModuleOutputPolicyRevision",
                    "nodeModuleOutputPolicyFingerprint",
                    "fileCount",
                    "totalBytes",
                    "workerSpinMilliseconds",
                    "workerSpinPhase",
                    "resolverPolicyRevision",
                    "snapshotTargetInventoryFingerprint",
                    "targetUniverseFingerprint",
                ),
                start.keys,
            )
            assertEquals(40, start.size)
            assertInputAuthority(start, snapshot)
            assertEquals(inventory.files.size, start.intValue("packageMetadataFileCount"))
            assertEquals(
                inventory.files.size + snapshot.fileInventory.size,
                start.intValue("fileCount"),
            )
            assertFails { framer.nextFrame() }
            framer.acknowledge(0)

            var sequence = 1
            val transportedPaths = ArrayList<String>()
            while (!framer.isComplete) {
                val encoded = framer.nextFrame()
                assertTrue(
                    encoded.toByteArray(StandardCharsets.UTF_8).size <=
                        R2_PROJECT_MAXIMUM_FRAME_BYTES,
                )
                val frame = parseFrame(encoded)
                assertFalse((frame["kind"] as String).startsWith("projectInputConfig"))
                if (frame["kind"] == "projectInputFileStart") {
                    transportedPaths += frame["path"] as String
                }
                assertFails { framer.nextFrame() }
                framer.acknowledge(sequence++)
            }
            assertEquals(
                inventory.files.map { file -> R2PackageMetadataPolicy.toVfsPath(file.path) } +
                    snapshot.fileInventory.map(R2ProjectFileMetadata::path),
                transportedPaths,
            )
        } finally {
            snapshot.close()
            packageFiles.forEach { packageFile -> packageFile.second.fill(0) }
        }
    }

    @Test
    fun rawConfigFramesAreStopAndWaitRehashedAndSeparateFromFileInventory() {
        val rawConfig = "{\"compilerOptions\":{\"strict\":true}}\n"
            .toByteArray(StandardCharsets.UTF_8)
        val snapshot = rawConfigSnapshot(rawConfig)
        val descriptor = requireNotNull(snapshot.rawConfigDescriptor)
        val framer = R2ProjectInputFramer("raw-config", snapshot)

        val start = parseFrame(framer.nextFrame())
        assertEquals(
            INPUT_AUTHORITY_KEYS + setOf(
                "kind",
                "requestId",
                "sequence",
                "targetProfile",
                "profileSchemaRevision",
                "profileRevision",
                "configSchemaRevision",
                "configKind",
                "entryPath",
                "configByteLength",
                "configSha256",
                "configPolicyRevision",
                "configPolicyFingerprint",
                "fileCount",
                "totalBytes",
                "workerSpinMilliseconds",
                "workerSpinPhase",
            ),
            start.keys,
        )
        assertInputAuthority(start, snapshot)
        assertEquals(TypeScriptR2Contract.CONFIG_KIND_TSCONFIG, start["configKind"])
        assertEquals(snapshot.entryPath, start["entryPath"])
        assertEquals(
            TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
            start.intValue("configSchemaRevision"),
        )
        assertEquals(descriptor.size, start.longValue("configByteLength"))
        assertEquals(descriptor.sha256, start["configSha256"])
        assertEquals(descriptor.policyRevision, start.intValue("configPolicyRevision"))
        assertEquals(descriptor.policyFingerprint, start["configPolicyFingerprint"])
        assertEquals(
            snapshot.totalUtf8Bytes + descriptor.size,
            start.longValue("totalBytes"),
        )
        assertFails { framer.nextFrame() }
        framer.acknowledge(0)

        val configStart = parseFrame(framer.nextFrame())
        assertEquals(
            setOf("kind", "requestId", "sequence", "path", "byteLength", "sha256"),
            configStart.keys,
        )
        assertEquals("projectInputConfigStart", configStart["kind"])
        assertEquals(descriptor.path, configStart["path"])
        framer.acknowledge(1)

        val configChunk = parseFrame(framer.nextFrame())
        assertEquals(
            setOf("kind", "requestId", "sequence", "chunkIndex", "data"),
            configChunk.keys,
        )
        assertEquals("projectInputConfigChunk", configChunk["kind"])
        assertArrayEquals(rawConfig, R2FramedBase64.decode(configChunk["data"] as String))
        val chunkBuffer = framer.javaClass.getDeclaredField("chunkBuffer").let { field ->
            field.isAccessible = true
            field.get(framer) as ByteArray
        }
        assertTrue(chunkBuffer.all { byte -> byte == 0.toByte() })
        framer.acknowledge(2)

        val configEnd = parseFrame(framer.nextFrame())
        assertEquals(
            setOf("kind", "requestId", "sequence", "chunkCount"),
            configEnd.keys,
        )
        assertEquals("projectInputConfigEnd", configEnd["kind"])
        assertEquals(1, configEnd.intValue("chunkCount"))
        framer.acknowledge(3)

        var sequence = 4
        var sawConfigAsFile = false
        while (!framer.isComplete) {
            val frame = parseFrame(framer.nextFrame())
            if (frame["kind"] == "projectInputFileStart") {
                sawConfigAsFile = sawConfigAsFile || frame["path"] == "/tsconfig.json"
            }
            framer.acknowledge(sequence++)
        }
        assertFalse(sawConfigAsFile)
        val telemetry = framer.telemetryForTesting()
        assertEquals(snapshot.fileInventory.size + 1, telemetry.openedCursorCount)
        assertEquals(snapshot.fileInventory.size + 1, telemetry.closedCursorCount)
        assertEquals(snapshot.totalUtf8Bytes + descriptor.size, telemetry.totalPayloadBytes)
        assertEquals(telemetry.totalPayloadBytes, telemetry.acceptedPayloadBytes)
        assertFalse(telemetry.activeCursor)
        assertTrue(telemetry.released)
        snapshot.close()
    }

    @Test
    fun rawConfigDigestMutationClosesCursorExactlyOnceAndZeroesBuffer() {
        val acceptedBytes = "{}\n".toByteArray(StandardCharsets.UTF_8)
        val changedBytes = "[]\n".toByteArray(StandardCharsets.UTF_8)
        val accepted = rawConfigSnapshot(acceptedBytes)
        var closeCount = 0
        val changedSnapshot = object : R2ProjectInputSnapshot by accepted {
            override fun openRawConfig(): R2ProjectFileCursor = byteCursor(changedBytes) {
                closeCount++
            }
        }
        val framer = R2ProjectInputFramer("changed-config", changedSnapshot)

        framer.nextFrame()
        framer.acknowledge(0)
        framer.nextFrame()
        framer.acknowledge(1)
        framer.nextFrame()
        framer.acknowledge(2)
        assertFails { framer.nextFrame() }

        assertEquals(1, closeCount)
        framer.release()
        framer.release()
        assertEquals(1, closeCount)
        val chunkBuffer = framer.javaClass.getDeclaredField("chunkBuffer").let { field ->
            field.isAccessible = true
            field.get(framer) as ByteArray
        }
        assertTrue(chunkBuffer.all { byte -> byte == 0.toByte() })
        accepted.close()
    }

    @Test
    fun packageMetadataUsesControlledStartAndPrecedesOrdinaryFilesWithGlobalIndexes() {
        val rootPackage = "{\"name\":\"project\"}\n".toByteArray()
        val dependencyPackage = "{\"name\":\"dependency\"}\n".toByteArray()
        val accepted = packageMetadataSnapshot(
            dependencyRoots = listOf("packages/dependency"),
            packageFiles = listOf(
                PACKAGE_JSON_ARCHIVE to rootPackage,
                DEPENDENCY_PACKAGE_JSON_ARCHIVE to dependencyPackage,
            ),
        )
        var packageCursorOpenCount = 0
        val snapshot = object : R2ProjectInputSnapshot by accepted {
            override fun openPackageMetadataFile(fileIndex: Int): R2ProjectFileCursor {
                packageCursorOpenCount++
                return accepted.openPackageMetadataFile(fileIndex)
            }
        }
        val inventory = requireNotNull(snapshot.packageMetadataInventory)
        val rawConfig = requireNotNull(snapshot.rawConfigDescriptor)
        val framer = R2ProjectInputFramer("package-metadata", snapshot)

        val start = parseFrame(framer.nextFrame())
        assertEquals(
            INPUT_AUTHORITY_KEYS + setOf(
                "kind",
                "requestId",
                "sequence",
                "targetProfile",
                "profileSchemaRevision",
                "profileRevision",
                "configSchemaRevision",
                "configKind",
                "entryPath",
                "configByteLength",
                "configSha256",
                "configPolicyRevision",
                "configPolicyFingerprint",
                "packageMetadataSchemaRevision",
                "packageMetadataPolicyRevision",
                "packageMetadataPolicyFingerprint",
                "packageMetadataCanonical",
                "packageMetadataFingerprint",
                "packageMetadataFileCount",
                "packageMetadataByteLength",
                "fileCount",
                "totalBytes",
                "workerSpinMilliseconds",
                "workerSpinPhase",
            ),
            start.keys,
        )
        assertInputAuthority(start, snapshot)
        assertEquals(snapshot.entryPath, start["entryPath"])
        assertEquals(
            R2PackageMetadataPolicy.SCHEMA_REVISION,
            start.intValue("packageMetadataSchemaRevision"),
        )
        assertEquals(
            inventory.authority.policyRevision,
            start.intValue("packageMetadataPolicyRevision"),
        )
        assertEquals(
            inventory.authority.policyFingerprint,
            start["packageMetadataPolicyFingerprint"],
        )
        assertEquals(inventory.canonicalInput, start["packageMetadataCanonical"])
        assertEquals(inventory.fingerprint, start["packageMetadataFingerprint"])
        assertEquals(inventory.files.size, start.intValue("packageMetadataFileCount"))
        assertEquals(inventory.totalBytes, start.longValue("packageMetadataByteLength"))
        assertEquals(
            inventory.files.size + snapshot.fileInventory.size,
            start.intValue("fileCount"),
        )
        assertEquals(
            rawConfig.size + inventory.totalBytes + snapshot.totalUtf8Bytes,
            start.longValue("totalBytes"),
        )
        framer.acknowledge(0)

        var sequence = 1
        val transportedPaths = ArrayList<String>()
        val transportedIndexes = ArrayList<Int>()
        var sawProjectEnd = false
        while (!framer.isComplete) {
            val frame = parseFrame(framer.nextFrame())
            assertEquals(sequence, frame.intValue("sequence"))
            when (frame["kind"]) {
                "projectInputFileStart" -> {
                    transportedPaths += frame["path"] as String
                    transportedIndexes += frame.intValue("fileIndex")
                    if (transportedIndexes.size <= inventory.files.size) {
                        assertEquals(
                            transportedIndexes.size - 1,
                            packageCursorOpenCount,
                        )
                    }
                }
                "projectInputEnd" -> {
                    sawProjectEnd = true
                    assertEquals(
                        inventory.files.size + snapshot.fileInventory.size,
                        frame.intValue("fileCount"),
                    )
                    assertEquals(
                        rawConfig.size + inventory.totalBytes + snapshot.totalUtf8Bytes,
                        frame.longValue("totalBytes"),
                    )
                }
            }
            framer.acknowledge(sequence++)
        }

        assertEquals(
            listOf(PACKAGE_JSON_VFS, DEPENDENCY_PACKAGE_JSON_VFS) +
                snapshot.fileInventory.map(R2ProjectFileMetadata::path),
            transportedPaths,
        )
        assertEquals(transportedPaths.indices.toList(), transportedIndexes)
        assertEquals(inventory.files.size, packageCursorOpenCount)
        assertTrue(sawProjectEnd)
        val telemetry = framer.telemetryForTesting()
        assertEquals(
            1 + inventory.files.size + snapshot.fileInventory.size,
            telemetry.openedCursorCount,
        )
        assertEquals(telemetry.openedCursorCount, telemetry.closedCursorCount)
        assertEquals(telemetry.totalPayloadBytes, telemetry.acceptedPayloadBytes)
        assertFalse(telemetry.activeCursor)
        assertTrue(telemetry.released)
        accepted.close()
    }

    @Test
    fun packageTargetsUseRevision5StartAndFollowMetadataBeforeOrdinaryFiles() {
        val rootPackage = "{\"name\":\"project\"}\n".toByteArray()
        val dependencyPackage = "{\"name\":\"dependency\"}\n".toByteArray()
        val declarationTarget = "export declare const answer: number;\n".toByteArray()
        val sourceTarget = "export const answer = 42;\n".toByteArray()
        val accepted = packageMetadataSnapshot(
            dependencyRoots = listOf(PACKAGE_DEPENDENCY_ROOT),
            packageFiles = listOf(
                PACKAGE_JSON_ARCHIVE to rootPackage,
                DEPENDENCY_PACKAGE_JSON_ARCHIVE to dependencyPackage,
            ),
            packageTargetFiles = listOf(
                PACKAGE_DECLARATION_TARGET_ARCHIVE to declarationTarget,
                PACKAGE_SOURCE_TARGET_ARCHIVE to sourceTarget,
            ),
        )
        var metadataCursorOpenCount = 0
        var targetCursorOpenCount = 0
        val snapshot = object : R2ProjectInputSnapshot by accepted {
            override fun openPackageMetadataFile(fileIndex: Int): R2ProjectFileCursor {
                metadataCursorOpenCount++
                return accepted.openPackageMetadataFile(fileIndex)
            }

            override fun openPackageTargetFile(fileIndex: Int): R2ProjectFileCursor {
                targetCursorOpenCount++
                return accepted.openPackageTargetFile(fileIndex)
            }
        }
        val metadata = requireNotNull(snapshot.packageMetadataInventory)
        val targets = requireNotNull(snapshot.packageTargetInventory)
        val rawConfig = requireNotNull(snapshot.rawConfigDescriptor)
        val framer = R2ProjectInputFramer("package-targets", snapshot)

        val start = parseFrame(framer.nextFrame())
        assertEquals(
            INPUT_AUTHORITY_KEYS + setOf(
                "kind",
                "requestId",
                "sequence",
                "targetProfile",
                "profileSchemaRevision",
                "profileRevision",
                "configSchemaRevision",
                "configKind",
                "entryPath",
                "configByteLength",
                "configSha256",
                "configPolicyRevision",
                "configPolicyFingerprint",
                "packageMetadataSchemaRevision",
                "packageMetadataPolicyRevision",
                "packageMetadataPolicyFingerprint",
                "packageMetadataCanonical",
                "packageMetadataFingerprint",
                "packageMetadataFileCount",
                "packageMetadataByteLength",
                "packageTargetSchemaRevision",
                "packageTargetPolicyRevision",
                "packageTargetPolicyFingerprint",
                "packageTargetCanonical",
                "packageTargetFingerprint",
                "packageTargetFileCount",
                "packageTargetByteLength",
                "fileCount",
                "totalBytes",
                "workerSpinMilliseconds",
                "workerSpinPhase",
            ),
            start.keys,
        )
        assertInputAuthority(start, snapshot)
        assertEquals(
            R2PackageTargetPolicy.SCHEMA_REVISION,
            start.intValue("packageTargetSchemaRevision"),
        )
        assertEquals(
            targets.authority.policyRevision,
            start.intValue("packageTargetPolicyRevision"),
        )
        assertEquals(targets.authority.policyFingerprint, start["packageTargetPolicyFingerprint"])
        assertEquals(targets.canonicalInput, start["packageTargetCanonical"])
        assertEquals(targets.fingerprint, start["packageTargetFingerprint"])
        assertEquals(targets.files.size, start.intValue("packageTargetFileCount"))
        assertEquals(targets.totalBytes, start.longValue("packageTargetByteLength"))
        assertEquals(
            metadata.files.size + targets.files.size + snapshot.fileInventory.size,
            start.intValue("fileCount"),
        )
        assertEquals(
            rawConfig.size + metadata.totalBytes + targets.totalBytes + snapshot.totalUtf8Bytes,
            start.longValue("totalBytes"),
        )
        framer.acknowledge(0)

        var sequence = 1
        val transportedPaths = ArrayList<String>()
        val transportedIndexes = ArrayList<Int>()
        while (!framer.isComplete) {
            val frame = parseFrame(framer.nextFrame())
            assertEquals(sequence, frame.intValue("sequence"))
            if (frame["kind"] == "projectInputFileStart") {
                transportedPaths += frame["path"] as String
                transportedIndexes += frame.intValue("fileIndex")
            }
            framer.acknowledge(sequence++)
        }

        assertEquals(
            metadata.files.map { file -> R2PackageMetadataPolicy.toVfsPath(file.path) } +
                targets.files.map { file -> R2PackageTargetPolicy.toVfsPath(file.path) } +
                snapshot.fileInventory.map(R2ProjectFileMetadata::path),
            transportedPaths,
        )
        assertEquals(transportedPaths.indices.toList(), transportedIndexes)
        assertEquals(metadata.files.size, metadataCursorOpenCount)
        assertEquals(targets.files.size, targetCursorOpenCount)
        val telemetry = framer.telemetryForTesting()
        assertEquals(
            1 + metadata.files.size + targets.files.size + snapshot.fileInventory.size,
            telemetry.openedCursorCount,
        )
        assertEquals(telemetry.openedCursorCount, telemetry.closedCursorCount)
        assertEquals(telemetry.totalPayloadBytes, telemetry.acceptedPayloadBytes)
        assertFalse(telemetry.activeCursor)
        assertTrue(telemetry.released)
        accepted.close()
    }

    @Test
    fun releaseClosesAnActivePackageTargetCursorExactlyOnceAndZeroesTheBuffer() {
        val targetBytes = "export declare const value: string;\n".toByteArray()
        val accepted = packageMetadataSnapshot(
            dependencyRoots = listOf(PACKAGE_DEPENDENCY_ROOT),
            packageFiles = listOf(
                PACKAGE_JSON_ARCHIVE to "{}".toByteArray(),
                DEPENDENCY_PACKAGE_JSON_ARCHIVE to "{}".toByteArray(),
            ),
            packageTargetFiles = listOf(PACKAGE_DECLARATION_TARGET_ARCHIVE to targetBytes),
        )
        var closeCount = 0
        val snapshot = object : R2ProjectInputSnapshot by accepted {
            override fun openPackageTargetFile(fileIndex: Int): R2ProjectFileCursor =
                byteCursor(targetBytes) { closeCount++ }
        }
        val targetPath = R2PackageTargetPolicy.toVfsPath(PACKAGE_DECLARATION_TARGET_ARCHIVE)
        val framer = R2ProjectInputFramer("release-target", snapshot)
        var sequence = 0
        while (true) {
            val frame = parseFrame(framer.nextFrame())
            framer.acknowledge(sequence++)
            if (frame["kind"] == "projectInputFileStart" && frame["path"] == targetPath) break
        }

        assertTrue(framer.telemetryForTesting().activeCursor)
        framer.release()
        framer.release()

        assertEquals(1, closeCount)
        assertFalse(framer.telemetryForTesting().activeCursor)
        assertTrue(framer.telemetryForTesting().released)
        val chunkBuffer = framer.javaClass.getDeclaredField("chunkBuffer").let { field ->
            field.isAccessible = true
            field.get(framer) as ByteArray
        }
        assertTrue(chunkBuffer.all { byte -> byte == 0.toByte() })
        assertFails { framer.nextFrame() }
        accepted.close()
    }

    @Test
    fun packageMetadataDigestMutationClosesCursorExactlyOnceAndZeroesBuffer() {
        val acceptedBytes = "{}".toByteArray()
        val changedBytes = "[]".toByteArray()
        val accepted = packageMetadataSnapshot(
            dependencyRoots = emptyList(),
            packageFiles = listOf(PACKAGE_JSON_ARCHIVE to acceptedBytes),
        )
        var closeCount = 0
        val changedSnapshot = object : R2ProjectInputSnapshot by accepted {
            override fun openPackageMetadataFile(fileIndex: Int): R2ProjectFileCursor =
                byteCursor(changedBytes) { closeCount++ }
        }
        val framer = R2ProjectInputFramer("changed-package", changedSnapshot)

        var expectedSequence = 0
        while (true) {
            val frame = parseFrame(framer.nextFrame())
            framer.acknowledge(expectedSequence++)
            if (frame["kind"] == "projectInputFileChunk") break
        }
        assertFails { framer.nextFrame() }
        assertEquals(1, closeCount)
        framer.release()
        framer.release()
        assertEquals(1, closeCount)
        val chunkBuffer = framer.javaClass.getDeclaredField("chunkBuffer").let { field ->
            field.isAccessible = true
            field.get(framer) as ByteArray
        }
        assertTrue(chunkBuffer.all { byte -> byte == 0.toByte() })
        assertFalse(framer.telemetryForTesting().activeCursor)
        accepted.close()
    }

    @Test
    fun packageMetadataCannotUseTheLegacyConfigNoneStartShape() {
        val packageBytes = "{}".toByteArray()
        val controlled = packageMetadataSnapshot(
            dependencyRoots = emptyList(),
            packageFiles = listOf(PACKAGE_JSON_ARCHIVE to packageBytes),
        )
        val configNone = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile("/declarations/lib.d.ts", ""),
                R2ProjectTextFile("/sources/main.ts", "export {};\n"),
            ),
            rootNames = listOf("/sources/main.ts"),
        )
        val invalid = object : R2ProjectInputSnapshot by configNone {
            override val packageMetadataInventory = controlled.packageMetadataInventory
            override fun openPackageMetadataFile(fileIndex: Int) =
                controlled.openPackageMetadataFile(fileIndex)
        }

        assertFails { R2ProjectInputFramer("legacy-package", invalid) }
        controlled.close()
        configNone.close()
    }

    @Test
    fun framerRevalidatesExactNodePackageCombinationsFromDelegatedSnapshots() {
        val moduleBytes = "{\"type\":\"module\"}".toByteArray(StandardCharsets.UTF_8)
        val commonJsBytes = "{\"type\":\"commonjs\"}".toByteArray(StandardCharsets.UTF_8)
        val nodeBase = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile("/declarations/lib.d.ts", ""),
                R2ProjectTextFile("/declarations/node/index.d.ts", ""),
                R2ProjectTextFile("/sources/main.ts", "export {};\n"),
            ),
            rootNames = listOf("/sources/main.ts"),
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
        )
        val controlled = packageMetadataSnapshot(
            dependencyRoots = emptyList(),
            packageFiles = listOf(
                PACKAGE_JSON_ARCHIVE to "{}".toByteArray(StandardCharsets.UTF_8),
            ),
        )
        val nodeTwoWithMetadata = nodeRevisionTwoSnapshot(
            packageFiles = listOf(PACKAGE_JSON_ARCHIVE to moduleBytes),
        )
        val nodeTwoWithDifferentMetadata = nodeRevisionTwoSnapshot(
            packageFiles = listOf(PACKAGE_JSON_ARCHIVE to commonJsBytes),
        )
        val nodeTwoWithoutMetadata = nodeRevisionTwoSnapshot()
        val nodeOneWithRawConfig = object : R2ProjectInputSnapshot by nodeBase {
            override val rawConfigDescriptor = controlled.rawConfigDescriptor
            override fun openRawConfig(): R2ProjectFileCursor? = controlled.openRawConfig()
        }
        val nodeTwoWithRawMetadata = object : R2ProjectInputSnapshot by nodeBase {
            override val profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
            override val rawConfigDescriptor = controlled.rawConfigDescriptor
            override val packageMetadataInventory = controlled.packageMetadataInventory
            override fun openRawConfig(): R2ProjectFileCursor? = controlled.openRawConfig()
            override fun openPackageMetadataFile(fileIndex: Int): R2ProjectFileCursor =
                controlled.openPackageMetadataFile(fileIndex)
        }
        val nodeTwoMissingCatalog = object : R2ProjectInputSnapshot by nodeTwoWithMetadata {
            override val nodePackageFormatCatalog: R2NodePackageFormatCatalog? = null
        }
        val nodeTwoCatalogWithoutMetadata =
            object : R2ProjectInputSnapshot by nodeTwoWithoutMetadata {
                override val nodePackageFormatCatalog =
                    nodeTwoWithMetadata.nodePackageFormatCatalog
            }
        val nodeTwoDriftedCatalog = object : R2ProjectInputSnapshot by nodeTwoWithMetadata {
            override val nodePackageFormatCatalog =
                nodeTwoWithDifferentMetadata.nodePackageFormatCatalog
        }
        try {
            listOf(
                "node1-raw-config" to nodeOneWithRawConfig,
                "node2-raw-metadata" to nodeTwoWithRawMetadata,
                "node2-missing-catalog" to nodeTwoMissingCatalog,
                "node2-catalog-without-metadata" to nodeTwoCatalogWithoutMetadata,
                "node2-drifted-catalog" to nodeTwoDriftedCatalog,
            ).forEach { (requestId, snapshot) ->
                val failure = runCatching {
                    R2ProjectInputFramer(requestId, snapshot)
                }.exceptionOrNull()
                assertTrue(failure is IllegalArgumentException)
                assertTrue(failure?.message.orEmpty().contains("Node"))
            }
        } finally {
            controlled.close()
            nodeBase.close()
            nodeTwoWithMetadata.close()
            nodeTwoWithDifferentMetadata.close()
            nodeTwoWithoutMetadata.close()
            moduleBytes.fill(0)
            commonJsBytes.fill(0)
        }
    }

    @Test
    fun nodeRevisionTwoNativeTransportAdmitsExactMtsAndCtsSourceKinds() {
        listOf("mts", "cts").forEach { extension ->
            val sourcePath = "/sources/main.$extension"
            val accepted = nodeRevisionTwoSnapshot(sourcePath = sourcePath)
            try {
                val framer = R2ProjectInputFramer("node2-$extension", accepted)
                val start = parseFrame(framer.nextFrame())
                assertEquals(30, start.size)
                assertInputAuthority(start, accepted)
                assertEquals(sourcePath, start["entryPath"])
                assertFalse(start.containsKey("nodePackageTargetFingerprint"))
                framer.release()
            } finally {
                accepted.close()
            }
        }
    }

    @Test
    fun framerRejectsTamperedMissingNonRootAndWrongKindEntryIdentities() {
        val accepted = rawConfigSnapshot("{}\n".toByteArray(StandardCharsets.UTF_8))
        listOf(
            "/sources/missing.ts",
            "/declarations/lib.d.ts",
        ).forEachIndexed { index, tamperedEntryPath ->
            val tampered = object : R2ProjectInputSnapshot by accepted {
                override val entryPath = tamperedEntryPath
            }
            assertFails {
                R2ProjectInputFramer("tampered-entry-$index", tampered)
            }
        }
        val detachedFromRoots = object : R2ProjectInputSnapshot by accepted {
            override val rootNames = emptyList<String>()
        }
        assertFails {
            R2ProjectInputFramer("tampered-entry-roots", detachedFromRoots)
        }
        accepted.close()
    }

    @Test
    fun workerSpinBindsAnExactExecutionPhase() {
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(
                    path = "/declarations/lib.d.ts",
                    text = "declare interface Array<T> { readonly length: number }\n",
                ),
                R2ProjectTextFile(
                    path = "/sources/main.ts",
                    text = "export const value = 1;\n",
                ),
            ),
            rootNames = listOf("/sources/main.ts"),
        )
        val framer = R2ProjectInputFramer(
            requestId = "phase-spin",
            snapshot = snapshot,
            workerSpinMilliseconds = 1,
            workerSpinPhase = R2ProjectExecutionPhase.SEMANTIC,
        )
        val start = R2StrictJson.parseObject(
            framer.nextFrame().toByteArray(StandardCharsets.UTF_8),
        )

        assertEquals(1, start.intValue("workerSpinMilliseconds"))
        assertEquals("semantic", start["workerSpinPhase"])
        framer.release()

        assertFails {
            R2ProjectInputFramer(
                requestId = "phase-missing",
                snapshot = snapshot,
                workerSpinMilliseconds = 1,
            )
        }
        assertFails {
            R2ProjectInputFramer(
                requestId = "phase-without-spin",
                snapshot = snapshot,
                workerSpinPhase = R2ProjectExecutionPhase.SEMANTIC,
            )
        }
    }

    @Test
    fun canonicalBase64RoundTripsBinaryAndRejectsAmbiguousForms() {
        val values = listOf(
            ByteArray(0),
            byteArrayOf(0),
            byteArrayOf(0, 1),
            byteArrayOf(0, 1, 2),
            ByteArray(R2_PROJECT_FRAME_RAW_CHUNK_BYTES) { index ->
                (index * 31).toByte()
            },
        )
        values.forEach { value ->
            val encoded = R2FramedBase64.encode(value)
            assertArrayEquals(value, R2FramedBase64.decode(encoded))
        }

        assertFails { R2FramedBase64.decode("A===") }
        assertFails { R2FramedBase64.decode("AB==") }
        assertFails { R2FramedBase64.decode("AA") }
        assertFails { R2FramedBase64.decode("AA-_") }
        assertFails { R2FramedBase64.decode("é===") }
    }

    @Test
    fun inputFramesCrossTheOldSingleMessageBudgetWithOneOutstandingFrame() {
        val declarationText = buildString(700 * 1024) {
            append("/// <reference no-default-lib=\"true\"/>\n")
            while (length < 700 * 1024) {
                append("interface FramedDeclaration { value: string; }\n")
            }
        }
        val sourceText = buildString {
            repeat(12_000) { index ->
                append("export const value")
                append(index)
                append(" = \"编译-")
                append(index)
                append("\";\n")
            }
        }
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile("/declarations/lib.d.ts", declarationText),
                R2ProjectTextFile("/sources/main.ts", sourceText),
            ),
            rootNames = listOf("/sources/main.ts"),
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
        )
        assertTrue(snapshot.totalUtf8Bytes > 512L * 1024L)

        val framer = R2ProjectInputFramer(
            requestId = "framed-input",
            snapshot = snapshot,
        )
        var expectedSequence = 0
        var frames = 0
        var decodedChunkBytes = 0L
        var sawStart = false
        var sawEnd = false
        while (!framer.isComplete) {
            val frame = framer.nextFrame()
            assertTrue(
                frame.toByteArray(StandardCharsets.UTF_8).size <=
                    R2_PROJECT_MAXIMUM_FRAME_BYTES,
            )
            assertFails { framer.nextFrame() }
            val parsed = R2StrictJson.parseObject(
                frame.toByteArray(StandardCharsets.UTF_8),
            )
            assertEquals(expectedSequence, parsed.intValue("sequence"))
            assertEquals("framed-input", parsed["requestId"])
            when (parsed["kind"]) {
                "projectInputStart" -> {
                    sawStart = true
                    val authority = snapshot.profileAuthority
                    assertEquals(snapshot.files.size, parsed.intValue("fileCount"))
                    assertEquals(snapshot.totalUtf8Bytes, parsed.longValue("totalBytes"))
                    assertEquals(0, parsed.intValue("workerSpinMilliseconds"))
                    assertEquals("none", parsed["workerSpinPhase"])
                    assertEquals(
                        authority.profileSchemaRevision,
                        parsed.intValue("profileSchemaRevision"),
                    )
                    assertEquals(authority.profileRevision, parsed.intValue("profileRevision"))
                    assertEquals(
                        authority.configSchemaRevision,
                        parsed.intValue("configSchemaRevision"),
                    )
                    assertEquals(authority.configKind, parsed["configKind"])
                }
                "projectInputFileChunk" -> {
                    decodedChunkBytes += R2FramedBase64.decode(
                        parsed["data"] as String,
                    ).size
                }
                "projectInputEnd" -> sawEnd = true
            }
            framer.acknowledge(expectedSequence)
            expectedSequence++
            frames++
        }

        assertTrue(sawStart)
        assertTrue(sawEnd)
        assertEquals(snapshot.totalUtf8Bytes, decodedChunkBytes)
        assertEquals(frames, framer.sentFrameCount)
        assertTrue(framer.maximumEncodedFrameBytes <= R2_PROJECT_MAXIMUM_FRAME_BYTES)
        assertFails { framer.nextFrame() }
        assertFails { framer.acknowledge(expectedSequence) }
    }

    @Test
    fun batchedInputKeepsExactInnerSequenceAndCommitsOnlyAfterEnvelopeAck() {
        val declarationText = buildString(180 * 1024) {
            append("/// <reference no-default-lib=\"true\"/>\n")
            while (length < 180 * 1024) {
                append("interface BatchedDeclaration { value: string; }\n")
            }
        }
        val sourceText = buildString(90 * 1024) {
            while (length < 90 * 1024) {
                append("export const value = \"批量传输\";\n")
            }
        }
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile("/declarations/lib.d.ts", declarationText),
                R2ProjectTextFile("/sources/main.ts", sourceText),
            ),
            rootNames = listOf("/sources/main.ts"),
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
        )
        val framer = R2BatchedProjectInputFramer(
            R2ProjectInputFramer("batched-input", snapshot),
        )
        var expectedSequence = 0
        var innerFrameCount = 0
        var envelopeCount = 0
        var decodedChunkBytes = 0L
        var sawStart = false
        var sawEnd = false
        var checkedUncommittedBatch = false
        var maximumPayloadChunkFrames = 0

        while (!framer.isComplete) {
            val before = framer.telemetryForTesting()
            val encoded = framer.nextFrame()
            envelopeCount++
            assertTrue(
                encoded.toByteArray(StandardCharsets.UTF_8).size <=
                    R2_PROJECT_MAXIMUM_BATCH_ENVELOPE_BYTES,
            )
            assertFails { framer.nextFrame() }
            val envelope = parseFrame(encoded)
            val acknowledgedSequence = envelope.intValue("sequence")
            assertEquals("batched-input", envelope["requestId"])
            when (envelope["kind"]) {
                "projectInputStart" -> {
                    assertEquals(0, envelope.intValue("sequence"))
                    assertFalse(sawStart)
                    sawStart = true
                    expectedSequence++
                    innerFrameCount++
                }
                "projectInputBatch" -> {
                    assertTrue(sawStart)
                    assertFalse(sawEnd)
                    @Suppress("UNCHECKED_CAST")
                    val frames = envelope["frames"] as List<Map<String, Any?>>
                    assertTrue(frames.isNotEmpty())
                    assertTrue(frames.size <= R2_PROJECT_INPUT_BATCH_MAXIMUM_FRAMES)
                    val payloadChunkFrames = frames.count { frame ->
                        frame["kind"] == "projectInputConfigChunk" ||
                            frame["kind"] == "projectInputFileChunk"
                    }
                    assertTrue(
                        payloadChunkFrames <=
                            R2_PROJECT_INPUT_BATCH_MAXIMUM_PAYLOAD_CHUNK_FRAMES,
                    )
                    maximumPayloadChunkFrames = maxOf(
                        maximumPayloadChunkFrames,
                        payloadChunkFrames,
                    )
                    frames.forEach { frame ->
                        assertEquals("batched-input", frame["requestId"])
                        assertEquals(expectedSequence, frame.intValue("sequence"))
                        assertTrue(
                            frame["kind"] in setOf(
                                "projectInputConfigStart",
                                "projectInputConfigChunk",
                                "projectInputConfigEnd",
                                "projectInputFileStart",
                                "projectInputFileChunk",
                                "projectInputFileEnd",
                            ),
                        )
                        if (frame["kind"] == "projectInputFileChunk") {
                            decodedChunkBytes += R2FramedBase64.decode(
                                frame["data"] as String,
                            ).size
                        }
                        expectedSequence++
                        innerFrameCount++
                    }
                    assertEquals(expectedSequence - 1, acknowledgedSequence)
                    if (!checkedUncommittedBatch) {
                        val whileOutstanding = framer.telemetryForTesting()
                        assertEquals(
                            before.acceptedPayloadBytes,
                            whileOutstanding.acceptedPayloadBytes,
                        )
                        assertEquals(before.fileIndex, whileOutstanding.fileIndex)
                        assertTrue(whileOutstanding.sentFrameCount > before.sentFrameCount)
                        assertFails { framer.acknowledge(acknowledgedSequence + 1) }
                        assertEquals(
                            acknowledgedSequence,
                            framer.outstandingSequence,
                        )
                        framer.acknowledge(acknowledgedSequence)
                        assertTrue(
                            framer.telemetryForTesting().acceptedPayloadBytes >
                                before.acceptedPayloadBytes,
                        )
                        checkedUncommittedBatch = true
                        continue
                    }
                }
                "projectInputEnd" -> {
                    assertTrue(sawStart)
                    assertFalse(sawEnd)
                    assertTrue(framer.outstandingCompletesTransport)
                    sawEnd = true
                    assertEquals(expectedSequence, acknowledgedSequence)
                    expectedSequence++
                    innerFrameCount++
                }
                else -> fail("Unexpected project input envelope kind: ${envelope["kind"]}")
            }
            framer.acknowledge(acknowledgedSequence)
        }

        val telemetry = framer.telemetryForTesting()
        assertTrue(sawStart)
        assertTrue(sawEnd)
        assertTrue(checkedUncommittedBatch)
        assertEquals(snapshot.totalUtf8Bytes, decodedChunkBytes)
        assertEquals(innerFrameCount, telemetry.sentFrameCount)
        assertEquals(envelopeCount, telemetry.sentEnvelopeCount)
        assertTrue(
            envelopeCount >=
            2 + (innerFrameCount - 2 + R2_PROJECT_INPUT_BATCH_MAXIMUM_FRAMES - 1) /
                R2_PROJECT_INPUT_BATCH_MAXIMUM_FRAMES,
        )
        assertTrue(envelopeCount < innerFrameCount)
        assertTrue(
            maximumPayloadChunkFrames <=
                R2_PROJECT_INPUT_BATCH_MAXIMUM_PAYLOAD_CHUNK_FRAMES,
        )
        assertTrue(telemetry.maximumEncodedFrameBytes <= R2_PROJECT_MAXIMUM_FRAME_BYTES)
        assertTrue(
            telemetry.maximumEncodedFrameBytes <=
                R2_PROJECT_MAXIMUM_BATCHABLE_FRAME_BYTES,
        )
        assertTrue(
            telemetry.maximumEncodedEnvelopeBytes <=
                R2_PROJECT_MAXIMUM_BATCH_ENVELOPE_BYTES,
        )
        assertEquals(telemetry.totalPayloadBytes, telemetry.acceptedPayloadBytes)
    }

    @Test
    fun releasingOutstandingBatchClosesItsActiveCursorExactlyOnce() {
        val snapshot = GeneratedProjectInputSnapshot(
            generatedFiles = listOf(
                GeneratedFile(
                    path = "/declarations/lib.d.ts",
                    byteLength =
                        R2_PROJECT_INPUT_BATCH_MAXIMUM_FRAMES *
                            R2_PROJECT_FRAME_RAW_CHUNK_BYTES,
                    value = 'd'.code.toByte(),
                ),
                GeneratedFile("/sources/main.ts", 1, '\n'.code.toByte()),
            ),
        )
        val framer = R2BatchedProjectInputFramer(
            R2ProjectInputFramer(
                requestId = "batched-release",
                snapshot = snapshot,
            ),
        )

        assertEquals("projectInputStart", parseFrame(framer.nextFrame())["kind"])
        framer.acknowledge(0)
        val outstandingBatch = parseFrame(framer.nextFrame())
        assertEquals("projectInputBatch", outstandingBatch["kind"])
        @Suppress("UNCHECKED_CAST")
        val outstandingFrames = outstandingBatch["frames"] as List<Map<String, Any?>>
        assertEquals(
            R2_PROJECT_INPUT_BATCH_MAXIMUM_PAYLOAD_CHUNK_FRAMES + 1,
            outstandingFrames.size,
        )
        assertEquals(
            R2_PROJECT_INPUT_BATCH_MAXIMUM_PAYLOAD_CHUNK_FRAMES,
            outstandingFrames.count { frame ->
                frame["kind"] == "projectInputFileChunk"
            },
        )
        assertTrue(framer.telemetryForTesting().activeCursor)

        framer.release()
        framer.release()

        val telemetry = framer.telemetryForTesting()
        assertTrue(telemetry.released)
        assertFalse(telemetry.activeCursor)
        assertEquals(1, telemetry.openedCursorCount)
        assertEquals(1, telemetry.closedCursorCount)
        assertEquals(1, snapshot.cursorCloseAttemptCount)
        assertEquals(1, snapshot.closedCursorCount)
        assertFails { framer.acknowledge(requireNotNull(framer.outstandingSequence)) }
    }

    @Test
    fun fileBackedFramerReadsAtMostOneChunkAndStreamsBeyondRevisionTwoBudget() {
        val snapshot = GeneratedProjectInputSnapshot(
            generatedFiles = listOf(
                GeneratedFile("/declarations/lib.d.ts", 1, '/'.code.toByte()),
                GeneratedFile(
                    "/sources/main.ts",
                    8 * 1024 * 1024,
                    'a'.code.toByte(),
                ),
                GeneratedFile("/sources/extra.ts", 1, '\n'.code.toByte()),
            ),
            maximumCursorReturnBytes = 997,
        )
        assertTrue(snapshot.totalUtf8Bytes > 8L * 1024L * 1024L)
        assertFalse(
            R2ProjectSnapshot.ordinaryCompilationAdmission(
                snapshot = snapshot,
                limits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION_REVISION_2,
            ).isEligible,
        )

        val framer = R2ProjectInputFramer(
            requestId = "file-backed-input",
            snapshot = snapshot,
            workerInputQuotaAuthority = R2WorkerInputQuotaAuthority.TEST_ONLY_EXPANDED,
        )
        var decodedBytes = 0L
        var expectedSequence = 0
        while (!framer.isComplete) {
            val frame = framer.nextFrame()
            val parsed = R2StrictJson.parseObject(
                frame.toByteArray(StandardCharsets.UTF_8),
            )
            assertEquals(expectedSequence, parsed.intValue("sequence"))
            if (parsed["kind"] == "projectInputFileChunk") {
                decodedBytes += R2FramedBase64.decode(parsed["data"] as String).size
            }
            framer.acknowledge(expectedSequence)
            expectedSequence++
        }

        assertEquals(snapshot.totalUtf8Bytes, decodedBytes)
        assertTrue(snapshot.maximumRequestedRead <= R2_PROJECT_FRAME_RAW_CHUNK_BYTES)
        assertEquals(1, snapshot.maximumConcurrentCursors)
        assertEquals(snapshot.fileInventory.size, snapshot.closedCursorCount)
        val telemetry = framer.telemetryForTesting()
        assertEquals("file-backed-input", telemetry.requestId)
        assertEquals(snapshot.fileInventory.size, telemetry.openedCursorCount)
        assertEquals(snapshot.fileInventory.size, telemetry.closedCursorCount)
        assertFalse(telemetry.activeCursor)
        assertTrue(telemetry.released)
        assertEquals(0, snapshot.snapshotCloseCount)
    }

    @Test
    fun fileBackedFramerRejectsChangedDigestAndReleasesCursorExactlyOnce() {
        val snapshot = GeneratedProjectInputSnapshot(
            generatedFiles = listOf(
                GeneratedFile(
                    path = "/sources/main.ts",
                    byteLength = 32,
                    value = 'x'.code.toByte(),
                    declaredSha256 = generatedSha256(32, 'y'.code.toByte()),
                ),
                GeneratedFile("/declarations/lib.d.ts", 0, 0),
            ),
        )
        val framer = R2ProjectInputFramer(
            requestId = "changed-file",
            snapshot = snapshot,
        )

        framer.nextFrame()
        framer.acknowledge(0)
        framer.nextFrame()
        framer.acknowledge(1)
        framer.nextFrame()
        framer.acknowledge(2)
        assertFails { framer.nextFrame() }

        framer.release()
        framer.release()
        assertEquals(1, snapshot.closedCursorCount)
        val telemetry = framer.telemetryForTesting()
        assertEquals(1, telemetry.openedCursorCount)
        assertEquals(1, telemetry.closedCursorCount)
        assertFalse(telemetry.activeCursor)
        assertTrue(telemetry.released)
        assertFails { framer.nextFrame() }
    }

    @Test
    fun fileBackedFramerRejectsCursorThatStopsBeforeAcceptedInventory() {
        val snapshot = GeneratedProjectInputSnapshot(
            generatedFiles = listOf(
                GeneratedFile("/sources/main.ts", 64, 'z'.code.toByte()),
                GeneratedFile("/declarations/lib.d.ts", 0, 0),
            ),
            stopAfterBytes = 17,
        )
        val framer = R2ProjectInputFramer(
            requestId = "short-cursor",
            snapshot = snapshot,
        )

        framer.nextFrame()
        framer.acknowledge(0)
        framer.nextFrame()
        framer.acknowledge(1)
        assertFails { framer.nextFrame() }
        framer.release()
        assertEquals(1, snapshot.closedCursorCount)
        assertEquals(1, framer.telemetryForTesting().openedCursorCount)
        assertEquals(1, framer.telemetryForTesting().closedCursorCount)
        assertFalse(framer.telemetryForTesting().activeCursor)
    }

    @Test
    fun fileBackedFramerVerifiesAndClosesZeroLengthFileWithoutAChunk() {
        val snapshot = GeneratedProjectInputSnapshot(
            generatedFiles = listOf(
                GeneratedFile("/sources/empty.ts", 0, 0),
                GeneratedFile("/declarations/lib.d.ts", 0, 0),
            ),
        )
        val framer = R2ProjectInputFramer(
            requestId = "empty-file",
            snapshot = snapshot,
        )
        var expectedSequence = 0
        var chunkCount = 0

        while (!framer.isComplete) {
            val frame = R2StrictJson.parseObject(
                framer.nextFrame().toByteArray(StandardCharsets.UTF_8),
            )
            if (frame["kind"] == "projectInputFileChunk") {
                chunkCount++
            }
            framer.acknowledge(expectedSequence++)
        }

        assertEquals(0, chunkCount)
        assertEquals(2, snapshot.closedCursorCount)
        assertEquals(2, snapshot.cursorCloseAttemptCount)
    }

    @Test
    fun fileBackedFramerRetainsCursorUntilCloseCanBeProven() {
        val snapshot = GeneratedProjectInputSnapshot(
            generatedFiles = listOf(
                GeneratedFile("/sources/main.ts", 1, 'x'.code.toByte()),
                GeneratedFile("/declarations/lib.d.ts", 0, 0),
            ),
            closeFailuresBeforeSuccess = 1,
        )
        val framer = R2ProjectInputFramer(
            requestId = "retry-close",
            snapshot = snapshot,
        )

        framer.nextFrame()
        framer.acknowledge(0)
        framer.nextFrame()
        framer.acknowledge(1)
        framer.nextFrame()
        framer.acknowledge(2)
        assertFails { framer.nextFrame() }
        assertEquals(1, snapshot.cursorCloseAttemptCount)
        assertEquals(0, snapshot.closedCursorCount)

        framer.release()
        assertEquals(2, snapshot.cursorCloseAttemptCount)
        assertEquals(1, snapshot.closedCursorCount)
        val telemetry = framer.telemetryForTesting()
        assertEquals(1, telemetry.openedCursorCount)
        assertEquals(1, telemetry.closedCursorCount)
        assertFalse(telemetry.activeCursor)
        assertTrue(telemetry.released)
        assertFails { framer.nextFrame() }
    }

    @Test
    fun resultAccumulatorRequiresExactChunksInventoryAndDigest() {
        val payload = buildString {
            append("{\"diagnostics\":[],\"text\":\"")
            repeat(40_000) { append("λ") }
            append("\"}")
        }.toByteArray(StandardCharsets.UTF_8)
        val digest = R2Digests.sha256(payload)
        val chunkCount =
            (payload.size + R2_PROJECT_FRAME_RAW_CHUNK_BYTES - 1) /
                R2_PROJECT_FRAME_RAW_CHUNK_BYTES
        val accumulator = R2ProjectResultAccumulator(
            requestId = "framed-result",
            generation = 7,
            byteLength = payload.size,
            sha256 = digest,
            chunkCount = chunkCount,
        )
        assertFalse(accumulator.hasAcceptedCompleteInventory())
        assertFails {
            accumulator.append(
                index = 1,
                chunk = payload.copyOfRange(
                    0,
                    minOf(payload.size, R2_PROJECT_FRAME_RAW_CHUNK_BYTES),
                ),
            )
        }
        repeat(chunkCount) { index ->
            val offset = index * R2_PROJECT_FRAME_RAW_CHUNK_BYTES
            accumulator.append(
                index = index,
                chunk = payload.copyOfRange(
                    offset,
                    minOf(payload.size, offset + R2_PROJECT_FRAME_RAW_CHUNK_BYTES),
                ),
            )
            assertEquals(
                index == chunkCount - 1,
                accumulator.hasAcceptedCompleteInventory(),
            )
        }
        assertArrayEquals(
            payload,
            accumulator.finish(
                endByteLength = payload.size,
                endSha256 = digest,
                endChunkCount = chunkCount,
            ),
        )
        assertFails {
            accumulator.finish(payload.size, digest, chunkCount)
        }
        assertFalse(accumulator.hasAcceptedCompleteInventory())
    }

    @Test
    fun opaqueOperationHandleCanOnlyBeClaimedOnce() {
        val handle = R2HostProjectOperationHandle.create()
        assertTrue(handle.claim())
        assertFalse(handle.claim())
        assertTrue(handle.requestId.isNotEmpty())

        val stoppedBeforeClaim = R2HostProjectOperationHandle.create()
        assertTrue(stoppedBeforeClaim.requestStop(R2ProjectStopCause.CALLBACK_DIED))
        assertTrue(stoppedBeforeClaim.requestStop(R2ProjectStopCause.TIMED_OUT))
        assertTrue(stoppedBeforeClaim.claim())
        assertEquals(
            R2ProjectStopCause.CALLBACK_DIED,
            stoppedBeforeClaim.acceptedStopCause(),
        )
        assertEquals(
            R2ProjectStopCause.CALLBACK_DIED,
            stoppedBeforeClaim.completeStopped(),
        )
        assertFalse(stoppedBeforeClaim.requestStop(R2ProjectStopCause.CANCELLED))
    }

    @Test
    fun completionDeadlineDecisionSharesTheOperationHandleCriticalSection() {
        val beforeDeadline = R2HostProjectOperationHandle.create()
        assertTrue(beforeDeadline.claim())
        assertEquals(
            R2HostProjectTerminalDecision.COMPLETED,
            beforeDeadline.tryCompleteBeforeElapsedRealtimeDeadline(
                deadlineAtElapsedRealtimeNanos = 10_000L,
                elapsedRealtimeNanos = { 9_999L },
            ),
        )
        assertFalse(beforeDeadline.requestStop(R2ProjectStopCause.TIMED_OUT))

        val atDeadline = R2HostProjectOperationHandle.create()
        assertTrue(atDeadline.claim())
        val clockEntered = CountDownLatch(1)
        val releaseClock = CountDownLatch(1)
        val completionReturned = CountDownLatch(1)
        val stopAttempted = CountDownLatch(1)
        val stopReturned = CountDownLatch(1)
        val completionDecision =
            AtomicReference<R2HostProjectTerminalDecision?>()
        val completionFailure = AtomicReference<Throwable?>()
        val stopAccepted = AtomicBoolean(false)
        val completionThread = Thread(
            {
                try {
                    completionDecision.set(
                        atDeadline.tryCompleteBeforeElapsedRealtimeDeadline(
                            deadlineAtElapsedRealtimeNanos = 10_000L,
                            elapsedRealtimeNanos = {
                                clockEntered.countDown()
                                check(
                                    releaseClock.await(
                                        DEADLINE_RACE_TIMEOUT_SECONDS,
                                        TimeUnit.SECONDS,
                                    ),
                                ) {
                                    "Deadline race clock was not released."
                                }
                                10_000L
                            },
                        ),
                    )
                } catch (error: Throwable) {
                    completionFailure.set(error)
                } finally {
                    completionReturned.countDown()
                }
            },
            "r2-deadline-completion",
        )
        val stopThread = Thread(
            {
                stopAttempted.countDown()
                stopAccepted.set(
                    atDeadline.requestStop(R2ProjectStopCause.CANCELLED),
                )
                stopReturned.countDown()
            },
            "r2-deadline-stop",
        )

        completionThread.start()
        assertTrue(
            clockEntered.await(
                DEADLINE_RACE_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            ),
        )
        stopThread.start()
        assertTrue(
            stopAttempted.await(
                DEADLINE_RACE_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            ),
        )
        val blockedStateDeadline =
            System.nanoTime() +
                TimeUnit.SECONDS.toNanos(DEADLINE_RACE_TIMEOUT_SECONDS)
        while (
            stopThread.state != Thread.State.BLOCKED &&
            System.nanoTime() < blockedStateDeadline
        ) {
            Thread.yield()
        }
        assertEquals(Thread.State.BLOCKED, stopThread.state)
        assertFalse(stopReturned.await(100L, TimeUnit.MILLISECONDS))

        releaseClock.countDown()
        assertTrue(
            completionReturned.await(
                DEADLINE_RACE_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            ),
        )
        assertTrue(
            stopReturned.await(
                DEADLINE_RACE_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            ),
        )
        completionFailure.get()?.let { error ->
            throw AssertionError("Deadline completion decision failed.", error)
        }
        assertEquals(
            R2HostProjectTerminalDecision.STOP_REQUESTED,
            completionDecision.get(),
        )
        assertTrue(stopAccepted.get())
        assertEquals(
            R2ProjectStopCause.TIMED_OUT,
            atDeadline.acceptedStopCause(),
        )
        assertEquals(
            R2ProjectStopCause.TIMED_OUT,
            atDeadline.completeStopped(),
        )
        assertFalse(atDeadline.requestStop(R2ProjectStopCause.CANCELLED))
    }

    @Test
    fun framedControlParserIsStrictAndDecodesChunksOffTheMainThreadBoundary() {
        val ack = CompilerHostProtocol.parseMessage(
            """
            {"kind":"projectInputAck","requestId":"strict","sequence":9,
             "generation":3,"pageHasTypeScript":false}
            """.trimIndent(),
        ) as CompilerHostMessage.ProjectInputAck
        assertEquals("strict", ack.requestId)
        assertEquals(9, ack.sequence)
        assertEquals(3, ack.generation)

        val phase = CompilerHostProtocol.parseMessage(
            """
            {"kind":"projectPhase","requestId":"strict","phase":"semantic",
             "state":"started","generation":3,"pageHasTypeScript":false}
            """.trimIndent(),
        ) as CompilerHostMessage.ProjectPhase
        assertEquals(R2ProjectExecutionPhase.SEMANTIC, phase.phase)
        assertEquals(R2ProjectExecutionPhaseState.STARTED, phase.state)

        val progress = CompilerHostProtocol.parseMessage(
            """
            {"kind":"projectPhaseProgress","requestId":"strict","phase":"semantic",
             "sequence":2,"completedUnits":48,"totalUnits":192,
             "generation":3,"pageHasTypeScript":false}
            """.trimIndent(),
        ) as CompilerHostMessage.ProjectPhaseProgress
        assertEquals(R2ProjectExecutionPhase.SEMANTIC, progress.phase)
        assertEquals(2, progress.sequence)
        assertEquals(48, progress.completedUnits)
        assertEquals(192, progress.totalUnits)

        val maximumEmitProgress = CompilerHostProtocol.parseMessage(
            """
            {"kind":"projectPhaseProgress","requestId":"strict","phase":"emit",
             "sequence":2,"completedUnits":2304,"totalUnits":9216,
             "generation":3,"pageHasTypeScript":false}
            """.trimIndent(),
        ) as CompilerHostMessage.ProjectPhaseProgress
        assertEquals(9_216, maximumEmitProgress.totalUnits)

        val bytes = byteArrayOf(0, 1, 2, 3, -1)
        val chunk = CompilerHostProtocol.parseMessage(
            """
            {"kind":"projectResultChunk","requestId":"strict","index":0,
             "data":"${R2FramedBase64.encode(bytes)}",
             "generation":3,"pageHasTypeScript":false}
            """.trimIndent(),
        ) as CompilerHostMessage.ProjectResultChunk
        assertArrayEquals(bytes, chunk.bytes)

        val terminated = CompilerHostProtocol.parseMessage(
            """
            {"kind":"workerTerminated","requestId":"strict",
             "terminatedGeneration":3,"replacementGeneration":4,
             "phaseAtTermination":"none",
             "phaseProgressSequenceAtTermination":0,
             "phaseProgressCompletedUnitsAtTermination":0,
             "phaseProgressTotalUnitsAtTermination":0,
             "generation":4,"pageHasTypeScript":false}
            """.trimIndent(),
        ) as CompilerHostMessage.WorkerTerminated
        assertEquals(3, terminated.terminatedGeneration)
        assertEquals(4, terminated.replacementGeneration)

        val terminatedDuringSemantic = CompilerHostProtocol.parseMessage(
            """
            {"kind":"workerTerminated","requestId":"strict",
             "terminatedGeneration":3,"replacementGeneration":4,
             "phaseAtTermination":"semantic",
             "phaseProgressSequenceAtTermination":2,
             "phaseProgressCompletedUnitsAtTermination":48,
             "phaseProgressTotalUnitsAtTermination":192,
             "generation":4,"pageHasTypeScript":false}
            """.trimIndent(),
        ) as CompilerHostMessage.WorkerTerminated
        assertEquals(
            R2ProjectExecutionPhase.SEMANTIC,
            terminatedDuringSemantic.phaseAtTermination,
        )
        assertEquals(
            48,
            terminatedDuringSemantic.phaseProgressCompletedUnitsAtTermination,
        )

        assertFails {
            CompilerHostProtocol.parseMessage(
                """
                {"kind":"projectInputAck","requestId":"strict","sequence":9,
                 "generation":3,"pageHasTypeScript":false,"unknown":true}
                """.trimIndent(),
            )
        }
        assertFails {
            CompilerHostProtocol.parseMessage(
                """
                {"kind":"workerTerminated","requestId":"strict",
                 "terminatedGeneration":3,"replacementGeneration":4,
                 "phaseAtTermination":"none",
                 "phaseProgressSequenceAtTermination":0,
                 "phaseProgressCompletedUnitsAtTermination":1,
                 "phaseProgressTotalUnitsAtTermination":0,
                 "generation":4,"pageHasTypeScript":false}
                """.trimIndent(),
            )
        }
        assertFails {
            CompilerHostProtocol.parseMessage(
                """
                {"kind":"projectError","requestId":"strict","message":"failed",
                 "generation":3,"pageHasTypeScript":false,"unknown":true}
                """.trimIndent(),
            )
        }
        assertFails {
            CompilerHostProtocol.parseMessage(
                """
                {"kind":"projectInputAck","requestId":"strict","sequence":9,
                 "sequence":10,"generation":3,"pageHasTypeScript":false}
                """.trimIndent(),
            )
        }
        assertFails {
            CompilerHostProtocol.parseMessage(
                """
                {"kind":"workerTerminated","requestId":"strict",
                 "terminatedGeneration":3,"replacementGeneration":5,
                 "phaseAtTermination":"semantic",
                 "phaseProgressSequenceAtTermination":2,
                 "phaseProgressCompletedUnitsAtTermination":48,
                 "phaseProgressTotalUnitsAtTermination":192,
                 "generation":5,"pageHasTypeScript":false}
                """.trimIndent(),
            )
        }
        assertFails {
            CompilerHostProtocol.parseMessage(
                """
                {"kind":"projectPhaseProgress","requestId":"strict","phase":"semantic",
                 "sequence":2,"completedUnits":47,"totalUnits":192,
                 "generation":3,"pageHasTypeScript":false}
                """.trimIndent(),
            )
        }
        assertFails {
            CompilerHostProtocol.parseMessage(
                """
                {"kind":"projectPhaseProgress","requestId":"strict","phase":"emit",
                 "sequence":2,"completedUnits":2304,"totalUnits":9217,
                 "generation":3,"pageHasTypeScript":false}
                """.trimIndent(),
            )
        }
        assertFails {
            CompilerHostProtocol.parseMessage(
                """
                {"kind":"projectPhase","requestId":"strict","phase":"semantic",
                 "state":"finished","generation":3,"pageHasTypeScript":false}
                """.trimIndent(),
            )
        }
        assertFails {
            CompilerHostProtocol.parseMessage(
                """
                {"kind":"projectResult","requestId":"legacy","result":{},
                 "generation":3,"pageHasTypeScript":false}
                """.trimIndent(),
            )
        }
    }

    @Test
    fun framedResultParserRejectsDuplicateUnknownAndInvalidUnicodeFields() {
        val valid = strictDiagnosticResultPayload()
        val parsed = CompilerHostProtocol.parseFramedProjectResult(
            requestId = "strict-result",
            generation = 4,
            payload = valid.toByteArray(StandardCharsets.UTF_8),
        )
        assertTrue(parsed.compilation.emitSkipped)
        assertEquals(1, parsed.compilation.diagnostics.size)
        val authority =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO_REVISION_1
        assertEquals(authority.profileSchemaRevision, parsed.compilation.profileSchemaRevision)
        assertEquals(authority.profileRevision, parsed.compilation.profileRevision)
        assertEquals(authority.configSchemaRevision, parsed.compilation.configSchemaRevision)
        assertEquals(authority.configKind, parsed.compilation.configKind)
        assertEquals(authority.effectiveOptions, parsed.compilation.effectiveOptions)
        val strictDiagnostic = (
            R2StrictJson.parseObject(valid.toByteArray(StandardCharsets.UTF_8))["diagnostics"]
                as List<*>
            ).single() as Map<*, *>
        assertEquals(NORMAL_DIAGNOSTIC_KEYS, strictDiagnostic.keys)

        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "strict-result",
                generation = 4,
                payload = valid.replace(
                    "\"strict\":true",
                    "\"strict\":true,\"strict\":true",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "strict-result",
                generation = 4,
                payload = valid.replace(
                    "\"emitSkipped\":true",
                    "\"unknown\":null,\"emitSkipped\":true",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "strict-result",
                generation = 4,
                payload = valid.replace(
                    "\"message\":\"error\"",
                    """"message":"\uD800"""",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "strict-result",
                generation = 4,
                payload = valid.replace(
                    "\"configKind\":\"none\"",
                    "\"configKind\":\"tsconfig\"",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "strict-result",
                generation = 4,
                payload = valid.replace(
                    "\"allowJs\":false",
                    "\"allowJs\":true",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "strict-result",
                generation = 4,
                payload = valid.replace(
                    "\"types\":[]",
                    "\"types\":[\"node\"]",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
    }

    @Test
    fun framedResultParserRetainsStrictBoundedRelatedInformation() {
        val relatedJson =
            "\"relatedInformation\":[{" +
                "\"category\":3," +
                "\"code\":6500," +
                "\"column\":2," +
                "\"length\":4," +
                "\"line\":3," +
                "\"message\":\"Related declaration.\"," +
                "\"sourcePath\":\"/declarations/lib.d.ts\"," +
                "\"start\":7}],"
        val payload = strictDiagnosticResultPayload().replace(
            "\"phase\":\"semantic\",",
            relatedJson + "\"phase\":\"semantic\",",
        )

        val compilation = CompilerHostProtocol.parseFramedProjectResult(
            requestId = "strict-result-related-information",
            generation = 4,
            payload = payload.toByteArray(StandardCharsets.UTF_8),
        ).compilation
        val related = compilation.diagnostics.single().relatedInformation.single()

        assertEquals(3, related.category)
        assertEquals(6_500, related.code)
        assertEquals("Related declaration.", related.message)
        assertEquals("/declarations/lib.d.ts", related.sourcePath)
        assertEquals(7, related.start)
        assertEquals(4, related.length)
        assertEquals(3, related.line)
        assertEquals(2, related.column)

        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "strict-result-related-partial-range",
                generation = 4,
                payload = payload.replace("\"column\":2", "\"column\":null")
                    .toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "strict-result-related-unknown-field",
                generation = 4,
                payload = payload.replace(
                    "\"start\":7}",
                    "\"start\":7,\"unknown\":true}",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
    }

    @Test
    fun framedResultParserPreservesExactUnifiedPreEmitDiagnosticPhases() {
        val phases = listOf(
            R2ProjectDiagnosticPhase.OPTIONS,
            R2ProjectDiagnosticPhase.SYNTACTIC,
            R2ProjectDiagnosticPhase.GLOBAL,
            R2ProjectDiagnosticPhase.SEMANTIC,
        )

        phases.forEachIndexed { index, phase ->
            val hasLocation = phase == R2ProjectDiagnosticPhase.SYNTACTIC ||
                phase == R2ProjectDiagnosticPhase.SEMANTIC
            val code = 910_300 + index
            val payload = strictUnifiedPreEmitDiagnosticResultPayload(
                phase = phase,
                code = code,
                hasLocation = hasLocation,
            )
            val compilation = CompilerHostProtocol.parseFramedProjectResult(
                requestId = "unified-${phase.protocolValue}",
                generation = 4,
                payload = payload.toByteArray(StandardCharsets.UTF_8),
            ).compilation
            val diagnostic = compilation.diagnostics.single()

            assertTrue(compilation.emitSkipped)
            assertTrue(compilation.outputs.isEmpty())
            assertEquals(phase, diagnostic.phase)
            assertEquals(1, diagnostic.category)
            assertEquals(code, diagnostic.code)
            assertEquals("${phase.protocolValue} error", diagnostic.message)
            assertEquals(if (hasLocation) "/sources/main.ts" else null, diagnostic.sourcePath)
            assertEquals(if (hasLocation) 0 else null, diagnostic.start)
            assertEquals(if (hasLocation) 1 else null, diagnostic.length)
            assertEquals(if (hasLocation) 1 else null, diagnostic.line)
            assertEquals(if (hasLocation) 1 else null, diagnostic.column)
            val encodedDiagnostic = (
                R2StrictJson.parseObject(
                    payload.toByteArray(StandardCharsets.UTF_8),
                )["diagnostics"] as List<*>
                ).single() as Map<*, *>
            assertEquals(NORMAL_DIAGNOSTIC_KEYS, encodedDiagnostic.keys)

            assertFails {
                CompilerHostProtocol.parseFramedProjectResult(
                    requestId = "unified-${phase.protocolValue}-case-drift",
                    generation = 4,
                    payload = payload.replace(
                        "\"phase\":\"${phase.protocolValue}\"",
                        "\"phase\":\"${phase.name}\"",
                    ).toByteArray(StandardCharsets.UTF_8),
                )
            }
            assertFails {
                CompilerHostProtocol.parseFramedProjectResult(
                    requestId = "unified-${phase.protocolValue}-space-drift",
                    generation = 4,
                    payload = payload.replace(
                        "\"phase\":\"${phase.protocolValue}\"",
                        "\"phase\":\"${phase.protocolValue} \"",
                    ).toByteArray(StandardCharsets.UTF_8),
                )
            }
        }
    }

    @Test
    fun framedResultParserAcceptsExactNodeAuthorityAndRejectsItsDrift() {
        val valid = strictNodeDiagnosticResultPayload()
        val parsed = CompilerHostProtocol.parseFramedProjectResult(
            requestId = "node-result",
            generation = 4,
            payload = valid.toByteArray(StandardCharsets.UTF_8),
        ).compilation
        val authority = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT

        assertTrue(parsed.emitSkipped)
        assertEquals(authority.profile.contractValue, parsed.targetProfile)
        assertEquals(authority.profileRevision, parsed.profileRevision)
        assertEquals(199, parsed.effectiveOptions.module)
        assertEquals(99, parsed.effectiveOptions.moduleResolution)
        assertEquals(authority.effectiveOptions, parsed.effectiveOptions)

        listOf(
            valid.replace("\"targetProfile\":\"node\"", "\"targetProfile\":\"node-drift\""),
            valid.replace("\"profileRevision\":1", "\"profileRevision\":0"),
            valid.replace("\"profileRevision\":1", "\"profileRevision\":2"),
            valid.replace("\"profileRevision\":1", "\"profileRevision\":3"),
            valid.replace("\"module\":199", "\"module\":1"),
            valid.replace("\"moduleResolution\":99", "\"moduleResolution\":2"),
        ).forEach { drifted ->
            assertFails {
                CompilerHostProtocol.parseFramedProjectResult(
                    requestId = "node-result",
                    generation = 4,
                    payload = drifted.toByteArray(StandardCharsets.UTF_8),
                )
            }
        }
    }

    @Test
    fun nodeRevisionTwoFramedResultRequiresExactSnapshotAndDerivesNativeMappings() {
        val snapshot = nodeRevisionTwoSnapshot(sourcePath = "/sources/main.mts")
        val withoutProjectId = nodeRevisionTwoSnapshot(
            sourcePath = "/sources/main.mts",
            acceptedProjectId = null,
        )
        val valid = strictNodeRevisionTwoSuccessResultPayload(
            sourcePath = "/sources/main.mts",
            modulePath = "/outputs/main.mjs",
        )
        try {
            assertFails {
                CompilerHostProtocol.parseFramedProjectResult(
                    requestId = "node-revision-two-result",
                    generation = 4,
                    payload = valid.toByteArray(StandardCharsets.UTF_8),
                )
            }
            assertFails {
                CompilerHostProtocol.parseFramedProjectResult(
                    requestId = "node-revision-two-result",
                    generation = 4,
                    payload = valid.toByteArray(StandardCharsets.UTF_8),
                    exactSnapshot = withoutProjectId,
                )
            }

            val parsed = CompilerHostProtocol.parseFramedProjectResult(
                requestId = "node-revision-two-result",
                generation = 4,
                payload = valid.toByteArray(StandardCharsets.UTF_8),
                exactSnapshot = snapshot,
            ).compilation
            val resultAuthority = requireNotNull(parsed.nodeRevisionTwoResultAuthority)
            val mapping = parsed.generatedModuleMappings.single()
            assertEquals(snapshot.acceptedProjectId, resultAuthority.acceptedProjectId)
            assertEquals("/sources/main.mts", mapping.sourcePath)
            assertEquals(R2ModuleFormat.ESM, mapping.moduleFormat)
            assertEquals("/outputs/main.mjs", mapping.generatedPath)
            assertEquals("/outputs/main.mjs.map", mapping.sourceMapPath)

            assertFails {
                CompilerHostProtocol.parseFramedProjectResult(
                    requestId = "node-revision-two-result",
                    generation = 4,
                    payload = strictNodeRevisionTwoSuccessResultPayload(
                        sourcePath = "/sources/main.mts",
                        modulePath = "/outputs/main.js",
                    ).toByteArray(StandardCharsets.UTF_8),
                    exactSnapshot = snapshot,
                )
            }
        } finally {
            snapshot.close()
            withoutProjectId.close()
        }
    }

    @Test
    fun nodeRevisionTwoConfigNoneResolutionDiagnosticKeepsNativeSnapshotAuthority() {
        val snapshot = nodeRevisionTwoSnapshot()
        val failure = R2NodeResolutionFailureCode.INVALID_CONTAINING_PATH
        val payload = strictConfigNoneResolutionDiagnosticResultPayload(failure)
            .replace("\"module\":1", "\"module\":199")
            .replace("\"moduleResolution\":2", "\"moduleResolution\":99")
            .replace("\"profileRevision\":1", "\"profileRevision\":2")
            .replace("\"targetProfile\":\"rhino\"", "\"targetProfile\":\"node\"")
        try {
            val compilation = CompilerHostProtocol.parseFramedProjectResult(
                requestId = "node-revision-two-resolution",
                generation = 4,
                payload = payload.toByteArray(StandardCharsets.UTF_8),
                exactSnapshot = snapshot,
            ).compilation

            assertTrue(compilation.emitSkipped)
            assertTrue(compilation.outputs.isEmpty())
            assertTrue(compilation.generatedModuleMappings.isEmpty())
            assertEquals(
                failure,
                requireNotNull(compilation.diagnostics.single().nodeResolution).policyCode,
            )
            assertEquals(
                snapshot.entryPath,
                requireNotNull(compilation.nodeRevisionTwoResultAuthority).entryPath,
            )
        } finally {
            snapshot.close()
        }
    }

    @Test
    fun nodeRevisionTwoDiagnosticsKeepTrustedDeclarationsDtsOnlyAndPackageTargetsV2() {
        val packageBytes = "{\"type\":\"module\"}".toByteArray(StandardCharsets.UTF_8)
        val targetBytes = "export {};\n".toByteArray(StandardCharsets.UTF_8)
        val targetPath = "$PACKAGE_DEPENDENCY_ROOT/index.d.mts"
        val snapshot = nodeRevisionTwoSnapshot(
            dependencyRoots = listOf(PACKAGE_DEPENDENCY_ROOT),
            packageFiles = listOf(
                PACKAGE_JSON_ARCHIVE to packageBytes,
                DEPENDENCY_PACKAGE_JSON_ARCHIVE to packageBytes,
            ),
            nodePackageTargetV2Files = listOf(targetPath to targetBytes),
        )
        val failure = R2NodeResolutionFailureCode.TARGET_NOT_IN_SNAPSHOT
        val basePayload = strictConfigNoneResolutionDiagnosticResultPayload(failure)
            .replace("\"module\":1", "\"module\":199")
            .replace("\"moduleResolution\":2", "\"moduleResolution\":99")
            .replace("\"profileRevision\":1", "\"profileRevision\":2")
            .replace("\"targetProfile\":\"rhino\"", "\"targetProfile\":\"node\"")
        try {
            val accepted = CompilerHostProtocol.parseFramedProjectResult(
                requestId = "node-revision-two-package-declaration-diagnostic",
                generation = 4,
                payload = basePayload.replace(
                    "\"sourcePath\":null",
                    "\"sourcePath\":\"/$targetPath\"",
                ).toByteArray(StandardCharsets.UTF_8),
                exactSnapshot = snapshot,
            ).compilation
            assertEquals("/$targetPath", accepted.diagnostics.single().sourcePath)

            assertFails {
                CompilerHostProtocol.parseFramedProjectResult(
                    requestId = "node-revision-two-trusted-declaration-diagnostic",
                    generation = 4,
                    payload = basePayload.replace(
                        "\"sourcePath\":null",
                        "\"sourcePath\":\"/declarations/types.d.mts\"",
                    ).toByteArray(StandardCharsets.UTF_8),
                    exactSnapshot = snapshot,
                )
            }
        } finally {
            snapshot.close()
            packageBytes.fill(0)
            targetBytes.fill(0)
        }
    }

    @Test
    fun framedResultParserAcceptsEveryExactNodeResolutionDiagnosticIdentity() {
        R2NodeResolutionFailureCode.entries.forEach { failure ->
            val parsed = CompilerHostProtocol.parseFramedProjectResult(
                requestId = "resolution-${failure.diagnosticCode}",
                generation = 4,
                payload = strictResolutionDiagnosticResultPayload(failure)
                    .toByteArray(StandardCharsets.UTF_8),
            ).compilation

            assertTrue(parsed.emitSkipped)
            assertTrue(parsed.outputs.isEmpty())
            val diagnostic = parsed.diagnostics.single()
            assertEquals(R2ProjectDiagnosticPhase.RESOLUTION, diagnostic.phase)
            assertEquals(failure.diagnosticCode, diagnostic.code)
            assertEquals(PACKAGE_TARGET_SOURCE, diagnostic.sourcePath)
            assertEquals(failure, requireNotNull(diagnostic.nodeResolution).policyCode)
        }
    }

    @Test
    fun framedResultParserRejectsMalformedNodeResolutionDiagnosticUnionVariants() {
        val failure = R2NodeResolutionFailureCode.INVALID_CONTAINING_PATH

        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "resolution-unknown-policy",
                generation = 4,
                payload = strictResolutionDiagnosticResultPayload(
                    failure = failure,
                    nodeResolutionJson =
                        "{\"policyCode\":\"NODE_RESOLUTION_NOT_FROZEN\"}",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "resolution-wrong-numeric",
                generation = 4,
                payload = strictResolutionDiagnosticResultPayload(
                    failure = failure,
                    diagnosticCode = failure.diagnosticCode + 1,
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "resolution-wrong-shape",
                generation = 4,
                payload = strictResolutionDiagnosticResultPayload(
                    failure = failure,
                    nodeResolutionJson =
                        "{\"policyCode\":\"${failure.contractValue}\",\"unknown\":true}",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "resolution-missing-evidence",
                generation = 4,
                payload = strictResolutionDiagnosticResultPayload(
                    failure = failure,
                    nodeResolutionJson = null,
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "ordinary-with-evidence",
                generation = 4,
                payload = strictResolutionDiagnosticResultPayload(
                    failure = failure,
                    phase = "semantic",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "resolution-with-range",
                generation = 4,
                payload = strictResolutionDiagnosticResultPayload(failure)
                    .replace("\"column\":null", "\"column\":1")
                    .replace("\"length\":null", "\"length\":1")
                    .replace("\"line\":null", "\"line\":1")
                    .replace("\"start\":null", "\"start\":0")
                    .toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "resolution-package-metadata",
                generation = 4,
                payload = strictResolutionDiagnosticResultPayload(failure)
                    .replace(
                        "\"sourcePath\":\"$PACKAGE_TARGET_SOURCE\"",
                        "\"sourcePath\":\"/packages/dependency/package.json\"",
                    )
                    .toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "config-none-resolution",
                generation = 4,
                payload = strictConfigNoneResolutionDiagnosticResultPayload(failure)
                    .toByteArray(StandardCharsets.UTF_8),
            )
        }
    }

    @Test
    fun framedResultParserRejectsUnknownOutputAndSourceMapFields() {
        val valid = strictSuccessResultPayload(strictSourceMapText())
        val parsed = CompilerHostProtocol.parseFramedProjectResult(
            requestId = "strict-output",
            generation = 4,
            payload = valid.toByteArray(StandardCharsets.UTF_8),
        )
        assertFalse(parsed.compilation.emitSkipped)
        assertEquals(2, parsed.compilation.outputs.size)

        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "strict-output",
                generation = 4,
                payload = valid.replace(
                    "\"text\":\"x\"",
                    "\"text\":\"x\",\"unknown\":true",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            val sourceMapWithUnknown = strictSourceMapText()
                .removeSuffix("}") + ",\"unknown\":true}"
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "strict-output",
                generation = 4,
                payload = strictSuccessResultPayload(sourceMapWithUnknown)
                    .toByteArray(StandardCharsets.UTF_8),
            )
        }
    }

    @Test
    fun framedResultParserAcceptsMappingsBeyondTheLegacyHalfMebibyteGate() {
        val mappings = "A".repeat(512 * 1024 + 1)
        val sourceMap = strictSourceMapText().replace(
            "\"mappings\":\"\"",
            "\"mappings\":\"$mappings\"",
        )

        val parsed = CompilerHostProtocol.parseFramedProjectResult(
            requestId = "expanded-source-map",
            generation = 4,
            payload = strictSuccessResultPayload(sourceMap)
                .toByteArray(StandardCharsets.UTF_8),
        )
        val parsedMap = R2StrictJson.parseObject(
            parsed.compilation.sourceMaps.single().text.toByteArray(StandardCharsets.UTF_8),
        )

        assertEquals(mappings.length, (parsedMap.getValue("mappings") as String).length)
    }

    @Test
    fun framedResultParserBindsControlledRootsAndExactEffectiveOptions() {
        val effective = R2ControlledTsConfigEffectiveOptions.resolve(
            mapOf("noUnusedLocals" to true),
        )
        val valid = strictControlledDiagnosticResultPayload(effective)
        val parsed = CompilerHostProtocol.parseFramedProjectResult(
            requestId = "controlled-result",
            generation = 4,
            payload = valid.toByteArray(StandardCharsets.UTF_8),
        ).compilation

        assertEquals(TypeScriptR2Contract.CONFIG_KIND_TSCONFIG, parsed.configKind)
        assertEquals(
            TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
            parsed.configSchemaRevision,
        )
        assertEquals(
            effective.canonicalJson,
            requireNotNull(parsed.controlledEffectiveOptions).canonicalJson,
        )
        assertEquals(effective.fingerprint, parsed.controlledEffectiveOptions.fingerprint)
        assertEquals(listOf("/sources/main.ts", "/sources/types.d.ts"), parsed.selectedRootNames)

        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "controlled-result",
                generation = 4,
                payload = valid.replace(
                    effective.fingerprint,
                    "0".repeat(64),
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "controlled-result",
                generation = 4,
                payload = valid.replace(
                    "\"noUnusedLocals\":true",
                    "\"noUnusedLocals\":false",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "controlled-result",
                generation = 4,
                payload = valid.replace(
                    "[\"/sources/main.ts\",\"/sources/types.d.ts\"]",
                    "[]",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
        val excessiveRoots = (0..R2_FILE_BACKED_MAXIMUM_FILES).joinToString(
            prefix = "[",
            postfix = "]",
        ) { index ->
            "\"/sources/root-${index.toString().padStart(5, '0')}.ts\""
        }
        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "controlled-result",
                generation = 4,
                payload = valid.replace(
                    "[\"/sources/main.ts\",\"/sources/types.d.ts\"]",
                    excessiveRoots,
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
    }

    @Test
    fun framedResultParserBindsRevision4BaseUrlAndPathsWithoutFlattening() {
        val effective = R2ControlledTsConfigEffectiveOptions.resolveRevision4(
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            explicitBooleanOptions = emptyMap(),
            baseUrl = ".",
            paths = linkedMapOf(
                "@/*" to listOf("src/*"),
                "@feature/*" to listOf("src/features/*"),
            ),
        )
        val valid = strictControlledDiagnosticResultPayload(effective)
        val parsed = CompilerHostProtocol.parseFramedProjectResult(
            requestId = "controlled-revision4-result",
            generation = 4,
            payload = valid.toByteArray(StandardCharsets.UTF_8),
        ).compilation

        val parsedEffective = requireNotNull(parsed.controlledEffectiveOptions)
        assertEquals(R2ControlledTsConfigEffectiveOptions.REVISION_4, parsedEffective.revision)
        assertEquals(".", parsed.effectiveOptions.baseUrl)
        assertEquals(effective.compilerOptions.paths, parsed.effectiveOptions.paths)
        assertEquals(effective.canonicalJson, parsedEffective.canonicalJson)
        assertEquals(effective.fingerprint, parsedEffective.fingerprint)

        assertFails {
            CompilerHostProtocol.parseFramedProjectResult(
                requestId = "controlled-revision4-result",
                generation = 4,
                payload = valid.replace(
                    "\"src/features/*\"",
                    "\"../outside/*\"",
                ).toByteArray(StandardCharsets.UTF_8),
            )
        }
    }

    private fun strictDiagnosticResultPayload(): String =
        """
        {
          "compilerApi":"createIncrementalProgram",
          "configKind":"none",
          "configSchemaRevision":1,
          "diagnostics":[{
            "category":1,
            "code":2322,
            "column":1,
            "length":1,
            "line":1,
            "message":"error",
            "phase":"semantic",
            "sourcePath":"/sources/main.ts",
            "start":0
          }],
          "emitSkipped":true,
          "options":{
            "allowJs":false,
            "checkJs":false,
            "ignoreDeprecations":"6.0",
            "incremental":false,
            "inlineSourceMap":false,
            "inlineSources":false,
            "module":1,
            "moduleResolution":2,
            "newLine":1,
            "noEmitOnError":true,
            "noLib":false,
            "outDir":"/outputs",
            "rootDir":"/sources",
            "skipLibCheck":false,
            "sourceMap":true,
            "strict":true,
            "target":5,
            "types":[]
          },
          "outputs":[],
          "profileRevision":1,
          "profileSchemaRevision":1,
          "targetProfile":"rhino",
          "tsVersion":"${BuildConfig.TYPESCRIPT_VERSION}"
        }
        """.trimIndent()

    private fun strictUnifiedPreEmitDiagnosticResultPayload(
        phase: R2ProjectDiagnosticPhase,
        code: Int,
        hasLocation: Boolean,
    ): String {
        val phasePayload = strictDiagnosticResultPayload()
            .replace("\"code\":2322", "\"code\":$code")
            .replace("\"message\":\"error\"", "\"message\":\"${phase.protocolValue} error\"")
            .replace("\"phase\":\"semantic\"", "\"phase\":\"${phase.protocolValue}\"")
        if (hasLocation) return phasePayload
        return phasePayload
            .replace("\"column\":1", "\"column\":null")
            .replace("\"length\":1", "\"length\":null")
            .replace("\"line\":1", "\"line\":null")
            .replace("\"sourcePath\":\"/sources/main.ts\"", "\"sourcePath\":null")
            .replace("\"start\":0", "\"start\":null")
    }

    private fun strictNodeDiagnosticResultPayload(): String =
        strictDiagnosticResultPayload()
            .replace("\"module\":1", "\"module\":199")
            .replace("\"moduleResolution\":2", "\"moduleResolution\":99")
            .replace("\"targetProfile\":\"rhino\"", "\"targetProfile\":\"node\"")

    private fun strictResolutionDiagnosticResultPayload(
        failure: R2NodeResolutionFailureCode,
        diagnosticCode: Int = failure.diagnosticCode,
        phase: String = "resolution",
        nodeResolutionJson: String? =
            "{\"policyCode\":\"${failure.contractValue}\"}",
    ): String {
        val evidenceField = nodeResolutionJson?.let { encoded ->
            "\"nodeResolution\":$encoded,"
        }.orEmpty()
        val effective = R2ControlledTsConfigEffectiveOptions.resolve(emptyMap())
        return """
        {
          "compilerApi":"createIncrementalProgram",
          "configKind":"tsconfig",
          "configSchemaRevision":2,
          "diagnostics":[{
            "category":1,
            "code":$diagnosticCode,
            "column":null,
            "length":null,
            "line":null,
            "message":"Controlled Node resolution failed.",
            $evidenceField
            "phase":"$phase",
            "sourcePath":"$PACKAGE_TARGET_SOURCE",
            "start":null
          }],
          "effectiveOptionsFingerprint":"${effective.fingerprint}",
          "effectiveOptionsRevision":${effective.revision},
          "emitSkipped":true,
          "options":${effective.canonicalJson},
          "outputs":[],
          "profileRevision":2,
          "profileSchemaRevision":1,
          "rootNames":["/sources/main.ts"],
          "targetProfile":"rhino",
          "tsVersion":"${BuildConfig.TYPESCRIPT_VERSION}"
        }
        """.trimIndent()
    }

    private fun strictConfigNoneResolutionDiagnosticResultPayload(
        failure: R2NodeResolutionFailureCode,
    ): String = strictDiagnosticResultPayload()
        .replace("\"code\":2322", "\"code\":${failure.diagnosticCode}")
        .replace("\"column\":1", "\"column\":null")
        .replace("\"length\":1", "\"length\":null")
        .replace("\"line\":1", "\"line\":null")
        .replace(
            "\"message\":\"error\"",
            "\"message\":\"Controlled Node resolution failed.\"," +
                "\"nodeResolution\":{\"policyCode\":\"${failure.contractValue}\"}",
        )
        .replace("\"phase\":\"semantic\"", "\"phase\":\"resolution\"")
        .replace("\"sourcePath\":\"/sources/main.ts\"", "\"sourcePath\":null")
        .replace("\"start\":0", "\"start\":null")

    private fun strictSuccessResultPayload(sourceMapText: String): String {
        val moduleText = "x"
        val escapedSourceMap = sourceMapText.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
        {
          "compilerApi":"createIncrementalProgram",
          "configKind":"none",
          "configSchemaRevision":1,
          "diagnostics":[],
          "emitSkipped":false,
          "options":{
            "allowJs":false,
            "checkJs":false,
            "ignoreDeprecations":"6.0",
            "incremental":false,
            "inlineSourceMap":false,
            "inlineSources":false,
            "module":1,
            "moduleResolution":2,
            "newLine":1,
            "noEmitOnError":true,
            "noLib":false,
            "outDir":"/outputs",
            "rootDir":"/sources",
            "skipLibCheck":false,
            "sourceMap":true,
            "strict":true,
            "target":5,
            "types":[]
          },
          "outputs":[
            {
              "path":"/outputs/main.js",
              "sha256":"${R2Digests.sha256(moduleText.toByteArray(StandardCharsets.UTF_8))}",
              "sourcePaths":["/sources/main.ts"],
              "text":"$moduleText"
            },
            {
              "path":"/outputs/main.js.map",
              "sha256":"${R2Digests.sha256(sourceMapText.toByteArray(StandardCharsets.UTF_8))}",
              "sourcePaths":["/sources/main.ts"],
              "text":"$escapedSourceMap"
            }
          ],
          "profileRevision":1,
          "profileSchemaRevision":1,
          "targetProfile":"rhino",
          "tsVersion":"${BuildConfig.TYPESCRIPT_VERSION}"
        }
        """.trimIndent()
    }

    private fun strictNodeRevisionTwoSuccessResultPayload(
        sourcePath: String,
        modulePath: String,
    ): String {
        val moduleText = "x"
        val sourceMapText =
            "{\"version\":3,\"file\":\"${modulePath.substringAfterLast('/')}\"," +
                "\"sourceRoot\":\"\",\"sources\":[\"../sources/" +
                "${sourcePath.substringAfterLast('/')}\"],\"names\":[],\"mappings\":\"AAAA\"}"
        val escapedSourceMap = sourceMapText.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
        {
          "compilerApi":"createIncrementalProgram",
          "configKind":"none",
          "configSchemaRevision":1,
          "diagnostics":[],
          "emitSkipped":false,
          "options":{
            "allowJs":false,
            "checkJs":false,
            "ignoreDeprecations":"6.0",
            "incremental":false,
            "inlineSourceMap":false,
            "inlineSources":false,
            "module":199,
            "moduleResolution":99,
            "newLine":1,
            "noEmitOnError":true,
            "noLib":false,
            "outDir":"/outputs",
            "rootDir":"/sources",
            "skipLibCheck":false,
            "sourceMap":true,
            "strict":true,
            "target":5,
            "types":[]
          },
          "outputs":[
            {
              "path":"$modulePath",
              "sha256":"${R2Digests.sha256(moduleText.toByteArray(StandardCharsets.UTF_8))}",
              "sourcePaths":["$sourcePath"],
              "text":"$moduleText"
            },
            {
              "path":"$modulePath.map",
              "sha256":"${R2Digests.sha256(sourceMapText.toByteArray(StandardCharsets.UTF_8))}",
              "sourcePaths":["$sourcePath"],
              "text":"$escapedSourceMap"
            }
          ],
          "profileRevision":2,
          "profileSchemaRevision":1,
          "targetProfile":"node",
          "tsVersion":"${BuildConfig.TYPESCRIPT_VERSION}"
        }
        """.trimIndent()
    }

    private fun strictControlledDiagnosticResultPayload(
        effective: R2ControlledTsConfigEffectiveOptions,
    ): String =
        """
        {
          "compilerApi":"createIncrementalProgram",
          "configKind":"tsconfig",
          "configSchemaRevision":2,
          "diagnostics":[{
            "category":1,
            "code":2322,
            "column":1,
            "length":1,
            "line":1,
            "message":"error",
            "phase":"semantic",
            "sourcePath":"/sources/main.ts",
            "start":0
          }],
          "effectiveOptionsFingerprint":"${effective.fingerprint}",
          "effectiveOptionsRevision":${effective.revision},
          "emitSkipped":true,
          "options":${effective.canonicalJson},
          "outputs":[],
          "profileRevision":2,
          "profileSchemaRevision":1,
          "rootNames":["/sources/main.ts","/sources/types.d.ts"],
          "targetProfile":"rhino",
          "tsVersion":"${BuildConfig.TYPESCRIPT_VERSION}"
        }
        """.trimIndent()

    private fun strictSourceMapText(): String =
        """{"version":3,"file":"main.js","sourceRoot":"","sources":["../sources/main.ts"],"names":[],"mappings":""}"""

    private fun Map<String, Any?>.intValue(key: String): Int =
        (get(key) as R2StrictJson.NumberToken).source.toInt()

    private fun Map<String, Any?>.longValue(key: String): Long =
        (get(key) as R2StrictJson.NumberToken).source.toLong()

    private fun parseFrame(frame: String): Map<String, Any?> =
        R2StrictJson.parseObject(frame.toByteArray(StandardCharsets.UTF_8))

    private fun assertInputAuthority(
        start: Map<String, Any?>,
        snapshot: R2ProjectInputSnapshot,
    ) {
        val authority = snapshot.acceptedInputAuthority
            ?: R2InputArchiveSchemaV1.compatibilityAuthority(snapshot)
        assertEquals(authority.schemaRevision, start.intValue("inputArchiveSchemaRevision"))
        assertEquals(authority.inputQuotaRevision, start.intValue("inputQuotaRevision"))
        assertEquals(authority.callerInventoryFingerprint, start["callerInventoryFingerprint"])
        assertEquals(authority.retainedInventoryFingerprint, start["retainedInventoryFingerprint"])
        assertEquals(authority.capacity.fileCount, start.intValue("retainedFileCount"))
        assertEquals(authority.capacity.largestFileUtf8Bytes, start.longValue("largestFileBytes"))
        assertEquals(
            authority.declarationProfileFingerprint,
            start["declarationProfileFingerprint"],
        )
        assertEquals(
            authority.declarationInventoryFingerprint,
            start["declarationInventoryFingerprint"],
        )
        assertEquals(authority.declarationFileCount, start.intValue("declarationFileCount"))
        assertEquals(authority.declarationByteLength, start.longValue("declarationByteLength"))
    }

    private fun rawConfigSnapshot(
        rawConfig: ByteArray,
        dependencyLayerFiles: List<Pair<String, ByteArray>> = emptyList(),
    ): R2ProjectSnapshot {
        val descriptor = R2RawProjectConfigDescriptor(
            path = R2ControlledTsConfigPolicy.CONFIG_PATH,
            size = rawConfig.size.toLong(),
            sha256 = R2Digests.sha256(rawConfig),
            policyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
            policyFingerprint = R2ControlledTsConfigPolicy.revision4Fingerprint,
        )
        val dependencyInventory = dependencyLayerFiles
            .takeIf { files -> files.isNotEmpty() }
            ?.let { files ->
                val descriptors = files.map { (path, bytes) ->
                    R2DependencyLayerFileDescriptor(
                        path = path,
                        size = bytes.size.toLong(),
                        sha256 = R2Digests.sha256(bytes),
                    )
                }
                val lockfile = descriptors.first { descriptor ->
                    descriptor.path == "dependencies/lockfiles/package-lock.json"
                }
                R2DependencyLayerInventory.create(
                    identityKind = R2DependencyLayerPolicy.IDENTITY_PACKAGE_LOCK,
                    identityPath = lockfile.path,
                    identityFingerprint = lockfile.sha256,
                    files = descriptors,
                )
            }
        return R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile("/declarations/lib.d.ts", "declare interface Array<T> {}\n"),
                R2ProjectTextFile("/sources/main.ts", "export const value = 1;\n"),
            ),
            rootNames = listOf("/sources/main.ts"),
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
            rawConfigDescriptor = descriptor,
            rawConfigBytes = rawConfig,
            dependencyLayerInventory = dependencyInventory,
            dependencyLayerBytes = dependencyLayerFiles.map { file -> file.second },
        )
    }

    private fun nodeRevisionTwoSnapshot(
        dependencyRoots: List<String> = emptyList(),
        packageFiles: List<Pair<String, ByteArray>> = emptyList(),
        nodePackageTargetV2Files: List<Pair<String, ByteArray>> = emptyList(),
        sourcePath: String = "/sources/main.ts",
        acceptedProjectId: String? = "node-revision-two-test-project",
        rawConfigBytes: ByteArray? = null,
        dependencyLayerFiles: List<Pair<String, ByteArray>> = emptyList(),
    ): R2ProjectSnapshot {
        val inventory = packageFiles
            .takeIf { files -> files.isNotEmpty() }
            ?.let { files ->
                R2PackageMetadataInventory.create(
                    dependencyRoots = dependencyRoots,
                    files = files.map { (path, bytes) ->
                        R2PackageMetadataFileDescriptor(
                            path = path,
                            size = bytes.size.toLong(),
                            sha256 = R2Digests.sha256(bytes),
                        )
                    },
                )
            }
        val targetInventory = nodePackageTargetV2Files
            .takeIf { files -> files.isNotEmpty() }
            ?.let { files ->
                R2NodePackageTargetV2Inventory.create(
                    packageMetadataInventory = requireNotNull(inventory),
                    files = files.map { (path, bytes) ->
                        R2NodePackageTargetV2FileDescriptor(
                            path = path,
                            size = bytes.size.toLong(),
                            sha256 = R2Digests.sha256(bytes),
                        )
                    },
                )
            }
        val projectFiles = listOf(
            R2ProjectTextFile("/declarations/lib.d.ts", "declare interface Array<T> {}\n"),
            R2ProjectTextFile("/declarations/node/index.d.ts", "declare const process: {};\n"),
            R2ProjectTextFile(sourcePath, "export const value = 1;\n"),
        )
        val dependencyInventory = dependencyLayerFiles
            .takeIf { files -> files.isNotEmpty() }
            ?.let { files ->
                val descriptors = files.map { (path, bytes) ->
                    R2DependencyLayerFileDescriptor(
                        path = path,
                        size = bytes.size.toLong(),
                        sha256 = R2Digests.sha256(bytes),
                    )
                }
                val lockfile = descriptors.first { descriptor ->
                    descriptor.path == "dependencies/lockfiles/package-lock.json"
                }
                R2DependencyLayerInventory.create(
                    identityKind = R2DependencyLayerPolicy.IDENTITY_PACKAGE_LOCK,
                    identityPath = lockfile.path,
                    identityFingerprint = lockfile.sha256,
                    files = descriptors,
                )
            }
        fun createSnapshot(
            projectId: String?,
            inputAuthority: R2AcceptedInputAuthorityV1?,
        ): R2ProjectSnapshot = R2ProjectSnapshot.create(
            files = projectFiles,
            rootNames = listOf(sourcePath),
            entryPath = sourcePath,
            acceptedProjectId = projectId,
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
            packageMetadataInventory = inventory,
            packageMetadataBytes = packageFiles.map { packageFile -> packageFile.second },
            nodePackageTargetV2Inventory = targetInventory,
            nodePackageTargetV2Bytes =
                nodePackageTargetV2Files.map { targetFile -> targetFile.second },
            rawConfigDescriptor = rawConfigBytes?.let { bytes ->
                R2RawProjectConfigDescriptor(
                    path = R2ControlledTsConfigPolicy.CONFIG_PATH,
                    size = bytes.size.toLong(),
                    sha256 = R2Digests.sha256(bytes),
                    policyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
                    policyFingerprint = R2ControlledTsConfigPolicy.revision4Fingerprint,
                )
            },
            rawConfigBytes = rawConfigBytes,
            dependencyLayerInventory = dependencyInventory,
            dependencyLayerBytes = dependencyLayerFiles.map { file -> file.second },
            acceptedInputAuthority = inputAuthority,
        )
        val compatibilitySnapshot = createSnapshot(projectId = null, inputAuthority = null)
        val inputAuthority = try {
            R2InputArchiveSchemaV1.compatibilityAuthority(compatibilitySnapshot)
        } finally {
            compatibilitySnapshot.close()
        }
        return createSnapshot(acceptedProjectId, inputAuthority)
    }

    private fun packageMetadataSnapshot(
        dependencyRoots: List<String>,
        packageFiles: List<Pair<String, ByteArray>>,
        packageTargetFiles: List<Pair<String, ByteArray>> = emptyList(),
    ): R2ProjectSnapshot {
        val rawConfig = "{}".toByteArray(StandardCharsets.UTF_8)
        val inventory = R2PackageMetadataInventory.create(
            dependencyRoots = dependencyRoots,
            files = packageFiles.map { (path, bytes) ->
                R2PackageMetadataFileDescriptor(
                    path = path,
                    size = bytes.size.toLong(),
                    sha256 = R2Digests.sha256(bytes),
                )
            },
        )
        val targetInventory = packageTargetFiles
            .takeIf { files -> files.isNotEmpty() }
            ?.map { (path, bytes) ->
                R2PackageTargetFileDescriptor(
                    path = path,
                    size = bytes.size.toLong(),
                    sha256 = R2Digests.sha256(bytes),
                )
            }
            ?.let { files ->
                R2PackageTargetInventory.create(
                    packageMetadataInventory = inventory,
                    files = files,
                )
            }
        return R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile("/declarations/lib.d.ts", "declare interface Array<T> {}\n"),
                R2ProjectTextFile("/sources/main.ts", "export const value = 1;\n"),
            ),
            rootNames = listOf("/sources/main.ts"),
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
            rawConfigDescriptor = R2RawProjectConfigDescriptor(
                path = R2ControlledTsConfigPolicy.CONFIG_PATH,
                size = rawConfig.size.toLong(),
                sha256 = R2Digests.sha256(rawConfig),
                policyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
                policyFingerprint = R2ControlledTsConfigPolicy.revision4Fingerprint,
            ),
            rawConfigBytes = rawConfig,
            packageMetadataInventory = inventory,
            packageMetadataBytes = packageFiles.map { packageFile -> packageFile.second },
            packageTargetInventory = targetInventory,
            packageTargetBytes = packageTargetFiles.map { targetFile -> targetFile.second },
        )
    }

    private fun byteCursor(
        source: ByteArray,
        onClose: () -> Unit = {},
    ): R2ProjectFileCursor = object : R2ProjectFileCursor {
        private var offset = 0
        private var closed = false

        override val remainingBytes: Long
            get() = if (closed) 0L else (source.size - offset).toLong()

        override fun read(
            destination: ByteArray,
            destinationOffset: Int,
            byteCount: Int,
        ): Int {
            check(!closed)
            if (byteCount == 0 || offset == source.size) return 0
            val count = minOf(byteCount, source.size - offset)
            source.copyInto(destination, destinationOffset, offset, offset + count)
            offset += count
            return count
        }

        override fun close() {
            if (closed) return
            closed = true
            onClose()
        }
    }

    private data class GeneratedFile(
        val path: String,
        val byteLength: Int,
        val value: Byte,
        val declaredSha256: String = generatedSha256(byteLength, value),
    )

    private class GeneratedProjectInputSnapshot(
        private val generatedFiles: List<GeneratedFile>,
        private val maximumCursorReturnBytes: Int = Int.MAX_VALUE,
        private val stopAfterBytes: Int? = null,
        private val closeFailuresBeforeSuccess: Int = 0,
    ) : R2ProjectInputSnapshot {

        override val profileAuthority =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO
        override val fileInventory = generatedFiles.map { file ->
            R2ProjectFileMetadata(
                path = file.path,
                utf8Bytes = file.byteLength.toLong(),
                utf16CodeUnits = file.byteLength,
                sha256 = file.declaredSha256,
            )
        }
        override val filePaths = fileInventory.mapTo(LinkedHashSet()) { it.path }
        override val rootNames = fileInventory
            .map(R2ProjectFileMetadata::path)
            .filter { path -> path.startsWith("/sources/") }
        override val entryPath = rootNames.first()
        override val totalUtf8Bytes = fileInventory.sumOf(R2ProjectFileMetadata::utf8Bytes)
        override val largestFileUtf8Bytes =
            fileInventory.maxOf(R2ProjectFileMetadata::utf8Bytes)

        var maximumRequestedRead = 0
            private set
        var maximumConcurrentCursors = 0
            private set
        var closedCursorCount = 0
            private set
        var cursorCloseAttemptCount = 0
            private set
        var snapshotCloseCount = 0
            private set

        private var concurrentCursors = 0

        override fun openFile(fileIndex: Int): R2ProjectFileCursor {
            val file = generatedFiles[fileIndex]
            concurrentCursors++
            maximumConcurrentCursors = maxOf(maximumConcurrentCursors, concurrentCursors)
            return object : R2ProjectFileCursor {
                private var offset = 0
                private var closed = false
                private var remainingCloseFailures = closeFailuresBeforeSuccess

                override val remainingBytes: Long
                    get() = (file.byteLength - offset).toLong()

                override fun read(
                    destination: ByteArray,
                    destinationOffset: Int,
                    byteCount: Int,
                ): Int {
                    check(!closed)
                    maximumRequestedRead = maxOf(maximumRequestedRead, byteCount)
                    if (stopAfterBytes != null && offset >= stopAfterBytes) return 0
                    if (offset == file.byteLength) return 0
                    val count = minOf(
                        byteCount,
                        maximumCursorReturnBytes,
                        file.byteLength - offset,
                        stopAfterBytes?.minus(offset) ?: Int.MAX_VALUE,
                    )
                    destination.fill(
                        file.value,
                        destinationOffset,
                        destinationOffset + count,
                    )
                    offset += count
                    return count
                }

                override fun close() {
                    if (closed) return
                    cursorCloseAttemptCount++
                    if (remainingCloseFailures > 0) {
                        remainingCloseFailures--
                        error("Injected project cursor close failure.")
                    }
                    closed = true
                    concurrentCursors--
                    closedCursorCount++
                }
            }
        }

        override fun close() {
            snapshotCloseCount++
        }
    }

    private companion object {
        const val DEADLINE_RACE_TIMEOUT_SECONDS = 5L
        const val PACKAGE_JSON_ARCHIVE = "package.json"
        const val PACKAGE_JSON_VFS = "/package.json"
        const val DEPENDENCY_PACKAGE_JSON_ARCHIVE =
            "packages/dependency/package.json"
        const val DEPENDENCY_PACKAGE_JSON_VFS =
            "/packages/dependency/package.json"
        const val PACKAGE_DEPENDENCY_ROOT = "packages/dependency"
        const val PACKAGE_DECLARATION_TARGET_ARCHIVE =
            "$PACKAGE_DEPENDENCY_ROOT/index.d.ts"
        const val PACKAGE_SOURCE_TARGET_ARCHIVE =
            "$PACKAGE_DEPENDENCY_ROOT/index.ts"
        const val PACKAGE_TARGET_SOURCE = "/$PACKAGE_DECLARATION_TARGET_ARCHIVE"
        val INPUT_AUTHORITY_KEY_ORDER = listOf(
            "inputArchiveSchemaRevision",
            "inputQuotaRevision",
            "callerInventoryFingerprint",
            "retainedInventoryFingerprint",
            "retainedFileCount",
            "largestFileBytes",
            "declarationProfileFingerprint",
            "declarationInventoryFingerprint",
            "declarationFileCount",
            "declarationByteLength",
        )
        val INPUT_AUTHORITY_KEYS = INPUT_AUTHORITY_KEY_ORDER.toSet()
        val NORMAL_DIAGNOSTIC_KEYS = setOf(
            "category",
            "code",
            "column",
            "length",
            "line",
            "message",
            "phase",
            "sourcePath",
            "start",
        )

        fun generatedSha256(
            byteLength: Int,
            value: Byte,
        ): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(minOf(byteLength.coerceAtLeast(1), 16 * 1024)) { value }
            var remaining = byteLength
            while (remaining > 0) {
                val count = minOf(remaining, buffer.size)
                digest.update(buffer, 0, count)
                remaining -= count
            }
            return digest.digest().joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        }
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            fail("Expected the operation to fail.")
        } catch (_: IllegalArgumentException) {
            Unit
        } catch (_: IllegalStateException) {
            Unit
        } catch (_: IndexOutOfBoundsException) {
            Unit
        }
    }

}
