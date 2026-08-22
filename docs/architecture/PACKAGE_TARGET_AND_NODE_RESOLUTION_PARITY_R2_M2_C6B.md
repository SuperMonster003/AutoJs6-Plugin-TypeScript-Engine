# R2-M2-C6-B Package Target Authority and Node Resolution Parity

> **R2-M3-S1 supersession（2026-08-14）。** 本页的 archive/start revision 描述只保留为
> pre-S1 provenance；当前 formal input archive 只接受 revision 1，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。target policy/parity 结论继续保留。

日期：2026-08-02

状态：**C6-B 的 package target authority 与 TypeScript 6.0.3 parity 原子证据已冻结。**
本文保留该切片验收时 resolver/Program activation 为 **0** 的历史边界；完整 `R2-M2-C6`
已于 2026-08-02 由
[Controlled Node Resolution Program](CONTROLLED_NODE_RESOLUTION_PROGRAM_R2_M2_C6.md)
关闭。ordinary compiler-host reads 仍不可见 package targets，但 resolver 精确授权的 target
现在可由 Program 消费；target 仍不是 root。P1/P2 继续是独立非目标。C6-D 的原子 transport
证据见 [Worker Package Target Transport](WORKER_PACKAGE_TARGET_TRANSPORT_R2_M2_C6D.md)。

## 完成边界

C6-B 冻结两项相互绑定、但仍保持离线的 authority：

1. C5 已接受的 package metadata identity 到精确 package target bytes 的控制面、inventory
   与 native snapshot 形状；
2. snapshot-only Kotlin resolver 对 pinned TypeScript 6.0.3 的封闭子集 parity oracle。

Package target 只是被 C5 identity 授权的候选解析目标，不是 project root，也不会自行进入
Program。整个 parity harness 只使用内存中的 `ModuleResolutionHost`；宿主 filesystem、网络、
Node.js 默认搜索路径与隐式 package fallback 均不是 authority。

## Package target policy authority

冻结 fixture：`app/src/test/resources/r2-package-targets/policy-v1.json`。

| 字段 | 冻结值 |
| --- | --- |
| schema / revision | `autojs6.typescript.package-target-policy` / `1` |
| policy revision | `1` |
| policy fingerprint | `e34f4825d8f75ba1b674849ff79c2be8f9c1b88fe940add86e6e466a139bbbaf` |
| inventory canonical domain | `autojs6.typescript.package-target.inventory.v1` |
| metadata binding | `exact-c5-inventory-fingerprint` |
| shared aggregate budget | `required-non-additive` |
| dependency root prefix | `packages/` |
| allowed source suffixes | `.ts`、`.d.ts` |
| file/path limits | 256 files；单路径 1024 chars；路径总长 32768 chars |
| byte limits | 单文件 8 MiB；target inventory 总量 8 MiB，边界值可接受 |

Policy fingerprint 标识 policy 本身，不等同于某次 target inventory fingerprint。Fixture 中的
示例 inventory fingerprint 为
`e8085791bdaf6118cc80093da3aa6920cd6df4b90cde32113e05c1fa6fc14eb5`，它精确提交到 C5
metadata inventory fingerprint
`15b6208eccedc31e14cb18fe085762066e1668d3652e44319e4813e0a57ef447`。

### C5 精确绑定

- `R2PackageTargetInventory` 的 canonical input 首先写入完整 C5 metadata inventory
  fingerprint，再按 canonical 顺序提交每个 target 的 path、size 与 SHA-256；
- control plane、manifest、重建出的 C5 inventory 与 target inventory 中的绑定 fingerprint
  必须完全一致；只要 identity 漂移就以
  `PACKAGE_TARGET_METADATA_IDENTITY_INVALID` fail closed；
- 每个 `packages/...` target 必须且只能属于一个 C5 已声明的 `dependencyRoot`；target 不能
  反向扩大 C5 允许的依赖集合；
- `.ts` 与 `.d.ts` 是 C6-B 唯一接受的 package target source kinds。`.mts`、`.cts`、
  `.tsx`、JavaScript 与任意目录目标都不能借此进入 snapshot authority。

### 8 MiB 是非叠加预算

Policy 同时设有单 target file 与 target inventory 的 inclusive 8 MiB 上限，但
`required-non-additive` 明确表示它不是在请求总预算之外再增加 8 MiB。受控 config、C5
package metadata、package targets、project sources 与 local declarations 必须共同装入既有的
单一 8 MiB snapshot aggregate budget；heap 与 file-backed snapshot 使用同一语义。

### 稳定 package target code catalog

Catalog 恰好包含以下 7 个 code，顺序与拼写均属于 fixture contract：

```text
PACKAGE_TARGET_PATH_INVALID
PACKAGE_TARGET_ROOT_NOT_ALLOWED
PACKAGE_TARGET_SOURCE_KIND_UNSUPPORTED
PACKAGE_TARGET_INVENTORY_INVALID
PACKAGE_TARGET_CASE_COLLISION
PACKAGE_TARGET_QUOTA_EXCEEDED
PACKAGE_TARGET_METADATA_IDENTITY_INVALID
```

`R2PackageTargetPolicyTest` 冻结 fixture exactness、policy fingerprint、C5 identity 绑定、
dependency-root ownership、canonical ordering、duplicate/case collision、路径与 byte inclusive
边界，以及上述 7-code catalog。

## Native payload 与生产激活边界

Native archive revision 5、heap snapshot 与 file-backed snapshot 已具备 package target
inventory/payload 形状，并按 inventory 的 exact path、size 与 SHA-256 打开 bytes。C6-D 已将
这套 authority/bytes 按 `metadata -> targets -> ordinary` 顺序送入 Worker，并保留在独立、可清理
的 `retainedPackageTargets` catalog：

- ordinary compiler VFS 的 `fileExists` / `readFile` / `readRecord` 仍看不到 target，
  `rootNames` 与 ordinary inventory 也不包含它们，因而 `host-visible=0`；
- 在 C6-B/C6-D 原子门禁建立时，C6-C Worker kernel 尚未由 production ingress/host 调用，
  compiler host 也尚未接入 resolver hook；
- 完整 C6 后续通过 compiler-host resolver callbacks 调用该 kernel，并只把成功解析的精确 target
  grant 给 Program；
- package target 没有成为 Program root，也没有进入 ordinary Program VFS；
- resolver failure 已作为 typed internal diagnostic 接入 production result/output contract。

因此 `host-visible=0` 的现行精确含义是：**ordinary host reads 始终不可见**。C6-B/C6-D
原子门禁中的 Program materialization 与生产 resolver 调用为 0 仍是有效历史证据，但完整 C6
已经在该边界之外增加 resolver-granted Program visibility，未扩大 ordinary host authority。

## TypeScript 6.0.3 parity oracle

共享 fixture：`app/src/test/resources/r2-node-resolution/parity-v1.json`；Node oracle：
`app/src/test/scripts/verify-r2-node-resolution-parity.js`。Fixture 恰好包含 **12 supported** 与
**6 fail-closed** cases，共 18 cases，并固定 TypeScript version `6.0.3`。

12 个 supported cases 覆盖：

- `exports` / `imports` exact key 与最具体 pattern；
- condition 的 JSON property order，以及隐式 `types`、`import` / `require`、`node` 和受信任
  custom condition；
- scoped dependency 与 nearest accepted nested package boundary；
- array target 的按序 fallback 与 `null` stop；
- `.js` target 的确定性 `.ts`、随后 `.d.ts` substitution；
- target 仅能命中显式内存 inventory 的 `ModuleResolutionHost-only` 行为。

6 个有意保留的 fail-closed cases 是：

1. 没有 `exports` 时的 legacy `types` fallback；
2. directory mapping；
3. project package self-name resolution；
4. `.mjs` 到 `.mts` target（留给 P1/P2）；
5. `.cjs` 到 `.cts` target（留给 P1/P2）；
6. canonical integer-index condition key。JavaScript 会重排这类 object key，因此 Kotlin
   policy 直接以 `NODE_RESOLUTION_PACKAGE_METADATA_INVALID` 关闭该不一致面。

Parity oracle 的稳定成功输出为：

```text
R2-M2-C6-B TypeScript 6.0.3 resolver parity verified: 12 supported, 6 fail-closed, 18 stable failures, ModuleResolutionHost-only
```

其中 `18 stable failures` 指
[C6-A](SNAPSHOT_NODE_RESOLUTION_KERNEL_R2_M2_C6A.md) 冻结的 18-code resolver failure
catalog，不是 18 个失败的 parity cases。该 oracle 证明的是明确列出的 closed-world subset，
不是对 TypeScript/NodeNext 全部解析行为的宣称。

直接 oracle 入口：

```powershell
node app/src/test/scripts/verify-r2-node-resolution-parity.js `
  <typescript.js> `
  app/src/test/resources/r2-node-resolution/parity-v1.json `
  6.0.3
```

同一 fixture 的 `expectedR2` 由 `R2NodeResolutionKernelTest` 消费，避免 TypeScript oracle 与
Kotlin kernel 使用两套可独立漂移的 golden。

## 完整 C6 后续收口

完整 `R2-M2-C6` 已于 2026-08-02 完成，路线 checkbox 可勾选。C6-B 建立时列出的后续项
现已按以下边界关闭：

- compiler host 已接通 snapshot-only resolver hook，使非 root import 仅通过 grant 消费 C6
  authority；
- 稳定 resolution failure 已接入 typed internal result，失败无产物，后续 clean successor
  不继承 resolver 状态；
- Worker/Program 端到端 gate 已覆盖受控解析、typed failure、无 root/output 污染与
  no-host-fallback；
- production/public capability 继续关闭，完整 C6 不改变公开可用性。

C6-D 的 exact rev5 shape、C5 identity、独立 retention、9 项 rejection 与清理证据见
[Worker Package Target Transport](WORKER_PACKAGE_TARGET_TRANSPORT_R2_M2_C6D.md)。

`R2-M2-P1` 的 provisional NodeNext profile 与 `R2-M2-P2` 的 `.mts/.cts` module/output 语义
仍是独立 checklist；C6-B 的 intentional fail-closed 不得提前替它们承诺行为。
