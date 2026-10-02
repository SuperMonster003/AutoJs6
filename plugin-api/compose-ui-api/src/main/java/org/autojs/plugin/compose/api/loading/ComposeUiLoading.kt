package org.autojs.plugin.compose.api.loading

import android.content.Context
import android.os.Bundle
import android.view.View
import org.autojs.plugin.compose.api.model.ThemeSpec
import org.autojs.plugin.compose.api.model.UiCommand
import org.autojs.plugin.compose.api.model.UiEvent
import org.autojs.plugin.compose.api.model.UiPatchBatch
import java.util.concurrent.Executor

interface ComposeUiRendererFactory {
    fun contractVersion(): Int
    /** Return a fresh Bundle using ComposeUiCapabilityKeys. Components must be a catalog subset. */
    fun capabilities(): Bundle
    /** Main thread. A returned renderer owns no script functions and must not execute script callbacks. */
    fun create(environment: ComposeUiHostEnvironment): ComposeUiRenderer
}

interface ComposeUiHostEnvironment {
    val hostContext: Context
    val mainExecutor: Executor
    val eventSink: ComposeUiEventSink
    val initialTheme: ThemeSpec
    val sessionId: Int
}

interface ComposeUiRenderer {
    /** All methods are main-thread confined. The returned view is created lazily and belongs to this renderer. */
    fun view(): View
    /** Validate the whole batch before applying it. Failure preserves the last valid tree and generation. */
    fun apply(batch: UiPatchBatch)
    fun execute(command: UiCommand)
    fun setTheme(theme: ThemeSpec)
    /** Idempotent. Release composition, pending renderer work and borrowed bitmap references. */
    fun dispose()
}

/** Nonblocking enqueue only. The host dispatches on its script scheduler and drops stale generations. */
fun interface ComposeUiEventSink { fun enqueue(event: UiEvent) }
