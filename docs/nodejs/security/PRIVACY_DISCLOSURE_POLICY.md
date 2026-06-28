# Node Privacy Disclosure Policy

Schema: `autojs6-node-privacy-disclosure-policy-v1`

P13-52 defines the prompt, disclosure, Android permission, and packaged APK
review requirements for sensitive Node profile surfaces. The policy is
intentionally `policy_partial` and `metadata_only_no_authority`: it documents
what must be shown to users before Pro/Desktop/Debug promotion, but it does not
grant runtime authority.

## Runtime Contract

- Effective profile remains `safe_default`.
- `implicitHighPrivilegeDenied=true` blocks silent high-privilege promotion.
- `packagedImplicitHighPrivilegeDenied=true` blocks packaged APKs from entering
  a higher-privilege state only because metadata is present.
- `requiresUserPrompt=true` is required for every sensitive surface listed
  below before that surface can be promoted.
- Foreground service notification/disclosure is required for screen capture,
  media recording, overlay, root shell, and inspector-style debug sessions.
- `grantsAuthority=false` and `implicitGrantAllowed=false` are invariant for
  this policy gate.

## Sensitive Surfaces

| Surface | Capability | Target profile | Prompt | Foreground disclosure | Android permission or setting |
| --- | --- | --- | --- | --- | --- |
| `screen_capture` | `screen_capture` | `pro_compat_opt_in` | required | required | MediaProjection prompt and foreground service permissions |
| `media_recording` | `media.recording` | `pro_compat_opt_in` | required | required | `RECORD_AUDIO` and microphone foreground service |
| `ui_overlay` | `ui.overlay` | `pro_compat_opt_in` | required | required | `SYSTEM_ALERT_WINDOW`, special-use foreground service, notification |
| `accessibility` | `accessibility` | `pro_compat_opt_in` | required | required | accessibility settings disclosure |
| `app_query` | `app.query` | `pro_compat_opt_in` | required | not required by default | project capability and packaged metadata review |
| `network` | `network` | `desktop_compat_opt_in` | required | not required by default | `android.permission.INTERNET` |
| `raw_network` | `raw_network` | `desktop_compat_opt_in` | required | not required by default | `android.permission.INTERNET` and packaged raw network review |
| `filesystem_relaxation` | `filesystem_relaxation` | `desktop_compat_opt_in` | required | not required by default | SAF/user-authorized roots before any expansion |
| `java_interop` | `java_interop` | `pro_compat_opt_in` | required | not required by default | project capability review |
| `shell_root` | `shell.root` | `pro_compat_opt_in` | required | required | root access disclosure |
| `shell_shizuku` | `shell.shizuku` | `pro_compat_opt_in` | required | not required by default | Shizuku permission dialog |
| `child_process` | `child_process` | `desktop_compat_opt_in` | required | not required by default | project capability review |
| `native_addon` | `native_addon` | `desktop_compat_opt_in` | required | not required by default | signed native artifact review |
| `inspector` | `inspector` | `debug_unsafe_lab` | required | required | explicit debug-session confirmation |
| `wasi` | `wasi` | `desktop_compat_opt_in` | required | not required by default | packaged metadata review |

## Packaged APK Policy

Packaged APKs must provide a reviewed disclosure manifest before any sensitive
surface is promoted. P13-52 reserves
`autojs6-node-packaged-disclosure-manifest-v1` with these fail-closed rules:

- `requiresDisclosureManifest=true`
- `reviewedManifestRequiredForPromotion=true`
- `denyUndeclaredHighRiskSurfaces=true`
- `permissionSynthesisRequiresReviewedDisclosure=true`
- `defaultAction=deny_without_reviewed_disclosure_manifest`

The reserved metadata keys are `project.json:node.disclosures` and
`package.json:autojs6.node.disclosures`. They are diagnostic-only until a later
packaged review UI and installer/runtime enforcement path lands.

## Doctor And Script Diagnostics

`NodePrivacyDisclosurePolicy` feeds both Node Doctor and
`require("autojs6:profile").privacyDisclosurePolicy`. Doctor output keeps
script contents and environment values redacted while reporting prompt and
foreground disclosure counts.

## Automation And Rollback Disclosure

Automatic crash circuit breaker evidence is a future implementation input, not
a new disclosure bypass. P13-52 records the privacy implication as
`metadata_only_no_script_contents`: crash evidence may drive Safe-profile
fallback, but script contents and environment values remain excluded and paths
stay redacted. Re-enabling a disabled sensitive surface still requires the
normal user disclosure path.

Remote packaged kill switches are rejected for v1.1. Packaged rollback uses the
P13-51 Safe-profile patch or promoted-metadata removal path, so there is no
remote packaged kill-switch disclosure prompt to expose in this policy version.

## Remaining Work

- Settings UI disclosure review and localized prompt copy.
- Foreground service notification ownership for promoted screenshot, recording,
  overlay, root shell, and inspector surfaces.
- Packaged disclosure manifest authoring/review UI.
- Device matrix for screenshot, recording, overlay, and accessibility flows.
- Automatic enforcement before any profile promotion.
