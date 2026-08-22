#include <cerrno>
#include <fcntl.h>
#include <jni.h>

extern "C" JNIEXPORT jint JNICALL
Java_io_github_supermonster003_autojs6_plugin_typescript_R2FileDescriptorOpenFlagsNative_getFileStatusFlags(
    JNIEnv*,
    jclass,
    jint descriptor) {
    int flags;
    do {
        errno = 0;
        flags = fcntl(descriptor, F_GETFL);
    } while (flags < 0 && errno == EINTR);
    if (flags >= 0) {
        return static_cast<jint>(flags);
    }
    const int error_number = errno == 0 ? EIO : errno;
    return static_cast<jint>(-error_number);
}
