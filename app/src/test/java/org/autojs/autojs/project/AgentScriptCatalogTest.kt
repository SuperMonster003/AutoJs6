package org.autojs.autojs.project

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class AgentScriptCatalogTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun discoversProjectsAndRegisteredJavaScriptOrTypeScriptOnly() {
        val work = temporary.newFolder("work")
        script(work, "task.js", "A registered task")
        script(work, "typed.ts", "A typed task")
        script(work, "types.d.ts", "Must never run declaration files")
        File(work, "ordinary.js").writeText("console.log('not registered')")
        val project = File(work, "coffee").apply { mkdirs() }
        File(project, "project.json").writeText("""{"name":"Coffee","main":"entry.ts","agent":{"description":"咖啡","risk":"sensitive"}}""")
        File(project, "entry.ts").writeText("const answer: number = 42;")
        val catalog = catalog(work)
        val entries = catalog.listScripts()
        assertEquals(3, entries.size)
        assertEquals(setOf("task", "typed", "coffee"), entries.map { it.id }.toSet())
        val coffee = entries.single { it.kind == "project" }
        assertEquals("before-run", coffee.confirm)
        assertEquals(project.canonicalPath, coffee.path)
        assertEquals(coffee, catalog.readManifest(File(project, "project.json").path))
        val launch = catalog.resolveForExecution(coffee.path, JsonObject())
        assertEquals(project.canonicalPath, launch.launchPath)
        assertEquals(File(project, "entry.ts").canonicalFile, launch.scriptFile)
        assertEquals("task", catalog.listScripts(query = "REGISTERED").single().id)
    }

    @Test fun invalidRegistrationsAreSkippedButExplicitReadsFail() {
        val work = temporary.newFolder()
        File(work, "bad.js").writeText("/**\n * @agent\n * @description private\n * @param {array} value\n */")
        script(work, "good.js", "valid")
        val badProject = File(work, "bad-project").apply { mkdirs() }
        File(badProject, "project.json").writeText("""{"agent":{"description":"private","unknown":true}}""")
        File(badProject, "main.js").writeText("void 0;")
        val catalog = catalog(work)
        assertEquals(listOf("good"), catalog.listScripts().map { it.id })
        for (path in listOf(File(work, "bad.js").path, badProject.path)) {
            val error = assertThrows(AgentManifestException::class.java) { catalog.readManifest(path) }
            assertEquals("SCRIPT_NOT_REGISTERED", error.code)
            assertFalse(error.message.orEmpty().contains("private"))
            assertFalse(error.message.orEmpty().contains(work.path))
        }
    }

    @Test fun scanDepthFourAndAgentSubtreeTotalDepthEightAreIndependent() {
        val work = temporary.newFolder()
        val ordinary4 = nested(work, 4)
        script(ordinary4, "at-four.js", "normal included")
        script(nested(ordinary4, 1), "at-five.js", "normal excluded")
        val agent = File(work, "agent").apply { mkdirs() }
        val agent8 = nested(agent, 7)
        script(agent8, "at-eight.js", "agent included")
        script(nested(agent8, 1), "at-nine.js", "agent excluded")
        val nestedAgent = File(work, "tasks/agent").apply { mkdirs() }
        script(nestedAgent, "nested-agent.js", "ordinary nested agent directory included")
        val catalog = catalog(work)
        assertEquals(setOf("at-four", "at-eight", "nested-agent"), catalog.listScripts().map { it.id }.toSet())
        assertEquals(setOf("at-four", "at-eight", "nested-agent"), catalog.listScripts(roots = listOf(work.path)).map { it.id }.toSet())
        assertEquals(listOf("at-eight"), catalog.listScripts(roots = listOf(agent8.path)).map { it.id })
        assertThrows(AgentManifestException::class.java) { catalog.listScripts(roots = listOf(nested(agent8, 1).path)) }
    }

    @Test fun catalogRespectsEntryCountSerializedByteCapAndRequestedLimit() {
        val work = temporary.newFolder()
        repeat(510) { script(work, "task-${it.toString().padStart(3, '0')}.js", "task") }
        val catalog = catalog(work)
        assertEquals(500, catalog.listScripts().size)
        assertEquals(7, catalog.listScripts(limit = 7).size)
        assertThrows(AgentManifestException::class.java) { catalog.listScripts(limit = 501) }
        val large = temporary.newFolder("large")
        repeat(70) { script(large, "large-${it.toString().padStart(3, '0')}.js", "文".repeat(1700)) }
        val entries = catalog(large).listScripts()
        assertTrue(entries.isNotEmpty() && entries.size < 70)
        val encoded = entries.joinToString(prefix = "[", postfix = "]", separator = ",") { it.toJsonObject().toString() }
        assertTrue(encoded.toByteArray(Charsets.UTF_8).size <= ThreeStoveAgentContract.MAX_SCRIPT_CATALOG_BYTES)
    }

    @Test fun cacheInvalidatesOnMtimeAndReturnedGsonTreesCannotMutateFutureReads() {
        val work = temporary.newFolder()
        val file = script(work, "cache.js", "first", "@param {integer} [count=1]")
        val catalog = catalog(work)
        val first = catalog.readManifest(file.path)
        first.manifest.parameters.getAsJsonObject("properties").getAsJsonObject("count").addProperty("default", 99)
        assertEquals(1, catalog.resolveForExecution(file.path, JsonObject()).arguments["count"].asInt)
        val timestamp = file.lastModified()
        script(work, "cache.js", "other", "@param {integer} [count=2]")
        assertTrue(file.setLastModified(timestamp + 2_000))
        val updated = catalog.readManifest(file.path)
        assertEquals("other", updated.description)
        assertEquals(2, catalog.resolveForExecution(file.path, JsonObject()).arguments["count"].asInt)
        assertTrue(updated.updatedAt > first.updatedAt)
        file.delete()
        assertThrows(AgentManifestException::class.java) { catalog.readManifest(file.path) }
    }

    @Test fun configuredVisibleRootsAreHostApprovedAndRequestsOnlyNarrowThem() {
        val visible = temporary.newFolder("visible")
        val work = File(visible, "work").apply { mkdirs() }
        val downloads = File(visible, "Downloads").apply { mkdirs() }
        script(work, "local.js", "local")
        val extra = script(downloads, "extra.js", "extra")
        val catalog = AgentScriptCatalog(work, listOf(visible))
        assertEquals(listOf("local"), catalog.listScripts().map { it.id })
        assertThrows(AgentManifestException::class.java) { catalog.listScripts(roots = listOf(downloads.path)) }
        catalog.updateConfiguredRoots(listOf(downloads))
        assertEquals(listOf("extra"), catalog.listScripts(roots = listOf(downloads.path)).map { it.id })
        assertEquals(extra.canonicalPath, catalog.readManifest(extra.path).path)
        assertThrows(AgentManifestException::class.java) { catalog.updateConfiguredRoots(listOf(visible)) }
        assertEquals("extra", catalog.readManifest(extra.path).id)
        assertThrows(AgentManifestException::class.java) { catalog.readManifest("../Downloads/extra.js") }
        assertThrows(AgentManifestException::class.java) { catalog.updateConfiguredRoots(listOf(temporary.newFolder("outside"))) }
        val privateDirectory = File(visible, "Android/data/private").apply { mkdirs() }
        assertThrows(AgentManifestException::class.java) { catalog.updateConfiguredRoots(listOf(privateDirectory)) }
        val hidden = File(visible, ".hidden").apply { mkdirs() }
        assertThrows(AgentManifestException::class.java) { catalog.updateConfiguredRoots(listOf(hidden)) }
    }

    @Test fun projectMainCannotEscapeTheProjectEvenIntoAnotherApprovedRoot() {
        val visible = temporary.newFolder()
        val work = File(visible, "work").apply { mkdirs() }
        val project = File(work, "project").apply { mkdirs() }
        script(work, "outside.js", "outside")
        File(project, "project.json").writeText("""{"main":"../outside.js","agent":{"description":"invalid"}}""")
        val catalog = AgentScriptCatalog(work, listOf(visible))
        assertThrows(AgentManifestException::class.java) { catalog.resolveForExecution(project.path, JsonObject()) }
        File(project, "project.json").writeText("""{"main":"${File(work, "outside.js").path.replace("\\", "\\\\")}","agent":{"description":"invalid"}}""")
        assertThrows(AgentManifestException::class.java) { catalog.readManifest(project.path) }
    }

    @Test fun overlappingAdditionalRootsRetainTheirIndependentDepthWithoutDuplicateEntries() {
        val work = temporary.newFolder("work")
        val extra = File(work, "extra").apply { mkdirs() }
        script(extra, "shallow.js", "discovered through both roots")
        script(nested(extra, 4), "deep.js", "outside working depth but within additional root depth")
        val catalog = catalog(work)
        assertEquals(listOf("shallow"), catalog.listScripts().map { it.id })
        catalog.updateConfiguredRoots(listOf(extra))
        assertEquals(setOf("shallow", "deep"), catalog.listScripts().map { it.id }.toSet())
        assertEquals(2, catalog.listScripts().size)
        assertEquals(setOf("shallow", "deep"), catalog.listScripts(roots = listOf(work.path)).map { it.id }.toSet())
        assertEquals(setOf("shallow", "deep"), catalog.listScripts(roots = listOf(extra.path)).map { it.id }.toSet())
    }

    @Test fun symbolicLinksOutsideTheApprovedRootCannotRegisterOrLaunch() {
        val work = temporary.newFolder("work")
        val outside = temporary.newFolder("outside")
        val target = script(outside, "private.js", "private")
        val link = File(work, "linked.js")
        try { Files.createSymbolicLink(link.toPath(), target.toPath()) } catch (error: Exception) {
            assumeNoException("The JVM filesystem does not allow symbolic links", error)
        }
        val catalog = catalog(work)
        assertTrue(catalog.listScripts().isEmpty())
        assertEquals("CAPABILITY_DENIED", assertThrows(AgentManifestException::class.java) {
            catalog.resolveForExecution(link.path, JsonObject())
        }.code)
    }

    @Test fun executionUsesValidatedDefaultsAndRejectsMalformedOrOversizedArguments() {
        val work = temporary.newFolder()
        val file = script(work, "args.js", "arguments", "@param {integer} [days=30]\n * @param {string} name")
        val catalog = catalog(work)
        val invocation = catalog.resolveForExecution(file.path, JsonParser.parseString("""{"name":"Alice"}""").asJsonObject)
        assertEquals(30, invocation.arguments["days"].asInt)
        assertEquals(file.canonicalFile, invocation.scriptFile)
        assertEquals(file.canonicalPath, invocation.launchPath)
        assertEquals("TOOL_ARGUMENTS_INVALID", assertThrows(AgentManifestException::class.java) {
            catalog.resolveForExecution(file.path, JsonObject())
        }.code)
    }

    private fun catalog(work: File) = AgentScriptCatalog(work, listOf(work.parentFile))
    private fun nested(root: File, depth: Int): File {
        var current = root
        repeat(depth) { current = File(current, "level-$it").apply { mkdirs() } }
        return current
    }
    private fun script(directory: File, name: String, description: String, extra: String = ""): File =
        File(directory, name).apply {
            parentFile.mkdirs()
            writeText("/**\n * @agent\n * @description $description\n" +
                (if (extra.isEmpty()) "" else " * $extra\n") + " */\nvoid 0;")
        }
}
