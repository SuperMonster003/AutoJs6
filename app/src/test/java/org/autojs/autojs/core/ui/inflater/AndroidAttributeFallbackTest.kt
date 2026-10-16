package org.autojs.autojs.core.ui.inflater

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.autojs.autojs.core.ui.inflater.AndroidAttributeFallback.Kind
import org.autojs.autojs.core.ui.inflater.AndroidAttributeTable.Format
import org.autojs.autojs.core.ui.inflater.util.Drawables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Host roadmap `docs/dev/android-xml-attributes-roadmap.md` A2: the fallback's naming rules, the parameter types it
 * prefers per attribute format, and the setters it finds on real platform classes (method signatures of android.jar).
 *
 * zh-CN: 宿主路线图 A2: 兜底的命名规则, 按属性格式偏好的参数类型, 以及在平台类上找到的 setter.
 */
class AndroidAttributeFallbackTest {

    private val table = AndroidAttributeTable.parse(File("src/main/assets/" + AndroidAttributeTable.ASSET_PATH).readText())
    private val fallback = AndroidAttributeFallback(table, ResourceParser(Drawables()))

    private fun setters(viewClass: Class<*>, name: String): List<String> =
        fallback.candidatesOf(viewClass, table.find(viewClass, "android", name)!!.attr).map { "${it.name}(${it.parameterTypes[0].simpleName})" }

    @Test
    fun namingRulesCoverTheCommonShapes() {
        assertEquals("setLetterSpacing", AndroidAttributeSetters.candidates("letterSpacing").first())
        assertTrue("setBackgroundTintList" in AndroidAttributeSetters.candidates("backgroundTint"))
        assertTrue("setButtonDrawable" in AndroidAttributeSetters.candidates("button"))
        assertTrue("setMinimumHeight" in AndroidAttributeSetters.candidates("minHeight"))
        assertTrue("setScrollContainer" in AndroidAttributeSetters.candidates("isScrollContainer"))
        assertTrue("setDuplicateParentStateEnabled" in AndroidAttributeSetters.candidates("duplicateParentState"))
        assertEquals("setHintTextColor", AndroidAttributeSetters.candidates("textColorHint").first())
        assertEquals("setNextFocusDownId", AndroidAttributeSetters.candidates("nextFocusDown").first())
    }

    @Test
    fun formatsOrderTheParameterTypes() {
        assertEquals(listOf(Kind.COLOR_STATE_LIST, Kind.COLOR_INT, Kind.DRAWABLE, Kind.TEXT, Kind.ID), Kind.preferred(setOf(Format.COLOR, Format.REFERENCE)))
        assertEquals(listOf(Kind.JAVA_ENUM, Kind.ENUM_INT), Kind.preferred(setOf(Format.ENUM)))
        assertEquals(listOf(Kind.DIMENSION_FLOAT, Kind.DIMENSION_INT), Kind.preferred(setOf(Format.DIMENSION)))
        assertTrue(Kind.DRAWABLE.accepts(Drawable::class.java))
        assertTrue(Kind.COLOR_STATE_LIST.accepts(ColorStateList::class.java))
        assertTrue(Kind.TEXT.accepts(CharSequence::class.java))
        assertEquals("CENTER_CROP", AndroidAttributeFallback.Values.enumConstantName("centerCrop"))
        assertEquals("FIT_XY", AndroidAttributeFallback.Values.enumConstantName("fitXY"))
        assertEquals("SRC_ATOP", AndroidAttributeFallback.Values.enumConstantName("src_atop"))
    }

    @Test
    fun platformSettersAreFoundByNameAndParameterType() {
        assertEquals(listOf("setLetterSpacing(float)"), setters(TextView::class.java, "letterSpacing"))
        assertEquals("setBackgroundTintList(ColorStateList)", setters(View::class.java, "backgroundTint").first())
        assertEquals("setScaleType(ScaleType)", setters(ImageView::class.java, "scaleType").first())
        assertEquals("setNextFocusDownId(int)", setters(View::class.java, "nextFocusDown").first())
        // Compared ignoring case: scrollbarStyle -> setScrollBarStyle.
        assertEquals("setScrollBarStyle(int)", setters(View::class.java, "scrollbarStyle").first())
        assertEquals("setMinimumHeight(int)", setters(View::class.java, "minHeight").first())
        // Overloads: a drawable before a resource id, a color state list before a plain color.
        assertEquals("setButtonDrawable(Drawable)", setters(CheckBox::class.java, "button").first())
        assertEquals("setHintTextColor(ColorStateList)", setters(TextView::class.java, "textColorHint").first())
        assertEquals("setDividerDrawable(Drawable)", setters(LinearLayout::class.java, "divider").first())
        assertEquals("setTooltipText(CharSequence)", setters(View::class.java, "tooltipText").first())
    }

    /** A5: gaps the coverage matrix showed, closed by names, a state rule, formats from attrs_manifest.xml and specials. */
    @Test
    fun coverageGapsAreClosed() {
        assertEquals("setThreshold", AndroidAttributeSetters.candidates("completionThreshold").first())
        assertEquals("setSelectedTabIndicator", AndroidAttributeSetters.candidates("tabIndicator").first())
        assertTrue("setTrackActiveTintList" in AndroidAttributeSetters.candidates("trackColorActive"))
        assertTrue("setHaloTintList" in AndroidAttributeSetters.candidates("haloColor"))
        assertTrue("setTickInactiveRadius" in AndroidAttributeSetters.candidates("tickRadiusInactive"))
        assertEquals(listOf("setThreshold(int)"), setters(android.widget.AutoCompleteTextView::class.java, "completionThreshold"))
        // logo and theme take their format from attrs_manifest.xml.
        assertEquals("setLogo(Drawable)", setters(android.widget.Toolbar::class.java, "logo").first())
        assertEquals(setOf(Format.REFERENCE), table.attr("android", "theme")!!.formats)
        for (name in listOf("layerType", "outlineProvider", "autofillHints", "pointerIcon", "digits", "textFontWeight", "textLocale", "stretchColumns", "contentInsetStart", "titleTextAppearance", "shapeAppearance")) {
            assertTrue(name, name in AndroidAttributeSetters.SPECIAL)
        }
        assertEquals("src", DynamicLayoutInflater.COMPAT_ALIASES["srcCompat"])
        assertEquals("drawableStart", DynamicLayoutInflater.COMPAT_ALIASES["drawableStartCompat"])
    }

    @Test
    fun ownMembersAndClassNamesListTheTable() {
        assertTrue("android.widget.TextView" in table.classNames())
        assertTrue("androidx.constraintlayout.widget.ConstraintLayout" in table.classNames(layout = true))
        val own = table.ownMembersOf("android.widget.TextView")
        assertTrue("android:text" in own)
        // Inherited View attributes belong to View.
        assertTrue("android:padding" !in own)
        assertTrue("android:padding" in table.ownMembersOf("android.view.View"))
        assertTrue("app:layout_constraintTop_toTopOf" in table.ownMembersOf("androidx.constraintlayout.widget.ConstraintLayout", layout = true))
    }
}
