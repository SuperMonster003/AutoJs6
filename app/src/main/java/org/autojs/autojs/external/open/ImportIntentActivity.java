package org.autojs.autojs.external.open;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.common.ScriptOperations;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Created by Stardust on Feb 2, 2017.
 */
public class ImportIntentActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        try {
            handleIntent(intent);
        } catch (Exception e) {
            e.printStackTrace();
            ViewUtils.showToast(this, R.string.edit_and_run_handle_intent_error, true);
            finish();
        }
    }

    private void handleIntent(Intent intent) throws FileNotFoundException {
        Uri uri = intent.getData();
        if (uri != null && "content".equals(uri.getScheme())) {
            String ext = PFiles.getExtension(uri.getScheme());
            if (TextUtils.isEmpty(ext)) {
                ext = "js";
            }
            InputStream stream = getContentResolver().openInputStream(uri);
            new ScriptOperations(this, null)
                    .importFile("", stream, ext)
                    .subscribe(s -> finish());
        } else {
            final String path = intent.getData().getPath();
            if (!TextUtils.isEmpty(path)) {
                new ScriptOperations(this, null)
                        .importFile(path)
                        .subscribe(s -> finish());
            }
        }
    }

}