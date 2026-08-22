package io.github.supermonster003.autojs6.plugin.typescript

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import java.io.Closeable
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

internal data class R2PrivateInputFingerprint(
    val byteCount: Long,
    val sha256: String,
)

/**
 * One captured input inode owned by the loader.
 *
 * The writer is synced and closed before [immutableReader] is opened read-only. On Android/Linux
 * its directory entry is then removed immediately; Windows retains [fallbackFile] read-only until
 * [close]. ZIP validation and final digest verification share this exact reader.
 */
internal class R2PrivateInputStaging private constructor(
    private val immutableReader: RandomAccessFile,
    private val fallbackFile: File?,
    val fingerprint: R2PrivateInputFingerprint,
    expectedSha256: ByteArray,
) : Closeable {

    private val lock = Any()
    private val expectedSha256 = expectedSha256.copyOf()
    private var usable = true
    private var readerClosed = false
    private var cleanupComplete = false

    val namedPathRetained: Boolean
        get() = fallbackFile != null

    /**
     * Runs [block] under the staging lifecycle lock with the owned reader positioned at zero.
     * The block borrows the reader and must neither close it nor retain it.
     */
    fun <T> withImmutableReader(
        block: (RandomAccessFile) -> T,
    ): T = synchronized(lock) {
        checkUsable()
        immutableReader.seek(0L)
        block(immutableReader)
    }

    fun verifyUnchanged(): R2PrivateInputFingerprint = withImmutableReader { reader ->
        val actual = try {
            digest(
                input = reader,
                maximumBytes = fingerprint.byteCount,
            )
        } catch (failure: R2PrivateInputStagingIntegrityException) {
            throw failure
        } catch (failure: IOException) {
            throw R2PrivateInputStagingIntegrityException(
                "R2 private input staging could not be verified.",
                failure,
            )
        } catch (failure: SecurityException) {
            throw R2PrivateInputStagingIntegrityException(
                "R2 private input staging could not be verified.",
                failure,
            )
        }
        if (
            actual.byteCount != fingerprint.byteCount ||
            !MessageDigest.isEqual(expectedSha256, actual.sha256)
        ) {
            throw R2PrivateInputStagingIntegrityException(
                "R2 private input staging bytes changed after capture.",
            )
        }
        actual.fingerprint
    }

    override fun close() {
        synchronized(lock) {
            if (cleanupComplete) return
            usable = false
            var failure: Throwable? = null
            if (!readerClosed) {
                try {
                    immutableReader.close()
                    readerClosed = true
                } catch (closeFailure: Throwable) {
                    failure = closeFailure
                }
            }
            fallbackFile?.let { file ->
                try {
                    deleteStagingFile(file)
                } catch (cleanupFailure: Throwable) {
                    failure?.addSuppressed(cleanupFailure)
                        ?: run { failure = cleanupFailure }
                }
            }
            if (failure == null) {
                cleanupComplete = true
                return
            }
            throw R2PrivateInputCleanupUnprovenException(
                "R2 private input staging cleanup could not be proven.",
                failure,
            )
        }
    }

    private fun checkUsable() {
        check(usable && !readerClosed) {
            "R2 private input staging is closed."
        }
    }

    companion object {

        /**
         * Captures [input] without taking ownership of it. The caller remains responsible for
         * closing the source stream. [privateDirectory] must be an existing app-private
         * directory selected by the owner, such as a dedicated directory returned by
         * Context.getDir.
         */
        fun stage(
            input: InputStream,
            privateDirectory: File,
            maximumBytes: Long = TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES,
        ): R2PrivateInputStaging {
            require(privateDirectory.isDirectory) {
                "R2 private input staging directory does not exist or is not a directory."
            }
            require(maximumBytes >= 0L) {
                "R2 private input staging byte limit must be non-negative."
            }

            val file = File.createTempFile(
                STAGING_FILE_PREFIX,
                STAGING_FILE_SUFFIX,
                privateDirectory,
            )
            var immutableReader: RandomAccessFile? = null
            try {
                val output = FileOutputStream(file, false)
                var captured: DigestResult? = null
                var writeFailure: Throwable? = null
                try {
                    captured = digest(
                        input = input,
                        maximumBytes = maximumBytes,
                        output = output,
                    )
                    output.fd.sync()
                } catch (failure: Throwable) {
                    writeFailure = failure
                }
                val writerCloseFailure = runCatching { output.close() }.exceptionOrNull()
                if (writerCloseFailure != null) {
                    throw R2PrivateInputCleanupUnprovenException(
                        "R2 private input writer close could not be proven.",
                        writerCloseFailure,
                    ).also { unproven ->
                        writeFailure?.let(unproven::addSuppressed)
                    }
                }
                writeFailure?.let { throw it }
                val completedCapture = checkNotNull(captured)
                if (!file.setReadOnly()) {
                    throw IOException(
                        "R2 private input staging could not be made read-only.",
                    )
                }
                val reader = RandomAccessFile(file, "r")
                immutableReader = reader
                val fallbackFile = if (SUPPORTS_UNLINK_WHILE_OPEN) {
                    if (!file.delete() && file.exists()) {
                        throw IOException(
                            "R2 private input staging could not be unlinked after capture.",
                        )
                    }
                    null
                } else {
                    file
                }
                return R2PrivateInputStaging(
                    immutableReader = reader,
                    fallbackFile = fallbackFile,
                    fingerprint = completedCapture.fingerprint,
                    expectedSha256 = completedCapture.sha256,
                )
            } catch (failure: Throwable) {
                var cleanupFailure: Throwable? = null
                try {
                    immutableReader?.close()
                } catch (closeFailure: Throwable) {
                    cleanupFailure = closeFailure
                }
                try {
                    deleteStagingFile(file)
                } catch (deleteFailure: Throwable) {
                    cleanupFailure?.addSuppressed(deleteFailure)
                        ?: run { cleanupFailure = deleteFailure }
                }
                if (cleanupFailure != null) {
                    throw R2PrivateInputCleanupUnprovenException(
                        "R2 failed input staging cleanup could not be proven.",
                        failure,
                    ).also { unproven ->
                        unproven.addSuppressed(cleanupFailure)
                    }
                }
                throw failure
            }
        }

        /**
         * Consumes the service-side descriptor reference on every path. Fixed-offset pread calls
         * deliberately ignore the shared open-file-description cursor, while fstat snapshots
         * reject a regular file that changes during capture.
         */
        fun stage(
            inputArchive: ParcelFileDescriptor,
            privateDirectory: File,
            maximumBytes: Long = TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES,
        ): R2PrivateInputStaging {
            var staging: R2PrivateInputStaging? = null
            var failure: Throwable? = null
            try {
                val descriptor = inputArchive.fileDescriptor
                // Keep pipe/non-seekable rejection without reading or changing the shared cursor.
                Os.lseek(descriptor, 0L, OsConstants.SEEK_CUR)
                val before = RegularFileState.capture(Os.fstat(descriptor))
                staging = stage(
                    input = FixedOffsetInputStream(
                        descriptor = descriptor,
                        expectedBytes = before.size,
                    ),
                    privateDirectory = privateDirectory,
                    maximumBytes = maximumBytes,
                )
                val after = RegularFileState.capture(Os.fstat(descriptor))
                if (before != after) {
                    throw R2PrivateInputStagingIntegrityException(
                        "R2 caller input changed while it was captured.",
                    )
                }
            } catch (error: Throwable) {
                failure = if (error is ErrnoException) {
                    R2PrivateInputStagingIntegrityException(
                        "R2 caller input must be a readable regular seekable file.",
                        error,
                    )
                } else {
                    error
                }
            }
            var descriptorCloseFailure: Throwable? = null
            try {
                inputArchive.close()
            } catch (closeFailure: Throwable) {
                descriptorCloseFailure = closeFailure
            }
            if (descriptorCloseFailure != null) {
                val unproven = R2PrivateInputCleanupUnprovenException(
                    "R2 caller input descriptor close could not be proven.",
                    descriptorCloseFailure,
                )
                failure?.let(unproven::addSuppressed)
                try {
                    staging?.close()
                } catch (cleanupFailure: Throwable) {
                    unproven.addSuppressed(cleanupFailure)
                }
                throw unproven
            }
            failure?.let { captureFailure ->
                try {
                    staging?.close()
                } catch (cleanupFailure: Throwable) {
                    captureFailure.addSuppressed(cleanupFailure)
                }
                throw captureFailure
            }
            return checkNotNull(staging)
        }

        private fun digest(
            input: InputStream,
            maximumBytes: Long,
            output: FileOutputStream?,
        ): DigestResult {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var byteCount = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) {
                    val singleByte = input.read()
                    if (singleByte < 0) break
                    ensureWithinLimit(
                        currentBytes = byteCount,
                        incomingBytes = 1,
                        maximumBytes = maximumBytes,
                    )
                    output?.write(singleByte)
                    digest.update(singleByte.toByte())
                    byteCount++
                    continue
                }
                ensureWithinLimit(
                    currentBytes = byteCount,
                    incomingBytes = read,
                    maximumBytes = maximumBytes,
                )
                output?.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                byteCount += read
            }
            return DigestResult(
                byteCount = byteCount,
                sha256 = digest.digest(),
            )
        }

        private fun digest(
            input: RandomAccessFile,
            maximumBytes: Long,
        ): DigestResult {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var byteCount = 0L
            input.seek(0L)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                ensureWithinLimit(
                    currentBytes = byteCount,
                    incomingBytes = read,
                    maximumBytes = maximumBytes,
                )
                digest.update(buffer, 0, read)
                byteCount += read
            }
            return DigestResult(
                byteCount = byteCount,
                sha256 = digest.digest(),
            )
        }

        private fun ensureWithinLimit(
            currentBytes: Long,
            incomingBytes: Int,
            maximumBytes: Long,
        ) {
            if (currentBytes > maximumBytes - incomingBytes) {
                throw R2PrivateInputStagingQuotaException(
                    "R2 private input exceeds its $maximumBytes-byte staging limit.",
                )
            }
        }

        private fun deleteStagingFile(file: File) {
            if (!file.exists()) return
            file.setWritable(true, true)
            if (!file.delete() && file.exists()) {
                throw IOException(
                    "R2 private input staging could not be deleted: ${file.absolutePath}",
                )
            }
        }

        private class FixedOffsetInputStream(
            private val descriptor: FileDescriptor,
            private val expectedBytes: Long,
        ) : InputStream() {

            private val singleByte = ByteArray(1)
            private var offset = 0L

            override fun read(): Int {
                val read = read(singleByte, 0, 1)
                return if (read < 0) -1 else singleByte[0].toInt() and 0xff
            }

            override fun read(
                destination: ByteArray,
                destinationOffset: Int,
                byteCount: Int,
            ): Int {
                if (
                    destinationOffset < 0 ||
                    byteCount < 0 ||
                    destinationOffset > destination.size - byteCount
                ) {
                    throw IndexOutOfBoundsException()
                }
                if (byteCount == 0) return 0
                if (offset == expectedBytes) return -1

                val requested = minOf(
                    byteCount.toLong(),
                    expectedBytes - offset,
                ).toInt()
                val read = Os.pread(
                    descriptor,
                    destination,
                    destinationOffset,
                    requested,
                    offset,
                )
                if (read <= 0) {
                    throw R2PrivateInputStagingIntegrityException(
                        "R2 caller input ended before its captured file size.",
                    )
                }
                offset += read
                return read
            }
        }

        private data class RegularFileState(
            val device: Long,
            val inode: Long,
            val mode: Int,
            val linkCount: Long,
            val owner: Int,
            val group: Int,
            val size: Long,
            val modifiedSeconds: Long,
            val changedSeconds: Long,
        ) {

            companion object {

                fun capture(stat: StructStat): RegularFileState {
                    if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_size < 0L) {
                        throw R2PrivateInputStagingIntegrityException(
                            "R2 caller input must be a readable regular seekable file.",
                        )
                    }
                    return RegularFileState(
                        device = stat.st_dev,
                        inode = stat.st_ino,
                        mode = stat.st_mode,
                        linkCount = stat.st_nlink,
                        owner = stat.st_uid,
                        group = stat.st_gid,
                        size = stat.st_size,
                        // Nanosecond StructStat fields require API 27; R2 still supports API 24.
                        modifiedSeconds = stat.st_mtime,
                        changedSeconds = stat.st_ctime,
                    )
                }
            }
        }

        private fun ByteArray.toLowerHex(): String =
            joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }

        private data class DigestResult(
            val byteCount: Long,
            val sha256: ByteArray,
        ) {
            val fingerprint: R2PrivateInputFingerprint
                get() = R2PrivateInputFingerprint(
                    byteCount = byteCount,
                    sha256 = sha256.toLowerHex(),
                )
        }

        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val STAGING_FILE_PREFIX = "r2-private-input-"
        private const val STAGING_FILE_SUFFIX = ".staging"
        private val SUPPORTS_UNLINK_WHILE_OPEN = File.separatorChar != '\\'
    }
}

internal class R2PrivateInputStagingQuotaException(
    message: String,
) : IOException(message)

internal class R2PrivateInputStagingIntegrityException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

internal class R2PrivateInputCleanupUnprovenException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
