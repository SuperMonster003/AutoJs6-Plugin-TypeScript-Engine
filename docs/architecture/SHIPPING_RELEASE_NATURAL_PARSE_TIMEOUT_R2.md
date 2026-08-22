# R2 Normal/Shipping Release Natural Parse Timeout Follow-up

> 路线提示（2026-08-01）：本文是 Rebaseline 前的历史证据文档；其中 29/29 与
> Public 0/54 均按旧口径保留。当前 milestone 状态见
> [R2 Roadmap](../roadmap/R2.md)。

日期：2026-08-01

状态：按旧口径，internal provisional evidence **29/29**、Public R2 **0/54**。本页记录第三个
**无编号** follow-up：用 standalone same-signer、distinct-UID、TEST_ONLY fixture
驱动真正的 normal app **Release** APK，经 ordinary public Binder admission 在
production fence 内自然命中 **parse** request-start absolute-deadline timeout。
它覆盖 shipping Release 代码与打包边界，但仍是本地发布候选验证，不代表 Public R2
已经开放或该 APK 已对外发布。

## 为什么改用公开进度

[EXTERNAL_NATURAL_PARSE_TIMEOUT_R2.md](EXTERNAL_NATURAL_PARSE_TIMEOUT_R2.md)
已在 TEST_ONLY evidence Debug 与 minified/non-debuggable Release-equivalent 上证明
external-UID Binder parse timeout、exact Worker replacement、512/512 cursor release
和 five-zero cleanup。normal Release 必须排除这些 private phase/cleanup receipt
namespace，因此不能复用那条测试回执证明 shipping 边界。

冻结的 `ITypeScriptCompilationCallback` 已经包含 `onProgress(Bundle)`；本 follow-up
不修改 AIDL transaction surface，而是在 additive `TypeScriptR2Contract` 中加入
revision 1 的 provisional public progress envelope。生产 host 只有在 exact
request/session/Worker、phase 顺序和真实 work threshold 全部验证后，才把事件同步交给
coordinator。coordinator 再从不可逆 server request-start monotonic nanos 计算相对时间，
通过同一个 oneway callback Binder 入队；terminal pipeline 只能在更早的 progress
调用返回后继续。fixture 同时验证 terminal callback 位于全部 progress callback 之后。

## 有界公开 progress envelope

每个事件只包含 primitive 值，并且每次 compilation 最多 **14** 条：

| 事件 | 固定字段 |
| --- | --- |
| common | `requestId`、`elapsedMs`、`progressSchemaRevision=1`、`progressKind`、`phase`、`elapsedRealtimeNanosSinceRequestStart` |
| phase | common + `phaseState=started|completed` |
| work | common + `progressSequence=1|2`、`completedUnits`、`totalUnits` |

phase 顺序固定为 `ingress → parse → semantic → emit`。除 ingress 外，每个工作阶段
最多发布两个 real-work threshold：sequence 1 为 `ceil(total/8)`，sequence 2 为
`ceil(total/4)`。成功的主 workload 精确发布 14 条事件；small successor 因各阶段
工作总量低于 8，只发布 8 条 phase transition。parse fault 精确停在以下 5 条前缀：

1. ingress started；
2. ingress completed；
3. parse started；
4. parse sequence 1；
5. parse sequence 2。

Bundle parser 要求 exact keys/types、合法枚举、单调 nanos、精确毫秒投影与冻结顺序。
progress 不携带 source path、source/output text、diagnostic、绝对时钟、Worker identity
或 private cleanup telemetry。

## Release 与 caller 边界

设备 runner 在 admission 前逐项证明：

- target package 为 `io.github.supermonster003.autojs6.plugin.typescript` 的 normal
  Release，`testOnly=false`、`debuggable=false`，并具有非空 R8 mapping；
- fixture 为 `io.github.supermonster003.autojs6.plugin.typescript.publicpreview`，仅
  fixture 声明 `TEST_ONLY`；fixture 与 target UID 不同、签名相同；
- service exported 且受 signature permission 保护；
- session 的 private telemetry 未启用，identity 为 `null`；`getCompilerInfo()` 不含
  natural-phase 或 cleanup private prefix；
- normal Release DEX 保留 public progress markers，但排除
  `__autojs6_r2_external_natural_phase_timeout_` private namespace。

两份 APK 的 V2 signer SHA-256 均为
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`。

## Workload 与单故障规则

runner 复用上一 follow-up 的最终 ordinary parse authority：404 caller files +
108 installed declarations = exact 512 files，合计 `8,177,717` bytes、`236,532`
tokens、`1,598` input frames；未使用 expanded capacity。32 roots 中的 15,000 个
type-only imports 固定分布为 sequence-2 前 9,000 与 root 8 后 6,000；371-file
low-stack reverse-index complete DAG 从 graph370 进入。每次成功生成 64 raw outputs，
总计 `13,155` bytes，aggregate SHA-256 为
`18f79093d9a09c3111740e282a3ad642576230c142d6b7d7c61bb9c7950ca038`。

每台设备在一个 Binder session 中严格执行：

1. 2 次不计入 calibration 的完整成功 precondition；
2. 3 次完整成功 baseline；
3. 取三次 `parse seq2/completed` strict intersection 的 unclamped midpoint，且
   timeout 自然不低于 public 6,000-ms minimum；
4. 恰好 1 次 timeout fault；
5. 1 个 small same-Binder successor。

任何 precondition、baseline 或 strict-window 条件失败都必须在
`faultAttempts=0` 时 fail closed，不允许 retry 或第二次 fault。

## API 24/35 normal Release runtime

下表时间均为 server request-start 后的毫秒值；`seq2/completed` 来自 normal
Release 公开 progress，而不是 private receipt。

| API / provider | precondition `seq2/completed` | baseline `seq2/completed` | strict window / timeout | fault `seq2` / wall / terminal / overshoot |
| --- | --- | --- | ---: | ---: |
| API 24 / Chrome `119.0.6045.194` | `8204/10966`, `5864/8894` | `6117/8527`, `5739/8385`, `5760/8172` | `6118..8172 / 7145` | `6356 / 7616 / 7593 / 448` |
| API 35 / WebView `130.0.6723.86` | `13870/15151`, `14285/15847` | `14113/15450`, `14041/15652`, `14097/15591` | `14114..15450 / 14782` | `14475 / 15144 / 15142 / 360` |

两行 fault 都为 `faultAttempts=1`，公开 progress 精确为 5 条并终止于 parse
sequence 2 `8/32`；terminal 为 `ERROR_TIMEOUT`、无 compilation payload、同步后的
output size 为 0，且 `privateReceipt=false`。随后同一 Binder 上的 successor 精确收到
8 条 phase transition，109 个 input files 成功生成 2 outputs / 312 bytes，SHA-256
为 `0b504753f68a345d6aa82eeb174f6d9c63eec3cb21b7810ed09f69e59296be25`。

API 24 的 host-side instrumentation 正常返回 `INSTRUMENTATION_CODE: -1`。API 35
启动后的 host-side ADB waiter 被 5 秒命令超时分离，但 device instrumentation 继续
运行并只发布一组完整 `PRECONDITION/BASELINE/WINDOW/FAULT/SUCCESSOR/MATRIX_PASS`
markers；events buffer 随后记录 fixture 因 `finished inst` 退出。为保持单故障规则，
没有重跑该 fault。两台设备的证据时段均未发现 target plugin/fixture 的 FATAL、OOM、
ANR 或 LMK。API 24 crash buffer 中另有一条矩阵结束约 6 分钟后、重装 APK 时发生的
`uid=2000` shell Binder shutdown SIGSEGV，不属于 target 进程或测试窗口。

## 冻结 artifact

| artifact | bytes | SHA-256 |
| --- | ---: | --- |
| normal Release app APK | `2,712,604` | `b5602ed1a4911e2a0823d791d7a483e35f1560ad185c7c83f196ac13d9c8f177` |
| Release fixture APK | `750,558` | `62080a3feab741bd4ad7f65c1e61774f598aab63b94dec5b69614a2006cfe1b1` |
| normal Release R8 mapping | `5,168,255` | `4cf3cec830624b47ecba33d522f4e9feea56a0ad68e305bf170d7edfe667dc8b` |

normal app APK 路径为
`app/build/outputs/apk/release/autojs6-plugin-typescript-engine-v0.5.0-universal.apk`；
fixture 为 `r2-public-preview/build/outputs/apk/release/r2-public-preview-release.apk`。

## 本地门禁

新增的两个独立门禁为：

```powershell
.\gradlew.bat :r2-public-preview:verifyR2ShippingReleaseNaturalParseTimeoutSourcePolicy
.\gradlew.bat :r2-public-preview:verifyR2ShippingReleaseNaturalParseTimeoutApks
```

source gate 固定 additive protocol、host correlation、coordinator publication、strict
fixture parser、normal Release runtime attestation、2+3+1 single-admission 规则以及禁止
stop/retry/spin/expanded/private receipt。APK gate 固定 normal Release public marker、
private prefix exclusion、fixture runner/authority hashes 与 non-empty normal Release
mapping。

最终聚合命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  :plugin-api:typescript-api:testDebugUnitTest `
  :app:verifyR2ProjectCompilerDesktop `
  :app:verifyR2ProjectCompilerCore `
  :r2-public-preview:verifyR2PublicPreviewFixture --console=plain
```

结果为 `BUILD SUCCESSFUL in 1m 3s`，521 actionable tasks：81 executed、440
up-to-date。它包含 Kotlin/JVM tests、TypeScript 6.0.3 desktop golden、512 MiB
old-space retained-heap child、Debug/Release compile、normal Release R8、source 与 APK
门禁。

## 结论与剩余边界

本 follow-up 证明：真正的 normal Release artifact 可经 distinct-UID public Binder
ordinary admission，用不依赖 private receipt 的有界公开进度选择 strict parse
deadline；唯一 fault 在 host 已验证的 parse `8/32` 后超时，保持 empty output、
exactly-once terminal ordering，并由 same-Binder successor 证明服务仍可继续使用。

它不证明 TypeScript parser 指令级瞬时中断。公开 `8/32` 只表示 native host 已观察并
验证 Worker 的第二个真实 work threshold；deadline 到 terminal 之间仍包含 stop
selection、Worker termination 与 Binder delivery。normal Release 没有 private receipt，
所以本页也不声称 exact Worker generation replacement、512/512 cursor release 或
five-zero FD/transient convergence；shipping cleanup 证据限于 production terminal
gate/source tests、零输出与同 Binder successor。

仍开放的主要边界包括：

- normal/shipping Release 的 natural semantic 与 emit timeout；
- renderer/target LMK、OOM、force-stop 及完整 system/death fault matrix；
- Unicode/high-token/adversarial 分布与 repeated large-job PSS/FD plateau；
- 128 MiB container / 256 MiB expanded file-backed output；
- formal request/output/diagnostic schemas 与 tsconfig/NodeNext。

ordinary production fence 继续为 **512 files、8 MiB/file、8 MiB total**；
`compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、`sourceMapV3`
四项 capability 继续全部为 `false`。本 follow-up 只把 internal provisional evidence
推进到 **29/29**；按当时旧口径，Public R2 checklist 为 **0/54**，没有公开 promotion。
