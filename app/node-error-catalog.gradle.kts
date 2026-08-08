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

private fun nodeErrorCatalogConfiguredFile(name: String): File? =
    if (extensions.extraProperties.has("nodeJsRuntimeConfig.$name")) {
        extensions.extraProperties.get("nodeJsRuntimeConfig.$name") as? File
    } else {
        null
    }

val nodeErrorCatalogRegistryFile =
    projectDir.resolve("src/main/java/org/autojs/autojs/engine/NodeRuntimeErrorCodes.kt")
val nodeErrorCatalogCatalogFile = rootProject.projectDir.resolve("docs/nodejs/ERROR_CATALOG.md")
val nodeErrorCatalogPluginProjectDir = nodeErrorCatalogConfiguredFile("nodeJsPluginProjectDir")
val nodeErrorCatalogPluginCppDir = nodeErrorCatalogConfiguredFile("nodeJsPluginCppDir")
    ?: nodeErrorCatalogPluginProjectDir?.resolve("app/src/main/cpp")
val nodeErrorCatalogConfiguredPluginRoots = listOfNotNull(
    nodeErrorCatalogPluginProjectDir?.resolve("app/src/main/java"),
    nodeErrorCatalogPluginCppDir,
)
val nodeErrorCatalogRuntimeSourceRoots = buildList {
    add(projectDir.resolve("src/main/java/org/autojs/autojs/engine"))
    if (nodeErrorCatalogConfiguredPluginRoots.isNotEmpty()) {
        addAll(nodeErrorCatalogConfiguredPluginRoots)
    } else {
        add(projectDir.resolve("src/main/cpp"))
    }
}

val verifyNodeErrorCatalog = tasks.register("verifyNodeErrorCatalog") {
    group = "verification"
    description = "Verifies docs/nodejs/ERROR_CATALOG.md against Embedded Node runtime error codes."

    val reportFile = layout.buildDirectory.file("reports/nodejs/error-catalog.json")

    inputs.file(nodeErrorCatalogRegistryFile)
    inputs.file(nodeErrorCatalogCatalogFile)
    nodeErrorCatalogRuntimeSourceRoots.filter(File::isDirectory).forEach { inputs.dir(it) }
    outputs.file(reportFile)

    doLast {
        if (!nodeErrorCatalogCatalogFile.isFile) {
            throw GradleException(
                "Missing Node error catalog: ${nodeErrorCatalogCatalogFile.relativeTo(rootProject.projectDir)}"
            )
        }
        val missingConfiguredPluginRoots = nodeErrorCatalogConfiguredPluginRoots.filterNot(File::isDirectory)
        if (missingConfiguredPluginRoots.isNotEmpty()) {
            throw GradleException(
                "Configured Node runtime plugin source directories are missing: " +
                        missingConfiguredPluginRoots.joinToString { it.absolutePath }
            )
        }

        val registryCodes = extractNodeErrorCodes(nodeErrorCatalogRegistryFile.readText())
        val runtimeCodes = nodeErrorCatalogRuntimeSourceRoots
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
        val catalogCodes = extractCatalogHeadings(nodeErrorCatalogCatalogFile.readText()).toSortedSet()
        val missing = (requiredCodes - catalogCodes).toSortedSet()
        val undocumentedRuntime = (runtimeCodes - registryCodes).toSortedSet()
        val stale = (catalogCodes - requiredCodes).toSortedSet()

        val report = mapOf(
            "schema" to "autojs6-node-error-catalog-v1",
            "registryFile" to nodeErrorCatalogRegistryFile.relativeTo(projectDir).invariantSeparatorsPath,
            "catalogFile" to nodeErrorCatalogCatalogFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
            "runtimeSourceRoots" to nodeErrorCatalogRuntimeSourceRoots
                .filter(File::isDirectory)
                .map { root ->
                    root.relativeToOrNull(rootProject.projectDir)?.invariantSeparatorsPath
                        ?: root.invariantSeparatorsPath
                },
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
