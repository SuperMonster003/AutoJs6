package org.autojs.autojs.runtime.api.augment.util

import android.os.Build
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException

object VersionCodesInfo {

    val list = VersionCodesInfoGenerated.list
    val obj = VersionCodesInfoGenerated.obj

    @JvmStatic
    @JvmOverloads
    fun briefOfVersionInt(versionInt: Int, isCompact: Boolean = false): String {
        return list.find { runCatching { it.apiLevel.toInt() }.getOrNull() == versionInt }?.let { info ->
            var result = "${if (isCompact) "API Lv." else "Android API Level "}${info.apiLevel}"
            info.releaseName.takeIf { it.isNotBlank() }?.let { result += " ($it)" }
            info.internalCodename.takeIf { it.isNotBlank() }?.let { result += " [$it]" }
            result
        } ?: throw WrappedIllegalArgumentException("$versionInt is an invalid or unknown version int")
    }

    @JvmStatic
    @JvmOverloads
    fun briefOfCurrentVersionInt(isCompat: Boolean = false) = briefOfVersionInt(Build.VERSION.SDK_INT, isCompat)

}
