# P6.1 启动计时与异常路径计时的 CI 门禁

ROADMAP P6.1 的设计. 门禁脚本 `tools/augment/ci_timing_gate.py`, 基线 `tools/augment/baseline/timing-gate.json`, 基线更新 `tools/augment/ci_timing_baseline.py`, 工作流 `.github/workflows/augment-timing-gate.yml`. 待决事项为附录 C Q150-Q154.

门禁是单态的: 不读也不写新脚本模块层开关 (`key_$_new_script_module_layer`), 只测宿主的默认状态 (P5.1 起默认开, P5.2 删除开关后是唯一状态), 因此 P5.2 删除开关不需要改门禁. 它在模拟器上发现回归, 不判定红线 R2: R2 仍在 XQ-AT72 上用 `device_r2.py --verify` 人工判定 (附录 A), 真机数字与模拟器数字不可比, 两者也不互相换算.

## 1. 测什么

- 启动: debug 宿主打印的 `runtime-init-prologue` (毫秒, `bench/aj6_init_timing.js`), 即 R2 的 prologue. `runtime-init-epilogue` 只记录, 不判定. release 宿主不打印这些行, 所以门禁装 debug 宿主.
- 失败调用: `bench/error_path.js` 的 `module-*` 失败项, 以及 `bench/calls/*.js` 中所有 `fail ` 开头的项 (门禁默认跑所有含失败项的文件, 新增文件自动纳入). 每项的值是它对同一次运行中 Rhino 参照项的比值, 参照的取法同 `device_r2.reference_of`: 参数错误对 `undefined.x` (`rhino-undefined-property`, 调用文件里是 `ref rhino-undefined-property`), Java 异常对经 Rhino 自带桥接抛出的 Java 异常 (`rhino-java-exception`), 读 `e.stack` 对 `undefined.x` 之后读 `e.stack`.
- 比值让结果基本不随机器快慢变化: 宿主慢一倍时参照也慢一倍. 微秒值只存档 (结果 JSON 的 `us` / `refUs`), 不判定.
- 不测: 成功调用的耗时, 冷启动 (第一次 prologue 含类加载与 JIT, 波动太大), 内存 (内存门禁 `memory-gate.yml` 已管).

## 2. 在哪跑

- 工作流: GitHub 托管的 `ubuntu-latest` (开 KVM), `reactivecircus/android-emulator-runner@v2.38.0`, API 35 `google_apis` x86_64, `pixel_7`, 2 核, 4096M 内存, 6000M 磁盘, `swiftshader_indirect`. 与 `memory-gate.yml` 同一配方 (该工作流用 runner 默认的 2 核, 这里明写 `cores: 2`, 因为基线是在 2 核上测的). API 35 是两个模拟器工作流共用的稳定镜像; 换镜像或核数要重测基线.
- 触发: push / pull request 到 master 且改动了 `app/src/main/java/org/autojs/autojs/runtime/api/modules/**`, `app/src/main/java/org/autojs/autojs/core/automator/**` (选择器实现, Q152 的变慢就来自这里; 附录 C Q154), `build-logic/ksp-script-modules-*/**`, `tools/augment/**` 或工作流本身; 也可手动 (workflow_dispatch, 可选基线档案与只跑部分调用文件). 同一分支 / PR 的新运行取消旧运行.
- 本机: 在同配方的 AVD 上跑同一个脚本, 步骤与 CI 完全相同 (见第 6 节命令). 门禁会安装宿主并改设置, 遇到非模拟器设备 (`ro.kernel.qemu` / `ro.boot.qemu` 不为 1) 直接拒绝.
- 时长: 本机 (模拟器进程优先级调高后, 第 7 节) 每次约 17 分钟: 暖机约 3 分钟, 每轮 21 个脚本约 7 分钟; 有重测时另加一次暖机与所重测脚本的时间. CI 上的时长要等实跑 (另加构建), 工作流上限 120 分钟.

## 3. 基线从哪来

`tools/augment/baseline/timing-gate.json`:

- `thresholds`: 第 4 节的参数.
- `profiles`: 每种运行环境一个档案 (`local-avd`, `github-actions`), 记录每项的基线值 (`values`, 多次全套运行的中位数), 各次的范围 (`spread`), 来源 (每次运行的时间, 提交, versionCode, 运行模拟器的机器的 CPU, 结论), 配方与更新日期. 门禁用 `--profile` 选档案 (工作流传 `github-actions`, 本机默认 `local-avd`).
- `fallbackProfile`: 所选档案不存在时借用的档案 (`local-avd`), 此时启动计时用更宽的系数 (第 4 节), 输出注明借用.
- `notes`: 个别项的说明 (已登记的回归, 不属 R2 的项), 每次运行都打印 (`CI_TIMING_KNOWN`).

`local-avd` 由本卡在本机 (i5-12400, 12 线程, Windows 11, WHPX, emulator 37.2.12) 同配方的 AVD 上测得 (附录 A "P6.1"), 与附录 A 的真机数字分开存放, 不互相比较. 绝对时间随机器变化 (模拟器 2 核跑在宿主机上, GitHub runner 是 4 vCPU 的 AMD EPYC 嵌套虚拟化), 所以启动计时的基线按环境分档; 比值同时含被测项与参照, 大体不随机器变化, 但嵌套虚拟化下是否相同要等 CI 实跑, 因此比值也按档案存放. `github-actions` 档案在 CI 首次实跑后用 3 次手动触发的结果生成 (第 6 节), 之前借用 `local-avd`.

## 4. 阈值

参数在基线文件的 `thresholds` 中 (附录 C Q150 / Q151):

| 检查 | 值 | 上限 |
| --- | --- | --- |
| `runtime-init-prologue` | 两轮各 10 次的中位数, 取较好的一轮 (ms) | 基线 x 1.5 (`prologueFactor`), 且不超过 60 ms (`prologueCapMs`, 即 R2 的红线); 借用其他档案时为基线 x 3 (`prologueFactorFallback`) |
| 每个失败调用 | 两轮共 6 次中最快的一次, 除以参照最快的一次 | max(1.2, 基线 x 1.35) (`ratioLimit` / `ratioFactor`); 借用其他档案时为 max(1.2, 基线 x 1.6) (`ratioFactorFallback`); 基线中没有的新项: 1.2 |

理由:

- 1.2 仍是下限: 基线低于 1.2 / 1.35 = 0.89 的项, 上限就是 R2 的 1.2. 模拟器上失败调用比值的中位为 0.99, 140 项中 127 项不超过 1.2, 与 XQ-AT72 的 0.87-1.20 相近.
- 不用固定的 1.2 加逐项上限: 模拟器的噪声比真机大得多. 本机同配方 7 次全套运行 (附录 A "P6.1"), 同一项在一次运行中的值对其余各次中位数的偏差, 用 "每轮中位数之比, 取较好一轮" (`device_r2.py` 的取值方式) 时 p90 +11%, p99 +27%, 最大 +44%, 11% 的值偏差超过 +10%; 固定 1.2 下, Q71 / Q86 / Q122 那类 1.1-1.2 的边界项遇到这样的噪声就会超出, 逐项上限又要随每次测量手工维护. 按基线的倍数, 边界项 (以及模拟器上本来就高于 1.2 的项) 自动得到与其值相称的余量.
- 1.35 的来由: 改用 "最快一次之比" 并取相邻参照后, 同样 7 次运行的偏差为 p90 +9%, p99 +18%, 最大 +31%, 980 个值中只有 1 个超过 +30%. 1.35 比最大值多留约 4 个百分点; 再加上重测时取两次尝试中较好的值 (第 7 节), 一项要连续两次超出才算失败. 能发现的回归: 失败调用比基线慢 35% 以上 (模拟器上失败调用约 60-80 us, 即多出约 20-30 us; 例如附录 C Q76 那种 `ScriptMessages` 停在解释执行的情形, 每个失败调用多 30-50 us), 以及任何超过 1.2 的低基线项. 更小的变化 (10-30%) 留给真机 R2.
- 绝对余量 (基线 + 0.1, 原型的做法) 不可行: 原型的取值下 11% 的值偏差超过 +10%, 而高比值的项 (8-18 倍的几项) 偏差以倍计, 绝对余量对它们没有意义.
- 最快一次: 模拟器上的噪声 (宿主机的其他负载, 模拟器自身的调度) 只会让一次运行变慢, 不会变快; 6 次中最快的一次接近不受干扰的耗时. 被测项与参照分别取最快, 不配对 (逐次配对的比值对单次干扰更敏感, 最大偏差 +110%).
- 相邻参照: 前导部分在脚本开头只测一次 `undefined.x`, 而失败项在其后几秒到几十秒才测. 一次运行中各脚本的前导参照可相差 5 倍 (51-278 us). 门禁在每个失败项之后紧接着再测一次 `undefined.x` (`GATE_SHIM`), 比值取这一次. 对照: 同样 3 次运行, 前导参照的偏差 p99 +46%, 相邻参照 +33% (都按 "每轮中位数之比" 计). 结果 JSON 仍保留对前导参照的比值 (`preludeRounds`), 供与 `device_r2.py` 对照.
- prologue: 7 次运行的较好一轮为 14.5-18.0 ms (中位 16.5 ms), 偏差至多 +9%; 单次 5-61 ms, 所以不用单次最快 (4 次中变化反而更大, +23%). 1.5 倍容得下噪声, 又能发现启动多出一个急切构建的大模块 (XQ-AT72 上旧 augment 为 130 ms, 新层 24 ms, 附录 A "P5.1"). 60 ms 的封顶让借用档案时的 3 倍也不会放过超过红线的值.
- 档案不存在时借用 `local-avd` 并放宽 (3 倍, 1.6 倍), 输出注明. 借用只是 CI 首次实跑前的过渡: `github-actions` 档案生成后按 1.5 / 1.35 判定.

## 5. 失败时打印什么

每个未通过的检查一行, 然后是结论行 (同样的内容也写进 GitHub Actions 的作业摘要表, 未通过的排在前面):

```
CI_TIMING_PROBLEM calls/colors: fail colors.toInt-type: over value=2.12 baseline=0.91 limit=1.23
CI_TIMING_PROBLEM calls/colors: fail colors.toInt-range: over value=1.92 baseline=1.13 limit=1.53
CI_TIMING_KNOWN calls/automator: fail pickup-result-type: ok value=15.23 baseline=18.29 limit=24.69 note=Q152: ...
CI_TIMING_RESULT FAIL checked=11 problems=2 attempts=2 versionCode=5935 profile=local-avd minutes=9
```

(本卡的注入验证: 在克隆里给失败的 `colors.toInt` 每次多 40 us, `--calls colors`; 未提交. `toInt()` 的参数个数错误在进入函数前抛出, 没有变慢, 也没有报出.) 其他问题的形式:

```
CI_TIMING_PROBLEM startup: runtime-init-prologue: over value=31 ms baseline=17 ms limit=26 ms
CI_TIMING_PROBLEM calls/ui: fail notice.cancel-type: missing value=- baseline=1.03 limit=1.39
CI_TIMING_PROBLEM calls/automator: timeout: timeout value=- baseline=- limit=-
```

- `over`: 值超过上限. 值的取法见第 4 节; 有重测时 (第 7 节) 是两次尝试中较好的值.
- `missing`: 基线里有这一项, 这次运行没有得到它 (脚本中途出错, 用例被改名或删除). 删改用例时要同时更新基线 (第 6 节), 否则门禁不过.
- `timeout`: 某个脚本的一次运行在 `--script-timeout` (默认 300 s) 内没有结束行.
- 结果 JSON (`build/augment-timing-gate/timing-gate-<label>-<versionCode>-<时间>.json`, CI 中上传为 artifact `augment-timing-gate-result`) 保存设备与机器信息, 参数, 每次尝试每项每轮的比值与微秒值, 全部检查行与结论. 它也是基线更新脚本的输入.
- `CI_TIMING_KNOWN`: 基线 `notes` 中的项 (Q152 的 pickup / detect 失败调用, 不属 R2 的 `blockedGet(1, 2)`, R2 边界项), 不论通过与否每次都打印, 免得被当作已解决.
- 退出码: 通过 0, 未通过 1, 得出结论之前出错 (框架未起来, 安装失败, 宿主不打印启动计时) 2, 此时打印 `CI_TIMING_ERROR`.

## 6. 怎样更新基线

需要更新的情况: 有意改变了失败路径或启动的开销 (并已在 XQ-AT72 上按 R2 复核), 增删或改名了 `bench/calls/*.js` / `error_path.js` 的失败项, 改了模拟器配方. 只有一个文件的改动时, 基线里其他文件的项保持不变也没有关系: 基线更新脚本按全部结果重写整个档案, 所以每次都用全套的结果.

1. 在同配方的 AVD 上跑全套至少 3 次, 各在新冷启动的模拟器或至少新进程上 (门禁自己会 force-stop 并暖机), 不要加 `--calls`:

   ```
   py -3.12 -I -u tools/augment/ci_timing_gate.py -s emulator-5554 --profile local-avd --label base1 --no-rerun
   ```

   CI 的档案 (`github-actions`) 用手动触发的工作流跑 3 次, 下载 3 个 artifact.
2. 预演, 看每项新旧值与 3 次的范围:

   ```
   py -3.12 -I tools/augment/ci_timing_baseline.py --profile local-avd build/augment-timing-gate/timing-gate-base*.json
   ```

3. 确认后加 `--write` 写入 `tools/augment/baseline/timing-gate.json`, 与引起变化的代码放在同一提交或紧随其后, 提交信息写明原因. 脚本只替换该档案的 `values` / `spread` / `sources`, 不动阈值与其他档案; 结果来自不同配方 (型号, SDK, ABI, 在线 CPU) 时拒绝写入, 少于 3 份时警告.

## 7. 噪声控制

- 新进程与暖机: 测量前 force-stop 宿主, 跑 100 次启动计时脚本暖机 (同 `device_r2.py --warmup 100`; debug 包冷进程约需 100 次脚本才稳定). 进程在整次测量中不变, 每轮打印 pid 与 cpuset (`CI_TIMING_ROUND`).
- 轮次: 2 轮, 每轮每个调用脚本 3 次, 启动计时 10 次 (启动计时便宜, 中位数需要更多样本). 失败调用取 6 次中最快的一次 (除以参照最快的一次), prologue 取两轮中位数中较好的一轮 (第 4 节). 噪声只会让值变大, 真实回归在每一次都在.
- 安装后的头几分钟: 系统在后台编译刚装的宿主, 与计时争 2 个核. 只跑一个调用文件时启动计时离安装只有约 3 分钟, 本卡的注入验证中首次尝试的 prologue 为 26 / 30.5 ms (上限 25.5), 重测为 16.5 ms; 全套运行的启动计时在每轮末尾 (安装后约 10 / 17 分钟), 未见此现象.
- 重测: 有检查未通过时 (且只在此时), 只把未通过项所在的脚本在新进程中 (force-stop, 同样暖机) 重测一次, 每项取两次尝试中较好的值, 超时以重测为准; 两次尝试都写进结果 JSON. 通过的运行不多花时间; 一项要两次都超出才算失败, 而真实回归两次都在. 本卡的通过验证 (本机负载高, 每轮约 780 s) 首次尝试有 5 项超出 1-15% (例如 threads.disposable-arity 1.31, 上限 1.23), 重测时都回到各自基线附近 (0.94-1.98), 结论 PASS.
- 本机的其他负载: 本卡测量时其他会话的 Gradle 构建同时在跑 (CPU 负载 50-70%), 一轮从约 430 s 拉长到 810 s, 噪声也明显变大. 本机跑门禁或测基线时, 把模拟器进程 (`qemu-system-x86_64-headless`) 的优先级调到 "高于正常" (PowerShell: `(Get-Process qemu-system-x86_64-headless).PriorityClass = 'AboveNormal'`), 让它接近 CI 上独占的 runner. 附录 A "P6.1" 的基线是这样测的.
- 模拟器上的三个坑 (已在门禁里处理, 本机试测时都曾让数字失真):
  1. API 30 起脚本在 `/sdcard/Download` 下, 宿主需要 "所有文件访问", `install -g` 不给; 门禁用 `appops set --uid org.autojs.autojs6 MANAGE_EXTERNAL_STORAGE allow`. 没有它时 RunIntentActivity 只弹出提示, 没有脚本运行.
  2. API 34 起系统冻结缓存进程 (cached apps freezer): RunIntentActivity 结束后宿主没有前台组件, 成为缓存进程后会停在脚本中途, 直到下一次启动 Activity. 表现为单次调用 0.15-2 s 的离群值和跑 8 分钟的调用文件. 门禁 `settings put global cached_apps_freezer disabled` (立即生效, 不需重启), 结束时恢复原值.
  3. 模拟器 logcat 默认只有 2 MiB 缓冲, 计时行会丢 (表现为 n=1); 门禁设为 16 MiB. 另外, 脚本一次运行超时后仍在宿主里跑, 会与下一个脚本重叠; 门禁记为 `timeout`, 换新进程再继续.
- 不需要 `device_r2.py --verify` 的进程核验 (附录 C Q76): 那是为 XQ-AT72 上 top-app 与 background 相差 4-6 倍的调度组, 以及可调试构建有时让 `ScriptMessages` 停在解释执行而设; 模拟器的 background 与 top-app 都是 CPU 0-1, 没有调度组差异, 而解释执行这类整进程的偏移由新进程重测兜住 (两次尝试在不同进程里, 取较好的). 核验每个脚本多两次 simpleperf 剖析 (各约 15 s), 21 个脚本约多 10 分钟, 不值得.
- debug 宿主的 LeakCanary 与 "脚本未回收" 警告 (开发者选项, debug 构建默认开) 保持默认: 修正上面的坑之后看不到它们对计时的影响, 关掉反而让被测宿主与平时用的 debug 包不同.

## 8. 待决与后续

- 附录 C Q150-Q154 (本卡登记, 2026-10-26 维护者已拍板): 比值的取值与上限, prologue 的上限与环境档案, CI 实跑的方式按推荐 (Q150 / Q151 / Q153); Q152 pickup / detect 失败调用变慢按推荐转交 selector 主题; 触发路径另加 `core/automator/**` (Q154).
- 未做: CI 实跑 (宿主只本地提交); `github-actions` 档案 (CI 首次实跑后按第 6 节生成); 真机 R2 复核 Q152 (属 selector 主题).
