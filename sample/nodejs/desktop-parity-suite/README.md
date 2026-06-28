# desktop-parity-suite

Phase 13 desktop Node parity example catalog.

This project groups migration-oriented snippets for common desktop Node
surfaces: ESM, worker_threads, http/https, raw network, advanced fs, WASI,
inspector, and npm dependency usage. The main entry is a catalog runner; it prints expected
markers for desktop-profile snippets and Safe-profile denial examples without
claiming that gated desktop authority is already promoted.

| Example | Snippet | Required profile | Required capabilities | Safe profile behavior | Packaged behavior | Skip reason |
| --- | --- | --- | --- | --- | --- | --- |
| ESM loader | `snippets/esm-loader.cjs` | `desktop_compat_opt_in` | `esm_loader` | Safe profile keeps raw loader hooks and network/file/data URL imports denied | managed local ESM graph only until packaged encrypted graph parity lands | managed ESM loader or dynamic import graph may be unavailable |
| Worker threads | `snippets/worker-threads.cjs` | `desktop_compat_opt_in` | `worker_threads` | Safe profile rejects default worker authority | packaged worker execution remains gated by native preflight and source graph evidence | native worker_threads provider may be unavailable |
| HTTP/HTTPS | `snippets/http-https.cjs` | `desktop_compat_opt_in` | `network`, `http`, `https` | Safe profile denies raw server/listen/CONNECT/socket authority | packaged network requires explicit capability, Android permission, and provider evidence | controlled HTTP/HTTPS provider or network permission may be unavailable |
| Raw network | `snippets/raw-network.cjs` | `desktop_compat_opt_in` | `raw_network` | Safe profile denies native Node `http`/`https`/`net`/`tls`/`dns` authority | packaged raw network requires reviewed disclosure, INTERNET permission proof, and rollback policy evidence | raw native Node network modules may be unavailable or disabled by profile policy |
| Advanced fs | `snippets/fs-advanced.cjs` | `desktop_compat_opt_in` | `fs.advanced`, `scoped_fs` | Safe profile stays scoped to project working directory | packaged advanced fs remains gated by close-on-destroy and expanded-root evidence | advanced scoped fs APIs or watcher support may be unavailable |
| WASI | `snippets/wasi.cjs` | `desktop_compat_opt_in` | `wasi` | Safe profile denies raw `wasi` and `node:wasi` | packaged WASI requires scoped preopen, fd/env/args, resource, and worker evidence | controlled WASI provider may be unavailable |
| Inspector | `snippets/inspector.cjs` | `desktop_compat_opt_in` | `inspector` | Safe profile denies inspector and debug session authority | packaged inspector is release-denied until debug UI and redaction evidence land | debug profile or inspector provider may be unavailable |
| npm dependency | `snippets/npm-dependency.cjs` | `desktop_compat_opt_in` | `package_manager`, `pure_js_npm` | Safe profile allows only checked-in sanitized pure JS dependencies | packaged dependency distribution remains gated by integrity and builder evidence | package manager facade or dependency corpus metadata may be unavailable |

Safe-profile denial markers are centralized in `snippets/safe-denials.cjs`.
Expected output is listed in `expected-output.txt`.

Package integrity and rollback guidance is intentionally referenced through
metadata instead of live packaged execution: each entry carries required
profile/capability metadata plus packaged behavior text, and packaged desktop
promotion remains gated by the Phase 13 integrity, disclosure, and rollback
policies before any static snippet can become a packaged smoke.

`smoke.cjs` is the focused runtime smoke subset. It executes only
`snippets/esm-loader.cjs` equivalent local ESM behavior, the controlled
HTTP/HTTPS facade shape used by `snippets/http-https.cjs`, the checked-in
`vendor/pure-math` dependency path used by `snippets/npm-dependency.cjs`, and
the scoped FileHandle plus non-persistent watcher path represented by
`snippets/fs-advanced.cjs`. The HTTP/HTTPS smoke does not dispatch live network
I/O. It does not execute worker, raw network, expanded filesystem-root, WASI,
inspector, or package manager authority examples.
