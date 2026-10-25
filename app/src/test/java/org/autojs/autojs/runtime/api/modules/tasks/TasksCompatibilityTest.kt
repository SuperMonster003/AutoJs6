package org.autojs.autojs.runtime.api.modules.tasks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject

class TasksCompatibilityTest {

    @Test
    fun taskCallbacksAreAsynchronousOnlyWhenExplicitlyRequested() {
        assertFalse(Tasks.shouldRunTaskCallbackAsync(NativeObject()))

        assertTrue(Tasks.shouldRunTaskCallbackAsync(NativeObject().apply {
            ScriptableObject.putProperty(this, "async", true)
        }))
        assertTrue(Tasks.shouldRunTaskCallbackAsync(NativeObject().apply {
            ScriptableObject.putProperty(this, "isAsync", true)
        }))
    }

    @Test
    fun legacyNullTaskOperationsReturnNullWhileBooleanBackendResultIsFalse() {
        assertFalse(Tasks.removeTaskSyncResult(null))
        assertTrue(Tasks.removeTaskRhino(null) == null)
        assertTrue(Tasks.updateTaskRhino(null) == null)
        assertTrue(Tasks.addTaskRhino(null) == null)
    }

}
