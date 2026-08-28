# T1-7 Node ESM live-binding 循环依赖

状态：**完成（2026-08-27）**。TypeScript 插件继续输出未经 CommonJS 降级的原生
Node ESM；配套 Node.js Runtime 已将 ESM 入口与 dynamic `import()` 从正则转换式
partial adapter 切换到 V8 `vm.SourceTextModule` / `vm.SyntheticModule` linker。四模块
循环图经桌面 Worker、公开 Binder、Node Runtime 原生 Binder 与 AutoJs6 宿主执行链
验证，读取值从 `1` 更新为 `42`，不再得到 `undefined`、`NaN` 或伪造的 TDZ 值。

## 用户可见结论

Node profile 现在可以直接编译并运行包含循环依赖、可变导出和 re-export 的 `.mts`
项目。例如：

```text
esm-live-binding/
├── project.json        # {"type":"node","main":"main.mts"}
├── package.json        # {"type":"module"}
├── main.mts
├── state.mts
├── reader.mts
└── barrel.mts
```

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

console.log(`initial=${snapshot()}`); // initial=1
setValue(42);
console.log(`updated=${snapshot()}`); // updated=42
```

`.mts` 源码仍按 NodeNext revision 2 生成 `.mjs + .mjs.map`。import specifier 中的
`.mjs` 会在编译期解析到对应 `.mts`，输出后则直接指向生成的 `.mjs`。根
`type: module` 下的 `.ts -> .js` 也使用相同运行时语义。

保证范围是 **ESM 入口或 dynamic `import()` 建立的 ESM 图**。CommonJS
`require(esm)` 的同步互操作仍保留既有兼容 adapter，不把 CommonJS 的快照语义冒充为
原生 ESM live binding；profile 中继续明确报告 `cjsInterop: sync_esm_only`。

## 旧 adapter 为什么失真

旧 Node Runtime 会把 ESM import 大致改写为：

```js
const namespace = loadEsmModule("./state.mjs");
const value = namespace["value"];
```

并在模块函数执行完毕后才把本地变量复制到 namespace。这样会产生两个结构性问题：

1. named import 是一次性 `const` 快照，而不是指向 export cell 的 live binding；
2. 循环图在模块尚未执行完时只能返回半初始化 namespace，re-export 又会继续复制这个
   半初始化值。

同一四模块 fixture 在修改前稳定输出 `initial=undefined`、`updated=undefined`。这不是
TypeScript emit 问题：生成的 `.mjs` 已经保留 `export let`、静态 import 与 re-export；
失真发生在 Runtime 的执行适配层。

## 原生 linker 设计

Node.js Runtime 的进程级初始化和 legacy 单次初始化均增加
`--experimental-vm-modules`，让内嵌 Node 24.5.0 暴露 VM module API。执行层按模块种类
建立两类 record：

- JavaScript ESM 使用 `vm.SourceTextModule`；
- CommonJS、JSON、builtin 与其他受控 namespace 使用 `vm.SyntheticModule`。

每个 record 在递归 link 前先写入 canonical module cache。`SourceTextModule.link()` 的
回调继续调用 Runtime 已有的 scoped resolver，因此相对路径、package
`exports`/`imports`、builtin allowlist、module-source overlay、JSON import attribute、
data URL 与工作目录边界没有改走 Node 的非受控全局 loader。link 完成后由 V8 执行
`evaluate()`；模块实例化次序、TDZ、top-level `await`、export cell、re-export 和循环图
都由同一原生 module graph 管理。

dynamic `import()` 也进入相同 record/cache/link/evaluate 管线，因此静态与动态入口不会
各维护一份 namespace。既有同步 adapter 只保留给 CommonJS `require(esm)` 兼容路径。

Runtime 新增两项原生诊断：

```text
embedded_script.esm_linker=vm_source_text_module
embedded_script.esm_live_bindings=true
```

`autojs6:engine-info` 同时把 `esmLoaderProfile.status` 报告为 `native_linker`，并公开
`implementation: v8_vm_source_text_module`、`liveBindings: native_linker` 与
`cyclicDependencies: native_linker`，避免文档和运行时能力元数据继续声称 partial。

## 协议与兼容性

T1-7 不修改 TypeScript 插件的 AIDL、input/output archive schema、NodeNext profile、
controlled-tsconfig revision 或 package metadata policy。原因是编译产物一直是标准 ESM，
需要修复的是下游 Runtime 的链接语义。

Node Runtime 也没有修改宿主 Binder 请求或结果 contract；两项 linker 诊断只是
`KEY_NATIVE_PAYLOAD` 中的 additive key。禁用 ESM 的请求仍不创建 VM module，并报告
`disabled_by_request`。CJS 入口、raw TypeScript fail-closed、Source Map 文件、执行取消
和 workspace commit/cleanup 路径保持原契约。

## 自动化与真机证据

TypeScript 插件专项 `verifyT1NodeTopLevelAwait` 现额外建立四个 `.mts` source files，
用 production Worker bindings 和 TypeScript 6.0.3 incremental Program 生成四份
`.mjs + .map`。门禁冻结 `export let value`、静态 import、re-export、全 ESM output
inventory，并把产物写入隔离临时目录交给真实桌面 Node 进程执行。当前输出为
`t1-7.initial=1`、`t1-7.updated=42`，状态指纹为
`d78ad1b93ed0c52b6f13b1464edf0c365c85b0db249f839f1bf9f05af7aae95f`；专项
最终态连续复跑通过（89 tasks，分别为 24s / 17s）。

公开 Binder instrumentation
`nodeEsmLiveBindingCycleCompilesThroughPublicBinder` 在 Xiaomi 23046RP50C、Android 15 /
API 35、WebView `130.0.6723.86` 上编译同一四模块 archive，确认四份 mapping 全部为
ESM，且生成物没有 `exports.value` / `module.exports`；结果 1/1 通过，
`BUILD SUCCESSFUL in 21s`。

Node Runtime instrumentation
`x3d_08_esmCyclePreservesLiveBindingsThroughReExport` 在同一设备上直接执行四模块 `.mjs`
循环图，验证两次输出以及 `vm_source_text_module`/`true` 原生诊断；结果 1/1 通过，
`BUILD SUCCESSFUL in 25s`。完整 `NodeRuntimePluginAndroidConformanceTest` 会继续覆盖 TLA、
dynamic import、JSON attribute、package resolution 与现有 CJS interop，防止 linker 修复
破坏相邻能力。

AutoJs6 宿主 instrumentation `executesEsmTypeScriptCycleWithLiveBindings` 再从原始 `.mts`
项目走完整 snapshot、插件 Binder、output authority、precompiled-only handoff 与 Node
执行链，并同时断言两项原生 linker 诊断。AutoJs6 `6.8.0 (5276)`、TypeScript 插件
`0.6.0` release APK 与 Node Runtime `1.2.0 (versionCode 61)` arm64 release APK 在同一
API 35 设备上覆盖安装后结果为 1/1 通过，`BUILD SUCCESSFUL in 43s`（680 tasks，
1 executed）；该用例是 T1-7 的最终跨仓 release 联调验收。

该专项由 `verifyT1NodeEsmLiveBindings` 挂入 `verifyCompilerSmoke` 和根项目默认
`check --offline`。用户示例见[快速上手](../user/QUICK_START.md#node-esm-循环依赖与-live-binding)，
配置边界见[配置与配额参考](../user/CONFIGURATION.md#node-esm-循环依赖与-live-binding)。
