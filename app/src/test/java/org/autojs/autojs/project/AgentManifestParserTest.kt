package org.autojs.autojs.project

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract
import org.junit.Assert.*
import org.junit.Test

class AgentManifestParserTest {
    @Test fun generatedSourceExportHeaderRegistersAsSensitiveAndAlwaysConfirmed() {
        val prefix = "/**\n * @agent\n * @description Generated JavaScript 0123456789abcdef\n * @risk sensitive\n * @confirm before-run\n * @timeout 60000\n */\n"
        val source = "/**\n * @agent\n * @description Untrusted later header\n * @risk readonly\n * @confirm never\n */\nconsole.log(42);"
        val manifest = AgentManifestParser.parseHeader((prefix + source).toByteArray(), "agent-generated-0123456789abcdef.js")!!
        assertEquals("sensitive", manifest.risk)
        assertEquals("before-run", manifest.confirm)
        assertEquals(60000L, manifest.timeoutMs)
        assertEquals("Generated JavaScript 0123456789abcdef", manifest.description)
        assertFalse(manifest.parameters["additionalProperties"].asBoolean)
    }
    @Test fun projectDefaultsAndSensitiveConfirmationFollowTheManifestContract() {
        val manifest = project("""{"description":"在美团点咖啡","risk":"sensitive","confirm":"never"}""", "Coffee Project")
        assertEquals("coffee-project", manifest.id)
        assertEquals("before-run", manifest.confirm)
        assertEquals(60_000L, manifest.timeoutMs)
        assertEquals("object", manifest.parameters["type"].asString)
        assertFalse(manifest.parameters["additionalProperties"].asBoolean)
        assertEquals(emptyList<String>(), manifest.tags)
        assertEquals("main.js", AgentManifestParser.projectMain("""{"agent":{"description":"x"}}"""))
    }

    @Test fun missingAndInvalidRegistrationFieldsFailClosedWithoutLeakingData() {
        assertNull(AgentManifestParser.parseProject("{}", "p"))
        assertNull(AgentManifestParser.parseProject("""{"agent":null}""", "p"))
        listOf(
            "{}", """{"description":""}""", """{"description":7}""",
            """{"description":"private","risk":"admin"}""",
            """{"description":"private","timeoutMs":0}""",
            """{"description":"private","timeoutMs":300001}""",
            """{"description":"private","unknown":1}""",
        ).forEach { json ->
            val error = assertThrows(AgentManifestException::class.java) { project(json) }
            assertEquals("SCRIPT_NOT_REGISTERED", error.code)
            assertFalse(error.message.orEmpty().contains("private"))
        }
        assertThrows(AgentManifestException::class.java) {
            AgentManifestParser.parseProject("""{"agent":{"description":"a","description":"b"}}""", "p")
        }
    }

    @Test fun headerParsesDefaultsEnumsRequiredValuesAndDeclarativeResult() {
        val manifest = header("""
            /**
             * @agent
             * @description 清理下载目录的旧安装包
             * @param {integer} [days=30] 保留天数
             * @param {string} [dir=/sdcard/Download] 目录
             * @param {boolean} [dryRun=false] 只统计
             * @param {string=a|b|c} choice 选择
             * @result {object} { removed: integer, freedBytes: integer }
             * @risk normal
             * @confirm before-run
             * @timeout 120000
             * @example 清理旧安装包
             * @tag 清理
             */
            console.log("source must not enter the manifest");
        """)
        val arguments = AgentManifestParser.validateArguments(manifest, json("""{"choice":"b"}"""))
        assertEquals(30, arguments["days"].asInt)
        assertEquals("/sdcard/Download", arguments["dir"].asString)
        assertFalse(arguments["dryRun"].asBoolean)
        assertEquals("b", arguments["choice"].asString)
        assertEquals(120_000L, manifest.timeoutMs)
        assertEquals("integer", manifest.result!!.getAsJsonObject("properties").getAsJsonObject("removed")["type"].asString)
        assertEquals(listOf("清理"), manifest.tags)
        assertEquals("before-run", manifest.confirm)
    }

    @Test fun headerMustBeFirstLeadingBlockAndMustEndInsideEightKiB() {
        assertNull(AgentManifestParser.parseHeader("/* ordinary */\n/**\n * @agent\n * @description later\n */".toByteArray(), "p"))
        assertNull(AgentManifestParser.parseHeader("run();\n/**\n * @agent\n * @description later\n */".toByteArray(), "p"))
        val truncated = "/**\n * @agent\n * @description " + "x".repeat(ThreeStoveAgentContract.MAX_MANIFEST_HEADER_BYTES) + "\n */"
        assertThrows(AgentManifestException::class.java) { AgentManifestParser.parseHeader(truncated.toByteArray(), "p") }
        val prefix = "/**\n * @agent\n * @description valid\n */\n"
        val padding = " ".repeat(ThreeStoveAgentContract.MAX_MANIFEST_HEADER_BYTES - prefix.toByteArray().size - 1)
        // The byte cap cuts a non-ASCII character after the complete header; it must not invalidate the header.
        assertEquals("valid", AgentManifestParser.parseHeader((prefix + padding + "文").toByteArray(), "p")!!.description)
        assertEquals("Unicode 😀", header("/**\n * @agent\n * @description Unicode 😀\n */").description)
    }

    @Test fun duplicateParamsTagsAndInvalidParameterSyntaxAreRejected() {
        val variants = listOf(
            "@param {integer} value\n * @param {string} value",
            "@param {array} values",
            "@param {boolean} [enabled=yes]",
            "@param {integer} [count=1.5]",
            "@param {string=a|b} [choice=c]",
            "@timeout 123\n * @timeout 456",
        )
        variants.forEach { extra ->
            assertThrows(AgentManifestException::class.java) {
                header("/**\n * @agent\n * @description valid\n * $extra\n */")
            }
        }
    }

    @Test fun schemaSubsetValidatesTypesBoundsDefaultsAndUnknownArguments() {
        val manifest = project("""{
            "description":"bounded",
            "parameters":{"type":"object","properties":{
                "count":{"type":"integer","minimum":1,"maximum":5,"default":3},
                "text":{"type":"string","minLength":1,"maxLength":1},
                "flag":{"type":"boolean","default":false},
                "ratio":{"type":"number","enum":[0.5,1]}
            },"required":["text","ratio"]}
        }""")
        val original = json("""{"text":"😀","ratio":1.0}""")
        val checked = AgentManifestParser.validateArguments(manifest, original)
        assertEquals(3, checked["count"].asInt)
        assertFalse(original.has("count"))
        listOf(
            """{"text":"ab","ratio":1}""", """{"text":"","ratio":1}""",
            """{"text":"a","ratio":"1"}""", """{"text":"a","ratio":2}""",
            """{"text":"a","ratio":1,"count":1.5}""", """{"text":"a","ratio":1,"count":6}""",
            """{"text":"a","ratio":1,"flag":"false"}""", """{"text":"a","ratio":1,"extra":1}""",
            """{"text":"a"}""", """{"text":null,"ratio":1}""",
        ).forEach { input ->
            val error = assertThrows(AgentManifestException::class.java) {
                AgentManifestParser.validateArguments(manifest, json(input))
            }
            assertEquals("TOOL_ARGUMENTS_INVALID", error.code)
        }
    }

    @Test fun unsupportedSchemaConstraintsAndInvalidDefaultsCannotBeSilentlyIgnored() {
        val schemas = listOf(
            """{"type":"array"}""",
            """{"type":"object","additionalProperties":true}""",
            """{"type":"object","properties":{"x":{"type":"object"}}}""",
            """{"type":"object","properties":{"x":{"type":"string","pattern":"x"}}}""",
            """{"type":"object","properties":{"x":{"type":"integer","default":"3"}}}""",
            """{"type":"object","properties":{"x":{"type":"integer","minimum":3,"maximum":1}}}""",
            """{"type":"object","properties":{"x":{"type":"string","minLength":3,"maxLength":1}}}""",
            """{"type":"object","properties":{"x":{"type":"integer","enum":[1,1.0]}}}""",
            """{"type":"object","properties":{"x":{"type":"string","enum":["a"],"default":"b"}}}""",
            """{"type":"object","required":["missing"]}""",
            """{"type":"object","properties":{"x":{"type":"number","default":1e1000000}}}""",
        )
        schemas.forEach { schema ->
            assertThrows(AgentManifestException::class.java) { project("""{"description":"x","parameters":$schema}""") }
        }
    }

    @Test fun defaultsAlsoCountTowardTheArgumentByteLimit() {
        val manifest = project("""{"description":"x","parameters":{"type":"object","properties":{"x":{"type":"string","default":"${"x".repeat(16384)}"}}}}""")
        assertThrows(AgentManifestException::class.java) { AgentManifestParser.validateArguments(manifest, JsonObject()) }
    }

    @Test fun rawManifestGsonRoundTripPreservesOmittedIdsAndStructuredParameters() {
        val gson = Gson()
        val manifest = gson.fromJson("""{
            "description":"Coffee 中文",
            "parameters":{"type":"object","properties":{"count":{"type":"integer","default":2}}}
        }""", AgentManifest::class.java)
        assertNull("An omitted ID must stay absent until catalog normalization", manifest.id)
        assertEquals("Coffee 中文", manifest.description)
        assertTrue(manifest.parameters["properties"].isJsonObject)
        val encoded = gson.toJson(manifest)
        assertFalse(json(encoded).has("id"))
        assertFalse(manifest.toJsonObject().has("id"))
        val normalized = project(encoded, "coffee")
        assertEquals("coffee", normalized.id)
        assertEquals(2, AgentManifestParser.validateArguments(normalized, JsonObject())["count"].asInt)
        assertEquals("", gson.fromJson("""{"id":"","description":"invalid"}""", AgentManifest::class.java).id)
        assertThrows(AgentManifestException::class.java) { project("""{"id":"","description":"invalid"}""") }
    }

    @Test fun fuzzyMainAliasesFollowProjectConfigWithoutAcceptingConflicts() {
        assertEquals("app.js", AgentManifestParser.projectMain("""{"mainName":"app.js"}"""))
        assertEquals("entry.ts", AgentManifestParser.projectMain("""{"Main-Script":"entry.ts"}"""))
        assertEquals("app.js", AgentManifestParser.projectMain("""{"main":"app.js","MAIN_FILE_NAME":"app.js"}"""))
        assertThrows(AgentManifestException::class.java) {
            AgentManifestParser.projectMain("""{"main":"a.js","mainName":"b.js"}""")
        }
        assertThrows(AgentManifestException::class.java) {
            AgentManifestParser.projectMain("""{"main":"a.js","MAIN-SCRIPT":"b.js"}""")
        }
    }

    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
    private fun project(agent: String, name: String = "script") = AgentManifestParser.parseProject("""{"agent":$agent}""", name)!!
    private fun header(value: String) = AgentManifestParser.parseHeader(value.trimIndent().toByteArray(Charsets.UTF_8), "script")!!
}
