package org.autojs.autojs.runtime.api.modules.storages

import org.autojs.autojs.core.storage.LocalStorage
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.runtime.api.augment.storages.StorageNativeObject
import org.autojs.autojs.runtime.api.augment.storages.Storages
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.mozilla.javascript.NativeArray

/**
 * The `storages` module on the new script module layer (roadmap P3.4): the functions of the current module in its order;
 * `create` returns the current storage object (`StorageNativeObject`, its methods unchanged; to be moved out before P5.2).
 * A storage name is a string (other values by their string form, as before); a nullish one is a TypeError (D10; the
 * current module threw IllegalArgumentException).
 *
 * zh-CN: 新脚本模块层上的 storages 模块 (P3.4): 函数及顺序同现有模块; create 返回现有的存储对象 (StorageNativeObject, 其方法不变; P5.2 前迁出). 存储名为字符串
 * (其他值与以前一样按字符串形式); null / undefined 为 TypeError (D10; 现有模块抛 IllegalArgumentException).
 */
@ScriptModule(name = "storages")
class StoragesModule {

    @ScriptFunction
    fun create(name: String): StorageNativeObject = StorageNativeObject(name)

    @ScriptFunction
    fun remove(name: String) {
        Storages.removeRhino(name)
    }

    @ScriptFunction
    fun removeSync(name: String): Boolean = Storages.removeSyncRhino(name)

    @ScriptFunction
    fun all(): NativeArray = LocalStorage.getAllStorages().toNativeArray()

    @ScriptFunction
    fun names(): NativeArray = LocalStorage.getAllStorageNames().toNativeArray()

}
