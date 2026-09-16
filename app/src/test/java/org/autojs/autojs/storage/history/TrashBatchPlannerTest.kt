package org.autojs.autojs.storage.history

import org.autojs.autojs.storage.history.TrashBatchPlanner.ConflictPolicy
import org.autojs.autojs.storage.history.TrashBatchPlanner.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashBatchPlannerTest {

    private val root = "/storage/emulated/0/Scripts"
    private val a = Entry("a", "$root/a.js", isDirectory = false)
    private val b = Entry("b", "$root/sub/b.js", isDirectory = false)
    private val d = Entry("d", "$root/dir", isDirectory = true)

    private fun plan(
        entries: List<Entry>,
        targetDir: String? = null,
        policy: ConflictPolicy,
        existing: Set<String> = emptySet(),
    ) = TrashBatchPlanner.plan(
        entries = entries,
        targetDir = targetDir,
        policy = policy,
        exists = { it in existing },
        siblingNames = { dir -> existing.filter { it.substringBeforeLast('/') == dir }.map { it.substringAfterLast('/') } },
    )

    @Test
    fun originalParentsWithoutConflicts() {
        val steps = plan(listOf(a, b, d), policy = ConflictPolicy.SKIP)
        assertEquals(listOf("$root/a.js", "$root/sub/b.js", "$root/dir"), steps.map { it.dest })
        assertTrue(steps.none { it.conflict || it.skipped })
    }

    @Test
    fun targetDirectoryOverridesParents() {
        val steps = plan(listOf(a, b), targetDir = "/sdcard/out/", policy = ConflictPolicy.SKIP)
        assertEquals(listOf("/sdcard/out/a.js", "/sdcard/out/b.js"), steps.map { it.dest })
    }

    @Test
    fun skipPolicyLeavesConflictingItemsInTrash() {
        val steps = plan(listOf(a, b), policy = ConflictPolicy.SKIP, existing = setOf("$root/a.js"))
        assertTrue(steps[0].conflict)
        assertTrue(steps[0].skipped)
        assertFalse(steps[1].conflict)
        assertFalse(steps[1].skipped)
    }

    @Test
    fun overwritePolicyKeepsDestinationButFlagsConflict() {
        val steps = plan(listOf(a), policy = ConflictPolicy.OVERWRITE, existing = setOf("$root/a.js"))
        assertEquals("$root/a.js", steps[0].dest)
        assertTrue(steps[0].conflict)
        assertFalse(steps[0].skipped)
    }

    @Test
    fun autoRenameUsesSiblingsAndEarlierBatchMembers() {
        val steps = plan(listOf(a, a.copy(id = "a2")), policy = ConflictPolicy.AUTO_RENAME, existing = setOf("$root/a.js", "$root/a-1.js"))
        assertEquals("$root/a-2.js", steps[0].dest)
        assertEquals("$root/a-3.js", steps[1].dest)
    }

    @Test
    fun duplicatesInsideBatchNeverShareDestinationEvenWhenOverwriting() {
        val steps = plan(listOf(a, a.copy(id = "a2"), d, d.copy(id = "d2")), policy = ConflictPolicy.OVERWRITE)
        assertEquals("$root/a.js", steps[0].dest)
        assertEquals("$root/a-1.js", steps[1].dest)
        assertEquals("$root/dir", steps[2].dest)
        assertEquals("$root/dir-1", steps[3].dest)
        assertTrue(steps[1].conflict)
        assertFalse(steps[1].skipped)
    }
}
