# wasm-worker

WASM worker policy example for Phase 9.

- Capabilities: future WASM worker integration
- Expected provider: design-gated worker/process isolation
- Packaged support: not supported for user code in this phase
- Security limitations: `worker_threads` remains default-disabled; heavy WASM must use bounded worker or process isolation when promoted

Expected output is listed in `expected-output.txt`.
