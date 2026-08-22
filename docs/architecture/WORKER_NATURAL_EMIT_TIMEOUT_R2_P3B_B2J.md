# R2-P3b-b2j Natural Emit Request-start Absolute-deadline Timeout

日期：2026-07-31

状态：历史 internal provisional evidence **25/25**；Public R2 **0/54**。本切片当时只关闭
ordinary same-UID Debug direct-host 的自然 **emit** request-start
absolute-deadline timeout 窄缺口；external UID、Binder 与 shipping Release 均不在
本切片证明范围，也不启用任何公开 capability。

> 历史边界说明：本文件的 workload、timing、APK 与设备结果记录的是
> 2026-07-31 当时的 **pre-result-egress completion boundary**，不得解释为当前
> binary 的 emit timing。当前 emit 已覆盖 Worker output validation/hashing、
> serialization/digest 与 ACK-backpressured framed egress，并在最后一个 result
> chunk ACK 后、`projectResultEnd` 前 completed；native typed result finalizer
> 不属于 emit。当前 external-UID Binder Debug/Release-equivalent × API 24/35 四格
> 证据由
> [R2-P3b-b2k External-UID Binder Natural Emit Timeout](EXTERNAL_NATURAL_EMIT_TIMEOUT_R2_P3B_B2K.md)
> 接管，当前 internal provisional evidence 为 **26/26**，且不宣称
> instruction-level `builder.emit` interruption。

## 证明目标

P3b-b2h 与 P3b-b2i 已分别证明同一窄范围内的自然 semantic、parse
request-start timeout。P3b-b2j 使用独立、冻结的普通 `compileProject`
emit workload：

- fault driver 不调用 `requestStop`，不注入 Worker spin，也不使用 expanded
  admission；
- 先完成两次成功 precondition；它们只证明 workload 可在当时设备完整执行，
  不参与 timeout 计算；
- 再运行三次完整成功 baseline，从三次真实 request-start timing 的严格交集
  选择固定 emit timeout；
- baseline 完成后冻结 timeout，每台设备只执行一次正式 fault，不重试、不搜索；
- timeout 必须返回 `ERROR_TIMEOUT`、`compilation=null` 与 `TIMED_OUT` stop
  selection，并命中 emit sequence 2、尚未 emit completed；
- exact Worker 被终止并替换，全部 cursor、snapshot、host 与 spool 可证明清理，
  小项目 successor 成功。

这里的 deadline 始终相对于整个 compile request 的开始时刻，不是相对于 emit
phase entry 的 phase-relative timeout。测试没有 phase-entry hook、可变 deadline、
synthetic timing sample、主动 stop 或 cancellation。若 precondition 失败，或三次
baseline 没有形成满足双边 margin 的严格共同窗口，用例必须 fail closed，正式
fault 保持未执行。

## 冻结工作负载与成功 authority

`R2NaturalEmitTimeoutWorkloadFixture` 固定使用 TypeScript `6.0.3` 与 ES2018
target，保持 production 512-file/8-MiB ordinary admission，不使用 P3b-b1
expanded authority。输入由 108 个 trusted declarations 与 8 个 root 组成；只有
8 个 root 进入 `rootNames`。

前 2 个 light root 只产生最小 JavaScript 与 Source Map。其余 6 个 heavy root
各包含 18 条合法 optional chain，每条 chain depth 为 110。TypeScript 必须把这些
optional chain 与 nullish fallback 自然 downlevel 到 ES2018。emit sequence 2 在
两个 light root 对应的 4 个 output 完成后发布，此时后续 6 个 heavy root 的
transform/write 工作尚未完成；窗口不依赖 sleep、spin 或人为 phase delay。

每次 precondition 与 calibration baseline 都必须完整成功并精确重验：

| 项目 | 冻结值 |
|---|---:|
| TypeScript / target | `6.0.3` / ES2018 |
| root files | 8（2 light + 6 heavy） |
| optional chains per heavy root | 18 |
| optional chain depth | 110 |
| aggregate input | 116 files / 3,841,832 bytes |
| scanner tokens | 110,639 / 262,144 |
| input transport frames | 400 |
| root aggregate | 57,074 bytes / 24,310 tokens |
| maximum root | 9,502 bytes / 4,049 tokens |
| trusted declarations | 108 files / 3,784,758 bytes / 86,329 tokens |
| aggregate path characters | 4,145 |
| maximum path characters | 46 |
| diagnostics | 0 |
| emitted JavaScript | 8 files / 769,510 bytes |
| emitted Source Maps | 8 files / 196,204 bytes |
| aggregate output | 16 files / 965,714 bytes |
| output aggregate SHA-256 | `e7eac5f110b4dff7bbb732ea47c3f21c083b44fa215b740895a8e8bac9ba4fa6` |

provisional compiler output ceiling 为 1 MiB（1,048,576 bytes）。冻结输出仍保留
`82,862 bytes`，比一个 64-KiB raw output chunk 多 `17,326 bytes`。因此成功
authority 不处于 quota 临界点，并明确保留超过一个完整 raw frame 的输出余量。

成功运行还必须收到 parse、semantic、emit 各两条真实 progress marker，证明 116
个 input cursor opened/closed 平衡并 released，并退休成功 session。上述 output
authority 只描述完整成功 precondition/baseline；单次 timeout fault 必须没有
compilation payload。

## 两次预检、三基线严格交集

每台设备先独立执行两次完整成功 precondition，再独立执行三次完整成功
calibration baseline。只有后三次 timing 参与 timeout 选择。每次 timing 都相对于
同一 request-start `elapsedRealtimeNanos`，记录 emit sequence 2 与 emit
completed：

```text
lower = max(floor(sequenceTwoNanos / 1 ms)) + 1 ms
upper = min(floor((completedNanos - 1 ns) / 1 ms))
timeout = lower + floor((upper - lower) / 2)
```

因此 `lower..upper` 是三次 baseline 的严格共同窗口：固定 timeout 在每次 emit
sequence 2 之后、每次 emit completed 之前。窗口必须至少保留双边 100 ms；
否则用例 fail closed，不执行 fault。precondition 不进入交集，校准不使用
synthetic samples，fault 不允许 retry。

当时 pre-result-egress boundary 的 request-start emit timing：

| 设备 | precondition 1 | precondition 2 | baseline 1 | baseline 2 | baseline 3 |
|---|---:|---:|---:|---:|---:|
| API 24 | `5,160/6,829 ms` | `4,509/6,088 ms` | `4,475/5,938 ms` | `4,585/6,050 ms` | `4,520/5,879 ms` |
| API 35 | `11,547/12,471 ms` | `10,956/11,904 ms` | `11,057/11,876 ms` | `10,923/11,902 ms` | `10,779/11,638 ms` |

表中每项均为 `emit sequence 2 / emit completed`。由三次 baseline 得到：

| 设备 | strict window | fixed timeout | left/right margin |
|---|---:|---:|---:|
| API 24 | `4,586..5,879 ms` | `5,232 ms` | `646 / 647 ms` |
| API 35 | `11,058..11,638 ms` | `11,348 ms` | `290 / 290 ms` |

## 历史 Android runtime evidence

当时的 API 24/35 binary 各只运行一个正式 emit fault。两案均满足：

- emit progress 为 sequence 2，`4/16`，emit 尚未 completed；
- outcome 为 `ERROR_TIMEOUT`，`compilation=none`，stop cause 为 `TIMED_OUT`；
- `stopSelectedAtElapsedRealtimeNanos >= deadlineAtElapsedRealtimeNanos`，且
  deadline overshoot 有界；
- 400 个 input frame 完整传输，116/116 cursor 平衡并 released；
- fault session generation 为 6，exact Worker generation `1→2`；
- workload close、small successor、session retirement、host close 与 spool cleanup
  全部通过。

| 设备 / provider | timeout | overshoot | session | Worker | runner | 结果 |
|---|---:|---:|---:|---:|---:|---|
| API 24 / Chrome-WebView `119.0.6045.194` | `5,232 ms` | `2 ms` | 6 | `1→2` | `45.271 s` | PASS |
| API 35 / WebView `130.0.6723.86` | `11,348 ms` | `1 ms` | 6 | `1→2` | `84.526 s` | PASS |

每台设备日志都精确包含 2 条
`NATURAL_EMIT_TIMEOUT_PRECONDITION_PASS`、3 条
`NATURAL_EMIT_TIMEOUT_BASELINE_PASS`、1 条
`NATURAL_EMIT_TIMEOUT_WINDOW_PASS`、1 条 `NATURAL_EMIT_TIMEOUT_PASS` 与 1 条
`NATURAL_EMIT_TIMEOUT_MATRIX_PASS`。该 marker 计数同时证明没有隐藏的 fault
retry；正式 fault 在两次成功 precondition 与三次成功 baseline 之后各执行一次。

定向设备命令固定使用显式 serial：

```powershell
adb -s emulator-5556 shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest#naturalEmitRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner

adb -s 968e9f18 shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest#naturalEmitRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner
```

## 候选筛选与 fail-closed 记录

正式 fault 前的 workload 筛选保留以下负证据，避免把不可完整执行或没有稳定共同
窗口的候选误写成成功：

- optional-chain depth 200、150 与 125 候选在 success-only probe 中触发
  `Maximum call stack size exceeded`，没有进入正式 fault；
- depth 100 候选能够完成部分 success-only matrix，但三次 baseline 的共同窗口
  为空或不足双边 100 ms，因而 fail closed，没有进入正式 fault；
- 把工作拆到更多文件尾部的候选仍无法形成足够的共同 emit tail；其中
  200 roots（50 light + 150 heavy）、depth 64 的 API 35 probe 得到空区间
  `16,968..16,339 ms`，没有进入正式 fault；
- 最终 8-root、18-chain、depth-110 authority 先在 API 24/35 分别完成全部
  success-only precondition/baseline，确认严格窗口成立后，才在每台设备执行唯一
  一次正式 fault。

这些候选失败没有被计入正式 timeout 成功，也没有通过 stop、retry、spin、
expanded admission 或 phase-relative deadline 改写结果。

## 本地门禁与 paired APK

```powershell
.\gradlew.bat :app:verifyR2P3bB2jNaturalEmitTimeoutSourcePolicy --no-daemon
.\gradlew.bat :app:verifyR2P3bB2jNaturalEmitTimeoutAndroidTestApk --no-daemon
.\gradlew.bat verifyR2PublicPreviewFixture --console=plain
```

当时的 source policy 锁定独立 emit workload、TypeScript/ES2018 profile、两次成功
precondition、三次真实 baseline、emit-only strict intersection、固定 midpoint、
单 fault attempt，以及不得出现测试侧 `requestStop`、retry、spin、expanded
admission、synthetic calibration 或 phase-relative deadline。desktop golden
同时锁定输入/输出 authority 与 1-MiB output margin。paired-APK gate 检查主应用
runtime DEX 与 androidTest fixture/evidence DEX；APK gate 本身不代替设备执行。

当时的定向门禁与配对 APK authority：

| 项目 | 结果 |
|---|---|
| b2j source policy | PASS；`78.600 s`；75 actionable tasks（8 executed、67 up-to-date） |
| b2j paired-APK gate | PASS；`51.725 s`；142 actionable tasks（21 executed、121 up-to-date） |
| root `verifyR2PublicPreviewFixture` | PASS；`BUILD SUCCESSFUL in 1m 40s`；616 actionable tasks（68 executed、548 up-to-date） |
| debug app APK | 5,604,818 bytes；SHA-256 `76d67804ce5c02182f55826ed4e941fef05cb3f1eb19fef45b8e1c1cde3a3e5c` |
| debug androidTest APK | 1,538,015 bytes；SHA-256 `9ba37d225afe85047011dd04de7f67fb95f29442a8c5629b020cdc96a01db35c` |

## 尚未证明

P3b-b2j 不证明：

- external-UID、Binder、normal Release、release-equivalent 或 shipping Release
  的自然 parse/semantic/emit timeout；
- 多轮 fault stress、repeated large-job PSS/FD plateau；
- renderer LMK/OOM、force-stop 或其他 system fault；
- Unicode/high-token/adversarial 分布；
- 128 MiB container / 256 MiB expanded file-backed output；
- formal request/output/diagnostic schemas 或 tsconfig/NodeNext。

其中 external-UID Binder 的自然 emit timeout 已由后续 b2k current-binary 四格
单独关闭；本列表保留的是 b2j 自身的历史证明边界，并不否定后续证据。

因此 ordinary production fence 继续为 **512 files、8 MiB/file、8 MiB total**；
`compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、`sourceMapV3`
四项 capability 继续全部为 `false`，Public R2 checklist 继续为 **0/54**。
P3b-b2j 把 internal provisional evidence 推进到 **25/25**，但不是 Public R2
开放声明。
