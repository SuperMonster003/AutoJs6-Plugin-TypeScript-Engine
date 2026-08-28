# T1-5 Node 顶层 `await`

状态：**完成（2026-08-26）**。NodeNext revision 2 已在 `.mts` 与根
`package.json` 声明 `type: module` 的 `.ts` 项目中完成顶层 `await` 的编译、Source
Map、公开 Binder 与 AutoJs6 + Node Runtime 真机执行闭环。

## 结论

T1-5 复用现有 profile 与受控配置，不发布 Node profile revision 3，也不发布
config policy revision 5：

- module kind 与 module resolution 继续固定为 `NodeNext`。它负责根据 `.mts/.cts`
  扩展名和 `package.json.type` 判定 ESM/CommonJS；把 `module` 改成普通 `ES2022` 会丢失
  Node package boundary 语义。
- controlled tsconfig revision 4 已允许 Node profile 显式选择 `target: ES2022` 与
  `lib: [ES2022]`。这就是当前推荐的顶层 `await` 配置。
- config-none 的 NodeNext revision 2 默认 target 是 ES2018；ES2018 已高于 TypeScript
  对顶层 `await` 的最低 target 要求，因此兼容路径本身也无需滚动 profile 身份。
- 顶层 `await` 是否成立最终由源码是不是 ESM 决定，而不是由一个新的 profile 编号决定。
  `.mts` 天然是 ESM；普通 `.ts` 必须由根 `package.json` 的 `"type":"module"` 明确
  归类为 ESM。

因此本项是现有 NodeNext/ES2022 能力的产品化闭环。仅为了改默认值或名称而新增
revision，会让协议分派、结果 authority、缓存身份与宿主兼容矩阵增加一套没有新增功能
的分支。

## 用户语义

推荐的 `.mts` 项目配置如下：

```jsonc
// tsconfig.json
{
  "compilerOptions": {
    "strict": true,
    "target": "ES2022",
    "lib": ["ES2022"]
  },
  "files": ["main.mts"]
}
```

```ts
// main.mts
const sleep = (milliseconds: number): Promise<void> =>
  new Promise((resolve): void => {
    setTimeout((): void => resolve(), milliseconds);
  });

await sleep(4);
export const answer: number = 40 + 2;
console.log(answer);
```

插件保留顶层 `await` 并产出 `main.mjs` 与 `main.mjs.map`，随后由 AutoJs6 的 Node
Runtime 插件执行。项目入口也可以是普通 `main.ts`，但根目录必须存在：

```json
{
  "type": "module"
}
```

该形态产出 ESM `main.js` 与 `main.js.map`。`project.json` 仍需声明
`"type":"node"`；`package.json.type` 只决定 Node `.ts` 的模块格式，不能替代项目
profile 路由。

## 支持边界

| 源码与 package boundary | 输出 | 顶层 `await` |
|---|---|---|
| `.mts` | ESM `.mjs` | 支持 |
| `.ts` + 根 `type: module` | ESM `.js` | 支持 |
| `.cts` | CommonJS `.cjs` | 不支持 |
| `.ts` + 根 `type: commonjs` | CommonJS `.js` | 不支持 |

CommonJS 入口需要把异步流程放入 `async` 函数；插件不会把顶层 `await` 包进匿名函数，
也不会在 ESM 与 CommonJS 之间静默换轨。Node `.tsx` 与任意 caller `node_modules` 仍是
现有边界；受控 workspace 子包 boundary 已由后续
[T1-6](NESTED_PACKAGE_BOUNDARIES_T1_6.md) 开放。

## 身份与编译证据

当前身份保持不变：

- profile：NodeNext revision 2；
- `configPolicyRevision=4`；
- policy fingerprint：
  `4a9a5e70150f81e1ec9a5e2f749cc081f93f207f092300c96a79abfdcc152dab`；
- NodeNext + ES2022/ES2022 effective fingerprint：
  `b77bfac45d80ad99a9a65168ecd97a10607f0574189e521e3355eb080b4a97ff`。

`verifyT1NodeTopLevelAwait` 直接加载 production `compiler-worker.js`、锁定的 TypeScript
6.0.3、正式 common/Node declaration layers 与 revision 4 policy。它调用 Worker 的
`nodeRevisionTwoImpliedModuleFormat`、`createNodeRevisionTwoSourceFile` 和受控配置解析，
在同一个真实 incremental `Program` 中验证：

- `/sources/main.mts` 分类为 ESM，并产出 `/outputs/main.mjs + .map`；
- `/sources/package-entry.ts` 在根 `type: module` 下分类为 ESM，并产出
  `/outputs/package-entry.js + .map`；
- 两份 JavaScript 都保留 `await sleep(4)` 与 ESM export，不出现 CommonJS
  `exports.answer`；
- 两份 Source Map v3 都指回唯一的原始 TypeScript 文件；
- 生成模块由真实桌面 Node dynamic import 执行，两者均返回 `answer=42`。

TypeScript 6.0.3 当前生成物 SHA-256 也作为输出漂移证据记录：`.mts` 为
`42e5072fa4b889fa3cf798710e77d258d4558da98d1b9563d5e50c068a70071f`，
`type: module` `.ts` 为
`8ca9e1e6f348959ed77f54471e1e23dece52762d26517be9a7f02bea5e83539d`。

该专项任务依赖 Kotlin 单元测试、AndroidTest Kotlin 编译和公开 API 测试，并已挂入
`verifyCompilerSmoke`，所以根项目 `check --offline` 会持续防止 T1-5 回退。

## Android 与宿主真机证据

在 Xiaomi 23046RP50C、Android 15 / API 35、Android System WebView
`130.0.6723.86` 上，插件公开 Binder 用例
`nodeTopLevelAwaitCompilesMtsAndTypeModuleTsThroughPublicBinder` 把 `.mts` 与
`type: module` `.ts` 放入同一个正式 input archive，核对 package metadata inventory、
NodeNext/ES2022 effective fingerprint、`.mjs/.js`、配对 Source Map 与 ESM generated
mapping，结果为 `Time: 11.53`、`OK (1 test)`、`INSTRUMENTATION_CODE: -1`。

同一设备上的 AutoJs6 `6.8.0 (5276)` 与 Node Runtime 插件 `1.2.0` 又运行宿主用例
`executesNodeTopLevelAwaitForMtsAndTypeModuleTs`。该用例创建两个独立 Node 项目，分别以
`.mts` 和根 `type: module` `.ts` 为入口，实际经过执行前编译、precompiled-only handoff
与 Node Plugin engine；两次 `await sleep(25)` 后都输出 `42`。结果为
`Time: 17.071`、`OK (1 test)`、`INSTRUMENTATION_CODE: -1`。

Binder service 的职责仍只是编译并返回产物，不在插件进程内执行 JavaScript；最后一条
宿主用例才是生成 ESM 已由真实 Android Node Runtime 接受并执行的端到端证据。
