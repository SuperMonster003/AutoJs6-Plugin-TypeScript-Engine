# T6-2 TSX 运行时十语言提示

状态：2026-08-29 完成。

## 结论

AutoJs6 宿主的 TypeScript TSX 用户可见校验链不再携带 hardcoded English。
`TypeScriptTsxRuntime` 原有 29 条工厂、props、属性、事件、子项、布局树、ID 与
XML 1.0 校验提示全部使用 Android string resource，并覆盖应用支持的十种语言。

本项不改变 TSX 语义、配额、异常类型或编译契约。非法输入仍在原调用点 fail closed，
只是异常文本改为跟随宿主应用语言。

## 运行时接线

宿主新增了最小 `MessageResolver` 边界：调用方提供 `@StringRes` ID 与格式参数，
生产实现通过应用 `Context.getString()` 解析，单元测试则注入不依赖 Android runtime
的确定性 resolver。

消息解析器覆盖完整生命周期：

```text
ui.__autojs6Tsx(...)
        │
        └─ create(args, resolver)
             ├─ tag / props / attribute / child validation
             └─ Value
                  │
ui.layout(...)
        │
        └─ renderOrNull(value, resolver)
             ├─ root / tree / id / XML validation
             └─ RenderedLayout(xml, bindings, resolver)
                                      │
                                      └─ attachClickHandlers(...)
                                           └─ late event-target validation
```

`RenderedLayout` 保留同一个 resolver，因此 inflate 后才发生的 click target 缺失提示
也不会退回英文常量。`UI.__autojs6Tsx` 与 `UI.layout` 两个宿主入口显式传入 resolver，
不存在可绕过资源化的旧 overload。

## 资源覆盖

十种语言及 Android 目录如下。Android 默认目录必须提供英语 fallback，因此英语有
`values` 与 `values-en` 两份内容完全相同的资源；这形成 10 种语言、11 个资源目录。

| 语言 | Android resource 目录 |
|---|---|
| English | `values`, `values-en` |
| العربية | `values-ar` |
| Español | `values-es` |
| Français | `values-fr` |
| 日本語 | `values-ja` |
| 한국어 | `values-ko` |
| Русский | `values-ru` |
| 简体中文 / zh-Hans | `values-zh` |
| 繁體中文（香港）/ zh-Hant-HK | `values-zh-rHK` |
| 繁體中文（台灣）/ zh-Hant-TW | `values-zh-rTW` |

每个目录都新增同一组 29 个按名称排序的 `error_typescript_tsx_*` 资源。覆盖面包括：

- 工厂参数、intrinsic tag、fragment 与 props 类型；
- XML attribute 名称、scalar/nullish/finite-number 值、属性数量；
- `onClick` 类型、不支持的事件 prop 与 inflate 后 event target；
- 子数组大小/深度、子项类型、文本控件限制与 text attribute 冲突；
- root 数量、树深、元素数、XML byte 上限、ID 合法性与重复；
- XML 1.0 非法文本。

## zh-Hans / English 人工校对

29 对默认英语与简体中文已逐条核对，重点检查技术 token 不被误译、`%1$s` 占位符
保持一致、错误条件与原实现语义相同，以及新增中文使用项目约定的 ASCII 标点。代表性
条目如下：

| 资源 | English | zh-Hans |
|---|---|---|
| `error_typescript_tsx_factory_arguments` | The TypeScript TSX factory requires a tag and props argument. | TypeScript TSX 工厂函数需要 tag 和 props 参数. |
| `error_typescript_tsx_event_unsupported` | TypeScript TSX event prop `%1$s` is not supported. | 不支持 TypeScript TSX 事件 prop `%1$s`. |
| `error_typescript_tsx_root_count` | A TypeScript TSX layout must resolve to exactly one root element. | TypeScript TSX 布局必须恰好解析为一个根元素. |
| `error_typescript_tsx_invalid_xml_text` | TypeScript TSX contains text that is not valid XML 1.0. | TypeScript TSX 包含不符合 XML 1.0 的文本. |
| `error_typescript_tsx_text_children_leaf_only` | TypeScript TSX text children are supported only by leaf text controls. | TypeScript TSX 文本子项仅受叶级文本控件支持. |

XML 中实际使用 `\"%1$s\"` 显示动态值；上表为便于阅读省略引号转义。

## 防漂移与验收

宿主 `TypeScriptTsxRuntimeTest` 新增资源覆盖测试，并与既有行为测试一起形成以下门禁：

- 从生产源码提取所有 `R.string.error_typescript_tsx_*` 引用，必须恰好为 29 个；
- 11 个资源目录必须各自包含完全相同的 29 个名称；
- `values` 与 `values-en` 的 29 对值必须逐项完全相同；
- 生产源码不得再出现带引号的 `TypeScript TSX` 诊断文本；
- fake resolver 断言动态事件名与无参数 XML 错误均经过正确 resource ID；
- 既有 fragment、XML escaping、generated event ID、duplicate ID、root 与容器文本行为
  保持不变。

实际验收结果：

| 检查 | 结果 |
|---|---|
| 11 个 `strings.xml` XML 解析 | PASS |
| 各目录资源数与名称排序 | 29/29，全部 PASS |
| 默认英语 / `values-en` 逐项相等 | PASS |
| TSX runtime quoted diagnostic 扫描 | 0 条残留 |
| `:app:compileAppDebugKotlin` | PASS |
| `TypeScriptTsxRuntimeTest` | 4/4 PASS |
| 宿主十语言 changelog JSON 与 Markdown/README 生成 | 10/10 PASS |
| TypeScript Engine `gradlew check --offline` | PASS，146 actionable tasks |

对应宿主提交：

| 提交 | 内容 |
|---|---|
| `b4a2d0cd9` | TSX runtime resolver、29 条十语言资源与防漂移测试 |
| `b8fe7367d` | 十语言 changelog 及生成的 README/CHANGELOG |

插件自身的市场说明、安装提示和编译失败提示在 S7-6 前置工作中已经覆盖相同十语言；
本次关闭最后一处明确记录的宿主 TSX 运行时缺口，因此 Roadmap T6-2 验收完成。
