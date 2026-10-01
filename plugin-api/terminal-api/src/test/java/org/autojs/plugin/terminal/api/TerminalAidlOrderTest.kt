package org.autojs.plugin.terminal.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Freezes the transaction order of the contract: Binder transaction codes follow the declaration
 * order, so a method may only ever be appended, never inserted, removed or renamed. Contract
 * version 1 declares the two files below (plugin roadmap D20, appendix B.1).
 */
class TerminalAidlOrderTest {

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
            Paths.get("plugin-api", "terminal-api", "src", "main", "aidl").resolve(AIDL_PACKAGE_PATH),
        ).map(Path::toAbsolutePath).map(Path::normalize)
        val directory = candidates.firstOrNull(Files::isDirectory)
        assertTrue("Terminal AIDL source directory was not found in $candidates", directory != null)
        return checkNotNull(directory)
    }

    private data class Declaration(val oneway: Boolean, val methods: List<String>)

    private companion object {
        val AIDL_PACKAGE_PATH: Path = Paths.get("org", "autojs", "plugin", "terminal", "api")

        val FROZEN = linkedMapOf(
            "ITerminalPlugin.aidl" to Declaration(
                oneway = false,
                methods = listOf(
                    "PluginInfo getInfo()",
                    "Bundle getCapabilities()",
                    "String listSessions()",
                    "String openSession(String requestJson)",
                    "boolean closeSession(String sessionId)",
                    "int closeAllSessions()",
                    "void writeInput(String sessionId, in byte[] data)",
                    "Bundle subscribeOutput(String sessionId, String optionsJson)",
                    "void unsubscribeOutput(String subscriptionId)",
                    "Bundle readTranscript(String sessionId, int maxBytes)",
                    "String getEnvironment()",
                    "void registerCallback(ITerminalCallback callback)",
                    "void unregisterCallback(ITerminalCallback callback)",
                ),
            ),
            "ITerminalCallback.aidl" to Declaration(
                oneway = true,
                methods = listOf(
                    "void onSessionsChanged(int count, String sessionsJson)",
                    "void onSessionExited(String sessionId, int exitCode)",
                    "void onOutputOverflow(String sessionId, String subscriptionId, long droppedBytes)",
                ),
            ),
        )
    }
}
