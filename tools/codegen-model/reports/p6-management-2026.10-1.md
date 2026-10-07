# P6.5-P6.8 model management acceptance

Date: 2026-10-07. Host: AutoJs6 6.8.0, build 5319. This report covers the model
comparison dialogs, official updates, management screen and P6 device acceptance.
The generation dialog's model switch and ranking integration remain in P7.

Implementation commits: P6.5 `41c6c4ec53`, P6.6 `3bfe1fdb61`, P6.7 `234ad66f16`.
This report and the device tests accompany the P6.8 acceptance commit.

## Implemented behavior

The management entry is **Settings > Accessibility service > Code generation models**.
Installed versions are grouped by model ID and show their version, source, package size,
compatibility/current state and update availability. A user can select the current version,
view its model card as bounded plain text, delete it, import a local package through the
system document picker, download an authorized catalog version or explicitly check for updates.
Deleting the current version explains rule ranking and clears the persisted current selection.

Duplicate checks run before downloading or installing. Identical content only shows a notice.
The same model ID with different content shows the twelve E.5 comparison rows, with a colored
background and a `≠` marker for differences, and offers replace / keep both / cancel. Replacement
affects the selected comparison version; keeping both preserves the current selection. The
same comparison view supports ordinary Activity windows and inspector overlay windows.

The comparison table enforces its viewport/font-derived width during measurement. Setting
only a child LayoutParams width is insufficient inside HorizontalScrollView: the device tests
caught a 3,172 px table in a 592 px viewport. The explicit measurement fixes that expansion,
keeps the three columns aligned and wraps values; larger fonts retain horizontal scrolling.
Import and check-updates actions use the toolbar overflow menu, avoiding fixed white action
text on a light theme background and leaving room for the screen title.

Official updates start immediately and retain the old/new comparison through progress and
completion. The old version remains installed until the user explicitly deletes it. A store
transaction changes current selection only if it still points to the updated old version;
an intervening user choice is preserved. Custom models have no online update action.

Local import uses a bounded staging copy, full package validation and another exact hash/size
check at installation. Cancellation reaches the document provider, closes the input and
interrupts the worker; pending imports release their staging lease on dismissal or destruction.
Submitted storage transactions and update completion run independently of an Activity's
lifetime. A process-level revision notifies a recreated page when such an operation completes.
A cancelled download remains busy until the downloader's terminal cleanup event, preventing
an immediate retry from attaching to the cancelled transfer.

Catalog reads after local mutations do not fetch the network. Opening the management page
uses the existing 24-hour refresh cache; check-updates and retry force a refresh. A failed
refresh shows a localized reason and retry without disabling compatible installed models.

## Signed public test v2

The maintainer signed and dispatched the test release. The agent did not read a private key,
sign a catalog or dispatch the workflow. Test repository commit:
`39b6507a95ecd93a0d63c03acc2225441d0f6473`.

- Workflow: <https://github.com/SuperMonster003/AutoJs6-Codegen-Distribution-Test/actions/runs/37570326238>
- Release: <https://github.com/SuperMonster003/AutoJs6-Codegen-Distribution-Test/releases/tag/codegen-models-v2>
- Key ID: `p256-fc44588d1cb789dc`.
- Catalog version: 2; generated at `2026-10-07T03:52:56Z`.
- CI: 63 distribution tests passed; current/historical signatures, reproduction and published
  asset readback passed. All six v1/v2 release files were also independently downloaded locally
  and compared to the reviewed hashes. The historical v1 files are unchanged.

| v2 file | Bytes | SHA-256 |
| --- | ---: | --- |
| `autojs6-codegen-autojs6.codegen.tiny-test-v2-908bd23f.ajcgm` | 23,633 | `908bd23ff3efc04cfe8fc21f83dc0a8fd1713044d3a449f1e778e559cbc91591` |
| `catalog-v1.json` | 3,726 | `177aaa5b0212cba28c0d85d33427cf47da0a37a7c02dfc541b088db73c283906` |
| `catalog-v1.sig.json` | 222 | `b001294f3e36d348065f0cab7597c819b317b4e8fbdf26d9029e51dc37fd4a44` |

Tiny v2 is an update-flow fixture: versionCode 2 / versionName 1.0.1. Its weights, features,
license and evaluation metrics are unchanged from v1; it makes no model-quality improvement
claim. The first production model and production catalog publication remain P8.3 work.

## JVM and build validation

The final normal-source debug APK and targeted JVM regression passed. There were **351 tests:
345 passed, 0 failures/errors, 6 skipped**. The six skips are four existing opt-in export/parity/
benchmark jobs and two Windows tests requiring symbolic-link privileges. The public HTTPS
download integration was explicitly enabled and passed. One earlier attempt timed out during
TLS negotiation; a retry and the final run passed without changing network/security settings.

New pure tests total 42: ModelUpdatesTest (11), ModelUpdateCompletionTest (3),
ModelTransferStateTest (5), CodeGenModelsStateTest (15) and CodegenModelOperationsTest (8).
They include current-selection compare-and-set behavior, preserving old versions, import
preview cleanup, changed staging bytes, the 100 MiB limit, interruption, source classification,
duplicate/replacement decisions, model-card integrity and management action availability.

An intermediate incremental Java compilation left existing Java classes absent from its
output. Recompiling only the host Java task without incremental compilation restored them;
no unrelated source or permanent Gradle settings were changed. Subsequent builds and tests
completed successfully. The local build override is an ignored file under `build/`.

## Android acceptance

Devices: API 24 x86 AVD and Redmi 22120RN86C, API 33. The distribution test build used the
existing source-only switch and its isolated `codegen_models-debug-distribution-test` store,
catalog cache and current-model preference. Tests assert that namespace before touching it.
The source switch was subsequently restored to false and normal-source APKs rebuilt.

CodeGenModelsDeviceTest exercises four actual Activity flows on both devices:

1. Flip exactly one byte in model.bin inside the real v1 ZIP; importing it shows a stable
   rejection reason and leaves the registry/staging unchanged.
2. Import unique custom model versions, select current, exercise identical / cancel / keep /
   replace, display a model card containing literal Markdown/HTML as plain text, and delete
   current with the rule-ranking explanation. No WebView is used.
3. Force a signed catalog refresh, import/select official v1, start the real GitHub v2 update
   with no confirmation, verify its full hash, keep old v1 by default and delete it only through
   the completion button. Current selection moves to v2.
4. Hold a submitted operation before it takes the store lock, destroy the old Activity, open
   a new one, complete the operation and observe its new current selection without refreshing.

ModelDialogsDeviceTest exercises seven cases: fixed comparison rows and difference markers;
all duplicate decisions and Back; identical-content dismissal; progress-to-success with the
same comparison view; cancellation; failure; and font scale 2.0 with English/Arabic RTL.
Behavior cases cover both ordinary and overlay windows. Every comparison cell's complete last
line must be reachable, and table width has an explicit window/font budget. API 24's nested
requestRectangleOnScreen propagation changed the requested rectangle during test scrolling;
the final harness drives the two scroll offsets separately while retaining full visibility
assertions. It does not relax the production layout requirement.

| Device | Management cases | Final dialog cases | Additional manual checks |
| --- | ---: | ---: | --- |
| API 24 AVD | 4 passed | 7 passed | System document picker import; airplane mode, forced refresh and retained current model |
| Redmi 22120RN86C, API 33 | 4 passed | 7 passed | Airplane mode, forced refresh and retained current model |

The final relevant logs are `build/codegen-p6-device-api24-final.log` (four management
cases passed; two dialog scrolling-driver failures diagnosed above),
`build/codegen-p6-device-api24-dialogs-verified.log` (all seven dialog cases passed after
that harness correction), `build/codegen-p6-device-redmi-final.log` (all eleven passed),
and `build/codegen-p6-device-redmi-dialogs-verified.log` (the final seven-case harness passed).
The last dialog runs use the restored normal-source APKs; no model download is involved in
those seven in-memory cases. There were no device-case skips in these runs.

The API 24 system document picker was also exercised manually: open import, navigate through
internal storage to the public tiny v1 fixture and select it. The management page installed
the exact 23,633 B artifact with its file display name as origin. It correctly shows official
source because this exact hash is in the verified catalog; the separate custom-content tests
cover the custom-source expectation in appendix B.7.

Both devices were temporarily put in airplane mode with Wi-Fi/data disabled. With v1 installed
and current, the management page's check-updates action showed the network reason, retry and
offline availability message while retaining the installed row/current selection. A forced
check was used because the fresh signed cache correctly avoids a network request on opening.
The original airplane/Wi-Fi/data settings were restored in finally blocks. The API 24 emulator's
temporary root adb session used for its protected airplane-mode broadcast was returned to
the original shell user. No phone reboot or phone root operation was needed.

Device tests preserve existing packages and current selection and remove only their own
installed content. The manual test fixtures were also removed through ModelStore. Production
model storage and selection were not used for these tests.

## Reproduction and records

From the host root, with JDK 21 and the configured Android SDK:

```powershell
$env:CODEGEN_DOWNLOAD_INTEGRATION = '1'
.\gradlew.bat --offline :app:assembleAppDebug :app:testAppDebugUnitTest --tests 'org.autojs.autojs.core.accessibility.codegen.*' --tests 'org.autojs.autojs.ui.settings.codegen.*' --tests 'org.autojs.autojs.ui.floating.codegen.*'
.\gradlew.bat --offline :app:assembleAppDebugAndroidTest
```

For distribution device cases, explicitly enable USE_DISTRIBUTION_TEST_CATALOG in the debug
source, rebuild/install the host and test APKs, and use:

```text
adb -s <device> shell am instrument -w -r -e codegenModelsDevice true -e class org.autojs.autojs.ui.floating.codegen.ModelDialogsDeviceTest,org.autojs.autojs.ui.settings.codegen.CodeGenModelsDeviceTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

Overlay permission and an unlocked device are required for the dialog cases. The runner uses
FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES. Without explicit opt-in these cases skip. The
management suite refuses production configuration before creating its services and preserves
pre-existing data; it skips the official update case if tiny-test is already installed. Restore
the source switch and normal APK afterward. ModelDialogsDeviceTest alone also runs with the
normal source because its fixtures are in memory and do not use model storage/network.

Local ignored evidence is under `build/codegen-p6-*`: build/JVM/device logs, release readback
hashes and app-only screenshots. The final relevant device runs are recorded in the P6.8
roadmap evidence. XML resources provide 79 new strings in all eleven directories; default
and English values agree. Ten changelog JSON files and their 22 generated Markdown files
contain one user-facing model-management feature entry, dated 2026/10/07.

Host changes are local commits only. The already reviewed/signed v2 test-repository update
was the only remote source push in this work; the production Shared-Assets repository and
host remote were not pushed.
