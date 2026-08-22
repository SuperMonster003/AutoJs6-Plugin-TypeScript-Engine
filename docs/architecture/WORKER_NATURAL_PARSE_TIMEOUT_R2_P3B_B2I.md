# R2-P3b-b2i Natural Parse Request-start Absolute-deadline Timeout

日期：2026-07-31

状态：internal provisional evidence **24/24**；Public R2 **0/54**。本切片只关闭
ordinary same-UID Debug direct-host 的自然 **parse** request-start
absolute-deadline timeout 窄缺口；natural emit timeout、external UID、Binder 与
shipping Release 均不在本切片证明范围，也不启用任何公开 capability。

## 证明目标

P3b-b2h 已证明 ordinary workload 的自然 semantic request-start timeout。
P3b-b2i 对 parse 使用独立、冻结的普通 `compileProject` workload，并把
sequence-2 后仍存在的真实 parse 工作变成可复核窗口：

- fault driver 不调用 `requestStop`，不注入 Worker spin，也不使用 expanded
  admission；
- 先完成两次成功 precondition；它们只证明 workload 在当前设备可完整执行，
  不参与 timeout 计算；
- 再运行三次完整成功 baseline，从三次真实 request-start timing 的严格交集
  选择固定 parse timeout；
- baseline 完成后冻结 timeout，parse fault 每台设备只运行一次，不重试、不搜索；
- timeout 必须返回 `ERROR_TIMEOUT`、`compilation=null` 与 `TIMED_OUT` stop
  selection，并命中 parse sequence 2、尚未 parse completed；
- exact Worker 被终止并替换，全部 cursor、snapshot、host 与 spool 可证明清理，
  小项目 successor 成功。

这里的“自然”表示 fault case 只依赖 ordinary request-start absolute deadline。
测试没有 phase-entry hook、可变 deadline、synthetic timing sample 或主动
cancellation。若 precondition 失败，或三次 baseline 没有形成满足双边 margin 的
严格共同窗口，用例必须 fail closed，fault attempt 保持为零。

## 冻结工作负载与成功 authority

`R2NaturalParseTimeoutWorkloadFixture` 保持 production 512-file/8-MiB ordinary
admission，不使用 P3b-b1 expanded authority。它包含 8 个 root、108 个 trusted
declaration 与 1 个 ambient declaration，共 117 个文件；只有 8 个 root 进入
`rootNames`。

8 个 root 位于同一条固定 8-level directory ancestry 下，完整 source path depth
为 10。index 2 root 通过相对 triple-slash reference 引入 ambient wildcard module；
index 3、4、5 root 各包含 5,700 条唯一 type-only import，共 17,100 条，module
index 冻结为 `0..17,099`。因此 Worker 在两个 root 完成并发布 parse sequence 2
后，仍须执行真实 Node10 ancestor resolution；工作量来自合法 TypeScript
解析/解析器路径，不依赖 sleep、spin、无界 filler 或生产阈值放宽。

每次 precondition 与 calibration baseline 都必须完整成功并精确重验：

| 项目 | 冻结值 |
|---|---:|
| root files | 8 |
| common resolver directory depth | 8 |
| source path depth | 10 |
| referenced ambient declarations | 1 |
| unique type-only imports | 17,100 |
| aggregate input | 117 files / 4,879,527 bytes |
| scanner tokens | 257,388 / 262,144 |
| aggregate path characters | 4,434 |
| maximum path characters | 65 |
| input transport frames | 418 |
| root aggregate | 1,094,708 bytes / 171,048 tokens |
| maximum root | 364,832 bytes / 57,006 tokens |
| trusted declarations | 108 files / 3,784,758 bytes / 86,329 tokens |
| ambient declaration | 1 file / 61 bytes / 11 tokens |
| diagnostics | 0 |
| emitted JavaScript | 8 files / 1,524 bytes |
| emitted Source Maps | 8 files / 1,814 bytes |
| aggregate output | 16 files / 3,338 bytes |
| output aggregate SHA-256 | `c16cf1e19354655321bc7bd13bea9840900f2f2b2bf67793b4d1b926ee42e6b3` |

成功运行还必须收到 parse、semantic、emit 各两条真实 progress marker，证明 117
个 input cursor opened/closed 平衡并 released，并退休成功 session。上述 output
authority 只描述完整成功 precondition/baseline；单次 timeout fault 必须没有
compilation payload。

## Desktop 有界内存与窗口预检

desktop golden 固定 Node `--max-old-space-size=512`，三次完整成功运行记录的 parse
sequence 2 / parse completed 如下：

| run | sequence 2 | parse completed | 单次区间 |
|---:|---:|---:|---:|
| 1 | `146.5295 ms` | `1,812.1837 ms` | `1,665.6542 ms` |
| 2 | `210.9464 ms` | `1,801.3931 ms` | `1,590.4467 ms` |
| 3 | `130.0059 ms` | `1,859.1628 ms` | `1,729.1569 ms` |

三次 timing 的 raw strict intersection 为 `1,590.4467 ms`；扣除左右各
`100 ms` 后，usable intersection 为 `1,390.4467 ms`。三次运行的 peak
`heapUsed` 为 `458,785,912 bytes`。这组数据锁定 workload 在 512-MiB old-space
下可完整成功，并在进入设备 fault 前证明 sequence-2 后存在有界、非零的真实
parse 工作；desktop 预检本身不替代 Android runtime evidence。

## 两次预检、三基线严格交集

每台设备先独立执行两次完整成功 precondition，再独立执行三次完整成功
calibration baseline。只有后三次 timing 参与 timeout 选择。每次 timing 都相对于
同一 request-start `elapsedRealtimeNanos`，记录 parse sequence 2 与 parse
completed：

```text
lower = max(floor(sequenceTwoNanos / 1 ms)) + 1 ms
upper = min(floor((completedNanos - 1 ns) / 1 ms))
timeout = lower + floor((upper - lower) / 2)
```

因此 `lower..upper` 是三次 baseline 的严格共同窗口：固定 timeout 在每次
sequence 2 之后、每次 completed 之前。窗口必须至少保留双边 100 ms；否则用例
fail closed，不执行 fault。precondition 不进入交集，校准不使用 synthetic
samples，fault 不允许 retry。

最终 request-start parse timing：

| 设备 | precondition 1 | precondition 2 | baseline 1 | baseline 2 | baseline 3 |
|---|---:|---:|---:|---:|---:|
| API 24 | `2,593/6,567 ms` | `2,501/6,570 ms` | `2,452/6,281 ms` | `2,413/5,949 ms` | `2,474/6,113 ms` |
| API 35 | `11,578/13,521 ms` | `11,034/13,293 ms` | `11,715/13,909 ms` | `11,893/13,896 ms` | `11,199/13,164 ms` |

表中每项均为 `parse sequence 2 / parse completed`。由三次 baseline 得到：

| 设备 | strict window | fixed timeout | left/right margin |
|---|---:|---:|---:|
| API 24 | `2,475..5,949 ms` | `4,212 ms` | `1,737 / 1,737 ms` |
| API 35 | `11,894..13,164 ms` | `12,529 ms` | `635 / 635 ms` |

## Android runtime evidence

API 24/35 各只运行一个正式 parse fault。两案均满足：

- parse progress 为 sequence 2，`2/8`，parse 尚未 completed；
- outcome 为 `ERROR_TIMEOUT`，`compilation=none`，stop cause 为 `TIMED_OUT`；
- `stopSelectedAtElapsedRealtimeNanos >= deadlineAtElapsedRealtimeNanos`，deadline
  overshoot 为 `0 ms`；
- 418 个 input frame 完整传输，117/117 cursor 平衡并 released；
- fault session generation 为 6，exact Worker generation `1→2`；
- workload close、small successor、session retirement、host close 与 spool cleanup
  全部通过。

| 设备 / provider | timeout | overshoot | session | Worker | runner | 结果 |
|---|---:|---:|---:|---:|---:|---|
| API 24 / Chrome `119.0.6045.194` | `4,212 ms` | `0 ms` | 6 | `1→2` | `50.885 s` | PASS |
| API 35 / WebView `130.0.6723.86` | `12,529 ms` | `0 ms` | 6 | `1→2` | `89.718 s` | PASS |

每台设备日志都精确包含 2 条
`NATURAL_PARSE_TIMEOUT_PRECONDITION_PASS`、3 条
`NATURAL_PARSE_TIMEOUT_BASELINE_PASS`、1 条
`NATURAL_PARSE_TIMEOUT_WINDOW_PASS`、1 条 `NATURAL_PARSE_TIMEOUT_PASS` 与 1 条
`NATURAL_PARSE_TIMEOUT_MATRIX_PASS`。该 marker 计数同时证明没有隐藏的 fault
retry；正式 fault 在两次成功 precondition 与三次成功 baseline 之后各执行一次。

API 35 日志中的 6 条 renderer `crash detected (code -1)` 分别紧邻 2 次
precondition、3 次 baseline 的成功 realm retirement，以及 fault 后的 successor /
host-close retirement；它们之后仍完成对应成功 marker 或最终 matrix。正式 API
24/35 日志均没有 V8 OOM、fatal signal、`SIGTRAP`、`SIGABRT` 或 LMK marker，
因此这些 `code -1` 记录属于本测试的预期 renderer 回收，不是被候选 OOM 污染的
成功证据。

定向设备命令固定使用显式 serial：

```powershell
adb -s emulator-5556 shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest#naturalParseRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner

adb -s 968e9f18 shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest#naturalParseRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner
```

## 候选筛选与 fail-closed 记录

正式 fault 前的 workload 筛选保留以下负证据，避免把不兼容旧 WebView 或没有共同
窗口的候选误写成成功：

- depth 10 候选曾在 API 35 完整成功；但 API 24 的第一次 precondition 触发 V8
  heap OOM 与 DedicatedWorker `SIGTRAP`，因此 fault attempts 为 0；
- depth 2 候选在 API 24 成功；但 API 35 三基线得到空区间
  `12,158..11,294 ms`（`lower > upper`），因此 fail closed，fault attempts 为
  0；
- compact depth 6 候选在 API 24 precondition 触发 V8 heap OOM，fault attempts
  为 0；
- depth 4 候选在 API 35 的 success-only probe raw intersection 仅
  `384.621 ms`，未选为正式 authority；
- 最终 depth 8 authority 先在 API 24/35 分别完成全部 success-only
  precondition/baseline，确认窗口成立后，才在每台设备执行唯一一次正式 fault。

这些候选失败没有被计入正式 timeout 成功，也没有通过重试 fault 改写结果。

## 本地门禁与 paired APK

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileReleaseKotlin `
  :app:compileDebugAndroidTestKotlin :app:verifyR2ProjectCompilerDesktop --stacktrace

.\gradlew.bat :app:verifyR2P3bB2iNaturalParseTimeoutSourcePolicy --no-daemon
.\gradlew.bat :app:verifyR2P3bB2iNaturalParseTimeoutAndroidTestApk --no-daemon
.\gradlew.bat verifyR2PublicPreviewFixture
```

source policy 锁定独立 parse workload、两次成功 precondition、三次真实 baseline、
parse-only strict intersection、固定 midpoint、单 fault attempt，以及不得出现测试
侧 `requestStop`、retry、spin、expanded admission 或 synthetic calibration。
desktop golden 同时锁定输入/输出 authority、512-MiB old-space、真实 timing 与
usable intersection。paired-APK gate 检查主应用 runtime DEX 与 androidTest
fixture/evidence DEX；APK gate 本身不代替设备执行。

最终门禁与配对 APK authority：

| 项目 | 结果 |
|---|---|
| b2i source policy | `BUILD SUCCESSFUL in 1m 1s`；74 actionable tasks（7 executed、67 up-to-date） |
| b2i paired-APK gate | `BUILD SUCCESSFUL in 38s`；141 actionable tasks（20 executed、121 up-to-date） |
| debug app APK | 5,604,818 bytes；SHA-256 `76d67804ce5c02182f55826ed4e941fef05cb3f1eb19fef45b8e1c1cde3a3e5c` |
| debug androidTest APK | 1,531,268 bytes；SHA-256 `ade636dccfe87ce6055d7a1a38d97cc71d894a0b0375b59507a16c552ecfc92b` |
| root `verifyR2PublicPreviewFixture` | `BUILD SUCCESSFUL in 1m 50s`；614 actionable tasks（98 executed、516 up-to-date） |

## 尚未证明

P3b-b2i 不证明：

- 自然 emit request-start timeout；
- external-UID、Binder、normal Release、release-equivalent 或 shipping Release
  的自然 parse/semantic timeout；
- 多轮 fault stress、repeated large-job PSS/FD plateau；
- renderer LMK/OOM、force-stop 或其他 system fault；
- Unicode/high-token/adversarial 分布；
- 128 MiB container / 256 MiB expanded file-backed output；
- formal request/output/diagnostic schemas 或 tsconfig/NodeNext。

因此 ordinary production fence 继续为 **512 files、8 MiB/file、8 MiB total**；
`compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、`sourceMapV3`
四项 capability 继续全部为 `false`，Public R2 checklist 继续为 **0/54**。
P3b-b2i 把 internal provisional evidence 推进到 **24/24**，但不是 Public R2
开放声明。
