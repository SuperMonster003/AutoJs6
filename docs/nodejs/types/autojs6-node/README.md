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

The `autojs6:compat` module is the explicit Rhino compatibility loader. It does not install Rhino globals by default; scripts must opt in before later Phase 11 facade modules can expose Rhino-style helpers. S11-11 adds the pure utility subset for `base64`, `colors`, `fmt.bytes`, `cvt.bytes`, `mime`, `nanoid`, and Node `util.inspect` migration. S11-12 adds text utility facades for `opencc`, `pinyin`, and `pinyin4j`; resource-backed paths return stable unsupported errors when dictionaries are unavailable. S11-13 adds async-compatible `storages` and callable `sqlite` facades over the controlled `storage/storages` and `database/sqlite` bridge modules. S11-14 adds readable debugging compatibility for `console`, Node timer globals, and source-map-aware error stack output; Android floating-console controls are represented as no-op output-capture shims or stable unsupported calls.

They intentionally do not declare experimental Java interop, native plugin loading, Android plugin adapters, or unrestricted desktop Node.js capabilities. The declarations are checked by `:app:verifyAutoJs6NodeTypeDeclarations`; the sample project at `sample/nodejs/typescript-smoke` can be type-checked with:

```powershell
tsc -p sample/nodejs/typescript-smoke/tsconfig.json --pretty false
```
