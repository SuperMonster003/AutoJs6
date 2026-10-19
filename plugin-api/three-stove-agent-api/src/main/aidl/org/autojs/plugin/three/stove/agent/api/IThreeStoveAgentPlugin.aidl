package org.autojs.plugin.three.stove.agent.api;

import android.os.Bundle;
import org.autojs.plugin.common.api.PluginInfo;
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentLink;
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentModelBroker;
import org.autojs.plugin.host.capability.api.IHostCapabilityBroker;
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentLinkCallback;

/**
 * 3-Stove Agent contract version 2; transaction order is frozen and future methods append only.
 * All Bundle parameters and returns are non-null snapshots, never mutated after sending.
 * Binder threads may call concurrently. Synchronous methods validate/enqueue within 200 ms.
 * Receivers own incoming descriptors and must close rejected/unread descriptors immediately.
 */
interface IThreeStoveAgentPlugin {
    /** Standard non-null identity; capabilities use ThreeStoveAgentCapabilityKeys. */
    PluginInfo getInfo();
    /** Non-null capabilities, readable before attaching. */
    Bundle getCapabilities();
    /**
     * Config contains KEY_LINK_CONFIG_JSON. Both brokers and callback are non-null and retained
     * until detach/death. Reattachment releases the old proxies and reports HOST_REATTACHED.
     * The returned link is non-null; failures are explicit protocol errors, never null sentinels.
     */
    IThreeStoveAgentLink attach(in Bundle config, IThreeStoveAgentModelBroker modelBroker, IHostCapabilityBroker capabilityBroker, IThreeStoveAgentLinkCallback callback);
}
