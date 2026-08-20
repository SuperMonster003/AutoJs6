# http-client-compat

Controlled HTTP client example for the stable network surface.

- Capabilities: `node:http`, Agent construction, stable controlled network behavior
- Expected provider: Embedded Node HTTP facade with declared `network` permission
- Packaged support: not part of the packaged smoke set yet
- Security limitations: controlled calls remain permissioned and budgeted; native Node networking has separate caller-owned limits

Expected output is listed in `expected-output.txt`.
