# T4-1 项目级 TypeScript 诊断

> 状态：2026-08-28 已在 AutoJs6 宿主与 Ace Editor 插件完成部署，并在
> Xiaomi 23046RP50C / API 35 上通过真实跨 APK 验收。

## 结论

编辑器现在消费宿主按执行前编译规则捕获的项目源码 snapshot。Ace 将完整项目源码、当前
未保存文档以及 T2 冻结依赖类型层放进同一个 TypeScript Language Service Program，因此
相对 import 可以跨文件解析，错误路径会实时发布 TS2307，并与运行前编译的诊断保持一致。

该能力通过 editor ABI contract 2 追加，不替换既有单文档编辑接口。snapshot 是可选提示
输入：宿主不能安全捕获、项目超过预算、内容校验失败或插件无法完整读取时，Ace 清除项目
源码层并回到原有单文档语义，不把半份项目当成权威。T4-1 自身不开放定义跳转、跨文件
补全、auto-import、quick fix 或 rename；其中跨文件智能与受约束定义跳转已由
[T4-2](CROSS_FILE_INTELLIGENCE_T4_2.md) 完成，写入类能力仍属于 T4-3 至 T4-4。

## 数据流与所有权

```text
AutoJs6 Explorer / editor document path
  -> TypeScriptEditorProjectSnapshotProvider（后台文件捕获）
  -> EditorPluginProjectSnapshot（contract 2，有界且带内容身份）
  -> AceTypeScriptProjectSourceLayer（逐项复验并冻结）
  -> file:///autojs6/editor/<relative-path>（只读源码 VFS）
       + 当前 Ace buffer（覆盖 snapshot 主文件）
       + T2 dependency type VFS
  -> TypeScript Language Service Program
  -> Ace diagnostics annotations
```

宿主 `AceScriptEditorEngine` 只把 provider 放进 `EditorPluginSessionConfig`，不会在 Android
主线程读取项目。Ace 的同一后台 executor 同时捕获项目源码与依赖类型层，验证成功后再把
两层作为一个状态更新发布给 LSP manager；切换文档路径会先清除旧层，避免旧项目在新文档
初始化期间短暂可见。两层都只提供按已发布 URI 精确读取，不提供任意宿主文件读取接口。

当前编辑 buffer 具有最高优先级。即使 snapshot 中包含主文件的落盘内容，Language
Service 的主文档 URI 也返回当前 Ace 文本；其他根文件才读取冻结 snapshot。因此未保存
修改可立即参与跨文件诊断，同时 snapshot 的其余文件仍保持同一次捕获的一致视图。

## Contract 2 snapshot

公开模型为 `EditorPluginProjectSnapshot` 与 `EditorPluginProjectSource`，schema revision
固定为 `1`。每个 snapshot 携带：

- canonical 项目根、canonical 文档路径及文档相对路径；
- `rhino` 或 `node` target profile；
- 按相对路径排序的完整源码 inventory；
- 每个文件的 UTF-8 文本、精确字节长度和小写 SHA-256；
- 文件数、总字节数及完整 inventory fingerprint。

inventory fingerprint 使用 SHA-256，domain 为
`autojs6.editor.project-source.inventory.v1`。每个排序后的条目依次编码相对路径、UTF-8
字节数和内容 SHA-256；Ace 不信任宿主传入的摘要，会重新编码文本、复算每文件摘要及完整
inventory fingerprint。相对路径还必须唯一且 ASCII 大小写折叠后仍唯一，防止不同文件
系统语义产生歧义。

宿主复用 `TypeScriptProjectArchive.isSnapshotSource` 与目录遍历规则，保证编辑器源码集合
和运行前项目编译的 source inventory 同源。`project.json` / `tsconfig.json` 是项目边界；
Node profile 还允许 `package.json` 成为边界。`.mts` / `.cts` 强制选择 Node profile，其他
文件按项目配置选择 Rhino 或 Node。

## 有界捕获与失败闭合

Contract 2 的硬限制为：

| 项目 | 上限 |
|---|---:|
| source files | 512 |
| single source UTF-8 bytes | 512 KiB |
| total source UTF-8 bytes | 8 MiB |
| relative-path UTF-8 bytes | 1,024 |

宿主拒绝非 canonical 文档、symlink traversal、根目录逃逸、非法 UTF-8、重复或歧义路径、
不支持的 profile 以及任一配额溢出。provider 对可预期的捕获失败返回 `null`，不让编辑器
打开失败；Ace 对 schema、字段、长度、摘要、inventory、请求文档身份或 profile 的任何
不一致同样拒绝整个源码层。

Language Service 只有在声明的每个源码 URI 都能从 bridge 完整读取后才把
`projectSnapshotReady` 置为 `true`。读取一个文件失败就销毁本次 service 状态并按无项目
snapshot 重建，而不是保留已读到的子集。snapshot 不监听磁盘热变更；重新进入编辑会话或
重新设置文档路径会捕获新身份。

## 诊断策略

单文档模式无法判断 `./shared` 是路径拼错还是尚未提供给编辑器的正常项目文件，因此继续
屏蔽 TS2307、TS2792 与 TS7016。T2 依赖类型层单独存在也不再解除该屏蔽；只有完整、经
验证且全部读取成功的 project source snapshot 才发布这些模块解析诊断。

项目模式中，Language Service 的根文件包含全部项目源码和依赖声明，当前文档 buffer
覆盖冻结主文件。验证 fixture 同时证明：

- `./shared` 从相邻 `shared.ts` 正确解析；
- `./absent` 精确产生一个 TS2307；
- 跨文件值上的错误赋值继续产生 TS2322；
- dayjs、TypeScript 6 `typesVersions`、嵌套声明、installed `@types` 与 lodash
  DefinitelyTyped fallback 不回归；
- Rhino 和 Node profile 的项目诊断 code 集合均精确为 `[2307, 2322]`。

TS6059 是一个明确的虚拟化例外。执行前编译的受控 `rootDir` 是真实 project `/sources`
布局，而 Ace 使用 `file:///autojs6/editor/` 作为不暴露设备路径的合成根；仅由这两个等价
根布局差异产生的 TS6059 不展示给用户。其他语法、类型和模块解析诊断不因虚拟 URI 被
过滤。

## ABI 兼容矩阵

AutoJs6 当前 editor contract 为 `VERSION=2`，接受范围为 `1..2`。宿主装载策略必须按该
范围判断，不能把当前版本误当成唯一可接受版本。

| 宿主 | Ace contract | 行为 |
|---|---:|---|
| 旧宿主（低于 5276） | 1 | 保持既有单文档编辑行为 |
| 旧宿主（低于 5276） | 2 | 依据 contract / `requiresHostVersion` 在类加载前拒绝 |
| 当前宿主（5276+） | 1 | 继续支持；不要求插件消费 project provider |
| 当前宿主（5276+） | 2 | 启用有界项目 snapshot 与实时跨文件诊断 |

Ace contract 2 的 manifest 与运行时 metadata 都声明最低宿主 version code `5276`。新增
provider 是可选输入，不改变 contract 1 插件必须实现的入口和会话行为；宿主 policy 及
入口二次校验均覆盖范围下界、上界和两个越界值。

## 验收证据

桌面与 JVM 门禁：

- Ace `./gradlew check --offline`：通过；
- Ace `:app:testDebugUnitTest` 的 LSP/source-layer 用例、
  `:app:compileDebugAndroidTestKotlin` 与 `:app:verifyAutoJs6LspRuntime`：通过；
- Node runtime verifier：Rhino 与 Node 项目诊断均为 `[2307, 2322]`，单文档降级不发布
  TS2307，shared source、dayjs completion/hover 与 dependency type golden 全部通过；
- AutoJs6 `:plugin-api:editor-api:testDebugUnitTest`、`AceEditorPluginPolicyTest` 与
  `TypeScriptProjectArchiveTest`：通过；Android test Kotlin 编译通过。

真机只使用序列号 `968e9f18` 的 Xiaomi 23046RP50C / API 35：

| 验收 | 结果 |
|---|---|
| Ace 真实 WebView unresolved-import live diagnostic | `Time: 2.306`, `OK (1 test)` |
| Ace project-source + dependency bridge | `Time: 0.389`, `OK (1 test)` |
| AutoJs6 真实 Host provider + 已安装 Ace 跨 APK | `Time: 2.901`, `OK (1 test)`, codes=`[2307]` |
| AutoJs6 原 editor ABI smoke | `Time: 1.885`, `OK (1 test)` |

跨 APK 用例在宿主 cache 中创建带 `tsconfig.json` 的真实项目：`main.ts` 同时导入存在的
`shared.ts` 与不存在的 `absent`。它先断言宿主 provider 捕获恰好两个源码，再通过正式插件
发现和类加载路径创建 Ace session，最后轮询真实 WebView diagnostics 并要求排序后的 code
集合严格等于 `[2307]`。这条用例同时覆盖“宿主 inventory 与 Ace Program 确实连通”，而
不只是分别测试两端的假对象。

AutoJs6 全量 `:app:testAppDebugUnitTest` 仍有一个在本项之前已存在、与编辑器无关的
Explorer action-catalog policy 失败；T4-1 的定向单元、API、Android 编译与上述真机门禁
全部通过，该基线失败未混入本项提交。T4-2 收口时已把该断言同步到 Explorer protocol
v18 的当前边界；宿主全量回归现为 1716 tests、0 failed、4 skipped。

## 后续边界

- T4-2 已在同一只读源码层上增加跨文件 completion、hover、signature help 与 definition
  location，并由宿主复验后负责打开目标文件及定位；完整设计与证据见
  [T4-2 跨文件智能](CROSS_FILE_INTELLIGENCE_T4_2.md)。
- T4-3 已通过 active-buffer-only contract 4 开放 TypeScript codefix / auto-import 子集；
  T4-1 自身仍不执行源码写入。完整边界见
  [T4-3 auto-import 与 quick fix](AUTO_IMPORT_QUICK_FIX_T4_3.md)。
- T4-4 的 project rename 必须新增可审核的多文件变更事务，不能把当前只读 bridge 扩成
  任意路径写接口。
- T4-5 可复用本项 snapshot identity 做增量刷新与防抖，但任何缓存命中都不能绕过完整性
  校验或让旧 project/profile 状态跨文档泄漏。
