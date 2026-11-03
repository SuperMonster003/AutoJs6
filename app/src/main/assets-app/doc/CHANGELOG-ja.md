******

### リリース履歴

******

# v6.8.0

###### 2026/11/03 - 一部機能をプラグイン化, 更新時は注意

* `ヒント` 一部の組み込み機能 (OCR, バーコード認識, OpenCV, OpenCC, Pinyin, MediaInfo, ターミナル, オフライン文書など) を独立したプラグインで提供, 使用前にプラグインセンターで対応プラグインのインストールと有効化が必要
* `ヒント` Rhino エンジンの Promise と一部の構文の動作を ECMAScript 仕様に準拠 (Promise コールバックをマイクロタスクで実行, const 変数への再代入で TypeError をスローなど), 旧動作に依存するスクリプトは調整が必要な場合あり
* `ヒント` console 要素と浮動コンソールは現在のスクリプトのログを表示し, グローバルログは globalconsole 要素またはログページで確認
* `ヒント` パッケージ化アプリの対象 SDK を 29 から 37 に変更, 共有ストレージとローカルネットワークへのアクセスにはシステム要件に従う許可が必要
* `ヒント` パッケージ化アプリで images など OpenCV に依存する機能を使用する場合, パッケージ作成時に OpenCV プラグインの選択が必要
* `新機能` 設定ウィザード, 初回使用時に権限申請とプラグインインストールを順に完了し, 初回起動後はホーム画面の歓迎メッセージから利用可能 (場所: 設定 > 構成 > 設定ウィザード)
* `新機能` プラグインインストールウィザード, 公式プラグインをカテゴリ別に選択して一括インストールし, Shizuku/Root/Dhizuku の権限がある場合は 3-Setup Installer プラグインでサイレントインストール (場所: 設定ウィザード/ホームのプラグインタブの浮動ボタンメニュー/プラグインセンターの "+" メニュー)
* `新機能` 権限申請ウィザード, 必要な権限を選択して順に申請し, "標準"/"最小"/"すべて" の組み込みプロファイルとカスタムプロファイルに対応 (場所: 設定ウィザード/ホームドロワーの "権限" グループ見出しボタン)
* `新機能` Rhino エンジンが class 構文に対応し, フィールド, 静的ブロック, プライベートメンバー, Error/Array/Map などの組み込み型の継承, Java クラスとインターフェースの継承をサポート (参照: プロジェクト文書 > [クラス構文](https://docs.autojs6.com/#/classSyntax))
* `新機能` Rhino エンジンが async/await に対応し, async 関数, async アロー関数, async メソッド, async ジェネレーター, for await をサポート (参照: プロジェクト文書 > [async/await と continuation](https://docs.autojs6.com/#/asyncAwait))
* `新機能` Node.js Runtime と Bun Runtime プラグイン, Node.js と Bun のスクリプトやプロジェクトを実行し, 自動化, 画像, UI, ファイルなど AutoJs6 の大部分のスクリプト機能を利用可能
* `新機能` Python Runtime, Lua Runtime, QuickJS Runtime プラグイン, Python, Lua, QuickJS (JavaScript) のスクリプトを実行
* `新機能` Java Runtime と Kotlin Runtime プラグイン, Java/Kotlin ソースをコンパイルして実行 (Java は Android 7+, Kotlin は Android 8+ が必要)
* `新機能` TypeScript Engine プラグイン, TypeScript ファイルとプロジェクトをコンパイルして実行し, 型チェックと UI モードの TSX レイアウトに対応, 定期タスク, ショートカット, ログページからも TypeScript を実行可能
* `新機能` ai モジュール, AI プロバイダープラグイン (3-Stone AI など) を通じてローカルまたはオンラインのモデルを呼び出し, ストリーミング出力, 永続セッション, 構造化 JSON 結果に対応 (参照: サンプルコード > ai)
* `新機能` ai.agent スクリプト API, 3-Stove Agent のタスク (プランモードとバックグラウンドタスクを含む) を開始, 制御し, スクリプトをエージェントから呼び出せるよう登録可能 (3-Stove Agent プラグインが必要) (参照: サンプルコード > agent)
* `新機能` MCP Server プラグイン, MCP クライアントから AutoJs6 のスクリプト, ファイルとデバイス操作を利用 (場所: ホームドロワー/プラグインセンター)
* `新機能` compose モジュール, Jetpack Compose で UI, 浮動ウィンドウ, ダイアログを構築し, コードエディターから 3-Block Compose ビジュアルデザイナーで UI を開いて編集可能 (3-Block Compose プラグインが必要) (参照: サンプルコード > 3-Block Compose)
* `新機能` speech モジュール, オフライン音声認識, ウェイクワード検出, 音声合成, 話者認識, 音声分析を提供 (3-Macaw Speech プラグインが必要) (参照: サンプルコード > 3-Macaw Speech)
* `新機能` 3-Shell Terminal プラグイン, アプリ内でターミナルを使用し, スクリプトから terminal グローバルオブジェクトでターミナルセッションを管理 (場所: ホームドロワー/プロジェクトツールバー/ファイルマネージャーのフォルダーメニュー) (参照: サンプルコード > ターミナル)
* `新機能` installer モジュール, アプリのインストール, 更新, アンインストールを行い, Shizuku/Root/Dhizuku によるサイレント操作に対応 (3-Setup Installer プラグインが必要) (参照: プロジェクト文書 > [インストーラー](https://docs.autojs6.com/#/installer))
* `新機能` mail モジュール, メールの送受信, 検索, 添付ファイルのダウンロード, 新着メールの監視に対応 (3-Stamp Mail プラグインが必要) (参照: プロジェクト文書 > [メール](https://docs.autojs6.com/#/mail))
* `新機能` epub モジュール, 電子書籍の内容の読み取りとリーダーの起動に対応 (3-Folio EPUB プラグインが必要) (参照: プロジェクト文書 > [電子書籍](https://docs.autojs6.com/#/epub))
* `新機能` gallery モジュール, 画像の選択, 表示, 編集, 変換に対応 (3-Maple Image プラグインが必要) (参照: プロジェクト文書 > [ギャラリー](https://docs.autojs6.com/#/gallery))
* `新機能` imgui グローバルオブジェクト, Dear ImGui で浮動ウィンドウと全画面 UI を構築 (ImGui プラグインが必要)
* `新機能` yolo モジュール, YOLO プラグインによる物体検出 (参照: プロジェクト文書 > [YOLO](https://docs.autojs6.com/#/yolo))
* `新機能` files.batchRename メソッド, 検索と置換, 正規表現, カウンター, 日付などのルールでファイルを一括リネームし, プレビューと元に戻すに対応 (Batch Rename プラグインが必要) (参照: プロジェクト文書 > [ファイル](https://docs.autojs6.com/#/files))
* `新機能` plugins.tools モジュール, プラグインが提供するツールの一覧取得と呼び出し (ツールを提供するプラグインが必要) (参照: プロジェクト文書 > [プラグイン](https://docs.autojs6.com/#/plugins))
* `新機能` images.quantize/quantizeToFile メソッドと images.save などの量子化オプション, PNG 画像を圧縮 (Image Quantization プラグインが必要)
* `新機能` flow モジュールと waitAsync グローバルメソッド, 待機と操作をチェーン形式で組み立て, 分岐, ループ, タイムアウト, 再試行, キャンセルに対応 (参照: プロジェクト文書 > [フロー](https://docs.autojs6.com/#/flow))
* `新機能` smartClick/scrollUntil/typeInto/launchAndWait などの自動化メソッドと waitForIdle/waitForToast などの待機メソッド, ウィジェットのクリック, スクロール検索, テキスト入力, アプリ切り替えに対応 (Flow のチェーン呼び出しに対応)
* `新機能` select メソッドが文字列セレクター構文に対応し, 属性, 正規表現, 真偽条件を組み合わせ可能
* `新機能` UiObject とウィジェットコレクションに isStale/waitUntilGone/toJSON/first/last/clickEach などのメソッドを追加, ノード状態の待機, シリアライズ, ウィジェットごとの操作に対応
* `新機能` auto.findWindows/explain/dump メソッド, 条件によるウィンドウの取得, セレクターの診断, ノードツリーのダンプに対応
* `新機能` shell.spawn/shizuku.spawn メソッド, コマンドをプロセスとして実行して出力をリアルタイムに読み取り, 入力の書き込み, 疑似端末, プロセスの終了に対応 (adb オプションと shizuku.spawn は Shizuku, root オプションは Root 権限が必要)
* `新機能` Shell コンストラクターが { cmd, root, adb, pty, env, cwd } オプションオブジェクトに対応し, $shell.setDefaultOptions/checkAccess などのメソッドを追加 (Auto.js Pro 互換)
* `新機能` settings モジュール, アプリ設定の読み取り, 変更, カテゴリ別エクスポートに対応 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (参照: プロジェクト文書 > [設定](https://docs.autojs6.com/#/settings))
* `新機能` work_manager モジュール, 定期タスクとブロードキャストタスクを管理 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (参照: プロジェクト文書 > [Work Manager](https://docs.autojs6.com/#/workManager))
* `新機能` power_manager モジュール, バッテリー最適化除外の確認と申請に使用 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (参照: プロジェクト文書 > [Power Manager](https://docs.autojs6.com/#/powerManager))
* `新機能` memory モジュール, スクリプトから AutoJs6 のメモリ診断データ (メモリスナップショット, 回収されていないスクリプトランタイム, hprof ヒープダンプなど) を読み取り (参照: プロジェクト文書 > [メモリ](https://docs.autojs6.com/#/memory)) _[`issue #452`](http://issues.autojs6.com/452)_
* `新機能` tts モジュール, テキスト読み上げに使用 (参照: プロジェクト文書 > [テキスト読み上げ](https://docs.autojs6.com/#/tts))
* `新機能` pangu グローバルオブジェクト, 中国語と欧文の混在時の空白処理と検査に使用 (参照: プロジェクト文書 > [テキスト間隔](https://docs.autojs6.com/#/pangu))
* `新機能` colors モジュールに distance/blend/contrast/harmony などのメソッドと colors.theme オブジェクトを追加, 色の計算, 配色の生成, OKLab/OKLCH 色空間の変換, アプリのテーマカラーの変更に対応 (参照: サンプルコード > 画像と色)
* `新機能` images.countPointsByColor/getMeanColor メソッド, 一致する色の画素数と平均色を取得
* `新機能` device.getMarketName メソッドと device.pageSize プロパティ (getter), デバイスの市場名とシステムのメモリページサイズを取得 _[`issue #513`](http://issues.autojs6.com/513)_
* `新機能` console.setTimeVisible/setTimeFormat/setColorful/setAvoidStatusBar/setInputVisible メソッドと対応する console.build オプション
* `新機能` runtime.loadAar/loadJarWithR8 メソッド, AAR ファイルの動的読み込みと R8 Compiler プラグインによる JAR ファイルの読み込みに対応 (後者は実験的)
* `新機能` UI レイアウトがプロジェクトの res ディレクトリの文字列, 色, スタイル, 画像などのリソースに対応し, ナイトモード, 言語, 画面の向きなどに応じた修飾子ディレクトリを選択 (参照: プロジェクト文書 > [ユーザーインターフェース](https://docs.autojs6.com/#/ui))
* `新機能` ネイティブ Android XML レイアウトがより多くのウィジェット属性, ConstraintLayout/CoordinatorLayout レイアウト, Material コンポーネントに対応 (参照: プロジェクト文書 > [Android XML 属性](https://docs.autojs6.com/#/uiAndroidAttributes))
* `新機能` ui.useAndroidLayout メソッド, Android ネイティブレイアウト要素の対応を有効化
* `新機能` UI モードの globalconsole 要素でグローバルログを表示し, console 系要素でタイトル, スタイルと入力欄の設定に対応
* `新機能` UI モードのイベントリスナーがコルーチンの中断 (dialogs.confirm(...).await() など) に対応し, スクリプトプロジェクトで continuation 機能の有効化が必要 (参照: プロジェクト文書 > [継続](https://docs.autojs6.com/#/continuation))
* `新機能` sqlite モジュールのデータベースコールバックが onDowngrade メソッドに対応し, データベースのバージョンダウンを処理 (参照: プロジェクト文書 > [SQLite](https://docs.autojs6.com/#/sqlite))
* `新機能` "AutoJs6 設定の管理" ページ, アプリ設定のカテゴリ別エクスポート/インポート, 既定値への復元, 直前の操作の取り消しに対応し, 認証情報の暗号化保存と自動バックアップをサポート (場所: 設定 > 構成 > AutoJs6 設定の管理)
* `新機能` ショートカットバックアップ機能, ホーム画面のショートカットを自動保存し, エクスポート/インポートと一括復元に対応 (場所: 設定 > 構成 > ショートカットバックアップ)
* `新機能` アクセシビリティサービスマネージャー, サービスの開始/停止, キャッシュ削除, 自己検査, イベント監視と診断情報のコピーに対応 (場所: ホームドロワーのアクセシビリティサービス項目)
* `新機能` ファイルマネージャーが複数ファイルの選択と一括コピー/移動/削除に対応し, パスバーを追加して親フォルダーへの移動, 戻る/進む, SD カードへの切り替えに対応
* `新機能` ファイルマネージャーが Shizuku または Root による制限付きフォルダーの閲覧とファイルの編集に対応 (対応する権限が必要, アクセス可能な範囲はシステムによる)
* `新機能` ファイルマネージャーのスクリプトメニューに "浮動実行", 浮動ツールバーでスクリプトの実行/停止, 編集とログ確認に対応
* `新機能` ファイルマネージャーが対応するプラグインを通じて, アーカイブの閲覧, Markdown/HTML ファイルのプレビュー, 画像の表示, 音声と動画の再生, 電子書籍の表示に対応
* `新機能` ファイルマネージャーが APK/APKS/XAPK/AAB などのインストールパッケージの情報とマニフェストの表示に対応
* `新機能` ファイルマネージャーが Batch Rename プラグインによるファイルとフォルダーの一括リネームに対応し, 新しい名前のプレビュー, 元に戻す, リネーム履歴をサポート (場所: 複数選択ツールバー/フォルダーメニュー/リネームダイアログの "その他" メニュー)
* `新機能` ファイルマネージャーのスクリプトメニューに "AutoJs4 で実行" と "AutoJs4 で実行して比較" を追加, AutoJs4 エンジンでスクリプトを実行して結果を比較 (AutoJs4 Runtime プラグインが必要)
* `新機能` コードエディターが Ace Editor プラグインによる Ace エディターに対応し, コード補完, 引数ヒント, ホバー文書, 診断, ショートカットパネルを提供, 設定でエディターエンジンを選択可能
* `新機能` コードエディター内で ui モードスクリプトのレイアウトをプレビューでき, プレビュー内のウィジェットをタップすると対応するソースコードへ移動 (場所: ツールバーの "プレビュー" スイッチ)
* `新機能` コードエディターの下部ログパネル, スクリプトの開始/停止とスタックフレームのソースへの移動に対応 (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `新機能` コードエディターが専用の設定ページ, 没入型全画面モード, ファイルエンコーディングの変換に対応し, 情報バーにカーソル位置とエンコーディングを表示
* `新機能` レイアウト分析に検索バーと "セレクターとしてコピー" を追加し, ノードツリーの保存とオフライン分析に対応, "コード生成" がより多くの待機方式, 操作, Flow スタイルに対応
* `新機能` テーマカラーがカスタムカラーライブラリに対応し, 新規作成, 複製, インポート/エクスポートとライブラリ内の色の管理が可能
* `新機能` スマートカラーライブラリ, ナイトモード, 時間帯, 日の出/日の入り, バッテリー残量などの状態に応じてテーマカラーを自動で切り替え, "壁紙に従う" などのプリセットを内蔵
* `新機能` バージョン履歴がプレビュー, 差分比較, 手動スナップショット, バージョンの固定に対応し, ごみ箱が一括復元と内容のプレビューに対応, どちらも保持ポリシーを設定可能
* `新機能` 定期タスクで月次/年次/カウントダウンと繰り返し終了条件に対応 _[`issue #294`](http://issues.autojs6.com/294)_ _[`issue #537`](http://issues.autojs6.com/537)_
* `新機能` プロジェクトフォルダーで定期タスクとブロードキャストタスクを作成でき, ブロードキャストタスクに "メール受信時" タイプを追加 (3-Stamp Mail プラグインが必要)
* `新機能` ホームドロワーの "画面の色を取得" ツール (3-Color Picker プラグインが必要)
* `新機能` 開発者オプションに "メモリ診断" ページを追加, メモリ使用量と終了後も回収されていないスクリプトを表示し, hprof ヒープダンプのエクスポートに対応 (場所: 開発者オプション > メモリ診断)
* `新機能` 開発者オプションでプロセス終了記録を表示可能 (Android 11+), また Node.js スクリプトをブレークポイントでデバッグするための "Node.js スクリプトで Inspector を許可" スイッチを追加
* `新機能` コード生成モデルの管理ページ, ローカルからのインポート, 必要に応じたダウンロード, 複数バージョンの管理, 公式モデルの更新に対応 (場所: 設定 > アクセシビリティサービス > コード生成モデル)
* `新機能` パッケージ化アプリが Node.js プロジェクトとスクリプトに対応, 実行時に Node.js Runtime プラグインが必要
* `新機能` パッケージ化アプリがビルド時の TypeScript プロジェクトのコンパイルに対応し, コンパイル結果の暗号化も可能
* `新機能` パッケージ設定でログの表示/非表示とランチャーショートカットの設定に対応 _[`issue #550`](http://issues.autojs6.com/550)_
* `新機能` VSCode プラグイン接続で QR スキャン, ペアリング確認と LAN 検出に対応 (VSCode プラグイン 1.1.0+ が必要)
* `新機能` VSCode プラグインからのリモートファイル管理とデバイス上でのスクリプト実行に対応 (VSCode プラグイン 1.1.0+ が必要)
* `新機能` クライアントモードとサーバーモードに接続マネージャーを追加し, クライアントモードで予期しない切断後の自動再接続に対応
* `修正` スクリプトの終了後や一部のページを閉じた後にメモリが解放されず, 繰り返し実行するとメモリ使用量が増え続ける問題 (threads.start, タイマー, WebSocket, webview ウィジェット, runtime.loadDex を使うスクリプトなど) _[`issue #548`](http://issues.autojs6.com/548)_
* `修正` 新しいバージョンが書き込んだデータ (ダウングレードインストール後など) があると AutoJs6 とパッケージ化アプリが起動時にクラッシュする, またはバージョン履歴やごみ箱などのデータを開けない問題
* `修正` Android 14+ で画面キャプチャ権限申請時にフォアグラウンドサービス起動の異常でアプリがクラッシュする場合がある問題 _[`issue #516`](http://issues.autojs6.com/516)_
* `修正` Android 14+ で画面の回転後や画面が静止した後に images.captureScreen が SecurityException をスローする問題
* `修正` 複数のスレッドやファイルから OCR を繰り返し呼び出すと画面キャプチャ権限を何度も申請する場合がある問題 _[`issue #551`](http://issues.autojs6.com/551)_
* `修正` Paddle OCR プラグインのサービスが切断後に回復しない, または未実行時に自動で起動しない問題 _[`issue #515`](http://issues.autojs6.com/515)_ _[`issue #525`](http://issues.autojs6.com/525)_ _[`issue #517`](http://issues.autojs6.com/517)_
* `修正` 一部のシステム (ColorOS など) で未実行のプラグインに接続できず, プラグインセンターで手動で有効化する必要があった問題
* `修正` パッケージ化アプリで Paddle OCR と Rapid OCR を順に使うとクラッシュする場合がある問題, および複数バージョンの Paddle OCR プラグインを同時に選択すると OCR を使えない問題
* `修正` app.isInstalled/kill などでパッケージ名引数を正常に使えない問題 _[`issue #495`](http://issues.autojs6.com/495)_
* `修正` dialogs.multiChoice の複数選択結果が誤って文字列に変換される問題 _[`issue #518`](http://issues.autojs6.com/518)_
* `修正` ループ本体の const 宣言が反復ごとに再代入されない問題 (for/while ループ本体の `const item = list[i]` が常に最初の値のままになるなど)
* `修正` Rhino エンジンのスコープ, 引数変換, 一部の組み込みメソッド (Object.fromEntries, Object.defineProperty など) の動作が正しくない問題 _[`Rhino #2518`](https://github.com/mozilla/rhino/issues/2518)_
* `修正` Android 7.x で Unicode プロパティエスケープを使う正規表現が例外をスローする問題
* `修正` runtime.loadJar が新しい Java バージョンでコンパイルされた class ファイルを読み込めない問題, および Android 7.x で runtime.loadDex/loadJar が失敗する場合がある問題
* `修正` コルーチン内の await が拒否時にエラーをスローしない, 再開後に再び中断するとスクリプトが終了する, Promise の wait メソッドがスクリプトスレッドでブロックし続ける問題
* `修正` Root 権限のないデバイスで shell 関連のメソッドを呼び出すとフリーズする場合がある問題
* `修正` スレッドの終了時にスクリプトのダイアログが閉じる場合や, スクリプトの停止時に子スレッドが画像を処理中の場合にアプリがクラッシュする場合がある問題
* `修正` 子スレッドやタイマーの終了後にスクリプトがまれに自動終了しない問題, および setInterval タイマーを持つスクリプトの停止時にまれにエラーになる問題
* `修正` ui.run, threads.disposable, ブロッキングダイアログ, ジェスチャーなどのメソッドが他のスレッドの結果を待つ際にまれに早く戻る, またはブロックし続ける問題
* `修正` 浮動コンソールの位置とサイズの設定が呼び出し順の影響を受ける問題, およびタイトルバーのドラッグが効かなくなる場合がある問題
* `修正` console.log などでオブジェクトや配列を出力すると文字列値が単一引用符で囲まれず, Auto.js 4 の形式と異なる問題
* `修正` http リクエストの insecure オプションにより後続のリクエストが証明書の検証をスキップする場合がある問題, およびリリース版の HTTP リクエストがネットワークセキュリティ構成により予期せずブロックされる場合がある問題
* `修正` http.getAsync などが返す Promise の then コールバックでレスポンス本文を読み取ると NetworkOnMainThreadException がスローされる問題
* `修正` UI モードの WebView ウィジェットでファイル選択画面を開けない問題
* `修正` UI モードの list/grid ウィジェットのアイテムテンプレート内の式が現在のアイテムにアクセスできず, リスト項目が空白になる問題
* `修正` UI レイアウトで RelativeLayout の配置, paddingHorizontal/paddingVertical, scrollbars などの属性が効かない, またはレイアウトの読み込みに失敗する問題, および UI スクリプトが分割画面で予期せず終了する場合がある問題
* `修正` セレクターの正規表現引数, contains 系セレクター, 結果数の上限が正しく動作しない問題, およびグローバルセレクターメソッドがスローした例外を try/catch で捕捉できない問題
* `修正` UiObject#child/parent などのメソッドが無限に再帰する, UiObject#clickBounds がスクリプトのイベントループを予期せず終了させる, findOnUiThread 有効時にウィジェット検索が永久に待機する場合がある問題
* `修正` アクセシビリティサービスの再起動後にセレクターのクエリが空の結果を返しイベントコールバックが動作しなくなる問題, およびサービスの状態と開始/停止がホームドロワーのスイッチと一致しない問題
* `修正` back などの操作を連続して実行するとアクセシビリティサービスが予期せず中断される場合がある問題 _[`issue #527`](http://issues.autojs6.com/527)_
* `修正` automator のグローバル操作の初回呼び出しで NullPointerException が発生する場合がある問題, およびジェスチャーの縦座標のスケーリングが正しくない問題
* `修正` 通知オブザーバーがシステム通知をトーストとして報告し, 通知イベントを取りこぼす問題
* `修正` currentPackage/currentActivity が既に閉じたウィンドウ (システム UI やランチャーなど) のパッケージ名と Activity を返す場合がある問題 (by [DeanTMaxim](https://github.com/DeanTMaxim)) _[`issue #570`](http://issues.autojs6.com/570)_ _[`pr #571`](http://pr.autojs6.com/571)_
* `修正` images の一部のメソッドが画像を早期に回収する, ネイティブメモリをリークする, 不正な引数でアプリをクラッシュさせる場合がある問題
* `修正` images.findImage/matchTemplate/matchFeatures の一致結果, 座標精度, 並び順が正しくない問題, および images.invert/clip などのメソッドの処理結果が誤っている問題
* `修正` colors.isSimilar/images.detectColor の hs アルゴリズムの結果が誤っている問題, および colors.rgb/rgba が小数やパーセントの成分を正しく処理できない問題
* `修正` 外部 URI, バックグラウンド, Tasker からスクリプトを実行すると元のパスや拡張子が失われる, または実行できない問題 _[`issue #545`](http://issues.autojs6.com/545)_ _[`issue #501`](http://issues.autojs6.com/501)_
* `修正` /sdcard のリンクがないデバイス (Fire OS など) で, インテントから /sdcard パスで起動したスクリプトが見つからない問題 (by [djbclark](https://github.com/djbclark)) _[`pr #558`](http://pr.autojs6.com/558)_
* `修正` ファイルマネージャー, 定期タスク, 外部インテントから実行した auto ファイルが JavaScript スクリプトとして実行される問題
* `修正` スクリプトプロジェクトが project.json で指定したエントリーで実行されない問題, および BOM 付きのスクリプトが実際のエンコーディングで読み込まれない問題
* `修正` 外部 URI からのインポートで, ファイル名の処理が不適切なため対象フォルダーの外に書き込む, または同名のファイルを上書きする場合がある問題
* `修正` ファイルマネージャーが読み取りの失敗を空のフォルダーとして表示する, ページに戻るとちらつく, またはスクロール位置がリセットされる, 項目に灰色の背景が残り続ける問題
* `修正` バージョン履歴やごみ箱からの復元で既存の内容が失われる場合がある, ファイルの移動後にバージョン履歴が追従しない, マルチユーザー環境でアクセスできない問題
* `修正` ページの再作成後にコードエディターがカーソル, 選択範囲, 未保存の内容を失う場合がある問題, およびソフトキーボードがコンテンツ領域を覆う問題
* `修正` 狭い画面でホームドロワーのコントロールが切れる問題, およびホーム画面を閉じたときやマルチタッチ時にアプリがクラッシュする場合がある問題
* `修正` ホームドロワーの "フォアグラウンドの時に画面を表示し続ける" の保存値が無効な場合にホーム画面がクラッシュする場合がある問題 (by [djbclark](https://github.com/djbclark)) _[`pr #557`](http://pr.autojs6.com/557)_
* `修正` 浮動ボタンメニューに表示される最近のアプリのパッケージ名と Activity 情報が正しくない問題 _[`issue #529`](http://issues.autojs6.com/529)_
* `修正` 浮動ボタンの "スクリプト実行" ダイアログを最小化すると復元できない場合がある問題 (試験的修正) _[`issue #500`](http://issues.autojs6.com/500)_
* `修正` プラグインセンターの更新時にインストール済みのプラグインが消える, 空のリストが表示される, 誤ったバリアントをインストールする (x86 デバイスで x86_64 パッケージを選ぶなど) 場合がある問題
* `修正` 権限の付与後や一時的な接続失敗後にプラグインを有効化できない問題, および Offline Docs プラグインをオフにした後に再び有効化できない問題
* `修正` AutoJs6 の更新後, 初めて Shizuku 関連の機能を使うと Shizuku サービスを使用できないと報告される問題
* `修正` AutoJs6 の強制停止後も Shizuku で起動したコマンド (音量キーの監視など) がバックグラウンドで動作し続け, 蓄積していく問題 _[`issue #474`](http://issues.autojs6.com/474)_
* `修正` Shizuku 権限のみの場合に RootAutomator を作成できない問題, およびスクリプトの記録終了後に入力イベント監視プロセスが終了しない問題
* `修正` 設定の "'音量+' キーでスクリプトの実行を制御" スイッチがアプリを再起動しないと反映されない問題
* `修正` APK のパッケージ化をキャンセルするとビルド失敗と報告される問題, および署名証明書の検証と Android 9-11 での署名の認識が正しくない問題
* `修正` ランタイムコンポーネントのないパッケージ化アプリで shell のネイティブライブラリが見つからずクラッシュする場合がある問題, およびパッケージ化した UI スクリプトが起動時にまれに空白ページを表示して終了する問題
* `修正` バックグラウンドで APK をインストールする際にインストールの確認を続行できない問題
* `修正` VSCode プラグインからプロジェクトを保存すると削除済みのファイルがデバイスに残る問題 _[`vscode-ext issue #29`](http://vscext-project.autojs6.com/issues/29)_
* `修正` VSCode プラグインでプロジェクトアーカイブ受信時にログ処理でメモリ不足になる場合がある問題 _[`issue #523`](http://issues.autojs6.com/523)_
* `修正` 無効になった VSCode プラグイン接続がログの送信をブロックし, 新しい接続がログを受信できない問題 _[`issue #385`](http://issues.autojs6.com/385)_
* `修正` 更新の確認時にデバイスの ABI の優先順位でインストールパッケージを選択しない問題
* `修正` 画面座標の換算 (cXy/cYx/setScaleBases) が正しくない問題, および自然な向きが横向きのデバイスで device.width/height が入れ替わる問題
* `修正` タイムアウトを指定しない device.keepAwake/keepScreenOn/keepScreenDim のウェイクロックがスクリプトの終了後も解放されない問題
* `修正` IntelliJ IDEA/Gradle でプロジェクトをビルドする際にモジュールの依存関係, SDK, パッケージ化テンプレートのパスが正しく解決されない問題
* `改善` 重要でない部品の初期化を遅延し, アプリ起動を高速化
* `改善` アプリ起動時の自動権限ダイアログを廃止し, 全ファイル管理権限がない場合はホームに直接許可できる通知を表示
* `改善` アプリとパッケージ化アプリを Android 17 と 16 KB メモリページのデバイスに対応
* `改善` インストールパッケージ内の依存関係, 言語リソース, 画像リソースを削減してサイズを縮小
* `改善` UI, 例外メッセージ, エンジンのエラーメッセージの多言語表示を改善
* `改善` 更新の確認で GitHub REST API を通じてバージョンとインストールパッケージの情報を取得
* `改善` アプリに簡体字中国語と英語のリリース履歴を内蔵し, その他の言語はオンラインで取得してキャッシュ
* `改善` オフラインのプロジェクト文書をプラグイン化 (Offline Docs プラグイン), 個別のインストールと更新に対応
* `改善` パッケージ化テンプレートをプラグイン化 (APK Builder Template プラグイン), パッケージ化時にテンプレートの互換性を検証
* `改善` ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization 関連機能をプラグイン化
* `改善` OpenCC/Pinyin/Pinyin4j/MediaInfo 関連機能をプラグイン化
* `改善` APK のインストールは 3-Setup Installer プラグインのインストールダイアログを使用し, プラグインを使用できない場合はシステムのインストーラーを使用, APKS/XAPK などの分割 APK コンテナのインストールにはこのプラグインが必要
* `改善` プラグインセンターのオンラインインストールと更新, バリアントの選択, 並べ替え, プラグイン設定, リリース履歴の表示を改善し, リストの読み込みと更新をよりスムーズに
* `改善` 公式プラグインはインストール後に既定で有効になり, テーマカラー, ナイトモード, 言語は AutoJs6 の設定に従う
* `改善` プラグインセンターで新しくインストールしたプラグインを一時的に上部に固定し, "新規インストール" または "有効化失敗" と表示
* `改善` ホームのプラグインタブの浮動ボタンメニューでインストールウィザードとプラグインライブラリを優先表示し, 浮動ボタンの長押しでプラグインセンターを開く
* `改善` Rhino エンジンを上流と同期し, new.target や Uint8Array の Base64/Hex 変換などの新機能に対応, スクリプトの起動効率と互換性を向上
* `改善` Rhino エンジンの分割代入, スプレッド, オプショナルチェーン, Proxy/Reflect, BigInt, ブロックレベルの関数宣言などの動作を ECMAScript 仕様に準拠
* `改善` グローバル Promise を Rhino の組み込み実装に統一し, Promise.any/withResolvers/try メソッドを追加, 未処理の拒否はコンソールにエラーとして出力
* `改善` 組み込みの com.stardust 互換クラスのコピーを削除し, com.stardust プレフィックスで参照するクラス名は AutoJs6 の同名の実装にマップ (参照: プロジェクト文書 > [AutoJs4 ランタイム](https://docs.autojs6.com/#/autojs4Runtime))
* `改善` ファイルログを組み込み実装に変更し, 文書化された設定と一般的な PatternLayout 書式を維持, その他の Log4j API は提供終了
* `改善` ocr はインストール済みのエンジンを既定で ML Kit/Paddle/Rapid の順に自動選択し, Paddle エンジンは言語別 (profile) のプラグイン選択と文字方向分類の useCls オプションに対応
* `改善` pinyin/pinyin4j/opencc モジュールがカスタム読み辞書, 複数のローマ字方式, 一括変換, チェーン変換に対応
* `改善` images.matchTemplate/matchFeatures などのメソッドがより多くの引数に対応し, 複数点の色検索, テンプレートマッチング, 特徴マッチングの速度と精度を向上
* `改善` images.captureScreen は画面が静止しているときもフレームを適時に更新し, images.requestScreenCapture は画面全体の共有のみを申請して, Android 14+ で単一のアプリを選択した際にスクリーンショットと座標が一致しない問題を回避
* `改善` runtime.loadJar が DEX Compiler プラグインによる JAR ファイルの読み込みに対応 (実験的, 既定でオフ)
* `改善` KeyCode メソッドの引数の互換性を向上し, Shizuku 権限でのキーコード送信に対応 _[`issue #492`](http://issues.autojs6.com/492)_
* `改善` shell 関数が adb オプションに対応し, Shizuku 経由でコマンドを実行 (Auto.js Pro 互換)
* `改善` アクセシビリティサービスが Shizuku による自動有効化に対応
* `改善` device.getAndroidId が useSystemId 引数に対応し, Android 8.0+ で Shizuku/Root を通じてシステムの値を取得
* `改善` console.rawInput/input メソッドを復活し, コンソールの入力バーからテキストの読み取りや式の評価が可能
* `改善` 浮動コンソールの共通操作メニュー, サイズと時刻, 着色, 文字サイズ, 透明度の設定に対応
* `改善` media.playMusic がフォアグラウンド再生, オーディオフォーカス, 通知バーでの操作に対応
* `改善` ネイティブ Android XML レイアウトで適用できない属性はスキップしてコンソールに警告を出力し, レイアウト全体の読み込み失敗を回避
* `改善` コードエディターのテーマ配色とメニューレイアウトを統一し, ファイルを開く際の表示を高速化, スクリプトの実行後は実行ボタンが停止ボタンに切り替わる
* `改善` ファイルマネージャーのファイルプレビューとプラグイン操作の入口を統一し, リストの更新時にスクロール位置を保持
* `改善` バージョン履歴の重複スナップショットを回避し, ごみ箱から新しいパスへ復元する際にバージョン履歴も移行
* `改善` レイアウト分析の階層ビューが属性の概要, 親の強調表示, 親ウィジェットの段階的な選択に対応
* `改善` ホームドロワーの "コンピューターに接続" グループを "リモート接続" に名称変更
* `改善` 浮動ボタンと関連ウィンドウがアプリの言語とナイトモードに従う
* `改善` 既定のカラーライブラリのライト用とダーク用のウィンドウ背景を "背景色" の 1 項目に統合し, 自動追従または自動反転を選択可能, アップグレード前の選択は維持
* `改善` VSCode プラグイン接続にハートビート検出とデータ長の検証を追加 _[`issue #333`](http://issues.autojs6.com/333)_ _[`issue #523`](http://issues.autojs6.com/523)_
* `改善` VSCode プラグインから保存するファイルを相対ディレクトリ構造のまま作業ディレクトリに保存可能 _[`vscode-ext issue #37`](http://vscext-project.autojs6.com/issues/37)_
* `改善` サンプルコードに 3-Block Compose, 音声, 自動化フロー, コンソール操作, 電子書籍操作の例を追加
* `改善` Gradle ビルドで Kotlin/KSP/AGP/R8 の互換バージョンを動的に照合し, プラットフォームの互換データを AutoJs6-Gradle-Platform-Versions プロジェクトに移行
* `依存関係` Android AXML をモジュール化
* `依存関係` Android ARSC Editor をモジュール化
* `依存関係` APK Signer をモジュール化
* `依存関係` APK Verifier をモジュール化
* `依存関係` ARSCLib バージョン 1.3.8 をモジュール化
* `依存関係` Image Quantization バージョン 2.17.0 をモジュール化
* `依存関係` OpenCV バージョン 4.8.0 をモジュール化
* `依存関係` juniversalchardet バージョン 2.0.4 を追加
* `依存関係` AndroidX AsyncLayoutInflater バージョン 1.1.0 を追加
* `依存関係` AndroidX Test Ext JUnit バージョン 1.3.0 を追加
* `依存関係` ZXing Android Embedded バージョン 4.3.0 を追加
* `依存関係` Android Hidden API Bypass バージョン 6.1 を追加
* `依存関係` NDK バージョン 28.2.13676358 と CMake バージョン 3.22.1 を追加
* `依存関係` Desugar JDK Libs バージョン 2.1.5 を Desugar JDK Libs NIO バージョン 2.1.5 に置き換え
* `依存関係` Android SDK バージョン 36 -> 37 を更新
* `依存関係` Gradle 9 バージョン 9.4.0 -> 9.7.1 を更新
* `依存関係` AndroidX Lifecycle バージョン 2.6.2 -> 2.9.4 を更新
* `依存関係` AndroidX SavedState バージョン 1.2.1 -> 1.3.2 を更新
* `依存関係` AndroidX Emoji2 バージョン 1.3.0 -> 1.4.0 を更新
* `依存関係` AndroidX Window バージョン 1.0.0 -> 1.5.0 を更新
* `依存関係` Rhino を 2.0.0-SNAPSHOT に更新 (上流スナップショット 2025/12/22 -> 2026/10/31)
* `依存関係` Android Annotations バージョン 4.8.0 を削除
* `依存関係` Android dx バージョン 1.14 を削除
* `依存関係` AndroidX AppCompat (Legacy) バージョン 1.0.2 を削除
* `依存関係` AndroidX MultiDex バージョン 2.0.1 を削除
* `依存関係` Apache Commons Lang3 バージョン 3.18.0 を削除
* `依存関係` APK Parser ローカルモジュール を削除
* `依存関係` Commons IO バージョン 2.8.0 を削除
* `依存関係` Flexmark Java HTML to Markdown バージョン 0.64.8 を削除
* `依存関係` GitHub API バージョン 1.306 を削除
* `依存関係` Glide KSP バージョン 5.0.5 を削除
* `依存関係` ICU4J バージョン 77.1 を削除
* `依存関係` Jackson DataBind バージョン 2.13.4.2 を削除
* `依存関係` Jaredrummler Android Device Names バージョン 2.1.1 を削除
* `依存関係` JavaMail for Android ローカルライブラリ を削除
* `依存関係` JCIP Annotations バージョン 1.0 を削除
* `依存関係` Jieba Analysis バージョン 1.0.3-SNAPSHOT (modified) を削除
* `依存関係` JUnit Jupiter バージョン 5.13.4 を削除
* `依存関係` Log4j バージョン 1.2.17 を削除
* `依存関係` Android Logging Log4j バージョン 1.0.3 を削除
* `依存関係` MLKit Barcode Scanning バージョン 17.3.0 を削除
* `依存関係` MLKit Text Recognition Chinese バージョン 16.0.1 を削除
* `依存関係` OpenCC バージョン 1.2.2 を削除
* `依存関係` Pinyin4j バージョン 2.5.1 を削除
* `依存関係` Rapid OCR ローカルライブラリ を削除
* `依存関係` Retrofit2 Converter Gson バージョン 3.0.0 を削除
* `依存関係` Retrofit2 Kotlin Coroutines Adapter バージョン 0.9.2 を削除
* `依存関係` Spongy Castle Prov バージョン 1.58.0.0 を削除
* `依存関係` Tiny Sign バージョン 0.9 を削除

# v6.7.0

###### 2026/03/14

* `新機能` プラグインセンター機能, プラグインのインストール/アンインストール/更新などの操作に対応 (入口: ホームドロワーボタン/ホームタブ)
* `新機能` 設定ページのランチャーショートカットに "プラグイン" オプションを追加, ショートカットからプラグインセンターページへ移動可能
* `新機能` バージョン履歴機能, 編集可能なファイルの履歴バージョンの表示/復元に対応 (入口: ホームドロワーボタン/ファイルマネージャーメニュー/コードエディタメニュー)
* `新機能` コードエディターに "多機能キーボード" > "記号設定" メニュー, 記号編集と構成単位の作成/インポート/エクスポートに対応
* `新機能` ゴミ箱機能, 削除済みファイル/フォルダーの表示/復元に対応 (入口: ホームドロワーボタン)
* `新機能` Paddle OCR (PP-OCRv5) プラグイン, 光学文字認識用
* `新機能` cvt モジュール, データ単位変換用 (プロジェクトドキュメント > [単位変換](https://docs.autojs6.com/#/cvt) を参照)
* `新機能` cvt.bytes メソッド, バイトデータ単位変換用 (プロジェクトドキュメント > [単位変換](https://docs.autojs6.com/#/cvt) を参照)
* `新機能` fmt モジュール, データ整形用 (プロジェクトドキュメント > [フォーマット](https://docs.autojs6.com/#/fmt) を参照)
* `新機能` fmt.bytes メソッド, バイトデータ整形用 (プロジェクトドキュメント > [フォーマット](https://docs.autojs6.com/#/fmt) を参照)
* `新機能` zip モジュール, ファイルの圧縮/展開操作用 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (プロジェクトドキュメント > [Zip](https://docs.autojs6.com/#/zip) を参照)
* `新機能` mediainfo モジュール, メディアファイルの詳細情報表示用 (プロジェクトドキュメント > [メディア情報](https://docs.autojs6.com/#/mediainfo) を参照)
* `新機能` s13n.bytes メソッド, バイトデータ標準化用 (プロジェクトドキュメント > [標準化](https://docs.autojs6.com/#/s13n) を参照)
* `新機能` app.isDualInstalled メソッド, デュアルアプリがインストールされているかの検出用 (Shizuku または Root 権限が必要) _[`issue #450`](http://issues.autojs6.com/450)_
* `新機能` device.getSharedDeviceId メソッド, アプリ間で共有される統一デバイス ID の取得用 _[`issue #455`](http://issues.autojs6.com/455)_
* `新機能` device.setPointerLocation などの Toggleable 系メソッド, ポインター位置システム設定項目の設定または取得用 _[`issue #381`](http://issues.autojs6.com/381)_
* `新機能` dialogs.build メソッドが textAllCaps/(positive/negative/neutral)TextAllCaps オプション引数に対応, ボタンテキストを全て大文字にするかどうかを制御可能
* `新機能` images.loadAsync メソッド, ネットワーク画像リソースの非同期取得用 _[`issue #327`](http://issues.autojs6.com/327)_
* `新機能` ui.getNavigationBarHeight メソッド/navigationBarHeight プロパティ (getter), ナビゲーションバーの高さ取得用 _[`issue #456`](http://issues.autojs6.com/456)_
* `新機能` ui.getVisible(Status/Navigation)BarHeight メソッド/visible(Status/Navigation)BarHeight プロパティ (getter), ステータスバー/ナビゲーションバーの可視高さを取得 _[`issue #456`](http://issues.autojs6.com/456)_
* `新機能` http.put/del(ete)/head メソッド, 異なる種類の HTTP リクエストを送信
* `新機能` http.getAsync/postAsync/requestAsync などのメソッド, ネットワークリクエストの非同期送信用 _[`issue #423`](http://issues.autojs6.com/423)_
* `新機能` http モジュールのリクエスト関連メソッドで取得される body オブジェクトに stream/saveToFile/close などのメソッドを追加 _[`issue #452`](http://issues.autojs6.com/452)_
* `新機能` http モジュールのリクエスト関連メソッドがキャッシュ制御オプション引数 (cacheBody/bodyCacheThresholdBytes) に対応
* `新機能` http モジュールのリクエスト関連メソッドが安全でないオプション引数 (isInsecure/insecure) に対応, 証明書関連例外の無視に利用可能 _[`issue #417`](http://issues.autojs6.com/417)_
* `新機能` http モジュールのリクエスト関連メソッドが options.client オプションに対応, OkHttpClient.Builder (followRedirects など) の設定に利用可能 _[`issue #454`](http://issues.autojs6.com/454)_
* `新機能` auto.state プロパティ (getter) および shizuku.state プロパティ (getter), アクセシビリティサービス状態および Shizuku サービス状態取得用
* `新機能` UiObject#isShifted メソッド, コントロール位置変化の検出用 _[`issue #469`](http://issues.autojs6.com/469)_
* `新機能` UiObject#snapshot メソッド, コントロールの現在状態の取得用 _[`issue #469`](http://issues.autojs6.com/469)_
* `新機能` 等価系セレクター (UiSelector#id/text/...) および包含系セレクター (UiSelector#xxxContains) が正規表現引数に対応
* `新機能` セレクターの正規表現引数がフラグ (i, m, s, u) に対応
* `新機能` runtime.(set/is)JavaPrimitiveWrap メソッド, Java プリミティブ型ラップ戦略の設定または取得用 _[`issue #435`](http://issues.autojs6.com/435)_
* `新機能` autojs.restart/exit メソッド, AutoJs6 の再起動/終了に使用し, 再起動後の指定スクリプト実行に対応 _[`issue #460`](http://issues.autojs6.com/460)_
* `新機能` engines.myEngine().getStartTime() メソッド, スクリプトエンジン起動タイムスタンプ取得用 _[`issue #449`](http://issues.autojs6.com/449)_
* `新機能` crash グローバルメソッド, クラッシュレポートページのテスト用
* `新機能` structuredClone グローバルメソッド, JavaScript オブジェクトのディープコピー用 ( [MDN](https://developer.mozilla.org/zh-CN/docs/Web/API/Window/structuredClone) を参照)
* `新機能` 正規表現が lookbehind 構文に対応 _[`issue #464`](http://issues.autojs6.com/464)_
* `新機能` ファイルマネージャーに "移動先" および "コピー先" メニュー項目を追加, 操作中止および進捗状態表示に対応
* `新機能` ホームドロワーに "ポインター位置" ツールを追加
* `新機能` ホームドロワーに "すべてのファイル管理権限" スイッチを追加
* `新機能` ホームドロワーの "バックグラウンドから画面を表示" スイッチ (Xiaomi/Vivo デバイス向け)
* `新機能` 設定ページに "Java プリミティブ型ラップ" 設定オプションを追加 _[`issue #435`](http://issues.autojs6.com/435)_
* `新機能` 設定ページに "定時タスクスケジューリングエンジン" 設定オプションを追加, AlarmManager/WorkManager/JobScheduler に対応 _[`issue #457`](http://issues.autojs6.com/457)_ _[`issue #434`](http://issues.autojs6.com/434)_ _[`issue #449`](http://issues.autojs6.com/449)_ _[`issue #388`](http://issues.autojs6.com/388)_ _[`issue #378`](http://issues.autojs6.com/378)_ _[`issue #163`](http://issues.autojs6.com/163)_ _[`issue #53`](http://issues.autojs6.com/53)_ _[`issue #21`](http://issues.autojs6.com/21)_
* `新機能` 設定ページに "アプリランチャーアイコン" 設定オプションを追加, 適応アイコン/透明背景アイコンに対応 _[`issue #405`](http://issues.autojs6.com/405)_
* `新機能` 設定ページの "再起動方針", ホームドロワーの再起動ボタンで高速再起動を使うか設定
* `新機能` 設定ページに "画面キャプチャ権限申請遅延" 設定オプションを追加, 権限申請ウィンドウによる画面遮蔽を回避
* `新機能` パッケージ化アプリが起動時自動実行に対応し, 設定ページにスイッチを追加
* `新機能` パッケージ化アプリがアプリショートカット (ログページ/設定ページ) に対応
* `新機能` アプリパッケージ設定ページに取り消し/やり直し/保存ボタンを追加
* `新機能` パッケージ設定ページで構成の作成/変更/削除/インポート/エクスポートに対応 _[`issue #483`](http://issues.autojs6.com/483)_
* `新機能` Scrapers ツール (run-scrapers.mjs), Gradle ビルドデータと README 共通データ/テンプレートを自動更新
* `新機能` Gradle Wrapper 切り替えツール (gradle-wrapper-switcher), ローカル Gradle メジャーバージョン (Gradle 8.x と Gradle 9.x など) の切り替え用
* `修正` notice メソッドの引数個数および型制限の誤り _[`issue #462`](http://issues.autojs6.com/462)_
* `修正` ui.R プロパティ (getter) が正常に使用できない問題
* `修正` ui.post メソッドのコールバック引数内で例外が発生した際にサイレントに失敗する可能性がある問題
* `修正` isJavaClass/isJavaPackage などのグローバルメソッドが無効な問題
* `修正` timers.keepAlive メソッドの timeout 引数機能が無効な問題
* `修正` floaty.window/rawWindow メソッドが文字列引数を受け取れない問題 _[`issue #467`](http://issues.autojs6.com/467)_
* `修正` floaty.rawWindow に誤ったコンテキストが渡されることで一部コントロール表示が異常になる問題
* `修正` util.isString メソッドが無効な問題
* `修正` util.class[Name]/getClass[Name] が誤った結果を返す可能性がある問題
* `修正` util.java.array が Boolean などの型引数を正常に使用できない問題
* `修正` threads.disposable() が返すオブジェクトでデータアクセス時に予期せずボクシングされる可能性がある問題 _[`issue #435`](http://issues.autojs6.com/435)_
* `修正` console.build メソッドを複数回呼び出した際にログフローティングウィンドウのスタイルオプションが正常にリセットされない問題
* `修正` console.build メソッドの (title/content)BackgroundColor オプションが透明度または着色オプションを上書きする問題 _[`issue #458`](http://issues.autojs6.com/458)_
* `修正` サーバーモード接続時に画面回転や言語切替などの Activity 再生成で VSCode コンソールへログを出力できなくなる問題 _[`issue #385`](http://issues.autojs6.com/385)_
* `修正` console/toast などのメソッドで Java Double を表示した際に末尾の 0 が失われる可能性がある問題
* `修正` console/toast などのメソッドで BigInt データ型を表示できない問題
* `修正` toast が Toast インスタンスへの強参照によりメモリリークを起こす場合がある問題
* `修正` 一部グローバルオブジェクトが JavaScript のプロトタイプ属性およびメソッドを失う可能性がある問題
* `修正` XML 構文で JavaScript 式を属性値として使用した際に this オブジェクトの参照先が誤る可能性がある問題
* `修正` dialogs.build メソッドの linkify オプション引数機能が無効な問題
* `修正` dialogs.build メソッドがオブジェクト引数の元データを破壊する可能性がある問題
* `修正` canvas 要素コントロールの setMaxFps メソッド内部フレームレート計算の誤り
* `修正` images.concat メソッドで縦方向連結時に幅の計算値が誤る問題
* `修正` images.requestScreenCapture メソッド呼び出し時にユーザーが認可をキャンセルするとアプリがクラッシュする可能性がある問題
* `修正` images.captureScreen メソッドで取得したスクリーンショットに向きの誤りや黒い余白が入る可能性がある問題 _[`issue #337`](http://issues.autojs6.com/337)_
* `修正` images 関連の一部メソッドで領域引数使用時に 1 が誤ってパーセントとして解釈される問題
* `修正` images 関連の一部メソッドで例外発生時に oneShot フラグ機能が無効になる問題 _[`issue #372`](http://issues.autojs6.com/372)_
* `修正` images 関連の一部メソッドでメモリリークが起きる場合がある問題 _[`issue #372`](http://issues.autojs6.com/372)_
* `修正` images.save および ImageWrapper#saveTo メソッドで指定パスが存在しない場合に正常にファイル書き込みできない問題
* `修正` ImageWrapper#recycle メソッドの画像リソース回収機能が無効な問題
* `修正` ocr の一部オーバーロードメソッドが正常に使用できない可能性がある問題
* `修正` ocr.detect メソッドで得られる結果が ocr.mode と一致しない可能性がある問題 _[`issue #468`](http://issues.autojs6.com/468)_
* `修正` 一部デバイスで ML Kit OCR を正常に初期化できない問題 (試験的修正) _[`issue #8`](http://issues.autojs6.com/8#issuecomment-3117061768)_
* `修正` auto.registerEvent で登録されたアクセシビリティサービスイベントが他のスクリプトに誤ってクリアされる問題 _[`issue #466`](http://issues.autojs6.com/466)_ _[`issue #343`](http://issues.autojs6.com/343#issuecomment-3263953918)_
* `修正` Android 10 において UiObject#child メソッドが ArrayIndexOutOfBoundsException を発生させる可能性がある問題 _[`issue #416`](http://issues.autojs6.com/416)_
* `修正` 組み込みモジュールの呼び出し時に Rhino コンテキストが不一致になる場合がある問題
* `修正` スクリプトで大量のフローティングウィンドウを作成した後, スクリプト終了時にウィンドウが残留する可能性がある問題
* `修正` プロジェクト実行時に project.json の設定引数が正常に解析されない可能性がある問題
* `修正` プロジェクトパッケージ時に project.json の excludedDirs 設定引数が設定ファイル解析失敗を引き起こす問題 _[`issue #428`](http://issues.autojs6.com/428)_
* `修正` パッケージ化アプリで Paddle OCR と Rapid OCR 機能が正常に使用できない問題
* `修正` パッケージ化アプリ設定ファイル project.json の key 値が予期せず変更される可能性がある問題
* `修正` アプリパッケージ設定ページで "必要な権限" リストをスクロールした際にチェックボックスアイコンが点滅する可能性がある問題
* `修正` Android 7.x でパッケージ機能が正常に使用できない可能性がある問題
* `修正` スクリプトプロジェクト設定ファイル保存時に元のキー名が変更される可能性がある問題
* `修正` スクリプトプロジェクト設定ファイル内のビルドバージョン番号またはビルド時刻に大きな数値が含まれるとアプリがクラッシュする可能性がある問題
* `修正` ImageReader の頻繁な取得または再構築時に, バッファに使用可能フレームが一時的に存在しないことでアプリがクラッシュする可能性がある問題
* `修正` 入力イベントオブザーバー InputEventObserver がアプリ起動時に顕著なカクつきを引き起こす可能性がある問題
* `修正` Shizuku ユーザーサービスプロセスが正常に終了せず, プロセスが蓄積する問題 _[`issue #474`](http://issues.autojs6.com/474)_
* `修正` リリース履歴ページで一部システムにおいてフォント差異により統計データが不完全に表示される問題
* `修正` 一部デバイスでファイルマネージャーの機能ボタンのクリックイベントが正常に発火しない問題 (試修) _[`issue #465`](http://issues.autojs6.com/465)_
* `修正` ファイルマネージャーの検索結果表示後に戻るボタンを押す, またはタブ切り替えを行うとアプリがクラッシュする可能性がある問題
* `修正` ファイルマネージャーでプロジェクトフォルダー削除後に UI が自動更新されない問題
* `修正` 一部デバイスでコードエディタの空行が四角記号で表示される問題 (試修)
* `修正` コードエディタでソフトキーボードがテキスト内容を遮る可能性がある問題
* `修正` コードエディタが読み取り専用モードでも内容を編集できてしまう問題
* `修正` コードエディタが読み取り専用モードでタイトル領域および一部メニュー項目をクリックするとアプリがクラッシュする問題
* `修正` コードエディタで大きなファイルを読み込むとアプリがクラッシュする可能性がある問題 (試修)
* `修正` コードエディタが新規ファイルを自動で開いた際に機能ボタン状態の初期化が異常になる問題
* `修正` コードエディタでファイル編集中にバックグラウンドへ移行するとアプリがクラッシュする可能性がある問題
* `修正` Android 15+ においてコードエディタの fx ボタンを押してもモジュール関数ショートカットパネルが表示されない問題 (試修)
* `修正` ErrorDialogActivity が正常に起動できない, または短時間で自動的に消える可能性がある問題 _[`issue #479`](http://issues.autojs6.com/479)_ _[`issue #471`](http://issues.autojs6.com/471)_ _[`issue #414`](http://issues.autojs6.com/414)_ _[`issue #340`](http://issues.autojs6.com/340#issuecomment-2973485826)_
* `修正` Canvas コンストラクターが受け入れる引数型の誤り _[`issue #402`](http://issues.autojs6.com/402)_
* `修正` クラッシュレポートページで詳細情報コピー機能が無効な問題
* `修正` Android 16+ でカスタム戻るロジックが無効になり戻る動作が異常になる問題
* `修正` タスクパネルのリスト項目が頻繁に変化するとアプリがクラッシュする可能性がある問題
* `修正` "3ボタン" ジェスチャーナビゲーション使用時にナビゲーションバー前景色がナイトモードと異常に関連付く問題
* `修正` APK ファイルタイプ情報ダイアログでアプリ名および SDK 情報を取得できない可能性がある問題
* `修正` クライアントモード接続時, 短時間で切断後に再接続するとハンドシェイクタイムアウトが発生し再接続しにくくなる問題
* `修正` クライアントモード接続時, 画面回転や言語切り替えなど Activity 再生成を伴う操作で AutoJs6 が常に接続を再構築してしまう問題
* `修正` VSCode プラグイン接続時に複数の方法で同時接続するとログ出力量が倍増する問題
* `修正` レイアウト解析ページのコード生成でコレクション系コントロールに対して生成失敗する可能性がある問題 (試修) _[`issue #328`](http://issues.autojs6.com/328)_
* `修正` 設定ページの "'音量小' キーで録画を制御" スイッチ機能が無効な問題 _[`issue #480`](http://issues.autojs6.com/480)_
* `修正` Xiaomi デバイスで "他のアプリの上に表示" スイッチが誤った設定ページへ遷移する問題
* `修正` 録画スクリプトの生成コードで座標値が実際の画面画素値と一致しない場合がある問題 _[`issue #480`](http://issues.autojs6.com/480)_
* `修正` スクリプト録画で生成されたコードファイルの最初の動作 (タップやスワイプなど) が常に無視される問題
* `修正` フローティングボタンの "スクリプト実行" ダイアログでバックグラウンドからファイル操作するとアプリがクラッシュする可能性がある問題
* `修正` ホーム Activity のライフサイクル終了後にホームへ再入するとフローティングボタン状態がリセットされる可能性がある問題
* `修正` 更新確認でダウンロードを中止した後もネットワークパケット取得がバックグラウンドで継続する問題 (試修)
* `修正` ビルドツールで isCleanup[Paddle/Rapid]Ocr オプションを有効にした際に Rebuild Project タスクが正常完了できない問題
* `改善` Paddle OCR (PP-OCRv3) 関連機能のプラグイン化 _[`issue #334`](http://issues.autojs6.com/334)_ _[`issue #331`](http://issues.autojs6.com/331)_ _[`issue #325`](http://issues.autojs6.com/325)_
* `改善` runtime.loadJar メソッドの互換性向上 (by [LYS86](https://github.com/LYS86)) _[`pr #475`](http://pr.autojs6.com/475)_
* `改善` ui.getStatusBarHeight メソッド/statusBarHeight プロパティ (getter) の互換性を一定程度向上
* `改善` console.log などのメソッドでグローバルオブジェクト (images, app, ocr など) を出力する際に詳細情報表示をサポート
* `改善` コンソールで巨大なデータを出力する際に上限に応じて一部を省略し, 出力時間を短縮
* `改善` 浮動コンソールのカウントダウン開始値を 6 秒から 9 秒へ変更
* `改善` 浮動コンソールの内部をロックレス化/キュー化し, 設定の効率と成功率を向上
* `改善` 浮動コンソールの背景色 API (透明度/着色/基色) を Android の設計規則に適合 _[`issue #458`](http://issues.autojs6.com/458)_
* `改善` dialogs.build メソッドが MaterialDialog.Builder のネイティブオプション引数に対応
* `改善` dialogs.build メソッドの animation/linkify オプション引数が文字列の緩やかな一致に対応
* `改善` device.rotation/orientation/width/height プロパティによる画面方向と幅/高さ取得の安定性と一貫性を改善
* `改善` app.launchDual/startDualActivity などのメソッドの実行効率向上および互換性強化
* `改善` android.graphics.Paint#setColor が ColorInt/ColorHex/ColorName などの色引数を正常に解析可能
* `改善` 組み込みモジュール関連メソッドの実引数型例外メッセージに型要約情報を追加
* `改善` 組み込みモジュール (dayjs/cheerio/i18n) をスクリプト実行方式からグローバル遅延読込へ移行
* `改善` ファイルマネージャーのフローティングボタン展開後にメニュー項目を押した際のメニュー格納タイミングを最適化
* `改善` ファイルマネージャー/タスクパネルがファイル/タスク数統計情報の表示に対応
* `改善` コードエディタでファイル保存失敗時に自動で下書き保存し, 新規ファイルとして別名保存に対応
* `改善` コードエディタで大容量ファイル読み込み時の滑らかさを一定程度向上
* `改善` コードエディタの "検索/置換" が状態保持および検索件数のリアルタイム表示に対応
* `改善` コードエディタで保存を促す際, 退出前に保存成功を保証することで保存失敗率を低減
* `改善` コードエディタの保存ボタン状態がユーザーの主観ロジックにより一致するよう改善
* `改善` コードエディタが "-", "=", "(", "[", "{", "<" などのキー長押し機能に対応
* `改善` アプリパッケージ処理ダイアログに "中止" ボタンを追加
* `改善` アプリパッケージ処理ダイアログの表示方式を改善し, 所要時間統計を追加
* `改善` 新しい設定でパッケージ化アプリをインストールする際に旧アプリ関連設定を上書きするよう改善
* `改善` アプリパッケージ設定ページで必要権限 (WAKE_LOCK/INTERNET/WRITE_EXTERNAL_STORAGE/...) を既定で選択 _[`issue #397`](http://issues.autojs6.com/397)_
* `改善` アプリパッケージ設定ページでより多くのオプションの読み取りおよび設定に対応 (起動時にログ表示/起動時にスプラッシュ表示/スプラッシュ標語テキスト/...)
* `改善` アプリパッケージ設定ページにフォアグラウンドサービススイッチを追加 _[`issue #406`](http://issues.autojs6.com/406)_
* `改善` スクリプトプロジェクト設定ファイル保存時にキー名衝突検出機構を追加し, キー名の曖昧性を防止
* `改善` クラッシュレポートページが 2 本指ズームによる文字サイズ調整および主要機能ボタン追加に対応
* `改善` アプリランチャーアイコンが適応アイコン特性に対応 _[`issue #405`](http://issues.autojs6.com/405)_
* `改善` Rhino エンジンでジェネリックシグネチャ解析失敗時に生のリフレクション型へフォールバックし, 低バージョン Android システムでのリフレクションメソッド利用性を強化
* `改善` ホームドロワーのスイッチ類項目に区切り線を追加, タイトル文字領域クリックで詳細ダイアログ表示に対応し, 必要に応じてシステム設定へのクイックジャンプをサポート
* `改善` AutoJs6 起動または再起動時に, ホームドロワーの "クライアントモード" タイトル文字領域を押して接続試行中の処理を中止可能
* `改善` クライアントモード接続が IPv6 アドレスおよびドメイン名アドレスでの接続に対応
* `改善` クライアントモード接続が特殊用途 IPv4 アドレス (loopback/broadcast/multicast/reserved/...) の検出案内に対応
* `改善` クライアントモード接続が接続状態の表示および管理 (アドレス修正/接続中止) に対応
* `改善` サーバーモード接続が接続済みクライアント数表示に対応
* `改善` 通知権限読み取りが自動再接続機構 ([requestRebind](https://developer.android.com/reference/android/service/notification/NotificationListenerService#requestRebind(android.content.ComponentName))) に対応し, 通知監視関連機能の安定性を向上
* `改善` フローティングボタンがバックグラウンドからの Activity 起動安全性を強化し, アプリクラッシュを回避
* `改善` フローティングボタンの "その他" ダイアログが非同期データ読み込み方式を使用し, 表示の滑らかさを向上
* `改善` フローティングボタンの "スクリプト実行" ダイアログに "ホーム" メニュー項目を追加
* `改善` フローティングボタンの "スクリプト実行" ダイアログが最小化および状態復元に対応し, 可能な限りウィンドウ常駐または自動復元を維持
* `改善` Shizuku 権限を利用したスクリプト録画および録画再生に対応 (再生の滑らかさは一部影響を受ける可能性あり) _[`issue #320`](http://issues.autojs6.com/320)_
* `改善` テーマカラー設定ページでテーマカラー位置特定に高速位置決め方式を使用し, 効率を向上
* `改善` 廃止済みの [LocalBroadcastManager](https://developer.android.com/jetpack/androidx/releases/localbroadcastmanager) を [LiveData](https://developer.android.com/topic/libraries/architecture/livedata) および [SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow) へ置き換え
* `改善` Gradle ビルドスクリプトで 7z 形式ファイルの展開効率を向上
* `改善` Gradle ビルドスクリプトが詳細な Android Studio IDE バージョン (例: "2025.1.4.7") の取得に対応
* `改善` Gradle ビルドスクリプトが VersionCodesList クラスに必要なデータの自動生成に対応し, スクリプト起動遅延を低減
* `改善` [Version Catalogs](https://developer.android.com/build/migrate-to-catalogs) を使用して Gradle 依存関係およびプラグインバージョンを集中管理
* `改善` Gradle ビルドスクリプトをモジュール化し, 共有ビルドロジックを build-logic へ移行して convention plugin として抽象化
* `改善` Gradle convention plugin を使用してローカル AAR ライブラリ読み込みロジックを簡素化
* `改善` Gradle 9 への適応のため "android.enableJetifier=true" Gradle 設定オプションを削除
* `改善` ビルド環境差異低減のため sourceCompatibility/targetCompatibility の代わりに [Toolchain](https://docs.gradle.org/current/userguide/toolchains.html) を使用
* `依存関係` モジュール化 Expandable Layout バージョン 1.6.0
* `依存関係` モジュール化 Expandable RecyclerView バージョン 3.0.0-RC1
* `依存関係` RecyclerView Flexible Divider バージョン 1.4.0 をモジュール化
* `依存関係` ローカライズ Root Shell バージョン 1.6
* `依存関係` AndroidX Activity (KTX) バージョン 1.12.2 を追加
* `依存関係` AndroidX Core (KTX) バージョン 1.15.0 を追加
* `依存関係` AndroidX Work Runtime バージョン 2.11.0 を追加
* `依存関係` AndroidX Interpolator バージョン 1.0.0 を追加
* `依存関係` Room RxJava2 バージョン 2.8.1 を追加
* `依存関係` Android SDK バージョン 35 -> 36 に更新
* `依存関係` Gradle 8 バージョン 8.14 -> 8.14.4 に更新
* `依存関係` Gradle 9 バージョン 8.14 -> 9.4.0 に更新
* `依存関係` Rhino バージョン 1.8.1-SNAPSHOT -> 2.0.0-SNAPSHOT に更新
* `依存関係` Desugar JDK Libs バージョン 2.0.4 -> 2.1.5 に更新
* `依存関係` Android Material バージョン 1.12.0 -> 1.13.0 に更新
* `依存関係` AndroidX AppCompat バージョン 1.7.0 -> 1.7.1 に更新
* `依存関係` AndroidX Room バージョン 2.7.1 -> 2.7.2 に更新
* `依存関係` AndroidX WebKit バージョン 1.13.0 -> 1.14.0 に更新
* `依存関係` Material Progressbar バージョン 1.4.2 -> 1.6.1 に更新
* `依存関係` Apache Commons Lang3 バージョン 3.16.0 -> 3.18.0 を更新
* `依存関係` Retrofit2 Retrofit バージョン 2.11.0 -> 2.12.0 に更新
* `依存関係` Retrofit2 Converter Gson バージョン 2.11.0 -> 2.12.0 に更新
* `依存関係` Retrofit2 RxJava2 バージョン 2.11.0 -> 2.12.0 に更新
* `依存関係` Joda Time バージョン 2.12.7 -> 2.14.0 に更新
* `依存関係` Gson バージョン 2.11.0 -> 2.13.1 に更新
* `依存関係` ARSCLib バージョン 1.3.1 -> 1.3.5 に更新
* `依存関係` Jsoup バージョン 1.19.1 -> 1.20.1 に更新
* `依存関係` JUnit Jupiter バージョン 5.10.3 -> 5.13.0 を更新
* `依存関係` OpenCC バージョン 1.2.0 -> 1.2.2 に更新
* `依存関係` Android Support Annotation v7 バージョン 28.0.0 を削除
* `依存関係` Android Support AppCompat v7 バージョン 28.0.0 を削除
* `依存関係` Android Support RecyclerView v7 バージョン 28.0.0 を削除

# v6.6.4

###### 2025/05/31

* `ヒント` API変更: ui.(status/navigation)BarAppearanceLight[By] -> ui.(status/navigation)BarIconLight[By]
* `新機能` util.dpToPx/spToPx/pxToDp/pxToSp メソッド (ピクセル単位変換用)
* `修正` 画面を横向きに回転するとサブタイトルが不完全に表示される問題
* `修正` 画面を横向きに回転すると一部ページのコンテンツがサイドナビゲーションバーに隠れる問題
* `修正` Android 15 で一部ページのステータスバー背景の着色領域が不完全になる問題 _[`issue #398`](http://issues.autojs6.com/398)_
* `修正` コードエディタが信頼性の低いエンコーディングでファイルを書き込み, デコードエラーを引き起こす可能性がある問題 (修正試行)
* `改善` アプリと開発者についてのページのレイアウト適応性を改善し, 不要なレイアウト分類を削除
* `改善` README.md のビルド節に設定ページへの移動方法を追加 _[`issue #404`](http://issues.autojs6.com/404)_
* `依存関係` AndroidX ConstraintLayout バージョン 2.2.1 を追加

# v6.6.3

###### 2025/05/27

* `新機能` リリース履歴機能, 多言語の履歴と統計を表示
* `新機能` timers.keepAlive メソッド (グローバルへ昇格) によりスクリプトの活動を維持
* `新機能` engines.on('start/stop/error', callback) などのイベントリスナーでエンジンのグローバルイベントを受信
* `新機能` images.detectMultiColors メソッドで複数点のカラーを検査 _[`issue #374`](http://issues.autojs6.com/374)_
* `新機能` images.matchFeatures/detectAndComputeFeatures メソッド: フル解像度画像検索 (参照 [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `新機能` images.compressToBytes メソッドで画像を圧縮してバイト配列を取得
* `新機能` images.downsample メソッド, 画像をダウンサンプリングして新しい ImageWrapper を返す
* `新機能` ui.keepScreenOn メソッドで UI ページがフォーカス中は画面を点灯したまま維持
* `新機能` ui.root プロパティ (getter) で UI レイアウト内の [ウィンドウコンテンツのルートコンテナ] ノードを取得
* `新機能` webview 要素で JsBridge ベースの Web レイアウトに対応 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (参照: サンプルコード > レイアウト > 対話型 HTML/Vue2 + Vant (SFC)) _[`issue #281`](http://issues.autojs6.com/281)_
* `新機能` レイアウトインスペクターが要素の非表示をサポート (寄稿 [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `修正` Docs タブおよび Docs アクティビティのオンラインドキュメントがシステムナビゲーションバーと重なる場合がある問題を修正
* `修正` 一部ページで Toolbar ボタンをタップすると誤ってタイトルのクリックイベントが発火する問題を修正
* `修正` 一部端末でコードエディタの空行が□表示になる問題 (暫定修正)
* `修正` テーマカラー設定のカラーピッカーダイアログが無限に重なる問題を修正
* `修正` Android 15 の一部ページでステータスバー背景色がテーマカラーと同期しない問題を修正
* `修正` アクセシビリティサービスが無効のときに音量アップキーが全スクリプトを停止しない問題を修正
* `修正` スケジュールタスクでユーザー定義ブロードキャストメッセージを編集する際に IME が入力エリアを覆う問題を修正
* `修正` webview 内のコントロールがソフトキーボードを正常に呼び出せない問題を修正
* `修正` APK 情報ダイアログがアプリ名や SDK 情報を取得できない場合がある問題を修正
* `修正` ファイルマネージャーのサンプルでプロジェクトディレクトリに入る際にサブフォルダーの内容が読み込まれない問題を修正
* `修正` Android 15 の UI モードでステータスバーが上部コンテンツを覆う問題を修正
* `修正` dialogs モジュールが customView プロパティをサポートしていない問題を修正 _[`issue #364`](http://issues.autojs6.com/364)_
* `修正` dialogs.input の expression パラメータが実行結果を返さない問題を修正
* `修正` JavaAdapter 使用時に ClassLoader スタックオーバーフローが発生する問題を修正 _[`issue #376`](http://issues.autojs6.com/376)_
* `修正` console.setContentTextColor がデフォルト文字色をリセットしてしまう問題を修正 _[`issue #346`](http://issues.autojs6.com/346)_
* `修正` console.setContentBackgroundColor がカラー名を受け付けない問題を修正 _[`issue #384`](http://issues.autojs6.com/384)_
* `修正` images.compress 実装を修正: ダウンサンプリングではなくエンコード品質を変更
* `修正` images.resize メソッドが正しく動作しない問題を修正
* `修正` engines.all が ConcurrentModificationException を投げる場合がある問題を修正 _[`issue #394`](http://issues.autojs6.com/394)_
* `修正` README.md の一部言語で日付フォーマットが誤っていた問題を修正
* `修正` Gradle ビルドがライブラリアーカイブのサイズ不正で失敗する問題を修正 _[`issue #389`](http://issues.autojs6.com/389)_
* `改善` レイアウトインスペクターのメニューに機能グループ化用のグラデーション区切りを追加
* `改善` project.json がスクリプトプロジェクト用 permissions オプションをサポート (寄稿 [wirsnow](https://github.com/wirsnow)) _[`pr #391`](http://pr.autojs6.com/391)_ _[`issue #362`](http://issues.autojs6.com/362)_
* `改善` 単一ファイルをパッケージングする際, インストール済みアプリの宣言パーミッションを自動読み取り/選択 _[`issue #362`](http://issues.autojs6.com/362)_
* `改善` テーマカラーの適用範囲を拡大, より多くのウィジェットをサポート
* `改善` メインページのドロワー幅がランドスケープおよび超ワイド画面に適応
* `改善` "アプリについて"と"開発者について"ページに横向きとコンパクトレイアウトを追加
* `改善` 設定ダイアログに [デフォルト値を使用] メニューを追加
* `改善` ファイルマネージャーの FAB が外部タップ時に自動で隠れるように
* `改善` 非同期ロードでファイルマネージャーリストのスクロールがよりスムーズに
* `改善` コードフォーマッターが `??`, `?.`, `??=` 演算子をサポート
* `改善` コードエディターで GB18030, UTF-16 (LE/BE), Shift_JIS などの文字コードによる読書きに対応
* `改善` コードエディタがファイルの詳細情報 (パス/エンコーディング/改行コード/総バイト数/文字数 など) の表示に対応 _[`issue #395`](http://issues.autojs6.com/395)_
* `改善` Intent 操作 (編集/閲覧/インストール/送信/再生など) にエラーメッセージを追加
* `改善` webview 要素の url 属性が相対パスをサポート
* `改善` ImageWrapper#saveTo の path パラメータが相対パスをサポート
* `改善` images.save が PNG 圧縮を quality パラメータでサポート _[`issue #367`](http://issues.autojs6.com/367)_
* `改善` 無視した更新リストとクライアントモードアドレスをクリア可能に
* `改善` バージョンアップデート情報を UI 言語に合わせて表示
* `改善` コンソールでのスクリプト例外メッセージの内容とフォーマットを改善
* `改善` サンプルコードをフォルダーの初期内容にリセット可能
* `改善` APK 署名検証を高速化
* `改善` APK/メディアファイル情報ダイアログを速度と表示ロジック面で最適化
* `改善` Gradle ビルドスクリプトが新しいバージョンに柔軟に対応 _[`discussion #369`](http://discussions.autojs6.com/369)_
* `依存関係` 同梱 Material Dialogs バージョン 0.9.6.0 (モジュール化)
* `依存関係` 同梱 Material Date Time Picker バージョン 4.2.3 (モジュール化)
* `依存関係` 同梱 libimagequant バージョン 2.17.0 (ローカライズ済み)
* `依存関係` 同梱 libpng バージョン 1.6.49 (ローカライズ済み)
* `依存関係` 追加 ICU4J バージョン 77.1
* `依存関係` 追加 Jsoup バージョン 1.19.1
* `依存関係` 追加 Material Progressbar バージョン 1.4.2
* `依存関係` 追加 Flexmark Java HTML to Markdown バージョン 0.64.8
* `依存関係` 更新 Gradle 8.14-rc-1 -> 8.14
* `依存関係` 更新 AndroidX Room 2.7.0 -> 2.7.1

# v6.6.2

###### 2025/04/16

* `新機能` ui.statusBarAppearanceLight/statusBarAppearanceLightBy/navigationBarColor などのメソッド, ステータスバー/ナビゲーションバーのスタイルを設定
* `新機能` ui.statusBarHeight 属性 (getter) により, ステータスバーの高さを取得可能 _[`issue #357`](http://issues.autojs6.com/357)_
* `新機能` images.flip メソッドで画像を反転 _[`issue #349`](http://issues.autojs6.com/349)_
* `新機能` 設定ページに"ファイル拡張子"オプションを追加
* `新機能` テーマ色設定でグループ分け, 位置特定, 検索, 履歴とカラーピッカー拡張に対応
* `修正` Android 15 において, ステータスバーの背景色がテーマカラーと一致しない問題を修正
* `修正` plugins.load メソッドでプラグインが正しくロードされない問題を修正 _[`issue #290`](http://issues.autojs6.com/290)_
* `修正` Android 7.x で dx ライブラリが正しく動作しない問題を修正 _[`issue #293`](http://issues.autojs6.com/293)_
* `修正` require を使用して組み込みモジュールをインポートする際, ScriptRuntime の同期状態に問題が生じる (暫定対応) _[`issue #298`](http://issues.autojs6.com/298)_
* `修正` notice モジュールに getBuilder などの拡張メソッドが欠如している問題を修正 _[`issue #301`](http://issues.autojs6.com/301)_
* `修正` shizuku/shell メソッドが文字列パラメータを受け付けない問題を修正 _[`issue #310`](http://issues.autojs6.com/310)_
* `修正` colors.pixel メソッドがシングルチャンネル画像のパラメータを受け付けない問題を修正 _[`issue #350`](http://issues.autojs6.com/350)_
* `修正` engines.execScript / execScriptFile メソッドが, スクリプト実行時にワーキングディレクトリを誤って設定する問題を修正 _[`issue #358`](http://issues.autojs6.com/358)_ _[`issue #340`](http://issues.autojs6.com/340)_ _[`issue #339`](http://issues.autojs6.com/339)_
* `修正` floaty.window / floaty.rawWindow がサブスレッドで実行できない問題を修正
* `修正` floaty.getClip がクリップボードを正常に取得できない場合がある問題 _[`issue #341`](http://issues.autojs6.com/341)_
* `修正` ui.inflate の返り値から attr, on, click などのプロトタイプメソッドが欠落する問題を修正
* `修正` XML 構文を用いて JavaScript の式を属性値として使用する際, スコープのコンテキストが不正にバインドされる問題 _[`issue #319`](http://issues.autojs6.com/319)_
* `修正` 一部メソッドの例外を try/catch ブロックで捕捉できない問題 _[`issue #345`](http://issues.autojs6.com/345)_
* `修正` レイアウト解析ページでコード生成時にアプリがクラッシュする可能性がある問題を修正 _[`issue #288`](http://issues.autojs6.com/288)_
* `修正` パッケージ化されたアプリが shizuku モジュールを正しく利用できない問題を修正 _[`issue #227`](http://issues.autojs6.com/227)_ _[`issue #231`](http://issues.autojs6.com/231)_ _[`issue #284`](http://issues.autojs6.com/284)_ _[`issue #287`](http://issues.autojs6.com/287)_ _[`issue #304`](http://issues.autojs6.com/304)_
* `修正` コードエディタで行末へ移動すると, カーソルが次の行の先頭に位置してしまう問題を修正
* `修正` 設定ページのダイアログ要素を連続して高速にタップすると, アプリがクラッシュする問題を修正
* `改善` パッケージアプリのテンプレートにおける APK ファイルサイズの最適化
* `改善` アプリ (およびパッケージアプリ) がより多くの権限に対応 _[`issue #338`](http://issues.autojs6.com/338)_
* `改善` アプリ打包設定ページで Pinyin ライブラリのオプションに対応
* `改善` パッケージアプリのメインページにおいて, ステータスバーの背景とテキスト色を最適化
* `改善` パッケージアプリの設定ページに, 特別な権限 (全ファイルアクセスや通知送信) 用のトグルスイッチを追加 _[`issue #354`](http://issues.autojs6.com/354)_
* `改善` コントロールのテキストとアイコンが, テーマの明るさに合わせて自動で調整されるよう改善
* `改善` コントロールの色と背景とのコントラストが低い場合の視認性を向上
* `改善` カラーピッカー内の HEX 入力欄に, クリップボードからの貼り付け時の互換性を向上
* `改善` アプリのナビゲーションバーを, より良いビジュアル体験のために透明または半透明に設定
* `改善` ライトモード時, ステータスバーとナビゲーションバーのデフォルト UI モードをカラー `md_grey_50` に設定
* `改善` ホームドロワー内のアクセシビリティサービスのスイッチを, スクリプトコードと同期するよう改善
* `改善` ホームページのドキュメントページに双方向検索ボタンを追加
* `改善` ホームページの"ファイル"タブで, 長押しによりフローティングボタンの表示状態を切り替え可能に
* `改善` コードエディタのタイトルがフォントサイズを自動調整するよう改善
* `改善` ログページのフローティングボタンの表示が, リストのスクロール動作と連動するよう改善
* `改善` スクリプトプロジェクトの project.json 設定ファイルが, より多くのパッケージオプションに対応 _[`issue #305`](http://issues.autojs6.com/305)_ _[`issue #306`](http://issues.autojs6.com/306)_
* `改善` project.json 設定ファイルが, オプション名の柔軟なマッチングとエイリアスに対応するよう改善
* `改善` APK ファイル情報ダイアログに, ファイルサイズと署名スキームの情報を追加
* `改善` APK ファイル情報ダイアログで, テキストコピーやアプリ詳細への遷移用クリックリスナーに対応
* `改善` com.stardust 接頭辞のパッケージを復元し, コード互換性を向上 _[`issue #290`](http://issues.autojs6.com/290)_
* `改善` floaty.window / floaty.rawWindow メソッドを, メインスレッドおよびサブスレッドの両方で実行可能に改善
* `改善` グローバルメソッド getClip が, 必要に応じて floaty.getClip を利用するよう改善し, 互換性を向上
* `改善` files.path および関連メソッドが, null のパス指定時にも適切に動作するよう改善
* `改善` 最新の公式 Rhino エンジンへの同期およびコードの調整
* `改善` README.md を改善し, プロジェクトのビルドおよび実行方法のドキュメントを充実 _[`issue #344`](http://issues.autojs6.com/344)_
* `依存関係` Eclipse Paho Client Mqtt V3 バージョン 1.1.0 を追加 _[`issue #330`](http://issues.autojs6.com/330)_
* `依存関係` Android SDK のバージョンを 34 から 35 に更新
* `依存関係` Gradle を 8.12 から 8.14-rc-1 に更新
* `依存関係` Rhino を 1.8.0-SNAPSHOT から 1.8.1-SNAPSHOT に更新
* `依存関係` AndroidX RecyclerView バージョン 1.3.2 -> 1.4.0 を更新
* `依存関係` AndroidX Room を 2.6.1 から 2.7.0 に更新
* `依存関係` AndroidX WebKit を 1.12.1 から 1.13.0 に更新
* `依存関係` Pinyin4j を 2.5.0 から 2.5.1 に更新

# v6.6.1

###### 2025/01/01

* `新機能` 中国語のピンイン変換用の Pinyin モジュール (プロジェクトドキュメントを参照 > [中国語ピンイン](https://docs.autojs6.com/#/pinyin))
* `新機能` 中国語のピンイン変換用の Pinyin4j モジュール (プロジェクトドキュメントを参照 > [中国語ピンイン](https://docs.autojs6.com/#/pinyin4j))
* `新機能` UiObject#isSimilar と UiObjectCollection#isSimilar メソッド, 要素/集合の類似性を判定
* `新機能` currentComponent グローバルメソッド, 現在の Activity のコンポーネント名を取得
* `修正` 一部の環境でバージョンを低く戻すことでプロジェクトが正常にコンパイルできなくなる問題
* `修正` 存在しないメソッドを呼び出す際に発生する可能性がある [非プリミティブ型の値] エラー
* `修正` 一部のデバイスでスクリプトショートカットが正常に追加できない問題 (暫定修正) _[`issue #221`](http://issues.autojs6.com/221)_
* `修正` automator.click/longClick メソッドのパラメーター型制限の誤り _[`issue #275`](http://issues.autojs6.com/275)_
* `修正` セレクターが ConsString 型のパラメーターをサポートしない問題 _[`issue #277`](http://issues.autojs6.com/277)_
* `修正` UiObjectCollection インスタンスにメソッドおよびプロパティが欠如している問題
* `改善` アプリ打包設定ページで署名設定, keystore 管理, 権限設定に対応 (by [luckyloogn](https://github.com/luckyloogn)) _[`pr #286`](http://pr.autojs6.com/286)_
* `改善` 単一ファイルをパッケージ化する際に, 自動生成されるパッケージ名のサフィックスを簡体字中国語からピンインに変換 (多音字対応)
* `改善` フローティングウィンドウの現在のパッケージ名および現在のアクティビティ名の認識精度を向上 (優先順位: Shizuku > Root > A11Y)
* `改善` currentPackage と currentActivity の認識精度を向上 (優先順位: Shizuku > Root > A11Y)
* `改善` ログウィンドウの各項目をダブルクリック/長押しでテキスト選択する機能を復元 _[`issue #280`](http://issues.autojs6.com/280)_
* `改善` project.json ファイルが破損した場合でも, スクリプトプロジェクトの重要な情報を可能な限り復元
* `改善` UiSelector#findOnce と UiSelector#find メソッドで負の引数をサポート
* `改善` UI 要素や className に関連するセレクターが RecyclerView や Snackbar などのパッケージ名プレフィックス省略形式にさらに対応
* `改善` app.startActivity/startDualActivity メソッドの適応性が向上しました
* `改善` Rhino エンジンの最新の上流コードを同期し, 既存のプロジェクトに適応させる
* `依存関係` Pinyin4j バージョン 2.5.0 を追加
* `依存関係` Jieba Analysis バージョン 1.0.3-SNAPSHOT (修正版) を追加
* `依存関係` Gradle バージョンを 8.11.1 から 8.12 にアップグレード

# v6.6.0

###### 2024/12/02 - 組み込みモジュールの書き換え, 慎重にアップグレードしてください

* `ヒント` 既存のモジュールは, スクリプトの実行効率を高めるために Kotlin によって再作成されましたが, 改良にはいくつかの反復が必要です
* `ヒント` 組み込みの init.js ファイルはデフォルトでは空ですが, 開発者がカスタマイズするためのモジュールをマウントできます
* `新機能` axios/cheerio モジュール, ネットワークリクエストと HTML 解析に使用 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX))
* `新機能` sqlite モジュール, SQLite データベース操作に使用 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (参照: プロジェクト文書 > [SQLite](https://docs.autojs6.com/#/sqlite))
* `新機能` mime モジュール, MIME タイプ文字列を処理および解析するために使用されます (プロジェクトドキュメント > [MIME](https://docs.autojs6.com/#/mime) を参照)
* `新機能` nanoid モジュール, 文字列 ID を生成 (Ref to [ai/nanoid](https://github.com/ai/nanoid))
* `新機能` sysprops モジュール, ランタイム環境の構成データを取得するために使用されます (プロジェクトドキュメント > [システムプロパティ](https://docs.autojs6.com/#/sysprops) を参照)
* `新機能` ocr モジュールは [Rapid OCR](https://github.com/RapidAI/RapidOCR) エンジンをサポートしています
* `新機能` レイアウト解析はウィンドウの切り替えをサポートしています (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新機能` auto.clearCache メソッド, 要素キャッシュを削除 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新機能` threads.pool メソッド, スレッドプールを作成して使用 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新機能` images.matchTemplate の useTransparentMask オプション, 透明画像の検索に使用 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新機能` images.requestScreenCaptureAsync メソッド, UI モードで非同期に画面キャプチャ権限を申請 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新機能` images.requestScreenCapture の isAsync オプション, 非同期画面キャプチャに使用 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新機能` images.on('screen_capture', callback) などの監視メソッド, キャプチャ利用可能イベントを監視 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新機能` images.stopScreenCapture メソッド, キャプチャ申請関連のリソースを能動的に解放 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新機能` images.psnr/mse/ssim/mssim/hist/ncc および images.getSimilarity メソッドは, 画像類似度測定値を取得するために使用されます
* `新機能` images.isGrayscale メソッドは, 画像がグレースケールかどうかを判断するために使用されます
* `新機能` images.invert メソッド, 画像の色を反転
* `新機能` s13n.point/time メソッドは, ポイントオブジェクトと時間オブジェクトを正規化するために使用されます (プロジェクトドキュメント > [正規化](https://docs.autojs6.com/#/s13n) を参照)
* `新機能` console モジュールの gravity (重力), touchThrough (タッチ透過), backgroundTint (背景着色) などのメソッド (参照: プロジェクト文書 > [コンソール](https://docs.autojs6.com/#/console))
* `新機能` Mathx.randomInt/randomFloat メソッド, 指定範囲のランダムな整数/浮動小数点数を取得
* `新機能` app.launchDual/startDualActivity などのメソッドは二重起動アプリケーションの処理に使用されます (Shizuku または Root 権限が必要) (実験的)
* `新機能` app.kill メソッド, アプリケーションを強制終了するために使用されます (Shizuku または Root 権限が必要)
* `新機能` floaty.getClip メソッド, 浮動ウィンドウのフォーカスを利用してクリップボードを取得
* `修正` Fragment のサブクラス (例 [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48) 等) 内の View Binding のメモリリーク
* `修正` [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21) 等のクラス内のインスタンスのメモリリーク
* `修正` Android 14 でスクリーンキャプチャの許可を求めるとアプリがクラッシュする問題 (by [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `修正` Android 14 でフォアグラウンドサービスを開始するとアプリがクラッシュする問題
* `修正` Android 14 でコードエディタの実行ボタンをクリックしても点灯しない問題
* `修正` コードエディタのデバッグ機能で FAILED ASSERTION 例外が発生する
* `修正` コードエディタでデバッグ中にエディタを閉じた後に再度正常にデバッグできない問題
* `修正` コードエディタで行の端へ移動すると末尾の文字が漏れ可能性がある問題
* `修正` プロジェクトのパッケージ後に必要なライブラリファイルが不足してアプリが正常に動作しない可能性がある問題 _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `修正` アプリパッケージが opencc モジュールを正常に使用できない問題
* `修正` プロジェクトの編集時に指定されたアイコンリソースが存在しない場合にアプリがクラッシュする問題 _[`issue #203`](http://issues.autojs6.com/203)_
* `修正` スクリーンキャプチャの許可を取得時に指定された画面方向のスクリーンショットリソースをパラメータで正常に取得できない場合がある問題
* `修正` 一部デバイスでスクリプトショートカットを正常に追加できない問題 (試み修正) _[`issue #221`](http://issues.autojs6.com/221)_
* `修正` http モジュールと関連する方法を呼び出すと蓄積される要求による送信遅延の問題 _[`issue #192`](http://issues.autojs6.com/192)_
* `修正` AutoJs6 がメインアクティビティページに入る前に Shizuku サービスが正常に利用できない可能性がある問題 (試み修正) _[`issue #255`](http://issues.autojs6.com/255)_
* `修正` random(min, max) メソッドが結果がオーバーフローする問題の可能性
* `修正` pickup メソッドの結果型パラメータが空配列を正しく渡せない問題
* `修正` UiObject#bounds() で取得したコントロール矩形が意図と異なる形で変更される可能性がある問題
* `修正` サービスの異常の問題が発生する可能性のあるアクセシビリティサービスの自動開始 (試み修正)
* `修正` text/button/input 要素のテキスト内容にハーフサイズの二重引用符が含まれている場合に正しく解析されない問題
* `修正` text/textswitcher 要素の autoLink 属性が機能しない問題
* `修正` 異なるスクリプトが同じ ScriptRuntime オブジェクトを誤って共有する可能性がある問題
* `修正` グローバル変数 HEIGHT/WIDTH が getter の動的属性を失う問題
* `修正` スクリプトの起動時に RootShell が即時ロードされることで起動が遅くなる問題
* `修正` 浮動コンソールの背景色設定で矩形の角丸スタイルが失われる問題
* `修正` メイン画面からログページを開くとちらつく場合がある問題
* `修正` ホームページのドキュメントページで左右に滑動すると WebView コントロールが ViewPager の切り替えを誘発する可能性がある問題
* `修正` ファイル拡張子が大文字を含む場合ファイルマネージャが認識できない問題
* `修正` ファイルマネージャがプロジェクトディレクトリに最初に入った時点でプロジェクトを自動的に認識できない可能性がある問題
* `修正` ファイルマネージャでフォルダを削除した後にページが自動更新されない問題
* `修正` ファイルマネージャでファイルおよびフォルダを並べ替える際に ASCII の先頭文字名を後置する可能性がある問題
* `改善` アプリ打包設定ページの "利用不可 ABI" コントロールのタップ時ヒント体験を改善
* `改善` 単一ファイルのパッケージ作成時に適切な正規化パッケージ名を自動入力し, 無効文字の除去を案内
* `改善` 単一ファイルパッケージング時に同じパッケージ名のアプリがインストールされている場合にアイコンを自動設定し, バージョン番号とバージョン名を増加させるサポート
* `改善` パックプロジェクト設定ファイルが abis/libs オプションをサポートし, デフォルトで含まれる ABI アーキテクチャと拡張ライブラリを指定
* `改善` パックプロジェクト設定ファイルの abis/libs オプションが無効または使用可能でない場合に関連メッセージをサポート
* `改善` Shizuku を使用して [ポインタ位置] 表示スイッチをコントロールするサポート
* `改善` Shizuku を使用して [メディアの投影] および [セキュリティ設定の変更] 許可スイッチをコントロールするサポート
* `改善` automator.gestureAsync/gesturesAsync がコールバック関数パラメータをサポート
* `改善` tasks モジュールが同期方式でデータベース操作を実行するサポート, データアクセスの不一致問題を回避
* `改善` スクリプト実行モードがパイプ記号区切りモードパラメータ (例 `"ui|auto";` のように開始) をサポート
* `改善` スクリプト実行モードがシングルクォーテーションおよびバックティックをサポートし, セミコロンを省略 (例 `'ui';` または `'ui'` のように開始) 可能
* `改善` スクリプト実行モードが axios, cheerio, dayjs などのモードパラメータで内蔵拡張モジュールのクイックインポートをサポート (例 `"axios";` のように開始)
* `改善` スクリプト実行モードが x または jsox モードパラメータで JavaScript 内蔵オブジェクト拡張モジュールをクイック有効化をサポート (例 `"x";` のように開始)
* `改善` img の src/path 属性でローカル相対パスに対応 (例: `<img src="a.png" />`)
* `改善` コードエディタで Java クラスやパッケージ名をインポートする位置のインテリジェント判断をサポート
* `改善` images モジュールが画像パラメータとしてパスの直接使用をサポート
* `改善` importPackage が文字列パラメータをサポート
* `改善` クライアントモードの IP アドレスでクリップボードインポート時の自動認識とスペースキー入力の自動変換に対応
* `改善` ファイルマネージャが新しいファイルを作成する際にデフォルトのプレフィックス選択と適切な数字サフィックスの自動生成をサポート
* `改善` ファイル管理プロジェクトの実行時の例外メッセージを具体化 _[`issue #268`](http://issues.autojs6.com/268)_
* `改善` ファイルマネージャがより多くの種類をサポートし, 対応するアイコンシンボルを表示 (800 種類以上のファイルタイプをサポート)
* `改善` ファイルマネージャで編集可能なファイルタイプ (jpg/doc/pdf/...) に編集ボタンを追加
* `改善` ファイルマネージャが APK ファイルの基本情報, Manifest 情報, 許可リストを表示するサポート
* `改善` ファイルマネージャがオーディオおよびビデオなどのメディアファイルの基本情報および MediaInfo 情報を表示するサポート
* `改善` 正式リリースから LeakCanary を除外してパッケージを縮小
* `改善` プロジェクトのソースコード内のすべての英語コメントに中国語の簡体字翻訳を追加してコメントの読みやすさを向上
* `改善` README および CHANGELOG に複数言語をサポート (スクリプトによる自動生成)
* `改善` Gradle のビルドスクリプトのバージョン適応能力を向上
* `依存関係` 追加 MIME Util バージョン 2.3.1
* `依存関係` 追加 Toaster バージョン 12.6
* `依存関係` 追加 EasyWindow (for Toaster) バージョン 10.3
* `依存関係` Gradle バージョンを 8.5 -> 8.11.1 にアップグレード
* `依存関係` Rhino バージョン 1.7.15-SNAPSHOT -> 1.8.0-SNAPSHOT にアップグレード
* `依存関係` Android Material バージョン 1.10.0 -> 1.12.0 にアップグレード
* `依存関係` AndroidX Annotation バージョン 1.7.0 -> 1.9.1 にアップグレード
* `依存関係` AndroidX AppCompat バージョン 1.6.1 -> 1.7.0 にアップグレード
* `依存関係` AndroidX WebKit バージョン 1.8.0 -> 1.12.1 にアップグレード
* `依存関係` Apache Commons Lang3 バージョン 3.13.0 -> 3.16.0 を更新
* `依存関係` ARSCLib バージョン 1.2.4 -> 1.3.1 にアップグレード
* `依存関係` Gson バージョン 2.10.1 -> 2.11.0 にアップグレード
* `依存関係` Jackson DataBind バージョン 2.13.3 -> 2.13.4.2 にアップグレード
* `依存関係` Joda Time バージョン 2.12.5 -> 2.12.7 にアップグレード
* `依存関係` LeakCanary バージョン 2.12 -> 2.14 にアップグレード
* `依存関係` MLKit Barcode Scanning バージョン 17.2.0 -> 17.3.0 にアップグレード
* `依存関係` MLKit Text Recognition Chinese バージョン 16.0.0 -> 16.0.1 にアップグレード
* `依存関係` Retrofit2 Converter Gson バージョン 2.9.0 -> 2.11.0 にアップグレード
* `依存関係` Retrofit2 Retrofit バージョン 2.9.0 -> 2.11.0 にアップグレード
* `依存関係` Desugar JDK Libs バージョン 2.0.3 -> 2.0.4 にアップグレード
* `依存関係` Test Runner バージョン 1.5.2 -> 1.6.2 にアップグレード
* `依存関係` JUnit Jupiter バージョン 5.10.0 -> 5.10.3 を更新
* `依存関係` OkHttp3 バージョン 5.0.0-alpha.11 -> 4.12.0 にダウングレード

# v6.5.0

###### 2023/12/02

* `新機能` opencc モジュール, 中国語の簡体/繁体と地域語彙を変換 (参照: プロジェクト文書 > [中国語変換](https://docs.autojs6.com/#/opencc)) (Ref to [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `新機能` UiSelector に [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) 及び [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) メソッドを追加 _[`issue #115`](http://issues.autojs6.com/115)_
* `新機能` アプリ打包設定ページで ABI とライブラリの絞り込みに対応 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `修正` パッケージ化アプリのファイルサイズが異常に大きい問題 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `修正` パッケージ化アプリで一部の例外メッセージが表示されないおよび印刷されない問題
* `修正` アプリ打包設定ページでアプリアイコン選択後に空白アイコンが表示されることがある問題を修正
* `修正` ML Kit OCR を含むパッケージ化アプリでコンテキスト未初期化例外が生じる場合がある問題
* `修正` ocr.recognizeText/detect メソッドが無効である問題
* `修正` 一部のテキスト (例:ログページ) 表示言語がアプリ設定言語と一致しない可能性のある問題
* `修正` 一部の言語でホームページドロワースイッチ項目にテキストが溢れる可能性のある問題
* `修正` 一部のデバイスでアクセシビリティサービスが有効化直後に自動的に無効化され, 通知メッセージが表示されない問題 _[`issue #181`](http://issues.autojs6.com/181)_
* `修正` 一部のデバイスでアクセシビリティサービスが有効化後に物理的なボタンでアプリがクラッシュする問題 (試修) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `修正` auto(true) メソッドでアクセシビリティサービスを再起動後, pickup 関数が異常を起こす問題 (試修) _[`issue #184`](http://issues.autojs6.com/184)_
* `修正` Android 7.x でアクセシビリティサービスが使用できない問題
* `修正` floaty モジュールでフローティングウィンドウを作成するとアプリがクラッシュする可能性のある問題 (試修)
* `修正` app.startActivity メソッドが略語パラメータを使用できない問題 _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `修正` importClass でインポートしたクラス名とグローバル変数が衝突するとコードが例外をスローする問題 _[`issue #185`](http://issues.autojs6.com/185)_
* `修正` Android 14 で runtime.loadJar/loadDex メソッドが正常に使用できない可能性がある問題 (試修)
* `修正` Android システムのクイック設定パネルに [レイアウト範囲分析] と [レイアウト階層分析] が表示されない問題 _[`issue #193`](http://issues.autojs6.com/193)_
* `修正` 自動更新チェック機能がアプリの [ANR](https://developer.android.com/topic/performance/vitals/anr) を引き起こす可能性がある問題 (試修) _[`issue #186`](http://issues.autojs6.com/186)_
* `修正` ファイルマネージャーでサンプルコードフォルダーをクリックして [上へ] ボタンを押すとワーキングパスページに戻れない問題 _[`issue #129`](http://issues.autojs6.com/129)_
* `修正` コードエディターで置換機能を使用時に置換ボタンが表示されない問題
* `修正` コードエディターで長押し削除するとアプリがクラッシュする可能性のある問題 (試修)
* `修正` コードエディターで fx ボタンをクリックしてモジュール関数クイックパネルが表示されない問題
* `修正` コードエディターのモジュール関数クイックパネルのボタン名が溢れる可能性のある問題
* `改善` コードエディターのモジュール関数クイックパネルがナイトモードに適応します
* `改善` パッケージ化されたアプリケーションの起動画面がナイトモードに適応し, アプリアイコンのレイアウトが調整されます
* `改善` アプリ打包設定ページでソフトキーボードの ENTER キーによるカーソルフォーカス移動に対応
* `改善` アプリ打包設定ページで ABI タイトルとライブラリタイトルのタップによる全選択切替に対応
* `改善` アプリ打包設定ページでデフォルト ABI のスマート選択に対応し, 選択不可項目の案内ヒントを追加
* `改善` ファイルマネージャーのフォルダ右クリックメニューには, パッケージ化アプリのオプションが追加されます
* `改善` ファイルマネージャーでファイル/フォルダーの種類と特性に応じてメニュー表示を調整
* `改善` アクセシビリティサービスが有効になっているが機能していない場合, 異常状態は AutoJs6 ホームページのドロワースイッチに反映されます
* `改善` コンソールのエラー出力に詳細なスタック情報を添付
* `依存関係` 追加 ARSCLib バージョン 1.2.4
* `依存関係` 追加 Flexbox バージョン 3.0.0
* `依存関係` 追加 Android OpenCC バージョン 1.2.0
* `依存関係` Gradle バージョンを 8.5-rc-1 -> 8.5 にアップグレード

# v6.4.2

###### 2023/11/15

* `新機能` dialogs.build メソッドは inputSingleLine オプション引数をサポートします
* `新機能` console.setTouchable メソッド, 浮動コンソールのタッチ可否を設定 _[`issue #122`](http://issues.autojs6.com/122)_
* `修正` ocr の一部メソッドが領域引数を認識できない問題 _[`issue #162`](http://issues.autojs6.com/162)_ _[`issue #175`](http://issues.autojs6.com/175)_
* `修正` Android 7.x で新バージョンを検出する際にバージョン情報を取得できなかった問題
* `修正` Android 14 でスクリーンショット権限を要求するとアプリがクラッシュする問題
* `修正` ホームドロワーで [浮動ボタン] を切り替えるとアプリがクラッシュすることがある問題
* `修正` メニューを使用して浮動ボタンを閉じた後にアプリを再起動すると浮動ボタンが表示されたままになることがある問題
* `修正` Android 13+ の設定で AutoJs6 の言語を切り替えても反映されない問題
* `修正` ビルドツールが最初のビルド時に OpenCV リソースの自動デプロイを完了できない問題
* `改善` bridges モジュールのネイティブ化によるスクリプト実行効率の向上 (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7c41af6d2b9b36d00440a9c8b7e971d025f98327))
* `改善` 無障害サービス関連コードのリファクタリングにより無障害サービスの機能安定性を強化 (実験的) _[`issue #167`](http://issues.autojs6.com/167)_
* `改善` UiObject と UiObjectCollection の印刷出力形式
* `改善` ビルドツールで Gradle JDK バージョンが要件を満たしていない場合のアップグレード通知
* `依存関係` Gradle バージョンのアップグレード 8.4 -> 8.5-rc-1
* `依存関係` Commons IO バージョンのダウングレード 2.14.0 -> 2.8.0
* `依存関係` Jackson DataBind バージョンのダウングレード 2.14.3 -> 2.13.3

# v6.4.1

###### 2023/11/02

* `修正` ビルドツールが未知のプラットフォームに自動適応しない問題 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `修正` スクリプトの終了時にアプリがクラッシュすることがある問題 _[`issue #159`](http://issues.autojs6.com/159)_
* `修正` http モジュールでレスポンスオブジェクトの body.contentType の戻り値タイプが間違っている問題 _[`issue #142`](http://issues.autojs6.com/142)_
* `修正` device.width と device.height の戻り値が正しくない問題 _[`issue #160`](http://issues.autojs6.com/160)_
* `修正` コードエディタでのロングプレス削除時にアプリがクラッシュすることがある問題 (試修) _[`issue #156`](http://issues.autojs6.com/156)_
* `修正` コードエディタでテキストを逆選択した後の通常操作でアプリがクラッシュすることがある問題
* `修正` 一部のデバイスで AutoJs6 アプリアイコンのロングプレスでショートカットメニューが表示されない問題
* `修正` 一部のデバイスでプロジェクトパッケージング時に確認ボタンをクリックしても反応しない問題
* `修正` app.sendBroadcast と app.startActivity が省略名パラメータを使用できない問題
* `修正` floaty モジュールの JsWindow#setPosition などのメソッドの初回呼び出し時の機能異常
* `改善` Termux 関連権限を追加して Intent を利用して Termux で ADB コマンドを実行可能に _[`issue #136`](http://issues.autojs6.com/136)_
* `改善` http レスポンスオブジェクトで body.string/bytes の再呼び出しに対応
* `改善` GitHub Actions の自動パッケージングサポートを追加 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `改善` ビルドツールが Temurin プラットフォームに自動適応
* `依存関係` Gradle バージョンのアップグレード 8.4-rc-3 -> 8.4
* `依存関係` Android dx バージョンのアップグレード 1.11 -> 1.14

# v6.4.0

###### 2023/10/30

* `新機能` ocr モジュールが Paddle Lite エンジンをサポート (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `新機能` パッケージング機能が内蔵プラグインと外部プラグインの両方のパッケージング方法をサポート (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `新機能` WebSocket モジュール, WebSocket 通信に使用 (参照: プロジェクト文書 > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `新機能` barcode/qrcode モジュール, バーコード/QR コード認識に使用 (参照: プロジェクト文書 > [バーコード](https://docs.autojs6.com/#/barcode) / [QR コード](https://docs.autojs6.com/#/qrcode))
* `新機能` shizuku モジュール (参照 プロジェクトドキュメント > [Shizuku](https://docs.autojs6.com/#/shizuku)) とホームドロワー権限スイッチ
* `新機能` device.rotation/orientation などのプロパティ, 画面方向を取得
* `新機能` 内部 Java クラスが静的プロパティアクセスをサポート
* `新機能` Android のシステム設定でアプリ言語の切替に対応 (Android 13+)
* `新機能` 設定から追加またはアプリアイコン長押しで [アプリショートカット](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn) を有効にし, 文書/設定などのページを起動可能
* `修正` 一部のスクリプトが正常に終了しない問題を解決するための一部 PR の再マージ (by [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `修正` パッケージングされたアプリが AutoJs6 の新しい API を使用できない問題 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `修正` パッケージングされたアプリのシステムナイトモードでのスタイル異常
* `修正` VSCode プラグインがファイルをローカルに保存する際にファイル拡張子情報が失われる問題
* `修正` コルーチン機能を使用してプロジェクトを実行する際に未処理例外でアプリがクラッシュする問題
* `修正` アプリの再起動または終了時に浮動ボタンの位置状態情報を記録できない問題
* `修正` 画面を横向きに回転させた時に浮動ボタンが画面をはみ出すことがある問題 _[`issue #90`](http://issues.autojs6.com/90)_
* `修正` 画面の反復回転時に浮動ボタンの座標と画面端の方向を復元できない問題
* `修正` デバイスの画面方向が変わった時に更新されたデバイスの設定情報を取得できない問題 _[`issue #153`](http://issues.autojs6.com/153)_
* `修正` 画面を横向きに回転させた時にツールバーのタイトルフォントが小さすぎる問題
* `修正` 画面を横向きに回転させた時にアプリのホームページのタブレイアウトが混んでいる問題
* `修正` 一部のデバイスでメッセージ浮動枠が表示されないまたは重複して表示されることがある問題
* `修正` 複数のスクリプトが同時に実行されている時にメッセージ浮動枠が隠れることがある問題 _[`issue #67`](http://issues.autojs6.com/67)_
* `修正` ブロードキャストを使用してレイアウトを解析する時にレイアウトをクリックするとメニューが表示されず, アプリがクラッシュする問題
* `修正` 二回目以降に作成された WebSocket インスタンスがリスナーを正常にトリガーできない問題
* `修正` importPackage の全体リダイレクトを取り消して特定のスコープでのパッケージインポートの異常を回避 _[`issue #88`](http://issues.autojs6.com/88)_
* `修正` ログページでコピー/エクスポートするとアプリがクラッシュする場合がある問題
* `改善` ログのエクスポートを送信に改名し, 実際の意味に沿ったエクスポートを再実装
* `改善` ログ送信で項目が多すぎる場合に自動切り詰めと通知に対応
* `改善` ocr モジュールで ML Kit と Paddle Lite 両エンジンに対応 (参照: プロジェクト文書 > [光学文字認識](https://docs.autojs6.com/#/ocr?id=p-mode))
* `改善` 無障害サービスの自動開始成功率を向上
* `改善` Kotlin アノテーション処理を kapt から KSP に移行
* `改善` ビルドツールで IntelliJ IDEA EAP に対応
* `改善` ビルドツールが Java リリースバージョンに適応して [無効なリリースバージョン] 問題を回避するように
* `改善` ビルドツールの IDE および関連プラグインのバージョンダウングレードロジックを最適化し, バージョン予測能力を追加
* `改善` VSCode プラグイン 1.0.7 に適応
* `依存関係` Rikka Shizuku バージョン 13.1.5 を追加
* `依存関係` MLKit Barcode Scanning バージョン 17.2.0 を追加
* `依存関係` Android SDK バージョンのアップグレード 33 -> 34
* `依存関係` OpenCV バージョンのアップグレード 4.5.5 -> 4.8.0 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依存関係` Gradle バージョンのアップグレード 8.3-rc-1 -> 8.4-rc-3
* `依存関係` Apache Commons Lang3 バージョンのアップグレード 3.12.0 -> 3.13.0
* `依存関係` Glide バージョンのアップグレード 4.15.1 -> 4.16.0
* `依存関係` Android Analytics バージョンのアップグレード 14.3.0 -> 14.4.0
* `依存関係` AndroidX WebKit バージョンのアップグレード 1.7.0 -> 1.8.0
* `依存関係` AndroidX Preference バージョンのアップグレード 1.2.0 -> 1.2.1
* `依存関係` AndroidX Annotation バージョンのアップグレード 1.6.0 -> 1.7.0
* `依存関係` AndroidX RecyclerView バージョン 1.3.0 -> 1.3.2 を更新
* `依存関係` Android Material バージョンのアップグレード 1.9.0 -> 1.10.0
* `依存関係` AndroidX AppCompat バージョンのアップグレード 1.4.2 -> 1.6.1
* `依存関係` Commons IO バージョンのアップグレード 2.8.0 -> 2.14.0
* `依存関係` Jackson DataBind バージョンのアップグレード 2.13.3 -> 2.14.3
* `依存関係` Zeugma Solutions LocaleHelper バージョン 1.5.1 を削除

# v6.3.3

###### 2023/07/21

* `新機能` コードエディターのコードコメント機能 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新機能` auto.stateListener プロパティ, アクセシビリティサービスの接続状態を監視 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新機能` UiObject#nextSibling/lastChild/offset などのメソッド, 要素間の移動に使用 (参照: プロジェクト文書 > [要素ノード](https://docs.autojs6.com/#/uiObjectType))
* `修正` VSCode プラグインがスクリプトの文字総数が 4 桁を超えるとデータを解析できない問題 _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `修正` VSCode プラグインでファイルを正常に保存できない問題 _[`issue #92`](http://issues.autojs6.com/92)_ _[`issue #94`](http://issues.autojs6.com/94)_
* `修正` 無障害サービスの [管理] メニュー項目のクリックでページが遷移しないことがある問題
* `修正` runtime.requestPermissions メソッドが消失した問題 _[`issue #104`](http://issues.autojs6.com/104)_
* `修正` events.emitter が MainThreadProxy パラメータに対応しない問題 _[`issue #103`](http://issues.autojs6.com/103)_
* `修正` _[`pr #78`](http://pr.autojs6.com/78)_ で存在していたコードエディタがコードを整形できない問題
* `修正` JavaAdapter を使用すると ClassLoader の呼び出しスタックがオーバーフローする問題 _[`issue #99`](http://issues.autojs6.com/99)_ _[`issue #110`](http://issues.autojs6.com/110)_
* `改善` モジュールスコープの調整 (by [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `改善` リリースバージョンアプリの起動時の署名チェックを削除 (by [LZX284](https://github.com/LZX284)) _[`pr #81`](http://pr.autojs6.com/81)_
* `改善` _[`pr #98`](http://pr.autojs6.com/98)_ に基づくコードエディタのコードコメント機能の動作, スタイル, およびカーソル位置処理
* `改善` _[`pr #98`](http://pr.autojs6.com/98)_ に基づいてコードコメントメニュー項目を追加
* `改善` VSCode プラグイン 1.0.6 に適応
* `改善` UiObject#parent メソッドに階数パラメータのサポートを追加 (参照 プロジェクトドキュメント > [コントロールノード](https://docs.autojs6.com/#/uiObjectType))
* `依存関係` Gradle バージョンのアップグレード 8.2 -> 8.3-rc-1

# v6.3.2

###### 2023/07/06

* `新機能` crypto モジュール, データの暗号化/復号に使用 (参照: プロジェクト文書 > [暗号](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
* `新機能` UI モードの textswitcher/viewswitcher/viewflipper/numberpicker/video/search などの要素
* `新機能` ログページのコピー/エクスポートなどの機能 _[`issue #76`](http://issues.autojs6.com/76)_
* `新機能` クライアントモードに IP アドレスの履歴機能を追加
* `修正` クライアントモードの自動接続やサーバーモードの自動起動後に IP アドレス情報が表示されない問題
* `修正` クライアントモードおよびサーバーモードで接続後に言語やナイトモードを切り替えると接続が切断され再接続できない問題
* `修正` クライアントモードでカスタムポートを使用できない問題
* `修正` クライアントモードで特定の文字を入力すると AutoJs6 がクラッシュする問題
* `修正` VSCode プラグインのリモートコマンドが解析に失敗し, コマンドが応答しない問題 (一時的な修正)
* `修正` Android 7.x で新バージョンを検出した際にバージョン詳細を取得できない問題
* `修正` images.pixel がアクセシビリティサービスのスクリーンショットのピクセルカラー値を取得できない問題 _[`issue #73`](http://issues.autojs6.com/73)_
* `修正` UI モードの Android ネイティブコントロールがプリセットコントロール属性を使用できない問題
* `修正` runtime.loadDex/loadJar で複数のファイルを読み込む際に最初のファイルだけが有効になる問題 _[`issue #88`](http://issues.autojs6.com/88)_
* `修正` 一部のデバイスでアプリケーションをインストール後にランチャーに文書アイコンのみが表示される問題 (一時的な修正) _[`issue #85`](http://issues.autojs6.com/85)_
* `改善` VSCode プラグインバージョン 1.0.5 に対応
* `改善` クライアントモード接続中にホームドロワーのサブタイトルにターゲット IP アドレスを表示
* `改善` クライアントモードで不正な入力に対して警告を表示するサポートを追加
* `改善` クライアントモードでソフトキーボードのエンターキーを使用して接続を確立
* `改善` サーバーモードの開始後は常にオンの状態を維持 (手動でオフにするかアプリケーションプロセスが終了するまで) _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `改善` AutoJs6 と VSCode プラグイン間の双方向バージョン検出を実装し異常が検出された場合に警告_[`issue #89`](http://issues.autojs6.com/89)_
* `改善` cheerio モジュールのサポート追加 (参照: [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `改善` JsWebSocket インスタンスで rebuild によりインスタンスを再生成して接続可能 _[`issue #69`](http://issues.autojs6.com/69)_
* `改善` base64 モジュールが number 配列および Java バイト配列を主要なパラメータとしてエンコード及びデコードをサポート
* `改善` JavaMail for Android のサポート追加 _[`issue #71`](http://issues.autojs6.com/71)_
* `改善` バージョン更新情報を取得する際, Blob データタイプを使用してプロクシなしのネットワーク環境に対応
* `改善` SMS データ読み取り許可 (android.permission.READ_SMS) を追加 (デフォルトではオフ)
* `改善` findMultiColors メソッドの内部実装 (by [LYS86](https://github.com/LYS86)) _[`pr #72`](http://pr.autojs6.com/72)_
* `改善` runtime.loadDex/loadJar/load がディレクトリレベルでのロードや複数ファイルの同時ロードをサポート
* `依存関係` LeakCanary バージョン 2.11 -> 2.12 にアップグレード
* `依存関係` Android Analytics バージョン 14.2.0 -> 14.3.0 にアップグレード
* `依存関係` Gradle バージョン 8.2-milestone-1 -> 8.2 にアップグレード

# v6.3.1

###### 2023/05/26

* `新機能` 通知許可及びホームドロワースイッチを追加 _[`issue #55`](http://issues.autojs6.com/55)_
* `新機能` UI モードが簡単な Android レイアウト解析をサポート (サンプルコード > レイアウト > 簡単な Android レイアウトを参照)
* `新機能` UI モードの console/imagebutton/ratingbar/switch/textclock/togglebutton などの要素
* `新機能` UI モードのコントロールに [OmniColor](https://docs.autojs6.com/#/omniTypes?id=omnicolor) タイプの色値をサポート (例: color="orange")
* `新機能` UI モードのコントロールが attr メソッドでの属性設定を完全サポート (例: ui.text.attr('color', 'blue'))
* `新機能` UI モードのコントロールがブール型の属性値のデフォルト形式をサポート (例: clickable="true" は clickable または isClickable と簡略化可能)
* `新機能` button コントロールが isColored および isBorderless ブール型属性をサポート
* `新機能` console.resetGlobalLogConfig メソッド, グローバルログ設定をリセット
* `新機能` web.newWebSocket メソッド, WebSocket インスタンスを作成 (参照: プロジェクト文書 > [Web](https://docs.autojs6.com/#/web?id=m-newwebsocket))
* `修正` ファイルマネージャーのフォルダソートの異常
* `修正` floaty モジュールで構築された浮動ウィンドウのスタイルや位置を調整できない問題 _[`issue #60`](http://issues.autojs6.com/60)_
* `修正` floaty モジュールで構築された浮動ウィンドウがシステムステータスバーと重なる問題
* `修正` http.postMultipart メソッドの機能異常 _[`issue #56`](http://issues.autojs6.com/56)_
* `修正` Android 7.x でスクリプトを実行できない問題 _[`issue #61`](http://issues.autojs6.com/61)_
* `修正` sign.property ファイルが存在しないとプロジェクトを構築できない問題
* `修正` 新しいバージョンのシステムで AutoJs6 がバックグラウンドにある時に前景通知許可がないためクラッシュする問題 (API >= 33)
* `修正` console.show メソッドの呼び出し後にログウィンドウの FAB ボタンをクリックしてもログがクリアされない問題
* `修正` スクリプトエディタのデバッグ時に発生する prototype の空ポインタ例外
* `修正` スクリプトエディタがスクリプトを実行する時キャッシュフォルダに一時スクリプトを実行するのではなく, 元の位置に保存して実行することでスクリプト内容の喪失問題を回避
* `修正` レイアウトの階層解析の階層色バー幅を調整して階層が多すぎる場合にコンポーネント名を表示できるようにした問題 _[`issue #46`](http://issues.autojs6.com/46)_
* `改善` レイアウト解析の浮動ウインドウに終了ボタンを追加してウィンドウを閉じるように _[`issue #63`](http://issues.autojs6.com/63)_
* `改善` スクリプトの絶対パスを省略形式で使用することによりテキストの長さを短縮し, 読みやすくする
* `改善` Error を Exception に変更し, 例外が発生した時に AutoJs6 がクラッシュしないようにする
* `改善` ビュー (View) のバインディングを ButterKnife から View Binding に移行 _[`issue #48`](http://issues.autojs6.com/48)_
* `改善` サーバーモードの異常終了時には AutoJs6 起動時に自動的に再起動 _[`issue #64`](http://issues.autojs6.com/64)_
* `改善` クライアントモードの異常終了時には AutoJs6 起動時に最新の履歴アドレスに自動的に接続
* `依存関係` LeakCanary バージョン 2.10 -> 2.11 にアップグレード
* `依存関係` Android Material バージョン 1.8.0 -> 1.9.0 にアップグレード
* `依存関係` AndroidX WebKit バージョン 1.6.1 -> 1.7.0 にアップグレード
* `依存関係` OkHttp3 バージョン 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11 にアップグレード
* `依存関係` MLKit Text Recognition Chinese バージョン 16.0.0-beta6 -> 16.0.0 にアップグレード

# v6.3.0

###### 2023/04/29

* `新機能` ocr モジュール, 光学文字認識に使用 (参照: プロジェクト文書 > [光学文字認識](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `新機能` notice モジュール, 通知を送信 (参照: プロジェクト文書 > [通知](https://docs.autojs6.com/#/notice))
* `新機能` s13n モジュール, データを標準化 (参照: プロジェクト文書 > [標準化](https://docs.autojs6.com/#/s13n))
* `新機能` Color クラス, 色オブジェクトの作成と操作に使用 (参照: プロジェクト文書 > [Color クラス](https://docs.autojs6.com/#/colorType))
* `新機能` colors.setPaintColor メソッド, android.graphics.Paint の色を設定 (参照: プロジェクト文書 > [色](https://docs.autojs6.com/#/color?id=m-setpaintcolor))
* `新機能` アプリのフォアグラウンド実行中に画面を点灯し続ける機能と設定
* `新機能` 独立した文書ランチャー, アプリ文書の閲覧に使用 (設定で非表示/表示に対応)
* `修正` colors.toString メソッドの機能異常
* `修正` colors.material カラースペースの色定数の誤った割り当て
* `修正` app.openUrl メソッドの自動プロトコル接頭辞追加機能異常
* `修正` app.viewFile/editFile 使用時, パラメータの対応ファイルが存在しない場合の動作異常
* `修正` pickup メソッドのコールバック関数が呼び出されない問題
* `修正` bounds/boundsInside/boundsContains セレクターが狭義の空矩形 (境界逆位の矩形など) を正常に選別できない _[`issue #49`](http://issues.autojs6.com/49)_
* `修正` アクセシビリティサービスの高確率でのサービス異常発生の問題 (試修)
* `修正` レイアウト解析表示のウィジェット情報の bounds 属性値負符号がカンマに置き換えられる問題
* `修正` テーマの変更や言語の修正後にホームドキュメントタブをクリックまたは長押しするとアプリがクラッシュする問題
* `修正` テキストエディタのピンチズームでフォントサイズを調整するときに発生する可能性のある揺れの問題
* `修正` ビルドスクリプトの一部依存関係のソースがダウンロードできない問題 (すべて統合済み) _[`issue #40`](http://issues.autojs6.com/40)_
* `修正` Tasker が AutoJs6 操作プラグイン (アクションプラグイン) を追加できない問題 (試修) _[`issue #41`](http://issues.autojs6.com/41)_
* `修正` 最新の JDK バージョンでプロジェクトをコンパイルすると, ButterKnife アノテーションがリソース ID を解析できない問題 _[`issue #48`](http://issues.autojs6.com/48)_
* `修正` images.medianBlur の size パラメータの使用方法がドキュメントと一致しない問題
* `修正` engines モジュールがスクリプトの全称を表示する際にファイル名と拡張子の間のピリオドが欠落する問題
* `修正` 加重 RGB 距離測定アルゴリズム内部の実装において計算ミスの可能性 (試修)
* `修正` console モジュールの浮動ウィンドウ関連メソッドが show メソッドの前に使用できない問題
* `修正` console.setSize などのメソッドが機能しない可能性のある問題 _[`issue #50`](http://issues.autojs6.com/50)_
* `修正` UI モードの日付選択ウィジェットの minDate および maxDate 属性が日付フォーマットを正しく解析できない問題
* `修正` スクリプトを実行した後, ホームの [タスク] タブページに素早く切り替えると, 同じ実行中のタスクが 2 つ表示される問題
* `修正` ファイルマネージャーへ他画面から戻ると状態がリセットされる場合がある問題 _[`issue #52`](http://issues.autojs6.com/52)_
* `修正` ファイルマネージャーの並べ替え状態とアイコン表示が一致しない問題
* `改善` ファイルマネージャーにファイル/フォルダーの更新日時を表示
* `改善` ファイルマネージャーの並べ替え種別を記憶
* `改善` README.md にプロジェクトコンパイルビルドセクションとスクリプト開発補助セクションを追加 _[`issue #33`](http://issues.autojs6.com/33)_
* `改善` Gradle ビルドスクリプトが JDK バージョンの自主降格 (フォールバック) をサポート
* `改善` Gradle ビルドスクリプトがプラットフォームの種類およびバージョンに応じて適切なビルドツールバージョンを自動的に選択 (程度は限定的)
* `改善` images モジュール関連メソッドのリージョン (region) オプションパラメータがより多くの入力方法をサポート (プロジェクトドキュメントを参照> [オムニタイプ](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `改善` app.startActivity ページの省略形パラメータに pref/homepage/docs/about などの形式をサポート
* `改善` web モジュールのグローバルメソッドをモジュール本体にマウントして使いやすく (プロジェクトドキュメントを参照> [ウェブ](https://docs.autojs6.com/#/web))
* `改善` web.newInjectableWebView メソッド内で一部の一般的な WebView 設定オプションをデフォルトで実装
* `改善` colors モジュールに多くの変換メソッドとツールメソッドを追加し, さらに多くの静的定数と直接パラメータとして使用できる色名を追加
* `改善` console モジュールに浮動コンソールの各種スタイル設定メソッドを追加し, build ビルダーによる一括設定に対応
* `改善` 浮動コンソールでタイトル領域をドラッグして位置を移動可能
* `改善` 浮動コンソールでスクリプト終了後の遅延自動終了に対応
* `改善` 浮動コンソールと対応する Activity ウィンドウでピンチ操作による文字サイズ変更に対応
* `改善` http モジュール関連メソッドはタイムアウトオプションパラメータ (timeout) をサポート
* `依存関係` ローカライズされた Auto.js APK Builder バージョン 1.0.3
* `依存関係` ローカライズされた MultiLevelListView バージョン 1.1
* `依存関係` ローカライズされた Settings Compat バージョン 1.1.5
* `依存関係` ローカライズされた Enhanced Floaty バージョン 0.31
* `依存関係` 追加 MLKit Text Recognition Chinese バージョン 16.0.0-beta6
* `依存関係` Gradle バージョン 8.0-rc-1 -> 8.2-milestone-1 にアップグレード
* `依存関係` Android Material バージョン 1.7.0 -> 1.8.0 にアップグレード
* `依存関係` Glide バージョン 4.14.2 -> 4.15.1 にアップグレード
* `依存関係` Joda Time バージョン 2.12.2 -> 2.12.5 にアップグレード
* `依存関係` Android Analytics バージョン 14.0.0 -> 14.2.0 にアップグレード
* `依存関係` AndroidX WebKit バージョン 1.5.0 -> 1.6.1 にアップグレード
* `依存関係` AndroidX RecyclerView バージョン 1.2.1 -> 1.3.0 を更新
* `依存関係` Zip4j バージョン 2.11.2 -> 2.11.5 にアップグレード
* `依存関係` JUnit Jupiter バージョン 5.9.2 -> 5.9.3 を更新
* `依存関係` AndroidX Annotation バージョン 1.5.0 -> 1.6.0 にアップグレード
* `依存関係` Jackson DataBind バージョン 2.14.1 -> 2.14.2 にアップグレード
* `依存関係` Desugar JDK Libs バージョン 2.0.0 -> 2.0.3 にアップグレード

# v6.2.0

###### 2023/01/21

* `新機能` プロジェクトドキュメントの再設計および再執筆 (一部完了)
* `新機能` スペイン語/フランス語/ロシア語/アラビア語/日本語/韓国語/英語/繁体字中国語などの多言語に対応
* `新機能` 作業パス設定オプションにパス選択/履歴/デフォルト値のインテリジェントサジェスト機能を追加
* `新機能` ファイルマネージャーで親ディレクトリへ段階的に移動可能 ("内部ストレージ" まで)
* `新機能` ファイルマネージャーが任意のディレクトリを作業パスとして設定することが可能
* `新機能` 更新版の無視と無視済み履歴の管理に対応
* `新機能` テキストエディタがピンチズームでフォントサイズ調整をサポート
* `新機能` UiSelector#idHex セレクター, 16 進数の要素 ID で絞り込み (参照: プロジェクト文書 > [セレクター](https://docs.autojs6.com/#/uiSelectorType))
* `新機能` UiSelector#action セレクター, 要素が対応する操作で絞り込み (参照: プロジェクト文書 > [セレクター](https://docs.autojs6.com/#/uiSelectorType))
* `新機能` UiSelector#xxxMatch などの Match 系セレクター, 正規表現照合に使用 (参照: プロジェクト文書 > [セレクター](https://docs.autojs6.com/#/uiSelectorType))
* `新機能` UiSelector#pickup セレクター, 条件を組み合わせて要素を取得 (参照: プロジェクト文書 > [セレクター](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `新機能` UiObject#detect メソッド, 要素の検出に使用 (参照: プロジェクト文書 > [要素ノード](https://docs.autojs6.com/#/uiObjectType))
* `新機能` UiObject#compass メソッド, 要素間の移動に使用 (参照: プロジェクト文書 > [要素ノード](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `新機能` wait グローバルメソッド, 条件待機に使用 (参照: プロジェクト文書 > [グローバルオブジェクト](https://docs.autojs6.com/#/global?id=m-wait))
* `新機能` cX/cY/cYx グローバルメソッド, 座標の拡大/縮小に使用 (参照: プロジェクト文書 > [グローバルオブジェクト](https://docs.autojs6.com/#/global?id=m-cx))
* `新機能` App グローバル型, アプリ列挙に使用 (参照: プロジェクト文書 > [アプリ列挙クラス](https://docs.autojs6.com/#/appType))
* `新機能` i18n モジュール, JavaScript の多言語処理に使用 (banana-i18n ベース) (参照: プロジェクト文書 > 国際化)
* `修正` ソフトウェア言語切り替え後にページのテキストがちらついたり, いくつかのページボタンが機能しない問題
* `修正` 作業パスがプロジェクトである場合, ソフトウェア起動後にプロジェクトツールバーが表示されない問題
* `修正` 作業パスがソフトウェア言語切り替えに応じて自動的に変更される可能性の問題 _[`issue #19`](http://issues.autojs6.com/19)_
* `修正` 定時タスク起動遅延が顕著な問題 (試修) _[`issue #21`](http://issues.autojs6.com/21)_
* `修正` JavaScript モジュール名が上書き宣言された場合に依存関係のある内部モジュールが正常に動作しない問題 _[`issue #29`](http://issues.autojs6.com/29)_
* `修正` 高バージョンの Android システムでクイック設定パネルのアイコンをクリックした後, パネルが自動的に閉じない場合がある問題 (試修) _[`issue #7`](http://issues.autojs6.com/7)_
* `修正` 高バージョンの Android システムで一部のページが通知バー領域と重なる問題
* `修正` Android 10+ で描画色設定のサンプルが正常に実行できない問題
* `修正` サンプルコード [音楽管理者] のファイル名を [ファイル管理者] に訂正し, 正常機能を回復
* `修正` ファイルマネージャーで下にスワイプして更新する際に位置ずれが発生する可能性のある問題
* `修正` ui モジュールのスコープバインディングエラーにより, 一部の UI ベーススクリプトがコンポーネントの属性にアクセスできない問題
* `修正` 録画スクリプト後のファイル名入力ダイアログが外部領域をクリックすると録画内容が失われる問題
* `修正` ドキュメントの一部の章の見出しが画面の幅を超える場合, 自動的に改行されず内容が失われる問題
* `修正` ドキュメントのサンプルコードエリアが正常に左右にスクロールできない問題
* `修正` ドキュメントページで下にスワイプして更新する際に異常が発生し, 更新操作を元に戻せない問題 (試修)
* `修正` アプリ初回インストール後, ホームドロワーの夜間モードの切り替えが機能しない問題
* `修正` システムの夜間モードをオンにするとアプリ起動後に夜間モードが強制的に有効になる問題
* `修正` 夜間モードをオンにした後, テーマカラーが生效しない場合がある問題
* `修正` 夜間モードで一部の設定オプションのテキストと背景色が同じで識別できない問題
* `修正` Android 7.x システムで夜間モードがオフの際にナビゲーションバーのボタンが識別しにくい問題
* `修正` About ページのボタンテキストが長すぎて完全に表示されない問題
* `修正` ホームドロワーの設定項目の見出しが長すぎて, テキストとボタンが重なる問題
* `修正` ホームドロワーの権限スイッチがメッセージボックス消失後に状態が同期しない可能性のある問題
* `修正` Root 権限でホームドロワーの権限スイッチを操作できない場合, ADB ツールボックスが表示されない問題
* `修正` Root 権限でさしゅ位置を表示する際, 初回使用時に権限がないことを通知しない問題
* `修正` アイコン選択ページでアイコン要素のレイアウトが異常
* `修正` テキストエディタ起動時に夜間モード設定で画面がフラッシュすることがある問題 (試修)
* `修正` テキストエディタのフォントサイズ設定時, 使用可能な最大値が制限されている問題
* `修正` 一部の Android システムでスクリプト終了時にログに実行時間が記録されない問題
* `修正` Floating ボタンメニューの閉じるボタンを使用した後, アプリを再起動しても Floating ボタンが表示される問題
* `修正` レイアウト階層分析でリストアイテムを長押しした際, ポップアップメニューが画面の下に溢れる問題
* `修正` http.post 等のメソッドで, リクエストが閉じられない場合の問題
* `修正` colors.toString メソッドにおいて, アルファチャンネルが 0 の時にそのチャンネル情報が結果に含まれない問題
* `改善` Auto.js 4.x バージョンの公開クラスをリダイレクトして可能な限り下位互換を実現 (限界あり)
* `改善` すべてのプロジェクトモジュールをマージし, 循環参照などの問題を回避 (暫時 inrt モジュールを削除)
* `改善` Gradle のビルド設定を Groovy から KTS に移行
* `改善` Rhino の例外メッセージに多言語サポートを追加
* `改善` ホームドロワーのパーミッションスイッチは有効時にのみメッセージを表示
* `改善` ホームドロワーレイアウトがステータスバーの下に配置され, 上部のカラーバーの低互換性を回避
* `改善` クライアントおよびサーバーの接続後にホームドロワーに対応するデバイスの IP アドレスを表示
* `改善` 更新チェック/更新ダウンロード/更新通知機能が Android 7.x システムに対応
* `改善` 設定ページを再設計 (AndroidX に移行)
* `改善` 設定ページで設定項目を長押しして詳細情報を取得することが可能
* `改善` 夜間モードに "システムに従う" 設定 (Android 9+)
* `改善` アプリ起動画面が夜間モードに適応
* `改善` テーマカラーにより多くの Material Design Color (マテリアルデザインカラー) オプションを追加
* `改善` ホームの検索バーのプレースホルダーテキストの色を夜間モードに適応
* `改善` ダイアログ/テキスト/FAB/AppBar/リスト項目などを夜間モードに対応
* `改善` アプリアイコンに番号付けを追加して複数のオープンソースバージョンを共存するユーザーの使用体験を向上
* `改善` ファイルマネージャー/タスクパネルなどのリストアイテムのアイコンを適度に軽量化し, テーマカラーに適応
* `改善` ドキュメント/設定/アバウト/テーマカラー/レイアウト分析などのページおよび Floating ボタンメニューを夜間モードに適応
* `改善` Floating ボタンのレイアウト分析ボタンをクリックして直接レイアウト範囲分析を実行
* `改善` レイアウト分析のテーマが自動適応 (Floating ウィンドウがアプリのテーマに従い, クイック設定パネルがシステムのテーマに従う)
* `改善` ページレイアウトが可能な限り RTL (Right-To-Left) レイアウトに対応
* `改善` About ページにアイコンアニメーション効果を追加
* `改善` About ページの著作権声明テキストが自動的に年次情報を更新
* `改善` アプリ初回インストール後, 適切な作業ディレクトリを自動的に決定および設定
* `改善` ドキュメントページのピンチズーム機能を無効化し, ドキュメント内容の表示不具合を回避
* `改善` タスクパネルリストアイテムを相対パスで簡略化し, タスクの名前およびパスを表示
* `改善` テキストエディタのボタンテキストを適度に短縮し, テキスト内容の溢れを避ける
* `改善` テキストエディタのフォントサイズ設定でデフォルト値に戻すことが可能
* `改善` Floating ボタンのクリック反応速度を向上
* `改善` レイアウトウィジェット情報リストを使用頻度に基づいて再編成
* `改善` レイアウトウィジェット情報をクリックしてコピーする際に, セレクタの種類に応じて出力フォーマットを自動的に最適化
* `改善` ui.R および auto.R が AutoJs6 のリソース ID を簡単に取得可能
* `改善` Floating ウィンドウでファイルを選択する際に, 戻るキーを押して上位ディレクトリに戻ることができ, ウィンドウを直接閉じない
* `改善` クライアントモードでコンピュータのアドレスを入力する際に, 数値の有効性チェックおよびピリオドシンボルの自動変換をサポート
* `改善` 一部のグローバルオブジェクトおよび組み込みモジュールにオーバーライドプロテクションを追加 (プロジェクトドキュメントを参照 > グローバルオブジェクト > [オーバーライドプロテクション](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `改善` importClass および importPackage が文字列パラメータおよび可変長パラメータをサポート
* `改善` ui.run が例外発生時にスタックトレース情報を出力サポート
* `改善` app モジュール内でアプリに関連する操作メソッドが App タイプパラメータおよびアプリのエイリアスパラメータをサポート
* `改善` app.startActivity などが url オプションパラメータをサポート (参照: サンプルコード > アプリケーション > インテント)
* `改善` dialogs モジュール内の非同期コールバック関連メソッドが事前入力パラメータの省略をサポート
* `改善` device モジュールが IMEI またはハードウェアシリアル番号の取得に失敗した場合, null を返し, 例外をスローしない
* `改善` console.show が表示するログの Floating ウィンドウの文字の明るさを向上させ, 内容の識別度を高める
* `改善` ImageWrapper#saveTo が相対パスで画像ファイルを保存サポート
* `改善` colors グローバルオブジェクトを再設計し, HSV/HSL などの色モードに対応 (参照: プロジェクト文書 > [色](https://docs.autojs6.com/#/color))
* `依存関係` ローカライズされた Android Job バージョン 1.4.3
* `依存関係` ローカライズされた Android Plugin Client SDK For Locale バージョン 9.0.0
* `依存関係` ローカライズされた GitHub API バージョン 1.306
* `依存関係` 追加された JCIP Annotations バージョン 1.0
* `依存関係` 追加された AndroidX WebKit バージョン 1.5.0
* `依存関係` 追加された Commons IO バージョン 2.8.0
* `依存関係` 追加された Desugar JDK Libs バージョン 2.0.0
* `依存関係` 追加された Jackson DataBind バージョン 2.13.3
* `依存関係` 追加された Jaredrummler Android Device Names バージョン 2.1.0
* `依存関係` 追加された Jaredrummler Animated SVG View バージョン 1.0.6
* `依存関係` Jrummyapps ColorPicker バージョン 2.1.7 を Jaredrummler ColorPicker バージョン 1.1.0 に置き換え
* `依存関係` Android SDK バージョンを 32 -> 33 にアップグレード
* `依存関係` Gradle バージョンを 7.5-rc-1 -> 8.0-rc-1 にアップグレード
* `依存関係` Gradle ビルドツールバージョンを 7.4.0-alpha02 -> 8.0.0-alpha09 にアップグレード
* `依存関係` Kotlin Gradle プラグインバージョンを 1.6.10 -> 1.8.0-RC2 にアップグレード
* `依存関係` Android Material バージョンを 1.6.0 -> 1.7.0 にアップグレード
* `依存関係` AndroidX Annotation バージョンを 1.3.0 -> 1.5.0 にアップグレード
* `依存関係` AndroidX AppCompat バージョンを 1.4.1 -> 1.4.2 にアップグレード
* `依存関係` Android Analytics バージョンを 13.3.0 -> 14.0.0 にアップグレード
* `依存関係` Gson バージョンを 2.9.0 -> 2.10 にアップグレード
* `依存関係` Joda Time バージョンを 2.10.14 -> 2.12.1 にアップグレード
* `依存関係` Kotlinx Coroutines バージョンを 1.6.1-native-mt -> 1.6.1 にアップグレード
* `依存関係` OkHttp3 バージョンを 3.10.0 -> 5.0.0-alpha.7 -> 5.0.0-alpha.9 にアップグレード
* `依存関係` Zip4j バージョンを 2.10.0 -> 2.11.2 にアップグレード
* `依存関係` Glide バージョンを 4.13.2 -> 4.14.2 にアップグレード
* `依存関係` JUnit Jupiter バージョン 5.9.0 -> 5.9.1 を更新

# v6.1.1

###### 2022/05/31

* `新機能` 更新チェック/更新ダウンロード/更新通知機能 (設定ページを参照) (Android 7.x システムには一時的に対応していません)
* `修正` Android 10 システムでの外部ストレージの読み書きができない問題 _[`issue #17`](http://issues.autojs6.com/17)_
* `修正` コードエディターの長押しでアプリがクラッシュする場合がある問題 _[`issue #18`](http://issues.autojs6.com/18)_
* `修正` コードエディターの長押しメニューの "行削除"/"行コピー" が無効な問題
* `修正` コードエディターのオプションメニューに "貼り付け" がない問題
* `改善` 一部例外メッセージを文字列リソース化 (en/zh)
* `改善` 保存していない内容のダイアログのボタンレイアウトを調整し, 色分けを追加
* `依存関係` GitHub API バージョン 1.306 を追加
* `依存関係` retrofit2-rxjava2-adapter バージョン 1.0.0 を adapter-rxjava2 バージョン 2.9.0 に置き換え

# v6.1.0

###### 2022/05/26 - パッケージ名変更, 慎重にアップグレード

* `ヒント` アプリのパッケージ名を org.autojs.autojs6 に変更し, オープンソースの Auto.js アプリパッケージ名との競合を回避
* `新機能` ホームドロワーに "メディア投影権限" スイッチ (Root/ADB 方式) (状態検出は実験的)
* `新機能` ファイルマネージャーで隠しファイル/フォルダーの表示に対応 (参照: 設定ページ)
* `新機能` Root の強制確認機能 (参照: 設定とサンプルコード)
* `新機能` autojs モジュール, AutoJs6 関連情報の取得と操作に使用 (参照: サンプルコード > AutoJs6)
* `新機能` tasks モジュール, 定期タスクを管理 (参照: サンプルコード > タスク)
* `新機能` console.launch メソッド, ログページを開く
* `新機能` util.morseCode ツール, モールス信号の処理に使用 (参照: サンプルコード > ユーティリティ > モールス信号)
* `新機能` util.versionCodes ツール, Android のバージョン情報を取得 (参照: サンプルコード > ユーティリティ > Android バージョン照会)
* `新機能` util.getClass などのメソッド, Java クラスとクラス名を取得 (参照: サンプルコード > ユーティリティ > クラスとクラス名の取得)
* `新機能` timers.setIntervalExt メソッド, 条件付き周期実行に使用 (参照: サンプルコード > タイマー > 条件付き周期実行)
* `新機能` colors.toInt/rgba などのメソッド, 色変換に使用 (参照: サンプルコード > 画像と色 > 基本色変換)
* `新機能` automator.isServiceRunning/ensureService メソッド, アクセシビリティサービスの確認と利用可能状態の確保に使用
* `新機能` automator.lockScreen などのメソッド, Android 9 で追加されたアクセシビリティ操作に使用 (参照: サンプルコード > アクセシビリティ > Android 9 の追加機能)
* `新機能` automator.headsethook などのメソッド, Android 11 で追加されたアクセシビリティ操作に使用 (参照: サンプルコード > アクセシビリティ > Android 11 の追加機能)
* `新機能` automator.captureScreen メソッド, アクセシビリティサービスの画面キャプチャを取得 (参照: サンプルコード > アクセシビリティ > 画面キャプチャ取得)
* `新機能` dialogs.build メソッドは animation, linkify などのオプション引数をサポートします (サンプルコード > ダイアログ > カスタムダイアログを参照)
* `修正` dialogs.build の inputHint, itemsSelectedIndex などのオプション引数の動作が異常です
* `修正` JsDialog#on('multi_choice') コールバックパラメータの機能異常
* `修正` UiObject#parent().indexInParent() が常に -1 を返す問題 _[`issue #16`](http://issues.autojs6.com/16)_
* `修正` Promise.resolve() で返される Thenable がスクリプトの終了時に呼び出されない可能性のある問題
* `修正` パッケージ名/クラス名の誤記 (boardcast -> broadcast/auojs -> autojs)
* `修正` 新しい Android で images.requestScreenCapture がアプリをクラッシュさせる場合がある問題 (API >= 31)
* `修正` 複数スクリプトが同時に images.requestScreenCapture を呼ぶとアプリがクラッシュする場合がある問題
* `修正` new RootAutomator() を呼び出した際に発生する可能性のあるフリーズ問題
* `改善` RootAutomator は Root 権限がない場合にインスタンス化できません
* `改善` [アプリおよび開発者について] ページを再設計
* `改善` すべての組み込み JavaScript モジュールを再構築
* `改善` すべての Gradle ビルドスクリプトを再構築し, 共通設定スクリプト (config.gradle) を追加
* `改善` Gradle ビルドツールがバージョン管理とビルドファイルの自動命名をサポート
* `改善` Gradle ビルドツールが task を追加し, CRC32 要約をビルドファイルに添付 (appendDigestToReleasedFiles)
* `改善` shell が呼び出し例外を直接投げず, 戻り値へ記録するよう変更 (try/catch 不要)
* `改善` Rhino 組み込みの JSON を使用, 元の json2 モジュールを置き換え
* `改善` auto.waitFor メソッドのタイムアウト引数に対応
* `改善` threads.start メソッドのアロー関数引数に対応
* `改善` console.trace メソッドのログレベル引数に対応 (参照: サンプルコード > コンソール > 呼び出しスタックの出力)
* `改善` コンソールの文字に Material Color を使用し, 通常/夜間テーマの可読性を改善
* `改善` device.vibrate でパターン振動とモールス信号振動に対応 (参照: サンプルコード > デバイス > パターン振動/モールス信号振動)
* `改善` 外部ストレージ読み書き権限が高バージョンの Android システムに適応 (API >= 30)
* `改善` すべての ImageWrapper インスタンスが弱参照され, スクリプト終了時に自動的に回収されます (実験的)
* `依存関係` CircleImageView バージョン 3.1.0 を追加
* `依存関係` Android Analytics バージョン 13.1.0 -> 13.3.0 にアップグレード
* `依存関係` Gradle ビルドツール バージョン 7.3.0-alpha06 -> 7.4.0-alpha02 にアップグレード
* `依存関係` Android Job バージョン 1.4.2 -> 1.4.3 にアップグレード
* `依存関係` Android Material バージョン 1.5.0 -> 1.6.0 にアップグレード
* `依存関係` CrashReport バージョン 2.6.6 -> 4.0.4 にアップグレード
* `依存関係` Glide バージョン 4.13.1 -> 4.13.2 にアップグレード
* `依存関係` Joda Time バージョン 2.10.13 -> 2.10.14 にアップグレード
* `依存関係` Kotlin Gradle プラグイン バージョン 1.6.10 -> 1.6.21 にアップグレード
* `依存関係` Kotlinx Coroutines バージョン 1.6.0 -> 1.6.1-native-mt にアップグレード
* `依存関係` LeakCanary バージョン 2.8.1 -> 2.9.1 にアップグレード
* `依存関係` OkHttp3 バージョン 5.0.0-alpha.6 -> 5.0.0-alpha.7 にアップグレード
* `依存関係` Rhino エンジン バージョン 1.7.14 -> 1.7.15-SNAPSHOT にアップグレード
* `依存関係` Zip4j バージョン 2.9.1 -> 2.10.0 にアップグレード
* `依存関係` Groovy JSON バージョン 3.0.8 を削除
* `依存関係` Kotlin Stdlib JDK7 バージョン 1.6.21 を削除

# v6.0.3

###### 2022/03/19

* `新機能` 多言語切り替え機能 (未完成)
* `新機能` recorder モジュール, 時間計測に使用 (参照: サンプルコード > ストップウォッチ)
* `新機能` アクセシビリティサービスを "セキュリティ設定変更権限" で自動有効化し, 対応する設定スイッチを提供
* `修正` クイック設定パネルの関連アイコンをクリックした後, パネルが自動的に閉じない問題 (仮修理) _[`issue #7`](http://issues.autojs6.com/7)_
* `修正` toast 使用時に強制表示パラメータを使用すると AutoJs6 がクラッシュする可能性がある問題
* `修正` Socket データヘッダー情報が不完全な場合に AutoJs6 がクラッシュする可能性がある問題
* `改善` AutoJs6 の起動または再起動時にオプション設定に従ってアクセシビリティサービスを自動的に有効化
* `改善` フローティングボタンのスイッチをオンにした際にアクセシビリティサービスを自動的に有効化
* `改善` すべてのリソースファイルに対応する英訳を補完
* `改善` ホームドロワーの配置を調整し, 項目間の間隔を縮小
* `改善` ホーム画面のドロワーに前景サービスステータスのスイッチの同期を追加
* `改善` ホーム画面のドロワーを展開する際にスイッチの状態を即座に同期
* `改善` ポインタ位置の表示にステータス検出と結果の通知を追加
* `改善` 64 ビットオペレーティングシステムに対応 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `改善` フローティングボタンの初期化時に透明度設定を同時に適用 (クリック後に透明度を適用する必要なし)
* `改善` ファイル内容リセット時にサンプルコードファイルかどうかを検出し, 結果の通知を追加
* `改善` パッケージプラグインのダウンロード先を GitHub から JsDelivr に移行
* `依存関係` Zeugma Solutions LocaleHelper バージョン 1.5.1 を追加
* `依存関係` Android SDK バージョン 31 -> 32 にアップグレード
* `依存関係` Kotlinx Coroutines バージョン 1.6.0-native-mt -> 1.6.0 にアップグレード
* `依存関係` OpenCV バージョン 3.4.3 -> 4.5.4 -> 4.5.5 にアップグレード (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依存関係` OkHttp3 バージョン 3.10.0 -> 5.0.0-alpha.4 -> 5.0.0-alpha.6 にアップグレード
* `依存関係` Gradle ビルドツール バージョン 7.2.0-beta01 -> 7.3.0-alpha06 にアップグレード
* `依存関係` Auto.js-ApkBuilder バージョン 1.0.1 -> 1.0.3 にアップグレード
* `依存関係` Glide Compiler バージョン 4.12.0 -> 4.13.1 にアップグレード
* `依存関係` Gradle バージョン 7.4-rc-2 -> 7.4.1 を更新
* `依存関係` Gson バージョン 2.8.9 -> 2.9.0 にアップグレード
* `依存関係` Android Material バージョン 1.6.0-alpha02 -> 1.5.0 にダウングレード

# v6.0.2

###### 2022/02/05

* `新機能` images.bilateralFilter メソッド, 画像のバイラテラルフィルター処理に使用
* `修正` 複数回呼び出された toast が最後の呼び出ししか効果がない問題
* `修正` toast.dismiss が無効になる場合がある問題
* `修正` クライアントモードおよびサーバーモードのスイッチが正常に動作しない可能性のある問題
* `修正` クライアントモードおよびサーバーモードのスイッチ状態が正常に更新されない問題
* `修正` Android 7.x での UI モードの text 要素解析異常 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `改善` sleep が投げる ScriptInterruptedException を無視
* `依存関係` 追加 AndroidX AppCompat (Legacy) バージョン 1.0.2
* `依存関係` アップグレード AndroidX AppCompat バージョン 1.4.0 -> 1.4.1
* `依存関係` アップグレード AndroidX Preference バージョン 1.1.1 -> 1.2.0
* `依存関係` アップグレード Rhino エンジンバージョン 1.7.14-SNAPSHOT -> 1.7.14
* `依存関係` アップグレード OkHttp3 バージョン 3.10.0 -> 5.0.0-alpha.3 -> 5.0.0-alpha.4
* `依存関係` アップグレード Android Material バージョン 1.6.0-alpha01 -> 1.6.0-alpha02
* `依存関係` アップグレード Gradle ビルドツールバージョン 7.2.0-alpha06 -> 7.2.0-beta01
* `依存関係` Gradle バージョン 7.3.3 -> 7.4-rc-2 を更新

# v6.0.1

###### 2022/01/01

* `新機能` VSCode プラグインクライアント (LAN) およびサーバー (LAN/ADB) モードのサポート (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新機能` base64 モジュール, Base64 のエンコード/デコードに使用 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新機能` isInteger/isNullish/isObject/isPrimitive/isReference グローバルメソッド, 型判定に使用
* `新機能` Object.getOwnPropertyDescriptors の互換実装 (polyfill)
* `新機能` Array.prototype.flat の互換実装 (polyfill)
* `改善` global.sleep メソッドでランダム範囲と負の引数に対応
* `改善` global.toast メソッドで時間設定, 強制上書きと dismiss 操作に対応
* `改善` パッケージ名オブジェクトのグローバル化 (okhttp3/androidx/de)
* `依存関係` アップグレード Android Material バージョン 1.5.0-beta01 -> 1.6.0-alpha01
* `依存関係` アップグレード Gradle ビルドツールバージョン 7.2.0-alpha04 -> 7.2.0-alpha06
* `依存関係` アップグレード Kotlinx Coroutines バージョン 1.5.2-native-mt -> 1.6.0-native-mt
* `依存関係` アップグレード Kotlin Gradle プラグインバージョン 1.6.0 -> 1.6.10
* `依存関係` Gradle バージョン 7.3 -> 7.3.3 を更新

# v6.0.0

###### 2021/12/01

* `新機能` ホーム画面のドロワー下部にアプリ再起動ボタンを追加
* `新機能` ホームドロワーの "バッテリー最適化を無視"/"他のアプリの上に表示" などのスイッチ
* `修正` アプリの初回インストール後, 一部の領域でテーマカラーのレンダリング異常問題
* `修正` sign.property ファイルが存在しない場合のプロジェクトビルド問題
* `修正` スケジュールタスクパネルの一回限りのタスクの月表示エラー
* `修正` アプリの設定ページのスイッチカラーがテーマ変更に応じて変わらない問題
* `修正` パッケージプラグインが認識されないおよびダウンロードアドレスが無効な問題
* `修正` ホーム画面ドロワー  [使用状況アクセス権限を見る] スイッチの状態が同期されない問題
* `修正` TemplateMatching.fastTemplateMatching 潜在的な Mat メモリリーク問題
* `改善` ViewUtil.getStatusBarHeight の互換性の向上
* `改善` ホーム画面ドロワーからユーザーログイン関連モジュールおよびレイアウトプレースホルダーを削除
* `改善` ホームのコミュニティ/マーケットタブを削除し, レイアウトの整列を改善
* `改善` いくつかの設定オプションのデフォルトスイッチ状態を変更
* `改善` [アプリについて] ページに SinceDate を追加し, Copyright の表示を最適化
* `改善` JSON モジュールを 2017-06-12 バージョンにアップグレードし, cycle.js を統合
* `改善` アクティビティがフォアグラウンドになるときの自動アップデートチェック機能および関連ボタンの削除
* `改善` AppOpsKt#isOpPermissionGranted 内部コードロジック
* `改善` ResourceMonitor の安全性向上のために ReentrantLock を使用 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `改善` Maven Central などのリポジトリを使用して JCenter リポジトリを置き換え
* `改善` 重複したローカルライブラリファイルの抽出および削除
* `依存関係` ローカライズ CrashReport バージョン 2.6.6
* `依存関係` ローカライズ MutableTheme バージョン 1.0.0
* `依存関係` 追加 AndroidX Preference バージョン 1.1.1
* `依存関係` AndroidX SwipeRefreshLayout バージョン 1.1.0 を追加
* `依存関係` アップグレード Android SDK バージョン 28 -> 30 -> 31
* `依存関係` アップグレード Android Analytics バージョン 7.0.0 -> 13.1.0
* `依存関係` アップグレード Android Annotations バージョン 4.5.2 -> 4.8.0
* `依存関係` アップグレード Gradle ビルドツールバージョン 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `依存関係` アップグレード Android Job バージョン 1.2.6 -> 1.4.2
* `依存関係` アップグレード Android Material バージョン 1.1.0-alpha01 -> 1.5.0-beta01
* `依存関係` アップグレード AndroidX MultiDex バージョン 2.0.0 -> 2.0.1
* `依存関係` アップグレード Apache Commons Lang3 バージョン 3.6 -> 3.12.0
* `依存関係` AndroidX AppCompat バージョン 1.0.2 -> 1.4.0 を更新
* `依存関係` アップグレード ButterKnife Gradle プラグインバージョン 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `依存関係` アップグレード ColorPicker バージョン 2.1.5 -> 2.1.7
* `依存関係` アップグレード Espresso Core バージョン 3.1.1-alpha01 -> 3.5.0-alpha03
* `依存関係` EventBus バージョン 3.0.0 -> 3.2.0 を更新
* `依存関係` アップグレード Glide Compiler バージョン 4.8.0 -> 4.12.0
* `依存関係` アップグレード Gradle Build Tool バージョン 29.0.2 -> 30.0.2
* `依存関係` Gradle バージョン 4.10.2 -> 6.5 -> 7.0.2 -> 7.3 を更新
* `依存関係` アップグレード Groovy-Json プラグインバージョン 3.0.7 -> 3.0.8
* `依存関係` アップグレード Gson バージョン 2.8.2 -> 2.8.9
* `依存関係` アップグレード Java バージョン 8 -> 11 -> 16
* `依存関係` アップグレード Joda Time バージョン 2.9.9 -> 2.10.13
* `依存関係` JUnit バージョン 4.12 -> 4.13.2 を更新
* `依存関係` アップグレード Kotlin Gradle プラグインバージョン 1.3.10 -> 1.4.10 -> 1.6.0
* `依存関係` アップグレード Kotlinx Coroutines バージョン 1.0.1 -> 1.5.2-native-mt
* `依存関係` アップグレード LeakCanary バージョン 1.6.1 -> 2.7
* `依存関係` アップグレード LicensesDialog バージョン 1.8.1 -> 2.2.0
* `依存関係` アップグレード Material Dialogs バージョン 0.9.2.3 -> 0.9.6.0
* `依存関係` アップグレード OkHttp3 バージョン 3.10.0 -> 5.0.0-alpha.2 -> 5.0.0-alpha.3
* `依存関係` ReactiveX RxJava2 RxAndroid バージョン 2.0.1 -> 2.1.1 を更新
* `依存関係` ReactiveX RxJava2 バージョン 2.1.2 -> 2.2.21 を更新
* `依存関係` アップグレード Retrofit2 Converter Gson バージョン 2.3.0 -> 2.9.0
* `依存関係` アップグレード Retrofit2 Retrofit バージョン 2.3.0 -> 2.9.0
* `依存関係` アップグレード Zip4j バージョン 1.3.2 -> 2.9.1
* `依存関係` アップグレード Rhino エンジンバージョン 1.7.7.2 -> 1.7.13 -> 1.7.14-SNAPSHOT
* `依存関係` アップグレード OpenCV バージョン 3.4.3 -> 4.5.4