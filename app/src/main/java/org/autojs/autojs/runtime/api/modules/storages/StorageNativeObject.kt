package org.autojs.autojs.runtime.api.modules.storages

import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.core.storage.LocalStorage
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.js_json_parse
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

@Suppress("unused")
class StorageNativeObject(@JvmField val name: String) : NativeObject(), StringReadable {

    private val storage = LocalStorage(name)

    private val mFunctionNames = arrayOf(
        Getter::get.name,
        Getter::put.name,
        Getter::putSync.name,
        Getter::remove.name,
        Getter::removeSync.name,
        Getter::contains.name,
        Getter::clear.name,
        Getter::clearSync.name,
        Getter::selfRemove.name,
        Getter::selfRemoveSync.name,
    )

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineProperty("name", name, PERMANENT)
        defineProperty("size", { storage.size() }, null, PERMANENT)
        RhinoUtils.defineFunctionProperties(this, mFunctionNames, Getter::class.java, PERMANENT)
    }

    override fun toStringReadable() = "Storage { name: $name, size: ${storage.size()} }"

    @Suppress("UNUSED_PARAMETER")
    internal object Getter : ArgumentGuards() {

        private val className = StorageNativeObject::class.java.simpleName

        private fun <R> requireInstanceForBlock(thisObj: Scriptable, funcName: String, block: (sto: LocalStorage) -> R): R {
            require(thisObj is StorageNativeObject) { "Argument \"thisObj\" for $className.$funcName must be an instance of $className instead of ${thisObj.jsBrief()}" }
            return block(thisObj.storage)
        }

        private fun requireInstanceForSelf(thisObj: Scriptable, funcName: String, block: (sto: LocalStorage) -> Any?): StorageNativeObject {
            requireInstanceForBlock(thisObj, funcName, block)
            return thisObj as StorageNativeObject
        }

        private fun requireNotUndefined(value: Any?, funcName: String) {
            require(!Undefined.isUndefined(value)) { "Value cannot be undefined for Storage#$funcName" }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun get(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any? = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            var (key, def) = argList
            if (argList.size == 1) def = UNDEFINED
            requireInstanceForBlock(thisObj, ::get.name) { sto -> parseStoredValue(sto.getString(coerceString(key), null), def) }
        }

        internal fun parseStoredValue(serialized: String?, defaultValue: Any?): Any? {
            return if (serialized == null) defaultValue else js_json_parse(serialized)
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun put(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): StorageNativeObject = ensureArgumentsLength(args, 2) { argList ->
            val (key, value) = argList
            requireNotUndefined(value, ::put.name)
            requireInstanceForSelf(thisObj, ::put.name) { sto -> sto.put(coerceString(key), Context.toString(js_json_stringify(value))) }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun putSync(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): StorageNativeObject = ensureArgumentsLength(args, 2) { argList ->
            val (key, value) = argList
            requireNotUndefined(value, ::putSync.name)
            requireInstanceForSelf(thisObj, ::putSync.name) { sto -> sto.putSync(coerceString(key), Context.toString(js_json_stringify(value))) }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun remove(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): StorageNativeObject = ensureArgumentsOnlyOne(args) { key ->
            requireInstanceForSelf(thisObj, ::remove.name) { sto -> sto.remove(coerceString(key)) }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun removeSync(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): StorageNativeObject = ensureArgumentsOnlyOne(args) { key ->
            requireInstanceForSelf(thisObj, ::removeSync.name) { sto -> sto.removeSync(coerceString(key)) }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun contains(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Boolean = ensureArgumentsOnlyOne(args) { key ->
            requireInstanceForBlock(thisObj, ::contains.name) { sto -> sto.contains(coerceString(key)) }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun clear(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsIsEmpty(args) {
            requireInstanceForBlock(thisObj, ::clear.name) { sto -> sto.clear() }
            UNDEFINED
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun clearSync(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsIsEmpty(args) {
            requireInstanceForBlock(thisObj, ::clearSync.name) { sto -> sto.clearSync() }
            UNDEFINED
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun selfRemove(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): StorageNativeObject = ensureArgumentsIsEmpty(args) {
            requireInstanceForSelf(thisObj, ::selfRemove.name) {
                Storages.removeRhino((thisObj as StorageNativeObject).name)
            }
        }

        @JvmStatic
        @RhinoStandardFunctionInterface
        fun selfRemoveSync(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): StorageNativeObject = ensureArgumentsIsEmpty(args) {
            requireInstanceForSelf(thisObj, ::selfRemoveSync.name) {
                Storages.removeSyncRhino((thisObj as StorageNativeObject).name)
            }
        }

    }

}
