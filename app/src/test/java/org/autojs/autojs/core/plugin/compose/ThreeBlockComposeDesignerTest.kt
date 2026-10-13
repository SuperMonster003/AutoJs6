package org.autojs.autojs.core.plugin.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** The designer hand-over is offered from plugin 1.2.0 on, and a document is "changed" when its bytes differ. */
class ThreeBlockComposeDesignerTest {

    @Test
    fun designerShipsWithPluginOneTwo() {
        assertTrue(ThreeBlockComposeDesigner.isAtLeast("1.2.0", ThreeBlockComposeDesigner.MIN_VERSION_NAME))
        assertTrue(ThreeBlockComposeDesigner.isAtLeast("1.2.1", ThreeBlockComposeDesigner.MIN_VERSION_NAME))
        assertTrue(ThreeBlockComposeDesigner.isAtLeast("1.10.0", ThreeBlockComposeDesigner.MIN_VERSION_NAME))
        assertTrue(ThreeBlockComposeDesigner.isAtLeast("2.0", ThreeBlockComposeDesigner.MIN_VERSION_NAME))
        assertTrue(ThreeBlockComposeDesigner.isAtLeast("1.2.0-beta+104", ThreeBlockComposeDesigner.MIN_VERSION_NAME))
        assertFalse(ThreeBlockComposeDesigner.isAtLeast("1.1.0", ThreeBlockComposeDesigner.MIN_VERSION_NAME))
        assertFalse(ThreeBlockComposeDesigner.isAtLeast("1.1.9", ThreeBlockComposeDesigner.MIN_VERSION_NAME))
        assertFalse(ThreeBlockComposeDesigner.isAtLeast("1.0.0", ThreeBlockComposeDesigner.MIN_VERSION_NAME))
        assertFalse(ThreeBlockComposeDesigner.isAtLeast("1", ThreeBlockComposeDesigner.MIN_VERSION_NAME))
        assertFalse(ThreeBlockComposeDesigner.isAtLeast("", ThreeBlockComposeDesigner.MIN_VERSION_NAME))
    }

    @Test
    fun fingerprintFollowsTheBytes() {
        val a = ThreeBlockComposeDesigner.fingerprint(ByteArrayInputStream("compose.mount(compose.Text({text: 'a'}));\n".toByteArray()))
        val same = ThreeBlockComposeDesigner.fingerprint(ByteArrayInputStream("compose.mount(compose.Text({text: 'a'}));\n".toByteArray()))
        val b = ThreeBlockComposeDesigner.fingerprint(ByteArrayInputStream("compose.mount(compose.Text({text: 'b'}));\n".toByteArray()))
        assertEquals(a, same)
        assertTrue(a != b)
        assertEquals(64, a!!.length)
        assertNull(ThreeBlockComposeDesigner.fingerprint(null))
    }
}
