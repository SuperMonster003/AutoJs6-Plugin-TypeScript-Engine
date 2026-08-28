# T4-2 TypeScript 跨文件智能与定义跳转

> 状态：2026-08-28 已在 AutoJs6 宿主与 Ace Editor 插件完成部署，并在
> Xiaomi 23046RP50C / Android API 35 上通过最终跨 APK 验收。

## 结论

Ace 的 TypeScript 6 Language Service 现在以 T4-1 项目源码 snapshot 和 T2 冻结依赖
类型层组成同一个 Program。completion、hover、signature help 与 definition location
因此都能跨越当前文档，命中项目源码以及 `node_modules` 内获准的声明文件。

定义跳转不是“插件给宿主一个路径”。editor ABI contract 3 新增类型化
`EditorPluginDefinitionTarget`：Ace 只能把 TypeScript 返回的虚拟 URI 重新绑定到当前
不可变项目层中的内容身份，宿主再从本地文件系统独立重建同一份权威并逐项复验。只有两端
身份完全一致时，宿主才会跳转当前文档或打开目标文档并定位。

T4-2 不执行源码写入，不开放 auto-import、codefix 或 project rename；这些仍分别属于
T4-3 与 T4-4。

## 端到端数据流

```text
F12 / Ctrl-or-Command+click / mobile Go to definition
  -> Ace bridge asks the TypeScript Language Service for a definition
  -> TypeScript returns a virtual URI plus a zero-based UTF-16 range
  -> Ace accepts only URIs in the immutable project-source or dependency-type layer
  -> Ace rebinds the URI to root + canonical relative path + SHA-256 + inventory identity
  -> editor ABI contract 3 callback publishes EditorPluginDefinitionTarget
  -> AutoJs6 re-captures the current source or dependency inventory on an IO scheduler
  -> host resolver validates identity, canonical path, bytes, UTF-8, hash, and range
  -> same document: ScriptEditorEngine.jumpTo(line, column)
     another document: EditActivity.editFileAt(name, path, line, column, newTask=false)
```

这条链路保留了明确的所有权边界：TypeScript 决定“语义上定义在哪里”，Ace 证明“该 URI
属于当前冻结 Program”，AutoJs6 决定“该本地文件是否仍是同一份获准内容”，最后仍由宿主
拥有 Activity、文档切换和光标定位。

## 跨文件语义面

| 能力 | 项目源码 | T2 依赖层 | 行为 |
|---|---:|---:|---|
| completion | 是 | 是 | 从同一 Program 返回成员与导出符号 |
| hover | 是 | 是 | 使用目标声明推导展示文本与类型 |
| signature help | 是 | 是 | 使用跨文件函数声明返回参数列表 |
| definition | 是 | 是，仅声明文件 | 返回受约束 URI 与范围，随后进入 contract 3 |

依赖定义目标只接受 `.d.ts`、`.d.mts` 与 `.d.cts`。依赖层中的 JavaScript、JSON、package
metadata 或任意未纳入冻结 inventory 的文件可以参与解析，但不能成为可导航的宿主目标。
项目目标同样只能来自 T4-1 已验证的源码 URI 集合，TypeScript 标准库、AutoJs6 内置声明与
任意未知 URI 都会在 Ace 内被过滤。

## Contract 3 目标

`EditorPluginDefinitionTarget` 携带以下最小权威：

| 字段 | 约束与用途 |
|---|---|
| `kind` | 仅 `PROJECT_SOURCE` 或 `DEPENDENCY_DECLARATION` |
| `projectRootPath` | 当前 snapshot 的绝对 canonical 项目根 |
| `relativePath` | 无空段、`.`、`..`、反斜线或控制字符的 canonical 相对路径 |
| `line/column/endLine/endColumn` | 有上限、零基、顺序有效的 UTF-16 范围 |
| `contentSha256` | 目标文件冻结内容的小写 SHA-256 |
| `projectSourceInventoryFingerprint` | 项目源码目标必须携带，且不得携带依赖 fingerprint |
| `dependencyInventoryFingerprint` | 依赖声明目标必须携带，且不得携带源码 fingerprint |

项目源码目标禁止穿过任意名为 `node_modules` 的路径段。依赖声明目标必须从
`node_modules/<package>/...` 开始并满足声明文件后缀。路径、hash、范围与 fingerprint
均有固定长度或数值上限；ABI 模型不能表达任意 URI、Intent、Activity 或文件描述符。

Ace 在 WebView 边界先限制 JSON payload 大小与数值，再由 `AceLspServerManager` 按精确
URI 查询源码/依赖层保存的 `relativePath + contentSha256`。来自页面的 `authority`、名称或
符号种类不被当作授权依据。

## 宿主独立复验

AutoJs6 的 `TypeScriptEditorDefinitionTargetResolver` 不信任插件已经做过的判断。每次请求
都在后台依次执行：

1. 校验 contract 3 字段组合、路径、hash 与范围上限；
2. 依据当前文档重新捕获 T4-1 项目源码 snapshot，并要求项目根精确相等；
3. 项目源码目标必须同时命中 source inventory fingerprint、相对路径与源码 SHA-256；
4. 依赖目标重新遍历 T2 同规则的有界 `node_modules` inventory，并同时命中 dependency
   fingerprint、声明路径与内容 SHA-256；
5. canonicalize 实际目标，要求它仍在项目根内；依赖目标还必须仍在 canonical
   `node_modules` 根内；
6. 要求请求路径与 canonical 路径完全相等，从而拒绝 symlink traversal；
7. 按源码或依赖单文件预算重新读取 bytes，严格解码 UTF-8，再计算一次 SHA-256；
8. 在实际文本上验证开始和结束行列均存在；
9. 若目标是当前文档，还要求编辑器内存文本的 UTF-8 SHA-256 与目标一致，避免用旧磁盘
   snapshot 在未保存 buffer 中定位。

依赖 inventory 与 T2 使用同一 domain：
`autojs6.typescript.dependency-layer.inventory.v1`。它采用相同的允许后缀、排除目录、lockfile
身份、8192 文件、单文件 8 MiB、总计 64 MiB、lockfile 8 MiB 与路径总计 1.5 MiB 上限，
因此宿主复验不会把 Ace 未曾获准的额外文件提升为定义目标。

解析期间若用户继续编辑、切换文档、切换编辑器引擎或销毁页面，`EditorView` 的文档身份
generation、文本 revision、请求 generation 与 editor identity 任一变化都会丢弃结果；新的
请求也会 dispose 前一项后台工作。解析异常与打开失败均不降级到未经验证的路径或位置。

## 打开与定位

验证后的目标由独立 dispatcher 处理：

- 当前文档调用 `jumpTo(line, column)` 并恢复编辑器焦点；
- 其他项目文件或依赖声明调用 `EditActivity.editFileAt`，传入精确文件名、canonical 路径、
  行、列以及 `newTask=false`；
- 打开失败只返回失败结果，不回退到当前文档、basename 搜索或任意相似路径。

桌面单元测试分别锁定“当前文档精确 jump”“另一文档精确 open + position”以及“打开失败
不执行未验证 fallback”。真实跨 APK 用例从 `main.ts` 中的 `shared('value')` 请求定义，最终
目标严格为 `shared.ts` 的 `(line=0, column=16)`，宿主 resolver 再解析为同一 canonical
文件。

## 兼容性

- `EditorPluginContract.VERSION = 3`，宿主支持范围仍为 `1..3`；旧 contract 1/2 插件继续
  走原有能力，新增 callback 为默认 no-op；
- Ace manifest 与 entrypoint 同步声明 contract 3，并新增 `definitionNavigation` capability；
- Ace 继续要求 AutoJs6 version code 5276；同 version code 但只支持 contract 2 的旧宿主会
  在插件发现阶段拒绝 contract 3，而不会把新插件装载进不兼容 ABI；
- contract 3 未改变 T4-1 project snapshot schema，也未改变 T2 dependency snapshot
  schema；它只新增从不可变层导出受约束导航目标的通道。

## 验收证据

桌面与生成式门禁：

| 门禁 | 结果 |
|---|---|
| AutoJs6 `:plugin-api:editor-api:testDebugUnitTest` | 5 tests，0 failed |
| AutoJs6 `:app:testAppDebugUnitTest` | 1716 tests，0 failed，4 skipped |
| Ace `:app:testDebugUnitTest` | 118 tests，0 failed |
| Ace `:app:verifyAutoJs6LspRuntime` | Rhino 与 Node profile 全部通过 |
| AutoJs6 `:app:assembleAppDebug :app:assembleAppDebugAndroidTest` | BUILD SUCCESSFUL |
| Ace `:app:assembleDebug :app:assembleDebugAndroidTest` | BUILD SUCCESSFUL |

运行时 verifier 在 Rhino 与 Node 两个 profile 中都确认：

- 项目 completion 同时包含 `projectHelper` 与 `sharedAnswer`；
- 项目 hover 解析出 `projectHelper` 及其 `number` 返回类型；
- 项目 signature help 返回 `projectHelper` 的两个参数；
- 项目 definition 分别命中 `src/shared.ts` 与 `src/shared.mts`；
- 依赖 definition 命中 `node_modules/dayjs/ts6/legacy/index.d.ts`；
- dayjs completion 仍为 `format`/`unix`，hover 仍为 `const value: Dayjs`，诊断仍为
  `[2307, 2322]`。

最终 APK 重新构建并安装到唯一指定设备 `968e9f18`：

| 真机门禁 | 结果 |
|---|---|
| 设备 | Xiaomi 23046RP50C，Android API 35 |
| Ace `AceCodeEditorSmokeTest` | `Time: 4.115`，`OK (6 tests)` |
| AutoJs6 `AceEditorPluginHostDeviceTest` | `Time: 4.665`，`OK (2 tests)` |
| 跨 APK 定义结果 | `shared.ts`，`PROJECT_SOURCE`，`(0, 16)`，source fingerprint 匹配 |

宿主设备用例经正式插件发现、签名/ABI 校验、类加载、真实 WebView、contract 3 callback 与
宿主 resolver 完成，不是两端各自的 mock。dispatcher 的打开/定位调用由宿主 JVM 测试锁定，
两组证据共同覆盖 Roadmap 的“打开目标文件并定位”验收。

## 部署提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| AutoJs6 | `3f9b03d2a` | editor ABI contract 3 与定义目标模型 |
| AutoJs6 | `7529be531` | 宿主复验、异步生命周期、打开/定位与跨 APK 测试 |
| AutoJs6 | `010e7f794` | 十语言 changelog 与生成文档 |
| Ace Editor | `eed5d25` | 跨文件 definition、交互入口、权威绑定与 verifier |
| Ace Editor | `d354a05` | 十语言 changelog 与生成文档 |

## 后续边界

- T4-3 可复用同一 Program 读取 TypeScript codefix，但任何 auto-import 都必须先变成宿主可
  审核的当前文档 edit；contract 3 不是写通道。
- T4-4 的跨文件 rename 需要新的多文件预览、内容身份与原子提交协议，不能复用定义目标
  callback 直接写文件。
- T4-5 若缓存宿主复验结果，缓存键必须包含 source/dependency inventory fingerprint、目标
  SHA-256 与文档 identity；命中缓存仍不得绕过当前 buffer 一致性检查。
