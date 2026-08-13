# YOLO R6 发布、升级与回滚操作手册

本文只固定首发所需的操作顺序和证据边界。它不包含签名秘密，也不把源码、静态 ELF、
TEST-SIGNED APK 或单台设备结果写成完整生产发布结论。

## 1. 固定身份与首发范围

- Host application ID：`org.autojs.autojs6`。
- Provider application ID：`io.github.supermonster003.autojs6.plugin.yolo.ncnn`。
- Provider service：
  `io.github.supermonster003.autojs6.plugin.yolo.ncnn.provider.YoloProviderService`。
- Host 和 Provider 必须由同一个经发布负责人确认的生产证书签发；gate 只接收证书的
  SHA-256 公钥指纹，不读取 keystore、alias 或密码。
- 首发 Provider 仅声明 `arm64-v8a / NCNN / CPU / YOLO11 detect / manifest schema v1 /
  ultralytics-detect`。Host 与 Provider APK 均不得内置模型；Vulkan、其他 ABI、decoder
  或任务不在首发声明中。
- Provider 始终 default-off。安装或升级不得自动授权、自动启用、改选其他 provider，
  也不得在 dispatch 后自动重试。

## 2. 发布证据分层

1. `tools/yolo/verify-yolo-r6-publishable.ps1 -SourceOnly` 只产生
   `SOURCE_STATIC_PASS / PRODUCTION_NOT_RUN`。它不检查 APK。
2. 同一脚本的 artifact 参数集必须显式接收 Host APK、Provider APK、两仓 full commit、
   Provider source-build receipt、发布负责人冻结的 `ExpectedProviderVersionCode` 和确认的
   `OfficialProductionSignerSha256`。它要求两仓 clean，并校验 non-debuggable、相同且匹配的
   signer、APK SHA-256、Provider arm64-only/严格 release-entry allowlist、NCNN
   provenance/NOTICE 和静态 ELF 包装。Host 只声明已知 YOLO/NCNN payload absent，不能由
   denylist 推导通用 model-free。unsigned source receipt 或可编辑 JSON 不能证明最终 signed
   APK 的 build identity，所以当前结果固定为
   `PARTIAL_NO_CONTROLLED_SIGNED_BUILD_RECEIPT / PRODUCTION_NOT_RUN`。
3. `tools/yolo/verify-yolo-r6-recovery.ps1` 每次只绑定一个显式 serial，执行真实 Provider
   冷/热推理、`PROVIDER_KILL_EXPLICIT_REOPEN`，并确认死亡后的旧 session fail-closed、
   没有自动重试。arm64 物理设备与 API 36 arm64 emulator 分别保存 receipt，不互相替代。
4. 只有索引、兼容范围、升级/回滚、两类 Android 环境和最终发布对象全部绑定到相同 APK
   SHA-256 后，才可人工汇总完整 `PRODUCTION` receipt。

## 3. 首次安装与升级

- 首次安装顺序：Host → Provider → 与 Host 同生产 signer 的 test-only instrumentation
  （仅验收环境）。Host 定义 signature permission，因此必须先安装。
- 插件索引必须固定 package、exact component、API version 范围、min/max Host version、
  ABI、backend 和两份 APK SHA-256；不得只有 engine 名称或隐式 fallback。
- 安装 Provider 后仍保持 disabled/ungranted。验收脚本只在测试期间保存并临时设置授权与
  enable 状态，结束后恢复；用户生产授权必须是显式操作。
- 当前 Provider 源码为 `versionCode=1`，最终发布 code 仍须由负责人冻结并与 source/APK
  一致。若本轮要实测升级，production candidate 推荐 code 2 或更高，并以同 signer 的本地
  code 1 artifact 作为 predecessor；若首发仍冻结 code 1，则升级证据必须保持 `NOT_RUN`，
  不能签发完整 R6 receipt。
- 正常升级仅接受相同 package、相同生产 signer、versionCode 严格增加且兼容范围明确的
  APK。升级前保存旧 APK、版本、证书指纹、SHA-256 和索引项；升级后重跑冷/热推理与
  进程死亡恢复。
- signer、package、component、协议或能力发生非计划漂移时立即停止，不尝试换 provider
  或复用旧 session。

## 4. 首发回滚

首发固定回滚策略为 `UNINSTALL_REINSTALL_CODE1`：

1. 停止脚本并关闭现有 detector/session；记录失败版本和 APK SHA-256。
2. 撤销/禁用 Provider，再卸载失败 Provider。
3. 从已验证归档重装首发 `versionCode=1` Provider，核对 package、exact component、生产
   signer 和归档 SHA-256。
4. 回滚后仍保持 default-off；由操作者重新显式授权/启用，再跑一次冷推理和
   `PROVIDER_KILL_EXPLICIT_REOPEN`。
5. 索引恢复到与归档 APK SHA-256 完全一致的旧项后才结束回滚。

首发明确**不得原位降级**：不依赖 `adb install -d`、系统保留数据降级或跨 versionCode
覆盖。卸载会删除 Provider 私有目录和 session；模型仍由 Host 通过 PFD 重新提供。若未来
需要保留 Provider 数据的原位回滚，必须单独设计迁移协议和破坏性测试，不能沿用本手册。

## 5. 停止条件

以下任一情况必须 fail-closed 并保留相应 `NOT_RUN/FAIL`，不得签发完整生产 receipt：

- 使用已知 R5 TEST-SIGNED certificate、debuggable APK 或未显式提供生产 signer 指纹；
- Host/Provider signer 不同，Provider 非 arm64-only，或任一生产 APK含 YOLO 模型；
- provenance、MPL-2.0/NCNN notice、APK hash、索引 hash 或 release note 互不一致；
- 真实 Provider 死亡后旧 session 可继续 dispatch、发生自动重试，或显式 reopen 失败；
- 缺少代表性 arm64 物理设备或当前 API 环境证据；
- 生产凭据、外部索引、发布目的地或最终版本尚未由负责人明确授权。
