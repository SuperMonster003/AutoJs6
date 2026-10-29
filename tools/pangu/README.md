# Bundled pangu.js

The Bundled Libraries plugin (AutoJs6-Plugin-Bundled-Libraries) carries the text API from
[pangu.js 10.1.0](https://github.com/vinta/pangu.js/tree/v10.1.0),
published as [pangu 10.1.0 on npm](https://www.npmjs.com/package/pangu/v/10.1.0).
Since augment P5.2 the AutoJs6 APK no longer has it; this build writes the
module to the plugin's `app/src/main/assets/modules`, where the plugin's Rhino
module tests (`PanguModuleTest`) read it (augment appendix C Q182).
The output directory is, in this order, the one given by `--out <dir>`, the
`AUTOJS6_BUNDLED_LIBRARIES_MODULES` environment variable, or
`../../../AutoJs6-Plugin-Bundled-Libraries/app/src/main/assets/modules`
(a checkout of the plugin next to this repository).
`package-lock.json` pins the npm tarball integrity and the build tools.

The upstream browser bundle contains class syntax that the bundled Rhino engine
cannot parse. Its DOM services also require a browser. The Node.js entry depends
on Node.js file APIs. We instead compile `src/shared/index.ts` from the same
release to ES5 CommonJS and minify it as `modules/pangu.min.js`.
The module exports the upstream `pangu` instance with `version`, `spaceText(text)`
and `hasProperSpacing(text)`. `rhino-compat.cjs` rewrites the two nested
name-suffix lookbehind guards as equivalent forward prefix checks because
Rhino otherwise inserts spaces inside names such as `公視+` and `AB-`.
The remaining rules are preserved. No global polyfills or browser/Node.js
services are installed.

The MIT license is kept alongside the module as `pangu.LICENSE.txt`.

From this directory:

```powershell
npm ci --ignore-scripts
npm run build
npm run check
npm run build -- --out D:/work/AutoJs6-Plugin-Bundled-Libraries/app/src/main/assets/modules
```

The plugin's Gradle build consumes the committed files and does not need npm or a
network request to load `pangu`. When upgrading, update the pinned version and
lockfile, rebuild into the plugin, run the plugin's unit tests
(`gradlew :app:testDebugUnitTest`), commit the module files there, and synchronize
the declarations and API docs.
