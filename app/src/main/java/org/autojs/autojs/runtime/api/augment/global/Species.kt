package org.autojs.autojs.runtime.api.augment.global

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import java.math.BigInteger

@Suppress("unused")
object Species : Augmentable(), Invokable {

    override val selfAssignmentFunctions = listOf(
        ::isArray.name,
        ::isArrayBuffer.name,
        ::isBigInt.name,
        ::isBoolean.name,
        ::isContinuation.name,
        ::isDataView.name,
        ::isDate.name,
        ::isError.name,
        ::isFloat32Array.name,
        ::isFloat64Array.name,
        ::isFunction.name,
        ::isInt16Array.name,
        ::isInt32Array.name,
        ::isInt8Array.name,
        ::isJavaObject.name,
        ::isJavaClass.name to AS_GLOBAL,
        ::isJavaPackage.name to AS_GLOBAL,
        ::isMap.name,
        ::isNamespace.name,
        ::isNull.name,
        ::isNumber.name,
        ::isObject.name to AS_GLOBAL,
        ::isQName.name,
        ::isRegExp.name,
        ::isSet.name,
        ::isString.name,
        ::isUint16Array.name,
        ::isUint32Array.name,
        ::isUint8Array.name,
        ::isUint8ClampedArray.name,
        ::isUndefined.name,
        ::isWeakMap.name,
        ::isWeakSet.name,
        ::isXML.name,
        ::isXMLList.name,
    )

    override fun invoke(vararg args: Any?): String = ensureArgumentsOnlyOne(args, true) {
        speciesRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun speciesRhino(o: Any?): String = runCatching {
        when {
            o == null -> "Null"
            Undefined.isUndefined(o) -> "Undefined"
            else -> when (val obj = withRhinoContext { cx -> Context.javaToJS(o, cx.initStandardObjects()) }) {
                is Boolean -> "Boolean"
                is String -> "String"
                is BigInteger -> "BigInt"
                is Number -> "Number"
                is Scriptable -> obj.className
                else -> "Unknown"
            }
        }
    }.getOrDefault("Unknown")

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isArray(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isArrayRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isArrayRhino(o: Any?) = speciesRhino(o) == "Array"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isArrayBuffer(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isArrayBufferRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isArrayBufferRhino(o: Any?) = speciesRhino(o) == "ArrayBuffer"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isBigInt(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isBigIntRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isBigIntRhino(o: Any?) = speciesRhino(o) == "BigInt"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isBoolean(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isBooleanRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isBooleanRhino(o: Any?) = speciesRhino(o) == "Boolean"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isContinuation(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isContinuationRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isContinuationRhino(o: Any?) = speciesRhino(o) == "Continuation"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isDataView(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isDataViewRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isDataViewRhino(o: Any?) = speciesRhino(o) == "DataView"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isDate(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isDateRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isDateRhino(o: Any?) = speciesRhino(o) == "Date"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isError(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isErrorRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isErrorRhino(o: Any?) = speciesRhino(o) == "Error"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isFloat32Array(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isFloat32ArrayRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isFloat32ArrayRhino(o: Any?) = speciesRhino(o) == "Float32Array"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isFloat64Array(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isFloat64ArrayRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isFloat64ArrayRhino(o: Any?) = speciesRhino(o) == "Float64Array"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isFunction(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isFunctionRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isFunctionRhino(o: Any?) = speciesRhino(o) == "Function"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isInt16Array(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isInt16ArrayRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isInt16ArrayRhino(o: Any?) = speciesRhino(o) == "Int16Array"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isInt32Array(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isInt32ArrayRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isInt32ArrayRhino(o: Any?) = speciesRhino(o) == "Int32Array"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isInt8Array(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isInt8ArrayRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isInt8ArrayRhino(o: Any?) = speciesRhino(o) == "Int8Array"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isJavaObject(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isJavaObjectRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isJavaObjectRhino(o: Any?) = speciesRhino(o) == "JavaObject"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isJavaClass(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isJavaClassRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isJavaClassRhino(o: Any?) = speciesRhino(o) == "JavaClass"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isJavaPackage(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isJavaPackageRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isJavaPackageRhino(o: Any?) = speciesRhino(o) == "JavaPackage"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isMap(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isMapRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isMapRhino(o: Any?) = speciesRhino(o) == "Map"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isNamespace(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isNamespaceRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isNamespaceRhino(o: Any?) = speciesRhino(o) == "Namespace"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isNull(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isNullRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isNullRhino(o: Any?) = speciesRhino(o) == "Null"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isNumber(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isNumberRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isNumberRhino(o: Any?) = speciesRhino(o) == "Number"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isObject(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isObjectRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isObjectRhino(o: Any?) = speciesRhino(o) == "Object"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isQName(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isQNameRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isQNameRhino(o: Any?) = speciesRhino(o) == "QName"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isRegExp(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isRegExpRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isRegExpRhino(o: Any?) = speciesRhino(o) == "RegExp"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isSet(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isSetRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isSetRhino(o: Any?) = speciesRhino(o) == "Set"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isString(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isStringRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isStringRhino(o: Any?) = speciesRhino(o) == "String"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isUint16Array(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isUint16ArrayRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isUint16ArrayRhino(o: Any?) = speciesRhino(o) == "Uint16Array"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isUint32Array(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isUint32ArrayRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isUint32ArrayRhino(o: Any?) = speciesRhino(o) == "Uint32Array"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isUint8Array(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isUint8ArrayRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isUint8ArrayRhino(o: Any?) = speciesRhino(o) == "Uint8Array"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isUint8ClampedArray(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isUint8ClampedArrayRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isUint8ClampedArrayRhino(o: Any?) = speciesRhino(o) == "Uint8ClampedArray"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isUndefined(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isUndefinedRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isUndefinedRhino(o: Any?) = speciesRhino(o) == "Undefined"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isWeakMap(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isWeakMapRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isWeakMapRhino(o: Any?) = speciesRhino(o) == "WeakMap"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isWeakSet(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isWeakSetRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isWeakSetRhino(o: Any?) = speciesRhino(o) == "WeakSet"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isXML(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isXMLRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isXMLRhino(o: Any?) = speciesRhino(o) == "XML"

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isXMLList(args: Array<out Any?>) = ensureArgumentsOnlyOne(args, true) {
        isXMLListRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isXMLListRhino(o: Any?) = speciesRhino(o) == "XMLList"

}
