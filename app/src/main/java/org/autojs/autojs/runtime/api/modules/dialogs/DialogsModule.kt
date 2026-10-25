package org.autojs.autojs.runtime.api.modules.dialogs

import android.view.View
import com.afollestad.materialdialogs.Theme
import org.autojs.autojs.core.ui.dialog.JsDialog
import org.autojs.autojs.core.ui.dialog.JsDialogBuilder
import org.autojs.autojs.core.ui.nativeview.NativeView
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsXml
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.util.StringUtils.looseMatches
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Wrapper

/**
 * The `dialogs` module on the new script module layer (roadmap P3.2): `build`, `rawInput` / `input` / `prompt`,
 * `alert`, `confirm`, `select`, `singleChoice` and `multiChoice`, in the order of the current `augment/dialogs/Dialogs`,
 * whose companion still builds and shows the dialogs (to be moved out before P5.2); `rawInput`, `prompt`, `alert` and
 * `confirm` are also global. Each function checks its arguments and calls the current companion with them; a callback
 * in place of the second argument still moves to the callback, a missing callback on the UI thread still gives a
 * Promise.
 *
 * Arguments follow the real signatures (T4, appendix C Q7). A title, prefill or content is a string (JS ToString,
 * nullish is empty as before), a callback a function, items an array of strings, an index an integer; another type is
 * a TypeError (the current module coerced most values, threw IllegalArgumentException for the rest). A callback in place
 * of the second argument together with a third argument, and a function among the items of `select` are TypeErrors (no
 * matching overload). `build` checks the documented properties (appendix E): their types (TypeError), their words and
 * numbers (RangeError), and every other key against the single-parameter methods of the dialog builder (an unknown key
 * is a RangeError; the current module threw IllegalArgumentException from reflection, the obsolete module ignored it).
 * The values of builder methods are still converted by the current module's reflection.
 *
 * zh-CN: 新脚本模块层上的 dialogs 模块 (P3.2): build, rawInput / input / prompt, alert, confirm, select, singleChoice 与 multiChoice, 顺序同现有
 * augment/dialogs/Dialogs, 对话框仍由其伴生对象构建与显示 (P5.2 前迁出); rawInput, prompt, alert 与 confirm 同时是全局函数. 各函数检查参数后以原参数调用
 * 现有伴生对象; 第二个参数位置上的回调照旧移到回调位置, UI 线程上没有回调时照旧返回 Promise. 参数按真实签名 (T4, 附录 C Q7). 标题, 预填与内容为字符串
 * (JS ToString, nullish 照旧为空串), 回调为函数, items 为字符串数组, 索引为整数; 类型不符为 TypeError (现有模块多数值强制转换, 其余抛
 * IllegalArgumentException). 第二个参数为回调又带第三个参数, 以及 select 的 items 中出现函数, 为 TypeError (没有匹配的重载). build 检查文档所列属性
 * (附录 E): 类型 (TypeError), 单词与数值 (RangeError), 其他键对照对话框构建器的单参数方法 (未知键为 RangeError; 现有模块由反射抛
 * IllegalArgumentException, 旧 JS 忽略). 构建器方法的值仍由现有模块的反射转换.
 */
@ScriptModule(name = "dialogs")
class DialogsModule(private val runtime: ScriptRuntime) {

    @ScriptFunction
    fun build(properties: Any? = null): JsDialog {
        checkProperties("dialogs.build", properties)
        return Dialogs.build(runtime, arrayOf(properties))
    }

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun rawInput(title: Any?, prefill: Any? = null, callback: Any? = null): Any? =
        Dialogs.rawInput(runtime, texts("dialogs.rawInput", "prefill", title, prefill, callback))

    @ScriptFunction
    fun input(title: Any?, prefill: Any? = null, callback: Any? = null): Any? =
        Dialogs.input(runtime, texts("dialogs.input", "prefill", title, prefill, callback))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun prompt(title: Any?, prefill: Any? = null, callback: Any? = null): Any? =
        Dialogs.prompt(runtime, texts("dialogs.prompt", "prefill", title, prefill, callback))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun alert(title: Any?, content: Any? = null, callback: Any? = null): Any? =
        Dialogs.alert(runtime, texts("dialogs.alert", "content", title, content, callback))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun confirm(title: Any?, content: Any? = null, callback: Any? = null): Any? =
        Dialogs.confirm(runtime, texts("dialogs.confirm", "content", title, content, callback))

    @ScriptFunction
    fun select(title: Any?, vararg items: Any?): Any? {
        val function = "dialogs.select"
        val first = items.firstOrNull()
        if (first !is NativeArray) {
            items.forEachIndexed { index, item ->
                if (item is Callable) throw ScriptArgs.noOverload(function, arrayOf(title, *items), SELECT_SIGNATURES)
                ScriptArgs.string(item, function, "items[$index]")
            }
            return Dialogs.select(runtime, arrayOf(text(function, "title", title), *items))
        }
        if (items.size > 2) throw ScriptArgs.noOverload(function, arrayOf(title, *items), SELECT_SIGNATURES)
        strings(function, "items", first)
        return Dialogs.select(runtime, arrayOf(text(function, "title", title), first, callback(function, items.getOrNull(1))))
    }

    @ScriptFunction
    fun singleChoice(title: Any?, items: Any?, index: Any? = null, callback: Any? = null): Any? {
        val function = "dialogs.singleChoice"
        val checkedIndex = if (ScriptArgs.isNullish(index)) 0 else ScriptArgs.int(index, function, "index")
        return Dialogs.singleChoice(runtime, arrayOf(text(function, "title", title), strings(function, "items", items), checkedIndex, callback(function, callback)))
    }

    @ScriptFunction
    fun multiChoice(title: Any?, items: Any?, indices: Any? = null, callback: Any? = null): Any? {
        val function = "dialogs.multiChoice"
        val checkedItems = strings(function, "items", items)
        when (indices) {
            is NativeArray -> indices.indices.forEach { ScriptArgs.int(indices[it], function, "indices[$it]") }
            else -> if (!ScriptArgs.isNullish(indices)) throw GlobalValues.typeError(function, "indices", "number[]", indices)
        }
        return Dialogs.multiChoice(runtime, arrayOf(text(function, "title", title), checkedItems, indices, callback(function, callback)))
    }

    /** The arguments of `rawInput(title, prefill?, callback?)` and the like, with a callback in place of [second] moved. */
    private fun texts(function: String, second: String, title: Any?, value: Any?, callback: Any?): Array<Any?> {
        if (value is Callable) {
            if (!ScriptArgs.isNullish(callback)) throw ScriptArgs.noOverload(function, arrayOf(title, value, callback), "(title, $second?, callback?), (title, callback)")
            return arrayOf(text(function, "title", title), "", value)
        }
        return arrayOf(text(function, "title", title), text(function, second, value), callback(function, callback))
    }

    private fun text(function: String, param: String, value: Any?): String = if (ScriptArgs.isNullish(value)) "" else ScriptArgs.string(value, function, param)

    private fun callback(function: String, value: Any?): Callable? = when (value) {
        is Callable -> value
        else -> if (ScriptArgs.isNullish(value)) null else throw GlobalValues.typeError(function, "callback", "function", value)
    }

    /** [value] as an array of strings, which the current module reads with JS ToString. */
    private fun strings(function: String, param: String, value: Any?): NativeArray {
        if (value !is NativeArray) throw GlobalValues.typeError(function, param, "string[]", value)
        value.indices.forEach { ScriptArgs.string(value[it], function, "$param[$it]") }
        return value
    }

    private fun checkProperties(function: String, properties: Any?) {
        if (ScriptArgs.isNullish(properties)) return
        if (properties !is NativeObject) throw GlobalValues.typeError(function, "properties", "Object", properties)
        properties.ids.forEach { id ->
            val key = id.toString()
            val value = ScriptableObject.getProperty(properties, key)
            if (key !in PROPERTIES && key !in CONVERTED && BUILDER_METHODS?.contains(key) == false) {
                throw GlobalValues.rangeError(function, "properties key", "a property of $function or a single-parameter method of ${JsDialogBuilder::class.java.simpleName}", key)
            }
            if (!ScriptArgs.isNullish(value)) checkProperty(function, "properties.$key", key, value)
        }
    }

    private fun checkProperty(function: String, param: String, key: String, value: Any?) {
        when (key) {
            in BOOLEANS -> ScriptArgs.boolean(value, function, param)
            "inputHint", "inputPrefill", "checkBoxPrompt" -> ScriptArgs.string(value, function, param)
            "itemsSelectMode" -> {
                if (!ScriptArgs.isString(value)) throw GlobalValues.typeError(function, param, SELECT_MODES.joinToString(" | ") { "'$it'" }, value)
                if (value.toString() !in SELECT_MODES) throw GlobalValues.rangeError(function, param, SELECT_MODES.joinToString(" | ") { "'$it'" }, value)
            }
            "itemsSelectedIndex", "itemsSelectedIndices" -> when (value) {
                is NativeArray -> value.indices.forEach { ScriptArgs.int(value[it], function, "$param[$it]") }
                else -> ScriptArgs.int(value, function, param)
            }
            "progress" -> {
                if (value !is NativeObject) throw GlobalValues.typeError(function, param, "Object", value)
                ScriptableObject.getProperty(value, "max").let { if (!ScriptArgs.isNullish(it)) ScriptArgs.int(it, function, "$param.max") }
                listOf("showMinMax", "horizontal").forEach { name ->
                    ScriptableObject.getProperty(value, name).let { if (!ScriptArgs.isNullish(it)) ScriptArgs.boolean(it, function, "$param.$name") }
                }
            }
            "theme" -> when {
                unwrap(value) is Theme -> Unit
                ScriptArgs.isString(value) -> if (value.toString().trim().lowercase() !in THEMES) throw GlobalValues.rangeError(function, param, "'light' | 'dark'", value)
                else -> throw GlobalValues.typeError(function, param, "${Theme::class.java.name} | 'light' | 'dark'", value)
            }
            "customView" -> if (!ScriptArgs.isString(value) && !value.isJsXml() && value !is NativeView && unwrap(value) !is View) {
                throw GlobalValues.typeError(function, param, "string | XML | ${View::class.java.name}", value)
            }
            "linkify" -> word(function, param, value, LINKIFY_WORDS)
            "animation" -> word(function, param, value, ANIMATION_WORDS)
            "dimAmount" -> if (ScriptArgs.double(value, function, param).isNaN()) throw GlobalValues.rangeError(function, param, "number", value)
            "onBackKey", "onBackPressed" -> when {
                value is Boolean || value is Callable -> Unit
                ScriptArgs.isString(value) -> if (!DISABLED.matches(value.toString())) throw GlobalValues.rangeError(function, param, "'disabled'", value)
                else -> throw GlobalValues.typeError(function, param, "boolean | function | 'disabled'", value)
            }
        }
    }

    /** `true` or one of [words] (matched as the current module does, ignoring case and separators); `false` is off. */
    private fun word(function: String, param: String, value: Any?, words: List<String>) {
        when {
            value is Boolean -> Unit
            ScriptArgs.isString(value) -> if (words.none { it.looseMatches(value.toString()) }) throw GlobalValues.rangeError(function, param, words.joinToString(" | ") { "'$it'" }, value)
            else -> throw GlobalValues.typeError(function, param, "boolean | string", value)
        }
    }

    private fun unwrap(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value

    private companion object {

        const val SELECT_SIGNATURES = "(title, items, callback?), (title, ...items)"

        /** The properties `dialogs.build` reads itself (the current module's `customJsPropertiesForBuild`). */
        val PROPERTIES = setOf(
            "animation", "background", "checkBoxChecked", "checkBoxPrompt", "customView", "dimAmount", "inputHint", "inputPrefill",
            "inputSingleLine", "itemsSelectedIndex", "itemsSelectedIndices", "itemsSelectMode", "keepScreenOn", "linkify", "onBackKey",
            "onBackPressed", "preset", "progress", "stubborn", "theme", "wrapInScrollView",
        )

        /** The keys the current module converts before it calls a builder method (its `propertySetterMap`). */
        val CONVERTED = setOf(
            "titleColor", "buttonRippleColor", "contentColor", "itemsColor", "positive", "positiveColor", "neutral", "neutralColor",
            "negative", "negativeColor", "linkColor", "bg", "background", "bgColor", "backgroundColor", "bgColorRes",
            "limitIconToDefaultSize", "alwaysCallSingleChoiceCallback", "alwaysCallMultiChoiceCallback",
        )

        val BOOLEANS = setOf(
            "preset", "inputSingleLine", "checkBoxChecked", "keepScreenOn", "stubborn", "wrapInScrollView",
            "limitIconToDefaultSize", "alwaysCallSingleChoiceCallback", "alwaysCallMultiChoiceCallback",
        )

        val SELECT_MODES = listOf("select", "single", "multi")

        val THEMES = setOf("light", "dark")

        val LINKIFY_WORDS = listOf(
            "webUrls", "web", "url", "urls", "emailAddresses", "email", "phoneNumbers", "phone", "tel",
            "mapAddresses", "addresses", "address", "street", "all",
        )

        val ANIMATION_WORDS = listOf("default", "activity", "dialog", "inputMethod", "toast", "translucent")

        val DISABLED = Regex("^disabled?$", RegexOption.IGNORE_CASE)

        /**
         * The public single-parameter methods of the dialog builder, the other keys `dialogs.build` accepts (the current
         * module calls them by reflection); null when the builder class cannot be loaded (the JVM without its Android
         * classes), in which case keys are not checked.
         */
        val BUILDER_METHODS: Set<String>? by lazy {
            try {
                JsDialogBuilder::class.java.methods.filter { it.parameterCount == 1 && it.declaringClass != Any::class.java }.map { it.name }.toSet()
            } catch (_: LinkageError) {
                null
            }
        }

    }

}
