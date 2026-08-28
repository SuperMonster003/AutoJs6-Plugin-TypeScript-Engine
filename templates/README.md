# AutoJs6 TypeScript 项目模板

这里有三个模板集，均面向 TypeScript Engine Plugin `v0.6.0`：

| 模板集 | profile | 入口 | 演示的真实 API |
|---|---|---|---|
| [`rhino-single-ui`](rhino-single-ui/README.md) | Rhino | `main.tsx` | `ui.layout`、`console.log`、`toast` |
| [`rhino-multi-file`](rhino-multi-file/README.md) | Rhino | `main.ts` | revision 4 `@/` 路径别名、`device.summary`、`console.log`、`toast` |
| [`node-esm-cjs`](node-esm-cjs/README.md) | NodeNext revision 2 | `.mts` + `.cts` 两个独立变体 | ESM 顶层 `await`、CJS `async` 函数、`toast.showToast`、`app.isInstalled`、`device.isScreenOn` |

复制某个可运行项目目录到 AutoJs6 的项目目录后，从它的 `project.json` 运行。Node
模板需要 Node.js Runtime 插件；Rhino 模板不需要。

仓库内离线门禁会使用锁定的 TypeScript `6.0.3` 和实际生成的 Rhino/Node declarations
类型检查并 emit 所有模板：

```powershell
.\gradlew :app:verifyS7UserGuidesAndTemplates --offline
```

该门禁证明源码、配置、声明与 JS/map emit 一致，但不能替代宿主 UI 和真机运行。
模板正式状态为“仓库内已交付、离线编译已验证”；宿主发布候选可安装后仍需按原样在
真机完成 S7-4 最终验收。

入门说明见 [`docs/user/QUICK_START.md`](../docs/user/QUICK_START.md)，配置边界见
[`docs/user/CONFIGURATION.md`](../docs/user/CONFIGURATION.md)。
