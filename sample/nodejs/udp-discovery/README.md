# udp-discovery

UDP discovery-shaped loopback example using Node's native `dgram` builtin.

- Capabilities: `network`, `raw-network`, `udp`
- Expected provider: embedded Node `dgram`
- Packaged support: not yet covered by the packaged-app regression matrix
- Security limitations: the deterministic sample binds loopback only; real UDP discovery payloads are unauthenticated; setting `rawNodeNetworkModulesEnabled=false` disables `dgram`

Expected output is listed in `expected-output.txt`.
