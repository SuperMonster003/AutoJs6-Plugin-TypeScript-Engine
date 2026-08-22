# R2-M2-P2-4 Node Source-kind Admission

> **R2-M3-S1 supersession（2026-08-14）。** 本页中的 input manifest revisions `2/6`、
> 14/27-field 与 20/30/37-field start 都是 pre-S1 receipt/provenance。当前 formal input archive
> 只接受 revision 1；对应 Worker exact counts 已变为 24/37 与 30/40/47，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。source-kind policy 与历史 receipt 仍保留。

日期：2026-08-02

状态：**P2-4 已实现 exact Node revision 2 native archive/snapshot admission；后续 P2-5 pure
resolver authority、P2-6 production Worker/NodeNext Program/emit 与 P2-7 result/private output
均已完成。P2-8 final aggregate 已完成，P2 已关闭，M2 为 `12/18`。**

## 本切片关闭的边界

P2-4 只把 P2-0 已冻结的 Node source-kind matrix 接入 native input authority：

| Profile authority | `/sources/*.ts` | `/sources/*.mts` | `/sources/*.cts` |
| --- | --- | --- | --- |
| Rhino revision 1 | executable | reject | reject |
| Node revision 1 | executable | reject | reject |
| exact Node revision 2 | executable | executable | executable |

只有小写 extension 被接受。`.MTS/.CTS`、mixed case、`.d.mts/.d.cts`、新 declaration
kind、`node_modules`、未显式允许的 `package.json` 与 sources root 外路径继续 fail closed。
每个 executable source 都必须进入完整、唯一、ASCII case-fold 无碰撞且排序的 root inventory；
`entryPath` 必须存在并属于 roots。

P2-4 本身不是 NodeNext Program activation：在该切片关闭时，真实 `.mts/.cts` snapshot 仍在
native framer 创建首帧前以稳定 P2-6 fence 拒绝。后续 P2-6 已用独立 20/30/37-field identity
接入这些 ordinary source kinds；P2-3B 的旧 14/27-field transport-only terminal 与 P1 clean
successor 仍保持不变。

## Authority layering

P2-4 新增 `R2ProjectSourceKindAdmission`，作为 P2 profile authority 对冻结 C4 escape policy
的窄 overlay：

- `R2CompilerEscapePolicy` schema/revision/canonical input/fingerprint 不修改；
- raw C4 classifier 继续把 `.mts/.cts` 标为 `deferred-source-kind`；
- overlay 只有在完整 profile authority 等于本地注册的 Node revision 2，路径位于
  `sources/` 或 `/sources/`，extension 为 exact lowercase executable `.mts/.cts`，且不是
  declaration 时，才解除这一种 rejection；
- package metadata 与 dependency-tree rejection 永不解除；
- `.ts` 行为仍由三个既有 profile 共享。

因此不能通过 profile-only lookup、伪造 revision、漂移 runtime fingerprint 或改变大小写取得
新 source kind。P2-0 module-output policy 与 P2-1 runtime capabilities fingerprint 已经绑定
`.ts,.mts,.cts`，P2-4 不增加 policy/schema revision。

## Native parity

同一 classifier 在以下边界复用并独立重验：

1. request manifest 的 `entryPath`、`rootPaths`、`files` 与 derived exact roots；
2. heap archive adapter 与 `R2ProjectSnapshot` 的 path、root completeness、entry identity；
3. actual ZIP spool 与 `R2FileBackedProjectSnapshot.seal`；
4. file-backed snapshot constructor 的 records、roots 与 entry revalidation。

Node revision 2 无 metadata 仍使用 manifest revision 2；metadata-present 仍使用 revision 6。
两者均继续复核 P2-2 `autojs://source/...` original-source URI。未声明但实际出现的 Node revision 2
`.mts/.cts` 已经是合法 source kind，因此按 exact inventory mismatch 拒绝；相同路径在 Rhino
或 Node revision 1 仍先命中 archive-path fence。

## 后续闭环状态

- **已完成 P2-5**：package-target `.d.mts/.d.cts` exact kind、resolver v2 substitution、ordinary
  snapshot 加 optional package-target 的 complete target-universe、metadata absent/present identity、
  module implied-format 与 type-reference `types` mode，以及 C6 v1 unchanged rejection parity；见
  [P2-5 Package-target / Resolution v2](NODE_PACKAGE_TARGET_RESOLUTION_V2_R2_M2_P2_5.md)。这不开放
  ordinary archive/snapshot 的新 declaration kind，也不激活 production；
- **已完成 P2-6**：Worker `.mts/.cts` ingress、`impliedNodeFormat`、NodeNext Program、resolver
  hooks 与 emit mapping；见
  [P2-6 NodeNext Program Integration](NODE_NEXT_PROGRAM_INTEGRATION_R2_M2_P2_6.md)；
- **已完成 P2-7**：Worker exact 11-key framed result、native exact-snapshot generated URI/mapping
  复算与 private manifest revision 3；见
  [P2-7 NodeNext Result and Private Output](NODE_NEXT_RESULT_PRIVATE_OUTPUT_R2_M2_P2_7.md)；
- 完整 P1/C6/v2/APK/public/source aggregate：P2-8 已完成。

四项 public capability 继续为 `false`，Node runtime 仍不是 compiler dependency。

## Gates

可复现入口：

```text
:app:testDebugUnitTest --tests io.github.supermonster003.autojs6.plugin.typescript.R2ProjectSourceKindAdmissionTest
:app:testDebugUnitTest --tests io.github.supermonster003.autojs6.plugin.typescript.R2ProjectSnapshotTest
:app:testDebugUnitTest --tests io.github.supermonster003.autojs6.plugin.typescript.R2FileBackedProjectSnapshotTest
:app:testDebugUnitTest --tests io.github.supermonster003.autojs6.plugin.typescript.R2ProjectArchiveTest
:app:testDebugUnitTest --tests io.github.supermonster003.autojs6.plugin.typescript.R2ProjectArchiveLoaderTest
:app:testDebugUnitTest --tests io.github.supermonster003.autojs6.plugin.typescript.R2ProjectFramedTransportTest
:app:verifyR2M2P2P4NodeSourceKindAdmission
```

Focused tests 覆盖三 profile exact matrix、C4 identity unchanged、heap metadata absent/present、
file-backed seal/constructor、manifest revision 2/6、actual ZIP、root/entry/URI/inventory negative，
以及 P2-4 关闭时真实 accepted Node revision 2 `.mts/.cts` snapshot 在 framer 首帧前仍被 P2-6
fence 拒绝的历史边界；后续 P2-6 gate 已替代该 framer fence。

## 2026-08-02 验证结果

- 六个 focused JVM suites 合计 `147/147`：source-kind admission `5/5`、heap snapshot
  `32/32`、file-backed snapshot `14/14`、archive `39/39`、archive loader `25/25`、
  framer `32/32`；
- 完整 Debug JVM XML 为 35 个 suites、`372/372` tests，`failures=0`、`errors=0`、
  `skipped=0`；
- archive 与 snapshot 两轮独立审计均为零 High/Medium。唯一 non-blocking Low 是 internal
  file-backed constructor 不重复执行 duplicate/case-collision/count/total 全量检查；production
  唯一构造入口 `seal` 已先完成这些验证；
- 加固后的 `verifyR2M2P2P3bNodePackageFormatTransport` 为 282 个 actionable tasks
  （50 executed、232 up-to-date）；
- `verifyR2M2P2P4NodeSourceKindAdmission` 为 283 个 actionable tasks
  （48 executed、235 up-to-date）。

P3b 自然 phase timing gate 使用 request-start absolute deadline，并要求三条 baseline 交集在固定
deadline 两侧各保留 100 ms。IDEA 同时运行其他大内存 Gradle build 时可能产生宿主换页/调度假阴性；
本轮未降低 margin、未加入 retry/样本筛选，也未修改 Worker 语义。失败消息现会打印三组 endpoint
与交集宽度。可复现 aggregate 前应等待其他 Gradle build 结束，停止当前工程 stale daemon，并使用
single-use daemon；这只控制验证环境，不改变仓库默认构建配置。

## 后续状态

P2-5 已建立独立 pure resolver/package-target v2、完整 target-universe 和 type-reference authority，
并保持 C6 resolver v1 对 `.mts/.cts/.mjs/.cjs/.d.mts/.d.cts` 的 unchanged rejection。P2-6 已复用
该 authority 接入 production Worker 的 Node revision-2 source-kind ingress、真实 NodeNext Program、
`impliedNodeFormat`、resolver grant 与 emit mapping，见
[P2-6 NodeNext Program Integration](NODE_NEXT_PROGRAM_INTEGRATION_R2_M2_P2_6.md)。P2-7 已接通
Worker framed result、native mapping authority 与 private output，见
[P2-7 NodeNext Result and Private Output](NODE_NEXT_RESULT_PRIVATE_OUTPUT_R2_M2_P2_7.md)。后续
P2-8 final aggregate 已判定通过并关闭 P2，见
[P2-8 Final Aggregate](NODE_MODULE_OUTPUT_FINAL_AGGREGATE_R2_M2_P2_8.md)。`R2-M2-P3` 随后已完成
[oracle-only compiler-bundle golden](COMPILER_BUNDLE_GOLDEN_R2_M2_P3.md)，P4 随后已完成
[syntax fixture matrix](SYNTAX_FIXTURE_MATRIX_R2_M2_P4.md)，P5 也已完成
[compiler-boundary fixture matrix](COMPILER_BOUNDARY_FIXTURE_MATRIX_R2_M2_P5.md)，X1 也已完成
[public-shaped deterministic bundle](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。X1
只开放 private Rhino/Node revision-1 end-to-end authority；Public/external/runtime 与 Node
revision-2 end-to-end 继续关闭。实时进度以 [R2 Roadmap](../roadmap/R2.md) 为准。
