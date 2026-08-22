package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal data class RemoteProcessIdentity(
    val pid: Int,
    val startTimeTicks: Long,
)

internal data class RemoteFileDescriptorSnapshot(
    val targets: List<String>,
) {
    val count: Int
        get() = targets.size
}

internal class RemoteProcessResourceProbe(
    private val context: Context,
) {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    fun requireIdentity(pid: Int): RemoteProcessIdentity =
        identityOrNull(pid)
            ?: throw AssertionError(
                "Could not read /proc/$pid/stat for the live compiler process.",
            )

    fun identityOrNull(pid: Int): RemoteProcessIdentity? {
        directIdentityOrNull(pid)?.let { return it }
        if (!File("/proc/$pid").exists()) return null
        val stat = shellOutput(
            "run-as ${context.packageName} cat /proc/$pid/stat 2>&1",
        )
        val startTimeTicks = stat
            ?.let(::parseProcessStartTimeTicks)
            ?: return null
        return RemoteProcessIdentity(
            pid = pid,
            startTimeTicks = startTimeTicks,
        )
    }

    fun directIdentityOrNull(pid: Int): RemoteProcessIdentity? {
        val startTimeTicks = runCatching {
            File("/proc/$pid/stat").readText()
        }.getOrNull()
            ?.let(::parseProcessStartTimeTicks)
            ?: return null
        return RemoteProcessIdentity(
            pid = pid,
            startTimeTicks = startTimeTicks,
        )
    }

    fun processDirectoryExists(pid: Int): Boolean =
        File("/proc/$pid").exists()

    fun awaitStableFileDescriptors(
        pid: Int,
        label: String,
        maximumCount: Int? = null,
        expectedIdentity: RemoteProcessIdentity? = null,
    ): RemoteFileDescriptorSnapshot {
        var lastSnapshot: RemoteFileDescriptorSnapshot? = null
        var previousQualifiedTargets: List<String>? = null
        var consecutiveQualifiedSamples = 0
        repeat(FD_STABILITY_LOOKUP_ATTEMPTS) {
            assertExpectedIdentity(pid, expectedIdentity, label)
            val snapshot = fileDescriptorSnapshotOrNull(pid)
            if (snapshot != null) {
                assertExpectedIdentity(pid, expectedIdentity, label)
                lastSnapshot = snapshot
                val stagingTargets = snapshot.targets.filter(::isR1StagingArchiveTarget)
                if (stagingTargets.isNotEmpty()) {
                    throw AssertionError(
                        "$label retained R1 staging archive descriptors: $stagingTargets",
                    )
                }
                val r2TransientTargets =
                    snapshot.targets.filter(::isR2TransientTarget)
                if (r2TransientTargets.isNotEmpty()) {
                    throw AssertionError(
                        "$label retained R2 transient descriptors: $r2TransientTargets",
                    )
                }

                val countWithinLimit =
                    maximumCount == null || snapshot.count <= maximumCount
                if (countWithinLimit) {
                    val canonicalTargets = snapshot.targets.sorted()
                    consecutiveQualifiedSamples =
                        if (previousQualifiedTargets == canonicalTargets) {
                            consecutiveQualifiedSamples + 1
                        } else {
                            1
                        }
                    previousQualifiedTargets = canonicalTargets
                    if (
                        consecutiveQualifiedSamples >=
                        REQUIRED_CONSECUTIVE_STABLE_SAMPLES
                    ) {
                        return snapshot
                    }
                } else {
                    previousQualifiedTargets = null
                    consecutiveQualifiedSamples = 0
                }
            } else {
                previousQualifiedTargets = null
                consecutiveQualifiedSamples = 0
            }
            Thread.sleep(FD_STABILITY_LOOKUP_INTERVAL_MILLIS)
        }

        val observed = lastSnapshot?.count?.toString() ?: "unavailable"
        val expected = maximumCount?.let { " at most $it" }.orEmpty()
        throw AssertionError(
            "$label did not reach a stable FD target multiset$expected; " +
                "last observed count=$observed, " +
                "targets=${lastSnapshot?.targets.orEmpty().sorted()}.",
        )
    }

    fun awaitExactFileDescriptorTargetCount(
        pid: Int,
        label: String,
        expectedCount: Int,
        expectedIdentity: RemoteProcessIdentity? = null,
        matchesTarget: (String) -> Boolean,
    ): RemoteFileDescriptorSnapshot {
        require(expectedCount >= 0)
        var lastSnapshot: RemoteFileDescriptorSnapshot? = null
        var lastMatchingTargets = emptyList<String>()
        var previousQualifiedTargets: List<String>? = null
        var consecutiveQualifiedSamples = 0
        repeat(FD_STABILITY_LOOKUP_ATTEMPTS) {
            assertExpectedIdentity(pid, expectedIdentity, label)
            val snapshot = fileDescriptorSnapshotOrNull(pid)
            if (snapshot != null) {
                assertExpectedIdentity(pid, expectedIdentity, label)
                lastSnapshot = snapshot
                val matchingTargets = snapshot.targets
                    .filter(matchesTarget)
                    .sorted()
                lastMatchingTargets = matchingTargets
                if (matchingTargets.size == expectedCount) {
                    consecutiveQualifiedSamples =
                        if (previousQualifiedTargets == matchingTargets) {
                            consecutiveQualifiedSamples + 1
                        } else {
                            1
                        }
                    previousQualifiedTargets = matchingTargets
                    if (
                        consecutiveQualifiedSamples >=
                        REQUIRED_CONSECUTIVE_STABLE_SAMPLES
                    ) {
                        return snapshot
                    }
                } else {
                    previousQualifiedTargets = null
                    consecutiveQualifiedSamples = 0
                }
            } else {
                previousQualifiedTargets = null
                consecutiveQualifiedSamples = 0
            }
            Thread.sleep(FD_STABILITY_LOOKUP_INTERVAL_MILLIS)
        }
        throw AssertionError(
            "$label did not expose exactly $expectedCount matching FD targets for " +
                "$REQUIRED_CONSECUTIVE_STABLE_SAMPLES consecutive readable samples; " +
                "last matching targets=$lastMatchingTargets, " +
                "last snapshot=${lastSnapshot?.targets.orEmpty().sorted()}.",
        )
    }

    fun awaitR2TransientFileDescriptors(
        pid: Int,
        label: String,
        expectedCount: Int,
        expectedIdentity: RemoteProcessIdentity? = null,
    ): RemoteFileDescriptorSnapshot =
        awaitExactFileDescriptorTargetCount(
            pid = pid,
            label = label,
            expectedCount = expectedCount,
            expectedIdentity = expectedIdentity,
            matchesTarget = ::isR2TransientTarget,
        )

    fun awaitR2ProjectSpoolFileDescriptors(
        pid: Int,
        label: String,
        expectedCount: Int,
        expectedIdentity: RemoteProcessIdentity? = null,
    ): RemoteFileDescriptorSnapshot =
        awaitExactFileDescriptorTargetCount(
            pid = pid,
            label = label,
            expectedCount = expectedCount,
            expectedIdentity = expectedIdentity,
            matchesTarget = ::isR2ProjectSpoolTarget,
        )

    fun awaitStagingArchiveFileDescriptors(
        pid: Int,
        label: String,
        expectedCount: Int,
        expectedIdentity: RemoteProcessIdentity? = null,
    ): RemoteFileDescriptorSnapshot =
        awaitExactFileDescriptorTargetCount(
            pid = pid,
            label = label,
            expectedCount = expectedCount,
            expectedIdentity = expectedIdentity,
            matchesTarget = ::isR1StagingArchiveTarget,
        )

    private fun assertExpectedIdentity(
        pid: Int,
        expectedIdentity: RemoteProcessIdentity?,
        label: String,
    ) {
        expectedIdentity ?: return
        val actualIdentity = identityOrNull(pid)
        if (actualIdentity != expectedIdentity) {
            throw AssertionError(
                "$label process identity changed during FD sampling: " +
                    "expected=$expectedIdentity, actual=$actualIdentity.",
            )
        }
    }

    private fun fileDescriptorSnapshotOrNull(
        pid: Int,
    ): RemoteFileDescriptorSnapshot? {
        val direct = runCatching {
            val descriptors = File("/proc/$pid/fd").listFiles() ?: return@runCatching null
            val targets = ArrayList<String>(descriptors.size)
            descriptors.forEach { descriptor ->
                val target = runCatching { Os.readlink(descriptor.path) }
                    .getOrNull()
                    ?: return@runCatching null
                targets += target
            }
            targets
                .takeIf(List<String>::isNotEmpty)
                ?.let(::RemoteFileDescriptorSnapshot)
        }.getOrNull()
        if (direct != null) return direct

        val output = shellOutput(
            "run-as ${context.packageName} ls -l /proc/$pid/fd 2>&1",
        ) ?: return null
        val outputLines = output.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        val descriptorLines = outputLines.filter { line -> " -> " in line }
        val unexpectedLines = outputLines - descriptorLines.toSet() -
            outputLines.filter { line -> line.startsWith("total ") }.toSet()
        if (descriptorLines.isEmpty() || unexpectedLines.isNotEmpty()) {
            return null
        }
        val targets = descriptorLines.map { line ->
            line.substringAfter(" -> ")
        }
        return targets
            .let(::RemoteFileDescriptorSnapshot)
    }

    private fun shellOutput(command: String): String? = runCatching {
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { reader -> reader.readText() }
    }.getOrNull()

    private fun parseProcessStartTimeTicks(stat: String): Long? {
        val processNameEnd = stat.lastIndexOf(')')
        if (processNameEnd < 0 || processNameEnd >= stat.lastIndex) return null
        val fieldsFromState = stat
            .substring(processNameEnd + 1)
            .trim()
            .split(WHITESPACE)
        return fieldsFromState
            .getOrNull(PROCESS_START_TIME_INDEX_FROM_STATE)
            ?.toLongOrNull()
    }

    private fun isR1StagingArchiveTarget(target: String): Boolean {
        val fileName = target
            .removeSuffix(DELETED_FILE_SUFFIX)
            .substringAfterLast('/')
        return fileName.endsWith(R1_STAGING_FILE_SUFFIX)
    }

    private fun isR2TransientTarget(target: String): Boolean {
        val fileName = target
            .removeSuffix(DELETED_FILE_SUFFIX)
            .substringAfterLast('/')
        return (
            fileName.startsWith(R2_PRIVATE_INPUT_PREFIX) &&
                fileName.endsWith(R2_PRIVATE_INPUT_SUFFIX)
            ) || (
            fileName.startsWith(R2_PROJECT_SPOOL_PREFIX) &&
                fileName.endsWith(R2_PROJECT_SPOOL_SUFFIX)
            ) || (
            fileName.startsWith(R2_PRIVATE_OUTPUT_PREFIX) &&
                fileName.endsWith(R2_PRIVATE_OUTPUT_SUFFIX)
            ) || (
            fileName.startsWith(R2_CALLER_INPUT_PREFIX) &&
                fileName.endsWith(R2_CALLER_ARCHIVE_SUFFIX)
            ) || (
            fileName.startsWith(R2_CALLER_OUTPUT_PREFIX) &&
                fileName.endsWith(R2_CALLER_ARCHIVE_SUFFIX)
            ) || (
            fileName.startsWith(R2_CAPACITY_OUTPUT_PREFIX) &&
                fileName.endsWith(R2_CALLER_ARCHIVE_SUFFIX)
            ) || (
            fileName.startsWith(R2_CAPACITY_INPUT_PREFIX) &&
                fileName.endsWith(R2_CALLER_ARCHIVE_SUFFIX)
            )
    }

    private fun isR2ProjectSpoolTarget(target: String): Boolean {
        val fileName = target
            .removeSuffix(DELETED_FILE_SUFFIX)
            .substringAfterLast('/')
        return fileName.startsWith(R2_PROJECT_SPOOL_PREFIX) &&
            fileName.endsWith(R2_PROJECT_SPOOL_SUFFIX)
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val PROCESS_START_TIME_INDEX_FROM_STATE = 19
        const val FD_STABILITY_LOOKUP_ATTEMPTS = 50
        const val FD_STABILITY_LOOKUP_INTERVAL_MILLIS = 100L
        const val REQUIRED_CONSECUTIVE_STABLE_SAMPLES = 5
        const val DELETED_FILE_SUFFIX = " (deleted)"
        const val R1_STAGING_FILE_SUFFIX = ".archive"
        const val R2_PRIVATE_INPUT_PREFIX = "r2-private-input-"
        const val R2_PRIVATE_INPUT_SUFFIX = ".staging"
        const val R2_PROJECT_SPOOL_PREFIX = "r2-project-vfs-"
        const val R2_PROJECT_SPOOL_SUFFIX = ".spool"
        const val R2_PRIVATE_OUTPUT_PREFIX = "r2-private-output-"
        const val R2_PRIVATE_OUTPUT_SUFFIX = ".zip.staging"
        const val R2_CALLER_INPUT_PREFIX = "r2-binder-input-"
        const val R2_CALLER_OUTPUT_PREFIX = "r2-binder-output-"
        const val R2_CAPACITY_INPUT_PREFIX = "r2-capacity-"
        const val R2_CAPACITY_OUTPUT_PREFIX = "r2-binder-capacity-output-"
        const val R2_CALLER_ARCHIVE_SUFFIX = ".archive"
    }
}
