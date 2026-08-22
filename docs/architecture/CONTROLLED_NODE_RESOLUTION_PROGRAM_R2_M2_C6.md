# R2-M2-C6 Controlled Node Resolution Program Integration

> **R2-M3-S1 supersession（2026-08-14）。** 本页保留 M2 的 pre-S1 input provenance；正文中的
> request-manifest revision `5` 与当时的 Worker start shape 不是当前 formal alias/fallback。
> 当前 metadata + target-v1 条件形状只属于 `autojs6.typescript.input-archive/revision=1`，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。C6 resolver/Program policy 继续保留。

日期：2026-08-02

状态：**internal `R2-M2-C6` 完成；production/public project compilation 仍未激活。**
本闭环把 closed-world snapshot Node package resolver 接入真实 TypeScript 6.0.3
`Program`，但不宣称 provisional NodeNext profile 或 `.mts/.cts` module-kind 规则已经完成；
它们仍分别属于 M2-P1/P2。

## 完成边界

C6 在既有 C1-C5/C7 authority 上关闭以下链路：

```text
revision-5 package metadata + target inventory
  -> frozen resolver catalog
  -> controlled module/type-reference hooks
  -> resolver grant-only Program materialization
  -> typed resolution diagnostic
  -> native result/private-output validation
  -> terminal cleanup and clean successor
```

该链路只在 controlled `configKind=tsconfig`、revision-5 framed input 中激活。C5 的
revision-4 metadata transport 仍保持其原始 `raw-not-resolver-authority` 语义；缺少完整 target
authority、identity 或受控配置时不会隐式升级为 resolver catalog。

四项 public capability 继续为 `false`，frozen v1 AIDL/Binder transaction prefix、normal Release
admission 与 config-none exact shapes 均未改变。

## Resolver authority

Worker 在完成 metadata、target 和 ordinary ingress 后才构造 resolver authority：

- metadata catalog、records、package JSON tree 与 target descriptors 递归冻结；
- target inventory fingerprint 精确绑定 C5 metadata inventory fingerprint；
- dependency roots、package boundary、ASCII case-fold identity 与 target kind 重新验证；
- package target 不进入 ordinary `files`、`sourcePaths` 或 `rootNames`；
- revision 2-4、partial revision-5 或 identity drift 一律 fail closed。

Package target 与 ordinary source 共用既有资源上限。每个文件最多 **65,536 tokens**，整个
project 最多 **262,144 tokens**；target 没有额外 token 配额。真实运行时门禁证明恰好
65,536-token target 可接受，而 65,537-token target 在 ingress 失败、raw byte buffer 归零，
随后 clean request 可继续执行。

## TypeScript Program hooks

Controlled compiler host 安装三个封闭回调：

- `resolveModuleNameLiterals`；
- `resolveTypeReferenceDirectiveReferences`；
- fail-closed `resolveLibrary`。

Module request 从 TypeScript usage location 取得 `import` / `require` mode；当前 provisional Rhino
仍是 CommonJS + Node10，因此 undefined mode 只在该精确 profile 下映射为 `require`。Type-reference
固定使用 `types` mode。Resolver 的 active conditions 由 mode、`types`、`node` 与唯一内部 trusted
condition `custom` 构成；用户 `customConditions` 仍由 C1/C4 禁止。

实现不会调用 TypeScript 默认 `resolveModuleName`/`nodeModuleNameResolver`、`ts.sys`、宿主文件
系统或网络。`exports`、`imports`、conditions、pattern subpath 与 nested package boundary 只在冻结
snapshot catalog 中解析，18 项 failure catalog 保持 stable string identity。

## Grant-only materialization

普通 compiler host 的 `fileExists`、`readFile` 与 ordinary record lookup 对 `/packages/...`
始终不可见。只有 resolver 成功命中的 exact target 才获得当前 compilation-local grant，随后
`getSourceFile` 可以从独立 retained target catalog 读取它。

因此：

- 未解析或 fallback target 不会被 Program materialize；
- package target 永远不是 Program root；
- package target 不进入 JS/Source Map output 或 output `sourcePaths`；
- Program 中出现的每个 package source 都必须能回溯到当前 resolver grant；
- release 后保存的旧 host 不能再次读取任何 target。

真实 Program golden 覆盖 package root `types` branch、内部 `custom` condition、pattern export、
CommonJS `require` branch、project `#imports`、nested package `#imports`，以及
`/// <reference types="pkg" />`。稳定证据为 **6 次 module resolution + 1 次 type-reference
resolution**，其中命中 **5 个唯一 package targets**，fallback target 未 materialize，roots/output
污染为 0。

## Typed failure contract

Resolver failure 不伪装成普通 semantic diagnostic。Worker 返回：

- `phase = "resolution"`；
- TypeScript error category `1`；
- stable numeric code `900001..900018`；
- `start/length/line/column = null`；
- nested exact evidence `nodeResolution: { policyCode }`。

启动门禁会把整个 numeric range 与当前 TypeScript 6.0.3 diagnostics catalog 比对，拒绝任何
碰撞。Numeric code 与 string policy code 在 Kotlin 模型、framed-result parser 和 private output
中双向绑定；普通 diagnostic 仍保持原 9-field shape，top-level controlled result 仍保持原 14 个
keys，明确不增加 top-level `nodeResolution` summary。

任一 resolution error 都跳过 semantic error collection 的不确定噪声并阻止 emit。Worker
`outputs` 必须为空；native 只可生成 diagnostics outcome 的私有归档，不得包含可执行 module 或
Source Map。Diagnostics-only archive 可以在其 `diagnostics.json` inventory 中引用 exact accepted
package target，但普通 diagnostic 和 executable output 不能借此扩大 source authority。

## Framing、phase 与 cleanup

完整门禁固定验证：

- `ingress -> parse -> semantic -> emit` 各自严格 started/completed；
- `emit:completed` 只在 native-style stop-and-wait result 的最终 ACK 后发布；
- result start/chunks/end 的 request、chunk count、byte length 与 SHA-256 完全一致；
- terminal late ACK 无副作用；
- success、两条 typed failure 与 token rejection 后均释放 framed state；
- resolver memo、grants、failure ledger、catalog paths、target records/text holders 与 raw bytes
  均在 terminal path 清理；失败结果不会污染 clean successor。

两条真实 Program failure 分别固定 `NODE_RESOLUTION_EXPORTS_NO_MATCH` 与
`NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT`。门禁同时证明 hook 实际收到目标 specifier、返回
unresolved、没有 materialize package source，而不是仅检查一条合成 diagnostic。

## 验证入口

完整 C6 gate：

```powershell
.\gradlew.bat --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" `
  :app:verifyR2M2C6NodeResolutionProgramIntegration
```

2026-08-02 串行结果：

```text
6 module + 1 type-reference resolutions
5 unique package targets
2 stable typed failures / 18-code catalog
token boundary = 65536 accepted / 65537 rejected
roots/outputs polluted = 0
default resolver / ts.sys / network = 0
clean successor = 1
BUILD SUCCESSFUL (124 actionable tasks)
```

完整项目聚合：

```powershell
.\gradlew.bat --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" `
  :app:verifyR2ProjectCompilerCore
```

同日结果为 `BUILD SUCCESSFUL`，共 **488 actionable tasks**。它同时覆盖 JVM/AndroidTest 编译、
debug/release、四 ABI native、public preview、shipping Release 与既有 timeout/resource evidence。

## 历史切片与后续任务

C6-A 至 C6-D 保留为原子边界证据：

- [C6-A Snapshot Kernel](SNAPSHOT_NODE_RESOLUTION_KERNEL_R2_M2_C6A.md)；
- [C6-B Package Target and Parity](PACKAGE_TARGET_AND_NODE_RESOLUTION_PARITY_R2_M2_C6B.md)；
- [C6-C Worker Resolver](WORKER_SNAPSHOT_NODE_RESOLUTION_R2_M2_C6C.md)；
- [C6-D Worker Target Transport](WORKER_PACKAGE_TARGET_TRANSPORT_R2_M2_C6D.md)。

这些文档中的 `Program=0` 或“尚未激活”只描述相应切片当时的刻意边界；本文件记录完整 C6
的后续闭环。普通 host read 不可见仍是现行安全不变量，Program visibility 只能来自当前
resolver grant。

`R2-M2-C8` 已于 2026-08-02 完成 common/Rhino/Node declarations 分层、source lock、
fingerprint 与双向隔离。P1/P2 随后已完成 provisional NodeNext revision 1/2、
`.mts/.cts/.ts` module-kind/output、result/private output 与 final aggregate；见
[P2-8 Final Aggregate](NODE_MODULE_OUTPUT_FINAL_AGGREGATE_R2_M2_P2_8.md)。`R2-M2-P3` 随后已完成
[oracle-only compiler-bundle golden](COMPILER_BUNDLE_GOLDEN_R2_M2_P3.md)，且未改变 production
admission 或创建 runtime engine；`R2-M2-P4` 随后已完成
[syntax fixture matrix](SYNTAX_FIXTURE_MATRIX_R2_M2_P4.md)，P5 也已完成
[compiler-boundary fixture matrix](COMPILER_BOUNDARY_FIXTURE_MATRIX_R2_M2_P5.md)，X1 也已完成
[public-shaped deterministic bundle](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。实时
M2 进度与下一活动项以 [R2 Roadmap](../roadmap/R2.md) 为准。
