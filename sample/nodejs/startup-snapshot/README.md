# startup-snapshot

Startup snapshot policy example for Phase 9.

- Capabilities: startup snapshot policy probe
- Expected provider: design-gated bootstrap snapshot loader
- Packaged support: not part of the packaged smoke set
- Security limitations: snapshots must not contain Android context, provider state, permissions, user paths, or module cache entries

Expected output is listed in `expected-output.txt`.
