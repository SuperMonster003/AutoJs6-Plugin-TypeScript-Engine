# R2-M3-D1 Ordinary Input Quota Decision

> **S1-3 supersession（2026-08-23）。** 本文余下内容冻结的是历史
> `inputQuotaRevision=1`。当前 production 使用 revision 2：`512 files / 8 MiB per file /
> 8 MiB total` 只计 caller sources、raw config、package metadata 与 package targets；
> plugin-owned trusted declarations 不再占 caller headroom，而由 trusted host 的独立 retained-input
> guardrail（4607 files / 72 MiB）约束。formal input archive schema revision 仍为 1，Public caller
> 不能选择 retained guardrail，也不能回退请求 revision 1。

状态：**完成（2026-08-13）**。本页冻结首版 ordinary compilation input quota 的职责边界；
`verifyR2M3D1OrdinaryInputQuotaDecision` 与无排除项 `verifyR2ProjectCompilerCore` 已在当前源码上
通过，Roadmap 的 D1 已关闭。

## 结论

首版 ordinary compilation input quota 保持：

- 最多 **512 个 Worker-retained input files**；
- 单文件最多 **8 MiB decoded UTF-8 bytes**；
- 全部 Worker-retained input 合计最多 **8 MiB decoded UTF-8 bytes**。

三个比较器边界均为 inclusive，前提是其余维度仍符合 quota；任一维度 `+1` 必须在 Worker
dispatch 前 fail closed。由于 trusted declarations 已占用 aggregate，caller 的单个 8 MiB 文件
并不构成真实可接受项目。D1 不把 64 MiB
capacity evidence 推广为 ordinary compilation capability，也不提供 caller opt-in、设备自适应放宽
或失败后的自动重试。

## 两层 quota 不得混淆

| 层 | 首版边界 | 语义 |
| --- | --- | --- |
| v1 input transport/validation | 4096 archive entries、64 MiB container、64 MiB expanded、8 MiB/entry | PFD staging 与 ZIP validation ceiling；通过不保证可编译 |
| ordinary compilation input | 512 files、8 MiB/file、8 MiB total | TypeScript Worker retained strings、SourceFile、Program/AST 的发布安全围栏 |
| TEST_ONLY capacity evidence | exact 64 MiB 与 `+1` 的专用 evidence variants | 证明受控 non-shipping full-pipeline capacity 与 fail-closed seam，不属于 normal build capability |

`TypeScriptCompilerContract.MAX_INPUT_*` 继续保持 v1 transport compatibility；D1 在 additive
`TypeScriptR2Contract` 中建立独立 ordinary-compilation quota revision 与常量，避免 caller 把
4096/64 MiB 理解为可编译项目规模。

## Exact counting domain

ordinary quota 对 accepted archive/profile authority 派生出的完整 Worker-retained input 计数：

1. caller project sources；
2. plugin-owned trusted declarations；
3. raw controlled `tsconfig.json`（存在时恰好一次）；
4. accepted package metadata；
5. accepted package targets v1 或 Node package targets v2。

`request-manifest.json`、ZIP headers、compressed/container bytes、private staging metadata 与 output
均不进入这个计数域。file count、largest decoded UTF-8 file 与 aggregate decoded UTF-8 bytes 必须
从同一 exact snapshot authority 派生，不能由 host 只看 ordinary `fileInventory` 后手工重建。

维度判定顺序固定为 file count、single-file bytes、total bytes。caller-owned超限映射为
`ERROR_ARCHIVE_QUOTA`、不 dispatch Worker、caller output empty；production archive authority 会把
plugin-owned declarations 自身破坏 quota 归为本地 compiler distribution failure，而不伪装成
caller quota。

## Profile-specific headroom

512/8 MiB 是完整 effective input 的上限，不是 caller sources 的独占额度。当前 TypeScript 6.0.3
distribution 的占用为：

| profile | trusted declarations | declaration bytes | config-none source-only headroom |
| --- | ---: | ---: | ---: |
| Rhino revision 1 | 109 | 3,785,583 | 403 files / 4,603,025 bytes |
| NodeNext revision 1/2 | 167 | 4,031,330 | 345 files / 4,357,278 bytes |

这些 headroom 是当前 distribution/profile 的派生事实，不是永久 public constants。M3-S1 必须让
caller 从 frozen compiler-info/input schema 获得 exact declaration occupancy；distribution 更新后应
重新派生，而不能继续硬编码本表数字。raw config 或 package inputs 会继续消耗相同额度。

早期 external timeout/capacity workload 使用的是仅含 108 个 TypeScript standard libraries 的
pre-C8 authority，因此其中 `404 files / 4,603,850 bytes` 只能作为历史保守证据，不能冒充当前
Rhino/Node profile 的 inclusive D1 或未来 M4 Release receipt。M4 必须在 frozen schema 与当前
profile declarations 上重建 workload。

## Authority 与回归要求

D1 的 canonical fixture 为
`app/src/test/resources/r2-input-quota/d1-v1.json`，schema identity 为
`autojs6.typescript.r2-m3-d1.ordinary-input-quota-decision`。JVM 与 Gradle 必须独立复核：

- additive public constants与 native `R2ProjectSnapshotLimits.ORDINARY_COMPILATION` exact 相等；
- legacy `PROVISIONAL_WORKER` 仅为 compatibility alias，production 使用 ordinary authority；
- heap/file-backed snapshot、archive adapter 与 host pre-dispatch 使用相同五域计数；
- 三个 inclusive 边界及三个 `+1`，并冻结 first-exceeded-dimension 顺序；
- Rhino/Node declaration occupancy与派生 headroom；
- v1 4096/64 MiB transport ceiling 仍独立，normal builds 不具备 64 MiB ordinary opt-in；
- quota rejection 为 empty output，TEST_ONLY capacity namespace不进入 shipping/public admission；
- Public capability、Node Runtime dependency 与 Node revision-2 end-to-end admission继续关闭。

验证入口：

- `:app:verifyR2M3D1OrdinaryInputQuotaFixture`；
- `:app:verifyR2M3D1OrdinaryInputQuotaDecision`；
- `:app:verifyR2ProjectCompilerCore`。

## 完成证据

- Targeted `:app:verifyR2M3D1OrdinaryInputQuotaDecision`：331 actionable tasks（114 executed、
  217 up-to-date），`BUILD SUCCESSFUL in 4m 31s`；mutex wrapper 总计 `334.6s`，其中等待
  其他 Gradle transaction 约 64 秒。
- 最终无排除 `:app:verifyR2ProjectCompilerCore`：527 actionable tasks（134 executed、393
  up-to-date），`BUILD SUCCESSFUL in 5m 6s`；mutex wrapper 总计 `409.4s`，其中等待约
  103 秒。
- 两轮均在取得共享 Gradle mutex 后串行执行；本项未运行 ADB、未使用 Android 设备，证据等级为
  source/JVM/Android compile-and-package，而不是 device runtime receipt。
- 4096/64 MiB transport ceiling 与 4607/72 MiB TEST_ONLY Worker capacity 继续独立于 Public
  ordinary quota；四项 compiler capability 与 Node Runtime dependency 共五项 metadata 均为
  `false`，Node revision 2 end-to-end admission 未改变。

pre-C8 common-only historical workload 的降级继续有效：它不能被重新标记为当前 D1 或 M4
receipt。M4 仍须在 frozen schema 与当前 profile declarations 上重建 normal shipping Release
证据。

## 非目标

D1 不冻结 input archive schema v1（M3-S1），不决定 output quota（M3-D2），不推广 Public
capability，不创建 Rhino/Node runtime，也不需要新的 Android device receipt。历史 API 24/35
capacity evidence只支持保守决策；M4 仍须在 frozen schema 上完成 normal shipping Release matrix。
