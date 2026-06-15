import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.time.ZonedDateTime

private data class NodeResolveTraceEvent(
    val event: String,
    val fields: Map<String, String>,
    val raw: String,
)

private data class NodeResolveTraceGroup(
    val request: String,
    val from: String,
    val events: List<NodeResolveTraceEvent>,
)

private data class NodeResolveVisualizerReport(
    val json: Map<String, Any?>,
    val compactText: String,
    val verboseText: String,
    val jsonText: String,
)

private val nodeResolveTraceFieldPattern = Regex("""([A-Za-z0-9_]+)=("(?:\\.|[^"])*"|[^\s]+)""")

private fun nodeResolveJson(value: Any?): String =
    JsonOutput.prettyPrint(JsonOutput.toJson(value))

private fun nodeResolveUnquote(value: String): String {
    val trimmed = value.trim()
    if (!trimmed.startsWith("\"")) {
        return trimmed
    }
    return runCatching { JsonSlurper().parseText(trimmed).toString() }
        .getOrElse { trimmed.removeSurrounding("\"") }
}

private fun nodeResolveNormalizeMode(value: String): String {
    val mode = value.trim().lowercase().ifBlank { "verbose" }
    if (mode !in setOf("compact", "verbose", "json")) {
        throw GradleException("Unsupported autojs.nodejs.resolve.mode='$value'. Expected compact, verbose, or json.")
    }
    return mode
}

private fun nodeResolveParseTraceLine(line: String): NodeResolveTraceEvent? {
    val prefix = "[resolve] "
    if (!line.contains(prefix)) {
        return null
    }
    val payload = line.substringAfter(prefix).trim()
    val firstField = Regex("""\s[A-Za-z0-9_]+=""").find(payload)
    val event = firstField
        ?.let { payload.substring(0, it.range.first).trim() }
        ?: payload
    val fieldsText = firstField
        ?.let { payload.substring(it.range.first + 1) }
        .orEmpty()
    val fields = nodeResolveTraceFieldPattern
        .findAll(fieldsText)
        .associate { match -> match.groupValues[1] to nodeResolveUnquote(match.groupValues[2]) }
    return NodeResolveTraceEvent(event, fields, line.substring(line.indexOf(prefix)))
}

private fun nodeResolveParseTrace(text: String): List<NodeResolveTraceGroup> {
    val groups = mutableListOf<MutableList<NodeResolveTraceEvent>>()
    text.lineSequence()
        .mapNotNull(::nodeResolveParseTraceLine)
        .forEach { event ->
            if (event.event == "request" || groups.isEmpty()) {
                groups += mutableListOf(event)
            } else {
                groups.last() += event
            }
        }
    return groups.map { events ->
        val request = events.firstOrNull { it.event == "request" }?.fields?.get("request").orEmpty()
        val from = events.firstOrNull { it.event == "request" }?.fields?.get("from").orEmpty()
        NodeResolveTraceGroup(request, from, events)
    }
}

private fun nodeResolveNormalizePath(value: String): String =
    value.replace('\\', '/').trimEnd('/')

private fun nodeResolvePathMatches(actual: String, expected: String): Boolean {
    if (expected.isBlank()) {
        return true
    }
    val normalizedActual = nodeResolveNormalizePath(actual)
    val normalizedExpected = nodeResolveNormalizePath(expected)
    return normalizedActual == normalizedExpected || normalizedActual.endsWith("/$normalizedExpected")
}

private fun nodeResolveSelectGroups(
    groups: List<NodeResolveTraceGroup>,
    request: String,
    from: String,
): List<NodeResolveTraceGroup> {
    val byRequest = groups.filter { request.isBlank() || it.request == request }
    val byFrom = byRequest.filter { nodeResolvePathMatches(it.from, from) }
    return when {
        byFrom.isNotEmpty() -> byFrom
        byRequest.isNotEmpty() -> byRequest
        request.isBlank() && groups.isNotEmpty() -> groups
        else -> emptyList()
    }
}

private fun nodeResolveDistinct(values: Iterable<String>): List<String> =
    values.map(String::trim).filter(String::isNotBlank).distinct()

private fun nodeResolveDiagnosis(
    request: String,
    finalErrors: List<Map<String, String?>>,
    nullBlocking: List<String>,
    packageBranches: List<String>,
): String {
    val codes = finalErrors.flatMap { listOfNotNull(it["autojs6Code"], it["code"]) }.filter(String::isNotBlank)
    return when {
        "ERR_AUTOJS6_BUILTIN_DISABLED" in codes -> "disabled-builtin"
        codes.any { it.startsWith("ERR_AUTOJS6_FS_") } -> "scoped-fs-policy"
        "ERR_PACKAGE_PATH_NOT_EXPORTED" in codes -> "package-exports"
        "ERR_PACKAGE_IMPORT_NOT_DEFINED" in codes -> "package-imports"
        "ERR_AUTOJS6_MODULE_NOT_FOUND" in codes || "MODULE_NOT_FOUND" in codes -> "module-not-found"
        nullBlocking.isNotEmpty() -> "package-null-blocking"
        packageBranches.isNotEmpty() -> "package-branch-selected"
        request.isNotBlank() -> "unclassified"
        else -> "no-request-selected"
    }
}

private fun nodeResolveRecommendations(diagnosis: String): List<String> = when (diagnosis) {
    "disabled-builtin" -> listOf("Use the Safe Node Profile replacement module, or remove the dependency on the disabled builtin.")
    "scoped-fs-policy" -> listOf("Keep module specifiers and package targets inside the working directory; remove absolute paths, parent escapes, and escaping symlinks.")
    "package-exports" -> listOf("Check package.json exports; import only exported subpaths or update the dependency package entry.")
    "package-imports" -> listOf("Check package.json imports aliases; define the alias or use a relative CommonJS path.")
    "module-not-found" -> listOf("Verify the dependency is vendored under node_modules and that package main/exports/imports targets point to existing files.")
    "package-null-blocking" -> listOf("A package target explicitly blocked this path with null; choose a public export or change package metadata.")
    "package-branch-selected" -> listOf("Review the selected package branch and final error; the resolver reached package metadata but did not complete successfully.")
    "no-request-selected" -> listOf("Pass --request and --from to focus the report, or omit both to inspect all trace groups.")
    else -> listOf("Inspect finalErrors and rawTrace; attach the JSON report when filing a bug.")
}

private fun nodeResolveAppendList(
    builder: StringBuilder,
    title: String,
    values: List<String>,
    emptyText: String = "none observed",
) {
    builder.appendLine(title)
    if (values.isEmpty()) {
        builder.appendLine("  $emptyText")
        builder.appendLine()
        return
    }
    values.forEach { builder.appendLine("  $it") }
    builder.appendLine()
}

private fun nodeResolveExplainGroups(
    request: String,
    from: String,
    traceFile: File?,
    traceText: String?,
): NodeResolveVisualizerReport {
    val groups = traceText?.let(::nodeResolveParseTrace).orEmpty()
    val selectedGroups = nodeResolveSelectGroups(groups, request, from)
    val selectedEvents = selectedGroups.flatMap { it.events }
    val searchPaths = nodeResolveDistinct(
        selectedEvents
            .filter { it.event == "node_modules search" }
            .flatMap { it.fields["paths"].orEmpty().split('|') }
    )
    val packageJsonFiles = nodeResolveDistinct(
        selectedEvents
            .filter { it.event == "package.json found" || it.event == "package scope" }
            .mapNotNull { it.fields["file"] ?: it.fields["packageDir"] }
    )
    val packageBranches = selectedEvents
        .filter {
            it.event.startsWith("exports") ||
                    it.event.startsWith("imports") ||
                    it.event == "main matched" ||
                    it.event == "index matched" ||
                    it.event == "self-reference matched"
        }
        .map { it.raw }
    val nullBlocking = selectedEvents
        .filter { it.event.contains("blocked") || it.fields["target"] == "null" }
        .map { it.raw }
    val finalErrors = selectedEvents
        .filter { it.event == "error" }
        .map { event ->
            linkedMapOf<String, String?>(
                "code" to event.fields["code"],
                "autojs6Code" to event.fields["autojs6Code"],
                "message" to event.fields["message"],
                "raw" to event.raw,
            )
        }
    val rawTrace = selectedEvents.map { it.raw }
    val traceAvailable = traceText != null
    val diagnosis = nodeResolveDiagnosis(request, finalErrors, nullBlocking, packageBranches)
    val recommendations = nodeResolveRecommendations(diagnosis)
    val report = linkedMapOf<String, Any?>(
        "schema" to "autojs6-node-resolver-visualizer-v1",
        "generatedAt" to ZonedDateTime.now().toString(),
        "request" to request,
        "from" to from,
        "traceFile" to traceFile?.absolutePath,
        "traceAvailable" to traceAvailable,
        "traceGroups" to groups.size,
        "selectedGroups" to selectedGroups.size,
        "searchPaths" to searchPaths,
        "packageJsonFieldsRead" to packageJsonFiles,
        "packageBranchesSelected" to packageBranches,
        "nullBlocking" to nullBlocking,
        "finalErrors" to finalErrors,
        "diagnosis" to diagnosis,
        "recommendations" to recommendations,
        "rawTrace" to rawTrace,
    )
    val verboseText = buildString {
        appendLine("AutoJs6 Node Resolver Explanation")
        appendLine("Mode: verbose")
        appendLine("Generated at: ${report["generatedAt"]}")
        appendLine("Request: ${request.ifBlank { "not specified" }}")
        appendLine("From: ${from.ifBlank { "not specified" }}")
        appendLine("Trace file: ${traceFile?.absolutePath ?: "not specified"}")
        appendLine("Trace available: $traceAvailable")
        appendLine("Trace groups: ${groups.size}")
        appendLine("Selected groups: ${selectedGroups.size}")
        appendLine("Diagnosis: $diagnosis")
        appendLine()
        if (!traceAvailable) {
            appendLine("No trace file was available.")
            appendLine("Run the failing script with AUTOJS6_NODE_RESOLVE_TRACE=1, capture stderr/logcat, then pass --trace-file <file>.")
            appendLine()
        }
        nodeResolveAppendList(this, "Search paths:", searchPaths)
        nodeResolveAppendList(this, "package.json fields read:", packageJsonFiles)
        nodeResolveAppendList(this, "Exports/imports/main branch:", packageBranches)
        nodeResolveAppendList(this, "Null blocking:", nullBlocking)
        nodeResolveAppendList(
            this,
            "Final errors:",
            finalErrors.map { "code=${it["code"]} autojs6Code=${it["autojs6Code"]} message=${it["message"]}" },
        )
        nodeResolveAppendList(this, "Recommendations:", recommendations)
        nodeResolveAppendList(this, "Raw trace:", rawTrace, "none selected")
    }
    val compactText = buildString {
        appendLine("AutoJs6 Node Resolver Explanation")
        appendLine("Mode: compact")
        appendLine("Request: ${request.ifBlank { "not specified" }}")
        appendLine("From: ${from.ifBlank { "not specified" }}")
        appendLine("Diagnosis: $diagnosis")
        appendLine()
        nodeResolveAppendList(this, "Recommendations:", recommendations)
        nodeResolveAppendList(
            this,
            "Final errors:",
            finalErrors.map { "code=${it["code"]} autojs6Code=${it["autojs6Code"]} message=${it["message"]}" },
        )
        nodeResolveAppendList(this, "Package branch:", packageBranches.take(3))
        nodeResolveAppendList(this, "Null blocking:", nullBlocking.take(3))
        nodeResolveAppendList(this, "Search paths:", searchPaths.take(5))
    }
    return NodeResolveVisualizerReport(report, compactText, verboseText, nodeResolveJson(report) + "\n")
}

tasks.register("explainNodeResolve") {
    group = "help"
    description = "Formats Embedded Node [resolve] trace output into an actionable resolver explanation report."

    outputs.file(layout.buildDirectory.file("reports/nodejs/resolver/resolve-explanation.json"))
    outputs.file(layout.buildDirectory.file("reports/nodejs/resolver/resolve-explanation.txt"))
    outputs.upToDateWhen { false }

    doLast {
        val requestValue = providers.gradleProperty("autojs.nodejs.resolve.request").orNull.orEmpty()
        val fromValue = providers.gradleProperty("autojs.nodejs.resolve.from").orNull.orEmpty()
        val traceFileValue = providers.gradleProperty("autojs.nodejs.resolve.traceFile").orNull.orEmpty()
        val mode = nodeResolveNormalizeMode(providers.gradleProperty("autojs.nodejs.resolve.mode").orNull.orEmpty())
        val traceFile = traceFileValue.takeIf(String::isNotBlank)?.let { rootProject.file(it) }
        val traceText = traceFile?.takeIf(File::isFile)?.readText()
        val report = nodeResolveExplainGroups(requestValue, fromValue, traceFile, traceText)
        val text = when (mode) {
            "compact" -> report.compactText
            "json" -> report.jsonText
            else -> report.verboseText
        }
        val outputDir = layout.buildDirectory.dir("reports/nodejs/resolver").get().asFile
        outputDir.mkdirs()
        val jsonFile = outputDir.resolve("resolve-explanation.json")
        val textFile = outputDir.resolve("resolve-explanation.txt")
        jsonFile.writeText(nodeResolveJson(report.json) + "\n")
        textFile.writeText(text)
        logger.lifecycle("Node resolver explanation JSON written to ${jsonFile.absolutePath}")
        logger.lifecycle("Node resolver explanation text written to ${textFile.absolutePath}")
    }
}

val verifyNodeResolverVisualizerFixtures = tasks.register("verifyNodeResolverVisualizerFixtures") {
    group = "verification"
    description = "Verifies the Node resolver visualizer parses request, search paths, package fields, null blocking, and final errors."

    doLast {
        val fixtureDir = layout.buildDirectory.dir("tmp/nodejs/resolver-visualizer").get().asFile
        fixtureDir.mkdirs()
        data class ResolverFixture(
            val name: String,
            val request: String,
            val from: String,
            val trace: String,
            val diagnosis: String,
            val needles: List<String>,
        )

        val fixtures = listOf(
            ResolverFixture(
                name = "package-exports",
                request = "trace-pkg/private",
                from = "/work/main.js",
                trace = """
                [resolve] request request="trace-pkg/private" from="/work/main.js"
                [resolve] node_modules search request="trace-pkg/private" from="/work/main.js" paths="/work/node_modules"
                [resolve] package.json found request="trace-pkg/private" packageDir="/work/node_modules/trace-pkg" file="/work/node_modules/trace-pkg/package.json"
                [resolve] exports blocked request="./private" module="trace-pkg/private" target="null" selectedCondition="" conditionKeys="" ignoredConditions=""
                [resolve] error request="trace-pkg/private" from="/work/main.js" code="ERR_PACKAGE_PATH_NOT_EXPORTED" autojs6Code="ERR_AUTOJS6_PACKAGE_PATH_NOT_EXPORTED" message="target=null"
                """.trimIndent(),
                diagnosis = "package-exports",
                needles = listOf("/work/node_modules", "/work/node_modules/trace-pkg/package.json", "exports blocked", "ERR_PACKAGE_PATH_NOT_EXPORTED"),
            ),
            ResolverFixture(
                name = "package-imports",
                request = "#missing",
                from = "/work/main.js",
                trace = """
                [resolve] request request="#missing" from="/work/main.js"
                [resolve] package scope request="#missing" packageDir="/work" file="/work/package.json"
                [resolve] error request="#missing" from="/work/main.js" code="ERR_PACKAGE_IMPORT_NOT_DEFINED" autojs6Code="" message="imports alias not defined"
                """.trimIndent(),
                diagnosis = "package-imports",
                needles = listOf("/work/package.json", "ERR_PACKAGE_IMPORT_NOT_DEFINED", "imports alias"),
            ),
            ResolverFixture(
                name = "builtin-disabled",
                request = "child_process",
                from = "/work/main.js",
                trace = """
                [resolve] request request="child_process" from="/work/main.js"
                [resolve] error request="child_process" from="/work/main.js" code="ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED" autojs6Code="ERR_AUTOJS6_BUILTIN_DISABLED" message="disabled builtin"
                """.trimIndent(),
                diagnosis = "disabled-builtin",
                needles = listOf("ERR_AUTOJS6_BUILTIN_DISABLED", "disabled builtin", "Safe Node Profile replacement"),
            ),
            ResolverFixture(
                name = "scoped-path-escape",
                request = "../secret.js",
                from = "/work/main.js",
                trace = """
                [resolve] request request="../secret.js" from="/work/main.js"
                [resolve] error request="../secret.js" from="/work/main.js" code="ERR_AUTOJS6_MODULE_NOT_FOUND" autojs6Code="ERR_AUTOJS6_FS_PATH_ESCAPE" message="path escapes workingDirectory"
                """.trimIndent(),
                diagnosis = "scoped-fs-policy",
                needles = listOf("ERR_AUTOJS6_FS_PATH_ESCAPE", "workingDirectory", "escaping symlinks"),
            ),
        )

        fixtures.forEach { fixture ->
            val traceFile = fixtureDir.resolve("${fixture.name}.log")
            traceFile.writeText(fixture.trace)
            val report = nodeResolveExplainGroups(fixture.request, fixture.from, traceFile, traceFile.readText())
            val verbose = report.verboseText
            val compact = report.compactText
            val jsonText = report.jsonText
            check(report.json["diagnosis"] == fixture.diagnosis) {
                "Resolver visualizer diagnosis mismatch for ${fixture.name}: expected ${fixture.diagnosis}, got ${report.json["diagnosis"]}"
            }
            check(compact.contains("Mode: compact")) { "Compact mode missing for ${fixture.name}:\n$compact" }
            check(jsonText.contains("\"diagnosis\"")) { "JSON mode missing diagnosis for ${fixture.name}:\n$jsonText" }
            fixture.needles.forEach { needle ->
                check(verbose.contains(needle) || compact.contains(needle) || jsonText.contains(needle)) {
                    "Resolver visualizer report for ${fixture.name} missing '$needle':\n$verbose"
                }
            }
        }
        logger.lifecycle("Node resolver visualizer fixtures parsed exports, imports, disabled builtin, and scoped path failures.")
    }
}

tasks.matching { it.name == "verifyEmbeddedNodeDebug" }.configureEach {
    dependsOn(verifyNodeResolverVisualizerFixtures)
}

tasks.matching { it.name == "verifyEmbeddedNodeReleaseCandidate" }.configureEach {
    dependsOn(verifyNodeResolverVisualizerFixtures)
}
