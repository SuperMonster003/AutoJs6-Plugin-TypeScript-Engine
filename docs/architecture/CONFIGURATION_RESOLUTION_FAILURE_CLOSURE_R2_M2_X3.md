# R2-M2-X3 Configuration and Resolution Failure Closure

日期：2026-08-11

状态：**已完成。Direct Worker decision oracle、targeted X3 aggregate 与最终无排除 Core
均已通过；M2 因此完成 18/18，下一活动项为 `R2-M3-D1`。本切片没有运行 ADB，也不构成
Public promotion、Node revision-2 end-to-end admission 或 runtime activation。**

## 决策摘要

X3 只关闭 accepted request/archive/profile/snapshot envelope 与 raw-config identity 之后，两组
已经存在的失败语义：

1. controlled `tsconfig.json` policy 的 **11 个 configuration policy codes**；
2. snapshot-owned Node resolver 的 **18 组 string policy code + numeric diagnostic code**。

“稳定 error code”按当前分层 contract 解释，不把 internal detail 冒充为现有 Binder v1
terminal 字段：

| Failure family | Internal machine-readable detail | Binder terminal | Caller output |
| --- | --- | --- | --- |
| controlled configuration | Worker `projectConfigError.policyCode` → `CompilerHostMessage.ProjectConfigError.policyCode` → `R2ProjectCompilationOutcome.configPolicyCode` | generic `ERROR_COMPILATION_DIAGNOSTICS` | empty；不生成 diagnostics ZIP |
| Node resolution | `R2ProjectDiagnostic.code` 与 `nodeResolution.policyCode` 的 exact pair | generic `ERROR_COMPILATION_DIAGNOSTICS` | diagnostics-only ZIP；仅含 manifest 与 diagnostics，不含 JS/Source Map |

`R2CompilationCoordinator` 当前不会把 configuration 的 `configPolicyCode` 复制为 Binder
terminal 的独立字段。即使 human-readable message 包含该 code，也不得把 message parsing 视为
public machine contract。若 M3 决定让 configuration detail 成为 caller-visible structured field，
必须作为正式 additive schema 冻结；X3 不提前修改 frozen v1 AIDL/Bundle。

## X3 narrow scope

### 11 个 configuration policy codes

X3 的 configuration catalog 精确等于 `R2ControlledTsConfigPolicy.policyCodes`：

```text
CONFIG_ENCODING_INVALID
CONFIG_SYNTAX_INVALID
CONFIG_DUPLICATE_KEY
CONFIG_UNKNOWN_PROPERTY
CONFIG_INVALID_VALUE
CONFIG_UNSUPPORTED_FEATURE
CONFIG_PROFILE_CONFLICT
CONFIG_PATH_INVALID
CONFIG_COMPLEXITY_EXCEEDED
CONFIG_NO_INPUTS
CONFIG_ENTRY_NOT_SELECTED
```

这些 code 只描述 accepted controlled-config bytes 在 encoding、JSONC grammar、closed-world
property/value/profile/path/complexity 与 root selection policy 上的拒绝。它们不重命名或吸收：

- request、Bundle、AIDL 或 profile admission failure；
- archive format/path/quota/integrity failure；
- compiler unavailable、protocol、transport、timeout、cancel 或 internal failure；
- Program 构造后的 options/syntactic/global/semantic/emit diagnostics。

Configuration rejection 在 Program 前终止，不发布 result start、不调用 emit，也不创建
`R2ProjectCompilation` 或 diagnostics archive。失败只持有 config raw bytes/hash 与 policy identity；
`R2ProjectCompilationOutcome.exactSnapshot` 只允许成功 compilation，因此 X3 不伪造 configuration
failure 的 result-snapshot binding。

### 18 个 resolution diagnostic identities

Resolution identity 必须同时匹配 string policy code 与 numeric diagnostic code；只匹配其中一项
必须 fail closed：

| String policy code | Numeric code |
| --- | ---: |
| `NODE_RESOLUTION_INVALID_CONTAINING_PATH` | `900001` |
| `NODE_RESOLUTION_INVALID_SPECIFIER` | `900002` |
| `NODE_RESOLUTION_INVALID_CONDITION` | `900003` |
| `NODE_RESOLUTION_AMBIGUOUS_CASE` | `900004` |
| `NODE_RESOLUTION_PACKAGE_NOT_ALLOWED` | `900005` |
| `NODE_RESOLUTION_PACKAGE_METADATA_MISSING` | `900006` |
| `NODE_RESOLUTION_PACKAGE_METADATA_INVALID` | `900007` |
| `NODE_RESOLUTION_IMPORTS_NOT_DEFINED` | `900008` |
| `NODE_RESOLUTION_IMPORTS_NO_MATCH` | `900009` |
| `NODE_RESOLUTION_EXPORTS_NOT_DEFINED` | `900010` |
| `NODE_RESOLUTION_EXPORTS_NO_MATCH` | `900011` |
| `NODE_RESOLUTION_CONDITION_NO_MATCH` | `900012` |
| `NODE_RESOLUTION_INVALID_TARGET` | `900013` |
| `NODE_RESOLUTION_TARGET_BLOCKED` | `900014` |
| `NODE_RESOLUTION_TARGET_ESCAPE` | `900015` |
| `NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT` | `900016` |
| `NODE_RESOLUTION_UNSUPPORTED_SOURCE_KIND` | `900017` |
| `NODE_RESOLUTION_CYCLE` | `900018` |

每项 resolution failure 都是 category `1` 的 blocking diagnostic，使用 `phase=resolution`，
`emitSkipped=true` 与 `outputs=[]`。Native framed parser、`R2ProjectCompilation` 和 private output
必须保存同一 exact pair；diagnostics-only ZIP 中不得出现 executable module 或 Source Map。

完整 18-pair catalog coverage 与真实 TypeScript Program reachability 是两层不同证据。X3 的
`18/18` 是 accepted authority 后逐项驱动 compiler-owned resolver decision、冻结 exact identity
与 isolation 的 direct decision coverage；这些失败全部在 Program 前收口，direct oracle 明确记录
`failure Program=0`。它不是“18 种失败都由真实 TypeScript Program 自然触发”。既有 C6/P2
Program integration regressions 继续用可自然触发的代表性失败证明 resolver hook 确实参与编译，
两层证据不得互相冒充。

## Profile authority hardening

Configuration 与 resolution 的 native model 只允许下列组合：

| Exact profile authority | Config kind | Configuration detail | Resolution diagnostic |
| --- | --- | --- | --- |
| provisional Rhino revision 1 | controlled `tsconfig` | admitted | admitted through controlled resolver-v1 |
| provisional Rhino revision 1 | config-none | not applicable | rejected |
| provisional NodeNext revision 1 | config-none | not applicable | rejected |
| provisional NodeNext revision 2 | config-none | not applicable | admitted through resolver-v2 |

Controlled `tsconfig` 不得与 Node revision 1 或 Node revision 2 组合。当前 native hardening marker
位于 `R2ProjectCompilation`：`CONFIG_KIND_TSCONFIG` 除了 schema/effective-options/root authority，
还必须匹配 exact `PROVISIONAL_RHINO`。因此原有
`configKind == tsconfig || isNodeRevisionTwo` resolution admission 实际收紧为：

```text
exact Rhino revision 1 + controlled tsconfig
or
exact NodeNext revision 2 + config-none + native result authority
```

JVM markers：

- [x] `controlledCompilationRequiresExactRhinoRevisionOneProfile`：Rhino positive，Node-r1/Node-r2
  controlled negative；
- [x] `resolutionDiagnosticsAreAdmittedOnlyForControlledRhinoOrNodeRevisionTwo`：controlled Rhino
  positive，Rhino config-none/Node-r1 negative，exact Node-r2 config-none positive。

两项测试已由 targeted X3 aggregate 与最终 Core 执行，native profile gap 已关闭。

## Exact snapshot 与 output authority

Resolution result 必须绑定触发该结果的 exact accepted snapshot：

- `CompilerHostProtocol.parseFramedProjectResult()` 不接受缺失、替换或 profile split-brain 的
  snapshot authority；
- Node revision 2 必须从 exact snapshot 与 frozen policy 独立派生
  `R2NodeRevisionTwoResultAuthority`，不能信任 Worker 自报 project/output identity；
- `R2ProjectCompilationOutcome.bindToExactSnapshot()` 只能绑定一次，并再次验证 Node-r2 result
  authority；
- diagnostic 的任何非空 source path、package metadata/target reference 与 archive inventory 都
  必须属于该 accepted snapshot/target universe；host absolute path 与相邻 snapshot path 均拒绝；
- blocking resolution result 只允许 diagnostics-only staging，且 commit 后 Binder terminal 仍是
  generic diagnostics failure。

Configuration rejection 没有 compilation result，因此不进入上述 result binding。它的闭包是
exact config raw bytes、declared digest、controlled policy revision/fingerprint 与 pre-Program root
selection authority；不得为了形式统一而构造空的伪 snapshot result。

## Isolation oracle

X3 的“不得回读 snapshot 外路径、不访问网络”必须由可观测 oracle 证明，而不是只搜索源码文本。

Configuration failure oracle：

- parser 只消费已传入的 controlled config bytes；`$schema` 即使为 HTTPS string 也保持 inert；
- Program creation、default resolver、`ts.sys`、host filesystem 与 network 调用均为零；
- result framing、emit 与 output commit 均不开始，caller output 保持 empty；
- config bytes、operation state 与 pending authority 在 terminal 后释放，clean successor 不受污染。

Resolution failure oracle：

- `resolveModuleNameLiterals` / `resolveTypeReferenceDirectiveReferences` 由 compiler-owned resolver
  接管，`resolveLibrary` fail closed；
- TypeScript 默认 `resolveModuleName` / `nodeModuleNameResolver`、`ts.sys`、host filesystem 与
  network 调用均为零；
- `fileExists` / `readFile` 等 ordinary host lookup 不能看到 `/packages/**`；只有当前 resolver
  成功授予的 exact retained target 可以 materialize；failure path 不授予 fallback target；
- resolver 的 source、metadata、target 与 condition lookup 全部来自 exact frozen VFS/catalog；
  `..`、absolute host path、case collision、unaccepted target 与 snapshot escape 均 fail closed；
- failure 后 `builder.emit()` 不调用，outputs 为空，resolver memo/grant/failure ledger/framed bytes
  清理，随后 clean successor 成功。

## Evidence matrix

| Layer | X3 evidence | Status |
| --- | --- | --- |
| direct Worker decision oracle | accepted authority 后 config `11/11`、resolution `18/18`；2 个 clean successors；failure Program、outside-snapshot positive reads、`ts.sys`/default resolver/network 均为 0 | PASS |
| JVM model/protocol/private output | exact profile matrix、internal detail preservation、18-pair strict identity、wrong pair/shape/outside-snapshot negatives、diagnostics-only archive 与 configuration empty-output boundary | PASS |
| Android build/static | Android/JVM compilation、AndroidTest APK、API/AIDL/source/public/capability/admission fences | PASS |
| targeted aggregate | `verifyR2M2X3StableConfigurationAndResolutionFailures`：329 actionable tasks（91 executed、238 up-to-date），`BUILD SUCCESSFUL in 5m 24s` | PASS |
| final aggregate | 无排除 `verifyR2ProjectCompilerCore`：525 actionable tasks（113 executed、412 up-to-date），`BUILD SUCCESSFUL in 5m 8s` | PASS |

Direct oracle 的原始稳定摘要为：

```text
R2-M2-X3 stable configuration/resolution failures verified: config=11/11, resolution=18/18, clean successors=2, failure Program=0, outside-snapshot positive reads=0, ts.sys/default resolver/network=0
```

Targeted aggregate 串行承载 X2 regression、C2/C3/C4/C6/C7、P2-7、JVM unit tests、
AndroidTest APK 与 source/public/API/capability fences。最终 Core 继续依赖该 X3 aggregate；运行脚本
取得 Core mutex 后，先等待另一个 active Gradle wrapper 约 55 秒，再启动本次 Core，脚本总耗时
`599.7s`。该等待遵守共享工作区的 Gradle 串行规则，不属于 gate 执行耗时，也没有并行启动第二个
Gradle transaction。

## 为什么 X3 不要求新的 ADB/device evidence

本切片不新增 Android runtime route：

- public-shaped/private Binder admission 仍只允许既有 Rhino revision 1 与 Node revision 1 的
  config-none authority；它们不能自然抵达 controlled-config failure 或 Node-r2 resolution；
- Node revision 2 继续只到 Worker-result/native-private-output，不加入
  `endToEndAdmittedAuthorities`；
- X3 不修改 AIDL/Bundle、service lifecycle、PFD ownership、callback ordering、process isolation、
  timeout/cancel 或 WebView runtime configuration；
- X1/X2 已负责既有 public-shaped transport 与 diagnostics-only callback 形状；X3 关闭的是既有
  policy/model/protocol catalog 与 isolation identity。

因此，desktop real-TypeScript/Worker、JVM native/framed/private-output 与 Android compile/APK/
source fences 已构成 X3 的最小证据层；新增 ADB 行既不能自然覆盖这两个当前未 end-to-end
admit 的分支，也不应通过 debug admission seam 扩张 shipping authority。若实现后来新增 public
controlled-config、Node-r2 Binder admission、Android-only host behavior或 Binder schema 字段，则
该前提失效，必须重新加入 focused device evidence。

## Non-goals

X3 不完成：

- public configuration/resolution diagnostic schema freeze；该职责属于 M3；
- caller-visible detailed configuration code 的新 Bundle/AIDL field；
- controlled Node tsconfig、Node revision-2 end-to-end/public admission 或 Node Runtime activation；
- external/distinct-UID Public promotion、capability metadata activation或 runtime execution；
- TypeScript diagnostics 文案稳定性；稳定 identity 是上述 code/pair，不是 message text；
- 网络 fallback、宿主文件系统 fallback、自动 retry 或任意 snapshot 外 resolution；
- 新的 soak、capacity、timeout、ADB 或设备矩阵。

X3 已关闭且 M2 完成 18/18，但本文仍不是 Public-ready 或 production-release acceptance。
四项 compiler capability 与 `NODE_RUNTIME_REQUIRED` 共五项 capability/dependency metadata 保持
`false`；Node revision 2 继续 Worker-result-only，Public、runtime 与 Node Runtime 均未开启。
