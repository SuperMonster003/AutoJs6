package org.autojs.plugin.python.runtime.api;

interface IPythonHostCapabilityBroker {

    byte[] dispatch(in byte[] request);
}
