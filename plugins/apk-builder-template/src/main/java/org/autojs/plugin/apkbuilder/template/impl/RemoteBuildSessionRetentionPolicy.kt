package org.autojs.plugin.apkbuilder.template.impl

internal object RemoteBuildSessionRetentionPolicy {

    fun shouldKeepCallbackDeathRecipientLinked(
        workspaceRetainedForResult: Boolean,
        sessionClosed: Boolean,
    ): Boolean {
        return workspaceRetainedForResult && !sessionClosed
    }
}
