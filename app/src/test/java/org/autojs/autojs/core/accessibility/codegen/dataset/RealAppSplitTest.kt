package org.autojs.autojs.core.accessibility.codegen.dataset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RealAppSplitTest {
    @Test
    fun matchesThePythonSplitOfTheDatasetTools() {
        // Computed with tools/codegen-model/dataset/split.py real_app_split (roadmap D25).
        val expected = linkedMapOf(
            "com.android.vending" to "real-test", "com.aurora.store" to "real-train", "com.fastaccess.github" to "real-train",
            "com.trainnote.rn" to "real-train", "com.tencent.mm" to "real-test", "com.android.settings" to "real-train",
            "org.mozilla.firefox" to "real-train", "com.google.android.apps.maps" to "real-test",
            "real.app0" to "real-train", "real.app1" to "real-train", "real.app2" to "real-test", "real.app3" to "real-test",
            "real.app4" to "real-train", "real.app5" to "real-test", "real.app6" to "real-train", "real.app7" to "real-train",
            "real.app8" to "real-train", "real.app9" to "real-test", "real.app10" to "real-train", "real.app11" to "real-train",
        )
        assertEquals(expected, expected.keys.associateWith(RealAppSplit::split))
        assertThrows(IllegalArgumentException::class.java) { RealAppSplit.split("") }
    }
}
