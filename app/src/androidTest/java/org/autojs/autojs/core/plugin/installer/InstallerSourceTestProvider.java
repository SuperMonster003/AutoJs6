package org.autojs.autojs.core.plugin.installer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Test-APK-only provider. Java keeps its separate process independent of the target's Kotlin runtime. */
public final class InstallerSourceTestProvider extends ContentProvider {
    public static final String REGISTER_ALIAS = "registerInstallerAlias";
    public static final String CLEAR_ALIAS = "clearInstallerAlias";
    private final Map<String, ParcelFileDescriptor> aliases = new HashMap<>();

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        if (isAlias(uri)) {
            synchronized (aliases) {
                ParcelFileDescriptor file = aliases.get(aliasId(uri.getLastPathSegment()));
                if (file == null) throw new IllegalArgumentException("unknown fixture alias");
                cursor.addRow(new Object[] {"fixture.apk", file.getStatSize()});
            }
        } else cursor.addRow(new Object[] {"stream.apk", null});
        return cursor;
    }

    /** Transfers only a test-owned descriptor, never grants the provider access to a host path. */
    @Override public Bundle call(String method, String arg, Bundle extras) {
        String id = aliasId(arg);
        synchronized (aliases) {
            if (CLEAR_ALIAS.equals(method)) {
                closeQuietly(aliases.remove(id));
            } else if (REGISTER_ALIAS.equals(method)) {
                if (extras == null) throw new IllegalArgumentException("missing fixture descriptor");
                extras.setClassLoader(ParcelFileDescriptor.class.getClassLoader());
                ParcelFileDescriptor received = extras.getParcelable("descriptor");
                if (received == null) throw new IllegalArgumentException("missing fixture descriptor");
                try {
                    if (aliases.size() >= 4 || aliases.containsKey(id)) throw new IllegalArgumentException("fixture alias already exists or limit reached");
                    aliases.put(id, ParcelFileDescriptor.dup(received.getFileDescriptor()));
                } catch (IOException failure) {
                    throw new IllegalStateException("cannot duplicate fixture descriptor", failure);
                } finally {
                    closeQuietly(received);
                }
            } else throw new UnsupportedOperationException("unknown fixture operation");
        }
        Bundle result = new Bundle();
        result.putBoolean("ok", true);
        return result;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if ("denied".equals(uri.getLastPathSegment())) throw new SecurityException("test provider revoked read access");
        if (!"r".equals(mode)) throw new IllegalArgumentException("read-only source");
        if (isAlias(uri)) {
            synchronized (aliases) {
                ParcelFileDescriptor file = aliases.get(aliasId(uri.getLastPathSegment()));
                if (file == null) throw new FileNotFoundException("unknown fixture alias");
                try { return ParcelFileDescriptor.dup(file.getFileDescriptor()); }
                catch (IOException failure) { throw new FileNotFoundException(failure.toString()); }
            }
        }
        final ParcelFileDescriptor[] pipe;
        try { pipe = ParcelFileDescriptor.createPipe(); }
        catch (IOException failure) { throw new FileNotFoundException(failure.toString()); }
        Thread writer = new Thread(() -> {
            try (ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                output.write("installer pipe bytes".getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) { /* The consumer may cancel. */ }
        }, "installer-test-source");
        writer.setDaemon(true);
        writer.start();
        return pipe[0];
    }
    private static boolean isAlias(Uri uri) {
        return uri.getPathSegments().size() == 2 && "fixture".equals(uri.getPathSegments().get(0));
    }
    private static String aliasId(String value) {
        if (value == null || !UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException("invalid fixture id");
        return value;
    }
    private static void closeQuietly(ParcelFileDescriptor file) {
        if (file != null) try { file.close(); } catch (IOException ignored) { /* Test cleanup. */ }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
