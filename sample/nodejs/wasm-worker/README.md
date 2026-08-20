# wasm-worker

WASM worker policy example for Phase 9.

- Capabilities: future WASM worker integration
- Expected provider: stable native `worker_threads` compute isolation
- Packaged support: supported by the three-ABI runtime kit
- Security limitations: workers cannot access AutoJs bridge modules or raw Android objects; heavy WASM must remain within worker count, memory, timeout, and cleanup budgets

Expected output is listed in `expected-output.txt`.
