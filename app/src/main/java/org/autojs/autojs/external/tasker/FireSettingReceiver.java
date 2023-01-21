package org.autojs.autojs.external.tasker;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.twofortyfouram.locale.sdk.client.receiver.AbstractPluginSettingReceiver;

import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.external.open.RunIntentActivity;
import org.json.JSONObject;

/**
 * Created by Stardust on 2017/3/27.
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
        context.startActivity(new Intent(context, RunIntentActivity.class)
                .putExtra("json", jsonObject.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

}
