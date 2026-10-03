package org.autojs.autojs.core.accessibility.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roadmap P3.1 / D18: the class-name preset rules and the equivalence of their regex export.
 * zh-CN: Roadmap P3.1 / D18: 类名预设规则及其正则导出的等价性.
 */
class ClassPresetTest {

    private val corpus = listOf(
        "android.widget.Button", "android.widget.ImageButton", "com.google.android.material.button.MaterialButton",
        "androidx.appcompat.widget.AppCompatButton", "android.widget.RadioButton", "android.widget.ToggleButton", "android.widget.CompoundButton",
        "android.widget.TextView", "androidx.appcompat.widget.AppCompatTextView", "android.widget.CheckedTextView",
        "android.widget.EditText", "android.widget.AutoCompleteTextView", "com.google.android.material.textfield.TextInputEditText",
        "android.widget.ImageView", "androidx.appcompat.widget.AppCompatImageView",
        "android.widget.CheckBox", "android.widget.Switch", "androidx.appcompat.widget.SwitchCompat", "com.google.android.material.materialswitch.MaterialSwitch",
        "androidx.recyclerview.widget.RecyclerView", "android.widget.ListView", "android.widget.ExpandableListView", "android.widget.ScrollView",
        "android.widget.HorizontalScrollView", "androidx.viewpager2.widget.ViewPager2", "android.widget.GridView",
        "android.widget.FrameLayout", "android.view.ViewGroup", "androidx.fragment.app.FragmentContainerView", "android.view.View", "android.webkit.WebView",
        "Button", "TextView", "",
    )

    private fun names(preset: ClassPreset) = corpus.filter { preset.matches(it) }.map(ClassPreset::simplify)

    @Test
    fun buttonsExcludeCompoundButtons() {
        assertEquals(listOf("Button", "ImageButton", "MaterialButton", "AppCompatButton", "Button"), names(ClassPreset.BUTTON))
    }

    @Test
    fun textExcludesAutoCompleteInputs() {
        assertEquals(listOf("TextView", "AppCompatTextView", "CheckedTextView", "TextView"), names(ClassPreset.TEXT))
        assertEquals(listOf("EditText", "AutoCompleteTextView", "TextInputEditText"), names(ClassPreset.INPUT))
    }

    @Test
    fun imagesTogglesScrollersAndContainers() {
        assertEquals(listOf("ImageView", "AppCompatImageView"), names(ClassPreset.IMAGE))
        assertEquals(listOf("RadioButton", "ToggleButton", "CompoundButton", "CheckBox", "Switch", "SwitchCompat", "MaterialSwitch"), names(ClassPreset.TOGGLE))
        assertEquals(listOf("RecyclerView", "ListView", "ExpandableListView", "ScrollView", "HorizontalScrollView", "ViewPager2", "GridView"), names(ClassPreset.SCROLL))
        assertEquals(listOf("FrameLayout", "ViewGroup", "FragmentContainerView"), names(ClassPreset.CONTAINER))
    }

    @Test
    fun nullAndPlainViewMatchNothing() {
        for (preset in ClassPreset.entries) {
            assertFalse(preset.name, preset.matches(null))
            assertFalse(preset.name, preset.matches("android.view.View"))
        }
        assertEquals(emptySet<ClassPreset>(), ClassPreset.of("android.webkit.WebView"))
        assertEquals(setOf(ClassPreset.TOGGLE), ClassPreset.of("android.widget.RadioButton"))
    }

    @Test
    fun regexExportIsEquivalentToTheRules() {
        for (preset in ClassPreset.entries) {
            val regex = Regex(preset.regex)
            for (name in corpus) {
                assertEquals("${preset.name} on $name", preset.matches(name), regex.matches(name))
            }
        }
        val filter = ClassPresetFilter(setOf(ClassPreset.IMAGE, ClassPreset.BUTTON))
        val combined = Regex(filter.regex)
        for (name in corpus) {
            assertEquals(name, ClassPreset.BUTTON.matches(name) || ClassPreset.IMAGE.matches(name), combined.matches(name))
        }
        assertTrue(filter.toString(), filter.toString().startsWith("classNameMatches(\"") && filter.toString().endsWith("\")"))
        assertFalse(filter.toString().contains("\\"))
    }

}
