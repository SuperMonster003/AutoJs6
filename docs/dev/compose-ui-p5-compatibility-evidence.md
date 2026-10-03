# P5.2 compatibility and packaged application evidence

Date: 2026-10-03. Host implementation includes P5.1 commit 472f52e39b and the P5.2
execution-publication correction below, 6.8.0 / build5317; plugin 004f9fd,
1.0.0 / build19. Host testing uses the isolated debug application
org.autojs.autojs6.compose.spike. The plugin is the minified, signed release19
APK described in p5-robustness-evidence.md. No original user host was replaced.

## D28 matrix

| Device | API / ABI / page size | Final result | Duration |
| --- | --- | --- | --- |
| Owned Google APIs AVD | 24 / x86 / 4 KB | 23/23 | 51.887 s |
| Sony G8441 | 28 / arm64-v8a / 4 KB | 16/16 | 42.582 s |
| Sony XQ-AT72 | 31 / arm64-v8a / 4 KB | 16/16 | 27.017 s |
| Redmi 22120RN86C | 33 / arm64-v8a / 4 KB | 16/16 | 40.052 s |
| Xiaomi 23046RP50C, HyperOS | 35 / arm64-v8a / 4 KB | 23/23 | 42.821 s |
| Owned Google APIs 16 KB AVD | 37 / x86_64 / 16 KB | 16/16 | 48.610 s |

All six run the same 16 functional cases, without skips. ComposeExamplesDeviceTest
executes the five actual bundled scripts: counter, form, 1000-item list, HUD and
theme. The form uses the real native input connection and IME. The asset bytes
must match the installed plugin. ComposeFloatyScriptDeviceTest adds eight cases:
worker posts, raw/resizable windows, geometry/touch/focus options, native floating
editor and IME, closeAll, provisional attachment cancellation, control-slot bounds,
owned engine stop and callback-queue saturation. One UI stop case verifies view
disposal and cancellation; two loader cases verify resource/configuration/JNI
ownership and standalone INFO without host contract classes. API24 and API35 each
also run the seven P5.1 robustness cases. Total: 110 passing cases.

The HyperOS floating editor explicitly requests window focus before focusing the
field. Tested HUD operation is over the launcher, following D28's existing
condition; this evidence does not claim unrestricted overlay interaction over
every foreground application or bypass vendor permission policy.

API24 uses the project minSdk24 and a stock private ramdisk override, whose source
and SHA-256 were documented at P4. Shared SDK images and Magisk settings were not
modified. The API37.1 image reports SDK37; getconf PAGESIZE reports 16384. Its
release plugin loads graphics-path through the actual plugin ClassLoader. The
single APK still includes exactly the four allowed native helper libraries with
16 KB ELF/ZIP alignment. The old roadmap parenthetical calling this pure bytecode
was corrected to the already accepted D22 native-library fact.

The additional plugin-local ComposeTestRule attempt on API37.1 is not included in
this successful host matrix: its Espresso InputManager reflection is incompatible
with that image. Five standalone contract tests passed there, while 27 framework
cases failed before useful execution. Full plugin suites instead passed 32/32 on
API24 and API35. Details and the R8 native-probe correction are in P5.1 evidence.

## Real APK Builder and INRT execution

The test builds the real inrt release Runtime Kit from the matching host, then
builds the independent APK Builder plugin with that Kit. The official signing
policy is used, and the normal production Binder client builds the script project.
No alternate Android application module substitutes for the generated output.
The Builder repository's version aliases were inconsistent at its initial HEAD:
VERSION_BUILD49 versus PLUGIN_VERSION_BUILD48. Commit 4884075 synchronizes both to
the next reachable count50 and adds repository guards. Its 38 JVM tests and native
alignment checks pass. The published HOST_VERSION_BUILD pairing is not rewritten;
the explicit local Runtime Kit override selects the matched build5317 fixture.

The 16 KB AVD lists translated arm64-v8a before x86_64 in SUPPORTED_ABIS. The normal
Builder discovery policy therefore rejected a single-x86_64 Builder variant.
Using the universal Builder is the supported solution; the producer explicitly
selects x86_64 and checks that the generated APK contains exactly that native ABI.
No shared ABI compatibility policy was relaxed.

ComposePackagedApkProducerDeviceTest generates a default-signed application with
package org.autojs.autojs6.compose.packaged.p5, versionName1.0.0, versionCode1, and
the host's compiled runtime version5317. It requests no extra permissions and
checks that the output neither declares nor requests org.autojs.permission.PLUGIN.
The normal Compose factory/INFO metadata, official certificate, contract and
shared-dependency checks remain in force. Application versionCode1 is not confused
with the embedded runtime's version when checking the minimum host requirement.

ComposePackagedAppDeviceTest force-stops only that owned app, starts its real
SplashActivity, and verifies its own accessible UI. With the external plugin
installed, the counter moves from 0 to 3 through the packaged app's actual Rhino
callbacks. With the plugin truly uninstalled, the script catches
PLUGIN_UNAVAILABLE and uses XML UI to display the localized explanation, including
the plugin package. The displayed identity must be App: 1; runtime: 5317 in both
modes. Each application uses its own enable/trust preferences; it does not borrow
the main host's user authorization records.

The producer keeps its APK and JSON receipt only in the owned host's external
files directory; pulled artifacts remain ignored build outputs. The receipt also
preserves the normal warnings about the isolated host package name and the
Builder's lightweight remote implementation. These warnings were not suppressed.

The first final-fixture present case passed in 2.756 s. A following missing case
timed out with no error view; the unchanged strict case then passed in 3.010 s.
The failed process log showed ScriptExecuteActivity created then destroyed before
any Rhino execution. Source review identified an existing publication race:
ScriptEngineService started the UI Activity before registering its execution ID,
while INRT launches on Dispatchers.IO. The Activity can look up the not-yet-published
execution and finish early. This is a concrete source race matching the observed
symptom, although that failed process did not log which early-finish branch ran.
The service now constructs and publishes the execution before starting an Activity
or worker, starts outside the table lock, and rolls back only that same object if
launch is refused or throws. Successful launch does not insert a second time, so
fast completion cannot resurrect an execution. The synchronized table returns a
snapshot for enumeration, and preparation failures without an engine also release
their entry. Listener wrapping, notification order and original exception propagation
are preserved; the legacy com/stardust implementation is untouched.

ScriptExecutionRegistrationTest adds five deterministic cases: a concurrent
Activity lookup before the IO start call returns, refused launch, thrown launch,
completion before start returns, and same-ID replacement ownership. These five,
the existing global-listener/state cases and the selected Compose suite total
200/200 JVM tests (27 classes, zero failures/errors/skips). API16 and Builder38
also pass. The rebuilt host, inrt Runtime Kit and Builder pass native alignment.
The six-device table above was rerun after this final host correction.

The final real producer passed in 6.706 s. Its output is 32502572 bytes, SHA-256
7a7944c2c5cd82fac0806631fd46a46600fd2f844e7acbbc94f782c9cb2debf5. All eight DEX
files match the freshly rebuilt inrt template byte-for-byte, including the new
registration helper; the output is not a stale pre-fix template. The same APK then
passed five consecutive fresh-process starts with the plugin installed (2.353,
2.955, 3.497, 4.597, 4.354 s), followed by real plugin uninstall and five consecutive
missing-plugin starts (4.397, 3.642, 4.426, 3.037, 3.278 s). All ten first attempts
passed the original strict assertions. Failures from the earlier fixture remain
in the ignored logs and are not counted as passes.

## Reproduction and limits

The opt-in producer takes composePackagedBuild=true and composePackagedAbi=x86_64.
The consumer takes composePackagedMode=present or missing. Plugin install/uninstall
is performed externally on the owned AVD, not faked inside either test. The
consumer's assertions must remain strict; increasing timeouts or retrying until
green is not a substitute for diagnosing a startup failure.

Local api/compose.md documents the packaged-app requirements and error fallback.
Complete API reference pages, TypeScript/Ace artifacts and Offline Docs sync remain
in their unchanged P6 stages. The six-device matrix covers functional compatibility,
not all hardware, all OEM overlay policies or full TalkBack certification. Remote
CI, official index registration, public releases and pushes remain disabled by D7.
