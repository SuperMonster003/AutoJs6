# Build Tool Version Resolution

AutoJs6 resolves its Android build-tool versions from generated compatibility data rather than from a single hard-coded version tuple.

## Constraint chain

The resolver applies these constraints together:

1. **Gradle → Kotlin Gradle Plugin**
   - Data: `gradle/data/gradle-kotlin-compat.properties`
   - Source: the official Gradle compatibility table.
2. **KSP → minimum Android Gradle Plugin**
   - Data: `gradle/data/ksp-agp-compat.properties`
   - Source: `MINIMUM_SUPPORTED_AGP_VERSION` in each official `google/ksp` release tag.
3. **Android Gradle Plugin → minimum Gradle**
   - Data: `gradle/data/agp-gradle-compat.properties`
   - Source: the official Android Gradle Plugin compatibility table.
4. **Kotlin → minimum R8**
   - Data: `gradle/data/kotlin-r8-compat.properties`
   - Source: the official Android Kotlin/D8/R8 compatibility table.

The selected AGP must satisfy both KSP and Gradle. The selected R8 must satisfy the resolved Kotlin family even when the selected AGP embeds an older R8 release.

For example, Kotlin `2.3.x` requires R8 `8.13.19` or newer. Selecting AGP `8.10.x` only because it satisfies KSP is insufficient because that AGP line embeds an older R8 version. The buildscript therefore forces the generated Kotlin-compatible R8 version.

## Updating the data

Run the scraper suite from `.utils`:

```bash
node run-scrapers.mjs
```

The relevant scrapers are:

```text
scrape-and-inject-gradle-kotlin-compatibility-map.mjs
scrape-and-inject-kotlin-r8-compatibility-map.mjs
scrape-and-inject-agp-gradle-compatibility-map.mjs
scrape-and-inject-ksp-agp-compatibility-map.mjs
```

A missing compatibility family is treated as an error instead of silently choosing a nearest older toolchain.