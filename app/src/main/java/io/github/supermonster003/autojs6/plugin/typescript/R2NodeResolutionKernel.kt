package io.github.supermonster003.autojs6.plugin.typescript

import java.util.Collections

/**
 * R2-M2-C6-A snapshot-only kernel with the C6-B TypeScript parity envelope.
 *
 * This contract has no TypeScript Program, compiler-host, host-filesystem or network access. An
 * exports/imports target is successful only when it exactly names an accepted [targets] entry, or
 * when a mapped `.js` target deterministically substitutes to an accepted `.ts` then `.d.ts` entry.
 * The P2-5 entry point below selects a separate target-kind/substitution policy without changing
 * that frozen v1 behavior. Production Worker/Program wiring remains closed until P2-6.
 */
internal object R2NodeResolutionKernel {

    fun resolve(
        request: R2NodeResolutionRequest,
        packageMetadata: R2NodePackageMetadataCatalog,
        targets: List<R2NodeResolutionTarget>,
    ): R2NodeResolutionResult = resolveWithTargetIndex(
        request = request,
        packageMetadata = packageMetadata,
        targetIndex = TargetIndex.create(targets),
    )

    /** P2-5 pure entry point. Production Worker/Program wiring remains closed until P2-6. */
    internal fun resolveVersionTwo(
        request: R2NodeResolutionRequest,
        packageMetadata: R2NodePackageMetadataCatalog?,
        targets: List<R2NodeResolutionTarget>,
    ): R2NodeResolutionResult = resolveWithTargetIndex(
        request = request,
        packageMetadata = packageMetadata,
        targetIndex = TargetIndex.createVersionTwo(targets),
    )

    private fun resolveWithTargetIndex(
        request: R2NodeResolutionRequest,
        packageMetadata: R2NodePackageMetadataCatalog?,
        targetIndex: TargetIndex,
    ): R2NodeResolutionResult {
        val conditions = ArrayList(request.conditions)
        requestFailure(request, conditions)?.let { return it }
        targetIndex.failure?.let { return it }
        return Session(
            packageMetadata = packageMetadata,
            targetIndex = targetIndex,
            activeConditions = linkedSetOf(request.mode.condition).apply {
                add(TYPES_CONDITION)
                add(NODE_CONDITION)
                addAll(conditions)
            },
        ).resolve(
            specifier = request.specifier,
            containingPath = request.containingPath,
        )
    }

    private fun requestFailure(
        request: R2NodeResolutionRequest,
        conditions: List<String>,
    ): R2NodeResolutionResult.Failure? {
        if (!request.containingPath.isCanonicalTargetPath()) {
            return failure(R2NodeResolutionFailureCode.INVALID_CONTAINING_PATH)
        }
        if (!request.specifier.isCanonicalSpecifier()) {
            return failure(R2NodeResolutionFailureCode.INVALID_SPECIFIER)
        }
        if (
            conditions.distinct().size != conditions.size ||
            conditions.any { condition -> !CONDITION_PATTERN.matches(condition) }
        ) {
            return failure(R2NodeResolutionFailureCode.INVALID_CONDITION)
        }
        return null
    }

    private class Session(
        private val packageMetadata: R2NodePackageMetadataCatalog?,
        private val targetIndex: TargetIndex,
        private val activeConditions: Set<String>,
    ) {

        private val resolvingImports = LinkedHashSet<String>()

        fun resolve(
            specifier: String,
            containingPath: String,
        ): R2NodeResolutionResult {
            return when {
                specifier.startsWith('#') -> resolveImports(
                    specifier = specifier,
                    record = packageMetadata?.nearestRecord(containingPath)
                        ?: return failure(R2NodeResolutionFailureCode.PACKAGE_METADATA_MISSING),
                    depth = 0,
                )
                specifier.startsWith("./") || specifier.startsWith("../") ->
                    resolveRelative(specifier, containingPath)
                specifier.startsWith('/') || ':' in specifier ->
                    failure(R2NodeResolutionFailureCode.INVALID_SPECIFIER)
                else -> resolvePackage(specifier, depth = 0)
            }
        }

        private fun resolveRelative(
            specifier: String,
            containingPath: String,
        ): R2NodeResolutionResult {
            val candidate = resolveRelativePath(
                boundary = containingPath.substringBeforeLast('/', missingDelimiterValue = "/"),
                relative = specifier,
                enforceBoundary = false,
            ) ?: return failure(R2NodeResolutionFailureCode.TARGET_ESCAPE)
            return targetIndex.resolve(
                candidate = candidate,
                packageBoundary = packageMetadata?.nearestRecord(containingPath)?.boundaryPath,
                packageMetadataPath = packageMetadata?.nearestRecord(containingPath)?.path,
                matchedKey = specifier,
                matchedConditions = emptyList(),
            )
        }

        private fun resolvePackage(
            specifier: String,
            depth: Int,
        ): R2NodeResolutionResult {
            if (depth > MAXIMUM_RECURSION_DEPTH) {
                return failure(R2NodeResolutionFailureCode.CYCLE)
            }
            val parsed = parsePackageSpecifier(specifier)
                ?: return failure(R2NodeResolutionFailureCode.INVALID_SPECIFIER)
            val dependencyRoot = "packages/${parsed.packageName}"
            val metadataCatalog = packageMetadata
                ?: return failure(R2NodeResolutionFailureCode.PACKAGE_NOT_ALLOWED)
            if (dependencyRoot !in metadataCatalog.dependencyRoots) {
                return failure(R2NodeResolutionFailureCode.PACKAGE_NOT_ALLOWED)
            }
            val record = metadataCatalog.recordAtBoundary("/$dependencyRoot")
                ?: return failure(R2NodeResolutionFailureCode.PACKAGE_METADATA_MISSING)
            if (!record.root.containsKey(EXPORTS_KEY)) {
                return failure(
                    code = R2NodeResolutionFailureCode.EXPORTS_NOT_DEFINED,
                    record = record,
                )
            }

            val exports = record.root[EXPORTS_KEY]
            val mapping = when (exports) {
                is Map<*, *> -> {
                    val objectValue = exports.asStringObject()
                        ?: return metadataFailure(record)
                    val hasSubpathKey = objectValue.keys.any { key -> key.startsWith('.') }
                    if (hasSubpathKey && objectValue.keys.any { key -> !key.startsWith('.') }) {
                        return metadataFailure(record)
                    }
                    if (hasSubpathKey) {
                        if (objectValue.keys.any { key -> !MappingKeyKind.EXPORTS.isValid(key) }) {
                            return metadataFailure(record)
                        }
                        findMapping(
                            values = objectValue,
                            requestedKey = parsed.subpath,
                            keyKind = MappingKeyKind.EXPORTS,
                        ) ?: return failure(
                            code = R2NodeResolutionFailureCode.EXPORTS_NO_MATCH,
                            record = record,
                        )
                    } else {
                        if (parsed.subpath != ".") {
                            return failure(
                                code = R2NodeResolutionFailureCode.EXPORTS_NO_MATCH,
                                record = record,
                            )
                        }
                        Mapping(
                            key = ".",
                            value = objectValue,
                            wildcard = null,
                        )
                    }
                }
                else -> {
                    if (parsed.subpath != ".") {
                        return failure(
                            code = R2NodeResolutionFailureCode.EXPORTS_NO_MATCH,
                            record = record,
                        )
                    }
                    Mapping(key = ".", value = exports, wildcard = null)
                }
            }
            return evaluateTarget(
                value = mapping.value,
                record = record,
                matchedKey = mapping.key,
                wildcard = mapping.wildcard,
                matchedConditions = emptyList(),
                importsTarget = false,
                depth = depth,
            )
        }

        private fun resolveImports(
            specifier: String,
            record: R2NodePackageMetadataRecord,
            depth: Int,
        ): R2NodeResolutionResult {
            if (depth > MAXIMUM_RECURSION_DEPTH) {
                return failure(R2NodeResolutionFailureCode.CYCLE, record)
            }
            if (!specifier.isCanonicalImportsSpecifier()) {
                return failure(R2NodeResolutionFailureCode.INVALID_SPECIFIER, record)
            }
            val cycleKey = "${record.path}|$specifier"
            if (!resolvingImports.add(cycleKey)) {
                return failure(R2NodeResolutionFailureCode.CYCLE, record)
            }
            try {
                if (!record.root.containsKey(IMPORTS_KEY)) {
                    return failure(R2NodeResolutionFailureCode.IMPORTS_NOT_DEFINED, record)
                }
                val imports = record.root[IMPORTS_KEY].asStringObject()
                    ?: return metadataFailure(record)
                if (imports.keys.any { key -> !MappingKeyKind.IMPORTS.isValid(key) }) {
                    return metadataFailure(record)
                }
                val mapping = findMapping(
                    values = imports,
                    requestedKey = specifier,
                    keyKind = MappingKeyKind.IMPORTS,
                ) ?: return failure(R2NodeResolutionFailureCode.IMPORTS_NO_MATCH, record)
                return evaluateTarget(
                    value = mapping.value,
                    record = record,
                    matchedKey = mapping.key,
                    wildcard = mapping.wildcard,
                    matchedConditions = emptyList(),
                    importsTarget = true,
                    depth = depth,
                )
            } finally {
                resolvingImports.remove(cycleKey)
            }
        }

        private fun evaluateTarget(
            value: Any?,
            record: R2NodePackageMetadataRecord,
            matchedKey: String,
            wildcard: String?,
            matchedConditions: List<String>,
            importsTarget: Boolean,
            depth: Int,
        ): R2NodeResolutionResult {
            return when (value) {
            null -> failure(
                code = R2NodeResolutionFailureCode.TARGET_BLOCKED,
                record = record,
                matchedKey = matchedKey,
                matchedConditions = matchedConditions,
            )
            is String -> resolveMappedTarget(
                target = value,
                record = record,
                matchedKey = matchedKey,
                wildcard = wildcard,
                matchedConditions = matchedConditions,
                importsTarget = importsTarget,
                depth = depth,
            )
            is List<*> -> {
                if (value.isEmpty()) {
                    metadataFailure(record, matchedKey, matchedConditions)
                } else {
                    var lastFailure: R2NodeResolutionResult.Failure? = null
                    value.forEach { candidate ->
                        when (
                            val result = evaluateTarget(
                                value = candidate,
                                record = record,
                                matchedKey = matchedKey,
                                wildcard = wildcard,
                                matchedConditions = matchedConditions,
                                importsTarget = importsTarget,
                                depth = depth,
                            )
                        ) {
                            is R2NodeResolutionResult.Resolved -> return result
                            is R2NodeResolutionResult.Failure -> {
                                if (result.code == R2NodeResolutionFailureCode.TARGET_BLOCKED) {
                                    return result
                                }
                                lastFailure = result
                            }
                        }
                    }
                    checkNotNull(lastFailure)
                }
            }
            is Map<*, *> -> {
                val conditions = value.asStringObject()
                    ?: return metadataFailure(record, matchedKey, matchedConditions)
                if (
                    conditions.isEmpty() ||
                    conditions.keys.any { condition ->
                        !CONDITION_PATTERN.matches(condition) ||
                            condition.startsWith('.') ||
                            condition.isCanonicalIntegerIndexCondition()
                    }
                ) {
                    return metadataFailure(record, matchedKey, matchedConditions)
                }
                conditions.entries.forEach { (condition, nested) ->
                    if (condition == DEFAULT_CONDITION || condition in activeConditions) {
                        return evaluateTarget(
                            value = nested,
                            record = record,
                            matchedKey = matchedKey,
                            wildcard = wildcard,
                            matchedConditions = matchedConditions + condition,
                            importsTarget = importsTarget,
                            depth = depth,
                        )
                    }
                }
                failure(
                    code = R2NodeResolutionFailureCode.CONDITION_NO_MATCH,
                    record = record,
                    matchedKey = matchedKey,
                    matchedConditions = matchedConditions,
                )
            }
                else -> metadataFailure(record, matchedKey, matchedConditions)
            }
        }

        private fun resolveMappedTarget(
            target: String,
            record: R2NodePackageMetadataRecord,
            matchedKey: String,
            wildcard: String?,
            matchedConditions: List<String>,
            importsTarget: Boolean,
            depth: Int,
        ): R2NodeResolutionResult {
            if (target.contains('*') && wildcard == null) {
                return failure(
                    R2NodeResolutionFailureCode.INVALID_TARGET,
                    record,
                    matchedKey,
                    matchedConditions,
                )
            }
            val substituted = if (wildcard == null) target else target.replace("*", wildcard)
            if (substituted.startsWith("./")) {
                val candidate = resolveRelativePath(
                    boundary = record.boundaryPath,
                    relative = substituted,
                    enforceBoundary = true,
                ) ?: return failure(
                    R2NodeResolutionFailureCode.TARGET_ESCAPE,
                    record,
                    matchedKey,
                    matchedConditions,
                )
                return targetIndex.resolve(
                    candidate = candidate,
                    packageBoundary = record.boundaryPath,
                    packageMetadataPath = record.path,
                    matchedKey = matchedKey,
                    matchedConditions = matchedConditions,
                )
            }
            if (!importsTarget) {
                return failure(
                    R2NodeResolutionFailureCode.INVALID_TARGET,
                    record,
                    matchedKey,
                    matchedConditions,
                )
            }
            if (substituted.startsWith('#')) {
                return resolveImports(substituted, record, depth + 1)
            }
            if (
                substituted.startsWith("../") ||
                substituted.startsWith('/') ||
                ':' in substituted
            ) {
                return failure(
                    R2NodeResolutionFailureCode.INVALID_TARGET,
                    record,
                    matchedKey,
                    matchedConditions,
                )
            }
            return resolvePackage(substituted, depth + 1)
        }
    }

    private class TargetIndex private constructor(
        private val exact: Map<String, R2NodeResolutionTarget>,
        private val folded: Map<String, R2NodeResolutionTarget>,
        private val versionTwo: Boolean,
        val failure: R2NodeResolutionResult.Failure?,
    ) {

        fun resolve(
            candidate: String,
            packageBoundary: String?,
            packageMetadataPath: String?,
            matchedKey: String,
            matchedConditions: List<String>,
        ): R2NodeResolutionResult {
            val candidates = if (versionTwo) {
                resolutionCandidatesVersionTwo(candidate)
            } else {
                resolutionCandidates(candidate)
            }
            candidates.forEach { resolutionCandidate ->
                val target = exact[resolutionCandidate]
                if (target != null) {
                    return R2NodeResolutionResult.Resolved(
                        target = target,
                        packageBoundary = packageBoundary,
                        packageMetadataPath = packageMetadataPath,
                        matchedKey = matchedKey,
                        matchedConditions = immutableCopy(matchedConditions),
                    )
                }
                if (folded[resolutionCandidate.asciiCaseFold()] != null) {
                    return failure(
                        code = R2NodeResolutionFailureCode.AMBIGUOUS_CASE,
                        packageBoundary = packageBoundary,
                        packageMetadataPath = packageMetadataPath,
                        matchedKey = matchedKey,
                        matchedConditions = matchedConditions,
                    )
                }
            }
            return failure(
                code = R2NodeResolutionFailureCode.TARGET_NOT_IN_SNAPSHOT,
                packageBoundary = packageBoundary,
                packageMetadataPath = packageMetadataPath,
                matchedKey = matchedKey,
                matchedConditions = matchedConditions,
            )
        }

        companion object {

            fun create(targets: List<R2NodeResolutionTarget>): TargetIndex {
                return create(targets, versionTwo = false)
            }

            fun createVersionTwo(targets: List<R2NodeResolutionTarget>): TargetIndex {
                return create(targets, versionTwo = true)
            }

            private fun create(
                targets: List<R2NodeResolutionTarget>,
                versionTwo: Boolean,
            ): TargetIndex {
                val exact = LinkedHashMap<String, R2NodeResolutionTarget>(targets.size)
                val folded = LinkedHashMap<String, R2NodeResolutionTarget>(targets.size)
                targets.forEach { target ->
                    if (!target.path.isCanonicalTargetPath()) {
                        return invalid(R2NodeResolutionFailureCode.INVALID_TARGET, versionTwo)
                    }
                    val supportedKind = if (versionTwo) {
                        target.hasSupportedVersionTwoKind()
                    } else {
                        target.hasSupportedKind()
                    }
                    if (!supportedKind) {
                        return invalid(
                            R2NodeResolutionFailureCode.UNSUPPORTED_SOURCE_KIND,
                            versionTwo,
                        )
                    }
                    if (exact.putIfAbsent(target.path, target) != null) {
                        return invalid(R2NodeResolutionFailureCode.AMBIGUOUS_CASE, versionTwo)
                    }
                    if (folded.putIfAbsent(target.path.asciiCaseFold(), target) != null) {
                        return invalid(R2NodeResolutionFailureCode.AMBIGUOUS_CASE, versionTwo)
                    }
                }
                return TargetIndex(
                    exact = Collections.unmodifiableMap(exact),
                    folded = Collections.unmodifiableMap(folded),
                    versionTwo = versionTwo,
                    failure = null,
                )
            }

            private fun invalid(
                code: R2NodeResolutionFailureCode,
                versionTwo: Boolean = false,
            ) = TargetIndex(
                exact = emptyMap(),
                folded = emptyMap(),
                versionTwo = versionTwo,
                failure = failure(code),
            )
        }
    }

    private fun findMapping(
        values: Map<String, Any?>,
        requestedKey: String,
        keyKind: MappingKeyKind,
    ): Mapping? {
        check(values.keys.all(keyKind::isValid))
        values[requestedKey]?.let { exact ->
            return Mapping(requestedKey, exact, wildcard = null)
        }
        if (values.containsKey(requestedKey)) {
            return Mapping(requestedKey, null, wildcard = null)
        }
        return values.entries.mapNotNull { (key, value) ->
            val star = key.indexOf('*')
            if (star < 0) return@mapNotNull null
            val prefix = key.substring(0, star)
            val suffix = key.substring(star + 1)
            if (
                !requestedKey.startsWith(prefix) ||
                !requestedKey.endsWith(suffix) ||
                requestedKey.length < prefix.length + suffix.length
            ) {
                return@mapNotNull null
            }
            Mapping(
                key = key,
                value = value,
                wildcard = requestedKey.substring(
                    prefix.length,
                    requestedKey.length - suffix.length,
                ),
            )
        }.sortedWith(
            compareByDescending<Mapping> { mapping -> mapping.key.substringBefore('*').length }
                .thenByDescending { mapping -> mapping.key.length }
                .thenBy { mapping -> mapping.key },
        ).firstOrNull()
    }

    private fun resolveRelativePath(
        boundary: String,
        relative: String,
        enforceBoundary: Boolean,
    ): String? {
        val baseSegments = if (boundary == "/") {
            ArrayList()
        } else {
            ArrayList(boundary.drop(1).split('/'))
        }
        relative.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> {
                    if (baseSegments.isEmpty()) return null
                    baseSegments.removeAt(baseSegments.lastIndex)
                }
                else -> {
                    if (!TARGET_SEGMENT_PATTERN.matches(segment)) return null
                    if (segment.equals(NODE_MODULES_SEGMENT, ignoreCase = true)) return null
                    baseSegments += segment
                }
            }
        }
        val candidate = "/" + baseSegments.joinToString("/")
        if (!candidate.isCanonicalTargetPath()) return null
        if (
            enforceBoundary && boundary != "/" &&
            candidate != boundary && !candidate.startsWith("$boundary/")
        ) {
            return null
        }
        return candidate
    }

    private fun parsePackageSpecifier(specifier: String): ParsedPackageSpecifier? {
        val segments = specifier.split('/')
        if (
            segments.isEmpty() ||
            segments.any { segment ->
                segment.isEmpty() ||
                    segment == "." ||
                    segment == ".." ||
                    !SPECIFIER_SEGMENT_PATTERN.matches(segment)
            }
        ) {
            return null
        }
        val packageSegmentCount = if (segments.first().startsWith('@')) 2 else 1
        if (segments.size < packageSegmentCount) return null
        if (packageSegmentCount == 2 && segments.first().length == 1) return null
        val packageName = segments.take(packageSegmentCount).joinToString("/")
        if (!DEPENDENCY_NAME_PATTERN.matches(packageName)) return null
        val remaining = segments.drop(packageSegmentCount)
        return ParsedPackageSpecifier(
            packageName = packageName,
            subpath = if (remaining.isEmpty()) "." else "./${remaining.joinToString("/")}",
        )
    }

    private fun String.isCanonicalSpecifier(): Boolean =
        isNotEmpty() &&
            length <= MAXIMUM_SPECIFIER_CHARS &&
            all { character -> character.code in PRINTABLE_ASCII } &&
            '\\' !in this &&
            '?' !in this &&
            '%' !in this &&
            '|' !in this &&
            ',' !in this &&
            ' ' !in this

    private fun String.isCanonicalImportsSpecifier(): Boolean =
        startsWith('#') &&
            this != "#" &&
            !startsWith("#/") &&
            count { character -> character == '*' } == 0 &&
            drop(1).split('/').all { segment ->
                segment.isNotEmpty() &&
                    segment != "." &&
                    segment != ".." &&
                    SPECIFIER_SEGMENT_PATTERN.matches(segment)
            }

    private fun String.isCanonicalTargetPath(): Boolean {
        if (
            !startsWith('/') ||
            length <= 1 ||
            length > MAXIMUM_TARGET_PATH_CHARS ||
            '\\' in this ||
            any { character -> character.code !in PRINTABLE_ASCII }
        ) {
            return false
        }
        val segments = drop(1).split('/')
        return segments.size >= 2 &&
            segments.first() in ALLOWED_TARGET_ROOTS &&
            segments.all { segment ->
                segment.isNotEmpty() &&
                    segment != "." &&
                    segment != ".." &&
                    !segment.equals(NODE_MODULES_SEGMENT, ignoreCase = true) &&
                    TARGET_SEGMENT_PATTERN.matches(segment)
            }
    }

    private fun R2NodeResolutionTarget.hasSupportedKind(): Boolean = when (kind) {
        R2NodeResolutionTargetKind.PROJECT_SOURCE ->
            path.endsWith(".ts") &&
                !path.endsWith(".d.ts") &&
                !path.endsWith(".mts") &&
                !path.endsWith(".cts") &&
                (path.startsWith("/sources/") || path.startsWith("/packages/"))
        R2NodeResolutionTargetKind.DECLARATION ->
            path.endsWith(".d.ts") &&
                (path.startsWith("/declarations/") || path.startsWith("/packages/"))
    }

    private fun R2NodeResolutionTarget.hasSupportedVersionTwoKind(): Boolean = when (kind) {
        R2NodeResolutionTargetKind.PROJECT_SOURCE ->
            !path.endsWith(".d.ts") &&
                !path.endsWith(".d.mts") &&
                !path.endsWith(".d.cts") &&
                (path.endsWith(".ts") || path.endsWith(".mts") || path.endsWith(".cts")) &&
                (path.startsWith("/sources/") || path.startsWith("/packages/"))
        R2NodeResolutionTargetKind.DECLARATION ->
            (
                path.endsWith(".d.ts") ||
                    path.endsWith(".d.mts") ||
                    path.endsWith(".d.cts")
                ) &&
                (
                    path.startsWith("/sources/") ||
                        path.startsWith("/declarations/") ||
                        path.startsWith("/packages/")
                    )
    }

    private fun resolutionCandidates(candidate: String): List<String> =
        if (candidate.endsWith(".js")) {
            val base = candidate.removeSuffix(".js")
            listOf("$base.ts", "$base.d.ts")
        } else {
            listOf(candidate)
        }

    private fun resolutionCandidatesVersionTwo(candidate: String): List<String> = when {
        candidate.endsWith(".d.ts") ||
            candidate.endsWith(".d.mts") ||
            candidate.endsWith(".d.cts") -> listOf(candidate)
        candidate.endsWith(".d.js") ||
            candidate.endsWith(".d.mjs") ||
            candidate.endsWith(".d.cjs") -> listOf(candidate)
        candidate.endsWith(".mjs") -> listOf(candidate.removeSuffix(".mjs") + ".mts")
        candidate.endsWith(".cjs") -> listOf(candidate.removeSuffix(".cjs") + ".cts")
        candidate.endsWith(".js") -> listOf(candidate.removeSuffix(".js") + ".ts")
        else -> listOf(candidate)
    }

    private fun String.isCanonicalIntegerIndexCondition(): Boolean {
        if (this == "0") return true
        if (isEmpty() || first() !in '1'..'9' || any { character -> character !in '0'..'9' }) {
            return false
        }
        return when {
            length < MAXIMUM_CANONICAL_INTEGER_INDEX_CHARS -> true
            length > MAXIMUM_CANONICAL_INTEGER_INDEX_CHARS -> false
            else -> this <= MAXIMUM_CANONICAL_INTEGER_INDEX
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asStringObject(): Map<String, Any?>? {
        if (this !is Map<*, *> || keys.any { key -> key !is String }) return null
        return this as Map<String, Any?>
    }

    private fun metadataFailure(
        record: R2NodePackageMetadataRecord,
        matchedKey: String? = null,
        matchedConditions: List<String> = emptyList(),
    ) = failure(
        R2NodeResolutionFailureCode.PACKAGE_METADATA_INVALID,
        record,
        matchedKey,
        matchedConditions,
    )

    private enum class MappingKeyKind {
        EXPORTS {
            override fun isValid(key: String): Boolean =
                key == "." ||
                    (
                        key.startsWith("./") &&
                            key.length > 2 &&
                            key.drop(2).isValidMappingPath()
                        )
        },
        IMPORTS {
            override fun isValid(key: String): Boolean =
                key.startsWith('#') &&
                    key != "#" &&
                    !key.startsWith("#/") &&
                    key.drop(1).isValidMappingPath()
        };

        abstract fun isValid(key: String): Boolean
    }

    private fun String.isValidMappingPath(): Boolean =
        count { character -> character == '*' } <= 1 &&
            split('/').all { segment ->
                segment.isNotEmpty() &&
                    segment != "." &&
                    segment != ".." &&
                    !segment.equals(NODE_MODULES_SEGMENT, ignoreCase = true) &&
                    MAPPING_SEGMENT_PATTERN.matches(segment)
            }

    private data class Mapping(
        val key: String,
        val value: Any?,
        val wildcard: String?,
    )

    private data class ParsedPackageSpecifier(
        val packageName: String,
        val subpath: String,
    )

    private const val EXPORTS_KEY = "exports"
    private const val IMPORTS_KEY = "imports"
    private const val DEFAULT_CONDITION = "default"
    private const val TYPES_CONDITION = "types"
    private const val NODE_CONDITION = "node"
    private const val NODE_MODULES_SEGMENT = "node_modules"
    private const val MAXIMUM_RECURSION_DEPTH = 32
    private const val MAXIMUM_SPECIFIER_CHARS = 1024
    private const val MAXIMUM_TARGET_PATH_CHARS = 4096
    private const val MAXIMUM_CANONICAL_INTEGER_INDEX_CHARS = 10
    private const val MAXIMUM_CANONICAL_INTEGER_INDEX = "4294967294"
    private val PRINTABLE_ASCII = 0x20..0x7e
    private val ALLOWED_TARGET_ROOTS = setOf("sources", "declarations", "packages")
    private val TARGET_SEGMENT_PATTERN = Regex("[A-Za-z0-9@._+~-]+")
    private val SPECIFIER_SEGMENT_PATTERN = Regex("@?[A-Za-z0-9][A-Za-z0-9._+~-]*")
    private val MAPPING_SEGMENT_PATTERN = Regex("[#@A-Za-z0-9*][#@A-Za-z0-9._+*~-]*")
    private val CONDITION_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
    private val DEPENDENCY_NAME_PATTERN = Regex(
        "(?:@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*",
    )
}

internal enum class R2NodeResolutionMode(
    internal val condition: String,
) {
    IMPORT("import"),
    REQUIRE("require"),
    TYPES("types"),
}

internal enum class R2NodeResolutionTargetKind {
    PROJECT_SOURCE,
    DECLARATION,
}

internal data class R2NodeResolutionTarget(
    val path: String,
    val kind: R2NodeResolutionTargetKind,
)

internal data class R2NodeResolutionRequest(
    val containingPath: String,
    val specifier: String,
    val mode: R2NodeResolutionMode,
    val conditions: List<String> = emptyList(),
)

internal enum class R2NodeResolutionFailureCode(
    val contractValue: String,
    val diagnosticCode: Int,
) {
    INVALID_CONTAINING_PATH("NODE_RESOLUTION_INVALID_CONTAINING_PATH", 900_001),
    INVALID_SPECIFIER("NODE_RESOLUTION_INVALID_SPECIFIER", 900_002),
    INVALID_CONDITION("NODE_RESOLUTION_INVALID_CONDITION", 900_003),
    AMBIGUOUS_CASE("NODE_RESOLUTION_AMBIGUOUS_CASE", 900_004),
    PACKAGE_NOT_ALLOWED("NODE_RESOLUTION_PACKAGE_NOT_ALLOWED", 900_005),
    PACKAGE_METADATA_MISSING("NODE_RESOLUTION_PACKAGE_METADATA_MISSING", 900_006),
    PACKAGE_METADATA_INVALID("NODE_RESOLUTION_PACKAGE_METADATA_INVALID", 900_007),
    IMPORTS_NOT_DEFINED("NODE_RESOLUTION_IMPORTS_NOT_DEFINED", 900_008),
    IMPORTS_NO_MATCH("NODE_RESOLUTION_IMPORTS_NO_MATCH", 900_009),
    EXPORTS_NOT_DEFINED("NODE_RESOLUTION_EXPORTS_NOT_DEFINED", 900_010),
    EXPORTS_NO_MATCH("NODE_RESOLUTION_EXPORTS_NO_MATCH", 900_011),
    CONDITION_NO_MATCH("NODE_RESOLUTION_CONDITION_NO_MATCH", 900_012),
    INVALID_TARGET("NODE_RESOLUTION_INVALID_TARGET", 900_013),
    TARGET_BLOCKED("NODE_RESOLUTION_TARGET_BLOCKED", 900_014),
    TARGET_ESCAPE("NODE_RESOLUTION_TARGET_ESCAPE", 900_015),
    TARGET_NOT_IN_SNAPSHOT("NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT", 900_016),
    UNSUPPORTED_SOURCE_KIND("NODE_RESOLUTION_UNSUPPORTED_SOURCE_KIND", 900_017),
    CYCLE("NODE_RESOLUTION_CYCLE", 900_018),
    ;

    companion object {
        fun fromDiagnosticIdentityOrNull(
            contractValue: String,
            diagnosticCode: Int,
        ): R2NodeResolutionFailureCode? = entries.singleOrNull { failure ->
            failure.contractValue == contractValue &&
                failure.diagnosticCode == diagnosticCode
        }
    }
}

internal sealed interface R2NodeResolutionResult {

    data class Resolved(
        val target: R2NodeResolutionTarget,
        val packageBoundary: String?,
        val packageMetadataPath: String?,
        val matchedKey: String,
        val matchedConditions: List<String>,
    ) : R2NodeResolutionResult

    data class Failure(
        val code: R2NodeResolutionFailureCode,
        val packageBoundary: String?,
        val packageMetadataPath: String?,
        val matchedKey: String?,
        val matchedConditions: List<String>,
    ) : R2NodeResolutionResult
}

/** Immutable C5-bound metadata record. Package-map semantics are interpreted only by C6-A. */
internal class R2NodePackageMetadataRecord private constructor(
    val path: String,
    val boundaryPath: String,
    val size: Long,
    val sha256: String,
    internal val root: Map<String, Any?>,
) {

    companion object {

        fun create(
            descriptor: R2PackageMetadataFileDescriptor,
            bytes: ByteArray,
        ): R2NodePackageMetadataRecord {
            require(bytes.size.toLong() == descriptor.size) {
                "Accepted package metadata size does not match its C5 descriptor: ${descriptor.path}."
            }
            require(R2Digests.equal(R2Digests.sha256(bytes), descriptor.sha256)) {
                "Accepted package metadata digest does not match its C5 descriptor: ${descriptor.path}."
            }
            R2PackageMetadataPolicy.validatePackageJson(bytes)
            val root = immutableJsonObject(
                R2StrictJson.parseObject(
                    bytes = bytes,
                    maximumDepth = R2PackageMetadataPolicy.MAXIMUM_JSON_DEPTH,
                    maximumNodes = R2PackageMetadataPolicy.MAXIMUM_JSON_NODES,
                ),
            )
            val directory = descriptor.path.removeSuffix("/${R2PackageMetadataPolicy.PACKAGE_FILE_NAME}")
            return R2NodePackageMetadataRecord(
                path = descriptor.path,
                boundaryPath = if (descriptor.path == R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH) {
                    "/"
                } else {
                    "/$directory"
                },
                size = descriptor.size,
                sha256 = descriptor.sha256,
                root = root,
            )
        }
    }
}

/** Exact C5 inventory plus parsed, immutable bodies; construction performs no external I/O. */
internal class R2NodePackageMetadataCatalog private constructor(
    dependencyRoots: List<String>,
    records: List<R2NodePackageMetadataRecord>,
    val inventoryFingerprint: String,
) {

    val dependencyRoots: List<String> = immutableCopy(dependencyRoots)
    val records: List<R2NodePackageMetadataRecord> = immutableCopy(records)
    private val recordsByBoundary: Map<String, R2NodePackageMetadataRecord> =
        Collections.unmodifiableMap(records.associateByTo(LinkedHashMap(), { it.boundaryPath }))

    internal fun recordAtBoundary(boundaryPath: String): R2NodePackageMetadataRecord? =
        recordsByBoundary[boundaryPath]

    internal fun nearestRecord(containingPath: String): R2NodePackageMetadataRecord? =
        records.asSequence()
            .filter { record ->
                record.boundaryPath == "/" ||
                    containingPath.startsWith("${record.boundaryPath}/")
            }
            .maxByOrNull { record -> record.boundaryPath.length }

    companion object {

        fun create(
            inventory: R2PackageMetadataInventory,
            packageMetadataBytes: List<ByteArray>,
        ): R2NodePackageMetadataCatalog {
            require(inventory.files.size == packageMetadataBytes.size) {
                "Accepted package metadata bodies do not exactly cover the C5 inventory."
            }
            val records = inventory.files.mapIndexed { index, descriptor ->
                require(R2PackageMetadataPolicy.isExplicitlyAllowed(descriptor.path, inventory)) {
                    "Package metadata path is outside its accepted C5 inventory: ${descriptor.path}."
                }
                R2NodePackageMetadataRecord.create(descriptor, packageMetadataBytes[index])
            }
            require(records.map { record -> record.boundaryPath }.distinct().size == records.size) {
                "Accepted package metadata contains a duplicate package boundary."
            }
            return R2NodePackageMetadataCatalog(
                dependencyRoots = inventory.dependencyRoots,
                records = records,
                inventoryFingerprint = inventory.fingerprint,
            )
        }
    }
}

private fun failure(
    code: R2NodeResolutionFailureCode,
    record: R2NodePackageMetadataRecord? = null,
    matchedKey: String? = null,
    matchedConditions: List<String> = emptyList(),
): R2NodeResolutionResult.Failure = failure(
    code = code,
    packageBoundary = record?.boundaryPath,
    packageMetadataPath = record?.path,
    matchedKey = matchedKey,
    matchedConditions = matchedConditions,
)

private fun failure(
    code: R2NodeResolutionFailureCode,
    packageBoundary: String?,
    packageMetadataPath: String?,
    matchedKey: String?,
    matchedConditions: List<String>,
): R2NodeResolutionResult.Failure = R2NodeResolutionResult.Failure(
    code = code,
    packageBoundary = packageBoundary,
    packageMetadataPath = packageMetadataPath,
    matchedKey = matchedKey,
    matchedConditions = immutableCopy(matchedConditions),
)

private fun immutableJsonObject(source: Map<String, Any?>): Map<String, Any?> =
    Collections.unmodifiableMap(
        source.entries.associateTo(LinkedHashMap(source.size)) { (key, value) ->
            key to immutableJsonValue(value)
        },
    )

private fun immutableJsonValue(value: Any?): Any? = when (value) {
    is Map<*, *> -> {
        require(value.keys.all { key -> key is String })
        @Suppress("UNCHECKED_CAST")
        immutableJsonObject(value as Map<String, Any?>)
    }
    is List<*> -> Collections.unmodifiableList(value.map(::immutableJsonValue))
    else -> value
}

private fun String.asciiCaseFold(): String = buildString(length) {
    this@asciiCaseFold.forEach { character ->
        append(
            if (character in 'A'..'Z') {
                (character.code + ASCII_CASE_OFFSET).toChar()
            } else {
                character
            },
        )
    }
}

private fun <T> immutableCopy(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

private const val ASCII_CASE_OFFSET = 'a'.code - 'A'.code
