#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>

#ifndef RENAME_NOREPLACE
#define RENAME_NOREPLACE 1
#endif

/* Byte arrays preserve standard UTF-8, including non-BMP filenames; JNI strings use modified UTF-8. */
static char *read_path(JNIEnv *env, jbyteArray bytes) {
    if (!bytes) return NULL;
    jsize length = (*env)->GetArrayLength(env, bytes);
    if (length <= 0 || length > 4096) return NULL;
    char *path = malloc((size_t)length + 1);
    if (!path) return NULL;
    (*env)->GetByteArrayRegion(env, bytes, 0, length, (jbyte *)path);
    if ((*env)->ExceptionCheck(env) || memchr(path, 0, (size_t)length)) {
        free(path);
        return NULL;
    }
    path[length] = 0;
    return path;
}

JNIEXPORT jint JNICALL
Java_org_autojs_autojs_model_explorer_PrivilegedFileNative_renameNoReplaceInternal(
        JNIEnv *env, jclass type, jbyteArray source_bytes, jbyteArray destination_bytes) {
    (void)type;
    char *source = read_path(env, source_bytes);
    char *destination = read_path(env, destination_bytes);
    int result = EINVAL;
    if (source && destination) {
#ifdef __NR_renameat2
        result = syscall(__NR_renameat2, AT_FDCWD, source, AT_FDCWD, destination, RENAME_NOREPLACE) == 0 ? 0 : errno;
#else
        result = ENOSYS;
#endif
    }
    free(source);
    free(destination);
    return result;
}
