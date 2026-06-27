# heap-snapshot

Heap snapshot policy example for Phase 9 diagnostics.

- Capabilities: heap snapshot and allocation sampling policy probe
- Expected provider: design-gated debug diagnostics
- Packaged support: not supported in release packaged apps
- Security limitations: snapshots require explicit export and must stay app-private until the user exports them

Expected output is listed in `expected-output.txt`.
