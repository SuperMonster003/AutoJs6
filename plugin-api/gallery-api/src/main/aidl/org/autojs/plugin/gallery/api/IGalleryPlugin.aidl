package org.autojs.plugin.gallery.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.autojs.plugin.common.api.PluginInfo;
import org.autojs.plugin.gallery.api.IGalleryCallback;
import org.autojs.plugin.gallery.api.IGallerySession;

/**
 * Entry point of the gallery plugin (contract version 1). Transaction codes follow the declaration
 * order, so methods may only ever be appended.
 *
 * The three interactive calls open a session and never start an Activity themselves: the session's
 * getState carries GalleryContract.KEY_SESSION_TOKEN, and the host starts the plugin Activity of
 * GalleryActions.SESSION_ACTIVITY_ACTION with the token as its only extra (two-step launch). One
 * interactive session is open per host at a time; opening another replaces it (REASON_REPLACED).
 *
 * File access stays with the host: the plugin only receives descriptors the host opened for the
 * script's own paths, so a script never reaches a file through the plugin that it could not open
 * itself. Methods that do not answer with a Bundle report failures as IllegalArgumentException or
 * IllegalStateException whose message starts with a GalleryErrorCodes code followed by ": "
 * (GalleryErrorCodes.encode); Bundle answers carry GalleryContract.KEY_ERROR_CODE instead.
 */
interface IGalleryPlugin {
    /** Identity, version and capabilities of the plugin; never null. */
    PluginInfo getInfo();

    /** Live capability bundle keyed by GalleryCapabilityKeys; may be null when the plugin has nothing to add. */
    Bundle getCapabilities();

    /**
     * Opens a picker session over the plugin's own media library. Options: KEY_MULTIPLE,
     * KEY_MAX_COUNT, KEY_MIME_TYPES and KEY_TITLE. The outcome is one EVENT_RESULT whose KEY_ITEMS
     * lists the chosen images (JSON) followed by EVENT_CLOSE (REASON_DONE), or EVENT_CLOSE alone when
     * the user backs out, or EVENT_ERROR with PERMISSION_REQUIRED when the user refuses photo access.
     */
    IGallerySession openPicker(in Bundle options, IGalleryCallback callback);

    /**
     * Opens a viewer session over the given images: read-only descriptors of regular files, owned and
     * closed by the host after the call returns (the plugin duplicates what it keeps), 1 to
     * GalleryContract.MAX_VIEW_PAGES of them. Options: KEY_DISPLAY_NAMES (one per source) and
     * KEY_START_INDEX. The session reports EVENT_OPEN, EVENT_PAGE on every page change and EVENT_CLOSE.
     */
    IGallerySession openViewer(in ParcelFileDescriptor[] sources, in Bundle options, IGalleryCallback callback);

    /**
     * Opens an editor session for one image. The source is read-only as in openViewer; the target is
     * an empty regular file the host staged for the output and opened read-write. When the user saves,
     * the plugin writes the image into the target, reports EVENT_RESULT (KEY_MIME_TYPE, KEY_WIDTH,
     * KEY_HEIGHT, KEY_SIZE) and closes with REASON_DONE; the host then reads it back and publishes it.
     * Options: KEY_DISPLAY_NAME, KEY_FORMAT, KEY_QUALITY, KEY_KEEP_EXIF and KEY_KEEP_LOCATION.
     */
    IGallerySession openEditor(in ParcelFileDescriptor source, in ParcelFileDescriptor target, in Bundle options, IGalleryCallback callback);

    /**
     * Converts one image without any UI: decodes the read-only source and writes the result into the
     * read-write target the host staged. Options follow the plugin's converter (KEY_FORMAT, KEY_QUALITY,
     * KEY_SIZE_MODE and its sizes, KEY_BACKGROUND, KEY_LOSSLESS, KEY_TARGET_BYTES, KEY_KEEP_EXIF,
     * KEY_KEEP_LOCATION). The answer carries KEY_MIME_TYPE, KEY_WIDTH, KEY_HEIGHT and KEY_SIZE, or
     * KEY_ERROR_CODE; the host then reads the target back and publishes it.
     */
    Bundle convert(in ParcelFileDescriptor source, in ParcelFileDescriptor target, in Bundle options);
}
