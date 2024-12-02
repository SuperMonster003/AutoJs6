package org.autojs.autojs.runtime.api.augment.global

import org.autojs.autojs.annotation.AugmentableSimpleGetterProxyInterface
import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.SimpleGetterProxy
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Scriptable.NOT_FOUND
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties

@Suppress("unused")
object GlobalClasses : SimpleGetterProxy {

    private val mClasses by lazy { Classes() }
    private val mClassesMembers by lazy { Classes::class.declaredMemberProperties }

    @JvmStatic
    @AugmentableSimpleGetterProxyInterface
    fun get(scope: Scriptable, key: String): Any? {

        // @Alter by SuperMonster003 on May 29, 2024.
        //  # mClassesMembers.find { it.name == name }?.let {
        //  #     it.call(mClasses)?.let { cls ->
        //  #         when (cls) {
        //  #             is KClass<*> -> NativeJavaClass(thisObj, cls.java)
        //  #             is Class<*> -> NativeJavaClass(thisObj, cls)
        //  #             else -> NativeJavaClass(thisObj, cls::class.java)
        //  #         }
        //  #     }
        //  # } ?: NOT_FOUND

        return runCatching {
            val value = Classes::class.java.getDeclaredField(key).apply { isAccessible = true }.get(mClasses) ?: NOT_FOUND
            when (value) {
                is KClass<*> -> NativeJavaClass(scope, value.java)
                is Class<*> -> NativeJavaClass(scope, value)
                else -> NativeJavaClass(scope, value::class.java)
            }
        }.getOrDefault(NOT_FOUND)
    }

}