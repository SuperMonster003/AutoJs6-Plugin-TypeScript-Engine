# T5-1 运行时 TypeScript 按需编译

状态: 2026-08-29 完成并通过真实跨 APK 验收。

## 结果

compiler-backed Node TypeScript 项目现在可以在脚本运行期间创建项目根内的
`.ts`、`.mts` 或 `.cts` 文件, 随后通过 `require()` 或 dynamic `import()` 执行。
新文件不需要在启动 snapshot 中出现, 也不会被宿主写回或复制到原项目目录。

T5-1 没有改变以下边界:

- 直接把 raw TypeScript 派给 Node Runtime 仍返回
  `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`。
- Rhino 运行期动态 TypeScript 仍不在本项范围内。
- `.d.ts`、`.d.mts`、`.d.cts`、`.tsx` 和大小写变体不属于 Node 按需执行源码。
- TypeScript 源字符串即时执行仍属于 T5-2。
- module-source provider v1/v2 不获得隐式新能力, 未命中时继续保持 snapshot not-found。

## 跨进程数据流

```text
Node 私有 workspace 中的新 TS 普通文件
  -> Node Runtime no-follow 精确读取并冻结字节
  -> module-source provider v3: PFD + byte count + SHA-256
  -> AutoJs6 Host: 路径/身份/UTF-8/额度复验
  -> 完整项目 snapshot + 内存 source overlay
  -> TypeScript Engine: 同一 project identity 的 resident session
  -> Host: 精确选择 JS + Source Map, 不写回项目
  -> Node Runtime: 复验生成路径并加入 execution-scoped 模块表
  -> Host: 合并初始与动态 Source Map bindings 后格式化 V8 stack
```

Node Runtime 仍是模块解析与运行时文件读取的权威方。Host 仍是编译请求、项目
snapshot 和 Source Map 的权威方。TypeScript Engine 只接收既有 R2 archive, 不获得
Node workspace 或任意宿主文件系统访问权。

## 协议 v3

Node runtime request contract 保持 version 2, AIDL transaction 也没有变化。
独立版本化的 module-source provider 从 v2 additive 升级到 v3:

| 项 | v3 值 | 语义 |
|---|---|---|
| capability | `hostTypeScriptOnDemandCompilation` | 插件声明理解 v3 按需编译 |
| operation | `compile_missing_typescript` | 只用于已经存在于 Node 私有 workspace 的运行时 TS 文件 |
| input | `moduleSourceProviderInputFd` | 只读 PFD, Binder 只携带 descriptor |
| input identity | `moduleSourceProviderInputBytes` + `moduleSourceProviderInputSha256` | 绑定精确长度与 lowercase SHA-256 |
| success status | `compiled_typescript` | 响应必须携带 JS PFD、精确长度和生成路径 |
| type failure | `ERR_AUTOJS6_TYPESCRIPT_COMPILATION_FAILED` | 消息包含受限数量的格式化 TypeScript diagnostics |

现有 `resolve_existing` 与 `materialize_missing_plaintext` 会由 v3 Host wrapper 委托给
原有 encrypted provider。委托请求降到其公开版本, 响应再规范化为 v3 envelope。
因此加密源码与 plaintext materialization 没有第二套实现。

## 文件与路径安全

Node 侧只读取 resolver 已选中的绝对候选。读取过程满足:

- 路径必须位于该 execution 的私有 workspace 中, 且不能等于 workspace 根。
- 扩展名严格区分大小写, 只接纳非声明形式的 `.ts/.mts/.cts`。
- 每个父目录都必须是非符号链接目录, leaf 必须是非符号链接普通文件。
- `lstat -> O_NOFOLLOW open -> fstat -> /proc/self/fd -> read -> fstat/lstat`
  前后身份、路径和字节数必须一致。
- Java 读取后的源码会在 Binder 请求结束时清零。

Host 将 Node workspace 路径映射回原项目根后再次检查:

- canonical 路径必须严格位于项目根之下。
- 项目根的 device/inode identity 必须与执行开始时一致。
- Host 项目中不能已经存在同名源码, 任一父目录不能是符号链接。
- 对应 `.js/.mjs/.cjs` 生成目标不能与 Host 项目现有路径碰撞。
- overlay 路径必须通过 archive canonical path 与 ASCII case-fold collision 检查。
- Host 只把 overlay 写入编译 transport archive, 不在原项目目录创建源码或生成物。

Node 收到成功响应后还会验证源码在编译期间没有更换 identity, 并要求响应路径精确为
`.ts -> .js`、`.mts -> .mjs` 或 `.cts -> .cjs`。任何不一致均 fail closed。

## 额度与 deadline

| 边界 | 上限 |
|---|---:|
| 单个动态 TS 输入 | 8 MiB |
| 单个 provider JS 输出 | 16 MiB |
| provider 聚合源码 | 64 MiB |
| 普通 v1/v2 provider operation | 5 s |
| v3 compile transport | 30 s |
| TypeScript 公开编译最小剩余预算 | 6 s |

Host 使用 Node 请求 deadline、项目总预算和 TypeScript 公开最大值三者的最小值。
compiler-backed Node 项目没有显式 `nodeProjectTimeoutMs` 时, 默认总预算从历史 5 s
提高到 30 s; 用户显式配置始终保留, 普通 JavaScript 项目也仍使用原默认值。

按需编译关闭 persistent result cache, 避免运行期 overlay 污染跨执行缓存。不过它使用与
启动编译相同的 canonical 项目根和 project identity, 因而可以复用 T3 resident Worker
session 中的 SourceFile、Program builder 和未变化输出。每次成功只持久保留当前 execution
所需的 overlay 副本, 取消时清零并释放。

## 诊断与 Source Map

TypeScript error diagnostics 不会变成半成品 JavaScript。Host 返回稳定的
`ERR_AUTOJS6_TYPESCRIPT_COMPILATION_FAILED`, 并以动态文件的项目相对路径格式化最多
20 条诊断; Node 将原 error code 和消息暴露给脚本。

成功 emit 后, Host 从该源码精确选择一个生成模块并解析配对 Source Map。binding 的
generated name 使用原项目下的规范生成路径, original name 使用动态 TS 的原项目路径。
Node 执行结束后, engine 把动态 bindings 与启动 snapshot bindings 合并, 再执行一次统一的
V8 stack formatter。动态异常因此显示 `.mts` 文件、原始行列, 不显示 `.mjs` 生成帧。

## 兼容性矩阵

| Host/provider | Node Runtime | 运行期新 TS |
|---|---|---|
| v3 | 支持 v3 | 按需编译 |
| v1/v2 | 支持 v3 | 保持 snapshot not-found, 不尝试传源码 |
| v3 | 旧 Runtime | 旧 Runtime 不发送新 operation, 行为不变 |
| 无 compiler-backed Node project | 任意 | 不建立 v3 provider |

## 验证证据

本地回归:

- Host `:app:compileAppDebugKotlin`
- Host `:app:testAppDebugUnitTest`
- Host `:plugin-api:nodejs-api:testDebugUnitTest`
- Node `:nodejs-api:testDebugUnitTest :app:testDebugUnitTest`
- Node `:app:assembleDebug`, 包含 arm64-v8a、armeabi-v7a、x86_64 与 universal

最终真机为 Xiaomi 23046RP50C, Android API 35, arm64-v8a。安装最终 Node Runtime
versionCode 63 后, 三个独立 instrumentation 用例均通过:

| 用例 | 时间 | 关键断言 |
|---|---:|---|
| `compilesRuntimeCreatedTypeScriptAndKeepsSnapshotErrorsStable` | 19.372 s | 新 `.mts` 得到值 99; on-demand count=1; 歧义和逃逸仍为稳定错误 |
| `runtimeCreatedTypeScriptTypeErrorsReturnReadableDiagnostics` | 17.251 s | 错误码稳定, 消息包含 `late-error.mts` 与 TS2322, 没有成功计数 |
| `mapsRuntimeCreatedTypeScriptFailureBackToDynamicSource` | 19.517 s | stack 命中 `late-stack.mts:3`, 不含 `late-stack.mjs` |

这些用例同时核对 Host compile/failure counters 与 native source byte counters, 证明执行的
不是测试内预置模块或 legacy stripping 路径。
