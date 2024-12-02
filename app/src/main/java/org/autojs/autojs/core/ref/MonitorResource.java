package org.autojs.autojs.core.ref;

/**
 * Created by SuperMonster003 on Dec 20, 2023.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 20, 2023.
public interface MonitorResource {

    long getPointer();

    void setNativeObjectReference(final NativeObjectReference<MonitorResource> reference);

}
