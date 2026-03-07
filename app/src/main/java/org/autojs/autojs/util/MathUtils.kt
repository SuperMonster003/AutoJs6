package org.autojs.autojs.util

/**
 * Created by Stardust on Nov 26, 2017.
 * Transformed by SuperMonster003 on Mar 5, 2026.
 * Modified by SuperMonster003 as of Mar 5, 2026.
 */
object MathUtils {

    @JvmStatic
    fun min(vararg ints: Int): Int =
        ints.minOrNull() ?: throw NoSuchElementException("Array is empty")

    @JvmStatic
    fun Int.floorMod(mod: Int): Int =
        Math.floorMod(this, mod)

    @JvmStatic
    fun Long.floorMod(mod: Long): Long =
        Math.floorMod(this, mod)

    @JvmStatic
    fun Long.floorMod(mod: Int): Int =
        Math.floorMod(this, mod)
}
