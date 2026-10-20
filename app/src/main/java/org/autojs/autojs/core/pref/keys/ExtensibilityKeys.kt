package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory.Companion.EXTENSIBILITY
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs6.R

/**
 * Settings > Extensibility (docs/settings/ROADMAP.md P2.4, appendix A.1 `extensibility`): extending the built-in
 * JavaScript objects and wrapping Java primitives in Rhino; both apply to scripts started afterwards.
 *
 * zh-CN: 设置 > 扩展性 (ROADMAP P2.4, 附录 A.1 `extensibility`): 扩展 JavaScript 内置对象, Rhino 中包装 Java 原始类型; 二者对之后
 * 启动的脚本生效.
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

    override val keys: List<PrefKey<*>> = listOf(EXTENDING_JS_BUILD_IN_OBJECTS, RHINO_JAVA_PRIMITIVE_WRAP)
}
