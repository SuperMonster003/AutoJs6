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
| Info service | the plugin's existing `org.autojs.plugin.INFO` service; `getInfo()` of the provider returns the same `PluginInfo` |
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

Every provider method answers quickly on its Binder thread. `invoke` only checks the request and
answers `PluginToolAcceptKeys` at once; the work runs on the plugin's own threads and ends with
exactly one `onResult` or `onError`. Providers never throw for invalid input; they answer an error
code.

## Catalog

Request (`PluginToolCatalogRequestKeys`): `contractVersion` (Int), `hostVersionCode` (Long).

Answer (`PluginToolCatalogKeys`): `contractVersion` (Int) and `catalogJson` (String), or
`errorCode` / `errorMessage`. The host caches the catalog per (package, versionCode) and asks again
when the package changes, so listing tools does not wake the plugin; a provider may adapt the catalog
to the device (for example list a tool only once its model is present) and should then stay
consistent until the next package change.

The document (`PluginToolCatalogJson`):

```json
{
  "shortName": "opencc",
  "tools": [
    {
      "name": "convert",
      "title": "Convert Chinese text",
      "description": "Converts Chinese text between Simplified, Traditional and regional variants with an OpenCC configuration.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "text": {"type": "string", "maxLength": 65536},
          "config": {"type": "string", "enum": ["s2t", "t2s", "s2tw", "tw2s"], "default": "s2t"}
        },
        "required": ["text"],
        "additionalProperties": false
      },
      "outputSchema": {"type": "object", "properties": {"text": {"type": "string"}}, "required": ["text"], "additionalProperties": false},
      "risk": "readonly",
      "timeoutMs": 30000,
      "maxConcurrency": 2
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
| `outputs` | optional binary outputs `{name, mimeType, description?}`, at most 8 |

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

### Images and files

A top-level string property may carry `"x-autojs6-type": "image"` or `"file"` (only at the top level,
at most 8 per tool). The consumer passes:

- image: `"screen"` (the host captures the screen as for the MCP screen tools; such calls count
  against the shared screenshot limit, MCP roadmap D39) or a path;
- file: a path.

The host checks the path scope (the working directory; shared storage read-only where the consumer
allows it, never `Android/data` or `Android/obb`), opens the file or captures the screen, and passes
a readable descriptor instead of the string. The provider gets the other arguments in
`argumentsJson` and one entry per descriptor in `inputsJson`.

## Invocation

Request (`PluginToolInvokeKeys`):

| Key | Content |
| --- | --- |
| `contractVersion` | Int |
| `invocationId` | String, unique per call |
| `tool` | the tool name without the short name |
| `argumentsJson` | JSON object of the validated arguments, image and file arguments removed; at most 64 KiB |
| `inputsJson` | JSON array, entry i describes `inputs[i]`: `{name, source, mimeType?, displayName?, byteCount?, width?, height?}`, `source` is `screen` or `file` |
| `outputsJson` | JSON array of the declared output names the caller wants written |
| `timeoutMs` | Long, the tool's `timeoutMs` capped by the consumer's limit |
| `consumer` | `mcp`, `agent` or `script`, informational |

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
| one descriptor (input or output) | 8 MiB |
| image or file parameters / outputs per tool | 8 / 8 |
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

## Status

| Part | State |
| --- | --- |
| Contract module, frozen literals and transactions | done (P12 host item 1) |
| Discovery and catalog cache, short name checks | done (P12 host item 2: `PluginToolRegistry`, `PluginToolCatalogParser`, `PluginToolShortNames`; fake provider `test-apps/plugin-tool-provider-conformance`) |
| Schema validation | done (P12 host item 3: `PluginToolSchema` checks schemas when catalogs load; argument validation and defaults are ready for the bridge) |
| Bridge module `plugin_tools`, grants and tokens | planned (P12 host item 4) |
| Script API `plugins.tools` | planned (P12 host item 5) |
| Pilots | OpenCC (part of the 6.8.0 release gate), YOLO, Image Quantization, Stack Archive |
