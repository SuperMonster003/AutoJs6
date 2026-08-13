# Lua runtime worktree integration closeout

Date: 2026-08-14

## Integrated source

- The former `work-lua` tip was
  `66faf0da4d55cad00082b0b6852910018664295e`.
- It was merged into `master` as
  `3b7378758c5a4f68e8680a78cf2c541c23628489`, with parents
  `1c7273aaf1d536d6e3dab2d0935fa5524fa5256c` and
  `66faf0da4d55cad00082b0b6852910018664295e`.
- Conflict resolution retained the existing Python and Node routing, kept Lua
  provider discovery default-off and fail-closed, and added direct-path,
  persisted-context, editor, task, shortcut, and CI coverage for Lua.
- Review fixes also prevent Android `Intent` objects from entering the Lua V1
  argument mapper, restrict the exported shortcut activity to direct paths,
  pin persisted Lua identity, and re-inspect the exact package identity
  immediately before dispatching the source PFD.

The independent plugin contained all provider, service, JNI, watchdog, native,
and instrumentation implementation that was worth retaining. The host
worktree's protocol, routing, fake-provider, and host-smoke sources remain in
the host repository rather than creating a second writable authority in the
plugin.

## Canonical plugin

- Canonical path: `D:\idea-projects\AutoJs6-Plugin-Lua-Runtime`
- Closeout revision: `49020f1e830ac9587718c7f8641d3a454c2aa951`
- The redundant detached plugin reproduction worktree contained no unique
  tracked or untracked source and was removed before the canonical repository
  was moved from `E:` to `D:`.
- `protocol-artifacts.lock.json` now records the merged host source revision
  `3b7378758c5a4f68e8680a78cf2c541c23628489`. The staged protocol AAR hashes
  pass the plugin's build-ready verifier.

Historical signed `0.1.0-rc.1` artifacts and device results remain attributable
only to plugin revision `0497d5061171421e655e0256ef5a1fe1566c5a23`. They are
not evidence for the closeout revision or the integrated host HEAD.

## Recoverable archive

- `.bak/AutoJs6-Lua-Worktree-20260813-work-lua-66faf0da.bundle`
  - bytes: `240134`
  - SHA-256:
    `7bd278ee04d072b6ecb7088c1d4504bbc74bf8c1f6ead7f5c4496604bfcfe580`
  - contains `refs/heads/work-lua` at `66faf0da4d55cad00082b0b6852910018664295e`
  - prerequisite: `8af5ec658d6d78ce080672c1c734b21a47b4f0c7`
  - `git bundle verify` passed after worktree and branch removal
- `.bak/AutoJs6-Lua-Auxiliary-Evidence-20260810/`
  - retains the two small device APK snapshots, the pinned Lua 5.4.8 upstream
    archive, and two Gradle daemon logs
  - its README records byte sizes, SHA-256 hashes, provenance, and evidence
    limitations

The removed worktree, intake, reproduction cache, device-audit source
directory, and redundant plugin worktree occupied approximately 2.08 GB in
total. Moving the canonical plugin repository from `E:` to `D:` normalized its
location but was not counted as reclaimed space.

## Cleanup proof

After the merge and archive verification:

- the linked `E:\AutoJs6-Worktree-Lua` entry was removed and pruned;
- local branch `work-lua` was deleted after proving it was merged;
- no worktree row or local branch ref containing `lua` remains in the host
  repository;
- all six candidate `E:` paths from the cleanup request are absent; and
- the canonical plugin has one worktree at the `D:` path above.

## Validation performed

- Host static Lua roadmap: `208/208` checks passed through R3.
- Host focused JVM tests: App `130` tests with `0` failures, `0` errors, and
  `1` Windows symlink skip; Lua API `50/0/0`; fake fixture `2/0/0`.
- Host Android compilation and debug APK assembly completed successfully,
  including Android-test Kotlin compilation.
- New string resources passed all-locale uniqueness, ordering, default/English
  parity, and ASCII-punctuation checks.
- Plugin repository verifier passed with `build_ready=true`; plugin unit tests
  passed `43/43`, and the debug APK assembled successfully.

These are source, JVM, compilation, packaging, and static-verifier results. No
new ADB/device run was performed during integration, so this closeout does not
claim fresh Binder/PFD, native-device, release-signing, public-release, or
production-soak acceptance. Known remaining plugin evidence or behavior gaps
include typed BUSY handling at the retained-session cap, the tiny-deadline
oneway-start race, blocking non-regular source containment, OS-level FD-leak
accounting, broader update/uninstall and peer-death cases, and production soak.
