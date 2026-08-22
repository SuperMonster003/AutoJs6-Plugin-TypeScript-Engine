# R2-M2-P2-6 NodeNext Program Integration

> **R2-M3-S1 supersession（2026-08-14）。** 本页中的 input manifest revision `7`、14/27-field
> 与 20/30/37-field start 是 pre-S1 receipt/provenance，不是当前 accepted frame。Formal input
> revision 1 的对应 Worker exact counts 为 24/37 与 30/40/47，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。Program semantics 与历史 receipt 仍保留。

日期：2026-08-10

状态：**P2-6 production Worker/TypeScript Program 接线与 aggregate gate 已完成；其历史
`P2_NODE_RESULT_NOT_ACTIVATED` 边界已由 P2-7 result/private-output 接线替代。`R2-M2-P2`
已由 P2-8 final aggregate 关闭，当时 M2 为 `12/18`；P3 oracle-only aggregate、P4 syntax
fixture matrix、P5 compiler-boundary matrix 与 X1 public-shaped deterministic bundle 随后已通过。
X1 只开放 private Rhino/Node revision-1 end-to-end authority；Public/external/runtime 与
Node revision-2 end-to-end 继续关闭。实时进度以 [R2 Roadmap](../roadmap/R2.md) 为准。**

## 切片边界

P2-6 把 P2-0 至 P2-5 已冻结的 Node revision-2 source/module/output、package-format 与 resolver-v2
authority 串行接入 production Worker，但不越过结果边界：

- native archive 增加 input-archive schema v1 的 `node-r2-metadata-target-v2` 条件形状与独立
  `R2NodePackageTargetV2Authority` / `R2NodePackageTargetV2Inventory`；
- heap 与 file-backed snapshot 都持有、复验并按既定顺序流式传输 metadata、可选 v2 package
  targets 与 ordinary project files；C6 v1 与 Node v2 target inventory 不能同时出现；
- native framer 从完整 ordinary snapshot inventory 和可选 v2 package targets 构造
  `R2NodeResolutionV2SnapshotTargetInventory` / `R2NodeResolutionV2Authority`，自动选择 exact
  `20/30/37`-field Program start；
- production Worker 独立重建 package-format、package-target 与 complete target-universe identity，
  创建真实 TypeScript `6.0.3` NodeNext Program，执行 resolver hooks、compile 与 emit；
- 在 P2-6 关闭时，compile/emit 成功后仍以 `P2_NODE_RESULT_NOT_ACTIVATED` 结束，且在任何
  `projectResultStart` 之前 fail closed；P2-7 已有意移除这一个 result fence，同时保持本节其余
  ingress/Program authority 不变。

因此 P2-6 是 production **Program/emit authority**，不是 native result、private output archive 或
public production activation。

## Native manifest、snapshot 与 framing

Schema v1 的 `node-r2-metadata-target-v2` 只允许 exact registered-only Node revision 2、
config-none、已接受 package
metadata 与 v2 package-target authority 的闭合组合。v2 target inventory 继续绑定 P2-5 policy
revision/fingerprint、完整 C5 metadata inventory fingerprint、路径/类型/size/SHA-256 与总量预算；
缺 metadata、identity split-brain、bytes 漂移、v1/v2 target 混用均在 Worker 前拒绝。

Heap snapshot 防御复制 target bytes；file-backed snapshot 保存独立 record，并在打开 cursor 前复验
路径、长度与 digest。两条路径都暴露独立的 `openNodePackageTargetV2File()` 生命周期入口；close 后
不得重新打开，也不能把 v2 bytes 混入 C6 v1 cursor。

P2-6 的 start shape 为严格加法扩展：

| Authority 状态 | Exact start | Identity 关系 |
| --- | ---: | --- |
| metadata absent | `20` fields | frozen legacy `14` + module-output `3` + resolver `3` |
| metadata present、targets absent | `30` fields | frozen legacy `27` + resolver `3` |
| metadata present、v2 targets present | `37` fields | P2-6 `30` + Node package-target `7` |

Resolver identity 的三个新增字段为 `resolverPolicyRevision`、
`snapshotTargetInventoryFingerprint` 与 `targetUniverseFingerprint`。37-field shape 再增加独立命名的：

```text
nodePackageTargetSchemaRevision
nodePackageTargetPolicyRevision
nodePackageTargetPolicyFingerprint
nodePackageTargetCanonical
nodePackageTargetFingerprint
nodePackageTargetFileCount
nodePackageTargetByteLength
```

这些字段不能别名为 C6 v1 的 `packageTarget*`。Legacy `14/27` shape、其 exact-key classifier 与
`P2_NODE_PROGRAM_NOT_ACTIVATED` terminal 原样保留，只作为 P2-3B transport regression；native
producer 已迁移为 `20/30/37` Program owner。

## Production Worker Program

Worker 只接受三个 exact Program shape，并从 frame 内容独立复算 canonical input、fingerprint、文件
数量/长度、ordinary snapshot inventory 与最终 target-universe fingerprint。它不信任 caller 提交的
module format、target kind 或 resolver outcome，也不调用默认 resolver、`ts.sys`、filesystem 或
network。

真实 Program 使用 TypeScript `6.0.3` 的 NodeNext 选项：

- `.mts` 固定 ESM、`.cts` 固定 CommonJS；`.ts` 由 nearest accepted package `type` 决定；
- `createNodeRevisionTwoSourceFile()` 通过 `CreateSourceFileOptions` 写入逐文件
  `impliedNodeFormat`；
- `resolveModuleNameLiterals` 与 `resolveTypeReferenceDirectiveReferences` 只消费受控 resolver-v2
  session；relative、`#imports`、package `exports` 与 type-reference `types` mode 均受 snapshot
  grant 约束；
- runtime target `.js/.mjs/.cjs` 分别 exact substitution 到 `.ts/.mts/.cts`，declaration target
  `.d.ts/.d.mts/.d.cts` 保持 exact kind；
- emit 只接受与 P2 policy identity 和 extension/path matrix 一致的 `.js/.mjs/.cjs` 与对应 Source
  Map，不允许目录、basename 或 extension 漂移；在 P2-6 关闭时 generated URI/result evidence
  留给 P2-7 复算，P2-7 现已完成该 exact-snapshot authority。

P2 start 完成输入 ACK 后创建唯一 Node revision-2 resolution session 与唯一 incremental Program。
在 P2-6 历史边界中，`beginProjectResult()` 会先完成 compile 与 emit，再命中
`P2_NODE_RESULT_NOT_ACTIVATED`，不发送 `projectResultStart`。P2-7 已把同一 Program 接到既有
stop-and-wait framed result，并把 emit completion 延长到最终 result ACK；project、resolver、target
VFS、result bytes 与 transport state 的清理及 P2/P1 clean successor 不变量保持不变。

## 门禁与验证

直接 Worker/TypeScript Program gate：

```powershell
node app/src/test/scripts/verify-r2-nodenext-revision2-program.js `
  app/src/main/assets/typescript-compiler-r1/compiler-worker.js `
  app/build/generated/typescriptCompilerAssets/assets/typescript/6.0.3/lib/typescript.js `
  app/src/test/resources/r2-project-compiler/provisional-nodenext-p2.json `
  app/src/test/resources/r2-project-compiler/provisional-nodenext-p1.json `
  app/src/test/resources/r2-node-package-format/transport-v1.json `
  app/src/test/resources/r2-package-metadata/policy-v1.json `
  app/src/test/resources/r2-node-module-output/policy-v1.json `
  app/src/test/resources/r2-package-targets/policy-v2.json `
  6.0.3
```

稳定成功输出为：

```text
R2-M2-P2-6 Worker/TypeScript Program verified: exact starts=20/30/37, ts/mts/cts implied formats, relative/imports/exports/type-reference, js/mjs/cjs+maps, stable failures=2x2, clean P2 successor=1, legacy 14/27 identity unchanged, P1 regression=1, projectResultStart=0 for P2, default resolver/ts.sys/network=0
```

Gradle 分层入口：

```text
:app:verifyR2M2P2P6NodeNextProgramDesktop
:app:verifyR2M2P2P6NodeNextProgramIntegration
:app:verifyR2ProjectCompilerCore
```

2026-08-10 的最终 Core 验证通过：508 actionable tasks，112 executed，396 up-to-date，
`BUILD SUCCESSFUL in 3m 25s`。该 Core 同时绑定 P1、C6 v1、legacy `14/27`、P2-5、JVM tests、
Android instrumentation compilation、host/source/API/public fences；本切片未运行 ADB 或设备事务。

## P2-7 替代的历史边界与当前未开放项

P2-6 关闭时，Node revision 2 不在 Worker-result/end-to-end admission，native result/private
archive 未接线，并固定以 `P2_NODE_RESULT_NOT_ACTIVATED` 结束。P2-7 已替代上述 result fence：
Worker 保持 exact 11-key result/4-key outputs，native 从 exact snapshot 独立复算 mapping/resolver
authority，private manifest revision 3 记录逐模块 evidence。见
[P2-7 NodeNext Result and Private Output](NODE_NEXT_RESULT_PRIVATE_OUTPUT_R2_M2_P2_7.md)。当前仍有：

- Node revision 2 只在 `workerResultAdmittedAuthorities`，仍不在 end-to-end admitted authority set；
- `COMPILER_AVAILABLE`、`PROJECT_COMPILATION`、`SEMANTIC_DIAGNOSTICS`、`SOURCE_MAP_V3` 四项 public
  capability 继续为 `false`；Node Runtime dependency 继续不存在；
- `R2-M2-P2` checklist 已由 P2-8 勾选，M2 为 `12/18`。

## P2-8 收口与下一项

P2-7 已在不改变 exact `20/30/37` Program ingress、legacy `14/27` regression、resolver-v2 或
generated mapping 决策的前提下，完成 Worker framed result、native exact-snapshot mapping 复算与
private output revision 3。P2-8 随后聚合 P1、C6 v1、全部 v2 gates、APK/API/public/source
fences，并判定关闭 P2，见
[P2-8 Final Aggregate](NODE_MODULE_OUTPUT_FINAL_AGGREGATE_R2_M2_P2_8.md)。`R2-M2-P3` 随后已完成
[oracle-only compiler-bundle golden](COMPILER_BUNDLE_GOLDEN_R2_M2_P3.md)，P4 随后已完成
[syntax fixture matrix](SYNTAX_FIXTURE_MATRIX_R2_M2_P4.md)，P5 也已完成
[compiler-boundary fixture matrix](COMPILER_BOUNDARY_FIXTURE_MATRIX_R2_M2_P5.md)，X1 也已完成
[public-shaped deterministic bundle](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。X1
只开放 private Rhino/Node revision-1 end-to-end authority；Public/external/runtime 与 Node
revision-2 end-to-end 未被隐式开启，也没有创建 runtime engine。实时进度以
[R2 Roadmap](../roadmap/R2.md) 为准。
