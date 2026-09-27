package org.autojs.autojs.core.plugin.agent

import org.junit.Assert.*
import org.junit.Test

class ThreeStoveAgentScriptRootsTest {
    @Test fun normalizesOnlyRootsWithoutGrantFields() {
        assertEquals("""{"scriptRoots":["/sdcard/a","/sdcard/b"]}""", ThreeStoveAgentScriptRoots.configuration("""{"scriptRoots":["/sdcard/b/","/sdcard/a"]}"""))
        assertEquals("""{"scriptRoots":[]}""", ThreeStoveAgentScriptRoots.configuration("{}"))
    }
    @Test fun rejectsDuplicateKeysPathsTraversalAndAuthorityFields() {
        for (value in listOf("""{"scriptRoots":[],"scriptRoots":[]}""", """{"scriptRoots":["/sdcard/a","/sdcard/a/"]}""",
            """{"scriptRoots":["/sdcard/../data"]}""", """{"scriptRoots":[1]}""", """{"scriptRoots":["relative"]}""",
            """{"grantSummary":{}}""", """{"locale":"en"}""", """{} {}""", """{'scriptRoots':[]}""")) {
            assertThrows(value, Exception::class.java) { ThreeStoveAgentScriptRoots.configuration(value) }
        }
    }
    @Test fun enforcesControlAndRootCountBounds() {
        assertThrows(Exception::class.java) { ThreeStoveAgentScriptRoots.configuration("{\"scriptRoots\":[" + (1..33).joinToString { "\"/sdcard/$it\"" } + "]}") }
        assertThrows(Exception::class.java) { ThreeStoveAgentScriptRoots.configuration("{\"scriptRoots\":[\"/sdcard/" + "x".repeat(8192) + "\"]}") }
    }
}
