package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class R2CompilationProfileAuthorityTest {

    @Test
    fun provisionalRhinoAuthorityOwnsExactRevisionConfigAndEffectiveOptions() {
        val authority = R2CompilationProfileRegistry.PROVISIONAL_RHINO
        val options = R2StrictJson.parseObject(
            authority.canonicalEffectiveOptionsJson.toByteArray(StandardCharsets.US_ASCII),
        )

        assertEquals(R2ProjectProfile.PROVISIONAL_RHINO, authority.profile)
        assertEquals(TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION, authority.profileSchemaRevision)
        assertEquals(1, authority.profileRevision)
        assertEquals(1, authority.configSchemaRevision)
        assertEquals("none", authority.configKind)
        assertEquals(
            setOf(
                "allowJs",
                "checkJs",
                "ignoreDeprecations",
                "incremental",
                "inlineSourceMap",
                "inlineSources",
                "module",
                "moduleResolution",
                "newLine",
                "noEmitOnError",
                "noLib",
                "outDir",
                "rootDir",
                "skipLibCheck",
                "sourceMap",
                "strict",
                "target",
                "types",
            ),
            options.keys,
        )
        assertEquals(false, options.getValue("allowJs"))
        assertEquals(false, options.getValue("checkJs"))
        assertEquals("6.0", options.getValue("ignoreDeprecations"))
        assertEquals(false, options.getValue("incremental"))
        assertEquals(false, options.getValue("inlineSourceMap"))
        assertEquals(false, options.getValue("inlineSources"))
        assertNumber(1, options.getValue("module"))
        assertNumber(2, options.getValue("moduleResolution"))
        assertNumber(1, options.getValue("newLine"))
        assertEquals(true, options.getValue("noEmitOnError"))
        assertEquals(false, options.getValue("noLib"))
        assertEquals("/outputs", options.getValue("outDir"))
        assertEquals("/sources", options.getValue("rootDir"))
        assertEquals(false, options.getValue("skipLibCheck"))
        assertEquals(true, options.getValue("sourceMap"))
        assertEquals(true, options.getValue("strict"))
        assertNumber(5, options.getValue("target"))
        assertEquals(emptyList<Any?>(), options.getValue("types"))

        assertEquals(
            authority.effectiveOptions,
            R2ProjectEffectiveOptions(
                allowJs = options.getValue("allowJs") as Boolean,
                checkJs = options.getValue("checkJs") as Boolean,
                ignoreDeprecations = options.getValue("ignoreDeprecations") as String,
                incremental = options.getValue("incremental") as Boolean,
                inlineSourceMap = options.getValue("inlineSourceMap") as Boolean,
                inlineSources = options.getValue("inlineSources") as Boolean,
                module = options.requireInt("module"),
                moduleResolution = options.requireInt("moduleResolution"),
                newLine = options.requireInt("newLine"),
                noEmitOnError = options.getValue("noEmitOnError") as Boolean,
                noLib = options.getValue("noLib") as Boolean,
                outDir = options.getValue("outDir") as String,
                rootDir = options.getValue("rootDir") as String,
                skipLibCheck = options.getValue("skipLibCheck") as Boolean,
                sourceMap = options.getValue("sourceMap") as Boolean,
                strict = options.getValue("strict") as Boolean,
                target = options.requireInt("target"),
                types = (options.getValue("types") as List<*>).map { it as String },
            ),
        )
    }

    @Test
    fun canonicalBytesOwnBothFingerprintsAndRegistryLookup() {
        val authority = R2CompilationProfileRegistry.PROVISIONAL_RHINO

        assertEquals(
            R2Digests.sha256(
                authority.canonicalEffectiveOptionsJson
                    .toByteArray(StandardCharsets.US_ASCII),
            ),
            authority.effectiveOptionsFingerprint,
        )
        assertEquals(
            R2Digests.sha256(
                authority.runtimeCapabilitiesCanonicalInput
                    .toByteArray(StandardCharsets.US_ASCII),
            ),
            authority.runtimeCapabilitiesFingerprint,
        )
        assertEquals(
            true,
            authority.runtimeCapabilitiesCanonicalInput.contains(
                "|defaultLibrary=/declarations/lib.es2018.d.ts|",
            ),
        )
        assertSame(
            authority,
            R2CompilationProfileRegistry.require(R2ProjectProfile.PROVISIONAL_RHINO),
        )
    }

    @Test
    fun provisionalNodeNextAuthorityIsExactConfigNoneTsOnlyAndIndependentFromRhino() {
        val rhino = R2CompilationProfileRegistry.PROVISIONAL_RHINO
        val node = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT
        val options = R2StrictJson.parseObject(
            node.canonicalEffectiveOptionsJson.toByteArray(StandardCharsets.US_ASCII),
        )

        assertEquals(R2ProjectProfile.PROVISIONAL_NODE_NEXT, node.profile)
        assertEquals(TypeScriptCompilerContract.PROFILE_NODE, node.profile.contractValue)
        assertEquals(TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION, node.profileSchemaRevision)
        assertEquals(1, node.profileRevision)
        assertEquals(R2CompilationProfileRegistry.CONFIG_SCHEMA_REVISION, node.configSchemaRevision)
        assertEquals(R2CompilationProfileRegistry.CONFIG_KIND_NONE, node.configKind)
        assertNumber(199, options.getValue("module"))
        assertNumber(99, options.getValue("moduleResolution"))
        assertNumber(5, options.getValue("target"))
        assertEquals(node.effectiveOptions, R2ProjectEffectiveOptions.PROVISIONAL_NODE_NEXT)
        assertEquals(
            R2Digests.sha256(
                node.canonicalEffectiveOptionsJson.toByteArray(StandardCharsets.US_ASCII),
            ),
            node.effectiveOptionsFingerprint,
        )
        assertEquals(
            R2Digests.sha256(
                node.runtimeCapabilitiesCanonicalInput.toByteArray(StandardCharsets.US_ASCII),
            ),
            node.runtimeCapabilitiesFingerprint,
        )
        assertEquals(true, node.runtimeCapabilitiesCanonicalInput.contains("|extensions=.ts|"))
        assertEquals(false, node.runtimeCapabilitiesCanonicalInput.contains(".mts"))
        assertEquals(false, node.runtimeCapabilitiesCanonicalInput.contains(".cts"))
        assertNotEquals(rhino.effectiveOptionsFingerprint, node.effectiveOptionsFingerprint)
        assertNotEquals(rhino.runtimeCapabilitiesFingerprint, node.runtimeCapabilitiesFingerprint)
        assertSame(node, R2CompilationProfileRegistry.require(R2ProjectProfile.PROVISIONAL_NODE_NEXT))
        assertSame(
            node,
            R2CompilationProfileRegistry.requireContractValue(TypeScriptCompilerContract.PROFILE_NODE),
        )
        assertSame(node, R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1)
    }

    @Test
    fun nodeRevisionTwoIsExactRegisteredOnlyAndCannotReplaceTheRevisionOneDefault() {
        val revisionOne = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1
        val revisionTwo = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2

        assertEquals(1, revisionOne.profileRevision)
        assertEquals(2, revisionTwo.profileRevision)
        assertSame(revisionOne, R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT)
        assertEquals(
            listOf(
                R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                revisionOne,
                revisionTwo,
            ),
            R2CompilationProfileRegistry.registeredAuthorities,
        )
        assertSame(
            revisionOne,
            R2CompilationProfileRegistry.require(R2ProjectProfile.PROVISIONAL_NODE_NEXT),
        )
        assertSame(
            revisionOne,
            R2CompilationProfileRegistry.requireContractValue(TypeScriptCompilerContract.PROFILE_NODE),
        )
        assertSame(
            revisionTwo,
            R2CompilationProfileRegistry.requireExactContractValue(
                TypeScriptCompilerContract.PROFILE_NODE,
                revisionTwo.profileSchemaRevision,
                revisionTwo.profileRevision,
            ),
        )
        assertSame(
            revisionTwo,
            R2CompilationProfileRegistry.requireExactRuntimeAuthority(
                TypeScriptCompilerContract.PROFILE_NODE,
                revisionTwo.profileSchemaRevision,
                revisionTwo.profileRevision,
                revisionTwo.runtimeCapabilitiesFingerprint,
            ),
        )
        assertEquals(revisionOne.effectiveOptions, revisionTwo.effectiveOptions)
        assertEquals(
            revisionOne.effectiveOptionsFingerprint,
            revisionTwo.effectiveOptionsFingerprint,
        )
        assertNotEquals(
            revisionOne.runtimeCapabilitiesFingerprint,
            revisionTwo.runtimeCapabilitiesFingerprint,
        )
        assertEquals(
            true,
            revisionTwo.runtimeCapabilitiesCanonicalInput.contains("|extensions=.ts,.mts,.cts|"),
        )
        assertEquals(
            true,
            revisionTwo.runtimeCapabilitiesCanonicalInput.contains("|moduleFormats=commonjs,esm|"),
        )
        assertEquals(
            true,
            revisionTwo.runtimeCapabilitiesCanonicalInput.contains(
                "|nodeModuleOutputPolicyFingerprint=${R2NodeModuleOutputPolicy.fingerprint}|",
            ),
        )
        assertEquals(true, R2CompilationProfileRegistry.containsExact(revisionTwo))

        assertSame(
            revisionTwo,
            R2CompilationProfileRegistry.requireEndToEndAdmitted(revisionTwo),
        )
        assertSame(
            revisionTwo,
            R2CompilationProfileRegistry.requireWorkerResultAdmittedExactContractValue(
                TypeScriptCompilerContract.PROFILE_NODE,
                revisionTwo.profileSchemaRevision,
                revisionTwo.profileRevision,
            ),
        )
    }

    @Test
    fun exactRegistryRejectsUnknownRevisionsAndCrossRevisionRuntimeFingerprints() {
        val revisionOne = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1
        val revisionTwo = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2

        listOf(0, 3).forEach { revision ->
            assertThrows(IllegalArgumentException::class.java) {
                R2CompilationProfileRegistry.requireExactContractValue(
                    TypeScriptCompilerContract.PROFILE_NODE,
                    TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
                    revision,
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2CompilationProfileRegistry.requireExactContractValue(
                TypeScriptCompilerContract.PROFILE_NODE,
                TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION + 1,
                revisionOne.profileRevision,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2CompilationProfileRegistry.requireExactRuntimeAuthority(
                TypeScriptCompilerContract.PROFILE_NODE,
                revisionOne.profileSchemaRevision,
                revisionOne.profileRevision,
                revisionTwo.runtimeCapabilitiesFingerprint,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2CompilationProfileRegistry.requireExactRuntimeAuthority(
                TypeScriptCompilerContract.PROFILE_NODE,
                revisionTwo.profileSchemaRevision,
                revisionTwo.profileRevision,
                revisionOne.runtimeCapabilitiesFingerprint,
            )
        }
    }

    @Test
    fun provisionalNodeNextArchiveAuthorityIsConfigNoneOnly() {
        val digest = "a".repeat(64)
        val authority = R2ProjectArchiveAuthority(
            requestId = "node-request",
            projectId = "node-project",
            entrySourceUri = "autojs://source/node/main.ts",
            profile = R2ProjectProfile.PROVISIONAL_NODE_NEXT,
            compilationMode = TypeScriptCompilerContract.MODE_DEVELOPMENT,
            compilerVersion = "6.0.3",
            compilerDistributionFingerprint = digest,
            declarationsFingerprint = digest,
            inputArchiveSchemaRevision = R2InputArchiveSchemaV1.REVISION,
            declarationProfileFingerprint = digest,
            runtimeCapabilitiesFingerprint =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT
                    .runtimeCapabilitiesFingerprint,
            archiveSha256 = digest,
            requestManifestSha256 = digest,
        )

        assertEquals(R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT, authority.profileAuthority)
        assertThrows(IllegalArgumentException::class.java) {
            authority.copy(
                rawConfigAuthority = R2RawProjectConfigAuthority(
                    size = 0,
                    sha256 = R2Digests.sha256(ByteArray(0)),
                ),
            )
        }
    }

    @Test
    fun archiveAuthorityCanCarryRegisteredRevisionTwoOnlyWhenExplicit() {
        val digest = "b".repeat(64)
        val revisionTwo = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        val authority = R2ProjectArchiveAuthority(
            requestId = "node-revision-two-request",
            projectId = "node-revision-two-project",
            entrySourceUri = "autojs://source/node/main.ts",
            profile = R2ProjectProfile.PROVISIONAL_NODE_NEXT,
            profileAuthority = revisionTwo,
            compilationMode = TypeScriptCompilerContract.MODE_DEVELOPMENT,
            compilerVersion = "6.0.3",
            compilerDistributionFingerprint = digest,
            declarationsFingerprint = digest,
            inputArchiveSchemaRevision = R2InputArchiveSchemaV1.REVISION,
            declarationProfileFingerprint = digest,
            runtimeCapabilitiesFingerprint = revisionTwo.runtimeCapabilitiesFingerprint,
            archiveSha256 = digest,
            requestManifestSha256 = digest,
        )

        assertSame(revisionTwo, authority.profileAuthority)
        assertThrows(IllegalArgumentException::class.java) {
            authority.copy(profile = R2ProjectProfile.PROVISIONAL_RHINO)
        }
    }

    private fun assertNumber(
        expected: Int,
        actual: Any?,
    ) {
        assertEquals(
            expected.toString(),
            (actual as R2StrictJson.NumberToken).source,
        )
    }

    private fun Map<String, Any?>.requireInt(key: String): Int =
        (getValue(key) as R2StrictJson.NumberToken).source.toInt()
}
