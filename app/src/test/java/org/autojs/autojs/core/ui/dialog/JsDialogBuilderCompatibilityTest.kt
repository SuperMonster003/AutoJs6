package org.autojs.autojs.core.ui.dialog

import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class JsDialogBuilderCompatibilityTest {

    @Test
    fun legacyOnShowCalledMethodRemainsPublic() {
        val method = JsDialogBuilder::class.java.getDeclaredMethod("onShowCalled")

        assertTrue(Modifier.isPublic(method.modifiers))
    }

}
