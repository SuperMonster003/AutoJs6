package org.autojs.plugin.mail.api

import org.autojs.plugin.common.api.PluginActions

/**
 * Discovery contract of the mail plugin family. The literals are frozen: the plugin manifest,
 * the host `<queries>` block, and the plugin center registration all repeat them.
 */
object MailActions {

    /** Generic plugin-discovery action answered by the plugin's `IPluginInfoProvider` service. */
    const val INFO = PluginActions.INFO

    /** Action of the [IMailPlugin] service; always paired with [SERVICE_CATEGORY]. */
    const val SERVICE_ACTION = "org.autojs.plugin.MAIL"

    /** Inline-credential sessions for independently signed applications; no saved accounts. */
    const val PACKAGED_SERVICE_ACTION = "org.autojs.plugin.MAIL_PACKAGED"

    const val PACKAGED_PERMISSION = "org.autojs.plugin.mail.permission.RUNTIME"

    /** Intent category of the mail service, so unrelated `org.autojs.plugin.*` services never match. */
    const val SERVICE_CATEGORY = "mail"

    /** Signature permission required by the original host entries and saved-account access. */
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"

    /** Parameterless settings entry (accounts page), exported only behind [PLUGIN_PERMISSION]. */
    const val OPEN_SETTINGS = "org.autojs.plugin.MAIL_SETTINGS"

    /**
     * Explicit broadcast the plugin sends to the host package (contract version 2, plugin roadmap
     * P8) when a background watch reports mail and no `openTrigger` subscription of the host is
     * alive; the host's receiver is exported behind [PLUGIN_PERMISSION]. Extras:
     * `MailContract.KEY_CONTRACT_VERSION`, `KEY_TRIGGER_ID` and `KEY_EVENT_JSON` (the same
     * document as `IMailTriggerCallback.onMail`, generation 0).
     */
    const val HOST_TRIGGER_ACTION = "org.autojs.autojs6.action.MAIL_TRIGGER"
}
