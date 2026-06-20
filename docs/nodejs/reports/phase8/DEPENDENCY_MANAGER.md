# S8-45 Dependency Manager

## Status

Complete for the host-side v1.1 project action.

S8-45 adds `:app:manageNodeDependencies` as an integrated dependency manager
entry point for Safe Node Profile projects that use `package-lock.json`. It
does not add package installation to the Android runtime.

## Action Chain

The manager performs the following host-side steps:

1. Validate the source project has `package.json` and `package-lock.json`.
2. Copy the project to a clean work directory without existing `node_modules`.
3. Run `npm ci --ignore-scripts --no-bin-links --install-links --no-audit --fund=false`.
4. Verify `package.json` and `package-lock.json` were not mutated.
5. Reject generated `node_modules/.bin`.
6. Run the existing Safe Node dependency sanitizer and compatibility analyzer.
7. Write manifest, SBOM-lite, compatibility, size, rejection, and manager reports.
8. Copy the sanitized project layout for packaged APK `assets/project`.
9. Write the content-addressed dependency cache entry.
10. Re-verify the packaged layout and blocking compatibility status.

## Reports

Default outputs:

- `app/build/nodejs/dependency-manager/project/`
- `app/build/reports/nodejs/dependency-manager/dependency-manager-report.json`
- `app/build/reports/nodejs/dependency-manager/dependency-manifest.json`
- `app/build/reports/nodejs/dependency-manager/dependency-sbom-lite.json`
- `app/build/reports/nodejs/dependency-manager/dependency-compatibility.json`
- `app/build/reports/nodejs/dependency-manager/dependency-size-report.txt`
- `app/build/reports/nodejs/dependency-manager/dependency-rejections.txt`
- `app/build/nodejs/dependency-cache/<bundle-hash>/project/`

`dependency-manager-report.json` uses schema
`autojs6-node-dependency-manager-v1`. The report records the action chain,
install command, lockfile hash, bundle hash, cache key, report index, package
totals, compatibility status, and safety controls.

## Safety Boundary

The manager remains host-side only:

- Android runtime install is not enabled.
- Package lifecycle scripts are not executed by default.
- `.bin` links are disabled and rejected if generated.
- Native addons, node-gyp artifacts, postinstall artifacts, suspicious
  executables, symlink/path escapes, unsupported package-manager layouts, and
  oversized dependency trees stay rejected by the existing sanitizer.
- `unsafe` and `unsupported` compatibility analyzer statuses block packaged
  layout verification.

## Verification

Focused host gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeDependencyManagerFixture
```

The fixture uses `sample/nodejs/npm-ci-pure-js`, whose dependencies resolve via
local `file:` packages so the gate does not require registry access.

Broader dependency gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeDependencyBundlerFixtures
```

This now depends on `verifyNodeDependencyManagerFixture`, preserving coverage in
the existing host gate path.
