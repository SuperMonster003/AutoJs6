# Runtime closeout in Node and Bun - 2026-10-31

This development batch (api-coverage P3.5 / P4.5, part B, Q120 (b), Q127 (a), Q179 (b), Q190 (b), Q194 (a)) closes what the
earlier batches left open before the Node and Bun columns of the matrix can be called clear. No release, version bump,
registry update or remote push was performed.

## Delivered behavior

- Relative paths (Q120 (b)): `zip`, `files.open` and `files.toFile` resolve relative paths against the script's source
  directory, as Rhino does. The Java facade is generic reflection and cannot tell which argument is a path, so the host hands
  the directory to the runtime and the facade makes the path absolute before the request; the wire protocol is unchanged.
  Node reads it from `engines.myEngine().hostCwd` (new script-visible property), Bun from execution.control `cwd`, which needs
  no grant. A file written into `process.cwd()` still cannot be handed to these calls: that directory is the plugin's private
  project copy, out of the host's reach.
- Color errors (Q127 (a)): the shared core no longer imitates InvocationTargetException. Rhino's real names were recorded
  from a host with the Q116 fix, and the device comparison now goes by name.
- Timeouts (Q179 (b)): `auto.waitFor` and `automator.waitForService` end the whole execution when they time out, from a main
  thread and from a Worker. The exit signal no longer reaches the script's catch as a rejection. `process.exit()` now clears
  the armed global timers (Q201).
- Task options (Q190 (b)): the 18 add*Task declarations take `options: ... | null`, like the other Q189 (b) narrowing.
  `query*Tasks` and `auto.explain` follow, as does the Node runtime (a null option no longer crashes the facade).

## Device evidence

Host debug 5935 built from ad62a8859. Redmi bek749scrwv4wo8h (22120RN86C, Android 13, API 33); read-only emulator AVD_API_33
(x86_64, API 33) for the timeout rounds.

| Round | Device | Rhino | Node main | Node ESM | Node Worker | Bun main | Bun Worker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Q120 relative paths | Redmi, Node 2f645450, Bun 05bc2f6 | - | 7 / 7 | - | 7 / 7 | 7 / 7 | 7 / 7 |
| Q127 colors | Redmi, Node 2f645450, Bun 42c47aa | 633 / 633 | 632 / 633 | 632 / 633 | 632 / 633 | 633 / 633 | 626 + 7 re-run, 7 / 7 |
| Global TODO and JS tools | Redmi, same | 526 / 526 | 524 + 2 skipped | 1 logcat line missed per run, other run passes | 524 + 2 skipped | - | - |
| Q179 automation compare | Redmi, same | 47 / 47 | 54 / 54 | 54 / 54 | 54 / 54 | - | - |
| Q179 undeclared | Redmi, same | - | 8 / 8 | - | - | - | - |
| Q194 automation compare | Redmi, Bun 42c47aa | 54 / 54 | - | - | - | 54 / 54 | 54 / 54 |
| Q194 denied | Redmi, Bun 42c47aa | - | - | - | - | 20 / 20 | 20 / 20 |
| Timeout | AVD_API_33, Node 2025b2e1, Bun 05bc2f6 | 2 / 2 | 2 / 2 | - | 2 / 2 | 2 / 2 | 2 / 2 |

The Node automation set has 54 cases, 7 of them Node-only members with a fixed expectation, so Rhino records 47. The Bun set
is a list of its own with 54 cases, all recorded on Rhino.

The 15 Color error cases on Rhino: InternalError 9, IllegalArgumentException 6, 0 interpreter failures.

The one Node color failure is `colors/toInt.themeColor`: it depends on the device theme, Rhino on this device gives the same
-14606047 and the recorded value -8531 came from another device. The 7 Bun Worker cases ran out of the limit of 1024 calls per
execution in the long group; a run of their own passed.

Timeout rounds: each form waits 30 s after the timeout, and only `before` was logged, neither the code after the wait nor a
timer armed before it. The card asked for this round on the Redmi, but a host that can turn the service on by itself never
reaches the timeout: WRITE_SECURE_SETTINGS (Redmi), root and Shizuku (Xiaomi 968e9f18 now) all make the wait succeed. So the
round needs a device without those channels, and the read-only emulator is one.

The first Node run of that round gave [-1, 0, 3], and fixing the catch gave [-1, 0, 2]: the exit signal reached the script's
catch, then the timer armed before the wait still ran. Both are fixed (Node 5c1ce68b, 2025b2e1). `process.exit()` does not
close sockets or other handles: the event loop ends when they close (HOST-API).

Repeated on the final commits (AVD_API_33, x86_64 debug packages of Node fde5c5a8 and Bun 24a66fe):

| Run | Result |
| --- | --- |
| Timeout, Rhino, Bun main, Bun Worker | 6 / 6 |
| Node AndroidTest: SimpleRunSmokeTest 1, ExecutionModeLifecycleSmokeTest 2, NpmEcosystemSmokeTest 1, NodeRuntimePluginAndroidConformanceTest 33 | OK (37 tests) |

The conformance class uses `process.exit` in 18 places, so it covers the change to the timers on the paths that already had
tests.

Not verified:

- `process.exit()` clearing timers on an arm64 device. The change is JavaScript in the shared core, and it ran on x86_64.
- `new Worker(__filename)` in a project without package.json throws SyntaxError (Q200). It is older than this batch.

## Restoration

Redmi: host b958b46599fe2c7188b58a84a1705553, Bun 293 bbfca1f67936a60a0700ec1a12e216eb, Node 291
a8811d17a2d96bd7cb2c4bb0a103e9a1, all equal to the record; grants restored; the host's accessibility service off, set back
with adb settings as the card asked.

Xiaomi 968e9f18 was used for one Node timeout attempt that stopped being useful once Shizuku turned the service on. Host
315b96ac1fb8e7d6d5f20dcb1be5d7b8, Node 404933ec and Bun 11d1525893c3dad058c175868a3da297 are restored. One side effect is left:
removing the host from enabled_accessibility_services made the system drop
`io.github.supermonster003.autojs6.plugin.three.adapt.a11y/com.google.android.accessibility.selecttospeak.SelectToSpeakService`
from the list too, and two attempts to write it back were dropped by the system again. Whether that entry was active before
the run is unknown, because the record of the starting value was cut at 200 characters. The maintainer has to check it on the
device and turn it on again if it was on.
