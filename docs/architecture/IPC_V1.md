# Compiler IPC v1

> **T1-1–T1-3 current-state amendment（2026-08-26）。** frozen AIDL transaction、
> formal archive schema 与 diagnostics schema 不变；ordinary input quota 当前为 revision 3，
> caller 上限是 `4096 files / 8 MiB per file / 64 MiB total`，旧宿主内联的 input quota
> revision 2 仍按 `512 files / 8 MiB total` 兼容。controlled tsconfig 当前为 policy
> revision 4，Rhino 与 NodeNext revision 2 均接受 20 个 boolean options、profile-scoped
> `target`/`lib` 及 snapshot-only `baseUrl`/`paths`；policy revision 2/3 仍按原身份兼容。
> 精确指纹、选项集合与
> 传输形状见
> [T1-3 Controlled `baseUrl`/`paths` Revision 4](CONTROLLED_PATH_ALIASES_T1_3_REVISION4.md)。

> **S1-1–S1-5 current-state amendment（2026-08-23）。** frozen AIDL transaction 与 ZIP
> transport version 不变；四项 compiler capability 当前均已开放。formal input/output archive
> schema revision 仍为 1，但 ordinary input/output quota revision 均为 2：caller input 是
> `512 files / 8 MiB per file / 8 MiB total` 且不计 plugin declarations；output 是 512 artifacts、
> 单 JS/map 各 16 MiB、64 MiB aggregate；decoded typed-result envelope 为 144 MiB。本文中更早的
> “capability=false”、shared declaration headroom 与 `512 KiB / 1 MiB / 6 MiB` 描述仅是历史记录。
> Public Rhino 当前也接受 controlled `tsconfig.json` schema/policy revision 2；18 个 boolean
> options 生效，未知非结构性字段 warning-ignore，结构性 profile/output/resolution/transformer
> 输入仍 fail closed。接入字段与精确语义见
> [S1-4 Public Controlled tsconfig Activation](CONTROLLED_TSCONFIG_ACTIVATION_S1_4.md)。
> Public NodeNext revision 2 也已通过 additive Bundle authority 准入，支持
> `.ts/.mts/.cts → .js/.mjs/.cjs + map`；Node revision 1 只保留为 private/debug
> compatibility default。Node 请求使用独立的 `node*` compiler-info identity，配置仍为
> `configKind=none`。详见
> [S1-5 NodeNext Public Admission](NODE_NEXT_PUBLIC_ADMISSION_S1_5.md)。

> **R2-M3-S1 input supersession（2026-08-14）。** 本页继续定义 frozen AIDL/Bundle/ZIP
> transport；`Input archive v1` 一节中的 manifest revisions `2..7`、caller-carried declarations
> 与旧 start shapes 只保留为 pre-S1 provenance，不是当前 formal alias/fallback。当前 formal input
> 只接受 `autojs6.typescript.input-archive/revision=1`，caller archive 不携带 declarations，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。

R0 提供可构建的 provisional v1 基线. R1 在不扩展公开 AIDL 或项目 schema
的前提下，通过既有 `prewarm` transaction 验证受限 WebView/Worker host，并在
Binder/FD、真实 compiler probe、冻结 AAR 与双向兼容门禁通过后冻结本文件的顶层
Binder envelope 和 ZIP container. input/output manifest、diagnostics schema 和
fingerprint revision 机制在 R2 冻结，具体 Rhino/Node profile revision 分别在
R4/R5 的真实执行验证后冻结. 这样可以稳定跨进程调用方式，同时避免过早锁死
项目语义.

R1 host 的 asset origin, Worker 隔离, runtime golden 和当前设备证据见
[Restricted Compiler Host R1](COMPILER_HOST_R1.md).
R1 已冻结的 raw ZIP 结构、path 算法和基础配额见
[ZIP Archive Container v1](ARCHIVE_CONTAINER_V1.md); 该结构层不包含项目
manifest 或 diagnostics schema.
R2-P3a 的 production native file-backed input 实现与非 Public 容量边界见
[R2-P3a File-backed Native Input](FILE_BACKED_INPUT_R2_P3A.md)；后续 DEBUG
device capacity/realm-retirement 见
[R2-P3b-b1 Android Device Capacity and Exact Realm Retirement](WORKER_INPUT_R2_P3B_B1.md)，
phase-entry/renderer-loss fault boundary 见
[R2-P3b-b2a DEBUG Phase-entry Hard-stop and Active Renderer-loss](WORKER_FAULT_R2_P3B_B2A.md).

## 服务

```text
action     = org.autojs.plugin.typescript.COMPILER
category   = typescript
process    = :typescript_compiler
permission = org.autojs.permission.PLUGIN
```

## Binder envelope

`ITypeScriptCompilerPlugin` 的 transaction 顺序是协议的一部分:

1. `getContractVersion()`
2. `getContractHash()`
3. `getCompilerInfo()`
4. `prewarm(options)`
5. `startCompilation(request, inputFd, outputFd, callback)`
6. `cancelCompilation(cancellation)`

冻结后不得删除, 重排或改变已有方法签名. 新方法只能追加. `Bundle` 只允许基础
标量, 基础数组和运行时 component type 严格为 `String` 的 `String[]`; 源码, JS,
map, diagnostics 和 manifest 不得放入 Bundle. 接收方忽略未知的合法 key, 但缺失
当前 revision 的必需 key 时必须拒绝. 顶层 null value, Parcelable,
Serializable, Binder, nested Bundle, Parcelable array 和 Bundle 内 FD 一律拒绝.
真实 `String[]` 可以包含 null element, 但不能用空或全 null 的 Parcelable array
伪装为字符串数组.

接收端在 materialize Bundle 前检查 raw transaction transport. `prewarm` 和
`cancelCompilation` 拒绝 transaction 中的任意 FD; `startCompilation` 只检查
第一个 typed request Bundle, 不得把其后合法的 input/output
`ParcelFileDescriptor` 误判为 Bundle metadata. 命中 raw Bundle FD 时不调用
`readBundle`, 同步返回稳定 reject, 并由 Parcel/Binder ownership 释放传输副本.
materialize 后发现其他非法或超预算 metadata 时, 接收端在读取安全 envelope 后
清空 Bundle, 避免延长其中对象的生命周期.

host 必须先读取 contract version/hash, 再创建请求. `startCompilation` 的 request
至少包含:

- contract version/hash.
- input/output archive transport version.
- request ID, project ID 和 entry source URI.
- target profile 和 development/release mode.
- timeout 和受限 compiler options.
- R2 起增加 profile revision, distribution/declarations fingerprint 和 runtime
  capabilities fingerprint.

R0/R1 以及普通 frozen-v1 production、release 和外部调用的
`startCompilation` 继续返回 `accepted=false` 和
`ERR_AUTOJS6_TYPESCRIPT_R0_NOT_AVAILABLE`。
R2-P1/P2/P3a/P3b-a/P3b-b1/P3b-b2a 仅为携带完整 additive profile/config
revision、fingerprint 与 archive-digest metadata 的
production-shaped request 增加
accepted preview；这不改变 capability=false。debug APK 另有 same-UID、私有
control key 保护的 lifecycle probe；它不属于公开 Bundle schema、冻结 AAR 或
capability。

R2-P3a 改变的是 accepted preview 内部的 native input ownership：production
loader 使用 app-private spool/metadata-only snapshot。P3b-a 改变的是 Worker
内部 direct-framing capacity、complexity budgets 与 per-job lifetime；P3b-b1
把当前 raw frame/internal envelope 调整为 64/128 KiB，并加入 same-UID
DEBUG-only expanded capacity control。P3b-b2a 为相同内部 transport 加入严格
ingress/parse/semantic/emit phase events，以及与 spin phase 成对的 same-UID
DEBUG-only fault control；production 固定为 zero spin/`none`。它们都没有新增
public Bundle key、AIDL transaction 或 capability，也没有扩大 Android
production admission。

## Bundle schema

所有 Bundle 的 marshalled size 不超过 64 KiB, key 不超过 64 个, 数组不超过
256 elements. compiler options UTF-8 不超过 48 KiB. request ID 最长 128 chars,
job token 256 chars, project ID 256 chars, URI/error message 4096 chars. 未知 key
只有在满足上述精确 runtime type 和总预算时才忽略. 数组的静态声明或内容看起来
像 String 不足以通过; runtime component type 必须是 `String`.

### compilerInfo

| Key | Type | Required | 说明 |
|---|---|---:|---|
| `contractVersion` | Int | 是 | 顶层 envelope version |
| `contractHash` | String | 是 | 冻结 envelope descriptor 的 SHA-256 |
| `backend` | String | 是 | `uninitialized`/实际 backend |
| capability keys | primitives | 是 | availability, revisions, profiles, fingerprints |
| `nodeProfileRevision` | Int | 是 | 当前 public Node exact profile revision |
| `nodeConfigSchemaRevision` / `nodeConfigKind` | Int / String | 是 | 当前 Node 配置 authority；S1-5 为 `1/none` |
| `nodeRuntimeCapabilitiesFingerprint` | String | 是 | Node revision-2 runtime authority SHA-256 |
| `nodeDeclarationProfileFingerprint` | String | 是 | Node declaration layer SHA-256 |

R0 不打包 distribution, 因此不得伪造 distribution/declarations fingerprint.
R1 打包经过验证的 distribution 后, `compilerInfo` 可以报告其 canonical
distribution/declarations fingerprint 和实际 `webview_worker` backend. 这些值
不覆盖 `compilerAvailable=false`.

### prewarm options/result

options 只含 `contractVersion`, `contractHash` 和 `timeoutMs`; result 使用
compilerInfo schema 并可带有界 `errorCode/errorMessage`. prewarm 不读取项目,
不接受 FD, 也不代表下一次 compile 一定成功.

R0 的合法调用返回 `ERR_AUTOJS6_TYPESCRIPT_R0_NOT_AVAILABLE`. R1 在相同
transaction 内运行固定的内部 Worker golden:

- 成功时返回 `compilerInfo`, 不附加 `errorCode`; backend/status/fingerprint
  可以反映 probe host 已 ready.
- 失败时返回 `compilerInfo + errorCode/errorMessage`.
- 成功或失败均保持 `compilerAvailable=false`, `projectCompilation=false`,
  `semanticDiagnostics=false` 和 `sourceMapV3=false`.
- 完整 probe VFS, emit, Source Map 和 diagnostic evidence 不进入 Binder Bundle.

R1 的内部 request ID/VFS 消息不是公共 Bundle schema. 调用方不得把 prewarm
成功解释为 compilation admission, 仍必须以 `compilerAvailable` 和
`startCompilation` ticket 为准.

### compilation request

| Key | Type | Required in v1 | 预算/规则 |
|---|---|---:|---|
| `contractVersion` | Int | 是 | 必须匹配 |
| `contractHash` | String | 是 | 必须匹配 |
| `inputArchiveTransportVersion` | Int | 是 | 必须匹配 |
| `outputArchiveTransportVersion` | Int | 是 | 必须匹配 |
| `requestId` | String | 是 | 非空, <=128 |
| `projectId` | String | 是 | <=256 |
| `entrySourceUri` | String | 是 | `autojs://source/`, <=4096 |
| `targetProfile` | String | 是 | Rhino/Node |
| `compilationMode` | String | 是 | development/release |
| `compilerOptionsJson` | String | 否 | R0 同步只接受缺省或精确 `{}`；不建立 inline options authority |
| `timeoutMs` | Long | 是 | frozen envelope 为 1..120000；R2 public preview 为 6000..120000，server clock 从 output ownership commit 开始 |
| `profileSchemaRevision` / `profileRevision` | Int | R2 preview | 必须匹配本地 profile authority |
| `configSchemaRevision` / `configKind` | Int / String | R2 preview | 必须匹配所选 profile；Rhino 支持 controlled `2/tsconfig`，Node rev2 仅支持 `1/none` |
| `runtimeCapabilitySchemaRevision` | Int | R2 preview | 必须匹配本地 runtime authority |
| compiler/declarations/runtime fingerprints | String | R2 preview | lowercase SHA-256，必须与本地可信发行包匹配 |
| `inputArchiveSha256` / `requestManifestSha256` | String | R2 preview | lowercase SHA-256，分别绑定完整容器与 manifest bytes |

### admission ticket

| Key | Type | 规则 |
|---|---|---|
| `contractVersion`/`contractHash` | Int/String | 始终返回 |
| `requestId` | String | 可安全解析时回显 |
| `accepted` | Boolean | 始终返回 |
| `jobToken` | String | 只在 accepted 时返回 |
| `acknowledgementRequired` | Boolean | R2 accepted 时为 `true` |
| `acknowledgementTimeoutMs` | Long | R2 public preview accepted 时固定为 `5000` |
| `errorCode`/`errorMessage` | String | reject 时返回 |

accepted 只表示进入有界队列, 不表示编译成功. accepted callback 不得在
`startCompilation()` transaction 返回前发送.

R2-P1 将 post-return barrier 固定为 v1 transaction 6 上的 additive operation：
accepted client 必须在 ticket 返回后，以相同 request ID/job token 调用
`cancelCompilation`, 并设置 `cancellationOperation=acknowledge`。只有 ACK
成功后才 arm pipeline/callback；因此 callback 不会抢在同步 ticket 前发布。
缺省或显式 `cancellationOperation=cancel` 保持取消语义。ACK 窗口为
5s，public preview 的 compilation timeout 下限为 6s。慢速且不修改输出的 FD
preflight 与普通同步 reject gate 都在 `ftruncate` 前完成；成功 `ftruncate` 是
ownership commit 和两个 server clock 的共同锚点。该 budget 不保证 ticket 返回后
仍有完整 5s。窗口后任务静默清理，public late-unack ACK 返回 `false`；
成功 ACK 的重复调用可幂等命中 terminal tombstone，但不能复活任务。private
debug probe 保留既有 late-unack 幂等返回值。

这组 optional key 位于 additive `TypeScriptR2Contract`，不改变 frozen AIDL、contract
version/hash 或 `TypeScriptCompilerContract`。四项 compiler capability 当前为 `true`；NodeNext
revision 2 也已完成 public 端到端编译准入。Worker retained-input guardrail 是 4607 files/72 MiB，ordinary production
另对 caller domain 保留 512 files/8 MiB total admission，plugin declarations 不占 caller
headroom。Worker 最终仍可能同时持有 String/AST，因此该 guardrail 不是 Public 72 MiB 编译承诺。
当前 formal config-none request manifest、framed Worker result 与 output 必须绑定同一个本地
profile/config authority。

### progress/result/cancellation

- progress: `requestId`, `stage`, `elapsedMs`; 不携带 diagnostics 或源码.
- result: `requestId`, `outcome`, `succeeded`, `errorCode/errorMessage`,
  `diagnosticCount`, `emittedModuleCount`, `elapsedMs`,
  `outputManifestSha256`.
- cancellation: `contractVersion`, `contractHash`, `requestId`, `jobToken`，以及可选
  `cancellationOperation=cancel|acknowledge`.

result/cancellation 的 request ID 和 token 仍受上述长度约束. 完整 diagnostics
只存在于 output archive, Bundle 中只返回数量和有界摘要.

`contractHash` 是 UTF-8 `HASH_INPUT` 的 lowercase SHA-256. `HASH_INPUT` 仅包含
冻结的 transaction signature, callback signature, primitive-only Bundle 和
FD envelope 规则; 增加可选 Bundle key 不自动改变 hash. 破坏 envelope 的变更
必须升级 contract version/hash, 并保留兼容 service 或明确拒绝.

## 冻结 artifact 与兼容门禁

R1 将以下只读 artifact 纳入版本库:

- `compat/v1/typescript-api-v1.aar`, SHA-256
  `470ba0e823389fc74ea5aba3b82312576d29d954e8817633697a4ab197fd4f79`.
- `compat/v1/binder-transactions.json`.
- `compat/v1/bundle-fixtures.json`.

`verifyR1ProtocolCompatibility` 比较当前 AIDL、编译后 contract 和 canonical
fixtures，验证冻结 AAR/checksum，并构建/lint 只依赖该 AAR 的 `r1-compat-v1`。
R2-P1/P2 后当前 AAR 可以新增独立 `TypeScriptR2Contract`；freeze gate 仍要求 frozen
`classes.jar` 的每个 v1 class 条目逐 byte 不变，且只允许这一组 R2 additive class，
不会重冻或覆盖 `compat/v1`。
`compat/v1` 基线不可从当前 post-v1 source/AAR 重建：
`:plugin-api:typescript-api:freezeTypeScriptApiV1` 永久 fail-closed，避免把
additive R2 class 污染进 v1 artifact。基线缺失或损坏时只能从版本控制或可信的
legacy v1 release 同时恢复 AAR 与 checksum。
framework-only 双向 instrumentation 已在 API 31/33 真机验证 frozen client →
current service 与 current target AIDL → frozen service 的 transaction 1--6、
未知合法 `String[]`、R1 reject、caller FD 引用/bytes 保持、frozen service
内部引用计数归零和零 callback. frozen service fixture 的计数不是 OS FD 观测；
真实 current service 的 service-side `/proc` ownership/close 证据由下述 accepted
lifecycle 门禁单独提供.
compat APK 与 app 的 debug/release signing identity 由构建门禁对实际 APK 执行
`apksigner --print-certs` 后强制一致；当前 debug certificate SHA-256 为
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`.
聚合任务不操作设备；README 保存了先安装 current app、再安装 compat APK 并串行
运行两条 framework-only instrumentation 的精确命令.

## Job identity, ownership 和取消

- `requestId` 只用于日志和调用方关联, 不作为授权凭证.
- R2 接受任务时由 service 使用安全随机数生成不可预测的 `jobToken`.
- 活动任务绑定 `Binder.getCallingUid() + jobToken + callback binder`.
- 同一 caller 的重复 request ID 必须拒绝; 不同 caller 之间不得互相查询或取消.
- `cancelCompilation` Bundle 必须携带 request ID 和 job token; owner 不匹配时返回
  `false`, 不泄露任务是否存在.
- service 对 callback 执行 `linkToDeath`; client death 等价于取消并立即清理.
- cancel 幂等。admission reject 不发送 callback；未 ACK 的 accepted preview
  静默回收且不发送 callback。成功 ACK、callback 已 arm 且双方 Binder 存活时，
  callback 恰好发送一次终态。client/service death 时无法保证远端 callback，host
  通过 binding death/timeout 合成本地稳定终态。
- progress 是有界提示, host 不依赖其完整性.

## FD 所有权和终态

跨进程 Binder transaction 为接收端建立独立 FD 引用:

1. caller 拥有并关闭自己的 input/output FD.
2. `startCompilation` transaction 返回后, caller 可以关闭自己的 FD 引用, 但在终态
   callback 前不得读取或复用 output backing file.
3. service 拥有收到的引用, 并在 admission rejection, success, failure, timeout,
   cancellation, callback death 和 service destruction 的所有路径关闭它们.
4. input 必须是可 seek/read 的普通 staging file, output 必须可
   seek/write/truncate; pipe, socket 和目录均拒绝, 两者不得指向相同 backing file.
5. service 在接受任务后先将 output 截断为 0, 在私有受限临时文件中构建完整 ZIP,
   校验后再复制到 output, `fsync`, seek 到 0 并关闭 service 侧 output FD.
6. `EMITTED` 输出包含 executable modules/maps/manifest; `DIAGNOSTICS` 输出包含
   diagnostics 和 non-executable manifest, modules 必须为 0.
7. 只有完整 output 提交并关闭 service FD 后才发送 `EMITTED` 或 `DIAGNOSTICS`
   callback. cancel/timeout/internal/transport failure 尽最大努力再次截断 output,
   关闭 FD 后再发送对应终态.
8. host 在 `EMITTED` 后验证 staging output 并原子发布 executable artifact; 在
   `DIAGNOSTICS` 后只读取诊断, 绝不创建执行引擎. 其他 outcome 不读取 output.
9. calling UID 在 admission Binder thread 捕获. timeout 从 admission 开始, 包含
   排队时间. service death 后 host 关闭自己的 FD, 删除 staging file 并合成本地
   compiler-unavailable 终态.

result Bundle 的 `outcome` 是权威终态; 兼容字段 `succeeded` 仅在
`outcome=emitted` 时为 `true`.

R0 只执行同步拒绝, 并在返回 ticket 前关闭 service 收到的两份 archive FD.
真实远程 Binder instrumentation 还重复验证 raw Bundle transport FD 在
`prewarm`, `startCompilation` 和 `cancelCompilation` 的拒绝路径被释放, 调用方
引用保持有效, compiler process 不残留对应 staging target, 且始终为 0 callback.
R1 debug lifecycle instrumentation 另在 accepted active 状态从
`/proc/<compiler-pid>/fd` 观察到两份 `.archive` service 引用，并在 completion、
cancellation、timeout、callback death 和 unacknowledged reaper 终态后确认
`.archive` target 归零且进程身份不变；饱和 reject 保持 caller output sentinel
不变. compiler 总 FD 有界与最终精确回到 warm baseline 由 10 次 warmup + 100 次
真实 Worker probe 的独立门禁验证，避免把 process/runtime 首用持久 descriptor
误归因于单个 accepted job. callback death 会释放 accepted job 而保持 compiler Binder 存活；
service death 的本地 exactly-once 终态由 instrumentation 内的代表性 client
arbiter 合成，不宣称已经接入 AutoJs6 正式执行链.

## Input archive v1

底层容器必须通过 [ZIP Archive Container v1](ARCHIVE_CONTAINER_V1.md). 下列目录曾是
pre-S1 provisional target，仅为历史上下文，不属于 R1 容器冻结，也不描述当前 formal
revision-1 caller archive：

```text
request-manifest.json
tsconfig.json
package.json
sources/<project-relative-path>
packages/<allowed-package-root>/**
declarations/common/**
declarations/rhino/** or declarations/node/**
```

上述完整目录不是当前 formal archive shape。以下 revision 2/3 描述只记录 pre-S1
provenance：revision 2 只允许 `request-manifest.json + sources/**`，revision 3 只再增加 root
`tsconfig.json`。C4 当时对两个 revision 保持 deny-only closure：manifest 声明或 actual extra
出现 reserved package/deferred path 均返回 `ERROR_ARCHIVE_PATH`；普通 undeclared extra
entry 返回 `ERROR_ARCHIVE_INTEGRITY`。C5 只有通过新的 manifest revision 才能正向加入
`package.json` 与 allowlisted package metadata。

`request-manifest.json` 至少包含:

- schema/transport/profile/runtime-capability revision.
- compiler contract version, request ID, project ID 和 entry URI.
- development/release mode.
- original source URI 到 archive path 的映射.
- 每个输入文件的 byte size 和 SHA-256.
- 项目文件和原始配置 bytes fingerprint.
- 项目 inventory 和显式允许的 dependency package roots.

host 负责安全读取, 按清单解密和打包文件, 不负责解释 TypeScript 或 NodeNext
模块图. compiler plugin 在完整受限 VFS 内负责 module resolution. `package.json`,
`exports`, `imports`, nested package boundary 和声明文件必须随 snapshot 提供.
首个 GA 不在执行中回读宿主任意路径, 不访问网络, 也不接受 snapshot 之外的 late
external TypeScript.

当前 P3a production loader 先以 fixed-offset copy 把 caller FD 捕获到
app-private read-only staging inode，再用 ZIP sink 以最多 16 KiB callback 校验
raw records、actual size、CRC 和 ratio，并把实际 payload 写入第二个 private VFS
spool。只有 provisional `request-manifest.json` 可以在 512 KiB 上限内短暂
materialize；source 通过 spool range 执行 size/hash/fatal UTF-8 检查。最终
snapshot 只保留 metadata/offset/read-only inode，native framer 通过 cursor
随机读取当前最多 64 KiB（P3a 初始为 16 KiB）。validator/sink failure 调用
sink `abort`；后续
manifest/UTF-8 failure 同样 fail closed 并关闭、丢弃 spool。

上述实现允许 native 层覆盖冻结的 64 MiB container/64 MiB actual uncompressed
结构 envelope，但不是 public 语言服务容量声明。Worker retained-input guardrail 是
4607 files/8 MiB/file/72 MiB total，并带 path/directory/token budgets、单份 decoded
String/lazy SourceFile 与 per-job release；dispatch 前的 production gate 对 caller domain
执行 512 files、8 MiB/file、8 MiB total admission，trusted declarations 单独计入 retained
authority。Worker 最终仍可能同时持有 decoded strings 与 TypeScript AST。

## Output archive v1

底层容器同样必须通过
[ZIP Archive Container v1](ARCHIVE_CONTAINER_V1.md). 下列目录、提交形态和
`compilation-manifest.json` 内容仍由 R2 冻结:

```text
compilation-manifest.json
modules/<generated-relative-path>
maps/<generated-relative-path>.map
diagnostics.json
```

输出分为两种可提交形态:

- `EMITTED`: diagnostics 不含阻塞错误, manifest 可引用 modules/maps.
- `DIAGNOSTICS`: 语法/配置/类型错误, 只含 manifest + diagnostics, emitted module
  count 必须为 0, 不可被缓存或执行为成功 artifact.

cancel, timeout, internal error 和 transport error 不提交 ZIP, output 必须为空.

每个 emitted module 至少记录:

- original/generated URI 和 module kind.
- JS/map path, byte size 和 SHA-256.
- compiler version 和 distribution fingerprint.
- declarations, normalized options, profile 和 runtime-capability fingerprint.

## Compiler options authority

[R2-M2-C1 Controlled tsconfig Policy](CONTROLLED_TSCONFIG_R2_M2_C1.md) 冻结以下
唯一优先级；[R2-M2-C2 Raw tsconfig Identity/Parser](CONTROLLED_TSCONFIG_R2_M2_C2.md)
已完成内部 bytes/hash、revision 3 manifest、独立 framing 与 plugin-owned parse；
[R2-M2-C3 Effective Options Authority](CONTROLLED_TSCONFIG_R2_M2_C3.md) 已完成 41-key
merge、canonicalization 与 fingerprint；
[R2-M2-C4 Compiler Escape Closure](COMPILER_ESCAPE_CLOSURE_R2_M2_C4.md) 已关闭
transformer/plugin、package/deferred path、Binder 与 host/network escape。S1-4 首先把这条
链路接入 production public Rhino；T1-2 随后发布 exact revision-3 `configKind=tsconfig`，
当前 Rhino 与 NodeNext profile 均可使用，revision 2 Rhino 继续作为兼容请求：

```text
immutable AutoJs6 security/profile constraints
> project tsconfig.json allowed options
> TypeScript explicit defaults
```

- request 的 `compilerOptionsJson` 必须缺省或精确为 `{}`；项目与虚拟单文件都只使用
  snapshot 中受控的 raw `tsconfig.json`。虚拟单文件需要配置时，由 host 构造同一
  policy revision 的 config，不建立第二套 inline authority。
- service 可在 R0 decision 前执行 non-owning descriptor inspection；任何其他 inline 值在
  R0 decision 中先于 descriptor-validity rejection，并在 coordinator admission/FD ownership
  transfer 前返回 `ERROR_INVALID_REQUEST`。
- 若未来需要 non-empty inline options，必须使用独立 `configKind`、raw hash 与 schema
  revision，并与 archive `tsconfig.json` 严格互斥；不得静默复用 frozen v1 field。
- plugin 是 options 的唯一解释者. host/input manifest 只声明原始 bytes hash,
  不得声称 effective options fingerprint.
- plugin 过滤用户配置后，按 profile 白名单应用 `target`/`lib`，最后覆盖
  module/moduleResolution/sourceMap/outDir/noEmitOnError 等 immutable profile；禁止项
  直接诊断，不静默执行。
- controlled effective options 精确为 41 keys：历史 18-key inventory、16 个新增 C1
  boolean key 与 7 个 TypeScript 6.0.3 profile pin；历史 config-none 18-key JSON 与
  fingerprint 不变。
- effective options 使用 TypeScript numeric enum、显式值、`types=[]`、UTF-16 code-unit
  稳定 key 排序和 compact printable-ASCII JSON。
- `effectiveOptionsFingerprint` 精确等于 canonical effective-options JSON bytes 的
  lowercase SHA-256。它没有 domain separator，也不拼接 profile/config revision、raw
  config、selectors、runtime capability、compiler distribution 或 declarations fingerprint；
  这些 authority 分字段记录。M3 若需要 composite identity，必须另定义字段与 revision。
- effective `strictNullChecks=false` 与 effective `exactOptionalPropertyTypes=true` 或
  `strictPropertyInitialization=true` 的组合按 `CONFIG_INVALID_VALUE` 形成 typed config
  diagnostics failure，不进入 Program。
- request, manifest 和实际 archive bytes 任一 hash 不一致时以 integrity error 拒绝,
  不尝试猜测或合并.

C2 additive metadata 为 `configSchemaRevision=2/configKind=tsconfig` 以及
`configByteLength(Long)`、`configSha256`、`configPolicyRevision=2`、
`configPolicyFingerprint`。service 必须识别这些 key：malformed/incomplete 返回
`ERROR_INVALID_REQUEST`，unsupported authority 返回 `ERROR_CONTRACT_MISMATCH`，均不接管
caller FD；完整且与本地 policy fingerprint 匹配的 identity 进入 public Rhino coordinator。
formal input archive schema v1 以 conditional config descriptor 携带 root `tsconfig.json`；旧
manifest revisions 2/3 仅保留为 pre-S1 provenance。

controlled `projectInputConfigEnd` 的 ACK 是 parse、precedence、组合校验、canonical/fingerprint
与 immutable state freeze 的原子 commit point；任一步失败都不 ACK，也不继续 source
framing。成功 ACK 后 selectors 由 C7 root selection 消费并进入真实 Program/result/output；
config policy failure 形成 typed `stage=config/outcome=diagnostics/policyCode=CONFIG_*` terminal。
未知非结构性字段改为 warning `910001/910002` 后忽略，不计入 no-emit error count。

C4 还要求九个 active input frame handler 各自只接受 exact key set，拒绝 transformer/plugin
注入字段；revision 2/3 archive、heap/file-backed snapshot 与 Worker VFS 逐层拒绝 reserved
package/deferred path。Worker compiler host 只读 snapshot VFS，不使用 `ts.sys`、网络或 caller
callback；`createIncrementalProgram` 只接收 `host/options/rootNames`，`emit()` 保持无参。

## 容器配额与 R2 语义配额

strict UTF-8/canonical path、ASCII casefold、regular-file、
container/file/metadata/depth/ratio 的 R1 冻结规则和 exact inclusive limits 以
[ZIP Archive Container v1](ARCHIVE_CONTAINER_V1.md) 为准. validator 会读取 raw
local/central records 并按实际 streaming bytes 重算 CRC、size 和 ratio, 不信任
manifest 或 ZIP header.

P3a 已在 production native loader 上使用该 frozen input limit 和 streaming sink：
64 MiB container、4096 entries、64 MiB actual uncompressed total、8 MiB/entry
可以在不建立全 entry byte map 的情况下完成结构校验。plugin-owned declarations
写入同一 VFS spool 后，revision 2 的 private native VFS index capacity 为 4607
source/declaration files，revision 3 因 raw config 占用一个 caller archive entry 而为
4606；storage envelope 均不超过 72 MiB（physical spool 另含唯一 request manifest，
revision 3 还包含 offset-backed raw config）；
这只是 `64 MiB caller input + 8 MiB trusted declarations` 的存储上界。

Worker direct-framing retained capacity 为 4607 files、8 MiB/file、72 MiB，并用 complexity
budgets 和 explicit release 约束 allocation shape；ordinary production caller admission 独立限制为
512 files、8 MiB/file、8 MiB total。四项 compiler capability 已开放，但这不把 retained guardrail
提升为 caller 可选的 72 MiB 编译额度。

当前项目语义配额不属于 ZIP v1 结构层：

- ordinary output：512 artifacts，单 JS/map 各 16 MiB，aggregate 64 MiB；
- diagnostics：最多 1000 条 primary，canonical document 最多 512 KiB；
- 单 manifest 最多 4 MiB，JSON nesting depth 最多 64；
- decoded typed-result internal envelope 最多 144 MiB。
