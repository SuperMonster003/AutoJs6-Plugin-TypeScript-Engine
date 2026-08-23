# AutoJs6 TypeScript Engine Plugin

AutoJs6 的完整 TypeScript 编译前端插件. 插件负责把 TypeScript 项目转换为
JavaScript, Source Map 和结构化诊断, 最终 JavaScript 仍由 AutoJs6 选择的
Rhino 或 Node.js 引擎执行.

> 当前状态（2026-08-23）：新版 Roadmap 的六个必需插件切片 `S1-1`–`S1-6` 已完成。插件现在通过
> `getCompilerInfo()` 广告 `typescriptCompilerAvailable`、项目编译、语义诊断与
> Source Map v3 四项 capability（mask `15`）；输入/输出 ZIP、Worker VFS、模块映射与
> diagnostics URI 已端到端支持安全 UTF-8 路径（仍拒绝控制字符、畸形 Unicode、绝对路径、
> 反斜杠与 `.`/`..` 逃逸）。ordinary quota revision 2 允许 caller 独占
> `512 files / 8 MiB` 输入额度（插件声明由独立 retained-input guardrail 约束），输出为
> `512 artifacts / 16 MiB per JS or map / 64 MiB aggregate`；约 5 MiB source 的公共 Binder
> 编译已在 API 37 模拟器与 Android 12 实机产出超过 2 MiB 的 JavaScript。
> Public Rhino 也已激活 controlled-tsconfig policy revision 2：18 个 boolean 选项真实参与
> effective options，未知或非结构性字段产生有界 warning 后忽略，`paths`、`outFile`、profile
> override 与 transformer authority 仍 fail closed。宿主可从 compiler info 读取 policy revision
> 与 fingerprint，并以 archive 内 hash-bound `tsconfig.json` 发起编译；详见
> [S1-4 Public Controlled tsconfig Activation](docs/architecture/CONTROLLED_TSCONFIG_ACTIVATION_S1_4.md)。
> `strict=false` + warning-ignore 的 focused Public Binder 回归已通过 API 31/API 35 实机与
> API 37 模拟器。
> Public NodeNext revision 2 也已准入：宿主按 compiler info 中独立的
> `nodeProfileRevision/nodeConfigSchemaRevision/nodeConfigKind/
> nodeRuntimeCapabilitiesFingerprint/nodeDeclarationProfileFingerprint` 精确构造请求，
> 即可把 `.ts/.mts/.cts` 编译为 `.js/.mjs/.cjs` 和配对 Source Map。Node revision 1
> 仅保留为 private/debug compatibility default，Node public config 仍为 `none`；插件锁定的
> Node declarations 使用独立 ambient-module 索引，caller 的任意裸包导入仍需既有 package
> authority。`.mts + .cts` 的 focused Public Binder 回归已通过 API 31/API 35 实机与 API 37
> 模拟器；详见
> [S1-5 NodeNext Public Admission](docs/architecture/NODE_NEXT_PUBLIC_ADMISSION_S1_5.md)。
> 默认验证图也已瘦身：`gradlew check --offline` 只运行应用/API 单元测试、统一供应链锁、
> AIDL/API v1 freeze，以及各一次真实 Rhino/NodeNext Worker 编译；历史 lint/APK/release/device/
> `verifyR2M*` 证据任务仍可显式调用。清理全部 project build/cache outputs 后的本地冷基线为
> 116 个任务、28.1 秒；详见
> [S1-6 Daily Verification](docs/architecture/DAILY_VERIFICATION_S1_6.md)。
> `nodeRuntimeRequired` 仍为 `false`。
> 编译服务已经可由宿主集成，但 AutoJs6 主仓库的消费端尚未实现，因此终端用户还不能
> 直接点击运行 `.ts`。当前工作清单以 [ROADMAP.md](ROADMAP.md) 为准。

<details>
<summary>查看旧 R2 路线截至 2026-08-22 的阶段性记录</summary>

> 下述文字是按当时源码状态冻结的历史记录；其中 capability=false、Node revision 2
> Worker-result-only 等结论已由 S1-1–S1-5 取代，不表示当前行为。

> 当前状态（2026-08-22）：R0、R1 已完成；R2 已完成路线 Rebaseline 与 **M2 功能闭环
> 18/18**，并在 **M3 Contract/Output Freeze** 完成 `R2-M3-D1/D2/D3/S1/S2/S3`，M3 当前为 **9/18**；
> 下一活动项为 `R2-M3-S4` manifest identity/pairing freeze（M3 不存在 D4）。后续 critical path 为
> M3 → M4 Final Release Hardening → M5 Public Promotion。
> 四项 compiler capability 与 Node Runtime dependency 共五项 metadata 仍为 `false`，因此尚不可向 AutoJs6 host 宣称项目编译
> 可用。活动清单见 [R2 Roadmap](docs/roadmap/R2.md)，重构前完整记录见
> [R2 Legacy Evidence](docs/roadmap/R2_LEGACY_EVIDENCE.md)。受控配置策略见
> [R2-M2-C1 Controlled tsconfig Policy](docs/architecture/CONTROLLED_TSCONFIG_R2_M2_C1.md)
> 与 [R2-M2-C2 Raw tsconfig Identity/Parser](docs/architecture/CONTROLLED_TSCONFIG_R2_M2_C2.md)
> 以及 [R2-M2-C3 Effective Options Authority](docs/architecture/CONTROLLED_TSCONFIG_R2_M2_C3.md)，
> 再加上 [R2-M2-C4 Compiler Escape Closure](docs/architecture/COMPILER_ESCAPE_CLOSURE_R2_M2_C4.md)，
> [R2-M2-C5 Controlled Package Metadata Transport](docs/architecture/CONTROLLED_PACKAGE_METADATA_R2_M2_C5.md)、
> [R2-M2-C6-A Snapshot Resolver Kernel](docs/architecture/SNAPSHOT_NODE_RESOLUTION_KERNEL_R2_M2_C6A.md)、
> [R2-M2-C6-B Package Target/Parity](docs/architecture/PACKAGE_TARGET_AND_NODE_RESOLUTION_PARITY_R2_M2_C6B.md)、
> [R2-M2-C6-C Worker Resolver](docs/architecture/WORKER_SNAPSHOT_NODE_RESOLUTION_R2_M2_C6C.md)、
> [R2-M2-C6-D Worker Target Transport](docs/architecture/WORKER_PACKAGE_TARGET_TRANSPORT_R2_M2_C6D.md)、
> [R2-M2-C6 Controlled Node Resolution Program](docs/architecture/CONTROLLED_NODE_RESOLUTION_PROGRAM_R2_M2_C6.md)、
> [R2-M2-C7 Controlled Root Selection](docs/architecture/CONTROLLED_ROOT_SELECTION_R2_M2_C7.md)、
> [R2-M2-C8 Declaration Profile Layering](docs/architecture/DECLARATION_PROFILE_LAYERING_R2_M2_C8.md)
> 与 [R2-M2-P1 Provisional NodeNext Profile](docs/architecture/PROVISIONAL_NODENEXT_PROFILE_R2_M2_P1.md)。
> C1-C8、P1、P2、P3、P4、P5、X1、X2 与 X3 均已完成；C6-A-D 是不计入固定分母的前置证据，完整 C6 已完成受控
> compiler-host/Program、typed resolution failure/result 与 clean-successor 闭环，M2 当前为
> **18/18**。已关闭的 `R2-M2-P2` 在保留 P1 Node revision 1 与 C6 resolver v1 regression 的
> 前提下，冻结 `.ts/.mts/.cts` module-kind、resolver v2 与 generated URI/output 规则。P2-0
> 至 P2-2 authority foundation、P2-3A native package-format input authority 与
> P2-3B exact frame/Worker transport-only authority、P2-4 exact Node revision-2 native
> `.mts/.cts` archive/snapshot admission、P2-5 pure resolver/package-target v2 authority，以及
> P2-6 production Worker/NodeNext Program integration、P2-7 Worker framed result/native
> private-output parity 均已完成。P2-7 的 pre-S1 historical receipt 使用 exact
> 20/30/37-field starts、11-key config-none result、4-key outputs 与 legacy 14/27-field terminal；
> S1 后 formal schema-v1 Worker shape 分别为 30/40/47 与 24/37 fields。native 从 exact snapshot 独立复算 10-field
> generated mappings、resolver/target-universe fingerprints，并在 pre-S2 历史实现中写入 private
> manifest revision 3；S2 后所有已打包 profile 统一使用 formal output revision 1。
> Direct TypeScript 6.0.3 harness 已覆盖 7 个 P2 framed results、stable failures 2x2、clean
> successor 与 P1/C6/legacy regression；P2-6 的完整 Core 历史证据为 508 actionable tasks
> （112 executed、396 up-to-date），`BUILD SUCCESSFUL in 3m 25s`。P2-7 最终 Core 证据为
> 509 actionable tasks（112 executed、397 up-to-date），`BUILD SUCCESSFUL in 2m 20s`。P2-8
> final aggregate 已在最终 Core 中通过：515 actionable tasks（101 executed、414 up-to-date），
> `BUILD SUCCESSFUL in 2m 36s`；见
> [P2 design draft](docs/architecture/NODE_MODULE_KIND_OUTPUT_POLICY_R2_M2_P2_DRAFT.md)，
> [P2 authority foundation](docs/architecture/NODE_MODULE_OUTPUT_AUTHORITY_FOUNDATION_R2_M2_P2.md)、
> [P2-3A package-format authority](docs/architecture/NODE_PACKAGE_FORMAT_AUTHORITY_R2_M2_P2_3A.md)、
> [P2-3B package-format transport](docs/architecture/NODE_PACKAGE_FORMAT_TRANSPORT_R2_M2_P2_3B.md)、
> [P2-4 source-kind admission](docs/architecture/NODE_SOURCE_KIND_ADMISSION_R2_M2_P2_4.md)、
> [P2-5 package-target/resolution v2](docs/architecture/NODE_PACKAGE_TARGET_RESOLUTION_V2_R2_M2_P2_5.md)、
> [P2-6 NodeNext Program integration](docs/architecture/NODE_NEXT_PROGRAM_INTEGRATION_R2_M2_P2_6.md)、
> [P2-7 NodeNext result/private output](docs/architecture/NODE_NEXT_RESULT_PRIVATE_OUTPUT_R2_M2_P2_7.md)
> 与 [P2-8 final aggregate](docs/architecture/NODE_MODULE_OUTPUT_FINAL_AGGREGATE_R2_M2_P2_8.md)。
> `R2-M2-P3` 已完成独立 TypeScript 6.0.3 compiler-bundle oracle：Rhino/Node
> 冻结 options 覆盖两文件 import 与 source circular import，mixed `.ts + .js + .json`
> 仅使用 oracle-local `allowJs/resolveJsonModule`，不注册 profile、不进入 production
> archive/Worker/native/result。Direct oracle 已通过 5 cases / 20 compiles，并确认
> `ts.sys`、host filesystem、network 与 output execution 均为 0；最终 Core 为
> 517 actionable tasks（100 executed、417 up-to-date），`BUILD SUCCESSFUL in 2m 37s`。
> P3 本轮未运行 ADB、未使用设备，也未创建 runtime engine。证据见
> [P3 Compiler Bundle Golden](docs/architecture/COMPILER_BUNDLE_GOLDEN_R2_M2_P3.md)。
> `R2-M2-P4` 随后已完成 TypeScript 6.0.3 syntax fixture matrix：enum、namespace、class fields、
> generics、optional chaining、async 与 standard Stage-3 decorators 在 Rhino/Node 两个冻结
> profile 下形成 14 cases / 56 compiles；每 case 为两个 executable `.ts` roots 与 4 个精确
> outputs。P4 direct oracle、JVM contract、final aggregate 与最终 Core 均通过，Core 为
> 519 actionable tasks（103 executed、416 up-to-date），`BUILD SUCCESSFUL in 3m 58s`；未运行
> ADB、未使用设备，也未执行 emitted JavaScript 或创建 runtime engine。证据见
> [P4 Syntax Fixture Matrix](docs/architecture/SYNTAX_FIXTURE_MATRIX_R2_M2_P4.md)。
> `R2-M2-P5` 随后已完成 compiler-boundary fixture matrix：7 类 boundary × Rhino/Node 两个
> 冻结 profile 共 14 rows，12 个 positive rows 通过 declared/reversed × 2 repetitions 的
> 48 次 compiles；canonical collision 与 65,537-token `+1` 各完成 8 次 pre-Program rejection。
> 65,536-token source 可接受，Rhino/Node profile aggregate 分别为 152,036 / 191,040，均低于
> 262,144 上限。当时中文路径仍只在 direct compiler 为 positive，production non-ASCII path
> 继续拒绝；该历史限制已由新版 Roadmap `S1-2` 取代。P5 desktop/JVM/aggregate 与最终 Core
> 均通过；Core 为 521 actionable tasks
> （109 executed、412 up-to-date），`BUILD SUCCESSFUL in 4m 19s`。本阶段未运行 ADB、未使用
> 设备、未执行 emitted JavaScript。证据见
> [P5 Compiler Boundary Fixture Matrix](docs/architecture/COMPILER_BOUNDARY_FIXTURE_MATRIX_R2_M2_P5.md)。
> `R2-M2-X1` 现已在 private same-UID Debug admission over the public-shaped
> Binder/PFD/ticket/ACK/callback surface 上关闭 Rhino revision 1 与 NodeNext revision 1
> deterministic bundle。targeted gate PASS：325 actionable tasks（70 executed、255 up-to-date），
> `BUILD SUCCESSFUL in 3m 38s`；QV710AF65F（Sony XQ-AT72，Android 12 / API 31）
> focused direct instrumentation 为 `Time: 34.639`、`OK (1 test)` 与
> `INSTRUMENTATION_CODE: -1`；最终 Core PASS：522 actionable tasks（101 executed、421
> up-to-date），`BUILD SUCCESSFUL in 5m 1s`。该证据不是 Public promotion；Node revision 2
> 继续 Worker-result-only，external Public、runtime execution 与 Node Runtime dependency 仍关闭。见
> [X1 Public-shaped Deterministic Bundle](docs/architecture/PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。
> `R2-M2-X2` 随后已统一 options/syntactic/global/semantic blocking diagnostics 与
> no-emit invariant。TypeScript 6.0.3 direct evidence 覆盖 4 个 Error phase、first-phase
> precedence、Warning non-blocking 与 6 个 clean successors；targeted X2 gate 为 327 actionable
> tasks（92 executed、235 up-to-date），`BUILD SUCCESSFUL in 5m 43s`。QV710AF65F focused
> Rhino/Node revision-1 source-diagnostics test 为 `Time: 34.657`、`OK (1 test)`、
> `INSTRUMENTATION_CODE: -1`；最终无排除 Core 为 523 actionable tasks（111 executed、412
> up-to-date），`BUILD SUCCESSFUL in 4m 41s`。options/global 仍只使用 desktop defensive seam；
> Node revision 2 继续 Worker-result-only，external Public、runtime execution 与 capability
> metadata 均未开启。见
> [X2 Unified No-emit Diagnostics](docs/architecture/UNIFIED_NO_EMIT_DIAGNOSTICS_R2_M2_X2.md)。
> `R2-M2-X3` 随后精确关闭 accepted authority 后的 11 个 controlled-config policy codes 与
> 18 组 resolver-decision string/numeric pairs。Direct oracle 为 config `11/11`、resolution
> `18/18`、2 个 clean successors，failure Program、snapshot 外 positive reads、`ts.sys`/default
> resolver/network 均为 0；`18/18` 是 pre-Program resolver-decision coverage，不是 18 种
> natural TypeScript Program failures。Targeted X3 gate 为 329 actionable tasks（91 executed、
> 238 up-to-date），`BUILD SUCCESSFUL in 5m 24s`；最终无排除 Core 为 525 actionable tasks
> （113 executed、412 up-to-date），`BUILD SUCCESSFUL in 5m 8s`。本切片无需且未运行新增 ADB
> evidence；Node revision 2 保持 Worker-result-only，Public/runtime 与五项 capability/dependency
> metadata 继续关闭。见
> [X3 Configuration and Resolution Failure Closure](docs/architecture/CONFIGURATION_RESOLUTION_FAILURE_CLOSURE_R2_M2_X3.md)。
> `R2-M3-D1` 随后已冻结首版 ordinary compilation input quota：完整 Worker-retained
> input 共用 **512 files / 8 MiB per file / 8 MiB total**，并精确计入 caller sources、
> trusted declarations、raw config、package metadata 与 package targets v1/v2。Targeted
> `:app:verifyR2M3D1OrdinaryInputQuotaDecision` 为 331 actionable tasks（114 executed、217
> up-to-date），`BUILD SUCCESSFUL in 4m 31s`；mutex wrapper 总计 `334.6s`，其中等待约
> 64 秒。最终无排除 Core 为 527 actionable tasks（134 executed、393 up-to-date），
> `BUILD SUCCESSFUL in 5m 6s`；wrapper 总计 `409.4s`，其中等待约 103 秒。两轮均串行取得
> Gradle mutex，未运行 ADB、未使用设备。4096/64 MiB transport ceiling 与 4607/72 MiB
> TEST_ONLY capacity 不构成 Public ordinary capability；五项 capability/dependency metadata
> 保持 `false`，Node revision 2 end-to-end admission 未改变。pre-C8 common-only workload 仅保留
> 为历史证据，M4 仍须基于 frozen schema 与当前 profile declarations 重建 receipt。见
> [D1 Ordinary Input Quota Decision](docs/architecture/ORDINARY_INPUT_QUOTA_DECISION_R2_M3_D1.md)。
> `R2-M3-D2` 又冻结首版 ordinary emitted output quota：canonical `sourceMappingURL` rewrite
> 后的 final packaged JS/map 合计最多 **512 artifacts / 1 MiB UTF-8 bytes**，单 JS 与单 Source
> Map 各最多 **512 KiB**，所有边界 inclusive。Worker/native parser 以相同数值执行 raw emitted
> text conservative early filter，private packager 是 rewrite 后最终 authority。legacy
> `128 MiB container / 256 MiB expanded` 只属于 transport/ZIP validation ceiling，不是 ordinary
> capacity；diagnostics document 与 typed-result/streaming envelope 分别留给 S3 与 O1。S2 现已把
> D2 quota revision、formal packaged archive identity 与 artifact authority 绑定，S3 只提供
> diagnostics shape 依赖；跨层 failure matrix 的剩余关闭由 S6/O1 继续完成。Targeted
> `:app:verifyR2M3D2OrdinaryOutputQuotaDecision` PASS：333 actionable tasks（89 executed、244
> up-to-date），`BUILD SUCCESSFUL in 3m 38s`；当前 receipt 为
> `build/reports/r2-m3-d2-current-targeted-20260813-162910.log`（SHA-256
> `50944f25396ed8936207e6531030df2f6a512e511b40ef7598b8652c6af8200d`）。IDEA 工具回传虽被
> 用户中断，后台 Gradle 已完整成功，随后也确认无残留 client/mutex；该轮没有虚构 wrapper
> elapsed。最终无排除 Core PASS：529 actionable tasks（116 executed、413 up-to-date），
> `BUILD SUCCESSFUL in 5m 8s`；Gradle wrapper 明确记录 `exit=0`、elapsed `309.2s`、mutex wait
> `32.3s`，receipt 为 `build/reports/r2-m3-d2-current-core-20260813-165526.log`（SHA-256
> `d96c53386f1ac9f88f1847eb240ff416aadd1a7e5a612327729b5845f6270c72`）。成功后的外层
> PowerShell 仅因 `exit$code` 拼写错误报告 postamble 命令错误，不是 Gradle 构建失败。两轮均未
> 运行 ADB、未使用设备；五项
> capability/dependency metadata 保持 `false`，Node revision 2 end-to-end admission 未改变。
> pre-C8 B2K 仅是 historical decision support，不能冒充 current-binary/frozen-schema Release
> acceptance。见
> [D2 Ordinary Output Quota Decision](docs/architecture/ORDINARY_OUTPUT_QUOTA_DECISION_R2_M3_D2.md)。
> `R2-M3-D3` 随后冻结 capability promotion compatibility revision 1：四项 compiler
> capability 按 `C → P → {D, M}` 依赖独立 promotion，16 个 mask 中仅
> `0/1/3/7/11/15` 结构兼容；`typescriptNodeRuntimeRequired` 是独立维度且 revision 1 仅接受
> `false`。Targeted `:app:verifyR2M3D3CapabilityPromotionCompatibilityDecision` PASS：335
> actionable tasks（77 executed、258 up-to-date），`BUILD SUCCESSFUL in 3m 19s`；wrapper
> `exit=0`、elapsed `199.7s`，receipt 为
> `build/reports/r2-m3-d3-targeted-current-20260813-225804.log`（SHA-256
> `044dfec7e72a454965284eb659d33698c77e222276e8bc394cd9b756973d3c19`）。最终无排除 Core
> PASS：531 actionable tasks（112 executed、419 up-to-date），`BUILD SUCCESSFUL in 3m 35s`；
> wrapper `exit=0`、elapsed `215.2s`，receipt 为
> `build/reports/r2-m3-d3-core-current-20260813-230500.log`（SHA-256
> `1cf1a185a2ed32b692e50ca0bf7e8bb5dcddadd1fb08251cb08cd637e778a8a9`）。D3 是
> decision-only producer contract/canonical fixture 与未来 host reference；主 AutoJs6 仓库当前没有
> consumer，不能宣称 host routing 已实现。两轮未运行 ADB、未使用设备；五项 metadata 仍为
> `false`，Node revision 2 继续 Worker-result-only，Public/external/runtime 全部关闭；历史证据不因
> 本项重新定级。见
> [D3 Capability Promotion Compatibility Decision](docs/architecture/CAPABILITY_PROMOTION_COMPATIBILITY_R2_M3_D3.md)。
> `R2-M3-S1` 现已冻结唯一 formal input archive
> `autojs6.typescript.input-archive/revision=1`、八种 exact conditional shapes、caller/local
> declaration/retained 三层 inventory fingerprint、D1 ordinary quota 与 Worker 独立复算；旧 input
> manifest revisions `2..7` 全部 fail closed，不是 revision 1 alias。route classifier 与 production
> Bundle `keySet()` adapter delegation 由行为 gate 绑定；accepted-input authority 六字段 tamper
> 全部在 Program 前拒绝，六个 clean successors 均成功（failure Program=0）。最终 targeted
> `:app:verifyR2M3S1InputArchiveSchemaV1` PASS：337 actionable tasks（82 executed、255
> up-to-date），`BUILD SUCCESSFUL in 3m 10s`；wrapper `exit=0`、elapsed `191.5s`，receipt 为
> `build/reports/r2-m3-s1-targeted-acceptance-20260814-043456.log`（SHA-256
> `0f1c9a491e4a89577842b19d7206f46440c54b7ae5a5e237e615b6ddcb6d2225`）。最终无排除 Core
> PASS：533 actionable tasks（121 executed、412 up-to-date），`BUILD SUCCESSFUL in 3m 30s`；
> wrapper `exit=0`、elapsed `211.2s`，receipt 为
> `build/reports/r2-m3-s1-core-acceptance-20260814-043846.log`（SHA-256
> `6bac7ea5f54c2d03a76551178033cafe2391c229a95bd85342a12985d6fe5a36`）。latest source
> `04:34:22`，两轮分别于 `04:34:56`/`04:38:46` 启动；均未运行 ADB/connected tests、未使用设备，
> AndroidTest 仅编译/组装与 APK 静态门禁、未执行 instrumentation，完成后 Gradle wrapper client
> 为 0。五项 capability/dependency metadata 仍为 `false`，Node revision 2 继续
> Worker-result-only。focused 71-task classification 与此前 targeted/Core 只作为修复过程证据，
> S1 主验收以上述两份 acceptance receipt 为准。见
> [S1 Input Archive Schema v1](docs/architecture/INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。
> `R2-M3-S2` 已冻结唯一 formal output archive
> `autojs6.typescript.output-archive/revision=1`，并把 emitted/diagnostics-only outcome、S1
> accepted-input/compiler provenance、effective-options identity、JavaScript-only module inventory 与
> diagnostics/JS/map artifact authority 绑定到 exact manifest。旧 provisional output revisions
> `2`/`3` fail closed，不是 revision 1 alias；terminal Bundle 仅在成功提交 archive 时携带
> `outputArchiveSchemaRevision=1` 与 exact manifest SHA-256。D2 quota 以
> `outputQuotaRevision=1` 绑定；S2 完成时 diagnostics document 仍为 provisional revision 2，随后已由
> S3 formal schema supersede。
> Targeted `:app:verifyR2M3S2OutputArchiveSchemaV1` PASS：339 actionable tasks（81 executed、258
> up-to-date），`BUILD SUCCESSFUL in 2m 24s`；最终无排除 Core PASS：535 actionable tasks（263
> executed、272 up-to-date），`BUILD SUCCESSFUL in 3m 19s`。两轮均未运行 ADB/connected tests、
> 未执行 instrumentation、未使用设备；五项 capability/dependency metadata 继续为 `false`，Node
> revision 2 仍为 Worker-result/private-output authority。见
> [S2 Output Archive Schema v1](docs/architecture/OUTPUT_ARCHIVE_SCHEMA_V1_R2_M3_S2.md)。
> `R2-M3-S3` 已冻结唯一 formal diagnostics document
> `autojs6.typescript.diagnostics/revision=1`：project-scoped `autojs://source/p-<sha256>/...` URI、
> UTF-16 code-unit range、TypeScript category/code/message、related information 与 canonical ordering。
> ordinary diagnostics quota 为最多 1,000 条 primary、每条/全局最多 1,000 条 related、完整 canonical
> JSON 最多 512 KiB UTF-8；旧 `10,000 / 8 MiB` 仅为 transport/ZIP ceiling。旧
> `autojs6.typescript.diagnostics.provisional@2` fail closed，不是 v1 alias。Targeted
> `:app:verifyR2M3S3DiagnosticsSchemaV1` PASS：348 actionable tasks（88 executed、260 up-to-date），
> `BUILD SUCCESSFUL in 3m`；最终无排除 Core PASS：537 actionable tasks（159 executed、378
> up-to-date），`BUILD SUCCESSFUL in 4m 39s`。全量 app/API JVM 分别为 477/26 tests、0 failures。
> 两轮均未运行 ADB、connected tests 或 instrumentation；五项 capability/dependency metadata 继续为 `false`。见
> [S3 Diagnostics JSON Schema v1](docs/architecture/DIAGNOSTICS_SCHEMA_V1_R2_M3_S3.md)。
> M2 保持 `18/18`；M3 当前为 `9/18`，下一活动项为 `R2-M3-S4`；不存在 `R2-M3-D4`。

</details>

<details>
<summary>查看 R2 重构前的 29 项内部证据摘要</summary>

> 以下内容按旧路线口径保留，只描述已经完成的 implementation/evidence，不表示当前
> 总体进度或 Public promotion 状态。
>
> R0 插件基线与 R1 官方编译器/真实宿主探针均已完成.
> v0.5.0 已加入经过三摘要验证的
> TypeScript 6.0.3 compiler distribution, 真实跨进程 Binder/FD 门禁以及独立
> WebView/Web Worker prewarm probe. Android 12/13 双设备已通过 service
> destruction, renderer recovery, 10 个冷进程, 100 次热调用和 compiler
> service FD、PSS/request correlation、双冷 Rhino、accepted lifecycle 及冻结
> AAR 双向兼容门禁; API 33/WebView 109 也通过 Node/Ace 四种安装态矩阵.
> minified release probe 已在 Android 13 真机通过，API 24/25 Chrome 119
> modern-provider runtime matrix 也已关闭最后一项 R1 退出门禁.
> R2-P1 已把 provisional Rhino project coordinator 接入 release，并加入 additive
> public-shaped request/ACK 与 external-UID fixture；R2-P2 又将 profile/config
> revision、18 项固定 options 与 fingerprint 贯穿 input/Worker/output；R2-P3a
> 再把 production native input 切换为 app-private file-backed VFS spool、
> metadata-only snapshot。R2-P3b-a 现已为
> Worker 内部建立 4607 files/8 MiB per file/72 MiB total、path/directory/token
> 复杂度预算、单份 decoded String/lazy SourceFile 生命周期，以及受 512 MiB
> Node old-space 约束的 64+8 MiB retained-memory gate。R2-P3b-b1 又将内部
> framing 调整为 64 KiB raw/128 KiB envelope，以 DEBUG-only expanded gate 在
> API 24/28/35 完成 exact 64 MiB direct-device capacity，并在 API 24/35 完成
> STORED/DEFLATED inclusive/`+1` Binder 边界；每个成功 project 现在先退休 exact
> WebView realm，再发布终态。R2-P3b-b2a 又以严格的
> ingress/parse/semantic/emit started/completed protocol 和 same-UID `DEBUG`
> phase-entry spin，在 API 24/35 覆盖四阶段 keyed cancel/timeout；API 35 还在
> active exact-64-MiB semantic entry 证明 exact renderer termination、旧 process
> identity 退出与不同 successor identity/recovery。R2-P3b-b2b 接着加入三轮
> repeated exact-64-MiB `DEBUG` Binder success 与自然 ingress cancel 的
> compiler-service/renderer PSS、稳定 FD target multiset、transient
> spool/cursor/Worker successor 门禁，并在 compiler service 冷启动时只清除
> recognized private input/spool/output orphan；无法枚举、删除或复核时 admission
> fail closed。R2-P3b-b2c 进一步冻结 standalone same-signer、distinct-UID
> fixture 的 pre-C8 external production-fence historical pipeline：正确哈希的 invalid ZIP
> accept/ACK 后 exactly once 返回 `ERROR_ARCHIVE_FORMAT` 和 empty output；
> `4,603,850`-byte caller 加当时 common-only trusted `3,784,758` bytes / 108 files
> 为当时 authority 的 exact `8,388,608`-byte boundary，可进入 emitted-output
> payload/inventory/authority 验证；caller `+1` 仍是结构与哈希合法的 archive，
> accept/ACK 后异步返回 `ERROR_ARCHIVE_QUOTA`、零 payload 与 empty output。
> debug 与 minified service release 的 API 24/35 四行 runtime matrix 已全部
> PASS。minified release 首轮同时暴露并关闭两处 production-only 阻塞：API 24
> FD flags 改为 API 30+ public `fcntlInt`、API 24–29 JNI/libc `fcntl`；online
> compiler/supervisor handshake 改用 Binder UID/PID、双方 self identity、固定
> process name、challenge token 与 exact endpoint/death binding，不再跨 PID
> 读取 procfs。fixture release 本身不宣称 minified；internal provisional
> checklist 在该历史切片为 **18/18**。R2-P3b-b2d 接着新增 non-shipping
> `r2CapacityEvidenceDebug` / `r2CapacityEvidenceRelease` app；后者继承 release、
> 经 R8/minify 且 non-debuggable，只是 release-equivalent evidence build，
> 两者都以独立 evidence 文件名生成并声明 TEST_ONLY；它们保持 ordinary app
> application ID/signing，安装必须使用 `-t`，**不是** shipping production
> release。standalone external-UID fixture 在每行先
> 回归 b2c invalid/8 MiB inclusive/`+1`，再覆盖 STORED container exact
> 64 MiB/`+1` 与 DEFLATED expanded exact 64 MiB/`+1`，最后运行普通 8 MiB
> successor。service 对 DEFLATED scope 只认证 1..64 MiB container；expanded
> exact-64 由 fixture 两遍自检与 runtime terminal 共同证明。最终 TEST_ONLY
> artifact 已重建并通过本地 gate，API 24/35 四行与 ordinary Debug/Release
> 回归均已 PASS；该历史切片的 internal provisional evidence 为 **19/19**。
> R2-P3b-b2e 继续在 ordinary same-UID debug artifact 上关闭自然 ingress
> timeout：direct-host exact 64 MiB caller 在 API 24/35 各连续三轮使用固定
> 5 秒 deadline、零 Worker spin、无主动 stop，均于 partial ingress 返回
> `ERROR_TIMEOUT`，平衡并释放 cursor、依次替换 Worker，随后小项目成功并退休
> exact WebView session。Binder exact 64 MiB STORED container 另以固定 10 秒
> deadline 和 public ACK 在两台设备返回 exactly-once timeout、零输出，最终
> active job、命名/remote transient FD 均清零，compiler identity 不变且小后继
> 成功。Binder 证据覆盖 end-to-end natural deadline/final cleanup；partial
> ingress 只由 direct-host telemetry 证明；该历史切片的 internal provisional
> evidence 为 **20/20**。R2-P3b-b2f 随后以 standalone same-signer、
> distinct-UID fixture 在 API 24/35 完成 normal Debug/Release production-fence
> `+1` black-box 回归，以及 TEST_ONLY evidence Debug/minified non-debuggable
> release-equivalent 的 production-fence、STORED container 64 MiB `+1` 与
> DEFLATED expanded 64 MiB `+1` scope-free cleanup matrix；每个 rejection 后都运行
> ordinary `4,603,850`-byte successor。evidence path 在 ACK 前证明 server
> input/output FD 各精确为 1 且 active lease 为 1；不可变 cleanup receipt 在
> terminal callback 前创建、在 callback 后读取并关联，随后连续 5 次证明
> caller/private input/project spool/private output FD 与命名 transient、active
> lease 全部为零，Binder 与安全 process epoch 不变。最终 8/8 runtime 全部 PASS，
> 该历史切片的 internal provisional evidence 为 **21/21**。R2-P3b-b2g 又在
> ordinary same-UID Debug、零 spin、无 timeout 注入的 direct host 上，以
> 300 files / `4,594,999` bytes / `232,249` tokens 的自然 workload 分别推进到
> parse `48/192`、semantic `48/192`、emit `96/384` 后执行 keyed cancellation；
> API 24/35 六案均证明 exact progress/termination receipt、300/300 cursor
> 平衡释放、Worker generation 替换、小项目 successor 以及 host/spool cleanup。
> 该历史切片的 internal provisional evidence 为 **22/22**。R2-P3b-b2h
> 随后只关闭 ordinary same-UID Debug direct-host 的自然 semantic
> request-start absolute-deadline timeout：每台设备先以 512-file workload
> 完成三次完整成功 baseline，从 semantic sequence-2/completed 的严格交集冻结
> 一个 timeout，再执行唯一一次 fault；无测试侧 `requestStop`、retry、spin 或
> expanded admission。修复 completion/deadline 原子决策后，API 24 使用
> `9,331..12,777 ms` 窗口与 `11,054 ms` timeout，API 35 使用
> `37,725..38,828 ms` 窗口与 `38,276 ms` timeout；overshoot 分别为
> `2/0 ms`。两案均返回 timeout/无 compilation；三次 baseline
> 的输出 authority 保持 512 files / `720,866` bytes 与 SHA-256
> `4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1`，
> 并证明 Worker `1→2`、successor 与 cleanup。该历史切片的 internal
> provisional evidence 为 **23/23**。R2-P3b-b2i 接着只关闭 ordinary
> same-UID Debug direct-host 的自然 parse request-start absolute-deadline
> timeout：每台设备先完成两次不进入 timing 样本的成功 precondition，再以三次
> 完整成功 baseline 的 parse sequence-2/completed 严格交集冻结 timeout，最后
> 只执行一次 fault；无测试侧 `requestStop`、retry、spin 或 expanded admission。
> 固定 workload 为 8 roots / 117 files / `4,879,527` bytes / `257,388`
> tokens / 418 frames，resolver depth 为 8、path depth 为 10；baseline 输出
> authority 为 16 files / `3,338` bytes，SHA-256
> `c16cf1e19354655321bc7bd13bea9840900f2f2b2bf67793b4d1b926ee42e6b3`。
> API 24 使用 `2,475..5,949 ms` 窗口与 `4,212 ms` timeout，双侧 margin
> 均为 `1,737 ms`，overshoot 为 `0 ms`，runner 为 `50.885 s`；API 35
> 使用 `11,894..13,164 ms` 窗口与 `12,529 ms` timeout，双侧 margin
> 均为 `635 ms`，overshoot 为 `0 ms`，runner 为 `89.718 s`。两案 fault
> 均停在 parse `2/8` 并返回 `ERROR_TIMEOUT` / null compilation /
> `TIMED_OUT`，同时证明 117/117 cursor 平衡释放、Worker `1→2`、successor
> 与 cleanup。该历史切片的 internal provisional evidence 为 **24/24**。
> R2-P3b-b2j 曾以 ordinary same-UID Debug direct-host 关闭自然 emit
> request-start absolute-deadline timeout；其 8-root / 16-output authority 与
> timing 是 **pre-result-egress completion boundary** 历史证据。当前 emit completion 已扩展到
> ACK-backpressured framed egress，因此 b2j 的旧窗口不再代表现行 phase 边界，
> timing/lifecycle 由 b2k 当前证据取代；该历史切片计数仍为 **25/25**。
> R2-P3b-b2k 接着把自然 emit timeout 推进到 standalone same-signer、
> distinct-UID public Binder pipeline：non-shipping TEST_ONLY evidence Debug 与
> minified/non-debuggable evidence Release 在 API 24/35 组成四行全 PASS 矩阵。
> 固定 authority 为 256 roots / 364 aggregate files；emit sequence-2 为
> `128/512`，成功输出为 512 files / `976,438` bytes，SHA-256
> `2b2a4529552aa3a3f9b33cd2a379240bc2852ca065a33ff884a6160847f1b1de`。
> API 24 Debug、API 35 Debug、API 24 Release-equivalent、API 35
> Release-equivalent 的 strict window / timeout 分别为
> `7,978..9,423 / 8,700 ms`、`21,178..23,383 / 22,280 ms`、
> `5,936..7,464 / 6,700 ms`、`10,044..11,486 / 10,765 ms`。每行都唯一返回
> `INSTRUMENTATION_CODE: -1`，只执行一次 fault，停在 emit `128/512`，发布零
> output 并通过 same-Binder successor。当前 emit 覆盖 Worker 输出校验、hash、
> Source Map/JSON/digest 与 ACK-backpressured framed egress；最后一个 chunk ACK
> 后才发布 `emit completed`，随后才是 `projectResultEnd`。native typed result
> finalizer 不属于 emit；该证据也不宣称 instruction-level `builder.emit`
> interruption。b2k 完成时 internal provisional evidence 为 **26/26**。
> b2k 之后又完成一个**无编号**的 external-UID Binder natural semantic timeout
> follow-up：API 24/35 × TEST_ONLY evidence Debug/minified non-debuggable
> Release-equivalent 四行均唯一返回 `INSTRUMENTATION_CODE: -1`。每行以 2 次成功
> precondition 和 3 次成功 baseline 的 semantic sequence-2/completed 严格交集
> 冻结唯一 timeout，再执行一次 fault；fault 停在 semantic `64/256`，发布零
> output，证明 512/512 cursor 平衡释放、Worker `1→2`、same-Binder successor 与
> cleanup。API 24 正式矩阵固定 Chrome `119.0.6045.194`；恢复 Google WebView
> `53.0.2785.124` 只得到当前 `compiler-host.js` 语法不兼容的 fail-closed 探索，
> 未形成 semantic timing 证据。Release-equivalent 仍是 non-shipping TEST_ONLY
> evidence，不是 normal/shipping Release。该历史切片完成后的 internal provisional evidence 为
> **27/27**。随后第二个**无编号** follow-up 又关闭 external-UID Binder natural
> parse timeout：exact 512-file workload 将 15,000 个 imports 固定为 sequence-2
> 前 9,000 + root 8 后 6,000，并以从 graph370 进入的 low-stack reverse-index
> complete DAG 保留 68,635 条真实边。API 24 Chrome 119 / API 35 WebView 130 ×
> TEST_ONLY evidence Debug/minified non-debuggable Release-equivalent 四行均通过
> 2 preconditions + 3 baselines + 1 parse fault + 1 same-Binder successor；fault
> 固定停在 parse `8/32`，证明 512/512 cursor release、Worker `1→2` 与 five-zero
> cleanup。第三个**无编号** follow-up 随后首次覆盖真正的 normal Release：新增
> 最多 14 条、只含 primitive 的 additive public phase/work progress，在 API 24/35
> 各以 2 preconditions + 3 baselines 选择 strict parse window，再运行唯一 fault 与
> same-Binder successor。两行 fault 都只收到 5 条公开进度并停在 parse `8/32`，
> 返回 timeout 与零输出；target app 均为 non-TEST_ONLY、non-debuggable、经 R8 的
> normal Release，且 DEX 排除 private receipt namespace。该历史证据链最终的 internal provisional
> evidence 为 **29/29**。
> Android production dispatch 仍限制为 512 files、8 MiB/file、8 MiB total；
> `compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、
> `sourceMapV3` 四项 capability 仍为 `false`；按旧口径，Public R2 checklist 为 **0/54**。
> 当前仍未证明 normal/shipping Release 的自然 semantic/emit timeout、
> 真实 LMK/OOM、
> Unicode/high-token/adversarial
> 分布、正式 schema、tsconfig/NodeNext；当时还把 128 MiB container/256 MiB expanded
> file-backed output 列为未证明目标，现已由 D2 明确降级为 legacy ceiling 而非 ordinary target。
> P1/P2/P3a/P3b-a/P3b-b1/P3b-b2a/P3b-b2b/P3b-b2c/P3b-b2d/P3b-b2e/P3b-b2f/P3b-b2g/P3b-b2h/P3b-b2i/P3b-b2j/P3b-b2k
> 与上述无编号 follow-ups 仅供发布前验证，
> 不能解释为 Public R2 或可供宿主执行的 TypeScript 项目能力。

</details>

## 固定基线

- AutoJs6 开发基线: `6.8.0 Alpha7`, build `5253`.
- Android: min SDK `24`, compile/target SDK `36`.
- Native build: Android NDK `28.2.13676358`, CMake `3.22.1`.
- TypeScript: 精确锁定 `6.0.3`.
- 插件 ID / engine: `typescript`.
- 插件 variant: `typescript_6_0_3`.
- Compiler action: `org.autojs.plugin.typescript.COMPILER`.
- Compiler process: `:typescript_compiler`.

## 核心边界

```text
AutoJs6 host
  -> 读取或解密项目并构造受限 VFS archive
  -> TypeScript compiler plugin
       -> JS + Source Map + diagnostics + manifest
  -> AutoJs6 host
       -> Rhino adapter 或 Node adapter
```

- 插件是编译前端, 不是第三个 JavaScript VM.
- 插件不读取宿主私有项目路径, 只消费宿主显式传入的归档.
- 插件不依赖, 不发现, 不启用 Node.js Runtime 插件.
- Node Runtime 未安装或未启用时, Rhino TypeScript 仍应可编译并执行.
- Ace 编辑器和编译插件可以共享 TypeScript 发行包规范, 但不共享 WebView,
  Language Service, AST 或生命周期.
- R1 compiler host 使用受限 HTTPS asset origin 和专用 Web Worker. 它只运行
  固定 golden, 不接收真实项目 VFS, 不执行 emitted JavaScript.
- R2 project path 在同一隔离 host 上接受 framed project VFS，四项 compiler
  capability 已按 S1-1 开启；宿主消费与运行时执行仍待 S2。完整边界见
  [R2 Public-shaped Admission Preview](docs/architecture/PUBLIC_ADMISSION_R2_PREVIEW.md)
  与
  [R2 Profile/Config Authority](docs/architecture/PROFILE_AUTHORITY_R2_PROVISIONAL.md)，
  native input 容量分层见
  [R2-P3a File-backed Native Input](docs/architecture/FILE_BACKED_INPUT_R2_P3A.md)，
  Worker 内部 capacity/lifetime 见
  [R2-P3b-a Worker Input Capacity and Lifetime](docs/architecture/WORKER_INPUT_R2_P3B_A.md)，
  Android DEBUG capacity/realm-retirement 见
  [R2-P3b-b1 Device Capacity and Exact Realm Retirement](docs/architecture/WORKER_INPUT_R2_P3B_B1.md)，
  DEBUG phase-entry/renderer-loss fault 边界见
  [R2-P3b-b2a Phase-entry Hard-stop and Active Renderer-loss](docs/architecture/WORKER_FAULT_R2_P3B_B2A.md)，
  repeated resource/startup orphan-cleanup 边界见
  [R2-P3b-b2b Repeated Resource and Startup Orphan Cleanup](docs/architecture/WORKER_RESOURCE_R2_P3B_B2B.md)，
  external production-fence pipeline 见
  [R2-P3b-b2c External Production-fence Pipeline](docs/architecture/EXTERNAL_FENCE_PIPELINE_R2_P3B_B2C.md)，
  non-shipping external exact-64 capacity pipeline 见
  [R2-P3b-b2d External Exact-64-MiB Capacity Evidence Pipeline](docs/architecture/EXTERNAL_EXACT64_PIPELINE_R2_P3B_B2D.md)，
  natural ingress timeout/final cleanup 见
  [R2-P3b-b2e Natural Ingress Timeout and Cleanup](docs/architecture/WORKER_NATURAL_INGRESS_TIMEOUT_R2_P3B_B2E.md)，
  external plus-one cleanup receipt 见
  [R2-P3b-b2f External Plus-one Cleanup Receipt Evidence](docs/architecture/EXTERNAL_PLUS_ONE_CLEANUP_R2_P3B_B2F.md)，
  ordinary natural parse/semantic/emit cancellation 见
  [R2-P3b-b2g Natural Parse/Semantic/Emit Cancellation](docs/architecture/WORKER_NATURAL_PHASE_CANCELLATION_R2_P3B_B2G.md)，
  ordinary natural semantic request-start timeout 见
  [R2-P3b-b2h Natural Semantic Request-start Absolute-deadline Timeout](docs/architecture/WORKER_NATURAL_SEMANTIC_TIMEOUT_R2_P3B_B2H.md)，
  ordinary natural parse request-start timeout 见
  [R2-P3b-b2i Natural Parse Request-start Absolute-deadline Timeout](docs/architecture/WORKER_NATURAL_PARSE_TIMEOUT_R2_P3B_B2I.md)，
  ordinary natural emit request-start timeout 见
  [R2-P3b-b2j Natural Emit Request-start Absolute-deadline Timeout](docs/architecture/WORKER_NATURAL_EMIT_TIMEOUT_R2_P3B_B2J.md)，
  external-UID Binder natural emit timeout 见
  [R2-P3b-b2k External-UID Binder Natural Emit Timeout](docs/architecture/EXTERNAL_NATURAL_EMIT_TIMEOUT_R2_P3B_B2K.md)，
  无编号 external-UID Binder natural semantic timeout follow-up 见
  [R2 External-UID Binder Natural Semantic Timeout](docs/architecture/EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_R2.md)，
  无编号 external-UID Binder natural parse timeout follow-up 见
  [R2 External-UID Binder Natural Parse Timeout](docs/architecture/EXTERNAL_NATURAL_PARSE_TIMEOUT_R2.md)，
  无编号 normal/shipping Release natural parse timeout follow-up 见
  [R2 Normal/Shipping Release Natural Parse Timeout](docs/architecture/SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_R2.md).
- 首个 GA 的 Rhino profile 支持 `.ts`, Node profile 支持 `.ts/.mts/.cts`.
- 首个 GA 只编译执行前 snapshot; 运行中外部 TS 与 bundled compiler 延至
  Post-GA.
- StackTrace 契约为 Node 文件/行/列, Rhino 文件/行; 两者编译诊断均到列.

## Roadmap

最终集成计划和逐项状态见 [ROADMAP.md](ROADMAP.md).

R1 发行物和受限宿主设计见
[Compiler Distribution v1](docs/architecture/COMPILER_DISTRIBUTION_V1.md) 与
[Restricted Compiler Host R1](docs/architecture/COMPILER_HOST_R1.md).

多语言发行记录见
[CHANGELOG-zh-Hans.md](.changelog/CHANGELOG-zh-Hans.md). 修改
`.changelog/lang_*.json` 后运行:

```powershell
py .python/generate_changelog.py
py .python/generate_changelog.py --check
```

## 构建与验证

```powershell
.\gradlew.bat verifyR0
.\gradlew.bat verifyR1CompilerDistribution
.\gradlew.bat verifyR1CompilerHost
.\gradlew.bat verifyR1ProtocolCompatibility
```

`verifyR1CompilerDistribution` 从 lock 内的精确 npm URL 获取 TypeScript
tarball, 验证 SHA-1, SHA-256 和 SRI SHA-512, 安全生成发行目录, 并重新校验
debug/release APK 中的实际资产字节. `verifyR1CompilerHost` 同时校验 host
JavaScript/CSP/HTTPS allowlist/WebView policy, 无危险 bridge API, 无 INTERNET
权限, 以及 debug/release APK 内 host 与唯一 compiler asset 的实际字节.
`verifyR1ProtocolCompatibility` 校验冻结 v1 AAR、transaction/Bundle fixtures、
compat APK 签名和双变体构建/lint；重冻 AAR 必须另行显式执行，普通 verification
不会覆盖基线. 该聚合任务是静态门禁，不会安装 APK 或操作连接设备. 双向设备门禁
必须先安装当前 app，再安装同签名 compat APK，并串行运行两个 framework-only
instrumentation:

```powershell
.\gradlew.bat verifyR1ProtocolCompatibility
adb -s <serial> install -r -t -d app\build\outputs\apk\debug\autojs6-plugin-typescript-engine-v0.5.0-universal.apk
adb -s <serial> install -r -t -d r1-compat-v1\build\outputs\apk\debug\r1-compat-v1-debug.apk
adb -s <serial> shell am instrument -w -r io.github.supermonster003.autojs6.plugin.typescript.compat.v1/io.github.supermonster003.autojs6.plugin.typescript.compat.v1.FrozenClientToCurrentServiceInstrumentation
adb -s <serial> shell am instrument -w -r io.github.supermonster003.autojs6.plugin.typescript.compat.v1/io.github.supermonster003.autojs6.plugin.typescript.compat.v1.CurrentClientToFrozenServiceInstrumentation
```

两条 runner 都必须唯一返回 `INSTRUMENTATION_CODE: -1`. 2026-07-27 的 API 31/33
双机证据均为 pass；app/compat debug APK 的实际 signer certificate SHA-256 同为
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`.

当前 AutoJs6 Rhino 的可行性探针是 debug-only 且显式 opt-in. 传入的 JAR 必须
匹配 `rhino-probe.lock.json` 的文件名和 SHA-256; 任务会构建 debug target/test
APK, 并同时验证 release APK/manifest 没有 probe service、process 或 Rhino
class 残留:

```powershell
$autoJs6Root = 'D:\path\to\AutoJs6'
.\gradlew.bat verifyR1RhinoProbe `
  "-Pr1RhinoProbeJar=$autoJs6Root\libs\org-mozilla-rhino-2_0_0-SNAPSHOT.jar"
```

连接设备后可只运行 Rhino/WebView 对比、双冷进程和取消 watchdog/rebind 门禁:

```powershell
$autoJs6Root = 'D:\path\to\AutoJs6'
.\gradlew.bat :app:connectedDebugAndroidTest `
  "-Pr1RhinoProbeJar=$autoJs6Root\libs\org-mozilla-rhino-2_0_0-SNAPSHOT.jar" `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.github.supermonster003.autojs6.plugin.typescript.R1RhinoFeasibilityInstrumentationTest"
```

不传 `r1RhinoProbeJar` 时普通 debug/check/connected 流程不会打包 Rhino, 上述
instrumentation 会以 assumption 跳过. 探针的固定预期是 `unsupported`, 它不会
启用 fallback、改变 `backend=webview_worker` / `compilerAvailable=false`, 或
降级 TypeScript 版本.

连接 Android 设备后运行真实 `:typescript_compiler` Binder/FD 与 prewarm
runtime golden 门禁:

```powershell
.\gradlew.bat verifyR1BinderFdDebug
```

当前已确认 Android 12/API 31 与 Android 13/API 33 上的 debug 远程 bind,
每台设备 100 次顺序 prewarm, 1 ms timeout 后的 Worker 重建恢复, 真实
renderer 首次退出后的单次 retry, 第二次退出后的稳定失败与后续恢复, service
destruction 的完整 teardown/rebind, 以及每台设备 10 个冷 compiler process 的
身份, fingerprint 和 FD 边界. API 33/WebView 109 的干净 AVD 还通过了 none,
Node-only, Ace-only 和 Node+Ace 四种安装态. minified release 也已在 Android 13
真机通过. Rhino debug probe 已在 Android 12/API 31、WebView
`145.0.7632.120` 与 Android 13/API 33、WebView `150.0.7871.124` 通过双冷进程
unsupported 与 watchdog/rebind 门禁；两台设备均稳定停在 compiler
`typescript.js:2341:13`，且 `fallbackEligible=false`.

同两台设备还通过了 API 31/33 的 service + renderer PSS 和 distinct-request
correlation 门禁、每台 7 项 debug-only accepted/cancel/timeout/death lifecycle
门禁，以及冻结 client → 当前 service / 当前 AIDL → 冻结 service 的双向
transaction 1--6 compatibility gate. production/release 与外部
`startCompilation` 仍全部拒绝，项目编译能力没有因此开启.

Node/Ace 四态证据可通过显式 opt-in 任务在 disposable emulator 上复现. 任务
不会加入普通 `check`; 它只接受明确的 `emulator-*` serial 与完全匹配的
`MUTATE:<serial>` 确认值, 并在任何设备改动前验证两个 APK 的 package name:

```powershell
.\gradlew.bat :app:verifyR1OptionalPluginMatrix `
  -Pr1MatrixSerial=emulator-5554 `
  -Pr1MatrixNodeApk=D:\apk\node.apk `
  -Pr1MatrixAceApk=D:\apk\ace.apk `
  -Pr1MatrixConfirm=MUTATE:emulator-5554
```

该任务会安装 debug target/test APK, 依次验证 none, Node-only, Node+Ace 和
Ace-only, 最后卸载 Node/Ace 并严格复核 none. 请勿指向保存用户数据的设备.
设备指纹, WebView provider, APK SHA-256 与四次 runner 原始输出保存在
`app/build/reports/r1/optional-plugin-matrix.txt`.

API 24 既有 6 项 Bundle/FD transport 门禁保持通过. 本轮官方 Play Store x86
镜像又确认 API 24/Chrome `51.0.2704.90` 与 API 25/Chrome
`55.0.2883.91` 的 remote Binder transport 均通过，但两个 stock-old provider
都在 `compiler-host.js:73` 因 trailing call comma 报
`Unexpected token )`，随后进入有界 Worker timeout. 这不是 compiler host
兼容证据. 两台 AVD 经 Play Store 更新并明确选中同签名 Chrome
`119.0.6045.194` 后，均通过真实 TypeScript 6.0.3 Worker golden、100 次热
探针/FD、timeout recovery、teardown/rebind 和 10 个冷 compiler process 门禁，
从而关闭最后一项 R1 退出证据. Android 7 上 Chrome 可能稳定保留 compiler
process 自身的 `statm`/`status` 两个只读 procfs telemetry FD；测试只允许这个完整
二元组，其他增长或 staging archive 残留仍失败.
当前 FD 证据不能外推为完整 renderer memory leak 结论.

`release-smoke` 是不依赖 AndroidX runner 的 framework-only helper APK, 用于
验证 R8/minified target. `verifyR1CompilerHost` 会确保 helper 可构建, 但不会
为 unsigned release APK 选择签名证书或操作设备. 真机门禁需先用同一测试证书
签名并安装 release target 与 helper, 再运行:

```powershell
adb shell am instrument -w io.github.supermonster003.autojs6.plugin.typescript.releasesmoke/.R1ReleaseSmokeInstrumentation
```

R0 的细分任务仍可分别运行:

```powershell
.\gradlew.bat :plugin-api:typescript-api:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:verifyOptionalPluginIndependence
.\gradlew.bat :app:verifyCompilerDistributionLock
.\gradlew.bat :app:assembleDebug
```

Debug/release APK 分别输出到 `app/build/outputs/apk/debug/` 与
`app/build/outputs/apk/release/`.

## License

项目使用 [MPL-2.0](LICENSE). TypeScript 发行物使用其上游许可证; R1 开始
打包发行物时必须同时包含 `LICENSE.txt` 和 `ThirdPartyNoticeText.txt`.
