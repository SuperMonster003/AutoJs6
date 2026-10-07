package org.autojs.plugin.compose.api.v2

import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.catalog.*
import org.autojs.plugin.compose.api.interop.AndroidViewInteropV1
import org.autojs.plugin.compose.api.model.*
import org.autojs.plugin.compose.api.model.ValueKind.*
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

/** Append-only V2 vocabulary. V1 catalog objects, model fields and optional factories are retained. */
object ComposeUiV2 {
    const val VERSION = 2
    const val REQUIRED_HOST_VERSION_CODE = 5322L
    const val FEATURE = "wide-components-v2"

    object Components {
        const val NAVIGATION_BAR = "NavigationBar"
        const val NAVIGATION_BAR_ITEM = "NavigationBarItem"
        const val NAVIGATION_RAIL = "NavigationRail"
        const val NAVIGATION_RAIL_ITEM = "NavigationRailItem"
        const val NAVIGATION_DRAWER = "NavigationDrawer"
        const val NAVIGATION_DRAWER_ITEM = "NavigationDrawerItem"
        const val TAB_ROW = "TabRow"
        const val TAB = "Tab"
        const val MODAL_BOTTOM_SHEET = "ModalBottomSheet"
        const val DROPDOWN_MENU = "DropdownMenu"
        const val DROPDOWN_MENU_ITEM = "DropdownMenuItem"
        const val DATE_PICKER = "DatePicker"
        const val TIME_PICKER = "TimePicker"
        const val HORIZONTAL_PAGER = "HorizontalPager"
        const val LAZY_VERTICAL_GRID = "LazyVerticalGrid"
        const val ASSIST_CHIP = "AssistChip"
        const val FILTER_CHIP = "FilterChip"
        const val INPUT_CHIP = "InputChip"
        const val BADGE = "Badge"
        const val SEGMENTED_BUTTON = "SegmentedButton"
        const val SEGMENTED_BUTTON_ITEM = "SegmentedButtonItem"
        const val FLOATING_ACTION_BUTTON = "FloatingActionButton"
        const val SEARCH_BAR = "SearchBar"
        const val TOOLTIP = "Tooltip"
        const val PULL_TO_REFRESH = "PullToRefresh"
    }
    object Props {
        const val SELECTED = "selected"
        const val ALWAYS_SHOW_LABEL = "alwaysShowLabel"
        const val GESTURES_ENABLED = "gesturesEnabled"
        const val SELECTED_INDEX = "selectedIndex"
        const val SCROLLABLE = "scrollable"
        const val CANCELABLE = "cancelable"
        const val SELECTED_DATE_MILLIS = "selectedDateMillis"
        const val YEAR_START = "yearStart"
        const val YEAR_END = "yearEnd"
        const val DISPLAY_MODE = "displayMode"
        const val SHOW_MODE_TOGGLE = "showModeToggle"
        const val HOUR = "hour"
        const val MINUTE = "minute"
        const val IS_24_HOUR = "is24Hour"
        const val PAGE = "page"
        const val PAGE_SPACING = "pageSpacing"
        const val COLUMNS = "columns"
        const val VISIBLE = "visible"
        const val MODE = "mode"
        const val QUERY = "query"
        const val EXPANDED = "expanded"
        const val REFRESHING = "refreshing"
    }
    object Slots {
        const val HEADER = "header"
        const val DRAWER_CONTENT = "drawerContent"
        const val BADGE = "badge"
        const val ANCHOR = "anchor"
        const val TOOLTIP = "tooltip"
    }
    object Events {
        const val OPEN_CHANGE = "openChange"
        const val DATE_CHANGE = "dateChange"
        const val DISPLAY_MODE_CHANGE = "displayModeChange"
        const val TIME_CHANGE = "timeChange"
        const val PAGE_CHANGE = "pageChange"
        const val SELECTED_CHANGE = "selectedChange"
        const val QUERY_CHANGE = "queryChange"
        const val SEARCH = "search"
        const val EXPANDED_CHANGE = "expandedChange"
        const val REFRESH = "refresh"
    }
    object Fields {
        const val OPEN = "open"
        const val SELECTED_DATE_MILLIS = "selectedDateMillis"
        const val DISPLAY_MODE = "displayMode"
        const val HOUR = "hour"
        const val MINUTE = "minute"
        const val PAGE = "page"
        const val SELECTED = "selected"
        const val EXPANDED = "expanded"
    }

    @JvmField val catalog: ComponentCatalog = create()
    @JvmField val interopCatalog = ComponentCatalog(catalog.components + AndroidViewInteropV1.component,
        catalog.modifiers, catalog.shortcuts, catalog.systemEvents)
    @JvmField val componentNames: Set<String> = java.util.Collections.unmodifiableSet(
        catalog.components.map { it.name }.toSet() - ComponentCatalog.V1.components.map { it.name }.toSet())

    /** Scalar and dependent bounds are checked by both the script bridge and the renderer store. */
    fun validateNode(node: UiNode, parentType: String? = null, checkParent: Boolean = true) {
        interopCatalog.validateNode(node, parentType)
        val expectedParent = when (node.type) {
            Components.NAVIGATION_BAR_ITEM -> Components.NAVIGATION_BAR
            Components.NAVIGATION_RAIL_ITEM -> Components.NAVIGATION_RAIL
            Components.TAB -> Components.TAB_ROW
            Components.SEGMENTED_BUTTON_ITEM -> Components.SEGMENTED_BUTTON
            else -> null
        }
        if (checkParent && expectedParent != null && parentType != expectedParent) {
            throw ComposeUiContractException(ComposeUiErrorCodes.SCOPE_MISMATCH,
                "${node.type} requires $expectedParent as its direct parent", node.nodeId)
        }
        when (node.type) {
            Components.LAZY_VERTICAL_GRID -> if ((node.props[ComposeUiProps.CONTENT_PADDING] as? UiValue.Dp)?.value?.let { it < 0 } == true) {
                invalid(node, ComposeUiProps.CONTENT_PADDING, "Content padding must be nonnegative")
            }
            Components.NAVIGATION_BAR, Components.NAVIGATION_RAIL, Components.SEGMENTED_BUTTON ->
                if (node.children.isEmpty()) invalid(node, "children", "At least one item is required")
            Components.TAB_ROW -> {
                val selected = number(node, Props.SELECTED_INDEX, 0.0).toInt()
                if (selected !in node.children.indices) invalid(node, Props.SELECTED_INDEX, "Index must name an existing tab")
            }
            Components.HORIZONTAL_PAGER -> {
                val page = number(node, Props.PAGE, 0.0).toInt()
                if (page !in node.children.indices) invalid(node, Props.PAGE, "Page must name an existing child")
            }
            Components.DATE_PICKER -> {
                val start = number(node, Props.YEAR_START, 1900.0).toInt()
                val end = number(node, Props.YEAR_END, 2100.0).toInt()
                if (start > end) invalid(node, Props.YEAR_START, "Year range is reversed")
                (node.props[Props.SELECTED_DATE_MILLIS] as? UiValue.Num)?.value?.let { millis ->
                    val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
                        gregorianChange = Date(Long.MIN_VALUE)
                        timeInMillis = millis.toLong()
                    }
                    if (millis.toLong() % MILLIS_PER_DAY != 0L || calendar.get(Calendar.ERA) != GregorianCalendar.AD ||
                        calendar.get(Calendar.YEAR) !in start..end) {
                        invalid(node, Props.SELECTED_DATE_MILLIS, "Date must be UTC midnight inside the year range")
                    }
                }
            }
        }
    }

    /** Validate the complete prospective tree before publishing any patch or borrowed view. */
    fun validateTree(tree: UiTree, allowDetachedRoot: Boolean = false) {
        val nodes = tree.nodes.associateBy { it.nodeId }
        val parents = HashMap<Int, String>()
        tree.nodes.forEach { node -> (node.children + node.slots.values).forEach { parents[it] = node.type } }
        tree.nodes.forEach { node ->
            val detached = allowDetachedRoot && node.nodeId == tree.rootId
            val syntheticParent = if (detached) node.modifier.mapNotNull {
                interopCatalog.modifier(it.name)?.allowedParents?.takeIf { parents -> parents.isNotEmpty() }?.toSet()
            }.reduceOrNull { a, b -> a intersect b }?.firstOrNull() else null
            validateNode(node, parents[node.nodeId] ?: syntheticParent, !detached)
            val childType = when (node.type) {
                Components.NAVIGATION_BAR -> Components.NAVIGATION_BAR_ITEM
                Components.NAVIGATION_RAIL -> Components.NAVIGATION_RAIL_ITEM
                Components.TAB_ROW -> Components.TAB
                Components.SEGMENTED_BUTTON -> Components.SEGMENTED_BUTTON_ITEM
                else -> null
            }
            if (node.slots.values.any { nodes.getValue(it).type in setOf(Components.NAVIGATION_BAR_ITEM,
                    Components.NAVIGATION_RAIL_ITEM, Components.TAB, Components.SEGMENTED_BUTTON_ITEM) }) {
                throw ComposeUiContractException(ComposeUiErrorCodes.SCOPE_MISMATCH,
                    "Scoped items must be primary children", node.nodeId)
            }
            if (childType != null && node.children.any { nodes.getValue(it).type != childType }) {
                invalid(node, "children", "Only $childType children are accepted")
            }
            if (node.type in setOf(Components.NAVIGATION_BAR, Components.NAVIGATION_RAIL) ||
                node.type == Components.SEGMENTED_BUTTON &&
                (node.props[Props.MODE] as? UiValue.Enum)?.value != "multi") {
                if (node.children.count { (nodes.getValue(it).props[Props.SELECTED] as? UiValue.Bool)?.value == true } > 1) {
                    invalid(node, Props.SELECTED, "At most one item may be selected")
                }
            }
        }
    }

    private fun number(node: UiNode, name: String, default: Double) = (node.props[name] as? UiValue.Num)?.value ?: default
    private fun invalid(node: UiNode, prop: String, message: String): Nothing =
        throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, message, node.nodeId, prop)
    private const val MILLIS_PER_DAY = 86_400_000L

    private fun create(): ComponentCatalog {
        val c = Components; val p = Props; val s = Slots; val e = Events; val f = Fields
        val base = ComponentCatalog.V1
        fun type(vararg kinds: ValueKind) = ValueType(kinds.toSet())
        fun enum(vararg values: String) = ValueType(setOf(ENUM), enumValues = values.toList())
        fun integer(minimum: Int, maximum: Int = Int.MAX_VALUE) = ValueType(setOf(NUMBER),
            minimum = minimum.toDouble(), maximum = maximum.toDouble(), integral = true)
        fun prop(name: String, type: ValueType, default: UiValue? = null) = PropSpec(name, type, defaultValue = default)
        fun bool(name: String, default: Boolean) = prop(name, type(BOOLEAN), UiValue.Bool(default))
        fun int(name: String, default: Int, minimum: Int = 0, maximum: Int = Int.MAX_VALUE) =
            prop(name, integer(minimum, maximum), UiValue.Num(default.toDouble()))
        fun event(name: String, vararg fields: PayloadFieldSpec) = EventSpec(name, fields.toList())
        fun field(name: String, type: PayloadType, required: Boolean = true) = PayloadFieldSpec(name, type, required)
        val baseEvents = base.requireComponent(ComposeUiComponents.TEXT).events
        val enabled = bool(ComposeUiProps.ENABLED, true)
        val selected = bool(p.SELECTED, false)
        val colors = listOf(prop(ComposeUiProps.CONTAINER_COLOR, type(COLOR, NULL)), prop(ComposeUiProps.CONTENT_COLOR, type(COLOR, NULL)))
        val content = SlotSpec(ComposeUiSlots.CONTENT)
        val label = SlotSpec(ComposeUiSlots.LABEL, true)
        val icon = SlotSpec(ComposeUiSlots.ICON)
        val leading = SlotSpec(ComposeUiSlots.LEADING_ICON)
        val trailing = SlotSpec(ComposeUiSlots.TRAILING_ICON)
        val dimension = ValueType(setOf(DP), minimum = 0.0)
        val openChange = event(e.OPEN_CHANGE, field(f.OPEN, PayloadType.BOOLEAN))
        val selectedChange = event(e.SELECTED_CHANGE, field(f.SELECTED, PayloadType.BOOLEAN))
        val displayMode = prop(p.DISPLAY_MODE, enum("picker", "input"), UiValue.Enum("picker"))
        val focusable = setOf(c.NAVIGATION_BAR_ITEM, c.NAVIGATION_RAIL_ITEM, c.NAVIGATION_DRAWER_ITEM,
            c.TAB, c.DROPDOWN_MENU_ITEM, c.ASSIST_CHIP, c.FILTER_CHIP, c.INPUT_CHIP,
            c.SEGMENTED_BUTTON_ITEM, c.FLOATING_ACTION_BUTTON, c.SEARCH_BAR)
        fun node(name: String, props: List<PropSpec> = emptyList(), slots: List<SlotSpec> = emptyList(),
            children: ChildrenPolicy = ChildrenPolicy.NONE, events: List<EventSpec> = emptyList(),
            commands: List<String> = if (name in focusable) listOf(ComposeUiCommands.FOCUS, ComposeUiCommands.BLUR) else emptyList()) =
            ComponentSpec(name, props, slots, baseEvents + events, children, commands = commands)
        val additions = mutableListOf<ComponentSpec>()
        additions += node(c.NAVIGATION_BAR, colors, children = ChildrenPolicy.MANY)
        additions += node(c.NAVIGATION_RAIL, colors, listOf(SlotSpec(s.HEADER)), ChildrenPolicy.MANY)
        for (name in listOf(c.NAVIGATION_BAR_ITEM, c.NAVIGATION_RAIL_ITEM)) additions += node(name,
            listOf(selected, enabled, bool(p.ALWAYS_SHOW_LABEL, true)), listOf(SlotSpec(ComposeUiSlots.ICON, true), SlotSpec(ComposeUiSlots.LABEL)))
        additions += node(c.NAVIGATION_DRAWER, listOf(bool(ComposeUiProps.OPEN, false), bool(p.GESTURES_ENABLED, true)),
            listOf(SlotSpec(s.DRAWER_CONTENT, true), content), ChildrenPolicy.SINGLE, listOf(openChange))
        additions += node(c.NAVIGATION_DRAWER_ITEM, listOf(selected, enabled), listOf(label, icon, SlotSpec(s.BADGE)))
        additions += node(c.TAB_ROW, listOf(int(p.SELECTED_INDEX, 0), bool(p.SCROLLABLE, false)) + colors, children = ChildrenPolicy.MANY)
        additions += node(c.TAB, listOf(enabled), listOf(SlotSpec(ComposeUiSlots.TEXT), icon))
        additions += node(c.MODAL_BOTTOM_SHEET, listOf(bool(ComposeUiProps.OPEN, false), bool(p.CANCELABLE, true),
            prop(ComposeUiProps.SHAPE, type(SHAPE))) + colors, listOf(content), ChildrenPolicy.SINGLE, listOf(event(ComposeUiEvents.DISMISS_REQUEST)))
        additions += node(c.DROPDOWN_MENU, listOf(bool(ComposeUiProps.OPEN, false)), listOf(SlotSpec(s.ANCHOR, true)),
            ChildrenPolicy.MANY, listOf(event(ComposeUiEvents.DISMISS_REQUEST)))
        additions += node(c.DROPDOWN_MENU_ITEM, listOf(enabled), listOf(SlotSpec(ComposeUiSlots.TEXT, true), leading, trailing))
        additions += node(c.DATE_PICKER, listOf(prop(p.SELECTED_DATE_MILLIS, ValueType(setOf(NUMBER, NULL),
            minimum = -12212553600000.0, maximum = 253402214400000.0, integral = true), UiValue.Null),
            int(p.YEAR_START, 1900, 1583, 9999), int(p.YEAR_END, 2100, 1583, 9999), displayMode, bool(p.SHOW_MODE_TOGGLE, true)),
            events = listOf(event(e.DATE_CHANGE, field(f.SELECTED_DATE_MILLIS, PayloadType.LONG, false)),
                event(e.DISPLAY_MODE_CHANGE, field(f.DISPLAY_MODE, PayloadType.STRING))))
        additions += node(c.TIME_PICKER, listOf(int(p.HOUR, 0, 0, 23), int(p.MINUTE, 0, 0, 59), bool(p.IS_24_HOUR, true), displayMode),
            events = listOf(event(e.TIME_CHANGE, field(f.HOUR, PayloadType.INTEGER), field(f.MINUTE, PayloadType.INTEGER))))
        additions += node(c.HORIZONTAL_PAGER, listOf(int(p.PAGE, 0), bool(ComposeUiProps.USER_SCROLL_ENABLED, true),
            prop(p.PAGE_SPACING, dimension, UiValue.Dp(0.0))), children = ChildrenPolicy.LAZY_ITEMS,
            events = listOf(event(e.PAGE_CHANGE, field(f.PAGE, PayloadType.INTEGER))), commands = listOf(ComposeUiCommands.SCROLL_TO))
        additions += node(c.LAZY_VERTICAL_GRID, listOf(int(p.COLUMNS, 2, 1, 64),
            prop(ComposeUiProps.SPACING, dimension, UiValue.Dp(0.0)),
            prop(ComposeUiProps.CONTENT_PADDING, ValueType(setOf(DP, LIST), listKinds = setOf(DP), listMinimum = 1, listMaximum = 4), UiValue.Dp(0.0)),
            bool(ComposeUiProps.USER_SCROLL_ENABLED, true), bool(ComposeUiProps.REVERSE_LAYOUT, false)),
            children = ChildrenPolicy.LAZY_ITEMS, commands = listOf(ComposeUiCommands.SCROLL_TO))
        additions += node(c.ASSIST_CHIP, listOf(enabled), listOf(label, leading, trailing))
        for (name in listOf(c.FILTER_CHIP, c.INPUT_CHIP)) additions += node(name, listOf(selected, enabled),
            listOf(label, leading, trailing), events = listOf(selectedChange))
        additions += node(c.BADGE, listOf(prop(ComposeUiProps.TEXT, type(STRING), UiValue.Str("")), bool(p.VISIBLE, true)),
            listOf(content), ChildrenPolicy.SINGLE)
        additions += node(c.SEGMENTED_BUTTON, listOf(prop(p.MODE, enum("single", "multi"), UiValue.Enum("single"))), children = ChildrenPolicy.MANY)
        additions += node(c.SEGMENTED_BUTTON_ITEM, listOf(selected, enabled), listOf(label, icon), events = listOf(selectedChange))
        additions += node(c.FLOATING_ACTION_BUTTON, colors + prop(ComposeUiProps.SHAPE, type(SHAPE)), listOf(content), ChildrenPolicy.MANY)
        additions += node(c.SEARCH_BAR, listOf(prop(p.QUERY, type(STRING), UiValue.Str("")), bool(p.EXPANDED, false), enabled),
            listOf(SlotSpec(ComposeUiSlots.PLACEHOLDER), leading, trailing, content), ChildrenPolicy.MANY,
            listOf(event(e.QUERY_CHANGE, field(ComposeUiEventFields.TEXT, PayloadType.STRING)),
                event(e.SEARCH, field(ComposeUiEventFields.TEXT, PayloadType.STRING)), event(e.EXPANDED_CHANGE, field(f.EXPANDED, PayloadType.BOOLEAN))))
        additions += node(c.TOOLTIP, listOf(bool(ComposeUiProps.OPEN, false)), listOf(SlotSpec(s.TOOLTIP, true), content),
            ChildrenPolicy.SINGLE, listOf(openChange))
        additions += node(c.PULL_TO_REFRESH, listOf(bool(p.REFRESHING, false), enabled), listOf(content), ChildrenPolicy.SINGLE, listOf(event(e.REFRESH)))
        return ComponentCatalog(base.components + additions, base.modifiers, base.shortcuts, base.systemEvents)
    }
}
