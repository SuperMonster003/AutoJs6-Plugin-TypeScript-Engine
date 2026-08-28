# T2-2 bare specifier 与依赖解析

> 历史身份说明：本文记录 T2-2 完成时的 resolver policy revision `1`。T2-4 以
> append-only revision `2` 增加 package declarations、installed `@types` 与
> `typesVersions` 的 Program/Ace 同步；T2-5 又以 revision `3`（fingerprint
> `6241375f2ea49ac2c2d2b3b79b28b7c1ee4adde75c989defc61e252c89ced2bd`）修正 runtime
> JavaScript 根入口到 installed `@types` 的模块类型回退。T2-6 当前 identity 为 revision
> `4`、fingerprint
> `d8207539237d2a08a6b97b530c5e6215f4b73311fdd6954ce9f8004717ebd635`，新增绑定原生 addon
> 与安装 lifecycle 明确拒绝边界。T2-2 的原始
> revision/fingerprint 继续保留为历史验收身份；当前规则见
> [T2-4 依赖类型与编辑器同步](DEPENDENCY_TYPES_EDITOR_T2_4.md)和
> [npm 真实包兼容矩阵](../user/REAL_PACKAGE_MATRIX.md)和
> [T2-6 原生依赖边界](NATIVE_DEPENDENCY_BOUNDARY_T2_6.md)。

## 结论

T2-2 已把 T2-1 冻结的依赖 archive/spool 接入编译器：项目在普通源码、声明与受控 package
target 全部传输完成后，再把精确依赖 inventory 以同一套有界 ACK frame 投影到 Worker 的
只读 `/node_modules/`。该能力最初只面向 Node revision 2；T2-3 在不改变 resolver identity
的前提下把同一传输与类型解析开放给 current Rhino。TypeScript 6.0.3 resolver 负责 bare
specifier、scoped package、子路径和 `exports` 条件选择；既有
`NODE_RESOLUTION_*` 层负责把未命中转换为稳定诊断，不会退回 WebView、Android 文件系统
或网络。

本阶段的原始验收只覆盖编译期模块与类型入口解析；policy 中
`runtimeLoading=deferred-t2-3` 是该冻结阶段身份的一部分。运行期能力现已由
[T2-3 依赖运行时装载](DEPENDENCY_RUNTIME_LOADING_T2_3.md) 完成，不回写 T2-2 fingerprint。

## 协议身份

| 项目 | 值 |
|---|---|
| dependency resolver policy revision | `1` |
| policy fingerprint | `e4bb5345bcf6c38f37e7feef94d5eed42f2ce1b5e88a0e3ff5dcf315a3c3872d` |
| package VFS root | `/node_modules/` |
| lockfile VFS root | `/dependency-lockfiles/` |
| read mode | `immutable-file-backed-framed-readonly-t2-2` |
| condition authority | `types`, `import`, `require`, `node`, `default` |
| legacy entry fallback | `types`, `typings`, `module`, `main`, `index` |
| runtime loading | deferred to `T2-3` |

resolver identity 与 T2-1 dependency-layer identity 分离：前者改变解析行为时会使宿主能力
协商与编译缓存失效，后者继续精确绑定 lock/content identity 和每个依赖文件的
path/size/SHA-256。T2-1 的 policy fingerprint 没有被重写。

## 传输与 VFS

起始帧在存在依赖层时新增 13 个精确字段：dependency schema/policy、identity、inventory、
layer fingerprint、独立 file/byte/path 计数，以及 resolver revision/fingerprint。没有依赖层
的旧 30/40/47-field Node revision 2 起始帧保持原形。

文件顺序固定为：

1. 受控 package metadata/targets；
2. 普通 `/sources` 与 `/declarations` inventory；
3. `dependencies/lockfiles/*` → `/dependency-lockfiles/*`；
4. `dependencies/node_modules/*` → `/node_modules/*`。

普通 `projectInputEnd.fileCount/totalBytes` 仍只描述普通 inventory。Worker 另行核对依赖层
file count、内容 bytes、archive path UTF-8 bytes、严格排序、ASCII case-fold collision、
lockfile SHA-256、inventory fingerprint 与 layer fingerprint。依赖源码不参与 caller token
配额；只有解析实际到达的文件才会进入 TypeScript Program。目录索引的额外路径预算只在
起始帧完整通过依赖权威校验后启用，普通项目仍保持原 8 MiB 路径上限。

## 解析语义

生产 resolver 使用 TypeScript 6.0.3 的 `ts.resolveModuleName` 和
`ts.resolveTypeReferenceDirective`，其 host 只能观察冻结 VFS。支持：

- `dayjs` 一类根 bare package；
- `@scope/tool` 一类 scoped package；
- `zod/mini`、`@scope/tool/feature` 一类子路径；
- `exports` 的 `types`、`import`、`require`、`node`、`default` 条件；
- 无 `exports` 根入口的 `types`/`typings`/`module`/`main`/`index` 回退；
- `.d.ts/.d.mts/.d.cts` 以及对应 TS/JS/JSON 文件种类；
- 最近依赖 package 的 `type: module` implied format。

依赖包存在但子路径未导出时返回
`NODE_RESOLUTION_EXPORTS_NO_MATCH`；目标未进入精确 snapshot 时返回
`NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT`；metadata 非法时返回
`NODE_RESOLUTION_PACKAGE_METADATA_INVALID`。包根在依赖层完全不存在时才交给原 workspace /
trusted declaration resolver，以保留 `packages/*`、`node:*` 与 `autojs6:*` 行为；依赖包
一旦存在，其内部 miss 不会静默落到其他文件系统。

## 验收与回归

`verify-t2-bare-specifier-resolution.js` 直接调用生产 resolver，并使用真实 TypeScript
6.0.3 覆盖：

- `import dayjs from "dayjs"` 的 `export =`/`esModuleInterop` 类型入口；
- `import { z } from "zod"` 与 `zod/mini` 的 root/subpath `types` export；
- 上述三条 import 进入同一完整 Program，零 semantic diagnostics，并实际 emit `.mjs`；
- `@scope/tool/feature` 在 import/require 下分别选择 `.d.mts/.d.cts`；
- `module` 优先于 `main` 的 fallback；
- 未导出子路径稳定得到 `NODE_RESOLUTION_EXPORTS_NO_MATCH`；
- 完全未安装的包只回到既有 snapshot resolver，不访问网络或系统文件。

Kotlin 单测另覆盖 archive↔VFS 可逆映射、公开指纹、依赖帧严格后置、普通/依赖独立计数
和 cursor 释放。聚合入口为 `:app:verifyT2BareSpecifierResolution`。该门禁继续只证明
T2-2 编译期语义；T2-3 另以宿主 runtime 单测和双 profile 真机执行验收。

2026-08-27 在 API 35 小米 23046RP50C 上覆盖安装本轮 TypeScript 插件、AutoJs6 debug
与 androidTest APK 后，宿主用例
`bareDayjsAndZodExportsCompileThroughRealPluginBinder` 建立含 dayjs、zod 与 `zod/mini`
exports 的真实项目目录。输入从宿主 archive 经 Binder、插件 spool/framing、Worker
`/node_modules/` VFS 进入 TypeScript 6.0.3 Program，最终返回一份 ESM module、零
diagnostics，并核对 resolver revision/fingerprint。结果为 `Time: 10.457`、
`OK (1 test)`、`INSTRUMENTATION_CODE: -1`。该用例只证明编译期能力，没有执行生成代码。

2026-08-28，current Rhino 也开始携带完整 dependency-layer/resolver authority，并通过同一
Worker `/node_modules/` VFS 完成 dual package 的 `types` 入口解析；随后由 T2-3 Rhino
CommonJS loader 选择该包的 `require` runtime target。
