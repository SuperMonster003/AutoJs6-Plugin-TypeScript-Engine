# Node ESM/CJS 双变体

这个模板集包含两个**彼此独立**的可运行 Node 项目：

- [`esm/`](esm/)：`main.mts` → ESM `.mjs`，根 `package.json.type=module`，直接演示
  三次顶层 `await`；
- [`cjs/`](cjs/)：`main.cts` → CommonJS `.cjs`，根
  `package.json.type=commonjs`，把相同异步调用放入 `async main()`。

两者都调用 `toast.showToast`、`app.isInstalled`、`device.isScreenOn` 和
`console.log`，需要 AutoJs6 的 Node.js Runtime 插件。分别复制 `esm` 或 `cjs` 目录，
再从其中的 `project.json` 运行；不要把两个子项目合并，因为当前版本拒绝嵌套
package boundary。

两个 `tsconfig.json` 都由 controlled tsconfig revision 4 在运行时真实消费，并选择
ES2022 `target`/`lib`。module kind、module resolution 和输出位置仍由 NodeNext profile
管理，不能写入配置；真正的 module kind 由入口扩展名和根 `package.json.type` 决定。
revision 4 也允许项目内 `baseUrl`/`paths`，但本模板保留 bare 宿主 bridge import，避免
把 `toast`、`app`、`device` 误写成项目源码别名。

顶层 `await` 只属于 ESM。普通 `.ts` 入口也可在根 `type: module` package boundary 下
获得相同能力并输出 `.js`；`.cts` 或 `type: commonjs` `.ts` 不会被静默包装或换成 ESM。
