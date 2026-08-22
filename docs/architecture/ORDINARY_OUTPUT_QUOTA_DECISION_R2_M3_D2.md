# R2-M3-D2 Ordinary Output Quota Decision

> **R2-M3-S2 follow-up（2026-08-22）。** S2 已将本页的 D2 decision 绑定为 formal output
> manifest 的 `outputQuotaRevision=1`，并冻结 packaged artifact authority；见
> [S2 Output Archive Schema v1](OUTPUT_ARCHIVE_SCHEMA_V1_R2_M3_S2.md)。下文把 S2 写成未来
> owner 的句子仅是 D2 完成时的历史上下文；D2 数值与 counting domain 不变。

状态：**完成（2026-08-13）**。本页冻结首版 ordinary compilation emitted-output quota 的
数值、计数域与最终判定 authority。Targeted
`verifyR2M3D2OrdinaryOutputQuotaDecision` 与无排除项 `verifyR2ProjectCompilerCore` 已在当前
源码上通过，Roadmap 的 D2 已关闭。

## 结论

首版 ordinary compilation output quota 为：

- 最多 **512 个 emitted artifacts**，JavaScript 与 Source Map 合并计数；
- 单个 packaged JavaScript 最多 **512 KiB UTF-8 bytes**；
- 单个 packaged Source Map 最多 **512 KiB UTF-8 bytes**；
- 全部 packaged JavaScript 与 Source Map 合计最多 **1 MiB UTF-8 bytes**。

四个比较器边界均为 inclusive，前提是其余维度仍符合 quota；任一维度 `+1` 必须 fail
closed、caller output 保持 empty，且不得自动重试。这里的 authoritative bytes 是 private
packager 完成 canonical `sourceMappingURL` rewrite 后将要发布的最终 artifact bytes，不是
Worker 返回的原始文本长度。

## 两层 ceiling 不得混淆

| 层 | 首版边界 | 语义 |
| --- | --- | --- |
| ordinary emitted output | 512 artifacts、512 KiB/JS、512 KiB/map、1 MiB aggregate | caller 可见的最终 packaged JS/map payload；D2 的 Public/native authority |
| v1 output transport/ZIP validation | 8192 archive files、128 MiB container、256 MiB expanded、16 MiB/JS、32 MiB/map、10,000 diagnostics、8 MiB diagnostics、4 MiB manifest | 外层 transport、archive 与 schema validation ceiling；通过不表示 ordinary compilation 可产生该规模 |

`TypeScriptCompilerContract.MAX_OUTPUT_*` 继续保持 legacy v1 compatibility。D2 在 additive
`TypeScriptR2Contract` 中建立独立 output quota revision 与常量；**128 MiB container / 256 MiB
expanded 不是 ordinary capacity target**，也不构成已验证的 end-to-end capacity。packaged
archive 已由 M3-S2 output archive schema 以 `outputQuotaRevision=1` 绑定；M3-S3 只提供其所需的
diagnostics shape 依赖。不能把 legacy ceiling 直接重新命名为首版 ordinary quota。

## Exact counting domain 与 authority

ordinary output quota 只计入：

1. canonical `sourceMappingURL` rewrite 后的 JavaScript UTF-8 bytes；
2. 对应的 Source Map v3 UTF-8 bytes。

它明确不计入 `diagnostics.json`、typed-result JSON/framing/base64 overhead、
`compilation-manifest.json`、ZIP header/metadata 与 container bytes。file count、单 artifact
bytes 与 aggregate bytes 必须从同一最终 payload inventory 派生。

Worker 与 native protocol parser 对 raw emitted text 使用相同数值作保守 early filter，避免明显
超限结果继续扩大 heap 压力；private packager 必须在 rewrite 后重新测量，并且是最终 authority。
因此 raw output 恰好位于边界不保证最终可发布：rewrite 新增的 bytes 可能令单个 JS 或 aggregate
在 staging/commit 前被拒绝。

维度判定顺序固定为 artifact count、按 canonical artifact order 的单 JS/map bytes、aggregate
bytes。最终判定发生在 generated manifest 建立和 caller FD staging 之前，失败不得留下 partial
caller output。

## 独立 guardrail 与下游 owner

D2 不把以下 internal guardrail 混入 Public emitted-output quota：

| guardrail | 当前 internal 值 | D2 状态 | 下游 owner |
| --- | ---: | --- | --- |
| diagnostics count / encoded array | 1000 / 512 KiB | 未冻结为 D2 quota | `R2-M3-S3` diagnostics schema |
| decoded typed result envelope | 6 MiB | Worker/native heap/framing guardrail | `R2-M3-O1` streaming/file-backed output |
| packaged archive/container authority | D2 revision 1 已由 formal output schema 绑定 | 不使用 legacy 128/256 MiB 冒充 | S2 已完成；S3 仅提供 diagnostics shape 依赖，O1 负责 streaming writer |

现有失败 identity 也仍是分层实现事实，而不是已冻结的统一 Public quota code：Worker/host early
quota failure 使用 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_UNAVAILABLE`，native final packaged rejection
使用 `ERR_AUTOJS6_TYPESCRIPT_OUTPUT_TRANSPORT`。两者均返回零 diagnostics、零 caller output、
不自动重试；S2 已冻结 output transport failure/empty-output policy，跨层 failure matrix 的剩余关闭
由 `R2-M3-S6` 与 `R2-M3-O1` 负责。

## Historical workload 的证据等级

pre-C8 external emit workload 曾产生 512 个 raw artifacts / 976,438 bytes；packager rewrite 增加
2,048 bytes 后为 512 个 artifacts / 978,486 bytes，距离 1 MiB aggregate 仍有 70,090 bytes
headroom。它支持 D2 采用 512 / 512 KiB / 1 MiB 的保守决策，但其 declaration authority、源码与
binary 都早于 C8，因此只是 **historical device decision support**，不能标记成当前 binary、frozen
schema 或 M4 normal Release acceptance。M4 必须按届时 frozen schema 与当前 Rhino/Node profile
重新生成 receipt。

其他 pre-current egress workload 只保存 raw Worker bytes，缺少 post-rewrite packaged authority，
同样不能补写成 D2 exact boundary evidence。

## Authority 与回归要求

D2 的 canonical fixture 为 `app/src/test/resources/r2-output-quota/d2-v1.json`，schema identity 为
`autojs6.typescript.r2-m3-d2.ordinary-output-quota-decision`。JVM 与 Gradle 必须独立复核：

- additive Public constants、Worker/parser early filter 与 native final packager 数值一致；
- 512 artifacts、512 KiB JS/map 与 1 MiB aggregate 的 inclusive/`+1` coverage；
- rewrite 前可接受而 rewrite 后单 JS 或 aggregate 超限时，在 staging 前 fail closed；
- diagnostics、typed result、manifest 与 ZIP/container bytes 不进入 D2 counting domain；
- legacy v1 128/256 MiB ceiling 不提升 ordinary capacity，也不定义 packaged archive quota；
- historical B2K 数据保持 pre-C8/non-release 标签；
- failure-code split 与 S2/S6/O1 downstream ownership 可机器复核；
- Public capability、Node Runtime dependency 与 Node revision-2 end-to-end admission 继续关闭。

验证入口：

- `:app:verifyR2M3D2OrdinaryOutputQuotaFixture`；
- `:app:verifyR2M3D2OrdinaryOutputQuotaDecision`；
- `:app:verifyR2ProjectCompilerCore`。

## 完成证据

- Targeted `:app:verifyR2M3D2OrdinaryOutputQuotaDecision`：333 actionable tasks（89 executed、
  244 up-to-date），`BUILD SUCCESSFUL in 3m 38s`。current receipt 为
  `build/reports/r2-m3-d2-current-targeted-20260813-162910.log`，SHA-256 为
  `50944f25396ed8936207e6531030df2f6a512e511b40ef7598b8652c6af8200d`。IDEA 工具回传被
  用户中断，但后台 Gradle 已完整成功；随后确认无残留 Gradle client/mutex。该次工具回传没有
  可信的完整 wrapper elapsed，因此不从 wall time 推算或补写该字段。
- 最终无排除 `:app:verifyR2ProjectCompilerCore`：529 actionable tasks（116 executed、413
  up-to-date），`BUILD SUCCESSFUL in 5m 8s`。Gradle wrapper 明确记录 `exit=0`、elapsed
  `309.2s`、mutex wait `32.3s`；current receipt 为
  `build/reports/r2-m3-d2-current-core-20260813-165526.log`，SHA-256 为
  `d96c53386f1ac9f88f1847eb240ff416aadd1a7e5a612327729b5845f6270c72`。外层 PowerShell 在
  Gradle 成功后因 `exit$code` 拼写错误报告 postamble command error；它不改变 wrapper 的
  `exit=0` 或 Gradle 的 `BUILD SUCCESSFUL` 结论。
- 两轮 gate 均在取得共享 Gradle mutex 后串行执行；本项未运行 ADB、未使用 Android 设备，
  当前证据等级为 source/JVM/Android compile-and-package，而不是 device runtime receipt。
- 四项 compiler capability 与 Node Runtime dependency 共五项 metadata 均为 `false`，Node
  revision 2 end-to-end admission 未改变。

## 非目标

D2 不冻结 output archive schema v1（M3-S2）或 diagnostics schema v1（M3-S3），不完成
streaming/file-backed writer（M3-O1），不统一 Public failure code，不开启 Public capability，也不
创建或执行 Rhino/Node runtime。现有 heap-materialized result 与 packaged `ByteArray` 仍是 O1 的
明确待办；D2 完成只表示数值和最终计数 authority 已决定。
