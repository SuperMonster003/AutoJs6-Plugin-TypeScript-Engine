# R2-M2-C7 Controlled Root Selection

> **R2-M3-S1 supersession（2026-08-14）。** 本页保留 M2 的 pre-S1 input provenance；正文中的
> request-manifest revisions `3/4/5` 与当时的 Worker start shape 不是当前 formal alias/fallback。
> 当前只接受 `autojs6.typescript.input-archive/revision=1`，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。C7 selector/root authority 继续保留。

日期：2026-08-01

状态：**internal C7 完成；production/public project compilation 仍未激活。** 完整
`R2-M2-C6` 已于 2026-08-02 由
[Controlled Node Resolution Program Integration](CONTROLLED_NODE_RESOLUTION_PROGRAM_R2_M2_C6.md)
关闭；C8 declarations 分层也已于 2026-08-02 完成。P1/P2 NodeNext profile/module-kind 在 C7
当时仍是后续任务；input schema freeze 现已由 M3-S1 完成。

## 目标与边界

C7 负责把 C1-C3 已冻结的 `files/include/exclude` selectors 变成真实 TypeScript
Program roots，并把 entry、selected roots、Program-reachable executable sources 与 output
逐层绑定。它不解释 C5 package metadata，也不替代 C6 NodeNext resolution。

该闭环只存在于 internal controlled branch。production profile registry 仍只接受
`configKind=none`，四项 public capability 仍为 `false`，AIDL transaction prefix 不变。

## Entry identity

Controlled revision-3/revision-4 `projectInputStart` 精确新增一个字段：

```text
entryPath: canonical /sources/.../*.ts, excluding *.d.ts
```

entry identity 从 request manifest 经 heap/file-backed snapshot 和 framer 无损进入 Worker。
每一层都要求它：

- 是 printable-ASCII、canonical、无 escape 的 `/sources/*.ts`；
- 不是 declaration；
- 存在于 exact snapshot；
- 属于 archive 接受的完整 executable-root inventory。

Config-none start frame 没有新增字段，legacy exact shape 保持不变。

## Snapshot-only selector semantics

Worker 固定使用 TypeScript `6.0.3` 的 `ts.matchFiles`。它只看到已经运输到内存的
`/sources` inventory，以及由该 inventory 构造的只读 `getFileSystemEntries`；不会调用
`ts.sys`、宿主文件系统或网络。

| 输入 | 冻结行为 |
| --- | --- |
| `files` | 与 include matches 做 union；重复项去重；不受 `exclude` 排除 |
| `include` | 相对 logical `/sources` root 匹配；`exclude` 只作用于 include matching |
| selectors 全部缺省 | 使用 `**/*` |
| `files: []` / 空选择 | 在 Program 前以 `CONFIG_NO_INPUTS` 失败 |
| entry 未入非空 roots | 在 Program 前以 `CONFIG_ENTRY_NOT_SELECTED` 失败 |

Selectors 与 source paths 先 NFC canonicalize，再按 UTF-16 code-unit 排序并拒绝 canonical
collision。`CONFIG_NO_INPUTS` 优先于 entry membership failure，失败时不会发布 result start，
也不会进入 parse phase 或构造 Program。

## 三个 inventory

C7 明确区分三个集合，避免把 selector roots 错当成全部 emit sources：

| 集合 | 来源 | 用途 |
| --- | --- | --- |
| accepted snapshot paths | native archive/snapshot | 所有 diagnostics、roots 和 outputs 的外边界 |
| selected root names | `files/include/exclude` | 传给 `createIncrementalProgram.rootNames`；可包含 `.d.ts` |
| reachable executable sources | `Program.getSourceFiles()` | selected executable roots 加其实际可达 import；每项必须产生一份 module 与 map |

因此，一个未被 selector 直接选中的 `.ts` 仍可因 selected entry 的相对 import 进入 Program
并 emit；一个既未选中又不可达的 source 不得 emit；`.d.ts` 可以成为 Program root，但永远
不是 executable output candidate。

## Controlled result 与 native binding

Controlled result 以 legacy 11 个 top-level keys 为基线，只增加：

- `effectiveOptionsFingerprint`：C3 canonical 41-key authority 的 lowercase SHA-256；
- `effectiveOptionsRevision`：固定为 `1`；
- `rootNames`：sorted、unique、canonical selected roots。

`options` 必须精确包含 C3 的 41 keys。Native protocol 重新构造
`R2ControlledTsConfigEffectiveOptions` 并复核 defaults、resolved booleans、canonical map 和
fingerprint，而不是信任 Worker 传回的摘要。`rootNames` 额外受 4,607 项 cardinality fence
约束。

Config-none result 仍是原 11 个 top-level keys 和 18-key options，不接受上述三个字段。

WebView host 和 private output staging 再次验证：

- entry 属于 selected roots；
- selected roots 全部属于 exact snapshot；
- selected executable roots 全部出现在 emitted sources；
- emitted sources 可以包含 import-reachable sources，但必须属于 accepted executable roots；
- module、Source Map 和 source identity 一一对应，entry 必须 emit。

Output manifest 在 controlled branch 使用受控 config schema/kind、41-key canonical options 与
对应 fingerprint；config-none manifest 保持原 authority。

## 独立验证

专属门禁：

```powershell
.\gradlew.bat --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" `
  :app:verifyR2M2C7ControlledTsConfigRootSelection
```

2026-08-01 的串行验证结果：

```text
5 selector semantics
3 pre-Program failures
3 reachable executable sources
41 controlled options / 18 legacy options
compiler host I/O = 0
BUILD SUCCESSFUL
```

该 gate 同时重跑 C1-C5、C6-A JVM/Node evidence、完整 config-none desktop/P3b harness、
source policy、public non-activation 与 frozen AIDL/API checks。大型 legacy harness 继续覆盖
4,607-file ingress 和 512 MiB retained-heap child。

## 非目标与下一步

C7 本身不把 C5 metadata 变成 resolver authority，也不启用 NodeNext profile、
`.mts/.cts/.js/.json` resolution 或 production admission。其后完整 C6 已在 revision-5 中把
resolver hook、retained-target grant、typed failure/result/output 接到本 C7 Program 闭环；
普通 compiler-host reads 仍不可见。其后 C8 declarations 分层与 P1/P2 NodeNext profile、
module-kind/output、result/private output 及 final aggregate 均已完成；见
[P2-8 Final Aggregate](NODE_MODULE_OUTPUT_FINAL_AGGREGATE_R2_M2_P2_8.md)。`R2-M2-P3` 随后已完成
[oracle-only compiler-bundle golden](COMPILER_BUNDLE_GOLDEN_R2_M2_P3.md)，且未改变 production
admission 或创建 runtime engine；`R2-M2-P4` 随后已完成
[syntax fixture matrix](SYNTAX_FIXTURE_MATRIX_R2_M2_P4.md)，P5 也已完成
[compiler-boundary fixture matrix](COMPILER_BOUNDARY_FIXTURE_MATRIX_R2_M2_P5.md)，X1 也已完成
[public-shaped deterministic bundle](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。实时
M2 进度与下一活动项以 [R2 Roadmap](../roadmap/R2.md) 为准。
