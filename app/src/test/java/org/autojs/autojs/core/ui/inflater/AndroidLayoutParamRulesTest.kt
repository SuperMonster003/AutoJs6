package org.autojs.autojs.core.ui.inflater

import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Host roadmap `docs/dev/android-xml-attributes-roadmap.md` A3: every `layout_*` attribute the table lists for
 * ConstraintLayout, CoordinatorLayout, AppBarLayout and CollapsingToolbarLayout names a public field or setter of the
 * container's LayoutParams (or is a special or design-time attribute), so the fallback can set it.
 *
 * zh-CN: 宿主路线图 A3: 属性表为上述容器列出的每个 `layout_*` 属性都对应其 LayoutParams 的公开字段或 setter (或为特例 / 设计期属性).
 */
class AndroidLayoutParamRulesTest {

    private val table = AndroidAttributeTable.parse(File("src/main/assets/" + AndroidAttributeTable.ASSET_PATH).readText())

    @Test
    fun constraintLayoutNamesMapOntoItsFields() {
        assertEquals("topToTop", AndroidLayoutParamRules.property("layout_constraintTop_toTopOf"))
        assertEquals("startToEnd", AndroidLayoutParamRules.property("layout_constraintStart_toEndOf"))
        assertEquals("baselineToBaseline", AndroidLayoutParamRules.property("layout_constraintBaseline_toBaselineOf"))
        assertEquals("horizontalBias", AndroidLayoutParamRules.property("layout_constraintHorizontal_bias"))
        assertEquals("verticalChainStyle", AndroidLayoutParamRules.property("layout_constraintVertical_chainStyle"))
        assertEquals("matchConstraintPercentWidth", AndroidLayoutParamRules.property("layout_constraintWidth_percent"))
        assertEquals("matchConstraintDefaultHeight", AndroidLayoutParamRules.property("layout_constraintHeight_default"))
        assertEquals("guidePercent", AndroidLayoutParamRules.property("layout_constraintGuide_percent"))
        assertEquals("goneStartMargin", AndroidLayoutParamRules.property("layout_goneMarginStart"))
        assertEquals("circleConstraint", AndroidLayoutParamRules.property("layout_constraintCircle"))
        assertEquals("scrollFlags", AndroidLayoutParamRules.property("layout_scrollFlags"))
        assertTrue(AndroidLayoutParamRules.isDesignTime("layout_editor_absoluteX"))
    }

    @Test
    fun everyListedContainerAttributeHasAMember() {
        val containers = listOf(
            ConstraintLayout::class.java to ConstraintLayout.LayoutParams::class.java,
            CoordinatorLayout::class.java to CoordinatorLayout.LayoutParams::class.java,
            AppBarLayout::class.java to AppBarLayout.LayoutParams::class.java,
            CollapsingToolbarLayout::class.java to CollapsingToolbarLayout.LayoutParams::class.java,
        )
        val missing = ArrayList<String>()
        for ((container, params) in containers) {
            val members = table.layoutMembersOf(container).values.filter { it.attr.namespace == "app" }
            assertTrue(container.simpleName, members.isNotEmpty())
            for (member in members) {
                val name = member.attr.name
                // Container attributes the libraries list among the layout ones (constraintSet, layoutDescription) never reach the params.
                if (!name.startsWith("layout_") || AndroidLayoutParamRules.isDesignTime(name) || name in AndroidLayoutParamRules.SPECIAL) continue
                val property = AndroidLayoutParamRules.property(name)
                val setters = AndroidAttributeSetters.candidates(property) + ("set" + property.replaceFirstChar { it.uppercaseChar() } + "Id")
                val hasSetter = params.methods.any { method -> method.parameterTypes.size == 1 && setters.any { method.name.equals(it, ignoreCase = true) } }
                val hasField = params.fields.any { it.name.equals(property, ignoreCase = true) }
                if (!hasSetter && !hasField) missing += "${container.simpleName}:$name -> $property"
            }
        }
        // A9 moved ConstraintLayout's own and its helpers' attributes out of the children's params; what is left
        // without a member has no public one in the library.
        val unmapped = missing.filter { entry -> NO_PUBLIC_MEMBER.none { entry.contains(it) } }
        assertEquals("unmapped layout attributes: $unmapped", emptyList<String>(), unmapped)
        assertNotNull(table.findLayout(ConstraintLayout::class.java, "app", "layout_constraintTop_toTopOf"))
        // A9: container and helper attributes belong to the ConstraintLayout and helper elements, not to children.
        assertEquals(null, table.findLayout(ConstraintLayout::class.java, "app", "barrierDirection"))
        assertNotNull(table.find(androidx.constraintlayout.widget.Barrier::class.java, "app", "barrierDirection"))
        assertNotNull(table.find(androidx.constraintlayout.helper.widget.Flow::class.java, "app", "flow_horizontalGap"))
        assertNotNull(table.find(ConstraintLayout::class.java, "app", "layout_optimizationLevel"))
        assertTrue(AndroidLayoutParamRules.isDesignTime("layout_constraintTop_creator"))
        assertEquals("baselineMargin", AndroidLayoutParamRules.property("layout_marginBaseline"))
    }

    private companion object {
        val NO_PUBLIC_MEMBER = listOf("chainUseRtl", "layout_constraintLayoutDescription")
    }
}
