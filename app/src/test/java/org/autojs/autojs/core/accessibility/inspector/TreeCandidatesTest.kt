package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Roadmap P3.2 / D16: the picker candidates come from the tree alone. zh-CN: Roadmap P3.2 / D16: 选择器候选只来自树本身.
 */
class TreeCandidatesTest {

    @Test
    fun collectsDistinctSortedPackagesActionsAndCountedClassNames() {
        val root = FakeUiNode.root("root") {
            className = "android.widget.FrameLayout"
            packageName = "com.b"
            actions = listOf("ACTION_SELECT")
            child("a") { className = "android.widget.TextView"; packageName = "com.a"; actions = listOf("ACTION_CLICK", "ACTION_SELECT") }
            child("b") { className = "androidx.appcompat.widget.AppCompatTextView"; packageName = "com.a" }
            child("c") { className = "android.widget.FrameLayout"; packageName = " "; actions = listOf("ACTION_CLICK") }
            child("d") { className = null; packageName = null }
        }
        val c = TreeCandidates.collect(root)
        assertEquals(listOf("com.a", "com.b"), c.packages)
        assertEquals(listOf("ACTION_CLICK", "ACTION_SELECT"), c.actions)
        assertEquals(listOf("FrameLayout" to 2, "AppCompatTextView" to 1, "TextView" to 1), c.classNames)
        assertEquals(5, c.nodeCount)
    }

}
