# T3 常驻项目会话与模块级增量编译

> 状态：2026-08-28 已在插件与 AutoJs6 宿主部署。本文冻结 T3-1/T3-2/T3-3 的
> 当前实现、兼容边界、资源生命周期与同设备验收数据。

## 用户行为

宿主显式请求 `projectSessionRevision=1` 后，同一个调用 UID、同一个 `projectId` 的
连续编译可以复用已经加载 TypeScript 6.0.3 的 WebView/Worker realm，以及该项目的
增量 Program 状态。修改一个文件后，Worker 只重新创建内容摘要发生变化的
`SourceFile`，TypeScript builder 计算受影响闭包，只重新 emit 受影响模块；返回给
宿主的 output archive 仍然是完整项目产物，不要求宿主持有或拼接旧输出。

该能力是显式 opt-in。未携带新 key 的旧宿主继续走每 job 销毁 realm 的全冷路径，
其输入、输出、错误与资源回收语义不变。

## Additive Bundle 契约

公开 AIDL transaction 与既有字段保持冻结，只增加以下 Bundle 能力：

```text
getCompilerInfo():
  projectSessionRevision = 1

startCompilation(request):
  projectSessionRevision = 1  // 可选；缺失即 legacy cold path
```

caller 不能直接指定 Worker cache key。service 在 admission 时从以下权威字段派生
domain-separated SHA-256：

```text
revision + Binder callingUid + accepted projectId
```

原始 UID 与 `projectId` 不进入 WebView；framed transport 只增加
`projectSessionRevision` 与 64 位小写十六进制 `projectSessionKey`。因此两个应用即使
使用相同 `projectId`，也不能命中彼此的常驻状态。未知 revision 在公开协议层按
unsupported authority fail closed。

## Worker 所有权与缓存

一个健康 Worker 内持有最多 3 个项目会话，估算保留总量最多 64 MiB。Map 使用访问后
重新插入的顺序淘汰最旧项；单项超过总预算时不保留。估算覆盖项目输入、依赖层、
package target 与完整输出文本。上限是 realm 内的第二道保险丝，不替代 Android
进程内存管理。

每个 entry 只保留：

- 与 compiler profile、受控配置、resolver/dependency 权威绑定的结构化
  `compilerAuthority`；权威变化会销毁旧 entry，全冷重建。
- 以接受后的文件 SHA-256 作为 `SourceFile.version` 的 SourceFile cache。
- `EmitAndSemanticDiagnosticsBuilderProgram`，供下一次
  `createIncrementalProgram` 使用。
- 上一次成功编译的完整 output map。新 emit 原子替换对应输出，未受影响输出复用；
  已不可达源码的输出在结果发布前删除。

只有 error diagnostics 为零且 emit 完整成功的 job 才提交 entry。协议错误、诊断失败、
emit 失败、取消、超时、renderer/Worker 故障都不会留下可继续使用的半成品状态。
常驻命中时，语法诊断只检查内容变化文件，语义诊断通过 builder 的 affected-file
迭代覆盖依赖闭包。

## Realm 生命周期

- legacy job：terminal success 发布前仍证明 exact WebView/MessagePort teardown，
  后续 job 懒创建新 generation。
- opted-in job：Worker 完整 result boundary 通过后发布 terminal success，健康 realm
  保持 READY。
- service `onTrimMemory`：空闲时立即销毁 realm；job 活动时设置 discard fence，终态
  走 exact teardown，不再保留。
- service close、进程重启、renderer/Worker 故障：原有 teardown/recovery 路径同时
  丢弃全部项目状态；下一请求自然全冷回退。

这仍是进程内优化，不是持久缓存。宿主不能依赖 session 一定存在，任何一次请求都
必须携带完整、可独立编译的 snapshot。

## 验收

### 桌面 Worker 门禁

`gradlew :app:verifyT3ResidentSessions --offline` 使用 20 个源码模块，每个模块 600 个
只用于扩大真实 parse/type-check 工作量的类型声明，修改叶模块一次。五次独立运行为：

| cold | warm | warm emit | 编译耗时下降 |
|---:|---:|---:|---:|
| 506 ms | 37 ms | 2 / 40 | 92.7% |
| 555 ms | 69 ms | 2 / 40 | 87.6% |
| 790 ms | 102 ms | 2 / 40 | 87.1% |
| 485 ms | 51 ms | 2 / 40 | 89.5% |
| 456 ms | 55 ms | 2 / 40 | 87.9% |

同一门禁还证明：warm 返回 40 个完整输出且变更模块不是旧内容；不同 session key 必须
执行 40-output 冷 emit；加入第 4 个项目后最旧项目重放也必须冷 emit，证明 3-session
淘汰上限；两个 legacy 请求各自执行 40-output 冷 emit，均不复用 builder。该任务已加入
根项目默认 `check`。

### Android 真机门禁

设备：Xiaomi 23046RP50C，API 35，WebView `130.0.6723.86`。测试：
`R2ProjectCompilerInstrumentationTest#residentProjectSessionReusesOneRealmAndDropsItUnderMemoryPressure`。
计时使用 monotonic clock；交互路径包含 snapshot framing、WebMessagePort 输入与完整结果
传输，增量编译路径单独取 ingress complete 到 emit complete 的 Worker/compiler 阶段。

| 运行 | 10 文件 cold | 10 文件 warm（5 次） | warm p50 | 20 文件 compiler cold | compiler warm（3 次） | warm p50 | 下降 |
|---|---:|---|---:|---:|---|---:|---:|
| 1 | 1199 ms | 536, 453, 480, 462, 408 ms | 462 ms | 535.388 ms | 138.255, 118.511, 139.766 ms | 138.255 ms | 74% |
| 2 | 1077 ms | 375, 309, 328, 322, 404 ms | 328 ms | 598.246 ms | 165.819, 94.622, 151.117 ms | 151.117 ms | 74% |

10 文件样本在 9 个稳定模块中各放 20 个类型，修改小型叶模块；20 文件样本在 19 个
稳定模块中各放 600 个类型，修改小型叶模块。两轮均满足 T3-1 的端到端 p50 `<1 s`
和 T3-3 的编译耗时下降 `>70%`。测试还用另一个项目身份冷编译不同叶值，验证完整输出
没有串线，随后触发内存压力回收并观察 exact realm generation `1` 退休。

## 宿主接入与绑定生命周期

AutoJs6 宿主已按以下边界接入 revision 1：

1. 只有 `getCompilerInfo()` 或 prewarm result 广告 exact revision `1` 时，宿主才把该
   revision 原样放入 compilation request；缺失或未知 revision 保持 legacy cold path，
   宿主不创建、持久化或猜测 Worker key。
2. 单文件使用 canonical source path 的 SHA-256、项目使用 canonical root path 的
   SHA-256 形成稳定 `projectId`；每次请求仍携带完整 snapshot 并校验完整 output
   archive，因此 session 消失只影响性能，不影响正确性。
3. App 主进程启动 3.6 秒后异步 bind + prewarm；打开 `.ts/.tsx/.mts/.cts` 及对应
   declaration 文件的编辑器时立即请求相同预热。真实编译在合法输入确认后等待正在
   进行的启动预热，避免两个并发 bootstrap。
4. 宿主最多保留一个 live dedicated binding。无编辑器/编译信号的启动预热有 60 秒
   空闲期限；编辑器访问或真实编译会取消该期限并接管保活。Binder 死亡会在下次访问
   时替换；memory trim、low-memory、应用 clear/exit 会立即释放。release 与异步获取、
   编译结束后的 lease 移交均用 generation fence 防止旧绑定重新复活。
5. 选择插件、合约验证、bind、prewarm 或异步线程启动任一步失败都只记录日志；普通
   编译仍走既有冷路径，不把预热可用性升级为正确性前提。

60 秒启动空闲期限采用保守资源预算。既有 API 35 Xiaomi 23046RP50C、8 文件
active-realm 记录中，compiler service / renderer median PSS 为 90,458 / 95,241 KiB，
combined 191,542 KiB；这是活动编译 realm 的历史观察值，不是空预热的精确 PSS，故只
用于决定不能无限保持无用户信号的启动 lease。原始记录见
[`docs/maintenance/memory-observations.json`](../maintenance/memory-observations.json)。

### AutoJs6 宿主真机验收

同一 Xiaomi 23046RP50C / API 35 设备安装当前宿主与插件 APK，运行
`TypeScriptCompilerPluginClientDeviceTest#startupPrewarmMovesCompilerBootstrapOffFirstRunAndKeepsRevisionOneSession`。
测试先显式执行宿主 prewarm，再对关闭 persistent output cache 的 10 文件 Rhino 项目
冷编译，修改叶模块后重编；输出必须包含新 revision marker。

| 启动预热 | 首次用户编译 bind + prewarm | 首次路径等待迁移 | core compiler cold | core compiler warm | 下降 |
|---:|---:|---:|---:|---:|---:|
| 1,626 ms | 45 ms | 97.2% | 539 ms | 156 ms | 71.1% |

仪器输出为 `OK (1 test)`，日志 schema 为
`typescript-startup-prewarm-acceptance schema=1`。该结果完成 T3-2，并同时证明宿主
revision 1 opt-in 确实获得 T3-1/T3-3 的增量状态，而不是只把 service bind 移到后台。

旧 AutoJs6 不发送新 key，仍保持完全兼容的逐 job cold path；新宿主配旧插件时 exact
revision 协商失败，也自动走同一路径。
