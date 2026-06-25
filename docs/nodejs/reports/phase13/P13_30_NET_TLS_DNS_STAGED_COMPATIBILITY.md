# P13-30 net/tls/dns Staged Compatibility

Status: partial

P13-30 records the existing `net` / `tls` / `dns` / `dns/promises` default
limited builtins as a Desktop-profile staged compatibility subset, and adds a
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

## Verification

- Host verifier:
  `tools/nodejs/phase13/verify-net-tls-dns-staged-compatibility.js`
- Gradle gate: `:app:verifyNodePhase13NetTlsDnsStagedCompatibility`
- Focused device gate:
  `NodeNetTlsDnsStagedParityInstrumentationTest`
- Existing regression gates:
  `NodeRawNetworkPolicyInstrumentationTest` and the limited builtin sections in
  `EmbeddedScriptMvpInstrumentationTest`

## Remaining Work

- Decide whether the raw Node network module opt-in can graduate beyond
  debug/build-time use, and define connection count, byte, timeout, host
  allow/deny, and background execution policy.
- Decide whether server sockets can ever be enabled, including bind-address,
  port ownership, foreground disclosure, packaged rollback, and lifecycle
  cleanup policy.
- Add native fd/socket leak instrumentation for close/destroy, timeout,
  process-stop, and provider-destroy paths before any broader raw socket
  promotion.
- Complete DNS privacy review before exposing Android resolver state, custom
  DNS servers, reverse lookup, or private-network topology.
- Define TLS session/certificate policy before exposing ALPN, certificate
  chains, keying material, client certificates, or custom trust hooks.
