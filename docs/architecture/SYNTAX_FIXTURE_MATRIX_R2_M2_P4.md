# R2-M2-P4 Syntax Fixture Matrix

日期：2026-08-11

状态：**已完成。P4 fixture、direct oracle、JVM contract、Gradle aggregate 与最终 Core
均已通过；`R2-M2-P4` 已勾选。P5 compiler-boundary matrix 与 X1 public-shaped
deterministic bundle 随后也已完成；实时 M2 进度与下一活动项以
[R2 Roadmap](../roadmap/R2.md) 为准。本阶段运行了
受互斥保护的 desktop/JVM/APK/API/source gate，但未运行 ADB、未使用设备，也未执行
emitted JavaScript 或创建 runtime engine。**

## 决策摘要

P4 继续采用与 P3 相同的独立 TypeScript compiler-bundle oracle，不修改 production
profile、controlled-tsconfig、archive、Worker、native result/private output 或 public
capability。首个 revision 固定为：

- `typescript@6.0.3`；
- 7 类 syntax：enum、namespace、class fields、generics、optional chaining、async、decorators；
- 2 个具有不同 compiler options 的 profile：Rhino revision 1 与 NodeNext revision 1；
- 共 14 cases；每个 case 使用 declared/reversed 两种输入顺序并各重复 2 次，共 56
  compiles；
- 每个 case 固定两个不同的 executable `.ts` files/roots；不得用 `.d.ts` 伪造第二个
  root，因此 reversed order 必须是真实的 source/root insertion-order 扰动；
- diagnostics 必须为空、`emitSkipped=false`，每个 `.ts` 必须精确产生一个 JavaScript
  output 与一个 Source Map v3，即每个 case 精确产生 4 个 outputs；
- `ts.sys`、host filesystem、network 与 emitted JavaScript execution 必须全部为 0。

Decorators 明确使用 TypeScript 5+ 默认的 **standard Stage-3 decorators**。冻结 profile
必须继续省略 `experimentalDecorators`，不得为了 P4 启用 legacy decorators。

## Profile 适用范围

| Authority | P4 direct cases | 理由 |
| --- | --- | --- |
| provisional Rhino revision 1 | 7 | 独立的 ES2018/CommonJS/Node10 compiler-options authority 与 Rhino declarations layer |
| provisional NodeNext revision 1 | 7 | 独立的 ES2018/NodeNext/NodeNext compiler-options authority 与 Node declarations layer |
| provisional NodeNext revision 2 | 0（只做 equality/fence） | compiler options JSON 与 fingerprint 和 revision 1 完全相同；revision 2 的差异属于 `.mts/.cts`、module-output、resolver 与 Worker-result authority，不属于本批单文件 `.ts` syntax semantics |
| controlled-tsconfig Rhino | 0（只做 rejection/default fence） | 它是 config mode，不是新 compiler profile；`useDefineForClassFields=false` 已固定，`experimentalDecorators` 是 known-but-unsupported option |

P4 JVM gate 必须同时证明：

1. Node revision 1/2 的 canonical effective-options JSON 和 effective-options fingerprint
   相同；
2. 两者的 runtime-capabilities fingerprint 不同，且 revision 2 仍只属于
   `workerResultAdmittedAuthorities`，不得进入 `endToEndAdmittedAuthorities`；
3. controlled-tsconfig 对 `experimentalDecorators` 继续返回
   `CONFIG_UNSUPPORTED_FEATURE`，不得把它加入 boolean allowlist；
4. controlled Rhino 的 `useDefineForClassFields` 继续为 `false`。

因此，P4 不需要第三套 Node revision-2 compile golden，也不需要 controlled-tsconfig
syntax variant。任何一项被加入都会把 compiler syntax 验证扩展成 source-kind、resolver 或
config-authority 变更，必须另立 revision 和路线项。

## Fixture matrix

14 个 case ID、顺序与 feature inventory 必须冻结：

| Feature | Rhino case | Node case | 成功 emit 的语义标记 |
| --- | --- | --- | --- |
| enum | `rhino-enum` | `node-enum` | 数值 enum IIFE 与正/反向 mapping；不得被擦除 |
| namespace | `rhino-namespace` | `node-namespace` | namespace IIFE 与 exported members |
| class fields | `rhino-class-fields` | `node-class-fields` | ES2018 assignment semantics（`this.value = 1`）；不得改为 instance `defineProperty` |
| generics | `rhino-generics` | `node-generics` | semantic type-check 通过，type parameter/annotation 从 JS 中擦除 |
| optional chaining | `rhino-optional-chaining` | `node-optional-chaining` | `?.` 与 `??` 下编译为 ES2018 nullish guards，生成 JS 不再含原运算符 |
| async | `rhino-async` | `node-async` | ES2018 原生 `async`/`await` 保留；不得错误期待 `__awaiter` |
| decorators | `rhino-decorators` | `node-decorators` | 包含 `__esDecorate` 与 `__runInitializers`；不得包含 legacy `__decorate` |

建议每个 feature 使用独立 feature source，避免一个语法的 output drift 污染其他 feature
的 golden。每个 feature source 必须配一个不同的 executable `*-context.ts` root；context
可以只导出稳定 primitive sentinel，但不能改用 declaration-only `.d.ts`。最小 feature-source
intent 如下：

```typescript
// enum.ts
export enum Direction { Up, Down = 4 }
export const selected: Direction = Direction.Down;
```

```typescript
// namespace.ts
export namespace Geometry {
    export const origin = 0;
    export function double(value: number): number { return value * 2; }
}
export const answer = Geometry.double(21);
```

```typescript
// class-fields.ts
export class Counter {
    value: number = 1;
    label?: string;
    increment(): number { return ++this.value; }
}
```

```typescript
// generics.ts
export function identity<T>(value: T): T { return value; }
export const answer = identity<number>(42);
```

```typescript
// optional-chaining.ts
export function read(input?: { nested?: { value: number } }): number {
    return input?.nested?.value ?? 0;
}
```

```typescript
// async.ts
export async function answer(): Promise<number> {
    return await Promise.resolve(42);
}
```

```typescript
// decorators-standard.ts
function logged(value: Function, context: ClassDecoratorContext): void {
    void value;
    void context;
}

@logged
export class Example { value = 42; }
```

每个 case 的 `files` 与 `rootNames` 必须都是两个元素、路径集合完全相等、两个 path 都是
exact lowercase executable `.ts` 且不是 `.d.ts`。这样 reversed root/input order 才是有效的
顺序扰动；单 source 或 declaration-only second root 均不能关闭 P4 的 order proof。

## Exact expected result

每个 case 的 checked-in `expected` 必须非空并包含：

- `diagnostics: []`；
- `emitSkipped: false`；
- 排序后的 `outputCollectionSha256`；
- 2 个 exact JavaScript 与 2 个 `.js.map` path、UTF-8 byte length、lowercase SHA-256 和
  source paths；
- map 的 exact `version/file/sourceRoot/sources/names`，以及 mappings byte length/SHA-256。

Collection canonical 继续使用 P3 的定义：

```text
sorted outputs;
UTF-8(path + NUL + decimal byteLength + NUL + lowercase sha256 + LF)
```

除 exact byte golden 外，direct harness 还必须执行 feature-aware sentinels，以免 fixture
被机械重生成成另一种语义：

- enum/namespace 的 IIFE 仍存在；
- class field 是 instance assignment，且没有 `Object.defineProperty(this, ...)`；
- generic type syntax 已擦除；
- optional chain/nullish syntax 已下编译；
- async 仍为原生 ES2018 async，不含 `__awaiter`；
- standard decorator 包含 `__esDecorate`/`__runInitializers`，不含 `__decorate`、
  `__metadata` 或 `Reflect.decorate`。

## Decorator authority

TypeScript 6.0.3 在 `experimentalDecorators` omitted/false 时使用 standard decorators；
`experimentalDecorators=true` 才切换到 legacy emit。P4 必须锁定 omitted，而不是向
canonical profile 显式加入 `false`，因为即使 output 暂时相同，增加 option key 也会改变
profile JSON/fingerprint 和跨 Worker/native contract。

必须同时冻结以下边界：

- Rhino/Node fixed-options JSON 不包含 `experimentalDecorators`；
- Worker 的 config-none option constructors 不包含该 key；
- controlled-tsconfig boolean allowlist 不包含该 key，并将其按 known unsupported option
  fail closed；
- 不启用 `emitDecoratorMetadata`，不依赖 `reflect-metadata`；
- fixture 不使用 legacy parameter decorator 或 legacy decorator function signature；
- compiler version 改变时必须显式升级 fixture revision 并重新审查 helper/runtime shape。

## Oracle 与 production 隔离

P4 harness 应复用 P3 的 snapshot-only CompilerHost 和 generated common/profile declaration
layers，使用 `ts.createIncrementalProgram`，但必须拥有独立 schema、fixture 与 task。编译期间
必须 fence：

- `ts.sys`；
- Node host filesystem；
- DNS/TCP/TLS/HTTP(S)；
- child process 与 worker threads；
- `eval`、`Function`、`node:vm` 和任何 emitted-JavaScript execution route。

P4 不允许修改：

- `R2CompilationProfileRegistry` 的 options、revision 或 admission sets；
- `R2ControlledTsConfigPolicy`/effective-options schema；
- `R2ProjectSourceKindAdmission`；
- project archive/snapshot/Worker frame；
- native result、generated mapping 或 private-output schema；
- public Binder/AIDL、capability metadata、Node Runtime dependency 或 AutoJs6 host routing。

尤其是 async fixture 的 PASS 只证明 ES2018 compiler emit。因为 ES2018 会保留原生
`async`/`await`，它不是 Rhino runtime compatibility 证据。Decorator helper 也不会在 P4
执行，因此不得把 compiler golden 描述为 runtime behavior proof。

## 建议 static/JVM gates

### Direct/static markers

- fixture schema/revision/version/scope exact；
- authority inventory exact 为 `rhino`、`node`，且绑定现有 canonical options SHA-256；
- case inventory/order exact 为上述 14 项，feature 各出现 2 次；
- 每个 case exact 两个 executable `.ts` files/roots、零 `.d.ts` root、4 outputs；
- `cases=14`、`compiles=56`、四类 escape/output-execution counter 均为 0；
- harness 必须包含 `createIncrementalProgram` 与 feature-aware emit sentinels；
- fixture 不得包含未定稿 expected、`expected: null` 或未封口 digest；
- fixed profile/Worker constructor 中 `experimentalDecorators` absent；
- production admission、Node revision-2 Worker-result-only，以及 `COMPILER_AVAILABLE`、
  `PROJECT_COMPILATION`、`SEMANTIC_DIAGNOSTICS`、`SOURCE_MAP_V3`、
  `NODE_RUNTIME_REQUIRED` 五项 public/runtime flag 继续为 `false`。

### JVM contract markers

- strict parse fixture 的 exact top-level/authority/case/expected/output/map keys；
- 将 Rhino/Node options JSON、revision 与 fingerprint 绑定
  `R2CompilationProfileRegistry`；
- 重算 14 个 output collection SHA-256 与每个 output/map digest；
- 证明所有 P4 root 都是 production-admitted executable `.ts`（非 `.d.ts`），且没有引入
  `.js/.json/.mts/.cts` 变更；
- 证明 Node revision 1/2 compiler options identity 相同、runtime capability identity 不同；
- 证明 controlled `experimentalDecorators` known-option rejection 与
  `useDefineForClassFields=false`；
- 锁定 standard/legacy decorator mode markers与 async/class-field lowering markers。

P4 aggregate 已依赖 P3 final aggregate、direct oracle、JVM fixture contract、
APK/API/public/source/freeze gates，并由 `verifyR2ProjectCompilerCore` 接入；主路线因此已把
M2 从 `13/18` 更新为 `14/18`。

## 验证证据

- checked-in fixture：`app/src/test/resources/r2-compiler-syntax/p4-v1.json`；schema revision 1，
  7 syntax × 2 profiles = 14 cases，14 个 source/output collection identities 与 56 个 output
  descriptors 均已封口；
- direct oracle：`verifyR2M2P4CompilerSyntaxDesktop` 通过 56 次 TypeScript 6.0.3 compile，
  stdout 为 `syntax=7, profiles=2, cases=14, compiles=56`，且
  `ts.sys/host-fs/network/output execution=0`；
- JVM contract：`R2CompilerSyntaxMatrixFixtureTest` 严格重建 fixture schema、source/output/map
  identity，并证明 standard decorators、controlled-option closure、Node revision 1/2 options
  equality 与 revision 2 Worker-result-only 边界；
- final aggregate：`verifyR2M2P4CompilerSyntax` 通过 P3、desktop、JVM、APK/API/public/source
  regression inventory，production profile/source-kind/Worker/native/public/runtime 均未扩张；
- final Core：受互斥保护运行 `:app:verifyR2ProjectCompilerCore`，结果为
  `BUILD SUCCESSFUL in 3m 58s`，519 actionable tasks（103 executed、416 up-to-date）。

这些结果只证明 deterministic compiler syntax/emit shape；不构成 Rhino/Node runtime、ADB、
设备或 public/end-to-end acceptance。

## 风险审计

### High

1. **误开 legacy decorators。** 向 profile/Worker/controlled config 增加
   `experimentalDecorators` 会改变 emit dialect，且可能改变 canonical fingerprint、transport
   identity 与用户语义。P4 必须 standard-only、option absent。
2. **把 compiler emit 当作 runtime 支持。** Rhino 的 async 保留为原生语法，decorator
   helpers 也未执行；据此提升 public/runtime capability 会产生虚假 acceptance。
3. **为 P4 修改 production authority。** syntax matrix 不需要新 source kind、profile revision、
   archive/result schema 或 end-to-end admission。任何此类改动都属于 scope escape。

### Medium

1. **Node revision 2 重复 golden 掩盖真正边界。** rev1/rev2 compiler options 相同，第三套
   compile matrix 没有新增语义证据；应改为 options equality + runtime authority inequality gate。
2. **单 source 或 `.d.ts` second root 使 order/admission 证明失真。** 每个 case 必须有两个
   production-admitted executable `.ts` roots；否则 reversed order 不能证明真实 source/root
   insertion-order invariance，且 fixture shape 与 production snapshot root 不一致。
3. **只锁 SHA 而不锁 emit dialect。** 自动重生成可把 standard decorator、class-field 或 async
   语义漂移隐藏在新 digest 后；必须有 feature-aware sentinels。
4. **泛型 fixture 只证明擦除、不证明运行时。** semantic diagnostics + exact emit 足够关闭
   P4 compiler syntax，但不能声称 generic runtime behavior。
5. **NodeNext package context 漂移。** P4 应保持无 package metadata 的 `.ts` compiler-only
   snapshot；若引入 package type/import graph，应转交既有 P2 resolver/module-output gates。

## 关闭条件（已满足）

以下条件已经同时满足，因此 `R2-M2-P4` 已勾选：

- checked-in 14-case fixture 已封口，无 placeholder；
- 每个 case 均为两个 executable `.ts` roots 与 4 个 outputs；
- 56 次 TypeScript 6.0.3 direct compiles 全部 exact-match；
- JVM contract、P4 final aggregate 与最终 Core 全部 PASS；
- production profile/config/source-kind/Worker/native/public/runtime fences 全部保持原值；
- 文档记录真实 task 输出，并明确本阶段未产生 runtime/device acceptance。
