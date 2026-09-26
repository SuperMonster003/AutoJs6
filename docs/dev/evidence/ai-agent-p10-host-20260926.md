# AI Agent P10 host contract evidence (2026-09-26)

Host source commit `3cdf7de13c74ddc745c2413d69217d85f5e3d45a` makes two additive
changes for the Agent plugin's optional MCP source:

- `AiAgentArguments` accepts the `mcp` tool group, keeps it off in the default
  group set and preserves assistant restriction narrowing.
- `AiAgentContract.ERROR_TOOL_FAILED` joins the public error vocabulary. An
  external tool failure remains separate from host death and successful progress.

The host does not acquire MCP credentials, implement an MCP client or dispatch
the plugin's external tools. Existing Agent V1 AIDL methods, transaction order
and contract version remain unchanged. The API AAR is rebuilt to include the
additive constant; this is not a claim that the artifact is byte-identical to
the previous AAR. Ten changelog sources and their generated histories accompany
the source commit. This follow-up documents the public contract in `docs/dev`.

Read-only compatibility review compared the preceding `cdf1b6a564` with the
current source. `AiAgentRunRegistry.decode`, `AiAgentRunRecord` and
`AgentRunNativeObject` preserve task error JSON without an `ERROR_CODES`
allow-list; these receive paths did not change. The production use of that set
is ordinary exception-message classification in `AiAgentJsErrors`, not rejection
of plugin task results. Therefore the new error string does not itself require
a V1 or minimum-host increase. Explicit public JS `mcp` options still require
the P10 argument change. This is source-level compatibility evidence, not a
claim that an older production host APK was reinstalled and tested this round.

## Completed verification

The clean isolated checkout `AutoJs6-Agent-P9-Verification-20260925` is aligned
to the source commit above. Its current scoped test XML and build logs report:

| Check | Result |
| --- | --- |
| `AiAgentArgumentsTest` | 8 passed, 0 failures/errors/skips |
| `AiAgentContractTest` | 6 passed, 0 failures/errors/skips |
| `AiAgentAidlOrderTest` | 1 passed, 0 failures/errors/skips |
| Scoped host JVM invocation | Passed, 52 s |
| Agent API JVM invocation | Passed, 19 s |
| API release AAR assembly | Passed; final clean invocation 22 s |

The argument tests cover explicit MCP selection, default-off behavior, narrowing
and duplicate rejection. The API tests check the centralized contract vocabulary
and frozen transaction order. Ignored local logs are `build/p10-host-tests.log`,
`build/p10-contract-tests.log`, `build/p10-aars.log` and
`build/p10-aars-clean.log`. These checks do not invoke a model or MCP server.

## Current-revision Android validation

The same clean source checkout rebuilt `assembleAppDebug`,
`assembleAppDebugAndroidTest` and `ai-agent-conformance:assembleDebug` successfully
in 26 seconds. It contains no production signing files. Host x86/x86_64,
instrumentation and fake-Agent APKs all verified with the standard Android
test certificate, SHA-256
`2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`.
The native 16 KiB alignment check also passed.

After the Agent repository's complete Android suites finished, two guarded
private `AI_Agent_Conformance_P92_*` AVDs temporarily replaced their reciprocal
fake host with the current real test host. `AiAgentPeerConformanceTest` passed
all six cases on API 24 / x86 (1.56 s) and API 37.1 / x86_64 / 16 KiB (2.38 s),
with zero failures or skips. The reciprocal fake host was restored afterwards.
Device identity, emulator flag, AVD name, existing package version and matching
APK signer were checked; no physical device or production host was replaced.

| APK | SHA-256 |
| --- | --- |
| Host x86 | `c27ab8fba2360a3e28646cba7ad2254bb671f9464d7b5d21397eba0ea227ad9d` |
| Host x86_64 | `d04722bbaf7013bf2a032662046d9201845a20fb7b066be3b314cf78f6cfc0e8` |
| Host instrumentation | `240162244d4662d5e84e5475a1507efa0a61c4e73abc9eb21183bdeb532f7d94` |
| Independent fake Agent | `5f57bb7b46453bb24969f829b3011931eebb008ea71fd44351159d9444fc74a5` |

Build output is in the ignored `build/p10-host-android-build.log`; device receipts
are in the Agent repository's ignored `build/p10-private/api24-host-peer.log`
and `api37-host-peer.log`. These fixtures use deterministic replies, not a real
model or an MCP tool execution.

The existing peer suite contains six cross-process cases covering early run
callbacks and admission, malformed/retired generations, run-ID/sequence/terminal
fencing, transferred descriptor cleanup and actual fake-plugin death/rebinding
without replay. The fixture uses its own UID and the production host controller
and registry; package/signer policy is not relaxed. Reproduction details and
the required opt-in flag are in
[the conformance fixture README](../../../test-apps/ai-agent-conformance/README.md).

Historical P7 peer/grant results and P9.3 host execution results remain valid for
their recorded revisions. They are not substituted for this P10 run.
Agent-side MCP transport, UI, actual MCP Server interoperability and any real
model task acceptance are separately owned and recorded by the Agent repository.
No physical-device, network, account, payment or Rhino-upstream changes are part
of this host contract follow-up. Whole-host JVM, release/R8 and whole-host lint
were not repeated for the two additive changes and are not claimed here.
