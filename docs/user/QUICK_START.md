# 快速上手

本文面向 AutoJs6 TypeScript Engine Plugin `v0.6.0`。插件只负责编译；生成的
JavaScript 仍由 AutoJs6 的 Rhino 或 Node.js 引擎执行。

> 当前 `v0.6.0` 是仓库内正式签名候选。宿主项目发布前，本项目不会创建 GitHub
> Release 或插件市场条目。源码联调可使用仓库构建产物；普通用户请等待宿主与插件
> 的公开下载同时可用，不要从第三方镜像安装同名 APK。

## 1. 准备环境

- Android 7.0 / API 24 或更高版本。
- Chrome/WebView `119` 或更高版本。API 24/25 自带的旧 WebView 不能解析当前
  compiler host，必须先更新 provider。
- AutoJs6 `6.8.0 Alpha7`（build `5253`）或更高兼容版本。
- 与宿主匹配、使用官方证书签名的 TypeScript Engine Plugin。
- 仅在运行 Node 项目时需要 Node.js Runtime 插件；Rhino 项目不需要它。TypeScript
  Engine Plugin 自身也不依赖 Node.js Runtime 才能编译。

安装 APK 后无需单独打开插件。AutoJs6 在运行 TypeScript 前会自动发现、绑定并
预热编译服务。

## 2. 第一个 Rhino 脚本

在 AutoJs6 中新建 `hello.ts`，粘贴以下完整内容：

<!-- verify-s7:rhino:hello.ts -->
```ts
const message: string = "Hello from TypeScript";

console.log(message);
toast(message);
```

保存后点击 AutoJs6 的运行按钮。成功时日志和短暂提示都会显示
`Hello from TypeScript`。不需要手工执行 `tsc`，也不要把编译后的 `.js` 当作项目
入口。

若编译失败，AutoJs6 会先显示结构化诊断而不会执行旧产物；点击带文件位置的诊断
可回到对应 TypeScript 源码。常见问题见[错误码目录](ERRORS.md)。

## 3. 选择 Rhino 或 Node profile

路由由项目类型、入口扩展名和 Node 项目的根 `package.json.type` 共同决定：

| 项目/入口 | 编译 profile | 输出/执行语义 | 额外要求 |
|---|---|---|---|
| 独立 `.ts`，或未声明 `type: "node"` 的项目 `.ts` | Rhino | CommonJS `.js` | 无 |
| 未声明 `type: "node"` 的项目 `.tsx` | Rhino | 原生 TSX DSL → CommonJS `.js` | 文件首部使用 `"ui"` 才进入 UI 模式 |
| Node 项目 `.mts` | NodeNext revision 2 | ESM `.mjs` | Node.js Runtime 插件 |
| Node 项目 `.cts` | NodeNext revision 2 | CommonJS `.cjs` | Node.js Runtime 插件 |
| Node 项目 `.ts` + `package.json` 的 `"type": "module"` | NodeNext revision 2 | ESM `.js` | Node.js Runtime 插件 |
| Node 项目 `.ts` + `package.json` 的 `"type": "commonjs"` | NodeNext revision 2 | CommonJS `.js` | Node.js Runtime 插件 |

最小 Rhino 项目的 `project.json` 如下；缺少 `type: "node"` 就表示 Rhino：

```json
{
  "name": "hello-rhino",
  "main": "main.ts"
}
```

最小 Node 项目的 `project.json` 如下：

```json
{
  "name": "hello-node",
  "type": "node",
  "main": "main.mts",
  "node": {
    "backend": "embedded",
    "timeoutMs": 30000,
    "permissions": ["toast", "app", "device"]
  }
}
```

Node 不支持 `.tsx`。任意 npm 裸包也尚未开放；`toast`、`app`、`device` 等是宿主
提供且有内置声明的受控模块，不是从 npm 下载的包。

Rhino 与 Node 项目根目录的 `tsconfig.json` 都会进入 controlled tsconfig revision 4。
例如，Node 项目可直接选择 ES2022 emit 与标准库：

```jsonc
{
  "compilerOptions": {
    "strict": true,
    "target": "ES2022",
    "lib": ["ES2022"],
    "preserveConstEnums": true,
    "removeComments": true
  },
  "include": ["**/*.ts", "**/*.mts", "**/*.cts"]
}
```

不要在该文件中重复 `module`、`moduleResolution`、`outDir`、`sourceMap`、`noEmit`
或 `types`；这些值由执行 profile/输出契约管理。旧宿主发出的 revision 2 Rhino 配置与
双 profile revision 3 配置仍精确兼容，但旧 revision 不会被当成 revision 4。完整清单见
[配置与配额参考](CONFIGURATION.md)。

多文件项目可以用 `baseUrl`/`paths` 消除深层相对路径：

```jsonc
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  },
  "include": ["main.ts", "src/**/*.ts"]
}
```

此时 `import { run } from "@/run"` 会在冻结的项目 snapshot 内解析，生成代码则自动使用
带真实 JS 后缀的相对路径；不需要在 Rhino 或 Node 运行时另装 alias loader。绝对路径、
`..` 与 snapshot 外目标不会被读取。

## 4. Node 顶层 `await`

Node ESM 项目可以直接在模块顶层等待异步操作。推荐把入口命名为 `main.mts`，并使用
ES2022 配置：

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

await sleep(25);
export const answer: number = 40 + 2;
console.log(answer);
```

运行时插件会收到 ESM `main.mjs` 与配对 Source Map，等待结束后日志显示 `42`。也可以
把入口写成 `main.ts`，但根 `package.json` 必须是 `{"type":"module"}`，即明确的
`type: module` 项目；此时输出为 ESM `.js`。`.cts` 或 `type: commonjs` 的 `.ts` 仍是
CommonJS，不能使用顶层 `await`，应改为 `async` 函数。详细边界见
[配置与配额参考](CONFIGURATION.md#node-顶层-await)。

## 5. Node workspace 子包

同一个 Node 项目可以让不同 workspace 子包各自选择 ESM 或 CommonJS。创建以下目录：

```text
workspace-demo/
├── project.json
├── package.json
├── main.mts
└── packages/
    ├── esm-package/
    │   ├── package.json
    │   └── value.ts
    └── commonjs-package/
        ├── package.json
        └── value.ts
```

`project.json` 选择 Node profile；根 `package.json` 可保持为空对象：

```json
{
  "name": "workspace-demo",
  "type": "node",
  "main": "main.mts"
}
```

```json
{}
```

两个子包的 `package.json` 分别写入 `{"type":"module"}` 和
`{"type":"commonjs"}`。然后创建源码：

```ts
// packages/esm-package/value.ts
export const esmValue: number = 20;
```

```ts
// packages/commonjs-package/value.ts
export const commonJsValue: number = 22;
```

```ts
// main.mts
import { esmValue } from "./packages/esm-package/value.js";
import { commonJsValue } from "./packages/commonjs-package/value.js";

console.log(esmValue + commonJsValue);
```

直接运行 `main.mts`，日志应显示 `42`。两个 `.ts` 文件虽然扩展名相同，却会按最近的
`package.json.type` 分别生成 ESM `.js` 与 CommonJS `.js`。子包只允许位于
`packages/<name>` 或 `packages/@scope/<name>`；任何 boundary 中都不能包含顶层
`scripts`，npm 裸包导入仍未开放。完整路径与配额规则见
[配置与配额参考](CONFIGURATION.md#workspace-package-boundary)。

### Node ESM 循环依赖与 live binding

在 Node ESM 项目中，可以让可变导出经过循环 import 与 re-export 后继续保持 live：

```ts
// state.mts
import { readValue } from "./reader.mjs";
export let value: number = 1;
export function setValue(next: number): void { value = next; }
export function snapshot(): number { return readValue(); }
```

```ts
// reader.mts
import { value } from "./barrel.mjs";
export function readValue(): number { return value; }
```

```ts
// barrel.mts
export { value } from "./state.mjs";
```

```ts
// main.mts
import { snapshot, setValue } from "./state.mjs";

console.log(`initial=${snapshot()}`);
setValue(42);
console.log(`updated=${snapshot()}`);
```

直接运行 `main.mts`，日志应依次显示 `initial=1`、`updated=42`。这条链路要求安装包含
V8 native ESM linker 的 Node.js Runtime；运行结果会报告
`embedded_script.esm_linker=vm_source_text_module`。CommonJS `require(esm)` 仍按既有同步
互操作规则工作。详细边界见
[配置与配额参考](CONFIGURATION.md#node-esm-循环依赖与-live-binding)。

## 6. 使用标准装饰器

TypeScript 6 的标准类/方法装饰器在 Rhino 与 Node profile 中默认可用，不需要在
`tsconfig.json` 中增加开关：

<!-- verify-s7:rhino:decorators.ts -->
```ts
type Constructor = new (...args: any[]) => object;

function announce<T extends Constructor>(
  value: T,
  context: ClassDecoratorContext<T>,
): T {
  context.addInitializer(function (): void {
    console.log(`loaded ${String(context.name)}`);
  });
  return value;
}

function logged<This, Args extends unknown[], Return>(
  value: (this: This, ...args: Args) => Return,
  context: ClassMethodDecoratorContext<This, (this: This, ...args: Args) => Return>,
): (this: This, ...args: Args) => Return {
  return function (this: This, ...args: Args): Return {
    console.log(`call ${String(context.name)}`);
    return value.apply(this, args);
  };
}

@announce
class Greeter {
  @logged
  hello(name: string): string {
    return `Hello, ${name}`;
  }
}

toast(new Greeter().hello("TypeScript"));
```

不要添加 `"experimentalDecorators": true`；它代表另一套 legacy 语义，当前会被
warning-ignore。`emitDecoratorMetadata` 同样不会生效。具体兼容边界见
[配置与配额参考](CONFIGURATION.md#标准装饰器)。

## 7. 字符串 XML UI（`.ts`）

把下面内容保存为 `ui-string.ts` 并直接运行。`"ui"` 必须是文件开头的指令语句；
它不是普通注释。

<!-- verify-s7:rhino:ui-string.ts -->
```ts
"ui";

ui.layout(`
  <vertical gravity="center" padding="24">
    <text id="status" text="TypeScript string UI ready" textSize="20sp" />
    <button id="action" text="Click me" />
  </vertical>
`);

const actionButton: UI.View | null = ui.findView("action");
if (actionButton === null) {
  throw new Error("UI button was not created");
}

actionButton.click(() => {
  console.log("String UI button clicked");
  toast("String UI works");
});
```

## 8. 原生 TSX UI（`.tsx`）

把下面内容保存为 `ui-native.tsx`。TSX 只属于 Rhino profile；它由插件的受控
`__autojs6Tsx` 工厂编译，不是 React，也不需要 React 依赖。

<!-- verify-s7:rhino:ui-native.tsx -->
```tsx
"ui";

ui.layout(
  <vertical gravity="center" padding={24}>
    <>
      <text id="status" textSize="20sp">TypeScript TSX ready</text>
      <button onClick={() => {
        console.log("TSX button clicked");
        toast("Native TSX works");
      }}>Click me</button>
    </>
  </vertical>,
);
```

当前 TSX 事件属性只开放 `onClick`；其他 `on*` 属性会被拒绝。属性值限于字符串、
数字、布尔值，布局必须有一个根节点。完整边界和稳定错误处理见
[错误码目录](ERRORS.md)。

## 9. 从模板继续

仓库的 [`templates/`](../../templates/README.md) 提供三个模板集：

- Rhino 单文件 TSX UI；
- Rhino 多文件相对导入；
- Node ESM/CJS 双变体。

配置项见[配置与配额参考](CONFIGURATION.md)，运行栈的映射精度和隐私边界见
[StackTrace 与 Source Map 契约](SOURCE_MAPS.md)。
