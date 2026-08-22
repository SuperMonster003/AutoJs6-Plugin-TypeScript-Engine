# R2-M2-P2-7 NodeNext Result and Private Output

> **R2-M3-S1 supersession（2026-08-14）。** 本页中的 14/27-field 与 20/30/37-field input starts
> 是 pre-S1 receipt/provenance，不是当前 accepted frame。Formal input revision 1 的对应 Worker
> exact counts 为 24/37 与 30/40/47，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。
>
> **R2-M3-S2 supersession（2026-08-22）。** 本页的 provisional private manifest revisions
> `2`/`3` 现在仅是 pre-S2 receipt/provenance，不是当前 output contract，也不是 formal revision 1
> alias。当前所有已打包 Rhino/Node profiles 使用
> `autojs6.typescript.output-archive/revision=1`，并绑定 S1 accepted-input provenance 与独立
> module/artifact inventory fingerprints，见
> [S2 Output Archive Schema v1](OUTPUT_ARCHIVE_SCHEMA_V1_R2_M3_S2.md)。

日期：2026-08-10

状态：**P2-7 production Worker result、native exact-snapshot result authority 与 private output
接线已完成；P2-8 final aggregate 已关闭 `R2-M2-P2`，当时 M2 为 `12/18`。P3
oracle-only aggregate、P4 syntax fixture matrix、P5 compiler-boundary matrix 与 X1 public-shaped
deterministic bundle 随后已通过。X1 只开放 private Rhino/Node revision-1 end-to-end
authority；Public/external/runtime、Node revision-2 end-to-end 与 Node Runtime dependency 继续
关闭。实时进度以 [R2 Roadmap](../roadmap/R2.md) 为准。**

## 切片边界

P2-7 只打开 P2-6 exact `20/30/37` Node revision-2 Program 的既有 framed-result 路径，并在
native 侧从已接受 snapshot 独立复算 output authority。它没有扩张 Worker ingress，也没有让
Worker 声明 native 才知道的 project identity：

- exact `20/30/37` start keys、字段含义与 P2-6 resolver/package-target identity 不变；
- legacy `14/27` transport 继续以 `P2_NODE_PROGRAM_NOT_ACTIVATED` 在 Program 前结束；
- config-none Worker result 继续是 exact `11` keys，单个 output 继续是 exact `4` keys；
- Worker 不新增 `projectId`、generated URI、package-boundary record 或 canonical mapping claim；
- Node revision 2 只加入 `workerResultAdmittedAuthorities`，不加入
  `endToEndAdmittedAuthorities`；
- private compilation manifest 对 Node revision 2 使用 additive provisional revision `3`；既有
  provisional revision `2`、P1、C6 与 public contract 不重解释。

因此 P2-7 是 **Worker-result/native-private-output authority**，不是 P2 aggregate、public
production admission 或 Node runtime activation。

## Worker wire 与 stop-and-wait result

P2-7 删除的唯一 terminal 是 P2-6 的 `P2_NODE_RESULT_NOT_ACTIVATED`。Worker 仍通过原有
`projectResultStart -> projectResultChunk* -> projectResultEnd` stop-and-wait 协议传输 JSON；每个
chunk 必须由 exact `(requestId, sequence)` ACK 推进。只有最后一个 result ACK 被接受后，Worker
才发布 `emit:completed`、清零 result bytes 并回到 idle；terminated operation 的 late ACK 仍无害。

Config-none result 的 exact keys 保持为：

```text
compilerApi, configKind, configSchemaRevision, diagnostics, emitSkipped,
options, outputs, profileRevision, profileSchemaRevision, targetProfile, tsVersion
```

每个 output 的 exact keys 保持为：

```text
path, sha256, sourcePaths, text
```

Worker 新增唯一 profile-aware emit mapping helper。P1/C6/Rhino 仍只允许 `.ts -> .js`；exact
Node revision 2 允许 `.ts -> .js`、`.mts -> .mjs`、`.cts -> .cjs`，并要求每个 module 与其
Source Map 都只关联同一个 reachable executable source。失败结果仍使用相同 11-key shape：
`emitSkipped=true`、`outputs=[]`，resolution failure 携带既有 typed `nodeResolution.policyCode`，
不引入 P2-only wire field。

## Native exact-snapshot result authority

`CompilerHostProtocol.parseFramedProjectResult()` 对 Node revision 2 要求 exact accepted snapshot；
缺 snapshot、profile split-brain 或缺 `acceptedProjectId` 均在 private output 前 fail closed。
解析 11-key result 与 4-key outputs 后，native 创建
`R2NodeRevisionTwoResultAuthority`，而不是信任 Worker 的 URI claim：

1. 从 snapshot file inventory 重建 `R2NodeResolutionV2SnapshotTargetInventory`；
2. 从 exact profile、可选 metadata/catalog/package-target 与 snapshot targets 重建
   `R2NodeResolutionV2Authority` 和 complete target-universe fingerprint；
3. 从每个 module 的唯一 `sourcePaths`、implied module format 与 nearest accepted package
   boundary 构造 `R2GeneratedModuleMappingRequest`；
4. 仅调用冻结的 `R2GeneratedModuleMappingPolicy.mapInventory()` 产生 canonical mapping；
5. 要求一个 mapping 精确对应一个 module 与一个 Source Map，并验证 path、extension、排序、
   exact collision 与 ASCII case-fold collision；
6. 将 authority 与 mappings 附到 `R2ProjectCompilation`；随后
   `bindToExactSnapshot()` 再次 `requireExact()`，阻止 A snapshot result 绑定到 B snapshot。

Native authority 绑定 module-output policy revision/fingerprint、generated-mapping
schema/revision、resolver revision、snapshot target fingerprint、可空 metadata/format/target
fingerprints、target-universe fingerprint、accepted entry/root inventory 与逐模块 mappings。
这些值全部来自 exact snapshot、冻结 policy 与 Worker outputs 的独立复算。

## Private output revision 3（pre-S2 historical）

`R2PrivateOutputArchive` 对 exact Node revision 2 再次绑定 accepted snapshot 与
`R2NodeRevisionTwoResultAuthority`，并使用 mapping 决定 entry module、module/Source Map inventory
以及 manifest evidence。`entrySourceUri` 必须等于同一 mapping policy 从
`(projectId, entryPath)` 计算出的 original-source URI。

在该 P2-7 历史切片中，Node revision-2 private manifest 使用 provisional revision `3`，additive
记录：

- module-output policy schema/revision/fingerprint；
- generated-mapping schema/revision；
- resolver revision、snapshot target、metadata、format catalog、package-target 与
  target-universe fingerprints；
- exact result snapshot entry/root inventory；
- sorted `generatedModuleMappings`，每项固定为 10-field evidence：`sourcePath`、
  `projectNamespace`、`originalSourceUri`、`moduleFormat`、`outputExtension`、`generatedPath`、
  `sourceMapPath`、`generatedUri`、`canonicalEvidence`、`fingerprint`。

Resolution diagnostics 可关联 exact Node revision-2 `.ts/.mts/.cts/.d.mts/.d.cts` snapshot/granted
target；blocking diagnostic 必须 `emitSkipped=true` 且无 output。P1/C6 的 provisional manifest
revision `2`、`.ts/.js` output rule 与 controlled diagnostic contract 保持不变。

## 门禁与当前证据

直接 Worker/TypeScript gate：

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

稳定输出为：

```text
R2-M2-P2-7 Worker framed results verified: exact starts=20/30/37, exact result=11 keys, outputs=4 keys, stop-and-wait ACK/sha256, ts/mts/cts implied formats, relative/imports/exports/type-reference, ts/mts/cts->js/mjs/cjs+maps, stable result failures=2x2, clean P2 successor=1, legacy 14/27 identity unchanged, P1 regression=1, P2 framed results=7, default resolver/ts.sys/network=0
```

Gradle 分层入口为：

```text
:app:verifyR2M2P2P7NodeNextResultDesktop
:app:verifyR2M2P2P7NodeNextResultAndPrivateOutput
:app:verifyR2ProjectCompilerCore
```

主部署流程在检测 wrapper、worker 与 active daemon 并取得连续两次空闲采样后，以
`--no-daemon --no-parallel --max-workers=2` 运行完整 Core。最终结果为 509 actionable tasks
（112 executed、397 up-to-date），`BUILD SUCCESSFUL in 2m 20s`；P2-7 aggregate、JVM 单测、
AndroidTest 编译、Release/APK、public/source 与 API freeze 均通过。Direct P2-7 Node gate、
`node --check`、diff whitespace gate 与 C6 real Program Node regression 也已通过。完整 provisional
Rhino desktop harness 的 functional/heap 阶段通过；先前单独探索时出现的三基线自然时序
calibration 宿主调度交集不足没有在最终 Core 中复现。全程未运行 ADB，也未触碰设备。

## P2-8 后仍未开放的边界

- `R2-M2-P2` 已为 `[x]`，M2 为 `12/18`；
- exact Node revision 2 只开放到 Worker result/native private archive，不进入 end-to-end admitted
  authority；
- 四项 public capability 继续为 `false`，public/external Node compilation 继续拒绝；
- Node Runtime dependency、R3 preparation 与 runtime execution 均不属于本切片；
- P1 Node revision 1、C6 resolver v1、legacy `14/27`、exact `20/30/37` ingress 和 frozen
  v1 API/AIDL 不修改。

P2-8 已聚合 P1、C6 v1、P2-0 至 P2-7、APK/API/public/source fences 与最终 regression
inventory，见 [P2-8 Final Aggregate](NODE_MODULE_OUTPUT_FINAL_AGGREGATE_R2_M2_P2_8.md)。
`R2-M2-P3` 随后已完成 [oracle-only compiler-bundle golden](COMPILER_BUNDLE_GOLDEN_R2_M2_P3.md)，
且未改变 production source kinds、public/end-to-end admission 或 runtime engine；P4/P5 也已
分别完成 [syntax fixture matrix](SYNTAX_FIXTURE_MATRIX_R2_M2_P4.md) 与
[compiler-boundary fixture matrix](COMPILER_BOUNDARY_FIXTURE_MATRIX_R2_M2_P5.md)，X1 也已完成
[public-shaped deterministic bundle](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。X1
不改变 Public promotion；实时进度与下一活动项以 [R2 Roadmap](../roadmap/R2.md) 为准。
