# AutoJs6 Node.js Error Catalog

Last reviewed: 2026-08-21.

Errors may originate in either process. The host owns routing, trust, bridge,
permission, workspace, provider, queue, and scheduling failures. The runtime plugin
owns Node/V8/module-loader/runtime failures. Preserve the original error code, name,
message, request ID, and execution ID when reporting a failure.

## Host Routing and Plugin Availability

- **ERR_AUTOJS6_NODE_DISABLED**: this host build is rhinoOnly.
- **ERR_AUTOJS6_NODE_RUNTIME_UNAVAILABLE**: no usable runtime path exists.
- **ERR_AUTOJS6_NODE_PLUGIN_UNAVAILABLE**: the required plugin cannot be discovered,
  trusted, enabled, bound, or used for the device ABI.
- **ERR_AUTOJS6_NODE_PLUGIN_CONTRACT_MISMATCH**: host and plugin contract ranges do
  not overlap.
- **ERR_AUTOJS6_NODE_PLUGIN_EXECUTION_LOST**: an in-flight plugin execution was lost.
- **ERR_AUTOJS6_NODE_PLUGIN_BUSY**, **ERR_AUTOJS6_NODE_ENGINE_BUSY**: bounded
  concurrency rejected the request.
- **ERR_AUTOJS6_NODE_QUEUE_FULL**, **ERR_AUTOJS6_NODE_QUEUE_TIMEOUT**,
  **ERR_AUTOJS6_NODE_QUEUE_CANCELLED**: queue policy ended the request.

## Bridge and Capability

- **ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED**: the project did not declare the
  required capability.
- **ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED**: Android/host policy denied authority.
- **ERR_AUTOJS6_BRIDGE_INVALID_REQUEST**: malformed or out-of-contract request.
- **ERR_AUTOJS6_BRIDGE_CANCELLED**, **ERR_AUTOJS6_BRIDGE_TIMEOUT**: terminal
  cancellation or timeout.
- **ERR_AUTOJS6_BRIDGE_PROCESS_DEAD**: the peer process died.
- **ERR_AUTOJS6_BRIDGE_RATE_LIMITED**, **ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT**:
  configured request/resource budget was exceeded.
- **ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED**: a host provider failed after validation.

## Workspace, Modules, and Policy

Filesystem errors use **ERR_AUTOJS6_FS_*** codes for NUL bytes, absolute-path denial,
scope/realpath/symlink escape, and watch limits. Module-source providers use
**ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_*** for invalid, denied, cancelled, timed-out,
or failed requests. Feature boundaries use specific codes for disabled builtins,
network, process APIs, workers, native addons, or Java interop.

Historical **ERR_AUTOJS6_EMBEDDED_NODE_*** codes can still appear from an older plugin
or compatibility payload. They are legacy wire names, not evidence that the current
host contains an embedded runtime.

The host constant inventory is **NodeRuntimeErrorCodes.kt**. Runtime-plugin-specific
codes and JavaScript error shaping are authoritative in the plugin repository.
