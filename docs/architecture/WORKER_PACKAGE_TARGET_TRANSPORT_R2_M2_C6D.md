# R2-M2-C6-D Worker Package Target Transport and Retention

> **R2-M3-S1 supersession（2026-08-14）。** 本页的 revision-5/31-field transport 只保留为
> pre-S1 receipt/provenance，不是当前 formal alias/fallback。当前 formal input archive 只接受
> revision 1，见 [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。target retention 结论继续保留。

日期：2026-08-02

状态：**revision-5 package target Worker transport 与独立 retention 原子证据已完成。** 本文
保留该切片尚未接入 production resolver 时的隔离边界；完整 `R2-M2-C6` 已于 2026-08-02 由
[Controlled Node Resolution Program](CONTROLLED_NODE_RESOLUTION_PROGRAM_R2_M2_C6.md)
关闭。ordinary compiler-host reads 仍不可见 package targets；只有 resolver 精确授权的 target
可由 Program 消费，且 target 仍不成为 Program root。P1/P2 继续是独立非目标。

## revision-5 start frame

C6-D 在 C5 package-metadata start shape 上精确增加以下 **7 个字段**：

```text
packageTargetSchemaRevision
packageTargetPolicyRevision
packageTargetPolicyFingerprint
packageTargetCanonical
packageTargetFingerprint
packageTargetFileCount
packageTargetByteLength
```

Worker 只接受完整、exact-key 的 revision-5 shape。Schema/policy revision 与 policy fingerprint
必须匹配冻结的 C6-B authority；canonical input、inventory fingerprint、file count 与 byte length
必须互相重建一致。Revision 2、3、4 的 exact shapes 保持不变：向 revision-4 shape 注入不完整的
target 字段不会被解释为兼容扩展，而会 fail closed。

Target canonical input 的首个绑定项是完整 C5 package metadata inventory fingerprint。Worker
先验证并重建 C5 metadata，再解析 target canonical；两者 identity 不完全相等时返回
`PACKAGE_TARGET_METADATA_IDENTITY_INVALID`。Target 不得扩大 C5 dependency roots，且 config、
metadata、targets 与 ordinary files 继续共同占用已有 file/byte budget，不获得叠加配额。

## 有序 ingress

单次 framed input 的文件顺序固定为：

```text
C5 package metadata -> C6 package targets -> ordinary project/declaration files
```

所有类别共享一个从 0 开始且连续的 `fileIndex`。Worker 只有在 C5 metadata inventory 已精确
完成后才接受第一个 target；只有 target inventory 精确完成后才接受 ordinary file。每个 target
仍逐帧验证 descriptor path、size、SHA-256、chunk 顺序、最终 digest 与 strict UTF-8。顺序、
descriptor 或 payload 任一漂移都会拒绝整个 operation，而不会发送该失败帧的 ACK。

## 独立 retainedPackageTargets

Ingress 将 target authority、冻结的 descriptor records 与可清理 text holders 分别保存在
`packageTargets`、`packageTargetFiles` 与 `packageTargetTextHolders`。构造只读 project VFS 时，
它们进入独立、冻结的 `retainedPackageTargets` catalog：

- catalog 精确保留 target inventory fingerprint 与其 C5 metadata fingerprint；
- target records、catalog 与 authority 均被冻结，只允许按已接受 path 读取；
- target 不进入 ordinary `files`、`sourcePaths` 或 `rootNames`；
- 现有 compiler-visible `fileExists`、`readFile` 与 `readRecord` 对 target 分别返回 `false`、
  `undefined`、`undefined`；只有 `retainedPackageTargets` 的独立 API 能看到它们。

因此本切片冻结的可见性结论是 **host-visible=0**。该数字精确描述 ordinary compiler VFS，
并在完整 C6 后继续成立；C6-D 原子门禁建立时，这里的 retention 尚不是 Program VFS
materialization 或 module-resolution activation。完整 C6 后续增加的是独立的 resolver grant，
没有把 retained targets 暴露给 ordinary `fileExists` / `readFile` / `readRecord`。

## release 与错误清理

清理覆盖正常 release、VFS release 与 framed-input error：

- `retainedPackageTargets.release()` 清空 target maps、case-fold index、directory index，并把每个
  retained text holder 改写为空字符串；
- `releaseProjectInputState` 同时清空 ordinary text、target text holders、target records、target
  path set 与 target authority；
- 任意 framed input 失败都会撤销匹配 request 的 `framedInput` / compilation / result 状态；
- rejected target 不会留下 ACK 或可复用 operation state；invalid UTF-8 gate 还直接证明暂存 raw
  byte buffer 被归零。

清理是幂等边界；失败请求不能把 target text、identity 或 file index 泄漏给后续 clean request。

## 9 项 rejection gate

`verify-r2-package-target-worker-transport.js` 以运行时计数固定验证恰好以下 9 类
revision-5 rejection：

1. target canonical 绑定到错误的 C5 metadata fingerprint；
2. target inventory fingerprint tamper；
3. revision-5 exact 31-field start shape 带未知字段；
4. 单文件/aggregate target quota 越界；
5. target ASCII case-fold collision；
6. 向 revision-4 start shape 注入 target 字段；
7. 在 C5 metadata 完成前发送 target，破坏 metadata -> targets -> ordinary 顺序；
8. 首个 target 已保留后，第二个 target file descriptor SHA-256 tamper，并证明首个
   target text 随 operation 一同清零；
9. target payload 不是 strict UTF-8。

其中 policy rejection 使用 C6-B 稳定的 `PACKAGE_TARGET_*` code；shape/order/descriptor 错误也必须
以单一 `projectError`、无 ACK、无 retained operation state 收口。

直接 oracle 入口：

```powershell
node app/src/test/scripts/verify-r2-package-target-worker-transport.js `
  app/src/main/assets/typescript-compiler-r1/compiler-worker.js `
  <typescript.js> `
  app/src/test/resources/r2-package-targets/policy-v1.json `
  app/src/test/resources/r2-package-metadata/policy-v1.json `
  app/src/test/resources/r2-controlled-tsconfig/policy-v1.json `
  6.0.3
```

稳定成功输出为：

```text
R2-M2-C6-D package target Worker transport verified: 2 retained targets, 9 rev5 rejections, host-visible=0, resolver/Program/I-O=0
```

该输出属于 C6-D 隔离 transport harness：其中 `resolver/Program/I-O=0` 是本原子切片的历史
sentinel，不表示完整 C6 的 production resolver 仍未激活。

Gradle 原子 gate 与完整聚合 gate：

```powershell
.\gradlew.bat --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" `
  :app:verifyR2M2C6DWorkerPackageTargetTransport
.\gradlew.bat --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" `
  :app:verifyR2ProjectCompilerCore
```

原子 gate 传递执行 C1-C5、C6-A/B/C/D、C7、JVM unit/API freeze 与 host policy；完整
聚合还覆盖完整 C6 resolver/Program gate、debug/release、四 ABI native、AndroidTest APK、
public-preview 与 shipping Release 既有证据链。

## C6-D 当时尚未激活的边界

C6-D 原子切片建立时没有完成下列工作；完整 C6 已在保持 ordinary host 隔离的前提下关闭
这些边界：

- `createProjectCompilerHost` 仍未、也不会把 `retainedPackageTargets` 接入普通 host reads；
- production compiler host 现在通过受控 resolver callbacks 调用
  `resolveControlledNodeSnapshot`；
- package targets 仍不是 Program roots，但精确解析并授予 grant 后可由 Program 读取；
- resolver success/failure 已进入 typed internal result/output contract。

C6-D gate 仍以 sentinel 冻结其隔离 harness 的 `host I/O=0`、`resolver=0` 与 `Program=0`；
完整 C6 的 production gate 则证明 resolver-granted Program 消费、typed failure/output、
no-host-fallback 与 clean successor。路线中的 C6 已可勾选，P1/P2 仍保持独立待办。
