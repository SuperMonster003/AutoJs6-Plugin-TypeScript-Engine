# R2 Provisional Project Output (Historical Foundation)

> **S1-2 amendment（2026-08-23）。** 本文中的 printable-ASCII output path 是历史边界；
> 当前 formal output ZIP 保留原始 canonical UTF-8 path，并将最终 `sourceMappingURL`
> 按 UTF-8 bytes 百分号编码。

> **R2-M3-S2 supersession（2026-08-22）。** 本文是 S4 private-output foundation 的历史切片。
> 其中 `autojs6.typescript.compilation.provisional/revision=2`、单一
> `inventoryFingerprint` 与“output schema 仍 provisional”的叙述已经被 formal
> `autojs6.typescript.output-archive/revision=1`、S1 accepted-input provenance、
> `moduleInventoryFingerprint`/`artifactInventoryFingerprint` 取代，见
> [S2 Output Archive Schema v1](OUTPUT_ARCHIVE_SCHEMA_V1_R2_M3_S2.md)。diagnostics document
> 仍为 provisional revision 2，等待 S3；Public promotion 仍未发生。
>
> 此外，“coordinator/public callback 尚未接线”等当时态叙述已由
> [R2-M2-X1 Public-shaped Deterministic Bundle](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)
> 取代；其中把 128/256 MiB 当作后续 end-to-end capacity target 的叙述也已由
> [R2-M3-D2 Ordinary Output Quota Decision](ORDINARY_OUTPUT_QUOTA_DECISION_R2_M3_D2.md)
> 取代。实时状态见
> [R2 Roadmap](../roadmap/R2.md)。

状态：**历史实现记录**；它本身不是当前 formal output archive contract。

本文记录 S2 freeze 之前的 app-private output ZIP staging 与 caller output FD commit 边界。
其中 output manifest 的 provisional identity 已被上方 S2 文档取代；diagnostics shape 仍由 S3
后续冻结。该历史实现不改变 AIDL、ZIP container v1 或 capability advertisement；production
Binder compilation admission 仍未开放。

## 当前范围与容量事实

这一切片建立了“先在插件私有空间形成并验证 artifact，再提交到 caller FD”的
生命周期边界，但尚未实现完整的大文件输出链：

- S3 Worker result 仍在 native/Worker heap 中完整物化；D2 已冻结 final packaged emitted
  output 为 512 artifacts、512 KiB/JS、512 KiB/map、**1 MiB total**，完整 typed result
  的 6 MiB 仍只是 internal guardrail；
- output builder 也会先持有各 entry 的 `ByteArray`，再写入私有 ZIP；
- builder 和 validator 会执行冻结的 ZIP v1 output 上限，包括 128 MiB container、
  256 MiB actual uncompressed total、8192 files 和 8 MiB metadata；
- 当前 writer 为纯 `STORED`，container 大小约等于 uncompressed payload 加
  metadata，不能用压缩把接近 256 MiB 的 payload 放入 128 MiB container；
- 因此前述 128/256 MiB 数字是必须遵守的 legacy 结构上限，**不是 ordinary capacity
  或待实现的 end-to-end 容量目标**。D2 quota 已由现有 packager 最终复核；在该 quota 下
  消除完整 heap materialization 仍需要 file-backed Worker sink 与 streaming private ZIP
  writer；正式 packaged archive quota 由 S2 output schema 冻结，S3 仅提供 diagnostics
  shape 依赖。

换言之，S4 当前完成的是 commit 的正确性边界，不是最终容量目标。冻结的 ZIP v1
结构规则见 [ZIP Archive Container v1](ARCHIVE_CONTAINER_V1.md)，S3 heap 与 frame
限制见 [R2 Provisional Framed Project Transport](PROJECT_TRANSPORT_R2_PROVISIONAL.md)。

## Artifact 形状与 exact snapshot binding

成功 staging 只接受绑定到同一个 accepted input snapshot 的
`R2ProjectCompilationOutcome`。production-shaped 入口以对象 identity 核对
`compilationOutcome.exactSnapshot === acceptedArchive.snapshot`，再从 accepted
archive 复制以下 authority：

- `requestId`、`projectId`、`entrySourceUri` 和 entry source path；
- target profile 和 compilation mode；
- compiler version、compiler distribution fingerprint；
- declarations fingerprint、runtime capabilities fingerprint；
- 完整 input archive SHA-256；
- accepted `request-manifest.json` SHA-256。

builder 还要求 compiler API/version/profile/options 与 authority 精确一致，
diagnostic source 必须属于 accepted snapshot。emitted 结果必须让每个 accepted
root 恰好对应一个 JavaScript module 和一个 Source Map，并精确覆盖 entry root；
Source Map v3 的 `sources` 必须解析回同一 source inventory。这样 output 不是仅凭
caller 提供的 ID 自洽，而是绑定到已经私有化并通过验证的 exact input snapshot。

当前 ZIP inventory 为：

```text
compilation-manifest.json
diagnostics.json
modules/<source-relative path>.js        # emitted only
maps/<source-relative path>.js.map       # emitted only
```

`compilation-manifest.json` 使用
`schema=autojs6.typescript.compilation.provisional`、`revision=2`。它记录上述 input
authority、outcome、entry module、compiler/effective options、diagnostic/module
计数，以及 payload `files[]`。每个 file descriptor 包含 canonical `path`、
`kind`、decimal `size`、lowercase SHA-256 和 `sourcePaths`。
当前可到达 packager 的 P2 config-none effective-options object 显式写出历史 18 个固定
字段；其 canonical JSON 使用稳定 key 顺序、TypeScript numeric enum 值和显式默认值。
`effectiveOptionsFingerprint` 只由该 canonical JSON 计算，compiler distribution、
declarations、runtime capability 与 profile/config revision 作为独立 authority
字段同时记录。raw config hash、policy fingerprint 与 selectors 同样不能拼入该字段；
若 M3 需要 composite identity，必须定义另一个 revisioned 字段。

R2-M2-C3 已为 controlled internal branch 建立独立 41-key canonical options 与同样的 pure
SHA semantics；R2-M2-C4 又关闭 transformer/plugin、reserved package/deferred path、Binder
与 host/network escape，但 C7 root selection 前该 branch 不产生 compiler result，也不能
进入当前 provisional output packager。因此 C3/C4 不把 41-key shape 偷渡进 revision-2
output、不生成 diagnostics-only ZIP，也不改变历史 18-key manifest/fingerprint。任何 C4
拒绝都保持 output 为空。

`inventoryFingerprint` 对排序后的 payload inventory 使用无歧义的
NUL/LF 分隔编码：

```text
path + NUL
+ kind + NUL
+ decimal size + NUL
+ lowercase SHA-256 + NUL
+ (sourcePath + NUL)*
+ LF
```

manifest 不把自身放入 `files[]`，避免 self-hash 循环。staged summary 另行持有
manifest SHA-256、完整 container SHA-256、精确 container byte length 和计数。
这些字段目前都是 private implementation detail，不可作为 v1 schema 依赖。

`diagnostics.json` 使用
`schema=autojs6.typescript.diagnostics.provisional`、`revision=2`，并在 diagnostics
数组前绑定 request ID、target profile、profile/config revisions 与 config kind。
diagnostics
按 `phase.ordinal -> sourcePath.orEmpty() -> start -> length -> line -> column ->
category -> code -> message` 的完整 key 确定性排序；可空 position 在排序时映射为
`-1`。每项记录 phase、category、code、message 及可空的 source/position。
`line`/`column` 为 1-based，`start`/`length` 沿用 TypeScript 的 UTF-16
code-unit offset。此 shape 同样尚未冻结。

当前 config-none output packager 在创建 staging ZIP 前再次 exact 比较 accepted input
authority、Worker result 的四个 profile/config 字段和完整 18-key effective options。任一漂移都按
不可表示的 compiler result 拒绝，不会进入 caller FD commit。完整 P2 说明见
[R2 Provisional Profile and Config Authority](PROFILE_AUTHORITY_R2_PROVISIONAL.md)。

编译器输出 `/outputs/**.js` 和 `/outputs/**.js.map` 分别映射到 `modules/**` 与
`maps/**`。builder 先验证 compiler module 的最后一行是精确的
`//# sourceMappingURL=<file>.js.map`，再把 packaged module 中该行改写为从
module 所在目录先逐层返回 archive root、再指向对应 `maps/**` entry 的 canonical
相对路径。例如：

```text
modules/main.js       -> //# sourceMappingURL=../maps/main.js.map
modules/a/main.js     -> //# sourceMappingURL=../../maps/a/main.js.map
```

是否保留末尾 LF 由原 module 决定。compiler 原始 output SHA-256 在改写前先复核；
manifest 中的 size/SHA-256 则描述改写后的实际 packaged bytes。Source Map bytes
保持不变。archive path 已被限制为 printable ASCII；URL encoder 只让
`A-Z a-z 0-9 - . _ ~ /` 原样通过，其余字符全部按单个 ASCII byte 使用大写
`%HH` percent-encoding。`/` 保留为 path delimiter，`#`、`?`、`%` 和 space
分别写作 `%23`、`%3F`、`%25` 和 `%20`，避免被解释为 URL fragment、query 或
已有 escape。

## Deterministic private ZIP

builder 先完成 result shape、path/case-fold collision、source inventory、
per-kind semantic quota 和 aggregate quota 校验，再按确定性顺序写 ZIP：

1. `compilation-manifest.json`；
2. 其余 payload 按 archive path 的 code-unit 顺序排列。

writer 固定使用 ZIP v1 `STORED` records，CRC-32、compressed/uncompressed size 和
local offset 均预先确定；flags、DOS time/date、extra field、entry comment、
archive comment 和 data descriptor 均为零/不存在。没有随机 metadata，临时文件名
也不进入容器，所以相同 authority 与相同 compiler result 产生完全相同的 ZIP
bytes。写入期间使用的 entry byte arrays 在 success 和 failure 路径都会清零。

私有 inode 的形成顺序为：

```text
bounded deterministic ZIP write
  -> writer fsync
  -> writer close
  -> chmod read-only
  -> open the same inode read-only
  -> unlink name on Android/Linux
  -> ZIP v1 OUTPUT streaming validation on that reader
  -> exact physical inventory/order check
  -> full container SHA-256
  -> retain only the read-only inode handle
```

Android/Linux 在 validation 前就删除 directory entry，后续 validation、digest 和
commit 都只接触同一个 open inode。Windows JVM test 环境无法 unlink open file，
因此保留 read-only fallback name，并由 staged handle 在 close 时删除。

`ZipArchiveV1Validator.validateImmutable(RandomAccessFile, OUTPUT)` 直接在该
random-access source 上检查 raw local/central/EOCD 布局，并 streaming 核对实际
payload、CRC-32、size、compression ratio 和所有 output quota；它不构造
128 MiB container snapshot，也不返回 256 MiB entry byte map。commit 前会在同一
reader 上再次执行结构验证并重算 container SHA-256，copy 完成后再次核对 digest。
这里的“nonmaterializing”专指 validator/commit reader；当前 S3 result 和 ZIP
builder 仍有上文所述 heap materialization。

## Caller FD admission

output FD 必须是可 seek 的 regular file、可写、与 input FD 的 device/inode 不同，
且不能带 `O_APPEND`。检查不会通过试写 caller data 来推断 access mode。

Android API 24–29 尚无 public SDK `Os.fcntlInt(F_GETFL)`；系统内部虽有同名
hidden API，但 target SDK 36 不能依赖。`R2FileDescriptorOpenFlags` 因而在
API 30+ 调用 public `Os.fcntlInt`，API 24–29 通过随 APK 打包的 JNI/libc
`fcntl(F_GETFL)` bridge；native call 对 `EINTR` 重试并把 errno fail closed
传回 Java。该路径不读取 procfs、不调用 hidden API，也不通过试写 caller data
推断 flags。admission 和最终 commit 都检查 access mode 与 `O_APPEND`。

debug/release APK gate 精确要求 arm64-v8a、armeabi-v7a、x86、x86_64 四份
bridge 及 exact JNI export；R8 只 keep native wrapper 的精确类/member。

必须拒绝 `O_APPEND`，因为 Linux 会把 append semantics 应用到 `pwrite`；若允许
它，所谓 fixed offset write 仍可能把 bytes 写到原文件尾部，破坏已经验证的
artifact length。input 同样要求 readable，且 input/output validation 会执行
`fstat`、regular-file、seekability 和 fixed-offset I/O 检查。

## Fixed-offset commit 与 terminal 边界

`R2OutputFdCommitter` 消费 service-owned caller output PFD。成功路径为：

```text
recheck writable + non-O_APPEND
  -> same staged inode: structural validation + length/SHA-256 check
  -> destination ftruncate(0)
  -> 64 KiB buffer: pread(staged, absolute offset)
                    + pwrite(destination, absolute offset)
  -> destination ftruncate(exact validated length)
  -> destination fsync
  -> staged inode SHA-256 unchanged check
  -> destination lseek(0)
  -> close service-owned output PFD
  -> return committed summary
```

`pread`/`pwrite` 不依赖或改变 shared open-file-description offset，因此 caller
并发的 offset churn 不会重定向 copy。最终 `lseek(0)` 明确把共享 offset 留在
零位；exact truncate、`fsync`、seek 和 close 任一失败，都不是成功 commit。

copy 期间 caller backing file 可能暂时可见部分 bytes，所以真正的 publication
规则必须是：caller 在 terminal callback 前不得消费 output，future Binder
coordinator 只能在上述全部步骤成功后发布 terminal `emitted`/`diagnostics`
outcome。当前 private committer 已提供该 linearization point，但尚未接入 public
callback。

## Diagnostics-only、empty failure 与 poison

TypeScript 语法/类型错误不是空 output。仅当 `emitSkipped=true`、存在 blocking
error 且没有 compiler outputs 时，builder 产生一个合法的 diagnostics-only ZIP：

```text
compilation-manifest.json   # outcome=diagnostics, entryModulePath=null,
                            # emittedModuleCount=0
diagnostics.json
```

它没有 `modules/**` 或 `maps/**`，不可执行，但仍是可验证、可提交的 artifact。

cancel、timeout 和 internal/transport failure 则必须留下**空 output file**。
`clear()` 执行 `ftruncate(0) -> fsync -> lseek(0) -> close`。普通 commit failure
也先尝试相同的 empty rollback；若 rollback 和 close 都成功，返回普通
`R2OutputCommitException`，caller 可依赖 output 已同步为空。

若 truncate、fsync、seek 或 close 中任何一步无法证明完成，则抛出
`R2OutputCommitUnprovenException`。此时不得把文件描述为“已回滚为空”，future
Binder admission arbiter 必须把它视为 poison：阻止后续 admission，直到
process-level supervisor 给出隔离/终止证明。这个区别避免在 cleanup 证据缺失时
错误复用 compiler slot 或发布 successor。

## 测试证据

同一组 5 个 `R2OutputFdCommitterInstrumentationTest` 已在 Android API 24、25 和
31 通过，覆盖：

- validated/unlinked private inode 以 fixed offsets 提交，精确 truncate、
  ZIP OUTPUT 复核、共享 offset 归零及 PFD close；
- first-write 后注入 failure，证明 ordinary rollback 得到 synced empty output；
- final seek 前强制关闭 descriptor，证明 cleanup 不可验证时产生
  `R2OutputCommitUnprovenException`，而不是伪称 empty；
- cancel/failure `clear()` 的 truncate/fsync/seek/close；
- append-mode output 在 admission 前被拒绝，caller sentinel 不被改写。

JVM tests 另外覆盖 emitted/diagnostics-only deterministic archive、exact authority
与 inventory fingerprint、Unicode/JSON escaping、无效 compiler outcome 拒绝、
frozen ZIP corpus 与新的 nonmaterializing `RandomAccessFile` validation seam。

## 尚未完成

- production Binder coordinator：与 R1 probe 共用的全局 admission arbiter、
  calling UID/owner/job token、absolute deadline、exact terminate ACK barrier、
  service-owned FD 生命周期和 callback exactly-once；
- file-backed Worker output sink 与 streaming ZIP writer，使 D2 冻结的 512 artifacts /
  512 KiB per JS/map / 1 MiB final packaged payload 不再依赖完整 result 与 packaged
  `ByteArray` 物化；legacy 128/256 MiB ceiling 不作为 ordinary capacity 目标；
- commit/teardown 无法在进程内证明时的 process supervisor，以及 poison 的恢复；
- public `compilation-manifest.json`、`diagnostics.json` schema v1 和正式 profile
  revision freeze，包括 controlled 41-key authority 的正式 output 表达与需要时另行定义的
  composite identity；
- C5/C6 新 revision package metadata/NodeNext 与 C7 selector/root activation；
- public capability 开启以及 host 侧只在 `emitted` 后创建可执行引擎、
  diagnostics-only 只展示不执行的完整接线。
