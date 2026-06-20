# require-esm

CommonJS entry that loads a local ESM module with `require()`.

- Capabilities: CommonJS entry, local ESM module graph
- Expected provider: Embedded Node v1.1 partial ESM loader
- Packaged support: not part of the packaged smoke set yet
- Security limitations: local project files only; no remote imports; disabled builtins remain denied

Expected output is listed in `expected-output.txt`.
