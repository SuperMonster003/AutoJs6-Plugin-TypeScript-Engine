# R2-M3-S1 Input Archive Schema v1

> **2026-08-23 S1-3 amendment：** formal archive schema revision 继续为 1，但其当前
> `inputQuotaRevision` 已增至 2。accepted authority 同时绑定 caller capacity 与完整 retained
> capacity；ordinary `512 files / 8 MiB per file / 8 MiB total` 只约束 caller domain，
> plugin-owned trusted declarations 由独立 retained-input guardrail 约束。下文关于 quota revision 1
> 与 declarations 消耗 caller headroom 的叙述只保留为历史 provenance。

状态：**已完成（PASS，2026-08-14）**。本文冻结 `R2-M3-S1` 的 formal input archive schema
revision 1。canonical fixture、source/JVM gate、Android compile-and-package gate 与最终无排除
Core 均已在当前源码上通过；本项未运行 ADB/connected tests、未使用设备，也未扩大
Public/runtime evidence。

## Schema identity 与版本边界

formal manifest 的唯一 schema identity 为：

```text
schema = autojs6.typescript.input-archive
revision = 1
manifest path = request-manifest.json
inputQuotaRevision = 2
```

Public additive Bundle authority 使用：

```text
inputArchiveSchemaRevision = 1
declarationProfileFingerprint = <lowercase SHA-256>
```

`inputArchiveSchemaRevision` 是 manifest 语义 revision；既有
`inputArchiveTransportVersion=1` 只表示 ZIP/PFD transport。两者不能互相推导或替代。旧的
provisional/internal manifest revision `2..7` 均不是 formal revision 1 的 alias；formal parser
必须拒绝这些 revision 以及任意未知 revision，不能按旧 shape 猜测或自动降级。

S1 是 additive Public Bundle metadata 与内部 archive/Worker authority freeze，不改变 frozen v1
AIDL transaction 或 `TypeScriptCompilerContract` 的既有 transport 常量。

## Outer control plane 先于 FD ownership

server 必须在获取或转移 caller input/output PFD ownership 之前，先严格分类 outer Bundle 中的
formal schema revision 与 declaration profile identity：

- key 缺失、Bundle 实际存储类型错误、fingerprint 不是 exact lowercase SHA-256，属于 malformed
  request，返回 `ERR_AUTOJS6_TYPESCRIPT_INVALID_REQUEST`；
- schema revision 未知、不是 exact `1`，或 declaration profile fingerprint 与本地选定 profile
  authority 不一致，返回 `ERR_AUTOJS6_TYPESCRIPT_CONTRACT_MISMATCH`；
- 两类失败都不得打开/接管 caller FD、不得进入 archive staging 或 Worker dispatch、不得写 caller
  output，也不得自动 retry；
- Public preview 仍只理解 exact Rhino profile authority；same-UID DEBUG 可以选择已注册的 Rhino 或
  Node profile，但同样必须按所选 profile 取得本地 C8 fingerprint，不能用 Rhino singleton 替 Node
  校验。

outer control plane 绑定完整 caller ZIP 的 `inputArchiveSha256` 与 manifest bytes 的
`requestManifestSha256`。这两个 digest 与下文 logical inventory fingerprints 各有独立 coverage，
不能互相冒充。

## Manifest closed-world shape

所有 legal manifest 都使用相同 formal revision 1。base object 精确包含 contract、transport、
request/project/entry、profile/config、runtime/compiler/declaration identity、quota、caller inventory、
roots 与 sources；未知或缺失字段 fail closed。关键字段包括：

```text
schema
revision
inputQuotaRevision
contractVersion
contractHash
inputArchiveTransportVersion
requestId
projectId
entrySourceUri
entryPath
targetProfile
compilationMode
profileSchemaRevision
profileRevision
runtimeCapabilitySchemaRevision
configSchemaRevision
runtimeCapabilitiesFingerprint
compilerPackage
compilerVersion
compilerDistributionFingerprint
declarationsFingerprint
declarationProfileFingerprint
configKind
callerInventoryFingerprint
rootPaths
files
```

`files[]` 只描述 caller sources，每项 exact keys 为 `path`、`sha256`、`size`。caller 不提供第二份
`callerFiles` inventory。`config`、`packageMetadata`、`packageTargets` 是条件字段；不适用时 key
必须缺失，而不是写 `null`、空 object 或占位 inventory。

### Exact eight conditional shapes

| shape | profile revision | config | package metadata | package targets |
| --- | --- | --- | --- | --- |
| `rhino-r1-none` | Rhino r1 | none | absent | absent |
| `rhino-r1-tsconfig` | Rhino r1 | controlled `tsconfig` | absent | absent |
| `rhino-r1-tsconfig-metadata` | Rhino r1 | controlled `tsconfig` | present | absent |
| `rhino-r1-tsconfig-metadata-target-v1` | Rhino r1 | controlled `tsconfig` | present | v1 |
| `node-r1-none` | Node r1 | none | absent | absent |
| `node-r2-none` | Node r2 | none | absent | absent |
| `node-r2-metadata` | Node r2 | none | present | absent |
| `node-r2-metadata-target-v2` | Node r2 | none | present | v2 |

除此之外的组合全部拒绝。特别是 config-none 携带 package data、Node r1 携带 package data、Node r2
携带 tsconfig、target 没有 metadata、Rhino 使用 target v2、Node r2 使用 target v1，或同时携带
target v1/v2，都不能重解释为较小 shape。

这张表冻结 archive admission shape，不等于八种 Public E2E capability。Node revision 2 仍只到
Worker result/private output authority；S1 不开启它的 Public admission、runtime handoff 或 E2E
release route。

## Physical archive 与 logical inventories

caller archive 的 exact physical coverage 是：

```text
request-manifest.json
+ every declared source
+ optional tsconfig.json
+ optional package metadata files
+ optional package target v1 or v2 files
```

caller 不能携带 `declarations/**`。trusted declarations 来自已签名 plugin-local distribution，
在 caller archive 完整校验后按所选 C8 profile 注入；caller manifest 只能绑定既有 common
`declarationsFingerprint` 与 exact `declarationProfileFingerprint`，不能声明本地 declaration file
count、byte length、inventory fingerprint、layer/root/count 等实现派生 authority。

logical inventory 使用一个 domain-separated canonical stream：

```text
autojs6.typescript.input-inventory.v1\n
domain NUL canonical-path NUL decimal-size NUL lowercase-sha256 LF
```

排序先按固定 domain order，再按 canonical path：

```text
source
declaration
config
package-metadata
package-target-v1
package-target-v2
```

source、declaration 与 package input 使用 Worker-visible leading-slash path；config 固定为
`tsconfig.json`。case-fold collision、非 canonical path 或同时出现 target v1/v2 均 fail closed。
虽然 canonical vocabulary 为两代 target 保留六个 domain label，任一 legal request 最多只选择
其中一个 target generation，因此 D1 的实际 Worker-retained ordinary accounting 仍是 source、
declaration、config、package metadata、selected package target 五类内容。

### 三层 inventory authority

- `callerInventoryFingerprint` 覆盖 caller 的所有 non-manifest payload：source、optional config、
  optional package metadata 与一个 selected target generation；不覆盖 declarations、manifest 或
  ZIP container bytes；
- `declarationInventoryFingerprint`、`declarationFileCount` 与 `declarationByteLength` 由 loader
  对本地已验证 C8 declarations 派生，caller wire 不得提供；
- `retainedInventoryFingerprint` 覆盖 exact caller inventory 加 local declarations；accepted
  authority 还保存 schema revision、declaration profile fingerprint、input quota revision、caller
  capacity 与完整 retained capacity。

manifest 本身不进入 logical inventory，避免 self-reference；它仍由
`requestManifestSha256` 独立绑定。完整 ZIP container 由 `inputArchiveSha256` 独立绑定。loader 必须
分别重算实际 archive、manifest、caller logical inventory 与 local declaration identity，并把冻结的
`R2AcceptedInputAuthorityV1` 随 snapshot 传入 framing；project-scoped production snapshot 缺少该
accepted authority 时必须拒绝，不能从可变 snapshot 临时合成。

## Ordinary quota、transport 与 TEST_ONLY capacity

`inputQuotaRevision=2` 的 ordinary production admission 覆盖 caller-owned content：

```text
maximum files             = 512
maximum single-file bytes = 8 MiB
maximum total bytes       = 8 MiB
comparison                = inclusive
```

计数/计字节覆盖 caller sources、optional raw config、package metadata 与 selected package
targets。trusted declarations、`request-manifest.json`、ZIP container bytes 与 framing overhead
不进入 ordinary caller quota。trusted declarations 仍进入 exact retained inventory，并受 trusted
host 选择的 file-backed/Worker guardrail 约束；caller 可以独立使用完整 512 files / 8 MiB 额度。

以下更大数字继续属于不同 authority，不能 promotion ordinary/Public capability：

- ZIP/PFD validation 与 private staging transport ceiling：4096 archive entries、64 MiB container、
  64 MiB expanded、8 MiB per entry；
- retained file-backed/Worker guardrail：最多 4607 files、8 MiB per file、72 MiB total，覆盖
  caller input 加 plugin declarations；它只能由 trusted host 选择，manifest/control plane 不能请求；
- non-shipping `TEST_ONLY_EXPANDED` 可把同一 guardrail 用作 caller authority，但仍只能由 trusted
  test seam 选择，Public caller 不可请求。

ordinary quota rejection 必须发生在 Worker handle claim/framing 之前。caller-owned oversize 映射为
`ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_QUOTA`；plugin-local declarations 破坏 retained guardrail 时属于
compiler unavailable。两者都保持 terminal empty output 且不自动重试。

## Native framing 与 Worker independent recomputation

accepted authority 贯穿 snapshot 与 `projectStart`，至少携带：

```text
inputArchiveSchemaRevision
inputQuotaRevision
callerInventoryFingerprint
retainedInventoryFingerprint
retainedFileCount
largestFileBytes
declarationProfileFingerprint
declarationInventoryFingerprint
declarationFileCount
declarationByteLength
```

既有 `fileCount` 仍表示随后运输的 file frames，不包含独立 config frames；
`retainedFileCount` 使用 D1 的完整 semantic count，包含 config。`totalBytes` 已包含 config；
`largestFileBytes` 也必须覆盖所有 retained content，而不是只看 ordinary file frames。

Worker 必须 strict-validate start shape，并在 `projectInputEnd`、进入 Program 之前，根据实际接收的
bytes 重新计算每个 record SHA-256、caller/retained/declaration inventory fingerprints、declaration
count/bytes、retained count/total/largest。任一 mismatch 都在 Program 前 fail closed；native 已
验证过同一值不能替代 Worker 的独立复核。

## Promotion 与证据边界

S1 只冻结 input archive schema/authority，并不：

- 把 `typescriptCompilerAvailable`、`typescriptProjectCompilation`、
  `typescriptSemanticDiagnostics`、`typescriptSourceMapV3` 或
  `typescriptNodeRuntimeRequired` 从 `false` promotion；
- 开启 Public compilation、AutoJs6 host consumer、external production route、Rhino/Node runtime
  execution 或 emitted artifact handoff；
- 把 Node revision 2 的 Worker-result-only 状态提升为 Public E2E；
- 重定级任何历史 TEST_ONLY、private same-UID、APK 或 device evidence；
- 证明 S2 output archive、S3 diagnostics、O1 streaming output 或 M4/M5 shipping acceptance。

S1 的完成证据目标是 canonical fixture/source/JVM 与 Android compile-and-package static closure；本项
不需要 ADB，也不因设备可用而运行 device matrix。APK/static evidence 不能表述为 runtime 或 Public
route evidence。

## Canonical fixture 与验证 gates

Canonical fixture：
`app/src/test/resources/r2-input-archive-schema/s1-v1.json`，schema identity 为
`autojs6.typescript.r2-m3-s1.input-archive-schema`。它应机器复核 single revision 1、exact eight
shapes、legacy/unknown revision rejection、outer pre-FD error classification、closed-world keys、
domain-separated inventories、hash coverage、ordinary/transport/TEST_ONLY capacity separation、
Worker recomputation 与 no-promotion fence。

验证入口：

- `:app:verifyR2M3S1InputArchiveSchemaV1Fixture`；
- `:app:verifyR2M3S1InputArchiveSchemaV1`；
- `:app:verifyR2ProjectCompilerCore`（最终无排除回归）。

| evidence | 当前状态 | 可证明范围 |
| --- | --- | --- |
| canonical fixture + JVM/source contract tests | **PASS** | revision、shape、inventory 与 fail-closed rules |
| targeted S1 gate | **PASS** | 337 actionable：82 executed、255 up-to-date；`BUILD SUCCESSFUL in 3m 10s` |
| final no-exclusion Core | **PASS** | 533 actionable：121 executed、412 up-to-date；`BUILD SUCCESSFUL in 3m 30s` |
| ADB/device/Public runtime | **NOT RUN / NOT CLAIMED** | S1 不需要且不能据此扩大 schema/static evidence |

最终 targeted receipt 为
`build/reports/r2-m3-s1-targeted-acceptance-20260814-043456.log`（SHA-256
`0f1c9a491e4a89577842b19d7206f46440c54b7ae5a5e237e615b6ddcb6d2225`），wrapper
`exit=0`、elapsed `191.5s`。最终无排除 Core receipt 为
`build/reports/r2-m3-s1-core-acceptance-20260814-043846.log`（SHA-256
`6bac7ea5f54c2d03a76551178033cafe2391c229a95bd85342a12985d6fe5a36`），wrapper
`exit=0`、elapsed `211.2s`。latest source 为 `04:34:22`，两轮分别于 `04:34:56`/`04:38:46`
启动，因此均是最终修复后的 fresh evidence。两份日志均包含 S1 fixture/aggregate、route classifier、
Bundle `keySet()` adapter delegation 与 accepted-input authority 六字段 tamper/六个 clean successors
门禁（failure Program=0）；均未运行 ADB/connected tests/instrumentation、未使用设备，AndroidTest
仅编译/组装与 APK 静态门禁，完成后 Gradle wrapper client 为 0。

focused 71-task classification 与此前 targeted/Core 只作为修复过程证据；S1 主验收以上述两份
acceptance receipt 为准。S1 完成当时使 M3 从 `6/18` 推进到 `7/18`；随后 S2/S3 已于
2026-08-22 完成，当前 M3 为 `9/18`，下一活动项是 `R2-M3-S4` manifest identity/pairing freeze。
S2/S3 边界见 [Output Archive Schema v1](OUTPUT_ARCHIVE_SCHEMA_V1_R2_M3_S2.md) 与
[Diagnostics JSON Schema v1](DIAGNOSTICS_SCHEMA_V1_R2_M3_S3.md)。五项
capability/dependency metadata 仍为 `false`，Node revision 2 继续 Worker-result/private-output
authority。

相关 quota 与 promotion 边界见
[R2-M3-D1 Ordinary Input Quota Decision](ORDINARY_INPUT_QUOTA_DECISION_R2_M3_D1.md) 与
[R2-M3-D3 Capability Promotion Compatibility Decision](CAPABILITY_PROMOTION_COMPATIBILITY_R2_M3_D3.md)。
旧 archive/P2/C2-C8 页只保留 historical pre-S1 provenance，并以 supersession banner 指向本文；
其中 provisional revisions `2..7`、14/27-field 与 20/30/37-field receipt 都不能被误读为当前
formal aliases 或 Worker shape。当前 formal Worker exact counts 为 24/37 与 30/40/47。
