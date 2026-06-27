# wasm-plugin

WASM plugin package policy example for Phase 9.

- Capabilities: future JS plus WASM plugin package shape
- Expected provider: JS-only plugin manager with design-gated WASM plugin metadata
- Packaged support: not part of the packaged smoke set
- Security limitations: native service plugins and raw native addons remain disabled; plugin integrity and resource limits are required before promotion

Expected output is listed in `expected-output.txt`.
