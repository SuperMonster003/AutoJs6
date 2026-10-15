package org.autojs.plugin.gallery.api;

import android.os.Bundle;

/**
 * One picker, viewer or editor session opened through IGalleryPlugin (contract version 1). The
 * plugin Activity that claimed the session owns its lifecycle, and the outcome is reported through
 * IGalleryCallback events. Calls after the session closed answer or throw
 * GalleryErrorCodes.SESSION_CLOSED.
 */
interface IGallerySession {
    /**
     * Session state: GalleryContract.KEY_SESSION_TOKEN, KEY_KIND and KEY_VISIBLE, and for a viewer
     * the current KEY_INDEX once the Activity claimed the session.
     */
    Bundle getState();

    /**
     * Closes the session: the token expires and events stop after the plugin's EVENT_CLOSE of reason
     * REASON_HOST. The Activity finishes unless GalleryContract.KEY_FINISH is false in the options (a
     * null bundle finishes it); an editor never writes its target after the host closed the session.
     */
    void close(in Bundle options);
}
