# Embedded Node Native Crash Diagnostics

This page describes the release artifacts and device logs needed to diagnose native crashes in the Embedded Node runtime.

## Symbol Archive Report

Generate the native symbol archive report before publishing a release candidate:

```powershell
.\gradlew.bat --console=plain :app:nativeNodeSymbolArchiveReport
```

The report is written to:

```text
app/build/reports/native-node/native-symbol-archive-appDebug.txt
```

For each supported ABI, the report records:

- `libnode.so` source, merged, and stripped paths
- `libnode.so` SHA-256
- `libnode.so` Build ID when present
- `libautojs6-node.so` CMake, merged, and stripped paths
- `libautojs6-node.so` SHA-256
- `libautojs6-node.so` Build ID when present
- whether each file still contains `.debug_*`, `.symtab`, or `.dynsym` sections
- the best local symbol file path when debug or full symbol table data is available

Keep this report with the APK, exact git commit, release flavor, and ABI split metadata. The runtime probe log also prints:

```text
libnode.so.sha256=...
libautojs6-node.so.sha256=...
```

Use these hashes to match a tombstone or logcat crash to the exact native libraries in the report.

## Release Gate

The local debug gate includes the symbol archive report:

```powershell
.\gradlew.bat --console=plain :app:verifyEmbeddedNodeDebug
```

The release-candidate aggregate also includes it through `verifyEmbeddedNodeDebug`:

```powershell
.\gradlew.bat --console=plain :app:verifyEmbeddedNodeReleaseCandidate
```

## Logcat Capture

Clear logcat before reproducing a crash:

```powershell
adb logcat -c
```

Capture logs after the crash:

```powershell
New-Item -ItemType Directory -Force app\build\reports\native-node | Out-Null
adb logcat -d -v threadtime > app\build\reports\native-node\logcat-native-crash.txt
```

For long-running reproduction, stream logcat to a file in another terminal:

```powershell
adb logcat -v threadtime > app\build\reports\native-node\logcat-native-crash.txt
```

Useful filters:

```powershell
adb logcat -d -v threadtime NativeNodeEmbeddedRuntime:* DEBUG:* libc:* AndroidRuntime:* *:S
```

## Tombstones And Bugreports

On non-root production devices, direct tombstone access is often blocked. Prefer collecting a bugreport:

```powershell
New-Item -ItemType Directory -Force app\build\reports\native-node | Out-Null
adb bugreport app\build\reports\native-node\bugreport.zip
```

On rooted test devices, list and pull tombstones:

```powershell
adb shell su -c "ls -lt /data/tombstones"
New-Item -ItemType Directory -Force app\build\reports\native-node\tombstones | Out-Null
adb shell su -c "cat /data/tombstones/tombstone_XX" > app\build\reports\native-node\tombstones\tombstone_XX.txt
```

If direct root access is unavailable, use Android Studio Device Explorer or the generated bugreport.

## Symbolication

Use the Build ID and SHA-256 from `native-symbol-archive-appDebug.txt` first. They determine which exact binary and symbol file should be used.

For bridge crashes in `libautojs6-node.so`, prefer the report's `latest CMake output` `symbol_file` path when it contains `.debug_*` or `.symtab` sections.

For `libnode.so` crashes, match by:

```text
ABI
Build ID
SHA-256
```

Then use the matching prebuilt `libnode.so` symbol artifact if one is available from the Node runtime build.

With Android NDK r26.1.10909125:

```powershell
$ndk = "$env:ANDROID_HOME\ndk\26.1.10909125"
& "$ndk\ndk-stack.cmd" -sym app\build\intermediates\cxx -dump app\build\reports\native-node\logcat-native-crash.txt
```

If `ndk-stack` does not resolve frames, use the exact `symbol_file` path listed by the report with `llvm-symbolizer` from the same NDK.

## Phase 11 Crash Bundle Tools

The final integration lane also provides schema-stable crash bundle tools:

```powershell
.\gradlew.bat --console=plain :app:collectNodeCrashBundle
.\gradlew.bat --console=plain :app:symbolizeNodeCrash
.\gradlew.bat --console=plain :app:verifyNodeCrashBundle
```

The default bundle is written to:

```text
build/reports/nodejs/final-crash-evidence-bundle/
```

For manual evidence, call the scripts directly with a tombstone or logcat file:

```powershell
node tools\nodejs\phase11\collect-node-crash-bundle.js `
  --repo-root . `
  --report-dir build\reports\nodejs `
  --logcat app\build\reports\native-node\logcat-native-crash.txt

node tools\nodejs\phase11\symbolize-node-crash.js `
  --repo-root . `
  --report-dir build\reports\nodejs `
  --logcat app\build\reports\native-node\logcat-native-crash.txt `
  --symbol-dir app\build\intermediates\cxx

node tools\nodejs\phase11\verify-node-crash-bundle.js `
  --repo-root . `
  --report-dir build\reports\nodejs
```

These tools make collection, schema validation, and best-effort symbolization callable. They do not claim final crash readiness until safe native crash markers, automatic next-launch collection, resource snapshots, timeline tail capture, and chaos injection exist.

## Triage Bundle

Attach these files to any native crash issue:

- APK or APK split that crashed
- git commit and release flavor
- `app/build/reports/native-node/native-symbol-archive-appDebug.txt`
- `app/build/reports/native-node/native-deps-appDebug.txt`
- `app/build/reports/native-node/logcat-native-crash.txt`
- tombstone text or `bugreport.zip`
- device model, Android version, and ABI
