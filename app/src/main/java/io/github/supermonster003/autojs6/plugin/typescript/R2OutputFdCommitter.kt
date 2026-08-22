package io.github.supermonster003.autojs6.plugin.typescript

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor
import java.io.IOException
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

/**
 * Copies a validated private output inode to the accepted caller descriptor.
 *
 * The copy is fixed-offset so concurrent shared-offset churn cannot redirect bytes. A successful
 * commit is not returned until exact length, fsync, final offset zero and service-side descriptor
 * close all succeed. Every ordinary failure attempts an empty rollback before returning an error.
 */
internal object R2OutputFdCommitter {

    fun commit(
        staged: R2StagedOutputArchive,
        outputArchive: ParcelFileDescriptor,
        faultInjector: (R2OutputCommitStage) -> Unit = {},
        terminalGate: () -> Unit = {},
    ): R2OutputArchiveSummary {
        try {
            val descriptor = outputArchive.fileDescriptor
            requireWritableNonAppend(outputArchive)
            val validation = staged.validateStructure()
            if (
                validation != staged.validation ||
                validation.containerBytes != staged.summary.containerBytes ||
                !staged.verifyUnchanged()
            ) {
                throw IOException(
                    "R2 private output changed before descriptor commit.",
                )
            }
            faultInjector(R2OutputCommitStage.BEFORE_COPY)
            Os.ftruncate(descriptor, 0L)
            copyFixedOffset(
                source = staged.fileDescriptor,
                destination = descriptor,
                byteCount = staged.summary.containerBytes,
                faultInjector = faultInjector,
            )
            Os.ftruncate(descriptor, staged.summary.containerBytes)
            faultInjector(R2OutputCommitStage.BEFORE_FSYNC)
            Os.fsync(descriptor)
            if (!staged.verifyUnchanged()) {
                throw IOException(
                    "R2 private output changed while it was committed.",
                )
            }
            faultInjector(R2OutputCommitStage.BEFORE_FINAL_SEEK)
            Os.lseek(descriptor, 0L, OsConstants.SEEK_SET)
            faultInjector(R2OutputCommitStage.BEFORE_CLOSE)
            terminalGate()
            outputArchive.close()
            return staged.summary
        } catch (failure: Throwable) {
            rollbackAndClose(
                outputArchive = outputArchive,
                originalFailure = failure,
            )
        }
    }

    /**
     * Finalizes cancel/timeout/internal-failure output as an empty, synced file at offset zero.
     */
    fun clear(outputArchive: ParcelFileDescriptor) {
        var failure: Throwable? = null
        try {
            val descriptor = outputArchive.fileDescriptor
            requireWritableNonAppend(outputArchive)
            Os.ftruncate(descriptor, 0L)
            Os.fsync(descriptor)
            Os.lseek(descriptor, 0L, OsConstants.SEEK_SET)
        } catch (error: Throwable) {
            failure = error
        }
        try {
            outputArchive.close()
        } catch (error: Throwable) {
            failure?.addSuppressed(error) ?: run { failure = error }
        }
        failure?.let { error ->
            throw R2OutputCommitUnprovenException(
                "R2 empty output finalization could not be proven.",
                error,
            )
        }
    }

    private fun copyFixedOffset(
        source: FileDescriptor,
        destination: FileDescriptor,
        byteCount: Long,
        faultInjector: (R2OutputCommitStage) -> Unit,
    ) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var offset = 0L
        var firstWrite = true
        try {
            while (offset < byteCount) {
                val requested = minOf(
                    buffer.size.toLong(),
                    byteCount - offset,
                ).toInt()
                val read = Os.pread(
                    source,
                    buffer,
                    0,
                    requested,
                    offset,
                )
                if (read <= 0) {
                    throw IOException(
                        "R2 private output ended before its validated length.",
                    )
                }
                var writtenFromBuffer = 0
                while (writtenFromBuffer < read) {
                    val written = Os.pwrite(
                        destination,
                        buffer,
                        writtenFromBuffer,
                        read - writtenFromBuffer,
                        offset + writtenFromBuffer,
                    )
                    if (written <= 0) {
                        throw IOException(
                            "R2 caller output stopped accepting committed bytes.",
                        )
                    }
                    writtenFromBuffer += written
                    if (firstWrite) {
                        firstWrite = false
                        faultInjector(R2OutputCommitStage.AFTER_FIRST_WRITE)
                    }
                }
                offset += read
            }
            if (offset != byteCount) {
                throw IOException("R2 output commit length is inconsistent.")
            }
        } finally {
            buffer.fill(0)
        }
    }

    private fun rollbackAndClose(
        outputArchive: ParcelFileDescriptor,
        originalFailure: Throwable,
    ): Nothing {
        var rollbackFailure: Throwable? = null
        val descriptor = try {
            outputArchive.fileDescriptor
        } catch (error: Throwable) {
            rollbackFailure = error
            null
        }
        if (descriptor != null) {
            try {
                if (descriptor.valid()) {
                    Os.ftruncate(descriptor, 0L)
                    Os.fsync(descriptor)
                    Os.lseek(descriptor, 0L, OsConstants.SEEK_SET)
                } else {
                    throw IOException(
                        "R2 output descriptor closed before empty rollback.",
                    )
                }
            } catch (error: Throwable) {
                rollbackFailure?.addSuppressed(error)
                    ?: run { rollbackFailure = error }
            }
        }
        try {
            outputArchive.close()
        } catch (error: Throwable) {
            rollbackFailure?.addSuppressed(error)
                ?: run { rollbackFailure = error }
        }
        rollbackFailure?.let { cleanupFailure ->
            originalFailure.addSuppressed(cleanupFailure)
            throw R2OutputCommitUnprovenException(
                "R2 output commit failed and empty rollback could not be proven.",
                originalFailure,
            )
        }
        throw when (originalFailure) {
            is R2OutputCommitException -> originalFailure
            else -> R2OutputCommitException(
                contractErrorCode =
                TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT,
                message = "R2 private output could not be committed.",
                cause = originalFailure,
            )
        }
    }

    private fun requireWritableNonAppend(outputArchive: ParcelFileDescriptor) {
        val descriptor = outputArchive.fileDescriptor
        if (!descriptor.valid()) {
            throw R2OutputCommitException(
                TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT,
                "R2 output descriptor is closed.",
            )
        }
        val flags = R2FileDescriptorOpenFlags.read(outputArchive)
        val accessMode = flags and OsConstants.O_ACCMODE
        if (
            accessMode == OsConstants.O_RDONLY ||
            flags and OsConstants.O_APPEND != 0
        ) {
            throw R2OutputCommitException(
                TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT,
                "R2 output descriptor must be writable and must not use O_APPEND.",
            )
        }
    }

    private const val COPY_BUFFER_BYTES = 64 * 1024
}

internal enum class R2OutputCommitStage {
    BEFORE_COPY,
    AFTER_FIRST_WRITE,
    BEFORE_FSYNC,
    BEFORE_FINAL_SEEK,
    BEFORE_CLOSE,
}

internal open class R2OutputCommitException(
    val contractErrorCode: String,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * Cleanup proof failure poisons later admission until a process-level supervisor is available.
 */
internal class R2OutputCommitUnprovenException(
    message: String,
    cause: Throwable? = null,
) : R2OutputCommitException(
    TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT,
    message,
    cause,
)
