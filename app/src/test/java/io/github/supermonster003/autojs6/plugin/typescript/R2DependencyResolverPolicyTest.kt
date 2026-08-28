package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2DependencyResolverPolicyTest {

    @Test
    fun revisionThreeProjectsTheFrozenArchiveLayerReversibly() {
        val archivePaths = listOf(
            "dependencies/lockfiles/package-lock.json",
            "dependencies/node_modules/dayjs/package.json",
            "dependencies/node_modules/@scope/pkg/subpath.d.mts",
        )

        archivePaths.forEach { archivePath ->
            val workerPath = R2DependencyResolverPolicy.toWorkerVfsPath(archivePath)
            assertEquals(
                archivePath,
                R2DependencyResolverPolicy.toArchivePath(workerPath),
            )
        }
        assertEquals(
            "/node_modules/dayjs/package.json",
            R2DependencyResolverPolicy.toWorkerVfsPath(
                "dependencies/node_modules/dayjs/package.json",
            ),
        )
        assertEquals(
            "/dependency-lockfiles/package-lock.json",
            R2DependencyResolverPolicy.toWorkerVfsPath(
                "dependencies/lockfiles/package-lock.json",
            ),
        )
    }

    @Test
    fun resolverIdentityIsPublicAndIncludesTypesAndRuntimeLoading() {
        assertEquals(
            TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_REVISION,
            R2DependencyResolverPolicy.POLICY_REVISION,
        )
        assertEquals(
            TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_FINGERPRINT,
            R2DependencyResolverPolicy.fingerprint,
        )
        assertTrue(R2DependencyResolverPolicy.canonicalInput.contains("types,import,require"))
        assertTrue(R2DependencyResolverPolicy.canonicalInput.contains("typesVersions,types,typings"))
        assertTrue(R2DependencyResolverPolicy.canonicalInput.contains("ambientTypes=installed-@types"))
        assertTrue(
            R2DependencyResolverPolicy.canonicalInput.contains(
                "moduleTypeFallback=runtime-js-to-installed-@types",
            ),
        )
        assertTrue(R2DependencyResolverPolicy.canonicalInput.contains("runtimeLoading=exact-readonly-t2-3"))
        assertThrows(IllegalArgumentException::class.java) {
            R2DependencyResolverPolicy.toArchivePath("/sources/main.mts")
        }
    }
}
