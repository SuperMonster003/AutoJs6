# Third-Party Notices

This file records third-party notices relevant to the ACE editor integration.

## ACE editor

- Component: ACE editor build assets
- Version path: `app/src/main/assets/editor/ace-builds-1.4.12`
- Upstream license file: `app/src/main/assets/editor/ace-builds-1.4.12/LICENSE`
- License: BSD-style license from Ajax.org B.V.
- Bundled files include ACE core, JavaScript mode/worker, themes, language tools, searchbox, and `ext-beautify.js`.

## AutoJs6 ACE integration assets

- Component: AutoJs6-owned ACE wrapper assets
- Path: `app/src/main/assets/editor/ace-builds-1.4.12/autojs6`
- Files: `autojs6_editor.html`, `autojs6_ace_bridge.js`, `autojs6_completer.js`, `autojs6_indices.js`, `autojs6.css`
- These files are project integration code and are covered by the repository license unless a file says otherwise.

## AutoJs6 TypeScript declarations

- Component: AutoJs6 TypeScript Declarations
- Package: `@sm003/autojs6-dts`
- Version: 3.0.0
- Path: `app/src/main/assets/editor/ace-builds-1.4.12/autojs6/types/autojs6`
- License: MPL-2.0
- Notice file: `app/src/main/assets/editor/ace-builds-1.4.12/autojs6/types/autojs6/LICENSE`

The integration bundles selected `aj6-int-*.d.ts` files and `aj6-declarations.d.ts` as the source for ACE static completion generation. It intentionally does not bundle the full `aj6-main-app.d.ts`, Android declaration graph, collections, or libraries declaration sets.

## Font assets

### JetBrains Mono

- Component: JetBrains Mono
- Version: 2.304
- Path: `app/src/main/assets/editor/ace-builds-1.4.12/fonts/jetbrains-mono`
- License: SIL Open Font License 1.1
- Notice files: `OFL.txt`, `AUTHORS.txt`

The integration bundles only the Regular, Italic, Bold, and BoldItalic WOFF2 files needed by the ACE editor.

### Source Code Pro

- Component: Source Code Pro
- Version package: `2.042R-u_1.062R-i_1.026Rvf`
- Path: `app/src/main/assets/editor/ace-builds-1.4.12/fonts/source-code-pro`
- License: SIL Open Font License 1.1
- Notice file: `LICENSE.md`

The integration bundles only the static TTF WOFF2 Regular, Italic, Bold, and BoldItalic files. Variable font assets are intentionally not bundled because the static files are more predictable across the Android WebView compatibility range.

No Consolas or other proprietary font file is redistributed by this integration.
