package org.autojs.autojs.statics

import org.junit.Test
import org.mozilla.javascript.Context

/**
 * Created by Stardust on May 13, 2017.
 */
class RhinoE4XTest {
    @Test
    @Suppress("DEPRECATION")
    fun testAttributeName() {
        val context = Context.enter()
        val scriptable = context.initStandardObjects()
        context.optimizationLevel = -1
        val o = context.evaluateString(scriptable, "XML.ignoreProcessingInstructions = true; (<xml id=\"foo\"></xml>).attributes()[0].name()", "<e4x>", 1, null)
        println(o)
    }
}
