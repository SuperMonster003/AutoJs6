package org.autojs.autojs.runtime.api.augment.dialogs

import android.os.Build
import android.text.util.Linkify
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager.LayoutParams
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.afollestad.materialdialogs.Theme
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.ui.dialog.JsDialog
import org.autojs.autojs.core.ui.dialog.JsDialogBuilder
import org.autojs.autojs.core.ui.nativeview.NativeView
import org.autojs.autojs.event.BackCompat.TAG_KEY_INSTALLED
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component4
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsString
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsXml
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.colors.Colors
import org.autojs.autojs.runtime.api.augment.ui.UI
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.constructFunction
import org.autojs.autojs.util.RhinoUtils.isUiThread
import org.autojs.autojs.util.RhinoUtils.js_eval
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.StringUtils.looseMatches
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable

class Dialogs(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::build.name,
        ::rawInput.name to AS_GLOBAL,
        ::input.name,
        ::prompt.name to AS_GLOBAL,
        ::alert.name to AS_GLOBAL,
        ::confirm.name to AS_GLOBAL,
        ::select.name,
        ::singleChoice.name,
        ::multiChoice.name,
    )

    companion object : ArgumentGuards() {

        private enum class PropertyConverter {
            COLOR_INT, BOOLEAN,
        }

        private val propertySetterMap = mapOf(
            "titleColor" to PropertyConverter.COLOR_INT,
            "buttonRippleColor" to PropertyConverter.COLOR_INT,
            "contentColor" to PropertyConverter.COLOR_INT,
            "itemsColor" to PropertyConverter.COLOR_INT,
            "positive" to "positiveText",
            "positiveColor" to PropertyConverter.COLOR_INT,
            "neutral" to "neutralText",
            "neutralColor" to PropertyConverter.COLOR_INT,
            "negative" to "negativeText",
            "negativeColor" to PropertyConverter.COLOR_INT,
            "linkColor" to PropertyConverter.COLOR_INT,
            "bg" to "background",
            "background" to "backgroundColor",
            "bgColor" to "backgroundColor",
            "backgroundColor" to PropertyConverter.COLOR_INT,
            "bgColorRes" to "backgroundColorRes",
            "limitIconToDefaultSize" to PropertyConverter.BOOLEAN,
            "alwaysCallSingleChoiceCallback" to PropertyConverter.BOOLEAN,
            "alwaysCallMultiChoiceCallback" to PropertyConverter.BOOLEAN,
        )

        @Suppress("DEPRECATION")
        private val linkifyMasksMappings = listOf(
            "webUrls" to Linkify.WEB_URLS,
            "web" to Linkify.WEB_URLS,
            "url" to Linkify.WEB_URLS,
            "urls" to Linkify.WEB_URLS,

            "emailAddresses" to Linkify.EMAIL_ADDRESSES,
            "email" to Linkify.EMAIL_ADDRESSES,

            "phoneNumbers" to Linkify.PHONE_NUMBERS,
            "phone" to Linkify.PHONE_NUMBERS,
            "tel" to Linkify.PHONE_NUMBERS,

            "mapAddresses" to Linkify.MAP_ADDRESSES,
            "addresses" to Linkify.MAP_ADDRESSES,
            "address" to Linkify.MAP_ADDRESSES,
            "street" to Linkify.MAP_ADDRESSES,

            "all" to Linkify.ALL,
        )

        private val animationMappings = listOf(
            "default" to android.R.style.Animation,
            "activity" to android.R.style.Animation_Activity,
            "dialog" to android.R.style.Animation_Dialog,
            "inputMethod" to android.R.style.Animation_InputMethod,
            "toast" to android.R.style.Animation_Toast,
            "translucent" to android.R.style.Animation_Translucent,
        )

        private val customJsPropertiesForBuild: Set<String> = setOf(
            "animation",
            "background",
            "checkBoxChecked",
            "checkBoxPrompt",
            "customView",
            "dimAmount",
            "inputHint",
            "inputPrefill",
            "inputSingleLine",
            "itemsSelectedIndex",
            "itemsSelectedIndices",
            "itemsSelectMode",
            "keepScreenOn",
            "linkify",
            "onBackKey",
            "onBackPressed",
            "preset",
            "progress",
            "stubborn",
            "theme",
            "wrapInScrollView",
        )

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun build(scriptRuntime: ScriptRuntime, args: Array<out Any?>): JsDialog = ensureArgumentsAtMost(args, 1) { argList ->
            val (propertiesRaw) = argList
            require(propertiesRaw is NativeObject || propertiesRaw.isJsNullish()) {
                "Argument properties ${propertiesRaw.jsBrief()} must be a JavaScript Object for dialogs.build"
            }
            val properties = when {
                propertiesRaw.isJsNullish() -> {
                    newNativeObject().also { o -> o.defineProp("preset", true) }
                }
                else -> RhinoUtils.js_object_assign(newNativeObject(), propertiesRaw as Scriptable) as NativeObject
            }
            if (properties.inquire("preset", ::coerceBoolean, false)) {
                checkPreset(properties, "title", R.string.text_preset_dialog_title)
                checkPreset(properties, "content", R.string.text_preset_dialog_content)
                checkPreset(properties, "positive", R.string.dialog_button_confirm)
                checkPreset(properties, "negative", R.string.dialog_button_cancel)
                checkPreset(properties, "neutral", R.string.dialog_button_more)
            }
            val builder = scriptRuntime.dialogs.newBuilder().also {
                it.thread = scriptRuntime.threads.currentThread()
            }
            properties.minus("preset").forEach { entry ->
                val (nameArg) = entry
                val name = coerceString(nameArg)
                applyDialogProperty(builder, name, properties.prop(name))
            }
            applyOtherDialogProperties(scriptRuntime, builder, properties)
            val dialog = UI.runRhinoRuntime(scriptRuntime, newBaseFunction("action", { builder.buildDialog() }, NOT_CONSTRUCTABLE)) as JsDialog
            applyBuiltDialogProperties(scriptRuntime, dialog, properties)
            dialog
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun rawInput(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val (titleArg, prefillArg, callback) = argList
            when {
                prefillArg is BaseFunction -> {
                    rawInput(scriptRuntime, arrayOf(titleArg, /* prefill = */ null, /* callback = */ prefillArg))
                }
                isUiThread() && callback !is BaseFunction -> {
                    val title = coerceString(titleArg, "")
                    val prefill = coerceString(prefillArg, "")
                    val scope = scriptRuntime.topLevelScope
                    val promiseExecutor = newBaseFunction("executor", { executorArgs ->
                        val (resolve) = executorArgs
                        require(resolve is BaseFunction) {
                            "Argument resolve ${resolve.jsBrief()} must be a JavaScript Function for promise executor of dialogs.rawInput"
                        }
                        scriptRuntime.dialogs.rawInput(title, prefill, newBaseFunction("callback", { callbackArgs ->
                            callFunction(resolve, scope, null, arrayOf(*callbackArgs))
                        }, NOT_CONSTRUCTABLE))
                    }, NOT_CONSTRUCTABLE)
                    constructFunction(scriptRuntime.js_Promise, scope, arrayOf(promiseExecutor))
                }
                callback.isJsNullish() -> {
                    val title = coerceString(titleArg, "")
                    val prefill = coerceString(prefillArg, "")
                    scriptRuntime.dialogs.rawInput(title, prefill, null)
                }
                else -> {
                    val title = coerceString(titleArg, "")
                    val prefill = coerceString(prefillArg, "")
                    require(callback is BaseFunction) {
                        "Argument callback ${callback.jsBrief()} must be a JavaScript Function for dialogs.rawInput"
                    }
                    scriptRuntime.dialogs.rawInput(title, prefill, callback)
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun input(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val (titleArg, prefillArg, callback) = argList
            when {
                prefillArg is BaseFunction -> {
                    input(scriptRuntime, arrayOf(titleArg, /* prefill = */ null, /* callback = */ prefillArg))
                }
                isUiThread() && callback !is BaseFunction -> {
                    val title = coerceString(titleArg, "")
                    val prefill = coerceString(prefillArg, "")
                    val scope = scriptRuntime.topLevelScope
                    val promiseExecutor = newBaseFunction("executor", { executorArgs ->
                        val (resolve) = executorArgs
                        require(resolve is BaseFunction) {
                            "Argument resolve ${resolve.jsBrief()} must be a JavaScript Function for promise executor of dialogs.input"
                        }
                        scriptRuntime.dialogs.rawInput(title, prefill, newBaseFunction("callback", { callbackArgs ->
                            val (inputStringArg) = callbackArgs
                            val inputString = coerceString(inputStringArg, "")
                            val evaluated = js_eval(scope, inputString)
                            callFunction(scriptRuntime, resolve, scope, null, arrayOf(evaluated))
                        }, NOT_CONSTRUCTABLE))
                    }, NOT_CONSTRUCTABLE)
                    constructFunction(scriptRuntime.js_Promise, scope, arrayOf(promiseExecutor))
                }
                callback.isJsNullish() -> {
                    val title = coerceString(titleArg, "")
                    val prefill = coerceString(prefillArg, "")
                    val result = scriptRuntime.dialogs.rawInput(title, prefill, null)
                    js_eval(scriptRuntime.topLevelScope, Context.toString(result))
                }
                else -> {
                    val title = coerceString(titleArg, "")
                    val prefill = coerceString(prefillArg, "")
                    require(callback is BaseFunction) {
                        "Argument callback ${callback.jsBrief()} must be a JavaScript Function for dialogs.input"
                    }
                    val newCallback = newBaseFunction("newCallback", { newCallbackArgs ->
                        val (str) = newCallbackArgs
                        val newStr = js_eval(scriptRuntime.topLevelScope, Context.toString(str))
                        callFunction(callback, scriptRuntime.topLevelScope, null, arrayOf(newStr))
                    }, NOT_CONSTRUCTABLE)
                    scriptRuntime.dialogs.rawInput(title, prefill, newCallback)
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun prompt(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val (title, prefill, callback) = argList
            when (prefill) {
                is BaseFunction -> prompt(scriptRuntime, arrayOf(title, /* prefill = */ null, /* callback = */ prefill))
                else -> rawInput(scriptRuntime, arrayOf(title, prefill, callback))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun alert(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val (titleArg, prefillArg, callback) = argList
            when {
                prefillArg is BaseFunction -> {
                    alert(scriptRuntime, arrayOf(titleArg, /* prefill = */ null, /* callback = */ prefillArg))
                }
                isUiThread() && callback !is BaseFunction -> {
                    val title = coerceString(titleArg, "")
                    val prefill = coerceString(prefillArg, "")
                    val scope = scriptRuntime.topLevelScope
                    val promiseExecutor = newBaseFunction("executor", { executorArgs ->
                        val (resolve) = executorArgs
                        require(resolve is BaseFunction) {
                            "Argument resolve ${resolve.jsBrief()} must be a JavaScript Function for promise executor of dialogs.alert"
                        }
                        scriptRuntime.dialogs.alert(title, prefill, newBaseFunction("callback", { callbackArgs ->
                            callFunction(scriptRuntime, resolve, scope, null, arrayOf(*callbackArgs))
                        }, NOT_CONSTRUCTABLE))
                    }, NOT_CONSTRUCTABLE)
                    constructFunction(scriptRuntime.js_Promise, scope, arrayOf(promiseExecutor))
                }
                callback.isJsNullish() -> {
                    val title = coerceString(titleArg, "")
                    val prefill = coerceString(prefillArg, "")
                    scriptRuntime.dialogs.alert(title, prefill, null)
                }
                else -> {
                    val title = coerceString(titleArg, "")
                    val prefill = coerceString(prefillArg, "")
                    require(callback is BaseFunction) {
                        "Argument callback ${callback.jsBrief()} must be a JavaScript Function for dialogs.alert"
                    }
                    scriptRuntime.dialogs.alert(title, prefill, callback)
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun confirm(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val (titleArg, prefillArg, callback) = argList
            when {
                prefillArg is BaseFunction -> {
                    confirm(scriptRuntime, arrayOf(titleArg, /* prefill = */ null, /* callback = */ prefillArg))
                }
                isUiThread() && callback !is BaseFunction -> {
                    val title = coerceString(titleArg, "")
                    val prefill = coerceString(prefillArg, "")
                    val scope = scriptRuntime.topLevelScope
                    val promiseExecutor = newBaseFunction("executor", { executorArgs ->
                        val (resolve) = executorArgs
                        require(resolve is BaseFunction) {
                            "Argument resolve ${resolve.jsBrief()} must be a JavaScript Function for promise executor of dialogs.confirm"
                        }
                        scriptRuntime.dialogs.confirm(title, prefill, newBaseFunction("callback", { callbackArgs ->
                            callFunction(scriptRuntime, resolve, scope, null, arrayOf(*callbackArgs))
                        }, NOT_CONSTRUCTABLE))
                    }, NOT_CONSTRUCTABLE)
                    constructFunction(scriptRuntime.js_Promise, scope, arrayOf(promiseExecutor))
                }
                callback.isJsNullish() -> {
                    val title = coerceString(titleArg, "")
                    val prefill = coerceString(prefillArg, "")
                    scriptRuntime.dialogs.confirm(title, prefill, null)
                }
                else -> {
                    val title = coerceString(titleArg, "")
                    val prefill = coerceString(prefillArg, "")
                    require(callback is BaseFunction) {
                        "Argument callback ${callback.jsBrief()} must be a JavaScript Function for dialogs.confirm"
                    }
                    scriptRuntime.dialogs.confirm(title, prefill, callback)
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun select(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtLeast(args, 1) { argList ->
            val (titleArg, items, callback) = argList
            val title = coerceString(titleArg, "")
            when (items) {
                is NativeArray -> when {
                    isUiThread() && callback !is BaseFunction -> {
                        val scope = scriptRuntime.topLevelScope
                        val promiseExecutor = newBaseFunction("executor", { executorArgs ->
                            val (resolve) = executorArgs
                            require(resolve is BaseFunction) {
                                "Argument resolve ${resolve.jsBrief()} must be a JavaScript Function for promise executor of dialogs.select"
                            }
                            val itemsToSelect = items.map(::coerceString).toTypedArray()
                            scriptRuntime.dialogs.select(title, itemsToSelect, newBaseFunction("callback", { callbackArgs ->
                                callFunction(scriptRuntime, resolve, scope, null, arrayOf(*callbackArgs))
                            }, NOT_CONSTRUCTABLE))
                        }, NOT_CONSTRUCTABLE)
                        constructFunction(scriptRuntime.js_Promise, scope, arrayOf(promiseExecutor))
                    }
                    callback.isJsNullish() -> {
                        val itemsToSelect = items.map(::coerceString).toTypedArray()
                        scriptRuntime.dialogs.select(title, itemsToSelect, null)
                    }
                    else -> {
                        require(callback is BaseFunction) {
                            "Argument callback ${callback.jsBrief()} must be a JavaScript Function for dialogs.select"
                        }
                        val itemsToSelect = items.map(::coerceString).toTypedArray()
                        scriptRuntime.dialogs.select(title, itemsToSelect, callback)
                    }
                }
                else -> {
                    val itemsGatheredFromArgs = argList.sliceArray(1 until argList.size)
                    val itemsToSelect = arrayOf(*itemsGatheredFromArgs.map(::coerceString).toTypedArray())
                    scriptRuntime.dialogs.select(title, itemsToSelect, null)
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun singleChoice(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 2..4) { argList ->
            val (titleArg, itemsArg, defaultIndex, callback) = argList
            val title = coerceString(titleArg, "")
            require(itemsArg is NativeArray) {
                "Argument items ${itemsArg.jsBrief()} must be a JavaScript Array for dialogs.singleChoice"
            }
            val items = itemsArg.map(::coerceString).toTypedArray()
            val selectedIndex = coerceIntNumber(defaultIndex, 0)
            when {
                isUiThread() && callback !is BaseFunction -> {
                    val scope = scriptRuntime.topLevelScope
                    val promiseExecutor = newBaseFunction("executor", { executorArgs ->
                        val (resolve) = executorArgs
                        require(resolve is BaseFunction) {
                            "Argument resolve ${resolve.jsBrief()} must be a JavaScript Function for promise executor of dialogs.singleChoice"
                        }
                        scriptRuntime.dialogs.singleChoice(title, selectedIndex, items, newBaseFunction("callback", { callbackArgs ->
                            callFunction(scriptRuntime, resolve, scope, null, arrayOf(*callbackArgs))
                        }, NOT_CONSTRUCTABLE))
                    }, NOT_CONSTRUCTABLE)
                    constructFunction(scriptRuntime.js_Promise, scope, arrayOf(promiseExecutor))
                }
                callback.isJsNullish() -> {
                    scriptRuntime.dialogs.singleChoice(title, selectedIndex, items, null)
                }
                else -> {
                    require(callback is BaseFunction) {
                        "Argument callback ${callback.jsBrief()} must be a JavaScript Function for dialogs.singleChoice"
                    }
                    scriptRuntime.dialogs.singleChoice(title, selectedIndex, items, callback)
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun multiChoice(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 2..4) { argList ->
            val (titleArg, itemsArg, defaultIndices, callback) = argList
            val title = coerceString(titleArg, "")
            require(itemsArg is NativeArray) {
                "Argument items ${itemsArg.jsBrief()} must be a JavaScript Array for dialogs.multiChoice"
            }
            val items = itemsArg.map(::coerceString).toTypedArray()
            val selectedIndices = when {
                defaultIndices.isJsNullish() -> intArrayOf()
                defaultIndices is NativeArray -> defaultIndices.map { coerceIntNumber(it, 0) }.toIntArray()
                else -> throw WrappedIllegalArgumentException("Argument \"defaultIndices\" ${defaultIndices.jsBrief()} for dialogs.multiChoice is invalid")
            }
            val scope = scriptRuntime.topLevelScope
            when {
                isUiThread() && callback !is BaseFunction -> {
                    val promiseExecutor = newBaseFunction("executor", { executorArgs ->
                        val (resolve) = executorArgs
                        require(resolve is BaseFunction) {
                            "Argument resolve ${resolve.jsBrief()} must be a JavaScript Function for promise executor of dialogs.multiChoice"
                        }
                        scriptRuntime.dialogs.multiChoice(title, selectedIndices, items, newBaseFunction("callback", { callbackArgs ->
                            val (results) = callbackArgs
                            callFunction(scriptRuntime, resolve, scope, null, arrayOf(toJsArrayIfNeeded(results)))
                        }, NOT_CONSTRUCTABLE))
                    }, NOT_CONSTRUCTABLE)
                    constructFunction(scriptRuntime.js_Promise, scope, arrayOf(promiseExecutor))
                }
                callback.isJsNullish() -> {
                    toJsArrayIfNeeded(scriptRuntime.dialogs.multiChoice(title, selectedIndices, items, null))
                }
                else -> {
                    require(callback is BaseFunction) {
                        "Argument callback ${callback.jsBrief()} must be a JavaScript Function for dialogs.multiChoice"
                    }
                    val transformedCallback = newBaseFunction("transformedCallback", { callbackArgs ->
                        val (results) = callbackArgs
                        val jsResults = toJsArrayIfNeeded(results)
                        callFunction(scriptRuntime, callback, scope, null, arrayOf(jsResults))
                    }, NOT_CONSTRUCTABLE)
                    toJsArrayIfNeeded(scriptRuntime.dialogs.multiChoice(title, selectedIndices, items, transformedCallback))
                }
            }
        }

        private fun toJsArrayIfNeeded(results: Any?) = when {
            results == null -> null
            results is Iterable<*> -> results.toNativeArray()
            results.javaClass.isArray -> RhinoUtils.javaArrayToString(results)
            else -> results
        }

        private fun checkPreset(properties: NativeObject, key: String, def: Any) {
            if (properties.prop(key).isJsNullish()) {
                properties.defineProp(key, def)
            }
        }

        private fun applyDialogProperty(builder: JsDialogBuilder, name: String, value: Any?) {
            val propertySetter = propertySetterMap[name]
            when {
                propertySetter == PropertyConverter.COLOR_INT -> {
                    val colorInt = Colors.toIntRhino(value)
                    invokeJavaMethod(builder, name, arrayOf(colorInt))
                }
                propertySetter == PropertyConverter.BOOLEAN && coerceBoolean(value, false) -> {
                    invokeJavaMethod(builder, name, emptyArray())
                }
                propertySetter is String -> when (propertySetter) {
                    in propertySetterMap -> {
                        applyDialogProperty(builder, propertySetter, value)
                    }
                    else -> invokeJavaMethod(builder, propertySetter, arrayOf(value))
                }
                else -> {
                    if (!customJsPropertiesForBuild.contains(name)) {
                        invokeJavaMethod(builder, name, arrayOf(value))
                    }
                }
            }
        }

        private fun applyOtherDialogProperties(scriptRuntime: ScriptRuntime, builder: JsDialogBuilder, properties: NativeObject) {
            if (!properties.prop("inputHint").isJsNullish() || !properties.prop("inputPrefill").isJsNullish()) {
                val inputHint = coerceString(properties.prop("inputHint"), "")
                val inputPrefill = coerceString(properties.prop("inputPrefill"), "")
                builder.input(inputHint, inputPrefill) { _, input ->
                    builder.emit("input_change", builder.dialog, input.toString())
                }.alwaysCallInputCallback()
            }

            if (!properties.prop("items").isJsNullish()) {
                val itemsSelectMode = properties.prop("itemsSelectMode")
                when {
                    itemsSelectMode.isJsNullish() || itemsSelectMode == "select" -> {
                        builder.itemsCallback { _, _, position, text ->
                            builder.emit("item_select", position, text.toString(), builder.dialog)
                        }
                    }
                    itemsSelectMode == "single" -> {
                        val selectedIndex = properties.inquire("itemsSelectedIndex", ::coerceIntNumber, -1)
                        builder.itemsCallbackSingleChoice(selectedIndex) { _, _, which, text ->
                            builder.emit("single_choice", which, text.toString(), builder.dialog)
                            true
                        }
                    }
                    itemsSelectMode == "multi" -> {
                        val itemsSelectedIndices = properties.prop("itemsSelectedIndices")
                        val itemsSelectedIndex = properties.prop("itemsSelectedIndex")
                        val selectedIndices = when {
                            !itemsSelectedIndices.isJsNullish() -> {
                                if (itemsSelectedIndices is NativeArray) {
                                    itemsSelectedIndices.map(::coerceIntNumber).toTypedArray()
                                } else {
                                    arrayOf(coerceIntNumber(itemsSelectedIndices))
                                }
                            }
                            !itemsSelectedIndex.isJsNullish() -> {
                                if (itemsSelectedIndex is NativeArray) {
                                    itemsSelectedIndex.map(::coerceIntNumber).toTypedArray()
                                } else {
                                    arrayOf(coerceIntNumber(itemsSelectedIndex))
                                }
                            }
                            else -> emptyArray()
                        }
                        builder.itemsCallbackMultiChoice(selectedIndices) { _, indices, texts ->
                            builder.emit(
                                "multi_choice",
                                indices.map(::coerceIntNumber).toNativeArray(),
                                texts.map(::coerceString).toNativeArray(),
                                builder.dialog,
                            )
                            true
                        }
                    }
                    else -> throw Error("Unknown itemsSelectMode $itemsSelectMode")
                }
            }

            if (!properties.prop("progress").isJsNullish()) {
                val progress = properties.prop("progress")
                require(progress is NativeObject) {
                    "Property \"progress\" ${progress.jsBrief()} of argument properties for dialogs.build must be a JavaScript Object"
                }
                val max = progress.inquire("max", ::coerceIntNumber, 0)
                val showMinMax = progress.inquire("showMinMax", ::coerceBoolean, false)
                val horizontal = progress.inquire("horizontal", ::coerceBoolean, false)
                builder.progress(max == -1, max, showMinMax)
                builder.progressIndeterminateStyle(horizontal)
            }

            if (!properties.prop("checkBoxPrompt").isJsNullish() || !properties.prop("checkBoxChecked").isJsNullish()) {
                builder.checkBoxPrompt(
                    properties.inquire("checkBoxPrompt", ::coerceString, ""),
                    properties.inquire("checkBoxChecked", ::coerceBoolean, false),
                ) { _, checked -> builder.dialog.emit("check", checked, builder.dialog) }
            }

            if (!properties.prop("customView").isJsNullish()) {
                var customView = properties.prop("customView")
                if (customView.isJsXml() || customView.isJsString()) {
                    customView = UI.runRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                        UI.inflateRhinoRuntime(scriptRuntime, customView)
                    }, NOT_CONSTRUCTABLE))
                }
                if (customView is NativeView) {
                    customView = customView.unwrap()
                }
                require(customView is View) {
                    "Property \"customView\" ${customView.jsBrief()} of argument properties for dialogs.build is invalid"
                }
                val wrapInScrollView = properties.inquire("wrapInScrollView", ::coerceBoolean, true)
                builder.customView(customView, wrapInScrollView)
            }

            if (properties.inquire("stubborn", ::coerceBoolean, false)) {
                val isOperationAvailable: (Any?) -> Boolean = { it.isJsNullish() || !Context.toBoolean(it) }
                if (isOperationAvailable(properties.prop("autoDismiss")) && isOperationAvailable(properties.prop("canceledOnTouchOutside"))) {
                    builder.autoDismiss(false)
                    builder.canceledOnTouchOutside(false)
                }
            }

            if (!properties.prop("theme").isJsNullish()) {
                when (val theme = properties.prop("theme")) {
                    is Theme -> builder.theme(theme)
                    is String -> when (theme.lowercase().trim()) {
                        "dark" -> builder.theme(Theme.DARK)
                        "light" -> builder.theme(Theme.LIGHT)
                        else -> throw WrappedIllegalArgumentException(
                            "Unknown property theme ${theme.jsBrief()} of argument properties for dialogs.build",
                        )
                    }
                }
            }
        }

        private fun applyBuiltDialogProperties(scriptRuntime: ScriptRuntime, dialog: JsDialog, properties: NativeObject) {
            if (!properties.prop("linkify").isJsNullish() && Context.toBoolean(properties.prop("linkify"))) {
                val linkifyRaw = when (properties.prop("linkify")) {
                    true -> "all"
                    else -> properties.inquire("linkify", transformer = ::coerceString)
                }
                val autoLinkMask = run {
                    linkifyRaw?.let {
                        linkifyMasksMappings.firstOrNull {
                            it.first.looseMatches(linkifyRaw)
                        }?.second
                    } ?: throw IllegalArgumentException(
                        "Unknown property linkify ${linkifyRaw.jsBrief()} of argument properties for dialogs.build",
                    )
                }
                dialog.contentView?.let { view ->
                    val text = view.text
                    UI.runRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                        view.autoLinkMask = autoLinkMask
                        // @Hint by SuperMonster003 on Nov 9, 2024.
                        //  ! To trigger linkification.
                        //  ! zh-CN: 触发链接化.
                        view.text = text
                        UNDEFINED
                    }, NOT_CONSTRUCTABLE))
                }
            }

            properties.inquire(listOf("onBackKey", "onBackPressed"))?.let { onBackKey ->
                val isFunction = onBackKey is BaseFunction
                val isDisabled = onBackKey == false || (onBackKey is String && onBackKey.matches(Regex("^disabled?$", RegexOption.IGNORE_CASE)))
                if (isDisabled || isFunction) {
                    dialog.setOnBackPressedFromJs {
                        if (onBackKey is BaseFunction) {
                            val result = callFunction(scriptRuntime, onBackKey, arrayOf(dialog))
                            return@setOnBackPressedFromJs coerceBoolean(result, false)
                        }
                        return@setOnBackPressedFromJs false
                    }
                    dialog.setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                            dialog.onBackPressed()
                            return@setOnKeyListener true
                        }
                        return@setOnKeyListener false
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        runCatching { if (dialog.window == null) dialog.create() }

                        val decor = dialog.window?.decorView ?: run {
                            val dispatcher = dialog.window?.onBackInvokedDispatcher
                            dispatcher?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY) {
                                runCatching {
                                    dialog.onBackPressed()
                                }.onFailure {
                                    runCatching { dialog.cancel() }.onFailure { runCatching { dialog.dismiss() } }
                                }
                            }
                            return
                        }

                        if (decor.getTag(TAG_KEY_INSTALLED) == true) return
                        decor.setTag(TAG_KEY_INSTALLED, true)

                        var dispatcher: OnBackInvokedDispatcher? = null
                        var callback: OnBackInvokedCallback? = null

                        fun unregister() {
                            val d = dispatcher
                            val c = callback
                            if (d != null && c != null) runCatching { d.unregisterOnBackInvokedCallback(c) }
                            dispatcher = null
                            callback = null
                        }

                        val listener = object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                unregister()
                                val d = dialog.window?.onBackInvokedDispatcher
                                val c = OnBackInvokedCallback {
                                    runCatching {
                                        dialog.onBackPressed()
                                    }.onFailure {
                                        runCatching { dialog.cancel() }.onFailure { runCatching { dialog.dismiss() } }
                                    }
                                }
                                d?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, c)
                                dispatcher = d
                                callback = c
                            }

                            override fun onViewDetachedFromWindow(v: View) {
                                unregister()
                            }
                        }

                        decor.addOnAttachStateChangeListener(listener)
                        if (decor.isAttachedToWindow) listener.onViewAttachedToWindow(decor)
                    }
                }
            }

            if (!properties.prop("dimAmount").isJsNullish()) {
                dialog.window?.let { win ->
                    var dim = Context.toNumber(properties.prop("dimAmount"))
                    while (dim > 1) dim /= 100
                    if (!dim.isNaN()) {
                        UI.postRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                            win.setDimAmount(dim.toFloat())
                        }, NOT_CONSTRUCTABLE))
                    }
                }
            }

            // @Hint by SuperMonster003 on Sep 28, 2024.
            //  ! Replaced by background, backgroundColor and backgroundColorRes in properties.
            //  ! zh-CN: 已被 properties 中的 background, backgroundColor 和 backgroundColorRes 替代.
            //  # if (!properties.prop( "background").isJsNullish()) {
            //  #     dialog.window?.let { win ->
            //  #         UI.postRhinoRuntime(scriptRuntime, newBaseFunction("action", {
            //  #             when (val background = properties.prop( "background")) {
            //  #                 is String -> {
            //  #                     val bg = background.trim()
            //  #                     if (bg.startsWith("#")) {
            //  #                         win.setBackgroundDrawable(ColorDrawable(Colors.toIntRhino(bg)))
            //  #                     } else {
            //  #                         val colorRes = android.R.color::class.java.getField(bg).getInt(null)
            //  #                         win.setBackgroundDrawableResource(colorRes)
            //  #                     }
            //  #                 }
            //  #                 is Number -> win.setBackgroundDrawable(ColorDrawable(coerceIntNumber(background)))
            //  #                 else -> throw IllegalArgumentException("Unknown type of background property: ${background.jsBrief()}")
            //  #             }
            //  #         }, NOT_CONSTRUCTABLE))
            //  #     }
            //  # }

            if (!properties.prop("animation").isJsNullish() && Context.toBoolean(properties.prop("animation"))) {
                val animationRaw = when (properties.prop("animation")) {
                    true -> "default"
                    else -> properties.inquire("animation", ::coerceString, "default")
                }
                val animationStyle = animationMappings.firstOrNull {
                    it.first.looseMatches(animationRaw)
                }?.second ?: throw IllegalArgumentException(
                    "Unknown property animation ${animationRaw.jsBrief()} of argument properties for dialogs.build",
                )
                dialog.window?.let { win ->
                    UI.postRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                        win.setWindowAnimations(animationStyle)
                    }, NOT_CONSTRUCTABLE))
                }
            }

            if (properties.inquire("keepScreenOn", ::coerceBoolean, false)) {
                dialog.window?.let { win ->
                    UI.postRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                        win.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }, NOT_CONSTRUCTABLE))
                }
            }

            properties.prop("inputSingleLine").let { inputSingleLine ->
                if (inputSingleLine is Boolean) {
                    dialog.inputEditText?.isSingleLine = inputSingleLine
                }
            }
        }

        private fun invokeJavaMethod(builder: JsDialogBuilder, key: String, argsForMethod: Array<out Any?>) {
            val methods = builder.javaClass.methods
            val targetMethodsForName = methods.filter {
                it.name == key
            }
            require(targetMethodsForName.isNotEmpty()) {
                "Method key \"$key\" for dialogs.build is not a valid method name"
            }
            val targetMethodsForParamCount = targetMethodsForName.filter {
                it.parameterCount == argsForMethod.size
            }
            require(targetMethodsForParamCount.isNotEmpty()) {
                val expectedLengthList = targetMethodsForName.map { it.parameterCount }.distinct().sorted()
                "Method key \"$key\" for dialogs.build should have arguments length of ${expectedLengthList.joinToString(" or ")} instead of ${argsForMethod.size}"
            }
            val invokedResult = targetMethodsForParamCount.any { targetMethod ->
                runCatching {
                    targetMethod.isAccessible = true
                    val paramTypes = targetMethod.parameterTypes
                    targetMethod.invoke(builder, *argsForMethod.mapIndexed { index: Int, obj: Any? ->
                        val targetType = paramTypes[index]
                        when (val o = obj.jsUnwrapped()) {
                            null -> {
                                when (targetType) {
                                    String::class.java -> ""
                                    CharSequence::class.java -> ""
                                    else -> null
                                }
                            }
                            is Double -> when (targetType) {
                                java.lang.Byte.TYPE -> o.toInt().toByte()
                                java.lang.Short.TYPE -> o.toInt().toShort()
                                Integer.TYPE -> o.toInt()
                                java.lang.Long.TYPE -> o.toLong()
                                java.lang.Float.TYPE -> o.toFloat()
                                java.lang.Double.TYPE -> o
                                else -> o
                            }
                            is Float -> when (targetType) {
                                java.lang.Byte.TYPE -> o.toInt().toByte()
                                java.lang.Short.TYPE -> o.toInt().toShort()
                                Integer.TYPE -> o.toInt()
                                java.lang.Long.TYPE -> o.toLong()
                                java.lang.Float.TYPE -> o
                                java.lang.Double.TYPE -> o.toDouble()
                                else -> o
                            }
                            is Long -> when (targetType) {
                                java.lang.Byte.TYPE -> o.toByte()
                                java.lang.Short.TYPE -> o.toShort()
                                Integer.TYPE -> o.toInt()
                                java.lang.Long.TYPE -> o
                                java.lang.Float.TYPE -> o.toFloat()
                                java.lang.Double.TYPE -> o.toDouble()
                                else -> o
                            }
                            is Int -> when (targetType) {
                                java.lang.Byte.TYPE -> o.toByte()
                                java.lang.Short.TYPE -> o.toShort()
                                Integer.TYPE -> o
                                java.lang.Long.TYPE -> o.toLong()
                                java.lang.Float.TYPE -> o.toFloat()
                                java.lang.Double.TYPE -> o.toDouble()
                                else -> o
                            }
                            is Short -> when (targetType) {
                                java.lang.Byte.TYPE -> o.toByte()
                                java.lang.Short.TYPE -> o
                                Integer.TYPE -> o.toInt()
                                java.lang.Long.TYPE -> o.toLong()
                                java.lang.Float.TYPE -> o.toFloat()
                                java.lang.Double.TYPE -> o.toDouble()
                                else -> o
                            }
                            is Byte -> when (targetType) {
                                java.lang.Byte.TYPE -> o
                                java.lang.Short.TYPE -> o.toShort()
                                Integer.TYPE -> o.toInt()
                                java.lang.Long.TYPE -> o.toLong()
                                java.lang.Float.TYPE -> o.toFloat()
                                java.lang.Double.TYPE -> o.toDouble()
                                else -> o
                            }
                            else -> o
                        }
                    }.toTypedArray())
                }.isSuccess
            }
            require(invokedResult) {
                val expectedTypeList = targetMethodsForParamCount.map {
                    "(" + it.parameterTypes.joinToString(", ") { paramType -> paramType.simpleName } + ")"
                }
                val actualTypeList = argsForMethod.joinToString(", ") {
                    it?.javaClass?.simpleName ?: "null"
                }
                "Failed to invoke method \"$key\" for dialogs.build: Method key \"$key\" for dialogs.build should have argument type ${expectedTypeList.joinToString(" or ")} instead of ($actualTypeList)"
            }
        }

    }

}
