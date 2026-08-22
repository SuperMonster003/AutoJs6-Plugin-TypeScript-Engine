# R2-M2-P2-5 Node Package-target / Resolution v2

> **R2-M3-S1 supersession（2026-08-14）。** 本页中的 14/27-field 与 20/30/37-field start 是
> pre-S1 receipt/provenance，不是当前 accepted frame。Formal input revision 1 的对应 Worker exact
> counts 为 24/37 与 30/40/47，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。resolver-v2 policy 与历史 receipt 仍保留。

日期：2026-08-10

状态：**P2-5 pure package-target/resolver v2 authority 与 aggregate gate 已完成；后续 P2-6
production Worker/Program/archive/snapshot/frame 与 P2-7 result/private output 接线也已完成。
P2-8 final aggregate 已完成，P2 已关闭，M2 为 `12/18`。**

## 切片边界

P2-5 只冻结可独立验证的 resolver v2 决策，不扩大任何 production ingress：

- 新增 `R2NodePackageTargetPolicyV2`、独立 descriptor/inventory/authority；
- 新增 `R2NodeResolutionKernelV2`，并通过 C6 kernel 的独立 v2 target-index 入口复用已经冻结的
  exports/imports、condition property order、array/null、cycle、escape、case-fold 与 snapshot-only
  语义；
- 新增 `R2NodeResolutionV2SnapshotTargetInventory`，防御复制并指纹化完整 ordinary snapshot
  inventory；resolver 只消费由它与可选 package targets 合并出的唯一 target universe；
- 同时支持 metadata/format/targets 全空、metadata+format only、metadata+format+targets 三种 exact
  authority 状态，并将 module 与 type-reference resolution 分成受控入口；
- 新增 checked-in `policy-v2.json`、`parity-v2.json` 和 TypeScript 6.0.3 独立 oracle；
- 不修改 schema-v1 Rhino metadata + target-v1 条件形状、Node revision-2 14/27-field frame、Worker
  retention、compiler host 或 Program；
- Node revision 2 仍不在 end-to-end / Worker-result admitted authority set 中。

因此 P2-5 本切片建立的是 **pure authority**，不是 production resolver activation。该历史边界
已由后续 [P2-6 NodeNext Program Integration](NODE_NEXT_PROGRAM_INTEGRATION_R2_M2_P2_6.md) 以独立
20/30/37-field Program identity 消费；它不改变 P2-5 自身的完成口径。

## Package-target v2 authority

| 字段 | 冻结值 |
| --- | --- |
| schema / revision | `autojs6.typescript.node-package-target-policy` / `1` |
| policy revision | `2` |
| policy fingerprint | `175cc694dae3e925d66eac3f77f308c95dc0db7b939238a33022d27812578224` |
| inventory domain | `autojs6.typescript.node-package-target.inventory.v2` |
| exact profile | registered-only Node revision 2 |
| resolver condition source | `implied-module-format` |
| trusted custom conditions | `0`，已进入 policy canonical/fingerprint |
| executable kinds | `.ts`、`.mts`、`.cts` |
| declaration kinds | `.d.ts`、`.d.mts`、`.d.cts` |
| kind classifier | declaration-first、exact lowercase |
| metadata binding | exact C5 inventory fingerprint |
| budgets | 256 files；1024 path chars；32768 aggregate path chars；8 MiB file/total |

Declaration suffix 必须先于 executable suffix 判定，防止 `.d.mts/.d.cts` 因
`endsWith(".mts/.cts")` 被误分类为 executable。`.js/.mjs/.cjs` 是 package mapping 的 runtime
target spelling，不是 snapshot target kind，因此不得进入 v2 inventory；uppercase/mixed-case、
`node_modules`、`package.json`、遍历、root drift、排序/duplicate/case-fold collision 与 quota drift
继续 fail closed。

V2 package inventory canonical input 绑定每个 target 的 path、derived kind、size、SHA-256 与完整 C5
inventory fingerprint。V1/V2 使用不同 Kotlin 类型、policy fingerprint 与 inventory domain，不能
交叉构造或隐式升级。

Ordinary snapshot authority 则绑定 exact Node revision 2、完整 project file metadata
（path/UTF-8 bytes/UTF-16 units/SHA-256）及其 resolver kind。它保留 P2-4 的边界：ordinary
`/sources` 只开放 `.ts/.mts/.cts` executable 与既有 `.d.ts`，`/declarations` 仍只开放 `.d.ts`；
`.d.mts/.d.cts` 目前只可作为 `/packages` 下的 v2 package target。最终 target-universe fingerprint
同时绑定 ordinary fingerprint、可空 package-target fingerprint、排序后的 path/kind inventory，
ghost containing path、exact duplicate 与 ASCII case-fold collision 均 fail closed。

## Resolver v2 substitutions

P2-0 已冻结的 v2 substitution 是：

```text
.js  -> .ts
.mjs -> .mts
.cjs -> .cts

.d.ts  -> exact .d.ts
.d.mts -> exact .d.mts
.d.cts -> exact .d.cts
```

V2 有意不继承 C6 v1 的 `.js -> .ts -> .d.ts` fallback；同理 `.mjs/.cjs` 也不 fallback 到
`.d.mts/.d.cts`。`foo.d.js/foo.d.mjs/foo.d.cjs` 在执行通用 runtime-extension substitution 前被
明确隔离，不能偶然解析为 declaration。TypeScript 6.0.3 在部分上述输入上会继续探测 declaration，
P2-5 将其作为明确的 `fail-closed` parity case，而不是扩大已冻结的 P2-0 policy。

C6 v1 保持原样：`.js -> [.ts,.d.ts]` 继续是旧 authority；`.mts/.cts/.mjs/.cjs/.d.mts/.d.cts`
仍以原稳定 failure 拒绝。旧 `policy-v1.json` fingerprint
`e34f4825d8f75ba1b674849ff79c2be8f9c1b88fe940add86e6e466a139bbbaf` 与
`parity-v1.json` 的 12 supported / 6 fail-closed / 18-code catalog 均未改写。

## Implied-format condition authority

V2 module request 不提供 caller-controlled `import/require` mode，也不接受 custom conditions：

- `.mts/.d.mts` 固定 ESM / `import`；
- `.cts/.d.cts` 固定 CommonJS / `require`；
- `.ts/.d.ts` 只由 longest accepted P2 package-format boundary 决定；exact `type=module` 使用
  ESM，其余 accepted state 与 no-boundary 使用 CommonJS；
- package `type` 不能反转 `.mts/.cts`；
- module active condition 仍由 C6 kernel 追加受控 `types` 与 `node`；独立
  `resolveTypeReference()` 入口固定使用 `types` mode，即使 `require/import` property 排在
  `types` 前也不得选择 executable target；trusted custom condition count 固定为 `0`。

`R2NodeResolutionV2Authority` 必须绑定 exact Node revision-2 profile 与 ordinary snapshot
authority；package authority 使用以下闭合矩阵：metadata absent 时 format/targets 都必须 absent；
metadata present 时 format 必须 present，而 package targets 可 absent；targets present 时必须与
metadata 的 C5 fingerprint、dependency roots、metadata path/size/SHA-256 全量一致。partial/split-
brain 组合在 resolver 调用前拒绝。无 metadata 项目仍可解析 ordinary relative specifier，bare
package 稳定返回 `PACKAGE_NOT_ALLOWED`，`#imports` 稳定返回 `PACKAGE_METADATA_MISSING`。

每次 outcome 携带 profile/resolver revision、implied module format、effective condition mode、
ordinary snapshot fingerprint、可空 C5/format/package-target fingerprint 与最终 target-universe
fingerprint，并显式携带 package-target policy revision/fingerprint，已供 P2-6 Worker/Program
接线消费；P2-7 result/private output 又从 exact snapshot 独立重建并绑定同一 authority，无需
重新拼接未绑定 target list 或猜测 resolver 决策。
非法/ghost containing path 返回稳定 `NODE_RESOLUTION_INVALID_CONTAINING_PATH` outcome，不以未捕获
异常越过 18-code catalog。

## Independent parity

`parity-v2.json` 固定 TypeScript `6.0.3` 的 18 个案例：

- 14 supported：原 `.js/.mjs/.cjs` source substitution、三个 package declaration exact kind、
  module/missing package `type` condition 分支，加上无 metadata ordinary relative 三种 source
  family、root `#imports`、ordinary `.d.ts` 与 type-reference `types` hook；
- 4 fail-closed：`.js -> .d.ts`、`.mjs -> .d.mts`、`.cjs -> .d.cts` 与
  `.d.mjs -> .d.mts` 的 TypeScript declaration probing。

独立 Node oracle 只使用内存 `ModuleResolutionHost`，禁止 `ts.sys`、filesystem、network、Worker、
production resolver hook 与 `createIncrementalProgram`。稳定成功输出为：

```text
R2-M2-P2-5 TypeScript 6.0.3 resolver v2 parity verified: 14 supported, 4 fail-closed, implied-module-format+types-hook, complete-target-universe, ModuleResolutionHost-only, Program=0
```

## 门禁与验证

直接 parity：

```powershell
node app/src/test/scripts/verify-r2-node-resolution-v2-parity.js app/build/generated/typescriptCompilerAssets/assets/typescript/6.0.3/lib/typescript.js app/src/test/resources/r2-node-resolution/parity-v2.json 6.0.3
```

Gradle 分层入口：

```text
:app:verifyR2M2P2P5NodePackageTargetResolutionV2Desktop
:app:verifyR2M2P2P5NodePackageTargetResolutionV2
:app:verifyR2ProjectCompilerCore
```

2026-08-10 已完成的证据：

- 上述 Node parity 直接通过，`14/4`；
- focused JVM 两个测试类共 `21/21` 通过（package policy `6`、resolver v2 `15`）；
- `verifyR2M2P2P5NodePackageTargetResolutionV2` aggregate 独立运行通过：285 actionable
  tasks，66 executed，219 up-to-date；最终 identity hardening 后又由 Core 依赖重新执行并通过，
  输出固定 `14/4` parity、complete target universe、types hook、C6 v1 `12/6` unchanged，且
  Worker/Program/host-fs/network 为 `0`；
- `verifyR2ProjectCompilerCore` 最终通过：506 actionable tasks，87 executed，419 up-to-date，
  `BUILD SUCCESSFUL in 2m 10s`；
- P2-5 开始前的 P2-4/Core 基线通过：504 actionable tasks，126 executed，378 up-to-date。

## P2-8 后的当前边界

- Node revision-2 package-target v2 已通过 schema-v1 `node-r2-metadata-target-v2` 条件形状与
  37-field start 进入
  archive/snapshot/frame；无 metadata 与 metadata-only 分别使用 20/30-field start；
- exact 20/30/37-field Program 已开放 Worker `.ts/.mts/.cts`、resolver v2、implied format 与 emit；
  package declaration `.d.mts/.d.cts` 仍只作为 resolver-granted target，不进入 root/output；
- 旧 14/27-field transport 的 `P2_NODE_PROGRAM_NOT_ACTIVATED` fence 保持；P2-6 在真实
  compile/emit 后使用的 `P2_NODE_RESULT_NOT_ACTIVATED` 历史 fence 已由 P2-7 替代，20/30/37
  Program 现在沿既有 stop-and-wait framed result 返回 exact 11-key result/4-key outputs；
- production compiler host 对 Node revision 2 调用独立 resolver-v2 session；C6 v1 resolver 与
  package-target helper 的冻结入口/行为保持不变；
- native 从 exact snapshot 与 outputs 独立复算 resolver/target-universe fingerprints 和逐模块
  generated mapping，private manifest revision 3 绑定同一 authority；
- public compiler capability 与 Node Runtime dependency 保持 `false`；
- `R2-M2-P2` checklist 已由 P2-8 关闭，M2 为 `12/18`；本 pure slice 本身仍不构成
  public/end-to-end promotion。

## P2-8 收口与下一项

P2-6 已在保持本 authority、C6 v1 regression 与 14/27-field transport identity 的前提下，完成
production Worker 的 Node revision-2 source-kind ingress、真实 NodeNext Program、
`impliedNodeFormat`、resolver v2 grant 与 emit mapping；其完整 Core 为 508 actionable tasks
（112 executed、396 up-to-date），`BUILD SUCCESSFUL in 3m 25s`。P2-7 又完成 Worker framed result、
native exact-snapshot mapping 复算与 private output revision 3，见
[P2-7 NodeNext Result and Private Output](NODE_NEXT_RESULT_PRIVATE_OUTPUT_R2_M2_P2_7.md)。后续
P2-8 final aggregate 已完成，见
[P2-8 Final Aggregate](NODE_MODULE_OUTPUT_FINAL_AGGREGATE_R2_M2_P2_8.md)。`R2-M2-P3` 随后已完成
[oracle-only compiler-bundle golden](COMPILER_BUNDLE_GOLDEN_R2_M2_P3.md)，P4 随后已完成
[syntax fixture matrix](SYNTAX_FIXTURE_MATRIX_R2_M2_P4.md)，P5 也已完成
[compiler-boundary fixture matrix](COMPILER_BOUNDARY_FIXTURE_MATRIX_R2_M2_P5.md)，X1 也已完成
[public-shaped deterministic bundle](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。X1
只开放 private Rhino/Node revision-1 end-to-end authority；Public/external/runtime 与 Node
revision-2 end-to-end 继续关闭。实时进度以 [R2 Roadmap](../roadmap/R2.md) 为准。
