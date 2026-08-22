# R2-M2-P2-3B Node Package-Format Transport

> **R2-M3-S1 supersession（2026-08-14）。** 本页冻结的 14/27-field transport 与后续
> 20/30/37-field Program shape 都是 pre-S1 receipt/provenance，不是当前 accepted frame。
> Formal input revision 1 的对应 Worker exact counts 为 24/37 与 30/40/47，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。原 transport receipt 仍按发生时原样保留。

日期：2026-08-02

状态：**P2-3B 已实现并冻结 exact 14/27-field transport-only identity；后续 P2-4、P2-5、
P2-6 production NodeNext Program 与 P2-7 result/private output 接线均已完成。旧 14/27 仍保持
pre-Program terminal，新 20/30/37 才拥有 Program/result 权限；P2-8 final aggregate 已完成，
P2 已关闭，M2 为 `12/18`。**

## 本切片关闭的边界

P2-3B 把 P2-3A 已由 manifest、heap snapshot 与 file-backed snapshot 冻结的 Node revision 2
package-format identity 送到 production Worker，但不创建 Node revision 2 TypeScript Program：

- 只接受 exact `(targetProfile=node, profileSchemaRevision=1, profileRevision=2,
  configSchemaRevision=1, configKind=none)`；
- frame 同时绑定具体 `entryPath` 与 P2-1 `runtimeCapabilitiesFingerprint`；
- metadata-present 路径绑定 C5 schema/policy/inventory instance、P2 module-output policy 以及 native
  catalog fingerprint；
- Worker 不接收 native 派生的 catalog canonical、records、boundary 或 `typeState`，而是从 exact
  descriptor 与原始 `package.json` bytes 独立重建；
- 完整 transport 在 `projectInputEnd` 校验后、end ACK 前以
  `P2_NODE_PROGRAM_NOT_ACTIVATED` 结束并清理 live state；
- P2-5 pure resolver v2 独立冻结 complete target-universe、module/type-reference condition 与
  extension substitution；这是 P2-3B 关闭时仍未接线的历史边界。P2-6 已通过独立
  20/30/37-field start 接入 NodeNext Program、implied format、resolver 与 emit；本切片冻结的旧
  14/27-field transport 仍以 `P2_NODE_PROGRAM_NOT_ACTIVATED` 终止，不能静默获得新权限。

P1 Node revision 1 的历史 12-field config-none start、C5 revision 4 的 controlled metadata start
以及 C6 revision 5 的 package-target start 均保持原协议；Node revision 2 不加入旧 matcher。

## Exact start shapes

Node revision 2 无 package boundary 时使用 exact 14 fields：

```text
kind, requestId, sequence,
targetProfile, profileSchemaRevision, profileRevision,
configSchemaRevision, configKind,
entryPath, runtimeCapabilitiesFingerprint,
fileCount, totalBytes,
workerSpinMilliseconds, workerSpinPhase
```

这个 shape 本身表示 catalog absent；不会发送伪造的 null record 或 no-boundary metadata record。

Metadata-present 使用 exact 27 fields，即上述 14 fields 加：

```text
packageMetadataSchemaRevision,
packageMetadataPolicyRevision,
packageMetadataPolicyFingerprint,
packageMetadataCanonical,
packageMetadataInventoryFingerprint,
packageMetadataFileCount,
packageMetadataByteLength,
nodePackageFormatSchemaRevision,
nodePackageFormatCatalogRevision,
nodePackageFormatCatalogFingerprint,
nodeModuleOutputPolicySchemaRevision,
nodeModuleOutputPolicyRevision,
nodeModuleOutputPolicyFingerprint
```

Node revision 2 使用更明确的 `packageMetadataInventoryFingerprint`；历史 C5/C6 start 继续使用
`packageMetadataFingerprint`，不能通过字段别名或 partial shape 混用。Metadata-present frame 不发送
catalog canonical、records、boundary、package `type` 或 `typeState`。这样 native 声明的唯一派生值是
期望 catalog fingerprint，Worker 必须用本地冻结 policy 与 raw bytes 重算后比较。

## 顺序、ACK 与预算

Node revision 2 不发送 config frames，也不允许 package-target frames。文件顺序固定为：

```text
projectInputStart
  -> C5 package metadata files（inventory order；若 present）
  -> declaration/source files（snapshot order）
  -> projectInputEnd
```

所有文件共享连续 `fileIndex`、`fileCount` 与 `totalBytes` accounting。每个 start/chunk/end frame
继续使用 stop-and-wait；descriptor path/size/digest、raw chunk 长度、UTF-8、C5 strict JSON、file
coverage 与 aggregate byte budget 均在 ACK 前验证。Metadata file-end 只有在 raw bytes digest、strict
JSON 与 P2 top-level `type` 状态成功安装后才 ACK。

最大合法 C5 inventory 可以让 metadata-bearing start 超过 ordinary 128 KiB envelope。因此 native
framer 与 compiler page 使用独立 **576 KiB start-only** hard limit；所有后续 native input frame、
Worker→page→native output 与普通 native message 仍保持 128 KiB。页面只有在消息匹配 frozen exact
27-key shape、`targetProfile=node`、`profileRevision=2` 且存在 `packageMetadataCanonical` 时才授予
扩展预算；extra key、非特权 profile 与绝对超限均拒绝。最大合法 native C5 regression 使用 128 roots、
256 metadata files，首帧约 403 KiB，并完整 drain 后续 stop-and-wait frames。

P2-3B Worker source inventory 只允许 `.ts`/`.d.ts`；entry 必须是非 declaration
`/sources/*.ts`。后续 P2-4 让 exact Node revision-2 native archive/snapshot 接受 `.mts/.cts`；
在 P2-6 完成前，framer 与 Worker ingress 继续 fail closed。当前 P2-6 只通过新增 20/30/37-field
identity 开放 Program，未改变上述旧 14/27-field 历史边界。

## Worker 独立重建

Worker 对每个 C5-validated `package.json` 只保留 frozen 小记录：metadata path、boundary、size、
SHA-256 与 `missing/commonjs/module` type state；不把 raw JSON root 放进 P2 catalog record。任意 present
但非 exact `commonjs`/`module` 的 `type` 都以 `NODE_PACKAGE_TYPE_INVALID` 拒绝。

`projectInputEnd` 在完整 coverage 后使用 Worker 自己的 P2/C5 constants 重建：

1. C5 inventory canonical input 与 instance fingerprint；
2. ordered boundary/type records；
3. P2 catalog canonical input 与 fingerprint。

重建 fingerprint 必须等于 start 声明的 native catalog fingerprint。即使 C5 descriptor、canonical
与 raw bytes 被一起重新计算为另一份自洽 inventory，只要 native catalog fingerprint 仍属于旧实例，
Worker 也会在 end ACK 前拒绝，避免 split-brain。

成功匹配后仍立即触发 transport-only terminal；不会设置 `framedCompilationRequestId`，不会调用
resolver session、`createIncrementalProgram`、emit 或 result framing。outer error path 负责清零 active
raw byte arrays、清空 source texts/records/catalog/sets/directory index，并把三个 framed globals 恢复为
idle，使 P1 clean successor 可继续运行。

每个可能跨 `await` 的 input frame 还在第一次异步操作前取得 generation token。same-request busy
challenger 会取消 incumbent，并由第一个 challenger 唯一认领 terminal；后续 challenger 与旧 continuation
均不能再修改 state、ACK 或发布 result。different-request challenger 只失败自己的 request，不取消
incumbent。start、file-end、input-end 的 delayed WebCrypto regression 都连续注入两个 challenger，
证明单 terminal、无 late ACK/phase/result、raw bytes 清零与 idle successor。

## Gates

可复现入口：

```text
:app:testDebugUnitTest --tests io.github.supermonster003.autojs6.plugin.typescript.R2ProjectFramedTransportTest
:app:verifyR2M2P2P3bNodePackageFormatWorkerTransportDesktop
:app:verifyR2M2P2P3bNodePackageFormatTransport
```

Native focused gate 覆盖 exact 14/27 keys、metadata-first、zero config、stop-and-wait、最大合法 C5
start-only envelope、catalog combination drift 与 framer `.mts/.cts` closure。Worker desktop gate 使用
actual production Worker、真实 TypeScript distribution、页面 VM 与独立 JavaScript catalog oracle，
覆盖 no-boundary、4 个 frozen valid catalog、96-root generated large catalog、8 个 invalid type、
exact-key/revision/fingerprint/accounting/order/digest/entry/source/race mutation、stale-catalog attack、
raw-byte cleanup、零 semantic activation 与 clean P1 successor。聚合 gate 继续依赖 P2-3A、P1、C5/C6
Worker/Program regression、完整 Debug JVM tests 与 Android test compilation。

2026-08-02 的验证结果：

- native `R2ProjectFramedTransportTest` 为 `32/32` 通过；
- Worker harness 接受 1 个 no-boundary、4 个 frozen metadata-present catalog 与 1 个 96-root
  generated large catalog，覆盖 1 个 recomputed-C5/stale-catalog attack、8 个 invalid type 与 38 个
  冻结 rejection IDs；collision/race 等场景合计执行 54 个 P2 request，随后 1 个 P1 clean successor 成功；
- P1 production Worker、C5 metadata transport 与 C6-D package-target transport 独立回归均通过；
- `verifyR2M2P2P3bNodePackageFormatWorkerTransportDesktop` 使用 actual production Worker 与
  generated TypeScript 6.0.3 通过；
- 未拆分的 `verifyR2M2P2P3bNodePackageFormatTransport` 完整通过，共 282 个 actionable tasks
  （50 executed、232 up-to-date），包含完整 Debug JVM、Android test compilation、Debug/Release
  APK、P1、C5、C6 real Program、P2 foundation、P2-3A 与 P2-3B gates；
- 后续 `verifyR2M2P2P4NodeSourceKindAdmission` 继续传递覆盖该聚合并完整通过，共 283 个
  actionable tasks（48 executed、235 up-to-date）。

## 后续切片

P2-3A 与 P2-3B 合计只完成 package-format input/transport authority。后续 P2-4 已仅对 exact
Node revision 2 增加 profile-aware `.mts/.cts` native archive/snapshot admission，见
[P2-4 Source-kind Admission](NODE_SOURCE_KIND_ADMISSION_R2_M2_P2_4.md)。P2-5 已完成 pure
package-target/resolver v2、ordinary snapshot 加 optional package-target 的 complete target-universe、
metadata absent/present identity 与 type-reference `types` authority，见
[P2-5 Package-target / Resolution v2](NODE_PACKAGE_TARGET_RESOLUTION_V2_R2_M2_P2_5.md)。P2-6 已完成
production Worker 的真实 NodeNext Program/resolver/emit，见
[P2-6 NodeNext Program Integration](NODE_NEXT_PROGRAM_INTEGRATION_R2_M2_P2_6.md)。P2-7 已在不
修改本切片 legacy 14/27 terminal 的前提下，打开 20/30/37 framed result 并完成 native/private
output parity，见
[P2-7 NodeNext Result and Private Output](NODE_NEXT_RESULT_PRIVATE_OUTPUT_R2_M2_P2_7.md)。后续
P2-8 final aggregate 已聚合上述历史边界并关闭 P2，见
[P2-8 Final Aggregate](NODE_MODULE_OUTPUT_FINAL_AGGREGATE_R2_M2_P2_8.md)。`R2-M2-P3` 随后已完成
[oracle-only compiler-bundle golden](COMPILER_BUNDLE_GOLDEN_R2_M2_P3.md)，P4 随后已完成
[syntax fixture matrix](SYNTAX_FIXTURE_MATRIX_R2_M2_P4.md)，P5 也已完成
[compiler-boundary fixture matrix](COMPILER_BOUNDARY_FIXTURE_MATRIX_R2_M2_P5.md)，X1 也已完成
[public-shaped deterministic bundle](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。X1
只开放 private Rhino/Node revision-1 end-to-end authority；四项 public capability、
external/runtime、Node revision-2 end-to-end 与 Node Runtime requirement 继续关闭。
实时进度以 [R2 Roadmap](../roadmap/R2.md) 为准。
