# Rhino 单文件 TSX UI

这是一个无需 Node.js Runtime 插件的 Rhino 项目。`main.tsx` 展示原生 TSX 布局、
`ui.layout`、设备宽度、日志和 toast；它不是 React 项目。

运行方式：

1. 把整个 `rhino-single-ui` 目录复制到 AutoJs6 项目目录。
2. 从该目录的 `project.json` 运行。
3. 页面出现后点击按钮；日志应出现 `TSX template button clicked`，并显示 toast。

`"ui"` 是入口的首条指令。当前 TSX 事件仅支持 `onClick`，其他边界见
[`docs/user/QUICK_START.md`](../../docs/user/QUICK_START.md)。
