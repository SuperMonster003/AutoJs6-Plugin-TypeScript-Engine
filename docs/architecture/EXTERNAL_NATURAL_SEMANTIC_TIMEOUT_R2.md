# R2 External-UID Binder Natural Semantic Timeout Follow-up

日期：2026-08-01

状态：本切片完成时 internal provisional evidence **27/27**；Public R2 **0/54**。本页记录一个
**无编号** follow-up：standalone same-signer、distinct-UID fixture 经 public Binder
admission 进入 non-shipping TEST_ONLY evidence Debug 与 minified/non-debuggable
Release-equivalent 后，在 ordinary production fence 内自然命中 **semantic**
request-start absolute-deadline timeout。它不是新的 Roadmap 编号，不启用任何公开
capability，也不是 normal/shipping Release 证明。

## 证明边界

[WORKER_NATURAL_SEMANTIC_TIMEOUT_R2_P3B_B2H.md](WORKER_NATURAL_SEMANTIC_TIMEOUT_R2_P3B_B2H.md)
已经在 ordinary same-UID Debug direct host 上证明 semantic timeout；
[EXTERNAL_NATURAL_EMIT_TIMEOUT_R2_P3B_B2K.md](EXTERNAL_NATURAL_EMIT_TIMEOUT_R2_P3B_B2K.md)
随后把自然 emit timeout 推进到 external-UID Binder pipeline。本 follow-up 复用已冻结的
public admission、absolute-nanosecond deadline、private phase receipt 与 pre-callback
cleanup receipt seam，只补齐 external-UID/Binder 的自然 semantic timeout：

- fixture 与 plugin 必须同签名但 UID 不同；service 继续由
  `org.autojs.permission.PLUGIN` signature permission 保护；fixture 与 evidence app
  都必须为 `TEST_ONLY`；
- 每个 artifact/device 只建立一个 Binder session。每次 request 在 accept ticket 与
  ACK 前 server ownership 复核后只做一次不可逆 admission，不 retry、不搜索 fault、
  不请求 capacity scope；
- 先执行 **2 次**完整成功 precondition，再执行 **3 次**完整成功 baseline；只有三次
  baseline 的真实 semantic sequence-2/completed timing 参与校准；
- midpoint 不 clamp，必须自然达到 public `6,000 ms` 最小 timeout，并在严格交集中
  左右各保留至少 `100 ms`；否则在 `faultAttempts=0` 时 fail closed；
- 校准后只执行 **1 次**正式 fault。它必须在 semantic sequence 2 `64/256` 后、
  semantic completed 前返回 `ERROR_TIMEOUT`、`compilation=null` 与 empty output；
- callback 前冻结 request-correlated phase/cleanup receipts；callback 后再证明 exact
  Worker replacement、512/512 input cursor 平衡释放与连续 5 次 terminal cleanup 全零；
- fault 后在同一 Binder session、同一安全 compiler process epoch 使用 replacement
  Worker 运行一个小 successor，并重新验证 output、manifest 与 109/109 cursor cleanup。

“自然”仍表示没有 phase-entry hook、同步 Worker spin、测试侧 `requestStop`、
synthetic timing sample、expanded admission、phase-relative deadline 或隐藏 fault retry。
本轮没有改动 production core timeout policy；既有 coordinator/host/page/Worker 的
phase-generic seam 直接承担 semantic 证明。

## 冻结 semantic fixture authority

`R2ExternalNaturalEmitTimeoutProjectFixture.createSemantic(...)` byte-for-byte 移植
internal semantic workload generator，并继续使用 TypeScript `6.0.3`、ES2018 和
ordinary **512 files / 8 MiB per file / 8 MiB total** admission。只有 256 个 source
roots 进入 `rootNames`；148 个 late helpers 作为 caller source 一同传输，另安装 108
个 trusted declarations。fixture 同时断言 aggregate file count `<= 512` 且
`== 512`，没有扩容或 capacity evidence scope。

| 项目 | 冻结 authority |
|---|---:|
| roots | 256（64 light + 192 heavy） |
| heavy-root private fields | 8 / heavy root |
| late helpers | 148（72 atoms / helper，24 KiB whitespace / helper） |
| root bytes / tokens | 178,722 / 36,928 |
| maximum root | 811 bytes / 169 tokens |
| helper bytes / tokens | 3,763,492 / 28,268 |
| maximum helper | 25,429 bytes / 191 tokens |
| caller files / bytes / tokens | 404 / 3,942,214 / 65,196 |
| installed declarations | 108 / 3,784,758 bytes / 86,329 tokens |
| aggregate input | 512 files / 7,726,972 bytes / 151,525 tokens |
| caller / aggregate path characters | 15,392 / 19,249 |
| input transport | 1,588 frames |
| caller inventory fingerprint | `460b3b9755e2669fb789c3190d9f6bc14e42c9f6cbe87bbf81cbe0d97bf6b3eb` |
| diagnostics | 0 |

每次成功 precondition/baseline 都完整重验 TypeScript raw output：

| raw files | raw JavaScript | raw Source Maps | raw total | aggregate SHA-256 |
|---:|---:|---:|---:|---|
| 512 | 535,202 bytes | 185,664 bytes | 720,866 bytes | `4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1` |

验证不止比较 aggregate digest。fixture 还检查完整 public output ZIP inventory、
canonical STORED ZIP32、revision-2 diagnostics、每个 v3 Source Map、manifest bytes 与
terminal `outputManifestSha256`，并从 packaged modules 无损重建 raw Worker bytes 后
逐项比较 path/size/SHA。input archive、request manifest、contract、profile/config、
compiler distribution、declarations 与 runtime-capabilities fingerprints 都必须由实际
payload 重算一致；任一层漂移都不能成为 baseline timing。

small successor 是一个 caller root 加 108 declarations：109 files / 3,784,795 bytes /
86,337 tokens / 379 input frames。它的 raw output 固定为 2 files / 166 JavaScript bytes /
146 map bytes / 312 bytes，aggregate SHA-256 为
`0b504753f68a345d6aa82eeb174f6d9c63eec3cb21b7810ed09f69e59296be25`。
successor 不参与 calibration，只证明 timeout 后同 Binder、同 process epoch 和
replacement Worker 仍可安全完成 ordinary request。

## 2 + 3 calibration、单次 fault 与 successor

每个 API/artifact 行都独立执行以下固定协议：

```text
2 successful preconditions
3 successful calibration baselines
strict semantic intersection + unclamped midpoint
1 natural semantic timeout fault
1 small same-Binder successor
```

所有 timing 都来自 immutable host evidence snapshot，并转换到对应 public Binder
request-start monotonic clock。三次 baseline 以 semantic sequence 2 与 semantic
completed 构造严格交集：

```text
lower = max(floor(sequenceTwoNanos / 1 ms)) + 1 ms
upper = min(floor((completedNanos - 1 ns) / 1 ms))
timeout = lower + floor((upper - lower) / 2)
```

`upper - lower` 必须至少为 `200 ms`，timeout 左右 margin 必须各至少 `100 ms`，
且 midpoint 自身必须 `>= 6,000 ms`。实现不会把不足 6 秒的候选 clamp 到 public
minimum；precondition、baseline、output、receipt 或 cleanup 失败，以及三条 baseline
没有合法共同窗口，都会在正式 fault 尚未执行时终止矩阵。

正式 fault 只调用一次 `startCompilation`。timeout receipt 必须同时证明：

- outcome=`TIMED_OUT` / public `ERROR_TIMEOUT`，无 compilation payload，fsynced public
  output 为 0 bytes；
- phase timing 只含已完成的 `parse`；history 为
  entered=`ingress,parse,semantic`、completed=`ingress,parse`；host stop phase 与 page
  termination phase 均为 `semantic`；
- page termination progress 精确为 sequence 2 `64/256`；
- terminated Worker generation 与 replacement generation 精确相差 1；本次正式四行
  都记录 `1→2`；
- 1,588 frames / 7,726,972 payload bytes 完整送达，512 opened = 512 closed，active
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

正式矩阵冻结为 API 24/35 × evidence Debug/Release-equivalent 四行。API 24 当前可执行
provider 为 Chrome `119.0.6045.194`；API 35 为 Android System WebView
`130.0.6723.86`。

| API / serial | model / ABI | build fingerprint | WebView provider |
|---|---|---|---|
| API 24 / `emulator-5556` | Android SDK built for x86 / x86 | `google/sdk_google_phone_x86/generic_x86:7.0/NYC/4409132:user/release-keys` | `com.android.chrome` `119.0.6045.194`（enabled=1） |
| API 35 / `968e9f18` | 23046RP50C / arm64-v8a | `Xiaomi/liuqin/liuqin:15/AQ3A.241006.001/OS2.0.9.0.VMYCNXM:user/release-keys` | `com.google.android.webview` `130.0.6723.86` |

下表所有 precondition、baseline、window、timeout 与 margin 都是 public request-start
后的整数毫秒。`fault wall` 是 fixture 从 admission 前到 callback 的 client wall time；
overshoot 是 exact stop selection 相对 absolute deadline 经毫秒截断后的非负值。

| API / artifact | precondition semantic `seq2/completed` | baseline semantic `seq2/completed` | strict window | timeout | margins L/R | fault wall / overshoot | runner |
|---|---|---|---:|---:|---:|---:|---:|
| API 24 / evidence Debug | `21094/30983`, `12289/16897` | `12362/17249`, `10879/16212`, `11430/15805` | `12363..15805` | `14084` | `1721/1721` | `14689 / 3` | `~149.2 s` |
| API 24 / evidence Release-equivalent | `9646/14234`, `7946/12591` | `7172/11401`, `7366/11775`, `7417/12112` | `7418..11401` | `9409` | `1991/1992` | `9919 / 12` | `~98.2 s` |
| API 35 / evidence Debug | `37333/40332`, `37426/40498` | `38364/41412`, `38796/41791`, `36360/39335` | `38797..39335` | `39066` | `269/269` | `39424 / 0` | `~285.1 s` |
| API 35 / evidence Release-equivalent | `13867/16840`, `13233/16255` | `13637/16452`, `13224/16265`, `13639/16649` | `13640..16265` | `14952` | `1312/1313` | `15238 / 0` | `~119.8 s` |

四行正式 fault 均为 `faultAttempts=1`、semantic sequence 2 `64/256`、
`outputBytes=0`、512/512 cursor 与 Worker `1→2`；successor 随后成功返回上述 2-file
raw authority。每行都保持同一 Binder session、同一安全 process epoch，并通过五次
全零 cleanup samples。每个最终显式 serial runner stdout 都只有一个
`INSTRUMENTATION_CODE: -1`。

每行日志精确包含 2 条
`EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_PRECONDITION_PASS`、3 条
`EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_BASELINE_PASS`、1 条
`EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_WINDOW_PASS`、1 条
`EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_FAULT_PASS`、1 条
`EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_SUCCESSOR_PASS` 和 1 条
`EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_MATRIX_PASS`。marker 计数与唯一 instrumentation
终态共同排除隐藏 fault retry。

定向命令固定使用显式 serial，且不传 external-capacity scope：

```powershell
adb -s emulator-5556 shell am instrument -w -r `
  -e externalNaturalSemanticTimeoutEvidence true `
  io.github.supermonster003.autojs6.plugin.typescript.publicpreview/io.github.supermonster003.autojs6.plugin.typescript.publicpreview.R2PublicPreviewInstrumentation

adb -s 968e9f18 shell am instrument -w -r `
  -e externalNaturalSemanticTimeoutEvidence true `
  io.github.supermonster003.autojs6.plugin.typescript.publicpreview/io.github.supermonster003.autojs6.plugin.typescript.publicpreview.R2PublicPreviewInstrumentation
```

## API 24 WebView 53 探索记录

正式矩阵前曾把 API 24 provider 恢复为 `com.google.android.webview`
`53.0.2785.124`，以检查历史 provider 能否承载当前 semantic binary。该探索没有形成
可计入矩阵的成功 precondition：WebView 53 加载当前
`typescript-compiler-r1/compiler-host.js` 时在 line 6 报
`Uncaught SyntaxError: Unexpected identifier`；该行包含现代 numeric separator
`4_607`。首个 precondition 因 host 从未 ready 而在 `120035 ms` timeout，runner
返回 `INSTRUMENTATION_CODE: 0`，没有 baseline timing，`faultAttempts=0`。

这是一条预期的 **fail-closed compatibility observation**，不是 semantic timeout
runtime evidence，也不纳入上面的四行统计。切回 Chrome 119 后清理 plugin/fixture
state 与 logcat，才执行并保留 API 24 正式矩阵。该观察只约束“当前 semantic
follow-up binary + WebView 53”，不追溯改变历史 emit 文档所记录的 artifact/runtime
authority。

## Artifact boundary 与本地门禁

四行正式矩阵使用同一次最终冻结重建的 paired artifacts：

| artifact | bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/r2CapacityEvidenceDebug/autojs6-plugin-typescript-engine-v0.5.0-r2-capacity-evidence-debug-universal.apk` | 5,647,700 | `b4ef844810d23b954f96856289c602f14e2a618d043d9cc8f087cefd3c1b1f53` |
| `app/build/outputs/apk/r2CapacityEvidenceRelease/autojs6-plugin-typescript-engine-v0.5.0-r2-capacity-evidence-release-universal.apk` | 2,729,000 | `e9fd53caae16b08b00ae32d366baac3759f5beab3fa1f10c29fb97909253a78c` |
| `r2-public-preview/build/outputs/apk/debug/r2-public-preview-debug.apk` | 1,110,581 | `c1eb953d2230aea801bb8350052f40b34ba93b069b701748a01bbfb1621517f0` |
| `r2-public-preview/build/outputs/apk/release/r2-public-preview-release.apk` | 740,786 | `b920d09d6cbdedb32cda99bc42fb72f2b68a20d28558bc8faa6e54a549e23139` |
| `app/build/outputs/mapping/r2CapacityEvidenceRelease/mapping.txt` | 5,646,401 | `67ab866d274026fd9eed0bbdfd30ae5bd466b1561ada586b01e060ec87dceeb0` |

四个 APK 的 signer SHA-256 均为
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`。
evidence Release-equivalent app APK 继承 Release、经 R8/minify、有非空 mapping、
runtime package flags 含 `TEST_ONLY` 且不含 `DEBUGGABLE`；它仍是 evidence-only
artifact。standalone fixture Release 同样为 TEST_ONLY/non-debuggable companion，但
不据此声称自身承担 app 的 R8/minification authority。normal shipping Release 的
DEX 必须排除 private receipt namespace。

定向门禁为：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :r2-public-preview:verifyR2ExternalNaturalSemanticTimeoutSourcePolicy
.\gradlew.bat :r2-public-preview:verifyR2ExternalNaturalSemanticTimeoutApks
.\gradlew.bat :r2-public-preview:verifyR2PublicPreviewFixture --console=plain
```

source policy 锁定 exact fixture authority、ordinary 512-file fence、2+3 calibration、
unclamped public-minimum midpoint、single admission/fault、semantic history/progress、
output/manifest/receipt/cleanup authority，并禁止 retry/stop/spin/expanded/synthetic
seams。paired-APK gate 检查 fixture Debug/Release 与 evidence app Debug/R8 Release-
equivalent 的 markers、TEST_ONLY/signer boundary、normal Release receipt exclusion 和
non-empty mapping；APK gate 不替代四行设备执行。receipt Bundle round-trip 单元回归
同时覆盖合法 parse-completed semantic timeout，以及 malformed completed-phase
history 的 fail-closed 解析。

## 尚未证明

本 follow-up 不证明：

- 本 follow-up 本身不证明 external-UID/Binder 的自然 parse timeout；该边界后来由
  [External-UID Binder Natural Parse Timeout](EXTERNAL_NATURAL_PARSE_TIMEOUT_R2.md)
  以独立无编号 follow-up 关闭；
- normal/shipping Release 的自然 parse、semantic 或 emit timeout；
- 多轮 timeout stress、repeated large-job PSS/FD plateau；
- renderer LMK/OOM、force-stop 或其他 system fault；
- Unicode/high-token/adversarial 分布；
- 128 MiB container / 256 MiB expanded file-backed output；
- formal request/output/diagnostic schemas 或 tsconfig/NodeNext。

因此 ordinary production fence 继续为 **512 files、8 MiB/file、8 MiB total**；
`compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、`sourceMapV3`
四项 capability 继续全部为 `false`。本 follow-up 当时只把 internal provisional
evidence 推进到 **27/27**；后续 parse follow-up 推进到 **28/28**。Public R2
checklist 仍为 **0/54**，没有公开 promotion。
