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
    val text: String,
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
            linkedMapOf(
                "code" to event.fields["code"],
                "autojs6Code" to event.fields["autojs6Code"],
                "message" to event.fields["message"],
                "raw" to event.raw,
            )
        }
    val rawTrace = selectedEvents.map { it.raw }
    val traceAvailable = traceText != null
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
        "rawTrace" to rawTrace,
    )
    val text = buildString {
        appendLine("AutoJs6 Node Resolver Explanation")
        appendLine("Generated at: ${report["generatedAt"]}")
        appendLine("Request: ${request.ifBlank { "not specified" }}")
        appendLine("From: ${from.ifBlank { "not specified" }}")
        appendLine("Trace file: ${traceFile?.absolutePath ?: "not specified"}")
        appendLine("Trace available: $traceAvailable")
        appendLine("Trace groups: ${groups.size}")
        appendLine("Selected groups: ${selectedGroups.size}")
        appendLine()
        if (!traceAvailable) {
            appendLine("No trace file was available.")
            appendLine("Run the failing script with AUTOJS6_NODE_RESOLVE_TRACE=1, capture stderr/logcat, then pass --trace-file <file>.")
            appendLine()
        }
        appendLine("Search paths:")
        if (searchPaths.isEmpty()) {
            appendLine("  none observed")
        } else {
            searchPaths.forEach { appendLine("  $it") }
        }
        appendLine()
        appendLine("package.json fields read:")
        if (packageJsonFiles.isEmpty()) {
            appendLine("  none observed")
        } else {
            packageJsonFiles.forEach { appendLine("  $it") }
        }
        appendLine()
        appendLine("Exports/imports/main branch:")
        if (packageBranches.isEmpty()) {
            appendLine("  none observed")
        } else {
            packageBranches.forEach { appendLine("  $it") }
        }
        appendLine()
        appendLine("Null blocking:")
        if (nullBlocking.isEmpty()) {
            appendLine("  none observed")
        } else {
            nullBlocking.forEach { appendLine("  $it") }
        }
        appendLine()
        appendLine("Final errors:")
        if (finalErrors.isEmpty()) {
            appendLine("  none observed")
        } else {
            finalErrors.forEach { error ->
                appendLine("  code=${error["code"]} autojs6Code=${error["autojs6Code"]} message=${error["message"]}")
            }
        }
        appendLine()
        appendLine("Raw trace:")
        if (rawTrace.isEmpty()) {
            appendLine("  none selected")
        } else {
            rawTrace.forEach { appendLine("  $it") }
        }
    }
    return NodeResolveVisualizerReport(report, text)
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
        val traceFile = traceFileValue.takeIf(String::isNotBlank)?.let { rootProject.file(it) }
        val traceText = traceFile?.takeIf(File::isFile)?.readText()
        val report = nodeResolveExplainGroups(requestValue, fromValue, traceFile, traceText)
        val outputDir = layout.buildDirectory.dir("reports/nodejs/resolver").get().asFile
        outputDir.mkdirs()
        val jsonFile = outputDir.resolve("resolve-explanation.json")
        val textFile = outputDir.resolve("resolve-explanation.txt")
        jsonFile.writeText(nodeResolveJson(report.json) + "\n")
        textFile.writeText(report.text)
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
        val traceFile = fixtureDir.resolve("sample-resolve-trace.log")
        traceFile.writeText(
            """
            [resolve] request request="trace-pkg/private" from="/work/main.js"
            [resolve] node_modules search request="trace-pkg/private" from="/work/main.js" paths="/work/node_modules"
            [resolve] package.json found request="trace-pkg/private" packageDir="/work/node_modules/trace-pkg" file="/work/node_modules/trace-pkg/package.json"
            [resolve] exports blocked request="./private" module="trace-pkg/private" target="null" selectedCondition="" conditionKeys="" ignoredConditions=""
            [resolve] error request="trace-pkg/private" from="/work/main.js" code="ERR_PACKAGE_PATH_NOT_EXPORTED" autojs6Code="ERR_AUTOJS6_PACKAGE_PATH_NOT_EXPORTED" message="target=null"
            """.trimIndent()
        )
        val report = nodeResolveExplainGroups("trace-pkg/private", "/work/main.js", traceFile, traceFile.readText())
        val text = report.text
        check(text.contains("/work/node_modules")) { "Search path missing from resolver visualizer report:\n$text" }
        check(text.contains("/work/node_modules/trace-pkg/package.json")) { "package.json field missing from resolver visualizer report:\n$text" }
        check(text.contains("exports blocked")) { "Null blocking branch missing from resolver visualizer report:\n$text" }
        check(text.contains("ERR_PACKAGE_PATH_NOT_EXPORTED")) { "Final error code missing from resolver visualizer report:\n$text" }
        logger.lifecycle("Node resolver visualizer fixture report parsed expected package failure details.")
    }
}

tasks.matching { it.name == "verifyEmbeddedNodeDebug" }.configureEach {
    dependsOn(verifyNodeResolverVisualizerFixtures)
}

tasks.matching { it.name == "verifyEmbeddedNodeReleaseCandidate" }.configureEach {
    dependsOn(verifyNodeResolverVisualizerFixtures)
}
