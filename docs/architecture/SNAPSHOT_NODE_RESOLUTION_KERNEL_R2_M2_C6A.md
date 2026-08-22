# R2-M2-C6-A Snapshot-only Node Resolution Kernel

> **R2-M3-S1 supersession（2026-08-14）。** 本页的 archive/start revision 描述只保留为
> pre-S1 provenance；当前 formal input archive 只接受 revision 1，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。本页的 resolver-kernel 结论继续保留。

日期：2026-08-01

状态：**C6-A 原子内核证据已完成；本文保留该切片建立时的离线边界。** 完整
`R2-M2-C6` 已于 2026-08-02 由
[Controlled Node Resolution Program](CONTROLLED_NODE_RESOLUTION_PROGRAM_R2_M2_C6.md)
关闭：ordinary compiler-host reads 继续看不到 package targets，只有 resolver 精确授权的 target
才可进入 Program。`R2-M2-P1` NodeNext profile、`R2-M2-P2` 的 `.mts/.cts` 输出语义与
production/public capability 仍由独立 checklist 负责，不属于 C6。

## 完成边界

C6-A 在 C5 已接受的 package metadata identity 上建立一个纯 Kotlin、完全离线的 resolution
oracle。调用方只能传入：

- C5 `R2PackageMetadataInventory` 与按 descriptor 顺序提供的 exact package bytes；
- canonical containing path、specifier、import/require/types mode 与显式 conditions；
- 一个已经封闭的 project/declaration target inventory。

内核没有 filesystem、network、TypeScript Program、compiler host 或 runtime API。任何 target
只有精确存在于调用方提供的 snapshot inventory 才能成功；除 C6-B 冻结的确定性
`.js -> .ts -> .d.ts` substitution 外，不执行其他 extension substitution、目录 probing 或
宿主 fallback。

## 已冻结语义

- C5 size/SHA-256/strict JSON/lifecycle policy 在 metadata catalog 构造时重新验证；
- `exports`、`imports` 支持 exact key 与最具体 pattern；
- condition 按 JSON property order 选择，array target 按声明顺序 fallback；
- 支持 scoped dependency、imports 到显式允许 dependency 的转发与 cycle detection；
- `#imports` 使用 containing path 的 nearest accepted nested package boundary；
- relative/package target 必须留在 package boundary 内并精确命中 snapshot；
- case-fold collision、target escape、unsupported source kind 与缺失 target 均 fail closed；
- malformed mapping key 映射为 metadata invalid，合法但无匹配才映射为 no-match。

## 稳定 failure catalog

```text
NODE_RESOLUTION_INVALID_CONTAINING_PATH
NODE_RESOLUTION_INVALID_SPECIFIER
NODE_RESOLUTION_INVALID_CONDITION
NODE_RESOLUTION_AMBIGUOUS_CASE
NODE_RESOLUTION_PACKAGE_NOT_ALLOWED
NODE_RESOLUTION_PACKAGE_METADATA_MISSING
NODE_RESOLUTION_PACKAGE_METADATA_INVALID
NODE_RESOLUTION_IMPORTS_NOT_DEFINED
NODE_RESOLUTION_IMPORTS_NO_MATCH
NODE_RESOLUTION_EXPORTS_NOT_DEFINED
NODE_RESOLUTION_EXPORTS_NO_MATCH
NODE_RESOLUTION_CONDITION_NO_MATCH
NODE_RESOLUTION_INVALID_TARGET
NODE_RESOLUTION_TARGET_BLOCKED
NODE_RESOLUTION_TARGET_ESCAPE
NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT
NODE_RESOLUTION_UNSUPPORTED_SOURCE_KIND
NODE_RESOLUTION_CYCLE
```

JVM oracle 冻结 exact 顺序与拼写，而不仅验证 `NODE_RESOLUTION_` prefix。

## 原子切片边界与完整 C6 后续收口

C6-B 现已提供 package target policy、archive revision 5 的 native heap/file-backed snapshot
payload，以及共享的 TypeScript parity oracle；详见
[C6-B package target 与 resolver parity](PACKAGE_TARGET_AND_NODE_RESOLUTION_PARITY_R2_M2_C6B.md)。
在 C6-A～D 原子切片分别验收时，这些 target bytes 虽已由 C6-D 进入独立
`retainedPackageTargets`，ordinary compiler VFS 与 Program 当时仍不可见，resolver 也尚未进入
compiler host 或结果 schema；C6-C 的 Worker 等价内核当时仍是孤立 oracle。详见
[C6-C Worker snapshot resolver](WORKER_SNAPSHOT_NODE_RESOLUTION_R2_M2_C6C.md) 与
[C6-D Worker target transport](WORKER_PACKAGE_TARGET_TRANSPORT_R2_M2_C6D.md)。这些描述是原子
门禁的历史边界，不代表当前 production activation 状态。

完整 C6 随后在不扩大 authority 的前提下完成了：

- 在 C7 Program/root-selection 闭环上接入 Worker/compiler-host resolver，只让精确 grant 消费
  retained targets，ordinary host visibility 仍为 0；
- 将 parity/failure catalog 接入 typed production result/output，并验证失败无产物与 clean
  successor；
- 以端到端 Program gate 证明没有宿主路径、默认 resolver、`ts.sys`、网络或其他 fallback。

`R2-M2-P1` 的 provisional NodeNext profile 与 `R2-M2-P2` 的 `.mts/.cts/.ts` module/output
语义是路线中的独立 checklist，不属于 C6 的完成条件。C6 只需交付可由这些后续 profile
消费的 snapshot-only resolution authority，不能以尚未完成 P1/P2 为由改变自身 DoD。

因此路线中的 C6 已可勾选；P1/P2 仍保持独立待办。

## 验收

原子 gate：

```powershell
.\gradlew.bat --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" `
  :app:verifyR2M2C6ASnapshotNodeResolutionKernel
```

该 gate 组合 C5、JVM kernel tests、exact failure catalog、无 filesystem/network/Program import
source fence，以及 public capability=false/frozen API gate。它证明的是 C6-A 纯 Kotlin 原子
内核，不单独代表后续的 resolver/Program activation；完整闭环证据见上述 C6 总结文档。
