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
so they still require negotiation. `installReason` and `packageSource` also accept explicit
JSON null to reset a source-profile value; their presence still requires V3 and the advanced
feature. Null for the other three advanced fields, numeric enum values, string booleans,
unknown enum strings and unknown script keys are rejected rather than coerced. Uninstall and
default-installer options do not acquire these installation-only fields.

Omission preserves existing behavior: no grant/update-ownership request, no extra manual dexopt,
no explicit install reason, and the existing local-file source attribution on API 33+.
`dexopt: 'none'` disables only the requested extra manual step, not Android's own compilation.
The new metadata does not change the trusted `sourceOrigin` host/script history field.

Capability advertisement means that the plugin understands these options; it does not grant
privileges or promise every option on every Android version. The plugin checks SDK, authorizer,
target user, installation state and policy before applying them, and rejects unsupported
requests without silently discarding flags or changing authorizer. Permission grants and update
ownership are requests to Android, not claims that all declared permissions or ownership were
acquired. Supported installer-changeable app-ops can also be affected, such as full-screen intent
access on Android 14. Accessibility, overlay and arbitrary signature permissions are not granted;
restricted/system-fixed/policy-fixed rules still apply, without an extra restricted-permission
allowlist flag. A false ownership option does not revoke an existing owner. Updates or a package
already installed in another user may cause Android to ignore the ownership request.
Android exposes install reasons from API 26, package-source metadata from API 33,
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

- `updateOwner` appears only after ownership was requested and a read completed for the calling
  identity. Null means Android returned no owner to that identity, either because none exists or
  because visibility/profile filtering hid it. It does not prove globally absent ownership.
  A failed read omits the field and records a note; omission also does not prove absence.
- `dexopt` appears only when the extra non-`none` step was requested. Its status is one of
  `accepted`, `failed`, `cancelled`, `timeout`, `unavailable`, or `unknown`. `accepted` includes
  a system skip and does not claim that compilation ran. `unknown` covers an unrecognized or
  lost result. The optional message describes the observation without changing its status.
- A post-install observation does not reverse an already confirmed installation. The host
  preserves the complete raw result document, including nested extension objects and explicit
  null, while keeping its normalized `ok` and error handling. These observations add no required
  result fields; the separately negotiated source-profile extension below has its own cleanup
  decision requirement.

## Source profiles within V3

`source-profiles` is an optional feature on the same live V3 Binder lease. The host sends
`applySourceProfiles: true` at the request root only after that lease advertises the feature.
The host UI/script implementation of this extension starts with host build 5312.
There is no new AIDL transaction or contract version. A discovery-only advertisement, V1/V2
peer, or V3 peer without the feature cannot opt in. Requests from older hosts lack this marker
and do not apply remote source profiles. Native host callers whose explicit field presence is
unknown also retain their original full request shape.

On an opted-in host/script request, `options` contains only explicitly supplied values for the
twelve per-package fields: `authorizer`, `allowDowngrade`, `allowTestOnly`, `bypassLowTargetSdk`,
`installer`, `user`, `deleteSource`, `grantAllRequestedPermissions`, `requestUpdateOwnership`,
`dexopt`, `installReason`, and `packageSource`. The host's `providedOptions` set is local state,
not a second wire field. Explicit false, `auto`, `current`, and `none` remain present and take
precedence over matching profile defaults. Only `installer`, `installReason`, and `packageSource`
allow an explicit null reset; `installer: ''` is the existing spelling of the same installer
reset. On older peers, the host sends the prior full/defaulted object and omits nullable resets
whose legacy meaning is already the default. Explicit advanced nulls still require the
advanced feature before that operation is sent.

`interaction` remains at the request root. `timeoutMillis` and `continueOnError` remain
session-wide options and are always encoded with their effective values. Profiles cannot
override them or restart/extend a batch deadline. Host UI requests keep `dialog`; script
requests keep the chosen interaction or `auto` when omitted.

The plugin owns profile storage, matching and validation. It freezes the selected configuration
when processing starts, then uses the real parsed package name and trusted origin to select
the first enabled matching entry in settings order. One entry provides defaults per package;
entries are not merged. Prefix matching is case-sensitive text matching, not filename matching.
Authorizer, target-user, SDK and mandatory local policy checks still apply to the final options.
No profile can grant itself a signature or blacklist exception.

Each successful item of an opted-in request must include a boolean `sourceDeleteRequested`,
including false. It reports the final effective request, not a claim that a file was deleted.
The host records the negotiation fact before calling `openSession`, so even a synchronous
terminal callback carries the correct local cleanup mode. A JSON field cannot forge that fact.

For both script and host UI file sources, the host performs requested deletion after complete
item results, on an IO worker and before closing its descriptors. Explicit script
`deleteSource: false` is a hard veto. A missing or non-boolean cleanup decision retains the file
and adds a note without changing confirmed installation success. Content URI ownership is never
assumed. Unsuccessful, unprocessed, and successful-but-retained items protect any shared path or
provable regular-file alias from another item's cleanup. Identity/replacement checks still run
immediately before deletion, and `sourceDeleted` is true only when all owned sources of that
item were safely removed. Without this negotiated extension, the existing explicit deletion
behavior remains in force and the new result field does not authorize cleanup.

## Local installation policy

Signature checks remain a plugin-local gate, and blacklist rules are local settings. Rejection uses the existing
`BLOCKED_BY_POLICY` code, with a message identifying the reason, so original hosts do not reduce
an unfamiliar code to `INTERNAL`. Only the plugin's one-time dialog confirmation may override a
signature-policy decision for that item. A blacklist decision is not overridable. The script
options contain no bypass field or reusable authorization token. Platform signature validation
still applies after any local confirmation. Source-profile defaults cannot bypass this gate.

Installed-signature queries using none/Dhizuku cover the current user; Shizuku/Root can query
globally. With nonempty SharedUID rules, an absent current-user package cannot exclude an
existing record in another user, so an unverifiable global state is blocked without override.

Public documentation and declarations are synchronized after the plugin's backend and device
semantics settle; this contract change is not a release or device-matrix announcement.
