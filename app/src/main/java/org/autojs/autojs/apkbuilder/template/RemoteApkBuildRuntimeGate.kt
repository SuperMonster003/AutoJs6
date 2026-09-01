package org.autojs.autojs.apkbuilder.template

import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs6.R

/** Host-side R1 kill switch. Missing or corrupt preference state always means disabled. */
internal object RemoteApkBuildRuntimeGate {

    fun isEnabled(): Boolean = runCatching {
        Pref.getBoolean(R.string.key_apk_builder_remote_build_enabled, false)
    }.getOrDefault(false)
}
