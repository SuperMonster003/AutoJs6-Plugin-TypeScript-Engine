package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/**
 * T2-6 compiler/editor resolver authority layered on dependency-layer policy revision 2.
 *
 * Dependency files remain in their exact T2-1 inventory. This policy defines the reversible
 * Worker projection, package/type lookup behavior, and the exact T2-3 runtime snapshot binding.
 */
internal object R2DependencyResolverPolicy {
    const val POLICY_REVISION = TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_REVISION
    const val NODE_MODULES_VFS_PREFIX = "/node_modules/"
    const val LOCKFILES_VFS_PREFIX = "/dependency-lockfiles/"
    const val READ_MODE = "immutable-file-backed-framed-readonly-t2-2"

    val canonicalInput: String =
        "autojs6.typescript.dependency-resolver-policy.v4" +
            "|dependencyLayerPolicyFingerprint=${R2DependencyLayerPolicy.fingerprint}" +
            "|vfsRoot=$NODE_MODULES_VFS_PREFIX" +
            "|conditionOrder=types,import,require,node,default" +
            "|fallbackOrder=typesVersions,types,typings,installed-@types,module,main,index" +
            "|typeReferences=package-types,typings,@types,typesVersions" +
            "|ambientTypes=installed-@types" +
            "|moduleTypeFallback=runtime-js-to-installed-@types" +
            "|runtimeLoading=exact-readonly-t2-3" +
            "|nativeBoundary=native-addon-and-install-lifecycle-rejected-t2-6"

    val fingerprint: String = R2Digests.sha256(
        canonicalInput.toByteArray(StandardCharsets.US_ASCII),
    )

    init {
        check(POLICY_REVISION == 4)
        check(canonicalInput.all { character -> character.code in ' '.code..'~'.code })
        check(
            R2Digests.equal(
                fingerprint,
                TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_FINGERPRINT,
            ),
        ) {
            "Dependency resolver policy fingerprint drifted from the public contract."
        }
    }

    fun toWorkerVfsPath(archivePath: String): String {
        R2DependencyLayerPolicy.requireArchivePath(archivePath)
        return when {
            archivePath.startsWith(R2DependencyLayerPolicy.DEPENDENCY_ARCHIVE_PREFIX) ->
                NODE_MODULES_VFS_PREFIX +
                    archivePath.removePrefix(R2DependencyLayerPolicy.DEPENDENCY_ARCHIVE_PREFIX)
            archivePath.startsWith(R2DependencyLayerPolicy.LOCKFILE_ARCHIVE_PREFIX) ->
                LOCKFILES_VFS_PREFIX +
                    archivePath.removePrefix(R2DependencyLayerPolicy.LOCKFILE_ARCHIVE_PREFIX)
            else -> error("Dependency-layer archive path has no Worker projection.")
        }
    }

    fun toArchivePath(workerVfsPath: String): String = when {
        workerVfsPath.startsWith(NODE_MODULES_VFS_PREFIX) ->
            R2DependencyLayerPolicy.DEPENDENCY_ARCHIVE_PREFIX +
                workerVfsPath.removePrefix(NODE_MODULES_VFS_PREFIX)
        workerVfsPath.startsWith(LOCKFILES_VFS_PREFIX) ->
            R2DependencyLayerPolicy.LOCKFILE_ARCHIVE_PREFIX +
                workerVfsPath.removePrefix(LOCKFILES_VFS_PREFIX)
        else -> throw IllegalArgumentException(
            "Worker dependency path is outside the read-only projection: $workerVfsPath.",
        )
    }.also(R2DependencyLayerPolicy::requireArchivePath)
}
