package org.autojs.sdk37.fleetfixture;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public class FixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File fixture(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (!"fixture.mp4".equals(name) && !"fixture.m4a".equals(name)) throw new FileNotFoundException();
        File file = new File(getContext().getCacheDir(), name);
        if (!file.isFile()) {
            try (InputStream in = getContext().getAssets().open(name); OutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[16384]; int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            } catch (IOException e) { throw new FileNotFoundException(e.toString()); }
        }
        return file;
    }
    @Override public String getType(Uri uri) { return "fixture.mp4".equals(uri.getLastPathSegment()) ? "video/mp4" : "audio/mp4"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        try {
            File file = fixture(uri);
            String[] columns = projection == null ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
                if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
            }
            cursor.addRow(row); return cursor;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("Read only fixture");
        return ParcelFileDescriptor.open(fixture(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
}
