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
import java.util.UUID;
import org.json.JSONObject;

/** Standalone test-APK process: deliberately depends only on Android and Java. */
public final class RuntimeWebDocumentsProvider extends DocumentsProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : new String[] {
                Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES, Root.COLUMN_ICON });
        Map<String, Object> values = new HashMap<>();
        values.put(Root.COLUMN_ROOT_ID, "fixtures"); values.put(Root.COLUMN_DOCUMENT_ID, "root");
        values.put(Root.COLUMN_TITLE, "AutoJs6 Web Fixtures"); values.put(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD);
        values.put(Root.COLUMN_MIME_TYPES, "*/*"); values.put(Root.COLUMN_ICON, android.R.drawable.ic_menu_upload);
        addRow(cursor, values); return cursor;
    }
    @Override public Cursor queryDocument(String documentId, String[] projection) {
        MatrixCursor cursor = cursor(projection); addDocument(cursor, documentId); return cursor;
    }
    @Override public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) {
        if (!("root".equals(parentDocumentId) || "outputs".equals(parentDocumentId))) throw new IllegalArgumentException("Unknown fixture directory");
        MatrixCursor cursor = cursor(projection);
        if ("root".equals(parentDocumentId)) { addDocument(cursor, "alpha"); addDocument(cursor, "beta"); addDocument(cursor, "outputs"); }
        File[] files = directory().listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) for (File file : files) {
            String id = file.getName().replace(".json", "");
            if (parentDocumentId.equals(metadata(id).optString("parent"))) addDocument(cursor, id);
        }
        return cursor;
    }
    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        if (documentId.startsWith("saved-")) { metadata(documentId); return ParcelFileDescriptor.open(dataFile(documentId), ParcelFileDescriptor.parseMode(mode)); }
        if (!("alpha".equals(documentId) || "beta".equals(documentId)) || !"r".equals(mode)) throw new FileNotFoundException("Unknown fixture file");
        return ParcelFileDescriptor.open(fixtureFile(documentId), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public boolean isChildDocument(String parentDocumentId, String documentId) {
        return "root".equals(parentDocumentId) || "outputs".equals(parentDocumentId) && documentId.startsWith("saved-") && "outputs".equals(metadata(documentId).optString("parent"));
    }
    @Override public android.provider.DocumentsContract.Path findDocumentPath(String parentDocumentId, String childDocumentId) throws FileNotFoundException {
        java.util.ArrayList<String> path = new java.util.ArrayList<>(); path.add("root");
        if ("outputs".equals(childDocumentId) || childDocumentId.startsWith("saved-") && "outputs".equals(metadata(childDocumentId).optString("parent"))) path.add("outputs");
        if (!"root".equals(childDocumentId) && !"outputs".equals(childDocumentId)) path.add(childDocumentId);
        if (parentDocumentId != null) {
            int index = path.indexOf(parentDocumentId); if (index < 0) throw new FileNotFoundException("Outside fixture tree");
            path = new java.util.ArrayList<>(path.subList(index, path.size()));
        }
        return new android.provider.DocumentsContract.Path(parentDocumentId == null ? "fixtures" : null, path);
    }
    @Override public String createDocument(String parentDocumentId, String mimeType, String displayName) throws FileNotFoundException {
        if (!("root".equals(parentDocumentId) || "outputs".equals(parentDocumentId))) throw new FileNotFoundException("Unknown fixture directory");
        String id = "saved-" + UUID.randomUUID();
        try {
            JSONObject value = new JSONObject().put("name", displayName).put("mime", mimeType).put("parent", parentDocumentId);
            try (FileOutputStream stream = new FileOutputStream(new File(directory(), id + ".json"))) { stream.write(value.toString().getBytes(StandardCharsets.UTF_8)); }
            if (!dataFile(id).createNewFile()) throw new IOException("Fixture document exists");
            return id;
        } catch (Exception error) { throw new FileNotFoundException(error.toString()); }
    }
    @Override public void deleteDocument(String documentId) throws FileNotFoundException {
        metadata(documentId); dataFile(documentId).delete(); new File(directory(), documentId + ".json").delete();
    }
    private File directory() { File dir = new File(getContext().getCacheDir(), "runtime-web-documents"); dir.mkdirs(); return dir; }
    private File dataFile(String id) { checkId(id); return new File(directory(), id + ".bin"); }
    private void checkId(String id) { if (!id.matches("saved-[0-9a-f-]{36}")) throw new IllegalArgumentException("Unknown fixture document"); }
    private JSONObject metadata(String id) {
        checkId(id);
        try (java.io.FileInputStream input = new java.io.FileInputStream(new File(directory(), id + ".json"))) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[1024]; int length;
            while ((length = input.read(buffer)) >= 0) bytes.write(buffer, 0, length);
            return new JSONObject(bytes.toString("UTF-8"));
        } catch (Exception error) { throw new IllegalArgumentException(error); }
    }
    private MatrixCursor cursor(String[] projection) {
        return new MatrixCursor(projection != null ? projection : new String[] { Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED });
    }
    private void addDocument(MatrixCursor cursor, String id) {
        boolean saved = id.startsWith("saved-"); JSONObject meta = saved ? metadata(id) : null;
        if (!("root".equals(id) || "outputs".equals(id) || "alpha".equals(id) || "beta".equals(id) || saved)) throw new IllegalArgumentException("Unknown fixture document");
        Map<String, Object> values = new HashMap<>(); boolean directory = "root".equals(id) || "outputs".equals(id);
        values.put(Document.COLUMN_DOCUMENT_ID, id);
        values.put(Document.COLUMN_DISPLAY_NAME, saved ? meta.optString("name") : directory ? ("outputs".equals(id) ? "Web outputs" : "AutoJs6 Web Fixtures") : "aj6-web-fixture-" + id + ".txt");
        values.put(Document.COLUMN_MIME_TYPE, saved ? meta.optString("mime") : directory ? Document.MIME_TYPE_DIR : "text/plain");
        values.put(Document.COLUMN_FLAGS, directory ? Document.FLAG_DIR_SUPPORTS_CREATE : saved ? Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE : 0);
        values.put(Document.COLUMN_SIZE, directory ? 0 : saved ? dataFile(id).length() : content(id).getBytes(StandardCharsets.UTF_8).length);
        values.put(Document.COLUMN_LAST_MODIFIED, directory ? 0L : saved ? dataFile(id).lastModified() : fixtureFile(id).lastModified()); addRow(cursor, values);
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
