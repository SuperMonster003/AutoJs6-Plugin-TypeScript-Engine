package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/**
 * Native authority for the public ordinary-compilation emitted-artifact quota.
 *
 * Callers must provide the final packaged JavaScript and Source Map byte sizes. Generated
 * manifest/diagnostics entries and ZIP metadata are deliberately outside this counting domain.
 */
internal object R2OrdinaryOutputQuota {

    fun admission(
        artifacts: List<R2OrdinaryOutputArtifact>,
    ): R2OrdinaryOutputQuotaAdmission {
        var javascriptBytes = 0L
        var sourceMapBytes = 0L
        var totalBytes = 0L
        var exceededDimension: R2OrdinaryOutputQuotaDimension? = null

        // Stable rejection precedence is file count, then the first artifact-order per-kind
        // excess, then aggregate bytes. The packager supplies canonical path-sorted artifacts.
        if (artifacts.size > TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES) {
            exceededDimension = R2OrdinaryOutputQuotaDimension.FILE_COUNT
        }
        artifacts.forEach { artifact ->
            require(artifact.size >= 0L) {
                "Ordinary output artifact size must be non-negative."
            }
            val perArtifactMaximum = when (artifact.kind) {
                R2OrdinaryOutputArtifactKind.JAVASCRIPT ->
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES
                R2OrdinaryOutputArtifactKind.SOURCE_MAP ->
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES
            }
            if (exceededDimension == null && artifact.size > perArtifactMaximum) {
                exceededDimension = when (artifact.kind) {
                    R2OrdinaryOutputArtifactKind.JAVASCRIPT ->
                        R2OrdinaryOutputQuotaDimension.JAVASCRIPT_BYTES
                    R2OrdinaryOutputArtifactKind.SOURCE_MAP ->
                        R2OrdinaryOutputQuotaDimension.SOURCE_MAP_BYTES
                }
            }
            when (artifact.kind) {
                R2OrdinaryOutputArtifactKind.JAVASCRIPT ->
                    javascriptBytes = saturatedAdd(javascriptBytes, artifact.size)
                R2OrdinaryOutputArtifactKind.SOURCE_MAP ->
                    sourceMapBytes = saturatedAdd(sourceMapBytes, artifact.size)
            }
            totalBytes = saturatedAdd(totalBytes, artifact.size)
            if (
                exceededDimension == null &&
                totalBytes > TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES
            ) {
                exceededDimension = R2OrdinaryOutputQuotaDimension.TOTAL_BYTES
            }
        }
        return R2OrdinaryOutputQuotaAdmission(
            measurement = R2OrdinaryOutputQuotaMeasurement(
                fileCount = artifacts.size,
                javascriptBytes = javascriptBytes,
                sourceMapBytes = sourceMapBytes,
                totalBytes = totalBytes,
            ),
            exceededDimension = exceededDimension,
        )
    }

    fun requireEligibility(
        artifacts: List<R2OrdinaryOutputArtifact>,
    ): R2OrdinaryOutputQuotaMeasurement {
        val admission = admission(artifacts)
        require(admission.exceededDimension == null) {
            "Output is outside the ordinary compilation limit: " +
                admission.exceededDimension?.name?.lowercase()
        }
        return admission.measurement
    }

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}

internal data class R2OrdinaryOutputArtifact(
    val kind: R2OrdinaryOutputArtifactKind,
    val size: Long,
)

internal enum class R2OrdinaryOutputArtifactKind {
    JAVASCRIPT,
    SOURCE_MAP,
}

internal data class R2OrdinaryOutputQuotaMeasurement(
    val fileCount: Int,
    val javascriptBytes: Long,
    val sourceMapBytes: Long,
    val totalBytes: Long,
)

internal data class R2OrdinaryOutputQuotaAdmission(
    val measurement: R2OrdinaryOutputQuotaMeasurement,
    val exceededDimension: R2OrdinaryOutputQuotaDimension?,
) {
    val eligible: Boolean
        get() = exceededDimension == null
}

internal enum class R2OrdinaryOutputQuotaDimension {
    FILE_COUNT,
    JAVASCRIPT_BYTES,
    SOURCE_MAP_BYTES,
    TOTAL_BYTES,
}
