package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory.Companion.EXTENSIBILITY
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingType
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs6.R

/**
 * Settings > Extensibility (docs/settings/ROADMAP.md P2.4, appendix A.1 `extensibility`): extending the built-in
 * JavaScript objects and wrapping Java primitives in Rhino; both apply to scripts started afterwards. P3.6 (appendix
 * C Q15) adds the script roots that 3-Stove Agent connects with.
 *
 * zh-CN: 设置 > 扩展性 (ROADMAP P2.4, 附录 A.1 `extensibility`): 扩展 JavaScript 内置对象, Rhino 中包装 Java 原始类型; 二者对之后
 * 启动的脚本生效. P3.6 (附录 C Q15) 加入 3-Stove Agent 连接时使用的脚本根目录配置.
 */
object ExtensibilityKeys : SettingsDomain {

    @JvmField
    val EXTENDING_JS_BUILD_IN_OBJECTS = SettingKey.Bool(
        R.string.key_extending_js_build_in_objects, EXTENSIBILITY,
        boolResource(R.bool.pref_extending_js_build_in_objects, "pref_extending_js_build_in_objects"),
    )

    @JvmField
    val RHINO_JAVA_PRIMITIVE_WRAP = SettingKey.Bool(
        R.string.key_rhino_java_primitive_wrap, EXTENSIBILITY,
        boolResource(R.bool.pref_rhino_java_primitive_wrap, "pref_rhino_java_primitive_wrap"),
    )

    /**
     * The script roots of 3-Stove Agent as a JSON object, normalized by `ThreeStoveAgentScriptRoots.configuration`
     * before it is saved (`ThreeStoveAgentConnectionOwner`); the name has no `key_` prefix (P3.6, appendix C Q15).
     * zh-CN: 3-Stove Agent 的脚本根目录配置 (JSON 对象, 保存前经 `ThreeStoveAgentScriptRoots.configuration` 规范化); 名称没有
     * `key_` 前缀 (P3.6, 附录 C Q15).
     */
    @JvmField
    val THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION = SettingKey.Json(
        name = "three_stove_agent_script_root_configuration", category = EXTENSIBILITY,
        jsonType = SettingType.Json(SettingsJson.OBJECT), default = SettingDefault.of(emptyMap()),
    )

    override val keys: List<PrefKey<*>> = listOf(EXTENDING_JS_BUILD_IN_OBJECTS, RHINO_JAVA_PRIMITIVE_WRAP, THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION)

    /**
     * The stored root configuration text as it is, else `{}` (`Pref.getString(key, "{}")`): the owner normalizes the
     * text itself and falls back to `{}` when that fails, as before (R2).
     * zh-CN: 原样返回存储的根配置文本, 不存在时为 `{}`: 规范化与失败时回退到 `{}` 仍由调用方完成, 与原先一致 (R2).
     */
    @JvmStatic
    fun threeStoveAgentScriptRootConfigurationText(): String =
        THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION.settingsStore.getString(THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION.name, "{}")!!

    /** Saves the normalized configuration [text] as it is (`Pref.putString`, applied asynchronously). */
    @JvmStatic
    fun setThreeStoveAgentScriptRootConfigurationText(text: String) {
        THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION.settingsStore.put(THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION.name, text)
    }
}
