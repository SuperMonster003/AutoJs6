# http-client-compat

Controlled HTTP client example for the v1.1 experimental network surface.

- Capabilities: `node:http`, Agent construction, default-denied network behavior
- Expected provider: Embedded Node HTTP facade when network is explicitly enabled
- Packaged support: not part of the packaged smoke set yet
- Security limitations: network access is disabled by default and must stay scoped

Expected output is listed in `expected-output.txt`.
