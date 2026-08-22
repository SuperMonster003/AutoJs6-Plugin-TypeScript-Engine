package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R2DiagnosticsSchemaV1FixtureTest {

    @Test
    fun fixtureFreezesFormalIdentityShapesUrisRangesCategoriesAndLimits() {
        val root = loadFixture()

        assertEquals(ROOT_KEYS, root.keys.toList())
        assertEquals("autojs6.typescript.r2-m3-s3.diagnostics-schema", root.string("schema"))
        assertEquals(1, root.int("revision"))
        assertEquals(
            "formal-diagnostics-v1-with-project-scoped-uri-utf16-range-" +
                "related-information-and-bounded-document",
            root.string("decision"),
        )

        val contract = root.objectValue("publicContract")
        assertEquals(PUBLIC_CONTRACT_KEYS, contract.keys.toList())
        assertEquals(
            TypeScriptR2Contract.KEY_DIAGNOSTICS_SCHEMA_REVISION,
            contract.string("schemaRevisionKey"),
        )
        assertEquals(TypeScriptR2Contract.DIAGNOSTICS_SCHEMA, contract.string("schemaName"))
        assertEquals(
            TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION,
            contract.int("schemaRevision"),
        )
        assertEquals(
            TypeScriptR2Contract.OUTPUT_ARCHIVE_DIAGNOSTICS_PATH,
            contract.string("diagnosticsPath"),
        )
        assertEquals(listOf(1), contract.intList("acceptedSchemaRevisions"))
        assertEquals(
            listOf("autojs6.typescript.diagnostics.provisional@2"),
            contract.stringList("rejectedLegacyIdentities"),
        )
        assertEquals("fail-closed", contract.string("unknownRevisionPolicy"))

        val documentShape = root.objectValue("documentShape")
        assertEquals(DOCUMENT_SHAPE_KEYS, documentShape.keys.toList())
        assertEquals(DOCUMENT_KEYS, documentShape.stringList("topLevelKeys"))
        assertTrue(documentShape.boolean("diagnosticCountMatchesArray"))
        assertTrue(documentShape.boolean("relatedInformationCountMatchesNestedArrays"))
        assertTrue(documentShape.boolean("canonicalUtf8"))

        val primaryShape = root.objectValue("primaryDiagnosticShape")
        assertEquals(PRIMARY_SHAPE_KEYS, primaryShape.keys.toList())
        assertEquals(PRIMARY_KEYS, primaryShape.stringList("baseKeys"))
        assertEquals("nodeResolution", primaryShape.string("nodeResolutionConditionalKey"))
        assertEquals(PHASE_ORDER, primaryShape.stringList("phaseOrder"))
        assertTrue(primaryShape.boolean("uriNullable"))
        assertTrue(primaryShape.boolean("rangeNullable"))
        assertTrue(primaryShape.boolean("relatedInformationAlwaysArray"))

        val relatedShape = root.objectValue("relatedInformationShape")
        assertEquals(RELATED_SHAPE_KEYS, relatedShape.keys.toList())
        assertEquals(RELATED_KEYS, relatedShape.stringList("keys"))
        assertTrue(relatedShape.boolean("phaseInheritedFromPrimaryForPathAdmission"))
        assertTrue(relatedShape.boolean("uriNullable"))
        assertTrue(relatedShape.boolean("rangeNullable"))
        assertEquals(RELATED_KEYS, relatedShape.stringList("canonicalOrder"))

        val uriPolicy = root.objectValue("uriPolicy")
        assertEquals(URI_POLICY_KEYS, uriPolicy.keys.toList())
        assertEquals("autojs", uriPolicy.string("scheme"))
        assertEquals("source", uriPolicy.string("authority"))
        assertEquals("p-<lower-sha256(projectId-utf8)>", uriPolicy.string("namespace"))
        assertEquals(
            listOf("sources", "declarations", "packages"),
            uriPolicy.stringList("pathDomains"),
        )
        assertTrue(uriPolicy.boolean("entrySelectionIndependent"))
        assertTrue(uriPolicy.boolean("sourceDomainMatchesGeneratedMappingOriginalUri"))
        assertEquals(
            TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS,
            uriPolicy.int("maximumAsciiCharacters"),
        )

        val rangePolicy = root.objectValue("rangePolicy")
        assertEquals(RANGE_POLICY_KEYS, rangePolicy.keys.toList())
        assertEquals(RANGE_KEYS, rangePolicy.stringList("keys"))
        assertEquals(
            TypeScriptR2Contract.DIAGNOSTICS_POSITION_ENCODING,
            rangePolicy.string("positionEncoding"),
        )
        assertEquals(0, rangePolicy.int("startAndLengthBase"))
        assertEquals(TypeScriptR2Contract.DIAGNOSTICS_LINE_BASE, rangePolicy.int("lineBase"))
        assertEquals(
            TypeScriptR2Contract.DIAGNOSTICS_COLUMN_BASE,
            rangePolicy.int("columnBase"),
        )
        assertEquals("reject", rangePolicy.string("partialRangePolicy"))
        assertTrue(rangePolicy.boolean("nullUriRequiresNullRange"))
        assertTrue(rangePolicy.boolean("uriMayHaveNullRange"))

        val categories = root.objectValue("categories")
        assertEquals(CATEGORY_KEYS, categories.keys.toList())
        assertEquals(TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_WARNING, categories.int("warning"))
        assertEquals(TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR, categories.int("error"))
        assertEquals(
            TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_SUGGESTION,
            categories.int("suggestion"),
        )
        assertEquals(TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_MESSAGE, categories.int("message"))

        val limits = root.objectValue("limits")
        assertEquals(LIMIT_KEYS, limits.keys.toList())
        assertEquals(TypeScriptR2Contract.DIAGNOSTICS_QUOTA_REVISION, limits.int("quotaRevision"))
        assertEquals(TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT, limits.int("maximumDiagnostics"))
        assertEquals(
            TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES,
            limits.long("maximumDocumentUtf8Bytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_RELATED_INFORMATION_PER_DIAGNOSTIC,
            limits.int("maximumRelatedInformationPerDiagnostic"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_RELATED_INFORMATION_TOTAL,
            limits.int("maximumRelatedInformationTotal"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_DIAGNOSTIC_MESSAGE_CHARS,
            limits.int("maximumMessageUtf16Characters"),
        )
        assertTrue(limits.boolean("boundariesInclusive"))
        assertEquals(
            TypeScriptCompilerContract.MAX_DIAGNOSTICS,
            limits.int("legacyTransportMaximumDiagnostics"),
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_DIAGNOSTICS_BYTES,
            limits.long("legacyTransportMaximumDiagnosticsBytes"),
        )
        assertFalse(limits.boolean("legacyTransportIsOrdinarySchemaAuthority"))
    }

    @Test
    fun consumerFixtureUsesTheExactFormalDocumentAndCanonicalRelatedLocation() {
        val document = loadFixture().objectValue("consumerFixture")
        assertEquals(DOCUMENT_KEYS, document.keys.toList())
        R2DiagnosticsSchemaV1.requireSupportedIdentity(
            schema = document.string("schema"),
            revision = document.int("revision"),
        )
        assertEquals(TypeScriptR2Contract.DIAGNOSTICS_QUOTA_REVISION, document.int("diagnosticsQuotaRevision"))
        assertEquals(TypeScriptR2Contract.DIAGNOSTICS_POSITION_ENCODING, document.string("positionEncoding"))
        assertEquals(TypeScriptR2Contract.DIAGNOSTICS_LINE_BASE, document.int("lineBase"))
        assertEquals(TypeScriptR2Contract.DIAGNOSTICS_COLUMN_BASE, document.int("columnBase"))

        val diagnostics = document.objectList("diagnostics")
        assertEquals(document.int("diagnosticCount"), diagnostics.size)
        val diagnostic = diagnostics.single()
        assertEquals(PRIMARY_KEYS, diagnostic.keys.toList())
        val projectId = document.string("projectId")
        assertEquals(
            R2DiagnosticsSchemaV1.sourceUri(
                projectId,
                "/sources/nested/hash# percent%.ts",
            ),
            diagnostic.string("uri"),
        )
        assertEquals(RANGE_KEYS, diagnostic.objectValue("range").keys.toList())
        assertEquals(TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR, diagnostic.int("category"))
        assertEquals(2_322, diagnostic.int("code"))

        val related = diagnostic.objectList("relatedInformation")
        assertEquals(document.int("relatedInformationCount"), related.size)
        val information = related.single()
        assertEquals(RELATED_KEYS, information.keys.toList())
        assertEquals(
            R2DiagnosticsSchemaV1.sourceUri(projectId, "/declarations/lib.d.ts"),
            information.string("uri"),
        )
        assertEquals(RANGE_KEYS, information.objectValue("range").keys.toList())
        assertEquals(
            TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_MESSAGE,
            information.int("category"),
        )
        R2DiagnosticsSchemaV1.requireCounts(diagnostics.size, related.size)

        val promotion = loadFixture().objectValue("promotionState")
        assertEquals(PROMOTION_KEYS, promotion.keys.toList())
        assertTrue(promotion.values.all { value -> value == false })
    }

    private fun loadFixture(): Map<String, Any?> = R2StrictJson.parseObject(
        requireNotNull(javaClass.getResourceAsStream("/r2-diagnostics-schema/s3-v1.json"))
            .use { input -> input.readBytes() },
    )

    private fun Map<String, Any?>.objectValue(name: String): Map<String, Any?> =
        (getValue(name) as? Map<*, *>)
            ?.entries
            ?.associate { (key, value) -> (key as String) to value }
            ?: error("$name must be an object.")

    private fun Map<String, Any?>.objectList(name: String): List<Map<String, Any?>> =
        listValue(name).mapIndexed { index, value ->
            (value as? Map<*, *>)
                ?.entries
                ?.associate { (key, item) -> (key as String) to item }
                ?: error("$name[$index] must be an object.")
        }

    private fun Map<String, Any?>.listValue(name: String): List<Any?> =
        getValue(name) as? List<Any?> ?: error("$name must be an array.")

    private fun Map<String, Any?>.stringList(name: String): List<String> =
        listValue(name).mapIndexed { index, value ->
            value as? String ?: error("$name[$index] must be a string.")
        }

    private fun Map<String, Any?>.intList(name: String): List<Int> =
        listValue(name).mapIndexed { index, value ->
            (value as? R2StrictJson.NumberToken)?.source?.toInt()
                ?: error("$name[$index] must be an integer.")
        }

    private fun Map<String, Any?>.string(name: String): String =
        getValue(name) as? String ?: error("$name must be a string.")

    private fun Map<String, Any?>.int(name: String): Int =
        (getValue(name) as? R2StrictJson.NumberToken)?.source?.toInt()
            ?: error("$name must be an integer.")

    private fun Map<String, Any?>.long(name: String): Long =
        (getValue(name) as? R2StrictJson.NumberToken)?.source?.toLong()
            ?: error("$name must be an integer.")

    private fun Map<String, Any?>.boolean(name: String): Boolean =
        getValue(name) as? Boolean ?: error("$name must be a Boolean.")

    companion object {
        val ROOT_KEYS = listOf(
            "schema",
            "revision",
            "decision",
            "publicContract",
            "documentShape",
            "primaryDiagnosticShape",
            "relatedInformationShape",
            "uriPolicy",
            "rangePolicy",
            "categories",
            "limits",
            "consumerFixture",
            "failurePolicy",
            "promotionState",
        )
        val PUBLIC_CONTRACT_KEYS = listOf(
            "schemaRevisionKey",
            "schemaName",
            "schemaRevision",
            "diagnosticsPath",
            "acceptedSchemaRevisions",
            "rejectedLegacyIdentities",
            "unknownRevisionPolicy",
        )
        val DOCUMENT_SHAPE_KEYS = listOf(
            "topLevelKeys",
            "diagnosticCountMatchesArray",
            "relatedInformationCountMatchesNestedArrays",
            "canonicalUtf8",
        )
        val DOCUMENT_KEYS = listOf(
            "schema",
            "revision",
            "diagnosticsQuotaRevision",
            "requestId",
            "projectId",
            "targetProfile",
            "profileSchemaRevision",
            "profileRevision",
            "configSchemaRevision",
            "configKind",
            "positionEncoding",
            "lineBase",
            "columnBase",
            "diagnosticCount",
            "relatedInformationCount",
            "diagnostics",
        )
        val PRIMARY_SHAPE_KEYS = listOf(
            "baseKeys",
            "nodeResolutionConditionalKey",
            "phaseOrder",
            "uriNullable",
            "rangeNullable",
            "relatedInformationAlwaysArray",
        )
        val PRIMARY_KEYS = listOf(
            "phase",
            "uri",
            "range",
            "category",
            "code",
            "message",
            "relatedInformation",
        )
        val RELATED_SHAPE_KEYS = listOf(
            "keys",
            "phaseInheritedFromPrimaryForPathAdmission",
            "uriNullable",
            "rangeNullable",
            "canonicalOrder",
        )
        val RELATED_KEYS = listOf("uri", "range", "category", "code", "message")
        val PHASE_ORDER = listOf("options", "syntactic", "global", "resolution", "semantic", "emit")
        val URI_POLICY_KEYS = listOf(
            "scheme",
            "authority",
            "namespace",
            "pathDomains",
            "segmentEncoding",
            "entrySelectionIndependent",
            "sourceDomainMatchesGeneratedMappingOriginalUri",
            "maximumAsciiCharacters",
        )
        val RANGE_POLICY_KEYS = listOf(
            "keys",
            "positionEncoding",
            "startAndLengthBase",
            "lineBase",
            "columnBase",
            "partialRangePolicy",
            "nullUriRequiresNullRange",
            "uriMayHaveNullRange",
        )
        val RANGE_KEYS = listOf("start", "length", "line", "column")
        val CATEGORY_KEYS = listOf("warning", "error", "suggestion", "message")
        val LIMIT_KEYS = listOf(
            "quotaRevision",
            "maximumDiagnostics",
            "maximumDocumentUtf8Bytes",
            "maximumRelatedInformationPerDiagnostic",
            "maximumRelatedInformationTotal",
            "maximumMessageUtf16Characters",
            "boundariesInclusive",
            "legacyTransportMaximumDiagnostics",
            "legacyTransportMaximumDiagnosticsBytes",
            "legacyTransportIsOrdinarySchemaAuthority",
        )
        val PROMOTION_KEYS = listOf(
            "typescriptCompilerAvailable",
            "typescriptProjectCompilation",
            "typescriptSemanticDiagnostics",
            "typescriptSourceMapV3",
            "nodeRuntimeRequired",
        )
    }
}
