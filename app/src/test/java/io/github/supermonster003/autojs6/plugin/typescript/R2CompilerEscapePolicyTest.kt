package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2CompilerEscapePolicyTest {

    @Test
    fun checkedInFixtureExactlyMirrorsTheEscapeAuthority() {
        val bytes = requireNotNull(
            javaClass.getResourceAsStream(
                "/r2-controlled-tsconfig/escape-policy-v1.json",
            ),
        ).use { input -> input.readBytes() }
        val root = R2StrictJson.parseObject(bytes)

        assertEquals(EXACT_FIXTURE_KEYS, root.keys)
        assertEquals(R2CompilerEscapePolicy.SCHEMA, root.getValue("schema"))
        assertNumber(R2CompilerEscapePolicy.REVISION, root.getValue("revision"))
        assertEquals(R2CompilerEscapePolicy.fingerprint, root.getValue("fingerprint"))
        assertEquals(
            R2ControlledTsConfigPolicy.fingerprint,
            root.getValue("configPolicyFingerprint"),
        )
        assertEquals(
            R2CompilerEscapePolicy.INLINE_COMPILER_OPTIONS_JSON,
            root.getValue("inlineCompilerOptionsJson"),
        )
        assertEquals(
            R2CompilerEscapePolicy.escapeForbiddenCompilerOptions.sorted(),
            root.requireStringList("escapeForbiddenCompilerOptions"),
        )
        assertEquals(
            R2CompilerEscapePolicy.customTransformerOptionNames.sorted(),
            root.requireStringList("customTransformerOptionNames"),
        )
        assertEquals(
            R2CompilerEscapePolicy.forbiddenPackageMetadataKeys.sorted(),
            root.requireStringList("forbiddenPackageMetadataKeys"),
        )
        assertEquals(
            R2CompilerEscapePolicy.forbiddenPackageFileNames.sorted(),
            root.requireStringList("forbiddenPackageFileNames"),
        )
        assertEquals(
            R2CompilerEscapePolicy.forbiddenPackagePathSegments.sorted(),
            root.requireStringList("forbiddenPackagePathSegments"),
        )
        assertEquals(
            R2CompilerEscapePolicy.deferredNodeSourceSuffixes.sorted(),
            root.requireStringList("deferredNodeSourceSuffixes"),
        )
        assertEquals(
            R2CompilerEscapePolicy.FIXED_WORKER_IMPORT_SCRIPT,
            root.getValue("fixedWorkerImportScript"),
        )
        assertEquals(
            R2CompilerEscapePolicy.forbiddenWorkerNetworkGlobals.sorted(),
            root.requireStringList("forbiddenWorkerNetworkGlobals"),
        )
        assertEquals(
            R2CompilerEscapePolicy.BINDER_METADATA_MODE,
            root.getValue("binderMetadataMode"),
        )
        assertEquals(R2CompilerEscapePolicy.HOST_IO_MODE, root.getValue("hostIoMode"))
    }

    @Test
    fun canonicalInputAndFingerprintAreStableAsciiAuthorities() {
        val expectedCanonical =
            "autojs6.typescript.compiler-escape-policy.v1" +
                "|revision=1" +
                "|configPolicyFingerprint=" + R2ControlledTsConfigPolicy.fingerprint +
                "|inlineCompilerOptionsJson={}" +
                "|escapeForbidden=" +
                R2ControlledTsConfigPolicy.escapeForbiddenCompilerOptions.sorted()
                    .joinToString(",") +
                "|customTransformers=customTransformer,customTransformers,transformers" +
                "|packageMetadataKeys=scripts" +
                "|packageFiles=package.json" +
                "|packageSegments=node_modules" +
                "|deferredNodeSourceSuffixes=.cts,.mts" +
                "|fixedWorkerImportScript=typescript.js" +
                "|networkGlobals=EventSource,RTCPeerConnection,SharedWorker,WebSocket," +
                "WebTransport,XMLHttpRequest,fetch,navigator.sendBeacon" +
                "|binderMetadataMode=primitive-additive-no-executable-authority" +
                "|hostIoMode=snapshot-only-no-ts-sys"

        assertEquals(expectedCanonical, R2CompilerEscapePolicy.canonicalInput)
        assertTrue(R2CompilerEscapePolicy.canonicalInput.all { character ->
            character.code in 0x20..0x7e
        })
        assertEquals(
            R2Digests.sha256(expectedCanonical.toByteArray(StandardCharsets.US_ASCII)),
            R2CompilerEscapePolicy.fingerprint,
        )
        assertEquals(
            "849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d",
            R2ControlledTsConfigPolicy.fingerprint,
        )
        assertEquals(
            R2ControlledTsConfigPolicy.escapeForbiddenCompilerOptions,
            R2CompilerEscapePolicy.escapeForbiddenCompilerOptions,
        )
        assertEquals(10, R2CompilerEscapePolicy.escapeForbiddenCompilerOptions.size)
    }

    @Test
    fun sourcePathClassificationIsAsciiCaseFoldedAndClosedWorld() {
        mapOf(
            "sources/package.json" to R2CompilerEscapeSourcePathRejection.PACKAGE_METADATA,
            "/sources/Package.Json" to R2CompilerEscapeSourcePathRejection.PACKAGE_METADATA,
            "sources/node_modules/pkg/index.ts" to
                R2CompilerEscapeSourcePathRejection.PACKAGE_DEPENDENCY_TREE,
            "/sources/Node_Modules/pkg/index.d.ts" to
                R2CompilerEscapeSourcePathRejection.PACKAGE_DEPENDENCY_TREE,
            "/declarations/Node_Modules/pkg/index.d.ts" to
                R2CompilerEscapeSourcePathRejection.PACKAGE_DEPENDENCY_TREE,
            "/declarations/Package.Json" to
                R2CompilerEscapeSourcePathRejection.PACKAGE_METADATA,
            "sources/main.mts" to R2CompilerEscapeSourcePathRejection.DEFERRED_SOURCE_KIND,
            "/sources/main.CTS" to R2CompilerEscapeSourcePathRejection.DEFERRED_SOURCE_KIND,
            "/declarations/main.d.MTS" to
                R2CompilerEscapeSourcePathRejection.DEFERRED_SOURCE_KIND,
        ).forEach { (path, expected) ->
            assertEquals(path, expected, R2CompilerEscapePolicy.sourcePathRejection(path))
        }

        assertEquals(
            setOf(
                "package-metadata",
                "package-dependency-tree",
                "deferred-source-kind",
            ),
            R2CompilerEscapeSourcePathRejection.values().mapTo(linkedSetOf()) { rejection ->
                rejection.contractValue
            },
        )
        listOf(
            "sources/config.json",
            "/sources/inert.js",
            "sources/main.ts",
            "/sources/types.d.ts",
        ).forEach { path ->
            assertNull(path, R2CompilerEscapePolicy.sourcePathRejection(path))
        }
    }

    private fun Map<String, Any?>.requireStringList(key: String): List<String> =
        (getValue(key) as List<*>).map { value -> value as String }

    private fun assertNumber(
        expected: Int,
        actual: Any?,
    ) {
        assertEquals(expected.toString(), (actual as R2StrictJson.NumberToken).source)
    }

    private companion object {
        val EXACT_FIXTURE_KEYS = setOf(
            "schema",
            "revision",
            "fingerprint",
            "configPolicyFingerprint",
            "inlineCompilerOptionsJson",
            "escapeForbiddenCompilerOptions",
            "customTransformerOptionNames",
            "forbiddenPackageMetadataKeys",
            "forbiddenPackageFileNames",
            "forbiddenPackagePathSegments",
            "deferredNodeSourceSuffixes",
            "fixedWorkerImportScript",
            "forbiddenWorkerNetworkGlobals",
            "binderMetadataMode",
            "hostIoMode",
        )
    }
}
