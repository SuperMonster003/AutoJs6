package org.autojs.plugin.mail.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Freezes the transaction order of the contract: Binder transaction codes follow the declaration
 * order, so a method may only ever be appended, never inserted, removed, or renamed. Version 1
 * declared the first six files; version 2 (plugin roadmap P8) appended two methods to
 * `IMailPlugin` and added `IMailTrigger` / `IMailTriggerCallback`.
 */
class MailAidlOrderTest {

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
            Paths.get("plugin-api", "mail-api", "src", "main", "aidl").resolve(AIDL_PACKAGE_PATH),
        ).map(Path::toAbsolutePath).map(Path::normalize)
        val directory = candidates.firstOrNull(Files::isDirectory)
        assertTrue("Mail AIDL source directory was not found in $candidates", directory != null)
        return checkNotNull(directory)
    }

    private data class Declaration(val oneway: Boolean, val methods: List<String>)

    private companion object {
        val AIDL_PACKAGE_PATH: Path = Paths.get("org", "autojs", "plugin", "mail", "api")

        val FROZEN = linkedMapOf(
            "IMailPlugin.aidl" to Declaration(
                oneway = false,
                methods = listOf(
                    "PluginInfo getInfo()",
                    "Bundle getCapabilities()",
                    "IMailSession openSession(in Bundle account, IMailSessionCallback callback)",
                    "Bundle listProviders()",
                    "Bundle listSavedAccounts()",
                    "IMailTrigger openTrigger(in Bundle options, IMailTriggerCallback callback)",
                    "Bundle listTriggers()",
                ),
            ),
            "IMailSession.aidl" to Declaration(
                oneway = false,
                methods = listOf(
                    "Bundle getStatus()",
                    "String call(in Bundle request, in ParcelFileDescriptor[] descriptors, IMailCallCallback callback)",
                    "void cancel(String requestId)",
                    "IMailWatch watch(in Bundle options, IMailWatchCallback callback)",
                    "void close()",
                ),
            ),
            "IMailCallCallback.aidl" to Declaration(
                oneway = true,
                methods = listOf(
                    "void onProgress(in Bundle progress)",
                    "void onResult(in Bundle response)",
                ),
            ),
            "IMailWatch.aidl" to Declaration(
                oneway = false,
                methods = listOf(
                    "Bundle getStatus()",
                    "void stop()",
                ),
            ),
            "IMailWatchCallback.aidl" to Declaration(
                oneway = true,
                methods = listOf(
                    "void onEvent(long generation, long seq, in Bundle event)",
                ),
            ),
            "IMailSessionCallback.aidl" to Declaration(
                oneway = true,
                methods = listOf(
                    "void onStatus(in Bundle status)",
                ),
            ),
            "IMailTrigger.aidl" to Declaration(
                oneway = false,
                methods = listOf(
                    "Bundle getStatus()",
                    "void update(in Bundle options)",
                    "void stop()",
                ),
            ),
            "IMailTriggerCallback.aidl" to Declaration(
                oneway = true,
                methods = listOf(
                    "void onMail(long generation, long seq, in Bundle event)",
                    "void onStatus(in Bundle status)",
                ),
            ),
        )
    }
}
