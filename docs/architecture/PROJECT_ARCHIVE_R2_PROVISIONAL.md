# R2 Provisional Project Archive Adapter

> **Historical provenance only.** `R2-M3-S1` 已冻结唯一 formal input archive
> `schema=autojs6.typescript.input-archive/revision=1` 与八种 exact conditional shapes。本文记录的
> provisional revisions `2..7` 均已被 formal parser fail closed 拒绝，不能作为 revision 1 alias、
> fallback 或当前 admission 说明。当前 authority 见
> [R2-M3-S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。

本文记录 R2-S2 以及 R2-P3a 的内部输入边界，**不是**公开 archive schema，也不
冻结 `request-manifest.json` v1。P3a 已把 production native loader 切换为
app-private file-backed VFS spool 和 metadata-only snapshot，但公开
`startCompilation`、capability 和 ZIP v1 语义均不因此改变。

## 本文原切片范围

本文记录的当时 production-active R2-S2 分支只接受一个刻意受限的 provisional Rhino 项目；
R2-M2-C2/C3/C4 另加入尚未公开激活的 revision-3 controlled-config internal branch：

- profile 固定为 `rhino`，profile revision 固定为 provisional revision `1`；
- `configSchemaRevision=1`、`configKind=none`，外层
  `compilerOptionsJson` 必须缺省或精确为 `{}`；
- revision 2 caller archive 只允许 `request-manifest.json` 和 `sources/**`；revision 3
  额外且必须包含 root-level `tsconfig.json`；
- 所有非 `.d.ts` 的 `sources/**/*.ts` 都必须作为显式 root；
- `tsconfig.json` 已完成 raw identity/framing/parser 与 41-key effective-options state，
  但 C7 前不执行 selectors、不进入 Program；仍不接受 `package.json`、`packages/**`、
  `.mts` 或 `.cts`；
- C4 在 revision 2/3 上保持 deny-only package/deferred path closure：manifest 声明或 actual
  extra 出现 reserved path 返回 `ERROR_ARCHIVE_PATH`，普通 undeclared extra 返回
  `ERROR_ARCHIVE_INTEGRITY`；heap/file-backed snapshot 与 Worker VFS 再分别复核；
- caller 不得写入保留的 `declarations/**` VFS root。

TypeScript standard libraries 由 compiler plugin 从已签名 APK 内的
`assets/typescript/<version>/lib/lib*.d.ts` 装载。运行时重新核对 108 个文件的
size/SHA-256 和 aggregate fingerprint 后才注入 `/declarations/**`。当前
fingerprint
`6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f`
只代表 TypeScript standard libraries；AutoJs6 common/Rhino/Node declarations
尚未建立独立 lock 和分层 fingerprint。

## 数据流和所有权

```text
service-owned caller PFD
  -> fstat regular-file snapshot
  -> fixed-offset pread(0..) + bounded copy + SHA-256 into app-private staging
  -> fstat unchanged check
  -> fsync and close writer
  -> open read-only inode
  -> unlink its name immediately on Android/Linux
  -> ZIP v1 raw structure + actual payload streaming validation on that inode
  -> append validated payload chunks into an app-private uncompressed VFS spool
  -> verify staged byte count/SHA-256 again
  -> materialize only the bounded request manifest
  -> exact inventory/hash validation + streaming strict UTF-8 scan
  -> append locally verified declarations to the same VFS spool
  -> fsync, reopen read-only and unlink the spool name on Android/Linux
  -> R2FileBackedProjectSnapshot(metadata + offsets + read-only inode)
```

PFD adapter 消费并关闭 service 侧 descriptor；纯 JVM `InputStream` overload 不接管
调用方 stream。`pread` 不读取或改变与 caller 共享的 open-file-description offset；
capture 前后还比较 device/inode/mode/size/mtime/ctime 等 regular-file 状态。staging
的 success、quota、I/O、ZIP、manifest 和 adapter failure 均通过同一 `Closeable`
清理。Android/Linux 后续验证只持有已 unlink 的同一 read-only
`RandomAccessFile`；Windows 仅保留 read-only fallback name 至 reader close。
Android instrumentation 覆盖 shared-offset lseek churn、pipe 拒绝、quota 和
`/proc/self/fd` 生命周期。

现有 `ZipArchiveV1Validator.validate(File)` 仍保持 R1 冻结的独立 `ByteArray`
snapshot 语义。P3a production 入口 `validateImmutableTo` 只用于已经私有化的
staging inode；它在同一 random-access source 上重新验证 raw local/central
records、实际 inflate bytes、CRC-32、size、file/total quota 和 compression
ratio，并以最多 16 KiB 的 `begin/write/end` transaction 把实际 payload 交给
spool。只有 entry 的实际 size、CRC 和 ratio 全部通过才调用 `end`；任一 ZIP 或
sink failure 都调用 `abort`，partial spool 不可发布。该路径不再创建全部 entry
bytes map，也不再为 source 建立 UTF-16 `String` snapshot。

`request-manifest.json` 是唯一允许从 spool 读入 bounded `ByteArray` 的 caller
entry，上限仍为 512 KiB，hash/parse 后清零。revision 3 的 config 只保留
metadata/offset cursor，不在 native loader 中物化或解释。每个 source 从 spool 重新核对
manifest size/SHA-256，并用流式 decoder 执行 fatal UTF-8 和 UTF-16 code-unit
计数。可信 declarations 随后写入同一 spool。最终
`R2FileBackedProjectSnapshot` 只保留 canonical metadata、root inventory、
offset/length 和同一只读 inode；production framer 通过 cursor 随机读取最多
64 KiB（P3a 初始为 16 KiB），并在 file end 前重新核对 SHA-256。

P2 使用 `R2CompilationProfileRegistry.PROVISIONAL_RHINO` 作为 production
profile/config-none 单一 authority；manifest、snapshot、framed Worker 和 output 都绑定
同一个对象。C2 raw-config authority 与 C3 41-key effective-options authority 是独立的
schema-2 overlay，不修改该对象，也不改变历史 18-key fingerprint。

## Provisional manifest revision 2

manifest 使用严格 UTF-8 JSON object，拒绝 BOM、duplicate key、trailing token、
非法 surrogate、超深或超节点输入。顶层 key 必须与下列集合精确相等：

```text
schema
revision
contractVersion
contractHash
inputArchiveTransportVersion
requestId
projectId
entrySourceUri
entryPath
targetProfile
compilationMode
profileSchemaRevision
profileRevision
runtimeCapabilitySchemaRevision
configSchemaRevision
runtimeCapabilitiesFingerprint
compilerPackage
compilerVersion
compilerDistributionFingerprint
declarationsFingerprint
configKind
inventoryFingerprint
rootPaths
files
```

固定值：

```text
schema = autojs6.typescript.request.provisional
revision = 2
compilerPackage = typescript
configSchemaRevision = 1
configKind = none
```

`files[]` 精确包含 `path`、decimal `size` 和 lowercase `sha256`。files/rootPaths
必须按 code-unit 严格递增、无 duplicate 或 ASCII case-fold collision。archive
entry 集合必须精确等于：

```text
{ request-manifest.json } union manifest.files[].path
```

每个实际解压文件重新计算 size/SHA-256。inventory fingerprint 对排序后的 files
使用与 compiler distribution 相同的无歧义编码：

```text
path UTF-8 + NUL
+ decimal size ASCII + NUL
+ lowercase SHA-256 ASCII + LF
```

manifest 无法无循环地包含自身或完整 archive 的摘要，因此 provisional control
object 另外携带 `requestManifestSha256` 和 `archiveSha256`。它们与 staging 实际
bytes、manifest bytes 三方不一致时以 archive integrity failure 拒绝。P2 additive
Binder metadata 已加入 config revision/kind，但公开 schema 仍未冻结。

`runtimeCapabilitiesFingerprint` 也不是 caller 自洽字段：loader 必须显式接收由
service/compiler host 计算的本地可信 fingerprint，并同时核对 control object 与
manifest。缺少本地可信值时不存在可调用的 production-shaped overload。

## Internal manifest revision 3

R2-M2-C2 不重新解释 revision 2，而是为受控 raw config 增加 exact revision 3。它沿用
revision 2 keys，并增加：

```text
config = {
  path = tsconfig.json
  size = 0..49152
  sha256 = lowercase SHA-256
  policyRevision = 1
  policyFingerprint = 849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d
}
configSchemaRevision = 2
configKind = tsconfig
```

control-plane raw authority、manifest config descriptor 与 actual ZIP entry 必须逐字段一致；
loader 从实际 entry stream 的 size/hash 验证第三方绑定。entry 集合精确为
`request-manifest.json + tsconfig.json + manifest.files[]`。snapshot 将 config 保存在
source/declaration VFS 之外，heap adapter defensive-copy/close-zeroize，production adapter
只记录 immutable spool offset。framer 和 Worker 分别再次重算 digest；acceptance 后若
plugin-owned state 漂移则以 compiler-unavailable fail closed，不重新归因 caller archive。

C3 不向 manifest 写入 caller 声称的 effective fingerprint。Worker 在 config-end 内完成
policy parse、precedence、typed combination validation、41-key canonicalization 与 pure SHA，
冻结 internal effective state 后才 ACK。raw config hash、policy/profile/runtime/distribution/
declarations identity 仍是独立 authority；C7 前 selectors 只被保存，project end 不创建
Program、compiler result 或 ZIP。

C4 不扩展 revision 3 的 positive shape。它只在 archive manifest/actual inventory、heap 与
file-backed snapshot constructor、Worker VFS 三层拒绝 reserved package/deferred path，并
阻断 transformer/plugin 与 host-I/O escape。C5 若加入 `package.json` 或 `packages/**`，必须
定义新的 manifest revision、显式 descriptor/allowlist 与稳定失败分类，不能重解释 revision 3。

## 配额与 WebMessage 边界

P3a native 路径使用冻结的 64 MiB input container 上限，并能在不保留全部解压
payload 的情况下执行 4096 entries、64 MiB actual uncompressed total 和
8 MiB/entry 的 ZIP v1 结构校验。provisional 项目层另外保持：

- `request-manifest.json` 最多 512 KiB，且它是唯一物化的 caller payload；
- revision 2 caller source inventory 最多 4095 files；revision 3 因 manifest + config
  占据 archive entry，最多 4094 sources；container 仍为 64 MiB total、8 MiB/entry；
- plugin-owned trusted declarations 最多 512 files、8 MiB total、8 MiB/file；
- revision 2 native VFS snapshot 最多 4607 source/declaration files；revision 3 因 raw
  config 占用一个 caller archive entry，最多 4606 source/declaration files。两者的
  source + declarations storage envelope 都不超过 72 MiB；physical spool 另含唯一
  request manifest，并在 revision 3 中另含 offset-backed raw config。

最后一组是 private native storage/index capacity；P3b-a Worker 内部
direct-framing envelope 已与它对齐，但仍不是 public/production admission。
archive staging 和 uncompressed spool 在转换期间可能同时存在；P3a 消除的是
caller source 的 native heap materialization，不是磁盘占用，也不把 ZIP 结构
配额改写为语言服务配额。

R2-S3/P3a 初始在 native ↔ page String `WebMessage` 边界使用 32 KiB JSON
envelope 与 16 KiB raw canonical Base64；P3b-b1 当前为 128/64 KiB。page ↔
Worker 仍使用同 schema structured-clone object，两段共同保持 window=1
stop-and-wait。native framer 只持有一个可复用 chunk buffer，并从 file-backed
snapshot 的 accepted offset range 读取。

Android production admission **仍为** 512 files、8 MiB/file、8 MiB total；revision 3
按 source + trusted declarations + config 的 aggregate 计数/计字节。
P3b-a Worker 内部可接收 4607 files/72 MiB，fatal decode 后只保留一份 text
record、lazy-create `SourceFile` 并在 `finally` 释放 per-job references，但
TypeScript 最终仍可能同时持有全部 strings 与 AST。P3b-b1 已加入 DEBUG
API 24/28/35 direct capacity、API 24/35 Binder boundaries 与 exact realm
retirement，但没有解除 production admission，也不允许据此宣称 Public
compilation 已开放。详见
[R2-P3a File-backed Native Input](FILE_BACKED_INPUT_R2_P3A.md) 和
[R2-P3b-a Worker Input Capacity and Lifetime](WORKER_INPUT_R2_P3B_A.md) 以及
[R2-P3b-b1 Android Device Capacity and Exact Realm Retirement](WORKER_INPUT_R2_P3B_B1.md)。
四项 capability 仍全部为 `false`，这些 slices 不关闭 Public R2 的任何
checklist 项，计数保持 `0/54`。

## 尚未完成

- 完整 P3b-b 仍需 external-UID debug/minified-release、active renderer/PSS、
  分阶段 cancel/timeout、OOM/LMK、重复大任务与 recovery，并据此决定是否解除
  production 512 files/8 MiB total gate；在这些 gate 关闭前不能宣称 Public
  64 MiB；
- 满足 128 MiB container/256 MiB uncompressed 配额的 private output ZIP
  原子提交；
- `configKind=none` 的 fixed-options authority 已由 P2 完成；C1-C4 已完成 `tsconfig`
  closed-world policy、raw parser、41-key effective-option precedence 与 escape closure；
  C5 package metadata、C6 NodeNext resolution 和 C7 selector/root matching 仍未完成；
- AutoJs6 common/Rhino/Node declarations lock、分层 fingerprint 和注入；
- 正式 request/output manifest 与 diagnostics JSON schema。

P2 profile/config 的跨边界不变量见
[R2 Provisional Profile and Config Authority](PROFILE_AUTHORITY_R2_PROVISIONAL.md)。
