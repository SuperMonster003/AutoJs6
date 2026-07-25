# Rhino Augment Inventory Schema

本文件定义 Phase 11A 使用的 Rhino `augment` inventory v1 schema。它描述
`org/autojs/autojs/runtime/api/augment` 中每个 Rhino 内置 JavaScript 模块
如何注册到 `ScriptRuntime.augment(...)`，以及每个导出的 API 在 Node.js
兼容层中的迁移状态。schema id 和版本保持 v1，并以可选字段表达声明式
多名称挂载。这里的兼容性是后向读取兼容：更新后的 schema 仍接受未包含
新字段的既有 v1 inventory。由于旧版 schema 使用
`additionalProperties: false`，持有旧版 schema 的 validator 必须先更新
schema，才能读取新 generator 输出的 `keys` 和 `mountNames`。

机器可读 schema 位于：

- `docs/nodejs/rhino-compat/rhino-augment-inventory.schema.json`

覆盖型 fixture 位于：

- `docs/nodejs/rhino-compat/fixtures/rhino-augment-inventory.sample.json`

## Schema 标识

- `schema`: `autojs6-node-rhino-augment-inventory-v1`
- `schemaVersion`: `1`

后续 `generateNodeRhinoAugmentInventory` 应输出同一 schema 标识的 JSON
报告。手工维护的 migration status manifest 也应引用同一 module/API id。

## Root

Root object 描述一次 inventory 快照：

- `schema`: 固定 schema id。
- `schemaVersion`: 固定整数版本。
- `generatedAt`: 生成时间，ISO-8601 字符串。fixture 可使用固定时间。
- `source`: scanner 输入和版本信息。
- `statusVocabulary`: 当前允许的迁移状态词汇。
- `modules`: 按 `ScriptRuntime.augment(...)` 注册顺序排序的模块数组。
- `totals`: 可选统计信息。

## Module

每个 `modules[]` entry 对应一个 Rhino augment 模块、嵌套模块或 proxy
surface。稳定 id 使用 lower snake case，例如 `toast`、`images`、
`websocket_fields`。

关键字段：

- `id`: inventory 内稳定模块 id。
- `key`: primary name。它镜像 `Augmentable.keys.first().name`，并继续
  作为 v1 兼容字段，例如 `toast`、`Canvas`。
- `keys`: 可选的有序声明式名称 descriptor 数组。每项分别声明 `name`
  和该名称的 `withDollarPrefix` 策略，对应源码中的 `AugmentableKey`。
- `mountNames`: 可选的最终挂载名称数组。它是 `keys` 的稳定展开结果。
- `className`: Kotlin/Java 类全名。
- `packageName`: 源码包名。
- `source`: 模块源码位置。
- `registration`: `ScriptRuntime.augment(...)` 中的注册位置、顺序和参数。
- `augmentable`: 基类、trait 和对象创建形态。
- `exports`: 当前模块导出的 API entries。
- `migration`: 模块级 Node 迁移状态。

### 多名称挂载

新 generator 应同时输出 `keys` 和 `mountNames`；schema 不允许只出现
其中一个字段。`keys` 必须非空，并遵循以下约束：

- `keys[0].name` 等于 `key`，作为 primary name。
- 每个 `name` 必须包含非空白字符，并在 `keys` 中唯一，数组顺序等于
  源码中的声明顺序。
- 源码中的 `keys` 必须使用稳定的 `val` 声明；可变 `var` 或 scanner
  无法静态解析的初始化表达式会被计入 unresolved。
- 每个 descriptor 先产生其原始 `name`；当 `withDollarPrefix` 为
  `true` 时，再产生 `"$" + name`。
- `mountNames` 按上述顺序展开且不得碰撞，并必须与
  `exports.moduleKeys` 完全一致。
- `registration.withDollarPrefix` 保留为必填 v1 字段，并镜像 primary
  descriptor 的 `withDollarPrefix`，供旧 consumer 继续读取；它不再
  表示 `ScriptRuntime` 调用点存在同名布尔参数。

只包含 v1 字段的 inventory 等价于单个 descriptor：

```json
{
  "name": "toast",
  "withDollarPrefix": true
}
```

其中两个值分别取自 `module.key` 和
`module.registration.withDollarPrefix`。

`assignWithRuntime` 不挂载模块对象，因此 scanner 对该注册方式继续使用
v1 的有效视图：只保留 primary name，并将其 `withDollarPrefix` 记为
`false`。这避免 inventory 虚构源码运行时不会产生的 dollar alias。

由于 JSON Schema 无法跨字段表达数组首项相等和数组展开关系，generator
和 consumer 的语义校验需要负责上述不变量。

## Augmentable model

`augmentable.baseClass` 支持：

- `Augmentable`
- `AugmentableProxy`
- `unknown`

`augmentable.traits` 支持：

- `Invokable`: 通过 `invoke(...)` 让模块对象本身可调用。
- `Constructable`: 通过 `construct(...)` 让模块对象可 `new`。
- `Versatile`: 同时可调用和可构造。
- `AsEmitter`: 为模块对象挂接事件 emitter 原型。
- `JsBuildInObjectExtensible`: 扩展 JS 内建对象原型。
- `SimpleGetterProxy`: 使用 `SimpleGetterProxy` 或
  `@AugmentableSimpleGetterProxyInterface` 提供延迟 getter。

`augmentable.construction` 用来描述 Rhino 侧最终暴露对象形态：

- `plain_object`
- `base_function_invokable`
- `base_function_constructable`
- `base_function_versatile`
- `proxy_object`
- `prototype_extension`
- `unknown`

## Export entries

`exports.entries[]` 是 inventory 的核心。每个 entry 描述一个可迁移的
API surface，而不要求 scanner 立即理解完整函数签名。

关键字段：

- `id`: 模块内稳定 API id，例如 `toast.call`、`images.findImage`。
- `name`: Kotlin/Rhino 原始名称。
- `exposedName`: Rhino 中对脚本暴露的名称。
- `kind`: API 形态。
- `assignment`: 来自哪个 `Augmentable` assignment list 或注册机制。
- `source`: 源码位置。
- `attributes`: Rhino flag 和是否 dollar alias。
- `aliases`: 可选的有序别名数组。对于代表模块本身的 generator entry，
  `exposedName` 使用 primary name，其余 `mountNames` 应依序写入
  `aliases`，因此同一 entry 可以拥有多个别名。
- `signature`: 可选人读签名。
- `migration`: 单个 API 的 Node 迁移状态。
- `risks`: 资源、权限、安全或语义风险。

`kind` 至少覆盖：

- `augment_object`
- `global_function`
- `global_property`
- `self_function`
- `self_property`
- `getter`
- `getter_setter`
- `java_class`
- `constructor`
- `invokable`
- `proxy_accessor`
- `event_emitter`
- `prototype_extension`
- `alias`
- `simple_getter_proxy`
- `constant`

`assignment` 至少覆盖 `Augmentable` 当前暴露的数组和特殊机制：

- `augment_key`
- `dollar_augment_key`
- `selfAssignmentProperties`
- `globalAssignmentProperties`
- `selfAssignmentFunctions`
- `globalAssignmentFunctions`
- `selfAssignmentJavaClasses`
- `globalAssignmentJavaClasses`
- `selfAssignmentGetters`
- `selfAssignmentGettersAndSetters`
- `globalAssignmentGetters`
- `prototype`
- `proxy`
- `annotation`
- `manual`
- `inferred`

## Migration status

模块级和 API 级 `migration.status` 使用同一词汇：

- `supported`
- `supported_async`
- `renamed`
- `partial`
- `blocked_by_design`
- `unsupported`

建议含义与 Phase 11A 任务文档保持一致：

- `supported`: Node 中已有同步或等价语义。
- `supported_async`: Node 中可用，但必须通过 Promise/async API。
- `renamed`: 能力可用，但模块名、方法名或参数形态不同。
- `partial`: 部分可用，缺关键参数、权限路径、资源释放或 packaged 覆盖。
- `blocked_by_design`: 违反 Safe Node Profile 边界，明确不实现。
- `unsupported`: 尚未实现，且暂未判定为永久拒绝。

## Risk tags

`risks[]` 用于后续排序和 gate 提示。v1 允许：

- `none`
- `raw_java_object`
- `raw_android_object`
- `lifecycle_resource`
- `permission`
- `background_execution`
- `scoped_fs`
- `provider_availability`
- `event_loop_semantics`
- `thread_model`
- `packaged_parity`
- `security_boundary`

## S11-01 coverage fixture

`fixtures/rhino-augment-inventory.sample.json` 覆盖以下形态：

- `Toast`: `Augmentable + Invokable`，并覆盖多个声明名称、逐名称
  dollar prefix 策略和多值 `exports.entries[].aliases`
- `UI`: `AugmentableProxy`
- `Canvas`: `Augmentable + Constructable`
- `Images`: `Augmentable + AsEmitter`
- `WebSocketFields`: `SimpleGetterProxy`，并保留省略 `keys`/`mountNames`
  的既有 v1 文档形态

该 fixture 不是完整 inventory，也不是后续 scanner 的黄金输出。它用于
锁定 v1 schema 的表达能力，确保 S11-02/S11-03 的 scanner 输出有明确
目标结构。
