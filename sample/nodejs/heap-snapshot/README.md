# heap-snapshot

Partial heap diagnostics example. An explicit debug-only request exposes
`inspector.Session`, enables the `HeapProfiler` domain and reads heap usage. It
does not write a heap snapshot artifact yet.

- Capabilities: Inspector `HeapProfiler` and runtime heap usage
- Expected provider: debug-only `inspector.Session`
- Packaged support: not supported in release packaged apps
- Security limitations: full snapshots can be very large and contain secrets;
  bounded app-private capture, cleanup and explicit user export are required
  before `takeHeapSnapshot` is promoted as a supported workflow

Expected output is listed in `expected-output.txt`.
