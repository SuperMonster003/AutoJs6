# AI Agent P9.3 host execution evidence (2026-09-26)

The original roadmap host grant item already had `engines.execScript` and `files.write`
in its exact C.4 allow-list. This change adds the execution lifetime required to use
inline source safely from a cancellable Agent run, while preserving ordinary Node/MCP
execution semantics.

## Implemented behavior

- `engines.execScript(name, source, {agentInvocationId, agentRunId?, presetName?, timeoutMs, captureConsole?})`
  uses the link-owned invocation registry when the UUID option is present. An early stop,
  repeated token, explicit stop, timeout or provider destruction cannot launch a fresh
  invocation or stop an unrelated engine.
- `AgentOwnedScriptExecution` shares the registered-script completion, result slot,
  console window and cancellation implementation. Registered file/project dispatch
  retains the previous entry verification and scalar-parameter rules.
- Inline source is non-empty valid Unicode, without NUL, and at most 8192 UTF-8 bytes.
  Only the standard background JavaScript runtime is accepted. Engine-switch and
  UI/UI-thread directives are rejected before evaluation. Model-provided working
  directory, arguments, loops, delay and wait options cannot widen the execution.
- `availableOptionalMethods` advertises `engines.execScript` only for a real Android
  Agent broker granted both execution and stop. Plugins require this advertisement,
  rather than interpreting the older grant as support for owned cancellation.
- Public Agent options recognize `script_dynamic` and leave it off by default. Merging
  an assistant's empty `tools` object now resolves the actual defaults and preserves
  default-off groups instead of enabling the whole known-group set.
- The generated `@agent` export header remains `sensitive` / `before-run` even when
  the original source contains a later `@agent`, `@risk readonly`, `@confirm never` block.

These scripts have ordinary host privileges. The timeout stops the owned engine, not
separately launched child engines, and does not undo side effects. Existing broker file
operations retain canonical workspace and symlink checks; they do not sandbox JavaScript
file calls. No Rhino upstream files, AIDL transactions, SDK artifacts or new permission
declarations were changed.

## Verification

The clean isolated checkout `AutoJs6-Agent-P9-Verification-20260925` started at host
`52ce694f92`. It received only this task's source changes and used the standard Android
test certificate. The main workspace's signing configuration and the separate Rhino
upstream work were left intact.

| Check | Result |
| --- | --- |
| `:app:testAppDebugUnitTest`, seven focused suites | 41 passed, 0 failures/errors |
| `:app:assembleAppDebug` and `:app:assembleAppDebugAndroidTest` | Passed |
| Native page alignment verification | Passed, 16384 bytes |
| API 24, private `AI_Agent_Conformance_P9_API24`, x86 | 26 passed |
| API 37.1, private `AI_Agent_Conformance_P9_API37`, x86_64, 16 KiB pages | 26 passed |
| Ten changelog sources and 22 generated history artifacts | Rendered with existing renderer, without refreshing online metadata |
| `git diff --check` | Passed |

The JVM suites cover dynamic source/option validation, Agent grants, invocation races,
workspace file scope, the execution result state, manifest parsing and public option
merging. Each Android run combines eight dynamic lifecycle tests, twelve registered-script
regressions and six broker tests. Dynamic cases exercise real source execution and result
reporting, console fallback, exceptions, deadline termination, cancellation isolation,
provider teardown, stop-before-exec, service-before-start cancellation and invalid inputs.

Raw logs are local ignored artifacts: `build/p93-host-build.log`, `build/p93-host-unit.log`,
`build/p93-host-api24.log` and `build/p93-host-api37.log`. Their fixtures use only private
host-cache files and no online model, account, accessibility action, payment or real order.

The tested APKs use certificate SHA-256
`2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`.
The original test build hashes are:

| Artifact | SHA-256 |
| --- | --- |
| x86 host | `b21cb1b7db9b058a867fd421d21f7031b5d0b0d6003962303b433f54434fb54c` |
| x86_64 host | `6346aca1240c6b303b46fa55e54aa05f964dbec3b43cf16f59e0a2182cd50382` |
| Android instrumentation | `240162244d4662d5e84e5475a1507efa0a61c4e73abc9eb21183bdeb532f7d94` |

After the generated changelog assets were synchronized, incremental debug assembly passed
again in 22 seconds. All DEX entries remained byte-identical to the tested build. The final
ARM64 APK's packaged English and simplified-Chinese changelogs match the current sources.

For the newly connected physical-device check, a separate ARM64 copy was signed using the
main checkout's canonical production signing configuration without copying or changing that
configuration. Passwords remained in memory and the signer's subprocess environment. All
3844 non-signature ZIP entries match the final isolated APK; the companion instrumentation
copy likewise preserves all 47 non-signature entries from the tested Android APK.

| Production-signed local artifact | SHA-256 |
| --- | --- |
| `build/p93-host-private/production/autojs6-v6.8.0-p93-final-arm64-v8a.apk` | `4112189a49177ebe187ed8fea04fcb83dfc8fa74bdbbf5b83eaefd486e7c7059` |
| `build/p93-host-private/production/autojs6-v6.8.0-p93-androidTest.apk` | `a300690c23cca6f2405d607270638979099eeeca0bed19fa239e189feb65a544` |

The certificate SHA-256 is
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`.
The host remains version `6.8.0`, build `5297`; owned dynamic execution is detected by its
optional-method advertisement, not inferred from that pre-existing build number. These are
local development artifacts, not a published release. Physical-device results are recorded
by the accompanying Agent session; this host receipt asserts only the tests above.

These results establish deterministic host execution and transport behavior. They do not
replace Agent UI confirmation checks, a real model's generation quality, or the pending
P9.1/P9.2 online device acceptance.
