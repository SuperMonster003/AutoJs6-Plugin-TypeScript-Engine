# R2-P3b-b2e Natural Ingress Timeout and Cleanup Evidence

状态：内部 `DEBUG` 自然超时与最终清理证据切片；不是完整 R2-P3b-b，也不开放
Public R2。

P3b-b2e 在不注入 Worker spin、不调用 `requestStop`、不放宽普通 production
admission 的前提下，补上 direct host 与真实 Binder 两条固定 deadline 路径：

- direct host 对 exact 64 MiB caller source 加 trusted declarations 后的完整
  `70,893,622`-byte input 连续执行三轮固定 `5,000 ms` 自然 ingress timeout；
- Binder 对 STORED exact `67,108,864`-byte container 发送 post-return public
  ACK，再等待固定 `10,000 ms` deadline 产生 exactly-once timeout terminal；
- 两条路径都在 timeout 后完成 small successor，证明 host/service 仍可恢复。

本切片使 internal provisional checklist 达到 **20/20**。Public R2 checklist
仍为 **0/54**；`compilerAvailable`、`projectCompilation`、
`semanticDiagnostics`、`sourceMapV3` 四项 capability 仍全部为 `false`。

## Direct exact-64 自然 ingress timeout

`repeatedExact64NaturalIngressTimeoutReleasesCursorAndRecovers` 构造 exact
`67,108,864`-byte caller source boundary；加入 plugin trusted declarations 后，
transport 总量固定为 `70,893,622` bytes。测试先完成 small baseline，然后连续
三轮执行：

```text
expandedWorkerCapacity=true
timeoutMs=5,000
workerSpinMilliseconds=0
workerSpinPhase=null
requestStop=never
```

每轮都必须在 live telemetry 中观察到 `INGRESS` phase、active cursor、已接受
bytes 大于零但小于总量，并且 input 尚未 complete/released。deadline 到期后必须
返回 `ERROR_TIMEOUT`、没有 compilation payload，且 elapsed time 不早于
`5,000 ms`。terminal telemetry 随后证明：

- input 仍未 complete；
- cursor opened/closed accounting 平衡；
- `activeCursor=false` 且 framer `released=true`；
- terminated Worker generation 严格前进，三轮依次为 `1 / 2 / 3`；
- exact caller snapshot 关闭后没有 capacity spool residue。

三轮结束后，small successor 必须成功并退休更新的 WebView project session；
最终 `host.close()` 还必须返回 `cleanupProven=true`，再次确认没有 capacity
spool residue。这条 direct 路径才是 P3b-b2e 对“真实 partial ingress”的证明。

## Binder exact-container deadline 与最终清理

`exact64NaturalDeadlinePublishesTimeoutCleansBinderStateAndRecovers` 先完成 small
warm baseline，并冻结 compiler service 的 exact `(pid,starttime)` identity。
随后它创建
`R2CapacityArchiveBoundary.STORED_CONTAINER_INCLUSIVE` fixture，container 精确为
`67,108,864` bytes，以固定 `10,000 ms` timeout 启动真实 Binder compilation。
accepted ticket 返回后，caller 发送与 request ID/job token 绑定的
`publicAcknowledgement`；测试不请求 cancellation，也不注入 Worker spin。

terminal 必须满足：

- callback 不得发生在 `startCompilation` 返回前；
- `OUTCOME_TIMED_OUT + ERROR_TIMEOUT`，`succeeded=false`；
- diagnostic count 与 emitted module count 都为零；
- 没有 output manifest digest，output FD 最终为 `0` bytes；
- terminal elapsed 与 wall elapsed 均不早于 `10,000 ms`；
- 经过 quiet window 后 callback count 仍精确为一。

terminal 返回后，测试继续等待并证明 active job count 为零、app-private named
input/spool/output transient 全部消失、compiler process 的 remote transient FD
为零且 exact process identity 不变。随后 small Binder successor 必须
`OUTCOME_EMITTED`，并再次通过相同的 active-job、named transient、remote FD 与
process identity 检查。

这条 Binder 单项可能在 staging、validation、spool 或 host ingress 任一尚未完成
的位置达到 absolute deadline；它没有暴露 direct host 的 phase/cursor telemetry，
因此不能据此单独声称 Binder case 本身已证明 partial ingress。它证明的是
exact-container accepted + public ACK 后的自然 deadline、exactly-once timeout
terminal，以及 terminal 之后的**最终**清理与 successor 恢复；它也不单独声称
cleanup-before-callback ordering。

## 最终 API 24/35 设备证据

两个设备使用同一组最终 normal debug app 与 debug androidTest artifact。没有
访问其他设备。

| 设备 / provider | direct 三轮 accepted bytes / runner | Binder terminal / wall / runner |
| --- | --- | --- |
| `emulator-5556` / API 24 / Chrome `119.0.6045.194` | `12,976,128 / 13,893,632 / 14,024,704`；Worker `1 / 2 / 3`；`OK (1 test)` / `20.131 s` | terminal `10,338 ms`；wall `10,358 ms`；`OK (1 test)` / `24.704 s` |
| `968e9f18` / API 35 / WebView `130.0.6723.86` | `3,604,480 / 3,538,944 / 4,259,840`；Worker `1 / 2 / 3`；`OK (1 test)` / `19.218 s` | terminal `10,324 ms`；wall `10,333 ms`；`OK (1 test)` / `37.831 s` |

六轮 direct accepted bytes 均大于零且严格小于 `70,893,622`，并在 terminal 后
通过 cursor balance/release、Worker replacement、spool cleanup、small successor
和 host close cleanup proof。两轮 Binder 均通过 public ACK、fixed-deadline
timeout、零 output、exactly-once、最终 remote/local transient cleanup、process
identity 与 successor 检查。

最终候选 artifact：

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| normal debug app APK | 5,089,165 | `83fca363539ae0a4b8e0fc1562d81b4e92b97d1230c2ecfc384b13f157862168` |
| debug androidTest APK | 1,491,255 | `696197b5158c3adb34287cd84ef309f228de5560d172535952d819f25257a26a` |

## 本地门禁与精确设备方法

不运行设备的两条定向 gate：

```powershell
.\gradlew.bat :app:verifyR2P3bB2eNaturalIngressTimeoutSourcePolicy
.\gradlew.bat :app:verifyR2P3bB2eNaturalIngressTimeoutAndroidTestApk
.\gradlew.bat verifyR2PublicPreviewFixture
```

最终根 aggregate 为 `BUILD SUCCESSFUL in 1m 7s`（606 actionable tasks：
82 executed、524 up-to-date）；它包含 b2e 两项 gate、JVM/desktop golden、
debug/release APK、lint、R1 compatibility 与 public fixture，但不替代下方
显式设备执行。

两台允许设备分别执行下列两个精确 instrumentation 方法：

```powershell
adb -s emulator-5556 shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest#repeatedExact64NaturalIngressTimeoutReleasesCursorAndRecovers' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner

adb -s emulator-5556 shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2BinderCompilationInstrumentationTest#exact64NaturalDeadlinePublishesTimeoutCleansBinderStateAndRecovers' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner

adb -s 968e9f18 shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest#repeatedExact64NaturalIngressTimeoutReleasesCursorAndRecovers' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner

adb -s 968e9f18 shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2BinderCompilationInstrumentationTest#exact64NaturalDeadlinePublishesTimeoutCleansBinderStateAndRecovers' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner
```

## 尚未证明

P3b-b2e 只关闭 exact-64 自然 ingress timeout 与上述最终清理矩阵。它不证明：

- production-fence 或 exact-64 `+1` 的 remote transient
  FD/spool/cursor/process cleanup；
- 零 spin 的自然 parse/semantic/emit 中途 cancel/timeout；
- disposable-device 的真实 LMK/OOM、dual process death 或 allocator reclaim；
- Unicode/high-token/adversarial distribution；
- 128 MiB container / 256 MiB expanded file-backed output writer；
- 正式 archive/manifest/diagnostics schema、tsconfig/NodeNext 与其余 R2 exit
  gates。

因此普通 Debug/Release 的 production fence 仍为 **512 files、8 MiB/file、
8 MiB total**；四项 capability 必须保持 `false`，Public R2 必须保持
**0/54**。

## 后续 P3b-b2f

本页保留 b2e 当时的历史结论：internal provisional evidence 为 **20/20**，
而 production-fence/exact-64 `+1` 的 remote transient
FD/spool/cursor/process cleanup 当时仍未证明；上文也没有声称 b2e 的 Binder
case 在 callback 前完成 cleanup。

后续
[R2-P3b-b2f External Plus-one Cleanup Receipt Evidence](EXTERNAL_PLUS_ONE_CLEANUP_R2_P3B_B2F.md)
以 API 24/35 normal/evidence Debug/Release 的 8/8 matrix 关闭该窄缺口：
scope-free ordinary/STORED/DEFLATED `+1` rejection 中，receipt 在 callback
前创建，fixture 只在 callback 后读取并关联 receipt，随后连续 5 次 current
snapshot 全零且 same-Binder successor 成功。b2f 完成时 internal provisional
evidence 为 **21/21**。再后续 P3b-b2g 在 API 24/35 ordinary
same-UID Debug 关闭零 spin 自然 parse/semantic/emit cancellation 窄矩阵，
当前为 **22/22**；这些阶段的自然 timeout 仍未证明。完整 b2g 边界见
[R2-P3b-b2g Natural Parse/Semantic/Emit Cancellation](WORKER_NATURAL_PHASE_CANCELLATION_R2_P3B_B2G.md)。

这不改变普通 Debug/Release 的 **512 files、
8 MiB/file、8 MiB total** production fence、四项 `false` capability 或
Public R2 **0/54**。
