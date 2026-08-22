package org.autojs.build

import groovy.json.JsonSlurper
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.gradle.api.GradleException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64
import java.util.Comparator
import java.util.Locale
import java.util.UUID

/**
 * Resource limits applied while reading the pinned npm tarball.
 *
 * Limits cover the complete archive, including entries that are not selected for
 * the generated distribution.
 */
data class TypeScriptDistributionLimits(
    val maximumTarballBytes: Long = 8L * 1024L * 1024L,
    val maximumArchiveEntries: Int = 256,
    val maximumArchiveFileBytes: Long = 12L * 1024L * 1024L,
    val maximumArchiveBytes: Long = 32L * 1024L * 1024L,
) {
    init {
        require(maximumTarballBytes > 0)
        require(maximumArchiveEntries > 0)
        require(maximumArchiveFileBytes > 0)
        require(maximumArchiveBytes > 0)
        require(maximumArchiveFileBytes <= maximumArchiveBytes)
    }
}

data class TypeScriptDistributionDigests(
    val sha1: String,
    val sha256: String,
    val integrity: String,
)

data class TypeScriptDistributionFile(
    val path: String,
    val size: Long,
    val sha256: String,
)

class TypeScriptDistributionException(
    message: String,
    cause: Throwable? = null,
) : GradleException(message, cause)

/**
 * Pure distribution generation and verification entry points used by the Gradle
 * task types in [TypeScriptDistributionTasks.kt].
 */
object TypeScriptDistribution {

    const val MANIFEST_FILE_NAME = "distribution.json"
    const val GENERATOR_ID = "org.autojs.build.typescript-distribution"
    const val GENERATOR_REVISION = 1
    const val MANIFEST_SCHEMA_VERSION = 1

    private const val NPM_PACKAGE_ROOT = "package"
    private const val BUFFER_SIZE = 64 * 1024
    private const val TAR_BLOCK_SIZE = 512
    private const val MAXIMUM_TAR_TRAILING_ZERO_BLOCKS = 20
    private const val TAR_NAME_OFFSET = 0
    private const val TAR_NAME_LENGTH = 100
    private const val TAR_SIZE_OFFSET = 124
    private const val TAR_SIZE_LENGTH = 12
    private const val TAR_CHECKSUM_OFFSET = 148
    private const val TAR_CHECKSUM_LENGTH = 8
    private const val TAR_TYPE_OFFSET = 156
    private const val TAR_PREFIX_OFFSET = 345
    private const val TAR_PREFIX_LENGTH = 155
    private const val GENERATOR_DESCRIPTOR =
        "id=org.autojs.build.typescript-distribution\n" +
            "generatorRevision=1\n" +
            "manifestSchemaVersion=1\n" +
            "fileFingerprint=path-nul-size-nul-sha256-lf\n" +
            "json=sorted-keys-utf8-lf\n"

    val defaultLimits = TypeScriptDistributionLimits()

    @JvmStatic
    @JvmOverloads
    fun verifyTarball(
        lockFile: File,
        tarballFile: File,
        maximumTarballBytes: Long = defaultLimits.maximumTarballBytes,
    ): TypeScriptDistributionDigests {
        val lock = DistributionLock.read(lockFile)
        return readVerifiedTarball(lock, tarballFile.toPath(), maximumTarballBytes).digests
    }

    @JvmStatic
    @JvmOverloads
    fun generate(
        lockFile: File,
        tarballFile: File,
        outputDirectory: File,
        limits: TypeScriptDistributionLimits = defaultLimits,
    ) {
        val lock = DistributionLock.read(lockFile)
        val verifiedTarball = readVerifiedTarball(
            lock = lock,
            tarball = tarballFile.toPath(),
            maximumBytes = limits.maximumTarballBytes,
        )
        val output = outputDirectory.toPath().toAbsolutePath().normalize()
        val parent = output.parent ?: fail("Distribution output must have a parent: $output")
        Files.createDirectories(parent)
        refuseSymbolicLink(output, "distribution output")

        val staging = Files.createTempDirectory(
            parent,
            ".${output.fileName}.staging-",
        )
        try {
            val files = inspectArchive(
                lock = lock,
                tarballBytes = verifiedTarball.bytes,
                limits = limits,
                extractionRoot = staging,
            )
            val manifestBytes = createManifest(lock, files)
            Files.write(
                staging.resolve(MANIFEST_FILE_NAME),
                manifestBytes,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            )
            verifyGeneratedDirectory(
                lock = lock,
                expectedFiles = files,
                distributionRoot = staging,
            )
            publishDirectory(staging, output)
        } catch (e: Throwable) {
            deleteTreeIfPresent(staging)
            if (e is TypeScriptDistributionException) throw e
            throw TypeScriptDistributionException(
                "Failed to generate TypeScript compiler distribution",
                e,
            )
        }
    }

    @JvmStatic
    @JvmOverloads
    fun verify(
        lockFile: File,
        tarballFile: File,
        distributionDirectory: File,
        limits: TypeScriptDistributionLimits = defaultLimits,
    ) {
        val lock = DistributionLock.read(lockFile)
        val verifiedTarball = readVerifiedTarball(
            lock = lock,
            tarball = tarballFile.toPath(),
            maximumBytes = limits.maximumTarballBytes,
        )
        val expectedFiles = inspectArchive(
            lock = lock,
            tarballBytes = verifiedTarball.bytes,
            limits = limits,
            extractionRoot = null,
        )
        verifyGeneratedDirectory(
            lock = lock,
            expectedFiles = expectedFiles,
            distributionRoot = distributionDirectory.toPath().toAbsolutePath().normalize(),
        )
    }

    internal fun readLock(lockFile: File): DistributionLock = DistributionLock.read(lockFile)

    internal fun readAndVerifyTarball(
        lock: DistributionLock,
        tarballFile: File,
        maximumBytes: Long,
    ): TypeScriptDistributionDigests =
        readVerifiedTarball(lock, tarballFile.toPath(), maximumBytes).digests

    private fun inspectArchive(
        lock: DistributionLock,
        tarballBytes: ByteArray,
        limits: TypeScriptDistributionLimits,
        extractionRoot: Path?,
    ): List<TypeScriptDistributionFile> {
        val tarBytes = decompressAndPreflightTarball(tarballBytes, limits)
        val canonicalNames = mutableMapOf<String, String>()
        val selectedFiles = mutableListOf<TypeScriptDistributionFile>()
        var entryCount = 0
        var archiveBytes = 0L

        try {
            ByteArrayInputStream(tarBytes).use { byteInput ->
                TarArchiveInputStream(
                    byteInput,
                    StandardCharsets.UTF_8.name(),
                ).use { tarInput ->
                    while (true) {
                        val entry = tarInput.nextEntry ?: break
                        if (!entry.isCheckSumOK) {
                            fail("Archive entry has an invalid header checksum: ${entry.name}")
                        }
                        entryCount += 1
                        if (entryCount > limits.maximumArchiveEntries) {
                            fail(
                                "Archive contains more than " +
                                    "${limits.maximumArchiveEntries} entries",
                            )
                        }

                        val name = validateArchiveEntryName(
                            rawName = entry.name,
                            isDirectory = entry.isDirectory,
                        )
                        val collisionKey = name.lowercase(Locale.ROOT)
                        canonicalNames.putIfAbsent(collisionKey, name)?.let { existing ->
                            fail(
                                "Archive contains duplicate or case-fold-colliding path: " +
                                    "$existing / $name",
                            )
                        }
                        if (entry.isSymbolicLink) {
                            fail("Archive contains symbolic link: $name")
                        }
                        if (entry.isLink) {
                            fail("Archive contains hard link: $name")
                        }
                        if (entry.isSparse) {
                            fail("Archive contains sparse entry: $name")
                        }
                        if (!entry.isFile) {
                            fail("Archive contains unsupported entry type: $name")
                        }
                        if (!tarInput.canReadEntryData(entry)) {
                            fail("Archive entry cannot be read safely: $name")
                        }

                        val declaredSize = entry.size
                        if (declaredSize < 0L) {
                            fail("Archive file has unknown size: $name")
                        }
                        if (declaredSize > limits.maximumArchiveFileBytes) {
                            fail(
                                "Archive file exceeds ${limits.maximumArchiveFileBytes} bytes: " +
                                    "$name ($declaredSize bytes)",
                            )
                        }
                        if (archiveBytes > limits.maximumArchiveBytes - declaredSize) {
                            fail(
                                "Archive expands beyond ${limits.maximumArchiveBytes} bytes",
                            )
                        }
                        archiveBytes += declaredSize

                        val selected = lock.isSelected(name)
                        val relativePath = if (selected) lock.outputPath(name) else null
                        val digest = if (selected) {
                            MessageDigest.getInstance("SHA-256")
                        } else {
                            null
                        }
                        val output = relativePath?.let { path ->
                            openSelectedOutput(extractionRoot, path)
                        }
                        val actualSize = output.useNullable { stream ->
                            copyCurrentTarEntry(
                                input = tarInput,
                                output = stream,
                                digest = digest,
                                declaredSize = declaredSize,
                                name = name,
                            )
                        }
                        if (selected) {
                            selectedFiles += TypeScriptDistributionFile(
                                path = requireNotNull(relativePath),
                                size = actualSize,
                                sha256 = requireNotNull(digest).digest().toLowerHex(),
                            )
                        }
                    }
                }
            }
        } catch (e: TypeScriptDistributionException) {
            throw e
        } catch (e: Throwable) {
            throw TypeScriptDistributionException(
                "Failed to read TypeScript npm tarball safely",
                e,
            )
        }

        return selectedFiles.sortedBy(TypeScriptDistributionFile::path).also { files ->
            lock.verifySelectedFiles(files)
        }
    }

    private fun decompressAndPreflightTarball(
        tarballBytes: ByteArray,
        limits: TypeScriptDistributionLimits,
    ): ByteArray {
        if (limits.maximumArchiveBytes > Int.MAX_VALUE) {
            fail("maximumArchiveBytes exceeds the in-memory preflight limit")
        }
        try {
            val compressedInput = ByteArrayInputStream(tarballBytes)
            val tarOutput = ByteArrayOutputStream()
            // Gradle exposes commons-compress from its parent classloader. Keep this build
            // logic compatible with that runtime (currently 1.26.x) instead of linking the
            // newer 1.28-only builder API. Reading one member and requiring exact compressed
            // consumption still rejects concatenated members and trailing data.
            GzipCompressorInputStream(compressedInput, false)
                .use { gzipInput ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var expandedBytes = 0L
                    while (true) {
                        val read = gzipInput.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        if (expandedBytes > limits.maximumArchiveBytes - read) {
                            fail(
                                "Expanded tar stream exceeds " +
                                    "${limits.maximumArchiveBytes} bytes",
                            )
                        }
                        expandedBytes += read
                        tarOutput.write(buffer, 0, read)
                    }
                    if (
                        gzipInput.compressedCount != tarballBytes.size.toLong() ||
                        compressedInput.available() != 0
                    ) {
                        fail("TypeScript npm tarball contains trailing compressed data")
                    }
                }
            return tarOutput.toByteArray().also { tarBytes ->
                preflightTarArchive(tarBytes, limits)
            }
        } catch (e: TypeScriptDistributionException) {
            throw e
        } catch (e: Throwable) {
            throw TypeScriptDistributionException(
                "Failed to decompress the TypeScript npm tarball safely",
                e,
            )
        }
    }

    private fun preflightTarArchive(
        tarBytes: ByteArray,
        limits: TypeScriptDistributionLimits,
    ) {
        if (tarBytes.isEmpty() || tarBytes.size % TAR_BLOCK_SIZE != 0) {
            fail("Expanded tar stream is empty or not aligned to 512-byte blocks")
        }

        val canonicalNames = mutableMapOf<String, String>()
        var offset = 0
        var entryCount = 0
        var archiveFileBytes = 0L

        while (offset < tarBytes.size) {
            if (tarBytes.isZeroBlock(offset)) {
                if (
                    offset + 2 * TAR_BLOCK_SIZE > tarBytes.size ||
                    !tarBytes.isZeroBlock(offset + TAR_BLOCK_SIZE)
                ) {
                    fail("Tar archive must terminate with at least two zero blocks")
                }
                val trailingBlocks = (tarBytes.size - offset) / TAR_BLOCK_SIZE
                if (trailingBlocks > MAXIMUM_TAR_TRAILING_ZERO_BLOCKS) {
                    fail(
                        "Tar archive has too many trailing zero blocks: $trailingBlocks",
                    )
                }
                if (tarBytes.anyNonZero(offset, tarBytes.size)) {
                    fail("Tar archive contains non-zero data after its end marker")
                }
                return
            }

            verifyTarHeaderChecksum(tarBytes, offset)
            entryCount += 1
            if (entryCount > limits.maximumArchiveEntries) {
                fail(
                    "Archive contains more than ${limits.maximumArchiveEntries} entries",
                )
            }

            val type = tarBytes[offset + TAR_TYPE_OFFSET].toInt() and 0xff
            if (type != 0 && type != '0'.code) {
                val typeLabel = when (type.toChar()) {
                    '1' -> "hard link"
                    '2' -> "symbolic link"
                    'g' -> "global PAX metadata"
                    'x' -> "PAX metadata"
                    'K' -> "GNU long-link metadata"
                    'L' -> "GNU long-name metadata"
                    else -> "type 0x${type.toString(16).padStart(2, '0')}"
                }
                fail("Archive contains unsupported $typeLabel entry")
            }

            val name = readTarEntryName(tarBytes, offset)
            validateArchiveEntryName(name, isDirectory = false)
            val collisionKey = name.lowercase(Locale.ROOT)
            canonicalNames.putIfAbsent(collisionKey, name)?.let { existing ->
                fail(
                    "Archive contains duplicate or case-fold-colliding path: " +
                        "$existing / $name",
                )
            }

            val declaredSize = parseTarOctal(
                bytes = tarBytes,
                offset = offset + TAR_SIZE_OFFSET,
                length = TAR_SIZE_LENGTH,
                fieldName = "size",
            )
            if (declaredSize > limits.maximumArchiveFileBytes) {
                fail(
                    "Archive file exceeds ${limits.maximumArchiveFileBytes} bytes: " +
                        "$name ($declaredSize bytes)",
                )
            }
            if (archiveFileBytes > limits.maximumArchiveBytes - declaredSize) {
                fail("Archive expands beyond ${limits.maximumArchiveBytes} bytes")
            }
            archiveFileBytes += declaredSize

            val dataOffset = offset.toLong() + TAR_BLOCK_SIZE
            val paddedSize = declaredSize
                .plus(TAR_BLOCK_SIZE - 1L)
                .div(TAR_BLOCK_SIZE)
                .times(TAR_BLOCK_SIZE)
            val nextOffset = dataOffset + paddedSize
            if (nextOffset > tarBytes.size.toLong() || nextOffset > Int.MAX_VALUE) {
                fail("Tar archive entry is truncated or exceeds addressable bounds: $name")
            }
            val contentEnd = dataOffset + declaredSize
            if (
                tarBytes.anyNonZero(
                    contentEnd.toInt(),
                    nextOffset.toInt(),
                )
            ) {
                fail("Tar archive entry has non-zero padding: $name")
            }
            offset = nextOffset.toInt()
        }

        fail("Tar archive is missing its two-block end marker")
    }

    private fun verifyTarHeaderChecksum(
        bytes: ByteArray,
        headerOffset: Int,
    ) {
        val expected = parseTarOctal(
            bytes = bytes,
            offset = headerOffset + TAR_CHECKSUM_OFFSET,
            length = TAR_CHECKSUM_LENGTH,
            fieldName = "checksum",
        )
        var actual = 0L
        for (index in 0 until TAR_BLOCK_SIZE) {
            actual += when (index) {
                in TAR_CHECKSUM_OFFSET until TAR_CHECKSUM_OFFSET + TAR_CHECKSUM_LENGTH -> 0x20
                else -> bytes[headerOffset + index].toInt() and 0xff
            }
        }
        if (actual != expected) {
            fail("Tar archive header checksum mismatch: expected=$expected, actual=$actual")
        }
    }

    private fun readTarEntryName(
        bytes: ByteArray,
        headerOffset: Int,
    ): String {
        val name = readTarString(
            bytes = bytes,
            offset = headerOffset + TAR_NAME_OFFSET,
            length = TAR_NAME_LENGTH,
            fieldName = "name",
        )
        val prefix = readTarString(
            bytes = bytes,
            offset = headerOffset + TAR_PREFIX_OFFSET,
            length = TAR_PREFIX_LENGTH,
            fieldName = "prefix",
        )
        if (name.isEmpty()) {
            fail("Tar archive header has an empty entry name")
        }
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    private fun readTarString(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        fieldName: String,
    ): String {
        val end = offset + length
        var terminator = offset
        while (terminator < end && bytes[terminator].toInt() != 0) {
            terminator += 1
        }
        for (index in terminator until end) {
            if (bytes[index].toInt() != 0) {
                fail("Tar archive $fieldName field has data after its NUL terminator")
            }
        }
        return String(
            bytes,
            offset,
            terminator - offset,
            StandardCharsets.US_ASCII,
        )
    }

    private fun parseTarOctal(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        fieldName: String,
    ): Long {
        val end = offset + length
        if (bytes[offset].toInt() and 0x80 != 0) {
            fail("Tar archive $fieldName field uses unsupported base-256 encoding")
        }

        var index = offset
        while (
            index < end &&
            (bytes[index].toInt() == 0 || bytes[index].toInt() == 0x20)
        ) {
            index += 1
        }
        var value = 0L
        var digits = 0
        while (index < end && bytes[index].toInt() in '0'.code..'7'.code) {
            val digit = bytes[index].toInt() - '0'.code
            if (value > (Long.MAX_VALUE - digit) / 8L) {
                fail("Tar archive $fieldName field overflows")
            }
            value = value * 8L + digit
            digits += 1
            index += 1
        }
        while (index < end) {
            if (bytes[index].toInt() != 0 && bytes[index].toInt() != 0x20) {
                fail("Tar archive $fieldName field is not canonical octal")
            }
            index += 1
        }
        if (digits == 0) {
            return 0L
        }
        return value
    }

    private fun ByteArray.isZeroBlock(offset: Int): Boolean =
        !anyNonZero(offset, offset + TAR_BLOCK_SIZE)

    private fun ByteArray.anyNonZero(start: Int, end: Int): Boolean {
        for (index in start until end) {
            if (this[index].toInt() != 0) return true
        }
        return false
    }

    private fun copyCurrentTarEntry(
        input: InputStream,
        output: OutputStream?,
        digest: MessageDigest?,
        declaredSize: Long,
        name: String,
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var actualSize = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            if (actualSize > declaredSize - read) {
                fail("Archive entry contains more data than declared: $name")
            }
            actualSize += read
            digest?.update(buffer, 0, read)
            output?.write(buffer, 0, read)
        }
        if (actualSize != declaredSize) {
            fail(
                "Archive entry size mismatch for $name: " +
                    "declared=$declaredSize, actual=$actualSize",
            )
        }
        return actualSize
    }

    private fun openSelectedOutput(
        extractionRoot: Path?,
        relativePath: String,
    ): OutputStream? {
        extractionRoot ?: return null
        val target = extractionRoot.resolve(relativePath).normalize()
        if (!target.startsWith(extractionRoot)) {
            fail("Selected archive path escapes output: $relativePath")
        }
        Files.createDirectories(target.parent)
        return Files.newOutputStream(
            target,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
    }

    private fun validateArchiveEntryName(
        rawName: String,
        isDirectory: Boolean,
    ): String {
        if (rawName.isEmpty()) fail("Archive contains an empty path")
        if (rawName.any { it.code < 0x21 || it.code > 0x7e }) {
            fail("Archive path is not printable ASCII: ${rawName.asPrintablePath()}")
        }
        if ('\\' in rawName) {
            fail("Archive path contains a backslash: $rawName")
        }
        if (rawName.startsWith('/')) {
            fail("Archive path is absolute: $rawName")
        }
        if (DRIVE_PATH.matchesAt(rawName, 0)) {
            fail("Archive path has a drive prefix: $rawName")
        }

        val name = when {
            rawName.endsWith('/') && isDirectory -> rawName.dropLast(1)
            rawName.endsWith('/') -> fail("Archive file path has a trailing slash: $rawName")
            else -> rawName
        }
        val parts = name.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) {
            fail("Archive path is not canonical: $rawName")
        }
        if (name != NPM_PACKAGE_ROOT && !name.startsWith("$NPM_PACKAGE_ROOT/")) {
            fail("Archive entry is outside the npm package root: $rawName")
        }
        return name
    }

    private fun verifyGeneratedDirectory(
        lock: DistributionLock,
        expectedFiles: List<TypeScriptDistributionFile>,
        distributionRoot: Path,
    ) {
        refuseSymbolicLink(distributionRoot, "distribution root")
        if (!Files.isDirectory(distributionRoot, LinkOption.NOFOLLOW_LINKS)) {
            fail("Distribution directory does not exist: $distributionRoot")
        }

        val actualFiles = mutableListOf<TypeScriptDistributionFile>()
        val actualDirectories = mutableSetOf<String>()
        var manifestBytes: ByteArray? = null

        Files.walk(distributionRoot).use { paths ->
            paths.forEach { path ->
                if (path == distributionRoot) return@forEach
                val relative = distributionRoot.relativeToInvariant(path)
                if (Files.isSymbolicLink(path)) {
                    fail("Distribution contains symbolic link: $relative")
                }
                when {
                    Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) -> {
                        actualDirectories += relative
                    }

                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> {
                        if (relative == MANIFEST_FILE_NAME) {
                            manifestBytes = Files.readAllBytes(path)
                        } else {
                            actualFiles += hashDistributionFile(path, relative)
                        }
                    }

                    else -> fail("Distribution contains unsupported file type: $relative")
                }
            }
        }

        val sortedActual = actualFiles.sortedBy(TypeScriptDistributionFile::path)
        if (sortedActual != expectedFiles) {
            fail(
                "Distribution files do not match the pinned tarball.\n" +
                    describeFileDifference(expectedFiles, sortedActual),
            )
        }

        val expectedDirectories = expectedFiles
            .flatMap { file -> file.path.parentDirectories() }
            .toSet()
        if (actualDirectories != expectedDirectories) {
            fail(
                "Distribution directories do not match the selected file set: " +
                    "expected=$expectedDirectories, actual=$actualDirectories",
            )
        }

        val expectedManifest = createManifest(lock, expectedFiles)
        val actualManifest = manifestBytes
            ?: fail("Distribution is missing $MANIFEST_FILE_NAME")
        if (!MessageDigest.isEqual(expectedManifest, actualManifest)) {
            fail("$MANIFEST_FILE_NAME is not the canonical manifest for this distribution")
        }
    }

    private fun createManifest(
        lock: DistributionLock,
        files: List<TypeScriptDistributionFile>,
    ): ByteArray {
        val compiler = files.singleFor(lock.outputPath(lock.runtimeEntry))
        val compilerApi = files.singleFor(lock.outputPath(lock.declarationEntry))
        val standardLibraries = files.filter { lock.isStandardLibraryOutput(it.path) }
        val notices = listOf(
            files.singleFor(lock.outputPath(lock.licenseEntry)),
            files.singleFor(lock.outputPath(lock.thirdPartyNoticeEntry)),
        ).sortedBy(TypeScriptDistributionFile::path)

        val generatorFingerprint = sha256(
            GENERATOR_DESCRIPTOR.toByteArray(StandardCharsets.UTF_8),
        )
        val manifest = mapOf(
            "components" to mapOf(
                "compiler" to singleFileComponent(compiler),
                "compilerApi" to singleFileComponent(compilerApi),
                "notices" to fileGroupComponent(notices),
                "standardLibraries" to (
                    fileGroupComponent(standardLibraries) +
                        mapOf("pattern" to lock.outputStandardLibraryGlob)
                    ),
            ),
            "files" to files.sortedBy(TypeScriptDistributionFile::path).map(::fileMap),
            "fingerprint" to distributionFingerprint(files, generatorFingerprint),
            "generator" to mapOf(
                "fingerprint" to generatorFingerprint,
                "id" to GENERATOR_ID,
                "revision" to GENERATOR_REVISION.toLong(),
            ),
            "package" to mapOf(
                "name" to lock.packageName,
                "version" to lock.version,
            ),
            "schemaVersion" to MANIFEST_SCHEMA_VERSION.toLong(),
            "tarball" to mapOf(
                "integrity" to lock.expectedDigests.integrity,
                "sha1" to lock.expectedDigests.sha1,
                "sha256" to lock.expectedDigests.sha256,
                "source" to lock.source.toASCIIString(),
            ),
        )
        return CanonicalJson.encode(manifest)
    }

    private fun singleFileComponent(
        file: TypeScriptDistributionFile,
    ): Map<String, Any> = fileMap(file) + mapOf(
        "fingerprint" to fingerprint(listOf(file)),
    )

    private fun fileGroupComponent(
        files: List<TypeScriptDistributionFile>,
    ): Map<String, Any> = mapOf(
        "fileCount" to files.size.toLong(),
        "fingerprint" to fingerprint(files),
        "totalBytes" to files.sumOf(TypeScriptDistributionFile::size),
    )

    private fun fileMap(file: TypeScriptDistributionFile): Map<String, Any> = mapOf(
        "path" to file.path,
        "sha256" to file.sha256,
        "size" to file.size,
    )

    private fun fingerprint(files: List<TypeScriptDistributionFile>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        updateFileFingerprint(digest, files)
        return digest.digest().toLowerHex()
    }

    private fun distributionFingerprint(
        files: List<TypeScriptDistributionFile>,
        generatorFingerprint: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("generator".toByteArray(StandardCharsets.US_ASCII))
        digest.update(0)
        digest.update(generatorFingerprint.toByteArray(StandardCharsets.US_ASCII))
        digest.update('\n'.code.toByte())
        updateFileFingerprint(digest, files)
        return digest.digest().toLowerHex()
    }

    private fun updateFileFingerprint(
        digest: MessageDigest,
        files: List<TypeScriptDistributionFile>,
    ) {
        files.sortedBy(TypeScriptDistributionFile::path).forEach { file ->
            digest.update(file.path.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(file.size.toString().toByteArray(StandardCharsets.US_ASCII))
            digest.update(0)
            digest.update(file.sha256.toByteArray(StandardCharsets.US_ASCII))
            digest.update('\n'.code.toByte())
        }
    }

    private fun readVerifiedTarball(
        lock: DistributionLock,
        tarball: Path,
        maximumBytes: Long,
    ): VerifiedTarball {
        if (maximumBytes <= 0) fail("Maximum tarball size must be positive")
        refuseSymbolicLink(tarball, "tarball")
        if (!Files.isRegularFile(tarball, LinkOption.NOFOLLOW_LINKS)) {
            fail("TypeScript npm tarball does not exist: $tarball")
        }

        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val sha512 = MessageDigest.getInstance("SHA-512")
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var size = 0L

        Files.newInputStream(tarball).buffered().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                if (size > maximumBytes - read) {
                    fail("TypeScript npm tarball exceeds $maximumBytes bytes")
                }
                size += read
                sha1.update(buffer, 0, read)
                sha256.update(buffer, 0, read)
                sha512.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
        }

        val actual = TypeScriptDistributionDigests(
            sha1 = sha1.digest().toLowerHex(),
            sha256 = sha256.digest().toLowerHex(),
            integrity = "sha512-${Base64.getEncoder().encodeToString(sha512.digest())}",
        )
        if (actual != lock.expectedDigests) {
            fail(
                "TypeScript npm tarball digest mismatch: " +
                    "expected=${lock.expectedDigests}, actual=$actual",
            )
        }
        return VerifiedTarball(
            bytes = output.toByteArray(),
            digests = actual,
        )
    }

    private fun hashDistributionFile(
        file: Path,
        relativePath: String,
    ): TypeScriptDistributionFile {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        Files.newInputStream(file).buffered().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                size += read
                digest.update(buffer, 0, read)
            }
        }
        return TypeScriptDistributionFile(
            path = relativePath,
            size = size,
            sha256 = digest.digest().toLowerHex(),
        )
    }

    private fun publishDirectory(staging: Path, output: Path) {
        if (!Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            moveDirectory(staging, output)
            return
        }
        refuseSymbolicLink(output, "existing distribution output")
        if (!Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS)) {
            fail("Distribution output exists and is not a directory: $output")
        }

        val backup = output.parent.resolve(
            ".${output.fileName}.backup-${UUID.randomUUID()}",
        )
        moveDirectory(output, backup)
        try {
            moveDirectory(staging, output)
        } catch (publishError: Throwable) {
            try {
                moveDirectory(backup, output)
            } catch (restoreError: Throwable) {
                publishError.addSuppressed(restoreError)
            }
            throw publishError
        }
        deleteTreeIfPresent(backup)
    }

    private fun moveDirectory(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun deleteTreeIfPresent(root: Path) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun refuseSymbolicLink(path: Path, description: String) {
        if (Files.isSymbolicLink(path)) {
            fail("$description must not be a symbolic link: $path")
        }
    }

    private fun describeFileDifference(
        expected: List<TypeScriptDistributionFile>,
        actual: List<TypeScriptDistributionFile>,
    ): String {
        val expectedByPath = expected.associateBy(TypeScriptDistributionFile::path)
        val actualByPath = actual.associateBy(TypeScriptDistributionFile::path)
        val missing = expectedByPath.keys - actualByPath.keys
        val extra = actualByPath.keys - expectedByPath.keys
        val changed = (expectedByPath.keys intersect actualByPath.keys).filter { path ->
            expectedByPath[path] != actualByPath[path]
        }
        return "missing=$missing, extra=$extra, changed=$changed"
    }

    private fun List<TypeScriptDistributionFile>.singleFor(
        path: String,
    ): TypeScriptDistributionFile =
        singleOrNull { it.path == path } ?: fail("Selected file is missing: $path")

    private fun String.parentDirectories(): List<String> {
        val result = mutableListOf<String>()
        var parent = substringBeforeLast('/', "")
        while (parent.isNotEmpty()) {
            result += parent
            parent = parent.substringBeforeLast('/', "")
        }
        return result
    }

    private fun Path.relativeToInvariant(path: Path): String =
        relativize(path).joinToString("/") { it.toString() }

    private fun String.asPrintablePath(): String = buildString {
        this@asPrintablePath.forEach { character ->
            when (character.code) {
                in 0x20..0x7e -> append(character)
                else -> append("\\u%04x".format(character.code))
            }
        }
    }

    private inline fun <T : AutoCloseable?, R> T.useNullable(
        block: (T) -> R,
    ): R {
        var failure: Throwable? = null
        try {
            return block(this)
        } catch (e: Throwable) {
            failure = e
            throw e
        } finally {
            when {
                this == null -> Unit
                failure == null -> close()
                else -> try {
                    close()
                } catch (closeError: Throwable) {
                    failure.addSuppressed(closeError)
                }
            }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toLowerHex()

    private fun ByteArray.toLowerHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun fail(message: String): Nothing =
        throw TypeScriptDistributionException(message)

    private val DRIVE_PATH = Regex("^[A-Za-z]:")

    private data class VerifiedTarball(
        val bytes: ByteArray,
        val digests: TypeScriptDistributionDigests,
    )

    internal data class DistributionLock(
        val schemaVersion: Int,
        val packageName: String,
        val version: String,
        val source: URI,
        val expectedDigests: TypeScriptDistributionDigests,
        val runtimeEntry: String,
        val declarationEntry: String,
        val licenseEntry: String,
        val thirdPartyNoticeEntry: String,
        val standardLibraryGlob: String,
    ) {
        private val standardLibraryPrefix = standardLibraryGlob.substringBefore('*')
        private val standardLibrarySuffix = standardLibraryGlob.substringAfter('*')
        val outputStandardLibraryGlob = outputPath(standardLibraryGlob)

        fun isSelected(path: String): Boolean =
            path == runtimeEntry ||
                path == declarationEntry ||
                path == licenseEntry ||
                path == thirdPartyNoticeEntry ||
                isStandardLibrary(path)

        fun isStandardLibraryOutput(path: String): Boolean =
            isStandardLibrary("$NPM_PACKAGE_ROOT/$path")

        fun outputPath(path: String): String {
            if (!path.startsWith("$NPM_PACKAGE_ROOT/")) {
                fail("Locked path is outside the npm package root: $path")
            }
            return path.removePrefix("$NPM_PACKAGE_ROOT/")
        }

        fun verifySelectedFiles(files: List<TypeScriptDistributionFile>) {
            val paths = files.map(TypeScriptDistributionFile::path)
            listOf(
                runtimeEntry,
                declarationEntry,
                licenseEntry,
                thirdPartyNoticeEntry,
            ).map(::outputPath).forEach { required ->
                if (paths.count { it == required } != 1) {
                    fail("Archive must contain exactly one selected file: $required")
                }
            }
            if (files.none { isStandardLibraryOutput(it.path) }) {
                fail("Archive does not contain any standard library declarations")
            }
        }

        private fun isStandardLibrary(path: String): Boolean {
            if (!path.startsWith(standardLibraryPrefix)) return false
            if (!path.endsWith(standardLibrarySuffix)) return false
            val middle = path.substring(
                standardLibraryPrefix.length,
                path.length - standardLibrarySuffix.length,
            )
            return '/' !in middle
        }

        companion object {
            fun read(file: File): DistributionLock {
                if (!file.isFile) fail("Compiler distribution lock does not exist: $file")
                val parsed = try {
                    JsonSlurper().parse(file)
                } catch (e: Throwable) {
                    throw TypeScriptDistributionException(
                        "Compiler distribution lock is not valid JSON: $file",
                        e,
                    )
                }
                val map = parsed as? Map<*, *>
                    ?: fail("Compiler distribution lock root must be an object")

                fun requiredString(key: String): String {
                    val value = map[key] as? String
                        ?: fail("Compiler distribution lock is missing string '$key'")
                    if (value.isEmpty()) {
                        fail("Compiler distribution lock has empty string '$key'")
                    }
                    return value
                }

                fun optionalString(key: String): String? = when (val value = map[key]) {
                    null -> null
                    is String -> value.takeIf(String::isNotEmpty)
                        ?: fail("Compiler distribution lock has empty string '$key'")
                    else -> fail("Compiler distribution lock '$key' must be a string")
                }

                val schemaVersion = (map["schemaVersion"] as? Number)?.toInt()
                    ?: fail("Compiler distribution lock is missing numeric 'schemaVersion'")
                if (schemaVersion !in 1..2) {
                    fail("Unsupported compiler distribution lock schema: $schemaVersion")
                }
                val packageName = requiredString("package")
                if (packageName != "typescript") {
                    fail("Unsupported compiler package: $packageName")
                }
                val version = requiredString("version")
                if (!VERSION.matches(version)) {
                    fail("Invalid TypeScript version in compiler distribution lock: $version")
                }
                val sourceText = requiredString("source")
                val expectedSource =
                    "https://registry.npmjs.org/typescript/-/typescript-$version.tgz"
                if (sourceText != expectedSource) {
                    fail(
                        "Compiler distribution source must be the exact pinned npm URL: " +
                            "$expectedSource",
                    )
                }
                val source = try {
                    URI(sourceText)
                } catch (e: Throwable) {
                    throw TypeScriptDistributionException(
                        "Invalid compiler distribution source URI",
                        e,
                    )
                }
                if (
                    source.scheme != "https" ||
                    source.host != "registry.npmjs.org" ||
                    source.userInfo != null ||
                    source.query != null ||
                    source.fragment != null
                ) {
                    fail("Compiler distribution source is not a safe HTTPS npm URL")
                }

                val sha1 = requiredString("sha1").requireLowerHex("sha1", 40)
                val sha256 = (
                    optionalString("sha256") ?: optionalString("tarballSha256")
                        ?: fail(
                            "Compiler distribution lock must pin 'sha256' " +
                                "(or 'tarballSha256')",
                        )
                    ).requireLowerHex("sha256", 64)
                val integrity = requiredString("integrity")
                validateIntegrity(integrity)

                val runtimeEntry = requiredString("runtimeEntry")
                val declarationEntry = optionalString("declarationEntry")
                    ?: runtimeEntry.removeSuffix(".js") + ".d.ts"
                val licenseEntry = requiredString("licenseEntry")
                val thirdPartyNoticeEntry = requiredString("thirdPartyNoticeEntry")
                val standardLibraryGlob = requiredString("standardLibraryGlob")
                if (standardLibraryGlob.count { it == '*' } != 1) {
                    fail("standardLibraryGlob must contain exactly one '*' wildcard")
                }
                if (standardLibraryGlob != "package/lib/lib*.d.ts") {
                    fail(
                        "Unsupported TypeScript standard library glob: " +
                            standardLibraryGlob,
                    )
                }
                listOf(
                    runtimeEntry,
                    declarationEntry,
                    licenseEntry,
                    thirdPartyNoticeEntry,
                ).forEach { entry ->
                    validateLockedEntry(entry)
                }

                return DistributionLock(
                    schemaVersion = schemaVersion,
                    packageName = packageName,
                    version = version,
                    source = source,
                    expectedDigests = TypeScriptDistributionDigests(
                        sha1 = sha1,
                        sha256 = sha256,
                        integrity = integrity,
                    ),
                    runtimeEntry = runtimeEntry,
                    declarationEntry = declarationEntry,
                    licenseEntry = licenseEntry,
                    thirdPartyNoticeEntry = thirdPartyNoticeEntry,
                    standardLibraryGlob = standardLibraryGlob,
                )
            }

            private fun validateLockedEntry(entry: String) {
                val validated = validateArchiveEntryName(entry, isDirectory = false)
                if (validated != entry) {
                    fail("Locked archive entry is not canonical: $entry")
                }
            }

            private fun validateIntegrity(value: String) {
                if (!value.startsWith("sha512-")) {
                    fail("Compiler distribution integrity must use SHA-512 SRI")
                }
                val decoded = try {
                    Base64.getDecoder().decode(value.removePrefix("sha512-"))
                } catch (e: IllegalArgumentException) {
                    throw TypeScriptDistributionException(
                        "Compiler distribution integrity is not valid Base64",
                        e,
                    )
                }
                if (decoded.size != 64) {
                    fail("Compiler distribution SHA-512 SRI has the wrong length")
                }
                val canonical = "sha512-${Base64.getEncoder().encodeToString(decoded)}"
                if (canonical != value) {
                    fail("Compiler distribution SHA-512 SRI is not canonical")
                }
            }

            private fun String.requireLowerHex(name: String, length: Int): String {
                if (this.length != length || any { it !in '0'..'9' && it !in 'a'..'f' }) {
                    fail(
                        "Compiler distribution $name must be $length lowercase hex characters",
                    )
                }
                return this
            }

            private val VERSION = Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")
        }
    }

    private object CanonicalJson {
        fun encode(value: Any): ByteArray =
            (buildString { appendValue(value) } + "\n").toByteArray(StandardCharsets.UTF_8)

        private fun StringBuilder.appendValue(value: Any?) {
            when (value) {
                null -> append("null")
                is Boolean -> append(if (value) "true" else "false")
                is Byte, is Short, is Int, is Long -> append(value.toString())
                is String -> appendString(value)
                is List<*> -> {
                    append('[')
                    value.forEachIndexed { index, item ->
                        if (index > 0) append(',')
                        appendValue(item)
                    }
                    append(']')
                }

                is Map<*, *> -> {
                    val entries = value.entries
                        .map { entry ->
                            val key = entry.key as? String
                                ?: fail("Canonical JSON object keys must be strings")
                            key to entry.value
                        }
                        .sortedBy(Pair<String, Any?>::first)
                    append('{')
                    entries.forEachIndexed { index, (key, item) ->
                        if (index > 0) append(',')
                        appendString(key)
                        append(':')
                        appendValue(item)
                    }
                    append('}')
                }

                else -> fail(
                    "Unsupported canonical JSON value: ${value::class.java.name}",
                )
            }
        }

        private fun StringBuilder.appendString(value: String) {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (character.code < 0x20) {
                            append("\\u%04x".format(character.code))
                        } else {
                            append(character)
                        }
                    }
                }
            }
            append('"')
        }
    }
}
