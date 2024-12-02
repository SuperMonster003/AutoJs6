package org.autojs.autojs.runtime.api.augment.storages

import android.content.Context.MODE_PRIVATE
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.core.storage.LocalStorage
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray

object Storages : Augmentable() {

    override val selfAssignmentFunctions = listOf(
        ::create.name,
        ::remove.name,
        ::removeSync.name,
        ::all.name,
        ::names.name,
    )

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun create(args: Array<out Any?>): StorageNativeObject = ensureArgumentsOnlyOne(args) {
        createRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun createRhino(name: Any?): StorageNativeObject = when {
        name.isJsNullish() -> throw WrappedIllegalArgumentException("Argument for storages.create cannot be nullish")
        else -> StorageNativeObject(Context.toString(name))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun remove(args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
        removeRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun removeRhino(name: Any?) = when {
        name.isJsNullish() -> throw WrappedIllegalArgumentException("Argument for storages.remove cannot be nullish")
        else -> LocalStorage.NAME_PREFIX + Context.toString(name)
    }.let { globalContext.getSharedPreferences(it, MODE_PRIVATE).edit().clear().apply() }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun removeSync(args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
        removeSyncRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun removeSyncRhino(name: Any?) = when {
        name.isJsNullish() -> throw WrappedIllegalArgumentException("Argument for storages.remove cannot be nullish")
        else -> LocalStorage.NAME_PREFIX + Context.toString(name)
    }.let { globalContext.getSharedPreferences(it, MODE_PRIVATE).edit().clear().commit() }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun all(args: Array<out Any?>): NativeArray = ensureArgumentsIsEmpty(args) {
        LocalStorage.getAllStorages().toNativeArray()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun names(args: Array<out Any?>): NativeArray = ensureArgumentsIsEmpty(args) {
        LocalStorage.getAllStorageNames().toNativeArray()
    }

}