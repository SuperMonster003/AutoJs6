# Installer Plugin Protocol V2

Date: 2026-10-02. P8 extends the [V1 contract](installer-plugin-protocol-v1.md) with the Dhizuku
authorizer, notification installation and an explicit persistent-default scope. Public AIDL
transaction numbers 1 through 10, existing JSON defaults, descriptor ownership, caller checks,
limits and session lifetime rules are unchanged. `InstallerIds.REQUIRED_HOST_VERSION_CODE`
remains 5299. The P8 script additions require a host that implements this extension and a plugin
advertising the requested capability; they do not become available merely because V1 can bind.

## Version and feature negotiation

`InstallerContract.CONTRACT_VERSION=2`, with host support for versions 1 through 2. A plugin that
also supports original hosts advertises these values in both its INFO capabilities and live
`getCapabilities()` response:

| Key | Type | Value / meaning |
| --- | --- | --- |
| `installerContractVersion` | Int | 1, the oldest supported version, retained for original V1 hosts |
| `installerMaxContractVersion` | Int | 2, the newest supported version; omission means only the baseline version |
| `installerAuthorizers` | String[] | Implemented concrete authorizers; add `dhizuku` only when implemented |
| `installerFeatures` | String[] | Existing feature names plus implemented V2 features described below |

The host validates `1 <= base <= 2`, `base <= maximum <= Int.MAX_VALUE`, then uses
`min(maximum, 2)`. It does not convert an overflowing version number to a small supported version.
A newer maximum can negotiate down, but an unsupported minimum or malformed range is rejected.

Each mutating request checks live capabilities on the same Binder lease that will receive it.
The host does not attempt an unknown transaction and infer support from its exception. Every
request envelope carries the negotiated `contractVersion`, the real host build number, and the
existing bounded `requestJson`. A helper without an explicitly negotiated version defaults to 1.

| Request | Additional requirement before sending | Missing support |
| --- | --- | --- |
| Explicit `authorizer: 'dhizuku'`, including state and permission requests | V2 and `dhizuku` in `installerAuthorizers` | `AUTHORIZER_UNAVAILABLE` |
| Install/session `interaction: 'notification'` | V2 and `notification-install` in `installerFeatures` | `INVALID_ARGUMENT` |
| Default `mode: 'persistent'` | V2 and `persistent-default-installer` in `installerFeatures` | `INVALID_ARGUMENT` |

Version 2 alone does not promise either optional feature. A V1 peer cannot enable extensions by
accidentally including the new strings in its feature list. Unsupported requests fail locally
before the operation is sent; open-session failure still closes the descriptors already acquired
by the host. No unrecognized mode field is added to a V1 default request.

Original V1 hosts accept a plugin whose baseline capability remains 1 and ignore the new maximum.
Their install/uninstall result decoders already accept an arbitrary `authorizer` string, and the
default-status decoder already accepts an arbitrary `method` string. Consequently, an old `auto`
request can receive a result naming a user-enabled Dhizuku authorizer, and an old status query can
receive `method: 'persistent'`, without a parse failure. V1 discovery still filters unknown explicit
authorizer names. An old error decoder can omit an unknown `error.authorizer`; existing error
code, message, status and systemMessage handling stays intact.

## Appended default-installer transaction

Only this method is appended to `IInstallerPlugin`, after `getUsers`:

```aidl
void setDefaultInstallerV2(boolean enable, in Bundle request, IInstallerCallback callback);
```

`requestJson` contains `{ "authorizer": "dhizuku", "mode": "persistent" }`. `mode` is the requested
scope, either `preferred` or `persistent`; `DEFAULT_MODE_*` names are distinct from the observed
`DEFAULT_METHOD_*` values. The result remains the existing DefaultInstallerState document:

```json
{ "component": "package/activity", "isSelf": true, "method": "persistent", "requiresClear": false }
```

An optional `persistentConfigured` boolean records the plugin's last completed local persistent
configuration for the relevant owner. It is a receipt, not proof of the system's current policy;
the host defaults an omitted value to false and never uses it to decide `isSelf` or operation
success. A false or absent receipt likewise does not prove that no persistent policy exists.
Read-only status must not derive `method: 'persistent'` from this receipt and ordinary intent
resolution. An observed ordinary preferred record takes precedence; otherwise the method is
`none` unless an authoritative policy read independently proves more.

The Dhizuku persistent path currently accepts API 26 through 33. On API 34+, Android reports
the final policy outcome to the owner's [PolicyUpdateReceiver](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#addPersistentPreferredActivity(android.content.ComponentName,%20android.content.IntentFilter,%20android.content.ComponentName)), so a void DPM return is not treated
as final success; until that result can be received, the plugin refuses this operation before
writing. It never writes a policy merely to probe status. A successful explicit write response
may report the operation's confirmed method separately from a later read-only state.

The old `setDefaultInstaller` transaction remains preferred-only. The host continues to use it
when mode is omitted or explicitly `preferred`, sending only `{ "authorizer": "..." }`, even
when V2 is negotiated. Persistent requests use the appended transaction only after the capability
checks. Disabling persistent scope removes only the plugin's managed persistent preference,
without clearing another package or erasing ordinary preferred records.

The script API retains a boolean result:

```js
installer.setDefault(true, { authorizer: 'dhizuku', mode: 'persistent' });
installer.setDefaultAsync(false, { authorizer: 'dhizuku', mode: 'persistent' });
```

- Omitted/preferred mode retains the original `state.isSelf === enabled` meaning.
- Persistent enable succeeds only when `isSelf` is true and the observed method is `persistent`.
- Persistent disable succeeds when the plugin is no longer the persistent handler. It can still
  be the ordinary preferred handler, so `isDefault()` can remain true after successful removal of
  persistent scope. A V2 persistent result must explicitly report a recognized method; a missing
  or unknown method does not prove successful removal.

No new option changes the original defaults: `enabled=true`, `authorizer='auto'`, and
`mode='preferred'`. Unknown keys, explicit null, non-boolean enabled values and unknown modes are
rejected before Binder work. Whether a device can grant or remove a persistent preference is a
plugin/device policy fact, not a promise made by the host contract.

## Dhizuku and notification boundaries

Dhizuku is a separately authorized device/profile-owner transport, available from API 26 in the
P8 plugin. Dhizuku must first be activated as owner, and the installer plugin must be authorized
separately. It is not equivalent to Root or a Shizuku shell identity. The P8 implementation only
targets its current owner/profile user and attributes installation to the owner package. It
explicitly refuses `allowDowngrade`, `allowTestOnly`, `bypassLowTargetSdk`, another installer
package, another/all users, and uninstall `keepData`; it never silently ignores these requests.
The host preserves the plugin's unsupported-argument or authorization result rather than falling
back to a different authorizer. Authorization to AutoJs6 itself does not grant the plugin access.

For new plugin configurations, automatic order is Shizuku, Root, Dhizuku, none. Existing explicit
orders and enabled-authorizer selections are preserved. Persistent default management filters
that order to enabled, usable Root in user 0 or Dhizuku on API 26 through 33. It skips Shizuku and
none. An explicit identity, or a failure after auto selects one, never falls back to another
identity or to preferred mode.

The Root persistent implementation starts an isolated system UID/GID 1000 process without
changing the shared RootService identity. It is limited to user 0 and the plugin's fixed four
APK intent filters. It requires a compatible Root manager, inherited private file descriptors
and framework/SELinux permission; incompatibility is reported without weakening SELinux. The
production bridge was exercised on API 24, Magisk 25.2, SELinux Enforcing. This does not establish
compatibility with other ROMs or Root managers. Competing APK persistent policies or unrecognized
existing plugin policies are rejected before mutation; other packages' policies and ordinary
defaults are preserved. Cancellation/failure only attempts rollback of proved newly created
policies, not earlier policy. A hard process death or blocked Binder cannot guarantee rollback;
unconfirmed operations retain their audit and are never replayed automatically.

Dhizuku's grouped policy writes likewise are not a transaction. A partial failure retains a
pending record and does not clear unknown earlier policy to simulate rollback. Passive status
does not write or clear anything to test policy. The script surface has no DefaultInstallerState
object or persistentConfigured property; isDefault and setDefault retain boolean results. Source
delivery and capability negotiation do not claim a device matrix pass; actual environments and
device evidence remain recorded in the plugin's roadmap.

Notification presentation applies to install/installAsync/session. It does not silently replace
an unsupported mode with `dialog` or `silent`. The plugin may still need Android's installation
confirmation; the notification directs the user to that action. If the notification permission,
global setting or required channel prevents showing it, the V2 error is
`NOTIFICATION_UNAVAILABLE`. The host preserves that stable code in thrown/rejected InstallerError
objects and per-item result errors. The new code is not automatically retryable.

Uninstall continues to accept only `auto`, `dialog` and `silent`; explicitly passing
`notification` to uninstall is `INVALID_ARGUMENT`. A feature named `notification-install` does
not advertise notification uninstall. Ordinary host UI installation buttons still explicitly
use `dialog`. Script install defaults remain `auto`, which can install silently with a usable
privileged authorizer. Source retention/deletion and history behavior are unchanged.

Synchronous queries, session creation and `session.wait()` still cannot run on the UI thread.
Async results and session events are delivered on their originating script thread, and session
objects must be used on their creating thread. The new modes introduce no thread or caller-identity
exception to the V1 boundary.
