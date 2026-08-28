# T1-4 TypeScript 标准装饰器

状态：**完成（2026-08-26）**。当前 Rhino revision 2 与 NodeNext revision 2 都以
TypeScript 6.0.3 的标准 Stage-3 语义编译类/方法装饰器；不需要修改 `tsconfig.json`，
也没有新增 policy revision、profile revision、AIDL 或 archive schema。

## 结论

T1-4 是一次现有编译能力的产品化收口，而不是新的 compiler switch：

- `experimentalDecorators` 省略时，TypeScript 6 使用标准装饰器语义；当前两个 profile
  的 canonical options 一直省略该键，因此标准模式天然是默认模式。
- `ClassDecoratorContext`、`ClassMethodDecoratorContext`、`addInitializer` 等标准类型
  已随官方 TypeScript declaration distribution 打包，并能由真实 Worker Program 解析。
- 类装饰器与方法装饰器已在两个 profile 下完成类型检查、emit、CommonJS 执行和
  Source Map 验收；公开 Binder 又在 API 35 真机完成相同项目的双 profile 编译/归档。
- legacy `experimentalDecorators` 与 `emitDecoratorMetadata` 暂不进入 effective options。
  revision 4 中写入这两个键会各产生一条 `910002` warning，然后被忽略；最终仍按标准
  语义编译，effective fingerprint 不变。

因此 T1-4 不发布 revision 5。没有配置语义变化时滚动 policy identity 只会制造一套
没有新增能力的缓存与兼容分支。

## 用户语义

标准装饰器直接写在 `.ts`、`.tsx`、`.mts` 或 `.cts` 源码中。下面的类/方法签名在
Rhino 与 Node profile 都可类型检查：

```ts
type Constructor = new (...args: any[]) => object;

function mark<T extends Constructor>(
  value: T,
  context: ClassDecoratorContext<T>,
): T {
  context.addInitializer(function (): void {
    console.log(`loaded ${String(context.name)}`);
  });
  return value;
}

function trace<This, Args extends unknown[], Return>(
  value: (this: This, ...args: Args) => Return,
  context: ClassMethodDecoratorContext<This, (this: This, ...args: Args) => Return>,
): (this: This, ...args: Args) => Return {
  return function (this: This, ...args: Args): Return {
    console.log(`call ${String(context.name)}`);
    return value.apply(this, args);
  };
}

@mark
class Greeter {
  @trace
  hello(name: string): string {
    return `Hello, ${name}`;
  }
}
```

不应为这段代码添加 `"experimentalDecorators": true`。该选项表示旧版 TypeScript
decorator dialect，不是“打开装饰器”的通用开关；当前配置策略会忽略它。

## Declaration 闭包

官方 TypeScript 6.0.3 标准库发行包包含 `lib.decorators.d.ts` 与
`lib.decorators.legacy.d.ts`。所有受控 `lib` 最终都会落到 `lib.es5.d.ts`，而该文件的
官方引用链同时声明：

```text
/// <reference lib="decorators" />
/// <reference lib="decorators.legacy" />
```

Worker 的 `createControlledStandardLibraryResolver` 允许解析
`lib.decorators.d.ts`/`lib.decorators.legacy.d.ts`，但 declaration type 的存在不决定
emit dialect；实际 emit 仍由 canonical compiler options 决定。T1-4 门禁同时核对 108 个
官方标准库文件的生成清单、两个 decorator declaration 文件及上述引用链，避免只证明
parser 能识别 `@`，却遗漏 `ClassMethodDecoratorContext` 等开发体验所需类型。

## Standard 与 legacy 的取舍

本轮明确选择 standard-only：

1. standard 与 legacy 的函数签名、调用次序和 helper 分别是两套语义，并非同一能力的
   开/关。把 legacy flag 混入 revision 4 会让相同配置身份产生不同 JavaScript。
2. `emitDecoratorMetadata` 属于 legacy metadata 流程；仅生成 `__metadata` 并不会自动
   提供 `Reflect.metadata`/`reflect-metadata` 消费端。在 T2 npm/依赖层完成前单独开放它，
   得到的是半套功能而不是更完整的开发体验。
3. TypeScript 6 的现代默认就是标准装饰器，已经满足新项目的类/方法装饰器需求，并能
   使用 typed context 与 `addInitializer`。

这不是永久禁止 legacy。如果后续需要迁移既有 Angular/Nest 风格工程，应在 T2
依赖运行时到位后，以 append-only revision 5（或更高）显式加入 legacy dialect 与
metadata runtime 的完整验收；旧 revision 4 仍保持 standard-only，不能就地重解释。

## 身份与验收

当前 policy identity 保持不变：

- `configPolicyRevision=4`
- policy fingerprint
  `4a9a5e70150f81e1ec9a5e2f749cc081f93f207f092300c96a79abfdcc152dab`

无别名的装饰器验收项目使用以下 effective fingerprints：

| profile | target / lib | effective fingerprint |
|---|---|---|
| Rhino revision 2 | `ES2018` / `ES2018` | `a5a49efc19206d54f61c5db67323c72da05250a023398ebcae84557bbcbcd8a7` |
| NodeNext revision 2 | `ES2022` / `ES2022` | `b77bfac45d80ad99a9a65168ecd97a10607f0574189e521e3355eb080b4a97ff` |

`verifyT1StandardDecorators` 使用 production Worker 解析 revision 4 配置，再把 exact
effective options 与正式 common/profile declaration layers 交给
`createIncrementalProgram`。两个 profile 均验证：

- 零 error diagnostics，JavaScript 与外部 Source Map v3 各一份；
- emit 包含 `__esDecorate`、`__runInitializers`，不含 legacy `__decorate`、
  `__metadata` 或 `Reflect.decorate`；
- 生成的 CommonJS 实际执行并返回 `decoratedAnswer=42`；
- 装饰器顺序为
  `method:apply|class:apply|class:init|method:init|method:call`；
- 同一配置加入 legacy/metadata 两个键后仅产生两条 `910002` warning，canonical JSON
  与 effective fingerprint 逐字不变。

桌面任务结果为 `BUILD SUCCESSFUL`（87 actionable tasks，5 executed），专用脚本双
profile 执行通过。随后在 Xiaomi 23046RP50C、Android 15 / API 35、Android System
WebView `130.0.6723.86` 上运行
`standardClassAndMethodDecoratorsCompileThroughBothPublicProfiles`：公开 Binder 对 Rhino
`.ts` 与 Node `.cts` 分别完成真实 Worker 编译、formal output archive、helper、map 与
fingerprint 核对，结果为 `Time: 20.381`、`OK (1 test)`、
`INSTRUMENTATION_CODE: -1`。

插件契约仍是“编译并返回产物”，不会在 Binder service 内执行生成代码；双 profile
运行语义由桌面验收直接执行相同 compiler output，设备用例负责证明正式 Android
声明层与公开编译链路没有偏差。
