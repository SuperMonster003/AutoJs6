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
 * Host roadmap `docs/dev/android-xml-attributes-roadmap.md` A5 on a device: one native layout with the common classes
 * (View, TextView, Button, EditText, ImageView, FrameLayout, RelativeLayout, ScrollView, TableLayout, ListView,
 * Switch, ProgressBar, SeekBar, CardView, the platform Toolbar and Material components), applied with
 * `ui.layoutFile`; every attribute is read back through the view's getters or layout params. It covers the three
 * routes (hand-written handlers, the setter fallback, layout params) and the gaps the coverage matrix showed
 * (relative rules, two-argument setters, AppCompat's `app:srcCompat`, project string arrays, Material components
 * under the script's AppCompat theme).
 *
 * zh-CN: 宿主路线图 A5 的设备验证: 一份含常用类的原生布局经 `ui.layoutFile` 应用, 逐个属性以 getter 或布局参数读回. 覆盖手写处理器,
 * setter 兜底与布局参数三条路径, 以及覆盖矩阵暴露后补上的缺口 (相对规则, 双参 setter, `app:srcCompat`, 工程字符串数组, AppCompat 主题下的 Material 组件).
 */
class AndroidXmlAttributesDeviceTest {

    @Test
    fun commonClassesTakeTheirAttributes() {
        val api26 = if (Build.VERSION.SDK_INT >= 26) "android:autofillHints=\"name\"" else ""
        val api28 = if (Build.VERSION.SDK_INT >= 28) "android:textFontWeight=\"700\"" else ""
        val layout = """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                <View android:id="@+id/plain" android:layout_width="10px" android:layout_height="10px"
                    android:alpha="0.5" android:layerType="software" android:outlineProvider="bounds"
                    android:fadingEdge="vertical" android:minHeight="12px" android:pointerIcon="hand" $api26 />
                <TextView android:id="@+id/text" android:layout_width="wrap_content" android:layout_height="wrap_content"
                    android:text="text" android:textSize="20px" android:digits="0123" android:textLocale="fr-FR"
                    android:letterSpacing="0.1" android:maxLines="2" $api28 />
                <Button android:id="@+id/button" android:layout_width="wrap_content" android:layout_height="wrap_content"
                    android:text="b" android:enabled="false" />
                <EditText android:id="@+id/edit" android:layout_width="match_parent" android:layout_height="wrap_content"
                    android:hint="h" android:inputType="number" android:maxLength="4" android:imeOptions="actionDone" />
                <ImageView android:id="@+id/image" android:layout_width="20px" android:layout_height="20px"
                    app:srcCompat="@android:drawable/ic_menu_add" android:scaleType="centerCrop" android:adjustViewBounds="true" />
                <FrameLayout android:id="@+id/frame" android:layout_width="40px" android:layout_height="40px" android:measureAllChildren="true">
                    <View android:id="@+id/frameChild" android:layout_width="10px" android:layout_height="10px" android:layout_gravity="end|bottom" />
                </FrameLayout>
                <RelativeLayout android:id="@+id/relative" android:layout_width="100px" android:layout_height="50px">
                    <View android:id="@+id/r1" android:layout_width="20px" android:layout_height="20px" android:layout_alignParentStart="true" />
                    <View android:id="@+id/r2" android:layout_width="20px" android:layout_height="20px" android:layout_toEndOf="@id/r1" />
                    <View android:id="@+id/r3" android:layout_width="20px" android:layout_height="20px" android:layout_alignParentEnd="true" />
                    <View android:id="@+id/r4" android:layout_width="20px" android:layout_height="20px" android:layout_toStartOf="@id/r3" />
                </RelativeLayout>
                <ScrollView android:id="@+id/scroll" android:layout_width="match_parent" android:layout_height="30px"
                    android:fillViewport="true" android:scrollbars="none" />
                <TableLayout android:id="@+id/table" android:layout_width="match_parent" android:layout_height="wrap_content"
                    android:stretchColumns="*" android:collapseColumns="1" />
                <ListView android:id="@+id/list" android:layout_width="match_parent" android:layout_height="60px" android:entries="@array/items" />
                <Switch android:id="@+id/toggle" android:layout_width="wrap_content" android:layout_height="wrap_content"
                    android:checked="true" android:showText="true" android:switchTextAppearance="@android:style/TextAppearance.Small" />
                <ProgressBar android:id="@+id/progress" style="?android:attr/progressBarStyleHorizontal"
                    android:layout_width="match_parent" android:layout_height="wrap_content" android:max="200" android:progress="50" />
                <SeekBar android:id="@+id/seek" android:layout_width="match_parent" android:layout_height="wrap_content" android:max="10" android:progress="3" />
                <androidx.cardview.widget.CardView android:id="@+id/card" android:layout_width="40px" android:layout_height="40px"
                    app:cardCornerRadius="8px" app:cardElevation="3px" app:contentPadding="5px" />
                <Toolbar android:id="@+id/toolbar" android:layout_width="match_parent" android:layout_height="wrap_content"
                    android:title="t" android:logo="@android:drawable/ic_menu_add" android:contentInsetStart="7px" android:titleMargin="3px" />
                <com.google.android.material.button.MaterialButton android:id="@+id/material" android:layout_width="wrap_content"
                    android:layout_height="wrap_content" android:text="m" app:cornerRadius="6px" />
                <com.google.android.material.slider.Slider android:id="@+id/slider" android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:valueTo="10" app:trackColorActive="#ff00ff00" />
            </LinearLayout>
        """.trimIndent()
        val arrays = """<resources><string-array name="items"><item>a</item><item>b</item><item>c</item></string-array></resources>"""
        val report = runUiScript(
            """
            ui.useAndroidLayout(true);
            ui.layoutFile('main.xml');
            function v(id) { return ui.findView(id); }
            function report() {
                var text = v('text');
                var edit = v('edit');
                var result = {
                    layerType: Number(v('plain').getLayerType()),
                    outlineBounds: v('plain').getOutlineProvider() === android.view.ViewOutlineProvider.BOUNDS,
                    verticalFading: v('plain').isVerticalFadingEdgeEnabled() == true,
                    minHeight: Number(v('plain').getMinimumHeight()),
                    pointerIcon: v('plain').getPointerIcon() != null,
                    textSize: Number(text.getTextSize()),
                    keyListener: String(text.getKeyListener().getClass().getSimpleName()),
                    textLocale: String(text.getTextLocale().toLanguageTag()),
                    letterSpacing: Number(text.getLetterSpacing()),
                    maxLines: Number(text.getMaxLines()),
                    buttonEnabled: v('button').isEnabled() == true,
                    hint: String(edit.getHint()),
                    inputType: Number(edit.getInputType()),
                    maxLength: Number(edit.getFilters()[0].getMax()),
                    imeAction: Number(edit.getImeOptions()) & 0xff,
                    image: v('image').getDrawable() != null,
                    scaleType: String(v('image').getScaleType().name()),
                    adjustViewBounds: v('image').getAdjustViewBounds() == true,
                    measureAll: v('frame').getMeasureAllChildren() == true,
                    childGravity: Number(v('frameChild').getLayoutParams().gravity),
                    r2Left: Number(v('r2').getLeft()),
                    r4Left: Number(v('r4').getLeft()),
                    fillViewport: v('scroll').isFillViewport() == true,
                    scrollbars: v('scroll').isVerticalScrollBarEnabled() == true,
                    stretchAll: v('table').isStretchAllColumns() == true,
                    collapsed: v('table').isColumnCollapsed(1) == true,
                    listCount: Number(v('list').getAdapter().getCount()),
                    checked: v('toggle').isChecked() == true,
                    showText: v('toggle').getShowText() == true,
                    max: Number(v('progress').getMax()),
                    progress: Number(v('progress').getProgress()),
                    indeterminate: v('progress').isIndeterminate() == true,
                    seek: Number(v('seek').getProgress()) + '/' + Number(v('seek').getMax()),
                    cardRadius: Number(v('card').getRadius()),
                    cardElevation: Number(v('card').getCardElevation()),
                    cardPadding: Number(v('card').getContentPaddingLeft()),
                    toolbarTitle: String(v('toolbar').getTitle()),
                    toolbarLogo: v('toolbar').getLogo() != null,
                    toolbarInset: Number(v('toolbar').getContentInsetStart()),
                    toolbarTitleMargin: Number(v('toolbar').getTitleMarginStart()),
                    materialClass: String(v('material').getClass().getSimpleName()),
                    materialRadius: Number(v('material').getCornerRadius()),
                    sliderTo: Number(v('slider').getValueTo()),
                    sliderTrack: Number(v('slider').getTrackActiveTintList().getDefaultColor()),
                };
                if (android.os.Build.VERSION.SDK_INT >= 26) result.autofill = String(v('plain').getAutofillHints()[0]);
                if (android.os.Build.VERSION.SDK_INT >= 28) result.weight = Number(text.getTypeface().getWeight());
                finish(result);
            }
            ui.post(report, 300);
            """,
            layout,
            mapOf("res/values/arrays.xml" to arrays),
        )
        // View: the setter fallback and its specials.
        assertEquals(1, report.getInt("layerType"))
        assertTrue(report.getBoolean("outlineBounds"))
        assertTrue(report.getBoolean("verticalFading"))
        assertEquals(12, report.getInt("minHeight"))
        assertTrue(report.getBoolean("pointerIcon"))
        if (Build.VERSION.SDK_INT >= 26) assertEquals("name", report.getString("autofill"))
        // TextView / Button / EditText: handlers, fallback setters and specials.
        assertEquals(20.0, report.getDouble("textSize"), 0.01)
        assertEquals("DigitsKeyListener", report.getString("keyListener"))
        // android:textLocale exists since API 29; below it the platform ignores the attribute as well.
        if (Build.VERSION.SDK_INT >= 29) assertEquals("fr-FR", report.getString("textLocale"))
        assertEquals(0.1, report.getDouble("letterSpacing"), 0.001)
        assertEquals(2, report.getInt("maxLines"))
        if (Build.VERSION.SDK_INT >= 28) assertEquals(700, report.getInt("weight"))
        assertEquals(false, report.getBoolean("buttonEnabled"))
        assertEquals("h", report.getString("hint"))
        assertEquals(2, report.getInt("inputType"))
        assertEquals(4, report.getInt("maxLength"))
        assertEquals(6, report.getInt("imeAction"))
        // ImageView: app:srcCompat as android:src.
        assertTrue(report.getBoolean("image"))
        assertEquals("CENTER_CROP", report.getString("scaleType"))
        assertTrue(report.getBoolean("adjustViewBounds"))
        // Containers and layout params.
        assertTrue(report.getBoolean("measureAll"))
        assertEquals(0x800005 or 0x50, report.getInt("childGravity"))
        assertEquals(20, report.getInt("r2Left"))
        assertEquals(60, report.getInt("r4Left"))
        assertTrue(report.getBoolean("fillViewport"))
        assertEquals(false, report.getBoolean("scrollbars"))
        assertTrue(report.getBoolean("stretchAll"))
        assertTrue(report.getBoolean("collapsed"))
        assertEquals(3, report.getInt("listCount"))
        // Widgets.
        assertTrue(report.getBoolean("checked"))
        assertTrue(report.getBoolean("showText"))
        assertEquals(200, report.getInt("max"))
        assertEquals(50, report.getInt("progress"))
        assertEquals(false, report.getBoolean("indeterminate"))
        assertEquals("3/10", report.getString("seek"))
        assertEquals(8.0, report.getDouble("cardRadius"), 0.01)
        assertEquals(3.0, report.getDouble("cardElevation"), 0.01)
        assertEquals(5, report.getInt("cardPadding"))
        assertEquals("t", report.getString("toolbarTitle"))
        assertTrue(report.getBoolean("toolbarLogo"))
        assertEquals(7, report.getInt("toolbarInset"))
        assertEquals(3, report.getInt("toolbarTitleMargin"))
        // Material components under the script's AppCompat theme.
        assertEquals("MaterialButton", report.getString("materialClass"))
        assertEquals(6, report.getInt("materialRadius"))
        assertEquals(10.0, report.getDouble("sliderTo"), 0.001)
        assertEquals(0xff00ff00.toInt(), report.getInt("sliderTrack"))
    }

    private fun runUiScript(body: String, layoutText: String, files: Map<String, String> = emptyMap()): JSONObject {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "android-xml-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        File(directory, "main.xml").writeText(layoutText)
        for ((path, text) in files) File(directory, path).apply { parentFile?.mkdirs() }.writeText(text)
        val report = File(directory, "report.json")
        val script = File(directory, "android-xml.js")
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
