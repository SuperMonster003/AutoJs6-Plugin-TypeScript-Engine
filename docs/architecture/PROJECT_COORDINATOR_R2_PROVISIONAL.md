# R2 Provisional Project Coordinator

状态：R2-S5 内部、production-shaped coordinator 实现；R2-S6a 提供
`DEBUG` / same-UID 的进程死亡与 successor fence proof；R2-S6b 另在
production manifest 的独立 `:typescript_supervisor` 进程中，为已经成功提交的
system work、或由 successor bind 补提交的 work 提供 persisted post-death
reconciliation owner；R2-S6c 再以 app-private typed AIDL 和非导出 bound Service
提供 production-build live-epoch owner；R2-S6d 证明该 owner hard-kill 后的
successor rebind 与 registration-gap poison replay；R2-S6e 再证明 fully-ready
successor 不会被同步重放的 retired-generation teardown handler 退休；R2-S6f 把
exact persisted-Job ownership 前置到 durable marker commit 之前；R2-S6g 收窄一次
running fixed-ID/different-token generation handoff；R2-S6h 再收窄一次 running
JobService owner process death 后同一 exact work generation 的跨 owner-epoch
redispatch/reconcile；R2-S6i 又覆盖 retained exact work 上并发发出的 owner/compiler
kill request。R2-P1 随后把 coordinator 接入 release 与 additive public-shaped
request/ACK；R2-P3a 再把 production native input loader/framer 切换为
app-private file-backed spool、metadata-only snapshot 和最多 16 KiB
random-access chunk；R2-P3b-b1 当前将 frame 调整为 64 KiB raw/128 KiB
envelope，并加入 DEBUG Android capacity 与 exact success realm retirement，但
capability 仍关闭。以上切片都**不是**完整 Public R2 contract。

本文记录当前 process-wide admission authority 与 S2 → S4 coordinator 的所有权、
终态竞争和 cleanup 规则。P1 后 release/debug 都创建 coordinator；普通 frozen-v1
request 仍保持 R0 拒绝，只有携带完整 additive R2 metadata 的 preview request 才能
进入 production path，并通过 transaction 6 的 owner-scoped post-return ACK 启动。
S6b 为内部恢复链新增
了 system JobService 和 `RECEIVE_BOOT_COMPLETED` 权限，S6c 新增的 typed AIDL 只在
app module 内，S6d registration gate/owner telemetry 与 S6e retired-handler
controls/telemetry 仅限 DEBUG same-UID；S6f/S6g 的 production internal Job
ordering/token gate 不进入 public contract，S6h 的 exact owner kill 与 API 35
forced-eligibility fallback 也只是 DEBUG/same-UID instrumentation seam。public AIDL、
frozen AIDL/contract hash、ZIP v1 和 capability advertisement 均未因此改变。
P1 的详细 request/ACK/compat 边界见
[R2 Production/Public-shaped Admission Preview](PUBLIC_ADMISSION_R2_PREVIEW.md)。
P2 又把 profile/config authority 贯穿该 pipeline，见
[R2 Provisional Profile and Config Authority](PROFILE_AUTHORITY_R2_PROVISIONAL.md)。
P3a 的 native input ownership 和容量分层见
[R2-P3a File-backed Native Input](FILE_BACKED_INPUT_R2_P3A.md)；P3b-b1 见
[R2-P3b-b1 Android Device Capacity and Exact Realm Retirement](WORKER_INPUT_R2_P3B_B1.md)。

S6a-S6h 的进程外 proof bridge、persisted reconciliation、live owner、restart、
retired-handler fence、exact Job pre-arm/generation handoff 与 running owner-death
proof 见
[R2 Provisional Compiler Process Supervisor](PROCESS_SUPERVISOR_R2_PROVISIONAL.md)。

## 组件与边界

S5 把前四个内部切片连成一条由单一 owner 管理的 pipeline：

```text
TypeScriptCompilerPluginService
  -> R2ProcessAdmissionAuthority           # process singleton across Service epochs
  -> R2GlobalAdmissionArbiter              # process-wide single slot
  -> R2CompilationCoordinator              # owner/deadline/FD/callback
  -> R2ProjectArchiveLoader                # P3a private file-backed input snapshot
  -> TypeScriptWebViewCompilerHost         # <=64 KiB raw framing + heap-backed Worker
  -> R2PrivateOutputArchive                # S4 private validated output inode
  -> R2OutputFdCommitter                    # S4 caller-FD commit
  -> terminal state/tombstone
  -> at-most-one callback invocation
```

coordinator 有独立的单线程 pipeline executor 和单线程 deadline scheduler。
Binder thread 只完成有界 admission；实际 archive load、Worker compile、output
staging 和 commit 不在 Binder call stack 内执行。

这条链仍有两个重要限制：

- release/debug 都创建 R2 coordinator，但 production admission 仅接受完整 P2
  additive metadata；fault-injection controls 仍只允许 debug same-UID caller；
- P3a native input 已不再物化 caller source heap snapshot，但 ordinary
  production Worker 仍以
  512 files/8 MiB total admission 保留全部 text 和 TypeScript AST；output result
  仍物化且暂为 1 MiB。因此这条链仍不代表冻结的 64 MiB input 与 128/256 MiB
  output 容量已经端到端实现。

## Process-wide admission 与 owner identity

`R2ProcessAdmissionAuthority` 是 compiler process 内的 singleton；每个
`TypeScriptCompilerPluginService` epoch 都取得同一个
`R2GlobalAdmissionArbiter`，而不是随 Service 重建 admission state。以下入口共同
使用这个 authority：

- public-compatible `prewarm` 内部 probe；
- R1 DEBUG Binder transport probe；
- R2 DEBUG full-pipeline seam。

因此 WebView/Worker 的所有入口共享一个 process-wide slot，不存在“probe 空闲但
project 正忙”或两个 coordinator 各自接受一个 job 的旁路。internal probe 持有
`R2InternalOperationLease`；accepted Binder job 持有
`R2AcceptedJobLease`。只有 lease owner 证明自身 cleanup boundary 后，arbiter
才释放 slot。

Service epoch 的 `close()` 只关闭新 admission，不清除 active lease、terminal
tombstone 或 poison；下一次 `openServiceEpoch()` 也只会在 authority 未 poisoned
时重新开放。cleanup proof 失败后，poison 会跨同一 PID 的 Service recreation
保留，新的 Service `onCreate()` 立即抛出
`R2ServiceShutdownCleanupUnprovenException`，不能在同一进程里“重开”slot。只有
外部观察到旧进程死亡并启动新进程，才建立新的 process authority。

accepted lease 绑定：

```text
calling UID
+ requestId
+ service-generated job token
+ callback Binder identity
```

job token 来自 32 bytes `SecureRandom`，序列化为 64 个 lowercase hex chars；
token authorization 使用 constant-time byte comparison。`cancel`/DEBUG ACK 必须
同时匹配 UID、request ID 和 token，只有 request ID 或只有 token 都不构成
authority。callback Binder 在 admission 时 `linkToDeath`，并作为 lease 的 owner
identity 保留。

arbiter 同时保留有界 terminal tombstone：

- key 为 `(callingUid, requestId)`；
- value 绑定原 job token 与 terminal kind；
- 同一 UID 不能重用已经 terminal 的 request ID；
- wrong UID/request/token 不匹配 active job 或 tombstone；
- 正确 terminal tuple 的重复 cancel/ACK 返回幂等成功，不会重放 callback；
- tombstone 最多 256 个，不静默 eviction；历史已满时要求 process restart。

并发 admission 只有一个 lease 获胜。尚未向 caller 返回 accepted 的准备失败可以
用 exact lease `abortJobAdmission`；accepted job 则只能在 cleanup proof 后
`completeJob`，或在证据缺失时保留原 lease 并 poison。

## Admission、FD ownership 与 DEBUG ACK seam

R2 debug route 不绕过已有 request/FD gate。`startCompilation` 仍先执行：

- primitive-only、bounded Bundle metadata 检查；
- contract hash/version、request/profile/timeout 等 R0 envelope 检查；
- input/output regular、seekable、access-mode 和不同 inode 检查；
- live callback Binder 检查。

只有 request 除了“R0 尚不可用”外全部合法、恰好选择一个 private debug protocol，
并且 `BuildConfig.DEBUG && callingUid == applicationInfo.uid` 时，才进入 S5。R2
private controls 还携带 input/request-manifest SHA-256 及本地可信 compiler、
declarations、runtime fingerprints；它们没有成为 public Bundle schema。

coordinator 在返回 accepted ticket 前依次：

1. 创建 host operation handle；
2. 在 coordinator lock 内取得 process-wide lease/job token、计算 absolute
   monotonic deadline、建立 callback death link、登记 active job，并重新确认
   callback 仍存活；
3. 安装 deadline 与 unacknowledged timers；
4. 对 caller output 执行不改变内容的 `fsync` 与
   `lseek(fd, 0, SEEK_CUR)` preflight；
5. 最后执行 `ftruncate(fd, 0)`。

前四步包含所有仍可能导致 admission reject 的预检和调度；失败时撤销尚未发布的
lease，并由 service 关闭 caller descriptors。`ftruncate(0)` 的**成功**是最终
不可逆 admission point：它自身失败仍可 reject，但一旦成功，后续不再有 rejected
分支，而是返回 accepted ticket。与该点竞争的 callback death 也作为 accepted job
的 terminal condition 清理，不回退成 rejection。accepted 时 input/output PFD
ownership 转移给 coordinator，service 外层不再关闭它们。

runtime-capability fingerprint 不只是 profile 名称或少量 revision。它对以下完整、
固定、按稳定顺序编码的 Worker policy canonical input 计算 SHA-256：

```text
canonical domain=autojs6.typescript.runtime-capabilities.provisional
runtimeCapabilitySchemaRevision=1
profileSchemaRevision=1
profileRevision=1
profile=rhino
extensions=.ts
compilerApi=createIncrementalProgram
allowJs=false
checkJs=false
ignoreDeprecations=6.0
incremental=false
inlineSourceMap=false
inlineSources=false
module=CommonJS
moduleResolution=Node10
newLine=LF
noEmitOnError=true
noLib=false
outDir=/outputs
rootDir=/sources
skipLibCheck=false
sourceMap=true
strict=true
target=ES2018
types=[]
```

因此 profile revision 或任一固定 Worker option 改变都会改变 runtime
fingerprint，并由 request manifest、private control 和本地 metadata 三方绑定。

同步 Binder `startCompilation` 无法从服务端证明 caller 已经观察到 return。当前
DEBUG seam 因此增加一个显式 post-return barrier：

```text
startCompilation(...)
  -> accepted ticket(requestId, jobToken)
  -> caller observes return
  -> cancelCompilation(
       requestId + jobToken +
       private command=ACKNOWLEDGE
     )
  -> callback armed + pipeline scheduled
```

ACK 复用现有 `cancelCompilation` transaction 和 private DEBUG metadata，没有新增
AIDL method。它只接受 same-UID exact owner tuple。ACK 前 job 处于
`RESERVED_UNACKNOWLEDGED`，callback 未 armed，pipeline 不启动；最多等待
`min(request timeout, 5 seconds)`。barrier 超时会选择 `UNACKNOWLEDGED`/deadline
stop、清理 FD 且不发送 callback。ACK 只有在 lock 内确认 phase 仍为
`RESERVED_UNACKNOWLEDGED`、尚无 stop intent 且当前时间严格早于该 cutoff 时才能
arm callback；到达或越过 cutoff 的 exact ACK 仍幂等返回成功，但会原子选择
`UNACKNOWLEDGED`/`TIMED_OUT` stop，绝不重启 pipeline 或重新 arm callback。已经
terminal 的迟到 ACK 同样只匹配 tombstone，不会复活 job 或补发 callback。

这个 seam 只用于证明 callback 不早于 `startCompilation` return；production
ticket acknowledgement、delivery 和 retry 规则尚未冻结，不能把 private command
写进 public client。

## S2 → S4 pipeline

ACK 后 pipeline 使用 accepted job 的同一 absolute deadline：

```text
take service-owned input PFD
  -> P3a fixed-offset private capture
  -> streaming ZIP/manifest/hash/UTF-8 validation into private VFS spool
  -> exact R2AdaptedProjectArchive/R2FileBackedProjectSnapshot
  -> recompute remaining deadline
  -> Worker 512 files/8 MiB admission
  -> S3 compileProject(exact host handle, exact metadata-only snapshot, remaining time)
  -> random-access <=64 KiB source framing
  -> typed result bound back to the exact snapshot identity
  -> close file-backed snapshot / prove input cleanup
  -> S4 deterministic private output ZIP
  -> same read-only unlinked inode validation
  -> S4 caller output FD commit
  -> terminal publication
```

input authority 来自 accepted envelope、private control hashes 和 plugin-local
runtime metadata。S4 packager 同时接收 adapted archive 与 compilation outcome，
继续执行 exact snapshot identity check；coordinator 不允许仅凭相同 request ID
拼接两个不同项目的 input/result。

P3a 在 archive capture 成功后将实际 STORED/DEFLATED payload 流式写入第二个
app-private spool。archive staging 在校验结束后关闭，snapshot 只接管只读 spool
inode、offset/length、digest 和 root metadata；Android/Linux 尽早 unlink，
Windows fallback name 保留到 snapshot close。success、reject、Worker failure、
cancel、timeout 和 coordinator close 都必须在 release accepted slot 前关闭
snapshot；无法证明 spool 删除/reader close 时沿既有 cleanup-unproven/poison
边界 fail closed。successful host outcome 只在 input framer release 已证明后返回；
coordinator 随即关闭 snapshot，再以仍可读的 immutable metadata/exact object
identity 执行 S4 provenance comparison，不在 output packaging 中重读 source
payload。

`EMITTED` 提交 manifest、diagnostics、modules 和 maps；语法/类型失败产生真实的
diagnostics-only ZIP。Worker/internal/transport failure、cancel 和 timeout 则在
publication 前证明 caller output 已同步为空。

## Commit/publication gate

S5 的“原子”是 coordinator terminal choice 的 linearization，不是 rename 式的
filesystem atomic replace。caller backing file 在 copy 期间仍可能看见中间 bytes，
caller 只能在 terminal callback 后消费。

commit 的关键顺序为：

```text
validate same private inode
  -> destination truncate/copy/exact truncate
  -> fsync
  -> verify private inode unchanged
  -> destination seek(0)
  -> terminalGate()
  -> close service-owned output PFD
  -> close/delete private staging
  -> publish terminal metadata
```

`terminalGate()` 在 coordinator lock 内重新检查 absolute deadline 和已选择的 stop：

- cancel/timeout/death 先赢：gate 抛出 superseded，committer 必须 rollback
  truncate-empty + fsync + seek-zero + close；
- gate 先赢：job 进入 `COMMITTING`，之后的 cancel/timeout 不再把一个已经 durable
  的 artifact 改写成 cancelled/timed-out；
- gate 后的 PFD close 仍是 commit 的组成部分。close 失败且 empty rollback 可证明
  时发布普通 failure；rollback 不可证明时 poison，而不是发布 success/failure。

gate 赢不等于 callback 已发送。只有 output PFD close、private staged inode cleanup
和最终 shape 检查都成功后，coordinator 才进入 `PUBLISHABLE`。随后
`completeJob` 原子写入 terminal tombstone、释放 global slot，并至多调用一次
`onCompleted`。callback 只在 ACK 已 armed、Binder 仍 alive、且未发生 callback
death/service destroy 时取得；callback Binder 调用失败不会重试或重放已提交的
terminal state。

内部 callback metadata 区分：

- `emitted`：`succeeded=true`，包含 diagnostic/module count 与 output manifest
  SHA-256；
- `diagnostics`：`succeeded=false`、`ERROR_COMPILATION_DIAGNOSTICS`，同样指向已经
  提交的 diagnostics-only ZIP；
- `cancelled`、`timed_out`、`failed`：output 已证明为空；
- callback death、service destroy、unacknowledged：保留内部 tombstone，但不向
  已死亡、未 armed 或正在销毁的 callback 发送终态。

这些 Bundle 字段当前只是复用 frozen constants 的 private integration seam，
不表示 public terminal schema 已开放。

## Cancel、deadline、death 与 shutdown

stop selection 在 coordinator lock 内 first-wins，且只允许发生在 commit gate
之前。每次边界检查都会重新比较 `SystemClock.elapsedRealtime()` 与 absolute
deadline；如果 deadline 已到，即使此时收到 cancel，也选择 `TIMED_OUT`。

active cancel 使用 exact `(callingUid, requestId, jobToken)`。wrong tuple 返回
`false`；相同正确 cancel 可在 active/terminal tombstone 上幂等返回 `true`。
stop 会把同一 host operation handle 映射为 S3 keyed hard-stop；pipeline 只有在
S3 返回 exact Worker replacement ACK 或 exact-session destroy/renderer-gone
proof，且该 pending job 已无 transferred result finalizer in flight 后，才继续
关闭 FD 和发布 terminal。若 teardown proof 先到，host 只设置
`stopTeardownProven` 并保留 pending；最后一个 finalizer 在 `finally` 中 wipe/
release exact accumulator 后，再回到 main thread 完成 `completeStopped()`。因此
普通 cancel/timeout/failure terminal 也绑定 exact teardown **与** finalizer
release，不能因 Worker 已终止就提前发布。

callback death 有两个阶段：

- admission link 期间已死亡：返回 rejected，不转移 FD ownership；
- accepted 后死亡：标记 `callbackDied`，gate 前选择 stop；无论竞争发生在哪个
  phase，都不会再调用该 callback。

service shutdown 先关闭 global arbiter 以拒绝新 admission，但
`arbiter.close()` 不伪造 active lease 已清理。R2 coordinator 在自身 lock 内设置
`closed/serviceDestroyed`，并以 first-wins 规则选择已经到期的 deadline 或
`SERVICE_DESTROYED`；已有 terminal intent 不会被 shutdown 覆盖。

R1 transport coordinator 同样在 lock 内关闭 admission：尚未选择 terminal 的 job
被 close-claim 为 `SERVICE_DESTROYED`，已经被 cancel/timeout/completion claim 的
job 保留原 first-wins intent。close-claimed cleanup 与 already-claimed cleanup
都在原先保留的 single-thread executor 上继续，不另起一个与 FD owner 竞争的清理
线程。R2 accepted cleanup 也继续使用其保留的 pipeline executor。

R1/R2 accepted coordinator 各自使用一个 absolute 5 秒 shutdown cleanup-proof
边界；等待多个 job 时不会为每个 job 重置 5 秒。进入 closed 状态后，即使
already-claimed job 完成 cleanup，也抑制 callback。只有 proof 完成后才允许
`shutdownNow()`；若 dispatch、cleanup 或 5 秒 proof 失败，则先 poison/fail-close
process authority，保留可能仍持有 descriptor 的 executor 进行 graceful
shutdown，绝不以 `shutdownNow()` 中断未知 FD owner。

host protocol/result verifier 另有一个由两条 executor 共享的 absolute 1 秒
termination-proof deadline。已经转交给 result verifier 的 accumulator 无论
`finish()`、parse、post 或 stale-current 检查如何结束，都在 `finally` 中 wipe/
release 并扣减 in-flight accounting；result executor 只做 graceful shutdown。
只有 protocol/result executor 都已终止后，host 才清 Handler callbacks。executor
未终止、transferred finalizer 未全部释放或 Handler cleanup 未证明，都会使 close
report 为 unproven，并由 Service poison/fail-close；它们不被 coordinator 的 5 秒
边界伪装成已完成。

Service `onDestroy()` 仍尝试所有 teardown 并聚合 failure，但 accepted cleanup 的
coordinator 等待上限各自是 5 秒，host verifier 另有上述 1 秒边界；这不是“整个
`onDestroy()` 共享一个 5 秒 wall-clock budget”。任一 proof 缺失时抛出
`R2ServiceShutdownCleanupUnprovenException`，要求进程外 supervisor 终止并观察
旧 process epoch 死亡。这是 bounded fail-fast，不是在同一 Service epoch 内恢复。
S6a 已用 `DEBUG` / same-UID internal seam 证明 exact Binder self-kill、旧 Binder
death + procfs death proof 和新进程恢复。S6b 已增加 production-manifest 的
post-death reconciliation owner；S6c 让 process-wide poison 自动交给已完成双向
exact registration 的 app-private live owner；S6d 又验证 hard-kill owner 后，同一
compiler epoch 可连接 successor registry，并在 registration 未 ready 的窗口保留
poison，待当前 generation 握手后重放；S6e 再于不同 successor fully ready 后同步
直接重放 retired teardown handler，并逐次验证 current authority 不变；S6f 把
exact Job pre-arm/readback、marker commit 与 Job post-check 串入 termination
deadline；S6g 以 token gate 约束一次 running generation replacement；S6h 再验证
一个 running JobService owner process epoch 消失后，同一 exact work 可在不同 owner
epoch 中进入 callback 并 reconcile。owner 未 ready 或 identity/endpoint 歧义时
availability 继续 fail closed。

## Cleanup proof 与 poison

普通 terminal 在释放 slot 前必须证明与 job 有关的资源已进入确定状态：

- 尚未被 S2 loader 消费的 input PFD 已关闭；
- cancel/timeout/failure output 已
  `truncate(0) + fsync + seek(0) + close`；
- emitted/diagnostics output 已完成 commit PFD close；
- private input/output staging 已成功 close/delete；
- S3 active Worker/session 已完成其 exact teardown boundary；
- 该 job 已转交给 S3 result verifier 的 accumulator/finalizer 已全部
  wipe/release；stop path 只有在 exact teardown 与 finalizer release 两项都成立后
  才完成 host outcome。

Service/host close 另要求 protocol/result executor 在共享 absolute 1 秒内终止，
所有 transferred result accumulator/finalizer 已 wipe/release，之后才清除
Handler callbacks；这些是 shutdown cleanup proof，不要求普通成功 job 关闭可复用
的 host executors。

下列证据缺失会进入 `SUPERVISION_REQUIRED`，而不是普通 failure：

- pipeline executor 无法接受已拥有的 job；
- coordinator 丢失其应持有的 input/output descriptor；
- S2 报告 private input cleanup unproven；
- S4 commit/empty rollback/close 返回 `R2OutputCommitUnprovenException`；
- private output staged inode cleanup 失败；
- stopped/failed job 的 input close 或 output empty finalization 无法证明。

poison 操作保留**同一个 active lease**，设置 process-wide poisoned state，取消
coordinator timers，并请求 host 按 service-destroyed cause 停止。它不会：

- 释放 global slot；
- 写 terminal tombstone；
- 发送普通 callback；
- 接受任何后续 job、transport probe 或 prewarm。

后续 admission 统一得到 `ERROR_COMPILER_UNAVAILABLE`，直到外部 supervisor 证明
旧 compiler process 已死亡或给出等价的进程级隔离证据。in-process coordinator
没有“清除 poison”入口；process singleton 也让同 PID Service recreation 无法
清除它，这是有意的 fail-closed 行为；这里的“外部”特指 compiler 进程外 owner，
不是尚未冻结的 external-caller public contract。

## S6a/S6b/S6c/S6d/S6e/S6f/S6g/S6h process-death supervision

S6a 在 `:typescript_compiler` 之外增加了最小 internal supervisor primitive。
它从 exact remote Binder 的 DEBUG telemetry 取得
`boot ID + UID + PID + /proc start-time ticks + process name + 256-bit process nonce`，
`linkToDeath` 后把完整 tuple 回送给同一 Binder。compiler 只在 process-wide
admission 已 poisoned、caller 为 same UID、caller PID 不同且 tuple 精确匹配时，
先持久化 quarantine marker，再调用 `Process.killProcess(Process.myPid())`；外部
caller 不对一个裸 numeric PID 发 signal。

procfs observer 有 `SAME`、`REUSED`、`ABSENT`、`UNREADABLE` 四态。supervisor
只有在 exact Binder death 与 `ABSENT/REUSED` 同时成立时才得到 death proof；
`SAME` 或 `UNREADABLE` 都 fail closed。command 在专用 daemon worker 上执行，
每个 supervisor 进程仅允许一个不可取消 transaction 在途。外部观察使用 1 s 至
30 s 的有界 absolute deadline（默认 10 s），不会让同步 Binder command 阻塞
supervisor 观察循环。request 同时携带提前 500 ms 的服务端
`elapsedRealtime` command deadline；compiler 在 durable mutation 前后及
self-kill 前重验，迟到 transaction 不会产生迟到自杀。

quarantine marker 使用 `AtomicFile` 与 directory `fsync`。death proof 之前抢先
创建的 quarantined successor Service 不打开 admission/host，并从 `onBind()` 返回
`null`。只有 supervisor 对 exact old identity 完成 marker clear 与 directory
`fsync` 后，inert successor 才 self-recycle，使 clear 前仍持有的
`BIND_AUTO_CREATE` connection 由 AMS 跨进程恢复。instrumentation 在 death proof
后故意暂停 clear 并并发 bind，证明该 null-binding fence 不会泄漏 successor
Binder；另一个用例从 persisted marker 恢复 identity，只在 procfs
`ABSENT/REUSED` 时 reconcile。

S6b 在 production manifest 中注册由系统调度的
`R2CompilerProcessSupervisorJobService`。它运行在独立
`:typescript_supervisor` 进程，只消费已经持久化的 quarantine marker，并在有界
worker 中观察 exact old epoch。只有 `ABSENT` 或因 boot/process identity 不同而
得到的 `REUSED` 才允许 exact marker clear；`SAME` 和 `UNREADABLE` 都 fail
closed、保留 marker 并请求 JobScheduler 重试。marker 存续期间 compiler Service
不开放 admission/host；marker 清除后 inert successor self-recycle，使 held
`BIND_AUTO_CREATE` connection 连接到新的 compiler process。

该 JobService 使用 persisted Job、deadline 与 exponential backoff。固定 Job ID
已有 expected component 时视为成功而不重新 schedule，避免替换等待/退避中的 job。
marker、procfs 与 telemetry I/O 全部由 worker 在 main callback 之外执行；worker
不直接完成 job。`completionPosted` CAS 至多 post 一次 main-looper completion，并
保持 `running` 到 runnable 执行，只有该 runnable 可在 job 仍 current 且未 stop 时
调用一次 `jobFinished()`。固定 Job ID 的 `onStopJob()` 不依赖 `JobParameters`
引用相等性；它以 `RUNNING -> STOPPED` 与 marker file lock 内 clear authorization
的 `RUNNING -> AUTHORIZED` CAS 线性化，stop 先赢就不能删除 marker。

telemetry 仅是 app-private、best-effort 观察证据，不能授权 clear 或 admission。
S6b 不向仍存活的 `SAME` epoch 发 signal；它只为成功提交的 persisted job，或由
successor bind 重新提交的 work 提供 post-death reconciliation。

S6c 在同一 `:typescript_supervisor` 进程增加非导出的 app-private bound Service。
compiler process-lifetime client 以 exact ComponentName 绑定，registry 校验 caller
UID/PID、procfs compiler epoch、remote typed Binder descriptor 与存活状态；
compiler callback 反向验证 supervisor boot/UID/PID/start-time/process-name。
256-bit registration token 只有双向 challenge 与 registry 返回一致后才激活；
production prewarm availability 在 registration ready 前 fail closed。

arbiter 的首次 poison 转换在锁外通知 client，poison observation 单调保留。通知、
注册和重绑使用 current connection generation，stale generation 不能消费新 owner
的重试。owner 复用 S6a 的 bounded exact command/death-proof engine，但经由 private
typed callback 而非 DEBUG public-compatible Bundle seam；compiler 仍只在 exact
token、supervisor identity、自身 epoch、poison 与 deadline 全部匹配时 durably mark
并自行终止。owner 没有任意 PID kill authority。

S6c provisionally 关闭 app-private live registration/authorization/termination
control-plane 子项，但不关闭 production ACK/public contract 或完整 owner/system
lifecycle blocker。S6d 为每个 bind attempt 分配独立 connection generation，以
active source + generation + registry Binder identity 拒绝旧 framework callback；
设备 fault 再证明 old owner exact hard death、successor registry connection、
registration-gap poison 保留与 resume 后 replay。该测试没有人为延迟旧 callback/
notification 来直接重叠两个 generation。

S6e 不改变 coordinator 的 poison、lease 或 cleanup 语义。它只在不同且 generation
更大的 successor 完成 registry/token/owner handshake 后，以 DEBUG/same-UID seam
各重复两次、同步直接调用 retired `onServiceDisconnected()`、API 26+
`onBindingDied()`、API 28+ `onNullBinding()` 与旧 registry death recipient；
每次调用后都复核 current connection/generation/registry Binder/death record/token/
owner 未变。该证据是 deterministic sequential handler invocation，不是 Android
framework 的真实投递或线程并发，也不覆盖 stale `onServiceConnected()`、旧
notification/token transaction 或 hung Binder call。

S6f 同样不改变 coordinator 的 active lease/poison 语义。它要求 exact persisted
Job 先 schedule 并 readback，之后才 durable-commit quarantine marker，commit 后再
post-check 同一 component/schema/token/old epoch，最后才允许 delayed exact
self-termination。S6g 则让 fixed-ID running generation 以 work token 而不是
`JobParameters` 引用仲裁 replacement 与 stale `onStopJob()`；DEBUG lifecycle
journal 只提供 token-scoped positive observation，不参与 coordinator authority。

S6h 不创建 replacement generation。设备测试让同一 exact work W 先在 JobService
owner P1 中运行并到达 `PREARM_RETRY`，marker 与 W exact-match 后 exact-kill P1，
并证明该 owner process epoch 消失；W 的 120 秒 PREARM command deadline 只属于
DEBUG deterministic fault seam，不是 production retry/backoff guarantee。old compiler
epoch 离开后，W 必须在不同 owner epoch P2 中得到 post-kill `START_CALLBACK` 与最终
`RECONCILED`。API 24/25/28 在
30 秒窗口内自然观察到该重投；API 35 在自然窗口未观察到 positive callback 后，
仅在 marker 与 scheduler-tracked W 再次精确匹配时，由 DEBUG instrumentation 请求
fixed-ID forced eligibility。命令精确成功回执之后、且最早 observed qualifying
START 严格晚于 invocation boundary，才记为非因果的
`POST_FORCE_INVOCATION_BOUNDARY`。因此 S6h 不证明 shell 导致 callback、API 35
OEM autonomous retry、全局 exactly-once，或 system-triggered/连续多次
JobService process restart。

pre-arm、marker commit 与 Job post-check 横跨 app-private file 和 JobScheduler state，
不是原子事务；S6f 已覆盖一次无 successor bind recovery，但 post-check 后外部取消/
system state loss、owner/compiler 同时崩溃、hung transaction，以及真实
reboot/force-stop、crash-loop/backoff 与 external-caller delivery 仍需独立验证。
完整边界、四态与限制见
[R2 Provisional Compiler Process Supervisor](PROCESS_SUPERVISOR_R2_PROVISIONAL.md)。

## 当前验证面

R2-S5 closure artifact 的本地证据：

- JVM：112 tests / 0 failures / 0 errors / 0 skipped；
- R2 desktop framed golden：TypeScript `6.0.3` 通过；
- Gradle：
  `:app:testDebugUnitTest`、`:app:verifyR2ProjectCompilerCore`、
  `verifyR1ProtocolCompatibility`、
  `:plugin-api:typescript-api:verifyTypeScriptApiV1Freeze`、
  `:app:verifyDebugTypeScriptDistributionApk` 与 `:app:lintDebug` 全部通过。

同一 S5 app/test APK 组合在三台目标设备的聚合 instrumentation 均为
`OK (32 tests)`：

| 设备 | 聚合结果 | 分项 |
| --- | --- | --- |
| `emulator-5556` / Android 7.0 / API 24 | `OK (32 tests)` | R1 accepted lifecycle 9 + R1 service destruction 1 + R2 Binder 6 + Output FD 6 + Project 10；renderer-exit 使用 capability assumption |
| `emulator-5554` / Android 7.1.1 / API 25 | `OK (32 tests)` | R1 accepted lifecycle 9 + R1 service destruction 1 + R2 Binder 6 + Output FD 6 + Project 10；renderer-exit 使用 capability assumption |
| `QV710AF65F` / Android 12 / API 31 | `OK (32 tests)` | R1 accepted lifecycle 9 + R1 service destruction 1 + R2 Binder 6 + Output FD 6 + Project 10；renderer-exit 实际执行 |

R1 accepted lifecycle 9-test suite 包含 service close 对 close-claimed 与
already-claimed completion 的 cleanup/callback suppression；独立的 R1 service
destruction test 验证 last unbind 释放 mature host，随后两个 bind 都可重新
probe。R2 Binder 6-test suite 同时覆盖 late exact ACK 幂等但绝不 re-arm，以及
admission reject 在 irreversible truncate 前保留 caller output/request owner；
Output FD 6-test suite 包含 terminal-gate stop 在 descriptor close 前赢得竞争时的
完整 rollback。

Project 10-test suite 包含两条 deterministic finalizer regression：

- in-flight close：transferred accumulator 进入 500 ms test window 后关闭 host，
  close 等待 finalizer、发布完整 cleanup proof，并由 replacement host 恢复；
- gated cancel：finalizer 持有 accumulator 时先取得 exact Worker teardown proof，
  future 仍不得完成；release 后才发布 cancel terminal，随后同一个 host 的 recovery
  compile 成功。

S5 artifact SHA-256：

```text
app APK  907A86C0CB48E225DDE91E25D6D7BC5072A2E587D50EAB2A0008E1D8CBE1BF16
test APK 522D8583B9DA594A20B46054178E7CFF3906A6B38C804632770CEF18DEDF4FA2
```

`git diff --check` 无 whitespace error，仅报告 compiler host/worker 两个已知
LF → CRLF warning。在该 S5 closure artifact 当时的基线中，冻结 AIDL、public
contract、ZIP v1、Android manifest 与 plugin metadata 均无 diff；
`compilerAvailable`、
`projectCompilation`、`semanticDiagnostics`、`sourceMapV3` 仍全部为 `false`。

S6a+S6b 历史同源 artifact（不包含后续 S6c）将 lifecycle 覆盖扩展到 14 项，包括 S6a exact
process-death/fence proof，以及 successor bind 提交 system work 后由 S6b Job owner
自动完成 post-death reconciliation、`SAME` epoch 保持 fail-closed 的证据。由于
IDE/旧模拟器的
`UiAutomation` 限制，API 24/25 的 lifecycle class 使用等价的分段执行；API 31
完整 class 一次执行：

```text
app APK  SHA-256 BD4E53CCE7E007A276901E6F0F60B932AE77EC6E8382E8EDC0DBD77ADE985607
test APK SHA-256 59D4738172D21087825CF5A4D4AE2F1911903A3BC0FA5A273B18A9B424C2D9C6
```

| 设备 | lifecycle 结果 |
| --- | --- |
| `emulator-5556` / Android 7.0 / API 24 | `OK (9) + OK (4) + OK (1) = 14/14` |
| `emulator-5554` / Android 7.1.1 / API 25 | `OK (9) + OK (4) + OK (1) = 14/14` |
| `QV710AF65F` / Android 12 / API 31 | `OK (14 tests)` |

同一 app/test APK 在三台设备还分别通过其余 coordinator/FD/project suites：
`OK (13) + OK (10)`；与 lifecycle 合计每台均为 `37/37`。覆盖面证明 exact
lease/FD owner 保留、无普通 callback、exact old Binder self-kill、Binder +
procfs death proof、clear 前 null-binding quarantine fence、held binding
跨进程恢复、successor-bind-submitted Job owner 自动 reconcile 已消失 epoch，以及
live `SAME` epoch 永不清 marker。API 24/25 的分段只规避 `UiAutomation` runner
限制，不减少测试总数。

S6c 历史同源 artifact 为：

```text
app APK  SHA-256 61B6CCB88040A0E562B356CDA4137DC133C018D7A4D15F7255D101EB8666B7A5
test APK SHA-256 13F5C46BD496891CDA2E152702C3AA574D987C50F0F8C234F8276D73CA936E63
```

JVM report 为 125 tests / 0 failures / 0 errors / 0 skipped；R2 core、release manifest/APK、
optional-plugin independence、lint、R1 compatibility 与 TypeScript API freeze
聚合门禁全部通过。新增 automatic owner 用例在 API 24/25/28/31/35 均为
`OK (1 test)`，验证 cleanup-unproven poison 不发布 ordinary callback、old
Binder/FD/process 消失、quarantine exact-clear 与 fresh epoch 正常 ACK/cancel。API
31 另以同一 artifact 完整运行 lifecycle class，结果为 `OK (15 tests)`；其他四个
API 层级本次只补跑新增单方法，不能与历史 37-test aggregate 混算。

这些设备证据不包含真实 release APK on-device poison、reboot、force-stop、live
owner/JobService process restart、连续
backoff、Job cancellation/reschedule、`onStopJob`/main-looper completion 竞争、
marker corruption 或 procfs/boot identity `UNREADABLE` 的 fault matrix，也没有
证明完整 production owner lifecycle。S6c 仍是 provisional internal control-plane
slice，不是 public compilation capability。

S6d 历史同源 artifact 为：

```text
app APK  SHA-256 6AB3E1003FB317D3E2BA5D216B33B0BD3088972BFFE439C01537CD297AB4B2EF
test APK SHA-256 84D8038C257386B0ACDABA2EE761D23AD7E7F3BF9857DB33415C9C8691905C7C
```

JVM report 仍为 125 tests / 0 failures / 0 errors / 0 skipped；全部本地聚合门禁
通过。新增 owner restart 用例在 API 24/25/28/31/35 均为 `OK (1 test)`，证明
old registry Binder/owner death、同一 compiler epoch 的 successor registry
connection、registration-gap cleanup poison 保留/replay、old compiler/FD/process
death、quarantine clear 与 fresh epoch ACK/cancel。API 31 另以同一 artifact 完整
运行 lifecycle class，结果为 `OK (16 tests)`；其他四个 API 只补跑 S6d 单方法，
不能与历史 aggregate 混算。

该证据没有直接注入重叠 stale/duplicate callback/notification，也不包含真实
release APK on-device poison、owner/compiler 同时崩溃、hung owner transaction、
marker → Job crash gap、reboot/force-stop、连续 backoff、marker corruption 或
procfs/boot identity `UNREADABLE`。

S6e 历史同源 artifact 为：

```text
app APK  SHA-256 3F2D71DDBAAC1BD8DD74E04B627AC9033A408318B0FD8A6DDCBD6785E7612B18
test APK SHA-256 9CF88E97A0F8AABC9F61C1EAA9AA0FBDF0EDCEB74B0309A01BA9A9CC3F31F742
```

JVM report 仍为 125 tests / 0 failures / 0 errors / 0 skipped，全部本地聚合门禁
通过。新增 `retiredSupervisorCallbacksCannotRetireReadySuccessorGeneration` 在
API 24/25/28/31/35 均为 `OK (1 test)`：它保留同一 accepted + ACK
cleanup-unproven job/FD，arm old ready generation，证明 exact old owner/Binder
death，再等待不同 successor fully ready；随后同步 duplicate replay API 可用的
retired teardown handlers，证明 current generation/owner/registry/token authority
与原 compiler job/FD 不变。之后才 cancel 产生 poison，由 current successor 完成
old compiler/FD/process death、无 ordinary callback、quarantine clear 与 fresh
epoch ACK/cancel。

API 24/25 各执行 4 次 direct delivery（disconnect + death recipient）；API
28/31/35 各执行 8 次（另含 binding-died + null-binding）。API 31 另以同一 artifact
完整运行 lifecycle class，结果为 `OK (17 tests)`；其他四个 API 只补跑 S6e
单方法，不与历史 aggregate 或 S6c/S6d artifact 追溯混算。

该证据是 DEBUG test Binder thread 上的 sequential direct invocation，不是 Android
framework 真实投递或并发 transaction；stale `onServiceConnected()`、
register/notify/token transaction、hung owner、dual crash、marker → Job gap、
release/reboot/force-stop/backoff/corrupt/`UNREADABLE` matrix 仍开放。

S6f/S6g 的历史同源 evidence 与 S6h 当前 evidence 统一记录在
[R2 Provisional Compiler Process Supervisor](PROCESS_SUPERVISOR_R2_PROVISIONAL.md)，
避免在 coordinator 文档中追溯混算 artifact。S6h 新增
`runningSystemJobIsRedeliveredAfterSupervisorProcessDeath`：API 24/25/28 均在
30 秒窗口内自然观察到同一 W 从已消失 P1 向不同 P2 的 post-kill
`START_CALLBACK`/`RECONCILED`；API 35 仅在自然窗口未观察到 positive callback 后，
以 exact marker + scheduler-tracked-W recheck 请求 test-only fixed-ID forced
eligibility，并在 invocation boundary 后观察到相同 W 的 callback/reconcile。
最终 artifact hashes、设备矩阵与 API 35 完整 20-test lifecycle evidence 记录在
supervisor 文档，不沿用 S6g androidTest artifact。Sony `QV710AF65F` 的 24h soak
本轮未被访问。

P3a 的当前证据是 JVM/source-level closure，不追加或改写上述历史 device
artifact：

- frozen ZIP STORED/DEFLATED sink 的最多 16 KiB callback、CRC/size/ratio 后置
  `end`、sink/validator failure `abort` 和 partial discard；
- production loader 在超过旧 1 MiB materialization gate 的 source 上返回
  file-backed snapshot，并覆盖 invalid UTF-8 与所有临时文件 cleanup；
- native framer 在 P3a 时跨过 8 MiB Worker budget 仍只读取一个最多 16 KiB
  chunk，并覆盖 digest change、short cursor 和 release。P3b-b1 当前 chunk 为 64 KiB，
  保持同一 one-chunk/window shape；

这些测试证明 native ownership/allocation shape，不是 64 MiB Worker compile 或
device memory/renderer proof。

P3b-a 又在独立 Node child 中加入 Worker source-level/desktop closure：

- Worker 内部 envelope 为 4607 files、8 MiB/file、72 MiB total，并在 AST 前
  限制 aggregate path/directory trie 与 token complexity；
- fatal UTF-8 后只保留一份 text record，CompilerHost lazy-create/cache
  `SourceFile`；diagnostics 增量计费，compile `finally` 释放 per-job references；
- `--max-old-space-size=512 --expose-gc` 下执行单次 64 MiB caller + 8 MiB
  trusted ASCII boundary、forced-GC retained plateau 和 small successor。

这仍是 same-isolate Node aggregate evidence，不是 WebView renderer、
compiler-service PSS、LMK/OOM 或重复大任务 device proof；production host
继续执行 512 files/8 MiB total gate。详见
[R2-P3b-a Worker Input Capacity and Lifetime](WORKER_INPUT_R2_P3B_A.md)。

P3b-b1 又在 API 24/28/35 运行 DEBUG exact 64 MiB direct capacity，并在
API 24/35 运行 STORED/DEFLATED inclusive/`+1` Binder pipeline；成功 project
在 callback 前退休 exact WebView realm，随后 small successor 使用更新 session。
这是 low-token ASCII/debug capacity proof，不是 minified-release、PSS 或 LMK/OOM
proof。详见
[R2-P3b-b1 Android Device Capacity and Exact Realm Retirement](WORKER_INPUT_R2_P3B_B1.md)。

## Public freeze 与下一 blocker

S5/S6a/S6b/S6c/S6d/S6e/S6f/S6g/S6h/P1/P2/P3a/P3b-a/P3b-b1 没有开放 public R2：

- ordinary production/public `startCompilation` 继续返回
  `ERR_AUTOJS6_TYPESCRIPT_R0_NOT_AVAILABLE`；
- `compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、
  `sourceMapV3` 四项 capability 仍全部为 `false`；
- Public R2 checklist 仍为 `0/54`，这些 provisional slices 不关闭其中任何一项；
- AIDL method、transaction code 和 public contract hash 未修改；
- DEBUG controls、ACK command、provisional request/output/diagnostics schema 都不
  是 public contract。
- Android manifest 已为内部 owner 新增同一 `:typescript_supervisor` process 中的
  S6b JobService（`exported=true` + `BIND_JOB_SERVICE`）和 S6c live Service
  （`exported=false` + 无 permission），以及 `RECEIVE_BOOT_COMPLETED`；这些变化
  没有开放 public Binder API、ZIP schema 或 capability advertisement。

S6c 已 provisionally 补齐 app-private production-build live-epoch registration/
authorization/termination control-plane 子项；S6d 已补单次 owner hard-kill、
successor rebind 与 registration-gap replay；S6e 已补 successor-ready 后同步重放
retired teardown handler/death-recipient 的 no-op fence；S6f 已补 marker 前 exact
persisted-Job ownership 与 no-successor recovery；S6g 已补一次 running fixed-ID
generation handoff；S6h 已补一次 same-boot running JobService owner-death 后 retained
exact W 跨 owner epoch redispatch/reconcile，其中 API 35 仅是 exact recheck 后的
forced-eligibility command acceptance 与 invocation-boundary 后 positive callback
的非因果 proof。production ACK/ticket source rule 已由 P1 固定；最紧邻的
delivery blockers 是
真实 external-UID/release device matrix，以及 framework-delivered/并发
stale callback（含 stale `onServiceConnected()`）、stale/duplicate
register/notification/token transaction、hung owner transaction、post-check 后外部
取消/system state loss、owner/compiler 同时崩溃、真实 reboot/force-stop、连续
backoff/crash-loop policy，以及 external-caller ownership/delivery/retry 尚未冻结。
遇 `UNREADABLE` 必须继续 fail closed；仅在 poisoned coordinator 内重置 Boolean
或释放 lease 仍不构成证明。

此外仍需完成：

- reboot、force-stop、system_server restart、不同 worker phase/连续多次或
  system-triggered JobService process restart/redelivery、same-ID handoff 中的 owner
  death、API 35/OEM natural retry runtime、连续 backoff、Job
  cancellation/reschedule、`onStopJob`/main-looper completion 竞争、marker
  corruption 与 procfs/boot identity `UNREADABLE` 的完整 device fault matrix；
- external-caller owner/callback/device tests 与 release full-pipeline 证据；
- 完整 P3b-b external-UID debug/minified-release、active renderer/PSS、分阶段
  cancel/timeout、OOM/LMK、重复大任务/recovery gates；production admission 仍为
  512 files/8 MiB total，Worker 最终仍可能同时持有 String/AST；
- 真正覆盖 128 MiB container/256 MiB uncompressed 的 file-backed/compressed
  output writer；
- callback death/service destroy/poison 的完整 device fault matrix；
- request/output/diagnostics v1 schema freeze、正式 Rhino/NodeNext profile 和
  全部 R2 exit gates。
