#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

function replaceOnce(content, before, after, label) {
    const first = content.indexOf(before);
    if (first < 0) throw new Error(`Missing patch anchor: ${label}`);
    if (content.indexOf(before, first + before.length) >= 0) {
        throw new Error(`Patch anchor is not unique: ${label}`);
    }
    return content.slice(0, first) + after + content.slice(first + before.length);
}

function patchFile(relativePath, transform) {
    const filePath = path.join(root, relativePath);
    const before = fs.readFileSync(filePath, 'utf8');
    const after = transform(before);
    if (after === before) {
        console.log(`unchanged: ${relativePath}`);
        return;
    }
    fs.writeFileSync(filePath, after, 'utf8');
    console.log(`updated: ${relativePath}`);
}

patchFile('.utils/run-scrapers.mjs', (content) => replaceOnce(
    content,
    "    'scrape-and-inject-ksp-releases-map.mjs',\n",
    "    'scrape-and-inject-ksp-releases-map.mjs',\n    'scrape-and-inject-ksp-agp-compatibility-map.mjs',\n",
    'register KSP/AGP compatibility scraper',
));

patchFile('settings.gradle.kts', (original) => {
    let content = original;

    content = replaceOnce(
        content,
        `    val kspReleaseProps by lazy { gradleDataProps("ksp-releases") }\n`,
        `    val kspReleaseProps by lazy { gradleDataProps("ksp-releases") }\n    val kspAgpCompatProps by lazy { gradleDataProps("ksp-agp-compat") }\n`,
        'load generated KSP/AGP compatibility data',
    );

    content = replaceOnce(
        content,
        `        private val toml: Map<String, String> = run {\n`,
        `        private fun expectedKspPluginVersion(): String? {
            val kspPlugin = config.libs.filterIsInstance<Plugin>().find {
                it.id == "com.google.devtools.ksp"
            } ?: return null

            if (!kspPlugin.version.startsWith("\${identifier.auto}:")) {
                return kspPlugin.version
            }
            return pluginVersionAutomatorMap.getValue("ksp")
                .invoke(kspReleaseProps)["version"]
        }

        private fun minimumAgpVersionForKsp(kspVersion: String): String? =
            kspAgpCompatProps[kspVersion]

        private fun canUseAgpWithCurrentGradle(agpVersion: String): Boolean {
            val minimumGradleVersion = Version(agpGradleCompatProps, agpVersion).bestMatchingValue
                ?: return false
            return gradle.gradleVersion.toGradleVersion() >= minimumGradleVersion.toGradleVersion()
        }

        private fun getAgpReleasedVersionAtLeast(minimumAgpVersion: String): String? {
            return agpReleases
                .asSequence()
                .filterNot { it.contains("-") }
                .filter { utils.compareVersionStrings(it, minimumAgpVersion) >= 0 }
                .sortedWith(utils::compareVersionStrings)
                .firstOrNull(::canUseAgpWithCurrentGradle)
        }

        private fun refineAgpVersionForKsp(
            agpVersion: String,
            isUserSpecified: Boolean,
        ): Pair<String, String> {
            val kspVersion = expectedKspPluginVersion() ?: return agpVersion to ""
            val minimumAgpVersion = minimumAgpVersionForKsp(kspVersion) ?: return agpVersion to ""
            if (utils.compareVersionStrings(agpVersion, minimumAgpVersion) >= 0) {
                return agpVersion to ""
            }

            if (isUserSpecified) {
                throw Exception(
                    "User specified AGP version $agpVersion is incompatible with KSP $kspVersion. " +
                            "KSP $kspVersion requires AGP $minimumAgpVersion or higher."
                )
            }

            val upgradedAgpVersion = getAgpReleasedVersionAtLeast(minimumAgpVersion)
                ?: throw Exception(
                    "Failed to determine an AGP version compatible with KSP $kspVersion. " +
                            "KSP $kspVersion requires AGP $minimumAgpVersion or higher, " +
                            "but current Gradle \${gradle.gradleVersion} cannot use any known compatible stable AGP release."
                )

            return upgradedAgpVersion to identifier.upgradedSuffix
        }

        private val toml: Map<String, String> = run {
`,
        'insert KSP/AGP compatibility refinement',
    );

    content = replaceOnce(
        content,
        `        val classpath = config.libs.filterIsInstance<Classpath>().map { lib ->
            var suffix = ""
            val version: String = when {
`,
        `        val classpath = config.libs.filterIsInstance<Classpath>().map { lib ->
            var suffix = ""
            val candidateVersion: String = when {
`,
        'name the initially selected classpath version',
    );

    content = replaceOnce(
        content,
        `            }\n            if (lib.id == "com.android.tools.build:gradle") {\n`,
        `            }
            val (version, compatibilitySuffix) = when (lib.id) {
                "com.android.tools.build:gradle" -> refineAgpVersionForKsp(
                    candidateVersion,
                    isUserSpecified = !lib.version.startsWith("\${identifier.auto}:"),
                )
                else -> candidateVersion to ""
            }
            suffix += compatibilitySuffix
            if (lib.id == "com.android.tools.build:gradle") {
`,
        'refine selected AGP against the resolved KSP version',
    );

    return content;
});

patchFile('tools/editor-refactor/check-editor-boundaries.mjs', (content) => replaceOnce(
    content,
    'assertIncludes(runScriptTextProvider, "TmpScriptFiles.create(context)");',
    'assertIncludes(runScriptTextProvider, "TmpScriptFiles.create(context, extensionWithDotProvider())");',
    'align temp-script boundary check with extension-aware API',
));
