# AutoJs6 Node.js Capability Stability Policy

Last reviewed: 2026-08-21.

This policy covers capabilities that cross the runtime-plugin/host boundary. Node
core behavior and native runtime implementation are owned and versioned by the
runtime plugin; Android automation authority is owned and enforced by the host.

## Stability Rules

A capability is stable only when all of the following are true:

1. The plugin declares the script-facing requirement and performs local preflight.
2. The plugin permission manifest knows the capability.
3. The host permission manifest maps it to the correct module/method and Android
   permissions.
4. A host provider implements the operation with bounded JSON-safe values.
5. The capability catalog records the public surface, or a time-bounded historical
   exception explains why the current catalog does not yet contain it.
6. A real-plugin instrumentation test covers success and denial behavior.

Documentation alone cannot promote a capability.

## Registration Order

Register new capabilities in this order:

1. Plugin node_bridge_sources.cpp.
2. Plugin NodeBridgePermissionManifest.java.
3. Host NodeBridgePermissionManifest.kt.
4. The next plugin capability catalog.

Then run the host task **verifyNodeCapabilityManifestAlignment** with an explicit
plugin root. The plugin and host manifests deliberately remain separate because
they protect different process boundaries.

## Compatibility

Removing or narrowing a published capability requires a contract/version decision
and an actionable error. Unknown declarations may be transported for forward
compatibility, but the host never grants an unknown operation. Runtime internals,
raw Android objects, file descriptors, native pointers, and unrestricted Java
reflection are not capabilities and must not cross the bridge.

See [Security model](SECURITY_MODEL.md) and the plugin repository's
**docs/HOST-API.md** for the current script-facing surface.
