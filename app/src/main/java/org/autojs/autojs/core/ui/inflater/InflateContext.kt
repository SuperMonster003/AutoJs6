package org.autojs.autojs.core.ui.inflater

class InflateContext {

    private val mProperties by lazy { HashMap<String, Any>() }

    fun put(key: String, value: Any) {
        mProperties[key] = value
    }

    fun get(key: String) = mProperties[key]

    fun remove(key: String) = mProperties.remove(key)

    fun has(key: String) = mProperties.containsKey(key)

}
