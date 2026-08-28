# S1-4 Public Controlled tsconfig Activation

状态：**完成（2026-08-23）**。

> 本文冻结 S1-4 当时的 Rhino policy revision 2 证据。T1-2/T1-3 已在 2026-08-26
> append-only 发布双 profile policy revision 3/4，同时保留本文身份作兼容；当前权威见
> [T1-3 Controlled `baseUrl`/`paths` Revision 4](CONTROLLED_PATH_ALIASES_T1_3_REVISION4.md)。

本切片把 R2-M2-C1–C7 已实现但仅内部可达的受控 `tsconfig.json` 链路接入
public Rhino 编译路径。AIDL v1、input/output archive schema v1 与 diagnostics schema v1
均未修改；新增行为只使用 `TypeScriptR2Contract` 中已有的 additive Bundle metadata 和
formal input archive 的 conditional config shape。

## 对宿主可见的能力

`getCompilerInfo()` 与插件 capability Bundle 现在发布：

```text
configSchemaRevision = 2
configKind = tsconfig
configPolicyRevision = 2
configPolicyFingerprint = 64ef1cf36d6531f84088cec49127e6407b107ab86eeaf7f4512358fd1343598b
```

宿主若选择受控配置，必须同时完成以下两件事：

1. 在 formal input archive 中加入 root-level `tsconfig.json`，并让
   `request-manifest.json` 的 conditional config descriptor、文件长度和 SHA-256 与实际 entry
   完全一致；
2. 在 `startCompilation` request Bundle 中提交完整的
   `configSchemaRevision/configKind/configByteLength/configSha256/configPolicyRevision/
   configPolicyFingerprint` identity。

其中任一字段缺失、类型错误、长度越界或摘要格式错误，均在 FD ownership transfer 前以
`ERROR_INVALID_REQUEST` 拒绝；revision、kind 或 fingerprint 不受支持时以
`ERROR_CONTRACT_MISMATCH` 拒绝。完整且匹配的 identity 现在产生
`ACCEPTED_CONTROLLED_TSCONFIG`，不再返回历史上的 not-activated disposition。

`configKind=none` 仍是兼容路径。S1-5 已在该路径加入 exact NodeNext revision 2 public
authority；受控 `tsconfig` 路径仍只接受 exact Rhino profile。Node 的独立接入字段与边界见
[S1-5 NodeNext Public Admission](NODE_NEXT_PUBLIC_ADMISSION_S1_5.md)。`compilerOptionsJson` 仍必须
缺省或精确为 `"{}"`：受控选项只从 archive 内经过 hash 绑定的 raw `tsconfig.json` 读取，避免
形成第二套 inline authority。

## revision 2 配置语义

parser 使用 TypeScript 6.0.3 JSONC 语法，允许 UTF-8 BOM、注释和 trailing comma；重复 key、
畸形 UTF-8、非法 JSONC、过深/过大的文档仍 fail closed。预算保持为 48 KiB、最多 512 个
selector、单 selector 最多 4 KiB UTF-8、JSON 深度 16、JSON value 节点 4,096。

以下 18 个 boolean compiler options 会真实参与 effective-options merge：

```text
strict
noImplicitAny
strictNullChecks
strictFunctionTypes
strictBindCallApply
strictPropertyInitialization
strictBuiltinIteratorReturn
noImplicitThis
useUnknownInCatchVariables
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

未显式设置时，`strict` 默认为 `true`，八个 strict-family 子项继承 effective `strict`，其余
选项默认为 `false`。插件随后覆盖 target、module、moduleResolution、sourceMap、outDir、
noEmitOnError 等冻结 profile 值。effective options 继续以 canonical JSON 和 SHA-256 写入结果，
caller 不声明或伪造 effective-options fingerprint。

`files/include/exclude` 继续使用已冻结的 C7 语义：`files` 与 `include` 取并集，`exclude` 只影响
`include`，缺省 include 为 `**/*`。selector 必须是安全的 NFC project-relative UTF-8 路径或
受控 glob；绝对路径、反斜杠和 `.`/`..` 逃逸仍被拒绝。

## warning-ignore 与结构性拒绝

revision 2 不再因为编辑器附加字段或未来 TypeScript 非结构性选项而拒绝整个项目：

| 输入 | 行为 | diagnostics code |
|---|---|---:|
| 未分类的 top-level property | warning 后忽略 | `910001` |
| 未分类的已知或未知 compiler option | warning 后忽略 | `910002` |

这些记录以 `Warning`、`phase=options` 发布，不携带虚构的 URI/range，不计入 error count，因而
不会触发 `noEmitOnError`。每次编译最多发布 64 条逐项 warning；超出时追加一条有界汇总。

能够改变 profile、输出拓扑、解析/宿主权限或执行代码的结构性输入仍 fail closed：

- `extends/references/watchOptions/typeAcquisition/compileOnSave`：
  `CONFIG_UNSUPPORTED_FEATURE`；
- `target/module/moduleResolution/outDir/sourceMap/noEmitOnError` 等 profile-owned 选项：
  `CONFIG_PROFILE_CONFLICT`；
- `baseUrl/paths/plugins/typeRoots/types/lib/rootDirs` 等解析或 host escape：
  `CONFIG_UNSUPPORTED_FEATURE`；
- `outFile/noEmit/declaration/inlineSourceMap/sourceRoot/mapRoot/incremental` 等输出拓扑：
  `CONFIG_UNSUPPORTED_FEATURE`；
- `customTransformer/customTransformers/transformers`：`CONFIG_UNKNOWN_PROPERTY`。

形似回调名但不是 compiler option 的 `before/after/afterDeclarations` 只是不可执行的 JSON 数据，
按未知项 warning-ignore；实际 transformer authority 的三个入口仍全部拒绝。Worker 仍只读取
snapshot VFS，不使用 `ts.sys`、网络、动态 `importScripts` 或 caller callback，且 `emit()` 不接收
caller-supplied transformer。

## 验收覆盖

- Kotlin policy/contract/metadata/public-classifier 单元测试绑定 revision 2、两个 warning code、
  policy fingerprint 与 exact raw-config authority；
- Node C1–C7 harness 覆盖 JSONC、18 项 merge、selector、escape、package metadata、snapshot
  resolver，并验证 warning 上限为 64 条逐项记录加一条汇总；
- Binder instrumentation 用 `strict=false` 编译一个在默认 strict profile 下会产生 implicit-any
  error 的项目，同时验证未知 top-level/compiler option 产生 `910001/910002` warning、成功 emit、
  output manifest 的 raw-config/effective-options provenance；该 focused test 已同时通过 Android
  12/API 31 实机、API 35 实机与 API 37 模拟器；
- config-none 路径、same-UID debug control plane、frozen AIDL 与 schema v1 均保留回归覆盖。

policy revision 1 fixture 与旧 C1–C7 文档继续保留为历史 provenance，不是当前 public authority。
当前 public policy 的 canonical fingerprint 是上文的 `64ef…598b`；compiler escape policy 同步
升级为 revision 2，fingerprint 为
`4f42fdf38782fd8d94b40a1bf5797295698b9ba24ca0f555c30186e4b43a1e98`。
