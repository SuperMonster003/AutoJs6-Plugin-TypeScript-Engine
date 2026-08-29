# T6-1 Node legacy TypeScript stripping 退役

状态：2026-08-29 完成。

## 结论

Node Runtime 现在只执行 JavaScript 编译产物。原始 `.ts`、`.mts`、`.cts`（以及
Node 不接受的 `.tsx`/声明文件）到达执行边界时，稳定返回
`ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`；不存在请求键、正则擦除器或 provider
私有协议可以把该行为重新打开。

这次删除不改变 TypeScript Engine 的职责：宿主仍先通过本插件取得
`.js/.mjs/.cjs`、Source Map 与 diagnostics，再把 JavaScript 快照交给 Node Runtime。

## 删除面

Node Runtime 仓库删除了以下实现：

- `NodeTypeScriptStripper.java` 的 802 行正则 erasure 实现；
- `KEY_LEGACY_TYPESCRIPT_STRIPPING_ENABLED` 公共契约常量，以及宿主请求中的对应
  Bundle 写入；
- module-source provider 的私有 `prepare_plaintext_typescript` 二次准备操作、pending
  状态、剥离计数与准备诊断；
- 正则剥离器和明文准备协议的专属 JVM 兼容测试。

替代实现 `NodeTypeScriptSourcePolicy` 只做 fail-closed admission。它不解析、不改写
TypeScript，只按源码名识别原始 TypeScript，并生成统一且有界的 compiler-required
诊断。入口源码、预加载 module source、runtime module source、provider 解密源码与
native 直达加载器都受同一不变量约束。

宿主 `NodeJsRuntimePluginBridge` 与宿主侧 nodejs-api 镜像同步删除该键；因此新宿主既
不会发送它，也无法通过源码常量重新启用旧路径。

## 编译读取与执行边界

snapshot 和 execution-scoped provider v3 仍必须能够读取原始 `.ts/.mts/.cts`，否则
动态新增模块无法按需编译。它们是唯一例外，但例外只允许“交给编译器读取”，不允许
“交给 V8 执行”：

```text
raw .ts/.mts/.cts
        │
        ├─ snapshot / provider v3 compiler input ──> TypeScript Engine
        │                                             │
        │                                      .js/.mjs/.cjs ──> Node execution
        │
        └─ direct runtime input ──> ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED
                                    （native dispatch 前终止）
```

完整一致性测试首次暴露了一个边界次序问题：通用路径校验过早拒绝原始 TypeScript，
使 snapshot missing-source 和 provider v3 尚未获得编译机会。终态校验器新增仅供编译
读取调用点使用的显式分支；所有执行调用点仍使用 fail-closed 默认值。修复后 snapshot
正例、missing/ambiguous/escape 负例和 provider v3 按需编译均保持原语义。

## 兼容与发布元数据

`releases/**/1.3.0` 是已发布的不可变 catalog/runtime-kit/API 快照，不因源码退役而
回写。APK 当前随附的 1.3.0 catalog/runtime-kit 也保持同一已发布字节；其中描述旧请求
键的字段没有生产消费者，不能形成执行路径。下一次 catalog/runtime-kit 版本化发布会
刷新这些描述性元数据，属于 T6-4 发布链，而不是恢复兼容实现的理由。

未知 Bundle 键本来就由 Android Bundle 契约宽容忽略；因此旧调用方即使按字符串发送
该字段，也只会得到当前 compiler-only 行为，不会改变执行结果。

## 验收证据

Node Runtime 离线门禁通过：

- `:app:testDebugUnitTest`；
- `:nodejs-api:testDebugUnitTest`；
- `:app:compileDebugJavaWithJavac` 与 `:app:compileDebugAndroidTestJavaWithJavac`；
- `:app:assembleDebug`（arm64-v8a、armeabi-v7a、x86_64）；
- `verifyNodeProjectWizard`（11 个模板）、`verifyNodeHostApiMirror`（10 个文件）、
  `verifyNodeHostCapabilityManifestAlignment`（48 = 48 = 48）与
  `verifyNodePluginExamples`。

同一最终源码的 `NodeRuntimePluginAndroidConformanceTest` 在三台真机完整通过：

| 设备 | Android | 结果 |
|---|---:|---:|
| Xiaomi 23046RP50C | 15 | 10/10 |
| Sony XQ-AT72 | 12 | 10/10 |
| Sony G8441 | 9 | 10/10 |
| 合计 | 9 / 12 / 15 | 30/30 |

覆盖点包括 raw entry、dynamic raw module、snapshot 预编译、snapshot
missing/ambiguous/escape、预编译 CJS/ESM corpus 与 provider v3 边界。raw
`.ts/.mts/.cts` 均在 native dispatch 或 workspace commit 前返回统一错误，provider
不会因该失败被误调用。

宿主侧 nodejs-api 契约测试、主 Kotlin 编译和 Android test Kotlin 编译共同验证了镜像
同步及请求构造不再依赖旧键。
