package org.autojs.autojs.external.shortcut;

import android.app.Activity;
import android.os.Bundle;

import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.model.script.PathChecker;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.util.ViewUtils;

/**
 * Created by Stardust on 2017/1/23.
 */
public class ShortcutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String path = getIntent().getStringExtra(ScriptIntents.EXTRA_KEY_PATH);
        if (new PathChecker(this).checkAndToastError(path)) {
            runScriptFile(path);
        }
        finish();
    }

    private void runScriptFile(String path) {
        try {
            Scripts.run(this, new ScriptFile(path));
        } catch (Exception e) {
            e.printStackTrace();
            ViewUtils.showToast(this, e.getMessage(), true);
        }
    }

}
