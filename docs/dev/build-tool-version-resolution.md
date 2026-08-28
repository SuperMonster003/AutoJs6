# Build Tool Version Resolution

AutoJs6 resolves its Android build-tool versions from compatibility data bundled in the `org.autojs.build.platform-versions` plugin rather than from a single hard-coded version tuple.

## Constraint chain

The resolver applies these constraints together:

1. **Gradle → Kotlin Gradle Plugin**
   - Plugin resource: `org/autojs/build/platform/data/gradle-kotlin-compat.properties`
   - Source: the official Gradle compatibility table.
2. **KSP → minimum Android Gradle Plugin**
   - Plugin resource: `org/autojs/build/platform/data/ksp-agp-compat.properties`
   - Source: `MINIMUM_SUPPORTED_AGP_VERSION` in each official `google/ksp` release tag.
3. **Android Gradle Plugin → minimum Gradle**
   - Plugin resource: `org/autojs/build/platform/data/agp-gradle-compat.properties`
   - Source: the official Android Gradle Plugin compatibility table.
4. **Kotlin → minimum R8**
   - Plugin resource: `org/autojs/build/platform/data/kotlin-r8-compat.properties`
   - Source: the official Android Kotlin/D8/R8 compatibility table.

AutoJs6 intentionally carries no same-named files under `gradle/data`, so these bundled resources are the single source used by its build. Other consumers can still provide a same-named `gradle/data` file when they need an explicit local override.

The selected AGP must satisfy both KSP and Gradle. The selected R8 must satisfy the resolved Kotlin family when the selected AGP embeds an older R8 release. When the selected AGP version line is already newer than the Kotlin-required R8 line, the build uses AGP's bundled R8 instead of adding an external `com.android.tools:r8` classpath entry.

For example, Kotlin `2.3.x` requires R8 `8.13.19` or newer. Selecting AGP `8.10.x` only because it satisfies KSP is insufficient because that AGP line embeds an older R8 version, so the buildscript injects the generated Kotlin-compatible R8 version. Selecting AGP `9.0.x` or newer already provides a newer bundled R8 line, so the external R8 classpath entry is omitted to avoid downgrading AGP's D8/R8 APIs.

## Updating the data

The compatibility data and its updater are owned by [AutoJs6-Gradle-Platform-Versions](https://github.com/SuperMonster003/AutoJs6-Gradle-Platform-Versions). From that repository, developers can run the interactive Windows entry point:

```bat
run-scrapers.bat
```

Cross-platform automation and future scheduled CI can use the non-interactive entries:

```bash
npm --prefix .utils run update-data
npm --prefix .utils run check-data
```

After publishing the updated plugin to Maven Local, update its version in `settings.gradle.kts`. AutoJs6's own `.utils/run-scrapers.mjs` now contains only tasks that update AutoJs6-specific files.

A missing compatibility family is treated as an error instead of silently choosing a nearest older toolchain.
