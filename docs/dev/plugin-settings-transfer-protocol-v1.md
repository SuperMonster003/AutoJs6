# Plugin Settings Transfer Protocol V1 (Draft)

Status: draft, not implemented (docs/settings/ROADMAP.md D3, appendix C Q7, P13.2). It describes the second phase of
"Manage AutoJs6 settings": how AutoJs6 asks an official plugin for its own settings when exporting, and hands them
back when importing. Until it is implemented the host registers no `PluginSettingsProvider`, exports no plugin
directory, and counts every plugin directory of an imported archive as `TARGET_MISSING` (P13.1). Names below are
proposals; they become normative once the constants land in `plugin-api:common-plugin-api` as
`AutoJs6PluginSettingsTransferContract`.

## Purpose

The settings archive (`autojs6-settings-<yyyymmdd-hhmmss>.zip`, appendix B of the roadmap) holds the host settings by
category. An official plugin keeps its own settings in its own process (its SharedPreferences, files or database),
which the host cannot and must not read. This protocol lets the plugin give its settings to the host as one JSON
document (`exportSettings`) and take such a document back (`importSettings`). The host stores the document verbatim
as `plugins/<packageName>/settings.json` and never interprets the plugin's own fields.

The direction is the reverse of `official-plugin-settings-contract-v1.md` (the host's read-only appearance snapshot
for plugins): here the plugin exposes the ContentProvider and AutoJs6 is the caller.

## Host side already in place (P13.1)

| Piece | Where | State |
| --- | --- | --- |
| Manifest field `plugins`: `{ "id": "<packageName>", "entries": N }` per plugin | `SettingsArchiveManifest.PluginEntry` | read and written; ids must be package names, listed once |
| Plugin file `plugins/<packageName>/settings.json` | `SettingsArchive.pluginFile`, `SettingsArchiveWriter.PluginFile`, `SettingsArchiveReader.plugin` | read and written |
| Extension point | `core/pref/PluginSettingsProvider` (`exportSettings`, `importSettings`) | interface only |
| Registry | `SettingsRegistry.pluginSettingsProviders` | empty |
| Import plan | `SettingsImportPlanner` (`plugins`, `providers`), `ImportPlan.plugins` | a directory without an installed plugin and provider is skipped whole as `TARGET_MISSING` |
| Archive check | `tools/settings/archive_inspect.py` | entry shape, file present, JSON object |

## Discovery

A plugin takes part when all of these hold:

1. It is an enabled official plugin (signer classified as official, the same admission as its plugin services).
2. Its application declares the meta-data `org.autojs.plugin.contract.SETTINGS_TRANSFER` with the highest protocol
   version it supports (`Int`, `1` for this document).
3. It exports a ContentProvider with the authority `<packageName>.autojs6.settings`, protected by
   `org.autojs.permission.PLUGIN` (the permission that already protects its `org.autojs.plugin.INFO` service), and the
   provider belongs to the plugin package.

The host checks 2 and 3 with `PackageManager` before any call. A plugin that declares a higher version than the host
knows is called with version 1 (see Versioning).

## Calls

Both methods go through `ContentResolver.call(Uri.parse("content://<packageName>.autojs6.settings"), method, null,
extras)` from a worker thread of the host, with a timeout of 10 seconds per call. Every request and every result
carries `protocolVersion`.

### `exportSettings`

Request extras:

| Key | Type | Meaning |
| --- | --- | --- |
| `protocolVersion` | `Int` | `1` |
| `hostPackageName` | `String` | Application id of the calling AutoJs6 |
| `hostVersionCode` | `Long` | Version code of the calling AutoJs6 |
| `purpose` | `String` | `export` (an archive the user saves) or `backup` (the internal backup before an import, see Host behavior) |

Result Bundle:

| Key | Type | Meaning |
| --- | --- | --- |
| `protocolVersion` | `Int` | `1` |
| `payload` | `String` | The JSON document below, at most 256 KiB in UTF-8 |
| `entries` | `Int` | Number of entries the document holds, shown by the wizards as "N items" |
| `error` | `String` | Present only on failure, see Errors |

### `importSettings`

Request extras: `protocolVersion`, `hostPackageName`, `hostVersionCode` as above, and

| Key | Type | Meaning |
| --- | --- | --- |
| `payload` | `String` | A document `exportSettings` returned, on this or another device, by this or another version of the plugin |

Result Bundle:

| Key | Type | Meaning |
| --- | --- | --- |
| `protocolVersion` | `Int` | `1` |
| `written` | `Int` | Entries taken |
| `skipped` | `Int` | Entries left as they were: unknown, invalid, or of a newer schema of the plugin |
| `restartNeeded` | `Boolean` | Optional; the plugin applies some entries only after it restarts |
| `error` | `String` | Present only on failure, see Errors |

## Payload

```json
{
  "format": "autojs6-plugin-settings",
  "formatVersion": 1,
  "packageName": "org.autojs.plugin.sample",
  "versionCode": 12,
  "versionName": "1.2.0",
  "exportedAt": "2026-10-20T15:30:00+08:00",
  "entries": 3,
  "settings": {
    "default": {
      "key_sample_mode": { "t": "string", "v": "fast" },
      "key_sample_limit": { "t": "int", "v": 20 }
    }
  }
}
```

- `format`, `formatVersion`, `packageName` and `entries` are the envelope the host checks: the format name, a
  `formatVersion` it reads (1), the package name of the directory and the number listed in the manifest.
- `versionCode`, `versionName` and `exportedAt` let the plugin migrate documents of its older versions; the host only
  copies them.
- `settings` belongs to the plugin. Plugins whose settings are SharedPreferences should use the typed form of the host's
  category files (store name -> key -> `{ "t", "v" }` with the tags `bool`, `int`, `long`, `float`, `string`,
  `stringSet`, `json`), so that one tool can read both; other shapes are allowed.

## Host behavior

Export:

- The category step of the export wizard lists every plugin found by Discovery after the host categories, titled with
  the plugin label and "N items" from `exportSettings`; plugin settings are selected by default like the host
  categories. A plugin whose call fails or times out is listed as unavailable and cannot be selected.
- For a selected plugin the host writes `payload` verbatim to `plugins/<packageName>/settings.json` and lists
  `{ "id": "<packageName>", "entries": N }` in the manifest. The host never changes the document.

Import:

- `SettingsImportPlanner` hands a plugin directory to its provider only when the plugin is installed and passes
  Discovery; any other directory is skipped as a whole (`TARGET_MISSING`), as in the first phase. The wizard lists
  importable plugins with the host categories.
- The host checks the envelope before calling: a document of another format, of another package name, or not a JSON
  object is skipped as `INVALID_VALUE`; a `formatVersion` above 1 is skipped as `NEWER_FORMAT`.
- Before calling `importSettings` the host calls `exportSettings` with `purpose = backup` and keeps that document in
  the internal backup (T9), so that "Undo last import" hands it back with `importSettings`. A plugin whose backup call
  fails is not imported.
- The result page adds `written` / `skipped` per plugin, reports failures by error code, and offers a restart of the
  plugin when `restartNeeded` is true.

## Plugin behavior

- `call` checks the caller before anything else: the calling package must be an AutoJs6 host that may bind the
  plugin's `org.autojs.plugin.INFO` service; otherwise it throws `SecurityException`. The provider exposes no other
  method, `query`, `insert`, `update`, `delete` or `openFile`.
- `exportSettings` returns settings the user chose, not caches, records of this device, downloaded files, models or
  history. Secrets (tokens, passwords, API keys, pairing keys) are left out in v1 (appendix C Q29).
- `importSettings` is lenient like the host (D9): it takes the entries it knows and that are valid, counts the others as
  `skipped`, and keeps the current value of every key the document does not mention unless the plugin documents that
  a missing key restores its default. It writes all entries at once (for example one `SharedPreferences.Editor.commit`)
  and is safe to repeat with the same document.
- Both calls return within the timeout and do not show any UI.

## Errors

`error` is one of:

| Code | Meaning |
| --- | --- |
| `UNSUPPORTED_PROTOCOL` | The request's `protocolVersion` is not one the plugin serves |
| `INVALID_PAYLOAD` | `importSettings` got a document it cannot read as a whole |
| `NEWER_FORMAT` | The document's `formatVersion`, or the plugin schema in it, is newer than the plugin reads, and nothing could be taken |
| `TOO_LARGE` | The settings exceed the 256 KiB limit of v1 |
| `BUSY` | The plugin cannot export or import now (for example during its own migration); the host may retry once |
| `FAILED` | Any other failure; the plugin logs the cause, the host shows a generic message |

Denied callers get `SecurityException`. The host maps every error, a `SecurityException`, a missing result Bundle and a
timeout to `IOException` in `PluginSettingsProvider` and reports the plugin as failed; it never retries in the
background.

## Versioning

- `protocolVersion` versions the Bundles; this document is version 1. New optional keys may be added within version 1
  and are ignored by readers that do not know them; changing a key's type or meaning needs version 2. The host calls a
  plugin with the lower of the two versions; a plugin that receives a version it does not serve answers
  `UNSUPPORTED_PROTOCOL`.
- `formatVersion` versions the payload envelope. The host skips a document of a higher version (`NEWER_FORMAT`); the
  plugin's own schema inside `settings` is the plugin's business, migrated with `versionCode`.
- The manifest of the archive keeps its own `formatVersion` (appendix B); adding plugin directories does not change it.

## Limits

- 256 KiB per payload in UTF-8, well below the Binder transaction limit; larger settings need a file-based transfer
  (`openFile`) in a later version.
- The archive's limit of 16 MB (T7) applies to all plugin files together with the host categories and data files.

## Open questions

- Appendix C Q29: secrets of plugins. This draft leaves them out of v1. The alternative is a `sensitive` flag in the
  result Bundle that makes the host seal the plugin file like the credentials category (D23 / T21, device slot only,
  later the passphrase slot of P7.7).
