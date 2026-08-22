package io.github.supermonster003.autojs6.plugin.typescript

import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Limits
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Validator
import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class R2PrivateOutputArchiveTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun emittedArchiveIsDeterministicStrictAndBoundToItsInputAuthority() {
        val privateDirectory = temporaryFolder.newFolder("emitted")
        val firstBytes: ByteArray
        val firstFingerprint: String
        R2PrivateOutputArchive.stageForTesting(
            privateDirectory = privateDirectory,
            authority = authority(),
            compilation = emittedCompilation(),
        ).use { staged ->
            assertEquals(
                TypeScriptCompilerContract.OUTCOME_EMITTED,
                staged.summary.outcome,
            )
            assertEquals(0, staged.summary.diagnosticCount)
            assertEquals(1, staged.summary.emittedModuleCount)
            assertTrue(staged.verifyUnchanged())
            assertEquals(staged.validation, staged.validateStructure())
            val contents = staged.withImmutableReader { reader ->
                ZipArchiveV1Validator.validateImmutableAndRead(
                    file = reader,
                    limits = ZipArchiveV1Limits.OUTPUT,
                )
            }
            assertEquals(
                listOf(
                    R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH,
                    R2PrivateOutputArchive.DIAGNOSTICS_PATH,
                    "maps/main.js.map",
                    "modules/main.js",
                ),
                contents.entries.keys.toList(),
            )
            val manifestBytes = requireNotNull(
                contents.entries[R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH],
            )
            assertEquals(
                staged.summary.outputManifestSha256,
                R2Digests.sha256(manifestBytes),
            )
            val manifest = R2StrictJson.parseObject(manifestBytes)
            assertEquals(MANIFEST_KEYS, manifest.keys)
            assertEquals(
                R2PrivateOutputArchive.FORMAL_MANIFEST_SCHEMA,
                manifest["schema"],
            )
            assertEquals(
                number(R2PrivateOutputArchive.FORMAL_MANIFEST_REVISION),
                manifest["revision"],
            )
            assertEquals("request-output-1", manifest["requestId"])
            assertEquals("project-output-1", manifest["projectId"])
            assertEquals(
                TypeScriptCompilerContract.OUTCOME_EMITTED,
                manifest["outcome"],
            )
            assertEquals("modules/main.js", manifest["entryModulePath"])
            assertEquals(number(1), manifest["emittedModuleCount"])
            assertEquals(
                number(R2CompilationProfileRegistry.CONFIG_SCHEMA_REVISION),
                manifest["configSchemaRevision"],
            )
            assertEquals(
                R2CompilationProfileRegistry.CONFIG_KIND_NONE,
                manifest["configKind"],
            )
            val options = manifest["effectiveOptions"] as Map<*, *>
            assertEquals(EFFECTIVE_OPTION_KEYS, options.keys)
            assertEquals(
                R2CompilationProfileRegistry.PROVISIONAL_RHINO.effectiveOptionsFingerprint,
                manifest["effectiveOptionsFingerprint"],
            )
            val files = manifest["files"] as List<*>
            assertEquals(3, files.size)
            assertEquals(
                listOf("diagnostics.json", "maps/main.js.map", "modules/main.js"),
                files.map { file -> (file as Map<*, *>)["path"] },
            )
            val diagnostics = R2StrictJson.parseObject(
                requireNotNull(
                    contents.entries[R2PrivateOutputArchive.DIAGNOSTICS_PATH],
                ),
            )
            assertEquals(DIAGNOSTICS_KEYS, diagnostics.keys)
            assertEquals(
                R2PrivateOutputArchive.FORMAL_DIAGNOSTICS_SCHEMA,
                diagnostics["schema"],
            )
            assertEquals(
                number(R2PrivateOutputArchive.FORMAL_DIAGNOSTICS_REVISION),
                diagnostics["revision"],
            )
            assertEquals(
                number(TypeScriptR2Contract.DIAGNOSTICS_QUOTA_REVISION),
                diagnostics["diagnosticsQuotaRevision"],
            )
            assertEquals("request-output-1", diagnostics["requestId"])
            assertEquals("project-output-1", diagnostics["projectId"])
            assertEquals(
                R2CompilationProfileRegistry.CONFIG_KIND_NONE,
                diagnostics["configKind"],
            )
            assertTrue((diagnostics["diagnostics"] as List<*>).isEmpty())
            assertEquals(number(0), diagnostics["diagnosticCount"])
            assertEquals(number(0), diagnostics["relatedInformationCount"])
            assertEquals(
                PACKAGED_JS_TEXT,
                R2Utf8.decode(
                    requireNotNull(contents.entries["modules/main.js"]),
                    "Test JavaScript",
                ),
            )
            firstBytes = staged.withImmutableReader { reader ->
                reader.seek(0L)
                ByteArray(reader.length().toInt()).also(reader::readFully)
            }
            firstFingerprint = staged.summary.containerSha256
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())

        R2PrivateOutputArchive.stageForTesting(
            privateDirectory = privateDirectory,
            authority = authority(),
            compilation = emittedCompilation(),
        ).use { staged ->
            val secondBytes = staged.withImmutableReader { reader ->
                reader.seek(0L)
                ByteArray(reader.length().toInt()).also(reader::readFully)
            }
            assertTrue(firstBytes.contentEquals(secondBytes))
            assertEquals(firstFingerprint, staged.summary.containerSha256)
        }
        firstBytes.fill(0)
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun blockingDiagnosticsCommitDiagnosticsOnlyArchiveWithNoModules() {
        val privateDirectory = temporaryFolder.newFolder("diagnostics")
        R2PrivateOutputArchive.stageForTesting(
            privateDirectory = privateDirectory,
            authority = authority(),
            compilation = diagnosticsCompilation(),
        ).use { staged ->
            assertEquals(
                TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
                staged.summary.outcome,
            )
            assertEquals(1, staged.summary.diagnosticCount)
            assertEquals(0, staged.summary.emittedModuleCount)
            val contents = staged.withImmutableReader { reader ->
                ZipArchiveV1Validator.validateImmutableAndRead(
                    file = reader,
                    limits = ZipArchiveV1Limits.OUTPUT,
                )
            }
            assertEquals(
                setOf(
                    R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH,
                    R2PrivateOutputArchive.DIAGNOSTICS_PATH,
                ),
                contents.entries.keys,
            )
            val manifest = R2StrictJson.parseObject(
                requireNotNull(
                    contents.entries[R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH],
                ),
            )
            assertEquals(
                TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
                manifest["outcome"],
            )
            assertEquals(null, manifest["entryModulePath"])
            assertEquals(number(0), manifest["emittedModuleCount"])
            assertEquals(true, manifest["emitSkipped"])

            val diagnostics = R2StrictJson.parseObject(
                requireNotNull(
                    contents.entries[R2PrivateOutputArchive.DIAGNOSTICS_PATH],
                ),
            )
            val diagnostic =
                (diagnostics["diagnostics"] as List<*>).single() as Map<*, *>
            assertEquals(NORMAL_DIAGNOSTIC_KEYS, diagnostic.keys)
            assertEquals("semantic", diagnostic["phase"])
            assertEquals(number(1), diagnostic["category"])
            assertEquals(number(2_322), diagnostic["code"])
            assertEquals("类型不匹配 \"value\"。", diagnostic["message"])
            assertEquals(
                R2DiagnosticsSchemaV1.sourceUri("project-output-1", MAIN_SOURCE),
                diagnostic["uri"],
            )
            val range = diagnostic["range"] as Map<*, *>
            assertEquals(RANGE_KEYS, range.keys)
            assertEquals(number(1), range["line"])
            assertTrue((diagnostic["relatedInformation"] as List<*>).isEmpty())
            assertFalse(contents.entries.keys.any { path -> path.startsWith("modules/") })
            assertFalse(contents.entries.keys.any { path -> path.startsWith("maps/") })
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun formalDiagnosticsRetainCanonicalSortedRelatedInformationAndSourceInventory() {
        val privateDirectory = temporaryFolder.newFolder("diagnostics-related-information")
        val primary = diagnosticsCompilation().diagnostics.single().copy(
            relatedInformation = listOf(
                R2ProjectRelatedInformation(
                    category = TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_MESSAGE,
                    code = 7_001,
                    message = "Source declaration.",
                    sourcePath = "/sources/types.d.ts",
                    start = 3,
                    length = 2,
                    line = 1,
                    column = 4,
                ),
                R2ProjectRelatedInformation(
                    category = TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_MESSAGE,
                    code = 6_500,
                    message = "Trusted declaration.",
                    sourcePath = "/declarations/lib.d.ts",
                    start = 7,
                    length = 4,
                    line = 3,
                    column = 2,
                ),
            ),
        )

        R2PrivateOutputArchive.stageForTesting(
            privateDirectory = privateDirectory,
            authority = authority(),
            compilation = compilation(
                diagnostics = listOf(primary),
                outputs = emptyList(),
                emitSkipped = true,
            ),
        ).use { staged ->
            val contents = staged.withImmutableReader { reader ->
                ZipArchiveV1Validator.validateImmutableAndRead(
                    file = reader,
                    limits = ZipArchiveV1Limits.OUTPUT,
                )
            }
            val diagnostics = R2StrictJson.parseObject(
                requireNotNull(contents.entries[R2PrivateOutputArchive.DIAGNOSTICS_PATH]),
            )
            assertEquals(number(1), diagnostics["diagnosticCount"])
            assertEquals(number(2), diagnostics["relatedInformationCount"])
            val diagnostic = (diagnostics["diagnostics"] as List<*>).single() as Map<*, *>
            val related = diagnostic["relatedInformation"] as List<*>
            assertEquals(2, related.size)
            val trusted = related[0] as Map<*, *>
            val source = related[1] as Map<*, *>
            assertEquals(RELATED_INFORMATION_KEYS, trusted.keys)
            assertEquals(RELATED_INFORMATION_KEYS, source.keys)
            assertEquals(
                R2DiagnosticsSchemaV1.sourceUri(
                    "project-output-1",
                    "/declarations/lib.d.ts",
                ),
                trusted["uri"],
            )
            assertEquals(number(6_500), trusted["code"])
            assertEquals(RANGE_KEYS, (trusted["range"] as Map<*, *>).keys)
            assertEquals(
                R2DiagnosticsSchemaV1.sourceUri(
                    "project-output-1",
                    "/sources/types.d.ts",
                ),
                source["uri"],
            )
            assertEquals(number(7_001), source["code"])

            val manifest = R2StrictJson.parseObject(
                requireNotNull(contents.entries[R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH]),
            )
            val diagnosticDescriptor = (manifest["files"] as List<*>)
                .map { value -> value as Map<*, *> }
                .single { descriptor ->
                    descriptor["path"] == R2PrivateOutputArchive.DIAGNOSTICS_PATH
                }
            assertEquals(
                listOf(
                    "declarations/lib.d.ts",
                    "sources/main.ts",
                    "sources/types.d.ts",
                ),
                diagnosticDescriptor["sourcePaths"],
            )
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun unifiedPreEmitPhasesProduceDeterministicDiagnosticsOnlyArchives() {
        val privateDirectory = temporaryFolder.newFolder("unified-pre-emit-diagnostics")

        unifiedPreEmitDiagnostics().forEach { expectedDiagnostic ->
            val compilation = compilation(
                diagnostics = listOf(expectedDiagnostic),
                outputs = emptyList(),
                emitSkipped = true,
            )
            assertTrue(compilation.executableModules.isEmpty())
            assertTrue(compilation.sourceMaps.isEmpty())
            var baselineBytes: ByteArray? = null
            var baselineFingerprint: String? = null

            repeat(2) { pass ->
                R2PrivateOutputArchive.stageForTesting(
                    privateDirectory = privateDirectory,
                    authority = authority(),
                    compilation = compilation,
                ).use { staged ->
                    assertEquals(
                        TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
                        staged.summary.outcome,
                    )
                    assertEquals(1, staged.summary.diagnosticCount)
                    assertEquals(0, staged.summary.emittedModuleCount)
                    val contents = staged.withImmutableReader { reader ->
                        ZipArchiveV1Validator.validateImmutableAndRead(
                            file = reader,
                            limits = ZipArchiveV1Limits.OUTPUT,
                        )
                    }
                    assertEquals(
                        listOf(
                            R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH,
                            R2PrivateOutputArchive.DIAGNOSTICS_PATH,
                        ),
                        contents.entries.keys.toList(),
                    )
                    assertFalse(
                        contents.entries.keys.any { path -> path.startsWith("modules/") },
                    )
                    assertFalse(
                        contents.entries.keys.any { path -> path.startsWith("maps/") },
                    )

                    val manifest = R2StrictJson.parseObject(
                        requireNotNull(
                            contents.entries[R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH],
                        ),
                    )
                    assertEquals(
                        TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
                        manifest["outcome"],
                    )
                    assertEquals(true, manifest["emitSkipped"])
                    assertEquals(null, manifest["entryModulePath"])
                    assertEquals(number(0), manifest["emittedModuleCount"])
                    assertEquals(
                        listOf(R2PrivateOutputArchive.DIAGNOSTICS_PATH),
                        (manifest["files"] as List<*>).map { file ->
                            (file as Map<*, *>)["path"]
                        },
                    )

                    val diagnostics = R2StrictJson.parseObject(
                        requireNotNull(contents.entries[R2PrivateOutputArchive.DIAGNOSTICS_PATH]),
                    )
                    val diagnostic =
                        (diagnostics["diagnostics"] as List<*>).single() as Map<*, *>
                    assertEquals(NORMAL_DIAGNOSTIC_KEYS, diagnostic.keys)
                    assertEquals(expectedDiagnostic.phase.protocolValue, diagnostic["phase"])
                    assertEquals(number(1), diagnostic["category"])
                    assertEquals(number(expectedDiagnostic.code), diagnostic["code"])
                    assertEquals(expectedDiagnostic.message, diagnostic["message"])
                    assertEquals(
                        expectedDiagnostic.sourcePath?.let { sourcePath ->
                            R2DiagnosticsSchemaV1.sourceUri("project-output-1", sourcePath)
                        },
                        diagnostic["uri"],
                    )
                    val range = diagnostic["range"] as? Map<*, *>
                    assertEquals(expectedDiagnostic.start == null, range == null)
                    if (range != null) {
                        assertEquals(RANGE_KEYS, range.keys)
                        assertEquals(number(requireNotNull(expectedDiagnostic.start)), range["start"])
                        assertEquals(number(requireNotNull(expectedDiagnostic.length)), range["length"])
                        assertEquals(number(requireNotNull(expectedDiagnostic.line)), range["line"])
                        assertEquals(number(requireNotNull(expectedDiagnostic.column)), range["column"])
                    }
                    assertTrue((diagnostic["relatedInformation"] as List<*>).isEmpty())

                    val archiveBytes = staged.withImmutableReader { reader ->
                        reader.seek(0L)
                        ByteArray(reader.length().toInt()).also(reader::readFully)
                    }
                    if (pass == 0) {
                        baselineBytes = archiveBytes
                        baselineFingerprint = staged.summary.containerSha256
                    } else {
                        assertTrue(requireNotNull(baselineBytes).contentEquals(archiveBytes))
                        assertEquals(baselineFingerprint, staged.summary.containerSha256)
                        archiveBytes.fill(0)
                    }
                }
                assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
            }
            requireNotNull(baselineBytes).fill(0)
        }
    }

    @Test
    fun nodeResolutionDiagnosticIsConditionallySerializedAndTargetBound() {
        val privateDirectory = temporaryFolder.newFolder("resolution-diagnostics")
        val failure = R2NodeResolutionFailureCode.EXPORTS_NO_MATCH
        val resolutionCompilation = resolutionDiagnosticsCompilation(failure)
        val resolutionAuthority = authority().copy(
            configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
            configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
        )

        R2PrivateOutputArchive.stageForTesting(
            privateDirectory = privateDirectory,
            authority = resolutionAuthority,
            compilation = resolutionCompilation,
            acceptedRootPaths = setOf(MAIN_SOURCE),
            acceptedSnapshotPaths = setOf(MAIN_SOURCE, PACKAGE_TARGET_SOURCE),
        ).use { staged ->
            assertEquals(TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS, staged.summary.outcome)
            assertEquals(0, staged.summary.emittedModuleCount)
            val contents = staged.withImmutableReader { reader ->
                ZipArchiveV1Validator.validateImmutableAndRead(
                    file = reader,
                    limits = ZipArchiveV1Limits.OUTPUT,
                )
            }
            assertFalse(contents.entries.keys.any { path -> path.startsWith("modules/") })
            assertFalse(contents.entries.keys.any { path -> path.startsWith("maps/") })
            val diagnostics = R2StrictJson.parseObject(
                requireNotNull(contents.entries[R2PrivateOutputArchive.DIAGNOSTICS_PATH]),
            )
            val diagnostic =
                (diagnostics["diagnostics"] as List<*>).single() as Map<*, *>
            assertEquals(RESOLUTION_DIAGNOSTIC_KEYS, diagnostic.keys)
            assertEquals("resolution", diagnostic["phase"])
            assertEquals(number(failure.diagnosticCode), diagnostic["code"])
            assertEquals(
                R2DiagnosticsSchemaV1.sourceUri(
                    "project-output-1",
                    PACKAGE_TARGET_SOURCE,
                ),
                diagnostic["uri"],
            )
            assertEquals(null, diagnostic["range"])
            assertTrue((diagnostic["relatedInformation"] as List<*>).isEmpty())
            val evidence = diagnostic["nodeResolution"] as Map<*, *>
            assertEquals(setOf("policyCode"), evidence.keys)
            assertEquals(failure.contractValue, evidence["policyCode"])
        }

        assertThrows(R2OutputArchiveException::class.java) {
            R2PrivateOutputArchive.stageForTesting(
                privateDirectory = privateDirectory,
                authority = resolutionAuthority,
                compilation = resolutionCompilation,
                acceptedRootPaths = setOf(MAIN_SOURCE),
                acceptedSnapshotPaths = setOf(MAIN_SOURCE),
            )
        }
        val ordinaryPackageDiagnostic = diagnosticsCompilation().diagnostics.single().copy(
            sourcePath = PACKAGE_TARGET_SOURCE,
        )
        assertThrows(R2OutputArchiveException::class.java) {
            R2PrivateOutputArchive.stageForTesting(
                privateDirectory = privateDirectory,
                authority = authority(),
                compilation = compilation(
                    diagnostics = listOf(ordinaryPackageDiagnostic),
                    outputs = emptyList(),
                    emitSkipped = true,
                ),
                acceptedSnapshotPaths = setOf(PACKAGE_TARGET_SOURCE),
            )
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun controlledOutputCoversSelectedRootsAndSnapshotOnlyReachableImports() {
        val privateDirectory = temporaryFolder.newFolder("controlled")
        val dependencySource = "/sources/dependency.ts"
        val effective = R2ControlledTsConfigEffectiveOptions.resolve(
            mapOf("noUnusedLocals" to true),
        )
        val compilation = controlledCompilation(
            effective = effective,
            outputs = listOf(
                output("/outputs/main.js", JS_TEXT),
                output(
                    "/outputs/main.js.map",
                    sourceMap("main.js", "../sources/main.ts"),
                ),
                output(
                    "/outputs/dependency.js",
                    "\"use strict\";\n//# sourceMappingURL=dependency.js.map\n",
                    dependencySource,
                ),
                output(
                    "/outputs/dependency.js.map",
                    sourceMap("dependency.js", "../sources/dependency.ts"),
                    dependencySource,
                ),
            ),
        )
        val authority = authority().copy(
            configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
            configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
        )

        R2PrivateOutputArchive.stageForTesting(
            privateDirectory = privateDirectory,
            authority = authority,
            compilation = compilation,
            acceptedRootPaths = setOf(MAIN_SOURCE, dependencySource),
            acceptedSnapshotPaths = setOf(MAIN_SOURCE, dependencySource),
        ).use { staged ->
            val contents = staged.withImmutableReader { reader ->
                ZipArchiveV1Validator.validateImmutableAndRead(
                    file = reader,
                    limits = ZipArchiveV1Limits.OUTPUT,
                )
            }
            val manifest = R2StrictJson.parseObject(
                requireNotNull(contents.entries[R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH]),
            )
            assertEquals(TypeScriptR2Contract.CONFIG_KIND_TSCONFIG, manifest["configKind"])
            assertEquals(effective.fingerprint, manifest["effectiveOptionsFingerprint"])
            assertEquals(
                R2ControlledTsConfigEffectiveOptions.canonicalOptionKeys.toSet(),
                (manifest["effectiveOptions"] as Map<*, *>).keys,
            )
            assertEquals(number(2), manifest["emittedModuleCount"])
        }

        assertThrows(R2OutputArchiveException::class.java) {
            R2PrivateOutputArchive.stageForTesting(
                privateDirectory = privateDirectory,
                authority = authority,
                compilation = compilation,
                acceptedRootPaths = setOf(MAIN_SOURCE),
                acceptedSnapshotPaths = setOf(MAIN_SOURCE),
            )
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun inconsistentCompilerResultIsRejectedBeforeAStagingFileSurvives() {
        val privateDirectory = temporaryFolder.newFolder("invalid")
        val failure = assertThrows(R2OutputArchiveException::class.java) {
            R2PrivateOutputArchive.stageForTesting(
                privateDirectory = privateDirectory,
                authority = authority(),
                compilation = compilation(
                    diagnostics = emptyList(),
                    outputs = emptyList(),
                    emitSkipped = false,
                ),
            )
        }

        assertEquals(
            TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT,
            failure.contractErrorCode,
        )
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun mismatchedModuleAndMapAreRejectedAtThePackagingBoundary() {
        val privateDirectory = temporaryFolder.newFolder("mismatched-map")
        val sourceMap =
            """{"version":3,"file":"other.js","sourceRoot":"","sources":["../sources/main.ts"],""" +
                """"names":[],"mappings":"AAAA"}"""

        assertThrows(R2OutputArchiveException::class.java) {
            R2PrivateOutputArchive.stageForTesting(
                privateDirectory = privateDirectory,
                authority = authority(),
                compilation = compilation(
                    diagnostics = emptyList(),
                    outputs = listOf(
                        output("/outputs/main.js", JS_TEXT),
                        output("/outputs/other.js.map", sourceMap),
                    ),
                    emitSkipped = false,
                ),
            )
        }

        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun nestedSourceMapReferenceIsRelativeAndUrlEncoded() {
        val privateDirectory = temporaryFolder.newFolder("nested-map")
        val sourcePath = "/sources/nested/hash#name.ts"
        val compilerJavaScript =
            "\"use strict\";\n//# sourceMappingURL=hash#name.js.map\n"
        val sourceMap =
            """{"version":3,"file":"hash#name.js","sourceRoot":"","sources":""" +
                """["../../sources/nested/hash#name.ts"],""" +
                """"names":[],"mappings":"AAAA"}"""

        R2PrivateOutputArchive.stageForTesting(
            privateDirectory = privateDirectory,
            authority = authority().copy(
                entrySourceUri = "autojs://source/output/nested/hash%23name.ts",
                entryPath = "sources/nested/hash#name.ts",
            ),
            compilation = compilation(
                diagnostics = emptyList(),
                outputs = listOf(
                    output(
                        path = "/outputs/nested/hash#name.js",
                        text = compilerJavaScript,
                        sourcePath = sourcePath,
                    ),
                    output(
                        path = "/outputs/nested/hash#name.js.map",
                        text = sourceMap,
                        sourcePath = sourcePath,
                    ),
                ),
                emitSkipped = false,
            ),
        ).use { staged ->
            val contents = staged.withImmutableReader { reader ->
                ZipArchiveV1Validator.validateImmutableAndRead(
                    file = reader,
                    limits = ZipArchiveV1Limits.OUTPUT,
                )
            }
            assertEquals(
                "\"use strict\";\n" +
                    "//# sourceMappingURL=../../maps/nested/hash%23name.js.map\n",
                R2Utf8.decode(
                    requireNotNull(contents.entries["modules/nested/hash#name.js"]),
                    "Nested JavaScript",
                ),
            )
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun diagnosticCountLimitIsEnforcedBeforeSerialization() {
        val privateDirectory = temporaryFolder.newFolder("diagnostic-count")
        val diagnostic = diagnosticsCompilation().diagnostics.single()
        val oversized = List(TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT + 1) {
            diagnostic
        }

        assertThrows(IllegalArgumentException::class.java) {
            R2PrivateOutputArchive.stageForTesting(
                privateDirectory = privateDirectory,
                authority = authority(),
                compilation = compilation(
                    diagnostics = oversized,
                    outputs = emptyList(),
                    emitSkipped = true,
                ),
            )
        }

        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun completeDiagnosticsDocumentByteLimitIsEnforcedBeforeArchiveCommit() {
        val privateDirectory = temporaryFolder.newFolder("diagnostic-document-bytes")
        val diagnostic = diagnosticsCompilation().diagnostics.single()
        val oversized = List(129) { index ->
            diagnostic.copy(
                code = 920_000 + index,
                message = "x".repeat(TypeScriptR2Contract.MAX_DIAGNOSTIC_MESSAGE_CHARS),
            )
        }

        val failure = assertThrows(R2OutputArchiveException::class.java) {
            R2PrivateOutputArchive.stageForTesting(
                privateDirectory = privateDirectory,
                authority = authority(),
                compilation = compilation(
                    diagnostics = oversized,
                    outputs = emptyList(),
                    emitSkipped = true,
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("frozen semantic byte limit"))
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun outputAuthorityRejectsNonCanonicalEntryAndDigestValues() {
        assertThrows(IllegalArgumentException::class.java) {
            authority().copy(entryPath = "sources/../main.ts")
        }
        assertThrows(IllegalArgumentException::class.java) {
            authority().copy(inputArchiveSha256 = "A".repeat(64))
        }
    }

    @Test
    fun nodeRevisionOnePrivateBundleIsByteDeterministicAndKeepsExactProfile() {
        val profile = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT
        val nodeAuthority = authority().copy(
            profile = R2ProjectProfile.PROVISIONAL_NODE_NEXT,
            profileAuthority = profile,
            runtimeCapabilitiesFingerprint = profile.runtimeCapabilitiesFingerprint,
        )
        val sourceMap =
            """{"version":3,"file":"main.js","sourceRoot":"","sources":["../sources/main.ts"],"names":[],"mappings":"AAAA"}"""
        val compilation = R2ProjectCompilation(
            compilerApi = "createIncrementalProgram",
            compilerVersion = "6.0.3",
            targetProfile = TypeScriptCompilerContract.PROFILE_NODE,
            profileSchemaRevision = profile.profileSchemaRevision,
            profileRevision = profile.profileRevision,
            configSchemaRevision = profile.configSchemaRevision,
            configKind = profile.configKind,
            effectiveOptions = profile.effectiveOptions,
            diagnostics = emptyList(),
            outputs = listOf(
                output("/outputs/main.js", JS_TEXT),
                output("/outputs/main.js.map", sourceMap),
            ),
            emitSkipped = false,
        )

        val privateDirectory = temporaryFolder.newFolder("node-next")
        val firstBytes = R2PrivateOutputArchive.stageForTesting(
            privateDirectory = privateDirectory,
            authority = nodeAuthority,
            compilation = compilation,
        ).use { staged ->
            val contents = staged.withImmutableReader { reader ->
                ZipArchiveV1Validator.validateImmutableAndRead(
                    file = reader,
                    limits = ZipArchiveV1Limits.OUTPUT,
                )
            }
            val manifest = R2StrictJson.parseObject(
                requireNotNull(contents.entries[R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH]),
            )
            assertEquals(TypeScriptCompilerContract.PROFILE_NODE, manifest["targetProfile"])
            assertEquals("modules/main.js", manifest["entryModulePath"])
            assertEquals(profile.effectiveOptionsFingerprint, manifest["effectiveOptionsFingerprint"])
            staged.withImmutableReader { reader ->
                reader.seek(0L)
                ByteArray(reader.length().toInt()).also(reader::readFully)
            }
        }
        R2PrivateOutputArchive.stageForTesting(
            privateDirectory = privateDirectory,
            authority = nodeAuthority,
            compilation = compilation,
        ).use { staged ->
            val secondBytes = staged.withImmutableReader { reader ->
                reader.seek(0L)
                ByteArray(reader.length().toInt()).also(reader::readFully)
            }
            assertTrue(firstBytes.contentEquals(secondBytes))
            secondBytes.fill(0)
        }
        firstBytes.fill(0)
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun registeredNodeRevisionTwoOutputAuthorityRejectsRevisionOneCompilation() {
        val revisionOne = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1
        val revisionTwo = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        val revisionTwoOutputAuthority = authority().copy(
            profile = R2ProjectProfile.PROVISIONAL_NODE_NEXT,
            profileAuthority = revisionTwo,
            entrySourceUri = R2GeneratedModuleMappingPolicy.originalSourceUri(
                projectId = authority().projectId,
                sourcePath = MAIN_SOURCE,
            ),
            runtimeCapabilitiesFingerprint = revisionTwo.runtimeCapabilitiesFingerprint,
        )
        val revisionOneCompilation = R2ProjectCompilation(
            compilerApi = "createIncrementalProgram",
            compilerVersion = "6.0.3",
            targetProfile = revisionOne.profile.contractValue,
            profileSchemaRevision = revisionOne.profileSchemaRevision,
            profileRevision = revisionOne.profileRevision,
            configSchemaRevision = revisionOne.configSchemaRevision,
            configKind = revisionOne.configKind,
            effectiveOptions = revisionOne.effectiveOptions,
            diagnostics = emptyList(),
            outputs = listOf(
                output("/outputs/main.js", JS_TEXT),
                output(
                    "/outputs/main.js.map",
                    """{"version":3,"file":"main.js","sourceRoot":"","sources":["../sources/main.ts"],"names":[],"mappings":"AAAA"}""",
                ),
            ),
            emitSkipped = false,
        )

        assertThrows(R2OutputArchiveException::class.java) {
            R2PrivateOutputArchive.stageForTesting(
                privateDirectory = temporaryFolder.newFolder("node-revision-cross-drift"),
                authority = revisionTwoOutputAuthority,
                compilation = revisionOneCompilation,
            )
        }
    }

    @Test
    fun nodeRevisionTwoMixedOutputsUseFormalRevisionOneAndRejectProjectIdentityDrift() {
        val privateDirectory = temporaryFolder.newFolder("node-revision-two-mixed")
        val acceptedRoots = linkedSetOf(NODE_MAIN_SOURCE, NODE_MODULE_SOURCE, NODE_COMMON_SOURCE)
        val snapshot = nodeRevisionTwoSnapshot(rootNames = acceptedRoots.toList())
        val driftedSnapshot = nodeRevisionTwoSnapshot(
            rootNames = acceptedRoots.toList(),
            projectId = "$NODE_PROJECT_ID-drift",
        )
        val outputAuthority = nodeRevisionTwoOutputAuthority()
        val compilation = nodeRevisionTwoCompilation(snapshot)
        try {
            R2PrivateOutputArchive.stageForTesting(
                privateDirectory = privateDirectory,
                authority = outputAuthority,
                compilation = compilation,
                acceptedRootPaths = acceptedRoots,
                acceptedSnapshotPaths = snapshot.filePaths,
                acceptedSnapshot = snapshot,
            ).use { staged ->
                val contents = staged.withImmutableReader { reader ->
                    ZipArchiveV1Validator.validateImmutableAndRead(
                        file = reader,
                        limits = ZipArchiveV1Limits.OUTPUT,
                    )
                }
                assertEquals(
                    listOf(
                        R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH,
                        R2PrivateOutputArchive.DIAGNOSTICS_PATH,
                        "maps/common.cjs.map",
                        "maps/main.js.map",
                        "maps/module.mjs.map",
                        "modules/common.cjs",
                        "modules/main.js",
                        "modules/module.mjs",
                    ),
                    contents.entries.keys.toList(),
                )
                val manifest = R2StrictJson.parseObject(
                    requireNotNull(contents.entries[R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH]),
                )
                assertEquals(MANIFEST_KEYS + NODE_REVISION_TWO_CONDITIONAL_KEYS, manifest.keys)
                assertEquals(
                    number(R2PrivateOutputArchive.FORMAL_MANIFEST_REVISION),
                    manifest["revision"],
                )
                assertEquals("modules/main.js", manifest["entryModulePath"])
                assertEquals(
                    number(R2NodeModuleOutputPolicy.SCHEMA_REVISION),
                    manifest["nodeModuleOutputPolicySchemaRevision"],
                )
                assertEquals(
                    R2NodeModuleOutputPolicy.fingerprint,
                    manifest["nodeModuleOutputPolicyFingerprint"],
                )
                assertEquals(
                    R2GeneratedModuleMappingPolicy.SCHEMA,
                    manifest["generatedMappingSchema"],
                )
                assertEquals(snapshot.entryPath, manifest["resultSnapshotEntryPath"])
                assertEquals(snapshot.rootNames, manifest["resultSnapshotRootNames"])
                val mappings = manifest["generatedModuleMappings"] as List<*>
                assertEquals(3, mappings.size)
                assertEquals(
                    listOf(NODE_COMMON_SOURCE, NODE_MAIN_SOURCE, NODE_MODULE_SOURCE),
                    mappings.map { mapping -> (mapping as Map<*, *>)["sourcePath"] },
                )
                assertEquals(
                    listOf("commonjs", "commonjs", "esm"),
                    mappings.map { mapping -> (mapping as Map<*, *>)["moduleFormat"] },
                )
                assertTrue(mappings.all { mapping ->
                    (mapping as Map<*, *>).keys == GENERATED_MAPPING_KEYS
                })
                assertEquals(
                    compilation.generatedModuleMappings.map(R2GeneratedModuleMapping::fingerprint),
                    mappings.map { mapping -> (mapping as Map<*, *>)["fingerprint"] },
                )
            }
            assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())

            assertThrows(R2OutputArchiveException::class.java) {
                R2PrivateOutputArchive.stageForTesting(
                    privateDirectory = privateDirectory,
                    authority = outputAuthority,
                    compilation = compilation,
                    acceptedRootPaths = acceptedRoots,
                    acceptedSnapshotPaths = driftedSnapshot.filePaths,
                    acceptedSnapshot = driftedSnapshot,
                )
            }
            assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
        } finally {
            snapshot.close()
            driftedSnapshot.close()
        }
    }

    private fun authority() = R2OutputArchiveAuthority(
        requestId = "request-output-1",
        projectId = "project-output-1",
        entrySourceUri = "autojs://source/output/main.ts",
        entryPath = "sources/main.ts",
        profile = R2ProjectProfile.PROVISIONAL_RHINO,
        profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        compilationMode = TypeScriptCompilerContract.MODE_DEVELOPMENT,
        compilerVersion = "6.0.3",
        compilerDistributionFingerprint = sha256("distribution"),
        declarationsFingerprint = sha256("declarations"),
        runtimeCapabilitiesFingerprint =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO
                .runtimeCapabilitiesFingerprint,
        inputArchiveSha256 = sha256("input archive"),
        requestManifestSha256 = sha256("request manifest"),
        inputArchiveSchemaRevision = R2InputArchiveSchemaV1.REVISION,
        inputQuotaRevision = R2InputArchiveSchemaV1.INPUT_QUOTA_REVISION,
        callerInventoryFingerprint = sha256("caller inventory"),
        retainedInventoryFingerprint = sha256("retained inventory"),
        declarationProfileFingerprint = sha256("declaration profile"),
        declarationInventoryFingerprint = sha256("declaration inventory"),
        declarationFileCount = 1,
        declarationByteLength = 128,
        retainedFileCount = 2,
        retainedByteLength = 256,
        largestRetainedFileBytes = 128,
    )

    private fun nodeRevisionTwoOutputAuthority(): R2OutputArchiveAuthority {
        val profile = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        return R2OutputArchiveAuthority(
            requestId = "node-revision-two-output",
            projectId = NODE_PROJECT_ID,
            entrySourceUri = R2GeneratedModuleMappingPolicy.originalSourceUri(
                projectId = NODE_PROJECT_ID,
                sourcePath = NODE_MAIN_SOURCE,
            ),
            entryPath = NODE_MAIN_SOURCE.removePrefix("/"),
            profile = R2ProjectProfile.PROVISIONAL_NODE_NEXT,
            profileAuthority = profile,
            compilationMode = TypeScriptCompilerContract.MODE_DEVELOPMENT,
            compilerVersion = "6.0.3",
            compilerDistributionFingerprint = sha256("node distribution"),
            declarationsFingerprint = sha256("node declarations"),
            runtimeCapabilitiesFingerprint = profile.runtimeCapabilitiesFingerprint,
            inputArchiveSha256 = sha256("node input archive"),
            requestManifestSha256 = sha256("node request manifest"),
            inputArchiveSchemaRevision = R2InputArchiveSchemaV1.REVISION,
            inputQuotaRevision = R2InputArchiveSchemaV1.INPUT_QUOTA_REVISION,
            callerInventoryFingerprint = sha256("node caller inventory"),
            retainedInventoryFingerprint = sha256("node retained inventory"),
            declarationProfileFingerprint = sha256("node declaration profile"),
            declarationInventoryFingerprint = sha256("node declaration inventory"),
            declarationFileCount = 1,
            declarationByteLength = 128,
            retainedFileCount = 4,
            retainedByteLength = 512,
            largestRetainedFileBytes = 128,
        )
    }

    private fun nodeRevisionTwoSnapshot(
        rootNames: List<String>,
        projectId: String = NODE_PROJECT_ID,
    ): R2ProjectSnapshot = R2ProjectSnapshot.create(
        files = listOf(
            R2ProjectTextFile("/declarations/lib.d.ts", "declare interface Array<T> {}\n"),
            R2ProjectTextFile(NODE_MAIN_SOURCE, "export const main = 1;\n"),
            R2ProjectTextFile(NODE_MODULE_SOURCE, "export const module = 2;\n"),
            R2ProjectTextFile(NODE_COMMON_SOURCE, "export const common = 3;\n"),
        ),
        rootNames = rootNames,
        entryPath = NODE_MAIN_SOURCE,
        acceptedProjectId = projectId,
        profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
    )

    private fun nodeRevisionTwoCompilation(
        snapshot: R2ProjectInputSnapshot,
    ): R2ProjectCompilation {
        val outputs = listOf(
            nodeRevisionTwoOutput(NODE_MAIN_SOURCE, "/outputs/main.js"),
            nodeRevisionTwoOutput(NODE_MAIN_SOURCE, "/outputs/main.js.map"),
            nodeRevisionTwoOutput(NODE_MODULE_SOURCE, "/outputs/module.mjs"),
            nodeRevisionTwoOutput(NODE_MODULE_SOURCE, "/outputs/module.mjs.map"),
            nodeRevisionTwoOutput(NODE_COMMON_SOURCE, "/outputs/common.cjs"),
            nodeRevisionTwoOutput(NODE_COMMON_SOURCE, "/outputs/common.cjs.map"),
        )
        val profile = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        val resultAuthority = R2NodeRevisionTwoResultAuthority.derive(snapshot, outputs)
        return R2ProjectCompilation(
            compilerApi = "createIncrementalProgram",
            compilerVersion = "6.0.3",
            targetProfile = profile.profile.contractValue,
            profileSchemaRevision = profile.profileSchemaRevision,
            profileRevision = profile.profileRevision,
            configSchemaRevision = profile.configSchemaRevision,
            configKind = profile.configKind,
            effectiveOptions = profile.effectiveOptions,
            diagnostics = emptyList(),
            outputs = outputs,
            emitSkipped = false,
            nodeRevisionTwoResultAuthority = resultAuthority,
        )
    }

    private fun nodeRevisionTwoOutput(
        sourcePath: String,
        outputPath: String,
    ): R2ProjectOutput {
        val modulePath = outputPath.removeSuffix(".map")
        val moduleFile = modulePath.substringAfterLast('/')
        val text = if (outputPath.endsWith(".map")) {
            sourceMap(moduleFile, "../sources/${sourcePath.substringAfterLast('/')}")
        } else {
            "export const value = 1;\n//# sourceMappingURL=$moduleFile.map\n"
        }
        return output(outputPath, text, sourcePath)
    }

    private fun emittedCompilation(): R2ProjectCompilation {
        val sourceMap =
            """{"version":3,"file":"main.js","sourceRoot":"","sources":["../sources/main.ts"],""" +
                """"names":[],"mappings":"AAAA"}"""
        return compilation(
            diagnostics = emptyList(),
            outputs = listOf(
                output("/outputs/main.js", JS_TEXT),
                output("/outputs/main.js.map", sourceMap),
            ),
            emitSkipped = false,
        )
    }

    private fun diagnosticsCompilation() = compilation(
        diagnostics = listOf(
            R2ProjectDiagnostic(
                phase = R2ProjectDiagnosticPhase.SEMANTIC,
                category = 1,
                code = 2_322,
                message = "类型不匹配 \"value\"。",
                sourcePath = MAIN_SOURCE,
                start = 0,
                length = 5,
                line = 1,
                column = 1,
            ),
        ),
        outputs = emptyList(),
        emitSkipped = true,
    )

    private fun unifiedPreEmitDiagnostics(): List<R2ProjectDiagnostic> =
        listOf(
            R2ProjectDiagnosticPhase.OPTIONS,
            R2ProjectDiagnosticPhase.SYNTACTIC,
            R2ProjectDiagnosticPhase.GLOBAL,
            R2ProjectDiagnosticPhase.SEMANTIC,
        ).mapIndexed { index, phase ->
            val hasLocation = phase == R2ProjectDiagnosticPhase.SYNTACTIC ||
                phase == R2ProjectDiagnosticPhase.SEMANTIC
            R2ProjectDiagnostic(
                phase = phase,
                category = 1,
                code = 910_400 + index,
                message = "${phase.protocolValue} error",
                sourcePath = if (hasLocation) MAIN_SOURCE else null,
                start = if (hasLocation) 0 else null,
                length = if (hasLocation) 1 else null,
                line = if (hasLocation) 1 else null,
                column = if (hasLocation) 1 else null,
            )
        }

    private fun resolutionDiagnosticsCompilation(
        failure: R2NodeResolutionFailureCode,
    ): R2ProjectCompilation {
        val effective = R2ControlledTsConfigEffectiveOptions.resolve(emptyMap())
        return R2ProjectCompilation(
            compilerApi = "createIncrementalProgram",
            compilerVersion = "6.0.3",
            targetProfile = "rhino",
            profileSchemaRevision =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileSchemaRevision,
            profileRevision =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision,
            configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
            configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
            effectiveOptions = effective.compilerOptions,
            controlledEffectiveOptions = effective,
            selectedRootNames = listOf(MAIN_SOURCE),
            diagnostics = listOf(
                R2ProjectDiagnostic(
                    phase = R2ProjectDiagnosticPhase.RESOLUTION,
                    category = 1,
                    code = failure.diagnosticCode,
                    message = "Controlled Node resolution failed.",
                    sourcePath = PACKAGE_TARGET_SOURCE,
                    start = null,
                    length = null,
                    line = null,
                    column = null,
                    nodeResolution = R2NodeResolutionDiagnosticEvidence(failure),
                ),
            ),
            outputs = emptyList(),
            emitSkipped = true,
        )
    }

    private fun controlledCompilation(
        effective: R2ControlledTsConfigEffectiveOptions,
        outputs: List<R2ProjectOutput>,
    ) = R2ProjectCompilation(
        compilerApi = "createIncrementalProgram",
        compilerVersion = "6.0.3",
        targetProfile = "rhino",
        profileSchemaRevision =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileSchemaRevision,
        profileRevision =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision,
        configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
        configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
        effectiveOptions = effective.compilerOptions,
        controlledEffectiveOptions = effective,
        selectedRootNames = listOf(MAIN_SOURCE),
        diagnostics = emptyList(),
        outputs = outputs,
        emitSkipped = false,
    )

    private fun compilation(
        diagnostics: List<R2ProjectDiagnostic>,
        outputs: List<R2ProjectOutput>,
        emitSkipped: Boolean,
    ) = R2ProjectCompilation(
        compilerApi = "createIncrementalProgram",
        compilerVersion = "6.0.3",
        targetProfile = "rhino",
        profileSchemaRevision =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileSchemaRevision,
        profileRevision =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision,
        configSchemaRevision =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.configSchemaRevision,
        configKind = R2CompilationProfileRegistry.PROVISIONAL_RHINO.configKind,
        effectiveOptions =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.effectiveOptions,
        diagnostics = diagnostics,
        outputs = outputs,
        emitSkipped = emitSkipped,
    )

    private fun output(
        path: String,
        text: String,
        sourcePath: String = MAIN_SOURCE,
    ): R2ProjectOutput {
        val bytes = R2Utf8.encode(text, "Test output")
        return try {
            R2ProjectOutput(
                path = path,
                text = text,
                sha256 = R2Digests.sha256(bytes),
                sourcePaths = listOf(sourcePath),
            )
        } finally {
            bytes.fill(0)
        }
    }

    private fun sourceMap(file: String, source: String): String =
        """{"version":3,"file":"$file","sourceRoot":"","sources":["$source"],"names":[],"mappings":"AAAA"}"""

    private fun sha256(text: String): String =
        R2Digests.sha256(text.toByteArray(StandardCharsets.UTF_8))

    private fun number(value: Int) = R2StrictJson.NumberToken(value.toString())

    private companion object {
        const val MAIN_SOURCE = "/sources/main.ts"
        const val PACKAGE_TARGET_SOURCE = "/packages/dependency/index.d.ts"
        const val NODE_PROJECT_ID = "node-revision-two-output-project"
        const val NODE_MAIN_SOURCE = "/sources/main.ts"
        const val NODE_MODULE_SOURCE = "/sources/module.mts"
        const val NODE_COMMON_SOURCE = "/sources/common.cts"
        const val JS_TEXT =
            "\"use strict\";\nexports.answer = 42;\n//# sourceMappingURL=main.js.map\n"
        const val PACKAGED_JS_TEXT =
            "\"use strict\";\nexports.answer = 42;\n" +
                "//# sourceMappingURL=../maps/main.js.map\n"
        val MANIFEST_KEYS = setOf(
            "schema",
            "revision",
            "outputQuotaRevision",
            "contractVersion",
            "contractHash",
            "outputArchiveTransportVersion",
            "requestId",
            "projectId",
            "entrySourceUri",
            "entryModulePath",
            "targetProfile",
            "profileSchemaRevision",
            "profileRevision",
            "configSchemaRevision",
            "configKind",
            "runtimeCapabilitySchemaRevision",
            "compilationMode",
            "compilerApi",
            "compilerPackage",
            "compilerVersion",
            "compilerDistributionFingerprint",
            "declarationsFingerprint",
            "runtimeCapabilitiesFingerprint",
            "inputArchiveSha256",
            "requestManifestSha256",
            "inputArchiveSchemaRevision",
            "inputQuotaRevision",
            "callerInventoryFingerprint",
            "retainedInventoryFingerprint",
            "declarationProfileFingerprint",
            "declarationInventoryFingerprint",
            "declarationFileCount",
            "declarationByteLength",
            "retainedFileCount",
            "retainedByteLength",
            "largestRetainedFileBytes",
            "outcome",
            "emitSkipped",
            "diagnosticCount",
            "emittedModuleCount",
            "diagnosticsPath",
            "effectiveOptions",
            "effectiveOptionsFingerprint",
            "moduleInventoryFingerprint",
            "artifactInventoryFingerprint",
            "files",
        )
        val NODE_REVISION_TWO_CONDITIONAL_KEYS = setOf(
            "nodeModuleOutputPolicySchemaRevision",
            "nodeModuleOutputPolicyRevision",
            "nodeModuleOutputPolicyFingerprint",
            "generatedMappingSchema",
            "generatedMappingSchemaRevision",
            "generatedMappingPolicyRevision",
            "resolverPolicyRevision",
            "snapshotTargetInventoryFingerprint",
            "packageMetadataInventoryFingerprint",
            "packageFormatCatalogFingerprint",
            "packageTargetInventoryFingerprint",
            "targetUniverseFingerprint",
            "resultSnapshotEntryPath",
            "resultSnapshotRootNames",
            "generatedModuleMappings",
        )
        val GENERATED_MAPPING_KEYS = setOf(
            "sourcePath",
            "projectNamespace",
            "originalSourceUri",
            "moduleFormat",
            "outputExtension",
            "generatedPath",
            "sourceMapPath",
            "generatedUri",
            "canonicalEvidence",
            "fingerprint",
        )
        val EFFECTIVE_OPTION_KEYS = setOf(
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
        )
        val DIAGNOSTICS_KEYS = setOf(
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
        val NORMAL_DIAGNOSTIC_KEYS = setOf(
            "phase",
            "uri",
            "range",
            "category",
            "code",
            "message",
            "relatedInformation",
        )
        val RESOLUTION_DIAGNOSTIC_KEYS = NORMAL_DIAGNOSTIC_KEYS + "nodeResolution"
        val RELATED_INFORMATION_KEYS = setOf(
            "uri",
            "range",
            "category",
            "code",
            "message",
        )
        val RANGE_KEYS = setOf("start", "length", "line", "column")
    }
}
