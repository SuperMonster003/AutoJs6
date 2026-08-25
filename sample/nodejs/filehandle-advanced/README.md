# filehandle-advanced

Scoped file handle example using `fs/promises.open()`.

- Capabilities: FileHandle open, write, sync, close
- Expected provider: Embedded Node scoped filesystem facade
- Packaged support: not part of the packaged smoke set yet
- Security limitations: Android app permissions define device-visible reach;
  sensitive `/proc`, `/sys`, and `/dev` paths remain denied

Expected output is listed in `expected-output.txt`.
