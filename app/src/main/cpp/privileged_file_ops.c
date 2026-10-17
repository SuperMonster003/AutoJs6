#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/xattr.h>
#include <unistd.h>

#ifndef RENAME_NOREPLACE
#define RENAME_NOREPLACE 1
#endif
#ifndef RENAME_EXCHANGE
#define RENAME_EXCHANGE 2
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

JNIEXPORT jint JNICALL
Java_org_autojs_autojs_model_explorer_PrivilegedFileNative_exchangeInternal(
        JNIEnv *env, jclass type, jbyteArray source_bytes, jbyteArray destination_bytes) {
    (void)type;
    char *source = read_path(env, source_bytes);
    char *destination = read_path(env, destination_bytes);
    int result = EINVAL;
    if (source && destination) {
#ifdef __NR_renameat2
        result = syscall(__NR_renameat2, AT_FDCWD, source, AT_FDCWD, destination, RENAME_EXCHANGE) == 0 ? 0 : errno;
#else
        result = ENOSYS;
#endif
    }
    free(source);
    free(destination);
    return result;
}

/* Linux limits xattr names/values to 64 KiB. Bounds also keep hostile files from allocating freely. */
#define MAX_XATTR_BYTES (64 * 1024)
#define MAX_ALL_XATTR_BYTES (1024 * 1024)

JNIEXPORT jint JNICALL
Java_org_autojs_autojs_model_explorer_PrivilegedFileNative_tryLockInternal(
        JNIEnv *env, jclass type, jint fd) {
    (void)env;
    (void)type;
    return flock(fd, LOCK_EX | LOCK_NB) == 0 ? 0 : errno;
}

struct attribute {
    char *name;
    void *value;
    size_t length;
};

struct attributes {
    struct attribute *items;
    size_t count;
    int unsupported;
};

static void free_attributes(struct attributes *attrs) {
    for (size_t i = 0; i < attrs->count; ++i) {
        free(attrs->items[i].name);
        free(attrs->items[i].value);
    }
    free(attrs->items);
    memset(attrs, 0, sizeof(*attrs));
}

static int read_attributes(int fd, struct attributes *attrs) {
    memset(attrs, 0, sizeof(*attrs));
    ssize_t length = flistxattr(fd, NULL, 0);
    if (length < 0) {
        if (errno == EOPNOTSUPP) {
            attrs->unsupported = 1;
            return 0;
        }
        return errno;
    }
    if (length > MAX_XATTR_BYTES) return E2BIG;
    if (length == 0) return 0;
    char *names = malloc((size_t)length);
    if (!names) return ENOMEM;
    ssize_t actual = flistxattr(fd, names, (size_t)length);
    int error = 0;
    if (actual < 0) {
        error = errno;
        goto done;
    }
    if (actual != length) {
        error = EAGAIN;
        goto done;
    }
    size_t count = 0;
    for (ssize_t i = 0; i < length; ++i) if (!names[i]) ++count;
    if (!count || names[length - 1] != 0) {
        error = EIO;
        goto done;
    }
    attrs->items = calloc(count, sizeof(*attrs->items));
    if (!attrs->items) {
        error = ENOMEM;
        goto done;
    }
    size_t total_bytes = 0;
    char *name = names;
    for (size_t i = 0; i < count; ++i) {
        struct attribute *item = &attrs->items[attrs->count++];
        item->name = strdup(name);
        if (!item->name) {
            error = ENOMEM;
            goto done;
        }
        ssize_t value_length = fgetxattr(fd, name, NULL, 0);
        if (value_length < 0) {
            error = errno;
            goto done;
        }
        if (value_length > MAX_XATTR_BYTES || total_bytes + (size_t)value_length > MAX_ALL_XATTR_BYTES) {
            error = E2BIG;
            goto done;
        }
        /* malloc(0) may return NULL without indicating an error. */
        item->value = malloc(value_length ? (size_t)value_length : 1);
        if (!item->value) {
            error = ENOMEM;
            goto done;
        }
        ssize_t read_length = fgetxattr(fd, name, item->value, (size_t)value_length);
        if (read_length < 0) {
            error = errno;
            goto done;
        }
        if (read_length != value_length) {
            error = EAGAIN;
            goto done;
        }
        item->length = (size_t)value_length;
        total_bytes += item->length;
        name += strlen(name) + 1;
    }
done:
    free(names);
    if (error) free_attributes(attrs);
    return error;
}

static const struct attribute *find_attribute(const struct attributes *attrs, const char *name) {
    for (size_t i = 0; i < attrs->count; ++i) {
        if (strcmp(attrs->items[i].name, name) == 0) return &attrs->items[i];
    }
    return NULL;
}

static int compare_attributes(const struct attributes *source, const struct attributes *destination) {
    if (source->unsupported != destination->unsupported) return EOPNOTSUPP;
    if (source->count != destination->count) return EAGAIN;
    for (size_t i = 0; i < source->count; ++i) {
        const struct attribute *a = &source->items[i];
        const struct attribute *b = find_attribute(destination, a->name);
        if (!b || a->length != b->length || memcmp(a->value, b->value, a->length)) return EAGAIN;
    }
    return 0;
}

static int compare_metadata(int source_fd, int destination_fd) {
    struct stat source, destination;
    if (fstat(source_fd, &source) || fstat(destination_fd, &destination)) return errno;
    if (!S_ISREG(source.st_mode) || !S_ISREG(destination.st_mode)) return EINVAL;
    if (source.st_uid != destination.st_uid || source.st_gid != destination.st_gid ||
            (source.st_mode & 07777) != (destination.st_mode & 07777)) return EAGAIN;
    struct attributes source_attrs, destination_attrs;
    int error = read_attributes(source_fd, &source_attrs);
    if (error) return error;
    error = read_attributes(destination_fd, &destination_attrs);
    if (!error) {
        error = compare_attributes(&source_attrs, &destination_attrs);
        free_attributes(&destination_attrs);
    }
    free_attributes(&source_attrs);
    return error;
}

JNIEXPORT jint JNICALL
Java_org_autojs_autojs_model_explorer_PrivilegedFileNative_compareSaveMetadataInternal(
        JNIEnv *env, jclass type, jint source_fd, jint destination_fd) {
    (void)env;
    (void)type;
    return compare_metadata(source_fd, destination_fd);
}

JNIEXPORT jint JNICALL
Java_org_autojs_autojs_model_explorer_PrivilegedFileNative_copySaveMetadataInternal(
        JNIEnv *env, jclass type, jint source_fd, jint destination_fd) {
    (void)env;
    (void)type;
    struct stat source, destination;
    if (fstat(source_fd, &source) || fstat(destination_fd, &destination)) return errno;
    if (!S_ISREG(source.st_mode) || !S_ISREG(destination.st_mode) || source.st_nlink != 1 ||
            destination.st_nlink != 1) return EINVAL;
    struct attributes source_attrs, destination_attrs;
    int error = read_attributes(source_fd, &source_attrs);
    if (error) return error;
    error = read_attributes(destination_fd, &destination_attrs);
    if (error) {
        free_attributes(&source_attrs);
        return error;
    }
    if (source_attrs.unsupported != destination_attrs.unsupported) {
        error = EOPNOTSUPP;
        goto done;
    }
    /* chown may clear set-id/capability bits, so ownership is applied before mode and xattrs. */
    if ((source.st_uid != destination.st_uid || source.st_gid != destination.st_gid) &&
            fchown(destination_fd, source.st_uid, source.st_gid)) {
        error = errno;
        goto done;
    }
    if ((source.st_mode & 07777) != (destination.st_mode & 07777) &&
            fchmod(destination_fd, source.st_mode & 07777)) {
        error = errno;
        goto done;
    }
    for (size_t i = 0; i < destination_attrs.count; ++i) {
        const struct attribute *item = &destination_attrs.items[i];
        if (!find_attribute(&source_attrs, item->name) && fremovexattr(destination_fd, item->name)) {
            error = errno;
            goto done;
        }
    }
    for (size_t i = 0; i < source_attrs.count; ++i) {
        const struct attribute *item = &source_attrs.items[i];
        const struct attribute *existing = find_attribute(&destination_attrs, item->name);
        /* Avoid requesting unnecessary SELinux relabel permission for an identical label. */
        if (existing && existing->length == item->length && !memcmp(existing->value, item->value, item->length)) continue;
        if (fsetxattr(destination_fd, item->name, item->value, item->length, 0)) {
            error = errno;
            goto done;
        }
    }
    struct timespec times[2] = { source.st_atim, { .tv_sec = 0, .tv_nsec = UTIME_NOW } };
    if (futimens(destination_fd, times)) {
        error = errno;
        goto done;
    }
    error = compare_metadata(source_fd, destination_fd);
done:
    free_attributes(&source_attrs);
    free_attributes(&destination_attrs);
    return error;
}
