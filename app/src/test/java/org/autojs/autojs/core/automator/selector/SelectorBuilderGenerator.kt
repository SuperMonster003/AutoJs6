package org.autojs.autojs.core.automator.selector

/**
 * Renders `UiSelectorBuilders.kt`, the builder methods of `UiSelector`, from [SelectorRegistry].
 * The output is committed to the main source set (readable, navigable, diffable, no build-time
 * plugin); [UiSelectorBuildersTest] fails whenever the committed file no longer matches the
 * registry and writes the fresh rendering next to the build outputs for copying over.
 *
 * zh-CN: 由 [SelectorRegistry] 渲染 `UiSelectorBuilders.kt` (`UiSelector` 的 builder 方法). 输出提交在主源码集中
 * (可读, 可跳转, 可 diff, 无构建期插件); 提交的文件与注册表不一致时 [UiSelectorBuildersTest] 失败, 并把新渲染写到
 * 构建输出旁以供复制.
 */
object SelectorBuilderGenerator {

    const val RELATIVE_PATH = "src/main/java/org/autojs/autojs/core/accessibility/UiSelectorBuilders.kt"

    const val CLASS_NAME = "UiSelectorBuilders"

    fun generate(registry: SelectorRegistry = SelectorRegistry): String = buildString {
        appendLine("@file:Suppress(\"unused\", \"DEPRECATION\")")
        appendLine()
        appendLine("package org.autojs.autojs.core.accessibility")
        appendLine()
        appendLine("import androidx.annotation.FloatRange")
        appendLine("import org.autojs.autojs.annotation.ScriptInterface")
        appendLine("import org.autojs.autojs.core.automator.filter.BooleanFilter")
        appendLine("import org.autojs.autojs.util.App")
        appendLine("import org.mozilla.javascript.regexp.NativeRegExp")
        appendLine()
        appendLine("/*")
        appendLine(" * GENERATED from SelectorRegistry by SelectorBuilderGenerator (app/src/test), do not edit by hand:")
        appendLine(" * change the registry, run UiSelectorBuildersTest and copy the rendering it writes to")
        appendLine(" * app/build/generated-selector-builders/ over this file.")
        appendLine(" *")
        appendLine(" * The builder methods of UiSelector, one per registry overload, each routing to the registry")
        appendLine(" * through applyEntry. They exist as real methods so that scripts (Rhino), the d.ts generator and")
        appendLine(" * Kotlin callers see the same typed surface as before; the behaviour lives in the registry.")
        appendLine(" *")
        appendLine(" * zh-CN: 由 SelectorRegistry 经 SelectorBuilderGenerator (app/src/test) 生成, 请勿手工编辑: 修改注册表后运行")
        appendLine(" * UiSelectorBuildersTest, 并把它写到 app/build/generated-selector-builders/ 的渲染复制到本文件.")
        appendLine(" *")
        appendLine(" * UiSelector 的 builder 方法, 注册表的每个重载对应一个, 全部经 applyEntry 转到注册表. 它们以真实方法存在, 使脚本")
        appendLine(" * (Rhino), d.ts 生成器与 Kotlin 调用方看到与从前相同的类型化表面; 行为在注册表中.")
        appendLine(" */")
        appendLine("abstract class $CLASS_NAME {")
        appendLine()
        appendLine("    /** Applies registry property [name] with [args] to this selector and returns it. zh-CN: 把注册表属性 [name] 以 [args] 施加到本选择器并返回它. */")
        appendLine("    protected abstract fun applyEntry(name: String, vararg args: Any?): UiSelector")
        for (entry in registry.entries) {
            for (overload in entry.overloads) {
                appendLine()
                appendLine("    @ScriptInterface")
                entry.deprecatedFor?.let { replacement ->
                    val call = overload.params.joinToString(", ") { it.name }
                    appendLine("    @Deprecated(\"Deprecated in Java\", ReplaceWith(\"$replacement($call)\"))")
                }
                val params = overload.params.joinToString(", ") { param -> renderParam(param, overload.isVararg) }
                val args = overload.params.joinToString("") { param -> if (overload.isVararg) ", *${param.name}" else ", ${param.name}" }
                appendLine("    fun ${entry.name}($params): UiSelector = applyEntry(\"${entry.name}\"$args)")
            }
        }
        appendLine()
        appendLine("}")
    }

    private fun renderParam(param: SelectorParam, vararg: Boolean): String {
        val range = param.range?.let { "@FloatRange(${it.start}, ${it.endInclusive}) " } ?: ""
        val modifier = if (vararg) "vararg " else ""
        return "$range$modifier${param.name}: ${param.type.kotlinType}"
    }

}
