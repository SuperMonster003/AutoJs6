package org.autojs.autojs.core.ui.inflater

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.JavaScriptFileSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Host roadmap `docs/dev/android-xml-attributes-roadmap.md` A2 on a device: a native layout file applied with
 * `ui.layoutFile` sets attributes the inflater has no handler for through the metadata fallback (break strategy,
 * next focus, label-for, nested scrolling, divider padding, touchscreen focus blocking, transition group, and the
 * API 26 / 28 attributes where the device has them), keeps document order for padding (`paddingHorizontal` /
 * `paddingVertical` after `padding`), and still ignores `tools:` and unknown attributes.
 *
 * zh-CN: 宿主路线图 A2 的设备验证: 以 `ui.layoutFile` 应用的原生布局文件经元数据兜底设置布局器没有处理器的属性, 内边距按书写顺序, `tools:` 与未知属性仍被忽略.
 */
class AndroidAttributeFallbackDeviceTest {

    private fun layout(): String {
        val api26 = if (Build.VERSION.SDK_INT >= 26) "android:tooltipText=\"tip\" android:focusedByDefault=\"true\" android:justificationMode=\"inter_word\" android:autoSizeTextType=\"uniform\"" else ""
        val api28 = if (Build.VERSION.SDK_INT >= 28) "android:screenReaderFocusable=\"true\" android:accessibilityHeading=\"true\" android:outlineSpotShadowColor=\"#ffff0000\" android:fallbackLineSpacing=\"false\"" else ""
        return """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                android:id="@+id/root"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:padding="2dp"
                android:paddingHorizontal="12dp"
                android:paddingVertical="6dp"
                android:dividerPadding="5px"
                android:touchscreenBlocksFocus="true"
                android:transitionGroup="true"
                tools:ignore="HardcodedText">

                <TextView
                    android:id="@+id/t"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="hello"
                    android:breakStrategy="simple"
                    android:nextFocusDown="@+id/b"
                    android:labelFor="@+id/b"
                    android:nestedScrollingEnabled="true"
                    android:noSuchAttribute="ignored"
                    $api26
                    $api28 />

                <Button
                    android:id="@+id/b"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="b" />
            </LinearLayout>
        """.trimIndent()
    }

    @Test
    fun nativeAttributesWithoutHandlersAreSetThroughThePublicApi() {
        val report = runUiScript(
            """
            ui.useAndroidLayout(true);
            ui.layoutFile('main.xml');
            var text = ui.findView('t'), button = ui.findView('b'), root = ui.findView('root');
            var sdk = android.os.Build.VERSION.SDK_INT;
            finish({
                breakStrategy: text.getBreakStrategy(),
                nextFocusDown: text.getNextFocusDownId() == button.getId(),
                labelFor: text.getLabelFor() == button.getId(),
                nestedScrolling: text.isNestedScrollingEnabled(),
                dividerPadding: root.getDividerPadding(),
                touchscreenBlocksFocus: root.getTouchscreenBlocksFocus(),
                transitionGroup: root.isTransitionGroup(),
                tooltip: sdk >= 26 ? String(text.getTooltipText()) : 'n/a',
                focusedByDefault: sdk >= 26 ? text.isFocusedByDefault() : true,
                justificationMode: sdk >= 26 ? text.getJustificationMode() : 1,
                autoSizeTextType: sdk >= 26 ? text.getAutoSizeTextType() : 1,
                screenReaderFocusable: sdk >= 28 ? text.isScreenReaderFocusable() : true,
                accessibilityHeading: sdk >= 28 ? text.isAccessibilityHeading() : true,
                outlineSpotShadowColor: sdk >= 28 ? text.getOutlineSpotShadowColor() : -65536,
                fallbackLineSpacing: sdk >= 28 ? text.isFallbackLineSpacing() : false,
                paddingLeft: root.getPaddingLeft(),
                paddingTop: root.getPaddingTop(),
                density: context.getResources().getDisplayMetrics().density,
            });
            """,
        )
        assertEquals(0, report.getInt("breakStrategy"))
        assertTrue(report.getBoolean("nextFocusDown"))
        assertTrue(report.getBoolean("labelFor"))
        assertTrue(report.getBoolean("nestedScrolling"))
        assertEquals(5, report.getInt("dividerPadding"))
        assertTrue(report.getBoolean("touchscreenBlocksFocus"))
        assertTrue(report.getBoolean("transitionGroup"))
        if (Build.VERSION.SDK_INT >= 26) assertEquals("tip", report.getString("tooltip"))
        assertTrue(report.getBoolean("focusedByDefault"))
        assertEquals(1, report.getInt("justificationMode"))
        assertEquals(1, report.getInt("autoSizeTextType"))
        assertTrue(report.getBoolean("screenReaderFocusable"))
        assertTrue(report.getBoolean("accessibilityHeading"))
        assertEquals(0xffff0000.toInt(), report.getInt("outlineSpotShadowColor"))
        assertEquals(false, report.getBoolean("fallbackLineSpacing"))
        // Document order: padding first, then paddingHorizontal / paddingVertical override its sides.
        val density = report.getDouble("density")
        assertEquals(12 * density, report.getDouble("paddingLeft"), 1.0)
        assertEquals(6 * density, report.getDouble("paddingTop"), 1.0)
    }

    @Test
    fun layoutParamsOfConstraintCoordinatorAndGridLayoutsAreSet() {
        val layoutText = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">

                <androidx.constraintlayout.widget.ConstraintLayout
                    android:id="@+id/constraint"
                    android:layout_width="300px"
                    android:layout_height="300px">

                    <View
                        android:id="@+id/a"
                        android:layout_width="50px"
                        android:layout_height="50px"
                        app:layout_constraintTop_toTopOf="parent"
                        app:layout_constraintStart_toStartOf="parent" />

                    <View
                        android:id="@+id/c"
                        android:layout_width="40px"
                        android:layout_height="40px"
                        app:layout_constraintTop_toBottomOf="@+id/a"
                        app:layout_constraintStart_toEndOf="@+id/a"
                        app:layout_goneMarginStart="7px" />

                    <View
                        android:id="@+id/d"
                        android:layout_width="0px"
                        android:layout_height="20px"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintBottom_toBottomOf="parent"
                        app:layout_constraintWidth_percent="0.5"
                        app:layout_constraintWidth_default="percent"
                        app:layout_constraintHorizontal_bias="1"
                        app:layout_editor_absoluteX="12px" />
                </androidx.constraintlayout.widget.ConstraintLayout>

                <androidx.coordinatorlayout.widget.CoordinatorLayout
                    android:layout_width="match_parent"
                    android:layout_height="200px">

                    <com.google.android.material.appbar.AppBarLayout
                        android:id="@+id/bar"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content">

                        <View
                            android:id="@+id/toolbar"
                            android:layout_width="match_parent"
                            android:layout_height="40px"
                            app:layout_scrollFlags="scroll|enterAlways" />
                    </com.google.android.material.appbar.AppBarLayout>

                    <androidx.core.widget.NestedScrollView
                        android:id="@+id/scroll"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        app:layout_behavior="@string/appbar_scrolling_view_behavior" />

                    <View
                        android:id="@+id/fab"
                        android:layout_width="20px"
                        android:layout_height="20px"
                        app:layout_anchor="@+id/bar"
                        app:layout_anchorGravity="bottom|end" />
                </androidx.coordinatorlayout.widget.CoordinatorLayout>

                <GridLayout
                    android:id="@+id/grid"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:columnCount="3">

                    <View
                        android:layout_width="30px"
                        android:layout_height="30px"
                        android:layout_row="0"
                        android:layout_column="0" />

                    <View
                        android:layout_width="30px"
                        android:layout_height="30px"
                        android:layout_row="0"
                        android:layout_column="1" />

                    <View
                        android:id="@+id/cell"
                        android:layout_width="30px"
                        android:layout_height="30px"
                        android:layout_row="1"
                        android:layout_column="2" />
                </GridLayout>
            </LinearLayout>
        """.trimIndent()
        val report = runUiScript(
            """
            ui.useAndroidLayout(true);
            ui.layoutFile('main.xml');
            var a = ui.findView('a'), c = ui.findView('c'), d = ui.findView('d');
            var bar = ui.findView('bar');
            function report() {
                var toolbarParams = ui.findView('toolbar').getLayoutParams();
                var scrollParams = ui.findView('scroll').getLayoutParams();
                var fabParams = ui.findView('fab').getLayoutParams();
                var cell = ui.findView('cell');
                finish({
                    aRight: a.getRight(), aBottom: a.getBottom(),
                    cLeft: c.getLeft(), cTop: c.getTop(),
                    goneStartMargin: c.getLayoutParams().goneStartMargin,
                    dLeft: d.getLeft(), dWidth: d.getWidth(), dBottom: d.getBottom(),
                    scrollFlags: toolbarParams.getScrollFlags(),
                    behavior: String(scrollParams.getBehavior() && scrollParams.getBehavior().getClass().getName()),
                    anchor: fabParams.getAnchorId() == bar.getId(),
                    anchorGravity: fabParams.anchorGravity,
                    cellLeft: cell.getLeft(), cellTop: cell.getTop(),
                });
            }
            ui.post(report, 300);
            """,
            layoutText,
        )
        // ConstraintLayout: c below and right of a; d is half the width, at the end, at the bottom.
        assertEquals(report.getInt("aRight"), report.getInt("cLeft"))
        assertEquals(report.getInt("aBottom"), report.getInt("cTop"))
        assertEquals(7, report.getInt("goneStartMargin"))
        assertEquals(150, report.getInt("dWidth"))
        assertEquals(150, report.getInt("dLeft"))
        assertEquals(300, report.getInt("dBottom"))
        // AppBarLayout scroll flags: scroll (0x1) | enterAlways (0x4); the scrolling view behavior; the anchor.
        assertEquals(0x5, report.getInt("scrollFlags"))
        assertTrue(report.getString("behavior"), report.getString("behavior").endsWith("ScrollingViewBehavior"))
        assertTrue(report.getBoolean("anchor"))
        assertEquals(0x50 or 0x800005, report.getInt("anchorGravity"))
        // GridLayout: row 1, column 2, after two 30 px cells in row 0.
        assertEquals(60, report.getInt("cellLeft"))
        assertEquals(30, report.getInt("cellTop"))
    }

    private fun runUiScript(body: String, layoutText: String = layout()): JSONObject {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "android-attrs-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        File(directory, "main.xml").writeText(layoutText)
        val report = File(directory, "report.json")
        val script = File(directory, "android-attrs.js")
        script.writeText(buildString {
            appendLine("\"ui rhino\";")
            appendLine("function finish(result) { files.write(${JSONObject.quote(report.absolutePath)}, JSON.stringify(result)); ui.finish(); }")
            appendLine(body.trimIndent())
        })
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        var execution: ScriptExecution? = null
        try {
            execution = AutoJs.instance.scriptEngineService.execute(
                JavaScriptFileSource(script), listener,
                ExecutionConfig(workingDirectory = directory.absolutePath),
            )
            assertTrue("the script timed out", completed.await(60, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("the script failed: ${it.message}", it) }
            assertTrue("the script did not report", report.isFile)
            return JSONObject(report.readText())
        } finally {
            execution?.engine?.let { if (!it.isDestroyed) it.forceStop() }
            directory.deleteRecursively()
        }
    }
}
