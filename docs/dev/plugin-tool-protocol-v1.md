# Plugin tool protocol v1

## Purpose

A plugin that implements this contract describes its own tools (name, description, parameter
schema, risk), and AutoJs6 lists them and forwards every call. MCP Server, the 3-Stove Agent and
scripts reach the tools through the host, so a new tool only needs a new plugin release, not a new
host bridge module. The design is fixed by MCP Server roadmap P12, decisions D29 - D37 and D41 / D43:

- the host offers the tools through one bridge module (`plugin_tools`: `list`, `invoke`, `cancel`)
  and a script API (`plugins.tools`); consumers never bind a provider themselves (D29);
- the contract is its own module with its own version, a provider joins with one extra service, the
  catalog comes back over Binder and the host caches it (D30);
- parameter schemas use a subset of JSON Schema that the host validates, with two special types for
  images and files (D31); images, files and binary outputs travel through descriptors the host opens
  and writes, so providers need no storage permission (D32);
- tools are named `<shortName>_<name>`; a shared family is reserved for later (D33);
- the host enforces trust levels and risk switches (D34).

The contract lives in the host module `plugin-api/plugin-tool-api` (package
`org.autojs.plugin.tool.api`). `PluginToolContractTest` freezes its literals and naming rules,
`PluginToolAidlOrderTest` its transactions and oneway flags. Until the four pilots (OpenCC, YOLO,
Image Quantization, Stack Archive) are stable the contract may still change; after AutoJs6 6.8.0 is
released it only grows by appended keys, values and transactions (D43).

## Discovery

| Item | Value |
| --- | --- |
| Provider service | action `org.autojs.plugin.TOOL_PROVIDER` (`PluginToolContract.SERVICE_ACTION`), no category, exported and protected by `org.autojs.permission.PLUGIN` |
| Info service | optional (MCP roadmap D60): a package that declares no other plugin service is listed, enabled and authorized in the plugin center through its provider (`getInfo()` of the provider, the string resource `plugin_description`), so a third-party plugin needs nothing but the provider service. A package with an `org.autojs.plugin.INFO` service or a family service keeps that endpoint in the plugin center, and `getInfo()` of its provider returns the same `PluginInfo` |
| Capability | `PluginInfo.capabilities[pluginToolContractVersion]` (Int, `PluginToolCapabilityKeys.CONTRACT_VERSION`), the highest version the provider speaks |
| Wake | the plugin's `WakeActivity`, as for every plugin (the host wakes a stopped package before binding) |

The host only lists providers whose package is enabled and authorized in the plugin center, whose
`requiresHostVersion` the host meets and whose `pluginToolContractVersion` it supports; it records
the trust level (OFFICIAL, TRUSTED, USER_GRANTED) of each. A plugin keeps its existing family
services; the provider service is an addition. One provider per package: when a package exports
several provider services, the host uses the first by class name.

`PluginToolRegistry` (host) keeps the providers and their catalogs. Before each use it takes a
PackageManager-only fingerprint of the provider services (package, versionCode, update time, plugin
center enable and authorization state) and rebuilds its snapshot only when the fingerprint changed,
so installing, updating, removing, enabling or authorizing a provider shows up on the next call while
an unchanged set binds nothing. A provider is reported as `AVAILABLE`, `DISABLED`, `UNAUTHORIZED`,
`INCOMPATIBLE`, `FAILED` or `REFUSED` with a detail.

## Binder interface

`IPluginToolProvider` (synchronous; transaction codes follow the declaration order):

| # | Method | Use |
| --- | --- | --- |
| 1 | `PluginInfo getInfo()` | identity and capabilities |
| 2 | `Bundle getCatalog(in Bundle request)` | the catalog, see below |
| 3 | `Bundle invoke(in Bundle request, in ParcelFileDescriptor[] inputs, IPluginToolCallback callback)` | accepts or refuses one call |
| 4 | `oneway void cancel(in Bundle request)` | asks a running call to stop |

`IPluginToolCallback` (oneway, implemented by the host):

| # | Method | Use |
| --- | --- | --- |
| 1 | `onProgress(in Bundle update)` | reserved; the host bridge of version 1 drops it |
| 2 | `onResult(in Bundle result, in ParcelFileDescriptor[] outputs)` | the result and the binary outputs |
| 3 | `onError(in Bundle error)` | the failure, including `CANCELLED` |

`IPluginToolOutputTree` (synchronous, implemented by the host for each directory output, see below):

| # | Method | Use |
| --- | --- | --- |
| 1 | `Bundle createDirectory(String relativePath)` | creates a directory and its parents |
| 2 | `ParcelFileDescriptor openFile(String relativePath, long expectedBytes, out Bundle answer)` | a writable descriptor of a new file, or null with the reason in `answer` |

Every provider method answers quickly on its Binder thread. `invoke` only checks the request and
answers `PluginToolAcceptKeys` at once; the work runs on the plugin's own threads and ends with
exactly one `onResult` or `onError`. Providers never throw for invalid input; they answer an error
code.

### Libraries and the provider base class

Plugins outside the host repository depend on `io.github.supermonster003:autojs6-plugin-tool-api`, which brings
`io.github.supermonster003:autojs6-common-plugin-api` (MCP roadmap D58, [plugin-api-maven-central.md](plugin-api-maven-central.md)).
Both are built for Java 17 bytecode and the Kotlin 2.2 language and standard library, need `minSdk` 24 and
`compileSdk` 36, and keep their classes through consumer R8 rules.

`PluginToolProviderService` (MCP roadmap D59) is an optional base class that answers the Binder interface above: a
subclass implements `createPluginInfo()`, `createCatalogJson()` and `onCall(PluginToolCall)`. The base class

- adds `pluginToolContractVersion` to the capabilities, checks the contract version of every request, refuses tools
  missing from the catalog with `UNKNOWN_TOOL` and duplicate invocation ids, and answers catalog errors thrown as
  `PluginToolException`;
- runs accepted calls on a pool of `workerThreads` daemon threads (default 4), forwards `cancel` as
  `PluginToolCall.isCancelled`, `onCancel` listeners and, unless `interruptOnCancel` is false, a thread interrupt;
- hands out the input descriptors by parameter name (`PluginToolInput`: `openStream`, `readBytes` with a limit) and the
  directory outputs (`PluginToolDirectoryOutput`: `createDirectory`, `openFile` returning null for `EXISTS`, `existing`);
- reports `PluginToolResult` (JSON, text, file outputs from a descriptor, a file or bytes; outputs the caller does not
  want are dropped) after checking `MAX_RESULT_BYTES` and `MAX_DESCRIPTOR_BYTES`, or the failure: the code of a
  `PluginToolException`, `INVALID_ARGUMENTS` for an `IllegalArgumentException`, `FAILED` otherwise and `CANCELLED`
  for anything after a cancel;
- closes every input descriptor, directory output stream and output descriptor before the single report.

The pilots implement the interface directly and do not need to migrate. The third-party sample repository
`AutoJs6-Third-Party-Tool-Sample` (D57, D61 - D64) shows the base class in Kotlin and in Java.

## Catalog

Request (`PluginToolCatalogRequestKeys`): `contractVersion` (Int), `hostVersionCode` (Long).

Answer (`PluginToolCatalogKeys`): `contractVersion` (Int) and `catalogJson` (String), or
`errorCode` / `errorMessage`. The host caches the catalog per (package, versionCode) and asks again
when the package changes, so listing tools does not wake the plugin; a provider may adapt the catalog
to the device (for example list a tool only once its model is present) and should then stay
consistent until the next package change.

The document (`PluginToolCatalogJson`), shown with the catalog of 3-Hanzi CC 2.1.0 (descriptions shortened, `enum`
reduced from its 32 names):

```json
{
  "shortName": "opencc",
  "tools": [
    {
      "name": "convert",
      "title": "Convert Chinese text (OpenCC)",
      "description": "Converts Chinese text between Simplified, Traditional, Hong Kong, Taiwan and Japanese Shinjitai character forms with OpenCC, offline on the phone. ...",
      "inputSchema": {
        "type": "object",
        "properties": {
          "text": {"type": "string", "description": "The text to convert, at most 16384 characters.", "maxLength": 16384},
          "config": {"type": "string", "description": "The conversion, named <from>2<to> ...", "enum": ["hk2s", "s2t", "s2twp", "t2s", "s2twi", "s2jp"], "default": "s2t"}
        },
        "required": ["text"],
        "additionalProperties": false
      },
      "outputSchema": {
        "type": "object",
        "properties": {"text": {"type": "string"}, "config": {"type": "string"}},
        "required": ["text", "config"],
        "additionalProperties": false
      },
      "risk": "readonly",
      "timeoutMs": 10000,
      "maxConcurrency": 4
    }
  ]
}
```

| Field | Rule |
| --- | --- |
| `shortName` | `^[a-z][a-z0-9]{1,15}$`, unique among providers, not in `PluginToolNames.RESERVED_SHORT_NAMES` (MCP tool groups, the P11 plugin families, host names) |
| `sharedFamily` | optional, reserved: a family short name on the host's allow list that several OFFICIAL plugins may share (D33); version 1 hosts only validate it and do not select providers |
| `tools` | 1 to 64 entries |
| `name` | `^[a-z][a-z0-9]*(_[a-z0-9]+)*$`, unique; `<shortName>_<name>` at most 50 characters so that `mcp__autojs6__` plus the name fits the 64 characters MCP clients allow |
| `title` | at most 128 characters |
| `description` | English, at most 4096 characters; it reaches model context as written |
| `inputSchema` | a closed object schema within the subset below |
| `outputSchema` | optional, same subset; when present the result is an object that matches it |
| `risk` | `readonly`, `write`, `destructive` or `network` |
| `timeoutMs` | optional, default 30000, at most 300000 |
| `maxConcurrency` | optional, default 1, at most 4 |
| `outputs` | optional outputs `{name, kind?, mimeType, description?}`, at most 8; `kind` is `file` (default, needs `mimeType`) or `directory` (MCP roadmap D53, no `mimeType`) |

The whole document is at most 256 KiB of UTF-8. A document that is not a JSON object, breaks a limit,
uses an invalid or reserved short name, declares a shared family it may not use, or repeats a tool
name is refused as a whole; a tool with an invalid name, field or schema is dropped alone, and a
document left without a usable tool is refused. Fields this version does not know are ignored, so
providers may append them; JSON null counts as absent for optional fields. The host reports the
reasons as the provider's detail and diagnostics.

Short names are unique across providers. When several providers claim one, the claim of the highest
trust level keeps it and the others are refused; claims that tie at the highest level are all
refused, so a less trusted plugin can never take over the tool names of a more trusted one. A shared
family is accepted only on the host's allow list (version 1: `ocr`) and only from OFFICIAL providers;
version 1 hosts keep listing such tools under the provider's short name until provider selection
exists (MCP roadmap D44).

### Schema subset

Every schema node has a `type` of `object`, `string`, `integer`, `number`, `boolean` or `array`, or
is a `oneOf` of such nodes. Allowed keywords:

| Node | Keywords |
| --- | --- |
| any | `type`, `title`, `description`, `default`, `enum` (scalars only) |
| object | `properties`, `required`, `additionalProperties: false` (mandatory: every object is closed) |
| string | `minLength`, `maxLength` |
| integer, number | `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum` |
| array | `items` (one schema: a scalar or an object), `minItems`, `maxItems` |
| choice | `oneOf` (2 or more nodes) |

Unknown keywords make the tool invalid, so that a host never silently ignores a constraint the author
relied on. Objects and arrays nest at most 8 levels, an object has at most 64 properties, property
names have 1 to 64 characters, `enum` holds distinct values that match the node, and a `default`
must itself match the node. The host validates the schema when it loads the catalog and the
arguments on every call, before the provider sees them: string lengths count Unicode code points, an
`integer` has no fraction, a `oneOf` value must match exactly one branch, unknown properties are
refused, and JSON null counts as an absent property. Absent properties that declare a `default` get
it before the call.

The host implements the subset itself (`PluginToolSchema`, JVM-tested) rather than through a general
JSON Schema library: the libraries bring Jackson or a second org.json and need R8 rules for reflection,
and the subset's own rules (closed objects, top-level-only special types, unknown keywords) would still
need code around them.

### Images, files and directories

A top-level string property may carry `"x-autojs6-type": "image"`, `"file"` or `"directory"` (only at
the top level, at most 8 per tool). The consumer passes:

- image: `"screen"` (the host captures the screen as for the MCP screen tools; such calls count
  against the shared screenshot limit, MCP roadmap D39) or a path;
- file: a path;
- directory: a path (MCP roadmap D49). Beside the type, `"x-autojs6-files"` may list the plain file names
  the tool needs (1 - 16 names, no directories); the host refuses a directory that lacks one of them and
  opens only those. Without the keyword the host opens every regular file directly inside the directory
  (at most 16, in name order, no subdirectories).

The host checks the path scope (the working directory; shared storage read-only where the consumer
allows it, never `Android/data` or `Android/obb`), opens the files or captures the screen, and passes
readable descriptors instead of the string. The provider gets the other arguments in `argumentsJson`
and one entry per descriptor in `inputsJson` (`name` is the parameter, `source` is `screen`, `file` or
`directory`, `displayName` is the file name, `byteCount` its size, `width` / `height` for images).
Files of file and directory parameters are opened read-only without a copy and may be large (up to
512 MiB each); images keep the 8 MiB descriptor ceiling. A model with several files, such as an NCNN
model, is one directory parameter:

```json
"model": {"type": "string", "x-autojs6-type": "directory", "x-autojs6-files": ["model.json", "model.ncnn.param", "model.ncnn.bin"]}
```

## Invocation

Request (`PluginToolInvokeKeys`):

| Key | Content |
| --- | --- |
| `contractVersion` | Int |
| `invocationId` | String, unique per call |
| `tool` | the tool name without the short name |
| `argumentsJson` | JSON object of the validated arguments, image and file arguments removed; at most 64 KiB |
| `inputsJson` | JSON array, entry i describes `inputs[i]`: `{name, source, mimeType?, displayName?, byteCount?, width?, height?}`, `source` is `screen`, `file` or `directory` |
| `outputsJson` | JSON array of the declared output names the caller wants written |
| `timeoutMs` | Long, the tool's `timeoutMs` capped by the consumer's limit |
| `consumer` | `mcp`, `agent` or `script`, informational |
| `outputTrees` | Bundle, only when the caller wants directory outputs: output name to the IBinder of an `IPluginToolOutputTree` |

The provider owns and closes the input descriptors. Immediate answer (`PluginToolAcceptKeys`):
`accepted` (Boolean), or `errorCode` / `errorMessage` (for example `UNKNOWN_TOOL`, `BUSY`,
`UNSUPPORTED_CONTRACT_VERSION`).

Result (`onResult`, `PluginToolResultKeys`): `invocationId`, `resultJson` (any JSON, an object
matching `outputSchema` when declared), optional `text` (a short plain-text summary consumers may
show), and `outputsJson`, a JSON array whose entry i describes `outputs[i]` as `{name, mimeType}`.
`resultJson` and `text` together stay within 512 KiB. Each output descriptor is readable; the host
reads at most 8 MiB from it, closes it, and writes the bytes atomically to the working-directory path
the caller gave for that output (D32). A provider may return a pipe or a file in its own cache.

Failure (`onError`, `PluginToolErrorKeys`): `invocationId`, `code`, `message` (English, at most 4096
characters).

### Directory outputs

A tool that produces a tree of files, such as extracting an archive, declares an output with
`"kind": "directory"` (MCP roadmap D53). For every directory output the caller wants, the request
carries an `IPluginToolOutputTree` in `outputTrees`, and the provider writes through it while the call
runs:

- Paths are relative, with `/` between segments; empty, `.` and `..` segments, a leading `/`, backslashes
  and control characters are refused (`INVALID_PATH`), as are paths above 4096 UTF-8 bytes or segments
  above 255 (`PluginToolOutputTreePaths` checks the same rule on the plugin side).
- `createDirectory` answers `ok`, or `errorCode` / `errorMessage`. `openFile` returns a writable
  descriptor of a new, empty file, or null with the reason in `answer`. The provider writes and closes
  every descriptor before it reports the result; a tree holds at most 100000 files (`LIMIT_EXCEEDED`).
- The destination directory may already exist (D56). A file that exists there answers `EXISTS` unless the
  caller passed `overwrite: true`; the provider may skip it, report it in its own result and go on.
- The host writes into a hidden staging directory next to the destination. When the call reports
  `onResult`, it moves the files into the destination (a file that appeared meanwhile, or a path that
  would leave the destination through a link, is reported as a conflict) and deletes the staging
  directory; when the call fails, times out, is cancelled or the provider dies, it deletes the staging
  directory and nothing reaches the destination. After the call ended both methods answer `CLOSED`.

Hosts before directory outputs never send `outputTrees`; a provider that finds no tree for a wanted
directory output may still answer, for example with a listing of what it would have written.

Time limit and cancel: when `timeoutMs` passes without a callback, or the consumer cancels, the host
calls `cancel({invocationId, reason})` with reason `timeout`, `caller` or `host`, answers the consumer
itself and ignores later callbacks. A provider stops as soon as it can and reports `CANCELLED`. When
the provider process dies, every running call of it fails on the host side.

## Error codes

| Code | Meaning |
| --- | --- |
| `UNSUPPORTED_CONTRACT_VERSION` | the request uses a version the provider does not speak |
| `UNKNOWN_TOOL` | the tool is not (or no longer) in the catalog |
| `INVALID_ARGUMENTS` | valid for the schema, not for the tool (for example an unknown model name) |
| `NOT_READY` | the tool cannot run yet, such as while a model is missing; the message says what is needed |
| `BUSY` | `maxConcurrency` reached; retry later |
| `LIMIT_EXCEEDED` | an input or the result exceeds a limit |
| `INPUT_UNREADABLE` | an input descriptor could not be read or decoded |
| `CANCELLED` | stopped after `cancel` |
| `TIMEOUT` | the provider gave up after its own time limit |
| `FAILED` | any other failure |

## Limits

| Limit | Value |
| --- | --- |
| catalog document | 256 KiB UTF-8 |
| tools per provider | 64 |
| short name / full name | 2 - 16 / at most 50 characters |
| title / description | 128 / 4096 characters |
| schema depth / properties per object | 8 / 64 |
| arguments | 64 KiB UTF-8 |
| result (`resultJson` + `text`) | 512 KiB UTF-8 |
| one image input or output descriptor | 8 MiB |
| one file of a file or directory parameter | 512 MiB (opened read-only, no copy) |
| image, file or directory parameters / outputs per tool | 8 / 8 |
| files per directory output / bytes of one relative path / of one segment | 100000 / 4096 / 255 |
| files per directory parameter / input descriptors per call | 16 / 32 |
| time limit | default 30 s, at most 300 s |
| concurrency per tool | default 1, at most 4 |

## Trust and switches

The host enforces the trust level (D34): tools of OFFICIAL and TRUSTED providers are available to
model-facing consumers (MCP, Agent) without further steps; tools of USER_GRANTED providers stay off
until the user turns each one on in the consumer's settings, because their descriptions reach model
context as the author wrote them. Consumers can only narrow what the host allows. Each provider forms
one switchable group; `readonly` tools follow the group switch, `write`, `destructive` and `network`
tools sit behind a second switch of the group that is off by default, and consumers map the risk to
MCP annotations (`readOnlyHint`, `destructiveHint`, `openWorldHint`). There is no confirmation on the
phone per call. Scripts written by the user may call every tool of an authorized provider.

## Bridge module `plugin_tools`

Consumers reach the tools through the host's Node bridge module `plugin_tools` (`NodeBridgePluginToolsBridge`), never
through the provider directly (MCP roadmap D29). Every method needs the token `plugin_tools`.

| Method | Arguments | Result (`schema`) |
| --- | --- | --- |
| `list()` | none | `autojs6-bridge-plugin-tools-list-v1`: `tools[]` with `name` (`<shortName>_<name>`), `tool`, `shortName`, `provider`, `trust`, `title`, `description`, `inputSchema`, `outputSchema`, `risk`, `timeoutMs`, `maxConcurrency`, `outputs`, `imageParameters`, `fileParameters`, `directoryParameters`; `providers[]` with `packageName`, `versionCode`, `trust`, `status`, `shortName`, `tools`, `diagnostics`, `detail` and `toolsHidden` |
| `invoke(name, arguments, options)` | `options`: `invocationId` (`[A-Za-z0-9._:-]{1,128}`, generated when absent), `timeoutMs` (only shortens the tool's), `outputs` (output name to working-directory path), `overwrite` (default false) | `autojs6-bridge-plugin-tools-invoke-v1`: `tool`, `provider`, `invocationId`, `result`, `text`, `outputs[]` (`name`, `path`, `bytes`, `mimeType`; a directory output adds `kind: "directory"`, `files` and, when some were left alone, `conflicts` (at most 20 paths) and `conflictCount`), `missingOutputs`, `durationMs` |
| `cancel(invocationId)` or `cancel({invocationId})` | | `autojs6-bridge-plugin-tools-cancel-v1`: `invocationId`, `cancelled` (false when the call already ended or never ran) |

Rules the bridge enforces before the provider sees a call:

- Tools of USER_GRANTED providers are listed and run only for requests that declare `plugin_tools.user_granted`; that
  token is never implied by `plugin_tools`. MCP Server declares it only for the USER_GRANTED tools the user turned on.
  Without it, `list` still names the provider with `toolsHidden: true`.
- The arguments must match the tool's schema (problems are reported with their JSON path); absent defaults are filled
  in. Image arguments of `"screen"` need `screen_capture` and are captured like `accessibility.captureScreenImage`
  (PNG); paths need `files` and resolve through the session's path policy (model-facing sessions: the working
  directory, and shared storage with `files.shared_read`; script sessions: the working directory only). Outputs need
  `files.write`, must be declared by the tool and resolve inside the working directory; an existing file is replaced
  only with `overwrite: true`.
- The time limit is the tool's `timeoutMs`, shortened by `options.timeoutMs` and by the bridge deadline (the request
  timeout minus 750 ms, so the bridge answers before the broker and can still tell the provider). On expiry the
  bridge answers `timeout` and calls `cancel` with reason `timeout`; `plugin_tools.cancel` answers the running call and
  calls `cancel` with reason `caller`; closing the session cancels with reason `host`. A provider process that dies
  during a call fails it at once. The call keeps a dedicated binding to the provider until it ends.
- A result larger than 512 KiB, a result that is not JSON or does not match the declared output schema, and an output
  above 8 MiB fail the call (`resource-limit` / `provider-failed`). Outputs are copied into a sibling temporary file
  and renamed over the target. A directory output may name an existing directory (not a file); its files are staged
  and moved in as described under "Directory outputs".

Provider error codes map onto bridge categories: `INVALID_ARGUMENTS`, `UNKNOWN_TOOL` and `INPUT_UNREADABLE` to
`invalid-request`, `BUSY` to `rate-limited`, `LIMIT_EXCEEDED` to `resource-limit`, `TIMEOUT` to `timeout`, `NOT_READY`
to `unavailable`, everything else to `provider-failed`; the message keeps the code and the provider's text.

The MCP Server default grant includes the three methods and both tokens; the 3-Stove Agent grant does not include
them until its roadmap decides. `plugin_tools.cancel` is an ordinary bridge call: while a session's concurrency limit
is used up by running calls, a cancel waits for a slot like any other request.

## Script API `plugins.tools`

Rhino scripts use the same bridge implementation in process through `plugins.tools` (`PluginsTools`, mounted with
the `plugins` module); there is no broker and no grant in between (MCP roadmap D35).

| Method | Result |
| --- | --- |
| `list()` | the `tools` array of `plugin_tools.list` |
| `providers()` | the `providers` array of `plugin_tools.list`, to find out why a tool is missing |
| `call(name, args?, options?)` | the result of `plugin_tools.invoke`; blocks the script thread, and stopping the script cancels the call (reason `host`) |
| `callAsync(name, args?, options?)` | a Promise of the same, settled on the script thread; it rejects with an `Error` that carries the reason |

- Scripts are the user's own code (D34): their requests carry every token of the module (`plugin_tools`,
  `plugin_tools.user_granted`, `screen_capture`, `files`, `files.write`), so the tools of every enabled and authorized
  provider, USER_GRANTED included, are available. The consumer is `script`.
- Paths of image and file arguments and of outputs resolve like `files.path` (relative paths against the script's
  working directory) and are not confined further; the contract limits still apply.
- Image arguments also accept an `ImageWrapper`: the host writes a PNG copy under the app cache directory
  (`plugin-tool-script-images`) and deletes it when the call ends. `"screen"` takes the accessibility screenshot on
  Android 11+ while the service runs, otherwise `images.captureScreen()`, which needs `images.requestScreenCapture()`
  first; without either the call fails and says so.
- `options` are those of `plugin_tools.invoke` without `invocationId`. The request timeout is set above the longest
  tool limit, so the tool's own limit (or a shorter `options.timeoutMs`) decides.

## Status

| Part | State |
| --- | --- |
| Contract module, frozen literals and transactions | done (P12 host item 1) |
| Discovery and catalog cache, short name checks | done (P12 host item 2: `PluginToolRegistry`, `PluginToolCatalogParser`, `PluginToolShortNames`; fake provider `test-apps/plugin-tool-provider-conformance`) |
| Schema validation | done (P12 host item 3: `PluginToolSchema` checks schemas when catalogs load; argument validation and defaults are ready for the bridge) |
| Bridge module `plugin_tools`, grants and tokens | done (P12 host item 4: `NodeBridgePluginToolsBridge`, `PluginToolInvocationPlanner`, MCP default grant) |
| Script API `plugins.tools` | done (P12 host item 5: `PluginsTools`; documentation, declarations 4.41.0 and Ace Editor completion synchronized) |
| Host changelog and protocol documents | done (P12 host item 6: 6.8.0 changelog, `host-capability-contract-v1.md`) |
| Pilot OpenCC (part of the 6.8.0 release gate) | done: 3-Hanzi CC 2.1.0 declares `opencc_convert` (`config` takes the 32 names of the `opencc` module, `text` up to 16384 characters); `OpenccPluginToolRoundTripTest` runs it through the script API and the bridge |
| Pilots YOLO, Image Quantization | done: `yolo_detect` (directory parameters, D49) and `imagequant_quantize` (binary output in the plugin's own process, D51 / D52) |
| Directory outputs | done (D53 / D56: `IPluginToolOutputTree`, staging and commit in `PluginToolOutputTree`, fake tool `emit_tree`) |
| Pilot Stack Archive | done: 3-Stack Archive 3.1.0 declares `archive_list`, `archive_read` and `archive_extract` (directory output, existing files kept, D54 - D56); `ArchivePluginToolRoundTripTest` runs them through the script API and the bridge |
| Provider base class | done (D59: `PluginToolProviderService` and its helpers, `@JvmStatic` contract helpers; in-process androidTests of the module, a Java subclass included) |
| Plugin center lists provider-only plugins | done (D60: `InstalledPluginRepository` queries `TOOL_PROVIDER` for packages without another plugin service) |
| Maven Central publishing | configuration done and verified with the local staging repository (D58); the release needs the maintainer's account and key |
| Third-party sample | `AutoJs6-Third-Party-Tool-Sample` 1.0.0: `ktsample` / `javasample` with `hash`, `thumbnail` and `split`, own signing key (USER_GRANTED), APKs in Auto.js-SDK-Plugins-Garage (D57, D61 - D64) |
