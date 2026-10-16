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
`batchRenameFeatures` (String array, a subset of `counter`, `dateTime`, `random`, `cleanup`, `pinyin`, `presets`, `metadata`),
`batchRenameMaxItems` (Int) and `batchRenameRegexDialect` (`ecmascript`). The host calls `plan` only
when the advertised contract version lies in its own supported range.

## Binder interface

```java
interface IBatchRenamePlugin {
    org.autojs.plugin.common.api.PluginInfo getInfo();
    Bundle plan(in Bundle request);
    Bundle presets(in Bundle request);        // feature `presets`, plugin 1.1.0
    Bundle importPresets(in Bundle request);  // feature `presets`, plugin 1.1.0
}
```

All methods are synchronous and safe to call concurrently; only `importPresets` changes state (the plugin's
presets). No method throws for invalid input or returns null: a failed call answers `errorCode` and `errorMessage`
only. Methods may only be appended in later versions; hosts call the preset methods only on plugins whose
`batchRenameFeatures` contain `presets`.

## Request

| Key | Type | Meaning |
| --- | --- | --- |
| `contractVersion` | Int | the host's contract version (1) |
| `options` | Bundle | rename options, below |
| `parents` | ArrayList of Bundle | one entry per directory: `parentId`, `existingNames`, `caseInsensitive`, `restrictedCharacters` |
| `items` | ArrayList of Bundle | targets in enumeration order: `index`, `parentId`, `name`, `kind` (1 file, 2 directory), `depth`, `lastModified`, `length`, `excluded`, `metadata` (feature `metadata`) |
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
| `pinyin` | Boolean: convert Chinese characters to pinyin (after the replacement, before `format`) | `false` |
| `pinyinTone` | `none`, `mark`, `number` | `none` |
| `pinyinSeparator` | `none`, `space`, `underscore`, `hyphen` (between syllables) | `none` |
| `pinyinCase` | `lower`, `capitalize` | `lower` |
| `pinyinInitials` | Boolean: only the first letter of each syllable | `false` |

The `pinyin*` keys (contract 1, added with plugin 1.1.0) also style the `${pinyin}` variable of the replacement.
Hosts send them only to plugins whose `batchRenameFeatures` contain `pinyin`; older plugins ignore unknown keys,
so a script that asks for pinyin from such a plugin fails with `PLUGIN_INCOMPATIBLE` instead.

`metadata` (Boolean, default `false`, feature `metadata`, plugin 1.1.0) expands the 25 photo metadata variables of
PowerRename (`$CAMERA_MAKE`, `$CAMERA_MODEL`, `$LENS`, `$ISO`, `$APERTURE`, `$SHUTTER`, `$FOCAL`, `$FLASH`, `$WIDTH`,
`$HEIGHT`, `$AUTHOR`, `$COPYRIGHT`, `$LATITUDE`, `$LONGITUDE`, `$ALTITUDE`, `$DATE_TAKEN_YYYY`, `$DATE_TAKEN_YY`,
`$DATE_TAKEN_MM`, `$DATE_TAKEN_DD`, `$DATE_TAKEN_HH`, `$DATE_TAKEN_mm`, `$DATE_TAKEN_SS`, `$EXPOSURE_BIAS`,
`$ORIENTATION`, `$COLOR_SPACE`) from the item key `metadata` (a Bundle keyed by `BatchRenameMetadataKeys`, raw values
the host read). They are matched before the date variables, longest name first, and stay literal while the option is
off. The plugin formats them (plugin roadmap D47-D50): `f2.8`, `1-250s`, `50mm`, `+0.3EV`, `120m`, `On` / `Off`,
`0` / `90M`, `sRGB` / `AdobeRGB` / `Uncalibrated`, degrees with six decimals; a missing value becomes empty and the
answer item carries `metadataMissing`, except `$DATE_TAKEN_*`, which fall back to the modification time in `timeZone`.

`preset` (String, feature `presets`, plugin 1.1.0) names a preset the plugin keeps. The plugin starts from the
defaults, applies the preset's options and then every other key of the request, so a request that names a preset
carries only the keys it overrides and may leave out `search`. An unknown name is `INVALID_OPTIONS`.

Regular expressions follow ECMAScript exactly, like `RegExp` in scripts: `g` comes from `matchAll`
and `i` from the absence of `caseSensitive`; the replacement supports `$1`, `$<name>`, `$&`,
`` $` ``, `$'` and `$$`. A plain-text search is escaped and evaluated the same way.

## Answer

| Key | Type | Meaning |
| --- | --- | --- |
| `contractVersion` | Int | equal to the request's |
| `errorCode`, `errorMessage` | String | only on failure |
| `rejected` | Boolean | the plan must not be executed |
| `items` | ArrayList of Bundle | one per request item, in order: `index`, `newName`, `status`, `reason`, `twoPhase`, `overwrites`, `metadataMissing` (feature `metadata`, when true) |
| `counts` | Bundle | Int per status |
| `options` | Bundle | the complete options the plan used, without `preset` (feature `presets`, every plan) |

Statuses: `rename`, `unchanged`, `conflict`, `invalid`, `excluded`. Reasons: `noMatch`, `sameName`,
`excludedByCaller`, `notIncluded`, `duplicateInBatch`, `existingEntry`, `numbersExhausted`,
`numbered`, `overwrite`, `empty`, `dotName`, `separator`, `tooLong`, `restrictedCharacter`. A plan is
`rejected` when any item is `invalid`, or when an item is in `conflict` under the `fail` policy;
under `skip` conflicting items keep their names, under `number` they get the first free numbered
name (up to 999), under `overwrite` they replace the existing entry (`overwrites`), which the host
moves to its Trash first.

Error codes: `UNSUPPORTED_CONTRACT_VERSION`, `INVALID_REQUEST`, `INVALID_OPTIONS` (including a regular
expression that does not compile), `LIMIT_EXCEEDED`, `INTERNAL_ERROR`.

## Presets

Named presets (plugin roadmap D36) belong to the plugin: its screen saves, loads, renames, deletes, imports and
exports them, and plans resolve `preset` against them. Both preset requests carry `contractVersion`; both answers
carry `contractVersion` and, on failure, `errorCode` and `errorMessage`.

| Call | Request | Answer |
| --- | --- | --- |
| `presets` | - | `presets`: ArrayList of Bundle (`name`, `options` with the complete options), in the plugin's order |
| `importPresets` | `document`: String, `onConflict`: `skip` (default), `overwrite`, `rename` | `imported` and `skipped`: ArrayList of String; `rejected`: ArrayList of Bundle (`index`, `name` when readable, `reason`) |

The document is the JSON the plugin exports: `{"format": "autojs6-batch-rename-presets", "version": 1, "presets":
[{"name": ..., "options": {...}}]}` with the option keys of the request (strings and booleans; missing keys take
their defaults, unknown keys are dropped). A document that is not of this format, or is longer than 131072
characters, fails as a whole with `INVALID_OPTIONS` or `LIMIT_EXCEEDED`; a single entry with a missing, blank,
duplicate or over-long name, or with option values outside the contract, is listed under `rejected` and the others are
imported. Under `skip` an entry whose name is taken keeps the existing preset; `rename` stores it under the first
free `name (n)`. Entries that would exceed 100 presets or 131072 characters of stored presets are rejected.

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
| Presets kept by the plugin | 100 |
| Characters of a preset name | 64 |
| Characters of a preset document (and of all stored presets) | 131072 |

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
- With `options.preset` the host sends only the options the script gave (validated the same way) plus `preset`,
  and `plan.options` shows the complete options the plugin answers, which `apply` sends again without the preset.
  `presets()` lists `{ name, options }` objects and `importPresets(json, { onConflict })` (a JSON string or an
  object) answers `{ imported, skipped, rejected }`; both have `Async` forms. A plugin without the `presets` feature
  makes all three `PLUGIN_INCOMPATIBLE`.
- With `metadata: true` and a metadata variable in `replace`, the host reads the image files among the targets
  (`BatchRenameMetadataReader`: EXIF, and XMP for author and copyright; four at a time) and sends their values with
  the items; otherwise it reads nothing. A plugin without the `metadata` feature makes `metadata: true`
  `PLUGIN_INCOMPATIBLE`. The file manager path gets the same values through Explorer Action v26 `readMetadata`.
- `preview(nameOrItems, options)` (and `previewAsync`, plugin roadmap D37) asks `plan` about names that need not
  exist: a name, `{ name, isDirectory, lastModified }` (epoch milliseconds or a `Date`; the time of the call when
  missing) or an array of them. The host sends them as the only entries of one directory without file-system traits,
  so counters advance in array order and conflicts come only from the array itself; nothing is enumerated or renamed.
  The answer is `{ newName, status, reason }`, or an array of them for an array. `metadata: true` is
  `INVALID_OPTIONS`, since preview reads no files.
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
Action session (`3bcd17aba`, P3.1) and the `files.batchRename` script API (`852afb776`, P3.2) are in place as
well, and so is the plugin screen (plugin roadmap P4). `BatchRenamePluginRoundTripTest` runs contract requests,
`BatchRenameScriptApiTest` runs scripts and `BatchRenameExplorerFlowTest` drives the three file manager entries
through an installed plugin. The Batch Rename plugin 1.0.0 requires host build 5438, the first build with every
change above (`852afb776`).
