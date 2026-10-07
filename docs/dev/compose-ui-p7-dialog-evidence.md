# Compose UI F.3 host integration

Date: 2026-10-07. Host 6.8.0 / 5321, dedicated Compose integration branch.
Merge `fbef5018f8c6206c0b63d9f4d62119957131fe4f` incorporated fixed mainline
`2ab34ecfe5`, retaining current editor hints, model management and both localized
release histories. The original host checkout and its concurrent work were not
modified or staged.

`compose.dialog(nodeOrRender, options?)` returns an ordinary ComposeSession.
Options are type (`alert` by default or `bottomSheet`), cancelable (true by
default), and the existing theme override. Nullish option containers use defaults;
explicit invalid fields and unknown keys retain typed errors. Dialogs do not
replace pages, floating windows or XML containers. All share the eight-session
limit and existing nested-render/factory/mount and rollback rules.

The creating engine's mode and Activity binding are captured independently of
the writable global activity alias. A UI dialog uses its bound live Activity;
non-UI uses the host's existing overlay permission and a scoped keepalive lease.
Permission checks do not launch authorization UI. Native View factories receive
the bound Activity or normal non-UI ScriptTheme Context, not a forged global.

The UI anchor is a non-interactive transparent 1 px child of the Activity decor.
The public initializeViewTreeOwners call establishes actual Activity ownership
even when no page has called setContentView. It does not replace that page or
acquire its Compose IME inset lease. The dialog uses the Activity Context's
default application token; the decor View's window token is not substituted.
Non-UI anchors reuse managed RawWindow registration with their invisible,
non-touchable/non-focusable parameters installed before WindowManager attachment.
They close with legacy floaty.closeAll, session close and engine exit.

The independently negotiated `dialog-v1` factory leaves V1 and F.2 intact.
The current release AAR SHA-256 is
`e3a4f2003bb0336338e229cfc9220c2296a5de462957fd540a20c37d86996fe8`;
all 206 retained class files are byte-identical and three dialog classes are
added. See `p7-dialog-aar-compatibility.json` and `compose-ui-dialog-v1.md`.

Dismissal and fatal asynchronous presentation failures use a reserved lifecycle
slot, independent of ordinary event overflow and later tree generations. The
system envelope is validated, unknown error codes become RENDER_FAILED, and a
fatal error is reported before one close. No script close callback runs in the
renderer sink stack. Basic V1/F.2 paths ignore unnegotiated lifecycle messages.

Validation before the implementation commit:

- Selected Compose/core/XML TSX/wizard/colors JVM suite: 214 cases pass. Ten new
  cases cover option parsing, independent sessions, limits/rollback and reserved
  lifecycle delivery under stale generations and saturated queues.
- Shared API: 23 JVM cases pass. The real V1/F.2 projections and 19 Python catalog
  regressions pass without changing frozen regions.
- Host app debug and androidTest assembly and native alignment pass.
- Owned API 35 x86_64 and API 24 x86: six real dialog scenarios pass on each,
  covering empty Activity ownership, writable-global resistance, underlying-page
  replacement, immediate focus, real IME interaction, cancelable=false, updated
  generations, anchor removal, non-UI keepalive and automatic exit, overlay
  closeAll/engine stop and suppression of immediately closed/rolled-back windows.
- API 35 overlay permission denial: one real case passes. A non-UI engine cannot
  borrow another engine's Activity to bypass permission; that foreign UI remains
  live. The owned permission setting is restored.
- The actual F.2 renderer build 30 rejects dialog capability before evaluating a
  new render function, while preserving the old page and its working callback.
  A prior signed F.3 candidate also passes the F.2 host's five examples, six
  interop scenarios and two JNI/INFO checks. Final candidate identities and
  repeated compatibility/TSX gates are recorded by the main Compose repository.

The JVM lifecycle fixture uses the existing pure-JVM event-envelope allocation
pattern rather than Android Bundle constructors. The initial attempt correctly
failed on unmocked BaseBundle.size; production Bundle parsing is covered by real
Android tests, not replaced by global default-return mocks.

Device checks exposed two real initialization defects: a page-less Activity had
no ViewTree owners, and an explicit decor token was unsuitable as an application
token. Both are fixed in production. Two fixture assumptions were also corrected:
system decor children and the SOFT_INPUT_IS_FORWARD_NAVIGATION bit can appear
after initial startup. Cleanup now checks the exact owned anchor object's parent
and attachment; IME policy checks retain STATE and ADJUST masks.

The focus API explicitly does not guarantee a visible software keyboard. The IME
case first proves immediate editable/focused state, then uses the same real
accessibility click path as the prior P2 input gate to open the keyboard. It still
requires a real TYPE_INPUT_METHOD window, Back hiding, policy preservation,
noncancelable behavior and explicit cleanup. No timeout was increased and no
platform was skipped. The renderer separately gates Focus on its actual dialog's
window focus; its exact command-order regression belongs to the plugin suite.

API 24 initially hit a system_server SIGSEGV in goldfish_dma_write while using
the legacy swiftshader_indirect emulator backend. The crash buffer is retained;
only the owned emulator was restarted with supported swiftshader, keeping the
same SDK image, private stock ramdisk and app data. A sheet swipe fixture also
needed to await the public enabled Dismiss semantics before its unchanged real
gesture; it does not invoke that semantics action as a substitute for dragging.

Ignored logs are under `app/build/compose-f3-*`. The main plugin's
`docs/dev/p7-dialog-evidence.md` records companion source identities, final TSX
execution, artifact metrics and owned-device cleanup. Full host JVM/lint,
packaged-app F.3 execution, the full physical-device matrix, new comparable
performance/long-term memory measurements and remote CI are not claimed here.
