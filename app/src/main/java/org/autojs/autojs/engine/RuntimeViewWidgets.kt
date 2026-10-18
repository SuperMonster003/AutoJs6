package org.autojs.autojs.engine

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import org.json.JSONArray
import org.json.JSONObject

/** Native controls shared by XML inflation and the existing JSON layout renderer. */
internal object RuntimeViewWidgets {
    val aliases = mapOf(
        "hscroll" to "HorizontalScrollView", "horizontalScrollView" to "HorizontalScrollView",
        "space" to "Space", "relative" to "RelativeLayout", "card" to "Card",
        "CardView" to "Card", "spinner" to "Spinner", "seekbar" to "SeekBar",
        "ratingbar" to "RatingBar", "radiogroup" to "RadioGroup", "radio" to "RadioButton",
        "radiobutton" to "RadioButton", "toggle" to "ToggleButton", "togglebutton" to "ToggleButton",
        "checkedtext" to "CheckedTextView", "checkedtextview" to "CheckedTextView", "numberpicker" to "NumberPicker",
        "img" to "Image", "linear" to "Column", "webview" to "WebView", "webView" to "WebView",
        "drawer" to "Drawer", "DrawerLayout" to "Drawer", "toolbar" to "Toolbar", "viewpager" to "ViewPager", "viewPager" to "ViewPager"
    )
    val types = setOf("HorizontalScrollView", "Space", "RelativeLayout", "Card", "Spinner", "SeekBar",
        "RatingBar", "RadioGroup", "RadioButton", "ToggleButton", "CheckedTextView", "NumberPicker",
        "WebView", "Drawer", "Toolbar", "ViewPager")

    fun create(type: String, context: Context, descriptor: JSONObject, session: NodeBridgeUiSession,
               child: (JSONObject) -> View): View? {
        val id = descriptor.optString("id")
        fun emit(name: String, vararg values: Pair<String, Any?>) {
            val value = JSONObject()
            values.forEach { (key, item) -> value.put(key, item ?: JSONObject.NULL) }
            session.emitComponentEvent(name, id, value)
        }
        val view: View = when (type) {
            "Space" -> Space(context)
            "RelativeLayout" -> RelativeLayout(context)
            "Card" -> CardView(context)
            "HorizontalScrollView" -> HorizontalScrollView(context)
            "RadioGroup" -> RadioGroup(context).apply {
                setOnCheckedChangeListener { group, selected ->
                    val name = group.findViewById<View>(selected)?.tag as? String
                    emit("change", "checkedId" to name, "value" to name)
                }
            }
            "RadioButton" -> RadioButton(context).apply {
                setOnCheckedChangeListener { _, checked -> emit("change", "checked" to checked, "value" to checked) }
            }
            "ToggleButton" -> ToggleButton(context).apply {
                isAllCaps = false
                setOnCheckedChangeListener { _, checked -> emit("change", "checked" to checked, "value" to checked) }
            }
            "CheckedTextView" -> CheckedTextView(context).apply {
                val style = context.obtainStyledAttributes(intArrayOf(android.R.attr.listChoiceIndicatorMultiple))
                try { checkMarkDrawable = style.getDrawable(0) } finally { style.recycle() }
                setOnClickListener { toggle(); emit("change", "checked" to isChecked, "value" to isChecked) }
            }
            "Spinner" -> Spinner(context).apply {
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: AdapterView<*>?, selected: View?, position: Int, rowId: Long) {
                        emit("select", "index" to position, "selectedIndex" to position, "text" to selectedItem?.toString())
                    }
                }
            }
            "SeekBar" -> SeekBar(context).apply {
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(bar: SeekBar) { emit("startTracking") }
                    override fun onStopTrackingTouch(bar: SeekBar) { emit("stopTracking", "progress" to bar.progress) }
                    override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                        emit("change", "progress" to progress, "value" to progress, "fromUser" to fromUser)
                    }
                })
            }
            "RatingBar" -> RatingBar(context).apply {
                setOnRatingBarChangeListener { _, rating, fromUser -> emit("change", "rating" to rating, "value" to rating, "fromUser" to fromUser) }
            }
            "NumberPicker" -> NumberPicker(context).apply {
                maxValue = 100
                setOnValueChangedListener { _, old, next -> emit("change", "oldValue" to old, "value" to next) }
            }
            else -> return RuntimeViewComponents.create(type, context, descriptor, session, child)
        }
        val children = descriptor.optJSONArray("children") ?: JSONArray()
        if (view is ViewGroup && type in setOf("RelativeLayout", "Card", "RadioGroup", "HorizontalScrollView")) {
            val target = if (view is HorizontalScrollView) LinearLayout(context).also {
                it.orientation = LinearLayout.HORIZONTAL
                view.addView(it, ViewGroup.LayoutParams(-2, -1))
            } else view
            repeat(children.length()) { addChild(target, child(children.getJSONObject(it))) }
            if (target is RelativeLayout) applyRelativeRules(target, children)
        } else require(children.length() == 0) { "$type does not accept children" }
        return view
    }

    /** Preserve declared dimensions/margins/weight instead of replacing child LayoutParams. */
    fun addChild(parent: ViewGroup, child: View) {
        val old = child.layoutParams ?: ViewGroup.LayoutParams(-1, -2)
        val props = RuntimeViewAttributes.declared(child)
        require(!props.has("weight") || parent is LinearLayout) { "weight requires a linear parent" }
        require(!props.has("layoutGravity") || parent is LinearLayout || parent is FrameLayout || parent is DrawerLayout || parent is Toolbar) { "layoutGravity requires a linear/frame/drawer/toolbar parent" }
        val params: ViewGroup.MarginLayoutParams = when (parent) {
            is DrawerLayout -> DrawerLayout.LayoutParams(old.width, old.height).also {
                it.gravity = RuntimeViewAttributes.gravity(props.opt("layoutGravity") ?: 0)
            }
            is Toolbar -> Toolbar.LayoutParams(old.width, old.height).also {
                it.gravity = RuntimeViewAttributes.gravity(props.opt("layoutGravity") ?: android.view.Gravity.START)
            }
            is RadioGroup -> RadioGroup.LayoutParams(old.width, old.height).also {
                it.weight = props.optDouble("weight", 0.0).toFloat()
                it.gravity = RuntimeViewAttributes.gravity(props.opt("layoutGravity") ?: -1)
            }
            is LinearLayout -> LinearLayout.LayoutParams(old.width, old.height).also {
                it.weight = props.optDouble("weight", 0.0).toFloat()
                it.gravity = RuntimeViewAttributes.gravity(props.opt("layoutGravity") ?: -1)
            }
            is RelativeLayout -> RelativeLayout.LayoutParams(old.width, old.height)
            is FrameLayout -> FrameLayout.LayoutParams(old.width, old.height,
                RuntimeViewAttributes.gravity(props.opt("layoutGravity") ?: -1))
            else -> ViewGroup.MarginLayoutParams(old.width, old.height)
        }
        if (old is ViewGroup.MarginLayoutParams) params.setMargins(old.leftMargin, old.topMargin, old.rightMargin, old.bottomMargin)
        parent.addView(child, params)
    }

    private fun applyRelativeRules(parent: RelativeLayout, children: JSONArray) {
        val anchors = mapOf("below" to RelativeLayout.BELOW, "above" to RelativeLayout.ABOVE,
            "toLeftOf" to RelativeLayout.LEFT_OF, "toRightOf" to RelativeLayout.RIGHT_OF,
            "toStartOf" to RelativeLayout.START_OF, "toEndOf" to RelativeLayout.END_OF,
            "alignTop" to RelativeLayout.ALIGN_TOP, "alignBottom" to RelativeLayout.ALIGN_BOTTOM,
            "alignStart" to RelativeLayout.ALIGN_START, "alignEnd" to RelativeLayout.ALIGN_END)
        val flags = mapOf("alignParentTop" to RelativeLayout.ALIGN_PARENT_TOP, "alignParentBottom" to RelativeLayout.ALIGN_PARENT_BOTTOM,
            "alignParentStart" to RelativeLayout.ALIGN_PARENT_START, "alignParentEnd" to RelativeLayout.ALIGN_PARENT_END,
            "centerInParent" to RelativeLayout.CENTER_IN_PARENT, "centerHorizontal" to RelativeLayout.CENTER_HORIZONTAL,
            "centerVertical" to RelativeLayout.CENTER_VERTICAL)
        repeat(children.length()) { index ->
            val props = RuntimeViewAttributes.normalize(children.getJSONObject(index))
            val params = parent.getChildAt(index).layoutParams as RelativeLayout.LayoutParams
            anchors.forEach { (key, rule) -> if (props.has(key)) {
                val name = props.getString(key).removePrefix("@id/").removePrefix("@+id/")
                val anchor = (0 until parent.childCount).map(parent::getChildAt).firstOrNull { it.tag == name }
                require(anchor != null && anchor !== parent.getChildAt(index)) { "Unknown/self relative anchor: $name" }
                params.addRule(rule, anchor.id)
            } }
            flags.forEach { (key, rule) -> if (props.optBoolean(key)) params.addRule(rule) }
        }
    }
}
