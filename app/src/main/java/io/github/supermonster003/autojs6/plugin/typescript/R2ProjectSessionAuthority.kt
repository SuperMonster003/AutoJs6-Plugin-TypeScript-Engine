package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/**
 * Trusted, service-derived identity for one opt-in resident compiler session.
 *
 * The raw project ID and Binder UID never cross into the WebView realm. The Worker sees only a
 * domain-separated SHA-256 identity, which also keeps equal project IDs from different callers
 * in disjoint cache namespaces.
 */
internal data class R2ProjectSessionAuthority(
    val revision: Int,
    val key: String,
) {

    init {
        require(revision == TypeScriptR2Contract.PROJECT_SESSION_REVISION) {
            "Project session authority has an unsupported revision."
        }
        R2Digests.requireLowerSha256(key, "Project session key")
    }

    companion object {

        fun derive(
            callingUid: Int,
            projectId: String,
            revision: Int,
        ): R2ProjectSessionAuthority {
            require(callingUid >= 0) { "Project session calling UID is invalid." }
            require(
                projectId.isNotBlank() &&
                    projectId.length <= TypeScriptCompilerContract.MAX_PROJECT_ID_CHARS,
            ) {
                "Project session project ID is invalid."
            }
            require(revision == TypeScriptR2Contract.PROJECT_SESSION_REVISION) {
                "Project session revision is not supported."
            }
            val canonical = buildString {
                append(CANONICAL_PREFIX)
                append(revision)
                append('\n')
                append(callingUid)
                append('\n')
                append(projectId)
            }.toByteArray(StandardCharsets.UTF_8)
            return R2ProjectSessionAuthority(
                revision = revision,
                key = R2Digests.sha256(canonical),
            )
        }

        private const val CANONICAL_PREFIX =
            "autojs6.typescript.project-session.v1\n"
    }
}
