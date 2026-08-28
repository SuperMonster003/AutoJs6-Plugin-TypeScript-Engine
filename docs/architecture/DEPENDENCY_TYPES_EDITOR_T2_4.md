# T2-4 依赖类型与编辑器同步

## 结论

T2-4 让编译器和 Ace Editor 消费 T2-1 同一份冻结依赖类型层。package 的
`types`/`typings`、TypeScript 6 `typesVersions` 分支、声明文件内部相对导入以及安装的
`@types/*` 都会进入 TypeScript Program；Ace 将完全相同的声明字节投影为只读项目 URI，
所以成员补全、hover 与 strict diagnostics 不再依赖一份另行维护的编辑器类型副本。

本项没有扩大运行时权限，也没有新增 Binder 方法。依赖仍由宿主在执行开始时冻结，设备端
不运行 npm/pnpm、install 或 lifecycle script。T2-3 的 Rhino/Node runtime loading 继续
消费相同 exact snapshot。

## Resolver policy revision 2（T2-4 完成身份）

| 项目 | 当前值 |
|---|---|
| resolver policy revision | `2` |
| resolver policy fingerprint | `6bf402612d6aec48947d11d1bbb44848cef3cbeb54d11560d230224b233e3dda` |
| dependency-layer policy revision | `1`（未改变） |
| dependency-layer policy fingerprint | `7564a07d9f9e980dab795312b1dfbfe719775fb3392536149efd558dc70fa68b` |
| package VFS root | `/node_modules/` |
| condition order | `types`, `import`, `require`, `node`, `default` |
| fallback order | `typesVersions`, `types`, `typings`, `module`, `main`, `index` |
| type references | package `types`/`typings`, installed `@types`, `typesVersions` |
| runtime loading | exact read-only T2-3 snapshot |

T2-2 revision `1` 是 bare specifier 首次开放时的历史身份，未被改写。revision `2` 通过宿主
API、插件 API、service metadata、framed start authority 与编译缓存协商，以 append-only
方式表示新增的类型解析能力。任何一侧仍携带旧 fingerprint 都会在进入 Program 前拒绝，
不会得到部分启用的类型行为。

T2-5 真实 lodash 发布包证明，仅把 installed `@types` 加入 type-directive overlay 还不够：
裸模块解析会先在 `main` JavaScript 停止。当前 resolver policy 已 append-only 升为 revision
`3`，fingerprint 为
`6241375f2ea49ac2c2d2b3b79b28b7c1ee4adde75c989defc61e252c89ced2bd`；当 package 自身没有
声明、TypeScript 只得到 runtime JavaScript，且同一冻结层确实安装了对应 DefinitelyTyped
package 时，模块解析先返回该声明再保留运行时 specifier。完整真实包证据见
[npm 真实包兼容矩阵](../user/REAL_PACKAGE_MATRIX.md)。本节表格保留 revision `2`，用于记录
T2-4 完成时的精确协议身份。

T2-6 继续保留上述类型回退，并把当前 resolver policy 升为 revision `4`、fingerprint
`d8207539237d2a08a6b97b530c5e6215f4b73311fdd6954ce9f8004717ebd635`，同时把 Ace 的
dependency-layer identity 升至 policy revision `2`。Ace 对可接受纯 JS package 的类型
行为不变；当 snapshot 含 `.node`/node-gyp/install lifecycle 时，它改为发布与宿主相同的
稳定错误诊断。详见 [T2-6 原生依赖边界](NATIVE_DEPENDENCY_BOUNDARY_T2_6.md)。

## 编译器 Program 类型层

Worker 从已经逐路径、size、SHA-256 验证的 dependency VFS 派生排序去重的 installed
`@types` 名称。普通 package 使用 `@types/name`；scoped package 按 DefinitelyTyped 规则
使用 `@types/scope__name`。派生数组与 Program options 都被冻结，并覆盖 profile 基线中
原本为空的 `types`，因此不会隐式扫描 WebView 或 Android 文件系统。

resolver 使用 TypeScript 6.0.3 的 package 语义，同时补齐冻结 VFS 所需的显式路径：

- `exports.types`、根 `types`/`typings` 与 `typesVersions`；
- `typesVersions` 根据当前 TypeScript `6.0.3` 选择匹配分支，命中后不回退旧 `types`；
- `.d.ts/.d.mts/.d.cts` 中的相对 import/export；
- declaration 继续引用的 bare/scoped package；
- `/// <reference types="..." />` 与自动 installed `@types`；
- NodeNext 的 import/require mode，以及 Rhino CommonJS/Node10 emit 下的 package type
  resolution。

自动 `@types` 是 Program-only overlay。Worker 对外返回的 provisional profile options 和
controlled tsconfig canonical options 仍保持原 authority，不能把自动派生的 `types` 写入
result metadata。真机首次验收正是通过这一边界发现并修复了内部 Program options 与公开
effective-options 共用对象的问题；`verifyT2DependencyTypes` 现在锁定 base options 的
`types=[]` 与 Program overlay 的 `types=["ambient"]` 必须同时成立。

## Ace 冻结类型层

Ace 在 `setDocumentPath` 后由独立后台线程寻找最近项目 boundary。Rhino 使用
`project.json`/`tsconfig.json`，Node 还允许根 `package.json`。捕获规则与编译依赖层保持
一致：

- 接受 `.cjs/.cts/.js/.json/.jsx/.mjs/.mts/.ts/.tsx`；
- 排除 `.bin`、docs/tests/examples/bench/coverage、Markdown 与 Source Map；
- 拒绝 symlink traversal、非 canonical path、非法 UTF-8 与越界路径；
- 8192 files、8 MiB single、64 MiB total、8 MiB lockfile、1.5 MiB path bytes；
- `package-lock.json` 优先、`pnpm-lock.yaml` 次之，否则使用 exact content identity；
- inventory 与 dependency-layer fingerprint 使用和编译器相同的 canonical domain。

冻结文本映射到 `file:///autojs6/editor/node_modules/...`，根 `package.json` 同时作为只读
project metadata 提供。Android bridge 只按 URI 返回当前 snapshot 中的文本，不接受任意
文件路径读取。LSP client 把 file URI inventory、`@types` 名称、两个 dependency
fingerprint 与 resolver policy identity 一起交给 TypeScript service；任一 identity 或
inventory 改变都会重建 service，而不是把新旧声明混用。

T2-4 完成时，只要项目类型层存在，Ace 就不再隐藏 TS2307、TS2792 与 TS7016；该阶段只
承诺依赖声明与当前文档一致。T4-1 后此策略已收紧：只有完整且经验证的项目源码 snapshot
才解除上述模块解析诊断屏蔽，单独存在依赖类型层时仍采用单文档降级，避免把未进入语言
服务的相邻源码误报为缺失模块。T4-2 已在同一冻结层上增加受约束定义跳转，见
[T4-2 跨文件智能](CROSS_FILE_INTELLIGENCE_T4_2.md)；auto-import 与 rename 继续由后续
T4 项实现。

## 一致性验收

桌面编译器 verifier 和 Ace runtime verifier 使用同一语义 fixture：

1. dayjs 的 legacy `types` 暴露 `legacyOnly()`；
2. `typesVersions >=6.0` 改为含嵌套 `./internal` 的 `Dayjs`，暴露 `format()`/`unix()`；
3. `@types/ambient` 提供全局 ambient 值；
4. 正确用法要求 hover 为 `const value: Dayjs`，completion 含 `format`/`unix` 且不含
   `legacyOnly`；
5. `const wrong: number = value.format()` 在 Rhino 与 Node 都只产生 TS2322。

已通过的门禁包括：

- `node --check compiler-worker.js`；
- `:app:verifyT2DependencyTypes` 与既有 `:app:verifyT2BareSpecifierResolution`；
- Ace `:app:testDebugUnitTest`、`:app:verifyAutoJs6LspRuntime` 与 Android test compilation；
- API 35 小米 23046RP50C Ace bridge smoke：`Time: 0.412`、`OK (1 test)`；
- 同设备 AutoJs6 Binder 双 profile Program/emit/diagnostic：`Time: 35.364`、
  `OK (1 test)`、`INSTRUMENTATION_CODE: -1`。

Ace bridge smoke 另确认 Android 实际发布两个 64-hex fingerprint、resolver revision `2`、
installed `ambient` type directive，并能只读返回 dayjs declaration。宿主真机用例先编译
正确脚本，再把返回值错误赋给 `number`，确认 Rhino 与 Node diagnostic code 集合精确相同。

## 明确边界

- 编辑会话内的依赖层是冻结 snapshot，不监听 `node_modules` 热变更；更新安装内容后重新
  打开编辑会话。
- package `imports` 尚未开放；native `.node`/node-gyp 边界由 T2-6 处理。
- T2-4 不让依赖 JavaScript 进入 `allowJs` 隐式转译，也不改变 Rhino 对 ESM-only package
  的稳定拒绝。
- T2-4 不实现项目源码跨文件 navigation/refactor；这些属于 T4，而依赖类型层已经为 T4
  提供统一输入。
