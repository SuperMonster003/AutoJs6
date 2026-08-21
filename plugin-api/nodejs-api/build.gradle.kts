plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "org.autojs.plugin.nodejs.api"

    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        consumerProguardFiles("consumer-rules.pro")
    }

    lint {
        targetSdk = versions.sdkVersionTarget
        abortOnError = false
    }

    buildFeatures {
        aidl = true
        buildConfig = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// The Node.js runtime plugin repository owns this contract; the sources here
// are a byte-for-byte mirror (modulo line endings) consumed by the host build.
// Manual-only guard, mirroring the sample guard in app/node-examples.gradle.kts:
//   gradlew :plugin-api:nodejs-api:verifyNodeJsApiMirror
//     [-Pautojs.nodejs.plugin.root=<plugin repo>]
tasks.register("verifyNodeJsApiMirror") {
    group = "verification"
    description = "Verifies this module mirrors the Node.js plugin repository's nodejs-api sources."

    doLast {
        val pluginRoot = (
            providers.gradleProperty("autojs.nodejs.pluginProjectDir")
                .orElse(providers.gradleProperty("autojs.nodejs.plugin.root"))
                .orNull
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let(rootProject::file)
                ?: sequenceOf("AutoJs6-Plugin-NodeJs-Runtime", "autojs6-plugin-nodejs")
                    .map(rootProject.projectDir.parentFile::resolve)
                    .firstOrNull(File::isDirectory)
                ?: throw GradleException(
                    "Node.js plugin repository not found next to this repo. " +
                        "Use -Pautojs.nodejs.plugin.root=<path>.",
                )
            ).canonicalFile
        val pluginSrc = pluginRoot.resolve("plugin-api/nodejs-api/src")
        if (!pluginSrc.isDirectory) {
            throw GradleException("Missing plugin contract sources: ${pluginSrc.absolutePath}")
        }
        val hostSrc = layout.projectDirectory.dir("src").asFile.canonicalFile

        fun inventory(root: File): Map<String, String> = root.walkTopDown()
            .filter(File::isFile)
            .associate { file ->
                file.relativeTo(root).invariantSeparatorsPath to
                    file.readText(Charsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n')
            }
            .toSortedMap()

        val host = inventory(hostSrc)
        val plugin = inventory(pluginSrc)
        val missingFromHost = (plugin.keys - host.keys).sorted()
        val extraInHost = (host.keys - plugin.keys).sorted()
        val contentMismatches = host.keys.intersect(plugin.keys)
            .filter { host[it] != plugin[it] }
            .sorted()
        if (missingFromHost.isNotEmpty() || extraInHost.isNotEmpty() || contentMismatches.isNotEmpty()) {
            throw GradleException(
                "Node.js API contract mirror is out of sync with ${pluginSrc.absolutePath}: " +
                    "missingFromHost=$missingFromHost, extraInHost=$extraInHost, " +
                    "contentMismatches=$contentMismatches. " +
                    "The plugin repository is the source of truth; re-sync this module from it.",
            )
        }
        logger.lifecycle("Verified ${host.size} mirrored Node.js API contract files against ${pluginSrc.absolutePath}.")
    }
}
