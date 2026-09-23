package org.autojs.autojs.core.plugin.agent;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;

/** Test APK only. This standalone process cannot load dependencies deduplicated into the host APK.
 * A platform-only Binder consumes the frozen oneway callback and provides a test-only death signal. */
public final class AgentCallbackDeathService extends Service {
    public static final int KILL_PROCESS = IBinder.FIRST_CALL_TRANSACTION + 100;
    private Binder callback(String descriptor) { return new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(descriptor); return true; }
            if (code == KILL_PROCESS) {
                try {
                    if (Binder.getCallingUid() != getPackageManager().getApplicationInfo("org.autojs.autojs6", 0).uid)
                        throw new SecurityException("Test host only");
                    Process.killProcess(Process.myPid());
                } catch (android.content.pm.PackageManager.NameNotFoundException e) { throw new SecurityException("Test host absent"); }
                return true;
            }
            if (code == FIRST_CALL_TRANSACTION || code == FIRST_CALL_TRANSACTION + 1) { data.enforceInterface(descriptor); return true; }
            return false;
        }
    }; }
    @Override public IBinder onBind(Intent intent) {
        return callback("link".equals(intent.getAction()) ? "org.autojs.plugin.ai.agent.api.IAiAgentLinkCallback" : "org.autojs.plugin.ai.agent.api.IAiAgentRunCallback");
    }
}
