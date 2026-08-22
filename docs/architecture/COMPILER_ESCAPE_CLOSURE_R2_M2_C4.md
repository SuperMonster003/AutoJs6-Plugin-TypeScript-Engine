# R2-M2-C4 Compiler Escape Closure

> **R2-M3-S1 supersession（2026-08-14）。** 本页保留 M2 的 pre-S1 input provenance；正文中的
> request-manifest revisions `2/3` 与当时的 Worker start shape 不是当前 formal alias/fallback。
> 当前只接受 `autojs6.typescript.input-archive/revision=1`，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。C4 deny-only security conclusions 继续保留。

日期：2026-08-01

状态：**C4 internal security closure complete；尚未开放 production tsconfig、package
transport 或 project compilation。** 当前 production authority 仍精确保持
`profileSchemaRevision=1/profileRevision=1/configSchemaRevision=1/configKind=none`，四项
compiler capability 仍全部为 `false`。C5 package metadata transport 与 C7 controlled
root selection 已在 internal branch 完成，受控请求不再停在旧 C7 fence；该 provisional
Program/result/output 路径仍未进入 production/public authority。

## 目的与完成定义

C4 关闭 caller 能建立第二套 compiler execution、resolution 或 host-I/O authority 的通道，
但不实现 C5-C8 的正向功能。完成边界为：

- controlled config 的 10 个 resolution/host escape option 按稳定 policy code 拒绝；
- custom transformer/plugin 不能经 tsconfig、inline Binder metadata 或 Worker frame 注入；
- revision 2/3 archive、heap/file-backed snapshot 与 Worker VFS 三层拒绝 package/deferred
  path；
- lifecycle `scripts` 作为 C5 必须继续消费的 deny-only authority 冻结，但 C4 不运输或解析
  package metadata；
- TypeScript 只能使用 snapshot VFS host，`createIncrementalProgram` 与无参 `emit()` 不能
  接收 caller callback；
- trusted compiler page/Worker、WebView、ServiceWorker、asset origin 与 manifest 共同阻断
  dynamic code、宿主路径和网络 escape；
- 所有拒绝均不产生 controlled Program/result/output，public capability 与 frozen AIDL 不变。

## 独立 policy authority

checked-in authority 为
`app/src/test/resources/r2-controlled-tsconfig/escape-policy-v1.json`：

```text
schema    = autojs6.typescript.compiler-escape-policy
revision  = 1
bytes     = 735 canonical printable-ASCII bytes
sha256    = ac6acb29d2c544584a84a4c5fe5828e5d7958755aade00cebf224191faf0a9fb
C1 policy = 849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d
```

它冻结以下集合和 mode：

| Authority | Revision-1 value |
| --- | --- |
| escape compiler options | `baseUrl, customConditions, lib, moduleSuffixes, paths, plugins, rootDirs, traceResolution, typeRoots, types` |
| transformer aliases | `customTransformer, customTransformers, transformers`；另测 `before/after/afterDeclarations` |
| package execution metadata | `scripts` |
| package path | basename `package.json`、任意 `node_modules` segment |
| deferred source kind | `.cts`, `.mts` |
| network globals | `EventSource, RTCPeerConnection, SharedWorker, WebSocket, WebTransport, XMLHttpRequest, fetch, navigator.sendBeacon` |
| Binder mode | `primitive-additive-no-executable-authority` |
| host-I/O mode | `snapshot-only-no-ts-sys` |

C4 authority 独立于 C1 grammar，因此不修改 C1 fingerprint；JVM 与 Node 分别重建 canonical
input 和 SHA-256，并逐项与 production Kotlin/Worker authority 对照。

## Config 与 Binder closure

closed-world mapping 保持 C1 顺序：

- 上述 10 个 TypeScript 已知 option 返回 `CONFIG_UNSUPPORTED_FEATURE`；
- transformer aliases 与 `before/after/afterDeclarations` 不是 standard tsconfig option，返回
  `CONFIG_UNKNOWN_PROPERTY`；
- config failure 只发布 exact `projectConfigError`，`stage=config`、
  `outcome=diagnostics`、`emittedModuleCount=0`，不 ACK config end，随后清空 operation state；
- `$schema` 只是 inert string；即使值为 HTTPS URL，也不发生 schema fetch。

`compilerOptionsJson` 缺省或精确为 `{}`。任何空白变体、plugin 或 transformer JSON 都由
`R0CompilationGate` 在 R0 decision 中先于 archive descriptor-validity rejection 返回
`ERROR_INVALID_REQUEST`，不能成为第二套 inline options authority。service 在该 decision 前
只执行 non-owning descriptor inspection；拒绝仍发生在 coordinator admission 与 FD ownership
transfer 之前。

frozen v1 Bundle 仍是 additive envelope：未知 primitive key 必须按兼容规则忽略，C4 不把
`plugins/typeRoots/customTransformers/scripts` 伪装成 public reserved key，也不因它们改变
classification。它们不会进入 envelope、archive、framer 或 Worker。Parcelable、Binder、
嵌套 Bundle、FD 或其他非 primitive metadata 继续由 `BinderMetadataValidator` 拒绝。

## Package 与 deferred path 三层 fence

C4 冻结时的 revision 2/3 不接受正式 package snapshot。相同的 ASCII-casefold policy 在三层执行：

1. archive manifest/inventory validation：manifest 主动声明 reserved path，或 actual archive
   仅额外出现 reserved path，均返回 `ERROR_ARCHIVE_PATH`；普通 undeclared extra entry 才由
   exact inventory gate 返回 `ERROR_ARCHIVE_INTEGRITY`；
2. heap snapshot 与 file-backed snapshot constructor：即使绕过 archive adapter，也重新验证
   every record/root path；
3. Worker `validateCanonicalVfsPath` 与 exact input-frame parser：即使 native frame 被篡改，
   reserved source/declaration path 也在 ACK 前返回 `projectError`、清空 bytes/state，并允许
   clean successor。

14 个独立 path attacks 覆盖 source/declaration roots、大小写变体、nested `node_modules`、
`.cts/.mts`。普通 `.json/.js/.ts/.d.ts` path 不被 C4 误拒绝；这只保持历史 VFS shape，
不代表 C4 开放 `allowJs`、NodeNext profile 或新的 Program root。

C5 后续通过显式 package descriptor/allowlist 在 revision 4 开放了 `package.json` 与
`packages/**`，并继续组合本 policy 的 `scripts` rejection。C4 不定义 package positive
schema、stable package failure catalog 或 NodeNext semantics。

## Worker execution 与 exact protocol

所有九类 active compiler control frame shape 都使用 exact keys：

- `projectInputStart` 及三种 config frame；
- `projectInputFileStart/Chunk/End`；
- `projectInputEnd`；
- matching active `projectResultAck`。

5 个代表 injection names 覆盖 `customTransformers/plugins/typeRoots/hostPath/fetch` extra field。
matching active frame 必须 fail closed；已经退休或 request ID 不匹配的 late result ACK 仍是
harmless no-op，不能破坏 successor。

真实 TypeScript 6.0.3 oracle 记录：

```text
ts.createIncrementalProgram({ host, options, rootNames })
builder.emit() // exactly zero arguments
```

Worker 没有 `ts.sys`、CommonJS `require`、dynamic import loader、`eval` 或 `Function` authority。
compiler host 的 `fileExists/readFile/directoryExists/getDirectories/realpath` 只查询封闭 VFS，
emit 只写 `/outputs/**.js` 与 `/outputs/**.js.map`。恶意 caller source 可以包含网络/进程 API
文本，但只作为 TypeScript 输入被诊断，从不由 compiler host 执行。

## WebView、asset 与 network closure

- page 精确创建一次 `new Worker("compiler-worker.js")`；
- Worker 精确执行一次 `importScripts("typescript.js")`，该 alias 只映射到已验证的 pinned
  distribution；
- page/Worker 不引用 policy 中 8 个 network globals；Node VM 使用 throwing poison globals
  和 poisoned `ts.sys` 再证明 clean/malicious compile 均没有调用；
- CSP 保持 `default-src 'none'`、`connect-src 'none'`、self-only script/worker；
- WebView 与 ServiceWorker 都禁 file/content access、network loads、cache/storage，ServiceWorker
  request 全部返回 403；navigation、SSL/auth/client-cert、permission、file chooser、download 与
  new-window path 均 fail closed；
- asset loader 只允许 reserved HTTPS appassets origin 的四个 exact path，HTTP 禁用；manifest
  不申请 `android.permission.INTERNET`，cleartext traffic 保持禁用；
- Android bridge 继续禁止 `addJavascriptInterface` 与 `evaluateJavascript`，只使用
  origin-bound `WebMessagePort`。

## C4 历史 C7/output fence 与当前 public 非激活

C4 原子验收完成时不改变 C3 的 transaction commit point。controlled config 即使成功 ACK，
project end 仍先命中当时的 C7 fence；不调用 `createIncrementalProgram`，不发布
`projectResult*`，不创建 diagnostics/output ZIP，也不提交 caller output FD。这是 C4 的历史
acceptance fence，不再描述当前 internal controlled branch。

C7 现已在 C4 escape closure 内完成 snapshot-only root selection，并在创建 Program 前绑定
entryPath、selected roots 与 effective-options authority；internal result 与 provisional output
也已接通。现有 config-none desktop golden、18-key fingerprint
`76a173383972b1bd8d4ffa3fffe5d51f5adecf7b84e7b16f58554c14f3c08e0b`
保持不变。

frozen compiler AIDL transaction prefix仍为六项，callback 仍为 `onProgress/onCompleted`；
`typescriptCompilerAvailable`、`typescriptProjectCompilation`、
`typescriptSemanticDiagnostics`、`typescriptSourceMapV3` 继续全部为 `false`。因此 C4 不新增
设备矩阵；设备与 normal Release promotion 仍由后续 public/release gate 负责。

## 验收

原子 gate：

```powershell
.\gradlew.bat :app:verifyR2M2C4CompilerEscapeClosure
```

它依赖 C3、真实 TypeScript distribution、C4 独立 Node verifier、app JVM、desktop golden、
compiler-host source policy、optional-plugin independence 与 frozen API v1，并静态锁定
archive/snapshot/Worker、exact frame、Program/emit ordering、WebView/network、controlled
result/output binding 与 public/AIDL fences。Node verifier 的成功 authority 为：

```text
10 escape options
3 transformer aliases + before/after/afterDeclarations
14 path attacks
9 exact framed shapes
5 representative frame-injection names
```

`verifyR2ProjectCompilerCore` 从 C3 提升为依赖该 C4 gate。C5 package metadata transport、
C7 root-selection/Program 闭环及 C6-A-D kernel/parity/target retention 均已落地；完整 C6
随后已把纯 resolver 以受控 hook 接入 compiler host/Program，并冻结 typed failure/result，
同时继续服从本 C4 的 host/network escape closure。见
[Controlled Node Resolution Program](CONTROLLED_NODE_RESOLUTION_PROGRAM_R2_M2_C6.md)。C4 的
production/public 非激活边界继续保持。
