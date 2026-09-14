# SDK 37: Samsung 真机网络与 DEX / Looper 补验

日期: 2026-09-14. 维护者恢复 `localhost:37478` 连接后执行.
实现提交 `494a048df`, 开测工作区 `master@8130e1941`, 工作区干净.
承接 [Q7 的模拟器与 APK 证据](android-sdk-37-q7-20260914.md), 本轮使用同一批已核验产物,
不重新构建或修改生产代码. 原始 JUnit 输出、命令、源文件 / APK 哈希和安装 / 清理结果见
[机器记录](android-sdk-37-q7-samsung-results-20260914.json).

## 1. 设备与测试条件

| 项目 | 实际值 |
| --- | --- |
| ADB serial / 型号 | `localhost:37478` / Samsung SM-F776B |
| 系统 / ABI | Android 17, API 37, `arm64-v8a`, user / release-keys |
| 指纹 | `samsung/b8sxeea/b8s:17/CP2A.260605.016/F776BXXU1AZGI_OXM1AZGI:user/release-keys` |
| 内核页 | getconf 和 `/proc/self/smaps` 均为 4096, 不属于真实 16 KB 内核 |
| 内存限制器 / Advanced Protection | 开测与结束均为 disabled / false |
| 开测安装状态 | 宿主、inrt 和对应两个测试包均不存在 |
| 兼容开关 | 使用实际 target 的默认状态, 没有写入 override; 相关条目的开测 / 结束 dump 相同 |

HTTP 用例的服务器位于被测应用进程内, 将保留域名和 GitHub 拒绝负例的 DNS 映射至 IPv4 回环.
TLS 服务器只监听 PC `127.0.0.1:17647`, 本轮用 `reverse --no-rebind tcp:17647 tcp:17647`
在选定手机创建一条转发. 此前没有 reverse, 未占用已有映射. 这不是真实 LAN / VPN 网络测试,
没有向真实 GitHub 发送明文请求, 没有安装系统 / 用户 CA 或手动授予权限.

## 2. 六组结果

所有 APK 均为上一轮归档并校验过的 6.8.0 build 5280 产物.
正常包取 `build/sdk37-q7-20260914/apks/restored`, 覆盖包取 `apks/target37`,
测试 APK 按目标分别取 `apks/normal` / `apks/target37`. 安装使用原包名和同一签名,
不改 APK, 不绕过禁止 release target override 的门禁. 当前 release 未启用 minify,
同签名测试 APK 可直接测试其安装后的实现和资源.

| APK | targetSdk | HTTP | TLS / CT | DEX | Looper / 线程池 | 合计 |
| --- | --- | --- | --- | --- | --- | --- |
| appDebug 正常包 | 36 | 4/4 | 6/6 | 1/1 | 2/2 | 13/13 |
| inrtDebug 正常包 | 29 | 4/4 | 6/6 | 1/1 | 2/2 | 13/13 |
| appDebug 覆盖包 | 37 | 4/4 | 6/6 | 1/1 | 2/2 | 13/13 |
| inrtDebug 覆盖包 | 37 | 4/4 | 6/6 | 1/1 | 2/2 | 13/13 |
| appRelease 正常包 | 36 | 4/4 | 1/1 | - | - | 5/5 |
| inrtRelease 正常包 | 29 | 4/4 | 1/1 | - | - | 5/5 |
| 总计 | | **24/24** | **26/26** | **4/4** | **8/8** | **62/62** |

每组按完整类名 / 方法名检查 JUnit 完成身份, 所有方法均返回成功, 无跳过、缺失或重复.
网络 / 脚本调用、服务器和测试线程均有超时 / 清理, 没有运行完整无障碍脚本或重绑服务.

HTTP 四项使用 `CleartextTrafficDeviceTest`: 读取实际 NSC 策略, 接收真实 OkHttp 响应,
验证 GitHub 资源域 HTTP 在到达服务器前被拒绝, 并用 Rhino `http.get` 验证相同正反例.
这补齐了 Q7 在 Samsung ARM64 真机上的宿主 / inrt 与 debug / release 证据.

Debug TLS 六项使用 `NetworkSecurityCompatibilityDeviceTest`: 未知 CA 拒绝、指定 debug 域信任、
显式 CT 缺少 SCT 拒绝、默认 CT / ECH 策略, 以及 `isInsecure` / `insecure` 的同步、回调、Promise
请求隔离和主机名校验恢复. Release 只运行生产配置下的未知 CA 拒绝方法, 不把 debug 信任正例
套到 release. target 37 的默认 CT=true / ECH mode=2, 正常旧 target 为 false / 1, 均通过断言.
服务器观测 118 条 ClientHello, ECH 扩展数为 0; 服务端不支持 ECH, 不宣称真实 ECH 协商通过.

## 3. 同时补齐的权限与线程观察

手机对宿主和 inrt 都记录了相同的权限变化:

| 安装阶段 | ACCESS_LOCAL_NETWORK | 回环网络用例 |
| --- | --- | --- |
| 首次安装正常 target 36 / 29 | granted=true, 带 REVOKE_WHEN_REQUESTED | 通过 |
| 原包覆盖为 target 37 | granted=false | 通过, 用例结束后仍为 false |
| 原包恢复正常 target 36 / 29 release | granted=true | 通过 |

这是系统随目标版本处理的隐式授权 / 撤销, 本轮未执行 `pm grant` / `pm revoke` 或打开权限请求 UI.
旧目标包的 Manifest 不显式声明新权限, 但系统 package dump 会列出其隐式权限状态;
不能把 dump 条目误当作 APK 的显式声明. 此结果只补充 P2.1 的目标版本转换和同设备 IPv4 回环,
不关闭 LAN 的允许 / 拒绝、权限弹窗、独立插件 UID 或跨 profile 验收.

`AndroidClassLoaderRawDexDeviceTest` 在四个 debug 组合分别通过:
两次加载均返回固定值, 原始 DEX 和缓存字节不变, staging 文件保持只读. 这补充 P2.3 的
ARM64 实机回归, 不代表 OpenCV / OCR 等原生插件的 `.so` 加载已在这台手机验收.

`LooperPlatformCompatibilityDeviceTest` 在同四组分别通过:
正常 target 保留原生中断标记, target 37 派发消息时清除原生标记, `ThreadCompat` 取消状态仍保留,
显式清除正常. target 37 的两个线程池绕过工作线程 `start()` 覆写但仍执行 `run()`,
直接调用普通 `Thread.start()` 继续执行覆写. 结论来自测试断言; 本轮指定标签的有界 logcat
读取返回空内容, 没有把空日志当作运行时值证据. 完整输出保留于机器记录.

## 4. 清理与验收边界

结束前读取手机上四个最终 APK 的 `sha256sum`, 与本轮安装记录逐一匹配后, 才移除这四个
开测时不存在的包. 只删除本轮创建的 `tcp:17647` reverse, 核对 PID / 启动时间 / 命令行后关闭
本地 TLS 服务. 13:02:19 +08:00 后的最终检查确认设备仍在线、四个包已不存在、reverse 为空,
内存限制器和 Advanced Protection 与开测状态相同. 原共享 adb server PID 31400 保留.

本轮只有设备测试及证据 / Roadmap 更新, 没有新的 feature / fix / improvement / dependency,
因此不重复增加更新日志或递增版本. 当前配置仍为 compileSdk 37 / 宿主 target 36 / inrt target 29.

实体机清单的 O02 和 A05 已取得部分实测证据, 但仍有真实公网 / 企业代理、ECH、完整脚本取消与
无障碍 / OEM 恢复待做. N01 只补充了权限状态与回环观察. 99 个完整验收行继续保留复选框,
不把部分通过写成整行完成, 也不再把所有子项概括为从未执行.
Media3、inrt 存储、插件升级发布、人工听音与其他实体机功能继续按原 Roadmap 推进.
