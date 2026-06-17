# Node Profile v1.1 Alpha Gate

This report defines the functional alpha milestone gate for Node Profile v1.1.
It is not a release candidate, tag, or production-identity gate.

Run it on the unattended Sony device:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11AlphaGate "-Pautojs.nodejs.test.deviceProfile=v1_1_alpha"
```

The gate writes:

```text
build/reports/nodejs/v1.1-alpha-summary.json
```

Required coverage:

- ESM partial tests and source-map stack shape.
- v1.1 npm corpus and desktop differential report.
- Controlled `fetch` and WebSocket bridge tests.
- Expanded AutoJs bridge suites for OCR, image, storage, notifications, sensors, UI, shell, accessibility selector, and `work_manager`.
- Packaged APK v1.1 bridge smoke for controlled `fetch`, OCR, storage, UI, and `work_manager`.
- Phase 5 security corpus.
- Bridge resource leak stress plus the existing memory leak stress subset.
- Worker design gate by default; worker experimental and resource stress tests only when `autojs.nodejs.worker_threads.experimental=true`.

Default stress arguments for this profile are intentionally shorter than nightly:

```text
autojs.nodejs.leak.iterations=12
autojs.nodejs.leak.imageIterations=6
autojs.nodejs.leak.faultIterations=3
autojs.nodejs.bridgeStress.iterations=8
```

Pass the same Gradle properties to raise or lower the budget for a local run.

The summary is intentionally measurable without creating a release tag. It records
the commit, dirty state, selected profile, latest connected-test XML counts,
packaged smoke host-report availability, differential report paths, and fuzzer
seed count.

Packaged `.mjs` and dynamic import smokes remain explicit template-override
subgates until the refreshed inrt template is the default packaged template.

## Initial Validation

Validated on 2026-06-18 Asia/Shanghai against Sony XQ-AT72 `QV710AF65F`:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11AlphaGate "-Pautojs.nodejs.test.deviceProfile=v1_1_alpha"
```

Result:

- Build: passed.
- Connected instrumentation: 126 tests, 0 failures, 0 errors, 0 skipped.
- Summary JSON: `build/reports/nodejs/v1.1-alpha-summary.json`.

The default alpha gate intentionally keeps packaged `.mjs` and dynamic import
template-override smokes outside the profile until the refreshed inrt template is
the default packaged template.
