package org.autojs.plugin.tool.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Freezes the transactions of the contract: Binder transaction codes follow the declaration order, so a method may only
 * ever be appended, never inserted, removed or renamed, and the oneway flags may not change (a oneway call returns
 * before the plugin runs it).
 *
 * zh-CN: 冻结契约的事务: Binder 事务号按声明顺序分配, 因此方法只能追加, 不能插入, 删除或改名, oneway 标志也不能改变.
 */
class PluginToolAidlOrderTest {

    @Test
    fun theContractHasThreeInterfaces() {
        val files = Files.list(aidlDirectory()).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".aidl") }.map { it.fileName.toString() }.sorted().toList()
        }
        assertEquals(listOf("IPluginToolCallback.aidl", "IPluginToolOutputTree.aidl", "IPluginToolProvider.aidl"), files)
    }

    @Test
    fun theProviderKeepsItsFrozenOrderAndOnlyCancelIsOneway() {
        val source = withoutComments("IPluginToolProvider.aidl")
        assertFalse("the provider interface itself is synchronous", Regex("\\boneway\\s+interface\\b").containsMatchIn(source))
        assertEquals(
            listOf(
                "org.autojs.plugin.common.api.PluginInfo getInfo()",
                "Bundle getCatalog(in Bundle request)",
                "Bundle invoke(in Bundle request, in ParcelFileDescriptor[] inputs, IPluginToolCallback callback)",
                "oneway void cancel(in Bundle request)",
            ),
            methods(source),
        )
    }

    @Test
    fun theCallbackIsOnewayAndKeepsItsFrozenOrder() {
        val source = withoutComments("IPluginToolCallback.aidl")
        assertTrue("callbacks never block the plugin", Regex("\\boneway\\s+interface\\s+IPluginToolCallback\\b").containsMatchIn(source))
        assertEquals(
            listOf(
                "void onProgress(in Bundle update)",
                "void onResult(in Bundle result, in ParcelFileDescriptor[] outputs)",
                "void onError(in Bundle error)",
            ),
            methods(source),
        )
    }

    @Test
    fun theOutputTreeIsSynchronousAndKeepsItsFrozenOrder() {
        val source = withoutComments("IPluginToolOutputTree.aidl")
        assertFalse("the plugin waits for each answer", Regex("\\boneway\\b").containsMatchIn(source))
        assertEquals(
            listOf(
                "Bundle createDirectory(String relativePath)",
                "ParcelFileDescriptor openFile(String relativePath, long expectedBytes, out Bundle answer)",
            ),
            methods(source),
        )
    }

    private fun withoutComments(file: String): String = aidlDirectory().resolve(file).toFile().readText(StandardCharsets.UTF_8)
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//.*"), "")

    private fun methods(source: String): List<String> = source.substringAfter("{").substringBeforeLast("}")
        .split(';')
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotEmpty() }

    private fun aidlDirectory(): Path {
        val packagePath = Paths.get("org", "autojs", "plugin", "tool", "api")
        val candidates = listOf(
            Paths.get("src", "main", "aidl").resolve(packagePath),
            Paths.get("plugin-api", "plugin-tool-api", "src", "main", "aidl").resolve(packagePath),
        ).map(Path::toAbsolutePath).map(Path::normalize)
        return checkNotNull(candidates.firstOrNull(Files::isDirectory)) { "AIDL source directory was not found in $candidates" }
    }
}
