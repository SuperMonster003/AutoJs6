package org.autojs.autojs.runtime.api.modules.imgui

/**
 * The API names of the ImGui plugin (AutoJs6-Plugin-ImGui, `app/src/main/assets/types/imgui.d.ts`: `ApiCore` and
 * `ApiAliases`), as [ImguiModule] defines them: [FUNCTIONS] and the PascalCase [ALIASES] forward to the plugin's API
 * object, [CONSTANTS] are its constant groups (Col, Key, WindowFlags, ...). The same names are declared on the module's
 * `@ScriptInstall` for the metadata (script-modules.json, read by the api-coverage, d.ts and documentation tools);
 * `ScriptModuleInstallsTest` keeps the two lists equal. When the plugin adds a name, add it to both: a name missing here
 * is still forwarded, but only after the plugin is loaded, and stays out of the metadata.
 *
 * zh-CN: ImGui 插件的 API 名称 (插件仓库 app/src/main/assets/types/imgui.d.ts 的 ApiCore 与 ApiAliases), [ImguiModule] 按此定义成员: [FUNCTIONS]
 * 与 PascalCase 别名 [ALIASES] 转发到插件的 API 对象, [CONSTANTS] 是它的常量组 (Col, Key, WindowFlags 等). 同一组名称声明在模块的 @ScriptInstall 上供
 * 元数据 (script-modules.json, api-coverage / d.ts / 文档工具读取) 使用; ScriptModuleInstallsTest 保证两份清单一致. 插件新增名称时两处同时添加: 这里缺少的
 * 名称仍会转发, 但要等插件加载之后, 且不进入元数据.
 */
object ImguiNames {

    /** Functions of the plugin API, camelCase (the `with*` scope helpers and the window / dialog helpers included). */
    val FUNCTIONS: List<String> = listOf(
        "bool", "float", "int", "string", "ref", "floats", "bytes", "strings", "version", "getBackendCapabilities", "listExamples",
        "getExampleSource", "runExample", "colorU32", "icon", "setNextWindowPos", "setNextWindowSize", "setNextWindowSizeConstraints",
        "setNextWindowBgAlpha", "setKeyboardFocusHere", "begin", "end", "beginChild", "endChild", "getWindowPos", "getWindowSize",
        "getContentRegionAvail", "getCursorPos", "setCursorPos", "text", "textWrapped", "textColored", "button", "smallButton", "checkbox",
        "radioButton", "sliderFloat", "sliderInt", "dragFloat", "dragInt", "inputText", "colorEdit3", "colorEdit4", "progressBar", "combo",
        "selectable", "collapsingHeader", "treeNode", "treePop", "beginTabBar", "endTabBar", "beginTabItem", "endTabItem", "sameLine", "separator",
        "spacing", "newLine", "dummy", "setNextItemWidth", "pushItemWidth", "popItemWidth", "isItemClicked", "isItemHovered", "wantCaptureMouse",
        "isAnyItemHovered", "isAnyItemActive", "styleColorsDark", "styleColorsLight", "styleColorsClassic", "showDemoWindow", "separatorText",
        "bullet", "bulletText", "labelText", "textDisabled", "arrowButton", "invisibleButton", "inputInt", "inputFloat", "inputTextMultiline",
        "beginListBox", "endListBox", "listBox", "colorPicker3", "colorPicker4", "colorButton", "plotLines", "plotHistogram", "treeNodeEx",
        "vSliderFloat", "vSliderInt", "toggle", "toggleSwitch", "beginCombo", "endCombo", "openPopup", "beginPopup", "beginPopupModal",
        "beginPopupContextItem", "beginPopupContextWindow", "endPopup", "closeCurrentPopup", "isPopupOpen", "beginMenuBar", "endMenuBar", "beginMenu",
        "endMenu", "menuItem", "setTooltip", "setItemTooltip", "beginTooltip", "beginItemTooltip", "endTooltip", "beginTable", "endTable",
        "tableNextRow", "tableNextColumn", "tableSetColumnIndex", "tableSetupColumn", "tableSetupScrollFreeze", "tableHeadersRow", "tableSetBgColor",
        "pushStyleColor", "popStyleColor", "setStyleColor", "setStyleProperties", "setTheme", "pushStyleVar", "popStyleVar", "setWindowFontScale",
        "pushFont", "popFont", "withFont", "createTexture", "destroyTexture", "image", "imageButton", "getWindowDrawList", "getForegroundDrawList",
        "getBackgroundDrawList", "pushId", "popId", "beginGroup", "endGroup", "indent", "unindent", "alignTextToFramePadding", "beginDisabled",
        "endDisabled", "getScrollX", "getScrollY", "getScrollMaxX", "getScrollMaxY", "setScrollX", "setScrollY", "setScrollHereX", "setScrollHereY",
        "isItemActive", "isItemEdited", "isItemActivated", "isItemDeactivated", "isItemDeactivatedAfterEdit", "isItemFocused", "isItemVisible",
        "isWindowHovered", "isWindowFocused", "isWindowCollapsed", "isWindowAppearing", "getMousePos", "isMouseDown", "isMouseClicked",
        "isMouseDoubleClicked", "isMouseReleased", "isMouseDragging", "getMouseDragDelta", "resetMouseDragDelta", "calcTextSize", "getFrameHeight",
        "getFrameHeightWithSpacing", "getTextLineHeight", "getTextLineHeightWithSpacing", "framerate", "addInputCharacters", "addKeyEvent", "sendKey",
        "addMouseWheel", "wantTextInput", "withWindow", "withChild", "withTreeNode", "withTabBar", "withTabItem", "withTable", "withPopup",
        "withPopupModal", "withContextItem", "withContextWindow", "withTooltip", "withItemTooltip", "withMenuBar", "withMenu", "withListBox",
        "withCombo", "withGroup", "withDisabled", "withId", "logWindow", "toast", "alert", "confirm", "singleChoice", "multiChoice", "statsOverlay",
        "dataTable", "createWindow", "closeWindow", "launchFloaty", "launch", "launchPrivileged", "addButtonEx", "setTouchable", "setMoreTouch",
        "setGlobalAlpha", "setSecure", "collapseWindow", "expandWindow", "getComponent", "dp", "px",
    )

    /** PascalCase aliases of [FUNCTIONS] (`Begin` for `begin`, `PushID` for `pushId`), functions as well. */
    val ALIASES: List<String> = listOf(
        "Ref", "GetBackendCapabilities", "ListExamples", "GetExampleSource", "RunExample", "LogWindow", "Toast", "Alert", "Confirm", "SingleChoice",
        "MultiChoice", "StatsOverlay", "DataTable", "GetComponent", "CreateWindow", "CloseWindow", "LaunchFloaty", "Launch", "LaunchPrivileged",
        "AddButtonEx", "SetTouchable", "SetMoreTouch", "SetGlobalAlpha", "SetSecure", "CollapseWindow", "ExpandWindow", "SetNextWindowPos",
        "SetNextWindowSize", "SetNextWindowSizeConstraints", "SetNextWindowBgAlpha", "SetKeyboardFocusHere", "Begin", "End", "BeginChild", "EndChild",
        "GetWindowPos", "GetWindowSize", "GetContentRegionAvail", "GetCursorPos", "SetCursorPos", "Text", "TextWrapped", "TextColored", "Button",
        "SmallButton", "Checkbox", "RadioButton", "SliderFloat", "SliderInt", "DragFloat", "DragInt", "InputText", "ColorEdit3", "ColorEdit4",
        "ProgressBar", "Combo", "Selectable", "CollapsingHeader", "TreeNode", "TreePop", "BeginTabBar", "EndTabBar", "BeginTabItem", "EndTabItem",
        "SameLine", "Separator", "Spacing", "NewLine", "Dummy", "SetNextItemWidth", "PushItemWidth", "PopItemWidth", "IsItemClicked", "IsItemHovered",
        "IsAnyItemHovered", "IsAnyItemActive", "ShowDemoWindow", "StyleColorsDark", "StyleColorsLight", "StyleColorsClassic", "WantCaptureMouse",
        "SeparatorText", "Bullet", "BulletText", "LabelText", "TextDisabled", "ArrowButton", "InvisibleButton", "InputInt", "InputFloat",
        "InputTextMultiline", "BeginListBox", "EndListBox", "ListBox", "ColorPicker3", "ColorPicker4", "ColorButton", "PlotLines", "PlotHistogram",
        "TreeNodeEx", "VSliderFloat", "VSliderInt", "Toggle", "ToggleSwitch", "BeginCombo", "EndCombo", "OpenPopup", "BeginPopup", "BeginPopupModal",
        "BeginPopupContextItem", "BeginPopupContextWindow", "EndPopup", "CloseCurrentPopup", "IsPopupOpen", "BeginMenuBar", "EndMenuBar", "BeginMenu",
        "EndMenu", "MenuItem", "SetTooltip", "SetItemTooltip", "BeginTooltip", "BeginItemTooltip", "EndTooltip", "BeginTable", "EndTable",
        "TableNextRow", "TableNextColumn", "TableSetColumnIndex", "TableSetupColumn", "TableSetupScrollFreeze", "TableHeadersRow", "TableSetBgColor",
        "PushStyleColor", "PopStyleColor", "SetStyleColor", "SetStyleProperties", "SetTheme", "PushStyleVar", "PopStyleVar", "SetWindowFontScale",
        "PushFont", "PopFont", "WithFont", "Icon", "ColorU32", "CreateTexture", "DestroyTexture", "Image", "ImageButton", "GetWindowDrawList",
        "GetForegroundDrawList", "GetBackgroundDrawList", "PushID", "PopID", "BeginGroup", "EndGroup", "Indent", "Unindent",
        "AlignTextToFramePadding", "BeginDisabled", "EndDisabled", "GetScrollX", "GetScrollY", "GetScrollMaxX", "GetScrollMaxY", "SetScrollX",
        "SetScrollY", "SetScrollHereX", "SetScrollHereY", "IsItemActive", "IsItemEdited", "IsItemActivated", "IsItemDeactivated",
        "IsItemDeactivatedAfterEdit", "IsItemFocused", "IsItemVisible", "IsWindowHovered", "IsWindowFocused", "IsWindowCollapsed",
        "IsWindowAppearing", "GetMousePos", "IsMouseDown", "IsMouseClicked", "IsMouseDoubleClicked", "IsMouseReleased", "IsMouseDragging",
        "GetMouseDragDelta", "ResetMouseDragDelta", "CalcTextSize", "GetFrameHeight", "GetFrameHeightWithSpacing", "GetTextLineHeight",
        "GetTextLineHeightWithSpacing", "Dp", "Px",
    )

    /** Constant groups of the plugin API (flag and enum tables, theme and font names), read through getters. */
    val CONSTANTS: List<String> = listOf(
        "Limits", "BackendStatus", "Cond", "WindowFlags", "InputTextFlags", "SliderFlags", "TreeNodeFlags", "SelectableFlags", "TabBarFlags",
        "TabItemFlags", "MouseButton", "ChildFlags", "ComboFlags", "PopupFlags", "HoveredFlags", "FocusedFlags", "ColorEditFlags", "TableFlags",
        "TableColumnFlags", "TableRowFlags", "TableBgTarget", "Col", "StyleVar", "Themes", "Appearance", "Fonts", "TextureFlags", "DrawListLayer",
        "DrawFlags", "TouchMode", "WindowMode", "LogLevel", "ToastPosition", "Icons", "Dir", "ButtonFlags", "Key", "density",
    )

}
