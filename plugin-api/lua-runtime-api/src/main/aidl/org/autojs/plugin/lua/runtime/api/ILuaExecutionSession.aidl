package org.autojs.plugin.lua.runtime.api;

oneway interface ILuaExecutionSession {

    void start();

    void grantOutputCredits(int count);

    void cancel();

    void close();
}
