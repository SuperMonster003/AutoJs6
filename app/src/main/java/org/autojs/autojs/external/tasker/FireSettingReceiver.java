package org.autojs.autojs.external.tasker;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import com.twofortyfouram.locale.sdk.client.receiver.AbstractPluginSettingReceiver;
import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.external.open.RunIntentActivity;
import org.autojs.autojs.util.IntentUtils;
import org.json.JSONObject;

/**
 * Created by Stardust on Mar 27, 2017.
 */
public class FireSettingReceiver extends AbstractPluginSettingReceiver {

    @Override
    protected boolean isJsonValid(@NonNull JSONObject jsonObject) {
        return ScriptIntents.isTaskerJsonObjectValid(jsonObject);
    }

    @Override
    protected boolean isAsync() {
        return true;
    }

    @Override
    protected void firePluginSetting(@NonNull Context context, @NonNull JSONObject jsonObject) {
        Intent intent = new Intent(context, RunIntentActivity.class)
                .putExtra(ScriptIntents.EXTRA_KEY_JSON, jsonObject.toString());
        IntentUtils.startSafely(intent, context);
    }

}
