# ACE Release Publishing Cleanup

This document records Phase 9 Gate 10 publishing cleanup findings. It is an
audit, not the final cleanup commit. The ACE branch still uses a temporary app
id and label so it can be installed next to other AutoJs6 test branches on the
same device.

## Temporary Package And Label

Current branch values:

- Official application id constant: `org.autojs.autojs6`
- Temporary application id constant: `org.autojs.autojs6.ace`
- `defaultConfig.applicationId`: `temporaryApplicationId`
- Main app label: `AutoJs6 ACE`
- Inrt label placeholder: `AutoJs6 ACE.$flavorNameInrt`
- File provider authorities use the temporary application id.

Impact:

- The temporary application id and app label affect all `app` variants because
  they live in `defaultConfig` and main resources.
- This includes release builds unless a later cleanup commit restores the
  official id/label or scopes the temporary values to debug/test-only variants.
- This is intentional during Sony XQ-AT72 side-by-side validation.

Required final cleanup before merge or non-test release:

- Restore `defaultConfig.applicationId` to `globalApplicationId`, or move the
  temporary id into debug/test-only variant configuration.
- Restore the main `app_name` string to the official label.
- Restore app and inrt provider authorities to match the intended release
  application ids.
- Re-run `node tools/ace-ci/check-ace-release-gate.mjs`.

## `version.properties`

Current working-tree change:

- `BUILD_TIME` changed from `1781494923681` to `1781526951814`.
- `VERSION_BUILD` changed from `3819` to `3822`.

Source:

- `build-logic/convention/src/main/kotlin/org/autojs/build/Versions.kt` updates
  `BUILD_TIME` after assemble tasks.
- The same code increments `VERSION_BUILD` when the build-time gap is greater
  than 0.75 hours and the requested task is not `assembleAppRelease` or
  `assembleInrtRelease`.

Handling:

- Treat this as build-generated churn during ACE development.
- Do not commit it with ACE editor code, docs, or release-gate changes.
- If a release version bump is needed, commit `version.properties` separately
  with an explicit release-version message.

## Default Editor And Experimental Features

Confirmed:

- Native is still the static default editor:
  `default_key_editor_engine` points to `key_editor_engine_native`.
- ACE remains an explicit opt-in editor entry.
- LSP remains default-off:
  `AceEditorLspPreferences.isEnabled()` uses `Pref.getBoolean(..., false)`.
- VisualEditorAce has no public enum, settings entry, or editor menu item.

Device note:

- The Sony XQ-AT72 test install currently has `key_$_editor_engine=ace` because
  ACE is explicitly enabled for the device gate. This does not change the
  static default for a fresh install.
