# disabled-features-demo

Runtime policy boundary example.

The script records the current desktop-like defaults (`child_process`,
`worker_threads`, network modules, and `process.chdir`) and the remaining hard
denials (native addons and `process.binding`). Request-level opt-out flags are
covered by the runtime conformance tests; project metadata does not override
those host request flags.

Expected output is listed in `expected-output.txt`.
