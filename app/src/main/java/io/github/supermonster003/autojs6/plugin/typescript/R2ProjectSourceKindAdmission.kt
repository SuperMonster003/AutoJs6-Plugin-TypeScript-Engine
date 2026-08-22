package io.github.supermonster003.autojs6.plugin.typescript

/**
 * Profile-aware source-kind overlay on the frozen R2-M2-C4 escape policy.
 *
 * Archive callers use relative `sources/...` paths while snapshots use absolute
 * `/sources/...` paths. Only the exact registered Node revision-2 authority opens the
 * executable `.mts` and `.cts` kinds; every other C4 rejection remains unchanged.
 */
internal object R2ProjectSourceKindAdmission {

    fun sourcePathRejection(
        path: String,
        profileAuthority: R2CompilationProfileAuthority,
    ): R2CompilerEscapeSourcePathRejection? {
        val rejection = R2CompilerEscapePolicy.sourcePathRejection(path)
        return if (
            rejection == R2CompilerEscapeSourcePathRejection.DEFERRED_SOURCE_KIND &&
            isExactNodeRevisionTwo(profileAuthority) &&
            path.isNodeRevisionTwoExecutableSourcePath()
        ) {
            null
        } else {
            rejection
        }
    }

    fun isExecutableSourcePath(
        path: String,
        profileAuthority: R2CompilationProfileAuthority,
    ): Boolean {
        if (
            !path.isBelowSourcesRoot() ||
            sourcePathRejection(path, profileAuthority) != null
        ) {
            return false
        }
        return when {
            path.endsWith(".d.ts") ||
                path.endsWith(".d.mts") ||
                path.endsWith(".d.cts") -> false
            path.endsWith(".ts") -> true
            isExactNodeRevisionTwo(profileAuthority) &&
                (path.endsWith(".mts") || path.endsWith(".cts")) -> true
            else -> false
        }
    }

    private fun isExactNodeRevisionTwo(
        profileAuthority: R2CompilationProfileAuthority,
    ): Boolean =
        profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2

    private fun String.isNodeRevisionTwoExecutableSourcePath(): Boolean =
        isBelowSourcesRoot() &&
            !endsWith(".d.mts") &&
            !endsWith(".d.cts") &&
            (endsWith(".mts") || endsWith(".cts"))

    private fun String.isBelowSourcesRoot(): Boolean =
        startsWith("/sources/") || startsWith("sources/")
}
