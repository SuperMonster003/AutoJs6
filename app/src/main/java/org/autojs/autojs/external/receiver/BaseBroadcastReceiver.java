package org.autojs.autojs.external.receiver;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.engine.RuntimeTasksBridge;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.timing.IntentTask;
import org.autojs.autojs.timing.TimedTaskManager;
import org.autojs.autojs.util.ViewUtils;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class BaseBroadcastReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "BaseBroadcastReceiver";

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    public void onReceive(Context context, Intent intent) {
        Log.d(LOG_TAG, "onReceive: intent = " + intent + ", this = " + this);
        try {
            TimedTaskManager.getIntentTaskOfAction(intent.getAction())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .filter(task -> !task.isLocal())
                    .subscribe(intentTask -> runTask(context, intent, intentTask), Throwable::printStackTrace);
        } catch (Exception e) {
            ViewUtils.showToast(context, e.getMessage(), true);
        }
    }

    public static void runTask(Context context, Intent intent, IntentTask task) {
        if (!java.util.Objects.equals(task.getAction(), intent.getAction())) return;
        if (task.getCategory() != null && !intent.hasCategory(task.getCategory())) return;
        if (task.getDataType() != null && task.getIntentFilter().matchData(intent.getType(), intent.getScheme(), intent.getData()) < 0) return;
        RuntimeTasksBridge.recordIntent(context, task, intent);
        Log.d(LOG_TAG, "runTask: action = " + intent.getAction() + ", script = " + task.getScriptPath());
        try {
            Intent launchIntent = new Intent().putExtra(ScriptIntents.EXTRA_KEY_PATH, task.getScriptPath());
            if (task.getSourceKind() != null) {
                launchIntent.putExtra(ScriptIntents.EXTRA_KEY_SOURCE_KIND, task.getSourceKind());
            }
            if (task.getProjectRoot() != null) {
                launchIntent.putExtra(ScriptIntents.EXTRA_KEY_PROJECT_ROOT, task.getProjectRoot());
            }
            ScriptIntents.PreparedLaunch preparedLaunch = ScriptIntents.prepareLaunch(
                    context,
                    launchIntent,
                    (Intent) intent.clone()
            );
            if (preparedLaunch != null) {
                Scripts.executeLaunch(context, preparedLaunch.getLaunch(), preparedLaunch.getConfig());
            }
        } catch (Exception e) {
            e.printStackTrace();
            ViewUtils.showToast(context, e.getMessage(), true);
        }
    }

}
