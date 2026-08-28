# T1-3 Controlled `baseUrl`/`paths` Revision 4

状态：2026-08-26 已实现并进入日常 `check`。本文是当前 controlled-tsconfig
authority；[T1-2 revision 3](CONTROLLED_TSCONFIG_T1_2_REVISION3.md) 与更早的
R2/S1 文档继续冻结各自发布时的兼容身份。

## 决策与身份

T1-3 采用 append-only policy revision 4，不改变 `configSchemaRevision=2`、AIDL、
formal input/output archive schema 或 profile revision。当前 Rhino revision 2 与
NodeNext revision 2 都接受 revision 4；旧 revision 按精确 fingerprint 分派，绝不
“升级后再解释”。

| policy | profile | value options | 状态 |
|---|---|---|---|
| revision 2 | Rhino | 无 | 永久兼容；18 个布尔项与原 fingerprint 不变 |
| revision 3 | Rhino / NodeNext | `target`、`lib` | 永久兼容；20 个布尔项与原 fingerprint 不变 |
| revision 4 | Rhino / NodeNext | `target`、`lib`、`baseUrl`、`paths` | 当前 authority |

revision 4 policy fingerprint：
`4a9a5e70150f81e1ec9a5e2f749cc081f93f207f092300c96a79abfdcc152dab`。

parent revision 3 fingerprint：
`1f77d37080466f2752176fb5aa1b96e9a3f3dbdc89b853f30b4a0fed4313e90a`。

legacy revision 2 fingerprint：
`64ef1cf36d6531f84088cec49127e6407b107ab86eeaf7f4512358fd1343598b`。

## 规范化 alias authority

Native 层把解析结果冻结为 `R2ControlledPathAliasTable`。表内只保存 project-relative
值，不保存 Android path、archive offset 或 Worker VFS path；因此同一 value object 可在
T4 编辑器服务中复用，而不会复制另一套 alias 语义。canonical effective options revision
也升为 4，并把 `baseUrl` 与按 key 排序的 `paths` 纳入 fingerprint/cache identity。

| 约束 | revision 4 值 |
|---|---:|
| alias keys | 最多 128 |
| targets / key | 最多 8 |
| targets aggregate | 最多 512 |
| key / target / baseUrl | 最多 4096 UTF-8 bytes |
| wildcard | key 与每个 target 各最多一个，且数量相同 |
| 字符域 | safe printable ASCII segments；拒绝反斜杠、冒号、`?`、`#` |
| 根边界 | `baseUrl`/target 只能是项目相对 pattern；拒绝绝对路径、`.` segment 与 `..` |

`paths` 非空而未写 `baseUrl` 时，effective `baseUrl` 为 `.`。alias key 必须是 bare
specifier，不能以 `.` 或 `/` 开头。key 以 code-unit 排序；target 保留用户顺序并去重。
非法形状返回既有 `CONFIG_INVALID_VALUE`、`CONFIG_PATH_INVALID` 或
`CONFIG_COMPLEXITY_EXCEEDED`，没有新 AIDL/terminal error。

## Snapshot-only 编译解析

Worker 在建立 `Program` 前才把 project-relative authority 展开到 `/sources`：

1. exact key 先于 wildcard；wildcard 选择最长 prefix，再选择最长 suffix；
2. target 按配置顺序替换 `*` 并尝试；未写扩展名时，Rhino 尝试
   `.ts/.tsx` 与对应 `index`，Node 尝试 `.ts/.mts/.cts` 与对应 `index`；
3. 只有 accepted VFS 中实际存在、且位于 `/sources/` 的 executable source 才能命中；
4. 命中 alias key 但全部 target 缺失时直接 unresolved，使 TypeScript 在原 import 上
   产生 `TS2307`，不会偷偷落入同名 package；
5. 未命中 `paths` 时可先按 `baseUrl` 尝试项目源码，仍未命中才委托既有 Rhino/Node
   resolver。相对 specifier、绝对形式与 `#imports` 不走 alias matcher。

resolver 被安装在既有 snapshot compiler host 上；没有 `ts.sys`、Android filesystem 或
网络 fallback。配置选择的 roots 仍由 `files/include/exclude` 决定，可达 alias dependency
由 TypeScript `Program` 正常纳入；transport manifest 的 `rootPaths` 全量 source inventory
约束保持不变。

## 运行时 specifier 与 Source Map

TypeScript 的 `paths` 只负责类型检查/模块解析，不会改写 JavaScript import。因此 revision 4
在 emit 时安装一个插件内部固定的 `before` transformer：它只查询上述 frozen alias authority
与 accepted VFS，把已解析的 `import`、`export ... from`、`import = require()`、动态
`import()` 和 `require()` 字符串改成目标 output 的相对 specifier。

输出后缀由既有 generated-module policy 决定：Rhino 为 `.js`；Node 根据 source kind 与
package boundary 为 `.js/.mjs/.cjs`。例如 `/sources/src/index.ts` 中的
`@feature/fault` 会发射为 `./features/fault.js`。运行时因此无需 alias loader，也不会把
TypeScript-only specifier 泄漏给 Rhino/Node。

这是 plugin-owned transform，不是 caller transformer authority。`customTransformer`、
`customTransformers`、`transformers` 及 callback-shaped tsconfig 字段仍按历史 C4 契约拒绝
或保持 inert；C4 门禁同时锁定 revision 4 的 transformer 只有一个内部构造调用点。

replacement literal 继承原 AST node/text range，外部 Source Map 仍由同一次 TypeScript emit
原生生成。缺失 alias 的编译诊断继续指向原 TS import，运行异常也经现有 generated mapping
与 Source Map 回到原 TS 文件/行/列。

## 验证证据

`verifyT1ControlledPathAliases` 独立重建 revision 4 canonical policy/fingerprint，并通过
实际 framed Worker 协议对同一 12-source `@/` 工程执行：

- Rhino CommonJS 编译与执行，结果为 `55`；
- Node CommonJS 编译与执行，结果为 `55`；
- 所有 alias emit 均变为带后缀的 output-relative specifier；
- 缺失 alias 产生 `TS2307`，source path/range 保留在原 `.ts`；
- `alias-runtime-fault` 的生成栈经 map 恢复到
  `/sources/src/features/fault.ts:2`；
- Rhino alias effective fingerprint 为
  `b2da71259b531113171de449e3226a8b5d7870620063feb4ac81459dedc4146b`；
- Node alias effective fingerprint 为
  `341d3f22c61fd7488bae6de0ed6003376e62cdfdf394ac153677d7dc3b67c546`。

Kotlin tests 另冻结 policy/effective canonical JSON、revision 2/3 compatibility、native/Worker
fingerprint parity、snapshot/framed transport 与 result parsing。API 35 Xiaomi 23046RP50C /
WebView `130.0.6723.86` 通过 public Binder 的双 profile 12-file compile/archive instrumentation，
核对 12 个 emitted modules、相对 require、map source 与 exact revision 4 fingerprints。

该 gate 挂在 `verifyCompilerSmoke` 下；根项目 `check --offline` 会自动执行。用户可见语法、
限制和错误处理见[配置与配额参考](../user/CONFIGURATION.md)与
[错误码目录](../user/ERRORS.md)。
