# AutoJs6 TypeScript Engine Integration Roadmap

此文档是最终路线的索引. 每个阶段的详细任务, 验收门槛和退出条件位于独立的
可勾选 Markdown 文件中.

## 不可变技术决策

- [x] 使用官方 TypeScript Compiler API, 精确锁定 `typescript@6.0.3`.
- [x] TypeScript 只是一等编译语言, 不是 Node.js 的文件扩展名别名.
- [x] Compiler 插件与 Node.js Runtime 插件保持零依赖.
- [x] Rhino 和 Node 使用同一编译协议, 但使用各自显式 compiler profile.
- [x] 首个 GA 中 Rhino profile 支持 `.ts`; Node profile 支持 `.ts/.mts/.cts`.
- [x] Source Map 是强制产物, 不允许用生成 JS 冒充原始 TS 坐标.
- [x] 首个 GA 采用一源文件一 JS 文件, 不 bundle.
- [x] 执行前类型检查和 emit 使用相同 compiler 版本与配置解析规则.
- [x] 大源码, JS, map 和 diagnostics 使用 FD archive, 不放进 Binder Bundle.
- [x] 开发态编译器运行于插件独立进程, 不依赖 Ace 页面或 Node 插件.
- [x] APK 默认 build-time 编译, 最终产物无需携带 TypeScript compiler.
- [x] 禁止项目自定义 transformer, compiler plugin, lifecycle script 和网络访问.
- [x] `.tsx` 不在首个 GA 范围, 除非后续单独冻结 JSX runtime 契约.
- [x] 首个 GA 只编译执行前 snapshot 内的 TS; 运行中出现的外部 TS 明确拒绝.
- [x] Node 运行栈承诺原 TS 文件/行/列; Rhino 运行栈承诺原 TS 文件/行.

## 阶段状态

| 阶段 | 主题 | 前置 | 单人工程周 | 状态 | 详细清单 |
|---|---|---|---:|---|---|
| R0 | 插件基线与协议 | - | 0.5 | 已完成 | [R0](docs/roadmap/R0.md) |
| R1 | 官方编译器发行包与真实宿主探针 | R0 | 1-2 | 已完成 | [R1](docs/roadmap/R1.md) |
| R2 | 生产级项目编译服务 | R1 | 待重估 | 进行中（M3 9/18） | [R2](docs/roadmap/R2.md) |
| R3 | AutoJs6 脚本准备管线与路由 | R2 | 1-2 | 未开始 | [R3](docs/roadmap/R3.md) |
| R4 | Rhino 执行适配 | R3 | 2 | 未开始 | [R4](docs/roadmap/R4.md) |
| R5 | Node 执行适配与旧路径退役 | R3 | 1-2 | 未开始 | [R5](docs/roadmap/R5.md) |
| R6 | Source Map, 缓存, 安全与性能 | R4+R5 | 2-3 | 未开始 | [R6](docs/roadmap/R6.md) |
| R7 | Ace, APK Builder, 加密与后台任务 | R2+R6 | 2-3 | 未开始 | [R7](docs/roadmap/R7.md) |
| R8 | Beta, GA 与迁移清理 | R0-R7 | 1-2 | 未开始 | [R8](docs/roadmap/R8.md) |

R1 当前已在 Android 12/API 31 与 Android 13/API 33 的 debug APK 中完成远程
prewarm, 使用独立 WebView/Web Worker 验证 `ts.version=6.0.3`, enum emit,
structured syntax diagnostic 和 Source Map golden. 每台设备 100 次热探针,
timeout 后的 Worker 重建, renderer 首次退出后的单次 retry/第二次退出后的稳定
失败与后续恢复, service destruction teardown/rebind, 以及 10 个冷 compiler
process 的 fingerprint, identity 和 FD 边界均已通过. Binder 门禁也会在 Bundle
反序列化前拒绝并释放 raw transport FD, 且只接受运行时类型严格为 `String[]`
的字符串数组. API 33/WebView 109 另通过 none, Node-only, Ace-only 和 Node+Ace
四种安装态的相同 runtime golden/fingerprint; 显式 opt-in 的
`:app:verifyR1OptionalPluginMatrix` 可在指定 disposable emulator 上重复并保存
该证据. API 31/33 的 compiler service + renderer PSS/request correlation、
双冷 Rhino unsupported/watchdog/rebind、debug-only accepted/cancel/timeout/death
lifecycle 和冻结 AAR 双向兼容门禁也均已通过. ZIP v1 printable-ASCII container
与跨 Android literal corpus、顶层 envelope v1 现已冻结；项目 manifest、
diagnostics 和 compiler profile revision 仍留给 R2.

官方 Play Store x86 AVD 上的 API 24/Chrome 51 与 API 25/Chrome 55 保留为
stock-old 不兼容负基线；两者只完成 remote Binder transport，并在
`compiler-host.js:73` 报 `Unexpected token )` 后由 bounded Worker timeout
收口. 同一 AVD 更新并明确选中同签名 Chrome `119.0.6045.194` 后，API 24/25
均通过真实 TypeScript 6.0.3 WebView/Worker golden、100 次热探针/FD、timeout
recovery、teardown/rebind 和 10 个冷 compiler process 门禁. 该 matrix 关闭
最后一项退出证据，R1 现已完成.

R1 完成不启用项目 compilation admission；项目 manifest、diagnostics 和
compiler profile revision 仍留给 R2，`compilerAvailable=false` 继续为权威状态.
当前 compiler service FD/PSS guardrail 也不是完整 renderer memory leak 结论.

### R2 当前路线（2026-08-22）

R2 已由单体清单重构为六个有依赖顺序的 milestone。**M0 已证明基础**与
**M1 路线重构**已经完成，**M2 功能闭环**也已完成 18/18；**M3 Contract/Output
Freeze** 已完成 `R2-M3-D1/D2/D3/S1/S2/S3`，当前为 9/18，critical path 切换到 `R2-M3-S4`
manifest identity/pairing freeze（M3 不存在 D4），随后依次进入 **M4 Final Shipping Release Hardening** 与
**M5 Public Promotion/R3 Handoff**。
`R2-M2-C1` controlled tsconfig policy、
`R2-M2-C2` raw config identity/parser wiring 与 `R2-M2-C3` 41-key effective-options
authority，以及 `R2-M2-C4` transformer/plugin/package/Binder/host/network escape closure
和 `R2-M2-C5` revision-4 controlled package metadata transport、完整 `R2-M2-C6`
controlled Node resolution Program、`R2-M2-C7` snapshot-only selector/Program/result/output
闭环、`R2-M2-C8` declaration source lock/profile layering，以及 internal/debug-only、config-none
的 `R2-M2-P1` provisional NodeNext revision 1 均已完成。C6-A-D 依次冻结
resolver kernel、C5-bound
package-target/revision-5 transport、TypeScript 6.0.3 parity、Worker pure resolver 与独立
retained target catalog；这些不计入固定分母的前置切片现已由完整 C6 的受控
compiler-host/Program、typed resolution failure/result 与 clean-successor 闭环承接；P2 关闭时
M2 为 **12/18**。`R2-M2-P2` 已在保留 P1 Node revision 1 与 C6 resolver v1 regression 的
前提下，冻结 `.ts/.mts/.cts` module-kind、resolver v2 与 generated URI/output 规则。P2-0
至 P2-2 authority foundation、P2-3A native package-format input 与
P2-3B exact frame/Worker transport-only authority、P2-4 exact Node revision-2 native
`.mts/.cts` archive/snapshot admission、P2-5 pure resolver/package-target v2 authority，以及 P2-6
production Worker/NodeNext Program integration、P2-7 Worker framed result/native private-output
parity 均已完成。P2-7 的 pre-S1 historical receipt 使用 exact 20/30/37-field ingress、
11-key config-none result、4-key outputs 与 legacy 14/27-field terminal；S1 后 formal schema-v1
Worker shape 分别为 30/40/47 与 24/37 fields。native 从 exact snapshot 独立复算 generated mappings 与
resolver/target-universe authority，并在 pre-S2 历史实现中写入 private manifest revision 3；S2 后
所有已打包 profile 统一使用 formal output revision 1。Direct TypeScript 6.0.3
harness 已覆盖 7 个 P2 framed results、stable failures 2x2、clean successor 与 P1/C6/legacy
regression；P2-6 完整 Core 历史证据为 508 actionable tasks（112 executed、396 up-to-date），
`BUILD SUCCESSFUL in 3m 25s`。P2-7 最终 Core 证据为 509 actionable tasks（112 executed、
397 up-to-date），`BUILD SUCCESSFUL in 2m 20s`。P2-8 final aggregate 已在最终 Core 中通过：
515 actionable tasks（101 executed、414 up-to-date），`BUILD SUCCESSFUL in 2m 36s`。证据见
[Controlled Package Metadata Transport](docs/architecture/CONTROLLED_PACKAGE_METADATA_R2_M2_C5.md)、
[Snapshot Resolver Kernel](docs/architecture/SNAPSHOT_NODE_RESOLUTION_KERNEL_R2_M2_C6A.md)、
[Package Target and Parity](docs/architecture/PACKAGE_TARGET_AND_NODE_RESOLUTION_PARITY_R2_M2_C6B.md)、
[Worker Snapshot Resolver](docs/architecture/WORKER_SNAPSHOT_NODE_RESOLUTION_R2_M2_C6C.md)、
[Worker Target Transport](docs/architecture/WORKER_PACKAGE_TARGET_TRANSPORT_R2_M2_C6D.md)、
[Controlled Node Resolution Program](docs/architecture/CONTROLLED_NODE_RESOLUTION_PROGRAM_R2_M2_C6.md)、
[Controlled Root Selection](docs/architecture/CONTROLLED_ROOT_SELECTION_R2_M2_C7.md)、
[Declaration Profile Layering](docs/architecture/DECLARATION_PROFILE_LAYERING_R2_M2_C8.md)、
[Provisional NodeNext Profile](docs/architecture/PROVISIONAL_NODENEXT_PROFILE_R2_M2_P1.md)、
[Node Module-kind and Output Policy Draft](docs/architecture/NODE_MODULE_KIND_OUTPUT_POLICY_R2_M2_P2_DRAFT.md)、
[P2 Authority Foundation](docs/architecture/NODE_MODULE_OUTPUT_AUTHORITY_FOUNDATION_R2_M2_P2.md)、
[P2-3A Package-Format Authority](docs/architecture/NODE_PACKAGE_FORMAT_AUTHORITY_R2_M2_P2_3A.md)、
[P2-3B Package-Format Transport](docs/architecture/NODE_PACKAGE_FORMAT_TRANSPORT_R2_M2_P2_3B.md)、
[P2-4 Source-kind Admission](docs/architecture/NODE_SOURCE_KIND_ADMISSION_R2_M2_P2_4.md)、
[P2-5 Package-target / Resolution v2](docs/architecture/NODE_PACKAGE_TARGET_RESOLUTION_V2_R2_M2_P2_5.md)、
[P2-6 NodeNext Program Integration](docs/architecture/NODE_NEXT_PROGRAM_INTEGRATION_R2_M2_P2_6.md)、
[P2-7 NodeNext Result and Private Output](docs/architecture/NODE_NEXT_RESULT_PRIVATE_OUTPUT_R2_M2_P2_7.md)
与 [P2-8 Final Aggregate](docs/architecture/NODE_MODULE_OUTPUT_FINAL_AGGREGATE_R2_M2_P2_8.md)。

`R2-M2-P3` 已完成独立 TypeScript 6.0.3 compiler-bundle oracle：Rhino/Node
冻结 options 覆盖两文件 import 与 source circular import，mixed `.ts + .js + .json`
仅使用 oracle-local `allowJs/resolveJsonModule`，不注册 profile，也不进入
archive/Worker/native/result。见
[P3 Compiler Bundle Golden](docs/architecture/COMPILER_BUNDLE_GOLDEN_R2_M2_P3.md)。Direct oracle
已通过 5 cases / 20 compiles，最终 Core 为 517 actionable tasks（100 executed、417 up-to-date），
`BUILD SUCCESSFUL in 2m 37s`；本轮未运行 ADB、未使用设备、未创建 runtime engine。

`R2-M2-P4` 随后已完成 TypeScript 6.0.3 syntax fixture matrix：7 类 syntax × Rhino/Node
两个冻结 profile，共 14 cases / 56 compiles；每 case 使用两个 executable `.ts` roots，精确
生成 2 个 JavaScript 与 2 个 Source Maps。Standard Stage-3 decorators 保持 config-none，
legacy decorator options 未开放。Direct oracle、JVM contract、P4 final aggregate 与最终 Core
均通过；Core 为 519 actionable tasks（103 executed、416 up-to-date），
`BUILD SUCCESSFUL in 3m 58s`。本阶段未运行 ADB、未使用设备、未执行 emitted JavaScript，
也未创建 runtime engine。见
[P4 Syntax Fixture Matrix](docs/architecture/SYNTAX_FIXTURE_MATRIX_R2_M2_P4.md)。

`R2-M2-P5` 随后已完成 TypeScript 6.0.3 compiler-boundary fixture matrix：7 类 boundary ×
Rhino/Node 两个冻结 profile 共 14 rows；12 个 positive rows 通过 48 次 exact compiles，
canonical collision 与 65,537-token `+1` 各完成 8 次 pre-Program rejection。65,536-token source
可接受，Rhino/Node profile aggregate 分别为 152,036 / 191,040，均低于 262,144 上限；中文
路径保持 direct-compiler positive / production printable-ASCII negative。P5 desktop/JVM/aggregate
与最终 Core 均通过；Core 为 521 actionable tasks（109 executed、412 up-to-date），
`BUILD SUCCESSFUL in 4m 19s`。本阶段未运行 ADB、未使用设备、未执行 emitted JavaScript。见
[P5 Compiler Boundary Fixture Matrix](docs/architecture/COMPILER_BOUNDARY_FIXTURE_MATRIX_R2_M2_P5.md)。

`R2-M2-X1` 已在 private same-UID Debug admission over the public-shaped
Binder/PFD/ticket/ACK/callback surface 上关闭 Rhino revision 1 与 NodeNext revision 1
deterministic bundle。targeted gate PASS：325 actionable tasks（70 executed、255 up-to-date），
`BUILD SUCCESSFUL in 3m 38s`；QV710AF65F（Sony XQ-AT72，Android 12 / API 31）
focused direct instrumentation 为 `Time: 34.639`、`OK (1 test)` 与
`INSTRUMENTATION_CODE: -1`；最终 Core PASS：522 actionable tasks（101 executed、421
up-to-date），`BUILD SUCCESSFUL in 5m 1s`。该证据不是 Public promotion；Node revision 2
继续 Worker-result-only，external Public、runtime execution 与 Node Runtime dependency 仍关闭。见
[X1 Public-shaped Deterministic Bundle](docs/architecture/PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。

`R2-M2-X2` 随后已统一 options/syntactic/global/semantic blocking diagnostics 与 no-emit
invariant。TypeScript 6.0.3 direct evidence 覆盖 4 个 Error phase、first-phase precedence、
Warning non-blocking 与 6 个 clean successors；targeted X2 gate PASS：327 actionable tasks
（92 executed、235 up-to-date），`BUILD SUCCESSFUL in 5m 43s`。QV710AF65F focused
Rhino/Node revision-1 source-diagnostics test 为 `Time: 34.657`、`OK (1 test)` 与
`INSTRUMENTATION_CODE: -1`；最终无排除 Core PASS：523 actionable tasks（111 executed、
412 up-to-date），`BUILD SUCCESSFUL in 4m 41s`。options/global 保持 desktop-only；Node
revision 2 继续 Worker-result-only，external Public、runtime execution 与 capability metadata
均未开启。见
[X2 Unified No-emit Diagnostics](docs/architecture/UNIFIED_NO_EMIT_DIAGNOSTICS_R2_M2_X2.md)。
`R2-M2-X3` 随后精确关闭 accepted authority 后的 11 个 controlled-config policy codes 与
18 组 resolver-decision string/numeric pairs。Direct oracle 为 config `11/11`、resolution
`18/18`、2 个 clean successors，failure Program、snapshot 外 positive reads、`ts.sys`/default
resolver/network 均为 0；这里的 `18/18` 是 pre-Program resolver-decision coverage，不是 18 种
natural TypeScript Program failures。Targeted X3 gate 为 329 actionable tasks（91 executed、
238 up-to-date），`BUILD SUCCESSFUL in 5m 24s`；最终无排除 Core 为 525 actionable tasks
（113 executed、412 up-to-date），`BUILD SUCCESSFUL in 5m 8s`。X3 无需且未运行新增 ADB；
Node revision 2 保持 Worker-result-only，external Public/runtime 与五项 capability/dependency
metadata 继续关闭。见
[X3 Configuration and Resolution Failure Closure](docs/architecture/CONFIGURATION_RESOLUTION_FAILURE_CLOSURE_R2_M2_X3.md)。
`R2-M3-D1` 随后已冻结首版 ordinary compilation input quota：完整 Worker-retained input
共用 **512 files / 8 MiB per file / 8 MiB total**，且五域计数包含 caller sources、trusted
declarations、raw config、package metadata 与 package targets v1/v2。Targeted
`:app:verifyR2M3D1OrdinaryInputQuotaDecision` PASS：331 actionable tasks（114 executed、217
up-to-date），`BUILD SUCCESSFUL in 4m 31s`；mutex wrapper `334.6s`，其中等待约 64 秒。最终
无排除 Core PASS：527 actionable tasks（134 executed、393 up-to-date），
`BUILD SUCCESSFUL in 5m 6s`；wrapper `409.4s`，其中等待约 103 秒。两轮均串行取得 Gradle
mutex，未运行 ADB、未使用设备。4096/64 MiB transport ceiling 与 4607/72 MiB TEST_ONLY
capacity 均不是 Public ordinary capability；五项 capability/dependency metadata 继续为
`false`，Node revision 2 end-to-end admission 未改变。pre-C8 common-only workload 仅保留为
历史证据，M4 仍须基于 frozen schema 与当前 profile declarations 重建 receipt。见
[D1 Ordinary Input Quota Decision](docs/architecture/ORDINARY_INPUT_QUOTA_DECISION_R2_M3_D1.md)。
`R2-M3-D2` 又冻结首版 ordinary emitted output quota：canonical `sourceMappingURL` rewrite
后的 final packaged JS/map 合计最多 **512 artifacts / 1 MiB UTF-8 bytes**，单 JS 与单 Source
Map 各最多 **512 KiB**，所有边界 inclusive。Worker/native parser 使用相同数值执行 raw emitted
text conservative early filter，private packager 是 rewrite 后最终 authority。legacy
`128 MiB container / 256 MiB expanded` 仅为 transport/ZIP validation ceiling，不代表 ordinary
capacity；diagnostics document 与 typed-result/streaming envelope 分别延期至 S3 与 O1。S2 现已
绑定 D2 quota revision、formal packaged archive identity 与 artifact authority，S3 只提供
diagnostics shape 依赖；跨层 failure matrix 的剩余关闭由 S6/O1 继续完成。Targeted
`:app:verifyR2M3D2OrdinaryOutputQuotaDecision` PASS：333 actionable tasks（89 executed、244
up-to-date），`BUILD SUCCESSFUL in 3m 38s`；current receipt 为
`build/reports/r2-m3-d2-current-targeted-20260813-162910.log`（SHA-256
`50944f25396ed8936207e6531030df2f6a512e511b40ef7598b8652c6af8200d`）。IDEA 工具回传被
用户中断，但后台 Gradle 完整成功，随后确认无残留 client/mutex；不为该轮虚构 wrapper
elapsed。最终无排除 Core PASS：529 actionable tasks（116 executed、413 up-to-date），
`BUILD SUCCESSFUL in 5m 8s`；Gradle wrapper 明确记录 `exit=0`、elapsed `309.2s`、mutex wait
`32.3s`，current receipt 为 `build/reports/r2-m3-d2-current-core-20260813-165526.log`（SHA-256
`d96c53386f1ac9f88f1847eb240ff416aadd1a7e5a612327729b5845f6270c72`）。成功后的外层
PowerShell `exit$code` 拼写错误只属于 postamble，不是 Gradle 构建失败。两轮均未运行 ADB、未使用设备；五项 metadata
保持 `false`，Node revision 2 end-to-end admission 未改变。pre-C8 B2K 只作为 historical
decision support，不能充当 current-binary/frozen-schema Release acceptance。见
[D2 Ordinary Output Quota Decision](docs/architecture/ORDINARY_OUTPUT_QUOTA_DECISION_R2_M3_D2.md)。
`R2-M3-D3` 随后冻结 capability promotion compatibility revision 1：四项 compiler
capability 按 `C → P → {D, M}` 依赖独立 promotion，exact compatible masks 为
`0/1/3/7/11/15`；Node Runtime dependency 保持独立且 revision 1 只接受 `false`。Targeted
`verifyR2M3D3CapabilityPromotionCompatibilityDecision` PASS：335 actionable tasks（77 executed、
258 up-to-date），`BUILD SUCCESSFUL in 3m 19s`；wrapper `exit=0`、elapsed `199.7s`，receipt
`build/reports/r2-m3-d3-targeted-current-20260813-225804.log`（SHA-256
`044dfec7e72a454965284eb659d33698c77e222276e8bc394cd9b756973d3c19`）。最终无排除 Core
PASS：531 actionable tasks（112 executed、419 up-to-date），`BUILD SUCCESSFUL in 3m 35s`；
wrapper `exit=0`、elapsed `215.2s`，receipt
`build/reports/r2-m3-d3-core-current-20260813-230500.log`（SHA-256
`1cf1a185a2ed32b692e50ca0bf7e8bb5dcddadd1fb08251cb08cd637e778a8a9`）。D3 只是
decision-only producer contract/canonical fixture 与未来 host reference；主 AutoJs6 仓库当前没有
consumer，不能宣称 host routing 已实现。两轮均未运行 ADB、未使用设备；五项 metadata 保持
`false`，Node revision 2 继续 Worker-result-only，Public/external/runtime 均关闭；历史证据未被
重新定级。见
[D3 Capability Promotion Compatibility Decision](docs/architecture/CAPABILITY_PROMOTION_COMPATIBILITY_R2_M3_D3.md)。
`R2-M3-S1` 已冻结唯一 formal input archive
`autojs6.typescript.input-archive/revision=1`、八种 exact conditional shapes、三层 inventory
fingerprint、D1 ordinary quota 与 Worker 独立复算；旧 input manifest revisions `2..7` 均 fail
closed，不是 revision 1 alias。route classifier 与 production Bundle `keySet()` adapter delegation
由行为 gate 绑定；accepted-input authority 六字段 tamper 全部在 Program 前拒绝，六个 clean
successors 均成功（failure Program=0）。最终 targeted `:app:verifyR2M3S1InputArchiveSchemaV1` PASS：337
actionable tasks（82 executed、255 up-to-date），`BUILD SUCCESSFUL in 3m 10s`；wrapper
`exit=0`、elapsed `191.5s`，receipt
`build/reports/r2-m3-s1-targeted-acceptance-20260814-043456.log`（SHA-256
`0f1c9a491e4a89577842b19d7206f46440c54b7ae5a5e237e615b6ddcb6d2225`）。最终无排除 Core
PASS：533 actionable tasks（121 executed、412 up-to-date），`BUILD SUCCESSFUL in 3m 30s`；
wrapper `exit=0`、elapsed `211.2s`，receipt
`build/reports/r2-m3-s1-core-acceptance-20260814-043846.log`（SHA-256
`6bac7ea5f54c2d03a76551178033cafe2391c229a95bd85342a12985d6fe5a36`）。latest source
`04:34:22`，两轮分别于 `04:34:56`/`04:38:46` 启动；均未运行 ADB/connected tests、未使用设备，
AndroidTest 仅编译/组装与 APK 静态门禁、未执行 instrumentation，完成后 Gradle wrapper client 为 0。五项 capability/dependency
metadata 仍为 `false`，Node revision 2 继续 Worker-result-only。
focused 71-task classification 与此前 targeted/Core 只作为修复过程证据，主验收以上述两份
acceptance receipt 为准。见
[S1 Input Archive Schema v1](docs/architecture/INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。
`R2-M3-S2` 已冻结唯一 formal output archive
`autojs6.typescript.output-archive/revision=1`，绑定 emitted/diagnostics-only outcome、S1
accepted-input/compiler provenance、effective-options identity、JavaScript-only module inventory 与
diagnostics/JS/map artifact authority；旧 provisional output revisions `2`/`3` fail closed，不是
revision 1 alias。terminal Bundle 仅在成功提交 archive 时携带
`outputArchiveSchemaRevision=1` 与 exact manifest SHA-256；D2 quota 通过
`outputQuotaRevision=1` 绑定；S2 完成时 diagnostics document 仍为 provisional revision 2，随后已由
S3 formal schema supersede。
Targeted `:app:verifyR2M3S2OutputArchiveSchemaV1` PASS：339 actionable tasks（81 executed、258
up-to-date），`BUILD SUCCESSFUL in 2m 24s`；最终无排除 Core PASS：535 actionable tasks（263
executed、272 up-to-date），`BUILD SUCCESSFUL in 3m 19s`。两轮均未运行 ADB/connected tests、
未执行 instrumentation、未使用设备；五项 capability/dependency metadata 继续为 `false`，Node
revision 2 仍为 Worker-result/private-output authority。见
[S2 Output Archive Schema v1](docs/architecture/OUTPUT_ARCHIVE_SCHEMA_V1_R2_M3_S2.md)。
`R2-M3-S3` 已冻结唯一 formal diagnostics document
`autojs6.typescript.diagnostics/revision=1`：project-scoped canonical URI、UTF-16 code-unit range、
TypeScript category/code/message、related information 与 deterministic ordering。ordinary limits 为
1,000 primary、每条/全局 1,000 related、完整 canonical JSON 512 KiB UTF-8；旧 10,000 / 8 MiB
只属于 transport/ZIP validation。provisional revision 2 与 unknown revisions 均 fail closed。Targeted
`:app:verifyR2M3S3DiagnosticsSchemaV1` PASS：348 actionable tasks（88 executed、260 up-to-date），
`BUILD SUCCESSFUL in 3m`；最终无排除 Core PASS：537 actionable tasks（159 executed、378
up-to-date），`BUILD SUCCESSFUL in 4m 39s`。全量 app/API JVM 分别为 477/26 tests、0 failures。
两轮均未运行 ADB/connected tests/instrumentation、未使用设备；五项 capability/dependency metadata 继续为 `false`。见
[S3 Diagnostics JSON Schema v1](docs/architecture/DIAGNOSTICS_SCHEMA_V1_R2_M3_S3.md)。
M2 保持 **18/18**；M3 当前为 **9/18**，下一活动项为 `R2-M3-S4`；不存在 `R2-M3-D4`。

历史 29/29 只表示既有 implementation/evidence ledger 已全部通过，不再充当剩余工作
分母；旧 Public 0/54 也不再作为总体进度。四项 compiler capability 与 Node Runtime
dependency 共五项 metadata 继续为 `false`，
ordinary production admission 继续限制为 512 files、8 MiB/file、8 MiB total。
当前活动路线见 [R2](docs/roadmap/R2.md)，重构前完整单体路线与设备证据见
[R2 Legacy Evidence](docs/roadmap/R2_LEGACY_EVIDENCE.md)。

<details>
<summary>旧路线的 29 项 implementation/evidence 摘要（历史口径）</summary>

以下段落保留截至 Rebaseline 时的技术证据摘要，不表示当前路线或当前进度分母。

R2 旧路线最后一个编号切片为内部 P3b-b2k，其后又完成一个无编号的
external-UID Binder natural semantic timeout follow-up，以及第二个无编号的
external-UID Binder natural parse timeout follow-up；第三个无编号 follow-up 又把
natural parse timeout 推进到真正的 normal/shipping Release。整体 internal path 中，
Worker 自身已具备 4607 files、8 MiB/file、72 MiB
total 的 direct-framing envelope，并加入 path/directory/token 复杂度预算、单份
decoded String、lazy per-job `SourceFile` cache、incremental diagnostics 与
`finally` release。受 512 MiB Node old-space 限制的 desktop gate 覆盖单次
64 MiB caller + 8 MiB trusted boundary、forced-GC retained plateau 和小型
successor。P3b-b1 通过 DEBUG-only expanded gate、64 KiB raw framing 与 exact
success realm retirement，在 API 24/28/35 完成 direct 64 MiB device capacity，
并在 API 24/35 完成 STORED/DEFLATED inclusive/`+1` Binder 边界。P3b-b2a
再以 strict ingress/parse/semantic/emit started/completed protocol 与 same-UID
`DEBUG` synthetic phase-entry spin，在 API 24/35 覆盖四阶段 cancel/timeout；
API 35 另在 active exact-64-MiB semantic entry 证明 exact renderer
termination、旧 `(pid,starttime)` 退出与不同 successor identity/recovery。
P3b-b2b 又加入三轮 repeated exact-64-MiB `DEBUG` Binder success 与自然 ingress
cancel 的 compiler-service/renderer PSS、稳定 FD target multiset、transient
spool/cursor/Worker successor 门禁，以及 compiler service 冷启动 recognized
private input/spool/output orphan cleanup；无法枚举、删除或复核时 admission
  fail closed。P3b-b2c 又冻结 standalone same-signer、distinct-UID fixture 的
  pre-C8 external production-fence historical 三路径：正确哈希的 invalid ZIP 在 accept/ACK 后
exactly once 返回 `ERROR_ARCHIVE_FORMAT` 与 empty output；`4,603,850`-byte
  caller 加当时 common-only trusted `3,784,758` bytes / 108 files 为当时 authority 的 exact
  8 MiB boundary，并验证 emitted diagnostics/JS/map/manifest 的 payload SHA、
inventory 与 authority；结构/哈希合法的 caller `+1` 在 accept/ACK 后异步返回
`ERROR_ARCHIVE_QUOTA`、零 payload 与 empty output。debug 与经 R8/minify 的
service release API 24/35 四行 runtime matrix 已全部 PASS；首轮 release
运行暴露的 API 24 FD-procfs 与 API 35 sibling-procfs registration 阻塞已分别由
public/JNI libc `fcntl(F_GETFL)` 和 Binder UID/PID + self identity + token/
endpoint-death handshake 关闭。fixture release 本身不宣称 minified。P3b-b2d
又增加 non-shipping `r2CapacityEvidenceDebug` /
`r2CapacityEvidenceRelease` app；后者继承 release、经 R8/minify 且
non-debuggable，只是 release-equivalent evidence build，不是 shipping
production release。两者都以独立 evidence 文件名生成并声明 TEST_ONLY，保持
ordinary app application ID/signing，安装必须使用 `-t`。standalone
external-UID fixture 在每行回归 b2c ordinary
invalid/inclusive/`+1` 后，覆盖 STORED container exact 64 MiB/`+1` 与 DEFLATED
expanded exact 64 MiB/`+1`，最后运行 ordinary production-fence successor。
service 对 DEFLATED scope 只认证 1..64 MiB container；expanded exact-64 由
fixture 两遍自检与 runtime terminal 共同证明。最终 TEST_ONLY artifact 已重建
并通过本地 gate，API 24/35 四行与 ordinary Debug/Release 回归均已 PASS。
P3b-b2e 再以 ordinary same-UID debug artifact 在 API 24/35 各完成 direct-host
三轮 exact-64-MiB partial ingress 自然 timeout：固定 5 秒 deadline、零 spin、
无主动 stop，cursor 全部平衡/释放、Worker 依次替换且小后继成功；Binder
exact-64-MiB STORED container 另以固定 10 秒 deadline/public ACK 返回
exactly-once timeout 与零输出，并在 terminal 后证明 active job、命名/remote
transient FD 最终清零、compiler identity 不变和小后继成功。Binder 单项覆盖
end-to-end natural deadline/final cleanup，partial ingress 由 direct-host
telemetry 证明；该 b2e 历史窄矩阵自身仍不直接证明 `+1` remote transient
cleanup。P3b-b2f 随后在 API 24/35 以 normal Debug/Release 回归 ordinary
production-fence `+1`，并以 TEST_ONLY evidence Debug/minified non-debuggable
release-equivalent 覆盖 scope-free production-fence、STORED container 64 MiB
`+1`、DEFLATED expanded 64 MiB `+1`。evidence path 在 ACK 前证明 active
lease 与精确 input/output FD ownership；cleanup receipt 在 callback 前创建、
在 callback 后读取并关联，随后连续 5 次观测全部 transient/lease 为零，同一
Binder/process epoch 下每案 ordinary successor 均成功。API 24/35 共 8 行全部
PASS。P3b-b2g 随后在 ordinary same-UID Debug direct host 上，以零 spin、无
timeout 注入的 300-file natural workload 分别推进到 parse `48/192`、semantic
`48/192`、emit `96/384` 后执行 keyed cancellation。API 24/35 六案全部证明
exact progress/termination receipt、300/300 cursor 平衡释放、Worker replacement、
小项目 successor 与 host/spool cleanup。P3b-b2h 接着只在 ordinary same-UID
Debug direct host 上关闭自然 semantic request-start absolute-deadline timeout：
每台设备先完成三次完整成功 baseline，以 semantic sequence-2/completed 的严格
交集冻结 timeout，再运行一次 fault；不使用测试侧 `requestStop`、retry、spin 或
expanded admission。API 24 的窗口/timeout 为
`9,331..12,777 / 11,054 ms`，API 35 为
`37,725..38,828 / 38,276 ms`；两案均证明 timeout/无 compilation、Worker
`1→2`、successor 与 cleanup。P3b-b2i 接着只在 ordinary same-UID Debug
direct host 上关闭自然 parse request-start absolute-deadline timeout：每台
设备先完成两次不参与 timing 的成功 precondition，再以三次完整成功 baseline
的 parse sequence-2/completed 严格交集冻结 timeout，最后只运行一次 fault；
不使用测试侧 `requestStop`、retry、spin 或 expanded admission。固定 workload
为 8 roots / 117 files / `4,879,527` bytes / `257,388` tokens / 418 frames，
resolver depth 为 8、path depth 为 10；16-file 输出为 `3,338` bytes，SHA-256
为 `c16cf1e19354655321bc7bd13bea9840900f2f2b2bf67793b4d1b926ee42e6b3`。
API 24 的窗口/timeout 为 `2,475..5,949 / 4,212 ms`，双侧 margin
`1,737 ms`、overshoot `0 ms`、runner `50.885 s`；API 35 为
`11,894..13,164 / 12,529 ms`，双侧 margin `635 ms`、overshoot `0 ms`、
runner `89.718 s`。两案 fault 均停在 parse `2/8`，返回
`ERROR_TIMEOUT` / null compilation / `TIMED_OUT`，并证明 117/117 cursor
平衡释放、Worker `1→2`、successor 与 cleanup。P3b-b2j 以 ordinary same-UID
Debug direct host 取得的 8-root / 16-output 自然 emit timeout 与 timing 属于
**pre-result-egress completion boundary** 历史证据。当前 emit completion 已扩展到
ACK-backpressured framed egress，因此旧窗口不再代表现行 phase 边界；当前
timing/lifecycle 由 b2k 证据取代。
P3b-b2k 接着把 emit timeout 推进到 standalone same-signer、distinct-UID public
Binder pipeline：API 24/35 × TEST_ONLY evidence Debug/minified non-debuggable
evidence Release 四行全部 PASS。固定 workload 为 256 roots / 364 aggregate
files，emit sequence-2 为 `128/512`；成功 authority 为 512 outputs /
`976,438` bytes，SHA-256
`2b2a4529552aa3a3f9b33cd2a379240bc2852ca065a33ff884a6160847f1b1de`。
API 24 Debug、API 35 Debug、API 24 Release-equivalent、API 35
Release-equivalent 的 strict window / timeout 分别为
`7,978..9,423 / 8,700 ms`、`21,178..23,383 / 22,280 ms`、
`5,936..7,464 / 6,700 ms`、`10,044..11,486 / 10,765 ms`。四行均唯一返回
`INSTRUMENTATION_CODE: -1`，只执行一次 fault，停在 emit `128/512`，零 output，
且 same-Binder successor 成功。emit 当前覆盖完整 Worker 输出构造与
ACK-backpressured framed egress，最后一个 chunk ACK 后才 completed，随后发送
`projectResultEnd`；native typed finalizer 不属于 emit，也不宣称 instruction-level
`builder.emit` interruption。b2k 后的无编号 follow-up 再用同签名、distinct-UID
fixture 在 API 24/35 × TEST_ONLY evidence Debug/minified non-debuggable
Release-equivalent 完成自然 semantic timeout 四行矩阵。每行以 2 次成功
precondition 与 3 次成功 baseline 的 semantic sequence-2/completed 严格交集
冻结唯一 timeout，只执行一次 fault；fault 停在 semantic `64/256`，返回零
output，并证明 512/512 cursor 平衡释放、Worker `1→2`、same-Binder successor 与
cleanup。API 24 正式矩阵使用 Chrome `119.0.6045.194`；恢复 Google WebView
`53.0.2785.124` 只得到当前 `compiler-host.js` 语法不兼容的 fail-closed 探索，
未形成正式 semantic timing。Release-equivalent 仍是 non-shipping TEST_ONLY
evidence，不是 normal/shipping Release。第二个无编号 follow-up 又以 exact
512-file workload、sequence-2 前 9,000 / root 8 后 6,000 imports 与从 graph370
进入的 low-stack reverse-index complete DAG，在相同四行边界完成自然 parse
timeout；fault 均停在 parse `8/32`，证明 512/512 cursor release、Worker `1→2`、
five-zero cleanup 与 same-Binder successor。normal shipping Release 不得包含
private receipt namespace。第三个无编号 follow-up 因此改用最多 14 条 primitive-only
public phase/work progress，在 API 24/35 的 normal Release 各完成 2+3 strict
calibration、唯一 parse fault 与 same-Binder successor；fault 均以 5 条公开进度停在
parse `8/32`，返回 timeout 与零输出，normal Release DEX 同时排除 private receipt
prefix。normal/shipping Release 的自然 semantic/emit timeout、真实 LMK/OOM、
Unicode/high-token/adversarial 与完整 device matrix 仍未证明；当时还把 128 MiB container /
256 MiB expanded file-backed output 列为目标，现已由 D2 明确降级为 legacy ceiling 而非
ordinary target。
Android production dispatch 继续限制为 512 files、
8 MiB/file、8 MiB total；`compilerAvailable`、`projectCompilation`、
`semanticDiagnostics`、`sourceMapV3` 四项 capability 仍为 `false`；按旧口径，
Public checklist 为 **0/54**、internal provisional checklist 为 **29/29**。完整边界见
[R2-P3b-a Worker Input Capacity and Lifetime](docs/architecture/WORKER_INPUT_R2_P3B_A.md)
与
[R2-P3b-b1 Device Capacity and Exact Realm Retirement](docs/architecture/WORKER_INPUT_R2_P3B_B1.md)，
phase/fault slice 见
[R2-P3b-b2a Phase-entry Hard-stop and Active Renderer-loss](docs/architecture/WORKER_FAULT_R2_P3B_B2A.md)，
repeated resource/startup orphan-cleanup slice 见
[R2-P3b-b2b Repeated Resource and Startup Orphan Cleanup](docs/architecture/WORKER_RESOURCE_R2_P3B_B2B.md)，
external production-fence slice 见
[R2-P3b-b2c External Production-fence Pipeline](docs/architecture/EXTERNAL_FENCE_PIPELINE_R2_P3B_B2C.md)，
non-shipping external exact-64 slice 见
[R2-P3b-b2d External Exact-64-MiB Capacity Evidence Pipeline](docs/architecture/EXTERNAL_EXACT64_PIPELINE_R2_P3B_B2D.md)，
natural ingress timeout/final cleanup slice 见
[R2-P3b-b2e Natural Ingress Timeout and Cleanup](docs/architecture/WORKER_NATURAL_INGRESS_TIMEOUT_R2_P3B_B2E.md)，
external plus-one cleanup receipt slice 见
[R2-P3b-b2f External Plus-one Cleanup Receipt Evidence](docs/architecture/EXTERNAL_PLUS_ONE_CLEANUP_R2_P3B_B2F.md)，
ordinary natural phase cancellation slice 见
[R2-P3b-b2g Natural Parse/Semantic/Emit Cancellation](docs/architecture/WORKER_NATURAL_PHASE_CANCELLATION_R2_P3B_B2G.md)，
ordinary natural semantic timeout slice 见
[R2-P3b-b2h Natural Semantic Request-start Absolute-deadline Timeout](docs/architecture/WORKER_NATURAL_SEMANTIC_TIMEOUT_R2_P3B_B2H.md)，
ordinary natural parse timeout slice 见
[R2-P3b-b2i Natural Parse Request-start Absolute-deadline Timeout](docs/architecture/WORKER_NATURAL_PARSE_TIMEOUT_R2_P3B_B2I.md)，
ordinary natural emit timeout slice 见
[R2-P3b-b2j Natural Emit Request-start Absolute-deadline Timeout](docs/architecture/WORKER_NATURAL_EMIT_TIMEOUT_R2_P3B_B2J.md)，
external-UID Binder natural emit timeout slice 见
[R2-P3b-b2k External-UID Binder Natural Emit Timeout](docs/architecture/EXTERNAL_NATURAL_EMIT_TIMEOUT_R2_P3B_B2K.md)，
无编号 external-UID Binder natural semantic timeout follow-up 见
[R2 External-UID Binder Natural Semantic Timeout](docs/architecture/EXTERNAL_NATURAL_SEMANTIC_TIMEOUT_R2.md)，
无编号 external-UID Binder natural parse timeout follow-up 见
[R2 External-UID Binder Natural Parse Timeout](docs/architecture/EXTERNAL_NATURAL_PARSE_TIMEOUT_R2.md)，
无编号 normal/shipping Release natural parse timeout follow-up 见
[R2 Normal/Shipping Release Natural Parse Timeout](docs/architecture/SHIPPING_RELEASE_NATURAL_PARSE_TIMEOUT_R2.md).

</details>

原路线的“R2 需 2–3 周、全程需 13–20 周”估计已失效，不再作为交付承诺。C1 已明确
配置范围，`R2-M3-D1/D2/D3/S1/S2` 也已冻结 ordinary 输入/输出 quota、capability
compatibility、formal input schema 与 packaged output archive authority；待 S3 diagnostics shape 与
O1 writer 进一步收敛后再重估
剩余工期。后续阶段只在前置退出条件
满足后推进，可并行部分仍以各路线依赖为准。

## 目标架构

```text
ScriptExecutionTask
        |
        v
Host-side project snapshot / editor overlay / decrypted source
        |
        v
ITypeScriptCompilerPlugin (FD archive transport)
        |
        v
CompilationBundle
  - compilation-manifest.json
  - modules/**
  - maps/**
  - diagnostics.json
        |
        +-------------------+
        |                   |
        v                   v
Rhino adapter          Node adapter
        |                   |
        +---- mapped stack--+
                  |
           Console / Editor
```

## GA 统一验收

- [ ] Node Runtime 插件缺失或禁用时, Rhino TypeScript 可运行.
- [ ] Ace 插件缺失, LSP 关闭或编辑器未打开时, TypeScript 可运行.
- [ ] 支持矩阵内的 `.ts/.mts/.cts` 全部经过官方编译器, 不再经过正则 stripping.
- [ ] enum, namespace, class fields 和启用后的 decorators 可正确 emit.
- [ ] 项目级语义诊断和 `noEmitOnError` 生效.
- [ ] Node CJS/ESM 与 Rhino CommonJS 具有真实多文件项目测试.
- [ ] Node/Rhino 编译错误均回到原 TS 文件, 行和列.
- [ ] Node 运行错误回到原 TS 文件, 行和列.
- [ ] Rhino 运行错误回到原 TS 文件和行; 首个 GA 不宣称运行栈列号.
- [ ] wrapper/bootstrap 后的双层 Source Map 可组合.
- [ ] CRLF, BOM, Unicode, 中文路径和空格路径通过.
- [ ] map 路径逃逸, archive bomb, 畸形 map 和超限输入安全失败.
- [ ] APK 不包含非预期 TS 源码或明文临时缓存.
- [ ] 打包 JS 不依赖 TypeScript compiler 即可运行.
- [ ] Ace 升级为项目级 VFS/模块解析后, 与运行前诊断在相同配置下保持一致.
- [ ] 支持矩阵, 安全边界, 性能报告和迁移说明齐备.
