package org.autojs.autojs.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import org.json.JSONArray
import org.json.JSONObject
import java.util.WeakHashMap

/** A bounded, typed property contract, with validation before native mutations. */
internal object RuntimeViewAttributes {
    val writable = setOf("text", "hint", "textSize", "textColor", "textStyle", "singleLine", "maxLines", "inputType",
        "checked", "enabled", "visible", "visibility", "alpha", "contentDescription", "clickable", "focusable",
        "width", "height", "padding", "margin", "backgroundColor", "gravity", "layoutGravity", "weight", "orientation",
        "min", "max", "progress", "indeterminate", "rating", "numStars", "stepSize", "isIndicator",
        "items", "selectedIndex", "checkedId", "textOn", "textOff", "value", "wrapSelectorWheel", "displayedValues",
        "cardCornerRadius", "cardElevation", "src", "scaleType", "adjustViewBounds")
    val aliases = mapOf("w" to "width", "h" to "height", "bg" to "backgroundColor", "entries" to "items",
        "layout_width" to "width", "layout_height" to "height", "layout_weight" to "weight",
        "layout_gravity" to "layoutGravity", "layout_margin" to "margin", "checkedButton" to "checkedId")
    private val declarations = WeakHashMap<View, JSONObject>()
    fun declared(view: View): JSONObject = declarations.getOrPut(view) { JSONObject() }

    fun normalize(props: JSONObject): JSONObject = JSONObject().apply {
        props.keys().forEach { original ->
            val short = original.removePrefix("android:")
            val name = aliases[short] ?: short.removePrefix("layout_")
            require(!has(name)) { "Duplicate native attribute alias: $name" }
            put(name, props.get(original))
        }
    }

    fun gravity(value: Any): Int {
        if (value is Number) return value.toInt()
        val values = mapOf("left" to Gravity.LEFT, "right" to Gravity.RIGHT, "start" to Gravity.START,
            "end" to Gravity.END, "top" to Gravity.TOP, "bottom" to Gravity.BOTTOM, "center" to Gravity.CENTER,
            "center_horizontal" to Gravity.CENTER_HORIZONTAL, "center_vertical" to Gravity.CENTER_VERTICAL,
            "fill" to Gravity.FILL, "fill_horizontal" to Gravity.FILL_HORIZONTAL, "fill_vertical" to Gravity.FILL_VERTICAL)
        return value.toString().split('|').fold(0) { result, token -> result or (values[token.trim()]
            ?: throw IllegalArgumentException("Unknown gravity: $token")) }
    }

    private fun number(value: Any, min: Double, max: Double): Double {
        val result = (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull()
        require(result != null && result.isFinite() && result in min..max) { "Invalid native numeric value: $value" }
        return result
    }
    private fun integer(value: Any, min: Int, max: Int): Int = number(value, min.toDouble(), max.toDouble()).also {
        require(it % 1.0 == 0.0) { "Native integer value required" }
    }.toInt()
    private fun boolean(value: Any): Boolean = when (value) {
        true, "true" -> true
        false, "false" -> false
        else -> throw IllegalArgumentException("Native boolean value required")
    }
    private fun color(value: Any): Int = if (value is Number) value.toInt() else Color.parseColor(value.toString())
    private fun dimension(view: View, value: Any): Int = when (value.toString()) {
        "*", "match_parent", "fill_parent", "-1" -> -1
        "auto", "wrap_content", "-2" -> -2
        else -> {
            val text = value.toString()
            val scale = if (text.endsWith("px")) 1f else view.resources.displayMetrics.density
            (number(text.removeSuffix("dp").removeSuffix("px"), 0.0, 1_000_000.0) * scale).toInt()
        }
    }
    private fun insets(view: View, value: Any): IntArray {
        val parts = when (value) {
            is JSONArray -> (0 until value.length()).map(value::get)
            is String -> value.trim().split(Regex("[ ,]+"))
            else -> listOf(value)
        }.map { dimension(view, it).also { n -> require(n >= 0) { "Insets must be nonnegative" } } }
        return when (parts.size) {
            1 -> IntArray(4) { parts[0] }
            2 -> intArrayOf(parts[0], parts[1], parts[0], parts[1])
            4 -> parts.toIntArray()
            else -> throw IllegalArgumentException("Insets require 1, 2 or 4 values")
        }
    }
    private fun items(value: Any): JSONArray = when (value) {
        is JSONArray -> JSONArray(value.toString())
        is String -> JSONArray(value.split('|'))
        else -> throw IllegalArgumentException("Items require an array or a pipe-separated string")
    }.also { require(it.length() <= 1024) { "Too many items" } }

    fun apply(view: View, raw: JSONObject, strict: Boolean = false, image: ((JSONObject) -> Bitmap)? = null) {
        val props = normalize(raw)
        if (strict) require(props.keys().asSequence().all { it in writable }) { "Unknown or read-only native View attribute" }
        val actions = ArrayList<() -> Unit>()
        fun prop(name: String, supported: Boolean = true, action: (Any) -> (() -> Unit)) {
            if (!props.has(name)) return
            require(supported) { "$name is not supported by ${view.javaClass.simpleName}" }
            actions += action(props.get(name))
        }
        val text = view as? TextView
        val progress = view as? ProgressBar
        val rating = view as? RatingBar
        val picker = view as? NumberPicker
        val spinner = view as? Spinner
        val compound = view as? Checkable

        prop("text", text != null) { value -> val s = value.toString(); { if (text!!.text.toString() != s) text.text = s } }
        prop("hint", text != null) { value -> val s = value.toString(); { text!!.hint = s } }
        prop("textColor", text != null) { value -> val c = color(value); { text!!.setTextColor(c) } }
        prop("textSize", text != null) { value ->
            val s = value.toString(); val n = number(s.removeSuffix("sp").removeSuffix("px"), 0.0, 1000.0).toFloat()
            val unit = if (s.endsWith("px")) TypedValue.COMPLEX_UNIT_PX else TypedValue.COMPLEX_UNIT_SP
            return@prop { text!!.setTextSize(unit, n) }
        }
        prop("textStyle", text != null) { value ->
            val style = mapOf("normal" to Typeface.NORMAL, "bold" to Typeface.BOLD, "italic" to Typeface.ITALIC,
                "bold|italic" to Typeface.BOLD_ITALIC)[value.toString()] ?: throw IllegalArgumentException("Invalid textStyle")
            return@prop { text!!.setTypeface(text.typeface, style) }
        }
        prop("singleLine", text != null) { value -> val flag = boolean(value); { text!!.setSingleLine(flag) } }
        prop("maxLines", text != null) { value -> val n = integer(value, 1, 10000); { text!!.maxLines = n } }
        prop("inputType", view is EditText) { value ->
            val types = mapOf("text" to InputType.TYPE_CLASS_TEXT, "textPassword" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
                "number" to InputType.TYPE_CLASS_NUMBER, "numberDecimal" to (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL),
                "phone" to InputType.TYPE_CLASS_PHONE, "textEmailAddress" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
            val n = if (value is Number) integer(value, 0, Int.MAX_VALUE) else types[value.toString()] ?: throw IllegalArgumentException("Unsupported inputType")
            return@prop { (view as EditText).inputType = n }
        }
        prop("checked", compound != null) { value -> val flag = boolean(value); { compound!!.isChecked = flag } }
        prop("enabled") { value -> val flag = boolean(value); { view.isEnabled = flag } }
        prop("visible") { value -> val flag = boolean(value); { view.visibility = if (flag) View.VISIBLE else View.GONE } }
        prop("visibility") { value ->
            val n = mapOf("visible" to View.VISIBLE, "invisible" to View.INVISIBLE, "gone" to View.GONE)[value.toString()]
                ?: throw IllegalArgumentException("Invalid visibility")
            return@prop { view.visibility = n }
        }
        prop("alpha") { value -> val n = number(value, 0.0, 1.0).toFloat(); { view.alpha = n } }
        prop("contentDescription") { value -> val s = if (value === JSONObject.NULL) null else value.toString(); { view.contentDescription = s } }
        prop("clickable") { value -> val flag = boolean(value); { view.isClickable = flag } }
        prop("focusable") { value -> val flag = boolean(value); { view.isFocusable = flag } }
        prop("backgroundColor") { value -> val c = color(value); { if (view is CardView) view.setCardBackgroundColor(c) else view.setBackgroundColor(c) } }
        for (name in listOf("width", "height")) prop(name) { value ->
            val n = dimension(view, value)
            return@prop { val params = view.layoutParams ?: ViewGroup.MarginLayoutParams(-2, -2)
                if (name == "width") params.width = n else params.height = n; view.layoutParams = params }
        }
        prop("padding") { value -> val n = insets(view, value); { view.setPadding(n[0], n[1], n[2], n[3]) } }
        prop("margin") { value -> val n = insets(view, value); {
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: ViewGroup.MarginLayoutParams(view.layoutParams ?: ViewGroup.LayoutParams(-2, -2))
            params.setMargins(n[0], n[1], n[2], n[3]); view.layoutParams = params
        } }
        prop("layoutGravity", view.parent == null || view.parent is LinearLayout || view.parent is FrameLayout) { value -> val n = gravity(value); {
            val params = view.layoutParams
            when (params) { is LinearLayout.LayoutParams -> params.gravity = n; is FrameLayout.LayoutParams -> params.gravity = n }
            view.requestLayout()
        } }
        prop("weight", view.parent == null || view.parent is LinearLayout) { value -> val n = number(value, 0.0, 10000.0).toFloat(); {
            val old = view.layoutParams ?: ViewGroup.LayoutParams(-2, -2)
            val params = old as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(old)
            params.weight = n; view.layoutParams = params
        } }
        prop("gravity", text != null || view is LinearLayout || view is RelativeLayout) { value -> val n = gravity(value); {
            when (view) { is TextView -> view.gravity = n; is LinearLayout -> view.gravity = n; is RelativeLayout -> view.gravity = n }
        } }
        prop("orientation", view is LinearLayout) { value ->
            val n = when(value.toString()) { "horizontal", "0" -> 0; "vertical", "1" -> 1; else -> throw IllegalArgumentException("Invalid orientation") }
            return@prop { (view as LinearLayout).orientation = n }
        }

        if (picker != null && listOf("min", "max", "value", "displayedValues").any(props::has)) {
            val min = integer(props.opt("min") ?: picker.minValue, 0, Int.MAX_VALUE)
            val max = integer(props.opt("max") ?: picker.maxValue, min, Int.MAX_VALUE)
            val value = if (props.has("value")) integer(props.get("value"), min, max) else picker.value.coerceIn(min, max)
            val labels = if (props.has("displayedValues")) props.optJSONArray("displayedValues")?.let { a -> (0 until a.length()).map(a::getString).toTypedArray() } else picker.displayedValues
            if (props.has("displayedValues")) require(props.isNull("displayedValues") || props.optJSONArray("displayedValues") != null)
            require(labels == null || labels.size.toLong() == max.toLong() - min + 1) { "NumberPicker displayedValues must match min/max" }
            actions += { picker.displayedValues = null; picker.minValue = 0; picker.maxValue = max; picker.minValue = min; picker.displayedValues = labels; picker.value = value }
        } else if (picker == null) {
            val min = if (android.os.Build.VERSION.SDK_INT >= 26) progress?.min ?: 0 else 0
            val nextMin = integer(props.opt("min") ?: min, 0, Int.MAX_VALUE)
            val nextMax = integer(props.opt("max") ?: progress?.max ?: 100, nextMin, Int.MAX_VALUE)
            prop("min", progress != null && android.os.Build.VERSION.SDK_INT >= 26) { { if (android.os.Build.VERSION.SDK_INT >= 26) progress!!.min = nextMin } }
            prop("max", progress != null) { { progress!!.max = nextMax } }
            prop("progress", progress != null) { value -> val n = integer(value, nextMin, nextMax); { progress!!.progress = n } }
            prop("value", progress != null || text != null || compound != null) { value -> when {
                compound != null -> { val flag = boolean(value); { compound.isChecked = flag } }
                progress != null -> { val n = integer(value, nextMin, nextMax); { progress.progress = n } }
                else -> { val s = value.toString(); { text!!.text = s } }
            } }
            prop("displayedValues", false) { {} }
        }
        prop("wrapSelectorWheel", picker != null) { value -> val flag = boolean(value); { picker!!.wrapSelectorWheel = flag } }
        prop("indeterminate", progress != null) { value -> val flag = boolean(value); { progress!!.isIndeterminate = flag } }
        prop("numStars", rating != null) { value -> val n = integer(value, 1, 100); { rating!!.numStars = n } }
        prop("stepSize", rating != null) { value -> val n = number(value, 0.01, 100.0).toFloat(); { rating!!.stepSize = n } }
        prop("rating", rating != null) { value -> val n = number(value, 0.0, (props.opt("numStars") ?: rating!!.numStars).toString().toDouble()).toFloat(); { rating!!.rating = n } }
        prop("isIndicator", rating != null) { value -> val flag = boolean(value); { rating!!.setIsIndicator(flag) } }
        prop("textOn", view is ToggleButton) { value -> val s = value.toString(); { (view as ToggleButton).textOn = s; view.isChecked = view.isChecked } }
        prop("textOff", view is ToggleButton) { value -> val s = value.toString(); { (view as ToggleButton).textOff = s; view.isChecked = view.isChecked } }
        val nextItems = if (props.has("items")) items(props.get("items")) else declared(view).optJSONArray("items")
        prop("items", spinner != null || view is ListView) { _ ->
            val labels = (0 until nextItems!!.length()).map { index -> nextItems.optJSONObject(index)?.optString("text") ?: nextItems.get(index).toString() }
            return@prop { val adapter = ArrayAdapter(view.context, if (spinner != null) android.R.layout.simple_spinner_item else android.R.layout.simple_list_item_1, labels)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                if (spinner != null) spinner.adapter = adapter else (view as ListView).adapter = adapter }
        }
        prop("selectedIndex", spinner != null || view is ListView) { value ->
            val count = nextItems?.length() ?: (view as AdapterView<*>).count
            val n = integer(value, if (count == 0) -1 else 0, count - 1)
            return@prop { if (spinner != null) spinner.setSelection(n) else (view as ListView).setSelection(n) }
        }
        prop("checkedId", view is RadioGroup) { value ->
            val target = if (value === JSONObject.NULL) null else (0 until (view as RadioGroup).childCount).map(view::getChildAt).firstOrNull { it.tag == value.toString().removePrefix("@id/").removePrefix("@+id/") }
            require(value === JSONObject.NULL || target is RadioButton) { "checkedId must identify a child RadioButton" }
            return@prop { (view as RadioGroup).check(target?.id ?: View.NO_ID) }
        }
        prop("cardCornerRadius", view is CardView) { value -> val n = dimension(view, value).also { require(it >= 0) }; { (view as CardView).radius = n.toFloat() } }
        prop("cardElevation", view is CardView) { value -> val n = dimension(view, value).also { require(it >= 0) }; { (view as CardView).cardElevation = n.toFloat() } }
        prop("adjustViewBounds", view is ImageView) { value -> val flag = boolean(value); { (view as ImageView).adjustViewBounds = flag } }
        prop("scaleType", view is ImageView) { value ->
            val key = value.toString().replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase(java.util.Locale.ROOT)
            val type = ImageView.ScaleType.valueOf(key)
            return@prop { (view as ImageView).scaleType = type }
        }
        prop("src", view is ImageView) { value ->
            when {
                value === JSONObject.NULL -> { { (view as ImageView).setImageDrawable(null) } }
                value is JSONObject -> {
                    require(value.optString("__autojs6ImageHandle").isNotBlank()) { "Image src requires an execution-owned image handle" }
                    require(image != null) { "Image handles require an execution-owned View" }
                    val bitmap = image(value);
                    return@prop { (view as ImageView).setImageBitmap(bitmap) }
                }
                else -> {
                    val path = value.toString(); require(path.startsWith("@drawable/") || path.startsWith("@android:drawable/")) { "Image src requires an image handle or drawable resource" }
                    val packageName = if (path.startsWith("@android:")) "android" else view.context.packageName
                    val id = view.resources.getIdentifier(path.substringAfter('/'), "drawable", packageName)
                    require(id != 0) { "Unknown drawable: $path" }
                    return@prop { (view as ImageView).setImageResource(id) }
                }
            }
        }
        actions.forEach { it() }
        props.keys().forEach { key -> declared(view).put(key, if (key == "items") nextItems else props.get(key)) }
    }

    fun snapshot(view: View): JSONObject = JSONObject().put("id", view.tag ?: JSONObject.NULL).put("type", view.javaClass.simpleName)
        .put("width", view.width).put("height", view.height).put("attached", view.isAttachedToWindow)
        .put("enabled", view.isEnabled).put("visible", view.visibility == View.VISIBLE)
        .put("visibility", when(view.visibility) { View.GONE -> "gone"; View.INVISIBLE -> "invisible"; else -> "visible" })
        .put("alpha", view.alpha.toDouble()).put("clickable", view.isClickable).put("focusable", view.isFocusable)
        .put("contentDescription", view.contentDescription?.toString() ?: JSONObject.NULL)
        .put("padding", JSONArray(listOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)))
        .put("backgroundColor", if (view is CardView) view.cardBackgroundColor.defaultColor else (view.background as? ColorDrawable)?.color ?: JSONObject.NULL)
        .apply {
            view.layoutParams?.let { params ->
                put("layoutWidth", params.width); put("layoutHeight", params.height)
                if (params is ViewGroup.MarginLayoutParams) put("margin", JSONArray(listOf(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin)))
                if (params is LinearLayout.LayoutParams) { put("weight", params.weight.toDouble()); put("layoutGravity", params.gravity) }
                if (params is FrameLayout.LayoutParams) put("layoutGravity", params.gravity)
            }
            if (view is TextView) {
                put("text", view.text.toString()); put("hint", view.hint?.toString().orEmpty())
                put("textSize", view.textSize / view.resources.displayMetrics.scaledDensity.toDouble()); put("textColor", view.currentTextColor)
                put("maxLines", view.maxLines); put("singleLine", view.maxLines == 1); put("gravity", view.gravity)
                put("textStyle", when(view.typeface?.style) { 1 -> "bold"; 2 -> "italic"; 3 -> "bold|italic"; else -> "normal" })
                if (view is EditText) { put("inputType", view.inputType); put("value", view.text.toString()) }
            }
            if (view is Checkable) { put("checked", view.isChecked); put("value", view.isChecked) }
            if (view is ToggleButton) { put("textOn", view.textOn.toString()); put("textOff", view.textOff.toString()) }
            if (view is LinearLayout) { put("orientation", if (view.orientation == 0) "horizontal" else "vertical"); put("gravity", view.gravity) }
            if (view is RelativeLayout) put("gravity", view.gravity)
            if (view is ProgressBar) {
                put("min", if (android.os.Build.VERSION.SDK_INT >= 26) view.min else 0); put("max", view.max)
                put("progress", view.progress); put("value", view.progress); put("indeterminate", view.isIndeterminate)
            }
            if (view is RatingBar) { put("rating", view.rating.toDouble()); put("numStars", view.numStars); put("stepSize", view.stepSize.toDouble()); put("isIndicator", view.isIndicator) }
            if (view is Spinner || view is ListView) { put("items", declared(view).optJSONArray("items") ?: JSONArray()); put("selectedIndex", (view as AdapterView<*>).selectedItemPosition) }
            if (view is RadioGroup) put("checkedId", view.findViewById<View>(view.checkedRadioButtonId)?.tag ?: JSONObject.NULL)
            if (view is NumberPicker) { put("min", view.minValue); put("max", view.maxValue); put("value", view.value); put("wrapSelectorWheel", view.wrapSelectorWheel); put("displayedValues", view.displayedValues?.let { JSONArray(it.toList()) } ?: JSONObject.NULL) }
            if (view is CardView) { put("cardCornerRadius", view.radius.toDouble()); put("cardElevation", view.cardElevation.toDouble()) }
            if (view is ImageView) { put("src", declared(view).opt("src") ?: JSONObject.NULL); put("scaleType", view.scaleType.name); put("adjustViewBounds", view.adjustViewBounds); put("drawableWidth", view.drawable?.intrinsicWidth ?: 0); put("drawableHeight", view.drawable?.intrinsicHeight ?: 0) }
        }
}
