# R2-M2-P3 Compiler Bundle Golden

日期：2026-08-11

状态：**已完成。Direct TypeScript 6.0.3 oracle、JVM fixture contract、P3 final aggregate
与最终 Core 均已通过；`R2-M2-P3` 关闭时 M2 升至 `13/18`。P4 syntax matrix、
P5 compiler-boundary matrix 与 X1 public-shaped deterministic bundle 随后均已完成；
实时 M2 进度与下一活动项以 [R2 Roadmap](../roadmap/R2.md) 为准。**

## 目的与关闭边界

P3 建立一个与 Android runtime 和 production transport 解耦的 compiler-bundle oracle，
用实际 `typescript@6.0.3` Compiler API 回答三个确定性问题：

1. Rhino 和 Node 的冻结 options 是否都能对两文件 import 图生成稳定 JS/Source Map
   output collection；
2. 普通 source circular import 是否能在两个 profile options 下稳定 emit，且不与
   C6/P2 resolver 的 package-target cycle fail-closed 语义混淆；
3. TypeScript 6.0.3 在显式 `allowJs=true` 与 `resolveJsonModule=true` 的独立 oracle
   options 下，是否能对 mixed `.ts + .js + .json` 图生成确定性 output collection。

这里的 “compiler bundle” 表示 direct Compiler API 产生的排序 output collection，不是
M3 尚未冻结的 public output archive schema，也不是把所有 module 合并为一个 JS
文件。一源文件一 JS 与强制 Source Map 的 GA 决策保持不变。

## 共享 fixture 与 direct oracle

唯一 checked-in scenario authority 为：

```text
app/src/test/resources/r2-compiler-bundle/p3-v1.json
```

它由下列 direct harness 消费：

```text
app/src/test/scripts/verify-r2-m2-p3-compiler-bundle.js
```

fixture 将 compiler version、scenario kind、effective options、排序 input inventory、预期
diagnostics/emit 状态、output path/text size/SHA-256 与 Source Map 关键字段作为一个
revisioned oracle 冻结。Harness 必须使用自建的 snapshot-only CompilerHost，不得把
`ts.sys`、宿主文件系统、网络或 Node Runtime 变成 compiler authority。

| Scenario | Options authority | 必须证明 |
| --- | --- | --- |
| Rhino two-file import | registered provisional Rhino exact options | `main.ts -> dependency.ts`，2 modules + 2 maps，无 blocking diagnostic |
| Rhino source cycle | registered provisional Rhino exact options | 两个 `.ts` source 互相 import 仍确定性 emit |
| Node two-file import | registered provisional NodeNext exact options | 两文件 graph 在 NodeNext options 下产生稳定 JS/maps |
| Node source cycle | registered provisional NodeNext exact options | source cycle 成功，不放宽 package-target cycle policy |
| mixed oracle | standalone oracle-only options | `.ts` 引用 `.js` 与 `.json`，`allowJs/resolveJsonModule=true`，稳定 JS/maps/JSON outputs |

对每个 scenario，oracle 必须至少重复编译两次，并以反转 input/root 插入顺序的
等价运行证明：

- diagnostics 分类、`emitSkipped` 与排序 output paths 一致；
- 每个 output 的 UTF-8 byte length 和 lowercase SHA-256 与 fixture 精确一致；
- JavaScript 与 Source Map v3 一一配对，map `file/sources/sourceRoot/mappings` 满足 fixture；
- 反转输入不改变 output order 或任何 output bytes；
- compile 期间 `ts.sys`/host filesystem/network 观测计数均为 0；
- 不执行任何生成的 JavaScript。

Mixed scenario 中 TypeScript 会生成 JS + map，并产生一个没有 Source Map 的 JSON
output。这个 shape 只是 compiler-semantics evidence；当前 production Worker/native/private
ZIP 没有接收该 mixed result 的 authority。

## Mixed oracle-only 隔离

Mixed options 必须使用 fixture 中独立、显式的 oracle mode/revision，不得伪装成
Rhino/Node registered profile 的新 revision。本切片不允许：

- 将 `allowJs=true` 或 `resolveJsonModule=true` 写入 production profile authority；
- 将 `.js/.json` 加入 `R2ProjectSourceKindAdmission` 的 executable source set；
- 调整 archive/snapshot root completeness、Worker frame、result exact keys、generated mapping 或
  private output artifact kinds；
- 将 mixed result 提升为 public-shaped service、end-to-end Node revision 2 或 runtime 能力；
- 修改 Rhino `.ts` 以及 Node `.ts/.mts/.cts` 的首个 GA source-kind 决策。

因此，P3 mixed PASS 只说明 pinned compiler 本身在该显式 options 下具备稳定语义，
不是插件 product admission 或 runtime compatibility 的证明。

## Gradle 门禁

Focused direct oracle：

```powershell
.\gradlew.bat :app:verifyR2M2P3CompilerBundleGoldenDesktop `
  --no-daemon --no-parallel --max-workers=2 `
  -Pkotlin.compiler.execution.strategy=in-process
```

P3 final aggregate：

```powershell
.\gradlew.bat :app:verifyR2M2P3CompilerBundleGolden `
  --no-daemon --no-parallel --max-workers=2 `
  -Pkotlin.compiler.execution.strategy=in-process
```

最终 aggregate 必须直接依赖 P2-8 final aggregate、direct oracle、app JVM/API
freeze、Debug/Release compiler-host APK、Release compatibility、public source-policy 与 optional-plugin
independence gates。它还必须独立确认：

- production `allowJs=false` 且 `resolveJsonModule=false`；
- Rhino 仍只有 `.ts`，Node 仍只有 `.ts/.mts/.cts`；
- Node revision 2 仍只在 `workerResultAdmittedAuthorities`，不在
  `endToEndAdmittedAuthorities`；
- `COMPILER_AVAILABLE`、`PROJECT_COMPILATION`、`SEMANTIC_DIAGNOSTICS`、
  `SOURCE_MAP_V3` 与 `NODE_RUNTIME_REQUIRED` 均继续为 `false`。

`verifyR2ProjectCompilerCore` 已依赖 P3 final aggregate。本切片
不需要 ADB 或 connected-device evidence；静态/JVM/desktop/APK 证据不得被描述为
device acceptance。

## 最终证据

2026-08-11 的关闭记录：

```text
verifyR2M2P3CompilerBundleGoldenDesktop: PASS
  TypeScript 6.0.3; cases=5; compiles=20;
  ts.sys/host-fs/network/output execution=0
verifyR2M2P3CompilerBundleGolden:        PASS
  desktop TypeScript semantics + JVM fixture contract;
  production allowJs/resolveJsonModule/source-kind/JSON-output admission unchanged;
  Node revision 2 Worker-result-only; public/end-to-end capabilities=false;
  runtime engines=0
verifyR2ProjectCompilerCore:             PASS
  BUILD SUCCESSFUL in 2m 37s
  517 actionable tasks: 100 executed, 417 up-to-date
```

本轮未运行 ADB、未使用设备；这些结果不构成 device acceptance。Mixed scenario 仍仅属
oracle-local compiler semantics，不进入注册 profile、production archive/Worker/native/result、
public/end-to-end admission 或 runtime。P3 关闭后 M2 从 `12/18` 更新为 `13/18`；P4 随后已完成
[Syntax Fixture Matrix](SYNTAX_FIXTURE_MATRIX_R2_M2_P4.md)，P5 也已完成
[Compiler Boundary Fixture Matrix](COMPILER_BOUNDARY_FIXTURE_MATRIX_R2_M2_P5.md)，X1 也已完成
[Public-shaped Deterministic Bundle](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。实时
M2 进度与下一活动项以 [R2 Roadmap](../roadmap/R2.md) 为准。

## 非目标

P3 不证明：

- public output/diagnostics schema freeze，该工作属于 M3；
- enum/decorator 等语法矩阵或 path/Unicode/high-token 矩阵，它们分别属于 P4/P5；
- public-shaped service 可生成 Rhino/Node deterministic bundle，P3 不证明该项；后续已由
  [M2-X1](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md) 独立关闭；
- runtime engine 创建、module loading、JSON runtime behavior 或 generated JavaScript 执行；
- Android/WebView byte parity、normal Release promotion 或设备 matrix；
- 任何 capability promotion、Node Runtime dependency 或 AutoJs6 host routing。
