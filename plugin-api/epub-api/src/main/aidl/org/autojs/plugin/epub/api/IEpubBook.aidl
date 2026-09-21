package org.autojs.plugin.epub.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/**
 * One open book (contract versions 1 and 2). Calls on one book are serialized by the plugin;
 * different books run in parallel. Every Bundle answer either carries the documented result keys
 * or EpubContract.KEY_ERROR_CODE plus KEY_ERROR_MESSAGE; after close every call answers
 * EpubErrorCodes.SESSION_CLOSED. Transaction codes follow the declaration order: a method may only
 * ever be appended.
 */
interface IEpubBook {
    /** Metadata document as JSON under EpubContract.KEY_METADATA (at most MAX_METADATA_BYTES). */
    Bundle getMetadata();

    /** Flattened table of contents as JSON under EpubContract.KEY_TOC (at most MAX_TOC_ENTRIES entries). */
    Bundle getToc();

    /** Reading order as JSON under EpubContract.KEY_READING_ORDER (at most MAX_READING_ORDER_ENTRIES entries). */
    Bundle getReadingOrder();

    /**
     * Plain text of one reading-order resource, addressed by KEY_HREF or KEY_INDEX, starting at the
     * character KEY_OFFSET and limited to KEY_MAX_CHARS characters (at most MAX_TEXT_CHARS_PER_CALL).
     * KEY_FORMAT selects plain text or the light Markdown rendering. The answer carries KEY_TEXT,
     * KEY_OFFSET (echo) and KEY_HAS_MORE; the next chunk starts at offset plus the returned length.
     */
    Bundle getText(in Bundle request);

    /**
     * Read end of a pipe streaming one manifest resource (cover image or any other href of the
     * publication, at most MAX_RESOURCE_BYTES). The host owns the returned descriptor and reads it
     * to end of stream. Unknown or out-of-manifest hrefs fail with EpubErrorCodes.RESOURCE_NOT_FOUND.
     */
    ParcelFileDescriptor openResource(String href);

    /**
     * Full-text search for KEY_QUERY (1 to MAX_QUERY_LENGTH characters), skipping KEY_OFFSET results
     * and answering at most KEY_LIMIT (default DEFAULT_SEARCH_LIMIT, at most MAX_SEARCH_RESULTS) as
     * JSON under KEY_RESULTS with KEY_HAS_MORE.
     */
    Bundle search(in Bundle request);

    /** Number of synthetic page positions under EpubContract.KEY_POSITIONS (0 when unknown). */
    Bundle getPositions();

    /** Releases the book and its descriptor; idempotent. */
    void close();

    /**
     * Contract version 2: the reader's highlights and notes of this book as JSON under
     * EpubContract.KEY_ANNOTATIONS, in reading order, skipping KEY_OFFSET entries and answering at
     * most KEY_LIMIT of them (default DEFAULT_ANNOTATIONS_LIMIT, at most MAX_ANNOTATIONS_PAGE; a
     * page is cut early to stay below MAX_ANNOTATIONS_BYTES). The answer carries KEY_OFFSET (echo)
     * and KEY_HAS_MORE; the next page starts at offset plus the returned length. Hosts that
     * negotiated version 1 must not call it.
     */
    Bundle getAnnotations(in Bundle request);
}
