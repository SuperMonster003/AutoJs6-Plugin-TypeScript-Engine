package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2OrdinaryOutputQuotaTest {

    @Test
    fun exactAggregateBoundaryIsInclusiveAcrossFinalPackagedArtifacts() {
        val admission = R2OrdinaryOutputQuota.admission(
            listOf(
                artifact(
                    R2OrdinaryOutputArtifactKind.JAVASCRIPT,
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES,
                ),
                artifact(
                    R2OrdinaryOutputArtifactKind.SOURCE_MAP,
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES,
                ),
            ),
        )

        assertTrue(admission.eligible)
        assertNull(admission.exceededDimension)
        assertEquals(2, admission.measurement.fileCount)
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES,
            admission.measurement.totalBytes,
        )
    }

    @Test
    fun aggregatePlusOneIsRejectedAfterPerArtifactChecksPass() {
        val admission = R2OrdinaryOutputQuota.admission(
            listOf(
                artifact(
                    R2OrdinaryOutputArtifactKind.JAVASCRIPT,
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES,
                ),
                artifact(
                    R2OrdinaryOutputArtifactKind.SOURCE_MAP,
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES,
                ),
                artifact(R2OrdinaryOutputArtifactKind.JAVASCRIPT, 1L),
            ),
        )

        assertEquals(
            R2OrdinaryOutputQuotaDimension.TOTAL_BYTES,
            admission.exceededDimension,
        )
        assertThrows(IllegalArgumentException::class.java) {
            R2OrdinaryOutputQuota.requireEligibility(
                listOf(
                    artifact(
                        R2OrdinaryOutputArtifactKind.JAVASCRIPT,
                        TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES,
                    ),
                    artifact(
                        R2OrdinaryOutputArtifactKind.SOURCE_MAP,
                        TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES,
                    ),
                    artifact(R2OrdinaryOutputArtifactKind.JAVASCRIPT, 1L),
                ),
            )
        }
    }

    @Test
    fun countAndPerKindPlusOneHaveStableDimensions() {
        assertEquals(
            R2OrdinaryOutputQuotaDimension.FILE_COUNT,
            R2OrdinaryOutputQuota.admission(
                List(TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES + 1) {
                    artifact(R2OrdinaryOutputArtifactKind.JAVASCRIPT, 0L)
                },
            ).exceededDimension,
        )
        assertEquals(
            R2OrdinaryOutputQuotaDimension.JAVASCRIPT_BYTES,
            R2OrdinaryOutputQuota.admission(
                listOf(
                    artifact(
                        R2OrdinaryOutputArtifactKind.JAVASCRIPT,
                        TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES + 1L,
                    ),
                ),
            ).exceededDimension,
        )
        assertEquals(
            R2OrdinaryOutputQuotaDimension.SOURCE_MAP_BYTES,
            R2OrdinaryOutputQuota.admission(
                listOf(
                    artifact(
                        R2OrdinaryOutputArtifactKind.SOURCE_MAP,
                        TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES + 1L,
                    ),
                ),
            ).exceededDimension,
        )
    }

    @Test
    fun negativeOrOverflowingMeasurementsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            R2OrdinaryOutputQuota.admission(
                listOf(artifact(R2OrdinaryOutputArtifactKind.JAVASCRIPT, -1L)),
            )
        }
        val overflow = R2OrdinaryOutputQuota.admission(
            listOf(
                artifact(R2OrdinaryOutputArtifactKind.JAVASCRIPT, Long.MAX_VALUE),
                artifact(R2OrdinaryOutputArtifactKind.SOURCE_MAP, Long.MAX_VALUE),
            ),
        )
        assertEquals(
            R2OrdinaryOutputQuotaDimension.JAVASCRIPT_BYTES,
            overflow.exceededDimension,
        )
        assertEquals(Long.MAX_VALUE, overflow.measurement.totalBytes)
    }

    @Test
    fun rejectionPrecedenceIsFileCountThenFirstPerKindThenAggregate() {
        val tooManyAndTooLarge = buildList {
            add(
                artifact(
                    R2OrdinaryOutputArtifactKind.SOURCE_MAP,
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES + 1L,
                ),
            )
            repeat(TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES) {
                add(artifact(R2OrdinaryOutputArtifactKind.JAVASCRIPT, 0L))
            }
        }
        assertEquals(
            R2OrdinaryOutputQuotaDimension.FILE_COUNT,
            R2OrdinaryOutputQuota.admission(tooManyAndTooLarge).exceededDimension,
        )

        val firstPerKindWins = R2OrdinaryOutputQuota.admission(
            listOf(
                artifact(
                    R2OrdinaryOutputArtifactKind.SOURCE_MAP,
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES + 1L,
                ),
                artifact(
                    R2OrdinaryOutputArtifactKind.JAVASCRIPT,
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES + 1L,
                ),
            ),
        )
        assertEquals(
            R2OrdinaryOutputQuotaDimension.SOURCE_MAP_BYTES,
            firstPerKindWins.exceededDimension,
        )
    }

    private fun artifact(
        kind: R2OrdinaryOutputArtifactKind,
        size: Long,
    ) = R2OrdinaryOutputArtifact(kind, size)
}
