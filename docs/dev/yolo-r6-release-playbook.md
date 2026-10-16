# YOLO R6 发布、恢复与后续升级操作手册

本文只固定首发所需的操作顺序和证据边界。它不包含签名秘密，也不把源码、静态 ELF、
TEST-SIGNED APK 或单台设备结果写成完整生产发布结论。

## 1. 固定身份与首发范围

- Host application ID：`org.autojs.autojs6`。
- Provider application ID：`io.github.supermonster003.autojs6.plugin.yolo.ncnn`。
- Provider service：
  `io.github.supermonster003.autojs6.plugin.yolo.ncnn.provider.YoloProviderService`。
- Host 和 Provider 必须由同一个经发布负责人确认的生产证书签发。受控 producer 会读取
  授权的 `sign.properties` 与 JKS，但不打印、记录或通过命令行传递 alias/password，并在
  finally 中删除临时副本；publishable/recovery consumer 只接收证书 SHA-256 指纹和
  producer receipt，不读取签名秘密。
- 首发 Provider 仅声明 `arm64-v8a / NCNN / CPU / YOLO11 detect / manifest schema v1 /
  ultralytics-detect`。Host 与 Provider APK 均不得内置模型；Vulkan、其他 ABI、decoder
  或任务不在首发声明中。
- Provider 运行时对非官方签名保持 default-off。安装或升级不得自动授权、改选其他 provider，也不得在
  dispatch 后自动重试。宿主同证书 (OFFICIAL) provider 没有启用记录时视为启用（2026-10-16 维护者决定），
  插件中心对其全新安装还会写入显式启用记录（2026-09-18 产品决定）；其他签名仍需手动启用。

## 2. 发布证据分层

1. `tools/yolo/verify-yolo-r6-publishable.ps1 -SourceOnly` 只产生
   `SOURCE_STATIC_PASS / PRODUCTION_NOT_RUN`。它不检查 APK。
2. 同一脚本的 artifact 参数集必须显式接收 Host APK、Provider APK、两仓 full commit、
   Provider source-build receipt、发布负责人冻结的 `ExpectedProviderVersionCode` 和确认的
   `OfficialProductionSignerSha256`。它要求两仓 clean，并校验 non-debuggable、相同且匹配的
   signer、APK SHA-256、Provider arm64-only/严格 release-entry allowlist、NCNN
   provenance/NOTICE、MPL-2.0 与 Kotlin runtime Apache-2.0 完整正文，以及静态 ELF 包装。Host 只声明已知 YOLO/NCNN payload absent，不能由
   denylist 推导通用 model-free。unsigned source receipt 或可编辑 JSON 不能证明最终 signed
   APK 的 build identity。`produce-yolo-r6-signed-artifacts.ps1` 在一个受控进程内绑定 clean
   source、构建调用、最终 signed APK 与后置的完整 unsigned Provider admission；consumer
   会重新计算 signer、hash、版本与 APK VCS metadata。该 receipt 明确不是 cryptographic
   attestation，artifact 预检通过后仍只代表 `PASS_CONTROLLED_SIGNED_BUILD / PRODUCTION_NOT_COMPLETE`。
3. `tools/yolo/verify-yolo-r6-recovery.ps1` 每次只绑定一个显式 serial，执行真实 Provider
   冷/热推理、`PROVIDER_KILL_EXPLICIT_REOPEN`，并确认死亡后的旧 session fail-closed、
   没有自动重试。代表性 arm64 实体机与当前 API arm64 环境分别保存 receipt；当前主机
   无法运行 arm64 AVD，产品负责人允许 API 35+ arm64 实体机作为当前 API 环境。
4. 只有索引、兼容范围、两类 Android 环境和最终发布对象全部绑定到相同 APK
   SHA-256 后，才可人工汇总完整 `PRODUCTION` receipt。

## 3. 首次安装与升级

- 首次安装顺序：Host → Provider → 与 Host 同生产 signer 的 test-only instrumentation
  （仅验收环境）。Host 定义 signature permission，因此必须先安装。
- 插件索引必须固定 package、exact component、API version 范围、min/max Host version、
  ABI、backend 和两份 APK SHA-256；不得只有 engine 名称或隐式 fallback。
- 安装 Provider 后仍保持 disabled/ungranted。验收脚本只在测试期间保存并临时设置授权与
  enable 状态，结束后恢复；用户生产授权必须是显式操作。
- Provider 首发已冻结为 `0.1.0 / versionCode=2`，最小 Host 为正式版 `6.8.0 / 5275`。
  产品负责人明确决定不制作或保留 versionCode 1 predecessor，因此本轮
  `upgradeRuntimeEvidence` 和 `versionRollbackEvidence` 均为
  `NOT_RUN_BY_PRODUCT_DECISION`，不得虚构升级或版本回滚证据。
- 正常升级仅接受相同 package、相同生产 signer、versionCode 严格增加且兼容范围明确的
  APK。升级前保存旧 APK、版本、证书指纹、SHA-256 和索引项；升级后重跑冷/热推理与
  进程死亡恢复。
- signer、package、component、协议或能力发生非计划漂移时立即停止，不尝试换 provider
  或复用旧 session。

## 4. 首发恢复与撤回

由于不存在 predecessor artifact，首发不声明版本回滚。故障处置固定为：

1. 停止脚本并关闭现有 detector/session；记录失败版本和 APK SHA-256。
2. 撤销/禁用 Provider、撤回索引项；必要时卸载 Provider。
3. 若仅需恢复同一首发二进制，可按 `ARCHIVED_EXACT_V2_REINSTALL_ONLY` 从已验证归档重装
   exact `versionCode=2`，并核对 package、
   exact component、生产 signer 和归档 SHA-256；这是 same-version recovery，不是 rollback。
4. 恢复后 OFFICIAL 包没有启用记录时视为启用，插件中心重装的 OFFICIAL 包会自动写入启用记录；
   其他签名由操作者重新显式授权/启用；随后再跑一次冷推理和 `PROVIDER_KILL_EXPLICIT_REOPEN`。
5. 若 v2 本身存在缺陷，则发布 forward-fix `versionCode=3`；不得把重装 v2 描述为版本回滚。

首发明确**不得原位降级**，也没有可降级的 code 1 artifact。卸载会删除 Provider 私有目录
和 session；模型仍由 Host 通过 PFD 重新提供。未来首次具备 predecessor 时，再单独设计并
验证升级/回滚协议。

## 5. 停止条件

以下任一情况必须 fail-closed 并保留相应 `NOT_RUN/FAIL`，不得签发完整生产 receipt：

- 使用已知 R5 TEST-SIGNED certificate、debuggable APK 或未显式提供生产 signer 指纹；
- Host/Provider signer 不同，Provider 非 arm64-only，或任一生产 APK含 YOLO 模型；
- provenance、MPL-2.0/Kotlin Apache-2.0/NCNN notice、APK hash、索引 hash 或 release note 互不一致；
- 真实 Provider 死亡后旧 session 可继续 dispatch、发生自动重试，或显式 reopen 失败；
- 缺少代表性 arm64 物理设备或当前 API 环境证据；
- 生产凭据、外部索引、发布目的地或最终版本尚未由负责人明确授权。
