# T3-4 TypeScript 7 原生后端评估

> 结论（2026-08-28）：**TypeScript 7.0.2 不进入当前生产后端（no-go）**。
> Android arm64/x86_64 二进制本身可行且原型明显更快；阻塞项是 7.0 官方明确不提供
> programmatic API，无法在不削弱冻结输入/输出、VFS、diagnostics 与取消契约的前提下
> 替换当前 TypeScript 6.0.3 Worker。等 7.1 的新 API 稳定后重新评估。

## 官方基线

- TypeScript 团队于 2026-07-08 发布 7.0，说明它是 Go 原生 port，full build 通常
  提升 8–12 倍；同一公告明确说明 7.0 **不带 API**，预计 7.1 提供新的、不同的 API：
  <https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/>。
- 本次锁定 release tag `typescript/v7.0.2`、commit
  `2bd066d87f5bafd315be9f40889d0a60b9e58e0b`。官方 release 有 23 个跨桌面/服务器
  平台资产，但没有 Android 资产：
  <https://github.com/microsoft/typescript-go/releases/tag/typescript%2Fv7.0.2>。
- 源码仓库的 Program、parse/type-check、JS emit、build/incremental 状态为 done，API
  标记为 not ready：<https://github.com/microsoft/typescript-go>。
- 主许可证为 Apache-2.0：
  <https://github.com/microsoft/typescript-go/blob/typescript/v7.0.2/LICENSE>。
  `NOTICE.txt` 还包含 DefinitelyTyped、Unicode、DOM/W3C 等第三方告知；如将来分发
  二进制，必须随包保留 LICENSE、NOTICE 与适用的第三方文本，不可只写一行
  Apache-2.0。

## 可复现构建

源码 `go.mod` 要求 Go 1.26。本次使用官方 `go1.26.7.windows-amd64.zip`，校验
SHA-256：

```text
f4f534a486e4bc3387fa18f08208f2f854b7aaea8a08f2a2d829a914a05abb11
```

构建命令的核心参数如下（均从锁定 tag 执行）：

```powershell
# arm64：纯 Go Android build
$env:GOOS = "android"
$env:GOARCH = "arm64"
$env:CGO_ENABLED = "0"
go build -trimpath -ldflags "-s -w" -o tsc-android-arm64 ./cmd/tsgo

# x86_64：Go 1.26 要求 external linking，使用 NDK 29 / API 24 clang
$env:GOOS = "android"
$env:GOARCH = "amd64"
$env:CGO_ENABLED = "1"
$env:CC = "<ndk>/toolchains/llvm/prebuilt/windows-x86_64/bin/" +
  "x86_64-linux-android24-clang.cmd"
go build -trimpath -ldflags "-s -w" -o tsc-android-x86_64 ./cmd/tsgo
```

| ABI | 未压缩大小 | SHA-256 | ELF / 动态依赖 |
|---|---:|---|---|
| arm64-v8a | 29,294,888 B（27.94 MiB） | `6f85d8a832071abafcf43188c6a66cadc54371f733d3f988cd2f0141bc6ecb91` | ELF64 AArch64 PIE；纯 Go build |
| x86_64 | 30,539,616 B（29.12 MiB） | `ae00fd7d3925346d471733f5221002434df786752a1ca471fe589389c4a65b5c` | ELF64 x86-64 PIE；`liblog.so`, `libdl.so`, `libc.so` |

如果两个 ABI 都直接放入 universal APK，仅未压缩 executable 就增加约 57.1 MiB；正式
方案必须采用 ABI split/App Bundle 或给出明确的 universal 体积预算。官方
`typescript-linux-arm64.tgz` 不能当作 Android 产物替代，生产供应链需要锁定 Android
交叉编译工具链、源码 commit、依赖和最终 ELF 摘要。

## Android 运行原型

arm64 binary 推送到 `/data/local/tmp` 后，四台已连接设备均直接执行
`--version -> Version 7.0.2`：

| 设备 | API | ABI | 版本探针 | 同一 60 文件 compile |
|---|---:|---|---|---:|
| Xiaomi 23046RP50C | 35 | arm64-v8a | 通过 | 0.141 s（首次单样本） |
| Sony G8441 | 28 | arm64-v8a | 通过 | 0.250 s |
| Sony XQ-AT72 | 31 | arm64-v8a | 通过 | 0.203 s |
| Sony XQ-DQ72 | 33 | arm64-v8a | 通过 | 0.079 s |

输入为仓库 `templates/node-esm-cjs/esm/main.mts` 加 Node profile 的 59 个 `.d.ts`，
合计 60 个 caller 文件、247,002 bytes；编译参数为 `strict`, `ES2022`, `NodeNext`,
`skipLibCheck`, `noEmit`。编译器连同内置标准库报告 123 files，全部 exit 0。

API 35 上各跑 11 次后的内部 `extendedDiagnostics`：

| 模式 | p50 | min–max | p50 memory |
|---|---:|---:|---:|
| 默认并行 | 162 ms | 95–227 ms | 28,081 KiB |
| `--singleThreaded` | 284 ms | 165–338 ms | 27,206 KiB |

x86_64 当前没有连接的对应设备，因此本次只完成 NDK API 24 交叉编译与 ELF/依赖检查，
不把它写成运行通过。所有推送到四台设备的临时源码和 binary 在采样后均已删除。

## 与 TypeScript 6.0.3 的桌面对比

Windows 11 / AMD64 / Node 24.13.0 上，对完全相同的 60 文件、247,002-byte 输入运行
21 次热样本；6.0.3 使用本项目锁定的 `typescript.js` CLI entry，7.0.2 使用上述
Windows native build。计时包含进程启动与 compiler load：

| 后端 | cold | p50 | p95 | 相对 6.0.3 p50 |
|---|---:|---:|---:|---:|
| TypeScript 6.0.3 JavaScript | 595.234 ms | 557.735 ms | 618.056 ms | 1.00× |
| TypeScript 7.0.2 native 默认 | 91.392 ms | 90.638 ms | 98.404 ms | 6.15× |
| TypeScript 7.0.2 native single-threaded | — | 101.763 ms | — | 5.48× |

该小样本没有达到官方 full-build 的 8–12×并不矛盾：进程启动占比更高，且输入只有
约 241 KiB。它仍足以证明 native 后端值得在 API 可用后继续投入。

## 契约差距

当前生产 Worker 不是一次普通 `tsc` CLI 调用。它依赖 programmatic API 完成：

- 只读、内存/FD-backed VFS，不向 compiler 暴露宿主真实路径；
- Rhino/NodeNext profile、受控 resolver、package target 与 runtime capability 权威；
- `createIncrementalProgram`、SourceFile 内容版本、affected-file diagnostics/emit；
- AutoJs6 TSX lowering transformer；
- 结构化 diagnostics、完整 output map、Source Map 与多重摘要校验；
- phase progress、deadline/cancel、输出配额和失败时的确定性清理。

TypeScript 7.0 只有 CLI/LSP executable，没有受支持的 compiler API。把 snapshot 写到
临时目录再启动 `tsc` 虽可做 demo，却会另开一套路径/文件生命周期、取消、输出解析与
错误映射协议，无法作为现有 frozen contract 的等价替换。未经 API 支持就绑定
`internal/*` Go package 也会把未承诺稳定的内部实现变成发布 ABI，升级风险不可接受。

即使 API 到位，native backend 也应使用新的 profile/backend revision，并与 6.0.3
并行一段时间；不能复用旧 compiler distribution fingerprint 冒充语义完全相同。

## 决策与重开条件

当前决策是 **no-go for production / go for a future re-evaluation**：T3 主线继续使用
本次已经部署的 TypeScript 6.0.3 常驻 Worker 增量会话，不另立 TypeScript 7 专项路线。
满足以下条件后重新打开评估：

1. TypeScript 7.1 或更新稳定版发布受支持的 programmatic API，并能表达内存 VFS、
   structured diagnostics、custom resolution/transform、incremental builder 与取消。
2. 新 backend 对 Rhino/NodeNext golden、受控 tsconfig、依赖层、Source Map 和错误码
   达到当前 Worker 的行为兼容；差异通过新 revision 显式表达。
3. 建立 Android arm64/x86_64 的离线可重放 supply-chain lock，以及 ABI split、
   executable/JNI 装载、进程隔离和回滚方案。
4. 把 Apache-2.0、`NOTICE.txt` 与全部适用第三方告知纳入 APK 内容门禁。
5. 在四设备矩阵记录 cold/warm、PSS、取消/超时、renderer/process recovery；收益足以
   抵消每 ABI 约 28–29 MiB 的未压缩体积。
