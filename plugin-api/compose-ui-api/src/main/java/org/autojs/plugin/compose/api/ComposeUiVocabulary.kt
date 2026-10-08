package org.autojs.plugin.compose.api

/** Frozen V1 wire and catalog names. Script onX aliases are mapped to these event names by the host. */
object ComposeUiComponents {
    const val COLUMN = "Column"
    const val ROW = "Row"
    const val BOX = "Box"
    const val SPACER = "Spacer"
    const val LAZY_COLUMN = "LazyColumn"
    const val LAZY_ROW = "LazyRow"
    const val SCAFFOLD = "Scaffold"
    const val SURFACE = "Surface"
    const val CARD = "Card"
    const val HORIZONTAL_DIVIDER = "HorizontalDivider"
    const val TOP_APP_BAR = "TopAppBar"
    const val TEXT = "Text"
    const val ICON = "Icon"
    const val IMAGE = "Image"
    const val BUTTON = "Button"
    const val ELEVATED_BUTTON = "ElevatedButton"
    const val FILLED_TONAL_BUTTON = "FilledTonalButton"
    const val OUTLINED_BUTTON = "OutlinedButton"
    const val TEXT_BUTTON = "TextButton"
    const val ICON_BUTTON = "IconButton"
    const val SWITCH = "Switch"
    const val CHECKBOX = "Checkbox"
    const val RADIO_BUTTON = "RadioButton"
    const val SLIDER = "Slider"
    const val TEXT_FIELD = "TextField"
    const val OUTLINED_TEXT_FIELD = "OutlinedTextField"
    const val CIRCULAR_PROGRESS_INDICATOR = "CircularProgressIndicator"
    const val LINEAR_PROGRESS_INDICATOR = "LinearProgressIndicator"
    const val ALERT_DIALOG = "AlertDialog"
    const val SNACKBAR = "Snackbar"
}

object ComposeUiProps {
    const val ARRANGEMENT = "arrangement"
    const val ALIGNMENT = "alignment"
    const val SPACING = "spacing"
    const val CONTENT_PADDING = "contentPadding"
    const val REVERSE_LAYOUT = "reverseLayout"
    const val USER_SCROLL_ENABLED = "userScrollEnabled"
    const val COLOR = "color"
    const val CONTENT_COLOR = "contentColor"
    const val TONAL_ELEVATION = "tonalElevation"
    const val SHAPE = "shape"
    const val ELEVATION = "elevation"
    const val THICKNESS = "thickness"
    const val TEXT = "text"
    const val STYLE = "style"
    const val FONT_SIZE = "fontSize"
    const val FONT_WEIGHT = "fontWeight"
    const val TEXT_ALIGN = "textAlign"
    const val MAX_LINES = "maxLines"
    const val MIN_LINES = "minLines"
    const val OVERFLOW = "overflow"
    const val SELECTABLE = "selectable"
    const val NAME = "name"
    const val TINT = "tint"
    const val SRC = "src"
    const val CONTENT_SCALE = "contentScale"
    const val ENABLED = "enabled"
    const val CONTAINER_COLOR = "containerColor"
    const val CHECKED = "checked"
    const val VALUE = "value"
    const val RANGE = "range"
    const val STEPS = "steps"
    const val SINGLE_LINE = "singleLine"
    const val KEYBOARD_TYPE = "keyboardType"
    const val IME_ACTION = "imeAction"
    const val IS_ERROR = "isError"
    const val READ_ONLY = "readOnly"
    const val VISUAL_TRANSFORMATION = "visualTransformation"
    const val PROGRESS = "progress"
    const val TRACK_COLOR = "trackColor"
    const val OPEN = "open"
    const val MESSAGE = "message"
    const val ACTION_LABEL = "actionLabel"
    const val DURATION = "duration"
}

object ComposeUiPropAliases {
    const val VERTICAL_ARRANGEMENT = "verticalArrangement"
    const val HORIZONTAL_ARRANGEMENT = "horizontalArrangement"
    const val HORIZONTAL_ALIGNMENT = "horizontalAlignment"
    const val VERTICAL_ALIGNMENT = "verticalAlignment"
    const val CONTENT_ALIGNMENT = "contentAlignment"
    const val SELECTED = "selected"
}

object ComposeUiEvents {
    const val CLICK = "click"
    const val LONG_CLICK = "longClick"
    const val VALUE_CHANGE = "valueChange"
    const val VALUE_CHANGE_FINISHED = "valueChangeFinished"
    const val CHECKED_CHANGE = "checkedChange"
    const val DISMISS_REQUEST = "dismissRequest"
    const val SCROLL = "scroll"
    const val FOCUS_CHANGE = "focusChange"
    const val ACTION = "action"
    const val DISMISS = "dismiss"
    const val ERROR = "error"
    const val CLOSE = "close"
}

object ComposeUiEventFields {
    const val TEXT = "text"
    const val SELECTION_START = "selectionStart"
    const val SELECTION_END = "selectionEnd"
    const val EDIT_SEQ = "editSeq"
    const val CHECKED = "checked"
    const val VALUE = "value"
    const val FIRST_VISIBLE_INDEX = "firstVisibleIndex"
    const val OFFSET = "offset"
    const val FOCUSED = "focused"
    const val CODE = "code"
    const val MESSAGE = "message"
    const val PROP = "prop"
}

object ComposeUiSlots {
    const val CONTENT = "content"
    const val TITLE = "title"
    const val ACTIONS = "actions"
    const val LABEL = "label"
    const val PLACEHOLDER = "placeholder"
    const val LEADING_ICON = "leadingIcon"
    const val TRAILING_ICON = "trailingIcon"
    const val SUPPORTING_TEXT = "supportingText"
    const val TOP_BAR = "topBar"
    const val SNACKBAR_HOST = "snackbarHost"
    const val NAVIGATION_ICON = "navigationIcon"
    const val TEXT = "text"
    const val CONFIRM = "confirm"
    const val DISMISS = "dismiss"
    const val ICON = "icon"
}

object ComposeUiModifiers {
    const val PADDING = "padding"
    const val SIZE = "size"
    const val WIDTH = "width"
    const val HEIGHT = "height"
    const val FILL_MAX_WIDTH = "fillMaxWidth"
    const val FILL_MAX_HEIGHT = "fillMaxHeight"
    const val FILL_MAX_SIZE = "fillMaxSize"
    const val WEIGHT = "weight"
    const val ALIGN = "align"
    const val BACKGROUND = "background"
    const val BORDER = "border"
    const val CLIP = "clip"
    const val ALPHA = "alpha"
    const val CLICKABLE = "clickable"
    const val VERTICAL_SCROLL = "verticalScroll"
    const val HORIZONTAL_SCROLL = "horizontalScroll"
    const val OFFSET = "offset"
    const val ASPECT_RATIO = "aspectRatio"
    const val TEST_TAG = "testTag"
    const val SEMANTICS = "semantics"
}

object ComposeUiShortcuts {
    const val W = "w"
    const val H = "h"
    const val PADDING = "padding"
    const val BG = "bg"
    const val WEIGHT = "weight"
    const val ALPHA = "alpha"
    const val TEST_TAG = "testTag"
    const val CONTENT_DESCRIPTION = "contentDescription"
}

object ComposeUiCommands {
    const val FOCUS = "focus"
    const val BLUR = "blur"
    const val SCROLL_TO = "scrollTo"
    const val EDIT = "edit"
    const val SHOW_SNACKBAR = "showSnackbar"
}

object ComposeUiThemeColors {
    const val PRIMARY = "primary"
    const val ON_PRIMARY = "onPrimary"
    const val PRIMARY_CONTAINER = "primaryContainer"
    const val ON_PRIMARY_CONTAINER = "onPrimaryContainer"
    const val INVERSE_PRIMARY = "inversePrimary"
    const val SECONDARY = "secondary"
    const val ON_SECONDARY = "onSecondary"
    const val SECONDARY_CONTAINER = "secondaryContainer"
    const val ON_SECONDARY_CONTAINER = "onSecondaryContainer"
    const val TERTIARY = "tertiary"
    const val ON_TERTIARY = "onTertiary"
    const val TERTIARY_CONTAINER = "tertiaryContainer"
    const val ON_TERTIARY_CONTAINER = "onTertiaryContainer"
    const val BACKGROUND = "background"
    const val ON_BACKGROUND = "onBackground"
    const val SURFACE = "surface"
    const val ON_SURFACE = "onSurface"
    const val SURFACE_VARIANT = "surfaceVariant"
    const val ON_SURFACE_VARIANT = "onSurfaceVariant"
    const val SURFACE_TINT = "surfaceTint"
    const val INVERSE_SURFACE = "inverseSurface"
    const val INVERSE_ON_SURFACE = "inverseOnSurface"
    const val ERROR = "error"
    const val ON_ERROR = "onError"
    const val ERROR_CONTAINER = "errorContainer"
    const val ON_ERROR_CONTAINER = "onErrorContainer"
    const val OUTLINE = "outline"
    const val OUTLINE_VARIANT = "outlineVariant"
    const val SCRIM = "scrim"
    const val SURFACE_BRIGHT = "surfaceBright"
    const val SURFACE_DIM = "surfaceDim"
    const val SURFACE_CONTAINER = "surfaceContainer"
    const val SURFACE_CONTAINER_HIGH = "surfaceContainerHigh"
    const val SURFACE_CONTAINER_HIGHEST = "surfaceContainerHighest"
    const val SURFACE_CONTAINER_LOW = "surfaceContainerLow"
    const val SURFACE_CONTAINER_LOWEST = "surfaceContainerLowest"
    const val PRIMARY_FIXED = "primaryFixed"
    const val PRIMARY_FIXED_DIM = "primaryFixedDim"
    const val ON_PRIMARY_FIXED = "onPrimaryFixed"
    const val ON_PRIMARY_FIXED_VARIANT = "onPrimaryFixedVariant"
    const val SECONDARY_FIXED = "secondaryFixed"
    const val SECONDARY_FIXED_DIM = "secondaryFixedDim"
    const val ON_SECONDARY_FIXED = "onSecondaryFixed"
    const val ON_SECONDARY_FIXED_VARIANT = "onSecondaryFixedVariant"
    const val TERTIARY_FIXED = "tertiaryFixed"
    const val TERTIARY_FIXED_DIM = "tertiaryFixedDim"
    const val ON_TERTIARY_FIXED = "onTertiaryFixed"
    const val ON_TERTIARY_FIXED_VARIANT = "onTertiaryFixedVariant"
    val names: Set<String> = java.util.Collections.unmodifiableSet(linkedSetOf(
        PRIMARY,
        ON_PRIMARY,
        PRIMARY_CONTAINER,
        ON_PRIMARY_CONTAINER,
        INVERSE_PRIMARY,
        SECONDARY,
        ON_SECONDARY,
        SECONDARY_CONTAINER,
        ON_SECONDARY_CONTAINER,
        TERTIARY,
        ON_TERTIARY,
        TERTIARY_CONTAINER,
        ON_TERTIARY_CONTAINER,
        BACKGROUND,
        ON_BACKGROUND,
        SURFACE,
        ON_SURFACE,
        SURFACE_VARIANT,
        ON_SURFACE_VARIANT,
        SURFACE_TINT,
        INVERSE_SURFACE,
        INVERSE_ON_SURFACE,
        ERROR,
        ON_ERROR,
        ERROR_CONTAINER,
        ON_ERROR_CONTAINER,
        OUTLINE,
        OUTLINE_VARIANT,
        SCRIM,
        SURFACE_BRIGHT,
        SURFACE_DIM,
        SURFACE_CONTAINER,
        SURFACE_CONTAINER_HIGH,
        SURFACE_CONTAINER_HIGHEST,
        SURFACE_CONTAINER_LOW,
        SURFACE_CONTAINER_LOWEST,
        PRIMARY_FIXED,
        PRIMARY_FIXED_DIM,
        ON_PRIMARY_FIXED,
        ON_PRIMARY_FIXED_VARIANT,
        SECONDARY_FIXED,
        SECONDARY_FIXED_DIM,
        ON_SECONDARY_FIXED,
        ON_SECONDARY_FIXED_VARIANT,
        TERTIARY_FIXED,
        TERTIARY_FIXED_DIM,
        ON_TERTIARY_FIXED,
        ON_TERTIARY_FIXED_VARIANT,
    ))
}
