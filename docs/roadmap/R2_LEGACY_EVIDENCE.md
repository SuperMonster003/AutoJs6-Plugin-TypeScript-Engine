# R2 Legacy - 单体路线与证据日志（归档）

> 本文件于 2026-08-01 在 R2 rebaseline 时从活动路线归档。归档前内容原样保留，
> 用于追溯 S1–S6i、P1–P3b-b2k 与三个无编号 follow-up 的完整实施记录、设备数据和
> 历史验收说明。当前活动路线见 [R2.md](R2.md)；新路线中的完成状态必须引用本归档
> 或对应 `docs/architecture` 证据，不得把本文件继续当作待办清单。

# R2 - 生产级项目编译服务

状态: 进行中.

目标: 从单文件宿主探针升级到完整的项目级 `createIncrementalProgram`
编译服务, 输出可由 Rhino 或 Node 消费的 `CompilationBundle`.

## 当前进展

R2-S1 provisional project compiler core、R2-S2 private archive adapter、
R2-S3 framed Worker transport/keyed hard-stop、R2-S4 private output
staging/FD commit、R2-S5 process-wide admission/coordinator、R2-S6a
`DEBUG` / same-UID active-termination proof，以及 R2-S6b production-manifest
system-owned post-death reconciliation owner、R2-S6c app-private live-epoch
supervisor owner，以及 R2-S6d owner hard-kill/rebind 与 registration-gap poison
replay；R2-S6e 又加入 successor-ready 后的 retired-generation teardown callback
replay fence；R2-S6f 再把 exact persisted Job ownership 前置到 durable marker
commit 之前，并证明没有 successor bind 时的 system-owned recovery；R2-S6g 再以
token-generation gate 与 `DEBUG` callback journal 收窄一次 running fixed-ID
same-ID/different-token handoff 的 framework 边界；R2-S6h 又正向证明同一个
durable work token 在一次 test-side exact JobService owner hard-death 后，由不同
`:typescript_supervisor` process epoch 接收并完成 exact reconcile；R2-S6i 再从
shared validation barrier 并发发出 supervisor owner 与 old compiler 的两个 exact
kill request，以 `bothSignalsReturnedNanos` 作为同一 retained work 后续 callback
的下界。R2-P1 现在又把既有 coordinator 接入 release，并新增 additive
public-shaped request/ACK、external-UID fixture 与 release APK presence gate，但
四项 capability 仍关闭。R2-P2 再将版本化 Rhino profile/config authority、完整
固定 options 和 fingerprint 贯穿 input → snapshot → framed Worker → result →
output，并新增实际 APK binary manifest shape gate。R2-P3a 又把 production native
input loader/framer 切换为 app-private file-backed VFS spool、metadata-only
snapshot、streaming ZIP sink 与最多 16 KiB random-access framing。R2-P3b-a
又把 Worker 内部 direct-framing envelope 提升到 4607 files、8 MiB/file、
72 MiB total，并加入解析前复杂度预算、单份 decoded String、lazy SourceFile、
incremental diagnostics 和 per-job release。R2-P3b-b1 再以 DEBUG-only expanded
gate、64 KiB raw/128 KiB envelope framing 与 exact success realm retirement，
在 API 24/28/35 完成 direct 64 MiB device capacity，并在 API 24/35 完成
STORED/DEFLATED inclusive/`+1` Binder 边界；Android production dispatch 仍保持
512 files/8 MiB total。R2-P3b-b2a 又加入 ingress/parse/semantic/emit 的严格
started/completed protocol，以 same-UID `DEBUG` synthetic phase-entry spin 在
API 24/35 覆盖四阶段 cancel/timeout，并在 API 35 的 active exact-64-MiB
semantic window 关联 renderer termination、旧 process identity 退出与不同
successor identity/recovery。R2-P3b-b2b 接着以三轮 exact-64-MiB Binder
success 与三轮零 spin 自然 ingress cancel，在 API 24/35 记录 exact
compiler/renderer PSS、稳定 FD target multiset、cursor/Worker successor 和
transient cleanup；compiler service 冷启动又只清除 recognized private
input/spool/output orphan，并在清理无法证明时关闭 admission。R2-P3b-b2c
再冻结 standalone same-signer、distinct-UID fixture 的 external
production-fence pipeline：正确哈希的 invalid ZIP 保留
`ERROR_ARCHIVE_FORMAT`/empty-output 回归；exact `4,603,850`-byte caller 加
installed trusted `3,784,758` bytes / 108 files 后完成 8 MiB inclusive emit
校验；结构/哈希合法 caller `+1` 则在 accept/ACK 后异步返回
  `ERROR_ARCHIVE_QUOTA`、零 payload 与 empty output。API 24/35 debug 与
  minified service release 四行 runtime matrix 已全部 PASS；首轮 release
  暴露的 FD-procfs 与 sibling-procfs registration 问题已由 public/JNI libc
  `fcntl(F_GETFL)`、Binder UID/PID + self identity + token/endpoint-death
  handshake 关闭。R2-P3b-b2d 又以 non-shipping
  `r2CapacityEvidenceDebug` / `r2CapacityEvidenceRelease` app 为
  standalone external-UID fixture 派生专用 capacity authority；release variant
  继承 release、经 R8/minify 且 non-debuggable，是 release-equivalent evidence
  build；两个 evidence variant 都以独立文件名生成并声明 TEST_ONLY，保持
  ordinary app application ID/signing，**不是** shipping production release。
  每行先回归 b2c ordinary
  invalid/inclusive/`+1`，再运行 STORED container 与 DEFLATED expanded 的
  exact 64 MiB/`+1` 四边界，最后运行 ordinary 8 MiB successor；最终 TEST_ONLY
  artifact 已重建并通过本地 gate，API 24/35 四行与 ordinary Debug/Release
  回归均已 PASS。R2-P3b-b2e 再以 ordinary same-UID debug artifact 在
  API 24/35 各完成 direct-host 三轮 exact-64-MiB partial ingress 自然 timeout：
  固定 5 秒 deadline、零 spin、无主动 stop，cursor 全部平衡/释放、Worker
  依次替换且小后继成功；Binder exact-64-MiB STORED container 另以固定 10 秒
  deadline/public ACK 返回 exactly-once timeout 与零输出，并在 terminal 后证明
  active job、命名/remote transient FD 最终清零、compiler identity 不变和小后继
  成功。Binder 单项覆盖 end-to-end natural deadline/final cleanup，partial
  ingress 只由 direct-host telemetry 证明。R2-P3b-b2f 随后以 standalone
  same-signer、distinct-UID fixture 在 API 24/35 完成 normal Debug/Release
  production-fence `+1` black-box 回归，以及 TEST_ONLY evidence
  Debug/minified non-debuggable release-equivalent 的 production-fence、
  STORED container 64 MiB `+1` 与 DEFLATED expanded 64 MiB `+1` scope-free
  cleanup matrix。evidence path 在 ACK 前证明 active lease 为 1、server
  input/output FD 各精确为 1；cleanup receipt 在 terminal callback 前创建、
  callback 后读取并按 request/token/descriptor target 关联，随后连续 5 次观测
  caller/private input/project spool/private output FD、命名 transient 与 active
  lease 全部为零，同一 Binder/安全 process epoch 下 ordinary successor 全部成功。
  R2-P3b-b2g 再以 ordinary same-UID Debug direct host、零 spin、无 timeout
  注入的 300-file natural workload，在 API 24/35 分别于 parse `48/192`、
  semantic `48/192`、emit `96/384` 真实 work units 后执行 keyed cancellation；
  六案全部证明 exact progress/termination receipt、300/300 cursor 平衡释放、
  Worker replacement、小项目 successor 与 host/spool cleanup。R2-P3b-b2h
  随后只在 ordinary same-UID Debug direct host 上，以三次完整成功 baseline 的
  semantic sequence-2/completed 严格交集冻结 request-start absolute deadline，
  再运行唯一一次 natural semantic timeout fault；API 24/35 均证明 timeout/
  无 compilation、Worker `1→2`、successor 与 cleanup，且不使用测试侧
  `requestStop`、retry、spin 或 expanded admission。R2-P3b-b2i 再以独立
  8-root ordinary workload，在不计入 timing 的两次成功 precondition 后，以
  三次完整成功 baseline 的 parse sequence-2/completed 严格交集冻结 deadline，
  随后只运行一个 natural parse timeout fault；API 24/35 均证明 timeout、
  零 compilation、117/117 cursor release、Worker `1→2`、successor 与 cleanup，
  同样无测试侧 stop、retry、spin 或 expanded admission。R2-P3b-b2j 随后以
  独立的 8-root / 116-file ordinary workload，在同样两次不计时的成功
  precondition 后，从三次完整成功 baseline 的 emit sequence-2/completed
  严格交集冻结 deadline，随后只运行一个 natural emit timeout fault；API 24/35
  均证明 timeout、零 compilation、116/116 cursor release、Worker `1→2`、
  successor 与 cleanup，且没有测试侧 stop、retry、spin、expanded admission、
  synthetic calibration 或 phase-relative deadline；该 timing 保留为旧 emit
  completion boundary 的历史 direct-host evidence。R2-P3b-b2k 接着把当前 emit
  timeout 推进到 standalone same-signer、distinct-UID public Binder pipeline：
  API 24/35 × TEST_ONLY evidence Debug/minified non-debuggable evidence Release
  四行均执行 2 次完整成功 precondition、3 次 baseline strict intersection、
  1 次 unclamped midpoint fault 与 1 个 small same-Binder successor；原始
  request-start absolute nanos 直接贯穿 coordinator/host，callback 前 phase/
  cleanup receipts 证明 emit sequence 2 `128/512`、364/364 cursor release、exact
  Worker replacement 与连续 5 次 transient/lease 全零；成功路径同时校验 512 个
  raw Worker outputs 与 public packaged manifest authority。当前 emit completion
  位于 ACK-backpressured result egress 的最终 ACK 之后，而不是
  `builder.emit()` 返回处。随后完成的无编号 external-UID Binder natural semantic
  timeout follow-up 复用同一 public pipeline，并以 exact 512-file ordinary
  workload 在 API 24 Chrome-WebView `119.0.6045.194` 与 API 35 Google WebView
  `130.0.6723.86` 上完成 evidence Debug/minified non-debuggable evidence Release
  四行矩阵；每行仍是 2 次成功 precondition、3 次 baseline strict intersection、
  1 次 unclamped midpoint fault 与 1 个 same-Binder successor，fault 均在 semantic
  sequence 2 `64/256` 后返回 timeout、零 compilation/output，证明 512/512 cursor
  release、exact Worker replacement、callback 前 cleanup 与随后 five-zero
  convergence。API 24 Google WebView `53.0.2785.124` 的探索运行因当前 page host
  JavaScript 语法不兼容而在首个 precondition fail closed，`faultAttempts=0`，不计入
  正式矩阵。随后完成的第二个无编号 follow-up 又以 exact 512-file ordinary parse
  workload 关闭 external-UID Binder natural parse timeout：15,000 个 imports 固定为
  sequence-2 前 9,000 + root 8 后 6,000，371-file complete DAG 改为从 graph370
  进入且只反向引用已发现节点，在 API 24 Chrome 119 与 API 35 WebView 130 的
  evidence Debug/minified non-debuggable evidence Release 四行均通过 2+3+1 strict
  calibration、单 fault、`8/32` termination、512/512 cursor release、Worker `1→2`、
  five-zero cleanup 与 same-Binder successor。Release-equivalent 仍不是
  normal/shipping Release。第三个无编号 follow-up 随后把同一 ordinary parse
  authority 部署到真正的 normal Release：production host/coordinator 通过 frozen
  `onProgress(Bundle)` 发布最多 14 条 primitive-only public phase/work progress，
  normal Release DEX 继续排除 private receipt namespace。API 24 Chrome 119 与 API 35
  WebView 130 各完成 2 次成功 precondition、3 次 baseline strict intersection、唯一
  parse fault 与 same-Binder successor；fault 均以 5 条公开进度停在 `8/32`，返回
  timeout、零 compilation/output，target app 均为 non-TEST_ONLY、non-debuggable、
  经 R8 的 normal Release。
S1-S6i/P1/P2/P3a/P3b-a/P3b-b1/P3b-b2a/P3b-b2b/P3b-b2c/P3b-b2d/P3b-b2e/P3b-b2f/P3b-b2g/P3b-b2h/P3b-b2i/P3b-b2j/P3b-b2k，以及其后的无编号
external-UID Binder natural semantic/parse timeout 与 normal/shipping Release natural
parse timeout follow-up，全部仍是 provisional
evidence，**不是** Public R2，且尚未达到 R2 退出条件。

内部 provisional slice 进度（不等同于下方 54 项 public R2 验收清单）：

- [x] R2-S1–S5 compiler/coordinator pipeline
- [x] R2-S6a `DEBUG` exact live-epoch proof
- [x] R2-S6b system-owned post-death reconciliation
- [x] R2-S6c app-private production-build live-epoch owner
- [x] R2-S6d owner restart/rebind and registration-gap poison replay
- [x] R2-S6e retired-generation teardown callback replay fence
- [x] R2-S6f exact persisted-Job pre-arm and no-successor recovery
- [x] R2-S6g single running fixed-ID Job handoff and `onStopJob()` token-fence proof
- [x] R2-S6h single same-token JobService owner-death redelivery proof
- [x] R2-S6i retained exact-work overlapped owner/compiler kill-request proof
- [x] R2-P1 production/public-shaped admission source and build gates
- [x] R2-P2 versioned profile/config authority and binary APK manifest gates
- [x] R2-P3a app-private file-backed input VFS spool and bounded random-access native framing
- [x] R2-P3b-a bounded Worker representation/lifetime and desktop 64+8 MiB retained-memory gate
- [x] R2-P3b-b1 DEBUG Android 64 MiB capacity boundaries and exact success realm retirement
- [x] R2-P3b-b2a DEBUG exact phase-entry hard-stop and active renderer-loss recovery evidence
- [x] R2-P3b-b2b repeated resource envelope and cold-start orphan cleanup evidence
- [x] R2-P3b-b2c external production-fence source/APK gates and API 24/35 debug/release runtime evidence
- [x] R2-P3b-b2d non-shipping external exact-64 final TEST_ONLY artifact/runtime evidence
- [x] R2-P3b-b2e exact-64 natural-ingress timeout and final cleanup evidence
- [x] R2-P3b-b2f external plus-one pre-callback cleanup receipt and same-Binder successor evidence
- [x] R2-P3b-b2g ordinary natural parse/semantic/emit cancellation and cleanup evidence
- [x] R2-P3b-b2h ordinary natural semantic request-start absolute-deadline timeout evidence
- [x] R2-P3b-b2i ordinary natural parse request-start absolute-deadline timeout evidence
- [x] R2-P3b-b2j ordinary natural emit request-start absolute-deadline timeout evidence
- [x] R2-P3b-b2k external-UID Binder natural emit request-start absolute-deadline timeout evidence
- [x] unnumbered external-UID Binder natural semantic request-start absolute-deadline timeout evidence
- [x] unnumbered external-UID Binder natural parse request-start absolute-deadline timeout evidence
- [x] unnumbered normal/shipping Release natural parse request-start absolute-deadline timeout evidence

internal provisional evidence 现为 **29/29**。下方 Public R2 checklist 仍为
**0/54**，四项 capability 仍全部为 `false`。

当前实现边界：

- 内部只读 `R2ProjectInputSnapshot` 现在同时支持 historical heap seam 与 P3a
  file-backed production snapshot。S1 direct-constructor path/limit policy 对
  UTF-8 text VFS 使用历史 provisional 配额（256 files、256 KiB/file、512 KiB
  total）；production loader 可按 frozen ZIP input envelope 流式校验并索引 caller
  source，但 host 在 dispatch 前仍以 `PROVISIONAL_WORKER` 限制 Worker 为
  512 files、8 MiB/file、8 MiB total。两种 snapshot 均执行 printable-ASCII canonical
  path、受控 `/sources`/`/declarations`、case-fold collision、排序后的完整
  source-root inventory 和 defensive-copy 校验；`/sources/*.d.ts` 可作为非 root
  声明被导入。P3b-b2c 的 ordinary external inclusive case 只让 caller
  `4,603,850` bytes 与 installed trusted `3,784,758` bytes / 108 files 合计
  exact `8,388,608` bytes。P3b-b2d 只在 non-shipping evidence build、精确
  external fixture attestation 与固定 scope/size 同时成立时派生 expanded Worker
  authority；普通 Debug/Release external/public dispatch 仍是 512 files、
  8 MiB/file、8 MiB total。
- 隔离 Worker 的 `compileProject` 使用 `ts.createIncrementalProgram`，不读取
  `ts.sys`，不访问宿主文件系统或网络。project ingress 只接受 S3 framed state
  machine；旧的单消息 `compileProject` dispatch 已删除。P3b-a Worker 内部可
  接受最多 4607 files、8 MiB/file、72 MiB total，但 production host 的独立
  eligibility fence 仍为 512 files/8 MiB total。
- Worker 在每文件 digest + fatal UTF-8 后清零 input bytes，只保留一份 decoded
  String record；VFS 不再重编码，CompilerHost 按需创建并按 canonical path 缓存
  `version="0"` 的 `SourceFile`。Program 创建前另限制 path length/depth、
  aggregate path/directory trie 和 per-file/project token；diagnostics 逐
  SourceFile/逐条计费，compile `finally` 释放 Program/cache/VFS/trie/input text
  引用。这仍可能在一个 job 内同时持有全部 String 与 AST，不是 streaming parser。
- 首个 provisional Rhino profile 固定 `ES2018 + CommonJS + Node10 resolution`,
  强制 `strict`, `noEmitOnError`, LF 和外部 Source Map; 每个 job 创建全新
  Program, 暂不跨 job 复用 build info.
- Worker 临时限制为 512 outputs、512 KiB/output、1 MiB total output 和
  1000 diagnostics/512 KiB；完整 typed result 暂限 6 MiB。这些不是
  frozen input 64 MiB container/64 MiB uncompressed 与 output 128 MiB
  container/256 MiB uncompressed archive contract.
- native host 通过有界 verifier 在 WebView main thread 之外执行 strict frame
  parse、Base64 decode、摘要/UTF-8 与最终 typed result 校验，并把所有失败绑定到
  exact pending identity/session/Worker generation。result root/options/
  diagnostics/outputs/Source Map 均要求 exact keys；duplicate、unknown field 和
  非法 surrogate 会被拒绝。大 result 的最终 typed verification 使用独立单线程
  executor，不阻塞 control-frame parser；预分配 accumulator 的 `finish()` 零拷贝
  转移 backing array，失败/释放以及 final parse 后会清零。typed
  diagnostics/modules/maps 及其派生集合均 defensive-copy 且不可变.
- P3a input frame 的 cursor read、streaming SHA-256、ACK-driven `nextFrame()` 与
  release 由独立单线程 executor 串行执行；WebView main thread 只做 exact
  pending/session/port/generation/epoch fence 与发送。native framer 只保留一个
  64 KiB reusable buffer；input/output raw chunk 均为 64 KiB，完整 String
  WebMessage 最多 128 KiB，window 仍为 1，不再编码或持有整个 caller source.
- golden 覆盖两文件相对 import（2 JS + 2 个非空 Source Map）、唯一 semantic
  diagnostic 且 0 output、`/declarations/lib.d.ts` 与非 root
  `/sources/types.d.ts` diagnostic、以及 type-only root 合法空 `mappings`.
  native 侧重新核对 output SHA-256、module/map/source 一一映射和 Source Map
  `file/sourceRoot/sources`.
- lifecycle instrumentation 覆盖 registration 前 first-wins stop、active keyed
  cancel/timeout、probe/project busy 互斥、pending project close、in-flight
  result-finalizer close 的 cleanup proof/replacement host、exact teardown 后仍
  等待 finalizer release 的 gated cancel，以及在实际 renderer handle 可用时
  project renderer death 不重试、下一 session 恢复。P3b-b1 进一步要求每个成功
  project 在 terminal publication 前销毁 exact WebView realm；下一 project 懒创建
  更高 generation 的 session。P3b-b2a 再以 exact request/session/Worker
  generation 复核 ingress、parse、semantic、emit 的 strict
   started/completed sequence；`DEBUG` phase-entry spin 可让 keyed cancel/timeout
   和 active renderer termination 命中同一 exact phase。它仍不是零 spin 的自然
   TypeScript parse/semantic/emit 阶段中途抢占或 LMK/OOM 证据。P3b-b2b 已用
   零 spin 的自然 ingress cancellation 补上 active cursor release、Worker
   replacement 与 repeated renderer PSS envelope，但没有将该结论外推到其余
   TypeScript phase。P3b-b2e 又以固定 5 秒 deadline 连续三轮命中 exact-64-MiB
   partial ingress，自然产生 timeout，并证明 cursor release、Worker replacement、
   exact session retirement、host close cleanup 与 small successor；Binder 固定
   10 秒 deadline/public ACK 另证明 end-to-end terminal、零输出与最终
   coordinator/transient cleanup。Binder deadline 可能在 staging/validation/spool
   或 host ingress 到期，因此 partial ingress 结论只来自 direct-host telemetry。
- P3b-b2f 的 observation bit 只由 TEST_ONLY evidence build 中精确
  same-signer、distinct-UID、singleton fixture package/calling UID attestation
  派生；它保持 `PUBLIC_PREVIEW` origin、`expandedWorkerCapacity=false`，三类
  dedicated `+1` request 均不携带 external capacity scope。普通 Debug/Release
  不获得该 telemetry，normal release 经 R8 后也不保留 cleanup prefix/class/
  epoch-domain marker。pre-ACK snapshot 在 `startCompilation` 返回后、ACK 前以
  最多 80 ms、每 10 ms 一次的有界只读 metadata sampling 惰性建立；unavailable
  或非 exact candidate 不缓存，只有 active lease 为 1 且 input/output exact-target
  FD 为 `1/1` 才缓存。同一次 `getCompilerInfo()` 内 pre-ACK capture 与 current
  snapshot 是两次独立枚举，以排除 API 24 同步 Binder transaction 暂存的
  descriptor duplicate；该 sampling 不是 admission/fault retry，且 80 ms 小于
  natural-timeout calibration 的 100 ms 最小 margin。
  terminal path 先释放 lease/FD/transient/cursor，再创建不可变 receipt，之后才
  调用 callback；fixture 在 callback 返回后读取 receipt 并连续取得 5 个 all-zero
  current snapshot。跨 pre-ACK/receipt/current/successor 只导出 pid、start ticks、
  process name 与包含 256-bit nonce 的 domain-separated SHA-256；原始 boot ID/
  nonce 不离开 compiler process，也不作为 external authority。
- P3b-b2g 的 Worker 在真实 parse/semantic/emit work unit 后各最多发布两个
  bounded internal progress marker；page 在同一 task 中冻结最后 marker 后终止
  exact Worker，native 仅在 request/session/Worker/phase/sequence receipt 全部
  匹配时发布 termination evidence。它不增加 start-frame flag，不使用 expanded
  authority、spin 或 timeout 注入，也不把“page 最后观察到 sequence 2 且未观察到
  completed”外推成 TypeScript 指令级瞬时位置。
- P3b-b2h 只校准 semantic：每台设备的三次完整成功 baseline 都以 request-start
  absolute nanos 记录 sequence 2/completed，严格交集保留双边至少 100 ms 后冻结
  midpoint；fault 只运行一次。等待使用同一 absolute nanos deadline，interrupt
  不能伪造提前 timeout，completion/deadline race 通过同一个 operation-handle
  terminal selection 收口。测试驱动不调用 `requestStop`，也无 retry、spin 或
  expanded admission。
- P3b-b2i 只校准 parse：两次完整成功 precondition 先稳定运行环境但不进入
  timing sample，随后三次完整成功 baseline 以 request-start absolute nanos
  记录 sequence 2/completed，并用同一 strict-intersection/midpoint 规则冻结
  timeout，最后只运行一个 fault。8-root workload 保持 ordinary 117-file/
  8-MiB production admission，测试驱动同样不调用 stop、不 retry、不 spin，
  也不使用 expanded authority。
- P3b-b2j 在其 source state 只校准 emit：独立 TypeScript 6.0.3 / ES2018
  workload 由 2 个 light root 与 6 个 heavy root 组成，每个 heavy root 含
  18 条 depth-110
  optional chain，共 116 files / 3,841,832 bytes / 110,639 tokens / 400
  frames。两次完整成功 precondition 不进入 timing sample，三次 baseline 以
  同一 request-start strict-intersection/midpoint 规则冻结 timeout，随后只运行
  一个 fault；测试驱动不调用 stop、不 retry、不 spin、不使用 expanded
  authority、synthetic calibration 或 phase-relative deadline。
- P3b-b2k 把相同的 emit-only 原则推进到 external public Binder。standalone
  fixture 必须 same-signer/distinct-UID，evidence app/fixture 必须 TEST_ONLY；每个
  API 24/35 × evidence Debug/Release 行在一个 Binder session 中执行 2 次
  precondition、3 次 baseline、严格 midpoint 后唯一一次 fault 与一个 small
  successor。coordinator 把 admission 时冻结的 request-start absolute
  `elapsedRealtimeNanos` deadline 原样传给 host，避免由毫秒 remainder 重建产生
  相邻 tick race；callback 前不可变 phase receipt 与 cleanup receipt 分别证明
  request-correlated Worker phase/termination/input telemetry 和全部 Binder/native
  ownership cleanup。normal Release 不携带这些 private receipt keys。
- b2k 同时把 emit pipeline 收口到真实的最终结果交付：emit 覆盖
  `builder.emit()`、有界顺序 output hashing、output/Source Map 结构校验、result
  JSON/UTF-8 与最终 digest，以及 window=1、ACK-backpressured framed result egress；
  `projectResultStart`/chunks 在 emit active 时合法，只有最终 chunk ACK 后才发布
  emit `completed`，随后发布 `projectResultEnd`。因此 natural timeout 证明的是
  必需 emit pipeline 在最终 ACK 前被终止，不声称 TypeScript 指令级
  `builder.emit()` 内部中断；`projectResultEnd` 后的 native SHA-256/fatal UTF-8/
  typed-result finalizer 不属于 emit。
- 无编号 external semantic follow-up 只扩展 fixture/workload、exclusive runner 与
  source/APK gate，不修改 production timeout 选择或 Worker phase protocol。semantic
  workload 精确占满 ordinary 512-file fence：404 个 caller files 加 108 个 installed
  declarations，共 7,726,972 bytes / 151,525 scanner tokens / 1,588 frames；每次成功
  生成 512 个 raw outputs，共 720,866 bytes。每行用 semantic sequence-2/completed
  strict intersection 选择自然达到 public 6,000-ms minimum 的 unclamped midpoint，
  只运行一个 fault；测试侧仍不调用 stop、不 retry、不 spin、不使用 expanded
  admission、synthetic calibration 或 phase-relative deadline。
- 该 semantic follow-up 的 evidence Release 虽继承 release、经 R8/minify 且
  non-debuggable，仍是 TEST_ONLY Release-equivalent evidence build，不能替代 normal/
  shipping Release。normal Release 继续排除 private phase/cleanup receipt keys。
- 无编号 external parse follow-up 同样不改 production protocol，只增加 exact
  512-file parse workload、exclusive runner 与 source/APK gate。15,000 个 imports
  分为 sequence-2 前 9,000 与 root 8 后 6,000；371-file complete DAG 使用
  reverse-index low-stack traversal，保留 68,635 条边但不形成线性递归栈。四行仍
  使用 2+3 strict intersection 与单 fault，parse termination 固定为 `8/32`；
  Release-equivalent 同样只是 TEST_ONLY evidence，不替代 normal/shipping Release。
- 无编号 normal/shipping Release parse follow-up 不使用 private receipt。host 在
  exact request/session/Worker、phase/order 与 real-work threshold 验证后生成 native
  observed event，coordinator 再从不可逆 request-start monotonic nanos 计算相对时间，
  通过 frozen oneway `onProgress(Bundle)` 发布 revision 1 的 additive public
  envelope。每次 compilation 最多 14 条；phase 事件只含 started/completed，work
  事件只含 sequence 1/2、`ceil(total/8)` / `ceil(total/4)` 与 total，不含 path、
  source/output text、diagnostic、绝对时钟或 Worker identity。normal Release 的成功
  main workload 精确发布 14 条，timeout 精确发布 ingress start/complete + parse
  start/sequence 1/sequence 2 共 5 条，small successor 只发布 8 条 phase transition。
  这证明 host-observed parse boundary，不外推为 TypeScript parser 指令级瞬时中断，
  也不在缺少 private receipt 时声称 exact Worker/cursor/five-zero cleanup。
- R1 `transpileModule` prewarm probe 仅作为冻结兼容门禁保留; R2 项目编译核心不
  使用它. frozen AIDL/`TypeScriptCompilerContract`/ZIP v1 未修改；当前 API 只
  additive 新增 `TypeScriptR2Contract`，freeze gate 逐 byte 保留全部 frozen v1
  class。普通 frozen-v1 `startCompilation` 仍按 R0 拒绝；携带完整 P1
  revision/fingerprint/archive hashes 的 request 可进入 release/debug preview，
  ticket 返回后必须以 transaction 6 的 public `acknowledge` operation 启动。
  `compilerAvailable/projectCompilation/semanticDiagnostics/sourceMapV3` 四项仍全部
  为 `false`.

R2-P1 增加的边界：

- release/debug Service 都创建 `R2CompilationCoordinator`；production preview
  admission 在 supervisor registration 未 ready 时 fail closed，且不接管 FD。
- additive request 要求 profile/runtime revisions、compiler/declarations/runtime
  fingerprints、完整 input archive SHA-256 与 request-manifest SHA-256；缺失或
  malformed 的 P1 metadata 在 FD ownership transfer 前拒绝。debug spin/cleanup
  hooks 由 control origin invariant 禁止进入 public request。
- accepted ticket 显式返回 `acknowledgementRequired=true` 与
  `acknowledgementTimeoutMs=5000`；public preview 的 `timeoutMs` 下限为
  6000 ms，慢速非修改性 output FD preflight 在 server-side 计时前完成，但
  不承诺 ticket 返回后仍有完整 5 秒。client 使用
  `cancellationOperation=acknowledge`；缺省/`cancel` 保持取消语义，授权继续绑定
  calling UID + request ID + secure job token。错过 public ACK deadline 返回
  `false`；成功 ACK 的重复调用不复活任务，private debug late-unack 幂等行为不变。
- `getCompilerInfo`/plugin capability Bundle 公开当前 provisional profile revision
  与 runtime-capability fingerprint，但四个 availability flags 继续为 false；
  Node/profile/schema/streaming 完成前不得据此宣称 Public R2。
- debug instrumentation 新增 public-shaped full emit/wrong-token/post-return proof；
  独立 `r2-public-preview` application fixture 为不同 UID、同签名 permission 的
  device proof 提供 accept → ACK → bounded failed terminal/empty-output 路径。
  当前 source/build gate 不替代尚未执行的 external device matrix。

完整 P1 契约见
[R2 Production/Public-shaped Admission Preview](../architecture/PUBLIC_ADMISSION_R2_PREVIEW.md).

R2-P2 增加的边界：

- `R2CompilationProfileRegistry.PROVISIONAL_RHINO` 成为唯一 profile/config
  authority：profile/config revisions、`configKind=none`、18 项 fixed options、
  canonical options JSON/SHA-256 与 runtime-capability fingerprint 从这里派生；
- additive public metadata 必须同时携带 config revision/kind；provisional input
  manifest exact shape 提升到 revision 2，并把同一 authority 保存在 snapshot；
- framed start/result 双向携带 profile/config authority，Worker 返回完整 18 项
  options；native parser 与 output packager 逐字段重新比较，任何 drift 都不能进入
  artifact commit；
- provisional compilation manifest/diagnostics 同步提升到 revision 2；effective
  options fingerprint 只由 plugin canonical JSON 计算，caller/input 不声明它；
- fixture 聚合现在解析 debug/release 实际 APK binary manifest，保留完整 XML
  QName，并精确验证 compiler service exported/permission/process/action/category、
  application/service effective enabled、整个 application 内唯一 compiler
  endpoint，以及 fixture self-target、distinct UID shape、permission/query；
  它仍不替代设备运行。

完整 P2 authority 说明见
[R2 Provisional Profile and Config Authority](../architecture/PROFILE_AUTHORITY_R2_PROVISIONAL.md).

R2-P3a 增加的边界：

- production `R2ProjectArchiveLoader` 在同一 private held staging inode 上用
  `validateImmutableTo(..., ZipArchiveV1Limits.INPUT)` 完成 frozen ZIP v1
  actual payload/CRC/size/ratio 校验，并通过 transactional sink 将每个最多
  16 KiB callback 写入第二个 app-private VFS spool；失败会 abort 并删除 partial
  state；
- 只有最多 512 KiB 的 provisional `request-manifest.json` 会短暂 materialize；
  caller source 的 size/SHA-256/fatal UTF-8/UTF-16 length 均从 spool range
  验证，trusted declarations 随后追加到同一 spool。seal 后 snapshot 只保存
  metadata、offset/length 与 shared read-only inode；
- native VFS snapshot/index 配置为最多 4095 caller sources + 512 trusted
  declarations、合计 72 MiB storage envelope；这不修改 frozen ZIP 64 MiB
  contract。production host 在创建 framer 前仍执行 512 files、8 MiB/file、
  8 MiB total 的 Worker eligibility gate；
- framer 使用单 cursor、单个 16 KiB reusable buffer、window=1 ACK 与 streaming
  digest recheck；input executor 负责 ACK/next/read/release，main thread 只接受
  exact identity/session/port/generation/epoch 对应的 prepared frame；这是 P3a
  当时的 frame size，P3b-b1 已在保持同一 allocation/window shape 下调整为
  64 KiB raw/128 KiB envelope；
- coordinator 在 compile success/failure、pre-dispatch stop 与 timeout 路径关闭
  adapted snapshot；reader/fallback path cleanup 无法证明时进入 supervision，
  不把结果提交为普通 terminal。

P3a 只消除 native caller-source aggregate heap materialization。P3b-a 已补上
Worker 内部 representation/lifetime 的 desktop 子阶段，P3b-b1 又补上 DEBUG
low-token ASCII 的 direct/Binder device boundary；P3b-b2d 后续补上
non-shipping external debug/release-equivalent exact-64 四边界，但 production
admission、自然 fault、PSS/cleanup 与 LMK/OOM gates 仍未关闭，因此仍不能宣称
Public end-to-end 64 MiB。
完整说明见
[R2-P3a File-backed Native Input](../architecture/FILE_BACKED_INPUT_R2_P3A.md).

R2-P3b-a 增加的边界：

- Worker ingress/VFS 的内部 capacity authority 为 4607 files、8 MiB/file、
  72 MiB total，对齐 4095 caller sources + 512 trusted declarations；native
  production host 仍在 framing 前执行 512 files、8 MiB/file、8 MiB total gate；
- file-end 在 SHA-256 后执行唯一一次 fatal UTF-8 decode，立即清零当前
  `Uint8Array`，只保存一份 text record。VFS 不 re-encode/re-decode；
  CompilerHost 按需构造并按 canonical path 缓存 `version="0"` 的 `SourceFile`；
- AST 前 complexity gates 限制 4096 path characters、64 segments、8 MiB
  aggregate path characters、65536 directory trie nodes、4 MiB unique
  directory-segment characters、65536 tokens/file 和 262144 tokens/project；
- diagnostics 按 options → per-file syntactic → global → per-file semantic →
  emit 增量序列化/计费；pre-emit error 不再调用 `builder.emit()`，避免重复聚合；
  compile `finally` 在成功/异常路径清空 Program、cache、VFS、trie 与 input text
  references；
- desktop child process 在 `--max-old-space-size=512 --expose-gc` 下完成单次
  64 MiB caller + 8 MiB trusted ASCII boundary compile，强制 GC 后检查 retained
  plateau，再执行小型 successor。它是 same-isolate Node aggregate evidence，
  不是 Android WebView renderer、compiler-service PSS、LMK/OOM 或 Unicode/
  高 token 项目的设备证据。

R2-P3b-b1 增加的边界：

- 新增 same-UID、`BuildConfig.DEBUG` 限定的 expanded-capacity control；public
  control invariant 与 release host 均禁止启用它，普通 production dispatch
  仍走 `PROVISIONAL_WORKER`；
- input/output raw chunk 从 16 KiB 调整为 64 KiB，内部 String WebMessage 上限为
  128 KiB，window=1、single cursor、single reusable buffer 与 exact ACK fence
  不变。API 35 上 16 KiB/23 KiB 诊断版本的 120 秒 ingress timeout 由 transport
  telemetry 定位，64 KiB 后完整 70,893,622-byte Worker payload 在 deadline 内通过；
- 流式 capacity fixture 精确生成 STORED container 64 MiB/`+1` 与 DEFLATED
  expanded 64 MiB/`+1`，两遍核对 size/CRC/SHA/compressed size，且 `+1` 会先在
  放宽 1 byte 的 streaming validator 下证明其余 ZIP 结构合法；
- API 24/28/35 direct host 完成 exact 64 MiB caller + 3,784,758-byte trusted
  declarations、大成功后的 exact session retirement 与 newer-session small
  successor；API 24/35 又完成 Binder accept → ACK → private ZIP/VFS → Worker →
  committed output 全链路的 STORED/DEFLATED inclusive/`+1`；
- 每个成功 project 现在先取得 exact `WebView.destroy()`/renderer-gone proof，
  再发布 terminal success；destroy 失败保留相同 pending/session identity 每
  250 ms 重试。该 realm boundary 仍不等于 PSS/LMK reclaim proof。

R2-P3b-b2a 增加的边界：

- b2a 首先让 Worker、page、native host 共同要求 ingress → parse → semantic →
  emit 各自精确一对 `started`/`completed`。b2k 的当前边界进一步让 ingress、
  parse、semantic 必须在 result start 前完成，而 emit 必须仍为 exact active
  phase；最终 result chunk ACK 后 emit 才完成，`projectResultEnd` 又必须位于该
  completion 之后；
- production frame 始终发送 `workerSpinMilliseconds=0` 与
  `workerSpinPhase=none`。只有 same-UID `DEBUG` control 可以同时指定非零 spin
  与 exact phase，public/release invariant 禁止该 seam；
- API 24/35 对四个 phase entry 分别完成 keyed cancel、deadline timeout 与
  replacement Worker ready proof，再由 small successor 验证恢复；
- API 35 在 exact 64 MiB caller 的 active semantic entry 终止 exact renderer，
  记录 request/phase/`RenderProcessGoneDetail`/session/Worker generation，证明旧
  `(pid,starttime)` 退出、不同 renderer identity 的 successor 成功。该次
  `didCrash=false` 只是主动 termination 分类，不是 LMK/OOM。

R2-P3b-b2b 增加的边界：

- API 24/35 在同一 compiler-service `(pid,starttime)` 连续完成三次
  exact-64-MiB STORED Binder success；每轮 terminal 后 active jobs、三类
  app-private transient 名称与 remote transient FD 都归零，完整 sorted FD
  target multiset 在成熟周期 1/2/3 保持一致，最后 small successor 成功；
- exact process identity 的 PSS 使用 5 个样本中位数。API 24 Chrome 119 是
  单进程 WebView，允许一次 96 MiB first-load envelope，但成熟 cycle 3 相对
  cycle 1 实测下降；API 35 独立 renderer 路径使用 48 MiB envelope，
  compiler-service cycle 3 相对 cycle 1 只增加 671 KiB；
- 零 spin 自然 ingress cancellation 连续三轮在 input 尚未完成、cursor active
  且至少 8 MiB 已接受时停止 exact handle；每轮 cursor opened/closed 平衡并
  released，terminated Worker generation 从 1 前进到 3。API 35 同一 renderer
  三轮 PSS 中位数为 153,021 / 147,886 / 154,175 KiB；API 24 provider 没有独立
  sandbox，因此只执行 cursor/Worker/recovery gate；
- service startup 在 admission epoch 打开后、host/coordinator 创建前非递归清除
  recognized `r2-private-input-*.staging`、`r2-project-vfs-*.spool` 与
  `r2-private-output-*.zip.staging` regular files。unknown/sentinel、
  recognized-name 目录和嵌套内容保留；枚举、权限恢复、删除或复核失败都会
  `failClosed()`。API 24/35 均以真实 predecessor hard exit → cold successor
  证明 cleanup 与后续 prewarm。

R2-P3b-b2c 增加的边界：

- standalone same-signer、distinct-UID fixture 固定 external invalid ZIP、
  production-fence inclusive 与合法 `+1` 三路径，均要求 accepted ticket、
  wrong-token ACK reject、exact ACK 与 1 秒 exactly-once quiet window；
- inclusive caller `4,603,850` bytes 与 installed trusted declarations
  `3,784,758` bytes / 108 files 合计 exact 8 MiB，并验证 emitted
  diagnostics/JS/map/manifest payload SHA、inventory 与 authority；合法 `+1`
  异步返回 `ERROR_ARCHIVE_QUOTA`、零 payload 与 empty output；
- authority 读取绑定 source APK/package identity 并在 request 构造前后复核。
  API 24/35 debug/minified-service-release runtime 已全部 PASS；本切片仍没有直接
  证明 plus-one remote cleanup。

R2-P3b-b2d 增加的边界：

- app 新增 non-shipping `r2CapacityEvidenceDebug` /
  `r2CapacityEvidenceRelease`；后者继承普通 release 的 R8/minify、
  resource shrink 与 non-debuggable 形态，只是 release-equivalent evidence
  build；两个 evidence variant 都以独立 evidence 文件名生成、声明 TEST_ONLY
  并保持 ordinary app application ID/signing，安装必须使用 `-t`，不是 shipping
  production release。normal release APK gate 只证明
  `__autojs6_r2_external_capacity_evidence_scope`、
  `stored-container-exact-64-v1` 与 `deflated-expanded-exact-64-v1` 三个 token
  不存在，不宣称其他广义 evidence marker 全部消失；
- service 只在 evidence build、base public metadata、固定 project/entry、
  distinct calling UID、唯一 fixture package、`FLAG_TEST_ONLY`、同签名、
  scope 与 frozen FD size 全部匹配时派生 external capacity control。caller 不
  提交 generic expanded bit；两个合法 `+1` rejection 也不携带 capacity scope。
  STORED scope 在 admission 时认证 container exact 64 MiB；DEFLATED scope
  只认证 container 位于 `1..64 MiB`，expanded exact-64 由 fixture 两遍自检与
  runtime terminal 共同证明；
- 常量内存 fixture 用 4 KiB `main.ts` + 八个 padding `.d.ts`、manifest/source
  fixed point 与显式两遍 ZIP32 writer，分别精确生成 STORED container
  64 MiB/`+1` 和 DEFLATED expanded 64 MiB/`+1`。inclusive 重验完整 emitted
  output；`+1` exactly once 返回 `ERROR_ARCHIVE_QUOTA`、零 payload 与 empty
  output；
- capacity runner 之前保留 b2c invalid ZIP/ordinary 8 MiB inclusive/`+1`，
  四边界之后再运行 ordinary 8 MiB inclusive successor。最终 TEST_ONLY
  artifact 已通过本地 gate，API 24/35 四行与 ordinary Debug/Release 回归均
  已 PASS；旧 hardening 前 hash/timing 不作为最终证据；

R2-P3b-b2e 增加的边界：

- direct-host 使用 exact 64 MiB caller 加 trusted declarations，在 API 24/35
  各连续三轮以固定 5 秒 deadline、`workerSpinMilliseconds=0`、
  `workerSpinPhase=null` 且不调用 `requestStop` 命中 partial ingress；每轮
  `ERROR_TIMEOUT` 的 elapsed 不早于 deadline，input cursor opened/closed
  平衡、released、accepted bytes 位于 `0..total` 内，terminated Worker
  generation 从 1 前进至 3，small successor 成功并退休 newer WebView session，
  host close 与 spool cleanup 均可证明；
- Binder 使用 STORED exact `67,108,864`-byte container、固定 10 秒 deadline 与
  public ACK；API 24/35 均返回 matching request ID、exactly-once
  `OUTCOME_TIMED_OUT + ERROR_TIMEOUT`、零 diagnostics/modules/manifest digest
  与 empty output，terminal elapsed 和独立 wall clock 都不早于 deadline。
  terminal 后 active job、命名/remote transient FD 最终归零，compiler
  `(pid,starttime)` 不变且 small successor 成功；
- Binder 路径可能在 staging/SHA/ZIP validation/spool 或 host ingress 到期，
  因而它只证明 end-to-end natural deadline/final cleanup；partial ingress
  由 direct-host phase/cursor telemetry 单独证明。它也不单独声称 callback
  发布一定晚于所有 cleanup，顺序边界由现有 production source policy 与最终
  cleanup 证据组合约束。

R2-P3b-b2f 增加的边界：

- standalone fixture 以 normal Debug/Release 在 API 24/35 各运行一次 ordinary
  production-fence `+1` rejection + inclusive successor；TEST_ONLY evidence
  Debug/minified non-debuggable release-equivalent 则各运行 production-fence、
  STORED container 64 MiB `+1`、DEFLATED expanded 64 MiB `+1`，每个 rejection
  后紧跟 ordinary inclusive successor。最终 normal/evidence 共 8 行全部 PASS；
- 三类 evidence `+1` request 都通过 ordinary public request 进入，明确不携带
  capacity scope，保持 512 files、8 MiB/file、8 MiB total Worker fence且不获得
  expanded authority。pre-ACK 按 request ID、job-token SHA-256 与 caller/server
  descriptor-target SHA-256 关联，server input/output FD 各精确为 1、active
  lease 为 1；
- coordinator 完成 lease、caller FD、private input/project spool/private output
  named/open transient 与 cursor cleanup 后，先保存 immutable terminal receipt，
  再调用 exactly-once callback。fixture 只在 callback 后读取并关联 receipt；
  quota rejection cursor 为 `NOT_CREATED`，successor cursor 为 opened/closed
  balanced + `RELEASED`，随后 5 个 current sample 连续全零；
- 同一 bound Binder 贯穿全部 case；safe process identity 只暴露 pid/start ticks/
  process name 与 domain-separated epoch SHA-256，不暴露可用于 termination
  authority 的 raw boot ID/nonce。API 35 cold supervisor 未 ready 由同一原始
  Binder 上的 bounded admission retry 收敛；API 24 pre-ACK 则在同步
  `startCompilation` transaction 返回后、ACK 前执行有界只读 metadata sampling，
  只缓存 exact `1/1` ownership，避免把 transaction-owned temporary duplicate FD
  计入 server ownership。该 observation sampling 不重发 admission 或 fault。

R2-P3b-b2g 增加的边界：

- deterministic natural workload 固定为 108 个 trusted declarations + 192 个
  roots = 300 files、4,594,999 bytes、232,249 tokens；baseline 必须以
  TypeScript 6.0.3 得到零 diagnostics、192 JS + 192 maps、562,945 output
  bytes 与冻结 aggregate SHA-256；
- Worker 在真实 parse/semantic/emit work unit 后各最多发布两个 bounded
  progress marker；本 workload 的 sequence 2 分别为 parse `48/192`、
  semantic `48/192`、emit `96/384`。page 在 exact termination 前冻结最后
  progress，native 只接受 request/session/Worker/phase/sequence 完全匹配的
  receipt；
- API 24/35 ordinary same-UID Debug 六个 cancellation case 全部 PASS，均保持
  `workerSpinMilliseconds=0`、`workerSpinPhase=none`、ordinary admission；
  每案 300/300 cursor 平衡释放、终止并替换 exact Worker、小项目 successor
  成功，最终 host/spool cleanup 可证明；
- 该证据只表示 page 在 terminate 前最后观察到 sequence 2 且尚未观察到 phase
  completed，不表示 TypeScript 指令级瞬时位置，也不覆盖自然 deadline/timeout。

R2-P3b-b2h 增加的边界：

- 独立 ordinary workload 固定为 108 个 trusted declarations、256 个 roots 与
  148 个 late declaration helpers，共 512 input files；三次 calibration
  baseline 均完整成功、零 diagnostics，并精确产生 256 JS + 256 maps、
  720,866 output bytes 与 aggregate SHA-256
  `4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1`；
- 每台设备只校准 semantic。`lower` 取三次 sequence-2 request-start elapsed 的
  最大值后再前进到严格下一毫秒，`upper` 取三次 completed 前的严格最后毫秒；
  固定 timeout 为交集 midpoint，必须保留双边至少 100 ms，之后只运行一个
  fault target；
- 修复 completion/deadline 原子决策后重建的 API 24 strict window 为
  `9,331..12,777 ms`，固定 timeout `11,054 ms`，双边 margin
  `1,723/1,723 ms`，deadline selection overshoot `2 ms`；API 35 为
  `37,725..38,828 ms`、`38,276 ms`、`551/552 ms` 与 `0 ms`；
- 两案均在 semantic sequence 2 `64/256` 后、semantic completed 前返回
  `ERROR_TIMEOUT` 与 `compilation=null`，stop cause 为 `TIMED_OUT`；512/512
  cursor 平衡并 released，Worker generation `1→2`，小项目 successor 与
  host/spool cleanup 可证明；
- fault driver 不调用 `requestStop`，不 retry、不注入 spin、不使用 expanded
  admission。deadline 以 request-start absolute monotonic nanos 计算；interrupt
  不改变 deadline，completion race 与 timeout 使用统一 terminal selection。

R2-P3b-b2i 增加的边界：

- 独立 ordinary workload 固定为 108 个 trusted declarations、8 个 roots 与
  1 个 ambient declaration，共 117 input files、4,879,527 bytes、
  257,388 tokens 和 418 transport frames；resolver depth 为 8、source path
  depth 为 10，aggregate path chars 为 4,434、单 path 最大 65 chars；
- TypeScript 6.0.3 desktop authority 精确产生 8 JS + 8 maps；JS/map 分别为
  1,524/1,814 bytes、总计 3,338 bytes，aggregate SHA-256 为
  `c16cf1e19354655321bc7bd13bea9840900f2f2b2bf67793b4d1b926ee42e6b3`；
- 每台设备先运行两次完整成功 precondition，且不把它们计入 timing；随后三次
  完整成功 baseline 形成 parse sequence-2/completed strict intersection，
  保留双边至少 100 ms 后冻结 midpoint，并只运行一个 fault target；
- API 24 strict window 为 `2,475..5,949 ms`，固定 timeout `4,212 ms`，
  双边 margin `1,737/1,737 ms`；API 35 为 `11,894..13,164 ms`、
  `12,529 ms` 与 `635/635 ms`。两案 deadline selection overshoot 均为
  `0 ms`；
- 两案均在 parse sequence 2 `2/8` 后、parse completed 前返回
  `ERROR_TIMEOUT`、`compilation=null` 与 `TIMED_OUT`；117/117 cursor
  平衡并 released，fault session generation 为 6、Worker generation
  `1→2`，小项目 successor 与 host/spool cleanup 可证明；
- fault driver 不调用测试侧 stop，不 retry、不注入 spin、不使用 expanded
  admission；ordinary production limits 继续为 512 files、8 MiB/file、
  8 MiB total。

R2-P3b-b2j 增加的边界：

- 独立 TypeScript 6.0.3 / ES2018 ordinary workload 固定为 108 个 trusted
  declarations 与 8 个 roots（2 light + 6 heavy），共 116 input files、
  3,841,832 bytes、110,639 tokens 与 400 transport frames；每个 heavy
  root 含 18 条 depth-110 optional chain，aggregate root 为 57,074 bytes /
  24,310 tokens，单 root 最大为 9,502 bytes / 4,049 tokens；
- TypeScript desktop authority 精确产生 8 JS + 8 maps；JS/map 分别为
  769,510/196,204 bytes、总计 965,714 bytes，aggregate SHA-256 为
  `e7eac5f110b4dff7bbb732ea47c3f21c083b44fa215b740895a8e8bac9ba4fa6`；
  距 1 MiB output ceiling 仍有 82,862 bytes；
- 每台设备先运行两次完整成功 precondition，且不把它们计入 timing；随后三次
  完整成功 baseline 形成 emit sequence-2/completed strict intersection，
  保留双边至少 100 ms 后冻结 midpoint，并只运行一个 fault target；
- 下列 b2j timing 属于当时“`builder.emit()` 后、result start 前完成 emit”的旧
  completion boundary，是可追溯的历史 direct-host 证据；b2k 当前二进制已把 emit
  completion 延伸到最终 result ACK，故这些数值不是当前二进制 calibration，当前
  emit timeout authority 由下方 b2k 四行矩阵取代；
- API 24 strict window 为 `4,586..5,879 ms`，固定 timeout `5,232 ms`，
  双边 margin `646/647 ms`，deadline selection overshoot `2 ms`；API 35
  为 `11,058..11,638 ms`、`11,348 ms`、`290/290 ms` 与 `1 ms`；
- 两案在当时的旧 completion boundary 上均于 emit sequence 2 `4/16` 后、
  emit completed 前返回
  `ERROR_TIMEOUT`、`compilation=null` 与 `TIMED_OUT`；116/116 cursor
  平衡并 released，fault session generation 为 6、Worker generation
  `1→2`，小项目 successor 与 host/spool cleanup 可证明；
- fault driver 不调用测试侧 stop，不 retry、不注入 spin、不使用 expanded
  admission、synthetic calibration 或 phase-relative deadline；ordinary
  production limits 继续为 512 files、8 MiB/file、8 MiB total。

R2-P3b-b2k 增加的边界：

- external fixture 固定为 same-signer、distinct-UID、TEST_ONLY caller，通过
  signature-protected exported service 的 public accept/ACK/callback pipeline；
  evidence Debug 与继承 release、R8/minified、non-debuggable 的 evidence Release
  在 API 24/35 形成四行矩阵，normal shipping Release 排除 private evidence keys；
- primary ordinary workload 为 256 roots（64 light + 192 heavy）；每个 heavy
  root 含 1 条 depth-61 optional chain 与 4 组、每组 90 个 erased `as any`
  assertions。caller 为 256 files / 555,766 bytes / 168,896 tokens；加 108 个
  installed declarations 后为 364 files / 4,340,524 bytes / 255,225 tokens /
  13,073 aggregate path chars / 1,144 frames，仍在 512-file/8-MiB ordinary
  production fence 内；
- 每行严格执行 2 次不计时的成功 precondition、3 次完整成功 baseline、1 次
  natural timeout fault 与 1 个 small successor。三 baseline 的 emit sequence-2/
  completed strict intersection 必须保留双边各至少 100 ms；midpoint 不 clamp，
  必须自然达到 public 6,000-ms minimum，失败时 faultAttempts=0；
- raw Worker primary output 固定为 512 files：717,302 JS bytes + 259,136 map
  bytes = 976,438 bytes，aggregate SHA-256
  `2b2a4529552aa3a3f9b33cd2a379240bc2852ca065a33ff884a6160847f1b1de`；
  距 1 MiB output ceiling 保留 72,138 bytes。fixture 先从 packaged modules 重建并
  验证 raw authority，再独立重验 ZIP inventory、diagnostics、Source Map v3、
  manifest digest、provenance/effective-options 与全部 packaged file
  size/SHA/inventory fingerprint；
- coordinator 在 admission 时冻结 request-start absolute nanos，并经
  `compileProjectUntilElapsedRealtimeDeadline` 把同一原始 nanos deadline 传给
  host。host completion wait、stop selection 与 page receipt 使用同一边界，不从
  rounded millisecond remainder 重建 deadline；
- callback 前冻结的 private phase receipt 关联 Binder request ID 与 exact private
  host request ID。成功 receipt 含 parse/semantic/emit timing；fault receipt 含
  completed parse/semantic timing、`TIMED_OUT` emit termination、sequence 2
  `128/512`、deadline/overshoot、exact Worker replacement 与 364-cursor input
  telemetry。既有 cleanup receipt 另证明 pre-ACK FD/lease ownership 与 callback
  前 terminal cleanup，callback 后连续 5 次 current snapshot 全零；
- emit 在 `builder.emit()` 后继续覆盖有界顺序 hashing、Worker result serialization/
  digest 与 ACK-backpressured framed egress；`projectResultStart` 不结束 emit，最终
  chunk ACK 后才发布 emit `completed`，再发布 `projectResultEnd`。fault receipt
  因而证明真实 required pipeline 在最终 ACK 前仍 active，但不把它表述为
  instruction-level `builder.emit()` 中断；native typed-result finalizer 位于其后、
  不属于 emit；
- small successor 为 109 files / 3,784,795 bytes / 86,337 tokens / 379 frames，
  在同一 Binder/process epoch 成功并完成 cleanup。它只有 2 个 outputs，不满足
  phase progress 的 8-unit minimum，因而不要求也不伪造 phase timing receipt。

无编号 external-UID Binder natural semantic timeout follow-up 增加的边界：

- 复用历史命名的 `R2ExternalNaturalEmitTimeoutProjectFixture`，新增独立 semantic
  workload 与 exclusive `externalNaturalSemanticTimeoutEvidence` mode，不改变 b2k
  emit/successor authority。caller 由 256 roots（64 light + 192 heavy）与 148 helpers
  组成，共 404 files / 3,942,214 bytes / 65,196 scanner tokens；加 108 个 installed
  declarations 后精确为 ordinary ceiling 的 512 files / 7,726,972 bytes /
  151,525 tokens / 19,249 aggregate path chars / 1,588 frames。caller inventory
  fingerprint 为
  `460b3b9755e2669fb789c3190d9f6bc14e42c9f6cbe87bbf81cbe0d97bf6b3eb`；
- 每次成功固定生成 512 outputs：535,202 JS bytes + 185,664 map bytes =
  720,866 bytes，aggregate SHA-256 为
  `4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1`。
  fixture 逐项复核 raw Worker output、packaged ZIP inventory、diagnostics、Source Map
  v3、terminal manifest、provenance/effective options 与 payload digest；ordinary
  512-file/8-MiB production fence 没有扩容；
- 四行正式矩阵各执行 2 次不计时的完整成功 precondition、3 次完整成功 baseline、
  1 次 single-admission natural semantic timeout fault 与 1 个 small successor。
  semantic sequence-2/completed strict intersection 必须双边各至少 100 ms，midpoint
  不 clamp 且必须自然不低于 public 6,000-ms minimum；任一前置条件失败都在
  `faultAttempts=0` 时 fail closed；
- fault receipt 的 timing 只含 parse，phase history 必须为 entered
  ingress/parse/semantic 与 completed ingress/parse，并以 `TIMED_OUT` semantic
  sequence 2 `64/256` 终止；同时证明 `ERROR_TIMEOUT`、零 compilation/output、512/512
  cursor balanced + released、exact Worker replacement、同一 Binder/process epoch、
  callback 前 cleanup 与随后 5 次 current snapshot 全零。small successor 继续使用
  b2k 的 109-file/312-byte raw-output authority；
- API 24 正式 provider 为 Chrome-WebView `119.0.6045.194`，API 35 正式 provider
  为 Google WebView `130.0.6723.86`。API 24 Google WebView `53.0.2785.124` 的
  探索运行在首个 precondition 因当前 page host JavaScript 语法不兼容而自然等到
  120,035 ms timeout，未形成成功 timing sample，`faultAttempts=0`；它只证明
  fail-closed，不属于正式矩阵；
- evidence Release 继承 release、经 R8/minify 且 non-debuggable，但和 evidence Debug
  一样声明 TEST_ONLY，只是 Release-equivalent evidence build，不是 normal/shipping
  Release。normal Release DEX 继续排除 private receipt markers。

无编号 external-UID Binder natural parse timeout follow-up 增加的边界：

- exact 512-file workload 固定 32 roots；15,000 个 type-only imports 分为
  sequence-2 前 9,000 与 root 8 后 6,000。371-file complete reference DAG 从
  graph370 进入、只反向引用已发现节点，仍遍历 68,635 条 50-byte edges，但避免
  WebView 130 的线性递归栈；aggregate 为 8,177,717 bytes / 236,532 tokens /
  1,598 frames，caller fingerprint 为
  `334b1a3a940a741105c5222ca6c189c72dfb6c0ea994a72b86013df1fdbf72ef`；
- 每次成功生成 64 raw outputs / 13,155 bytes，SHA-256 为
  `18f79093d9a09c3111740e282a3ad642576230c142d6b7d7c61bb9c7950ca038`。
  四行各执行 2 preconditions + 3 baselines + 1 fault + 1 successor；正式 fault
  均在 parse sequence 2 `8/32` 后返回 `ERROR_TIMEOUT`、零 compilation/output、
  512/512 cursor release、Worker `1→2`、five-zero cleanup 与 same-Binder successor；
- API 24 Debug/Release-equivalent 的 strict window 分别为 `9296..11265` 与
  `6320..8464 ms`；API 35 Debug/Release-equivalent 分别为 `34486..35566` 与
  `14493..15001 ms`。四行 midpoint 均自然高于 public 6,000-ms minimum；
- ticket 返回后、ACK 前的最多 80 ms 只读 metadata sampling 只缓存 exact
  active lease `1` + FD `1/1`，不重发 request；API 24 Debug successor 实际以
  2 samples / 18 ms 收敛；
- 原始全-pre imports 候选在 API 24 Release 只形成 178 ms strict intersection，
  forward-DAG 候选又在 API 35 首个 precondition fail closed with
  `Maximum call stack size exceeded`；两者均未执行正式 fault，不计入最终矩阵。

无编号 normal/shipping Release natural parse timeout follow-up 增加的边界：

- frozen AIDL 不变；additive `TypeScriptR2Contract` 增加 public progress revision 1，
  固定 phase/work exact Bundle keys、request-start monotonic nanos 与每次最多 14 条。
  normal Release R8 DEX 必须保留公开 progress markers，同时排除 private natural-phase
  receipt prefix；
- API 24 Chrome `119.0.6045.194` normal Release 的 baseline parse
  `seq2/completed` 为 `6117/8527`、`5739/8385`、`5760/8172 ms`，strict window
  `6118..8172 ms`，timeout `7145 ms`；唯一 fault 在公开 seq2 `6356 ms` 后返回，
  terminal `7593 ms`、overshoot `448 ms`；
- API 35 WebView `130.0.6723.86` normal Release 的三次 baseline 为
  `14113/15450`、`14041/15652`、`14097/15591 ms`，strict window
  `14114..15450 ms`，timeout `14782 ms`；唯一 fault 在公开 seq2 `14475 ms`
  后返回，terminal `15142 ms`、overshoot `360 ms`；
- 两行 target app 均为 non-TEST_ONLY/non-debuggable normal Release，fixture 为
  distinct-UID/same-signer TEST_ONLY；fault 都精确收到 5 条 progress、parse `8/32`、
  `ERROR_TIMEOUT`、零 output、`privateReceipt=false`，随后同一 Binder 的 8-event
  small successor 成功。shipping path 不含 private receipt，因此不据此声称 exact
  Worker replacement、512/512 cursor 或 five-zero cleanup。

完整 P3b-b 仍需 normal/shipping
Release semantic/emit timeout、真实
LMK/OOM、Unicode/high-token/adversarial、output 边界与其余 fault-path
cleanup，之后
才能决定是否解除 production 围栏。完整说明见
[R2-P3b-b1 Android Device Capacity and Exact Realm Retirement](../architecture/WORKER_INPUT_R2_P3B_B1.md)；
phase/fault 边界见
[R2-P3b-b2a DEBUG Phase-entry Hard-stop and Active Renderer-loss](../architecture/WORKER_FAULT_R2_P3B_B2A.md)；
repeated resource/startup cleanup 边界见
[R2-P3b-b2b Repeated Resource and Startup Orphan Cleanup](../architecture/WORKER_RESOURCE_R2_P3B_B2B.md)；
external production-fence 边界见
[R2-P3b-b2c External Production-fence Pipeline](../architecture/EXTERNAL_FENCE_PIPELINE_R2_P3B_B2C.md)；
external exact-64 evidence 边界见
[R2-P3b-b2d External Exact-64-MiB Capacity Evidence Pipeline](../architecture/EXTERNAL_EXACT64_PIPELINE_R2_P3B_B2D.md)；
natural ingress timeout/final cleanup 边界见
[R2-P3b-b2e Natural Ingress Timeout and Cleanup](../architecture/WORKER_NATURAL_INGRESS_TIMEOUT_R2_P3B_B2E.md)；
external plus-one cleanup receipt 边界见
[R2-P3b-b2f External Plus-one Cleanup Receipt Evidence](../architecture/EXTERNAL_PLUS_ONE_CLEANUP_R2_P3B_B2F.md)；
ordinary natural phase cancellation 边界见
[R2-P3b-b2g Natural Parse/Semantic/Emit Cancellation](../architecture/WORKER_NATURAL_PHASE_CANCELLATION_R2_P3B_B2G.md)；
ordinary natural semantic timeout 边界见
[R2-P3b-b2h Natural Semantic Request-start Absolute-deadline Timeout](../architecture/WORKER_NATURAL_SEMANTIC_TIMEOUT_R2_P3B_B2H.md)；
ordinary natural parse timeout 边界见
[R2-P3b-b2i Natural Parse Request-start Absolute-deadline Timeout](../architecture/WORKER_NATURAL_PARSE_TIMEOUT_R2_P3B_B2I.md)；
ordinary natural emit timeout 边界见
[R2-P3b-b2j Natural Emit Request-start Absolute-deadline Timeout](../architecture/WORKER_NATURAL_EMIT_TIMEOUT_R2_P3B_B2J.md)；
external-UID Binder natural emit timeout 边界见
[R2-P3b-b2k External-UID Binder Natural Emit Timeout](../architecture/EXTERNAL_NATURAL_EMIT_TIMEOUT_R2_P3B_B2K.md)；
无编号 external-UID Binder natural semantic timeout follow-up 边界见
[External-UID Binder Natural Semantic Timeout](../architecture/EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_R2.md)；
无编号 external-UID Binder natural parse timeout follow-up 边界见
[External-UID Binder Natural Parse Timeout](../architecture/EXTERNAL_NATURAL_PARSE_TIMEOUT_R2.md)；
无编号 normal/shipping Release natural parse timeout follow-up 边界见
[Normal/Shipping Release Natural Parse Timeout](../architecture/SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_R2.md)；
P3b-a allocation/lifetime 背景见
[R2-P3b-a Worker Input Capacity and Lifetime](../architecture/WORKER_INPUT_R2_P3B_A.md).

R2-S2 增加的边界:

- service-owned caller PFD 通过 fixed-offset `pread` 从 offset 0 捕获, 不读取或
  改变共享 open-file-description cursor; capture 前后 `fstat` regular-file
  identity/size/state. bytes 写入 app-private staging、SHA-256、`fsync`, writer
  close 后仅持有同一个 read-only `RandomAccessFile`; Android/Linux 立即 unlink,
  Windows 保留 read-only fallback 至 reader close.
- frozen `ZipArchiveV1Validator.validate(File)` 行为未改. S2 新 random-access
  entry point 在同一 held inode 上检查 raw local/central records、actual
  inflate/CRC/size/ratio, 再在同一 handle 重算完整 staging digest.
- provisional `request-manifest.json` 使用 strict UTF-8/JSON, exact top-level/file
  keys, revision/profile/runtime/compiler 字段, sorted root/file inventory,
  per-file size/SHA-256 和 aggregate fingerprint; control object 另绑定完整
  archive 与 manifest SHA-256, archive 必须被 inventory 精确覆盖.
- caller 只可提供 `sources/**`. plugin 从签名 APK 重新验证并注入 108 个
  TypeScript standard libraries（3,784,758 bytes;
  fingerprint
  `6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f`）;
  distribution provenance path 唯一推导 `/declarations/**` VFS path. local
  runtime-capability fingerprint 也必须与 control object/manifest 三方一致.
- S2 legacy heap adapter 对内部 entry arrays 做 bounded ownership copy 后才
  hash/decode，并保留 257 ZIP entries/1 MiB、512 KiB/entry 的受限 test seam。
  P3a production loader 已不再调用该路径，而是使用上文 file-backed spool；
  512 KiB manifest materialization gate 仍保留。
- S3 framed transport 仍只把符合 8 MiB Worker eligibility 的 snapshot（包括完整
  标准库）送入 Worker，不再经过旧 512 KiB single-WebMessage 门禁；P3a 改变的是
  native snapshot/framer allocation shape，不是 Worker admission。

完整 provisional schema 与所有权说明见
[R2 Provisional Project Archive Adapter](../architecture/PROJECT_ARCHIVE_R2_PROVISIONAL.md).

R2-S3 增加的边界:

- API 24 基线的 native ↔ page 边界只使用 String `WebMessage`，每个 JSON
  envelope 在 S3/P3a 最多 32 KiB；P3b-b1 为真实 64 MiB device throughput 将内部
  上限调整为 128 KiB。page ↔ Worker 使用同一 frame schema 的 structured-clone
  object。input/result data 使用 canonical padded Base64，当前每帧最多 64 KiB
  decoded bytes.
- input 与 output 均为 window=1 stop-and-wait；exact receiver 完成
  shape/sequence/quota/data 校验并 ACK 后 sender 才推进。page 维护唯一 active
  project，禁止第二个 project 与无 key restart 旁路；Worker → page 只接受显式
  kind allowlist，active project 上的未知 kind 会精确失败并替换 Worker.
- ordinary production dispatch 继续使用 512 files、8 MiB/file、8 MiB total
  admission；P3b-b1 的 same-UID DEBUG expanded path 使用 4607 files/72 MiB；
  每文件 SHA-256 + fatal UTF-8 后才接受，source roots 从最终 inventory 推导。
- output result 当前也按 64 KiB raw chunks 分帧；native 重新核对 result
  SHA-256/fatal UTF-8，再执行 exact-key typed diagnostics/output/Source
  Map/inventory verifier。任何单个 WebMessage 都不再携带整个项目或整个 result.
- host-generated opaque operation handle 与 Worker generation 构成 teardown key。
  active cancel/timeout 由 page 精确调用旧 Worker `terminate()`；只有 replacement
  完成构造、脚本装载和 TypeScript startup 并发出 exact `ready` 后，page 才发布
  terminated/replacement generation ACK。构造或异步装载失败不伪造 ACK；
  wrong/stale key 不杀当前 Worker，旧 Worker frame 受 object identity +
  generation 双 fence.
- handle 在 registration 前后共享同一个 first-wins terminal-intent monitor；
  absolute monotonic deadline 在所有正常 terminal 竞争前再次检查。active host
  slot 只在 exact ready-gated termination ACK，或 exact session token 对应的
  `WebView.destroy()` 成功/renderer-gone proof 后释放，不产生 timeout/close
  synthetic outcome。pre-dispatch stop 因从未赋予 Worker operation 可直接终结。
  exact-session destroy 失败时保留 slot/session identity 并每 250 ms 重试；只有
  accepted teardown 的 main-handler post 或 retry scheduling 失败时，host 才进入
  fatal/poisoned admission 状态并标记需要外部 supervision。若 destroy 永久失败但
  重试仍可调度，当前 S3 会安全地无限保留 pending；S6a 证明 DEBUG/same-UID
  live-epoch termination，S6b 在 old epoch 离开后 reconcile 已成功提交或由
  successor bind 补提交的 work，S6c 再 provisionally 接入 app-private、非 DEBUG
  限定的 exact live-epoch owner。完整 owner/system lifecycle fault matrix 仍是
  blocker.
- 普通 production Worker 仍会 materialize 最多 8 MiB input text/AST，output 仍是 1 MiB
  heap-materializing provisional sink；P3a native input spool 不声称实现
  end-to-end 64 MiB compilation。P3b-b1 已用 DEBUG-only gate 在设备上证明低 token
  ASCII 的 exact boundary，但没有解除 production gate，也不声称 output 已达到
  128 MiB container/256 MiB uncompressed artifact contract.

完整 wire/state/teardown 说明见
[R2 Provisional Framed Project Transport](../architecture/PROJECT_TRANSPORT_R2_PROVISIONAL.md).

R2-S4 增加的边界:

- 成功的 internal host result 现在携带 exact `R2ProjectSnapshot` identity；
  production output staging 同时接收该 result 与原 `R2AdaptedProjectArchive`，
  identity 不一致或未绑定时拒绝，避免将项目 B 的合法结果写成项目 A 的
  request/project/input provenance。直接构造 seam 只在 DEBUG boundary tests 使用.
- packager 再次核对固定 Rhino effective options、diagnostic count/category/
  position、exact accepted roots、每源一 module/map、Source Map v3 shape/source
  resolution 与 entry module，不只依赖 Worker parser。TypeScript 原始
  `//# sourceMappingURL=foo.js.map` 会被有界改写成从 `modules/**` 指向
  `maps/**` 的相对路径，写入 manifest 的 size/SHA-256 以改写后的 artifact
  bytes 为准.
- provisional `compilation-manifest.json` 与 `diagnostics.json` 使用 strict UTF-8、
  deterministic JSON/ZIP inventory，携带 request/project/input/compiler/
  declarations/runtime provenance、profile/runtime schema revision、canonical
  effective-options fingerprint、每文件 size/SHA-256/source inventory。
  `EMITTED` 写 manifest + diagnostics + modules/maps；`DIAGNOSTICS` 只写
  manifest + diagnostics。两份 JSON schema 仍未冻结.
- 创建 staging file 前精确预检 frozen 8192 files、8 MiB ZIP metadata、
  128 MiB container、256 MiB total、16 MiB/JS、32 MiB/map、10000
  diagnostics、8 MiB diagnostics JSON 与 4 MiB manifest；当前 writer 使用
  deterministic STORED records，无 extra/data descriptor。S3 result 仍是
  1 MiB heap materialization，因此这只建立正确 commit 边界，不声称已能实际
  生成完整 128/256 MiB artifact.
- writer `fsync`/close 后只持有同一个 read-only inode；Android/Linux 立即
  unlink，Windows test fallback 保留至可重试 cleanup 完成。同一 held
  `RandomAccessFile` 通过新的 non-materializing `validateImmutable(..., OUTPUT)`
  streaming 重算 raw ZIP/CRC/size/ratio，再绑定 container SHA-256.
- output admission 在 API 30+ 通过 public `Os.fcntlInt(F_GETFL)`、API 24–29
  通过四 ABI JNI/libc `fcntl(F_GETFL)` 非写入地读取 open flags，拒绝 read-only
  与 `O_APPEND`（Linux 会把 append 语义施加到 `pwrite`）。该路径不依赖 hidden
  API 或 procfs。commit 使用 64 KiB buffer 与 fixed-offset `pread`/`pwrite`，
  最后精确 `ftruncate`、`fsync`、重验 private inode、`seek(0)` 并关闭
  service PFD 后才可发布 summary.
- 普通 copy/commit failure 必须先证明 caller output 已
  truncate-empty + fsync + seek-zero + close；cancel/timeout/internal failure
  使用同一 empty finalizer。若 FD 已关闭或任一步无法证明，返回
  `R2OutputCommitUnprovenException`；S5 coordinator 会保留 exact lease 并
  poison process-wide admission，绝不把非空 output 当作普通 failure.

完整 provisional output/ownership 说明见
[R2 Provisional Private Output Commit](../architecture/PROJECT_OUTPUT_R2_PROVISIONAL.md).

R2-S5 增加的边界：

- `R2ProcessAdmissionAuthority` 持有 process singleton
  `R2GlobalAdmissionArbiter`；每个 Service epoch 都取得同一实例，并由 prewarm
  internal probe、R1 DEBUG transport probe 与 R2 DEBUG coordinator 共享。任一
  时刻只有一个 internal/job lease；accepted job 绑定 calling UID、request ID、
  callback Binder identity 和 service 生成的 256-bit random job token.
- terminal tombstone 有界保留 UID/request/token，wrong owner 不可 cancel/ACK，
  正确 active/terminal tuple 保持幂等；重复 request ID 不会复活或重放 callback。
  slot 只在 coordinator 证明 cleanup 后释放；poison 则保留 exact active lease，
  同时拒绝后续 job 与 prewarm。active/tombstone/poison 跨同 PID Service
  recreation 保留；cleanup proof 失败后，同一 PID 的新 Service epoch 会立即
  fail-fast，不能 reopen admission.
- full pipeline 只在 `DEBUG && same UID` 下创建。`startCompilation` 返回 accepted
  ticket 后，caller 通过现有 `cancelCompilation` transaction 发送 private
  `ACKNOWLEDGE` command；ACK 才 arm callback 并调度 pipeline，避免 callback
  早于 synchronous start return。未 ACK job 最多保留 5 秒或至 request deadline。
  ACK 只有在 lock 内确认 reservation 尚未 stop 且严格早于 cutoff 时才可 arm；
  cutoff 后的 exact ACK 仍幂等成功，但只会选择 `UNACKNOWLEDGED`/`TIMED_OUT`
  cleanup，绝不重新 arm callback。该 seam 未新增 AIDL，也不是 production
  acknowledgement contract.
- admission 在不可逆点前完成 lease、非修改性的 `fsync`/
  `lseek(..., SEEK_CUR)` preflight、deadline/executor availability 与 death-link/
  callback-alive gate。成功的 `ftruncate(0)` 是最终不可逆点；随后才锚定 clock、
  安装 active record 并调度 timer。它之后不再同步 reject；timer/cleanup 调度异常
  与任何 death/stop race 都作为 accepted job cleanup/supervision 处理。
- coordinator 消费 service-owned input/output PFD，串接 S2 private input、
  S3 framed Worker/exact hard-stop、S4 exact-snapshot output staging 与 FD commit。
  absolute monotonic deadline 在每个边界重验；callback death、service destroy、
  cancel 和 timeout 都绑定同一 lease/host handle.
- ordinary cancel/timeout stop 不能只凭 Worker/session teardown 提前完成。host
  分别记录 exact `stopTeardownProven` 与 per-job
  `projectResultFinalizerInFlight`；只有 teardown 已证明且 transferred accumulator
  已 wipe/release，才完成 `completeStopped()` 并让 coordinator 继续 FD cleanup/
  terminal。两者无论谁先到，都由 exact pending identity fence 后再汇合.
- runtime-capability fingerprint 同时绑定 canonical domain、runtime capability
  schema revision 1、profile schema revision 1、profile revision 1 与完整固定
  Worker policy：`extensions=.ts`、`compilerApi=createIncrementalProgram`、
  `allowJs=false`、`checkJs=false`、`ignoreDeprecations=6.0`、
  `incremental=false`、`inlineSourceMap=false`、`inlineSources=false`、
  `module=CommonJS`、`moduleResolution=Node10`、`newLine=LF`、
  `noEmitOnError=true`、`noLib=false`、`outDir=/outputs`、`rootDir=/sources`、
  `skipLibCheck=false`、`sourceMap=true`、`strict=true`、`target=ES2018` 与
  `types=[]`；任一固定 option 改变都会改变 fingerprint.
- S4 committer 在 durable copy、`fsync`、private-inode 重验与 `seek(0)` 后调用
  coordinator terminal gate。gate 前 stop 获胜会触发 synced-empty rollback；
  gate 获胜后迟到 cancel/timeout 不再覆盖 artifact。output PFD close、private
  staging cleanup 和 terminal shape 全部成功后，才写 tombstone、释放 slot，并
  至多调用一次 callback.
- shutdown terminal intent 在 coordinator lock 内 first-wins。R1 close-claimed
  和 already-claimed cleanup、以及 R2 accepted cleanup 都留在原保留 executor，
  R1/R2 coordinator 各自使用一个 absolute 5 秒 proof 边界；closed 后抑制
  callback。proof 失败时 poison/fail-close 并抛出 cleanup-unproven，而不是
  `shutdownNow()` 可能仍持有 FD 的 executor.
- host protocol/result verifier 另共享一个 absolute 1 秒 termination-proof
  deadline；transferred result accumulator 在所有 exit 上无条件 wipe/release，
  result executor 只 graceful shutdown，且两条 executor 终止后才清 Handler
  callbacks。任一 host proof 缺失同样 fail-close。两个明确边界让
  `onDestroy` bounded fail-fast，但不表示整个 `onDestroy` 共享单一 5 秒
  wall-clock budget；恢复交由进程外 supervisor.
- input/output/staging cleanup、empty rollback、executor/FD ownership 任一无法
  证明时进入 `SUPERVISION_REQUIRED`：不发布普通 terminal、不释放 lease，且
  process-wide admission 返回 unavailable，等待进程外 supervision。S6a 提供
  DEBUG/same-UID live-epoch proof bridge；S6b 已接入 production-manifest
  post-death reconciliation owner；S6c 会把首次 process-wide poison 自动交给
  已完成双向注册的 app-private live owner；S6d 再证明 live owner hard-kill 后，
  同一 compiler epoch 可连接 successor registry，并在新 registration 未 ready
  的窗口保留 poison，待当前 generation 完成握手后重放；S6e 再直接重放 retired
  generation 的 teardown handlers，逐次证明已 ready successor 的 exact authority
  不变。owner 未 ready、identity/descriptor 歧义或 procfs `UNREADABLE` 时继续
  fail closed.

完整 provisional admission/owner/publication 说明见
[R2 Provisional Project Coordinator](../architecture/PROJECT_COORDINATOR_R2_PROVISIONAL.md).

R2-S6a/S6b/S6c/S6d/S6e/S6f/S6g/S6h/S6i 增加的边界：

- 位于 `:typescript_compiler` 外的 same-UID DEBUG supervisor 从 exact remote
  Binder 取得 `canonical kernel boot ID + UID + PID + procfs start-time +
  process name + 256-bit nonce` process epoch。它不对 caller-supplied PID 发
  signal，而是 `linkToDeath` 后把完整 tuple 回送给同一 Binder；只有已 poisoned
  的 compiler 精确验证 tuple，才先 pre-arm exact persisted Job 并 readback
  完整 work generation，再 durable-commit quarantine marker、post-check 同一 Job，
  最后安排 delayed `Process.killProcess(Process.myPid())`。pre-arm/readback、
  marker commit、post-check、command deadline 或 termination worker 任一步失败，
  都会先 exact-clear-or-absent marker；只有 marker 已确认不存在时才 best-effort
  retire 同 token pre-arm，无法清除 marker 时保留 system recovery owner 并拒绝自杀.
- procfs observation 固定为 `SAME`、`REUSED`、`ABSENT`、`UNREADABLE` 四态。
  boot ID 缺失/非法为 `UNREADABLE`，不同 boot 或 PID reuse 为 `REUSED`。S6a
  death proof 必须同时观察 exact Binder death 与 `ABSENT/REUSED`；
  `SAME`/`UNREADABLE` 不构成 proof.
- quarantine 保留历史 v1 pathname，但 payload 已为 v2 boot-scoped identity；旧
  v1 payload 仍可见但不可读，并 fail closed。marker 使用 `AtomicFile`、有界 exact
  shape 与 directory `fsync`；进程内 monitor 与独立 lock file 的跨进程 OS lock
  串行化 read/mark/clear。clear 在 lock 内重读 exact generation，并在删除前执行
  clear authorization callback，阻止 stale exact-clear/ABA 与 stop/clear TOCTOU.
- marker 存在时所有 build type 的抢先 successor Service 均不打开 admission/host，
  且只返回 null binding。S6a synchronous supervisor 仅在 old death proof 后
  exact-clear；inert successor 观察 clear 后 self-recycle，使 clear 前仍持有的
  `BIND_AUTO_CREATE` connection 跨进程恢复。隔离 Service 的后台 watcher 负责
  marker 轮询与 S6b/S6f recovery Job 调度；`onBind()` 只在 watcher 未激活时重试
  启动并立即返回 null，避免主线程等待跨进程 marker lock.
- supervisor 的同步 command 在专用 daemon worker 上执行，且每个 supervisor
  进程仅允许一个不可取消 transaction 在途。观察循环使用 1 s 至 30 s 的
  absolute deadline（默认 10 s）；request 另携带提前 500 ms 的服务端
  `elapsedRealtime` command deadline，compiler 在 durable mutation 前后与
  self-kill 前重验，迟到 transaction 不会再触发破坏性副作用。worker 启动失败、
  stale/healthy epoch command rejection、procfs `UNREADABLE`、超时或 marker
  无法 exact-clear 都 fail closed.
- S6b/S6f 在 production manifest 注册受 `BIND_JOB_SERVICE` 保护、运行于独立
  `:typescript_supervisor` 进程、`android:exported="true"` 且无 intent filter 的
  system-bound `JobService`，并声明 `RECEIVE_BOOT_COMPLETED`。固定 Job ID 使用
  persisted scheduling、最小 latency/deadline 与 exponential backoff；strict
  `PersistableBundle` 绑定 mode、256-bit work token、完整 compiler epoch，以及
  PREARM mode 的 absolute monotonic deadline。pre-arm 在 app-private Job lock 内
  schedule 新 token 并 exact readback；marker recovery 只复用 component、schema 与
  exact epoch 都成立的 pending work，否则以新 token 调度 fixed-ID recovery.
- Job 的 marker/procfs/telemetry I/O 全部在 daemon worker；`completionPosted`
  CAS 让每个 running Job 至多一次 post 到 main looper，只有当前且未 stop 的
  main runnable 可移除 running state 并调用 `jobFinished()`。固定 Job ID 的
  running/start/stop 以 persisted work token 区分 generation，不依赖
  `JobParameters` 引用相等性；同 token stop 与 marker-lock 内 clear authorization
  通过原子 phase 线性化，迟到的 different-token stop 不退休 replacement.
- S6f 的 PREARM Job 在 marker 尚未出现且 deadline 仍可用时，若 old epoch 为
  `SAME/UNREADABLE`，会记录 `PREARM_RETRY` 并在 worker 上等待 marker/death
  transition；若先得到 `ABSENT/REUSED`，会在 marker lock 下重新检查后才
  `NO_WORK`。marker 出现后只有 work identity 与 marker exact-match 才能 reconcile；
  corrupt/legacy work 或 different generation 不能清除 current marker.
- persisted reconcile 绑定 exact marker/work generation。S6f 已关闭旧的
  “marker 已 durable commit、但 persisted Job 尚未建立”producer crash window，
  因为 production 顺序现在是 exact pre-arm/readback → marker commit → exact
  post-check → delayed self-termination；但 marker 与 JobScheduler 仍不是原子事务，
  post-check 后的系统/外部取消或 scheduler state loss 仍可能留下无 owner marker.
- S6g 把 running Job generation 仲裁抽为同一个 token gate。generation A 已运行时，
  same fixed Job ID 的 different-token generation B 先退休 A 的 clear phase，再成为
  current；`STOP(A) → START(B)` 与 `START(B) → late STOP(A)` 都不会退休 B，后者明确
  判为 `STALE`。marker clear authorization 仍由原有 exact phase fence 决定，journal
  不参与 authority.
- S6g 的 `DEBUG`-only lifecycle journal 在 callback 上只采集 process sequence、
  `elapsedRealtimeNanos`、PID、event/decision 与两个 canonical token；procfs owner
  capture、SHA-256、strict ASCII parsing、跨进程 lock 与 `AtomicFile` 写入都在 daemon
  writer。内存 queue 最多 32 条、持久记录最多 64 条、文件最多 32 KiB；boot-scoped
  durable cutoff 使测试 clear 之前已经排队或 drain 的跨进程旧 event 不能迟到重建
  当前 trace。journal 仍是 non-authoritative/best-effort，只能提供 token-scoped
  positive observation，不能以 absence 或 count 推导 framework 全局线性化。
- S6h 不创建 replacement generation。唯一 generation `W` 在 owner `P1` 中到达
  `PREARM_RETRY` 后，测试先 exact commit marker 并复核 scheduler-tracked work 仍为
  `W`，再以 procfs full identity 精确 kill `P1`。为建立 deterministic fault window，
  `W` 使用 DEBUG/test-only 的 120 s PREARM monotonic command deadline；这不是
  production deadline/backoff 保证。旧 compiler 随后在 exact procfs 复核后被 kill；
  只有 monotonic kill boundary 之后、同一 token `W` 在不同 owner `P2` 中的
  `START_CALLBACK`，以及同一 `P2` 写下的 `RECONCILED(W)`，才构成这次正向证据。
- failure retry 只有 backoff 下界，没有 latest-runtime 保证。S6h 先给自然重投
  30 s；若未观察到 positive callback，测试会重新确认 marker 与 scheduler-tracked
  work 都精确属于 `W`，并确认当前恰有一个 component/extras 与 `W` 匹配的 fixed-ID
  Job，再请求
  `cmd jobscheduler run -f -u <current-user-id> <package> <job-id>`。只有 stdout
  精确等于 `Running job [FORCED]` 才记为命令已接受；回调仍独立按 `W` 的 token、
  owner identity 与 monotonic time 匹配。该 seam 不调用 app scheduler、不替换
  fixed ID/extras/token；命令与 `W` 并非原子绑定，故只证明 recheck 时 scheduler
  仍跟踪 exact `W`，且命令调用边界之后观察到 `W` 的 callback/reconcile，不证明
  shell 因果或 production autonomous retry 的时延上界。
- S6i 复用且不替换同一 test-only 120 s PREARM generation `W`。关闭唯一 external
  compiler binding 后，测试再次精确复核 `P1`、old compiler `C`、active FD、marker
  与 scheduler-tracked `W`。两个 kill worker 分别完成 `P1`/`C` exact-live validation
  后在 shared barrier 就绪，再由同一 latch 同时取得发出 kill request 的资格。
- latch release 后两个 worker 并发调用 same-UID test-side
  `Process.killProcess(exactPid)`；owner/compiler signal initiation 的先后不确定，测试
  只记录两者 initiation time、return time 与 initiation span。两次调用都返回后的
  `bothSignalsReturnedNanos` 才是 S6i qualifying `START_CALLBACK(W, P2)` 的 monotonic
  下界；signal return 不等于 death proof，S6i 也不声称 instruction-level simultaneous
  kill 或两个 process 的实际 death interval 必然重叠。
- boundary 后的正向链仍要求 `P1` 与 `C` 各自得到 exact disappearance proof、同一
  `W` 在不同 owner `P2` 中产生 qualifying START、同一 `P2` 以 W token/old nonce
  `RECONCILED`、marker/Job clear，以及 fresh compiler accepted + ACK + cancel/FD
  release。API 35 forced-eligibility 与 invocation-boundary 分类沿用 S6h 的非因果
  限定。
- S6c 在同一独立 `:typescript_supervisor` 进程新增
  `R2CompilerProcessSupervisorService`：`exported=false`、无 permission、无 intent
  filter、`directBootAware=false`。两条 typed AIDL 仅位于 app module，不进入
  `plugin-api`，也不修改 frozen public transaction/API/ZIP v1.
- compiler 注册时，registry 同时校验 Binder caller UID/PID、canonical compiler
  identity、与 owner self identity 相同的 boot ID、固定 compiler process name、
  remote Binder descriptor 与存活状态；owner 再以自身 self-captured
  boot/UID/PID/start-time/fixed process-name 反向 challenge。compiler 以 Binder
  caller UID/PID 与自身 boot ID 校验；256-bit token 只有 challenge 与 registry
  返回值一致后才从 pending 提升为 active。online handshake 不再跨 PID 读取
  non-debuggable sibling procfs；alive endpoint 不允许被新注册覆盖.
- poison 是 process-monotonic 状态；通知与注册重试每次面向当前 connection
  generation，旧 generation 不能吞掉新 owner 的投递。owner 复用同一个有界
  command/death-proof engine；compiler 只在 token、caller supervisor identity、
  exact自身 epoch、poison 与 deadline 全部成立后执行 exact Job pre-arm、durable
  mark、Job post-check 与 delayed exact self-termination，owner 从不获得任意 PID
  kill authority.
- production build 的 prewarm availability 在注册 ready 前 fail closed。DEBUG
  instrumentation 只能以 same-UID test key 启用同一 automatic handoff 路径；
  普通 frozen-v1 `startCompilation` 仍按 R0 拒绝，完整 P1 additive metadata
  request 是唯一 production-shaped preview 例外；四项 capability flags 仍为
  `false`.
- S6d 将每次 bind attempt 分配为独立
  `GenerationServiceConnection`，generation 在 `bindService()` 前产生；framework
  callback、registry Binder death 与 registration retry 只有在
  `activeConnection === source`、generation 和 Binder identity 同时匹配时才可
  退休或提交当前状态。旧 binding 的迟到 disconnect/null/binding-died callback
  因而不能 unbind 或清除 successor registry.
- DEBUG/same-UID registration-pause seam 只冻结新 registry 的 token handshake，
  不阻塞 successor owner process 与 Binder connection。pause 与握手重叠时，
  challenge callback 和最终 token promotion 都重新检查 pause；resume 后只向
  then-current registry 注册。握手通过的 owner PID/start-time telemetry 不暴露
  token，也不进入 public contract.
- S6e 的 DEBUG/same-UID one-shot seam 只能在 exact ready generation 上 `ARM`；
  该 generation 真正退休时才保留其 source connection 与 registry death recipient。
  不保存未被精确 arm 的历史 Binder，`RESET` 与成功 replay 都释放 test-only 引用，
  release 路径不会捕获 retired Binder。
- successor 完成新 handshake 且 generation 严格递增后，S6e 同步、重复调用 retired
  `onServiceDisconnected()`、API 26+ `onBindingDied()`、API 28+
  `onNullBinding()` 与旧 registry death recipient。每次 delivery 后都在 client
  lock 内复核 successor 的 active connection/generation、registry Binder/death
  record、registration token 与 owner identity/PID 未变；telemetry 只公开
  generation/count/preserved，不公开 token。
- deterministic device fault seam 保留 cleanup-unproven job 的 exact lease 与
  input/output FD owner，不发布普通 callback。instrumentation 证明 stale epoch
  不自杀、exact Binder self-kill、clear 前并发 bind 受 null-binding fence 阻断，
  clear 后不同 process epoch 恢复 accepted/cancelled job、Job owner 自动
  reconcile 注入的 `ABSENT` marker、live `SAME` epoch 保持 quarantine，以及
  stale exact-clear 无法删除当前 generation。S6d 另以 exact procfs identity
  hard-kill live owner，证明旧 registry Binder death、旧 owner
  `ABSENT/REUSED`、不同 successor owner 与新 registry connection；在
  registration 未 ready 时制造 cleanup-unproven，compiler/FD 仍保持且无 ordinary
  callback，解除 gate 后 pending poison 由 post-old current owner 重放，最终完成
  old compiler death、quarantine clear 与 fresh epoch accepted/ACK/cancel recovery。
  S6e 则在 successor ready 后执行上述 retired teardown handler 的 duplicate
  replay，继续证明同一 compiler epoch/FD/job 保持，再由该 successor 处理随后产生的
  poison 并完成相同 recovery 闭环。S6f 另在 exact termination path 中先观察
  marker-absent 的 pre-armed owner，再移除唯一 compiler client，证明无需 successor
  bind 即可由同一 work token 完成 old-epoch reconcile 与 fresh-epoch recovery。
  S6g 再让 generation A 先到达 `PREARM_RETRY`，随后由 production exact-termination
  path 以相同 fixed Job ID、不同 token 调度 generation B；有界窗口内按 token
  正向观察到 A/B 的 START 与 A 的 STOP callback，最终 exact owner 为 B。A 的迟到
  stop 不影响 B 由 pure gate policy 与 token-scoped positive trace 共同约束，而不是
  由 journal 中“没有更多记录”推导。S6h 则只创建 `W`：先精确证明 `W/P1`，
  hard-kill `P1` 与 old compiler，再以 post-kill `START(W, P2)`、`P2 != P1`、
  `RECONCILED(W, P2)`、marker/Job absent 和 fresh compiler recovery 封闭一次
  same-token owner-death/redelivery 闭环。S6i 复用同一闭环，但让两个已完成
  exact-live validation 的 kill worker 从 shared barrier 并发发出 `P1`/`C` kill
  request；只有晚于 `bothSignalsReturnedNanos` 的 `START(W, P2)` 才计入正向证据。

完整 S6a/S6b/S6c/S6d/S6e/S6f/S6g/S6h/S6i identity/death/quarantine 说明见
[R2 Provisional Compiler Process Supervisor](../architecture/PROCESS_SUPERVISOR_R2_PROVISIONAL.md).

S6c 已 provisionally 关闭 app-private production-build live-epoch registration/
authorization/termination control-plane 子项；S6d 又关闭单次 live owner
hard-kill、successor rebind 和 registration-gap poison replay 子项；S6e 关闭的是
DEBUG 同步重放 retired teardown handler 的 generation-fence 子项；S6f 关闭的是
marker commit 前建立并 post-check exact Job ownership 的 producer crash window，
S6g 又收窄一次 running same-ID/different-token handoff 与该窗口内
`onStopJob()` 的 token-fence 子项；S6h 再收窄一次 same-boot、same-token、running
JobService owner hard-death/redelivery 子项；S6i 又收窄一次 retained exact W 下、
shared validation barrier 后并发发出 owner/compiler exact kill request 的子项，但
这不等于完整 production supervisor 已关闭。
stale `onServiceConnected()`、register/notify/token transaction 与真实并发 framework
delivery、system/LMK/OOM/force-stop 触发的 owner/compiler dual death、instruction-level
simultaneous signal/death、实际 death interval overlap、显式穷举两种 signal initiation
order、hung owner transaction、连续或重叠多次 replacement、当前设备矩阵之外的
callback ordering、handoff 期间 JobService 重复崩溃或不同 worker phase 的 process
death/restart、尚未观察的 completion/`onStopJob()` race、非权威 telemetry generation
的非完全线性化、真实 reboot/force-stop/system_server persistence、post-check 后外部
取消、跨 reboot monotonic deadline 与 procfs `UNREADABLE` 的组合、连续
crash/backoff 上限及完整 device fault matrix、以及不借助 test dispatch 的 autonomous
retry 时延仍开放。production
ACK/ticket/callback delivery 与 external-caller ownership 仍是独立 blocker。
P3b-b1 已关闭 DEBUG low-token ASCII 的 exact 64 MiB direct/Binder capacity
基础设施与 API 24/28/35 部分 device matrix；P3b-b2a 又关闭 same-UID DEBUG
synthetic 四阶段入口 hard-stop 与一次 active exact-64-MiB renderer
loss/recovery infrastructure；P3b-b2b 再关闭 API 24/35 same-UID DEBUG 三轮
exact64 success 的 compiler-service PSS/FD/transient envelope、零 spin 自然
ingress cancel 的 cursor/Worker/renderer envelope，以及真实 cold successor
orphan cleanup；P3b-b2d 又关闭 non-shipping external-UID
debug/release-equivalent exact-64 四边界；P3b-b2e 再关闭 API 24/35 ordinary
same-UID debug exact-64 partial ingress 自然 timeout 与 Binder end-to-end
natural deadline/final cleanup；P3b-b2f 又关闭 API 24/35 external-UID
normal/evidence Debug/Release 的 production-fence 与 exact-shaped STORED/
DEFLATED `+1` pre-callback cleanup receipt、5-sample convergence、same-Binder
successor 矩阵；P3b-b2g 再关闭 API 24/35 ordinary same-UID Debug 零 spin
parse/semantic/emit 自然 cancellation、exact progress/termination receipt、
cursor/Worker/successor/host/spool cleanup 窄矩阵；P3b-b2h 又关闭同一
ordinary same-UID Debug direct-host 边界内的 natural semantic request-start
absolute-deadline timeout，一台设备一个固定 timeout fault，均来自三次完整成功
baseline 的严格交集；P3b-b2i 再以两次不计时的完整成功 precondition、三次
完整成功 baseline 与一个 fault，关闭同一 ordinary direct-host 边界内的
natural parse request-start absolute-deadline timeout；P3b-b2j 再以独立
8-root / 116-file workload、相同的两次 precondition / 三次 baseline / 单 fault
规则留下旧 completion boundary 的 ordinary direct-host natural emit
request-start absolute-deadline timeout 历史证据；P3b-b2k 再以 standalone
distinct-UID public Binder、2+3+1 strict
calibration、absolute-nanos coordinator/host seam、pre-callback phase/cleanup receipts
与 small successor，关闭 API 24/35 evidence Debug/release-equivalent 的 natural
emit timeout。其后的无编号 follow-up 再以 exact 512-file semantic workload、相同
2+3+1 single-admission 规则与 callback 前 phase/cleanup receipts，关闭 API 24
Chrome-WebView 119 与 API 35 Google WebView 130 上 evidence Debug/
release-equivalent 的 external Binder natural semantic timeout；第二个无编号
follow-up 又以 32-root / 371-file low-stack DAG parse workload 和相同 2+3+1 规则，
关闭同一四行边界的 external Binder natural parse timeout；第三个无编号 follow-up
再以 bounded public phase/work progress 关闭 API 24/35 normal Release 的 natural
parse timeout。完整 P3b-b 仍需 normal/shipping Release semantic/emit timeout 与其余
fault-path cleanup、Unicode/high-token/adversarial 和真实 LMK/OOM 证据，才能
决定是否解除 production 围栏。其后还需
真正覆盖 128 MiB container/256 MiB uncompressed 的 file-backed/compressed
output writer，以及
callback-death/service-destroy/poison device fault matrix。这些只是开放
capabilities 的必要条件；四项 flags 仍须等全部 R2 exit/schema/device gates
完成后才可开放。request/output manifest 与 diagnostics schema 仍只在 R2 退出时
冻结.

本地无设备聚合门禁:

```powershell
.\gradlew.bat verifyR2PublicPreviewFixture
```

上面的 P1/P2/P3a/P3b-a/P3b-b1/P3b-b2a/P3b-b2b/P3b-b2c/P3b-b2d/P3b-b2e/P3b-b2f/P3b-b2g/P3b-b2h/P3b-b2i/P3b-b2j/P3b-b2k
及无编号 external semantic/parse、normal/shipping Release parse follow-up aggregate
包含下列可单独诊断的门禁：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:verifyR2ProjectCompilerCore
.\gradlew.bat :app:verifyR2P3bB2bResourceSourcePolicy
.\gradlew.bat :app:verifyR2P3bB2bAndroidTestApk
.\gradlew.bat :app:verifyR2P3bB2eNaturalIngressTimeoutSourcePolicy
.\gradlew.bat :app:verifyR2P3bB2eNaturalIngressTimeoutAndroidTestApk
.\gradlew.bat :r2-public-preview:verifyR2P3bB2fExternalPlusOneCleanupSourcePolicy
.\gradlew.bat :r2-public-preview:verifyR2P3bB2fExternalPlusOneCleanupApks
.\gradlew.bat :app:verifyR2P3bB2gNaturalPhaseCancellationSourcePolicy
.\gradlew.bat :app:verifyR2P3bB2gNaturalPhaseCancellationAndroidTestApk
.\gradlew.bat :app:verifyR2P3bB2hNaturalPhaseTimeoutSourcePolicy
.\gradlew.bat :app:verifyR2P3bB2hNaturalPhaseTimeoutAndroidTestApk
.\gradlew.bat :app:verifyR2P3bB2iNaturalParseTimeoutSourcePolicy
.\gradlew.bat :app:verifyR2P3bB2iNaturalParseTimeoutAndroidTestApk
.\gradlew.bat :app:verifyR2P3bB2jNaturalEmitTimeoutSourcePolicy
.\gradlew.bat :app:verifyR2P3bB2jNaturalEmitTimeoutAndroidTestApk
.\gradlew.bat :r2-public-preview:verifyR2P3bB2kExternalNaturalEmitTimeoutSourcePolicy
.\gradlew.bat :r2-public-preview:verifyR2P3bB2kExternalNaturalEmitTimeoutApks
.\gradlew.bat :r2-public-preview:verifyR2ExternalNaturalSemanticTimeoutSourcePolicy
.\gradlew.bat :r2-public-preview:verifyR2ExternalNaturalSemanticTimeoutApks
.\gradlew.bat :r2-public-preview:verifyR2ExternalNaturalParseTimeoutSourcePolicy
.\gradlew.bat :r2-public-preview:verifyR2ExternalNaturalParseTimeoutApks
.\gradlew.bat :r2-public-preview:verifyR2ShippingReleaseNaturalParseTimeoutSourcePolicy
.\gradlew.bat :r2-public-preview:verifyR2ShippingReleaseNaturalParseTimeoutApks
.\gradlew.bat verifyR1ProtocolCompatibility
.\gradlew.bat :plugin-api:typescript-api:verifyTypeScriptApiV1Freeze
.\gradlew.bat :app:verifyDebugTypeScriptDistributionApk
.\gradlew.bat :app:verifyReleaseCompilerHostApk
.\gradlew.bat :app:verifyOptionalPluginIndependence
.\gradlew.bat :app:lintDebug
```

`verifyR2ProjectCompilerCore` 包含 JVM tests、Android test APK 编译、TypeScript
distribution trust-root 复核、compiler-host source policy 和 Node
`v24.13.0`/TypeScript `6.0.3`
framed desktop golden（从 generated、lock-verified distribution 装载全部 108
个 standard libraries）；P3b-b2a golden 还要求四阶段 exact
started/completed sequence 与 production `0 + none` control。P3b-b2b source/APK
gate 再锁定 exact-identity 5-sample PSS、完整 FD target multiset、三轮
exact64/cursor/Worker successor、startup scavenger strict namespace 与
fail-closed 顺序；P3b-b2e source/APK gate 又锁定固定 5/10 秒 deadline、
direct partial ingress/cursor/Worker/session/host cleanup、Binder public ACK/
timeout/zero-output/exactly-once/final transient cleanup 与 successor；P3b-b2f
source/APK gate 再锁定 exact TEST_ONLY external caller、scope-free 三类 `+1`、
post-return/pre-ACK ownership、receipt-before-callback/read-after-callback、
five-zero convergence、same-Binder/process epoch、normal release cleanup-marker
exclusion 与 ordinary successor；P3b-b2g source/APK gate 又锁定 deterministic
300-file workload、真实两级 phase progress、page/native termination receipt、
三阶段 cancellation、cursor/Worker/successor/host/spool cleanup，并同时检查
成对 debug app/androidTest APK 的 runtime/fixture DEX marker；P3b-b2h
source/APK gate 再锁定 512-file timeout workload、三次真实 baseline 的
semantic strict intersection、request-start absolute nanos、interrupt/completion
race、固定 midpoint 与单次 fault，并禁止测试侧 stop/retry/spin/expanded
admission；P3b-b2i source/APK gate 又锁定 8-root/117-file parse timeout
workload、两次不计时的完整成功 precondition、三次真实 baseline 的 parse
strict intersection、固定 midpoint 与单次 fault，并同时锁定 418-frame/path/
output authority、禁止 stop/retry/spin/expanded admission 以及成对 debug
app/androidTest APK 的 runtime/fixture DEX markers；P3b-b2j source/APK gate
最后锁定独立的 TypeScript 6.0.3 / ES2018 8-root/116-file emit workload、
2-light/6-heavy 与 18×depth-110 optional-chain authority、两次不计时的成功
precondition、三次真实 baseline 的 emit strict intersection、固定 midpoint
与单次 fault，同时锁定 400-frame/input/output/hash/1-MiB-headroom authority，
禁止 stop/retry/spin/expanded admission/synthetic calibration/phase-relative
deadline，并要求成对 debug app/androidTest APK 不含废弃 success-probe marker。
P3b-b2k source/APK gate 接着锁定 same-signer/distinct-UID TEST_ONLY caller、
256-root/364-file external workload、64-light/192-heavy、depth-61 optional chain +
4×90 erased-assertion authority、2 precondition + 3 baseline + 1 fault、unclamped
public-minimum midpoint、single admission、absolute nanos seam、final-ACK emit
completion、phase/cleanup receipt-before-callback ordering、512-output raw Worker 与
packaged manifest 双重 authority 及 same-Binder successor；同时禁止
stop/retry/spin/expanded/synthetic/phase-relative seam，并要求 receipt markers
存在于 evidence Debug 与经 R8 的 Release-equivalent APK、从 normal Release DEX
排除。无编号 semantic follow-up 的 source/APK gate 又锁定 404 caller + 108
declaration 的 exact 512-file fence、2+3+1 single-admission semantic strict
intersection、unclamped public-minimum midpoint、semantic sequence 2 `64/256`、
512-output raw/packaged authority、512-cursor release 与 same-Binder successor；同样
禁止 stop/retry/spin/expanded/synthetic/phase-relative seam，要求 private receipt
markers 只存在于 evidence Debug/R8 Release-equivalent APK、从 normal Release DEX
排除。无编号 normal/shipping Release parse follow-up 的 source/APK gate 最后锁定
revision-1 public progress exact keys、14-event bound、host identity/order validation、
request-start nanos publication、fixture strict parser、normal Release flag/private-prefix
attestation、2+3+1 single-fault matrix、5-event parse prefix、8-event successor 与
normal Release non-empty R8 mapping；同时禁止 path/text/diagnostic payload、
stop/retry/spin/expanded/private receipt。
所有 APK gate 都
明确不运行设备。P3b-a golden 另在独立
`--max-old-space-size=512 --expose-gc` child 中执行单次 64 MiB caller + 8 MiB
trusted boundary、forced-GC retained plateau 和 small successor，并覆盖
capacity/UTF-8/path/directory/token/lazy SourceFile/incremental diagnostic
负向；它不代表 Android renderer/PSS。精确的 3,784,758 bytes 与 aggregate fingerprint 由
distribution trust-root gate 复核，并由 APK loader instrumentation 在设备上
独立重验；Node golden 本身不把 generated tree 当成已签名 APK。
`verifyR1ProtocolCompatibility` 继续验证冻结 R1 AIDL/API/ZIP v1。S1-S3 Android
设备矩阵直接驱动 internal host，S4 另直接驱动 private output/FD committer；
S5 新增 DEBUG same-UID remote Binder full-pipeline instrumentation，S6a 又在
  同一 private lifecycle class 中加入 exact process-death/quarantine recovery，
  S6b 再加入 production Job owner、marker-lock ABA 与 live-epoch fail-closed
  覆盖，S6c 增加 automatic live-owner handoff 与新 epoch recovery，S6d 增加
  live owner hard-kill、successor registry connection 与 registration-gap replay，
  S6e 增加 successor-ready 后的 retired teardown callback duplicate replay fence，
  S6f 增加 exact persisted-Job pre-arm/post-check 与无 successor bind 的
  system-owned recovery，S6g 增加 pure token-generation gate、boot-scoped bounded
  callback journal 与一次 running fixed-ID generation handoff，S6h 增加一次
  retained exact-W owner-death redelivery，S6i 再增加 shared validation barrier 后的
  owner/compiler concurrent exact-kill-request proof。
  release/static
门禁分别校验 system JobService（`exported=true` + `BIND_JOB_SERVICE`）和唯一
app-private live Service（`exported=false` + 无 permission）的 process/direct-boot/
intent-filter 形状，并继续校验 `RECEIVE_BOOT_COMPLETED`。
这些都不是 public Binder capability，不能据此开放 flags.

2026-07-27 R2-S1 historical Android test artifact（不包含后续 S2 代码）:

- app APK SHA-256:
  `d1430e6bb393fb67d1718ba2af1fe5c04c5017926e6e94147b6214e70d1efb46`
- androidTest APK SHA-256:
  `2cebdd2c9ee5e020164f4ab5a6b93e166959c321c8f758f2c6929a35d80c28ee`

设备命令:

```powershell
adb -s <serial> shell am instrument -w -r -e class io.github.supermonster003.autojs6.plugin.typescript.R2ProjectCompilerInstrumentationTest io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner
```

| 设备 | WebView provider | 最终结果 |
| --- | --- | --- |
| API 24 AVD | Chrome `119.0.6045.194` | 4 tests / 0 failures; 3 pass + renderer-handle assumption |
| API 25 AVD | Chrome `119.0.6045.194` | 4 tests / 0 failures; 3 pass + renderer-handle assumption |
| API 31 真机 | Android System WebView `145.0.7632.120` | 4 tests / 0 failures; renderer death 实际执行 |
| API 33 真机 | Android System WebView `150.0.7871.124`（离线前记录） | 最终 artifact 复跑待设备重新上线 |

API 24/25 provider 宣告 renderer-termination feature, 但单进程配置未暴露实际
renderer handle, 因而该一项使用 JUnit capability assumption；golden、diagnostics、
timeout/recovery 和 busy/close 均实际执行通过. API 31 实际消费 termination hook,
验证 project renderer death 不重试及下一 session 恢复.

2026-07-27 R2-S2 historical private staging instrumentation（不包含后续 S3
transport/lifecycle 代码）:

- app APK SHA-256:
  `4e451f7d4f008aa1228a98d6d455682c528a647df68f4241274b6e15ea870fb1`
- androidTest APK SHA-256:
  `07d98568304f6bd4ecba5f8e50c0a1210dd9e6e874bb881d3b32513bede466a5`

```powershell
.\gradlew.bat --% :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.supermonster003.autojs6.plugin.typescript.R2PrivateInputStagingInstrumentationTest
.\gradlew.bat --% :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.supermonster003.autojs6.plugin.typescript.R2TrustedDeclarationsInstrumentationTest
```

| 设备 | 系统 | 结果 |
| --- | --- | --- |
| `emulator-5556` / R1_API24_Play | Android 7.0 / API 24 | staging 4/4 + declarations 1/1 |
| `emulator-5554` / R1_API25_Play | Android 7.1.1 / API 25 | staging 4/4 + declarations 1/1 |
| `QV710AF65F` / XQ-AT72 | Android 12 / API 31 | staging 4/4 + declarations 1/1 |

共 12/12 execution，覆盖 PFD 从 offset 0 capture 且不移动 caller shared offset、
并发 caller `lseek` churn、read-only unlinked inode 与 `/proc/self/fd` cleanup、
pipe rejection/close 以及 regular-PFD quota cleanup. 这是 S2 input-staging
证据；尚不是 public Binder compilation、framed Worker transport 或 output commit
证据. 同三台设备另各以 1/1 通过
`R2TrustedDeclarationsInstrumentationTest`, 运行时重新装载并核对 packaged 108
files、3,784,758 bytes、aggregate fingerprint 与 `/declarations/lib.d.ts`.

2026-07-27 R2-S3 framed transport/keyed hard-stop instrumentation（当前
shared tree）:

- app APK SHA-256:
  `4de7552beab79300d56d0ae77da65ca01088774438df189ebaab238f896238c9`
- androidTest APK SHA-256:
  `b5a55ea19fcadd1fc42639813ddee0a244c7475f61ac8fd560ee1ce1c4020483`

同一个 8-test class 使用上文 `adb ... R2ProjectCompilerInstrumentationTest`
命令执行：

| 设备 | WebView provider | 最终结果 |
| --- | --- | --- |
| API 24 AVD / `emulator-5556` | Chrome `119.0.6045.194` | `OK (8 tests)`；7 actual + renderer-handle assumption |
| API 25 AVD / `emulator-5554` | Chrome `119.0.6045.194` | `OK (8 tests)`；7 actual + renderer-handle assumption |
| API 31 真机 / `QV710AF65F` | Android System WebView `145.0.7632.120` | `OK (8 tests)`；8 actual，renderer death 实际执行 |

该 artifact 覆盖完整 packaged standard-library snapshot 穿越旧 512 KiB 单消息
门禁、golden/semantic diagnostics、exact active cancel、registration 前 stop、
active timeout、busy/close 与 recovery。API 24/25 provider 未暴露实际 renderer
handle，因此仅该一项使用 JUnit capability assumption；API 31 实际证明 renderer
death terminal 且 successor 使用新 session。本地 `testDebugUnitTest` 同时为
90 tests / 0 failures / 0 errors / 0 skipped.

2026-07-27 R2-S4 private output staging/FD commit instrumentation（当前
shared tree）:

- app APK SHA-256:
  `be5af0a70536337650187d51fafcaa609eb7709c17242c7fa30de77614f9f7d7`
- androidTest APK SHA-256:
  `9710571e496be3e246c913da3a9cf6cf18f5ffd07e85297ac5d6c2df01072100`

```powershell
adb -s <serial> shell am instrument -w -r -e class io.github.supermonster003.autojs6.plugin.typescript.R2OutputFdCommitterInstrumentationTest io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner
```

| 设备 | 系统 | output FD 结果 | project/lifecycle 回归 |
| --- | --- | --- | --- |
| `emulator-5556` / R1_API24_Play | Android 7.0 / API 24 | `OK (5 tests)` | `OK (8 tests)`；7 actual + renderer-handle assumption |
| `emulator-5554` / R1_API25_Play | Android 7.1.1 / API 25 | `OK (5 tests)` | `OK (8 tests)`；7 actual + renderer-handle assumption |
| `QV710AF65F` / XQ-AT72 | Android 12 / API 31 | `OK (5 tests)` | `OK (8 tests)`；8 actual |

每台设备的 5-test suite 实际覆盖 validated unlinked private inode 的
fixed-offset copy、精确 length/fsync/seek/close、partial-copy empty rollback、
cancel/failure empty finalizer、`O_APPEND` pre-admission reject，以及在 final seek
前故意关闭 FD 时保留非空 artifact 并返回 `Unproven`（未来 coordinator 必须
  poison admission，不能发布普通 failure）。当时替换 API-30-only `fcntlInt`
  后，API 24/25/31 debuggable target 均通过旧 procfs flags 路径；P3b-b2c
  minified API 24 后续证明该路径不适用于 non-debuggable production process，
  当前实现已由 API 30+ public `fcntlInt`、API 24–29 JNI/libc `fcntl` 取代。本地
`testDebugUnitTest` 为 99 tests / 0 failures / 0 errors / 0 skipped；R2 core、
R1 freeze compatibility、debug APK distribution verification 与 lint 聚合门禁
全部通过.

2026-07-28 R2-S5 closure evidence（S5 同源 source/artifact state）:

- JVM：112 tests / 0 failures / 0 errors / 0 skipped；
- R2 desktop framed golden：TypeScript `6.0.3` 通过；
- `:app:testDebugUnitTest`、`:app:verifyR2ProjectCompilerCore`、
  `verifyR1ProtocolCompatibility`、
  `:plugin-api:typescript-api:verifyTypeScriptApiV1Freeze`、
  `:app:verifyDebugTypeScriptDistributionApk` 与 `:app:lintDebug` 全部通过；
- app APK SHA-256：
  `907A86C0CB48E225DDE91E25D6D7BC5072A2E587D50EAB2A0008E1D8CBE1BF16`；
- androidTest APK SHA-256：
  `522D8583B9DA594A20B46054178E7CFF3906A6B38C804632770CEF18DEDF4FA2`。

同一最终 app/test APK 组合的设备聚合结果：

| 设备 | 最终结果 | 32-test 分项 |
| --- | --- | --- |
| `emulator-5556` / Android 7.0 / API 24 | `OK (32 tests)` | R1 accepted lifecycle 9 + R1 service destruction 1 + R2 Binder 6 + Output FD 6 + Project 10；renderer-exit assumption |
| `emulator-5554` / Android 7.1.1 / API 25 | `OK (32 tests)` | R1 accepted lifecycle 9 + R1 service destruction 1 + R2 Binder 6 + Output FD 6 + Project 10；renderer-exit assumption |
| `QV710AF65F` / Android 12 / API 31 | `OK (32 tests)` | R1 accepted lifecycle 9 + R1 service destruction 1 + R2 Binder 6 + Output FD 6 + Project 10；renderer-exit actual |

R1 accepted lifecycle 9-test suite 覆盖 service close 的
close-claimed/already-claimed cleanup 与 callback suppression；独立的 R1 service
destruction test 验证 last unbind 释放 mature host，随后两个 bind 均可重新
probe。R2 Binder 6-test suite 新增 late exact ACK 幂等但永不 re-arm，并继续覆盖
irreversible truncate 前的 non-destructive reject；Output FD 6-test suite 包含
terminal-gate stop 在 descriptor close 前赢得竞争时的完整 rollback。

Project 10-test suite 包含两条 deterministic finalizer regression：

- in-flight close：transferred accumulator 进入 500 ms test window 后关闭 host，
  close 等待 finalizer、发布完整 cleanup proof，并由 replacement host 恢复；
- gated cancel：finalizer 持有 accumulator 时先取得 exact Worker teardown proof，
  future 仍不得完成；release 后才发布 cancel terminal，随后同一个 host 的 recovery
  compile 成功。

API 24/25 仅 renderer-exit 使用 capability assumption，API 31 实际执行该路径。
`git diff --check` 无 whitespace error，仅有 compiler host/worker 两个已知
LF → CRLF warning。在该 S5 artifact 当时的基线中，冻结 AIDL、public contract、
ZIP v1、Android manifest 与 plugin metadata 相对 `HEAD` 均无 diff。
`compilerAvailable`、
`projectCompilation`、`semanticDiagnostics`、`sourceMapV3` 仍全部为 `false`。
这些证据只封闭 S5 internal provisional implementation，不表示 public R2 已完成.

2026-07-28 R2-S6a/S6b internal provisional proof evidence（历史同源
app/test artifact，不包含后续 S6c）：

当前 JVM 为 120 tests / 0 failures / 0 errors / 0 skipped。

```text
app APK  SHA-256 BD4E53CCE7E007A276901E6F0F60B932AE77EC6E8382E8EDC0DBD77ADE985607
test APK SHA-256 59D4738172D21087825CF5A4D4AE2F1911903A3BC0FA5A273B18A9B424C2D9C6
```

```powershell
adb -s <serial> shell am instrument -w -r -e class io.github.supermonster003.autojs6.plugin.typescript.R1AcceptedLifecycleInstrumentationTest io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner
```

| 设备 | 系统 | lifecycle 结果 |
| --- | --- | --- |
| `emulator-5556` | Android 7.0 / API 24 | `OK (9) + OK (4) + OK (1) = 14/14` |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (9) + OK (4) + OK (1) = 14/14` |
| `QV710AF65F` | Android 12 / API 31 | 完整 class：`OK (14 tests)` |

API 24/25 将同一 lifecycle class 串行拆为稳定的 `9 + 4 + 1` 运行，以绕开旧
emulator UiAutomation/`run-as` 与 IDEA 长调用通道的不稳定；每段 clean rerun
均为 0 failures，不改变 14 个 test method 的覆盖。API 31 直接运行完整 class。

S6a 的第 10 项
`cleanupUnprovenRequiresOldProcessDeathBeforeNewProcessAdmission` 覆盖：

- cleanup-unproven 后保留 exact active lease、input/output FD 与 owner record，
  不发布 ordinary callback，后续 prewarm fail closed；
- stale epoch command 不杀 old process；healthy successor 同样拒绝 termination；
- exact remote Binder 自杀命令先 durable-mark quarantine，再由 compiler
  self-kill；
- exact Binder death 与 procfs `ABSENT/REUSED` 的双重 old-epoch proof；
- death proof 后故意暂停 marker clear，并发 bind 只能 null-bind，不能泄漏
  successor admission；
- exact-clear 后保持原 `BIND_AUTO_CREATE` connection，inert successor self-recycle
  后由 AMS 跨进程自动重连；新 process identity/nonce 不同，accepted job 可继续
  ACK、cancel，且 terminal exactly once、FD released、output empty。

S6a 的第 11 项
`persistedQuarantineReconcilesAfterSupervisorRestartAndRecoversHeldBinding`
覆盖 durable marker restart primitive：重新读取 exact old identity，只在 procfs
明确为 `ABSENT` 时 exact-clear，并验证原 held null binding 自动恢复。

S6b 新增第 12–14 项：

- `persistedQuarantineIsAutomaticallyReconciledBySystemJobOwner`：successor bind
  提交 system Job 后，独立 `:typescript_supervisor` JobService 自动 reconcile
  注入的 `ABSENT` marker，并由 held binding 恢复；
- `systemJobOwnerNeverClearsAStillLiveCompilerEpoch`：live `SAME` epoch 只记录
  retry，marker 与 compiler process 均保持；
- `staleExactClearCannotRemoveCurrentMarkerGeneration`：在 marker lock 内替换
  generation 后，旧 exact-clear 被拒绝，证明 ABA fence。

同一 artifact 还通过 R1 service destruction 1、R2 Binder 6、Output FD 6 与
Project 10，形成每台三段均通过、合计 37/37 的 aggregate：

| 设备 | 最终结果 | 37-test 分项 |
| --- | --- | --- |
| `emulator-5556` / Android 7.0 / API 24 | `14/14 + OK (13) + OK (10) = 37/37` | lifecycle 14 + service destruction 1 + Binder 6 + Output FD 6 + Project 10；renderer-exit assumption |
| `emulator-5554` / Android 7.1.1 / API 25 | `14/14 + OK (13) + OK (10) = 37/37` | lifecycle 14 + service destruction 1 + Binder 6 + Output FD 6 + Project 10；renderer-exit assumption |
| `QV710AF65F` / Android 12 / API 31 | `OK (14) + OK (13) + OK (10) = 37/37` | lifecycle 14 + service destruction 1 + Binder 6 + Output FD 6 + Project 10；renderer-exit actual |

公开 AIDL、public contract、ZIP v1 与 capability advertisement 均未修改；
`compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、
`sourceMapV3` 仍全部为 `false`。Android manifest 已由 S6b 有意新增
`RECEIVE_BOOT_COMPLETED`，以及受 `BIND_JOB_SERVICE` 保护、无 intent filter、
运行于 `:typescript_supervisor` 的 JobService；source 与 merged debug/release
manifest 均由静态门禁验证。这组三设备 37-test aggregate 只证明 S6a
live-termination 与 S6b post-death reconciliation provisional slices；在该历史
artifact 当时它**不关闭** production live-epoch supervisor blocker，也不表示
public R2 已完成。

2026-07-28 R2-S6c app-private live-epoch owner evidence（历史同源 artifact）：

```text
app APK  SHA-256 61B6CCB88040A0E562B356CDA4137DC133C018D7A4D15F7255D101EB8666B7A5
test APK SHA-256 13F5C46BD496891CDA2E152702C3AA574D987C50F0F8C234F8276D73CA936E63
```

JVM report 为 125 tests / 0 failures / 0 errors / 0 skipped。本地聚合门禁
`:app:testDebugUnitTest`、`:app:verifyR2ProjectCompilerCore`、
`:app:verifyReleaseCompilerHostApk`、`:app:verifyOptionalPluginIndependence`、
`:app:lintDebug`、`verifyR1ProtocolCompatibility` 与
`:plugin-api:typescript-api:verifyTypeScriptApiV1Freeze` 全部通过；desktop framed
golden 继续由 TypeScript `6.0.3` 通过。

新增
`cleanupUnprovenIsAutomaticallyRecoveredByProductionSupervisorOwner`
不调用成功路径上的手工 supervisor：它等待 private registration ready，制造
cleanup-unproven、ACK/cancel，然后验证无 ordinary callback、old Binder death、
old FD/process 消失、procfs `ABSENT/REUSED`、quarantine exact-clear，最后由不同
epoch 完成 accepted + ACK + cancel、空 output 与 FD release。相同单方法在五个
Android API 层级均为 `OK (1 test)`：

| 设备 | 系统 | S6c 单方法 |
| --- | --- | --- |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` |
| `QV710AF65F` | Android 12 / API 31 | `OK (1 test)` |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` |

API 31 还以同一 artifact 一次运行完整
`R1AcceptedLifecycleInstrumentationTest`，结果为 `OK (15 tests)`。API 24/25/28/35
在本次 S6c artifact 上只补跑上述新增单方法；不得把历史 S6a/S6b 37-test aggregate
追溯改写为本次五设备完整 aggregate。

这些证据 provisionally 关闭 app-private live registration/authorization/termination
slice，并证明 public API/capability freeze 与 optional-plugin independence 未回归。
它们尚未覆盖真实 release APK 的 on-device poison、owner kill/rebind、reboot、
force-stop、同时崩溃、连续 backoff/crash-loop、marker corruption 或
`UNREADABLE` 组合，因此完整 production supervisor 与 public R2 仍未关闭。

2026-07-28 R2-S6d owner restart/rebind evidence（历史同源 artifact）：

```text
app APK  SHA-256 6AB3E1003FB317D3E2BA5D216B33B0BD3088972BFFE439C01537CD297AB4B2EF
test APK SHA-256 84D8038C257386B0ACDABA2EE761D23AD7E7F3BF9857DB33415C9C8691905C7C
```

JVM report 仍为 125 tests / 0 failures / 0 errors / 0 skipped；本地 R2 core、
release APK/manifest、optional-plugin independence、lint、R1 compatibility 与
TypeScript API freeze 聚合门禁全部通过，desktop framed golden 继续由
TypeScript `6.0.3` 通过。

新增
`poisonDuringProductionSupervisorRestartIsReplayedAfterRebind` 先从已完成双向
handshake 的 telemetry 取得 registered owner PID/start-time，并以 boot ID、UID、
完整进程名和两次 procfs stat 复核 exact identity。测试临时观察 registry Binder
并在 hard-kill 前解除自身 binding，随后证明：

1. old registry Binder death，old owner procfs identity 明确
   `ABSENT/REUSED`；
2. 不同的 successor owner 稳定出现，同一 compiler epoch/nonce 保持存活；
3. successor registry 已连接，但 DEBUG registration gate 使 token/registered
   owner identity 均未 ready；
4. 此 registration gap 中 cleanup-unproven 只设置 process-monotonic poison，
   不发 ordinary callback，compiler Binder 与未证明可释放的 input/output FD
   继续由 old compiler epoch 持有；
5. gate 解除后，then-current post-old owner 完成新握手并自动重放 pending poison，
   继而证明 old compiler Binder/FD/process 消失、procfs `ABSENT/REUSED`、
   quarantine exact-clear；
6. fresh compiler epoch 重新 accepted，并完成 ACK + cancel、空 output 与 FD
   release。

同一最终 artifact 的新增单方法在五个 Android API 层级均通过：

| 设备 | 系统 | S6d 单方法 |
| --- | --- | --- |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` |
| `QV710AF65F` | Android 12 / API 31 | `OK (1 test)` |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` |

API 31 另以同一 artifact 完整运行
`R1AcceptedLifecycleInstrumentationTest`，结果为 `OK (16 tests)`。其他四个 API
本次只补跑 S6d 单方法；不得把历史 S6a/S6b 37-test aggregate 或 S6c 单方法结果
追溯混算为当前 artifact 的完整 aggregate。

S6d 修复了单一 `ServiceConnection` 被跨 bind attempt 复用、旧 callback 可能清除
新 registry 的实现 race；当前 device fault 证明的是 old registration/token
失效、successor connection 与 pending poison 向 post-old current generation
重放，**没有**人为延迟旧 callback/notification 来直接制造重叠代际竞争。真实
release APK on-device poison、owner/compiler 同时崩溃、hung owner transaction、
故意重叠的 stale/duplicate callback/notification、marker → Job crash gap、
reboot/force-stop、连续 backoff/crash-loop、corrupt marker 与 procfs
`UNREADABLE` 仍属于开放 fault matrix。

2026-07-28 R2-S6e retired teardown handler replay evidence（历史同源
artifact）：

```text
app APK  SHA-256 3F2D71DDBAAC1BD8DD74E04B627AC9033A408318B0FD8A6DDCBD6785E7612B18
test APK SHA-256 9CF88E97A0F8AABC9F61C1EAA9AA0FBDF0EDCEB74B0309A01BA9A9CC3F31F742
```

JVM report 仍为 125 tests / 0 failures / 0 errors / 0 skipped。本地
`:app:testDebugUnitTest`、`:app:verifyR2ProjectCompilerCore`、
`:app:verifyReleaseCompilerHostApk`、`:app:verifyOptionalPluginIndependence`、
`:app:lintDebug`、`verifyR1ProtocolCompatibility` 与
`:plugin-api:typescript-api:verifyTypeScriptApiV1Freeze` 全部通过；desktop framed
golden 继续由 TypeScript `6.0.3` 通过。

新增 `retiredSupervisorCallbacksCannotRetireReadySuccessorGeneration`：

1. 在 accepted + ACK 的 cleanup-unproven HOLD job 保持 exact lease/FD 时，从
   fully-ready registration 取得 old owner 与 connection generation `gA`，精确
   `ARM` 该 connection；
2. exact hard-kill old owner，证明 old registry Binder death 与 old owner procfs
   `ABSENT/REUSED`；
3. 等待不同 successor owner 完成双向 handshake，证明同一 compiler epoch/job/FD
   未变且 `gB > gA`；
4. DEBUG/same-UID seam 各重复两次、同步直接调用 retired
   `onServiceDisconnected()`、API 26+ `onBindingDied()`、API 28+
   `onNullBinding()` 与旧 registry death recipient。每次 delivery 后的进程内
   snapshot 都证明 `gB` connection/registry/death record/token/owner 不变，外部测试
   又持续 1 秒轮询同一 generation/owner；
5. replay 后才 cancel 产生 poison；current successor 自动终止 old compiler，且无
   ordinary callback，old FD/process 消失、procfs `ABSENT/REUSED`、quarantine
   exact-clear；
6. fresh compiler epoch 重新 accepted，并完成 ACK + cancel、空 output 与 FD
   release。

同一最终 artifact 的新增单方法在五个 Android API 层级均通过：

| 设备 | 系统 | S6e 单方法 | 该 API 的同步 duplicate delivery |
| --- | --- | --- | --- |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` | 4：disconnect + death recipient |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` | 4：disconnect + death recipient |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` | 8：另含 binding-died + null-binding |
| `QV710AF65F` | Android 12 / API 31 | `OK (1 test)` | 8：另含 binding-died + null-binding |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` | 8：另含 binding-died + null-binding |

API 31 另以同一 artifact 完整运行
`R1AcceptedLifecycleInstrumentationTest`，结果为 `OK (17 tests)`。其他四个 API
本次只补跑 S6e 单方法；不得与历史 37-test aggregate 或 S6c/S6d artifact 追溯
混算。

S6e 证明的是 test Binder thread 上 deterministic、sequential 的 retired teardown
handler no-op fence，不是 Android framework 的真实迟到投递或并发 transaction。
stale `onServiceConnected()`、旧 register/notify/token transaction、hung owner、
owner/compiler 同时崩溃、marker → Job crash gap、真实 release on-device poison、
reboot/force-stop、连续 backoff/crash-loop、corrupt marker 与 procfs
`UNREADABLE` 仍开放。S6e 只增加第 6 个 internal `[x]`；下方 54 个 public R2
checkbox 仍全部未关闭。

2026-07-28 R2-S6f exact persisted-Job pre-arm evidence（历史同源 source；以下保留
当时原文）：

```text
debug app APK   5,894,411 bytes
SHA-256         9056364D6B136F6B7DBCAF233EECE7A5D82128FF07246B8FF236DDF8ABB273E4
androidTest APK 1,190,685 bytes
SHA-256         71C6ED0AA0241F9703D33B9406334513E333772731E4CCA07DCA502A8E386F9A
release APK     2,548,139 bytes
SHA-256         4981474741F5DFEA708106C8DE23B9A531EDE257E0916E9362D23AFF6DE76A97
```

JVM report 为 126 tests / 0 failures / 0 errors / 0 skipped。聚合 Gradle gates
`:app:testDebugUnitTest`、`:app:verifyR2ProjectCompilerCore`、
`:app:verifyDebugTypeScriptDistributionApk`、`:app:verifyReleaseCompilerHostApk`、
`:app:verifyOptionalPluginIndependence`、`:app:lintDebug`、
`verifyR1ProtocolCompatibility` 与
`:plugin-api:typescript-api:verifyTypeScriptApiV1Freeze` 全部通过；desktop framed
golden 继续由 TypeScript `6.0.3` 通过。

新增 `prearmedSystemJobRecoversPoisonedEpochWithoutSuccessorBinding`：

1. 在 cleanup-unproven HOLD job 保持 exact lease/FD、无 ordinary callback 且
   marker 尚不存在时，exact termination path 先 schedule 带唯一 work token 的
   PREARM Job；JobService 观察 live old epoch 的 `SAME/UNREADABLE`，记录
   `PREARM_RETRY`，而 compiler 仍存活；
2. compiler durable-commit exact quarantine，post-check 同一 pending
   component/extras/token 后才接受 delayed self-termination；
3. 测试在 self-kill 前关闭唯一 `BIND_AUTO_CREATE` compiler connection，随后证明
   old Binder/FD/process 消失与 procfs `ABSENT/REUSED`，期间不创建 successor
   compiler；
4. 独立 `:typescript_supervisor` JobService 以同一 work-token digest 记录
   `RECONCILED`、exact-clear marker；此后才 fresh-bind 不同 compiler epoch，并完成
   accepted + ACK + cancel、空 output 与 FD release。

最终 hash 对应 APK 的 S6f 单方法矩阵为：

| 设备 | 系统 | S6f 单方法 | runner time |
| --- | --- | --- | ---: |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` | 7.201 s |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` | 17.495 s |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` | 8.538 s |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` | 4.62 s |

同源 source 还在 API 35 完整运行
`R1AcceptedLifecycleInstrumentationTest`，结果为 `OK (18 tests)`、57.788 s，
其中包含 S6f 新用例；最终 hash artifact 又以上表的 4.62 s 定向复核该方法。
此前暴露互锁的
`cleanupUnprovenRequiresOldProcessDeathBeforeNewProcessAdmission`
又连续复跑 `2/2`：`OK (1 test)` / 5.071 s 与
`OK (1 test)` / 4.900 s。不得把历史 S6a/S6b 37-test aggregate 或
S6c/S6d/S6e artifact 追溯混算为本轮 aggregate。

Sony `QV710AF65F` 正在执行 24h soak，本轮完全未访问：没有安装 APK、没有 adb
查询或 instrumentation。其上方 S6a–S6e 行只保留历史证据，不表示当前 S6f
artifact 曾在该设备运行。

S6f 证明 exact persisted Job 已在 marker commit 前建立且 commit 后仍可 readback，
并在没有 successor bind 时拥有 post-death reconciliation；它不证明 marker 与
JobScheduler 是原子事务。running same-ID/different-token replacement 与
`onStopJob()` 的真机调度语义、telemetry generation 的完全线性化（telemetry
仍是非权威 best-effort 观测）、reboot/force-stop/system_server persisted state、
post-check 后外部取消、跨 reboot monotonic deadline + procfs `UNREADABLE`、连续
crash/backoff 与完整 fault matrix 仍开放。S6f 只增加第 7 个 internal `[x]`；
下方 54 个 public R2 checkbox 仍全部未关闭。

2026-07-29 R2-S6g running fixed-ID Job generation handoff evidence（历史同源
source；以下保留当时原文）：

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

新增 `runningSystemJobReplacementHandsOffToNewExactGeneration`：

1. generation A 先以 exact PREARM work 运行并到达 `PREARM_RETRY`；DEBUG lifecycle
   journal 按 A 的 work-token digest 正向观察到 `START_CALLBACK(A)`；
2. production exact-termination path 随后以相同 fixed Job ID、不同 token 调度
   generation B。测试不预设 framework 的 STOP/START 总顺序，而是按 token 正向
   观察 `STOP_CALLBACK(A, CURRENT|STALE)` 与 `START_CALLBACK(B)`；若 stop 为
   `STALE`，其 current-token digest 必须精确指向 B；
3. pure generation-gate tests 同时覆盖 `STOP(A) → START(B)` 与
   `START(B) → late STOP(A)`；后者不能退休、clear 或 reschedule B。owner telemetry
   又以 B token 记录 exact old epoch 的 `PREARM_RETRY`，最终由 B
   `RECONCILED` 并 exact-clear marker，随后 fresh compiler epoch 完成
   accepted + ACK + cancel、空 output 与 FD release；
4. lifecycle journal 只提供上述 token-scoped positive records。它是有界、
   non-authoritative、best-effort observation；不得由 record absence、最终 count
   或文件 append 顺序推导 framework 全局 exactly-once 或全排序。

最终 hash 对应 APK 的 S6g 单方法矩阵为：

| 设备 | 系统 | S6g 单方法 | runner time |
| --- | --- | --- | ---: |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` | 13.009 s |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` | 20.018 s |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` | 11.397 s |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` | 10.072 s |

同一最终 artifact 在 API 35 又定向复跑 S6g 单方法两次，分别为
`OK (1 test)` / 10.579 s 与 `OK (1 test)` / 13.483 s；连同上表该设备结果累计
`3/3`。同一 artifact 的完整 `R1AcceptedLifecycleInstrumentationTest` 为
`OK (19 tests)` / 71.522 s，其中包含第 4 次 S6g 方法执行。单方法累计与完整 class
是不同 runner invocation，不追溯混算为一个 aggregate。

Sony `QV710AF65F` 仍在执行 24h soak，S6g 本轮完全未访问：没有 adb 查询、没有安装
APK，也没有运行 instrumentation。上方历史 evidence 中的 API 31/QV 行只属于其各自
旧 artifact，不表示当前 S6g artifact 在该设备运行。

S6g 关闭的是当前设备矩阵中一次 running fixed-ID/different-token handoff，以及该
有界窗口内按 token 观察到的 A START、A STOP 与 B START；最终 exact owner 和
reconcile generation 为 B。它不证明连续或重叠 replacement、矩阵外 callback
ordering、handoff 中 JobService process death/restart、尚未观察的
completion/`onStopJob()` race、全局 exactly-once、reboot/force-stop/system_server
persistence、外部取消或 continuous crash/backoff policy。S6g 只增加第 8 个
internal `[x]`；下方 54 个 public R2 checkbox 仍全部未关闭。

2026-07-29 R2-S6h running JobService owner-death exact-work redispatch evidence
（历史同源 source；以下保留当时原文）：

```text
debug app APK   5,894,411 bytes
SHA-256         255B2B9DF1F612414A1D4B1DB3B1796CD50790236AF625606324F6E95A4D446D
androidTest APK 1,209,751 bytes
SHA-256         D8CD7E7F28D1F29F887B379274FBAB47680DEE4E7DABAC52181683F535FACE36
release APK     2,548,843 bytes
SHA-256         65F4AA96827CB5C58158796FA6033CAA16F987BC9C4EF94483DBE4FEE2CBD55C
```

production app/release APK 与 S6g 相同；S6h 的变化位于 androidTest source/artifact。
JVM report 为 131 tests / 0 failures / 0 errors / 0 skipped。聚合 Gradle gates
`:app:testDebugUnitTest`、`:app:verifyR2ProjectCompilerCore`、
`:app:verifyDebugTypeScriptDistributionApk`、`:app:verifyReleaseCompilerHostApk`、
`:app:verifyOptionalPluginIndependence`、`:app:lintDebug`、
`verifyR1ProtocolCompatibility`、
`:plugin-api:typescript-api:verifyTypeScriptApiV1Freeze` 与
`:app:assembleDebugAndroidTest` 全部通过（390 tasks）；desktop framed golden
继续由 TypeScript `6.0.3` 通过。

新增 `runningSystemJobIsRedeliveredAfterSupervisorProcessDeath`：

1. exact PREARM generation `W` 先在 owner `P1` 中到达 `PREARM_RETRY`，测试按
   lifecycle journal 正向确认 `START_CALLBACK(W, P1)`，再 commit old compiler
   epoch `C` 的 marker，并复核 fixed-ID Job 的完整 extras 仍为 `W`；
2. 测试在 monotonic boundary 后 exact-kill `P1`，证明该 owner process epoch
   `ABSENT/REUSED`；随后关闭唯一 compiler binding，仅在 exact procfs 复核后 kill
   `C`，并证明 old Binder、FD 与 process 消失；
3. post-kill `START_CALLBACK(W)` 必须来自不同 owner，最终
   `RECONCILED(W)` owner 又必须存在自身的 post-kill `START_CALLBACK(W)`；
   P1/final owner 的 boot ID、UID 与 process name 相同，但 exact process identity
   不同，work-token digest 与 old-nonce digest 保持一致；
4. marker 与 Job 消失、poisoned callback/output 未被发布、fresh compiler
   accepted + ACK + cancel 与 FD release 全部通过后，测试才写出
   `R2S6hJobDeath` completion evidence。

最终 hash 对应 APK 的 S6h 单方法矩阵为：

| 设备 | 系统 | S6h 单方法 | completion evidence path | runner time |
| --- | --- | --- | --- | ---: |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` | `NATURAL`，未调用 shell seam | 13.971 s |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` | `NATURAL`，未调用 shell seam | 18.616 s |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` | `NATURAL`，未调用 shell seam | 21.697 s |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` | `POST_FORCE_INVOCATION_BOUNDARY`；stdout 精确为 `Running job [FORCED]` | 34.378 s |

runner time 是完整 test invocation 时间，不是单独的 redelivery latency。API 35
completion evidence 同时记录 command boundary 与最早 observed qualifying
`START_CALLBACK(W)` 的 `elapsedRealtimeNanos`；本次最早记录严格晚于 boundary。
这只表示命令调用边界之后观察到 callback，不推导 shell 因果。API 24/25/28 的
evidence 均为 `NATURAL`，且 `forceOutput=<not-invoked>`。

同一最终 artifact 在 API 35 完整运行
`R1AcceptedLifecycleInstrumentationTest`，结果为 `OK (20 tests)` / 104.348 s，
其中包含 S6h。最终实现之前的一次 API 35 诊断曾纯自然等待 90 秒：framework
日志显示约 11 秒后的 restart 安排，但 OEM 在该观察窗内没有实际提供 post-P1
positive callback；该失败诊断不属于上表通过结果，也不是自然重投证据。

Sony `QV710AF65F` 正在执行 24h soak，S6h 本轮完全未访问：没有 adb 查询、安装
APK 或运行 instrumentation。上方 API 31/QV 行只属于各自历史 artifact。

S6h 只关闭一次 same-boot、test-side exact owner kill 后，同一 scheduler-tracked
generation `W` 由不同 owner 接收并 reconcile 的子项。API 24/25/28 提供 30 秒窗口内
的自然重投正向证据；API 35 只证明 30 秒内未观察到 positive callback、recheck 时
marker 与 scheduler-tracked `W` 仍精确匹配、fixed-ID forced-eligibility 命令被接受，
随后观察到 `W` callback/reconcile。它不证明 shell 因果、OEM autonomous retry
时限或 fixed backoff。不同 worker phase、连续多次或 system-triggered JobService
crash/redelivery、same-ID replacement handoff 中的 owner death、
completion/`onStopJob()` race、system kill、system_server restart、reboot、
force-stop、外部取消、corrupt/`UNREADABLE` 与 continuous crash/backoff policy
仍开放。当前 internal provisional checklist 为 9/9 checked；下方 54 个 public R2
checkbox 仍为 0/54 checked。

2026-07-29 R2-S6i retained exact-work overlapped owner/compiler kill-request
evidence（当前同源 source）：

```text
debug app APK   5,894,411 bytes
SHA-256         255B2B9DF1F612414A1D4B1DB3B1796CD50790236AF625606324F6E95A4D446D
androidTest APK 1,214,660 bytes
SHA-256         0D1EFBD0DBFB1EC71A4B48ABA4858A9D1B74BD87DBDF1663D09BD154E6BB30EE
release APK     2,548,843 bytes
SHA-256         65F4AA96827CB5C58158796FA6033CAA16F987BC9C4EF94483DBE4FEE2CBD55C
```

production app/release APK 与 S6h 相同；S6i 的变化位于 androidTest
source/artifact。JVM report 仍为 131 tests / 0 failures / 0 errors / 0 skipped；
聚合 Gradle gates 全部通过（390 tasks），desktop framed golden 继续由 TypeScript
`6.0.3` 通过。

新增
`runningSystemJobIsRedeliveredAfterOverlappedSupervisorAndCompilerDeath`：

1. test-only 120 s PREARM generation `W` 先在 owner `P1` 中到达
   `PREARM_RETRY`；测试 exact-commit old compiler epoch `C` 的 marker，并复核
   scheduler-tracked work、active FD、`P1` 与 `C` 都仍是各自 exact-live identity；
2. 关闭唯一 external compiler binding 后，两个 kill worker 各自再次完成 exact-live
   validation，再通过 shared ready/fire latch 同时取得发出 kill request 的资格；
3. 两个 worker 并发调用 same-UID test-side `Process.killProcess(exactPid)`。测试不规定
   owner/compiler signal initiation 顺序，只记录各自 initiation/return time 与
   `initiationSpanNanos`；`bothSignalsReturnedNanos` 是两次调用都已返回的时间，不是
   两个 process 的共同 death time，也不证明 actual death interval overlap；
4. qualifying `START_CALLBACK(W, P2)` 必须严格晚于
   `bothSignalsReturnedNanos`、携带同一 W token 且 owner 不等于 `P1`。随后测试分别
   证明 `P1` 与 `C` exact-disappeared，最终 `P2` 以同一 W token/old nonce
   `RECONCILED`，marker/Job clear，fresh compiler accepted + ACK + cancel、empty
   output 与 FD release 全部成立后，才写出 `R2S6iDualDeath` completion evidence；
5. API 35 仍复用 exact scheduler-tracked-W recheck 与 fixed-ID forced-eligibility
   seam；`POST_FORCE_INVOCATION_BOUNDARY` 只表示 invocation boundary 后观察到
   qualifying callback，不证明 shell 因果。

最终 hash 对应 APK 的 S6i 单方法矩阵为：

| 设备 | 系统 | S6i 单方法 | completion evidence path | kill initiation span | runner time |
| --- | --- | --- | --- | ---: | ---: |
| `emulator-5556` | Android 7.0 / API 24 | `OK (1 test)` | `NATURAL`；shell 未调用 | 33,100 ns | 14.190 s |
| `emulator-5554` | Android 7.1.1 / API 25 | `OK (1 test)` | `NATURAL`；shell 未调用 | 3,498,300 ns | 20.814 s |
| `BH900ASK9E` | Android 9 / API 28 | `OK (1 test)` | `NATURAL`；shell 未调用 | 354,167 ns | 18.585 s |
| `968e9f18` | Android 15 / API 35 | `OK (1 test)` | `POST_FORCE_INVOCATION_BOUNDARY`；stdout 精确为 `Running job [FORCED]` | 20,417 ns | 34.322 s |

`kill initiation span` 是两个 worker 记录的 signal initiation time 之差，不是 signal
duration、death latency 或 actual death overlap。runner time 是完整 test invocation
时间。API 24/25/28 completion evidence 为 `NATURAL`；API 35 只证明 30 秒内未观察到
positive callback、recheck 时 marker 与 scheduler-tracked `W` 仍精确匹配、
forced-eligibility command 被接受，随后在 invocation boundary 后观察到同一 `W`
callback/reconcile。

同一最终 artifact 在 API 35 完整运行
`R1AcceptedLifecycleInstrumentationTest`，结果为 `OK (21 tests)` / 138.103 s，
其中包含 S6h 与 S6i。

Sony `QV710AF65F` 正在执行 24h soak，S6i 本轮完全未访问：没有 adb 查询、安装 APK
或运行 instrumentation。上方 API 31/QV 行只属于各自历史 artifact。

S6i 只关闭一次 same-boot、retained exact `W`、shared validation barrier 后并发
发出 test-side exact owner/compiler kill request，并以 `bothSignalsReturnedNanos`
约束后续 positive callback 的子项。它不证明固定 signal initiation 顺序、
instruction-level simultaneous kill、两个 process 的 actual death interval overlap，
或 system/LMK/OOM/force-stop 触发的 dual death。不同 worker phase、连续/重复 dual
fault、same-ID replacement handoff 中的 dual death、system_server/reboot、
corrupt/`UNREADABLE` 与 continuous crash/backoff policy 仍开放。当前 internal
provisional checklist 截至 S6i 为 10/10 checked；下方 54 个 public R2 checkbox 仍为
0/54 checked。

2026-07-29 R2-P1 production/public-shaped admission source/build evidence：

- release/debug Service 均接入 `R2CompilationCoordinator`；普通 frozen-v1 request
  继续 R0 reject，完整 additive revision/fingerprint/archive-digest request 才进入
  preview；
- 独立 `r2-public-preview` APK 的 instrumentation target 是 fixture 自身，运行时
  会验证 caller/plugin UID 不同、permission base protection 为 signature、permission
  owner/app/fixture signer 一致、remote Binder、四项 capability 全为 false，以及
  accept → wrong-token ACK reject → exact post-return ACK → 单一 failed terminal →
  empty output；
- no-device aggregate 同时覆盖 compiler core/JVM/desktop golden、debug AndroidTest
  编译、debug/release APK R8 marker 与 debug-hook exclusion、v1 byte freeze、app
  lint/optional-plugin independence、fixture debug/release build/lint 和实际 APK
  signer certificate 比对；
- 设备 instrumentation 本轮未执行，因此 external runtime、第二 caller UID owner
  isolation、minified release full pipeline 与支持矩阵仍是开放 blocker。

本轮最终 no-device evidence（同一 source state；未执行任何 `adb` 命令）：

```text
debug app APK       5,894,411 bytes
SHA-256             9DA307707E7B35D3CB162FB1EDD5F5C7D925F860815D12A8E0CAD1F63CCE7479
androidTest APK     1,215,829 bytes
SHA-256             1B601E0F1BC104C525AC3D8C63ED2814B7238A9CF80BD1A677D9C36150863B3D
release app APK     2,625,495 bytes
SHA-256             903B224F3ED5CBEF406E2FFAF062CC5656B7E7C03CBEE054745A4E1F14A951FB
fixture debug APK     946,317 bytes
SHA-256             9FBB742FB8E1DC7D59172153087B93254CB678740805E4A270BC4464FA6AD97F
fixture release APK   678,534 bytes
SHA-256             B70061E3DC5BCE5895BF5F7B2EA9B6227C445FA571F51F2649246ED1CA2A4C33
```

`:app:testDebugUnitTest` 最终报告为 16 suites / 137 tests / 0 failures /
0 errors / 0 skipped；`verifyR2ProjectCompilerDesktop` 由 TypeScript `6.0.3`
通过。根聚合门禁 `verifyR2PublicPreviewFixture` 通过；app lint 为 0 errors /
3 warnings，fixture debug/release lint 均为 `No issues found`。

P1 使 internal provisional checklist 达到 11/11 checked；下方 54 个 public R2
checkbox 仍为 0/54 checked。构建命令、设备执行命令与证据边界见
[R2 Production/Public-shaped Admission Preview](../architecture/PUBLIC_ADMISSION_R2_PREVIEW.md)。

2026-07-29 R2-P2 versioned profile/config authority 与 binary APK manifest
evidence：

- public metadata → request manifest → snapshot → framed start → Worker result →
  native parser → private output manifest/diagnostics 全部绑定同一个
  `R2CompilationProfileAuthority`；input/output provisional exact shape 均提升为
  revision 2；
- canonical effective options 明确写出 18 项固定字段、numeric TypeScript enum、
  stable key order 和 SHA-256；Worker missing/unknown/revision/config/option drift 在
  native packaging 前 fail closed；
- 实际 debug/release app/fixture APK binary manifest gate 保留完整 XML QName，
  精确验证 compiler service、effective enabled、唯一 compiler endpoint 与
  external fixture shape，仍保留实际 APK signer certificate 比对；
- frozen AIDL、`TypeScriptCompilerContract`、ZIP v1 与 compat AAR/checksum 未修改，
  四项 capability flags 仍全部为 false。

最终 no-device artifact evidence：

```text
debug app APK       5,894,411 bytes
SHA-256             6FA9F6E3830B83BB4B047830BBAE57861B79B0EB8DF194B6E5EA39AEA0CC6D1A
androidTest APK     1,216,136 bytes
SHA-256             3170AFD2C2DBC9E300BF0C9DA4083AEACCECC82D6C4EFD08ADA58A116D05F2FF
release app APK     2,628,391 bytes
SHA-256             FB00A41B8A030A87623A4DA6FA0E4395D660238647D597A6D773E9DED9762E33
fixture debug APK     946,422 bytes
SHA-256             A8F98152A3F06F083955CEBE323FC4DE2833C04846E8FE76BE14EF20D996C089
fixture release APK   678,566 bytes
SHA-256             6B975CFADD2A654E90B7C8F5B346663BDC39012BB74D5532ABA65DB8FFFDF8B6
```

根聚合 `verifyR2PublicPreviewFixture` 为 `BUILD SUCCESSFUL`（485 actionable
tasks，89.6 s），包含 JVM/desktop、AndroidTest compile、debug/release R8/APK、
API freeze、lint、signer 与 binary manifest shape。随后以 `--rerun-tasks`
强制重跑 app/API JVM 和真实 Worker desktop golden，同样通过（73 tasks，106 s；
TypeScript `6.0.3`）。最终 XML：

- app：17 suites / 139 tests / 0 failures / 0 errors / 0 skipped；
- TypeScript API：3 suites / 16 tests / 0 failures / 0 errors / 0 skipped；
- app lint：0 errors / 3 warnings；fixture debug/release lint：`No issues found`。

独立审计后又收紧 binary parser 的 QName、effective-enabled 与全 application
唯一 endpoint 校验；定向 gate（284 tasks）及根聚合（485 tasks，39 s）再次
`BUILD SUCCESSFUL`，审计复查未再发现 blocker/major。

Kotlin daemon 连接失败时 Gradle 使用 documented fallback compiler，两个命令最终
exit code 均为 0。P2 本轮未执行任何 `adb` 命令。internal provisional checklist
达到 12/12 checked；下方 54 个 public R2 checkbox 仍为 0/54 checked。

2026-07-29 R2-P3a app-private file-backed native input evidence：

- production input 在 app-private held inode 上执行 frozen ZIP v1 streaming
  validator，并通过 transactional sink 写入独立 VFS spool；任何 entry、摘要、
  UTF-8 或 inventory 失败都会 abort 并清理 partial spool；
- production snapshot 只保留 immutable metadata、spool offset/length 和 read-only
  inode ownership。native framer 每次最多通过 cursor 读取 16 KiB，使用单个
  reusable buffer 流式重验 SHA-256，不再 materialize 或持有完整 caller source；
- ACK、`nextFrame()`、cursor read 和 release 全部在 dedicated 单线程 input
  executor 上串行；main thread 只接受通过 exact pending/session/port/generation/
  epoch fence 的 prepared frame。stale frame 不会发送给 successor session；
- compile/stop/close 路径在 terminal publication 或 output packaging 前先完成
  exact input release。cleanup 无法证明时 fail closed、poison admission 并抛出
  input-cleanup-unproven，不会把成功 outcome 送入 output staging；红队审计进一步
  覆盖 spool create、seal reader、post-seal ownership publication 与 Windows
  read-only fallback 删除失败，均保留 cause/suppressed 链并进入同一监督路径；
- P3a 的 72 MiB native storage envelope 只表示 file-backed native input 能力，
  **不等于** Worker 已支持 64 MiB。截至 P3a，Worker 仍 materialize heap-backed
  VFS/Program，dispatch admission 限制为 512 files、8 MiB/file、8 MiB total；
  后续 P3b-a desktop slice 与仍开放的 P3b-b device blockers 见下一节；
- `compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、
  `sourceMapV3` 四项 capability flags 仍全部为 `false`。

本轮最终 no-device evidence（同一 source state；未运行 `adb`）：

```text
debug app APK       5,894,411 bytes
SHA-256             8FB232C653F0A4202863659E2FC6AAEF8CDB187A3519992FEEC8F1BB69B17CCD
androidTest APK     1,130,695 bytes
SHA-256             906C68AC6850E4C1885F73BE707ED378DEAEC2939676D465C0BDDDAF80E1DF82
release app APK     2,636,143 bytes
SHA-256             9C14CE10A39D8F5ADEBD28DF9AE001E20568FADE12331399444ADD65EC85E21D
fixture debug APK     946,422 bytes
SHA-256             A8F98152A3F06F083955CEBE323FC4DE2833C04846E8FE76BE14EF20D996C089
fixture release APK   678,566 bytes
SHA-256             6B975CFADD2A654E90B7C8F5B346663BDC39012BB74D5532ABA65DB8FFFDF8B6
```

最终 XML：

- app：17 suites / 157 tests / 0 failures / 0 errors / 0 skipped；
- TypeScript API：3 suites / 16 tests / 0 failures / 0 errors / 0 skipped。

强制重跑 app/API JVM、desktop golden 与 source policy 为 `BUILD SUCCESSFUL`
（74 actionable tasks，31 s）；根聚合 `verifyR2PublicPreviewFixture` 同样为
`BUILD SUCCESSFUL`（485 actionable tasks，45 executed，1m32s），其中
`:app:verifyR2ProjectCompilerCore` 通过，desktop golden 使用 TypeScript `6.0.3`。
P3a 使 internal provisional checklist 达到 13/13 checked；下方 54 个 public R2
checkbox 仍为 0/54 checked。本轮未运行任何 `adb` 命令或真机 instrumentation，
因此不得据此开放 Public R2。

2026-07-30 R2-P3b-a bounded Worker input/lifetime evidence：

- Worker direct-framing authority 现为 4607 files、8 MiB/file、72 MiB total；
  desktop golden 完整送入 4607 个 zero-byte file records，验证 ingress state、
  错误释放与小型 successor；另由 start/file/end frame 与 VFS accounting 复核
  72 MiB inclusive boundary，file-count 和 total-byte `+1` fail closed。Android
  host 的 `PROVISIONAL_WORKER` eligibility 仍独立保持 512 files、8 MiB/file、
  8 MiB total；
- 每文件只在 digest 成功后执行一次 fatal UTF-8 decode，并清零完整 input
  `Uint8Array`；只读 VFS 不 re-encode/re-decode，CompilerHost 使用 per-job lazy
  `SourceFile` cache 与 stable `version="0"`；
- scanner/token、aggregate path characters 与 directory trie node/character gates
  在 AST 前拒绝 adversarial complexity。diagnostics 逐 SourceFile/逐条执行
  bounded serialization、deduplication 与 byte accounting；pre-emit error
  short-circuit emit；
- compile `finally` 覆盖成功/异常路径，断开 Program/builder/host/cache/VFS/trie，
  并置空 input record text；ingress failure 另清零 active bytes 并释放未转交的
  state；
- desktop retained-memory child 明确使用 512 MiB old-space 与 exposed GC，在同一
  isolate 中执行 warm-up → 单次 64 MiB caller + 8 MiB trusted ASCII boundary →
  forced-GC plateau → small successor。它验证的是 Node aggregate/retained
  references，不是 WebView renderer、Android PSS、LMK/OOM 或重复大任务 device
  matrix；本轮 `heapUsed` 为 22.6 MiB baseline → 23.7 MiB boundary →
  24.0 MiB small successor；
- `:app:testDebugUnitTest` 最终报告为 17 suites / 159 tests / 0 failures /
  0 errors / 0 skipped；TypeScript API 为 3 suites / 16 tests / 0 failures /
  0 errors / 0 skipped；
- `:app:verifyR2ProjectCompilerCore` 为 `BUILD SUCCESSFUL`（113 actionable
  tasks，12 executed，26 s）；根聚合 `verifyR2PublicPreviewFixture` 同样为
  `BUILD SUCCESSFUL`（最终复核 485 actionable tasks，28 executed，1m10s），包含新的
  TypeScript `6.0.3` Worker capacity/retained-memory golden、debug/release APK、
  R8、API freeze、lint、fixture signer 与 binary manifest shape。

P3b-a 使 internal provisional checklist 达到 14/14 checked；下方 54 个 public
R2 checkbox 仍为 0/54 checked。production 围栏与四项 capability flags 均未改变，
本阶段没有新增 `adb`/真机证据；P3b-b device matrix 前不得开放 Public R2。

2026-07-30 R2-P3b-b1 Android device capacity / exact realm evidence：

- `expandedWorkerCapacity` 只存在于 same-UID `DEBUG` control；public control
  constructor invariant、release parser 与 release host 都不能启用。普通
  production host 的 512 files/8 MiB total fence 未改变；
- 16 KiB frame 的 API 35 exact-boundary run 在 120 秒仍未完成 ingress；23 KiB
  诊断版本在 deadline 时为 `57,797,632 / 70,893,622` bytes、file index 6、
  2473 frames。当前 input/output raw chunk 为 64 KiB，完整 internal message
  上限 128 KiB；window=1、single cursor/reusable buffer 不变，设备最大 encoded
  frame 为 87,525 bytes；
- direct file-backed test 将 exact 67,108,864-byte caller、3,784,758-byte trusted
  declarations（total 70,893,622 bytes、117 files、1419 frames）实际送入 Worker，
  并证明大成功前 exact session 已退休、spool 已 unlink、small successor 使用更高
  generation。API 24/WebView `119.0.6045.194` runner 50.129 s，
  API 28/WebView `126.0.6478.186` runner 106.237 s，
  API 35/WebView `130.0.6723.86` runner 105.159 s，全部 PASS；
- 流式 ZIP fixture 不物化 64 MiB，精确生成 STORED container 64 MiB/`+1` 与
  DEFLATED expanded 64 MiB/`+1`。每个 `+1` 先在放宽 1 byte 的 production
  streaming validator 下通过 structure/inflate/CRC/size/ratio，再在正式 limit
  下仅以 `QUOTA` 失败；
- API 24 Binder 最终矩阵 `OK (2 tests)` / 85.627 s：STORED `+1`/exact 为
  1,279/38,106 ms，DEFLATED `+1`/exact 为 962/35,093 ms；API 35 为
  `OK (2 tests)` / 218.776 s：652/104,472 ms 与 593/103,964 ms。`+1` 都是
  accepted → ACK 后 `OUTCOME_FAILED + ERROR_ARCHIVE_QUOTA + 0-byte output`；
  exact 都是 committed validated output、1 module、exact manifest provenance
  与 exactly-one callback；
- 每个成功 project 现在在 terminal publication 前销毁 exact WebView realm，
  失败时保留同一 pending/session identity 每 250 ms 重试；next project 懒创建新
  session。这是 realm lifetime proof，不是 renderer/compiler-service PSS 或
  allocator reclaim 证明；
- 定向 `compileDebugAndroidTestKotlin`、framer/protocol/public-control JVM tests
  与 desktop Worker golden 全部通过。desktop retained heap 仍为
  22.6 MiB baseline → 23.7 MiB boundary → 24.0 MiB small replay；最终根聚合
  `verifyR2PublicPreviewFixture` 为 `BUILD SUCCESSFUL`（485 actionable tasks，
  25 executed，47s）。app JVM 为 17 suites/160 tests/0 failures，TypeScript API
  为 3 suites/16 tests/0 failures；app lint 为 0 errors/3 warnings，public fixture
  debug/release lint 均为 `No issues found`。

最终设备复跑 artifacts（working-tree build，本阶段未创建 commit）：

```text
debug app APK       5,894,411 bytes
SHA-256             05B3FBC1915D9FFEBC362C8EAA8DDD7671C01E9F55D6860BCBE043FF9D5EB8D6
androidTest APK     1,375,422 bytes
SHA-256             F0673E92D0F50E488873AAF0AE91E90356E04FEC3898AF45C46F5E261F04382D
release app APK     2,638,091 bytes
SHA-256             29C381C97E023335D64A6346899A9FC145B1BBBFFEA5CFD8819D912DA303A955
```

API 24/35 最终复跑后未留下 capacity fixture/output 文件；本阶段没有访问
`QV710AF65F`。

P3b-b1 使 internal provisional checklist 达到 15/15 checked；下方 54 个 public
R2 checkbox 仍为 0/54 checked。尚未完成 external-UID debug/minified-release
valid 64 MiB、
ingress/parse/semantic/emit cancel/timeout、active renderer death、重复大任务
PSS/FD/spool cleanup、真实 LMK/OOM、Unicode/high-token 与 output 128/256 MiB，
因此 production fence 与四项 capability flags 继续保持关闭。完整边界见
[R2-P3b-b1 Android Device Capacity and Exact Realm Retirement](../architecture/WORKER_INPUT_R2_P3B_B1.md)。

2026-07-30 R2-P3b-b2a DEBUG phase-entry hard-stop / active renderer-loss
evidence：

- 在 b2a source state，Worker、page 与 native strict parser 首次共同冻结内部
  ingress → parse → semantic → emit 顺序，各有 exact `started`/`completed`，且
  当时 result start 不能越过 active 或未完成 phase。ingress 窗口覆盖真实 framed
  input；parse 包含 Program construction 与 syntactic/global diagnostics，semantic
  包含 per-file semantic diagnostics，当时 emit completion 位于 emit decision/
  `builder.emit()` 之后；b2k 当前二进制后来有意把 emit 延伸到 result framing 的
  最终 ACK，因此此处是历史 phase-entry 证据，不是当前 completion boundary；
- `workerSpinMilliseconds` 与 `workerSpinPhase` 必须成对启用。
  production frame 始终发送 `0 + none`；只有 same-UID `DEBUG` private control
  能在 exact phase 的 `started` 后进入有界 synthetic spin。该 seam 证明 phase
  entry 可由 keyed hard stop 终止，不证明零 spin 的自然 TypeScript 工作在阶段
  中途被抢占；
- `phaseEntryHardStopCoversCancelAndTimeoutAcrossAllWorkerPhases` 在
  `emulator-5556` / API 24 / Chrome `119.0.6045.194` 与 `968e9f18` / API 35 /
  Android System WebView `130.0.6723.86` 均为 `OK (1 test)`，分别耗时
  `27.591 s` / `24.794 s`：四个入口各完成 cancel 与 timeout，最后
  terminated Worker generation 均为 `8`，随后 small project recovery 成功；
- `activeLargeProjectRendererTerminationIsCorrelatedAndRecovers` 在 API 35 为
  `OK (1 test)`，耗时 `82.492 s`：exact 67,108,864-byte caller +
  3,784,758-byte declarations 进入 active semantic entry 后，只允许 exact
  handle/session/phase 调用 renderer termination；terminal 为
  `ERROR_COMPILER_UNAVAILABLE` 且无 compilation，callback 记录
  `didCrash=false`、`rendererPriorityAtExit=2` 与 exact correlation。旧 renderer
  `(pid=5530,starttime=97989470)` 消失，successor
  `(pid=5788,starttime=97996568)` 使用不同 identity 与更新 session 成功；
  project terminal `elapsedMs=70550`。`didCrash=false` 不是 LMK/OOM 证明；
- 同一最终 artifact 上的 Binder deadline/cancel 两项回归在 API 24/35 均为
  `OK (2 tests)`，分别耗时 `3.046 s` / `1.497 s`；
- 定向 framer/control JVM、desktop TypeScript `6.0.3` phase sequence golden、
  Kotlin debug/androidTest compilation、source policy 与 debug/release APK
  marker gate 已通过；最终根 `verifyR2PublicPreviewFixture` 为
  `BUILD SUCCESSFUL in 17s`（485 tasks：20 executed、465 up-to-date），其中
  4,607-file desktop golden 为 `984 ms`，retained heap
  baseline/boundary/replay 为 `22.6/23.7/24.0 MiB`。最终 debug app、
  androidTest、minified-release app SHA-256 分别为
  `2b99087a92ecb383a28ff22cffc167e4fc5862b274dc446e0dd60b95c818d4c3`、
  `22ab8b90a256b708d363da1a3202139d43054dd9b8d16dc84988474e36451135`、
  `b08718d2c7737707a6bac516ff737bf62748c4543aba40380b4ffdd1ddb1e295`。

P3b-b2a 使 internal provisional checklist 达到 16/16 checked；下方 54 个
public R2 checkbox 仍为 0/54 checked。尚未完成 external-UID
debug/minified-release valid 64 MiB、零 spin 的自然 phase 中途
cancel/timeout、重复大任务 PSS/FD/spool/cursor/Worker cleanup、真实 LMK/OOM、
Unicode/high-token 与 output 128/256 MiB，因此 production 512 files/8 MiB
total fence 与四项 capability flags 均未改变。完整边界见
[R2-P3b-b2a DEBUG Phase-entry Hard-stop and Active Renderer-loss](../architecture/WORKER_FAULT_R2_P3B_B2A.md)。

2026-07-30 R2-P3b-b2b repeated resource / startup orphan-cleanup evidence：

- `R2ProjectInputTransportTelemetry` 新增 exact request、cursor opened/closed、
  active 与 released accounting；自然 ingress cancel 必须命中未完成的 active
  cursor，terminal 必须 opened=closed、inactive/released、无 compilation，
  且 terminated Worker generation 前进。framer release、session port/WebView
  teardown、coordinator executor termination 或 callback death-recipient unlink
  无法证明时，不再发布普通 terminal，而是 poison process admission；
- compiler-service PSS 使用 exact `(pid,starttime)` 前后夹持的 5-sample median；
  Android 7 旧式 `TOTAL` 与新式 `TOTAL:`/`TOTAL PSS:` 均严格解析，歧义 fail
  closed。FD gate 要求连续 5 次完整 sorted target multiset 一致，并硬拒绝三类
  private staging/spool 与 caller/capacity archive target，包括 deleted inode；
- `R2PrivateTransientOrphanScavenger` 在每个正常 service epoch 启动时非递归清理
  recognized private input/spool/output regular files，恢复 owner-write 后删除并
  二次枚举。unknown/sentinel、recognized-name directory 与 nested file 均保留；
  list/classify/write/delete/recheck 任一步无法证明都先 `failClosed()`；
- API 24 / Chrome `119.0.6045.194` 的三轮 Binder exact64 success 为
  `OK (1 test)` / `139.605 s`：同一 compiler `(1923,24020567)`，warm PSS
  61,402 KiB，cycle medians 130,416 / 124,394 / 125,291 KiB，成熟 FD
  127 / 127 / 127。Chrome 119 是单进程 WebView，因此首个大工作集使用
  96 MiB first-load envelope；cycle 3 相对成熟 cycle 1 下降 5,125 KiB；
- API 35 / WebView `130.0.6723.86` 的三轮 Binder exact64 success 为
  `OK (1 test)` / `353.825 s`：同一 compiler `(19544,98366926)`，warm PSS
  91,525 KiB，cycle medians 93,041 / 93,369 / 93,712 KiB，成熟 FD
  214 / 214 / 214；cycle 3 相对 cycle 1 仅增加 671 KiB。两台设备每轮都证明
  callback exactly once、active jobs=0、named/remote transient=0、committed
  emitted output，最后 small successor 成功；
- 同一最终 androidTest artifact 的零 spin 自然 ingress cancel 在 API 24 为
  `OK (1 test)` / `19.206 s`：三轮 accepted bytes 都为 8,388,608，
  cursor `1/1`，Worker generation 1/2/3；该 provider 没有独立 renderer。
  API 35 为 `OK (1 test)` / `39.363 s`：accepted bytes
  9,699,328 / 9,240,576 / 9,502,720，cursor `2/2`，generation 1/2/3，
  同一 renderer `(21269,98405115)` 的 PSS medians 为
  153,021 / 147,886 / 154,175 KiB；
- cold predecessor → orphan placement → different successor 的真实 startup
  matrix 在 API 24/35 均为 `OK (1 test)`，分别 `2.192 s` / `1.252 s`；
  三类只读 orphan 消失，unknown/目录/nested fixture 保留，随后 remote Binder
  metadata 与 prewarm 成功。JVM scavenger 7-test fail-closed matrix、
  framer cursor accounting tests、b2b source policy 与 AndroidTest APK
  marker gate 也全部通过；
- 最终同一 app/androidTest artifact 的 debug app、androidTest、minified-release
  app SHA-256 分别为
  `88b8da9a57ae2549f43f9e91072d8ce8e2b0a26753008eccbea085f85a6a42de`、
  `ffe6c1de01cebc3326c17e38d7ad87062454e991ea34d064ebbf7995ddbeb689`、
  `fb7239f88074d7b734581a374d1664bd914eeda0552e5b3419b3f28a7316e399`；
  app JVM 为 18 suites/169 tests/0 failures，TypeScript API 为
  3 suites/16 tests/0 failures。最终 no-device 根
  `verifyR2PublicPreviewFixture` 为 `BUILD SUCCESSFUL in 1m 6s`
  （487 tasks：31 executed、456 up-to-date），4,607-file desktop ingress 为
  938 ms，retained heap baseline/boundary/replay 为 22.6/23.7/24.0 MiB；
- 最终三组设备命令都只访问 `emulator-5556` 与 `968e9f18`，没有访问
  `QV710AF65F`。

P3b-b2b 使 internal provisional checklist 达到 17/17 checked；下方 54 个
public R2 checkbox 仍为 0/54 checked。终审另记录 no-follow symlink
classification 与 direct-test 最终 `host.close()` cleanup-report 断言作为后续
hardening；它们不改变本切片的设备结论。尚未完成 external-UID
debug/minified-release valid 64 MiB、零 spin 自然 parse/semantic/emit 中途
cancel/timeout、自然 ingress timeout、真实 LMK/OOM、Unicode/high-token 与
output 128/256 MiB，因此 production 512 files/8 MiB total fence 与四项
capability flags 均未改变。完整边界见
[R2-P3b-b2b Repeated Resource and Startup Orphan Cleanup](../architecture/WORKER_RESOURCE_R2_P3B_B2B.md)。

2026-07-30 R2-P3b-b2c external production-fence pipeline source/APK/device
evidence：

- standalone `r2-public-preview` fixture 与 plugin 使用不同 application ID，
  signer/APK/manifest gate 要求同签名；设备 instrumentation 还要求 runtime UID
  不同、permission base protection 为 `signature`，并核对 permission owner、
  plugin 与 fixture signer。API 24 runtime UID 为 fixture/plugin
  `10078/10090`，API 35 为 `10314/10312`；
- 正确哈希的 invalid ZIP 必须先 accept，拒绝 wrong-token ACK、接受 exact ACK，
  再 exactly once 返回
  `OUTCOME_FAILED + ERROR_ARCHIVE_FORMAT`、零 compilation payload 与
  truncate/fsync/seek 后的 empty output；该回归没有被容量 case 取代；
- fixture 从已安装 plugin 锁定 declarations/profile/config/runtime authority
  与 source APK/package identity，并在 request 构造前后复核，关闭 authority
  TOCTOU；profile/runtime schema revision 精确读取
  `TypeScriptPluginCapabilityKeys.PROFILE_SCHEMA_REVISION` /
  `RUNTIME_CAPABILITY_SCHEMA_REVISION`。inclusive caller 为 exact
  `4,603,850` bytes；加 installed trusted
  declarations `3,784,758` bytes / 108 files 后恰为 `8,388,608` bytes。
  accepted → wrong ACK reject → exact ACK 后必须 exactly once
  `OUTCOME_EMITTED`，并重验 diagnostics/JS/map/manifest payload SHA、
  inventory、module/map/source pairing、manifest SHA 及完整 authority；
  `sourceMappingURL` 必须位于终端，允许可选 terminal LF、拒绝 CR；
- plus-one caller 为 `4,603,851` bytes；archive 结构、entry metadata、
  manifest coverage、CRC 和 archive/request-manifest/source payload SHA 均合法。
  accepted/ACK 后必须异步、exactly once 返回
  `OUTCOME_FAILED + ERROR_ARCHIVE_QUOTA`、零 compilation payload 与 empty
  output。三条 terminal 路径都等待 1 秒 quiet window 后断言 callback exactly
  once；本切片没有直接证明 plus-one remote transient cleanup；
- 首轮 minified release 运行另暴露两个 production-only blocker：API 24
  non-debuggable compiler 无权读取 procfs FD metadata；API 35
  `hidepid=invisible` 使 supervisor 无法跨 PID capture sibling compiler，导致
  registration retry 与 `ERR_COMPILER_UNAVAILABLE`。最终 FD flags 在 API 30+
  使用 public `Os.fcntlInt(F_GETFL)`，API 24–29 使用四 ABI JNI/libc
  `fcntl(F_GETFL)`；online handshake 只 capture self identity，并以 Binder
  calling UID/PID、固定 process name、双方 boot ID、256-bit token、exact
  descriptor/Binder 与 `linkToDeath` 授权。旧 PID 的 post-death
  `ABSENT/REUSED/UNREADABLE` quarantine observation 仍保留 procfs；
- 最终 no-device 根 `verifyR2PublicPreviewFixture` 为
  `BUILD SUCCESSFUL in 1m 22s`（513 tasks：50 executed、463 up-to-date）；
  定向 P3b-b2c source/APK + release-compatibility gate 为 309 tasks：
  25 executed、284 up-to-date。最终候选 app debug/release 为
  5,894,744 / 2,711,128 bytes，SHA-256 分别为
  `89783d4d52aa9a23a0731a49f40cb14a58aac96150d61d5eb5566ecc6ac28054` /
  `03dd61e71b1071e0b4ead41dd1d9a78f13ce740f15bb036732ef400231e29ddb`；
  fixture debug/release 为 976,559 / 691,274 bytes，SHA-256 分别为
  `8f0d4dff497c1fddafbd6297af39d3f9162a08ea068f07607de85f596b241008` /
  `2136073c16db0172f35ecf42e1254bc4f3c5d2cfcd74d6b0b3e9c3c851c3b029`。
  “minified service release” 只表示 app release 中 service pipeline 经
  R8/minify，不宣称 fixture release 自身 minified。

上述最终候选 artifact 的四行设备 runtime 全部收到 passed stream、
`INSTRUMENTATION_CODE=-1`、ADB exit 0，且 crash/unavailable 命中为 0：

| API | service variant | fixture UID / plugin UID | invalid / inclusive / `+1` timings | runner |
| ---: | --- | --- | --- | --- |
| 24 | debug | `10078 / 10090` | `1,543 / 9,876 / 2,205 ms` | PASS；code -1；ADB 0；wall 13,894 ms |
| 24 | minified service release | `10078 / 10090` | `1,608 / 9,060 / 1,672 ms` | PASS；code -1；ADB 0；wall 12,631 ms |
| 35 | debug | `10314 / 10312` | `1,799 / 19,867 / 1,532 ms` | PASS；code -1；ADB 0；wall 23,706 ms |
| 35 | minified service release | `10314 / 10312` | `1,596 / 7,861 / 1,338 ms` | PASS；code -1；ADB 0；wall 11,218 ms |

最终 debug app 还在 API 24 定向复跑完整
`R2OutputFdCommitterInstrumentationTest`，得到 6/6 PASS、
`INSTRUMENTATION_CODE=-1`、ADB exit 0、wall 622 ms；append-mode admission、
正常 commit、两类 rollback、finalizer 与 unproven fail-closed 均被覆盖。

API 24 为 Android 7.0 + Chrome/WebView `119.0.6045.194`；API 35 为 Android 15 +
Google WebView `130.0.6723.86`。每台设备最后均恢复并重新核对 debug app +
fixture。

P3b-b2c 使 internal provisional checklist 达到 18/18 checked；下方 54 个 public
R2 checkbox 仍为 0/54 checked。该窄矩阵不等于 external exact-64-MiB full
pipeline。production 512 files、8 MiB/file、8 MiB total fence 与四项 capability
flags 均未改变；自然 parse/semantic/emit 中途 fault、自然 ingress timeout、
plus-one remote cleanup、真实 LMK/OOM、Unicode/high-token 与 output 128/256 MiB
也仍开放。完整边界见
[R2-P3b-b2c External Production-fence Pipeline](../architecture/EXTERNAL_FENCE_PIPELINE_R2_P3B_B2C.md)。

2026-07-30 R2-P3b-b2d non-shipping external exact-64-MiB
source/APK/device evidence：

- app 新增 `r2CapacityEvidenceDebug` /
  `r2CapacityEvidenceRelease`；后者继承 ordinary release、经 R8/minify 且
  non-debuggable，是 release-equivalent evidence build。两个 evidence variant
  都声明 TEST_ONLY、使用独立 evidence APK 文件名，并保持 ordinary app
  application ID/signing；安装必须使用 `-t`，它们不是 shipping production
  release。普通 Debug/Release external/public dispatch 继续为
  512 files、8 MiB/file、8 MiB total；
- normal release APK gate 只证明
  `__autojs6_r2_external_capacity_evidence_scope`、
  `stored-container-exact-64-v1` 与 `deflated-expanded-exact-64-v1` 三个 token
  不存在，不宣称 enum literal 或其他广义 evidence marker 全部消失；
- service 不接受 generic expanded bit，只在 evidence build、public base
  authority、固定 project/entry、distinct Binder calling UID、唯一 TEST_ONLY
  fixture package、同签名、scope 与 frozen FD size 全部匹配时派生
  `EXTERNAL_CAPACITY_EVIDENCE` control。STORED scope 在 admission 时认证
  container exact 64 MiB；DEFLATED scope 只认证 container 位于 `1..64 MiB`，
  expanded exact-64 由 fixture 两遍自检与 runtime terminal 共同证明。两个合法
  `+1` rejection 不携带 capacity scope；
- standalone fixture 以 4 KiB `main.ts` + 八个 padding `.d.ts`、
  manifest/source fixed point 和显式两遍 ZIP32 writer 生成 STORED container
  `67,108,864` / `67,108,865` bytes，以及 DEFLATED expanded
  `67,108,864` / `67,108,865` bytes。每行先回归 b2c invalid ZIP、ordinary
  8 MiB inclusive/`+1`，再运行四条 capacity boundary，最后运行 ordinary
  8 MiB inclusive successor；
- 最终 normal app debug/release 为 5,089,165 / 2,711,128 bytes，SHA-256
  分别为
  `83fca363539ae0a4b8e0fc1562d81b4e92b97d1230c2ecfc384b13f157862168` /
  `4c51525c05ed5be116d2c90bf59cd8a4eb7559477ead516f7a90534c9076afb5`；
  TEST_ONLY evidence debug/release-equivalent 为
  5,072,657 / 2,711,140 bytes，SHA-256 分别为
  `70a22016ba9465026cc0ee7491aceb975b12cdf07139e6b3dadb7ea7212441bb` /
  `602437f32ccbe0b835d6dada2a42f562479d46483ddb8fdc675b10aa44cc45fd`；
  fixture debug/release 为 998,116 / 706,034 bytes，SHA-256 分别为
  `e71741788c026a3e52e3f2153b657ae83337a562fdec4840ea6d2197f232dd6d` /
  `76c4b289c9a81172a6c247a51fd0f42d324ed04d55f2fcfae89d7b5a9b4f9bb6`；
- source/APK/manifest/signing/file-name/minified-mapping local gate 已通过；最终
  根 `verifyR2PublicPreviewFixture` aggregate 为 `BUILD SUCCESSFUL in 49s`
  （604 actionable tasks：61 executed、543 up-to-date）。这是加入 b2d gate
  后的最终结果，不与上一个 b2c 历史切片的 513-task aggregate 混用；
  API 24 / Android 7 x86 / Chrome-WebView `119.0.6045.194` 的最终
  fixture/plugin UID 为 `10078/10090`。TEST_ONLY evidence debug 四边界为
  STORED exact `67,108,864 / 67,107,642 → 7,551 B`（40,863 ms）、
  STORED `+1` `67,108,865 / 67,107,643`（`CONTAINER_BYTES`、0 B、
  3,577 ms）、DEFLATED exact
  `25,804,055 / 67,108,864 → 7,561 B`（38,840 ms）、DEFLATED `+1`
  `25,804,054 / 67,108,865`（`UNCOMPRESSED_BYTES`、0 B、5,922 ms）；
  successor 9,172 ms，runner PASS、code -1、ADB 0、wall 113,715 ms；
- API 24 TEST_ONLY minified/non-debuggable release-equivalent 的四边界耗时为
  21,345 / 3,100 / 20,680 / 5,307 ms，successor 6,364 ms，runner PASS、
  code -1、ADB 0、wall 68,213 ms。normal non-TEST_ONLY Debug/Release no-arg
  b2c 分别 wall 15,369 / 10,510 ms，均 PASS 且无 capacity event。最终恢复
  normal debug + fixture debug，plugin/fixture UID `10090/10078` 与 manifest
  flags 均正确；
- API 35 / Android 15 arm64 / Google WebView `130.0.6723.86` 的最终
  fixture/plugin UID 为 `10314/10312`。TEST_ONLY evidence debug 四边界为
  STORED exact `67,108,864 / 67,107,642 → 7,551 B`（106,932 ms）、
  STORED `+1` `67,108,865 / 67,107,643`（`CONTAINER_BYTES`、0 B、
  2,449 ms）、DEFLATED exact
  `25,804,054 / 67,108,864 → 7,561 B`（107,900 ms）、DEFLATED `+1`
  `25,804,054 / 67,108,865`（`UNCOMPRESSED_BYTES`、0 B、4,605 ms）；
  successor 17,897 ms，runner PASS、code -1、ADB 0、wall 263,569 ms；
- API 35 TEST_ONLY minified/non-debuggable release-equivalent 的四边界为
  STORED exact/`+1` 38,185 / 1,790 ms，DEFLATED exact
  `25,804,054 / 67,108,864 → 7,561 B`（41,934 ms），DEFLATED `+1`
  `25,804,056 / 67,108,865`（`UNCOMPRESSED_BYTES`、0 B、4,111 ms）；
  successor 8,318 ms，runner PASS、code -1、ADB 0、wall 106,751 ms。
  normal non-TEST_ONLY Debug/Release no-arg b2c case timings 分别为
  `1,842 / 17,878 / 1,555 ms`（wall 21,787 ms）和
  `1,604 / 8,997 / 1,349 ms`（wall 12,354 ms），均 PASS、无 capacity event，
  全部 failure scan 为空。最终恢复 normal debug + fixture debug，app UID
  `10312` 为 debuggable/non-testOnly，fixture UID `10314` 为
  debuggable/testOnly，且无残留相关 process。

P3b-b2d 使 internal provisional evidence 达到 **19/19**；下方 54 个 Public R2
checkbox 仍为 **0/54**，四项 capability 仍全部为 `false`。
它只证明 external exact-64 full-pipeline 四边界与 ordinary b2c regression，
不证明 remote plus-one cleanup、自然 parse/semantic/emit/ingress timeout、
LMK/OOM、Unicode/high-token/adversarial、output 128/256 MiB、formal schemas、
tsconfig/NodeNext。完整边界见
[R2-P3b-b2d External Exact-64-MiB Capacity Evidence Pipeline](../architecture/EXTERNAL_EXACT64_PIPELINE_R2_P3B_B2D.md)。

2026-07-30 R2-P3b-b2e natural ingress timeout/final cleanup evidence：

- 新增
  `verifyR2P3bB2eNaturalIngressTimeoutSourcePolicy` 与
  `verifyR2P3bB2eNaturalIngressTimeoutAndroidTestApk`；前者锁定固定 5/10 秒
  deadline、零 spin/no stop、partial ingress/cursor/Worker/session/host cleanup、
  Binder public ACK/timeout/zero-output/exactly-once/final transient cleanup 与
  successor，后者从最终 androidTest DEX 重验两项方法与关键 assertion/log marker。
  定向 source gate 为 `BUILD SUCCESSFUL in 13s`（7 actionable tasks：3 executed、
  4 up-to-date）；最终 APK gate 为 `BUILD SUCCESSFUL in 12s`（113 actionable
  tasks：16 executed、97 up-to-date）；
- 加入 b2e 两项 gate 后，最终根 `verifyR2PublicPreviewFixture` 为
  `BUILD SUCCESSFUL in 1m 7s`（606 actionable tasks：82 executed、
  524 up-to-date）。同轮 desktop 4,607-file ingress 为 372 ms，512 MiB
  old-space retained heap baseline/boundary/replay 仍为
  `22.6 / 23.7 / 24.0 MiB`；lint、debug/release APK、R1 compatibility 与
  public fixture gate 全部通过；
- 最终 ordinary debug app 为 5,089,165 bytes，SHA-256
  `83fca363539ae0a4b8e0fc1562d81b4e92b97d1230c2ecfc384b13f157862168`；
  androidTest APK 为 1,491,255 bytes，SHA-256
  `696197b5158c3adb34287cd84ef309f228de5560d172535952d819f25257a26a`；
- `emulator-5556` / Android 7.0 / API 24 / Chrome-WebView
  `119.0.6045.194` 的 direct-host 三轮 accepted/total bytes 为
  `12,976,128 / 70,893,622`、`13,893,632 / 70,893,622`、
  `14,024,704 / 70,893,622`；cursor opened/closed 各为 `2/2`，Worker
  generation 为 `1/2/3`，runner `OK (1 test)` / 20.131 s。Binder exact
  container `67,108,864` bytes 的 terminal/wall elapsed 为
  `10,338 / 10,358 ms`，runner `OK (1 test)` / 24.704 s；
- `968e9f18` / Android 15 / API 35 / Google WebView `130.0.6723.86`
  的 direct-host 三轮 accepted/total bytes 为
  `3,604,480 / 70,893,622`、`3,538,944 / 70,893,622`、
  `4,259,840 / 70,893,622`；cursor opened/closed 各为 `1/1`，Worker
  generation 为 `1/2/3`，runner `OK (1 test)` / 19.218 s。Binder terminal/
  wall elapsed 为 `10,324 / 10,333 ms`，runner `OK (1 test)` / 37.831 s；
- 四个定向 device run 全部 PASS，命令只访问上述两个明确 serial。direct 用例
  证明 exact-64-MiB partial ingress natural timeout、cursor/Worker/session/host
  cleanup 与 successor；Binder 用例证明 exact64 end-to-end natural deadline、
  public ACK、timeout terminal、empty output、exactly-once 与 terminal 后最终
  coordinator/named/remote transient cleanup。Binder deadline 可能在
  staging/validation/spool 或 host ingress 到期，不外推为 Binder partial-ingress
  证据，也不单独声称 cleanup-before-callback ordering；
- P3b-b2e 使 internal provisional evidence 达到 **20/20**；54 个 Public R2
  checkbox 仍为 **0/54**，四项 capability 仍全部为 `false`，production dispatch
  继续为 512 files、8 MiB/file、8 MiB total。在 b2e 当时仍开放 remote
  plus-one cleanup、自然 parse/semantic/emit 中途 fault、真实 LMK/OOM、
  Unicode/high-token/adversarial、output 128/256 MiB、formal schemas、
  tsconfig/NodeNext 与其余 exit/device gates。完整边界见
  [R2-P3b-b2e Natural Ingress Timeout and Cleanup](../architecture/WORKER_NATURAL_INGRESS_TIMEOUT_R2_P3B_B2E.md)。

2026-07-30 R2-P3b-b2f external plus-one cleanup receipt evidence：

- 新增
  `verifyR2P3bB2fExternalPlusOneCleanupSourcePolicy` 与
  `verifyR2P3bB2fExternalPlusOneCleanupApks`。source gate 锁定 exact TEST_ONLY
  external caller、三类 scope-free `+1`、post-return/pre-ACK FD/lease ownership、
  receipt-before-callback/read-after-callback ordering、five-zero convergence、
  same Binder/safe process epoch 与 ordinary successor；APK gate 重验 fixture
  Debug/Release、evidence Debug/release-equivalent marker，要求 shipping normal
  release 不含 cleanup prefix/class/epoch-domain marker，并要求最终 minified
  evidence mapping 存在。修复 device-discovered hardening 后的定向构建为
  `BUILD SUCCESSFUL in 1m 22s`（412 actionable tasks：63 executed、
  349 up-to-date）；最终根 `verifyR2PublicPreviewFixture` 为
  `BUILD SUCCESSFUL in 1m 32s`（608 actionable tasks：68 executed、
  540 up-to-date），desktop 4,607-file ingress 为 3,176 ms，512 MiB
  old-space retained heap baseline/boundary/replay 为
  `22.6 / 23.7 / 24.0 MiB`；
- API 24 同步 `startCompilation` transaction 在返回前可能暂时持有 incoming
  descriptor duplicate；pre-ACK snapshot 因而改为 ticket 返回后、ACK 前最多
  80 ms 的只读 `getCompilerInfo()` sampling，非 exact/unavailable candidate 不缓存，
  exact `1/1` 才发布。API 35 cold supervisor 可能暂时返回
  compiler unavailable；fixture 改为在同一个原始 bound Binder 上 bounded
  retry，不通过 rebind/新 process 绕过 same-Binder 证明；
- 最终 artifact：

  | artifact | bytes | SHA-256 |
  | --- | ---: | --- |
  | normal debug | 5,593,036 | `7db1fbaf6041fcaeb90055c8126e4331e1e7b9671a88bf3397fe88525f3c9726` |
  | normal release | 2,711,128 | `42c7b5a4dee4b64f344088510978bbb9944b835bd48c93ade54aa1f74bf88a13` |
  | TEST_ONLY evidence debug | 5,560,679 | `552d9c315b7cd654b9b7ef463ee99168c3cb5f2ae79ee3f0eeb5c27efa99e503` |
  | TEST_ONLY evidence release-equivalent | 2,711,140 | `dcbbea9d31609e883d366508f394ca828aeb026cc5e1e82bcac04cd5ef88cfe2` |
  | fixture debug | 1,012,953 | `ce514c781128896c7cd966926a466a5ec176f07f86e71928df5380b567ef0583` |
  | fixture release | 717,950 | `d2df097faa675e39cc02de8bac4199a5b08b81d58b5343ac750a7b2f5b75685c` |

  六包共同 V2 signer certificate SHA-256 为
  `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`。
  normal Debug/Release 均非 TEST_ONLY、debuggable 分别为 true/false；
  evidence 与 fixture Debug/Release 均为 TEST_ONLY、debuggable 分别为
  true/false；
- 最终 runtime：

  | API / provider | normal debug / release | TEST_ONLY evidence debug / release-equivalent |
  | --- | --- | --- |
  | API 24 / Chrome `119.0.6045.194` | `18.427 / 8.274 s` | `38.726 / 30.570 s` |
  | API 35 / WebView `130.0.6723.86` | `23.002 / 10.893 s` | `70.929 / 35.879 s` |

  8/8 均 PASS、无 crash/ANR；API 24 fixture/plugin UID 为
  `10078/10090`，API 35 为 `10314/10312`。normal 行证明 ordinary
  production-fence `+1` + successor black-box recovery；每条 evidence
  ordinary/STORED/DEFLATED case 都证明 pre-ACK active lease `1`、input/output
  exact-target FD `1/1`、pre-callback receipt 全零、callback 后连续 5 个
  all-zero sample、同 Binder/safe epoch，并完成 ordinary successor；
- P3b-b2f 使 internal provisional evidence 达到 **21/21**；54 个 Public R2
  checkbox 仍为 **0/54**，四项 capability 仍全部为 `false`，ordinary
  production dispatch 继续为 512 files、8 MiB/file、8 MiB total。该切片当时仍开放
  自然 parse/semantic/emit 中途 fault、真实 LMK/OOM、Unicode/high-token/
  adversarial、output 128/256 MiB、formal schemas、tsconfig/NodeNext 与其余
  exit/device gates。完整边界见
  [R2-P3b-b2f External Plus-one Cleanup Receipt Evidence](../architecture/EXTERNAL_PLUS_ONE_CLEANUP_R2_P3B_B2F.md)。

2026-07-31 R2-P3b-b2g ordinary natural parse/semantic/emit cancellation
evidence：

- 新增 `projectPhaseProgress` bounded internal telemetry。Worker 只在真实
  parse/semantic/emit work unit 后发布每阶段最多两个 marker；page 在同一 task
  冻结最后 marker 后调用 exact `Worker.terminate()`，native 只有在
  request/session/Worker/phase/sequence selection 与 receipt 完全一致时才发布
  termination evidence。旧 cancel/timeout 没有 marker 时继续使用
  `none/0/0/0`；普通 start frame 未增加 evidence flag；
- deterministic workload 固定为 108 declarations + 192 roots = 300 files、
  4,594,999 bytes、232,249 scanner tokens。TypeScript 6.0.3 baseline 为零
  diagnostics、192 JS + 192 maps、562,945 output bytes，aggregate SHA-256 为
  `2f4dc949984424bb6eff1d806efcac2fd21f4ae8ad87a0c44f03f2c086d1444d`；
- Android matrix 在 ordinary same-UID Debug、`workerSpinMilliseconds=0`、
  `workerSpinPhase=none`、无 timeout 注入下，分别等待 parse `48/192`、
  semantic `48/192`、emit `96/384` 后 keyed cancel。每案均返回
  `CANCELLED`，300/300 cursor 平衡并 released，exact Worker generation
  `1→2→3→4` 依次替换，小项目 successor 成功，最终 host/spool cleanup 可证明；
- 最终 device runtime：

  | API / provider | parse / semantic / emit | runner |
  | --- | --- | ---: |
  | API 24 / Chrome `119.0.6045.194` | `48/192` / `48/192` / `96/384` | `OK (1 test)` / `32.635 s` |
  | API 35 / WebView `130.0.6723.86` | `48/192` / `48/192` / `96/384` | `OK (1 test)` / `66.169 s` |

  六案全部记录 `NATURAL_PHASE_CANCEL_PASS`，两行都记录
  `NATURAL_PHASE_CANCEL_MATRIX_PASS`，且
  `ordinaryAdmission=true`、`hostCleanupProven=true`、
  `spoolCleanupProven=true`；
- API 35 首次 preflight 曾将新 androidTest APK 与旧主应用 APK 配对，初始化期
  因缺少新 runtime class 返回 `NoClassDefFoundError`。重建并安装同一次构建的
  主包/测试包后最终运行通过；成对 APK gate 因此同时依赖 `assembleDebug` 与
  `assembleDebugAndroidTest`，并分别检查主包 runtime DEX 与测试包 fixture DEX
  marker；
- 本地 `:app:testDebugUnitTest`、`:app:compileReleaseKotlin`、
  `:app:compileDebugAndroidTestKotlin`、`:app:verifyR2ProjectCompilerDesktop`、
  b2g source policy 与成对 APK gate 全部 PASS；root aggregate
  `verifyR2PublicPreviewFixture` 也以 610 actionable tasks
  （110 executed、500 up-to-date）在 2m 2s 内 PASS。P3b-b2g 使 internal
  provisional evidence 达到 **22/22**；54 个 Public R2 checkbox 仍为
  **0/54**，四项 capability 仍全部为 `false`，ordinary production dispatch
  继续为 512 files、8 MiB/file、8 MiB total。当前仍开放自然
  parse/semantic/emit timeout、external/Binder/Release natural cancellation、
  repeated PSS/FD、真实 LMK/OOM、Unicode/high-token/adversarial、output
  128/256 MiB、formal schemas、tsconfig/NodeNext 与其余 exit/device gates。
  完整边界见
  [R2-P3b-b2g Natural Parse/Semantic/Emit Cancellation](../architecture/WORKER_NATURAL_PHASE_CANCELLATION_R2_P3B_B2G.md)。

2026-07-31 R2-P3b-b2h ordinary natural semantic request-start
absolute-deadline timeout evidence：

- 新增独立 512-file ordinary workload 与 TypeScript 6.0.3 desktop authority。
  每台设备先运行三次完整成功 baseline；每次均为零 diagnostics、512 outputs、
  720,866 output bytes，aggregate SHA-256 为
  `4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1`，
  并证明 512/512 cursor 平衡 released 与 session retirement；
- 校准只选择 semantic。strict lower/upper 分别来自三次 request-start-relative
  sequence-2 的最大值之后与 completed 的最小值之前；窗口必须保留双边至少
  100 ms，timeout 冻结为 midpoint。baseline 后仅运行一个 fault target，不
  `requestStop`、不 retry、不 spin、不启用 expanded admission；
- request 开始时以 monotonic `elapsedRealtimeNanos` 计算唯一 absolute
  deadline。等待被 interrupt 时继续针对同一 deadline 并最终恢复 interrupt
  flag；completion 与 deadline 的 race 统一通过 operation-handle terminal
  selection，不能由 interrupt 伪造提前 timeout，也不能由晚到 completion
  绕过已选 timeout；
- 最终 device runtime：

  | API | strict window / timeout | margins | overshoot | result |
  | --- | ---: | ---: | ---: | --- |
  | API 24 | `9,331..12,777 / 11,054 ms` | `1,723 / 1,723 ms` | `2 ms` | PASS |
  | API 35 | `37,725..38,828 / 38,276 ms` | `551 / 552 ms` | `0 ms` | PASS |

  两案均在 semantic sequence 2 `64/256` 后、semantic completed 前返回
  `ERROR_TIMEOUT`、`compilation=null` 与 `TIMED_OUT`，Worker generation
  `1→2`，小项目 successor、host close 与 spool cleanup 全部通过；runner
  总时间分别为 API 24 `62.476 s`、API 35 `168.226 s`；
- 终审发现并关闭 completion path 先读 clock、再在另一临界区提交
  `COMPLETED` 的 TOCTOU。现在 operation handle 在自己的同一临界区内调用
  monotonic clock supplier，并原子选择 `COMPLETED` 或 `TIMED_OUT`；latch
  并发回归证明 deadline 边界的 timeout 先于竞争 cancellation。修复后的
  interrupt/absolute-deadline instrumentation 在 API 24/35 又分别以
  `1.641 / 1.595 s` PASS；
- 修复后的 b2h source + paired-APK 定向门禁为
  `BUILD SUCCESSFUL in 1m 4s`（140 actionable tasks：20 executed、
  120 up-to-date）；最终根 `verifyR2PublicPreviewFixture` 为
  `BUILD SUCCESSFUL in 1m 45s`（612 actionable tasks：78 executed、
  534 up-to-date）；
- P3b-b2h 使 internal provisional evidence 达到 **23/23**；54 个 Public R2
  checkbox 仍为 **0/54**，四项 capability 仍全部为 `false`。当前仍开放
  natural parse/emit timeout、external-UID/Binder/shipping Release semantic
  timeout、pressure/LMK/OOM、Unicode/high-token/adversarial、output
  128/256 MiB、formal schemas、tsconfig/NodeNext 与其余 exit/device gates。
  完整边界见
  [R2-P3b-b2h Natural Semantic Request-start Absolute-deadline Timeout](../architecture/WORKER_NATURAL_SEMANTIC_TIMEOUT_R2_P3B_B2H.md)。

2026-07-31 R2-P3b-b2i ordinary natural parse request-start absolute-deadline
timeout evidence：

- 新增独立 8-root ordinary parse workload 与 TypeScript 6.0.3 desktop
  authority。最终 authority 为 108 trusted declarations + 8 roots + 1 ambient
  declaration，共 117 files、4,879,527 bytes、257,388 scanner tokens 与
  418 transport frames；resolver depth 为 8、source path depth 为 10，
  aggregate path chars 为 4,434，单 path 最大为 65 chars；
- 每次完整成功精确生成 8 JS + 8 maps；JS 为 1,524 bytes、maps 为
  1,814 bytes，总 output 为 3,338 bytes，aggregate SHA-256 为
  `c16cf1e19354655321bc7bd13bea9840900f2f2b2bf67793b4d1b926ee42e6b3`。
  每台设备先运行两次完整成功 precondition 稳定环境，但不把它们计入 timing；
  随后三次完整成功 baseline 形成 parse sequence-2/completed strict
  intersection，冻结 midpoint 后只运行一次 fault target；
- 最终 device runtime：

  | API / provider | precondition parse `seq2/completed` | baseline parse `seq2/completed` | strict window / timeout | margins / overshoot | runner |
  | --- | --- | --- | ---: | ---: | ---: |
  | API 24 / Chrome `119.0.6045.194` | `2,593/6,567`, `2,501/6,570 ms` | `2,452/6,281`, `2,413/5,949`, `2,474/6,113 ms` | `2,475..5,949 / 4,212 ms` | `1,737/1,737 / 0 ms` | `50.885 s` |
  | API 35 / WebView `130.0.6723.86` | `11,578/13,521`, `11,034/13,293 ms` | `11,715/13,909`, `11,893/13,896`, `11,199/13,164 ms` | `11,894..13,164 / 12,529 ms` | `635/635 / 0 ms` | `89.718 s` |

  两个 fault 均在 parse sequence 2 `2/8` 后、parse completed 前返回
  `ERROR_TIMEOUT`、`compilation=null` 与 `TIMED_OUT`；117/117 cursor
  平衡并 released，fault session generation 均为 6，Worker generation
  `1→2`，小项目 successor、host close 与 spool cleanup 全部通过。测试驱动
  不调用 stop，不 retry、不注入 spin、不使用 expanded admission；
- workload 选择过程保留失败证据而不将其写成成功：depth-10 在 API 24 的首个
  precondition 中发生 renderer V8 OOM，fault 尚未开始；depth-2 在 API 35
  baseline 后得到 empty strict window，fault 尚未开始；compact depth-6 在
  API 24 OOM，compact depth-4 在 API 35 只有 `384 ms` raw window，未选为
  最终 authority。最终 depth-8 才进入并完成上述成对矩阵；
- b2i source policy gate 为 `BUILD SUCCESSFUL in 1m 1s`
  （74 actionable tasks：7 executed、67 up-to-date）；成对 APK gate 为
  `BUILD SUCCESSFUL in 38s`（141 actionable tasks：20 executed、
  121 up-to-date）。最终 app APK 为 5,604,818 bytes、SHA-256
  `76d67804ce5c02182f55826ed4e941fef05cb3f1eb19fef45b8e1c1cde3a3e5c`；
  androidTest APK 为 1,531,268 bytes、SHA-256
  `ade636dccfe87ce6055d7a1a38d97cc71d894a0b0375b59507a16c552ecfc92b`；
- 最终根 `verifyR2PublicPreviewFixture` 为 `BUILD SUCCESSFUL in 1m 50s`
  （614 actionable tasks：98 executed、516 up-to-date）。P3b-b2i 使
  internal provisional evidence 达到 **24/24**；54 个 Public R2 checkbox
  仍为 **0/54**，四项 capability 仍全部为 `false`，ordinary production
  dispatch 继续为 512 files、8 MiB/file、8 MiB total。当前仍开放 natural
  emit timeout、external-UID/Binder/shipping Release parse/semantic timeout、
  pressure/LMK/OOM、Unicode/high-token/adversarial、output 128/256 MiB、
  formal schemas、tsconfig/NodeNext 与其余 exit/device gates。完整边界见
  [R2-P3b-b2i Natural Parse Request-start Absolute-deadline Timeout](../architecture/WORKER_NATURAL_PARSE_TIMEOUT_R2_P3B_B2I.md)。

2026-07-31 R2-P3b-b2j ordinary natural emit request-start absolute-deadline
timeout evidence：

- 新增独立 TypeScript 6.0.3 / ES2018 ordinary emit workload 与 desktop
  authority。最终 authority 为 108 trusted declarations + 8 roots（2 light +
  6 heavy），共 116 files、3,841,832 bytes、110,639 scanner tokens 与
  400 transport frames；每个 heavy root 含 18 条 depth-110 optional chain，
  aggregate root 为 57,074 bytes / 24,310 tokens，单 root 最大为
  9,502 bytes / 4,049 tokens，aggregate path chars 为 4,145，单 path
  最大为 46 chars；
- 每次完整成功精确生成 8 JS + 8 maps；JS 为 769,510 bytes、maps 为
  196,204 bytes，总 output 为 965,714 bytes，aggregate SHA-256 为
  `e7eac5f110b4dff7bbb732ea47c3f21c083b44fa215b740895a8e8bac9ba4fa6`。
  距 1 MiB output ceiling 保留 82,862 bytes，比 64 KiB 多 17,326 bytes。
  每台设备先运行两次完整成功 precondition 稳定环境，但不把它们计入 timing；
  随后三次完整成功 baseline 形成 emit sequence-2/completed strict
  intersection，冻结 midpoint 后只运行一次 fault target；
- 这些 baseline/fault 在 b2j source state 的旧 completion boundary 上运行：emit
  在 `builder.emit()` 后、result start 前完成。b2k 当前二进制已把 emit completion
  延伸到 ACK-backpressured result egress 的最终 ACK；所以下表只保留为历史
  direct-host evidence，不是当前二进制 timing authority，当前证据由紧随其后的
  b2k 四行矩阵取代；
- 历史 device runtime（旧 completion boundary）：

  | API / provider | precondition emit `seq2/completed` | baseline emit `seq2/completed` | strict window / timeout | margins / overshoot | runner |
  | --- | --- | --- | ---: | ---: | ---: |
  | API 24 / Chrome-WebView `119.0.6045.194` | `5,160/6,829`, `4,509/6,088 ms` | `4,475/5,938`, `4,585/6,050`, `4,520/5,879 ms` | `4,586..5,879 / 5,232 ms` | `646/647 / 2 ms` | `45.271 s` |
  | API 35 / WebView `130.0.6723.86` | `11,547/12,471`, `10,956/11,904 ms` | `11,057/11,876`, `10,923/11,902`, `10,779/11,638 ms` | `11,058..11,638 / 11,348 ms` | `290/290 / 1 ms` | `84.526 s` |

  两个 fault 在当时均于 emit sequence 2 `4/16` 后、emit completed 前返回
  `ERROR_TIMEOUT`、`compilation=null` 与 `TIMED_OUT`；116/116 cursor
  平衡并 released，fault session generation 均为 6，Worker generation
  `1→2`，小项目 successor、host close 与 spool cleanup 全部通过。测试驱动
  不调用 stop，不 retry、不注入 spin、不使用 expanded admission、synthetic
  calibration 或 phase-relative deadline；每台日志精确为 2 precondition /
  3 baseline / 1 window / 1 fault / 1 matrix marker；
- workload 选择过程保留失败证据而不将其写成成功：depth-200、150 与 125
  optional-chain 候选在 success-only probe 中发生 stack overflow；depth-100
  候选在部分矩阵的 strict window 为空或不足双边 100 ms；将 workload 分散到
  200 roots 的候选也未形成稳定共同 emit tail。所有这些候选均在正式 fault
  前 fail closed。最终 depth-110 × 18 × 6-heavy authority 先完成双设备
  success-only 校准，才进入上述每台唯一一次 fault；
- b2j source policy gate 为 `BUILD SUCCESSFUL in 1m 18s`
  （75 actionable tasks：8 executed、67 up-to-date；实测 `78.600 s`）；
  成对 APK gate 为 `BUILD SUCCESSFUL in 51s`
  （142 actionable tasks：21 executed、121 up-to-date；实测 `51.725 s`）。
  最终 app APK 为 5,604,818 bytes、SHA-256
  `76d67804ce5c02182f55826ed4e941fef05cb3f1eb19fef45b8e1c1cde3a3e5c`；
  androidTest APK 为 1,538,015 bytes、SHA-256
  `9ba37d225afe85047011dd04de7f67fb95f29442a8c5629b020cdc96a01db35c`；
- 最终根 `verifyR2PublicPreviewFixture` 为 `BUILD SUCCESSFUL in 1m 40s`
  （616 actionable tasks：68 executed、548 up-to-date）。
  P3b-b2j 使 internal provisional evidence 达到 **25/25**；54 个 Public R2
  checkbox 仍为 **0/54**，四项 capability 仍全部为 `false`，ordinary
  production dispatch 继续为 512 files、8 MiB/file、8 MiB total。在 b2j
  source state 当时仍开放
  external-UID/Binder/normal Release/release-equivalent/shipping Release
  parse/semantic/emit timeout、pressure/LMK/OOM、Unicode/high-token/adversarial、
  output 128/256 MiB、formal schemas、tsconfig/NodeNext 与其余
  exit/device gates。完整边界见
  [R2-P3b-b2j Natural Emit Request-start Absolute-deadline Timeout](../architecture/WORKER_NATURAL_EMIT_TIMEOUT_R2_P3B_B2J.md)。

2026-08-01 R2-P3b-b2k external-UID Binder natural emit request-start
absolute-deadline timeout evidence：

- 新增 standalone `R2ExternalNaturalEmitTimeoutProjectFixture` 与 exclusive
  `externalNaturalEmitTimeoutEvidence` mode。fixture 必须 same-signer、
  distinct-UID、TEST_ONLY，并经 signature-protected exported compiler service
  完成 accept → pre-ACK ownership → ACK → exactly-once callback；整个矩阵复用
  同一 Binder 与安全 compiler process epoch；
- primary workload 为 256 roots（64 light + 192 heavy）；每个 heavy root 含
  1 条 depth-61 optional chain 与 4 组、每组 90 个 erased `as any` assertions。
  caller 为 256 files / 555,766 bytes / 168,896 scanner tokens；加 108 installed
  declarations 后为 364 files / 4,340,524 bytes / 255,225 tokens / 13,073
  aggregate path chars / 1,144 frames；ordinary 512-file/8-MiB production fence
  未放宽；
- 每个 API 24/35 × evidence Debug/minified non-debuggable evidence Release 行固定
  运行 2 次不计时的成功 precondition、3 次完整成功 baseline、1 次正式 fault 与
  1 个 small successor。strict intersection midpoint 不 clamp，必须自然达到
  public 6,000-ms minimum且保留双边各至少 100 ms；任一前置失败都在
  faultAttempts=0 时 fail closed；
- raw TypeScript authority 为 512 outputs：717,302 JS bytes + 259,136 map
  bytes = 976,438 bytes，SHA-256
  `2b2a4529552aa3a3f9b33cd2a379240bc2852ca065a33ff884a6160847f1b1de`，距
  1 MiB output ceiling 保留 72,138 bytes。fixture 从 packaged modules 重建 raw
  Worker bytes 后验证该 authority，并独立重验完整
  packaged ZIP inventory、diagnostics、Source Map v3、terminal manifest digest、
  provenance/effective options 及每项 payload size/SHA/inventory fingerprint；
- coordinator 新增 exact request-start absolute nanos seam，host 不再从 rounded
  millisecond remainder 重建 deadline。每个 host operation 返回一个 request-
  correlated immutable snapshot；coordinator 在 callback 前生成 private phase
  receipt，成功案含 parse/semantic/emit timing，fault 案含 completed parse/semantic、
  `TIMED_OUT` emit sequence 2 `128/512`、deadline/overshoot、Worker replacement 与
  364/364 cursor telemetry；
- 当前 emit pipeline 覆盖 `builder.emit()`、有界顺序 output hashing、output/
  Source Map 结构验证、result JSON/UTF-8、最终 digest 与 window=1、ACK-backpressured result
  egress。`projectResultStart`/chunks 发生时 emit 仍 active；最终 chunk ACK 后先
  发布 emit `completed`，再发布 `projectResultEnd`。因此 fault 证明 required
  pipeline 在最终 ACK 前被终止，但不声称 instruction-level `builder.emit()` 内部
  中断；`projectResultEnd` 后的 native typed-result finalizer 明确不属于 emit；
- cleanup receipt 独立证明 ACK 前 active lease 与 input/output FD ownership，
  terminal callback 前冻结 cleanup，callback 后连续 5 次 caller/private input、
  spool/private output、named transient、cursor 与 lease 全零。small successor
  为 109 files / 3,784,795 bytes / 86,337 tokens / 379 frames，在 same Binder/
  process epoch 成功并清理；
- 最终 current-binary device matrix：

  | API / provider | evidence variant | baseline emit `seq2/completed` | strict window / timeout | margins / overshoot |
  | --- | --- | --- | ---: | ---: |
  | API 24 / Google WebView `53.0.2785.124` | Debug | `7,977/9,871`, `7,445/9,423`, `7,661/9,556 ms` | `7,978..9,423 / 8,700 ms` | `722/723 / 3 ms` |
  | API 35 / Google WebView `130.0.6723.86` | Debug | `21,048/23,506`, `20,789/23,383`, `21,177/23,671 ms` | `21,178..23,383 / 22,280 ms` | `1,102/1,103 / 1 ms` |
  | API 24 / Google WebView `53.0.2785.124` | minified non-debuggable evidence Release | `5,852/7,632`, `5,935/7,653`, `5,807/7,464 ms` | `5,936..7,464 / 6,700 ms` | `764/764 / 13 ms` |
  | API 35 / Google WebView `130.0.6723.86` | minified non-debuggable evidence Release | `9,988/11,564`, `10,043/11,747`, `9,853/11,486 ms` | `10,044..11,486 / 10,765 ms` | `721/721 / 0 ms` |

  每行先完成 2 次不计时的成功 precondition，再完成表中 3 次完整成功 baseline；
  唯一 fault 的 sequence 2 均为 `128/512`，`faultAttempts=1`、output count `0`、
  364/364 cursor balanced + released，返回 `ERROR_TIMEOUT`、无 compilation，并证明
  exact Worker replacement。每行从 precondition 到 small successor 都保持同一
  Binder/process epoch，cleanup receipt 与随后 5 次 current snapshot 全零；runner
  stdout 每行仅有一个 `INSTRUMENTATION_CODE: -1`；
- source/APK gate 锁定上述 2+3+1/absolute-nanos/receipt/output authority 与禁用
  stop/retry/spin/expanded/synthetic/phase-relative seam；两份 evidence app APK
  必须 TEST_ONLY，Release-equivalent 必须保留 R8 mapping，normal Release DEX
  必须排除 private receipt prefix。上表与
  [R2-P3b-b2k External-UID Binder Natural Emit Timeout](../architecture/EXTERNAL_NATURAL_EMIT_TIMEOUT_R2_P3B_B2K.md)
  共同作为最终四行收据；b2j 的旧 completion-boundary timing 不再作为当前二进制
  authority；
- P3b-b2k 使 internal provisional evidence 达到 **26/26**；54 个 Public R2
  checkbox 仍为 **0/54**，四项 capability 仍全部为 `false`，ordinary production
  dispatch 继续为 512 files、8 MiB/file、8 MiB total。当前仍开放 external-UID/
  Binder parse/semantic timeout、normal/shipping Release parse/semantic/emit timeout、
  pressure/LMK/OOM、Unicode/high-token/adversarial、output 128/256 MiB、formal
  schemas、tsconfig/NodeNext 与其余 exit/device gates。

2026-08-01 无编号 external-UID Binder natural semantic timeout follow-up：

- 在既有 external fixture 中增加独立 `NATURAL_SEMANTIC` workload 与 exclusive
  `externalNaturalSemanticTimeoutEvidence` runner；emit/successor fingerprint 保持
  不变。caller 为 256 roots（64 light + 192 heavy）+ 148 helpers，共 404 files /
  3,942,214 bytes / 65,196 scanner tokens；与 108 installed declarations 合计
  exact 512 files / 7,726,972 bytes / 151,525 tokens / 19,249 aggregate path
  chars / 1,588 frames，未使用 expanded capacity；
- 每次成功固定生成 512 raw outputs：535,202 JS bytes + 185,664 map bytes =
  720,866 bytes，aggregate SHA-256 为
  `4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1`。
  caller inventory fingerprint 为
  `460b3b9755e2669fb789c3190d9f6bc14e42c9f6cbe87bbf81cbe0d97bf6b3eb`；
- 每行先完成 2 次不计时的成功 precondition，再以 3 次完整成功 baseline 的
  semantic sequence-2/completed strict intersection 冻结不 clamp 的 midpoint，
  然后只运行 1 次 fault 与 1 个 small same-Binder successor。正式 device matrix：

  | API / provider | evidence variant | precondition semantic `seq2/completed` | baseline semantic `seq2/completed` | strict window / timeout | fault wall / margins / overshoot |
  | --- | --- | --- | --- | ---: | ---: |
  | API 24 / Chrome-WebView `119.0.6045.194` | Debug | `21,094/30,983`, `12,289/16,897 ms` | `12,362/17,249`, `10,879/16,212`, `11,430/15,805 ms` | `12,363..15,805 / 14,084 ms` | `14,689 / 1,721+1,721 / 3 ms` |
  | API 24 / Chrome-WebView `119.0.6045.194` | minified non-debuggable evidence Release | `9,646/14,234`, `7,946/12,591 ms` | `7,172/11,401`, `7,366/11,775`, `7,417/12,112 ms` | `7,418..11,401 / 9,409 ms` | `9,919 / 1,991+1,992 / 12 ms` |
  | API 35 / Google WebView `130.0.6723.86` | Debug | `37,333/40,332`, `37,426/40,498 ms` | `38,364/41,412`, `38,796/41,791`, `36,360/39,335 ms` | `38,797..39,335 / 39,066 ms` | `39,424 / 269+269 / 0 ms` |
  | API 35 / Google WebView `130.0.6723.86` | minified non-debuggable evidence Release | `13,867/16,840`, `13,233/16,255 ms` | `13,637/16,452`, `13,224/16,265`, `13,639/16,649 ms` | `13,640..16,265 / 14,952 ms` | `15,238 / 1,312+1,313 / 0 ms` |

  四个 fault 均为 `faultAttempts=1`，在 semantic sequence 2 `64/256` 后返回
  `ERROR_TIMEOUT`、`compilation=null`、output count `0`；512/512 cursor balanced +
  released，Worker generation `1→2`；phase timing 只含 parse，history 为 entered
  ingress/parse/semantic 与 completed ingress/parse，semantic 同时为 host/page
  termination phase。每行从 precondition 到 successor
  保持同一 Binder/process epoch，cleanup receipt 与随后 5 次 current snapshot
  全零；runner stdout 各只有一个 `INSTRUMENTATION_CODE: -1`。small successor 为
  109 input files，生成 2 outputs / 312 bytes，raw SHA-256
  `0b504753f68a345d6aa82eeb174f6d9c63eec3cb21b7810ed09f69e59296be25`；
- API 24 曾将 provider 恢复为 Google WebView `53.0.2785.124` 做一次探索运行；
  当前 `compiler-host.js` 在 line 6 触发 `Uncaught SyntaxError: Unexpected identifier`，
  首个 precondition 自然等待到 `120,035 ms` timeout，runner 返回
  `INSTRUMENTATION_CODE: 0` 且 `faultAttempts=0`。该结果只作为 provider
  incompatibility 的 fail-closed 记录，不是成功 precondition、timing sample 或
  正式矩阵结果；正式 API 24 provider 因而冻结为 Chrome-WebView 119；
- 最终重建 artifact：evidence Debug app 为 5,647,700 bytes / SHA-256
  `b4ef844810d23b954f96856289c602f14e2a618d043d9cc8f087cefd3c1b1f53`，evidence
  Release app 为 2,729,000 bytes /
  `e9fd53caae16b08b00ae32d366baac3759f5beab3fa1f10c29fb97909253a78c`；fixture
  Debug/Release 分别为 1,110,581 bytes /
  `c1eb953d2230aea801bb8350052f40b34ba93b069b701748a01bbfb1621517f0` 与
  740,786 bytes /
  `b920d09d6cbdedb32cda99bc42fb72f2b68a20d28558bc8faa6e54a549e23139`；
  evidence Release R8 mapping 为 5,646,401 bytes /
  `67ab866d274026fd9eed0bbdfd30ae5bd466b1561ada586b01e060ec87dceeb0`。
  source/APK gates 与 semantic receipt JVM tests 均通过；
- evidence Release 仍是 TEST_ONLY、R8/minified、non-debuggable 的
  Release-equivalent build，**不是** normal/shipping Release。该 follow-up 使
  internal provisional evidence 达到 **27/27**；54 个 Public R2 checkbox 仍为
  **0/54**，四项 capability 仍全部为 `false`，ordinary production dispatch 继续
  为 512 files、8 MiB/file、8 MiB total。下一个显著缺口是 external-UID/Binder
  natural parse timeout 与 normal/shipping Release parse/semantic/emit timeout；
  pressure/LMK/OOM、Unicode/high-token/adversarial、output 128/256 MiB、formal
  schemas、tsconfig/NodeNext 与其余 exit/device gates 也仍开放。完整边界见
  [External-UID Binder Natural Semantic Timeout](../architecture/EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_R2.md)。

2026-08-01 无编号 external-UID Binder natural parse timeout follow-up：

- 在既有 external fixture 中增加独立 `NATURAL_PARSE` workload 与 exclusive
  `externalNaturalParseTimeoutEvidence` runner；successor authority 保持不变。caller
  为 32 roots + 1 ambient declaration + 371 reference-only declarations，共
  404 files / 4,392,959 bytes / 150,203 tokens；与 108 installed declarations
  合计 exact 512 files / 8,177,717 bytes / 236,532 tokens / 29,343 aggregate path
  chars / 1,598 frames，未使用 expanded capacity；
- 15,000 个 type-only imports 固定为 sequence-2 前 9,000 与 root 8 后 6,000；
  371-file complete DAG 从 graph370 进入且只反向引用已发现节点，保留 68,635 条
  exact 50-byte edges，同时避免 WebView 130 线性递归栈。caller fingerprint 为
  `334b1a3a940a741105c5222ca6c189c72dfb6c0ea994a72b86013df1fdbf72ef`；
- 每次成功固定生成 64 raw outputs：5,962 JS bytes + 7,193 map bytes =
  13,155 bytes，aggregate SHA-256 为
  `18f79093d9a09c3111740e282a3ad642576230c142d6b7d7c61bb9c7950ca038`；
- 每行先完成 2 次成功 precondition，再以 3 次完整成功 baseline 的 parse
  sequence-2/completed strict intersection 冻结不 clamp 的 midpoint，然后只运行
  1 次 fault 与 1 个 small same-Binder successor。正式 device matrix：

  | API / provider | evidence variant | baseline parse `seq2/completed` | strict window / timeout | margins / overshoot |
  | --- | --- | --- | ---: | ---: |
  | API 24 / Chrome `119.0.6045.194` | Debug | `8,671/11,265`, `8,785/11,364`, `9,295/11,720 ms` | `9,296..11,265 / 10,280 ms` | `984+985 / 8 ms` |
  | API 24 / Chrome `119.0.6045.194` | minified non-debuggable evidence Release | `6,319/8,783`, `6,093/8,464`, `5,857/8,465 ms` | `6,320..8,464 / 7,392 ms` | `1,072+1,072 / 57 ms` |
  | API 35 / WebView `130.0.6723.86` | Debug | `34,485/35,859`, `34,281/35,738`, `34,251/35,566 ms` | `34,486..35,566 / 35,026 ms` | `540+540 / 0 ms` |
  | API 35 / WebView `130.0.6723.86` | minified non-debuggable evidence Release | `13,358/15,001`, `14,492/16,083`, `14,020/15,515 ms` | `14,493..15,001 / 14,747 ms` | `254+254 / 0 ms` |

  四个 fault 均为 `faultAttempts=1`，在 parse sequence 2 `8/32` 后返回
  `ERROR_TIMEOUT`、`compilation=null`、output count `0`；512/512 cursor balanced +
  released，Worker generation `1→2`；completed phase timing 为空，history 为 entered
  ingress/parse 与 completed ingress，parse 同时为 host/page termination phase。
  每行保持同一 Binder/process epoch，cleanup receipt 与随后 5 次 current snapshot
  全零；small successor 为 109 input files / 2 outputs / 312 bytes；
- ticket 返回后、ACK 前的 ownership 允许最多 80 ms、每 10 ms 一次的只读 metadata
  sampling；非 exact/unavailable candidate 不缓存，exact lease `1` + FD `1/1` 才
  发布。API 24 Debug successor 实际以 2 samples / 18 ms 收敛；该 sampling 小于
  100 ms calibration margin，且不重发 admission/fault；
- 原始全-pre imports 候选在 API 24 evidence Release 只形成 178 ms strict
  intersection，`faultAttempts=0`；重排后的 forward-DAG 候选又在 API 35 首个
  precondition 因 `Maximum call stack size exceeded` fail closed，同样没有 baseline
  或 fault。最终 reverse-index DAG 才作为正式 artifact；
- 最终 artifact：evidence Debug app 5,647,904 bytes /
  `9991077490a9078870b276cef866e06daacc0dfd90c9d168007cd76ea196f169`；evidence
  Release app 2,729,000 bytes /
  `52cd37cf6e24dbe586dddf10dfce809ce4ec056c1841181d07b8652729fa7291`；fixture
  Debug/Release 分别为 1,125,367 bytes /
  `baf91243d9524cea6f571f9e30edd0c08a3323931a09f3b0bf626cb38b8f7175` 与
  744,870 bytes /
  `3fbab9ba6ca0cc2b8e4e594ecea717d1547ca6f24b60376ebd6da3d16f058b7f`；mapping
  5,648,137 bytes /
  `aadd181583c722af95659a5238f9a0d004bf056f06129a32a5a44775d2a4c18d`；
- evidence Release 仍是 TEST_ONLY、R8/minified、non-debuggable 的
  Release-equivalent build，**不是** normal/shipping Release。该 follow-up 使
  internal provisional evidence 达到 **28/28**；54 个 Public R2 checkbox 仍为
  **0/54**，四项 capability 仍全部为 `false`。当前仍开放 normal/shipping Release
  parse/semantic/emit timeout、pressure/LMK/OOM、Unicode/high-token/adversarial、
  output 128/256 MiB、formal schemas、tsconfig/NodeNext 与其余 exit/device gates。
  完整边界见
  [External-UID Binder Natural Parse Timeout](../architecture/EXTERNAL_NATURAL_PARSE_TIMEOUT_R2.md)。
- 最终无设备聚合同时执行 app/API JVM tests、desktop TypeScript `6.0.3` golden、
  compiler core 与完整 preview APK gate，结果为 `BUILD SUCCESSFUL in 1m 20s`
  （519 actionable tasks：59 executed、460 up-to-date）。旧 `b2g` source policy
  的提取终点已限定在自身函数之后，不再把后续 timeout 场景的进度 tuple 混入
  其冻结的 6 条回执。

2026-08-01 无编号 normal/shipping Release natural parse timeout follow-up：

- frozen AIDL surface 不变，additive `TypeScriptR2Contract` 增加 public progress
  schema revision 1。common fields 为 request ID、phase、kind、request-start-relative
  monotonic nanos 与其毫秒投影；phase event 只增加 started/completed，work event 只
  增加 sequence 1/2、completed/total units。host 先验证 exact request/session/Worker、
  phase/order 与 `ceil(total/8)` / `ceil(total/4)` threshold，coordinator 再发布；每次
  compilation 最多 14 条，不携带 path、text、diagnostic、absolute clock 或 private
  identity；
- 新增 exclusive `shippingReleaseNaturalParseTimeoutEvidence` fixture runner。runtime
  强制 target 与 fixture distinct UID/same signer、service signature permission/exported、
  fixture TEST_ONLY，同时强制 target `testOnly=false`、`debuggable=false`、private
  telemetry disabled/identity null，并扫描 `getCompilerInfo()` 不得出现 private
  natural-phase/cleanup keys；
- 继续复用最终 exact 512-file / 8,177,717-byte / 236,532-token / 1,598-frame ordinary
  parse authority 与 64-output / 13,155-byte /
  `18f79093d9a09c3111740e282a3ad642576230c142d6b7d7c61bb9c7950ca038`
  output authority。每台设备在同一个 Binder session 内运行 2 次成功 precondition、
  3 次完整 baseline strict intersection、1 次 fault 与 1 个 small successor；成功
  main/fault/successor 的 public progress count 分别精确为 14/5/8；
- normal Release device matrix：

  | API / provider | baseline parse `seq2/completed` | strict window / timeout | fault public seq2 / terminal / overshoot |
  | --- | --- | ---: | ---: |
  | API 24 / Chrome `119.0.6045.194` | `6117/8527`, `5739/8385`, `5760/8172 ms` | `6118..8172 / 7145 ms` | `6356 / 7593 / 448 ms` |
  | API 35 / WebView `130.0.6723.86` | `14113/15450`, `14041/15652`, `14097/15591 ms` | `14114..15450 / 14782 ms` | `14475 / 15142 / 360 ms` |

  两行 fault 均为 `faultAttempts=1`、parse sequence 2 `8/32`、`ERROR_TIMEOUT`、
  `compilation=null`、output `0`、`privateReceipt=false`、same Binder；small successor
  均以 8 条 phase transition 生成 2 outputs / 312 bytes。API 24 host-side instrument
  返回 code -1；API 35 的 ADB waiter 在启动后被短超时分离，但 device-side
  instrumentation 完整发布唯一 `MATRIX_PASS` 并由 events buffer 记录 `finished inst`，
  为保持单 fault 没有重跑。两台设备的 evidence window 均无 target FATAL/OOM/ANR/LMK；
- 最终 normal Release app 为 2,712,604 bytes /
  `b5602ed1a4911e2a0823d791d7a483e35f1560ad185c7c83f196ac13d9c8f177`；Release
  fixture 为 750,558 bytes /
  `62080a3feab741bd4ad7f65c1e61774f598aab63b94dec5b69614a2006cfe1b1`；normal
  Release R8 mapping 为 5,168,255 bytes /
  `4cf3cec830624b47ecba33d522f4e9feea56a0ad68e305bf170d7edfe667dc8b`。两份 APK
  signer SHA-256 同为
  `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`；
- 最终无设备聚合执行 app/API JVM tests、desktop TypeScript `6.0.3` golden、512 MiB
  old-space retained-heap child、compiler core、normal Release R8 与完整 preview
  source/APK gate，结果为 `BUILD SUCCESSFUL in 1m 3s`（521 actionable tasks：
  81 executed、440 up-to-date）；
- 该 follow-up 使 internal provisional evidence 达到 **29/29**；54 个 Public R2
  checkbox 仍为 **0/54**，四项 capability 仍全部为 `false`。公开 `8/32` 只证明
  host-observed real-work boundary，不是 parser 指令级瞬时位置；normal Release 不含
  private receipt，因此不声称 exact Worker replacement、512 cursor 或 five-zero
  cleanup。当前仍开放 normal/shipping Release semantic/emit timeout、LMK/OOM、
  Unicode/high-token/adversarial、output 128/256 MiB、formal schemas、
  tsconfig/NodeNext 与其余 exit/device gates。完整边界见
  [Normal/Shipping Release Natural Parse Timeout](../architecture/SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_R2.md)。

## VFS 和配置

- [ ] 冻结 input/output archive schema.
- [ ] manifest 显式携带 schema, profile 和 runtime-capability revision.
  （S2/S4 provisional manifest 已完成；P2 又加入 config revision/kind 并把
  profile/config authority 贯穿 input/Worker/output，仍待正式 schema freeze）
- [ ] manifest 显式携带 compiler distribution, declarations 和 normalized options
  fingerprint.（S4 provisional output 已写入三项 provenance 与 canonical
  effective-options fingerprint；P2 将完整 18 项 options 与 fingerprint 收敛到
  单一 authority，待正式 module schema/用户配置后关闭）
- [ ] 实现只读内存 VFS 和严格 path canonicalization.（S1 historical heap seam
  已完成；P3a production native input 已切换为 app-private file-backed read-only
  VFS、metadata-only snapshot 与严格 canonical path；P3b-a Worker 又加入
  bounded directory trie 与 aggregate path gate，正式 package roots/schema 及
  完整 shipping-release/device fault capacity 仍待后续阶段后关闭；P3b-b2d 的
  release-equivalent evidence build 不能替代 shipping production release）
- [ ] 拒绝绝对路径, `..`, symlink 和规范化冲突.
- [ ] streaming 重新验证 compressed/uncompressed 大小, 文件数, ratio 和摘要.
  （S2 已在同一 private inode 上完成 input 实际 payload/CRC/size/ratio 与 archive
  SHA；P3a production input 已把 frozen validator 的最多 16 KiB callback
  transactional streaming 到 file-backed VFS spool，并在 framing 时流式重验每文件
  SHA；S4 对 output 同样使用 non-materializing held-inode validator 与 container
  SHA；P3b-a 已补 Worker 内部 representation/lifetime desktop slice，P3b-b1
  已补 same-UID DEBUG inclusive/`+1` device boundary，P3b-b2d 已补 external
  non-shipping debug/release-equivalent exact-64 四边界，仍待正式 schema、
  shipping fault/memory gates 与 output writer 配额接入后关闭）
- [ ] 拒绝 duplicate/case-fold/NFC collision, ZIP64, encryption 和未知 compression.
- [ ] manifest 必须精确覆盖 archive 中全部允许文件, 不多也不少.
  （S2 provisional source-only inventory 已完成）
- [ ] 支持 `tsconfig.json`, 但过滤 AutoJs6 禁止选项.
- [ ] 项目以 tsconfig 为唯一用户配置; `compilerOptionsJson` 仅用于无 tsconfig 的
  虚拟单文件项目, 两者同时提供时拒绝.
- [ ] compiler plugin 独占 TypeScript module resolution, host 不预判语义模块图.
- [ ] VFS 携带项目 `package.json` 和显式允许依赖的 package metadata.
- [ ] NodeNext resolver 覆盖 `exports`, `imports`, conditions 和 nested package boundary.
- [ ] snapshot 中所有符合受控 tsconfig include/files 的 TS 都作为 Program
  root/emit 候选, 不依赖非字面 import 的静态可发现性.
- [ ] AutoJs6 强制管理 target, module, resolution, sourceMap 和 outDir.
- [ ] 用户可管理 strict, noImplicitAny 和受支持的语义选项.
- [ ] options precedence 固定为 immutable profile > allowed user options >
  TypeScript explicit defaults.（P2 已固定 immutable Rhino profile 与 explicit
  defaults；allowed user options 尚未实现）
- [ ] canonical effective options 使用显式默认值, TS enum 值, 稳定 key 顺序和
  规范化 URI/path.（P2 已对 config-none Rhino 的 18 项 options 使用 numeric
  TS enum、稳定 key 顺序与 canonical fingerprint；正式 tsconfig/NodeNext 尚未接入）
- [ ] input 只声明 raw config bytes hash; effective options/fingerprint 只由 plugin
  计算并写入 output.（P2 的 `configKind=none` 无 raw config，effective
  fingerprint 仅由 plugin authority 计算并写 output；tsconfig bytes hash 待实现）
- [ ] request/manifest/archive hash 不一致时以 integrity error 拒绝.
  （S2 provisional control/manifest/staging 三方绑定已完成；待正式 schema 后关闭）
- [ ] 禁止 custom transformer, plugin, arbitrary typeRoots 和生命周期脚本.
- [ ] common/Rhino/Node declarations 分层装载.
  （S2 已完成 APK-local TypeScript standard libraries 的 runtime digest/fingerprint
  重验与保留 VFS root 注入；AutoJs6 common/Rhino/Node lock 仍待实现）

## 编译

- [ ] 使用 `createIncrementalProgram`, 不以 `transpileModule` 作为正式通道.
  （S1 内核已完成；P3b-a 又加入 per-job lazy SourceFile cache/finally release；
  待正式 schema/profile/device 通道后关闭）
- [ ] 收集 options, syntactic, global 和 semantic diagnostics.（S1 内核已完成；
  P3b-a 改为逐 SourceFile incremental cap，并在 pre-emit error 时跳过 emit；
  diagnostics schema 仍未冻结）
- [ ] 强制 `noEmitOnError=true`.（S1 内核及 semantic-error golden 已完成）
- [ ] 强制 `sourceMap=true`.（S1 内核已完成）
- [ ] 一源文件一 JS 文件, 不 bundle.（S1 Rhino golden 已完成）
- [ ] 生成 provisional Rhino CommonJS profile, 具体 revision 到 R4 冻结.
  （S1 固定 profile 已完成；P2 已接 archive/frame/result/output options authority，
  具体 revision 仍待 R4 冻结）
- [ ] 生成 provisional NodeNext profile, 具体 revision 到 R5 冻结.
- [ ] `.mts -> .mjs`, `.cts -> .cjs`, `.ts` 按 profile 规则处理.
- [ ] 输出真实 Source Map v3.（S1 runtime-bearing golden 的 `mappings` 非空；
  type-only module 按 TypeScript 合法产生空 `mappings`）
- [ ] 输出结构化 diagnostics JSON.（S1 仅完成 internal Worker result/typed
  diagnostics；S4 已生成 provisional deterministic `diagnostics.json`，schema
  尚未冻结）
- [ ] 输出包含 URI, module kind, fingerprint 和摘要的 manifest.（S4 provisional
  manifest 已有 provenance/options/inventory fingerprints 与 file size/SHA；
  original/generated URI、显式 module kind/JS-map pairing 仍待正式 schema）

## 生命周期

- [ ] 单活动编译或明确有界队列.（S5 internal process singleton arbiter 已统一
  prewarm/R1 probe/R2 job 单 slot，且跨同 PID Service epoch 保留 authority；
  P1 已接 production/public-shaped preview，但 capability 与正式 schema 仍未开放）
- [ ] start 立即返回 admission ticket.（P1 ticket 已返回 request ID + job token +
  ACK required/timeout，并冻结 transaction 6 post-return ACK；late exact ACK 已验证
  幂等且不会 re-arm；P3b-b2c 已锁 production-fence external source/APK path，
  P3b-b2d 又锁 non-shipping external exact-64 四边界；API 24/35
  debug/release-equivalent runtime 已通过；P3b-b2f 又在 normal/evidence
  Debug/Release 共 8 行证明 ticket 返回后、ACK 前的 external FD/lease
  ownership，正式 schema/完整 matrix 前不关闭）
- [ ] service 生成安全随机 job token, 与 calling UID 和 callback binder 绑定.
  （S5 internal lease 已使用 256-bit random token + UID/request/callback identity；
  P1 已接 public operation；P3b-b2c fixture 强制 distinct UID/same signer，
  runtime UID 已在 API 24/35 两组设备上证明不同）
- [ ] callback 只发送有界 progress 和终态元数据.（S5 private path 只发送有界
  terminal Bundle；P1 public-shaped path 复用 frozen v1 typed result；P3b-b2c
  已锁 external invalid/inclusive/`+1` exactly-once terminal 形态，P3b-b2d
  exact-64 四边界也已完成 device runtime；P3b-b2f 三类 `+1` 又证明
  exactly-once callback 只在 immutable all-zero cleanup receipt 创建后发布，
  fixture 在 callback 后读取并关联 receipt；无编号 normal/shipping Release parse
  follow-up 又通过 frozen `onProgress(Bundle)` 发布 revision-1 primitive-only
  phase/work envelope，每次最多 14 条，并在 normal Release API 24/35 runtime 验证
  exact key/order/time projection 与 terminal-after-progress；正式 schema 仍待完成）
- [ ] terminal outcome 区分 emitted, diagnostics, cancelled, timed-out 和 failed.
  （S5 coordinator 已区分并绑定 artifact/empty-output proof；P1 已接 public-shaped
  callback，正式 schema/device matrix 未完成）
- [ ] cancel 使用 request ID + job token, 校验 owner 且保持幂等.（P1 已公开
  `cancel|acknowledge` operation，并以 calling UID + request ID + token 授权及
  terminal tombstone 保持幂等；P3b-b2c fixture 三条路径均先拒绝 wrong-token
  ACK、再接受 exact ACK；P3b-b2g 又在 API 24/35 ordinary same-UID Debug
  自然 parse/semantic/emit progress 后证明 keyed cancellation、exact Worker
  termination receipt 与 successor cleanup，但 external/Binder/Release matrix
  尚未完成）
- [ ] 超时后停止 worker 并关闭所有 FD.（S5 absolute deadline 已串接 S3 exact
  hard-stop、per-job finalizer release 与 input close/output empty proof；cleanup
  无法证明时 poison 而不伪造 terminal；P3b-b2e 已在 API 24/35 以三轮
  exact-64-MiB partial ingress 固定 5 秒自然 timeout 证明 cursor release、
  Worker replacement、host close/spool cleanup，并以 Binder fixed 10 秒
  deadline 证明 terminal 后 active job 与 remote transient FD 最终清零；
  P3b-b2h 又以三次 baseline 交集选定 fixed timeout，在 ordinary same-UID Debug
  direct host 上证明 semantic `ERROR_TIMEOUT`、Worker `1→2`、512 cursor release、
  successor 与 cleanup；P3b-b2i 再以两次不计时 precondition、三次 baseline
  交集与单个 fault，在同一 ordinary direct-host 边界证明 parse
  `ERROR_TIMEOUT`、Worker `1→2`、117 cursor release、successor 与 cleanup；
  P3b-b2j 又以相同的两次 precondition、三次 baseline 交集与单个 fault，
  在旧 completion boundary 证明 emit `ERROR_TIMEOUT`、Worker `1→2`、116 cursor
  release、successor 与 cleanup；P3b-b2k 再以 external-UID public Binder、
  evidence Debug/release-equivalent 四行、absolute nanos seam 与 pre-callback
  phase/cleanup
  receipts 证明最终 ACK 前自然 emit `ERROR_TIMEOUT`、sequence 2 `128/512`、
  Worker replacement、364 cursor release、five-zero convergence 与 same-Binder
  successor；随后的无编号 external semantic follow-up 再以四行
  Debug/Release-equivalent public Binder matrix 证明 semantic sequence 2 `64/256`、
  `ERROR_TIMEOUT`、512 cursor release、Worker replacement、five-zero convergence 与
  same-Binder successor；随后的无编号 external parse follow-up 又以四行
  Debug/Release-equivalent public Binder matrix 证明 parse sequence 2 `8/32`、
  `ERROR_TIMEOUT`、512 cursor release、Worker replacement、five-zero convergence 与
  same-Binder successor；无编号 normal/shipping Release parse follow-up 又以公开
  5-event prefix 证明真正 normal Release 的 parse `8/32`、`ERROR_TIMEOUT`、零输出与
  same-Binder successor，但不在缺少 private receipt 时声称 exact Worker/cursor/
  five-zero；normal/shipping Release semantic/emit 及完整
  accepted/rejected/death/system fault matrix 前仍不关闭）
- [ ] client binder death 自动取消其请求.（S5 admission 已 link callback death，
  gate 前自动 stop 且不再调用死亡 callback；device death race matrix 待补）
- [ ] 重复 request ID 安全拒绝.（S5 以 `(UID, requestId)` bounded tombstone 拒绝）
- [ ] 所有 accepted/rejected/cancel/death/error 路径均有 FD close 测试.（最终
  原有 R1 accepted lifecycle 9 项已覆盖 service-close 的
  close-claimed/already-claimed cleanup，独立 service destruction/close-proof 1
  验证 host teardown 后 successor bind；R2 Binder 6 / Output FD 6 已覆盖
  irreversible admission 与 terminal-gate rollback，Project 10 已覆盖 in-flight
  finalizer close 及 teardown-before-release gated cancel；S6a 另覆盖
  deterministic injected cleanup-poison → old-process death → successor recovery
  与 synchronous persisted quarantine reconcile；S6b 覆盖 system Job owner 的
  `ABSENT` reconcile、live `SAME` fail-closed 与 marker-generation ABA fence，
  S6c 覆盖 automatic app-private live-owner handoff 与新 epoch recovery；S6d
  覆盖 live owner hard-kill、successor registry connection、registration-gap
  poison replay 及另一 fresh epoch recovery；S6e 覆盖 successor-ready 后 retired
  teardown handler/death-recipient 的 sequential duplicate no-op fence、随后 poison
  recovery 与 fresh epoch；S6f 覆盖 exact persisted-Job pre-arm/readback、
  marker post-check、无 successor bind 的 system reconcile 与 fresh epoch；S6g
  收窄一次 running fixed-ID/different-token handoff，以 token-scoped positive
  START/STOP trace 与最终 exact B owner 证明该有界窗口，但 stale connected/
  notification transaction；S6h/S6i 又分别覆盖 retained W 的 owner-death 与 shared
  barrier 后 owner/compiler concurrent kill-request recovery，继续证明 old FD/process
  disappearance 与 fresh epoch FD release；P3b-b2f 再以 external-UID
  production-fence/STORED/DEFLATED 三类 `+1` 证明 caller FD、private
  input/project spool/private output named/open transient、cursor 与 lease 在
  callback 前清理，随后 5 个 current sample 全零并完成 same-Binder successor；
  但 system-triggered dual death、actual
  death overlap、连续/重叠 replacement、未观察 callback ordering、handoff 中
  JobService restart、真实 reboot/force-stop/system_server persistence、外部取消、
  跨 reboot deadline + `UNREADABLE`、连续 backoff/crash-loop 与完整 fault matrix
  尚未完成）
- [ ] emitted/diagnostics output 先写私有受限临时 ZIP, 校验并
  truncate/copy/fsync/seek/close 后才 callback.（S5 已把 S4 primitive 接入 terminal
  gate，P1 public-shaped Binder 复用同一路径；正式 schema/streaming/device matrix
  未完成）
- [ ] 语法/类型失败提交 diagnostics-only ZIP, module count 为 0 且不可执行.
  （S5 private full-pipeline 已提交该形态；public host execution policy 未接入）
- [ ] cancel/timeout/internal/transport failure output 保持空.（S4 empty finalizer
  与 ordinary rollback 已由 S5 coordinator 接入；`Unproven` 会 poison exact lease，
  S6a 已证明 DEBUG same-UID exact process-death recovery，S6b 已接入
  production-manifest post-death Job owner，S6c 已 provisionally 补齐 app-private
  live registration/authorization/termination control plane，S6d 已补齐单次 owner
  restart/rebind 与 registration-gap replay，S6e 已补 successor-ready retired
  teardown handler fence，S6f 已补 marker 前 exact persisted-Job ownership 与
  no-successor reconciliation，S6g 已补一次 running fixed-ID generation handoff
  的 gate/journal 正向观测；S6h 又补一次 running JobService owner-death 后同一
  scheduler-tracked W 跨 owner epoch 的 positive callback/reconcile，其中 API 35
  仅为 forced-eligibility invocation boundary 后的非因果正向证据；S6i 再补 shared
  validation barrier 后 owner/compiler concurrent exact-kill request 与
  `bothSignalsReturnedNanos` 下界；P3b-b2e 的 API 24/35 Binder exact64 natural
  timeout 又证明 empty output、零 compilation payload 与 small successor，
  public path 与完整 system-lifecycle matrix 仍未完成）
- [ ] host 只在 emitted 后原子发布 artifact; diagnostics 只展示, 不创建引擎.
  （S5 private coordinator 已原子选择 artifact terminal；public host 的
  emitted-only engine creation 尚未接入）

## 退出条件

- [ ] 两文件 import 项目在 Rhino/Node profile 均成功 emit.
- [ ] enum, namespace, class fields, generics, async 和 decorators fixture 通过.
- [ ] 类型错误时无可执行产物.
- [ ] 混合 `.ts + .js + .json` 图通过.
- [ ] 真实 compiler golden tests 在桌面和 Android 输出一致.
- [ ] request/output manifest 和 diagnostics JSON schema 此时才冻结为 v1.
- [ ] profile revision/fingerprint 机制冻结, 具体 Rhino/Node revision 尚不冻结.
