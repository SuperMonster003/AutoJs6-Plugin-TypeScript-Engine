# Architecture Boundaries

## 组件职责

### AutoJs6 host

- 识别 TypeScript 源语言和最终执行引擎.
- 读取普通源码, 未保存编辑器 overlay, 并在执行前解密 inventory 内源码.
- 构造经过路径校验, 大小限制和内容指纹化的只读 VFS archive.
- 绑定 compiler 插件并持有独立 binding lease 至编译终态.
- 校验输出 archive, 注册 Source Map, 创建 Rhino 或 Node 引擎.
- 管理 execution-scoped generated/original URI 映射.
- 首个 GA 在编译开始前封闭 snapshot, 不为运行中的外部 TS 回读路径.

### TypeScript compiler plugin

- 校验 IPC contract 和 archive schema.
- 在插件私有进程内加载固定版本 TypeScript compiler.
- 解析受控 `tsconfig` 和 AutoJs6 强制 compiler profile.
- 执行项目级类型检查和 emit.
- 输出 JS, Source Map, diagnostics 和 compilation manifest.
- 不执行生成的 JavaScript.
- 不发现或调用 Node Runtime.
- 不直接打开宿主项目路径.

R1 的 [Restricted Compiler Host](COMPILER_HOST_R1.md) 只运行固定单文件 golden.
host page 不加载 TypeScript, 专用 Worker 不访问 DOM 或原生 bridge, emitted JS
不在生产 compiler host 内执行. R1 的内存 VFS 消息不接收项目输入, 也不是 R2
archive/manifest/diagnostics contract.

### Rhino adapter

- 消费 Rhino profile 的 CommonJS 产物.
- 通过 `ScriptCompileSpec + SourceMapV3` 编译生成 JS.
- 将 Rhino 运行栈映射回 TypeScript.
- 首个 GA 保证 runtime stack 的原文件和行; diagnostics 另保证文件, 行和列.

### Node adapter

- 消费 NodeNext profile 的 `.js/.mjs/.cjs` 产物.
- 只在最终执行前检查 Node Runtime 可用性.
- 将 Node wrapper map 与 compiler map 组合.
- 保证 runtime stack 的原文件, 行和列.

### Ace editor plugin

- 提供补全, hover, signature help 和编辑器诊断.
- 可以复用相同 TypeScript 发行包和 host-core 规则.
- 不向 compiler 插件提供活体 WebView, session, AST 或 Language Service.

## 禁止依赖

```text
typescript plugin -X-> nodejs-api
typescript plugin -X-> Node Runtime package discovery
typescript plugin -X-> Ace WebView or LSP state
typescript plugin -X-> arbitrary host filesystem paths
typescript plugin -X-> network or npm install
```

## 引擎路由契约

后续宿主路由优先级固定为:

```text
explicit script directive
> project.json type
> TypeScript default engine preference
> Rhino
```

`.ts` 表示源语言. 首个 GA 的 Rhino profile 支持 `.ts`, Node profile 支持
`.ts/.mts/.cts`. 该矩阵不会让 compiler 插件依赖 Node; 用户最终选择 Node 时,
Node Runtime 可用性只由 Node adapter 检查.

## 首个 GA 的动态边界

- 所有可执行 TS 必须位于执行前 snapshot.
- 加密 TS 可以在 snapshot 构造阶段解密到内存或加密 staging.
- dynamic import 只能命中 snapshot 内已经完成 type-check 和 emit 的模块.
- 运行中发现的 late external TS 返回稳定 unsupported 错误.
- APK bundled compiler 与任意路径动态编译属于 Post-GA, 不暗中降级为 stripping.
