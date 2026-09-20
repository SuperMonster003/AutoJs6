package org.autojs.plugin.epub.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.autojs.plugin.common.api.PluginInfo;
import org.autojs.plugin.epub.api.IEpubBook;
import org.autojs.plugin.epub.api.IEpubReaderCallback;
import org.autojs.plugin.epub.api.IEpubReaderSession;

/**
 * Entry point of the EPUB plugin (contract version 1). Transaction codes follow the declaration
 * order, so methods may only ever be appended.
 *
 * Methods that do not answer with a Bundle report failures as IllegalArgumentException or
 * IllegalStateException whose message starts with an EpubErrorCodes code followed by ": "
 * (see EpubErrorCodes.encode); Bundle answers carry EpubContract.KEY_ERROR_CODE instead.
 */
interface IEpubPlugin {
    /** Identity, version and capabilities of the plugin; never null. */
    PluginInfo getInfo();

    /** Live capability bundle keyed by EpubCapabilityKeys; may be null when the plugin has nothing to add. */
    Bundle getCapabilities();

    /**
     * Opens one book for extraction. The descriptor is owned by the host, must refer to a regular
     * file opened read-only and is closed by the host after the call returns; the plugin duplicates
     * it before returning. Options are keyed by EpubContract (contract version, display name) and
     * stay below EpubContract.MAX_OPTIONS_BYTES. At most EpubContract.MAX_OPEN_BOOKS books are open
     * per host at a time; a book that stays idle for EpubContract.BOOK_IDLE_TIMEOUT_MS is closed.
     */
    IEpubBook openBook(in ParcelFileDescriptor source, in Bundle options);

    /**
     * Opens a reader session: the plugin pre-opens the book, mints a random session token and
     * returns the session whose getState carries EpubContract.KEY_SESSION_TOKEN. The host then
     * starts the reader Activity itself with EpubActions.READER_ACTIVITY_ACTION and the token as
     * its only extra (two-step launch); a session not claimed by the Activity within
     * EpubContract.READER_CLAIM_TIMEOUT_MS closes with EpubContract.REASON_TIMEOUT. Options are
     * the start position (KEY_LOCATOR, KEY_HREF or KEY_PROGRESSION) and KEY_PREFERENCES. Opening a
     * second session replaces the first, which closes with EpubContract.REASON_REPLACED.
     */
    IEpubReaderSession openReader(in ParcelFileDescriptor source, in Bundle options, IEpubReaderCallback callback);
}
