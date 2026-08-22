# R2-M2-C5 Controlled Package Metadata Transport

> **R2-M3-S1 supersession（2026-08-14）。** 本页保留 M2 的 pre-S1 package-policy provenance；旧
> request-manifest revision `4` 不是当前 formal alias/fallback。当前 metadata 条件形状只属于
> `autojs6.typescript.input-archive/revision=1`，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。C5 raw metadata policy 继续保留。

日期：2026-08-01

状态：**C5 internal contract/transport complete；raw-only 边界现由 input-archive schema v1
的 Rhino controlled-config + metadata 条件形状承载。**
完整 C6 已于 2026-08-02 在同一 schema v1 的 metadata + target-v1 条件形状中关闭；production/public project
compilation 仍未开放。见
[Controlled Node Resolution Program](CONTROLLED_NODE_RESOLUTION_PROGRAM_R2_M2_C6.md)。
当前 production authority 仍精确保持
`profileSchemaRevision=1/profileRevision=1/configSchemaRevision=1/configKind=none`，
`typescriptCompilerAvailable`、`typescriptProjectCompilation`、
`typescriptSemanticDiagnostics`、`typescriptSourceMapV3` 继续全部为 `false`。

## 目的与完成边界

C5 为项目根 `package.json` 与 caller 显式声明的 dependency package metadata 建立一条
有界、可复核、与普通 compiler VFS inventory 分离的正向运输路径。完成定义为：

- input-archive schema v1 的 exact 条件形状精确声明 package policy authority、dependency roots、
  package file inventory 及 inventory fingerprint；
- archive actual entries、manifest descriptors 与 control-plane authority 三方精确绑定；
- heap/file-backed snapshot 独立持有 package metadata inventory 和 cursor，不把它伪装成
  source、declaration、Program root 或 token/directory authority；
- native framer 先运输 controlled raw config，再按 canonical inventory 顺序运输 package
  metadata，最后才运输 ordinary source/declaration files；
- Worker 重新验证 exact frame、byte count、SHA-256、UTF-8、strict JSON、complexity 与
  lifecycle `scripts` deny policy；
- 所有 package bytes 都受独立 policy budget 与既有 aggregate snapshot/transport budget
  共同约束，失败后 bytes/cursor/state 可证明释放；
- C5 只运输 raw metadata，绝不把 `exports`、`imports`、`type`、conditions 或 package
  boundary 解释为 resolver authority；该职责由 C6 独占；
- C5 原子验收当时的 controlled request 在 C7 前不创建 Program、result 或 output ZIP。

C5 不改变 config-none 或不携带 metadata 的 controlled-tsconfig 条件形状。无显式
`packageMetadata` authority 的形状继续拒绝 caller 把 `package.json` 放入 ordinary
source/declaration inventory；只有 schema v1 明确允许的 metadata 条件形状可以越过 C4 的
deny-only path fence。

## 冻结 policy authority

机器 authority 为 `R2PackageMetadataPolicy`，checked-in golden 为
`app/src/test/resources/r2-package-metadata/policy-v1.json`：

```text
schema              = autojs6.typescript.package-metadata-policy
schemaRevision      = 1
policyRevision      = 1
projectPackagePath  = package.json
dependencyRootPrefix = packages/
inventoryDomain     = autojs6.typescript.package-metadata.inventory.v1
safePathGrammar     = ^[A-Za-z0-9@._/+~-]+$
jsonMode            = strict-utf8-duplicate-safe-raw
resolverMode        = raw-not-resolver-authority
policyFingerprint   = fab8fec29ed0f0a1de65594620e233c9a9bef2fa514dac16c3079d5570325e97
```

policy fingerprint 是 exact printable-ASCII canonical input 的 lowercase SHA-256；JVM、
Worker 与 Node verifier 分别重建并比较，不能由 manifest 或 caller 自报替代。

独立 package budget 为：

| Dimension | Inclusive limit |
| --- | ---: |
| dependency roots | 128 |
| package metadata files | 256 |
| single package file | 65,536 bytes |
| aggregate package bytes | 1,048,576 bytes |
| JSON depth | 16 |
| JSON nodes | 4,096 |

JSON node budget 不计算 document root object，本体下的 object/array/scalar value 均计数；
因此 4,096 个 non-root value 精确允许，4,097 个稳定映射为
`PACKAGE_COMPLEXITY_EXCEEDED`。该边界由 JVM 与 Worker 独立 oracle 同时冻结。

这些是 package policy 自己的上限，不替代 production Worker 的 **512 files、8 MiB/file、
8 MiB total** admission，也不替代 file-backed native envelope。raw config、package files、
ordinary files 与 trusted declarations 在 snapshot/admission 层继续组合计数；任何一个独立
或 aggregate dimension 超限都 fail closed。

稳定 package policy code 为：

```text
PACKAGE_PATH_INVALID
PACKAGE_ROOT_NOT_ALLOWED
PACKAGE_INVENTORY_INVALID
PACKAGE_QUOTA_EXCEEDED
PACKAGE_ENCODING_INVALID
PACKAGE_JSON_INVALID
PACKAGE_DUPLICATE_KEY
PACKAGE_COMPLEXITY_EXCEEDED
PACKAGE_LIFECYCLE_FORBIDDEN
```

## Path、inventory 与 JSON policy

项目根 metadata 必须精确为 `package.json`。dependency root 必须是小写 canonical
`packages/name` 或 `packages/@scope/name`；roots 严格排序、唯一且不重叠。每个 root 必须
至少声明自己的 `package.json`，额外 nested package boundary 也必须作为严格排序的 exact
descriptor 显式出现。absolute path、空 segment、`.`/`..`、反斜线、ASCII case-fold
collision、`node_modules`、`.cts/.mts`、空格或 grammar 外字符全部拒绝。

每个 descriptor 精确包含：

```text
path
size
sha256
```

inventory canonical input 按严格排序的 roots/files 形成：

```text
autojs6.typescript.package-metadata.inventory.v1
  |root=<dependency-root>...
  |file=<path>,<size>,<lowercase-sha256>...
```

空 dependency roots、内容为 `{}` 的项目 package example 固定为：

```text
canonical = autojs6.typescript.package-metadata.inventory.v1|file=package.json,2,44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a
fingerprint = 5226a24ffd4ccf70a6ae0aa18b5c0f30630612a2e2edd348bf007c87e62a941d
```

package body 使用 fatal UTF-8 与 strict JSON object parser；不接受 JSONC comment、trailing
comma、document-leading BOM/U+FEFF、duplicate key、trailing token 或 array root；JSON
string 内的 U+FEFF 与合法但超出 IEEE-754 finite range 的 JSON number token（例如
`1e400`）允许，因为 C5 不把 raw value 用作 resolver authority。lone surrogate 则稳定映射为
`PACKAGE_ENCODING_INVALID`。C4 冻结的 exact top-level
`scripts` 继续映射为 `PACKAGE_LIFECYCLE_FORBIDDEN`；escaped `\u0073cripts` 解码后同样
拒绝。`Scripts` 或 nested inert `metadata.scripts` 不被误当作 lifecycle authority。
`$schema` 只是 raw string，不能触发网络读取。

## Input-archive schema v1 条件形状与 exact archive coverage

`rhino-r1-tsconfig-metadata` 条件形状同时要求 controlled raw `tsconfig.json` 与
`packageMetadata` object。输入归档整体固定为
`schema=autojs6.typescript.input-archive/revision=1`；package policy 不再拥有、复制或暗示
manifest revision。`packageMetadata` object 只接受六个 exact keys：

```text
schemaRevision
policyRevision
policyFingerprint
inventoryFingerprint
dependencyRoots
files
```

parse 顺序先验证 local policy revision/fingerprint，再从 roots/files 重建 inventory
canonical identity，随后同时比较 manifest `inventoryFingerprint` 与 control-plane
`R2PackageMetadataAuthority.fingerprint`。因此 manifest 不能用内部自洽但未经 control plane
接受的另一套 package inventory。

actual archive inventory 必须精确等于：

```text
request-manifest.json
tsconfig.json
all declared package metadata files
all declared ordinary project files
```

missing/extra、descriptor size/SHA drift、control-plane fingerprint drift、该条件形状缺少
config/package object，或 reserved path 未被 package inventory 显式允许，均在 snapshot
发布前拒绝。heap adapter 与 file-backed spool adapter 都对同一 bytes/hash/JSON policy
复核；file-backed record 额外复核 immutable spool offset/range 和 ownership transfer。

## Snapshot 分层、ownership 与容量

`R2ProjectInputSnapshot` 增加独立的：

```text
packageMetadataInventory: R2PackageMetadataInventory?
openPackageMetadataFile(index): R2ProjectFileCursor
```

package metadata 明确不进入 ordinary `fileInventory`、`filePaths`、`rootNames`、
`totalUtf8Bytes` 或 `largestFileUtf8Bytes`。heap snapshot 对 caller bytes 做 defensive copy，
按 descriptor 顺序复核 size/SHA/JSON，失败或 close 时清零 owned bytes；已经打开的 cursor
在 snapshot close 后失效。file-backed snapshot 拥有独立
`R2FileBackedPackageMetadataRecord` collection/cursor，constructor 与 seal 都重新验证
inventory authority、path、record coverage 和 immutable range，seal 失败仍保留 spool cleanup
ownership。

分离 metadata identity 不等于绕过容量。admission 的 effective capacity 为：

```text
fileCount = raw-config-record + package-records + ordinary/trusted records
largestFile = max(raw config, every package file, every ordinary/trusted file)
totalBytes = raw config + package metadata + ordinary/trusted bytes
```

## Framed transport 与 Worker isolation

package transport 只能与 controlled raw config 同时存在。`projectInputStart` 的 schema-v1
controlled-config + metadata shape 增加七个 exact authority/accounting fields：

```text
packageMetadataSchemaRevision
packageMetadataPolicyRevision
packageMetadataPolicyFingerprint
packageMetadataCanonical
packageMetadataFingerprint
packageMetadataFileCount
packageMetadataByteLength
```

transport 顺序严格为：

```text
projectInputStart
  -> config start/chunk/end
  -> package file start/chunk/end, canonical inventory order
  -> ordinary file start/chunk/end, ordinary inventory order
  -> projectInputEnd
```

package metadata 复用既有 stop-and-wait file frames，不增加可被旧 parser 误解的新 frame
kind。frame 中的 global `fileIndex` 连续覆盖 package + ordinary files；`fileCount` 也只计算
这两类 file record，raw config 仍使用独立 config frames。`totalBytes` 则精确等于 raw config、
package metadata 与 ordinary file bytes 之和。每个 cursor 只在其 file-start ACK 后打开，
end 前重新计算 digest，所有 completion/error/release path 都关闭 cursor 并清零 chunk buffer。

Worker 在 ordinary VFS 之外保存 `packageMetadata` authority 与
`packageMetadataFiles` identity。package bytes 不进入 `files`、root selection、directory
index、path-character budget、source token budget 或 `SourceFile` materialization。Worker
仍重新执行 exact inventory order/path/size/hash、strict UTF-8/JSON、duplicate/complexity 与
`scripts` rejection；验证后只保留 metadata record identity。

这就是 `raw-not-resolver-authority`：C5 证明 raw package metadata 被完整、安全地运输和
验证，但不读取 `exports`、`imports`、`main`、`types`、`type` 或 conditions 来决定 module
resolution。后续 C6 只在 schema-v1 metadata + target-v1 inventory identity 完整匹配时，才于
snapshot-only host 内构造 resolver、nested boundary 和稳定 failure catalog；C5 raw JSON
presence 单独出现仍不构成 resolution success。

## C5 历史 C7 fence 与当前 public/device/promotion 非激活

C5 原子验收完成时不改变 C3/C4 的 transaction boundary。完整的 schema-v1
controlled-config + metadata + source input 即使全部 ACK，`projectInputEnd` 仍命中：

```text
Controlled tsconfig root selection is not available until R2-M2-C7.
```

该失败发生在 `createIncrementalProgram`、parse/semantic/emit、`projectResult*`、result/
diagnostics/output ZIP 与 caller output commit 之前，并清空 Worker operation/package state，
允许 clean successor。C5 transport-only Node verifier 明确证明该历史 acceptance fence 的
resolver/program/emit/result call count 为零；它不描述当前 aggregate controlled branch。

C7 现已替代该 fence：internal controlled request 会在封闭 snapshot 中解释 selectors，验证
entryPath membership，把所有 selected roots 交给 Program，并把 effective-options authority、
rootNames 与 provisional result/output 绑定。C7 本身不读取 C5 package JSON 来决定 resolution；
其后完整 C6 只在 schema-v1 exact metadata/target-v1 authority 上升级 resolver catalog。C5
raw metadata 本身仍不是 resolver authority。

frozen AIDL/Binder transaction prefix、public API artifact、normal Release admission 与四项
capability metadata 均未改变。C5 没有 externally reachable production behavior change，
因此不新增设备矩阵，不生成 TEST_ONLY device evidence，也不执行 normal Release 或 Public
promotion。设备与 promotion 仍由 M4/M5 对最终 M2/M3 contract 执行，不能用本次 JVM/Node
transport gate 替代。

## 验收

原子 gate：

```powershell
.\gradlew.bat :app:verifyR2M2C5ControlledPackageMetadataTransport
```

该 gate 组合 C4、package policy golden/JVM、schema-v1 metadata 条件形状与 heap/file-backed snapshot
tests、framer/Worker Node transport verifier、existing project/archive/config regressions、
compiler-host source policy、frozen/additive API 与四项 capability=false source fence。
独立 Node verifier 覆盖 accepted project/dependency paths、9 类 package JSON rejection、
8 类 transport authority rejection、clean successor，以及
transport-only 历史 fence 的 `resolver/program/emit/result=0`。

后续 C6-B-D 已补齐 C5-bound target policy、schema-v1 target-v1 native framing、TypeScript parity、
Worker pure resolver 与独立 target retention；完整 `R2-M2-C6` 又把 retained targets 接入
受控 compiler-host resolver hook，并冻结 typed failure/result/output wiring。即便如此，C5
metadata presence 仍不会被单独提升为 resolution success，P1/P2 的 NodeNext profile 与
module-kind/output 规则也仍是后续任务。
