# DEX Compiler R5.3 V1.1 classpath representative matrix evidence

Date: 2026-08-27 (Asia/Shanghai)

Status: **PASS** for the bounded R5.3 three-cell representative matrix.

## Evidence boundary

This receipt expands the earlier single API 34/x86_64 V1.1 vertical slice to three authorized
real-provider cells: an API 24/x86 emulator, an API 34/x86_64 emulator, and an API 35/arm64-v8a
physical device. Every cell executed the exact instrumentation selector
`DexCompilerRealProviderAndroidTest#representativeRhinoClasspathEntryUsesRealProviderAndRetainsLegacyLoadJar`.
The method covers the public Rhino `runtime.loadJarWithClasspath()` entry, real provider dispatch,
ordered compile-only classpath, final `DexClassLoader` execution, exact V1.1 cache reuse, exclusion
of the classpath stub from emitted DEX, and an independent V1.0 `runtime.loadJar()` regression.

This is a three-cell R5.3 representative gate, not a replacement for the canonical seven-cell R1
matrix and not a claim about every Android build, vendor, ABI, or device.

## Source and APK identity

- Host commit: `b276708646b127e64b7f43af4ede94839004d0c9` (`test(dex): expand R5 classpath device coverage`).
- Plugin release-source commit: `e5804757d926d45db219fc2eb4b2b128714b4160`.
- Host build: AutoJs6 6.8.0, `versionCode=5276`, minSdk 24, targetSdk 36.
- Host and androidTest APKs were produced in the clean detached worktree
  `D:\idea-projects\AutoJs6-R5-Classpath` with
  `.\gradlew.bat :app:assembleAppDebug :app:assembleAppDebugAndroidTest --offline --no-daemon --console=plain`;
  the build completed successfully with 680 tasks.
- Plugin artifact: DEX Compiler v1.1.0 (`versionCode=5`), D8/R8 8.13.22.

| Role | Bytes | SHA-256 |
|---|---:|---|
| AutoJs6 universal Debug | 43,975,067 | `24f4ee855174b994d9e37472b48120b2059cc82f3e64507c447799b95dfcaa8f` |
| AutoJs6 androidTest | 1,508,760 | `2b82aabf644cc1c2cc2983faf902d57ca93975bfb59197245fca9e34eea2fdcb` |
| DEX Compiler v1.1.0 Release | 6,642,158 | `a8fe8b64723c0bae58c94a3129ce58043ea2cb2ef5a8b168b0a861f8d11b9205` |

All three APKs passed APK Signature Scheme v2 verification and have the same complete signer
certificate SHA-256:
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`.

## Final release-bound campaign

Campaign: `d9b97e77-84ad-41dd-a0f2-a8e08eef3fea`.

| Cell | Authorized target | API / ABI | Commands | Report SHA-256 | Manifest SHA-256 |
|---|---|---|---:|---|---|
| `release-api24-x86-avd-api24` | `emulator-5558`, `AVD_API_24` | 24 / x86 | 57 | `0e5b6837809dd3fe2a337c9e3e6c491ee0d8e5ac687cda807951de825891a30b` | `224ac68cad137098c819f4e2519039e01a3ee4e9bc8efcd5c7d1bb26f0dabe59` |
| `release-api34-x86-64-dex-r1` | `emulator-5560`, `DEX_R1_API34_X64` | 34 / x86_64 | 57 | `ea7cb80f115a3e321654a18e541bdd09c45569a7ebbe76d4a995adb6f87615b7` | `9ea5a7cbac13e753cef3cc993ee023c24943a2662444c9399abf614b65473e97` |
| `release-api35-arm64-physical-968e9f18` | `968e9f18`, Xiaomi 23046RP50C | 35 / arm64-v8a | 56 | `3248615df4e8f6dd0cbc6fa9d832341df0cf8ed2575b0682cf7af2bfe99300be` | `5d94c0556146c1f367c91e4a897229b34306aea15edba5656ae28bbc7be55ff3` |

Each cell reported exactly `OK (1 test)` and `INSTRUMENTATION_CODE: -1`. The aggregate verifier
accepted exactly these three reports and emitted `passed=true`, `cellCount=3`, with the expected
API 24/25 emulator, API 26+ emulator, and arm64 physical-device roles.

- Aggregate Gate:
  `D:\idea-projects\.bak\AutoJs6-DEX-R5-Evidence-20260827\matrix-gate-d9b97e77-84ad-41dd-a0f2-a8e08eef3fea.json`.
- Aggregate Gate bytes / SHA-256: 2,378 /
  `e2f8c722e2c539e08dd119b39d56af7a2d79581c32c3f50136939ebe43e03a24`.
- Cell runner SHA-256:
  `3b784a9cacca79990d971df531ccb3263927d6bff61218afd3a6a61f3c4d28d5`.
- Aggregate verifier SHA-256:
  `f1dbb4fcfd3d5256a1ee2bd5e5fc00b65bcfdbca455115bff84cac7abf091e76`.

The earlier campaign `614e0c1d-d2cb-48c2-a284-e95b634934c9`, which first proved the expanded
test against the pre-release v1.0.0 plugin package, remains preserved. The final campaign above is
the release-binding result because it uses the frozen v1.1.0 Release APK and the clean v1.1.0
source commit.

## Device safety and cleanup

- Every ADB command recorded by the cell runner was pinned to the caller-supplied exact serial.
- The host, test, plugin, and fake-provider packages were required to be absent before installation
  and absent again after cleanup. All three cells passed both checks with zero cleanup failures.
- `DEX_R1_API34_X64` was launched with `-no-snapshot-save`, then explicitly stopped after a final
  four-package absence check. The already-running `AVD_API_24` lifecycle was not otherwise changed.
- The physical target `968e9f18` was explicitly authorized as a backup device. Preferred device
  `QV710AF65F` was not connected during this campaign, so this receipt does not claim a QV710 run.
- No package, data, process, AVD snapshot, or host/plugin repository modification from the campaign
  remained after postflight cleanup.

Raw evidence is retained under
`D:\idea-projects\.bak\AutoJs6-DEX-R5-Evidence-20260827\classpath-release-*`. Each cell directory contains
the frozen APK copies, recorded command stdout/stderr, `report.json`, and a self-excluding
`files.sha256.json` manifest.
