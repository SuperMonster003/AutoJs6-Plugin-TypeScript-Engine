# R2-P3b-b2b Repeated Resource and Startup Orphan Cleanup

状态：内部 `DEBUG` 资源/清理证据切片；不是完整 R2-P3b-b，也不是
Public R2。

P3b-b1 已证明 exact 64 MiB caller input 可以通过 direct host 与 Binder
pipeline；P3b-b2a 又证明 synthetic phase-entry hard stop 和一次 active
renderer loss。P3b-b2b 在不放宽 production admission 的前提下补上三类证据：

- 同一 compiler-service epoch 连续完成三次 exact-64-MiB Binder success，
  每轮等待 active jobs、私有 staging 名称和远端 transient FD 清零，再对
  exact `(pid,starttime)` 采样 PSS 与完整 FD target multiset；
- 对同一 exact-64-MiB file-backed snapshot 连续三次执行零 spin 的自然
  ingress cancellation，证明 active cursor 释放、opened/closed 平衡、
  replacement Worker generation 前进和 small successor 恢复；API 35 同时采样
  独立 renderer PSS；
- compiler service 冷启动在 admission 开放后、host/coordinator 创建前清理上一
  process epoch 遗留的 recognized private input/spool/output 文件；任何清理证明
  失败都先 `failClosed()`，不会继续接单。

这些证据不改变 production 的 **512 files、8 MiB/file、8 MiB total** Worker
eligibility fence。`compilerAvailable`、`projectCompilation`、
`semanticDiagnostics`、`sourceMapV3` 四项 capability 继续全部为 `false`，
Public R2 checklist 继续为 **0/54**。

## Cursor、Worker 和 teardown proof

`R2ProjectInputTransportTelemetry` 现在除 frame/byte accounting 外还记录：

- exact `requestId`；
- `openedCursorCount` / `closedCursorCount`；
- terminal 前是否仍有 `activeCursor`；
- framer 是否已 `released`。

framer 在每次 cursor open/close、正常完成、失败和显式 release 上更新同一份
accounting。host 暴露的 live telemetry 只会返回 exact pending handle；terminal
测试要求 `openedCursorCount > 0`、opened 等于 closed、`activeCursor=false` 且
`released=true`。自然 ingress 测试不使用 `workerSpinMilliseconds` 或
`workerSpinPhase`：它等待至少 8 MiB payload 已被接受且 cursor 仍 active，然后
对 exact handle 发出 `CANCELLED`。每轮必须得到无 compilation payload 的
`ERROR_CANCELLED`、未完成 input、更新的 terminated Worker generation，并在三轮
后完成小型 successor。

session teardown 也由“尝试过”收紧为同时证明 native port closed 与
`WebView.destroy()`/renderer-gone。若 port、WebView、input executor 或 main
handler 无法给出 exact release proof，host 保留 exact pending/session 引用并通知
service 的 external-teardown observer；observer 立即 poison process-wide
admission。coordinator shutdown 同样要求 protocol/result executor 都已终止，
callback death recipient 的 unlink 必须由成功返回、callback 已死亡或 Binder
已经不存活来证明。

## PSS 与 FD 探针

单进程 PSS batch 固定为 5 个样本。每个 `dumpsys meminfo` 样本前后都重新核对
exact `(pid,starttime)`，中位数用于 retained-growth gate，最大值保留为诊断信息。
parser 同时接受 Android 7 表格 `TOTAL <number> ...`、`TOTAL: <number>` 与
`TOTAL PSS: <number>`；整行锚定，不会误取 `TOTAL SWAP PSS`。非法逗号分组、
溢出、无候选或不一致的多候选均 fail closed。

远端 FD probe 不再只比较数量。一次 quiescent snapshot 必须：

- 连续 5 次、每次间隔 100 ms，得到完全相同的 sorted target multiset；
- sampling 前后保持 exact compiler process identity；
- 不含 R1 archive 或 R2 private input、project spool、private output、
  caller/capacity input/output transient target，包括带 ` (deleted)` 的 inode。

warm 与首个成熟周期之间只允许一个完整的、无 replacement 的 exact
lazy-initialization pair：`eventpoll + eventfd`，或 Android 7 Chrome 针对自身
PID 的 `/proc/<pid>/{statm,status}`。缺半对、额外 target、baseline target 被替换
或成熟周期 1/2/3 之间发生任何 target churn 都失败。

## Startup orphan scavenger

`R2PrivateTransientOrphanScavenger` 非递归扫描两个 app-private 目录，只识别：

| 目录 | 精确 namespace |
| --- | --- |
| `typescript-r2-input-staging` | `r2-private-input-*.staging` |
| `typescript-r2-input-staging` | `r2-project-vfs-*.spool` |
| `typescript-r2-output-staging` | `r2-private-output-*.zip.staging` |

普通 unknown/near-match 文件、recognized-name 目录及其嵌套内容都不会删除。
当前实现以 `File.isFile` 分类 recognized child；分类为文件后会先恢复
owner-write，再删除，并通过第二次目录枚举证明没有残留。初始枚举、分类、
权限恢复、删除或 post-cleanup 枚举失败都会抛
`R2PrivateTransientCleanupUnprovenException`。

service 只在 durable quarantine 不存在、process admission epoch 已打开且未
poison 后执行 scavenger。异常路径先 `admissionArbiter.failClosed()`，随后让
service 初始化以 cleanup-unproven 失败；正常路径必须在
`prepareCompilerProcess()`、host 与 coordinator 创建前完成。

JVM 的 7-test matrix 覆盖 strict namespace、非递归、sentinel/目录保留、只读
文件、幂等，以及 initial list、delete、recheck 和“伪删除成功但 residue 仍在”
的 fail-closed 路径。独立 Android test 又建立并终止真实 compiler predecessor，
放置三类只读 orphan，再证明不同 successor epoch 清除它们、保留 unknown/目录/
nested fixture，并完成远端 Binder metadata 与真实 prewarm。

## 最终设备证据

最终 working-tree artifact 未创建 commit，也未访问 `QV710AF65F`。

### 三轮 Binder exact-64-MiB success

每轮输入都是 STORED container inclusive boundary：

- container：67,108,864 bytes；
- uncompressed caller payload：67,107,642 bytes；
- accepted ticket → public-shaped ACK → committed emitted output；
- callback exactly once；
- terminal 后 active jobs 为 0，named private transient 与 remote transient FD
  均为 0；
- 三轮后完成 small successor，compiler `(pid,starttime)` 不变。

| 设备 / provider | compiler identity | warm PSS median KiB | cycle 1/2/3 median KiB | mature FD | runner |
| --- | --- | ---: | --- | ---: | ---: |
| API 24 AVD / Chrome `119.0.6045.194` | `(1923,24020567)` | 61,402 | 130,416 / 124,394 / 125,291 | 127 / 127 / 127 | `OK (1 test)` / 139.605 s |
| API 35 `968e9f18` / Android System WebView `130.0.6723.86` | `(19544,98366926)` | 91,525 | 93,041 / 93,369 / 93,712 | 214 / 214 / 214 | `OK (1 test)` / 353.825 s |

API 24 的 Chrome 119 使用单进程 WebView，没有独立 sandbox ServiceRecord；
首个 64 MiB 工作集因此让 compiler-service 从 small warm 上升 69,014 KiB。该平台
使用 96 MiB first-load envelope，但第 2/3 轮仍必须相对成熟 cycle 1 不增长超过
32 MiB；实测 cycle 3 反而低 5,125 KiB。API 35 保持 48 MiB first-load envelope，
cycle 3 相对 cycle 1 只增加 671 KiB。

### 三轮零 spin 自然 ingress cancel

| 设备 / provider | 每轮 cursor | accepted payload bytes | Worker generations | renderer median PSS KiB | runner |
| --- | --- | --- | --- | --- | ---: |
| API 24 / Chrome `119.0.6045.194` | opened/closed `1/1` | 8,388,608 / 8,388,608 / 8,388,608 | 1 / 2 / 3 | 单进程 provider，无独立 renderer | `OK (1 test)` / 19.206 s |
| API 35 / WebView `130.0.6723.86` | opened/closed `2/2` | 9,699,328 / 9,240,576 / 9,502,720 | 1 / 2 / 3 | 153,021 / 147,886 / 154,175 | `OK (1 test)` / 39.363 s |

API 35 三轮都关联同一 renderer `(pid=21269,starttime=98405115)`；最后一轮中位
PSS 相对第一轮增加 1,154 KiB，低于 64 MiB envelope。每轮 terminal 都证明
input incomplete、cursor balanced/released、Worker generation 前进；矩阵末尾的
small compilation 成功且 exact project session 已退休。

### 冷 successor orphan cleanup

| 设备 | 结果 |
| --- | --- |
| API 24 AVD | `OK (1 test)` / 2.192 s |
| API 35 `968e9f18` | `OK (1 test)` / 1.252 s |

两端均实际终止 predecessor epoch，证明 successor identity 不同；三类只读
recognized orphan 消失，unknown/sentinel、三类 recognized-name 目录及嵌套文件
保持存在，随后 Binder contract metadata 与 prewarm 成功。

### Artifact 与 no-device aggregate

上述三组设备方法使用同一最终 app/androidTest 组合：

```text
debug app APK       5,894,411 bytes
SHA-256             88B8DA9A57AE2549F43F9E91072D8CE8E2B0A26753008ECCBEA085F85A6A42DE
androidTest APK     1,410,781 bytes
SHA-256             FFE6C1DE01CEBC3326C17E38D7AD87062454E991EA34D064EBBF7995DDBEB689
release app APK     2,642,199 bytes
SHA-256             FB7239F88074D7B734581A374D1664BD914EEDA0552E5B3419B3F28A7316E399
fixture debug APK     946,422 bytes
SHA-256             A8F98152A3F06F083955CEBE323FC4DE2833C04846E8FE76BE14EF20D996C089
fixture release APK   678,566 bytes
SHA-256             6B975CFADD2A654E90B7C8F5B346663BDC39012BB74D5532ABA65DB8FFFDF8B6
```

最终根 `verifyR2PublicPreviewFixture` 为 `BUILD SUCCESSFUL in 1m 6s`
（487 tasks：31 executed、456 up-to-date）。其中 b2b source policy、
AndroidTest APK marker、debug/release private-key stripping/presence 与 startup
scavenger production marker 全部通过；desktop TypeScript `6.0.3` 的
4,607-file ingress 为 938 ms，retained heap baseline/boundary/replay 仍为
22.6/23.7/24.0 MiB。app JVM 最终为 18 suites / 169 tests / 0 failures /
0 errors / 0 skipped；TypeScript API 为 3 suites / 16 tests / 0 failures /
0 errors / 0 skipped。根 aggregate 是 no-device gate，不替代上面的显式
instrumentation 结果。

## 构建与复跑命令

无设备门禁：

```powershell
.\gradlew.bat :app:verifyR2P3bB2bResourceSourcePolicy
.\gradlew.bat :app:verifyR2P3bB2bAndroidTestApk
.\gradlew.bat :app:verifyR2ProjectCompilerCore
.\gradlew.bat verifyR2PublicPreviewFixture
```

`verifyR2P3bB2bResourceSourcePolicy` 锁定 exact process identity/5-sample PSS、
完整 FD target multiset、三轮 exact64、cursor balance/release、Worker successor、
strict orphan namespace 与 service startup fail-closed 顺序。
`verifyR2P3bB2bAndroidTestApk` 只证明最终 test APK 包含这些 matrix；它明确不会
运行设备。

设备方法：

```powershell
adb -s <serial> shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2BinderCompilationInstrumentationTest#repeatedExact64BinderJobsReachCompilerPssFdAndTransientCleanupPlateau' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner

adb -s <serial> shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest#repeatedExact64NaturalIngressCancellationReleasesCursorAndBoundsRendererPss' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner

adb -s <serial> shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2PrivateTransientOrphanScavengerInstrumentationTest#coldSuccessorScavengesExactOrphansAndPreservesOtherChildren' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner
```

## 截至 P3b-b2e 仍开放的 P3b-b / Public blockers

P3b-b2b 关闭的是 same-UID `DEBUG` 下的重复低 token ASCII 大任务资源包络、
自然 ingress cancellation cleanup 和一次真实 cold-start orphan recovery。
终审另保留两项不推翻本切片设备结论的 hardening：`File.isFile` 会跟随符号链接，
后续应改为 no-follow classification，避免 recognized-name symlink 的
`setWritable` 作用到 staging 目录外的同 UID 目标；direct repeated test 的最终
`host.close()` 还应显式断言 `CompilerHostCloseReport.cleanupProven`，当前每轮
session/cursor release 与 small successor 已有独立断言。
后续
[R2-P3b-b2c External Production-fence Pipeline](EXTERNAL_FENCE_PIPELINE_R2_P3B_B2C.md)
已冻结 standalone same-signer、distinct-UID fixture 的 production-fence
invalid/inclusive/`+1` source/APK gate；其 API 24/35 debug 与 minified service
release runtime 也已全部 PASS。该后续切片不改变 b2b 的 same-UID `DEBUG`
历史证据；它本身也没有证明 external exact-64。再后续
[R2-P3b-b2d External Exact-64-MiB Capacity Evidence Pipeline](EXTERNAL_EXACT64_PIPELINE_R2_P3B_B2D.md)
已使用 non-shipping TEST_ONLY evidence debug 与 minified/non-debuggable
release-equivalent build 完成 external exact-64 source/APK/device matrix；
API 24/35 最终四行均已 PASS。再后续
[R2-P3b-b2e Natural Ingress Timeout and Cleanup](WORKER_NATURAL_INGRESS_TIMEOUT_R2_P3B_B2E.md)
已在 ordinary debug 的 API 24/35 关闭三轮 fixed-5s partial-ingress natural
timeout，并补 Binder fixed-10s end-to-end natural deadline/final cleanup，但仍
没有证明下列当时的缺口：

- production-fence plus-one remote transient FD/spool/cursor/process cleanup；
- 零 spin 的自然 parse/semantic/emit 中途 cancel/timeout，以及这些 fault path
  的完整资源矩阵；
- disposable-device 的 LMK/OOM、dual process death 或 allocator reclaim；
- Unicode/high-token/adversarial distribution；
- 128 MiB output container / 256 MiB expanded file-backed writer；
- 正式 archive/manifest/diagnostics schema、tsconfig/NodeNext 和其余 R2 exit
  gates。

因此 b2d 完成时的 internal provisional evidence 为 **19/19**；b2e 后当时为
**20/20**。后续 b2f 关闭 external plus-one cleanup 后为 **21/21**；b2g 又在
API 24/35 ordinary same-UID Debug 关闭自然 parse/semantic/emit cancellation
窄矩阵，当前为 **22/22**。这些阶段的自然 timeout 与完整 repeated
resource/system-fault matrix 仍开放。普通
Debug/Release 的 production 512 files、8 MiB/file、8 MiB total fence、四项
`false` capability 与下方 Public R2 **0/54** 状态仍不得改变。

后续 b2g 完整边界见
[R2-P3b-b2g Natural Parse/Semantic/Emit Cancellation](WORKER_NATURAL_PHASE_CANCELLATION_R2_P3B_B2G.md)。
