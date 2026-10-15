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

## Plan semantics

The official plugin (plugin roadmap P2) computes every item in request order:

1. Items excluded by the caller, or by `includeFiles`, `includeFolders` and `includeSubfolders` (depth above 0),
   keep their names (`excluded`).
2. The `target` part is the name stem, the extension or the whole name. Only files have an extension: the text
   after the last dot, unless that dot leads (`.bashrc`) or ends the name. An item without the part, or whose part
   does not match the search, is `unchanged` / `noMatch`. An empty `search` lets every included item take part
   without a replacement, so formatting and cleanup alone apply.
3. The variables of `replace` are expanded for the item: counters `${}` and `${start=X;increment=X;padding=X}`
   (from 0, advancing only for items whose name changes, before conflicts are decided), the date and time
   variables `$YYYY` ... `$f` (24-hour clock, month and weekday names in `locale`, standalone forms), and
   `${rstringalnum=N}`, `${rstringalpha=N}`, `${rstringdigit=N}` (N from 1 to 256) and `${ruuidv4}`. Variables of
   disabled options and text that does not parse stay literal. `$$` is a literal `$` and keeps the following text
   from being read as a variable (`$$YYYY` gives `$YYYY`). With `regex` the remaining text keeps its replacement
   patterns; without it the replacement is literal.
4. The replacement, text formatting and the cleanup options (normalization, half-width conversion, sanitizing,
   trimming, in this order) apply to the part, which is joined back; an empty extension leaves no dot.
5. An unchanged result is `unchanged` / `sameName`; the rest is validated and checked for conflicts.

`conflict` and `invalid` items carry the rejected candidate in `newName` for previews; only `rename` items are
executed. Under `number` the pattern goes before the extension (`photo (1).jpg`) and the numbers start at 1; under
`skip`, `number` and `overwrite` the first of several items that compute the same name keeps it, and `overwrite`
never replaces another item of the batch. Items that keep their names keep occupying them, so a refusal can make
other items conflict as well.

`twoPhase` marks a case-only change in a case-insensitive directory and every rename whose new name another
renamed item of the batch still holds (chains, swaps and cycles). The host executor
(`core/plugin/batchrename/BatchRenameExecutor`) works the same classification out again from the file system when it
runs, moves those items to temporary names first and never depends on the order of the remaining renames; the flag
tells callers which renames take that detour.

Every regular expression evaluation (compile, test or replace) gets one second and a whole plan thirty seconds;
running out answers `LIMIT_EXCEEDED`, which stops catastrophic backtracking.

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

## Script path

`files.batchRename` (`runtime/api/augment/files/BatchRename.kt`, with the Rhino-free logic in
`core/plugin/batchrename/BatchRenameScript*.kt` and `BatchRenameTargetCollector.kt`) is the script side of the
contract:

- `plan(paths, options)` resolves `paths` (a string or an array) against the script's working directory and
  enumerates them: every named path at depth 0 in the given order, and with `includeSubfolders` everything below a
  named directory, depth first and sorted by name. Symbolic links are never followed: a named path must not be one,
  and links found below a directory are skipped but keep their names in `existingNames`. Duplicates are dropped.
  More than 2000 items, 512 parents, 20000 existing names or 32 folder levels is `LIMIT_EXCEEDED`; a missing path,
  the root directory or an empty list is `INVALID_PATHS`. The options become the complete option Bundle with the
  defaults of roadmap D17; a `RegExp` search sets `regex`, takes `matchAll` from `g`, `caseSensitive` from the
  absence of `i` and keeps `m`, `s`, `u`, `y` as `regexFlags`. `locale`, `timeZone` and `now` come from the device
  at planning time.
- The script receives the plan as an object (`id`, `options`, `items`, `counts`, `rejected`); the host keeps the
  request and the answer of the newest 16 plans of each script. `apply(plan, { onConflict, onProgress })` looks the
  plan up by `id`; when the script set `excluded` on items or overrides the policy, the host asks the plugin again
  with the same enumeration, `now` and names. A rejected plan is `PLAN_REJECTED` and renames nothing; the `rename`
  items go to the host executor (`overwrite` for `overwrites` items) with the modification time and size seen at
  planning time, so items that changed since are reported `stale`. A plan runs at most once; applying it again, or
  applying a plan the script no longer keeps, is `PLAN_STALE`.
- `undo(result | batchId)` and `history(limit)` work on the shared journal, which also holds the batches of
  Explorer Action sessions. A batch that does not exist or has nothing left to undo is `UNDO_UNAVAILABLE`.
- Failures reach the script as `files.batchRename.BatchRenameError` with `code`: no installed plugin is
  `PLUGIN_UNAVAILABLE` (with the host's localized "missing plugin" message), a disabled or unauthorized one
  `PLUGIN_DISABLED`, one that needs a newer host or speaks another contract version `PLUGIN_INCOMPATIBLE`; the
  plan error codes `INVALID_OPTIONS` and `LIMIT_EXCEEDED` keep their names, `UNSUPPORTED_CONTRACT_VERSION` becomes
  `PLUGIN_INCOMPATIBLE` and the rest `INTERNAL`.

## Status

The contract (host commit `aa4fd4f6d`), Explorer Action v25 (`95c9f0455`), the Plugin Center
registration, `BatchRenamePluginHost` and the rename dialog "More" menu (`26656b7c4`) are in place, and the
official plugin answers `plan` with its engine (plugin roadmap P2). The host executor, its journal and the Explorer
Action session (`3bcd17aba`, P3.1) and the `files.batchRename` script API (P3.2) are in place as well.
`BatchRenamePluginRoundTripTest` runs contract requests and `BatchRenameScriptApiTest` runs scripts through an
installed plugin. The plugin screen (P4) follows.
