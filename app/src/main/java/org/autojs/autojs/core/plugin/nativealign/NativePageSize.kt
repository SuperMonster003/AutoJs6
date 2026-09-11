package org.autojs.autojs.core.plugin.nativealign

import android.system.Os
import android.system.OsConstants

object NativePageSize {
    private val bytes by lazy {
        Os.sysconf(OsConstants._SC_PAGESIZE).also { check(it > 0) { "Unable to determine native page size" } }
    }
    fun current(): Long = bytes
}
