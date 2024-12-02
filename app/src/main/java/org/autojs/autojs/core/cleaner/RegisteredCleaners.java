package org.autojs.autojs.core.cleaner;

import org.autojs.autojs.core.ref.MonitorResource;
import org.autojs.autojs.core.ref.NativeObjectReference;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by SuperMonster003 on Dec 22, 2023.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 22, 2023.
public final class RegisteredCleaners {

    public static final Set<NativeObjectReference<MonitorResource>> registeredCleaners = Collections.newSetFromMap(new ConcurrentHashMap<>());

}
