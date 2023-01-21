package org.autojs.autojs.external;

import android.content.Context;
import android.content.Intent;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.execution.ExecutionConfig;
import org.autojs.autojs.model.script.PathChecker;
import org.autojs.autojs.script.JavaScriptFileSource;
import org.autojs.autojs.script.ScriptSource;
import org.autojs.autojs.script.SequenceScriptSource;
import org.autojs.autojs.script.StringScriptSource;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.json.JSONObject;

import java.io.File;

/**
 * Created by Stardust on 2017/4/1.
 */
public class ScriptIntents {

    public static final String EXTRA_KEY_PATH = "path";
    public static final String EXTRA_KEY_PRE_EXECUTE_SCRIPT = "script";
    public static final String EXTRA_KEY_LOOP_TIMES = "loop";
    public static final String EXTRA_KEY_LOOP_INTERVAL = "interval";
    public static final String EXTRA_KEY_DELAY = "delay";

    public static boolean isTaskerJsonObjectValid(JSONObject json) {
        return json.has(ScriptIntents.EXTRA_KEY_PATH) || json.has(EXTRA_KEY_PRE_EXECUTE_SCRIPT);
    }

    public static boolean handleIntent(Context context, Intent intent) {
        String path = getPath(intent);
        String script = intent.getStringExtra(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT);
        int loopTimes = intent.getIntExtra(EXTRA_KEY_LOOP_TIMES, 1);
        long delay = intent.getLongExtra(EXTRA_KEY_DELAY, 0);
        long interval = intent.getLongExtra(EXTRA_KEY_LOOP_INTERVAL, 0);
        ScriptSource source = null;
        ExecutionConfig config = new ExecutionConfig();
        config.setDelay(delay);
        config.setLoopTimes(loopTimes);
        config.setInterval(interval);
        config.setArgument("intent", intent);
        if (path == null && script != null) {
            source = new StringScriptSource(script);
        } else if (path != null && new PathChecker(context).checkAndToastError(path)) {
            JavaScriptFileSource fileScriptSource = new JavaScriptFileSource(path);
            if (script != null) {
                source = new SequenceScriptSource(fileScriptSource.getName(), new StringScriptSource(script), fileScriptSource);
            } else {
                source = fileScriptSource;
            }
            String workingDirectory = new File(path).getParent();
            if (workingDirectory != null) {
                config.setWorkingDirectory(workingDirectory);
            }
        } else {
            config.setWorkingDirectory(WorkingDirectoryUtils.getPath());
        }
        if (source == null) {
            return false;
        }
        AutoJs.getInstance().getScriptEngineService().execute(source, config);
        return true;
    }

    private static String getPath(Intent intent) {
        if (intent.getData() != null && intent.getData().getPath() != null)
            return intent.getData().getPath();
        return intent.getStringExtra(ScriptIntents.EXTRA_KEY_PATH);
    }
}
