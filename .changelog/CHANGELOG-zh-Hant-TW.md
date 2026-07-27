******

### 發行歷史

******

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
