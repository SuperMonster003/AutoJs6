package org.autojs.autojs.engine;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Standalone test-APK process: deliberately depends only on Android and Java. */
public final class RuntimeWebDocumentsProvider extends DocumentsProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : new String[] {
                Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES, Root.COLUMN_ICON });
        Map<String, Object> values = new HashMap<>();
        values.put(Root.COLUMN_ROOT_ID, "fixtures"); values.put(Root.COLUMN_DOCUMENT_ID, "root");
        values.put(Root.COLUMN_TITLE, "AutoJs6 Web Fixtures"); values.put(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY);
        values.put(Root.COLUMN_MIME_TYPES, "text/plain"); values.put(Root.COLUMN_ICON, android.R.drawable.ic_menu_upload);
        addRow(cursor, values); return cursor;
    }
    @Override public Cursor queryDocument(String documentId, String[] projection) {
        MatrixCursor cursor = cursor(projection); addDocument(cursor, documentId); return cursor;
    }
    @Override public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) {
        if (!"root".equals(parentDocumentId)) throw new IllegalArgumentException("Unknown fixture directory");
        MatrixCursor cursor = cursor(projection); addDocument(cursor, "alpha"); addDocument(cursor, "beta"); return cursor;
    }
    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        if (!("alpha".equals(documentId) || "beta".equals(documentId)) || !"r".equals(mode)) throw new FileNotFoundException("Unknown fixture file");
        return ParcelFileDescriptor.open(fixtureFile(documentId), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public boolean isChildDocument(String parentDocumentId, String documentId) {
        return "root".equals(parentDocumentId) && ("alpha".equals(documentId) || "beta".equals(documentId));
    }
    private MatrixCursor cursor(String[] projection) {
        return new MatrixCursor(projection != null ? projection : new String[] { Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED });
    }
    private void addDocument(MatrixCursor cursor, String id) {
        if (!("root".equals(id) || "alpha".equals(id) || "beta".equals(id))) throw new IllegalArgumentException("Unknown fixture document");
        Map<String, Object> values = new HashMap<>(); boolean directory = "root".equals(id);
        values.put(Document.COLUMN_DOCUMENT_ID, id);
        values.put(Document.COLUMN_DISPLAY_NAME, directory ? "AutoJs6 Web Fixtures" : "aj6-web-fixture-" + id + ".txt");
        values.put(Document.COLUMN_MIME_TYPE, directory ? Document.MIME_TYPE_DIR : "text/plain");
        values.put(Document.COLUMN_FLAGS, 0); values.put(Document.COLUMN_SIZE, directory ? 0 : content(id).getBytes(StandardCharsets.UTF_8).length);
        values.put(Document.COLUMN_LAST_MODIFIED, directory ? 0L : fixtureFile(id).lastModified()); addRow(cursor, values);
    }
    private synchronized File fixtureFile(String id) {
        File file = new File(getContext().getCacheDir(), "aj6-web-fixture-" + id + ".txt");
        // Chromium checks that a selected file stays unchanged between metadata and repeated reads.
        if (!file.exists()) {
            try (FileOutputStream stream = new FileOutputStream(file)) { stream.write(content(id).getBytes(StandardCharsets.UTF_8)); }
            catch (IOException error) { throw new IllegalStateException(error); }
        }
        return file;
    }
    private static void addRow(MatrixCursor cursor, Map<String, Object> values) {
        MatrixCursor.RowBuilder row = cursor.newRow(); for (String column : cursor.getColumnNames()) row.add(values.get(column));
    }
    public static String content(String id) { return "AutoJs6 file chooser " + id + "\n"; }
}
