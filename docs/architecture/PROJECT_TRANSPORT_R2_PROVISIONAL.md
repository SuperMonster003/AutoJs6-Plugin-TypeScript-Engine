# R2 Provisional Framed Project Transport

> **S5-5 performance amendment（2026-08-24）。** 逻辑 input frame 的 exact shape、
> 全局 sequence、逐文件 SHA-256/UTF-8/quota 校验与单 cursor ownership 不变；native
> 仍单独发送 `projectInputStart`/`projectInputEnd`，中间 config/file frame 以最多 4 条
> 封入一个 `projectInputBatch`。外层 envelope 保持 window=1，只 ACK 最后一条 inner
> sequence；Worker 按原顺序逐条执行原 handler，全部成功后才发送这一条 ACK。result
> transport 仍是逐 frame stop-and-wait。该改动不修改 AIDL、public progress schema、
> profile fingerprint 或 archive/result schema。

> **S1-3 amendment（2026-08-23）。** ordinary input revision 2 将 caller
> `512 files / 8 MiB` 与 plugin declarations 分账；完整 retained input 仍由 trusted
> 4607-files/72-MiB guardrail 约束。ordinary output revision 2 为 512 artifacts、单 JS/map
> 各 16 MiB、aggregate 64 MiB，decoded typed-result envelope 为 144 MiB。

> **S1-2 amendment（2026-08-23）。** 本文中的 printable-ASCII VFS path 是历史边界；
> 当前 framed transport 与 Worker VFS 接受 canonical UTF-8 path，安全规则见
> [ZIP Archive Container v1](ARCHIVE_CONTAINER_V1.md)。

> **R2-M3-S1 supersession（2026-08-14）。** 本页中的旧 projectInputStart shapes 只保留为
> pre-S1 transport provenance，不是当前 accepted frame。Formal input revision 1 的 Worker
> exact counts 为 24/37 与 30/40/47，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。exact inner-frame 与
> ownership 结论继续保留；当前外层 input envelope batching 以上述 S5-5 amendment 为准。

状态: R2-S3/P3a/P3b-a/P3b-b1/P3b-b2a/P3b-b2b/P3b-b2k/R2-M2-C2/C3/C4
内部协议，未冻结为 public contract。

该协议把 R2-P3a 的 app-private file-backed archive snapshot 接入专用 Web
Worker，同时继续保持 AIDL、public API、ZIP v1 和 capability advertisement
冻结。P3a 已解决 native source payload 的 heap snapshot 和整文件 framing；
P3b-a 又加入 Worker 内部 4607 files/72 MiB envelope、复杂度预算、单份 decoded
String/lazy SourceFile、incremental diagnostics 与 per-job release。它仍不是最终
input 64 MiB container/64 MiB uncompressed、output 128 MiB container/256 MiB
uncompressed artifact pipeline。P3b-b1 又把 raw frame 调整为 64 KiB、internal
message bound 调整为 128 KiB，并用 DEBUG-only expanded gate 建立 Android
capacity 与 exact success realm-retirement evidence；production admission 未放宽。
P3b-b2a 又加入严格 ingress/parse/semantic/emit phase protocol 与 same-UID
`DEBUG` phase-entry hard-stop/active-renderer fault evidence；它不修改 public
schema 或 production capacity。
P3b-b2b 再加入三轮 repeated exact-64-MiB `DEBUG` Binder success 与自然 ingress
cancel 的 compiler-service/renderer PSS、稳定 FD target multiset、transient
spool/cursor/Worker successor 门禁，以及 compiler-service startup recognized
private orphan scavenging；它同样不修改 wire schema 或 production capacity。
P3b-b2k 将当前 emit completion 冻结在完整 Worker 输出构造与
ACK-backpressured framed egress 之后：最后一个 result chunk ACK 后发布
`emit completed`，再发送 `projectResultEnd`。它以 256 roots / 364 aggregate
files、emit sequence-2 `128/512` 和 512 outputs / `976,438` bytes（SHA-256
`2b2a4529552aa3a3f9b33cd2a379240bc2852ca065a33ff884a6160847f1b1de`）在
API 24/35 × evidence Debug/Release-equivalent 四行通过 external-UID Binder
自然 timeout；这不是 instruction-level `builder.emit` interruption 证明。
`compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、
`sourceMapV3` 四项 capability 仍全部为 `false`；这些 provisional slices
不关闭 Public R2 checklist，计数保持 `0/54`；internal provisional checklist
当前为 **26/26**。

## 边界与配额

- Android API 24 基线的 native ↔ page 边界只使用 String `WebMessage`，不依赖
  binary WebMessage；普通 inner frame 最多 128 KiB UTF-8，受控 Node start 与
  `projectInputBatch` 外层 envelope 最多 576 KiB UTF-8。
- page ↔ Worker 使用同一 frame schema 的 structured-clone object，不把对象
  再序列化成 String `WebMessage`。
- data 使用 canonical padded Base64；每帧最多 64 KiB decoded bytes。
- native → page → Worker 的 input 外层 envelope 使用 window=1：start/end 单独发送，
  中间最多 4 条 exact inner frame 组成一个 batch；Worker 串行完成每条 inner frame
  的 shape、sequence、quota、payload 与 commit 校验后才 ACK batch 的最后 sequence。
  Worker → page → native 的 result 流继续逐 frame window=1 stop-and-wait。
- P3a native snapshot 是 metadata + offsets + read-only inode；framer 用一个
  64 KiB buffer 从当前 file cursor 随机读取并在 file end 重新计算 SHA-256，
  不在 native heap 保留 source-sized `ByteArray` 或 UTF-16 text map。
- native VFS snapshot/index 与 Worker retained-input guardrail 最多 4607 files、
  72 MiB caller + trusted declarations、8 MiB/file；production host 在发 frame 前另对 caller
  domain 执行 admission：512 files、8 MiB/file、8 MiB total，trusted declarations 不占该额度。
- Worker 为当前文件分配完整 `Uint8Array`，fatal UTF-8 decode 后清零 bytes 并
  只保留一份 text record；VFS 不再重编码，`SourceFile` 按需创建并在当前 job
  缓存。TypeScript 最终仍可能同时持有全部 strings 与 Program/AST，因此 input
  frame 和 desktop 64+8 MiB proof 不能外推为 WebView heap/device capacity。
- ordinary output 为 512 artifacts、单 JS/map 各 16 MiB、64 MiB aggregate，完整 decoded typed
  result 最多 144 MiB，并继续在 heap materialize。legacy output 128 MiB container/256 MiB
  uncompressed 只是 ZIP/transport ceiling，不是 ordinary capacity。

## 输入状态机

下图描述逻辑 inner-frame 状态机。S5-5 后，任意连续 1–4 条 config/file middle frame
可以作为一个 `projectInputBatch` 外层消息发送；图中的每条 ACK 仍表示该 inner frame
必须成功通过原校验/commit，只有 batch 全部成功后才向 native 发出最后 sequence 的
单一外层 ACK。start/end 不进入 batch。

```text
projectInputStart
  -> projectPhase(ingress, started)
  -> ACK
  -> (projectInputConfigStart -> ACK
      -> projectInputConfigChunk* -> ACK each
      -> projectInputConfigEnd
      -> native/Worker SHA-256 + UTF-8 + JSONC/policy parse
      -> precedence + typed combination validation
      -> canonical options/fingerprint + immutable state freeze
      -> ACK only after the complete config transaction succeeds)?
  -> (projectInputFileStart
       -> ACK
       -> open exact metadata-indexed native file cursor
       -> projectInputFileChunk*  # random-access read, <=64 KiB raw canonical Base64
       -> ACK each
       -> projectInputFileEnd
       -> native SHA-256 recheck; Worker SHA-256 + fatal UTF-8 validation
       -> ACK)*
  -> projectInputEnd
  -> exact declared file count/total bytes validation
  -> projectPhase(ingress, completed)
  -> ACK
  -> derive sorted roots + default-lib/root inventory validation  # config-none only until C7
  -> projectPhase(parse, started)
  -> synchronous createIncrementalProgram + syntactic/global diagnostics
  -> projectPhase(parse, completed)
  -> projectPhase(semantic, started)
  -> per-SourceFile semantic diagnostics
  -> projectPhase(semantic, completed)
  -> projectPhase(emit, started)
  -> emit decision / builder.emit / output validation and hashing
  -> result JSON UTF-8 encoding + framed-result digest
  -> ACK-backpressured projectResultStart + projectResultChunk*
  -> final result-chunk ACK
  -> projectPhase(emit, completed)
  -> projectResultEnd
```

P2 将 `projectInputStart` 的 exact shape 扩展为同时携带
`profileSchemaRevision`、`profileRevision`、`configSchemaRevision` 和
`configKind`。历史 config-none branch 只接受本地唯一的 provisional Rhino/config-none
authority；其 framed result 必须回传同四字段以及 18 项完整 effective options。native parser
逐 key、逐值与 `R2CompilationProfileRegistry.PROVISIONAL_RHINO` 比较，不能由
Worker 或 caller 自行声明另一组 authority。

R2-M2-C2 保留上述 config-none start 的 exact shape，同时增加 controlled start：
`configSchemaRevision=2/configKind=tsconfig` 并携带 exact
`configByteLength/configSha256/configPolicyRevision/configPolicyFingerprint`。raw config
使用独立 `projectInputConfigStart/Chunk/End`，不能伪装成 VFS source；start/end
`totalBytes` 是 config + VFS aggregate，`fileCount` 只计 VFS files。config end 只有在
digest、fatal UTF-8、strict JSONC、duplicate/complexity 与 C1 closed-world policy 全部
通过后才可进入 C3。C3 再执行 immutable profile > explicit allowed caller boolean >
explicit defaults、TS5052-compatible combination validation、41-key canonicalization 与 pure
SHA，并先冻结 immutable effective state；只有整条链成功才 ACK config end。任何失败都
不 ACK、不继续 source framing，也不保留 partial state。

C3 成功只允许继续传输 sources。selectors 仍原样保留给 C7；C7 完成前，controlled
request 到达 input/project end 继续 fail closed，不进入 parse/semantic/emit，不创建
Program、`projectResult*` 或 output/diagnostics ZIP。不得退回“所有 source 都是 root”的
config-none 行为。

C4 不增加 frame kind 或 positive package transport。九个 active input handler 各自只接受
exact key set，拒绝 transformer/plugin injection name；revision 2/3 archive、heap/file-backed
snapshot 与 Worker VFS 三层拒绝 reserved package/deferred path。C5 只有通过新 manifest
revision 才能增加 package metadata，C6 才建立 NodeNext resolution。

P3b-b2a 还要求 `projectInputStart` 精确携带 `workerSpinPhase`。production
framer 始终发送 `workerSpinMilliseconds=0` 与 `workerSpinPhase=none`；只有
same-UID `BuildConfig.DEBUG` private control 才能同时启用非零 milliseconds 与
一个 exact phase。二者不能单独启用，public control 构造 invariant 和 release
parser/host 都禁止该 seam。

每个逻辑 input frame 带 host-generated opaque `requestId` 和全局单调 `sequence`；file
frames 另带连续 `fileIndex`/`chunkIndex`。file header 声明 canonical path、
decoded byte length 和 SHA-256。P3a framer 只在该文件收到 start ACK 后打开
cursor；每个 batch 最多读取 4 条 middle frame 所需的有界 chunk，在 file end frame 发布前要求
cursor 精确耗尽且重新计算的 digest 与 accepted metadata 相同。cancel、timeout、
host release 或 framing failure 都关闭当前 cursor 并清零 reusable chunk buffer。
ACK、cursor read、`nextFrame()` 与 release 全部在 dedicated single-thread input
executor 上串行；main thread 只发送同时满足 pending identity、session token、
port、Worker generation 与 preparation epoch 的 prepared frame。release 在有界
等待内无法证明时，compile path 抛出 cleanup-unproven 并进入 supervision，不能把
先前生成的 success 交给 output packaging。

当前 snapshot 规则要求所有
`/sources/**/*.ts`（排除 `.d.ts`）都是 root，因此 Worker 从最终 inventory
推导并按 code-unit 排序 rootNames，不把潜在超大 root array 放入 start frame。

Worker 独立重新执行以下检查：

- printable-ASCII canonical `/sources`/`/declarations` path；
- exact/case-fold collision；
- declarations 必须是 `.d.ts`；
- decoded file/aggregate quota；
- canonical Base64、连续 chunk 和 exact decoded size；
- 每文件 SHA-256 与 fatal UTF-8；
- `/declarations/lib.d.ts` 存在；
- source-root inventory 精确覆盖。
- reserved `package.json`、`packages/**`、`node_modules/**` 与大小写/嵌套/`.mts`/`.cts`
  变体在当前 revision 上拒绝；普通 `.json/.js/.ts/.d.ts` path 不被路径 fence 误伤。

controlled config 另执行 root `tsconfig.json` identity、48 KiB quota、schema/policy
fingerprint、canonical Base64/sequence、SHA-256、UTF-8/BOM、JSONC token/duplicate、
depth/node/selector 与 option classification；C3 随后冻结 41-key effective state。
C4 在 option classification 中稳定拒绝 10 个 resolution/host escape option 与
transformer/plugin aliases，且 Worker host 只读 snapshot VFS，不使用 `ts.sys`、网络或
caller callback。
effective `strictNullChecks=false` 与 effective `exactOptionalPropertyTypes=true` 或
`strictPropertyInitialization=true` 的组合也以 exact internal
`projectConfigError(stage=config,outcome=diagnostics,errorCode,
policyCode=CONFIG_INVALID_VALUE,emittedModuleCount=0)` 返回；其他 policy failure 保留各自
`CONFIG_*` code。page/native 都 strict-parse。当前 coordinator 只形成 typed failure 并
保持 output 为空，M3 前不创建 diagnostics-only ZIP。

`projectPhase` 事件携带 exact request ID、`started|completed` state，并由 page
按 Worker generation、transport phase、下一期望 phase 与当前 active phase
复核；native 再绑定 pending operation/session/generation。缺失、重复、越序、
unknown state，或 parse/semantic 尚 active 时发送 result start，都会 fail closed
并替换 Worker。唯一允许跨越 result start 的 phase 是已完成 Worker-side output
构造的 exact active emit；它必须保持 active，直到最后一个 chunk ACK 后收到
`emit completed`，而 `projectResultEnd` 必须再晚一步。production path 也发送
这些内部 phase 事件，但不执行 spin。

## 输出状态机

emit 从 `builder.emit` 覆盖到完整 Worker-side output pipeline 和
ACK-backpressured framed egress：Worker 执行 output/diagnostic/Source Map
配额与结构校验、顺序 output hashing、JSON UTF-8 编码、result quota 与最终
digest，然后保持 emit active 发送：

```text
projectResultStart(byteLength, chunkCount, sha256)
  -> native ACK(-1)
  -> projectResultChunk(index, Base64 data)
  -> native ACK(index), one chunk at a time
  -> final chunk ACK
  -> projectPhase(emit, completed)
  -> projectResultEnd(repeated inventory)
  -> native SHA-256 + fatal UTF-8 + typed result validation
  -> terminal publication
```

`emit completed` 严格晚于最后一个 chunk ACK、早于 `projectResultEnd`。因此在
真实 output egress 期间命中的 keyed timeout 可以终止仍 active 的 emit，但该
receipt 只证明完整输出 pipeline 被硬停，不证明在 `builder.emit` 的某条指令内
抢占。`projectResultEnd` 后的 native SHA/UTF-8/typed parsing 与 terminal
publication 是 result finalizer，明确不属于 emit phase。

native verifier 对 frame 使用 strict JSON，拒绝 duplicate/unknown keys；Base64
在 verifier thread 解码。main thread 只做 exact pending/session/generation
correlation、小块 accumulator 更新和 MessagePort post。预分配 accumulator 在
完成时直接转移 byte-array ownership，失败/释放时清零。最终 payload 使用独立
单线程 result executor 和 `R2StrictJson` typed parser；root/options/
diagnostics/outputs 以及嵌套 Source Map 均要求 exact keys，并拒绝 duplicate、
unknown field 和非法 surrogate。SHA-256、fatal UTF-8、output SHA、Source Map
v3 以及 module/map/source inventory 全部通过后才发布
`R2ProjectCompilation`，不会让大 result finalizer 阻塞 control-frame parser。

`projectResultEnd` 后 page 可清除 active operation；native 在 phase 外的最终 typed
verification 完成前仍保留 active host slot，因此 successor 不会提前发送新的
input start。

## Keyed hard stop

TypeScript program construction、diagnostics 和 emit 是 Worker 中的同步 CPU
阶段。向 Worker 发送 cancel 消息无法及时执行，因此 hard stop 由 host page
控制：

```json
{
  "kind": "terminateWorker",
  "requestId": "<opaque host operation id>",
  "expectedGeneration": 3
}
```

page 仅在 `(requestId, generation)` 与唯一 active project 完全匹配时：

1. 同步调用旧 Worker 的 `terminate()`；
2. 清除 active project；
3. generation 递增；
4. 创建 replacement Worker；
5. replacement 完成构造、脚本装载和 TypeScript startup 并发出 exact
   `ready` 后，才发布携带 terminated/replacement generation 的
   `workerTerminated` ACK。

wrong/stale key 静默不终止当前 Worker。旧 Worker handler 还受 Worker object
identity 与 captured generation 双重 fence，迟到 frame 不会进入 successor。
无 key 的 R1 `restartWorker` 仅在 page 没有 active project 时可用。replacement
构造或异步 script-load/startup 失败不发布 termination ACK；native 必须继续持有
旧 operation，直到 exact-session teardown proof。Worker → page 另有显式 kind
allowlist；active project 发出未知 kind 时只失败该 exact project 并替换 Worker，
不能把 page-owned `workerTerminated` 等控制消息伪造成 teardown proof。

native 使用不透明 `R2HostProjectOperationHandle`。future Binder coordinator
负责把 public request ID、job token、calling UID 和 callback Binder 映射到该
handle；这些 public ownership 字段不进入 WebView。handle 用单一 monitor 覆盖
claim、registration 前后 first-wins stop intent 和 terminal CAS；所有正常
terminal path 还会先与 absolute monotonic deadline 竞争。

cancel/timeout 后保留 pending slot，直到收到 ready-gated exact
`workerTerminated`，或 exact `CompilerSessionToken` 获得
`WebView.destroy()` 成功/renderer-gone proof，才发布 terminal outcome。
timeout、close 和 accepted teardown handler 调度失败均不制造 synthetic
terminal。pre-dispatch stop 因为从未向 Worker 赋予 operation，可直接终结。
exact destroy 失败时 host 保留 session/slot identity 并每 250 ms 重试；只有
accepted teardown 的 main-handler post 或 retry scheduling 失败时，才设置
fatal/poisoned admission 状态并标记需要外部 supervision。若 destroy 永久失败但
重试仍可调度，S3 会安全地无限保留 pending；真正的跨进程 process supervisor
仍未实现，属于后续独立切片。

P3b-b1 将同一 exact-session proof 扩展到成功路径：typed result 通过后先销毁
exact WebView realm、关闭 port 并返回 BOOTSTRAP，再绑定 exact snapshot 和发布
success。下一 project 懒创建更高 generation 的 session。destroy 失败继续保留
同一 pending identity 重试，不能先发布成功。

P3b-b2a 在每个 exact `started` 后提供 DEBUG-only bounded spin。API 24/35
instrumentation 在 ingress、parse、semantic、emit 四个入口分别等待 native
观测 active phase，再触发 keyed cancel 或 deadline timeout；每次都必须取得
replacement ready proof，最后由 small successor 验证恢复。该证据只覆盖
synthetic phase entry，不表示零 spin 的自然 TypeScript 同步工作已在中途被
抢占。

API 35 的 active renderer seam 还要求 operation handle、WebView session 与
expected active phase 同时匹配才调用 `renderer.terminate()`。renderer-gone
evidence 记录 request/phase/requested phase、`RenderProcessGoneDetail`、
session/Worker generation 与 monotonic timestamp，并验证旧
`(pid,starttime)` 消失和不同 renderer identity 的 successor 成功。当前
`didCrash=false` 不等于 LMK/OOM。

P3b-b2b 的 repeated-resource gate 以 exact process identity 包围每个奇数样本
PSS batch，要求成熟轮次的 compiler-service FD target multiset 稳定且 recognized
private transient 不残留。自然 ingress cancel 路径使用 production `0 + none`
phase control，在 exact input cursor active 后触发 keyed cancel，并要求
opened/closed cursor 平衡、snapshot released、Worker generation 前进与 small
successor recovery；该路径没有扩展为自然 parse/semantic/emit 中途 fault。

compiler-service startup 还会在 host/coordinator 创建前扫描 recognized private
input staging、working spool 与 output staging orphan。只删除三个精确 namespace
下的 regular files，保留 unknown entries 与目录；初始枚举、owner-write 恢复、
删除或 post-cleanup 复核任一无法证明时，admission 进入 fail-closed 状态。

## 当前证据与未完成项

JVM tests 覆盖 canonical Base64、128 KiB envelope、one outstanding frame、
大于旧 512 KiB gate 的 input、sequence/inventory/digest result accumulator 和
strict framed control parser。P2 又加入 start/result authority mutation、完整
options exact-key/value 和跨语言 desktop golden，锁定 Kotlin 与实际 Worker 的
revision/config/options 一致性。

P3a 增加 file-backed cursor 回归：native framer 可跨越当前 8 MiB Worker budget
逐块读取；P3b-b1 当前任意时刻只请求一个最多 64 KiB chunk（P3a 初始值为
16 KiB），还覆盖 accepted digest 被
改变、cursor 提前结束和 release/close。这里“大于 Worker budget”只证明 native
framing 的 allocation shape，不是 production Worker 已接受或编译该输入。

Node harness 让原五组 Rhino golden 全部走 framed transport，并从 generated、
lock-verified distribution 实际装载 108 个 TypeScript standard libraries。
精确的 3,784,758 bytes 与 aggregate fingerprint 由独立 distribution
trust-root gate 和 APK loader instrumentation 复核。harness 还覆盖 ACK 前不推进、
digest/UTF-8/Base64 failure、同步 Worker spin，以及 fake page 上的
wrong/stale/exact terminate key、legacy project bypass 缺失、伪造 page-owned
termination、replacement constructor/async-load failure、exact ready-gated ACK
和旧 Worker late message。P3b-b2a 进一步要求四阶段 exact
started/completed sequence、production `0 + none`、非法 phase state
fail-closed，并在 API 24/35 执行 phase-entry cancel/timeout matrix、API 35
执行 active exact-64-MiB renderer loss/recovery。P3b-b2b 再加入 repeated
exact-64-MiB resource、自然 ingress cursor cleanup/successor 与 startup orphan
scavenger 的 source/APK policy gate；设备数值与 artifact 证据由独立 b2b 记录
承载，不在本协议索引中复制。

仍未完成：

- P3b-b1 已完成 DEBUG low-token ASCII direct/Binder capacity 与 exact realm
  retirement；P3b-b2a 已完成 same-UID DEBUG synthetic phase-entry hard-stop
  和一次主动 active renderer loss/recovery；P3b-b2b 已加入 repeated resource、
  自然 ingress cancel cleanup 与 startup orphan scavenging。完整 P3b-b 仍缺
  external-UID debug/minified-release、零 spin 自然 parse/semantic/emit 中途
  fault、真实 LMK/OOM 与 Unicode/high-token distributions，并据此决定是否解除
  production 512 files、8 MiB/file、8 MiB total admission；
- 128 MiB container/256 MiB uncompressed private output ZIP、
  fsync/seek/commit；
- 无法在进程内取得 teardown proof 时的 process supervisor；
- C5 新 revision package metadata 与 C6 NodeNext resolution；
- C7 selector/root matching 与 controlled Program activation；
- diagnostics/output manifest v1 freeze；
- NodeNext profile 与正式 profile revision。

P2 绑定说明见
[R2 Provisional Profile and Config Authority](PROFILE_AUTHORITY_R2_PROVISIONAL.md)。
P3a native 边界、容量分层和 Public 非声明见
[R2-P3a File-backed Native Input](FILE_BACKED_INPUT_R2_P3A.md)，P3b-a Worker
allocation/lifetime 见
[R2-P3b-a Worker Input Capacity and Lifetime](WORKER_INPUT_R2_P3B_A.md)，P3b-b1
device boundary/realm retirement 见
[R2-P3b-b1 Android Device Capacity and Exact Realm Retirement](WORKER_INPUT_R2_P3B_B1.md)，
P3b-b2a phase/fault boundary 见
[R2-P3b-b2a DEBUG Phase-entry Hard-stop and Active Renderer-loss](WORKER_FAULT_R2_P3B_B2A.md)，
P3b-b2b repeated resource/startup orphan-cleanup boundary 见
[R2-P3b-b2b Repeated Resource and Startup Orphan Cleanup](WORKER_RESOURCE_R2_P3B_B2B.md)。
