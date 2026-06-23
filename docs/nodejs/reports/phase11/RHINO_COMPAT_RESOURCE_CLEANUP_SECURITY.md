# Rhino Compat Resource Cleanup/Security Gate

Status: S11-30 resource cleanup/security gate and report.

S11-30 adds a Rhino compat focused cleanup/security contract for the Node engine. The gate does not duplicate the lower-level instrumentation. It binds the existing cleanup and denial evidence for the compat facade scope into one Phase 11 report:

- `image`
- `selector`
- `event`
- `ui`
- `floaty`
- `db`
- `websocket/fetch`

## Commands

Host contract:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatResourceCleanupSecurityHost
```

Connected-device gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatResourceCleanupSecurity "-Pautojs.nodejs.test.deviceProfile=sony"
```

The host gate writes:

- `build/reports/nodejs/rhino-compat-resource-cleanup-security-verification.json`
- `build/reports/nodejs/rhino-compat-resource-cleanup-security-verification.md`

The JSON report uses schema `autojs6-node-rhino-compat-resource-cleanup-security-verification-v1`.

## Acceptance

Every surface must satisfy one of these states:

- `verified_zero_after_recycle_or_stop`: runtime resources are explicitly released and pending bridge calls drain to zero.
- `verified_pending_zero_after_timeout_cancel`: timeout/cancellation paths reject with stable errors and leave pending count zero.
- `verified_zero_after_close_destroy`: opaque provider resources close explicitly or are destroyed with active resource count zero.
- `verified_zero_after_close`: database handles close and provider diagnostics return active handle/resource counts to zero.
- `verified_pending_zero_and_default_denial`: network resources are denied or destroyed without active fetch/WebSocket diagnostics.
- `design_gated_no_resource`: no compat facade is exposed, so no native resource can be created.
- `blocked_by_design_no_resource`: exact Rhino semantics remain blocked and create no Node-owned resource.

Any non-zero active resource after stop, timeout, close, or destroy is a blocker unless the surface is design-gated and the report records a residual policy explaining why no resource was created.

## Surface Evidence

| Surface | Status | Cleanup/security evidence | residual policy |
| --- | --- | --- | --- |
| `image` | `verified_zero_after_recycle_or_stop` | `NodeImageBridgeExpansionInstrumentationTest#jsImageExpansionUsesFakeTransportAndBlocksShadowing`, `NodeAutoJsBridgeInstrumentationTest#mediaProjectionAndImageModulesUseFakeTransportAndBlockShadowing`, and `NodeBridgeResourceRegistryInstrumentationTest#directRegistryClosesLeakedBridgeResourcesAndIsIdempotent` cover `image.recycle`, screen-capture `stop`, pending=0, and direct registry destroy cleanup. | Live screenshot permission remains fail-closed until a provider owns the permission/session; fake and provider-owned handles must end at active resource zero. |
| `selector` | `verified_pending_zero_after_timeout_cancel` | `NodeAccessibilitySelectorBridgeInstrumentationTest#jsRhinoCompatSelectorFacadeUsesFakeTransport` and `#jsAccessibilitySelectorV2HonorsTimeoutAndCancellation` cover Rhino selector pending=0, timeout, cancellation, and live dispatch pending count zero. | Raw `AccessibilityNodeInfo` never crosses into Node; unavailable providers reject without retained resources. |
| `event` | `design_gated_no_resource` | `docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md` and the status manifest keep raw Rhino `events` and input observer compatibility design-gated. | A future `input_observer` provider must add resource registry ownership and zero active observer checks before exposure. |
| `ui` | `verified_zero_after_close_destroy` | `NodeUiBridgeInstrumentationTest#jsUiModuleUsesFakeTransportForLayoutEventsAndPolicy` and `#androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource` cover opaque `UiHandle`, pending=0, close, provider destroy, foreign-handle denial, and active UI session limit denial. | Rhino `ui.run`, `ui.post`, raw View lookup, and Activity emitter semantics remain blocked by design and create no Node resources. |
| `floaty` | `blocked_by_design_no_resource` | `docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md` and the status manifest keep `floaty.window`, `floaty.rawWindow`, `compat.floaty`, and global `floaty` unavailable. | A future overlay provider must use explicit opaque handles, user-visible permission flow, listener cleanup, and destroy/closeAll cleanup tests. |
| `db` | `verified_zero_after_close` | `NodeDatabaseBridgeInstrumentationTest#jsDatabaseBridgeUsesFakeTransportAndBlocksShadowing`, `#androidDatabaseProviderPersistsTransactionsAndDeniesEscapes`, and `#androidDatabaseProviderRegistersAndClosesResources` cover `database/sqlite` open/transaction/close, `database_active_handle_count=0`, bridge active count zero, `node:sqlite` denial, `ATTACH`, and `load_extension` denial. | SQL escape paths are rejected before persistent external resources can be opened. |
| `websocket/fetch` | `verified_pending_zero_and_default_denial` | `NodeFetchBridgeInstrumentationTest#jsFetchModuleUsesFakeTransportAndKeepsRawModulesDisabled`, `#jsFetchPolicyHardeningUsesFakeTransport`, `NodeWebSocketBridgeInstrumentationTest#websocketMvpUsesControlledBridgeAndKeepsRawSocketsDisabled`, and `#androidWebSocketProviderStaysDisabledInNormalBuilds` cover timeout, abort, service destroy, raw network denial, diagnostics active counts, and pending=0. | Live OkHttp destroy cleanup remains covered by `verifyNodeLiveFetchGate` and `verifyNodeLiveWebSocketGate` when `autojs.nodejs.network.experimental=true`; normal builds deny these providers by default. |

## Device Selector Set

The connected-device gate selects the focused methods registered by `nodeJsRhinoCompatResourceCleanupSecurityInstrumentationClasses`, including:

- `NodeBridgeResourceRegistryInstrumentationTest`
- `NodeBridgeResourceLeakStressInstrumentationTest`
- `NodeImageBridgeExpansionInstrumentationTest#jsImageExpansionUsesFakeTransportAndBlocksShadowing`
- `NodeAutoJsBridgeInstrumentationTest#mediaProjectionAndImageModulesUseFakeTransportAndBlockShadowing`
- `NodeAccessibilitySelectorBridgeInstrumentationTest#jsRhinoCompatSelectorFacadeUsesFakeTransport`
- `NodeAccessibilitySelectorBridgeInstrumentationTest#jsAccessibilitySelectorV2HonorsTimeoutAndCancellation`
- `NodeUiBridgeInstrumentationTest#jsUiModuleUsesFakeTransportForLayoutEventsAndPolicy`
- `NodeUiBridgeInstrumentationTest#androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource`
- `NodeDatabaseBridgeInstrumentationTest#jsDatabaseBridgeUsesFakeTransportAndBlocksShadowing`
- `NodeDatabaseBridgeInstrumentationTest#androidDatabaseProviderPersistsTransactionsAndDeniesEscapes`
- `NodeDatabaseBridgeInstrumentationTest#androidDatabaseProviderRegistersAndClosesResources`
- `NodeFetchBridgeInstrumentationTest#jsFetchModuleUsesFakeTransportAndKeepsRawModulesDisabled`
- `NodeFetchBridgeInstrumentationTest#jsFetchPolicyHardeningUsesFakeTransport`
- `NodeFetchBridgeInstrumentationTest#androidFetchProviderStaysDisabledInNormalBuilds`
- `NodeWebSocketBridgeInstrumentationTest#websocketMvpUsesControlledBridgeAndKeepsRawSocketsDisabled`
- `NodeWebSocketBridgeInstrumentationTest#androidWebSocketProviderStaysDisabledInNormalBuilds`
- `NodeSecurityRegressionInstrumentationTest#phase5BridgeNetworkUiAndShellBoundariesStayDenied`
- `NodeSecurityRegressionInstrumentationTest#phase7CapabilityBypassDeniesBeforeProviderDispatch`
- `NodeSecurityRegressionInstrumentationTest#phase7ProviderPolicyAndPluginEscapesStayDeniedBeforeDispatch`

## Security Boundary

The gate requires evidence that denial paths do not dispatch work or retain resources:

- fetch invalid schemes and raw `http`/`https`/`net` denial
- WebSocket invalid scheme and raw `net`/`tls` denial
- UI raw View absence, foreign-handle denial, WebView security gate denial
- database scoped-name, `ATTACH`, `DETACH`, and `load_extension` denial
- undeclared bridge capability denial before provider dispatch
- design-gated `events`, `floaty`, and Rhino `ui.run` paths creating no resource

S11-30 is therefore a compatibility maintenance gate: future Rhino compat facade work must either satisfy active resource zero after stop/timeout/close/destroy or explicitly remain design-gated with a residual policy.
