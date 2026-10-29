# Bundled pangu.js

The Bundled Libraries plugin (AutoJs6-Plugin-Bundled-Libraries) carries the text API from
[pangu.js 10.1.0](https://github.com/vinta/pangu.js/tree/v10.1.0),
published as [pangu 10.1.0 on npm](https://www.npmjs.com/package/pangu/v/10.1.0).
Since augment P5.2 the AutoJs6 APK no longer has it; this build writes the
module to the host's test resources (`app/src/test/resources/script-modules/bundled-libraries`),
where the Rhino module tests read it.
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
```

Gradle consumes the committed files and does not need npm or a network request
to load `pangu`. When upgrading, update the pinned version and lockfile, rebuild,
run the Rhino module tests, copy `pangu.min.js` and `pangu.LICENSE.txt` to the
plugin's `app/src/main/assets/modules`, and synchronize the declarations and API docs.
