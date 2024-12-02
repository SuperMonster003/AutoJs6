package org.autojs.autojs.extension

import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R.string as R_string
import org.mozilla.javascript.ScriptableObject

/**
 * Created by SuperMonster003 on Jun 27, 2024.
 */
object ScriptableObjectExtensions {

    fun ScriptableObject.acquire(name: String): Any {
        val propValue = this.prop(name)
        require(!propValue.isJsNullish()) { str(R_string.error_required_property_is_nullish_or_does_not_exist, name) }
        return propValue!!
    }

    fun <R> ScriptableObject.acquire(name: String, transformer: (o: Any) -> R): R {
        val result = transformer(acquire(name))
        require(!result.isJsNullish()) { str(R_string.error_the_transformer_for_required_property_cannot_return_nullish, name) }
        return result!!
    }

    fun ScriptableObject.inquire(name: String, def: Any? = null): Any? {
        return this.prop(name).takeUnless { it.isJsNullish() } ?: def
    }

    fun <R> ScriptableObject.inquire(name: String, transformer: (o: Any) -> R?): R? {
        return this.prop(name).takeUnless { it.isJsNullish() }?.let { transformer(it) }
    }

    fun <R> ScriptableObject.inquire(name: String, transformer: (o: Any, defaultValue: R) -> R?, defaultValue: R): R {
        return this.prop(name).takeUnless { it.isJsNullish() }?.let { transformer(it, defaultValue) } ?: defaultValue
    }

    fun ScriptableObject.inquire(names: List<String>, def: Any? = null): Any? {
        names.forEach { name ->
            val value = this.prop(name)
            if (!value.isJsNullish()) {
                return value
            }
        }
        return def
    }

    fun <R> ScriptableObject.inquire(names: List<String>, transformer: (o: Any) -> R?): R? {
        names.forEach { name ->
            val value = this.prop(name)
            if (!value.isJsNullish()) {
                return transformer(value!!)
            }
        }
        return null
    }

    fun <R> ScriptableObject.inquire(names: List<String>, transformer: (o: Any, defaultValue: R) -> R?, defaultValue: R): R {
        names.forEach { name ->
            val value = this.prop(name)
            if (!value.isJsNullish()) {
                return transformer(value!!, defaultValue) ?: defaultValue
            }
        }
        return defaultValue
    }

}
