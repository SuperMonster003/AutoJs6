# AI Agent P8 host documentation and installation catalog

## Implemented

- `ai-agent-protocol-v1.md` and `agent-script-manifest-v1.md` are versioned V1.
  Removed obsolete P3/P5/P6 pending claims and linked current public API,
  execution, production attachment and conformance evidence.
- Kept the compatibility floors distinct: base contract constant 5286,
  current plugin INFO/runtime minimum 5289, public task API minimum 5293,
  registered-script result/context since 5287. No constant, AIDL, AAR, grant,
  or public script signature changed in this documentation step.
- P1/P5 user-facing changes already exist in every v6.8.0 language record:
  brokers, catalog/registered execution, drawer/Plugin Center, approved roots,
  task handles and script result/context. Extended the existing entry-management
  record with installation-wizard availability instead of duplicating these items.
- The wizard includes the exact AI Agent package in Tools, optional in both
  Standard and Minimal profiles. Existing selection, enablement and download
  admission rules are unchanged.

## Publication dependency

The separate official index requires published releases containing APK assets.
Its required-repository inventory is also validated during generation. Adding an
unpublished required repository now would make routine index generation fail.
The AI Agent download entry and inventory update therefore remain pending until
the final release APK exists on GitHub. The host wizard omits a catalog item
absent from the downloaded index. No release version, URL or checksum is invented.

The original Agent P8 host/index checkbox remains open for that final index work.
The roadmap has not gained, split or discarded a stage. No GitHub/npm/Pages
publication or push was performed in this step.

## Validation

- Temurin JDK 21: `:app:testAppDebugUnitTest --tests '*PluginInstallWizard*Test'`
  and `:app:assembleAppDebug` passed in 2m 44s.
- Four existing wizard suites: 18/18, no failures, errors or skips. Coverage
  includes unique packages, category ordering, profile membership, lookup,
  index-to-row loading, profile codec and selection rules.
- Ten changelog source records and 22 generated Markdown files were updated
  through the existing `handle_changelog_placeholder` renderer. Every artifact
  changes exactly the matching entry; dates and online metadata are unchanged.
- JSON parsing, P1/P5 entry coverage, punctuation, artifact correspondence,
  local protocol links and `git diff --check` pass.
- The assembled host remains versionCode 5296. No new host APK was installed on
  users' devices, and no real model/device operation or Rhino edit was performed.
- The prior strict lint result for parent `433472897a` remains 0 Error/Fatal,
  2403 Warning and 3 Hint, with scoped legacy exceptions documented in
  `tools/lint_audit/2026-09-25.md`. The one-line wizard catalog addition was
  verified by compilation and its existing tests; full lint was not repeated.
