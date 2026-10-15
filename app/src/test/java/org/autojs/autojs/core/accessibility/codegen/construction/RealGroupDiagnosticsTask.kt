package org.autojs.autojs.core.accessibility.codegen.construction

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.dataset.FeatureExport
import org.autojs.autojs.core.accessibility.codegen.dataset.TaskGroup
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackage
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageReader
import org.autojs.autojs.core.accessibility.codegen.model.ObliviousTreeModel
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.rank.ModelRanker
import org.autojs.autojs.core.accessibility.codegen.rank.RuleRanker
import org.autojs.autojs.core.accessibility.codegen.render.ClassicRenderer
import org.autojs.autojs.core.accessibility.codegen.search.CandidateSearch
import org.autojs.autojs.core.accessibility.codegen.search.SearchBudget
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Rejected
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Explicit offline diagnostic for maintainer-labelled real application groups (P2.5). It runs the
 * fourth experiment's frozen rule search, construction policy and final rankers on every private
 * group, records each candidate's observed survival in the later states, and optionally verifies
 * hand-written probe plans. The output is reading evidence inside `CODEGEN_PRIVATE_DIR`; it is not a
 * selection protocol. It reads real-train and real-test applications alike (D25) and trains, admits or promotes nothing.
 * zh-CN: 维护者标注的真实应用任务组 (P2.5) 的显式离线诊断. 对每个私有任务组运行第四轮冻结的规则搜索,
 * 构造策略与终局排序器, 记录每个候选在后续状态的观测存活, 并可验证手写探针计划. 输出仅是放在
 * `CODEGEN_PRIVATE_DIR` 内的阅读证据, 不构成选择协议: 同样读取 real-train 与 real-test 的应用 (D25), 此处不训练, 不录用也不推广任何策略或排序器.
 */
class RealGroupDiagnosticsTask {
    @Test fun runOfflineRealGroupDiagnostics() {
        val tasks = System.getenv("CODEGEN_REAL_DIAGNOSTICS_TASKS")
        assumeTrue("Set CODEGEN_REAL_DIAGNOSTICS_TASKS to a private directory of .cgtask.json groups", tasks != null)
        fun directory(name: String) = File(requireNotNull(System.getenv(name)) { "Missing $name" }).canonicalFile
        RealGroupDiagnostics.run(
            tasks = File(requireNotNull(tasks)).canonicalFile,
            policy = directory("CODEGEN_CONSTRUCTION_R3_FIT_BEST"),
            ranker = directory("CODEGEN_CONSTRUCTION_RANKER"),
            output = directory("CODEGEN_REAL_DIAGNOSTICS_OUTPUT"),
            probes = System.getenv("CODEGEN_REAL_DIAGNOSTICS_PROBES")?.takeIf { it.isNotBlank() }?.let { File(it).canonicalFile },
        )
    }
}

internal object RealGroupDiagnostics {
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().setPrettyPrinting().create()
    private const val SEARCH_MS = 800L
    private const val TOP = 5
    private val constructionBudget = ConstructionBudget(maxMillis = SEARCH_MS)
    private val methods = listOf(
        "rule_pipeline", "baseline_tiny", "baseline_ranker", "construction_rule", "construction_ranker",
        "heuristic_rule", "merged_rule", "merged_tiny", "merged_ranker",
    )

    private class Later(val stateId: String, val request: CodeGenRequest<SnapshotNode>, val verifier: PlanVerifier<SnapshotNode>)

    /** One canonical plan with its later-state outcomes; a `null` reason marks a surviving state. */
    private class Scored(val candidate: Verified<SnapshotNode>, val key: String, val outcomes: List<Pair<String, String?>>) {
        val sources = linkedSetOf<String>()
        val survival: Double get() = outcomes.count { it.second == null }.toDouble() / outcomes.size
    }

    private class Probe(val name: String, val plan: LocatePlan)

    /** A ranking model with the feature schema it was trained on. zh-CN: 排序模型及其训练时的特征 schema. */
    private class Model(val model: ObliviousTreeModel, val featureSchemaVersion: Int)

    fun run(tasks: File, policy: File, ranker: File, output: File, probes: File?) {
        FeatureExport.requirePrivate(tasks)
        FeatureExport.requirePrivate(output)
        probes?.let(FeatureExport::requirePrivate)
        val policyModel = ConstructionPolicyModel.read(File(policy, "policy.json"))
        val refittedModel = ExperimentRankerModel.read(File(ranker, "ranker.json"))
        val tinyPackage = ModelPackageReader().read(tinyBytes().inputStream()) as ModelPackage
        val refitted = Model(refittedModel.model, refittedModel.featureSchemaVersion)
        val tiny = Model(tinyPackage.model, tinyPackage.manifest.featureSchemaVersion)
        val probeJson = probes?.let { JsonParser.parseString(it.readText(Charsets.UTF_8)).asJsonObject }
        val files = tasks.walkTopDown().filter { it.isFile && it.name.endsWith(".cgtask.json") }.sortedBy { it.name }.toList()
        require(files.isNotEmpty()) { "No .cgtask.json under $tasks" }
        val groups = JsonArray()
        for (file in files) {
            val group = TaskGroup.load(file)
            require(group.source == "real") { "The diagnostic accepts real application groups only: ${group.groupId}" }
            group.requireLabelable()
            groups.add(evaluate(group, file, tasks, policyModel, refitted, tiny, probeJson?.getAsJsonArray(group.groupId)))
        }
        val result = JsonObject().apply {
            addProperty("format", 1)
            addProperty("role", "real-group-diagnostics")
            addProperty("constructionPolicySha256", hash(File(policy, "policy.json").readBytes()))
            addProperty("rankerSha256", hash(File(ranker, "ranker.json").readBytes()))
            addProperty("rankerFeatureSchemaVersion", refitted.featureSchemaVersion)
            addProperty("tinyRankerSha256", hash(tinyBytes()))
            addProperty("tinyFeatureSchemaVersion", tiny.featureSchemaVersion)
            addProperty("hostFeatureSchemaVersion", FeatureSchema.featureSchemaVersion)
            add("constructionBudget", gson.toJsonTree(constructionBudget))
            add("methods", gson.toJsonTree(methods))
            add("groups", groups)
        }
        require(output.isDirectory || output.mkdirs()) { "Cannot create $output" }
        File(output, "results.json").writeText(gson.toJson(result) + "\n", Charsets.UTF_8)
        println("Real group diagnostics: ${groups.size()} group(s) -> ${File(output, "results.json")}")
    }

    private fun evaluate(
        group: TaskGroup, file: File, root: File, policy: ConstructionPolicyModel,
        refitted: Model, tiny: Model, probeJson: JsonArray?,
    ): JsonObject {
        val capture = group.states[group.captureState]
        val request = group.request.forState(capture)
        val later = group.states.filterIndexed { index, _ -> index != group.captureState }
            .map { Later(it.stateId, group.request.forState(it), PlanVerifier(it.root)) }
        val cancellation = CancellationSignal()
        val verifier = PlanVerifier(capture.root, cancellation)
        fun millisSince(start: Long) = (System.nanoTime() - start) / 1e6
        var start = System.nanoTime()
        val rule = CandidateSearch(capture.root, cancellation, SearchBudget(), verifier).search(request)
        val ruleMillis = millisSince(start)
        start = System.nanoTime()
        val construction = ConstructionSearch(
            capture.root, cancellation, constructionBudget, policy, verifier,
            frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR,
        ).search(request)
        val constructionMillis = millisSince(start)
        start = System.nanoTime()
        val heuristic = ConstructionSearch(
            capture.root, cancellation, constructionBudget, ConstructionHeuristicPolicy, PlanVerifier(capture.root, cancellation),
            frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR,
        ).search(request)
        val heuristicMillis = millisSince(start)

        val scored = LinkedHashMap<String, Scored>()
        fun score(verified: Verified<SnapshotNode>): Scored {
            val key = ConstructionSearch.canonicalKey(verified.plan)
            return scored.getOrPut(key) {
                Scored(verified, key, later.map { state ->
                    state.stateId to (state.verifier.verify(state.request, verified.plan) as? Rejected)?.reason?.name
                })
            }
        }
        fun admit(source: String, candidates: List<Verified<SnapshotNode>>): List<Scored> =
            candidates.map { score(it).also { entry -> entry.sources += source } }.distinctBy { it.key }
        val ruleSet = admit("rule", rule.candidates)
        val constructionSet = admit("construction", construction.candidates)
        val heuristicSet = admit("heuristic", heuristic.candidates)
        val mergedSet = (ruleSet + constructionSet).distinctBy { it.key }
        val renderer = ClassicRenderer()

        fun entry(item: Scored, rank: Int? = null) = JsonObject().apply {
            rank?.let { addProperty("rank", it) }
            addProperty("key", item.key)
            addProperty("planKey", item.candidate.plan.planKey)
            addProperty("family", item.candidate.plan.family.name)
            add("sources", gson.toJsonTree(item.sources.toList()))
            addProperty("survival", item.survival)
            add("failedStates", JsonObject().apply { item.outcomes.forEach { (state, reason) -> if (reason != null) addProperty(state, reason) } })
            addProperty("code", renderer.render(request, item.candidate.plan))
        }
        fun ranking(name: String, set: List<Scored>, ranker: String): JsonObject = JsonObject().apply {
            addProperty("method", name)
            addProperty("candidates", set.size)
            val byKey = set.associateBy { it.key }
            val ordered: List<Scored>? = try {
                val ranked = when (ranker) {
                    "rule" -> RuleRanker().rank(request, set.map { it.candidate })
                    "tiny" -> ModelRanker(tiny.model, budgetMs = 150, featureSchemaVersion = tiny.featureSchemaVersion).rank(request, set.map { it.candidate })
                    else -> ModelRanker(refitted.model, budgetMs = 150, featureSchemaVersion = refitted.featureSchemaVersion).rank(request, set.map { it.candidate })
                }
                ranked.map { byKey.getValue(ConstructionSearch.canonicalKey(it.plan)) }
            } catch (failure: Exception) {
                addProperty("error", failure.javaClass.simpleName + ": " + failure.message)
                null
            }
            addProperty("coverage", if (set.isEmpty()) 0.0 else 1.0)
            addProperty("candidateBest", set.maxOfOrNull { it.survival } ?: 0.0)
            addProperty("top1Survival", ordered?.firstOrNull()?.survival ?: 0.0)
            addProperty("top3BestSurvival", ordered?.take(3)?.maxOfOrNull { it.survival } ?: 0.0)
            add("top", JsonArray().apply { ordered?.take(TOP)?.forEachIndexed { index, item -> add(entry(item, index + 1)) } })
            add("order", JsonArray().apply { ordered?.forEach { add(it.key) } })
        }
        val rankings = JsonArray().apply {
            add(ranking("rule_pipeline", ruleSet, "rule"))
            add(ranking("baseline_tiny", ruleSet, "tiny"))
            add(ranking("baseline_ranker", ruleSet, "refitted"))
            add(ranking("construction_rule", constructionSet, "rule"))
            add(ranking("construction_ranker", constructionSet, "refitted"))
            add(ranking("heuristic_rule", heuristicSet, "rule"))
            add(ranking("merged_rule", mergedSet, "rule"))
            add(ranking("merged_tiny", mergedSet, "tiny"))
            add(ranking("merged_ranker", mergedSet, "refitted"))
        }
        val orders = rankings.associate { it.asJsonObject["method"].asString to it.asJsonObject.getAsJsonArray("order").map { key -> key.asString } }

        val receiver = ruleSet.firstOrNull()?.candidate?.plan?.receiver
            ?: constructionSet.firstOrNull()?.candidate?.plan?.receiver
            ?: receiverFor(request, verifier)
        val probes = JsonArray().apply {
            probeJson?.forEach { item -> add(probe(parseProbe(item.asJsonObject, receiver), request, verifier, later, scored, orders, renderer)) }
        }

        return JsonObject().apply {
            addProperty("groupId", group.groupId)
            addProperty("app", group.app)
            addProperty("screenFamily", group.screenFamily)
            addProperty("task", file.relativeTo(root).invariantSeparatorsPath)
            addProperty("captureState", group.captureState)
            add("request", gson.toJsonTree(group.request))
            add("states", JsonArray().apply {
                group.states.forEach { state ->
                    add(JsonObject().apply {
                        addProperty("stateId", state.stateId)
                        add("mutations", gson.toJsonTree(state.mutations))
                        add("targetPaths", gson.toJsonTree(state.targetPaths))
                        addProperty("nodes", countNodes(state.root))
                    })
                }
            })
            addProperty("receiver", receiver.toString())
            add("searches", JsonObject().apply {
                add("rule", JsonObject().apply {
                    addProperty("candidates", rule.candidates.size)
                    addProperty("attempted", rule.attemptedCount)
                    addProperty("budgetExhausted", rule.budgetExhausted)
                    addProperty("millis", ruleMillis)
                    add("rejectionCounts", gson.toJsonTree(rule.rejectionCounts.mapKeys { it.key.name }))
                    addProperty("closestRejection", rule.closestRejection?.reason?.name)
                    add("families", gson.toJsonTree(rule.candidates.groupingBy { it.plan.family.name }.eachCount()))
                })
                add("construction", constructionJson(construction, constructionMillis))
                add("heuristic", constructionJson(heuristic, heuristicMillis))
            })
            add("rankings", rankings)
            add("ceiling", JsonArray().apply { scored.values.filter { it.survival == 1.0 }.forEach { add(entry(it)) } })
            add("survivalBySource", JsonObject().apply {
                for (source in listOf("rule", "construction", "heuristic")) {
                    val members = scored.values.filter { source in it.sources }
                    add(source, JsonObject().apply {
                        addProperty("candidates", members.size)
                        addProperty("surviveAll", members.count { it.survival == 1.0 })
                        addProperty("surviveSome", members.count { it.survival > 0.0 && it.survival < 1.0 })
                        addProperty("surviveNone", members.count { it.survival == 0.0 })
                    })
                }
            })
            add("candidates", JsonArray().apply {
                scored.values.sortedWith(compareByDescending<Scored> { it.survival }.thenBy { it.key }).forEach { add(entry(it)) }
            })
            add("probes", probes)
        }
    }

    private fun probe(
        probe: Probe, request: CodeGenRequest<SnapshotNode>, verifier: PlanVerifier<SnapshotNode>, later: List<Later>,
        scored: Map<String, Scored>, orders: Map<String, List<String>>, renderer: ClassicRenderer,
    ) = JsonObject().apply {
        val key = ConstructionSearch.canonicalKey(probe.plan)
        addProperty("name", probe.name)
        addProperty("key", key)
        addProperty("planKey", probe.plan.planKey)
        addProperty("code", renderer.render(request, probe.plan))
        val captured = verifier.verify(request, probe.plan)
        addProperty("captureOutcome", (captured as? Rejected)?.reason?.name ?: "VERIFIED")
        val outcomes = later.map { it.stateId to (it.verifier.verify(it.request, probe.plan) as? Rejected)?.reason?.name }
        add("laterOutcomes", JsonObject().apply { outcomes.forEach { (state, reason) -> addProperty(state, reason ?: "VERIFIED") } })
        addProperty("survival", outcomes.count { it.second == null }.toDouble() / outcomes.size)
        add("sources", gson.toJsonTree(scored[key]?.sources?.toList().orEmpty()))
        add("rankByMethod", JsonObject().apply {
            orders.forEach { (method, order) -> order.indexOf(key).let { addProperty(method, if (it < 0) null else it + 1) } }
        })
    }

    private fun parseProbe(json: JsonObject, receiver: PlanReceiver): Probe {
        fun literal(element: JsonElement): Any {
            val primitive = element.asJsonPrimitive
            return when {
                primitive.isBoolean -> primitive.asBoolean
                primitive.isNumber -> primitive.asInt
                else -> primitive.asString
            }
        }
        fun spec(array: JsonArray) = SelectorSpec(array.map { pair -> Condition(pair.asJsonArray[0].asString, literal(pair.asJsonArray[1])) })
        val selector = spec(json.getAsJsonArray("selector"))
        val steps = json.getAsJsonArray("steps")?.map { step ->
            val kind = step.asJsonArray[0].asString
            val value = step.asJsonArray[1]
            when (kind) {
                "up" -> PlanStep.Up(value.asInt)
                "child" -> PlanStep.Child(value.asInt)
                "index" -> PlanStep.Index(value.asInt)
                "search" -> PlanStep.Search(spec(value.asJsonArray))
                else -> error("Unknown probe step: $kind")
            }
        }.orEmpty()
        val family = LocatePlan.Family.valueOf(json["family"].asString)
        val cardinality = if (family == LocatePlan.Family.CONTAINER_ITEMS) LocatePlan.Cardinality.ALL else LocatePlan.Cardinality.ONE
        return Probe(json["name"].asString, LocatePlan(family, selector, steps, cardinality, receiver))
    }

    private fun receiverFor(request: CodeGenRequest<SnapshotNode>, verifier: PlanVerifier<SnapshotNode>): PlanReceiver {
        if (request.action == CodeGenRequest.Action.NONE || request.receiver == CodeGenRequest.Receiver.SELF) return PlanReceiver.Self
        var node = verifier.canonical(request.targets.single())
        var levels = 0
        while (node != null && !PlanVerifier.supports(node, request.action)) {
            node = verifier.tree.parent(node)
            levels++
        }
        return if (node == null || levels == 0) PlanReceiver.Self else PlanReceiver.Up(levels)
    }

    private fun constructionJson(result: ConstructionResult<SnapshotNode>, millis: Double) = JsonObject().apply {
        addProperty("candidates", result.candidates.size)
        addProperty("stopReason", result.stopReason.name)
        addProperty("attempted", result.stats.attemptedCount)
        addProperty("expandedStates", result.stats.expandedStates)
        addProperty("actionScores", result.stats.actionScores)
        addProperty("millis", millis)
        addProperty("policyFailure", result.policyFailure)
        add("rejectionCounts", gson.toJsonTree(result.rejectionCounts.mapKeys { it.key.name }))
        add("families", gson.toJsonTree(result.candidates.groupingBy { it.plan.family.name }.eachCount()))
    }

    private fun countNodes(root: SnapshotNode): Int {
        var count = 0
        val stack = ArrayDeque<SnapshotNode>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            count++
            for (index in 0 until node.childCount()) node.child(index)?.let(stack::add)
        }
        return count
    }

    private fun tinyBytes() = requireNotNull(javaClass.getResourceAsStream("/codegen/models/tiny-test-v1.ajcgm")).use { it.readBytes() }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
