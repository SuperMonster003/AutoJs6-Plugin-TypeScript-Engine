package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2NodeRevisionTwoResultAuthorityTest {

    @Test
    fun derivesCanonicalMappingsForTsMtsAndCtsOutputs() {
        val snapshot = snapshot(
            projectId = "mapping-project",
            sources = listOf(MAIN_TS, MODULE_MTS, COMMON_JS_CTS),
            rootNames = listOf(MAIN_TS, MODULE_MTS, COMMON_JS_CTS),
        )
        try {
            val authority = R2NodeRevisionTwoResultAuthority.derive(
                snapshot = snapshot,
                outputs = outputs(
                    MAIN_TS to "/outputs/main.js",
                    MODULE_MTS to "/outputs/module.mjs",
                    COMMON_JS_CTS to "/outputs/common.cjs",
                ),
            )

            assertEquals("mapping-project", authority.acceptedProjectId)
            assertEquals(snapshot.entryPath, authority.entryPath)
            assertEquals(snapshot.rootNames, authority.rootNames)
            assertEquals(R2NodeResolutionKernelV2.POLICY_REVISION, authority.resolverPolicyRevision)
            assertEquals(3, authority.generatedModuleMappings.size)
            assertEquals(
                listOf(
                    COMMON_JS_CTS to R2ModuleFormat.COMMONJS,
                    MAIN_TS to R2ModuleFormat.COMMONJS,
                    MODULE_MTS to R2ModuleFormat.ESM,
                ),
                authority.generatedModuleMappings.map { mapping ->
                    mapping.sourcePath to mapping.moduleFormat
                },
            )
            assertEquals(
                listOf(".cjs", ".js", ".mjs"),
                authority.generatedModuleMappings.map(R2GeneratedModuleMapping::outputExtension),
            )
        } finally {
            snapshot.close()
        }
    }

    @Test
    fun exactPackageTypeAndProjectIdDriveNativeMappingEvidence() {
        val packageBytes = "{\"type\":\"module\"}\n".toByteArray(StandardCharsets.UTF_8)
        val packageInventory = R2PackageMetadataInventory.create(
            dependencyRoots = emptyList(),
            files = listOf(
                R2PackageMetadataFileDescriptor(
                    path = R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH,
                    size = packageBytes.size.toLong(),
                    sha256 = R2Digests.sha256(packageBytes),
                ),
            ),
        )
        val first = snapshot(
            projectId = "first-project",
            sources = listOf(MAIN_TS),
            rootNames = listOf(MAIN_TS),
            packageInventory = packageInventory,
            packageBytes = listOf(packageBytes),
        )
        val second = snapshot(
            projectId = "second-project",
            sources = listOf(MAIN_TS),
            rootNames = listOf(MAIN_TS),
            packageInventory = packageInventory,
            packageBytes = listOf(packageBytes),
        )
        try {
            val emitted = outputs(MAIN_TS to "/outputs/main.js")
            val firstAuthority = R2NodeRevisionTwoResultAuthority.derive(first, emitted)
            val secondAuthority = R2NodeRevisionTwoResultAuthority.derive(second, emitted)
            val firstMapping = firstAuthority.generatedModuleMappings.single()
            val secondMapping = secondAuthority.generatedModuleMappings.single()

            assertEquals(R2ModuleFormat.ESM, firstMapping.moduleFormat)
            assertEquals(packageInventory.fingerprint, firstAuthority.packageMetadataInventoryFingerprint)
            assertEquals(
                requireNotNull(first.nodePackageFormatCatalog).fingerprint,
                firstAuthority.packageFormatCatalogFingerprint,
            )
            assertNotEquals(firstMapping.projectNamespace, secondMapping.projectNamespace)
            assertNotEquals(firstMapping.originalSourceUri, secondMapping.originalSourceUri)
            assertNotEquals(firstMapping.generatedUri, secondMapping.generatedUri)
            assertNotEquals(firstMapping.fingerprint, secondMapping.fingerprint)
            assertThrows(IllegalArgumentException::class.java) {
                R2NodeRevisionTwoResultAuthority.requireExact(
                    snapshot = second,
                    outputs = emitted,
                    claimed = firstAuthority,
                )
            }
        } finally {
            first.close()
            second.close()
            packageBytes.fill(0)
        }
    }

    @Test
    fun nearestWorkspacePackageBoundariesDriveEsmAndCommonJsMappings() {
        val packageBodies = listOf(
            "{}\n".toByteArray(StandardCharsets.UTF_8),
            "{\"type\":\"commonjs\"}\n".toByteArray(StandardCharsets.UTF_8),
            "{\"type\":\"module\"}\n".toByteArray(StandardCharsets.UTF_8),
        )
        val packagePaths = listOf(
            "package.json",
            "packages/cjs/package.json",
            "packages/esm/package.json",
        )
        val packageInventory = R2PackageMetadataInventory.create(
            dependencyRoots = listOf("packages/cjs", "packages/esm"),
            files = packagePaths.zip(packageBodies) { path, bytes ->
                R2PackageMetadataFileDescriptor(
                    path = path,
                    size = bytes.size.toLong(),
                    sha256 = R2Digests.sha256(bytes),
                )
            },
        )
        val cjsSource = "/sources/packages/cjs/value.ts"
        val esmSource = "/sources/packages/esm/value.ts"
        val snapshot = snapshot(
            projectId = "workspace-package-boundaries",
            sources = listOf(cjsSource, esmSource),
            rootNames = listOf(cjsSource, esmSource),
            packageInventory = packageInventory,
            packageBytes = packageBodies,
        )
        try {
            val authority = R2NodeRevisionTwoResultAuthority.derive(
                snapshot = snapshot,
                outputs = outputs(
                    cjsSource to "/outputs/packages/cjs/value.js",
                    esmSource to "/outputs/packages/esm/value.js",
                ),
            )
            val mappings = authority.generatedModuleMappings.associateBy { mapping ->
                mapping.sourcePath
            }
            assertEquals(R2ModuleFormat.COMMONJS, mappings.getValue(cjsSource).moduleFormat)
            assertEquals(R2ModuleFormat.ESM, mappings.getValue(esmSource).moduleFormat)
            assertTrue(
                mappings.getValue(cjsSource).canonicalEvidence.contains(
                    "packageBoundary=%2Fpackages%2Fcjs",
                ),
            )
            assertTrue(
                mappings.getValue(esmSource).canonicalEvidence.contains(
                    "packageBoundary=%2Fpackages%2Fesm",
                ),
            )
        } finally {
            snapshot.close()
            packageBodies.forEach { bytes -> bytes.fill(0) }
        }
    }

    @Test
    fun rejectsMissingProjectIdentityWrongOutputKindAndSnapshotRootDrift() {
        val withoutProjectId = snapshot(
            projectId = null,
            sources = listOf(MODULE_MTS),
            rootNames = listOf(MODULE_MTS),
        )
        try {
            assertThrows(IllegalArgumentException::class.java) {
                R2NodeRevisionTwoResultAuthority.derive(
                    withoutProjectId,
                    outputs(MODULE_MTS to "/outputs/module.mjs"),
                )
            }
        } finally {
            withoutProjectId.close()
        }

        val snapshot = snapshot(
            projectId = "strict-project",
            sources = listOf(MAIN_TS),
            rootNames = listOf(MAIN_TS),
        )
        val driftedRoots = snapshot(
            projectId = "strict-project",
            sources = listOf(MAIN_TS, MODULE_MTS),
            rootNames = listOf(MAIN_TS, MODULE_MTS),
        )
        try {
            assertThrows(IllegalArgumentException::class.java) {
                R2NodeRevisionTwoResultAuthority.derive(
                    driftedRoots,
                    outputs(MODULE_MTS to "/outputs/module.js"),
                )
            }
            val diagnosticAuthority = R2NodeRevisionTwoResultAuthority.derive(
                snapshot = snapshot,
                outputs = emptyList(),
            )
            assertThrows(IllegalArgumentException::class.java) {
                R2NodeRevisionTwoResultAuthority.requireExact(
                    snapshot = driftedRoots,
                    outputs = emptyList(),
                    claimed = diagnosticAuthority,
                )
            }
        } finally {
            snapshot.close()
            driftedRoots.close()
        }
    }

    private fun snapshot(
        projectId: String?,
        sources: List<String>,
        rootNames: List<String>,
        packageInventory: R2PackageMetadataInventory? = null,
        packageBytes: List<ByteArray> = emptyList(),
    ): R2ProjectSnapshot = R2ProjectSnapshot.create(
        files = listOf(
            R2ProjectTextFile("/declarations/lib.d.ts", "declare interface Array<T> {}\n"),
        ) + sources.map { sourcePath ->
            R2ProjectTextFile(sourcePath, "export const value = 1;\n")
        },
        rootNames = rootNames,
        entryPath = rootNames.first(),
        acceptedProjectId = projectId,
        profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
        packageMetadataInventory = packageInventory,
        packageMetadataBytes = packageBytes,
    )

    private fun outputs(vararg pairs: Pair<String, String>): List<R2ProjectOutput> =
        pairs.flatMap { (sourcePath, modulePath) ->
            listOf(
                output(modulePath, sourcePath),
                output("$modulePath.map", sourcePath),
            )
        }

    private fun output(path: String, sourcePath: String): R2ProjectOutput {
        val text = if (path.endsWith(".map")) "{}" else "export const value = 1;\n"
        return R2ProjectOutput(
            path = path,
            text = text,
            sha256 = R2Digests.sha256(text.toByteArray(StandardCharsets.UTF_8)),
            sourcePaths = listOf(sourcePath),
        )
    }

    private companion object {
        const val MAIN_TS = "/sources/main.ts"
        const val MODULE_MTS = "/sources/module.mts"
        const val COMMON_JS_CTS = "/sources/common.cts"
    }
}
