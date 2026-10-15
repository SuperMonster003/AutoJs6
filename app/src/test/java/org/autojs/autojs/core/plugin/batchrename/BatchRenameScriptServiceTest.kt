package org.autojs.autojs.core.plugin.batchrename

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.batchrename.api.BatchRenameOptionKeys
import org.autojs.plugin.batchrename.api.BatchRenameValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The script API service of `files.batchRename` (plugin roadmap P3.2) with a stand-in planner and the real executor on
 * a temporary directory. The planner does plain substring replacement and reports conflicts with existing names like
 * the plugin.
 * zh-CN: `files.batchRename` 的脚本 API 服务, 使用替身方案器与临时目录上的真实执行器. 替身只做普通子串替换, 并像插件一样报告与既有名称的冲突.
 */
class BatchRenameScriptServiceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val root: File by lazy { folder.newFolder("work") }
    private val executor by lazy { BatchRenameExecutor(BatchRenameJournalStore(File(folder.root, "journal")), NoEnvironment, clock = { ++tick }) }
    private var tick = 1_000L
    private val requests = mutableListOf<BatchRenamePlanRequest>()
    private val service by lazy { BatchRenameScriptService(executor = { executor }, planner = ::fakePlan, locale = { "en-US" }, timeZone = { "UTC" }, clock = { 42L }) }

    private fun file(name: String, text: String = name) = File(root, name).apply {
        parentFile.mkdirs()
        writeText(text)
    }

    private fun options(text: String) = BatchRenameScriptArguments.options(JsonParser.parseString(text), null)

    private fun names(directory: File = root) = directory.list().orEmpty().sorted()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.items() = this["items"] as List<Map<String, Any?>>

    private fun expectCode(code: String, block: suspend () -> Unit) {
        try {
            runBlocking { block() }
        } catch (e: BatchRenameScriptException) {
            assertEquals(e.message, code, e.code)
            return
        }
        fail("expected $code")
    }

    private fun fakePlan(request: BatchRenamePlanRequest): BatchRenamePlanAnswer {
        requests += request
        val search = request.options[BatchRenameOptionKeys.SEARCH] as String
        val replace = request.options[BatchRenameOptionKeys.REPLACE] as String
        val policy = request.options[BatchRenameOptionKeys.ON_CONFLICT] as String
        val existing = request.parents.associate { it.id to it.existingNames.toSet() }
        val items = request.items.mapIndexed { index, target ->
            val name = target.file.name
            when {
                request.excluded[index] -> BatchRenamePlanAnswerItem(name, BatchRenameValues.STATUS_EXCLUDED, BatchRenameValues.REASON_EXCLUDED_BY_CALLER, false)
                search !in name -> BatchRenamePlanAnswerItem(name, BatchRenameValues.STATUS_UNCHANGED, BatchRenameValues.REASON_NO_MATCH, false)
                else -> {
                    val newName = name.replace(search, replace)
                    val taken = newName in existing.getValue(target.parentId)
                    when {
                        !taken -> BatchRenamePlanAnswerItem(newName, BatchRenameValues.STATUS_RENAME, null, false)
                        policy == BatchRenameValues.CONFLICT_OVERWRITE -> BatchRenamePlanAnswerItem(newName, BatchRenameValues.STATUS_RENAME, BatchRenameValues.REASON_OVERWRITE, true)
                        else -> BatchRenamePlanAnswerItem(newName, BatchRenameValues.STATUS_CONFLICT, BatchRenameValues.REASON_EXISTING_ENTRY, false)
                    }
                }
            }
        }
        val counts = items.groupingBy { it.status }.eachCount()
        val rejected = policy == BatchRenameValues.CONFLICT_FAIL && items.any { it.status == BatchRenameValues.STATUS_CONFLICT }
        return BatchRenamePlanAnswer(rejected, items, counts)
    }

    @Test
    fun `plan shows every item and apply renames them once`() = runBlocking {
        val a = file("IMG_1.jpg")
        file("IMG_2.jpg")
        file("notes.txt")
        val plan = service.plan(listOf(root), options("{\"search\": \"IMG_\", \"replace\": \"Photo_\"}"))
        val request = requests.single()
        assertEquals(listOf("en-US", "UTC", 42L), listOf(request.locale, request.timeZone, request.now))
        assertTrue((plan["id"] as String).matches(Regex("p_[0-9a-f]{32}")))
        assertEquals(false, plan["rejected"])
        assertEquals(mapOf("rename" to 2, "unchanged" to 2, "conflict" to 0, "invalid" to 0, "excluded" to 0), plan["counts"])
        val first = plan.items()[1]
        assertEquals(listOf(a.absolutePath, "IMG_1.jpg", "file", 1, "Photo_1.jpg", "rename", false), listOf(first["path"], first["name"], first["kind"], first["depth"], first["newName"], first["status"], first["excluded"]))
        assertEquals("dir", plan.items()[0]["kind"])
        assertEquals("Photo_", (plan["options"] as Map<*, *>)[BatchRenameOptionKeys.REPLACE])

        val progress = mutableListOf<Pair<Int, Int>>()
        val result = service.apply(plan["id"] as String, null, null) { done, total, item ->
            progress += done to total
            assertEquals("renamed", item["status"])
        }
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        assertEquals(listOf("Photo_1.jpg", "Photo_2.jpg", "notes.txt"), names())
        assertEquals(mapOf("renamed" to 2, "skipped" to 2, "failed" to 0, "stale" to 0, "excluded" to 0), result["counts"])
        assertEquals(File(root, "Photo_1.jpg").path, result.items()[1]["newPath"])
        assertEquals(true, result["undoable"])
        assertEquals(1, requests.size)
        expectCode(BatchRenameScriptCodes.PLAN_STALE) { service.apply(plan["id"] as String, null, null, null) }
        expectCode(BatchRenameScriptCodes.PLAN_STALE) { service.apply("p_" + "0".repeat(32), null, null, null) }
    }

    @Test
    fun `excluding items or changing the policy plans again`() = runBlocking {
        file("a-old.txt")
        file("b-old.txt")
        file("c-new.txt")
        file("c-old.txt")
        val plan = service.plan(listOf(File(root, "a-old.txt"), File(root, "b-old.txt"), File(root, "c-old.txt")), options("{\"search\": \"old\", \"replace\": \"new\"}"))
        assertEquals(true, plan["rejected"])
        expectCode(BatchRenameScriptCodes.PLAN_REJECTED) { service.apply(plan["id"] as String, null, null, null) }
        // A rejected plan did not run and may be applied with other choices.
        val result = service.apply(plan["id"] as String, listOf(false, true, false), BatchRenameValues.CONFLICT_SKIP, null)
        assertEquals(listOf(false, true, false), requests.last().excluded)
        assertEquals("skip", requests.last().options[BatchRenameOptionKeys.ON_CONFLICT])
        assertEquals(listOf("renamed", "excluded", "skipped"), result.items().map { it["status"] })
        assertEquals(listOf("a-new.txt", "b-old.txt", "c-new.txt", "c-old.txt"), names())
        expectCode(BatchRenameScriptCodes.INVALID_OPTIONS) {
            val again = service.plan(listOf(File(root, "b-old.txt")), options("{\"search\": \"old\", \"replace\": \"new\"}"))
            service.apply(again["id"] as String, listOf(false, false), null, null)
        }
    }

    @Test
    fun `overwrite, stale items and an empty batch`() = runBlocking {
        file("keep.txt", "old")
        val source = file("draft.txt", "new")
        val plan = service.plan(listOf(source), options("{\"search\": \"draft\", \"replace\": \"keep\", \"onConflict\": \"overwrite\"}"))
        assertEquals(true, plan.items().single()["overwrites"])
        // The stand-in environment has no Trash: the overwrite fails, the file keeps its name.
        val failed = service.apply(plan["id"] as String, null, null, null)
        assertEquals("failed", failed.items().single()["status"])
        assertEquals(BatchRenameExecutor.REASON_TRASH_FAILED, failed.items().single()["reason"])

        val changing = file("x.txt", "x")
        val second = service.plan(listOf(changing), options("{\"search\": \"x\", \"replace\": \"y\"}"))
        changing.writeText("changed after the plan")
        val stale = service.apply(second["id"] as String, null, null, null)
        assertEquals("stale", stale.items().single()["status"])
        assertEquals(false, stale["undoable"])

        val nothing = service.plan(listOf(changing), options("{\"search\": \"zzz\"}"))
        val empty = service.apply(nothing["id"] as String, null, null, null)
        assertNull(empty["batchId"])
        assertEquals(false, empty["undoable"])
    }

    @Test
    fun `undo restores a script batch once and history lists it`() = runBlocking {
        val dir = File(root, "album").apply { mkdirs() }
        File(dir, "old.jpg").writeText("pixels")
        val plan = service.plan(listOf(dir), options("{\"search\": \"old\", \"replace\": \"new\"}"))
        val result = service.apply(plan["id"] as String, null, null, null)
        val batchId = result["batchId"] as String
        val history = service.history(20)
        assertEquals(batchId, history.first()["batchId"])
        assertEquals("script", history.first()["source"])
        assertEquals(mapOf("renamed" to 1, "notRenamed" to 0, "restored" to 0), history.first()["counts"])
        assertEquals(emptyList<Any>(), service.history(0))

        val undone = service.undo(batchId)
        assertEquals("restored", undone["state"])
        assertEquals(listOf(File(dir, "new.jpg").path to File(dir, "old.jpg").path), undone.items().map { it["path"] to it["newPath"] })
        assertEquals(mapOf("restored" to 1, "stale" to 0, "failed" to 0), undone["counts"])
        assertFalse(undone["undoable"] as Boolean)
        assertEquals("pixels", File(dir, "old.jpg").readText())
        expectCode(BatchRenameScriptCodes.UNDO_UNAVAILABLE) { service.undo(batchId) }
        expectCode(BatchRenameScriptCodes.UNDO_UNAVAILABLE) { service.undo("b_" + "f".repeat(32)) }
    }

    @Test
    fun `a planner that does not match the request is INTERNAL`() {
        file("a.txt")
        val broken = BatchRenameScriptService(executor = { executor }, planner = { BatchRenamePlanAnswer(false, emptyList(), emptyMap()) })
        expectCode(BatchRenameScriptCodes.INTERNAL) { broken.plan(listOf(File(root, "a.txt")), options("{\"search\": \"a\"}")) }
        val invalid = BatchRenameScriptService(executor = { executor }, planner = {
            BatchRenamePlanAnswer(false, listOf(BatchRenamePlanAnswerItem("x/y", BatchRenameValues.STATUS_RENAME, null, false)), emptyMap())
        })
        expectCode(BatchRenameScriptCodes.INTERNAL) { invalid.plan(listOf(File(root, "a.txt")), options("{\"search\": \"a\"}")) }
    }

    @Test
    fun `a script keeps its newest plans`() = runBlocking {
        file("a.txt")
        val ids = (1..BatchRenameScriptService.MAX_PLANS + 1).map {
            service.plan(listOf(File(root, "a.txt")), options("{\"search\": \"zzz\"}"))["id"] as String
        }
        expectCode(BatchRenameScriptCodes.PLAN_STALE) { service.apply(ids.first(), null, null, null) }
        assertEquals(null, service.apply(ids.last(), null, null, null)["batchId"])
    }

    private object NoEnvironment : BatchRenameEnvironment {
        override fun moveToTrash(file: File): String? = null
        override fun restoreFromTrash(id: String, target: File): Boolean = false
        override fun relocateHistory(from: File, to: File) = Unit
        override fun notifyChanged(directories: Collection<File>) = Unit
    }
}
