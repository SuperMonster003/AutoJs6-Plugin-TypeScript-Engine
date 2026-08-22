# R2-M2-C6-C Worker Snapshot Node Resolution Kernel

> **R2-M3-S1 supersession（2026-08-14）。** 本页的 archive/start revision 描述只保留为
> pre-S1 provenance；当前 formal input archive 只接受 revision 1，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。Worker resolver-kernel 结论继续保留。

日期：2026-08-02

状态：**Worker 侧纯 snapshot resolver kernel 与独立 Node oracle 原子证据已完成。** 本文
保留该切片尚未接入 TypeScript Program 时的隔离边界；完整 `R2-M2-C6` 已于 2026-08-02 由
[Controlled Node Resolution Program](CONTROLLED_NODE_RESOLUTION_PROGRAM_R2_M2_C6.md)
关闭。ordinary compiler-host reads 仍不可见 package targets，resolver 精确授权的 target
现在可由 Program 消费；P1/P2 仍非目标。

## 交付边界

`compiler-worker.js` 内的 `resolveControlledNodeSnapshot` 是 C6-A Kotlin kernel 的 Worker
等价实现。调用输入必须是已经接受且 deep-frozen 的 request、C5 package metadata catalog
和显式 target inventory；解析结果只可能命中该 inventory，或返回稳定 failure。它不调用：

- `ts.sys`、filesystem、network 或 Node 默认 module search；
- `ts.resolveModuleName`、`resolveModuleNameLiterals` 或其他隐式 fallback；
- `createIncrementalProgram` 或任意 Program 构造路径。

在 C6-C 原子切片建立时，源码 fence 只允许该函数作为孤立内核存在；C6-D ingress 随后把
target bytes 保留在独立 `retainedPackageTargets` catalog，但当时的 ingress、ordinary compiler
VFS 与 compiler host 尚未调用该 resolver，因此本切片 oracle 的 resolver Program activation
为 **0**。完整 C6 后来增加 production call site 与 resolver grant；它没有改变本函数自身
不访问宿主、默认 resolver 或 Program 构造路径的纯内核性质，也没有让 ordinary host reads
看到 target。

## 冻结语义

Worker kernel 与 C6-A/C6-B 共享完全相同的 18 项
`NODE_RESOLUTION_*` failure catalog，并冻结以下 closed-world 行为：

- `exports` / `imports` exact 与最具体 pattern；
- nearest accepted nested package boundary；
- 按 JSON property order 选择 condition，active conditions 为 mode、隐式 `types`、隐式
  `node`，再加显式 custom conditions；
- array target 顺序 fallback，`null` 是 terminal block；
- `.js` target 只按 `.ts`、随后 `.d.ts` 的固定顺序 substitution；
- source target 只接受 `.ts`，declaration target 只接受 `.d.ts`；`.mts/.cts` 保持
  fail closed，留给 P1/P2；
- path、case-fold、boundary、cycle、target existence 与 source kind 全部在 snapshot 内判断。

ECMAScript canonical integer-index condition key 会改变 JavaScript object enumeration order，
因此 kernel 明确拒绝 `0..4294967294`。`4294967295`、更大的十进制文本和带前导零的
`01` 不是 integer-index，继续按普通 JSON property order 处理。JVM 与 Worker oracle 均冻结
这个边界。

## 独立 Worker oracle

验证入口：

```powershell
node app/src/test/scripts/verify-r2-node-resolution-worker.js `
  app/src/main/assets/typescript-compiler-r1/compiler-worker.js `
  <typescript.js> `
  6.0.3
```

Harness 在 Node VM 中加载真实 Worker 源码和 pinned TypeScript 6.0.3，同时把 `ts.sys` 包装为
一旦访问即失败的 proxy，并把 Program 构造替换为计数后失败的 sentinel。输入在调用前递归
freeze。成功输出固定为：

```text
R2-M2-C6 Worker snapshot resolver verified: 14 resolutions, 19 failures, 18-code Kotlin catalog, host I/O=0, Program=0
```

14 个 resolution oracle 覆盖 package/imports、scoped pattern、nested boundary、condition
顺序、custom condition、array fallback、`.js` substitution 与 relative target。19 个 failure
oracle 覆盖完整稳定 failure catalog，并额外冻结整数索引上下界；计数本身由 harness 显式
assert，不能静默漂移。

Gradle 原子入口为：

```powershell
.\gradlew.bat --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" `
  :app:verifyR2M2C6CWorkerSnapshotNodeResolutionKernel
```

该 gate 依赖 C6-B parity/package-target gate，并再次检查 failure catalog、纯内核标记、隔离
oracle 中的 host-I/O/Program 禁止项以及公开非激活链路。上面的 `host I/O=0, Program=0`
稳定输出仅描述该原子 harness，不表示完整 C6 的 production resolver activation 仍为 0。

## 完整 C6 后续收口

C6-D 已完成 revision-5 target transport、C5 identity 复核、`metadata -> targets -> ordinary`
ingress 与独立可清理 retention；详见
[Worker Package Target Transport](WORKER_PACKAGE_TARGET_TRANSPORT_R2_M2_C6D.md)。完整 C6
随后关闭了当时列出的四项工作：

1. compiler-host resolver hook 已调用本内核，并把成功结果转换为 TypeScript resolution；
2. retained targets 仅通过 resolver grant 受控暴露，ordinary host reads、root 提升与任意宿主
   fallback 继续禁止；
3. 18 项 resolution failure catalog 已映射为 typed internal result，失败无 emit、无宿主
   fallback 且 successor clean；
4. Program 端到端 gate 已建立，路线中的完整 `R2-M2-C6` 可勾选。

生产 compilation profile 仍是 config-none Rhino，四项 public capability 仍为 `false`；本切片
不改变 Binder transaction prefix 或公开可用性。
