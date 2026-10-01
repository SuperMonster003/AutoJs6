# Installer Plugin Protocol V3

Date: 2026-10-02. This extends [V2](installer-plugin-protocol-v2.md) with advanced installation
options and optional post-install observations. All eleven `IInstallerPlugin` transactions,
the other AIDL interfaces, descriptor ownership, caller guards and JSON ceilings are unchanged.
The original discovery minimum remains host build 5299; the V3 script implementation starts
with host build 5308. Availability still requires a plugin implementing the requested feature.

## Negotiation and request shape

`InstallerContract.MIN_CONTRACT_VERSION=1`, `MAX_CONTRACT_VERSION=3`, `CONTRACT_VERSION=3`.
A plugin compatible with old hosts advertises `installerContractVersion=1` and
`installerMaxContractVersion=3`. A new host negotiates the lower supported maximum, and old
hosts continue to select V1/V2. Existing result envelopes may retain version 1 because their
structure has not changed. No unknown AIDL transaction is attempted to probe support.

Every supplied option below requires both negotiated V3 and the live
`advanced-install-options` feature from the same Binder lease that receives `openSession`.
An INFO/discovery capability from another connection is insufficient. Version 3 alone,
or a feature advertised by a V1/V2 peer, is insufficient. Missing support raises
`INVALID_ARGUMENT` before sending the operation. Existing source cleanup on failure remains.

The fields live inside the existing `options` JSON object:

| Field | Wire type and closed values |
| --- | --- |
| `grantAllRequestedPermissions` | Boolean |
| `requestUpdateOwnership` | Boolean |
| `dexopt` | `none`, `verify`, `speed-profile`, `speed` |
| `installReason` | `unknown`, `device-restore`, `device-setup`, `user`, `policy` |
| `packageSource` | `unspecified`, `store`, `local-file`, `downloaded-file`, `other` |

The host retains a separate omitted state for all five fields and does not encode missing
fields. Explicit `false`, `none`, `unknown` and `unspecified` are still supplied V3 requests,
so they still require negotiation. Explicit JSON null, numeric enum values, string booleans,
unknown enum strings and unknown script keys are rejected rather than coerced. Uninstall and
default-installer options do not acquire these installation-only fields.

Omission preserves existing behavior: no grant/update-ownership request, no extra manual dexopt,
no explicit install reason, and the existing local-file source attribution on API 33+.
`dexopt: 'none'` disables only the requested extra manual step, not Android's own compilation.
The new metadata does not change the trusted `sourceOrigin` host/script history field.

Capability advertisement means that the plugin understands these options; it does not grant
privileges or promise every option on every Android version. The plugin checks SDK, authorizer,
target user, installation state and policy before applying them, and rejects unsupported
requests without silently discarding flags or changing authorizer. Runtime permission requests
and update ownership are requests to Android, not claims that all permissions or ownership
were acquired. Android exposes install reasons from API 26, package-source metadata from API 33,
and update-ownership requests from API 34; ownership enforcement can only start on an initial
installation. See the official [SessionParams reference](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams).

## Stages and optional observations

`optimizing` is an additional nonterminal stage after `committing`. It is sent only for V3
requests containing a non-`none` dexopt option; V1/V2 sessions retain their prior stage vocabulary.
Existing terminal states are unchanged. No stage is evidence that the platform performed a
particular amount of compilation.

Install results may include:

```json
{
  "ok": true,
  "updateOwner": null,
  "dexopt": {
    "filter": "speed-profile",
    "status": "accepted",
    "message": "Optional bounded platform detail"
  }
}
```

- `updateOwner` appears only after ownership was requested and its actual state was read back.
  A successful read with no owner uses explicit null. A failed read omits the field and records
  a note; omission must not be interpreted as proof that no owner exists.
- `dexopt` appears only when the extra non-`none` step was requested. Its status is one of
  `accepted`, `failed`, `cancelled`, `timeout`, `unavailable`, or `unknown`. `accepted` includes
  a system skip and does not claim that compilation ran. `unknown` covers an unrecognized or
  lost result. The optional message describes the observation without changing its status.
- A post-install observation does not reverse an already confirmed installation. The host
  preserves the complete raw result document, including nested extension objects and explicit
  null, while keeping its normalized `ok` and error handling. There are no new required result
  fields or new Kotlin `InstallResult` constructor arguments.

## Local installation policy

Signature and blacklist policy remain local plugin settings. Their rejection uses the existing
`BLOCKED_BY_POLICY` code, with a message identifying the reason, so original hosts do not reduce
an unfamiliar code to `INTERNAL`. Only the plugin's one-time dialog confirmation may override a
signature-policy decision for that item. A blacklist decision is not overridable. The script
options contain no bypass field or reusable authorization token. Platform signature validation
still applies after any local confirmation. This protocol adds no per-source profile behavior.

Public documentation and declarations are synchronized after the plugin's backend and device
semantics settle; this contract change is not a release or device-matrix announcement.
