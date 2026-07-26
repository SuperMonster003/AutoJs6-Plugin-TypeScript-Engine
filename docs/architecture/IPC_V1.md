# Compiler IPC v1

R0 提供可构建的 provisional v1 基线. R1 只冻结顶层 Binder envelope 和 ZIP
container; input/output manifest, diagnostics schema 和 fingerprint revision 机制在
R2 冻结, 具体 Rhino/Node profile revision 分别在 R4/R5 的真实执行验证后冻结.
这样可以稳定跨进程调用方式, 同时避免过早锁死项目语义.

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
标量, 基础数组和字符串数组; 源码, JS, map, diagnostics 和 manifest 不得放入
Bundle. 接收方忽略未知的合法 key, 但缺失当前 revision 的必需 key 时必须拒绝.
Parcelable, Serializable, Binder, nested Bundle 和 Bundle 内 FD 一律拒绝.

host 必须先读取 contract version/hash, 再创建请求. `startCompilation` 的 request
至少包含:

- contract version/hash.
- input/output archive transport version.
- request ID, project ID 和 entry source URI.
- target profile 和 development/release mode.
- timeout 和受限 compiler options.
- R2 起增加 profile revision, distribution/declarations fingerprint 和 runtime
  capabilities fingerprint.

R0 始终返回 `accepted=false` 和
`ERR_AUTOJS6_TYPESCRIPT_R0_NOT_AVAILABLE`. R0 不会发送 callback, 不宣称具备
compiler 能力.

## Bundle schema

所有 Bundle 的 marshalled size 不超过 64 KiB, key 不超过 64 个, 数组不超过
256 elements. compiler options UTF-8 不超过 48 KiB. request ID 最长 128 chars,
job token 256 chars, project ID 256 chars, URI/error message 4096 chars. 未知 key
只有在满足 primitive type 和上述总预算时才忽略.

### compilerInfo

| Key | Type | Required | 说明 |
|---|---|---:|---|
| `contractVersion` | Int | 是 | 顶层 envelope version |
| `contractHash` | String | 是 | 冻结 envelope descriptor 的 SHA-256 |
| `backend` | String | 是 | `uninitialized`/实际 backend |
| capability keys | primitives | 是 | availability, revisions, profiles, fingerprints |

R0 不打包 distribution, 因此不得伪造 distribution/declarations fingerprint.

### prewarm options/result

options 只含 `contractVersion`, `contractHash` 和 `timeoutMs`; result 使用
compilerInfo schema 并可带有界 `errorCode/errorMessage`. prewarm 不读取项目,
不接受 FD, 也不代表下一次 compile 一定成功. R0 已校验上述字段和 metadata
预算, 合法调用仍返回 `ERR_AUTOJS6_TYPESCRIPT_R0_NOT_AVAILABLE`.

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
| `compilerOptionsJson` | String | 否 | 缺省 `{}`, R0 视为 opaque UTF-8; R2 仅用于无 tsconfig 的虚拟单文件项目 |
| `timeoutMs` | Long | 是 | 1..120000, 从 admission 开始计时 |
| profile/fingerprint keys | String/Int | R2 | revision 机制冻结后必需 |

### admission ticket

| Key | Type | 规则 |
|---|---|---|
| `contractVersion`/`contractHash` | Int/String | 始终返回 |
| `requestId` | String | 可安全解析时回显 |
| `accepted` | Boolean | 始终返回 |
| `jobToken` | String | 只在 accepted 时返回 |
| `errorCode`/`errorMessage` | String | reject 时返回 |

accepted 只表示进入有界队列, 不表示编译成功. accepted callback 不得在
`startCompilation()` transaction 返回前发送.

### progress/result/cancellation

- progress: `requestId`, `stage`, `elapsedMs`; 不携带 diagnostics 或源码.
- result: `requestId`, `outcome`, `succeeded`, `errorCode/errorMessage`,
  `diagnosticCount`, `emittedModuleCount`, `elapsedMs`,
  `outputManifestSha256`.
- cancellation: `contractVersion`, `contractHash`, `requestId`, `jobToken`.

result/cancellation 的 request ID 和 token 仍受上述长度约束. 完整 diagnostics
只存在于 output archive, Bundle 中只返回数量和有界摘要.

`contractHash` 是 UTF-8 `HASH_INPUT` 的 lowercase SHA-256. `HASH_INPUT` 仅包含
冻结的 transaction signature, callback signature, primitive-only Bundle 和
FD envelope 规则; 增加可选 Bundle key 不自动改变 hash. 破坏 envelope 的变更
必须升级 contract version/hash, 并保留兼容 service 或明确拒绝.

## Job identity, ownership 和取消

- `requestId` 只用于日志和调用方关联, 不作为授权凭证.
- R2 接受任务时由 service 使用安全随机数生成不可预测的 `jobToken`.
- 活动任务绑定 `Binder.getCallingUid() + jobToken + callback binder`.
- 同一 caller 的重复 request ID 必须拒绝; 不同 caller 之间不得互相查询或取消.
- `cancelCompilation` Bundle 必须携带 request ID 和 job token; owner 不匹配时返回
  `false`, 不泄露任务是否存在.
- service 对 callback 执行 `linkToDeath`; client death 等价于取消并立即清理.
- cancel 幂等. admission reject 不发送 callback. accepted 且双方 Binder 存活时,
  callback 恰好发送一次终态. client/service death 时无法保证远端 callback, host
  通过 binding death/timeout 合成本地稳定终态.
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

R0 只执行同步拒绝, 并在返回 ticket 前关闭 service 收到的两份 FD.

## Input archive v1

容器使用单卷 ZIP, entry name 使用 UTF-8. 只允许未加密的 regular file entry 和
`STORED`/`DEFLATED`; 禁止 ZIP64, symlink, hardlink, device, data path alias 和
multi-disk archive.

```text
request-manifest.json
tsconfig.json
package.json
sources/<project-relative-path>
packages/<allowed-package-root>/**
declarations/common/**
declarations/rhino/** or declarations/node/**
```

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

## Output archive v1

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

R2 冻结以下唯一优先级:

```text
immutable AutoJs6 security/profile constraints
> project tsconfig.json allowed options
> TypeScript explicit defaults
```

- 项目 snapshot 存在 `tsconfig.json` 时, request 的 `compilerOptionsJson` 必须缺省
  或为 `{}`.
- 虚拟单文件项目可以用 `compilerOptionsJson` 代替 tsconfig; R2 必须解析为 JSON
  object 并执行同一 allowlist/denylist.
- plugin 是 options 的唯一解释者. host/input manifest 只声明原始 bytes hash,
  不得声称 effective options fingerprint.
- plugin 过滤用户配置后, 最后覆盖 target/module/moduleResolution/sourceMap/outDir/
  noEmitOnError 等 immutable profile; 禁止项直接诊断, 不静默执行.
- effective options 使用 TypeScript enum canonical value, 显式默认值, 稳定 key
  排序和规范化 URI/path 生成 canonical JSON.
- output manifest 的 options fingerprint =
  canonical effective options + profile revision + runtime capability fingerprint +
  compiler distribution/declarations fingerprint.
- request, manifest 和实际 archive bytes 任一 hash 不一致时以 integrity error 拒绝,
  不尝试猜测或合并.

## Canonical path 和配额

- path 必须是 NFC 规范化, `/` 分隔, 非空的项目相对路径.
- 禁止绝对路径, drive/UNC prefix, `.`/`..`, `\`, NUL, symlink 和规范化后重复路径.
- 大小和摘要由 plugin 重新计算, 不信任 manifest 或 ZIP header.
- input: container/compressed 最多 64 MiB, 4096 files, uncompressed 64 MiB,
  单文件 8 MiB.
- output: container/compressed 最多 128 MiB, 8192 files, uncompressed 256 MiB.
- 单 JS 16 MiB, 单 Source Map 32 MiB.
- diagnostics 最多 10000 条, 序列化后最多 8 MiB.
- entry name UTF-8 最多 1024 bytes, path depth 最多 64.
- 单 manifest 最多 4 MiB, JSON nesting depth 最多 64.
- ZIP central-directory/extra/comment 元数据累计最多 8 MiB.
- input extraction, private output ZIP 和 output copy 同时占用的私有 staging
  总预算最多 384 MiB.
- 单 entry 和 archive 总压缩比均不得超过 100:1.
- 超过任一配额立即失败, 停止解压/写入并清理临时文件.

ASCII name 可不带 UTF-8 flag; 任何非 ASCII name 必须带 UTF-8 flag. 非法 UTF-8
直接拒绝. 为保证 Android/desktop/remote builder 一致, NFC 后大小写折叠冲突也
拒绝. archive comment 和 entry comment 不参与语义, 但仍计入元数据预算.

全部配额按实际 streaming 读写字节计数, 不能信任 central-directory 或
`ZipEntry.size`. R2 冻结 schema 前必须直接覆盖 duplicate entry, zip slip,
Unicode/case-fold collision, symlink, forged size, truncated/oversized central
directory, invalid flag, bomb 和输出超限测试. format/integrity/path/quota 分别使用
稳定错误码.
