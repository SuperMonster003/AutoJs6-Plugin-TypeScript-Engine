# R2-M3-D3 Capability Promotion Compatibility Decision

状态：**完成（2026-08-13）**。本页冻结四项 compiler capability 的首版依赖关系、兼容状态与
未来 host route 判定规则。canonical fixture、additive Public API、producer metadata、targeted
gate 与无排除 Core 已全部通过；D3 是 decision-only contract/source/fixture closure，不改变任何
capability、Public admission 或 runtime 状态。

> 后续状态（2026-08-23）：[S 时代 Roadmap](../roadmap/ROADMAP_LEGACY_S_ERA.md) 的 S1-1 已将生产 metadata
> 提升为 compiler capability mask `15`，`nodeRuntimeRequired=false`。本页原有的 M4/M5
> promotion 前置流程与“当前 mask 0”描述仅记录 2026-08-13 的历史状态，不再构成发布门禁；
> revision 1 的结构兼容矩阵继续有效。

## 决策

四项 compiler capability **按依赖独立 promotion**，但不是四个可以任意组合的开关：

- `C`（bit `1`）：`typescriptCompilerAvailable`，依赖图的 root；
- `P`（bit `2`）：`typescriptProjectCompilation`，要求 `C`；
- `D`（bit `4`）：`typescriptSemanticDiagnostics`，要求 `C + P`；
- `M`（bit `8`）：`typescriptSourceMapV3`，要求 `C + P`。

`D` 与 `M` 互不依赖，因此 diagnostics 与 Source Map 可以在 project compilation 之后分别
promotion 或 rollback。由此，compatibility revision 1 只接受以下六个 mask；mask 是按上述 bit
从四个 Boolean 派生的判定值，不是另一个可由 metadata 独立声明的 authority：

| mask | C | P | D | M | 允许的能力形状 |
| ---: | :---: | :---: | :---: | :---: | --- |
| `0` | false | false | false | false | disabled / discovery-only |
| `1` | true | false | false | false | compiler discovery、bind/prewarm |
| `3` | true | true | false | false | project compilation |
| `7` | true | true | true | false | project compilation + semantic diagnostics |
| `11` | true | true | false | true | project compilation + Source Map v3 |
| `15` | true | true | true | true | 完整 compiler-service shape |

其余十个四位 mask 全部 fail closed。特别是任何 `P=true, C=false`，或任何
`D/M=true, P=false` 的组合都不允许。关闭 `C` 时必须同时关闭 `P/D/M`；关闭 `P` 时必须先关闭
`D/M`。这冻结的是结构兼容关系，不代表某一允许状态已经满足 promotion evidence。

compatibility key 固定为 `capabilityPromotionCompatibilityRevision`，revision 固定为 `1`。
revision 1 不改变 frozen v1 AIDL/Binder transaction surface，只在既有 primitive-only Bundle
metadata 上增加可忽略的 additive key。

## Strict metadata 与 legacy exception

理解 revision 1 的 consumer 必须按 Bundle 的真实存储类型解析，禁止字符串、数值或 truthy
coercion：

- revision key 存在时必须是 exact `Int(1)`；`Long(1)`、字符串 `"1"`、Boolean、零、负数或
  任意未知正 revision 都 fail closed；
- 四项 compiler capability 与 `typescriptNodeRuntimeRequired` 都必须是 exact Boolean；任何已知
  key 缺失或 wrong-typed 都 fail closed；
- revision 1 必须先验证完整五项 Boolean，再派生 `C/P/D/M` mask；mask 合法也不能掩盖错误类型、
  缺失 key 或 Node dependency 不兼容；
- bounded unknown primitive key 可以遵循 v1 additive-Bundle 规则被忽略，但不得被推断为已知
  capability、compatibility revision、依赖满足或 route authority；既有 metadata budget、FD 与
  primitive-only validation 继续独立生效。

唯一的 revision-absent legacy exception 是：四项 frozen compiler capability 都存在、都是 exact
Boolean `false`；`typescriptNodeRuntimeRequired` 可以缺失并归一为 `false`，若存在则必须是 exact
Boolean `false`。consumer 可以把它识别为 disabled / discovery-only provider，以便显示插件身份或
升级提示，但不得 bind/prewarm、提交 compiler request、推断部分能力或把缺少 revision 当作
revision 1。无 revision 时任何 capability 或 Node dependency 为 `true`、四项 frozen capability
缺失或 wrong-typed、或存在但 wrong-typed 的 Node dependency，均 fail closed。

这个例外只保护旧版“全部关闭”的安全行为；它不是 legacy provider 的隐式 promotion，也不能用于
绕过新 revision。rollback 到 mask `0` 的新 producer 仍必须发布 revision 1，而不是主动删掉 revision
key。

## `NODE_RUNTIME_REQUIRED` 是独立依赖维度

`typescriptNodeRuntimeRequired` 不进入 `C/P/D/M` 四位 mask。它表达 **compiler service 自身**
是否必须先取得 Node Runtime provider，并不表达 emitted JavaScript 最终选择 Rhino 还是 Node.js。

当前 TypeScript compiler bundle 在隔离 WebView Worker 内自包含；Rhino 与 NodeNext profile 的
编译都不发现、不绑定、不启用 Node Runtime。因此 revision 1 只兼容
`typescriptNodeRuntimeRequired=false`，当前 producer 也必须继续发布 `false`。即使 NodeNext
revision 2 能产生 `.js/.mjs/.cjs`，也不能把这一编译产物事实改写为 compiler 的 Node Runtime
依赖。

如果未来 compiler service 本身真正依赖 Node Runtime，必须定义新的 compatibility revision、
依赖/选择协议和相应 release evidence；不得在 revision 1 下直接把该字段翻为 `true`。Node Runtime
provider 选择和 emitted artifact 的 runtime routing 仍属于 R3/R5，不由 D3 实现。

## Host route compatibility

consumer 必须先验证 metadata 类型、revision、Node dependency 和完整 mask，再对**本次请求所需
route**判定最小能力；不能只检查任意一个 `true`，也不能用一个高层能力反向修补缺失的依赖：

| host route | revision 1 最小 mask / 条件 | 不满足时的规则 |
| --- | --- | --- |
| identity/display discovery | mask `0` legacy exception，或任一合法 revision-1 mask | 只允许读取/展示身份；mask `0` 不产生 operational route |
| compiler bind/prewarm | `C`，即合法 mask `1/3/7/11/15` | 不绑定、不预热；保持 provider unavailable |
| ordinary project compilation | `C + P`，即 `3/7/11/15` | 请求在 dispatch 前 fail closed |
| project + semantic diagnostics | `C + P + D`，即 `7/15` | 不得假设 compilation 自动包含公开 diagnostics contract |
| project + Source Map v3 | `C + P + M`，即 `11/15` | 不得假设 emitted JS 自动附带可消费 Source Map |
| project + diagnostics + Source Map | exact `15` | 不拆分、降级或 dispatch 后自动重试 |

合法 metadata 只证明 producer 宣称了结构上可理解的能力。Host 仍须独立验证 exact provider/
component、UID/signer、contract/hash、schema/profile、transport、distribution、service health 与该
route 的 release policy。请求尚未 dispatch 时可以选择另一个明确兼容的 provider；一旦 dispatch，
不得因为 terminal failure 自动 replay 或静默降级。

## Metadata advertisement 不等于 server admission

`PluginInfo.capabilities` / `getCompilerInfo()` 是 discovery 与 routing metadata；它不是 server 对
某个 request 的 ACK，也不转移 PFD ownership。即使未来某个 capability 为 `true`，server 仍须对
每次调用独立执行 Binder metadata、request/profile/schema/fingerprint、quota、single-active、
deadline 与 callback validation，并可返回稳定的 pre-dispatch rejection。

反方向也同样成立：private same-UID probe、public-shaped preview 或 TEST_ONLY external evidence 能
进入 server seam，不代表 metadata 可以提前 promotion。D3 不把现有内部可调用路径改写成 Public
capability，也不把 structurally compatible 解释为 runtime healthy、device verified 或 release
admitted。

截至本决策的只读检查，主 AutoJs6 仓库尚无消费这五项 TypeScript metadata 并实现上述 route
matrix 的 host client。因此本仓库只能冻结 producer contract、canonical consumer fixture 与未来
host 必须遵守的规则，**不能宣称 AutoJs6 host routing 已实现**。实际 host consumer、artifact
publication 与 runtime handoff 仍由 M5/R3 验收。

## 当前 promotion 状态

当前 producer 必须继续发布：

```text
typescriptCompilerAvailable     = false
typescriptProjectCompilation    = false
typescriptSemanticDiagnostics   = false
typescriptSourceMapV3           = false
typescriptNodeRuntimeRequired   = false
```

也就是 revision-1 mask `0`。D3 只决定将来的 promotion/rollback 兼容顺序，不执行 promotion。
NodeNext revision 2 继续停留在已验证的 internal Worker/result/private-output authority；external
Public activation、Rhino/Node runtime execution 与用户可见 host route 全部保持关闭。M5 仍须按每项
最低 gate 独立改变 metadata，不能为了制造进度一次性翻开四项。

## Canonical fixture 与验证 gate

Canonical fixture：
`app/src/test/resources/r2-capability-promotion/d3-v1.json`，schema identity 为
`autojs6.typescript.r2-m3-d3.capability-promotion-compatibility-decision`。fixture/source tests
已机器复核：

- `C/P/D/M = 1/2/4/8`，16 个组合中 exact compatible masks 只有
  `0/1/3/7/11/15`；
- `D` 与 `M` 分别 promotion/rollback 的两条分支，以及 dependency-breaking negatives；
- revision 1 exact Int、unknown/non-positive/wrong-type revision 与 revision-absent legacy
  exception；
- 五项 known Boolean 的 exact-type/presence rules，revision-1 Node dependency `true` rejection；
- bounded unknown primitive key 可忽略但不产生 authority；
- discovery/prewarm、project、diagnostics、Source Map 与 full route 的 exact requirements；
- producer metadata 当前 exact mask `0`、Node dependency `false`，且 private/public-shaped server
  seam 不构成 promotion；
- NodeNext revision 2、Public admission 与 runtime activation 保持不变。

验证入口：

- `:app:verifyR2M3D3CapabilityPromotionCompatibilityDecision`；
- `:app:verifyR2ProjectCompilerCore`（无排除项最终回归）。

| evidence | 当前状态 | 可证明范围 |
| --- | --- | --- |
| Public API exact-six helper/tests | **PASS** | revision、bit order、合法 mask 与 Node=false 结构关系 |
| canonical fixture + producer/static/source gate | **PASS** | strict metadata、legacy exception、route matrix 与 current-all-false fence |
| targeted D3 Gradle gate | **PASS** | source/JVM/Android compile-and-package 聚合；不是 device runtime |
| final no-exclusion Core | **PASS** | 当前源码完整 compiler-core 回归 |
| AutoJs6 host consumer/device Public route | **NOT CLAIMED** | 主仓 consumer 尚未实现；由 M5/R3 与后续 normal Release evidence 负责 |

### 最终 receipts

- Targeted `:app:verifyR2M3D3CapabilityPromotionCompatibilityDecision`：335 actionable tasks
  （77 executed、258 up-to-date），`BUILD SUCCESSFUL in 3m 19s`；Gradle wrapper `exit=0`、
  elapsed `199.7s`。Receipt：
  `build/reports/r2-m3-d3-targeted-current-20260813-225804.log`，SHA-256
  `044dfec7e72a454965284eb659d33698c77e222276e8bc394cd9b756973d3c19`。
- 最终无排除 `:app:verifyR2ProjectCompilerCore`：531 actionable tasks（112 executed、419
  up-to-date），`BUILD SUCCESSFUL in 3m 35s`；Gradle wrapper `exit=0`、elapsed `215.2s`。
  Receipt：`build/reports/r2-m3-d3-core-current-20260813-230500.log`，SHA-256
  `1cf1a185a2ed32b692e50ca0bf7e8bb5dcddadd1fb08251cb08cd637e778a8a9`。

两轮均为当前 D3 源码的 source/JVM/Android compile-and-package evidence；均未运行 ADB、未使用
设备，也不构成 host consumer/device Public route 或 runtime evidence。历史证据保持原有等级，
不会因本次 Core 通过而被重新标记为 current Public/Release acceptance。

D3 是纯 compatibility contract/source/fixture 决策，不改变 Binder 行为、APK 安装状态或设备可见
route，因此无需且不会为本项新增 ADB/device evidence。设备执行也不能补足当前缺失的 host consumer；
promotion-relevant normal Release API 24/35 验证仍留在 M4/M5，不能用历史内部 evidence 替代。

## 非目标与后续

D3 不冻结 input/output/diagnostics schema，不实现 host client，不开启 capability，不创建 Rhino/Node
runtime，也不修改既有 public Binder v1。D3 gate 已全部通过，M3 为 **6/18**，下一活动项直接是
`R2-M3-S1` input archive schema v1；不存在 `R2-M3-D4`。主仓仍无 current consumer，五项 metadata
仍为 `false`，Node revision 2 仍为 Worker-result-only，Public/external/runtime 保持关闭。
