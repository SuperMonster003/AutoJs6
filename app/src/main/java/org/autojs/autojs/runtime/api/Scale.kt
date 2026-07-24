package org.autojs.autojs.runtime.api

import org.autojs.autojs.runtime.ScriptRuntime

class Scale @JvmOverloads constructor(@Suppress("UNUSED_PARAMETER") scriptRuntime: ScriptRuntime? = null) {

    private var privateBaseX = 720
    private var privateBaseY = 1280

    private var baseXState = false
    private var baseYState = false

    var baseX: Int
        get() = privateBaseX
        set(value) {
            ensureBase(value)
            ensureBaseXSetOnlyOnce()
            privateBaseX = value
            baseXState = true
        }

    var baseY: Int
        get() = privateBaseY
        set(value) {
            ensureBase(value)
            ensureBaseYSetOnlyOnce()
            privateBaseY = value
            baseYState = true
        }

    private fun ensureBaseXSetOnlyOnce() {
        require(!baseXState) { "Scale base X could be set only once, $baseX has been set as the base" }
    }

    private fun ensureBaseYSetOnlyOnce() {
        require(!baseYState) { "Scale base Y could be set only once, $baseY has been set as the base" }
    }

    fun ensureBasesConsistent() {
        require(baseXState == baseYState) { "Scale bases must be consistent, { x: ${baseXState}, y: $baseYState }" }
    }

    companion object {

        @JvmStatic
        fun ensureBase(base: Int): Int {
            require(base > 0) { "Scale base \"$base\" must be a positive integer" }
            return base
        }

    }

}
