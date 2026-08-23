package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class R2NodeResolutionKernelV2Test {

    @Test
    fun impliedFormatDerivesOnlyFromExtensionAndNearestAcceptedPackageType() {
        val module = authority(
            projectJson = "{\"type\":\"module\"}",
            packageJson = "{\"exports\":\"./entry.mjs\"}",
            targetPaths = listOf("packages/pkg/entry.mts"),
        )
        val commonjs = authority(
            projectJson = "{\"type\":\"commonjs\"}",
            packageJson = "{\"exports\":\"./entry.cjs\"}",
            targetPaths = listOf("packages/pkg/entry.cts"),
        )

        assertEquals(
            R2ModuleFormat.ESM,
            R2NodeResolutionKernelV2.impliedModuleFormat(
                "/sources/main.ts",
                module.packageFormat,
            ),
        )
        assertEquals(
            R2ModuleFormat.COMMONJS,
            R2NodeResolutionKernelV2.impliedModuleFormat(
                "/sources/main.ts",
                commonjs.packageFormat,
            ),
        )
        assertEquals(
            R2ModuleFormat.COMMONJS,
            R2NodeResolutionKernelV2.impliedModuleFormat("/sources/main.ts", null),
        )
        assertEquals(
            R2ModuleFormat.ESM,
            R2NodeResolutionKernelV2.impliedModuleFormat(
                "/sources/main.mts",
                commonjs.packageFormat,
            ),
        )
        assertEquals(
            R2ModuleFormat.COMMONJS,
            R2NodeResolutionKernelV2.impliedModuleFormat(
                "/sources/main.cts",
                module.packageFormat,
            ),
        )
        assertEquals(
            R2ModuleFormat.ESM,
            R2NodeResolutionKernelV2.impliedModuleFormat(
                "/packages/pkg/types.d.mts",
                commonjs.packageFormat,
            ),
        )
    }

    @Test
    fun revisionTwoSubstitutesRuntimeExtensionsToOneExactSourceFamily() {
        val esm = authority(
            projectJson = "{\"type\":\"commonjs\"}",
            packageJson = "{\"exports\":\"./entry.mjs\"}",
            targetPaths = listOf("packages/pkg/entry.mts"),
        )
        val esmOutcome = resolve(esm, "/sources/main.mts")
        assertEquals("/packages/pkg/entry.mts", resolved(esmOutcome).target.path)
        assertEquals(R2ModuleFormat.ESM, esmOutcome.evidence.impliedModuleFormat)
        assertEquals(R2NodeResolutionV2ConditionMode.IMPORT, esmOutcome.evidence.conditionMode)

        val cjs = authority(
            projectJson = "{\"type\":\"module\"}",
            packageJson = "{\"exports\":\"./entry.cjs\"}",
            targetPaths = listOf("packages/pkg/entry.cts"),
        )
        val cjsOutcome = resolve(cjs, "/sources/main.cts")
        assertEquals("/packages/pkg/entry.cts", resolved(cjsOutcome).target.path)
        assertEquals(R2ModuleFormat.COMMONJS, cjsOutcome.evidence.impliedModuleFormat)
        assertEquals(R2NodeResolutionV2ConditionMode.REQUIRE, cjsOutcome.evidence.conditionMode)

        val js = authority(
            projectJson = "{\"type\":\"module\"}",
            packageJson = "{\"exports\":\"./entry.js\"}",
            targetPaths = listOf("packages/pkg/entry.ts"),
        )
        assertEquals(
            "/packages/pkg/entry.ts",
            resolved(resolve(js, "/sources/main.ts")).target.path,
        )
    }

    @Test
    fun completeSnapshotTargetUniverseResolvesRelativeSourcesWithoutMetadata() {
        val context = authority(
            projectJson = null,
            packageJson = null,
            targetPaths = listOf(
                "/sources/dep.cts",
                "/sources/dep.mts",
                "/sources/dep.ts",
                "/sources/types.d.ts",
            ),
        )

        listOf(
            Triple("/sources/main.mts", "./dep.mjs", "/sources/dep.mts"),
            Triple("/sources/main.cts", "./dep.cjs", "/sources/dep.cts"),
            Triple("/sources/main.ts", "./dep.js", "/sources/dep.ts"),
            Triple("/sources/main.ts", "./types.d.ts", "/sources/types.d.ts"),
        ).forEach { (containingPath, specifier, expected) ->
            assertEquals(expected, resolved(resolve(context, containingPath, specifier)).target.path)
        }
        val noBoundary = resolve(context, "/sources/main.ts", "./dep.js")
        assertEquals(R2ModuleFormat.COMMONJS, noBoundary.evidence.impliedModuleFormat)
        assertNull(noBoundary.evidence.packageMetadataInventoryFingerprint)
        assertNull(noBoundary.evidence.packageFormatFingerprint)
        assertNull(noBoundary.evidence.packageTargetInventoryFingerprint)
    }

    @Test
    fun revisionTwoPreservesUtf8SourcePathsInFingerprintAndResolution() {
        val context = authority(
            projectJson = null,
            packageJson = null,
            targetPaths = listOf("/sources/dep.mts"),
            additionalSnapshotPaths = listOf("/sources/入口.mts"),
        )
        val resolved = resolved(resolve(context, "/sources/入口.mts", "./dep.mjs"))
        assertEquals("/sources/dep.mts", resolved.target.path)
        assertTrue(context.snapshotTargets.canonicalInput.contains("/sources/%E5%85%A5%E5%8F%A3.mts"))
        assertTrue(context.targetUniverseCanonicalInput.contains("/sources/%E5%85%A5%E5%8F%A3.mts"))
        assertTrue(
            context.snapshotTargets.canonicalInput.all { character ->
                character.code in 0x20..0x7e
            },
        )

        val literalPercentPath = snapshotTargets(
            listOf(projectFile("/sources/%E5%85%A5%E5%8F%A3.mts")),
        )
        assertNotEquals(context.snapshotTargets.fingerprint, literalPercentPath.fingerprint)
        assertTrue(literalPercentPath.canonicalInput.contains("/sources/%25E5%2585"))

        val nestedContext = authority(
            projectJson = null,
            packageJson = null,
            targetPaths = listOf("/sources/目录/dep.mts"),
            additionalSnapshotPaths = listOf("/sources/目录/入口.mts"),
        )
        assertEquals(
            "/sources/目录/dep.mts",
            resolved(resolve(nestedContext, "/sources/目录/入口.mts", "./dep.mjs")).target.path,
        )
    }

    @Test
    fun metadataOnlyRootImportsCanResolveOrdinarySnapshotTargets() {
        val context = authority(
            projectJson =
                "{\"type\":\"module\",\"imports\":{\"#util\":\"./sources/util.mjs\"}}",
            packageJson = null,
            targetPaths = listOf("/sources/util.mts"),
        )

        val outcome = resolve(context, "/sources/main.ts", "#util")
        assertEquals("/sources/util.mts", resolved(outcome).target.path)
        assertEquals(R2NodeResolutionV2ConditionMode.IMPORT, outcome.evidence.conditionMode)
        assertTrue(outcome.evidence.packageMetadataInventoryFingerprint != null)
        assertTrue(outcome.evidence.packageFormatFingerprint != null)
        assertNull(outcome.evidence.packageTargetInventoryFingerprint)
    }

    @Test
    fun noMetadataBarePackageAndImportsFailWithStableCatalogCodes() {
        val context = authority(
            projectJson = null,
            packageJson = null,
            targetPaths = listOf("/sources/dep.ts"),
        )
        assertEquals(
            R2NodeResolutionFailureCode.PACKAGE_NOT_ALLOWED,
            failure(resolve(context, "/sources/main.ts", "pkg")).code,
        )
        assertEquals(
            R2NodeResolutionFailureCode.PACKAGE_METADATA_MISSING,
            failure(resolve(context, "/sources/main.ts", "#missing")).code,
        )
    }

    @Test
    fun revisionTwoDeclarationTargetsAreExactAndNeverRuntimeFallbacks() {
        listOf("d.ts", "d.mts", "d.cts").forEach { suffix ->
            val path = "packages/pkg/index.$suffix"
            val context = authority(
                projectJson = "{\"type\":\"module\"}",
                packageJson = "{\"exports\":\"./index.$suffix\"}",
                targetPaths = listOf(path),
            )
            val result = resolved(resolve(context, "/sources/main.mts"))
            assertEquals("/$path", result.target.path)
            assertEquals(R2NodeResolutionTargetKind.DECLARATION, result.target.kind)
        }

        listOf(
            "./index.js" to "packages/pkg/index.d.ts",
            "./index.mjs" to "packages/pkg/index.d.mts",
            "./index.cjs" to "packages/pkg/index.d.cts",
            "./index.d.js" to "packages/pkg/index.d.ts",
            "./index.d.mjs" to "packages/pkg/index.d.mts",
            "./index.d.cjs" to "packages/pkg/index.d.cts",
        ).forEach { (mapping, target) ->
            val context = authority(
                projectJson = "{\"type\":\"module\"}",
                packageJson = "{\"exports\":\"$mapping\"}",
                targetPaths = listOf(target),
            )
            assertEquals(
                R2NodeResolutionFailureCode.TARGET_NOT_IN_SNAPSHOT,
                failure(resolve(context, "/sources/main.mts")).code,
            )
        }
    }

    @Test
    fun moduleConditionFollowsImpliedFormatAndTypeReferenceUsesTypesOnly() {
        val packageJson =
            "{\"exports\":{\".\":{\"require\":\"./cjs.cjs\",\"import\":\"./esm.mjs\"," +
                "\"types\":\"./types.d.mts\"}}}"
        val module = authority(
            projectJson = "{\"type\":\"module\"}",
            packageJson = packageJson,
            targetPaths = listOf(
                "packages/pkg/cjs.cts",
                "packages/pkg/esm.mts",
                "packages/pkg/types.d.mts",
            ),
        )
        val commonjs = authority(
            projectJson = "{}",
            packageJson = packageJson,
            targetPaths = listOf(
                "packages/pkg/cjs.cts",
                "packages/pkg/esm.mts",
                "packages/pkg/types.d.mts",
            ),
        )

        assertEquals(
            "/packages/pkg/esm.mts",
            resolved(resolve(module, "/sources/main.ts")).target.path,
        )
        assertEquals(
            "/packages/pkg/cjs.cts",
            resolved(resolve(commonjs, "/sources/main.ts")).target.path,
        )
        val typeReference = R2NodeResolutionKernelV2.resolveTypeReference(
            request = R2NodeResolutionV2Request("/sources/main.cts", "pkg"),
            authority = commonjs,
        )
        assertEquals("/packages/pkg/types.d.mts", resolved(typeReference).target.path)
        assertEquals(R2NodeResolutionV2ConditionMode.TYPES, typeReference.evidence.conditionMode)
        assertEquals(listOf("types"), resolved(typeReference).matchedConditions)
        assertEquals(0, R2NodeResolutionKernelV2.TRUSTED_CUSTOM_CONDITION_COUNT)
        assertEquals("implied-module-format", R2NodeResolutionKernelV2.CONDITION_SOURCE)
        assertEquals("type-reference-hook", R2NodeResolutionKernelV2.TYPE_REFERENCE_CONDITION_SOURCE)
    }

    @Test
    fun nearestDependencyBoundaryControlsTsConditionMode() {
        val context = authority(
            projectJson = "{\"type\":\"module\"}",
            packageJson =
                "{\"type\":\"commonjs\",\"exports\":{\".\":{\"import\":\"./esm.mjs\"," +
                    "\"require\":\"./cjs.cjs\"}}}",
            targetPaths = listOf(
                "packages/pkg/cjs.cts",
                "packages/pkg/consumer.ts",
                "packages/pkg/esm.mts",
            ),
        )
        val outcome = resolve(context, "/packages/pkg/consumer.ts")
        assertEquals(R2ModuleFormat.COMMONJS, outcome.evidence.impliedModuleFormat)
        assertEquals(R2NodeResolutionV2ConditionMode.REQUIRE, outcome.evidence.conditionMode)
        assertEquals("/packages/pkg/cjs.cts", resolved(outcome).target.path)
    }

    @Test
    fun snapshotTargetAuthorityIsImmutableFingerprintBoundAndRejectsDrift() {
        val mutableFiles = arrayListOf(
            projectFile("/declarations/lib.d.ts"),
            projectFile("/sources/main.mts"),
        )
        val first = snapshotTargets(mutableFiles)
        mutableFiles.clear()
        assertEquals(2, first.files.size)
        assertEquals(2, first.resolutionTargets.size)
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (first.files as MutableList<R2ProjectFileMetadata>).clear()
        }
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (first.resolutionTargets as MutableList<R2NodeResolutionTarget>).clear()
        }

        val changed = snapshotTargets(
            listOf(
                projectFile("/declarations/lib.d.ts"),
                projectFile("/sources/main.mts", sha256 = ONE_BYTE_SHA256, utf8Bytes = 1),
            ),
        )
        assertNotEquals(first.fingerprint, changed.fingerprint)
        assertTrue(first.canonicalInput.startsWith(
            R2NodeResolutionV2SnapshotTargetInventory.CANONICAL_DOMAIN,
        ))

        assertFails<IllegalArgumentException> {
            snapshotTargets(
                listOf(
                    projectFile("/sources/A.ts"),
                    projectFile("/sources/a.ts"),
                ),
            )
        }
        assertFails<IllegalArgumentException> {
            snapshotTargets(listOf(projectFile("/declarations/types.d.mts")))
        }
        assertFails<IllegalArgumentException> {
            R2NodeResolutionV2SnapshotTargetInventory.create(
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
                files = listOf(projectFile("/sources/main.ts")),
            )
        }
    }

    @Test
    fun authorityAcceptsExactOptionalMatrixAndRejectsSplitBrain() {
        val noMetadata = authority(null, null, listOf("/sources/dep.ts"))
        assertNull(noMetadata.packageMetadata)
        assertNull(noMetadata.packageFormat)
        assertNull(noMetadata.packageTargets)

        val metadataOnly = authority(
            projectJson = "{\"imports\":{\"#x\":\"./sources/dep.js\"}}",
            packageJson = null,
            targetPaths = listOf("/sources/dep.ts"),
        )
        assertTrue(metadataOnly.packageMetadata != null)
        assertTrue(metadataOnly.packageFormat != null)
        assertNull(metadataOnly.packageTargets)

        val packageTargets = authority(
            projectJson = "{}",
            packageJson = "{\"exports\":\"./entry.mjs\"}",
            targetPaths = listOf("packages/pkg/entry.mts"),
        )
        assertTrue(packageTargets.packageTargets != null)

        assertFails<IllegalArgumentException> {
            R2NodeResolutionV2Authority(
                snapshotTargets = metadataOnly.snapshotTargets,
                packageMetadata = metadataOnly.packageMetadata,
            )
        }
        assertFails<IllegalArgumentException> {
            R2NodeResolutionV2Authority(
                snapshotTargets = noMetadata.snapshotTargets,
                packageTargets = packageTargets.packageTargets,
            )
        }
    }

    @Test
    fun invalidOrGhostContainingPathReturnsStableFailureOutcome() {
        val context = authority(null, null, listOf("/sources/dep.ts"))
        listOf(
            "/sources/main.MTS",
            "/sources/ghost.ts",
            "/outside/main.ts",
        ).forEach { path ->
            val outcome = resolve(context, path, "./dep.js")
            assertEquals(R2NodeResolutionFailureCode.INVALID_CONTAINING_PATH, failure(outcome).code)
            assertNull(outcome.evidence.impliedModuleFormat)
            assertNull(outcome.evidence.conditionMode)
        }
    }

    @Test
    fun revisionOneKernelAndPolicyRemainFrozenAndRejectEveryNewKind() {
        val context = authority(
            projectJson = "{\"type\":\"module\"}",
            packageJson = "{\"exports\":\"./entry.mjs\"}",
            targetPaths = listOf("packages/pkg/entry.mts"),
        )
        val packageMetadata = requireNotNull(context.packageMetadata)
        listOf(
            R2NodeResolutionTarget("/packages/pkg/a.mts", R2NodeResolutionTargetKind.PROJECT_SOURCE),
            R2NodeResolutionTarget("/packages/pkg/a.cts", R2NodeResolutionTargetKind.PROJECT_SOURCE),
            R2NodeResolutionTarget("/packages/pkg/a.mjs", R2NodeResolutionTargetKind.PROJECT_SOURCE),
            R2NodeResolutionTarget("/packages/pkg/a.cjs", R2NodeResolutionTargetKind.PROJECT_SOURCE),
            R2NodeResolutionTarget("/packages/pkg/a.d.mts", R2NodeResolutionTargetKind.DECLARATION),
            R2NodeResolutionTarget("/packages/pkg/a.d.cts", R2NodeResolutionTargetKind.DECLARATION),
        ).forEach { target ->
            val v1 = R2NodeResolutionKernel.resolve(
                request = R2NodeResolutionRequest(
                    containingPath = "/sources/main.ts",
                    specifier = "pkg",
                    mode = R2NodeResolutionMode.IMPORT,
                ),
                packageMetadata = packageMetadata,
                targets = listOf(target),
            )
            assertEquals(R2NodeResolutionFailureCode.UNSUPPORTED_SOURCE_KIND, failure(v1).code)
        }
        val mixed = R2NodeResolutionKernel.resolve(
            request = R2NodeResolutionRequest(
                containingPath = "/sources/main.ts",
                specifier = "pkg",
                mode = R2NodeResolutionMode.IMPORT,
            ),
            packageMetadata = packageMetadata,
            targets = listOf(
                R2NodeResolutionTarget(
                    "/packages/pkg/a.ts",
                    R2NodeResolutionTargetKind.PROJECT_SOURCE,
                ),
                R2NodeResolutionTarget(
                    "/packages/pkg/b.mts",
                    R2NodeResolutionTargetKind.PROJECT_SOURCE,
                ),
            ),
        )
        assertEquals(R2NodeResolutionFailureCode.UNSUPPORTED_SOURCE_KIND, failure(mixed).code)

        val v1Declaration = authority(
            projectJson = "{}",
            packageJson = "{\"exports\":\"./entry.js\"}",
            targetPaths = listOf("packages/pkg/entry.d.ts"),
        )
        val legacyResult = R2NodeResolutionKernel.resolve(
            request = R2NodeResolutionRequest(
                containingPath = "/sources/main.ts",
                specifier = "pkg",
                mode = R2NodeResolutionMode.REQUIRE,
            ),
            packageMetadata = requireNotNull(v1Declaration.packageMetadata),
            targets = requireNotNull(v1Declaration.packageTargets).toResolutionTargets(),
        )
        assertEquals("/packages/pkg/entry.d.ts", resolved(legacyResult).target.path)
        assertEquals(
            R2NodeResolutionFailureCode.TARGET_NOT_IN_SNAPSHOT,
            failure(resolve(v1Declaration, "/sources/main.ts")).code,
        )
        assertEquals(V1_POLICY_FINGERPRINT, R2PackageTargetPolicy.fingerprint)
        assertEquals(18, R2NodeResolutionFailureCode.entries.size)
    }

    @Test
    fun exactAuthorityRejectsProfileAndCatalogIdentityDrift() {
        val first = authority(
            projectJson = "{}",
            packageJson = "{\"exports\":\"./a.mjs\"}",
            targetPaths = listOf("packages/pkg/a.mts"),
        )
        val second = authority(
            projectJson = "{\"type\":\"module\"}",
            packageJson = "{\"exports\":\"./b.mjs\"}",
            targetPaths = listOf("packages/pkg/b.mts"),
        )
        assertFails<IllegalArgumentException> {
            R2NodeResolutionV2Authority(
                snapshotTargets = first.snapshotTargets,
                packageMetadata = first.packageMetadata,
                packageFormat = second.packageFormat,
                packageTargets = first.packageTargets,
            )
        }
        assertFails<IllegalArgumentException> {
            R2NodeResolutionV2Authority(
                snapshotTargets = first.snapshotTargets,
                packageMetadata = first.packageMetadata,
                packageFormat = first.packageFormat,
                packageTargets = first.packageTargets,
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
            )
        }
    }

    @Test
    fun resolverV2EvidenceBindsEveryPureAuthorityWithoutProgramOrHostState() {
        val context = authority(
            projectJson = "{\"type\":\"module\"}",
            packageJson = "{\"exports\":\"./entry.mjs\"}",
            targetPaths = listOf("packages/pkg/entry.mts"),
        )
        val outcome = resolve(context, "/sources/main.mts")
        assertEquals(2, outcome.evidence.profileRevision)
        assertEquals(2, outcome.evidence.resolverPolicyRevision)
        assertEquals(
            context.snapshotTargets.fingerprint,
            outcome.evidence.snapshotTargetInventoryFingerprint,
        )
        assertEquals(2, outcome.evidence.packageTargetPolicyRevision)
        assertEquals(
            R2NodePackageTargetPolicyV2.fingerprint,
            outcome.evidence.packageTargetPolicyFingerprint,
        )
        assertEquals(
            requireNotNull(context.packageMetadata).inventoryFingerprint,
            outcome.evidence.packageMetadataInventoryFingerprint,
        )
        assertEquals(
            requireNotNull(context.packageFormat).fingerprint,
            outcome.evidence.packageFormatFingerprint,
        )
        assertEquals(
            requireNotNull(context.packageTargets).fingerprint,
            outcome.evidence.packageTargetInventoryFingerprint,
        )
        assertEquals(context.targetUniverseFingerprint, outcome.evidence.targetUniverseFingerprint)
        assertTrue(context.targetUniverseCanonicalInput.contains(
            "packageTargetPolicyFingerprint=${R2NodePackageTargetPolicyV2.fingerprint}",
        ))
        assertTrue(context.targetUniverseCanonicalInput.contains("/sources/main.mts"))
        assertFalse(context.targetUniverseCanonicalInput.contains("Program"))
        assertTrue(resolved(outcome).matchedConditions.isEmpty())
    }

    @Test
    fun sharedTypeScript603RevisionTwoParityFixtureMatchesPureKernel() {
        val bytes = requireNotNull(
            javaClass.getResourceAsStream("/r2-node-resolution/parity-v2.json"),
        ).use { input -> input.readBytes() }
        val fixture = R2StrictJson.parseObject(bytes)
        assertEquals(
            setOf(
                "schema",
                "revision",
                "resolverPolicyRevision",
                "typescriptVersion",
                "scope",
                "cases",
            ),
            fixture.keys,
        )
        assertEquals("autojs6.typescript.node-resolution-parity", fixture["schema"])
        assertNumber(2, fixture["revision"])
        assertNumber(2, fixture["resolverPolicyRevision"])
        assertEquals("6.0.3", fixture["typescriptVersion"])

        val cases = fixture.requireList("cases")
        var supported = 0
        var failClosed = 0
        val ids = HashSet<String>()
        cases.forEach { rawCase ->
            val testCase = rawCase.requireObject("case")
            assertEquals(
                setOf(
                    "id",
                    "usage",
                    "classification",
                    "projectJson",
                    "packageJson",
                    "containingPath",
                    "specifier",
                    "targetPaths",
                    "expectedTypeScriptTarget",
                    "expectedV2",
                ),
                testCase.keys,
            )
            val id = testCase.requireString("id")
            assertTrue("Duplicate P2-5 parity id: $id", ids.add(id))
            when (testCase.requireString("classification")) {
                "supported" -> supported += 1
                "fail-closed" -> failClosed += 1
                else -> fail("$id has an invalid parity classification.")
            }
            val containingPath = testCase.requireString("containingPath")
            val context = authority(
                projectJson = testCase.optionalString("projectJson"),
                packageJson = testCase.optionalString("packageJson"),
                targetPaths = testCase.requireStringList("targetPaths"),
                additionalSnapshotPaths = listOf(containingPath),
            )
            val request = R2NodeResolutionV2Request(
                containingPath = containingPath,
                specifier = testCase.requireString("specifier"),
            )
            val outcome: R2NodeResolutionV2Outcome = when (testCase.requireString("usage")) {
                "module" -> R2NodeResolutionKernelV2.resolve(request, context)
                "type-reference" -> R2NodeResolutionKernelV2.resolveTypeReference(request, context)
                else -> throw AssertionError("$id has an invalid resolver usage.")
            }
            val expected = testCase.getValue("expectedV2").requireObject("expectedV2")
            assertEquals(
                expected.requireString("moduleFormat"),
                requireNotNull(outcome.evidence.impliedModuleFormat).contractValue,
            )
            assertEquals(
                expected.requireString("conditionMode"),
                requireNotNull(outcome.evidence.conditionMode).contractValue,
            )
            when (expected.requireString("kind")) {
                "resolved" -> assertEquals(
                    expected.requireString("target"),
                    resolved(outcome).target.path,
                )
                "failure" -> assertEquals(
                    expected.requireString("code"),
                    failure(outcome).code.contractValue,
                )
                else -> fail("$id has an invalid P2-5 result kind.")
            }
        }
        assertEquals(18, cases.size)
        assertEquals(14, supported)
        assertEquals(4, failClosed)
    }

    private fun resolve(
        authority: R2NodeResolutionV2Authority,
        containingPath: String,
        specifier: String = "pkg",
    ): R2NodeResolutionV2Outcome = R2NodeResolutionKernelV2.resolve(
        request = R2NodeResolutionV2Request(containingPath, specifier),
        authority = authority,
    )

    private fun authority(
        projectJson: String?,
        packageJson: String?,
        targetPaths: List<String>,
        additionalSnapshotPaths: List<String> = emptyList(),
    ): R2NodeResolutionV2Authority {
        val ordinaryTargetPaths = targetPaths.filter { path -> path.startsWith('/') }
        val packageTargetPaths = targetPaths.filterNot { path -> path.startsWith('/') }
        require(packageTargetPaths.all { path -> path.startsWith("packages/") })
        val snapshotFiles = (
            DEFAULT_SNAPSHOT_PATHS + ordinaryTargetPaths +
                additionalSnapshotPaths.filter { path -> !path.startsWith("/packages/") }
            ).distinct().sorted().map(::projectFile)
        val snapshotTargets = snapshotTargets(snapshotFiles)

        if (projectJson == null) {
            require(packageJson == null && packageTargetPaths.isEmpty())
            return R2NodeResolutionV2Authority(snapshotTargets = snapshotTargets)
        }

        val bodiesByPath = linkedMapOf(
            "package.json" to projectJson.toByteArray(StandardCharsets.UTF_8),
        )
        if (packageJson != null) {
            bodiesByPath["packages/pkg/package.json"] =
                packageJson.toByteArray(StandardCharsets.UTF_8)
        }
        val descriptors = bodiesByPath.map { (path, bytes) ->
            R2PackageMetadataFileDescriptor(
                path = path,
                size = bytes.size.toLong(),
                sha256 = R2Digests.sha256(bytes),
            )
        }
        val inventory = R2PackageMetadataInventory.create(
            dependencyRoots = if (packageJson == null) emptyList() else listOf("packages/pkg"),
            files = descriptors,
        )
        val bodies = descriptors.map { descriptor ->
            checkNotNull(bodiesByPath[descriptor.path]).copyOf()
        }
        val metadataCatalog = R2NodePackageMetadataCatalog.create(inventory, bodies)
        val formatCatalog = R2NodePackageFormatCatalog.create(inventory, bodies)
        bodies.forEach { bytes -> bytes.fill(0) }
        val packageTargets = if (packageTargetPaths.isEmpty()) {
            null
        } else {
            require(packageJson != null)
            R2NodePackageTargetV2Inventory.create(
                packageMetadataInventory = inventory,
                files = packageTargetPaths.sorted().map { path ->
                    R2NodePackageTargetV2FileDescriptor(path, 0, EMPTY_SHA256)
                },
            )
        }
        return R2NodeResolutionV2Authority(
            snapshotTargets = snapshotTargets,
            packageMetadata = metadataCatalog,
            packageFormat = formatCatalog,
            packageTargets = packageTargets,
        )
    }

    private fun snapshotTargets(
        files: List<R2ProjectFileMetadata>,
    ): R2NodeResolutionV2SnapshotTargetInventory =
        R2NodeResolutionV2SnapshotTargetInventory.create(
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            files = files,
        )

    private fun projectFile(
        path: String,
        sha256: String = EMPTY_SHA256,
        utf8Bytes: Long = 0,
    ) = R2ProjectFileMetadata(
        path = path,
        utf8Bytes = utf8Bytes,
        utf16CodeUnits = 0,
        sha256 = sha256,
    )

    private fun resolved(outcome: R2NodeResolutionV2Outcome): R2NodeResolutionResult.Resolved =
        resolved(outcome.result)

    private fun resolved(result: R2NodeResolutionResult): R2NodeResolutionResult.Resolved {
        if (result is R2NodeResolutionResult.Resolved) return result
        fail("Expected resolved result, got $result.")
        error("unreachable")
    }

    private fun failure(outcome: R2NodeResolutionV2Outcome): R2NodeResolutionResult.Failure =
        failure(outcome.result)

    private fun failure(result: R2NodeResolutionResult): R2NodeResolutionResult.Failure {
        if (result is R2NodeResolutionResult.Failure) return result
        fail("Expected failure result, got $result.")
        error("unreachable")
    }

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        fail("Expected ${T::class.java.name}.")
        error("unreachable")
    }

    private fun Map<String, Any?>.requireString(key: String): String =
        getValue(key) as? String ?: throw AssertionError("'$key' is not a string.")

    private fun Map<String, Any?>.optionalString(key: String): String? {
        val value = getValue(key)
        return if (value == null) null else value as? String
            ?: throw AssertionError("'$key' is neither null nor a string.")
    }

    private fun Map<String, Any?>.requireList(key: String): List<*> =
        getValue(key) as? List<*> ?: throw AssertionError("'$key' is not an array.")

    private fun Map<String, Any?>.requireStringList(key: String): List<String> =
        requireList(key).map { value ->
            value as? String ?: throw AssertionError("'$key' contains a non-string.")
        }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.requireObject(label: String): Map<String, Any?> =
        this as? Map<String, Any?> ?: throw AssertionError("'$label' is not an object.")

    private fun assertNumber(expected: Int, actual: Any?) {
        val token = actual as? R2StrictJson.NumberToken
            ?: throw AssertionError("Expected number $expected, got $actual.")
        assertEquals(expected.toString(), token.source)
    }

    private companion object {
        const val EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        const val ONE_BYTE_SHA256 =
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb"
        const val V1_POLICY_FINGERPRINT =
            "e34f4825d8f75ba1b674849ff79c2be8f9c1b88fe940add86e6e466a139bbbaf"
        val DEFAULT_SNAPSHOT_PATHS = listOf(
            "/declarations/lib.d.ts",
            "/sources/main.cts",
            "/sources/main.mts",
            "/sources/main.ts",
        )
    }
}
