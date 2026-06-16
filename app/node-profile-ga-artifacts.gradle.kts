import java.io.File
import java.security.MessageDigest
import java.time.ZonedDateTime

private data class NodeProfileGaArtifact(
    val source: File?,
    val targetPath: String,
    val required: Boolean = true,
)

private fun nodeProfileGaArtifactSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun nodeProfileGaArtifactLatestFile(dir: File, pattern: Regex): File? =
    dir.takeIf { it.isDirectory }
        ?.listFiles { file -> file.isFile && pattern.matches(file.name) }
        ?.maxByOrNull { it.lastModified() }

private fun nodeProfileGaArtifactsGit(vararg args: String): String {
    return try {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0) output else "unavailable"
    } catch (_: Exception) {
        "unavailable"
    }
}

val collectNodeProfileGaArtifacts = tasks.register("collectNodeProfileGaArtifacts") {
    group = "verification"
    description = "Collects Node Profile v1 GA evidence into one archiveable report directory."

    val outputDirProvider = rootProject.layout.buildDirectory.dir("reports/nodejs/node-profile-v1-ga")
    outputs.dir(outputDirProvider)
    outputs.upToDateWhen { false }

    doLast {
        val outputDir = outputDirProvider.get().asFile
        val appReports = layout.buildDirectory.dir("reports").get().asFile
        val docsRoot = rootProject.projectDir.resolve("docs/nodejs")
        val perfDir = appReports.resolve("nodejs/perf")
        val latestPerformanceReport = nodeProfileGaArtifactLatestFile(
            perfDir,
            Regex("""embedded-node-performance-.*\.json"""),
        )
        val latestMemoryReport = nodeProfileGaArtifactLatestFile(
            perfDir,
            Regex("""embedded-node-memory-leak-stress-.*\.json"""),
        )
        val artifacts = listOf(
            NodeProfileGaArtifact(appReports.resolve("nodejs/release-manifest.json"), "manifest/release-manifest.json"),
            NodeProfileGaArtifact(appReports.resolve("nodejs/release-manifest.md"), "manifest/release-manifest.md"),
            NodeProfileGaArtifact(appReports.resolve("nodejs/compatibility-profile.json"), "compatibility/compatibility-profile.json"),
            NodeProfileGaArtifact(appReports.resolve("nodejs/compatibility-profile.md"), "compatibility/compatibility-profile.md"),
            NodeProfileGaArtifact(appReports.resolve("native-node/native-symbol-archive-appDebug.txt"), "native/native-symbol-archive-appDebug.txt"),
            NodeProfileGaArtifact(appReports.resolve("nodejs/abi-size-report.md"), "native/abi-size-report.md"),
            NodeProfileGaArtifact(latestPerformanceReport, "performance/${latestPerformanceReport?.name ?: "embedded-node-performance-missing.json"}"),
            NodeProfileGaArtifact(latestMemoryReport, "performance/${latestMemoryReport?.name ?: "embedded-node-memory-leak-stress-missing.json"}"),
            NodeProfileGaArtifact(appReports.resolve("embedded-node/packaged-smoke-aggregate.txt"), "packaged/packaged-smoke-aggregate.txt"),
            NodeProfileGaArtifact(docsRoot.resolve("reports/phase4/sony-ga-candidate/README.md"), "gate/sony-ga-candidate.md"),
            NodeProfileGaArtifact(docsRoot.resolve("releases/NODE_PROFILE_V1_KNOWN_ISSUES.md"), "docs/NODE_PROFILE_V1_KNOWN_ISSUES.md"),
            NodeProfileGaArtifact(docsRoot.resolve("releases/NODE_PROFILE_V1_STABLE_SURFACE.md"), "docs/NODE_PROFILE_V1_STABLE_SURFACE.md"),
            NodeProfileGaArtifact(docsRoot.resolve("ERROR_CATALOG.md"), "docs/ERROR_CATALOG.md"),
            NodeProfileGaArtifact(docsRoot.resolve("releases/NODE_PROFILE_V1_GA.md"), "docs/NODE_PROFILE_V1_GA.md"),
            NodeProfileGaArtifact(docsRoot.resolve("releases/NODE_PROFILE_V1_RELEASE_IDENTITY.md"), "docs/NODE_PROFILE_V1_RELEASE_IDENTITY.md"),
            NodeProfileGaArtifact(docsRoot.resolve("MAINTENANCE_PLAN.md"), "docs/MAINTENANCE_PLAN.md"),
        )
        val missing = artifacts.filter { it.required && it.source?.isFile != true }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing Node Profile GA artifact(s): " +
                        missing.joinToString { "${it.targetPath} from ${it.source?.path ?: "unavailable"}" } +
                        ". Generate final reports and device evidence before collecting the GA bundle."
            )
        }

        outputDir.deleteRecursively()
        outputDir.mkdirs()
        val checksumLines = mutableListOf<String>()
        artifacts.forEach { artifact ->
            val source = artifact.source ?: return@forEach
            if (!source.isFile) return@forEach
            val target = outputDir.resolve(artifact.targetPath)
            target.parentFile.mkdirs()
            source.copyTo(target, overwrite = true)
            checksumLines += "${nodeProfileGaArtifactSha256(target)}  ${target.relativeTo(outputDir).invariantSeparatorsPath}"
        }
        outputDir.resolve("SHA256SUMS.txt").writeText(checksumLines.sorted().joinToString(separator = "\n", postfix = "\n"))
        outputDir.resolve("README.md").writeText(
            buildString {
                appendLine("# AutoJs6 Node Profile v1 GA Artifact Bundle")
                appendLine()
                appendLine("- generatedAt: `${ZonedDateTime.now()}`")
                appendLine("- source commit: `${nodeProfileGaArtifactsGit("rev-parse", "--short", "HEAD")}`")
                appendLine("- source status: `${nodeProfileGaArtifactsGit("status", "--short").ifBlank { "clean" }}`")
                appendLine()
                appendLine("This directory is produced by `:app:collectNodeProfileGaArtifacts`.")
                appendLine("Regenerate final reports after the approved release identity, version, commit, and tag are fixed.")
            }
        )
        logger.lifecycle("Node Profile v1 GA artifact bundle written to ${outputDir.absolutePath}")
        logger.lifecycle("Node Profile v1 GA artifact checksums written to ${outputDir.resolve("SHA256SUMS.txt").absolutePath}")
    }
}
