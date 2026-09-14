# SDK 37 P2.13: 内存限制器环境与退出报告入口核对

日期: 2026-09-14. 源码起点 `8ce71de0e`, 本轮生产修改仅涉及 UI 脚本 Activity 的窗口配置声明.
本记录完成执行条件和现有报告链路核对, 不构成内存上限功能验收.

后续已实现历史进程退出入口及 target 36 / 37 回归, 见
[P2.13 / P2.14 后续记录](android-sdk-37-p213-p214-20260914.md).
下文保留本次预检时的源码状态; 限制器压力条件仍未满足.

## 1. 当前镜像状态

独占只读 `AVD_API_37`, serial `emulator-5592`, 私有 adb server 5055,
RAM 4096 MiB. 镜像指纹为
`google/sdk_gphone16k_x86_64/emu64xa16k:17/CE2A.260420.019/15611780:userdebug/dev-keys`.

实际执行 `am memory-limiter status`, 命令返回码为 0, 输出为:

```text
Memory limiter
  disabled
```

P2.12 的每组前置快照同时记录该命令, target 36 / 37 的状态均为 disabled.
原始目录 `build/sdk37-p212-session-20260914/`, 可提交摘要位于
[配置测试机器记录](android-sdk-37-p212-results-20260914.json) 的各组 `memoryLimiter` 字段.
仅更改宿主 target 不会让这个 disabled 环境成为内存限制器的正向对照.

[Android 17 官方说明](https://developer.android.com/about/versions/17/behavior-changes-all)
将内存限制列为影响所有应用的变化, 上限与设备 RAM 有关.
该机制终止进程后, `ApplicationExitInfo` 的 reason 为 `REASON_OTHER`,
description 包含 `MemoryLimiter:AnonSwap`. 官方调试命令包括 status / manual / ignore,
但这些命令在没有运行该机制的设备上不生效.

本轮没有修改全局 DeviceConfig、设备内存策略或时间, 没有用无限分配强行制造 OOM,
也没有执行 OpenCV 大图 / OCR / Node 桥重负载. 普通 Java OOM、LMKD 或手动 force-stop
均不能作为 `MemoryLimiter:AnonSwap` 的替代证据.

## 2. 现有报告链路

- [CrashHandler.kt](../../../app/src/main/java/org/autojs/autojs/tool/CrashHandler.kt)
  实现 `Thread.UncaughtExceptionHandler`, 处理 Java 异常和既有重复异常路径.
  打开报告页时仅通过 Intent 传入 `message` / `error`.
- [CrashReportActivity.kt](../../../app/src/main/java/org/autojs/autojs/ui/error/CrashReportActivity.kt)
  组合应用版本、系统信息和这两个 Intent extra. 当前没有查询历史进程退出记录.
- 对生产源码执行 `rg 'getHistoricalProcessExitReasons|ApplicationExitInfo|MemoryLimiter'`
  无匹配. 因此原 Roadmap 的 "报告页对 REASON_OTHER 的描述展示可读" 不是已有能力的
  简单验收项; 需要实现历史退出信息读取及用户能够进入的展示路径.

本轮没有把系统终止进程等同于 Java 异常, 没有添加无法到达的报告分支,
也没有提前变更公开脚本 API 或报告页产品文案.

## 3. 后续执行条件

1. 在实际启用限制器的 API 37 系统镜像或设备上保存 status, 再运行有界、可取消的
   OpenCV / OCR / Node 代表负载. 同时保留进程 ID、内存采样和真实退出记录,
   核对 reason / description 与被测进程的对应关系.
2. 补齐历史退出诊断入口, 处理多进程、旧记录、重复展示和正常退出等情况,
   再核对真实限制器终止后的可读报告. 实现时更新相应多语言资源 / changelog.
3. 当前可以继续其他 Roadmap 小节; 无需维护者立即执行手动操作.
   后续若能提供限制器已启用的 API 37 镜像或设备, 可补齐这部分证据.

P2.13 保持部分完成, 不关闭重负载或退出报告验收.
