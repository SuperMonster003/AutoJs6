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
- `notifications`
- `sensors`
- `ui`
- `fetch`
- `axios`
- `websocket`
- `work_manager`

They intentionally do not declare experimental Java interop or unrestricted desktop Node.js capabilities. The declarations are checked by `:app:verifyAutoJs6NodeTypeDeclarations`; the sample project at `sample/nodejs/typescript-smoke` can be type-checked with:

```powershell
tsc -p sample/nodejs/typescript-smoke/tsconfig.json --pretty false
```
