package org.autojs.autojs.runtime.api.augment.ui

import android.view.View
import org.autojs.autojs.core.ui.JsViewHelper
import org.autojs.autojs.core.ui.ViewExtras
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.runtime.ScriptRuntime
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/** Private runtime half of the compiler-owned Rhino classic-TSX contract. */
internal object TypeScriptTsxRuntime {

    object FragmentToken

    class Value internal constructor(
        internal val tagName: String?,
        internal val attributes: Map<String, String>,
        internal val onClick: Callable?,
        internal val children: List<Child>,
    )

    sealed interface Child {
        data class Element(val value: Value) : Child
        data class Text(val value: String) : Child
    }

    @ConsistentCopyVisibility
    data class RenderedLayout internal constructor(
        val xml: String,
        private val clickBindings: List<ClickBinding>,
    ) {
        fun attachClickHandlers(scriptRuntime: ScriptRuntime, root: View) {
            clickBindings.forEach { binding ->
                val target = requireNotNull(
                    JsViewHelper.findViewByStringId(root, binding.id),
                ) {
                    "Inflated TypeScript TSX view is missing event target '${binding.id}'."
                }
                ViewExtras.getNativeView(
                    scriptRuntime.topLevelScope,
                    target,
                    target::class.java,
                    scriptRuntime,
                ).viewPrototype.click(binding.listener)
            }
        }
    }

    internal data class ClickBinding(
        val id: String,
        val listener: Callable,
    )

    fun create(args: Array<out Any?>): Value {
        require(args.size >= 2) {
            "The private TypeScript TSX factory requires a tag and props argument."
        }
        val rawType = args[0].jsUnwrapped()
        val isFragment = rawType === FragmentToken
        val tagName = when {
            isFragment -> null
            rawType is String && TAG_NAME.matches(rawType) -> rawType
            rawType is String -> throw IllegalArgumentException(
                "TypeScript TSX tag '$rawType' is not a supported XML tag name.",
            )
            else -> throw IllegalArgumentException(
                "TypeScript TSX supports only intrinsic XML tags and fragments.",
            )
        }
        val parsedProps = parseProps(args[1], isFragment)
        val children = ArrayList<Child>()
        args.drop(2).forEach { child -> appendChild(child, children, 0) }
        return Value(
            tagName = tagName,
            attributes = parsedProps.first,
            onClick = parsedProps.second,
            children = children.toList(),
        )
    }

    fun renderOrNull(value: Any?): RenderedLayout? {
        val root = value.jsUnwrapped() as? Value ?: return null
        val roots = ArrayList<Value>()
        flattenRoot(root, roots, 0)
        require(roots.size == 1) {
            "A TypeScript TSX layout must resolve to exactly one root element."
        }
        val bindings = ArrayList<ClickBinding>()
        val ids = HashSet<String>()
        val state = RenderState()
        val xml = buildString {
            appendElement(roots.single(), bindings, ids, state, 1)
        }
        require(xml.toByteArray(Charsets.UTF_8).size <= MAXIMUM_XML_BYTES) {
            "TypeScript TSX layout exceeds the XML byte limit."
        }
        return RenderedLayout(xml, bindings.toList())
    }

    private fun parseProps(
        value: Any?,
        isFragment: Boolean,
    ): Pair<Map<String, String>, Callable?> {
        if (value.isJsNullish()) return emptyMap<String, String>() to null
        require(!isFragment) { "TypeScript TSX fragments cannot carry props." }
        val props = value as? Scriptable
            ?: throw IllegalArgumentException("TypeScript TSX props must be an object or null.")
        require(props.ids.size <= MAXIMUM_ATTRIBUTES_PER_ELEMENT) {
            "TypeScript TSX element has too many attributes."
        }
        val attributes = linkedMapOf<String, String>()
        var onClick: Callable? = null
        props.ids.forEach { id ->
            val name = id as? String
                ?: throw IllegalArgumentException("TypeScript TSX prop names must be strings.")
            require(ATTRIBUTE_NAME.matches(name)) {
                "TypeScript TSX prop '$name' is not a supported XML attribute name."
            }
            val raw = ScriptableObject.getProperty(props, name)
            if (name == ON_CLICK_PROPERTY) {
                if (!raw.isJsNullish()) {
                    require(raw is Callable) {
                        "TypeScript TSX onClick must be a JavaScript function."
                    }
                    onClick = raw
                }
            } else {
                require(!name.startsWith("on")) {
                    "TypeScript TSX event prop '$name' is not supported."
                }
                attributes[name] = scalarAttribute(raw, name)
            }
        }
        return attributes.toMap() to onClick
    }

    private fun scalarAttribute(value: Any?, name: String): String {
        val unwrapped = value.jsUnwrapped()
        require(!unwrapped.isJsNullish()) {
            "TypeScript TSX attribute '$name' cannot be null or undefined."
        }
        return when (unwrapped) {
            is String -> unwrapped.also(::requireValidXmlText)
            is Boolean -> unwrapped.toString()
            is Number -> {
                require(unwrapped.toDouble().isFinite()) {
                    "TypeScript TSX attribute '$name' must be a finite number."
                }
                Context.toString(unwrapped)
            }
            else -> throw IllegalArgumentException(
                "TypeScript TSX attribute '$name' must be a string, number, or boolean.",
            )
        }
    }

    private fun appendChild(value: Any?, destination: MutableList<Child>, depth: Int) {
        require(depth <= MAXIMUM_ARRAY_DEPTH) {
            "TypeScript TSX child arrays are nested too deeply."
        }
        val unwrapped = value.jsUnwrapped()
        when {
            unwrapped.isJsNullish() || unwrapped is Boolean -> Unit
            unwrapped is Value -> {
                if (unwrapped.tagName == null) {
                    unwrapped.children.forEach { child -> appendNormalizedChild(child, destination) }
                } else {
                    destination += Child.Element(unwrapped)
                }
            }
            unwrapped is NativeArray -> {
                require(unwrapped.length <= MAXIMUM_CHILDREN_PER_ARRAY) {
                    "TypeScript TSX child array is too large."
                }
                for (index in 0 until unwrapped.length) {
                    appendChild(unwrapped[index], destination, depth + 1)
                }
            }
            unwrapped is String -> {
                requireValidXmlText(unwrapped)
                if (unwrapped.isNotBlank()) destination += Child.Text(unwrapped)
            }
            unwrapped is Number -> {
                require(unwrapped.toDouble().isFinite()) {
                    "TypeScript TSX text children must be finite numbers."
                }
                destination += Child.Text(Context.toString(unwrapped))
            }
            else -> throw IllegalArgumentException(
                "TypeScript TSX children must be elements, arrays, text, numbers, or nullish values.",
            )
        }
    }

    private fun appendNormalizedChild(child: Child, destination: MutableList<Child>) {
        when (child) {
            is Child.Element -> {
                if (child.value.tagName == null) {
                    child.value.children.forEach { nested ->
                        appendNormalizedChild(nested, destination)
                    }
                } else {
                    destination += child
                }
            }
            is Child.Text -> destination += child
        }
    }

    private fun flattenRoot(value: Value, destination: MutableList<Value>, depth: Int) {
        require(depth <= MAXIMUM_TREE_DEPTH) { "TypeScript TSX tree is nested too deeply." }
        if (value.tagName != null) {
            destination += value
            return
        }
        value.children.forEach { child ->
            when (child) {
                is Child.Element -> flattenRoot(child.value, destination, depth + 1)
                is Child.Text -> require(child.value.isBlank()) {
                    "A TypeScript TSX root fragment cannot contain text."
                }
            }
        }
    }

    private fun StringBuilder.appendElement(
        element: Value,
        bindings: MutableList<ClickBinding>,
        ids: MutableSet<String>,
        state: RenderState,
        depth: Int,
    ) {
        require(depth <= MAXIMUM_TREE_DEPTH) { "TypeScript TSX tree is nested too deeply." }
        state.nodeCount += 1
        require(state.nodeCount <= MAXIMUM_ELEMENTS) {
            "TypeScript TSX layout contains too many elements."
        }
        val tagName = requireNotNull(element.tagName)
        val attributes = element.attributes.toMutableMap()
        val textChildren = element.children.filterIsInstance<Child.Text>()
        val elementChildren = element.children.filterIsInstance<Child.Element>()
        if (textChildren.isNotEmpty()) {
            require(elementChildren.isEmpty() && tagName in TEXT_CHILD_TAGS) {
                "TypeScript TSX text children are supported only by leaf text controls."
            }
            require(TEXT_ATTRIBUTE !in attributes) {
                "TypeScript TSX cannot combine a text child with a text attribute."
            }
            attributes[TEXT_ATTRIBUTE] = textChildren.joinToString("") { child -> child.value }
        }
        val explicitId = attributes[ID_ATTRIBUTE]
        if (explicitId != null) {
            require(ID_VALUE.matches(explicitId) && !explicitId.startsWith(RESERVED_ID_PREFIX)) {
                "TypeScript TSX id '$explicitId' is invalid or reserved."
            }
            require(ids.add(explicitId)) { "TypeScript TSX id '$explicitId' is duplicated." }
        }
        if (element.onClick != null) {
            val targetId = explicitId ?: "$RESERVED_ID_PREFIX${state.generatedIdCount++}"
            if (explicitId == null) {
                attributes[ID_ATTRIBUTE] = targetId
                check(ids.add(targetId))
            }
            bindings += ClickBinding(targetId, element.onClick)
        }
        append('<')
        append(tagName)
        attributes.toSortedMap().forEach { (name, value) ->
            append(' ')
            append(name)
            append("=\"")
            appendEscapedAttribute(value)
            append('"')
        }
        if (elementChildren.isEmpty()) {
            append(" />")
            return
        }
        append('>')
        elementChildren.forEach { child ->
            val childValue = child.value
            if (childValue.tagName == null) {
                childValue.children.forEach { fragmentChild ->
                    require(fragmentChild is Child.Element) {
                        "TypeScript TSX fragments inside layouts cannot contain text."
                    }
                    appendElement(fragmentChild.value, bindings, ids, state, depth + 1)
                }
            } else {
                appendElement(childValue, bindings, ids, state, depth + 1)
            }
        }
        append("</")
        append(tagName)
        append('>')
    }

    private fun StringBuilder.appendEscapedAttribute(value: String) {
        requireValidXmlText(value)
        value.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(character)
            }
        }
    }

    private fun requireValidXmlText(value: String) {
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                character == '\t' || character == '\n' || character == '\r' -> index += 1
                character.code in 0x20..0xd7ff || character.code in 0xe000..0xfffd -> index += 1
                character.isHighSurrogate() &&
                    index + 1 < value.length &&
                    value[index + 1].isLowSurrogate() -> index += 2
                else -> throw IllegalArgumentException(
                    "TypeScript TSX contains text that is not valid XML 1.0.",
                )
            }
        }
    }

    private class RenderState(
        var nodeCount: Int = 0,
        var generatedIdCount: Int = 0,
    )

    private const val ON_CLICK_PROPERTY = "onClick"
    private const val ID_ATTRIBUTE = "id"
    private const val TEXT_ATTRIBUTE = "text"
    private const val RESERVED_ID_PREFIX = "__autojs6_tsx_"
    private const val MAXIMUM_ATTRIBUTES_PER_ELEMENT = 128
    private const val MAXIMUM_CHILDREN_PER_ARRAY = 4_096L
    private const val MAXIMUM_ARRAY_DEPTH = 32
    private const val MAXIMUM_TREE_DEPTH = 64
    private const val MAXIMUM_ELEMENTS = 2_048
    private const val MAXIMUM_XML_BYTES = 1024 * 1024

    private val TAG_NAME = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")
    private val ATTRIBUTE_NAME = Regex("[A-Za-z_][A-Za-z0-9_.:-]{0,127}")
    private val ID_VALUE = Regex("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
    private val TEXT_CHILD_TAGS = setOf(
        "button",
        "checkbox",
        "input",
        "radio",
        "switch",
        "text",
    )
}
