package org.autojs.plugin.apkbuilder.template.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteBuildSessionRetentionPolicyTest {

    @Test
    fun successfulResultKeepsDeathRecipientUntilSessionClose() {
        assertTrue(
            RemoteBuildSessionRetentionPolicy.shouldKeepCallbackDeathRecipientLinked(
                workspaceRetainedForResult = true,
                sessionClosed = false,
            )
        )
        assertFalse(
            RemoteBuildSessionRetentionPolicy.shouldKeepCallbackDeathRecipientLinked(
                workspaceRetainedForResult = true,
                sessionClosed = true,
            )
        )
    }

    @Test
    fun cleanedFailureOrCancellationDoesNotKeepDeathRecipient() {
        assertFalse(
            RemoteBuildSessionRetentionPolicy.shouldKeepCallbackDeathRecipientLinked(
                workspaceRetainedForResult = false,
                sessionClosed = false,
            )
        )
    }
}
