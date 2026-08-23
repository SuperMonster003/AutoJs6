# Python Runtime Plugin Protocol V1

Status: versioned V1 contract. R0 froze 1.0; the backward-compatible R5-P1
extension added the 1.1 host capability snapshot, U1-R2 added explicit module
entry at 1.2, and U1-R2 interactive input appends a typed prompt/reply channel
at 1.3. Protocol 1.4 adds explicit bounded JSON results and hash-manifested
output artifacts without changing AIDL: it uses the descriptor array already
declared by `onResult`. Protocol 1.3 appended one method to each execution AIDL
interface; existing declarations and transaction numbers otherwise remain
unchanged.
This protocol does not enable Python execution in the AutoJs6 host by itself.

## Decision

Python is a separate script-engine family backed by an independent plugin APK,
UID, and runtime process. CPython, its standard library, installed packages,
native libraries, and native wheels stay in that plugin. They must not be
loaded into the AutoJs6 process.

The stable engine ID is `python`. The discovery and runtime actions are:

- `org.autojs.plugin.INFO`
- `org.autojs.plugin.python.RUNTIME`

The runtime service uses the signature permission
`org.autojs.permission.PLUGIN`. Discovery does not grant authority: the host
and plugin must pin the resolved component and validate package identity, UID,
signer, version, and update identity. Every Binder entry point must validate
the caller again. Those Android checks belong to R1/R2 and are not claimed by
the R0 API artifact.

The runtime action is for discovery only. After selecting and pinning a
component, the host binds with an explicit component-only Intent whose action
may be null. A conforming service must not require the discovery action in
`onBind()`.

## V1 Binder Surface

```aidl
IPythonRuntimeProvider
    byte[] getRuntimeInfo()
    byte[] getCapabilities()
    void openSession(
        byte[] request,
        ParcelFileDescriptor[] descriptors,
        IPythonSessionOpenCallback admissionCallback,
        IPythonExecutionCallback executionCallback
    )

IPythonSessionOpenCallback
    oneway void onOpened(IPythonExecutionSession session)
    oneway void onRejected(byte[] error)

IPythonExecutionSession
    oneway void start()
    oneway void grantOutputCredits(int count)
    oneway void cancel()
    oneway void close()
    oneway void replyInput(byte[] reply)

IPythonExecutionCallback
    oneway void onStarted(byte[] metadata)
    oneway void onStdout(byte[] chunk)
    oneway void onStderr(byte[] chunk)
    oneway void onResult(byte[] result, ParcelFileDescriptor[] descriptors)
    oneway void onFailed(byte[] error)
    oneway void onCancelled(byte[] cancellation)
    oneway void onInputPrompt(byte[] prompt)
```

All metadata uses the deterministic tagged wire described in
[Plugin Protocol Wire V1](plugin-protocol-wire-v1.md). `Bundle`, Java
serialization, arbitrary `Parcelable` values, class names, host paths, URIs,
and host Java objects are not part of the contract.

`getRuntimeInfo()` and `getCapabilities()` are bounded synchronous metadata
calls. `openSession()` performs only the short admission work defined below.
After both callbacks are non-null and exactly one valid 16-byte request ID has
been decoded, it reports exactly one outcome through `admissionCallback`:
`onOpened` with a non-null session, or `onRejected` with a structured
pre-dispatch error carrying that request ID. A decoded semantic rejection must
not be reported as an arbitrary Binder exception.

There are three synchronous carve-outs before that correlated-callback
obligation begins:

- Caller authentication failure throws the platform `SecurityException`.
- A null admission/execution callback, null request, or envelope failure before
  obtaining one unique valid request ID throws the platform
  `IllegalArgumentException`.
- An already-dead admission/execution callback fails synchronously with the
  platform `DeadObjectException` (or its `RemoteException` transport surface).
- These paths emit zero callbacks and adopt/retain zero receiver PFD copies. Classic AIDL
  may already have unmarshalled receiver-side incoming PFD copies before the
  service method runs, so the synchronous exception path must close every
  non-null incoming copy in `finally` before returning. The host enters
  `FAILED_CLOSED`; none of these exceptions is retry authority.

After the request ID is available, later request/descriptor structural errors
produce exactly one matching `INVALID_REQUEST` rejection, with all partial PFD
ownership rolled back. A null `onOpened` session or any other malformed
admission callback irreversibly enters `FAILED_CLOSED`; a local admission
deadline or close enters `ABANDONED`. Neither state may later accept
`onOpened`, and an execution-session policy is exposed only after the first
valid `onOpened` wins.

For every non-null `onOpened(session)` callback which the admission plan does
not accept, the host immediately sends best-effort `session.close()` on that
non-winning handle. The provider releases every unstarted admitted session and
its adopted PFDs on close or caller/callback Binder death; a late or
duplicate callback cannot retain resources. A time-based unstarted-session
lease is not part of R0 and must not be invented without a later versioned
timeout contract.

An envelope which is too malformed to recover its required request ID cannot
form a correlated execution-error document; it follows the fixed synchronous
`IllegalArgumentException` path above.

Classic AIDL transaction numbers follow declaration order. V1 methods may only
be appended to an interface; existing declarations may not be inserted,
removed, reordered, or changed. An incompatible Binder change requires a new
interface descriptor and runtime action. The R0 gate freezes normalized AIDL
source digests in addition to tagged-wire fixtures.

The cross-APK compatibility surface is the AIDL descriptor/order plus encoded
wire bytes, not shared JVM class identity. Each APK packages its own matching
Kotlin model/codec implementation. R0 therefore does not promise arbitrary
binary mixing of separately copied AARs; R1 must verify a synchronized
`protocol-wire-api` + `python-runtime-api` distribution from consumer builds.

## Schemas

| Schema | ID | Purpose |
| --- | ---: | --- |
| Runtime info | `0x50590001` | Provider and CPython identity plus protocol range |
| Capabilities | `0x50590002` | ABI, optional transports/entry modes, cancellation and limits |
| Resource limits | `0x50590003` | Provider ceilings bounded by host hard limits |
| Payload reference | `0x50590004` | PFD index, role, exact length and SHA-256 |
| Execution request | `0x50590010` | Relative file/dotted module entry point, source and execution limits |
| Session started | `0x50590011` | Negotiated limits, first sequence and runtime generation |
| Output chunk | `0x50590012` | Shared sequence, stream identity and bounded bytes |
| Execution result | `0x50590013` | Exit code, elapsed time and exact output counters |
| Traceback frame | `0x50590014` | Structured Python frame metadata |
| Execution error | `0x50590015` | Stable code/phase and optional Python exception |
| Cancellation | `0x50590016` | Reason, phase and elapsed time |
| Interactive input policy | `0x50590017` | Execution-specific prompt/reply ceilings |
| Input prompt | `0x50590018` | Typed prompt ID, text, echo and reply deadline |
| Input reply | `0x50590019` | Exactly one value, EOF or cancelled response |
| Result policy | `0x5059001a` | Execution-specific JSON and artifact limits |
| Output artifact | `0x5059001b` | Logical path, result PFD index, exact length and SHA-256 |

Schema major `1` is required. A future minor may add optional fields. Unknown
optional fields are skipped; unknown required fields, duplicate scalar fields,
unknown enum values, and a different schema major are rejected.

### Field Tables

`R` means exactly one required field, `O` means zero or one optional field, and
`*` means a bounded repeated field. Enum values use `INT32` and are defined by
the public Kotlin contract.

Runtime info (`0x50590001`):

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `INT32` | R | protocol minimum major |
| 2 | `INT32` | R | protocol minimum minor |
| 3 | `INT32` | R | protocol maximum major |
| 4 | `INT32` | R | protocol maximum minor |
| 5 | `UTF8` | R | provider ID |
| 6 | `UTF8` | R | provider version name |
| 7 | `INT64` | R | provider version code |
| 8 | `INT32` | R | Python implementation |
| 9 | `UTF8` | R | Python version |
| 10 | `INT32` | R | isolation mode |
| 11 | `INT64` | O | minimum host version code |
| 12 | `INT64` | O | maximum host version code |

Capabilities (`0x50590002`):

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `INT32` | R | Python implementation |
| 2 | `UTF8` | R | Python version |
| 3 | `UTF8` | * | supported ABI; at least one |
| 4 | `BOOLEAN` | R | workspace ZIP support |
| 5 | `BOOLEAN` | R | finite stdin snapshot support |
| 6 | `BOOLEAN` | R | structured traceback support |
| 7 | `BOOLEAN` | R | cooperative cancellation support |
| 8 | `INT32` | R | cancellation mode |
| 9 | `DOCUMENT` | R | resource limits |
| 10 | `BOOLEAN` | O | host capability snapshot support; absent means false |
| 11 | `BOOLEAN` | O | module-entry support; absent means false |
| 12 | `BOOLEAN` | O | interactive built-in input support; absent means false |
| 13 | `BOOLEAN` | O | structured JSON result support; absent means false |
| 14 | `BOOLEAN` | O | output artifact support; absent means false |

Resource limits (`0x50590003`):

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `INT64` | R | maximum source bytes |
| 2 | `INT64` | R | maximum workspace ZIP bytes |
| 3 | `INT32` | R | maximum workspace entries |
| 4 | `INT64` | R | maximum expanded workspace bytes |
| 5 | `INT64` | R | maximum stdin bytes |
| 6 | `INT64` | R | maximum aggregate output bytes |
| 7 | `INT32` | R | maximum output chunk bytes |
| 8 | `INT32` | R | maximum outstanding output credits |
| 9 | `INT64` | R | maximum timeout milliseconds |
| 10 | `INT32` | R | maximum concurrent sessions; V1 requires `1` |
| 11 | `INT64` | R | maximum output chunks |
| 12 | `INT64` | O | maximum host capability snapshot bytes; absent means zero |
| 13 | `INT32` | O | maximum input prompt bytes; absent means zero |
| 14 | `INT32` | O | maximum input reply bytes; absent means zero |
| 15 | `INT32` | O | maximum input prompts per execution; absent means zero |
| 16 | `INT64` | O | maximum wait per input reply in milliseconds; absent means zero |
| 17 | `INT32` | O | maximum structured JSON UTF-8 bytes; absent means zero |
| 18 | `INT32` | O | maximum output artifacts; absent means zero |
| 19 | `INT32` | O | maximum output artifact path UTF-8 bytes; absent means zero |
| 20 | `INT64` | O | maximum bytes in one output artifact; absent means zero |
| 21 | `INT64` | O | maximum aggregate output artifact bytes; absent means zero |

Payload reference (`0x50590004`):

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `INT32` | R | fixed payload role |
| 2 | `FD_REF` | R | descriptor-array index |
| 3 | `INT64` | R | exact byte length |
| 4 | `BYTES` | R | 32-byte SHA-256 |

Execution request (`0x50590010`):

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `BYTES` | R | 16-byte request ID |
| 2 | `INT32` | R | selected protocol major |
| 3 | `INT32` | R | selected protocol minor |
| 4 | `UTF8` | R | normalized relative `.py` entry point |
| 5 | `DOCUMENT` | R | source payload reference |
| 6 | `DOCUMENT` | O | workspace ZIP payload reference |
| 7 | `DOCUMENT` | O | stdin payload reference |
| 8 | `UTF8` | * | Python argument |
| 9 | `INT64` | R | timeout milliseconds |
| 10 | `INT64` | R | maximum aggregate output bytes |
| 11 | `INT32` | R | maximum output chunk bytes |
| 12 | `INT64` | R | maximum output chunks |
| 13 | `DOCUMENT` | O | host capability snapshot payload reference |
| 14 | `INT32` | O | entry mode; absent means file; module is required-for-reader |
| 15 | `DOCUMENT` | O | interactive input policy; presence is required-for-reader |
| 16 | `DOCUMENT` | O | result policy; presence is required-for-reader |

Tag 14 remains absent for file mode. When it selects module mode, its wire
header carries the required-for-reader flag: a pre-1.2 reader must reject the
unknown field rather than silently execute the dotted entry with file
semantics.

Tag 15 is absent for background/non-interactive execution. When present, its
wire header is required-for-reader: a pre-1.3 reader rejects the request rather
than accepting user code which may later block or read an unintended process
stdin. The nested policy uses schema `0x50590017`:

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `INT32` | R | maximum prompts for this execution |
| 2 | `INT32` | R | maximum UTF-8 prompt bytes |
| 3 | `INT32` | R | maximum UTF-8 reply bytes |
| 4 | `INT64` | R | monotonic reply deadline duration in milliseconds |

Tag 16 is absent when no explicit result channel is authorized. When present,
its wire header is required-for-reader: a pre-1.4 reader rejects the request
rather than executing code whose result contract it cannot enforce. The nested
policy uses schema `0x5059001a`:

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `INT32` | R | maximum structured JSON UTF-8 bytes; zero disables JSON |
| 2 | `INT32` | R | maximum artifact count; zero disables artifacts |
| 3 | `INT32` | R | maximum logical artifact path UTF-8 bytes |
| 4 | `INT64` | R | maximum bytes in one artifact |
| 5 | `INT64` | R | maximum aggregate artifact bytes |

Session started (`0x50590011`):

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `BYTES` | R | request ID |
| 2 | `INT32` | R | protocol major |
| 3 | `INT32` | R | protocol minor |
| 4 | `UTF8` | R | Python version |
| 5 | `INT64` | R | first shared output sequence |
| 6 | `INT64` | R | effective aggregate output bytes |
| 7 | `INT32` | R | effective output chunk bytes |
| 8 | `INT64` | R | positive runtime generation |
| 9 | `INT64` | R | effective output chunks |

Output chunk (`0x50590012`):

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `BYTES` | R | request ID |
| 2 | `INT64` | R | shared stdout/stderr sequence |
| 3 | `INT32` | R | stream (`stdout` or `stderr`) |
| 4 | `BYTES` | R | non-empty chunk bytes |

Execution result (`0x50590013`):

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `BYTES` | R | request ID |
| 2 | `INT32` | R | exit code, `0..255` |
| 3 | `INT64` | R | elapsed milliseconds |
| 4 | `INT64` | R | stdout bytes |
| 5 | `INT64` | R | stderr bytes |
| 6 | `INT64` | R | stdout chunks |
| 7 | `INT64` | R | stderr chunks |
| 8 | `UTF8` | O | strict structured JSON; presence is required-for-reader |
| 9 | `DOCUMENT` | * | output artifact; each occurrence is required-for-reader |

An output artifact uses schema `0x5059001b`:

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `UTF8` | R | unique normalized logical relative path |
| 2 | `FD_REF` | R | index in the `onResult` descriptor array |
| 3 | `INT64` | R | exact byte length |
| 4 | `BYTES` | R | 32-byte SHA-256 |

Every result descriptor is referenced exactly once. Count, path, per-file and
aggregate sizes must fit both hard V1 ceilings and the request policy. A Host
must validate the typed terminal metadata before it reads any result PFD, then
require a regular file, exact length, immediate EOF, and matching SHA-256.
stdout/stderr are never parsed or reinterpreted as a result.

Traceback frame (`0x50590014`):

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `UTF8` | R | relative or synthetic logical file name |
| 2 | `INT32` | R | positive line number |
| 3 | `UTF8` | R | function name |
| 4 | `UTF8` | O | bounded source line |
| 5 | `INT32` | R | sanitized source origin |

Execution error (`0x50590015`):

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `BYTES` | R | request ID |
| 2 | `INT32` | R | stable error code |
| 3 | `INT32` | R | failure phase |
| 4 | `UTF8` | R | bounded public message |
| 5 | `BOOLEAN` | R | safe-to-retry-before-dispatch diagnostic; false after dispatch |
| 6 | `UTF8` | O | Python exception type; required for Python exceptions |
| 7 | `UTF8` | O | Python exception message; required for Python exceptions |
| 8 | `DOCUMENT` | * | traceback frame; Python exceptions only |

Cancellation (`0x50590016`):

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `BYTES` | R | request ID |
| 2 | `INT32` | R | cancellation reason |
| 3 | `INT32` | R | cancellation phase |
| 4 | `INT64` | R | elapsed milliseconds |

Input prompt (`0x50590018`):

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `BYTES` | R | request ID |
| 2 | `INT64` | R | positive, execution-scoped monotonic prompt ID |
| 3 | `UTF8` | R | bounded prompt text; may be empty |
| 4 | `INT32` | R | echo policy |
| 5 | `INT32` | R | maximum UTF-8 reply bytes for this prompt |
| 6 | `INT64` | R | reply timeout in milliseconds |

Input reply (`0x50590019`):

| Tag | Type | Card. | Field |
| ---: | --- | :---: | --- |
| 1 | `BYTES` | R | request ID |
| 2 | `INT64` | R | exact pending prompt ID |
| 3 | `INT32` | R | reply status |
| 4 | `UTF8` | O | value text; required only for `VALUE`, including empty text |

### Enum Wire Codes

| Enum | Code | Meaning |
| --- | ---: | --- |
| Python implementation | 1 | CPython |
| Isolation mode | 1 | dedicated plugin process |
| Cancellation mode | 1 | process restart only |
| Cancellation mode | 2 | cooperative, then process restart |
| Python entry mode | 1 | file |
| Python entry mode | 2 | module |
| Payload kind | 1 | source |
| Payload kind | 2 | workspace archive |
| Payload kind | 3 | stdin snapshot |
| Payload kind | 4 | host capability snapshot |
| Output stream | 1 | stdout |
| Output stream | 2 | stderr |
| Input echo | 1 | visible text |
| Input echo | 2 | hidden/password text |
| Input reply status | 1 | value |
| Input reply status | 2 | EOF |
| Input reply status | 3 | cancelled |
| Traceback origin | 1 | project |
| Traceback origin | 2 | standard library |
| Traceback origin | 3 | installed package |
| Traceback origin | 4 | generated source |
| Cancellation reason | 1 | requested |

Error codes are `INVALID_REQUEST=1`, `UNSUPPORTED_PROTOCOL=2`,
`UNSUPPORTED_CAPABILITY=3`, `BUSY=4`, `SOURCE_REJECTED=5`,
`WORKSPACE_REJECTED=6`, `PYTHON_EXCEPTION=7`, `TIMEOUT=8`,
`OUTPUT_LIMIT_EXCEEDED=9`, `CANCELLATION_FAILED=10`,
`RUNTIME_CRASHED=11`, `STORAGE_EXHAUSTED=12`, `INTERNAL=13`,
`SESSION_OPEN_FAILED=14`, `STDIN_REJECTED=15`,
`HOST_CAPABILITY_SNAPSHOT_REJECTED=16`, `INPUT_TIMEOUT=17`,
`INPUT_LIMIT_EXCEEDED=18`, and `OUTPUT_ARTIFACT_REJECTED=19`. Codes are
append-only.

Failure phases are `NEGOTIATION=1`, `SESSION_OPEN=2`, `INPUT_VALIDATION=3`,
`RUNTIME_START=4`, `EXECUTION=5`, `OUTPUT=6`, `CANCELLATION=7`,
`CLEANUP=8`, `INTERACTIVE_INPUT=9`, and `RESULT=10`. Admission rejection is limited to the negotiation/session-open
phases and their allowed pre-dispatch error codes. An execution callback must
always encode the safe-to-retry-before-dispatch field as false.

`SOURCE_REJECTED`, `WORKSPACE_REJECTED`, `STDIN_REJECTED`, and
`HOST_CAPABILITY_SNAPSHOT_REJECTED` use `INPUT_VALIDATION`;
`PYTHON_EXCEPTION` uses `EXECUTION`;
`OUTPUT_LIMIT_EXCEEDED` uses `OUTPUT`; and `CANCELLATION_FAILED` uses
`CANCELLATION`. `INPUT_TIMEOUT` and `INPUT_LIMIT_EXCEEDED` use
`INTERACTIVE_INPUT`; `OUTPUT_ARTIFACT_REJECTED` uses `RESULT`. Timeout, runtime crash, storage exhaustion, and internal errors
may identify the post-dispatch phase in which they occurred. This mapping is
part of V1 and prevents provider-specific error/phase combinations.

## Descriptor Contract

The request has one required read-only source descriptor and may negotiate a
finite workspace ZIP snapshot, finite stdin snapshot, and, since protocol 1.1,
a finite host capability snapshot. Each reference
contains a fixed role, one descriptor-array index, exact byte length, and
SHA-256 digest.

The synchronous `openSession` admission stage must return quickly. It:

1. Revalidates caller identity and decodes the bounded envelope.
2. Rejects null callbacks, a null descriptor array or element,
   negative/out-of-range indices, duplicate references, unreferenced supplied
   descriptors, unsupported access modes, and capability/ABI/version mismatch.
3. Atomically adopts every accepted complete Binder-unmarshalled receiver PFD
   copy into session-owned lifetime as one all-or-nothing operation. A provider
   which makes an additional copy must preserve the complete PFD, including a
   reliable pipe's communication descriptor; raw
   `ParcelFileDescriptor.dup(pfd.fileDescriptor)` is not conforming because it
   loses producer `closeWithError` evidence. On any ownership failure, it
   closes every unadopted or partially owned receiver copy and reports one
   structured admission rejection.
4. Returns without reading payload contents, hashing, extracting, starting a
   runtime, or executing user code.

Only after the first accepted `start()` may a bounded worker consume the
session-owned receiver copies. It must:

1. Give one reader exclusive ownership of each adopted copy; no code may read or
   seek the same open-file description concurrently.
2. Use a deadline-aware, cancellable reader for regular files and pipes.
   Cancellation, Binder death, timeout, or the read deadline initiates reader
   interruption and descriptor cleanup. Closing a descriptor from another
   thread is not, by itself, accepted as proof that a hostile blocking read
   wakes; R1 must demonstrate the chosen mechanism with a never-ending pipe.
3. Copy exactly the declared bytes into a plugin-private immutable snapshot
   while computing SHA-256, then require EOF and the declared digest. It must
   never execute or extract directly from sender-controlled storage.
4. Publish the complete snapshot set atomically only after every descriptor
   passes; otherwise remove every partial snapshot and close every resource.
5. Close all unadopted incoming PFDs, adopted receiver copies, reserved result PFDs, and
   delete snapshots on every result, failure, cancellation, timeout, death,
   partial-open failure, and local-close path.

Binder transfer gives the receiver its own descriptor lifetime while the
underlying open-file description may still share position and backing storage.
Descriptor transfer or additional duplication is therefore not a content or
TOCTOU boundary. The caller closes regular-file originals after `openSession`
returns and must not seek the transferred open-file description during staging.
For a reliable pipe, an AIDL `in` parameter duplicates both the data and
communication descriptors without silently relinquishing the caller's copy.
The caller must keep its original passed endpoint open and unread until the
receiver reports `onStarted` or a terminal callback; closing it earlier can
inject an `OK` status from a duplicate endpoint, mask producer `closeWithError`,
or make the receiver observe a false peer death. The caller then closes that
original in its normal cleanup path. Even if a backing file is mutated through
another descriptor, the digest check and
receiver-private snapshot remain the execution boundary. CPython consumes only
the published private snapshot.

In file mode, the source entry point is a normalized project-relative `.py`
path. Absolute paths, Windows drive prefixes (including `C:main.py`),
backslashes, empty segments, `.`/`..`, control characters, non-NFC text, and
non-Python extensions are rejected. An empty source payload is a valid Python
program when its length and empty-content SHA-256 match. The wire never exposes
a host sandbox path.

Protocol 1.2 module mode is explicit and requires both a project workspace and
`supportsModuleEntry=true`. Its entry point is a normalized dotted ASCII module
name. Every segment must be a non-keyword Python identifier, and `__init__` is
rejected as a final segment. The source path must use the exact lowercase
`.py` extension and no directory or filename stem may itself contain a dot, so
the mapping stays reversible. The Host deterministically maps an admitted entry
such as `pkg/main.py` to `pkg.main`; the provider maps it back to
`pkg/main.py`, requires the staged file to equal the independently verified
SOURCE bytes, prepends only the project root to `sys.path`, and executes it as
`__main__` with standard module metadata. Module capability additionally
requires workspace-archive capability and a protocol range reaching 1.2.

Workspace archives remain a negotiated optional transport. A provider may
advertise `supportsWorkspaceArchive=true` only after ZIP entry normalization,
duplicate/case policy, symlink rejection,
compression and disk ceilings, and the rule that an archive cannot contain or
shadow the separately supplied entry point are implemented.

The R4 project profile uses descriptor 0 for the separately supplied source and
descriptor 1 for the workspace ZIP. ZIP names use NFC-normalized UTF-8 and `/`
separators. Absolute or drive-prefixed names, backslashes, control characters,
empty, `.` or `..` segments, duplicate or case-fold aliases, file/directory
prefix conflicts, symlink or special-file metadata, unsupported compression,
ZIP64 and multi-disk archives are rejected. The provider validates the archive
length and digest before extraction, bounds compressed bytes, entry count and
expanded bytes by its advertised limits, and extracts only into a request-owned
private directory. It then writes the already verified SOURCE bytes to the
normalized entry point. User writes remain inside that temporary directory and
are never written back to the host project.

The R5-P1 profile appends a host capability snapshot after the source and
optional workspace descriptors (index 1 for a single file, index 2 for a
project). It is an optional, provider-negotiated, at-most-64-KiB JSON document
bound to the request ID and entry point. It contains only whitelisted scalar
execution/app/device data and grants; it must not contain Android/Java live
objects, Binder handles, host paths, device identifiers, or authority for
capabilities not explicitly listed. The provider validates it before
`onStarted`. Project file reads use the already isolated R4 workspace rather
than a live host callback. Detailed compatibility rules are in
`docs/python/R5_P1_CAPABILITY_SNAPSHOT_PROTOCOL.md`.

Protocol 1.0 through 1.3 require the `onResult` descriptor array to be empty.
Protocol 1.4 authorizes only output artifacts named by the typed result manifest
under the exact descriptor-reference and byte/digest rules above.

After the protocol receiver has copied and verified a successful protocol 1.4
result, the current Host presents it as follows without changing the wire
contract:

- A non-null `structuredJson` value is appended to the global console as
  `[result] <json>`. JSON-looking stdout remains ordinary diagnostic output.
- Verified artifacts are first written to a hidden staging directory, with the
  logical path, byte length and SHA-256 revalidated, and then published by one
  same-parent directory rename. The final directory is
  `<project-root>/.python-artifacts/execution-<engine-id>-<request-uuid>/` for a
  project or the equivalent path below a single script's parent directory.
  Every published file is reported as `[artifact] <absolute-path>`.
- The `.python-artifacts` top-level directory is reserved for Host-published
  results and excluded from later Python project workspace snapshots, so prior
  outputs cannot consume transport budgets or become source input. A project
  entry point inside this directory is rejected.
- No artifact directory is created when the result contains no artifacts.
  Existing execution directories are never overwritten. Interruption, unsafe
  paths, filesystem conflicts or write/publish failures remove the staging
  tree and fail the Host execution with
  `PYTHON_RUNTIME_RESULT_PUBLICATION_FAILED` instead of reporting a successful
  partial publication.

## Lifecycle And Replay Boundary

`openSession` validates admission metadata and acquires descriptor ownership but
must not read payload content or execute user code. The first accepted
`start()` is the dispatch boundary. From that moment, Binder failure, timeout,
plugin death, or any execution error must never cause automatic replay: user
code may already have run. A pre-dispatch retry hint is only valid on a typed
admission rejection and is never sufficient authority by itself.

Only a still-pending, un-cancelled `CREATED` session is classified as safe
before dispatch. Explicit cancel, local close/abandonment, or a fail-closed
protocol violation forbids automatic replay even if `start()` was never sent.

The session rules are:

- One active session and zero provider-side queueing in V1.
- Duplicate `start()` never starts a second execution.
- `cancel()` and `close()` are idempotent.
- `onStarted` occurs at most once, after `start`, and before output; an early
  failure or cancellation may be terminal without it.
- stdout and stderr share one strictly increasing sequence.
- Every provider-sent output callback consumes one provider-side shared credit.
  Because callbacks are one-way, a host validation failure closes the session;
  the sequence or credit cannot be reused without an acknowledgement protocol.
- Chunk bytes and aggregate stdout plus stderr bytes are bounded.
- Total non-empty output chunk count is negotiated and bounded independently of
  the byte limit, and therefore cannot exceed the accepted output byte count.
- Result counters must exactly match accepted chunks.
- A result-enabled request accepts only explicitly typed strict JSON and
  manifest-referenced regular-file PFDs; stdout text has no result semantics.
- Result PFDs and private snapshots remain owned until callback delivery and are
  closed/deleted on delivery failure, cancellation, timeout, death, or close.
- An interactive request permits at most one pending prompt. Prompt IDs start
  at 1 and increase exactly once per accepted prompt; request ID, prompt ID,
  UTF-8 byte ceilings, echo policy and deadline are validated on both sides.
- Each accepted prompt receives at most one exact reply. A duplicate,
  out-of-order, oversized or unsolicited prompt/reply fails closed. A terminal
  event invalidates the pending prompt, and a late reply is ignored.
- Only a foreground user-gesture launch may attach the Host's opaque input
  authorization and Material dialog controller. Background launches omit tag
  15, never open UI, retain finite stdin-snapshot semantics, and reach immediate
  EOF after that snapshot is exhausted.
- Cancellation, the execution deadline, callback Binder death, local close,
  service destruction and runtime-generation retirement all wake a Plugin
  thread waiting for an input reply. No wait may keep an old generation alive.
- At most one result, failure, or cancellation callback is accepted as terminal.
  A local close or Binder death may end ownership without a terminal callback.
- No callback is accepted after terminal or close.
- A provider may advertise cooperative-then-restart cancellation or
  process-restart-only cancellation. If Python or a native extension does not
  cooperate, the plugin retires and rebuilds its runtime process; CPython is
  not treated as a security sandbox.

Credits may be granted before or after `start`. A provider must not emit output
without credit. Terminal callbacks are never credit-gated.

Output payloads are opaque bytes. They are not required to be valid UTF-8, and
a multibyte character may span chunks. Text decoding, replacement policy, and
console rendering are host presentation concerns and cannot change sequence,
credit, byte, or chunk accounting.

Cancellation and callbacks travel in opposite one-way Binder directions, so
they have no global ordering. A cancel request stops a not-yet-dispatched
start and prevents new credit grants, but a previously dispatched `onStarted`,
credited output, result, or failure may already be in flight and may win the
single terminal race. The host must never report both that terminal and a later
cancellation.

The requested timeout starts at the provider's first accepted `start()` using
a monotonic elapsed-time clock. It covers input snapshot read/hash/EOF checks,
interactive reply waits,
runtime startup, execution, output draining, cooperative cancellation grace,
and hard process retirement. The host also keeps a conservative deadline from
its dispatch call. `openSession` has a separate short RPC/admission deadline and
must never perform potentially blocking payload reads.

## Hard Host Ceilings

| Resource | V1 ceiling |
| --- | ---: |
| Metadata document | 128 KiB |
| Source | 16 MiB |
| Workspace ZIP | 128 MiB compressed |
| Workspace entries | 20,000 |
| Workspace expanded bytes | 256 MiB |
| stdin snapshot | 16 MiB |
| Host capability snapshot | 64 KiB |
| One interactive prompt | 16 KiB UTF-8 |
| One interactive reply | 64 KiB UTF-8 |
| Interactive prompts per execution | 1,024 |
| One interactive reply wait | 30 minutes |
| Structured JSON result | 64 KiB UTF-8 |
| Output artifacts | 16 |
| One output artifact path | 1024 UTF-8 bytes |
| One output artifact | 4 MiB |
| Aggregate output artifacts | 8 MiB |
| stdout + stderr | 32 MiB |
| One output chunk | 48 KiB |
| Output chunks per execution | 65,536 |
| Outstanding output credits | 8 |
| Timeout | 24 hours |
| Concurrent sessions | 1 |
| Arguments | 128 / 4 KiB each / 32 KiB aggregate |
| Entry point | 4 KiB UTF-8 |
| Provider ID / version text | 128 bytes each |
| Advertised ABIs | 8 |
| Public error / exception text | 16 KiB each |
| Traceback frames | 256 |
| Traceback field text | 4 KiB each |

Provider-advertised and request-specific limits may only narrow these values.
Workspace, stdin, host-capability-snapshot, interactive-input, structured-result
and output-artifact limits must be zero when the corresponding capability is
disabled. The authoritative complete set remains `PythonRuntimeContract`; this
table highlights its transport and diagnostic resource ceilings.

## R0 Scope And Non-Goals

R0 froze and tested the 1.0 build-time protocol. It covers CPython identity,
finite source/PFD metadata, optional finite workspace/stdin transports,
stdout/stderr, structured tracebacks, exit code, JVM state-machine
representations of timeout/cancellation, and compatibility rules. R5-P1
extends the same build-time contract with the optional 1.1 host-capability
snapshot transport. U1-R2 extends it at 1.2 with explicit, capability-gated
file/module entry negotiation and at 1.3 with foreground-only typed built-in
input prompts, one-shot replies and required-reader compatibility behavior. At
1.4 it adds request-scoped strict JSON results and optional output artifacts
bounded by count, normalized path, per-file/aggregate size, exact PFD reference,
and SHA-256. It never infers a result from stdout.
None of this static/API evidence is Android Binder or device evidence.

R0 does not provide a runtime APK, bind a service, route `.py`, register a
script engine, change packaged APKs, or expose an AutoJs6 capability. It cannot
be cited as Android Binder, CPython, ABI, 16 KiB page-size, native-wheel,
performance, real timeout enforcement, cancellation effectiveness, or device
evidence.

V1 does not authorize general or background UI. Protocol 1.3 authorizes only
the bounded foreground prompt/reply controller carried by an opaque
user-gesture execution grant. V1 does not authorize online `pip install`, runtime-downloaded code,
arbitrary Java/Android interoperability, raw `Context`/`ScriptRuntime`/View
objects, debugger ports, shell, accessibility, unrestricted filesystem access,
or automatic fallback to Rhino.
