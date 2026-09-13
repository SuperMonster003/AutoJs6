package org.autojs.sdk37.balfixture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CallbackReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { TokenProvider.record(context, intent, "broadcast-received"); }
}
