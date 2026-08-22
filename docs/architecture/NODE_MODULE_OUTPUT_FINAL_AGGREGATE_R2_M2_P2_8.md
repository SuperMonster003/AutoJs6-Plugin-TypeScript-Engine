# R2-M2-P2-8 Node Module-kind/Output Final Aggregate

> **R2-M3-S1 supersession（2026-08-14）。** 本页中的 input manifest revisions `6/7`、
> 14/27-field 与 20/30/37-field starts 是 pre-S1 receipt/provenance，不是当前 accepted frame。
> Formal input revision 1 的对应 Worker exact counts 为 24/37 与 30/40/47，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。原 aggregate receipt 仍按发生时原样保留。

日期：2026-08-10

状态：**P2-8 final aggregate 已完成；`R2-M2-P2` 关闭时 M2 为 `12/18`。Node revision 2
继续只进入 Worker-result authority，不进入 end-to-end/public admission；Node Runtime dependency
继续关闭。P3 oracle-only aggregate、P4 syntax fixture matrix、P5 compiler-boundary matrix 与
X1 public-shaped deterministic bundle 随后已通过。X1 只开放 private Rhino/Node
revision-1 end-to-end authority；Public/external/runtime、Node revision-2 end-to-end 与 Node Runtime
dependency 继续关闭。实时进度以 [R2 Roadmap](../roadmap/R2.md) 为准。**

## 关闭边界

P2-8 不增加新的 compiler feature 或 wire field。它把 P1、C6 v1 与 P2-0 至 P2-7 的分层
证据组合为一个可持续执行的最终门禁，并确认下列内部 module-kind/output contract 已闭环：

- P1 Node revision 1 仍是 config-none、`.ts -> .js` 的默认/兼容 authority；
- C6 resolver v1 仍只接受 `.ts/.d.ts`，其 `12 supported / 6 fail-closed` fixture 不变；
- exact Node revision 2 接受 `.ts/.mts/.cts`，使用 resolver v2、package-format、package-target
  与 complete target-universe authority；
- legacy `14/27`-field start 仍在 Program 前以 `P2_NODE_PROGRAM_NOT_ACTIVATED` fail closed；
- revision-2 Program 只由 exact `20/30/37`-field start 激活；
- Worker result/output wire 保持 exact `11/4`，native/private output 从 exact accepted snapshot
  独立复算 generated mapping 与 resolver authority；
- input manifest revisions `6/7` 与 private output manifest revision `3` 保持各自职责，不回溯
  重解释 P1、C6 或 frozen public API/AIDL。

P2-8 关闭的是 internal M2-P2 policy item，不是完整 M2、Public R2、Node Runtime 或 R3
preparation/runtime execution。

## 聚合依赖

`verifyR2M2P2P8FinalAggregate` 直接聚合：

- `verifyR2M2P1ProvisionalNodeNextProfile`；
- `verifyR2M2C6NodeResolutionProgramIntegration`；
- P2 authority foundation、P2-3A、P2-3B、P2-4、P2-5、P2-6、P2-7 aggregates；
- Debug/Release TypeScript distribution、declaration distribution 与 compiler-host APK gates；
- Release compatibility APK gate、JVM tests 与 AndroidTest Kotlin compilation；
- compiler-host source、public non-activation、optional-plugin independence 与 API v1 freeze gates。

最终 Core 通过 `verifyR2M2P2P8FinalAggregate` 消费上述闭环，因此 P2-8 不依赖历史日志或仅靠
文档声明完成。

## 冻结 regression inventory

| Authority | P2-8 冻结值 |
| --- | --- |
| P2 module/output policy | fingerprint `5c2294f0ee9d6d25bd2132b4ea24866d0a5f9caaa521a63bf81ffe1d897f6ce7`；`.ts/.mts/.cts`；`commonjs/esm` |
| generated mapping parity | 10 valid / 19 invalid |
| package-format catalog parity | 4 valid / 8 invalid type / 5 invalid lookup |
| C6 resolver v1 | 12 supported / 6 fail-closed；只接受 `.ts/.d.ts` target kinds |
| resolver v2 | 14 supported / 4 fail-closed；`unchanged-reject-new-kinds` v1 compatibility |
| Worker ingress | legacy `14/27`；Program `20/30/37` |
| Worker result | 11-key config-none result；4-key outputs；7 framed P2 results |
| native/private output | exact-snapshot result authority；10-field generated mappings；manifest revision 3 |

Direct TypeScript 6.0.3 harness 同时覆盖 `.ts/.mts/.cts -> .js/.mjs/.cjs + maps`、
`impliedNodeFormat`、relative/imports/exports/type-reference resolution、stable failures `2x2`、clean
P2 successor、P1 regression、legacy transport identity，以及 default resolver/`ts.sys`/network 为零。

## APK gate ownership 修正

P2-8 的 clean rebuild 暴露并修正了 APK verification ownership 漂移。最终规则为：

- normal build 中 malformed public R2 metadata 必须继续 fail closed；
- capacity-only request rejection 只属于显式 evidence variants；
- 8 个 private request sentinels 在 Debug/Release 中都必须 fail closed；
- stop telemetry 只允许存在于 Debug，不进入 Release。

这些规则由最终 APK/source gates 重新验证；它们没有开放 public request、private control namespace
或 Release telemetry。

## 最终验证

在确认没有其他 Codex/Gradle 事务，并取得连续两次空闲采样后，使用互斥保护、无并行和受限
worker 的命令执行最终 Core：

```powershell
./gradlew.bat --console=plain --no-daemon --no-parallel --max-workers=2 `
  -Pkotlin.compiler.execution.strategy=in-process `
  :app:verifyR2ProjectCompilerCore
```

最终结果：

```text
BUILD SUCCESSFUL in 2m 36s
515 actionable tasks: 101 executed, 414 up-to-date
```

该 Core 内的 `verifyR2M2P2P8FinalAggregate` PASS；未另行执行 standalone aggregate。Direct
Node P2-7/P2-8 dependency harness PASS（exact starts `20/30/37`、result `11`、outputs `4`、
framed results `7`）；Worker/host `node --check` PASS；`git diff --check` PASS。

本阶段没有运行 ADB，没有查询或触碰任何设备。因此上述证据是 JVM/desktop/Android build/APK
级闭环，不冒充 device 或 production-public acceptance。

## 继续关闭的边界与下一步

- `endToEndAdmittedAuthorities` 继续只有 Rhino 与 Node revision 1；
- Node revision 2 只额外进入 `workerResultAdmittedAuthorities`；
- `COMPILER_AVAILABLE`、`PROJECT_COMPILATION`、`SEMANTIC_DIAGNOSTICS`、`SOURCE_MAP_V3` 与
  `NODE_RUNTIME_REQUIRED` 继续为 `false`；
- public/external Node compilation、Node Runtime、R3 preparation 与 runtime execution 均未开放；
- P4、P5 与 X1 随后已完成；X2-X3 仍待完成，实时进度以
  [R2 Roadmap](../roadmap/R2.md) 为准。

`R2-M2-P3` 已完成独立 TypeScript 6.0.3 compiler-bundle oracle：两文件 import
与 source cycle 使用 Rhino/Node 冻结 options，mixed `.ts + .js + .json` 只使用
oracle-local `allowJs/resolveJsonModule`。它不改变 production source kinds，也不创建
runtime engine；本轮未运行 ADB 或使用设备。见
[P3 Compiler Bundle Golden](COMPILER_BUNDLE_GOLDEN_R2_M2_P3.md)。
