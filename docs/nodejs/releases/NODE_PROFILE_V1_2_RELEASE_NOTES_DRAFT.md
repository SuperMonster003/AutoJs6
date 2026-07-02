# Node Profile v1.2 Release Notes Draft

Status: draft for RC planning; not release tag approval.

Node Profile v1.2 keeps the Safe profile as the default runtime profile. Safe profile remains default for release-candidate planning. The
Phase 14 summary records `phase14_summary_gate_ready` with zero open final
blockers, zero policy violations, and zero release-blocking reports.

## Included Status

- `provider_poc_not_promoted`: Pro UI/floaty, accessibility, screenshot/OCR,
  tasks, notifications, power, recorder/media, MediaStore, and related
  providers have planning or POC evidence, but their future-only authority is
  not promoted for user scripts.
- `worker_intentionally_unsupported_v1_2`: runnable native `worker_threads`
  remains unavailable on current target ABI evidence.
- `host_contract_ready`: packaged aggregate smoke contracts are deterministic
  on the host, but production packaged install-run evidence is still required
  before any tag approval.
- `phase14_summary_gate_ready`: the closeout classifier separates required
  v1.2 blockers from future backlog.

## Boundaries

These release notes do not imply raw Android object parity. Raw `Context`,
`Activity`, `View`, `Bitmap`, `Intent`, `MediaProjection`, `MediaRecorder`,
`TimedTask`, `BroadcastReceiver`, `Notification`, `WakeLock`, and Java object
handles remain denied unless a future provider gate explicitly promotes a
scoped handle boundary.

The v1.3 future promotion backlog contains the high-privilege provider work
that remains classified as `future_promotion`, `external`, or
`intentional_non_goal` in Phase 14 reports.
