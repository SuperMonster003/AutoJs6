package org.autojs.autojs.core.terminal

import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R

/**
 * Persisted terminal settings. zh-CN: 持久化的终端设置.
 */
object TerminalPreferences {

    const val DEFAULT_TEXT_SIZE_SP = 12
    const val MIN_TEXT_SIZE_SP = 6
    const val MAX_TEXT_SIZE_SP = 40

    var textSizeSp: Int
        get() = Pref.getInt(R.string.key_terminal_text_size, DEFAULT_TEXT_SIZE_SP).coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
        set(value) = Pref.putInt(R.string.key_terminal_text_size, value.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP))

    /**
     * Registry URL for npm / corepack, or null when npm's own default applies.
     * zh-CN: npm / corepack 的 registry URL; 使用 npm 自身默认值时为 null.
     */
    val npmRegistry: String?
        get() = when (Pref.getStringOrNull(R.string.key_terminal_npm_registry)) {
            key(R.string.key_terminal_npm_registry_npmmirror) -> TerminalNodeEnvironment.NPMMIRROR_REGISTRY
            key(R.string.key_terminal_npm_registry_custom) -> TerminalNodeEnvironment.sanitizeRegistry(npmRegistryCustomUrl)
            else -> null
        }

    var npmRegistryCustomUrl: String?
        get() = Pref.getStringOrNull(R.string.key_terminal_npm_registry_custom_url)
        set(value) = Pref.putString(R.string.key_terminal_npm_registry_custom_url, value?.trim()?.takeIf { it.isNotEmpty() })

    val npmIgnoreScripts: Boolean
        get() = Pref.getBoolean(R.string.key_terminal_npm_ignore_scripts, false)

    fun nodeEnvironmentOptions() = TerminalNodeEnvironment.Options(
        registry = npmRegistry,
        ignoreScripts = npmIgnoreScripts,
    )

}
