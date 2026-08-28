******

### リリース履歴

******

# v0.6.0

###### 2026/08/28

* `ヒント` AutoJs6 6.8.0 Alpha7 (build 5253) 以降で project compilation を有効化; Node profile では Node.js Runtime plugin も必要だが Rhino profile では不要
* `ヒント` compile と execution は起動時の同一 exact snapshot を使用; bare/scoped npm package は compile 時に解決され, Rhino CommonJS または Node.js Runtime plugin により runtime で load される
* `ヒント` Android 7.0 / API 24 では Chrome/WebView 119 以降が必要; API 24/25 に同梱された旧 provider は非対応で fail closed になる
* `追加` dependency-layer policy revision 2 と resolver policy revision 4 を追加: host archive と production Worker は `.node`, `binding.gyp`, `gypfile`, install lifecycle hook を安定した native/install-script error と pure JavaScript/WASM の案内付きで決定的に拒否します
* `追加` Rhino と Node の両 profile で dayjs 1.11.13, lodash 4.17.21, zod 4.1.5, uuid 13.0.0, nanoid 5.1.5 を検証するオフライン実パッケージ受け入れマトリクスを追加し, resolver policy revision 3 では同梱宣言を上書きせず runtime JavaScript からインストール済み `@types` へフォールバックできるようにしました
* `追加` compiler と Ace の dependency package type を同期: package `types`/`typings`, TypeScript 6 `typesVersions`, nested declaration import, installed `@types` が同じ frozen type layer に入り, dayjs completion, hover, strict TS2322 diagnostic が Rhino/Node profile で一致
* `追加` `compilerAvailable`, `projectCompilation`, `semanticDiagnostics`, `sourceMapV3` を同時に有効化 (capability mask 15); 完全な TypeScript Program compile, structured diagnostics, 対応する external Source Map v3 output を提供
* `追加` UTF-8 path と AutoJs6 native TSX UI lowering に対応した public Rhino `.ts/.tsx` project compilation を追加; controlled tsconfig revision 3 は Rhino と NodeNext の両方で 20 個の boolean option と profile-scoped `target`/`lib` を提供し, revision 2 Rhino request も引き続き互換
* `追加` `.mts/.cts` と project-scoped `.ts` に対応する public NodeNext revision 2 compilation を追加し, source kind と package type に応じて `.mjs/.cjs/.js` を出力
* `追加` compiler を AutoJs6 execution, clickable compile diagnostics, runtime stack mapping, multi-file project, dynamic import mapping, Tasker/scheduled entry, APK Builder の encrypted-project in-memory flow に統合; package には compile 済み JS/map のみを含み TS source は含めない
* `追加` Ace editor declarations と TypeScript 6.0.3 behavior を execution 前 compile に一致させた
* `追加` controlled tsconfig revision 4 で Rhino と NodeNext に project-local `baseUrl`/`paths` alias を追加; resolution は frozen snapshot 内に限定し, emit specifier は明示的な runtime-relative module に書き換え, revision 2/3 identity は完全互換を維持
* `追加` Node ESM `.mts` および root が `type: module` の `.ts` project で top-level `await` を有効化; controlled ES2022 設定から対応する Source Map 付きの `.mjs/.js` を出力し, Node.js Runtime plugin で直接実行可能
* `追加` `packages/<name>` と `packages/@scope/<name>` に controlled Node workspace package boundary を追加; 同一 project の `.ts` は nearest `package.json.type` により ESM と CommonJS を混在でき, `scripts` は引き続き禁止
* `追加` 対応 Node Runtime は ESM entry と dynamic `import()` を V8 native linker で実行し, 循環依存, 可変 export, re-export live binding を保持; 4 module の `.mts` graph は `initial=1` から `updated=42` に更新可能
* `追加` 独立 quota の read-only `node_modules` dependency layer と bare/scoped package resolution を追加; subpath `exports`, `types`/`import`/`require` condition, `types`/`typings`/`module`/`main`/`index` fallback, dayjs + zod の full Program acceptance を含む; 同じ exact layer が runtime に渡され, Rhino は CommonJS/JSON `require` entry を選択して ESM-only を stable error にし, Node は dependency を write-back しない private workspace overlay で ESM/CJS/dual package を実行
* `改善` deterministic cross-execution cache reuse を追加; 実測 hit は 18-63 ms で完了し, bounded input batching により観測 device の ingress cost を 40%-57% 削減
* `改善` ordinary output limit を 512 artifacts, JavaScript または map ごとに 16 MiB, aggregate 64 MiB へ拡大し, ordinary caller input を revision 3 の 4096 files, file ごとに 8 MiB, aggregate 64 MiB へ昇格; built-in declarations は caller quota の対象外で, revision 2 host の 512 files / 8 MiB compatibility path も維持
* `改善` cold project ingress は middle frame を最大 6 条にまとめつつ payload chunk を最大 4 条に維持; 固定 109-file fixture は 97 から 68 envelopes へ減少し, 4 組の Android/WebView で compiler と ingress の median が改善

# v0.5.0

###### 2026/07/27

* `ヒント` R1 のすべての終了ゲートが完了し, API 24/25 の Chrome 119 modern-provider runtime matrix も通過済み. production project compilation は R2 の範囲であるため, `compilerAvailable=false` は引き続き正式な状態を示す
* `修正` service destruction で WebView, Worker, MessagePort, ServiceWorker client, pending request, Handler callback を解放してから close report を公開し waiter を起動; deadline と terminal publication の race も解消
* `修正` `/proc` と FD の観測を `(pid,starttime)` に結び付け, 利用不能または部分的な shell 出力を fail closed とし, process exit polling 時の UiAutomation finish race を回避
* `改善` Android 12 と Android 13 の各 device で 10 個の cold compiler process と 100 回の hot prewarm を検証; compiler service FD は staging archive を残さず正確に baseline へ復帰
* `改善` API 33 と WebView 109 で none, Node-only, Ace-only, Node+Ace の各 installation state を通過; static gate で production source, manifest, direct dependency による Node/Ace の discovery と dependency を禁止

# v0.4.0

###### 2026/07/27

* `ヒント` `compilerAvailable=false` は v0.4.0 でも正式な状態を示す: API 24 は Bundle/FD transport gate のみ通過し, 古い WebView は元の TypeScript 6.0.3 compiler JavaScript を解析できない; API 24/25 modern-provider matrix は引き続き未完了
* `修正` deserialization 前に raw Bundle transport 内の FD を拒否し, prewarm, compilation, cancellation の各 reject path で transport copy を解放
* `修正` Bundle metadata の string array を実際の runtime type が `String[]` のものだけに制限し, top-level null value, Parcelable array, Binder, Serializable, nested Bundle を拒否
* `修正` timeout recovery の実行を epoch で統合し, stale recovery による replacement session の破棄を防止; 連続する 2 回の 1 ms timeout を使う deterministic gate で queued probe の recovery を検証
* `改善` Android 12 と Android 13 で実際の WebView renderer exit gate を通過: 最初の exit は 1 回だけ retry し, 2 回目は確実に失敗し, その後の通常 probe は recovery に成功
* `改善` Android 12 と Android 13 の各 device で `:typescript_compiler` process を 10 回 restart しても distribution/declarations fingerprints が安定; API 24 は別途 6 件の Bundle/FD transport gate を通過

# v0.3.0

###### 2026/07/27

* `ヒント` `compilerAvailable=false` は v0.3.0 でも正式な状態を示す: R1 prewarm は制限された compiler host の golden probe のみを実行し, R2 完了前は project compilation を利用できない
* `追加` 専用 `:typescript_compiler` process に private HTTPS asset origin, 厳密な resource allowlist, WebMessagePort, 制限された network, file, content, storage, window capabilities を持つ WebView/Worker compiler host を構築
* `追加` R1 prewarm で実際の TypeScript 6.0.3 runtime を読み込み, runtime version, enum emit, structured syntax diagnostic, Source Map v3, 固定 output fingerprints, inline source content がないことを検証
* `改善` Android 12 と Android 13 の各 device で 100 回の hot probe と timeout 後の Worker rebuild recovery を検証し, Android 13 の独立 helper で minified release も検証しながら全ての project compilation capabilities は false を維持
* `依存関係` AndroidX WebKit バージョン 1.14.0 を追加

# v0.2.0

###### 2026/07/27

* `ヒント` `compilerAvailable=false` は v0.2.0 でも正式な状態を示す: R1 は compiler distribution と IPC/FD transport の準備と検証のみを行い, R2 完了前は project compilation を利用できない
* `追加` 公式 TypeScript 6.0.3 distribution の再現可能な pipeline で SHA-1, SHA-256, SRI SHA-512 の 3 種類の digest を検証し, 厳格な tar entry allowlist だけを安全に抽出して canonical `distribution.json` と compiler, standard libraries, generator, distribution fingerprints を生成
* `追加` Debug/release APK content gate で `distribution.json` に基づいて実際の package bytes を再検証し, allowlist 内の `lib/typescript.js` が 1 つだけ存在することと declarations, license, third-party notice の完全性を保証
* `追加` Instrumentation で実際のリモート `:typescript_compiler` プロセスの Binder discovery/capability と R0 の同期拒否を検証し, 拒否後も呼び出し側が所有する FD は有効であり, 無効または共有された descriptor は有界の短い観測時間内に callback を発生させないことを確認
* `依存関係` AndroidX Test Runner バージョン 1.7.0 を追加
* `依存関係` AndroidX Test JUnit Extension バージョン 1.3.0 を追加

# v0.1.0

###### 2026/07/27

* `ヒント` `compilerAvailable=false` は R0 がプラグイン検出と暫定コンパイル契約のみを公開し, TypeScript のコンパイルまたは実行はまだできないことを示す
* `追加` 独立した AutoJs6 TypeScript コンパイラフロントエンドプラグインプロジェクトを構築し, TypeScript 6.0.3 を固定して Node.js Runtime プラグインへの依存をゼロに維持
* `追加` `org.autojs.permission.PLUGIN` で保護された `org.autojs.plugin.INFO` と `org.autojs.plugin.typescript.COMPILER` サービスによるプラグイン検出とコンパイラ機能ネゴシエーション
* `追加` 専用の `:typescript_compiler` プロセスでコンパイラサービスを実行し, 暫定 Binder envelope で小さな `Bundle` メタデータと `ParcelFileDescriptor` による入出力 archive を転送
* `追加` API contract, admission/rejection, compiler distribution lock, Node Runtime 非依存性, Lint, universal APK 内容を対象とする R0 自動ゲート
