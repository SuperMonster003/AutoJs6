# fs-watch

Scoped `fs.watch()` lifecycle example.

- Capabilities: project-local watch creation and close
- Expected provider: Embedded Node scoped filesystem watcher facade
- Packaged support: not part of the packaged smoke set yet
- Security limitations: project-relative watch targets only; watcher quota remains enforced

Expected output is listed in `expected-output.txt`.
