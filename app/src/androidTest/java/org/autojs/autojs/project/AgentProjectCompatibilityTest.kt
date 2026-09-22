package org.autojs.autojs.project

import android.system.Os
import android.system.OsConstants
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Covers ProjectConfig's real Android resource initialization and filesystem symbolic links. */
class AgentProjectCompatibilityTest {
    @Test fun headerParametersAndResultParseWithAndroidRegex() {
        val manifest = AgentManifestParser.parseHeader("""
            /**
             * @agent
             * @description Android 参数解析
             * @param {integer} [days=7] Age
             * @param {string} [dir=/sdcard/Download] Folder
             * @param {boolean} [dryRun=false] Preview
             * @param {string=a|b} choice Choice
             * @result {object} { removed: integer }
             */
            void 0;
        """.trimIndent().toByteArray(Charsets.UTF_8), "android-header")!!
        val arguments = AgentManifestParser.validateArguments(manifest, json("""{"choice":"b"}"""))
        assertEquals(7, arguments["days"].asInt)
        assertEquals("/sdcard/Download", arguments["dir"].asString)
        assertFalse(arguments["dryRun"].asBoolean)
        assertEquals("b", arguments["choice"].asString)
        assertEquals("integer", manifest.result!!.getAsJsonObject("properties").getAsJsonObject("removed")["type"].asString)
        assertEquals("TOOL_ARGUMENTS_INVALID", assertThrows(AgentManifestException::class.java) {
            AgentManifestParser.validateArguments(manifest, JsonObject())
        }.code)
    }

    @Test fun projectConfigUsesItsActualFuzzyGsonToRetainAgentObjects() {
        // LaunchConfig reads application resources, so this compatibility check needs Android.
        val field = ProjectConfig::class.java.getDeclaredField("sGson").apply { isAccessible = true }
        val gson = field.get(null) as Gson
        val config = gson.fromJson("""{
            "name":"Coffee","Main-Script":"entry.ts",
            "agent":{"description":"Coffee 中文","parameters":{"type":"object","properties":{"count":{"type":"integer","default":2}}}}
        }""", ProjectConfig::class.java)
        assertNotNull(config.agent)
        assertNull("Omitted IDs stay absent in the raw ProjectConfig model", config.agent!!.id)
        assertEquals("Coffee 中文", config.agent!!.description)
        assertTrue(config.agent!!.parameters["properties"].isJsonObject)
        assertEquals("entry.ts", config.mainScriptFileName)
        val encoded = gson.toJson(config)
        assertTrue(json(encoded).getAsJsonObject("agent").has("parameters"))
        assertFalse(json(encoded).getAsJsonObject("agent").has("id"))
        val saved = config.toJson(false)
        assertEquals("entry.ts", json(saved)["Main-Script"].asString)
        assertEquals("entry.ts", AgentManifestParser.projectMain(saved))
        assertFalse(json(saved).getAsJsonObject("agent").has("id"))
        assertEquals("coffee", AgentManifestParser.parseProject(saved, "coffee")!!.id)
        assertEquals(2, AgentManifestParser.validateArguments(
            AgentManifestParser.parseProject(encoded, "coffee")!!, JsonObject())["count"].asInt)
        val ordinary = gson.fromJson("""{"name":"Ordinary","main":"main.js"}""", ProjectConfig::class.java)
        assertNull(ordinary.agent)
        assertFalse("Saving an ordinary project must not opt it into Agent", json(ordinary.toJson(false)).has("agent"))
    }

    @Test fun symbolicLinksToFilesAndProjectsOutsideTheApprovedRootAreRejected() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val fixture = File(cache, "agent-project-${UUID.randomUUID()}")
        check(fixture.mkdir())
        try {
            val work = File(fixture, "work").apply { check(mkdir()) }
            val outside = File(fixture, "outside").apply { check(mkdir()) }
            val local = script(work, "local.js", "local")
            val privateScript = script(outside, "private.js", "private")
            val privateProject = File(outside, "private-project").apply { check(mkdir()) }
            File(privateProject, "project.json").writeText("""{"main":"main.js","agent":{"description":"private project"}}""")
            File(privateProject, "main.js").writeText("void 0;")
            val fileLink = File(work, "linked.js")
            val directoryLink = File(work, "linked-project")
            Os.symlink(privateScript.path, fileLink.path)
            Os.symlink(privateProject.path, directoryLink.path)
            val catalog = AgentScriptCatalog(work, listOf(fixture))
            assertEquals(listOf(local.canonicalPath), catalog.listScripts().map { it.path })
            for (link in listOf(fileLink, directoryLink)) {
                assertEquals("CAPABILITY_DENIED", assertThrows(AgentManifestException::class.java) {
                    catalog.readManifest(link.path)
                }.code)
                assertEquals("CAPABILITY_DENIED", assertThrows(AgentManifestException::class.java) {
                    catalog.resolveForExecution(link.path, JsonObject())
                }.code)
            }
            assertEquals("CAPABILITY_DENIED", assertThrows(AgentManifestException::class.java) {
                catalog.listScripts(roots = listOf(directoryLink.path))
            }.code)
            assertTrue(privateScript.isFile)
            assertTrue(File(privateProject, "main.js").isFile)
        } finally {
            deleteWithoutFollowingLinks(fixture)
        }
    }

    private fun script(directory: File, name: String, description: String) = File(directory, name).apply {
        writeText("/**\n * @agent\n * @description $description\n */\nvoid 0;")
    }

    private fun json(value: String) = JsonParser.parseString(value).asJsonObject

    private fun deleteWithoutFollowingLinks(file: File) {
        // lstat examines the link itself; symbolic directory targets must never be traversed.
        if (OsConstants.S_ISDIR(Os.lstat(file.path).st_mode)) {
            file.listFiles().orEmpty().forEach(::deleteWithoutFollowingLinks)
        }
        check(file.delete()) { "Failed to clean the isolated Agent project test fixture" }
    }
}
