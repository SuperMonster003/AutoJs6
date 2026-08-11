package org.autojs.autojs.external.shortcut;

import android.app.Activity;
import android.os.Bundle;

import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.util.ViewUtils;

/**
 * Created by Stardust on Jan 23, 2017.
 */
public class ShortcutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        runScriptIntent();
        finish();
    }

    private void runScriptIntent() {
        try {
            ScriptIntents.handleIntent(this, getIntent());
        } catch (Exception e) {
            e.printStackTrace();
            ViewUtils.showToast(this, e.getMessage(), true);
        }
    }

}
