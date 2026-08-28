# T2-1 独立只读依赖层

## 结论

T2-1 选择 Roadmap 中的策略 (a)：宿主把 `node_modules/` 内与编译相关的文本资产按
明确排除规则完整打包，并在同一个 stored ZIP / PFD 中使用独立的
`dependencies/` namespace。依赖层有自己的 schema、policy、配额、inventory 与
fingerprint，不占用 4096 files / 64 MiB 的 ordinary caller 配额，也不修改冻结的
Binder 方法。

策略 (b) 的静态 import 可达性裁剪暂不采用：仅扫描调用方源码无法可靠覆盖 package
`exports` 条件、类型入口、re-export、动态解析和嵌套依赖。策略 (c) 的跨请求独立传输
可在 T3 增量缓存阶段继续演进；T2-1 先保留独立语义身份和私有 spool，为以后拆分物理
归档留出兼容路径。

本节记录 T2-1 完成时的阶段边界：当时只建立可信输入、缓存和只读存储链路，bare
specifier、`exports` 条件解析与 Worker `/node_modules/` 投影刻意留给 T2-2。当前源码已由
[T2-2 bare specifier 与依赖解析](DEPENDENCY_RESOLUTION_T2_2.md) 激活 framed read-only
投影，并由 [T2-3 依赖运行时装载](DEPENDENCY_RUNTIME_LOADING_T2_3.md) 将同一 exact layer
交给 Rhino/Node runtime。T2-6 保留 schema revision `1`，但为明确拒绝 `.node`、
`binding.gyp`、`gypfile` 与安装 lifecycle，把当前 policy append-only 升为 revision `2`、
fingerprint
`c159ae179de24307c456c69ae4e456ed9d7dba71dc965c22e6114f632af3834a`；见
[T2-6 原生依赖边界](NATIVE_DEPENDENCY_BOUNDARY_T2_6.md)。下表保留 T2-1 完成时身份。

## 协议身份

| 项目 | revision / 值 |
|---|---|
| schema | `autojs6.typescript.dependency-layer`, revision `1` |
| policy | revision `1` |
| policy fingerprint | `7564a07d9f9e980dab795312b1dfbfe719775fb3392536149efd558dc70fa68b` |
| manifest conditional key | `dependencyLayer` |
| package archive prefix | `dependencies/node_modules/` |
| lockfile archive prefix | `dependencies/lockfiles/` |
| future Worker VFS prefix | `/node_modules/` |
| T2-1 frozen read mode | `immutable-file-backed-not-framed-until-t2-2` |
| T2-2 active resolver read mode | `immutable-file-backed-framed-readonly-t2-2` |

current Rhino 与 Node revision 2 metadata 广告 schema、policy revision 与 policy
fingerprint。宿主只在三个值完整且与当前实现一致时启用依赖层；请求 Bundle 另外携带
dependency fingerprint 与 inventory fingerprint。legacy Rhino revision 1 和旧 Node
authority 不会把缺失字段解释成 revision 1。

## 物理归档与所有权

归档逻辑上包含三个互不混算的区域：

```text
request-manifest.json
sources/...                         ordinary caller inventory
package.json / packages/...         ordinary caller metadata
tsconfig.json                       ordinary caller config
dependencies/lockfiles/...          dependency identity
dependencies/node_modules/...       independent dependency inventory
```

服务端先对 ZIP 中每一条依赖记录验证 canonical path、strict UTF-8、size 与 SHA-256，
再写入插件私有不可变 spool。`R2ProjectSnapshot` 为依赖层提供独立的 forward-only
read-only cursor；依赖文件不出现在 `filePaths`、ordinary admission capacity 或当前
Worker frame 中。heap 测试适配器和生产 file-backed 适配器使用同一个 inventory
契约；关闭 snapshot 后游标不可再创建，heap 副本会清零。

T2-1 完成时，`node_modules` 中的 `package.json.scripts` 全部被当作普通、不可执行的
JSON 字节接受。T2-6 当前规则继续允许 inert `build`/`test` 等普通键，但
`preinstall`/`install`/`postinstall` 会在归档阶段明确失败；任何脚本都不会执行。

## 文件选择

允许的文本后缀：

- `.cjs`, `.cts`, `.js`, `.json`, `.jsx`, `.mjs`, `.mts`, `.ts`, `.tsx`

排除的文件后缀：

- `.map`, `.markdown`, `.md`

路径任一 segment 命中下列目录名时排除：

- `.bin`, `.git`, `.github`
- `bench`, `benchmark`, `benchmarks`
- `coverage`, `doc`, `docs`
- `example`, `examples`, `test`, `tests`

嵌套 `node_modules` 被保留，以免破坏 npm/pnpm 的真实依赖图。符号链接遍历、非 canonical
路径、ASCII case-fold collision、非 UTF-8 内容和未列出的二进制资产会在打包或服务端
验证阶段拒绝。

## 独立配额

| 维度 | 依赖层上限 |
|---|---:|
| 文件数（含最多一个身份 lockfile） | 8192 |
| 单文件 | 8 MiB |
| 文件内容合计 | 64 MiB |
| lockfile | 8 MiB |
| canonical path UTF-8 bytes 合计 | 1.5 MiB |

带依赖层的整个输入归档使用加法 envelope：12289 entries、140 MiB ZIP container、
132 MiB expanded bytes。没有 `node_modules/` 的请求继续使用原来的 4097 entries、65 MiB
container、64.5 MiB expanded envelope。两种 envelope 都不改变 ordinary caller 的
4096 files / 64 MiB 语义配额。

## 身份与缓存

依赖身份按固定优先级选择：

1. 根 `package-lock.json`：identity kind `package-lock`；
2. 根 `pnpm-lock.yaml`：identity kind `pnpm-lock`；
3. 无 lockfile：identity kind `content`，identity fingerprint 等于 exact inventory
   fingerprint。

有 lockfile 时也始终计算并绑定实际文件 inventory。这样 lockfile 没更新但
`node_modules` 字节改变时，缓存仍会失效；反过来，仅 lockfile 改变也会失效。宿主
cache-key format revision `3` 写入 schema/policy identity、lock/content identity、
inventory fingerprint、layer fingerprint、精确文件路径/size/SHA-256 和总量。

## 验证面

- `R2DependencyLayerDecisionFixtureTest` 冻结策略、namespace、上限与三包验收身份；
- `R2DependencyLayerTest` 覆盖 fingerprint、inert scripts、只读游标、排除规则与
  case-fold collision；
- `R2ProjectArchiveTest` 覆盖 ZIP/Binder control-plane 对账、私有 spool 可读性以及
  caller capacity 不受影响；
- `R2FileBackedProjectSnapshotTest` 覆盖生产 file-backed 独立游标与 immutable digest；
- AutoJs6 的 `TypeScriptProjectArchiveTest` 和 `TypeScriptCompilationCacheKeyTest` 覆盖
  三直接依赖打包、lock/content 漂移与缓存失效；
- AutoJs6 真机测试
  `dependencyLayerWithThreeDirectPackagesCompilesThroughRealPluginBinder` 覆盖
  dayjs/lodash/zod 形状项目从宿主归档、Binder、插件验证、私有 spool 到真实编译器的
  完整 T2-1 链路，并在仅修改一个 dependency 文件后再次编译，断言两轮均为
  `CACHE_MISS` 且均进入真实编译器。bare imports 刻意留给 T2-2。

2026-08-27 在 Android 15 / API 35 小米 23046RP50C（serial `968e9f18`）覆盖安装本轮
TypeScript debug APK、AutoJs6 `6.8.0 (5276)` arm64 debug APK 与 androidTest APK 后，
上述真机用例返回 `Time: 18.052`、`OK (1 test)`、`INSTRUMENTATION_CODE: -1`。测试项目
具有 dayjs `1.11.13`、lodash `4.17.21`、zod `4.1.5` 三个直接 dependency 目录，
每包同时包含 JS、`.d.ts`、带 `postinstall` 的 package metadata 以及应排除的 docs/tests；
入口不使用 bare specifier，以保持 T2-1/T2-2 的阶段边界。首轮完成后测试只把
`node_modules/dayjs/index.js` 从 `value = 0` 改为 `value = 42`，再次通过同一 client
编译；两轮生成的入口 JS 相同，但性能快照均为 `CACHE_MISS` 且 compiler elapsed 非空，
直接证明依赖层精确 inventory/content 已进入宿主缓存 key。

机器可重复的聚合入口为 `:app:verifyT2DependencyLayer`。
