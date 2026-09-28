# Accessibility Companion Contract V1

This document defines how AutoJs6 cooperates with official companion plugins that ship their own
accessibility service, such as `AutoJs6-Plugin-Three-Adapt-A11y`. The normative constants live in
`plugin-api:common-plugin-api` as `AutoJs6AccessibilityCompanionContract`.

## Scope

A companion is an official-signed plugin whose `org.autojs.plugin.INFO` service carries the category
`org.autojs.plugin.category.ACCESSIBILITY_COMPANION` and whose `PluginInfo.capabilities` contain:

| Key | Type | Meaning |
| --- | --- | --- |
| `org.autojs.plugin.accessibility.compat.SERVICE_COMPONENT` | `String` | Flattened `package/class` of the companion accessibility service; must belong to the plugin package |
| `org.autojs.plugin.accessibility.compat.SERVICE_POLICY` | `String` | `follow-host`, `enabled`, or `disabled` |
| `org.autojs.plugin.accessibility.compat.CONTRACT_VERSION` | `Int` | Companion contract version; `3` is the first version that publishes the policy |
| `org.autojs.plugin.accessibility.compat.MODE` | `String` | Informational companion mode |
| `org.autojs.plugin.accessibility.compat.TARGET_PACKAGES` | `ArrayList<String>` | Informational target packages |

Plugins that predate the contract publish no policy and are left alone.

## Host behavior

`AccessibilityCompanionSync` runs a pass at application start and, debounced, whenever the host
accessibility service binds or unbinds (`A11yServiceRegistry` binding changes):

1. Discover companion packages and require `PluginTrustManager.resolveTrustInfo(...).isOfficial`.
2. Read the capabilities through `PluginInfoServiceHost.discover`.
3. For `follow-host` companions only, compare the companion service with the *listed* state of the
   host service (`enabled_accessibility_services`), not with its binding, so that a host restart does
   not detach the companion. Attach or detach the companion with the host's own
   `SettingsStoreStartStrategy` instances (root, secure settings, Shizuku), each gated by the same
   preferences the host uses for itself. When no strategy is available nothing is written.
4. Send `org.autojs.plugin.action.ACCESSIBILITY_SERVICE_STATE_CHANGED` to each companion package
   with `sendBroadcast(intent, "org.autojs.permission.PLUGIN")`, `FLAG_INCLUDE_STOPPED_PACKAGES`, and
   the extras `hostPackageName`, `hostServiceComponent`, `hostServiceEnabled`, `hostServiceBound`.

The host never writes any other secure setting and never changes `enabled` or `disabled`
companions; those choices belong to the companion's own UI.

## Companion behavior

The companion may also keep itself aligned (content-triggered job on the enabled services setting,
its own strategies, `disableSelf()`), so that it degrades gracefully with hosts that lack this
logic. Both sides act idempotently on the enabled services list, so concurrent passes converge.

## Security

- Only official-signed plugins are managed; the receiver in the companion is protected by the
  signature permission `org.autojs.permission.PLUGIN`, so only AutoJs6 and official plugins can send
  the broadcast.
- The service component must belong to the plugin package; anything else is rejected by
  `AccessibilityCompanionPolicy.companionOf`.
- Discovery uses the existing `<queries>` entry for `org.autojs.plugin.INFO`.

## Tests

`AccessibilityCompanionPolicyTest` covers capability validation and the attach/detach decision.
Strategy behavior is covered by `SettingsStoreStartStrategyTest`.
