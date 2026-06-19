# Package Manager And Vendoring Strategy

## Status

Decision gate. No runtime implementation is enabled by this document.

AutoJs6 Safe Node Profile supports checked-in pure JavaScript CommonJS dependencies under `node_modules`, but it does not run `npm install`, package lifecycle scripts, native addon builds, or package managers inside the Android runtime.

## Goals

- Keep dependency handling reproducible and inspectable.
- Preserve scoped filesystem and builtin allowlist guarantees.
- Support packaged APK dependency bundling without running code during packaging.
- Make licenses and dependency size visible before release.
- Provide a path toward a future safe bundler without implying full npm compatibility.

## Non-Goals

- Runtime `npm install` on Android.
- Running `preinstall`, `install`, `postinstall`, or `prepare`.
- Native addon compilation or loading.
- Executing arbitrary package manager plugin hooks.
- Creating `.bin` symlink farms on Android.
- Supporting packages that require unrestricted network, child process, native addon, or Java interop.

## Options

### A. No Runtime Install; User Vendors `node_modules`

Users prepare dependencies outside AutoJs6 and include the resulting `node_modules` tree in the project.

Pros:

- Smallest runtime attack surface.
- No Android package manager process, shell execution, or network dependency.
- Works with current resolver and packaged APK flow.
- Easy to explain for Safe Node Profile v1.

Cons:

- Users need desktop tooling.
- Unsafe files must be detected by tests or packaging checks.
- License and size visibility require extra tooling.

Decision: recommended current model.

### B. Build-Time Install On Desktop Only

Gradle or a host tool may run a package manager before packaging, but only on the developer machine.

Pros:

- Matches normal npm workflows.
- Keeps installation away from Android runtime.
- Can be integrated with lockfiles and CI.

Cons:

- Still executes package lifecycle scripts unless disabled.
- CI and developer machines need Node/npm.
- Requires clear policy for lockfiles, cache, and reproducibility.

Decision: allowed only as a user-controlled pre-step. AutoJs6 should not silently run it.

### C. In-App Pure-JS Installer

AutoJs6 downloads package tarballs and extracts pure JavaScript files only.

Pros:

- Better mobile-only experience.
- Could enforce no native addon and no lifecycle scripts.

Cons:

- Requires registry/network policy, TLS/proxy handling, cache integrity, license UI, quota management, and conflict resolution.
- Hard to make reproducible and user-auditable.
- Increases privacy and supply-chain risk.

Decision: planned-only, not part of Safe Node Profile v1.

### D. Full npm Install With Postinstall Disabled

AutoJs6 exposes a mostly normal npm installer but blocks lifecycle scripts and native addons.

Pros:

- Most familiar npm shape.

Cons:

- npm behavior is large and complex.
- Many packages assume lifecycle scripts, `.bin`, native builds, optional platform packages, or network.
- Failure modes are hard to make safe and understandable on Android.

Decision: rejected for the default profile.

## Recommended Strategy

AutoJs6 should use a two-stage model:

1. User or CI vendors dependencies outside the Android runtime.
2. AutoJs6 verifies and bundles the dependency tree without executing package code.

The `:app:bundleNodeDependencies` task consumes an existing project directory and produces:

- sanitized dependency bundle
- dependency manifest
- size report
- license report
- rejection report for unsupported files or policies

## Required Safety Checks

The bundler or packaged APK verifier must reject or report:

- `.node` native addons unless an explicit future developer-mode policy allows them
- `preinstall`, `install`, `postinstall`, and `prepare` scripts
- symlink escapes
- files outside the project root
- absolute paths in package targets
- package `exports`/`imports` targets that escape the package root
- packages exceeding configured size limits
- ESM-only packages unless the relevant experimental ESM policy is active
- executable `.bin` entries that require shell semantics
- hidden package manager caches that are not explicitly included by policy

## Lockfiles

Recommended lockfile order:

1. `package-lock.json`
2. `pnpm-lock.yaml`
3. `yarn.lock`

Safe Node Profile v1 should record lockfile metadata if present, but it should not implement lockfile resolution on Android. A future host-side bundler may use lockfiles to verify dependency identity and reproducibility.

## Package Size Limits

Initial suggested defaults:

- maximum single package unpacked size: 5 MiB
- maximum dependency bundle unpacked size: 25 MiB
- maximum single file size: 2 MiB unless explicitly allowed
- maximum package count: 200

The values are release-policy defaults, not resolver semantics. A future bundler can expose project overrides within hard upper bounds.

## License Display

The dependency manifest should include:

- package name
- version
- license field
- license file presence
- repository/homepage if present
- package size
- dependency path

Packaged APK tooling should be able to export this manifest so users can review third-party license obligations before release.

## Packaged APK Bundling

Packaged APK builds should consume sanitized dependencies, not arbitrary host paths. The packager should include the dependency manifest and fail with actionable diagnostics when:

- a dependency is rejected by policy
- a dependency target escapes the project root
- an expected package entry is missing
- package size limits are exceeded
- package metadata is malformed

## Current Compatibility Statement

Supported:

- checked-in pure JavaScript CommonJS `node_modules`
- package `main`, `exports`, `imports`, and self-reference under the current resolver
- fixed offline compatibility fixtures
- packaged APK inclusion of vetted JavaScript dependency files

Unsupported:

- runtime `npm install`
- package manager lifecycle scripts
- native addon installation
- postinstall-generated sources
- arbitrary `.bin` execution
- mobile registry downloads

## Follow-Up Implementation

Task 76 adds the first build-time dependency bundler:

```powershell
.\gradlew.bat --console=plain :app:bundleNodeDependencies
.\gradlew.bat --console=plain :app:prepareNodeNpmCiDependencies
.\gradlew.bat --console=plain :app:verifyNodeDependencyBundlerFixtures
```

Default input is `sample/nodejs/npm-pure-js-cjs`. Override with:

```powershell
.\gradlew.bat --console=plain :app:bundleNodeDependencies "-Pautojs.nodejs.bundle.projectRoot=D:\path\to\project"
.\gradlew.bat --console=plain :app:prepareNodeNpmCiDependencies "-Pautojs.nodejs.npmCi.projectRoot=D:\path\to\project"
```

Outputs:

- `app/build/nodejs/dependency-bundle/project/`
- `app/build/nodejs/npm-ci-bundle/project/`
- `app/build/nodejs/dependency-cache/<bundle-hash>/project/`
- `app/build/reports/nodejs/dependencies/dependency-manifest.json`
- `app/build/reports/nodejs/dependencies/dependency-compatibility.json`
- `app/build/reports/nodejs/npm-ci/dependency-manifest.json`
- `app/build/reports/nodejs/dependencies/dependency-size-report.txt`
- `app/build/reports/nodejs/dependencies/dependency-rejections.txt`

The direct bundler task does not execute package code. The desktop-assisted npm route requires `package-lock.json`, copies
the project to a temporary host work directory, runs `npm ci --ignore-scripts --no-bin-links --install-links --no-audit
--fund=false`, verifies `package.json` and `package-lock.json` were not mutated, rejects generated `.bin` entries, and
then runs the same sanitizer. Both routes reject native addon files, lifecycle scripts, unsupported `.bin` entries,
symlinks, path escapes, unsupported package-manager layouts, oversized files/packages/trees, excessive package counts, and
default-profile ESM-only package metadata.

The dependency manifest records a normalized sanitized-project bundle hash and uses that hash as the content-addressed
cache key. The hash covers `package.json`, optional `package-lock.json`, and `node_modules` file paths/content only; host
working directories and cache paths are not part of the hash. Identical lockfiles and install options should therefore
produce the same bundle hash. Package entries include package/version, integrity or content hash fallback, license,
file count, total bytes, unsupported capability usage, and an AutoJs6 compatibility score. The SBOM-lite report mirrors
the package identity, integrity, license, size, capability, and score fields for packaged APK review.

`dependency-compatibility.json` is the v1.1 dependency compatibility analyzer output. It classifies the installed project
as `compatible`, `compatible with capability`, `experimental`, `unsupported`, or `unsafe`; records ESM/CJS entry format,
required builtins, declared and required capabilities, native/lifecycle/raw-network/process/fs-scope signals,
package exports/imports conditions, and expected packaged graph size; and is summarized from
`dependency-manifest.json` so APK-builder preflight gates can reject `unsafe` and `unsupported` bundles before copying
`assets/project`.
