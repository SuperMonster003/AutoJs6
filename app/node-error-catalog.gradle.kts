import groovy.json.JsonOutput
import java.io.File

private val nodeErrorCodePattern = Regex("""ERR_AUTOJS6_[A-Z0-9_]+""")

private fun extractNodeErrorCodes(text: String): Set<String> =
    nodeErrorCodePattern.findAll(text).map { it.value }.toSet()

private fun extractCatalogHeadings(text: String): Set<String> =
    Regex("""(?m)^##\s+(ERR_AUTOJS6_[A-Z0-9_]+)\s*$""")
        .findAll(text)
        .map { it.groupValues[1] }
        .toSet()

val verifyNodeErrorCatalog = tasks.register("verifyNodeErrorCatalog") {
    group = "verification"
    description = "Verifies docs/nodejs/ERROR_CATALOG.md against Embedded Node runtime error codes."

    val reportFile = layout.buildDirectory.file("reports/nodejs/error-catalog.json")

    outputs.file(reportFile)

    doLast {
        val registryFile = projectDir.resolve("src/main/java/org/autojs/autojs/engine/NodeRuntimeErrorCodes.kt")
        val runtimeSourceRoots = listOf(
            projectDir.resolve("src/main/java/org/autojs/autojs/engine"),
            projectDir.resolve("src/main/cpp"),
        )
        val catalogFile = rootProject.projectDir.resolve("docs/nodejs/ERROR_CATALOG.md")

        if (!catalogFile.isFile) {
            throw GradleException("Missing Node error catalog: ${catalogFile.relativeTo(rootProject.projectDir)}")
        }

        val registryCodes = extractNodeErrorCodes(registryFile.readText())
        val runtimeCodes = runtimeSourceRoots
            .filter(File::isDirectory)
            .flatMap { root ->
                root.walkTopDown()
                    .filter(File::isFile)
                    .filter { it.extension in setOf("kt", "java", "cpp", "cc", "c", "h", "hpp") }
                    .flatMap { file -> extractNodeErrorCodes(file.readText()).asSequence() }
                    .toList()
            }
            .toSet()

        val requiredCodes = (registryCodes + runtimeCodes).toSortedSet()
        val catalogCodes = extractCatalogHeadings(catalogFile.readText()).toSortedSet()
        val missing = (requiredCodes - catalogCodes).toSortedSet()
        val undocumentedRuntime = (runtimeCodes - registryCodes).toSortedSet()
        val stale = (catalogCodes - requiredCodes).toSortedSet()

        val report = mapOf(
            "schema" to "autojs6-node-error-catalog-v1",
            "registryFile" to registryFile.relativeTo(projectDir).invariantSeparatorsPath,
            "catalogFile" to catalogFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
            "registryCodes" to registryCodes.sorted(),
            "runtimeCodes" to runtimeCodes.sorted(),
            "catalogCodes" to catalogCodes.sorted(),
            "missingCatalogEntries" to missing.toList(),
            "runtimeCodesMissingRegistryConstants" to undocumentedRuntime.toList(),
            "staleCatalogEntries" to stale.toList(),
        )
        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)))
        }

        if (missing.isNotEmpty() || undocumentedRuntime.isNotEmpty() || stale.isNotEmpty()) {
            val messages = buildList {
                if (missing.isNotEmpty()) {
                    add("missing catalog entries: ${missing.joinToString()}")
                }
                if (undocumentedRuntime.isNotEmpty()) {
                    add("runtime codes missing NodeRuntimeErrorCodes constants: ${undocumentedRuntime.joinToString()}")
                }
                if (stale.isNotEmpty()) {
                    add("catalog entries not present in runtime sources: ${stale.joinToString()}")
                }
            }
            throw GradleException("Node error catalog verification failed; ${messages.joinToString("; ")}")
        }

        logger.lifecycle("Embedded Node error catalog covers ${requiredCodes.size} runtime error codes.")
        logger.lifecycle("Embedded Node error catalog report written to ${reportFile.get().asFile.absolutePath}")
    }
}

tasks.matching {
    it.name in setOf(
        "verifyNodePrGate",
        "verifyNodeNightlyGate",
        "verifyNodeReleaseGate",
        "verifyEmbeddedNodeReleaseCandidate",
    )
}.configureEach {
    dependsOn(verifyNodeErrorCatalog)
}
