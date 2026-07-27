# Restricted Compiler Host R1

R1 在插件私有的 `:typescript_compiler` 进程内提供一个受限
WebView/Web Worker host, 用真实 `typescript@6.0.3` 完成运行时探针. 此 host
只验证发行物, JavaScript 隔离和单文件 Compiler API golden, 不接入 AutoJs6
项目执行链, 也不构成 R2 的生产编译服务.

`compilerAvailable=false` 仍是对外能力协商的权威值. `backend=webview_worker`,
`serviceStatus=ready` 和发行物 fingerprint 只说明 R1 probe host 已经成功加载,
不能解释为 `startCompilation` 可以接受项目.

## 组件和线程

```text
ITypeScriptCompilerPlugin.prewarm
        |
        | primitive-only Bundle
        v
TypeScriptWebViewCompilerHost
        |
        | exact-origin WebMessagePort
        v
compiler-host.js
        |
        | request ID + bounded in-memory VFS
        v
compiler-worker.js
        |
        v
typescript/6.0.3/lib/typescript.js
```

- compiler WebView 由 compiler service 独占, 不复用 Ace activity, editor WebView
  或 Language Service.
- Kotlin 主线程只创建, 配置, 恢复和销毁 WebView/MessagePort. TypeScript 加载和
  `transpileModule` 均在专用 Web Worker 内执行.
- Binder worker thread 等待有界 probe 结果. 主线程调用 probe 会安全失败, 避免
  等待自身造成死锁.
- host 同一时间只允许一个活动 probe. request ID 和 Worker generation 同时匹配
  后才接受结果, 迟到或串线消息被忽略.
- R1 probe protocol 是 app 内部实现细节. 它不属于 AIDL, API AAR, Binder Bundle,
  input/output archive, diagnostics schema 或 compiler profile.

## HTTPS asset origin

host 只加载 Android 为应用资产保留的 origin:

```text
https://appassets.androidplatform.net/typescript-compiler-r1/
```

allowlist 精确包含:

```text
index.html
compiler-host.js
compiler-worker.js
typescript.js
```

其中 `typescript.js` 是
`typescript/6.0.3/lib/typescript.js` 的受控 runtime alias. WebView 不直接暴露
完整 APK assets 树. 请求必须是 HTTPS `GET`, authority, encoded path, query 和
fragment 必须精确匹配; 未匹配请求返回拒绝响应, 不回落到网络.

每个允许响应都携带 `no-store`, CSP, same-origin resource policy,
`nosniff` 和受限 permissions policy. 页面 CSP 只允许同源 script/worker;
Worker CSP 只允许加载同源 compiler script. host 不使用
`addJavascriptInterface`.

WebView 和 Service Worker 设置同时禁止:

- file 和 content access.
- network load 和 mixed content.
- DOM storage, database 和 Web Storage 持久状态.
- cookie 和 third-party cookie.
- image, download, geolocation, media permission 和 file chooser.
- JavaScript window opening, dialog 和非入口页面导航.
- HTTP auth, client certificate 和 SSL error 继续执行.

应用 Manifest 不声明 `INTERNET` 权限. 即使 allowlist 或页面脚本发生错误,
WebView 也不能把未处理 URL 当作远程资源获取.

## Worker 隔离和消息

原生侧通过带精确 target origin 的 `WebMessagePort` 把一个端口交给 host page.
页面只负责 Worker 生命周期和消息转发. 每条返回消息都证明页面全局没有 `ts`;
probe 还验证 Worker 环境存在, 且 `window`, `document` 和原生 bridge 均不存在.

原生请求只含内部 request ID 和固定的有界内存 VFS fixture. Worker 返回有界的
golden 证据; TypeScript 源码, emitted JS, Source Map 和完整 diagnostics 都不进入
Binder Bundle. Worker 不执行 emitted JavaScript. 它使用 TypeScript JS parser
验证 emit 无语法诊断, 检查 enum runtime assignment, 并计算与桌面 reference
一致的 SHA-256.

此固定 probe VFS 不接受 AutoJs6 项目输入. R2 必须通过 FD archive 建立独立的
项目 VFS 和正式 manifest/diagnostics schema, 不能把 R1 JSON 消息直接提升为公共
协议.

## `prewarm` 语义

`prewarm(options)` 的 options 仍只允许:

- `contractVersion`.
- `contractHash`.
- `timeoutMs`.

接收端先执行 primitive-only, marshalled size, key count, contract 和 timeout
校验. 校验失败时返回现有稳定错误码. 合法调用同步启动或复用受限 host, 等待真实
Worker golden:

- 成功时 result 使用 `compilerInfo` schema, 不携带 `errorCode`, 并可报告
  `backend=webview_worker`, `serviceStatus=ready`, distribution fingerprint 和
  declarations fingerprint.
- Worker, runtime version, protocol 或 timeout 失败时, result 使用相同
  `compilerInfo` schema 并附加有界 `errorCode/errorMessage`.
- prewarm 不读取 input/output FD, 不创建 job token, 不发送 compilation callback,
  也不保证下一次项目编译成功.

无论 prewarm 是否成功, R1 均保持:

| 能力 | R1 值 |
|---|---|
| `compilerAvailable` | `false` |
| `projectCompilation` | `false` |
| `semanticDiagnostics` | `false` |
| `sourceMapV3` | `false` |
| `startCompilation` | `accepted=false` |
| `cancelCompilation` | `false` |

因此调用方必须先以 `compilerAvailable` 判定可否提交项目, 不能只看 backend 或
service status.

## TypeScript 6.0.3 runtime golden

probe 固定使用:

```text
target          = ES2018
module          = CommonJS
sourceMap       = true
inlineSourceMap = false
inlineSources   = false
newLine         = LF
```

enum fixture:

```typescript
enum Direction { Up, Down }
(self as any).__r1ProbeValue = Direction.Down;
```

syntax fixture:

```typescript
const broken: = 1;
```

真实 `typescript@6.0.3` 的 golden 为:

| 项目 | 值 |
|---|---|
| runtime `ts.version` | `6.0.3` |
| enum source URI | `autojs://source/r1-probe/enum.ts` |
| enum JS SHA-256 | `75c017bb059e4e246ed6db84c9ea19121df3e0473c428d4aa38e4d2fee1b51a9` |
| enum map SHA-256 | `f6b14de7b69b6cf5e52b247f6ab3f8c0625cc7070fb2a81d05b16e06331fb02f` |
| enum JS parse diagnostics | `0` |
| enum runtime assignment | `self.__r1ProbeValue = Direction.Down` |
| syntax source URI | `autojs://source/r1-probe/syntax-error.ts` |
| diagnostic category/code | `1 / 1110` |
| diagnostic start/length | `14 / 1` UTF-16 code units |
| diagnostic line/column | `1 / 15` for the internal one-based probe view |
| diagnostic message | `Type expected.` |
| syntax JS SHA-256 | `6b57a203bc8cd6b27e7b8383c924ba13cb9365de40637f31a8a839385111ff75` |
| syntax map SHA-256 | `1f5041e240885a38ac5ffcb0f88cae4c149d875805361a30f3a21078c8c38fea` |
| Source Map | `version=3`, non-empty `mappings`, no `sourcesContent` |

enum JS 的相同 golden bytes 已在桌面 reference 中执行并得到
`Direction.Down == 1`; Android Worker 验证完全相同的 emit hash, runtime
assignment 和零 JS parse diagnostic. 这只证明 R1 单文件探针, 不证明项目级
semantic diagnostics 或 R2 正式 emit.

内部 probe 使用 one-based line/column 便于断言, 但该表示不是公共 diagnostics
schema. R2 冻结 schema 时仍需明确 UTF-16 offset, line/column base 和 message
chain 规则.

## 生命周期和恢复

实现包含以下保护:

- probe timeout 后终止并重建 Worker; 同一恢复周期最多尝试一次 Worker restart.
- API 26+ 的 renderer death callback 销毁失效 WebView, 当前 probe 最多重试一次.
- service destruction 完成 pending probe, 关闭 MessagePort, 停止 Worker 并销毁
  WebView.
- API 28+ 为 `:typescript_compiler` 使用独立 WebView data directory suffix.

这些路径只有在相应 instrumentation 和资源观测通过后才能作为 R1 完成项.
当前双设备 instrumentation 已覆盖 timeout 后的 Worker rebuild; 仍不等价于
renderer death 或 destruction 验收.

## 当前证据和限制

当前 debug instrumentation 已在 Android 12/API 31 与 Android 13/API 33
两台真实设备上通过:

- 绑定真实 `:typescript_compiler` 远程 Binder.
- 每台设备运行 100 次顺序 prewarm, 每次完成真实 Worker `ts.version` 和
  golden 校验.
- 以 1 ms deadline 强制 timeout, 验证 Worker rebuild 后下一次 prewarm 恢复.
- 返回稳定 distribution/declarations fingerprint.
- prewarm 前后持续报告 `compilerAvailable=false` 及所有项目编译能力为 false.
- 在 Android 13/API 33 安装临时测试签名的 minified release APK, 通过独立
  framework-only `release-smoke` helper 完成 discovery, remote bind 和相同
  Worker golden, 包括无 inline source content.

尚未形成完成证据:

- API 24/25 的真实 WebView/Worker 运行.
- renderer death retry 和 service destruction.
- 10 次冷启动及 PSS/FD/pending-request 泄漏门禁.
- compiler process 重启后的 fingerprint 稳定性.
- Node/Ace 缺失矩阵和 Rhino compiler context probe.

`onRenderProcessGone` 从 API 26 才可用. API 24/25 只能依赖有界 timeout 和完整
session teardown 作为兜底, 当前尚无真实设备证据, 因而不能声称这两个 API level
已满足 renderer recovery 或 R1 退出条件.
