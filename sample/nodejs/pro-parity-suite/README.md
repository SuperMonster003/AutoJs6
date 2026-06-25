# pro-parity-suite

Phase 13 Auto.js Pro parity example catalog.

This project groups migration-oriented snippets for the most common Pro-style
Node surfaces: `$autojs.java`, `rhino.install`, UI, floaty/overlay, tasks,
screenshot/OCR, and accessibility. The main entry is a catalog runner; it
prints the expected marker for each snippet without claiming that gated Android
permissions or future providers are already promoted.

| Example | Snippet | Required capabilities | Packaged behavior | Skip reason |
| --- | --- | --- | --- | --- |
| Java interop | `snippets/java-interop.cjs` | `java_interop` | packaged Java interop stays metadata-only until profile promotion and allowlist review | `java_interop` requires explicit Pro/debug opt-in and allowlist provider |
| Rhino install | `snippets/rhino-install.cjs` | `rhino`, `java_interop` | packaged Rhino proxy globals stay experimental and metadata-only | `rhino.install({ experimental: true })` requires Java proxy provider readiness |
| UI layout | `snippets/ui-layout.cjs` | `ui` | packaged UI is provider-dependent until lifecycle/disclosure evidence lands | live Activity-owned UI provider may be unavailable |
| Floaty overlay | `snippets/overlay-floaty.cjs` | `ui.overlay`, `ui.overlay.permission` | packaged overlay requires reviewed disclosure and foreground notification ownership | overlay permission or promoted overlay provider may be unavailable |
| Tasks | `snippets/tasks-work-manager.cjs` | `work_manager` | one-shot metadata is supported; daily/weekly/intent tasks remain gated | WorkManager provider or persistent task rows may be unavailable |
| Screenshot/OCR | `snippets/screenshot-ocr.cjs` | `screen_capture`, `image`, `ocr` | packaged capture/OCR remains gated by disclosure and permission review | MediaProjection permission, image handle, or OCR provider may be unavailable |
| Accessibility | `snippets/accessibility-selector.cjs` | `accessibility` | packaged accessibility needs explicit service/disclosure review | accessibility service may be disabled |

Expected output is listed in `expected-output.txt`.
