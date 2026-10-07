# P5 distribution and P6 catalog implementation evidence (2026-10-03)

This work implements the Shared-Assets model distribution namespace, catalog schemas,
offline signing/verification tools, immutable release workflow, verified bootstrap staging,
and the host's independent catalog backend. Production signing material, the real remote
workflow acceptance run and production resource wiring are still outstanding. This report
records local E1 evidence; it does not claim P5 remote E2 or P6 device E3 acceptance.

## Shared-Assets inputs and local acceptance

The related repository is `D:/idea-projects/AutoJs6-Shared-Assets`. All changes are local
commits. No host or Shared-Assets branch was pushed, and no Release or workflow was created.

| Roadmap scope | Shared-Assets local commit | Completion boundary |
| --- | --- | --- |
| P5.1 namespace/source/pending proposal | `d706807` | Formal public key and signed current pair pending |
| P5.2 schemas | `51dc70b` | Implemented and locally verified |
| P5.3 distribution tools | `56b4c7e` | Implemented and locally verified |
| P5.4 workflow | `e3d2f26` | Real fork/private run still required |
| P5.5 bootstrap preparation tool | `397f844` | Production host resource pair still required |

- Reviewed source: `codegen-models/sources/autojs6.codegen.tiny-test/1/`.
- Package: 23,633 bytes, SHA-256
  `9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17`.
- License: existing test package's MPL-2.0 terms. This does not select the future official
  production model/data license.
- Dataset: the controlled P3 `cg-ds-2026.10-1` aggregate manifest; device serials removed.
  `sourceManifestSha256` preserves the original digest
  `8617555fde5a4d002cc1e95de1f893c27d4c65498ca6fcd5f12d729a602bc812`.
- Production pending v1: empty `models`, timestamp `2026-10-03T11:12:51Z`, explicitly
  **unsigned**, prepared using `--empty-bootstrap`. It is an APK-only bootstrap proposal;
  do not promote or dispatch it as a remote release before the P8.3 first official model.

Catalogs bind repository, content-addressed package filenames, exact size/SHA-256, feature
schema, dataset, metrics and license. Catalog preparation requires consecutive versions and
preserves historical package URLs. Model version contents cannot change; deprecated/revoked
flags can only become true. Recommended version is null when every version is disabled.

Production sources need a passed P8.1 `gate.json` bound to the package SHA. The tools do not
fabricate gates. The sole tiny-test fixture is excluded from production scans with an audit
record, and only explicit test mode in a noncanonical repository allows its gate exemption.
Local-evaluation packages are refused in every release mode.

The local CLI integration executed eleven checks: prepare twice and compare exact bytes,
validate catalog, validate assets, generate a disposable key outside Git, sign, verify,
prepare bootstrap, reject byte tampering, and verify simulated release readback/signature.
The synthetic namespace was `example/AutoJs6-Codegen-Test`; no network call was made.
The private key was removed. Public signed test material is retained only in JVM test
resources and ignored local build output.

The release workflow checks the default branch, required signing inputs, current and every
historical signature, adjacent historical invariants, exact reproduction, authenticated
tag/Release absence, immutable draft creation/upload/publication, and exact asset readback.
Only explicit authenticated HTTP 404 permits creation; transport/auth/rate-limit/server
failures stop before remote mutation. CI receives no signing private key.

## Cross-language compatibility fixes

Independent review found and corrected three concrete mismatches before acceptance:

1. Python's ZIP reader tolerated prefixes, trailing bytes and non-regular ZIP file attributes
   that the host rejects. The distribution validator now checks the actual ZIP structure
   and compressed-stream boundaries against the host package contract.
2. Python accepted week dates and more than nine fractional second digits, which the host's
   `OffsetDateTime` cannot parse. The host packager and distribution tools now share a strict
   calendar timestamp subset, preserve nanosecond text, and compare catalog timestamps
   without Python microsecond truncation.
3. Independently valid historical signatures did not prove that an intermediate catalog
   preserved old model contents. The workflow now validates each adjacent historical pair
   before checking the current transition.

The host timestamp correction is local commit `d58c4e9bc3`, with 15 packaging tests passing.
It changes offline tooling and introduces no application UI or script API change.

Local verification: 63 model distribution tests passed (26 package/catalog/schema,
19 signing/bootstrap, 18 release/workflow). Ace font regression: 43 passed and three
host-specific integration cases skipped by that existing suite. Its root `.gitattributes`
pin is preserved by scoping the new binary rule inside `codegen-models/.gitattributes`.
The stricter package validator also reverified the actual signed test catalog and local
readback artifact. No Python dependency was added.

## Host catalog backend

`codegen/store/ModelCatalog*` uses the existing P-256 verifier, HTTP catalog fetcher and
recoverable file-lock/write helpers. It has no Android types. Directory, clock, verifier,
fetcher, repository namespace and bootstrap bytes are injected.
The backend and permanent interoperability fixtures are local host commit `32e5bf478d`.

Local reads never fetch. Explicit refresh uses a 24-hour cache; forced refresh checks the
remote source immediately. Unsupported schemas and host versions remain visible with an
unavailable compatibility state. Exact catalog bytes are verified before parsing or saving.
The persistent history rejects catalog rollback, same-version different bytes, model version
replacement, time rollback and reversal of revocation/deprecation, even after a model entry
temporarily disappears from a later catalog.

History is saved before the cache. An interrupted cache write may leave older display-only
metadata, but cannot reauthorize a version revoked by newer accepted history. Two repository
instances share the same file lock and persisted history. Invalid remote responses preserve
the old verified cache.

The signed bootstrap is always display-only and does not establish the remote version
watermark. An empty bootstrap v1 therefore permits the first verified remote v1 to contain
the official model. An old bootstrap cannot restore download authority after remote history
has been accepted. `src/main/res/raw` still contains no placeholder model key or signature.

The permanent `src/test/resources/codegen/catalog/` fixture was produced by the actual Python
and OpenSSL CLIs. Kotlin verifies its exact signed bytes, checks model metadata against the
actual tiny package, rejects byte/pin/namespace changes, and consumes the signed bootstrap.

- Test public SPKI SHA-256:
  `0971601a168da7ee9db0f4e23499454a69b9925e39ec1ce8fa353ab8ab839fa7`.
- Signed test catalog SHA-256:
  `07af51c5ed5f512dcb902066aec53e2a0495e58c8df60f8643f9011ca0f89bb5`.

Host compilation and combined codegen/inspector/filter/Ace-font regression passed: 351 cases,
347 passed and four expected opt-in skips (external dataset/queue/parity and benchmark jobs).
After the final history-recovery, unavailable-lock fallback and signed-zero normalization
fixes, the affected catalog suite was recompiled and rerun: **32 passed, zero failures/errors/
skips** (parser 8, repository 21, Python/OpenSSL interoperability 3). Public metrics normalize
negative zero before fingerprinting, matching Python's numeric equality for immutable fields.

```powershell
.\gradlew.bat --offline :app:testAppDebugUnitTest --tests 'org.autojs.autojs.core.accessibility.codegen.*' --tests 'org.autojs.autojs.ui.edit.editor.ace.font.*' --tests 'org.autojs.autojs.core.accessibility.inspector.*' --tests 'org.autojs.autojs.core.automator.filter.*'
.\gradlew.bat --offline :app:testAppDebugUnitTest --tests 'org.autojs.autojs.core.accessibility.codegen.store.*'
```

This is JVM evidence. Neither a production catalog endpoint nor a phone was used. Application
entry points will be connected after production resource wiring and the remaining P6/P7 work.

## Remaining maintainer inputs

1. Generate/retain the new model P-256 private key offline. Supply the public key and
   independently recorded fingerprint/key ID, plus the signed reviewed bootstrap catalog
   and signature. Private key material stays with the maintainer.
2. Choose a fork/private test repository, prepare/promote its tiny-test catalog, and run the
   workflow as prescribed by roadmap usage rule 5/P5.4/P5.6. Record the actual run URL and
   readback results. Local simulated readback is not that evidence.
3. After verified production materials arrive, install the two P5.5 resource files, finish
   release/debug source-set catalog configuration, and rerun the host catalog checks.
4. Before P8 publication, choose the production model/data license and provide real-app
   evaluation data. P3 real-test remains zero; this work does not supply real-app evidence.

No additional device or manual phone action was required. Model storage/download/management
UI, generation integration and real-device performance acceptance remain P6.2 onward/P7/P8.
The commands and exact signing/bootstrap procedure are in Shared-Assets
`tools/codegen-model-distribution/README.md`.
