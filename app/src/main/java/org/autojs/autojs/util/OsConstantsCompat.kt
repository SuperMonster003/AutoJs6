package org.autojs.autojs.util

/** Constants available in the Linux ABI before Android exposed their SDK fields. */
internal object OsConstantsCompat {

    // Linux UAPI O_CLOEXEC on every supported Android ABI. OsConstants.O_CLOEXEC
    // is exposed only from API 27; the kernel flag also works on Android 7.
    // Keep it in the open flags so close-on-exec is set atomically.
    // zh-CN: 保留打开文件时原子设置 close-on-exec 的语义, 避免访问 API 27 才公开的字段.
    const val O_CLOEXEC = 0x80000
}
