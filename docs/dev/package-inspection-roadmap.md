# 安装包检查与文件操作路线图

更新日期: 2026-10-02.

## 大型安装包

- [x] 普通 APK 的基础信息通过 ZIP 根清单直接读取, 不再依赖全包资源条目枚举.
- [x] 容器检查, 嵌套 APK 清单, AAB 清单扫描与 bundletool toc.pb 使用一致的 `PackageInspectionLimits`.
- [x] 归档条目数提高到 262144, 非 bundletool 集合包 APK 数量提高到 4096, 名称长度提高到 4096 字符.
- [x] 单条目读取提高到 8 GiB, 声明总量提高到 64 GiB, 嵌套扫描和安装暂存总量提高到 16 GiB; 实际暂存仍检查可用磁盘空间.
- [x] APK/AAB 清单与 toc.pb 输入提高到 16 MiB, 容器元数据提高到 4 MiB, 同步提高节点与字符串预算.
- [x] 合成测试覆盖 70000 个资源条目, 位于包末尾的清单, 嵌套 XAPK, 大条目数 AAB/APKS 与超过旧 4 MiB 上限的清单; 超出新预算的容器继续明确拒绝.

流式读取预算与内存分配预算分别约束. 路径合法性, ZIP/protobuf 边界, 重复名称, 字节计数和安装摘要绑定继续校验.

## 插件与文件管理器

- [x] APK Inspector 动作目录声明单文件基数, 同时发布主动作与更多菜单动作, 共用只读检查入口; 源码位于配套 APK Inspector 仓库.
- [x] .m3u8/.pls/.wax/.wvx/.asx 文件行使用播放与编辑两个按钮; 播放按钮继续关联已有播放器. .m3u 的 Ember 优先和 Terra 回退规则继续生效.
- [x] QV710AF65F 联合验证安装/启用状态刷新, 六类安装包扩展名的菜单入口, 微信 APK 独立报告及播放列表按钮.

验证入口: `LargePackageInspectionTest`, `AndroidPackageArchiveInspectorTest`, `AabManifestDisplayDecoderTest`, `BundletoolTocDecoderTest`, `ExplorerItemActionPolicyTest`, `ExplorerM3uPlaylistInstrumentationTest`, `ApkInspectorIntegrationTest`.

配套项目的容量表与验收记录见 APK Inspector 和 APK Builder Template 各自的 `ROADMAP.md`. 本地验证日志位于 `app/build/inspection-fixes/`.

实机复核: 微信 APK 包名 `com.tencent.mm`, 版本 `8.0.48 (2589)`, 报告可显示 V2/V3 签名验证通过. 更多菜单实有 `Inspect Android package` 动作; 五种播放列表的 UI 节点均仅包含播放与编辑两个快捷操作. 截图见 `app/build/inspection-fixes/apk-menu.png`, `apk-inspector-report.png` 与 `playlist-icons.png`.

## 共享解析组件 (3-Setup Installer 原 P8 条目)

- [x] APK Inspector 1.2.2 / build 42 改为消费宿主 `plugin-api/package-archive-parser` 的固定 Release AAR, 与 3-Setup Installer 所用工件相同. 来源为宿主 `0767971bc9` / build 5299, SHA-256 `1441bbcee8468362b0ee41f7b3d5ab47eb87b4df78a1388bb1134223055f46e7`.
- [x] Inspector 删除重叠的 APK/AAB 二进制清单与 TOC 解码, 共用格式/分包选择和预算. 专有签名验证, 授权 idsig, 组件/权限/图标, native/DEX/16 KiB 和 AAB 分发摘要保留在只读报告层, 不把安装暂存接口引入文件检查入口.
- [x] 消费方原测试与新边界回归共 231 项 JVM 通过; QV770340J7/API 33 的服务/元数据契约和六格式 Android 解析共 3/3 通过. 测试仅操作本次私有缓存合成样本, 不安装夹具, 不修改默认项/Root/Shizuku.

此迁移只变更 Inspector 对共享实现的消费方式; 宿主公开脚本/Explorer Action v22/host file-information v1 不变, Inspector 最低宿主仍为 5277. 工件锁, 第三方来源, 签名 Release 和收尾证据见 Inspector `docs/development/shared-parser-migration-evidence.md`, 远端发布继续延迟.
