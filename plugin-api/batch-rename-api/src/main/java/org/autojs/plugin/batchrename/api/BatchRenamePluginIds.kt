package org.autojs.plugin.batchrename.api

/**
 * Identity of the official Batch Rename plugin, as reported by its `PluginInfo`.
 *
 * zh-CN: 官方 Batch Rename 插件的身份, 与其 `PluginInfo` 返回值一致.
 */
object BatchRenamePluginIds {
    const val ID = "batch-rename"
    const val ENGINE = "batch-rename"
    const val VARIANT_DEFAULT = "default"

    /** Application id of the official plugin (install wizard and diagnostics). */
    const val PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.batch.rename"
}
