package org.autojs.plugin.mail.api;

import android.os.Bundle;
import org.autojs.plugin.common.api.PluginInfo;
import org.autojs.plugin.mail.api.IMailSession;
import org.autojs.plugin.mail.api.IMailSessionCallback;

/**
 * Entry point of the mail plugin (contract version 1). Transaction codes follow the declaration
 * order, so methods may only ever be appended.
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
}
