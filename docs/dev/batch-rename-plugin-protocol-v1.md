# Batch Rename plugin protocol v1

## Purpose

The Batch Rename plugin (`AutoJs6-Plugin-Batch-Rename`) renames many files and folders at once with
search and replace, ECMAScript regular expressions, counters, date and random variables and a few
cleanup rules, modeled on PowerToys PowerRename. AutoJs6 offers it in two places:

- the script API `files.batchRename` (plan, apply, undo, history);
- the file manager: the selection toolbar, the folder menu and the "More" menu of the rename dialog.

Both paths share one division of work (plugin roadmap D3 / D13): the plugin computes rename plans,
the host enumerates targets, performs every rename, checks conflicts again and keeps the undo
journal. The plugin never needs storage permission.

The contract lives in the host module `plugin-api/batch-rename-api`; its literals are frozen by
`BatchRenameContractTest` and its transaction order by `BatchRenameAidlOrderTest`. The file manager
path additionally uses Explorer Action protocol v25 (`explorer-action-protocol-v25.md`).

## Discovery

| Item | Value |
| --- | --- |
| Capability service | action `org.autojs.plugin.BATCH_RENAME`, category `batch-rename` |
| Info service | action `org.autojs.plugin.INFO`, category `batch-rename` |
| Permission | `org.autojs.permission.PLUGIN` (signature) on every exported entry |
| Identity | id / engine `batch-rename`, variant `default` |
| Package | `io.github.supermonster003.autojs6.plugin.batch.rename` |

`PluginInfo.capabilities` carries `requiresHostVersion` (Long), `batchRenameContractVersion` (Int),
`batchRenameFeatures` (String array, a subset of `counter`, `dateTime`, `random`, `cleanup`),
`batchRenameMaxItems` (Int) and `batchRenameRegexDialect` (`ecmascript`). The host calls `plan` only
when the advertised contract version lies in its own supported range.

## Binder interface

```java
interface IBatchRenamePlugin {
    org.autojs.plugin.common.api.PluginInfo getInfo();
    Bundle plan(in Bundle request);
}
```

Both methods are synchronous, stateless and safe to call concurrently. `plan` never throws for
invalid input and never returns null: a failed call answers `errorCode` and `errorMessage` only.
Methods may only be appended in later versions.

## Request

| Key | Type | Meaning |
| --- | --- | --- |
| `contractVersion` | Int | the host's contract version (1) |
| `options` | Bundle | rename options, below |
| `parents` | ArrayList of Bundle | one entry per directory: `parentId`, `existingNames`, `caseInsensitive`, `restrictedCharacters` |
| `items` | ArrayList of Bundle | targets in enumeration order: `index`, `parentId`, `name`, `kind` (1 file, 2 directory), `depth`, `lastModified`, `length`, `excluded` |
| `locale` | String | BCP 47 tag for month and weekday names and case conversion |
| `timeZone` | String | IANA id for formatting times |
| `now` | Long | epoch milliseconds for the `now` date source, fixed for the whole plan |

`existingNames` lists every entry of the directory, including the targets, so the plugin detects
conflicts with entries that are not renamed. `caseInsensitive` and `restrictedCharacters` describe
the directory's file system (shared storage and FAT32 / exFAT volumes).

Options (missing keys take the PowerRename defaults of roadmap D17):

| Key | Type | Default |
| --- | --- | --- |
| `search` | String | required |
| `replace` | String | `""` |
| `regex`, `matchAll`, `caseSensitive` | Boolean | `false` |
| `regexFlags` | String of `m`, `s`, `u`, `y` | `""` |
| `target` | `name`, `extension`, `all` | `all` |
| `includeFiles`, `includeFolders`, `includeSubfolders` | Boolean | `true` |
| `format` | `none`, `lower`, `upper`, `title`, `capitalize` | `none` |
| `enumerate`, `random` | Boolean | `false` (variables stay literal) |
| `dateTime` | `modified`, `now`, `none` | `modified` |
| `normalize` | `none`, `nfc`, `nfd` | `none` |
| `halfWidth`, `sanitize`, `trim`, `continueNumbering` | Boolean | `false` |
| `sanitizeReplacement` | String | `_` |
| `onConflict` | `fail`, `skip`, `number`, `overwrite` | `fail` |
| `conflictNumberPattern` | String containing `{n}` | ` ({n})` |

Regular expressions follow ECMAScript exactly, like `RegExp` in scripts: `g` comes from `matchAll`
and `i` from the absence of `caseSensitive`; the replacement supports `$1`, `$<name>`, `$&`,
`` $` ``, `$'` and `$$`. A plain-text search is escaped and evaluated the same way.

## Answer

| Key | Type | Meaning |
| --- | --- | --- |
| `contractVersion` | Int | equal to the request's |
| `errorCode`, `errorMessage` | String | only on failure |
| `rejected` | Boolean | the plan must not be executed |
| `items` | ArrayList of Bundle | one per request item, in order: `index`, `newName`, `status`, `reason`, `twoPhase`, `overwrites` |
| `counts` | Bundle | Int per status |

Statuses: `rename`, `unchanged`, `conflict`, `invalid`, `excluded`. Reasons: `noMatch`, `sameName`,
`excludedByCaller`, `notIncluded`, `duplicateInBatch`, `existingEntry`, `numbersExhausted`,
`numbered`, `overwrite`, `empty`, `dotName`, `separator`, `tooLong`, `restrictedCharacter`. A plan is
`rejected` when any item is `invalid`, or when an item is in `conflict` under the `fail` policy;
under `skip` conflicting items keep their names, under `number` they get the first free numbered
name (up to 999), under `overwrite` they replace the existing entry (`overwrites`), which the host
moves to its Trash first.

Error codes: `UNSUPPORTED_CONTRACT_VERSION`, `INVALID_REQUEST`, `INVALID_OPTIONS` (including a regular
expression that does not compile), `LIMIT_EXCEEDED`, `INTERNAL_ERROR`.

## Limits

| Limit | Value |
| --- | --- |
| Items per plan | 2000 |
| Parent directories per plan | 512 |
| Existing names of all parents | 20000 |
| Characters of one transported name | 1024 |
| Characters of a text option | 4096 |
| Characters of all strings of one request | 262144 |
| Random string length | 256 |
| Conflict numbers tried | 999 |
| UTF-8 bytes of a valid resulting name | 255 |

The character budget keeps one request far below the Binder transaction buffer; the host maps a
`TransactionTooLargeException` to `LIMIT_EXCEEDED` as well.

## Host responsibilities

- Enumerate the targets (no symbolic-link traversal, depth at most 32), collect the existing names
  and file-system traits of every parent, and send one request per plan.
- Treat the answer as a proposal: before renaming, revalidate every item (unchanged identity, valid
  and still free name unless `overwrites`), rename children before parents, use temporary names for
  case-only changes, swaps and cycles, and record each execution as an undoable batch.
- Map plan error codes and missing, disabled or incompatible plugins to the script error codes of
  `files.batchRename` (plugin roadmap D27).

## Status

The contract (host commit `aa4fd4f6d`), Explorer Action v25 (`95c9f0455`), the Plugin Center
registration and `BatchRenamePluginHost` are in place. The plugin engine (plugin roadmap P2), the host executor, journal and script API (P3) and the
plugin service and screen (P4) follow.
