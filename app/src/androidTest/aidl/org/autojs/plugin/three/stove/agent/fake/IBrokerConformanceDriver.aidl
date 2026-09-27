package org.autojs.plugin.three.stove.agent.fake;

import android.os.Bundle;

/** Test APK only. Its copy in app/src/androidTest/aidl must remain byte-identical. */
interface IBrokerConformanceDriver {
    Bundle getIdentity();
    void dispatch(IBinder broker, in Bundle request, IBinder callback);
    void model(IBinder broker, String operation, in Bundle request, IBinder callback);
    Bundle probeUnauthorized(IBinder capability, IBinder model);
    void sendAttach(String identityMode, String marker, IBinder completion);
    IBinder getPlugin();
    Bundle control(String operation, int generation, in Bundle payload);
}
