package org.autojs.autojs.apkbuilder.template

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkBuilderTemplateAbiPolicyTest {

    @Test
    fun legacyAndUniversalMetadataRemainCompatible() {
        assertTrue(ApkBuilderTemplateAbiPolicy.isCompatible(null, listOf("arm64-v8a")))
        assertTrue(ApkBuilderTemplateAbiPolicy.isCompatible(emptyList(), listOf("x86_64")))
    }

    @Test
    fun exactAbiVariantIsAccepted() {
        assertTrue(
            ApkBuilderTemplateAbiPolicy.isCompatible(
                listOf("arm64-v8a"),
                listOf("arm64-v8a", "armeabi-v7a"),
            )
        )
    }

    @Test
    fun unrelatedAbiVariantIsRejected() {
        assertFalse(
            ApkBuilderTemplateAbiPolicy.isCompatible(
                listOf("x86_64"),
                listOf("arm64-v8a", "armeabi-v7a"),
            )
        )
    }

}
