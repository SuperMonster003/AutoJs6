package org.autojs.autojs.core.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptProjectSessionPolicyTest {

    @Test
    fun negotiatesOnlyTheKnownAdditiveRevision() {
        assertNull(TypeScriptProjectSessionPolicy.negotiatedRevision(null))
        assertNull(TypeScriptProjectSessionPolicy.negotiatedRevision(0))
        assertEquals(
            TypeScriptR2Contract.PROJECT_SESSION_REVISION,
            TypeScriptProjectSessionPolicy.negotiatedRevision(
                TypeScriptR2Contract.PROJECT_SESSION_REVISION,
            ),
        )
        assertNull(
            TypeScriptProjectSessionPolicy.negotiatedRevision(
                TypeScriptR2Contract.PROJECT_SESSION_REVISION + 1,
            ),
        )
    }

    @Test
    fun editorPrewarmRecognizesAllTypeScriptSourceFamiliesCaseInsensitively() {
        assertTrue(
            TypeScriptProjectSessionPolicy.shouldPrewarmForEditor(
                listOf(null, "content://documents/project/ENTRY.TSX"),
            ),
        )
        assertTrue(
            TypeScriptProjectSessionPolicy.shouldPrewarmForEditor(
                listOf("/project/types/runtime.d.mts"),
            ),
        )
        assertTrue(
            TypeScriptProjectSessionPolicy.shouldPrewarmForEditor(
                listOf("module.cts"),
            ),
        )
        assertFalse(
            TypeScriptProjectSessionPolicy.shouldPrewarmForEditor(
                listOf(null, "main.js", "notes.txt"),
            ),
        )
    }
}
