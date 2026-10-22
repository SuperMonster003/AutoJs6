# 设置归档格式 v1 (archive-format-v1)

本文是 [ROADMAP.md](ROADMAP.md) 附录 B 的正式版 (P14.2), 规定设置归档 (`format` 为 `autojs6-settings`, `formatVersion` 为 1) 的文件命名与 zip 结构, `manifest.json` 与各分类文件的字段, 敏感分类的加密信封, 数据分类与插件目录, 导入规则, 读取方错误码, 以及校验工具 `tools/settings/archive_inspect.py`. 内容按 2026-10-22 工作区代码核对, 与附录 B 草案不同之处以本文为准.

- 写入方: 导出向导, 三种自动备份 (periodic / change / operation), 脚本 `settings.export` 与内部备份库 (供 "撤销上次导入" 使用) 都经 `SettingsArchiveWriter` 写出同一格式. 读取方: 导入向导与 "撤销上次导入", 经 `SettingsArchiveReader` 读取.
- 源码路径相对 `app/src/main/java/org/autojs/autojs/`; 决策与任务编号 (D / T / Q / P / R) 见 ROADMAP.
- "必填" 指读取方在缺少该字段或类型不对时拒绝整个文件. "错误码" 指 `SettingsArchiveException.Reason` (第 8 节); 导入时逐条跳过的原因是 `SkipReason` (第 7 节), 不是错误码.
- JSON 文件一律 UTF-8 无 BOM, 由 `SettingsJson` 写出: 缩进 2 空格 (与 Android `JSONObject.toString(2)` 的排版相同), 键后为 `": "`, 换行为 LF, 文件末尾没有换行, 空对象与空数组写作 `{}` 与 `[]`; 字符串中的非 ASCII 字符写字面量, `/` 与 HTML 字符 (`<`, `>`, `&`, `=`, 单引号) 不转义, 双引号, 反斜杠, 控制字符与 U+2028 / U+2029 写成转义序列; 浮点数按 `Float.toString()` / `Double.toString()` 写出 (如 `1.5`, `1.0E10`). 读取同样经 `SettingsJson`: 重复的键, 非有限数 (NaN, Infinity) 与语法错误都拒绝; 数字依次收窄为 Int, Long, Double, 需要整数处接受整数值的小数 (`1.0` 按 1).
- 源码: `core/pref/SettingsJson.kt`.

## 1. 文件与 zip 结构

源码: `core/pref/archive/SettingsArchive.kt`, `core/pref/archive/SettingsArchiveWriter.kt`, `core/pref/archive/SettingsArchiveReader.kt`, `core/pref/archive/SettingsAutoBackup.kt`, `core/pref/archive/SettingsBackupVault.kt`, `runtime/api/augment/settings/SettingsScriptExport.kt`, `ui/settings/transfer/SettingsExportWizard.kt`, `ui/settings/transfer/SettingsImporter.kt`.

### 1.1 文件命名

时间部分 `<yyyyMMdd-HHmmss>` 按系统时区与 `Locale.ROOT` 格式化. `N` 为同一秒内的序号, 从 1 起, 最多 9 位.

| 来源 | 文件名 | 位置 | 敏感分类 (`credentials`) |
| --- | --- | --- | --- |
| 导出向导 | `autojs6-settings-<yyyyMMdd-HHmmss>.zip` (建议名, 用户可改) | 用户经系统文件选择器 (SAF) 选定 | 勾选时以设备密钥加密, 设了口令时另加口令槽 |
| 定期自动备份 | `autojs6-settings-auto-periodic-<yyyyMMdd-HHmmss>[-N].zip` | `<工作目录>/backups/settings/` | 不含 |
| 变更后自动备份 | `autojs6-settings-auto-change-<yyyyMMdd-HHmmss>[-N].zip` | 同上 | 不含 |
| 操作后自动备份 | `autojs6-settings-auto-operation-<yyyyMMdd-HHmmss>[-N].zip` | 同上 | 不含 |
| 脚本 `settings.export(path?, categories?)` | 默认同导出向导, 重名时追加 `-1`, `-2`, ... | 默认同自动备份目录 | 从不导出 |
| 内部备份库 | `auto-import-<yyyyMMdd-HHmmss>[-N].zip` / `auto-reset-<yyyyMMdd-HHmmss>[-N].zip` | `<filesDir>/settings-backups/` (应用私有目录) | 含, 以设备密钥加密 |

- 自动备份: 三种机制 (定期: 每天 / 每周 / 每月; 变更后: 最后一次修改 5 分钟后; 操作后: 导入, 重置或撤销之后) 各有开关, 各保留最新 7 个 (`SettingsAutoBackup.KEEP`). 轮换只认文件名符合 `autojs6-settings-auto-(periodic|change|operation)-([0-9]{8}-[0-9]{6})(-[1-9][0-9]{0,8})?[.]zip` 的文件, 目录中的其他文件不动. 先写 `<文件名>.tmp` 再改名. 内容为默认勾选且非敏感的分类, 不含 `other` 与 `developer`, 含数据分类, 因此没有加密文件.
- 脚本: `path` 为 null / undefined 时写入自动备份目录; `path` 为已存在的目录或以 `/` 结尾时在其中使用默认文件名; 这两种情况遇到同名文件追加 `-1`, `-2`, ..., 不替换; 其他路径即文件本身, 已存在时替换. 先写同目录的 `.autojs6-settings-*.tmp` 再改名.
- 内部备份库: 两种合计保留最新 5 个 (`SettingsBackupVault.KEEP`), 见 7.8.
- 读取方不依据文件名做任何判断, 导入向导接受任意文件名.

### 1.2 zip 条目

| 条目名 | 出现条件 | 内容 | 节 |
| --- | --- | --- | --- |
| `manifest.json` | 总是 | 清单 | 2 |
| `categories/<id>.json` | `manifest.categories` 的每一项 | 分类文件或加密信封 | 3, 4 |
| `data/shortcuts.zip` | 勾选 `data_shortcuts` 且有条目 | 快捷方式备份 | 5 |
| `data/color-library.json` | 勾选 `data_color_library` 且有条目 | 颜色库文档 | 5 |
| `plugins/<packageName>/settings.json` | `manifest.plugins` 的每一项 (第一期不出现) | 插件导出的 JSON 文档 | 6 |

- 名称模式: 分类 id 为 `[a-z][a-z0-9_]*`; 数据文件名 (`data/` 之后的部分) 为 `[a-z0-9][a-z0-9._-]*`; 包名为 `[A-Za-z][A-Za-z0-9_]*([.][A-Za-z][A-Za-z0-9_]*)+`. 因此条目名全是 ASCII.
- 写入顺序: `manifest.json` 最先, 然后是 `manifest.categories` 顺序 (即 `SettingCategory.ALL` 的页面顺序) 的分类文件, `manifest.data` 顺序的数据文件, `manifest.plugins` 顺序的插件文件.
- 写入方不写目录条目. 每个条目用 `ZipEntry` 默认的 DEFLATED 方法与默认压缩级别, 条目时间为写入时刻 (`data/shortcuts.zip` 内部条目的时间另见第 5 节).
- 写入方只调用 `finish()`, 不关闭输出流; 失败时由调用方删除不完整的文件 (导出向导删除所建文档, 脚本与自动备份删除临时文件).

### 1.3 大小与数量限制

- `SettingsArchive.MAX_BYTES` = 16777216 (16 MiB).
- 写入方统计压缩后的输出字节数, 超过即 `TOO_LARGE`.
- 读取方统计两项, 任一超过即 `TOO_LARGE`: 从归档流读到的原始字节数 (只计到目标条目为止), 以及目标文件解压后的字节数.
- 导入向导先把所选文档复制到 `cacheDir/settings-import/import.zip`, 复制超过 16 MiB 即 `TOO_LARGE`.
- 顶层的条目数与分类数没有单独上限; 数据文件内部的上限见第 5 节.

### 1.4 读取方对 zip 的处理

- 每读一个文件都重新打开归档, 用 `java.util.zip.ZipInputStream` 按本地文件头顺序扫描, 不使用中央目录. 从不解压到文件系统.
- 查找: 取第一个名称与目标完全相同的非目录条目. 因此:
  - 重复条目: 只读第一个. 写入方从不写重复条目, 其他工具也不应写.
  - 多余文件与目录条目: 忽略.
  - `manifest.json` 不必是第一个条目 (写入方总把它写在第一个, 校验工具要求如此).
  - 路径穿越: 条目名只用于比较, 从不拼成文件路径, 因此 `../`, 绝对路径与反斜杠都无从生效; manifest 能指向的名称又受 1.2 的名称模式约束.
- 失败: 不是 zip, 没有任何条目, 截断 (`EOFException`) 或 `ZipException` (含 zip 级加密, 不支持的压缩方法) 为 `NOT_AN_ARCHIVE`; 找不到目标文件为 `MISSING_FILE` (只有目录条目的 zip 也是 `MISSING_FILE`).

## 2. manifest.json

源码: `core/pref/archive/SettingsArchiveManifest.kt`, `core/pref/archive/SettingsArchive.kt`.

示例 (导出向导, 勾选外观, 编辑器外观, 凭据与两个数据分类; 写入方的实际排版):

```json
{
  "format": "autojs6-settings",
  "formatVersion": 1,
  "exportedAt": "2026-10-20T15:30:00+08:00",
  "host": {
    "packageName": "org.autojs.autojs6",
    "versionCode": 5592,
    "versionName": "6.8.0"
  },
  "device": {
    "model": "XQ-AT72",
    "sdk": 31
  },
  "categories": [
    {
      "id": "appearance",
      "entries": 8,
      "sensitive": false
    },
    {
      "id": "editor_appearance",
      "entries": 0,
      "sensitive": false
    },
    {
      "id": "credentials",
      "entries": 2,
      "sensitive": true
    }
  ],
  "data": [
    {
      "id": "data_shortcuts",
      "entries": 12,
      "file": "data/shortcuts.zip"
    },
    {
      "id": "data_color_library",
      "entries": 3,
      "file": "data/color-library.json"
    }
  ],
  "plugins": []
}
```

### 2.1 字段

读取方的每项失败都是 `INVALID_MANIFEST`; JSON 语法错误或重复的键也是 `INVALID_MANIFEST`; 归档中没有 `manifest.json` 为 `MISSING_FILE`.

| 字段 | 类型 | 必填 | 取值 | 读取方校验 |
| --- | --- | --- | --- | --- |
| (根) | 对象 | 是 | | 是对象 |
| `format` | 字符串 | 是 | `autojs6-settings` | 完全相等 |
| `formatVersion` | 整数 | 是 | 写入 1 | 整数且 >= 1; 大于 1 时发出警告 `NEWER_FORMAT`, 不拒绝 (2.2) |
| `exportedAt` | 字符串 | 是 | 写入 `yyyy-MM-dd'T'HH:mm:ssxxx`, 系统时区, 如 `2026-10-20T15:30:00+08:00`; UTC 写作 `+00:00`, 不写 `Z` | 只校验是字符串, 不校验格式; 仅供显示 |
| `host` | 对象 | 是 | | 是对象 |
| `host.packageName` | 字符串 | 是 | 导出宿主的包名 | 符合包名模式; 与本机宿主包名不同时发出警告 `OTHER_HOST_PACKAGE`, 不拒绝 |
| `host.versionCode` | 整数 | 是 | `BuildConfig.VERSION_CODE` | 整数; 键迁移据此判断 (7.7) |
| `host.versionName` | 字符串 | 是 | `BuildConfig.VERSION_NAME` | 是字符串 |
| `device` | 对象 | 是 | | 是对象 |
| `device.model` | 字符串 | 是 | `Build.MODEL`, 可为空串 | 是字符串 |
| `device.sdk` | 整数 | 是 | `Build.VERSION.SDK_INT` | 整数 |
| `categories` | 数组 | 是 | 设置分类, 页面顺序; 可为空数组 | 是数组, 元素是对象; `id` 不重复 |
| `categories[].id` | 字符串 | 是 | 分类 id | 符合分类 id 模式; 不要求是本版本认识的分类 |
| `categories[].entries` | 整数 | 是 | 分类文件中的值的个数 (所有存储合计); 加密分类为加密前的个数 | 整数且 >= 0; 不与文件核对 |
| `categories[].sensitive` | 布尔 | 是 | 写入方取自 `SettingCategory.sensitive`, 目前只有 `credentials` 为 true | 是布尔; 不与分类声明或文件核对 |
| `data` | 数组 | 否 | 写入方总是写出, 可为空数组 | 缺少或为 null 视为空数组; 存在但不是数组则拒绝; `id` 不重复, `file` 不重复 |
| `data[].id` | 字符串 | 是 | 数据分类 id | 符合分类 id 模式 |
| `data[].entries` | 整数 | 是 | 文件中的条目数 (第 5 节) | 整数且 >= 0 |
| `data[].file` | 字符串 | 是 | `data/<文件名>` | 以 `data/` 开头, 其余部分符合数据文件名模式 |
| `plugins` | 数组 | 否 | 写入方总是写出; 第一期总是空数组 | 同 `data`; `id` 不重复 |
| `plugins[].id` | 字符串 | 是 | 插件包名 | 符合包名模式 |
| `plugins[].entries` | 整数 | 是 | 插件报告的条目数 | 整数且 >= 0 |

### 2.2 未知字段与更高版本

- 任何层级的未知字段都忽略. v1 的写入方不写额外字段, 读取方也不依赖它们.
- `formatVersion` 大于 1: 读取方照常按 v1 规则读取, 由 `SettingsArchiveManifest.warnings` 给出 `NEWER_FORMAT`, 导入向导第 1 步显示警告 (附录 C Q26). 本版本不认识的分类与类型标签在导入时计 `NEWER_FORMAT` (第 7 节).
- `formatVersion` 小于 1, 不是整数或缺少: `INVALID_MANIFEST`.
- `categories` 中本版本不认识的分类 id 只要符合模式就接受; 导入向导把它们列为 "不认识的分类", 导入时整体计 `NEWER_FORMAT`.
- `data` 中本版本不认识的数据分类 id 同样接受, 但导入向导不列出 (`SettingsCategoryChoices.import` 只列已知分类), 不导入, 也不计入跳过计数.
- 一个分类文件是否加密由文件本身决定 (有非 null 的 `sealed` 字段, 第 4 节), 不看 `sensitive`. `sensitive` 只用于导入向导第 1 步: `sensitive` 为 true 且本机打不开的分类列为 "已锁定".

## 3. 分类文件 categories/<id>.json

源码: `core/pref/archive/SettingsSnapshot.kt`, `core/pref/archive/SettingsSnapshotCodec.kt`, `core/pref/SettingType.kt`, `core/pref/OtherCategoryFallback.kt`, `core/pref/store/StoreId.kt`, `core/pref/keys/NamedStores.kt`, `core/pref/keys/JsonFileStores.kt`, `core/pref/SettingCategory.kt`.

示例 (`appearance`, 写入方的实际排版):

```json
{
  "id": "appearance",
  "stores": {
    "default": {
      "key_$_keep_screen_on_when_in_foreground": {
        "t": "string",
        "v": "key_$_keep_screen_on_when_in_foreground_disabled"
      },
      "key_$_night_mode": {
        "t": "string",
        "v": "key_$_night_mode_follow_system"
      },
      "key_$_theme_color_primary": {
        "t": "int",
        "v": -16738680
      }
    }
  }
}
```

没有任何存储值的分类仍写出文件:

```json
{
  "id": "editor_appearance",
  "stores": {}
}
```

### 3.1 字段

| 字段 | 类型 | 必填 | 取值 | 读取方校验 |
| --- | --- | --- | --- | --- |
| (根) | 对象 | 是 | | 不是对象: `INVALID_FILE` |
| `id` | 字符串 | 是 | 分类 id | 符合分类 id 模式且等于所请求的 id, 否则 `INVALID_FILE` |
| `stores` | 对象 | 是 | 键为 `StoreId` 名称 | 缺少或不是对象: `INVALID_FILE` |
| `stores.<store>` | 对象 | | 键为存储中的名称 | 不是对象: 该存储整体记为拒绝 (名称为 null), 导入时计 `INVALID_VALUE` 且不改动该存储 |
| `stores.<store>.<name>` | 对象 | | `{ "t": 标签, "v": 值 }`, 先 `t` 后 `v` | 见 3.3; 失败只拒绝该条目 |

整个文件的 JSON 语法错误也是 `INVALID_FILE`.

### 3.2 存储与名称

- `stores` 的键是 `StoreId` 名称: 默认 SharedPreferences 为 `default`, 其他为 `getSharedPreferences` 的文件名, 登记在 `core/pref/keys/NamedStores.kt` (如 `editor_symbols_config`, `build_apk_config_profiles`, `plugin_center_filter_state`). JSON 文件存储全部永久排除 (`core/pref/keys/JsonFileStores.kt`), 不出现在归档中.
- 条目的键是存储中的字面名称, 即键资源的字符串值 (如资源 `key_night_mode` 存为 `key_$_night_mode`), 或声明时给出的字面名称 (如 `THEME_COLOR_DERIVED_ACCENT_AND_DARK`, `key_$_editor_engine_selection_mode`). `docs/settings/inventory.md` 列的是资源名.
- 顺序: `default` 在前, 其余存储按名称排序; 每个存储内按名称排序; 没有值的存储不写出; 分类没有任何值时 `stores` 为 `{}`.
- 写出的值 (`SettingsSnapshot.capture`):
  - 分类中每个已声明的键 (`SettingKey`, `StateKey`) 在其存储中有值的. `CacheKey` 不属于任何分类, 从不导出; `ValueConstant` 与 `Excluded` 不持有值.
  - 分类中每个键族的成员: 存储中名称与键族匹配的全部条目, 例如 `plugin_enable_state` 中以包名结尾的启用状态. 一个命名存储整份导出, 是以空前缀的键族 (`everyName`) 覆盖该存储的全部名称实现的; 命名存储中既没有声明也不属于键族的名称不导出.
  - `other` 分类另含默认存储中注册表不认识的条目 (`OtherCategoryFallback.collect`), 按运行时类型打标签, 以免丢失注册表尚未覆盖的值.
  - 同一分类中同一名称只写一次.
  - 无法打标签的值 (非有限的 Float, 其他类型) 不写出, 记入快照的 `unexportable` (`<store>:<name>`), 由导出完成页与脚本结果计数.
- `manifest.categories[].entries` 等于各存储条目数之和.
- 键迁移 (重命名) 在读取时按 `host.versionCode` 进行, 见 7.7.

### 3.3 类型标签 `t` 与值 `v`

标签按值的运行时类型决定, 不按声明类型: 与声明类型不同的值保留自己的标签, 导入时计 `TYPE_MISMATCH`. 声明为 `Json` 的键, 文本能解析为 JSON 时标 `json`, 否则标 `string` (导入时同样计 `TYPE_MISMATCH`).

| `t` | 运行时类型 (声明类型) | `v` 的写法 | 读取方校验 (失败即拒绝该条目) |
| --- | --- | --- | --- |
| `bool` | Boolean (`Bool`) | `true` / `false` | 是 JSON 布尔 |
| `int` | Int (`Int`) | JSON 整数 | 能转为 Int (32 位, 整数值的小数也接受) |
| `long` | Long (`Long`) | JSON 整数 | 能转为 Long |
| `float` | Float (`Float`) | `Float.toString()` 的有限数 | 任何有限数 |
| `string` | String (`Str`, `Enum`) | JSON 字符串; `Enum` 为存储的候选值 | 是 JSON 字符串 |
| `stringSet` | Set of String (`StrSet`) | JSON 字符串数组, 按字符串排序 | 是数组且元素全是字符串 |
| `json` | 文本为 JSON 的 String (`Json`) | 解析后的 JSON 文档 (对象, 数组或标量), 不是再次字符串化的文本 | `v` 必须存在 (可为 null); 读取后按 `SettingsJson` 重新字符串化 |

- 条目对象中 `t` 与 `v` 以外的字段被忽略 (校验工具则要求恰好这两个字段).
- `t` 不是字符串, 缺少 `v` 或 `v` 与标签不符: 拒绝该条目, 导入时计 `INVALID_VALUE`.
- `t` 是字符串但不在上表: 拒绝该条目并标记为未知标签, 导入时计 `NEWER_FORMAT`.

## 4. 敏感分类的加密信封

源码: `core/pref/archive/SettingsSealedFile.kt`, `core/pref/archive/SettingsDeviceKey.kt`, `core/pref/archive/SettingsPassword.kt`, `core/pref/archive/AndroidSettingsKeys.kt`, `core/pref/archive/SettingsArchiveWriter.kt`.

`SettingCategory.sensitive` 为 true 的分类 (目前只有 `credentials`) 的文件是加密信封, 没有明文形式 (D23 / T21). 写入方缺少设备密钥时在写出任何内容之前拒绝 (`IllegalArgumentException`). 解密所得的文本就是第 3 节的分类文件.

示例 (设了口令时; 写入方的实际排版, 二进制字段为占位符):

```json
{
  "id": "credentials",
  "sealed": {
    "version": 1,
    "cipher": "AES-256-GCM",
    "nonce": "<Base64, 12 字节>",
    "data": "<Base64, 分类文件密文与 16 字节标签>",
    "slots": [
      {
        "kind": "device",
        "kdf": "HKDF-SHA256",
        "fingerprint": "<16 位小写十六进制>",
        "nonce": "<Base64, 12 字节>",
        "key": "<Base64, 48 字节: 包裹的 32 字节内容密钥与 16 字节标签>"
      },
      {
        "kind": "password",
        "kdf": "PBKDF2-HMAC-SHA256",
        "iterations": 600000,
        "salt": "<Base64, 16 字节>",
        "nonce": "<Base64, 12 字节>",
        "key": "<Base64, 48 字节>"
      }
    ]
  }
}
```

### 4.1 字段

二进制字段为标准 Base64 (RFC 4648, 带填充). 下表的失败除注明外都是 `INVALID_FILE`.

| 字段 | 类型 | 必填 | 取值 | 读取方校验 |
| --- | --- | --- | --- | --- |
| `id` | 字符串 | 是 | 分类 id | 等于所请求的 id |
| `sealed` | 对象 | 是 | | 非 null 即视为信封; 不是对象则拒绝 |
| `sealed.version` | 整数 | 是 | 1 | 恰好为 1; 其他版本一律拒绝 |
| `sealed.cipher` | 字符串 | 是 | `AES-256-GCM` | 完全相等 |
| `sealed.nonce` | Base64 | 是 | 12 字节随机数 | 能解码且为 12 字节 |
| `sealed.data` | Base64 | 是 | 密文与 16 字节标签 | 能解码; 解密失败 (少于 16 字节, 标签不符) 拒绝 |
| `sealed.slots` | 数组 | 是 | 设备槽在前, 口令槽在后 | 是数组; 非对象元素与不认识的 `kind` 忽略 |
| 设备槽 `kind` | 字符串 | 是 | `device` | |
| 设备槽 `kdf` | 字符串 | 是 | `HKDF-SHA256` | 不校验 |
| 设备槽 `fingerprint` | 字符串 | 是 | 16 位小写十六进制 | 与本机设备密钥的指纹比较 |
| 设备槽 `nonce` / `key` | Base64 | 是 | 12 字节 / 48 字节 | 解包失败即拒绝, 不再尝试口令槽 |
| 口令槽 `kind` | 字符串 | 是 | `password` | |
| 口令槽 `kdf` | 字符串 | 是 | `PBKDF2-HMAC-SHA256` | 不是该值的口令槽忽略 |
| 口令槽 `iterations` | 整数 | 是 | 写入 600000 | 1000 到 10000000, 超出即拒绝 (不是跳过) |
| 口令槽 `salt` | Base64 | 是 | 写入 16 字节随机数 | 16 到 64 字节, 超出即拒绝 |
| 口令槽 `nonce` / `key` | Base64 | 是 | 12 字节 / 48 字节 | 解包失败尝试下一个口令槽 |

### 4.2 密钥与加密

- 内容密钥: 每个文件随机 32 字节. `data` = AES-256-GCM (`AES/GCM/NoPadding`, 128 位标签) 以内容密钥, `sealed.nonce` 与附加数据 `autojs6-settings:<id>` (UTF-8) 加密分类文件文本的 UTF-8 字节.
- 槽: `key` = AES-256-GCM 以槽密钥, 槽的 `nonce` 与附加数据 `autojs6-settings-slot:<kind>:<id>` 加密内容密钥, 共 48 字节.
- 设备密钥 (`SettingsDeviceKey`): 从不存储, 每次由 `Settings.Secure.ANDROID_ID` 派生. HKDF-SHA256 (RFC 5869), 输入密钥材料为 `ANDROID_ID` 去首尾空白并按 `Locale.ROOT` 转小写后的 UTF-8, 盐为 `org.autojs.autojs6/settings-archive/device-key/v1`, info 为 `credentials`, 输出 32 字节. `ANDROID_ID` 为空时取设备密钥失败 (`IOException`). Android 8 起 `ANDROID_ID` 随签名密钥与用户而不同, 因此 debug 包与 release 包, 不同用户之间互相打不开设备槽.
- 指纹: HMAC-SHA256 (设备密钥, `fingerprint`) 的前 8 字节, 写作 16 位小写十六进制.
- 口令 (`SettingsPassword`, P7.7): 文本按 NFC 规范化后取 UTF-8; 空口令视为没有口令, 不写口令槽. 槽密钥 = PBKDF2-HMAC-SHA256 (RFC 8018), 写入时迭代 600000 次 (OWASP 2023, 附录 C Q30), 盐 16 字节, 输出 32 字节.

### 4.3 读取方打开信封的顺序

1. 依次校验 4.1 中的 `id`, `sealed`, `version`, `cipher`, `nonce`, `data`, `slots`.
2. 有设备密钥时, 取第一个 `kind` 为 `device` 且指纹相符的槽解包内容密钥; 解包失败为 `INVALID_FILE`.
3. 没有相符的设备槽且给出了口令时, 依次尝试 `kdf` 为 `PBKDF2-HMAC-SHA256` 的口令槽; 迭代次数或盐长度超出范围为 `INVALID_FILE`; 全部解包失败为 `LOCKED` (口令错误与口令槽被改动无法区分).
4. 没有可用的槽为 `LOCKED`: 需要口令, 本机没有设备密钥, 或文件为其他设备 (其他签名, 其他用户) 加密.
5. 内容密钥不是 32 字节, 或 `data` 解密失败, 为 `INVALID_FILE`.
6. 明文按第 3 节解析, `id` 不符为 `INVALID_FILE`.

导入向导先用 `SettingsArchiveReader.access` 判断每个加密分类: `PLAIN` (未加密), `DEVICE` (设备槽可用), `PASSWORD` (只有口令槽可用), `LOCKED`. 打不开的分类不可勾选; 有口令槽的分类在用户输入口令并经 `checkPassword` 验证后可勾选.

## 5. 数据分类

源码: `core/pref/data/SettingsDataCapture.kt`, `core/pref/data/ShortcutDataCodec.kt`, `core/pref/data/ColorLibraryDataCodec.kt`, `core/pref/data/SettingsDataEntries.kt`, `core/pref/data/SettingsDataImport.kt`, `external/shortcut/ShortcutBackupArchive.kt`, `theme/library/ColorLibraryJson.kt`.

数据分类 (`SettingCategory` 中类型为 DATA 的分类) 的内容不是键值, 而是各自页面的导出文件, 列在 `manifest.data` 而不是 `manifest.categories`.

| id | 文件 | 内容 | `entries` |
| --- | --- | --- | --- |
| `data_shortcuts` | `data/shortcuts.zip` | 快捷方式备份, 与 "快捷方式" 页导出的文件同一写法 (`ShortcutBackupArchive.writeExport`) | 快捷方式个数 |
| `data_color_library` | `data/color-library.json` | 颜色库文档 (`ColorLibraryJson.encode`) | 颜色库个数 |

- 条目数为 0 的数据分类不写出 (导出向导, 自动备份与脚本都如此; 脚本在结果中把它记为 `empty`). 因此 `manifest.data` 中的每一项都有对应文件且条目数大于 0.
- 写入时读取失败: 导出向导中所选数据分类读不出为 `IOException`, 导出失败; 自动备份中跳过该分类并记录日志.
- 细化 (P10.2): 导出与导入向导的细化页可只选部分条目, 快捷方式按 id, 颜色库按其在文件中的序号; 所写文件只含选中的条目, 以归档的导出时间重新写出.
- 读取: 数据文件的路径取自 `manifest.data[].file` (读取方只要求 `data/` 前缀与名称模式, 不要求是上表的固定文件名), 编解码按 `id` 选择. 解码失败为 `INVALID_FILE`.

### 5.1 data/shortcuts.zip

- 内层 zip 含 `manifest.json` (Gson 紧凑格式) 与 `icons/<序号>.png`. `manifest.json` 为 `{ "format": "autojs6-shortcuts", "version": 2, "markerToken", "launcher", "activity", "shortcuts": [...] }`, 每个快捷方式含 `id`, `shortLabel`, `longLabel`, `target`, `path`, `sourceKind`, `projectRoot`, `icon` (`icons/<i>.png` 或 null), `status`, `notified`, `requestToken`, `iconNoticeShown`; 导出时清空 `requestToken`, `notified` 与 `iconNoticeShown`.
- 内层条目时间取归档的导出时间, 因此同一状态, 同一导出时间写出的字节与页面导出完全相同.
- 读取方接受 `version` 1 与 2; 条目名受限, 重复条目拒绝; 图标须为 PNG (24 字节到 1 MiB), 不超过 512 x 512; 条目最多 1000 个 (`MAX_ENTRIES`), `manifest.json` 最多 4 MiB, 合计最多 64 MiB (外层归档另受 16 MiB 限制).

### 5.2 data/color-library.json

- `format` 为 `autojs6.color-library`, `schemaVersion` 为 1; 只含用户颜色库 (普通与智能), 不含预设; `app` 为宿主的 `versionName` / `versionCode`. 由 Gson 以缩进格式写出, 不做 HTML 转义.
- 读取时最多 2 MiB, 去掉 BOM; 颜色库 1 到 256 个, 每个最多 4096 项. 失败原因 (`TOO_LARGE`, `MALFORMED`, `NOT_A_COLOR_LIBRARY`, `NEWER_SCHEMA`, `NO_LIBRARIES`, `TOO_MANY_LIBRARIES`, `TOO_MANY_ITEMS`) 在设置归档中统一为 `INVALID_FILE`.

### 5.3 导入

数据分类不走第 7 节的键级规则: 快捷方式经 `ShortcutBackupRepository.import` 合并, 有冲突时该分类失败, 原有备份不变; 颜色库作为新的用户颜色库加入 (`ColorLibraryExchange.importLibrary`, 智能分组获得新 id). 某个数据分类失败不影响其他分类. "撤销上次导入" 删除导入新增的条目 (7.8).

## 6. 插件目录

源码: `core/pref/PluginSettingsProvider.kt`, `core/pref/archive/SettingsArchive.kt`, `core/pref/archive/SettingsArchiveWriter.kt`, `core/pref/archive/SettingsImportPlanner.kt`.

- 每个官方插件一个文件 `plugins/<packageName>/settings.json`, 内容是插件 `PluginSettingsProvider.exportSettings` 给出的 JSON 文档 (第二期契约草案 `docs/dev/plugin-settings-transfer-protocol-v1.md`, P13.2), 宿主原样保存, 不解析. `manifest.plugins` 每项为 `{ "id": "<包名>", "entries": N }` (2.1).
- 写入方: 同一插件给出两次, 包名不符合模式或条目数为负时拒绝 (`IllegalArgumentException`).
- 读取方: `SettingsArchiveReader.plugin(packageName)` 返回该文件的文本; 缺少为 `MISSING_FILE`. 校验工具另要求它是 JSON 对象.
- 第一期: `SettingsRegistry.pluginSettingsProviders` 为空, 没有写入方会写出插件目录; 导入时每个插件目录都计 `TARGET_MISSING` (7.4).
- 插件目录与宿主自己关于插件的设置 (`plugin_enable_state`, `plugin_center` 等分类) 无关; 后者是普通分类文件.

## 7. 导入规则

源码: `ui/settings/transfer/SettingsImporter.kt`, `core/pref/archive/SettingsImportPlanner.kt`, `core/pref/archive/ImportPlan.kt`, `core/pref/archive/SettingsImportTargets.kt`, `core/pref/archive/SettingsImportApplier.kt`, `core/pref/archive/SettingsKeyMigrations.kt`, `core/pref/archive/SettingsBackupVault.kt`, `core/pref/DeploymentKind.kt`, `core/pref/deploy/DeploymentPlanner.kt`, `core/pref/deploy/DeploymentItems.kt`.

### 7.1 流程

1. 复制所选文档到缓存 (1.3), 读 `manifest.json`, 求出警告 (2.2), 已锁定的加密分类 (4.3) 与本版本不认识的分类.
2. 用户勾选. 计划包含: 勾选的可选设置分类 (去重, 按页面顺序) 与全部不认识的分类; 数据分类另行导入 (第 5 节); 再加 `manifest.plugins` 中的插件目录.
3. 读取所需分类文件并做键迁移 (7.7), 生成计划 (`SettingsImportPlanner.plan`); 有部署项时请用户确认 (7.5).
4. 把全部设置分类 (含 `credentials` 与 `other`) 与要导入的数据分类备份到内部备份库 (7.8); 写入计划: 每个存储一次 `commit()`, 先删除后写入; 导入数据分类; 记录本次操作; 执行生效表 (`SettingsEffects`); 运行用户确认的部署项; 写操作后自动备份.

### 7.2 逐条规则

对已知分类中的每个条目, 按以下顺序判定, 命中即止:

1. 找归属: 先找该存储与名称上声明的键, 否则找该存储中前缀最长的匹配键族. 没有归属, 或归属于其他分类, 或存储不认识: `UNKNOWN_KEY`.
2. 标签与声明类型的标签不同: `TYPE_MISMATCH`, 附注写明两者.
3. 值经该键的解码与再编码失败 (`Enum` 不在候选项, `Json` 被该键的编解码拒绝等): `INVALID_VALUE`.
4. 该键指向的插件未安装 (`plugin_enable_state` 中以包名结尾的启用 / 暂停键族, dex 编译器与 R8 的组件键对, 包名取自组件值): `TARGET_MISSING`, 附注为包名.
5. 该键带部署项, 计划需要部署项, 且其值在本机尚未就绪: 进入部署项 (7.5).
6. 其余: 写入 (以存储形式).

另外:

- 分类文件中被拒绝的条目 (3.3): 未知标签计 `NEWER_FORMAT`, 其余计 `INVALID_VALUE`. 无法读取的存储 (不是对象) 计 `INVALID_VALUE` 且整个存储不改动.
- 不认识的分类 (更新版本): 其全部条目与被拒绝条目计 `NEWER_FORMAT`, 不写入, 不删除.
- 删除 (恢复默认): 该分类中已声明的键与归属该分类的键族成员, 本机存储中有值而归档中没有的, 删除. 归档中出现过的名称 (无论是否写入) 不删除; 无法读取的存储不删除.
- `other`: 只写入注册表中归属 `other` 的名称 (`StateKey` 与 `other` 的键族). 导出时由运行时兜底 (3.2) 写出的未登记名称, 导入时计 `UNKNOWN_KEY`.
- 勾选了却缺少分类文件: 读取时为 `MISSING_FILE`.

### 7.3 写入

`SettingsImportApplier` 按存储分组, 每个存储一次 `commit()`, 先删除后写入, 返回实际变化的键, 再由 `SettingsEffects` 执行需要立即生效的副作用 (如主题色, 夜间模式, 启动图标). 部署项的键在部署成功后单独写入.

### 7.4 跳过原因 SkipReason

按 `ImportPlan.SkipReason` 的声明顺序. 计数 (`skipCounts`) 包含计数为 0 的原因.

| 原因 | 含义 |
| --- | --- |
| `UNKNOWN_KEY` | 本版本注册表中该分类没有这个键: 未声明, 声明在其他分类, 或存储不认识 |
| `TYPE_MISMATCH` | 类型标签与声明类型不符 |
| `INVALID_VALUE` | 值无效: `Enum` 不在候选项, `Json` 被编解码拒绝, 条目或存储格式错误 |
| `TARGET_MISSING` | 目标不在本机: 键指向的插件未安装; 插件目录对应的插件未安装 (附注 `not installed`) 或本机没有其设置提供方 (附注 `no settings provider`) |
| `NEWER_FORMAT` | 来自更新的格式: 本版本不认识的分类, 或不认识的类型标签 |
| `DECLINED` | 用户放弃了该键的部署项, 保持当前值 |

插件目录的跳过项以 `plugins/` 为分类, 包名为存储, 名称为 null.

### 7.5 部署项

| `DeploymentKind` | 键 | 本机已就绪的判断 | 前置条件 |
| --- | --- | --- | --- |
| `CREATE_DIRECTORY` | `ExplorerKeys.WORKING_DIRECTORY` (工作目录) | 该目录已存在 | 无 (运行时需要存储权限) |
| `DOWNLOAD_EDITOR_FONT` | `EditorKeys.ACE_EDITOR_FONT` (Ace 编辑器字体) | 该字体已安装 | 未安装 Ace 编辑器插件时需要 `ACE_EDITOR_PLUGIN` |

用户在第 3 步确认的部署项依次运行, 成功后写入其键; 某项失败不影响其他项; 用户放弃的项计 `DECLINED`, 保持当前值; 用户取消时尚未开始的项放弃. "撤销上次导入" 生成的计划不含部署项, 直接写入.

### 7.6 不导入的内容

- 本机打不开的加密分类 (4.3) 不可勾选.
- 未勾选的分类不改动. 数据分类按各自规则合并或新增 (5.3), 不按键级规则删除本机已有的条目.

### 7.7 键迁移

`SettingsKeyMigrations.Rename(store, from, to, sinceVersionCode)`: 读取方在 `categories()` 中对每个分类文件, 按 `sinceVersionCode` 升序应用 `manifest.host.versionCode` 小于 `sinceVersionCode` 的规则. `to` 已存在时保留 `to` 并丢弃 `from`, 否则把 `from` 原位改名为 `to`. v1 的迁移表 `RENAMES` 为空.

### 7.8 内部备份与撤销

- 每次导入或重置之前, 把全部设置分类 (含 `credentials`, 以设备密钥加密, 与 `other`) 及要导入的数据分类写入 `<filesDir>/settings-backups/auto-import-*.zip` 或 `auto-reset-*.zip` (1.1), 两种合计保留最新 5 个.
- 本机取不到设备密钥 (`ANDROID_ID` 为空) 时, 内部备份略去敏感分类 (`credentials`, 清单中也不列出), 其余照常写入, 导入与恢复默认照常进行; 从不以明文写凭据. 本次操作触及 `credentials` 时, 结果页提示内部备份未包含凭据, 撤销不会恢复凭据 (撤销只导入备份中有的分类, 见下). 附录 C Q39 已决 (2026-10-22).
- 本次操作记录在同目录的 `last-operation.json` (缩进 2): `operation` (`import` 或 `reset`), `time` (毫秒), `backup` (备份文件名), `categories` (触及的分类), `data` (触及的数据分类, 可省略), `added` (对象: 数据分类 id 到该次新增条目 id 的字符串数组, 只能是 `data` 中的分类; P10.3 起才有). 任一字段不合规时整份记录视为不存在, "撤销上次导入" 不可用.
- "撤销上次导入" 用该备份再次导入触及的分类 (备份中没有的分类不计入; 不含部署项, 本身不再写内部备份), 删除 `added` 记录的数据条目, 然后清除记录. 备份已不在为 `MISSING_FILE`; P10.3 之前的记录没有 `added`, 数据分类无法撤销, 结果页提示.

## 8. 读取方错误码

源码: `core/pref/archive/SettingsArchive.kt` (`SettingsArchiveException.Reason`), `core/pref/archive/SettingsArchiveReader.kt`, `core/pref/archive/SettingsArchiveManifest.kt`, `core/pref/archive/SettingsSealedFile.kt`.

| 错误码 | 含义 | 触发 |
| --- | --- | --- |
| `TOO_LARGE` | 超过 16 MiB | 归档本身 (读到目标条目为止的原始字节, 或复制到缓存时), 或其中一个文件解压后; 写入时压缩后的输出 |
| `NOT_AN_ARCHIVE` | 不是归档 | 不是 zip, 没有任何条目, 截断, `ZipException` |
| `INVALID_MANIFEST` | 清单无效 | `manifest.json` 语法错误, 或 2.1 的任一校验失败 |
| `MISSING_FILE` | 缺少文件 | 没有 `manifest.json`; 缺少清单所列的分类, 数据或插件文件; 撤销时内部备份已不在 |
| `INVALID_FILE` | 文件无效 | 分类文件语法错误, 根或 `stores` 格式错误, `id` 与请求的分类不符; 信封格式错误, 版本不是 1, 被改动 (设备槽或数据解密失败), 口令槽参数超出范围; 数据文件解码失败 |
| `LOCKED` | 打不开 | 加密分类没有本机设备密钥可用的槽, 也没有给出的口令能打开的口令槽 |

不属于错误码, 但读取时会给出:

- manifest 警告 `SettingsArchiveManifest.Warning`: `NEWER_FORMAT` (`formatVersion` 大于 1), `OTHER_HOST_PACKAGE` (导出宿主的包名与本机不同). 只提示, 不拒绝.
- 导入计划的 `SkipReason` (7.4).
- 脚本 `settings.export` 的跳过原因: `sensitive` (请求了 `credentials`), `empty` (数据分类没有条目); 参数错误 (未知分类 id, 空数组, 只请求了 `credentials`) 为 `IllegalArgumentException`.

## 9. 校验工具 archive_inspect.py

源码: `tools/settings/archive_inspect.py`.

用法:

```
py -3.12 -I tools/settings/archive_inspect.py ARCHIVE [--expect-categories N] [--expect-ids ID,ID,...]
py -3.12 -I tools/settings/archive_inspect.py ARCHIVE --set-format-version N --out OUT
```

- 输出: 归档字节数, manifest 摘要 (格式, 版本, 导出时间, 宿主, 设备), 每个分类一行 (清单所列条目数, 文件中的条目数与存储数; 加密分类只显示清单所列的数), 每个问题一行 `FAIL ...`, 最后一行 `structure: PASS` 或 `structure: FAIL (n problems)`. 从不打印值 (归档可能含凭据, R6).
- 退出码: 有任何问题为 1, 否则为 0.
- `--expect-categories N` / `--expect-ids ID,ID,...`: 要求清单的 `categories` 恰好 N 项 / 恰好是这些 id 且顺序相同.
- `--set-format-version N --out OUT`: 把归档复制到 OUT, 只把 `manifest.json` 的 `formatVersion` 换成 N (用 `json.dumps` 以 `ensure_ascii=False`, 缩进 2 重写), 其余条目原样, 用于附录 D 第 3 项 (更高版本的警告). 必须同时给出 `--out`.

检查项:

- 归档: 文件不超过 16 MiB; 是 zip 且第一个条目是 `manifest.json`; 每个条目声明的解压大小不超过 16 MiB.
- manifest: `format`; `formatVersion` 为整数 (不接受布尔) 且 >= 1; `exportedAt` 为字符串; `host.packageName` / `host.versionName` 为字符串, `host.versionCode` 为整数; `device.model` 为字符串, `device.sdk` 为整数; `categories` 为数组, 每项为对象, `id` 为字符串且不重复; `sensitive` 为布尔, 且只有 `credentials` 为 true.
- 分类文件: 存在; 未加密的文件 `id` 相符, `stores` 为对象, 每个存储为对象, 每个条目恰好有 `t` 与 `v`, 标签已知, 值与标签相符 (`int` 在 32 位范围, `long` 在 64 位范围, `float` 接受整数与小数, `json` 接受任何值), 清单所列条目数等于文件中的条目数; 加密文件恰好有 `id` 与 `sealed`, 符合 4.1 (版本 1, `AES-256-GCM`, 12 字节 nonce, `data` 至少 16 字节, `slots` 非空且至少有一个设备槽: `HKDF-SHA256`, 16 位小写十六进制指纹, 12 字节 nonce, 48 字节 `key`; 口令槽: `PBKDF2-HMAC-SHA256`, 迭代 1000 到 10000000, 盐 16 到 64 字节, 12 字节 nonce, 48 字节 `key`). 敏感分类未加密, 或非敏感分类被加密, 都是问题. 不解密, 因此不核对加密分类的条目数.
- 数据: 每项有以 `data/` 开头的 `file` 且文件存在; 已知 id 必须用固定文件名 (`data/shortcuts.zip`, `data/color-library.json`), 并按页面的读法检查内容: 快捷方式 (`autojs6-shortcuts` v1 / v2, 只含 `manifest.json` 与 `icons/<n>.png`, 5.1 的上限, `target` 与 `status` 在已知集合内, 每个声明的图标恰好出现一次且是 PNG), 颜色库 (5.2 的上限, 普通颜色为 `#RRGGBB` 或 `#AARRGGBB`); 清单所列条目数等于文件中的条目数. 其他 id 只检查文件存在.
- 插件: `plugins` 为数组, `id` 为包名且不重复, `entries` 为非负整数 (不接受布尔), `plugins/<id>/settings.json` 存在且是 JSON 对象.
- 多余文件: 除 `plugins/` 下的目录条目外, 清单未列出的文件都是问题.

与读取方的差别 (校验工具更严或更松之处):

| 项 | 读取方 | 校验工具 |
| --- | --- | --- |
| `manifest.json` 的位置 | 任意 | 必须是第一个条目 |
| 多余文件 | 忽略 | 问题 |
| 重复条目名 | 取第一个 | 不检查 (Python `zipfile` 读到的是最后一个) |
| JSON 重复键, NaN / Infinity | 拒绝 | 不检查 (`json.loads` 接受) |
| `host.packageName` 模式, 分类 id 模式 | 检查 | 不检查 |
| `data` 的 `id` / `file` 重复 | 拒绝 | 不检查 |
| `sensitive` 与分类的对应 | 不核对 | 只有 `credentials` 为 true, 且与是否加密一致 |
| 清单条目数与文件 | 不核对 | 必须相等 (加密分类除外) |
| 条目对象的额外字段 | 忽略 | 问题 |
| 数据文件名 | 只要求 `data/` 前缀与模式 | 已知 id 必须用固定文件名 |
| 信封 | 解密并校验 | 只检查结构与长度, 不解密 |

## 附录. 与 ROADMAP 附录 B 草案的差别

附录 B 是立项时的草案, 下列各处以本文为准:

1. 分类文件的键是存储中的字面名称 (`key_$_night_mode` 等), 不是资源名; 存储与名称按 3.2 排序, 对象逐行展开.
2. `stores` 的键一律是 `StoreId` 名称; 命名存储的 "整份导出" 是空前缀键族的效果, 未声明也不属于键族的名称不导出 (3.2).
3. 读取方错误码共 6 个 (第 8 节); `NEWER_FORMAT` 是 manifest 警告与 `SkipReason`, 不是错误码.
4. 信封的 `version` 必须恰好为 1; 设备槽不校验 `kdf`, 指纹相符而解包失败时直接 `INVALID_FILE`; 口令槽参数超出范围为 `INVALID_FILE` (4.1, 4.3).
5. manifest 的 `data` / `plugins` 可省略; 另有包名模式, `data` 的 `id` / `file` 不重复与数据文件名模式的校验; `exportedAt` 格式, `sensitive` 与 `entries` 不核对 (2.1).
6. 条目数为 0 的数据分类不写出; `data/shortcuts.zip` 只在状态与导出时间都相同时与页面导出字节一致 (第 5 节).
7. 写入除 "存在且类型匹配" 外还要求值有效, 插件目标存在, 部署项就绪; 归档中出现过的名称不删除, 不是对象的存储不改动 (7.2).
8. `UNKNOWN_KEY`, `INVALID_VALUE` 与 `NEWER_FORMAT` 的范围按 7.4; `TARGET_MISSING` 也用于宿主中指向未安装插件的键.
9. `other` 只写入归属 `other` 的 `StateKey` 与键族; 兜底导出的未登记名称导入时计 `UNKNOWN_KEY` (7.2).
10. 附录 B 未写的: 按 `host.versionCode` 的键迁移 (7.7), 各写入方的文件名与内容范围 (1.1), 读取方取第一个重名条目并忽略多余文件 (1.4), 缺少设备密钥时内部备份失败 (7.8, Q39).
