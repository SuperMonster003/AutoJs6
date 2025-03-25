package com.mcal.apksigner.app.filepicker;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by radiationx on 13.01.17.
 */

public class FilePickHelper {
//    private final static String LOG_TAG = FilePickHelper.class.getSimpleName();

    @NonNull
    public static Intent pickFile(boolean apk) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        if (apk) {
            intent.setType("application/vnd.android.package-archive");
        } else {
            intent.setType("*/*");
        }
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        return Intent.createChooser(intent, "Select file");
    }

    @NonNull
    public static List<RequestFile> onActivityResult(@NonNull Context context, @NonNull Intent data) {
        List<RequestFile> files = new ArrayList<>();
        RequestFile tempFile;
//        Log.d(LOG_TAG, "onActivityResult " + data);
        if (data.getData() == null) {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    tempFile = createFile(context, data.getClipData().getItemAt(i).getUri());
                    if (tempFile != null) files.add(tempFile);
                }
            }
        } else {
            tempFile = createFile(context, data.getData());
            if (tempFile != null) files.add(tempFile);
        }
        return files;
    }

    @Nullable
    private static RequestFile createFile(@NonNull Context context, @NonNull Uri uri) {
        RequestFile requestFile = null;
//        Log.d(LOG_TAG, "createFile " + uri);
        try {
            InputStream inputStream = null;
            String name = getFileName(context, uri);
            String extension = MimeTypeUtil.getExtension(name);
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mimeType == null) {
                mimeType = context.getContentResolver().getType(uri);
            }
            if (mimeType == null) {
                mimeType = MimeTypeUtil.getType(extension);
            }
            if (uri.getScheme().equals("content")) {
                inputStream = context.getContentResolver().openInputStream(uri);
            } else if (uri.getScheme().equals("file")) {
                inputStream = new FileInputStream(uri.getPath());
            }
            requestFile = new RequestFile(name, mimeType, inputStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return requestFile;
    }

    @NonNull
    public static String getFileName(@NonNull Context context, @NonNull Uri uri) {
//        Log.d(LOG_TAG, "getFileName " + uri.getScheme() + " : " + context.getContentResolver().getType(uri));
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
//            Log.d(LOG_TAG, "res " + uri.getPath());
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}
