# R2 Provisional Compiler Process Supervisor

状态：R2-S6a `DEBUG` / same-UID active-termination proof、R2-S6b system-owned
persisted post-death reconciliation owner，以及 R2-S6c app-private production-build
live-epoch owner；R2-S6d 另补 owner hard-kill/rebind 与 registration-gap poison
replay，R2-S6e 再补 successor-ready 后的 retired-generation teardown callback
replay fence，R2-S6f 再把 exact persisted Job ownership 前置到 durable marker
commit 之前，R2-S6g 再补一次 running fixed-ID same-ID/different-token generation
handoff 的 token gate 与 framework callback observation，R2-S6h 再补一次 running
JobService owner process death 后同一 exact work generation 的跨 owner-epoch
redispatch/reconcile proof，R2-S6i 再从 shared validation barrier 并发发出 owner/
compiler exact kill request，并以两次调用都返回的 monotonic time 约束 retained work
后续 callback。S6c 已 provisionally
补齐 exact registration/authorization/live
termination control-plane，S6d 补齐单次 owner restart/reregistration 子项，S6e
补齐 DEBUG 同步 retired teardown handler fence 子项，S6f 补齐 producer
mark-before-schedule crash window 与 no-successor system-owner proof，S6g 收窄一次
running replacement/`onStopJob()` token-fence 子项，S6h 收窄一次 same-boot running
JobService owner-death/retained exact-work redispatch 子项，S6i 收窄一次 retained
exact-work overlapped kill-request 子项；S1-S6i 仍是
internal slices，**不是**完整 production process supervisor，也没有关闭 public
R2 的 supervisor blocker。

本文记录当前位于 `:typescript_compiler` 之外的进程恢复原语。cleanup 无法确认后的
poisoned compiler epoch 可由 app-private owner 关联到 exact remote Binder，要求该
Binder 所属进程自行终止，同时观察旧 Binder 与旧 procfs identity 消失；在死亡证明
完成前，durable quarantine 会拒绝 successor admission。

S6a active control plane 仍复用 frozen Binder method 中的 private `DEBUG` Bundle
metadata；它不是 public AIDL、public Bundle contract、production ticket 或
capability。S6b 不复用该 control plane，也不能命令一个 live epoch；它只从
app-private durable marker 恢复 exact identity。S6c 使用只位于 app module 的 typed
AIDL 和非导出 bound Service；它存在于 production build，但同样不是 public plugin
AIDL、ticket 或 capability。S6d 修改的是该 app-private client 的 bind-generation
状态机；registration pause/PID telemetry 与 S6e retired callback replay 都仅为
`DEBUG && same UID` fault seam。S6f 改变 production termination/Job scheduling
顺序；其强制早到 Job 与 extended termination delay 只作为 `DEBUG && same UID`
deterministic proof seam。S6g 的 generation gate 属 production internal
JobService 状态机；callback journal、durable test cutoff 与 handoff delay 仍只用于
`DEBUG` observation，不进入任何 authority。S6h 不改变 production owner protocol；
其 exact owner kill 与 `cmd jobscheduler run -f -u <current-user-id>` fallback 都是
DEBUG/same-UID
instrumentation seam。该 fallback 只在精确复核 scheduler-tracked work 后，请求
fixed-ID Job 的 forced eligibility；shell invocation 与 exact work 并非原子绑定，
其后 callback 仍独立按 token/owner/time 匹配。它不证明 shell 因果，也不建立
API 35 OEM 自然 retry 的 production guarantee。S6i 同样只增加 DEBUG/same-UID
instrumentation fault seam；并发 kill request 不改变 production Job/marker protocol。

## 边界与威胁模型

S6a 只允许：

- debug build；
- caller UID 与 plugin application UID 相同；
- supervisor 当前进程不是 `:typescript_compiler`；
- endpoint 是 remote `ITypeScriptCompilerPlugin` Binder，而不是 local Binder；
- compiler process-wide admission 已进入 `SUPERVISION_REQUIRED`；
- request 的完整 epoch identity 与该 Binder 所属 compiler epoch 精确一致。

S6b/S6f/S6g/S6h/S6i 的 Job owner 边界不同：

- `JobScheduler` 是 work lifecycle owner；
- JobService 位于独立 `:typescript_supervisor` process；
- 组件只能由持有 system signature permission
  `android.permission.BIND_JOB_SERVICE` 的系统调度器绑定；
- persisted extras 使用 strict schema，绑定 mode、256-bit work token、完整 old
  epoch，以及 PREARM mode 的 monotonic command deadline；
- owner 没有任意 PID signal 或 live compiler termination authority；
- 只有 work 与 marker identity exact-match 且 `ABSENT/REUSED` 才可 exact-clear；
  `SAME/UNREADABLE`、corrupt work/marker 或 stale generation 全部 fail closed。

S6c/S6d/S6e 的 live control-plane 边界为：

- 非导出的 `R2CompilerProcessSupervisorService` 位于独立
  `:typescript_supervisor` process，无 permission、无 intent filter；
- registry 只接受 application UID、Binder-authenticated exact caller PID、
  canonical compiler identity、与 supervisor self identity 相同的 boot ID、固定
  compiler process name、exact remote AIDL descriptor 与存活 callback Binder；
- compiler 反向校验 supervisor Binder caller UID/PID、与 compiler self identity
  相同的 boot ID、正数 start-time 与固定 process name；256-bit registration
  token 只有双向握手完整后才激活；
- online handshake 只从 procfs capture self identity，不读取 non-debuggable
  sibling process。registration 未 ready、self identity 不可读、descriptor/
  identity 歧义、stale generation 或 deadline 不可用时全部 fail closed；
- owner 只能调用 exact callback Binder；compiler 只在自身 process-wide admission
  已 poisoned 时执行 exact self-termination，owner 没有任意 PID signal authority。

supervisor 不接受一个裸 PID 后直接调用 `kill(pid)`。numeric PID 会复用，而且调用方
提供的 PID 无法证明仍属于原 Binder。S6a DEBUG proof 的顺序是：

```text
same-UID process outside :typescript_compiler
  -> read DEBUG identity from the exact remote Binder
  -> validate expected UID / process name / non-local Binder
  -> linkToDeath(exact Binder)
  -> cross-check exact epoch against procfs
  -> send one private TERMINATE_POISONED_EPOCH with an absolute command deadline
  -> compiler validates the complete epoch tuple
  -> compiler validates the deadline before durable mutation
  -> compiler pre-arms an exact persisted Job and reads back the full work generation
  -> compiler durably installs the quarantine marker
  -> compiler post-checks that the exact Job generation is still pending
  -> compiler revalidates the deadline
  -> compiler schedules delayed Process.killProcess(Process.myPid())
  -> supervisor observes Binder death
  -> supervisor observes procfs ABSENT or REUSED
  -> synchronous supervisor or system Job exact-clears the marker
  -> an inert successor self-recycles and held BIND_AUTO_CREATE reconnects
  -> a fresh process authority opens admission
```

因此 termination authority 留在 kernel-owned Binder endpoint 与 compiler 本身；
supervisor 不向 caller-supplied numeric PID 发信号，也不能要求一个 healthy epoch
自杀。stale nonce、wrong UID/name/start time、local Binder、仍健康的 admission
或显式 command rejection 都 fail closed。

## Exact process epoch identity

`R2CompilerProcessEpochIdentity` 由以下字段组成：

```text
+ kernel boot ID
+ UID
+ PID
+ /proc/<pid>/stat start-time ticks
+ exact process name
+ process-lifetime 256-bit SecureRandom nonce
```

nonce 编码为 64 个 canonical lowercase hex characters，在一个 PID 的 Service
recreation 间保持不变，只在真正的新进程中重新生成。termination request 必须回显
全部字段；nonce 使用 constant-time byte comparison。

boot ID 编码为 canonical lowercase UUID，并从
`/proc/sys/kernel/random/boot_id` 有界读取。跨 reboot 时，即使 numeric PID、
start-time ticks、UID 与 process name 恰好重新出现，boot ID 不同也会把旧 epoch
归类为 `REUSED`；boot ID 缺失或 malformed 则为 `UNREADABLE`，保持 fail closed。

PID 与 start-time 解决同一 boot 内的普通 PID reuse，process name 与 UID 限制预期的
`:typescript_compiler` 身份，nonce 则把控制命令绑定到从 exact Binder 读取到的
进程 epoch。任一字段不匹配时，compiler 不写 marker、不安排自杀。

## Binder death 与 procfs 四态

supervisor 在发送命令前先对 exact remote Binder `linkToDeath`。procfs 只作为独立
交叉证据，不代替 Binder identity。`R2CompilerProcessProcfs.observe()` 返回四种
状态：

| 状态 | 含义 | 可否作为旧 epoch 已离开的 procfs 证据 |
| --- | --- | --- |
| `SAME` | PID 的 start time、UID 与 process name 都仍匹配 | 否 |
| `REUSED` | boot ID 已变化，或 PID 可见但 start time、UID/process name 不匹配 | 是 |
| `ABSENT` | `/proc/<pid>` 返回 `ENOENT`/`ESRCH` | 是 |
| `UNREADABLE` | procfs 存在但无法取得无歧义快照 | 否，fail closed |

读取采用有界 kernel boot ID、`/proc/<pid>/stat` 与 `cmdline`，并在 UID/name 检查
前后读取两次 `stat` start time，避免在一次观察中拼接两个 PID epoch。`stat`
parser 从最后一个 `)` 后取 field 22，允许 process `comm` 自身包含空格或括号。

最终 `deathProven` 必须同时满足：

```text
exact old Binder death
AND
(procfs ABSENT OR procfs REUSED)
```

仅 Binder death、仅 procfs absent、`SAME` 或 `UNREADABLE` 都不会开放 successor。

## Durable quarantine 与 null-binding fence

compiler 只有在 exact poisoned-epoch command 验证成功后，才通过 `AtomicFile` 在
app `noBackupFilesDir` 写入 quarantine marker。marker 包含完整 epoch identity，
使用含 boot ID 的有界 v2 text payload，但故意保留原 v1 pathname：
`r2-compiler-process-quarantine-v1`。因此升级前留下的 v1 payload 仍然可见，却无法
通过 v2 exact parser，会保持 quarantine 并 fail closed，而不会因换 pathname 被
静默绕过。写入完成后同时验证内容并 `fsync` marker 所在目录。

`AtomicFile` 只保证单次写入，不保证跨进程 compare-and-delete。所有 exact
read/mark/clear 还共同经过一个进程内 monitor 和独立 lock file 上的 OS file lock；
identity 比较、clear authorization callback、删除与 directory `fsync` 位于同一个
marker critical section。这样旧 clearer 无法在读取 epoch A 后删除后来写入的 epoch B，
封闭跨线程及跨进程 ABA。

S6f 把顺序改为 exact persisted Job pre-arm/readback → marker commit → exact Job
post-check → delayed self-termination。pre-arm 在 strict persisted extras 中携带完整
epoch、unique work token 与 command deadline；`schedule()` 成功后还必须由
`getPendingJob()` readback 同一 component 与完整 work generation。marker 在 deadline
仍可用时 durable commit 后，compiler 再 post-check 同一 Job 仍 pending，最后才在独立
daemon thread 延迟调用 `Process.killProcess(Process.myPid())`。

任一步失败都会先执行 exact-clear-or-already-absent；只有 marker 已确认不存在时，
才 best-effort conditional-cancel 同 token pre-arm。若 marker clear 无法证明，则
Job 保留为 recovery owner，compiler 拒绝安排自杀。这个顺序关闭的是 producer
“marker 已提交但 persisted Job 尚未建立”crash window；marker 与 JobScheduler
仍不是原子事务，post-check 后的系统/外部取消或 scheduler state loss 不在该声明内。

这个 marker 关闭了“旧进程已经安排自杀，但 supervisor 尚未证明死亡”与“Android
抢先创建 successor Service”之间的窗口。所有 build type 的 successor
`Service.onCreate()` 在看到 marker 时：

- 不打开 `R2ProcessAdmissionAuthority`；
- 不创建 compiler host/coordinator；
- `onBind()` 返回 `null`；
- `onDestroy()` 不伪造任何 cleanup proof。

supervisor 只有在 Binder death 加 `ABSENT/REUSED` 已经成立后，才执行
exact-or-already-absent marker clear；marker 存在但 identity 不匹配、内容损坏或
目录 durability 无法确认时不会删除。清除后再次 `fsync` 目录，之后的新 bind 才能
创建新的 process singleton authority。已经返回 null binding 的 Service 不能原地
升级 Binder，因此 inert successor 由独立后台 watcher 观察 marker 消失后 self-kill
一次；AMS 随后为仍存活的 `BIND_AUTO_CREATE` connection 启动无 marker 的新进程。
同一 watcher 以有界间隔尝试提交 marker recovery Job；`onBind()` 只在 watcher 未
激活时重试启动并立即返回 null，避免 Service main thread 等待跨进程 marker lock。
一次瞬时 thread-start/schedule failure 会由后续 bind 或 watcher retry 重试。

如果 supervisor 在旧进程死亡后、exact clear 之前重启，
`reconcilePersistedQuarantine()` 会重新读取 marker 中的完整 identity。只有 procfs
明确返回 `ABSENT` 或 `REUSED` 时才 exact-clear；`SAME`、`UNREADABLE`、损坏 marker
或 scope 不匹配全部保持 fail closed。

## S6b system-owned reconciliation owner

main manifest 新增 `R2CompilerProcessSupervisorJobService`：

```text
process=:typescript_supervisor
exported=true
permission=android.permission.BIND_JOB_SERVICE
directBootAware=false
```

`exported=true` 是 JobScheduler system binding 的组件要求；system signature
permission 是外部授权边界，组件没有 intent filter，也没有 app-callable Binder
contract。manifest 同时声明 `RECEIVE_BOOT_COMPLETED`，JobInfo 使用
`setPersisted(true)`，向系统请求已提交 work 的 reboot persistence；CE
`noBackupFilesDir` 在 unlock 后可用。真实 reboot/system_server persistence 与
跨 reboot deadline 语义仍属于开放 device matrix。

JobScheduler 使用固定 job ID、2 秒 minimum latency、5 秒 initial deadline 与
10 秒 exponential backoff。S6f 的 schema-1 `PersistableBundle` exact keys 为：

```text
mode + 256-bit work token
+ boot ID + UID + PID + start-time ticks + process name + process nonce
+ PREARM_EXACT only: elapsedRealtime command deadline
```

所有 app-side pending query/schedule/conditional-cancel 都经过进程内 monitor 与
app-private Job lock file 的跨进程 OS lock。`prearmExact()` 每次生成新 token，
schedule fixed ID，并要求 `getPendingJob()` exact readback component 与完整 extras。
`scheduleIfQuarantined()` 在同一 lock 下读取 marker；只有 pending component/schema
有效且 work identity exact-match marker 时才复用，否则调度新的
`RECOVERY_EXACT` token。意外 component 占用 fixed ID 时 fail closed。

因此 same-ID/different-token replacement 是有意支持的 producer 行为，而不是可忽略
的 scheduler 细节。进程内 `onStartJob()` 以 token 区分 generation：新 token 可退休
旧 worker；迟到的旧-token `onStopJob()` 不停止或 reschedule replacement。该 fence
已有 deterministic code/JVM proof；S6g 又把一次 running replacement 接入
framework-delivered callback observation。S6h 再让同一 exact work generation 在
running owner P1 死亡后由不同 owner epoch P2 接收并 reconcile；S6i 又在 retained W
下从 shared validation barrier 并发发出 P1/old-compiler exact kill request。两者只
覆盖 same-boot、`PREARM_RETRY` phase 的单次窗口；连续/重叠 replacement、其他
callback ordering、不同 phase/连续多次/system-triggered process death，以及 API 35
OEM 的自然 retry runtime 仍开放。

`onStartJob()` 不执行 marker、procfs 或 telemetry 文件 I/O，只登记 running job 并
启动 daemon worker；读取 marker、procfs observation、restart reconcile 与最终
telemetry 全部在 JobService main callback 之外执行。worker 按 exact work/marker
组合处理：

- exact marker + matching work：`ABSENT/REUSED` 才 exact-clear + directory
  `fsync`；`SAME/UNREADABLE` 保留 marker 并 retry；
- marker absent + live PREARM whose deadline window remains open：
  `SAME/UNREADABLE` 先记录 `PREARM_RETRY`，worker 继续轮询 marker/death transition；
  `ABSENT/REUSED` 必须在 marker lock 下重查，仍 absent 才 `NO_WORK`；
- marker absent + expired pre-arm、recovery work 或 malformed/legacy extras：
  `NO_WORK`；corrupt/unreadable marker 保留并 retry；
- marker identity 与 work identity 不同：`STALE_JOB`，绝不 clear current marker。

`onStopJob()` 不执行文件 I/O，也绝不把 stop 当作 cleanup proof。只有 params extras
解析出的 work token 与当前 running generation 相等时，才以
`RUNNING -> STOPPED` CAS 与 clear authorization 竞争并请求 system reschedule；
different-token late stop 直接忽略。Service 不依赖 scheduler 的独立 start/stop
Binder callbacks 之间 `JobParameters` 的引用相等性。
worker 的 clear callback 位于上述 marker file lock critical section 内，以
`RUNNING -> AUTHORIZED` CAS 作为 stop/clear linearization point：stop 先赢则 callback
拒绝删除；clear 先赢则 exact mutation 已获授权，`onStopJob()` 只抑制稍后的
completion。

worker 不直接调用 `jobFinished()`。每个 running job 的 `completionPosted` CAS
至多向 main looper post 一次 completion，并保持 `running` 记录直到该 runnable
执行；只有该 main-looper runnable 才可在 job 仍为 current 且未 stop 时移除
`running` 并调用一次 `jobFinished()`。stop、stale job 或正在退出且无法接受 post 的
looper 都不会发送迟到 completion。worker 的 best-effort AtomicFile telemetry
记录 owner boot/UID/PID/start-time/process name、work-token/旧 epoch nonce digest、
观察结果与 `RUNNING/PREARM_RETRY/RECONCILED/RETRY/NO_WORK/STALE_JOB`。它从不参与
clear 或 admission 授权；record write 与随后的 running-generation recheck 不是一个
跨线程原子 transaction，所以 telemetry generation 不是完全线性化的权威状态。

因此 S6b 已把“已成功提交 persisted Job、且 old epoch 已离开后的 system-owned
invocation/reconcile”接入真实 Android 生命周期；S6f 又在 marker commit 前建立并
readback exact work、commit 后 post-check，并由后台 watcher 保留 marker recovery
补调度。它仍没有任意 live PID kill authority，也不把两套 system state 声称为原子
transaction。若 marker 对应 `SAME`，quarantine + backoff 是正确的 fail-closed
结果，不是恢复成功。S6h 另证明一个 running JobService owner process epoch 消失后，
同一 exact work 可在不同 owner epoch 中再次进入 callback 并完成 reconcile；它不把
test-side process kill 或 API 35 forced-eligibility invocation 表述为 autonomous
system-kill/natural-retry guarantee。

instrumentation 在 supervisor 已取得死亡证明、但故意暂停 marker clear 的窗口内
并发 bind，验证 successor 只能 null-bind，不能泄漏一个可用 compiler Binder。
随后解除暂停、exact-clear marker，并保持原自动绑定直到它跨进程自动重连，再完成
一次 accepted/cancelled probe。另一个 test 从持久 marker 模拟 supervisor restart，
验证 `ABSENT` reconcile 与 held binding recovery。这是 durable
quarantine/null-bind fence 的 race proof。S6b 又让 system Job owner 自动执行同一
reconcile，增加 live `SAME` 不清 fence 的负向设备证明，并用两个 marker generation
验证 stale exact-clear 不能删除 current marker。

## S6c app-private live-epoch owner

main manifest 另新增 `R2CompilerProcessSupervisorService`：

```text
process=:typescript_supervisor
exported=false
permission=<none>
directBootAware=false
intent-filter=<none>
```

它与 S6b JobService 运行在同一独立进程，但具有不同授权边界：JobService 由系统以
`BIND_JOB_SERVICE` 绑定；S6c Service 是 application-private explicit bound
Service。两条 `IR2CompilerProcess*` typed AIDL 只位于 app module，不进入
`plugin-api`，因此没有修改 frozen public AIDL transaction、contract hash 或 ZIP
v1。

compiler Service 在打开 production prewarm availability 前启动 process-lifetime
client。registration 顺序为：

```text
:typescript_compiler
  -> explicit bind non-exported live owner
  -> validate exact ComponentName + remote registry descriptor
  -> registry validates Binder caller UID/PID + canonical compiler epoch
  -> registry binds compiler boot ID to supervisor self boot ID + fixed process name
  -> registry pins exact remote callback Binder with linkToDeath
  -> registry generates 256-bit token
  -> registry challenges callback with supervisor self identity + token
  -> compiler validates Binder caller UID/PID/self-boot/start-time/fixed process-name
  -> compiler stores a pending handshake only
  -> registry publishes exact endpoint registration
  -> registerEpoch returns the same token
  -> compiler atomically promotes pending handshake to active registration
```

任一步失败都不会产生 half-ready registration。alive callback 保持
authoritative，不能被另一个 endpoint 覆盖；duplicate exact registration 必须
重做 same-token challenge。registry state 属于 supervisor
process，而不是某个 Service instance；callback/registry Binder death、disconnect
和 rebinding 使用 connection generation 清除 stale token，再对当前 generation
重注册。注册与 poison retry 都 coalesce，但 timer 每次重新读取当前 registry；
旧 generation 不能吞掉新 owner 的唯一重试。

`R2GlobalAdmissionArbiter` 只在首次 false → true poison 转换后、在 arbiter lock
之外通知 client。poison observation 单调保留；如果 poison 先于 registration，
active token 建立后会自动 replay。owner 收到 exact epoch/token 后启动现有 bounded
supervisor engine，后者 pin callback Binder、发出 typed command，并要求 Binder
death + procfs `ABSENT/REUSED` 后才 exact-clear quarantine。一次通知 ACK 只表示
engine 已接管，不表示 recovery 已完成；compiler 存活期间会继续幂等重试。

typed callback 再次验证 active token、registered supervisor UID/PID 与 Binder
caller、完整 compiler epoch、process-wide poison 与 absolute deadline，然后复用
S6f 的 exact
persisted-Job pre-arm/readback、durable marker、Job post-check 与 delayed exact
self-kill。该链没有 `kill(callerPid)` 或任意 PID authority。release build 默认启用
automatic handoff，且 prewarm 在 registration ready 前返回 unavailable；DEBUG
设备测试必须由 same-UID private key 显式启用同一路径。ordinary public
`startCompilation` 仍按 R0 fail closed。

## S6d owner restart/rebind 与 registration-gap replay

S6d 修正了 S6c client 的 bind generation 边界。此前所有 bind attempt 复用同一个
`ServiceConnection`，generation 又在 `onServiceConnected()` 才生成；旧 attempt
迟到的 `onServiceDisconnected()` / `onBindingDied()` / `onNullBinding()` 因而无法
辨认来源，理论上可以清除并 unbind 已建立的 successor registry。

当前每次 `bindService()` 前即分配单独的 `GenerationServiceConnection` 和单调
generation。所有 framework callback 与 registry death recipient 都携带 source
connection；只有同时满足以下条件才能改变 client authority：

```text
activeConnection === source
+ source.generation == current generation
+ registry Binder identity matches（Binder death path）
```

退休当前 attempt 会在同一 client lock 内清除 registry、registration token、
supervisor identity/PID 与 pending handshake，再在锁外 unlink death recipient、
unbind exact source 并合并 retry。旧 attempt 的 callback 最多成为 no-op，不能
unbind successor connection。registration/notification retry 仍在每次执行时读取
then-current registry/generation；poison observation 不随 owner death 清除。

为确定性制造“successor registry 已连接但 registration authority 尚不可用”的
窗口，DEBUG same-UID prewarm 可暂停新 token handshake。该 gate 不停止 owner
process，也不阻止 Binder connection；challenge callback、register worker 和最终
token promotion 均重新检查 pause。pause 与一次注册重叠时不会产生
`paused=true && ready=true` 的新 token；resume 只注册 then-current live registry，
duplicate endpoint 仍须完成 same-token challenge。release build 的 gate 固定为
false，automatic termination 仍默认启用。

同一锁内 telemetry snapshot 只向 DEBUG instrumentation 提供 registry-connected、
registration-ready、gate 状态，以及已经通过反向 handshake 的 supervisor
PID/start-time；它不暴露 token。设备测试再以 boot ID、UID、完整 process name 与
双 stat start-time 快照交叉验证该 PID。

## S6e retired-generation teardown callback replay fence

S6e 不依赖 Android framework 自然产生一个可重复的迟到 callback。DEBUG/same-UID
one-shot seam 分为 `ARM`、`REPLAY`、`RESET`：

1. `ARM` 只接受 exact ready generation，并记录 active
   `GenerationServiceConnection`、generation 与 registry Binder identity；
2. 只有该 exact connection 真正在 `restartBinding()` 中退休，才捕获它的 source
   connection 与 `RegistryDeath`。未被 arm 的历史 Binder 从不保存；
3. `REPLAY` 要求 successor connection 不同、generation 严格递增，且 successor 已
   完成 registry/token/owner handshake；
4. seam 各重复两次调用 retired `onServiceDisconnected()`、API 26+
   `onBindingDied()`、API 28+ `onNullBinding()` 与旧 registry death recipient；
5. 每次 delivery 后同步复核 successor 的 active connection/generation、registry
   Binder、`RegistryDeath` object/generation、registration token、owner exact
   identity/PID 与 Binder live 状态均未改变。

replay snapshot 一次即消费；成功或 `RESET` 后不再持有 retired connection/Binder。
返回 telemetry 只有 old/current generation、delivery count 与 state-preserved，
不暴露 token。capture 位于 `BuildConfig.DEBUG` 分支，release client 不保存 retired
Binder。

这个 seam 是在一次 prewarm Binder transaction 中**串行直接调用旧 handler**，证明
retired source 的迟到/重复 teardown handler 无法退休已 ready successor；它不是
Android framework 的真实投递，也不证明与 register/death transaction 的真实并发。
它还不覆盖 stale `onServiceConnected()`、旧 register/notify/token transaction 或
hung Binder call。

## S6f exact persisted-Job pre-arm

S6f 让 system-owned recovery work 在 admission fence 之前存在。compiler 通过
`prearmExact()` 取得包含 exact work 的 lease；只有 schedule 成功并 exact readback
后才进入 marker critical section。marker commit 又要求 command deadline 在该 lock
内仍有可用预算，commit 后再以 lease 的完整 extras/token post-check pending Job。
termination thread 延迟启动，且真正 self-kill 前最后一次拒绝已过期 deadline。

PREARM Job 可能早于 marker 启动，这不是无工作状态：

1. worker 在 marker lock 下确认 marker absent 且 pre-arm deadline window 仍开放；
2. procfs `SAME/UNREADABLE` 时记录 `PREARM_RETRY`，继续在 daemon worker 上等待，
   不阻塞 JobService main callback；
3. marker 随后 exact-commit 时，worker 转入普通 reconcile；old epoch
   `ABSENT/REUSED` 后 exact-clear；
4. 若 old epoch 先 `ABSENT/REUSED`，worker 在 marker lock 下重查，仍 absent 才
   `NO_WORK`，避免漏掉观察与死亡之间的 marker commit；
5. deadline 已关闭且 marker absent 时 pre-arm 不再拥有未来 mutation，因而
   `NO_WORK`。

失败回滚遵循 fence-first 顺序：`clearExactOrAbsent(expected epoch)` 成功后，才在
Job lock 中确认 marker 仍 absent、pending component/extras/token 仍等于 lease，
再 best-effort `cancel(fixed ID)`。JobScheduler 没有 conditional-cancel primitive，
所以正确性来自 deadline 在 marker lock 下关闭未来 commit，而不是 cancel 本身。
marker 无法 exact-clear 时绝不 cancel recovery owner。

这个 protocol 只关闭“producer 已 durable-commit marker、却在建立 Job 前崩溃”的
窗口。pre-arm、marker 与 post-check 横跨 JobScheduler state 和 app-private durable
file，不是原子 transaction；post-check 后的外部取消、system_server state loss、
reboot/force-stop persistence、跨 reboot `elapsedRealtime` deadline，以及 running
same-ID/different-token 的连续/重叠 replacement、已观察矩阵之外的
`onStopJob()` ordering 与 handoff 中 JobService restart 仍需 fault matrix。

## S6g running fixed-ID generation handoff

S6g 演练一个 live fixed-ID generation handoff。generation A 先作为 exact
PREARM work 运行并到达 `PREARM_RETRY`；随后 production exact-termination path 用
相同 fixed Job ID、不同 256-bit work token 调度 generation B。共同的
`R2CompilerProcessSupervisorJobGenerationGate` 以 token 而不是
`JobParameters` 引用决定 generation：

- `STOP(A) → START(B)` 先退休 A，再安装未退休的 B；
- `START(B) → late STOP(A)` 在 B 已 current 时把 stop 判为 `STALE`，不退休、
  clear 或 reschedule B；
- replacement 与 current stop 都先使旧 generation 的 clear phase 失去
  `RUNNING` authority，再改变 current reference；已经赢得 `AUTHORIZED` 的 exact
  marker mutation 不被事后撤销。

为观察真实 callback，S6g 增加 `DEBUG`-only、bounded、non-authoritative lifecycle
journal。JobService main callback 只执行固定 64-character token canonicalization、
token relation comparison、process-local sequence、`elapsedRealtimeNanos`/PID capture
与最多 32 条的 memory enqueue；不读取 procfs、不计算 digest、不取得文件锁，也不写
文件。daemon writer 才执行 procfs owner capture、SHA-256、strict printable-ASCII
codec、跨进程 lock 与 `AtomicFile` commit。持久记录最多 64 条，总文件最多
32 KiB；落盘只含 token digest，不含 raw token。

测试 clear 使用 `{kernel boot ID, elapsedRealtimeNanos}` durable cutoff。clear 之前
已在另一个 `:typescript_supervisor` process 排队或 drain、却尚未取得 journal file
lock 的 event，稍后写入时仍因 `event <= cutoff` 被拒绝；boot 改变时 cutoff epoch
重置。parser 同时约束 header/record type/field count、ASCII、canonical digest、
owner UID/process name、每个 process identity 的严格 sequence/time 单调性，以及
event/decision/token 的 gate-reachable 关系。corrupt state 对普通 writer fail closed；
显式 DEBUG clear 可以建立新的 cutoff state。

本次有界窗口按 token 正向观察 A/B 的 START 与 A 的 STOP callback，最终 durable
exact owner 属于 B。A 的迟到 stop 不影响 B 由 pure gate policy 与上述
token-scoped positive trace 共同支撑；journal 的 daemon、容量淘汰或写失败都可能
丢 observation，因此不得从 record absence、最终 count 或文件 append 顺序推导
framework 全局 exactly-once/线性化。

该 slice 只关闭一次 running fixed-ID/different-token handoff。连续或重叠多次
replacement、当前设备矩阵之外的 callback ordering、handoff 期间 JobService
process death/restart、尚未观察的 completion/`onStopJob()` race、continuous
crash/backoff policy、reboot/force-stop/system_server persistence 与外部取消仍开放。

## S6h running JobService owner death and exact-work redispatch

S6h 不创建 replacement generation。设备测试
`runningSystemJobIsRedeliveredAfterSupervisorProcessDeath` 先 pre-arm exact
`PREARM_EXACT` work generation W，等待 W 在 JobService owner P1 中到达
`PREARM_RETRY`，并由 lifecycle record 取得 P1 的 boot ID、UID、PID、procfs
start-time 与 process name。测试确认 P1 是 live exact
`:typescript_supervisor` owner、scheduler-tracked work 仍为 W，再 durable-commit 与 W
携带的 old compiler epoch 精确匹配的 marker。W 使用 DEBUG/test-only 的 120 秒
PREARM monotonic command deadline，以建立 deterministic fault window；它不是
production deadline、retry latency 或 backoff guarantee。

测试随后在 same UID 下 exact-kill P1，并以 procfs process identity 证明 P1 已消失；
marker 必须仍是同一 old epoch。之后测试关闭唯一 compiler binding，仅在 exact old
compiler epoch 仍为 `SAME` 时执行 test-side exact kill，再证明 old Binder、FD 与
process 已消失且 procfs 为 `ABSENT/REUSED`。这一步是 deterministic device fault
injection，不是 Android system kill 的生产证据。

post-kill redelivery predicate 只接受：

- event 为 `START_CALLBACK`；
- work-token digest 精确等于 W；
- event time 严格晚于 P1 kill boundary；
- callback owner identity 不等于 P1。

测试先给 framework 30 秒自然重投窗口；若未得到 positive record，会重读 journal
一次以排除 writer 落盘 race。仍未命中时，测试必须先证明 marker 仍为同一 old epoch、
`pendingWorkForTesting()` 仍精确等于 W，并在 `JobScheduler.allPendingJobs` 中确认
恰有一个 component/extras 与 W 匹配的 Job；之后才由 DEBUG instrumentation 执行
`cmd jobscheduler run -f -u <current-user-id> <package> <existing-job-id>`。只有
stdout 精确等于 `Running job [FORCED]` 才记录命令已接受。该 fallback 不调用
schedule、不替换 fixed-ID generation，也不产生新 work token；`allPendingJobs`
只能证明 scheduler 在 recheck 时仍跟踪 exact W（该 Job 也可能已 active），shell
命令按 package/fixed ID 而不是 W 原子寻址。测试因此仍需独立观察同 token W 的
post-kill callback，而不把命令回执当作 redispatch proof。

最终 `RECONCILED` owner P2 必须携带同一 W token digest 与 old-epoch nonce digest。
P1/P2 的 boot ID、UID 与 process name 相同，但 exact owner identity 不同；P1 已先
得到 disappearance proof。compiler-side live-owner binding 可能在 Job callback 前
创建 P2，因此 S6h 只声称观察到 framework 对 P2 的 W callback，不声称由
JobScheduler 首先创建 P2。P2 exact-clear marker 并完成 W 后，测试才 fresh-bind
不同 compiler epoch，验证 accepted + ACK + cancel、empty output 与 FD release。

API 24、25、28 的当前 device evidence 在 30 秒窗口内自然观察到 W 的 post-P1
`START_CALLBACK`。API 35 的 pre-final diagnostic 曾等待 90 秒自然重投：framework
日志显示约 11 秒后的 restart 安排，但观察窗内没有出现 post-P1 positive callback；
最终测试改为 30 秒自然窗口后执行上述 exact recheck + forced-eligibility
request。成功证据记录 shell invocation 前的 monotonic boundary，并按所有已观察
qualifying W START 中最早一条分类；boundary 前记录归 `NATURAL_FORCE_RACE`，
只有最早记录严格晚于 boundary 才记为非因果的
`POST_FORCE_INVOCATION_BOUNDARY`。因此 API 35 只证明
recheck 时 scheduler 仍跟踪 exact W、命令被接受，随后观察到 W callback/reconcile；
不证明 shell 导致 callback 或 OEM autonomous redelivery latency/backoff。

lifecycle journal 在 S6h 仍只是 bounded、non-authoritative positive observation。
测试不要求 P1 收到 `STOP_CALLBACK`，也不从 record absence、最终 count 或 append
order 推导全局 exactly-once/全排序。不同 worker phase、连续多次或 system-triggered
JobService death/redelivery，same-ID replacement handoff 中的 owner death，
completion/`onStopJob()` race、system kill、system_server restart、reboot、
force-stop、外部取消、corrupt/`UNREADABLE` 与 continuous crash/backoff policy
仍开放。

## S6i retained exact-work overlapped owner/compiler kill requests

S6i 复用 S6h 的 test-only 120 秒 PREARM W，不创建 replacement generation。关闭唯一
external compiler binding 后，测试复核 active FD、marker、scheduler-tracked W、
owner P1 与 old compiler C；两个 kill worker 又分别完成 P1/C exact-live validation，
再通过 shared ready/fire latch 同时取得发出 kill request 的资格。

latch release 后两个 worker 并发调用 same-UID test-side
`Process.killProcess(exactPid)`。owner/compiler signal initiation 的先后不确定；
evidence 只记录两者 initiation/return time 与 initiation span。两次调用都返回后的
`bothSignalsReturnedNanos` 是 qualifying `START_CALLBACK(W, P2)` 的 monotonic 下界。
signal return 不是 death proof；该 seam 不证明 instruction-level simultaneous kill
或两个 process 的 actual death interval overlap。

后续 proof 仍分别要求 P1 与 C exact-disappeared、同一 W 在不同 owner P2 中产生
boundary 后 START、P2 以同一 W token/old nonce `RECONCILED`、marker/Job clear，以及
fresh compiler accepted + ACK + cancel、empty output 与 FD release。API 35 继续复用
S6h 的 exact scheduler recheck、forced-eligibility command acceptance 与
`POST_FORCE_INVOCATION_BOUNDARY` 非因果分类。

S6i 只关闭一次 same-boot、shared validation barrier 后的 test-side concurrent
kill-request window。system/LMK/OOM/force-stop dual death、actual death overlap、
显式穷举 signal initiation order、不同 worker phase、连续/重复 dual fault、
same-ID handoff 中的 dual death、system_server/reboot 与完整 crash/backoff matrix
仍开放。

## 有界 supervisor worker

同步 Binder command 不在 supervisor 调用线程上执行，而是在 daemon command
worker 上执行；每个 supervisor 进程只允许一个不可取消 transaction 在途，避免
超时重试累积 worker。supervisor 使用一个 absolute monotonic deadline 统一轮询
command completion、Binder death 与 procfs：

- timeout 范围固定为 1 s 至 30 s；
- 默认 10 s；
- poll interval 为 25 ms；
- request 还携带基于同一 `elapsedRealtime` 时钟的服务端 absolute command
  deadline；它比 supervisor proof deadline 提前 500 ms，并要求至少 200 ms
  command budget；
- compiler 在 pre-arm 前、marker lock 内、post-check 后和 self-kill 前重新验证
  command deadline；
  transaction 晚到或过期时不会继续产生迟到 self-kill；
- command 明确拒绝且 Binder 仍存活时立即失败；
- 超时、worker 无法启动、Binder 无法 pin、identity 变得歧义或 marker 无法
  exact-clear 时均抛出 supervision exception。

该 worker 只携带 exact Binder command，没有任意 PID kill authority。S6a daemon
worker 和轮询器是 DEBUG active-termination proof；S6b 增加 production-manifest
中的 post-death work owner、persisted scheduling/backoff 配置、successor-bind
补调度和 best-effort telemetry；S6c 用 app-private typed AIDL 把同一 exact
command/death-proof engine 接入 production build 的 live owner；S6d 证明该 owner
单次 hard death 后的 successor connection、reregistration 与 pending poison
replay；S6f 把 exact Job pre-arm/readback、marker commit、Job post-check 与 delayed
self-termination 串入同一 command deadline；S6g 把一次 running fixed-ID
generation handoff 接到相同 token gate，并用独立 DEBUG journal 做正向 callback
observation；S6h 再以同一 journal/telemetry 正向证明一个 running JobService owner
process epoch 死亡后，同一 exact work 在不同 owner epoch 中被派发并 reconcile；
S6i 再把 owner/compiler exact kill request 放到 shared validation barrier 后并发发出。
完整 owner/system lifecycle matrix 仍未完成。

## 已验证的 fault path

同一个
`R1AcceptedLifecycleInstrumentationTest#
cleanupUnprovenRequiresOldProcessDeathBeforeNewProcessAdmission`
执行以下链路：

1. same-UID DEBUG transport job accepted 并 ACK；
2. deterministic cleanup-unproven seam 保留 input/output FD、exact active lease
   与 owner record，poison process-wide admission，不发普通 callback；
3. prewarm 返回 unavailable；old Binder 仍存活，old procfs identity 为 `SAME`；
4. stale epoch termination 被拒绝，进程与 marker 都不改变；
5. exact Binder command 被接受，compiler pre-arm/readback persisted Job，写
   quarantine、post-check exact Job 后再 delayed self-kill；
6. supervisor 同时证明 Binder death 与 procfs `ABSENT/REUSED`；
7. death proof 后暂停 marker clear，并发 bind 只能得到 null binding；
8. exact marker clear 后，clear 前已经持有的自动绑定跨进程恢复，得到不同
   epoch/process identity；
9. healthy successor 拒绝 supervision termination，随后 accepted job 可以 ACK、
   cancel、发布一个 cancelled terminal，并证明 FD 释放与 output 为空。

另一个
`persistedQuarantineReconcilesAfterSupervisorRestartAndRecoversHeldBinding`
从 durable marker 恢复 exact identity，仅在 old PID epoch 为 `ABSENT` 后清除
fence，并验证 held null binding 自动重连。

S6b 的三条新增/扩展设备路径为：

- `persistedQuarantineIsAutomaticallyReconciledBySystemJobOwner`：successor bind
  为注入的 `ABSENT` marker 提交 system work 后，独立
  `:typescript_supervisor` JobService 自动 reconcile，并恢复 held binding；
- `systemJobOwnerNeverClearsAStillLiveCompilerEpoch`：live `SAME` 只得到 `RETRY`，
  marker 与 Binder 都保持；
- `staleExactClearCannotRemoveCurrentMarkerGeneration`：在 marker lock 内验证 stale
  generation 的 exact-clear 不会删除 current generation，覆盖 ABA fence。

S6g 的
`runningSystemJobReplacementHandsOffToNewExactGeneration` 让 generation A 先到达
`PREARM_RETRY`，再通过 production exact-termination path 以相同 fixed ID、不同
token 调度 B。测试窗口按 token 正向观察 A/B START 与 A STOP，随后以 owner
telemetry、exact marker/work reconciliation 和 fresh epoch recovery 确认最终 owner
是 B。pure gate tests 另覆盖 `STOP(A) → START(B)` 与
`START(B) → late STOP(A)`；后者的 stale stop 保持 B current。journal 只用于正向
observation，不以 absence/count 证明没有额外 framework delivery。

S6h 的
`runningSystemJobIsRedeliveredAfterSupervisorProcessDeath` 不调度 replacement：
同一 W 先在 exact owner P1 中到达 `PREARM_RETRY`；marker 与 W exact-match 后，
instrumentation exact-kill P1 并证明该 process epoch 消失，再使 old compiler epoch
离开。API 24/25/28 在 30 秒窗口内自然观察到 W 在不同 owner P2 中的 post-kill
`START_CALLBACK`；API 35 仅在 30 秒自然窗口未观察到 positive callback 后，精确复核
marker 与 scheduler-tracked W，再请求 fixed-ID forced eligibility；命令接受之后才
观察到 W callback。两条路径最终都以 W token/old nonce 的 `RECONCILED` telemetry、
marker clear 与 fresh epoch recovery 收口；不要求 P1 `STOP_CALLBACK`，不把 API 35
记作自然重投，也不推导 shell 因果。

S6i 的
`runningSystemJobIsRedeliveredAfterOverlappedSupervisorAndCompilerDeath` 复用同一 W，
让两个已完成 exact-live validation 的 worker 从 shared latch 并发调用 test-side
exact kill。signal initiation order 不确定；只有晚于 `bothSignalsReturnedNanos` 的
W/P2 START 才可计入，随后仍要求 P1/C disappearance、W/P2 `RECONCILED` 与 fresh
epoch recovery。该 proof 不声称 simultaneous death 或 actual death overlap。

S6a/S6b 历史同源 artifact（不包含后续 S6c）的 hashes 为：

```text
app APK  SHA-256 BD4E53CCE7E007A276901E6F0F60B932AE77EC6E8382E8EDC0DBD77ADE985607
test APK SHA-256 59D4738172D21087825CF5A4D4AE2F1911903A3BC0FA5A273B18A9B424C2D9C6
```

| 设备 | 系统 | lifecycle 结果 |
| --- | --- | --- |
| `emulator-5556` | Android 7.0 / API 24 | clean 分组 `OK (9) + OK (4) + OK (1) = 14/14` |
| `emulator-5554` | Android 7.1.1 / API 25 | clean 分组 `OK (9) + OK (4) + OK (1) = 14/14` |
| `QV710AF65F` | Android 12 / API 31 | `OK (14 tests)` |

同一 app/test APK 的 device aggregate 还包含 R1 service destruction 1、
R2 Binder 6、Output FD 6 与 Project 10；三台设备的三段结果均为
`OK (14) + OK (13) + OK (10) = 37/37`。API 24/25 的 lifecycle class 为上述
clean 分组，API 31 为单次 14-test class；API 24/25 的 renderer-handle case 使用
既有 capability assumption，API 31 实际执行。该矩阵证明 S6a active proof 与 S6b
post-death Job owner/marker concurrency 路径在 API 24/25/31 上成立，不代表
external production caller 或完整 production supervisor 已经完成。

S6c 历史同源 artifact 为：

```text
app APK  SHA-256 61B6CCB88040A0E562B356CDA4137DC133C018D7A4D15F7255D101EB8666B7A5
test APK SHA-256 13F5C46BD496891CDA2E152702C3AA574D987C50F0F8C234F8276D73CA936E63
```

JVM report 为 125 tests / 0 failures / 0 errors / 0 skipped；R2 core、release manifest/APK、
optional-plugin independence、lint、R1 compatibility 与 TypeScript API freeze
聚合门禁全部通过。新增
`cleanupUnprovenIsAutomaticallyRecoveredByProductionSupervisorOwner` 在成功路径
不调用手工 supervisor，直接验证 automatic poison handoff、无 ordinary callback、
old Binder/FD/process 消失、procfs death proof、exact quarantine clear 与新 epoch
accepted + ACK + cancel：

| 设备 | 系统 | S6c 单方法 |
| --- | --- | --- |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` |
| `QV710AF65F` | Android 12 / API 31 | `OK (1 test)` |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` |

API 31 另以同一 artifact 完整运行 lifecycle class，结果为 `OK (15 tests)`。
API 24/25/28/35 本次只补跑新增单方法，不能与历史 37-test aggregate 混算。

这些 device results **没有**提供真实 release APK on-device poison、reboot、
force-stop、live owner/JobService process
restart、连续 backoff 循环、`onStopJob`/main-looper completion 竞争、corrupt
marker 或 procfs `UNREADABLE` fault proof。
`setPersisted(true)`、`RECEIVE_BOOT_COMPLETED` 和 exponential backoff 是当前
production manifest/JobInfo 形状；上述系统事件与循环仍属于开放 device matrix。

S6d 历史同源 artifact 为：

```text
app APK  SHA-256 6AB3E1003FB317D3E2BA5D216B33B0BD3088972BFFE439C01537CD297AB4B2EF
test APK SHA-256 84D8038C257386B0ACDABA2EE761D23AD7E7F3BF9857DB33415C9C8691905C7C
```

JVM report 仍为 125 tests / 0 failures / 0 errors / 0 skipped；R2 core、release
manifest/APK、optional-plugin independence、lint、R1 compatibility 与 TypeScript
API freeze 聚合门禁全部通过。新增
`poisonDuringProductionSupervisorRestartIsReplayedAfterRebind`：

1. 从已验证 registration telemetry 取得 old owner PID/start-time，以完整 procfs
   identity 复核，并临时 pin registry Binder 后立即解除 test binding；
2. 在 accepted + ACK 的 cleanup-unproven job 存活时暂停新 registration，exact
   hard-kill old owner；
3. 证明 old registry Binder death、old owner `ABSENT/REUSED`、不同 successor
   owner 稳定出现、同一 compiler epoch 存活；
4. 证明 successor registry connected 但 registration/token/registered identity
   未 ready，再 cancel 产生 process-monotonic poison；无 ordinary callback，
   compiler Binder 与未证明已释放的 archive FD 继续存活；
5. resume 后由 post-old current owner 完成 handshake/replay，证明 old compiler
   Binder/FD/process death、quarantine exact-clear；
6. fresh epoch 完成 accepted + ACK + cancel、空 output 与 FD release。

| 设备 | 系统 | S6d 单方法 |
| --- | --- | --- |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` |
| `QV710AF65F` | Android 12 / API 31 | `OK (1 test)` |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` |

API 31 另以同一 artifact 完整运行 lifecycle class，结果为 `OK (16 tests)`。
其他四个 API 本次只补跑 S6d 单方法，不能与历史 37-test aggregate 或 S6c
artifact 追溯混算。

该 fault path 没有注入一个被人为延迟、并与 successor transaction 重叠的 stale
callback/notification；它证明 old registration/token 失效与 replay 面向 post-old
current generation。真实 release APK on-device poison、owner/compiler 同时崩溃、
hung owner transaction、重叠 stale/duplicate generation、marker → Job crash gap、
reboot/force-stop、连续 backoff、corrupt marker 与 procfs `UNREADABLE` 仍开放。

S6e 历史同源 artifact 为：

```text
app APK  SHA-256 3F2D71DDBAAC1BD8DD74E04B627AC9033A408318B0FD8A6DDCBD6785E7612B18
test APK SHA-256 9CF88E97A0F8AABC9F61C1EAA9AA0FBDF0EDCEB74B0309A01BA9A9CC3F31F742
```

JVM report 仍为 125 tests / 0 failures / 0 errors / 0 skipped；R2 core、release
manifest/APK、optional-plugin independence、lint、R1 compatibility 与 TypeScript
API freeze 聚合门禁全部通过。新增
`retiredSupervisorCallbacksCannotRetireReadySuccessorGeneration`：

1. 保留 accepted + ACK cleanup-unproven HOLD job、同一 compiler epoch 与 exact
   input/output FD，arm fully-ready old owner connection generation；
2. exact hard-kill old owner并证明 old registry Binder death、old owner procfs
   `ABSENT/REUSED`；
3. 等待不同 successor owner fully ready 且 generation 严格递增；
4. 同步 duplicate replay API 可用的 retired teardown handlers，逐次证明 current
   connection/registry/death record/token/owner 不变，并继续观察同一 owner/generation
   1 秒；
5. 随后 cancel 产生 poison，由 current successor 自动完成 old compiler
   Binder/FD/process death、无 ordinary callback、quarantine exact-clear 与 fresh
   epoch ACK/cancel recovery。

| 设备 | 系统 | S6e 单方法 | 同步 duplicate delivery |
| --- | --- | --- | --- |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` | 4：disconnect + death recipient |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` | 4：disconnect + death recipient |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` | 8：另含 binding-died + null-binding |
| `QV710AF65F` | Android 12 / API 31 | `OK (1 test)` | 8：另含 binding-died + null-binding |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` | 8：另含 binding-died + null-binding |

API 31 另以同一 artifact 完整运行 lifecycle class，结果为 `OK (17 tests)`。
其他四个 API 本次只补跑 S6e 单方法，不与历史 aggregate 或 S6c/S6d artifact
追溯混算。

这是 DEBUG seam 的 sequential direct invocation proof，不是 framework-delivered
callback 或真实并发。stale `onServiceConnected()`、stale register/notify/token
transaction、hung owner、owner/compiler 同时崩溃、marker → Job crash gap、
release on-device poison、reboot/force-stop、连续 backoff、corrupt marker 与 procfs
`UNREADABLE` 仍开放。

S6f 历史同源 source evidence（以下保留当时原文）为：

```text
debug app APK   5,894,411 bytes
SHA-256         9056364D6B136F6B7DBCAF233EECE7A5D82128FF07246B8FF236DDF8ABB273E4
androidTest APK 1,190,685 bytes
SHA-256         71C6ED0AA0241F9703D33B9406334513E333772731E4CCA07DCA502A8E386F9A
release APK     2,548,139 bytes
SHA-256         4981474741F5DFEA708106C8DE23B9A531EDE257E0916E9362D23AFF6DE76A97
```

JVM report 为 126 tests / 0 failures / 0 errors / 0 skipped；R2 core、debug
distribution APK、release manifest/APK、optional-plugin independence、lint、R1
compatibility 与 TypeScript API freeze 聚合 Gradle gates 全部通过。新增
`prearmedSystemJobRecoversPoisonedEpochWithoutSuccessorBinding`：

1. cleanup-unproven HOLD job 保留 exact lease/FD 且 marker absent；production
   termination path pre-arm exact token，JobService 先观察 live old epoch 并记录
   `PREARM_RETRY`；
2. compiler commit marker、post-check 同一 Job generation 后接受 delayed
   self-termination；
3. instrumentation 在 kill 前关闭唯一 compiler client connection，之后不 bind
   successor；old Binder/FD/process 消失且 procfs 为 `ABSENT/REUSED`；
4. 同一 Job token 的 owner telemetry 到达 `RECONCILED`，marker exact-clear；最后才
   fresh bind 不同 epoch 并完成 ACK/cancel、empty output 与 FD release。

最终 hash 对应 APK 的单方法结果为：

| 设备 | 系统 | S6f 单方法 | runner time |
| --- | --- | --- | ---: |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` | 7.201 s |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` | 17.495 s |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` | 8.538 s |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` | 4.62 s |

同源 source 在 API 35 的完整 lifecycle class 为 `OK (18 tests)` / 57.788 s，
其中包含 S6f 方法；最终 hash APK 又以上表单方法复核。此前暴露互锁的
`cleanupUnprovenRequiresOldProcessDeathBeforeNewProcessAdmission`
又连续复跑 `2/2`：`OK (1 test)` / 5.071 s 与
`OK (1 test)` / 4.900 s。

Sony `QV710AF65F` 正在执行 24h soak，本轮完全未访问，没有安装、adb 查询或
instrumentation；本文此前的 API 31 行仅为 S6a–S6e 历史证据，不表示当前 S6f
artifact 在该设备运行。

该 evidence 关闭 producer marker-before-schedule crash window，并证明无 successor
bind 的 exact pre-armed Job recovery。它不把 marker 与 JobScheduler 声称为原子
transaction；running same-ID/different-token replacement/`onStopJob()` 真机语义、
非权威 telemetry generation 的非完全线性化、reboot/force-stop/system_server
persistence、post-check 后外部取消、跨 reboot monotonic deadline +
`UNREADABLE`、连续 crash/backoff 仍开放。

S6g 历史同源 source evidence（以下保留当时原文）为：

```text
debug app APK   5,894,411 bytes
SHA-256         255B2B9DF1F612414A1D4B1DB3B1796CD50790236AF625606324F6E95A4D446D
androidTest APK 1,197,909 bytes
SHA-256         5D7E90033C28CFA6F366D4F1024B1C8747FD49F0D6F3CA3B244CD7080CEEEBB0
release APK     2,548,843 bytes
SHA-256         65F4AA96827CB5C58158796FA6033CAA16F987BC9C4EF94483DBE4FEE2CBD55C
```

JVM report 为 131 tests / 0 failures / 0 errors / 0 skipped。聚合 Gradle gates
`:app:testDebugUnitTest`、`:app:verifyR2ProjectCompilerCore`、
`:app:verifyDebugTypeScriptDistributionApk`、`:app:verifyReleaseCompilerHostApk`、
`:app:verifyOptionalPluginIndependence`、`:app:lintDebug`、
`verifyR1ProtocolCompatibility` 与
`:plugin-api:typescript-api:verifyTypeScriptApiV1Freeze` 全部通过；desktop framed
golden 继续由 TypeScript `6.0.3` 通过。

新增 `runningSystemJobReplacementHandsOffToNewExactGeneration`。generation A 先以
exact PREARM work 运行并到达 `PREARM_RETRY`；production exact-termination path
随后以相同 fixed ID、不同 token 调度 B。测试不预设 framework STOP/START 的全排序，
而是按 token 正向观察：

- `START_CALLBACK(A)`；
- `STOP_CALLBACK(A)`，decision 为当前 framework ordering 对应的
  `CURRENT` 或 `STALE`；若为 `STALE`，current-token digest 精确指向 B；
- `START_CALLBACK(B)`。

pure generation-gate tests 另覆盖 `STOP(A) → START(B)` 与
`START(B) → late STOP(A)`；后者保持 B current。owner telemetry 以 B token 记录
exact old epoch 的 `PREARM_RETRY`，最终由 B `RECONCILED` 并 exact-clear marker；
随后 fresh compiler epoch 完成 accepted + ACK + cancel、empty output 与 FD release。
这些 journal records 只是 token-scoped positive observation，不能由 absence、count
或 append order 推导 framework 全局 exactly-once/线性化。

最终 hash 对应 APK 的单方法结果为：

| 设备 | 系统 | S6g 单方法 | runner time |
| --- | --- | --- | ---: |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` | 13.009 s |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` | 20.018 s |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` | 11.397 s |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` | 10.072 s |

同一最终 artifact 在 API 35 又定向复跑 S6g 单方法两次：
`OK (1 test)` / 10.579 s 与 `OK (1 test)` / 13.483 s；连同上表累计 `3/3`。
同一 artifact 的完整 lifecycle class 为 `OK (19 tests)` / 71.522 s，其中包含
第 4 次 S6g 方法执行；两类 runner invocation 不追溯混算。

Sony `QV710AF65F` 仍在执行 24h soak，S6g 本轮完全未访问：没有 adb 查询、没有
安装 APK，也没有运行 instrumentation。此前 API 31/QV 行只保留各历史 artifact
证据，不表示当前 S6g artifact 在该设备运行。

该 evidence 只关闭当前矩阵中一次 running fixed-ID/different-token handoff，以及
该窗口内 token-scoped A START、A STOP、B START 与最终 exact B owner/reconcile。
连续或重叠 replacement、矩阵外 callback ordering、handoff 中 JobService
process death/restart、尚未观察的 completion/`onStopJob()` race、全局
exactly-once、reboot/force-stop/system_server persistence、外部取消与 continuous
crash/backoff policy 仍开放。

S6h 历史同源 source evidence（以下保留当时原文）为：

```text
debug app APK   5,894,411 bytes
SHA-256         255B2B9DF1F612414A1D4B1DB3B1796CD50790236AF625606324F6E95A4D446D
androidTest APK 1,209,751 bytes
SHA-256         D8CD7E7F28D1F29F887B379274FBAB47680DEE4E7DABAC52181683F535FACE36
release APK     2,548,843 bytes
SHA-256         65F4AA96827CB5C58158796FA6033CAA16F987BC9C4EF94483DBE4FEE2CBD55C
```

production app/release APK 与 S6g production source 相同；S6h 的新增 proof 位于
androidTest source/artifact。JVM report 为 131 tests / 0 failures / 0 errors /
0 skipped。聚合 Gradle gates 全部通过（390 tasks），desktop framed golden 继续由
TypeScript `6.0.3` 通过。

新增 `runningSystemJobIsRedeliveredAfterSupervisorProcessDeath`，按本节协议验证
同一 W 的 P1 exact death、final owner 的 post-kill `START_CALLBACK(W)`、同 token/
old nonce `RECONCILED`、marker/Job clear 与 fresh compiler recovery。completion
evidence 只在以上断言全部成功后写出。

| 设备 | 系统 | S6h 单方法 | completion evidence path | runner time |
| --- | --- | --- | --- | ---: |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` | `NATURAL`；shell 未调用 | 13.971 s |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` | `NATURAL`；shell 未调用 | 18.616 s |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` | `NATURAL`；shell 未调用 | 21.697 s |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` | `POST_FORCE_INVOCATION_BOUNDARY`；exact stdout success | 34.378 s |

runner time 是完整 test invocation 时间，不是单独的 redelivery latency。API 35
pre-final 90 秒诊断中，framework 日志显示约 11 秒后的 restart 安排，但 OEM 没有
提供 post-P1 positive callback；该失败诊断不属于表中的通过结果。最终 API 35 路径
使用 30 秒自然窗口，再 exact recheck marker/scheduler-tracked W，shell stdout
精确为 `Running job [FORCED]`；最早 observed qualifying START 的 monotonic time
严格晚于 invocation boundary。该分类明确非因果，不构成 autonomous
redelivery/backoff 证明。

同一最终 artifact 在 API 35 完整运行
`R1AcceptedLifecycleInstrumentationTest`，结果为 `OK (20 tests)` / 104.348 s，
其中包含 S6h。

Sony `QV710AF65F` 正在执行 24h soak，S6h 本轮完全未访问：没有 adb 查询、安装 APK
或运行 instrumentation；此前 API 31/QV 行只属于各自历史 artifact。

该 evidence 只关闭一次 same-boot、running JobService owner P1 死亡后，同一 durable
generation W 由不同 owner P2 接收并 reconcile 的子项。API 24/25/28 有自然重投正向
证据；API 35 只有 exact scheduler recheck、forced-eligibility command acceptance
与 invocation boundary 后 positive W callback/reconcile 的非因果证据。完整开放边界
见下节。

S6i 当前同源 source evidence 为：

```text
debug app APK   5,894,411 bytes
SHA-256         255B2B9DF1F612414A1D4B1DB3B1796CD50790236AF625606324F6E95A4D446D
androidTest APK 1,214,660 bytes
SHA-256         0D1EFBD0DBFB1EC71A4B48ABA4858A9D1B74BD87DBDF1663D09BD154E6BB30EE
release APK     2,548,843 bytes
SHA-256         65F4AA96827CB5C58158796FA6033CAA16F987BC9C4EF94483DBE4FEE2CBD55C
```

production app/release APK 与 S6h production source 相同；S6i 新增 proof 位于
androidTest source/artifact。JVM report 仍为 131 tests / 0 failures / 0 errors /
0 skipped；聚合 Gradle gates 全部通过（390 tasks），desktop framed golden 继续由
TypeScript `6.0.3` 通过。

新增 `runningSystemJobIsRedeliveredAfterOverlappedSupervisorAndCompilerDeath`：
两个 worker 在 exact P1/C validation 后通过 shared ready/fire barrier 发起并发
`Process.killProcess(exactPid)`；发起顺序不确定。证据记录双方 initiation/return
时间及 initiation span，并以 `bothSignalsReturnedNanos` 作为 qualifying
`START_CALLBACK(W)` 的严格 monotonic 下界。signal return 不等于 death proof，
本测试不声称 instruction-level 同时发起或实际 death interval overlap。

| 设备 | 系统 | S6i 单方法 | completion evidence path | initiation span | runner time |
| --- | --- | --- | --- | ---: | ---: |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` | `NATURAL`；shell 未调用 | 33,100 ns | 14.190 s |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` | `NATURAL`；shell 未调用 | 3,498,300 ns | 20.814 s |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` | `NATURAL`；shell 未调用 | 354,167 ns | 18.585 s |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` | `POST_FORCE_INVOCATION_BOUNDARY`；exact stdout success | 20,417 ns | 34.322 s |

API 35 的 shell stdout 精确为 `Running job [FORCED]`，因此该路径仍明确分类为
非因果证据。同一最终 artifact 在 API 35 完整运行
`R1AcceptedLifecycleInstrumentationTest`，结果为 `OK (21 tests)` / 138.103 s，
其中包含 S6i。Sony `QV710AF65F` 正在执行 24h soak，S6i 本轮完全未访问：
没有 adb 查询、安装 APK 或运行 instrumentation。

该 evidence 只关闭一次 retained exact W 上、shared validation barrier 后并发发起
P1/C exact kill request，以及双方 signal return 后的 W/P2
redispatch/reconcile。system/LMK/OOM/force-stop 双进程死亡、固定 signal 发起顺序、
instruction-level 同时发起与实际 death interval overlap 仍开放。

## Public freeze 与开放限制

S6a/S6b/S6c/S6d/S6e/S6f/S6g/S6h/S6i 没有修改 public AIDL method、transaction code、contract hash、
frozen ZIP v1，或 `compilerAvailable`、`projectCompilation`、
`semanticDiagnostics`、`sourceMapV3` capability flags；四项仍全部为 `false`。
现有 `:typescript_compiler` public Service 声明也未改变。

production manifest 现在包含两个职责不同的 supervisor 组件：

- S6b `R2CompilerProcessSupervisorJobService`：`exported=true`、受
  `android.permission.BIND_JOB_SERVICE` 保护；
- S6c `R2CompilerProcessSupervisorService`：`exported=false`、无 permission。

两者都运行于独立 `:typescript_supervisor` process，均无 intent filter 且
`directBootAware=false`。S6c typed AIDL 只在 app 内部；S6a/S6d/S6e Bundle controls
与 fault injection 仍只在 `DEBUG && same UID` 下存在。S6f 的 pre-arm/marker/
post-check 顺序属于 production internal path，强制 early-run proof seam 仅限
`DEBUG && same UID`。S6g 的 token gate 同样属于 production internal path；
lifecycle journal/cutoff 只在 `DEBUG` observation 中启用。S6h/S6i 不新增
production API；exact owner/compiler kill 与 API 35 的
`cmd jobscheduler run -f -u <current-user-id>` fallback 都只是 DEBUG/same-UID
instrumentation seam。ordinary release/public `startCompilation`
继续按 R0 fail closed。

S6c 已 provisionally 完成 app-private production-build live poisoned-epoch
registration/authorization/termination control-plane 子项；S6d 已完成单次 live
owner hard-kill、successor rebind 与 registration-gap replay 子项；S6e 已完成
successor-ready 后同步重放 retired teardown handler/death-recipient 的 no-op fence
子项；S6f 已完成 marker 前 exact persisted-Job ownership 与 no-successor recovery
子项；S6g 已完成一次 running fixed-ID/different-token handoff 与该窗口内
`onStopJob()` token fence 子项；S6h 已完成一次 same-boot、running JobService
owner-death 后 retained exact W 跨 owner epoch redispatch/reconcile 子项，其中
API 35 只具有 exact recheck、forced-eligibility command acceptance 与 invocation
boundary 后 positive callback 的非因果 proof；S6i 已完成一次 retained exact W
上 shared validation barrier 后并发发起 P1/C exact kill request，并以
`bothSignalsReturnedNanos` 约束后续 positive callback 的子项，但不证明固定发起
顺序、instruction-level 同时发起或实际 death interval overlap。完整 production process
supervisor blocker
**仍然开放**，至少还需要：

- 验证真实 framework-delivered/并发 stale callback（包括 stale
  `onServiceConnected()`）、stale/duplicate register/notification/token
  transaction、hung owner transaction，以及 system/LMK/OOM/force-stop
  owner/compiler 双进程死亡；补齐 signal 发起顺序枚举、instruction-level 同时
  发起与实际 death interval overlap proof；
- 验证连续或重叠多次 same-ID/different-token replacement、当前矩阵之外的
  framework callback ordering、same-ID handoff 中的 owner death，以及不同 worker
  phase、连续多次或 system-triggered JobService process death/restart/redelivery；
  补齐 API 35/OEM natural retry runtime 与尚未观察的 completion/`onStopJob()` race，
  并定义 continuous replacement/crash/backoff policy；
- 收紧或明确 best-effort telemetry generation 的非完全线性化；telemetry 继续保持
  非权威，不得成为 clear/admission proof；
- system kill、force-stop、真实 reboot、system_server/JobService restart、
  persisted state、post-check 后外部取消、跨 reboot monotonic deadline 与 procfs
  `UNREADABLE`、连续 backoff、marker corruption 的完整 device fault matrix；
- production ACK/ticket/callback delivery、external-caller ownership 与 retry
  规则；
- 与 64 MiB file-backed input、128/256 MiB output writer 及正式 schema/device
  gates 的集成。

在这些条件完成前，S6a 是 DEBUG exact live-process proof，S6b 是
production-manifest post-death reconciliation/marker-concurrency proof，S6c 是
app-private live control-plane proof，S6d 是单次 live-owner restart/reregistration
proof，S6e 是 deterministic retired teardown-handler no-op proof，S6f 是 exact
persisted-Job pre-arm/post-check 与 no-successor recovery proof，S6g 是一次 running
fixed-ID generation handoff 与 token-scoped positive callback observation proof，
S6h 是一次 same-boot running JobService owner-death 与 retained exact-W
redispatch/reconcile proof（API 35 仅为 exact recheck、forced-eligibility command
acceptance 与 invocation boundary 后 positive callback 的非因果证据），S6i 是
shared validation barrier 后并发 P1/C exact kill request 与
`bothSignalsReturnedNanos` 后 positive callback/reconcile proof；
当前 internal slices 为 10/10 checked，public capability freeze 仍为 0/54；
这些 internal slices 都不能用于开放 public R2 capabilities。
