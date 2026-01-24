package org.autojs.autojs.util

import org.autojs.autojs.util.StringUtils.looseKey

object IterableUtils {

    @JvmStatic
    fun Iterable<String>.containsLoosely(value: String): Boolean {
        val key = value.looseKey()
        return any { it.looseKey() == key }
    }

}