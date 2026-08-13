package org.autojs.plugin.lua.runtime.fake.control;

/** Test-only control plane. Counter indexes are defined by FakeLuaConformanceCounterContract. */
interface IFakeLuaConformanceControl {

    void reset();

    void selectScenario(String metadataValue);

    void selectRuntimeAmbiguity(String firstMetadataValue, String secondMetadataValue);

    void releasePluginInfoStall();

    void releaseRuntimeInfoStall();

    void releaseCreateExecutionStall();

    void releaseAllStalls();

    long[] getCounters();
}
