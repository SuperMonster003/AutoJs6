# P13-29 HTTP/HTTPS Client Parity

Status: ready

P13-29 records the controlled `http` / `https` client facade as a Desktop
profile compatibility subset. It does not expose raw Node sockets. Requests are
still serialized through AutoJs6 controlled fetch, require an enforced
`network` capability manifest, and remain subject to the existing network
provider flag and Android permission policy.

## Closure

P13-29 closes the controlled HTTP/HTTPS client subset for v1.1. The closure
evidence now includes the focused `http`/`https` client facade instrumentation,
the nine-fixture offline HTTP npm corpus, the live Android/OkHttp loopback
fetch/axios/`http.get()` smoke from `verifyNodeLiveFetchGate`, and the P13-45 packaged
capability metadata proof that `network` maps to
`android.permission.INTERNET`.

## Promoted Evidence

- `http` / `node:http` and `https` / `node:https` resolve to reserved limited
  builtins and cannot be shadowed by local `node_modules`.
- `request()` and `get()` support client request headers, buffered request
  bodies, response status/header metadata, `IncomingMessage` data/end/close
  events, `pipe()` compatibility, timeout, abort, and explicit redirect follow
  through `maxRedirects`.
- `http.Agent` and `https.Agent` expose safe shape-only fields for common
  package compatibility while socket pool maps remain empty.
- `https` keeps certificate validation provider-owned and rejects TLS
  customization such as `rejectUnauthorized: false`, custom secure contexts,
  client certificates, and custom connector hooks.
- P13-29 adds focused instrumentation for TLS/provider error propagation,
  proxy/tunnel option denial, `CONNECT` denial, server/listen denial, and the
  fetch/axios boundary.
- P13-29 expands the offline npm-shaped HTTP corpus from six to nine fixtures
  with follow-redirects-style redirect handling, TLS provider failure handling,
  and per-request proxy configuration denial.
- P13-29 adds minimal TypeScript declarations for the controlled client subset.
- Live Android/OkHttp evidence for fetch, axios, and controlled `http.get()`
  is covered by
  `NodeFetchBridgeInstrumentationTest#jsFetchAndAxiosUseAndroidLiveProviderWhenNetworkExperimentEnabled`
  under `-Pautojs.nodejs.network.experimental=true`.
- Packaged permission synthesis evidence is covered by the P13-45 packaged
  capability metadata gate: declaring `network` records
  `android.permission.INTERNET` through the bridge permission manifest.

## Boundary

The following remain unavailable:

- raw TCP/TLS sockets, fd/native handles, DNS lookup handles, and TLS sessions;
- `createServer().listen()` server authority;
- `CONNECT` tunneling, upgrade authority, custom connector hooks, socket paths,
  and per-request proxy configuration;
- unrestricted desktop Node agent pooling;
- live provider promotion in normal builds without
  `autojs.nodejs.network.experimental=true`.

Full raw socket, DNS lookup, TLS session, and backpressure semantics stay scoped
to P13-30 or later. P13-29 only tracks controlled fetch-backed client
semantics, so future P13-30 raw `net` / `tls` / `dns` authority decisions do
not count as separate P13-29 blockers.

Per-request proxy support stays denied in P13-29; only provider/system-owned
proxy behavior may exist behind controlled fetch.

## Verification

- Host verifier: `tools/nodejs/phase13/verify-http-https-client-parity.js`
- Gradle gate: `:app:verifyNodePhase13HttpHttpsClientParity`
- Focused device gate:
  `NodeHttpDesktopParityInstrumentationTest`
- Existing regression gates:
  `NodeHttpClientFacadeInstrumentationTest`,
  `NodeHttpStreamSemanticsInstrumentationTest`,
  `NodeHttpsClientFacadeInstrumentationTest`,
  `NodeHttpAgentFacadeInstrumentationTest`, and
  `NodeRawNetworkPolicyInstrumentationTest`

## Future Work

- Full raw socket, DNS lookup, TLS session, server/listen, and backpressure
  semantics remain scoped to P13-30 or later.
- Broader packaged install/run network smoke, disclosure UX, and rollback
  diagnostics remain future packaged distribution work beyond this controlled
  client subset.
