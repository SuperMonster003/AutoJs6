package org.autojs.plugin.ai.agent.api;

import android.os.Bundle;
import org.autojs.plugin.common.api.PluginInfo;
import org.autojs.plugin.ai.agent.api.IAiAgentLink;
import org.autojs.plugin.ai.agent.api.IAiAgentModelBroker;
import org.autojs.plugin.host.capability.api.IHostCapabilityBroker;
import org.autojs.plugin.ai.agent.api.IAiAgentLinkCallback;

/**
 * AI Agent contract version 1; transaction order is frozen and future methods append only.
 * All Bundle parameters and returns are non-null snapshots, never mutated after sending.
 * Binder threads may call concurrently. Synchronous methods validate/enqueue within 200 ms.
 * Receivers own incoming descriptors and must close rejected/unread descriptors immediately.
 */
interface IAiAgentPlugin {
    /** Standard non-null identity; capabilities use AiAgentCapabilityKeys. */
    PluginInfo getInfo();
    /** Non-null capabilities, readable before attaching. */
    Bundle getCapabilities();
    /**
     * Config contains KEY_LINK_CONFIG_JSON. Both brokers and callback are non-null and retained
     * until detach/death. Reattachment releases the old proxies and reports HOST_REATTACHED.
     * The returned link is non-null; failures are explicit protocol errors, never null sentinels.
     */
    IAiAgentLink attach(in Bundle config, IAiAgentModelBroker modelBroker, IHostCapabilityBroker capabilityBroker, IAiAgentLinkCallback callback);
}
