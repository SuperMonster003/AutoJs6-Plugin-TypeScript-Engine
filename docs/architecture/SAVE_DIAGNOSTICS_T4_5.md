# T4-5 保存即诊断

> 状态: 2026-08-29 已在 AutoJs6 宿主与 Ace Editor 插件完成部署, 并在
> Xiaomi 23046RP50C / Android API 35 上通过 20 文件真实跨 APK 验收。

## 结论

Ace 编辑器现在把 TypeScript Language Service 保持在整个编辑会话中。用户停顿输入时,
当前缓冲区诊断经过 450 ms 防抖后刷新; 精确保存成功后, 宿主在后台重新捕获有界项目快照,
Ace 对拓扑未变化的快照只更新发生变化的兄弟文件及其 script version, 再以 50 ms 调度延迟
刷新诊断。快速连续编辑、重复保存和过期项目捕获都由单调递增的 revision 或 generation
取消, 旧任务不能覆盖较新的诊断状态。

这项部署没有新增 editor ABI contract。宿主继续通过 contract 2 的只读
EditorPluginProjectSnapshotProvider 提供项目快照, 保存侧只复用既有的文档路径同步入口。
Ace 不获得文件写权限, 不直接扫描宿主文件系统, 也不把磁盘中的当前文件覆盖到尚未保存的活动
buffer。

最终 20 文件验收在指定设备上得到:

| 指标 | 结果 |
|---|---:|
| 保存完成到 TS2322 可见 | 201 ms |
| 包含宿主保存的端到端耗时 | 234 ms |
| 诊断调度到发布延迟 | 68 ms |
| TypeScript 诊断计算 | 18 ms |
| Language Service instance revision | 1, 保存前后未重建 |

因此 Roadmap 的“20 文件项目保存后诊断刷新小于 1 s”门槛已经满足, 同时保留完整项目身份校验、
资源上限、慢操作熔断和结构变化时的安全重建。

## 范围与非目标

T4-5 处理编辑器会话中的两类刷新:

1. 活动 buffer 发生普通输入变化时, 在用户停顿后重新诊断当前 Program。
2. 当前文件精确保存完成时, 重捕获项目源码与依赖类型层, 让兄弟文件的磁盘变化进入同一个
   常驻 Program, 然后重新诊断活动 buffer。

本项不包含:

- 文件系统 watcher; 外部进程修改兄弟文件后, 要到下一次文档路径同步或精确保存才会重捕获。
- 动态增删项目文件时的原地 Program 改造; URI 拓扑变化会安全销毁并重建 Language Service。
- 对宿主项目捕获过程做逐文件增量扫描; 当前捕获仍是完整、有界、后台执行的 snapshot。
- 对已经开始执行的同步 TypeScript API 调用做线程级抢占; 防抖会取消尚未执行的任务, 资源上限与
  慢操作熔断约束已经开始的调用。
- T5 的运行时动态编译; 编辑器 Language Service 与执行期 compiler session 仍是不同生命周期。

## 端到端数据流

### 精确保存路径

~~~text
EditorView.save()
  -> write the captured editor revision to disk
  -> confirm that the current buffer is still exactly that saved revision
  -> editor.markTextAsSaved()
  -> syncEditorDocumentPath()
  -> existing Ace setDocumentPath(same path)
  -> cancel the previous pending capture and increment request revision
  -> wait 75 ms to coalesce repeated same-document refreshes
  -> capture source layer and dependency type layer on one background executor
  -> reject an obsolete result by request revision
  -> apply frozen layers on the main thread
  -> refreshLsp("project-snapshot")
  -> compare structural identity and content fingerprint
  -> update all available changed siblings transactionally
  -> keep the active Ace buffer authoritative
  -> schedule diagnostics after 50 ms
  -> publish only the current diagnostic generation
  -> send runtime state to Ace Kotlin automatically
~~~

保存开始后如果用户又输入了内容, 宿主不会把较早的 IO 结果当作当前 buffer 的 clean 状态, 也不会
从该分支触发 T4-5 项目刷新。只有写入磁盘的文本仍精确等于当前编辑器 revision 时,
markTextAsSaved 与 syncEditorDocumentPath 才连续发生。这个边界避免旧保存结果捕获新 buffer
无法代表的项目状态。

### 输入停顿路径

~~~text
Ace session change
  -> update the known document length
  -> cancel the previous validation timer
  -> allocate a new diagnostic generation
  -> wait 450 ms
  -> verify generation and configuration revision
  -> synchronize the active session into the resident service
  -> compute diagnostics
  -> verify the generation again before publication
~~~

五次快速输入只保留一个 450 ms timer。测试还主动执行一个已经被 clear 的旧 timer, 证明其
generation 检查会拒绝发布; 最新一代才可以修改 Ace annotations、诊断计数和宿主可观察状态。

## 宿主保存边界与后台捕获

AutoJs6 的改动位于普通文件保存成功路径, 并紧跟已有的 markTextAsSaved。它没有修改
EditorPlugin、EditorView 或文件服务的公共接口, 也没有改变 rename 事务、编码保存或 sticky
dirty 的既有规则。

AceCodeEditor 对 setDocumentPath 分成两种情况:

- 文档身份变化时立即开始第一次项目捕获, 以免打开文件多等待一轮 debounce。
- 同一文档再次同步时取消尚未开始的捕获, request revision 加一, 再等待 75 ms。

实际 source layer 与 dependency type layer 捕获运行在单线程后台 executor。每个结果回到主线程
前再次核对 destroyed 状态和 request revision。文档切换、下一次保存或销毁发生后, 较旧任务即使
已经完成磁盘读取也不能 apply。destroy 会移除尚未开始的 Runnable、递增 revision 并关闭
executor。

75 ms 只合并宿主项目捕获, 不取代 450 ms 输入停顿防抖或 50 ms 保存后诊断调度。三者分别控制
磁盘层、编辑事件层和更新后的 Program 诊断层。

## 常驻 Language Service 的增量更新

LSP client 每次 refresh 都重新读取宿主提供的 options, 并把变化分成两类。

### 可原地更新的内容变化

只有 source inventory fingerprint 或 source byte length 变化, 同时下面的结构身份保持不变时,
client 才调用 resident service 的 updateProjectSnapshot:

- root URI 与 document URI;
- 已排序 project source URI 列表及 source file count;
- snapshot ready 状态与 schema revision;
- TypeScript profile、profile revision 与版本;
- declaration library 列表、project type URI 列表;
- dependency inventory、resolver policy、边界状态与全部类型层身份;
- LSP enabled、checkJs 等影响 Program 语义的配置。

服务先验证新快照仍为 schema 1、仍包含当前文档, 并且 URI 拓扑与原 Program 完全相同。随后它在
修改任何 files 或 script version 前读取每一个非当前源码。只要任一兄弟文件不可用, 整次原地
更新返回失败, 已有文本、版本和 fingerprint 全部保持不变。client 随即走安全重建路径, 不发布
一个只更新了一半的 Program。

全部兄弟文件可用后, 服务只替换文本真正变化的 URI, 并只递增这些文件的 script version。
inventory fingerprint、文件数与总字节数随后作为同一次更新发布。
projectSnapshotUpdateCount 记录成功更新次数,
projectSnapshotChangedFileCount 记录实际变化的兄弟文件总数。

当前文档故意不从 snapshot bridge 重新载入。诊断前仍由 syncSession 使用 Ace session 的当前
文本更新它, 因而活动 buffer 始终是当前文件的唯一权威。即使未来在保存后 75 ms 窗口内又发生
输入, 磁盘内容也不能回写或覆盖新文本。

### 必须重建的结构变化

项目根、当前文档、源码 URI 拓扑、文件数量、profile、标准库、依赖声明或 resolver policy
任何一项变化, 都会销毁旧服务并按完整 options 创建新 Program。原地读取失败也使用相同回退。
这个选择把增量优化限定在可以证明“仍是同一个项目图”的范围内, 不用猜测文件增删、依赖版本或
配置变化是否可安全 patch。

保存侧验收同时断言 tsServiceInstanceRevision 保持为 1、tsServiceDisposeCount 保持为 0,
projectSnapshotIncrementalRefreshCount 增加, 且 service 内部
projectSnapshotUpdateCount 大于 0。

## 诊断调度、取消与发布

每次 scheduleDiagnostics 都先清除现有 timer, 再生成包含以下身份的 run:

- 单调递增 diagnosticGeneration;
- 当前 configurationRevision;
- reason, scheduledAt 与调度延迟。

timer 到期时先验证这两个 revision。开始诊断前和设置 annotations 前还会再次验证。旧 timer 被
错误唤醒、配置刷新、文档切换、destroy 或手动 validate 都不能让较老一代覆盖新结果。

reason 也属于可观察契约:

- idle-change 使用 450 ms;
- project-snapshot 使用 50 ms;
- warmup、manual 与 attach 使用相同 generation 规则。

诊断发布后, onDiagnosticsPublished 回调立即把完整 LSP state 交给页面 bridge, 再由既有
lspStateChanged 通道进入 Ace Kotlin。宿主测试因此不需要轮询一个会主动改变语义的
validateNow; 它只读取已发布状态, 可以精确测量真实保存路径的刷新时间。

## 资源预算与降级

| 边界 | 固定值 | 行为 |
|---|---:|---|
| 同文档项目捕获 debounce | 75 ms | 合并重复保存/路径同步, 捕获在后台 executor |
| 输入停顿诊断 debounce | 450 ms | 每次输入取消上一代 timer |
| 保存后诊断调度 | 50 ms | snapshot apply 后快速刷新, 仍允许相邻任务合并 |
| 项目源码文件数 | 512 | contract 2 快照超限即拒绝 |
| 单个项目源码 | 512 KiB UTF-8 | 宿主读取前后均复验 |
| 项目源码总量 | 8 MiB UTF-8 | 完整快照累计超限即拒绝 |
| 活动文档语义长度 | 524,288 UTF-16 code units | 超限关闭语义服务, 保留静态降级 |
| TypeScript 诊断数 | 100 | Language Service 输出有界 |
| 慢语义操作阈值 | 1000 ms | 达到阈值参与连续慢操作计数 |
| 连续慢操作上限 | 2 | 打开 semantic circuit, 销毁服务并切换静态 fallback |

项目快照提供者继续规范化 canonical path, 拒绝 symbolic link traversal, 只收集当前
Rhino/Node profile 可接纳的源码, 对相对路径排序, 并把路径、UTF-8 长度与内容 SHA-256 汇入
inventory fingerprint。T4-5 没有放宽 T4-1 建立的任何来源或大小边界。

Language Service 本身仍运行在 Ace WebView 的 JavaScript 环境中, 所以已经开始的同步 TypeScript
调用不是可抢占线程。这里的非阻塞策略是: 不在每个键击上扫描项目, 把宿主文件捕获移出主线程,
合并旧任务, 限制输入规模, 并对连续 1 s 慢操作 fail-safe 降级。最终设备上的实际诊断计算为
18 ms。

## 可观察状态

T4-5 在既有 LSP runtime state 中新增并向 Ace Kotlin 透传:

- projectSnapshotIncrementalRefreshCount 与 projectSnapshotChangedFileCount;
- diagnosticGeneration、diagnosticPublishCount 与 lastPublishedDiagnosticGeneration;
- lastDiagnosticReason、scheduledAt、startedAt、publishedAt、latencyMs 与 durationMs;
- diagnosticDebounceDelayMs 与 projectSnapshotDiagnosticDelayMs;
- tsServiceInstanceRevision 与 tsServiceDisposeCount;
- service 内的 projectSnapshotUpdateCount、projectSnapshotChangedFileCount、
  projectSourceInventoryFingerprint、source file count 与 source byte length。

这些字段只提供诊断与验收证据, 不授予新能力。自动发布回调不会携带项目源码文本。

## 验证矩阵

### 桌面与构建门禁

| 仓库 / 门禁 | 最终结果 |
|---|---|
| Ace :app:verifyAutoJs6LspRuntime | BUILD SUCCESSFUL; TypeScript 6.0.3 |
| Ace 20-file incremental fixture | 1 个兄弟文件变化, TS2322, 6.8 ms, partial snapshot 原子拒绝 |
| Ace diagnostic scheduler fixture | 5 次快速编辑合并为 1 次, 旧 generation 拒绝, 450/50 ms 锁定 |
| Ace resident-service fixture | 保存侧 create 1, dispose 0, incremental refresh 1 |
| Ace :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest | BUILD SUCCESSFUL |
| AutoJs6 :app:compileAppDebugAndroidTestKotlin | BUILD SUCCESSFUL |
| AutoJs6 :app:assembleAppDebug :app:assembleAppDebugAndroidTest | BUILD SUCCESSFUL |
| 十语言 changelog JSON 与 Markdown generator | 10/10 解析并生成成功 |

桌面 20 文件 fixture 还验证了两个失败关闭边界: 活动 buffer 文本优先于 snapshot 中的当前文件,
以及缺少任一兄弟文件的 snapshot 不得部分更新服务。

### 真机门禁

最终 APK 与 instrumentation 只安装、运行于显式指定的设备 968e9f18, 没有访问其他在线设备。

| 真机门禁 | 最终结果 |
|---|---|
| 设备 | Xiaomi 23046RP50C, Android API 35 |
| Ace AceCodeEditorSmokeTest | 8/8 通过 |
| AutoJs6 AceEditorPluginHostDeviceTest | 4/4 通过 |
| 20 文件保存即诊断 | TS2322 在保存完成后 201 ms 发布 |
| 包含保存的总时长 | 234 ms |
| 调度延迟 / 诊断耗时 | 68 ms / 18 ms |
| 常驻服务 | instance revision 1, 保存未重建 |

真实宿主用例创建 20 个 TypeScript 文件。main.ts 最初从 file01.ts 读取 number, 初始诊断不含
TS2322; 测试随后把兄弟文件改为 string, 并证明旧冻结 snapshot 在保存前仍未吸收外部变化。
活动 Ace buffer 再产生一个无害换行并调用正式 EditorView.save。精确保存触发新 snapshot 后,
用例要求 fingerprint 更新、reason 为 project-snapshot、增量刷新计数和发布计数增长、
Language Service revision 不变, 最终出现 TS2322, 且保存后与包含保存的两种时长都小于 1 s。

完整 Host 类还回归真实插件发现、签名与 ABI 加载, T4-2 项目诊断/定义跳转/quick fix,
以及 T4-4 三文件跨项目重命名。完整 Ace 类回归 WebView 生命周期、宿主替换、选择和既有编辑器
行为。

## 部署提交

| 仓库 | 提交 | 内容 |
|---|---|---|
| Ace Editor | 2713ea1f4 | 常驻项目快照增量更新、诊断 generation、防抖、遥测与桌面 fixture |
| AutoJs6 | d9e22ff49 | 精确保存后刷新项目上下文与 20 文件真实跨 APK 验收 |
| AutoJs6 | 3422e4522 | 十语言 changelog 与生成的 README/CHANGELOG |

## 后续边界

- 宿主 capture 当前仍遍历完整的有界项目。若 512 文件 / 8 MiB 上限内的项目仍出现可测量捕获
  压力, 后续可以设计带文件 identity 的增量 source layer, 但不能只相信修改时间或跳过 SHA-256。
- 若需要即时吸收外部文件变化, 应新增受控 watcher 生命周期与事件合并; 不能让 Ace 获得任意
  目录读取权限。
- 若要安全取消已经开始的超长 TypeScript 语义计算, 需要 Worker 或可中断执行模型。当前
  generation 解决的是排队与发布过期, semantic circuit 解决的是连续慢调用后的降级。
- T5 可以复用 T3 resident compiler session, 但不能把编辑器 WebView Language Service 当作
  执行期编译授权或输出来源。
