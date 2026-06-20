# compile-cache

Module compile-cache shaped example for repeated CommonJS loads.

- Capabilities: CommonJS module cache, optional `module.enableCompileCache()`
- Expected provider: Embedded Node compile-cache policy gate
- Packaged support: not part of the packaged smoke set yet
- Security limitations: cache storage remains runtime-managed and cannot load native addons

Expected output is listed in `expected-output.txt`.
