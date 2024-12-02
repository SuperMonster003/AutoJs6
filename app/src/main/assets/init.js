/**
 * <p>Since AutoJs6 6.6.0, the current file (`init.js`) does not contain any code by default,
 * but individual developers can still use this file to import and extend modules.</p>
 * <p>For example, if a developer has written a module named "apple.js"
 * and the file is placed in the `\app\src\main\assets\modules\` directory,
 * they can write the following code in `init.js` to import and extend the "apple" module:</p>
 * <pre>"apple.js";</pre>
 * <p>The ".js" extension can be omitted:</p>
 * <pre>"apple";</pre>
 * <p>If more modules need to be imported and extended, an array form can be used:</p>
 * <pre>[ "apple", "orange", "banana" ];</pre>
 * <p>Alternatively, a pipe symbol can be used to replace the array:</p>
 * <pre>"apple|orange|banana";</pre>
 * <p>If preprocessing is needed before module import, or a custom import method is required,
 * a global anonymous function can be used:</p>
 * <pre>function(scriptRuntime, scope) {
 *     let apple = require("apple.js");
 *
 *     apple.color = "dark-red";
 *     apple.amount += 2;
 *     delete apple.isSold;
 *
 *     scope.apple = apple;
 * };</pre>
 * <p>In the above example, the "apple" module is extended to the `scope` object under the name "apple".</p>
 * <p>The following method can be used to change the extension name:</p>
 * <pre>function(scriptRuntime, scope) {
 *     scope.myAppleObject = require("apple.js");
 * };</pre>
 * <p>Additionally, a global object can be directly written in `init.js` for module name remapping:</p>
 * <pre>{ myAppleObject: "apple.js" }</pre>
 * <p>If you want to extend the module onto an existing built-in module instead of `scope`,
 * the global anonymous function form can still be used:</p>
 * <pre>function(scriptRuntime, scope) {
 *     images.apple = require("apple.js");
 * };</pre>
 * <p>When the module name matches an existing built-in module name,
 * the module will be merged into the built-in module:</p>
 * <pre>"device";</pre>
 * <p>When merging, properties in the module will override properties with the same name in the built-in module,
 * so extra caution is needed during merging.</p>
 * <p>Each module needs to export its output using the `module.exports = ...` format.</p>
 * <p>It is recommended that all modules follow a unified format specification:</p>
 * <pre>module.exports = function(scriptRuntime, scope) {
 *     // Module internal logic.
 *     ...
 *
 *     // Module output content.
 *     return ...
 * };</pre>
 * <p>In this context, the function parameter `scriptRuntime` represents the script runtime object,
 * and `scope` represents the scope object, which usually directly references the object `global`.</p>
 *
 * zh-CN:
 *
 * <p>自 AutoJs6 6.6.0 起, 当前文件 (`init.js`) 默认情况下不包含任何代码, 但个人开发者仍可在此文件进行模块引入及扩展.</p>
 * <p>例如个人开发者编写了一个模块, 文件名为 "apple.js", 且文件已放入 `\app\src\main\assets\modules\` 目录,
 * 此时在 `init.js` 中可编写以下代码进行 "apple" 模块的引入及扩展:</p>
 * <pre>"apple.js";</pre>
 * <p>其中, ".js" 可省略:</p>
 * <pre>"apple";</pre>
 * <p>如果有更多模块需要引入并扩展, 可使用数组形式:</p>
 * <pre>[ "apple", "orange", "banana" ];</pre>
 * <p>也可以使用管道符号替代数组:</p>
 * <pre>"apple|orange|banana";</pre>
 * <p>如需进行模块引入前的预处理, 或自定义引入方式, 可使用全局匿名函数方式:</p>
 * <pre>function(scriptRuntime, scope) {
 *     let apple = require("apple.js");
 *
 *     apple.color = "dark-red";
 *     apple.amount += 2;
 *     delete apple.isSold;
 *
 *     scope.apple = apple;
 * };</pre>
 * <p>上述示例中, "apple" 模块扩展到 `scope` 对象上的名称也是 "apple".</p>
 * <p>以下方式可修改扩展名称:</p>
 * <pre>function(scriptRuntime, scope) {
 *     scope.myAppleObject = require("apple.js");
 * };</pre>
 * <p>除此之外, 还可以直接在 `init.js` 编写一个全局对象进行模块名称重映射:</p>
 * <pre>{ myAppleObject: "apple.js" }</pre>
 * <p>如希望将模块扩展在现有内置模块上, 而非 `scope`, 依然可使用全局匿名函数形式:</p>
 * <pre>function(scriptRuntime, scope) {
 *     images.apple = require("apple.js");
 * };</pre>
 * <p>当模块名称与现有内置模块名称相同时, 模块将合并至内置模块:</p>
 * <pre>"device";</pre>
 * <p>合并时, 模块中的属性将覆盖内置模块中的同名属性, 因此合并时要额外小心</p>
 * <p>每个模块都需要使用 `module.exports = ...` 形式导出模块的输出内容.</p>
 * <p>建议所有模块都遵循统一的格式规范:</p>
 * <pre>module.exports = function(scriptRuntime, scope) {
 *     // 模块内部逻辑.
 *     ...
 *
 *     // 模块输出内容.
 *     return ...
 * };</pre>
 * <p>其中, 函数体参数 `scriptRuntime` 表示脚本运行时对象, `scope` 表示作用域, 通常它直接引用全局对象 `global`.</p>
 */
