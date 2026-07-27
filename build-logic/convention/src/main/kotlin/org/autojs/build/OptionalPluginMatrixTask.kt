package org.autojs.build

import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

internal data class OptionalPluginMatrixState(
    val label: String,
    val nodeInstalled: Boolean,
    val aceInstalled: Boolean,
)

internal val optionalPluginMatrixStates = listOf(
    OptionalPluginMatrixState("none", nodeInstalled = false, aceInstalled = false),
    OptionalPluginMatrixState("node-only", nodeInstalled = true, aceInstalled = false),
    OptionalPluginMatrixState("node+ace", nodeInstalled = true, aceInstalled = true),
    OptionalPluginMatrixState("ace-only", nodeInstalled = false, aceInstalled = true),
)

internal fun parseAapt2PackageName(output: String): String {
    val lines = output.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
    require(lines.size == 1) {
        "Expected aapt2 to return exactly one package name, found: $lines"
    }
    return lines.single().also { packageName ->
        require(packageName.matches(Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+"""))) {
            "aapt2 returned an invalid package name: $packageName"
        }
    }
}

internal fun requireSuccessfulOptionalPluginInstrumentation(output: String) {
    val normalized = output.replace("\r\n", "\n")
    val lines = normalized.lineSequence().map(String::trim).toList()
    val forbiddenMarkers = listOf(
        "FAILURES!!!",
        "INSTRUMENTATION_ABORTED:",
        "INSTRUMENTATION_FAILED:",
        "Process crashed.",
        "shortMsg=Process crashed",
        "OK (0 tests)",
    )
    require(forbiddenMarkers.none(normalized::contains)) {
        "Optional plugin matrix instrumentation reported failure:\n$normalized"
    }
    val okLines = lines.filter { line -> line.startsWith("OK (") }
    val terminalCodeLines = lines.filter { line ->
        line.startsWith("INSTRUMENTATION_CODE:")
    }
    require(okLines == listOf("OK (1 test)")) {
        "Optional plugin matrix instrumentation did not report one unique OK line:\n" +
            normalized
    }
    require(terminalCodeLines == listOf("INSTRUMENTATION_CODE: -1")) {
        "Optional plugin matrix instrumentation did not report exactly one successful test:\n" +
            normalized
    }
}

@DisableCachingByDefault(
    because = "The task intentionally mutates and verifies an explicitly selected emulator.",
)
abstract class VerifyR1OptionalPluginMatrix : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val adbExecutable: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aapt2Executable: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val targetApkDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val testApkDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val nodeApk: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aceApk: RegularFileProperty

    @get:Input
    abstract val deviceSerial: Property<String>

    @get:Input
    abstract val mutationConfirmation: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        group = "verification"
        description =
            "Runs the strict R1 Node/Ace four-state matrix on an explicit disposable emulator."
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun verifyMatrix() {
        val serial = deviceSerial.get().trim()
        val expectedConfirmation = "MUTATE:$serial"
        if (mutationConfirmation.get() != expectedConfirmation) {
            throw GradleException(
                "Set -Pr1MatrixConfirm=$expectedConfirmation to confirm package changes.",
            )
        }
        if (!serial.matches(Regex("""emulator-\d+"""))) {
            throw GradleException(
                "R1 optional plugin matrix only accepts an explicit emulator-* serial: $serial",
            )
        }

        val targetApkFile = resolveSingleApkOutput(targetApkDirectory.get().asFile)
        val testApkFile = resolveSingleApkOutput(testApkDirectory.get().asFile)
        val nodeApkFile = nodeApk.get().asFile
        val aceApkFile = aceApk.get().asFile
        val apkFiles = linkedMapOf(
            TARGET_PACKAGE to targetApkFile,
            TEST_PACKAGE to testApkFile,
            NODE_PACKAGE to nodeApkFile,
            ACE_PACKAGE to aceApkFile,
        )

        validateApkPackageNames(apkFiles)
        validateTestApkInstrumentation(testApkFile)
        validateSelectedEmulator(serial)

        val report = mutableListOf<String>()
        val instrumentationReports = mutableListOf<Pair<String, String>>()
        var primaryFailure: Throwable? = null
        var packageMutationStarted = false

        report += "R1 optional plugin matrix"
        report += "serial=$serial"
        report += "api=${adb(serial, "shell", "getprop", "ro.build.version.sdk").stdout.trim()}"
        report += "buildFingerprint=" +
            adb(serial, "shell", "getprop", "ro.build.fingerprint").stdout.trim()
        report += "webViewProvider=" + queryWebViewProvider(serial)
        apkFiles.forEach { (packageName, apkFile) ->
            report += "apk[$packageName]=${apkFile.absolutePath}"
            report += "sha256[$packageName]=${apkFile.sha256()}"
        }

        try {
            installApk(serial, targetApkFile)
            installApk(serial, testApkFile)

            packageMutationStarted = true
            optionalPluginMatrixStates.forEach { state ->
                setPackageState(serial, NODE_PACKAGE, nodeApkFile, state.nodeInstalled)
                setPackageState(serial, ACE_PACKAGE, aceApkFile, state.aceInstalled)
                adb(serial, "shell", "am", "force-stop", TARGET_PACKAGE)

                val result = adb(
                    serial,
                    "shell",
                    "am",
                    "instrument",
                    "-w",
                    "-r",
                    "-e",
                    "class",
                    "$TEST_CLASS#$TEST_METHOD",
                    "-e",
                    "expectedNode",
                    state.nodeInstalled.toString(),
                    "-e",
                    "expectedAce",
                    state.aceInstalled.toString(),
                    "$TEST_PACKAGE/$TEST_RUNNER",
                    requireSuccess = false,
                )
                val output = buildString {
                    appendLine("ADB_EXIT_CODE=${result.exitValue}")
                    append(result.combinedOutput())
                }
                instrumentationReports += state.label to output
                if (result.exitValue != 0) {
                    throw GradleException(
                        "Optional plugin matrix instrumentation command failed " +
                            "for '${state.label}' (${result.exitValue}).",
                    )
                }
                requireSuccessfulOptionalPluginInstrumentation(output)
                logger.lifecycle(
                    "R1 optional plugin matrix state '${state.label}' passed on $serial.",
                )
            }
        } catch (failure: Throwable) {
            primaryFailure = failure
        } finally {
            if (packageMutationStarted) {
                val cleanupFailure = cleanOptionalPlugins(serial)
                if (cleanupFailure != null) {
                    if (primaryFailure == null) {
                        primaryFailure = cleanupFailure
                    } else {
                        primaryFailure.addSuppressed(cleanupFailure)
                    }
                }
            }

            report += "result=${if (primaryFailure == null) "PASS" else "FAIL"}"
            instrumentationReports.forEach { (label, output) ->
                report += ""
                report += "===== $label ====="
                report += output.trimEnd()
            }
            primaryFailure?.let { failure ->
                report += ""
                report += "===== failure ====="
                report += failure.stackTraceToString().trimEnd()
            }
            val reportFailure = runCatching {
                val output = reportFile.get().asFile
                output.parentFile.mkdirs()
                output.writeText(report.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            }.exceptionOrNull()
            if (reportFailure != null) {
                if (primaryFailure == null) {
                    primaryFailure = reportFailure
                } else {
                    primaryFailure.addSuppressed(reportFailure)
                }
            }
        }

        primaryFailure?.let { throw it }
    }

    private fun validateApkPackageNames(apks: Map<String, File>) {
        apks.forEach { (expectedPackage, apkFile) ->
            if (!apkFile.isFile) {
                throw GradleException("APK does not exist: $apkFile")
            }
            val packageName = parseAapt2PackageName(
                runTool(
                    executable = aapt2Executable.get().asFile,
                    arguments = listOf("dump", "packagename", apkFile.absolutePath),
                ).stdout,
            )
            if (packageName != expectedPackage) {
                throw GradleException(
                    "Unexpected package in ${apkFile.name}: " +
                        "expected=$expectedPackage, actual=$packageName",
                )
            }
        }
    }

    private fun validateTestApkInstrumentation(testApkFile: File) {
        val manifestTree = runTool(
            executable = aapt2Executable.get().asFile,
            arguments = listOf(
                "dump",
                "xmltree",
                "--file",
                "AndroidManifest.xml",
                testApkFile.absolutePath,
            ),
        ).stdout.replace("\r\n", "\n")
        val instrumentationBlock = Regex(
            """(?ms)^(\s*)E: instrumentation\b.*?(?=^\1E: |\z)""",
        ).find(manifestTree)?.value
            ?: throw GradleException(
                "Test APK manifest does not contain an instrumentation element.",
            )
        val expectedRunner = Regex.escape(TEST_RUNNER)
        val expectedTarget = Regex.escape(TARGET_PACKAGE)
        val runnerMatches = Regex(
            """(?m)^\s+A: .*:name(?:\(.*?\))?="$expectedRunner"(?:\s|$)""",
        ).findAll(instrumentationBlock).count()
        val targetMatches = Regex(
            """(?m)^\s+A: .*:targetPackage(?:\(.*?\))?="$expectedTarget"(?:\s|$)""",
        ).findAll(instrumentationBlock).count()
        if (runnerMatches != 1 || targetMatches != 1) {
            throw GradleException(
                "Unexpected test APK instrumentation: runnerMatches=$runnerMatches, " +
                    "targetMatches=$targetMatches\n$instrumentationBlock",
            )
        }
    }

    private fun validateSelectedEmulator(serial: String) {
        val devices = runTool(
            executable = adbExecutable.get().asFile,
            arguments = listOf("devices"),
        ).stdout.lineSequence()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith("List of devices") }
            .map { line -> line.split(Regex("""\s+""")) }
            .toList()
        val selected = devices.singleOrNull { columns -> columns.firstOrNull() == serial }
            ?: throw GradleException(
                "The explicitly selected emulator is not connected exactly once: $serial",
            )
        if (selected.getOrNull(1) != "device") {
            throw GradleException(
                "The selected emulator is not ready: $serial (${selected.drop(1)})",
            )
        }
        val selectedState = adb(serial, "get-state").stdout.trim()
        if (selectedState != "device") {
            throw GradleException(
                "The selected emulator did not confirm get-state=device: " +
                    "$serial ($selectedState)",
            )
        }
        val qemu = adb(serial, "shell", "getprop", "ro.kernel.qemu").stdout.trim()
        if (qemu != "1") {
            throw GradleException(
                "The selected serial does not report ro.kernel.qemu=1: $serial",
            )
        }
    }

    private fun queryWebViewProvider(serial: String): String {
        val command = adb(
            serial,
            "shell",
            "cmd",
            "webviewupdate",
            "getCurrentWebViewPackage",
            requireSuccess = false,
        )
        if (command.exitValue == 0 && command.stdout.isNotBlank()) {
            return command.stdout.trim()
        }
        return adb(serial, "shell", "dumpsys", "webviewupdate").stdout.trim()
    }

    private fun setPackageState(
        serial: String,
        packageName: String,
        apkFile: File,
        installed: Boolean,
    ) {
        if (isPackageInstalled(serial, packageName) != installed) {
            if (installed) {
                installApk(serial, apkFile)
            } else {
                uninstallPackage(serial, packageName)
            }
        }
        awaitPackageState(serial, packageName, installed)
    }

    private fun installApk(serial: String, apkFile: File) {
        adb(serial, "install", "-r", "-t", "-d", apkFile.absolutePath)
    }

    private fun uninstallPackage(serial: String, packageName: String) {
        adb(serial, "uninstall", packageName, requireSuccess = false)
    }

    private fun awaitPackageState(
        serial: String,
        packageName: String,
        expectedInstalled: Boolean,
    ) {
        repeat(PACKAGE_STATE_ATTEMPTS) {
            if (isPackageInstalled(serial, packageName) == expectedInstalled) return
            Thread.sleep(PACKAGE_STATE_POLL_MS)
        }
        throw GradleException(
            "Package state did not converge for $packageName: expectedInstalled=$expectedInstalled",
        )
    }

    private fun isPackageInstalled(serial: String, packageName: String): Boolean {
        val result = adb(
            serial,
            "shell",
            "pm",
            "path",
            packageName,
            requireSuccess = false,
        )
        if (result.stderr.isNotBlank()) {
            throw GradleException(
                "Unable to observe package state for $packageName:\n${result.combinedOutput()}",
            )
        }
        if (result.exitValue != 0) {
            if (result.stdout.isBlank()) {
                return isPackageListedForCurrentUser(serial, packageName)
            }
            throw GradleException(
                "Unable to observe package state for $packageName:\n${result.combinedOutput()}",
            )
        }
        val lines = result.stdout.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        if (lines.isEmpty()) {
            return isPackageListedForCurrentUser(serial, packageName)
        }
        if (lines.all { line -> line.startsWith("package:") }) return true
        throw GradleException(
            "Unexpected pm path output for $packageName:\n${result.combinedOutput()}",
        )
    }

    private fun isPackageListedForCurrentUser(
        serial: String,
        packageName: String,
    ): Boolean {
        val currentUser = adb(
            serial,
            "shell",
            "am",
            "get-current-user",
        ).stdout.trim()
        if (!currentUser.matches(Regex("""\d+"""))) {
            throw GradleException("Unexpected current Android user: $currentUser")
        }
        val result = adb(
            serial,
            "shell",
            "pm",
            "list",
            "packages",
            "--user",
            currentUser,
            packageName,
        )
        if (result.stderr.isNotBlank()) {
            throw GradleException(
                "Unable to list package state for $packageName:\n${result.combinedOutput()}",
            )
        }
        val lines = result.stdout.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        if (lines.isEmpty()) return false
        if (lines == listOf("package:$packageName")) return true
        throw GradleException(
            "Unexpected package-list output for $packageName:\n${result.combinedOutput()}",
        )
    }

    private fun cleanOptionalPlugins(serial: String): Throwable? {
        var cleanupFailure: Throwable? = null
        listOf(NODE_PACKAGE, ACE_PACKAGE).forEach { packageName ->
            try {
                uninstallPackage(serial, packageName)
                awaitPackageState(serial, packageName, expectedInstalled = false)
            } catch (failure: Throwable) {
                if (cleanupFailure == null) {
                    cleanupFailure = failure
                } else {
                    cleanupFailure.addSuppressed(failure)
                }
            }
        }
        return cleanupFailure
    }

    private fun adb(
        serial: String,
        vararg arguments: String,
        requireSuccess: Boolean = true,
    ): CommandResult = runTool(
        executable = adbExecutable.get().asFile,
        arguments = listOf("-s", serial) + arguments,
        requireSuccess = requireSuccess,
    )

    private fun runTool(
        executable: File,
        arguments: List<String>,
        requireSuccess: Boolean = true,
    ): CommandResult {
        val standardOutput = ByteArrayOutputStream()
        val errorOutput = ByteArrayOutputStream()
        val execution = execOperations.exec {
            executable(executable.absolutePath)
            args(arguments)
            isIgnoreExitValue = true
            this.standardOutput = standardOutput
            this.errorOutput = errorOutput
        }
        val result = CommandResult(
            exitValue = execution.exitValue,
            stdout = standardOutput.toString(StandardCharsets.UTF_8),
            stderr = errorOutput.toString(StandardCharsets.UTF_8),
        )
        if (requireSuccess && result.exitValue != 0) {
            throw GradleException(
                "Command failed (${result.exitValue}): " +
                    (listOf(executable.absolutePath) + arguments).joinToString(" ") +
                    "\n${result.combinedOutput()}",
            )
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveSingleApkOutput(apkDirectory: File): File {
        val metadataFile = apkDirectory.resolve("output-metadata.json")
        if (!metadataFile.isFile) {
            throw GradleException("APK output metadata does not exist: $metadataFile")
        }
        val metadata = JsonSlurper().parse(metadataFile) as Map<String, Any?>
        if ((metadata["artifactType"] as? Map<String, Any?>)?.get("type") != "APK") {
            throw GradleException("Output metadata is not for an APK: $metadataFile")
        }
        val elements = metadata["elements"] as? List<Map<String, Any?>>
            ?: throw GradleException("APK output metadata has no elements: $metadataFile")
        if (elements.size != 1 || elements.single()["type"] != "SINGLE") {
            throw GradleException("Expected one universal APK output, found $elements")
        }
        val outputName = elements.single()["outputFile"] as? String
            ?: throw GradleException("APK output metadata does not declare outputFile.")
        if (outputName.isBlank() || File(outputName).name != outputName) {
            throw GradleException(
                "APK output metadata contains an unsafe outputFile: $outputName",
            )
        }
        return apkDirectory.resolve(outputName).also { apkFile ->
            if (!apkFile.isFile) {
                throw GradleException(
                    "APK declared by output metadata does not exist: $apkFile",
                )
            }
        }
    }

    private data class CommandResult(
        val exitValue: Int,
        val stdout: String,
        val stderr: String,
    ) {
        fun combinedOutput(): String = buildString {
            append(stdout)
            if (stdout.isNotEmpty() && stderr.isNotEmpty() && !stdout.endsWith('\n')) {
                append('\n')
            }
            append(stderr)
        }
    }

    private fun File.sha256(): String = inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private companion object {
        const val ACE_PACKAGE = "io.github.supermonster003.autojs6.plugin.ace.editor"
        const val NODE_PACKAGE = "io.github.supermonster003.autojs6.plugin.nodejs"
        const val PACKAGE_STATE_ATTEMPTS = 20
        const val PACKAGE_STATE_POLL_MS = 250L
        const val TARGET_PACKAGE = "io.github.supermonster003.autojs6.plugin.typescript"
        const val TEST_CLASS =
            "io.github.supermonster003.autojs6.plugin.typescript." +
                "R1OptionalPluginMatrixInstrumentationTest"
        const val TEST_METHOD =
            "installedOptionalPluginStateDoesNotAffectRemotePrewarmGolden"
        const val TEST_PACKAGE = "io.github.supermonster003.autojs6.plugin.typescript.test"
        const val TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
    }
}
