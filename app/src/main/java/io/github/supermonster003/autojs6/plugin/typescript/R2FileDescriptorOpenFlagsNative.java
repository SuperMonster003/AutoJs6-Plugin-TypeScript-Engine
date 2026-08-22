package io.github.supermonster003.autojs6.plugin.typescript;

/**
 * API 24-29 libc bridge for {@code fcntl(F_GETFL)}.
 *
 * <p>The class and native member are kept by the app's exact R8 rule so JNI symbol lookup remains
 * stable in the minified release artifact.
 */
final class R2FileDescriptorOpenFlagsNative {

    static {
        System.loadLibrary("autojs6_r2_fd_flags");
    }

    private R2FileDescriptorOpenFlagsNative() {
        throw new AssertionError("No instances.");
    }

    static native int getFileStatusFlags(int descriptor);
}
