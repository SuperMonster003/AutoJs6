# Official Plugin Settings Contract V1

This document defines the versioned, read-only settings snapshot that AutoJs6 exposes to trusted
official plugins. The normative constants live in
`plugin-api:common-plugin-api` as `AutoJs6HostSettingsContract`.

## Scope

V1 exposes only an allowlisted appearance snapshot:

- Host version identity.
- Primary, primary-dark, and accent colors as Android ARGB integers.
- The configured dark-mode policy and the currently resolved dark-mode state.
- The configured language tag and the currently resolved compatible language tag.

The contract does not expose arbitrary preference keys, credentials, file paths, script content,
account data, or mutable host state. It has no write operation.

## Caller Admission

The provider authority is `org.autojs.autojs6.plugin.settings`. AutoJs6 admits a caller only when
all of these checks pass for the Binder calling UID:

1. The framework calling package belongs to the Binder UID.
2. The plugin application is enabled.
3. The package exposes an enabled and exported `org.autojs.plugin.INFO` service protected by
   `org.autojs.permission.PLUGIN`.
4. AutoJs6 classifies the package signer as an official plugin signer.
5. Android grants the provider-level `org.autojs.permission.PLUGIN` signature permission.

Failure is reported as a `SecurityException`. A plugin must not interpret denial as permission to
read AutoJs6 private preferences by another mechanism.

## Invocation

Official plugins should compile against the matching `common-plugin-api.aar` and call the provider
without binding a service:

```kotlin
import android.net.Uri
import org.autojs.plugin.common.api.AutoJs6HostSettingsContract as Contract

val result = contentResolver.call(
    Uri.parse(Contract.CONTENT_URI),
    Contract.METHOD_GET_SETTINGS,
    null,
    null,
) ?: return

if (result.getInt(Contract.KEY_PROTOCOL_VERSION) != Contract.PROTOCOL_VERSION) return
```

The application manifest must request `org.autojs.permission.PLUGIN`. The package also needs its
normal official plugin information service declaration; the settings provider does not replace the
plugin discovery contract.

## Result Bundle

`getSettings` returns these Bundle values:

| Key constant | Bundle type | Meaning |
| --- | --- | --- |
| `KEY_PROTOCOL_VERSION` | `Int` | Exact settings protocol version. V1 is `1`. |
| `KEY_HOST_PACKAGE_NAME` | `String` | AutoJs6 application ID. |
| `KEY_HOST_VERSION_CODE` | `Long` | Installed AutoJs6 version code. |
| `KEY_HOST_VERSION_NAME` | `String` | Installed AutoJs6 version name. |
| `KEY_THEME_COLOR_PRIMARY` | `Int` | Primary ARGB color. |
| `KEY_THEME_COLOR_PRIMARY_DARK` | `Int` | Primary-dark ARGB color. |
| `KEY_THEME_COLOR_ACCENT` | `Int` | Accent ARGB color. |
| `KEY_DARK_MODE_POLICY` | `String` | `FOLLOW_SYSTEM`, `LIGHT`, or `DARK`. |
| `KEY_DARK_MODE_ACTIVE` | `Boolean` | Whether dark mode is active at snapshot time. |
| `KEY_LANGUAGE_TAG` | `String` | Configured AutoJs6 language tag. |
| `KEY_RESOLVED_LANGUAGE_TAG` | `String` | Language tag resolved for the current device. |

The Bundle is a point-in-time snapshot. Plugins should query again when they resume if their UI has
a Follow AutoJs6 mode. V1 does not define a change listener.

## Availability And Fallback

A client should distinguish these states:

- AutoJs6 is not installed.
- AutoJs6 is installed but disabled.
- The installed AutoJs6 predates this contract or the call otherwise fails.
- A compatible snapshot is available.

When AutoJs6 is absent or disabled, a Follow AutoJs6 choice should remain visible for context but
be disabled, and the plugin should switch to an appropriate application-level default. A transient
contract failure should also use safe application defaults, but clients may retain the stored Follow
AutoJs6 preference so it can recover after a host update.

Clients must reject unknown protocol versions and malformed or incomplete snapshots. New optional
keys may be added within a compatible version, while changing existing key types or meanings
requires a new protocol version.
