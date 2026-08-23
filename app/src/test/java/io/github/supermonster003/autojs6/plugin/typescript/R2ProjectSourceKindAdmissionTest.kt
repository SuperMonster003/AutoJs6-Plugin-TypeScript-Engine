package io.github.supermonster003.autojs6.plugin.typescript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2ProjectSourceKindAdmissionTest {

    @Test
    fun activatedConfigOverlayRebindsTheEscapeAuthority() {
        assertEquals(2, R2CompilerEscapePolicy.REVISION)
        assertEquals(
            "4f42fdf38782fd8d94b40a1bf5797295698b9ba24ca0f555c30186e4b43a1e98",
            R2CompilerEscapePolicy.fingerprint,
        )
    }

    @Test
    fun exactProfileMatrixOpensOnlyNodeRevisionTwoMtsAndCtsExecutables() {
        val profiles = listOf(
            R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        )

        profiles.forEach { profile ->
            assertTrue(
                R2ProjectSourceKindAdmission.isExecutableSourcePath(
                    "/sources/nested/main.ts",
                    profile,
                ),
            )
            assertTrue(
                R2ProjectSourceKindAdmission.isExecutableSourcePath(
                    "sources/nested/main.ts",
                    profile,
                ),
            )
            val opensNodeKinds =
                profile == R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
            listOf("mts", "cts").forEach { extension ->
                assertEquals(
                    opensNodeKinds,
                    R2ProjectSourceKindAdmission.isExecutableSourcePath(
                        "/sources/nested/main.$extension",
                        profile,
                    ),
                )
                assertEquals(
                    opensNodeKinds,
                    R2ProjectSourceKindAdmission.isExecutableSourcePath(
                        "sources/nested/main.$extension",
                        profile,
                    ),
                )
            }
        }
    }

    @Test
    fun nodeRevisionTwoOverlayOnlyLiftsExactLowercaseExecutableDeferredKinds() {
        val nodeRevisionTwo =
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2

        listOf(
            "/sources/main.mts",
            "/sources/nested/main.cts",
            "sources/main.mts",
            "sources/nested/main.cts",
        ).forEach { path ->
            assertNull(
                path,
                R2ProjectSourceKindAdmission.sourcePathRejection(path, nodeRevisionTwo),
            )
            assertTrue(
                path,
                R2ProjectSourceKindAdmission.isExecutableSourcePath(path, nodeRevisionTwo),
            )
        }

        listOf(
            "/sources/main.MTS",
            "/sources/main.Mts",
            "/sources/main.CTS",
            "/sources/main.Cts",
            "/sources/types.d.mts",
            "/sources/types.d.cts",
            "/declarations/types.d.mts",
            "/declarations/types.d.cts",
            "/outside/main.mts",
            "outside/main.cts",
        ).forEach { path ->
            assertEquals(
                path,
                R2CompilerEscapeSourcePathRejection.DEFERRED_SOURCE_KIND,
                R2ProjectSourceKindAdmission.sourcePathRejection(path, nodeRevisionTwo),
            )
            assertFalse(
                path,
                R2ProjectSourceKindAdmission.isExecutableSourcePath(path, nodeRevisionTwo),
            )
        }
    }

    @Test
    fun packageAndDependencyEscapeRejectionsRemainClosedForEveryProfile() {
        val cases = mapOf(
            "/sources/package.json" to
                R2CompilerEscapeSourcePathRejection.PACKAGE_METADATA,
            "sources/Package.Json" to
                R2CompilerEscapeSourcePathRejection.PACKAGE_METADATA,
            "/sources/node_modules/pkg/main.mts" to
                R2CompilerEscapeSourcePathRejection.PACKAGE_DEPENDENCY_TREE,
            "sources/Node_Modules/pkg/main.cts" to
                R2CompilerEscapeSourcePathRejection.PACKAGE_DEPENDENCY_TREE,
        )

        R2CompilationProfileRegistry.registeredAuthorities.forEach { profile ->
            cases.forEach { (path, expected) ->
                assertEquals(
                    path,
                    expected,
                    R2ProjectSourceKindAdmission.sourcePathRejection(path, profile),
                )
                assertFalse(
                    path,
                    R2ProjectSourceKindAdmission.isExecutableSourcePath(path, profile),
                )
            }
        }
    }

    @Test
    fun driftedNodeRevisionTwoAuthorityCannotOpenNewSourceKinds() {
        val drifted = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2.copy(
            configKind = "drifted-none",
        )

        assertEquals(
            R2CompilerEscapeSourcePathRejection.DEFERRED_SOURCE_KIND,
            R2ProjectSourceKindAdmission.sourcePathRejection(
                "/sources/main.mts",
                drifted,
            ),
        )
        assertFalse(
            R2ProjectSourceKindAdmission.isExecutableSourcePath(
                "/sources/main.mts",
                drifted,
            ),
        )
    }
}
