package org.autojs.autojs.external.open;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.script.ScriptLaunchSourceFactory;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.common.ScriptOperations;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.io.FileNotFoundException;
import java.io.InputStream;

import static org.autojs.autojs.util.FileUtils.TYPE.JAVASCRIPT;

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
        ScriptIntents.enforceExternalUriRequestShape(intent);
        if (uri != null && "content".equalsIgnoreCase(uri.getScheme())) {
            String displayName = IntentUtils.getFileName(this, uri);
            ScriptIntents.enforceSingleFileImportBoundary(displayName, uri.getPath());
            String providerMimeType = getContentResolver().getType(uri);
            String ext = ScriptLaunchSourceFactory.resolvePythonImportExtension(
                    displayName,
                    uri.getPath(),
                    intent.getType(),
                    providerMimeType
            );
            if (TextUtils.isEmpty(ext)) {
                ext = JAVASCRIPT.extension;
            }
            InputStream stream = getContentResolver().openInputStream(uri);
            if (stream == null) {
                throw new FileNotFoundException(uri.toString());
            }
            new ScriptOperations(this, null)
                    .importFile("", stream, ext)
                    .subscribe(s -> finish());
        } else {
            final String path = uri == null ? null : uri.getPath();
            if (!TextUtils.isEmpty(path)) {
                ScriptIntents.enforceSingleFileImportBoundary(PFiles.getName(path), path);
                ScriptLaunchSourceFactory.resolvePythonImportExtension(
                        PFiles.getName(path), path, intent.getType(), null);
                new ScriptOperations(this, null)
                        .importFile(path)
                        .subscribe(s -> finish());
            }
        }
    }

}
