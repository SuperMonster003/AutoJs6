package org.autojs.autojs.core.ui.inflater

import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.autojs.autojs.core.ui.inflater.AndroidAttributeTable.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Host roadmap `docs/dev/android-xml-attributes-roadmap.md` A1: the committed attribute table reads back with the
 * formats, values and API levels of the SDK and the bundled libraries, per view class and per parent class.
 *
 * zh-CN: 宿主路线图 A1: 已提交的属性表按 View 类与父容器类读出 SDK 与自带库的格式, 取值与起始 API.
 */
class AndroidAttributeTableTest {

    private val table = AndroidAttributeTable.parse(File("src/main/assets/" + AndroidAttributeTable.ASSET_PATH).readText())

    @Test
    fun viewClassesTakeTheirOwnAndInheritedAttributes() {
        val textSize = table.find(TextView::class.java, "android", "textSize")!!
        assertEquals(setOf(Format.DIMENSION), textSize.attr.formats)
        assertEquals("android:TextView", textSize.styleable)
        assertEquals(24, textSize.sinceApi)
        // Inherited from View, public since API 26.
        val tooltip = table.find(TextView::class.java, "android", "tooltipText")!!
        assertEquals("android:View", tooltip.styleable)
        assertEquals(26, tooltip.sinceApi)
        assertNull("layout params are not view attributes", table.find(View::class.java, "android", "layout_width"))
        assertNull(table.find(View::class.java, "android", "noSuchAttribute"))
    }

    @Test
    fun enumAndFlagValuesAreKept() {
        val gravity = table.attr("android", "gravity")!!
        assertEquals(setOf(Format.FLAG), gravity.formats)
        assertEquals(17, gravity.flags["center"])
        assertEquals(0x800005, gravity.flags["end"])
        val visibility = table.attr("android", "visibility")!!
        assertEquals(mapOf("visible" to 0, "invisible" to 1, "gone" to 2), visibility.enums)
    }

    @Test
    fun layoutParamsFollowTheParentClass() {
        val linear = table.layoutMembersOf(LinearLayout::class.java)
        assertEquals(setOf(Format.FLOAT), linear["android:layout_weight"]!!.attr.formats)
        assertTrue(linear.containsKey("android:layout_width"))
        assertTrue("MarginLayoutParams come from ViewGroup", linear.containsKey("android:layout_marginStart"))
        assertTrue(table.layoutMembersOf(RelativeLayout::class.java).containsKey("android:layout_toEndOf"))
        val constraint = table.findLayout(ConstraintLayout::class.java, "app", "layout_constraintTop_toTopOf")!!
        assertEquals("app:ConstraintLayout_Layout", constraint.styleable)
        assertEquals(0, constraint.sinceApi)
        assertEquals(mapOf("parent" to 0), constraint.attr.enums)
        assertNotNull(table.findLayout(CoordinatorLayout::class.java, "app", "layout_behavior"))
        assertNull(table.findLayout(LinearLayout::class.java, "app", "layout_constraintTop_toTopOf"))
    }

    @Test
    fun libraryViewsTakeAppAttributesAndPrefixesFallBack() {
        val autoSize = table.find(AppCompatTextView::class.java, "app", "autoSizeTextType")!!
        assertEquals("app:AppCompatTextView", autoSize.styleable)
        assertTrue(Format.ENUM in autoSize.attr.formats)
        // An unprefixed name tries android first, then app; a wrong prefix still finds the one that exists, and an
        // attribute both define (autoSizeTextType is also a platform attribute since API 26) follows the prefix.
        assertEquals("android", table.find(AppCompatTextView::class.java, null, "textSize")!!.attr.namespace)
        assertEquals("app", table.find(AppCompatTextView::class.java, "android", "drawableStartCompat")!!.attr.namespace)
        assertEquals("android", table.find(AppCompatTextView::class.java, "android", "autoSizeTextType")!!.attr.namespace)
        // The nearest class wins: AppCompatTextView's own styleable comes before TextView's.
        val members = table.membersOf(AppCompatTextView::class.java).values.map { it.styleable }
        assertTrue(members.indexOf("app:AppCompatTextView") < members.indexOf("android:TextView"))
    }
}
