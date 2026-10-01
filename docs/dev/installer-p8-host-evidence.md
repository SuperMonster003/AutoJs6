# Installer P8 host compatibility evidence

Date: 2026-10-02, Asia/Shanghai. This host change implements the V2 contract and script options
described in [Installer Plugin Protocol V2](installer-plugin-protocol-v2.md). It does not claim
that the plugin's Dhizuku/device-owner, notification or persistent-default device matrix has
passed; those backend and device results are maintained in the plugin repository.

- The V1 AIDL first ten transactions and all other interface orders remain frozen. Only
  `setDefaultInstallerV2(boolean, Bundle, IInstallerCallback)` is appended.
- The baseline capability remains compatible with original host build 5299. The optional maximum
  version enables V2, and each operation checks the live capabilities of the Binder that receives
  it. No failed-transaction probing is used. Preferred defaults retain the old request shape;
  persistent defaults and notification installation require their separate feature names.
- Dhizuku must be explicitly advertised and negotiated. The script layer accepts its name, but
  does not assert Root-equivalent privileges or bypass the plugin's owner/profile restrictions.
- `setDefault` keeps its boolean result. Persistent success checks the requested scope, including
  disabling a persistent lock while an ordinary preferred handler remains. Unknown V2 result
  methods do not establish successful removal.
- Notification is installation-only; uninstall continues to reject it. Notification-unavailable
  errors keep their stable V2 code, while the original host UI routes still use `dialog`.

## Validation

The installer-api module's seven vocabulary/ceiling tests and one AIDL order test pass with no
failures or skips. Its signed-independent release AAR assembly passes. The distribution artifact
is `plugin-api/installer-api/build/outputs/aar/installer-api-release.aar`, 31,624 bytes, SHA-256
`2de14168b579748a5e8e72605062190ab0328bf572932185af0fdc4f3bf72b5f`. It contains no host build number;
the plugin stages this audited release AAR and updates its own hash lock and notices separately.

`build/p8-installer-host-final.log` records the final build 5306 verification: 91 installer-related
JVM tests, no failures/errors/skips, App Debug and androidTest assembly, and Inrt Debug Kotlin
compilation passed. The peer tests cover V1 rejection of advertised-but-unnegotiated extensions,
independent feature gates, missing Dhizuku, invalid/overflowing version ranges, future maximum
negotiation, and unknown values. Codec/script tests cover legacy preferred JSON, persistent JSON,
strict option types and mode-specific booleans. An initial run retained a V1-only test expectation
that `notification` and `dhizuku` were unknown; the obsolete expectations were replaced with
actually unknown strings, and the final entire installer selection passed.

Ten-language changelog source and 32 associated generated artifacts were updated using only the
repository generator's pure rendering functions. The verification run reports zero differences.
README changes are limited to the installer entry and current development-version date, preserving
unrelated existing history differences and avoiding network metadata refreshes. `git diff --check`
passes. No additional device mutation was needed for the host-only capability tests.

## Version and workspace boundary

The host started clean at `5120885724`, build 5305. Its historical Git reachable-commit count was
3406, so its existing build sequence advances to 5306; the plugin repository's separate equality
rule must not lower the host below its already-published compatibility threshold. The host's own
AGENTS.md permits this build sequence, and the coordinating agent confirmed it.

This logical commit includes only the installer API/client/script/tests/protocol evidence,
installer changelog renderings and version file. The parallel APK Inspector work's
`docs/dev/package-inspection-roadmap.md` is excluded. Public documentation and declaration/Offline
Docs/Ace synchronization follow as separate repository changes. All commits remain local, with
no push, tag, package publication or GitHub Release.
