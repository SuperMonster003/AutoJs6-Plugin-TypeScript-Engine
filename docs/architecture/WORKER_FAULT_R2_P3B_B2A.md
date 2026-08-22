# R2-P3b-b2a DEBUG Phase-entry Hard-stop and Active Renderer-loss

状态：内部 `DEBUG` fault-infrastructure 切片；不是完整 R2-P3b-b，也不是
Public R2。

P3b-b1 已把 exact 64 MiB caller input 送过 Android direct/Binder pipeline，
并在成功前退休 exact WebView realm。P3b-b2a 在不放宽 production admission
的前提下，为 Worker 的 ingress、parse、semantic、emit 建立严格 phase
telemetry，以可重复的入口 spin 证明 keyed cancel/timeout 会硬终止 exact
Worker；同时在 API 35 的 active exact-64-MiB semantic window 中主动终止
renderer，关联 `RenderProcessGoneDetail`、旧/新 renderer process identity 与
successor recovery。

后续 P3b-b2b 已在同一 production fence 下补充 repeated exact-64-MiB
resource、自然 ingress cancel cleanup 与 compiler-service startup recognized
orphan scavenging；见
[R2-P3b-b2b Repeated Resource and Startup Orphan Cleanup](WORKER_RESOURCE_R2_P3B_B2B.md)。
后续 P3b-b2k 又把当前 emit completion 延长到完整 Worker output pipeline 与
ACK-backpressured framed egress 的最后一个 chunk ACK；本文件仍只记录
P3b-b2a 的 phase-entry/renderer-loss 证据，不能把该历史 spin 证据解释为自然
egress timeout 或 instruction-level `builder.emit` interruption。

这里的 phase-entry spin 是确定性测试 seam。它不是自然 TypeScript 工作负载在
某一阶段中途被打断的证据，也不能代替 PSS、LMK 或 OOM 测量。

## 严格 phase protocol

Worker、page 和 native host 共同接受唯一顺序：

```text
ingress started
  -> framed input
ingress completed
  -> parse started
  -> createIncrementalProgram + syntactic/global diagnostics
parse completed
  -> semantic started
  -> per-SourceFile semantic diagnostics
semantic completed
  -> emit started
  -> emit decision / builder.emit / output validation and hashing
  -> result JSON encoding + digest
  -> projectResultStart / ACK-backpressured result chunks
  -> final result-chunk ACK
emit completed
  -> projectResultEnd
  -> native result finalizer (outside emit)
```

每个事件都携带 exact `requestId`、phase 与
`started|completed` state。page 还以 Worker generation、transport state、
下一期望 phase 和当前 active phase 复核事件；native host 再以 pending
operation handle、session 与 Worker generation 关联。phase 缺失、重复、
越序或错误 state 都会 fail closed，不能成为 terminal success。result start
只能在 exact emit active 且 Worker-side output 构造已完成时出现；emit 必须保持
active 穿过 stop-and-wait result transfer，最后一个 chunk ACK 后 completed，
`projectResultEnd` 再随后到达。

`ingress started` 在 `projectInputStart` ACK 前发布，`ingress completed` 在
`projectInputEnd` ACK 前发布，因此该窗口覆盖真实 framed input。parse、
semantic、emit 的 `started` 在对应 TypeScript 工作前发布。parse/semantic 的
`completed` 在对应调用返回后发布；emit 还覆盖 output validation、hashing、
serialization 与 ACK-backpressured framed egress，并在最后一个 chunk ACK 后、
`projectResultEnd` 前 completed。native typed result finalizer 位于 emit phase
之外。该边界证明的是完整输出 pipeline 的 active window，不是
instruction-level `builder.emit` 抢占语义。

## DEBUG phase-entry hard stop

framer 的内部 start frame 现在总是带
`workerSpinPhase`。production path 固定发送：

```text
workerSpinMilliseconds = 0
workerSpinPhase = none
```

只有 same-UID、`BuildConfig.DEBUG` 私有 control 才能同时启用非零
`workerSpinMilliseconds` 与 exact phase；两者必须一起启用或一起关闭。
public-control 构造 invariant 禁止该字段，release parser/host 也不能启用它。
它没有成为 AIDL、冻结 Bundle schema 或 public capability。

命中配置 phase 时，Worker 在发出 `started` 后、进入该 phase 的正常工作前执行
有界同步 spin。instrumentation 等待 native 观察到 exact active phase 后：

- cancel 以 exact operation handle 请求 keyed Worker termination；
- timeout 由同一 absolute deadline 请求 keyed Worker termination；
- terminal 只能在 replacement Worker 的 exact `ready` ACK 或 exact session
  teardown proof 后发布；
- 每次 terminated Worker generation 必须严格递增；
- 八个 fault case 完成后，小型 successor 必须成功。

这证明四个入口都能被 host 的硬停机机制覆盖。由于停机点位于
`started` 后的 synthetic spin，它不证明零 spin 的真实
`createIncrementalProgram`、semantic traversal 或 emit 已在运行途中被抢占。

## Active exact-64-MiB renderer loss

API 35 instrumentation 使用 P3b-b1 的 private expanded capacity：

- caller input 为 exact 67,108,864 bytes；
- trusted TypeScript declarations 为 3,784,758 bytes；
- Worker total 为 70,893,622 bytes；
- target phase 为 `semantic`，并使用同一个 DEBUG phase-entry spin 保持可观测
  active window。

测试在 ingress 期间通过 compiler client PID 关联并记录 renderer 的
`(pid, /proc/<pid>/stat starttime)`，随后等待同一 operation 到达 exact active
semantic phase。termination helper 只有在 operation handle、session、WebView
与 expected phase 全部仍匹配时才调用 renderer `terminate()`。

renderer-gone callback 记录：

- request ID；
- callback 时的 active phase；
- termination 请求时的 expected phase；
- `RenderProcessGoneDetail.didCrash()`；
- `rendererPriorityAtExit()`；
- monotonic observation timestamp；
- exact session generation 与 Worker generation。

该运行要求 terminal 为 `ERROR_COMPILER_UNAVAILABLE` 且没有 compilation payload，
旧 `(pid,starttime)` identity 必须消失。随后一个小型 successor 在新的 renderer
identity 中成功，且成功退休的 session generation 必须大于 fault session。
capacity snapshot/spool 在退出后关闭并检查无残留。

当前实测 `didCrash=false`。这只描述 WebView 对本次主动 termination callback
的分类，**不表示**测试触发了 LMK、OOM 或系统内存压力。

## 设备证据

定向 instrumentation 均使用 working-tree debug build：

| serial | API / WebView | 证据 | 结果 |
| --- | --- | --- | --- |
| `emulator-5556` | API 24 / Chrome `119.0.6045.194` | ingress/parse/semantic/emit 各一次 cancel、各一次 timeout、small recovery | PASS，`27.591 s`，最后 terminated Worker generation `8` |
| `968e9f18` | API 35 / Android System WebView `130.0.6723.86` | 同一四阶段 phase-entry matrix 与 small recovery | PASS，`24.794 s`，最后 terminated Worker generation `8` |
| `968e9f18` | API 35 / Android System WebView `130.0.6723.86` | active exact-64-MiB semantic renderer termination、old identity exit、different successor identity/recovery | PASS，`82.492 s`；project terminal `elapsedMs=70550` |

最终 active run 的旧 renderer identity 为 `(pid=5530,starttime=97989470)`，
successor 为 `(pid=5788,starttime=97996568)`；callback 报告
`didCrash=false`、`rendererPriorityAtExit=2`。这些 PID 只与该次运行的 procfs
starttime 配对才有意义，不是跨运行常量。

同一最终 artifact 上，Binder
`admissionDeadlineStopsWorkerAndPublishesOnlyAfterEmptyOutput` 与
`cancelOwnsFullPipelineAndSharedSlotUntilEmptyOutputProof` 也分别在 API 24/35
通过：两项合并耗时为 `3.046 s` / `1.497 s`。

本切片没有访问 `QV710AF65F`。

复现 phase-entry matrix：

```powershell
adb -s <serial> shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest#phaseEntryHardStopCoversCancelAndTimeoutAcrossAllWorkerPhases' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner
```

API 31+ designated renderer-capable device 上复现 active renderer loss：

```powershell
adb -s <serial> shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest#activeLargeProjectRendererTerminationIsCorrelatedAndRecovers' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner
```

## Source/build gates

JVM 与 desktop gates 覆盖：

- spin milliseconds/phase 必须成对，public request 不可携带 phase；
- production start frame 始终为 `0 + none`；
- strict phase parser 拒绝 unknown state；
- Worker/page golden 要求四个 phase 的 exact started/completed 顺序，且 emit
  completed 严格位于 final result-chunk ACK 与 `projectResultEnd` 之间；
- 只有 exact active emit 可以跨越 result start；result finalizer 不能越过未完成
  phase；
- release APK 不得保留 DEBUG phase-control marker。

定向 Kotlin compilation、app JVM、desktop TypeScript `6.0.3` golden、
debug/release APK source/binary policy 与根
`verifyR2PublicPreviewFixture` 已全部通过。最终根 aggregate 为
`BUILD SUCCESSFUL in 17s`（485 tasks：20 executed、465 up-to-date）；
其中 4,607-file desktop golden 为 `984 ms`，retained heap
baseline/boundary/replay 为 `22.6/23.7/24.0 MiB`。同一轮 lint、debug marker
与 release marker/APK gate 均通过。

最终设备 artifact SHA-256：

```text
app debug:
2b99087a92ecb383a28ff22cffc167e4fc5862b274dc446e0dd60b95c818d4c3
androidTest debug:
22ab8b90a256b708d363da1a3202139d43054dd9b8d16dc84988474e36451135
app minified release:
b08718d2c7737707a6bac516ff737bf62748c4543aba40380b4ffdd1ddb1e295
```

## 截至 P3b-b2b 尚未关闭的 P3b-b / Public blockers

P3b-b2a 只关闭 same-UID DEBUG synthetic phase-entry hard-stop 与一次主动
active renderer-loss/recovery infrastructure。P3b-b2b 随后加入 repeated
exact-64-MiB compiler-service/renderer PSS、稳定 FD target multiset、transient
spool/cursor/Worker successor cleanup，以及 startup recognized orphan
scavenging；后者无法证明枚举、owner-write 恢复、删除或复核时，会在
host/coordinator 创建前 fail closed。至少仍需：

- external-UID debug 与 minified-release valid 64 MiB full-pipeline matrix；
- 零 spin、高工作量项目在自然 parse/semantic/emit **中途**的 cancel/timeout
  与 cleanup 证据；P3b-b2b 的自然 cancel 只覆盖 ingress；
- disposable-device 的真实 LMK/OOM 与 successor recovery；主动
  `renderer.terminate()`、`didCrash=false` 不关闭这一项；
- Unicode/high-token 合法与 adversarial distributions；
- 128 MiB container/256 MiB expanded file-backed output writer；
- 正式 archive/manifest/diagnostics schema、tsconfig/NodeNext 与其余 R2
  exit gates。

因此 Android production admission 继续为 512 files、8 MiB/file、8 MiB total；
`compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、
`sourceMapV3` 四项 capability 继续为 `false`，Public R2 checklist 继续为
**0/54**。

后续 P3b-b2d 已用 non-shipping TEST_ONLY evidence build 关闭 external
exact-64 四边界；P3b-b2e 又在 API 24/35 以 ordinary debug 关闭三轮 fixed-5s
partial-ingress natural timeout，并补 Binder fixed-10s end-to-end natural
deadline/final cleanup。它只关闭上列自然 ingress timeout 子项，不改变本切片的
历史结论；b2e 当时使 internal provisional evidence 达到 **20/20**，remote
plus-one cleanup 当时仍未证明。后续 P3b-b2f 已关闭该 plus-one cleanup
窄缺口，使当时的 internal provisional evidence 达到 **21/21**。后续
P3b-b2g 又在 API 24/35 ordinary same-UID Debug 关闭零 spin 自然
parse/semantic/emit cancellation 窄矩阵，当时为 **22/22**。P3b-b2h/b2i 随后
分别关闭 ordinary same-UID Debug 的自然 semantic/parse request-start timeout；
P3b-b2j 的 ordinary emit timing 是 pre-result-egress completion boundary 的历史
证据。P3b-b2k 以 256 roots / 364 aggregate files、emit sequence-2
`128/512`、512 outputs / `976,438` bytes 与 SHA-256
`2b2a4529552aa3a3f9b33cd2a379240bc2852ca065a33ff884a6160847f1b1de`，在
API 24/35 × external-UID Binder evidence Debug/Release-equivalent 四格全部通过，
把当前 internal provisional evidence 推进到 **26/26**。external-UID Binder
自然 parse/semantic timeout、normal/shipping Release 自然 parse/semantic/emit
timeout、真实 LMK/OOM、Unicode/high-token/adversarial、output 128/256 MiB、
formal schemas/NodeNext 与其余 exit gates 仍开放。
完整 b2e 边界见
[R2-P3b-b2e Natural Ingress Timeout and Cleanup](WORKER_NATURAL_INGRESS_TIMEOUT_R2_P3B_B2E.md)；
b2f 见
[R2-P3b-b2f External Plus-one Cleanup Receipt Evidence](EXTERNAL_PLUS_ONE_CLEANUP_R2_P3B_B2F.md)；
b2g 见
[R2-P3b-b2g Natural Parse/Semantic/Emit Cancellation](WORKER_NATURAL_PHASE_CANCELLATION_R2_P3B_B2G.md)。
