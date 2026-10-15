package org.autojs.plugin.ui.preview;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.autojs.plugin.ui.preview.IUiPreviewCallback;

// Host-side preview of a "ui" mode XML layout for the 3-Block Compose designer: the host inflates the
// layout with its real dynamic layout inflater and script runtime, and either embeds the view tree into
// the caller's SurfaceView (API 30+) or draws it offscreen into a snapshot.
interface IUiPreviewService {

    // The transaction order is frozen; new methods are append-only.
    int getContractVersion();

    String getContractHash();

    // Host version, supported modes, limits and status; see the KEY_* constants of UiPreviewContract.
    Bundle getPreviewInfo();

    // Creates a session: mode (embedded / snapshot), size, optional configuration overrides and, for the
    // embedded mode, the SurfaceView's hostToken (API 30+) or inputTransferToken (API 35+). The result
    // carries sessionId and, for the embedded mode, the SurfacePackage to attach.
    Bundle createSession(in Bundle request, IUiPreviewCallback callback);

    // Inflates xml with bindingsJson as the {{ }} binding context and replaces the session's content.
    // Returns synchronously with the outcome; bounds follow through the callback. For the snapshot mode
    // the ARGB_8888 pixels are written to output, a writable descriptor owned by the caller.
    Bundle setLayout(String sessionId, in Bundle layout, in @nullable ParcelFileDescriptor output);

    // Changes the logical screen size; the embedded hierarchy is relaid out, a snapshot session only
    // records the size for the next setLayout.
    Bundle resize(String sessionId, int widthPx, int heightPx);

    void release(String sessionId);

    // Embedded mode: a fresh SurfacePackage for the same hierarchy after the caller's surface was
    // destroyed and created again.
    Bundle reattach(String sessionId);

    // 0 = edit (the host root consumes pointer input, reports taps through onTap and draws the highlight),
    // 1 = interactive (pointer input reaches the inflated views; API 33+ only).
    Bundle setPointerMode(String sessionId, int mode);

    // Draws a highlight frame around the view of elementIndex on the host root; -1 clears it.
    Bundle setHighlight(String sessionId, int elementIndex);

    // v2 (interactive preview, 3-Block Compose 1.7.0): runs a whole "ui" mode script (scriptText, scriptName) as a
    // normal host script execution whose ui.layout content goes into the session's embedded root. A run that is
    // still going is stopped first. Embedded sessions on API 33+ only. The result carries runId; console lines,
    // errors and the end of the run arrive through the v2 callbacks.
    Bundle runScript(String sessionId, in Bundle request);

    // v2: stops the running script of the session, if any; the layout preview runtime stays.
    Bundle stopScript(String sessionId);
}
