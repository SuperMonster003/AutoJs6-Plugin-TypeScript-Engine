# R2-M2-C1 Controlled tsconfig Policy

日期：2026-08-01

状态：**C1 contract/policy complete；尚未接入 production request。** 当前 production
registry、result 与 output 继续只接受 `configKind=none`，四项 compiler capability 继续为
`false`。[R2-M2-C2](CONTROLLED_TSCONFIG_R2_M2_C2.md) 已完成 raw config
bytes/hash、framing 与 plugin-owned parser wiring；
[R2-M2-C3](CONTROLLED_TSCONFIG_R2_M2_C3.md) 已完成独立 internal branch 的 41-key
effective-options precedence/canonical fingerprint；
[R2-M2-C4](COMPILER_ESCAPE_CLOSURE_R2_M2_C4.md) 已关闭 config/package/Binder 与
host/network escape，但 C7 前仍不进入 Program。

## 目的

C1 冻结首版受控 `tsconfig.json` 的 closed-world grammar、默认值、root-selection
语义、拒绝分类与稳定 policy code。它不改变 frozen v1 AIDL/Binder surface，也不把
声明好的策略误当成已经可调用的产品能力。

唯一机器 authority 为
`R2ControlledTsConfigPolicy`；checked-in golden 为
`app/src/test/resources/r2-controlled-tsconfig/policy-v1.json`，policy fingerprint 为：

```text
849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d
```

## Config identity 与预算

| 项 | 冻结值 |
| --- | --- |
| config kind | `tsconfig` |
| policy revision | `1` |
| config path | project-root logical `tsconfig.json` |
| grammar | TypeScript `6.0.3` JSONC |
| encoding | UTF-8；允许一个开头 BOM，拒绝无效 UTF-8/lone surrogate |
| JSONC | 允许 comments 与 trailing comma；所有 object depth 均拒绝 duplicate key |
| raw bytes | inclusive `48 KiB` |
| JSON complexity | depth `16`、nodes `4096` |
| selectors | `files/include/exclude` 合计最多 `512` 项 |
| selector size | 每项最多 `4096` UTF-8 bytes |

TypeScript `parseConfigFileTextToJson` 会接受 duplicate key 并采用 last-wins；因此 C2
必须先做 duplicate-aware preflight，不能只依赖 TypeScript parser。raw bytes 超限使用
既有 `ERROR_ARCHIVE_QUOTA`，raw bytes/hash 不一致使用 `ERROR_ARCHIVE_INTEGRITY`。

预算上限均为 inclusive，计数算法也属于 revision 1 契约：

- raw bytes 是包含可选 BOM 在内的原始配置总字节数；
- JSON depth 以根 value 为 `1`，每进入一个 child value 加 `1`，任何 value 的 depth
  大于 `16` 即失败；
- JSON nodes 统计每个 JSON value，根 value 计入，object property name、comment、逗号
  与 BOM 不计入；第 `4097` 个 node 失败；
- selector 数量统计 `files/include/exclude` 三个原始数组的全部 element，在类型校验、NFC
  normalization 与去重之前计数；合成的默认 `include=["**/*"]` 不消耗 caller selector
  budget，第 `513` 个 element 失败；
- 单项大小取 JSON unescape 后字符串与其 NFC-normalized 字符串两者 UTF-8 bytes 的较大值，
  最大允许 `4096` bytes。

这些预算不提升 ordinary production 的 512 files、8 MiB/file、8 MiB total admission。

## Exact top-level shape

首版只允许以下五个 key：

| Key | 类型 | 语义 |
| --- | --- | --- |
| `$schema` | string | 仅编辑器注解；plugin 不解析、不读取 URL、不访问网络 |
| `compilerOptions` | object | 只接受下一节列出的 boolean allowlist |
| `files` | string[] | 显式 root selectors |
| `include` | string[] | TypeScript 6.0.3 glob selectors |
| `exclude` | string[] | 只影响 `include`，不排除 `files` 或 import 到的文件 |

`files` 与 `include` 同时存在时取并集，再按 canonical logical path 去重。两者均缺省时，
canonical default 为 `include=["**/*"]`；`exclude` 缺省为 `[]`。最终 root set 必须非空，
且 `entryPath` 必须位于 root set。

selectors 相对于 project root，只在已经封闭的 snapshot inventory 中匹配。它们使用 NFC
logical project path，而不是 ZIP v1 的 printable-ASCII entry name；C2/C7 必须提供无损
logical-path ↔ transport-path 绑定，绝不能回读宿主路径。绝对路径、drive/URI scheme、
`..` escape、NUL 与 canonical collision 均以 `CONFIG_PATH_INVALID` 拒绝。

以下 top-level feature 在 revision 1 显式不支持：

- `extends`、`references`；
- `compileOnSave`、`watchOptions`、`typeAcquisition`；
- 任何未知 top-level property。

## compilerOptions allowlist

所有允许值都必须是 JSON boolean。未列出的 option 不透传给 TypeScript。

| Option | 显式默认 |
| --- | --- |
| `strict` | `true` |
| `noImplicitAny` | inherit `strict` |
| `strictNullChecks` | inherit `strict` |
| `strictFunctionTypes` | inherit `strict` |
| `strictBindCallApply` | inherit `strict` |
| `strictPropertyInitialization` | inherit `strict` |
| `strictBuiltinIteratorReturn` | inherit `strict` |
| `noImplicitThis` | inherit `strict` |
| `useUnknownInCatchVariables` | inherit `strict` |
| `noUnusedLocals` | `false` |
| `noUnusedParameters` | `false` |
| `exactOptionalPropertyTypes` | `false` |
| `noImplicitReturns` | `false` |
| `noFallthroughCasesInSwitch` | `false` |
| `noUncheckedIndexedAccess` | `false` |
| `noImplicitOverride` | `false` |
| `noPropertyAccessFromIndexSignature` | `false` |
| `skipLibCheck` | `false` |

显式 strict-family child 覆盖用户的 `strict` umbrella；缺省 child 才继承 `strict`。
C3 已使用 TypeScript 6.0.3 的 strict-option semantics 生成完整 explicit effective
options，且不把 `ts.getDefaultCompilerOptions()` 当作完整默认 authority。

`allowUnusedLabels` 与 `allowUnreachableCode` 首版不开放，因为 omitted 与显式 `false`
具有不同语义。experimental decorators、JSX 与 build/reference mode 也不在本 revision。

## Closed-world reject policy

分类顺序固定如下：

1. allowlist 中的 boolean option：进入 value/default 处理；
2. profile-managed option：即使值与 profile 相同也返回 `CONFIG_PROFILE_CONFLICT`；
3. TypeScript 6.0.3 已知但未开放的 option：返回 `CONFIG_UNSUPPORTED_FEATURE`；
4. TypeScript 6.0.3 不认识的 option：返回 `CONFIG_UNKNOWN_PROPERTY`。

profile-managed 集合覆盖 target/module/resolution、source/output path、Source Map、
newline、`noEmitOnError`、JS/JSON admission 与 interop/class-field semantics。用户不能建立
第二个 authority。

以下类别被显式 pin 为 unsupported：

- host/resolution escape：`plugins`、`typeRoots`、`types`、`lib`、`baseUrl`、`paths`、
  `rootDirs`、`moduleSuffixes`、`customConditions`、`traceResolution`；
- output/build control：declaration family、incremental/build-info、inline map/source、
  `noEmit`、`outFile`、map/source root、helpers 与 comment/BOM emit controls；
- custom transformer 不是标准 tsconfig option，按 unknown property fail closed；
- C4 已冻结 `package.json`/`node_modules` 与 lifecycle `scripts` 的 deny-only authority；
  C5 才正向运输受控 package metadata，C1 不伪装成已实现该 transport。

## 稳定 policy codes

| Code | 适用边界 |
| --- | --- |
| `CONFIG_ENCODING_INVALID` | UTF-8/BOM/surrogate 规则失败 |
| `CONFIG_SYNTAX_INVALID` | JSONC lexical/grammar failure 或空文档 |
| `CONFIG_DUPLICATE_KEY` | 任意 object depth duplicate |
| `CONFIG_UNKNOWN_PROPERTY` | unknown top-level 或 compiler option |
| `CONFIG_INVALID_VALUE` | well-formed JSONC 的 root/字段类型、boolean value 或字段组合非法 |
| `CONFIG_UNSUPPORTED_FEATURE` | 已知但首版不支持的 feature/option |
| `CONFIG_PROFILE_CONFLICT` | caller 声明 profile-owned option |
| `CONFIG_PATH_INVALID` | selector/path escape 或 canonical collision |
| `CONFIG_COMPLEXITY_EXCEEDED` | AST/selector complexity 超限 |
| `CONFIG_NO_INPUTS` | root selection 为空 |
| `CONFIG_ENTRY_NOT_SELECTED` | entry 不在最终 root set |

错误 message 是 bounded 人类说明，不是兼容契约，也不得回显完整用户文本。只有先通过
raw byte quota、hash/integrity 与 config kind/schema revision transport gates 的请求，其
encoding、JSONC、closed-world policy 和 root-selection 失败才映射为
`OUTCOME_DIAGNOSTICS + ERROR_COMPILATION_DIAGNOSTICS + stage=config`，且 module count 为
0；正式 diagnostics JSON field 由 M3 冻结。

raw bytes 超限继续使用 `ERROR_ARCHIVE_QUOTA`，raw bytes/hash 不一致继续使用
`ERROR_ARCHIVE_INTEGRITY`，config kind/schema revision 不匹配继续使用
`ERROR_CONTRACT_MISMATCH`；这三类 transport/contract failure 的 output 均为空，不映射成
config diagnostics。

## 后续边界

- C2：已运输并三方绑定 raw bytes/hash，执行 JSONC/duplicate/complexity/policy parsing；
- C3：已执行 immutable profile > allowed user options > explicit defaults，冻结 41-key
  canonical effective options 与 pure fingerprint；
- C4：已证明 config/package/Binder 不存在 transformer、plugin、filesystem 或 network
  escape，见 [Compiler Escape Closure](COMPILER_ESCAPE_CLOSURE_R2_M2_C4.md)；
- C5/C6：受控 package metadata 与 NodeNext resolution；C1 不开放 paths/baseUrl；
- C7：通过 snapshot-only `ParseConfigHost.readDirectory` 实现 selectors，并与
  TypeScript 6.0.3 filesystem oracle 对照；
- C8：common/Rhino/Node declarations 分层，用户不能用 types/typeRoots/lib 绕过。

现有 Rhino `profileRevision=1/configSchemaRevision=1/configKind=none` 保持原样。C2 已用
request-manifest revision 3 与 config schema revision 2 建立独立内部路径；C3 只在该
路径创建 revision-1/41-key effective authority，未原地重新解释历史 request。

## 验收

```powershell
.\gradlew.bat :app:verifyR2M2C1ControlledTsConfigPolicy
```

该 gate 执行：

- app/API JVM tests 与 frozen-v1/additive-R2 AAR compatibility；
- checked-in policy golden 与 fingerprint；
- TypeScript 6.0.3 option existence/boolean type/strict inheritance；
- JSONC comments/trailing-comma/BOM goldens与 duplicate last-wins threat golden；
- public-shaped 行为测试确认 `configKind=tsconfig` 当前仍被拒绝；
- source policy 确认 archive/Worker 仍为 `configKind=none`、inline options 仍为 `{}`，四项
  compiler capability 仍为 `false`。

该 C1 gate 不运行设备、不改变 capability；它在 C1 closure 当时不替代 C2/C3/C4 的独立
验收。后续三项的完成状态与证据分别由各自文档承载；下一项 C5 将以新 manifest revision
正向定义受控 package metadata，不会重解释 C1 authority。

2026-08-01 实际结果：

```text
R2-M2-C1 controlled tsconfig policy verified: 18 options,
849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d
BUILD SUCCESSFUL
110 actionable tasks: 5 executed, 105 up-to-date
```

closure 阶段重复执行两次，均以退出码 0 完成；其中一次 Kotlin daemon 未能连接后自动
回退到非 daemon 编译，另一次正常完成。app/API compilation、unit tests 与 AAR freeze
gate 均实际通过。
