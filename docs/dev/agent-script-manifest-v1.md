# Agent script manifest V1

Status: **versioned V1**. This is the host registration format for scripts that
explicitly opt in to 3-Stove Agent. The bounded catalog, parameter validation/defaults,
missing-value questions, confirmation, invocation and structured result channel
are implemented. Public `ai.agent.result` and `ai.agent.context` are available
from host build 5287; task creation/control requires build 5293.

The current plugin requires AutoJs6 6.8.0 / build 5289 for its inspected screen
actions; the base V1 contract retains its historical 5286 floor. Use build 5293+
for the complete task API. Registration is consumed through the attached, granted
host capability broker, not a general plugin filesystem API. Connection and
management are described in [3-Stove Agent protocol V2](three-stove-agent-protocol-v2.md).
Versioned protocol status does not declare that plugin release gates have passed.

## Project registration

Put an `agent` object in `project.json`:

```json
{
  "name": "Meituan Coffee",
  "main": "main.js",
  "agent": {
    "id": "meituan-coffee",
    "description": "在美团外卖为用户下单指定门店的咖啡并送到指定地址",
    "parameters": {
      "type": "object",
      "properties": {
        "product": { "type": "string", "description": "商品名称" },
        "size": {
          "type": "string",
          "enum": ["中杯", "大杯", "超大杯"],
          "default": "大杯"
        },
        "temperature": { "type": "string", "enum": ["热", "冰"] },
        "address": { "type": "string", "description": "配送地址" },
        "maxPrice": { "type": "number", "minimum": 0 }
      },
      "required": ["product", "address"]
    },
    "result": {
      "type": "object",
      "properties": {
        "orderStatus": { "enum": ["cart", "pending_payment", "submitted", "paid"] },
        "total": { "type": "number" }
      }
    },
    "risk": "sensitive",
    "confirm": "before-run",
    "timeoutMs": 300000,
    "examples": ["帮我在美团点一杯星巴克拿铁送到公司前台"],
    "tags": ["外卖", "咖啡"]
  }
}
```

The registration fields are:

| Field | Rule |
| --- | --- |
| `id` | Optional explicit lowercase kebab-case identifier. The default normalizes the directory name; a name with no ASCII letters/digits uses a stable `script-` hash suffix. |
| `description` | Required nonblank string. |
| `parameters` | Optional object schema using the subset below; defaults to an empty closed object. |
| `result` | Optional JSON object describing the result. It is descriptive metadata, not a validator for the result-reporting API. |
| `risk` | `readonly`, `normal` (default), or `sensitive`. |
| `confirm` | `never` (default) or `before-run`; sensitive scripts are normalized to `before-run`. |
| `timeoutMs` | Integer from 1 through 300,000; default 60,000. |
| `examples`, `tags` | Optional arrays of nonblank strings. |

Unknown registration fields and duplicate JSON keys are rejected. The catalog
requires strict JSON even though other ProjectConfig consumers support permissive
editing formats. `ProjectConfig.agent` preserves this data through the existing
Gson/FuzzyDeserializer path; catalog and execution admission separately validate
it with `AgentManifestParser`.

The default entry is `main.js`. Existing ProjectConfig main-name aliases are
recognized, but conflicting aliases fail closed. The resolved entry must be a
supported JavaScript/TypeScript source inside that project directory, and must
also remain inside an approved catalog root. Absolute entries, parent traversal
and symbolic links outside the project are rejected. The project directory stays
the registration's public `path`; execution also receives the resolved main file
so it can preserve the existing project launch context.

## Standalone file registration

The first leading JSDoc block must contain an `@agent` line. UTF-8 BOM,
whitespace and leading line comments are permitted; another block comment or
executable code before the JSDoc prevents registration.

```javascript
/**
 * @agent
 * @description 清理下载目录中指定天数之前的安装包
 * @param {integer} [days=30] 保留天数
 * @param {string} [dir=/sdcard/Download] 目录
 * @param {boolean} [dryRun=false] 只统计不删除
 * @result {object} { removed: integer, freedBytes: integer }
 * @risk normal
 * @confirm before-run
 * @timeout 120000
 * @example 清理一下下载目录里的旧安装包
 * @tag 清理
 */
```

Each tag occupies its own line. A parameter without brackets is required:
`@param {string} address 配送地址`. Brackets make it optional, and `=value`
provides a default. String defaults may be unquoted (as in the path above) or
JSON-quoted. Number/integer and boolean defaults must use their JSON scalar forms.
An enumeration is expressed as `@param {string=a|b|c} choice`; the same shorthand
also accepts number/integer/boolean scalar values.

Duplicate parameters, duplicate singleton tags, unknown tags, invalid defaults
and unsupported types reject the registration. `@example` and `@tag` may repeat.
Description/example/result text can continue onto subsequent untagged lines.
Other defaults match project registration. A standalone file's default ID comes
from its filename without the final extension.

Only the first 8 KiB are read. The block must end within those bytes; a truncated
registered block is invalid. A multibyte character cut off later in the script
body cannot invalidate an already complete header. No script body is returned in
the catalog. File detection uses the host's JavaScript-family extension policy,
including `.js`, `.ts` and `.tsx`; declaration-only TypeScript files are excluded.
The execution layer still applies its supported-engine checks.

## Parameter schema and argument validation

The root schema is an object with `type: "object"`, `properties`, optional
`required`, and optional `additionalProperties: false`. Omitting properties or
required means an empty set. Additional arguments are rejected even if the
`additionalProperties` keyword is omitted; the normalized schema includes
`additionalProperties: false`.

Property names use ASCII identifier characters (a letter, underscore or dollar
sign first, then letters, digits, underscores or dollar signs). Each property has
one scalar `type`: `string`, `number`, `integer` or `boolean`. Supported
constraints are:

- `enum`: a nonempty array of distinct values of the declared type.
- `default`: a value that passes the property's type, enumeration and bounds.
- `description`: a string.
- `minimum` / `maximum`: numeric bounds on number/integer properties.
- `minLength` / `maxLength`: nonnegative integer bounds on strings; lengths count
  Unicode code points.

Required names must refer to declared properties and cannot repeat. Bounds must
be ordered. Numeric values must be finite; integer values cannot have a
fractional part. Numeric enumeration equality treats `1` and `1.0` as equal.
Unsupported schema keywords, nested object/array parameters, type coercion and
null arguments are rejected. A structured value can instead be carried in a
string parameter whose description states its JSON format.

Execution validates a copied argument object, inserts defaults for missing keys,
and then checks required keys and constraints. Both the original and defaulted
serialized objects must fit within 16 KiB of UTF-8 JSON. This returns a new object
and does not mutate the caller's arguments or cached schema.

## Catalog roots, records and caching

The plugin launcher provides a script-directory settings page. Enter one absolute
path per line, at most 32 extra roots. The authenticated attachment request proposes
them to the host, which validates and saves the accepted configuration. Settings
changes cancel plugin tasks and invalidate its cache. Task `options.scriptRoots`
may select a subset; an empty array retains only the host working directory and
its `agent/` subtree. The model's `script_catalog` arguments accept only a query,
never root paths. Minimum host build 5286 supports this configuration flow.

`AgentScriptCatalog` receives the working directory, user-visible storage roots
and host-approved additional roots. Per-request `roots` can only narrow that
configured scope. Updating the configured roots validates the entire replacement
before applying it and clears the cache. Additional roots must be existing
directories under a user-visible root and cannot be the working directory or its
ancestor. Hidden directories and Android/data or Android/obb private trees are
not accepted as additional roots.

Every scanned path, manifest and execution entry is canonicalized and checked
against the approved roots. A symbolic link outside the scanned root is skipped;
an explicit request outside approved roots is denied. Canonical containment also
prevents sibling-prefix matches such as `work-other` being treated as `work`.
The implementation uses `java.io.File` path checks compatible with Android API 24.

The default scan covers working-directory nesting depths 0 through 4, plus its
`agent/` subtree through total depth 8. An additional root has depth 0 through 4.
Requesting a deeper subdirectory does not reset these depth budgets. A catalog
contains at most 500 records and 256 KiB of serialized UTF-8 JSON, including the
array brackets and separators. A smaller positive `limit` may be requested;
`query` performs a case-insensitive search over ID, description, tags and examples.
A defensive traversal budget of 20,000 visited entries also bounds discovery in
directories containing many unregistered files. Reaching a discovery limit returns
the collected prefix of results; it does not imply that every script was scanned.

Each record has `id`, canonical `path`, `kind: "project" | "file"`,
`description`, `parameters`, optional `result`, `risk`, `confirm`, `timeoutMs`,
`examples`, `tags` and `updatedAt`. Paths identify entries; duplicate explicit or
derived IDs in different directories do not authorize an ambiguous ID-only launch.

Project JSON reads are capped at 256 KiB. The bounded 1,000-entry LRU cache uses
the registration file's canonical path, last-modified time and byte length.
Changed or removed files are rechecked; returned Gson trees are copies. Project
`updatedAt` also includes the entry script's modification time. Invalid
registrations are skipped during discovery, while explicit read/execute requests
fail with a stable catalog error detail:

| Code | Meaning |
| --- | --- |
| `SCRIPT_NOT_REGISTERED` | Missing or invalid registration, unsupported entry, or invalid project main path. |
| `CAPABILITY_DENIED` | Requested path/root escapes host-approved scope. |
| `TOOL_ARGUMENTS_INVALID` | Arguments fail the registered schema or the 16 KiB bound. |
| `INVALID_REQUEST` | Invalid catalog limit or oversized query. |

Diagnostics contain neither private paths nor manifest/argument contents.

These uppercase names are catalog exception codes. Over Node Bridge they appear
as the error message/detail, while `error.code` retains the existing Node Bridge
code vocabulary. `CAPABILITY_DENIED` maps to category `capability-denied`, a
`LIMIT_EXCEEDED` execution admission error maps to `resource-limit`, and the other
catalog validation failures map to `invalid-request`. Malformed bridge options
rejected before catalog parsing also use `invalid-request`, with a generic
diagnostic rather than a catalog code.

## Execution arguments and result reporting

The validated arguments are supplied to the host execution layer so scripts can
read `engines.myEngine().execArgv`. Risk and confirmation metadata is retained
for the plugin's confirmation gate; registration alone does not grant additional
device capabilities.

P1.4 provides the host execution result slot and P3.3 connects `ai.agent.result(value)`
(up to 64 KiB UTF-8 JSON) and `ai.agent.context().parameters` in host build 5287.
`resultReported` distinguishes an explicitly reported JSON null from no report.
The last accepted report wins until completion. Outside registered execution,
`context()` returns null and `result()` returns false with a console warning.
Task creation and control are available through `ai.agent` from build 5293.
See [execution tests](agent-script-execution-testing.md) for the invocation stop
token, confirmed-manifest check and real-device commands, and the maintained
[public API](https://github.com/SuperMonster003/AutoJs6-Documentation/blob/master/api/ai.md)
for task handles and result semantics. A risk declaration describes a reviewed
script; it does not sandbox JavaScript or grant extra host capabilities.
