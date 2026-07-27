******

### リリース履歴

******

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
