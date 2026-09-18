# P6.4 JsTextViewLegacy 与 appcompat 1.0.2 移除 (2026-09-18)

对应 [apk-size-reduction-roadmap.md](../apk-size-reduction-roadmap.md) P6.4 (决策 D13 "允许更好方案"), 体积报告见 [apk-size-p6-4-20260918.md](apk-size-p6-4-20260918.md).

## 结论

- issue #4 / #9 (Android 7.x 上 UI 模式 `text` 元素异常) 的根因不在布局 / 主题 / 属性侧, 而在 Rhino 反射 Java 成员的方式: appcompat 1.1+ 的 `AppCompatTextView` / `AppCompatEditText` 声明了 `setTextClassifier(android.view.textclassifier.TextClassifier)`, 该参数类型自 API 26 起才存在; Rhino 为脚本包装任意实例时对整个类做一次成员反射, `Method.getParameterTypes()` 在 API 24 / 25 上对该方法抛 `NoClassDefFoundError`, 整个类的反射失败, 于是任何 `AppCompatTextView` 子类都无法从脚本使用. 旧版 appcompat 1.0.2 没有这个方法, 所以 `JsTextViewLegacy` 当年 "修复" 了症状.
- 修复落在 Rhino 分支 `Rhino-For-AutoJs6` (提交 `43767f337`): `JavaMembers.registerMethod` 捕获 `NoClassDefFoundError | TypeNotPresentException` 后跳过该方法 (与 6.7.0 Alpha13 的 `ExecutableBox` 泛型签名回退同一思路). 这类方法在当前系统上本来就不可能被调用, 跳过没有功能损失.
- 宿主移除 `libs/androidx-appcompat-1_0_2` 模块 (整套 appcompat 1.0.2 类与资源), `JsTextViewLegacy` 及 4 个配套文件, 4 处 `SDK_INT < O` 分支; `text` 标签在所有 API 级别映射 `JsTextView` (appcompat 1.7.1).
- universal APK 33,136,742 -> 32,906,264 字节 (-230,478 字节 = -0.22 MiB; 对基线 -8,239,791 字节 = -7.86 MiB, -20.0%), 条目 3923 -> 3829, DEX 定义方法 308,453 -> 304,553, `androidx.appcompatlegacy` 归属 0.33 MiB -> 0.
- 证据等级 E3: AVD API 24 + AVD API 25 + Sony G8441 API 28 + Redmi 22120RN86C API 33 脚本验证, JVM 全量单测.

## (a) 复现

`build/tmp-size/p6-4-probe.js` 与 `p6-4-probe2.js` 在 AVD API 24 (P6.2 构建, 仍含旧版 appcompat) 上:

| 用例 | 结果 |
| --- | --- |
| `<text>` 标签 (映射 `JsTextViewLegacy`) | OK |
| `<org.autojs.autojs.core.ui.widget.JsTextView>` FQN 标签 (appcompat 1.7.1) | `NoClassDefFoundError: android.view.textclassifier.TextClassifier` |
| `<androidx.appcompat.widget.AppCompatTextView>` FQN 标签 | 同上 |
| `new Packages.androidx.appcompat.widget.AppCompatTextView(activity)` / `AppCompatEditText` / `new JsTextView(activity)` | 同上, 脚本被终止 (Java `Error` 不可被 JS `catch` 捕获) |
| `<input>` (`JsEditText` 继承平台 `EditText`), `<button>`, `<checkbox>`, `<img>`, `<spinner>`, `new android.widget.TextView(activity)` | 全部 OK |

堆栈 (截取):

```text
java.lang.NoClassDefFoundError: android.view.textclassifier.TextClassifier
    at libcore.reflect.InternalNames.getClass(InternalNames.java:55)
    at java.lang.Class.getDexCacheType(Class.java:2551)
    at java.lang.reflect.AbstractMethod.getParameterTypes(AbstractMethod.java:169)
    at java.lang.reflect.Method.getParameterTypes(Method.java:193)
    at org.mozilla.javascript.JavaMembers$MethodSignature.<init>(JavaMembers.java:434)
    at org.mozilla.javascript.JavaMembers.registerMethod(JavaMembers.java:408)
    at org.mozilla.javascript.JavaMembers.discoverPublicMethods(JavaMembers.java:403)
    at org.mozilla.javascript.JavaMembers_jdk11.discoverPublicMethods(JavaMembers_jdk11.java:24)
    at org.mozilla.javascript.JavaMembers.discoverAccessibleMethods(JavaMembers.java:376)
    at org.mozilla.javascript.JavaMembers.reflect(JavaMembers.java:456)
    at org.mozilla.javascript.NativeJavaObject.initMembers(NativeJavaObject.java:100)
    at org.autojs.autojs.core.ui.nativeview.NativeView.<init>(NativeView.java:41)
    at org.autojs.autojs.core.ui.ViewExtras.getNativeView(ViewExtras.java:51)
    at org.autojs.autojs.runtime.api.augment.ui.UI$UIProxyLayoutInflaterDelegate.afterInflateView(UI.kt:358)
    at org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater.doInflation(DynamicLayoutInflater.kt:468)
```

issue #4 (6.0.1, 雷电 / 逍遥模拟器 Android 7.1) 记录的 "Wrapped android.view.InflateException: java.lang.IllegalArgumentException: Cannot add a null child view to a ViewGroup" 是同一异常在旧版 `ui.js` 模块里被吞掉后的下游表现. issue #9 无细节, 同类.

`javap` 核对 appcompat 1.7.1 `classes.jar`: `AppCompatTextView` 与 `AppCompatEditText` 各有 `setTextClassifier(TextClassifier)` / `getTextClassifier()` (以及 `access$` 桥接), `AppCompatButton` / `AppCompatCheckBox` / `AppCompatImageView` / `AppCompatSpinner` 没有 API 26+ 类型的签名, 与复现结果一致.

## (b) 根因与修复

- 为什么只有 Rhino 出错: 平台 `LayoutInflater` 与 Kotlin 代码只调用具体方法, 从不枚举成员; Rhino 的 `JavaMembers.reflect` 对每个首次包装的类执行 `getMethods()` / `getDeclaredMethods()` 并为每个方法构造 `MethodSignature` (`getParameterTypes()`), ART 在解析 dex cache 中不存在的类型时抛 `NoClassDefFoundError`. 6.7.0 Alpha13 已让 `ExecutableBox` 在泛型签名解析失败时回退到原始类型, 但 `registerMethod` 在更早一步就失败了.
- 修复 (`Rhino-For-AutoJs6` `rhino/src/main/java/org/mozilla/javascript/JavaMembers.java`): `registerMethod` 包裹 `try { ... } catch (NoClassDefFoundError | TypeNotPresentException e) { return null; }`, 调用处 `discoverAccessibleMethods` 对 `null` 不再调用 `setAccessible`. 重建 `rhino-all` fat jar (`./gradlew :rhino-all:shadowJar`, JDK 21, 需联网取 JLine 3.30.6) 并复制为 `libs/org-mozilla-rhino-2_0_0-SNAPSHOT.jar`: 1186 个条目与原 jar 一一对应, 仅 `JavaMembers` / `JavaMembers$MethodSignature` / `BeanProperty` / `FieldAndMethods` / `MemberBox$1` / `MemberBox$2` 6 个 class 内容变化 (后 4 个为同一编译单元的重新编译), JLine 版本不变 (宿主的 `slimRhinoJar` 仍剔除 `org/jline/**`).
- 副作用评估: 被跳过的方法在该系统上本来无法调用 (其参数类型不可加载); 在 API 26+ 上 `TextClassifier` 存在, 行为不变 (验证脚本在 API 28 / 33 上 `setTextClassifier` 仍可见).

## (c) 宿主改动

- `core/ui/xml/XmlConverter.kt`: `"text"` 固定映射 `JsTextView`.
- `core/ui/inflater/DynamicLayoutInflater.kt`: 固定注册 `JsTextViewInflater`, 去掉两个 legacy import.
- `core/ui/attribute/ViewAttributesFactory.kt`: 固定注册 `JsTextViewAttributes`.
- `runtime/api/augment/global/Classes.kt`: `JsTextView` 全局代理固定指向 `core/ui/widget/JsTextView`.
- 删除 `core/ui/widget/JsTextViewLegacy.kt`, `core/ui/attribute/JsTextViewLegacyAttributes.kt`, `core/ui/attribute/AppCompatTextViewLegacyAttributes.kt`, `core/ui/inflater/inflaters/JsTextViewLegacyInflater.kt`, `core/ui/inflater/inflaters/TextViewLegacyInflater.kt`.
- 删除 `libs/androidx-appcompat-1_0_2/` (`appcompat-legacy.aar` + `build.gradle.kts`), `settings.gradle.kts` 的模块名与 `app/build.gradle.kts` 的 `implementation(project(":libs:androidx-appcompat-1_0_2"))`.
- 三个文件的 `android.os.Build` import 随分支一起去掉 (各自无其它用途).

## 验证

### 脚本 (`build/tmp-size/p6-4-verify.js`, 四环境全部 OK)

| 检查项 | AVD 24 | AVD 25 | Sony 28 | Redmi 33 |
| --- | --- | --- | --- | --- |
| `JsTextViewLegacy` / `androidx.appcompatlegacy.widget.AppCompatTextView` 不可加载 | OK | OK | OK | OK |
| `<text>` -> `JsTextView`, text / textSize / textColor / gravity / textStyle bold\|italic / ellipsize / maxLines / autoLink / lineSpacingExtra / padding / typeface / textAllCaps 生效 | OK | OK | OK | OK |
| 全局 `JsTextView` 类, `new JsTextView(activity)` + `addView`, `ui.inflate` + `attr()` | OK | OK | OK | OK |
| `setTextClassifier` 可见性 = `SDK_INT >= 26`, `setText` / `setPrecomputedText` / `setTextFuture` 保留 | false / true | false / true | true / true | true / true |
| 直接构造 `AppCompatTextView` / `AppCompatEditText` 并加入布局 | OK | OK | OK | OK |
| issue #4 样例风格布局 (appbar + toolbar + text + input + button) | OK | OK | OK | OK |

`isAllCaps()` 自 API 28 起才有, 验证脚本在 24 / 25 上按 n/a 处理 (与本改动无关).

### 其它

- `:app:compileAppDebugKotlin` 与 `:app:assembleAppRelease` 通过; `:app:testAppDebugUnitTest` 全量 2881 项, 0 失败, 5 跳过.
- 体积 ([apk-size-p6-4-20260918.md](apk-size-p6-4-20260918.md)):

| 指标 | P6.2 | P6.4 | 差 |
| --- | ---: | ---: | ---: |
| universal 字节 | 33,136,742 | 32,906,264 | -230,478 |
| arm64-v8a 字节 | 31,939,716 | 31,709,238 | -230,478 |
| 条目数 | 3923 | 3829 | -94 |
| DEX 定义方法 | 308,453 | 304,553 | -3,900 |
| `androidx.appcompatlegacy` 归属 MiB | 0.33 | 0 | -0.33 |
| 对基线 (41,146,055) | -7.64 MiB | -7.86 MiB (-20.0%) | |

## 后续

- Rhino 分支提交 `43767f337` 未推送; 宿主 jar 已更新. 若上游 Rhino 接受同类补丁可在下次同步时替换.
- 若日后再遇到 "Android 7.x 上某个 appcompat 控件从脚本用不了", 先用 `javap` 查该类是否声明了 API 26+ 类型的参数 / 返回值; 修复后这类签名会被跳过而非致错.
