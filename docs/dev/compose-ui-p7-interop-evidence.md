# Compose UI F.2 host integration

Date: 2026-10-07. Host 6.8.0 / 5320 on the dedicated Compose integration branch.
Merge e05218974a incorporated the committed host state 8b5bef91f3, including the
current colors/theme work. Original-host untracked/concurrent work was not used
as input or edited. Merge resolution retained both localized release histories
and the merged 47-entry plugin wizard catalog.

The XML `<compose>` alias uses the existing inflater and FrameLayout attributes,
with a runtime-owned container. `compose.attach` returns a normal session and
only replaces the same container's preceding session. Page, floating-window and
container sessions share the existing eight-session bound. Containers can be
prepared off-window; a real later detach ends their session. Activity and legacy
raw/resizable-window ownership, keepalive and engine cleanup are covered.

The optional AndroidView extension is selected through an advertised feature
and independent factory, retaining contract version 1. All 197 retained V1 class
files are identical to the prior AAR; nine new interop classes provide the
extension and weak process-wide View claims. Seven retired P0 spike classes were
removed as scheduled. Details and artifact identities are in
`compose-ui-interop-v1.md` and `p7-interop-aar-compatibility.json`.

Factories prepare Views on main through a one-way handoff and direct Rhino call;
they do not use the generic UI helper that exits a script on callback failure.
The graph retains actual Views beside bounded lease tokens and carries complete
bindings through pending, flight and accepted submissions. Same-node View reuse
and A -> B -> A updates preserve leases. New mounts preflight parent/claim/ancestor
constraints before closing old content. Render/factory/same-stack replacement
callbacks cannot open nested mounts; independent close callbacks can still mount.

`ui.inflate` now handles compiled XML TSX and native click bindings while retaining
string/E4X and parent/attach semantics. Direct Compose handles remain invalid XML.
Positional node arguments are recognized before NativeObject's Map interface,
preserving valid `compose.Column(nodeA, nodeB)` calls.

Validation before the implementation commit:

- 197 selected host JVM tests, including ten new interop cases, ordinary Compose,
  XML TSX, plugin wizard merge and colors regression: zero failures.
- Shared API JVM: 21 tests, including new catalog/weak-claim policy checks; both
  real catalog exporters pass. Original V1 projection remains byte-identical.
- Host debug/androidTest assembly and native alignment pass.
- Owned API 24 x86 and API 35 x86_64: six interop scenarios pass on each, including
  actual clicks, multiple containers, state, native factory/getter/replacement,
  off-window attachment and focus, detach/close/engine exit, legacy floating
  windows, factory failure and parent/ownership rejection.
- The real TypeScript provider compiles and executes `compose-tsx/interop.tsx`,
  including `<compose>`, AndroidView, an XML TSX `ui.inflate` factory and its click
  handler. The case passes with the real bundled declarations and signed provider.
- Old host 5319, without the new API classes, runs the new signed renderer's five
  real examples plus JNI/resource and independent INFO checks: seven pass.
- New host with the actual old renderer build 27: one dedicated case passes,
  proving basic attach remains usable while AndroidView mount/attach/transaction
  publication reject with PLUGIN_INCOMPATIBLE and preserve old nodes/callbacks.

The device harness initially used an invalid Button.text property and assumed
native Button text would not be uppercased by the theme. Fixtures were corrected
to documented Text children and explicit supported textAllCaps=false, retaining
the original visibility, click, thread, focus and cleanup assertions/timeouts.
An API 35 first-start ANR occurred before tests while the application main thread
was Runnable in ScriptEngineService initialization; the system recorded high CPU
pressure. The same application passed subsequent runs without startup or timeout
changes. Two compiler waits under load were retried with unchanged timeouts; the
actual F.2 diagnostic then exposed a missing legitimate textAllCaps declaration
in the compiler profile, which was added and verified through the real provider.

The first broad old-host runner selection also included a nonexistent helper test
class and a debug-only reflection check for a Compose class renamed by R8. The
correct signed-package selection passes; those selection mistakes are not hidden
as successful tests. The direct mixed UI tests use the actual application path,
not renderer-only mocks.

Ignored logs are under `app/build/compose-f2-*`. Final renderer transaction
hardening, signed-artifact identities, companion commit identities and owned AVD
cleanup are recorded by the Compose plugin in `docs/dev/p7-interop-evidence.md`.
Full host JVM/lint, packaged-app F.2 execution, the entire physical-device matrix
and new comparable performance/long-term memory baselines are not claimed.
