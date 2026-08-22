package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2InputArchiveSchemaV1FixtureTest {

    @Test
    fun fixtureFreezesFormalRevisionOneAndRejectsLegacyRevisionOverloading() {
        val fixture = fixture()
        assertEquals(ROOT_KEYS, fixture.keys.toList())
        assertEquals(
            "autojs6.typescript.r2-m3-s1.input-archive-schema",
            fixture.string("schema"),
        )
        assertEquals(1, fixture.int("revision"))
        assertEquals(
            "single-formal-revision-one-with-domain-separated-authority",
            fixture.string("decision"),
        )

        val contract = fixture.objectValue("publicContract")
        assertEquals(PUBLIC_CONTRACT_KEYS, contract.keys.toList())
        assertEquals(
            TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
            contract.string("schemaRevisionKey"),
        )
        assertEquals(
            TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
            contract.int("schemaRevision"),
        )
        assertEquals(TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA, contract.string("schemaName"))
        assertEquals(
            TypeScriptR2Contract.INPUT_ARCHIVE_MANIFEST_PATH,
            contract.string("manifestPath"),
        )
        assertEquals(
            TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
            contract.string("declarationProfileFingerprintKey"),
        )
        assertEquals(listOf(1), contract.intList("acceptedSchemaRevisions"))
        assertEquals(
            (2..7).toList(),
            contract.intList("rejectedLegacyManifestRevisions"),
        )
        assertEquals("reject-not-formal-v1-alias", contract.string("legacyRevisionPolicy"))
        assertEquals("fail-closed", contract.string("unknownRevisionPolicy"))
        assertTrue(1 !in contract.intList("rejectedLegacyManifestRevisions"))
    }

    @Test
    fun archiveAuthorityRejectsEveryLegacyAndUnknownSchemaRevision() {
        val profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO
        fun authority(revision: Int) = R2ProjectArchiveAuthority(
            requestId = "request-s1",
            projectId = "project-s1",
            entrySourceUri = "autojs://source/main.ts",
            profile = profileAuthority.profile,
            profileAuthority = profileAuthority,
            compilationMode = TypeScriptCompilerContract.MODE_DEVELOPMENT,
            compilerVersion = "6.0.3",
            compilerDistributionFingerprint = ZERO_SHA256,
            declarationsFingerprint = ZERO_SHA256,
            inputArchiveSchemaRevision = revision,
            declarationProfileFingerprint = ZERO_SHA256,
            runtimeCapabilitiesFingerprint = profileAuthority.runtimeCapabilitiesFingerprint,
            archiveSha256 = ZERO_SHA256,
            requestManifestSha256 = ZERO_SHA256,
        )

        assertEquals(
            TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
            authority(TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION)
                .inputArchiveSchemaRevision,
        )
        ((2..7).toList() + listOf(0, 8, Int.MAX_VALUE)).forEach { revision ->
            assertThrows(IllegalArgumentException::class.java) { authority(revision) }
        }
    }

    @Test
    fun manifestShapeUsesExactBaseAndConditionalKeySets() {
        val shape = fixture().objectValue("manifestShape")
        assertEquals(MANIFEST_SHAPE_KEYS, shape.keys.toList())
        assertEquals(MANIFEST_BASE_KEYS, shape.stringList("baseTopLevelKeys"))
        assertEquals(
            listOf("path", "sha256", "size"),
            shape.stringList("sourceFileDescriptorKeys"),
        )
        assertEquals(CONFIG_KEYS, shape.stringList("configKeys"))
        assertEquals(PACKAGE_METADATA_KEYS, shape.stringList("packageMetadataKeys"))
        assertEquals(PACKAGE_TARGET_KEYS, shape.stringList("packageTargetKeys"))
        assertEquals(CONDITIONAL_KEYS, shape.stringList("conditionalKeys"))
        assertEquals("key-absent", shape.string("absentConditionalEncoding"))
        assertEquals("fail-closed", shape.string("unknownFieldPolicy"))
        assertEquals("fail-closed", shape.string("missingFieldPolicy"))
        assertEquals(MANIFEST_BASE_KEYS.size, MANIFEST_BASE_KEYS.toSet().size)
        assertTrue(CONDITIONAL_KEYS.none(MANIFEST_BASE_KEYS::contains))
    }

    @Test
    fun schemaRevisionOneUnifiesAllLegacyFeatureCombinationsWithoutRevisionOverloading() {
        val matrix = fixture().objectList("conditionalMatrix")
        assertEquals(CONDITIONAL_COMBINATION_IDS, matrix.map { it.string("id") })
        assertEquals(8, matrix.size)
        matrix.forEach { row ->
            assertEquals(CONDITIONAL_MATRIX_KEYS, row.keys.toList())
            assertEquals(
                expectedConditionalKeys(row),
                row.stringList("exactConditionalKeys"),
            )
            assertTrue(row.string("profile") in setOf("rhino", "node"))
            assertTrue(row.string("configKind") in setOf("none", "tsconfig"))
            assertTrue(row.string("packageTargets") in setOf("none", "v1", "v2"))
            assertEquals(row.boolean("config"), row.string("configKind") == "tsconfig")
            assertEquals(
                row.string("packageTargets") != "none",
                "packageTargets" in row.stringList("exactConditionalKeys"),
            )
        }

        assertEquals(
            REJECTED_COMBINATIONS,
            fixture().stringList("rejectedConditionalCombinations"),
        )
        assertTrue(
            matrix.none { row ->
                row.string("profile") == "node" &&
                    row.int("profileRevision") == 2 &&
                    row.boolean("config")
            },
        )
        assertTrue(
            matrix.filter { row -> row.string("packageTargets") != "none" }
                .all { row -> row.boolean("packageMetadata") },
        )
        assertTrue(
            matrix.single { row -> row.string("packageTargets") == "v1" }
                .string("profile") == "rhino",
        )
        assertTrue(
            matrix.single { row -> row.string("packageTargets") == "v2" }
                .let { row -> row.string("profile") == "node" && row.int("profileRevision") == 2 },
        )
    }

    @Test
    fun manifestLogicalInventoryExactlyCoversCallerAndTrustedDomains() {
        val caller = fixture().objectValue("callerArchiveDomains")
        assertEquals(CALLER_ARCHIVE_KEYS, caller.keys.toList())
        assertEquals(TypeScriptR2Contract.INPUT_ARCHIVE_MANIFEST_PATH, caller.string("manifestPath"))
        assertEquals(CALLER_DOMAINS, caller.stringList("payloadDomains"))
        assertEquals("sources/", caller.string("sourceRoot"))
        assertEquals(R2ControlledTsConfigPolicy.CONFIG_PATH, caller.string("configPath"))
        assertEquals(
            R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH,
            caller.string("projectPackageMetadataPath"),
        )
        assertEquals(
            R2PackageMetadataPolicy.DEPENDENCY_ROOT_PREFIX,
            caller.string("dependencyPackageRoot"),
        )
        assertFalse(caller.boolean("callerDeclarationPathsAllowed"))
        assertFalse(caller.boolean("manifestIncludedInPayloadInventory"))
        assertEquals(
            "manifest-plus-every-declared-caller-payload-exactly-once",
            caller.string("exactPhysicalCoverage"),
        )

        val declarations = fixture().objectValue("trustedDeclarationDomain")
        assertEquals(TRUSTED_DECLARATION_KEYS, declarations.keys.toList())
        assertEquals("declaration", declarations.string("domain"))
        assertEquals("signed-plugin-local-distribution", declarations.string("ownership"))
        assertFalse(declarations.boolean("physicallyPresentInCallerArchive"))
        assertTrue(declarations.boolean("injectedAfterCallerArchiveValidation"))
        assertEquals(
            TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
            declarations.string("profileFingerprintKey"),
        )
        assertTrue(declarations.boolean("exactLocalFileInventoryRequired"))
        assertEquals(
            listOf("declarationsFingerprint", "declarationProfileFingerprint"),
            declarations.stringList("callerWireIdentityIncludes"),
        )
        assertEquals(
            listOf(
                "declarationInventoryFingerprint",
                "declarationFileCount",
                "declarationByteLength",
            ),
            declarations.stringList("locallyDerivedAcceptedAuthorityIncludes"),
        )
        assertTrue("declaration" !in caller.stringList("payloadDomains"))
    }

    @Test
    fun archiveManifestCallerAndRetainedFingerprintsAreDomainSeparated() {
        val inventory = fixture().objectValue("logicalInventory")
        assertEquals(LOGICAL_INVENTORY_KEYS, inventory.keys.toList())
        assertEquals(CALLER_DOMAINS, inventory.stringList("callerInventoryIncludes"))
        assertEquals(CALLER_EXCLUDES, inventory.stringList("callerInventoryExcludes"))
        assertEquals(listOf("declaration"), inventory.stringList("declarationInventoryIncludes"))
        assertEquals(RETAINED_DOMAINS, inventory.stringList("retainedInventoryIncludes"))
        assertEquals(CANONICAL_PREFIX, inventory.string("canonicalPrefix"))
        assertEquals(CANONICAL_DOMAIN_ORDER, inventory.stringList("canonicalDomainOrder"))
        assertEquals(
            "domain-nul-path-nul-size-nul-lower-sha256-newline",
            inventory.string("canonicalRecord"),
        )
        assertEquals("canonical-domain-order-then-canonical-path", inventory.string("ordering"))
        assertEquals("fail-closed", inventory.string("asciiCaseFoldCollisionPolicy"))

        val source = FixtureRecord("source", "/sources/main.ts", "export {};\n")
        val declaration = FixtureRecord(
            "declaration",
            "/declarations/lib.d.ts",
            "declare const value: number;\n",
        )
        val callerFingerprint = inventoryFingerprint(listOf(source))
        val declarationFingerprint = inventoryFingerprint(listOf(declaration))
        val retainedFingerprint = inventoryFingerprint(listOf(source, declaration))
        assertEquals(3, setOf(callerFingerprint, declarationFingerprint, retainedFingerprint).size)

        val manifestA = "{\"revision\":1}\n".toByteArray(StandardCharsets.UTF_8)
        val manifestB = "{\"revision\":1,\"changed\":true}\n".toByteArray(StandardCharsets.UTF_8)
        val archiveA = manifestA + source.bytes
        val archiveB = manifestB + source.bytes
        assertNotEquals(sha256(manifestA), sha256(manifestB))
        assertNotEquals(sha256(archiveA), sha256(archiveB))
        assertEquals(callerFingerprint, inventoryFingerprint(listOf(source)))
        assertNotEquals(
            callerFingerprint,
            inventoryFingerprint(listOf(source, declaration)),
        )

        val config = FixtureRecord("config", "tsconfig.json", "{}\n")
        val packageMetadata = FixtureRecord(
            "package-metadata",
            "/package.json",
            "{\"name\":\"fixture\"}\n",
        )
        val targetV1 = FixtureRecord(
            "package-target-v1",
            "/packages/fixture/index.d.ts",
            "export {};\n",
        )
        val targetV2 = targetV1.copy(domain = "package-target-v2")
        val sixDomains = listOf(
            source,
            declaration,
            config,
            packageMetadata,
            targetV1,
            targetV2,
        )
        assertEquals(
            inventoryFingerprint(sixDomains),
            inventoryFingerprint(sixDomains.reversed()),
        )
        assertEquals(
            sixDomains.size,
            sixDomains.map { record ->
                inventoryFingerprint(listOf(record))
            }.toSet().size,
        )

        val hashes = fixture().objectList("hashAuthority")
        assertEquals(HASH_NAMES, hashes.map { it.string("name") })
        hashes.forEach { hash ->
            assertEquals(HASH_AUTHORITY_KEYS, hash.keys.toList())
            assertTrue(hash.boolean("recomputed"))
        }
        assertEquals("entire-caller-zip-container", hashes[0].string("coverage"))
        assertEquals("exact-request-manifest-bytes", hashes[1].string("coverage"))
        assertEquals(
            "all-non-manifest-caller-payload-descriptors",
            hashes[2].string("coverage"),
        )
        assertEquals("caller-payload-plus-injected-declarations", hashes[5].string("coverage"))
    }

    @Test
    fun productionOwnerUsesOnePrefixAndDomainTaggedCanonicalRecords() {
        val source = FixtureRecord("source", "/sources/main.ts", "export {};\n")
        val declaration = FixtureRecord(
            "declaration",
            "/declarations/lib.d.ts",
            "declare const value: number;\n",
        )
        val productionRecords = listOf(source, declaration).map { record ->
            R2InputArchiveSchemaV1.Record(
                domain = record.domain,
                path = record.path,
                size = record.bytes.size.toLong(),
                sha256 = sha256(record.bytes),
            )
        }

        assertEquals(
            inventoryFingerprint(listOf(source, declaration)),
            R2InputArchiveSchemaV1.fingerprint(productionRecords.reversed()),
        )
        assertNotEquals(
            R2InputArchiveSchemaV1.fingerprint(listOf(productionRecords[0])),
            R2InputArchiveSchemaV1.fingerprint(listOf(productionRecords[1])),
        )

        val unicodeEntry = source.copy(path = "/sources/中文目录/入口.ts")
        val otherUnicodeEntry = source.copy(path = "/sources/中文目录/工具.ts")
        val unicodeRecord = unicodeEntry.toProductionRecord()
        val otherUnicodeRecord = otherUnicodeEntry.toProductionRecord()
        assertEquals(
            inventoryFingerprint(listOf(unicodeEntry)),
            R2InputArchiveSchemaV1.fingerprint(listOf(unicodeRecord)),
        )
        assertNotEquals(
            R2InputArchiveSchemaV1.fingerprint(listOf(unicodeRecord)),
            R2InputArchiveSchemaV1.fingerprint(listOf(otherUnicodeRecord)),
        )

        val callerFingerprint = R2InputArchiveSchemaV1.callerInventoryFingerprint(
            sources = listOf(
                R2ProjectArchiveFile(
                    path = "sources/main.ts",
                    size = source.bytes.size.toLong(),
                    sha256 = sha256(source.bytes),
                ),
            ),
            rawConfig = null,
            packageMetadata = null,
            packageTargets = null,
            nodePackageTargetsV2 = null,
        )
        assertEquals(inventoryFingerprint(listOf(source)), callerFingerprint)

        val trustedFile = R2TrustedDeclarationFile(
            archivePath = "declarations/lib.d.ts",
            fingerprintPath = "lib/lib.d.ts",
            size = declaration.bytes.size.toLong(),
            sha256 = sha256(declaration.bytes),
            text = declaration.text,
        )
        val trusted = R2TrustedDeclarations.create(
            fingerprint = R2TrustedDeclarations.fingerprint(listOf(trustedFile)),
            files = listOf(trustedFile),
        )
        val accepted = R2InputArchiveSchemaV1.acceptedAuthority(
            callerInventoryFingerprint = callerFingerprint,
            sources = listOf(
                R2ProjectArchiveFile(
                    path = "sources/main.ts",
                    size = source.bytes.size.toLong(),
                    sha256 = sha256(source.bytes),
                ),
            ),
            trustedDeclarations = trusted,
            rawConfig = null,
            packageMetadata = null,
            packageTargets = null,
            nodePackageTargetsV2 = null,
        )
        assertEquals(TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION, accepted.schemaRevision)
        assertEquals(callerFingerprint, accepted.callerInventoryFingerprint)
        assertEquals(
            inventoryFingerprint(listOf(source, declaration)),
            accepted.retainedInventoryFingerprint,
        )
        assertEquals(
            inventoryFingerprint(listOf(declaration)),
            accepted.declarationInventoryFingerprint,
        )
        assertEquals(trusted.profileFingerprint, accepted.declarationProfileFingerprint)
        assertEquals(1, accepted.declarationFileCount)
        assertEquals(declaration.bytes.size.toLong(), accepted.declarationByteLength)
        assertEquals(2, accepted.capacity.fileCount)
        assertEquals(
            source.bytes.size.toLong() + declaration.bytes.size.toLong(),
            accepted.capacity.totalUtf8Bytes,
        )
    }

    @Test
    fun legacyZipTransportCeilingDoesNotPromoteOrdinaryInputQuota() {
        val quota = fixture().objectValue("ordinaryQuota")
        assertEquals(ORDINARY_QUOTA_KEYS, quota.keys.toList())
        assertEquals(
            TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION,
            quota.int("inputQuotaRevision"),
        )
        assertEquals(TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES, quota.int("maximumFiles"))
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
            quota.long("maximumSingleFileBytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES,
            quota.long("maximumTotalBytes"),
        )
        assertEquals("inclusive", quota.string("comparison"))
        assertEquals(RETAINED_DOMAINS, quota.stringList("countedDomains"))
        assertEquals(QUOTA_EXCLUDES, quota.stringList("excludedDomains"))
        assertEquals(TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA, quota.string("callerQuotaError"))
        assertEquals(
            TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            quota.string("trustedDeclarationQuotaError"),
        )
        assertEquals("before-worker-handle-claim-and-framing", quota.string("admissionPoint"))

        val transport = fixture().objectValue("transportCeiling")
        assertEquals(TRANSPORT_KEYS, transport.keys.toList())
        assertEquals(
            "legacy-zip-validation-and-private-staging-only",
            transport.string("authority"),
        )
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
        assertEquals(R2_FILE_BACKED_MAXIMUM_FILES, transport.int("privateFileBackedMaximumFiles"))
        assertEquals(
            R2_FILE_BACKED_MAXIMUM_TOTAL_BYTES,
            transport.long("privateFileBackedMaximumTotalBytes"),
        )
        assertEquals(Int.MAX_VALUE, transport.int("testOnlyWorkerQuotaRevision"))
        assertEquals(
            "trusted-debug-or-evidence-host-only",
            transport.string("testOnlySelectionAuthority"),
        )
        assertFalse(transport.boolean("promotesFormalSchema"))
        assertFalse(transport.boolean("promotesOrdinaryQuota"))
        assertTrue(transport.int("maximumArchiveFiles") > quota.int("maximumFiles"))
        assertTrue(
            transport.long("privateFileBackedMaximumTotalBytes") > quota.long("maximumTotalBytes"),
        )
    }

    @Test
    fun failureAndPromotionStateRemainFailClosedAndDisabled() {
        val failure = fixture().objectValue("failurePolicy")
        assertEquals(FAILURE_KEYS, failure.keys.toList())
        assertEquals(
            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            failure.string("outerMissingOrWrongType"),
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            failure.string("outerUnknownSchemaOrProfile"),
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            failure.string("schemaRevisionMismatch"),
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            failure.string("archiveOrManifestHashMismatch"),
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            failure.string("payloadInventoryMismatch"),
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
            failure.string("callerDeclarationPath"),
        )
        assertEquals("fail-closed", failure.string("unknownOrMissingField"))
        assertFalse(failure.boolean("fdOwnershipTransferred"))
        assertFalse(failure.boolean("archiveStagingStarted"))
        assertFalse(failure.boolean("workerDispatchStarted"))
        assertEquals(0L, failure.long("callerOutputBytes"))
        assertFalse(failure.boolean("automaticRetryAllowed"))

        val promotion = fixture().objectValue("promotionState")
        assertEquals(PROMOTION_KEYS, promotion.keys.toList())
        promotion.values.forEach { value -> assertEquals(false, value) }
    }

    private fun expectedConditionalKeys(row: Map<String, Any?>): List<String> = buildList {
        if (row.boolean("config")) add("config")
        if (row.boolean("packageMetadata")) add("packageMetadata")
        if (row.string("packageTargets") != "none") add("packageTargets")
    }

    private fun fixture(): Map<String, Any?> = requireNotNull(
        javaClass.getResourceAsStream("/r2-input-archive-schema/s1-v1.json"),
    ).use { input -> R2StrictJson.parseObject(input.readBytes()) }

    private fun inventoryFingerprint(records: List<FixtureRecord>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(CANONICAL_PREFIX.toByteArray(StandardCharsets.US_ASCII))
        records.sortedWith(
            compareBy<FixtureRecord> { CANONICAL_DOMAIN_ORDER.indexOf(it.domain) }
                .thenBy(FixtureRecord::path),
        ).forEach { record ->
            digest.update(record.domain.toByteArray(StandardCharsets.US_ASCII))
            digest.update(0)
            digest.update(record.path.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(record.bytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
            digest.update(0)
            digest.update(sha256(record.bytes).toByteArray(StandardCharsets.US_ASCII))
            digest.update('\n'.code.toByte())
        }
        return digest.digest().toLowerHex()
    }

    private fun FixtureRecord.toProductionRecord() = R2InputArchiveSchemaV1.Record(
        domain = domain,
        path = path,
        size = bytes.size.toLong(),
        sha256 = sha256(bytes),
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toLowerHex()

    private fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

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

    private fun Map<String, Any?>.intList(key: String): List<Int> =
        (getValue(key) as List<*>).map { value ->
            (value as R2StrictJson.NumberToken).source.toInt()
        }

    private data class FixtureRecord(
        val domain: String,
        val path: String,
        val text: String,
    ) {
        val bytes: ByteArray = text.toByteArray(StandardCharsets.UTF_8)
    }

    private companion object {
        val ROOT_KEYS = listOf(
            "schema",
            "revision",
            "decision",
            "publicContract",
            "manifestShape",
            "conditionalMatrix",
            "rejectedConditionalCombinations",
            "callerArchiveDomains",
            "trustedDeclarationDomain",
            "logicalInventory",
            "hashAuthority",
            "ordinaryQuota",
            "transportCeiling",
            "failurePolicy",
            "promotionState",
        )
        val PUBLIC_CONTRACT_KEYS = listOf(
            "schemaRevisionKey",
            "schemaRevision",
            "schemaName",
            "manifestPath",
            "declarationProfileFingerprintKey",
            "acceptedSchemaRevisions",
            "rejectedLegacyManifestRevisions",
            "legacyRevisionPolicy",
            "unknownRevisionPolicy",
        )
        val MANIFEST_SHAPE_KEYS = listOf(
            "baseTopLevelKeys",
            "sourceFileDescriptorKeys",
            "configKeys",
            "packageMetadataKeys",
            "packageTargetKeys",
            "conditionalKeys",
            "absentConditionalEncoding",
            "unknownFieldPolicy",
            "missingFieldPolicy",
        )
        val MANIFEST_BASE_KEYS = listOf(
            "schema",
            "revision",
            "inputQuotaRevision",
            "contractVersion",
            "contractHash",
            "inputArchiveTransportVersion",
            "requestId",
            "projectId",
            "entrySourceUri",
            "entryPath",
            "targetProfile",
            "compilationMode",
            "profileSchemaRevision",
            "profileRevision",
            "runtimeCapabilitySchemaRevision",
            "configSchemaRevision",
            "runtimeCapabilitiesFingerprint",
            "compilerPackage",
            "compilerVersion",
            "compilerDistributionFingerprint",
            "declarationsFingerprint",
            "declarationProfileFingerprint",
            "configKind",
            "callerInventoryFingerprint",
            "rootPaths",
            "files",
        )
        val CONFIG_KEYS = listOf("path", "size", "sha256", "policyRevision", "policyFingerprint")
        val PACKAGE_METADATA_KEYS = listOf(
            "schemaRevision",
            "policyRevision",
            "policyFingerprint",
            "inventoryFingerprint",
            "dependencyRoots",
            "files",
        )
        val PACKAGE_TARGET_KEYS = listOf(
            "schemaRevision",
            "policyRevision",
            "policyFingerprint",
            "packageMetadataInventoryFingerprint",
            "inventoryFingerprint",
            "files",
        )
        val CONDITIONAL_KEYS = listOf("config", "packageMetadata", "packageTargets")
        val CONDITIONAL_MATRIX_KEYS = listOf(
            "id",
            "profile",
            "profileRevision",
            "configKind",
            "config",
            "packageMetadata",
            "packageTargets",
            "exactConditionalKeys",
        )
        val CONDITIONAL_COMBINATION_IDS = listOf(
            "rhino-r1-none",
            "rhino-r1-tsconfig",
            "rhino-r1-tsconfig-metadata",
            "rhino-r1-tsconfig-metadata-target-v1",
            "node-r1-none",
            "node-r2-none",
            "node-r2-metadata",
            "node-r2-metadata-target-v2",
        )
        val REJECTED_COMBINATIONS = listOf(
            "rhino-r1-none-with-package-metadata",
            "rhino-r1-none-with-package-target-v1",
            "rhino-r1-with-package-target-v2",
            "node-r1-with-package-metadata",
            "node-r1-with-package-target",
            "node-r2-with-tsconfig",
            "node-r2-target-without-package-metadata",
            "node-r2-with-package-target-v1",
            "mixed-package-target-v1-v2",
        )
        val CALLER_ARCHIVE_KEYS = listOf(
            "manifestPath",
            "payloadDomains",
            "sourceRoot",
            "configPath",
            "projectPackageMetadataPath",
            "dependencyPackageRoot",
            "callerDeclarationPathsAllowed",
            "manifestIncludedInPayloadInventory",
            "exactPhysicalCoverage",
        )
        val TRUSTED_DECLARATION_KEYS = listOf(
            "domain",
            "ownership",
            "physicallyPresentInCallerArchive",
            "injectedAfterCallerArchiveValidation",
            "profileFingerprintKey",
            "exactLocalFileInventoryRequired",
            "callerWireIdentityIncludes",
            "locallyDerivedAcceptedAuthorityIncludes",
        )
        const val CANONICAL_PREFIX = "autojs6.typescript.input-inventory.v1\n"
        val ZERO_SHA256 = "0".repeat(64)
        val CANONICAL_DOMAIN_ORDER = listOf(
            "source",
            "declaration",
            "config",
            "package-metadata",
            "package-target-v1",
            "package-target-v2",
        )
        val CALLER_DOMAINS = listOf(
            "source",
            "config",
            "package-metadata",
            "package-target-v1",
            "package-target-v2",
        )
        val RETAINED_DOMAINS = CANONICAL_DOMAIN_ORDER
        val CALLER_EXCLUDES = listOf(
            "request-manifest-json",
            "declaration",
            "zip-container-bytes",
        )
        val LOGICAL_INVENTORY_KEYS = listOf(
            "canonicalPrefix",
            "canonicalDomainOrder",
            "callerInventoryIncludes",
            "callerInventoryExcludes",
            "declarationInventoryIncludes",
            "retainedInventoryIncludes",
            "canonicalRecord",
            "ordering",
            "asciiCaseFoldCollisionPolicy",
        )
        val HASH_NAMES = listOf(
            "archiveSha256",
            "requestManifestSha256",
            "callerInventoryFingerprint",
            "declarationProfileFingerprint",
            "declarationInventoryFingerprint",
            "retainedInventoryFingerprint",
        )
        val HASH_AUTHORITY_KEYS = listOf("name", "coverage", "boundBy", "recomputed")
        val ORDINARY_QUOTA_KEYS = listOf(
            "inputQuotaRevision",
            "maximumFiles",
            "maximumSingleFileBytes",
            "maximumTotalBytes",
            "comparison",
            "countedDomains",
            "excludedDomains",
            "callerQuotaError",
            "trustedDeclarationQuotaError",
            "admissionPoint",
        )
        val QUOTA_EXCLUDES = listOf(
            "request-manifest-json",
            "zip-container-bytes",
            "framing-overhead",
        )
        val TRANSPORT_KEYS = listOf(
            "authority",
            "maximumArchiveFiles",
            "maximumArchiveContainerBytes",
            "maximumArchiveExpandedBytes",
            "maximumSingleFileBytes",
            "privateFileBackedMaximumFiles",
            "privateFileBackedMaximumTotalBytes",
            "testOnlyWorkerQuotaRevision",
            "testOnlySelectionAuthority",
            "promotesFormalSchema",
            "promotesOrdinaryQuota",
        )
        val FAILURE_KEYS = listOf(
            "outerMissingOrWrongType",
            "outerUnknownSchemaOrProfile",
            "schemaRevisionMismatch",
            "archiveOrManifestHashMismatch",
            "payloadInventoryMismatch",
            "callerDeclarationPath",
            "unknownOrMissingField",
            "fdOwnershipTransferred",
            "archiveStagingStarted",
            "workerDispatchStarted",
            "callerOutputBytes",
            "automaticRetryAllowed",
        )
        val PROMOTION_KEYS = listOf(
            "compilerAvailable",
            "projectCompilation",
            "semanticDiagnostics",
            "sourceMapV3",
            "nodeRuntimeRequired",
            "nodeRevisionTwoEndToEndChanged",
            "deviceEvidenceRefreshed",
        )
    }
}
