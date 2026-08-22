package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R2OutputArchiveSchemaV1FixtureTest {

    @Test
    fun fixtureFreezesOneFormalRevisionAndRejectsLegacyAliases() {
        val root = fixture()
        assertEquals(ROOT_KEYS, root.keys.toList())
        assertEquals("autojs6.typescript.r2-m3-s2.output-archive-schema", root.string("schema"))
        assertEquals(1, root.int("revision"))
        assertEquals(
            "single-formal-output-revision-one-with-input-provenance-and-dual-inventory-authority",
            root.string("decision"),
        )

        val contract = root.objectValue("publicContract")
        assertEquals(PUBLIC_CONTRACT_KEYS, contract.keys.toList())
        assertEquals(
            TypeScriptR2Contract.KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION,
            contract.string("schemaRevisionKey"),
        )
        assertEquals(TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION, contract.int("schemaRevision"))
        assertEquals(TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA, contract.string("schemaName"))
        assertEquals(
            TypeScriptR2Contract.OUTPUT_ARCHIVE_MANIFEST_PATH,
            contract.string("manifestPath"),
        )
        assertEquals(
            TypeScriptR2Contract.OUTPUT_ARCHIVE_DIAGNOSTICS_PATH,
            contract.string("diagnosticsPath"),
        )
        assertEquals(listOf(1), contract.intList("acceptedSchemaRevisions"))
        assertEquals(listOf(2, 3), contract.intList("rejectedLegacyManifestRevisions"))
        assertEquals(
            "autojs6.typescript.compilation.provisional",
            contract.string("legacySchemaName"),
        )
        assertEquals("reject-not-formal-v1-alias", contract.string("legacyRevisionPolicy"))
        assertEquals("fail-closed", contract.string("unknownRevisionPolicy"))
        assertEquals(
            "independent-zip-pfd-version-one",
            contract.string("transportVersionAuthority"),
        )
    }

    @Test
    fun manifestShapeAndOutcomeMatrixAreClosedWorld() {
        val shape = fixture().objectValue("manifestShape")
        assertEquals(MANIFEST_SHAPE_KEYS, shape.keys.toList())
        assertEquals(MANIFEST_BASE_KEYS, shape.stringList("baseTopLevelKeys"))
        assertEquals(NODE_REVISION_TWO_KEYS, shape.stringList("nodeRevisionTwoConditionalKeys"))
        assertEquals(ARTIFACT_DESCRIPTOR_KEYS, shape.stringList("artifactDescriptorKeys"))
        assertEquals("fail-closed-for-formal-consumers", shape.string("unknownFieldPolicy"))
        assertEquals("fail-closed", shape.string("missingFieldPolicy"))
        assertEquals("keys-absent", shape.string("absentNodeAuthorityEncoding"))

        val outcomes = fixture().objectList("outcomes")
        assertEquals(listOf("emitted", "diagnostics"), outcomes.map { it.string("outcome") })
        val emitted = outcomes[0]
        assertEquals(EMITTED_OUTCOME_KEYS, emitted.keys.toList())
        assertFalse(emitted.boolean("emitSkipped"))
        assertEquals(1, emitted.int("minimumModules"))
        assertEquals("one-to-one", emitted.string("moduleMapCardinality"))
        assertEquals(
            listOf("diagnostics", "javascript", "source_map"),
            emitted.stringList("artifactKinds"),
        )
        assertEquals("required", emitted.string("entryModulePath"))
        assertTrue(emitted.boolean("executable"))

        val diagnostics = outcomes[1]
        assertEquals(DIAGNOSTICS_OUTCOME_KEYS, diagnostics.keys.toList())
        assertTrue(diagnostics.boolean("emitSkipped"))
        assertEquals(1, diagnostics.int("minimumDiagnostics"))
        assertEquals(0, diagnostics.int("moduleCount"))
        assertEquals(listOf("diagnostics"), diagnostics.stringList("artifactKinds"))
        assertEquals("null", diagnostics.string("entryModulePath"))
        assertFalse(diagnostics.boolean("executable"))
        assertEquals(
            listOf("cancelled", "timed_out", "failed"),
            fixture().stringList("emptyOutputOutcomes"),
        )
    }

    @Test
    fun acceptedInputCompilerAndEffectiveOptionsProvenanceAreExplicit() {
        val provenance = fixture().objectValue("provenance")
        assertEquals(PROVENANCE_KEYS, provenance.keys.toList())
        assertEquals(COMPILER_PROVENANCE_FIELDS, provenance.stringList("compilerFields"))
        assertEquals(PROFILE_PROVENANCE_FIELDS, provenance.stringList("profileFields"))
        assertEquals(ACCEPTED_INPUT_FIELDS, provenance.stringList("acceptedInputFields"))
        assertTrue(provenance.boolean("exactSnapshotBindingRequiredBeforePackaging"))
        assertTrue(provenance.boolean("manifestDigestBoundByCompletionBundle"))

        val effective = fixture().objectValue("effectiveOptionsAuthority")
        assertEquals(EFFECTIVE_OPTIONS_KEYS, effective.keys.toList())
        assertEquals("effectiveOptions", effective.string("objectKey"))
        assertEquals("effectiveOptionsFingerprint", effective.string("fingerprintKey"))
        assertEquals(
            "exact-profile-or-controlled-config-result-authority",
            effective.string("source"),
        )
        assertEquals("sha256-over-canonical-json-bytes", effective.string("fingerprintAlgorithm"))
        assertFalse(effective.boolean("callerSupplied"))
        assertTrue(effective.boolean("s5EncodingDetailsStillPending"))
    }

    @Test
    fun dualInventoryFingerprintsMatchIndependentCanonicalOracle() {
        val module = fixture().objectValue("moduleInventory")
        val artifact = fixture().objectValue("artifactAuthority")
        assertEquals(MODULE_INVENTORY_KEYS, module.keys.toList())
        assertEquals(ARTIFACT_AUTHORITY_KEYS, artifact.keys.toList())
        assertEquals(MODULE_PREFIX, module.string("canonicalPrefix"))
        assertEquals(ARTIFACT_PREFIX, artifact.string("canonicalPrefix"))
        assertEquals(CANONICAL_RECORD, module.string("canonicalRecord"))
        assertEquals(CANONICAL_RECORD, artifact.string("canonicalRecord"))
        assertEquals("canonical-archive-path-ascending", module.string("ordering"))
        assertEquals("canonical-archive-path-ascending", artifact.string("ordering"))
        assertTrue(module.boolean("diagnosticsOnlyFingerprintUsesEmptyRecordSet"))
        assertTrue(module.boolean("explicit-uri-module-kind-and-pairingFieldsDeferredToS4"))
        assertTrue(artifact.boolean("manifestSelfReferenceAvoided"))

        val files = listOf(
            file("diagnostics.json", "diagnostics", "diagnostics", emptyList()),
            file("maps/main.js.map", "source_map", "map", listOf("sources/main.ts")),
            file("modules/main.js", "javascript", "js", listOf("sources/main.ts")),
        )
        val expectedArtifact = fingerprint(ARTIFACT_PREFIX, files)
        val expectedModule = fingerprint(
            MODULE_PREFIX,
            files.filter { file -> file.kind == R2OutputArchiveSchemaV1.KIND_JAVASCRIPT },
        )
        assertEquals(
            expectedArtifact,
            R2OutputArchiveSchemaV1.artifactInventoryFingerprint(files),
        )
        assertEquals(
            expectedModule,
            R2OutputArchiveSchemaV1.moduleInventoryFingerprint(files),
        )
        assertNotEquals(expectedArtifact, expectedModule)
    }

    @Test
    fun quotaDiagnosticsFailureAndPromotionBoundariesRemainSeparated() {
        val quota = fixture().objectValue("ordinaryQuota")
        assertEquals(ORDINARY_QUOTA_KEYS, quota.keys.toList())
        assertEquals(
            TypeScriptR2Contract.ORDINARY_COMPILATION_OUTPUT_QUOTA_REVISION,
            quota.int("outputQuotaRevision"),
        )
        assertEquals(TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES, quota.int("maximumArtifacts"))
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES,
            quota.long("maximumSingleJavaScriptBytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES,
            quota.long("maximumSingleSourceMapBytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES,
            quota.long("maximumArtifactBytes"),
        )
        assertEquals("inclusive", quota.string("comparison"))
        assertTrue(quota.boolean("manifestDiagnosticsAndZipBytesExcluded"))

        val container = fixture().objectValue("archiveContainer")
        assertEquals(ARCHIVE_CONTAINER_KEYS, container.keys.toList())
        assertEquals("zip-v1-stored", container.string("format"))
        assertTrue(container.boolean("manifestExcludedFromArtifactInventory"))
        assertTrue(container.boolean("containerSha256IsPrivateCommitEvidence"))
        assertTrue(container.boolean("streamingWriterDeferredToO1"))

        val diagnostics = fixture().objectValue("diagnosticsDependency")
        assertEquals(DIAGNOSTICS_DEPENDENCY_KEYS, diagnostics.keys.toList())
        assertEquals("autojs6.typescript.diagnostics.provisional", diagnostics.string("schema"))
        assertEquals(2, diagnostics.int("revision"))
        assertTrue(diagnostics.boolean("pathFrozenByS2"))
        assertFalse(diagnostics.boolean("documentShapeFrozenByS2"))
        assertEquals("R2-M3-S3", diagnostics.string("formalDiagnosticsOwner"))

        val failure = fixture().objectValue("failurePolicy")
        assertEquals(FAILURE_KEYS, failure.keys.toList())
        assertEquals(TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT, failure.string("artifactDigestOrSizeMismatch"))
        assertEquals(TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT, failure.string("inventoryMismatch"))
        assertFalse(failure.boolean("automaticRetryAllowed"))

        val promotion = fixture().objectValue("promotionState")
        assertEquals(PROMOTION_KEYS, promotion.keys.toList())
        promotion.values.forEach { value -> assertEquals(false, value) }
    }

    private fun fixture(): Map<String, Any?> = requireNotNull(
        javaClass.getResourceAsStream("/r2-output-archive-schema/s2-v1.json"),
    ).use { input -> R2StrictJson.parseObject(input.readBytes()) }

    private fun file(
        path: String,
        kind: String,
        text: String,
        sourcePaths: List<String>,
    ): R2OutputArchiveFile {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return R2OutputArchiveFile(path, kind, bytes.size.toLong(), sha256(bytes), sourcePaths)
    }

    private fun fingerprint(prefix: String, files: List<R2OutputArchiveFile>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(prefix.toByteArray(StandardCharsets.US_ASCII))
        files.sortedBy(R2OutputArchiveFile::path).forEach { file ->
            digest.update(file.kind.toByteArray(StandardCharsets.US_ASCII))
            digest.update(0)
            digest.update(file.path.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(file.size.toString().toByteArray(StandardCharsets.US_ASCII))
            digest.update(0)
            digest.update(file.sha256.toByteArray(StandardCharsets.US_ASCII))
            digest.update(0)
            digest.update(file.sourcePaths.size.toString().toByteArray(StandardCharsets.US_ASCII))
            file.sourcePaths.forEach { sourcePath ->
                digest.update(0)
                digest.update(sourcePath.toByteArray(StandardCharsets.UTF_8))
            }
            digest.update('\n'.code.toByte())
        }
        return digest.digest().toLowerHex()
    }

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

    private companion object {
        const val ARTIFACT_PREFIX = "autojs6.typescript.output-artifact-inventory.v1\n"
        const val MODULE_PREFIX = "autojs6.typescript.output-module-inventory.v1\n"
        const val CANONICAL_RECORD =
            "kind NUL path NUL decimal-size NUL lowercase-sha256 NUL source-count " +
                "(NUL source-path)* LF"
        val ROOT_KEYS = listOf(
            "schema",
            "revision",
            "decision",
            "publicContract",
            "manifestShape",
            "outcomes",
            "emptyOutputOutcomes",
            "provenance",
            "effectiveOptionsAuthority",
            "moduleInventory",
            "artifactAuthority",
            "ordinaryQuota",
            "archiveContainer",
            "diagnosticsDependency",
            "failurePolicy",
            "promotionState",
        )
        val PUBLIC_CONTRACT_KEYS = listOf(
            "schemaRevisionKey",
            "schemaRevision",
            "schemaName",
            "manifestPath",
            "diagnosticsPath",
            "acceptedSchemaRevisions",
            "rejectedLegacyManifestRevisions",
            "legacySchemaName",
            "legacyRevisionPolicy",
            "unknownRevisionPolicy",
            "transportVersionAuthority",
        )
        val MANIFEST_SHAPE_KEYS = listOf(
            "baseTopLevelKeys",
            "nodeRevisionTwoConditionalKeys",
            "artifactDescriptorKeys",
            "unknownFieldPolicy",
            "missingFieldPolicy",
            "absentNodeAuthorityEncoding",
        )
        val MANIFEST_BASE_KEYS = listOf(
            "schema", "revision", "outputQuotaRevision", "contractVersion", "contractHash",
            "outputArchiveTransportVersion", "requestId", "projectId", "entrySourceUri",
            "entryModulePath", "targetProfile", "profileSchemaRevision", "profileRevision",
            "configSchemaRevision", "configKind", "runtimeCapabilitySchemaRevision",
            "compilationMode", "compilerApi", "compilerPackage", "compilerVersion",
            "compilerDistributionFingerprint", "declarationsFingerprint",
            "runtimeCapabilitiesFingerprint", "inputArchiveSha256", "requestManifestSha256",
            "inputArchiveSchemaRevision", "inputQuotaRevision", "callerInventoryFingerprint",
            "retainedInventoryFingerprint", "declarationProfileFingerprint",
            "declarationInventoryFingerprint", "declarationFileCount", "declarationByteLength",
            "retainedFileCount", "retainedByteLength", "largestRetainedFileBytes", "outcome",
            "emitSkipped", "diagnosticCount", "emittedModuleCount", "diagnosticsPath",
            "effectiveOptions", "effectiveOptionsFingerprint", "moduleInventoryFingerprint",
            "artifactInventoryFingerprint", "files",
        )
        val NODE_REVISION_TWO_KEYS = listOf(
            "nodeModuleOutputPolicySchemaRevision", "nodeModuleOutputPolicyRevision",
            "nodeModuleOutputPolicyFingerprint", "generatedMappingSchema",
            "generatedMappingSchemaRevision", "generatedMappingPolicyRevision",
            "resolverPolicyRevision", "snapshotTargetInventoryFingerprint",
            "packageMetadataInventoryFingerprint", "packageFormatCatalogFingerprint",
            "packageTargetInventoryFingerprint", "targetUniverseFingerprint",
            "resultSnapshotEntryPath", "resultSnapshotRootNames", "generatedModuleMappings",
        )
        val ARTIFACT_DESCRIPTOR_KEYS = listOf("path", "kind", "size", "sha256", "sourcePaths")
        val EMITTED_OUTCOME_KEYS = listOf(
            "outcome", "emitSkipped", "minimumModules", "moduleMapCardinality",
            "artifactKinds", "entryModulePath", "executable",
        )
        val DIAGNOSTICS_OUTCOME_KEYS = listOf(
            "outcome", "emitSkipped", "minimumDiagnostics", "moduleCount", "artifactKinds",
            "entryModulePath", "executable",
        )
        val PROVENANCE_KEYS = listOf(
            "compilerFields", "profileFields", "acceptedInputFields",
            "exactSnapshotBindingRequiredBeforePackaging", "manifestDigestBoundByCompletionBundle",
        )
        val COMPILER_PROVENANCE_FIELDS = listOf(
            "compilerApi", "compilerPackage", "compilerVersion", "compilerDistributionFingerprint",
        )
        val PROFILE_PROVENANCE_FIELDS = listOf(
            "targetProfile", "profileSchemaRevision", "profileRevision", "configSchemaRevision",
            "configKind", "runtimeCapabilitySchemaRevision", "runtimeCapabilitiesFingerprint",
            "declarationsFingerprint",
        )
        val ACCEPTED_INPUT_FIELDS = listOf(
            "inputArchiveSchemaRevision", "inputQuotaRevision", "inputArchiveSha256",
            "requestManifestSha256", "callerInventoryFingerprint", "retainedInventoryFingerprint",
            "declarationProfileFingerprint", "declarationInventoryFingerprint",
            "declarationFileCount", "declarationByteLength", "retainedFileCount",
            "retainedByteLength", "largestRetainedFileBytes",
        )
        val EFFECTIVE_OPTIONS_KEYS = listOf(
            "objectKey", "fingerprintKey", "source", "fingerprintAlgorithm", "callerSupplied",
            "s5EncodingDetailsStillPending",
        )
        val MODULE_INVENTORY_KEYS = listOf(
            "fingerprintKey", "canonicalPrefix", "selection", "canonicalRecord", "ordering",
            "diagnosticsOnlyFingerprintUsesEmptyRecordSet",
            "explicit-uri-module-kind-and-pairingFieldsDeferredToS4",
        )
        val ARTIFACT_AUTHORITY_KEYS = listOf(
            "fingerprintKey", "canonicalPrefix", "includedKinds", "excludedKinds",
            "canonicalRecord", "ordering", "manifestSelfReferenceAvoided",
            "exactPhysicalCoverage",
        )
        val ORDINARY_QUOTA_KEYS = listOf(
            "outputQuotaRevision", "maximumArtifacts", "maximumSingleJavaScriptBytes",
            "maximumSingleSourceMapBytes", "maximumArtifactBytes", "comparison",
            "measurementPoint", "manifestDiagnosticsAndZipBytesExcluded",
        )
        val ARCHIVE_CONTAINER_KEYS = listOf(
            "format", "entryOrder", "timestamps", "compression",
            "manifestExcludedFromArtifactInventory", "containerSha256IsPrivateCommitEvidence",
            "streamingWriterDeferredToO1",
        )
        val DIAGNOSTICS_DEPENDENCY_KEYS = listOf(
            "schema", "revision", "pathFrozenByS2", "documentShapeFrozenByS2",
            "formalDiagnosticsOwner",
        )
        val FAILURE_KEYS = listOf(
            "unknownOutputSchema", "legacyOutputRevision", "artifactDigestOrSizeMismatch",
            "inventoryMismatch", "outcomeInventoryMismatch", "commitFailure",
            "automaticRetryAllowed",
        )
        val PROMOTION_KEYS = listOf(
            "compilerAvailable", "projectCompilation", "semanticDiagnostics", "sourceMapV3",
            "nodeRuntimeRequired", "nodeRevisionTwoEndToEndChanged", "deviceEvidenceRefreshed",
        )
    }
}
