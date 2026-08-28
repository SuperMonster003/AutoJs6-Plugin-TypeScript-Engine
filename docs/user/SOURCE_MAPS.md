# StackTrace 与 Source Map 契约

TypeScript Engine Plugin 对每个成功 emit 的模块强制生成一份外部 Source Map v3。
AutoJs6 在执行前把 JavaScript、map 和原始模块身份绑定在同一次 execution scope 中；
不会用生成 JS 的行号冒充 TypeScript 坐标。

controlled tsconfig revision 4 的 `baseUrl`/`paths` alias 会在同一次 TypeScript emit 中
改写为 output-relative runtime specifier；replacement literal 继承原 AST range，因此该
改写不会建立第二份 map，也不会让 diagnostics/stack 丢失原 import 或异常位置。

## 用户可见的定位精度

| 场景 | TypeScript 定位 | column 行为 |
|---|---|---|
| 编译期 diagnostics | 文件 + 行 + 列（有精确 span 时） | 1-based；无精确 span 时可为空 |
| Node 运行时 stack | 文件 + 行 + 列 | 使用 V8 的生成列查询 Source Map；映射后为 1-based 原始列 |
| Rhino 运行时 stack | 文件 + 行 | `column=null`；文本栈省略列，不显示虚构的 `:0` |

因此，同一段源码在 Node 与 Rhino 中看到不同精度是预期行为。Rhino 异常帧没有可靠
的生成列；映射器只能使用该生成行的首个有效 segment，绝不会猜一列。Tasker、定时
任务等后台入口也使用同一份 execution-scoped bindings；宿主包装器造成的行偏移会在
查询 map 前扣除。

## 多文件帧如何选择 map

宿主按以下顺序寻找唯一 binding：

1. 生成文件身份完全匹配；
2. 唯一的最长多段路径后缀匹配；
3. 只剩 basename 的运行时，以唯一 basename 匹配。

任何一级出现歧义都会保持该帧未映射，不会把两个同名 `index.ts` 中的一个随机选中。
与当前 execution 无关的 Rhino/Node 帧保持原样。

Map 解析同样 fail closed：只接受受控 Source Map v3 字段、匹配的生成文件名、空
`sourceRoot`、合法 VLQ mappings，以及每个 emit 模块恰好一个原始 source。损坏或属于
另一个模块的 map 不会用于改写栈。

## 发布 APK 与 `sourcesContent`

开发运行和 APK Builder 都保留 map，才能把错误定位回 TypeScript 文件。发布 APK
中的 map 默认删除 `sourcesContent`，且打包产物不携带 `.ts/.tsx/.mts/.cts` 源文件；
只留下生成代码、位置映射和必要的模块身份。该处理不会改写原项目，也不会反向修改
已有的持久编译缓存。

固定编译 profile 本身使用 `inlineSources=false` 和外部 `sourceMap=true`。即便未来
上游 map 带有合法 `sourcesContent`，发布打包边界仍会剥离它。

## 隐私边界

- compiler Worker 无网络权限，不读取宿主任意路径，只消费宿主冻结并显式发送的项目
  snapshot 与插件自带 declarations。
- 常规编译/性能日志只记录阶段、耗时、计数和稳定状态；当前性能 schema 2
  不记录源码正文、路径、request ID 或错误 message。
- `diagnostics.json` 包含 message、source URI 和 range，不包含整份源码；错误文字仍可能
  暴露用户自己写入的标识符或文件名。
- Source Map 的 `sources` 字段会暴露逻辑路径，mappings 会暴露代码结构。它不是秘密
  存储格式；需要分享 APK/map 做排障时仍应先检查路径信息。
- 发布 APK 默认不含 `sourcesContent` 只能降低源码直接泄露风险，不能把生成 JavaScript
  变成不可逆的保密载体。

## 排障时应提供什么

优先提供稳定错误码、目标 profile、入口扩展名、AutoJs6/插件/WebView 版本，以及
`tools/collect-typescript-issue-evidence.mjs` 生成的 `diagnostics.redacted.json` 与
`typescript-compile-perf` 计时记录。原始 `diagnostics.json` 只留在本地。若必须提供
map，先检查 `sources` 路径；若必须提供源码，应裁剪成最小复现项目。

错误码含义见[错误码目录](ERRORS.md)，配额与诊断坐标编码见
[配置与配额参考](CONFIGURATION.md)，完整采集/复现/关闭流程见
[Issue 分诊手册](../maintenance/ISSUE_TRIAGE.md)。
