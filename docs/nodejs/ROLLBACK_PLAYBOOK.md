# AutoJs6 Node.js Rollback Playbook

Last reviewed: 2026-08-21.

Rollback must preserve engine identity. Never execute a Node-marked script as Rhino.

## Runtime-Plugin Regression

1. Disable the affected plugin version or roll the plugin back to a contract-compatible
   signed build.
2. Verify host trust/authorization and ABI again.
3. Re-run core bridge, engine-switch, and example smoke tests.
4. If no compatible plugin is available, leave Node execution unavailable with the
   explicit plugin error.

## Host Regression

Revert the host routing/bridge change while keeping the shared contract range
compatible. Run cross-repository guards and real-plugin instrumentation before
shipping.

## Product-Level Node Disable

Build with **rhinoOnly** when a release must exclude Node routing. This does not
convert Node projects to Rhino; users must maintain an explicit Rhino entry if they
need that fallback.

Do not restore host libnode packaging, nodeFull/nodeMini, native loaders, or embedded
services as an emergency shortcut. Those paths no longer have a maintained native
implementation in the host.
