package org.autojs.autojs.core.accessibility.codegen.construction

import org.autojs.autojs.core.accessibility.NodeInfoSnapshot
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.dataset.TaskGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.IdentityHashMap

/**
 * Frozen synthetic holdout, authored without inspecting a policy or its evaluation results.
 * These are not captured application layouts and must be reported separately from existing test data.
 * Every scenario has three fixed seeds, single/collection requests, and two future states.
 * Nothing in the accessible node attributes marks a node as a ground-truth target.
 */
object ConstructionBottleneckFixtures {
    private const val APP = "org.autojs.autojs6.fixture.construction"
    private const val FRAME = "android.widget.FrameLayout"
    private const val COLUMN = "android.widget.LinearLayout"
    private const val TEXT = "android.widget.TextView"
    private const val BUTTON = "android.widget.Button"

    private data class Scene(val root: NodeInfoSnapshot, val targets: List<NodeInfoSnapshot>, val mutations: List<String>)
    private data class Scenario(val name: String, val build: (Int, Int, Boolean) -> Scene)

    fun groups(): List<TaskGroup> {
        val scenarios = listOf(
            Scenario("head-insertion", ::headInsertion),
            Scenario("nearby-anchor", ::nearbyAnchor),
            Scenario("dynamic-attributes", ::dynamicAttributes),
            Scenario("nested-containers", ::nestedContainers),
        )
        return scenarios.flatMap { scenario ->
            (0..2).flatMap { seed ->
                listOf(false, true).map { collection -> group(scenario, seed, collection) }
            }
        }
    }

    private fun group(scenario: Scenario, seed: Int, collection: Boolean): TaskGroup {
        val scopeName = if (collection) "collection" else "single"
        val groupId = "construction-bottleneck-${scenario.name}-seed${seed + 1}-$scopeName"
        val states = (0..2).map { version ->
            val scene = scenario.build(seed, version, collection)
            annotate(scene.root)
            val paths = targetPaths(scene.root, scene.targets)
            val wrapped = SnapshotNode(scene.root)
            val targets = paths.map { path -> path.fold(wrapped) { node, index -> requireNotNull(node.child(index)) } }
            TaskGroup.State(
                stateId = "s$version",
                snapshot = "synthetic/$groupId/s$version.layout.json",
                mutations = scene.mutations,
                targetPaths = paths,
                root = wrapped,
                targets = targets,
            )
        }
        return TaskGroup(
            groupId = groupId,
            source = "fixture",
            app = APP,
            screenFamily = "construction-bottleneck/${scenario.name}",
            request = TaskGroup.Request(
                scope = if (collection) CodeGenRequest.Scope.COLLECTION else CodeGenRequest.Scope.SINGLE,
                attributes = setOf(CodeGenRequest.Attr.ID, CodeGenRequest.Attr.TEXT, CodeGenRequest.Attr.DESC),
                action = CodeGenRequest.Action.CLICK,
                receiver = CodeGenRequest.Receiver.SELF,
            ),
            captureState = 0,
            states = states,
        )
    }

    /** Repeated rows acquire new siblings before the existing report; a status badge also appears. */
    private fun headInsertion(seed: Int, version: Int, collection: Boolean): Scene {
        val count = 14 + seed * 4
        val reportIndex = 4 + seed * 2
        val prefixCount = version * (seed + 1)
        val identities = (-prefixCount until 0).toList() + (0 until count)
        val targets = ArrayList<NodeInfoSnapshot>()
        val rows = identities.mapIndexed { displayIndex, identity ->
            val top = 160 + (displayIndex - reportIndex - prefixCount) * 110
            val heading = if (identity == reportIndex) "Quarterly report ${2024 + seed}" else "Document ${identity + 101}"
            val actions = (0 until if (collection) 2 else 1).map { action ->
                button("Open", "document_action", "Open document", 550 + action * 190, top + 48).also {
                    if (identity == reportIndex) targets += it
                }
            }
            val children = ArrayList<NodeInfoSnapshot>()
            if (version == 2) children += label("Synced", "sync_status", top)
            children += label(heading, "document_name", top + 22)
            children += container(COLUMN, "document_actions", actions, top + 45)
            container(COLUMN, "document_row", children, top)
        }
        return Scene(page("Documents", container("androidx.recyclerview.widget.RecyclerView", "documents", rows, 120)), targets,
            when (version) {
                0 -> emptyList()
                1 -> listOf("prepend-document-rows")
                else -> listOf("prepend-more-document-rows", "insert-sync-status-before-row-heading")
            })
    }

    /** Many peers at the same depth have a nearby, ordinary unique record heading. */
    private fun nearbyAnchor(seed: Int, version: Int, collection: Boolean): Scene {
        val count = when (seed) { 0 -> 40; 1 -> 75; else -> 1000 }
        val accountIndex = count * 3 / 4 + seed
        val prefixCount = version * 3
        val identities = (-prefixCount until 0).toList() + (0 until count)
        val targets = ArrayList<NodeInfoSnapshot>()
        val cards = identities.mapIndexed { displayIndex, identity ->
            // Keep the selected record near the viewport; captured offscreen peers stay offscreen.
            val top = 250 + (displayIndex - accountIndex - prefixCount) * 104
            val open = button("Open", "record_action", "Open record", 550, top + 35)
            val more = button("More", "record_action", "More record actions", 770, top + 35)
            if (identity == accountIndex) {
                targets += open
                if (collection) targets += more
            }
            container(COLUMN, "record_card", listOf(
                label("Account ${18000 + seed * 2000 + identity}", "record_heading", top), open, more,
            ), top)
        }
        return Scene(page("Records", container("androidx.recyclerview.widget.RecyclerView", "records", cards, 120)), targets,
            when (version) {
                0 -> emptyList()
                1 -> listOf("prepend-record-cards")
                else -> listOf("prepend-more-record-cards")
            })
    }

    /** User-visible amounts, descriptions and generated button ids change; the billing panel remains. */
    private fun dynamicAttributes(seed: Int, version: Int, collection: Boolean): Scene {
        val targets = ArrayList<NodeInfoSnapshot>()
        val billingChildren = ArrayList<NodeInfoSnapshot>()
        billingChildren += label("Billing", "panel_heading", 370)
        if (version > 0) billingChildren += label("Updated ${9 + version}:0${seed}", "last_update", 408)
        repeat(if (collection) 2 else 1) { index ->
            val amount = 25 + seed * 9 + version * 7 + index * 3
            val invoice = 7021 + seed * 17 + version * 11 + index
            val action = button("Pay $$amount.00", "payment_${invoice}_${version + 100}", "Pay invoice $invoice", 540 + index * 200, 452)
            billingChildren += action
            targets += action
        }
        val billing = container(COLUMN, "billing_panel", billingChildren, 350)
        val otherPanels = (0 until 7 + seed).map { index ->
            val top = 610 + index * 115
            container(COLUMN, "overview_panel", listOf(
                label("Subscription ${index + 1}", "panel_heading", top),
                button("Pay $${40 + index}.00", "payment_${9000 + index + version * 101}", "Pay subscription", 550, top + 35),
            ), top)
        }.toMutableList()
        otherPanels.add((seed + version * 2) % otherPanels.size, billing)
        otherPanels.forEachIndexed { index, panel -> shiftVertical(panel, 210 + index * 115 - panel.bounds[1]) }
        return Scene(page("Account overview", container(COLUMN, "account_overview", otherPanels, 120)), targets,
            when (version) {
                0 -> emptyList()
                1 -> listOf("update-amount-description-generated-id", "insert-last-update-label", "reorder-panels")
                else -> listOf("update-amount-description-generated-id-again", "reorder-panels-again")
            })
    }

    /** Deep, anonymous wrappers vary while an ordinary archive container identifies the same section. */
    private fun nestedContainers(seed: Int, version: Int, collection: Boolean): Scene {
        val targets = ArrayList<NodeInfoSnapshot>()
        val archiveIndex = 2 + seed
        val sections = (0 until 6 + seed).map { index ->
            val top = 210 + index * 160
            val controls = (0 until if (collection) 2 else 1).map { action ->
                button("Restore", "archive_action", "Restore archive item", 550 + action * 200, top + 62).also {
                    if (index == archiveIndex) targets += it
                }
            }
            var content = container(COLUMN, "archive_actions", controls, top + 50)
            repeat(7 + seed + if (version == 1) 1 else if (version == 2) -1 else 0) {
                content = container(FRAME, null, listOf(content), top + 45)
            }
            container(COLUMN, "archive_section", listOf(
                label("Archive ${2015 + seed * 10 + index}", "archive_heading", top), content,
            ), top, desc = "Archive section ${2015 + seed * 10 + index}")
        }.toMutableList()
        if (version > 0) {
            val moved = sections.removeAt(0)
            sections.add(moved)
        }
        sections.forEachIndexed { index, section -> shiftVertical(section, 210 + index * 160 - section.bounds[1]) }
        return Scene(page("Archived files", container(COLUMN, "archives", sections, 120)), targets,
            when (version) {
                0 -> emptyList()
                1 -> listOf("insert-anonymous-wrapper", "rotate-archive-sections")
                else -> listOf("remove-anonymous-wrapper", "rotate-archive-sections")
            })
    }

    private fun page(title: String, content: NodeInfoSnapshot) = container(FRAME, "screen", listOf(
        label(title, "screen_title", 32), content,
    ), 0).apply { bounds = intArrayOf(0, 0, 1080, 1920) }

    private fun container(type: String, id: String?, children: List<NodeInfoSnapshot>, top: Int, desc: String? = null) =
        node(type, id, top = top, desc = desc).apply {
            this.children = children
            bounds = intArrayOf(0, top, 1080, maxOf(top + 100, children.maxOfOrNull { it.bounds[3] } ?: top + 100))
            scrollable = type.endsWith("RecyclerView")
        }

    private fun label(text: String, id: String, top: Int) = node(TEXT, id, top = top, text = text)

    private fun button(text: String, id: String, desc: String, left: Int, top: Int) =
        node(BUTTON, id, top = top, text = text, desc = desc).apply {
            clickable = true
            focusable = true
            bounds = intArrayOf(left, top, left + 170, top + 44)
            actionNames = listOf("ACTION_CLICK")
        }

    private fun node(type: String, id: String?, top: Int, text: String = "", desc: String? = null) = NodeInfoSnapshot(
        packageName = APP,
        id = id,
        fullId = id?.let { "$APP:id/$it" },
        text = text,
        desc = desc,
        className = type,
        enabled = true,
        bounds = intArrayOf(16, top, 500, top + 34),
    )

    private fun shiftVertical(root: NodeInfoSnapshot, offset: Int) {
        val pending = ArrayDeque<NodeInfoSnapshot>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            node.bounds[1] += offset
            node.bounds[3] += offset
            node.children.forEach(pending::add)
        }
    }

    private fun annotate(root: NodeInfoSnapshot) {
        data class Item(val node: NodeInfoSnapshot, val depth: Int, val index: Int, val parent: NodeInfoSnapshot?)
        val pending = ArrayDeque<Item>()
        pending.add(Item(root, 0, 0, null))
        while (pending.isNotEmpty()) {
            val (node, depth, index, parent) = pending.removeLast()
            node.depth = depth
            node.indexInParent = index
            node.childCount = node.children.size
            node.visibleToUser = node.bounds[3] > 0 && node.bounds[1] < 1920
            node.center = doubleArrayOf((node.bounds[0] + node.bounds[2]) / 2.0, (node.bounds[1] + node.bounds[3]) / 2.0)
            node.boundsInParent = node.bounds.copyOf().also { bounds ->
                if (parent != null) {
                    bounds[0] -= parent.bounds[0]; bounds[2] -= parent.bounds[0]
                    bounds[1] -= parent.bounds[1]; bounds[3] -= parent.bounds[1]
                }
            }
            node.children.forEachIndexed { childIndex, child -> pending.add(Item(child, depth + 1, childIndex, node)) }
        }
    }

    private fun targetPaths(root: NodeInfoSnapshot, targets: List<NodeInfoSnapshot>): List<List<Int>> {
        val wanted = IdentityHashMap<NodeInfoSnapshot, Boolean>().apply { targets.forEach { put(it, true) } }
        val found = IdentityHashMap<NodeInfoSnapshot, List<Int>>()
        val pending = ArrayDeque<Pair<NodeInfoSnapshot, List<Int>>>()
        pending.add(root to emptyList())
        while (pending.isNotEmpty()) {
            val (node, path) = pending.removeLast()
            if (wanted.containsKey(node)) found[node] = path
            node.children.forEachIndexed { index, child -> pending.add(child to (path + index)) }
        }
        require(targets.isNotEmpty() && targets.distinct().size == targets.size)
        return targets.map { requireNotNull(found[it]) { "Synthetic target must be part of the captured tree" } }
    }
}

class ConstructionBottleneckFixturesTest {
    @Test
    fun everySyntheticGroupHasCaptureAndTwoResolvableFutureStates() {
        val groups = ConstructionBottleneckFixtures.groups()
        assertEquals(24, groups.size)
        assertEquals(groups.size, groups.map { it.groupId }.toSet().size)
        assertEquals(4, groups.map { it.screenFamily }.toSet().size)
        for (group in groups) {
            group.requireLabelable()
            assertEquals("fixture", group.source)
            assertTrue(group.screenFamily.startsWith("construction-bottleneck/"))
            assertEquals(0, group.captureState)
            assertEquals(3, group.states.size)
            val expectedTargets = if (group.request.scope == CodeGenRequest.Scope.SINGLE) 1 else 2
            for (state in group.states) {
                assertEquals(expectedTargets, state.targets.size)
                assertEquals(expectedTargets, state.targetPaths.distinct().size)
                state.targetPaths.forEachIndexed { index, path ->
                    val resolved = path.fold(state.root) { node, child -> requireNotNull(node.child(child)) }
                    assertSame(resolved, state.targets[index])
                    assertEquals("android.widget.Button", resolved.className())
                    assertTrue(resolved.enabled() && resolved.clickable())
                }
                for (node in nodes(state.root)) {
                    assertEquals(node.childCount(), node.snapshot.childCount)
                    node.parent()?.let { parent ->
                        assertEquals(parent.depth() + 1, node.depth())
                        assertSame(node, parent.child(node.indexInParent()))
                    }
                    assertTrue(node.snapshot.summary.isEmpty())
                    assertTrue(!node.selected() && !node.accessibilityFocused())
                }
            }
        }
    }

    @Test
    fun fixedSeedsAreDeterministicAndEveryFutureChangesTheCapturedLayout() {
        val first = ConstructionBottleneckFixtures.groups()
        val second = ConstructionBottleneckFixtures.groups()
        assertEquals(first.map { it.groupId }, second.map { it.groupId })
        first.zip(second).forEach { (left, right) ->
            left.states.zip(right.states).forEach { (leftState, rightState) ->
                assertEquals(leftState.targetPaths, rightState.targetPaths)
                assertEquals(fingerprint(leftState.root), fingerprint(rightState.root))
            }
            assertTrue(left.states.first().mutations.isEmpty())
            left.states.drop(1).forEach { state ->
                assertTrue(state.mutations.isNotEmpty())
                assertNotEquals(fingerprint(left.states.first().root), fingerprint(state.root))
            }
        }
    }

    @Test
    fun onlyTwoGroupsHaveLargeTreesAndTruthIsNotStoredInNodeAttributes() {
        val groups = ConstructionBottleneckFixtures.groups()
        val large = groups.filter { nodes(it.states.first().root).count() >= 3000 }
        assertEquals(2, large.size)
        large.forEach { group ->
            assertEquals("construction-bottleneck/nearby-anchor", group.screenFamily)
            group.states.forEach { state -> assertTrue(nodes(state.root).count() in 3000..5000) }
        }
        groups.filterNot(large::contains).forEach { group ->
            group.states.forEach { state -> assertTrue(nodes(state.root).count() < 1000) }
        }
        for (group in groups) {
            for (state in group.states) {
                nodes(state.root).forEach { node ->
                    listOf(node.text(), node.desc().orEmpty(), node.id().orEmpty()).forEach { value ->
                        assertTrue("Truth must not appear in accessible attributes", !value.contains("target", ignoreCase = true))
                        assertTrue(!value.contains("truth", ignoreCase = true))
                    }
                }
            }
            if (group.screenFamily.endsWith("dynamic-attributes")) {
                val capture = group.states.first().targets
                group.states.drop(1).forEach { state ->
                    capture.zip(state.targets).forEach { (before, after) ->
                        assertNotEquals(before.id(), after.id())
                        assertNotEquals(before.text(), after.text())
                        assertNotEquals(before.desc(), after.desc())
                    }
                }
            } else {
                group.states.drop(1).forEach { assertNotEquals(group.states.first().targetPaths, it.targetPaths) }
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

    private fun fingerprint(root: SnapshotNode): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (node in nodes(root)) {
            val value = listOf(node.depth(), node.indexInParent(), node.childCount(), node.className(), node.id(),
                node.text(), node.desc(), node.left(), node.top(), node.right(), node.bottom(), node.clickable()).joinToString("\u0000")
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
