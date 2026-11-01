package org.autojs.autojs.runtime.api.modules.imgui

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptInstallKind
import org.autojs.autojs.runtime.api.modules.ScriptInstallNames
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.js_require
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

/**
 * `imgui` on the script module layer: the global object of the ImGui plugin (AutoJs6-Plugin-ImGui), registered by the
 * host as `pinyin` and `mediainfo` are for their plugins (api-coverage PLUGIN-APIS.md Q2-1), so a script calls
 * `imgui.begin(...)` without `plugins.load`. The plugin stays an SDK (in-process) plugin: the first use of a member loads
 * it through `Plugins.load` (installed, enabled and trusted in the Plugin Center; its assets copied, its service bound,
 * unbound when the script exits) and runs its `index.js` once. That script receives as `this` a private object whose
 * prototype is the global object: its reads of the global object (`files`) resolve, while the `imgui` it installs on
 * `this` lands on that object and the host module keeps the global name.
 *
 * The members are the plugin's API names ([ImguiNames], from the plugin's `imgui.d.ts`): functions and their PascalCase
 * aliases forward to the API object (`this` of the forwarded call is that object), the constant groups (Col, Key,
 * WindowFlags, ...) are getters reading it. `__pluginId` answers the identity check of `index.js` with the plugin's
 * package, except while `index.js` runs, so `plugins.load("io.github.supermonster003.autojs6.plugin.imgui")` of an
 * older script returns this module object instead of building a second API object. A member used when the plugin is not
 * installed, disabled or untrusted fails with the Plugin Center message, mapped by the layer (T5); nothing is loaded
 * until a member is used, so a script that only mentions `imgui` pays nothing. A plugin newer than this host may have
 * public members [ImguiNames] does not list: once the plugin is loaded (by the first use of any member) they are defined
 * on the module object the same way, so `imgui.newMember(...)` works from then on, while before the load such a name is
 * still undefined (the `ui` the render callback receives is the API object itself and always has every member).
 *
 * zh-CN: 脚本模块层上的 imgui: ImGui 插件 (AutoJs6-Plugin-ImGui) 的全局对象, 由宿主注册, 形式同 pinyin / mediainfo 之于各自的插件 (api-coverage
 * PLUGIN-APIS.md Q2-1), 脚本直接调用 imgui.begin(...) 而不再需要 plugins.load. 插件仍是 SDK (进程内) 插件: 首次使用某个成员时经 Plugins.load 加载
 * (插件中心中已安装, 已启用且已授信; 复制 assets, 绑定服务, 脚本结束时解绑), 并运行一次它的 index.js. 该脚本收到的 this 是一个以全局对象为原型的私有对象:
 * 它对全局对象的读取 (files) 照常解析, 而它安装到 this 上的 imgui 落在该对象上, 全局名称仍由宿主模块持有.
 * 成员即插件的 API 名称 ([ImguiNames], 来自插件的 imgui.d.ts): 函数及其 PascalCase 别名转发到 API 对象 (转发调用的 this 即该对象), 常量组 (Col, Key,
 * WindowFlags 等) 是读取它的 getter. __pluginId 以插件包名回应 index.js 的身份检查 (index.js 运行期间除外), 因此旧脚本的
 * plugins.load("io.github.supermonster003.autojs6.plugin.imgui") 返回的就是本模块对象, 不会再构建第二个 API 对象. 插件未安装, 未启用或未授信时使用
 * 成员以插件中心的提示失败, 由本层映射 (T5); 使用成员之前不加载任何东西, 只提到 imgui 的脚本没有开销. 插件比宿主新时可能有 [ImguiNames] 未列出的公开成员:
 * 插件加载后 (首次使用任一成员时) 以同样方式补挂到模块对象上, 此后 imgui.newMember(...) 可用; 加载之前该名称仍为 undefined (渲染回调收到的 ui 就是 API
 * 对象本身, 始终含全部成员).
 */
@ScriptModule(name = "imgui")
class ImguiModule(private val runtime: ScriptRuntime) {

    private lateinit var installation: ScriptModuleInstallation
    private lateinit var scope: VarScope
    private lateinit var global: ScriptableObject

    /** The object holding the module's members, where [forwardUnlisted] defines the names [ImguiNames] lacks. */
    private var members: ScriptableObject? = null

    /** The API object `index.js` of the plugin returned, built on the first use of a member. */
    private var api: Scriptable? = null

    /** True while `index.js` runs: its identity check (`globalObject.imgui.__pluginId`) must then see undefined. */
    private var loading = false

    @ScriptInstall(
        names = [
            ScriptInstallNames(names = [PLUGIN_ID_KEY], kind = ScriptInstallKind.GETTER),
            ScriptInstallNames(
                names = [
                    "bool", "float", "int", "string", "ref", "floats", "bytes", "strings", "version", "getBackendCapabilities", "listExamples",
                    "getExampleSource", "runExample", "colorU32", "icon", "setNextWindowPos", "setNextWindowSize", "setNextWindowSizeConstraints",
                    "setNextWindowBgAlpha", "setKeyboardFocusHere", "begin", "end", "beginChild", "endChild", "getWindowPos", "getWindowSize",
                    "getContentRegionAvail", "getCursorPos", "setCursorPos", "text", "textWrapped", "textColored", "button", "smallButton",
                    "checkbox", "radioButton", "sliderFloat", "sliderInt", "dragFloat", "dragInt", "inputText", "colorEdit3", "colorEdit4",
                    "progressBar", "combo", "selectable", "collapsingHeader", "treeNode", "treePop", "beginTabBar", "endTabBar", "beginTabItem",
                    "endTabItem", "sameLine", "separator", "spacing", "newLine", "dummy", "setNextItemWidth", "pushItemWidth", "popItemWidth",
                    "isItemClicked", "isItemHovered", "wantCaptureMouse", "isAnyItemHovered", "isAnyItemActive", "styleColorsDark",
                    "styleColorsLight", "styleColorsClassic", "showDemoWindow", "separatorText", "bullet", "bulletText", "labelText", "textDisabled",
                    "arrowButton", "invisibleButton", "inputInt", "inputFloat", "inputTextMultiline", "beginListBox", "endListBox", "listBox",
                    "colorPicker3", "colorPicker4", "colorButton", "plotLines", "plotHistogram", "treeNodeEx", "vSliderFloat", "vSliderInt", "toggle",
                    "toggleSwitch", "beginCombo", "endCombo", "openPopup", "beginPopup", "beginPopupModal", "beginPopupContextItem",
                    "beginPopupContextWindow", "endPopup", "closeCurrentPopup", "isPopupOpen", "beginMenuBar", "endMenuBar", "beginMenu", "endMenu",
                    "menuItem", "setTooltip", "setItemTooltip", "beginTooltip", "beginItemTooltip", "endTooltip", "beginTable", "endTable",
                    "tableNextRow", "tableNextColumn", "tableSetColumnIndex", "tableSetupColumn", "tableSetupScrollFreeze", "tableHeadersRow",
                    "tableSetBgColor", "pushStyleColor", "popStyleColor", "setStyleColor", "setStyleProperties", "setTheme", "pushStyleVar",
                    "popStyleVar", "setWindowFontScale", "pushFont", "popFont", "withFont", "createTexture", "destroyTexture", "image", "imageButton",
                    "getWindowDrawList", "getForegroundDrawList", "getBackgroundDrawList", "pushId", "popId", "beginGroup", "endGroup", "indent",
                    "unindent", "alignTextToFramePadding", "beginDisabled", "endDisabled", "getScrollX", "getScrollY", "getScrollMaxX",
                    "getScrollMaxY", "setScrollX", "setScrollY", "setScrollHereX", "setScrollHereY", "isItemActive", "isItemEdited",
                    "isItemActivated", "isItemDeactivated", "isItemDeactivatedAfterEdit", "isItemFocused", "isItemVisible", "isWindowHovered",
                    "isWindowFocused", "isWindowCollapsed", "isWindowAppearing", "getMousePos", "isMouseDown", "isMouseClicked",
                    "isMouseDoubleClicked", "isMouseReleased", "isMouseDragging", "getMouseDragDelta", "resetMouseDragDelta", "calcTextSize",
                    "getFrameHeight", "getFrameHeightWithSpacing", "getTextLineHeight", "getTextLineHeightWithSpacing", "framerate",
                    "addInputCharacters", "addKeyEvent", "sendKey", "addMouseWheel", "wantTextInput", "withWindow", "withChild", "withTreeNode",
                    "withTabBar", "withTabItem", "withTable", "withPopup", "withPopupModal", "withContextItem", "withContextWindow", "withTooltip",
                    "withItemTooltip", "withMenuBar", "withMenu", "withListBox", "withCombo", "withGroup", "withDisabled", "withId", "logWindow",
                    "toast", "alert", "confirm", "singleChoice", "multiChoice", "statsOverlay", "dataTable", "createWindow", "closeWindow",
                    "launchFloaty", "launch", "launchPrivileged", "addButtonEx", "setTouchable", "setMoreTouch", "setGlobalAlpha", "setSecure",
                    "collapseWindow", "expandWindow", "getComponent", "dp", "px", "listClipper", "showMetricsWindow", "showDebugLogWindow",
                    "showIdStackToolWindow", "showStyleEditor", "showAboutWindow", "showUserGuide", "dragFloat2", "dragFloat3", "dragFloat4",
                    "dragInt2", "dragInt3", "dragInt4", "sliderFloat2", "sliderFloat3", "sliderFloat4", "sliderInt2", "sliderInt3", "sliderInt4",
                    "inputFloat2", "inputFloat3", "inputFloat4", "inputInt2", "inputInt3", "inputInt4", "dragFloatRange2", "dragIntRange2",
                    "sliderAngle", "tableGetSortSpecs", "ints", "addMousePosEvent", "addMouseButtonEvent", "isKeyDown", "isKeyPressed", "isKeyReleased", "getKeyPressedAmount", "getKeyName",
                    "isKeyChordPressed", "shortcut", "setNextItemShortcut", "getClipboardText", "setClipboardText", "beginDragDropSource", "setDragDropPayload",
                    "endDragDropSource", "beginDragDropTarget", "acceptDragDropPayload", "endDragDropTarget", "getDragDropPayload", "withDragDropSource",
                    "withDragDropTarget", "pushClipRect", "popClipRect", "beginMultiSelect", "endMultiSelect", "setNextItemSelectionUserData",
                    "isItemToggledSelection", "selectionStorage", "setNextWindowFocus", "setNextWindowCollapsed",
                ],
                kind = ScriptInstallKind.FUNCTION,
            ),
            ScriptInstallNames(
                names = [
                    "Ref", "GetBackendCapabilities", "ListExamples", "GetExampleSource", "RunExample", "LogWindow", "Toast", "Alert", "Confirm",
                    "SingleChoice", "MultiChoice", "StatsOverlay", "DataTable", "GetComponent", "CreateWindow", "CloseWindow", "LaunchFloaty",
                    "Launch", "LaunchPrivileged", "AddButtonEx", "SetTouchable", "SetMoreTouch", "SetGlobalAlpha", "SetSecure", "CollapseWindow",
                    "ExpandWindow", "SetNextWindowPos", "SetNextWindowSize", "SetNextWindowSizeConstraints", "SetNextWindowBgAlpha",
                    "SetKeyboardFocusHere", "Begin", "End", "BeginChild", "EndChild", "GetWindowPos", "GetWindowSize", "GetContentRegionAvail",
                    "GetCursorPos", "SetCursorPos", "Text", "TextWrapped", "TextColored", "Button", "SmallButton", "Checkbox", "RadioButton",
                    "SliderFloat", "SliderInt", "DragFloat", "DragInt", "InputText", "ColorEdit3", "ColorEdit4", "ProgressBar", "Combo", "Selectable",
                    "CollapsingHeader", "TreeNode", "TreePop", "BeginTabBar", "EndTabBar", "BeginTabItem", "EndTabItem", "SameLine", "Separator",
                    "Spacing", "NewLine", "Dummy", "SetNextItemWidth", "PushItemWidth", "PopItemWidth", "IsItemClicked", "IsItemHovered",
                    "IsAnyItemHovered", "IsAnyItemActive", "ShowDemoWindow", "StyleColorsDark", "StyleColorsLight", "StyleColorsClassic",
                    "WantCaptureMouse", "SeparatorText", "Bullet", "BulletText", "LabelText", "TextDisabled", "ArrowButton", "InvisibleButton",
                    "InputInt", "InputFloat", "InputTextMultiline", "BeginListBox", "EndListBox", "ListBox", "ColorPicker3", "ColorPicker4",
                    "ColorButton", "PlotLines", "PlotHistogram", "TreeNodeEx", "VSliderFloat", "VSliderInt", "Toggle", "ToggleSwitch", "BeginCombo",
                    "EndCombo", "OpenPopup", "BeginPopup", "BeginPopupModal", "BeginPopupContextItem", "BeginPopupContextWindow", "EndPopup",
                    "CloseCurrentPopup", "IsPopupOpen", "BeginMenuBar", "EndMenuBar", "BeginMenu", "EndMenu", "MenuItem", "SetTooltip",
                    "SetItemTooltip", "BeginTooltip", "BeginItemTooltip", "EndTooltip", "BeginTable", "EndTable", "TableNextRow", "TableNextColumn",
                    "TableSetColumnIndex", "TableSetupColumn", "TableSetupScrollFreeze", "TableHeadersRow", "TableSetBgColor", "PushStyleColor",
                    "PopStyleColor", "SetStyleColor", "SetStyleProperties", "SetTheme", "PushStyleVar", "PopStyleVar", "SetWindowFontScale",
                    "PushFont", "PopFont", "WithFont", "Icon", "ColorU32", "CreateTexture", "DestroyTexture", "Image", "ImageButton",
                    "GetWindowDrawList", "GetForegroundDrawList", "GetBackgroundDrawList", "PushID", "PopID", "BeginGroup", "EndGroup", "Indent",
                    "Unindent", "AlignTextToFramePadding", "BeginDisabled", "EndDisabled", "GetScrollX", "GetScrollY", "GetScrollMaxX",
                    "GetScrollMaxY", "SetScrollX", "SetScrollY", "SetScrollHereX", "SetScrollHereY", "IsItemActive", "IsItemEdited",
                    "IsItemActivated", "IsItemDeactivated", "IsItemDeactivatedAfterEdit", "IsItemFocused", "IsItemVisible", "IsWindowHovered",
                    "IsWindowFocused", "IsWindowCollapsed", "IsWindowAppearing", "GetMousePos", "IsMouseDown", "IsMouseClicked",
                    "IsMouseDoubleClicked", "IsMouseReleased", "IsMouseDragging", "GetMouseDragDelta", "ResetMouseDragDelta", "CalcTextSize",
                    "GetFrameHeight", "GetFrameHeightWithSpacing", "GetTextLineHeight", "GetTextLineHeightWithSpacing", "Dp", "Px",
                    "ListClipper", "ShowMetricsWindow", "ShowDebugLogWindow", "ShowIDStackToolWindow", "ShowStyleEditor", "ShowAboutWindow",
                    "ShowUserGuide", "DragFloat2", "DragFloat3", "DragFloat4", "DragInt2", "DragInt3", "DragInt4", "SliderFloat2", "SliderFloat3",
                    "SliderFloat4", "SliderInt2", "SliderInt3", "SliderInt4", "InputFloat2", "InputFloat3", "InputFloat4", "InputInt2", "InputInt3",
                    "InputInt4", "DragFloatRange2", "DragIntRange2", "SliderAngle", "TableGetSortSpecs",
                    "AddMousePosEvent", "AddMouseButtonEvent", "IsKeyDown", "IsKeyPressed", "IsKeyReleased", "GetKeyPressedAmount", "GetKeyName",
                    "IsKeyChordPressed", "Shortcut", "SetNextItemShortcut", "GetClipboardText", "SetClipboardText", "BeginDragDropSource", "SetDragDropPayload",
                    "EndDragDropSource", "BeginDragDropTarget", "AcceptDragDropPayload", "EndDragDropTarget", "GetDragDropPayload", "WithDragDropSource",
                    "WithDragDropTarget", "PushClipRect", "PopClipRect", "BeginMultiSelect", "EndMultiSelect", "SetNextItemSelectionUserData",
                    "IsItemToggledSelection", "SelectionStorage", "SetNextWindowFocus", "SetNextWindowCollapsed",
                ],
                kind = ScriptInstallKind.FUNCTION,
            ),
            ScriptInstallNames(
                names = [
                    "Limits", "BackendStatus", "Cond", "WindowFlags", "InputTextFlags", "SliderFlags", "TreeNodeFlags", "SelectableFlags",
                    "TabBarFlags", "TabItemFlags", "MouseButton", "ChildFlags", "ComboFlags", "PopupFlags", "HoveredFlags", "FocusedFlags",
                    "ColorEditFlags", "TableFlags", "TableColumnFlags", "TableRowFlags", "TableBgTarget", "SortDirection", "Col", "StyleVar",
                    "Themes", "Appearance", "Fonts", "TextureFlags", "DrawListLayer", "DrawFlags", "TouchMode", "WindowMode", "LogLevel",
                    "ToastPosition", "Icons", "Dir", "ButtonFlags", "Key", "density", "Mod", "InputFlags", "DragDropFlags",
                    "MultiSelectFlags",
                ],
                kind = ScriptInstallKind.GETTER,
            ),
        ],
    )
    fun install(installation: ScriptModuleInstallation) {
        this.installation = installation
        scope = installation.scope
        global = installation.global
        installation.onBuilt { members ->
            this.members = members
            members.defineGetter(PLUGIN_ID_KEY, installation) { if (loading) Undefined.instance else PLUGIN_ID }
            (ImguiNames.FUNCTIONS + ImguiNames.ALIASES).forEach { name -> members.defineForwarder(name) }
            ImguiNames.CONSTANTS.forEach { name ->
                members.defineGetter(name, installation) { member(name) }
            }
        }
    }

    /** Defines [name] as a permanent function forwarding to the function of the same name of the API object. */
    private fun ScriptableObject.defineForwarder(name: String) {
        defineProperty(name, installation.function(name, 0) { cx, s, _, args -> call(name, cx, s, args) }, ScriptableObject.PERMANENT)
    }

    /**
     * Defines on the module object the public members of [api] (names not starting with `_`) that it does not have yet:
     * a function as a forwarder, any other value as a getter, as for the listed names. A plugin newer than this host
     * thus keeps its new members on `imgui` without a host release; the names stay out of the declared installs, they
     * exist only after the plugin is loaded.
     *
     * zh-CN: 把 [api] 的公开成员 (不以 _ 开头的名称) 中模块对象尚未定义的, 按与名单内名称相同的方式补挂到模块对象上: 函数定义为转发函数, 其他值定义为 getter.
     * 这样插件比宿主新时, 新成员不必等宿主发版也能经 imgui 使用; 这些名称不进入声明的安装项, 只在插件加载后存在.
     */
    private fun forwardUnlisted(api: Scriptable) {
        val members = members ?: return
        api.ids.forEach { id ->
            val name = id as? String ?: return@forEach
            if (name.startsWith("_") || members.has(name, members)) return@forEach
            when (ScriptableObject.getProperty(api, name)) {
                is Callable -> members.defineForwarder(name)
                else -> members.defineGetter(name, installation) { member(name) }
            }
        }
    }

    /**
     * Defines [name] as a permanent accessor property whose getter runs [read] on each read of the value. An accessor
     * slot (not a lambda data slot) keeps reading the descriptor (`Object.getOwnPropertyDescriptor`, the installs test)
     * from loading the plugin. The getter is built by [installation] so its errors map like the functions' do.
     *
     * zh-CN: 把 [name] 定义为永久的访问器属性, 每次读值时运行 [read]. 用访问器槽 (而不是 lambda 数据槽) 是为了读取描述符
     * (`Object.getOwnPropertyDescriptor`, 安装项测试) 时不触发插件加载. getter 由 [installation] 构建, 错误映射方式与函数一致.
     */
    private fun ScriptableObject.defineGetter(name: String, installation: ScriptModuleInstallation, read: () -> Any?) {
        setGetterOrSetter(name, 0, installation.function(name, 0) { _, _, _, _ -> read() }, false)
        setAttributes(name, ScriptableObject.PERMANENT)
    }

    /** Calls the function [name] of the API object with the object as `this`, as `imgui.name(...)` did on the plugin's object. */
    private fun call(name: String, cx: Context, scope: VarScope, args: Array<out Any?>): Any? {
        val api = api()
        val function = ScriptableObject.getProperty(api, name) as? Callable
            ?: throw IllegalStateException("The ImGui plugin has no function named $name (plugin ${version()})")
        return function.call(cx, scope, api, Array(args.size) { args[it] })
    }

    /** The value of [name] on the API object, undefined when the plugin does not define it. */
    private fun member(name: String): Any? = ScriptableObject.getProperty(api(), name).takeUnless { it === Scriptable.NOT_FOUND } ?: Undefined.instance

    /** The API object, loading the plugin on the first call; one object per runtime, whichever thread asks first. */
    private fun api(): Scriptable = synchronized(this) {
        api ?: load().also {
            api = it
            forwardUnlisted(it)
        }
    }

    private fun load(): Scriptable {
        check(!loading) { "A member of imgui is used while index.js of the ImGui plugin is running" }
        loading = true
        try {
            val plugin = runtime.plugins.load(PACKAGE)
            val entry = js_require(runtime, plugin.mainScriptPath) as? Callable
                ?: throw IllegalStateException("index.js of the ImGui plugin does not export a function")
            // `this` of index.js: a private object over the global object. `globalObject.files` resolves through the
            // prototype; `Object.defineProperty(globalObject, "imgui", ...)` defines the name here, not on the global object.
            // zh-CN: index.js 的 this: 以全局对象为原型的私有对象. globalObject.files 经原型解析; Object.defineProperty(globalObject, "imgui", ...)
            // 把名称定义在这里, 不在全局对象上.
            val receiver = RhinoUtils.newNativeObject(scope)
            receiver.prototype = global
            val result = RhinoUtils.withRhinoContext(runtime) { cx -> entry.call(cx, scope, receiver, arrayOf<Any?>(plugin.unwrap())) }
            return result as? Scriptable ?: throw IllegalStateException("index.js of the ImGui plugin did not return its API object")
        } finally {
            loading = false
        }
    }

    private fun version(): Any? = api?.let { ScriptableObject.getProperty(it, "version") as? Callable }?.let { function ->
        try {
            RhinoUtils.withRhinoContext(runtime) { cx -> function.call(cx, scope, api, emptyArray()) }
        } catch (_: Throwable) {
            null
        }
    }

    private companion object {
        /** The plugin's package, also `PLUGIN_ID` of its `index.js` (`__pluginId` of its API object). */
        const val PACKAGE = "io.github.supermonster003.autojs6.plugin.imgui"
        const val PLUGIN_ID = PACKAGE
        const val PLUGIN_ID_KEY = "__pluginId"
    }

}
