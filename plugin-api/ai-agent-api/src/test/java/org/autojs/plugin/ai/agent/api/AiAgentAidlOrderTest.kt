package org.autojs.plugin.ai.agent.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Freezes the transaction order of contract version 1: Binder transaction codes follow the
 * declaration order, so a method may only ever be appended, never inserted, removed, or renamed.
 */
class AiAgentAidlOrderTest {

    @Test
    fun declaredMethodsKeepTheirFrozenOrderAndOnewayFlags() {
        val directory = locateAidlSourceDirectory()
        val actualFileNames = Files.list(directory).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".aidl") }
                .map { path -> path.fileName.toString() }
                .sorted()
                .toList()
        }
        assertEquals(FROZEN.keys.sorted(), actualFileNames)

        FROZEN.forEach { (fileName, expected) ->
            val source = directory.resolve(fileName).toFile().readText(StandardCharsets.UTF_8)
            val declaration = parse(source)
            assertEquals("oneway flag of $fileName", expected.oneway, declaration.oneway)
            assertEquals("method order of $fileName", expected.methods, declaration.methods)
        }
    }

    private fun parse(source: String): Declaration {
        val withoutComments = source
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//.*"), "")
        val body = withoutComments.substringAfter("{").substringBeforeLast("}")
        val methods = body.split(';')
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
        val oneway = Regex("\\boneway\\s+interface\\b").containsMatchIn(withoutComments)
        return Declaration(oneway, methods)
    }

    private fun locateAidlSourceDirectory(): Path {
        val candidates = listOf(
            Paths.get("src", "main", "aidl").resolve(AIDL_PACKAGE_PATH),
            Paths.get("plugin-api", "ai-agent-api", "src", "main", "aidl").resolve(AIDL_PACKAGE_PATH),
        ).map(Path::toAbsolutePath).map(Path::normalize)
        val directory = candidates.firstOrNull(Files::isDirectory)
        assertTrue("AiAgent AIDL source directory was not found in $candidates", directory != null)
        return checkNotNull(directory)
    }

    private data class Declaration(val oneway: Boolean, val methods: List<String>)

    private companion object {
        val AIDL_PACKAGE_PATH: Path = Paths.get("org", "autojs", "plugin", "ai", "agent", "api")

        val FROZEN = linkedMapOf(
            "IAiAgentPlugin.aidl" to Declaration(
                oneway = false,
                methods = listOf(
                    "PluginInfo getInfo()",
                    "Bundle getCapabilities()",
                    "IAiAgentLink attach(in Bundle config, IAiAgentModelBroker modelBroker, IHostCapabilityBroker capabilityBroker, IAiAgentLinkCallback callback)",
                ),
            ),
            "IAiAgentLink.aidl" to Declaration(
                oneway = false,
                methods = listOf(
                    "Bundle getStatus()",
                    "Bundle startRun(in Bundle request, IAiAgentRunCallback callback)",
                    "Bundle respond(in Bundle response)",
                    "void cancelRun(in Bundle ref)",
                    "Bundle listRuns(in Bundle query)",
                    "Bundle getRun(in Bundle ref)",
                    "Bundle listPresets(in Bundle query)",
                    "void updateConfig(in Bundle config)",
                    "void detach(in Bundle reason)",
                ),
            ),
            "IAiAgentLinkCallback.aidl" to Declaration(
                oneway = true,
                methods = listOf(
                    "void onStatus(in Bundle status)",
                    "void onEvent(in Bundle event)",
                ),
            ),
            "IAiAgentRunCallback.aidl" to Declaration(
                oneway = true,
                methods = listOf(
                    "void onRunEvent(in Bundle event)",
                ),
            ),
            "IAiAgentModelBroker.aidl" to Declaration(
                oneway = false,
                methods = listOf(
                    "Bundle getBrokerInfo()",
                    "void listTargets(in Bundle request, IAiAgentModelCallback callback)",
                    "void generate(in Bundle request, IAiAgentModelCallback callback)",
                    "void cancel(in Bundle ref)",
                    "void destroy(in Bundle reason)",
                ),
            ),
            "IAiAgentModelCallback.aidl" to Declaration(
                oneway = true,
                methods = listOf(
                    "void onEvent(in Bundle event)",
                ),
            ),
        )
    }
}
