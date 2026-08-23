# S1-5 NodeNext Revision 2 Public Admission

状态：**完成（2026-08-23）**。

本切片把此前已经覆盖 input archive、snapshot、Worker Program、framed result、generated
mapping 与 formal output archive、但只允许内部到达的 exact NodeNext revision 2 接入 public
Binder 编译路径。AIDL v1、contract version/hash、input/output archive schema v1 与 diagnostics
schema v1 均未修改；公开增量只使用 `TypeScriptR2Contract` 的 Bundle keys。

## 对宿主发布的 authority

`getCompilerInfo()` 继续用无前缀的 `profileRevision/config*` 与 runtime/declaration fingerprint
描述默认 Rhino authority，同时增加以下 Node 专用字段，避免宿主把两个 profile 的 revision 或
fingerprint 混用：

| Key | S1-5 含义 |
|---|---|
| `nodeProfileRevision` | exact public Node profile revision，当前为 `2` |
| `nodeConfigSchemaRevision` | Node config schema revision，当前为 `1` |
| `nodeConfigKind` | 当前精确为 `none` |
| `nodeRuntimeCapabilitiesFingerprint` | `.ts/.mts/.cts`、NodeNext module kind 与 output policy 的完整 runtime fingerprint |
| `nodeDeclarationProfileFingerprint` | common + 59 个 Node declaration files 的 profile fingerprint |

Node 请求仍同时携带冻结 v1 envelope 的 `targetProfile=node`、
`profileSchemaRevision=1`、`runtimeCapabilitySchemaRevision=1`、compiler distribution/declarations
fingerprint、input archive/manifest SHA-256 与 PFD。请求中的 `profileRevision/config*`、runtime
fingerprint 和 declaration-profile fingerprint 必须逐项复制上表对应 Node authority；不能使用
默认 Rhino 字段。

## 准入与失败关闭

Public classifier 只接受两个 exact authority：

- Rhino revision 1，可选 `configKind=none` 或受控 `configKind=tsconfig` revision 2；
- Node revision 2，只接受 `configSchemaRevision=1/configKind=none`。

Node revision 1 继续由 `PROVISIONAL_NODE_NEXT` 作为 private/debug compatibility default 使用，
但 public request 会以 `ERROR_CONTRACT_MISMATCH` 拒绝。未知 profile revision、交叉 profile
runtime/declaration fingerprint、Node controlled tsconfig、`.d.ts/.d.mts/.d.cts` executable entry、
不匹配的 generated mapping/source URI 与 unsupported source kind 均继续 fail closed。

成功请求沿用 formal input archive revision 1：`.ts/.mts/.cts` 可作为 Node roots；Worker 以
TypeScript 6.0.3 NodeNext Program 推导 module format，并分别产出 `.js/.mjs/.cjs`。native result
authority 从 accepted snapshot 复算每个 source 的 generated URI、module format、module/map path
和 mapping fingerprint；formal output archive revision 1 写入 `modules/`、`maps/`、
`diagnostics.json` 与 `compilation-manifest.json`。

## 推进中修复的真实阻断

首次 Public Binder 运行已经进入 Node revision-2 Program，但一个不含任何 import 的用户项目仍
得到 37 条 `NODE_RESOLUTION_PACKAGE_NOT_ALLOWED`。这些错误全部来自插件自带 Node `.d.ts`
之间的 ambient bare-module 引用，例如 `https.d.ts` 引用 `http`、`rhino_compat.d.ts` 引用
`device/ui/storage`。旧 resolver 把这些 plugin-owned 引用误送入 caller package resolver，而
config-none 请求没有 caller package metadata，因此必然 diagnostics-only。

修复后，Worker 从 exact snapshot 中 plugin-owned、fingerprint-bound 的
`/declarations/node/*.d.ts` 使用 TypeScript AST 收集顶层 string-literal ambient module
declarations，并建立闭集 `module specifier → declaration target`。这同时覆盖 `http`、
`node:http`、`autojs6:compat` 等别名。命中只授予 snapshot 中已存在的 trusted declaration，
不读取文件系统、不访问网络，也不把 caller 文件加入可信域。

用户源码的任意 bare package 不会因此放行：只有由锁定 Node declaration AST 声明的精确模块名
走本地 catalog；其他 bare specifier 仍进入既有 package-metadata/package-target resolver，在
缺少 allowlist 或 target authority 时返回原有稳定错误。

## 验收结果

Focused Public Binder 用例使用同一项目中的：

- `sources/module.mts`，期望 `modules/module.mjs + maps/module.mjs.map`；
- `sources/common.cts`，从 trusted `http` ambient module 导入类型并期望
  `modules/common.cjs + maps/common.cjs.map`。

用例还验证 terminal outcome 为 `emitted`、diagnostics 为 0、emitted modules 为 2、formal
manifest 的 `targetProfile=node/profileRevision=2`、entry module、Node output-policy fingerprint
和两条 generated mappings。它已在以下三种环境通过：

- Android 12 / API 31 实机；
- API 35 实机；
- API 37 x86_64 模拟器。

JVM 单元测试覆盖 exact public classifier、legacy Node revision-1 reject、Node revision-2
config-none accept、Node controlled-tsconfig reject 与 end-to-end registry；既有 P2 Program/result/
output aggregate 也在提升 authority 后保持通过。下一活动项是 Roadmap S1-6 验证层瘦身。
