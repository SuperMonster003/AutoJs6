package org.autojs.autojs.runtime.api.augment.ui

import android.content.Context as AndroidContext
import android.view.View
import androidx.annotation.StringRes
import org.autojs.autojs6.R
import org.autojs.autojs.core.ui.JsViewHelper
import org.autojs.autojs.core.ui.ViewExtras
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.compose.Compose
import org.autojs.autojs.runtime.api.augment.compose.ComposeNode
import org.autojs.autojs.runtime.api.augment.compose.ComposeNodeNativeObject
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/** Private runtime half of the compiler-owned Rhino classic-TSX contract. */
internal object TypeScriptTsxRuntime {

    object FragmentToken

    fun interface MessageResolver {
        fun resolve(@StringRes resourceId: Int, formatArgs: Array<out Any>): String
    }

    fun messageResolver(context: AndroidContext): MessageResolver = MessageResolver { resourceId, formatArgs ->
        context.getString(resourceId, *formatArgs)
    }

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
        private val messages: MessageResolver,
    ) {
        fun attachClickHandlers(scriptRuntime: ScriptRuntime, root: View) {
            clickBindings.forEach { binding ->
                val target = requireNotNull(
                    JsViewHelper.findViewByStringId(root, binding.id),
                ) {
                    messages.text(R.string.error_typescript_tsx_event_target_missing, binding.id)
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

    fun create(args: Array<out Any?>, messages: MessageResolver, compose: Compose? = null): Any = composeBoundary(compose) {
        require(args.size >= 2) {
            messages.text(R.string.error_typescript_tsx_factory_arguments)
        }
        val rawType = unwrapTsxValue(args[0])
        val isFragment = rawType === FragmentToken
        if (isFragment) {
            parseProps(args[1], true, messages)
            return@composeBoundary TypeScriptTsxFragment.create(compose?.graph?.tsxOwner, args.drop(2))
        }
        if (compose?.isTsxType(rawType) == true) {
            return@composeBoundary compose.createTsx(rawType, args[1], args.drop(2))
        }
        val tagName = when {
            isFragment -> null
            rawType is String && TAG_NAME.matches(rawType) -> rawType
            rawType is String -> throw IllegalArgumentException(
                messages.text(R.string.error_typescript_tsx_tag_name_unsupported, rawType),
            )
            else -> throw IllegalArgumentException(
                messages.text(R.string.error_typescript_tsx_supports_intrinsic_only),
            )
        }
        val parsedProps = parseProps(args[1], isFragment, messages)
        val children = ArrayList<Child>()
        args.drop(2).forEach { child -> appendChild(child, children, 0, messages, compose?.graph?.tsxOwner) }
        Value(
            tagName = tagName,
            attributes = parsedProps.first,
            onClick = parsedProps.second,
            children = children.toList(),
        )
    }

    fun renderOrNull(value: Any?, messages: MessageResolver, compose: Compose? = null): RenderedLayout? = composeBoundary(compose) {
        val raw = unwrapTsxValue(value)
        rejectComposeNode(raw)
        if (raw !is Value && raw !is TypeScriptTsxFragment) return@composeBoundary null
        val roots = ArrayList<Value>()
        if (raw is Value) flattenRoot(raw, roots, 0, messages)
        else {
            val children = ArrayList<Child>()
            appendChild(raw, children, 0, messages, compose?.graph?.tsxOwner)
            children.forEach { child -> when (child) {
                is Child.Element -> flattenRoot(child.value, roots, 0, messages)
                is Child.Text -> require(child.value.isBlank()) {
                    messages.text(R.string.error_typescript_tsx_root_fragment_text)
                }
            } }
        }
        require(roots.size == 1) {
            messages.text(R.string.error_typescript_tsx_root_count)
        }
        val bindings = ArrayList<ClickBinding>()
        val ids = HashSet<String>()
        val state = RenderState()
        val xml = buildString {
            appendElement(roots.single(), bindings, ids, state, 1, messages)
        }
        require(xml.toByteArray(Charsets.UTF_8).size <= MAXIMUM_XML_BYTES) {
            messages.text(R.string.error_typescript_tsx_layout_xml_limit)
        }
        RenderedLayout(xml, bindings.toList(), messages)
    }

    private fun <T> composeBoundary(compose: Compose?, action: () -> T): T = try { action() }
    catch (failure: ComposeUiContractException) {
        if (compose == null) throw failure
        compose.guardError { throw failure }
    }

    private fun rejectComposeNode(value: Any?) {
        if (value is ComposeNode || value is ComposeNodeNativeObject) {
            throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT)
        }
    }

    private fun parseProps(
        value: Any?,
        isFragment: Boolean,
        messages: MessageResolver,
    ): Pair<Map<String, String>, Callable?> {
        if (value.isJsNullish()) return emptyMap<String, String>() to null
        require(!isFragment) { messages.text(R.string.error_typescript_tsx_fragment_props) }
        val props = value as? Scriptable
            ?: throw IllegalArgumentException(
                messages.text(R.string.error_typescript_tsx_props_object),
            )
        require(props.ids.size <= MAXIMUM_ATTRIBUTES_PER_ELEMENT) {
            messages.text(R.string.error_typescript_tsx_props_attributes_limit)
        }
        val attributes = linkedMapOf<String, String>()
        var onClick: Callable? = null
        props.ids.forEach { id ->
            val name = id as? String
                ?: throw IllegalArgumentException(
                    messages.text(R.string.error_typescript_tsx_prop_names_strings),
                )
            require(ATTRIBUTE_NAME.matches(name)) {
                messages.text(R.string.error_typescript_tsx_attribute_name_unsupported, name)
            }
            val raw = ScriptableObject.getProperty(props, name)
            if (name == ON_CLICK_PROPERTY) {
                if (!raw.isJsNullish()) {
                    require(raw is Callable) {
                        messages.text(R.string.error_typescript_tsx_onclick_function)
                    }
                    onClick = raw
                }
            } else {
                require(!name.startsWith("on")) {
                    messages.text(R.string.error_typescript_tsx_event_unsupported, name)
                }
                attributes[name] = scalarAttribute(raw, name, messages)
            }
        }
        return attributes.toMap() to onClick
    }

    private fun scalarAttribute(
        value: Any?,
        name: String,
        messages: MessageResolver,
    ): String {
        val unwrapped = unwrapTsxValue(value)
        require(!unwrapped.isJsNullish()) {
            messages.text(R.string.error_typescript_tsx_attribute_nullish, name)
        }
        return when (unwrapped) {
            is String -> unwrapped.also { requireValidXmlText(it, messages) }
            is Boolean -> unwrapped.toString()
            is Number -> {
                require(unwrapped.toDouble().isFinite()) {
                    messages.text(R.string.error_typescript_tsx_attribute_finite_number, name)
                }
                Context.toString(unwrapped)
            }
            else -> throw IllegalArgumentException(
                messages.text(R.string.error_typescript_tsx_attribute_scalar_required, name),
            )
        }
    }

    private fun appendChild(
        value: Any?,
        destination: MutableList<Child>,
        depth: Int,
        messages: MessageResolver,
        owner: Any? = null,
    ) {
        require(depth <= MAXIMUM_ARRAY_DEPTH) {
            messages.text(R.string.error_typescript_tsx_child_arrays_too_deep)
        }
        val unwrapped = unwrapTsxValue(value)
        rejectComposeNode(unwrapped)
        when {
            unwrapped.isJsNullish() || unwrapped is Boolean -> Unit
            unwrapped is TypeScriptTsxFragment -> {
                if (unwrapped.owner !== owner) throw ComposeUiContractException(ComposeUiErrorCodes.NODE_DETACHED)
                require(depth + unwrapped.arrayDepth <= MAXIMUM_ARRAY_DEPTH) {
                    messages.text(R.string.error_typescript_tsx_child_arrays_too_deep)
                }
                require(unwrapped.largestArray <= MAXIMUM_CHILDREN_PER_ARRAY) {
                    messages.text(R.string.error_typescript_tsx_child_array_too_large)
                }
                unwrapped.values.forEach { appendChild(it, destination, depth, messages, owner) }
            }
            unwrapped is Value -> {
                if (unwrapped.tagName == null) {
                    unwrapped.children.forEach { child -> appendNormalizedChild(child, destination) }
                } else {
                    destination += Child.Element(unwrapped)
                }
            }
            unwrapped is NativeArray -> {
                require(unwrapped.length <= MAXIMUM_CHILDREN_PER_ARRAY) {
                    messages.text(R.string.error_typescript_tsx_child_array_too_large)
                }
                for (index in 0 until unwrapped.length) {
                    appendChild(unwrapped[index], destination, depth + 1, messages, owner)
                }
            }
            unwrapped is List<*> || unwrapped is Array<*> -> {
                // The former generic unwrapper normalized Java collections into NativeArray.
                // Preserve that XML behavior explicitly while retaining branded Scriptable values.
                val values = if (unwrapped is List<*>) unwrapped else (unwrapped as Array<*>).asList()
                require(values.size <= MAXIMUM_CHILDREN_PER_ARRAY) {
                    messages.text(R.string.error_typescript_tsx_child_array_too_large)
                }
                values.forEach { appendChild(it, destination, depth + 1, messages, owner) }
            }
            unwrapped is String -> {
                requireValidXmlText(unwrapped, messages)
                if (unwrapped.isNotBlank()) destination += Child.Text(unwrapped)
            }
            unwrapped is Number -> {
                require(unwrapped.toDouble().isFinite()) {
                    messages.text(R.string.error_typescript_tsx_text_children_finite)
                }
                destination += Child.Text(Context.toString(unwrapped))
            }
            else -> throw IllegalArgumentException(
                messages.text(R.string.error_typescript_tsx_children_unsupported),
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

    private fun flattenRoot(
        value: Value,
        destination: MutableList<Value>,
        depth: Int,
        messages: MessageResolver,
    ) {
        require(depth <= MAXIMUM_TREE_DEPTH) {
            messages.text(R.string.error_typescript_tsx_tree_too_deep)
        }
        if (value.tagName != null) {
            destination += value
            return
        }
        value.children.forEach { child ->
            when (child) {
                is Child.Element -> flattenRoot(child.value, destination, depth + 1, messages)
                is Child.Text -> require(child.value.isBlank()) {
                    messages.text(R.string.error_typescript_tsx_root_fragment_text)
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
        messages: MessageResolver,
    ) {
        require(depth <= MAXIMUM_TREE_DEPTH) {
            messages.text(R.string.error_typescript_tsx_tree_too_deep)
        }
        state.nodeCount += 1
        require(state.nodeCount <= MAXIMUM_ELEMENTS) {
            messages.text(R.string.error_typescript_tsx_layout_elements_limit)
        }
        val tagName = requireNotNull(element.tagName)
        val attributes = element.attributes.toMutableMap()
        val textChildren = element.children.filterIsInstance<Child.Text>()
        val elementChildren = element.children.filterIsInstance<Child.Element>()
        if (textChildren.isNotEmpty()) {
            require(elementChildren.isEmpty() && tagName in TEXT_CHILD_TAGS) {
                messages.text(R.string.error_typescript_tsx_text_children_leaf_only)
            }
            require(TEXT_ATTRIBUTE !in attributes) {
                messages.text(R.string.error_typescript_tsx_text_attribute_conflict)
            }
            attributes[TEXT_ATTRIBUTE] = textChildren.joinToString("") { child -> child.value }
        }
        val explicitId = attributes[ID_ATTRIBUTE]
        if (explicitId != null) {
            require(ID_VALUE.matches(explicitId) && !explicitId.startsWith(RESERVED_ID_PREFIX)) {
                messages.text(R.string.error_typescript_tsx_id_invalid, explicitId)
            }
            require(ids.add(explicitId)) {
                messages.text(R.string.error_typescript_tsx_id_duplicate, explicitId)
            }
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
            appendEscapedAttribute(value, messages)
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
                        messages.text(R.string.error_typescript_tsx_fragment_layout_text)
                    }
                    appendElement(
                        fragmentChild.value,
                        bindings,
                        ids,
                        state,
                        depth + 1,
                        messages,
                    )
                }
            } else {
                appendElement(childValue, bindings, ids, state, depth + 1, messages)
            }
        }
        append("</")
        append(tagName)
        append('>')
    }

    private fun StringBuilder.appendEscapedAttribute(value: String, messages: MessageResolver) {
        requireValidXmlText(value, messages)
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

    private fun requireValidXmlText(value: String, messages: MessageResolver) {
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
                    messages.text(R.string.error_typescript_tsx_invalid_xml_text),
                )
            }
        }
    }

    private fun MessageResolver.text(
        @StringRes resourceId: Int,
        vararg formatArgs: Any,
    ): String = resolve(resourceId, formatArgs)

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
