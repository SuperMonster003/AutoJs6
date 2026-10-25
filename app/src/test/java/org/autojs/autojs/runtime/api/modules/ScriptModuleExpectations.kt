package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import java.io.File

/**
 * The recorded results of the old implementation (roadmap P5.2). Until P5.2 the script module tests compared the new
 * layer with the old implementation in the same JVM: the obsolete `__xxx__.js` modules (the JS side of
 * DifferentialRhino) and the augment package with the switch off. P5.2 deletes both, so the old side of each comparison
 * is replayed from the values recorded while it still existed: one text file per test class,
 * `src/test/resources/script-modules/obsolete/<class>.txt` (the class name without `org.autojs.autojs.`), an entry per
 * [obsolete] call keyed by the test method and a label; the comparisons themselves are unchanged.
 *
 * Recording (only possible before the deletion, with the obsolete assets present): create the empty file
 * `build/record-script-module-expectations` and run the tests; [obsolete] then computes the old value, writes it, and
 * returns it. Review the diff of the resources before committing them.
 *
 * zh-CN: 旧实现的记录结果 (P5.2). P5.2 之前脚本模块测试在同一 JVM 中把新层与旧实现比较: 旧 `__xxx__.js` 模块 (DifferentialRhino 的 JS 侧) 与开关关闭时的
 * augment 包. P5.2 删除了两者, 每项比较的旧侧改为回放删除前记录的值: 每个测试类一个文本文件 src/test/resources/script-modules/obsolete/<类名>.txt
 * (去掉 org.autojs.autojs. 前缀), 每次 [obsolete] 调用一个条目, 键为测试方法与标签; 比较本身不变.
 * 记录 (只能在删除前, 旧资产在场时): 创建空文件 build/record-script-module-expectations 后运行测试, [obsolete] 计算旧值, 写入并返回; 提交前检查资源文件的差异.
 */
internal object ScriptModuleExpectations {

    private val DIR = File("src/test/resources/script-modules/obsolete")

    /** Whether the tests record the old values (they compute them) instead of replaying them. */
    val RECORDING = File("build/record-script-module-expectations").exists()

    private const val ENTRY = "=== "

    private const val PREFIX = "org.autojs.autojs."

    private val loaded = HashMap<String, LinkedHashMap<String, String>>()

    private val seen = HashMap<String, Int>()

    private var current: String? = null

    /**
     * The old value [label] of the calling test method: computed by [compute] and recorded when [RECORDING], read from
     * the recording otherwise. A label used more than once in a method is numbered in the order of the calls.
     */
    @Synchronized
    fun obsolete(label: String, compute: () -> String): String {
        val (testClass, key) = key(label)
        // The tests of the old package itself (P0 differential tests) are deleted with it: nothing to record.
        // zh-CN: 旧包自身的测试 (P0 差分测试) 随旧包删除, 不记录.
        if (testClass.startsWith("runtime.api.augment.")) return compute()
        val entries = loaded.getOrPut(testClass) { read(testClass) }
        if (RECORDING) {
            return compute().also { value ->
                entries[key] = value
                write(testClass, entries)
            }
        }
        return entries[key] ?: throw AssertionError("No recorded old value '$key' in ${file(testClass)} (see ScriptModuleExpectations)")
    }

    /** The old rendering [label] (key and detail, see DifferentialRhino.render) of the calling test method, recorded as [obsolete] does. */
    fun obsoleteRendered(label: String, compute: () -> DifferentialRhino.Rendered): DifferentialRhino.Rendered {
        // The detail (an error message, not compared) keeps no path of the recording machine's temporary directory.
        // zh-CN: 详情 (错误消息, 不参与比较) 不保留记录所在机器的临时目录路径.
        val tmp = File(System.getProperty("java.io.tmpdir")).absolutePath.trimEnd('/', '\\')
        val temporary = Regex(Regex.escape(tmp) + """([\\/]junit\d+)*""")
        val lines = obsolete(label) { compute().let { if (it.detail == null) it.key else it.key + "\n" + it.detail.replace(temporary, "<tmp>") } }.split('\n', limit = 2)
        return DifferentialRhino.Rendered(lines[0], lines.getOrNull(1))
    }

    /** The old surface [label] (see [SurfaceDiff.surface]) of the calling test method, recorded as [obsolete] does. */
    fun obsoleteSurface(label: String, compute: () -> Map<String, SurfaceDiff.Member>): Map<String, SurfaceDiff.Member> =
        SurfaceDiff.decode(obsolete(label) { SurfaceDiff.encode(compute()) })

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
        val base = "${frame.methodName} " + label.replace("\r", "\\r").replace("\n", "\\n")
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

    private fun write(testClass: String, entries: Map<String, String>) {
        DIR.mkdirs()
        val text = buildString {
            entries.forEach { (key, value) ->
                append(ENTRY).append(key).append('\n')
                value.split('\n').forEach { line -> append(if (line.startsWith(ENTRY) || line.startsWith("\\" + ENTRY)) "\\$line" else line).append('\n') }
            }
        }
        file(testClass).writeText(text, Charsets.UTF_8)
    }

}
