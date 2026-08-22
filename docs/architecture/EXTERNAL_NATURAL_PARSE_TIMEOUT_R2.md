# R2 External-UID Binder Natural Parse Timeout Follow-up

> 路线提示（2026-08-01）：本文是 Rebaseline 前的历史证据文档；其中 28/28、29/29
> 与 Public 0/54 均按旧口径保留。当前 milestone 状态见
> [R2 Roadmap](../roadmap/R2.md)。

日期：2026-08-01

状态：本切片完成时 internal provisional evidence **28/28**；后续 normal/shipping
Release parse follow-up 将旧口径计数推进到 **29/29**，旧 Public R2 为 **0/54**。本页记录一个
**无编号** follow-up：standalone same-signer、distinct-UID fixture 经 public Binder
admission 进入 non-shipping TEST_ONLY evidence Debug 与 minified/non-debuggable
Release-equivalent 后，在 ordinary production fence 内自然命中 **parse**
request-start absolute-deadline timeout。它不是新的 Roadmap 编号，不启用任何公开
capability，也不是 normal/shipping Release 证明。

后续真正 normal Release 的 bounded public-progress 证明见
[SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_R2.md](SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_R2.md)；
它关闭下列第一项中的 shipping parse 子项，但不回写本页的历史 TEST_ONLY 证明边界。

## 证明边界

[WORKER_NATURAL_PARSE_TIMEOUT_R2_P3B_B2I.md](WORKER_NATURAL_PARSE_TIMEOUT_R2_P3B_B2I.md)
已经在 ordinary same-UID Debug direct host 上证明历史 parse timeout；
[EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_R2.md](EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_R2.md)
随后把自然 semantic timeout 推进到 external-UID Binder pipeline。本 follow-up 复用
已冻结的 public admission、absolute-nanosecond deadline、private phase receipt 与
pre-callback cleanup receipt seam，只补齐 external-UID/Binder 的自然 parse timeout：

- fixture 与 plugin 必须同签名但 UID 不同；service 继续由
  `org.autojs.permission.PLUGIN` signature permission 保护；fixture 与 evidence app
  都必须为 `TEST_ONLY`；
- 每个 artifact/device 只建立一个 Binder session。每次 request 在 ticket 返回后、
  ACK 前证明 server ownership，然后只做一次不可逆 admission，不 retry、不搜索
  fault、不调用 `requestStop`，也不请求 capacity scope；
- 先执行 **2 次**完整成功 precondition，再执行 **3 次**完整成功 baseline；只有三次
  baseline 的真实 parse sequence-2/completed timing 参与校准；
- midpoint 不 clamp，必须自然达到 public `6,000 ms` 最小 timeout，并在严格交集中
  左右各保留至少 `100 ms`；否则在 `faultAttempts=0` 时 fail closed；
- 校准后只执行 **1 次**正式 fault。它必须在 parse sequence 2 `8/32` 后、parse
  completed 前返回 `ERROR_TIMEOUT`、`compilation=null` 与 empty output；
- callback 前冻结 request-correlated phase/cleanup receipts；callback 后再证明 exact
  Worker replacement、512/512 input cursor 平衡释放与连续 5 次 terminal cleanup
  全零；
- fault 后在同一 Binder session、同一安全 compiler process epoch 使用 replacement
  Worker 运行一个小 successor，并重新验证 output、manifest 与 109/109 cursor
  cleanup。

“自然”仍表示没有 phase-entry hook、同步 Worker spin、synthetic timing sample、
expanded admission、phase-relative deadline 或隐藏 fault retry。本轮没有修改
production timeout policy；既有 coordinator/host/page/Worker 的 phase-generic seam
直接承担 parse 证明。

## 冻结 parse fixture authority

`R2ExternalNaturalEmitTimeoutProjectFixture.createParse(...)` 使用 TypeScript `6.0.3`、
ES2018 与 ordinary **512 files / 8 MiB per file / 8 MiB total** admission。32 个
source roots 进入 `rootNames`；一个 ambient declaration 与 371 个 reference-only
declarations 作为 caller source 一同传输，另安装 108 个 trusted declarations。

为了让 sequence 2 后存在稳定的真实 parse 尾部，又不增加 AST 总量与 heap 压力，
15,000 个 type-only imports 被固定分区为：

- roots 2/3/4 各 3,000 个，共 9,000 个，位于 sequence 2 前；
- root 8 含 6,000 个，位于 sequence 2 `8/32` 后；
- module 名连续覆盖 `probe-00000` 至 `probe-14999`。

root 8 同时触发 371-file complete DAG。最终 DAG 从 `graph370` 进入，每个 graph
文件只按升序引用编号更低、已经发现的文件；它仍精确遍历 68,635 条 50-byte
triple-slash reference，但不再形成随文件数增长的递归调用栈。

| 项目 | 冻结 authority |
|---|---:|
| roots / sequence-2 | 32 / `8/32` |
| pre/post-sequence imports | 9,000 / 6,000（post root 8） |
| graph | 371 files / 68,635 edges / entry 370 |
| root bytes / tokens | 961,148 / 150,192 |
| maximum root | 384,082 bytes / 60,006 tokens |
| ambient | 61 bytes / 11 tokens |
| graph bytes / tokens | 3,431,750 / 0 |
| caller files / bytes / tokens | 404 / 4,392,959 / 150,203 |
| installed declarations | 108 / 3,784,758 bytes / 86,329 tokens |
| aggregate input | 512 files / 8,177,717 bytes / 236,532 tokens |
| caller / aggregate path characters | 25,486 / 29,343（maximum 65） |
| input transport | 1,598 frames |
| caller inventory fingerprint | `334b1a3a940a741105c5222ca6c189c72dfb6c0ea994a72b86013df1fdbf72ef` |
| diagnostics | 0 |

每次成功 precondition/baseline 都完整重验 TypeScript raw output：

| raw files | raw JavaScript | raw Source Maps | raw total | aggregate SHA-256 |
|---:|---:|---:|---:|---|
| 64 | 5,962 bytes | 7,193 bytes | 13,155 bytes | `18f79093d9a09c3111740e282a3ad642576230c142d6b7d7c61bb9c7950ca038` |

fixture 还检查完整 public output ZIP inventory、canonical STORED ZIP32、revision-2
diagnostics、每个 v3 Source Map、manifest bytes 与 terminal
`outputManifestSha256`，并从 packaged modules 无损重建 raw Worker bytes 后逐项比较
path/size/SHA。深目录 Source Map directive 会按 module parent depth 重建，不能假定
固定一个 `../`。input archive、request manifest、contract、profile/config、compiler
distribution、declarations 与 runtime-capabilities fingerprints 都必须由实际 payload
重算一致；任一层漂移都不能成为 baseline timing。

small successor 为 109 input files，raw output 固定为 2 files / 166 JavaScript bytes /
146 map bytes / 312 bytes，aggregate SHA-256 为
`0b504753f68a345d6aa82eeb174f6d9c63eec3cb21b7810ed09f69e59296be25`。

## 2 + 3 calibration、单次 fault 与 successor

每个 API/artifact 行都独立执行以下固定协议：

```text
2 successful preconditions
3 successful calibration baselines
strict parse intersection + unclamped midpoint
1 natural parse timeout fault
1 small same-Binder successor
```

所有 timing 都来自 immutable host evidence snapshot，并转换到对应 public Binder
request-start monotonic clock。三次 baseline 以 parse sequence 2 与 parse completed
构造严格交集：

```text
lower = max(floor(sequenceTwoNanos / 1 ms)) + 1 ms
upper = min(floor((completedNanos - 1 ns) / 1 ms))
timeout = lower + floor((upper - lower) / 2)
```

`upper - lower` 必须至少为 `200 ms`，timeout 左右 margin 必须各至少 `100 ms`，
且 midpoint 自身必须 `>= 6,000 ms`。实现不会把不足 6 秒的候选 clamp 到 public
minimum；任一 precondition、baseline、output、receipt、cleanup 或共同窗口失败，都会
在正式 fault 尚未执行时终止矩阵。

ticket 返回后的 pre-ACK ownership 使用最多 `80 ms`、每 `10 ms` 一次的有界只读
metadata sampling；`80 ms < 100 ms` calibration margin。unavailable 或非 exact
candidate 不缓存，只有 active lease `1` 且 input/output exact-target FD 为 `1/1`
才缓存。同一次 `getCompilerInfo()` 内的 pre-ACK capture 与 current snapshot 是两次
独立 descriptor enumeration；这不是 admission 或 fault retry。最终 API 24 Debug
successor 真实使用 2 个 samples / 18 ms 后收敛，其余请求在 1 个 sample 内命中。

正式 fault receipt 必须同时证明：

- outcome=`TIMED_OUT` / public `ERROR_TIMEOUT`，无 compilation payload，fsynced public
  output 为 0 bytes；
- completed phase timing 为空；history 为 entered=`ingress,parse`、
  completed=`ingress`；host stop phase 与 page termination phase 均为 `parse`；
- page termination progress 精确为 sequence 2 `8/32`；
- terminated Worker generation 与 replacement generation 精确相差 1；最终四行均为
  `1→2`；
- 1,598 frames / 8,177,717 payload bytes 完整送达，512 opened = 512 closed，active
  cursor 为空且 input released；
- stop selection 不早于 absolute deadline，overshoot 不超过 `5,000 ms`；
- phase receipt 与 terminal cleanup receipt 均在 callback 前创建，callback 后连续 5
  次 current samples 的 lease、input/output FD、spool、named transient 与 cursor
  全部为零。

private receipt 继续使用 non-shipping
`__autojs6_r2_external_natural_phase_timeout_` Bundle namespace，只能在 exact
same-signer/distinct-UID TEST_ONLY evidence attestation 下由 `getCompilerInfo()` 返回。
normal builds 保持 receipt disabled；它不增加 public compilation authority。

## 正式 Android runtime evidence

正式矩阵冻结为 API 24/35 × evidence Debug/Release-equivalent 四行。API 24 provider
为 Chrome `119.0.6045.194`；API 35 为 Google WebView `130.0.6723.86`。

| API / serial | model / ABI | build fingerprint | WebView provider |
|---|---|---|---|
| API 24 / `emulator-5556` | Android SDK built for x86 / x86 | `google/sdk_google_phone_x86/generic_x86:7.0/NYC/4409132:user/release-keys` | `com.android.chrome` `119.0.6045.194` |
| API 35 / `968e9f18` | 23046RP50C / arm64-v8a | `Xiaomi/liuqin/liuqin:15/AQ3A.241006.001/OS2.0.9.0.VMYCNXM:user/release-keys` | `com.google.android.webview` `130.0.6723.86` |

下表所有 timing 都是 public request-start 后的整数毫秒。`fault wall` 是 fixture 从
admission 前到 callback 的 client wall time；overshoot 是 exact stop selection 相对
absolute deadline 经毫秒截断后的非负值。

| API / artifact | precondition parse `seq2/completed` | baseline parse `seq2/completed` | strict window | timeout | margins L/R | fault wall / overshoot |
|---|---|---|---:|---:|---:|---:|
| API 24 / evidence Debug | `10209/13372`, `8740/11342` | `8671/11265`, `8785/11364`, `9295/11720` | `9296..11265` | `10280` | `984/985` | `10925 / 8` |
| API 24 / evidence Release-equivalent | `7710/10396`, `6138/8883` | `6319/8783`, `6093/8464`, `5857/8465` | `6320..8464` | `7392` | `1072/1072` | `8093 / 57` |
| API 35 / evidence Debug | `33909/35320`, `35859/37222` | `34485/35859`, `34281/35738`, `34251/35566` | `34486..35566` | `35026` | `540/540` | `35394 / 0` |
| API 35 / evidence Release-equivalent | `13742/15087`, `11760/13274` | `13358/15001`, `14492/16083`, `14020/15515` | `14493..15001` | `14747` | `254/254` | `15084 / 0` |

四行正式 fault 均为 `faultAttempts=1`、parse sequence 2 `8/32`、
`outputBytes=0`、512/512 cursor 与 Worker `1→2`；successor 随后成功返回上述 2-file
raw authority。每行都保持同一 Binder session、同一安全 process epoch，并通过五次
全零 cleanup samples。每行日志精确包含 2 条 precondition、3 条 baseline、1 条
window、1 条 fault、1 条 successor 和 1 条 matrix PASS marker，且各有 7 条 pre-ACK
ownership marker；无 FATAL、OOM、target crash、ANR 或显式 LMK kill。

API 24 两行与 API 35 Release-equivalent 的宿主 stdout 各只有一个
`INSTRUMENTATION_CODE: -1`。API 35 Debug 因单行实际耗时约 245 秒，宿主 ADB client
先命中 240 秒上限；设备端随后完成唯一 fault/successor/matrix marker，并由 events
buffer 记录 fixture `due to finished inst`。该行没有重发 fault，也没有把宿主超时
解释成编译 timeout。

定向命令固定使用显式 serial，且不传 external-capacity scope：

```powershell
adb -s emulator-5556 shell am instrument -w -r `
  -e externalNaturalParseTimeoutEvidence true `
  io.github.supermonster003.autojs6.plugin.typescript.publicpreview/io.github.supermonster003.autojs6.plugin.typescript.publicpreview.R2PublicPreviewInstrumentation

adb -s 968e9f18 shell am instrument -w -r `
  -e externalNaturalParseTimeoutEvidence true `
  io.github.supermonster003.autojs6.plugin.typescript.publicpreview/io.github.supermonster003.autojs6.plugin.typescript.publicpreview.R2PublicPreviewInstrumentation
```

## Fail-closed workload design history

正式 artifact 前有两次未计入矩阵的设计反馈：

1. 原始 workload 把 15,000 imports 全放在 sequence 2 前。API 24
   Release-equivalent 的三 baseline 只形成 `8711..8889 ms`、178 ms 宽的交集，低于
   200 ms 门槛；runner 在 `faultAttempts=0` 时停止。最终把既有 imports 重排为
   9,000 pre + 6,000 post，保持 aggregate bytes/tokens 与 heap 压力不变。
2. 重排后的第一版仍使用 371-file complete forward DAG。API 24 可完成，但 API 35
   WebView 130 在首个 precondition 报 `RangeError: Maximum call stack size exceeded`；
   没有 baseline 或 fault。最终 reverse-index DAG 保留相同文件数、字节数与边数，
   同时消除线性递归深度。

这两次结果都是 workload calibration/portability 的 fail-closed 记录，不是 timeout
runtime evidence，也不纳入正式 marker 计数。

## Artifact boundary 与本地门禁

四行正式矩阵使用同一次最终冻结的 paired artifacts：

| artifact | bytes | SHA-256 |
|---|---:|---|
| evidence Debug app | 5,647,904 | `9991077490a9078870b276cef866e06daacc0dfd90c9d168007cd76ea196f169` |
| evidence Release app | 2,729,000 | `52cd37cf6e24dbe586dddf10dfce809ce4ec056c1841181d07b8652729fa7291` |
| fixture Debug | 1,125,367 | `baf91243d9524cea6f571f9e30edd0c08a3323931a09f3b0bf626cb38b8f7175` |
| fixture Release | 744,870 | `3fbab9ba6ca0cc2b8e4e594ecea717d1547ca6f24b60376ebd6da3d16f058b7f` |
| evidence Release R8 mapping | 5,648,137 | `aadd181583c722af95659a5238f9a0d004bf056f06129a32a5a44775d2a4c18d` |

四个 APK 的 signer SHA-256 均为
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`。
evidence Release app 继承 Release、经 R8/minify、有非空 mapping、为 TEST_ONLY 且
non-debuggable；它仍是 evidence-only artifact。normal shipping Release 的 DEX
必须排除 private receipt namespace。

定向门禁为：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:verifyR2ProjectCompilerDesktop
.\gradlew.bat :r2-public-preview:verifyR2ExternalNaturalParseTimeoutSourcePolicy
.\gradlew.bat :r2-public-preview:verifyR2ExternalNaturalParseTimeoutApks
.\gradlew.bat :r2-public-preview:verifyR2PublicPreviewFixture --console=plain
```

最终无设备聚合把 app/API JVM tests、desktop TypeScript `6.0.3` golden、compiler
core 与完整 preview APK gate 合并执行，结果为 `BUILD SUCCESSFUL in 1m 20s`
（519 actionable tasks：59 executed、460 up-to-date）。旧 `b2g` source policy 的
golden 提取边界也已收紧到自身函数，避免把后续 natural-timeout 用例的进度 tuple
误计入其冻结的 6 条回执。

source policy 锁定 exact fixture authority、ordinary 512-file fence、低栈 DAG、
2+3 calibration、unclamped public-minimum midpoint、single admission/fault、parse
history/progress、output/manifest/receipt/cleanup authority，并禁止 retry/stop/spin/
expanded/synthetic seams。paired-APK gate 检查 fixture Debug/Release 与 evidence app
Debug/R8 Release-equivalent 的 markers、TEST_ONLY/signer boundary、normal Release
receipt exclusion 和 non-empty mapping；APK gate 不替代四行设备执行。

## 尚未证明

本 follow-up 不证明：

- normal/shipping Release 的自然 parse、semantic 或 emit timeout；
- 多轮 timeout stress、repeated large-job PSS/FD plateau；
- renderer LMK/OOM、force-stop 或其他 system fault；
- Unicode/high-token/adversarial 分布；
- 128 MiB container / 256 MiB expanded file-backed output；
- formal request/output/diagnostic schemas 或 tsconfig/NodeNext。

因此 ordinary production fence 继续为 **512 files、8 MiB/file、8 MiB total**；
`compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、`sourceMapV3`
四项 capability 继续全部为 `false`。本 follow-up 只把 internal provisional evidence
推进到 **28/28**；按当时旧口径，Public R2 checklist 为 **0/54**，没有公开 promotion。
