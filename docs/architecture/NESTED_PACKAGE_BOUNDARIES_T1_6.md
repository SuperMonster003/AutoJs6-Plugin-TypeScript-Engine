# T1-6 嵌套 package boundary / monorepo 初步

状态：**完成（2026-08-27）**。NodeNext revision 2 现可在同一 AutoJs6 Node 项目中
接收多个受控 `package.json`，并按源码所在的最近 package boundary 分别生成 ESM 或
CommonJS。桌面 Worker、公开 Binder、AutoJs6 宿主和 Node Runtime 已完成同一 ESM +
CommonJS 双子包项目的闭环验证。

## 用户可见结论

T1-6 支持两种 workspace 根目录：

- `packages/<name>`；
- `packages/@scope/<name>`。

`<name>` 与 `@scope` 使用小写 ASCII package 名字符域：首字符为字母或数字，其余可含
字母、数字、点、下划线和连字符。文件名必须精确写成小写 `package.json`。最小项目为：

```text
workspace-project/
├── project.json
├── package.json
├── main.mts
└── packages/
    ├── esm-package/
    │   ├── package.json       # {"type":"module"}
    │   └── value.ts           # ESM .js
    └── commonjs-package/
        ├── package.json       # {"type":"commonjs"}
        └── value.ts           # CommonJS .js
```

根 `package.json` 在存在任一 workspace package 时必须存在；它可以只写 `{}`，因为
`.mts` 入口自身已经明确是 ESM。普通 `.ts` 仍由最近的 `package.json.type` 决定格式：

| 源码 | 最近 boundary | 输出语义 |
|---|---|---|
| `main.mts` | 任意 | ESM `.mjs` |
| `main.cts` | 任意 | CommonJS `.cjs` |
| `packages/esm-package/value.ts` | `type: module` | ESM `.js` |
| `packages/commonjs-package/value.ts` | `type: commonjs` | CommonJS `.js` |

本项不开放 caller `node_modules`、npm bare specifier、package `exports`/`imports` 或安装
流程；这些仍属于 T2。`package.json` 只是编译和运行时的只读模块格式元数据。

## 为什么没有协议 revision

既有 package metadata schema/policy revision 1 从一开始就包含：

- 一个必需的项目根 `package.json`；
- 显式排序的 `dependencyRoots`；
- 根及各 dependency root 下的多个 package metadata 文件；
- 每个文件的 path、size、SHA-256 与 inventory fingerprint；
- 最多 128 个 roots、256 个 metadata 文件、单文件 64 KiB、合计 1 MiB；
- strict UTF-8/JSON、重复键拒绝，以及顶层 `scripts` 禁止。

因此 T1-6 激活的是已有 v1 正能力，不改变 AIDL、input/output archive schema、NodeNext
profile revision、controlled-tsconfig revision 或 package policy fingerprint。旧宿主仍可只
发送根 metadata；新宿主发送多文件 inventory 时，旧协议字段的 canonical identity 继续
逐字成立。

宿主编译缓存格式从 1 升为 2，因为缓存键现在必须包含所有 roots、metadata 文件、
`typeState` 与 inventory fingerprint。该变化只失效旧本地缓存，不改变跨进程协议。

## 最近边界判定

package metadata 的 canonical boundary 位于项目命名空间，例如 `/packages/esm-package`；
编译源码位于 Worker VFS 的 `/sources/packages/esm-package/value.ts`。此前 Native 与 Worker
直接比较这两种路径，导致任何 workspace 源码都错误回落到根 boundary。

T1-6 在唯一边界查询入口先执行命名空间投影：

```text
/sources/packages/esm-package/value.ts
                  ↓ remove archive-only /sources layer
/packages/esm-package/value.ts
                  ↓ longest containing boundary
/packages/esm-package
```

Native `R2NodePackageBoundaryLookup`、Node resolution metadata catalog 与 production
Worker 使用同一规则：候选必须是根 `/`，或是源码路径的完整目录前缀；多个候选时选择
路径最长者。`.mts/.cts` 的扩展名语义仍优先，普通 `.ts` 才消费最近 boundary 的
`typeState`。

## 归档、输出与运行时闭环

AutoJs6 宿主不再把第二个 `package.json` 当作无条件错误，而是在建立冻结项目 snapshot
时完成以下处理：

1. 只收集根文件和 `packages/<name>` / `packages/@scope/<name>` 受控 roots；其他位置的
   `package.json` 仍明确拒绝，避免出现宿主与插件对项目拓扑理解不同的静默忽略；
2. 对每个文件执行 canonical-path、符号链接、UTF-8、strict JSON、重复键、复杂度、
   `scripts` 与配额检查；
3. 把完整 roots/files inventory 写入正式 input archive，并把所有 metadata 字节纳入
   caller 输入配额、manifest hash 与编译缓存键；
4. 宿主解析 output archive 时，按每个 `.ts` 的最近 metadata 验证 generated mapping
   中的 `moduleFormat`、`packageBoundary` 和 inventory fingerprint；
5. 运行时项目复制并再次核验所有 package metadata，使 Node Runtime 看到的目录边界与
   编译期完全一致。

所有 boundary 的顶层 `scripts` 均返回既有 `PACKAGE_LIFECYCLE_FORBIDDEN`，从未进入
执行路径。该规则适用于根和每个 workspace 子包，不存在“子包 scripts 例外”。

## 自动化与真机证据

`verifyT1NestedPackageBoundaries` 依赖真实 production Worker 专项。该专项在一个
TypeScript 6.0.3 incremental Program 中同时编译四个入口，其中 workspace ESM `.ts`
产生 ESM `.js + .map`，workspace CommonJS `.ts` 产生 CommonJS `.js + .map`；两份生成
模块分别由桌面 Node 的 ESM loader 与 CommonJS VM 实际执行并得到 `42`。当前生成物
SHA-256 为：

- workspace ESM：
  `c311b411058914e446b3af99db59945a671a3fcd879cb4760893a465377068ef`；
- workspace CommonJS：
  `77cffdbf86a640479036b33703f4558434e9ef72bcfbc1aab514a9b4d5014e73`。

Kotlin 单元测试另冻结 `/sources` → 项目命名空间投影、longest-boundary 选择、ESM/CJS
generated mapping 与 canonical boundary evidence。公开 Binder instrumentation
`nestedPackageBoundariesCompileEsmAndCommonJsThroughPublicBinder` 在 Xiaomi
23046RP50C、Android 15 / API 35、WebView `130.0.6723.86` 上编译同一双子包 archive，
结果为 1/1 通过，Gradle `BUILD SUCCESSFUL in 20s`。

同一设备上的 AutoJs6 `6.8.0 (5276)` 与 Node Runtime 插件 `1.2.0` 随后运行宿主用例
`executesEsmAndCommonJsWorkspacePackagesInOneNodeProject`：单个 `.mts` 入口静态导入两个
`.ts` 子包，经宿主归档、公开 Binder、输出 authority、precompiled-only handoff 与真实
Node engine 后输出 `42`；结果为 1/1 通过，Gradle `BUILD SUCCESSFUL in 22s`。

该专项已挂入 `verifyCompilerSmoke`，根项目 `check --offline` 会持续验证 Worker 执行、
Native authority、AndroidTest 源码和用户文档锚点。用户配置与示例见
[配置与配额参考](../user/CONFIGURATION.md#workspace-package-boundary)和
[快速上手](../user/QUICK_START.md#5-node-workspace-子包)。
