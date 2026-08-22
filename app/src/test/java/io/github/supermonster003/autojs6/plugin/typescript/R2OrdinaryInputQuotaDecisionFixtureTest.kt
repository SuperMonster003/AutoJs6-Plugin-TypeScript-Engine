package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class R2OrdinaryInputQuotaDecisionFixtureTest {

    @Test
    @Suppress("DEPRECATION")
    fun fixtureFreezesOrdinaryQuotaAcrossPublicAndNativeAuthorities() {
        val fixture = fixture()
        assertEquals(ROOT_KEYS, fixture.keys.toList())
        assertEquals(
            "autojs6.typescript.r2-m3-d1.ordinary-input-quota-decision",
            fixture.string("schema"),
        )
        assertEquals(1, fixture.int("revision"))
        assertEquals(
            "retain-512-files-8mib-single-file-8mib-total",
            fixture.string("decision"),
        )

        val quota = fixture.objectValue("ordinaryCompilationQuota")
        assertEquals(ORDINARY_QUOTA_KEYS, quota.keys.toList())
        assertEquals(
            TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION,
            quota.int("schemaRevision"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES,
            quota.int("maximumFiles"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
            quota.long("maximumSingleFileBytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES,
            quota.long("maximumTotalBytes"),
        )
        assertEquals(
            "inclusive-when-all-other-dimensions-remain-eligible",
            quota.string("comparison"),
        )

        val ordinary = R2ProjectSnapshotLimits.ORDINARY_COMPILATION
        assertEquals(quota.int("maximumFiles"), ordinary.maximumFiles)
        assertEquals(quota.long("maximumSingleFileBytes"), ordinary.maximumSingleFileBytes)
        assertEquals(quota.long("maximumTotalBytes"), ordinary.maximumTotalBytes)
        assertSame(ordinary, R2ProjectSnapshotLimits.PROVISIONAL_WORKER)

        val boundaryCases = fixture.objectList("boundaryCases")
        assertEquals(BOUNDARY_DIMENSIONS, boundaryCases.map { boundary -> boundary.string("dimension") })
        assertEquals(3, boundaryCases.size)
        boundaryCases.forEach { boundary ->
            assertEquals(BOUNDARY_KEYS, boundary.keys.toList())
            assertTrue(boundary.boolean("atLimitAccepted"))
            assertFalse(boundary.boolean("plusOneAccepted"))
            assertEquals(boundary.long("atLimit") + 1L, boundary.long("plusOne"))
        }

        val exactFileCount = R2ProjectInputCapacity(
            fileCount = ordinary.maximumFiles,
            largestFileUtf8Bytes = 0L,
            totalUtf8Bytes = 0L,
        ).admissionAgainst(ordinary)
        assertTrue(exactFileCount.isEligible)
        assertEquals(
            R2ProjectInputCapacityDimension.FILE_COUNT,
            exactFileCount.capacity.copy(fileCount = ordinary.maximumFiles + 1)
                .admissionAgainst(ordinary).exceededDimension,
        )

        val exactSingleFile = R2ProjectInputCapacity(
            fileCount = 1,
            largestFileUtf8Bytes = ordinary.maximumSingleFileBytes,
            totalUtf8Bytes = ordinary.maximumSingleFileBytes,
        ).admissionAgainst(ordinary)
        assertTrue(exactSingleFile.isEligible)
        assertEquals(
            R2ProjectInputCapacityDimension.SINGLE_FILE_BYTES,
            exactSingleFile.capacity.copy(
                largestFileUtf8Bytes = ordinary.maximumSingleFileBytes + 1L,
                totalUtf8Bytes = ordinary.maximumSingleFileBytes + 1L,
            ).admissionAgainst(ordinary).exceededDimension,
        )

        val exactTotalBytes = R2ProjectInputCapacity(
            fileCount = 1,
            largestFileUtf8Bytes = 0L,
            totalUtf8Bytes = ordinary.maximumTotalBytes,
        ).admissionAgainst(ordinary)
        assertTrue(exactTotalBytes.isEligible)
        assertEquals(
            R2ProjectInputCapacityDimension.TOTAL_BYTES,
            exactTotalBytes.capacity.copy(totalUtf8Bytes = ordinary.maximumTotalBytes + 1L)
                .admissionAgainst(ordinary).exceededDimension,
        )
    }

    @Test
    fun profileDeclarationOccupancyDerivesCurrentCallerHeadroom() {
        val profiles = fixture().objectList("profileDeclarationOccupancy")
        assertEquals(listOf("rhino", "node"), profiles.map { profile -> profile.string("profile") })
        assertEquals(PROFILE_OCCUPANCY_KEYS, profiles.single { it.string("profile") == "rhino" }.keys.toList())
        profiles.forEach { profile ->
            assertEquals(108, profile.int("commonFiles"))
            assertEquals(3_784_758L, profile.long("commonBytes"))
            assertEquals(
                profile.int("commonFiles") + profile.int("profileFiles"),
                profile.int("occupiedFiles"),
            )
            assertEquals(
                profile.long("commonBytes") + profile.long("profileBytes"),
                profile.long("occupiedBytes"),
            )
            assertEquals(
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES -
                    profile.int("occupiedFiles"),
                profile.int("maximumCallerFilesBeforeOptionalMetadata"),
            )
            assertEquals(
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES -
                    profile.long("occupiedBytes"),
                profile.long("maximumCallerBytesBeforeOptionalMetadata"),
            )
        }
        val rhino = profiles.single { profile -> profile.string("profile") == "rhino" }
        assertEquals(1, rhino.int("profileFiles"))
        assertEquals(825L, rhino.long("profileBytes"))
        assertEquals(109, rhino.int("occupiedFiles"))
        assertEquals(3_785_583L, rhino.long("occupiedBytes"))
        assertEquals(403, rhino.int("maximumCallerFilesBeforeOptionalMetadata"))
        assertEquals(4_603_025L, rhino.long("maximumCallerBytesBeforeOptionalMetadata"))

        val node = profiles.single { profile -> profile.string("profile") == "node" }
        assertEquals(59, node.int("profileFiles"))
        assertEquals(246_572L, node.long("profileBytes"))
        assertEquals(167, node.int("occupiedFiles"))
        assertEquals(4_031_330L, node.long("occupiedBytes"))
        assertEquals(345, node.int("maximumCallerFilesBeforeOptionalMetadata"))
        assertEquals(4_357_278L, node.long("maximumCallerBytesBeforeOptionalMetadata"))

        val distributionRoot = requireNotNull(
            System.getProperty("r2.generatedDeclarationDistributionDir"),
        ) { "Generated declaration distribution directory is not configured." }
        val distribution = R2StrictJson.parseObject(
            java.io.File(distributionRoot, "distribution.json").readBytes(),
        )
        val layers = distribution.objectValue("layers")
        val generatedCommon = layers.objectValue("common")
        assertEquals(108, generatedCommon.int("fileCount"))
        assertEquals(3_784_758L, generatedCommon.long("totalBytes"))
        profiles.forEach { profile ->
            val generatedProfile = layers.objectValue(profile.string("profile"))
            assertEquals(profile.int("profileFiles"), generatedProfile.int("fileCount"))
            assertEquals(profile.long("profileBytes"), generatedProfile.long("totalBytes"))
        }
    }

    @Test
    fun countingDomainAndTestOnlyTransportCapacityRemainDisjoint() {
        val fixture = fixture()
        val countingDomain = fixture.objectValue("countingDomain")
        assertEquals(COUNTING_DOMAIN_KEYS, countingDomain.keys.toList())
        assertEquals("decoded-utf8-bytes", countingDomain.string("unit"))
        assertEquals(COUNTED_INPUTS, countingDomain.stringList("fileCountIncludes"))
        assertEquals(COUNTED_INPUTS, countingDomain.stringList("byteCountIncludes"))
        assertEquals(EXCLUDED_INPUTS, countingDomain.stringList("excludes"))

        val rawConfig = "{}\n".toByteArray()
        val rootPackage = "{\"type\":\"module\"}\n".toByteArray()
        val dependencyPackage = "{\"type\":\"module\"}\n".toByteArray()
        val packageMetadata = packageMetadataInventory(
            dependencyRoot = PACKAGE_DEPENDENCY_ROOT,
            rootPackageBytes = rootPackage,
            dependencyPackageBytes = dependencyPackage,
        )
        val packageTargetBytes = "export declare const value: number;\n".toByteArray()
        val packageTargets = R2PackageTargetInventory.create(
            packageMetadataInventory = packageMetadata,
            files = listOf(
                R2PackageTargetFileDescriptor(
                    path = PACKAGE_TARGET,
                    size = packageTargetBytes.size.toLong(),
                    sha256 = R2Digests.sha256(packageTargetBytes),
                ),
            ),
        )
        val packageTargetSnapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                R2ProjectTextFile(MAIN_SOURCE, "export {};\n"),
            ),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            limits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
            rawConfigDescriptor = rawConfigDescriptor(rawConfig),
            rawConfigBytes = rawConfig,
            packageMetadataInventory = packageMetadata,
            packageMetadataBytes = listOf(rootPackage, dependencyPackage),
            packageTargetInventory = packageTargets,
            packageTargetBytes = listOf(packageTargetBytes),
        )
        packageTargetSnapshot.use { snapshot ->
            val admission = R2ProjectSnapshot.ordinaryCompilationAdmission(snapshot)
            assertTrue(admission.isEligible)
            assertEquals(
                snapshot.fileInventory.size + 1 + packageMetadata.files.size +
                    packageTargets.files.size,
                admission.capacity.fileCount,
            )
            assertEquals(
                snapshot.totalUtf8Bytes + rawConfig.size + packageMetadata.totalBytes +
                    packageTargets.totalBytes,
                admission.capacity.totalUtf8Bytes,
            )
            assertEquals(
                maxOf(
                    snapshot.largestFileUtf8Bytes,
                    rawConfig.size.toLong(),
                    packageMetadata.files.maxOf { file -> file.size },
                    packageTargets.files.maxOf { file -> file.size },
                ),
                admission.capacity.largestFileUtf8Bytes,
            )
        }

        val nodeTargetBytes = "export declare const nodeValue: string;\n".toByteArray()
        val nodeTargets = R2NodePackageTargetV2Inventory.create(
            packageMetadataInventory = packageMetadata,
            files = listOf(
                R2NodePackageTargetV2FileDescriptor(
                    path = NODE_PACKAGE_TARGET,
                    size = nodeTargetBytes.size.toLong(),
                    sha256 = R2Digests.sha256(nodeTargetBytes),
                ),
            ),
        )
        val nodeTargetSnapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                R2ProjectTextFile(MAIN_SOURCE, "export {};\n"),
            ),
            rootNames = listOf(MAIN_SOURCE),
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            limits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
            packageMetadataInventory = packageMetadata,
            packageMetadataBytes = listOf(rootPackage, dependencyPackage),
            nodePackageTargetV2Inventory = nodeTargets,
            nodePackageTargetV2Bytes = listOf(nodeTargetBytes),
        )
        nodeTargetSnapshot.use { snapshot ->
            val admission = R2ProjectSnapshot.ordinaryCompilationAdmission(snapshot)
            assertTrue(admission.isEligible)
            assertEquals(
                snapshot.fileInventory.size + packageMetadata.files.size +
                    nodeTargets.files.size,
                admission.capacity.fileCount,
            )
            assertEquals(
                snapshot.totalUtf8Bytes + packageMetadata.totalBytes + nodeTargets.totalBytes,
                admission.capacity.totalUtf8Bytes,
            )
            assertEquals(
                maxOf(
                    snapshot.largestFileUtf8Bytes,
                    packageMetadata.files.maxOf { file -> file.size },
                    nodeTargets.files.maxOf { file -> file.size },
                ),
                admission.capacity.largestFileUtf8Bytes,
            )
        }

        val transport = fixture.objectValue("transportCeiling")
        assertEquals(TRANSPORT_KEYS, transport.keys.toList())
        assertEquals("archive-validation-and-private-staging-only", transport.string("authority"))
        assertEquals(TypeScriptCompilerContract.MAX_INPUT_FILES, transport.int("maximumArchiveFiles"))
        assertEquals(
            TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES,
            transport.long("maximumArchiveContainerBytes"),
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_INPUT_BYTES,
            transport.long("maximumArchiveExpandedBytes"),
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES,
            transport.long("maximumSingleFileBytes"),
        )
        assertFalse(transport.boolean("promotesOrdinaryCompilationQuota"))
        assertTrue(
            transport.int("maximumArchiveFiles") >
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES,
        )
        assertTrue(
            transport.long("maximumArchiveExpandedBytes") >
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES,
        )

        val testOnly = fixture.objectValue("testOnlyExpandedCapacity")
        assertEquals(TEST_ONLY_KEYS, testOnly.keys.toList())
        assertEquals(
            "same-signer-distinct-uid-test-only-evidence",
            testOnly.string("authority"),
        )
        assertTrue(testOnly.boolean("evidenceBuildRequired"))
        assertTrue(testOnly.boolean("testOnlyCallerRequired"))
        assertTrue(testOnly.boolean("sameSignerRequired"))
        assertTrue(testOnly.boolean("distinctUidRequired"))
        assertFalse(testOnly.boolean("publicRequestAllowed"))
        assertFalse(testOnly.boolean("ordinaryQuotaMutated"))
        assertEquals(
            R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE.maximumFiles,
            testOnly.int("maximumWorkerFiles"),
        )
        assertEquals(
            R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE.maximumTotalBytes,
            testOnly.long("maximumWorkerTotalBytes"),
        )
        assertTrue(
            R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE.maximumFiles >
                R2ProjectSnapshotLimits.ORDINARY_COMPILATION.maximumFiles,
        )
        assertTrue(
            R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE.maximumTotalBytes >
                R2ProjectSnapshotLimits.ORDINARY_COMPILATION.maximumTotalBytes,
        )

        val rejection = fixture.objectValue("quotaRejection")
        assertEquals(QUOTA_REJECTION_KEYS, rejection.keys.toList())
        assertEquals(TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA, rejection.string("errorCode"))
        assertEquals(0, rejection.int("diagnosticCount"))
        assertEquals(0L, rejection.long("callerOutputBytes"))
        assertFalse(rejection.boolean("workerDispatchStarted"))
        assertFalse(rejection.boolean("automaticRetryAllowed"))

        val promotion = fixture.objectValue("promotionState")
        assertEquals(PROMOTION_KEYS, promotion.keys.toList())
        assertFalse(promotion.boolean("publicCapabilityChanged"))
        assertFalse(promotion.boolean("runtimeCapabilityChanged"))
        assertFalse(promotion.boolean("nodeRevisionTwoEndToEndChanged"))
    }

    private fun rawConfigDescriptor(bytes: ByteArray) = R2RawProjectConfigDescriptor(
        path = R2ControlledTsConfigPolicy.CONFIG_PATH,
        size = bytes.size.toLong(),
        sha256 = R2Digests.sha256(bytes),
        policyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
        policyFingerprint = R2ControlledTsConfigPolicy.fingerprint,
    )

    private fun packageMetadataInventory(
        dependencyRoot: String,
        rootPackageBytes: ByteArray,
        dependencyPackageBytes: ByteArray,
    ) = R2PackageMetadataInventory.create(
        dependencyRoots = listOf(dependencyRoot),
        files = listOf(
            R2PackageMetadataFileDescriptor(
                path = PACKAGE_JSON,
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

    private fun fixture(): Map<String, Any?> = requireNotNull(
        javaClass.getResourceAsStream("/r2-input-quota/d1-v1.json"),
    ).use { input -> R2StrictJson.parseObject(input.readBytes()) }

    private fun Map<String, Any?>.string(key: String): String = getValue(key) as String
    private fun Map<String, Any?>.int(key: String): Int =
        (getValue(key) as R2StrictJson.NumberToken).source.toInt()
    private fun Map<String, Any?>.long(key: String): Long =
        (getValue(key) as R2StrictJson.NumberToken).source.toLong()
    private fun Map<String, Any?>.boolean(key: String): Boolean = getValue(key) as Boolean

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?> =
        getValue(key) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objectList(key: String): List<Map<String, Any?>> =
        (getValue(key) as List<*>).map { value -> value as Map<String, Any?> }

    private fun Map<String, Any?>.stringList(key: String): List<String> =
        (getValue(key) as List<*>).map { value -> value as String }

    private companion object {
        const val DEFAULT_LIBRARY = "/declarations/lib.d.ts"
        const val MAIN_SOURCE = "/sources/main.ts"
        const val PACKAGE_JSON = "package.json"
        const val PACKAGE_DEPENDENCY_ROOT = "packages/dependency"
        const val PACKAGE_TARGET = "$PACKAGE_DEPENDENCY_ROOT/index.d.ts"
        const val NODE_PACKAGE_TARGET = "$PACKAGE_DEPENDENCY_ROOT/index.mts"

        val ROOT_KEYS = listOf(
            "schema",
            "revision",
            "decision",
            "ordinaryCompilationQuota",
            "countingDomain",
            "profileDeclarationOccupancy",
            "boundaryCases",
            "transportCeiling",
            "testOnlyExpandedCapacity",
            "quotaRejection",
            "promotionState",
        )
        val ORDINARY_QUOTA_KEYS = listOf(
            "schemaRevision",
            "maximumFiles",
            "maximumSingleFileBytes",
            "maximumTotalBytes",
            "comparison",
        )
        val COUNTING_DOMAIN_KEYS = listOf(
            "unit",
            "fileCountIncludes",
            "byteCountIncludes",
            "excludes",
        )
        val COUNTED_INPUTS = listOf(
            "project-vfs-files-including-trusted-declarations",
            "raw-config-if-present",
            "package-metadata-files",
            "package-target-files",
            "node-package-target-v2-files",
        )
        val EXCLUDED_INPUTS = listOf(
            "request-manifest-json",
            "zip-container-bytes",
            "framing-and-base64-overhead",
            "output-archive-bytes",
        )
        val PROFILE_OCCUPANCY_KEYS = listOf(
            "profile",
            "commonFiles",
            "commonBytes",
            "profileFiles",
            "profileBytes",
            "occupiedFiles",
            "occupiedBytes",
            "maximumCallerFilesBeforeOptionalMetadata",
            "maximumCallerBytesBeforeOptionalMetadata",
        )
        val BOUNDARY_KEYS = listOf(
            "dimension",
            "atLimit",
            "atLimitAccepted",
            "plusOne",
            "plusOneAccepted",
        )
        val BOUNDARY_DIMENSIONS = listOf("file-count", "single-file-bytes", "total-bytes")
        val TRANSPORT_KEYS = listOf(
            "authority",
            "maximumArchiveFiles",
            "maximumArchiveContainerBytes",
            "maximumArchiveExpandedBytes",
            "maximumSingleFileBytes",
            "promotesOrdinaryCompilationQuota",
        )
        val TEST_ONLY_KEYS = listOf(
            "authority",
            "evidenceBuildRequired",
            "testOnlyCallerRequired",
            "sameSignerRequired",
            "distinctUidRequired",
            "publicRequestAllowed",
            "ordinaryQuotaMutated",
            "maximumWorkerFiles",
            "maximumWorkerTotalBytes",
        )
        val QUOTA_REJECTION_KEYS = listOf(
            "errorCode",
            "diagnosticCount",
            "callerOutputBytes",
            "workerDispatchStarted",
            "automaticRetryAllowed",
        )
        val PROMOTION_KEYS = listOf(
            "publicCapabilityChanged",
            "runtimeCapabilityChanged",
            "nodeRevisionTwoEndToEndChanged",
        )
    }
}
