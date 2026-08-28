# npm 真实包兼容矩阵

这页记录 T2-5 使用发布版 npm tarball 得到的可重复结果。矩阵不是根据
`package.json` 形状拼出的模拟用例：仓库固定保存五个运行时包和一个声明包的原始
tarball，门禁先核对 npm SHA-512 integrity，再按生产 T2-1 规则建立只读
`node_modules` 快照，用生产 Worker 与 TypeScript 6.0.3 对 Rhino、Node 两个 profile
分别执行 strict Program 编译。能运行的组合随后实际执行生成代码；不能运行的组合必须
命中公开稳定错误码。

## 结果

| package | 固定版本 | 类型来源 | Rhino profile | Node profile | 边界 / workaround |
|---|---:|---|---|---|---|
| `dayjs` | `1.11.13` | package 自带 `index.d.ts` | 通过；执行 `dayjs.min.js` | 通过；执行 `dayjs.min.js` | 无 |
| `lodash` | `4.17.21` | `@types/lodash@4.17.25` | 通过；执行 `lodash.js` | 通过；执行 `lodash.js` | 运行时包不带声明，必须安装 `@types/lodash`；缺少时 strict 编译稳定报告 TS7016 |
| `zod` | `4.1.5` | package 自带 `.d.cts` | 通过；按 `exports.require` 执行 `index.cjs` | 通过；按 `exports.import` 执行 `index.js` | 无；dual package 会按 profile 选择不同入口 |
| `uuid` | `13.0.0` | package 自带 `dist/index.d.ts` | 编译通过；运行稳定返回 `ERR_AUTOJS6_TYPESCRIPT_RHINO_ESM_ONLY_DEPENDENCY` | 通过；执行 `dist-node/index.js` | 该版本只发布 ESM 运行入口；使用 Node profile |
| `nanoid` | `5.1.5` | package 自带 `index.d.ts` | 编译通过；运行稳定返回 `ERR_AUTOJS6_TYPESCRIPT_RHINO_ESM_ONLY_DEPENDENCY` | 通过；执行 `index.js` | 该版本只发布 ESM 运行入口；使用 Node profile |

`dayjs` 用例返回 `2020-02-03`，`lodash` 返回 `[[1,2],[3]]`，`zod` 返回
`"ok"`，`uuid` 完成 UUID v4 校验，`nanoid` 生成长度为 8 的 ID。矩阵因此同时覆盖
legacy CommonJS、外置 DefinitelyTyped 声明、带 `require`/`import` 双分支的 package，
以及 ESM-only package。

## 真机链路

离线真实 tarball 门禁之外，API 35 设备已覆盖安装当前 AutoJs6、TypeScript 插件与 Ace
插件 APK，并通过三条聚焦 instrumentation 用例：宿主经真实插件 Binder 编译 dayjs/zod
项目（`Time: 11.141`），同一冻结类型层在 Rhino/Node profile 的 `typesVersions` 与
installed `@types` 解析结果一致（`Time: 34.08`），Ace Android bridge 发布冻结依赖类型
（`Time: 0.403`）。三条结果均为 `OK (1 test)`；覆盖安装未清除应用数据。

## 项目写法

依赖仍由项目开发环境安装；Android 侧不会运行 npm、pnpm 或 lifecycle script。一个
同时使用 lodash 与 zod 的项目可以直接写：

```powershell
npm install lodash zod
npm install --save-dev @types/lodash
```

```ts
import lodash from "lodash";
import { z } from "zod";

const chunks: number[][] = lodash.chunk([1, 2, 3], 2);
const label: string = z.string().parse("ok");
console.log(chunks, label);
```

编译器会优先使用 package 自带的 `types`/`typings`、`exports.types` 与
`typesVersions`。如果裸包根入口最终只解析到 JavaScript，resolver policy revision 4
会保留 revision 3 引入的 installed-`@types` 回退：
在返回运行时 JS 前尝试同一冻结依赖层中的 `@types/<package>`；scoped package 对应
DefinitelyTyped 的 `@types/<scope>__<name>`。这正是 lodash 能在 strict 模式通过的路径，
不会让外置 `@types` 覆盖 package 自带声明。

选择 profile 时遵循运行入口而不是声明格式：

- 有 CommonJS `main`、`exports.require`、`index.cjs` 或非 `type: module` 的 `.js`
  入口时，可使用 Rhino。
- 只有 ESM 入口时使用 Node。Rhino 不会在背后把依赖重新转译，而会返回
  `ERR_AUTOJS6_TYPESCRIPT_RHINO_ESM_ONLY_DEPENDENCY`。
- Node profile 仍需要兼容的 AutoJs6 Node.js Runtime 插件。
- `.node`、node-gyp 和依赖安装脚本属于 T2-6 的原生模块边界，会返回公开稳定错误；详见
  [原生 npm 依赖](NATIVE_DEPENDENCIES.md)。

## 离线复验与固定输入

所有输入都位于
`app/src/test/resources/t2-real-package-matrix/`。`t2-5-v1.json` 固定 package 名称、
版本、MIT license、tarball 文件名、npm integrity、最小脚本、预期值、profile 边界和
workaround。验证命令不访问 registry：

```powershell
.\gradlew.bat :app:verifyT2RealPackageMatrix --offline
```

门禁还锁定 T2-1 配额：本轮过滤后是 2,749 个 UTF-8 文件、5,810,879 bytes 内容与
136,057 path bytes，均在 8,192 files / 64 MiB / 1.5 MiB path bytes 上限内。当前
dependency resolver policy identity 是 revision `4`、fingerprint
`d8207539237d2a08a6b97b530c5e6215f4b73311fdd6954ce9f8004717ebd635`。本矩阵最初关闭
T2-5 时使用 revision `3`、fingerprint
`6241375f2ea49ac2c2d2b3b79b28b7c1ee4adde75c989defc61e252c89ced2bd`；T2-6 只在其上新增
原生/安装生命周期边界，不改变五个纯 JavaScript release 的结果。
