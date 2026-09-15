# MCP Server conformance test app

This Android application is a test-only Binder conformance fixture for the MCP Server protocol V1 (`plugin-api/mcp-server-api`). It is not a production plugin, a host fallback, or source shared with the independent `AutoJs6-Plugin-MCP-Server` project.

The fixture keeps a distinct fake package (`org.autojs.plugin.mcp.server.fake`) and deterministic services. It opens no socket: the `running` status carries a loopback endpoint string for the configured port and nothing listens on it. During startup every session reads the host broker info and sends one `device.info` request through the host broker, the same probe the production plugin makes, so a passing run also proves `openServer -> dispatch -> onResponse` against the real `McpHostCapabilityBroker`.

## Scenarios and components

One service per scenario, selected by the `org.autojs.plugin.mcp.server.fake.SCENARIO` meta-data of the component (the DEX compiler fixture pattern); each service checks on creation that its compiled scenario matches its manifest entry. All services answer the contract action `org.autojs.plugin.MCP_SERVER` with category `mcp-server`, are exported, and require `org.autojs.permission.PLUGIN`. The INFO provider `FakeMcpServerInfoService` answers `org.autojs.plugin.INFO` with the normal scenario's metadata.

| Scenario | Component | Behaviour |
| --- | --- | --- |
| `normal` | `NormalMcpServerService` | Valid metadata (contract version 1, `requiresHostVersion` 5279), one `onStatus(running)` after the broker probe, one `onEvent(warning)`, one `onStatus(stopped)` after `stop()`, nothing after `close()` |
| `wrong-contract-version` | `WrongContractVersionMcpServerService` | `mcpServerContractVersion` is one above the newest version the host supports; `openServer` throws `IllegalStateException("unsupported_contract: ...")` if a host calls it anyway |
| `oversized-bundle` | `OversizedBundleMcpServerService` | `statusLastError` carries 64 KiB (sixteen times the 4 KiB cap) in every status bundle and callback; startup also dispatches one `device.info` request whose inline JSON exceeds the 512 KiB cap and records the broker's answer |
| `duplicate-callback` | `DuplicateCallbackMcpServerService` | Every status and event callback twice, and one more `onStatus(stopped)` after `close()` returned |
| `death-callback` | `DeathCallbackMcpServerService` | Reports `running`, then kills its own process (`:mcp_server_mortal`) 400 ms later while the host still holds the session; no `stopped` callback ever arrives |

The fixture serves one session at a time (`already_open` otherwise), validates the config bundle's contract version and port (`invalid_config`), and refuses callers other than the installed AutoJs6 host signed with the fixture's own test key.

## Fixture-only status keys

Every status bundle also carries what the session observed, so the host test can assert it without a control plane:

| Key | Type | Meaning |
| --- | --- | --- |
| `fakeScenario` | String | The scenario meta-data value |
| `fakeStatusCallbacks` | Int | `onStatus` calls delivered so far |
| `fakeEventCallbacks` | Int | `onEvent` calls delivered so far |
| `fakeBrokerInfoReceived` | Boolean | `getBrokerInfo()` answered (also mirrored into `statusHostAvailable`) |
| `fakeBrokerMaxConcurrentCalls` | Int | `grantMaxConcurrentCalls` from the broker info |
| `fakeProbeResponseJson` | String | Summary of the probe answer: `id`, `ok`, `bytes`, and `category` / `message` of an error |
| `fakeOversizedResponseJson` | String | The same summary for the oversized request (oversized scenario only) |

## Host-side test

`app/src/androidTest/java/org/autojs/autojs/core/plugin/mcp/McpServerAndroidConformanceTest.kt` binds each component directly (the strict `McpServerPluginHost` pins the production package and must keep the fixture invisible) and checks `McpServerPluginHost.validatePluginInfo`, the `McpServerBundles` caps and the real `McpHostCapabilityBroker` with the default grant against the five scenarios. It is opt-in:

```
adb shell am instrument -w -r -e class org.autojs.autojs.core.plugin.mcp.McpServerAndroidConformanceTest \
    -e autojs.mcpServer.conformance.enabled true \
    org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

Only a debug variant is available. The manifest sets `android:testOnly="true"`, and the app uses the Android default debug/test signing key. Run same-signer Android compatibility tests only from an isolated checkout where the fixture and host debug build intentionally use the same default or dedicated test key. Never copy, reference, or use a production signing key for this matrix.
