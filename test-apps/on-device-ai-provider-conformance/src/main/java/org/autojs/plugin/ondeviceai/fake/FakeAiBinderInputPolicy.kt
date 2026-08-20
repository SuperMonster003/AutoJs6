package org.autojs.plugin.ondeviceai.fake

import org.autojs.plugin.ai.common.api.AiCommonLimits

/** Cheap Binder-thread preflight checks that must run before copying metadata or duplicating FDs. */
object FakeAiBinderInputPolicy {
    fun requireEnvelopeSize(byteCount: Int) {
        require(byteCount in 1..AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES) {
            "Fake AI Binder envelope size is invalid"
        }
    }

    fun requireRequestDescriptorCount(descriptorCount: Int, providerMaximum: Int) {
        require(providerMaximum in 0..AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST)
        require(descriptorCount in 0..minOf(providerMaximum, AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST)) {
            "Fake AI request descriptor count exceeds the protocol limit"
        }
    }

    fun requireSessionDescriptorCount(descriptorCount: Int, providerMaximum: Int) {
        require(providerMaximum in 0..AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION)
        require(descriptorCount in 0..minOf(providerMaximum, AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION)) {
            "Fake AI session descriptor count exceeds the protocol limit"
        }
    }
}
