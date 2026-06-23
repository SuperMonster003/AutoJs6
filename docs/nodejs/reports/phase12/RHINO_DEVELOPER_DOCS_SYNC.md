# Rhino Developer Documentation Sync

Status: P12-34 formal migration and Pro 9 comparison documentation sync landed.

P12-34 publishes the completed Phase 12 Rhino compatibility results in the
developer-facing documentation set. The goal is to keep promoted APIs visible to
script authors, keep rejected APIs tied to stable error codes, and avoid
calling completed P12 surfaces provider gaps.

## Promoted API Examples

The formal migration guide now includes examples for:

- `compat.keys` and `compat.events.keys` key constants;
- `compat.tasks.addDisposableTask()`, `removeTask()`, and one-shot WorkManager
  migration;
- direct `require("barcode")`, `compat.barcode`, and QR-only `compat.qrcode`;
- `require("s13n").bytes(...)` and `compat.s13n.bytes(...)`.

## Stable Rejections

The documentation keeps these stable rejection paths visible:

- `rhino.run()` without `experimental: true` rejects with
  `ERR_AUTOJS6_RHINO_SHIM_DISABLED`;
- `rhino.install()` throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`;
- `s13n.color`, `s13n.point`, `s13n.throwable`, and `s13n.time` reject with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`;
- raw task object mutation and exact Rhino-style raw window/thread targets use
  `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN` where a callable compatibility
  stub exists.

## Surface Status

P12-34 moves `dual_engine.device_evidence` from `provider_poc` to `promoted` in
the Phase 12 surface manifest. The fake device evidence remains test-only and
does not enable new Java interop, Rhino global installation, or live provider
authority.

## Evidence

- Formal migration guide:
  `docs/nodejs/MIGRATION_FROM_RHINO.md`
- Pro 9 comparison:
  `docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md`
- Compatibility matrix:
  `docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`
- Security model:
  `docs/nodejs/SECURITY_MODEL.md`
- Testing guide:
  `docs/nodejs/TESTING.md`
- Type declaration README:
  `docs/nodejs/types/autojs6-node/README.md`
- Surface manifest:
  `docs/nodejs/rhino-compat/phase12-surface-manifest.json`
  `docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md`
- Verification script:
  `tools/nodejs/phase12/verify-rhino-compat-developer-docs-sync.js`
- Gradle gate:
  `:app:verifyNodeRhinoCompatDeveloperDocsSync`
