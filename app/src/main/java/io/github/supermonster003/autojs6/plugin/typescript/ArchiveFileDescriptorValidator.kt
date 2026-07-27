package io.github.supermonster003.autojs6.plugin.typescript

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants

data class ArchiveFileDescriptorValidation(
    val inputArchiveValid: Boolean,
    val outputArchiveValid: Boolean,
    val backingFilesDistinct: Boolean,
)

object ArchiveFileDescriptorValidator {

    fun validate(
        inputArchive: ParcelFileDescriptor?,
        outputArchive: ParcelFileDescriptor?,
    ): ArchiveFileDescriptorValidation {
        val input = inspect(inputArchive, Access.READABLE)
        val output = inspect(outputArchive, Access.WRITABLE)
        return ArchiveFileDescriptorValidation(
            inputArchiveValid = input != null,
            outputArchiveValid = output != null,
            backingFilesDistinct = input != null && output != null && input != output,
        )
    }

    private fun inspect(
        descriptor: ParcelFileDescriptor?,
        requiredAccess: Access,
    ): FileIdentity? = runCatching {
        val fileDescriptor = requireNotNull(descriptor).fileDescriptor
        check(fileDescriptor.valid())

        val stat = Os.fstat(fileDescriptor)
        check(OsConstants.S_ISREG(stat.st_mode))

        val currentOffset = Os.lseek(fileDescriptor, 0L, OsConstants.SEEK_CUR)
        Os.lseek(fileDescriptor, currentOffset, OsConstants.SEEK_SET)
        when (requiredAccess) {
            Access.READABLE -> {
                Os.pread(fileDescriptor, ByteArray(1), 0, 1, currentOffset)
            }

            Access.WRITABLE -> {
                Os.ftruncate(fileDescriptor, stat.st_size)
            }
        }
        FileIdentity(device = stat.st_dev, inode = stat.st_ino)
    }.getOrNull()

    private enum class Access {
        READABLE,
        WRITABLE,
    }

    private data class FileIdentity(
        val device: Long,
        val inode: Long,
    )
}
