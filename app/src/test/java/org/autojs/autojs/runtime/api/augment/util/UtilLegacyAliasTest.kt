package org.autojs.autojs.runtime.api.augment.util

import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

class UtilLegacyAliasTest {

    @Before
    fun enterContext() {
        Context.enter()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun legacyBigintSpellingIsExportedAlongsideCanonicalName() {
        val target = RhinoUtils.newNativeObject()

        Util.assign(target)

        val canonical = ScriptableObject.getProperty(target, "ensureBigIntType")
        val legacy = ScriptableObject.getProperty(target, "ensureBigintType")
        assertTrue(canonical is BaseFunction)
        assertTrue(legacy is BaseFunction)
        assertEquals(
            Undefined.instance,
            RhinoUtils.callFunction(legacy as BaseFunction, target, target, emptyArray()),
        )
    }

}
