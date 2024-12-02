package org.autojs.autojs.extension

import org.autojs.autojs.util.RhinoUtils.undefined
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/**
 * Created by SuperMonster003 on Sep 28, 2024.
 */
object ScriptableExtensions {

    /**
     * Gets a named property from an object or any object in its prototype chain.
     *
     * Searches the prototype chain for a property named `key`.
     *
     * @param key A property name
     * @return The value of a property with name `key` found in this object or any object in its prototype chain, or [Scriptable.NOT_FOUND] if not found
     */
    fun Scriptable.getProp(key: String): Any? = ScriptableObject.getProperty(this, key)

    /**
     * This method is a clone of [Scriptable.getProp].
     *
     * Gets a named property from an object or any object in its prototype chain.
     *
     * Searches the prototype chain for a property named `key`.
     *
     * @see Scriptable.getProp
     */
    fun Scriptable.prop(key: String): Any? = this.getProp(key)

    /**
     * Returns whether a named property is defined in this object or any object in its prototype chain.
     *
     * Searches the prototype chain for a property named `key`.
     *
     * @param key A property key
     * @return Whether property was found or not
     */
    fun Scriptable.hasProp(key: String): Boolean = ScriptableObject.hasProperty(this, key)

    /**
     * Utility method to add properties to this Scriptable object.
     *
     * If this object is instance of ScriptableObject, calls [ScriptableObject.defineProperty] there,
     * otherwise calls [Scriptable.put] in destination ignoring attributes.
     *
     * @param key The key of the property to define.
     * @param value The initial value of the property
     * @param attributes The attributes of the JavaScript property
     */
    fun Scriptable.defineProp(key: String, value: Any?, attributes: Int = ScriptableObject.EMPTY) = undefined { ScriptableObject.defineProperty(this, key, value, attributes) }

    /**
     * Puts a named property in this object or in an object in its prototype chain.
     *
     * Searches for the named property in the prototype chain.
     * If it is found, the value of the property in current object is changed
     * through a call to [Scriptable.put] on the prototype passing `this` as the `start` argument.
     * If the property is not found, it is added in current object.
     *
     * @param key A property key
     * @param value Any JavaScript value accepted by [Scriptable.put]
     */
    fun Scriptable.putProp(key: String, value: Any?) = undefined { ScriptableObject.putProperty(this, key, value) }

    /**
     * Removes the property from this object or its prototype chain.
     *
     * Searches for a property with `key` in current object or its prototype chain.
     * If it is found, this object's delete method is called.
     *
     * @param key A property key
     * @return If the property doesn't exist or was successfully removed
     */
    fun Scriptable.deleteProp(key: String): Boolean = ScriptableObject.deleteProperty(this, key)

}
