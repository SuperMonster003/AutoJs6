package org.autojs.plugin.editor.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/**
 * Stable discovery and in-process ABI shared by the host and editor plugins.
 * Any incompatible interface, model or enum change requires a new [VERSION].
 */
object EditorPluginContract {

    const val VERSION = 7
    const val MIN_SUPPORTED_VERSION = 1
    const val MAX_SUPPORTED_VERSION = VERSION

    const val ACTION_EDITOR = "org.autojs.plugin.EDITOR"
    const val CATEGORY_ACE = "ace-editor"

    const val PLUGIN_ID = "ace-editor"
    const val ENGINE = "editor"
    const val VARIANT_ACE = "ace"

    const val DEFAULT_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.ace.editor"
    const val DEFAULT_ENTRYPOINT_CLASS_NAME =
        "io.github.supermonster003.autojs6.plugin.ace.editor.AceEditorPluginEntrypoint"

    const val META_DATA_ENTRYPOINT = "org.autojs.plugin.editor.ENTRYPOINT"
    const val META_DATA_CONTRACT_VERSION = "org.autojs.plugin.editor.CONTRACT_VERSION"
    const val META_DATA_REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION
    const val META_DATA_MAX_HOST_VERSION = "org.autojs.plugin.editor.MAX_HOST_VERSION"

    const val CAPABILITY_EMBEDDED_VIEW = "embeddedView"
    const val CAPABILITY_TEXT_MIRROR = "textMirror"
    const val CAPABILITY_BREAKPOINTS = "breakpoints"
    const val CAPABILITY_SEMANTIC_COMPLETION = "semanticCompletion"
    const val CAPABILITY_THEMES = "themes"
    const val CAPABILITY_FONTS = "fonts"
    const val CAPABILITY_SETTINGS = "settings"
}
