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
 * Independently authored second-iteration holdout, frozen before evaluating the new controller.
 * No construction search, learned weights, or evaluation outputs were used to author these tasks.
 * These four scenario definitions and group IDs are separate from the captured training fixture
 * and first synthetic cohort. Familiar UI concepts overlap (for example, settings and grids);
 * this is not proof that every visual or semantic pattern is absent from training data.
 * They remain synthetic layouts, not evidence of generalization to real applications.
 * Seeds are fixed variations of a scenario, not four additional independent application samples.
 * Ground truth exists only in target identity/path lists, never in accessible node attributes.
 *
 * zh-CN: 在新控制器验收前独立编写并冻结的第二轮留出集. 未根据搜索或模型结果调整场景.
 * 四个新场景族仍为合成布局, 不能作为真实应用泛化证据. 真值仅保存在节点身份及路径列表中.
 */
object ConstructionGeneralizationFixtures {
    private const val APP = "org.autojs.autojs6.fixture.generalization"
    private const val FRAME = "android.widget.FrameLayout"
    private const val COLUMN = "android.widget.LinearLayout"
    private const val TEXT = "android.widget.TextView"
    private const val SWITCH = "android.widget.Switch"
    private const val IMAGE_BUTTON = "android.widget.ImageButton"
    private const val EDIT = "android.widget.EditText"

    private data class Scene(val root: NodeInfoSnapshot, val targets: List<NodeInfoSnapshot>, val mutations: List<String>)
    private data class Scenario(
        val name: String,
        val action: CodeGenRequest.Action,
        val build: (Int, Int, Boolean) -> Scene,
    )

    fun groups(): List<TaskGroup> = listOf(
        Scenario("permission-state", CodeGenRequest.Action.CLICK, ::permissionState),
        Scenario("localized-toolbar", CodeGenRequest.Action.CLICK, ::localizedToolbar),
        Scenario("validated-editor", CodeGenRequest.Action.SET_TEXT("example"), ::validatedEditor),
        Scenario("media-receiver", CodeGenRequest.Action.LONG_CLICK, ::mediaReceiver),
    ).flatMap { scenario ->
        (0..2).flatMap { seed ->
            listOf(false, true).map { collection -> group(scenario, seed, collection) }
        }
    }

    private fun group(scenario: Scenario, seed: Int, collection: Boolean): TaskGroup {
        val scope = if (collection) "collection" else "single"
        val groupId = "construction-generalization-${scenario.name}-seed${seed + 1}-$scope"
        return TaskGroup(
            groupId = groupId,
            source = "fixture",
            app = APP,
            screenFamily = "construction-generalization/${scenario.name}",
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

    /** Check state and enabled decoys change after a settings refresh; intent is the same switches. */
    private fun permissionState(seed: Int, version: Int, collection: Boolean): Scene {
        val targets = ArrayList<NodeInfoSnapshot>()
        fun setting(index: Int, selectedSection: Boolean): NodeInfoSnapshot {
            val toggle = node(SWITCH, "permission_switch", text = "Allow reminders", desc = "Reminder permission").apply {
                clickable = true
                focusable = true
                checkable = true
                checked = (index + seed + version) % 2 == 0
                actionNames = listOf("ACTION_CLICK")
            }
            if (selectedSection) targets += toggle
            return container("permission_row", listOf(label("Reminders", "setting_label"), toggle))
        }
        val desired = (0 until if (collection) 2 else 1).map { setting(it, true) }
        val explanation = node("android.widget.CheckBox", "permission_switch", text = "Ask before sharing").apply {
            checkable = true
            checked = version == 2
            enabled = version > 0
            clickable = enabled
            actionNames = if (enabled) listOf("ACTION_CLICK") else emptyList()
        }
        val section = container("notification_permissions", if (version == 1) listOf(explanation) + desired.reversed()
            else desired + explanation)
        val otherSections = (0 until 5 + seed).map { index ->
            container("settings_section", listOf(label("Profile ${index + 1}", "section_title"), setting(index, false)))
        }.toMutableList()
        otherSections.add(2 + version % 2, section)
        return Scene(page("Permissions", otherSections), targets, when (version) {
            0 -> emptyList()
            1 -> listOf("toggle-existing-switch-values", "enable-checkbox", "move-notification-section")
            else -> listOf("toggle-switch-values-again", "check-checkbox", "restore-section-order")
        })
    }

    /** A compact toolbar becomes a labelled toolbar in another locale, then returns to icon mode. */
    private fun localizedToolbar(seed: Int, version: Int, collection: Boolean): Scene {
        val targets = ArrayList<NodeInfoSnapshot>()
        val descriptions = listOf("Save for later", "Enregistrer pour plus tard", "Guardar para despues")
        fun toolbar(id: String, count: Int, selectedSection: Boolean): NodeInfoSnapshot {
            val icons = (0 until count).map { index ->
                val icon = node(IMAGE_BUTTON, "toolbar_action", desc = descriptions[version]).apply {
                    clickable = true
                    focusable = true
                    actionNames = listOf("ACTION_CLICK")
                }
                if (selectedSection) targets += icon
                val children = if (version == 1) listOf(icon, label("Enregistrer", "action_label")) else listOf(icon)
                container("toolbar_item", children).apply { drawingOrder = index }
            }
            val label = label(listOf("Reading list", "Liste de lecture", "Lista de lectura")[version], "toolbar_title")
            val content = if (version == 2) icons.reversed() + label else listOf(label) + icons
            return container(id, content)
        }
        val history = (0 until 4 + seed).map { toolbar("history_toolbar", 2, false) }
        val main = toolbar("reading_toolbar", if (collection) 2 else 1, true)
        val children = history.toMutableList().apply { add(1 + (version + seed) % 3, main) }
        return Scene(page("Library", children), targets, when (version) {
            0 -> emptyList()
            1 -> listOf("switch-to-french", "show-toolbar-action-labels", "move-reading-toolbar")
            else -> listOf("switch-to-spanish", "hide-toolbar-action-labels", "reverse-toolbar-items")
        })
    }

    /** Validation adds wrappers and error labels while populated fields stay editable. */
    private fun validatedEditor(seed: Int, version: Int, collection: Boolean): Scene {
        val targets = ArrayList<NodeInfoSnapshot>()
        fun field(index: Int, selectedSection: Boolean): NodeInfoSnapshot {
            val input = node(EDIT, "contact_value", text = when (version) {
                0 -> "person${seed + index}@example.test"
                1 -> "person${seed + index}+work@example.test"
                else -> "person${seed + index}+home@example.test"
            }, desc = if (version == 1) "Contact address, check spelling" else "Contact address").apply {
                editable = true
                focusable = true
                clickable = true
                actionNames = listOf("ACTION_SET_TEXT", "ACTION_CLICK")
            }
            if (selectedSection) targets += input
            val decoration = label(if (version == 1) "Check spelling" else "Email address", "field_hint")
            val content = if (version == 1) container(null, listOf(decoration, input)) else input
            return container("contact_field", if (version == 1) listOf(content) else listOf(decoration, content))
        }
        val fields = (0 until if (collection) 2 else 1).map { field(it, true) }
        val addContact = node("android.widget.Button", "contact_value", text = "Add contact").apply {
            clickable = true
            focusable = true
            actionNames = listOf("ACTION_CLICK")
        }
        val form = container("contact_editor", if (version == 2) listOf(addContact) + fields.reversed() else fields + addContact)
        val previews = (0 until 6 + seed).map { index -> container("contact_preview", listOf(field(index, false))) }.toMutableList()
        previews.add(1 + version, form)
        return Scene(page("Contacts", previews), targets, when (version) {
            0 -> emptyList()
            1 -> listOf("populate-edited-addresses", "insert-validation-wrapper", "show-validation-messages")
            else -> listOf("populate-addresses-again", "remove-validation-wrapper", "reverse-editor-fields")
        })
    }

    /** Grid captions are selected; long press belongs to their cards and survives grid reflow. */
    private fun mediaReceiver(seed: Int, version: Int, collection: Boolean): Scene {
        val targets = ArrayList<NodeInfoSnapshot>()
        fun card(index: Int, selectedSection: Boolean): NodeInfoSnapshot {
            val caption = label("Recording ${(index + seed) % 2 + 1}", "media_caption")
            if (selectedSection) targets += caption
            val thumbnail = node("android.widget.ImageView", "media_thumbnail", desc = "Video thumbnail")
            val badge = label(if (version == 1) "Offline" else "Video", "media_badge")
            val children = if (version == 1) listOf(badge, caption, thumbnail) else listOf(thumbnail, caption, badge)
            return container("media_card", children).apply {
                longClickable = true
                clickable = true
                focusable = true
                selected = (index + version) % 2 == 0
                actionNames = listOf("ACTION_CLICK", "ACTION_LONG_CLICK")
            }
        }
        val downloaded = (0 until if (collection) 2 else 1).map { card(it, true) }
        val grid = container("downloaded_media", if (version == 2) downloaded.reversed() else downloaded).apply {
            className = "android.widget.GridLayout"
            columnCount = if (version == 1) 1 else 2 + seed
        }
        val albums = (0 until 5 + seed).map { album ->
            container("media_album", (0 until 3 + seed).map { card(it + album, false) })
        }.toMutableList()
        albums.add(1 + version, grid)
        return Scene(page("Media", albums), targets, when (version) {
            0 -> emptyList()
            1 -> listOf("reflow-media-grid", "move-caption-within-card", "toggle-card-selection")
            else -> listOf("restore-wide-media-grid", "reverse-downloaded-cards", "toggle-card-selection-again")
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

class ConstructionGeneralizationFixturesTest {
    @Test
    fun everyFrozenGroupHasThreeStatesAndResolvableTargetsWithoutTruthMarkers() {
        val groups = ConstructionGeneralizationFixtures.groups()
        assertEquals(24, groups.size)
        assertEquals(24, groups.map { it.groupId }.distinct().size)
        assertEquals(4, groups.map { it.screenFamily }.distinct().size)
        assertTrue(groups.map { it.groupId }.toSet().intersect(ConstructionBottleneckFixtures.groups().map { it.groupId }.toSet()).isEmpty())
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
        val first = ConstructionGeneralizationFixtures.groups()
        val second = ConstructionGeneralizationFixtures.groups()
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
        for (group in ConstructionGeneralizationFixtures.groups()) {
            val (anchorId, inner) = when (group.screenFamily.substringAfterLast('/')) {
                "permission-state" -> "notification_permissions" to SelectorSpec(Condition("className", "android.widget.Switch"))
                "localized-toolbar" -> "reading_toolbar" to SelectorSpec(Condition("className", "android.widget.ImageButton"))
                "validated-editor" -> "contact_editor" to SelectorSpec(Condition("editable", true))
                "media-receiver" -> "downloaded_media" to SelectorSpec(Condition("id", "media_caption"))
                else -> error("Unknown frozen scenario")
            }
            val collection = group.request.scope == CodeGenRequest.Scope.COLLECTION
            val receiver = if (group.screenFamily.endsWith("media-receiver")) PlanReceiver.Up(1) else PlanReceiver.Self
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
