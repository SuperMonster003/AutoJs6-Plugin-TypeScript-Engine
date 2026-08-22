# R2-M2-P2-3A Node Package-Format Authority

> **R2-M3-S1 supersession（2026-08-14）。** 本页中的 input manifest revision `6`、14/27-field
> 与 20/30/37-field start 都是 pre-S1 receipt/provenance。当前 formal input archive 只接受
> revision 1；对应 Worker exact counts 已变为 24/37 与 30/40/47，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。package-format policy 与历史 receipt 仍保留。

日期：2026-08-02

状态：**P2-3A 已建立 pure catalog、input-archive schema v1 Node-r2 metadata 条件形状与
heap/file-backed identity；
后续 P2-3B transport-only authority、P2-6 production Program、P2-7 result/private output 接线与
P2-8 final aggregate 均已完成；P2 已关闭，M2 为 `12/18`。**

本文记录 P2-3A 完成时的 internal/debug-only implementation boundary，不是 public schema，也不构成
production Node compilation admission。后续 P2-3B 增加 production Worker 的 transport-only 消费，
P2-4 让 exact Node revision 2 native archive/snapshot 接受 `.mts/.cts`；P2-6 又以独立
20/30/37-field Program identity 接入 Worker/NodeNext Program，P2-7 随后接通 framed result 与
native/private output authority。四项 public compiler capability 与 `NODE_RUNTIME_REQUIRED` 均保持
`false`。

## P2-3A 已建立的边界

当前 P2-3A 只关闭以下 native input-side authority：

- 从 exact C5 `R2PackageMetadataInventory` 及其已校验原始 `package.json` bytes 纯派生
  `R2NodePackageFormatCatalog`；
- Node revision 2 config-none 且携带 C5 metadata 时使用 schema v1 的
  `node-r2-metadata` exact 条件形状；
- heap snapshot 与 file-backed immutable spool 都重建并绑定同一个 catalog identity；
- framer 在任何 frame 前重新验证 exact local profile 与 Rhino/Node revision package 组合，
  delegated snapshot 不能绕过 snapshot constructor 的矩阵；
- P2-3A 当时以显式 framer fence 保持 Worker 未激活；该历史 fence 已由后续 P2-3B 的 exact
  14/27-field transport-only authority 替换。

P2-3A 单独只证明 archive/snapshot package-format 输入身份；Worker 消费与 pre-Program fence 证据
见 [P2-3B Package-Format Transport](NODE_PACKAGE_FORMAT_TRANSPORT_R2_M2_P2_3B.md)。后续 P2-4
已作为独立切片完成 native source-kind admission，见
[P2-4 Source-kind Admission](NODE_SOURCE_KIND_ADMISSION_R2_M2_P2_4.md)。

## Pure catalog authority

Catalog schema 为 `autojs6.typescript.node-package-format-catalog`，schema revision 为 `1`，
catalog revision 为 `1`。Canonical domain 为：

```text
autojs6.typescript.node-package-format.catalog.v1
```

实际 canonical input 是单一连续 printable-ASCII 字符串。字段顺序与拼写精确如下；最后的
`record` 按 C5 metadata path 严格排序，并在 present catalog 中重复一次或多次。C5
project-root invariant 与 catalog defense-in-depth 检查共同保证第一条 record 必须是
`package.json` 对应的 `/` boundary：

```text
autojs6.typescript.node-package-format.catalog.v1
|schemaRevision=1
|catalogRevision=1
|profileSchemaRevision=1
|profileRevision=2
|nodeModuleOutputPolicySchemaRevision=1
|nodeModuleOutputPolicyRevision=1
|nodeModuleOutputPolicyFingerprint=5c2294f0ee9d6d25bd2132b4ea24866d0a5f9caaa521a63bf81ffe1d897f6ce7
|packageMetadataSchemaRevision=1
|packageMetadataPolicyRevision=1
|packageMetadataPolicyFingerprint=fab8fec29ed0f0a1de65594620e233c9a9bef2fa514dac16c3079d5570325e97
|packageMetadataInventoryFingerprint=<exact C5 inventory instance lowercase SHA-256>
|record=<packageMetadataPath>,<packageBoundaryPath>,<size>,<sha256>,<typeState>
```

这里的换行仅用于展示，实际 canonical input 不含换行。Catalog fingerprint 是该连续字符串
US-ASCII bytes 的 lowercase SHA-256。`packageMetadataInventoryFingerprint` 是具体 C5 inventory
canonical input 的实例 fingerprint，不是 C5 policy fingerprint；两者不可互换。

每条 `record` 的 exact field order 为：

1. C5 relative metadata path；
2. `/` 或由该 path 精确推导的 absolute package boundary；
3. C5 descriptor size；
4. C5 descriptor lowercase SHA-256；
5. exact top-level `type` state：`missing`、`commonjs` 或 `module`。

无 accepted metadata 时不创建空 catalog，而是以 null catalog 表示 `no-boundary`；此时生成映射
authority 的 inventory fingerprint 与 boundary 也都为 null。Present record 不允许使用
`no-boundary`。`package.json` 缺少 `type` 精确派生为 `missing`；只有 string `commonjs` 与
`module` 被接受，其他任何 present value 均以 `NODE_PACKAGE_TYPE_INVALID` fail closed。

Catalog 重用 C5 的 strict UTF-8、duplicate-safe JSON、forbidden lifecycle key、depth/node、
single-file/aggregate bytes、file/root count、path sorting、exact/ASCII case-fold collision 与
explicit dependency-root 约束，不建立第二套放宽后的 metadata parser。调用方 byte arrays 会被
复制；用于解析的临时副本在派生后清零，catalog 本身只保留不可变 descriptor/type identity。

## Input-archive schema v1 conditional matrix

所有合法形状共享 `schema=autojs6.typescript.input-archive/revision=1`。Exact top-level base
key set 为：

```text
schema, revision, inputQuotaRevision, contractVersion, contractHash,
inputArchiveTransportVersion,
requestId, projectId, entrySourceUri, entryPath, targetProfile, compilationMode,
profileSchemaRevision, profileRevision, runtimeCapabilitySchemaRevision,
configSchemaRevision, runtimeCapabilitiesFingerprint, compilerPackage,
compilerVersion, compilerDistributionFingerprint, declarationsFingerprint,
declarationProfileFingerprint, configKind, callerInventoryFingerprint, rootPaths, files
```

P2-3A 不重解释其他 schema-v1 条件形状。与本切片最相关的 exact difference 如下：

| Conditional shape | Exact authority/用途 | Conditional top-level key | Raw config | C5 metadata | Catalog 行为 |
| --- | --- | --- | --- | --- | --- |
| `node-r2-none` | registered-only Node rev2 config-none，无 metadata | 无 | 禁止 `config` object，`configKind=none` | 禁止 `packageMetadata` | catalog 必须为 null |
| `rhino-r1-tsconfig-metadata` | controlled-tsconfig + C5 metadata；不用于 Node rev2 | `config`, `packageMetadata` | 必须存在 exact controlled config object | 必须存在 | 按既有 C5/controlled-config 语义处理，不升级为 Node rev2 catalog |
| `node-r2-metadata` | exact Node profile schema rev1/profile rev2、config-none、有 C5 metadata、无 package targets | `packageMetadata` | 禁止 `config` object，`configKind=none` | 必须存在 | consumer 从 exact C5 descriptors + archive raw bytes 派生 catalog |

其他合法的 Rhino config-none/controlled-config、Node revision-1，以及 metadata + target-v1/v2
形状不受本切片改变。`node-r2-metadata` 的选择是 authority-driven：只有 exact Node revision 2、
raw config absent、C5 metadata present、package targets absent 才成立；Node revision 2 无 metadata
必须使用 `node-r2-none`。跨 profile 冒充、缺 metadata、unexpected config/target 或 identity drift
均拒绝。

`node-r2-metadata` 的 `packageMetadata` object 仍使用 C5 exact keys：

```text
schemaRevision, policyRevision, policyFingerprint, inventoryFingerprint,
dependencyRoots, files
```

其中每个 `files[]` object 的 exact keys 为 `path, sha256, size`。Manifest/archive 必须携带并
覆盖原始 `package.json` bytes；manifest 不携带 `typeState`、boundary、catalog canonical input
或 catalog fingerprint。任何调用方提供的派生字段都是额外 key 并被拒绝，consumer 必须从
exact raw bytes 独立重建，避免“descriptor 说一套、派生状态说另一套”的 split-brain。

## Heap 与 file-backed identity

Heap adapter 在 archive inventory、size、digest、C5 JSON policy 全部通过后，把原始 metadata
bytes 交给 snapshot。Node revision 2 snapshot 从 owned copies 派生 catalog，并要求 metadata 与
catalog 同时存在或同时缺失；catalog C5 canonical input/fingerprint 及每条 path/size/digest 必须
与 inventory 精确一致。

File-backed adapter 不把 metadata 变成 text VFS authority。它从 immutable spool 的 exact
offset/size/digest 读取有界临时 bytes，验证 C5 后派生 catalog并清零临时 bytes；seal 后的
`R2FileBackedProjectSnapshot` 再从 immutable records 重读并重建一次，逐项比较：

- dependency roots 与 ordered records；
- C5 inventory canonical input 与 instance fingerprint；
- catalog canonical input 与 catalog fingerprint。

任一不一致都拒绝 snapshot。关闭 snapshot 只使 cursor 失效，不改变已经冻结的不可变 catalog
identity。Heap 与 file-backed 因而共享同一派生函数、同一 C5 instance binding 与同一
`NODE_PACKAGE_TYPE_INVALID` 失败语义。

## Shared fixture 与门禁

共享 fixture `r2-node-package-format/catalog-v1.json` revision 1 固定 4 个 valid catalog：

- `root-missing`；
- `root-commonjs`；
- `root-module`；
- `nested-longest-boundary`，同时证明 segment-aware longest boundary，不把
  `nestedness` 误认为 `nested`。

它还固定 8 个 invalid present `type`：empty string、case drift `Module`、unknown string
`node`、null、boolean、number、array、object，以及 5 个 invalid lookup path：缺失 leading
slash、仅 root、parent traversal、backslash 与 empty segment。No-boundary fixture 独立冻结 null
inventory fingerprint/null boundary，不能伪造为一条 metadata record。

当前可复现 gates 为：

```text
:app:testDebugUnitTest --tests io.github.supermonster003.autojs6.plugin.typescript.R2NodePackageFormatCatalogTest
:app:verifyR2M2P2P3aNodePackageFormatCatalogDesktop
:app:verifyR2M2P2P3aNodePackageFormatAuthority
```

JVM targeted gate 当前结果为 `tests=4, failures=0, errors=0, skipped=0`。独立 JavaScript
mirror/harness 从同一 fixture 重算 strict type state、C5 inventory canonical/fingerprint、catalog
canonical/fingerprint 与 longest boundary，当前结果为
`valid=4, invalidType=8, invalidLookup=5`。JavaScript mirror 消费已经通过 frozen C5 authority 的
输入；完整 strict UTF-8/duplicate/lifecycle/path C5 rejection 仍由 C5 Kotlin/Worker gates 证明，
这里不再实现一套第二 parser。

聚合 `verifyR2M2P2P3aNodePackageFormatAuthority` 依赖 P2 authority foundation、JavaScript
desktop parity、完整 Debug JVM unit tests 与 Android test compilation；它还静态确认 schema-v1
`node-r2-metadata` 条件形状、
heap/file-backed derivation。这个聚合 gate 是 P2-3A native identity 的持续回归入口；P2-3B
frame/Worker evidence 由独立后续 gate 维护。

2026-08-02 的组合验证结果为：

- 11 个 focused JVM test class 共 `177/177` 通过；
- 先行的可归因聚合（排除旧 Rhino desktop calibration）通过；
- 随后的未排除完整 `verifyR2M2P2P3aNodePackageFormatAuthority` 通过，共 280 个 actionable
  tasks，包含旧 Rhino desktop golden、P1 Node revision 1、P2 foundation、Debug/Release APK、
  Android test compilation 与 P2-3A gate；
- 独立 semantic audit 发现的 framer delegated-snapshot matrix bypass 与 Kotlin/JavaScript
  invalid-lookup divergence 均已修复并复核，剩余 Blocker/High/Medium 为零。

## 后续切片：P2-3B（已完成）

P2-3B 已移除历史 framer fence 并完成以下要求：

1. 为 exact Node profile rev2 增加 revisioned `projectInputStart` package-format authority，绑定
   P2 policy、C5 policy、C5 inventory instance 与 catalog identity；
2. 继续传输 exact C5 descriptors 和原始 `package.json` bytes，而不是信任 native 派生的
   `typeState`；保持 stop-and-wait、exact coverage、byte/file/path budgets 与 digest rehash；
3. 在 Worker ingress strict parse 原始 bytes，并独立重建相同 catalog；revision、fingerprint、
   ordering、boundary、type 或 profile 任一漂移都必须在创建 Program 前拒绝；
4. 增加 native/Worker parity、mutation、legacy revision 1/frame regression 与 clean-successor
   gates，并继续证明 Worker 无网络、`ts.sys`、child process 或 Node Runtime；
5. 保持 resolver-v2、真实 NodeNext Program/emit、`.mts/.cts` archive admission、result/private
   output mapping 关闭，分别留给 P2-4 至 P2-7。

上述第 5 条是 P2-3B 完成时的历史边界；后续 P2-4 开放 exact Node revision-2 native
archive/snapshot `.mts/.cts` admission，P2-6 再以独立 20/30/37-field identity 开放受控
Worker/Program/emit，P2-7 又开放 20/30/37 framed result 并由 native/private output 复算 mapping。
旧 14/27-field transport 仍保持原 pre-Program fence，public/end-to-end admission 继续关闭。

完整协议、mutation 与 cleanup 证据见
[P2-3B Package-Format Transport](NODE_PACKAGE_FORMAT_TRANSPORT_R2_M2_P2_3B.md)。P2-3B
仍不授予 Node revision 2 end-to-end 或 Worker-result admission。

## Public 与 Runtime 不变量

P2-3A/P2-3B 后以下 public metadata 仍精确为 `false`：

- `COMPILER_AVAILABLE`；
- `PROJECT_COMPILATION`；
- `SEMANTIC_DIAGNOSTICS`；
- `SOURCE_MAP_V3`。

`NODE_RUNTIME_REQUIRED=false` 同样保持不变。Compiler plugin 继续使用 APK 内置 TypeScript
Worker，不发现、不启动、也不依赖 Node Runtime。上述状态只有后续独立 public admission
milestone 才能改变，不能由 P2-3A/P2-3B 或后续 P2-6/P2-7 内部接线隐式开放。P2-6 当前证据见
[P2-6 NodeNext Program Integration](NODE_NEXT_PROGRAM_INTEGRATION_R2_M2_P2_6.md)，P2-7 当前证据见
[P2-7 NodeNext Result and Private Output](NODE_NEXT_RESULT_PRIVATE_OUTPUT_R2_M2_P2_7.md)，最终聚合见
[P2-8 Final Aggregate](NODE_MODULE_OUTPUT_FINAL_AGGREGATE_R2_M2_P2_8.md)。`R2-M2-P3` 随后已完成
[oracle-only compiler-bundle golden](COMPILER_BUNDLE_GOLDEN_R2_M2_P3.md)，P4 随后已完成
[syntax fixture matrix](SYNTAX_FIXTURE_MATRIX_R2_M2_P4.md)，P5 也已完成
[compiler-boundary fixture matrix](COMPILER_BOUNDARY_FIXTURE_MATRIX_R2_M2_P5.md)，X1 也已完成
[public-shaped deterministic bundle](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。X1
只开放 private Rhino/Node revision-1 end-to-end authority；Public/external/runtime、Node
revision-2 end-to-end 与 Node Runtime dependency 继续关闭。实时进度以
[R2 Roadmap](../roadmap/R2.md) 为准。
