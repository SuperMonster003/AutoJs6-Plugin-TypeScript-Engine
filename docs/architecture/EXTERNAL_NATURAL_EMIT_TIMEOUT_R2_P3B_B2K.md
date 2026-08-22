# R2-P3b-b2k External-UID Binder Natural Emit Timeout

日期：2026-07-31

状态：internal provisional evidence **26/26**；Public R2 **0/54**。本切片只关闭
standalone same-signer、distinct-UID fixture 经 public Binder admission 进入
non-shipping TEST_ONLY evidence Debug 与 minified/non-debuggable evidence Release
时的自然 **emit** request-start absolute-deadline timeout 窄缺口。它不是 shipping
Release 证明，也不启用任何公开 capability。

## 证明目标

P3b-b2j 已在 ordinary same-UID Debug direct host 上证明自然 emit timeout；它没有
覆盖外部 UID、public accept/ACK、Binder callback、release-equivalent R8 或
pre-callback cleanup ordering。P3b-b2k 保持相同的自然工作原则，并把证明边界推进到
external public pipeline：

- fixture 与 plugin 同签名但 UID 必须不同；plugin service 必须 exported，并继续由
  `org.autojs.permission.PLUGIN` signature permission 保护；两端 evidence artifact
  都必须为 `TEST_ONLY`；
- 整个矩阵只建立一个 Binder session。每个 request 只有一次不可逆 admission，先
  复核 accept ticket 与 ACK 前 server ownership，再发送 public ACK；不 retry、不
  搜索 fault、不请求 capacity scope；
- 每个 artifact/device 先运行 **2 次**完整成功 precondition；它们只证明 workload
  可完整执行，不参与 timeout 计算；
- 再运行 **3 次**完整成功 baseline，只用真实 emit sequence-2/completed 的严格
  交集选择 midpoint；midpoint 不 clamp，必须自然达到 public `6,000 ms` 下限并
  保留左右各至少 `100 ms`；
- 校准后只运行 **1 次**正式 fault。它必须返回 `ERROR_TIMEOUT`、
  `compilation=null`、empty output，并停在 emit sequence 2 `128/512`；
- callback 前创建 request-correlated phase receipt 与 cleanup receipt；fixture 在
  callback 后读取二者，证明 exact Worker replacement、364/364 cursor 平衡释放、
  terminal cleanup 连续 5 次全零；
- fault 后在同一 Binder、同一安全 compiler process epoch 运行一个小 successor，
  并再次证明 output 与 109/109 cursor cleanup。

“自然”仍表示没有 phase-entry hook、同步 Worker spin、测试侧 `requestStop`、
synthetic timing sample、expanded admission 或 phase-relative deadline。任何
precondition/baseline/output/receipt/cleanup 失败，或者三条 baseline 没有形成合法
窗口，都必须 fail closed；正式 fault attempt 保持为零。

## 冻结 external workload

`R2ExternalNaturalEmitTimeoutProjectFixture` 使用 TypeScript `6.0.3`、ES2018 与
ordinary production admission（512 files、8 MiB/file、8 MiB total）。它使用
256 个 executable roots：64 个 light root，以及 192 个 heavy root。每个 heavy
root 含 1 条 depth-61 optional chain，并把 4 组 expression 各包裹在 90 层会被擦除的
`as any` type assertion 中。这个拓扑让前 64 个 light roots 的 128 个 output 恰好
形成 emit sequence 2，同时在 WebView call-stack bound 与 Worker token/output ceiling
内延长真实 compiler emit、output finalization 和 framed egress。没有 distributive
resolver workload、expanded admission、phase hook 或 artificial spin。

| 项目 | primary workload authority |
|---|---:|
| roots | 256（64 light + 192 heavy） |
| heavy-root topology | `192 × (1 depth-61 optional chain + 4 × 90 erased assertions)` |
| caller files / bytes / tokens | 256 / 555,766 / 168,896 |
| maximum caller root | 2,884 bytes / 877 tokens |
| installed trusted declarations | 108 / 3,784,758 bytes / 86,329 tokens |
| aggregate input | 364 files / 4,340,524 bytes / 255,225 tokens |
| input transport | 1,144 frames |
| aggregate path characters | 13,073 |
| caller inventory fingerprint | `7cd2119c93657dbf2a8709e67d8045331b1d444f0e3b3e6a3831e1a712ce187e` |
| diagnostics | 0 |

raw primary output 保留 72,138 bytes 的 1-MiB ceiling headroom，超过冻结的
64-KiB 保护线 6,602 bytes。

small successor 是独立的一 root ordinary project；连同 installed declarations 为
109 files / 3,784,795 bytes / 86,337 tokens / 379 frames。它不参与 calibration。
successor 只有 2 个 emit outputs，达不到 Worker phase progress 的最小 8 units，
因此不伪造 phase timing receipt；它仍必须通过 public output、cleanup receipt、
same-Binder 与 same-process-epoch 验证。

## Raw Worker 与 packaged output 双重 authority

primary 与 successor 的 TypeScript raw Worker output 都固定验证。aggregate SHA-256
按排序后的 `/outputs/...` path、NUL 分隔符与 raw payload 计算：

| workload | raw files | raw JS | raw maps | raw total | raw aggregate SHA-256 |
|---|---:|---:|---:|---:|---|
| primary | 512 | 717,302 | 259,136 | 976,438 | `2b2a4529552aa3a3f9b33cd2a379240bc2852ca065a33ff884a6160847f1b1de` |
| successor | 2 | 166 | 146 | 312 | `0b504753f68a345d6aa82eeb174f6d9c63eec3cb21b7810ed09f69e59296be25` |

public output packager 会把每个 JavaScript 的 raw basename
`sourceMappingURL` 重写为 `../maps/<stem>.js.map`。每个 module 精确增加 8 bytes；
primary 256 个 modules 因此共增加 2,048 bytes，packaged TS payload 为 512 files /
719,350 JS bytes / 259,136 map bytes / 978,486 bytes；successor 增加 8 bytes，packaged TS payload 为
2 files / 174 JS bytes / 146 map bytes / 320 bytes。fixture 会从 packaged module
逐项验证 canonical UTF-8 与唯一末尾 directive，再无损重建 raw bytes；不能把
packager rewrite 后的 bytes 误当作 TypeScript Worker authority。

packaged ZIP 同时接受另一层完整验证，而不只比较 raw aggregate：

- inventory 必须精确为 `compilation-manifest.json`、`diagnostics.json` 与每个 root
  对应的一组 `modules/*.js` / `maps/*.js.map`；所有 entry 必须是 canonical
  STORED ZIP32 且无重复；
- diagnostics 必须是 revision-2 provisional schema、同 request/profile authority
  且为空；每个 Source Map 必须是 v3，并包含非空 mappings；
- terminal `outputManifestSha256` 必须等于真实 manifest bytes；manifest 必须绑定
  request/project/entry、input archive、request manifest、contract、profile/config、
  compiler distribution、declarations 与 runtime-capabilities fingerprints；
- manifest 的 effective options、完整 packaged file size/SHA/source inventory 与
  inventory fingerprint 必须从实际 ZIP payload 重算后逐项一致。

所以 raw Worker authority 与 public packaged-manifest authority 是两项独立证明；
任一层漂移都不能计为成功 baseline。

## Request-start absolute nanosecond seam

public coordinator 在 admission 时一次性记录
`startedAtElapsedRealtimeNanos`，并以 exact addition 得到
`deadlineAtElapsedRealtimeNanos`。ordinary zero-spin pipeline 调用
`compileProjectUntilElapsedRealtimeDeadline(...)`，把这个原始绝对纳秒 deadline
直接交给 `TypeScriptWebViewCompilerHost`。

host 只为既有接口向上取整计算剩余毫秒，但内部 completion wait、timeout stop
selection 与 page termination receipt 都继续使用原始 absolute nanos。这样避免
“先换算为剩余毫秒、再重建 host deadline”把 host 与 coordinator timer 推到相邻
调度 tick，导致 callback 已 timeout 而 exact phase receipt 尚未选择的 ±1 ms race。
这条 seam 是 production ordinary zero-spin control flow，不是测试侧 deadline hook。

本切片把 emit 生命周期冻结为一条连续的真实工作边界：从 `builder.emit()`，经过
output 结构校验、逐项 SHA-256、Source Map 校验、result JSON/UTF-8 序列化与最终
digest，一直覆盖 ACK-backpressured framed result egress。Worker 收到最后一个 result
chunk ACK 后才发布 `emit completed`，随后发布 `projectResultEnd`。因此 fault receipt
证明的是 deadline 在仍处于 emit 生命周期、且已到达 sequence 2 时选择并终止 exact
Worker；它**不**声称 instruction-level 地中断在 `builder.emit()` 函数内部。
最后一个 ACK 只证明 native accumulator 已接受全部 payload bytes；
`projectResultEnd` 之后的 native SHA-256、fatal UTF-8 与 typed-result finalizer
仍明确位于 emit phase 之外。

每次成功 host operation 在返回时冻结一个不可变
`R2ProjectCompilationEvidenceSnapshot`：private host request ID、project start、phase
timing/history、termination、input transport、session/Worker generation 全部属于同一
request。coordinator 只在 host 返回后立即按 exact host request ID 捕获该 snapshot，
再把所有 timing 转换到 public Binder request-start clock。

## Phase receipt 与 cleanup receipt

phase receipt 使用 private、non-shipping
`__autojs6_r2_external_natural_phase_timeout_` Bundle namespace。它只会在 evidence
build 且 exact same-signer/distinct-UID TEST_ONLY caller attestation 成立时由
`getCompilerInfo()` 返回，不增加 public compilation authority：

- 成功 receipt 关联 Binder request ID 与不同的 private host request ID，声明
  `createdBeforeCallback=true`，并携带 parse/semantic/emit 各自真实 sequence-2 与
  completed request-start nanos；
- timeout receipt 只允许已完成的 parse/semantic timing，并携带 `TIMED_OUT`、
  host/page phase=`emit`、entered=`ingress,parse,semantic,emit`、
  completed=`ingress,parse,semantic`、deadline/stop-selection/overshoot、session/
  Worker generations，以及 page termination progress=`2,128/512`；
- input telemetry 必须为 1,144 frames / 4,340,524 bytes、complete、364 opened =
  364 closed、无 active cursor 且 released；
- receipt 在 terminal cleanup 和 immutable terminal selection 之后、callback 发布
  之前创建。缺 key、错误类型、跨 request snapshot 或不合法 phase ordering 都按
  fail-closed 解析为无可用证据。

同一轮还复用 b2f cleanup protocol：ACK 前 active lease 为 1，server input/output
FD ownership 各精确为 1；terminal receipt 在 callback 前冻结，callback 后连续 5
个 current samples 必须同时证明 caller/private input、project spool、private
output、named transient、cursor 与 active lease 全零。phase receipt 回答“Worker 在
哪个自然 phase 被 deadline 终止”，cleanup receipt 回答“Binder/native ownership
是否在 callback 前完整释放”，两者不可互相替代。

## 两次预检、三基线与单次 fault

每个 API/artifact 行都独立执行：

```text
2 successful preconditions
3 successful calibration baselines
strict intersection + fixed midpoint
1 natural timeout fault
1 small same-Binder successor
```

三次 baseline 的 emit timing 都相对于同一类 public request-start monotonic clock：

```text
lower = max(floor(sequenceTwoNanos / 1 ms)) + 1 ms
upper = min(floor((completedNanos - 1 ns) / 1 ms))
timeout = lower + floor((upper - lower) / 2)
```

必须满足 `upper - lower >= 200 ms`、timeout 左右 margin 各至少 100 ms，且
`timeout >= 6,000 ms`。代码不把低于 public minimum 的 midpoint clamp 到 6 秒；
低于下限会在 faultAttempts=0 时直接失败。因此正式 fault 使用的是三条真实
baseline 的共同 emit interval，而不是预先猜测或运行时搜索出的 timeout。

## Android runtime evidence

正式矩阵为 API 24/35 × TEST_ONLY evidence Debug/minified non-debuggable evidence
Release 四行。evidence Release 继承 release、启用 R8/minify，且 mapping 必须存在；
standalone fixture Release 自身不据此宣称 minified。normal shipping Release 必须由
APK gate 证明不含上述 private phase-receipt namespace。

设备环境被冻结为：

| API / serial | model | build fingerprint | WebView provider |
|---|---|---|---|
| API 24 / `emulator-5556` | Android SDK built for x86 | `google/sdk_google_phone_x86/generic_x86:7.0/NYC/4409132:user/release-keys` | `com.google.android.webview` `53.0.2785.124` |
| API 35 / `968e9f18` | 23046RP50C | `Xiaomi/liuqin/liuqin:15/AQ3A.241006.001/OS2.0.9.0.VMYCNXM:user/release-keys` | `com.google.android.webview` `130.0.6723.86` |

下表的 precondition、baseline、window、timeout 与 margin 均为 public request-start
之后的整数毫秒；`fault wall` 是 fixture 从 admission 前开始观测到 callback 的 client
wall time。overshoot 是 exact timeout stop selection 相对 absolute deadline 经
`NANOSECONDS.toMillis` 截断后的毫秒值。

| API / artifact | precondition emit `seq2/completed` | baseline emit `seq2/completed` | strict window | timeout | margins L/R | fault wall / overshoot |
|---|---|---|---:|---:|---:|---:|
| API 24 / evidence Debug | `7411/9452`, `7610/9613` | `7977/9871`, `7445/9423`, `7661/9556` | `7978..9423` | `8700` | `722/723` | `9147 / 3` |
| API 24 / evidence Release | `5785/7445`, `5551/7287` | `5852/7632`, `5935/7653`, `5807/7464` | `5936..7464` | `6700` | `764/764` | `7210 / 13` |
| API 35 / evidence Debug | `19680/22220`, `20899/23574` | `21048/23506`, `20789/23383`, `21177/23671` | `21178..23383` | `22280` | `1102/1103` | `22666 / 1` |
| API 35 / evidence Release | `9395/10975`, `9866/11538` | `9988/11564`, `10043/11747`, `9853/11486` | `10044..11486` | `10765` | `721/721` | `11060 / 0` |

四行正式 fault 都精确为 emit sequence 2 `128/512`、`faultAttempts=1`、
`outputBytes=0`、input cursors `364/364`，并在同一 Binder session 与同一安全
compiler process epoch 内完成 timeout 和 successor。timeout 都终止 Worker generation
1，replacement 为 generation 2；successor 使用 replacement Worker 成功返回。每条
最终保留的显式 serial runner stdout 都只有一个 `INSTRUMENTATION_CODE: -1`；runner
内部没有第二次 fault target 或隐藏 retry。

每行最终日志必须精确包含 2 条
`EXTERNAL_NATURAL_EMIT_TIMEOUT_PRECONDITION_PASS`、3 条
`EXTERNAL_NATURAL_EMIT_TIMEOUT_BASELINE_PASS`、1 条
`EXTERNAL_NATURAL_EMIT_TIMEOUT_WINDOW_PASS`、1 条
`EXTERNAL_NATURAL_EMIT_TIMEOUT_FAULT_PASS`、1 条
`EXTERNAL_NATURAL_EMIT_TIMEOUT_SUCCESSOR_PASS` 与 1 条
`EXTERNAL_NATURAL_EMIT_TIMEOUT_MATRIX_PASS`。该 marker 计数同时证明没有隐藏的
fault retry。

定向设备命令必须使用显式 serial，且不传 external-capacity scope：

```powershell
adb -s emulator-5556 shell am instrument -w -r `
  -e externalNaturalEmitTimeoutEvidence true `
  io.github.supermonster003.autojs6.plugin.typescript.publicpreview/io.github.supermonster003.autojs6.plugin.typescript.publicpreview.R2PublicPreviewInstrumentation

adb -s 968e9f18 shell am instrument -w -r `
  -e externalNaturalEmitTimeoutEvidence true `
  io.github.supermonster003.autojs6.plugin.typescript.publicpreview/io.github.supermonster003.autojs6.plugin.typescript.publicpreview.R2PublicPreviewInstrumentation
```

## 本地门禁与 artifact boundary

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :r2-public-preview:verifyR2P3bB2kExternalNaturalEmitTimeoutSourcePolicy --no-daemon
.\gradlew.bat :r2-public-preview:verifyR2P3bB2kExternalNaturalEmitTimeoutApks --no-daemon
.\gradlew.bat verifyR2PublicPreviewFixture --console=plain
```

source policy 锁定 standalone external workload、2+3+1 matrix、strict
intersection、unclamped midpoint/public minimum、single admission/fault、absolute
nanos seam、phase/cleanup receipt ordering、raw Worker 与 packaged manifest
authority，并禁止 stop/retry/spin/expanded-capacity/synthetic/phase-relative seam。

paired-APK gate 同时检查 fixture Debug/Release、app ordinary Debug/Release 与
`r2CapacityEvidenceDebug`/`r2CapacityEvidenceRelease`：两份 evidence app APK 必须
TEST_ONLY，phase markers 必须同时存在于 Debug 与经 R8 的 Release-equivalent DEX；
normal Release 不得保留 receipt prefix，evidence Release mapping 必须非空。APK gate
只证明 artifact shape，不替代上面的四行设备执行。

## 尚未证明

P3b-b2k 不证明：

- external-UID/Binder 的自然 parse 或 semantic timeout；
- normal/shipping Release 的自然 parse、semantic 或 emit timeout；
- 多轮 timeout stress、repeated large-job PSS/FD plateau；
- renderer LMK/OOM、force-stop 或其他 system fault；
- Unicode/high-token/adversarial 分布；
- 128 MiB container / 256 MiB expanded file-backed output；
- formal request/output/diagnostic schemas 或 tsconfig/NodeNext。

因此 ordinary production fence 继续为 **512 files、8 MiB/file、8 MiB total**；
`compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、`sourceMapV3`
四项 capability 继续全部为 `false`，Public R2 checklist 继续为 **0/54**。
P3b-b2k 把 internal provisional evidence 推进到 **26/26**，但不是 Public R2
开放声明。
