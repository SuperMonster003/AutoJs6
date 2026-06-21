# Runtime Provenance

Phase 9 tracks runtime provenance separately from runtime promotion. The goal is
to make every source, fork, patch point, and native artifact auditable before
Node 24.17 can become the default embedded runtime.

## Node 24.17 Upstream Baseline

- repository: <https://github.com/nodejs/node>
- tag: <https://github.com/nodejs/node/tree/v24.17.0>
- release: <https://github.com/nodejs/node/releases/tag/v24.17.0>
- commit: <https://github.com/nodejs/node/commit/413e874773eef1e27a8397ddc949dbbe19cadb31>
- commit SHA: `413e874773eef1e27a8397ddc949dbbe19cadb31`
- tag object: `9c9a7894a60efe6ef9084ec9796a36f0b8091408`
- release kind: Node 24.17.0 `Krypton` LTS security release
- published: `2026-06-18T04:37:35Z`
- source archive SHA-256:
  `a7ab562ed2369a29c68b72fa00e3103bcdfe37063dff799c6acc8e404e275fcd`
- headers archive SHA-256:
  `aab64d32cd1690e4027326e746877bdac62f0a8458215241638477cbfe0a4192`

## Android Fork Baseline

- fork repository: <https://github.com/degaso/nodejs-mobile>
- original repository: <https://github.com/nodejs-mobile/nodejs-mobile>
- local path: `D:/idea-projects/nodejs-mobile`
- current branch: `update-v24.5.0`
- current tag: `v24.5.0-r1`
- current commit: `116bad1dc919702d4a701d49df26d960403ee4a4`
- current commit date: `2026-03-22T20:53:16+01:00`

Current checked Android artifact:

- release: <https://github.com/degaso/nodejs-mobile/releases/tag/v24.5.0-r1>
- asset: `nodejs-mobile-24.05-android.zip`
- URL: <https://github.com/degaso/nodejs-mobile/releases/download/v24.5.0-r1/nodejs-mobile-24.05-android.zip>
- size: `90705528`
- SHA-256:
  `02e541cc42e7b61bc4d514e4ef2354abb1d46bcf024dd58ce4fbf6b4cdc3a128`
- ABI count: `3`

## Current Decision

The degaso Android artifact is the current Node 24.5 source artifact, not the
Node 24.17 promotion artifact. Node 24.17 still requires an Android port,
runtime artifacts, per-ABI `libnode.so` SHA-256 values, Build IDs, and device
validation before default promotion.

Host-side provenance check:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRuntimeBuildPlan
```

Generated reports:

- `build/reports/nodejs/runtime-build-plan.json`
- `build/reports/nodejs/runtime-build-plan.md`
