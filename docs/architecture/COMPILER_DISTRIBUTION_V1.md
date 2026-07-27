# Compiler Distribution v1

R1 将官方 `typescript@6.0.3` npm tarball 转换为一个只读, 可复现且可供
compiler host 与 Ace 后续共同消费的发行目录. 此 schema 只描述 TypeScript
发行物, 不包含项目源码, compiler profile 或编译结果.

## Lock 与获取

`compiler-distribution.lock.json` 必须使用精确版本和精确 HTTPS tarball URL.
`latest`, semver range, 重定向到非原始 origin 以及任意未锁定来源一律拒绝.

下载开始前必须校验 lock 字段格式. 下载流和落盘文件必须分别验证:

- npm tarball SHA-1.
- npm SRI SHA-512.
- 项目额外固定的 tarball SHA-256.

任一摘要不匹配时不得生成发行目录. 构建可使用显式指定的离线 tarball, 但仍执行
完全相同的三摘要校验.

## 发行目录

```text
typescript/<version>/
  distribution.json
  LICENSE.txt
  ThirdPartyNoticeText.txt
  lib/typescript.js
  lib/typescript.d.ts
  lib/lib*.d.ts
```

只允许上述文件. `_tsc.js`, `tsc.js`, `tsserver*`, localization JSON, executable
shim, package metadata 和其他 npm tarball 内容不得进入 APK.

生成器必须扫描完整 tar archive, 并在写入 allowlist 文件前拒绝:

- 多 gzip member, compressed trailing data, 超限 expanded tar stream 和非零 tar
  EOF trailing data.
- absolute, drive, UNC, empty, `.`, `..`, backslash 和非 NFC path.
- symlink, hardlink, sparse file, device, FIFO 和其他非 regular entry.
- PAX/global-PAX, GNU long-name/long-link metadata 和损坏的 tar header checksum.
- 原始或 Unicode/case-fold 规范化后重复 path.
- 超过 entry count, compressed size, expanded size 或 single-file 配额的输入.

所有输出先写入 task-owned staging 目录. 只有完整验证通过后才能发布到 Gradle
generated assets 目录.

R1 generator revision 1 的硬配额为 8 MiB compressed tarball, 32 MiB expanded tar
stream, 256 entries, 12 MiB single file 和最多 20 个 tar EOF zero blocks.

## `distribution.json`

JSON 使用 UTF-8, LF, 稳定 key/array 顺序, 不记录时间戳或绝对路径. 顶层至少包含:

- `schemaVersion`.
- npm package name/version 与 tarball source/digests.
- generator ID, revision 和 fingerprint.
- compiler, compiler API, standard libraries 与 notices component.
- 每个文件的相对 path, byte size 和 SHA-256; component 映射定义文件角色.
- component fingerprints 与总 distribution fingerprint.

文件集合 fingerprint 的输入按 path 升序排列. 每项编码为:

```text
<path> NUL <decimal-byte-size> NUL <lowercase-sha256> LF
```

然后对完整 UTF-8 byte sequence 计算 SHA-256. component fingerprint 只包含该
component 文件. distribution fingerprint 先编码
`generator NUL <generator-fingerprint> LF`, 再追加全部发行文件的上述编码并计算
SHA-256. generator 行为或 canonicalization 规则发生变化时必须升级 revision.

## APK 门禁

Debug 和 release APK 校验必须重新读取实际 ZIP entry bytes, 对照
`distribution.json` 重算 size/hash, 并验证:

- 发行目录不存在缺失或额外文件.
- APK 中只有一份 `lib/typescript.js`.
- compiler, standard libraries 和 generator fingerprint 与生成目录一致.
- 上游 `LICENSE.txt` 与 `ThirdPartyNoticeText.txt` 均存在.

运行时 compiler host 仍需在受限 JavaScript context 内读取 `ts.version`, 并要求
它与 distribution version 完全一致. 文件摘要验证不能替代该运行时探针.

## TypeScript 6.0.3 golden

R1 generator revision 1 对 lock 内 tarball 产生:

| 项目 | 值 |
|---|---|
| selected files / bytes | `112 / 13564447` |
| compiler SHA-256 | `569177652966bd528c319171c7dd22860dbf72bde116cbc4f644f1d02bb12e39` |
| compiler API SHA-256 | `c967e03c8dbb4770f7e2f0b71b5d13593de679a41cc2b60266c4b69f1342a263` |
| standard libraries files / bytes | `108 / 3784758` |
| standard libraries fingerprint | `6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f` |
| generator fingerprint | `1b60932afd24b6efb99a61ef9a87694af2e3e3ab412d55bf470b7ce1aea6fb76` |
| distribution fingerprint | `3855b04b968db8a9529b60c96dd8b8ec94fa1ee040cac10a5ab569aa6e33f658` |
