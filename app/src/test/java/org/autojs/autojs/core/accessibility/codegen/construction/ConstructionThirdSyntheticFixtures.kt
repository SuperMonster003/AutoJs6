package org.autojs.autojs.core.accessibility.codegen.construction

import com.google.gson.Gson
import org.autojs.autojs.core.accessibility.NodeInfoSnapshot
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.dataset.TaskGroup
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.IdentityHashMap

/**
 * Independently authored third-iteration holdout, frozen before any third-round policy was trained
 * or evaluated. No construction search, learned weights or evaluation output was consulted while
 * writing these scenes. The four scenario families (a chat thread, search results, a wizard form
 * and a file browser) are separate from the captured fixture application and from the first two
 * synthetic cohorts; familiar UI concepts still overlap, so this is not proof that every visual
 * or semantic pattern is absent from training data. They remain synthetic layouts, not evidence
 * of generalization to real applications. Seeds are fixed variations of a scenario, not four
 * additional independent application samples. Ground truth exists only in target identity/path
 * lists, never in accessible node attributes.
 *
 * zh-CN: 第三轮独立编写并冻结的留出集, 编写时未参考任何第三轮策略或评估结果. 四个场景族
 * (聊天线程, 搜索结果, 向导表单, 文件浏览) 与受控夹具应用及前两批合成任务不同, 但常见界面概念
 * 仍有重叠, 不能作为真实应用泛化证据. 真值仅保存在节点身份及路径列表中.
 */
object ConstructionThirdSyntheticFixtures {
    private const val APP = "org.autojs.autojs6.fixture.third"
    private const val FRAME = "android.widget.FrameLayout"
    private const val COLUMN = "android.widget.LinearLayout"
    private const val TEXT = "android.widget.TextView"
    private const val BUTTON = "android.widget.Button"
    private const val IMAGE = "android.widget.ImageView"
    private const val IMAGE_BUTTON = "android.widget.ImageButton"
    private const val EDIT = "android.widget.EditText"

    private data class Scene(val root: NodeInfoSnapshot, val targets: List<NodeInfoSnapshot>, val mutations: List<String>)
    private data class Scenario(
        val name: String,
        val action: CodeGenRequest.Action,
        val build: (Int, Int, Boolean) -> Scene,
    )

    fun groups(): List<TaskGroup> = listOf(
        Scenario("chat-thread", CodeGenRequest.Action.LONG_CLICK, ::chatThread),
        Scenario("search-results", CodeGenRequest.Action.CLICK, ::searchResults),
        Scenario("wizard-stepper", CodeGenRequest.Action.SET_TEXT("example"), ::wizardStepper),
        Scenario("file-browser", CodeGenRequest.Action.CLICK, ::fileBrowser),
    ).flatMap { scenario ->
        (0..2).flatMap { seed ->
            listOf(false, true).map { collection -> group(scenario, seed, collection) }
        }
    }

    private fun group(scenario: Scenario, seed: Int, collection: Boolean): TaskGroup {
        val scope = if (collection) "collection" else "single"
        val groupId = "construction-third-${scenario.name}-seed${seed + 1}-$scope"
        return TaskGroup(
            groupId = groupId,
            source = "fixture",
            app = APP,
            screenFamily = "construction-third/${scenario.name}",
            request = TaskGroup.Request(
                scope = if (collection) CodeGenRequest.Scope.COLLECTION else CodeGenRequest.Scope.SINGLE,
                attributes = setOf(CodeGenRequest.Attr.ID, CodeGenRequest.Attr.TEXT, CodeGenRequest.Attr.DESC),
                action = scenario.action,
                receiver = CodeGenRequest.Receiver.AUTO,
            ),
            captureState = 0,
            states = (0..2).map { version ->
                val scene = scenario.build(seed, version, collection)
                annotate(scene.root)
                val paths = targetPaths(scene.root, scene.targets)
                val root = SnapshotNode(scene.root)
                TaskGroup.State(
                    stateId = "s$version",
                    snapshot = "synthetic/$groupId/s$version.layout.json",
                    mutations = scene.mutations,
                    targetPaths = paths,
                    root = root,
                    targets = paths.map { path -> path.fold(root) { node, index -> requireNotNull(node.child(index)) } },
                )
            },
        )
    }

    /** Message bodies are selected; the long press belongs to their bubbles, which later gain frames. */
    private fun chatThread(seed: Int, version: Int, collection: Boolean): Scene {
        val targets = ArrayList<NodeInfoSnapshot>()
        val reactions = listOf("React", "Réagir", "Reaccionar")
        fun message(index: Int, pinned: Boolean): NodeInfoSnapshot {
            val body = label("Note ${index + 1}", "message_text")
            if (pinned) targets += body
            val time = label("10:${(index * 7 + version * 3 + seed) % 50 + 10}", "message_time")
            val bubble = container("message_bubble", listOf(body, time)).apply {
                longClickable = true
                clickable = true
                focusable = true
                actionNames = listOf("ACTION_CLICK", "ACTION_LONG_CLICK")
            }
            val avatar = node(IMAGE, "message_avatar", desc = "Avatar")
            val react = node(IMAGE_BUTTON, "message_react", desc = reactions[version]).apply {
                clickable = true
                focusable = true
                actionNames = listOf("ACTION_CLICK")
            }
            val framed = if (version == 2) container("bubble_frame", listOf(bubble)) else bubble
            return container("message_row", listOf(avatar, framed, react))
        }
        val pinned = (0 until if (collection) 2 else 1).map { message(it + 20, true) }
        val notice = label("Pinned by you", "thread_notice")
        val pinnedSection = container("pinned_messages", listOf(notice) + if (version == 2) pinned.reversed() else pinned)
        val threadMessages = (0 until 6 + seed).map { message(it, false) }.toMutableList()
        if (version == 1) threadMessages.addAll(0, (0 until 2).map { message(it + 40, false) })
        val thread = container("thread_messages", threadMessages)
        val sections = if (version == 1) listOf(thread, pinnedSection) else listOf(pinnedSection, thread)
        return Scene(page("Conversation", sections), targets, when (version) {
            0 -> emptyList()
            1 -> listOf("insert-new-messages-at-head", "move-pinned-section-below-thread", "retext-message-times")
            else -> listOf("switch-reaction-locale", "wrap-bubbles-in-frames", "reverse-pinned-order", "retext-message-times-again")
        })
    }

    /** Sponsored cards keep their buttons while organic results reorder, reprice and localize. */
    private fun searchResults(seed: Int, version: Int, collection: Boolean): Scene {
        val targets = ArrayList<NodeInfoSnapshot>()
        val labels = listOf("Add to cart", "Added", "Añadir")
        fun card(index: Int, sponsored: Boolean): NodeInfoSnapshot {
            val title = label("Item ${index + 1}", "result_title")
            val price = label("$${12 + index * 3 + version * 2}.99", "result_price")
            val rating = label("${(index + seed) % 5 + 1} stars", "result_rating")
            val action = node(BUTTON, "result_action", text = labels[version]).apply {
                clickable = true
                focusable = true
                actionNames = listOf("ACTION_CLICK")
            }
            if (sponsored) targets += action
            val body = container("result_body", listOf(title, price, rating))
            val children = if (version == 2) listOf(container("card_frame", listOf(body, action))) else listOf(body, action)
            return container("result_card", children)
        }
        val sponsoredCards = (0 until if (collection) 2 else 1).map { card(it + 30, true) }
        val sponsoredContent = buildList {
            add(label("Sponsored", "section_title"))
            if (version == 1) add(label("Ads related to your search", "sponsored_notice"))
            addAll(if (version == 1) sponsoredCards.reversed() else sponsoredCards)
        }
        val sponsored = container("sponsored_results", sponsoredContent)
        val organicCards = (0 until 5 + seed).map { card(it, false) }
        val rotated = if (version == 1) organicCards.drop(2) + organicCards.take(2) else organicCards
        val organic = container("organic_results", listOf(label("Results", "section_title")) + rotated)
        val sections = if (version == 1) listOf(organic, sponsored) else listOf(sponsored, organic)
        return Scene(page("Search", sections), targets, when (version) {
            0 -> emptyList()
            1 -> listOf("rotate-organic-results", "retext-prices", "mark-buttons-added", "insert-sponsored-notice", "move-sponsored-below-organic", "reverse-sponsored-cards")
            else -> listOf("switch-button-locale", "wrap-card-content-in-frames", "retext-prices-again")
        })
    }

    /** Form inputs are selected for text entry; the form later scrolls, renames ids and gains helpers. */
    private fun wizardStepper(seed: Int, version: Int, collection: Boolean): Scene {
        val targets = ArrayList<NodeInfoSnapshot>()
        val hints = listOf("Enter value", "Enter value", "Introduce un valor")
        fun field(index: Int, inForm: Boolean): NodeInfoSnapshot {
            val input = node(EDIT, if (version == 2) "wizard_field" else "step_input",
                text = listOf("", "Draft ${index + 1}", "Draft ${index + 1} edited")[version], desc = hints[version]).apply {
                editable = true
                focusable = true
                clickable = true
                actionNames = listOf("ACTION_SET_TEXT", "ACTION_CLICK", "ACTION_FOCUS")
            }
            if (inForm) targets += input
            val caption = label("Field ${index + 1}", "field_caption")
            val children = if (version == 2) listOf(caption, label("Required", "field_helper"), input) else listOf(caption, input)
            return container("field_row", children)
        }
        fun review(index: Int): NodeInfoSnapshot {
            val value = node(EDIT, "review_value", text = "Answer ${index + 1}", desc = "Previous answer").apply {
                focusable = true
                enabled = false
            }
            return container("review_row", listOf(label("Step ${index + 1}", "review_caption"), value))
        }
        val indicators = (0 until 3 + seed % 2).map { step ->
            label("${step + 1}", "step_indicator").apply { selected = step == version }
        }
        val header = container("step_header", indicators)
        val fields = (0 until if (collection) 2 else 1).map { field(it, true) }
        val footer = container("step_footer", listOf(
            node(BUTTON, "step_back", text = "Back").apply { clickable = true; focusable = true; actionNames = listOf("ACTION_CLICK") },
            node(BUTTON, "step_next", text = "Next").apply { clickable = true; focusable = true; actionNames = listOf("ACTION_CLICK") },
        ))
        val form = container("step_form", fields + footer)
        val wrappedForm = if (version == 1) container("form_scroll", listOf(form)).apply {
            className = "android.widget.ScrollView"
            scrollable = true
            actionNames = listOf("ACTION_SCROLL_FORWARD", "ACTION_SCROLL_BACKWARD")
        } else form
        val reviews = container("review_panel", (0 until 3 + seed).map(::review))
        return Scene(page("Setup", listOf(header, wrappedForm, reviews)), targets, when (version) {
            0 -> emptyList()
            1 -> listOf("advance-step-indicator", "wrap-form-in-scroll", "populate-draft-text")
            else -> listOf("advance-step-indicator-again", "rename-input-ids", "insert-helper-labels", "switch-hint-locale", "edit-draft-text")
        })
    }

    /** Overflow buttons of recent files are selected; lists resort, grow and lose button ids. */
    private fun fileBrowser(seed: Int, version: Int, collection: Boolean): Scene {
        val targets = ArrayList<NodeInfoSnapshot>()
        val names = listOf("report.pdf", "budget.xlsx", "notes.txt", "photo.jpg", "archive.zip", "slides.pptx", "readme.md")
        val options = listOf("More options", "More options", "Más opciones")
        fun row(index: Int, recent: Boolean): NodeInfoSnapshot {
            val icon = node(IMAGE, "file_icon", desc = if ((index + seed) % 3 == 0) "Folder" else "Document")
            val name = label(names[(index + seed) % names.size] + if (recent) "" else " ${index + 1}", "file_name")
            val size = label("${(index * 37 + version * 11 + seed) % 900 + 100} KB", "file_size")
            val menu = node(IMAGE_BUTTON, if (version == 2) null else "file_menu", desc = options[version]).apply {
                clickable = true
                focusable = true
                actionNames = listOf("ACTION_CLICK")
            }
            if (recent) targets += menu
            val details = container("file_details", listOf(name, size))
            return container("file_row", listOf(icon, details, menu)).apply {
                clickable = true
                focusable = true
                actionNames = listOf("ACTION_CLICK")
            }
        }
        val recentRows = (0 until if (collection) 2 else 1).map { row(it + 10, true) }
        val recent = container("recent_files", listOf(label("Recent", "section_title")) + if (version == 2) recentRows.reversed() else recentRows)
        val allRows = (0 until 6 + seed).map { row(it, false) }.toMutableList()
        if (version == 1) allRows.add(0, row(50, false))
        val all = container("all_files", listOf(label("All files", "section_title")) + if (version == 1) allRows.reversed() else allRows)
        val sections = if (version == 1) listOf(all, recent) else listOf(recent, all)
        return Scene(page("Files", sections), targets, when (version) {
            0 -> emptyList()
            1 -> listOf("insert-new-file", "reverse-sort-order", "retext-file-sizes", "move-recent-below-all")
            else -> listOf("strip-menu-button-ids", "switch-menu-locale", "reverse-recent-rows", "retext-file-sizes-again")
        })
    }

    private fun page(title: String, children: List<NodeInfoSnapshot>): NodeInfoSnapshot {
        val content = container("page_content", listOf(label(title, "screen_title")) + children)
        val scroll = container("page_scroll", listOf(content)).apply {
            className = "android.widget.ScrollView"
            scrollable = true
            actionNames = listOf("ACTION_SCROLL_FORWARD", "ACTION_SCROLL_BACKWARD")
        }
        return container("screen", listOf(scroll)).apply { className = FRAME }
    }

    private fun container(id: String?, children: List<NodeInfoSnapshot>) = node(COLUMN, id).apply { this.children = children }
    private fun label(text: String, id: String) = node(TEXT, id, text = text)
    private fun node(type: String, id: String?, text: String = "", desc: String? = null) = NodeInfoSnapshot(
        packageName = APP, id = id, fullId = id?.let { "$APP:id/$it" },
        className = type, text = text, desc = desc, enabled = true,
    )

    /** Assign ordinary layout metadata without inspecting the target list. */
    private fun annotate(root: NodeInfoSnapshot) {
        data class Item(val node: NodeInfoSnapshot, val depth: Int, val index: Int, val parent: NodeInfoSnapshot?)
        val pending = ArrayDeque<Item>()
        pending.add(Item(root, 0, 0, null))
        val annotated = ArrayList<NodeInfoSnapshot>()
        var order = 0
        while (pending.isNotEmpty()) {
            val (node, depth, index, parent) = pending.removeLast()
            annotated += node
            val top = order++ * 28
            node.depth = depth
            node.indexInParent = index
            node.childCount = node.children.size
            node.bounds = intArrayOf(depth * 16, top, 1080 - depth * 16, top + 28)
            node.visibleToUser = top < 1920
            node.center = doubleArrayOf(540.0, top + 14.0)
            node.boundsInParent = node.bounds.copyOf().also { bounds ->
                if (parent != null) {
                    bounds[0] -= parent.bounds[0]; bounds[2] -= parent.bounds[0]
                    bounds[1] -= parent.bounds[1]; bounds[3] -= parent.bounds[1]
                }
            }
            for (childIndex in node.children.indices.reversed()) {
                pending.add(Item(node.children[childIndex], depth + 1, childIndex, node))
            }
        }
        for (node in annotated.asReversed()) {
            if (node.children.isNotEmpty()) {
                node.bounds[3] = node.children.maxOf { it.bounds[3] }
                node.boundsInParent[3] = node.boundsInParent[1] + node.bounds[3] - node.bounds[1]
                node.center[1] = (node.bounds[1] + node.bounds[3]) / 2.0
            }
        }
        root.bounds = intArrayOf(0, 0, 1080, 1920)
        root.boundsInParent = root.bounds.copyOf()
        root.center = doubleArrayOf(540.0, 960.0)
    }

    private fun targetPaths(root: NodeInfoSnapshot, targets: List<NodeInfoSnapshot>): List<List<Int>> {
        val wanted = IdentityHashMap<NodeInfoSnapshot, Boolean>().apply { targets.forEach { put(it, true) } }
        val found = IdentityHashMap<NodeInfoSnapshot, List<Int>>()
        val pending = ArrayDeque<Pair<NodeInfoSnapshot, List<Int>>>()
        pending.add(root to emptyList())
        while (pending.isNotEmpty()) {
            val (node, path) = pending.removeLast()
            if (wanted.containsKey(node)) found[node] = path
            node.children.forEachIndexed { index, child -> pending.add(child to path + index) }
        }
        require(targets.isNotEmpty() && wanted.size == targets.size)
        return targets.map { requireNotNull(found[it]) { "Fixture target must belong to its captured tree" } }
    }
}

class ConstructionThirdSyntheticFixturesTest {
    @Test
    fun everyFrozenGroupHasThreeStatesAndResolvableTargetsWithoutTruthMarkers() {
        val groups = ConstructionThirdSyntheticFixtures.groups()
        assertEquals(24, groups.size)
        assertEquals(24, groups.map { it.groupId }.distinct().size)
        assertEquals(4, groups.map { it.screenFamily }.distinct().size)
        val earlier = (ConstructionBottleneckFixtures.groups() + ConstructionGeneralizationFixtures.groups())
        assertTrue(groups.map { it.groupId }.toSet().intersect(earlier.map { it.groupId }.toSet()).isEmpty())
        assertTrue(groups.map { it.screenFamily }.toSet().intersect(earlier.map { it.screenFamily }.toSet()).isEmpty())
        for (group in groups) {
            group.requireLabelable()
            assertEquals("fixture", group.source)
            assertEquals(0, group.captureState)
            assertEquals(3, group.states.size)
            assertEquals(CodeGenRequest.Receiver.AUTO, group.request.receiver)
            for (state in group.states) {
                val count = if (group.request.scope == CodeGenRequest.Scope.SINGLE) 1 else 2
                assertEquals(count, state.targets.size)
                assertEquals(count, state.targetPaths.distinct().size)
                state.targetPaths.forEachIndexed { index, path ->
                    assertSame(state.targets[index], path.fold(state.root) { node, child -> requireNotNull(node.child(child)) })
                }
                val nodes = nodes(state.root).toList()
                assertTrue(nodes.size in 10..499)
                for (node in nodes) {
                    assertEquals(node.childCount(), node.snapshot.childCount)
                    node.parent()?.let { parent ->
                        assertEquals(parent.depth() + 1, node.depth())
                        assertSame(node, parent.child(node.indexInParent()))
                    }
                    assertTrue(node.snapshot.summary.isEmpty())
                    assertTrue(!node.accessibilityFocused() && !node.focused())
                    listOf(node.id().orEmpty(), node.text(), node.desc().orEmpty()).forEach { value ->
                        assertTrue(!value.contains("target", ignoreCase = true))
                        assertTrue(!value.contains("truth", ignoreCase = true))
                    }
                }
            }
        }
    }

    @Test
    fun repeatedConstructionIsDeterministicAndEveryFutureChangesTheCapture() {
        val first = ConstructionThirdSyntheticFixtures.groups()
        val second = ConstructionThirdSyntheticFixtures.groups()
        first.zip(second).forEach { (left, right) ->
            assertEquals(left.groupId, right.groupId)
            left.states.zip(right.states).forEach { (a, b) ->
                assertEquals(a.targetPaths, b.targetPaths)
                assertEquals(fingerprint(a.root), fingerprint(b.root))
            }
            assertTrue(left.states.first().mutations.isEmpty())
            left.states.drop(1).forEach { state ->
                assertTrue(state.mutations.isNotEmpty())
                assertNotEquals(fingerprint(left.states.first().root), fingerprint(state.root))
            }
            assertTrue(left.states.drop(1).any { it.targetPaths != left.states.first().targetPaths })
        }
    }

    @Test
    fun fixedGrammarPlansMeetIntentAcrossAllStatesIncludingActionReceivers() {
        // Direct verifier witnesses establish grammar representability. This test does not call
        // a constructor, supply a witness to search, or assert that a learned policy finds it.
        for (group in ConstructionThirdSyntheticFixtures.groups()) {
            val (anchorId, inner) = when (group.screenFamily.substringAfterLast('/')) {
                "chat-thread" -> "pinned_messages" to SelectorSpec(Condition("id", "message_text"))
                "search-results" -> "sponsored_results" to SelectorSpec(Condition("className", "android.widget.Button"))
                "wizard-stepper" -> "step_form" to SelectorSpec(Condition("editable", true))
                "file-browser" -> "recent_files" to SelectorSpec(Condition("className", "android.widget.ImageButton"))
                else -> error("Unknown frozen scenario")
            }
            val collection = group.request.scope == CodeGenRequest.Scope.COLLECTION
            val receiver = if (group.screenFamily.endsWith("chat-thread")) PlanReceiver.Up(1) else PlanReceiver.Self
            val plan = LocatePlan(
                family = if (collection) LocatePlan.Family.CONTAINER_ITEMS else LocatePlan.Family.ANCHOR_SEARCH,
                selector = SelectorSpec(Condition("id", anchorId)),
                steps = listOf(PlanStep.Search(inner)),
                cardinality = if (collection) LocatePlan.Cardinality.ALL else LocatePlan.Cardinality.ONE,
                receiver = receiver,
            )
            for (state in group.states) {
                val verification = PlanVerifier(state.root).verify(group.request.forState(state), plan)
                assertTrue("${group.groupId}/${state.stateId}: $verification", verification is Verified<*>)
                assertEquals(state.targets.toSet(), (verification as Verified<SnapshotNode>).resolved.toSet())
                for (target in state.targets) {
                    val actionNode = if (receiver is PlanReceiver.Up) requireNotNull(target.parent()) else target
                    assertTrue(PlanVerifier.supports(actionNode, group.request.action))
                    if (receiver is PlanReceiver.Up) assertTrue(!PlanVerifier.supports(target, group.request.action))
                }
            }
        }
    }

    @Test
    fun selfSelectorsOfTheCaptureDoNotSurviveEveryLaterState() {
        // Each scenario removes at least one capture-time shortcut: section moves, id renames or
        // stripped ids, localized text/desc and reprinted numbers. A plan that only names the target
        // by its own id or its text must therefore fail at least once per scenario family.
        val families = ConstructionThirdSyntheticFixtures.groups().groupBy { it.screenFamily }
        assertEquals(4, families.size)
        for ((family, groups) in families) {
            var anySelfFailure = false
            for (group in groups) {
                val capture = group.states[group.captureState]
                val target = capture.targets.first()
                val conditions = buildList {
                    target.id()?.takeIf(String::isNotEmpty)?.let { add(Condition("id", it.substringAfterLast(":id/"))) }
                    target.text().takeIf(String::isNotEmpty)?.let { add(Condition("text", it)) }
                    target.desc()?.takeIf(String::isNotEmpty)?.let { add(Condition("desc", it)) }
                }
                if (conditions.isEmpty()) continue
                val plan = LocatePlan(
                    family = if (group.request.scope == CodeGenRequest.Scope.COLLECTION) LocatePlan.Family.SELF else LocatePlan.Family.SELF_INDEX,
                    selector = SelectorSpec(conditions),
                    steps = if (group.request.scope == CodeGenRequest.Scope.COLLECTION) emptyList() else listOf(PlanStep.Index(0)),
                    cardinality = if (group.request.scope == CodeGenRequest.Scope.COLLECTION) LocatePlan.Cardinality.ALL else LocatePlan.Cardinality.ONE,
                    receiver = if (family.endsWith("chat-thread")) PlanReceiver.Up(1) else PlanReceiver.Self,
                )
                val survived = group.states.drop(1).count { state -> PlanVerifier(state.root).verify(group.request.forState(state), plan) is Verified }
                if (survived < group.states.size - 1) anySelfFailure = true
            }
            assertTrue("Scenario $family never invalidates a self selector", anySelfFailure)
        }
    }

    private fun nodes(root: SnapshotNode): Sequence<SnapshotNode> = sequence {
        val pending = ArrayDeque<SnapshotNode>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            yield(node)
            for (index in node.childCount() - 1 downTo 0) pending.add(requireNotNull(node.child(index)))
        }
    }

    private fun fingerprint(root: SnapshotNode): String = MessageDigest.getInstance("SHA-256")
        .digest(Gson().toJson(root.snapshot).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
