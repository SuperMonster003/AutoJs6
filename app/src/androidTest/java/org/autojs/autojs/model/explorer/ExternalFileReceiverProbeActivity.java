package org.autojs.autojs.model.explorer;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.ResultReceiver;
import android.provider.OpenableColumns;
import android.system.Os;
import android.system.OsConstants;

import java.util.Arrays;

/**
 * Runs in the test APK's own UID and process, without target APK or Kotlin dependencies.
 * Every descriptor is closed before the result is sent back to instrumentation.
 */
public final class ExternalFileReceiverProbeActivity extends Activity {
    public static final String EXTRA_RECEIVER = "stream_probe.receiver";
    public static final String EXTRA_OFFSETS = "stream_probe.offsets";
    public static final String EXTRA_LENGTHS = "stream_probe.lengths";
    public static final String EXTRA_MODE = "stream_probe.mode";
    public static final String EXTRA_OTHER_URI = "stream_probe.other_uri";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        final Intent intent = getIntent();
        @SuppressWarnings("deprecation")
        final ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RECEIVER);
        if (receiver == null || intent.getData() == null) {
            finish();
            return;
        }
        new Thread(() -> {
            final Bundle result = new Bundle();
            result.putInt("uid", Process.myUid());
            result.putInt("pid", Process.myPid());
            try {
                probe(intent, result);
            } catch (Throwable error) {
                result.putString("fatal_error", describe(error));
            } finally {
                runOnUiThread(() -> {
                    finish();
                    receiver.send(Activity.RESULT_OK, result);
                });
            }
        }, "ExternalFileReceiverProbe").start();
    }

    private void probe(Intent intent, Bundle result) {
        final Uri uri = intent.getData();
        final String[] projection = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, "_data"};
        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                throw new IllegalStateException("No metadata row");
            }
            result.putBoolean("queried", true);
            result.putString("name", cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)));
            result.putLong("size", cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)));
            final int dataColumn = cursor.getColumnIndex("_data");
            result.putBoolean("data_is_null", dataColumn < 0 || cursor.isNull(dataColumn));
        } catch (Throwable error) {
            result.putString("query_error", describe(error));
        }
        try {
            result.putString("mime", getContentResolver().getType(uri));
        } catch (Throwable error) {
            result.putString("type_error", describe(error));
        }

        final String requestedMode = intent.getStringExtra(EXTRA_MODE);
        final String mode = requestedMode == null ? "r" : requestedMode;
        try (ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, mode)) {
            if (descriptor == null) throw new IllegalStateException("No file descriptor");
            result.putBoolean("opened", true);
            // Writable-mode probes deliberately never write, even if a regression grants access.
            if ("r".equals(mode)) {
                final long[] offsets = intent.getLongArrayExtra(EXTRA_OFFSETS);
                final int[] lengths = intent.getIntArrayExtra(EXTRA_LENGTHS);
                if (offsets == null || lengths == null || offsets.length != lengths.length || offsets.length > 16) {
                    throw new IllegalArgumentException("Invalid probe ranges");
                }
                for (int index = 0; index < offsets.length; index++) {
                    if (offsets[index] < 0 || lengths[index] < 0 || lengths[index] > 4096) {
                        throw new IllegalArgumentException("Probe range exceeds its limit");
                    }
                    byte[] buffer = new byte[lengths[index]];
                    int count = Os.pread(descriptor.getFileDescriptor(), buffer, 0, buffer.length, offsets[index]);
                    result.putByteArray("bytes_" + index, Arrays.copyOf(buffer, count));
                    long position = Os.lseek(descriptor.getFileDescriptor(), offsets[index], OsConstants.SEEK_SET);
                    result.putLong("seek_" + index, position);
                    byte[] sequential = new byte[lengths[index]];
                    int sequentialCount = Os.read(descriptor.getFileDescriptor(), sequential, 0, sequential.length);
                    result.putByteArray("seek_bytes_" + index, Arrays.copyOf(sequential, sequentialCount));
                }
            }
        } catch (Throwable error) {
            result.putString("open_error", describe(error));
        }

        // A String extra carries no URI grant. It tests that granting one token never grants a
        // sibling token, even when both documents belong to the same source directory/provider.
        final String otherValue = intent.getStringExtra(EXTRA_OTHER_URI);
        if (otherValue != null) {
            final Uri other = Uri.parse(otherValue);
            try (Cursor ignored = getContentResolver().query(other, projection, null, null, null)) {
                result.putBoolean("other_query_allowed", true);
            } catch (Throwable error) {
                result.putString("other_query_error", describe(error));
            }
            try (ParcelFileDescriptor ignored = getContentResolver().openFileDescriptor(other, "r")) {
                result.putBoolean("other_open_allowed", true);
            } catch (Throwable error) {
                result.putString("other_open_error", describe(error));
            }
        }
    }

    private static String describe(Throwable error) {
        return error.getClass().getName() + ": " + String.valueOf(error.getMessage());
    }
}
