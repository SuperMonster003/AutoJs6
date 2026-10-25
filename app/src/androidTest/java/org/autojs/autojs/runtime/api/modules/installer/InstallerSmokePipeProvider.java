package org.autojs.autojs.runtime.api.modules.installer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.OpenableColumns;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Instrumentation-only source which can never become an APK; cancellation is observed by EPIPE.
 * Pure Java: the test APK's independent provider process has neither production classes nor the
 * Kotlin runtime excluded from androidTest packaging as a duplicate of the instrumented app.
 */
public class InstallerSmokePipeProvider extends ContentProvider {
    public static final String AUTHORITY = "org.autojs.autojs6.test.installer.smoke.pipe";

    private static final class Entry {
        ParcelFileDescriptor writer;
        boolean opened;
        boolean readersClosed;
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        enforceTestCaller();
        if ("create".equals(method)) {
            String token = UUID.randomUUID().toString();
            entries.put(token, new Entry());
            Bundle reply = new Bundle();
            reply.putString("uri", "content://" + AUTHORITY + "/" + token);
            return reply;
        }
        Entry entry = arg == null ? null : entries.get(arg);
        if (entry == null) throw new IllegalArgumentException("Unknown smoke source");
        synchronized (entry) {
            if ("probe".equals(method)) {
                if (entry.writer != null) {
                    try {
                        // The tests write only a few zero bytes, never a valid archive or a full pipe.
                        Os.write(entry.writer.getFileDescriptor(), new byte[]{0}, 0, 1);
                    } catch (ErrnoException error) {
                        if (error.errno != OsConstants.EPIPE) throw new IllegalStateException("Pipe probe failed", error);
                        entry.readersClosed = true;
                        closeWriter(entry);
                    } catch (IOException error) {
                        throw new IllegalStateException("Pipe probe was interrupted", error);
                    }
                }
                Bundle reply = new Bundle();
                reply.putBoolean("opened", entry.opened);
                reply.putBoolean("readersClosed", entry.readersClosed);
                return reply;
            }
            if ("close".equals(method)) {
                closeWriter(entry);
                entries.remove(arg);
                return new Bundle();
            }
            throw new IllegalArgumentException("Unsupported smoke source command");
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        enforceTestCaller();
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only smoke source");
        String token = uri.getLastPathSegment();
        Entry entry = token == null ? null : entries.get(token);
        if (entry == null) throw new FileNotFoundException("Unknown smoke source");
        synchronized (entry) {
            if (entry.opened) throw new FileNotFoundException("Smoke source may be opened only once");
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                entry.writer = pipe[1];
                entry.opened = true;
                return pipe[0];
            } catch (IOException error) {
                FileNotFoundException failure = new FileNotFoundException("Cannot create smoke pipe");
                failure.initCause(error);
                throw failure;
            }
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        enforceTestCaller();
        String[] columns = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "waiting.apk";
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        enforceTestCaller();
        return "application/vnd.android.package-archive";
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }

    @Override
    public void shutdown() {
        for (Entry entry : entries.values()) {
            synchronized (entry) {
                try { closeWriter(entry); } catch (RuntimeException ignored) { }
            }
        }
        entries.clear();
        super.shutdown();
    }

    private static void closeWriter(Entry entry) {
        ParcelFileDescriptor descriptor = entry.writer;
        entry.writer = null;
        if (descriptor == null) return;
        try { descriptor.close(); } catch (IOException error) { throw new IllegalStateException("Cannot close smoke pipe", error); }
    }

    private void enforceTestCaller() {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return;
        Context context = getContext();
        if (context == null) throw new SecurityException("Smoke provider has no context");
        try {
            InstrumentationInfo[] targets = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_INSTRUMENTATION).instrumentation;
            if (targets == null || targets.length != 1) throw new SecurityException("Smoke instrumentation target is unavailable");
            String[] callers = context.getPackageManager().getPackagesForUid(uid);
            if (callers == null || !Arrays.asList(callers).contains(targets[0].targetPackage)) {
                throw new SecurityException("Smoke sources belong to the instrumented host");
            }
        } catch (PackageManager.NameNotFoundException error) {
            throw new SecurityException("Smoke instrumentation target is unavailable", error);
        }
    }
}
