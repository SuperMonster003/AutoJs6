package org.autojs.plugin.compose.api.catalog

import org.autojs.plugin.compose.api.ComposeUiComponents as C
import org.autojs.plugin.compose.api.ComposeUiProps as P
import org.autojs.plugin.compose.api.ComposeUiPropAliases as A
import org.autojs.plugin.compose.api.ComposeUiSlots as S
import org.autojs.plugin.compose.api.ComposeUiEvents as E
import org.autojs.plugin.compose.api.ComposeUiEventFields as F
import org.autojs.plugin.compose.api.ComposeUiModifiers as M
import org.autojs.plugin.compose.api.ComposeUiShortcuts as Q
import org.autojs.plugin.compose.api.ComposeUiCommands as K
import org.autojs.plugin.compose.api.model.*
import org.autojs.plugin.compose.api.model.ValueKind.*

internal object V1Catalog {
    private fun type(vararg kinds: ValueKind) = ValueType(kinds.toSet())
    private val string = type(STRING)
    private val boolean = type(BOOLEAN)
    private val number = ValueType(setOf(NUMBER), minimum = -Float.MAX_VALUE.toDouble(), maximum = Float.MAX_VALUE.toDouble())
    private val positiveNumber = ValueType(setOf(NUMBER), minimum = Float.MIN_VALUE.toDouble(), maximum = Float.MAX_VALUE.toDouble())
    private val fraction = ValueType(setOf(NUMBER), minimum = 0.0, maximum = 1.0)
    private val dimension = ValueType(setOf(DP), minimum = 0.0)
    private val signedDimension = type(DP)
    private val textSize = ValueType(setOf(SP), minimum = 0.0, minimumExclusive = true)
    private val color = type(COLOR, NULL)
    private val shape = type(SHAPE)
    private val integer = ValueType(setOf(NUMBER), minimum = 0.0, maximum = Int.MAX_VALUE.toDouble(), integral = true)
    private val positiveInteger = ValueType(setOf(NUMBER), minimum = 1.0, maximum = Int.MAX_VALUE.toDouble(), integral = true)
    private val typographyNames = listOf("displayLarge", "displayMedium", "displaySmall", "headlineLarge", "headlineMedium", "headlineSmall", "titleLarge", "titleMedium", "titleSmall", "bodyLarge", "bodyMedium", "bodySmall", "labelLarge", "labelMedium", "labelSmall")
    private val boxAlignments = listOf("topStart", "topCenter", "topEnd", "centerStart", "center", "centerEnd", "bottomStart", "bottomCenter", "bottomEnd")
    private val verticalArrangements = listOf("top", "center", "bottom", "spaceBetween", "spaceAround", "spaceEvenly")
    private val horizontalArrangements = listOf("start", "center", "end", "spaceBetween", "spaceAround", "spaceEvenly")
    private fun enum(vararg values: String) = ValueType(setOf(ENUM), enumValues = values.toList())
    private fun prop(name: String, type: ValueType, default: UiValue? = null, required: Boolean = false, aliases: List<String> = emptyList(), update: PropertyUpdate = PropertyUpdate.SET_PROPS) =
        PropSpec(name, type, required, default, aliases, update)
    private fun field(name: String, type: PayloadType, required: Boolean = true) = PayloadFieldSpec(name, type, required)
    private val focus = EventSpec(E.FOCUS_CHANGE, listOf(field(F.FOCUSED, PayloadType.BOOLEAN)))
    private val scroll = EventSpec(E.SCROLL, listOf(field(F.FIRST_VISIBLE_INDEX, PayloadType.INTEGER, false), field(F.OFFSET, PayloadType.INTEGER)))
    private val baseEvents = listOf(EventSpec(E.CLICK), EventSpec(E.LONG_CLICK), focus, scroll)
    private val checked = EventSpec(E.CHECKED_CHANGE, listOf(field(F.CHECKED, PayloadType.BOOLEAN)))
    private val sliderChange = EventSpec(E.VALUE_CHANGE, listOf(field(F.VALUE, PayloadType.NUMBER)))
    private val textChange = EventSpec(E.VALUE_CHANGE, listOf(field(F.TEXT, PayloadType.STRING), field(F.SELECTION_START, PayloadType.INTEGER), field(F.SELECTION_END, PayloadType.INTEGER), field(F.EDIT_SEQ, PayloadType.LONG)))
    private val enabled = prop(P.ENABLED, boolean, UiValue.Bool(true))
    private val spacing = prop(P.SPACING, dimension, UiValue.Dp(0.0))
    private val contentPadding = prop(P.CONTENT_PADDING, ValueType(setOf(DP, LIST), listKinds = setOf(DP), listMinimum = 1, listMaximum = 4), UiValue.Dp(0.0))
    private val textStyle = prop(P.STYLE, ValueType(setOf(ENUM, TEXT_STYLE), enumValues = typographyNames))
    private val focusCommands = listOf(K.FOCUS, K.BLUR)

    private fun node(name: String, props: List<PropSpec> = emptyList(), slots: List<SlotSpec> = emptyList(),
        events: List<EventSpec> = emptyList(), children: ChildrenPolicy = ChildrenPolicy.NONE,
        scope: ScopeKind = ScopeKind.ANY, commands: List<String> = emptyList()) =
        ComponentSpec(name, props, slots, baseEvents + events, children, scope, commands)

    private fun layoutProps(vertical: Boolean) = listOf(
        prop(P.ARRANGEMENT, ValueType(setOf(ENUM), enumValues = if (vertical) verticalArrangements else horizontalArrangements),
            UiValue.Enum(if (vertical) "top" else "start"), aliases = listOf(if (vertical) A.VERTICAL_ARRANGEMENT else A.HORIZONTAL_ARRANGEMENT)),
        prop(P.ALIGNMENT, if (vertical) enum("start", "center", "end") else enum("top", "center", "bottom"),
            UiValue.Enum(if (vertical) "start" else "top"), aliases = listOf(if (vertical) A.HORIZONTAL_ALIGNMENT else A.VERTICAL_ALIGNMENT)),
        spacing,
    )

    fun create(): ComponentCatalog {
        val components = mutableListOf(
            node(C.COLUMN, layoutProps(true), listOf(SlotSpec(S.CONTENT)), children = ChildrenPolicy.MANY, scope = ScopeKind.COLUMN_ROW),
            node(C.ROW, layoutProps(false), listOf(SlotSpec(S.CONTENT)), children = ChildrenPolicy.MANY, scope = ScopeKind.COLUMN_ROW),
            node(C.BOX, listOf(prop(P.ALIGNMENT, ValueType(setOf(ENUM), enumValues = boxAlignments), UiValue.Enum("topStart"), aliases = listOf(A.CONTENT_ALIGNMENT))),
                listOf(SlotSpec(S.CONTENT)), children = ChildrenPolicy.MANY, scope = ScopeKind.BOX),
            node(C.SPACER),
        )
        for ((name, vertical) in listOf(C.LAZY_COLUMN to true, C.LAZY_ROW to false)) {
            components += node(name, layoutProps(vertical) + listOf(contentPadding,
                prop(P.REVERSE_LAYOUT, boolean, UiValue.Bool(false)), prop(P.USER_SCROLL_ENABLED, boolean, UiValue.Bool(true))),
                children = ChildrenPolicy.LAZY_ITEMS, commands = listOf(K.SCROLL_TO))
        }
        components += node(C.SCAFFOLD, listOf(prop(P.CONTAINER_COLOR, color), prop(P.CONTENT_COLOR, color)),
            listOf(SlotSpec(S.CONTENT), SlotSpec(S.TOP_BAR), SlotSpec(S.SNACKBAR_HOST)), children = ChildrenPolicy.SINGLE,
            commands = listOf(K.SHOW_SNACKBAR))
        components += node(C.SURFACE, listOf(prop(P.COLOR, color), prop(P.CONTENT_COLOR, color), prop(P.TONAL_ELEVATION, dimension, UiValue.Dp(0.0)), prop(P.SHAPE, shape, UiValue.Shape(ShapeKind.RECTANGLE))),
            listOf(SlotSpec(S.CONTENT)), children = ChildrenPolicy.SINGLE)
        components += node(C.CARD, listOf(prop(P.COLOR, color), prop(P.CONTENT_COLOR, color), prop(P.ELEVATION, dimension), prop(P.SHAPE, shape)),
            listOf(SlotSpec(S.CONTENT)), children = ChildrenPolicy.MANY)
        components += node(C.HORIZONTAL_DIVIDER, listOf(prop(P.COLOR, color), prop(P.THICKNESS, dimension, UiValue.Dp(1.0))))
        components += node(C.TOP_APP_BAR, listOf(prop(P.CONTAINER_COLOR, color), prop(P.CONTENT_COLOR, color)),
            listOf(SlotSpec(S.TITLE), SlotSpec(S.NAVIGATION_ICON), SlotSpec(S.ACTIONS)))
        components += node(C.TEXT, listOf(prop(P.TEXT, string, UiValue.Str("")), textStyle, prop(P.COLOR, color),
            prop(P.FONT_SIZE, textSize), prop(P.FONT_WEIGHT, ValueType(setOf(NUMBER), minimum = 1.0, maximum = 1000.0, integral = true)),
            prop(P.TEXT_ALIGN, enum("start", "end", "left", "right", "center", "justify"), UiValue.Enum("start")),
            prop(P.MAX_LINES, positiveInteger, UiValue.Num(Int.MAX_VALUE.toDouble())),
            prop(P.OVERFLOW, enum("clip", "ellipsis", "visible"), UiValue.Enum("clip")), prop(P.SELECTABLE, boolean, UiValue.Bool(false))))
        components += node(C.ICON, listOf(prop(P.NAME, type(ICON_NAME), required = true), prop(P.TINT, color)))
        components += node(C.IMAGE, listOf(prop(P.SRC, type(BITMAP), required = true),
            prop(P.CONTENT_SCALE, enum("fit", "crop", "fillBounds", "fillWidth", "fillHeight", "inside", "none"), UiValue.Enum("fit"))))
        for (name in listOf(C.BUTTON, C.ELEVATED_BUTTON, C.FILLED_TONAL_BUTTON, C.OUTLINED_BUTTON, C.TEXT_BUTTON, C.ICON_BUTTON)) {
            components += node(name, listOf(enabled, prop(P.CONTAINER_COLOR, color), prop(P.CONTENT_COLOR, color), prop(P.SHAPE, shape)),
                listOf(SlotSpec(S.CONTENT)), children = ChildrenPolicy.MANY, commands = focusCommands)
        }
        for (name in listOf(C.SWITCH, C.CHECKBOX, C.RADIO_BUTTON)) {
            components += node(name, listOf(enabled, prop(P.CHECKED, boolean, UiValue.Bool(false), aliases = if (name == C.RADIO_BUTTON) listOf(A.SELECTED) else emptyList())),
                events = listOf(checked), commands = focusCommands)
        }
        components += node(C.SLIDER, listOf(enabled, prop(P.VALUE, number, UiValue.Num(0.0)),
            prop(P.RANGE, ValueType(setOf(LIST), listKinds = setOf(NUMBER), listMinimum = 2, listMaximum = 2), UiValue.ListOf(listOf(UiValue.Num(0.0), UiValue.Num(1.0)))),
            prop(P.STEPS, integer, UiValue.Num(0.0))), events = listOf(sliderChange, EventSpec(E.VALUE_CHANGE_FINISHED)), commands = focusCommands)
        for (name in listOf(C.TEXT_FIELD, C.OUTLINED_TEXT_FIELD)) {
            components += node(name, listOf(enabled, prop(P.TEXT, string, UiValue.Str(""), aliases = listOf(P.VALUE), update = PropertyUpdate.EDIT_COMMAND),
                textStyle, prop(P.SINGLE_LINE, boolean, UiValue.Bool(false)), prop(P.MIN_LINES, positiveInteger, UiValue.Num(1.0)),
                prop(P.MAX_LINES, positiveInteger, UiValue.Num(Int.MAX_VALUE.toDouble())),
                prop(P.KEYBOARD_TYPE, enum("text", "ascii", "number", "phone", "uri", "email", "password", "numberPassword", "decimal"), UiValue.Enum("text")),
                prop(P.IME_ACTION, enum("default", "none", "go", "search", "send", "previous", "next", "done"), UiValue.Enum("default")),
                prop(P.IS_ERROR, boolean, UiValue.Bool(false)), prop(P.READ_ONLY, boolean, UiValue.Bool(false)),
                prop(P.VISUAL_TRANSFORMATION, enum("none", "password"), UiValue.Enum("none"))),
                listOf(SlotSpec(S.LABEL), SlotSpec(S.PLACEHOLDER), SlotSpec(S.LEADING_ICON), SlotSpec(S.TRAILING_ICON), SlotSpec(S.SUPPORTING_TEXT)),
                listOf(textChange), commands = focusCommands + K.EDIT)
        }
        for (name in listOf(C.CIRCULAR_PROGRESS_INDICATOR, C.LINEAR_PROGRESS_INDICATOR)) {
            components += node(name, listOf(prop(P.PROGRESS, ValueType(setOf(NUMBER, NULL), minimum = 0.0, maximum = 1.0), UiValue.Null), prop(P.COLOR, color), prop(P.TRACK_COLOR, color)))
        }
        components += node(C.ALERT_DIALOG, listOf(prop(P.OPEN, boolean, UiValue.Bool(false)), prop(P.CONTAINER_COLOR, color), prop(P.SHAPE, shape)),
            listOf(SlotSpec(S.ICON), SlotSpec(S.TITLE), SlotSpec(S.TEXT), SlotSpec(S.CONFIRM, required = true), SlotSpec(S.DISMISS)),
            listOf(EventSpec(E.DISMISS_REQUEST)))
        components += ComponentSpec(C.SNACKBAR, listOf(prop(P.MESSAGE, string, required = true), prop(P.ACTION_LABEL, type(STRING, NULL), UiValue.Null),
            prop(P.DURATION, enum("short", "long", "indefinite"), UiValue.Enum("short"))), events = listOf(EventSpec(E.ACTION), EventSpec(E.DISMISS)),
            commands = listOf(K.SHOW_SNACKBAR), invocation = ComponentInvocation.COMMAND)

        val modifiers = modifierCatalog()
        val shortcuts = listOf(ShortcutSpec(Q.W, M.WIDTH), ShortcutSpec(Q.H, M.HEIGHT), ShortcutSpec(Q.PADDING, M.PADDING, true),
            ShortcutSpec(Q.BG, M.BACKGROUND), ShortcutSpec(Q.WEIGHT, M.WEIGHT), ShortcutSpec(Q.ALPHA, M.ALPHA),
            ShortcutSpec(Q.TEST_TAG, M.TEST_TAG), ShortcutSpec(Q.CONTENT_DESCRIPTION, M.SEMANTICS))
        val system = listOf(EventSpec(E.ERROR, listOf(field(F.CODE, PayloadType.STRING), field(F.MESSAGE, PayloadType.STRING), field(F.PROP, PayloadType.STRING, false))), EventSpec(E.CLOSE))
        return ComponentCatalog(components, modifiers, shortcuts, system)
    }

    private fun arg(name: String, type: ValueType, default: UiValue? = null, callback: Boolean = false) = ModifierArgSpec(name, type, default, callback)
    private fun sig(vararg args: ModifierArgSpec) = ModifierSignature(args.toList())
    private fun modifierCatalog(): List<ModifierSpec> = listOf(
        ModifierSpec(M.PADDING, listOf(sig(arg("all", dimension)), sig(arg("horizontal", dimension), arg("vertical", dimension)),
            sig(arg("start", dimension), arg("top", dimension), arg("end", dimension), arg("bottom", dimension)))),
        ModifierSpec(M.SIZE, listOf(sig(arg("size", dimension)), sig(arg("width", dimension), arg("height", dimension)))),
        ModifierSpec(M.WIDTH, listOf(sig(arg("width", dimension)))),
        ModifierSpec(M.HEIGHT, listOf(sig(arg("height", dimension)))),
        ModifierSpec(M.FILL_MAX_WIDTH, listOf(sig(arg("fraction", fraction, UiValue.Num(1.0))))),
        ModifierSpec(M.FILL_MAX_HEIGHT, listOf(sig(arg("fraction", fraction, UiValue.Num(1.0))))),
        ModifierSpec(M.FILL_MAX_SIZE, listOf(sig(arg("fraction", fraction, UiValue.Num(1.0))))),
        ModifierSpec(M.WEIGHT, listOf(sig(arg("weight", positiveNumber), arg("fill", boolean, UiValue.Bool(true)))), ScopeKind.COLUMN_ROW, listOf(C.COLUMN, C.ROW)),
        ModifierSpec(M.ALIGN, listOf(sig(arg("alignment", ValueType(setOf(ENUM), enumValues = boxAlignments)))), ScopeKind.BOX, listOf(C.BOX)),
        ModifierSpec(M.BACKGROUND, listOf(sig(arg("color", type(COLOR)), arg("shape", shape, UiValue.Shape(ShapeKind.RECTANGLE))))),
        ModifierSpec(M.BORDER, listOf(sig(arg("width", dimension), arg("color", type(COLOR)), arg("shape", shape, UiValue.Shape(ShapeKind.RECTANGLE))))),
        ModifierSpec(M.CLIP, listOf(sig(arg("shape", shape)))),
        ModifierSpec(M.ALPHA, listOf(sig(arg("alpha", fraction)))),
        ModifierSpec(M.CLICKABLE, listOf(sig(arg("callbackId", positiveInteger, callback = true), arg("enabled", boolean, UiValue.Bool(true)))), commands = focusCommands),
        ModifierSpec(M.VERTICAL_SCROLL, listOf(sig(arg("enabled", boolean, UiValue.Bool(true)))), commands = listOf(K.SCROLL_TO)),
        ModifierSpec(M.HORIZONTAL_SCROLL, listOf(sig(arg("enabled", boolean, UiValue.Bool(true)))), commands = listOf(K.SCROLL_TO)),
        ModifierSpec(M.OFFSET, listOf(sig(arg("x", signedDimension), arg("y", signedDimension)))),
        ModifierSpec(M.ASPECT_RATIO, listOf(sig(arg("ratio", positiveNumber)))),
        ModifierSpec(M.TEST_TAG, listOf(sig(arg("tag", string)))),
        ModifierSpec(M.SEMANTICS, listOf(sig(arg("contentDescription", type(STRING, NULL))))),
    )
}
