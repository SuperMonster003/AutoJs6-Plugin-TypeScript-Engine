# R2-P3b-b2h Natural Semantic Request-start Absolute-deadline Timeout

日期：2026-07-31

状态：internal provisional evidence **23/23**；Public R2 **0/54**。本切片只关闭
ordinary same-UID Debug direct-host 的自然 **semantic** request-start
absolute-deadline timeout 窄缺口；parse/emit timeout、external UID、Binder 与
shipping Release 均不在本切片证明范围，也不启用任何公开 capability。

## 证明目标

P3b-b2g 已证明 ordinary workload 在 parse、semantic、emit 的真实 sequence-2
progress 后可由 keyed cancellation 终止，但该用例主动调用 cancellation
control。P3b-b2h 改用普通 `compileProject` deadline：

- fault driver 不调用 `requestStop`，不注入 Worker spin，也不使用 expanded
  admission；
- 先运行三次完整成功 baseline，再从三次真实 request-start timing 的严格交集
  选择一个固定 semantic timeout；
- baseline 完成后冻结 timeout，semantic fault 只运行一次，不重试、不搜索；
- timeout 必须返回 `ERROR_TIMEOUT`、`compilation=null` 与 `TIMED_OUT` stop
  selection，并命中 semantic sequence 2、尚未 semantic completed；
- exact Worker 被终止并替换，全部 cursor、snapshot、host 与 spool 可证明清理，
  小项目 successor 成功。

这里的“自然”表示 fault case 只依赖 ordinary request-start deadline。host 的普通
deadline path 到期后会在内部选择 `TIMED_OUT`；测试驱动本身不发送 cancellation
请求，也不借助 phase-entry hook 改变运行时序。

## 冻结工作负载与成功 authority

`R2NaturalPhaseTimeoutWorkloadFixture` 保持 production 512-file/8-MiB ordinary
admission，不使用 P3b-b1 expanded authority。输入由 108 个 trusted declarations、
256 个 source roots 与 148 个 late declaration helpers 组成；只有 256 个 roots
进入 `rootNames`。每次 calibration baseline 都必须完整成功并精确重验：

| 项目 | 冻结值 |
|---|---:|
| aggregate input files | 512 |
| aggregate UTF-8 bytes | 7,726,972 |
| scanner tokens | 151,525 / 262,144 |
| diagnostics | 0 |
| emitted JavaScript | 256 files |
| emitted Source Maps | 256 files |
| aggregate output | 512 files / 720,866 bytes |
| output aggregate SHA-256 | `4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1` |

baseline 还必须收到 parse、semantic、emit 各两条真实 progress marker，证明 512
个 input cursor opened/closed 平衡并 released，并退休成功 session。上述 output
authority 只描述完整成功 baseline；单次 timeout fault 必须没有 compilation
payload。

## 三基线严格交集

每台设备独立执行三次完整成功 baseline。每次 timing 都相对于同一
request-start `elapsedRealtimeNanos`，记录 semantic sequence 2 与 semantic
completed。校准只接受三次真实 observation：

```text
lower = max(floor(sequenceTwoNanos / 1 ms)) + 1 ms
upper = min(floor((completedNanos - 1 ns) / 1 ms))
timeout = lower + floor((upper - lower) / 2)
```

因此 `lower..upper` 是三次 baseline 的严格共同窗口：固定 timeout 在每次
sequence 2 之后、每次 completed 之前。窗口必须至少保留双边 100 ms；否则用例
fail closed，不执行 fault。校准不会使用 synthetic samples，fault 不允许 retry。

最终设备窗口：

| 设备 | strict window | fixed timeout | left/right margin |
|---|---:|---:|---:|
| API 24 | `9,331..12,777 ms` | `11,054 ms` | `1,723 / 1,723 ms` |
| API 35 | `37,725..38,828 ms` | `38,276 ms` | `551 / 552 ms` |

## 绝对 deadline 与 completion race

host 在 request 开始时读取一次 `SystemClock.elapsedRealtimeNanos()`，以
`Math.addExact(startedAtNanos, timeoutNanos)` 得到唯一 absolute deadline。等待
逻辑每轮都用同一 deadline 减当前 monotonic nanos，不把一次相对 wait 的提前返回
解释为 timeout。

`InterruptedException` 只会被记录；等待继续使用原 absolute deadline，终态后恢复
线程 interrupt flag。因此 interrupt 不能伪造 deadline 已到，也不能把本用例变成
主动 cancellation。

终审曾发现：如果 completion path 先在 host lock 内读 clock，再到 operation
handle 的另一临界区提交 `COMPLETED`，线程可能在两者之间跨过 deadline。当前实现
把 monotonic clock supplier 传入 operation handle，并在 handle 的同一
`stateLock` 临界区内采样时间、原子选择 `COMPLETED` 或写入 `TIMED_OUT`。host
仍在自己的同一 state selection 内立即冻结 phase/progress stop receipt。这样晚到
的 completed/result 不能越过 deadline，timeout 也不能在 deadline 之前胜出。
latch 并发回归同时覆盖 deadline 前正常完成，以及 deadline 边界的
`TIMED_OUT` 先于竞争 `CANCELLED`。termination evidence 携带 deadline 与
selection 的 absolute nanos。

## Android runtime evidence

API 24/35 各只运行一个 semantic fault target。两案均满足：

- semantic progress 为 sequence 2，`64/256`，semantic 尚未 completed；
- outcome 为 `ERROR_TIMEOUT`，`compilation=none`，stop cause 为 `TIMED_OUT`；
- `stopSelectedAtElapsedRealtimeNanos >= deadlineAtElapsedRealtimeNanos`；
- 512/512 cursor 平衡、input released；
- exact Worker generation `1→2`；
- workload close、small successor、session retirement、host close 与 spool cleanup
  全部通过。

| 设备 / provider | timeout | overshoot | Worker | runner | 结果 |
|---|---:|---:|---:|---:|---|
| API 24 / Chrome `119.0.6045.194` | `11,054 ms` | `2 ms` | `1→2` | `62.476 s` | PASS |
| API 35 / WebView `130.0.6723.86` | `38,276 ms` | `0 ms` | `1→2` | `168.226 s` | PASS |

每案记录 `NATURAL_PHASE_TIMEOUT_PASS`，设备行最终记录
`NATURAL_PHASE_TIMEOUT_MATRIX_PASS`。overshoot 只表示 host stop selection 相对
absolute deadline 的非负差值，不是 WebView/Worker 终止完成时延。

修复后的 interrupt/absolute-deadline regression 也在 API 24/35 分别以
`1.641 / 1.595 s` PASS。定向设备命令固定使用显式 serial：

```powershell
adb -s emulator-5556 shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest#naturalSemanticRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner

adb -s 968e9f18 shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest#naturalSemanticRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner
```

## 本地门禁

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileReleaseKotlin `
  :app:compileDebugAndroidTestKotlin :app:verifyR2ProjectCompilerDesktop --stacktrace

.\gradlew.bat :app:verifyR2P3bB2hNaturalPhaseTimeoutSourcePolicy --no-daemon
.\gradlew.bat :app:verifyR2P3bB2hNaturalPhaseTimeoutAndroidTestApk --no-daemon
.\gradlew.bat verifyR2PublicPreviewFixture
```

desktop golden 与 source policy 锁定独立 timeout workload、三次真实 baseline、
semantic-only strict intersection、固定 midpoint、单 fault attempt，以及不得出现
测试侧 `requestStop`、retry、spin、expanded admission 或 synthetic calibration。
paired-APK gate 同时检查主应用 runtime DEX 与 androidTest fixture/evidence DEX；
APK gate 本身不代替设备执行。修复 TOCTOU 后的 b2h source + paired-APK 定向门禁
为 `BUILD SUCCESSFUL in 1m 4s`（140 actionable tasks：20 executed、
120 up-to-date）；最终根 `verifyR2PublicPreviewFixture` 为
`BUILD SUCCESSFUL in 1m 45s`（612 actionable tasks：78 executed、
534 up-to-date）。

## 尚未证明

P3b-b2h 不证明：

- 自然 parse 或 emit request-start timeout；
- external-UID、Binder、normal Release、release-equivalent 或 shipping Release
  的自然 semantic timeout；
- 多轮 fault stress、repeated large-job PSS/FD plateau；
- renderer LMK/OOM、force-stop 或其他 system fault；
- Unicode/high-token/adversarial 分布；
- 128 MiB container / 256 MiB expanded file-backed output；
- formal request/output/diagnostic schemas 或 tsconfig/NodeNext。

因此 ordinary production fence 继续为 **512 files、8 MiB/file、8 MiB total**；
`compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、`sourceMapV3`
四项 capability 继续全部为 `false`，Public R2 checklist 继续为 **0/54**。
P3b-b2h 是 internal provisional semantic-timeout evidence，不是 Public R2
开放声明。

## 后续证据

后续 P3b-b2i 已用独立 ordinary workload 关闭同一窄范围内的自然 parse
request-start absolute-deadline timeout，并把 internal provisional evidence
推进到 **24/24**；其两次成功 precondition、三基线严格交集、API 24/35 单次
fault 与 fail-closed 候选记录见
[WORKER_NATURAL_PARSE_TIMEOUT_R2_P3B_B2I.md](WORKER_NATURAL_PARSE_TIMEOUT_R2_P3B_B2I.md)。
这项后续证据不追溯扩大本页 b2h 的 semantic-only 证明边界；Public R2 仍为
**0/54**，四项 capability 仍全部为 `false`。
