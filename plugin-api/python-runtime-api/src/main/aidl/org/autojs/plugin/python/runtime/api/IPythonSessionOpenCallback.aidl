package org.autojs.plugin.python.runtime.api;

import org.autojs.plugin.python.runtime.api.IPythonExecutionSession;

oneway interface IPythonSessionOpenCallback {

    void onOpened(IPythonExecutionSession session);

    void onRejected(in byte[] error);
}
