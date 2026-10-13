# TypeScript 解析服务 V1

状态: 0.7.0 新增 (2026-10-13). 契约由宿主 `plugin-api/typescript-parser-api` 定义
(包 `org.autojs.plugin.typescript.parser`, 宿主文档 `docs/dev/typescript-parser-plugin-protocol-v1.md`),
本仓库以源码副本 `plugin-api/typescript-parser-api` 引入. 第一个调用方是 3-Block Compose 设计器的 TSX
工程 (该插件路线图 D42).

## 范围

- 只解析单个内存中的源文件: `ts.createSourceFile`, 不建 Program, 不做类型检查, 不 emit, 不执行任何代码,
  不读文件, Worker 不保留请求之间的状态.
- 输出 schema revision 1 的 JSON 语法树 (每个节点的 `kind`, `start`, `end`, 按 TypeScript 公开 AST
  属性名组织的子节点, 标识符与字面量的 `text` 等) 与语法诊断, 写入调用方提供的文件描述符.
- 支持 `.ts`, `.tsx`, `.mts`, `.cts`, `.js`, `.jsx`; 按扩展名选择 ScriptKind 与 JSX 处理.

## 与编译服务的关系

- 独立的 AIDL (`ITypeScriptParserPlugin`) 与 action (`org.autojs.plugin.typescript.PARSER`), PLUGIN
  权限保护. 编译 AIDL (`ITypeScriptCompilerPlugin`) 的 transaction prefix, Bundle 规则, `typescript-api`
  的 v1 freeze 校验都不变; 路线图 "新能力一律走 additive Bundle key" 一条仍只约束编译契约.
- 服务 `TypeScriptParserPluginService` 与编译服务同在 `:typescript_compiler` 进程 (共用进程级 WebView
  数据目录准备与 quarantine 标记: 被隔离的进程两者都不提供绑定), 但不共用编译页面, 编译 Worker 或全局准入
  租约: 解析不占编译槽位, 编译不等待解析; 编译侧的超时恢复与会话重建不影响解析, 反之亦然.
- 解析页面 `typescript-parser-r1` 由 `TypeScriptWebViewParserHost` 在首次请求时创建, 空闲 60 s, 内存压力
  (`TRIM_MEMORY_RUNNING_LOW` 及以上) 或渲染进程退出时销毁. 页面与编译页面同样不加载 TypeScript, 只把请求
  转给专用解析 Worker (`parser-worker.js`, `importScripts("typescript.js")` 为唯一导入).
- 资源经 `ParserAssetOrigin` 提供: HTTPS 资源域, 精确路径白名单 (`index.html`, `parser-host.js`,
  `parser-worker.js`, 锁定版本的 `typescript.js`), 与编译页面相同的 CSP, 不联网.
- 原生与页面之间只用 WebMessagePort 收发 JSON; 请求源码与返回的语法树都按 16 K 字符分帧, 帧边界不落在代理对
  中间. 不使用 `addJavascriptInterface` 或 `evaluateJavascript`.

## 并发, 超时与失败

- 同时最多 2 个解析 (`MAX_CONCURRENT_PARSES`), 第 3 个立即得到 `ERR_AUTOJS6_TYPESCRIPT_PARSER_BUSY`.
- 超时默认 15 s (含页面冷启动), 范围 1 - 60 s. 超时后重启解析 Worker, 正在等待的其它解析以
  `ERR_AUTOJS6_TYPESCRIPT_PARSER_UNAVAILABLE` 结束.
- 超过 250000 个节点, 32 MiB 树或 1000 条诊断, 以及嵌套过深 (Worker 栈溢出) 都以
  `ERR_AUTOJS6_TYPESCRIPT_PARSER_TREE_LIMIT` 失败, Worker 继续可用.
- 解析期间渲染进程中多一份 TypeScript (约数十 MB); 编译与解析共用同一应用渲染进程, 页面在空闲与内存压力时
  回收以限制这一开销.

## 验证

- `:plugin-api:typescript-parser-api:testDebugUnitTest`: 契约哈希, transaction 顺序 (与 AIDL 声明顺序比对),
  限额一致性.
- `:app:testDebugUnitTest` 中的 `TypeScriptParserRequestsTest`: 请求校验与分帧.
- `:app:verifyParserWorkerDesktop` (挂在 `check`): 以锁定的 TypeScript 6.0.3 运行 `parser-worker.js`, 检查
  树结构, 位置, 标记名不泄漏, `nodeCount`, 各项限额与深嵌套失败; 以假 Worker 检查页面分帧.
- `ParserServiceInstrumentedTest`: 经真实 Binder 服务, WebView 页面与 Worker 解析 TSX, 检查树, SHA-256,
  诊断与请求拒绝, 并记录冷 / 热解析耗时 (`PARSER_TIMING`). 2026-10-13 在 AVD_API_30 上 3 项通过, 冷启动
  约 0.8 s, 热解析约 11 ms.
