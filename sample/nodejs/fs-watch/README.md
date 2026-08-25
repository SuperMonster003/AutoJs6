# fs-watch

Scoped `fs.watch()` lifecycle example.

- Capabilities: project-local watch creation and close
- Expected provider: Embedded Node scoped filesystem watcher facade
- Packaged support: not part of the packaged smoke set yet
- Security limitations: Android app permissions define device-visible reach;
  watcher quota and sensitive-path denials remain enforced

Expected output is listed in `expected-output.txt`.
