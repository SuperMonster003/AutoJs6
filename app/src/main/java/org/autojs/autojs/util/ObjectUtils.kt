package org.autojs.autojs.util

object ObjectUtils {

    @JvmStatic
    fun isEqual(o1: Any?, o2: Any?) = if (o1 == null) o2 == null else o1 == o2

}