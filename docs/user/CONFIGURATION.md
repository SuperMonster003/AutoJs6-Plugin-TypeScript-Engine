# 配置与配额参考

本文描述 `v0.6.0` 当前源码的公开行为。关键词“拒绝”表示编译 fail closed：不会退回
正则 stripping、不会静默改成另一个 module kind，也不会运行上一次的 JavaScript。

## 配置作用域与协议身份

受控 `tsconfig.json` revision 4 同时作用于当前 Rhino 与 NodeNext profile。配置必须位于
项目根目录并命名为 `tsconfig.json`；存在配置时，公开请求使用 `configKind="tsconfig"`。
项目没有该文件时使用 profile 默认值，公开请求使用 `configKind="none"`。

当前策略身份：

- `configSchemaRevision=2`
- `configPolicyRevision=4`
- policy fingerprint
  `4a9a5e70150f81e1ec9a5e2f749cc081f93f207f092300c96a79abfdcc152dab`

revision 4 是 append-only 升级。revision 3 请求仍按 20 个布尔项及 `target`/`lib`
的原始语义和指纹执行，不会被重新解释为带路径别名的 revision 4：

- `configPolicyRevision=3`
- policy fingerprint
  `1f77d37080466f2752176fb5aa1b96e9a3f3dbdc89b853f30b4a0fed4313e90a`

旧宿主已内联的 Rhino revision 2 请求也仍按原始
18 个布尔项和原始指纹执行，结果与缓存身份不变：

- `configPolicyRevision=2`
- policy fingerprint
  `64ef1cf36d6531f84088cec49127e6407b107ab86eeaf7f4512358fd1343598b`
- 默认 effective-options fingerprint
  `5b529b507e94fafe30ccce6de4d69f89604bc0f1430b7b4ee8f12259fe03a807`

revision 2 的受控配置仅属于 Rhino。Node 项目携带 revision 2 配置身份会以 contract
mismatch 拒绝，不会把它解释成 revision 3/4，也不会忽略后继续编译。revision 3 与
revision 4 均可用于当前两个 profile。

## Profile 固定语义

两个 profile 都使用 TypeScript `6.0.3`、外部 Source Map、
`noEmitOnError=true`，并把输出限制在 `/outputs`。module kind、解析算法、输出位置和
Source Map 形式仍由 profile 管理；`target` 是 revision 3 起有界开放的例外，
`baseUrl`/`paths` 则是 revision 4 新增的项目 snapshot 内解析能力。

| profile | 入口 | module / resolution | `target` 范围 | 默认 `target` / `lib` |
|---|---|---|---|---|
| Rhino revision 2 | `.ts`、`.tsx` | CommonJS / Node10 | 仅 `ES2018` | `ES2018` / `ES2018` |
| NodeNext revision 2 | `.ts`、`.mts`、`.cts` | NodeNext / NodeNext | `ES2018`–`ES2022` | `ES2018` / `ES2018` |

Node 的 `.mts` 始终产出 ESM `.mjs`，`.cts` 始终产出 CommonJS `.cjs`；`.ts` 的
`.js` 语义仍由项目根 `package.json.type` 决定。修改 `target` 或 `lib` 不会改写这些
module-kind 规则。

## Compose TSX 的可用条件

Compose TSX 对应 TypeScript Engine `0.6.4`, AutoJs6 `6.8.0` / 构建号 `5319`
或以上, 以及 Compose UI `1.1.0` 本地开发预览. 两个插件都需要已安装, 已启用,
并通过宿主的信任与兼容性检查. 普通 JavaScript Compose 工厂 API 的最低宿主仍为
`5316`; 仅安装新版渲染器不会给旧宿主增加 TSX 接入.

将源码保存为 `.tsx`, 通过 Rhino 的正常脚本入口运行. UI 页面在源码开头保留
`"ui";`, 非 UI 脚本使用 `compose.floaty` 并授予宿主悬浮窗权限. Node profile
不接受 `.tsx`. TypeScript 编译错误仍然 fail closed, 不会把原始 TSX 当作普通
JavaScript 执行, 也不会回退到先前的编译结果.

```tsx
"ui";

let count = compose.state(0);
compose.mount(() => (
    <>
        <compose.Column padding={16} spacing={8}>
            <compose:Text key="count" text={'Count: ' + count.value} />
            <compose.Button key="increment" onClick={() => { count.value++; }}>
                Increment
            </compose.Button>
        </compose.Column>
    </>
));
```

`<compose.Column>` 保留当前脚本的真实工厂引用, `<compose:Column>` 保留目录名称.
小写 `<vertical>`, `<text>`, `<button>` 仍使用已有 XML UI 语义. 两种 UI 的节点不能
混在同一树中, 错误为 `INVALID_ARGUMENT`; Compose 渲染失败保留上一个成功帧.
任意用户函数不会作为 Compose 函数组件执行. Fragment 只在消费位置展开, 不自动
创建容器; Compose 挂载根需要恰好一个节点, 单节点插槽最多一个节点.

Rhino profile 继续使用 classic JSX 的 `__autojs6Tsx` 与 `__autojs6TsxFragment`
宿主入口. 不需要配置 JSX factory 或引入 React; `jsx`, `jsxFactory`,
`jsxFragmentFactory` 和 React runtime 选项仍不作为用户可改写的编译逃生门.
本次配套为真实 Rhino 声明层增加 Compose 模块和 TSX 声明, 保留原有 TypeScript
编译器, profile 与 Binder 协议. 声明由插件自身供应, 用户不需要在脚本中编写
`declare const compose: any` 或复制测试声明来通过编译.

完整组件, 插槽, 回调与生命周期说明见
[Compose UI 模块参考](https://docs.autojs6.com/#/compose).

与已有 XML UI 互嵌需要 TypeScript Engine `0.6.5`, AutoJs6 `6.8.0` / `5320`
及支持 `android-view-interop-v1` 的 Compose UI `1.1.0` 渲染器. XML 树中的空
`<compose>` 是容器, 用 `compose.attach(container, nodeOrRender)` 挂入 Compose
内容. Compose 树中的 `compose.AndroidView` 则借用原生 View, 或接收在主线程
执行的 View 工厂. 工厂可以返回 `ui.inflate` 创建的 XML TSX 控件:

```tsx
"ui";

const count = compose.state(0);
ui.layout(<vertical>
    <text text="XML header" />
    <compose id="slot" h={240} />
</vertical>);
const slot = ui.findView('slot');
if (slot === null) throw new Error('Missing Compose container');
const native = <compose.AndroidView view={() => ui.inflate(
    <button text="Increment" textAllCaps={false} onClick={() => { count.value++; }} />
)} />;
compose.attach(slot, () => <compose.Column>
    <compose.Text text={'Count: ' + count.value} />
    {native}
</compose.Column>);
```

同一原生 View 不能同时属于多个节点或会话, 也不能从已有父容器中被强行移走.
关闭会话会卸下借用的 View, 不清空其监听器或销毁调用方的资源. XML 原生控件的
回调继续使用原有 UI 事件规则. 这两个桥接点之外, 直接混合 XML 与 Compose 节点
仍返回类型化错误. `<compose>` 不能带 XML 子节点, AndroidView 不能带 Compose
子节点; JSX 表达式的全局类型精度有限, 宿主仍执行这些运行时校验.

## 可直接使用的示例

Rhino 示例：

```jsonc
{
  // JSONC 注释和尾随逗号可用
  "compilerOptions": {
    "strict": true,
    "noUnusedLocals": true,
    "removeComments": true,
    "target": "ES2018",
    "lib": ["ES2018"],
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    },
  },
  "include": ["main.ts", "src/**/*.ts"],
  "exclude": ["src/generated/**"],
}
```

Node 示例：

```jsonc
{
  "compilerOptions": {
    "strict": true,
    "exactOptionalPropertyTypes": true,
    "preserveConstEnums": true,
    "target": "ES2022",
    "lib": ["ES2022"]
  },
  "files": ["main.cts"]
}
```

解析器接受 UTF-8 BOM、JSONC 注释和尾随逗号；重复键会被拒绝，避免同一配置在
不同解析器中产生两种含义。

## 顶层键与文件选择

| 顶层键 | 行为 |
|---|---|
| `$schema` | 接受，仅作编辑器提示，不改变编译器身份 |
| `compilerOptions` | 应用下文列出的 20 个布尔项以及 `target`、`lib`、`baseUrl`、`paths` |
| `files` | 选择精确文件；与 `include` 取并集 |
| `include` | 按项目逻辑路径匹配；省略时默认为 `**/*` |
| `exclude` | 只过滤 `include`，不会移除 `files` 显式列出的文件 |
| `extends`、`references`、`watchOptions`、`typeAcquisition`、`compileOnSave` | `CONFIG_UNSUPPORTED_FEATURE`，fail closed |
| 其他未知顶层键 | warning `910001` 后忽略 |

入口文件必须落在最终选择集合中，否则返回 `CONFIG_ENTRY_NOT_SELECTED`。选择结果为空则
返回 `CONFIG_NO_INPUTS`。选择器在验证、标准化和去重**之前**计数。

配置预算：原始文件最多 48 KiB；`files`、`include`、`exclude` 共最多 512 个选择器；
每个解码后或 NFC 标准化后的选择器最多 4096 UTF-8 bytes；路径别名最多 128 个 key、
每个 key 最多 8 个 target、合计最多 512 个 target，单个 pattern 最多 4096 UTF-8
bytes；JSON 最大深度 16、最大 4096 个值节点。

## 20 个可生效布尔项

`inherit strict` 表示未显式填写时跟随 `strict`；其余默认值不会因为某个未知选项而
改变。

| 选项 | 默认值 | 说明 |
|---|---:|---|
| `strict` | `true` | strict 家族总开关 |
| `noImplicitAny` | inherit `strict` | 禁止隐式 `any` |
| `strictNullChecks` | inherit `strict` | 区分 `null`/`undefined` |
| `strictFunctionTypes` | inherit `strict` | 严格检查函数参数变型 |
| `strictBindCallApply` | inherit `strict` | 严格检查 `bind`/`call`/`apply` |
| `strictPropertyInitialization` | inherit `strict` | 检查类属性初始化；要求 `strictNullChecks=true` |
| `strictBuiltinIteratorReturn` | inherit `strict` | 严格检查内置迭代器返回值 |
| `noImplicitThis` | inherit `strict` | 禁止隐式 `any` 的 `this` |
| `useUnknownInCatchVariables` | inherit `strict` | catch 变量默认为 `unknown` |
| `noUnusedLocals` | `false` | 报告未使用的局部声明 |
| `noUnusedParameters` | `false` | 报告未使用的参数 |
| `exactOptionalPropertyTypes` | `false` | 精确可选属性；要求 `strictNullChecks=true` |
| `noImplicitReturns` | `false` | 检查并非所有分支都返回值 |
| `noFallthroughCasesInSwitch` | `false` | 检查 switch case 贯穿 |
| `noUncheckedIndexedAccess` | `false` | 索引访问结果加入 `undefined` |
| `noImplicitOverride` | `false` | 覆盖成员必须显式写 `override` |
| `noPropertyAccessFromIndexSignature` | `false` | 索引签名属性要求方括号访问 |
| `preserveConstEnums` | `false` | emit 时保留 `const enum` 声明，同时仍内联成员值 |
| `removeComments` | `false` | 从生成 JavaScript 中移除普通源码注释；外部 Source Map 注释仍保留 |
| `skipLibCheck` | `false` | 跳过 `.d.ts` 类型检查；不会跳过用户源码 |

非法组合不会由 TypeScript 自行猜测：例如
`exactOptionalPropertyTypes=true` 且 `strictNullChecks=false` 会返回
`CONFIG_INVALID_VALUE`。

## `target` 与 `lib`

| 选项 | 类型与默认值 | Rhino | NodeNext |
|---|---|---|---|
| `target` | ES 年份字符串，不区分大小写；默认 `ES2018` | 仅 `ES2018` | `ES2018`、`ES2019`、`ES2020`、`ES2021`、`ES2022` |
| `lib` | 非空 ES 年份字符串数组，不区分大小写；省略时跟随 `target` | 仅 `ES2018` | 可从 `ES2018`–`ES2022` 中选择一个或多个 |

`lib` 会去重、排序并规范化为 `lib.es20xx.d.ts`。它只开放锁定 TypeScript
distribution 中的 ECMAScript 标准库，不会引入 DOM、WebWorker、第三方或项目外声明。
数组中的每一项只需属于所选 profile 的白名单，不要求小于或等于 `target`；因此
`target` 与 `lib` 可以表达“较旧 emit 语法 + 较新运行时 API”的常见组合。

Rhino 使用 `ES2019`–`ES2022` 的 `target` 或 `lib` 会返回
`CONFIG_INVALID_VALUE`；Node 使用 `ES2023`、空数组、非字符串元素也会返回同一码。

## Node 顶层 `await`

NodeNext revision 2 的 ESM 入口支持顶层 `await`。推荐在 controlled tsconfig
revision 4 中显式选择 ES2022：

```jsonc
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
// main.mts -> main.mjs
const sleep = (milliseconds: number): Promise<void> =>
  new Promise((resolve): void => {
    setTimeout((): void => resolve(), milliseconds);
  });

await sleep(4);
export const answer: number = 42;
```

位于项目根的普通 `.ts` 也可以使用同一写法，但项目根 `package.json` 必须声明
`{"type":"module"}`，即 `type: module` package boundary；输出是 ESM `.js`。workspace
子包内的普通 `.ts` 改由最近的受控 `package.json.type` 决定格式。`.mts` 不依赖 package
type，始终输出 ESM `.mjs`。这些形态都会保留 ESM 语义并生成配对 Source Map，最终由
AutoJs6 的 Node Runtime 插件执行。

`.cts` 与根 `type: commonjs` 下的 `.ts` 是 CommonJS，不能使用顶层 `await`；请改为
显式 `async` 函数。不要在 `tsconfig.json` 中写入 `module` 或 `moduleResolution` 来
强行切换格式，这两项继续由 NodeNext profile 管理。完整设计和真机证据见
[T1-5 Node 顶层 await](../architecture/NODE_TOP_LEVEL_AWAIT_T1_5.md)。

## Node ESM 循环依赖与 live binding

Node ESM 入口和 dynamic `import()` 使用 Node Runtime 的 V8 native linker。`.mts` 以及
最近 `package.json` 为 `type: module` 的 `.ts` 可以组成循环图，并保留可变导出与
re-export 的 live binding：

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

修改 `value` 后，任何通过 `reader.mts` 或 `barrel.mts` 读取的 binding 都会立即看到新值；
Runtime 不再把 named import 改写成一次性属性快照。`.mts` 源码中的运行时 specifier 应写
生成扩展名 `.mjs`，TypeScript 会在编译期解析到对应 `.mts`；对应入口应依次得到
`initial=1` 与 `updated=42`。

这项保证覆盖 ESM→ESM 图。CommonJS `require(esm)` 仍属于同步互操作兼容路径，不承诺
把 CommonJS 局部变量变成 ESM live binding。运行结果的原生诊断应包含
`embedded_script.esm_linker=vm_source_text_module` 与
`embedded_script.esm_live_bindings=true`。完整实现边界和真机证据见
[T1-7 Node ESM live binding](../architecture/NODE_ESM_LIVE_BINDINGS_T1_7.md)。

## `baseUrl` 与 `paths`

revision 4 在两个 profile 中开放项目内路径别名。最常见的配置是：

```jsonc
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"],
      "@feature/*": ["src/features/*"]
    }
  }
}
```

```ts
import { formatReport } from "@/format-report";
import { raise } from "@feature/fault";
```

`paths` 存在而省略 `baseUrl` 时，`baseUrl` 默认为项目根 `.`。`baseUrl` 也可以是安全的
项目相对目录，例如 `src`；它不能是绝对路径、不能含 `..`，也不能通过反斜杠、冒号、
查询串或 fragment 伪装成项目外位置。

alias key 必须是 bare specifier pattern，不能以 `.` 或 `/` 开头；key 与每个 target
都最多包含一个 `*`，且 wildcard 数必须一致。target 相对于 effective `baseUrl`，按数组
顺序尝试；重复 target 会在 canonical authority 中去重。匹配顺序为精确 key 优先，随后
选择 prefix 最长、再 suffix 最长的 wildcard key。省略扩展名时，Rhino 依次尝试
`.ts/.tsx` 与目录 `index`，Node 依次尝试 `.ts/.mts/.cts` 与目录 `index`。

解析只读取执行开始时已经接纳的 `/sources` snapshot，不会访问 Android 文件系统或网络。
命中 alias 但目标不存在时，TypeScript 产生标准 `TS2307`，位置仍指向原始 import；没有
命中 `paths` 的 bare specifier 会先尝试 `baseUrl`，未找到后再交给既有宿主模块/Node
package resolver。`#imports`、相对 import 和绝对形式不参与 `paths` 匹配。

TypeScript 自身不会为运行时改写 `paths` specifier，因此插件在 emit 前用固定的内部
transformer 把已解析别名改成目标 output 的显式相对 specifier，并带上 `.js/.mjs/.cjs`
后缀。该 transformer 不能由项目注入；Source Map 仍由同一次 TypeScript emit 直接生成，
编译诊断与运行异常都继续映射到原 `.ts/.tsx/.mts/.cts`。

## 标准装饰器

Rhino 与 NodeNext 都直接支持 TypeScript 6 的标准 Stage-3 装饰器，不需要任何
`compilerOptions` 开关。类/方法装饰器可以使用官方上下文类型与 initializer：

```ts
type Constructor = new (...args: any[]) => object;

function registered<T extends Constructor>(
  value: T,
  context: ClassDecoratorContext<T>,
): T {
  context.addInitializer(function (): void {
    console.log(`registered ${String(context.name)}`);
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

@registered
class Greeter {
  @logged
  hello(name: string): string {
    return `Hello, ${name}`;
  }
}
```

生成代码使用 `__esDecorate`/`__runInitializers` 标准 helper，Source Map 仍指回原 TS。
不要添加 `"experimentalDecorators": true` 来“启用”上述语法：该键选择的是旧版
legacy decorator dialect。revision 4 会以 warning `910002` 忽略
`experimentalDecorators` 与 `emitDecoratorMetadata`，所以最终仍是标准语义，且不会
生成 metadata。需要旧 Angular/Nest 风格 decorator 或 `reflect-metadata` 的项目暂未
支持；当前完整决策与双 profile 验收见
[T1-4 标准装饰器](../architecture/STANDARD_DECORATORS_T1_4.md)。

## 被拒绝或忽略的 compilerOptions

### Profile 冲突：fail closed

以下选项由 profile 冻结。revision 4 项目中出现它们会返回
`CONFIG_PROFILE_CONFLICT`：

`allowJs`、`allowSyntheticDefaultImports`、`alwaysStrict`、`checkJs`、
`esModuleInterop`、`forceConsistentCasingInFileNames`、`ignoreDeprecations`、`module`、
`moduleDetection`、`moduleResolution`、`newLine`、`noEmitOnError`、`outDir`、
`resolveJsonModule`、`rootDir`、`sourceMap`、`useDefineForClassFields`。

这些键会改变可执行语法、模块解析、输出位置或 Source Map 契约，因此不能只“尽量
应用”。`target` 已从 revision 3 起按 profile 范围开放，不在此集合内。

### 解析/权限逃逸：fail closed

以下选项返回 `CONFIG_UNSUPPORTED_FEATURE`：

`customConditions`、`moduleSuffixes`、`plugins`、`rootDirs`、`traceResolution`、
`typeRoots`、`types`。

它们可能让解析器离开已冻结的项目 snapshot、注入额外代码或改变受信声明集合。
`lib` 已改为只接受前文的内置 ES 子集；`baseUrl`/`paths` 只接受上一节的受控项目内形状。

### 输出拓扑变化：fail closed

以下选项同样返回 `CONFIG_UNSUPPORTED_FEATURE`：

`declaration`、`declarationDir`、`declarationMap`、`emitBOM`、
`emitDeclarationOnly`、`importHelpers`、`incremental`、`inlineSourceMap`、
`inlineSources`、`mapRoot`、`noEmit`、`noEmitHelpers`、`outFile`、`sourceRoot`、
`tsBuildInfoFile`。

公开输出必须保持“每个可执行模块一份 JS + 一份外部 map”的拓扑。
`removeComments` 只改变 JavaScript 文本，不改变 artifact 数量或路径，因此 revision 3+
允许它生效。

### Transformer：不执行

`customTransformer`、`customTransformers`、`transformers` 返回
`CONFIG_UNKNOWN_PROPERTY`。`before`、`after`、`afterDeclarations` 等未分类键会产生
warning `910002` 后被忽略；这**不代表**任何 transformer 已获授权或会执行。

除 20 个布尔项、`target`、`lib`、`baseUrl` 与 `paths` 外，其他已知或未知 compiler
option 默认都是 warning `910002` 后忽略。warning 最多逐项报告 64 条，超出部分以
汇总诊断表示。
不要依赖被忽略键产生任何效果。

## project.json、package.json 与 module kind

### Rhino 项目

```json
{
  "name": "rhino-project",
  "main": "main.ts"
}
```

没有 `"type": "node"` 时为 Rhino。入口可以是 `.ts` 或 `.tsx`；相对导入仍必须落在
执行开始时冻结的项目 snapshot 内。

### Node 项目

```json
{
  "name": "node-project",
  "type": "node",
  "main": "main.ts",
  "node": {
    "backend": "embedded",
    "timeoutMs": 30000,
    "permissions": ["toast", "app.query", "device"]
  }
}
```

对 Node profile：

- `.mts` 始终按 ESM 编译为 `.mjs`；
- `.cts` 始终按 CommonJS 编译为 `.cjs`；
- `.ts` 必须由源码所在的最近 `package.json` 中 `"type": "module"` 或
  `"type": "commonjs"` 明确决定 `.js` 的语义；项目根源码使用根 metadata；
- 不允许 `.tsx`；
- 允许 `packages/<name>/package.json` 与 `packages/@scope/<name>/package.json` 建立
  workspace 子包边界；其他目录中的第二个 `package.json` 仍会被拒绝；
- workspace boundary 的顶层 `scripts` 仍会拒绝；`node_modules` 内的 package metadata
  则作为不可执行的依赖层字节接收，任何 install/lifecycle script 都不会运行；
- T2-2/T2-4 已把 `node_modules` framed 到只读 Worker VFS，并开放 npm bare/scoped
  package、子路径、`exports`、package declarations、`typesVersions` 与 installed
  `@types` 解析；
- 根 `tsconfig.json` 由 revision 4 解析并真实影响编译，不能再放入 `module`、
  `moduleResolution`、`noEmit` 或 `types` 等 profile/拓扑项。

最小 package metadata 应只保留受控类型：

```json
{
  "type": "module"
}
```

## Workspace package boundary

Node 项目可以让受控 workspace 子包分别使用 ESM 与 CommonJS，而无需把所有 `.ts`
统一成根 package 的格式：

```text
node-workspace/
├── project.json
├── package.json                 # {}
├── main.mts
└── packages/
    ├── esm-package/
    │   ├── package.json         # {"type":"module"}
    │   └── value.ts
    └── commonjs-package/
        ├── package.json         # {"type":"commonjs"}
        └── value.ts
```

```ts
// main.mts
import { esmValue } from "./packages/esm-package/value.js";
import { commonJsValue } from "./packages/commonjs-package/value.js";

console.log(esmValue + commonJsValue);
```

```ts
// packages/esm-package/value.ts
export const esmValue: number = 20;

// packages/commonjs-package/value.ts
export const commonJsValue: number = 22;
```

这里两个源码文件都使用 `.ts`，但最近 boundary 分别是
`/packages/esm-package` 与 `/packages/commonjs-package`，所以它们会生成语义不同的
`.js`。入口 `.mts` 输出 `.mjs`，运行结果为 `42`。

workspace root 只接受 `packages/<name>` 或 `packages/@scope/<name>`；scope/name 使用
小写 ASCII 字母、数字、点、下划线与连字符，且首字符必须是字母或数字。存在任一
workspace 子包时，项目根 `package.json` 必须存在，但当根目录只有 `.mts/.cts` 时可写成
`{}`。文件名必须精确为小写 `package.json`。

所有 metadata 都使用 strict UTF-8/JSON，重复键和顶层 `scripts` 会被拒绝。受控 inventory
上限为 128 个 workspace roots、256 个 package metadata 文件、单文件 64 KiB、合计
1 MiB；这些文件同时计入普通 caller 输入配额。该能力只提供 package format boundary；
独立 `node_modules` 依赖层见下一节；npm bare import 与 `exports` 已由 T2-2 的独立
resolver 提供，package `imports` 与 lifecycle 执行仍不属于 workspace boundary。设计与真机
证据见 [T1-6 嵌套 package boundary](../architecture/NESTED_PACKAGE_BOUNDARIES_T1_6.md)。

## T2-1 `node_modules` 独立依赖层与 T2-2–T2-6 解析、运行、类型、原生边界

当前 Rhino 与 Node revision 2 项目会把项目根 `node_modules/` 打包进 input archive 的
独立只读 namespace。它不出现在调用方 source VFS，也不占用下节的 4096 files / 64 MiB
ordinary caller 配额。T2-2 会在普通项目 inventory 完成后，把同一精确依赖层 framed 到
编译器只读 `/node_modules/` VFS。因此 PC 上已经执行过 `npm install`/`pnpm install` 的
项目可以直接使用 `import dayjs from "dayjs"`、
`import { z } from "zod"`、`import { mini } from "zod/mini"` 等编译期导入。

支持 scoped package、子路径 `exports`、`types`/`import`/`require`/`node`/`default`
条件，以及无 `exports` 根入口的 `types`、`typings`、installed `@types`、`module`、
`main`、`index` 回退。
依赖包存在但请求的子路径没有导出时，编译器返回稳定的
`NODE_RESOLUTION_EXPORTS_NO_MATCH`，不会搜索 Android/WebView 文件系统或网络。

T2-4 会让 package `types`/`typings` 和 TypeScript 6 `typesVersions` 选中的声明文件进入
同一个 Program。依赖层中安装的顶层 `@types/<name>` 会自动加入内部 type-directive
清单；scoped package 使用 DefinitelyTyped 的 `@types/<scope>__<name>` 命名。声明文件内的
相对导入与继续引用的依赖 package 同样只能从冻结的 `/node_modules/` 读取。自动派生的
`types` 只属于 Program overlay，不会改变受控 tsconfig 或 provisional profile 对外报告的
effective options；安装内容本身已通过 dependency inventory/layer fingerprint 进入缓存键。

兼容的 Ace Editor 插件会在打开项目文档时按同一 project boundary、后缀/排除规则和依赖
配额冻结类型层，将声明映射到只读 `file:///autojs6/editor/node_modules/`，并把相同
`@types` 名称交给 TypeScript 6.0.3 language service。因此 `dayjs()` 等 package 返回类型、
成员补全、hover 和错误用法诊断与执行前编译一致。该能力只同步依赖声明；项目源码的完整
跨文件诊断、定义跳转与重命名仍属于 T4。编辑会话不会监视 PC/文件系统中的依赖变更，更新
`node_modules` 后应重新打开编辑会话，使编译与编辑器都捕获新的 exact snapshot。

T2-2 完成时，运行期装载仍属于 T2-3；T2-3 现已完成并继续使用同一 exact dependency
snapshot。Node 通过私有 workspace overlay 交给普通 Node package resolver，可执行
ESM/CJS/dual package；Rhino 通过临时 `node_modules` 与 CommonJS resolver 执行 CJS、JSON
及 dual package 的 `require` 分支。Rhino 不透明转译 ESM-only 依赖，而是返回稳定错误并提示
改用 Node profile。若依赖在编译后发生路径、size 或内容变化，两个 profile 都在执行前返回
`ERR_AUTOJS6_TYPESCRIPT_RUNTIME_DEPENDENCY_CHANGED`。

打包策略是“与编译相关的文本资产完整打包 + 明确排除”。普通 dependency scripts
永不执行；`preinstall`/`install`/`postinstall` 三个安装键从 T2-6 起会直接给稳定错误：

| 项目 | 当前规则 |
|---|---|
| 接受后缀 | `.cjs`, `.cts`, `.js`, `.json`, `.jsx`, `.mjs`, `.mts`, `.ts`, `.tsx` |
| 排除后缀 | `.map`, `.md`, `.markdown` |
| 排除目录 | `.bin`, `.git`, `.github`, `bench*`, `coverage`, `doc(s)`, `example(s)`, `test(s)` |
| 嵌套依赖 | 保留嵌套 `node_modules` |
| 内容 | strict UTF-8；符号链接遍历、非 canonical path 与大小写折叠冲突拒绝 |
| package scripts | `build`/`test` 等普通键可作为 inert JSON；`preinstall`/`install`/`postinstall` 明确拒绝 |
| 原生信号 | `.node`、`binding.gyp`、顶层 `gypfile: true` 明确拒绝，不会被过滤成后续 module-not-found |

依赖 identity 优先使用根 `package-lock.json`，其次使用 `pnpm-lock.yaml`；都不存在时使用
精确内容 inventory fingerprint。即使存在 lockfile，实际 inventory 的每个路径、size 与
SHA-256 仍进入 layer fingerprint 和编译缓存键，所以 lockfile 或安装内容任一变化都会
使缓存失效。

| 依赖层维度 | 上限 |
|---|---:|
| 文件数（含最多一个身份 lockfile） | 8192 |
| 单文件 | 8 MiB |
| 内容合计 | 64 MiB |
| lockfile | 8 MiB |
| canonical path UTF-8 bytes 合计 | 1.5 MiB |
| 带依赖层的 ZIP entries | 12289 |
| 带依赖层的 ZIP container | 140 MiB |
| 带依赖层的 expanded bytes | 132 MiB |

当前协议身份是 dependency-layer schema revision `1` / policy revision `2`，policy
fingerprint 为
`c159ae179de24307c456c69ae4e456ed9d7dba71dc965c22e6114f632af3834a`。完整边界和证据见
[T2-1 独立只读依赖层](../architecture/DEPENDENCY_LAYER_T2_1.md)。
T2-2 的 frame、条件选择与错误边界见
[T2-2 bare specifier 与依赖解析](../architecture/DEPENDENCY_RESOLUTION_T2_2.md)。
T2-3 的 Node overlay、Rhino CommonJS 规则与稳定错误见
[T2-3 依赖运行时装载](../architecture/DEPENDENCY_RUNTIME_LOADING_T2_3.md)。
当前 dependency resolver policy revision 为 `4`，fingerprint 为
`d8207539237d2a08a6b97b530c5e6215f4b73311fdd6954ce9f8004717ebd635`；它保留 revision 3
引入的 module-to-installed-`@types` 回退，并绑定 T2-6 原生/安装生命周期边界。T2-5
完成时的历史 revision 3 fingerprint 是
`6241375f2ea49ac2c2d2b3b79b28b7c1ee4adde75c989defc61e252c89ced2bd`。类型层与 Ace 同步见
[T2-4 依赖类型与编辑器同步](../architecture/DEPENDENCY_TYPES_EDITOR_T2_4.md)，五个固定发布包的
profile 结果与 workaround 见
[npm 真实包兼容矩阵](REAL_PACKAGE_MATRIX.md)。
`.node`、node-gyp 与 dependency install script 的错误码、排查和 pure/WASM 替代方案见
[原生 npm 依赖](NATIVE_DEPENDENCIES.md)。

## 普通编译配额

这些是用户项目的语义上限。ZIP/FD 传输层另为 request manifest 与容器记录保留
有界空间，不会从 caller 配额中扣除。

| 领域 | `v0.6.0` 上限 | 计数说明 |
|---|---:|---|
| caller 输入文件数 | 4096 | 源码、原始配置、package metadata/targets 计入；可选 metadata 会相应减少源码槽位 |
| caller 单文件 | 8 MiB | 任一 caller 文件 |
| caller 输入合计 | 64 MiB | 内置 TypeScript/AutoJs6 declarations 与 transport manifest 不计入 |
| JS + map artifact 数 | 512 | 只计最终打包的 JavaScript 与 Source Map |
| 单个 JavaScript | 16 MiB | 按 UTF-8、在最终 `sourceMappingURL` 重写后复测 |
| 单个 Source Map | 16 MiB | 按 UTF-8、在最终打包后复测 |
| JS + map 合计 | 64 MiB | manifest、diagnostics、ZIP metadata 不计入此语义额度 |
| primary diagnostics | 1000 | schema revision 1 |
| related information | 每条最多 1000、全文合计最多 1000 | 两个上限同时生效 |
| 单条诊断 message | 4096 UTF-16 code units | 不是 UTF-8 bytes |
| diagnostics 文档 | 512 KiB UTF-8 | 包含 envelope 与 related information |

普通 caller 配额由 `inputQuotaRevision=3` 表示。无依赖层的物理输入归档允许 4097 entries
（4096 个 caller entries + 1 个 request manifest）、65 MiB container 与 64.5 MiB
expanded bytes；其中额外的 1 entry、1 MiB container 与 512 KiB expanded allowance
只承载有界 manifest/ZIP 开销。存在 T2-1 依赖层时改用上节的加法 transport envelope，
同样不会扩张 caller 编译配额。Worker retained guardrail
另外容纳插件内置 declarations，当前为 4608 files / 72 MiB。已编译旧宿主会内联
input quota revision 2，仍按 512 files、单文件 8 MiB、合计 8 MiB 进入同一服务端
兼容路径。

诊断 range 使用从 0 开始的 UTF-16 offset/length；面向人的 line/column 从 1 开始。
DiagnosticCategory 数字身份为 warning `0`、error `1`、suggestion `2`、message `3`。
