package org.autojs.autojs.runtime.api.modules.colors

import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException

/**
 * Created by SuperMonster003 on May 26, 2024.
 */
object Color : ArgumentGuards() {

    fun invoke(vararg args: Any?): ColorNativeObject = ensureArgumentsLengthInRange(args, 0..4) {
        when (it.size) {
            0 -> ColorNativeObject()
            1 -> ColorNativeObject(it[0])
            3 -> ColorNativeObject(it[0], it[1], it[2])
            4 -> ColorNativeObject(it[0], it[1], it[2], it[3])
            else -> throw WrappedIllegalArgumentException("Invalid arguments length ${it.size} for Color")
        }
    }

}
