package org.autojs.autojs.runtime.api

/**
 * Created by SuperMonster003 on Jun 13, 2024.
 */
interface StringReadable {

    fun toStringReadable(): String

    companion object {
        @JvmStatic
        val KEY = StringReadable::toStringReadable.name
    }

}
