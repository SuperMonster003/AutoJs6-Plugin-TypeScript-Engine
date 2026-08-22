package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2DiagnosticsSchemaV1Test {

    @Test
    fun identityCategoriesAndInclusiveQuotasAreExactRevisionOne() {
        R2DiagnosticsSchemaV1.requireSupportedIdentity(
            schema = TypeScriptR2Contract.DIAGNOSTICS_SCHEMA,
            revision = TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION,
        )
        assertThrows(IllegalArgumentException::class.java) {
            R2DiagnosticsSchemaV1.requireSupportedIdentity(
                schema = "autojs6.typescript.diagnostics.provisional",
                revision = 2,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2DiagnosticsSchemaV1.requireSupportedIdentity(
                schema = TypeScriptR2Contract.DIAGNOSTICS_SCHEMA,
                revision = 2,
            )
        }

        listOf(0, 1, 2, 3).forEach(R2DiagnosticsSchemaV1::requireCategory)
        assertThrows(IllegalArgumentException::class.java) {
            R2DiagnosticsSchemaV1.requireCategory(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2DiagnosticsSchemaV1.requireCategory(4)
        }

        R2DiagnosticsSchemaV1.requireCounts(
            diagnosticCount = TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT,
            relatedInformationCount = TypeScriptR2Contract.MAX_RELATED_INFORMATION_TOTAL,
        )
        assertThrows(IllegalArgumentException::class.java) {
            R2DiagnosticsSchemaV1.requireCounts(
                diagnosticCount = TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT + 1,
                relatedInformationCount = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2DiagnosticsSchemaV1.requireCounts(
                diagnosticCount = 0,
                relatedInformationCount =
                    TypeScriptR2Contract.MAX_RELATED_INFORMATION_TOTAL + 1,
            )
        }

        R2DiagnosticsSchemaV1.requireDocumentBytes(
            ByteArray(TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES.toInt()),
        )
        assertThrows(IllegalArgumentException::class.java) {
            R2DiagnosticsSchemaV1.requireDocumentBytes(
                ByteArray(
                    TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES.toInt() + 1,
                ),
            )
        }
    }

    @Test
    fun canonicalProjectUrisAreStableEncodedAndMatchGeneratedSourceIdentity() {
        val projectId = "diagnostics-project"
        val sourcePath = "/sources/nested/hash# percent%.ts"
        val sourceUri = R2DiagnosticsSchemaV1.sourceUri(projectId, sourcePath)

        assertEquals(
            R2GeneratedModuleMappingPolicy.originalSourceUri(projectId, sourcePath),
            sourceUri,
        )
        assertTrue(sourceUri.endsWith("/sources/nested/hash%23%20percent%25.ts"))
        val namespace = sourceUri.substringAfter("autojs://source/").substringBefore('/')
        assertEquals(
            "autojs://source/$namespace/declarations/lib.d.ts",
            R2DiagnosticsSchemaV1.sourceUri(projectId, "/declarations/lib.d.ts"),
        )
        assertEquals(
            "autojs://source/$namespace/packages/pkg/index.d.mts",
            R2DiagnosticsSchemaV1.sourceUri(projectId, "/packages/pkg/index.d.mts"),
        )
        val unicodePath = "/sources/中文目录/错误😀.ts"
        val unicodeUri = R2DiagnosticsSchemaV1.sourceUri(projectId, unicodePath)
        assertEquals(
            R2GeneratedModuleMappingPolicy.originalSourceUri(projectId, unicodePath),
            unicodeUri,
        )
        assertTrue(
            unicodeUri.endsWith(
                "/sources/%E4%B8%AD%E6%96%87%E7%9B%AE%E5%BD%95/" +
                    "%E9%94%99%E8%AF%AF%F0%9F%98%80.ts",
            ),
        )

        listOf(
            "sources/main.ts",
            "/outputs/main.js",
            "/sources/../main.ts",
            "/unknown/main.ts",
            "/sources/raw\\name.ts",
            "/sources/control\u0085name.ts",
            "/sources/bad\ud800name.ts",
        ).forEach { invalidPath ->
            assertThrows(IllegalArgumentException::class.java) {
                R2DiagnosticsSchemaV1.sourceUri(projectId, invalidPath)
            }
        }
    }
}
