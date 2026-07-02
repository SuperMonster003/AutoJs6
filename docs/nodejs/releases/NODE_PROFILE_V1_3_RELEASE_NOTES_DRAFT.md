# Node Profile v1.3 Release Notes Draft

P15-26 records v1.3 migration and Pro comparison docs as ready. Release-note
claims match executable evidence and do not imply raw Android object parity.
The v1.3 release-note state has no newly promoted providers, and runtime
authority remains unchanged.
The v1.3 release-note state records that runtime authority remains unchanged.

## Claim Areas

- Pro UI and floaty: live UI and overlay remain `provider_poc_not_promoted`
  until device, disclosure, cleanup, packaged, security, TypeScript
  declarations, migration, and rollback evidence pass.
- accessibility: accessibility remains `provider_poc_not_promoted` until
  service-enabled device archives, selector/action lifecycle proof, disclosure,
  security corpus, packaged behavior, and rollback evidence pass.
- screenshot/OCR: MediaProjection and image/OCR remain
  `provider_poc_not_promoted`; capture, raw image handles, OCR provider modes,
  and packaged OCR claims stay tied to executable evidence.
- tasks: task database, schema migration, packaged task scheduling, and
  IntentTask/broadcast remain `provider_poc_not_promoted`.
- notifications: notification ownership and settings/power remain
  `provider_poc_not_promoted`.
- recorder: recorder sessions and packaged recorder stress remain
  `provider_poc_not_promoted`.
- MediaStore/playback: playback, playlists, MediaStore query/mutation, broad
  media permissions, and raw media handles remain `stable_denied`.
- security corpus: P15-23 is
  `security_corpus_updated_no_authority_promoted`.
- packaged behavior: P15-24 is `host_contract_ready`, not real device
  install-run release approval.
- TypeScript declarations: `NodeProfileV13CapabilityCatalog` is metadata-only.
- rollback: Safe downgrade, profile rollback, packaged metadata mismatch,
  provider disable flags, and release-note claim rollback remain required.

No v1.3 migration, Pro comparison, compatibility, example, or release-note row
grants raw `Context`, raw `Activity`, raw `View`, raw `Bitmap`, raw `Intent`,
raw `MediaProjection`, raw `MediaRecorder`, raw `TimedTask`, raw
`BroadcastReceiver`, raw `Notification`, raw `WakeLock`, raw
`ContentResolver`, raw `MediaStore`, raw file descriptors, or Java objects.
Release notes grant no raw `BroadcastReceiver` or raw `ContentResolver`
authority.
