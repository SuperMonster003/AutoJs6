# worker-cpu

CPU task example that uses `worker_threads` when available and falls back to the
main thread while the Safe Node Profile keeps workers default-disabled.

- Capabilities: `worker_computation`
- Expected provider: native `worker_threads` when explicitly enabled; in-process fallback otherwise
- Packaged support: blocked until native worker execution and packaged source graph verification are promoted
- Security limitations: bridge, filesystem, and network authority are not inherited by worker code

Expected output is listed in `expected-output.txt`.
