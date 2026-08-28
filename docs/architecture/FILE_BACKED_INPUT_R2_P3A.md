# R2-P3a File-backed Native Input

> **S10-1 performance amendment（2026-08-25）。** 本项只取代 S5-5 的 batch 数量：
> production middle envelope 最多 6 条 frame，但 Base64 payload chunk 仍最多 4 条，
> batchable middle frame 另受 92 KiB encoded 上限约束；start/end、window=1 与 576 KiB
> hard envelope 不变。标准库 97 -> 68 envelopes 的四设备数据见
> [冷启动与 ingress 观察](../maintenance/COLD_START.md)。

> **S5-5 performance amendment（2026-08-24）。** 本页的 logical frame、cursor、digest
> 与 release ownership 保持有效；production 现由 `R2BatchedProjectInputFramer` 单独发送
> project start/end，并把最多 4 条中间 config/file frame 封入一个 bounded envelope。
> Worker 仍顺序执行每条原 handler，全部成功后只 ACK 最后 sequence。下文的 window=1
> 指当前外层 envelope；逐 inner-frame ACK 的文字保留为 P3a 历史实现边界。

状态：production native input source complete，capability disabled，尚不是
Public R2。

P3a 把 accepted preview 的 caller archive 从“解压 bytes + UTF-16 text heap
snapshot”迁移到 app-private file-backed VFS spool、metadata-only snapshot 和
bounded random-access framing。它关闭的是 **native caller-input
materialization**，不是 Worker/TypeScript 的 64 MiB end-to-end capacity。

后续 P3b-b1 在不改变该 file-backed ownership shape 的前提下，把当前 raw
frame/JSON envelope 调整为 64/128 KiB，并加入 DEBUG Android capacity proof；
本节保留的 16/32 KiB 数值用于描述 P3a 当时的实现。

## 范围与非目标

P3a production 路径包括：

- service-owned caller FD 的 fixed-offset private capture；
- frozen ZIP v1 input envelope 的 streaming structural/payload validation；
- caller payload 到 app-private uncompressed VFS spool 的事务式写入；
- manifest/inventory/hash/fatal UTF-8 validation；
- plugin-owned trusted declarations 注入同一 spool；
- metadata + offset/length/digest snapshot；
- native framer 对 snapshot 的最多 16 KiB random-access cursor read。

P3a 不改变：

- AIDL transaction、frozen contract version/hash 或 ZIP v1；
- provisional request/output/diagnostics schema；
- Worker 的 512 files、8 MiB/file、8 MiB total admission；
- Worker 对 decoded `String` 和 TypeScript program/AST 的持有；
- output 的 heap/result 和 private ZIP 容量；
- 四项 public capability 或 Public R2 checklist。

legacy heap adapter 仍可用于受限测试/内部兼容路径，并保留自己的 materialization
配额；`R2ProjectArchiveLoader` 的 production-shaped overload 使用 P3a
file-backed path。

## Production 数据流

```text
service-owned caller PFD
  -> fstat regular-file identity
  -> pread(0..) + SHA-256 + <=64 MiB app-private archive staging
  -> unchanged identity/size/timestamps check
  -> fsync, read-only open, unlink name on Android/Linux
  -> validateImmutableTo(frozen ZIP input limits)
       -> begin(entry)
       -> write(actual payload, <=16 KiB)*
       -> verify actual size + CRC-32 + compression ratio
       -> end(entry)
  -> app-private uncompressed VFS spool
  -> verify archive inode byte count/SHA-256 unchanged
  -> materialize request-manifest.json only (<=512 KiB)
  -> exact archive inventory + per-source size/SHA-256
  -> streaming fatal UTF-8 scan + UTF-16 code-unit count
  -> append locally verified declarations
  -> fsync, set read-only, reopen, unlink name on Android/Linux
  -> R2FileBackedProjectSnapshot
       metadata: path/UTF-8 bytes/UTF-16 units/SHA-256/roots
       index: immutable spool offset/length
  -> R2ProjectInputFramer
       one cursor + one reusable <=16 KiB byte buffer
  -> R2BatchedProjectInputFramer
       start/end single; <=6 middle frames, <=4 payload chunks per bounded envelope
       window=1 envelope/ACK
       digest recheck before file end
```

caller archive staging 和 uncompressed VFS spool 在转换期间可能同时存在。P3a
消除的是 caller source 的 native heap snapshot，不是 private disk staging。

## ZIP sink transaction

`ZipArchiveV1Validator.validateImmutableTo` 仍执行 frozen validator 的全部检查：

- EOCD、central directory、local record 和 data descriptor 一致性；
- canonical path、case-fold collision、entry/file/metadata/depth quota；
- STORED/DEFLATED actual payload size；
- actual CRC-32；
- actual total bytes 与 compression ratio；
- container 在同一 immutable random-access inode 内完整闭合。

sink callback 按 physical local-record order 到达：

```text
begin(entry)
write(bytes, offset, length <= 16 KiB)*
end(entry)
```

`end` 只在该 entry 的 actual size、CRC 和 ratio 全部通过后调用。sink 只能暂存
数据，必须等整个 `validateImmutableTo` 成功返回后才能发布。validator、source I/O
或 sink callback 任一失败都会调用一次 `abort(primaryFailure)`；abort/cleanup
失败作为 suppressed failure 保留，partial spool 不得转成 snapshot。

## Snapshot 与 framing ownership

`R2FileBackedProjectSnapshot` 不保存 caller source `ByteArray` 或 `String` map。
它保存 immutable metadata 和共享 read-only `RandomAccessFile`，每个
`openFile(fileIndex)` cursor 只允许读取 accepted offset range。shared reader 的
seek/read 在 snapshot lock 内串行执行，cursor 不能越过 indexed length。

native framer：

- 只在 `projectInputFileStart` 获得 exact ACK 后打开当前 cursor；
- P3a 初始每个 data frame 最多读取 16 KiB raw bytes；P3b-b1 当前为 64 KiB，
  再做 canonical padded Base64；
- native ↔ page JSON envelope 从 P3a 的 32 KiB 调整为当前 128 KiB；
- 外层 envelope window=1；一个 batch 最多 6 条 middle frame，其中最多顺序读取 4 条
  payload chunk；外层 ACK 前不构造下一 envelope；
- 对实际 framed bytes 重新计算 SHA-256；
- 只有 cursor 精确耗尽且 digest 等于 metadata 才发送 file-end frame；
- complete、failure、cancel、timeout 或 host release 都关闭 cursor，并清零
  reusable buffer。
- ACK/next/read/release 在 dedicated single-thread input executor 上串行；release
  的有界等待无法证明 cleanup 时，compile path 抛 cleanup-unproven 并进入
  supervision，不能把 success 继续交给 output packaging。cursor close 抛错时保留
  exact cursor 引用供同一 executor 重试。

framer 自身可索引 native file-backed capacity，以便独立验证 allocation shape；
production host 在创建/发送 frame 前仍执行更小的 Worker eligibility gate。

## 容量分层

| 层 | 当前边界 | 能证明什么 |
| --- | --- | --- |
| caller archive staging | 64 MiB container | fixed-offset private capture，不代表可编译 |
| frozen ZIP input validator | 4096 entries；64 MiB actual uncompressed total；8 MiB/entry | raw ZIP v1 可流式验证 |
| provisional manifest | 512 KiB | 唯一允许物化的 caller entry |
| caller source inventory | 最多 4095 source files；每文件 8 MiB；仍受 ZIP 64 MiB aggregate 约束 | source 可按 offset 留在 spool |
| trusted declarations | 最多 512 files；8 MiB total；8 MiB/file | plugin-owned payload 可追加到 spool |
| native VFS snapshot/index | configured 4607 files、72 MiB source + declarations | 4095 caller sources + 512 trusted declarations |
| native input frame/envelope | P3a: 16/32 KiB；logical current: 64/128 KiB raw/frame；batch 最多 6 frames / 4 payload chunks、middle frame 92 KiB、envelope 576 KiB；window=1 envelope | 单次 native framing allocation/backpressure |
| Android production admission | 512 files、8 MiB/file、8 MiB total | 当前实际 production compile admission |
| P3b-a Worker 内部 envelope | 4607 files、8 MiB/file、72 MiB total | desktop direct-framing capacity，不绕过上一行 |

72 MiB 来自冻结 caller input 上限与 8 MiB trusted-declaration budget 的组合，不是
新的 ZIP 或 public quota。`request-manifest.json` 也占 frozen 64 MiB actual
uncompressed aggregate，因此可达 source payload 小于或等于该 aggregate。

trusted declarations 是签名 APK 内的本地 trust root；当前 declaration loader
和 injection 仍可能短暂持有本地 text/单文件编码 bytes。P3a 的 file-backed
声明严格限定为 caller-input snapshot/framing，不把一个 64 KiB reusable raw
buffer 包装成“整个 native 进程恒定内存”。

## 为什么还不是端到端 64 MiB

Worker 仍在 `projectInputFileStart` 为当前文件分配完整 `Uint8Array`。file end
完成 SHA-256 与 fatal UTF-8 后，byte array 会清零，但 decoded text 被加入
project file inventory；P3b-a 已确保只保留一份 text record、VFS 不重复编码，
并 lazy-create/cache `SourceFile`。但所有 source/declaration `String` 仍可能一直
保留到编译结束，`createIncrementalProgram` 也会持有 SourceFile、symbol/type
information 和其他 AST/program state。

因此 8 MiB input bytes 已可能对应更高的 WebView renderer heap；把 native gate
直接提高到 64 MiB 会放大 UTF-16、AST、diagnostics 和 emit 的并发峰值。P3b-a
虽已用 path/directory/token budgets、incremental diagnostics、per-job release
和受 512 MiB old-space 限制的 Node 64+8 MiB gate 收窄风险，P3b-b1 也已证明
API 24/28/35 上的 DEBUG low-token ASCII boundary；它仍不证明 minified release、
Unicode/high-token、重复大任务 PSS 或 LMK/OOM 下的完整安全 envelope。

## Cleanup 与 fail-closed

- archive staging 和 VFS spool 都创建在 plugin private directory；
- Android/Linux 在只读 reader 打开后立即 unlink name；
- Windows 不能 unlink open file，保留 read-only fallback name 到 reader close；
- `R2AdaptedProjectArchive.close()` 关闭 snapshot；coordinator 必须在 success、
  reject、Worker failure、cancel、timeout、callback/death 和 shutdown 路径关闭它；
- partial ZIP entry、manifest/hash/UTF-8 failure 或 spool seal failure 都删除
  staging state；
- reader close 或 fallback delete 无法证明时抛出
  cleanup-unproven failure，不能释放/复用 admission slot 假装成功。

metadata 在 snapshot close 后仍可用于 exact input/result provenance comparison，
但 `openFile` 必须拒绝读取已关闭 snapshot。

## 当前证据边界

JVM regressions 覆盖：

- STORED/DEFLATED sink bytes、最多 16 KiB callback 和实际 size/CRC/ratio；
- sink failure 与晚到 CRC failure 后的 `abort`/partial discard；
- 超过旧 1 MiB materialization gate 的 production loader file-backed snapshot；
- invalid UTF-8、archive hash/ZIP failure 和 private-directory cleanup；
- framer 跨过 8 MiB Worker budget 时的 one-chunk read；
- changed digest、short cursor、ACK ordering 和 cursor release。

“跨过 8 MiB”测试只调用 native framer，不经过 production Worker admission，也
不是设备 renderer/memory evidence。

## P3b-a/P3b-b 与 Public gate

P3b-a 已定义并实现 Worker-side representation/lifetime 的 desktop slice：
4607 files/72 MiB direct-framing envelope、单份 decoded String、lazy
`SourceFile`、解析前 complexity budgets、incremental diagnostics 和 `finally`
release。它没有消除同一 job 的 retained strings + AST 峰值，也没有解除
production 512 files/8 MiB total gate。完整说明见
[R2-P3b-a Worker Input Capacity and Lifetime](WORKER_INPUT_R2_P3B_A.md)。

P3b-b1 已覆盖 DEBUG direct/Binder 的 64 MiB STORED/DEFLATED inclusive/`+1`、
64 KiB transport、exact success realm retirement 与 small successor，见
[R2-P3b-b1 Android Device Capacity and Exact Realm Retirement](WORKER_INPUT_R2_P3B_B1.md)。

在开放 Public R2 前，完整 P3b-b 至少还需要：

- minified release 的真实 external-caller 64 MiB full pipeline；
- Unicode/high-token、UTF-8、digest、Base64、sequence/ACK 等设备负向；
- Roadmap 选定 API/WebView 组合上的 renderer peak、稳定性和重复运行；
- ingress/parse/semantic/emit cancel/timeout、active renderer termination 与
  successor recovery；
- archive staging、VFS spool、cursor、Worker 和 output 的全路径 cleanup proof。

在这些 P3b-b/device gates 以及其余 Public exit gate 完成前：

- `typescriptCompilerAvailable=false`
- `typescriptProjectCompilation=false`
- `typescriptSemanticDiagnostics=false`
- `typescriptSourceMapV3=false`
- Public R2 checklist 保持 `0/54`

相关文档：

- [R2 Provisional Project Archive Adapter](PROJECT_ARCHIVE_R2_PROVISIONAL.md)
- [R2 Provisional Framed Project Transport](PROJECT_TRANSPORT_R2_PROVISIONAL.md)
- [R2 Provisional Project Coordinator](PROJECT_COORDINATOR_R2_PROVISIONAL.md)
- [R2 Production/Public-shaped Admission Preview](PUBLIC_ADMISSION_R2_PREVIEW.md)
- [R2-P3b-a Worker Input Capacity and Lifetime](WORKER_INPUT_R2_P3B_A.md)
- [R2-P3b-b1 Android Device Capacity and Exact Realm Retirement](WORKER_INPUT_R2_P3B_B1.md)
- [Compiler IPC v1](IPC_V1.md)
