# ZIP Archive Container v1

> **S1-2 UTF-8 amendment（2026-08-23）。** 结构版本仍为 v1；对先前合法的 ASCII
> archive 完全兼容，并新增带 bit 11 的严格 UTF-8 entry name。下文路径规则已按当前
> production 实现更新；历史 golden 文件名中的 `invalid` 不再代表其中每个 section 都拒绝。

R1 只冻结 input/output FD 所承载的 ZIP **结构层**. 本文不定义或解析
`request-manifest.json`, `compilation-manifest.json`, diagnostics, compiler profile,
允许的项目根目录或文件之间的语义关系; 这些内容仍由 R2 冻结. 因此一个结构上
合法的 ZIP v1 不一定是合法的编译请求或输出.

release source set 内的结构校验器参考实现位于
`app/src/main/java/io/github/supermonster003/autojs6/plugin/typescript/archive/`。
它直接读取 raw EOCD, central-directory 和 local-file records, 再流式读取或解压
entry data. `ZipInputStream`, central-directory size 和 entry header 都不能单独
作为可信证据. `File` 入口先按 container quota 捕获独立的 byte snapshot, 再只对
该 snapshot 执行多遍结构与 payload 校验; 捕获完成后对原文件的改写不会改变本次
校验所见 bytes.

R1 只冻结该结构实现与 golden，并未把它接入仍然全拒绝的 compilation admission
或 extraction 链. `File` helper 的 snapshot 最多会分配一个 container limit 大小
的 `ByteArray`，适合冻结/测试但不是 R2 的低内存集成方案. R2 必须先把 caller FD
有界复制到 private staging inode，再在同一个不可变 snapshot 上完成
validate + extract，并按设备内存预算实现 streaming/mapped 读取；不得校验原始
caller file 后再重新打开它使用.

## 物理布局

- 容器是一个普通、可 seek 的单卷 ZIP. disk number 和 entry start disk 必须为 0,
  `entries on disk` 必须等于总 entry 数.
- EOCD 必须是文件的唯一、无歧义终止记录; EOCD comment 后不得有 trailing byte.
- 不允许 self-extracting preamble. 第一个 local header 位于 offset 0; 后续 local
  record 紧密相接, 不允许 gap、重叠或隐藏 record.
- central directory 紧接最后一个 local record, EOCD 紧接 central directory.
  central directory 只包含声明数量的 file header, 不允许 digital signature 或
  其他 record.
- local 与 central 的 raw name bytes, version-needed, flags, compression method,
  DOS time/date 必须一致. 不使用 data descriptor 时, CRC 和 size 也必须一致.
- 空 ZIP 在结构层允许, 其 EOCD 位于 offset 0. R2 manifest/schema 可以再拒绝它.
- 任何 16-bit/32-bit ZIP64 sentinel、ZIP64 EOCD/locator 或 multi-disk 布局均拒绝.
  v1 不接受 preamble/trailing data 来包裹另一种容器.

## Flags、压缩与 data descriptor

general-purpose flags 只允许:

| Bit | 含义 | v1 规则 |
|---:|---|---|
| 3 | data descriptor | 只允许 `DEFLATED`; local CRC/size 必须为 0 |
| 11 | UTF-8 name | entry name 含 non-ASCII byte 时必须设置；纯 ASCII path 可选 |

bit 0 encryption、deflate tuning bits 1/2 和其他所有 bit 均拒绝. AES/strong
encryption 因此也被拒绝.

只允许:

- method 0 `STORED`, version-needed 1.0 至 2.0, 且 compressed size 必须等于
  uncompressed size.
- method 8 `DEFLATED`, version-needed 必须为 2.0, raw DEFLATE stream 不得需要
  preset dictionary 或在 stream 末尾留有 compressed trailing byte.

v1 的 data descriptor 必须是带 `0x08074b50` signature 的 16-byte、32-bit
descriptor, 且 CRC/compressed/uncompressed size 与 central header 精确一致.
无 signature descriptor、64-bit descriptor 和 `STORED` descriptor 均拒绝.

## Extra fields、comments 与 attributes

- local 和 central extra length 必须为 0. v1 有意拒绝全部 extra fields, 包括
  ZIP64、Unicode path alias、extended timestamp、AES 和 NTFS fields, 避免同一
  entry 存在第二套路径、size 或加密解释.
- entry comment 和 archive comment 是不透明、无语义 bytes, 可以存在, 但计入
  metadata quota. 若 comment 内伪造 EOCD 造成多个可终止候选, 容器以歧义拒绝.
- creator system 只允许 0 (`FAT/MS-DOS`) 或 3 (`Unix`). DOS attributes 中
  volume-label `0x08`、directory `0x10`、device/reserved `0x40` 和保留高位
  `0x80` 均拒绝; regular file 只允许 read-only/hidden/system/archive 属性.
  Unix mode 的 file-type bits 可以为 0 (未声明) 或 `S_IFREG`; symlink,
  directory, socket, FIFO 和 device 均拒绝. ZIP v1 不把 entry materialize 为链接.

## UTF-8 与 canonical path

- entry name 的 raw bytes 非空且最多 1024 bytes。含任意 high-bit byte 时必须设置
  UTF-8 flag；设置后（以及纯 ASCII name）都按 strict UTF-8 解码，malformed、overlong
  或不可映射序列拒绝。不得用平台默认 charset、CP437 fallback 或 replacement character。
- path 使用 `/`, 是非空相对路径. 禁止 leading `/`, ASCII drive prefix,
  backslash, C0/C1 control, 空 segment, `.`、`..` 和 trailing `/`; UTF-16 lone
  surrogate 不能形成合法 UTF-8，因此同样拒绝。
- path depth 是 `/` 分隔后的 segment 数, 最多 64.
- 每个 entry 都必须是 regular file; directory placeholder 也不允许.
- Unicode 文件名按原始 scalar sequence 保留，不做 NFC/NFD normalization，也不依赖
  Android API 或 locale 的 Unicode casing 数据。

duplicate/case collision key 冻结为:

```text
ASCII-lowercase(path)
```

其中只把 `A..Z` 加上固定偏移转换为 `a..z`, 其他 Unicode code unit 原样保留.
同一 archive 中 key 重复即拒绝. 实现不得调用设备 locale、`Normalizer` 或运行时
Unicode casing table. 因此同一 ZIP v1 在 Android API 24/25/31/33 上具有相同的
path 接受集和 collision key.

## 基础配额

MiB 均为 `1024 * 1024` bytes. limit 是 inclusive.

| 配额 | Input | Output |
|---|---:|---:|
| 整个 ZIP container | 64 MiB | 128 MiB |
| regular files | 4096 | 8192 |
| 实际 uncompressed 总量 | 64 MiB | 256 MiB |
| 单个 regular file | 8 MiB | 256 MiB |
| raw entry name | 1024 bytes | 1024 bytes |
| path depth | 64 | 64 |
| structural metadata | 8 MiB | 8 MiB |
| 单 entry/总压缩比 | 100:1 | 100:1 |

output 的 JS、Source Map、manifest 和 diagnostics 可以在 R2 增加更小的语义配额;
这些不属于本容器冻结.

`structural metadata` 精确定义为:

```text
EOCD fixed bytes + archive comment
+ 每个 central fixed header + central raw name + entry comment
+ 每个 local fixed header + local raw name
+ 每个 signed data descriptor
```

v1 extra fields 长度为 0. Compressed payload 不计入 metadata, 但计入 container.

validator 先用 header declared size 做早期拒绝, 随后按实际 streaming bytes 再次
执行单文件和总量配额、CRC-32 与 size 校验. 压缩比不用浮点数:

```text
uncompressedBytes <= compressedBytes * maximumRatio
```

该不等式对每个 entry 和 archive 总和分别执行并做 overflow-safe 计算.
`uncompressedBytes == 0` 合法; `compressedBytes == 0` 且 uncompressed 非 0 时拒绝.

## Golden 与错误类别

文本 `.hex` fixtures 位于 `app/src/test/resources/archive-v1/`. 它们固定 valid
STORED/DEFLATED/ASCII-with-UTF-8-flag/descriptor/comment/empty、带 UTF-8 flag 的
non-ASCII path、精确
`1200 / 12 == 100:1` 压缩比边界, 以及 encryption、ZIP64、extra、
preamble/trailing、未带 UTF-8 flag 的 non-ASCII path、malformed UTF-8、control path、
ASCII case collision、DOS/Unix
non-regular、local/central 不一致和 CRC 等拒绝. `fixtures.sha256` 固定两个
fixture 文件在换行统一为 LF 后的 SHA-256.

结构或不支持的 feature 映射为 `FORMAT`; header/data/CRC 不一致映射为
`INTEGRITY`; UTF-8/canonical path、collision 和 non-regular 映射为
`PATH`; 超过任一 inclusive limit 映射为 `QUOTA`. 这些类别不冻结 R2 的 manifest
或 diagnostics 错误 schema.

`R1ZipArchiveV1InstrumentationTest` 直接把同一 `src/test/resources/archive-v1`
corpus 打入 androidTest assets，不维护第二份 fixture. 它在官方 API 24/25 x86
镜像和 API 31/33 真机上均为 1/1 pass。S1-2 后同一 corpus 复核 4 个原 valid、
1 个历史 non-ASCII-with-flag fixture 的新增 positive，以及其余 16 个 invalid 的
错误类别及 `100:1` pass / `99:1` reject，固定 Android zlib/Inflater 的当前接受集.
