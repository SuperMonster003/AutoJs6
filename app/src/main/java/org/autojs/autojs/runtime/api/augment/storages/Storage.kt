package org.autojs.autojs.runtime.api.augment.storages

import org.autojs.autojs.core.storage.LocalStorage
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.js_json_parse
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined

class Storage(@JvmField val name: String) {

    private val mStorage = LocalStorage(name)

    fun put(key: Any?, value: Any?): Storage = also {
        require(!Undefined.isUndefined(value)) { "Value cannot be undefined for Storage#put" }
        mStorage.put(coerceString(key), Context.toString(js_json_stringify(value)))
    }

    fun putSync(key: Any?, value: Any?): Storage = also {
        require(!Undefined.isUndefined(value)) { "Value cannot be undefined for Storage#putSync" }
        mStorage.putSync(coerceString(key), Context.toString(js_json_stringify(value)))
    }

    @JvmOverloads
    fun get(key: Any?, def: Any? = UNDEFINED): Any? = mStorage.getString(coerceString(key), null)
        ?.let { js_json_parse(it) } ?: def

    fun remove(key: Any?) = also { mStorage.remove(coerceString(key)) }

    fun removeSync(key: Any?) = also { mStorage.removeSync(coerceString(key)) }

    fun contains(key: Any?) = mStorage.contains(coerceString(key))

    fun clear() = mStorage.clear()

    fun clearSync() = mStorage.clearSync()

    fun selfRemove() = Storages.removeRhino(name)

    fun selfRemoveSync() = Storages.removeSyncRhino(name)

}
