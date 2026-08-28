package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.LinkedHashMap
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/**
 * Immutable revision-4 `baseUrl`/`paths` authority.
 *
 * Values remain project-relative here. The Worker is the only component that expands them into
 * `/sources/...` compiler paths, resolves them against the accepted snapshot, and rewrites emitted
 * module specifiers. Keeping this value independent from Android filesystem paths also makes it a
 * reusable input for the editor-side resolver planned by T4.
 */
internal class R2ControlledPathAliasTable private constructor(
    val baseUrl: String?,
    paths: Map<String, List<String>>,
) {

    val paths: Map<String, List<String>> = immutablePaths(paths)

    init {
        require(baseUrl == null || isValidBaseUrl(baseUrl)) {
            "Controlled tsconfig baseUrl is not a canonical project-relative directory."
        }
        require(baseUrl != null || this.paths.isEmpty()) {
            "Controlled tsconfig paths require an effective baseUrl."
        }
        require(this.paths.size <= TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_PATH_ALIASES) {
            "Controlled tsconfig contains too many path aliases."
        }
        var totalTargets = 0
        this.paths.forEach { (key, targets) ->
            require(isValidAliasKey(key)) {
                "Controlled tsconfig contains an invalid path-alias key."
            }
            require(
                targets.isNotEmpty() &&
                    targets.size <=
                    TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_PATH_TARGETS_PER_ALIAS,
            ) {
                "Controlled tsconfig path alias has an invalid target count."
            }
            require(targets == targets.distinct()) {
                "Controlled tsconfig path-alias targets are not canonical."
            }
            val keyWildcards = key.count { character -> character == '*' }
            targets.forEach { target ->
                require(
                    isValidTargetPattern(target) &&
                        target.count { character -> character == '*' } == keyWildcards,
                ) {
                    "Controlled tsconfig contains an invalid path-alias target."
                }
            }
            totalTargets = Math.addExact(totalTargets, targets.size)
        }
        require(totalTargets <= TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_PATH_TARGETS) {
            "Controlled tsconfig contains too many path-alias targets."
        }
    }

    companion object {

        val EMPTY = R2ControlledPathAliasTable(null, emptyMap())

        fun normalize(
            baseUrl: String?,
            paths: Map<String, List<String>>,
        ): R2ControlledPathAliasTable {
            val effectiveBaseUrl = baseUrl ?: R2ControlledTsConfigPolicy
                .PATH_ALIAS_BASE_URL_DEFAULT
                .takeIf { paths.isNotEmpty() }
            return R2ControlledPathAliasTable(effectiveBaseUrl, paths)
        }

        private fun immutablePaths(
            values: Map<String, List<String>>,
        ): Map<String, List<String>> {
            val copy = LinkedHashMap<String, List<String>>(values.size)
            values.entries.sortedBy { entry -> entry.key }.forEach { (key, targets) ->
                copy[key] = Collections.unmodifiableList(ArrayList(targets))
            }
            return Collections.unmodifiableMap(copy)
        }

        private fun isValidBaseUrl(value: String): Boolean =
            value == R2ControlledTsConfigPolicy.PATH_ALIAS_BASE_URL_DEFAULT ||
                isValidRelativePattern(value, allowWildcard = false)

        private fun isValidAliasKey(value: String): Boolean =
            value.isNotEmpty() &&
                !value.startsWith('.') &&
                !value.startsWith('/') &&
                isValidPatternBytes(value) &&
                value.count { character -> character == '*' } <= 1 &&
                value.split('/').all(::isValidPatternSegment)

        private fun isValidTargetPattern(value: String): Boolean =
            isValidRelativePattern(value, allowWildcard = true)

        private fun isValidRelativePattern(
            value: String,
            allowWildcard: Boolean,
        ): Boolean =
            value.isNotEmpty() &&
                !value.startsWith('/') &&
                isValidPatternBytes(value) &&
                (allowWildcard || '*' !in value) &&
                value.count { character -> character == '*' } <= 1 &&
                value.split('/').all(::isValidPatternSegment)

        private fun isValidPatternBytes(value: String): Boolean =
            value.toByteArray(StandardCharsets.UTF_8).size <=
                TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_PATH_PATTERN_UTF8_BYTES &&
                value.all { character ->
                    character.code in 0x21..0x7e &&
                        character != '\\' &&
                        character != ':' &&
                        character != '?' &&
                        character != '#'
                }

        private fun isValidPatternSegment(segment: String): Boolean =
            segment.isNotEmpty() &&
                segment != "." &&
                segment != ".." &&
                segment.all { character ->
                    character.isLetterOrDigit() ||
                        character == '@' ||
                        character == '$' ||
                        character == '_' ||
                        character == '-' ||
                        character == '.' ||
                        character == '~' ||
                        character == '*'
                }
    }
}
