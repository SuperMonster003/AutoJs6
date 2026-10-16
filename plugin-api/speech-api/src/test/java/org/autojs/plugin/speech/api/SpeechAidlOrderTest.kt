package org.autojs.plugin.speech.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Freezes the transaction order of the contract (version 1): Binder transaction codes follow the declaration order, so
 * a method may only ever be appended, never inserted, removed or renamed. The plugin's device contract test calls
 * `getInfo()` by its code.
 *
 * zh-CN: 冻结契约 (版本 1) 的事务顺序: Binder 事务号按声明顺序分配, 因此方法只能追加, 不能插入, 删除或改名. 插件的设备契约测试按事务号
 * 调用 `getInfo()`.
 */
class SpeechAidlOrderTest {

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
            Paths.get("plugin-api", "speech-api", "src", "main", "aidl").resolve(AIDL_PACKAGE_PATH),
        ).map(Path::toAbsolutePath).map(Path::normalize)
        val directory = candidates.firstOrNull(Files::isDirectory)
        assertTrue("speech AIDL source directory was not found in $candidates", directory != null)
        return checkNotNull(directory)
    }

    private data class Declaration(val oneway: Boolean, val methods: List<String>)

    private companion object {
        val AIDL_PACKAGE_PATH: Path = Paths.get("org", "autojs", "plugin", "speech", "api")

        val FROZEN = linkedMapOf(
            "ISpeechPlugin.aidl" to Declaration(
                oneway = false,
                methods = listOf(
                    "PluginInfo getInfo()",
                    "Bundle getCapabilities()",
                    "Bundle listModels(in Bundle query)",
                    "int installModel(in Bundle request, ISpeechProgressCallback callback)",
                    "void cancelOperation(int opId)",
                    "Bundle removeModel(in Bundle request)",
                    "Bundle recognizeFile(in ParcelFileDescriptor audio, in Bundle options)",
                    "Bundle detectVoice(in ParcelFileDescriptor audio, in Bundle options)",
                    "ISpeechSession openSession(in Bundle options, ISpeechSessionCallback callback)",
                    "ISpeechSession openWakeSession(in Bundle options, ISpeechWakeCallback callback, ISpeechSessionCallback listenCallback)",
                    "int synthesizeToFile(in Bundle request, in ParcelFileDescriptor output, ISpeechProgressCallback callback)",
                    "int importModel(in ParcelFileDescriptor archive, in Bundle request, ISpeechProgressCallback callback)",
                    "Bundle setDefaultModel(in Bundle request)",
                    "int refreshCatalog(ISpeechProgressCallback callback)",
                ),
            ),
            "ISpeechSession.aidl" to Declaration(
                oneway = false,
                methods = listOf(
                    "void feed(in byte[] pcm16)",
                    "void finish()",
                    "void cancel()",
                    "boolean isActive()",
                    "Bundle snapshot()",
                    "void finishRound(boolean discard)",
                ),
            ),
            "ISpeechSessionCallback.aidl" to Declaration(
                oneway = true,
                methods = listOf(
                    "void onReady(in Bundle info)",
                    "void onPartial(in Bundle result)",
                    "void onFinal(in Bundle result)",
                    "void onEndpoint()",
                    "void onVoiceActivity(in Bundle activity)",
                    "void onWarning(in Bundle warning)",
                    "void onError(in Bundle error)",
                    "void onEnd(in Bundle end)",
                ),
            ),
            "ISpeechWakeCallback.aidl" to Declaration(
                oneway = true,
                methods = listOf(
                    "void onReady(in Bundle info)",
                    "void onWake(in Bundle hit)",
                    "void onWarning(in Bundle warning)",
                    "void onError(in Bundle error)",
                    "void onEnd(in Bundle end)",
                ),
            ),
            "ISpeechProgressCallback.aidl" to Declaration(
                oneway = true,
                methods = listOf(
                    "void onProgress(int opId, long done, long total)",
                    "void onCompleted(int opId, in Bundle result)",
                    "void onFailed(int opId, in Bundle error)",
                ),
            ),
        )
    }
}
