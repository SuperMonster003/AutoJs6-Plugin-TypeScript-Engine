******

### 發行歷史

******

# v0.7.0

###### 2026/10/13

* `提示` 解析服務首次使用時載入一份 TypeScript (約 1 秒), 閒置約 1 分鐘或系統記憶體不足時釋放; 3-Block Compose 1.4.0 的 TSX 專案需要 TypeScript Engine 0.7.0
* `新增` TypeScript 語法樹解析服務 (org.autojs.plugin.typescript.PARSER), 供 3-Block Compose 設計器等外掛解析單個 .ts / .tsx / .js 檔案: 只做語法分析, 傳回帶原始碼位置的語法樹與語法錯誤, 不做型別檢查與編譯, 與編譯服務互不排隊

# v0.6.8

###### 2026/10/08

* `提示` compose.memo 需要配套 AutoJs6 6.8.0 / 5323; TSX 配套需要 TypeScript Engine 0.6.8
* `改善` compose.memo 的 TypeScript / TSX 宣告與參數檢查

# v0.6.7

###### 2026/10/08

* `提示` Compose UI V2 擴充元件需要配套 AutoJs6 6.8.0 / 5322; TSX 配套需要 TypeScript Engine 0.6.7. 新宿主仍可使用舊 V1 轉譯器的原有元件, 擴充元件需要 V2 轉譯器
* `改善` Compose UI V2 元件的 TypeScript / TSX 屬性, 插槽和事件宣告

# v0.6.6

###### 2026/10/07

* `提示` 獨立彈窗需要配套 AutoJs6 6.8.0 / 5321 與支援 dialog-v1 的 Compose UI; TSX 另需 TypeScript Engine 0.6.6. 非 UI 指令碼需要宿主懸浮視窗權限
* `改善` compose.dialog 傳回可更新和關閉的工作階段, 支援一般對話框與模態底部面板, 可在 UI 或一般指令碼中使用

# v0.6.5

###### 2026/10/07

* `提示` 互操作需要相符的 AutoJs6 6.8.0 / 5320 宿主與支援 AndroidView 擴充的 Compose UI 建置; TSX 另需 TypeScript Engine 0.6.5. 基礎 V1 轉譯最低宿主仍為 5316
* `改善` Compose UI TSX: XML `<compose>` 容器與 compose.attach 可在 UI 頁面或舊浮動視窗內嵌入獨立 Compose 工作階段; compose.AndroidView 可承載現有 Android View 或同步工廠傳回的 View

# v0.6.4

###### 2026/10/05

* `改善` Rhino 專案可檢查 Compose UI TSX 元件屬性與回呼, 需要相符的本機 AutoJs6 宿主; 舊 XML TSX 繼續可用
* `改善` Android 系統應用程式資訊圖示與圖示工作台共用圖稿及明暗底色, 保留外掛程式中心透明圖稿與現有啟動器選項

# v0.6.3

###### 2026/09/19

* `修正` AGP 9.1 建置時的 SDK XML v4 解析警告及 JVM 單元測試組裝工作誤觸發 APK 原生程式庫對齊檢查的問題 (共用建置外掛 1.8.3)
* `改善` 將 compileSdk 與 targetSdk 提升到 37 (Android 17), 外掛程式行為不受新目標版本影響

# v0.6.2

###### 2026/09/13

* `修正` 外掛中心可透過受保護入口啟用新安裝的外掛, 顯示的中繼資料與實際安裝套件一致
* `修正` 外掛中繼資料的建置日期固定使用英文, 不受建置機器語言影響
* `改善` 宿主啟用, 外掛中繼資料, 多語言文件與簽章發佈彙整遵循統一外掛規範

# v0.6.1

###### 2026/09/12

* `改善` 建置階段校驗 64 位原生函式庫的 16 KB 頁面大小對齊, 檢查 manifest 契約並輸出 JSON 報告
* `改善` 由公共 Gradle 平台外掛程式統一決定建構工具版本, 移除本機相容性資料並同步維護檢查

# v0.6.0

###### 2026/08/29

* `提示` 專案編譯現已面向 AutoJs6 6.8.0 Alpha7 (build 5253) 或更高版本啟用; Node profile 另需 Node.js Runtime 外掛, Rhino profile 不需要
* `提示` 編譯與執行現共用啟動時的一份精確 snapshot; npm 裸套件/scoped package 可於編譯期解析, 並透過 Rhino CommonJS 或 Node.js Runtime 外掛在執行階段載入
* `提示` Android 7.0 / API 24 需要 Chrome/WebView 119 或更新版本; API 24/25 系統內建的舊 provider 不受支援並會 fail closed
* `新增` compiler-backed Node TypeScript 專案現可按需編譯執行期間建立的 `.ts/.mts/.cts`: Node 透過綁定精確位元組數與 SHA-256 的有界 PFD 將原始碼交給主機, 主機重用常駐專案工作階段, 保留型別診斷與 Source Map 堆疊對應, 並繼續拒絕路徑逸出, 符號連結和歧義目標
* `新增` 新增 dependency-layer policy revision 2 與 resolver policy revision 4: 主機 archive 和 production Worker 以固定順序拒絕 `.node`, `binding.gyp`, `gypfile` 與安裝生命週期 hook, 回傳穩定 native/install-script 錯誤及純 JavaScript/WASM 替代指引
* `新增` 離線真實套件驗收矩陣在 Rhino 與 Node profile 涵蓋 dayjs 1.11.13, lodash 4.17.21, zod 4.1.5, uuid 13.0.0 與 nanoid 5.1.5; resolver policy revision 3 現可從執行階段 JavaScript 回退至已安裝的 `@types`, 且不會覆蓋套件內建宣告
* `新增` 同步編譯器與 Ace 的相依套件型別: package `types`/`typings`, TypeScript 6 `typesVersions`, 巢狀宣告匯入及已安裝 `@types` 現進入同一凍結型別層; dayjs 補全, hover 與 strict TS2322 診斷在 Rhino 和 Node profile 之間一致
* `新增` 同時啟用 `compilerAvailable`, `projectCompilation`, `semanticDiagnostics` 和 `sourceMapV3` (capability mask 15), 提供完整 TypeScript Program 編譯, 結構化診斷與配對的外部 Source Map v3 輸出
* `新增` 新增公開 Rhino `.ts/.tsx` 專案編譯, 支援 UTF-8 路徑與原生 AutoJs6 TSX UI lowering; controlled tsconfig revision 3 同時涵蓋 Rhino 和 NodeNext, 提供 20 個 boolean 選項及依 profile 限定的 `target`/`lib`, revision 2 Rhino 請求保持相容
* `新增` 新增公開 NodeNext revision 2 編譯, 支援 `.mts/.cts` 與專案範圍內的 `.ts`, 並依原始碼種類和 package type 產出 `.mjs/.cjs/.js`
* `新增` 編譯器已接入 AutoJs6 執行鏈, 可點擊的編譯診斷與執行階段 stack mapping, 多檔案專案, dynamic import 映射, Tasker/排程入口及 APK Builder 加密專案記憶體流程; 封裝產物只含編譯後的 JS/map, 不含 TS 原始碼
* `新增` Ace 編輯器的 declarations 與 TypeScript 6.0.3 行為現已和執行前編譯保持一致
* `新增` controlled tsconfig revision 4 為 Rhino 與 NodeNext 新增專案內 `baseUrl`/`paths` 路徑別名; 解析僅限凍結 snapshot, 生成 specifier 會改寫為明確的執行階段相對模組, revision 2/3 身份保持精確相容
* `新增` Node ESM `.mts` 與根 `type: module` `.ts` 專案現已支援頂層 `await`; controlled ES2022 設定會產出帶配對 Source Map 的 `.mjs/.js`, 可由 Node.js Runtime 外掛直接執行
* `新增` 新增受控 Node workspace package boundary, 支援 `packages/<name>` 與 `packages/@scope/<name>`; 同一專案的 `.ts` 可依最近 `package.json.type` 混用 ESM 與 CommonJS, `scripts` 持續禁止
* `新增` 相容 Node Runtime 現以 V8 native linker 執行 ESM 入口與 dynamic `import()`, 支援循環相依, 可變匯出及 re-export live binding; 四模組 `.mts` 圖可由 `initial=1` 更新為 `updated=42`
* `新增` 新增獨立配額的唯讀 `node_modules` 相依層與 bare/scoped package 解析, 支援子路徑 `exports`, `types`/`import`/`require` 條件及 `types`/`typings`/`module`/`main`/`index` fallback, 並以 dayjs + zod 完整 Program 驗收; 同一精確相依層現可進入執行階段, Rhino 選擇 CommonJS/JSON `require` 入口並為 ESM-only 提供穩定錯誤, Node 透過不回寫相依套件的私有 workspace overlay 執行 ESM/CJS/dual package
* `改善` 新增明確 opt-in 的專案工作階段 revision 1: 服務按 caller/project 衍生身分, 在有界 Worker realm 中跨執行重用摘要版本化 SourceFile, 增量 builder 與未變輸出; 舊請求仍完全冷編譯, 記憶體壓力會丟棄全部工作階段
* `改善` 新增確定性的跨執行 cache 復用; 實測命中耗時 18-63 ms, 有界輸入 batching 在觀測裝置上降低 ingress 成本 40%-57%
* `改善` 一般輸出上限提升至 512 artifacts, 單個 JavaScript 或 map 16 MiB, 合計 64 MiB; 一般 caller 輸入升級至 revision 3 的 4096 files, 單檔案 8 MiB, 合計 64 MiB, 外掛內建 declarations 不占 caller 配額, revision 2 舊宿主繼續相容 512 files / 8 MiB
* `改善` 冷專案 ingress 進一步採用最多 6 條中間 frame 且最多 4 條 payload chunk 的 batching; 固定 109-file 測試從 97 降至 68 個 envelopes, 四組 Android/WebView 的 compiler 與 ingress 中位數均改善

# v0.5.0

###### 2026/07/27

* `提示` R1 全部退出門檻現已完成, 包括 API 24/25 Chrome 119 modern-provider runtime matrix; `compilerAvailable=false` 仍為權威狀態, 因為正式專案編譯屬於 R2
* `修正` service destruction 先完成 WebView, Worker, MessagePort, ServiceWorker client, pending request 和 Handler callback 清理, 再發布 close report 並喚醒等待方; 同時消除 deadline 與終態 publication 的競態
* `修正` `/proc` 與 FD 觀測綁定 `(pid,starttime)`, 對不可用或部分 shell 輸出 fail closed, 並避免行程結束輪詢觸發 UiAutomation finish 競態
* `改善` Android 12 和 Android 13 實機各驗證 10 個 cold compiler process 與 100 次 hot prewarm; compiler service FD 最終精確回到 baseline, 且不殘留 staging archive
* `改善` API 33 與 WebView 109 通過 none, Node-only, Ace-only 和 Node+Ace 四種安裝態; 靜態門檻禁止 production source, manifest 和 direct dependency 發現或依賴 Node/Ace

# v0.4.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 在 v0.4.0 中仍為權威狀態: API 24 僅通過 Bundle/FD transport 門檻, 舊 WebView 無法解析原始 TypeScript 6.0.3 compiler JavaScript; API 24/25 modern-provider matrix 仍待完成
* `修正` Binder 在反序列化前拒絕 Bundle raw transport 中的 FD, 並在 prewarm, compilation 和 cancellation 拒絕路徑釋放傳輸副本
* `修正` Bundle metadata 驗證僅接受實際 runtime type 為 `String[]` 的字串陣列, 並拒絕頂層 null value, Parcelable array, Binder, Serializable 和 nested Bundle
* `修正` 以 epoch 合併 timeout recovery 執行, 防止 stale recovery 銷毀替換 session; 確定性連續 1 ms timeout 門檻驗證排隊 probe 可恢復
* `改善` Android 12 和 Android 13 實機通過真實 WebView renderer 結束門檻: 首次結束只重試一次, 第二次結束穩定失敗, 後續的一般 probe 可恢復
* `改善` Android 12 和 Android 13 實機各完成 10 輪 `:typescript_compiler` 行程重啟並保持 distribution/declarations fingerprints 穩定; API 24 另通過 6 項 Bundle/FD transport 門檻

# v0.3.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 在 v0.3.0 中仍為權威狀態: R1 prewarm 僅執行受限 compiler host golden 探針, R2 完成前仍無法進行專案編譯
* `新增` 獨立 `:typescript_compiler` 行程中的 WebView/Worker compiler host 使用私有 HTTPS asset origin, 精確資源 allowlist, WebMessagePort 和收緊的 network, file, content, storage 及 window 權限
* `新增` R1 prewarm 實際載入 TypeScript 6.0.3 並驗證 runtime version, enum emit, 結構化語法診斷, Source Map v3, 固定輸出 fingerprints 和無 inline source content
* `改善` Android 12 和 Android 13 實機門禁涵蓋每部裝置 100 次熱探針及逾時後的 Worker 重建恢復, Android 13 亦透過獨立 helper 驗證 minified release, 同時保持全部專案編譯能力為 false
* `依賴` 附加 AndroidX WebKit 版本 1.14.0

# v0.2.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 在 v0.2.0 中仍為權威狀態: R1 僅準備並驗證 compiler distribution 和 IPC/FD transport, R2 完成前仍無法進行專案編譯
* `新增` TypeScript 6.0.3 官方發行套件管線驗證 SHA-1, SHA-256 和 SRI SHA-512 三種摘要, 透過嚴格 tar entry allowlist 安全擷取檔案, 並產生 canonical `distribution.json` 以及 compiler, standard libraries, generator 和 distribution fingerprints
* `新增` Debug/release APK 內容門檻依據 `distribution.json` 重新驗證實際封裝位元組, 確保 APK 僅包含一份 allowlist 內的 `lib/typescript.js`, 且 declarations, license 和 third-party notice 完整一致
* `新增` Instrumentation 涵蓋真實遠端 `:typescript_compiler` 行程的 Binder 服務探索/能力與 R0 同步拒絕, 驗證拒絕後呼叫端持有的 FD 仍然有效, 且無效或共用 descriptor 在有界短觀察窗內未觸發 callback
* `依賴` 附加 AndroidX Test Runner 版本 1.7.0
* `依賴` 附加 AndroidX Test JUnit Extension 版本 1.3.0

# v0.1.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 表示 R0 僅提供外掛探索和暫定編譯合約, 尚無法編譯或執行 TypeScript
* `新增` 獨立的 AutoJs6 TypeScript 編譯前端外掛工程, 精確鎖定 TypeScript 6.0.3, 並與 Node.js Runtime 外掛保持零依賴
* `新增` 透過受 `org.autojs.permission.PLUGIN` 保護的 `org.autojs.plugin.INFO` 和 `org.autojs.plugin.typescript.COMPILER` 服務提供外掛探索及編譯能力協商
* `新增` 編譯服務在獨立的 `:typescript_compiler` 行程執行, 暫定 Binder envelope 使用小型 `Bundle` 中繼資料和 `ParcelFileDescriptor` 輸入/輸出 archive
* `新增` R0 自動化門檻涵蓋 API contract, admission/rejection, compiler distribution lock, Node Runtime 獨立性, Lint 和 universal APK 內容
