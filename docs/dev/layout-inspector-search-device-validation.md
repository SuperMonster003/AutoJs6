# 布局分析搜索 P6.3 设备验证

日期: 2026-10-02. 宿主版本: 6.8.0 (5315). 界面调整提交: `3316bd49f3`.

## 当前结论

P6.3 **尚未关闭**. API 24 AVD, Sony API 31 / 33, Redmi API 33 和 Xiaomi Pad API 35 已完成附录 B 的主流程复跑, 并通过实时 / 离线区域筛选增补. Sony G8441 API 28 的新界面设备测试通过, 但这一轮实时捕获受系统中失效的无障碍服务连接阻挡, 不能把旧会话的部分记录或测试脚本的退出码当作全机队通过.

恢复时工作区干净, P6.1 / P6.2 已提交. 上次会话中留在 `build/inspector-smoke/` 的脚本和日志已复用; 原日志另外保留在其 `previous-session/` 子目录. 本次没有修改搜索引擎或公开脚本 API.

## 界面改动与检查

- 节点信息以主题色背景表示选中行, 没有复选框, 切换前后行坐标与文字列宽不变. 同时检查长值换行, 280 dp 宽度, 1.0 / 1.3 字体缩放, 多项选择及清空选择.
- 多选按钮为 "设置 / 返回 / 复制". "返回" 退出多选; 取消最后一项后仍在多选模式, 此时禁止复制. 复制方式默认 "总是询问", 可以记忆对象选择器或链式选择器. Redmi 上实际检查了默认询问, 取消格式选择后继续选择, 设置后直接复制, 再次打开设置的选中项, 最后恢复为 "总是询问".
- 主题色按所在背景调整. 设备测试检查浅色 / 深色模式, 11 种主题色, 对话框 / 搜索栏 / 半透明层次背景, 以及选中行 / 命中子串 / 角标的前景背景组合. 文字目标对比度 4.5, 可用控件目标 3.0. 半透明表面分别叠加纯黑与纯白底图检查.
- 搜索栏 Aa / k=v / $() 字样由 11 sp 增至 15 sp, 筛选 / 正则 / 导航图标也增大, 保留原搜索栏高度和按钮占位.

设备测试为 `app/src/androidTest/java/org/autojs/autojs/ui/floating/layoutinspector/InspectorUiDeviceTest.kt`, 每台 3 项, 六台共 18 项通过. 相关 JVM 测试 78 项通过.

```powershell
.\gradlew.bat :app:assembleAppDebug :app:assembleAppDebugAndroidTest :app:testAppDebugUnitTest --tests 'org.autojs.autojs.core.accessibility.inspector.*' --tests 'org.autojs.autojs.ui.floating.layoutinspector.*' --console=plain
```

最终构建通过, 用时 3 分 49 秒. APK 通过构建中的 16 KB 原生库对齐检查. 设备测试使用手动安装测试 APK 后的 `am instrument`, 没有执行会卸载宿主的 connectedAndroidTest 清理流程.

## 设备记录

| 设备 | API | 新界面设备测试 | 附录 B 主流程 | 实时 / 离线区域增补 |
| --- | --- | --- | --- | --- |
| AVD, Android SDK built for x86 | 24 | 3/3 | 复跑完成; 配置保存已写入偏好, 一次 Done 定位漏测另行检查默认配置关闭按钮 | 通过; 离线末尾清除步骤遇到一次 adb 读取失败, 保留现场重试该步骤后通过 |
| Sony G8441 | 28 | 3/3 | 本轮未完成: 系统服务连接失效 | 未重新验收 |
| Sony XQ-AT72 | 31 | 3/3 | 复跑完成; 6 组导出选择器与脚本计数一致 | 通过 |
| Redmi 22120RN86C | 33 | 3/3 | 复跑完成; 6 组导出选择器与脚本计数一致 | 通过 |
| Sony XQ-DQ72 | 33 | 3/3 | 复跑完成; 6 组导出选择器与脚本计数一致 | 通过 |
| Xiaomi Pad 23046RP50C | 35 | 3/3 | 复跑完成; 6 组导出选择器与脚本计数一致 | 通过 |

API 24 同样完成 6 组导出与脚本计数核对. 其系统设置页没有 WLAN 标签, 相关查询的预期值确为 0; 字段 / 选择器的 id 查询有非零匹配. 区域增补使用实际可见节点的类名作关键词, 并断言结果非零, 避免只验证零匹配路径.

主流程包括关键词 / 正则 / 字段 / 选择器模式, 非法及未结束表达式, 搜索结果和历史, 配置保存 / 再次打开 / 重命名 / 删除, 各筛选维度, 跨窗口状态, 选择器导出及重新执行, 保存布局和离线打开. 原 shell 脚本包含打印结果而非断言的步骤, 因此必须结合截图和逐项输出审阅; `##### done` 本身不代表全部通过.

## 输入法, 焦点与脚本调整

1. Sony API 31 / 33 和平板使用本机 HTTP 页面作为被分析内容, 避开这些设备系统设置页对非系统悬浮窗的限制. HTTP 服务与 adb reverse 仅用于测试页面.
2. Sony API 31 / 33 测试期间临时使用已安装的 Button Mapper 输入法, 避免恢复记录中的 Gboard ASCII 输入变化; 退出后恢复原输入法. 平板的搜狗输入法完成三种模式的测试. 测试期间变更的应用语言也已恢复.
3. 旧脚本按固定坐标滚动筛选对话框, 小屏会点到对话框外. 增补改为读取当前可滚动容器的边界.
4. 区域拖拽从屏幕边缘开始会触发系统返回手势. 增补从内部向左上反向拖拽, 以实际可见节点决定区域和点选位置.
5. 展开浮标会使空查询获焦并显示历史下拉. 未收起搜索栏就点击画布, 实际可能选择了历史条目, 造成查询突然变化或找不到节点菜单. 增补先检查计数和角标, 再收起搜索栏点选画布.
6. API 24 的 TYPE_PHONE 窗口在初始化时有状态栏偏移与可访问性坐标暂不一致的情况. 工具在未展开时重试原坐标与状态栏修正坐标. adb dump 偶尔返回不完整 XML 或读取错误, 只重试有界的读取过程, 不把缺失输出当作通过.

增补工具:

```powershell
python tools/layout-inspector/smoke-region.py <adb-serial>
```

工具使用系统设置页, 或设备 `/sdcard/inspector-target.txt` 中已经配置的测试页面 URL. 它断言区域内存在匹配项, 角标生效, 点击可打开节点菜单, 切换层次窗口保留区域, 清除后角标消失; 保存并离线打开后再检查非零搜索, 文件中的包名候选及同样的区域操作, 并确认离线菜单不提供实时窗口操作. 需要已安装的宿主及已启用的无障碍服务 / 悬浮窗权限.

## 剩余验收与证据位置

Sony G8441 的设置仍显示宿主服务已启用, 但 `dumpsys accessibility` 没有宿主的已绑定实例; `dumpsys activity services org.autojs.autojs6` 保留了宿主 AccessibilityServiceUsher 的 `ConnectionRecord ... DEAD`. 重启应用和重新启用该服务没有恢复本轮捕获. 已请求维护者确认是否重启该设备; 未经确认没有重启. 应在系统服务恢复后补齐本轮实时流程和区域增补, 再决定是否勾选 P6.3.

设备 instrumentation 结束会影响宿主服务绑定. 测试后已单独执行恢复并检查实际绑定状态, 不能只看 secure setting 中是否含组件名. AVD, Sony API 31 / 33 和平板已恢复绑定; Redmi 已完成本轮功能验证, 但最后一次 instrumentation 后再次出现未恢复的绑定, 系统同样保留了 DEAD 连接. Redmi 的重启也已单独请求维护者确认, 没有擅自重启. 这些设备当前状态与之前已录制的功能检查结果分开记录. 恢复输出在本地 `service-restoration.log`.

另保留 API 24 保存配置后 Done 按钮的稳定 UI 复核: 本轮确认了新配置已写入偏好, 默认配置的 Done 关闭操作也已单独通过, 但随后重试新建配置时没有可靠定位到名称输入框, 因此不把这一额外流程计入通过项.

本地证据均在 `build/inspector-smoke/` (gitignored):

- `review-build.log`, `ui-tests-<serial>.log`: 最终构建和设备测试结果
- `fleet-<serial>.log`: 本轮主流程, `previous-session/`: 恢复时的旧日志与无效轮次
- `region-<serial>/result.json` 及 PNG: 五台设备的增补结果与截图
- `region-emulator-5554-resume.log`: adb 读取失败后的离线清除步骤复核
- `profile-emulator-5554-resume.log`: 默认配置 Done 关闭复核; 后续单独重试的新建配置流程未完成, 不额外记作通过
- `node-ui-bek749scrwv4wo8h.json` 及 PNG: 多选行坐标, 按钮和复制方式检查
- `service-restoration.log`: 测试后服务恢复检查

这些原始记录用于复核本次结论; 持久化的验收状态以本文和 Roadmap 为准.
