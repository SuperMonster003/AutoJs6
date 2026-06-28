# P13-30 net/tls/dns Staged Compatibility

Status: ready

P13-30 records the v1.1 `net` / `tls` / `dns` / `dns/promises` default limited
builtins as a Desktop-profile staged compatibility subset, and adds a
debug/build-time raw Node network module opt-in for selected native Node
builtins. The default surface remains shape-first and fail-closed: scripts can
inspect common classes, constants, and loopback/IP-literal DNS helpers without
opening sockets or external DNS. When built or launched with
`autojs.nodejs.rawNetworkModules.experimental=true`, CommonJS
`require("dns")`, `require("dns/promises")`, `require("http")`,
`require("https")`, `require("net")`, and `require("tls")` route to Node's
native builtin modules.

## Promoted Evidence

- `net` / `node:net`, `tls` / `node:tls`, `dns` / `node:dns`, and
  `dns/promises` / `node:dns/promises` resolve to reserved limited builtins and
  cannot be shadowed by local `node_modules`.
- `net.Socket`, `net.Server`, `net.SocketAddress`, `net.BlockList`, IP helpers,
  auto-select-family helpers, bytes counters, close/destroy state, and
  `getConnections()` expose deterministic compatibility shapes without raw fd
  handles.
- `dns.lookup()` and `dns.promises.lookup()` are limited to deterministic
  `localhost`, loopback aliases, and IP literals. Non-local hostnames return
  `ENOTFOUND`, `resolve*()` and `reverse()` reject with the canonical AutoJs6
  network-disabled code, and `getServers()` returns an empty frozen list.
- The DNS shape now covers Node v24 `resolve(hostname, rrtype)`,
  `resolve4()` / `resolve6()` TTL options, typed MX/NAPTR/SOA/SRV/CAA/TLSA
  record declarations, `Resolver({ timeout, tries, maxTimeout })`, and the
  runtime DNS error constants already exposed by the limited facade. These
  entries stay fail-closed until network DNS is promoted.
- `tls.createSecureContext()`, `tls.TLSSocket`, `tls.Server`, safe metadata
  helpers, cipher/root-certificate constants, ticket-key shape checks, and
  `checkServerIdentity()` expose package-probing compatibility without opening
  a TLS transport.
- TypeScript declarations cover the staged subset and record-result shapes, and
  are exercised by the TypeScript smoke.
- `autojs.nodejs.rawNetworkModules.experimental` is registered as a
  release-forbidden experimental build flag, plumbed through Kotlin request
  serialization, JNI/runtime adapter execution flags, and the embedded
  JavaScript bootstrap.
- `NodeNetTlsDnsStagedParityInstrumentationTest` now includes an explicit
  opt-in path that verifies native `http` / `net` / `tls` construction does not
  throw AutoJs6 network-denied errors and exercises the user-facing
  `dns.resolve("www.github.com")` sample path.
- The external DNS sample is opportunistic evidence, not a deterministic CI
  prerequisite: the focused opt-in test accepts either native DNS resolution or
  a native DNS error, and only asserts that AutoJs6 raw-network policy denial is
  not produced while the debug/build-time opt-in is enabled.

## Boundary

The following remain unavailable:

- raw TCP/TLS client sockets, fd/native handles, socket pooling, and native
  stream backpressure in the default Safe profile;
- `net.Server.listen()` / `tls.Server.listen()` server authority;
- network DNS resolution, Android resolver server discovery, private network
  search-domain leakage, TLSA/other RR data retrieval, reverse lookups, and
  custom DNS servers in the default Safe profile;
- TLS sessions, certificate exchange, ALPN negotiation, renegotiation,
  keying-material export, client certificates, and custom trust policy outside
  the raw native module opt-in;
- packaged APK INTERNET permission proof and live Android network evidence for
  this Desktop-profile path.

## v1.1 Release Posture

P13-30 keeps real TCP/TLS client socket authority rejected for v1.1
release/default profiles. The supported release path is the controlled
HTTP/HTTPS client facade from P13-29 plus the limited shape-only `net`/`tls`/DNS
facades above. Any future raw client socket provider must be a separate
promotion gate with host allow/deny policy, connection counts, byte/time
budgets, background behavior, Android permission mapping, lifecycle cleanup,
and rollback evidence.

Server sockets are stable-denied for v1.1. `net.Server.listen()` and
`tls.Server.listen()` remain fail-closed for every bind address, including
loopback. No packaged metadata, project profile, or raw-network debug flag may
turn an AutoJs6 app into a listening network server before a separate server
socket provider owns bind-address policy, port ownership, foreground/background
behavior, listener lifecycle cleanup, disclosure, and rollback.

DNS privacy review is closed for v1.1 by denial. Default `dns` and
`dns/promises` remain limited to loopback, localhost aliases, IP literals, and
an empty frozen `getServers()` result. Android resolver servers, private search
domains, VPN/private DNS state, reverse lookup, custom DNS servers, and local
network topology are not exposed to JavaScript.

`autojs.nodejs.rawNetworkModules.experimental=true` remains debug/build-time
only, release-forbidden, and unavailable through project or packaged APK
metadata. Its rollback is disabling the Gradle/build property. It has no
published-profile stability contract and no AutoJs6-managed connection,
byte, or time budget; those budgets are prerequisites for any future promotion,
not behavior granted by the debug opt-in.

## Verification

- Host verifier:
  `tools/nodejs/phase13/verify-net-tls-dns-staged-compatibility.js`
- Gradle gate: `:app:verifyNodePhase13NetTlsDnsStagedCompatibility`
- Focused device gate:
  `NodeNetTlsDnsStagedParityInstrumentationTest`
- Existing regression gates:
  `NodeRawNetworkPolicyInstrumentationTest` and the limited builtin sections in
  `EmbeddedScriptMvpInstrumentationTest`

## Future Promotion Work

- Add native fd/socket leak instrumentation plus packaged INTERNET/live-network
  proof for close/destroy, timeout, process-stop, and provider-destroy paths
  before any broader raw socket promotion.
- Track TLS session/certificate policy as future promotion design before
  exposing ALPN, certificate chains, keying material, client certificates, or
  custom trust hooks; it is not a separate P13-30 blocker while TLS sessions
  remain denied.

The type declarations, docs, desktop matrix, surface manifest, and Gradle gate
are evidence-only for this v1.1 limited-facade decision. Future raw socket or
live network promotion must satisfy the fd/socket leak and packaged
INTERNET/live-network evidence item above, but that evidence is not a P13-30
v1.1 blocker while raw sockets and network DNS stay denied.
