# T2-3 依赖运行时装载

## 结论

T2-3 已让编译期冻结的同一份 `node_modules` 依赖层直接进入 Rhino 与 Node 的运行时。
它不重新扫描一份“看起来相同”的安装目录，也不在执行时运行 npm、pnpm、install、
postinstall 或其他 lifecycle：宿主保留编译结果携带的 exact dependency inventory，执行前
逐文件复核 canonical path、size 与 SHA-256，然后把已验证字节交给目标 runtime。

两条 profile 路径共享依赖身份，但保持各自原生模块语义：

- Node 将依赖作为 host-only workspace module-source overlay 注入 Node Runtime 插件的私有
  workspace archive；V8/Node resolver 按真实 package `exports` 与 ESM/CJS 规则装载。
- Rhino 将依赖物化到单次执行临时根的 `node_modules/`，并在 Rhino CommonJS `require()`
  前增加 bare/scoped package resolver；dual package 选择 `require` 分支，ESM-only package
  返回稳定错误，不做隐藏的 allowJs 降级转译。

T2-1 dependency-layer schema/policy revision `1` 与 T2-2 resolver policy revision `1` 均保持
原身份。T2-3 没有增加 Binder 方法、Bundle 数组或编译 profile revision；它消费的就是已
认证编译输出中的依赖层。

## 精确快照交接

`TypeScriptCompilationOutput.Emitted`、`TypeScriptExecutableSource` 与 runtime project 会一直
保留 `TypeScriptPreparedDependencyLayer`。执行前只处理
`dependencies/node_modules/` inventory，并完成以下一致性检查：

1. archive path 可逆映射为项目根内的 `node_modules/...`；
2. lexical path 与 canonical path 相同，目标仍是普通文件；
3. 当前 size 与编译快照 descriptor 相同；
4. 重新读取的完整字节 SHA-256 与 descriptor 相同；
5. 文本仍是 strict UTF-8。

任一项改变都在 runtime 建立前返回
`ERR_AUTOJS6_TYPESCRIPT_RUNTIME_DEPENDENCY_CHANGED`。验证后的 `ByteArray` 会清零；Node
overlay 与 Rhino 临时项目各自持有不可变字符串副本，因此后续解析不再读取原项目文件。

## Node 路径

`TypeScriptNodeRuntimeProjectBuilder` 把依赖源放入独立的 `workspaceModuleSources`，与编译
生成模块使用的 `moduleSources` 分开。该字段只存在于 AutoJs6 宿主内部：

1. `NodePluginJavaScriptEngine` 建立预编译 snapshot 请求；
2. `NodeJsPluginWorkspaceArchiveTransport` 在宿主私有 ZIP 中按项目相对路径写入依赖字节；
3. Binder 仍只得到既有 workspace archive PFD 与有限 metadata；
4. Node Runtime 在自己的私有 workspace 展开完整项目，普通 Node resolver 直接看到
   `node_modules`；
5. overlay 路径及其后代全部标记为 ephemeral，插件输出同步阶段会读取并丢弃这些条目，
   不会把生成物、修改或删除同步回宿主项目的依赖目录。

overlay 优先于随后进行的项目树收集，因此项目磁盘上同名文件不会覆盖刚刚验证的 snapshot
字节。路径碰撞、越界、符号链接与 transport 保留目录仍按 workspace archive 的既有规则
拒绝。

## Rhino 路径

`TypeScriptMaterializedRhinoProject` 在 `cache/typescript-rhino-projects/run-*` 下建立单次执行
目录，依次写入项目 runtime support 文件、依赖层与编译生成模块。依赖 JSON 会转换为
`module.exports = <json>`，使 Rhino 的 CommonJS loader 与项目 JSON 行为一致；原始
`package.json` 文本另外保留给 resolver，不会解析已转换的 JavaScript。

`TypeScriptRhinoCommonJsRequire` 只改写能够由当前依赖快照解析的 bare/scoped request；相对
路径、`node:*`、`autojs6:*` 与其他既有 request 继续交给原 Rhino loader。解析从调用模块
所在目录开始，逐级查找父级 `node_modules`，所以
`parent/node_modules/shared` 会优先于项目根 `node_modules/shared`，嵌套版本不会坍缩成全局
别名。

package 入口规则如下：

| package 形态 | Rhino 选择 |
|---|---|
| `exports` condition object | 按 `require`, `node`, `default` 顺序选择 |
| dual package | 选择 CJS `require` target |
| 无 `exports` 根入口 | `main`, 然后 `index.cjs` / CommonJS `index.js` / `index.json` |
| 未声明 `type: module` 的 `.js` | 作为 CommonJS |
| `.cjs` / `.json` | 直接支持 |
| `.mjs` / `.mts` 或 `type: module` 下的 `.js` | 稳定 ESM-only 错误，并提示改用 Node profile |
| `.ts` / `.cts` 等未生成依赖入口 | 稳定 unsupported-entry 错误，不在 runtime 临时转译 |

`exports` 支持精确 subpath、单 `*` pattern、数组 fallback 与 `null` blocker。target 必须留在
package 根内；最近的嵌套 `package.json.type` 决定 `.js` 的格式。执行完成、异常或引擎关闭
都会递归清理该次临时根。

## 稳定运行时错误

| 错误码 | 含义 |
|---|---|
| `ERR_AUTOJS6_TYPESCRIPT_RUNTIME_DEPENDENCY_CHANGED` | 编译后到 runtime snapshot 建立前，依赖路径、类型、size、SHA-256 或 UTF-8 内容改变 |
| `ERR_AUTOJS6_TYPESCRIPT_RHINO_ESM_ONLY_DEPENDENCY` | Rhino package 只提供 ESM/import 入口，或选中的 `.js` 位于 `type: module` boundary |
| `ERR_AUTOJS6_TYPESCRIPT_RHINO_DEPENDENCY_ENTRY_UNSUPPORTED` | CJS target 缺失，或入口扩展名不能由 Rhino CommonJS 执行 |
| `ERR_AUTOJS6_TYPESCRIPT_RHINO_DEPENDENCY_PACKAGE_INVALID` | `package.json` 非 object、target 非法或 target 越出 package 根 |
| `ERR_AUTOJS6_TYPESCRIPT_RHINO_DEPENDENCY_PATH_NOT_EXPORTED` | 请求的 root/subpath 被 blocker 阻止，或没有 `require`/`node`/`default` 可用分支 |

## 验收与回归

宿主 JVM 测试覆盖 exact inventory 复核、编译后依赖变更、dual package 的 `require` 选择、
嵌套 `node_modules` 最近版本及 ESM-only 稳定错误。Node workspace transport 测试另外验证
overlay 输入与 plugin output 均不会写回宿主依赖。插件侧测试覆盖 current Rhino 与 Node
revision 2 的 heap/file-backed snapshot、framed resolver authority 和 Worker VFS admission。

2026-08-28 在 API 35 小米 23046RP50C 上运行宿主用例
`executesSameDualPackageSnapshotThroughRhinoAndNodeProfiles`。项目只安装一份
`dual-runtime`：`exports.types` 指向 `index.d.ts`，`exports.require` 指向返回
`cjs:42` 的 `index.cjs`，`exports.import` 指向返回 `esm:42` 的 `index.mjs`。同一项目先由
Rhino 完成 CommonJS 执行，再由 Node Runtime 完成 ESM 执行；结果为 `Time: 19.304`、
`OK (1 test)`、`INSTRUMENTATION_CODE: -1`。
