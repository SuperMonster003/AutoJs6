package org.autojs.autojs.external.shortcut;

import android.app.Activity;
import android.os.Bundle;

import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.model.script.PathChecker;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.script.ScriptLaunchSourceFactory;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.io.File;

/**
 * Created by Stardust on Jan 23, 2017.
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
            ScriptLaunchSourceFactory.Launch launch = ScriptLaunchSourceFactory.createFileLaunch(new File(path));
            if (!ScriptShortcutIntentFactory.isCompatible(getIntent(), path, launch.getEngineName())) {
                ViewUtils.showToast(this, getString(R.string.error_script_shortcut_descriptor_mismatch), true);
                return;
            }
            Scripts.executeLaunch(this, launch);
        } catch (Exception e) {
            e.printStackTrace();
            ViewUtils.showToast(this, e.getMessage(), true);
        }
    }

}
