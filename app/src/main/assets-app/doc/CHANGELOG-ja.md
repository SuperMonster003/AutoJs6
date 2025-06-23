******

### バージョン履歴

******

# v6.6.4

###### 2025/05/31

* `ヒント` API変更: ui.(status/navigation)BarAppearanceLight[By] -> ui.(status/navigation)BarIconLight[By]
* `新機能` util.dpToPx/spToPx/pxToDp/pxToSp メソッド (ピクセル単位変換用)
* `修正` 画面を横向きに回転するとサブタイトルが不完全に表示される問題
* `修正` 画面を横向きに回転すると一部ページのコンテンツがサイドナビゲーションバーに隠れる問題
* `修正` Android 15 で一部ページのステータスバー背景の着色領域が不完全になる問題 _[`issue #398`](http://issues.autojs6.com/398)_
* `修正` コードエディタが信頼性の低いエンコーディングでファイルを書き込み, デコードエラーを引き起こす可能性がある問題 (修正試行)
* `改善` アプリおよび開発者ページのレイアウト適応性を向上し, 不要なレイアウト区分を削除
* `改善` README.md のビルド節に複数の方法を追加してターゲット設定ページの特定を容易化 _[`issue #404`](http://issues.autojs6.com/404)_
* `依存関係` Androidx ConstraintLayout バージョン 2.2.1 を追加

# v6.6.3

###### 2025/05/27

* `新機能` バージョン履歴機能: 多言語の変更履歴と統計を閲覧
* `新機能` timers.keepAlive メソッド (グローバルへ昇格) によりスクリプトの活動を維持
* `新機能` engines.on('start/stop/error', callback) などのイベントリスナーでエンジンのグローバルイベントを受信
* `新機能` images.detectMultiColors メソッドで複数点のカラーを検査 _[`issue #374`](http://issues.autojs6.com/374)_
* `新機能` images.matchFeatures/detectAndComputeFeatures メソッド: フル解像度画像検索 (参照 [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `新機能` images.compressToBytes メソッドで画像を圧縮してバイト配列を取得
* `新機能` images.downsample メソッドで解像度を下げて新しい ImageWrapper を生成
* `新機能` ui.keepScreenOn メソッドで UI ページがフォーカス中は画面を点灯したまま維持
* `新機能` ui.root プロパティ (getter) で UI レイアウト内の「ウィンドウコンテンツのルートコンテナ」ノードを取得
* `新機能` webview 要素が JsBridge ベースのウェブページレイアウトをサポート (参照 [Auto.js Pro](https://g.pro.autojs.org/)) [例: Layout > Interactive HTML / Vue2 + Vant (SFC)] _[`issue #281`](http://issues.autojs6.com/281)_
* `修正` Docs タブおよび Docs アクティビティのオンラインドキュメントがシステムナビゲーションバーと重なる場合がある問題を修正
* `修正` 一部ページで Toolbar ボタンをタップすると誤ってタイトルのクリックイベントが発火する問題を修正
* `修正` コードエディタの空行が一部デバイスで四角形として表示される問題を修正
* `修正` テーマカラー設定のカラーピッカーダイアログが無限に重なる問題を修正
* `修正` アクセシビリティサービスが無効のときに音量アップキーが全スクリプトを停止しない問題を修正
* `修正` スケジュールタスクでユーザー定義ブロードキャストメッセージを編集する際に IME が入力エリアを覆う問題を修正
* `修正` webview 内のコントロールがソフトキーボードを正常に呼び出せない問題を修正
* `修正` APK 情報ダイアログがアプリ名や SDK 情報を取得できない場合がある問題を修正
* `修正` ファイルマネージャーのサンプルでプロジェクトディレクトリに入る際にサブフォルダーの内容が読み込まれない問題を修正
* `修正` Android 15 の UI モードでステータスバーが上部コンテンツを覆う問題を修正
* `修正` Android 15 の一部ページでステータスバー背景色がテーマカラーと同期しない問題を修正
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
* `改善` レイアウトインスペクターが要素の非表示をサポート (寄稿 [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `改善` レイアウトインスペクターのメニューに機能グループ化用のグラデーション区切りを追加
* `改善` project.json がスクリプトプロジェクト用 permissions オプションをサポート (寄稿 [wirsnow](https://github.com/wirsnow)) _[`pr #391`](http://pr.autojs6.com/391)_ _[`issue #362`](http://issues.autojs6.com/362)_
* `改善` 単一ファイルをパッケージングする際, インストール済みアプリの宣言パーミッションを自動読み取り・選択 _[`issue #362`](http://issues.autojs6.com/362)_
* `改善` テーマカラーの適用範囲を拡大, より多くのウィジェットをサポート
* `改善` メインページのドロワー幅がランドスケープおよび超ワイド画面に適応
* `改善` 『アプリについて』と『開発者について』ページに横向きとコンパクトレイアウトを追加
* `改善` 設定ダイアログに「デフォルト値を使用」メニューを追加
* `改善` ファイルマネージャーの FAB が外部タップ時に自動で隠れるように
* `改善` コードフォーマッターが `??`, `?.`, `??=` 演算子をサポート
* `改善` コードエディタが GB18030 / UTF-16 (LE/BE) / Shift_JIS などのエンコーディングでのファイル読み書きに対応
* `改善` コードエディタがファイルの詳細情報 (パス/エンコーディング/改行コード/総バイト数・文字数 など) の表示に対応 _[`issue #395`](http://issues.autojs6.com/395)_
* `改善` Intent 操作 (編集/閲覧/インストール/送信/再生など) にエラーメッセージを追加
* `改善` webview 要素の url 属性が相対パスをサポート
* `改善` ImageWrapper#saveTo の path パラメータが相対パスをサポート
* `改善` images.save が PNG 圧縮を quality パラメータでサポート _[`issue #367`](http://issues.autojs6.com/367)_
* `改善` 無視した更新リストとクライアントモードアドレスをクリア可能に
* `改善` バージョンアップデート情報を UI 言語に合わせて表示
* `改善` 非同期ロードでファイルマネージャーリストのスクロールがよりスムーズに
* `改善` コンソールでのスクリプト例外メッセージの内容とフォーマットを改善
* `改善` サンプルコードをフォルダーの初期内容にリセット可能
* `改善` APK 署名検証を高速化
* `改善` APK/メディアファイル情報ダイアログを速度と表示ロジック面で最適化
* `改善` Gradle ビルドスクリプトが新しいバージョンに柔軟に対応 _[`discussion #369`](http://discussions.autojs6.com/369)_
* `依存関係` 同梱 Material Dialogs バージョン 0.9.6.0 (ローカライズ済み)
* `依存関係` 同梱 Material Date Time Picker バージョン 4.2.3 (ローカライズ済み)
* `依存関係` 同梱 libimagequant バージョン 2.17.0 (ローカライズ済み)
* `依存関係` 同梱 libpng バージョン 1.6.49 (ローカライズ済み)
* `依存関係` 追加 ICU4J バージョン 77.1
* `依存関係` 追加 Jsoup バージョン 1.19.1
* `依存関係` 追加 Material Progressbar バージョン 1.4.2
* `依存関係` 追加 Flexmark Java HTML to Markdown バージョン 0.64.8
* `依存関係` 更新 Gradle 8.14-rc-1 -> 8.14
* `依存関係` 更新 Androidx Room 2.7.0 -> 2.7.1

# v6.6.2

###### 2025/04/16

* `新機能` ui.statusBarAppearanceLight, statusBarAppearanceLightBy, navigationBarColor などのメソッド
* `新機能` ui.statusBarHeight 属性 (getter) により, ステータスバーの高さを取得可能 _[`issue #357`](http://issues.autojs6.com/357)_
* `新機能` images.flip メソッドで画像を反転 _[`issue #349`](http://issues.autojs6.com/349)_
* `新機能` 設定ページに『ファイル拡張子』オプションを追加
* `新機能` テーマ設定ページが新しいレイアウト (グルーピング, 配置, 検索, 履歴, カラーピッカーの改善など) に対応
* `修正` Android 15 において, ステータスバーの背景色がテーマカラーと一致しない問題を修正
* `修正` plugins.load メソッドでプラグインが正しくロードされない問題を修正 _[`issue #290`](http://issues.autojs6.com/290)_
* `修正` Android 7.x で dx ライブラリが正しく動作しない問題を修正 _[`issue #293`](http://issues.autojs6.com/293)_
* `修正` require を使用して組み込みモジュールをインポートする際, ScriptRuntime の同期状態に問題が生じる (暫定対応) _[`issue #298`](http://issues.autojs6.com/298)_
* `修正` notice モジュールに getBuilder などの拡張メソッドが欠如している問題を修正 _[`issue #301`](http://issues.autojs6.com/301)_
* `修正` shizuku/shell メソッドが文字列パラメータを受け付けない問題を修正 _[`issue #310`](http://issues.autojs6.com/310)_
* `修正` colors.pixel メソッドがシングルチャンネル画像のパラメータを受け付けない問題を修正 _[`issue #350`](http://issues.autojs6.com/350)_
* `修正` engines.execScript / execScriptFile メソッドが, スクリプト実行時にワーキングディレクトリを誤って設定する問題を修正 _[`issue #358`](http://issues.autojs6.com/358)_ _[`issue #340`](http://issues.autojs6.com/340)_ _[`issue #339`](http://issues.autojs6.com/339)_
* `修正` floaty.window / floaty.rawWindow がサブスレッドで実行できない問題を修正
* `修正` floaty.getClip がクリップボードの内容を正しく取得できない場合がある問題を修正 _[`issue #341`](http://issues.autojs6.com/341)_
* `修正` ui.inflate の返り値から attr, on, click などのプロトタイプメソッドが欠落する問題を修正
* `修正` XML 構文を用いて JavaScript の式を属性値として使用する際, スコープのコンテキストが不正にバインドされる問題 _[`issue #319`](http://issues.autojs6.com/319)_
* `修正` 一部メソッドで発生する例外が try..catch によって捕捉されない問題を修正 _[`issue #345`](http://issues.autojs6.com/345)_
* `修正` レイアウト解析ページでコード生成時にアプリがクラッシュする可能性がある問題を修正 _[`issue #288`](http://issues.autojs6.com/288)_
* `修正` パッケージ化されたアプリが shizuku モジュールを正しく利用できない問題を修正 _[`issue #227`](http://issues.autojs6.com/227)_ _[`issue #231`](http://issues.autojs6.com/231)_ _[`issue #284`](http://issues.autojs6.com/284)_ _[`issue #287`](http://issues.autojs6.com/287)_ _[`issue #304`](http://issues.autojs6.com/304)_
* `修正` コードエディタで行末へ移動すると, カーソルが次の行の先頭に位置してしまう問題を修正
* `修正` 設定ページのダイアログ要素を連続して高速にタップすると, アプリがクラッシュする問題を修正
* `改善` パッケージアプリのテンプレートにおける APK ファイルサイズの最適化
* `改善` アプリ (およびパッケージアプリ) がより多くの権限に対応 _[`issue #338`](http://issues.autojs6.com/338)_
* `改善` パッケージングページに Pinyin ライブラリのオプションを追加
* `改善` パッケージアプリのメインページにおいて, ステータスバーの背景とテキスト色を最適化
* `改善` パッケージアプリの設定ページに, 特別な権限 (全ファイルアクセスや通知送信) 用のトグルスイッチを追加 _[`issue #354`](http://issues.autojs6.com/354)_
* `改善` コントロールのテキストとアイコンが, テーマの明るさに合わせて自動で調整されるよう改善
* `改善` コントロールの色と背景とのコントラストが低い場合の視認性を向上
* `改善` カラーピッカー内の HEX 入力欄に, クリップボードからの貼り付け時の互換性を向上
* `改善` アプリのナビゲーションバーを, より良いビジュアル体験のために透明または半透明に設定
* `改善` ライトモード時, ステータスバーとナビゲーションバーのデフォルト UI モードをカラー `md_grey_50` に設定
* `改善` ホームドロワー内のアクセシビリティサービスのスイッチを, スクリプトコードと同期するよう改善
* `改善` ホームページのドキュメントページに双方向検索ボタンを追加
* `改善` ホームページの『ファイル』タブで, 長押しによりフローティングボタンの表示状態を切り替え可能に
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
* `依存関係` Eclipse Paho Client Mqttv3 バージョン 1.1.0 を追加 _[`issue #330`](http://issues.autojs6.com/330)_
* `依存関係` Gradle Compile のバージョンを 34 から 35 に更新
* `依存関係` Gradle を 8.12 から 8.14-rc-1 に更新
* `依存関係` Rhino を 1.8.0-SNAPSHOT から 1.8.1-SNAPSHOT に更新
* `依存関係` Androidx Recyclerview を 1.3.2 から 1.4.0 に更新
* `依存関係` Androidx Room を 2.6.1 から 2.7.0 に更新
* `依存関係` Androidx WebKit を 1.12.1 から 1.13.0 に更新
* `依存関係` Pinyin4j を 2.5.0 から 2.5.1 に更新

# v6.6.1

###### 2025/01/01

* `新機能` 中国語のピンイン変換用の Pinyin モジュール (プロジェクトドキュメントを参照 > [中国語ピンイン](https://docs.autojs6.com/#/pinyin))
* `新機能` 中国語のピンイン変換用の Pinyin4j モジュール (プロジェクトドキュメントを参照 > [中国語ピンイン](https://docs.autojs6.com/#/pinyin4j))
* `新機能` コントロールまたはコントロールコレクションが類似しているかを判定する UiObject#isSimilar と UiObjectCollection#isSimilar メソッド
* `新機能` グローバルメソッド "currentComponent", 現在アクティブなコンポーネントの名前情報を取得するために使用されます
* `修正` 一部の環境でバージョンを低く戻すことでプロジェクトが正常にコンパイルできなくなる問題
* `修正` 存在しないメソッドを呼び出す際に発生する可能性がある「非プリミティブ型の値」エラー
* `修正` 一部のデバイスでスクリプトショートカットが正常に追加できない問題 (暫定修正) _[`issue #221`](http://issues.autojs6.com/221)_
* `修正` automator.click/longClick メソッドのパラメーター型制限の誤り _[`issue #275`](http://issues.autojs6.com/275)_
* `修正` セレクターが ConsString 型のパラメーターをサポートしない問題 _[`issue #277`](http://issues.autojs6.com/277)_
* `修正` UiObjectCollection インスタンスにメソッドおよびプロパティが欠如している問題
* `改善` パッケージ化ページで署名設定, キーストア管理, 権限設定をサポートします ([luckyloogn]() による) _[`pr #286`]()_
* `改善` フローティングウィンドウの現在のパッケージ名および現在のアクティビティ名の認識精度を向上 (優先順位: Shizuku > Root > A11Y)
* `改善` currentPackage と currentActivity の認識精度を向上 (優先順位: Shizuku > Root > A11Y)
* `改善` ログアクティビティウィンドウの個別エントリのテキスト内容をダブルクリックまたは長押しで選択する機能を復元 _[`issue #280`](http://issues.autojs6.com/280)_
* `改善` project.json ファイルが破損した場合でも, スクリプトプロジェクトの重要な情報を可能な限り復元
* `改善` 単一ファイルをパッケージ化する際に, 自動生成されるパッケージ名のサフィックスを簡体字中国語からピンインに変換 (多音字対応)
* `改善` UiSelector#findOnce と UiSelector#find メソッドで負の引数をサポート
* `改善` app.startActivity/startDualActivity メソッドの適応性が向上しました
* `改善` UI 要素や className に関連するセレクターが RecyclerView や Snackbar などのパッケージ名プレフィックス省略形式にさらに対応
* `改善` Rhino エンジンの最新の上流コードを同期し, 既存のプロジェクトに適応させる
* `依存関係` Pinyin4j バージョン 2.5.0 を追加
* `依存関係` Jieba Analysis バージョン 1.0.3-SNAPSHOT (修正版) を追加
* `依存関係` Gradle バージョンを 8.11.1 から 8.12 にアップグレード

# v6.6.0

###### 2024/12/02 - 組み込みモジュールの書き換え, 慎重にアップグレードしてください

* `ヒント` 既存のモジュールは, スクリプトの実行効率を高めるために Kotlin によって再作成されましたが, 改良にはいくつかの反復が必要です
* `ヒント` 組み込みの init.js ファイルはデフォルトでは空ですが, 開発者がカスタマイズするためのモジュールをマウントできます
* `新機能` axios モジュール / cheerio モジュール ([AutoX](https://github.com/kkevsekk1/AutoX) 参照)
* `新機能` sqlite モジュール, SQLite データベースの簡単な操作に使用されます ([Auto.js Pro](https://g.pro.autojs.org/) 参照) (プロジェクトドキュメント > [SQLite](https://docs.autojs6.com/#/sqlite) を参照)
* `新機能` mime モジュール, MIME タイプ文字列を処理および解析するために使用されます (プロジェクトドキュメント > [MIME](https://docs.autojs6.com/#/mime) を参照)
* `新機能` nanoid モジュール, 文字列 ID ジェネレーターとして使用できます ([ai/nanoid](https://github.com/ai/nanoid) 参照)
* `新機能` sysprops モジュール, ランタイム環境の構成データを取得するために使用されます (プロジェクトドキュメント > [システムプロパティ](https://docs.autojs6.com/#/sysprops) を参照)
* `新機能` ocr モジュールは [Rapid OCR](https://github.com/RapidAI/RapidOCR) エンジンをサポートしています
* `新機能` レイアウト解析はウィンドウの切り替えをサポートしています (Auto.js Pro)
* `新機能` auto.clearCache メソッド, コントロールキャッシュをクリアすることをサポートします (Auto.js Pro)
* `新機能` threads.pool メソッド, シンプルなスレッドプールアプリケーションをサポートします (Auto.js Pro)
* `新機能` images.matchTemplate メソッドは useTransparentMask オプションパラメーターを追加し, 透明画像検索をサポートします (Auto.js Pro)
* `新機能` images.requestScreenCaptureAsync メソッド, UI モードで非同期にスクリーンキャプチャの許可を要求するために使用されます (Auto.js Pro)
* `新機能` images.requestScreenCapture メソッドは isAsync オプションパラメーターを追加し, 非同期でスクリーンキャプチャを取得することをサポートします (Auto.js Pro)
* `新機能` images.on('screen_capture', callback) などのイベントリスニングメソッドは, スクリーンキャプチャが使用可能なイベントをモニターすることをサポートします (Auto.js Pro)
* `新機能` images.stopScreenCapture メソッドは, スクリーンキャプチャ要求に関連するリソースを積極的に解放することをサポートします (Auto.js Pro)
* `新機能` images.psnr/mse/ssim/mssim/hist/ncc および images.getSimilarity メソッドは, 画像類似度測定値を取得するために使用されます
* `新機能` images.isGrayscale メソッドは, 画像がグレースケールかどうかを判断するために使用されます
* `新機能` images.invert メソッドは, 画像の色を反転するために使用されます
* `新機能` s13n.point/time メソッドは, ポイントオブジェクトと時間オブジェクトを正規化するために使用されます (プロジェクトドキュメント > [正規化](https://docs.autojs6.com/#/s13n) を参照)
* `新機能` console モジュールの gravity (重力), touchThrough (タッチスルー), backgroundTint (背景の色調) などの関連メソッド (プロジェクトドキュメント > [コンソール](https://docs.autojs6.com/#/console) を参照)
* `新機能` Mathx.randomInt/Mathx.randomFloat メソッドは, 指定された範囲内の乱数またはランダム浮動小数点数を戻すために使用されます
* `新機能` app.launchDual/startDualActivity などのメソッドは二重起動アプリケーションの処理に使用されます (Shizuku または Root 権限が必要) (実験的)
* `新機能` app.kill メソッド, アプリケーションを強制終了するために使用されます (Shizuku または Root 権限が必要)
* `新機能` floaty.getClip メソッドは, フローティングウィンドウのフォーカスを通じてクリップボードの内容を間接的に取得するために使用されます
* `修正` Fragment のサブクラス (例 [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48) 等) 内の View Binding のメモリリーク
* `修正` [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21) 等のクラス内のインスタンスのメモリリーク
* `修正` Android 14 でスクリーンキャプチャの許可を求めるとアプリがクラッシュする問題 (by [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `修正` Android 14 でフォアグラウンドサービスを開始するとアプリがクラッシュする問題
* `修正` Android 14 でコードエディタの実行ボタンをクリックしても点灯しない問題
* `修正` プロジェクトのパッケージ後に必要なライブラリファイルが不足してアプリが正常に動作しない可能性がある問題 _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `修正` プロジェクトの編集時に指定されたアイコンリソースが存在しない場合にアプリがクラッシュする問題 _[`issue #203`](http://issues.autojs6.com/203)_
* `修正` スクリーンキャプチャの許可を取得時に指定された画面方向のスクリーンショットリソースをパラメータで正常に取得できない場合がある問題
* `修正` 一部デバイスでスクリプトショートカットを正常に追加できない問題 (試み修正) _[`issue #221`](http://issues.autojs6.com/221)_
* `修正` http モジュールと関連する方法を呼び出すと蓄積される要求による送信遅延の問題 _[`issue #192`](http://issues.autojs6.com/192)_
* `修正` AutoJs6 がメインアクティビティページに入る前に Shizuku サービスが正常に利用できない可能性がある問題 (試み修正) _[`issue #255`](http://issues.autojs6.com/255)_
* `修正` random(min, max) メソッドが結果がオーバーフローする問題の可能性
* `修正` pickup メソッドの結果型パラメータが空配列を正しく渡せない問題
* `修正` UiObject#bounds() で取得したコントロール矩形が意図と異なる形で変更される可能性がある問題
* `修正` text/button/input 要素のテキスト内容にハーフサイズの二重引用符が含まれている場合に正しく解析されない問題
* `修正` text/textswitcher 要素の autoLink 属性が機能しない問題
* `修正` 異なるスクリプトが同じ ScriptRuntime オブジェクトを誤って共有する可能性がある問題
* `修正` グローバル変数 HEIGHT および WIDTH が Getter 動的属性を失う問題
* `修正` スクリプトの起動時に RootShell が即時ロードされることで起動が遅くなる問題
* `修正` コンソールフローティングウィンドウで背景色を設定すると矩形の角が失われる問題
* `修正` サービスの異常の問題が発生する可能性のあるアクセシビリティサービスの自動開始 (試み修正)
* `修正` ホームページのドキュメントページで左右に滑動すると WebView コントロールが ViewPager の切り替えを誘発する可能性がある問題
* `修正` ファイル拡張子が大文字を含む場合ファイルマネージャが認識できない問題
* `修正` ファイルマネージャがプロジェクトディレクトリに最初に入った時点でプロジェクトを自動的に認識できない可能性がある問題
* `修正` ファイルマネージャでフォルダを削除した後にページが自動更新されない問題
* `修正` ファイルマネージャでファイルおよびフォルダを並べ替える際に ASCII の先頭文字名を後置する可能性がある問題
* `修正` コードエディタのデバッグ機能で FAILED ASSERTION 例外が発生する
* `修正` コードエディタでデバッグ中にエディタを閉じた後に再度正常にデバッグできない問題
* `修正` コードエディタで行の端へ移動すると末尾の文字が漏れ可能性がある問題
* `修正` メインアクティビティページでログアクティビティページを起動するときにフラッシュスクリーンの問題が発生する可能性がある
* `修正` アプリパッケージが opencc モジュールを正常に使用できない問題
* `改善` パッケージページ内の「利用不可 ABI」コントロールのクリックヒントユーザー体験
* `改善` Shizuku を使用して「ポインタ位置」表示スイッチをコントロールするサポート
* `改善` Shizuku を使用して「メディアの投影」および「セキュリティ設定の変更」許可スイッチをコントロールするサポート
* `改善` automator.gestureAsync/gesturesAsync がコールバック関数パラメータをサポート
* `改善` tasks モジュールが同期方式でデータベース操作を実行するサポート, データアクセスの不一致問題を回避
* `改善` スクリプト実行モードがパイプ記号区切りモードパラメータ (例 `"ui|auto";` のように開始) をサポート
* `改善` スクリプト実行モードがシングルクォーテーションおよびバックティックをサポートし, セミコロンを省略 (例 `'ui';` または `'ui'` のように開始) 可能
* `改善` スクリプト実行モードが axios, cheerio, dayjs などのモードパラメータで内蔵拡張モジュールのクイックインポートをサポート (例 `"axios";` のように開始)
* `改善` スクリプト実行モードが x または jsox モードパラメータで JavaScript 内蔵オブジェクト拡張モジュールをクイック有効化をサポート (例 `"x";` のように開始)
* `改善` img 要素の src および path 属性がローカル相対パスをサポート (例 `<img src="a.png"` />)
* `改善` コードエディタで Java クラスやパッケージ名をインポートする位置のインテリジェント判断をサポート
* `改善` images モジュールが画像パラメータとしてパスの直接使用をサポート
* `改善` importPackage が文字列パラメータをサポート
* `改善` サーバーモードの IP アドレスがクリップボードインポートをスマートに認識し, スペースキーのスマート変換をサポート
* `改善` ファイルマネージャが新しいファイルを作成する際にデフォルトのプレフィックス選択と適切な数字サフィックスの自動生成をサポート
* `改善` ファイル管理プロジェクトの実行時の例外メッセージを具体化 _[`issue #268`](http://issues.autojs6.com/268)_
* `改善` ファイルマネージャがより多くの種類をサポートし, 対応するアイコンシンボルを表示 (800 種類以上のファイルタイプをサポート)
* `改善` ファイルマネージャで編集可能なファイルタイプ (jpg/doc/pdf...) に編集ボタンを追加
* `改善` ファイルマネージャが APK ファイルの基本情報, Manifest 情報, 許可リストを表示するサポート
* `改善` ファイルマネージャがオーディオおよびビデオなどのメディアファイルの基本情報および MediaInfo 情報を表示するサポート
* `改善` 単一ファイルパッケージング時に適切な正規化名パッケージを自動入力し, 無効な文字フィルタリング推奨をサポート
* `改善` 単一ファイルパッケージング時に同じパッケージ名のアプリがインストールされている場合にアイコンを自動設定し, バージョン番号とバージョン名を増加させるサポート
* `改善` パックプロジェクト設定ファイルが abis/libs オプションをサポートし, デフォルトで含まれる ABI アーキテクチャと拡張ライブラリを指定
* `改善` パックプロジェクト設定ファイルの abis/libs オプションが無効または使用可能でない場合に関連メッセージをサポート
* `改善` 不必要な増加を避けるためにリークキャナリーをリリースバージョンから除外
* `改善` プロジェクトのソースコード内のすべての英語コメントに中国語の簡体字翻訳を追加してコメントの読みやすさを向上
* `改善` README および CHANGELOG に複数言語をサポート (スクリプトによる自動生成)
* `改善` Gradle のビルドスクリプトのバージョン適応能力を向上
* `依存関係` 追加 MIME Util バージョン 2.3.1
* `依存関係` 追加 Toaster バージョン 12.6
* `依存関係` 追加 EasyWindow (for Toaster) バージョン 10.3
* `依存関係` Gradle バージョンを 8.5 -> 8.11.1 にアップグレード
* `依存関係` Rhino バージョン 1.7.15-SNAPSHOT -> 1.8.0-SNAPSHOT にアップグレード
* `依存関係` Android Material Lang3 バージョン 1.10.0 -> 1.12.0 にアップグレード
* `依存関係` Androidx Annotation バージョン 1.7.0 -> 1.9.1 にアップグレード
* `依存関係` Androidx AppCompat バージョン 1.6.1 -> 1.7.0 にアップグレード
* `依存関係` Androidx WebKit バージョン 1.8.0 -> 1.12.1 にアップグレード
* `依存関係` Apache Commons バージョン 3.13.0 -> 3.16.0 にアップグレード
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
* `依存関係` Junit Jupiter バージョン 5.10.0 -> 5.10.3 にアップグレード
* `依存関係` OkHttp3 バージョン 5.0.0-alpha.11 -> 4.12.0 にダウングレード

# v6.5.0

###### 2023/12/02

* `新機能` opencc モジュール (参照 プロジェクトドキュメント > [中文変換](https://docs.autojs6.com/#/opencc)) (Ref to [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `新機能` UiSelector に [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) 及び [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) メソッドを追加 _[`issue #115`](http://issues.autojs6.com/115)_
* `新機能` パッケージ化アプリページに ABI およびライブラリのフィルタリングを追加 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `修正` パッケージ化アプリのファイルサイズが異常に大きい問題 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `修正` パッケージ化アプリで一部の例外メッセージが表示されないおよび印刷されない問題
* `修正` パッケージ化ページでアプリアイコンを選択すると空のアイコンが表示される可能性のある問題
* `修正` パッケージ化アプリに MLKit Google OCR ライブラリを含むと初期化コンテキスト例外が発生する可能性がある
* `修正` ocr.<u>mlkit/ocr</u>.<u>recognizeText/detect</u> メソッドが無効である問題
* `修正` 一部のテキスト (例:ログページ) 表示言語がアプリ設定言語と一致しない可能性のある問題
* `修正` 一部の言語でホームページドロワースイッチ項目にテキストが溢れる可能性のある問題
* `修正` 一部のデバイスでアクセシビリティサービスが有効化直後に自動的に無効化され, 通知メッセージが表示されない問題 _[`issue #181`](http://issues.autojs6.com/181)_
* `修正` 一部のデバイスでアクセシビリティサービスが有効化後に物理的なボタンでアプリがクラッシュする問題 (試修) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `修正` auto(true) メソッドでアクセシビリティサービスを再起動後, pickup 関数が異常を起こす問題 (試修) _[`issue #184`](http://issues.autojs6.com/184)_
* `修正` floaty モジュールでフローティングウィンドウを作成するとアプリがクラッシュする可能性のある問題 (試修)
* `修正` app.startActivity メソッドが略語パラメータを使用できない問題 _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `修正` importClass でインポートしたクラス名とグローバル変数が衝突するとコードが例外をスローする問題 _[`issue #185`](http://issues.autojs6.com/185)_
* `修正` Android 7.x でアクセシビリティサービスが使用できない問題
* `修正` Android 14 で runtime.<u>loadJar/loadDex</u> メソッドが正常に使用できない可能性がある問題 (試修)
* `修正` Android システムのクイック設定パネルに「レイアウト範囲分析」と「レイアウト階層分析」が表示されない問題 _[`issue #193`](http://issues.autojs6.com/193)_
* `修正` 自動更新チェック機能がアプリの [ANR](https://developer.android.com/topic/performance/vitals/anr) を引き起こす可能性がある問題 (試修) _[`issue #186`](http://issues.autojs6.com/186)_
* `修正` ファイルマネージャーでサンプルコードフォルダーをクリックして「上へ」ボタンを押すとワーキングパスページに戻れない問題 _[`issue #129`](http://issues.autojs6.com/129)_
* `修正` コードエディターで置換機能を使用時に置換ボタンが表示されない問題
* `修正` コードエディターで長押し削除するとアプリがクラッシュする可能性のある問題 (試修)
* `修正` コードエディターで fx ボタンをクリックしてモジュール関数クイックパネルが表示されない問題
* `修正` コードエディターのモジュール関数クイックパネルのボタン名が溢れる可能性のある問題
* `改善` コードエディターのモジュール関数クイックパネルがナイトモードに適応します
* `改善` パッケージ化されたアプリケーションの起動画面がナイトモードに適応し, アプリアイコンのレイアウトが調整されます
* `改善` パッケージ化されたアプリのページは, ソフトウェアキーボードの ENTER キーを使用してカーソルナビゲーションをサポートします
* `改善` パッケージ化されたアプリのページでは, ABI およびライブラリのタイトルをクリックして全選択状態を切り替えることができます
* `改善` パッケージ化されたアプリのページでは, デフォルトの ABI 選択がインテリジェントになり, 選択不可の項目にガイドプロンプトが追加されます
* `改善` ファイルマネージャーは, ファイルとフォルダの種類と特性に基づいてメニュー項目の表示を調整します
* `改善` ファイルマネージャーのフォルダ右クリックメニューには, パッケージ化アプリのオプションが追加されます
* `改善` アクセシビリティサービスが有効になっているが機能していない場合, 異常状態は AutoJs6 ホームページのドロワースイッチに反映されます
* `改善` コンソールはエラーメッセージを印刷するときに詳細なスタック情報を含みます
* `依存関係` 追加 ARSCLib バージョン 1.2.4
* `依存関係` 追加 Flexbox バージョン 3.0.0
* `依存関係` 追加 Android OpenCC バージョン 1.2.0
* `依存関係` Gradle バージョンを 8.5-rc-1 -> 8.5 にアップグレード

# v6.4.2

###### 2023/11/15

* `新機能` dialogs.build() オプションパラメータ属性 inputSingleLine
* `新機能` console.setTouchable メソッド _[`issue #122`](http://issues.autojs6.com/122)_
* `修正` ocr モジュールの一部の方法でエリアパラメータを認識できなかった問題 _[`issue #162`](http://issues.autojs6.com/162)_ _[`issue #175`](http://issues.autojs6.com/175)_
* `修正` Android 7.x で新バージョンを検出する際にバージョン情報を取得できなかった問題
* `修正` Android 14 でスクリーンショット権限を要求するとアプリがクラッシュする問題
* `修正` ホームドロワーで「浮動ボタン」を切り替えるとアプリがクラッシュすることがある問題
* `修正` メニューを使用して浮動ボタンを閉じた後にアプリを再起動すると浮動ボタンが表示されたままになることがある問題
* `修正` Android 13 以降のシステム設定ページで AutoJs6 の言語を選択および切り替えると反映されない問題
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
* `改善` http モジュールで取得したレスポンスオブジェクトが body.string() および body.bytes() メソッドを再使用可能に
* `改善` GitHub Actions の自動パッケージングサポートを追加 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `改善` ビルドツールが Temurin プラットフォームに自動適応
* `依存関係` Gradle バージョンのアップグレード 8.4-rc-3 -> 8.4
* `依存関係` Android dx バージョンのアップグレード 1.11 -> 1.14

# v6.4.0

###### 2023/10/30

* `新機能` ocr モジュールが Paddle Lite エンジンをサポート (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `新機能` パッケージング機能が内蔵プラグインと外部プラグインの両方のパッケージング方法をサポート (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `新機能` WebSocket モジュール (参照 プロジェクトドキュメント > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `新機能` barcode / qrcode モジュール (参照 プロジェクトドキュメント > [バーコード](https://docs.autojs6.com/#/barcode) / [QR コード](https://docs.autojs6.com/#/qrcode))
* `新機能` shizuku モジュール (参照 プロジェクトドキュメント > [Shizuku](https://docs.autojs6.com/#/shizuku)) とホームドロワー権限スイッチ
* `新機能` device.rotation / device.orientation などの方法
* `新機能` 内部 Java クラスが静的プロパティアクセスをサポート
* `新機能` Android システム設定ページでアプリの言語を選択および切り替えをサポート (Android 13 以降)
* `新機能` 設定ページにアプリのショートカットを追加またはアプリアイコンを長押しして [アプリショートカット](https://developer.android.com/guide/topics/ui/shortcuts?hl=ja) をアクティブ化し, ドキュメントや設定ページを起動可能に
* `修正` 一部のスクリプトが正常に終了しない問題を解決するための一部 PR の再マージ (by [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `修正` パッケージングされたアプリが AutoJs6 の新しい API を使用できない問題 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `修正` パッケージングされたアプリのシステムナイトモードでのスタイル異常
* `修正` VSCode プラグインがファイルをローカルに保存する際にファイル拡張子情報が失われる問題
* `修正` コルーチン機能を使用してプロジェクトを実行する際に未処理例外でアプリがクラッシュする問題
* `修正` アプリの再起動または終了時に浮動ボタンの位置状態情報を記録できない問題
* `修正` デバイスの画面方向が変わった時に更新されたデバイスの設定情報を取得できない問題 _[`issue #153`](http://issues.autojs6.com/153)_
* `修正` 画面を横向きに回転させた時にツールバーのタイトルフォントが小さすぎる問題
* `修正` 画面を横向きに回転させた時にアプリのホームページのタブレイアウトが混んでいる問題
* `修正` 画面を横向きに回転させた時に浮動ボタンが画面をはみ出すことがある問題 _[`issue #90`](http://issues.autojs6.com/90)_
* `修正` 画面を何度も回転させた時に浮動ボタンの座標および画面の横方向の位置を復元できない問題
* `修正` 一部のデバイスでメッセージ浮動枠が表示されないまたは重複して表示されることがある問題
* `修正` 複数のスクリプトが同時に実行されている時にメッセージ浮動枠が隠れることがある問題 _[`issue #67`](http://issues.autojs6.com/67)_
* `修正` ブロードキャストを使用してレイアウトを解析する時にレイアウトをクリックするとメニューが表示されず, アプリがクラッシュする問題
* `修正` 二回目以降に作成された WebSocket インスタンスがリスナーを正常にトリガーできない問題
* `修正` importPackage の全体リダイレクトを取り消して特定のスコープでのパッケージインポートの異常を回避 _[`issue #88`](http://issues.autojs6.com/88)_
* `修正` ログアクティビティページでコピーまたはエクスポート機能を使用するとアプリがクラッシュする問題
* `改善` ログアクティビティページのエクスポート機能の名前を送信機能に変更し, 実際の意味に合ったエクスポート機能を再実装
* `改善` ログアクティビティページの送信機能がエントリー数が多い場合には自動的に分割して通知
* `改善` ocr モジュールが Google MLKit および Paddle Lite エンジンの双方をサポートに (参照 プロジェクトドキュメント > [光学文字認識](https://docs.autojs6.com/#/ocr?id=p-mode))
* `改善` 無障害サービスの自動開始成功率を向上
* `改善` Kotlin アノテーション処理を kapt から KSP に移行
* `改善` ビルドツールが IntelliJ Idea EAP バージョンをサポート
* `改善` ビルドツールが Java リリースバージョンに適応して「無効なリリースバージョン」問題を回避するように
* `改善` ビルドツールの IDE および関連プラグインのバージョンダウングレードロジックを最適化し, バージョン予測能力を追加
* `改善` VSCode プラグイン 1.0.7 に適応
* `依存関係` Rikka Shizuku バージョン 13.1.5 を追加
* `依存関係` MLKit Barcode Scanning バージョン 17.2.0 を追加
* `依存関係` OpenCV バージョンのアップグレード 4.5.5 -> 4.8.0 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依存関係` Gradle Compile バージョンのアップグレード 33 -> 34
* `依存関係` Gradle バージョンのアップグレード 8.3-rc-1 -> 8.4-rc-3
* `依存関係` Apache Commons Lang3 バージョンのアップグレード 3.12.0 -> 3.13.0
* `依存関係` Glide バージョンのアップグレード 4.15.1 -> 4.16.0
* `依存関係` Android Analytics バージョンのアップグレード 14.3.0 -> 14.4.0
* `依存関係` Androidx WebKit バージョンのアップグレード 1.7.0 -> 1.8.0
* `依存関係` Androidx Preference バージョンのアップグレード 1.2.0 -> 1.2.1
* `依存関係` Androidx Annotation バージョンのアップグレード 1.6.0 -> 1.7.0
* `依存関係` Androidx Recyclerview バージョンのアップグレード 1.3.0 -> 1.3.2
* `依存関係` Android Material バージョンのアップグレード 1.9.0 -> 1.10.0
* `依存関係` Androidx AppCompat バージョンのアップグレード 1.4.2 -> 1.6.1
* `依存関係` Commons IO バージョンのアップグレード 2.8.0 -> 2.14.0
* `依存関係` Jackson DataBind バージョンのアップグレード 2.13.3 -> 2.14.3
* `依存関係` Zeugma Solutions LocaleHelper バージョン 1.5.1 を削除

# v6.3.3

###### 2023/07/21

* `新機能` コードエディタのコードコメント機能 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新機能` auto.stateListener を無障害サービス接続状態のリスニングに使用 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新機能` UiObject タイプに nextSibling / lastChild / offset などの方法を追加 (参照 プロジェクトドキュメント > [コントロールノード](https://docs.autojs6.com/#/uiObjectType))
* `修正` VSCode プラグインがスクリプトの文字総数が 4 桁を超えるとデータを解析できない問題 _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `修正` VSCode プラグインがファイルを正常に保存できない問題 _[`issue #92`](http://issues.autojs6.com/91)_ _[`issue #94`](http://issues.autojs6.com/93)_
* `修正` 無障害サービスの「管理」メニュー項目のクリックでページが遷移しないことがある問題
* `修正` runtime.requestPermissions メソッドが消失した問題 _[`issue #104`](http://issues.autojs6.com/104)_
* `修正` events.emitter が MainThreadProxy パラメータに対応しない問題 _[`issue #103`](http://issues.autojs6.com/103)_
* `修正`  _[`pr #78`](http://pr.autojs6.com/78)_ で存在していたコードエディタがコードを整形できない問題
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

* `新機能` crypto モジュール (プロジェクトドキュメント > [暗号文](https://docs.autojs6.com/#/crypto) を参照) _[`issue #70`](http://issues.autojs6.com/70)_
* `新機能` UI モードに textswitcher / viewswitcher / viewflipper / numberpicker / video / search などのコントロールを追加
* `新機能` ログ活動ページにログのコピーやエクスポート機能を追加 _[`issue #76`](http://issues.autojs6.com/76)_
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
* `改善` cheerio モジュールのサポート追加 (参照: [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `改善` JsWebSocket インスタンスが rebuild メソッドを使用してインスタンスを再構築し接続を行うサポート追加 _[`issue #69`](http://issues.autojs6.com/69)_
* `改善` base64 モジュールが number 配列および Java バイト配列を主要なパラメータとしてエンコード及びデコードをサポート
* `改善` JavaMail for Android のサポート追加 _[`issue #71`](http://issues.autojs6.com/71)_
* `改善` バージョン更新情報を取得する際, Blob データタイプを使用してプロクシなしのネットワーク環境に対応
* `改善` クライアントモード接続中にホームドロワーのサブタイトルにターゲット IP アドレスを表示
* `改善` クライアントモードで不正な入力に対して警告を表示するサポートを追加
* `改善` クライアントモードでソフトキーボードのエンターキーを使用して接続を確立
* `改善` サーバーモードの開始後は常にオンの状態を維持 (手動でオフにするかアプリケーションプロセスが終了するまで) _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `改善` AutoJs6 と VSCode プラグイン間の双方向バージョン検出を実装し異常が検出された場合に警告_[`issue #89`](http://issues.autojs6.com/89)_
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
* `新機能` UI モードに console / imagebutton / ratingbar / switch / textclock / togglebutton などのコントロールを追加
* `新機能` UI モードのコントロールに [OmniColor](https://docs.autojs6.com/#/omniTypes?id=omnicolor) タイプの色値をサポート (例: color="orange")
* `新機能` UI モードのコントロールが attr メソッドでの属性設定を完全サポート (例: ui.text.attr('color', 'blue')) 
* `新機能` UI モードのコントロールがブール型の属性値のデフォルト形式をサポート (例: clickable="true" は clickable または isClickable と簡略化可能) 
* `新機能` button コントロールが isColored および isBorderless ブール型属性をサポート
* `新機能` console.resetGlobalLogConfig メソッドがグローバルログ設定をリセット
* `新機能` web.newWebSocket メソッドが Web Socket インスタンスの作成をサポート (プロジェクトドキュメント > [万維网](https://docs.autojs6.com/#/web?id=m-newwebsocket) を参照) 
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
* `依存関係` Androidx WebKit バージョン 1.6.1 -> 1.7.0 にアップグレード
* `依存関係` OkHttp3 バージョン 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11 にアップグレード
* `依存関係` MLKit Text Recognition Chinese バージョン 16.0.0-beta6 -> 16.0.0 にアップグレード

# v6.3.0

###### 2023/04/29

* `新機能` ocr モジュール (プロジェクトドキュメントを参照> [光学文字認識](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `新機能` notice モジュール (プロジェクトドキュメントを参照> [メッセージ通知](https://docs.autojs6.com/#/notice))
* `新機能` s13n モジュール (プロジェクトドキュメントを参照> [標準化](https://docs.autojs6.com/#/s13n))
* `新機能` Color モジュール (プロジェクトドキュメントを参照> [カラータイプ](https://docs.autojs6.com/#/colorType))
* `新機能` 前景時に画面を常時点灯させる機能と設定オプション
* `新機能` 独立したアプリ文書を読むための追加のドキュメントランチャー (設定で非表示または表示が可能) 
* `修正` colors.toString メソッドの機能異常
* `修正` app.openUrl メソッドの自動プロトコル接頭辞追加機能異常
* `修正` app.viewFile/editFile 使用時, パラメータの対応ファイルが存在しない場合の動作異常
* `修正` pickup メソッドのコールバック関数が呼び出されない問題
* `修正` レイアウト解析表示のウィジェット情報の bounds 属性値負符号がカンマに置き換えられる問題
* `修正` bounds/boundsInside/boundsContains セレクターが狭義の空矩形 (境界逆位の矩形など) を正常に選別できない _[`issue #49`](http://issues.autojs6.com/49)_
* `修正` テーマの変更や言語の修正後にホームドキュメントタブをクリックまたは長押しするとアプリがクラッシュする問題
* `修正` テキストエディタのピンチズームでフォントサイズを調整するときに発生する可能性のある揺れの問題
* `修正` ビルドスクリプトの一部依存関係のソースがダウンロードできない問題 (すべて統合済み) _[`issue #40`](http://issues.autojs6.com/40)_
* `修正` Tasker が AutoJs6 操作プラグイン (アクションプラグイン) を追加できない問題 (試修) _[`issue #41`](http://issues.autojs6.com/41)_
* `修正` 最新の JDK バージョンでプロジェクトをコンパイルすると, ButterKnife アノテーションがリソース ID を解析できない問題 _[`issue #48`](http://issues.autojs6.com/48)_
* `修正` アクセシビリティサービスの高確率でのサービス異常発生の問題 (試修) 
* `修正` images.medianBlur の size パラメータの使用方法がドキュメントと一致しない問題
* `修正` engines モジュールがスクリプトの全称を表示する際にファイル名と拡張子の間のピリオドが欠落する問題
* `修正` 加重 RGB 距離測定アルゴリズム内部の実装において計算ミスの可能性 (試修) 
* `修正` console モジュールの浮動ウィンドウ関連メソッドが show メソッドの前に使用できない問題
* `修正` console.setSize などのメソッドが機能しない可能性のある問題 _[`issue #50`](http://issues.autojs6.com/50)_
* `修正` colors.material カラースペースの色定数の誤った割り当て
* `修正` UI モードの日付選択ウィジェットの minDate および maxDate 属性が日付フォーマットを正しく解析できない問題
* `修正` スクリプトを実行した後, ホームの「タスク」タブページに素早く切り替えると, 同じ実行中のタスクが 2 つ表示される問題
* `修正` ファイルマネージャのページが他のページから戻るとページの状態がリセットされる可能性のある問題 _[`issue #52`](http://issues.autojs6.com/52)_
* `修正` ファイルマネージャのページソート状態とアイコン表示状態が一致しない問題
* `改善` ファイルマネージャページにファイルおよびフォルダの変更時間の表示を追加
* `改善` ファイルマネージャページのソートタイプが状態記憶をサポート
* `改善` README.md にプロジェクトコンパイルビルドセクションとスクリプト開発補助セクションを追加 _[`issue #33`](http://issues.autojs6.com/33)_
* `改善` images モジュール関連メソッドのリージョン (region) オプションパラメータがより多くの入力方法をサポート (プロジェクトドキュメントを参照> [オムニタイプ](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `改善` app.startActivity ページの省略形パラメータに pref/homepage/docs/about などの形式をサポート
* `改善` web モジュールのグローバルメソッドをモジュール本体にマウントして使いやすく (プロジェクトドキュメントを参照> [ウェブ](https://docs.autojs6.com/#/web))
* `改善` web.newInjectableWebView メソッド内で一部の一般的な WebView 設定オプションをデフォルトで実装
* `改善` colors モジュールに多くの変換メソッドとツールメソッドを追加し, さらに多くの静的定数と直接パラメータとして使用できる色名を追加
* `改善` console モジュールに多くのコンソール浮動ウィンドウのスタイル設定メソッドを追加し, build 構築子を用いてウィンドウスタイルを統一設定
* `改善` コンソール浮動ウィンドウがタイトル部分をドラッグしてウィンドウの位置を移動可能
* `改善` コンソール浮動ウィンドウはスクリプト終了後に自動的に遅延して閉じる
* `改善` コンソール浮動ウィンドウおよびその Activity ウィンドウがピンチズームでフォントサイズ調整をサポート
* `改善` http モジュール関連メソッドがタイムアウトパラメータ (timeout) をサポート
* `改善` Gradle ビルドスクリプトが JDK バージョンの自主降格 (フォールバック) をサポート
* `改善` Gradle ビルドスクリプトがプラットフォームの種類およびバージョンに応じて適切なビルドツールバージョンを自動的に選択 (程度は限定的) 
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
* `依存関係` Androidx WebKit バージョン 1.5.0 -> 1.6.1 にアップグレード
* `依存関係` Androidx Recyclerview バージョン 1.2.1 -> 1.3.0 にアップグレード
* `依存関係` Zip4j バージョン 2.11.2 -> 2.11.5 にアップグレード
* `依存関係` Junit Jupiter バージョン 5.9.2 -> 5.9.3 にアップグレード
* `依存関係` Androidx Annotation バージョン 1.5.0 -> 1.6.0 にアップグレード
* `依存関係` Jackson DataBind バージョン 2.14.1 -> 2.14.2 にアップグレード
* `依存関係` Desugar JDK Libs バージョン 2.0.0 -> 2.0.3 にアップグレード

# v6.2.0

###### 2023/01/21

* `新機能` プロジェクトドキュメントの再設計および再執筆 (一部完了)
* `新機能` 西/法/露/阿/日/韓/英/繁中など多言語対応
* `新機能` 作業パス設定オプションにパス選択/履歴/デフォルト値のインテリジェントサジェスト機能を追加
* `新機能` ファイルマネージャーが任意のディレクトリの上位階層にジャンプをサポート ("内部ストレージ" ディレクトリまで)
* `新機能` ファイルマネージャーが任意のディレクトリを作業パスとして設定することが可能
* `新機能` バージョン更新無視および管理された無視更新機能
* `新機能` テキストエディタがピンチズームでフォントサイズ調整をサポート
* `新機能` idHex セレクタ (UiSelector#idHex) (プロジェクトドキュメントを参照 > [セレクタ](https://docs.autojs6.com/#/uiSelectorType))
* `新機能` action セレクタ (UiSelector#action) (プロジェクトドキュメントを参照 > [セレクタ](https://docs.autojs6.com/#/uiSelectorType))
* `新機能` Match シリーズセレクタ (UiSelector#xxxMatch) (プロジェクトドキュメントを参照 > [セレクタ](https://docs.autojs6.com/#/uiSelectorType))
* `新機能` ピックアップセレクタ (UiSelector#pickup) (プロジェクトドキュメントを参照 > [セレクタ](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `新機能` ウィジェット検出 (UiObject#detect) (プロジェクトドキュメントを参照 > [ウィジェットノード](https://docs.autojs6.com/#/uiObjectType))
* `新機能` ウィジェットコンパス (UiObject#compass) (プロジェクトドキュメントを参照 > [ウィジェットノード](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `新機能` グローバル待機メソッド wait (プロジェクトドキュメントを参照 > [グローバルオブジェクト](https://docs.autojs6.com/#/global?id=m-wait))
* `新機能` グローバルスケーリングメソッド cX/cY/cYx (プロジェクトドキュメントを参照 > [グローバルオブジェクト](https://docs.autojs6.com/#/global?id=m-wait))
* `新機能` グローバル App タイプ (プロジェクトドキュメントを参照 > [アプリケーション列挙型](https://docs.autojs6.com/#/appType))
* `新機能` i18n モジュール (banana-i18n をベースとした JavaScript 多言語ソリューション) (プロジェクトドキュメントを参照 > 国際化)
* `修正` ソフトウェア言語切り替え後にページのテキストがちらついたり, いくつかのページボタンが機能しない問題
* `修正` 作業パスがプロジェクトである場合, ソフトウェア起動後にプロジェクトツールバーが表示されない問題
* `修正` 作業パスがソフトウェア言語切り替えに応じて自動的に変更される可能性の問題 _[`issue #19`](http://issues.autojs6.com/19)_
* `修正` 定時タスク起動遅延が顕著な問題 (試修) _[`issue #21`](http://issues.autojs6.com/21)_
* `修正` JavaScript モジュール名が上書き宣言された場合に依存関係のある内部モジュールが正常に動作しない問題 _[`issue #29`](http://issues.autojs6.com/29)_
* `修正` 高バージョンの Android システムでクイック設定パネルのアイコンをクリックした後, パネルが自動的に閉じない場合がある問題 (試修) _[`issue #7`](http://issues.autojs6.com/7)_
* `修正` 高バージョンの Android システムで一部のページが通知バー領域と重なる問題
* `修正` Android 10 以降のシステムでブラシの色を設定するサンプルコードが正常に動作しない問題
* `修正` サンプルコード「音楽管理者」のファイル名を「ファイル管理者」に訂正し, 正常機能を回復
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
* `修正` Android 7.x システムで夜間モードがオフの際にナビゲーションバーのボタンが識別しにくい問題
* `修正` http.post 等のメソッドで, リクエストが閉じられない場合の問題
* `修正` colors.toString メソッドにおいて, アルファチャンネルが 0 の時にそのチャンネル情報が結果に含まれない問題
* `改善` Auto.js 4.x バージョンの公開クラスをリダイレクトして可能な限り下位互換を実現 (限界あり)
* `改善` すべてのプロジェクトモジュールをマージし, 循環参照などの問題を回避 (暫時 inrt モジュールを削除)
* `改善` Gradle のビルド設定を Groovy から KTS に移行
* `改善` Rhino の例外メッセージに多言語サポートを追加
* `改善` ホームドロワーのパーミッションスイッチは有効時にのみメッセージを表示
* `改善` ホームドロワーレイアウトがステータスバーの下に配置され, 上部のカラーバーの低互換性を回避
* `改善` 更新チェック/更新ダウンロード/更新通知機能が Android 7.x システムに対応
* `改善` 設定ページを再設計 (AndroidX に移行)
* `改善` 設定ページで設定項目を長押しして詳細情報を取得することが可能
* `改善` 夜間モードに「システムに従う」設定オプションを追加 (Android 9 および以上)
* `改善` アプリ起動画面が夜間モードに適応
* `改善` アプリアイコンに番号付けを追加して複数のオープンソースバージョンを共存するユーザーの使用体験を向上
* `改善` テーマカラーにより多くの Material Design Color (マテリアルデザインカラー) オプションを追加
* `改善` ファイルマネージャー/タスクパネルなどのリストアイテムのアイコンを適度に軽量化し, テーマカラーに適応
* `改善` ホームの検索バーのプレースホルダーテキストの色を夜間モードに適応
* `改善` ダイアログ/テキスト/Fab/AppBar/リストアイテムなどのコンポーネントを夜間モードに適応
* `改善` ドキュメント/設定/アバウト/テーマカラー/レイアウト分析などのページおよび Floating ボタンメニューを夜間モードに適応
* `改善` ページレイアウトが可能な限り RTL (Right-To-Left) レイアウトに対応
* `改善` About ページにアイコンアニメーション効果を追加
* `改善` About ページの著作権声明テキストが自動的に年次情報を更新
* `改善` アプリ初回インストール後, 適切な作業ディレクトリを自動的に決定および設定
* `改善` ドキュメントページのピンチズーム機能を無効化し, ドキュメント内容の表示不具合を回避
* `改善` タスクパネルリストアイテムを相対パスで簡略化し, タスクの名前およびパスを表示
* `改善` テキストエディタのボタンテキストを適度に短縮し, テキスト内容の溢れを避ける
* `改善` テキストエディタのフォントサイズ設定でデフォルト値に戻すことが可能
* `改善` Floating ボタンのクリック反応速度を向上
* `改善` Floating ボタンのレイアウト分析ボタンをクリックして直接レイアウト範囲分析を実行
* `改善` レイアウト分析のテーマが自動適応 (Floating ウィンドウがアプリのテーマに従い, クイック設定パネルがシステムのテーマに従う)
* `改善` レイアウトウィジェット情報リストを使用頻度に基づいて再編成
* `改善` レイアウトウィジェット情報をクリックしてコピーする際に, セレクタの種類に応じて出力フォーマットを自動的に最適化
* `改善` Floating ウィンドウでファイルを選択する際に, 戻るキーを押して上位ディレクトリに戻ることができ, ウィンドウを直接閉じない
* `改善` クライアントモードでコンピュータのアドレスを入力する際に, 数値の有効性チェックおよびピリオドシンボルの自動変換をサポート
* `改善` クライアントおよびサーバーの接続後にホームドロワーに対応するデバイスの IP アドレスを表示
* `改善` 一部のグローバルオブジェクトおよび組み込みモジュールにオーバーライドプロテクションを追加 (プロジェクトドキュメントを参照 > グローバルオブジェクト > [オーバーライドプロテクション](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `改善` importClass および importPackage が文字列パラメータおよび可変長パラメータをサポート
* `改善` ui.run が例外発生時にスタックトレース情報を出力サポート
* `改善` ui.R および auto.R が AutoJs6 のリソース ID を簡単に取得可能
* `改善` app モジュール内でアプリに関連する操作メソッドが App タイプパラメータおよびアプリのエイリアスパラメータをサポート
* `改善` dialogs モジュール内の非同期コールバック関連メソッドが事前入力パラメータの省略をサポート
* `改善` app.startActivity などが url オプションパラメータをサポート (参照: サンプルコード > アプリケーション > インテント)
* `改善` device モジュールが IMEI またはハードウェアシリアル番号の取得に失敗した場合, null を返し, 例外をスローしない
* `改善` console.show が表示するログの Floating ウィンドウの文字の明るさを向上させ, 内容の識別度を高める
* `改善` ImageWrapper#saveTo が相対パスで画像ファイルを保存サポート
* `改善` colors グローバルオブジェクトを再設計し, HSV/HSL などのカラーモードをサポート (プロジェクトドキュメントを参照 > [カラー](https://docs.autojs6.com/#/color))
* `依存関係` Gradle Compile バージョンを 32 -> 33 にアップグレード
* `依存関係` ローカライズされた Android Job バージョン 1.4.3
* `依存関係` ローカライズされた Android Plugin Client SDK For Locale バージョン 9.0.0
* `依存関係` ローカライズされた GitHub API バージョン 1.306
* `依存関係` 追加された JCIP Annotations バージョン 1.0
* `依存関係` 追加された Androidx WebKit バージョン 1.5.0
* `依存関係` 追加された Commons IO バージョン 2.8.0
* `依存関係` 追加された Desugar JDK Libs バージョン 2.0.0
* `依存関係` 追加された Jackson DataBind バージョン 2.13.3
* `依存関係` 追加された Jaredrummler Android Device Names バージョン 2.1.0
* `依存関係` 追加された Jaredrummler Animated SVG View バージョン 1.0.6
* `依存関係` Jrummyapps ColorPicker バージョン 2.1.7 を Jaredrummler ColorPicker バージョン 1.1.0 に置き換え
* `依存関係` Gradle バージョンを 7.5-rc-1 -> 8.0-rc-1 にアップグレード
* `依存関係` Gradle ビルドツールバージョンを 7.4.0-alpha02 -> 8.0.0-alpha09 にアップグレード
* `依存関係` Kotlin Gradle プラグインバージョンを 1.6.10 -> 1.8.0-RC2 にアップグレード
* `依存関係` Android Material バージョンを 1.6.0 -> 1.7.0 にアップグレード
* `依存関係` Androidx Annotation バージョンを 1.3.0 -> 1.5.0 にアップグレード
* `依存関係` Androidx AppCompat バージョンを 1.4.1 -> 1.4.2 にアップグレード
* `依存関係` Android Analytics バージョンを 13.3.0 -> 14.0.0 にアップグレード
* `依存関係` Gson バージョンを 2.9.0 -> 2.10 にアップグレード
* `依存関係` Joda Time バージョンを 2.10.14 -> 2.12.1 にアップグレード
* `依存関係` Kotlinx Coroutines バージョンを 1.6.1-native-mt -> 1.6.1 にアップグレード
* `依存関係` OkHttp3 バージョンを 3.10.0 -> 5.0.0-alpha.7 -> 5.0.0-alpha.9 にアップグレード
* `依存関係` Zip4j バージョンを 2.10.0 -> 2.11.2 にアップグレード
* `依存関係` Glide バージョンを 4.13.2 -> 4.14.2 にアップグレード
* `依存関係` Junit Jupiter バージョンを 5.9.0 -> 5.9.1 にアップグレード

# v6.1.1

###### 2022/05/31

* `新機能` 更新チェック/更新ダウンロード/更新通知機能 (設定ページを参照) (Android 7.x システムには一時的に対応していません)
* `修正` Android 10 システムでの外部ストレージの読み書きができない問題 _[`issue #17`](http://issues.autojs6.com/17)_
* `修正` エディタページで長押しするとアプリがクラッシュする可能性のある問題 _[`issue #18`](http://issues.autojs6.com/18)_
* `修正` エディタページの長押しメニュー「行を削除」と「行をコピー」の機能が無効になっている問題
* `修正` エディタページのオプションメニューで「貼り付け」機能が欠如している問題
* `改善` 一部の例外メッセージ文字列をリソース化 (en / zh)
* `改善` 保存していない内容のダイアログのボタンレイアウトを調整し, 色分けを追加
* `依存関係` github-api バージョン 1.306 を追加
* `依存関係` retrofit2-rxjava2-adapter バージョン 1.0.0 を adapter-rxjava2 バージョン 2.9.0 に置き換え

# v6.1.0

###### 2022/05/26 - パッケージ名変更, 慎重にアップグレード

* `ヒント` アプリのパッケージ名を org.autojs.autojs6 に変更し, オープンソースの Auto.js アプリパッケージ名との競合を回避
* `新機能` ホーム画面のドロワーに「投影メディア権限」スイッチを追加 (Root / ADB 方法) (スイッチの状態検出は実験的)
* `新機能` ファイルブラウザが隠しファイルとフォルダを表示サポート (設定ページを参照)
* `新機能` 強制 Root チェック機能 (設定ページ及びサンプルコードを参照)
* `新機能` autojs モジュール (サンプルコード > AutoJs6 を参照)
* `新機能` tasks モジュール (サンプルコード > タスクを参照)
* `新機能` console.launch() メソッドでログアクティビティページを起動
* `新機能` util.morseCode ツール (サンプルコード > ツール > モールス信号を参照)
* `新機能` util.versionCodes ツール (サンプルコード > ツール > Android バージョン情報取得を参照)
* `新機能` util.getClass() などのメソッド (サンプルコード > ツール > クラスとクラス名の取得を参照)
* `新機能` timers.setIntervalExt() メソッド (サンプルコード > タイマー > 条件付き周期実行を参照)
* `新機能` colors.toInt() / rgba() などのメソッド (サンプルコード > イメージとカラー > 基本カラー変換を参照)
* `新機能` automator.isServiceRunning() / ensureService() メソッド
* `新機能` automator.lockScreen() などのメソッド (サンプルコード > アクセシビリティサービス > Android 9 の新機能を参照)
* `新機能` automator.headsethook() などのメソッド (サンプルコード > アクセシビリティサービス > Android 11 の新機能を参照)
* `新機能` automator.captureScreen() メソッド (サンプルコード > アクセシビリティサービス > スクリーンショットの取得を参照)
* `新機能` dialogs.build() のオプションパラメータプロパティ animation, linkify など (サンプルコード > ダイアログ > カスタムダイアログを参照)
* `修正` dialogs.build() のオプションパラメータプロパティ inputHint, itemsSelectedIndex などの機能異常
* `修正` JsDialog#on('multi_choice') コールバックパラメータの機能異常
* `修正` UiObject#parent().indexInParent() が常に -1 を返す問題 _[`issue #16`](http://issues.autojs6.com/16)_
* `修正` Promise.resolve() で返される Thenable がスクリプトの終了時に呼び出されない可能性のある問題
* `修正` パッケージ名またはクラス名の可能性のあるスペルミス (boardcast -> broadcast / auojs -> autojs)
* `修正` images.requestScreenCapture() が高バージョンの Android システムでアプリがクラッシュする可能性のある問題 (API >= 31)
* `修正` images.requestScreenCapture() が複数のスクリプトインスタンスを同時に申請するとアプリがクラッシュする可能性のある問題
* `修正` new RootAutomator() を呼び出した際に発生する可能性のあるフリーズ問題
* `改善` RootAutomator は Root 権限がない場合にインスタンス化できません
* `改善` 「アプリおよび開発者について」ページを再設計
* `改善` すべての組み込み JavaScript モジュールを再構築
* `改善` すべての Gradle ビルドスクリプトを再構築し, 共通設定スクリプト (config.gradle) を追加
* `改善` Gradle ビルドツールがバージョン管理とビルドファイルの自動命名をサポート
* `改善` Gradle ビルドツールが task を追加し, CRC32 要約をビルドファイルに添付 (appendDigestToReleasedFiles)
* `改善` shell() コール時に例外を直接スローするのではなく, 結果に例外を書き込みます (try/catch 不要)
* `改善` Rhino 組み込みの JSON を使用, 元の json2 モジュールを置き換え
* `改善` auto.waitFor() がタイムアウトパラメータをサポート
* `改善` threads.start() がアロー関数パラメータをサポート
* `改善` console.trace() がログレベルパラメータをサポート (サンプルコード > コンソール > コールスタックの表示を参照)
* `改善` device.vibrate() がモード振動とモールス振動をサポート (サンプルコード > デバイス > モード振動 / モールス振動を参照)
* `改善` 外部ストレージ読み書き権限が高バージョンの Android システムに適応 (API >= 30)
* `改善` コンソールフォントが Material Color を採用し, 通常およびナイトテーマでのフォントの可読性を向上
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
* `新機能` recorder モジュール (サンプルコード > 計時器を参照)
* `新機能` 「安全設定の変更権限」を使用してアクセシビリティサービスと設定のスイッチを自動的に有効化
* `修正` クイック設定パネルの関連アイコンをクリックした後, パネルが自動的に閉じない問題 (仮修理) _[`issue #7`](http://issues.autojs6.com/7)_
* `修正` toast 使用時に強制表示パラメータを使用すると AutoJs6 がクラッシュする可能性がある問題
* `修正` Socket データヘッダー情報が不完全な場合に AutoJs6 がクラッシュする可能性がある問題
* `改善` AutoJs6 の起動または再起動時にオプション設定に従ってアクセシビリティサービスを自動的に有効化
* `改善` フローティングボタンのスイッチをオンにした際にアクセシビリティサービスを自動的に有効化
* `改善` すべてのリソースファイルに対応する英訳を補完
* `改善` ホーム画面ドロワーのレイアウトを微調整し, プロジェクト間のスペースを減少
* `改善` ホーム画面のドロワーに前景サービスステータスのスイッチの同期を追加
* `改善` ホーム画面のドロワーを展開する際にスイッチの状態を即座に同期
* `改善` ポインタ位置の表示にステータス検出と結果の通知を追加
* `改善` 64 ビットオペレーティングシステムに対応 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `改善` フローティングボタンの初期化時に透明度設定を同時に適用 (クリック後に透明度を適用する必要なし)
* `改善` ファイル内容リセット時にサンプルコードファイルかどうかを検出し, 結果の通知を追加
* `改善` パッケージプラグインのダウンロード先を GitHub から JsDelivr に移行
* `依存関係` Zeugma Solutions LocaleHelper バージョン 1.5.1 を追加
* `依存関係` Android Material バージョン 1.6.0-alpha02 -> 1.5.0 にダウングレード
* `依存関係` Kotlinx Coroutines バージョン 1.6.0-native-mt -> 1.6.0 にアップグレード
* `依存関係` OpenCV バージョン 3.4.3 -> 4.5.4 -> 4.5.5 にアップグレード (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依存関係` OkHttp3 バージョン 3.10.0 -> 5.0.0-alpha.4 -> 5.0.0-alpha.6 にアップグレード
* `依存関係` Gradle ビルドツール バージョン 7.2.0-beta01 -> 7.3.0-alpha06 にアップグレード
* `依存関係` Auto.js-ApkBuilder バージョン 1.0.1 -> 1.0.3 にアップグレード
* `依存関係` Glide Compiler バージョン 4.12.0 -> 4.13.1 にアップグレード
* `依存関係` Gradle リリースバージョン 7.4-rc-2 -> 7.4.1 にアップグレード
* `依存関係` Gradle コンパイルバージョン 31 -> 32 にアップグレード
* `依存関係` Gson バージョン 2.8.9 -> 2.9.0 にアップグレード

# v6.0.2

###### 2022/02/05

* `新機能` images.bilateralFilter() 両側フィルタ画像処理方法
* `修正` 複数回呼び出された toast が最後の呼び出ししか効果がない問題
* `修正` toast.dismiss() が無効になる可能性のある問題
* `修正` クライアントモードおよびサーバーモードのスイッチが正常に動作しない可能性のある問題
* `修正` クライアントモードおよびサーバーモードのスイッチ状態が正常に更新されない問題
* `修正` Android 7.x での UI モードの text 要素解析異常 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `改善` sleep() の ScriptInterruptedException 例外を無視
* `依存関係` 追加 Androidx AppCompat (Legacy) バージョン 1.0.2
* `依存関係` アップグレード Androidx AppCompat バージョン 1.4.0 -> 1.4.1
* `依存関係` アップグレード Androidx Preference バージョン 1.1.1 -> 1.2.0
* `依存関係` アップグレード Rhino エンジンバージョン 1.7.14-SNAPSHOT -> 1.7.14
* `依存関係` アップグレード OkHttp3 バージョン 3.10.0 -> 5.0.0-alpha.3 -> 5.0.0-alpha.4
* `依存関係` アップグレード Android Material バージョン 1.6.0-alpha01 -> 1.6.0-alpha02
* `依存関係` アップグレード Gradle ビルドツールバージョン 7.2.0-alpha06 -> 7.2.0-beta01
* `依存関係` アップグレード Gradle リリースバージョン 7.3.3 -> 7.4-rc-2

# v6.0.1

###### 2022/01/01

* `新機能` VSCode プラグインクライアント (LAN) およびサーバー (LAN/ADB) モードのサポート (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新機能` base64 モジュール (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新機能` isInteger/isNullish/isObject/isPrimitive/isReference グローバルメソッドの追加
* `新機能` polyfill (Object.getOwnPropertyDescriptors) の追加
* `新機能` polyfill (Array.prototype.flat) の追加
* `改善` global.sleep の拡張 ランダム範囲/負数の互換性
* `改善` global.toast の拡張 持続時間/強制上書き制御/dismiss のサポート
* `改善` パッケージ名オブジェクトのグローバル化 (okhttp3/androidx/de)
* `依存関係` アップグレード Android Material バージョン 1.5.0-beta01 -> 1.6.0-alpha01
* `依存関係` アップグレード Gradle ビルドツールバージョン 7.2.0-alpha04 -> 7.2.0-alpha06
* `依存関係` アップグレード Kotlinx Coroutines バージョン 1.5.2-native-mt -> 1.6.0-native-mt
* `依存関係` アップグレード Kotlin Gradle プラグインバージョン 1.6.0 -> 1.6.10
* `依存関係` アップグレード Gradle リリースバージョン 7.3 -> 7.3.3

# v6.0.0

###### 2021/12/01

* `新機能` ホーム画面のドロワー下部にアプリ再起動ボタンを追加
* `新機能` ホーム画面のドロワーにバッテリー最適化無視/他のアプリの上に表示するスイッチを追加
* `修正` アプリの初回インストール後, 一部の領域でテーマカラーのレンダリング異常問題
* `修正` sign.property ファイルが存在しない場合のプロジェクトビルド問題
* `修正` スケジュールタスクパネルの一回限りのタスクの月表示エラー
* `修正` アプリの設定ページのスイッチカラーがテーマ変更に応じて変わらない問題
* `修正` パッケージプラグインが認識されないおよびダウンロードアドレスが無効な問題
* `修正` ホーム画面ドロワー 「使用状況アクセス権限を見る」スイッチの状態が同期されない問題
* `修正` TemplateMatching.fastTemplateMatching 潜在的な Mat メモリリーク問題
* `改善` アップグレード Rhino エンジンバージョン 1.7.7.2 -> 1.7.13 -> 1.7.14-SNAPSHOT
* `改善` アップグレード OpenCV バージョン 3.4.3 -> 4.5.4
* `改善` ViewUtil.getStatusBarHeight の互換性の向上
* `改善` ホーム画面ドロワーからユーザーログイン関連モジュールおよびレイアウトプレースホルダーを削除
* `改善` ホーム画面からコミュニティおよびマーケットタブを削除し, レイアウトアライメントを最適化
* `改善` いくつかの設定オプションのデフォルトスイッチ状態を変更
* `改善` 「アプリについて」ページに SinceDate を追加し, Copyright の表示を最適化
* `改善` JSON モジュールを 2017-06-12 バージョンにアップグレードし, cycle.js を統合
* `改善` アクティビティがフォアグラウンドになるときの自動アップデートチェック機能および関連ボタンの削除
* `改善` AppOpsKt#isOpPermissionGranted 内部コードロジック
* `改善` ResourceMonitor の安全性向上のために ReentrantLock を使用 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `改善` Maven Central などのリポジトリを使用して JCenter リポジトリを置き換え
* `改善` 重複したローカルライブラリファイルの抽出および削除
* `依存関係` ローカライズ CrashReport バージョン 2.6.6
* `依存関係` ローカライズ MutableTheme バージョン 1.0.0
* `依存関係` 追加 Androidx Preference バージョン 1.1.1
* `依存関係` 追加 SwipeRefreshLayout バージョン 1.1.0
* `依存関係` アップグレード Android Analytics バージョン 7.0.0 -> 13.1.0
* `依存関係` アップグレード Android Annotations バージョン 4.5.2 -> 4.8.0
* `依存関係` アップグレード Gradle ビルドツールバージョン 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `依存関係` アップグレード Android Job バージョン 1.2.6 -> 1.4.2
* `依存関係` アップグレード Android Material バージョン 1.1.0-alpha01 -> 1.5.0-beta01
* `依存関係` アップグレード Androidx MultiDex バージョン 2.0.0 -> 2.0.1
* `依存関係` アップグレード Apache Commons Lang3 バージョン 3.6 -> 3.12.0
* `依存関係` アップグレード Appcompat バージョン 1.0.2 -> 1.4.0
* `依存関係` アップグレード ButterKnife Gradle プラグインバージョン 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `依存関係` アップグレード ColorPicker バージョン 2.1.5 -> 2.1.7
* `依存関係` アップグレード Espresso Core バージョン 3.1.1-alpha01 -> 3.5.0-alpha03
* `依存関係` アップグレード Eventbus バージョン 3.0.0 -> 3.2.0
* `依存関係` アップグレード Glide Compiler バージョン 4.8.0 -> 4.12.0 -> 4.12.0
* `依存関係` アップグレード Gradle Build Tool バージョン 29.0.2 -> 30.0.2
* `依存関係` アップグレード Gradle Compile バージョン 28 -> 30 -> 31
* `依存関係` アップグレード Gradle リリースバージョン 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `依存関係` アップグレード Groovy-Json プラグインバージョン 3.0.7 -> 3.0.8
* `依存関係` アップグレード Gson バージョン 2.8.2 -> 2.8.9
* `依存関係` アップグレード JavaVersion バージョン 1.8 -> 11 -> 16
* `依存関係` アップグレード Joda Time バージョン 2.9.9 -> 2.10.13
* `依存関係` アップグレード Junit バージョン 4.12 -> 4.13.2
* `依存関係` アップグレード Kotlin Gradle プラグインバージョン 1.3.10 -> 1.4.10 -> 1.6.0
* `依存関係` アップグレード Kotlinx Coroutines バージョン 1.0.1 -> 1.5.2-native-mt
* `依存関係` アップグレード LeakCanary バージョン 1.6.1 -> 2.7
* `依存関係` アップグレード LicensesDialog バージョン 1.8.1 -> 2.2.0
* `依存関係` アップグレード Material Dialogs バージョン 0.9.2.3 -> 0.9.6.0
* `依存関係` アップグレード OkHttp3 バージョン 3.10.0 -> 5.0.0-alpha.2 -> 5.0.0-alpha.3
* `依存関係` アップグレード Reactivex RxJava2 RxAndroid バージョン 2.0.1 -> 2.1.1
* `依存関係` アップグレード Reactivex RxJava2 バージョン 2.1.2 -> 2.2.21
* `依存関係` アップグレード Retrofit2 Converter Gson バージョン 2.3.0 -> 2.9.0
* `依存関係` アップグレード Retrofit2 Retrofit バージョン 2.3.0 -> 2.9.0
* `依存関係` アップグレード Zip4j バージョン 1.3.2 -> 2.9.1