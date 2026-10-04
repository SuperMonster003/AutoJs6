# P6.1-P6.4 catalog, storage, download and comparison acceptance (2026-10-04)

The host now has build-specific catalog trust, durable multi-version model storage, cancellable
verified downloads, and duplicate/version comparison logic. The comparison dialogs, management
page and generation flow are still P6.5-P7 work. No script API or model runtime dependency was added.

| Roadmap item | Local implementation commit |
| --- | --- |
| P6.1 build-specific catalog configuration | `601c4ee9ff` |
| P6.2 model store and current selection | `9aa4472d1b` |
| P6.3 verified, cancellable downloads | `bc6efaf1f1` |
| P6.4 duplicate and version comparison | `788f292e5c` |

Both debug and release Kotlin variants compiled successfully after the final adapter changes;
release was also recompiled after the signed-zero compatibility correction. All host commits
remain local. The existing generation interface still uses the previously delivered rule flow,
so this backend work does not add a premature application release-note entry.

## Catalog configuration

`CodegenCatalogFactory.create(context)` loads the real signed P5.5 production resources and
checks the full SPKI digest before creating the existing verifier/repository. Release builds
require the production namespace/key in both the source-set configuration and the factory.
Normal debug builds also use production. An explicit source-only switch in
`app/src/debug/java/org/autojs/autojs/ui/settings/codegen/CodegenCatalogBuildConfiguration.kt`
can select the already published P5.4 test source.

| State | Production | Explicit debug test profile |
| --- | --- | --- |
| Catalog cache under noBackupFilesDir | `codegen_models/catalog` | `codegen_models/catalog-debug-distribution-test` |
| Model store | `codegen_models` | `codegen_models-debug-distribution-test` |
| Selection preference | Resource `key_codegen_model_current` | Same base key plus `_debug_distribution-test` |
| Key ID | `p256-f1a540e052cf0865` | `p256-fc44588d1cb789dc` |

The debug model registry and selection are isolated as well as the catalog cache, so test
installations do not become production official-model records after switching builds.
Release resource symbols contain only the two production model-catalog resources; the test
key/bootstrap symbols exist only in debug. Neither construction nor local reads refresh the network.

## Model storage and selection

`ModelStore` streams input into leased staging files, creates a private installation snapshot,
hashes and parses that snapshot with the actual `ModelPackageReader`, then atomically renames
the version directory and updates `registry.json` under a file lock. Paths use
`<modelId>/<versionCode>-<sha8>/model.ajcgm`. Full hashes identify content; an actual collision
in the eight-character directory suffix rejects the incoming package without overwriting
the existing one. The collision test uses two valid ZIP comments on the existing tiny fixture.

Verified catalog hashes determine official/custom provenance. Installing another version
normally retains the previous version and current selection. Replacement deletes only the
chosen comparison version and switches current selection if that version was current.
Deleting current or selecting a missing version follows the roadmap's selection rules.

Registry transactions journal selection changes before synchronously persisting Pref. They
replay after a failure even if Android has already changed the in-memory preference value.
The journal is cleared only after successful persistence. Corrupt or unrecoverable registries
stop cleanup and preserve installed packages. Startup removes known orphans while protecting
catalog namespaces and active staging leases. The in-process lease map avoids opening a second
descriptor for a live POSIX lock; a second close must not invalidate the original process lock.

## Download and duplicate handling

`ModelDownloader` uses the existing HTTP source/connection classes. It checks catalog authority
before network access and again while holding the catalog lock across installation. The store
then checks the actual package against the signed size, full hash and manifest summary.
Revocation or changed artifact metadata accepted during transfer prevents installation.

Exact concurrent requests share a subscription and transfer. Explicit downloads continue to
share after a deprecation-only catalog update. Cancellation closes the connection, and terminal
events follow staging cleanup. Mirror retries truncate partial data while progress remains
monotonic. Progress is limited by 100 ms or a one-percent increment. Event delivery is ordered
even with parallel callback executors; observers cannot break transfer completion.
`CodegenModelServices` provides one shared service per application storage root for future
settings/floating UI, with an eight-second timeout per catalog HTTP call and Android main-thread
event delivery. P7 still owns its complete generation-flow deadline and cancellation policy.

`DuplicateCheck` checks full package hashes first, then chooses a comparison version in the
specified same-version/current/highest-version order. `VersionComparison` returns the twelve
E.5 rows with an injected localizable formatter. Diff flags use original values, including
full hashes, sizes and metrics, rather than rounded display strings. Missing values are `-`.
Catalog v1 does not include dataset creation time, so that part remains missing before download.

## Validation

| Check | Result |
| --- | --- |
| Debug codegen/settings-codegen/Ace-font regression | 334 cases: 328 passed, 0 failures/errors, 6 skipped |
| Release configuration and preference tests | 10 passed, 0 failures/errors/skips |
| ModelStore | 32 cases: 30 passed, 2 Windows symlink-permission skips |
| Final download fake-source cases | 14 passed |
| Real public HTTPS catalog/download/install/select/delete | 1 passed |
| Duplicate detection and comparison | 25 passed |

The other four debug skips are the existing opt-in dataset export, queue export, external
parity and benchmark jobs. Symbolic-link tests could not run with this Windows account's
permissions. The lease rule was validated with an assertion that cleanup never opens another
descriptor for an in-process active lease; this is not an Android/Linux process-level test.
The combined run preceded the final signed-zero compatibility fix. Its affected download
suite was rerun afterward: all 14 cases passed, including a real repackaged model with `-0.0`
metrics matched against the catalog parser's normalized `+0.0` value. Byte hashes remain exact;
only numerically equivalent zero metadata is normalized for the manifest comparison.

The opt-in real download used the published P5.4 tiny package, 23,633 bytes, SHA-256
`9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17`. It used real HTTP, the pinned
test key, catalog parser, downloader, actual package reader and temporary model store. It then
selected/deleted the installed package and verified selection clearing. No private signing key,
publication, Android-device install or production model download was involved.

Reproduction from the host root:

```powershell
$env:CODEGEN_DOWNLOAD_INTEGRATION = '1'
.\gradlew.bat --offline :app:testAppDebugUnitTest --tests 'org.autojs.autojs.core.accessibility.codegen.*' --tests 'org.autojs.autojs.ui.settings.codegen.*' --tests 'org.autojs.autojs.ui.edit.editor.ace.font.*'
Remove-Item Env:CODEGEN_DOWNLOAD_INTEGRATION
.\gradlew.bat --offline -PyoloR6ReleaseAndroidTest=true :app:testAppReleaseUnitTest --tests 'org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogConfigurationTest' --tests 'org.autojs.autojs.ui.settings.codegen.*'
```

The existing build property selects release as the tested build type, making the release unit
test task available; it does not publish or install an APK. Gradle `--offline` only controls
dependency resolution. The explicitly enabled integration test still performs public HTTPS GETs.

These are backend E1/E2 checks. P6.8's API 24/Redmi UI and filesystem acceptance remains with
the completed management flow. No new hardware or maintainer signing/dispatch is needed for
the next implementation steps. P8 still needs real-application evaluation and performance evidence.
