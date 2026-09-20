package org.autojs.plugin.epub.api;

import android.os.Bundle;

/**
 * One reader session opened through IEpubPlugin.openReader (contract version 1). The reader
 * Activity of the plugin owns the lifecycle: host requests are forwarded to it, and the outcome
 * is reported through IEpubReaderCallback events. Calls after the session closed answer or throw
 * EpubErrorCodes.SESSION_CLOSED; calls before the Activity claimed the session or while it is not
 * visible answer or throw EpubErrorCodes.READER_NOT_VISIBLE.
 */
interface IEpubReaderSession {
    /**
     * Session state: EpubContract.KEY_SESSION_TOKEN, KEY_VISIBLE, and once the Activity claimed the
     * session KEY_LOCATOR (JSON), KEY_PROGRESSION (total progression), KEY_HREF, KEY_INDEX and
     * KEY_TITLE of the current resource.
     */
    Bundle getState();

    /** Jumps to KEY_LOCATOR (JSON), KEY_HREF or KEY_PROGRESSION of the target bundle. */
    void goTo(in Bundle target);

    /** Moves by one of the EpubContract.DIRECTION_* constants. */
    void navigate(int direction);

    /**
     * Applies the EpubContract.PREFERENCE_* subset carried as JSON under KEY_PREFERENCES (at most
     * MAX_PREFERENCES_BYTES); unsupported fields are ignored and reported through an
     * EpubContract.EVENT_ERROR event with EpubErrorCodes.UNSUPPORTED_PREFERENCE.
     */
    void setPreferences(in Bundle preferences);

    /** Bookmarks of the book as JSON under EpubContract.KEY_BOOKMARKS (at most MAX_BOOKMARKS). */
    Bundle getBookmarks();

    /**
     * Closes the session: the token expires, events stop and the reader Activity stays open as an
     * ordinary reader unless EpubContract.KEY_FINISH is true in the options (a null bundle keeps
     * the Activity). The plugin confirms with an EVENT_CLOSE event of reason REASON_HOST.
     */
    void close(in Bundle options);
}
