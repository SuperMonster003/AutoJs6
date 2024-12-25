******

### 版本歷史

******

# v6.6.1

###### 2024/12/25

* `新增` pinyin 模組, 用於漢語拼音轉換 (參閱 專案文件 > [漢語拼音](https://docs.autojs6.com/#/pinyin))
* `新增` pinyin4j 模組, 用於漢語拼音轉換 (參閱 專案文件 > [漢語拼音](https://docs.autojs6.com/#/pinyin4j))
* `新增` UiObject#isSimilar 及 UiObjectCollection#isSimilar 方法, 用於確定控制元件或控制元件集合是否相似
* `修復` 部分環境因回退版本過低而無法正常編譯專案的問題
* `修復` 呼叫不存在的方法時可能出現的 "非原始型別值" 異常
* `修復` 部分裝置無法正常新增指令碼快捷方式的問題 (試修) _[`issue #221`](http://issues.autojs6.com/221)_
* `修復` automator.click/longClick 方法引數型別限制錯誤 _[`issue #275`](http://issues.autojs6.com/275)_
* `修復` 選擇器不支援 ConsString 型別引數的問題 _[`issue #277`](http://issues.autojs6.com/277)_
* `修復` UiObjectCollection 例項缺失自身方法及屬性的問題
* `最佳化` 恢復日誌活動視窗單個條目文字內容的雙擊或長按選擇功能 _[`issue #280`](http://issues.autojs6.com/280)_
* `最佳化` 指令碼專案識別在 project.json 損壞情況下儘可能還原關鍵資訊
* `最佳化` 打包單檔案時自動生成的包名字尾支援將簡體中文轉換為拼音 (支援多音字)
* `最佳化` UiSelector#findOnce 及 UiSelector#find 方法支援負數引數
* `最佳化` UI 元素及 className 相關選擇器支援更多的包名字首省略形式 (如 RecyclerView, Snackbar 等)
* `依賴` 附加 Jieba Analysis 版本 1.0.3-SNAPSHOT (modified)
* `依賴` 升級 Gradle 版本 8.11.1 -> 8.12

# v6.6.0

###### 2024/12/02 - 內建模組重寫, 謹慎升級

* `提示` 內建模組使用 Kotlin 重新編寫以提升指令碼執行效率但可能需要多次迭代逐步完善
* `提示` 內建 init.js 檔案預設為空但支援開發者自行擴充套件內建模組或掛載外部模組
* `新增` axios 模組 / cheerio 模組 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX))
* `新增` sqlite 模組, 用於 SQLite 資料庫簡單操作 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (參閱 專案文件 > [SQLite](https://docs.autojs6.com/#/sqlite))
* `新增` mime 模組, 用於處理和解析 MIME 型別字串 (參閱 專案文件 > [MIME](https://docs.autojs6.com/#/mime))
* `新增` nanoid 模組, 可作為字串 ID 生成器 (Ref to [ai/nanoid](https://github.com/ai/nanoid))
* `新增` sysprops 模組, 用於獲取執行時環境配置資料 (參閱 專案文件 > [系統屬性](https://docs.autojs6.com/#/sysprops))
* `新增` ocr 模組支援 [Rapid OCR](https://github.com/RapidAI/RapidOCR) 引擎
* `新增` 佈局分析支援切換視窗 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` auto.clearCache 方法, 支援清除控制元件快取 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` threads.pool 方法, 支援執行緒池簡單應用 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.matchTemplate 方法增加 useTransparentMask 選項引數, 支援透明找圖 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.requestScreenCaptureAsync 方法, 用於 UI 模式非同步方式申請截圖許可權 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.requestScreenCapture 方法增加 isAsync 選項引數, 支援非同步方式獲取螢幕截圖 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.on('screen_capture', callback) 等事件監聽方法, 支援監聽螢幕截圖可用事件 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.stopScreenCapture 方法, 支援主動釋放截圖申請的相關資源 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.psnr/mse/ssim/mssim/hist/ncc 及 images.getSimilarity 方法, 用於獲取影象相似性度量值
* `新增` images.isGrayscale 方法, 用於判斷影象是否為灰度影象
* `新增` images.invert 方法, 用於反色影象轉換
* `新增` s13n.point/time 方法, 用於標準化點物件及時長物件 (參閱 專案文件 > [標準化](https://docs.autojs6.com/#/s13n))
* `新增` console 模組 gravity (重力), touchThrough (穿透點選), backgroundTint (背景著色) 等相關方法 (參閱 專案文件 > [控制檯](https://docs.autojs6.com/#/console))
* `新增` Mathx.randomInt/Mathx.randomFloat 方法, 用於返回指定範圍內的隨機整數或隨機浮點數
* `新增` app.launchDual/startDualActivity 等方法, 用於處理雙開應用 (需要 Shizuku 或 Root 許可權) (實驗性)
* `新增` app.kill 方法, 用於強制停止應用 (需要 Shizuku 或 Root 許可權)
* `新增` floaty.getClip 方法, 用於藉助浮動視窗聚焦間接獲取剪下板內容
* `修復` Fragment 子類 (如 [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48) 等) 中存在的 View Binding 記憶體洩漏
* `修復` [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21) 等類中存在的例項記憶體洩漏
* `修復` Android 14+ 申請截圖許可權導致應用崩潰的問題 (by [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `修復` Android 14+ 開啟前臺服務導致應用崩潰的問題
* `修復` Android 14+ 程式碼編輯器執行按鈕點選後無法正常亮起的問題
* `修復` 專案打包後應用可能因缺少必要庫檔案無法正常執行的問題 _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `修復` 編輯專案時可能因指定圖示資源不存在而導致應用崩潰的問題 _[`issue #203`](http://issues.autojs6.com/203)_
* `修復` 截圖許可權申請時無法正常使用引數獲取指定螢幕方向的截圖資源
* `修復` 部分裝置無法正常新增指令碼快捷方式的問題 (試修) _[`issue #221`](http://issues.autojs6.com/221)_
* `修復` 呼叫 http 模組與傳送請求相關的方法將出現累積性請求傳送延遲的問題 _[`issue #192`](http://issues.autojs6.com/192)_
* `修復` Shizuku 服務在 AutoJs6 進入主活動頁面之前可能無法正常使用的問題 (試修) _[`issue #255`](http://issues.autojs6.com/255)_
* `修復` random(min, max) 方法可能出現結果越界的問題
* `修復` pickup 方法結果型別引數無法正常傳入空陣列的問題
* `修復` UiObject#bounds() 得到的控制元件矩形可能被意外修改而破壞其不變性的問題
* `修復` text/button/input 元素的文字內容包含半形雙引號時無法正常解析的問題
* `修復` text/textswitcher 元素的 autoLink 屬性功能失效的問題
* `修復` 不同指令碼可能錯誤地共享同一個 ScriptRuntime 物件的問題
* `修復` 全域性變數 HEIGHT 及 WIDTH 丟失 Getter 動態屬性的問題
* `修復` 指令碼啟動時 RootShell 隨即載入可能導致啟動高延遲的問題
* `修復` 控制檯浮動視窗設定背景顏色導致矩形圓角樣式丟失的問題
* `修復` 無障礙服務自動啟動可能出現的服務異常問題 (試修)
* `修復` 主頁文件頁面左右滑動 WebView 控制元件時可能觸發 ViewPager 切換的問題
* `修復` 檔案管理器無法識別包含大寫字母副檔名的問題
* `修復` 檔案管理器首次進入專案目錄時可能無法自動識別專案的問題
* `修復` 檔案管理器刪除資料夾後頁面無法自動重新整理的問題
* `修復` 檔案管理器排序檔案及資料夾時可能出現 ASCII 首字母名稱置後的問題
* `修復` 程式碼編輯器除錯功能的 FAILED ASSERTION 異常
* `修復` 程式碼編輯器除錯過程中關閉編輯器後無法再次正常除錯的問題
* `修復` 程式碼編輯器跳轉到行尾時可能遺漏末尾字元的問題
* `修復` 主活動頁面啟動日誌活動頁面時可能出現閃屏的問題
* `修復` 打包應用無法正常使用 opencc 模組的問題
* `最佳化` 打包頁面中 "不可用 ABI" 控制元件的點選提示體驗
* `最佳化` 支援使用 Shizuku 控制 "指標位置" 顯示開關
* `最佳化` 支援使用 Shizuku 控制 "投影媒體" 及 "修改安全設定" 許可權開關
* `最佳化` automator.gestureAsync/gesturesAsync 支援回撥函式引數
* `最佳化` tasks 模組使用同步方式進行資料庫操作避免可能的資料訪問不一致問題
* `最佳化` 指令碼執行模式支援管道符號分隔模式引數 (如 `"ui|auto";` 開頭)
* `最佳化` 指令碼執行模式支援單引號及反引號且支援省略分號 (如 `'ui';` 或 `'ui'` 開頭)
* `最佳化` 指令碼執行模式支援 axios, cheerio, dayjs 等模式引數快捷匯入內建擴充套件模組 (如 `"axios";` 開頭)
* `最佳化` 指令碼執行模式支援 x 或 jsox 模式引數快捷啟用 JavaScript 內建物件擴充套件模組 (如 `"x";` 開頭)
* `最佳化` img 元素 src 及 path 屬性支援本地相對路徑 (如 `<img src="a.png"` />)
* `最佳化` 程式碼編輯器匯入 Java 類和包名時支援智慧判斷插入位置
* `最佳化` images 模組支援直接使用路徑作為影象引數
* `最佳化` importPackage 支援字串引數
* `最佳化` 服務端模式 IP 地址支援剪下板匯入智慧識別且支援空格按鍵智慧轉換
* `最佳化` 檔案管理器新建檔案時支援預設字首選擇並自動生成合適的數字字尾
* `最佳化` 檔案管理器執行專案時具體化異常訊息提示 _[`issue #268`](http://issues.autojs6.com/268)_
* `最佳化` 檔案管理器支援更多型別並支援顯示對應的圖示符號 (支援 800 多種檔案型別)
* `最佳化` 檔案管理器可編輯的檔案型別 (jpg/doc/pdf...) 增加編輯按鈕
* `最佳化` 檔案管理器 APK 檔案支援檢視基礎資訊, Manifest 資訊及許可權列表
* `最佳化` 檔案管理器音影片等媒體檔案支援檢視基礎資訊及 MediaInfo 資訊
* `最佳化` 打包單檔案時支援自動填入合適的標準化名包並支援無效字元過濾提示
* `最佳化` 打包單檔案時支援根據已安裝同包名應用自動設定圖示並自增版本號及版本名稱
* `最佳化` 打包專案配置檔案支援 abis/libs 選項指定預設包含的 ABI 架構及擴充套件庫
* `最佳化` 打包專案配置檔案 abis/libs 選項無效或無可用時支援相關訊息提示
* `最佳化` LeakCanary 在正式發行版本中被排除以避免增加不必要性
* `最佳化` 專案原始碼所有英文註釋增加簡體中文翻譯以增強註釋可讀性
* `最佳化` README 及 CHANGELOG 支援多語言 (由指令碼自動生成)
* `最佳化` Gradle 構建指令碼提升版本自適應能力
* `依賴` 附加 MIME Util 版本 2.3.1
* `依賴` 附加 Toaster 版本 12.6
* `依賴` 附加 EasyWindow (for Toaster) 版本 10.3
* `依賴` 升級 Gradle 版本 8.5 -> 8.11.1
* `依賴` 升級 Rhino 版本 1.7.15-snapshot -> 1.7.16-snapshot
* `依賴` 升級 Android Material Lang3 版本 1.10.0 -> 1.12.0
* `依賴` 升級 Androidx Annotation 版本 1.7.0 -> 1.9.1
* `依賴` 升級 Androidx AppCompat 版本 1.6.1 -> 1.7.0
* `依賴` 升級 Androidx WebKit 版本 1.8.0 -> 1.12.1
* `依賴` 升級 Apache Commons 版本 3.13.0 -> 3.16.0
* `依賴` 升級 ARSCLib 版本 1.2.4 -> 1.3.1
* `依賴` 升級 Gson 版本 2.10.1 -> 2.11.0
* `依賴` 升級 Jackson DataBind 版本 2.13.3 -> 2.13.4.2
* `依賴` 升級 Joda Time 版本 2.12.5 -> 2.12.7
* `依賴` 升級 LeakCanary 版本 2.12 -> 2.14
* `依賴` 升級 MLKit Barcode Scanning 版本 17.2.0 -> 17.3.0
* `依賴` 升級 MLKit Text Recognition Chinese 版本 16.0.0 -> 16.0.1
* `依賴` 升級 Retrofit2 Converter Gson 版本 2.9.0 -> 2.11.0
* `依賴` 升級 Retrofit2 Retrofit 版本 2.9.0 -> 2.11.0
* `依賴` 升級 Desugar JDK Libs 版本 2.0.3 -> 2.0.4
* `依賴` 升級 Test Runner 版本 1.5.2 -> 1.6.2
* `依賴` 升級 Junit Jupiter 版本 5.10.0 -> 5.10.3
* `依賴` 降級 OkHttp3 版本 5.0.0-alpha.11 -> 4.12.0

# v6.5.0

###### 2023/12/02

* `新增` opencc 模組 (參閱 專案文件 > [中文轉換](https://docs.autojs6.com/#/opencc)) (Ref to [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `新增` UiSelector 增加 [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) 及 [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) 方法 _[`issue #115`](http://issues.autojs6.com/115)_
* `新增` 打包應用頁面增加 ABI 及庫的篩選支援 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `修復` 打包應用檔案體積異常龐大的問題 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `修復` 打包應用無法顯示並列印部分異常訊息的問題
* `修復` 打包應用頁面選擇應用圖示後可能顯示空圖示的問題
* `修復` 打包應用包含 MLKit Google OCR 庫時可能出現的上下文未初始化異常
* `修復` ocr.<u>mlkit/ocr</u>.<u>recognizeText/detect</u> 方法無效的問題
* `修復` 部分文字 (如日誌頁面) 顯示語言與應用設定語言可能不相符的問題
* `修復` 部分語言在主頁抽屜開關項可能出現文字溢位的問題
* `修復` 部分裝置無障礙服務開啟後立即自動關閉且無任何提示訊息的問題 _[`issue #181`](http://issues.autojs6.com/181)_
* `修復` 部分裝置無障礙服務開啟後設備物理按鍵可能導致應用崩潰的問題 (試修) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `修復` 使用 auto(true) 重啟無障礙服務後 pickup 功能異常的問題 (試修) _[`issue #184`](http://issues.autojs6.com/184)_
* `修復` floaty 模組建立浮動視窗拖動時可能導致應用崩潰的問題 (試修)
* `修復` app.startActivity 無法使用簡稱引數的問題 _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `修復` importClass 匯入的類名與全域性變數衝突時程式碼丟擲異常的問題 _[`issue #185`](http://issues.autojs6.com/185)_
* `修復` Android 7.x 無法使用無障礙服務的問題
* `修復` Android 14 可能無法正常使用 runtime.<u>loadJar/loadDex</u> 方法的問題 (試修)
* `修復` 安卓系統快速設定面板中 "佈局範圍分析" 和 "佈局層次分析" 不可用的問題 _[`issue #193`](http://issues.autojs6.com/193)_
* `修復` 自動檢查更新功能可能導致應用 [ANR](https://developer.android.com/topic/performance/vitals/anr) 的問題 (試修) _[`issue #186`](http://issues.autojs6.com/186)_
* `修復` 檔案管理器示例程式碼資料夾點選 "向上" 按鈕後無法回到工作路徑頁面的問題 _[`issue #129`](http://issues.autojs6.com/129)_
* `修復` 程式碼編輯器使用替換功能時替換按鈕無法顯示的問題
* `修復` 程式碼編輯器長按刪除時可能導致應用崩潰的問題 (試修)
* `修復` 程式碼編輯器點選 fx 按鈕無法顯示模組函式快捷面板的問題
* `修復` 程式碼編輯器模組函式快捷面板按鈕函式名稱可能溢位的問題
* `最佳化` 程式碼編輯器模組函式快捷面板適配夜間模式
* `最佳化` 打包應用啟動頁面適配夜間模式並調整應用圖示佈局
* `最佳化` 打包應用頁面支援使用軟鍵盤 ENTER 鍵實現游標跳轉
* `最佳化` 打包應用頁面支援點選 ABI 標題及庫標題切換全選狀態
* `最佳化` 打包應用頁面預設 ABI 智慧選擇並增加不可選擇項的引導提示
* `最佳化` 檔案管理器根據檔案及資料夾的型別及特徵調整選單項的顯示情況
* `最佳化` 檔案管理器資料夾右鍵選單增加打包應用選項
* `最佳化` 無障礙服務啟用但功能異常時在 AutoJs6 主頁抽屜開關處將體現異常狀態
* `最佳化` 控制檯列印錯誤訊息時附加詳細的堆疊資訊
* `依賴` 附加 ARSCLib 版本 1.2.4
* `依賴` 附加 Flexbox 版本 3.0.0
* `依賴` 附加 Android OpenCC 版本 1.2.0
* `依賴` 升級 Gradle 版本 8.5-rc-1 -> 8.5

# v6.4.2

###### 2023/11/15

* `新增` dialogs.build() 選項引數屬性 inputSingleLine
* `新增` console.setTouchable 方法 _[`issue #122`](http://issues.autojs6.com/122)_
* `修復` ocr 模組部分方法無法識別區域引數的問題 _[`issue #162`](http://issues.autojs6.com/162)_  _[`issue #175`](http://issues.autojs6.com/175)_
* `修復` Android 7.x 發現新版本時無法獲取版本詳情的問題
* `修復` Android 14 申請截圖許可權時導致應用崩潰的問題
* `修復` 主頁抽屜快速切換 "浮動按鈕" 開關時可能導致應用崩潰的問題
* `修復` 使用選單關閉浮動按鈕時重啟應用後浮動按鈕可能依然顯示的問題
* `修復` Android 13 及以上系統設定頁面選擇並切換 AutoJs6 語言後無法生效的問題
* `修復` 構建工具初次構建時無法自動完成 OpenCV 資源部署的問題
* `最佳化` 原生化 bridges 模組以提升指令碼執行效率 (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7c41af6d2b9b36d00440a9c8b7e971d025f98327))
* `最佳化` 重構無障礙服務相關程式碼以增強無障礙服務的功能穩定性 (實驗性) _[`issue #167`](http://issues.autojs6.com/167)_
* `最佳化` UiObject 和 UiObjectCollection 的列印輸出格式
* `最佳化` 構建工具在構建環境 Gradle JDK 版本不滿足要求時作出升級提示
* `依賴` 升級 Gradle 版本 8.4 -> 8.5-rc-1
* `依賴` 降級 Commons IO 版本 2.14.0 -> 2.8.0
* `依賴` 降級 Jackson DataBind 版本 2.14.3 -> 2.13.3

# v6.4.1

###### 2023/11/02

* `修復` 構建工具無法自適應未知平臺的問題 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `修復` 指令碼退出時可能導致應用崩潰的問題 _[`issue #159`](http://issues.autojs6.com/159)_
* `修復` http 模組獲取響應物件的 body.contentType 返回值型別錯誤 _[`issue #142`](http://issues.autojs6.com/142)_
* `修復` device.width 及 device.height 返回資料不正確的問題 _[`issue #160`](http://issues.autojs6.com/160)_
* `修復` 程式碼編輯器長按刪除時可能導致應用崩潰的問題 (試修) _[`issue #156`](http://issues.autojs6.com/156)_
* `修復` 程式碼編輯器反向選擇文字後進行常規操作可能導致應用崩潰的問題
* `修復` 部分裝置長按 AutoJs6 應用圖示無法顯示快捷方式選單的問題
* `修復` 部分裝置打包專案時點選確認按鈕無響應的問題
* `修復` app.sendBroadcast 及 app.startActivity 無法使用簡稱引數的問題
* `修復` floaty 模組 JsWindow#setPosition 等方法首次呼叫時的功能異常
* `最佳化` 增加 Termux 相關許可權以支援 Intent 呼叫 Termux 執行 ADB 命令 _[`issue #136`](http://issues.autojs6.com/136)_
* `最佳化` http 模組獲取的響應物件可重複使用 body.string() 及 body.bytes() 方法
* `最佳化` 增加 GitHub Actions 自動打包支援 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `最佳化` 構建工具自適應 Temurin 平臺
* `依賴` 升級 Gradle 版本 8.4-rc-3 -> 8.4
* `依賴` 升級 Android dx 版本 1.11 -> 1.14

# v6.4.0

###### 2023/10/30

* `新增` ocr 模組支援 Paddle Lite 引擎 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `新增` 打包功能支援內建外掛與外部外掛兩種打包方式 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `新增` WebSocket 模組 (參閱 專案檔案 > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `新增` barcode / qrcode 模組 (參閱 專案檔案 > [條碼](https://docs.autojs6.com/#/barcode) / [二維碼](https://docs.autojs6.com/#/qrcode))
* `新增` shizuku 模組 (參閱 專案檔案 > [Shizuku](https://docs.autojs6.com/#/shizuku)) 及主頁抽屜許可權開關
* `新增` device.rotation / device.orientation 等方法
* `新增` 內部 Java 類支援 class 靜態屬性訪問
* `新增` 支援在安卓系統設定頁面選擇並切換應用語言 ( Android 13 及以上)
* `新增` 支援設定頁面新增或長按應用圖示啟用 [應用快捷方式](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn) , 可啟動檔案和設定等頁面
* `修復` 重新合併部分 PR (by [aiselp](https://github.com/aiselp)) 以解決部分指令碼無法正常結束執行的問題 _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `修復` 打包應用無法使用 AutoJs6 新增 API 的問題 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `修復` 打包應用在系統夜間模式下的樣式異常
* `修復` VSCode 外掛儲存檔案到本地時副檔名資訊丟失的問題
* `修復` 使用協程特性執行專案產生未捕獲異常致使應用崩潰的問題
* `修復` 重啟或退出應用時浮動按鈕無法記錄其位置狀態資訊的問題
* `修復` 裝置螢幕方向改變時無法獲取更新後的裝置配置資訊的問題 _[`issue #153`](http://issues.autojs6.com/153)_
* `修復` 螢幕旋轉至橫向時 Toolbar 標題字型過小的問題
* `修復` 螢幕旋轉至橫向時應用主頁的頁籤排版過於擁擠的問題
* `修復` 螢幕旋轉至橫向時浮動按鈕可能溢位螢幕的問題 _[`issue #90`](http://issues.autojs6.com/90)_
* `修復` 螢幕多次旋轉時無法恢復浮動按鈕的座標及螢幕側邊方向的問題
* `修復` 部分裝置訊息浮動框可能出現遺漏顯示或重複顯示的問題
* `修復` 訊息浮動框在多個指令碼同時執行時可能存在被遮蔽的問題 _[`issue #67`](http://issues.autojs6.com/67)_
* `修復` 使用廣播分析佈局時點選佈局無法彈出選單且導致應用崩潰的問題
* `修復` 第二次及以後建立的 WebSocket 例項均無法正常觸發監聽器的問題
* `修復` 撤銷 importPackage 的全域性重定向方法以避免某些作用域下的包匯入異常 _[`issue #88`](http://issues.autojs6.com/88)_
* `修復` 日誌活動頁面使用複製或匯出功能時可能導致應用崩潰的問題
* `最佳化` 日誌活動頁面匯出功能重新命名為傳送功能並重新實現符合實際意義的匯出功能
* `最佳化` 日誌活動頁面傳送功能支援條目數量過大時自動擷取並作出提示
* `最佳化` ocr 模組同時相容 Google MLKit 及 Paddle Lite 引擎 (參閱 專案檔案 > [光學字元識別](https://docs.autojs6.com/#/ocr?id=p-mode))
* `最佳化` 提升無障礙服務自動啟動的成功機率
* `最佳化` Kotlin 註解處理由 kapt 遷移至 KSP
* `最佳化` 構建工具支援 IntelliJ Idea EAP 版本
* `最佳化` 構建工具自適應 Java 發行版本以儘量避免 "無效的發行版本" 問題
* `最佳化` 構建工具最佳化 IDE 及相關外掛的版本退級邏輯並增加版本預測能力
* `最佳化` 適配 VSCode 外掛 1.0.7
* `依賴` 附加 Rikka Shizuku 版本 13.1.5
* `依賴` 附加 MLKit Barcode Scanning 版本 17.2.0
* `依賴` 升級 OpenCV 版本 4.5.5 -> 4.8.0 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依賴` 升級 Gradle Compile 版本 33 -> 34
* `依賴` 升級 Gradle 版本 8.3-rc-1 -> 8.4-rc-3
* `依賴` 升級 Apache Commons Lang3 版本 3.12.0 -> 3.13.0
* `依賴` 升級 Glide 版本 4.15.1 -> 4.16.0
* `依賴` 升級 Android Analytics 版本 14.3.0 -> 14.4.0
* `依賴` 升級 Androidx WebKit 版本 1.7.0 -> 1.8.0
* `依賴` 升級 Androidx Preference 版本 1.2.0 -> 1.2.1
* `依賴` 升級 Androidx Annotation 版本 1.6.0 -> 1.7.0
* `依賴` 升級 Androidx Recyclerview 版本 1.3.0 -> 1.3.2
* `依賴` 升級 Android Material 版本 1.9.0 -> 1.10.0
* `依賴` 升級 Androidx AppCompat 版本 1.4.2 -> 1.6.1
* `依賴` 升級 Commons IO 版本 2.8.0 -> 2.14.0
* `依賴` 升級 Jackson DataBind 版本 2.13.3 -> 2.14.3
* `依賴` 移除 Zeugma Solutions LocaleHelper 版本 1.5.1

# v6.3.3

###### 2023/07/21

* `新增` 程式碼編輯器的程式碼註釋功能 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新增` auto.stateListener 用於無障礙服務連線狀態監聽 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新增` UiObject 型別新增 nextSibling / lastChild / offset 等方法 (參閱 專案文件 > [控制元件節點](https://docs.autojs6.com/#/uiObjectType))
* `修復` VSCode 外掛在指令碼字元總長度超過四位十進位制數時無法解析資料的問題 _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `修復` VSCode 外掛無法正常儲存檔案的問題 _[`issue #92`](http://issues.autojs6.com/91)_ _[`issue #94`](http://issues.autojs6.com/93)_
* `修復` 浮動按鈕選單項 "管理無障礙服務" 點選後可能未發生頁面跳轉的問題
* `修復` runtime.requestPermissions 方法丟失的問題 _[`issue #104`](http://issues.autojs6.com/104)_
* `修復` events.emitter 不支援 MainThreadProxy 引數的問題 _[`issue #103`](http://issues.autojs6.com/103)_
* `修復` 在 _[`pr #78`](http://pr.autojs6.com/78)_ 中存在的程式碼編輯器無法格式化程式碼的問題
* `修復` 使用 JavaAdapter 時導致 ClassLoader 呼叫棧溢位的問題 _[`issue #99`](http://issues.autojs6.com/99)_ _[`issue #110`](http://issues.autojs6.com/110)_
* `最佳化` 調整模組作用域 (by [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `最佳化` 移除發行版本應用啟動時的簽名校驗 (by [LZX284](https://github.com/LZX284)) _[`pr #81`](http://pr.autojs6.com/81)_
* `最佳化` 在 _[`pr #98`](http://pr.autojs6.com/98)_ 基礎上的編輯器程式碼註釋功能的行為, 樣式及游標位置處理
* `最佳化` 在 _[`pr #98`](http://pr.autojs6.com/98)_ 基礎上新增程式碼註釋選單項
* `最佳化` 適配 VSCode 外掛 1.0.6
* `最佳化` UiObject#parent 方法增加級數引數支援 (參閱 專案文件 > [控制元件節點](https://docs.autojs6.com/#/uiObjectType))
* `依賴` 升級 Gradle 版本 8.2 -> 8.3-rc-1

# v6.3.2

###### 2023/07/06

* `新增` crypto 模組 (參閱 專案文件 > [密文](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
* `新增` UI 模式增加 textswitcher / viewswitcher / viewflipper / numberpicker / video / search 等控制元件
* `新增` 日誌活動頁面增加複製及匯出日誌等功能 _[`issue #76`](http://issues.autojs6.com/76)_
* `新增` 客戶端模式增加 IP 地址歷史記錄功能
* `修復` 客戶端模式自動連線或服務端模式自動開啟後可能無法顯示 IP 地址資訊的問題
* `修復` 客戶端模式及服務端模式連線後在切換語言或夜間模式時連線斷開且無法再次連線的問題
* `修復` 客戶端模式輸入目標地址時無法使用自定義埠的問題
* `修復` 客戶端模式輸入目標地址時某些字元將導致 AutoJs6 崩潰的問題
* `修復` VSCode 外掛遠端命令可能出現解析失敗造成命令無法響應的問題 (試修)
* `修復` Android 7.x 發現新版本時無法獲取版本詳情的問題
* `修復` images.pixel 無法獲取無障礙服務截圖的畫素色值的問題 _[`issue #73`](http://issues.autojs6.com/73)_
* `修復` UI 模式 Android 原生控制元件 (大寫字母開頭) 無法使用預置控制元件屬性的問題
* `修復` runtime.loadDex/loadJar 載入多個檔案時僅第一個檔案生效的問題 _[`issue #88`](http://issues.autojs6.com/88)_
* `修復` 部分裝置安裝應用後啟動器僅顯示文件圖示的問題 (試修) _[`issue #85`](http://issues.autojs6.com/85)_
* `最佳化` 適配 VSCode 外掛 1.0.5
* `最佳化` 支援 cheerio 模組 (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `最佳化` JsWebSocket 例項支援使用 rebuild 方法重新重建例項並建立連線 _[`issue #69`](http://issues.autojs6.com/69)_
* `最佳化` base64 模組支援 number 陣列及 Java 位元組陣列作為主要引數的編解碼
* `最佳化` 增加對 JavaMail for Android 的支援 _[`issue #71`](http://issues.autojs6.com/71)_
* `最佳化` 獲取版本更新資訊時使用 Blob 資料型別以增強無代理網路環境適應性
* `最佳化` 客戶端模式連線過程中在主頁抽屜副標題顯示目標 IP 地址
* `最佳化` 客戶端模式輸入目標地址時支援對不合法的輸入進行提示
* `最佳化` 客戶端模式支援使用軟鍵盤迴車鍵建立連線
* `最佳化` 服務端模式開啟後保持常開狀態 (除非手動關閉或應用程序結束) _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `最佳化` 實現 AutoJs6 與 VSCode 外掛的雙向版本檢測並提示異常檢測結果 _[`issue #89`](http://issues.autojs6.com/89)_
* `最佳化` 增加簡訊資料讀取許可權 (android.permission.READ_SMS) (預設關閉)
* `最佳化` findMultiColors 方法內部實現 (by [LYS86](https://github.com/LYS86)) _[`pr #72`](http://pr.autojs6.com/72)_
* `最佳化` runtime.loadDex/loadJar/load 支援按目錄級別載入或同時載入多個檔案
* `依賴` 升級 LeakCanary 版本 2.11 -> 2.12
* `依賴` 升級 Android Analytics 版本 14.2.0 -> 14.3.0
* `依賴` 升級 Gradle 版本 8.2-milestone-1 -> 8.2

# v6.3.1

###### 2023/05/26

* `新增` 釋出通知許可權及主頁抽屜開關 _[`issue #55`](http://issues.autojs6.com/55)_
* `新增` UI 模式支援簡單的 Android 佈局解析 (參閱 示例程式碼 > 佈局 > 簡單安卓佈局)
* `新增` UI 模式增加 console / imagebutton / ratingbar / switch / textclock / togglebutton 等控制元件
* `新增` UI 模式控制元件的顏色色值支援 [OmniColor](https://docs.autojs6.com/#/omniTypes?id=omnicolor) 型別 (如 color="orange")
* `新增` UI 模式的控制元件完全支援 attr 方法設定控制元件屬性 (如 ui.text.attr('color', 'blue'))
* `新增` UI 模式控制元件支援布林型別屬性值的預設形式 (如 clickable="true" 可簡寫為 clickable 或 isClickable)
* `新增` button 控制元件支援 isColored 及 isBorderless 布林型別屬性
* `新增` console.resetGlobalLogConfig 方法用於重置全域性日誌配置
* `新增` web.newWebSocket 方法用於建立 Web Socket 例項 (參閱 專案文件 > [全球資訊網](https://docs.autojs6.com/#/web?id=m-newwebsocket))
* `修復` 檔案管理器的資料夾排序異常
* `修復` floaty 模組構建的浮動視窗無法調節樣式及位置的問題 _[`issue #60`](http://issues.autojs6.com/60)_
* `修復` floaty 模組構建的浮動視窗與系統狀態列重疊的問題
* `修復` http.postMultipart 方法功能異常 _[`issue #56`](http://issues.autojs6.com/56)_
* `修復` Android 7.x 無法執行任何指令碼的問題 _[`issue #61`](http://issues.autojs6.com/61)_
* `修復` sign.property 檔案不存在時無法構建專案的問題
* `修復` 高版本系統 AutoJs6 置於後臺時可能因無前臺通知許可權而崩潰的問題 (API >= 33)
* `修復` 呼叫 console.show 方法後日志視窗點選 FAB 按鈕無法清空日誌的問題
* `修復` 指令碼編輯器除錯時出現的 prototype 空指標異常
* `修復` 指令碼編輯器執行指令碼時在快取資料夾執行臨時指令碼而非先儲存再在原始位置執行以避免可能的指令碼內容丟失問題
* `修復` 調整佈局層次分析的層級色條寬度避免層級過多時控制元件名稱無法顯示的問題 _[`issue #46`](http://issues.autojs6.com/46)_
* `最佳化` 佈局分析浮動視窗增加退出按鈕以關閉視窗 _[`issue #63`](http://issues.autojs6.com/63)_
* `最佳化` 指令碼絕對路徑使用簡稱形式以縮減文字長度並增加可讀性
* `最佳化` 將 Error 替換為 Exception 避免出現異常時 AutoJs6 應用崩潰
* `最佳化` 檢視 (View) 繫結方式由 ButterKnife 遷移至 View Binding _[`issue #48`](http://issues.autojs6.com/48)_
* `最佳化` 服務端模式非正常關閉時將於 AutoJs6 啟動時自動開啟 _[`issue #64`](http://issues.autojs6.com/64)_
* `最佳化` 客戶端模式非正常關閉時將於 AutoJs6 啟動時按最近一次的歷史地址自動連線
* `依賴` 升級 LeakCanary 版本 2.10 -> 2.11
* `依賴` 升級 Android Material 版本 1.8.0 -> 1.9.0
* `依賴` 升級 Androidx WebKit 版本 1.6.1 -> 1.7.0
* `依賴` 升級 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11
* `依賴` 升級 MLKit Text Recognition Chinese 版本 16.0.0-beta6 -> 16.0.0

# v6.3.0

###### 2023/04/29

* `新增` ocr 模組 (參閱 專案檔案 > [光學字元識別](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `新增` notice 模組 (參閱 專案檔案 > [訊息通知](https://docs.autojs6.com/#/notice))
* `新增` s13n 模組 (參閱 專案檔案 > [標準化](https://docs.autojs6.com/#/s13n))
* `新增` Color 模組 (參閱 專案檔案 > [顏色類](https://docs.autojs6.com/#/colorType))
* `新增` 前臺時保持螢幕常亮功能及設定選項
* `新增` 額外的檔案啟動器 (launcher) 便於獨立閱讀應用檔案 (支援在設定中隱藏或顯示)
* `修復` colors.toString 方法功能異常
* `修復` app.openUrl 方法自動新增協議字首功能異常
* `修復` app.viewFile/editFile 在引數對應檔案不存在時的行為異常
* `修復` pickup 方法的回撥函式無法被呼叫的問題
* `修復` 佈局分析顯示的控制元件資訊 bounds 屬性值負數符號被替換為逗號的問題
* `修復` bounds/boundsInside/boundsContains 選擇器無法正常篩選狹義空矩形 (如邊界倒置矩形) _[`issue #49`](http://issues.autojs6.com/49)_
* `修復` 更換主題或修改語言後點選或長按主頁檔案標籤將導致應用崩潰的問題
* `修復` 文字編輯器雙指縮放調節字型大小時可能出現抖動的問題
* `修復` 構建指令碼中部分依賴源無法下載的問題 (已全部整合) _[`issue #40`](http://issues.autojs6.com/40)_
* `修復` Tasker 無法新增 AutoJs6 操作外掛 (Action Plugin) 的問題 (試修) _[`issue #41`](http://issues.autojs6.com/41)_
* `修復` 高版本 JDK 編譯專案時 ButterKnife 註解無法解析資源 ID 的問題 _[`issue #48`](http://issues.autojs6.com/48)_
* `修復` 無障礙服務較大機率出現服務異常的問題 (試修)
* `修復` images.medianBlur 的 size 引數使用方式與檔案不符的問題
* `修復` engines 模組顯示指令碼全稱時檔名與副檔名之間句點符號丟失的問題
* `修復` 加權 RGB 距離檢測演演算法內部實現可能存在的計算失誤 (試修)
* `修復` console 模組的浮動視窗相關方法無法在 show 方法之前使用的問題
* `修復` console.setSize 等方法可能無法生效的問題 _[`issue #50`](http://issues.autojs6.com/50)_
* `修復` colors.material 顏色空間的顏色常量賦值錯誤
* `修復` UI 模式的日期選擇控制元件 minDate 及 maxDate 屬性無法正確解析日期格式的問題
* `修復` 執行指令碼後快速切換到主頁 "任務" 標籤頁面將出現兩個相同執行中任務的問題
* `修復` 檔案管理頁面從其他頁面返回時頁面狀態可能被重置的問題 _[`issue #52`](http://issues.autojs6.com/52)_
* `修復` 檔案管理頁面排序狀態與圖示顯示狀態不符的問題
* `最佳化` 檔案管理頁面增加檔案及資料夾修改時間顯示
* `最佳化` 檔案管理頁面排序型別支援狀態記憶
* `最佳化` README.md 新增專案編譯構建小節與指令碼開發輔助小節 _[`issue #33`](http://issues.autojs6.com/33)_
* `最佳化` images 模組相關方法的區域 (region) 選項引數支援更多傳入方式 (參閱 專案檔案 > [全能型別](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `最佳化` app.startActivity 頁面簡寫引數增加 pref/homepage/docs/about 等形式的支援
* `最佳化` web 模組的全域性方法掛載到模組本身以增強可用性 (參閱 專案檔案 > [全球資訊網](https://docs.autojs6.com/#/web))
* `最佳化` web.newInjectableWebView 方法內部預設實現部分常用的 WebView 設定選項
* `最佳化` colors 模組新增多種轉換方法及工具方法並新增更多靜態常量以及可直接作為引數的顏色名稱
* `最佳化` console 模組新增多種控制檯浮動視窗的樣式配置方法並支援 build 構建器統一配置視窗樣式
* `最佳化` 控制檯浮動視窗支援拖動標題區域移動視窗位置
* `最佳化` 控制檯浮動視窗支援指令碼結束後自動延遲關閉
* `最佳化` 控制檯浮動視窗及其 Activity 活動視窗支援雙指縮放調整字型大小
* `最佳化` http 模組相關方法支援超時引數 (timeout)
* `最佳化` Gradle 構建指令碼支援 JDK 版本主動降級 (fallback)
* `最佳化` Gradle 構建指令碼支援根據平臺型別及版本自動選擇合適的構建工具版本 (程度有限)
* `依賴` 本地化 Auto.js APK Builder 版本 1.0.3
* `依賴` 本地化 MultiLevelListView 版本 1.1
* `依賴` 本地化 Settings Compat 版本 1.1.5
* `依賴` 本地化 Enhanced Floaty 版本 0.31
* `依賴` 附加 MLKit Text Recognition Chinese 版本 16.0.0-beta6
* `依賴` 升級 Gradle 版本 8.0-rc-1 -> 8.2-milestone-1
* `依賴` 升級 Android Material 版本 1.7.0 -> 1.8.0
* `依賴` 升級 Glide 版本 4.14.2 -> 4.15.1
* `依賴` 升級 Joda Time 版本 2.12.2 -> 2.12.5
* `依賴` 升級 Android Analytics 版本 14.0.0 -> 14.2.0
* `依賴` 升級 Androidx WebKit 版本 1.5.0 -> 1.6.1
* `依賴` 升級 Androidx Recyclerview 版本 1.2.1 -> 1.3.0
* `依賴` 升級 Zip4j 版本 2.11.2 -> 2.11.5
* `依賴` 升級 Junit Jupiter 版本 5.9.2 -> 5.9.3
* `依賴` 升級 Androidx Annotation 版本 1.5.0 -> 1.6.0
* `依賴` 升級 Jackson DataBind 版本 2.14.1 -> 2.14.2
* `依賴` 升級 Desugar JDK Libs 版本 2.0.0 -> 2.0.3

# v6.2.0

###### 2023/01/21

* `新增` 重新設計及編寫專案文件 (部分完成)
* `新增` 西/法/俄/阿/日/韓/英/繁中等多語言適配
* `新增` 工作路徑設定選項增加路徑選擇/歷史記錄/預設值智慧提示等功能
* `新增` 檔案管理器支援任意目錄的上一級跳轉 (直至 "內部儲存" 目錄)
* `新增` 檔案管理器支援將任意目錄快捷設定為工作路徑
* `新增` 版本更新忽略及管理已忽略更新功能
* `新增` 文字編輯器支援雙指縮放調節字型大小
* `新增` idHex 選擇器 (UiSelector#idHex) (參閱 專案文件 > [選擇器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` action 選擇器 (UiSelector#action) (參閱 專案文件 > [選擇器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` Match 系列選擇器 (UiSelector#xxxMatch) (參閱 專案文件 > [選擇器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` 拾取選擇器 (UiSelector#pickup) (參閱 專案文件 > [選擇器](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `新增` 控制元件探測 (UiObject#detect) (參閱 專案文件 > [控制元件節點](https://docs.autojs6.com/#/uiObjectType))
* `新增` 控制元件羅盤 (UiObject#compass) (參閱 專案文件 > [控制元件節點](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `新增` 全域性等待方法 wait (參閱 專案文件 > [全域性物件](https://docs.autojs6.com/#/global?id=m-wait))
* `新增` 全域性縮放方法 cX/cY/cYx (參閱 專案文件 > [全域性物件](https://docs.autojs6.com/#/global?id=m-wait))
* `新增` 全域性 App 型別 (參閱 專案文件 > [應用列舉類](https://docs.autojs6.com/#/appType))
* `新增` i18n 模組 (基於 banana-i18n 的 JavaScript 多語言方案) (參閱 專案文件 > 國際化)
* `修復` 軟體語言切換後可能導致的頁面文字閃變及部分頁面按鈕功能異常
* `修復` 工作路徑為一個專案時軟體啟動後不顯示專案工具欄的問題
* `修復` 工作路徑可能跟隨軟體語言切換而自動改變的問題 _[`issue #19`](http://issues.autojs6.com/19)_
* `修復` 定時任務啟動延時顯著 (試修) _[`issue #21`](http://issues.autojs6.com/21)_
* `修復` JavaScript 模組名被覆蓋宣告時導致存在依賴關係的內部模組無法正常使用的問題 _[`issue #29`](http://issues.autojs6.com/29)_
* `修復` 高版本安卓系統點選快速設定面板中相關圖示後面板可能無法自動收起的問題 (試修) _[`issue #7`](http://issues.autojs6.com/7)_
* `修復` 高版本安卓系統可能出現部分頁面與通知欄區域重疊的問題
* `修復` Android 10 及以上系統無法正常執行有關設定畫筆顏色的示例程式碼的問題
* `修復` 示例程式碼 "音樂管理器" 更正檔名為 "檔案管理器" 並恢復正常功能
* `修復` 檔案管理器下拉重新整理時可能出現定位漂移的問題
* `修復` ui 模組作用域繫結錯誤導致部分基於 UI 的指令碼無法訪問元件屬性的問題
* `修復` 錄製指令碼後的輸入檔名對話方塊可能因外部區域點選導致已錄製內容丟失的問題
* `修復` 文件中部分章節標題超出螢幕寬度時無法自動換行造成內容丟失的問題
* `修復` 文件中的示例程式碼區域無法正常左右滑動的問題
* `修復` 文件頁面下拉重新整理時表現異常且無法撤銷重新整理操作的問題 (試修)
* `修復` 應用初始安裝後主頁抽屜夜間模式開關聯動失效的問題
* `修復` 系統夜間模式開啟時應用啟動後強制開啟夜間模式的問題
* `修復` 夜間模式開啟後已設定的主題色可能無法生效的問題
* `修復` 夜間模式下部分設定選項文字與背景色相同而無法辨識的問題
* `修復` 關於頁面功能按鈕文字長度過大導致文字顯示不完全的問題
* `修復` 主頁抽屜設定項標題長度過大導致文字與按鈕重疊的問題
* `修復` 主頁抽屜許可權開關在提示訊息對話方塊消失後可能出現狀態未同步的問題
* `修復` Root 許可權修改主頁抽屜許可權開關失敗時未繼續彈出 ADB 工具對話方塊的問題
* `修復` Root 許可權顯示指標位置在初次使用時提示無許可權的問題
* `修復` 圖示選擇頁面的圖示元素排版異常
* `修復` 文字編輯器啟動時可能因夜間模式設定導致閃屏的問題 (試修)
* `修復` 文字編輯器設定字型大小時可用最大值受限的問題
* `修復` 部分安卓系統指令碼執行結束時日誌中無法統計執行時長的問題
* `修復` 使用浮動按鈕選單關閉按鈕後重啟應用時浮動按鈕依然顯示的問題
* `修復` 佈局層次分析時長按列表項可能導致彈出選單溢位下方螢幕的問題
* `修復` Android 7.x 系統在夜間模式關閉時導航欄按鈕難以辨識的問題
* `修復` http.post 等方法可能出現的請求未關閉異常
* `修復` colors.toString 方法在 Alpha 通道為 0 時其通道資訊在結果中丟失的問題
* `最佳化` 重定向 Auto.js 4.x 版本的公有類以實現儘可能的向下相容 (程度有限)
* `最佳化` 合併全部專案模組避免可能的迴圈引用等問題 (臨時移除 inrt 模組)
* `最佳化` Gradle 構建配置從 Groovy 遷移到 KTS
* `最佳化` Rhino 異常訊息增加多語言支援
* `最佳化` 主頁抽屜許可權開關僅在開啟時彈出提示訊息
* `最佳化` 主頁抽屜佈局緊貼於狀態列下方避免頂部顏色條的低相容性
* `最佳化` 檢查更新/下載更新/更新提示功能相容 Android 7.x 系統
* `最佳化` 重新設計設定頁面 (遷移至 AndroidX)
* `最佳化` 設定頁面支援長按設定選項獲取詳細資訊
* `最佳化` 夜間模式增加 "跟隨系統" 設定選項 ( Android 9 及以上)
* `最佳化` 應用啟動畫面適配夜間模式
* `最佳化` 應用圖示增加數字標識以提升多個開源版本共存使用者的使用體驗
* `最佳化` 主題色增加更多 Material Design Color (材料設計顏色) 選項
* `最佳化` 檔案管理器/任務面板等列表項圖示適當輕量化並適配主題色
* `最佳化` 主頁搜尋框的提示文字顏色適配夜間模式
* `最佳化` 對話方塊/文字/Fab/AppBar/列表項等部件適配夜間模式
* `最佳化` 文件/設定/關於/主題色/佈局分析等頁面及浮動按鈕選單適配夜間模式
* `最佳化` 頁面佈局儘可能相容 RTL (Right-To-Left) 佈局
* `最佳化` 關於頁面增加圖示動畫效果
* `最佳化` 關於頁面版權宣告文字自動更新年份資訊
* `最佳化` 應用初始安裝後自動決定並設定合適的工作目錄
* `最佳化` 禁用文件頁面雙指縮放功能避免文件內容顯示異常
* `最佳化` 任務面板列表項按相對路徑簡化顯示任務的名稱及路徑
* `最佳化` 文字編輯器按鈕文字適當縮寫避免文字內容溢位
* `最佳化` 文字編輯器設定字型大小支援恢復預設值
* `最佳化` 提升浮動按鈕點選響應速度
* `最佳化` 點選浮動按鈕佈局分析按鈕直接進行佈局範圍分析
* `最佳化` 佈局分析主題自適應 (浮動視窗跟隨應用主題, 快速設定面板跟隨系統主題)
* `最佳化` 佈局控制元件資訊列表按可能的使用頻率重新排序
* `最佳化` 佈局控制元件資訊點選複製時根據選擇器型別自動最佳化輸出格式
* `最佳化` 使用浮動視窗選擇檔案時按返回鍵可返回至上級目錄而非直接關閉視窗
* `最佳化` 客戶端模式連線計算機輸入地址時支援數字有效性檢測及點分符號自動轉換
* `最佳化` 客戶端及服務端建立連線後在主頁抽屜顯示對應裝置的 IP 地址
* `最佳化` 部分全域性物件及內建模組增加覆防寫 (參閱 專案文件 > 全域性物件 > [覆防寫](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `最佳化` importClass 和 importPackage 支援字串引數及不定長引數
* `最佳化` ui.run 支援出現異常時列印棧追蹤資訊
* `最佳化` ui.R 及 auto.R 可便捷獲取 AutoJs6 的資源 ID
* `最佳化` app 模組中與操作應用相關的方法支援 App 型別引數及應用別名引數
* `最佳化` dialogs 模組中與非同步回撥相關的方法支援省略預填引數
* `最佳化` app.startActivity 等支援 url 選項引數 (參閱 示例程式碼 > 應用 > 意圖)
* `最佳化` device 模組獲取 IMEI 或硬體序列號失敗時返回 null 而非丟擲異常
* `最佳化` 提升 console.show 顯示的日誌浮動視窗文字亮度以增強內容辨識度
* `最佳化` ImageWrapper#saveTo 支援相對路徑儲存影象檔案
* `最佳化` 重新設計 colors 全域性物件並增加 HSV / HSL 等色彩模式支援 (參閱 專案文件 > [顏色](https://docs.autojs6.com/#/color))
* `依賴` 升級 Gradle Compile 版本 32 -> 33
* `依賴` 本地化 Android Job 版本 1.4.3
* `依賴` 本地化 Android Plugin Client SDK For Locale 版本 9.0.0
* `依賴` 本地化 GitHub API 版本 1.306
* `依賴` 附加 JCIP Annotations 版本 1.0
* `依賴` 附加 Androidx WebKit 版本 1.5.0
* `依賴` 附加 Commons IO 版本 2.8.0
* `依賴` 附加 Desugar JDK Libs 版本 2.0.0
* `依賴` 附加 Jackson DataBind 版本 2.13.3
* `依賴` 附加 Jaredrummler Android Device Names 版本 2.1.0
* `依賴` 附加 Jaredrummler Animated SVG View 版本 1.0.6
* `依賴` 替換 Jrummyapps ColorPicker 版本 2.1.7 為 Jaredrummler ColorPicker 版本 1.1.0
* `依賴` 升級 Gradle 版本 7.5-rc-1 -> 8.0-rc-1
* `依賴` 升級 Gradle 構建工具版本 7.4.0-alpha02 -> 8.0.0-alpha09
* `依賴` 升級 Kotlin Gradle 外掛版本 1.6.10 -> 1.8.0-RC2
* `依賴` 升級 Android Material 版本 1.6.0 -> 1.7.0
* `依賴` 升級 Androidx Annotation 版本 1.3.0 -> 1.5.0
* `依賴` 升級 Androidx AppCompat 版本 1.4.1 -> 1.4.2
* `依賴` 升級 Android Analytics 版本 13.3.0 -> 14.0.0
* `依賴` 升級 Gson 版本 2.9.0 -> 2.10
* `依賴` 升級 Joda Time 版本 2.10.14 -> 2.12.1
* `依賴` 升級 Kotlinx Coroutines 版本 1.6.1-native-mt -> 1.6.1
* `依賴` 升級 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.7 -> 5.0.0-alpha.9
* `依賴` 升級 Zip4j 版本 2.10.0 -> 2.11.2
* `依賴` 升級 Glide 版本 4.13.2 -> 4.14.2
* `依賴` 升級 Junit Jupiter 版本 5.9.0 -> 5.9.1

# v6.1.1

###### 2022/05/31

* `新增` 檢查更新/下載更新/更新提示功能 (參閱 設定頁面) (暫不支援 Android 7.x 系統)
* `修復` 應用在 Android 10 系統無法讀寫外部儲存的問題 _[`issue #17`](http://issues.autojs6.com/17)_
* `修復` 編輯器頁面長按時可能導致應用崩潰的問題 _[`issue #18`](http://issues.autojs6.com/18)_
* `修復` 編輯器頁面長按選單 "刪除行" 和 "複製行" 功能無效的問題
* `修復` 編輯器頁面選項選單中 "貼上" 功能缺失的問題
* `最佳化` 部分異常訊息字串資源化 (en / zh)
* `最佳化` 調整內容未儲存對話方塊的按鈕佈局並增加顏色區分
* `依賴` 附加 github-api 版本 1.306
* `依賴` 替換 retrofit2-rxjava2-adapter 版本 1.0.0 為 adapter-rxjava2 版本 2.9.0

# v6.1.0

###### 2022/05/26 - 包名變更, 謹慎升級

* `提示` 修改應用包名為 org.autojs.autojs6 避免與開源 Auto.js 應用包名衝突
* `新增` 主頁抽屜增加 "投影媒體許可權" 開關 (Root / ADB 方式) (開關狀態檢測為實驗性)
* `新增` 檔案瀏覽器支援顯示隱藏檔案和資料夾 (參閱 設定頁面)
* `新增` 強制 Root 檢查功能 (參閱 設定頁面 及 示例程式碼)
* `新增` autojs 模組 (參閱 示例程式碼 > AutoJs6)
* `新增` tasks 模組 (參閱 示例程式碼 > 任務)
* `新增` console.launch() 方法啟動日誌活動頁面
* `新增` util.morseCode 工具 (參閱 示例程式碼 > 工具 > 摩斯電碼)
* `新增` util.versionCodes 工具 (參閱 示例程式碼 > 工具 > 安卓版本資訊查詢)
* `新增` util.getClass() 等方法 (參閱 示例程式碼 > 工具 > 獲取類與類名)
* `新增` timers.setIntervalExt() 方法 (參閱 示例程式碼 > 定時器 > 條件週期執行)
* `新增` colors.toInt() / rgba() 等方法 (參閱 示例程式碼 > 影象與顏色 > 基本顏色轉換)
* `新增` automator.isServiceRunning() / ensureService() 方法
* `新增` automator.lockScreen() 等方法 (參閱 示例程式碼 > 無障礙服務 > Android 9 新增)
* `新增` automator.headsethook() 等方法 (參閱 示例程式碼 > 無障礙服務 > Android 11 新增)
* `新增` automator.captureScreen() 方法 (參閱 示例程式碼 > 無障礙服務 > 獲取螢幕截圖)
* `新增` dialogs.build() 選項引數屬性 animation, linkify 等 (參閱 示例程式碼 > 對話方塊 > 個性化對話方塊)
* `修復` dialogs.build() 選項引數屬性 inputHint, itemsSelectedIndex 等功能異常
* `修復` JsDialog#on('multi_choice') 回撥引數功能異常
* `修復` UiObject#parent().indexInParent() 總是返回 -1 的問題 _[`issue #16`](http://issues.autojs6.com/16)_
* `修復` Promise.resolve() 返回的 Thenable 在臨近指令碼結束時可能不被呼叫的問題
* `修復` 包名或類名中可能的拼寫失誤 (boardcast -> broadcast / auojs -> autojs)
* `修復` images.requestScreenCapture() 在高版本安卓系統可能導致應用崩潰的問題 (API >= 31)
* `修復` images.requestScreenCapture() 多個指令碼例項同時申請可能導致應用崩潰的問題
* `修復` 呼叫 new RootAutomator() 可能出現的假死問題
* `最佳化` RootAutomator 在無 Root 許可權時將無法例項化
* `最佳化` 重新設計 "關於應用與開發者" 頁面
* `最佳化` 重構全部內建 JavaScript 模組
* `最佳化` 重構全部 Gradle 構建指令碼並增加公共配置指令碼 (config.gradle)
* `最佳化` Gradle 構建工具支援版本號自動管理及構建檔案自動命名
* `最佳化` Gradle 構建工具增加 task 支援附加 CRC32 摘要到構建檔案 (appendDigestToReleasedFiles)
* `最佳化` shell() 呼叫時將異常寫入返回結果而非直接將異常丟擲 (無需 try/catch)
* `最佳化` 使用 Rhino 內建的 JSON 替代原 json2 模組
* `最佳化` auto.waitFor() 支援超時引數
* `最佳化` threads.start() 支援箭頭函式引數
* `最佳化` console.trace() 支援按日誌等級引數 (參閱 示例程式碼 > 控制檯 > 列印呼叫棧)
* `最佳化` device.vibrate() 支援模式振動及摩斯電碼振動 (參閱 示例程式碼 > 裝置 > 模式振動 / 摩斯電碼振動)
* `最佳化` 外部儲存讀寫許可權適配高版本安卓系統 (API >= 30)
* `最佳化` 控制檯字型採用 Material Color 增強普通及夜間主題下的字型可讀性
* `最佳化` 儲存 ImageWrapper 所有例項弱引用並在指令碼結束時自動回收 (實驗性)
* `依賴` 附加 CircleImageView 版本 3.1.0
* `依賴` 升級 Android Analytics 版本 13.1.0 -> 13.3.0
* `依賴` 升級 Gradle 構建工具版本 7.3.0-alpha06 -> 7.4.0-alpha02
* `依賴` 升級 Android Job 版本 1.4.2 -> 1.4.3
* `依賴` 升級 Android Material 版本 1.5.0 -> 1.6.0
* `依賴` 升級 CrashReport 版本 2.6.6 -> 4.0.4
* `依賴` 升級 Glide 版本 4.13.1 -> 4.13.2
* `依賴` 升級 Joda Time 版本 2.10.13 -> 2.10.14
* `依賴` 升級 Kotlin Gradle 外掛版本 1.6.10 -> 1.6.21
* `依賴` 升級 Kotlinx Coroutines 版本 1.6.0 -> 1.6.1-native-mt
* `依賴` 升級 LeakCanary 版本 2.8.1 -> 2.9.1
* `依賴` 升級 OkHttp3 版本 5.0.0-alpha.6 -> 5.0.0-alpha.7
* `依賴` 升級 Rhino 引擎版本 1.7.14 -> 1.7.15-snapshot
* `依賴` 升級 Zip4j 版本 2.9.1 -> 2.10.0
* `依賴` 移除 Groovy JSON 版本 3.0.8
* `依賴` 移除 Kotlin Stdlib JDK7 版本 1.6.21

# v6.0.3

###### 2022/03/19

* `新增` 多語言切換功能 (尚不完善)
* `新增` recorder 模組 (參閱 示例程式碼 > 計時器)
* `新增` 使用 "修改安全設定許可權" 自動啟用無障礙服務及開關設定
* `修復` 點選快速設定面板中相關圖示後面板未自動收起的問題 (試修) _[`issue #7`](http://issues.autojs6.com/7)_
* `修復` toast 使用強制顯示引數時可能導致 AutoJs6 崩潰的問題
* `修復` Socket 傳輸資料頭部資訊不完整時可能導致 AutoJs6 崩潰的問題
* `最佳化` 啟動或重啟 AutoJs6 時根據選項設定自動開啟無障礙服務
* `最佳化` 開啟浮動按鈕開關時嘗試自動開啟無障礙服務
* `最佳化` 所有資原始檔補全元素對應的英文翻譯
* `最佳化` 微調主頁抽屜佈局 減小專案排列間距
* `最佳化` 主頁抽屜增加前臺服務狀態開關的同步
* `最佳化` 主頁抽屜展開時立即按需同步開關狀態
* `最佳化` 顯示指標位置增加狀態檢測及結果提示
* `最佳化` 支援 64 位作業系統 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `最佳化` 浮動按鈕初始化時同時應用透明度設定 (無需點選後再應用透明度)
* `最佳化` 重置檔案內容時增加是否為示例程式碼檔案的檢測並增加結果提示
* `最佳化` 轉移打包外掛下載地址 GitHub -> JsDelivr
* `依賴` 附加 Zeugma Solutions LocaleHelper 版本 1.5.1
* `依賴` 降級 Android Material 版本 1.6.0-alpha02 -> 1.5.0
* `依賴` 升級 Kotlinx Coroutines 版本 1.6.0-native-mt -> 1.6.0
* `依賴` 升級 OpenCV 版本 3.4.3 -> 4.5.4 -> 4.5.5 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依賴` 升級 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.4 -> 5.0.0-alpha.6
* `依賴` 升級 Gradle 構建工具版本 7.2.0-beta01 -> 7.3.0-alpha06
* `依賴` 升級 Auto.js-ApkBuilder 版本 1.0.1 -> 1.0.3
* `依賴` 升級 Glide Compiler 版本 4.12.0 -> 4.13.1
* `依賴` 升級 Gradle 發行版本 7.4-rc-2 -> 7.4.1
* `依賴` 升級 Gradle Compile 版本 31 -> 32
* `依賴` 升級 Gson 版本 2.8.9 -> 2.9.0

# v6.0.2

###### 2022/02/05

* `新增` images.bilateralFilter() 雙邊濾波影象處理方法
* `修復` 多次呼叫 toast 只生效最後一次呼叫的問題
* `修復` toast.dismiss() 可能無效的問題
* `修復` 客戶端模式及服務端模式開關可能無法正常工作的問題
* `修復` 客戶端模式及服務端模式開關狀態不能正常重新整理的問題
* `修復` Android 7.x 解析 UI 模式 text 元素異常 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `最佳化` 忽略 sleep() 的 ScriptInterruptedException 異常
* `依賴` 附加 Androidx AppCompat (Legacy) 版本 1.0.2
* `依賴` 升級 Androidx AppCompat 版本 1.4.0 -> 1.4.1
* `依賴` 升級 Androidx Preference 版本 1.1.1 -> 1.2.0
* `依賴` 升級 Rhino 引擎版本 1.7.14-snapshot -> 1.7.14
* `依賴` 升級 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.3 -> 5.0.0-alpha.4
* `依賴` 升級 Android Material 版本 1.6.0-alpha01 -> 1.6.0-alpha02
* `依賴` 升級 Gradle 構建工具版本 7.2.0-alpha06 -> 7.2.0-beta01
* `依賴` 升級 Gradle 發行版本 7.3.3 -> 7.4-rc-2

# v6.0.1

###### 2022/01/01

* `新增` 連線 VSCode 外掛支援客戶端 (LAN) 及服務端 (LAN/ADB) 方式 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` base64 模組 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` 增加 isInteger/isNullish/isObject/isPrimitive/isReference 全域性方法
* `新增` 增加 polyfill (Object.getOwnPropertyDescriptors)
* `新增` 增加 polyfill (Array.prototype.flat)
* `最佳化` 擴充套件 global.sleep 支援 隨機範圍/負數相容
* `最佳化` 擴充套件 global.toast 支援 時長控制/強制覆蓋控制/dismiss
* `最佳化` 包名物件全域性化 (okhttp3/androidx/de)
* `依賴` 升級 Android Material 版本 1.5.0-beta01 -> 1.6.0-alpha01
* `依賴` 升級 Gradle 構建工具版本 7.2.0-alpha04 -> 7.2.0-alpha06
* `依賴` 升級 Kotlinx Coroutines 版本 1.5.2-native-mt -> 1.6.0-native-mt
* `依賴` 升級 Kotlin Gradle 外掛版本 1.6.0 -> 1.6.10
* `依賴` 升級 Gradle 發行版本 7.3 -> 7.3.3

# v6.0.0

###### 2021/12/01

* `新增` 主頁抽屜底部增加重啟應用按鈕
* `新增` 主頁抽屜增加忽略電池最佳化/顯示在其他應用上層等開關
* `修復` 應用初始安裝後部分割槽域主題顏色渲染異常的問題
* `修復` sign.property 檔案不存在時無法構建專案的問題
* `修復` 定時任務面板一次性任務的月份存取錯誤
* `修復` 應用設定頁面開關顏色不隨主題變更的問題
* `修復` 無法識別打包外掛及打包外掛下載地址無效的問題
* `修復` 主頁抽屜 "檢視使用情況許可權" 開關狀態可能不同步的問題
* `修復` TemplateMatching.fastTemplateMatching 潛在的 Mat 記憶體洩漏問題
* `最佳化` 升級 Rhino 引擎版本 1.7.7.2 -> 1.7.13 -> 1.7.14-snapshot
* `最佳化` 升級 OpenCV 版本 3.4.3 -> 4.5.4
* `最佳化` ViewUtil.getStatusBarHeight 提升相容性
* `最佳化` 主頁抽屜移除使用者登入相關模組並移除佈局佔位
* `最佳化` 主頁移除社群及市場標籤頁面並最佳化佈局對其方式
* `最佳化` 修改一些設定選項的預設開關狀態
* `最佳化` 關於頁面增加 SinceDate 並最佳化 Copyright 顯示
* `最佳化` 升級 JSON 模組至 2017-06-12 版本並整合 cycle.js
* `最佳化` 移除 Activity 前置時的自動檢查更新功能並移除檢查更新相關按鈕
* `最佳化` AppOpsKt#isOpPermissionGranted 內部程式碼邏輯
* `最佳化` ResourceMonitor 使用 ReentrantLock 增強安全性 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `最佳化` 使用 Maven Central 等倉庫替換 JCenter 倉庫
* `最佳化` 抽離並移除重複的本地庫檔案
* `依賴` 本地化 CrashReport 版本 2.6.6
* `依賴` 本地化 MutableTheme 版本 1.0.0
* `依賴` 附加 Androidx Preference 版本 1.1.1
* `依賴` 附加 SwipeRefreshLayout 版本 1.1.0
* `依賴` 升級 Android Analytics 版本 7.0.0 -> 13.1.0
* `依賴` 升級 Android Annotations 版本 4.5.2 -> 4.8.0
* `依賴` 升級 Gradle 構建工具版本 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `依賴` 升級 Android Job 版本 1.2.6 -> 1.4.2
* `依賴` 升級 Android Material 版本 1.1.0-alpha01 -> 1.5.0-beta01
* `依賴` 升級 Androidx MultiDex 版本 2.0.0 -> 2.0.1
* `依賴` 升級 Apache Commons Lang3 版本 3.6 -> 3.12.0
* `依賴` 升級 Appcompat 版本 1.0.2 -> 1.4.0
* `依賴` 升級 ButterKnife Gradle 外掛版本 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `依賴` 升級 ColorPicker 版本 2.1.5 -> 2.1.7
* `依賴` 升級 Espresso Core 版本 3.1.1-alpha01 -> 3.5.0-alpha03
* `依賴` 升級 Eventbus 版本 3.0.0 -> 3.2.0
* `依賴` 升級 Glide Compiler 版本 4.8.0 -> 4.12.0 -> 4.12.0
* `依賴` 升級 Gradle Build Tool 版本 29.0.2 -> 30.0.2
* `依賴` 升級 Gradle Compile 版本 28 -> 30 -> 31
* `依賴` 升級 Gradle 發行版本 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `依賴` 升級 Groovy-Json 外掛版本 3.0.7 -> 3.0.8
* `依賴` 升級 Gson 版本 2.8.2 -> 2.8.9
* `依賴` 升級 JavaVersion 版本 1.8 -> 11 -> 16
* `依賴` 升級 Joda Time 版本 2.9.9 -> 2.10.13
* `依賴` 升級 Junit 版本 4.12 -> 4.13.2
* `依賴` 升級 Kotlin Gradle 外掛版本 1.3.10 -> 1.4.10 -> 1.6.0
* `依賴` 升級 Kotlinx Coroutines 版本 1.0.1 -> 1.5.2-native-mt
* `依賴` 升級 LeakCanary 版本 1.6.1 -> 2.7
* `依賴` 升級 LicensesDialog 版本 1.8.1 -> 2.2.0
* `依賴` 升級 Material Dialogs 版本 0.9.2.3 -> 0.9.6.0
* `依賴` 升級 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.2 -> 5.0.0-alpha.3
* `依賴` 升級 Reactivex RxJava2 RxAndroid 版本 2.0.1 -> 2.1.1
* `依賴` 升級 Reactivex RxJava2 版本 2.1.2 -> 2.2.21
* `依賴` 升級 Retrofit2 Converter Gson 版本 2.3.0 -> 2.9.0
* `依賴` 升級 Retrofit2 Retrofit 版本 2.3.0 -> 2.9.0
* `依賴` 升級 Zip4j 版本 1.3.2 -> 2.9.1