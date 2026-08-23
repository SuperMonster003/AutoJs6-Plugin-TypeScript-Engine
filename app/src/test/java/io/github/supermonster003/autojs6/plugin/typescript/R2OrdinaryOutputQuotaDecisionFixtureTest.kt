package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2OrdinaryOutputQuotaDecisionFixtureTest {

    @Test
    fun fixtureFreezesFinalPackagedArtifactQuotaAgainstPublicAuthority() {
        val fixture = fixture()
        assertEquals(ROOT_KEYS, fixture.keys.toList())
        assertEquals(
            "autojs6.typescript.s1-3.ordinary-output-quota-decision",
            fixture.string("schema"),
        )
        assertEquals(2, fixture.int("revision"))
        assertEquals(
            "expand-512-artifacts-16mib-per-artifact-64mib-packaged-payload",
            fixture.string("decision"),
        )

        val quota = fixture.objectValue("ordinaryCompilationOutputQuota")
        assertEquals(ORDINARY_QUOTA_KEYS, quota.keys.toList())
        assertEquals(
            TypeScriptR2Contract.ORDINARY_COMPILATION_OUTPUT_QUOTA_REVISION,
            quota.int("schemaRevision"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES,
            quota.int("maximumPackagedFiles"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES,
            quota.long("maximumSinglePackagedJavaScriptBytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES,
            quota.long("maximumSinglePackagedSourceMapBytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES,
            quota.long("maximumPackagedPayloadBytes"),
        )
        assertEquals(
            "inclusive-when-all-other-dimensions-remain-eligible",
            quota.string("comparison"),
        )

        val boundaries = fixture.objectList("boundaryCases")
        assertEquals(BOUNDARY_LIMITS.keys.toList(), boundaries.map { it.string("dimension") })
        boundaries.forEach { boundary ->
            assertEquals(BOUNDARY_KEYS, boundary.keys.toList())
            val limit = BOUNDARY_LIMITS.getValue(boundary.string("dimension"))
            assertEquals(limit, boundary.long("atLimit"))
            assertTrue(boundary.boolean("atLimitAccepted"))
            assertEquals(limit + 1L, boundary.long("plusOne"))
            assertFalse(boundary.boolean("plusOneAccepted"))
        }
    }

    @Test
    fun finalPackagedDomainKeepsDiagnosticsResultAndZipGuardrailsSeparate() {
        val fixture = fixture()
        val domain = fixture.objectValue("countingDomain")
        assertEquals(COUNTING_DOMAIN_KEYS, domain.keys.toList())
        assertEquals("final-packaged-artifact-utf8-bytes", domain.string("unit"))
        assertEquals(
            listOf(
                "javascript-after-source-mapping-url-rewrite",
                "source-map-v3-bytes",
            ),
            domain.stringList("includes"),
        )
        assertEquals(EXCLUDED_DOMAINS, domain.stringList("excludes"))
        assertEquals(
            "R2-M3-S2-output-archive-schema",
            domain.string("packagedArchiveQuotaOwner"),
        )
        assertEquals(
            "R2-M3-S3-diagnostics-schema",
            domain.string("packagedArchiveDiagnosticsShapeDependency"),
        )

        val guardrails = fixture.objectValue("separateGuardrails")
        assertEquals(listOf("diagnostics", "internalResultEnvelope"), guardrails.keys.toList())
        val diagnostics = guardrails.objectValue("diagnostics")
        assertEquals(DIAGNOSTICS_GUARDRAIL_KEYS, diagnostics.keys.toList())
        assertEquals(1_000, diagnostics.int("maximumDiagnostics"))
        assertEquals(512L * 1024L, diagnostics.long("maximumEncodedArrayBytes"))
        assertEquals("worker-result-json-domain", diagnostics.string("unit"))
        assertFalse(diagnostics.boolean("publicOutputQuota"))

        val result = guardrails.objectValue("internalResultEnvelope")
        assertEquals(RESULT_GUARDRAIL_KEYS, result.keys.toList())
        assertEquals(
            R2_PROJECT_MAXIMUM_RESULT_ENVELOPE_BYTES.toLong(),
            result.long("maximumDecodedTypedResultBytes"),
        )
        assertEquals("decoded-result-json-utf8-bytes", result.string("unit"))
        assertFalse(result.boolean("publicOutputQuota"))

        val legacy = fixture.objectValue("legacyTransportAndArchiveCeiling")
        assertEquals(LEGACY_KEYS, legacy.keys.toList())
        assertEquals(
            "v1-transport-zip-validation-and-schema-ceiling-only",
            legacy.string("authority"),
        )
        assertEquals(TypeScriptCompilerContract.MAX_OUTPUT_FILES, legacy.int("maximumArchiveFiles"))
        assertEquals(
            TypeScriptCompilerContract.MAX_OUTPUT_ARCHIVE_BYTES,
            legacy.long("maximumArchiveContainerBytes"),
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_OUTPUT_BYTES,
            legacy.long("maximumArchiveExpandedBytes"),
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_SINGLE_JAVASCRIPT_BYTES,
            legacy.long("maximumSingleJavaScriptBytes"),
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_SINGLE_SOURCE_MAP_BYTES,
            legacy.long("maximumSingleSourceMapBytes"),
        )
        assertEquals(TypeScriptCompilerContract.MAX_DIAGNOSTICS, legacy.int("maximumDiagnostics"))
        assertEquals(
            TypeScriptCompilerContract.MAX_DIAGNOSTICS_BYTES,
            legacy.long("maximumDiagnosticsBytes"),
        )
        assertEquals(TypeScriptCompilerContract.MAX_MANIFEST_BYTES, legacy.long("maximumManifestBytes"))
        assertFalse(legacy.boolean("promotesOrdinaryCompilationOutputQuota"))
        assertFalse(legacy.boolean("definesOrdinaryPackagedArchiveQuota"))
        assertTrue(
            legacy.long("maximumArchiveExpandedBytes") >
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES,
        )
    }

    @Test
    fun measuredWorkloadsDistinguishRawWorkerFromFinalPackagedPayload() {
        val workloads = fixture().objectList("measuredWorkloads")
        assertEquals(
            listOf(
                "b2k-historical-pre-c8-external-emit",
                "b2j-direct-natural-emit",
                "b2g-direct-natural-phase",
            ),
            workloads.map { it.string("id") },
        )
        workloads.forEach { workload ->
            assertEquals(MEASURED_WORKLOAD_KEYS, workload.keys.toList())
            assertFalse(workload.boolean("closesCurrentBinaryReleaseAcceptance"))
        }

        val b2k = workloads.first()
        assertEquals("historical-device-pre-c8-declaration-workload", b2k.string("evidenceClass"))
        assertEquals(512, b2k.int("rawWorkerFiles"))
        assertEquals(976_438L, b2k.long("rawWorkerBytes"))
        assertEquals(512, b2k.int("packagedFiles"))
        assertEquals(978_486L, b2k.long("packagedPayloadBytes"))
        assertEquals(2_048L, b2k.long("packagerRewriteDeltaBytes"))
        assertEquals(
            b2k.long("rawWorkerBytes") + b2k.long("packagerRewriteDeltaBytes"),
            b2k.long("packagedPayloadBytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES -
                b2k.long("packagedPayloadBytes"),
            b2k.long("packagedPayloadHeadroomBytes"),
        )
        assertTrue(b2k.boolean("withinOrdinaryPackagedPayloadQuota"))

        workloads.drop(1).forEach { historical ->
            assertNull(historical.nullableLong("packagedFiles"))
            assertNull(historical.nullableLong("packagedPayloadBytes"))
            assertNull(historical.nullableLong("packagerRewriteDeltaBytes"))
            assertNull(historical.nullableLong("packagedPayloadHeadroomBytes"))
            assertNull(historical["withinOrdinaryPackagedPayloadQuota"])
        }
        val b2j = workloads[1]
        assertEquals("historical-device-pre-current-egress-boundary", b2j.string("evidenceClass"))
        assertEquals(16, b2j.int("rawWorkerFiles"))
        assertEquals(965_714L, b2j.long("rawWorkerBytes"))

        val b2g = workloads[2]
        assertEquals("historical-device-provisional-workload", b2g.string("evidenceClass"))
        assertEquals(384, b2g.int("rawWorkerFiles"))
        assertEquals(562_945L, b2g.long("rawWorkerBytes"))

        val failure = fixture().objectValue("failureOwnership")
        assertEquals(FAILURE_KEYS, failure.keys.toList())
        assertEquals(
            TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            failure.string("workerOrHostQuotaFailureCode"),
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT,
            failure.string("nativeFinalPackagedQuotaFailureCode"),
        )
        assertEquals(0, failure.int("diagnosticCount"))
        assertEquals(0L, failure.long("callerOutputBytes"))
        assertFalse(failure.boolean("automaticRetryAllowed"))

        val promotion = fixture().objectValue("promotionState")
        assertEquals(PROMOTION_KEYS, promotion.keys.toList())
        assertTrue(promotion.boolean("publicCapabilityAvailable"))
        assertTrue(promotion.boolean("runtimeCapabilityAvailable"))
        assertFalse(promotion.boolean("nodeRevisionTwoEndToEndAvailable"))
        assertTrue(promotion.boolean("largeOutputDeviceEvidenceRefreshed"))
    }

    private fun fixture(): Map<String, Any?> = requireNotNull(
        javaClass.getResourceAsStream("/r2-output-quota/s1-3-v2.json"),
    ).use { input -> R2StrictJson.parseObject(input.readBytes()) }

    private fun Map<String, Any?>.string(key: String): String = getValue(key) as String
    private fun Map<String, Any?>.int(key: String): Int = long(key).toInt()
    private fun Map<String, Any?>.long(key: String): Long =
        (getValue(key) as R2StrictJson.NumberToken).source.toLong()
    private fun Map<String, Any?>.nullableLong(key: String): Long? =
        get(key)?.let { value -> (value as R2StrictJson.NumberToken).source.toLong() }
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
        val ROOT_KEYS = listOf(
            "schema",
            "revision",
            "decision",
            "ordinaryCompilationOutputQuota",
            "countingDomain",
            "boundaryCases",
            "separateGuardrails",
            "measuredWorkloads",
            "legacyTransportAndArchiveCeiling",
            "failureOwnership",
            "promotionState",
        )
        val ORDINARY_QUOTA_KEYS = listOf(
            "schemaRevision",
            "maximumPackagedFiles",
            "maximumSinglePackagedJavaScriptBytes",
            "maximumSinglePackagedSourceMapBytes",
            "maximumPackagedPayloadBytes",
            "comparison",
        )
        val COUNTING_DOMAIN_KEYS = listOf(
            "unit",
            "includes",
            "excludes",
            "packagedArchiveQuotaOwner",
            "packagedArchiveDiagnosticsShapeDependency",
        )
        val EXCLUDED_DOMAINS = listOf(
            "diagnostics-json",
            "typed-result-json-overhead",
            "result-framing-and-base64-overhead",
            "compilation-manifest-json",
            "zip-metadata-and-container-bytes",
        )
        val BOUNDARY_KEYS = listOf(
            "dimension",
            "atLimit",
            "atLimitAccepted",
            "plusOne",
            "plusOneAccepted",
        )
        val BOUNDARY_LIMITS = linkedMapOf(
            "packaged-file-count" to
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES.toLong(),
            "packaged-javascript-bytes" to
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES,
            "packaged-source-map-bytes" to
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES,
            "packaged-payload-bytes" to
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES,
        )
        val DIAGNOSTICS_GUARDRAIL_KEYS = listOf(
            "maximumDiagnostics",
            "maximumEncodedArrayBytes",
            "unit",
            "publicOutputQuota",
        )
        val RESULT_GUARDRAIL_KEYS = listOf(
            "maximumDecodedTypedResultBytes",
            "unit",
            "publicOutputQuota",
        )
        val MEASURED_WORKLOAD_KEYS = listOf(
            "id",
            "evidenceClass",
            "rawWorkerFiles",
            "rawWorkerBytes",
            "packagedFiles",
            "packagedPayloadBytes",
            "packagerRewriteDeltaBytes",
            "packagedPayloadHeadroomBytes",
            "withinOrdinaryPackagedPayloadQuota",
            "closesCurrentBinaryReleaseAcceptance",
        )
        val LEGACY_KEYS = listOf(
            "authority",
            "maximumArchiveFiles",
            "maximumArchiveContainerBytes",
            "maximumArchiveExpandedBytes",
            "maximumSingleJavaScriptBytes",
            "maximumSingleSourceMapBytes",
            "maximumDiagnostics",
            "maximumDiagnosticsBytes",
            "maximumManifestBytes",
            "promotesOrdinaryCompilationOutputQuota",
            "definesOrdinaryPackagedArchiveQuota",
        )
        val FAILURE_KEYS = listOf(
            "workerOrHostQuotaFailureCode",
            "nativeFinalPackagedQuotaFailureCode",
            "diagnosticCount",
            "callerOutputBytes",
            "automaticRetryAllowed",
        )
        val PROMOTION_KEYS = listOf(
            "publicCapabilityAvailable",
            "runtimeCapabilityAvailable",
            "nodeRevisionTwoEndToEndAvailable",
            "largeOutputDeviceEvidenceRefreshed",
        )
    }
}
