package org.autojs.autojs.runtime.api.augment.ui

import android.view.View
import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableExtensions.hasProp
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

@Suppress("unused", "UNUSED_PARAMETER")
object UIWidget : FlexibleArray() {

    @JvmStatic
    @RhinoStandardFunctionInterface
    fun renderInternal(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any = ensureArgumentsIsEmpty(args) {
        when (val renderFunc = thisObj.prop("render")) {
            is BaseFunction -> renderFunc.call(cx, thisObj.parentScope ?: thisObj, thisObj, arrayOf(*args))
                ?: throw WrappedIllegalArgumentException("Function render must return a non-null value")
            else -> "< />"
        }
    }

    @JvmStatic
    @RhinoStandardFunctionInterface
    fun defineAttr(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsLengthInRange(args, 1..3) { argList ->
        val (arg0, arg1, arg2) = argList
        require(!arg0.isJsNullish()) {
            str(R.string.error_argument_name_for_class_name_and_member_func_name_cannot_be_nullish, "attrName", UIWidget::class.java.simpleName, ::defineAttr.name)
        }
        val attrName = Context.toString(arg0)
        var attrAlias = attrName
        var applier: Any? = null
        if (arg1 is String) {
            attrAlias = arg1
            if (argList.size >= 3) {
                applier = arg2
            }
        } else if (arg1 is BaseFunction && arg2 !is BaseFunction) {
            applier = arg1
        }
        val (getter, setter) = when {
            arg1 is BaseFunction && arg2 is BaseFunction -> arg1 to arg2
            else -> newBaseFunction(
                "getter", {
                    thisObj.prop(attrAlias)
                }, NOT_CONSTRUCTABLE,
            ) to newBaseFunction(
                "setter", { setterArgList ->
                    val (_, _, value, _) = setterArgList
                    thisObj.defineProp(attrAlias, value)
                    if (applier is BaseFunction) applier.call(cx, thisObj, thisObj, setterArgList)
                }, NOT_CONSTRUCTABLE,
            )
        }
        val attrsObj = thisObj.prop("__attrs__") as NativeObject
        val attrValue = newNativeObject().also {
            it.defineProp("getter", getter)
            it.defineProp("setter", setter)
        }
        attrsObj.defineProp(attrName, attrValue)
    }

    @JvmStatic
    @RhinoStandardFunctionInterface
    fun hasAttr(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Boolean = ensureArgumentsAtMost(args, 1) { argList ->
        val (attrName) = argList
        getAttrsObject(thisObj).hasProp(coerceString(attrName))
    }

    @JvmStatic
    @RhinoStandardFunctionInterface
    fun setAttr(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = unwrapArguments(args) {
        var (view, attrName, value, setter) = args
        attrName = coerceString(attrName)
        val attrsObject = getAttrsObject(thisObj)
        val attrValue = attrsObject[attrName]
        require(attrValue is ScriptableObject) {
            "Property ui.Widget#__attrs__[\"${attrName}\"] must be a ScriptableObject instead of ${attrValue.jsBrief()}"
        }
        val setterFunc = attrValue.prop("setter")
        require(setterFunc is BaseFunction) {
            "Property ui.Widget#__attrs__[\"${attrName}\"][\"setter\"] must be a JavaScript Function instead of ${setterFunc.jsBrief()}"
        }
        setterFunc.call(cx, attrsObject, attrValue, arrayOf(view, attrName, value, setter))
        UNDEFINED
    }

    @JvmStatic
    @RhinoStandardFunctionInterface
    fun getAttr(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any? = unwrapArguments(args) {
        var (view, attrName, getter) = it
        attrName = coerceString(attrName)
        val attrsObject = getAttrsObject(thisObj)
        val attrValue = attrsObject[attrName]
        require(attrValue is ScriptableObject) {
            "Property ui.Widget#__attrs__[\"${attrName}\"] must be a ScriptableObject instead of ${attrValue.jsBrief()}"
        }
        val getterFunc = attrValue.prop("getter")
        require(getterFunc is BaseFunction) {
            "Property ui.Widget#__attrs__[\"${attrName}\"][\"getter\"] must be a JavaScript Function instead of ${getterFunc.jsBrief()}"
        }
        getterFunc.call(cx, attrsObject, attrValue, arrayOf(view, attrName, getter))
    }

    @JvmStatic
    @RhinoStandardFunctionInterface
    fun notifyViewCreated(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsOnlyOne(args) { view ->
        require(view is View) {
            "Argument view for ui.Widget#notifyViewCreated must be a View instead of ${view.jsBrief()}"
        }
        val onViewCreatedFunc = thisObj.prop("onViewCreated")
        if (onViewCreatedFunc is BaseFunction) {
            onViewCreatedFunc.call(cx, thisObj, thisObj, arrayOf(view))
        }
        UNDEFINED
    }

    @JvmStatic
    @RhinoStandardFunctionInterface
    fun notifyAfterInflation(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsOnlyOne(args) { view ->
        require(view is View) {
            "Argument view for ui.Widget#notifyAfterInflation must be a View instead of ${view.jsBrief()}"
        }
        val onFinishInflationFunc = thisObj.prop("onFinishInflation")
        if (onFinishInflationFunc is BaseFunction) {
            onFinishInflationFunc.call(cx, thisObj, thisObj, arrayOf(view))
        }
        UNDEFINED
    }

    private fun getAttrsObject(thisObj: Scriptable) = thisObj.prop("__attrs__").also { attrsObject ->
        require(attrsObject is NativeObject) {
            "Argument __attrs__ for instance of ui.Widget must be a NativeObject instead of ${attrsObject.jsBrief()}"
        }
    } as NativeObject

}