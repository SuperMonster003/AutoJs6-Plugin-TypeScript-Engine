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

/** Downloads the exact public Rhino declaration tarball pinned by the R2 lock. */
@CacheableTask
abstract class DownloadR2DeclarationDistributionTarball : DefaultTask() {

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
        description = "Downloads and verifies the pinned R2 Rhino declaration tarball."
        maximumTarballBytes.convention(
            R2DeclarationDistribution.defaultLimits.maximumTarballBytes,
        )
        connectTimeoutSeconds.convention(30L)
        requestTimeoutSeconds.convention(120L)
    }

    @TaskAction
    fun download() {
        val lock = lockFile.get().asFile
        val spec = R2DeclarationDistribution.rhinoDownloadSpec(lock)
        val output = tarballFile.get().asFile.toPath().toAbsolutePath().normalize()
        val maximumBytes = maximumTarballBytes.get()
        requirePositive(maximumBytes, "maximumTarballBytes")
        requirePositive(connectTimeoutSeconds.get(), "connectTimeoutSeconds")
        requirePositive(requestTimeoutSeconds.get(), "requestTimeoutSeconds")
        if (spec.tarballBytes > maximumBytes) {
            throw R2DeclarationDistributionException(
                "Locked Rhino tarball exceeds maximumTarballBytes",
            )
        }
        if (Files.isSymbolicLink(output)) {
            throw R2DeclarationDistributionException(
                "Rhino tarball output must not be a symbolic link: $output",
            )
        }
        if (Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) {
            try {
                R2DeclarationDistribution.verifyRhinoTarball(lock, output.toFile(), maximumBytes)
                logger.info("Reusing verified R2 Rhino declaration tarball: $output")
                return
            } catch (e: R2DeclarationDistributionException) {
                logger.info("Existing R2 Rhino tarball is invalid; downloading again", e)
            }
        } else if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw R2DeclarationDistributionException(
                "Rhino tarball output exists and is not a regular file: $output",
            )
        }

        val parent = output.parent ?: throw R2DeclarationDistributionException(
            "Rhino tarball output must have a parent: $output",
        )
        Files.createDirectories(parent)
        val staging = Files.createTempFile(parent, ".${output.fileName}.", ".part")
        try {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds.get()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
            val request = HttpRequest.newBuilder(spec.source)
                .GET()
                .timeout(Duration.ofSeconds(requestTimeoutSeconds.get()))
                .header("Accept", "application/octet-stream")
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { body ->
                if (response.statusCode() != 200) {
                    throw R2DeclarationDistributionException(
                        "Rhino declaration tarball request returned HTTP ${response.statusCode()}",
                    )
                }
                response.headers().firstValueAsLong("Content-Length").ifPresent { length ->
                    if (length != spec.tarballBytes || length > maximumBytes) {
                        throw R2DeclarationDistributionException(
                            "Rhino declaration tarball Content-Length does not match lock",
                        )
                    }
                }
                val downloaded = copyAndDigest(body, staging, maximumBytes)
                if (downloaded.first != spec.tarballBytes || downloaded.second != spec.digests) {
                    throw R2DeclarationDistributionException(
                        "Downloaded Rhino declaration tarball does not match the lock",
                    )
                }
            }
            R2DeclarationDistribution.verifyRhinoTarball(lock, staging.toFile(), maximumBytes)
            try {
                Files.move(
                    staging,
                    output,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staging, output, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Throwable) {
            Files.deleteIfExists(staging)
            if (e is R2DeclarationDistributionException) throw e
            throw R2DeclarationDistributionException(
                "Failed to download the pinned Rhino declaration tarball",
                e,
            )
        }
    }

    private fun copyAndDigest(
        input: InputStream,
        output: java.nio.file.Path,
        maximumBytes: Long,
    ): Pair<Long, R2DeclarationDigests> {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val sha512 = MessageDigest.getInstance("SHA-512")
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        Files.newOutputStream(
            output,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        ).buffered().use { target ->
            BufferedInputStream(input).use { source ->
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    if (size > maximumBytes - read) {
                        throw R2DeclarationDistributionException(
                            "Rhino declaration tarball exceeds $maximumBytes bytes",
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
        return size to R2DeclarationDigests(
            sha1 = sha1.digest().toLowerHex(),
            sha256 = sha256.digest().toLowerHex(),
            integrity = "sha512-${Base64.getEncoder().encodeToString(sha512.digest())}",
        )
    }

    private fun ByteArray.toLowerHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun requirePositive(value: Long, name: String) {
        if (value <= 0L) throw R2DeclarationDistributionException("$name must be positive")
    }
}

/** Generates the independent assets/r2-declarations/v2 distribution root. */
@CacheableTask
abstract class GenerateR2DeclarationDistribution : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lockFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rhinoTarballFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nodeSourceDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val typescriptDistributionDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val distributionDirectory: DirectoryProperty

    @get:Input
    abstract val declarationReleaseVersion: Property<String>

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
        description = "Generates the canonical R2 declaration distribution assets."
        applyDefaultLimits()
    }

    @TaskAction
    fun generate() {
        R2DeclarationDistribution.generate(
            lockFile = lockFile.get().asFile,
            rhinoTarballFile = rhinoTarballFile.get().asFile,
            nodeSourceDirectory = nodeSourceDirectory.get().asFile,
            typescriptDistributionDirectory = typescriptDistributionDirectory.get().asFile,
            outputDirectory = distributionDirectory.get().asFile,
            declarationReleaseVersion = declarationReleaseVersion.get(),
            limits = configuredLimits(),
        )
    }

    private fun applyDefaultLimits() {
        val defaults = R2DeclarationDistribution.defaultLimits
        maximumTarballBytes.convention(defaults.maximumTarballBytes)
        maximumArchiveEntries.convention(defaults.maximumArchiveEntries)
        maximumArchiveFileBytes.convention(defaults.maximumArchiveFileBytes)
        maximumArchiveBytes.convention(defaults.maximumArchiveBytes)
    }

    private fun configuredLimits() = R2DeclarationDistributionLimits(
        maximumTarballBytes = maximumTarballBytes.get(),
        maximumArchiveEntries = maximumArchiveEntries.get(),
        maximumArchiveFileBytes = maximumArchiveFileBytes.get(),
        maximumArchiveBytes = maximumArchiveBytes.get(),
    )
}

/** Reconstructs every expected byte and rejects missing, extra, or changed output. */
@DisableCachingByDefault(because = "Verification task intentionally has no outputs")
abstract class VerifyR2DeclarationDistribution : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lockFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rhinoTarballFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nodeSourceDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val typescriptDistributionDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val distributionDirectory: DirectoryProperty

    @get:Input
    abstract val declarationReleaseVersion: Property<String>

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
        description = "Verifies the canonical R2 declaration distribution."
        applyDefaultLimits()
    }

    @TaskAction
    fun verify() {
        R2DeclarationDistribution.verify(
            lockFile = lockFile.get().asFile,
            rhinoTarballFile = rhinoTarballFile.get().asFile,
            nodeSourceDirectory = nodeSourceDirectory.get().asFile,
            typescriptDistributionDirectory = typescriptDistributionDirectory.get().asFile,
            distributionDirectory = distributionDirectory.get().asFile,
            declarationReleaseVersion = declarationReleaseVersion.get(),
            limits = configuredLimits(),
        )
    }

    private fun applyDefaultLimits() {
        val defaults = R2DeclarationDistribution.defaultLimits
        maximumTarballBytes.convention(defaults.maximumTarballBytes)
        maximumArchiveEntries.convention(defaults.maximumArchiveEntries)
        maximumArchiveFileBytes.convention(defaults.maximumArchiveFileBytes)
        maximumArchiveBytes.convention(defaults.maximumArchiveBytes)
    }

    private fun configuredLimits() = R2DeclarationDistributionLimits(
        maximumTarballBytes = maximumTarballBytes.get(),
        maximumArchiveEntries = maximumArchiveEntries.get(),
        maximumArchiveFileBytes = maximumArchiveFileBytes.get(),
        maximumArchiveBytes = maximumArchiveBytes.get(),
    )
}
