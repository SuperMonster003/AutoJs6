package org.autojs.autojs.ui.main.task

import org.autojs.autojs.script.AutoFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.timing.IntentTask
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BunPendingTaskRoutingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun missingPendingTaskUsesTheBunMarkerAsItsFailClosedFallback() {
        val script = temporaryFolder.root.resolve("missing.bun.ts")
        val task = Task.PendingTask(null, IntentTask().apply { scriptPath = script.path })

        assertEquals(JavaScriptSource.ENGINE_BUN, task.engineName)
    }

    @Test
    fun fallbackKeepsBunNodeRhinoAndAutoRoutesDistinct() {
        assertEquals(
            JavaScriptSource.ENGINE_BUN,
            Task.PendingTask.resolveFallbackEngineName("worker.BUN.JS", null, null),
        )
        assertEquals(
            JavaScriptSource.ENGINE_BUN,
            Task.PendingTask.resolveFallbackEngineName("worker.bun.tsx", null, null),
        )
        assertEquals(
            JavaScriptSource.ENGINE_NODEJS,
            Task.PendingTask.resolveFallbackEngineName("worker.node.js", null, null),
        )
        assertEquals(
            JavaScriptSource.ENGINE,
            Task.PendingTask.resolveFallbackEngineName("worker.js", null, null),
        )
        assertEquals(
            AutoFileSource.ENGINE,
            Task.PendingTask.resolveFallbackEngineName("worker.auto", null, null),
        )
    }
}
