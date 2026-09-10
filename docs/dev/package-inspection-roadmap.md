# 安装包检查与文件操作路线图

更新日期: 2026-09-10.

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
