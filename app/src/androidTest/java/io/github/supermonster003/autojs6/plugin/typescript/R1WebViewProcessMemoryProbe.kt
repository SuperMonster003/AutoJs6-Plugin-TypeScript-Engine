package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

internal data class R1LiveProcess(
    val pid: Int,
    val processName: String,
    val startTimeTicks: Long,
)

internal data class R1CompilerWebViewProcesses(
    val compilerService: R1LiveProcess,
    val sandboxRenderer: R1LiveProcess,
)

internal data class R1ProcessPssSnapshot(
    val compilerServicePssKiB: Int,
    val sandboxRendererPssKiB: Int,
) {
    val combinedPssKiB: Int
        get() = compilerServicePssKiB + sandboxRendererPssKiB
}

internal data class R1ProcessPssBatch(
    val samples: List<R1ProcessPssSnapshot>,
) {
    init {
        require(samples.isNotEmpty() && samples.size % 2 == 1)
    }

    val compilerServiceMedianPssKiB: Int =
        samples.map(R1ProcessPssSnapshot::compilerServicePssKiB).median()
    val sandboxRendererMedianPssKiB: Int =
        samples.map(R1ProcessPssSnapshot::sandboxRendererPssKiB).median()
    val combinedMedianPssKiB: Int =
        samples.map(R1ProcessPssSnapshot::combinedPssKiB).median()
    val compilerServiceMaximumPssKiB: Int =
        samples.maxOf(R1ProcessPssSnapshot::compilerServicePssKiB)
    val sandboxRendererMaximumPssKiB: Int =
        samples.maxOf(R1ProcessPssSnapshot::sandboxRendererPssKiB)
    val combinedMaximumPssKiB: Int =
        samples.maxOf(R1ProcessPssSnapshot::combinedPssKiB)
}

internal data class R1SingleProcessPssBatch(
    val process: R1LiveProcess,
    val samplesPssKiB: List<Int>,
) {
    init {
        require(samplesPssKiB.isNotEmpty() && samplesPssKiB.size % 2 == 1)
    }

    val medianPssKiB: Int = samplesPssKiB.median()
    val maximumPssKiB: Int = samplesPssKiB.max()
}

/**
 * Shell-backed process and PSS probe for the R1 API 31/33 instrumentation gate.
 *
 * WebView renderer processes run under an isolated UID, so the target process cannot use
 * ActivityManager.getProcessMemoryInfo() for them. The shell-visible ServiceRecord is the
 * authoritative association: packageName must be the plugin package, app must identify the live
 * sandbox ProcessRecord, and the renderer binding must name the live :typescript_compiler client.
 */
internal class R1WebViewProcessMemoryProbe(
    private val context: Context,
) {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    fun awaitCompilerAndRenderer(
        expectedCompilerProcessName: String,
    ): R1CompilerWebViewProcesses {
        require(PACKAGE_NAME.matches(context.packageName)) {
            "Unexpected package name for a shell command: ${context.packageName}"
        }
        var lastDump = ""
        repeat(PROCESS_LOOKUP_ATTEMPTS) {
            lastDump = shellOutput(
                "dumpsys activity services ${context.packageName}",
            )
            resolveCompilerAndRenderer(
                dump = lastDump,
                expectedCompilerProcessName = expectedCompilerProcessName,
            )?.let { processes ->
                requireIdentity(processes.compilerService)
                requireIdentity(processes.sandboxRenderer)
                return processes
            }
            Thread.sleep(PROCESS_LOOKUP_INTERVAL_MILLIS)
        }
        throw AssertionError(
            "Could not uniquely associate the live compiler service and its package-scoped " +
                "WebView sandbox renderer. Last dumpsys output:\n" +
                lastDump.take(MAX_FAILURE_DUMP_CHARS),
        )
    }

    fun awaitRendererForClient(
        clientPid: Int,
    ): R1LiveProcess {
        require(clientPid > 0)
        var lastDump = ""
        repeat(PROCESS_LOOKUP_ATTEMPTS) {
            lastDump = shellOutput(
                "dumpsys activity services ${context.packageName}",
            )
            resolveRendererForClient(
                dump = lastDump,
                clientPid = clientPid,
            )?.let { renderer ->
                requireIdentity(renderer)
                return renderer
            }
            Thread.sleep(PROCESS_LOOKUP_INTERVAL_MILLIS)
        }
        throw AssertionError(
            "Could not uniquely associate a WebView sandbox renderer with client PID " +
                "$clientPid. Last dumpsys output:\n" +
                lastDump.take(MAX_FAILURE_DUMP_CHARS),
        )
    }

    fun awaitProcessExit(
        expected: R1LiveProcess,
    ) {
        var consecutiveMissingObservations = 0
        var lastObservation = "not sampled"
        repeat(PROCESS_LOOKUP_ATTEMPTS) {
            val processTable = runCatching {
                shellOutput("ps -A")
            }
            when (processTablePidState(processTable.getOrNull(), expected.pid)) {
                ProcessTablePidState.MISSING -> {
                    consecutiveMissingObservations += 1
                    lastObservation =
                        "missing ($consecutiveMissingObservations consecutive observations)"
                    if (
                        consecutiveMissingObservations >=
                        REQUIRED_MISSING_PROCESS_OBSERVATIONS
                    ) {
                        return
                    }
                }
                ProcessTablePidState.PRESENT -> {
                    consecutiveMissingObservations = 0
                    val startTime = runCatching {
                        readStartTimeTicks(expected.pid)
                    }
                    val actualStartTime = startTime.getOrNull()
                    lastObservation =
                        if (actualStartTime == null) {
                            "unreadable: ${startTime.exceptionOrNull()?.message}"
                        } else {
                            "startTimeTicks=$actualStartTime"
                        }
                    if (
                        actualStartTime != null &&
                        actualStartTime != expected.startTimeTicks
                    ) {
                        return
                    }
                }
                ProcessTablePidState.UNREADABLE -> {
                    consecutiveMissingObservations = 0
                    lastObservation =
                        "unreadable process table: " +
                            processTable.exceptionOrNull()?.message
                }
            }
            Thread.sleep(PROCESS_LOOKUP_INTERVAL_MILLIS)
        }
        throw AssertionError(
            "Process identity exit was not proven for $expected. " +
                "Last observation: $lastObservation.",
        )
    }

    private fun processTablePidState(
        processTable: String?,
        pid: Int,
    ): ProcessTablePidState {
        val rows = processTable
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toList()
            .orEmpty()
        val header = rows.firstOrNull()
            ?.split(WHITESPACE)
            .orEmpty()
        val pidColumn = header.indexOf("PID")
        if (pidColumn < 0) return ProcessTablePidState.UNREADABLE
        val present = rows.drop(1).any { row ->
            row.split(WHITESPACE)
                .getOrNull(pidColumn)
                ?.toIntOrNull() == pid
        }
        return if (present) {
            ProcessTablePidState.PRESENT
        } else {
            ProcessTablePidState.MISSING
        }
    }

    private enum class ProcessTablePidState {
        PRESENT,
        MISSING,
        UNREADABLE,
    }

    fun requireUnchanged(
        expected: R1CompilerWebViewProcesses,
        expectedCompilerProcessName: String,
    ): R1CompilerWebViewProcesses {
        val actual = awaitCompilerAndRenderer(expectedCompilerProcessName)
        if (actual != expected) {
            throw AssertionError(
                "Compiler/WebView process association changed during PSS sampling: " +
                    "expected=$expected, actual=$actual.",
            )
        }
        return actual
    }

    fun samplePss(
        processes: R1CompilerWebViewProcesses,
        sampleCount: Int,
        sampleIntervalMillis: Long,
    ): R1ProcessPssBatch {
        require(sampleCount > 0 && sampleCount % 2 == 1)
        require(sampleIntervalMillis >= 0)
        val samples = ArrayList<R1ProcessPssSnapshot>(sampleCount)
        repeat(sampleCount) { index ->
            requireIdentity(processes.compilerService)
            requireIdentity(processes.sandboxRenderer)
            val compilerServicePssKiB =
                readTotalPssKiB(processes.compilerService)
            requireIdentity(processes.compilerService)
            requireIdentity(processes.sandboxRenderer)
            val sandboxRendererPssKiB =
                readTotalPssKiB(processes.sandboxRenderer)
            requireIdentity(processes.compilerService)
            requireIdentity(processes.sandboxRenderer)
            samples += R1ProcessPssSnapshot(
                compilerServicePssKiB = compilerServicePssKiB,
                sandboxRendererPssKiB = sandboxRendererPssKiB,
            )
            if (index + 1 < sampleCount) {
                Thread.sleep(sampleIntervalMillis)
            }
        }
        requireIdentity(processes.compilerService)
        requireIdentity(processes.sandboxRenderer)
        return R1ProcessPssBatch(samples)
    }

    fun samplePss(
        process: R1LiveProcess,
        sampleCount: Int,
        sampleIntervalMillis: Long,
    ): R1SingleProcessPssBatch {
        require(sampleCount > 0 && sampleCount % 2 == 1)
        require(sampleIntervalMillis >= 0)
        val samples = ArrayList<Int>(sampleCount)
        repeat(sampleCount) { index ->
            requireIdentity(process)
            samples += readTotalPssKiB(process)
            requireIdentity(process)
            if (index + 1 < sampleCount) {
                Thread.sleep(sampleIntervalMillis)
            }
        }
        return R1SingleProcessPssBatch(
            process = process,
            samplesPssKiB = samples.toList(),
        )
    }

    private fun resolveCompilerAndRenderer(
        dump: String,
        expectedCompilerProcessName: String,
    ): R1CompilerWebViewProcesses? {
        val records = parseServiceRecords(dump)
        val compilerRecords = records.filter { record ->
            record.packageName == context.packageName &&
                record.processName == expectedCompilerProcessName &&
                COMPILER_SERVICE_CLASS_NAME in record.header &&
                record.app != null
        }
        if (compilerRecords.size != 1) return null
        val compiler = requireNotNull(compilerRecords.single().app)
        if (compiler.processName != expectedCompilerProcessName) return null

        val compilerClient = Regex(
            """ProcessRecord\{[^\s}]+\s+${compiler.pid}:""" +
                """${Regex.escape(expectedCompilerProcessName)}/[^}\s]+\}""",
        )
        val rendererRecords = records.filter { record ->
            record.packageName == context.packageName &&
                SANDBOX_SERVICE_CLASS_NAME in record.header &&
                record.processName?.contains(SANDBOX_PROCESS_MARKER) == true &&
                record.app?.processName == record.processName &&
                compilerClient.containsMatchIn(record.raw)
        }
        if (rendererRecords.size != 1) return null
        val renderer = requireNotNull(rendererRecords.single().app)
        if (renderer.pid == compiler.pid) return null

        return R1CompilerWebViewProcesses(
            compilerService = compiler.withStartTime(),
            sandboxRenderer = renderer.withStartTime(),
        )
    }

    private fun resolveRendererForClient(
        dump: String,
        clientPid: Int,
    ): R1LiveProcess? {
        val client = Regex(
            """ProcessRecord\{[^\s}]+\s+$clientPid:[^/\s}]+/[^}\s]+\}""",
        )
        val renderers = parseServiceRecords(dump)
            .filter { record ->
                record.packageName == context.packageName &&
                    SANDBOX_SERVICE_CLASS_NAME in record.header &&
                    record.processName?.contains(SANDBOX_PROCESS_MARKER) == true &&
                    record.app?.processName == record.processName &&
                    client.containsMatchIn(record.raw)
            }
            .mapNotNull(ServiceRecord::app)
            .filter { renderer -> renderer.pid != clientPid }
            .distinctBy(ParsedProcess::pid)
        if (renderers.size != 1) return null
        return renderers.single().withStartTime()
    }

    private fun parseServiceRecords(dump: String): List<ServiceRecord> {
        val records = ArrayList<ServiceRecord>()
        var currentLines: MutableList<String>? = null
        dump.lineSequence().forEach { line ->
            if (SERVICE_RECORD_HEADER.matches(line)) {
                currentLines?.let { records += parseServiceRecord(it) }
                currentLines = mutableListOf(line)
            } else if (
                currentLines != null &&
                SERVICE_RECORD_SECTION_END.matches(line)
            ) {
                records += parseServiceRecord(requireNotNull(currentLines))
                currentLines = null
            } else {
                currentLines?.add(line)
            }
        }
        currentLines?.let { records += parseServiceRecord(it) }
        return records
    }

    private fun parseServiceRecord(lines: List<String>): ServiceRecord {
        val raw = lines.joinToString("\n")
        val appMatch = APP_PROCESS_RECORD.find(raw)
        return ServiceRecord(
            header = lines.first(),
            packageName = PACKAGE_NAME_FIELD.find(raw)?.groupValues?.get(1),
            processName = PROCESS_NAME_FIELD.find(raw)?.groupValues?.get(1),
            app = appMatch?.let { match ->
                ParsedProcess(
                    pid = match.groupValues[1].toInt(),
                    processName = match.groupValues[2],
                )
            },
            raw = raw,
        )
    }

    private fun ParsedProcess.withStartTime(): R1LiveProcess =
        R1LiveProcess(
            pid = pid,
            processName = processName,
            startTimeTicks = readStartTimeTicks(pid),
        )

    private fun requireIdentity(expected: R1LiveProcess) {
        val actualStartTime = readStartTimeTicks(expected.pid)
        if (actualStartTime != expected.startTimeTicks) {
            throw AssertionError(
                "Process identity changed during PSS sampling: " +
                    "expected=$expected, actualStartTimeTicks=$actualStartTime.",
            )
        }
    }

    private fun readStartTimeTicks(pid: Int): Long {
        val stat = shellOutput("cat /proc/$pid/stat")
        return parseStartTimeTicks(pid, stat)
    }

    private fun parseStartTimeTicks(
        pid: Int,
        stat: String,
    ): Long {
        val processNameEnd = stat.lastIndexOf(')')
        if (processNameEnd < 0 || processNameEnd >= stat.lastIndex) {
            throw AssertionError("Malformed /proc/$pid/stat output: $stat")
        }
        return stat
            .substring(processNameEnd + 1)
            .trim()
            .split(WHITESPACE)
            .getOrNull(PROCESS_START_TIME_INDEX_FROM_STATE)
            ?.toLongOrNull()
            ?: throw AssertionError("Missing start time in /proc/$pid/stat: $stat")
    }

    private fun readTotalPssKiB(process: R1LiveProcess): Int {
        val output = shellOutput("dumpsys meminfo ${process.pid}")
        return parseTotalPssKiB(output)
            ?: throw AssertionError(
                "Could not read TOTAL PSS for $process. dumpsys output:\n" +
                    output.take(MAX_FAILURE_DUMP_CHARS),
            )
    }

    private fun parseTotalPssKiB(output: String): Int? {
        val candidates = LinkedHashSet<Int>()
        output.lineSequence().forEach { line ->
            val rawValue = TOTAL_PSS_SUMMARY.matchEntire(line)
                ?.groupValues
                ?.get(1)
                ?: TOTAL_PSS_TABLE_ROW.matchEntire(line)
                    ?.groupValues
                    ?.get(1)
                ?: return@forEach
            val value = parsePssKiB(rawValue) ?: return null
            candidates += value
        }
        return candidates.singleOrNull()
    }

    private fun parsePssKiB(rawValue: String): Int? {
        if (
            ',' in rawValue &&
            !GROUPED_DECIMAL_INTEGER.matches(rawValue)
        ) {
            return null
        }
        return rawValue.replace(",", "").toIntOrNull()
    }

    private fun shellOutput(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { reader ->
            reader.readText()
        }

    private data class ParsedProcess(
        val pid: Int,
        val processName: String,
    )

    private data class ServiceRecord(
        val header: String,
        val packageName: String?,
        val processName: String?,
        val app: ParsedProcess?,
        val raw: String,
    )

    private companion object {
        val PACKAGE_NAME = Regex("""[A-Za-z0-9_.]+""")
        val SERVICE_RECORD_HEADER = Regex("""^\s*\*\s+ServiceRecord\{.*$""")
        val SERVICE_RECORD_SECTION_END =
            Regex("""^\s{2}Connection bindings to services:\s*$""")
        val PACKAGE_NAME_FIELD = Regex("""(?m)^\s*packageName=(\S+)\s*$""")
        val PROCESS_NAME_FIELD = Regex("""(?m)^\s*processName=(\S+)\s*$""")
        val APP_PROCESS_RECORD = Regex(
            """(?m)^\s*app=ProcessRecord\{[^\s}]+\s+""" +
                """(\d+):([^/\s}]+)/[^}\s]+\}\s*$""",
        )
        val TOTAL_PSS_SUMMARY =
            Regex("""^\s*TOTAL(?: PSS)?:\s*([0-9][0-9,]*)(?:\s|$).*$""")
        val TOTAL_PSS_TABLE_ROW =
            Regex("""^\s*TOTAL\s+([0-9][0-9,]*)(?:\s+[0-9][0-9,]*){3,}\s*$""")
        val GROUPED_DECIMAL_INTEGER =
            Regex("""[0-9]{1,3}(?:,[0-9]{3})+""")
        val WHITESPACE = Regex("""\s+""")

        const val COMPILER_SERVICE_CLASS_NAME = "TypeScriptCompilerPluginService"
        const val SANDBOX_SERVICE_CLASS_NAME =
            "org.chromium.content.app.SandboxedProcessService"
        const val SANDBOX_PROCESS_MARKER = ":sandboxed_process"
        const val PROCESS_START_TIME_INDEX_FROM_STATE = 19
        const val PROCESS_LOOKUP_ATTEMPTS = 50
        const val PROCESS_LOOKUP_INTERVAL_MILLIS = 100L
        const val REQUIRED_MISSING_PROCESS_OBSERVATIONS = 2
        const val MAX_FAILURE_DUMP_CHARS = 8_192
    }
}

private fun List<Int>.median(): Int = sorted()[size / 2]
