# R2-M3-S2 Output Archive Schema v1

状态：**已完成（PASS，2026-08-22）**。本文冻结 `R2-M3-S2` 的 formal output archive
schema revision 1：terminal outcome、accepted-input/compiler provenance、effective-options identity、
module inventory 与 exact packaged-artifact authority。canonical fixture、JVM/source gate、
Debug/Release APK gate、AndroidTest compile-and-package gate与最终无排除 Core 均已在当前源码上通过；
本项未运行 ADB/connected tests、未执行 instrumentation、未使用设备，也未发生 Public/runtime
capability promotion。

> 2026-08-22 supersession：S2 完成时有意保留的 provisional diagnostics document 已由
> [R2-M3-S3 Diagnostics JSON Schema v1](DIAGNOSTICS_SCHEMA_V1_R2_M3_S3.md) 正式替代。本文中
> provisional revision 2 的描述只记录 S2 当时的任务边界，不再描述当前 production output。

## Schema identity 与版本边界

formal output manifest 的唯一 schema identity 为：

```text
schema = autojs6.typescript.output-archive
revision = 1
manifest path = compilation-manifest.json
diagnostics path = diagnostics.json
outputQuotaRevision = 1
```

成功提交 emitted 或 diagnostics-only archive 后，terminal Bundle 同时携带：

```text
outputArchiveSchemaRevision = 1
outputManifestSha256 = <lowercase SHA-256 of exact manifest bytes>
```

`outputArchiveSchemaRevision` 描述 manifest 与 payload authority；既有
`outputArchiveTransportVersion=1` 只描述 ZIP/PFD transport。两者不能互相推导或替代。旧的
`autojs6.typescript.compilation.provisional` revisions `2`、`3` 均是 pre-S2 historical
provenance，不是 formal revision 1 alias；unknown schema/revision 与 legacy revision 必须 fail
closed，不能猜测、降级或自动重解释。

cancelled、timed-out 与 failed terminal 保持 empty-output contract：不提交 archive、
`outputManifestSha256` 缺失，`outputArchiveSchemaRevision` 也必须缺失。S2 是 additive Bundle
metadata 与 private archive authority freeze，不改变 frozen v1 AIDL transaction，也不打开 Public
compilation route。

compiler metadata 现在可广告其支持的 output schema revision，但五项 capability/dependency
Boolean 继续全部为 `false`；schema discoverability 不等于 capability promotion。

## Manifest closed-world shape

所有 formal manifests 使用同一 revision 1。base object 精确记录 contract/transport、
request/project/entry、profile/config、compiler、accepted input、outcome、effective options 与两层
output inventory authority。base keys 为：

```text
schema
revision
outputQuotaRevision
contractVersion
contractHash
outputArchiveTransportVersion
requestId
projectId
entrySourceUri
entryModulePath
targetProfile
profileSchemaRevision
profileRevision
configSchemaRevision
configKind
runtimeCapabilitySchemaRevision
compilationMode
compilerApi
compilerPackage
compilerVersion
compilerDistributionFingerprint
declarationsFingerprint
runtimeCapabilitiesFingerprint
inputArchiveSha256
requestManifestSha256
inputArchiveSchemaRevision
inputQuotaRevision
callerInventoryFingerprint
retainedInventoryFingerprint
declarationProfileFingerprint
declarationInventoryFingerprint
declarationFileCount
declarationByteLength
retainedFileCount
retainedByteLength
largestRetainedFileBytes
outcome
emitSkipped
diagnosticCount
emittedModuleCount
diagnosticsPath
effectiveOptions
effectiveOptionsFingerprint
moduleInventoryFingerprint
artifactInventoryFingerprint
files
```

exact Node revision-2 result authority 适用时，继续使用已验证的 conditional keys；不适用时这些
keys 必须缺失，而不是写 `null` 或占位值：

```text
nodeModuleOutputPolicySchemaRevision
nodeModuleOutputPolicyRevision
nodeModuleOutputPolicyFingerprint
generatedMappingSchema
generatedMappingSchemaRevision
generatedMappingPolicyRevision
resolverPolicyRevision
snapshotTargetInventoryFingerprint
packageMetadataInventoryFingerprint
packageFormatCatalogFingerprint
packageTargetInventoryFingerprint
targetUniverseFingerprint
resultSnapshotEntryPath
resultSnapshotRootNames
generatedModuleMappings
```

`files[]` 是 non-manifest artifact inventory。每个 descriptor 的 exact keys 为 `path`、`kind`、
`size`、`sha256`、`sourcePaths`。manifest 不把自身放入 `files[]`，避免 self-reference；其 exact
bytes 由 terminal Bundle 的 `outputManifestSha256` 单独绑定。formal consumer 对缺失字段与未声明
字段 fail closed。后续 S4/S5 若需要增加语义或编码，必须形成显式、可审查的兼容性决定，不能
静默重解释 S2 revision 1。

## Outcome 与 archive representability

formal archive 只表示两种已提交结果：

| outcome | `emitSkipped` | diagnostics | modules/maps | executable |
| --- | --- | --- | --- | --- |
| `emitted` | `false` | 允许 0..limit | 至少 1 个 JS；每个 JS 恰好有同路径 map | 是 |
| `diagnostics` | `true` | 至少 1 个 | 0/0；inventory 只有 diagnostics | 否 |

emitted 的 `emittedModuleCount` 必须等于 JavaScript descriptor 数；Source Map descriptor 数必须
与 module 数相同，并按 `modules/<relative>.(js|mjs|cjs)` ↔
`maps/<relative>.(js|mjs|cjs).map` 一一配对。diagnostics-only 必须
`entryModulePath=null`、`emittedModuleCount=0`，且 archive 中除 manifest 外只有
`diagnostics.json`。

cancelled、timed-out、failed 不可用 formal archive 表示，必须维持 empty-output terminal；任何
outcome/inventory split-brain 都在 caller FD commit 前拒绝。

## Provenance 与 exact accepted-input binding

output authority 不只复制 caller ID。packager 必须绑定同一个 accepted S1 snapshot，并记录三组
provenance：

- compiler：`compilerApi`、package/version、distribution fingerprint；
- profile/config：target profile、profile/config/runtime-capability revisions、config kind、
  declarations/runtime-capabilities fingerprints；
- accepted input：formal input schema/quota revisions、完整 ZIP 与 request manifest SHA-256、
  caller/retained inventory fingerprints、declaration profile/inventory fingerprints，以及
  declaration/retained count、bytes 与 largest retained file。

production staging 继续要求 compilation outcome 的 exact snapshot identity 与 accepted archive
一致，并重新核对 compiler/profile/config/options、diagnostic source 与 emitted inventory。S1
accepted-input authority 不是从可变结果临时推导；任一 provenance 漂移都使结果不可打包，不能写
caller output。

`effectiveOptions` 来自 exact profile authority 或 controlled-config result authority，caller 不能
提供。`effectiveOptionsFingerprint` 是 canonical JSON bytes 的纯 SHA-256；distribution、
declarations、runtime capability、raw config 与 inventory identity 均保留为独立字段，不能混入该
fingerprint。explicit defaults、numeric TypeScript enums、稳定 key order 与 canonical URI/path 的
最终编码细节仍由 `R2-M3-S5` 关闭；S2 冻结的是 object/fingerprint 的身份与来源边界。

## Artifact 与 module inventory authority

允许的 non-manifest artifact kinds 只有：

```text
diagnostics -> diagnostics.json
javascript  -> modules/**.(js|mjs|cjs)
source_map  -> maps/**.(js|mjs|cjs).map
```

inventory 必须按 canonical archive path 升序；path 必须是 printable ASCII relative path，满足既有
entry-name/path-depth 上限，不含反斜杠、空 segment、`.` 或 `..`，并拒绝 exact duplicate 与 ASCII
case-fold collision。SHA-256 必须是 lowercase hex；`sourcePaths` 必须排序、去重且仅位于
`sources/`、`declarations/` 或 `packages/`。JS/map 各自只关联一个 `sources/**` source；
diagnostics descriptor 可以关联可达 source/declaration/package diagnostics authority。

两种 fingerprint 共用无歧义 canonical record，但使用不同 domain prefix：

```text
autojs6.typescript.output-artifact-inventory.v1\n
autojs6.typescript.output-module-inventory.v1\n

kind NUL path NUL decimal-size NUL lowercase-sha256
NUL source-count (NUL source-path)* LF
```

- `artifactInventoryFingerprint` 覆盖 diagnostics、JavaScript 与 Source Map 全部 non-manifest
  artifacts；不覆盖 manifest、ZIP metadata 或 container bytes；
- `moduleInventoryFingerprint` 只选择 JavaScript descriptors；diagnostics-only archive 对固定
  module-domain prefix 加空 record set 求 SHA-256；
- manifest + `files[]` 必须精确覆盖 ZIP physical entries，每个声明 artifact 恰好出现一次；
- staged private summary 仍独立绑定完整 container SHA-256、container byte length 与 manifest
  SHA-256；container digest 不是 artifact inventory 的替代品。

original/generated URI、跨 profile module kind、显式 JS/map pairing descriptor 与逐模块 size/hash
的最终 manifest 表达由 `R2-M3-S4` 关闭。S2 已冻结 artifact kind/path/source/size/hash authority 与
双 inventory digest，S4 不得依赖未声明的隐式关系或把 historical Node-only mapping 当成所有 profile
的通用 contract。

## D2 quota 与 deterministic container

`outputQuotaRevision=1` 复用已冻结的 ordinary final packaged output quota：

```text
maximum emitted artifacts      = 512
maximum single JavaScript      = 512 KiB
maximum single Source Map      = 512 KiB
maximum aggregate JS/map bytes = 1 MiB
comparison                     = inclusive
measurement point              = after canonical sourceMappingURL rewrite
```

manifest、diagnostics 与 ZIP/container bytes 不进入 ordinary emitted-artifact aggregate；它们继续
受各自 transport/metadata guardrail 约束。packager 必须在 canonical `sourceMappingURL` rewrite 后
按最终 bytes 做 D2 复核，不能只信 Worker 的 raw output size。

当前 archive container 仍是 deterministic ZIP v1 `STORED`：manifest first，随后按 canonical
artifact path 排序，timestamps 为 0，不使用 compression。S2 冻结 schema/container relationship，
不宣称当前 writer 已 nonmaterializing；真正 streaming/file-backed writer 仍由 `R2-M3-O1` 负责。

## Diagnostics 与后续任务边界

S2 冻结 `diagnosticsPath=diagnostics.json`、descriptor kind、size/hash 与其在 artifact inventory 中的
authority，但在 S2 完成时**不冻结 diagnostics document shape**。当时 document 是：

```text
schema = autojs6.typescript.diagnostics.provisional
revision = 2
```

URI、range、category、code、message、related information 与 size/count limits 的 formal
diagnostics schema v1 现已由 `R2-M3-S3` 冻结；当前 identity 是
`autojs6.typescript.diagnostics/revision=1`。S2 没有把 provisional diagnostics revision 2 冒充
formal schema，也没有提前关闭 S3、S4、S5、S6、O1/O2/O3 或 M3 exit criteria。

## Failure 与 promotion policy

unknown/legacy output schema、artifact digest/size mismatch、inventory mismatch 与
outcome/inventory mismatch 全部 fail closed。packaging/validation/commit failure 映射到既有 output
transport failure 层，保持 terminal empty output，且不允许自动 retry。S6 将继续冻结 duplicate、
NFC/case-fold、ZIP64、encryption、compression 与 archive/schema mismatch 的完整跨层 failure
matrix；S2 不把已有实现证据夸大为 S6 完成。

S2 不改变以下状态：

- `typescriptCompilerAvailable=false`；
- `typescriptProjectCompilation=false`；
- `typescriptSemanticDiagnostics=false`；
- `typescriptSourceMapV3=false`；
- `typescriptNodeRuntimeRequired=false`；
- Node revision 2 继续是 Worker-result/private-output authority，不进入 Public E2E；
- 不开启 AutoJs6 host consumer、runtime execution 或 external production route。

## Canonical fixture 与验证 gates

Canonical fixture：
`app/src/test/resources/r2-output-archive-schema/s2-v1.json`，fixture identity 为
`autojs6.typescript.r2-m3-s2.output-archive-schema/revision=1`。它机器复核 single formal
revision、legacy rejection、exact base/conditional shapes、outcome matrix、S1 provenance、
effective-options binding、双 inventory canonical encoding、D2 quota、deterministic container、
S3 boundary 与 no-promotion fence。

验证入口：

- `:app:verifyR2M3S2OutputArchiveSchemaV1Fixture`；
- `:app:verifyR2M3S2OutputArchiveSchemaV1`；
- `:app:verifyR2ProjectCompilerCore`（最终无排除回归）。

| evidence | 当前状态 | 可证明范围 |
| --- | --- | --- |
| focused JVM/API tests | **PASS** | schema authority、manifest、quota、fixture 与 Public constants；75 actionable：9 executed、66 up-to-date |
| build-logic compatibility tests | **PASS** | stable commons-compress gzip path 与 trailing/concatenated rejection；7 actionable：3 executed、4 up-to-date |
| AndroidTest compile-and-package | **PASS** | Android test consumer 能编译/组装；111 actionable：26 executed、85 up-to-date |
| targeted S2 aggregate | **PASS** | 339 actionable：81 executed、258 up-to-date；`BUILD SUCCESSFUL in 2m 24s` |
| final no-exclusion Core | **PASS** | 535 actionable：263 executed、272 up-to-date；`BUILD SUCCESSFUL in 3m 19s` |

AndroidTest 证据只表示 compile/package/static closure；本轮没有运行任何 device instrumentation。

S2 完成当时使 M3 从 `7/18` 推进到 `8/18`；S3 随后于 2026-08-22 完成，当前 M3 为
`9/18`，下一活动项是 `R2-M3-S4` manifest identity/pairing freeze。
