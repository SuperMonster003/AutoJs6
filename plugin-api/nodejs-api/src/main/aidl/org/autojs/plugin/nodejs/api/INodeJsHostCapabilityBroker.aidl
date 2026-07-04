package org.autojs.plugin.nodejs.api;

import android.os.Bundle;
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityCallback;

interface INodeJsHostCapabilityBroker {

    Bundle getBrokerInfo();

    void dispatch(in Bundle request, INodeJsHostCapabilityCallback callback);

    Bundle getNativeDiagnostics();

    void destroy(in Bundle reason);

}
