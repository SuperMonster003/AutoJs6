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

## Font assets

No `.ttf`, `.otf`, `.woff`, or `.woff2` font files are bundled under the ACE asset tree. The ACE integration CSS uses the generic system `monospace` family, so no Consolas or other proprietary font file is redistributed by this integration.
