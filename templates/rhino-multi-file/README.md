# Rhino 多文件项目

这个模板演示 Rhino profile 的项目 snapshot 与 revision 4 `@/` 路径别名。入口通过
`@/lib/device-report` 和 `@/lib/format-report` 调用两个本地模块，
读取 `device` 信息，再通过 `console` 和 `toast` 输出结果；无需 Node.js Runtime 插件。

运行方式：

1. 把整个 `rhino-multi-file` 目录复制到 AutoJs6 项目目录。
2. 从该目录的 `project.json` 运行。
3. 日志应显示屏幕尺寸、Android API level 和设备摘要，并出现 toast。

不要只复制 `main.ts`：`lib/` 必须在执行开始前已经位于项目 snapshot 内。
`tsconfig.json` 将 `baseUrl` 设为项目根并把 `@/*` 映射到 `*`；插件只在该 snapshot
中解析，并把生成 JavaScript 的别名改写为显式相对 `.js` 路径，Rhino 不需要额外 loader。
