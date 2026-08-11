package org.autojs.autojs.script

import org.autojs.autojs6.BuildConfig

/** Stable host-flavor admission policy for the independent Python Runtime plugin. */
internal object PythonRuntimeChannelPolicy {

    /** The normal AutoJs6 app always exposes the host adapter; packaged INRT apps do not. */
    fun isEnabled(isInrt: Boolean): Boolean = !isInrt

    val isEnabledForCurrentBuild: Boolean
        get() = isEnabled(
            isInrt = BuildConfig.isInrt,
        )
}
