package org.autojs.autojs.core.activity

import org.junit.Assert.*
import org.junit.Test

class ActivityWindowIdentityTest {
    private val declared: (String, String) -> Boolean = { pkg, name -> name == "$pkg.Main" || name == "$pkg.Second" }
    @Test fun containerAndDialogEventsDoNotReplaceTheActivity() {
        val main = ActivityWindowIdentity().observe("test", "test.Main", declared)
        assertEquals("test.Main", main.activityName)
        assertEquals(main, main.observe("test", "android.widget.FrameLayout", declared))
        assertEquals(main, main.observe("test", "android.app.Dialog", declared))
        assertEquals("test.Second", main.observe("test", "test.Second", declared).activityName)
    }
    @Test fun aNewPackageCannotBorrowThePreviousActivity() {
        val main = ActivityWindowIdentity().observe("first", "first.Main", declared)
        val other = main.observe("other", "android.widget.FrameLayout", declared)
        assertEquals("other", other.packageName); assertEquals("", other.activityName)
        assertEquals("other.Main", other.observe("other", "other.Main", declared).activityName)
    }
    @Test fun missingClassesDoNotTriggerPackageManagerQueries() {
        val value = ActivityWindowIdentity().observe("test", "", { _, _ -> error("No class") })
        assertEquals("test", value.packageName); assertEquals("", value.activityName)
    }
    @Test fun aLateToastFromAnUnresolvedWindowCannotUndoTheForegroundTransition() {
        val foreground = ActivityWindowIdentity().observe("first", "first.Main", declared)
        assertEquals(foreground, foreground.observe("toast", "android.widget.FrameLayout", declared, windowKnown = false))
        assertEquals("second.Main", foreground.observe("second", "second.Main", declared, windowKnown = false).activityName)
        assertEquals("toast", foreground.observe("toast", "android.widget.FrameLayout", declared, windowKnown = true).packageName)
    }
    @Test fun freshlyBoundServicesCanUseTheVisibleWindowAndOwnFocusedActivity() {
        assertEquals(ActivityWindowIdentity("app", ""), ActivityWindowIdentity().foreground("app"))
        val owned = ActivityWindowIdentity("app", "app.Main")
        assertEquals(owned, ActivityWindowIdentity().foreground("app", owned))
        assertEquals(owned, ActivityWindowIdentity().foreground(null, owned))
        assertEquals(ActivityWindowIdentity("other", ""), owned.foreground("other"))
        assertEquals(owned, owned.foreground("app"))
    }
}
