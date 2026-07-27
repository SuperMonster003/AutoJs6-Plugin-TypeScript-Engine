# AutoJs6 TypeScript Engine Integration Roadmap

此文档是最终路线的索引. 每个阶段的详细任务, 验收门槛和退出条件位于独立的
可勾选 Markdown 文件中.

## 不可变技术决策

- [x] 使用官方 TypeScript Compiler API, 精确锁定 `typescript@6.0.3`.
- [x] TypeScript 只是一等编译语言, 不是 Node.js 的文件扩展名别名.
- [x] Compiler 插件与 Node.js Runtime 插件保持零依赖.
- [x] Rhino 和 Node 使用同一编译协议, 但使用各自显式 compiler profile.
- [x] 首个 GA 中 Rhino profile 支持 `.ts`; Node profile 支持 `.ts/.mts/.cts`.
- [x] Source Map 是强制产物, 不允许用生成 JS 冒充原始 TS 坐标.
- [x] 首个 GA 采用一源文件一 JS 文件, 不 bundle.
- [x] 执行前类型检查和 emit 使用相同 compiler 版本与配置解析规则.
- [x] 大源码, JS, map 和 diagnostics 使用 FD archive, 不放进 Binder Bundle.
- [x] 开发态编译器运行于插件独立进程, 不依赖 Ace 页面或 Node 插件.
- [x] APK 默认 build-time 编译, 最终产物无需携带 TypeScript compiler.
- [x] 禁止项目自定义 transformer, compiler plugin, lifecycle script 和网络访问.
- [x] `.tsx` 不在首个 GA 范围, 除非后续单独冻结 JSX runtime 契约.
- [x] 首个 GA 只编译执行前 snapshot 内的 TS; 运行中出现的外部 TS 明确拒绝.
- [x] Node 运行栈承诺原 TS 文件/行/列; Rhino 运行栈承诺原 TS 文件/行.

## 阶段状态

| 阶段 | 主题 | 前置 | 单人工程周 | 状态 | 详细清单 |
|---|---|---|---:|---|---|
| R0 | 插件基线与协议 | - | 0.5 | 已完成 | [R0](docs/roadmap/R0.md) |
| R1 | 官方编译器发行包与真实宿主探针 | R0 | 1-2 | 已完成 | [R1](docs/roadmap/R1.md) |
| R2 | 生产级项目编译服务 | R1 | 2-3 | 未开始 | [R2](docs/roadmap/R2.md) |
| R3 | AutoJs6 脚本准备管线与路由 | R2 | 1-2 | 未开始 | [R3](docs/roadmap/R3.md) |
| R4 | Rhino 执行适配 | R3 | 2 | 未开始 | [R4](docs/roadmap/R4.md) |
| R5 | Node 执行适配与旧路径退役 | R3 | 1-2 | 未开始 | [R5](docs/roadmap/R5.md) |
| R6 | Source Map, 缓存, 安全与性能 | R4+R5 | 2-3 | 未开始 | [R6](docs/roadmap/R6.md) |
| R7 | Ace, APK Builder, 加密与后台任务 | R2+R6 | 2-3 | 未开始 | [R7](docs/roadmap/R7.md) |
| R8 | Beta, GA 与迁移清理 | R0-R7 | 1-2 | 未开始 | [R8](docs/roadmap/R8.md) |

R1 当前已在 Android 12/API 31 与 Android 13/API 33 的 debug APK 中完成远程
prewarm, 使用独立 WebView/Web Worker 验证 `ts.version=6.0.3`, enum emit,
structured syntax diagnostic 和 Source Map golden. 每台设备 100 次热探针,
timeout 后的 Worker 重建, renderer 首次退出后的单次 retry/第二次退出后的稳定
失败与后续恢复, service destruction teardown/rebind, 以及 10 个冷 compiler
process 的 fingerprint, identity 和 FD 边界均已通过. Binder 门禁也会在 Bundle
反序列化前拒绝并释放 raw transport FD, 且只接受运行时类型严格为 `String[]`
的字符串数组. API 33/WebView 109 另通过 none, Node-only, Ace-only 和 Node+Ace
四种安装态的相同 runtime golden/fingerprint; 显式 opt-in 的
`:app:verifyR1OptionalPluginMatrix` 可在指定 disposable emulator 上重复并保存
该证据. API 31/33 的 compiler service + renderer PSS/request correlation、
双冷 Rhino unsupported/watchdog/rebind、debug-only accepted/cancel/timeout/death
lifecycle 和冻结 AAR 双向兼容门禁也均已通过. ZIP v1 printable-ASCII container
与跨 Android literal corpus、顶层 envelope v1 现已冻结；项目 manifest、
diagnostics 和 compiler profile revision 仍留给 R2.

官方 Play Store x86 AVD 上的 API 24/Chrome 51 与 API 25/Chrome 55 保留为
stock-old 不兼容负基线；两者只完成 remote Binder transport，并在
`compiler-host.js:73` 报 `Unexpected token )` 后由 bounded Worker timeout
收口. 同一 AVD 更新并明确选中同签名 Chrome `119.0.6045.194` 后，API 24/25
均通过真实 TypeScript 6.0.3 WebView/Worker golden、100 次热探针/FD、timeout
recovery、teardown/rebind 和 10 个冷 compiler process 门禁. 该 matrix 关闭
最后一项退出证据，R1 现已完成.

R1 完成不启用项目 compilation admission；项目 manifest、diagnostics 和
compiler profile revision 仍留给 R2，`compilerAvailable=false` 继续为权威状态.
当前 compiler service FD/PSS guardrail 也不是完整 renderer memory leak 结论.

单人顺序实施约 13-20 个工程周. R2 完成后, AutoJs6 host, Rhino/Node adapters
和 Ace/Builder 的部分工作可并行; 两名熟悉现有仓库的工程师可将日历时间压缩到
约 9-12 周. 任何阶段只有在其退出条件全部勾选后才能推进状态.

## 目标架构

```text
ScriptExecutionTask
        |
        v
Host-side project snapshot / editor overlay / decrypted source
        |
        v
ITypeScriptCompilerPlugin (FD archive transport)
        |
        v
CompilationBundle
  - compilation-manifest.json
  - modules/**
  - maps/**
  - diagnostics.json
        |
        +-------------------+
        |                   |
        v                   v
Rhino adapter          Node adapter
        |                   |
        +---- mapped stack--+
                  |
           Console / Editor
```

## GA 统一验收

- [ ] Node Runtime 插件缺失或禁用时, Rhino TypeScript 可运行.
- [ ] Ace 插件缺失, LSP 关闭或编辑器未打开时, TypeScript 可运行.
- [ ] 支持矩阵内的 `.ts/.mts/.cts` 全部经过官方编译器, 不再经过正则 stripping.
- [ ] enum, namespace, class fields 和启用后的 decorators 可正确 emit.
- [ ] 项目级语义诊断和 `noEmitOnError` 生效.
- [ ] Node CJS/ESM 与 Rhino CommonJS 具有真实多文件项目测试.
- [ ] Node/Rhino 编译错误均回到原 TS 文件, 行和列.
- [ ] Node 运行错误回到原 TS 文件, 行和列.
- [ ] Rhino 运行错误回到原 TS 文件和行; 首个 GA 不宣称运行栈列号.
- [ ] wrapper/bootstrap 后的双层 Source Map 可组合.
- [ ] CRLF, BOM, Unicode, 中文路径和空格路径通过.
- [ ] map 路径逃逸, archive bomb, 畸形 map 和超限输入安全失败.
- [ ] APK 不包含非预期 TS 源码或明文临时缓存.
- [ ] 打包 JS 不依赖 TypeScript compiler 即可运行.
- [ ] Ace 升级为项目级 VFS/模块解析后, 与运行前诊断在相同配置下保持一致.
- [ ] 支持矩阵, 安全边界, 性能报告和迁移说明齐备.
