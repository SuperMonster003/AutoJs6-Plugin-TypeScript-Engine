# R2-M2-C3 Controlled tsconfig Effective Options Authority

> **R2-M3-S1 supersession（2026-08-14）。** 本页保留 M2 的 pre-S1 input provenance；正文中的
> request-manifest revision `3` 与当时的 Worker start shape 不是当前 formal alias/fallback。
> 当前只接受 `autojs6.typescript.input-archive/revision=1`，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。41-key options authority 本身继续保留。

日期：2026-08-01

状态：**C3 internal contract complete；C4 escape closure、C5 package metadata transport
与 C7 controlled root selection 均已完成；尚未开放 production tsconfig compilation。**
当前 production authority 仍精确保持
`profileSchemaRevision=1/profileRevision=1/configSchemaRevision=1/configKind=none`，
四项 compiler capability 仍全部为 `false`。C3 只在 C2 的 revision-3 internal
controlled-config branch 内完成 precedence、typed invalid-combination validation、
canonicalization、fingerprint 与 config-end 原子冻结。C7 现已在该 authority 上解释 root
selectors，并把 41-key effective options 与 selected roots 绑定到 internal Program、result
framing 和 provisional output；C4 继续关闭 compiler/package/Binder/host/network escape。
这些 downstream 闭环不改变 production registry 或 public capability。

## 目的与 authority 分层

C3 把 [C1 closed-world policy](CONTROLLED_TSCONFIG_R2_M2_C1.md) 与
[C2 raw identity/parser](CONTROLLED_TSCONFIG_R2_M2_C2.md) 产生的显式 boolean map
解析为唯一、不可变的 effective-options state：

```text
immutable AutoJs6 profile
  > explicit C1-allowed caller boolean
  > explicit TypeScript 6.0.3 default
```

profile-managed option 已在 C1/C2 closed-world mapping 中拒绝；即使 caller 声明的值
与 profile pin 相同，也必须返回 `CONFIG_PROFILE_CONFLICT`，不能建立第二个 authority。
因此 C3 的输入只包含 C2 已验证的 18 项 allowlisted boolean presence/value，以及原样
保留、交由 C7 解释的 `files/include/exclude` selectors。

历史 config-none authority 与 controlled authority 是两个不互相重解释的 revision：

| Authority | Canonical key 数 | 边界 |
| --- | ---: | --- |
| historical config-none | 18 | 原 exact JSON、值和 fingerprint 全部保持不变 |
| controlled tsconfig effective-options revision 1 | 41 | 仅用于 schema-2/revision-3 internal branch |

41-key inventory 由以下不重叠增量组成：历史 18 个 key、C1 allowlist 中历史 inventory
尚未包含的 16 个 boolean key，以及 7 个 TypeScript 6.0.3 profile-managed computed pin。
历史 18 个 key 中的 `strict` 与 `skipLibCheck` 在 controlled branch 上由 caller/default
层解析；其余 profile-owned 值保持 immutable。

机器 authority 为 `R2ControlledTsConfigEffectiveOptions`；checked-in golden 为
`app/src/test/resources/r2-controlled-tsconfig/effective-options-v1.json`。

## 41-key canonical inventory

canonical JSON 的 exact key set 按 UTF-16 code-unit 顺序排列：

```text
allowJs
allowSyntheticDefaultImports
alwaysStrict
checkJs
esModuleInterop
exactOptionalPropertyTypes
forceConsistentCasingInFileNames
ignoreDeprecations
incremental
inlineSourceMap
inlineSources
module
moduleDetection
moduleResolution
newLine
noEmitOnError
noFallthroughCasesInSwitch
noImplicitAny
noImplicitOverride
noImplicitReturns
noImplicitThis
noLib
noPropertyAccessFromIndexSignature
noUncheckedIndexedAccess
noUnusedLocals
noUnusedParameters
outDir
resolveJsonModule
rootDir
skipLibCheck
sourceMap
strict
strictBindCallApply
strictBuiltinIteratorReturn
strictFunctionTypes
strictNullChecks
strictPropertyInitialization
target
types
useDefineForClassFields
useUnknownInCatchVariables
```

其中新增的 7 个 explicit profile pins 为：

| Option | Pin |
| --- | --- |
| `allowSyntheticDefaultImports` | `true` |
| `alwaysStrict` | `true` |
| `esModuleInterop` | `true` |
| `forceConsistentCasingInFileNames` | `true` |
| `moduleDetection` | `2` |
| `resolveJsonModule` | `false` |
| `useDefineForClassFields` | `false` |

它们把 TypeScript 6.0.3 可能计算或派生的行为变成显式 authority；caller 不能声明或
覆盖这些值。`moduleDetection` 与其他 TypeScript enum 一样使用 numeric canonical value。

## Boolean precedence 与 explicit defaults

`strict` 缺省为 `true`。以下 8 个 strict-family child 在未显式声明时继承最终解析后的
`strict`；显式 child 始终覆盖 caller 自己的 umbrella 值：

```text
noImplicitAny
strictNullChecks
strictFunctionTypes
strictBindCallApply
strictPropertyInitialization
strictBuiltinIteratorReturn
noImplicitThis
useUnknownInCatchVariables
```

以下 9 项缺省为 `false`：

```text
noUnusedLocals
noUnusedParameters
exactOptionalPropertyTypes
noImplicitReturns
noFallthroughCasesInSwitch
noUncheckedIndexedAccess
noImplicitOverride
noPropertyAccessFromIndexSignature
skipLibCheck
```

解析结果显式包含全部 18 项 C1 boolean；不存在“省略后交给当前 TypeScript 默认值”的
隐式状态，也不调用 `ts.getDefaultCompilerOptions()` 形成第二套默认 authority。

## TS5052-compatible typed invalid combinations

组合合法性在 precedence/strict inheritance **之后**按 effective value 判定。以下任一
条件成立时均为 typed config failure：

```text
strictNullChecks=false && exactOptionalPropertyTypes=true
strictNullChecks=false && strictPropertyInitialization=true
```

稳定映射为：

```text
projectConfigError
stage=config
outcome=diagnostics
errorCode=ERROR_COMPILATION_DIAGNOSTICS
policyCode=CONFIG_INVALID_VALUE
emittedModuleCount=0
```

这与 TypeScript 6.0.3 的 TS5052 约束一致，但 public diagnostics schema 仍由 M3
冻结。失败只发布内部 typed control failure；不 ACK `projectInputConfigEnd`，不继续
source framing，不创建 Program/result，也不生成 diagnostics ZIP。

## Canonical JSON 与 pure SHA semantics

canonical effective-options revision 固定为 `1`，并满足：

- key set 精确为上述 41 项，按 UTF-16 code-unit 排序；
- compact JSON，无空白，所有值显式存在；
- 只包含 printable ASCII；string 使用 JSON escaping；
- TypeScript enum 使用 numeric value；`types` 显式为 `[]`；
- boolean 使用解析后的完整 effective value，不保留 presence/implicit 标记。

fingerprint 的唯一计算式是：

```text
effectiveOptionsFingerprint = lowercaseHex(
  SHA-256(exact printable-ASCII canonicalEffectiveOptionsJson bytes)
)
```

该字段**不是 composite fingerprint**，没有 domain separator，也不拼接 profile/config
revision、raw config bytes/hash、policy fingerprint、selectors、compiler distribution、
declarations 或 runtime-capability fingerprint。这些 authority 继续使用各自独立字段；若
M3 需要 composite identity，必须另定义名称、canonical input 和 revision，不能改变
`effectiveOptionsFingerprint` 的 pure canonical SHA 语义。

默认 41-key canonical JSON 为 992 bytes，fingerprint 固定为：

```text
2d8daf5f2f3516c9684f8e4659f51dfb9b037d21849bc2bbafca2fbaf034bd42
```

历史 config-none 18-key canonical JSON 及其 fingerprint
`76a173383972b1bd8d4ffa3fffe5d51f5adecf7b84e7b16f58554c14f3c08e0b`
必须保持 byte-for-byte 不变。

## Config-end 原子边界

controlled branch 的 `projectInputConfigEnd` 成功路径严格为：

```text
native/Worker byte count + SHA-256 recheck
  -> fatal UTF-8 + JSONC/duplicate/complexity validation
  -> C1 closed-world policy mapping
  -> immutable profile / caller / default merge
  -> TS5052-compatible combination validation
  -> 41-key canonical JSON + pure fingerprint
  -> freeze immutable effective-options/config state
  -> ACK projectInputConfigEnd
```

ACK 是完整 config transaction 的 commit point。上述任一步失败都不发送 config-end ACK，
也不能留下可被 source framing 或后继 request 复用的 partial effective state。只有 state
冻结成功后才允许继续当前 request 的 source frames。

## C3 历史 C7 fail-closed 边界与当前闭环

C3 只保存 normalized `files/include/exclude` selectors，不解释 glob，也不把当前所有
`sources/**/*.ts` 静默当作 roots。已经完成的 C7 独占负责：

- 在封闭 snapshot inventory 内执行 files/include/exclude matching 与 canonical dedup；
- 产生 `CONFIG_NO_INPUTS` / `CONFIG_ENTRY_NOT_SELECTED`；
- 强制 `entryPath` 属于最终 root set；
- 把所有选中 roots 交给 Program/emit candidate inventory。

C3 原子验收完成时，C7 尚未落地；即使 config-end 已成功 ACK，controlled request 到达
input/project end 仍 fail closed：不调用 `createIncrementalProgram`，不进入
parse/semantic/emit phase，不发布 `projectResult*`，不创建 result/diagnostics/output ZIP，
也不提交 caller output FD。这是 C3 的历史 acceptance fence，不再描述当前 internal
controlled branch。

C7 现已在创建 Program 前完成 snapshot-only selector matching、canonical dedup、entryPath
membership 与稳定空输入失败，并把 selected roots、41-key effective options revision/
fingerprint 绑定到 result 和 provisional output。现有 config-none compiler path 仍不得被
复用来绕过 selector authority。完整 C6 已于 2026-08-02 在该 controlled branch 上接入
closed-world snapshot package resolver 与 typed failure；P1/P2 的 NodeNext profile 和
module-kind/output 规则仍未完成。见
[Controlled Node Resolution Program](CONTROLLED_NODE_RESOLUTION_PROGRAM_R2_M2_C6.md)。

## Public 非激活证明

C3 不改变任何外部可达行为：

- production registry 继续只声明 schema `1` / `configKind=none` 与历史 18-key authority；
- 完整合法的 controlled public metadata 继续在 coordinator/FD ownership 前同步返回
  `ERROR_CONTRACT_MISMATCH`；
- `compilerOptionsJson` 继续缺省或精确为 `{}`；service 可先执行 non-owning descriptor
  inspection，其他值由 C4 在 coordinator admission/FD ownership transfer 前同步拒绝；
- frozen AIDL、Binder transaction prefix、public contract hash 与 ZIP v1 不修改；
- config-none/public result/output schema 不修改；C7 只为 internal controlled result 增加
  additive authority 字段，仍不新增 diagnostics-only ZIP；
- `typescriptCompilerAvailable`、`typescriptProjectCompilation`、
  `typescriptSemanticDiagnostics`、`typescriptSourceMapV3` 全部为 `false`。

由于不存在 externally reachable runtime behavior change，C3 不新增设备测试；设备与
normal Release promotion 继续由后续 public activation/release gate 负责。

## 验收

原子 gate：

```powershell
.\gradlew.bat :app:verifyR2M2C3ControlledTsConfigEffectiveOptions
```

gate 应覆盖 41-key exact inventory、7 pins、strict inheritance/child override、9 项 false
default、TS5052-compatible rejection、canonical golden/pure SHA、legacy 18-key fingerprint
不变、config-end ACK 原子顺序、C7 downstream root-selection/result binding、public metadata
同步拒绝、缺省/精确 `{}` 的 `compilerOptionsJson`、四项 capability=false，以及
frozen/additive API/source policy。

C4 使用独立 gate 组合上述 C3 authority，并增加 exact frame、reserved path、transformer/
plugin、snapshot-only host 与 network fences；它不改写以下 C3 历史验收结果。C5 已以新
manifest revision 增加 package metadata，C7 也已完成 internal Program/root-selection 闭环；
production/public activation 仍由后续 milestone 独立负责。

2026-08-01 实际结果：

```text
R2-M2-C1 controlled tsconfig policy verified: 18 options,
849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d
R2-M2-C2 controlled tsconfig parser verified: 3 accepted, 30 rejected fixtures
R2-M2-C3 controlled tsconfig effective options verified: 41 canonical keys,
6 accepted, 3 rejected
R2 provisional Rhino desktop golden passed (TypeScript 6.0.3).
app JVM: 218 tests, 0 failures/errors/skipped
typescript-api JVM: 18 tests, 0 failures/errors/skipped
BUILD SUCCESSFUL in 1m 2s
115 actionable tasks: 11 executed, 104 up-to-date
```
