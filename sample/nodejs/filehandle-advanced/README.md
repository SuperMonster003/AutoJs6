# filehandle-advanced

Scoped file handle example using `fs/promises.open()`.

- Capabilities: FileHandle open, write, sync, close
- Expected provider: Embedded Node scoped filesystem facade
- Packaged support: not part of the packaged smoke set yet
- Security limitations: project-relative files only; host filesystem escape remains denied

Expected output is listed in `expected-output.txt`.
