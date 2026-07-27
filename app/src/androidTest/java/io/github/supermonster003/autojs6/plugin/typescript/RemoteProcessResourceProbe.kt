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

    fun awaitStableFileDescriptors(
        pid: Int,
        label: String,
        maximumCount: Int? = null,
        expectedIdentity: RemoteProcessIdentity? = null,
    ): RemoteFileDescriptorSnapshot {
        var lastSnapshot: RemoteFileDescriptorSnapshot? = null
        var previousQualifiedCount: Int? = null
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

                val countWithinLimit =
                    maximumCount == null || snapshot.count <= maximumCount
                if (countWithinLimit) {
                    consecutiveQualifiedSamples =
                        if (previousQualifiedCount == snapshot.count) {
                            consecutiveQualifiedSamples + 1
                        } else {
                            1
                        }
                    previousQualifiedCount = snapshot.count
                    if (
                        consecutiveQualifiedSamples >=
                        REQUIRED_CONSECUTIVE_STABLE_SAMPLES
                    ) {
                        return snapshot
                    }
                } else {
                    previousQualifiedCount = null
                    consecutiveQualifiedSamples = 0
                }
            } else {
                previousQualifiedCount = null
                consecutiveQualifiedSamples = 0
            }
            Thread.sleep(FD_STABILITY_LOOKUP_INTERVAL_MILLIS)
        }

        val observed = lastSnapshot?.count?.toString() ?: "unavailable"
        val expected = maximumCount?.let { " at most $it" }.orEmpty()
        throw AssertionError(
            "$label did not reach a stable FD count$expected; last observed count=$observed.",
        )
    }

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

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val PROCESS_START_TIME_INDEX_FROM_STATE = 19
        const val FD_STABILITY_LOOKUP_ATTEMPTS = 40
        const val FD_STABILITY_LOOKUP_INTERVAL_MILLIS = 50L
        const val REQUIRED_CONSECUTIVE_STABLE_SAMPLES = 3
        const val DELETED_FILE_SUFFIX = " (deleted)"
        const val R1_STAGING_FILE_SUFFIX = ".archive"
    }
}
