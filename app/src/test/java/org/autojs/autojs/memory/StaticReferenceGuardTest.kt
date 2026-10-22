package org.autojs.autojs.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Source guard of docs/memory/ROADMAP.md T8 / P4.3. Every process-level reference [StaticReferenceScanner] finds in
 * `app/src/main/java` (Java static fields and Kotlin object / companion object / top-level properties typed with a script,
 * Rhino or UI object or an Android Context and not held weakly, process-level registries, files that register receivers,
 * bind services, add idle handlers, prepare loopers or create WebViews) needs a line with a reason in
 * `src/test/resources/memory/static-reference-whitelist.txt`. The generated columns of that file come from
 * `tools/memory/static_audit.py --guard`, which applies the same rules; [scannerAgreesWithStaticAudit] checks that the
 * script and the scanner give the same rows, so the two implementations cannot drift apart unnoticed.
 * zh-CN: docs/memory/ROADMAP.md T8 / P4.3 的源码守卫. [StaticReferenceScanner] 在 `app/src/main/java` 中找到的每个进程级引用 (类型为脚本, Rhino,
 * UI 对象或 Android Context 且非弱引用的 Java 静态字段与 Kotlin object / companion object / 顶层属性, 进程级注册表, 注册接收器, 绑定服务, 添加
 * IdleHandler, 准备 Looper 或创建 WebView 的文件) 都须在 `src/test/resources/memory/static-reference-whitelist.txt` 中有一行并写明理由. 该文件的
 * 生成列来自按同一规则运行的 `tools/memory/static_audit.py --guard`; [scannerAgreesWithStaticAudit] 核对脚本与扫描器给出相同的行, 两份实现无法悄悄分叉.
 */
class StaticReferenceGuardTest {

    private class Entry(val number: Int, val kind: String, val path: String, val name: String, val detail: String, val reason: String, val note: String) {
        val key get() = "$kind | $path | $name"
        val generated get() = "$kind | $path | $name | $detail"
    }

    @Test
    fun everyProcessLevelFieldIsWhitelisted() {
        val missing = rows.filter { it.kind == "field" && it.key !in entryKeys }
        if (missing.isEmpty()) return
        fail(
            buildString {
                appendLine("${missing.size} process-level field(s) not in the whitelist (docs/memory/ROADMAP.md T8):")
                missing.forEach { row ->
                    val owner = row.owner.ifEmpty { File(row.path).nameWithoutExtension }
                    appendLine("  ${row.path}:${row.line}  $owner.${row.name.substringBefore('#')}  (${row.detail}; type ${row.type})")
                }
                append(HOW_TO_REGISTER)
                append(" Prefer holding it through a WeakReference, or removing its entries per runtime / owner id when the owner ends (ROADMAP T7).")
            },
        )
    }

    @Test
    fun everyRegistrationCallFileIsWhitelisted() {
        val missing = rows.filter { it.kind == "call" && it.key !in entryKeys }
        if (missing.isEmpty()) return
        fail(
            buildString {
                appendLine("${missing.size} file(s) with registration calls not in the whitelist (docs/memory/ROADMAP.md T8 (b)):")
                missing.forEach { row -> appendLine("  ${row.path}  calls ${row.name}  (${row.detail})") }
                append(HOW_TO_REGISTER)
                append(" The note names the release path (where the receiver is unregistered, the service unbound, the WebView destroyed).")
            },
        )
    }

    @Test
    fun whitelistHasNoStaleLines() {
        val keys = rows.mapTo(HashSet()) { it.key }
        val stale = entries.filter { it.kind != "manual" && it.key !in keys }
        if (stale.isEmpty()) return
        fail(
            buildString {
                appendLine("${stale.size} whitelist line(s) no longer match a reference in the sources (removed, renamed or made weak):")
                stale.forEach { appendLine("  line ${it.number}: ${it.key}") }
                append("Delete them from $WHITELIST_PATH, or run `$REGENERATE` in the repository root.")
            },
        )
    }

    @Test
    fun everyLineHasAReasonAndANote() {
        val problems = entries.mapNotNull { entry ->
            when {
                entry.reason !in REASONS.getValue(entry.kind) -> "reason \"${entry.reason}\" is not one of ${REASONS.getValue(entry.kind)}"
                entry.note.isBlank() || entry.note.startsWith("TODO") -> "the note is missing"
                else -> null
            }?.let { "  line ${entry.number}: ${entry.key}: $it" }
        }
        assertTrue("Whitelist lines without a valid reason ($WHITELIST_PATH; reasons are explained in its header):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** ROADMAP R5: ASCII punctuation only. zh-CN: ROADMAP R5: 只用 ASCII 标点. */
    @Test
    fun whitelistIsAscii() {
        val lines = whitelistFile.readLines().withIndex().filter { (_, line) -> line.any { it.code !in 0x20..0x7e && it != '\t' } }
        assertTrue("Non-ASCII characters in $WHITELIST_PATH at lines ${lines.map { it.index + 1 }}", lines.isEmpty())
    }

    @Test
    fun manualLinesNameExistingDeclarations() {
        val manual = entries.filter { it.kind == "manual" }
        val problems = manual.mapNotNull { entry ->
            val file = File(sourceRoot, "org/autojs/autojs/${entry.path}")
            if (!file.isFile) return@mapNotNull "  line ${entry.number}: ${entry.path} does not exist"
            val code = StaticReferenceScanner.stripCode(file.readText(), file.extension == "kt")
            val name = Regex.escape(entry.name)
            val declaration = if (file.extension == "kt") Regex("\\b(?:val|var)\\s+$name\\b") else Regex("\\b$name\\s*(?:=|;)")
            if (declaration.containsMatchIn(code)) null else "  line ${entry.number}: ${entry.path} no longer declares ${entry.name}"
        }
        assertTrue("Stale manual whitelist lines (update or delete them in $WHITELIST_PATH):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * The generated columns of the whitelist (written by `static_audit.py --guard`) equal the rows of the Kotlin scanner.
     * zh-CN: 白名单的生成列 (由 `static_audit.py --guard` 写出) 与 Kotlin 扫描器的行完全相同.
     */
    @Test
    fun scannerAgreesWithStaticAudit() {
        val python = entries.filter { it.kind != "manual" }.map { it.generated }.sorted()
        val kotlin = rows.map { it.generated }
        if (python == kotlin) return
        val onlyPython = python - kotlin.toSet()
        val onlyKotlin = kotlin - python.toSet()
        fail(
            buildString {
                appendLine("The generated columns of $WHITELIST_PATH differ from StaticReferenceScanner (${python.size} / ${kotlin.size} rows).")
                onlyPython.forEach { appendLine("  only in the file:    $it") }
                onlyKotlin.forEach { appendLine("  only in the scanner: $it") }
                append("Run `$REGENERATE` in the repository root. If the difference remains, static_audit.py and StaticReferenceScanner ")
                append("no longer apply the same rules: change both together.")
            },
        )
    }

    /** The rules on small samples (what is reported and what is not). zh-CN: 规则在小样例上的表现 (报出什么, 不报什么). */
    @Test
    fun scannerRulesOnSamples() {
        val java = """
            package org.autojs.autojs.runtime;
            import android.view.View;
            import android.app.Activity;
            import java.lang.ref.WeakReference;
            public class Sample {
                private static View sView; // static ScriptRuntime sInComment;
                private static WeakReference<Activity> sActivity;
                private static final java.util.List<String> NAMES = new java.util.ArrayList<>();
                private View mInstanceView;
                private static final String TEXT = "static View sInString;";
            }
        """.trimIndent()
        assertEquals(
            listOf("field | runtime/Sample.java | sView | java static; strong; View"),
            StaticReferenceScanner.scanText("runtime/Sample.java", java).map { it.generated },
        )
        val kotlin = """
            package org.autojs.autojs.runtime
            class Sample(private val runtime: ScriptRuntime) {
                val instanceListeners = mutableListOf<Listener>()
                companion object {
                    var lastRuntime: ScriptRuntime? = null
                    private val listeners = mutableListOf<Listener>()
                    private val names = mutableListOf<String>()
                    private val weakRuntime = WeakReference<ScriptRuntime>(null)
                    /* val commented: ScriptRuntime? = null */
                }
            }
            object Registry {
                private val windows: MutableMap<String, FloatyWindow> = HashMap()
            }
        """.trimIndent()
        val rows = StaticReferenceScanner.scanText("runtime/Sample.kt", kotlin)
        assertEquals(
            listOf(
                "field | runtime/Sample.kt | lastRuntime | kotlin object; strong; ScriptRuntime",
                "field | runtime/Sample.kt | listeners | kotlin object; registry; -",
                "field | runtime/Sample.kt | windows | kotlin object; registry; -",
            ),
            rows.map { it.generated },
        )
        assertEquals(listOf("Sample.Companion", "Sample.Companion", "Registry"), rows.map { it.owner })
        val calls = """
            package org.autojs.autojs.ui
            class Page(context: Context) : WebView(context) {
                fun start(context: Context) {
                    context.registerReceiver(receiver, filter)
                    context.bindService(intent, connection, 0)
                    val view = WebView(context)
                }
                fun stop(context: Context) = context.unbindService(connection)
            }
        """.trimIndent()
        assertEquals(
            listOf(
                "call | ui/Page.kt | WebView( | released: no; page",
                "call | ui/Page.kt | bindService | released: yes; page",
                "call | ui/Page.kt | registerReceiver | released: no; page",
            ),
            StaticReferenceScanner.scanText("ui/Page.kt", calls).map { it.generated },
        )
    }

    private companion object {

        const val WHITELIST_PATH = "app/src/test/resources/memory/static-reference-whitelist.txt"
        const val REGENERATE = "py -3.12 -I tools/memory/static_audit.py --guard"
        const val HOW_TO_REGISTER = "To register: run `$REGENERATE` in the repository root (it adds the line with the reason TODO to " +
            "$WHITELIST_PATH and keeps the other lines), then replace TODO with a reason from the file header and a note."

        val FIELD_REASONS = setOf("process", "table", "weak", "owner", "page", "exempt")
        val REASONS = mapOf(
            "field" to FIELD_REASONS,
            "manual" to FIELD_REASONS,
            "call" to setOf("process", "owner", "paired", "page", "exempt"),
        )

        /** The unit tests run in the module directory; the fallback covers runs from the repository root. */
        val sourceRoot = sequenceOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val whitelistFile = File(sourceRoot, "../../test/resources/memory/static-reference-whitelist.txt").normalize()

        val rows by lazy { StaticReferenceScanner.scan(sourceRoot) }

        val entries by lazy {
            whitelistFile.readLines().withIndex().filter { (_, line) -> line.isNotBlank() && !line.startsWith("#") }.map { (index, line) ->
                val parts = line.split(" | ", limit = 6)
                check(parts.size == 6 && parts[0] in REASONS) { "Malformed line ${index + 1} of $WHITELIST_PATH: $line" }
                Entry(index + 1, parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
            }
        }

        val entryKeys by lazy { entries.mapTo(HashSet()) { it.key } }
    }

}
