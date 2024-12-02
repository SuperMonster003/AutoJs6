package org.autojs.autojs.runtime.api

import org.autojs.autojs.runtime.ScriptRuntime

@Suppress("UNUSED_PARAMETER")
class Scale(scriptRuntime: ScriptRuntime) {

    private var privateBaseX = 720
    private var privateBaseY = 1280

    private var baseXState = false
    private var baseYState = false

    var baseX: Int
        get() = privateBaseX
        set(value) {
            ensureBase(value)
            privateBaseX = value
            ensureBaseXSetOnlyOnce()
            baseXState = true
        }

    var baseY: Int
        get() = privateBaseY
        set(value) {
            ensureBase(value)
            privateBaseY = value
            ensureBaseYSetOnlyOnce()
            baseYState = true
        }

    private fun ensureBase(o: Int) {
        require(o > 0) { "Scale base \"$o\" must be a positive integer" }
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

}
