# wasi-scoped-fs

Controlled WASI facade policy example for Phase 9.

- Capabilities: future `autojs6:wasi` facade shape
- Expected provider: design-gated scoped-fs WASI facade
- Packaged support: not supported for user code in this phase
- Security limitations: raw `node:wasi` is not exposed as a security sandbox; preopens must map through scoped fs

Expected output is listed in `expected-output.txt`.
