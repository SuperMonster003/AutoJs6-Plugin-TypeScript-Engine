# R2-M2-X1 Public-shaped Deterministic Bundle

日期：2026-08-11

状态：**已完成。targeted X1 gate、QV710AF65F focused Android runtime receipt 与最终
`verifyR2ProjectCompilerCore` 均已 PASS。本文中的 `public-shaped` 只描述冻结的
Binder/PFD/ticket/ACK/callback service surface；admission 仍是 private same-UID Debug，
不是 Public promotion。**

## 决策摘要

X1 不新增 production profile、origin、AIDL 字段或 public classifier。它复用真实
`ITypeScriptCompilerPlugin.startCompilation(Bundle, PFD, PFD, callback)` 链路，证明两个
已存在的 end-to-end provisional authority 可以在同一个 service product shape 下生成
deterministic private bundle：

- provisional Rhino revision 1；
- provisional NodeNext revision 1，`configKind=none`、ordinary `.ts -> .js`；
- 每个 profile 使用两个 executable `.ts` roots、两个独立且等长的 request identities；
- 请求进入 accepted ticket/job token、post-return public-shaped ACK、production coordinator、
  archive/snapshot、WebView Worker、private ZIP stage、caller FD commit 与 exactly-one callback；
- 不执行 emitted JavaScript，也不创建 Rhino 或 Node runtime。

NodeNext revision 2 不属于 X1 end-to-end authority。它继续只进入 Worker-result admission；
external `PUBLIC_PREVIEW` 继续只能选择 Rhino。下列 metadata 继续为 `false`：

- `COMPILER_AVAILABLE`；
- `PROJECT_COMPILATION`；
- `SEMANTIC_DIAGNOSTICS`；
- `SOURCE_MAP_V3`；
- `NODE_RUNTIME_REQUIRED`。

因此，X1 关闭的是 **private admission over a public-shaped service surface**，不是 external
caller、distinct-UID normal Release 或 public capability promotion。后者仍属于 M5。

## Service matrix

| Profile | Roots | Requests | Expected bundle |
| --- | ---: | ---: | --- |
| Rhino revision 1 | `helper.ts`、`main.ts` | 2 | manifest、diagnostics、2 JS、2 maps |
| NodeNext revision 1 | `helper.ts`、`main.ts` | 2 | manifest、diagnostics、2 JS、2 maps |

Rhino 的 `main.ts` 使用 `./helper`；Node revision 1 使用 `./helper.js`，并由冻结的
NodeNext resolver 解析到 snapshot 内的 `/sources/helper.ts`。两个 requests 不得复用
`requestId`：global admission arbiter 会保留 process-local tombstone，重复 identity 应当
fail closed。

所有 X1 requests 都关闭 spin、capacity、fault 与 cleanup-delay test knobs，只保留 private
admission 所需的 enabled、input/archive digest、request-manifest digest 与 exact profile
revision selector。ACK 在 `startCompilation` 返回 accepted ticket 后发出，不绕过真实
stop-and-wait admission。

## Determinism definition

两个独立 request 的 caller ZIP 不能直接声明为 raw byte-identical。`requestId`、input archive
SHA-256、request manifest SHA-256 会合法变化；diagnostics 也绑定 requestId，其 SHA-256 又会
进入 output manifest file descriptor 与 `inventoryFingerprint`。

X1 的跨请求 deterministic comparison 因而必须按以下闭包执行：

1. 每份真实 ZIP 独立验证 exact 6-entry name/order/kind、terminal manifest SHA-256、profile、
   effective-options、runtime authority、每项 byte length/SHA-256 与原始 inventory；
2. 只把两个等长 request IDs 定点替换为同一 sentinel，比较 normalized diagnostics bytes；
3. 用 normalized diagnostics SHA-256 重建 manifest 中对应 file descriptor；
4. 只归一 manifest 的 `requestId`、`inputArchiveSha256`、`requestManifestSha256`，并从完整
   normalized files 独立重建 `inventoryFingerprint`；
5. 比较 normalized manifest/diagnostics canonical structures；
6. 两次请求的全部 module 与 Source Map payload 保持原始 byte-for-byte identical。

JVM 另行用相同 Node revision-1 authority/input 连续 stage 两次 private archive，并比较完整
ZIP raw bytes。这项 JVM 证据与 Android 跨 request normalization 证明不同层级，不得互相
冒充。

## Evidence layers

| 层级 | Gate | 结果 | 证据边界 |
| --- | --- | --- | --- |
| source/JVM/APK | `:app:verifyR2M2X1PublicShapedDeterministicBundle` | PASS；3m38s；325 actionable（70 executed、255 up-to-date） | authority、promotion fences、JVM private ZIP determinism、AndroidTest APK 可构建；不能替代设备运行 |
| Android runtime | `R2BinderCompilationInstrumentationTest#rhinoAndNodeRevisionOneProduceDeterministicBundlesThroughPublicShapedService` | PASS；`Time: 34.639`；`OK (1 test)`；`INSTRUMENTATION_CODE: -1` | 真 Binder/PFD/ACK/Worker/output-FD/callback 链；关闭 X1 的设备证据 |
| final aggregate | `:app:verifyR2ProjectCompilerCore` | PASS；5m1s；522 actionable（101 executed、421 up-to-date） | X1 与既有 P1-P5、APK/API/source gates 无回归 |

3m38s targeted run 临时使用了 `-x :app:verifyR2ProjectCompilerDesktop`，只为隔离当时主机
高负载对历史 b2j timing calibration 的影响；它不独立充当 P5 或完整 Core
acceptance。最终 5m1s `verifyR2ProjectCompilerCore` 未使用任何排除项，并覆盖完整
dependency graph。

Focused Android receipt 必须记录 explicit serial、型号/API/ABI、WebView provider/version、app
与 test APK SHA-256、runner stdout、耗时，以及唯一 `OK (1 test)` 与
`INSTRUMENTATION_CODE: -1`。不得把 `assembleDebugAndroidTest`、JVM test 或静态 marker
写成 Binder/Worker runtime PASS。

### Focused Android receipt

| 字段 | 值 |
| --- | --- |
| serial | `QV710AF65F` |
| 设备 | Sony `XQ-AT72` |
| Android / API / ABI | Android 12 / API 31 / `arm64-v8a` |
| build fingerprint | `Sony/XQ-AT72_CN/XQ-AT72:12/58.2.A.10.44A/058002A0100044A0891821322:user/release-keys` |
| WebView | `com.google.android.webview` `145.0.7632.120` |
| app APK | 5,886,330 bytes；SHA-256 `AED7038E0B157A3E9886120687815BD9BAE32650778A44704B00B2EAA7B9A8B4` |
| androidTest APK | 1,552,454 bytes；SHA-256 `F765DF7A4037C95635937662F7C0137A9E0230A83DE52C29567E279E65AD36B2` |
| signer SHA-256 | 两个 APK 相同；`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213` |
| direct runner | `Time: 34.639`；`OK (1 test)`；`INSTRUMENTATION_CODE: -1` |
| logcat receipt | 1 test / 0 failed / 0 ignored |
| teardown | active instrumentation processes = 0；active target processes = 0 |

该 receipt 只关闭 same-UID Debug X1 product-shape 证据。它不会因 APK 同签名、真实
Binder 或真实 WebView Worker 运行而自动升级为 distinct-UID normal Release/Public
evidence。

## Promotion fences

X1 aggregate 与 instrumentation 必须继续证明：

- `endToEndAdmittedAuthorities` 精确包含 Rhino revision 1 与 Node revision 1，不含 revision 2；
- `workerResultAdmittedAuthorities` 才 additive 包含 Node revision 2；
- non-Debug/public request 不能用 private selector 激活 Node；
- external public preview 的 Node request 与 registered Node revision-2 end-to-end request 均
  稳定拒绝；
- AIDL/hash、provisional output/diagnostics schema 与 capability metadata 不变；
- 没有 runtime engine、R3 preparation routing、automatic retry、network 或 snapshot 外读取。

## Non-goals

X1 不证明：

- distinct-UID external caller 或 normal Release Public promotion；
- `getCompilerInfo()` 的 multi-profile public discovery catalog；
- Node revision 2 end-to-end、`.mts/.cts` public service admission 或 controlled tsconfig；
- emitted JavaScript runtime semantics；
- frozen public ZIP/diagnostics schema。

这些边界继续由 M3-M5 与 R3-R5 显式推进。

## 关闭结论

`R2-M2-X1` 的 targeted gate、focused device runtime 与最终 Core 已形成闭环；M2
因此更新为 `16/18`。下一活动项是 `R2-M2-X2`：统一 type/syntax/global/options
error 的 no-emit 与 internal diagnostics model。实时状态见
[R2 Roadmap](../roadmap/R2.md)。
