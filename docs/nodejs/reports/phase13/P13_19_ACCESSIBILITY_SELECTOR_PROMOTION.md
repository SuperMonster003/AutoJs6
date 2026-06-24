# P13-19 Accessibility Selector Promotion

Status: partial

P13-19 records the current `require("accessibility")` and
`require("autojs6:compat")` selector state for Phase 13. The bridge already has
a useful Pro/Rhino migration slice: selector v2 builders produce frozen JSON
descriptors, selector queries return frozen JSON node snapshots, common
selector actions route through the Android accessibility service when it is
visible in the current process, and Rhino-style selector handles are available
through the explicit compat loader.

The surface is not promoted to `pro_compat_opt_in` yet. Full Auto.js Pro
automation parity still needs a packaged accessibility-service lifecycle,
broader selector aliases, `waitFor` semantics, gesture APIs, and clear
root/Shizuku separation.

## Implemented Evidence

- Direct JavaScript module: `require("accessibility")` exposes
  `isEnabled`, `ensureEnabled`, coordinate `click`, selector `click`,
  `back`, `home`, `recentApps`, `findByText`, `clickText`, `findOne`,
  `findAll`, `longClick`, `setText`, `scrollForward`, and `scrollBackward`.
- Selector builders: `text`, `desc`, `id`, `className`, contains/matches
  variants, `clickable`, `enabled`, `depth`, `boundsInside`, and
  `boundsContains` produce frozen selector descriptors.
- Android live path: when an operational AutoJs6 accessibility service is
  visible in the current process, the provider can run selector find/action
  calls and Back/Home/Recents global actions; when unavailable, actions reject
  with a stable bridge permission error while `isEnabled` and `ensureEnabled`
  resolve `false`.
- Rhino compat facade: `compat.text("OK").findOne(1000)`, `find()`,
  `findAll()`, `click()`, `longClick()`, `setText()`, `scrollForward()`,
  `scrollBackward()`, `exists()`, selector builders, coordinate `click`,
  `back`, `home`, `recentApps`, and `recents` are Promise-based wrappers over
  the same accessibility bridge.
- Raw boundary: raw `AccessibilityService`, `AccessibilityNodeInfo`,
  `UiObject`, live selector objects, Android `Context`, Binder, and Java
  objects do not cross into Node.
- Type declarations: `docs/nodejs/types/autojs6-node/accessibility.d.ts`,
  `rhino_compat.d.ts`, and the TypeScript smoke sample cover the direct and
  compat selector surfaces.

## Boundaries

- `swipe`, `gesture`, `powerDialog`, direct `waitFor`, raw node handles,
  raw `UiObject`, and root/Shizuku gesture fallbacks are not promoted by
  P13-19.
- Direct coordinate `click(x, y)` is part of the JavaScript facade and fake
  transport tests, but the live Android provider currently performs real
  clicks only through selector descriptors.
- The optional real-device accessibility smoke remains manual. It must not
  enable the accessibility service or change Android accessibility settings.
- Packaged APK accessibility-service ownership, settings flow, foreground
  disclosure, denial cases, restore behavior, and service disconnect cleanup
  still need evidence.
- The current Rhino compat surface is async/Promise based. It is not a
  drop-in synchronous Rhino selector runtime.

## Remaining Blockers

- Implement or explicitly deny more Rhino selector aliases and common chain
  helpers, including `waitFor`, `pickup`, `detect`, and scrollable helpers.
- Add a separate reviewed gesture provider for `swipe`, `gesture`, and
  `powerDialog`, including root/Shizuku tiers when applicable.
- Decide and test live coordinate-click behavior, or keep it as a stable
  fake-provider/compat-only surface with clear documentation.
- Add packaged APK install-run evidence for accessibility service metadata,
  settings guidance, provider availability, service disconnect cleanup,
  missing-capability denial, and user-visible disclosure.
- Expand the real-device accessibility matrix for selector actions and safe
  optional Back/Home/Recents validation.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase13/verify-accessibility-selector-promotion.js --repo-root D:\idea-projects\AutoJs6 --report-dir D:\idea-projects\AutoJs6\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13AccessibilitySelectorPromotion
```

Expected reports:

```text
build/reports/nodejs/phase13-accessibility-selector-promotion.json
build/reports/nodejs/phase13-accessibility-selector-promotion.md
```
