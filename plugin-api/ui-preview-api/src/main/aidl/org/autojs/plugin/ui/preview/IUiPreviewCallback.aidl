package org.autojs.plugin.ui.preview;

import android.os.Bundle;

// Delivered by the host to the preview client; every method is oneway and carries the session id first.
oneway interface IUiPreviewCallback {

    // The transaction order is frozen; new methods are append-only.

    // Element bounds after a layout pass: a JSON array in boundsJson (see UiPreviewContract.Bounds),
    // truncated to maxBoundsEntries with boundsTruncated = true.
    void onBounds(String sessionId, in Bundle bounds);

    // A failure outside a setLayout call, for example a {{ }} binding that failed while a list item was
    // bound: errorCode, errorMessage, elementIndex (-1 when unknown) and attrName (optional).
    void onLayoutFailed(String sessionId, in Bundle failure);

    // The host released the session on its own: reason names why (stopped, timeout, service_destroyed,
    // script_error). The session id is invalid afterwards.
    void onSessionLost(String sessionId, in Bundle reason);

    // Edit pointer mode only: a tap on the embedded root with x, y (root pixels), the deepest hit
    // element's index (-1 when none) and its class name.
    void onTap(String sessionId, in Bundle tap);

    // v2: one console entry of the running script: runId, level (android.util.Log priority) and text.
    void onConsole(String sessionId, in Bundle entry);

    // v2: an uncaught error of the running script: runId, errorMessage and line (-1 when unknown); v4 adds errorCode
    // and elapsedMs when the busy-wait limit ended the run (ERR_AUTOJS6_UI_PREVIEW_BUSY_WAIT).
    void onScriptError(String sessionId, in Bundle error);

    // v2: the running script ended: runId and reason (exit, stopped, error, replaced, released).
    void onScriptExit(String sessionId, in Bundle exit);

    // v4: an event of sendLifecycle reached the run: runId, event and consumed (back_pressed only; false means the
    // run then ended with exit).
    void onLifecycleHandled(String sessionId, in Bundle result);
}
