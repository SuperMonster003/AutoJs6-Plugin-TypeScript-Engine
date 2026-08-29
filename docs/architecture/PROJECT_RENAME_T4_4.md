# T4-4 TypeScript 跨文件重命名

> 状态：2026-08-29 已在 AutoJs6 宿主与 Ace Editor 插件完成部署，并在
> Xiaomi 23046RP50C / Android API 35 上通过最终跨 APK 验收。

## 结论

Ace 现在从内置 TypeScript 6.0.3 Language Service 的精确项目 Program 获取 rename
语义。用户可按 F2，或在移动端选区工具栏选择“重命名项目符号”；Ace 只把 TypeScript
确认的项目源码引用绑定到当前冻结 snapshot，再让用户输入新标识符。

多文件写入不属于插件。editor ABI contract 5 只能表达项目根身份、源码 inventory
fingerprint、规范相对路径、每个文件的 base/result SHA-256，以及对旧符号的有序 UTF-16
替换。AutoJs6 独立重捕获 snapshot、重放全部 edit、展示宿主预览、冻结用户输入、取得稳定
排序的文件写锁、在目标文件同目录暂存结果与恢复副本，并完成发布或反向回滚。活动缓冲区只由
宿主以一个 undo history token 更新；Ace 不写磁盘，也不会再应用第二次 buffer edit。

最终三文件验收把 `answer` 重命名为 `projectAnswer`，精确更新 `consumer.ts`、`main.ts` 与
`shared.ts` 的 5 处引用。重命名后新 snapshot 的 inventory fingerprint 与磁盘一致，
Language Service 为 0 条诊断；生产 TypeScript 6.0.3 编译器还分别以 Rhino/CommonJS 与
Node/NodeNext 配置成功 emit 3 个 JavaScript 和 3 个 source map，满足“全部引用点更新且编译
通过”的 Roadmap 验收。

## 范围与非目标

T4-4 接受的是“同一个已保存项目中，至少跨两个源码文件的普通 TypeScript 标识符 rename”。
当前边界有意保持窄小：

- 旧名称和新名称都必须匹配 `[A-Za-z_$][A-Za-z0-9_$]*`；
- rename 必须命中 2–128 个项目源码文件，并且包含活动文档；
- 只查找代码中的符号位置，不查找字符串或注释；
- 禁止 module specifier/path rename，`allowRenameOfImportPath` 固定为 `false`；
- 不修改 dependency snapshot、TypeScript 标准库或 `node_modules`；
- 不创建、删除或重命名文件，不运行 TypeScript command，也不安装 package；
- 活动文档必须已保存；dirty buffer 不进入预览或事务；
- 单文件 rename、非 ASCII Unicode 标识符和文件名 rename 不在本项开放范围。

这些约束不是 UI 提示，而是 JavaScript、Ace Kotlin、共享 ABI 和宿主事务各自重新执行的结构化
门禁。任一层无法证明候选仍绑定到同一项目内容时，操作都会失败关闭，不降级成文本搜索替换。

## 端到端数据流

```text
F2 / mobile Rename / test bridge renameSymbol(row, column)
  -> synchronize the active Ace session into the exact project Program
  -> languageService.getRenameInfo(..., allowRenameOfImportPath=false)
  -> languageService.findRenameLocations(..., strings=false, comments=false)
  -> accept only exact spans in the frozen project-source URI set
  -> group and sort locations; enforce file/edit/payload budgets
  -> Ace Kotlin rebinds every URI to relative path + source hash + inventory identity
  -> verify exact old text, UTF-16 boundaries, BOM view, mirror revision and clean state
  -> native Ace dialog validates the new identifier
  -> compute every result text and SHA-256 without writing
  -> editor ABI contract 5 publishes one typed project transaction
  -> AutoJs6 re-captures and independently replays the complete project snapshot
  -> host preview lists old/new names, file count, edit count and each relative path
  -> user confirms; host freezes user input and acquires stable multi-URI write locks
  -> write+sync sibling backup and staged files; verify mode, identity and hashes
  -> publish each staged file with rename, result-hash verification and directory sync
  -> host replaces the active Ace buffer once with one undo history token
  -> delete temporary files, mark the buffer clean, refresh project context
  -> callback APPLIED / REJECTED / STALE / FAILED
```

TypeScript 负责回答“哪些语法位置属于同一符号”；Ace 负责证明这些虚拟 URI 和位置仍属于同一个
冻结 Program；AutoJs6 负责决定本地文件是否仍与请求完全相同，并独占所有持久化与恢复动作。

## TypeScript rename 候选

页面只有在 T4-1 project snapshot 已就绪、全部 project source URI 仍存在且活动 session 已同步
时才查询 rename。`getRenameInfo` 必须返回 `canRename=true`，trigger span 必须落在当前文本内，
而 span 的实际文本和 TypeScript `displayName` 必须同时等于一个受支持标识符。

随后 `findRenameLocations` 关闭 strings、comments 与 prefix/suffix 重写。每个 location 必须：

1. 属于冻结 project-source URI 集合，而不是依赖、标准库或未知虚拟文件；
2. 指向仍存在的精确文本；span 长度和内容必须等于旧名称；
3. 不携带 `prefixText` 或 `suffixText`；
4. 在单文件内按 start offset 严格递增、互不重叠；
5. 汇总后包含活动 URI，并命中至少两个文件。

页面按 URI 排序后只发布 `symbolName + [{uri, edits}]`。Ace Kotlin 不把 URI 当成文件授权，
而是通过当前 `AceTypeScriptProjectSourceLayer` 重新取得相对路径、内容 SHA-256 与文本；未知 URI
立即拒绝。活动文档允许编辑器视图去掉一个 UTF-8 BOM，但磁盘 base/result hash 仍包含 BOM。

候选展示期间保存完整 base text、text mirror revision 与 project layer identity。普通编辑、项目
snapshot 更新、文档切换、read-only、销毁或另一个 rename in-flight 都会使候选过期。用户输入
新名称后，Ace 在内存中倒序重放全部 edit，自行计算各文件 result hash 和修改后的项目总字节数；
只有完整请求再次通过共享 contract 才调用宿主。

## Contract 5：只能描述有界项目符号替换

`EditorPluginProjectRenameRequest` 的权威字段如下：

| 字段 | 用途 |
|---|---|
| `projectRootPath` | T4-1 snapshot 的绝对 canonical 项目根身份 |
| `projectSourceInventoryFingerprint` | 完整项目源码路径、内容与顺序的不可变 inventory 身份 |
| `documentRelativePath` | 活动文档的规范项目相对路径 |
| `symbolName` / `newName` | 旧、新 ASCII TypeScript 标识符 |
| `files` | 按相对路径严格排序的受影响文件列表 |
| file `relativePath` | 不含空段、`.`、`..`、反斜线或控制字符的规范相对路径 |
| file `baseContentSha256` | snapshot 中磁盘 base bytes 的 SHA-256 |
| file `resultContentSha256` | 宿主重放 edit 后结果 bytes 的 SHA-256 |
| file `edits` | 相对 base text 的有序、互不重叠 UTF-16 offset edit |

模型没有 absolute target path、URI、file descriptor、Intent、dependency authority、command、
任意 Bundle、新文件、删除或文件移动字段。每个 replacement 必须精确等于 `newName`，每个被替换
span 的长度必须精确等于 `symbolName.length`；因此 contract 5 不能伪装成一般多文件 patch API。

共享 API 冻结以下预算：

| 项目 | 上限 |
|---|---:|
| project root | 4,096 UTF-8 bytes，非空且无控制字符 |
| relative path | 1,024 UTF-8 bytes，规范路径段 |
| old/new identifier | 各 256 UTF-8 bytes |
| 文件数 | 2–128 |
| 每文件 edit | 1–512 |
| 总 edit | 4,096 |
| 单文档 | 1,048,576 UTF-16 code units |
| replacement UTF-8 bytes 合计 | 1,048,576 |
| inventory/base/result SHA-256 | 精确 64 个小写十六进制字符 |

文件路径除严格排序外还必须在 ASCII case-fold 后唯一，避免大小写不敏感文件系统把两个模型条目
绑定到同一目标。活动文档必须恰好出现在文件集合中。Ace 的 WebView 入口另设 1,000,000-character
JSON payload 与 4,096-character URI 上限。

callback 不是布尔授权，而是终态枚举：

| 结果 | 含义 |
|---|---|
| `APPLIED` | 宿主已完成磁盘发布、活动 buffer 更新与清理 |
| `REJECTED` | 用户取消、结构不支持、read-only/dirty 或其他前置条件不满足 |
| `STALE` | 文档、snapshot、文件内容或元数据在等待期间变化 |
| `FAILED` | 发布、buffer 更新或恢复路径发生运行时失败 |

## 分层校验

### JavaScript / TypeScript 层

页面限制 TypeScript API 调用结果，只允许 exact project-source locations，拒绝未知 URI、
prefix/suffix、字符串、注释、路径 rename、越界和重叠 span，并在跨桥前执行 128 文件、每文件
512 edit 与总计 4,096 edit 预算。页面不计算宿主路径、不打开文件，也不调用 Ace 文本替换。

### Ace Kotlin 层

`AceTypeScriptProjectRenameCandidate.bind` 从当前不可变 source layer 重绑定每个 URI，检查内容
hash、relative path、活动文档数量、良构 UTF-16、surrogate-pair 边界、旧符号实际文本、BOM
视图和 project byte budget。`prepare(newName)` 重新重放所有 edit，计算磁盘语义的 base/result
hash，并再次执行 contract 5。

Ace 在弹出输入框和请求 host 前检查 clean/read-only/lifecycle、完整 base text、mirror revision
以及 project layer identity。宿主 callback 返回期间设置 `projectRenameRequestInFlight`，不会并发
发出第二项请求。无 listener 或任何 stale 状态都默认拒绝。最重要的是：Ace 不因 `APPLIED`
再次写入活动 buffer；该 buffer 已由宿主事务更新。

### AutoJs6 宿主层

`EditorView` 只接受当前仍挂载的 Ace engine，并检查文档是 file URI、活动 buffer 干净且未处于
加载、保存、编码转换、草稿恢复、reload-required 或另一个 rename 状态。prepare 在 IO 线程
重新捕获 T4-1 snapshot，要求项目根、inventory fingerprint、活动相对路径、canonical 文档
路径、源码集合和每个 base hash 都精确相等。

宿主随后在自己的文本上验证良构 UTF-16 和 surrogate 边界，逐 edit 检查实际旧名称，倒序重放
并独立核对 result SHA-256、单文件预算和修改后项目总预算。插件提供的 result hash 只是待验证
声明，不能代替宿主重放。

prepare 完成后仍绑定 EditorView 的 request generation、document identity generation、text
revision、engine identity 与完整 base text。预览期间任何一项变化都返回 `STALE`，不会继续使用
旧 `Prepared`。

## 宿主发布与回滚协议

用户确认宿主预览后，活动 Ace editor 进入 read-only，从而冻结用户输入和普通编辑动作。宿主按
URI identity 去重、排序后一次持有全部 `EditorFileIoCoordinator` write locks；同一进程内的普通
Save、Reload、Convert 或另一编辑器事务不能穿插在 freshness check 与发布之间，稳定全序也避免
两个重叠事务死锁。

事务分为四个明确阶段。

### 1. Freshness 与 staging

发布前再次捕获完整 snapshot，并重新比较 root、inventory 与活动路径。对每个目标执行 `stat`
和 SHA-256 检查：必须是独立 regular file、link count 必须为 1、canonical 路径必须仍在项目根
内，文件 identity、permission mode 与 base hash 必须保持不变。canonical alias、symlink traversal
和 hard link 都被拒绝。

每个目标目录创建两个随机 sibling 临时文件：`.autojs6-rename-backup-*` 保存精确 base bytes，
`.autojs6-rename-stage-*` 保存 result bytes。两者继承目标 permission bits，写入后执行 file-descriptor
sync 并重新核对 hash。UTF-8 BOM 按磁盘内容保留；活动 editor 的无 BOM offset 不会改变磁盘
编码身份。

### 2. Publication

每个目标在替换前再次核对 hash、device/inode identity、mode、link count 与 regular-file 状态，
随后使用同目录 `rename(stage, target)` 替换。每次替换后要求 target hash 精确等于 result hash，
并尝试同步父目录。所有磁盘文件发布后，宿主才调用 `replaceAllTextUndoably` 更新活动 Ace buffer，
并核对回调成功及完整结果文本。

用户输入的 read-only 与宿主可信替换是两个权限面。Ace 在冻结状态下仍接受宿主发起的
`replaceAllTextUndoably`，但键盘、选择工具栏和页面 mutation 继续被阻止；这保证事务完成阶段
不会因为用于防并发的 read-only 反过来拒绝已授权的 buffer 收尾。

### 3. Completion

活动 buffer 成功后删除 backup/staged 临时文件并同步相关目录，标记 editor 文本已保存，丢弃
旧 stable draft，清除 dirty 状态，再通过 `setDocumentPath` 重新捕获项目上下文。最后解除冻结并
回调 `APPLIED`。刷新后的 Language Service 因而使用重命名后的源码 inventory，而不是继续持有
旧 fingerprint。

### 4. Rollback

staging 或任一 publication 步骤失败时，宿主按发布顺序反向处理已经替换的文件：先要求当前
target 仍等于本事务的 result hash，再用 sibling backup 恢复，核对 base hash并同步目录。活动
buffer 写回失败时也先恢复 base buffer，再走同一磁盘回滚。成功回滚后清理全部临时文件，不保留
部分 rename。

若回滚本身无法恢复某个文件，宿主不会删除仍存在的 recovery backup；它把文档标记为必须 reload，
展示恢复副本的绝对路径并返回 `FAILED`。Activity 销毁与后台 publication 竞态也有明确终态：若
活动 buffer 已成功应用则完成清理，否则在 IO worker 上回滚磁盘 publication。

## “原子”的精确含义

本协议向 AutoJs6 编辑会话提供 all-or-rollback 语义：成功时所有目标和活动 buffer 都是 result；
任何普通失败都恢复所有已发布目标，不向用户保留已知的部分结果。单个 sibling `rename` 是同目录
目录项的原子替换，应用内稳定多文件锁阻止 AutoJs6 自己的文件操作交错。

它不是一个跨多个 inode 的单一文件系统事务。Linux/Android 没有让多个独立目录项在同一瞬间
一起切换的通用原语，因此不受 AutoJs6 锁约束的外部进程理论上可能在逐文件 publication 期间
观察到中间状态；掉电耐久性也依赖设备文件系统对 file sync、rename 与 directory sync 的实现。
文档和 UI 中的“一个事务”指完整成功或可验证回滚协议，不承诺对任意外部观察者的多文件瞬时
可见性。

## 交互与兼容性

- 桌面/硬件键盘入口为 F2；移动端在可写 TypeScript 选区工具栏显示“重命名项目符号”；
- Ace 先显示新名称输入框，默认选中旧名称；不合法或未变化的标识符不发送请求；
- AutoJs6 再显示独立预览，包含 old/new name、总文件数、总引用数和每个相对路径的 edit 数；
- 用户取消返回 `REJECTED`，不会创建临时文件或修改 buffer；
- rename、成功、save-first、stale、失败与 rollback-failed 文案已覆盖现有本地化资源；
- `EditorPluginContract.VERSION = 5`，AutoJs6 支持范围为 `1..5`；旧 contract 1–4 插件继续使用
  原能力，新增 callback 的默认实现返回 `REJECTED`；
- Ace manifest、runtime metadata 与 entrypoint 同步声明 contract 5，并发布 `projectRename`
  capability；Ace 仍要求 AutoJs6 version code 5276；
- contract 5 只追加 project rename 模型和 callback，不改变 T4-1 snapshot、T4-2 definition 或
  T4-3 current-document code action schema；
- Ace 内置 `editor-api.aar` 与 AutoJs6 contract 5 release AAR 均为 78,079 bytes，SHA-256 均为
  `30ae0e4a78fe7adb8cf3c2399ca39cc739966111d137356260272e05194ca960`。

## 验收证据

桌面、构建与生产编译器门禁：

| 门禁 | 结果 |
|---|---|
| AutoJs6 `:plugin-api:editor-api:testDebugUnitTest` | 12 tests，0 failed |
| AutoJs6 `TypeScriptEditorProjectRenameTransactionTest` | 6 tests，0 failed |
| AutoJs6 `:app:compileAppDebugAndroidTestKotlin` | BUILD SUCCESSFUL |
| Ace `:app:testDebugUnitTest` | 126 tests，0 failed，0 skipped |
| Ace `:app:verifyAutoJs6LspRuntime` | TypeScript 6.0.3，Rhino/Node projectRename 均为 3 files、5 edits、0 diagnostics |
| Ace `:app:assembleDebug :app:assembleDebugAndroidTest` | BUILD SUCCESSFUL |
| Engine `:app:verifyR2ProjectCompilerDesktop --rerun-tasks` | BUILD SUCCESSFUL；生产 Worker Rhino golden 通过 |
| 生产 TypeScript 6.0.3 精确三文件直编译 | Rhino/CommonJS 与 Node/NodeNext 均 0 error、emit 未跳过，各 3 JS + 3 source maps |

事务单元测试不仅覆盖成功路径，还锁定 UTF-8 BOM、dirty/stale inventory、edit 未精确选择旧符号、
publication 中途失败后的全文件回滚，以及 preview 与 publication 之间发生冲突时零写入。共享 ABI
测试同时拒绝路径/inventory authority 逃逸、大小写别名、非 rename replacement、缺失活动文档和
非法/未变化标识符。

最终 APK 只安装并运行于显式指定的 `968e9f18`：

| 真机门禁 | 结果 |
|---|---|
| 设备 | Xiaomi 23046RP50C，Android API 35 |
| Ace `hostReplacementSucceedsWhileUserInputIsReadOnly` | `Time: 1.373`，`OK (1 test)` |
| AutoJs6 `installedAceEditorPluginLoadsAndRunsThroughHostAbi` | 通过真实插件发现、签名与 ABI 加载 |
| AutoJs6 `installedAceEditorConsumesHostSnapshotForProjectDiagnosticsAndDefinitionNavigation` | 既有项目诊断与定义跳转回归通过 |
| AutoJs6 `installedAceEditorRenamesThreeFilesThroughHostTransaction` | `Time: 4.618`，`OK (1 test)` |

最终跨 APK 用例驱动正式 `EditActivity`、真实 Ace WebView 的 rename 查询与新名称对话框，再通过
accessibility 确认 AutoJs6 宿主预览。断言精确 3 files / 5 edits，三个磁盘文件和活动 buffer
全部变为 `projectAnswer`，buffer clean 且解除 read-only；项目中不存在
`.autojs6-rename-*` 残留。重新捕获的 3-source snapshot fingerprint 与磁盘一致，Language
Service 诊断严格为 0。

所有 adb 安装、属性读取和 instrumentation 命令都显式使用 `adb -s 968e9f18`，没有访问或
修改其他在线设备。

## 部署提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| AutoJs6 | `2da291821` | editor ABI contract 5、类型化多文件 rename 模型与共享预算 |
| AutoJs6 | `8a1a3eebc` | 宿主预览、稳定锁、staging、publication、buffer 更新与回滚 |
| AutoJs6 | `2758daa58` | rename 成功后重捕获项目上下文 |
| AutoJs6 | `b574f4e86` | 真实三文件跨 APK 事务验收 |
| AutoJs6 | `51565c30e` | 十语言 changelog 与生成文档 |
| Ace Editor | `a098256` | TypeScript project rename 语义、绑定、UI 与 contract 5 请求 |
| Ace Editor | `e9ec558` | 三文件 WebView rename 与 JVM 边界测试 |
| Ace Editor | `deb5920` | manifest/runtime metadata 宣告 contract 5 与 `projectRename` |
| Ace Editor | `9e481b1` | 用户输入冻结时允许可信宿主完成 undoable replacement |
| Ace Editor | `5df8303` | 十语言 changelog 与生成文档 |
| Ace Editor | `2a7bf98` | 同步最终 contract 5 release editor API AAR |

## 后续边界

- T4-5 已把 T4-4 成功后的 `setDocumentPath` 收紧为 exact-save 触发, 并补齐常驻服务、
  generation 取消、450/75/50 ms 调度与项目预算；20-file 真机保存后诊断为 201 ms。见
  [T4-5 保存即诊断](SAVE_DIAGNOSTICS_T4_5.md)。
- 若未来开放单文件 rename、Unicode identifier、file/module rename 或依赖源码 refactor，需要单独
  扩展 ABI、预览和事务 policy；不能通过放宽当前字符串字段绕过 contract 5。
- 若未来要求对外部进程也具备真正的多文件瞬时可见性，需要不同存储模型，例如版本目录加单指针
  切换；当前 sibling rename + rollback 协议不能被描述成这样的文件系统事务。
