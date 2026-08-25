# cpu-profile

Partial CPU profile example backed by `inspector.Session`. It records a short
in-memory profile when the host sends an explicit debug-only inspector request;
the ordinary sample runner exercises the denied-by-default branch.

- Capabilities: Node `Profiler` domain through a controlled inspector session
- Expected provider: debug-only `inspector.Session`
- Packaged support: not part of the packaged smoke set
- Security limitations: release builds cannot enable it; a managed,
  user-selected, app-private `.cpuprofile` export path is not implemented; CPU
  profiles may contain source locations and runtime-sensitive metadata

Expected output is listed in `expected-output.txt`.
