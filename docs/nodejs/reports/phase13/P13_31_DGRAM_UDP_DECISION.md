# P13-31 dgram UDP Decision

Status: partial

P13-31 records `dgram` / `node:dgram` as stable-denied for the current Node
Profile v1.1 Desktop track. UDP is not promoted as a client, server, or
shape-only facade in this phase because the required Android provider,
packaged permission, socket lifecycle, and fd cleanup evidence is not present.

## Decision

- `require("dgram")`, `require("node:dgram")`, `require.resolve("dgram")`,
  and `require.resolve("node:dgram")` reject with the canonical AutoJs6
  builtin-disabled code.
- Local `node_modules/dgram` and transitive packages such as
  `require("needs-dgram")` cannot shadow or bypass the reserved disabled
  builtin decision.
- UDP client sockets, UDP server bind/listen, multicast and broadcast remain
  unavailable.
- No UDP socket is opened, so the current gate does not add Android INTERNET
  permission mapping, multicast permission mapping, fd ownership, or packet
  lifecycle obligations.

## Boundary

The following remain future work before any UDP promotion:

- unicast UDP client send/recv semantics, packet size limits, timeout policy,
  queue limits, and background execution behavior;
- server bind/listen policy, bind-address restrictions, port ownership,
  foreground disclosure, lifecycle cleanup, and packaged rollback behavior;
- multicast and broadcast remain unavailable until separate capability tiers,
  permission mapping, user disclosure, and device behavior evidence exist;
- fd/native handle ownership, close/destroy cleanup, process-stop cleanup,
  provider-destroy cleanup, and leak instrumentation;
- packaged APK INTERNET and multicast permission proof plus live Android UDP
  smoke under an explicit Desktop-profile opt-in.

## Verification

- Host verifier: `tools/nodejs/phase13/verify-dgram-udp-decision.js`
- Gradle gate: `:app:verifyNodePhase13DgramUdpDecision`
- Focused device gate:
  `NodeDgramUdpDecisionInstrumentationTest`
- Existing regression gates:
  `NodeRawNetworkPolicyInstrumentationTest`,
  `NodeCoreBuiltinCompatibilityInstrumentationTest`, and
  `NodeSecurityRegressionInstrumentationTest`

## Remaining Work

- Decide whether a future Desktop-profile UDP provider should split unicast
  client, server bind/listen, broadcast, multicast, and source-specific
  multicast into independent capability tokens.
- Define Android manifest synthesis for `android.permission.INTERNET`,
  multicast lock / Wi-Fi multicast behavior, and packaged disclosure.
- Add fd/socket leak instrumentation before exposing any UDP socket object.
- Add live-device UDP tests for packet budgets, timeout/cancel behavior,
  network loss, background policy, and process/service destroy cleanup.

The docs, desktop matrix, release matrix, surface manifest, and Gradle gate are
evidence checks only. They do not add a second blocker for the same UDP provider
permission, packaged, fd-leak, and lifecycle evidence.
