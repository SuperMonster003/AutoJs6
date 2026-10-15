package org.autojs.plugin.batchrename.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Freezes the transaction order of the contract: Binder transaction codes follow the declaration order, so a method may
 * only ever be appended, never inserted, removed or renamed. The plugin's device contract test calls `getInfo()` and
 * `plan()` by these codes.
 *
 * zh-CN: 冻结契约的事务顺序: Binder 事务号按声明顺序分配, 因此方法只能追加, 不能插入, 删除或改名. 插件的设备契约测试按这些事务号
 * 调用 `getInfo()` 与 `plan()`.
 */
class BatchRenameAidlOrderTest {

    @Test
    fun declaredMethodsKeepTheirFrozenOrder() {
        val directory = locateAidlSourceDirectory()
        val files = Files.list(directory).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".aidl") }.map { it.fileName.toString() }.sorted().toList()
        }
        assertEquals(listOf("IBatchRenamePlugin.aidl"), files)
        val source = directory.resolve("IBatchRenamePlugin.aidl").toFile().readText(StandardCharsets.UTF_8)
        val withoutComments = source
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//.*"), "")
        assertTrue("the contract is synchronous", !Regex("\\boneway\\b").containsMatchIn(withoutComments))
        val methods = withoutComments.substringAfter("{").substringBeforeLast("}")
            .split(';')
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
        assertEquals(
            listOf(
                "org.autojs.plugin.common.api.PluginInfo getInfo()",
                "Bundle plan(in Bundle request)",
            ),
            methods,
        )
    }

    private fun locateAidlSourceDirectory(): Path {
        val packagePath = Paths.get("org", "autojs", "plugin", "batchrename", "api")
        val candidates = listOf(
            Paths.get("src", "main", "aidl").resolve(packagePath),
            Paths.get("plugin-api", "batch-rename-api", "src", "main", "aidl").resolve(packagePath),
        ).map(Path::toAbsolutePath).map(Path::normalize)
        return checkNotNull(candidates.firstOrNull(Files::isDirectory)) { "AIDL source directory was not found in $candidates" }
    }
}
