package org.autojs.plugin.bun.runtime.api;

public final class BunPluginCapabilityKeys {
    public static final String CONTRACT_VERSION = "org.autojs.plugin.bun.CONTRACT_VERSION";
    public static final String RUNTIME_VERSION = "org.autojs.plugin.bun.RUNTIME_VERSION";
    public static final String RUNTIME_REVISION = "org.autojs.plugin.bun.RUNTIME_REVISION";
    public static final String SOURCE_TRANSPORT = "org.autojs.plugin.bun.SOURCE_TRANSPORT";
    public static final String EXECUTION_MODEL = "org.autojs.plugin.bun.EXECUTION_MODEL";
    public static final String SUPPORTS_TYPESCRIPT = "org.autojs.plugin.bun.SUPPORTS_TYPESCRIPT";
    public static final String SUPPORTS_CANCELLATION = "org.autojs.plugin.bun.SUPPORTS_CANCELLATION";
    public static final String SUPPORTS_STREAMING_OUTPUT = "org.autojs.plugin.bun.SUPPORTS_STREAMING_OUTPUT";
    public static final String SUPPORTS_WORKSPACE_ARCHIVE = "org.autojs.plugin.bun.SUPPORTS_WORKSPACE_ARCHIVE";
    public static final String SUPPORTS_HOST_INFO = "org.autojs.plugin.bun.SUPPORTS_HOST_INFO";
    public static final String SUPPORTS_HOST_CAPABILITY_BRIDGE = "org.autojs.plugin.bun.SUPPORTS_HOST_CAPABILITY_BRIDGE";
    /** Int: the most granted capability IDs one request may carry (BunRuntimeContract.MAX_HOST_CAPABILITY_COUNT of the plugin's contract). */
    public static final String MAX_HOST_CAPABILITY_COUNT = "org.autojs.plugin.bun.MAX_HOST_CAPABILITY_COUNT";

    private BunPluginCapabilityKeys() {
    }
}
