package org.autojs.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64

/**
 * Downloads the exact tarball URL from the compiler distribution lock.
 *
 * Redirects are deliberately disabled. The response is hashed while it is
 * staged and is then reopened and verified a second time before publication.
 */
@CacheableTask
abstract class DownloadTypeScriptDistributionTarball : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lockFile: RegularFileProperty

    @get:OutputFile
    abstract val tarballFile: RegularFileProperty

    @get:Input
    abstract val maximumTarballBytes: Property<Long>

    @get:Input
    abstract val connectTimeoutSeconds: Property<Long>

    @get:Input
    abstract val requestTimeoutSeconds: Property<Long>

    init {
        group = "build setup"
        description = "Downloads and verifies the pinned TypeScript npm tarball."
        maximumTarballBytes.convention(
            TypeScriptDistribution.defaultLimits.maximumTarballBytes,
        )
        connectTimeoutSeconds.convention(30L)
        requestTimeoutSeconds.convention(120L)
    }

    @TaskAction
    fun download() {
        val lock = TypeScriptDistribution.readLock(lockFile.get().asFile)
        val output = tarballFile.get().asFile.toPath().toAbsolutePath().normalize()
        val maximumBytes = maximumTarballBytes.get()
        requirePositive(maximumBytes, "maximumTarballBytes")
        requirePositive(connectTimeoutSeconds.get(), "connectTimeoutSeconds")
        requirePositive(requestTimeoutSeconds.get(), "requestTimeoutSeconds")

        if (Files.isSymbolicLink(output)) {
            throw TypeScriptDistributionException(
                "Tarball output must not be a symbolic link: $output",
            )
        }
        if (Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) {
            try {
                TypeScriptDistribution.readAndVerifyTarball(
                    lock = lock,
                    tarballFile = output.toFile(),
                    maximumBytes = maximumBytes,
                )
                logger.info("Reusing verified TypeScript npm tarball: $output")
                return
            } catch (e: TypeScriptDistributionException) {
                logger.info(
                    "Existing TypeScript npm tarball is not valid; downloading it again",
                    e,
                )
            }
        } else if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw TypeScriptDistributionException(
                "Tarball output exists and is not a regular file: $output",
            )
        }

        val parent = output.parent ?: throw TypeScriptDistributionException(
            "Tarball output must have a parent: $output",
        )
        Files.createDirectories(parent)
        val staging = Files.createTempFile(
            parent,
            ".${output.fileName}.",
            ".part",
        )

        try {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds.get()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
            val request = HttpRequest.newBuilder(lock.source)
                .GET()
                .timeout(Duration.ofSeconds(requestTimeoutSeconds.get()))
                .header("Accept", "application/octet-stream")
                .build()
            val response = client.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream(),
            )
            response.body().use { body ->
                if (response.statusCode() != 200) {
                    throw TypeScriptDistributionException(
                        "TypeScript npm tarball request returned HTTP " +
                            response.statusCode(),
                    )
                }
                response.headers().firstValueAsLong("Content-Length")
                    .ifPresent { contentLength ->
                        if (contentLength > maximumBytes) {
                            throw TypeScriptDistributionException(
                                "TypeScript npm tarball Content-Length exceeds " +
                                    "$maximumBytes bytes",
                            )
                        }
                    }
                val downloadedDigests = copyAndDigest(
                    input = body,
                    output = staging.toFile(),
                    maximumBytes = maximumBytes,
                )
                if (downloadedDigests != lock.expectedDigests) {
                    throw TypeScriptDistributionException(
                        "Downloaded TypeScript npm tarball digest mismatch: " +
                            "expected=${lock.expectedDigests}, " +
                            "actual=$downloadedDigests",
                    )
                }
            }

            TypeScriptDistribution.readAndVerifyTarball(
                lock = lock,
                tarballFile = staging.toFile(),
                maximumBytes = maximumBytes,
            )
            try {
                Files.move(
                    staging,
                    output,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    staging,
                    output,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (e: Throwable) {
            Files.deleteIfExists(staging)
            if (e is TypeScriptDistributionException) throw e
            throw TypeScriptDistributionException(
                "Failed to download the pinned TypeScript npm tarball",
                e,
            )
        }
    }

    private fun copyAndDigest(
        input: InputStream,
        output: File,
        maximumBytes: Long,
    ): TypeScriptDistributionDigests {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val sha512 = MessageDigest.getInstance("SHA-512")
        val buffer = ByteArray(64 * 1024)
        var size = 0L

        Files.newOutputStream(
            output.toPath(),
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        ).buffered().use { target ->
            BufferedInputStream(input).use { source ->
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    if (size > maximumBytes - read) {
                        throw TypeScriptDistributionException(
                            "TypeScript npm tarball exceeds $maximumBytes bytes",
                        )
                    }
                    size += read
                    sha1.update(buffer, 0, read)
                    sha256.update(buffer, 0, read)
                    sha512.update(buffer, 0, read)
                    target.write(buffer, 0, read)
                }
            }
        }
        return TypeScriptDistributionDigests(
            sha1 = sha1.digest().toLowerHex(),
            sha256 = sha256.digest().toLowerHex(),
            integrity = "sha512-${Base64.getEncoder().encodeToString(sha512.digest())}",
        )
    }

    private fun ByteArray.toLowerHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun requirePositive(value: Long, name: String) {
        if (value <= 0) {
            throw TypeScriptDistributionException("$name must be positive")
        }
    }
}

/**
 * Verifies and safely extracts the allowlisted TypeScript compiler files.
 */
@CacheableTask
abstract class GenerateTypeScriptDistribution : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lockFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tarballFile: RegularFileProperty

    @get:OutputDirectory
    abstract val distributionDirectory: DirectoryProperty

    @get:Input
    abstract val maximumTarballBytes: Property<Long>

    @get:Input
    abstract val maximumArchiveEntries: Property<Int>

    @get:Input
    abstract val maximumArchiveFileBytes: Property<Long>

    @get:Input
    abstract val maximumArchiveBytes: Property<Long>

    init {
        group = "build setup"
        description = "Generates the canonical TypeScript compiler distribution assets."
        applyDefaultLimits()
    }

    @TaskAction
    fun generate() {
        TypeScriptDistribution.generate(
            lockFile = lockFile.get().asFile,
            tarballFile = tarballFile.get().asFile,
            outputDirectory = distributionDirectory.get().asFile,
            limits = configuredLimits(),
        )
    }

    private fun applyDefaultLimits() {
        val defaults = TypeScriptDistribution.defaultLimits
        maximumTarballBytes.convention(defaults.maximumTarballBytes)
        maximumArchiveEntries.convention(defaults.maximumArchiveEntries)
        maximumArchiveFileBytes.convention(defaults.maximumArchiveFileBytes)
        maximumArchiveBytes.convention(defaults.maximumArchiveBytes)
    }

    private fun configuredLimits() = TypeScriptDistributionLimits(
        maximumTarballBytes = maximumTarballBytes.get(),
        maximumArchiveEntries = maximumArchiveEntries.get(),
        maximumArchiveFileBytes = maximumArchiveFileBytes.get(),
        maximumArchiveBytes = maximumArchiveBytes.get(),
    )
}

/**
 * Independently re-reads the pinned tarball and checks every generated byte.
 *
 * This task intentionally has no outputs, so Gradle executes it whenever it is
 * requested instead of treating an old verification marker as sufficient.
 */
@DisableCachingByDefault(because = "Verification task intentionally has no outputs")
abstract class VerifyTypeScriptDistribution : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lockFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tarballFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val distributionDirectory: DirectoryProperty

    @get:Input
    abstract val maximumTarballBytes: Property<Long>

    @get:Input
    abstract val maximumArchiveEntries: Property<Int>

    @get:Input
    abstract val maximumArchiveFileBytes: Property<Long>

    @get:Input
    abstract val maximumArchiveBytes: Property<Long>

    init {
        group = "verification"
        description = "Verifies the canonical TypeScript compiler distribution."
        applyDefaultLimits()
    }

    @TaskAction
    fun verify() {
        TypeScriptDistribution.verify(
            lockFile = lockFile.get().asFile,
            tarballFile = tarballFile.get().asFile,
            distributionDirectory = distributionDirectory.get().asFile,
            limits = configuredLimits(),
        )
    }

    private fun applyDefaultLimits() {
        val defaults = TypeScriptDistribution.defaultLimits
        maximumTarballBytes.convention(defaults.maximumTarballBytes)
        maximumArchiveEntries.convention(defaults.maximumArchiveEntries)
        maximumArchiveFileBytes.convention(defaults.maximumArchiveFileBytes)
        maximumArchiveBytes.convention(defaults.maximumArchiveBytes)
    }

    private fun configuredLimits() = TypeScriptDistributionLimits(
        maximumTarballBytes = maximumTarballBytes.get(),
        maximumArchiveEntries = maximumArchiveEntries.get(),
        maximumArchiveFileBytes = maximumArchiveFileBytes.get(),
        maximumArchiveBytes = maximumArchiveBytes.get(),
    )
}
