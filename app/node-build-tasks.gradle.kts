import java.time.ZonedDateTime

private fun nodeBuildExtraName(name: String): String = "nodeJsRuntimeConfig.$name"

@Suppress("UNCHECKED_CAST")
private fun <T> nodeBuildExtra(name: String): T =
    extensions.extraProperties.get(nodeBuildExtraName(name)) as T

private val taskNameAssembleInrtFullNodeRelease = nodeBuildExtra<String>("taskNameAssembleInrtFullNodeRelease")
private val isNodeFuzzerSeedReplayRequested = nodeBuildExtra<Boolean>("isNodeFuzzerSeedReplayRequested")
private val nodeJsReleaseFlavor = nodeBuildExtra<String>("nodeJsReleaseFlavor")
private val nodeJsReleaseExpectedAbis = nodeBuildExtra<List<String>>("nodeJsReleaseExpectedAbis")
private val effectiveNodeJsRuntimeBackend = nodeBuildExtra<String>("effectiveNodeJsRuntimeBackend")
private val isNodeJsNativeRuntimeRequested = nodeBuildExtra<Boolean>("isNodeJsNativeRuntimeRequested")
private val isNodeJsHostBridgeBuildRequested = nodeBuildExtra<Boolean>("isNodeJsHostBridgeBuildRequested")
private val nodeJsTestDeviceProfile = nodeBuildExtra<String?>("nodeJsTestDeviceProfile")
private val nodeJsFuzzerBudget = nodeBuildExtra<String>("nodeJsFuzzerBudget")
private val nodeJsFuzzerSeedFile = nodeBuildExtra<String?>("nodeJsFuzzerSeedFile")
private val nodeJsFuzzerReplayRunnerClass = nodeBuildExtra<String?>("nodeJsFuzzerReplayRunnerClass")
private val nodeJsFuzzerReplaySeed = nodeBuildExtra<String?>("nodeJsFuzzerReplaySeed")
private val nodeJsTestDeviceProfileClasses = nodeBuildExtra<List<String>>("nodeJsTestDeviceProfileClasses")
private val nodeJsApkFileExtension = "apk"

tasks.register(taskNameAssembleInrtFullNodeRelease) {
    group = "build"
    description = "Builds a full Embedded Node.js inrt release APK for template/runtime testing without updating assets-app/template.apk."

    dependsOn("packageInrtRelease")
    outputs.file(layout.buildDirectory.file("outputs/apk/inrt/full-node-template/template-node-full.apk"))
    outputs.file(layout.buildDirectory.file("reports/embedded-node/inrt-full-node-template-assemble.txt"))
    outputs.upToDateWhen { false }

    doFirst {
        if (nodeJsReleaseFlavor == "rhinoOnly" || !isNodeJsNativeRuntimeRequested || !isNodeJsHostBridgeBuildRequested) {
            throw GradleException(
                "$taskNameAssembleInrtFullNodeRelease requires Embedded Node native runtime packaging. " +
                        "Do not pass -Pautojs.nodejs.releaseFlavor=rhinoOnly or -Pautojs.nodejs.hostBridge.enabled=false."
            )
        }
    }

    doLast {
        val sourceApk = file("build/outputs/apk/inrt/release").listFiles { candidate ->
            candidate.isFile &&
                    candidate.extension.equals(nodeJsApkFileExtension, ignoreCase = true) &&
                    candidate.name.contains("universal", ignoreCase = true)
        }?.maxByOrNull(File::lastModified)
            ?: throw GradleException("inrtRelease universal APK was not produced.")
        val outputApk = layout.buildDirectory
            .file("outputs/apk/inrt/full-node-template/template-node-full.apk")
            .get()
            .asFile
        val reportFile = layout.buildDirectory
            .file("reports/embedded-node/inrt-full-node-template-assemble.txt")
            .get()
            .asFile
        outputApk.parentFile.mkdirs()
        reportFile.parentFile.mkdirs()
        sourceApk.copyTo(outputApk, overwrite = true)
        reportFile.writeText(
            buildString {
                appendLine("Embedded Node.js full inrt template APK assemble report")
                appendLine("Generated at: ${ZonedDateTime.now()}")
                appendLine()
                appendLine("Source:")
                appendLine("  APK: ${sourceApk.absolutePath}")
                appendLine("  bytes: ${sourceApk.length()}")
                appendLine()
                appendLine("Output:")
                appendLine("  APK: ${outputApk.absolutePath}")
                appendLine("  bytes: ${outputApk.length()}")
                appendLine()
                appendLine("Node packaging:")
                appendLine("  autojs.nodejs.releaseFlavor=$nodeJsReleaseFlavor")
                appendLine("  autojs.nodejs.releaseAbis=${if (nodeJsReleaseExpectedAbis.isEmpty()) "none" else nodeJsReleaseExpectedAbis.joinToString()}")
                appendLine("  autojs.nodejs.backend=$effectiveNodeJsRuntimeBackend")
                appendLine("  native runtime requested=$isNodeJsNativeRuntimeRequested")
                appendLine("  host bridge build requested=$isNodeJsHostBridgeBuildRequested")
                appendLine("  updates assets-app/template.apk=false")
            }
        )
        logger.lifecycle("Embedded Node.js full inrt template APK: ${outputApk.absolutePath}")
    }
}

tasks.register("printNodeTestDeviceProfile") {
    group = "verification"
    description = "Prints the Embedded Node instrumentation classes selected by autojs.nodejs.test.deviceProfile."

    doLast {
        println("Embedded Node test device profile: ${nodeJsTestDeviceProfile ?: "unset"}")
        println("Embedded Node fuzzer budget: $nodeJsFuzzerBudget")
        if (nodeJsFuzzerSeedFile != null) {
            println("Embedded Node fuzzer seed file: $nodeJsFuzzerSeedFile")
            println("Embedded Node fuzzer replay class: ${nodeJsFuzzerReplayRunnerClass ?: "unavailable"}")
            println("Embedded Node fuzzer replay seed: ${nodeJsFuzzerReplaySeed ?: "unavailable"}")
        }
        if (nodeJsTestDeviceProfileClasses.isEmpty()) {
            println("Embedded Node selected instrumentation classes: none; pass -Pautojs.nodejs.test.deviceProfile=sony|emulator|smoke|accessibility|screen_capture|full")
        } else {
            println("Embedded Node selected instrumentation classes:")
            nodeJsTestDeviceProfileClasses.forEach { className ->
                println("  $className")
            }
        }
    }
}

tasks.register("replayNodeFuzzerSeed") {
    group = "verification"
    description = "Replays a saved Embedded Node fuzzer seed with connected Android instrumentation."

    dependsOn("connectedAppDebugAndroidTest")

    doFirst {
        val seedFile = nodeJsFuzzerSeedFile
            ?: throw GradleException("Pass -Pautojs.nodejs.fuzzerSeedFile=<build/reports/nodejs/fuzz-seeds/*.json>.")
        if (nodeJsFuzzerReplayRunnerClass.isNullOrBlank() || nodeJsFuzzerReplaySeed.isNullOrBlank()) {
            throw GradleException("Fuzzer seed file '$seedFile' is missing replayClass/baseSeed fields.")
        }
        println("Replaying Embedded Node fuzzer seed: $seedFile")
        println("  class: $nodeJsFuzzerReplayRunnerClass")
        println("  budget: $nodeJsFuzzerBudget")
        println("  seed: $nodeJsFuzzerReplaySeed")
    }
}

if (isNodeFuzzerSeedReplayRequested) {
    tasks.matching { it.name == "connectedAppDebugAndroidTest" }.configureEach {
        doFirst {
            val seedFile = nodeJsFuzzerSeedFile
                ?: throw GradleException("Pass -Pautojs.nodejs.fuzzerSeedFile=<build/reports/nodejs/fuzz-seeds/*.json>.")
            if (nodeJsFuzzerReplayRunnerClass.isNullOrBlank() || nodeJsFuzzerReplaySeed.isNullOrBlank()) {
                throw GradleException("Fuzzer seed file '$seedFile' is missing replayClass/baseSeed fields.")
            }
        }
    }
}
