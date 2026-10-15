# Script API coverage checks

`extract_exports.py` reads the actual `ScriptRuntime` mounts and explicit
`Augmentable` assignments. `check.py` compares those names with the associated
documentation pages, resolves their real TypeScript symbols, and queries the
plugin's actual semantic and static completion providers.

The extractor also resolves Mail's explicit default-client forwarding table,
including its synchronous-only methods and generated Async names. List suffixes,
mutation chains and dynamic aliases that are not understood must be reported as
unresolved, including changes to referenced forwarding constants.

Keep the host, documentation, declarations and Ace plugin checkouts beside one
another, or supply their locations explicitly:

```powershell
python tools/api-coverage/check.py `
  --documentation ../AutoJs6-Documentation `
  --declarations ../AutoJs6-TypeScript-Declarations `
  --ace ../AutoJs6-Plugin-Ace-Editor
```

The check uses Ace's bundled TypeScript compiler. `--typescript` can select a
different compiler for the canonical declarations. `--skip-ace` explicitly
limits a run to documentation and canonical types; it is not a complete pass.

Outputs are written under `build/reports/api-coverage` by default:

- `exports.json`: registered names, module aliases and unparsed expressions.
- `documentation.json`: name evidence from the associated source pages.
- `surfaces.json`: real type resolution, default/all-group semantic completion
  and receiver-aware static completion for each queried path.
- `summary.json`: scope, counts and failures.

LSP bundles are generated into an owned temporary directory for each check;
the check does not rewrite plugin source or build assets. The plugin's selected
declarations and static index must already have been synchronized. See the
commands in `tools/ace-completion/README.md` for synchronization and freshness.

Run the extractor's regression tests with:

```text
python -m unittest discover -s tools/api-coverage -p "test_*.py"
```

The tests cover nested expressions, comments and strings, aliases, ignored
exports, global assignment, unknown registration forms and scoped document
lookup. Newly unparsed registration expressions fail the full gate instead of
silently reducing its denominator. `policy.json` contains narrowly named
internal/standard exceptions, explanations for dynamic registrations, page
mappings and selected returned-object probes. Public API gaps must be fixed at
their source rather than added to the internal exceptions.

This is a name and type-resolution gate, not a proof of every runtime overload.
Java prototype inventories, color tables, generated Compose catalogues and
conditional JSOX extensions have dedicated checks. The declarations repository's
`tools/verify-api-coverage.mjs` also tests invalid calls, all handwritten
declaration bodies, and isolated opt-in JSOX augmentation. The Ace repository's
`tools/ace-lsp/verify-api-coverage.mjs` tests default returned-object inference,
JSOX activation and false positives. Coordinate behavior is tested in the host's
`CoordinateScaleTest` against portrait/landscape dimensions, explicit modes,
partial base configuration and numeric boundaries.

The `Script API coverage` workflow checks related repository heads by default.
Its manual inputs can select coordinated branch names or commits when reviewing
a change spanning all four repositories. A pass against one set of revisions
does not certify a different set of published artifacts.

## Physical-device checks

Build and install matching current host and instrumentation APKs with `adb install
-r -t`, plus the current Ace plugin APK. Select APKs using `output-metadata.json`
and record their hashes; an unchanged version name does not prove the installed
bytes match the current checkout. Do not clear application data.

The existing device runner checks every selected test's completion identity and
rejects failures, skips and missing results even when `am instrument` exits zero:

```text
python tools/sdk37/physical_device.py instrument --serial SERIAL --suite coordinate-api --expected-target 37 --output build/reports/device-coordinates
python tools/sdk37/physical_device.py instrument --serial SERIAL --suite ace-api --expected-target 37 --output build/reports/device-ace-api
```

Each output directory must be new. Coordinate tests temporarily rotate an owned
UI script through 0/90/180/270/0, measure Android's actual display independently,
and restore the original rotation settings. They use a fresh Rhino runtime per
base configuration and also retain one UI runtime throughout the rotations.
Setter failure recovery and the established integer-conversion behavior are
separate cases. Detailed results are written to the host's external-files
`coordinate-scale-device.json`.

The host Ace test loads the installed plugin through the production ABI, restores
the previous settings after testing the genuine defaults, and queries the public
LSP completer with its normal budgets. It checks returned-object completion and
JSOX activation/removal, saving `ace-api-host-device.json` in the same directory.
The plugin's `AceApiCoverageDeviceTest` additionally checks actual popup geometry
and captures screenshots, so a correct candidate list cannot hide an overlapping
documentation tooltip. These are physical-device results only when run on an
identified physical serial; desktop jsdom checks are complementary.
