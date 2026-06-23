# AutoJs6 Node TypeScript Declarations

These declarations describe the AutoJs6 Safe Node Profile bridge modules exposed through CommonJS `require()`:

- `toast`
- `app`
- `dialogs`
- `clipboard`
- `device`
- `shell`
- `engines`
- `accessibility`
- `media_projection`
- `image`
- `images`
- `ocr`
- `storage`
- `storages`
- `database`
- `sqlite`
- `console` / `node:console`
- `timers` / `node:timers`
- `notifications`
- `sensors`
- `ui`
- `ui.overlay`
- `input_observer`
- `fetch`
- `axios`
- `websocket`
- `work_manager`
- `autojs6:lifecycle`
- `autojs6:compat`
- `files`
- `base64`
- `colors`
- `formatter` / `fmt`
- `converter` / `cvt`
- `mime`
- `nanoid`
- `util` / `node:util`
- `opencc`
- `pinyin`
- `pinyin4j`
- `plugins`

The `autojs6:lifecycle` module is available only inside the explicit interactive long-running runtime. It stores JSON-only project-scoped checkpoints for explicit restore and does not enable automatic restart.

The `autojs6:compat` module is the explicit Rhino compatibility loader. It does not install Rhino globals by default; scripts must opt in before later Phase 11 facade modules can expose Rhino-style helpers. S11-11 adds the pure utility subset for `base64`, `colors`, `fmt.bytes`, `cvt.bytes`, `mime`, `nanoid`, and Node `util.inspect` migration. S11-12 adds text utility facades for `opencc`, `pinyin`, and `pinyin4j`; resource-backed paths return stable unsupported errors when dictionaries are unavailable. S11-13 adds async-compatible `storages` and callable `sqlite` facades over the controlled `storage/storages` and `database/sqlite` bridge modules. S11-14 adds readable debugging compatibility for `console`, Node timer globals, and source-map-aware error stack output; Android floating-console controls are represented as no-op output-capture shims or stable unsupported calls. S11-16 adds the first selector/accessibility compatibility surface: `compat.text("OK").findOne(1000)`, `find()`, selector `click()`, and coordinate `click/back/home` wrappers are Promise-returning APIs over the checked `accessibility` bridge. S11-17 exposes `compat.app`, `compat.device`, and callable `compat.shell(command, optionsOrRoot)` over the controlled app/device/shell bridges; shell root remains opt-in via `true` or `{ root: true }` and Shizuku remains unsupported. S11-18 exposes `compat.images` with Rhino-style `read`, `save`, region/size overloads, image matching, color search, and explicit `recycle()` over opaque image handles. S11-19 adds Promise-based `requestScreenCapture()` and `captureScreen()` compatibility wrappers over `media_projection`/`images`; permission denial remains a bridge error and packaged fallback does not prompt directly. S11-20 adds callable `compat.ocr` declarations for ML Kit-only OCR over path/handle/capture image inputs with JSON-safe results; barcode/qrcode remain bridge-gap strategy items until a controlled barcode provider is exposed. P12-03 adds pure Rhino `events.keys` constants through `compat.keys` and `compat.events.keys`; explicit global installation may expose `keys`, but does not replace Node's core `events` module or add Android key observers.

`input_observer` is a P12 provider POC. It exposes `observeKeys`, `drainEvents`, `close`, and `getAvailableSources` against fake-source JSON snapshots and the P12-06 `accessibility` live-source gate. Live key observation requires an operational AutoJs6 accessibility service and still remains subject to P12-07 cleanup/security/packaged policy evidence.

`ui.overlay` is a P12-13 fake-provider POC over the P12-12A overlay contract. It exposes JSON-only `show`, `update`, `drainEvents`, `close`, `closeAll`, `hasPermission`, and `openPermissionSettings` shapes with opaque handles. P12-14 adds the Android live gate for permission snapshots, explicit settings dry-run/launch evidence, packaged `SYSTEM_ALERT_WINDOW` metadata, foreground disclosure requirements, and cleanup summaries; live overlay creation remains fail-closed.

They intentionally do not declare experimental Java interop, native plugin loading, Android plugin adapters, or unrestricted desktop Node.js capabilities. The declarations are checked by `:app:verifyAutoJs6NodeTypeDeclarations`; the sample project at `sample/nodejs/typescript-smoke` can be type-checked with:

```powershell
tsc -p sample/nodejs/typescript-smoke/tsconfig.json --pretty false
```
