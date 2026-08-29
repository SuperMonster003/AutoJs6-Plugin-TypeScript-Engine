# T4-3 TypeScript auto-import 与 quick fix

> 状态：2026-08-29 已在 AutoJs6 宿主与 Ace Editor 插件完成部署，并在
> Xiaomi 23046RP50C / Android API 35 上通过最终跨 APK 验收。

## 结论

Ace 现在直接从内置 TypeScript 6.0.3 Language Service 读取 codefix，并开放两个明确子集：
缺失符号的 auto-import 与已知符号的拼写纠正。用户可以按 Ctrl/Command+.，也可以在移动端
选区工具栏选择“快速修复”；只有一个候选时直接请求授权，多个候选时先在原生对话框中选择。

这不是把 TypeScript 的任意 `CodeFixAction` 交给页面执行。Ace 只接纳当前活动文档中的纯
文本 edit，AutoJs6 再通过 editor ABI contract 4 对当前宿主缓冲区独立重放和授权。模型在
结构上不能表达路径、URI、命令、新文件或第二个文件；宿主不解析 TypeScript 的标题来决定
权限，也不接受插件声称的结果。批准后仍由 Ace 在相同 text revision 上把完整结果作为一次
可撤销修改写回。

T4-3 没有开放跨文件 rename、source action、package 安装、任意 TypeScript command 或
文件系统写入。这些能力不能借用本项的一次性布尔批准扩权。

## 端到端数据流

```text
Ctrl/Command+. / mobile Quick fix / test bridge quickFix(row, column)
  -> current TypeScript Program diagnostics at the cursor
  -> languageService.getCodeFixesAtPosition(...)
  -> admit only fixName=import or fixName=spelling
  -> reject commands, new files, multiple files, and non-current-file changes
  -> normalize sorted UTF-16 edits with bounded payload
  -> JavaScript bridge publishes baseLength + action candidates
  -> Ace Kotlin reparses and replays edits against its exact text mirror
  -> compute base/result SHA-256 and capture mirror revision
  -> editor ABI contract 4 callback publishes one typed active-buffer action
  -> AutoJs6 independently validates and replays against the current host buffer
  -> one-shot Boolean approval
  -> Ace rechecks exact text + revision
  -> replaceAllTextUndoably(resultText, one history token)
  -> normal text-change bridge refreshes diagnostics
```

页面只负责语义候选发现。Ace Kotlin 是 WebView 不可信数据边界，AutoJs6 是最终写入授权边界；
两端校验用途不同，不能因为其中一端已经通过就省略另一端。

## TypeScript codefix 子集

`autojs6_ts_language_service.js` 在光标位置同时查询 syntactic 与 semantic diagnostics，并沿用
既有诊断过滤规则：编辑器合成诊断不会产生 action，未获得完整 project snapshot 时也不会
让单文档不可靠的模块诊断产生 action。每个诊断单独调用 TypeScript
`getCodeFixesAtPosition`，然后只保留下表范围。

| TypeScript action | T4-3 | 约束 |
|---|---:|---|
| `fixName = import` | 接受 | 只能修改当前虚拟文档，不能带 command 或创建文件 |
| `fixName = spelling` | 接受 | 同上，通常为当前符号范围内的单 edit |
| 其他 codefix | 拒绝 | 不按 description、fixId 或相似名称猜测权限 |
| `commands` 非空 | 拒绝 | 不执行 TypeScript command |
| `isNewFile = true` | 拒绝 | 不创建文件 |
| `changes.length != 1` | 拒绝 | 不做多文件事务 |
| change 的 fileName 不是 current file | 拒绝 | 不写 project snapshot 或 dependency VFS |

格式与 import preference 使用当前文档的换行和引号倾向。Rhino profile 保持普通相对
specifier；NodeNext profile 要求 JavaScript 运行时后缀，因此 `.mts` 项目中的 `shared.mts`
会生成 `./shared.mjs`。`includePackageJsonAutoImports` 关闭，Language Service 不从未冻结的
package metadata 扩展候选，也不允许 `allowTextChangesInNewFiles`。

最多发布 16 个候选。相同 kind、title 与 edits 的候选去重；超过上限时停止收集，而不是把
未审查的尾部候选延迟执行。

## Contract 4：只能描述当前缓冲区

`EditorPluginCurrentDocumentCodeAction` 只有以下字段：

| 字段 | 用途 |
|---|---|
| `kind` | 仅 `AUTO_IMPORT` 或 `SPELLING_CORRECTION` |
| `title` | 展示文本，不参与授权决策 |
| `diagnosticCode` | 产生 action 的正整数 TypeScript 诊断码 |
| `baseContentSha256` | Ace 计算 action 时精确活动文本的 SHA-256 |
| `resultContentSha256` | Ace 本地重放 edits 后精确结果文本的 SHA-256 |
| `edits` | 相对 base text 的有序、互不重叠 UTF-16 offset edit |

模型故意没有 `path`、`uri`、`fileName`、file descriptor、Intent、command、fixId、任意 Bundle
或第二文件列表。宿主 callback 名称也明确为
`onCurrentDocumentCodeActionRequested`，因此 contract 4 不能被解释成一般文件写接口。

共享 API 冻结以下预算：

| 项目 | 上限 |
|---|---:|
| title | 512 UTF-8 bytes，非空且无控制字符 |
| diagnostic code | `1..999999` |
| 当前/结果文档 | 1,048,576 UTF-16 code units |
| 单 action edits | 32 |
| 单个 replacement | 65,536 UTF-8 bytes |
| replacement 合计 | 131,072 UTF-8 bytes |
| SHA-256 | 精确 64 个小写十六进制字符，base 与 result 不得相同 |

Ace 的 WebView 入口另设 300,000-character JSON payload 与 16-action 上限。API contract 与 Ace
Kotlin 都要求 edit 按 `startOffset` 严格递增且不得重叠；空区间加空 replacement、越界 offset、
拆分 UTF-16 surrogate pair、孤立 surrogate、空 edit 集合与无效果结果全部拒绝。

## 三层校验

### JavaScript / TypeScript 层

页面先验证 action 类型、file change 数量、current file identity、span 整数和范围、edit 数量、
replacement 总量、排序及重叠。它只发布数据，不直接调用 Ace `session.replace`，也不持有宿主
文件路径写能力。

`baseLength` 是页面到 Kotlin 的低成本时序门禁：若生成 action 后文档长度已经变化，Kotlin
立即拒绝。它不是安全身份；真正身份由下一层从 Kotlin text mirror 计算的 SHA-256 提供。

### Ace Kotlin 层

`AceTypeScriptCodeAction.prepare(baseText)` 从当前 mirror 重新检查全部 API 预算、offset、
surrogate 边界与 UTF-16 完整性，按倒序实际重放 edits，并自行计算 base/result hash。候选
展示或授权等待期间会保存完整 base text 与 mirror revision；任一普通文本变更都会关闭候选
对话框。

请求 host 前以及收到布尔结果后，Ace 都要求：

1. editor 未销毁且不是 read-only；
2. 当前 mirror revision 与捕获值精确相等；
3. 当前 mirror text 与捕获的 base text 逐字符相等；
4. host 明确返回 `true`。

缺少 listener 时默认拒绝。拒绝、过期或 dialog 无法显示只写入内部 event history，不会回退
到直接执行 action。成功路径通过既有 `replaceAllTextUndoably` 写入，因此一次 codefix 只生成
一个 history token，随后恢复编辑器焦点。

### AutoJs6 宿主层

`EditorView` 只为当前仍挂载的 `AceScriptEditorEngine` 配置 callback。每次请求同步验证 editor
未销毁、identity 未切换、engine type 仍为 ACE，并把宿主持有的 `textSnapshot` 与 read-only
状态交给 `EditorCurrentDocumentCodeActionAuthorizer`。

authorizer 不信任 Ace 的预检，独立执行：

1. 重新运行共享 contract 结构与预算校验；
2. 拒绝 read-only、超长或畸形 UTF-16 current text；
3. 对宿主当前文本计算 UTF-8 SHA-256，要求精确等于 base hash；
4. 在宿主文本上验证所有 offset 与 surrogate-pair 边界；
5. 按倒序重放 edits，防止较早 offset 被后续 replacement 移动；
6. 拒绝超长、畸形或与 base 相同的结果；
7. 自行计算结果 SHA-256，要求精确等于 result hash。

畸形 UTF-16 在 base hash 前即被拒绝，避免 UTF-8 encoder 对孤立 surrogate 做 replacement 后让
不同 Java/Kotlin String 共享同一编码身份。宿主只返回一次批准，不接收也不执行来自插件的
脚本、路径或 command。

## 交互入口

- 桌面/硬件键盘：Ctrl+.；macOS key map：Command+.；
- 移动端：有选区且 LSP 可用、文档可写时，selection toolbar 显示本地化“快速修复”；
- 一个合法候选：直接进入宿主授权，满足“一键补 import”；
- 多个合法候选：原生 `AlertDialog` 展示 TypeScript title，用户选中后才请求宿主；
- read-only、无 LSP、无候选、payload 超限或任一校验失败：不发送授权请求；
- 成功后：当前 buffer 以一个 undo group 更新，正常 text-change 流程重新触发诊断。

“快速修复”已加入 Ace 的默认、英语、简体中文、繁体中文（香港/台湾）、阿拉伯语、西班牙语、
法语、日语、韩语与俄语资源，并按资源名排序。

## 兼容性

- `EditorPluginContract.VERSION = 4`，AutoJs6 支持范围为 `1..4`；旧 contract 1/2/3 插件继续
  使用原能力，新增 callback 提供默认拒绝实现；
- Ace manifest 与 entrypoint 同步声明 contract 4，并发布 `codeActions` capability；
- Ace 继续要求 AutoJs6 version code 5276。相同 version code 但只支持 contract 3 的旧宿主会
  在插件发现阶段拒绝新版 Ace，不会以缺少 callback 的类加载状态继续运行；
- Ace 内置 `editor-api.aar` 与 AutoJs6 contract 4 release AAR 的 SHA-256 均为
  `7d495951c6c8ac894bc07548811a109dea7014c015b5952ae89fe98a307319f9`；
- contract 4 不修改 T4-1 project snapshot、T2 dependency snapshot 或 T4-2 definition target
  schema，只追加一个 active-buffer-only 的授权请求。

## 验收证据

桌面与生成式门禁：

| 门禁 | 结果 |
|---|---|
| AutoJs6 `:plugin-api:editor-api:testDebugUnitTest` | 8 tests，0 failed |
| AutoJs6 `:app:testAppDebugUnitTest` | 1720 tests，0 failed，4 skipped |
| Ace `:app:testDebugUnitTest` | 122 tests，0 failed |
| Ace `:app:verifyAutoJs6LspRuntime` | TypeScript 6.0.3，Rhino/Node 全部通过 |
| AutoJs6 `:app:compileAppDebugAndroidTestKotlin` | BUILD SUCCESSFUL |
| Ace `:app:compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL |
| AutoJs6 `:app:assembleAppDebug :app:assembleAppDebugAndroidTest` | BUILD SUCCESSFUL |
| Ace `:app:assembleDebug :app:assembleDebugAndroidTest` | BUILD SUCCESSFUL |

runtime verifier 在 Rhino 与 Node 两个 profile 都从未导入的 `sharedAnswer` 取得
`AUTO_IMPORT / TS2304` action，并分别生成：

- Rhino：`Add import from "./shared"`；
- NodeNext：`Add import from "./shared.mjs"`；
- spelling：`Change spelling to 'correctlySpelled'`，结果把 `correctlySpeld` 改为
  `correctlySpelled`。

每个 action 的 edit 都被实际重放并检查只落在当前文档范围。既有 dependency-type 测试的
diagnostics 仍严格为 `[2307, 2322]`，dayjs completion/hover、项目与依赖 definition、双
profile options 也保持原 golden。

最终 APK 只安装并运行于显式指定的 `968e9f18`：

| 真机门禁 | 结果 |
|---|---|
| 设备 | Xiaomi 23046RP50C，Android API 35 |
| Ace `AceCodeEditorSmokeTest` | `Time: 4.25`，`OK (6 tests)` |
| AutoJs6 `AceEditorPluginHostDeviceTest` | `Time: 4.442`，`OK (2 tests)` |
| action 前 diagnostics | `[2304, 2307]` |
| contract 4 请求 | `AUTO_IMPORT`，diagnostic `2304` |
| 宿主批准后 | 首行 import 包含 `projectMagicAnswer`，diagnostics 严格为 `[2307]` |

Ace smoke 在真实 WebView 中生成 action、经 callback 批准、等待 undoable text change，并再次
调用 Language Service 验证 TS2304 消失。宿主用例则经正式插件发现、签名/ABI 校验、独立
class loader、contract 4 callback、host authorizer 与真实 WebView 完成同一路径；批准不是
测试直接返回常量。该用例还保留 T4-2 的 `shared.ts (0, 16)` 定义目标验证，证明 codefix
接入没有破坏跨文件 definition。

设备列表中虽然另有三台在线设备，但所有安装、属性读取与 instrumentation 命令都显式使用
`adb -s 968e9f18`，没有访问或修改其他设备。

## 部署提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| AutoJs6 | `675182e9e` | editor ABI contract 4、类型化 action/edit 与共享预算 |
| AutoJs6 | `a60abfc5e` | 当前缓冲区独立重放、生命周期绑定与一次性授权 |
| AutoJs6 | `f5219238f` | 畸形 UTF-16 哈希身份加固与回归测试 |
| AutoJs6 | `5d70e3c94` | 真实宿主跨 APK auto-import 验收 |
| AutoJs6 | `c480fbfec` | 十语言 changelog 与生成文档 |
| Ace Editor | `7207168` | codefix 子集、UI、双层校验、contract 4 与 runtime verifier |
| Ace Editor | `47445a3` | 真实 WebView approved auto-import smoke |
| Ace Editor | `1f8d4ec` | 十语言 changelog 与生成文档 |

## 后续边界

- T4-4 的跨文件 rename 需要多文件预览、每个 base/result 内容身份、宿主逐文件授权、冲突检测
  与原子提交/回滚协议；contract 4 的单文档布尔 callback 不可复用为 rename 写通道。
- T4-5 已在本项既有 Language Service 生命周期上完成保存/停顿刷新, 并以 generation、
  资源预算与 20-file `< 1 s` 真机证据收口；见
  [T4-5 保存即诊断](SAVE_DIAGNOSTICS_T4_5.md)。
- 若未来增加更多 codefix，必须按稳定 action kind 单独扩充 ABI 与宿主 policy；不能放宽为
  “TypeScript 返回什么就执行什么”，也不能允许 `commands` 或 `isNewFile`。
