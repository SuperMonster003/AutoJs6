package org.autojs.autojs.external.open;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.script.StringScriptSource;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Created by Stardust on Feb 22, 2017.
 */
public class RunIntentActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            handleIntent(getIntent());
        } catch (Exception e) {
            e.printStackTrace();
            ViewUtils.showToast(this, R.string.edit_and_run_handle_intent_error, true);
        }
        finish();
    }

    private void handleIntent(Intent intent) throws FileNotFoundException {
        Uri uri = intent.getData();
        if (uri != null && "content".equals(uri.getScheme())) {
            InputStream stream = getContentResolver().openInputStream(uri);
            Scripts.run(this, new StringScriptSource(PFiles.read(stream)));
        } else {
            ScriptIntents.handleIntent(this, intent);
        }
    }

}
