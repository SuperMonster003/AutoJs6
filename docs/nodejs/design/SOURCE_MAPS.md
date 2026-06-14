# Embedded Node Source Maps

Source map support is a diagnostics feature for the AutoJs6 Safe Node Profile. It improves uncaught script `errorStack` output without granting broader filesystem or loader access.

## Current Scope

Supported in v1:

- inline `//# sourceMappingURL=data:application/json;base64,...`
- inline percent-encoded `data:` JSON source maps
- external `.map` files resolved relative to the generated script or module file
- external maps that stay inside `workingDirectory`
- external maps injected through `moduleSources`
- version 3 `sources` and `mappings`
- line, column, and source-name mapping for embedded result `errorStack`

Unsupported in v1:

- source-map consuming JavaScript APIs
- inspector/debugger integration
- coverage integration
- network, `file:`, `content:`, or other protocol map URLs
- query or hash map URLs
- loading original sources referenced by the map
- loader hooks or `node:module` integration

## Security Policy

Source maps must not relax scoped filesystem rules. External map paths are validated before reading:

- no NUL bytes
- no path escape outside `workingDirectory`
- no `/proc`, `/sys`, or `/dev`
- no symlink or realpath escape
- no protocol URLs except inline `data:`

If an external map is denied, the original script error remains intact. The result envelope records diagnostics such as `embedded_script.source_map_denied_count` and `embedded_script.source_map_last_error`.

## Packaged APK And Encrypted Scripts

Packaged and encrypted Node scripts do not automatically expose original source maps. A packaged script can use inline maps embedded in the decrypted script text, or external maps explicitly packaged as moduleSources/assets that resolve inside the packaged working directory. The runtime must not infer or read host build paths from a map URL.

Encrypted packaged ESM/module graphs remain separately design-gated. Source map v1 only maps stack strings after an uncaught error reaches the embedded result envelope.
