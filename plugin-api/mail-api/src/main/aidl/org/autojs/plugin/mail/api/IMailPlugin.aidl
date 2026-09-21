package org.autojs.plugin.mail.api;

import android.os.Bundle;
import org.autojs.plugin.common.api.PluginInfo;
import org.autojs.plugin.mail.api.IMailSession;
import org.autojs.plugin.mail.api.IMailSessionCallback;
import org.autojs.plugin.mail.api.IMailTrigger;
import org.autojs.plugin.mail.api.IMailTriggerCallback;

/**
 * Entry point of the mail plugin. Transaction codes follow the declaration order, so methods may
 * only ever be appended: contract version 1 declared the first five, contract version 2 (plugin
 * roadmap P8, background watches) appended openTrigger and listTriggers.
 */
interface IMailPlugin {
    /** Identity, version and capabilities of the plugin; never null. */
    PluginInfo getInfo();

    /** Live capability bundle keyed by MailCapabilityKeys; may be null when the plugin has nothing to add. */
    Bundle getCapabilities();

    /**
     * Opens a mail session for one account. The bundle carries either the account JSON plus the
     * secrets (MailContract.KEY_ACCOUNT_JSON, KEY_SECRET_PASSWORD or KEY_SECRET_ACCESS_TOKEN) or a
     * saved-account alias (KEY_ACCOUNT_ALIAS). No network is touched here; the first call does that.
     * Returns null when the plugin refuses the account (details arrive through the callback).
     */
    IMailSession openSession(in Bundle account, IMailSessionCallback callback);

    /** Provider presets as a JSON document under MailContract.KEY_PROVIDERS_JSON. */
    Bundle listProviders();

    /** Saved accounts (alias, address and non-secret fields only) under MailContract.KEY_ACCOUNTS_JSON. */
    Bundle listSavedAccounts();

    /**
     * Contract version 2: subscribes to one background watch the plugin keeps without any script
     * running. The bundle carries MailContract.KEY_TRIGGER_OPTIONS_JSON (`triggerId`, optional
     * `filter`) and KEY_GENERATION. Events reach the callback while the subscription lives; when
     * no subscription is alive the plugin wakes the host with MailActions.HOST_TRIGGER_ACTION
     * instead. Returns null when the plugin refuses (an unknown or disabled watch, too many
     * subscribers); the reason arrives through the callback's onStatus.
     */
    IMailTrigger openTrigger(in Bundle options, IMailTriggerCallback callback);

    /** Contract version 2: the background watches configured in the plugin, under MailContract.KEY_TRIGGERS_JSON. */
    Bundle listTriggers();
}
