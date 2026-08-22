# R2-M2-P2 Node Module/Output Authority Foundation

> **R2-M3-S1 supersession（2026-08-14）。** 本页中的 input manifest revisions `2/6/7`、
> 14/27-field 与 20/30/37-field start 都是 pre-S1 receipt/provenance。当前 formal input archive
> 只接受 revision 1；对应 Worker exact counts 已变为 24/37 与 30/40/47，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。module/output authority 与历史 receipt 仍保留。

日期：2026-08-02

状态：**P2-0、P2-1、P2-2 已完成；后续 P2-3A/P2-3B、P2-4、P2-5 complete
target-universe/type-reference resolver authority、P2-6 production Worker/Program/emit 与 P2-7
result/private output authority 也已完成。P2-8 final aggregate 已关闭 P2，M2 为 `12/18`；
public/end-to-end 与 Node Runtime dependency 仍关闭。**

## 已关闭边界

### P2-0 exact policy

- `R2NodeModuleOutputPolicy` 冻结 Rhino revision 1、Node revision 1/2、五条
  source/module/output matrix、strict package `type`、URI grammar 与 resolver-v2 语义；
- policy canonical input 绑定 schema/revision、source extension、wire module format、C5 policy
  fingerprint、path/URI budgets、collision mode、resolver substitutions 与 failure catalog；
- 当前 exact fingerprint 为
  `5c2294f0ee9d6d25bd2132b4ea24866d0a5f9caaa521a63bf81ffe1d897f6ce7`。

### P2-1 exact authority

- registry 以 `(profile, profileSchemaRevision, profileRevision)` exact tuple 查找，不按 profile
  隐式选择最新 revision；
- Rhino revision 1、Node revision 1 与 registered-only Node revision 2 各自拥有独立 runtime
  fingerprint；在 P2-1 关闭时 Node revision 2 尚未进入 end-to-end 或 Worker-result admission，
  P2-7 只增加了 Worker-result admission，end-to-end 仍关闭；
- private debug control-plane 以 key presence 选择私有平面，要求 `enabled` 为 exact Boolean
  `true`、revision 为 exact Int，不再回落 public metadata 或默认 revision；
- archive authority 构造时即拒绝 profile/runtime split-brain，P1 Node revision 1 production
  admission 保持不变。

### P2-2 pure generated mapping

- Kotlin authority 与独立 JavaScript mirror 共享 10 个正例、19 个拒绝例；
- project namespace 为 `p-` 加 strict-UTF-8 project ID 的 lowercase SHA-256；source/generated
  URI 使用 RFC 3986 segment encoding 与 uppercase percent hex；
- Rhino `.ts -> .js`、Node `.mts -> .mjs`、`.cts -> .cjs`、`.ts -> .js` matrix 精确；
- Node `.ts` 绑定真实 `R2PackageMetadataInventory.canonicalInput` 实例 fingerprint，而不是 C5
  policy fingerprint；no-boundary 使用 null identity；
- source/output/Source Map path budgets、exact/case-fold collision、mixed-project inventory、
  immutable inventory 与 10-field exact claim 均 fail closed；
- helper 本身继续是 pure authority；P2-6 的 production Worker Program/emit 已绑定同一 policy
  identity 并验证 identical extension/path matrix，P2-7 又让 native result parser 与 private
  output 从 exact snapshot/outputs 复算并绑定同一 generated mapping evidence。

## 可复现门禁

```text
:app:verifyR2M2P2GeneratedModuleMappingDesktop
:app:verifyR2M2P2AuthorityFoundation
```

2026-08-02 的验证将旧 Rhino desktop calibration 与其余聚合证据分开执行：

- `verifyR2ProjectCompilerDesktop` 独立通过；
- `verifyR2M2P2AuthorityFoundation -x verifyR2ProjectCompilerDesktop` 通过，覆盖 P1 aggregate、
  full Debug JVM unit tests、Android instrumentation compilation、P2 policy/registry/control-plane
  静态不变量与 Kotlin/JavaScript mapping parity；
- mapping shared oracle 结果为 `valid=10, invalid=19`。

这样记录是为了区分 P2 失败与旧校准任务的 100ms 自然时序波动；两部分都必须有成功证据，
但不把拆分执行描述为一次未拆分的成功。

P2-3A 落地后，未排除任何任务的
`verifyR2M2P2P3aNodePackageFormatAuthority` 又于同日完整通过，其中包含该旧 Rhino desktop
calibration。因此上述拆分记录只保留为首次 foundation 验证的可归因历史，不再是当前聚合证据
的限制。

P2-6 增加以下 production Program 门禁：

```text
:app:verifyR2M2P2P6NodeNextProgramDesktop
:app:verifyR2M2P2P6NodeNextProgramIntegration
:app:verifyR2ProjectCompilerCore
```

2026-08-10 的直接 Node gate 固定输出 exact `20/30/37` starts、`.ts/.mts/.cts`
`impliedNodeFormat`、relative/imports/exports/type-reference、`.js/.mjs/.cjs` 与 Source Map、
stable failures `2x2`、clean P2 successor、legacy `14/27`、P1 regression，并证明 P2
`projectResultStart=0`、default resolver/`ts.sys`/network=`0`。最终 Core 通过：508 actionable
tasks，112 executed，396 up-to-date，`BUILD SUCCESSFUL in 3m 25s`。详见
[P2-6 NodeNext Program Integration](NODE_NEXT_PROGRAM_INTEGRATION_R2_M2_P2_6.md)。

P2-7 保持 Worker wire 为 exact 11-key config-none result/4-key outputs，并新增以下分层入口：

```text
:app:verifyR2M2P2P7NodeNextResultDesktop
:app:verifyR2M2P2P7NodeNextResultAndPrivateOutput
:app:verifyR2ProjectCompilerCore
```

direct TypeScript 6.0.3 gate 已验证 7 个 P2 stop-and-wait framed results、stable failures `2x2`、
clean successor、legacy `14/27`、P1 与 C6 regression；native 从 exact snapshot 独立复算
10-field mappings，private manifest 对 Node revision 2 使用 additive revision 3。详见
[P2-7 NodeNext Result and Private Output](NODE_NEXT_RESULT_PRIVATE_OUTPUT_R2_M2_P2_7.md)。

## 明确未开放

- `R2-M2-P2` checklist 已由 P2-8 勾选，M2 为 `12/18`；
- `.mts/.cts` 已对 exact Node revision 2 开放 native archive/snapshot/framer、production Worker
  Program/emit/result 与 private output；legacy `14/27` transport identity 与 terminal 保持冻结；
- P2-6 的 `P2_NODE_RESULT_NOT_ACTIVATED` 历史 fence 已由 P2-7 替代；Node revision 2 仍是
  internal/Worker-result-only，不进入 end-to-end admission；
- P2-5 resolver v2 已绑定 complete ordinary/package target-universe、metadata absent/present
  authority、module implied-format 与 type-reference `types` mode，并在 P2-6 接入 production
  Worker；P2-7 native result/private output 已从 exact snapshot 复算同一 authority；
- 四项 public compiler capability 与 Node Runtime dependency 均保持 `false`。

后续 P2-3A 已建立 Node revision 2 config-none package-format catalog、schema-v1
`node-r2-metadata` 条件形状与
heap/file-backed identity，见
[P2-3A Package-Format Authority](NODE_PACKAGE_FORMAT_AUTHORITY_R2_M2_P2_3A.md)；P2-3B 已完成
exact 14/27-field frame、Worker raw-byte rebuild 与 pre-Program transport-only fence，见
[P2-3B Package-Format Transport](NODE_PACKAGE_FORMAT_TRANSPORT_R2_M2_P2_3B.md)；P2-4 已完成
exact Node revision-2 native source-kind admission，见
[P2-4 Source-kind Admission](NODE_SOURCE_KIND_ADMISSION_R2_M2_P2_4.md)；P2-5 已完成 pure
package-target/resolver v2、完整 target-universe 与 type-reference authority，见
[P2-5 Package-target / Resolution v2](NODE_PACKAGE_TARGET_RESOLUTION_V2_R2_M2_P2_5.md)；P2-6 已
完成 schema-v1 `node-r2-metadata-target-v2` 条件形状、exact `20/30/37` native producer与
production Worker/Program/emit
接线，见 [P2-6 NodeNext Program Integration](NODE_NEXT_PROGRAM_INTEGRATION_R2_M2_P2_6.md)；P2-7
已完成 Worker framed result、native exact-snapshot authority 与 private output revision 3，见
[P2-7 NodeNext Result and Private Output](NODE_NEXT_RESULT_PRIVATE_OUTPUT_R2_M2_P2_7.md)。随后
[P2-8 Final Aggregate](NODE_MODULE_OUTPUT_FINAL_AGGREGATE_R2_M2_P2_8.md) 完成聚合并关闭 P2。
`R2-M2-P3` 随后已完成 [oracle-only compiler-bundle golden](COMPILER_BUNDLE_GOLDEN_R2_M2_P3.md)，
且未改变 production admission 或创建 runtime engine；`R2-M2-P4` 又完成
[syntax fixture matrix](SYNTAX_FIXTURE_MATRIX_R2_M2_P4.md)，P5 也已完成
[compiler-boundary fixture matrix](COMPILER_BOUNDARY_FIXTURE_MATRIX_R2_M2_P5.md)，X1 也已完成
[public-shaped deterministic bundle](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。X1
只开放 private Rhino/Node revision-1 end-to-end authority；Public/external/runtime 与 Node
revision-2 end-to-end 仍关闭。实时进度以 [R2 Roadmap](../roadmap/R2.md) 为准。
