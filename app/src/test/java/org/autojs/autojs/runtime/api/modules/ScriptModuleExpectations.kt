package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.differential.SurfaceDiff
import org.autojs.autojs6.BuildConfig
import java.io.File

/**
 * The recorded results of the old implementation (roadmap P5.2). Until P5.2 the script module tests compared the new
 * layer with the old implementation in the same JVM: the obsolete `__xxx__.js` modules (the JS side of
 * DifferentialRhino) and the augment package with the switch off. P5.2 deleted both; the old side of each comparison is
 * replayed from the values recorded before the deletion (stage 3a): one text file per test class,
 * `src/test/resources/script-modules/obsolete/<class>.txt` (the class name without `org.autojs.autojs.`), an entry per
 * call keyed by the test method and a label; the comparisons themselves are unchanged. Nothing can be recorded any
 * more: a new case compares with a fixed expectation in the test instead. The values of the build that the old side
 * read from BuildConfig (version code, name and date) are kept as tokens ([BUILD_TOKENS]), in the keys and the values,
 * so that a record does not depend on the build it was made with.
 *
 * zh-CN: 旧实现的记录结果 (P5.2). P5.2 之前脚本模块测试在同一 JVM 中把新层与旧实现比较: 旧 `__xxx__.js` 模块 (DifferentialRhino 的 JS 侧) 与开关关闭时的
 * augment 包. P5.2 删除了两者, 每项比较的旧侧回放删除前 (阶段 3a) 记录的值: 每个测试类一个文本文件 src/test/resources/script-modules/obsolete/<类名>.txt
 * (去掉 org.autojs.autojs. 前缀), 每次调用一个条目, 键为测试方法与标签; 比较本身不变. 之后无法再记录: 新用例改为与测试中的固定期望比较. 旧侧从 BuildConfig
 * 读取的构建值 (版本号, 版本名, 日期) 在键与值中都以占位符保存 ([BUILD_TOKENS]), 记录因此与记录时的构建无关.
 */
internal object ScriptModuleExpectations {

    /** A value of this build that the records hold as [token], replaced only as a whole word. */
    private class BuildToken(val value: String, val token: String) {
        val pattern = Regex("(?<![\\w.])" + Regex.escape(value) + "(?!\\w)")
    }

    /** [BuildConfig.VERSION_CODE] + 1 first: a test asks for the next version too. */
    private val BUILD_TOKENS = listOf(
        BuildToken("${BuildConfig.VERSION_CODE + 1}", "{VERSION_CODE+1}"),
        BuildToken("${BuildConfig.VERSION_CODE}", "{VERSION_CODE}"),
        BuildToken(BuildConfig.VERSION_NAME, "{VERSION_NAME}"),
        BuildToken(BuildConfig.VERSION_DATE, "{VERSION_DATE}"),
    )

    private fun tokenized(text: String) = BUILD_TOKENS.fold(text) { t, b -> b.pattern.replace(t, Regex.escapeReplacement(b.token)) }

    private fun detokenized(text: String) = BUILD_TOKENS.fold(text) { t, b -> t.replace(b.token, b.value) }

    private val DIR = File("src/test/resources/script-modules/obsolete")

    private const val ENTRY = "=== "

    private const val PREFIX = "org.autojs.autojs."

    private val loaded = HashMap<String, LinkedHashMap<String, String>>()

    private val seen = HashMap<String, Int>()

    private var current: String? = null

    /** The old value [label] of the calling test method. A label used more than once in a method is numbered in the order of the calls. */
    @Synchronized
    fun obsolete(label: String): String {
        val (testClass, key) = key(label)
        val entries = loaded.getOrPut(testClass) { read(testClass) }
        return detokenized(entries[key] ?: throw AssertionError("No recorded old value '$key' in ${file(testClass)} (see ScriptModuleExpectations)"))
    }

    /** The old rendering [label] (key and detail, see DifferentialRhino.render) of the calling test method. */
    fun obsoleteRendered(label: String): DifferentialRhino.Rendered {
        val lines = obsolete(label).split('\n', limit = 2)
        return DifferentialRhino.Rendered(lines[0], lines.getOrNull(1))
    }

    /** The old surface [label] (see [SurfaceDiff.surface]) of the calling test method. */
    fun obsoleteSurface(label: String): Map<String, SurfaceDiff.Member> = SurfaceDiff.decode(obsolete(label))

    /**
     * The test class and the key of [label]: the outermost frame of a test class (no `$` in its class or method name) on
     * the stack is the test method, also when a helper of the class or of the test support makes the call.
     */
    private fun key(label: String): Pair<String, String> {
        val frame = Thread.currentThread().stackTrace.lastOrNull { frame ->
            '$' !in frame.className && '$' !in frame.methodName && frame.className.endsWith("Test")
        } ?: throw IllegalStateException("ScriptModuleExpectations must be called from a test class")
        val testClass = frame.className.removePrefix(PREFIX)
        // The numbers count within one run of the test method, whatever ran before it.
        // zh-CN: 编号只在测试方法的一次运行内计数, 与之前运行了什么无关.
        val method = "${frame.className}.${frame.methodName}"
        if (method != current) {
            seen.clear()
            current = method
        }
        val base = "${frame.methodName} " + tokenized(label).replace("\r", "\\r").replace("\n", "\\n")
        val count = seen.merge(base, 1, Int::plus)!!
        return testClass to if (count == 1) base else "$base #$count"
    }

    private fun file(testClass: String) = File(DIR, "$testClass.txt")

    private fun read(testClass: String): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        val f = file(testClass)
        if (!f.isFile) return result
        var key: String? = null
        val value = StringBuilder()
        fun flush() {
            key?.let { result[it] = value.toString().removeSuffix("\n") }
            value.setLength(0)
        }
        f.readLines(Charsets.UTF_8).forEach { line ->
            if (line.startsWith(ENTRY)) {
                flush()
                key = line.removePrefix(ENTRY)
            } else {
                value.append(if (line.startsWith("\\" + ENTRY)) line.substring(1) else line).append('\n')
            }
        }
        flush()
        return result
    }

}
