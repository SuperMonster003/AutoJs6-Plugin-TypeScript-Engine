package io.github.supermonster003.autojs6.plugin.typescript

import java.util.Base64
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2StableFailureCatalogFixtureTest {

    @Test
    fun fixtureFreezesExactConfigurationAndResolutionFailureCatalogs() {
        val fixture = fixture()
        assertEquals(EXACT_ROOT_KEYS, fixture.keys.toList())
        assertEquals(
            "autojs6.typescript.r2-m2-x3.configuration-resolution-failures",
            fixture.requireString("schema"),
        )
        assertEquals(1, fixture.requireInt("revision"))
        assertEquals("6.0.3", fixture.requireString("typescriptVersion"))

        val configurationFailures = fixture.requireObjectList("configurationFailures")
        assertEquals(CONFIGURATION_FAILURE_NAMES, configurationFailures.map { it.requireString("name") })
        assertEquals(EXPECTED_CONFIG_POLICY_CODES.size, configurationFailures.size)
        configurationFailures.forEachIndexed { index, failure ->
            assertEquals(EXACT_CONFIGURATION_FAILURE_KEYS, failure.keys.toList())
            assertEquals(EXPECTED_CONFIG_POLICY_CODES[index], failure.requireString("policyCode"))
            assertEquals(
                if (index < CONFIG_END_FAILURE_COUNT) "config-end" else "input-end",
                failure.requireString("boundary"),
            )
            assertEquals(
                if (index == 0) "base64" else "utf8",
                failure.requireString("sourceEncoding"),
            )
            val source = failure.requireString("source")
            if (index == 0) {
                assertEquals(
                    listOf(0xc3.toByte(), 0x28.toByte()),
                    Base64.getDecoder().decode(source).toList(),
                )
            } else {
                assertEquals(source, source.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
            }
        }
        assertEquals(EXPECTED_CONFIG_POLICY_CODES, R2ControlledTsConfigPolicy.policyCodes.toList())

        val resolutionFailures = fixture.requireObjectList("resolutionFailures")
        assertEquals(RESOLUTION_FAILURE_NAMES, resolutionFailures.map { it.requireString("name") })
        assertEquals(
            EXPECTED_RESOLUTION_POLICY_CODES,
            resolutionFailures.map { it.requireString("policyCode") },
        )
        assertEquals(R2NodeResolutionFailureCode.entries.size, resolutionFailures.size)
        resolutionFailures.forEachIndexed { index, failure ->
            assertEquals(EXACT_RESOLUTION_FAILURE_KEYS, failure.keys.toList())
            val authority = R2NodeResolutionFailureCode.entries[index]
            val policyCode = failure.requireString("policyCode")
            val diagnosticCode = failure.requireInt("diagnosticCode")
            assertEquals(EXPECTED_RESOLUTION_POLICY_CODES[index], policyCode)
            assertEquals(authority.contractValue, policyCode)
            assertEquals(900_001 + index, diagnosticCode)
            assertEquals(authority.diagnosticCode, diagnosticCode)
            assertSame(
                authority,
                R2NodeResolutionFailureCode.fromDiagnosticIdentityOrNull(
                    contractValue = policyCode,
                    diagnosticCode = diagnosticCode,
                ),
            )

            val diagnostic = R2ProjectDiagnostic(
                phase = R2ProjectDiagnosticPhase.RESOLUTION,
                category = 1,
                code = diagnosticCode,
                message = "Controlled Node resolution failed.",
                sourcePath = null,
                start = null,
                length = null,
                line = null,
                column = null,
                nodeResolution = R2NodeResolutionDiagnosticEvidence(authority),
            )
            assertSame(authority, requireNotNull(diagnostic.nodeResolution).policyCode)
            assertThrows(IllegalArgumentException::class.java) {
                diagnostic.copy(code = diagnosticCode + 1)
            }
        }
    }

    @Test
    fun catalogKeepsInternalDetailsSeparateFromCallerTerminalAndExcludedDomains() {
        val fixture = fixture()
        val callerErrorCode = fixture.requireString("callerErrorCode")
        assertEquals(TypeScriptCompilerContract.ERROR_COMPILATION_DIAGNOSTICS, callerErrorCode)

        val scope = fixture.requireObject("scope")
        assertEquals(EXACT_SCOPE_KEYS, scope.keys.toList())
        assertEquals(
            "accepted-controlled-tsconfig-parser-policy-and-root-selection",
            scope.requireString("configuration"),
        )
        assertEquals(
            "accepted-snapshot-resolver-decision",
            scope.requireString("resolution"),
        )
        assertEquals("worker-direct-oracle-only", scope.requireString("evidenceLevel"))
        assertEquals(EXCLUDED_DOMAINS, fixture.requireStringList("excludedDomains"))

        val configDetails = fixture.requireObjectList("configurationFailures")
            .map { failure -> failure.requireString("policyCode") }
        val resolutionDetails = fixture.requireObjectList("resolutionFailures")
            .map { failure -> failure.requireString("policyCode") }
        val allDetails = configDetails + resolutionDetails
        assertEquals(allDetails.size, allDetails.toSet().size)
        assertFalse(callerErrorCode in allDetails)
        assertTrue(configDetails.all { detail -> detail.startsWith("CONFIG_") })
        assertTrue(resolutionDetails.all { detail -> detail.startsWith("NODE_RESOLUTION_") })
        assertTrue(allDetails.none { detail -> detail.startsWith("ERR_AUTOJS6_TYPESCRIPT_") })
        assertTrue(EXCLUDED_CALLER_ERROR_CODES.none(allDetails::contains))

        configDetails.forEach { policyCode ->
            val outcome = R2ProjectCompilationOutcome.failed(
                code = callerErrorCode,
                message = "Controlled tsconfig was rejected.",
                elapsedMs = 0,
                configPolicyCode = policyCode,
            )
            assertEquals(callerErrorCode, outcome.errorCode)
            assertEquals(policyCode, outcome.configPolicyCode)
            assertNotEquals(outcome.errorCode, outcome.configPolicyCode)
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2ProjectCompilationOutcome.failed(
                code = callerErrorCode,
                message = "A resolution detail is not a config-policy detail.",
                elapsedMs = 0,
                configPolicyCode = resolutionDetails.first(),
            )
        }

        val excludedPolicyDetails = buildSet {
            addAll(R2PackageMetadataPolicyCode.entries.map { code -> code.contractValue })
            addAll(R2PackageTargetPolicyCode.entries.map { code -> code.contractValue })
            addAll(R2NodeModuleOutputPolicyCode.entries.map { code -> code.contractValue })
            addAll(R2GeneratedModuleMappingFailureCode.entries.map { code -> code.contractValue })
        }
        assertTrue(excludedPolicyDetails.intersect(allDetails.toSet()).isEmpty())

        val successors = fixture.requireObjectList("cleanSuccessors")
        assertEquals(CLEAN_SUCCESSORS, successors.map { successor ->
            assertEquals(EXACT_CLEAN_SUCCESSOR_KEYS, successor.keys.toList())
            successor.requireString("name") to successor.requireString("kind")
        })

        val rhino = R2CompilationProfileRegistry.PROVISIONAL_RHINO
        val nodeRevisionOne = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1
        val nodeRevisionTwo = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        listOf(rhino, nodeRevisionOne, nodeRevisionTwo).forEach { authority ->
            assertEquals(TypeScriptR2Contract.CONFIG_KIND_NONE, authority.configKind)
        }
        assertSame(
            nodeRevisionTwo,
            R2CompilationProfileRegistry.requireEndToEndAdmitted(nodeRevisionTwo),
        )
        assertSame(
            nodeRevisionTwo,
            R2CompilationProfileRegistry.requireWorkerResultAdmittedExactContractValue(
                contractValue = nodeRevisionTwo.profile.contractValue,
                profileSchemaRevision = nodeRevisionTwo.profileSchemaRevision,
                profileRevision = nodeRevisionTwo.profileRevision,
            ),
        )
    }

    private fun fixture(): Map<String, Any?> = requireNotNull(
        javaClass.getResourceAsStream("/r2-config-resolution-failures/x3-v1.json"),
    ).use { input -> R2StrictJson.parseObject(input.readBytes()) }

    private fun Map<String, Any?>.requireString(key: String): String =
        getValue(key) as String

    private fun Map<String, Any?>.requireInt(key: String): Int =
        (getValue(key) as R2StrictJson.NumberToken).source.toInt()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.requireObject(key: String): Map<String, Any?> =
        getValue(key) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.requireObjectList(key: String): List<Map<String, Any?>> =
        (getValue(key) as List<*>).map { value -> value as Map<String, Any?> }

    private fun Map<String, Any?>.requireStringList(key: String): List<String> =
        (getValue(key) as List<*>).map { value -> value as String }

    private companion object {
        const val CONFIG_END_FAILURE_COUNT = 9

        val EXACT_ROOT_KEYS = listOf(
            "schema",
            "revision",
            "typescriptVersion",
            "callerErrorCode",
            "scope",
            "excludedDomains",
            "configurationFailures",
            "resolutionFailures",
            "cleanSuccessors",
        )
        val EXACT_SCOPE_KEYS = listOf("configuration", "resolution", "evidenceLevel")
        val EXACT_CONFIGURATION_FAILURE_KEYS =
            listOf("name", "policyCode", "boundary", "sourceEncoding", "source")
        val EXACT_RESOLUTION_FAILURE_KEYS = listOf("name", "policyCode", "diagnosticCode")
        val EXACT_CLEAN_SUCCESSOR_KEYS = listOf("name", "kind")

        val EXPECTED_CONFIG_POLICY_CODES = listOf(
            TypeScriptR2Contract.CONFIG_POLICY_CODE_ENCODING_INVALID,
            TypeScriptR2Contract.CONFIG_POLICY_CODE_SYNTAX_INVALID,
            TypeScriptR2Contract.CONFIG_POLICY_CODE_DUPLICATE_KEY,
            TypeScriptR2Contract.CONFIG_POLICY_CODE_UNKNOWN_PROPERTY,
            TypeScriptR2Contract.CONFIG_POLICY_CODE_INVALID_VALUE,
            TypeScriptR2Contract.CONFIG_POLICY_CODE_UNSUPPORTED_FEATURE,
            TypeScriptR2Contract.CONFIG_POLICY_CODE_PROFILE_CONFLICT,
            TypeScriptR2Contract.CONFIG_POLICY_CODE_PATH_INVALID,
            TypeScriptR2Contract.CONFIG_POLICY_CODE_COMPLEXITY_EXCEEDED,
            TypeScriptR2Contract.CONFIG_POLICY_CODE_NO_INPUTS,
            TypeScriptR2Contract.CONFIG_POLICY_CODE_ENTRY_NOT_SELECTED,
        )
        val CONFIGURATION_FAILURE_NAMES = listOf(
            "encoding-invalid",
            "syntax-invalid",
            "duplicate-key",
            "unknown-property",
            "invalid-value",
            "unsupported-feature",
            "profile-conflict",
            "path-invalid",
            "complexity-exceeded",
            "no-inputs",
            "entry-not-selected",
        )
        val RESOLUTION_FAILURE_NAMES = listOf(
            "invalid-containing-path",
            "invalid-specifier",
            "invalid-condition",
            "ambiguous-case",
            "package-not-allowed",
            "package-metadata-missing",
            "package-metadata-invalid",
            "imports-not-defined",
            "imports-no-match",
            "exports-not-defined",
            "exports-no-match",
            "condition-no-match",
            "invalid-target",
            "target-blocked",
            "target-escape",
            "target-not-in-snapshot",
            "unsupported-source-kind",
            "cycle",
        )
        val EXPECTED_RESOLUTION_POLICY_CODES = listOf(
            "NODE_RESOLUTION_INVALID_CONTAINING_PATH",
            "NODE_RESOLUTION_INVALID_SPECIFIER",
            "NODE_RESOLUTION_INVALID_CONDITION",
            "NODE_RESOLUTION_AMBIGUOUS_CASE",
            "NODE_RESOLUTION_PACKAGE_NOT_ALLOWED",
            "NODE_RESOLUTION_PACKAGE_METADATA_MISSING",
            "NODE_RESOLUTION_PACKAGE_METADATA_INVALID",
            "NODE_RESOLUTION_IMPORTS_NOT_DEFINED",
            "NODE_RESOLUTION_IMPORTS_NO_MATCH",
            "NODE_RESOLUTION_EXPORTS_NOT_DEFINED",
            "NODE_RESOLUTION_EXPORTS_NO_MATCH",
            "NODE_RESOLUTION_CONDITION_NO_MATCH",
            "NODE_RESOLUTION_INVALID_TARGET",
            "NODE_RESOLUTION_TARGET_BLOCKED",
            "NODE_RESOLUTION_TARGET_ESCAPE",
            "NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT",
            "NODE_RESOLUTION_UNSUPPORTED_SOURCE_KIND",
            "NODE_RESOLUTION_CYCLE",
        )
        val EXCLUDED_DOMAINS = listOf(
            "archive-integrity-quota-and-output",
            "config-and-project-transport-frame-validation",
            "snapshot-and-package-ingress-validation",
            "module-output-and-ordinary-typescript-diagnostics",
            "internal-invariant-cancel-timeout-and-compiler-unavailable",
            "public-and-runtime-promotion",
        )
        val EXCLUDED_CALLER_ERROR_CODES = setOf(
            TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE,
            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            TypeScriptCompilerContract.ERROR_BUSY,
            TypeScriptCompilerContract.ERROR_CANCELLED,
            TypeScriptCompilerContract.ERROR_TIMEOUT,
            TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT,
            TypeScriptCompilerContract.ERROR_ARCHIVE_FORMAT,
            TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
            TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
        )
        val CLEAN_SUCCESSORS = listOf(
            "controlled-program-after-configuration-failures" to "controlled-program",
            "relative-resolution-after-resolution-failures" to "snapshot-resolution",
        )
    }
}
