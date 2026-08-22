# R2-P3b-b2g Natural Parse/Semantic/Emit Cancellation

日期：2026-07-31

状态：internal provisional evidence **22/22**；Public R2 **0/54**。本切片只关闭
ordinary same-UID Debug direct-host 的自然 parse/semantic/emit **取消**窄缺口；
不证明这些阶段的自然 deadline/timeout，也不启用任何公开 capability。

> 后续进展：R2-P3b-b2h 已在相同 ordinary same-UID Debug direct-host 边界内
> 单独关闭自然 semantic request-start absolute-deadline timeout，使当前 internal
> provisional evidence 达到 **23/23**；parse/emit timeout 仍开放。参见
> [R2-P3b-b2h Natural Semantic Request-start Absolute-deadline Timeout](WORKER_NATURAL_SEMANTIC_TIMEOUT_R2_P3B_B2H.md)。
> 本文继续保留 P3b-b2g 当时的 **22/22** cancellation 证据语义。

## 证明目标

P3b-b2a 的 phase-entry spin 能精确测试四阶段入口 hard-stop，但它是 DEBUG-only
synthetic seam。P3b-b2g 不使用 spin、timeout、expanded capacity authority 或新的
start-frame evidence flag，而是在普通编译工作真实推进后取消：

- `workerSpinMilliseconds=0`、`workerSpinPhase=none`；
- 使用 ordinary production-admission envelope 内的 300-file workload；
- parse、semantic、emit 各等待第二个真实 work-unit progress marker，再调用
  `requestStop`；
- page 必须终止被 native 选中的 exact Worker，native 必须收到同一
  request/session/Worker/phase/progress receipt；
- 取消后 cursor、Worker、host 与 spool 可证明清理，小项目 successor 成功。

这里的证据语义是：page 在调用 exact `Worker.terminate()` 前最后观察到 sequence 2，
且当时尚未接受该 phase 的 completed event。它不声称能观测 TypeScript 内部某条
JavaScript 指令的瞬时执行位置。

## 冻结工作负载

`R2NaturalPhaseWorkloadFixture` 与 TypeScript 6.0.3 desktop golden 使用相同的确定性
生成规则：

| 项目 | 冻结值 |
|---|---:|
| trusted declarations | 108 files |
| source roots | 192 files |
| aggregate files | 300 |
| aggregate UTF-8 bytes | 4,594,999 |
| scanner tokens | 232,249 / 262,144 |
| max source-root tokens | 760 |
| diagnostics | 0 |
| emitted JavaScript | 192 files / 290,497 bytes |
| emitted Source Maps | 192 files / 272,448 bytes |
| aggregate output | 384 files / 562,945 bytes |
| output aggregate SHA-256 | `2f4dc949984424bb6eff1d806efcac2fd21f4ae8ad87a0c44f03f2c086d1444d` |

source path 固定为 `/sources/natural-phase-%03d.ts`。每个 root 含一个
`NaturalRecord` interface、16 个 exported runtime constants、46 个 exported type
aliases 和一个 selector function。baseline 必须先完整编译并精确重验上述
diagnostics、output inventory、bytes 与 aggregate digest，之后才允许执行 fault
matrix。

## 有界 progress 与终止关联

Worker 对 parse、semantic、emit 各最多发布两条内部
`projectPhaseProgress`：

- sequence 1：真实完成 `ceil(totalUnits / 8)` 个 work units 后；
- sequence 2：真实完成 `ceil(totalUnits / 4)` 个 work units 后；
- 小项目可以不发布 progress；
- semantic error 导致 emit 被跳过时，不伪造 emit progress。

本 workload 的 parse/semantic `totalUnits=192`，所以 sequence 2 为 `48/192`；
emit 以 384 个真实 output units 计数，sequence 2 为 `96/384`。page 在同一个
JavaScript task 中冻结最后观察到的 progress，再调用 exact `Worker.terminate()`，
并把 receipt 放入 `workerTerminated`。native `requestStop` 在锁内冻结
request/session/Worker/phase/progress selection；只有 page receipt 与该 selection
逐项一致且 sequence 为 2 时，才发布
`R2ProjectPhaseTerminationEvidence`。旧 cancellation/timeout 没有自然 progress
时继续使用 `none/0/0/0`，协议保持向后兼容。

这组 progress 是 bounded internal telemetry，不是 public protocol，也不改变
ordinary admission。source/APK gate 明确禁止 expanded authority、spin/timeout
注入或 start-frame bypass。

## Android fault matrix

每台设备先运行一次完整 baseline，再依次执行 parse、semantic、emit：

1. 等待目标 phase 的 sequence 2；
2. 调用 keyed `requestStop`，结果必须为 `CANCELLED`；
3. 校验 page receipt 与 native selection 完全相同，且该 phase 尚未 completed；
4. 校验 300 个 cursor 全部 opened/closed、snapshot released；
5. 校验 exact Worker generation 被终止并由更新 generation 替换；
6. 运行小项目 successor，并证明 newer session retirement；
7. 最终关闭 host，证明 host cleanup 与 project spool cleanup。

| 设备 | Provider | parse | semantic | emit | 总时间 |
|---|---|---:|---:|---:|---:|
| API 24 / `emulator-5556` | Chrome `119.0.6045.194` | `48/192`, gen `1→2` | `48/192`, gen `2→3` | `96/384`, gen `3→4` | `32.635 s` |
| API 35 / `968e9f18` | WebView `130.0.6723.86` | `48/192`, gen `1→2` | `48/192`, gen `2→3` | `96/384`, gen `3→4` | `66.169 s` |

六个 cancellation case 全部记录
`NATURAL_PHASE_CANCEL_PASS`，两行最终矩阵都记录
`NATURAL_PHASE_CANCEL_MATRIX_PASS`；每案均为 `openedCursors=300`、
`closedCursors=300`、`released=true`，最终均为
`ordinaryAdmission=true`、`workerSpinMilliseconds=0`、
`workerSpinPhase=none`、`hostCleanupProven=true` 与
`spoolCleanupProven=true`。

设备命令固定显式 serial：

```powershell
adb -s emulator-5556 shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest#naturalParseSemanticEmitCancellationUsesRealProgressAndRecovers' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner

adb -s 968e9f18 shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest#naturalParseSemanticEmitCancellationUsesRealProgressAndRecovers' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner
```

API 35 的第一次 preflight 曾把新 androidTest APK 与旧主应用 APK 配对，因缺少新
runtime class 在初始化期返回 `NoClassDefFoundError`；这不是自然阶段运行失败。
重建并安装同一次构建的主包/测试包后最终运行通过。为关闭该 artifact-pairing
缺口，`verifyR2P3bB2gNaturalPhaseCancellationAndroidTestApk` 现在同时依赖
`assembleDebug` 与 `assembleDebugAndroidTest`，并分别检查主包 runtime DEX 与
测试包 fixture DEX marker。

## 本地门禁

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileReleaseKotlin `
  :app:compileDebugAndroidTestKotlin :app:verifyR2ProjectCompilerDesktop --stacktrace

.\gradlew.bat :app:verifyR2P3bB2gNaturalPhaseCancellationSourcePolicy --no-daemon
.\gradlew.bat :app:verifyR2P3bB2gNaturalPhaseCancellationAndroidTestApk --no-daemon
.\gradlew.bat verifyR2PublicPreviewFixture
```

desktop golden 使用 TypeScript 6.0.3 scanner 与完整 compile 冻结 workload，
同时覆盖 page progress/termination receipt correlation。source policy 锁定
Worker/page/native protocol、ordinary workload 与 Android assertions；成对 APK
gate 验证最终 DEX evidence shape。APK gate 本身不运行设备。最终根
`verifyR2PublicPreviewFixture` 为 `BUILD SUCCESSFUL in 2m 2s`
（610 actionable tasks：110 executed、500 up-to-date）。

## 尚未证明

P3b-b2g 不证明：

- 自然 parse/semantic/emit absolute deadline/timeout；
- external-UID、Binder 或 shipping Release 的自然阶段取消；
- renderer LMK/OOM、force-stop 或其他 system fault；
- repeated large-job PSS/FD plateau；
- Unicode/high-token/adversarial 分布；
- 128 MiB container / 256 MiB expanded file-backed output；
- formal request/output/diagnostic schemas 或 tsconfig/NodeNext。

以上列表描述 P3b-b2g 自身没有证明的边界。后续 P3b-b2h 只补 natural semantic
timeout，不回写本切片的 cancellation 方法、设备结果或 **22/22** 历史状态，也
不关闭 parse/emit、external/Binder/Release 或 system-fault 缺口。

因此普通 Debug/Release 继续执行 **512 files、8 MiB/file、8 MiB total**
production fence；`compilerAvailable`、`projectCompilation`、
`semanticDiagnostics`、`sourceMapV3` 继续全部为 `false`；Public R2 checklist
继续为 **0/54**。P3b-b2g 是 internal provisional cancellation evidence，不是
Public R2 开放声明。
