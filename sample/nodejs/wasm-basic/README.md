# wasm-basic

WebAssembly compute example for Phase 9.

- Capabilities: `WebAssembly.compile()` and `WebAssembly.instantiate()`
- Expected provider: embedded Node V8 WebAssembly runtime
- Packaged support: not part of the packaged smoke set
- Security limitations: native addons remain denied; WASM modules must still obey project resource policy

Expected output is listed in `expected-output.txt`.
