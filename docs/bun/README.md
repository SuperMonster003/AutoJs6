# Bun Runtime plugin integration

AutoJs6 host build 5278 introduces a distinct `bun` JavaScript engine backed by the
`AutoJs6-Plugin-Bun-Runtime` application. User source is never evaluated by Rhino or
the Node.js plugin. The host selects an enabled and authorized Bun provider, sends a
bounded cleartext source snapshot through a read-only file descriptor, and waits for
the provider's dedicated `:bun_runtime` process.

## Selecting Bun

Use either an explicit directive or one of the supported compound file suffixes:

```javascript
"bun";

console.log(`Bun ${Bun.version} on ${process.platform}`);
```

```text
*.bun.js
*.bun.ts
*.bun.tsx
```

Engine selections are exclusive. A source that selects Bun together with Rhino or
Node.js fails before dispatch instead of falling back to another engine.

## Version 0.1 boundary

The first protocol deliberately supports a bounded single-source execution model:

- JavaScript, TypeScript, and TSX are parsed directly by Bun.
- Standard output and standard error are delivered as bounded callback chunks.
- Host stop requests and configured timeouts terminate the Bun child process.
- Encrypted AutoJs6 entry files are decrypted by the host into a temporary snapshot;
  both host and provider snapshots are deleted after execution.
- The provider starts Bun with `--no-install`. It does not implicitly download missing
  packages.

It does not yet provide AutoJs6 globals, a Java or Android API bridge, relative project
modules, Bun workspaces, `preExecuteScript`, UI execution flags, or packaged APK/inrt
execution. Those cases fail closed. Use the Node.js or Rhino engine when a script needs
their existing AutoJs6 integration.

## Runtime and compatibility

The provider contract is published by `plugin-api/bun-runtime-api`. The runtime plugin
requires host version code 5278 or newer and Android 14 (API 34) or newer. It currently
ships the official Bun 1.4.0 Android executables for `arm64-v8a` and `x86_64`.
AutoJs6 applies its normal plugin enablement, trust, ABI, and minimum-host-version checks
before binding. Bun 1.4.0 calls `close_range`; Android 13 and older app seccomp policies
reject that syscall with `SIGSYS`, so the plugin manifest prevents installation there.

The bundled ELF files are statically checked for 16 KiB `PT_LOAD` alignment. This is a
packaging gate, not a substitute for execution on a real 16 KiB page-size device; the
release checklist must keep that device test explicit until it has been completed.

Packaged applications cannot depend on AutoJs6's signature-protected external plugin,
so the APK builder rejects Bun entries rather than silently compiling or rerouting them.
