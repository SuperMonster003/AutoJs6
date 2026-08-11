package org.autojs.autojs.script

/** Source marker for the default-off Python Runtime host channel. */
abstract class PythonScriptSource protected constructor(
    sourceName: String,
) : ScriptSource(sourceName, EXTENSION) {

    final override val engineName: String
        get() = ENGINE

    companion object {
        const val ENGINE = "python"
        const val EXTENSION = "py"

        @JvmStatic
        fun isPreviewEnabled(): Boolean = PythonRuntimePreviewFeature.isEnabled
    }
}

/** Compatibility facade for existing preview-named source-routing call sites. */
internal object PythonRuntimePreviewFeature {
    val isEnabled: Boolean
        get() = PythonRuntimeChannelPolicy.isEnabledForCurrentBuild
}
