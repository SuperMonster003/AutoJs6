# P13-30 net/tls/dns Staged Compatibility

Status: partial

P13-30 records the existing `net` / `tls` / `dns` / `dns/promises` limited
builtins as a Desktop-profile staged compatibility subset. The surface is
shape-first and fail-closed: scripts can inspect common classes, constants, and
loopback/IP-literal DNS helpers, but real network sockets, network DNS queries,
TLS sessions, and server listeners remain unavailable.

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
- `tls.createSecureContext()`, `tls.TLSSocket`, `tls.Server`, safe metadata
  helpers, cipher/root-certificate constants, ticket-key shape checks, and
  `checkServerIdentity()` expose package-probing compatibility without opening
  a TLS transport.
- Minimal TypeScript declarations now cover the staged subset and are exercised
  by the TypeScript smoke.

## Boundary

The following remain unavailable:

- raw TCP/TLS client sockets, fd/native handles, socket pooling, and native
  stream backpressure;
- `net.Server.listen()` / `tls.Server.listen()` server authority;
- network DNS resolution, Android resolver server discovery, private network
  search-domain leakage, reverse lookups, and custom DNS servers;
- TLS sessions, certificate exchange, ALPN negotiation, renegotiation,
  keying-material export, client certificates, and custom trust policy;
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

- Decide whether a Desktop-profile opt-in should allow real `net` client
  sockets, and define connection count, byte, timeout, host allow/deny, and
  background execution policy.
- Decide whether server sockets can ever be enabled, including bind-address,
  port ownership, foreground disclosure, packaged rollback, and lifecycle
  cleanup policy.
- Add native fd/socket leak instrumentation for close/destroy, timeout,
  process-stop, and provider-destroy paths before any raw socket promotion.
- Complete DNS privacy review before exposing Android resolver state, custom
  DNS servers, reverse lookup, or private-network topology.
- Define TLS session/certificate policy before exposing ALPN, certificate
  chains, keying material, client certificates, or custom trust hooks.
