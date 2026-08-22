# R2 Production/Public-shaped Admission Preview

> **R2-M3-S1 supersession（2026-08-14）。** 本页中的 provisional archive revision `2` 与旧
> request/start shape 只保留为 pre-S1 admission provenance，不是当前 formal alias/fallback。
> 当前 formal input 只接受 `autojs6.typescript.input-archive/revision=1`，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。这不 promotion Public capability。

> 路线提示（2026-08-01）：本文是 Rebaseline 前的历史证据文档；其中 29/29 与
> Public 0/54 均按旧口径保留，不代表当前总体进度。活动路线见
> [R2 Roadmap](../roadmap/R2.md)，完整旧路线见
> [R2 Legacy Evidence](../roadmap/R2_LEGACY_EVIDENCE.md)。2026-08-11 的 private same-UID
> public-shaped service 完成状态及其非 Public-promotion 边界见
> [R2-M2-X1](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。

状态：R2-P1/P2/P3a/P3b-a/P3b-b1/P3b-b2a/P3b-b2b internal complete；
P3b-b2c external production-fence complete；P3b-b2d non-shipping external
exact-64 source/APK policy 已落地，最终 TEST_ONLY evidence artifact 已重建并
通过本地 gate；API 24/35 四行与 ordinary Debug/Release 回归均已 PASS。
P3b-b2e natural ingress timeout/final cleanup source/APK gate 与 API 24/35
四项定向 runtime 已全部 PASS，该历史切片的 internal provisional evidence 为
**20/20**。P3b-b2f external plus-one cleanup source/APK gate 与 API 24/35
normal/evidence Debug/Release 8 行 runtime 又全部 PASS，该历史切片为
**21/21**。P3b-b2g ordinary natural parse/semantic/emit cancellation
source/paired-APK gate 与 API 24/35 六案 runtime 也全部 PASS，该历史切片为
**22/22**。P3b-b2h ordinary natural semantic request-start absolute-deadline
timeout source/paired-APK gate 与 API 24/35 两案 runtime 又全部 PASS，该历史
切片为 **23/23**。后续 b2i ordinary parse、b2j 历史 emit 与 b2k external-UID
Binder emit timeout 依次将历史计数推进到 **26/26**；b2k 后的无编号
external-UID Binder natural semantic timeout follow-up 又在 API 24/35 ×
TEST_ONLY evidence Debug/minified non-debuggable Release-equivalent 四行全部
PASS；第二个无编号 external-UID Binder natural parse timeout follow-up 又以
exact 512-file low-stack DAG workload 在相同四行边界全部 PASS；第三个无编号
follow-up 再以 bounded public phase/work progress 在 API 24/35 的真正 normal
Release 上关闭 natural parse timeout。该历史证据链最终的 internal provisional
evidence 为 **29/29**。四项 capability 仍为 `false`；按旧口径，Public R2 为
**0/54**。

## 目的

P1 把已经存在的 S2–S6 compiler/coordinator primitive 接到 production build，
以便先验证真实外部 caller 的 Binder ownership、post-return ACK、callback 和 FD
生命周期。
P2 又把 profile/config revision、固定 effective options 与 runtime fingerprint
收敛为单一 authority，并贯穿 input、Worker result 和 output。
Rebaseline 后的 R2-M2-C1/C2/C3/C4 已在独立、尚不可公开到达的 controlled branch 内完成
policy、raw parser、41-key effective-options authority 与 compiler escape closure；这不改写
本文的历史 P2 config-none admission 证据。
P3a 已把 production native input loader/framer 切换为 app-private file-backed
spool、metadata-only snapshot 和最多 16 KiB random-access frame。P3b-a 又加入
Worker 内部 4607 files/72 MiB envelope、complexity budgets、bounded per-job
lifetime 和 desktop 64+8 MiB retained-memory gate。P3b-b1 将当前 frame 调整为
64 KiB raw/128 KiB envelope，并用 DEBUG-only gate 建立 API 24/28/35 capacity
与 exact success realm-retirement evidence。P3b-b2a 再加入严格 phase
started/completed protocol，以 same-UID `DEBUG` synthetic phase-entry spin
建立 API 24/35 cancel/timeout，并在 API 35 关联一次 active exact-64-MiB
renderer loss 与 successor recovery。P3b-b2b 又补三轮大任务资源包络、自然
ingress cancellation cursor/Worker cleanup 与 cold-start orphan scavenger。
P3b-b2c 再冻结 standalone same-signer、distinct-UID fixture 的 production-fence
invalid/inclusive/`+1` 三路径；debug 与 minified service release 的 API 24/35
runtime matrix 已全部 PASS。首轮 minified release 暴露的 API 24 FD-procfs 与
API 35 sibling-procfs registration 阻塞也已关闭。P3b-b2d 又为 non-shipping
`r2CapacityEvidenceDebug` / `r2CapacityEvidenceRelease` app，在 API 24/35
定义 external STORED container exact 64 MiB/`+1` 与 DEFLATED expanded exact
64 MiB/`+1` matrix；后者是 minified/non-debuggable release-equivalent evidence
build，两个 evidence app variant 都是 TEST_ONLY，**不是** shipping production
release。最终 TEST_ONLY artifact 已通过本地 gate，API 24/35 四行均已 PASS。
P3b-b2e 再以 ordinary same-UID debug artifact 证明 API 24/35 direct exact64
三轮 fixed-5s partial-ingress natural timeout，以及 Binder exact64 fixed-10s
public-ACK natural deadline、empty output、exactly-once、terminal 后最终清理与
successor。Binder 单项不外推为 partial-ingress 或 cleanup-before-callback
ordering 证据。P3b-b2f 随后用 standalone distinct-UID fixture 完成 ordinary
production-fence、STORED container 64 MiB `+1`、DEFLATED expanded 64 MiB
`+1` scope-free cleanup：receipt 在 callback 前创建，在 callback 后读取并
关联；连续 5 次 all-zero 与 same-Binder successor 均通过。P3b-b2g 接着在
ordinary same-UID Debug、零 spin、无 timeout 注入的 300-file workload 上，于
parse/semantic/emit 的真实 sequence-2 progress 后执行 keyed cancellation；
API 24/35 六案均证明 exact termination receipt、cursor/Worker/successor 与
host/spool cleanup。P3b-b2h 接着只用 ordinary same-UID Debug direct host 的
request-start absolute deadline 关闭 semantic timeout：三次完整成功 baseline
的严格交集冻结 timeout，再执行一个 fault，不使用测试侧 `requestStop`、retry、
spin 或 expanded admission；API 24/35 均证明 timeout、Worker replacement、
successor 与 cleanup。正式 schema、NodeNext、自然 parse/emit timeout、
external/Binder/Release semantic timeout 与完整 memory/system-fault device
gates 在该历史边界仍未完成。后续 b2i 与 b2k 已分别补 ordinary direct-host
parse 和 external-UID Binder emit；b2k 后的无编号 follow-up 又补
external-UID Binder semantic，但只覆盖 TEST_ONLY evidence Debug 与
non-shipping Release-equivalent；第二个无编号 follow-up 又补 external-UID Binder
parse，仍只覆盖相同 evidence variants。normal/shipping Release 的 natural
parse 后来由第三个无编号 follow-up 以 public progress 关闭；normal/shipping Release
的 natural semantic/emit 与完整 system-fault
device gates 仍未完成。

它刻意不翻开以下 capability：

- `typescriptCompilerAvailable`
- `typescriptProjectCompilation`
- `typescriptSemanticDiagnostics`
- `typescriptSourceMapV3`

## Additive request

普通 frozen-v1 request 继续稳定返回 `R0_NOT_AVAILABLE`。只有同时携带下列完整
metadata 的请求才会被分类为 R2 preview：

- frozen v1 envelope 的全部必需字段；
- `profileSchemaRevision`、`profileRevision`；
- `configSchemaRevision`、`configKind`；
- `runtimeCapabilitySchemaRevision`；
- compiler distribution、declarations、runtime-capability fingerprints；
- `inputArchiveSha256`、`requestManifestSha256`。

R2-M2-C2 又预留受控 raw config identity：`configByteLength` 使用 Bundle `Long`，
`configSha256`/`configPolicyFingerprint` 使用 lowercase SHA-256，
`configPolicyRevision` 使用 `Int`。这些任一 key 单独出现也必须被识别为 R2 metadata，
不能绕回 frozen-v1/R0 分类。

这些新增 key 位于 `TypeScriptR2Contract`。它们不修改 AIDL transaction、contract
version/hash 或 frozen `TypeScriptCompilerContract`。任一字段缺失、类型错误、
revision 不匹配、非 lowercase SHA-256 或 runtime fingerprint 与本地 authority
不一致，都会在 FD ownership transfer 前拒绝。缺失、错误 Bundle 类型、declared length
越界或摘要 lexical 畸形返回 `ERROR_INVALID_REQUEST`；unsupported profile/config/policy
authority 返回 `ERROR_CONTRACT_MISMATCH`。

当前 preview 仍只接受 provisional Rhino profile、`configKind=none`、缺省或精确 `{}` 的
`compilerOptionsJson` 和 provisional archive schema revision 2。任何其他 inline 值在 R0
decision 中先于 descriptor-validity rejection，并在 coordinator admission/FD ownership
transfer 前返回 `ERROR_INVALID_REQUEST`；service 在此前只可做 non-owning descriptor
inspection。完整 18 项
effective options 由 plugin authority 固定，caller 不提交其 fingerprint。它不是
正式 schema v1。完整且合法的
`configSchemaRevision=2/configKind=tsconfig/policyRevision=1` metadata 即使在 C4 internal
authority 完成后仍同步返回 `ERROR_CONTRACT_MISMATCH`，不会进入 coordinator 或接管 FD；
这明确区分“C1-C4 internal shape 已完成”与“public tsconfig compilation 已激活”。未知
primitive Bundle key 继续按 additive compatibility 忽略，且不会因名称为
`plugins/typeRoots/customTransformers/scripts` 而成为 public metadata authority。
C4 没有修改 AIDL/public contract、result/output schema 或 diagnostics ZIP，四项 capability
继续为 `false`，因此没有 externally reachable runtime behavior change，也不新增设备矩阵。

## Admission 与 ACK

release/debug 都创建 `R2CompilationCoordinator`。production preview 还要求
`:typescript_supervisor` registration ready；否则同步返回 compiler unavailable，
不会接管 archive FD。

accepted ticket 包含：

- `requestId`
- 安全随机 `jobToken`
- `acknowledgementRequired=true`
- `acknowledgementTimeoutMs=5000`

public preview 的 `timeoutMs` 下限为 6000 ms，因此 server-side ACK budget 固定为
5000 ms，并与 project deadline 留有至少 1000 ms 的边界。慢速且不修改输出的
`fsync`/seek preflight，以及 callback link/alive、deadline executor availability
等普通同步 reject gate，均在 `ftruncate` 前完成。成功 `ftruncate` 是不可逆
ownership commit；ACK/project 两个 server clock 从这一刻锚定。commit 后若 timer
调度异常，任务按未 ACK 的 accepted job 静默清理，不回退为同步 rejection。
budget 是 server-side cutoff，不承诺 Binder ticket 返回后仍有完整 5 秒。client
必须在 ticket 返回后调用 transaction 6：

```text
cancelCompilation(
  contractVersion,
  contractHash,
  requestId,
  jobToken,
  cancellationOperation="acknowledge"
)
```

ACK 仍由 calling UID + request ID + job token 授权。缺省或显式
`cancellationOperation="cancel"` 执行取消。wrong owner/token/request 返回
`false`。成功 ACK 的重复调用和其他已终态调用保持幂等；public preview 错过 ACK
deadline 后返回 `false`，让 caller 能察觉 admission 从未 arm callback。private
debug probe 继续为相同 late-unack token 返回 `true`，以保留既有 lifecycle test
兼容性；两条路径都不会重新 arm callback。

## P3a/P3b-a/P3b-b1/P3b-b2a/P3b-b2b/P3b-b2c/P3b-b2d/P3b-b2e/P3b-b2f/P3b-b2g/P3b-b2h 容量与 fault 声明边界

P3a 可以在 app-private immutable staging inode 上按冻结 ZIP v1 input limits
流式验证最多 64 MiB container/64 MiB actual uncompressed payload，并把 entry
payload 写入 private spool；production snapshot 只保留 metadata、offset 和
read-only inode。native framer 在 P3a 初始每次随机读取最多 16 KiB；P3b-b1 当前
使用一个 64 KiB raw buffer 与 128 KiB envelope，不保留 caller source 的整文件
byte array 或 text map。

P3b-a 已把 Worker 自身的 direct-framing envelope 提升到 4607 files、
8 MiB/file、72 MiB total，并加入 path/directory/token complexity gates、单份
decoded String、lazy `SourceFile` cache、incremental diagnostics 和 per-job
release。受 512 MiB Node old-space 约束的 desktop gate覆盖单次 64 MiB caller +
8 MiB trusted boundary、forced-GC retained plateau 和 small successor。

P3b-b1 又通过 DEBUG-only expanded gate 在 API 24/28/35 完成 exact 64 MiB
direct capacity、newer-session small successor，并在 API 24/35 完成
STORED/DEFLATED inclusive/`+1` Binder full pipeline。每个成功 project 在 terminal
前退休 exact WebView realm。

P3b-b2a 的 Worker/page/native protocol 要求 ingress、parse、semantic、emit 各有
exact `started`/`completed`，result 不得越过 active 或未完成 phase。production
start frame 固定为 `workerSpinMilliseconds=0` 与 `workerSpinPhase=none`；
same-UID `DEBUG` test 才能在 exact phase entry 启用 bounded spin。API 24/35
据此覆盖四阶段 cancel/timeout；API 35 又在 exact 64 MiB active semantic entry
终止 renderer，记录 `RenderProcessGoneDetail` 和 old/new
`(pid,starttime)` recovery。该 seam 不是自然零 spin 阶段中途抢占，也不是
LMK/OOM。

P3b-b2b 在同一 compiler epoch 连续执行三次 same-UID `DEBUG` exact-64-MiB
Binder success，并以 PSS、完整 FD target multiset、private transient、cursor
与 small successor 收敛资源边界；另以三轮零 spin 自然 ingress cancellation
证明 cursor/Worker replacement，并在 compiler service 冷启动时 fail-closed
清理 recognized private orphan。该证据不能外推为 external caller、release 或
plus-one remote cleanup。

P3b-b2c 的 standalone fixture 固定三条 external production-fence path：

- 正确哈希的 invalid ZIP：accept → wrong-token ACK reject → exact ACK →
  exactly-once `OUTCOME_FAILED + ERROR_ARCHIVE_FORMAT` → empty output；
- inclusive：`4,603,850`-byte caller + installed trusted
  `3,784,758` bytes / 108 files = exact `8,388,608` bytes，accept/ACK 后
  exactly-once `OUTCOME_EMITTED`，并重验 diagnostics/JS/map/manifest payload
  SHA、inventory 和 compiler/declarations/options/runtime authority；
- plus-one：结构、manifest coverage 与全部哈希合法的 `4,603,851`-byte caller
  在 accept/ACK 后异步、exactly once 返回
  `OUTCOME_FAILED + ERROR_ARCHIVE_QUOTA`，terminal 为零 compilation payload，
  output 为空。

三条路径 terminal 后均使用 1 秒 quiet window 证明 callback exactly once。
authority snapshot 另绑定 source APK/package identity，并在 request 构造前后复核，
避免安装替换或 authority drift 的 TOCTOU。这里的 inclusive 是 production
8 MiB aggregate caller boundary，不是 external 64 MiB；本切片也未直接证明
plus-one remote transient cleanup。

P3b-b2d 只在 non-shipping `r2CapacityEvidenceDebug` /
`r2CapacityEvidenceRelease` app 中开放派生 evidence authority。service 同时
核对 build flag、public base authority、固定 project/entry、distinct calling
UID、唯一 testOnly fixture package、同签名、固定 scope 与 frozen FD size；
任一项不匹配都 fail closed。release evidence app 继承普通 release 的
R8/minify、resource shrink 与 non-debuggable 形态；两个 evidence variant 都是
TEST_ONLY，并使用独立 evidence APK 文件名。它们与 ordinary app 保持相同
application ID/signing，安装会替换普通 app，必须使用 `adb install -t`；它们
不是 shipping production release。

standalone fixture 用显式 streaming ZIP32 writer 运行四个边界：

- STORED container `67,108,864` bytes emitted；
- STORED container `67,108,865` bytes exactly-once
  `ERROR_ARCHIVE_QUOTA`、empty output；
- DEFLATED expanded `67,108,864` bytes emitted，container 不超过 64 MiB；
- DEFLATED expanded `67,108,865` bytes exactly-once
  `ERROR_ARCHIVE_QUOTA`、empty output，container 不超过 64 MiB。

service admission 对 STORED scope 认证 frozen container exact 64 MiB；对
DEFLATED scope 只认证 frozen container 位于 `1..64 MiB`，不在 admission 时
content-bound 认证 expanded exact 64 MiB。DEFLATED expanded exact/`+1` 由
fixture 两遍 size/CRC/SHA/compressed-size 自检与 runtime terminal/output 共同
证明。

每行在四边界前仍运行 b2c invalid ZIP、ordinary 8 MiB inclusive/`+1`，之后
再运行 ordinary 8 MiB inclusive successor。最终 TEST_ONLY artifact 的 API
24/35 四行均已 PASS；该 b2d 历史切片的 internal provisional evidence 为
**19/19**。

P3b-b2e 在 ordinary same-UID debug artifact 上增加两条互补证据：

- direct-host exact 64 MiB caller 加 trusted declarations，在 API 24/35 各连续
  三轮以固定 5 秒 deadline、零 spin、`workerSpinPhase=null` 且无主动 stop
  命中 partial ingress；每轮返回 elapsed 不早于 deadline 的 `ERROR_TIMEOUT`，
  cursor opened/closed 平衡并 released，Worker generation 1→2→3，small
  successor 成功并退休 newer session，host close/spool cleanup 可证明；
- Binder STORED exact `67,108,864`-byte container 使用固定 10 秒 deadline 与
  public ACK，在 API 24/35 返回 matching request ID、exactly-once timeout、
  零 diagnostics/modules/manifest digest 与 empty output；terminal 与独立
  wall clock 均不早于 deadline，随后 active job、命名/remote transient FD
  最终清零、compiler identity 不变且 small successor 成功。

Binder deadline 可能在 staging/SHA/ZIP validation/spool 或 host ingress 到期，
所以它证明 end-to-end natural deadline/final cleanup，partial ingress 只由
direct-host phase/cursor telemetry 证明；该用例也不单独声称 callback 发布晚于
所有 cleanup。P3b-b2e 后 internal provisional evidence 为 **20/20**。

P3b-b2f 在 standalone same-signer、distinct-UID fixture 上增加 scope-free
external `+1` cleanup 证据：

- normal Debug/Release 各运行 ordinary production-fence `+1` rejection 与
  `4,603,850`-byte successor，提供不依赖 private telemetry 的 black-box
  recovery 回归；
- TEST_ONLY evidence Debug/minified non-debuggable release-equivalent 各运行
  production-fence、STORED container `67,108,865` bytes 与 DEFLATED expanded
  `67,108,865` bytes 三个 `+1` rejection；每个 request 都不携带 exact64
  capacity scope，保持 `PUBLIC_PREVIEW` origin 与
  `expandedWorkerCapacity=false`，每案后都运行 ordinary successor；
- `startCompilation` 返回后、ACK 前以最多 80 ms、每 10 ms 一次的有界只读
  metadata sampling 惰性记录 pre-ACK ownership。unavailable 或非 exact candidate
  不缓存，只有 active lease 为 1、server input/output exact-target FD 为 `1/1`，
  且 request/token/descriptor digests 全部匹配时才发布；同一次
  `getCompilerInfo()` 的 current snapshot 会再次独立枚举。该 observation sampling
  不是 admission/fault retry；
- coordinator 在 callback 前释放 lease/FD/private transient/cursor 并创建
  immutable receipt；fixture 在 callback 后读取并关联 receipt，随后取得连续
  5 个 all-zero current snapshot。quota rejection cursor 为 `NOT_CREATED`，
  successor cursor opened/closed 平衡且 `RELEASED`；Binder 与 safe process
  epoch 全程不变，raw boot ID/nonce 从不暴露。

API 24/35 normal/evidence Debug/Release 共 8 行全部 PASS，P3b-b2f 后当时
internal provisional evidence 为 **21/21**。

P3b-b2g 在 ordinary same-UID Debug direct host 上增加 natural phase
cancellation 证据：

- deterministic workload 固定为 108 declarations + 192 roots = 300 files、
  4,594,999 bytes、232,249 tokens；TypeScript 6.0.3 baseline 精确得到零
  diagnostics、192 JS + 192 maps、562,945 output bytes 与冻结 digest；
- Worker 只在真实 work unit 后发布每阶段最多两个 bounded internal progress；
  page 在 exact terminate 前冻结最后 marker，native 只接受 request/session/
  Worker/phase/sequence 完全匹配的 termination receipt；
- API 24/35 分别在 parse `48/192`、semantic `48/192`、emit `96/384`
  sequence 2 后取消，六案均为 ordinary admission、零 spin、无 timeout 注入；
  300/300 cursor 平衡释放，Worker generation 依次替换，小项目 successor 与最终
  host/spool cleanup 可证明。

P3b-b2g 后当时 internal provisional evidence 为 **22/22**。该 receipt 只证明
page 在 exact termination 前最后观察到 sequence 2 且尚未观察到 phase
completed，不声称 TypeScript 指令级瞬时位置。

P3b-b2h 在 ordinary same-UID Debug direct host 上增加 natural semantic
request-start absolute-deadline timeout：

- 每台设备先执行三次完整成功 baseline。每次都重验 512 input files、零
  diagnostics、256 JS + 256 maps、720,866 output bytes 与 aggregate SHA-256
  `4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1`；
- semantic strict window 取三次 request-start-relative sequence-2/completed
  窗口的交集，保留双边至少 100 ms 后冻结 midpoint；fault 只运行一次，不
  `requestStop`、不 retry、不 spin、不启用 expanded admission；
- 修复 completion/deadline 原子决策后重建的 API 24
  window/timeout/margins 为
  `9,331..12,777 / 11,054 / 1,723,1,723 ms`，selection overshoot `2 ms`；
  API 35 为 `37,725..38,828 / 38,276 / 551,552 ms`，overshoot `0 ms`；
- 两案均在 semantic sequence 2 `64/256` 后、completed 前返回
  `ERROR_TIMEOUT`、`compilation=null` 与 `TIMED_OUT`；512/512 cursor
  released、Worker `1→2`、small successor 与 host/spool cleanup 全部通过；
- deadline 固定为 request-start monotonic absolute nanos；interrupt 不能伪造
  timeout，completion/deadline race 通过同一 terminal selection 收口。

P3b-b2h 完成时 internal provisional evidence 为 **23/23**。该证据只关闭上述
same-UID Debug direct-host semantic timeout，不外推 parse/emit、external UID、
Binder、Release 或 system fault。

这仍不等于 Public R2 已支持 64 MiB compilation。普通 Debug/Release
external/public host 在 dispatch 前继续执行 512 files、8 MiB/file、8 MiB total
admission；normal release APK gate 只证明
`__autojs6_r2_external_capacity_evidence_scope`、
`stored-container-exact-64-v1` 与 `deflated-expanded-exact-64-v1` 三个 token
不存在；P3b-b2f gate 另证明 cleanup prefix/class/epoch-domain marker 经 R8
消失，但不宣称 enum literal 或其他广义 evidence marker 全部消失。Worker 仍会
为当前文件分配完整 `Uint8Array`，最终可能同时保留全部 source/declaration
`String` 与 TypeScript Program/AST。b2d external capacity success 本身不能替代
resource-cleanup evidence；b2f 只补上上述 `+1` remote cleanup 窄矩阵，不能替代
完整 PSS 或 device LMK/OOM evidence；b2g 只补 ordinary same-UID Debug 的自然
phase cancellation，不能外推为 timeout、external/Binder/Release 或 system
fault；b2h 只补同一 direct-host 边界内的 semantic timeout。

后续 b2i/b2j/b2k 与无编号 external-UID Binder semantic/parse follow-ups 将 internal
provisional evidence 推进到 **28/28**。前两个无编号 follow-ups 只证明 API 24/35
的 TEST_ONLY evidence Debug 和 minified/non-debuggable Release-equivalent；后者
仍是 non-shipping evidence，不是 normal/shipping Release。API 24 正式矩阵固定
Chrome `119.0.6045.194`；Google WebView `53.0.2785.124` 只形成当前
`compiler-host.js` 语法不兼容的 fail-closed compatibility exploration，没有
形成 semantic timing 证据。

第三个无编号 follow-up 随后在 frozen `onProgress(Bundle)` 上加入 revision-1
primitive-only public phase/work envelope：exact request/session/Worker validation 后，
每次 compilation 最多发布 14 条 request-start-relative progress；normal Release DEX
保留这些 public markers 并排除 private receipt prefix。API 24/35 normal Release
各以 2+3 strict calibration、唯一 parse fault 与 same-Binder successor 通过，fault
均精确收到 5 条事件并停在 parse `8/32`、返回 timeout 与零输出。它将 internal
provisional evidence 推进到 **29/29**，但缺少 private receipt 时不声称 exact
Worker/cursor/five-zero cleanup。

因此四项 capability 继续为 `false`；按当时旧口径，Public R2 checklist 为 **0/54**；
这些 slices 不关闭其中任何一项。P3b-b 仍至少需要 normal/shipping Release natural
semantic/emit timeout、真实 LMK/OOM、
Unicode/high-token/adversarial、output 128/256 MiB、formal
schemas、C7 selector/root activation 与 NodeNext，之后才能决定是否解除 production
admission。raw tsconfig identity/parser、41-key effective options 与 escape closure 已由
R2-M2-C2/C3/C4 在 internal branch 完成，但未改变 capability；C5/C6 仍需以新 revision
加入 package metadata 与 NodeNext resolution。
P3a 的完整边界见
[R2-P3a File-backed Native Input](FILE_BACKED_INPUT_R2_P3A.md)，P3b-a 见
[R2-P3b-a Worker Input Capacity and Lifetime](WORKER_INPUT_R2_P3B_A.md)，P3b-b1 见
[R2-P3b-b1 Android Device Capacity and Exact Realm Retirement](WORKER_INPUT_R2_P3B_B1.md)，
P3b-b2a 见
[R2-P3b-b2a DEBUG Phase-entry Hard-stop and Active Renderer-loss](WORKER_FAULT_R2_P3B_B2A.md)，
P3b-b2b 见
[R2-P3b-b2b Repeated Resource and Startup Orphan Cleanup](WORKER_RESOURCE_R2_P3B_B2B.md)，
P3b-b2c 见
[R2-P3b-b2c External Production-fence Pipeline](EXTERNAL_FENCE_PIPELINE_R2_P3B_B2C.md)，
P3b-b2d 见
[R2-P3b-b2d External Exact-64-MiB Capacity Evidence Pipeline](EXTERNAL_EXACT64_PIPELINE_R2_P3B_B2D.md)，
P3b-b2e 见
[R2-P3b-b2e Natural Ingress Timeout and Cleanup](WORKER_NATURAL_INGRESS_TIMEOUT_R2_P3B_B2E.md)，
P3b-b2f 见
[R2-P3b-b2f External Plus-one Cleanup Receipt Evidence](EXTERNAL_PLUS_ONE_CLEANUP_R2_P3B_B2F.md)，
P3b-b2g 见
[R2-P3b-b2g Natural Parse/Semantic/Emit Cancellation](WORKER_NATURAL_PHASE_CANCELLATION_R2_P3B_B2G.md)，
P3b-b2h 见
[R2-P3b-b2h Natural Semantic Request-start Absolute-deadline Timeout](WORKER_NATURAL_SEMANTIC_TIMEOUT_R2_P3B_B2H.md)，
P3b-b2i 见
[R2-P3b-b2i Natural Parse Request-start Absolute-deadline Timeout](WORKER_NATURAL_PARSE_TIMEOUT_R2_P3B_B2I.md)，
P3b-b2j 历史 emit 边界见
[R2-P3b-b2j Natural Emit Request-start Absolute-deadline Timeout](WORKER_NATURAL_EMIT_TIMEOUT_R2_P3B_B2J.md)，
P3b-b2k 见
[R2-P3b-b2k External-UID Binder Natural Emit Timeout](EXTERNAL_NATURAL_EMIT_TIMEOUT_R2_P3B_B2K.md)，
无编号 semantic follow-up 见
[R2 External-UID Binder Natural Semantic Timeout](EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_R2.md)，
无编号 parse follow-up 见
[R2 External-UID Binder Natural Parse Timeout](EXTERNAL_NATURAL_PARSE_TIMEOUT_R2.md)，
无编号 normal/shipping Release parse follow-up 见
[R2 Normal/Shipping Release Natural Parse Timeout](SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_R2.md)。

## 兼容与验证边界

- frozen AAR、checksum 和 fixtures 不修改；
- freeze gate 逐 byte 保留 frozen `classes.jar` v1 entries，只允许新增
  `TypeScriptR2Contract` class；
- debug instrumentation 覆盖 production-shaped request、wrong-token ACK、
  post-return callback 和完整 emit；
- 独立 `r2-public-preview` fixture 要求以不同 UID、同签名权限绑定 compiler
  service，运行时还验证 permission base protection 为 signature、permission
  owner signer 与四项 capability 仍为 false；正确哈希的 invalid ZIP 回归继续
  验证 external accept/ACK → exactly-once `ERROR_ARCHIVE_FORMAT` → empty
  output，不能由 inclusive/`+1` case 替代；
- production-fence inclusive case 验证 exact `4,603,850`-byte caller 与
  installed trusted declarations 合计 8 MiB 后 emit，并重验完整 output
  payload/inventory/authority；结构/哈希合法 caller `+1` 在 accept/ACK 后异步
  返回 `ERROR_ARCHIVE_QUOTA`、零 payload 与 empty output；
- b2d evidence authority 只存在于 `r2CapacityEvidenceDebug` /
  `r2CapacityEvidenceRelease`，并绑定 exact fixture UID/package/testOnly/signer、
  project/entry、scope 与 FD size；普通 Debug/Release 和 shipping release
  fail closed。两个 evidence app variant 也是 TEST_ONLY、使用独立 evidence
  文件名并保持 ordinary application ID/signing。四边界、ordinary b2c 前置
  回归与 post-capacity successor 都由 source/APK policy gate 固定；
- b2e source/APK gate 锁定 ordinary debug 的 fixed-5s direct partial-ingress
  natural timeout、cursor/Worker/session/host cleanup，以及 fixed-10s Binder
  public ACK/timeout/zero-output/exactly-once/final coordinator/transient cleanup
  与 successor；APK gate 只检查最终 DEX evidence shape，不代替设备执行；
- b2f source/APK gate 锁定 exact TEST_ONLY caller attestation、三类 scope-free
  `+1`、ticket-return 后的 pre-ACK FD/lease ownership、receipt-created-before-
  callback/read-and-correlated-after-callback、连续 5 次 all-zero、同一
  Binder/safe epoch 与逐案 ordinary successor；normal release DEX 必须不含
  cleanup prefix/class/epoch-domain marker，evidence release-equivalent 必须保留
  cleanup telemetry 且生成非空 R8 mapping；APK gate 不代替设备执行；
- b2g source/paired-APK gate 锁定 deterministic 300-file workload、TypeScript
  6.0.3 baseline、真实两级 parse/semantic/emit progress、page/native exact
  termination receipt、三阶段 cancellation 与 cursor/Worker/successor/host/spool
  cleanup。paired gate 同时构建并检查 debug app runtime DEX 与 androidTest
  fixture DEX，仍不代替设备执行；
- b2h source/paired-APK gate 锁定独立 512-file ordinary workload、三次完整成功
  calibration baseline、semantic-only strict intersection、固定 midpoint 与单次
  fault attempt；同时禁止测试侧 `requestStop`、retry、spin、expanded
  admission、synthetic calibration，以及可由 interrupt/relative wait 伪造的
  deadline。paired gate 检查 absolute-nanos/terminal-selection runtime DEX 与
  androidTest fixture/evidence DEX，仍不代替设备执行；
- release APK gate 要求 public key/pipeline markers 存在，且 DEBUG fault-control
  markers 不存在；这里只宣称 service APK release 经 R8/minify，不宣称 fixture
  release 自身 minified；
- API 30+ FD flags 使用 public `Os.fcntlInt(F_GETFL)`，API 24–29 使用四 ABI
  JNI/libc bridge；online supervisor handshake 使用 Binder UID/PID、双方
  self identity、固定 process name、256-bit token 与 exact descriptor/Binder
  death，不再依赖跨 PID procfs；
- binary APK manifest gate 保留完整 XML QName，只把 Android namespace 属性计入
  contract；同时精确验证 compiler service 的 exported/permission/action/category/
  process、application/service effective enabled 状态、整个 application 内唯一
  compiler endpoint，以及 fixture self-target、独立 application ID、permission
  和 package query。

P3b-b2c 历史 no-device aggregate：

```powershell
.\gradlew.bat verifyR2PublicPreviewFixture
```

最终结果为 `BUILD SUCCESSFUL in 1m 22s`（513 tasks：50 executed、
463 up-to-date）；定向 P3b-b2c source/APK + release-compatibility gate 为
309 tasks：25 executed、284 up-to-date。

P3b-b2d 在根 aggregate 中新增以下定向 source/APK gate：

```powershell
.\gradlew.bat :r2-public-preview:verifyR2P3bB2dExternalExact64SourcePolicy
.\gradlew.bat :r2-public-preview:verifyR2P3bB2dExternalExact64Apks
.\gradlew.bat verifyR2PublicPreviewFixture
```

加入 b2d gate 后，最终根 aggregate 为 `BUILD SUCCESSFUL in 49s`
（604 actionable tasks：61 executed、543 up-to-date）；上方 `1m 22s` /
513-task 数据只记录 b2c 历史切片，不是最终 b2d aggregate。

P3b-b2e 在 app core aggregate 中新增：

```powershell
.\gradlew.bat :app:verifyR2P3bB2eNaturalIngressTimeoutSourcePolicy
.\gradlew.bat :app:verifyR2P3bB2eNaturalIngressTimeoutAndroidTestApk
.\gradlew.bat verifyR2PublicPreviewFixture
```

加入 b2e gate 后，最终根 aggregate 为 `BUILD SUCCESSFUL in 1m 7s`
（606 actionable tasks：82 executed、524 up-to-date）；desktop 4,607-file
ingress 为 372 ms，retained heap baseline/boundary/replay 为
`22.6 / 23.7 / 24.0 MiB`。上方 604-task 数字只记录 b2d 历史切片，不是当前
最终 aggregate。

P3b-b2f 在 fixture aggregate 中新增：

```powershell
.\gradlew.bat :r2-public-preview:verifyR2P3bB2fExternalPlusOneCleanupSourcePolicy
.\gradlew.bat :r2-public-preview:verifyR2P3bB2fExternalPlusOneCleanupApks
.\gradlew.bat verifyR2PublicPreviewFixture
```

device-discovered API 24 pre-ACK/API 35 cold-supervisor hardening 后、最终 gate
patch 前的定向构建为 `BUILD SUCCESSFUL in 1m 22s`（412 actionable tasks：
63 executed、349 up-to-date）。最终根 aggregate 为
`BUILD SUCCESSFUL in 1m 32s`（608 actionable tasks：68 executed、
540 up-to-date）；desktop 4,607-file ingress 为 3,176 ms，512 MiB old-space
retained heap baseline/boundary/replay 为 `22.6 / 23.7 / 24.0 MiB`。上方
606-task 数字只记录 b2e 历史切片。

P3b-b2g 在 app/root aggregate 中新增：

```powershell
.\gradlew.bat :app:verifyR2P3bB2gNaturalPhaseCancellationSourcePolicy
.\gradlew.bat :app:verifyR2P3bB2gNaturalPhaseCancellationAndroidTestApk
.\gradlew.bat verifyR2PublicPreviewFixture
```

第二个门禁同时依赖 `assembleDebug` 与 `assembleDebugAndroidTest`，分别检查成对
主应用/测试 APK 的 DEX evidence shape。它关闭了新测试 APK 与旧主应用 APK
误配的 preflight 缺口；source gate 与 paired-APK gate 均已 PASS。加入 b2g
gate 后，最终根 aggregate 为 `BUILD SUCCESSFUL in 2m 2s`
（610 actionable tasks：110 executed、500 up-to-date）；desktop retained heap
baseline/boundary/replay 为 `22.6 / 23.8 / 24.0 MiB`。上方 608-task 数字只记录
b2f 历史切片。

P3b-b2h 在 app/root aggregate 中新增：

```powershell
.\gradlew.bat :app:verifyR2P3bB2hNaturalPhaseTimeoutSourcePolicy
.\gradlew.bat :app:verifyR2P3bB2hNaturalPhaseTimeoutAndroidTestApk
.\gradlew.bat verifyR2PublicPreviewFixture
```

source gate 锁定 request-start absolute nanos、interrupt-resilient wait、
completion/timeout terminal selection、三次真实 baseline strict intersection 与
单次 semantic fault；paired-APK gate 同时检查主应用和 androidTest DEX。两项 gate
与 API 24/35 定向 runtime 均已 PASS。修复 completion/deadline TOCTOU 后的两项
b2h 定向门禁为 `BUILD SUCCESSFUL in 1m 4s`（140 actionable tasks：20
executed、120 up-to-date）；最终根 aggregate 为
`BUILD SUCCESSFUL in 1m 45s`（612 actionable tasks：78 executed、
534 up-to-date）。上方 `2m 2s` / 610-task 数据只记录 b2g 历史切片。

最终 b2d normal app debug/release SHA-256 分别为
`83fca363539ae0a4b8e0fc1562d81b4e92b97d1230c2ecfc384b13f157862168` /
`4c51525c05ed5be116d2c90bf59cd8a4eb7559477ead516f7a90534c9076afb5`；
TEST_ONLY evidence debug/release-equivalent 分别为
`70a22016ba9465026cc0ee7491aceb975b12cdf07139e6b3dadb7ea7212441bb` /
`602437f32ccbe0b835d6dada2a42f562479d46483ddb8fdc675b10aa44cc45fd`；
fixture debug/release 分别为
`e71741788c026a3e52e3f2153b657ae83337a562fdec4840ea6d2197f232dd6d` /
`76c4b289c9a81172a6c247a51fd0f42d324ed04d55f2fcfae89d7b5a9b4f9bb6`。
不得复用 TEST_ONLY hardening 前的 evidence artifact hash。

设备证据必须在明确选定的 disposable serial 上串行执行，不能使用无 `-s` 的隐式
设备选择。debug 组合：

```powershell
adb -s <serial> install -r app\build\outputs\apk\debug\autojs6-plugin-typescript-engine-v0.5.0-universal.apk
adb -s <serial> install -t -r r2-public-preview\build\outputs\apk\debug\r2-public-preview-debug.apk
adb -s <serial> shell am instrument -w -r io.github.supermonster003.autojs6.plugin.typescript.publicpreview/io.github.supermonster003.autojs6.plugin.typescript.publicpreview.R2PublicPreviewInstrumentation
```

release 组合使用对应 `release` 目录与 `r2-public-preview-release.apk`，instrumentation
component 不变。每个支持 API/WebView 组合都必须保留 serial、系统/WebView 版本、
app/fixture APK SHA-256、runner 输出和耗时；debug 通过不能替代 minified release
通过。用于下述 P3b-b2c 历史 matrix 的 app debug/release SHA-256 分别为
`89783d4d52aa9a23a0731a49f40cb14a58aac96150d61d5eb5566ecc6ac28054` /
`03dd61e71b1071e0b4ead41dd1d9a78f13ce740f15bb036732ef400231e29ddb`，
fixture debug/release 分别为
`8f0d4dff497c1fddafbd6297af39d3f9162a08ea068f07607de85f596b241008` /
`2136073c16db0172f35ecf42e1254bc4f3c5d2cfcd74d6b0b3e9c3c851c3b029`。

下表是 P3b-b2c ordinary production-fence 历史矩阵：

| API | service variant | fixture UID / plugin UID | invalid / inclusive / `+1` timings | runner |
| ---: | --- | --- | --- | --- |
| 24 | debug | `10078 / 10090` | `1,543 / 9,876 / 2,205 ms` | PASS；code -1；ADB 0；wall 13,894 ms |
| 24 | minified service release | `10078 / 10090` | `1,608 / 9,060 / 1,672 ms` | PASS；code -1；ADB 0；wall 12,631 ms |
| 35 | debug | `10314 / 10312` | `1,799 / 19,867 / 1,532 ms` | PASS；code -1；ADB 0；wall 23,706 ms |
| 35 | minified service release | `10314 / 10312` | `1,596 / 7,861 / 1,338 ms` | PASS；code -1；ADB 0；wall 11,218 ms |

P3b-b2d 使用相同 external fixture component，但 app 改为 non-shipping
evidence variant，并显式增加 `-e externalCapacityEvidence true`：

| API | evidence variant | fixture UID / plugin UID | ordinary b2c + four exact-64 boundaries + successor | runner |
| ---: | --- | --- | --- | --- |
| 24 | TEST_ONLY `r2CapacityEvidenceDebug` | `10078 / 10090` | PASS；normal debug no-arg PASS，15,369 ms，无 capacity event | code -1；ADB 0；wall 113,715 ms |
| 24 | TEST_ONLY minified/non-debuggable `r2CapacityEvidenceRelease` | `10078 / 10090` | PASS；normal release no-arg PASS，10,510 ms，无 capacity event | code -1；ADB 0；wall 68,213 ms |
| 35 | TEST_ONLY `r2CapacityEvidenceDebug` | `10314 / 10312` | PASS；normal debug no-arg PASS，21,787 ms，无 capacity event | code -1；ADB 0；wall 263,569 ms |
| 35 | TEST_ONLY minified/non-debuggable `r2CapacityEvidenceRelease` | `10314 / 10312` | PASS；normal release no-arg PASS，12,354 ms，无 capacity event | code -1；ADB 0；wall 106,751 ms |

exact container/expanded accounting、quota axis、output bytes 与逐 case timing 见
[R2-P3b-b2d External Exact-64-MiB Capacity Evidence Pipeline](EXTERNAL_EXACT64_PIPELINE_R2_P3B_B2D.md)。

P3b-b2e 使用 ordinary debug app/androidTest APK；最终文件分别为
5,089,165 / 1,491,255 bytes，SHA-256 分别为
`83fca363539ae0a4b8e0fc1562d81b4e92b97d1230c2ecfc384b13f157862168` /
`696197b5158c3adb34287cd84ef309f228de5560d172535952d819f25257a26a`。
四项定向设备结果：

| API / provider | direct fixed-5s 三轮 partial ingress | Binder fixed-10s exact64 | runner |
| --- | --- | --- | --- |
| API 24 / Chrome `119.0.6045.194` | accepted `12,976,128 / 13,893,632 / 14,024,704` of `70,893,622` bytes；cursor `2/2`；Worker `1/2/3` | terminal/wall `10,338 / 10,358 ms`；final cleanup + successor PASS | `20.131 s + 24.704 s`；2/2 PASS |
| API 35 / WebView `130.0.6723.86` | accepted `3,604,480 / 3,538,944 / 4,259,840` of `70,893,622` bytes；cursor `1/1`；Worker `1/2/3` | terminal/wall `10,324 / 10,333 ms`；final cleanup + successor PASS | `19.218 s + 37.831 s`；2/2 PASS |

Binder deadline 可能在 staging/validation/spool 或 host ingress 到期；表中的
partial-ingress 数据只来自 direct-host telemetry。完整说明与精确复跑命令见
[R2-P3b-b2e Natural Ingress Timeout and Cleanup](WORKER_NATURAL_INGRESS_TIMEOUT_R2_P3B_B2E.md)。

P3b-b2f 最终候选 artifact 为：

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| normal debug app APK | 5,593,036 | `7db1fbaf6041fcaeb90055c8126e4331e1e7b9671a88bf3397fe88525f3c9726` |
| normal release app APK | 2,711,128 | `42c7b5a4dee4b64f344088510978bbb9944b835bd48c93ade54aa1f74bf88a13` |
| TEST_ONLY evidence debug app APK | 5,560,679 | `552d9c315b7cd654b9b7ef463ee99168c3cb5f2ae79ee3f0eeb5c27efa99e503` |
| TEST_ONLY evidence release-equivalent app APK | 2,711,140 | `dcbbea9d31609e883d366508f394ca828aeb026cc5e1e82bcac04cd5ef88cfe2` |
| fixture debug APK | 1,012,953 | `ce514c781128896c7cd966926a466a5ec176f07f86e71928df5380b567ef0583` |
| fixture release APK | 717,950 | `d2df097faa675e39cc02de8bac4199a5b08b81d58b5343ac750a7b2f5b75685c` |

六包共同的 V2 signer SHA-256 为
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`。
normal Debug/Release 的 `testOnly` 均为 false，`debuggable` 分别为
true/false；evidence 与 fixture Debug/Release 的 `testOnly` 均为 true，
`debuggable` 分别为 true/false，且 fixture 恰有一个 self-target
instrumentation。normal release DEX 不含 b2f cleanup prefix/class/epoch-domain
marker；evidence release-equivalent DEX 保留所需 telemetry marker，并生成非空
R8 mapping。

最终 API 24/35 runtime matrix 如下。`O` 表示 ordinary production-fence `+1`，
`S` 表示 STORED container `+1`，`D` 表示 DEFLATED expanded `+1`；每格为
`rejection → ordinary successor`：

| API / provider | app variant | O / S / D（ms） | runner wall |
| --- | --- | --- | ---: |
| API 24 / Chrome `119.0.6045.194` | normal Debug | `2,212 → 15,703 / — / —` | 18.427 s |
| API 24 / Chrome `119.0.6045.194` | normal Release | `1,536 → 6,437 / — / —` | 8.274 s |
| API 24 / Chrome `119.0.6045.194` | TEST_ONLY evidence Debug | `2,376 → 8,827 / 3,666 → 8,348 / 5,610 → 9,607` | 38.726 s |
| API 24 / Chrome `119.0.6045.194` | TEST_ONLY evidence Release | `1,793 → 6,975 / 3,670 → 6,394 / 5,288 → 6,132` | 30.570 s |
| API 35 / WebView `130.0.6723.86` | normal Debug | `1,883 → 20,300 / — / —` | 23.002 s |
| API 35 / WebView `130.0.6723.86` | normal Release | `1,575 → 8,821 / — / —` | 10.893 s |
| API 35 / WebView `130.0.6723.86` | TEST_ONLY evidence Debug | `2,371 → 20,285 / 2,955 → 19,176 / 5,240 → 20,186` | 70.929 s |
| API 35 / WebView `130.0.6723.86` | TEST_ONLY evidence Release | `1,856 → 9,240 / 2,235 → 8,657 / 4,440 → 8,919` | 35.879 s |

8/8 runner 均 PASS。每个 evidence `O/S/D` case 都在
`startCompilation` 返回后、ACK 前证明 active lease 为 1 且 server
input/output exact-target FD 为 `1/1`，并证明此时不存在 matching terminal
receipt。coordinator 完成清理、创建含全零 pre-callback resource snapshot 的
receipt 后才发布 callback；fixture 在 callback 后读取并关联 receipt，随后 5 次
current snapshot 全零。每个 case 的 Binder 与 safe epoch 相同，API 24
fixture/plugin UID 为 `10078/10090`，API 35 为 `10314/10312`，crash/ANR
检索为空。最终均恢复 normal debug + fixture debug，manifest flags 与预期一致。

最终回归也保持通过：ordinary b2c 在 API 24 Debug/Release 为
`13.056/9.621 s`、API 35 为 `22.664/11.992 s`，4/4 PASS；b2d evidence 在
API 24 Debug/Release 为 `103.607/63.455 s`、API 35 为
`263.179/106.820 s`，4/4 PASS。完整 b2f 证据、设备 hardening 与复跑边界见
[R2-P3b-b2f External Plus-one Cleanup Receipt Evidence](EXTERNAL_PLUS_ONE_CLEANUP_R2_P3B_B2F.md)。

P3b-b1 已运行 plugin same-UID debug capacity matrix；P3b-b2a 已运行 same-UID
debug phase-entry/active-renderer matrix；P3b-b2b 已运行 repeated resource/cold
orphan matrix。P3b-b2c 已完成 external production-fence，P3b-b2d 又完成
non-shipping external exact-64 source/APK policy 与 API 24/35 final TEST_ONLY
四行，ordinary b2c regression 也已 PASS；P3b-b2e 再完成 API 24/35 natural
ingress timeout/final cleanup 4/4；P3b-b2f 再完成 external scope-free
ordinary/STORED/DEFLATED `+1` cleanup receipt 与 same-Binder successor 8/8；
P3b-b2g 又完成 API 24/35 ordinary same-UID Debug 的自然
parse/semantic/emit cancellation 6/6；P3b-b2h 再完成同一 direct-host 边界的
自然 semantic request-start absolute-deadline timeout 2/2。后续 b2i/b2j/b2k
依次补 ordinary parse、历史 emit 与 external-UID Binder emit；b2k 后的无编号
follow-up 又完成 API 24/35 × TEST_ONLY evidence Debug/minified non-debuggable
Release-equivalent 的 external-UID Binder semantic timeout 4/4；第二个无编号
follow-up 又完成相同四行边界的 external-UID Binder parse timeout 4/4。API 24 正式
provider 为 Chrome `119.0.6045.194`；Google WebView `53.0.2785.124` 只形成
fail-closed compatibility exploration。第三个无编号 follow-up 又完成 API 24/35
真正 normal Release natural parse timeout 2/2；该历史证据链最终的 internal
provisional evidence 为 **29/29**；按旧口径，Public R2 为 **0/54**。
normal/shipping Release natural semantic/emit、LMK/OOM、
Unicode/high-token/adversarial、output 128/256 MiB、formal schemas、C7 selector/root
activation 与 NodeNext 仍未完成；raw config identity/parser、41-key effective options 与
escape closure 已由 C2/C3/C4 在 internal branch 完成，C5 package metadata 仍未开始。

完整 P3b-b memory/fault gates 完成前，这些 provisional slices 不能关闭 Public
R2 的 54 项 checklist。P2 profile/config
authority 的完整边界见
[R2 Provisional Profile and Config Authority](PROFILE_AUTHORITY_R2_PROVISIONAL.md)，
P3b-a 见
[R2-P3b-a Worker Input Capacity and Lifetime](WORKER_INPUT_R2_P3B_A.md)，P3b-b1 见
[R2-P3b-b1 Android Device Capacity and Exact Realm Retirement](WORKER_INPUT_R2_P3B_B1.md)，
P3b-b2a 见
[R2-P3b-b2a DEBUG Phase-entry Hard-stop and Active Renderer-loss](WORKER_FAULT_R2_P3B_B2A.md)，
P3b-b2b 见
[R2-P3b-b2b Repeated Resource and Startup Orphan Cleanup](WORKER_RESOURCE_R2_P3B_B2B.md)，
P3b-b2c 见
[R2-P3b-b2c External Production-fence Pipeline](EXTERNAL_FENCE_PIPELINE_R2_P3B_B2C.md)，
P3b-b2d 见
[R2-P3b-b2d External Exact-64-MiB Capacity Evidence Pipeline](EXTERNAL_EXACT64_PIPELINE_R2_P3B_B2D.md)，
P3b-b2e 见
[R2-P3b-b2e Natural Ingress Timeout and Cleanup](WORKER_NATURAL_INGRESS_TIMEOUT_R2_P3B_B2E.md)，
P3b-b2f 见
[R2-P3b-b2f External Plus-one Cleanup Receipt Evidence](EXTERNAL_PLUS_ONE_CLEANUP_R2_P3B_B2F.md)，
P3b-b2g 见
[R2-P3b-b2g Natural Parse/Semantic/Emit Cancellation](WORKER_NATURAL_PHASE_CANCELLATION_R2_P3B_B2G.md)，
P3b-b2h 见
[R2-P3b-b2h Natural Semantic Request-start Absolute-deadline Timeout](WORKER_NATURAL_SEMANTIC_TIMEOUT_R2_P3B_B2H.md)，
无编号 external-UID Binder semantic follow-up 见
[R2 External-UID Binder Natural Semantic Timeout](EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_R2.md)。
