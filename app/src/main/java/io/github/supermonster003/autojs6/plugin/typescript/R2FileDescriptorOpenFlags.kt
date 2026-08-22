package io.github.supermonster003.autojs6.plugin.typescript

import android.annotation.TargetApi
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor

/**
 * API-24-compatible, non-mutating inspection of this process's Linux open-file flags.
 *
 * Android did not expose `Os.fcntlInt(F_GETFL)` as public SDK API until API 30. Older releases
 * use the bundled libc bridge instead of a hidden API or procfs: production processes are not
 * guaranteed permission to read procfs file-descriptor metadata.
 */
internal object R2FileDescriptorOpenFlags {

    fun read(descriptor: ParcelFileDescriptor): Int {
        val descriptorNumber = descriptor.fd
        check(descriptorNumber >= 0) { "File descriptor is closed." }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Api30.read(descriptor.fileDescriptor)
        }

        val result =
            R2FileDescriptorOpenFlagsNative.getFileStatusFlags(
                descriptorNumber,
            )
        if (result < 0) {
            throw ErrnoException(
                "fcntl(F_GETFL)",
                -result,
            )
        }
        return result
    }

    /**
     * Keeps the API-30 method reference out of the class verified on API 24-29.
     */
    @TargetApi(Build.VERSION_CODES.R)
    private object Api30 {

        fun read(descriptor: FileDescriptor): Int =
            Os.fcntlInt(
                descriptor,
                OsConstants.F_GETFL,
                0,
            )
    }
}
