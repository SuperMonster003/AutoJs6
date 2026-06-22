# Rhino Augment Inventory Schema

本文件定义 Phase 11A 使用的 Rhino `augment` inventory v1 schema。它描述
`org/autojs/autojs/runtime/api/augment` 中每个 Rhino 内置 JavaScript 模块
如何注册到 `ScriptRuntime.augment(...)`，以及每个导出的 API 在 Node.js
兼容层中的迁移状态。

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
- `key`: Rhino 中由 `Augmentable.key` 计算或 `originateKeyName()` 指定的
  key，例如 `toast`、`Canvas`。
- `className`: Kotlin/Java 类全名。
- `packageName`: 源码包名。
- `source`: 模块源码位置。
- `registration`: `ScriptRuntime.augment(...)` 中的注册位置、顺序和参数。
- `augmentable`: 基类、trait 和对象创建形态。
- `exports`: 当前模块导出的 API entries。
- `migration`: 模块级 Node 迁移状态。

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
- `aliases`: 可选别名。
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

- `Toast`: `Augmentable + Invokable`
- `UI`: `AugmentableProxy`
- `Canvas`: `Augmentable + Constructable`
- `Images`: `Augmentable + AsEmitter`
- `WebSocketFields`: `SimpleGetterProxy`

该 fixture 不是完整 inventory，也不是后续 scanner 的黄金输出。它用于
锁定 v1 schema 的表达能力，确保 S11-02/S11-03 的 scanner 输出有明确
目标结构。
