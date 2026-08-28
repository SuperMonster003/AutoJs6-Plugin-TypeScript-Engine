# R2-M2-P2 Node Module-kind and Output Policy Draft

> **R2-M3-S1 supersession（2026-08-14）。** 本页中的 input manifest revisions `2/6/7`、
> 14/27-field 与 20/30/37-field start 都是 pre-S1 receipt/provenance。当前 formal input archive
> 只接受 revision 1；对应 Worker exact counts 已变为 24/37 与 30/40/47，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。module/output policy 与历史 receipt 仍保留。

日期：2026-08-02

状态：**P2-0 exact policy、P2-1 exact revision registry/control-plane、P2-2 pure
generated mapping authority、P2-3A native input authority、P2-3B transport-only authority、
P2-4 native source-kind admission、P2-5 complete target-universe/type-reference resolver
authority、P2-6 production Worker/Program/emit 与 P2-7 result/private output authority 已完成；
P2-8 final aggregate 也已完成，`R2-M2-P2` 已关闭，M2 为 `12/18`；
本文不是 public schema。**

## 目的

P2 为 Rhino 与 provisional NodeNext profile 冻结 source extension、module kind、output extension、
resolver substitution 与 generated URI 的单一决策边界。它解决 P1 有意保留的空白：NodeNext
不能仅凭 `module=199` 推断 `.ts/.mts/.cts` 的逐文件格式，也不能让 Worker、native output
packager 和 diagnostic URI 各自实现一套 extension 替换。

本文冻结设计并记录已落地的 authority foundation。Package-format transport、真实 TypeScript
Program parity 与 native result/private output 已分别由 P2-3B、P2-6、P2-7 完成；P2-8 又聚合
P1、C6 v1、全部 v2、APK/API/source/public 与 final regression gates，因此 P2 checklist 已完成。
基础证据见
[P2 Authority Foundation](NODE_MODULE_OUTPUT_AUTHORITY_FOUNDATION_R2_M2_P2.md)，production
Program 证据见 [P2-6 NodeNext Program Integration](NODE_NEXT_PROGRAM_INTEGRATION_R2_M2_P2_6.md)，
result/output 证据见
[P2-7 NodeNext Result and Private Output](NODE_NEXT_RESULT_PRIVATE_OUTPUT_R2_M2_P2_7.md)，最终聚合见
[P2-8 Final Aggregate](NODE_MODULE_OUTPUT_FINAL_AGGREGATE_R2_M2_P2_8.md)。

## Module-kind/output 矩阵

唯一候选矩阵为：

| Profile | Source | Module kind | Generated output |
| --- | --- | --- | --- |
| Rhino | `.ts` | CommonJS | `.js` |
| Node | `.mts` | ESM | `.mjs` |
| Node | `.cts` | CommonJS | `.cjs` |
| Node | `.ts`, nearest accepted package `type=module` | ESM | `.js` |
| Node | `.ts`, nearest accepted package `type=commonjs` 或无 `type` | CommonJS | `.js` |

Rhino profile 不接受 `.mts/.cts`。Node `.mts/.cts` 的 module kind 由 extension 决定，不允许被
package `type` 反转；Node `.ts` 的 module kind 才由 nearest、已接受并已绑定 snapshot identity 的
package metadata 决定。无 accepted package boundary 或 `type` 缺省时使用 CommonJS。

所有 source-to-output 映射必须保持 canonical source basename 与目录 pairing，只替换上表定义的
最终 extension。不得把 `.ts` ESM 输出改名为 `.mjs`，也不得把 `.mts/.cts` 都退化为 `.js`。

## `impliedNodeFormat` authority

Node profile 创建 Program 前，Worker 必须从受控 package metadata inventory 推导每个 source 的
nearest accepted package boundary，并据此设置或验证 TypeScript `SourceFile.impliedNodeFormat`：

- `.mts`：ESM；
- `.cts`：CommonJS；
- `.ts`：按上述 accepted package `type` 决定；缺省为 CommonJS。

authority 只能来自 archive/snapshot 中已经过 C5 identity、size/hash、strict JSON 与 lifecycle
policy 验证的 metadata。Worker 不得读取宿主 filesystem、环境 package.json、网络或 raw
unvalidated package bytes；native 层也不能让 caller 直接提交 `impliedNodeFormat` 覆盖值。

Program source inventory、TypeScript 实际 `sourceFile.impliedNodeFormat`、emit extension 与 result
manifest 必须逐文件一致。任一缺失、额外、重复、case-fold collision 或 module/output mismatch
都应在 artifact commit 前 fail closed。

P2-0 已冻结 wire module-format 只允许 `commonjs` 与 `esm`；它们在 Worker 内部分别映射
TypeScript `ModuleKind.CommonJS=1` 与 `ModuleKind.ESNext=99`，numeric enum 或
`CommonJS`/`ESNext` 名称不能直接上 wire。package `type` exact policy 为：无 accepted boundary
或 boundary object 缺少 `type` 时使用 CommonJS；精确 string `module` 使用 ESM；精确 string
`commonjs` 使用 CommonJS；其他 string 与所有非 string 值均以
`NODE_PACKAGE_TYPE_INVALID` fail closed。该规则有意比 TypeScript 对非法值静默回落 CommonJS
更严格。

nearest boundary 只来自 C5 已接受的 project-root `package.json` 和 explicit dependency roots
内显式 descriptor。`package.json` 映射 `/`，其余 descriptor 映射去掉 `/package.json` 的 VFS
目录；source 必须是 boundary 子树成员，选择 segment 最长者。不得从 filesystem 隐式发现
metadata，不得越过 explicit dependency root。T1-6 已让宿主把受控 `packages/*` workspace
metadata 放入该 inventory，并在查询时把 ordinary `/sources/packages/**` 投影回项目 package
命名空间；因此 resolver grant 与用户 workspace 源码现在共享同一 longest-boundary authority。
完整激活记录见 [T1-6](NESTED_PACKAGE_BOUNDARIES_T1_6.md)。

## Resolver v2

P2 新增独立 resolver policy revision `v2`，而不是原地扩展 C6 v1。v2 必须认识 TypeScript
NodeNext extension kinds，并至少冻结下列 deterministic substitutions：

```text
runtime/package target .js  -> source .ts
runtime/package target .mjs -> source .mts
runtime/package target .cjs -> source .cts
declaration target .d.ts / .d.mts / .d.cts -> exact matching declaration kind
```

substitution 仍必须精确命中 snapshot inventory，并保留 C6 的 package-boundary、exports/imports、
condition order、case-fold、target escape、cycle、no filesystem/network/default-resolver fallback
等全部 fence。v2 的 condition mode 必须与 containing source 的 implied module format 一致，不能
仅由 specifier 拼写或 emit extension 猜测。

P2-0 policy 已把 resolver revision 精确冻结为 `2`，substitution inventory 为
`.js -> .ts`、`.mjs -> .mts`、`.cjs -> .cts`，以及 `.d.ts`、`.d.mts`、`.d.cts` declaration
kind 的 exact self-kind matching；condition authority 固定为 `implied-module-format`。

C6 resolver v1 是 frozen regression authority：它继续只支持既有 `.js -> .ts -> .d.ts`
语义，并继续拒绝 `.mts/.cts/.mjs/.cjs/.d.mts/.d.cts` 新 source/declaration kinds。P2 gate 必须
同时证明 v2 正例与 v1 rejection，禁止通过修改旧 golden 让历史 gate “自然通过”。

## Generated URI 单一 authority

P2 必须引入一个纯、deterministic、revisioned helper，作为 source path、generated output path 与
generated URI 的唯一 authority。Worker emit collection、native result parser、diagnostic/source-map
关联、private output archive 和 manifest writer 都只能消费该 helper 的 canonical mapping，不能
各自执行字符串后缀替换。

helper 输入至少绑定：

- exact profile authority 与 `profileRevision`；
- canonical source path；
- accepted source extension；
- resolved/implied module format；
- accepted package metadata identity（仅 Node `.ts` 需要）。

helper 输出固定为 canonical generated path、generated URI 与 expected output extension。它必须
拒绝 unknown extension、non-canonical/traversal path、目录或 basename 漂移、重复 generated
target、case-fold collision 及 matrix 不一致。URI escaping、separator 与 root prefix 也必须在
helper 中一次冻结，并由 native/Worker parity golden 覆盖。

该 helper 与 URI grammar 已在 P2-2 作为不参与 admission 的纯 authority 落地。P2-6 的 production
Worker Program/emit 已绑定同一 policy identity 并验证 `.js/.mjs/.cjs` extension/path matrix；P2-7
又让 native result parser、private output archive 与 manifest 从 exact snapshot/outputs 独立复算
并消费 generated mapping evidence。该闭环仍是 private/internal authority，不等于 public/end-to-end
admission。P1 的 `.ts -> .js` 仍只代表 revision 1 的窄边界。

P2-0 冻结的 URI grammar 为：

```text
project-namespace = "p-" + lowercase-sha256(strict-utf8(projectId))
original URI      = autojs://source/<project-namespace>/<encoded-source-path>
generated URI     = autojs://generated/<project-namespace>/<encoded-output-path>
```

source/output path 分别为 canonical `/sources/**` 与 `/outputs/**`，URI path 去掉首个 `/` 但保留
`sources`/`outputs` root。每个 segment 按 UTF-8 RFC 3986 编码，仅 unreserved
`A-Z a-z 0-9 - . _ ~` 原样保留，percent hex 必须大写，`/` 只能作为 delimiter。scheme 与
authority 必须逐字小写；userinfo、port、query、fragment、encoded slash、escaped unreserved、
非最短 UTF-8、decode/re-encode 不相等全部拒绝。relative path 上限 1024 printable-ASCII chars、
64 segments，URI 为 ASCII 且不超过 4096 bytes。source/generated exact duplicate、ASCII
case-fold collision、不同 project identity 的 namespace collision 均 fail closed。

Node revision 2 的 supplied entry source URI 必须等于 helper 对 entry path 计算的 original URI；
revision 1 历史 URI 不回溯重解释。original/generated URI 共享 project namespace 与相对
directory/basename，只允许 authority、root 和最终 extension 按 matrix 变化。P2-2 已实现该纯
helper、Kotlin/JavaScript parity 与 shared fixture；P2-6 已接入 native Program framing，并让 Worker
emit 验证同一 extension/path matrix，P2-7 已完成 native result/output 对 helper evidence 的
exact-snapshot 复算。

## Revision 与兼容规则

P2 扩大 Node source/output 语义时，Node `profileRevision` **必须从 `1` 提升到 `2`**。新的 runtime
capabilities canonical input、fingerprint、request/snapshot/frame/result/output authority 与测试
golden 必须同步更新；revision 1 Node request 仍只能表示 P1 的 config-none、`.ts -> .js`
debug-only boundary。

Rhino revision、controlled tsconfig authority、C6 resolver v1、frozen AIDL/API 与 C8 declaration
distribution 不因 P2 自动改版。若 P2 实现确实要求其他 schema/revision 变化，必须单独列出并
提供兼容/拒绝矩阵，不能借 Node profile revision 隐式重解释它们。

四项 public capability 在 P2 internal implementation 与 gate 阶段继续为 `false`。Node Runtime
仍不是 compiler dependency；是否以及何时开放 production/public Node compilation 属于后续
milestone，不由本草案决定。

P2-0 冻结 revision matrix：Rhino revision 1 保持历史 `.ts`，所有 Rhino unknown revision 与
`.mts/.cts` 拒绝；Node revision 1 继续只接受 DEBUG、config-none、无 package authority 的
`.ts -> .js`；Node revision 2 仅接受 DEBUG、config-none 的 `.ts/.mts/.cts` 与 exact P2 policy
identity。revision 1/2 的 runtime fingerprint、snapshot、frame、result 与 output 不能交叉；
missing/unknown revision 拒绝。public/external Node revision 1/2 均拒绝。registry parser 必须按
`(profile, profileRevision)` exact lookup，不能用 profile-only lookup 隐式选择 latest。C6 resolver
v1 保持 `unchanged-reject-new-kinds`，不能接受或重解释 v2 target/source kinds。

## 当前 implementation blocker 与实施切片

上述 semantic blocker 已由 `R2NodeModuleOutputPolicy`、checked-in
`r2-node-module-output/policy-v1.json` 与 JVM exactness tests 在 P2-0 冻结；包括 Node revision
1/2 并存拒绝矩阵、hashed project namespace URI grammar、strict package `type`、C5 boundary
scope、wire module formats 与 resolver-v2 semantics。P2-1 已增加 exact tuple registry、独立
Node revision-2 runtime authority 与 debug control-plane type/presence fence；P2-2 已增加绑定真实
C5 inventory instance identity 的 Kotlin/JavaScript pure generated mapping parity；P2-3A/P2-3B 已
完成 package-format snapshot identity 与 Worker transport-only 接线；P2-4 已把 exact Node
revision 2 `.mts/.cts` 接入 archive/heap/file-backed snapshot；P2-5 已冻结 package-target v2、
ordinary snapshot 加 optional package-target 的 complete target-universe、metadata absent/present
identity、module implied-format condition 与 type-reference `types` mode，并保持 C6 resolver v1
拒绝新 kinds。P2-6 又增加 manifest revision 7 与独立 v2 package-target inventory，将 exact
`20/30/37` native producer、Worker source-kind ingress、真实 TypeScript 6.0.3 NodeNext
Program/resolver/emit 接线，同时冻结 legacy `14/27` transport terminal。P2 仍不能仅通过“把 Node
revision 改成 2 并放开后缀”关闭。P2-7 已保持 11-key/4-key Worker wire，打开 stop-and-wait
framed result，并让 native/private output 从 exact snapshot 复算同一 mapping authority；P2-8
已完成最终 aggregate，但没有改变 public/end-to-end 非激活决策。
按以下不改变 M2 固定分母的内部切片推进：

1. `P2-0`：**已完成**，冻结上述语义、revision 1/2 拒绝矩阵与纯 policy fixture/JVM gate；
2. `P2-1`：**已完成**，Node revision 1/2 authority 可按 exact tuple 查找，并保留 P1 revision 1
   production/Worker-result admission 与全量 regression；
3. `P2-2`：**已完成**，实现 native/JavaScript parity 的纯 generated module mapping helper；
   P2-6 已由 Worker/emit 消费，P2-7 又由 native result/output 复算并消费；
4. `P2-3`：为 Node revision 2 config-none 增加 exact package-format metadata authority：
   - `P2-3A`：**已完成**，pure catalog、manifest revision 6、heap/file-backed snapshot identity
     已冻结；其历史 pre-Worker fence 已由 P2-3B transport-only authority 替换，见
     [P2-3A Package-Format Authority](NODE_PACKAGE_FORMAT_AUTHORITY_R2_M2_P2_3A.md)；
   - `P2-3B`：**已完成**，增加 exact 14/27-field frame 与 Worker raw-byte 独立重建 catalog
     的 transport-only authority；完整输入在 end ACK/Program 前以稳定 fence 结束，见
     [P2-3B Package-Format Transport](NODE_PACKAGE_FORMAT_TRANSPORT_R2_M2_P2_3B.md)；
5. `P2-4`：**已完成**，仅对 exact Node revision 2 开放 profile-aware `.mts/.cts`
   archive/heap/file-backed snapshot admission；P2-6 已完成 framer/Worker Program handoff，见
   [P2-4 Source-kind Admission](NODE_SOURCE_KIND_ADMISSION_R2_M2_P2_4.md)；
6. `P2-5`：**已完成**，冻结 resolver/package-target v2、完整 snapshot/package target-universe、
   metadata absent/present authority、module/type-reference condition mode 与 C6 v1 rejection parity，
   并由 P2-6 接入 production Worker，见
   [P2-5 Package-target / Resolution v2](NODE_PACKAGE_TARGET_RESOLUTION_V2_R2_M2_P2_5.md)；
7. `P2-6`：**已完成**，接入 manifest revision 7、exact `20/30/37` producer、production Worker
   的真实 NodeNext Program、`impliedNodeFormat`、resolver hooks 与 `.js/.mjs/.cjs` emit mapping，
   并在当时的 result frame 前以 `P2_NODE_RESULT_NOT_ACTIVATED` fail closed；该历史 fence 已由
   P2-7 替代，见
   [P2-6 NodeNext Program Integration](NODE_NEXT_PROGRAM_INTEGRATION_R2_M2_P2_6.md)；
8. `P2-7`：**已完成**，Worker 返回既有 exact framed result；native result/private archive 从
   exact snapshot 与 outputs 复算同一 mapping，并携带逐模块 authority evidence，见
   [P2-7 NodeNext Result and Private Output](NODE_NEXT_RESULT_PRIVATE_OUTPUT_R2_M2_P2_7.md)；
9. `P2-8`：**已完成**，聚合 P1、C6 v1、所有 v2 gates、APK/API/public/source fences 与
   final regression inventory 后关闭 P2，见
   [P2-8 Final Aggregate](NODE_MODULE_OUTPUT_FINAL_AGGREGATE_R2_M2_P2_8.md)。

`P2-2` 的 pure mapping 与 `P2-5` 的 pure resolver authority 均已独立冻结，P2-6 已完成
archive/snapshot/framing 到 Worker resolver/emit 的串行接线，P2-7 已让 native result/output
消费并复算同一 authority，没有另行实现 extension 或 URI 决策。

## 完成验收

P2 已以以下证据关闭：

- 上表五条 matrix 的真实 TypeScript NodeNext Program/emit parity（P2-6 已完成）；
- `.mts/.cts/.ts` 的 `sourceFile.impliedNodeFormat` 与受控 package boundary 正反例（P2-6 已完成）；
- production Worker 对已冻结 complete target-universe、module/type-reference condition、extension
  substitution 与 snapshot-only fence 的直接复用 parity（P2-6 已完成）；
- 单一 generated URI helper 的 native/Worker pure parity、collision/traversal/mismatch mutation 已
  冻结；native result/output 已由 P2-7 对同一 mapping 完成 exact-snapshot 复算；
- result parser、private archive revision 3、manifest 与 diagnostics/source-map association 使用
  同一 mapping（P2-7 已完成）；
- Node profile revision 1/2 明确拒绝矩阵，public capability=false 与完整 compiler-core 回归。

**当前结果：P2-0 至 P2-2 authority foundation、P2-3A native input authority、P2-3B
package-format frame/Worker transport-only authority、P2-4 native source-kind admission，以及
P2-5 complete target-universe/type-reference resolver authority、P2-6 production
Worker/Program/emit authority、P2-7 Worker result/native private-output authority 已完成；P2 总项
已由 P2-8 final aggregate 关闭；P2 关闭时 M2 为 `12/18`。`R2-M2-P3` oracle-only
compiler-bundle aggregate、P4 syntax fixture matrix、P5 compiler-boundary fixture matrix 与 X1
public-shaped deterministic bundle 随后均已通过。见
[P3 Compiler Bundle Golden](COMPILER_BUNDLE_GOLDEN_R2_M2_P3.md) 与
[P4 Syntax Fixture Matrix](SYNTAX_FIXTURE_MATRIX_R2_M2_P4.md)、
[P5 Compiler Boundary Fixture Matrix](COMPILER_BOUNDARY_FIXTURE_MATRIX_R2_M2_P5.md) 与
[X1 Public-shaped Deterministic Bundle](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。X1
只开放 private Rhino/Node revision-1 end-to-end authority；Public/external/runtime、Node
revision-2 end-to-end 与 Node Runtime dependency 继续关闭。实时进度以
[R2 Roadmap](../roadmap/R2.md) 为准。**
