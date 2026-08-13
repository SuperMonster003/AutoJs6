package org.autojs.autojs.external.shortcut;

import android.app.Activity;
import android.os.Bundle;

import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

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
            ScriptShortcutIntentFactory.DirectPathLaunch preparedLaunch =
                    ScriptShortcutIntentFactory.prepareDirectPathLaunch(this, getIntent());
            if (preparedLaunch == null) {
                return;
            }
            if (!ScriptShortcutIntentFactory.isCompatible(
                    getIntent(), preparedLaunch.getPath(), preparedLaunch.getLaunch().getEngineName())) {
                ViewUtils.showToast(this, getString(R.string.error_script_shortcut_descriptor_mismatch), true);
                return;
            }
            Scripts.executeLaunch(this, preparedLaunch.getLaunch(), preparedLaunch.getConfig());
        } catch (Exception e) {
            e.printStackTrace();
            ViewUtils.showToast(this, e.getMessage(), true);
        }
    }

}
