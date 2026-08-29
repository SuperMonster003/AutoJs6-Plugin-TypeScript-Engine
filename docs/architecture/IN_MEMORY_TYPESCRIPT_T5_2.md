# T5-2 TypeScript 字符串即时执行

状态: 2026-08-29 完成并通过真实 Host + TypeScript Compiler Plugin 真机验收。

## 结果

AutoJs6 既有 `engines.execScript(name, source, config)` 入口现在会按 `name` 的规范
扩展名自动区分 JavaScript 与 compiler-backed TypeScript。用户不需要学习第二套执行 API:

```javascript
engines.execScript(
  "calculation.ts",
  "const answer: number = 40; answer + 2",
)
```

上述脚本经过独立 TypeScript Compiler Plugin 编译后由 Rhino 执行, listener 收到最终值
`42`。完整日志页和编辑器日志底部面板也提供同一条链路的单行输入框, 输入会以 `>` 记录,
成功结果以 `=>` 记录。

T5-2 没有增加 raw TypeScript 直派或宿主内置编译器。插件缺失、类型错误、运行异常与
本地 TypeScript 文件执行使用同一套稳定失败路径。

## 自动识别语义

| `name` | 编译 profile | 执行引擎 | 结果 |
|---|---|---|---|
| `snippet.ts` | Rhino | loop-based Rhino | 编译并返回最终求值结果 |
| `view.tsx` | Rhino | loop-based Rhino | 编译后执行, 保留既有 TSX/UI 能力 |
| `module.mts` | Node | Node Runtime Plugin | 以 ESM 入口编译执行 |
| `module.cts` | Node | Node Runtime Plugin | 以 CommonJS 入口编译执行 |
| 普通名称或 `.js` | 无 | 既有 JavaScript 路由 | 行为不变 |
| `.d.ts/.d.mts/.d.cts` | 无 | 无 | 执行前稳定拒绝 |

内存 source name 必须是单一规范文件名: 非空, 不包含 `/`、`\\` 或控制字符, 也不能是
`.` 或 `..`。`.ts/.tsx` 不会因源码首行的 `"nodejs"` directive 改走 Node, 因为单个内存
`.ts` 没有可验证的 package metadata; 需要 Node profile 时必须使用 `.mts` 或 `.cts`。
UI 等非引擎 execution mode directive 仍由编译后脚本按既有规则处理。

## 执行数据流

```text
engines.execScript(name, TypeScript text)
  -> TypeScriptStringSource: canonical name + semantic identity
  -> ScriptEngineService worker preparation
  -> TypeScriptCompilerPluginClient.compileSingleSource
  -> bounded UTF-8 bytes + existing authenticated R2 single-file archive
  -> independent TypeScript Compiler Plugin
  -> emitted JavaScript + mandatory Source Map, or bounded diagnostics
  -> existing CompiledTypeScriptSource
  -> Rhino or Node execution
  -> listener final value / standard exception reporting
```

Engine 插件的 Binder/AIDL/R2 contract 没有变化。Host 只增加一种 archive 输入来源:
过去是只读文件字节, 现在也可以是调用者提供的内存字符串。两者进入同一个 archive writer,
输出验证、diagnostic admission、Source Map 解析和缓存验证均不分叉。

## 身份、额度与缓存

每个内存 source 使用两个相互区分的名称:

- `compilationSourceName`: 用户传入的规范文件名, 用于 TypeScript diagnostic 与
  Source Map original source。
- `compilationSourceIdentity`: `autojs://string/<sourceName>`, 用于稳定 project identity,
  不伪造设备文件路径。

源码在 transport 前按严格 UTF-8 编码, 同时检查 UTF-16 code-unit 快速上限、最终 UTF-8
字节上限以及 archive 总输入上限。临时源码 byte array 在请求结束后清零。persistent cache
仍以完整输入 archive、profile、compiler/policy fingerprint 等语义数据判定命中;
稳定 identity 不会使不同源码文本共享错误结果。

## 最终值回传

历史 `LoopedBasedJavaScriptExecution` 调用 loop-based engine 的异步接口后固定返回 `null`,
即使 Rhino 已经求得最后一个表达式的值。T5-2 为每次 loop execution 安装统一 callback:

- `onResult` 保存最近一次执行结果;
- 多次 loop 配置完成后返回最后一次结果;
- `onException` 保持原异常类型传播, 不把失败伪装成 `null`;
- 普通 JavaScript 同样获得正确的最终值, 其脚本选择与执行语义不变。

这使 `engines.execScript` 的 `ScriptExecutionListener.onSuccess(..., result)` 与交互控制台都能
取得 `42`、字符串、`null` 或 Rhino `undefined` 等真实值。

## 交互控制台

`ConsoleView` 的输入区默认隐藏, 只由完整日志页和编辑器日志底部面板显式启用, 因而不会
改变其他复用该控件的页面。交互行为为:

- 仅接收单行 TypeScript expression 或 statement;
- 键盘 Send/Done/Enter 与 Run 按钮共用提交入口;
- 执行期间禁用输入与按钮, terminal callback 后恢复;
- 完整日志页使用当前编辑文件父目录, 底部面板使用当前脚本父目录作为 working directory;
- 输入和结果写入 Global Console, 编译或运行错误继续由全局执行 listener 按标准格式记录;
- 独立 navigation-bar spacer 吸收 bottom inset, 输入栏不会落到系统手势区域下方。

控制台固定以 `console.ts` 运行, 因而使用 Rhino profile。它面向快速表达式和语句求值,
不是跨提交共享 lexical scope 的 REPL; 每次提交都是一个独立脚本执行。

## 诊断与 Source Map

内存源码的 TypeScript error diagnostics 仍转换为 Host `ScriptCompilationException`:
文件名、1-based 行列、severity 与 `TSxxxx` code 均保留, 且 engine 在编译失败时不会创建。

成功 emit 后使用既有 Source Map v3 binding。generated frame 可以使用内部生成名称,
但用户可见 original frame 使用 `compilationSourceName`。因此运行异常显示
`inline-runtime.ts:3`, 不显示 `.generated.js`。

## 兼容性边界

- `engines.execScript` 的公开参数与返回类型没有变化, 文档声明文件无需新增 API。
- 普通字符串 JavaScript 仍建立 `StringScriptSource`, 不触发 compiler plugin。
- declaration source、路径式 name 与不受支持扩展名不会进入 TypeScript 编译。
- `.mts/.cts` 仍要求 Node Runtime Plugin; 不提供 Rhino fallback。
- compiler plugin 不可用时使用现有本地化错误, 不执行 raw TypeScript。
- 交互控制台不保持上一次提交的变量环境, 也不扩大文件系统或插件权限。

## 验证证据

Host 本地回归:

- `:app:compileAppDebugKotlin :app:compileAppDebugJavaWithJavac`
- `:app:compileAppDebugAndroidTestKotlin :app:compileAppDebugAndroidTestJavaWithJavac`
- `:app:testAppDebugUnitTest` 的 T5-2 定向测试, 9/9 通过
- `:app:lintAppDebug`, `BUILD SUCCESSFUL`
- `git diff --check`

Xiaomi 23046RP50C, Android 15 真机使用最终 Host 测试 APK 与已安装的独立 TypeScript
Compiler Plugin。核心执行测试 4/4 通过:

| 用例 | 关键断言 |
|---|---|
| `compilesAndExecutesInMemoryTypeScriptWithItsFinalValue` | archive 编译 emit, Source Map 含原名称, Rhino listener 收到 `42` |
| `compilesAndExecutesInMemoryNodeTypeScript` | `.mts` 选择 Node profile, Node Runtime Plugin 成功执行并输出 marker `:42` |
| `reportsDiagnosticsForInMemoryTypeScriptWithoutExecutingIt` | TS2322 可读, engine 未创建 |
| `mapsInMemoryRuntimeErrorsBackToTheTypeScriptName` | stack 命中 `inline-runtime.ts:3`, 不含 `.generated.js` |

控制台链路另由 `TypeScriptConsoleEvaluatorDeviceTest` 验证输入记录与最终 `=> 42`,
`ConsoleViewStackFrameLinkDeviceTest` 验证单行控件提交、清空及现有 stack link 行为。
