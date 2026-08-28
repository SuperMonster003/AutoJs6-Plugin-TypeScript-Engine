# TypeScript 错误码目录

本文覆盖 `v0.6.0` 的公开稳定错误身份。错误文本用于帮助人阅读，可能在后续版本改写；
自动化工具应依赖 `errorCode`、diagnostic `code` 或配置 detail code，不能解析英文/中文
message。

一次失败可能同时出现两个层级：例如终态是
`ERR_AUTOJS6_TYPESCRIPT_COMPILATION_DIAGNOSTICS`，而 `diagnostics.json` 中还有标准
TypeScript 数字码、`NODE_RESOLUTION_*` 对应的数字码，或 `CONFIG_*` 配置详情。先处理
最具体的那一层。

## 宿主/运行时错误

| 稳定错误码 | 何时触发 | 用户侧处理 |
|---|---|---|
| `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED` | 原始 TypeScript 到达 Rhino/Node 执行层，但宿主没有提供已编译模块；运行时禁止用 stripping 兜底 | 安装/启用与宿主版本匹配的 TypeScript Engine Plugin；确认插件 capability mask 为 15；不要把 `.ts` 直接交给旧宿主或旧 Node Runtime |
| `ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND` | 运行中的 `require()`/`import()` 指向执行开始时未进入 snapshot，或没有生成模块映射的 TypeScript 文件 | 在点击运行前把目标文件放入项目；检查相对路径、扩展名和大小写；运行中临时下载/生成的 TS 当前不支持 |
| `ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_AMBIGUOUS` | 一个运行时 specifier 可对应多个 snapshot/生成模块，宿主拒绝猜测 | 消除仅大小写不同或重复 basename/路径后缀的文件；改用唯一、明确的相对路径 |
| `ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION` | Node 项目使用 `.tsx`，或入口/动态目标是当前 profile 不接受的 TypeScript 扩展名 | TSX 改用 Rhino `.tsx`；Node 使用 `.ts/.mts/.cts`，并按需要设置根 `package.json.type` |
| `ERR_AUTOJS6_TYPESCRIPT_RUNTIME_DEPENDENCY_CHANGED` | `node_modules` 文件在编译完成后到运行时建立前发生路径、文件类型、size、SHA-256 或 UTF-8 内容变化 | 重新运行以建立新编译快照；不要在同一次启动的编译与执行之间改写或重新安装依赖 |
| `ERR_AUTOJS6_TYPESCRIPT_RHINO_ESM_ONLY_DEPENDENCY` | Rhino 请求的包只提供 ESM/import 入口，或选中 `type: module` 下的 `.js` / `.mjs` / `.mts` | 将项目切换到 Node profile，或安装/选择该包明确提供的 CommonJS `require` 版本 |
| `ERR_AUTOJS6_TYPESCRIPT_RHINO_DEPENDENCY_ENTRY_UNSUPPORTED` | Rhino 包的 CommonJS target 不在精确依赖快照中，或 target 后缀不能由 Rhino CommonJS loader 执行 | 检查 package 的 `exports.require`/`main` 和实际安装文件；使用 `.cjs`、CommonJS `.js` 或 `.json` 入口 |
| `ERR_AUTOJS6_TYPESCRIPT_RHINO_DEPENDENCY_PACKAGE_INVALID` | Rhino 读取到非 object `package.json`、非法 exports target，或 target 试图越出 package 根 | 修复/重装该依赖；确保 target 是包内规范相对路径且 package metadata 为合法 JSON object |
| `ERR_AUTOJS6_TYPESCRIPT_RHINO_DEPENDENCY_PATH_NOT_EXPORTED` | Rhino 请求的 subpath 未导出、被 `null` 阻断，或 exports 没有 `require`/`node`/`default` 可用分支 | 改用 package 已公开的 CommonJS subpath，或使用 Node profile 执行 import-only 分支 |
| `ERR_AUTOJS6_TYPESCRIPT_NATIVE_DEPENDENCY_UNSUPPORTED` | dependency 含 `.node`、`binding.gyp` 或顶层 `gypfile: true`；桌面 Node addon 不能作为 Android TypeScript 依赖加载 | 换用 pure JavaScript/TypeScript、无需 install 生成资产的 WASM package，或使用专门的 Android/AutoJs6 插件；详见[原生 npm 依赖](NATIVE_DEPENDENCIES.md) |
| `ERR_AUTOJS6_TYPESCRIPT_DEPENDENCY_INSTALL_SCRIPT_UNSUPPORTED` | dependency `package.json.scripts` 含 `preinstall`、`install` 或 `postinstall`；Android snapshot 不执行 npm lifecycle | 换用发布包已包含全部资产的 pure/WASM 版本；不要删除脚本字段后继续依赖其生成物 |

## 编译插件终态错误

这些值来自冻结的 Binder v1 契约，宿主也会原样识别。

| 稳定错误码 | 何时触发 | 用户侧处理 |
|---|---|---|
| `ERR_AUTOJS6_TYPESCRIPT_INVALID_REQUEST` | 请求缺字段、字段类型错误、入口/profile/mode 不合法，或 Bundle/manifest 不满足公开协议 | 普通用户先升级宿主与插件；API 调用方核对 request ID、entry URI、profile authority、archive schema 和 primitive-only Bundle |
| `ERR_AUTOJS6_TYPESCRIPT_CONTRACT_MISMATCH` | 宿主与插件的 contract version/hash 或附加 profile identity 不兼容 | 安装配套版本，不要混装来自不同构建/签名渠道的宿主、API AAR 和插件 |
| `ERR_AUTOJS6_TYPESCRIPT_COMPILER_BUSY` | 全局编译槽已有任务，或重复 job token/request 尚未结束 | 等待当前任务完成后重试；不要并发提交同一 token；若长期不释放，重启宿主并保留日志 |
| `ERR_AUTOJS6_TYPESCRIPT_COMPILATION_CANCELLED` | 用户、宿主生命周期或显式 cancel 终止任务 | 若是主动取消无需处理；否则检查后台任务/Activity 是否提前销毁并重试 |
| `ERR_AUTOJS6_TYPESCRIPT_COMPILATION_DIAGNOSTICS` | TypeScript、受控配置或 Node 解析产生 error，`noEmitOnError` 阻止输出 | 打开 `diagnostics.json` 或宿主诊断面板，优先修复带文件位置和下文 detail code 的记录 |
| `ERR_AUTOJS6_TYPESCRIPT_COMPILATION_TIMEOUT` | 任务超过请求 deadline（默认 30 秒，公开最大 120 秒） | 修复无限/异常复杂类型，缩小项目 snapshot；合理提高宿主项目 timeout，但不要超过 120 秒 |
| `ERR_AUTOJS6_TYPESCRIPT_COMPILER_UNAVAILABLE` | compiler backend 未就绪、WebView/Worker 启动失败、编译进程死亡或健康检查失败 | 确认 Android API 24+、Chrome/WebView 119+ 和插件版本；重试一次，仍失败则收集 WebView 版本及 compiler 日志 |
| `ERR_AUTOJS6_TYPESCRIPT_OUTPUT_TRANSPORT` | 编译结果无法写入/读取输出 FD，或 formal output transport 在提交阶段损坏 | 检查存储/进程异常和可用空间，重试；持续复现时连同 request ID、输出阶段日志提交问题 |
| `ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_FORMAT` | 输入/输出不是受支持的 ZIP transport，条目方法、容器结构或 schema 文件不符合格式 | 不要手工拼接中间 ZIP；升级宿主/插件；API 调用方按 archive schema v1 重新生成 |
| `ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_INTEGRITY` | 声明的长度/SHA-256 与实际 archive、manifest 或 entry 不一致，或内容在传输中被截断 | 重新生成并完整写入 FD；不要复用已关闭/已移动游标的 descriptor |
| `ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_PATH` | ZIP 路径不是规范 UTF-8 项目相对路径，包含绝对路径、`..`、控制字符、反斜杠或冲突条目 | 统一使用 `/`、相对路径和一致大小写；移除路径穿越、空 segment 与重复规范化路径 |
| `ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_QUOTA` | 输入、输出、诊断或 archive 容器超过公开/普通编译额度 | 对照[配置与配额参考](CONFIGURATION.md)缩小文件数/字节数；不要把 `node_modules`、生成物或无关资源放进 snapshot |
| `ERR_AUTOJS6_TYPESCRIPT_R0_NOT_AVAILABLE` | 旧 R0 单文件/旧 profile 调用进入已淘汰的兼容门禁 | 升级到使用项目编译 capability 的当前宿主；新代码不要调用 R0 路径 |

## 受控 tsconfig detail code

配置 detail code 的 message 非契约。宿主 UI 可能先显示通用 compilation diagnostics；
结构化消费者若拿到 detail 字段，应按下表处理。

| 稳定 detail code | 何时触发 | 修复方式 |
|---|---|---|
| `CONFIG_ENCODING_INVALID` | `tsconfig.json` 不是合法 UTF-8 | 以 UTF-8 重存；UTF-8 BOM 本身允许 |
| `CONFIG_SYNTAX_INVALID` | JSONC 语法错误、未闭合注释/字符串等 | 修正语法；注释和尾随逗号允许，但仍必须是 TypeScript 6.0.3 可解析的 JSONC |
| `CONFIG_DUPLICATE_KEY` | 同一 object 出现重复键 | 只保留一个键；不要依赖“最后一个值获胜” |
| `CONFIG_UNKNOWN_PROPERTY` | 显式禁止的自定义 transformer 字段等未知结构 | 删除 `customTransformer`、`customTransformers`、`transformers`；公开编译不执行项目代码插件 |
| `CONFIG_INVALID_VALUE` | 已支持键的类型/值错误，选项组合矛盾，或 `target`/`lib` 超出 profile 白名单 | 布尔项只填 `true/false`；Rhino 使用 `ES2018`，Node 使用 `ES2018`–`ES2022`；例如启用 `exactOptionalPropertyTypes` 时保持 `strictNullChecks=true` |
| `CONFIG_UNSUPPORTED_FEATURE` | `extends`、`references`、`outFile`、compiler plugin 等仍会越过受控边界的功能 | 删除该键；module/output topology 继续由 profile 管理。项目内别名请使用 revision 4 的受控 `baseUrl`/`paths` |
| `CONFIG_PROFILE_CONFLICT` | 项目尝试覆盖 `module`、`moduleResolution`、`outDir`、`sourceMap` 等 profile 固定项 | 删除覆盖；`target` 只能使用 revision 3+ 为当前 profile 开放的范围，module kind 仍通过扩展名/project/package boundary 选择 |
| `CONFIG_PATH_INVALID` | `files/include/exclude` selector，或 revision 4 的 `baseUrl`/`paths` key/target 非规范、越出项目、wildcard 数不匹配、包含无效字符或超长 | selector 改成 NFC 项目相对路径/受支持 glob；alias 使用安全 ASCII bare key 与项目相对 target，统一 `/`，不要写绝对路径、`..`、反斜杠、query/fragment |
| `CONFIG_COMPLEXITY_EXCEEDED` | 配置超过 48 KiB、512 selectors、单 selector/pattern 4096 UTF-8 bytes、128 aliases、每 alias 8 targets、合计 512 targets、深度 16 或 4096 nodes | 拆掉生成式/巨型配置，缩短选择器/pattern，并合并重复 alias target |
| `CONFIG_NO_INPUTS` | `files/include/exclude` 计算后没有可编译输入 | 检查 glob、大小写和 exclude；省略 include 时默认 `**/*` |
| `CONFIG_ENTRY_NOT_SELECTED` | `project.json.main` 存在，但被最终选择集合排除 | 把入口加入 `files`/`include`，或停止用 `exclude` 过滤它 |

以下两项是 warning diagnostic，不会单独让 emit 失败：

| 数字 diagnostic code | 含义 | 处理 |
|---:|---|---|
| `910001` | 未分类的顶层 tsconfig 键被忽略 | 删除该键或确认可以接受其完全不生效 |
| `910002` | revision 4 的 20 个布尔项及 `target`/`lib`/`baseUrl`/`paths` 以外、且不属于结构性拒绝集合的 compiler option 被忽略；revision 3 没有别名项，legacy revision 2 仍是原 18 项 | 不要依赖该选项；如确有用户需求，提供最小项目供后续 revision 评估 |

逐项 ignored warning 最多 64 条，更多项会被一条汇总记录替代。

## Node snapshot 解析 diagnostics

下表的符号名与数字 diagnostic code 是固定配对，category 为 error、phase 为
`resolution`。出现任一项都会跳过 emit，并使终态成为 compilation diagnostics。

| 稳定解析身份 | 数字 code | 触发条件与处理 |
|---|---:|---|
| `NODE_RESOLUTION_INVALID_CONTAINING_PATH` | `900001` | 发起 import 的文件路径不在规范 snapshot 域；修正 archive/project 路径 |
| `NODE_RESOLUTION_INVALID_SPECIFIER` | `900002` | specifier 为空、非规范、包含无效编码/路径；改用合法相对路径、`#imports` 或已授权模块名 |
| `NODE_RESOLUTION_INVALID_CONDITION` | `900003` | package condition 非法或不在受控 condition 集；删除自定义 condition |
| `NODE_RESOLUTION_AMBIGUOUS_CASE` | `900004` | 大小写折叠后对应多个文件；统一命名和 import 大小写 |
| `NODE_RESOLUTION_PACKAGE_NOT_ALLOWED` | `900005` | caller 尝试解析未授权 npm/裸包；移除依赖，当前只允许宿主 bridge 模块和受控 snapshot 路径 |
| `NODE_RESOLUTION_PACKAGE_METADATA_MISSING` | `900006` | `.ts` 或 package 解析需要根 metadata，但 snapshot 中没有可用 `package.json`；补充最小根文件 |
| `NODE_RESOLUTION_PACKAGE_METADATA_INVALID` | `900007` | `package.json` 语法、`type` 或受控结构无效；只保留合法 `module/commonjs` 类型和允许字段 |
| `NODE_RESOLUTION_IMPORTS_NOT_DEFINED` | `900008` | 使用 `#...` specifier，但根 package 没有对应 imports authority；改用相对路径或声明受控映射 |
| `NODE_RESOLUTION_IMPORTS_NO_MATCH` | `900009` | imports 存在但没有匹配 key/pattern；修正 specifier 或映射 |
| `NODE_RESOLUTION_EXPORTS_NOT_DEFINED` | `900010` | 请求 package subpath，但该受控 package 没有 exports authority；使用公开入口 |
| `NODE_RESOLUTION_EXPORTS_NO_MATCH` | `900011` | exports 没有匹配所请求 subpath；改用已公开 subpath |
| `NODE_RESOLUTION_CONDITION_NO_MATCH` | `900012` | 映射存在但没有当前 ESM/CJS condition 分支；补充受控分支或改用匹配入口 |
| `NODE_RESOLUTION_INVALID_TARGET` | `900013` | imports/exports target 不是受支持字符串、对象/数组结构或规范相对 target；修正 metadata |
| `NODE_RESOLUTION_TARGET_BLOCKED` | `900014` | target 被显式设为 `null`/阻断；不要导入该私有 subpath |
| `NODE_RESOLUTION_TARGET_ESCAPE` | `900015` | target 试图用绝对路径、`..` 等离开受控 package/snapshot；改成包内目标 |
| `NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT` | `900016` | 合法 target 指向的文件未在执行开始时进入 snapshot；在运行前加入目标文件 |
| `NODE_RESOLUTION_UNSUPPORTED_SOURCE_KIND` | `900017` | target 后缀不是当前 Node revision 2 可编译/可加载的 source kind；改用受支持扩展名 |
| `NODE_RESOLUTION_CYCLE` | `900018` | imports/exports target 重定向形成解析循环；打断 metadata 映射环。它不是普通相对模块运行循环的通用报错 |

## 标准 TypeScript diagnostics

其他数字码（例如 `2304`、`2322`、`2307`）沿用锁定的 TypeScript `6.0.3`
Compiler API。range 的 offset/length 从 0 开始并按 UTF-16 code unit 计数；面向用户的
line/column 从 1 开始。category 数字为 warning `0`、error `1`、suggestion `2`、
message `3`。

配额与选项分类见[配置与配额参考](CONFIGURATION.md)，运行错误的映射精度见
[StackTrace 与 Source Map 契约](SOURCE_MAPS.md)。
