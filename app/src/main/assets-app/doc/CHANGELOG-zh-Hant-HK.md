******

### 版本歷史

******

# v6.6.1

###### 2024/12/25

* `新增` pinyin 模塊, 用於漢語拼音轉換 (參閲 項目文檔 > [漢語拼音](https://docs.autojs6.com/#/pinyin))
* `新增` pinyin4j 模塊, 用於漢語拼音轉換 (參閲 項目文檔 > [漢語拼音](https://docs.autojs6.com/#/pinyin4j))
* `新增` UiObject#isSimilar 及 UiObjectCollection#isSimilar 方法, 用於確定控件或控件集合是否相似
* `修復` 部分環境因回退版本過低而無法正常編譯項目的問題
* `修復` 調用不存在的方法時可能出現的 "非原始類型值" 異常
* `修復` 部分設備無法正常添加腳本快捷方式的問題 (試修) _[`issue #221`](http://issues.autojs6.com/221)_
* `修復` automator.click/longClick 方法參數類型限制錯誤 _[`issue #275`](http://issues.autojs6.com/275)_
* `修復` 選擇器不支持 ConsString 類型參數的問題 _[`issue #277`](http://issues.autojs6.com/277)_
* `修復` UiObjectCollection 實例缺失自身方法及屬性的問題
* `優化` 恢復日誌活動窗口單個條目文本內容的雙擊或長按選擇功能 _[`issue #280`](http://issues.autojs6.com/280)_
* `優化` 腳本項目識別在 project.json 損壞情況下儘可能還原關鍵信息
* `優化` 打包單文件時自動生成的包名後綴支持將簡體中文轉換為拼音 (支持多音字)
* `優化` UiSelector#findOnce 及 UiSelector#find 方法支持負數參數
* `優化` UI 元素及 className 相關選擇器支持更多的包名前綴省略形式 (如 RecyclerView, Snackbar 等)
* `優化` 同步最新的 Rhino 引擎官方上游代碼並進行必要的代碼適配
* `依賴` 附加 Jieba Analysis 版本 1.0.3-SNAPSHOT (modified)
* `依賴` 升級 Gradle 版本 8.11.1 -> 8.12

# v6.6.0

###### 2024/12/02 - 內置模塊重寫, 謹慎升級

* `提示` 內置模塊使用 Kotlin 重新編寫以提升腳本運行效率但可能需要多次迭代逐步完善
* `提示` 內置 init.js 文件默認為空但支持開發者自行擴展內置模塊或掛載外部模塊
* `新增` axios 模塊 / cheerio 模塊 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX))
* `新增` sqlite 模塊, 用於 SQLite 數據庫簡單操作 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (參閲 項目文檔 > [SQLite](https://docs.autojs6.com/#/sqlite))
* `新增` mime 模塊, 用於處理和解析 MIME 類型字符串 (參閲 項目文檔 > [MIME](https://docs.autojs6.com/#/mime))
* `新增` nanoid 模塊, 可作為字符串 ID 生成器 (Ref to [ai/nanoid](https://github.com/ai/nanoid))
* `新增` sysprops 模塊, 用於獲取運行時環境配置數據 (參閲 項目文檔 > [系統屬性](https://docs.autojs6.com/#/sysprops))
* `新增` ocr 模塊支持 [Rapid OCR](https://github.com/RapidAI/RapidOCR) 引擎
* `新增` 佈局分析支持切換窗口 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` auto.clearCache 方法, 支持清除控件緩存 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` threads.pool 方法, 支持線程池簡單應用 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.matchTemplate 方法增加 useTransparentMask 選項參數, 支持透明找圖 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.requestScreenCaptureAsync 方法, 用於 UI 模式異步方式申請截圖權限 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.requestScreenCapture 方法增加 isAsync 選項參數, 支持異步方式獲取屏幕截圖 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.on('screen_capture', callback) 等事件監聽方法, 支持監聽屏幕截圖可用事件 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.stopScreenCapture 方法, 支持主動釋放截圖申請的相關資源 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.psnr/mse/ssim/mssim/hist/ncc 及 images.getSimilarity 方法, 用於獲取圖像相似性度量值
* `新增` images.isGrayscale 方法, 用於判斷圖像是否為灰度圖像
* `新增` images.invert 方法, 用於反色圖像轉換
* `新增` s13n.point/time 方法, 用於標準化點對象及時長對象 (參閲 項目文檔 > [標準化](https://docs.autojs6.com/#/s13n))
* `新增` console 模塊 gravity (重力), touchThrough (穿透點擊), backgroundTint (背景着色) 等相關方法 (參閲 項目文檔 > [控制枱](https://docs.autojs6.com/#/console))
* `新增` Mathx.randomInt/Mathx.randomFloat 方法, 用於返回指定範圍內的隨機整數或隨機浮點數
* `新增` app.launchDual/startDualActivity 等方法, 用於處理雙開應用 (需要 Shizuku 或 Root 權限) (實驗性)
* `新增` app.kill 方法, 用於強制停止應用 (需要 Shizuku 或 Root 權限)
* `新增` floaty.getClip 方法, 用於藉助浮動窗口聚焦間接獲取剪切板內容
* `修復` Fragment 子類 (如 [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48) 等) 中存在的 View Binding 內存泄漏
* `修復` [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21) 等類中存在的實例內存泄漏
* `修復` Android 14+ 申請截圖權限導致應用崩潰的問題 (by [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `修復` Android 14+ 開啓前台服務導致應用崩潰的問題
* `修復` Android 14+ 代碼編輯器運行按鈕點擊後無法正常亮起的問題
* `修復` 項目打包後應用可能因缺少必要庫文件無法正常運行的問題 _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `修復` 編輯項目時可能因指定圖標資源不存在而導致應用崩潰的問題 _[`issue #203`](http://issues.autojs6.com/203)_
* `修復` 截圖權限申請時無法正常使用參數獲取指定屏幕方向的截圖資源
* `修復` 部分設備無法正常添加腳本快捷方式的問題 (試修) _[`issue #221`](http://issues.autojs6.com/221)_
* `修復` 調用 http 模塊與發送請求相關的方法將出現累積性請求發送延遲的問題 _[`issue #192`](http://issues.autojs6.com/192)_
* `修復` Shizuku 服務在 AutoJs6 進入主活動頁面之前可能無法正常使用的問題 (試修) _[`issue #255`](http://issues.autojs6.com/255)_
* `修復` random(min, max) 方法可能出現結果越界的問題
* `修復` pickup 方法結果類型參數無法正常傳入空數組的問題
* `修復` UiObject#bounds() 得到的控件矩形可能被意外修改而破壞其不變性的問題
* `修復` text/button/input 元素的文本內容包含半角雙引號時無法正常解析的問題
* `修復` text/textswitcher 元素的 autoLink 屬性功能失效的問題
* `修復` 不同腳本可能錯誤地共享同一個 ScriptRuntime 對象的問題
* `修復` 全局變量 HEIGHT 及 WIDTH 丟失 Getter 動態屬性的問題
* `修復` 腳本啓動時 RootShell 隨即加載可能導致啓動高延遲的問題
* `修復` 控制枱浮動窗口設置背景顏色導致矩形圓角樣式丟失的問題
* `修復` 無障礙服務自動啓動可能出現的服務異常問題 (試修)
* `修復` 主頁文檔頁面左右滑動 WebView 控件時可能觸發 ViewPager 切換的問題
* `修復` 文件管理器無法識別包含大寫字母文件擴展名的問題
* `修復` 文件管理器首次進入項目目錄時可能無法自動識別項目的問題
* `修復` 文件管理器刪除文件夾後頁面無法自動刷新的問題
* `修復` 文件管理器排序文件及文件夾時可能出現 ASCII 首字母名稱置後的問題
* `修復` 代碼編輯器調試功能的 FAILED ASSERTION 異常
* `修復` 代碼編輯器調試過程中關閉編輯器後無法再次正常調試的問題
* `修復` 代碼編輯器跳轉到行尾時可能遺漏末尾字符的問題
* `修復` 主活動頁面啓動日誌活動頁面時可能出現閃屏的問題
* `修復` 打包應用無法正常使用 opencc 模塊的問題
* `優化` 打包頁面中 "不可用 ABI" 控件的點擊提示體驗
* `優化` 支持使用 Shizuku 控制 "指針位置" 顯示開關
* `優化` 支持使用 Shizuku 控制 "投影媒體" 及 "修改安全設置" 權限開關
* `優化` automator.gestureAsync/gesturesAsync 支持回調函數參數
* `優化` tasks 模塊使用同步方式進行數據庫操作避免可能的數據訪問不一致問題
* `優化` 腳本執行模式支持管道符號分隔模式參數 (如 `"ui|auto";` 開頭)
* `優化` 腳本執行模式支持單引號及反引號且支持省略分號 (如 `'ui';` 或 `'ui'` 開頭)
* `優化` 腳本執行模式支持 axios, cheerio, dayjs 等模式參數快捷導入內置擴展模塊 (如 `"axios";` 開頭)
* `優化` 腳本執行模式支持 x 或 jsox 模式參數快捷啓用 JavaScript 內置對象擴展模塊 (如 `"x";` 開頭)
* `優化` img 元素 src 及 path 屬性支持本地相對路徑 (如 `<img src="a.png"` />)
* `優化` 代碼編輯器導入 Java 類和包名時支持智能判斷插入位置
* `優化` images 模塊支持直接使用路徑作為圖像參數
* `優化` importPackage 支持字符串參數
* `優化` 服務端模式 IP 地址支持剪切板導入智能識別且支持空格按鍵智能轉換
* `優化` 文件管理器新建文件時支持默認前綴選擇並自動生成合適的數字後綴
* `優化` 文件管理器運行項目時具體化異常消息提示 _[`issue #268`](http://issues.autojs6.com/268)_
* `優化` 文件管理器支持更多類型並支持顯示對應的圖標符號 (支持 800 多種文件類型)
* `優化` 文件管理器可編輯的文件類型 (jpg/doc/pdf...) 增加編輯按鈕
* `優化` 文件管理器 APK 文件支持查看基礎信息, Manifest 信息及權限列表
* `優化` 文件管理器音視頻等媒體文件支持查看基礎信息及 MediaInfo 信息
* `優化` 打包單文件時支持自動填入合適的標準化名包並支持無效字符過濾提示
* `優化` 打包單文件時支持根據已安裝同包名應用自動設置圖標並自增版本號及版本名稱
* `優化` 打包項目配置文件支持 abis/libs 選項指定默認包含的 ABI 架構及擴展庫
* `優化` 打包項目配置文件 abis/libs 選項無效或無可用時支持相關消息提示
* `優化` LeakCanary 在正式發行版本中被排除以避免增加不必要性
* `優化` 項目源代碼所有英文註釋增加簡體中文翻譯以增強註釋可讀性
* `優化` README 及 CHANGELOG 支持多語言 (由腳本自動生成)
* `優化` Gradle 構建腳本提升版本自適應能力
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

* `新增` opencc 模塊 (參閲 項目文檔 > [中文轉換](https://docs.autojs6.com/#/opencc)) (Ref to [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `新增` UiSelector 增加 [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) 及 [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) 方法 _[`issue #115`](http://issues.autojs6.com/115)_
* `新增` 打包應用頁面增加 ABI 及庫的篩選支持 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `修復` 打包應用文件體積異常龐大的問題 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `修復` 打包應用無法顯示並打印部分異常消息的問題
* `修復` 打包應用頁面選擇應用圖標後可能顯示空圖標的問題
* `修復` 打包應用包含 MLKit Google OCR 庫時可能出現的上下文未初始化異常
* `修復` ocr.<u>mlkit/ocr</u>.<u>recognizeText/detect</u> 方法無效的問題
* `修復` 部分文本 (如日誌頁面) 顯示語言與應用設置語言可能不相符的問題
* `修復` 部分語言在主頁抽屜開關項可能出現文本溢出的問題
* `修復` 部分設備無障礙服務開啓後立即自動關閉且無任何提示消息的問題 _[`issue #181`](http://issues.autojs6.com/181)_
* `修復` 部分設備無障礙服務開啓後設備物理按鍵可能導致應用崩潰的問題 (試修) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `修復` 使用 auto(true) 重啓無障礙服務後 pickup 功能異常的問題 (試修) _[`issue #184`](http://issues.autojs6.com/184)_
* `修復` floaty 模塊創建浮動窗口拖動時可能導致應用崩潰的問題 (試修)
* `修復` app.startActivity 無法使用簡稱參數的問題 _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `修復` importClass 導入的類名與全局變量衝突時代碼拋出異常的問題 _[`issue #185`](http://issues.autojs6.com/185)_
* `修復` Android 7.x 無法使用無障礙服務的問題
* `修復` Android 14 可能無法正常使用 runtime.<u>loadJar/loadDex</u> 方法的問題 (試修)
* `修復` 安卓系統快速設置面板中 "佈局範圍分析" 和 "佈局層次分析" 不可用的問題 _[`issue #193`](http://issues.autojs6.com/193)_
* `修復` 自動檢查更新功能可能導致應用 [ANR](https://developer.android.com/topic/performance/vitals/anr) 的問題 (試修) _[`issue #186`](http://issues.autojs6.com/186)_
* `修復` 文件管理器示例代碼文件夾點擊 "向上" 按鈕後無法回到工作路徑頁面的問題 _[`issue #129`](http://issues.autojs6.com/129)_
* `修復` 代碼編輯器使用替換功能時替換按鈕無法顯示的問題
* `修復` 代碼編輯器長按刪除時可能導致應用崩潰的問題 (試修)
* `修復` 代碼編輯器點擊 fx 按鈕無法顯示模塊函數快捷面板的問題
* `修復` 代碼編輯器模塊函數快捷面板按鈕函數名稱可能溢出的問題
* `優化` 代碼編輯器模塊函數快捷面板適配夜間模式
* `優化` 打包應用啓動頁面適配夜間模式並調整應用圖標佈局
* `優化` 打包應用頁面支持使用軟鍵盤 ENTER 鍵實現光標跳轉
* `優化` 打包應用頁面支持點擊 ABI 標題及庫標題切換全選狀態
* `優化` 打包應用頁面默認 ABI 智能選擇並增加不可選擇項的引導提示
* `優化` 文件管理器根據文件及文件夾的類型及特徵調整菜單項的顯示情況
* `優化` 文件管理器文件夾右鍵菜單增加打包應用選項
* `優化` 無障礙服務啓用但功能異常時在 AutoJs6 主頁抽屜開關處將體現異常狀態
* `優化` 控制枱打印錯誤消息時附加詳細的堆棧信息
* `依賴` 附加 ARSCLib 版本 1.2.4
* `依賴` 附加 Flexbox 版本 3.0.0
* `依賴` 附加 Android OpenCC 版本 1.2.0
* `依賴` 升級 Gradle 版本 8.5-rc-1 -> 8.5

# v6.4.2

###### 2023/11/15

* `新增` dialogs.build() 選項參數屬性 inputSingleLine
* `新增` console.setTouchable 方法 _[`issue #122`](http://issues.autojs6.com/122)_
* `修復` ocr 模塊部分方法無法識別區域參數的問題 _[`issue #162`](http://issues.autojs6.com/162)_  _[`issue #175`](http://issues.autojs6.com/175)_
* `修復` Android 7.x 發現新版本時無法獲取版本詳情的問題
* `修復` Android 14 申請截圖權限時導致應用崩潰的問題
* `修復` 主頁抽屜快速切換 "浮動按鈕" 開關時可能導致應用崩潰的問題
* `修復` 使用菜單關閉浮動按鈕時重啓應用後浮動按鈕可能依然顯示的問題
* `修復` Android 13 及以上系統設置頁面選擇並切換 AutoJs6 語言後無法生效的問題
* `修復` 構建工具初次構建時無法自動完成 OpenCV 資源部署的問題
* `優化` 原生化 bridges 模塊以提升腳本執行效率 (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7c41af6d2b9b36d00440a9c8b7e971d025f98327))
* `優化` 重構無障礙服務相關代碼以增強無障礙服務的功能穩定性 (實驗性) _[`issue #167`](http://issues.autojs6.com/167)_
* `優化` UiObject 和 UiObjectCollection 的打印輸出格式
* `優化` 構建工具在構建環境 Gradle JDK 版本不滿足要求時作出升級提示
* `依賴` 升級 Gradle 版本 8.4 -> 8.5-rc-1
* `依賴` 降級 Commons IO 版本 2.14.0 -> 2.8.0
* `依賴` 降級 Jackson DataBind 版本 2.14.3 -> 2.13.3

# v6.4.1

###### 2023/11/02

* `修復` 構建工具無法自適應未知平台的問題 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `修復` 腳本退出時可能導致應用崩潰的問題 _[`issue #159`](http://issues.autojs6.com/159)_
* `修復` http 模塊獲取響應對象的 body.contentType 返回值類型錯誤 _[`issue #142`](http://issues.autojs6.com/142)_
* `修復` device.width 及 device.height 返回數據不正確的問題 _[`issue #160`](http://issues.autojs6.com/160)_
* `修復` 代碼編輯器長按刪除時可能導致應用崩潰的問題 (試修) _[`issue #156`](http://issues.autojs6.com/156)_
* `修復` 代碼編輯器反向選擇文本後進行常規操作可能導致應用崩潰的問題
* `修復` 部分設備長按 AutoJs6 應用圖標無法顯示快捷方式菜單的問題
* `修復` 部分設備打包項目時點擊確認按鈕無響應的問題
* `修復` app.sendBroadcast 及 app.startActivity 無法使用簡稱參數的問題
* `修復` floaty 模塊 JsWindow#setPosition 等方法首次調用時的功能異常
* `優化` 增加 Termux 相關權限以支持 Intent 調用 Termux 執行 ADB 命令 _[`issue #136`](http://issues.autojs6.com/136)_
* `優化` http 模塊獲取的響應對象可重複使用 body.string() 及 body.bytes() 方法
* `優化` 增加 GitHub Actions 自動打包支持 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `優化` 構建工具自適應 Temurin 平台
* `依賴` 升級 Gradle 版本 8.4-rc-3 -> 8.4
* `依賴` 升級 Android dx 版本 1.11 -> 1.14

# v6.4.0

###### 2023/10/30

* `新增` ocr 模塊支持 Paddle Lite 引擎 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `新增` 打包功能支持內置插件與外部插件兩種打包方式 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `新增` WebSocket 模塊 (參閲 項目文檔 > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `新增` barcode / qrcode 模塊 (參閲 項目文檔 > [條碼](https://docs.autojs6.com/#/barcode) / [二維碼](https://docs.autojs6.com/#/qrcode))
* `新增` shizuku 模塊 (參閲 項目文檔 > [Shizuku](https://docs.autojs6.com/#/shizuku)) 及主頁抽屜權限開關
* `新增` device.rotation / device.orientation 等方法
* `新增` 內部 Java 類支持 class 靜態屬性訪問
* `新增` 支持在安卓系統設置頁面選擇並切換應用語言 ( Android 13 及以上)
* `新增` 支持設置頁面添加或長按應用圖標激活 [應用快捷方式](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn) , 可啓動文檔和設置等頁面
* `修復` 重新合併部分 PR (by [aiselp](https://github.com/aiselp)) 以解決部分腳本無法正常結束運行的問題 _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `修復` 打包應用無法使用 AutoJs6 新增 API 的問題 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `修復` 打包應用在系統夜間模式下的樣式異常
* `修復` VSCode 插件保存文件到本地時文件擴展名信息丟失的問題
* `修復` 使用協程特性運行項目產生未捕獲異常致使應用崩潰的問題
* `修復` 重啓或退出應用時浮動按鈕無法記錄其位置狀態信息的問題
* `修復` 設備屏幕方向改變時無法獲取更新後的設備配置信息的問題 _[`issue #153`](http://issues.autojs6.com/153)_
* `修復` 屏幕旋轉至橫向時 Toolbar 標題字體過小的問題
* `修復` 屏幕旋轉至橫向時應用主頁的頁籤排版過於擁擠的問題
* `修復` 屏幕旋轉至橫向時浮動按鈕可能溢出屏幕的問題 _[`issue #90`](http://issues.autojs6.com/90)_
* `修復` 屏幕多次旋轉時無法恢復浮動按鈕的座標及屏幕側邊方向的問題
* `修復` 部分設備消息浮動框可能出現遺漏顯示或重複顯示的問題
* `修復` 消息浮動框在多個腳本同時運行時可能存在被遮蔽的問題 _[`issue #67`](http://issues.autojs6.com/67)_
* `修復` 使用廣播分析佈局時點擊佈局無法彈出菜單且導致應用崩潰的問題
* `修復` 第二次及以後創建的 WebSocket 實例均無法正常觸發監聽器的問題
* `修復` 撤銷 importPackage 的全局重定向方法以避免某些作用域下的包導入異常 _[`issue #88`](http://issues.autojs6.com/88)_
* `修復` 日誌活動頁面使用複製或導出功能時可能導致應用崩潰的問題
* `優化` 日誌活動頁面導出功能重命名為發送功能並重新實現符合實際意義的導出功能
* `優化` 日誌活動頁面發送功能支持條目數量過大時自動截取並作出提示
* `優化` ocr 模塊同時兼容 Google MLKit 及 Paddle Lite 引擎 (參閲 項目文檔 > [光學字符識別](https://docs.autojs6.com/#/ocr?id=p-mode))
* `優化` 提升無障礙服務自動啓動的成功概率
* `優化` Kotlin 註解處理由 kapt 遷移至 KSP
* `優化` 構建工具支持 IntelliJ Idea EAP 版本
* `優化` 構建工具自適應 Java 發行版本以儘量避免 "無效的發行版本" 問題
* `優化` 構建工具優化 IDE 及相關插件的版本退級邏輯並增加版本預測能力
* `優化` 適配 VSCode 插件 1.0.7
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

* `新增` 代碼編輯器的代碼註釋功能 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新增` auto.stateListener 用於無障礙服務連接狀態監聽 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新增` UiObject 類型添加 nextSibling / lastChild / offset 等方法 (參閲 項目文檔 > [控件節點](https://docs.autojs6.com/#/uiObjectType))
* `修復` VSCode 插件在腳本字符總長度超過四位十進制數時無法解析數據的問題 _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `修復` VSCode 插件無法正常保存文件的問題 _[`issue #92`](http://issues.autojs6.com/91)_ _[`issue #94`](http://issues.autojs6.com/93)_
* `修復` 浮動按鈕菜單項 "管理無障礙服務" 點擊後可能未發生頁面跳轉的問題
* `修復` runtime.requestPermissions 方法丟失的問題 _[`issue #104`](http://issues.autojs6.com/104)_
* `修復` events.emitter 不支持 MainThreadProxy 參數的問題 _[`issue #103`](http://issues.autojs6.com/103)_
* `修復` 在 _[`pr #78`](http://pr.autojs6.com/78)_ 中存在的代碼編輯器無法格式化代碼的問題
* `修復` 使用 JavaAdapter 時導致 ClassLoader 調用棧溢出的問題 _[`issue #99`](http://issues.autojs6.com/99)_ _[`issue #110`](http://issues.autojs6.com/110)_
* `優化` 調整模塊作用域 (by [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `優化` 移除發行版本應用啓動時的簽名校驗 (by [LZX284](https://github.com/LZX284)) _[`pr #81`](http://pr.autojs6.com/81)_
* `優化` 在 _[`pr #98`](http://pr.autojs6.com/98)_ 基礎上的編輯器代碼註釋功能的行為, 樣式及光標位置處理
* `優化` 在 _[`pr #98`](http://pr.autojs6.com/98)_ 基礎上添加代碼註釋菜單項
* `優化` 適配 VSCode 插件 1.0.6
* `優化` UiObject#parent 方法增加級數參數支持 (參閲 項目文檔 > [控件節點](https://docs.autojs6.com/#/uiObjectType))
* `依賴` 升級 Gradle 版本 8.2 -> 8.3-rc-1

# v6.3.2

###### 2023/07/06

* `新增` crypto 模塊 (參閲 項目文檔 > [密文](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
* `新增` UI 模式增加 textswitcher / viewswitcher / viewflipper / numberpicker / video / search 等控件
* `新增` 日誌活動頁面增加複製及導出日誌等功能 _[`issue #76`](http://issues.autojs6.com/76)_
* `新增` 客户端模式增加 IP 地址歷史記錄功能
* `修復` 客户端模式自動連接或服務端模式自動開啓後可能無法顯示 IP 地址信息的問題
* `修復` 客户端模式及服務端模式連接後在切換語言或夜間模式時連接斷開且無法再次連接的問題
* `修復` 客户端模式輸入目標地址時無法使用自定義端口的問題
* `修復` 客户端模式輸入目標地址時某些字符將導致 AutoJs6 崩潰的問題
* `修復` VSCode 插件遠程命令可能出現解析失敗造成命令無法響應的問題 (試修)
* `修復` Android 7.x 發現新版本時無法獲取版本詳情的問題
* `修復` images.pixel 無法獲取無障礙服務截圖的像素色值的問題 _[`issue #73`](http://issues.autojs6.com/73)_
* `修復` UI 模式 Android 原生控件 (大寫字母開頭) 無法使用預置控件屬性的問題
* `修復` runtime.loadDex/loadJar 加載多個文件時僅第一個文件生效的問題 _[`issue #88`](http://issues.autojs6.com/88)_
* `修復` 部分設備安裝應用後啓動器僅顯示文檔圖標的問題 (試修) _[`issue #85`](http://issues.autojs6.com/85)_
* `優化` 適配 VSCode 插件 1.0.5
* `優化` 支持 cheerio 模塊 (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `優化` JsWebSocket 實例支持使用 rebuild 方法重新重建實例並建立連接 _[`issue #69`](http://issues.autojs6.com/69)_
* `優化` base64 模塊支持 number 數組及 Java 字節數組作為主要參數的編解碼
* `優化` 增加對 JavaMail for Android 的支持 _[`issue #71`](http://issues.autojs6.com/71)_
* `優化` 獲取版本更新信息時使用 Blob 數據類型以增強無代理網絡環境適應性
* `優化` 客户端模式連接過程中在主頁抽屜副標題顯示目標 IP 地址
* `優化` 客户端模式輸入目標地址時支持對不合法的輸入進行提示
* `優化` 客户端模式支持使用軟鍵盤迴車鍵建立連接
* `優化` 服務端模式開啓後保持常開狀態 (除非手動關閉或應用進程結束) _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `優化` 實現 AutoJs6 與 VSCode 插件的雙向版本檢測並提示異常檢測結果 _[`issue #89`](http://issues.autojs6.com/89)_
* `優化` 增加短信數據讀取權限 (android.permission.READ_SMS) (默認關閉)
* `優化` findMultiColors 方法內部實現 (by [LYS86](https://github.com/LYS86)) _[`pr #72`](http://pr.autojs6.com/72)_
* `優化` runtime.loadDex/loadJar/load 支持按目錄級別加載或同時加載多個文件
* `依賴` 升級 LeakCanary 版本 2.11 -> 2.12
* `依賴` 升級 Android Analytics 版本 14.2.0 -> 14.3.0
* `依賴` 升級 Gradle 版本 8.2-milestone-1 -> 8.2

# v6.3.1

###### 2023/05/26

* `新增` 發佈通知權限及主頁抽屜開關 _[`issue #55`](http://issues.autojs6.com/55)_
* `新增` UI 模式支持簡單的 Android 佈局解析 (參閲 示例代碼 > 佈局 > 簡單安卓佈局)
* `新增` UI 模式增加 console / imagebutton / ratingbar / switch / textclock / togglebutton 等控件
* `新增` UI 模式控件的顏色色值支持 [OmniColor](https://docs.autojs6.com/#/omniTypes?id=omnicolor) 類型 (如 color="orange")
* `新增` UI 模式的控件完全支持 attr 方法設置控件屬性 (如 ui.text.attr('color', 'blue'))
* `新增` UI 模式控件支持布爾類型屬性值的缺省形式 (如 clickable="true" 可簡寫為 clickable 或 isClickable)
* `新增` button 控件支持 isColored 及 isBorderless 布爾類型屬性
* `新增` console.resetGlobalLogConfig 方法用於重置全局日誌配置
* `新增` web.newWebSocket 方法用於創建 Web Socket 實例 (參閲 項目文檔 > [萬維網](https://docs.autojs6.com/#/web?id=m-newwebsocket))
* `修復` 文件管理器的文件夾排序異常
* `修復` floaty 模塊構建的浮動窗口無法調節樣式及位置的問題 _[`issue #60`](http://issues.autojs6.com/60)_
* `修復` floaty 模塊構建的浮動窗口與系統狀態欄重疊的問題
* `修復` http.postMultipart 方法功能異常 _[`issue #56`](http://issues.autojs6.com/56)_
* `修復` Android 7.x 無法運行任何腳本的問題 _[`issue #61`](http://issues.autojs6.com/61)_
* `修復` sign.property 文件不存在時無法構建項目的問題
* `修復` 高版本系統 AutoJs6 置於後台時可能因無前台通知權限而崩潰的問題 (API >= 33)
* `修復` 調用 console.show 方法後日志窗口點擊 FAB 按鈕無法清空日誌的問題
* `修復` 腳本編輯器調試時出現的 prototype 空指針異常
* `修復` 腳本編輯器運行腳本時在緩存文件夾運行臨時腳本而非先保存再在原始位置運行以避免可能的腳本內容丟失問題
* `修復` 調整佈局層次分析的層級色條寬度避免層級過多時控件名稱無法顯示的問題 _[`issue #46`](http://issues.autojs6.com/46)_
* `優化` 佈局分析浮動窗口增加退出按鈕以關閉窗口 _[`issue #63`](http://issues.autojs6.com/63)_
* `優化` 腳本絕對路徑使用簡稱形式以縮減文本長度並增加可讀性
* `優化` 將 Error 替換為 Exception 避免出現異常時 AutoJs6 應用崩潰
* `優化` 視圖 (View) 綁定方式由 ButterKnife 遷移至 View Binding _[`issue #48`](http://issues.autojs6.com/48)_
* `優化` 服務端模式非正常關閉時將於 AutoJs6 啓動時自動開啓 _[`issue #64`](http://issues.autojs6.com/64)_
* `優化` 客户端模式非正常關閉時將於 AutoJs6 啓動時按最近一次的歷史地址自動連接
* `依賴` 升級 LeakCanary 版本 2.10 -> 2.11
* `依賴` 升級 Android Material 版本 1.8.0 -> 1.9.0
* `依賴` 升級 Androidx WebKit 版本 1.6.1 -> 1.7.0
* `依賴` 升級 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11
* `依賴` 升級 MLKit Text Recognition Chinese 版本 16.0.0-beta6 -> 16.0.0

# v6.3.0

###### 2023/04/29

* `新增` ocr 模塊 (參閲 項目文檔 > [光學字符識別](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `新增` notice 模塊 (參閲 項目文檔 > [消息通知](https://docs.autojs6.com/#/notice))
* `新增` s13n 模塊 (參閲 項目文檔 > [標準化](https://docs.autojs6.com/#/s13n))
* `新增` Color 模塊 (參閲 項目文檔 > [顏色類](https://docs.autojs6.com/#/colorType))
* `新增` 前台時保持屏幕常亮功能及設置選項
* `新增` 額外的文檔啓動器 (launcher) 便於獨立閲讀應用文檔 (支持在設置中隱藏或顯示)
* `修復` colors.toString 方法功能異常
* `修復` app.openUrl 方法自動添加協議前綴功能異常
* `修復` app.viewFile/editFile 在參數對應文件不存在時的行為異常
* `修復` pickup 方法的回調函數無法被調用的問題
* `修復` 佈局分析顯示的控件信息 bounds 屬性值負數符號被替換為逗號的問題
* `修復` bounds/boundsInside/boundsContains 選擇器無法正常篩選狹義空矩形 (如邊界倒置矩形) _[`issue #49`](http://issues.autojs6.com/49)_
* `修復` 更換主題或修改語言後點擊或長按主頁文檔標籤將導致應用崩潰的問題
* `修復` 文本編輯器雙指縮放調節字體大小時可能出現抖動的問題
* `修復` 構建腳本中部分依賴源無法下載的問題 (已全部整合) _[`issue #40`](http://issues.autojs6.com/40)_
* `修復` Tasker 無法添加 AutoJs6 操作插件 (Action Plugin) 的問題 (試修) _[`issue #41`](http://issues.autojs6.com/41)_
* `修復` 高版本 JDK 編譯項目時 ButterKnife 註解無法解析資源 ID 的問題 _[`issue #48`](http://issues.autojs6.com/48)_
* `修復` 無障礙服務較大概率出現服務異常的問題 (試修)
* `修復` images.medianBlur 的 size 參數使用方式與文檔不符的問題
* `修復` engines 模塊顯示腳本全稱時文件名與擴展名之間句點符號丟失的問題
* `修復` 加權 RGB 距離檢測算法內部實現可能存在的計算失誤 (試修)
* `修復` console 模塊的浮動窗口相關方法無法在 show 方法之前使用的問題
* `修復` console.setSize 等方法可能無法生效的問題 _[`issue #50`](http://issues.autojs6.com/50)_
* `修復` colors.material 顏色空間的顏色常量賦值錯誤
* `修復` UI 模式的日期選擇控件 minDate 及 maxDate 屬性無法正確解析日期格式的問題
* `修復` 運行腳本後快速切換到主頁 "任務" 標籤頁面將出現兩個相同運行中任務的問題
* `修復` 文件管理頁面從其他頁面返回時頁面狀態可能被重置的問題 _[`issue #52`](http://issues.autojs6.com/52)_
* `修復` 文件管理頁面排序狀態與圖標顯示狀態不符的問題
* `優化` 文件管理頁面增加文件及文件夾修改時間顯示
* `優化` 文件管理頁面排序類型支持狀態記憶
* `優化` README.md 添加項目編譯構建小節與腳本開發輔助小節 _[`issue #33`](http://issues.autojs6.com/33)_
* `優化` images 模塊相關方法的區域 (region) 選項參數支持更多傳入方式 (參閲 項目文檔 > [全能類型](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `優化` app.startActivity 頁面簡寫參數增加 pref/homepage/docs/about 等形式的支持
* `優化` web 模塊的全局方法掛載到模塊本身以增強可用性 (參閲 項目文檔 > [萬維網](https://docs.autojs6.com/#/web))
* `優化` web.newInjectableWebView 方法內部默認實現部分常用的 WebView 設置選項
* `優化` colors 模塊添加多種轉換方法及工具方法並添加更多靜態常量以及可直接作為參數的顏色名稱
* `優化` console 模塊添加多種控制枱浮動窗口的樣式配置方法並支持 build 構建器統一配置窗口樣式
* `優化` 控制枱浮動窗口支持拖動標題區域移動窗口位置
* `優化` 控制枱浮動窗口支持腳本結束後自動延遲關閉
* `優化` 控制枱浮動窗口及其 Activity 活動窗口支持雙指縮放調整字體大小
* `優化` http 模塊相關方法支持超時參數 (timeout)
* `優化` Gradle 構建腳本支持 JDK 版本主動降級 (fallback)
* `優化` Gradle 構建腳本支持根據平台類型及版本自動選擇合適的構建工具版本 (程度有限)
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

* `新增` 重新設計及編寫項目文檔 (部分完成)
* `新增` 西/法/俄/阿/日/韓/英/繁中等多語言適配
* `新增` 工作路徑設置選項增加路徑選擇/歷史記錄/默認值智能提示等功能
* `新增` 文件管理器支持任意目錄的上一級跳轉 (直至 "內部存儲" 目錄)
* `新增` 文件管理器支持將任意目錄快捷設置為工作路徑
* `新增` 版本更新忽略及管理已忽略更新功能
* `新增` 文本編輯器支持雙指縮放調節字體大小
* `新增` idHex 選擇器 (UiSelector#idHex) (參閲 項目文檔 > [選擇器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` action 選擇器 (UiSelector#action) (參閲 項目文檔 > [選擇器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` Match 系列選擇器 (UiSelector#xxxMatch) (參閲 項目文檔 > [選擇器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` 拾取選擇器 (UiSelector#pickup) (參閲 項目文檔 > [選擇器](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `新增` 控件探測 (UiObject#detect) (參閲 項目文檔 > [控件節點](https://docs.autojs6.com/#/uiObjectType))
* `新增` 控件羅盤 (UiObject#compass) (參閲 項目文檔 > [控件節點](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `新增` 全局等待方法 wait (參閲 項目文檔 > [全局對象](https://docs.autojs6.com/#/global?id=m-wait))
* `新增` 全局縮放方法 cX/cY/cYx (參閲 項目文檔 > [全局對象](https://docs.autojs6.com/#/global?id=m-wait))
* `新增` 全局 App 類型 (參閲 項目文檔 > [應用枚舉類](https://docs.autojs6.com/#/appType))
* `新增` i18n 模塊 (基於 banana-i18n 的 JavaScript 多語言方案) (參閲 項目文檔 > 國際化)
* `修復` 軟件語言切換後可能導致的頁面文字閃變及部分頁面按鈕功能異常
* `修復` 工作路徑為一個項目時軟件啓動後不顯示項目工具欄的問題
* `修復` 工作路徑可能跟隨軟件語言切換而自動改變的問題 _[`issue #19`](http://issues.autojs6.com/19)_
* `修復` 定時任務啓動延時顯著 (試修) _[`issue #21`](http://issues.autojs6.com/21)_
* `修復` JavaScript 模塊名被覆蓋聲明時導致存在依賴關係的內部模塊無法正常使用的問題 _[`issue #29`](http://issues.autojs6.com/29)_
* `修復` 高版本安卓系統點擊快速設置面板中相關圖標後面板可能無法自動收起的問題 (試修) _[`issue #7`](http://issues.autojs6.com/7)_
* `修復` 高版本安卓系統可能出現部分頁面與通知欄區域重疊的問題
* `修復` Android 10 及以上系統無法正常運行有關設置畫筆顏色的示例代碼的問題
* `修復` 示例代碼 "音樂管理器" 更正文件名為 "文件管理器" 並恢復正常功能
* `修復` 文件管理器下拉刷新時可能出現定位漂移的問題
* `修復` ui 模塊作用域綁定錯誤導致部分基於 UI 的腳本無法訪問組件屬性的問題
* `修復` 錄製腳本後的輸入文件名對話框可能因外部區域點擊導致已錄製內容丟失的問題
* `修復` 文檔中部分章節標題超出屏幕寬度時無法自動換行造成內容丟失的問題
* `修復` 文檔中的示例代碼區域無法正常左右滑動的問題
* `修復` 文檔頁面下拉刷新時表現異常且無法撤銷刷新操作的問題 (試修)
* `修復` 應用初始安裝後主頁抽屜夜間模式開關聯動失效的問題
* `修復` 系統夜間模式開啓時應用啓動後強制開啓夜間模式的問題
* `修復` 夜間模式開啓後已設置的主題色可能無法生效的問題
* `修復` 夜間模式下部分設置選項文字與背景色相同而無法辨識的問題
* `修復` 關於頁面功能按鈕文本長度過大導致文本顯示不完全的問題
* `修復` 主頁抽屜設置項標題長度過大導致文本與按鈕重疊的問題
* `修復` 主頁抽屜權限開關在提示消息對話框消失後可能出現狀態未同步的問題
* `修復` Root 權限修改主頁抽屜權限開關失敗時未繼續彈出 ADB 工具對話框的問題
* `修復` Root 權限顯示指針位置在初次使用時提示無權限的問題
* `修復` 圖標選擇頁面的圖標元素排版異常
* `修復` 文本編輯器啓動時可能因夜間模式設置導致閃屏的問題 (試修)
* `修復` 文本編輯器設置字體大小時可用最大值受限的問題
* `修復` 部分安卓系統腳本運行結束時日誌中無法統計運行時長的問題
* `修復` 使用浮動按鈕菜單關閉按鈕後重啓應用時浮動按鈕依然顯示的問題
* `修復` 佈局層次分析時長按列表項可能導致彈出菜單溢出下方屏幕的問題
* `修復` Android 7.x 系統在夜間模式關閉時導航欄按鈕難以辨識的問題
* `修復` http.post 等方法可能出現的請求未關閉異常
* `修復` colors.toString 方法在 Alpha 通道為 0 時其通道信息在結果中丟失的問題
* `優化` 重定向 Auto.js 4.x 版本的公有類以實現儘可能的向下兼容 (程度有限)
* `優化` 合併全部項目模塊避免可能的循環引用等問題 (臨時移除 inrt 模塊)
* `優化` Gradle 構建配置從 Groovy 遷移到 KTS
* `優化` Rhino 異常消息增加多語言支持
* `優化` 主頁抽屜權限開關僅在開啓時彈出提示消息
* `優化` 主頁抽屜佈局緊貼於狀態欄下方避免頂部顏色條的低兼容性
* `優化` 檢查更新/下載更新/更新提示功能兼容 Android 7.x 系統
* `優化` 重新設計設置頁面 (遷移至 AndroidX)
* `優化` 設置頁面支持長按設置選項獲取詳細信息
* `優化` 夜間模式增加 "跟隨系統" 設置選項 ( Android 9 及以上)
* `優化` 應用啓動畫面適配夜間模式
* `優化` 應用圖標增加數字標識以提升多個開源版本共存用户的使用體驗
* `優化` 主題色增加更多 Material Design Color (材料設計顏色) 選項
* `優化` 文件管理器/任務面板等列表項圖標適當輕量化並適配主題色
* `優化` 主頁搜索框的提示文本顏色適配夜間模式
* `優化` 對話框/文本/Fab/AppBar/列表項等部件適配夜間模式
* `優化` 文檔/設置/關於/主題色/佈局分析等頁面及浮動按鈕菜單適配夜間模式
* `優化` 頁面佈局儘可能兼容 RTL (Right-To-Left) 佈局
* `優化` 關於頁面增加圖標動畫效果
* `優化` 關於頁面版權聲明文本自動更新年份信息
* `優化` 應用初始安裝後自動決定並設置合適的工作目錄
* `優化` 禁用文檔頁面雙指縮放功能避免文檔內容顯示異常
* `優化` 任務面板列表項按相對路徑簡化顯示任務的名稱及路徑
* `優化` 文本編輯器按鈕文本適當縮寫避免文本內容溢出
* `優化` 文本編輯器設置字體大小支持恢復默認值
* `優化` 提升浮動按鈕點擊響應速度
* `優化` 點擊浮動按鈕佈局分析按鈕直接進行佈局範圍分析
* `優化` 佈局分析主題自適應 (浮動窗口跟隨應用主題, 快速設置面板跟隨系統主題)
* `優化` 佈局控件信息列表按可能的使用頻率重新排序
* `優化` 佈局控件信息點擊複製時根據選擇器類型自動優化輸出格式
* `優化` 使用浮動窗口選擇文件時按返回鍵可返回至上級目錄而非直接關閉窗口
* `優化` 客户端模式連接計算機輸入地址時支持數字有效性檢測及點分符號自動轉換
* `優化` 客户端及服務端建立連接後在主頁抽屜顯示對應設備的 IP 地址
* `優化` 部分全局對象及內置模塊增加覆寫保護 (參閲 項目文檔 > 全局對象 > [覆寫保護](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `優化` importClass 和 importPackage 支持字符串參數及不定長參數
* `優化` ui.run 支持出現異常時打印棧追蹤信息
* `優化` ui.R 及 auto.R 可便捷獲取 AutoJs6 的資源 ID
* `優化` app 模塊中與操作應用相關的方法支持 App 類型參數及應用別名參數
* `優化` dialogs 模塊中與異步回調相關的方法支持省略預填參數
* `優化` app.startActivity 等支持 url 選項參數 (參閲 示例代碼 > 應用 > 意圖)
* `優化` device 模塊獲取 IMEI 或硬件序列號失敗時返回 null 而非拋出異常
* `優化` 提升 console.show 顯示的日誌浮動窗口文字亮度以增強內容辨識度
* `優化` ImageWrapper#saveTo 支持相對路徑保存圖像文件
* `優化` 重新設計 colors 全局對象並增加 HSV / HSL 等色彩模式支持 (參閲 項目文檔 > [顏色](https://docs.autojs6.com/#/color))
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
* `依賴` 升級 Kotlin Gradle 插件版本 1.6.10 -> 1.8.0-RC2
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

* `新增` 檢查更新/下載更新/更新提示功能 (參閲 設置頁面) (暫不支持 Android 7.x 系統)
* `修復` 應用在 Android 10 系統無法讀寫外部存儲的問題 _[`issue #17`](http://issues.autojs6.com/17)_
* `修復` 編輯器頁面長按時可能導致應用崩潰的問題 _[`issue #18`](http://issues.autojs6.com/18)_
* `修復` 編輯器頁面長按菜單 "刪除行" 和 "複製行" 功能無效的問題
* `修復` 編輯器頁面選項菜單中 "粘貼" 功能缺失的問題
* `優化` 部分異常消息字符串資源化 (en / zh)
* `優化` 調整內容未保存對話框的按鈕佈局並增加顏色區分
* `依賴` 附加 github-api 版本 1.306
* `依賴` 替換 retrofit2-rxjava2-adapter 版本 1.0.0 為 adapter-rxjava2 版本 2.9.0

# v6.1.0

###### 2022/05/26 - 包名變更, 謹慎升級

* `提示` 修改應用包名為 org.autojs.autojs6 避免與開源 Auto.js 應用包名衝突
* `新增` 主頁抽屜增加 "投影媒體權限" 開關 (Root / ADB 方式) (開關狀態檢測為實驗性)
* `新增` 文件瀏覽器支持顯示隱藏文件和文件夾 (參閲 設置頁面)
* `新增` 強制 Root 檢查功能 (參閲 設置頁面 及 示例代碼)
* `新增` autojs 模塊 (參閲 示例代碼 > AutoJs6)
* `新增` tasks 模塊 (參閲 示例代碼 > 任務)
* `新增` console.launch() 方法啓動日誌活動頁面
* `新增` util.morseCode 工具 (參閲 示例代碼 > 工具 > 摩斯電碼)
* `新增` util.versionCodes 工具 (參閲 示例代碼 > 工具 > 安卓版本信息查詢)
* `新增` util.getClass() 等方法 (參閲 示例代碼 > 工具 > 獲取類與類名)
* `新增` timers.setIntervalExt() 方法 (參閲 示例代碼 > 定時器 > 條件週期執行)
* `新增` colors.toInt() / rgba() 等方法 (參閲 示例代碼 > 圖像與顏色 > 基本顏色轉換)
* `新增` automator.isServiceRunning() / ensureService() 方法
* `新增` automator.lockScreen() 等方法 (參閲 示例代碼 > 無障礙服務 > Android 9 新增)
* `新增` automator.headsethook() 等方法 (參閲 示例代碼 > 無障礙服務 > Android 11 新增)
* `新增` automator.captureScreen() 方法 (參閲 示例代碼 > 無障礙服務 > 獲取屏幕截圖)
* `新增` dialogs.build() 選項參數屬性 animation, linkify 等 (參閲 示例代碼 > 對話框 > 個性化對話框)
* `修復` dialogs.build() 選項參數屬性 inputHint, itemsSelectedIndex 等功能異常
* `修復` JsDialog#on('multi_choice') 回調參數功能異常
* `修復` UiObject#parent().indexInParent() 總是返回 -1 的問題 _[`issue #16`](http://issues.autojs6.com/16)_
* `修復` Promise.resolve() 返回的 Thenable 在臨近腳本結束時可能不被調用的問題
* `修復` 包名或類名中可能的拼寫失誤 (boardcast -> broadcast / auojs -> autojs)
* `修復` images.requestScreenCapture() 在高版本安卓系統可能導致應用崩潰的問題 (API >= 31)
* `修復` images.requestScreenCapture() 多個腳本實例同時申請可能導致應用崩潰的問題
* `修復` 調用 new RootAutomator() 可能出現的假死問題
* `優化` RootAutomator 在無 Root 權限時將無法實例化
* `優化` 重新設計 "關於應用與開發者" 頁面
* `優化` 重構全部內置 JavaScript 模塊
* `優化` 重構全部 Gradle 構建腳本並增加公共配置腳本 (config.gradle)
* `優化` Gradle 構建工具支持版本號自動管理及構建文件自動命名
* `優化` Gradle 構建工具增加 task 支持附加 CRC32 摘要到構建文件 (appendDigestToReleasedFiles)
* `優化` shell() 調用時將異常寫入返回結果而非直接將異常拋出 (無需 try/catch)
* `優化` 使用 Rhino 內置的 JSON 替代原 json2 模塊
* `優化` auto.waitFor() 支持超時參數
* `優化` threads.start() 支持箭頭函數參數
* `優化` console.trace() 支持按日誌等級參數 (參閲 示例代碼 > 控制枱 > 打印調用棧)
* `優化` device.vibrate() 支持模式振動及摩斯電碼振動 (參閲 示例代碼 > 設備 > 模式振動 / 摩斯電碼振動)
* `優化` 外部存儲讀寫權限適配高版本安卓系統 (API >= 30)
* `優化` 控制枱字體採用 Material Color 增強普通及夜間主題下的字體可讀性
* `優化` 保存 ImageWrapper 所有實例弱引用並在腳本結束時自動回收 (實驗性)
* `依賴` 附加 CircleImageView 版本 3.1.0
* `依賴` 升級 Android Analytics 版本 13.1.0 -> 13.3.0
* `依賴` 升級 Gradle 構建工具版本 7.3.0-alpha06 -> 7.4.0-alpha02
* `依賴` 升級 Android Job 版本 1.4.2 -> 1.4.3
* `依賴` 升級 Android Material 版本 1.5.0 -> 1.6.0
* `依賴` 升級 CrashReport 版本 2.6.6 -> 4.0.4
* `依賴` 升級 Glide 版本 4.13.1 -> 4.13.2
* `依賴` 升級 Joda Time 版本 2.10.13 -> 2.10.14
* `依賴` 升級 Kotlin Gradle 插件版本 1.6.10 -> 1.6.21
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
* `新增` recorder 模塊 (參閲 示例代碼 > 計時器)
* `新增` 使用 "修改安全設置權限" 自動啓用無障礙服務及開關設置
* `修復` 點擊快速設置面板中相關圖標後面板未自動收起的問題 (試修) _[`issue #7`](http://issues.autojs6.com/7)_
* `修復` toast 使用強制顯示參數時可能導致 AutoJs6 崩潰的問題
* `修復` Socket 傳輸數據頭部信息不完整時可能導致 AutoJs6 崩潰的問題
* `優化` 啓動或重啓 AutoJs6 時根據選項設置自動開啓無障礙服務
* `優化` 開啓浮動按鈕開關時嘗試自動開啓無障礙服務
* `優化` 所有資源文件補全元素對應的英文翻譯
* `優化` 微調主頁抽屜佈局 減小項目排列間距
* `優化` 主頁抽屜增加前台服務狀態開關的同步
* `優化` 主頁抽屜展開時立即按需同步開關狀態
* `優化` 顯示指針位置增加狀態檢測及結果提示
* `優化` 支持 64 位操作系統 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `優化` 浮動按鈕初始化時同時應用透明度設置 (無需點擊後再應用透明度)
* `優化` 重置文件內容時增加是否為示例代碼文件的檢測並增加結果提示
* `優化` 轉移打包插件下載地址 GitHub -> JsDelivr
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

* `新增` images.bilateralFilter() 雙邊濾波圖像處理方法
* `修復` 多次調用 toast 只生效最後一次調用的問題
* `修復` toast.dismiss() 可能無效的問題
* `修復` 客户端模式及服務端模式開關可能無法正常工作的問題
* `修復` 客户端模式及服務端模式開關狀態不能正常刷新的問題
* `修復` Android 7.x 解析 UI 模式 text 元素異常 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `優化` 忽略 sleep() 的 ScriptInterruptedException 異常
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

* `新增` 連接 VSCode 插件支持客户端 (LAN) 及服務端 (LAN/ADB) 方式 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` base64 模塊 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` 增加 isInteger/isNullish/isObject/isPrimitive/isReference 全局方法
* `新增` 增加 polyfill (Object.getOwnPropertyDescriptors)
* `新增` 增加 polyfill (Array.prototype.flat)
* `優化` 擴展 global.sleep 支持 隨機範圍/負數兼容
* `優化` 擴展 global.toast 支持 時長控制/強制覆蓋控制/dismiss
* `優化` 包名對象全局化 (okhttp3/androidx/de)
* `依賴` 升級 Android Material 版本 1.5.0-beta01 -> 1.6.0-alpha01
* `依賴` 升級 Gradle 構建工具版本 7.2.0-alpha04 -> 7.2.0-alpha06
* `依賴` 升級 Kotlinx Coroutines 版本 1.5.2-native-mt -> 1.6.0-native-mt
* `依賴` 升級 Kotlin Gradle 插件版本 1.6.0 -> 1.6.10
* `依賴` 升級 Gradle 發行版本 7.3 -> 7.3.3

# v6.0.0

###### 2021/12/01

* `新增` 主頁抽屜底部增加重啓應用按鈕
* `新增` 主頁抽屜增加忽略電池優化/顯示在其他應用上層等開關
* `修復` 應用初始安裝後部分區域主題顏色渲染異常的問題
* `修復` sign.property 文件不存在時無法構建項目的問題
* `修復` 定時任務面板一次性任務的月份存取錯誤
* `修復` 應用設置頁面開關顏色不隨主題變更的問題
* `修復` 無法識別打包插件及打包插件下載地址無效的問題
* `修復` 主頁抽屜 "查看使用情況權限" 開關狀態可能不同步的問題
* `修復` TemplateMatching.fastTemplateMatching 潛在的 Mat 內存泄漏問題
* `優化` 升級 Rhino 引擎版本 1.7.7.2 -> 1.7.13 -> 1.7.14-snapshot
* `優化` 升級 OpenCV 版本 3.4.3 -> 4.5.4
* `優化` ViewUtil.getStatusBarHeight 提升兼容性
* `優化` 主頁抽屜移除用户登錄相關模塊並移除佈局佔位
* `優化` 主頁移除社區及市場標籤頁面並優化佈局對其方式
* `優化` 修改一些設置選項的默認開關狀態
* `優化` 關於頁面增加 SinceDate 並優化 Copyright 顯示
* `優化` 升級 JSON 模塊至 2017-06-12 版本並整合 cycle.js
* `優化` 移除 Activity 前置時的自動檢查更新功能並移除檢查更新相關按鈕
* `優化` AppOpsKt#isOpPermissionGranted 內部代碼邏輯
* `優化` ResourceMonitor 使用 ReentrantLock 增強安全性 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `優化` 使用 Maven Central 等倉庫替換 JCenter 倉庫
* `優化` 抽離並移除重複的本地庫文件
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
* `依賴` 升級 ButterKnife Gradle 插件版本 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `依賴` 升級 ColorPicker 版本 2.1.5 -> 2.1.7
* `依賴` 升級 Espresso Core 版本 3.1.1-alpha01 -> 3.5.0-alpha03
* `依賴` 升級 Eventbus 版本 3.0.0 -> 3.2.0
* `依賴` 升級 Glide Compiler 版本 4.8.0 -> 4.12.0 -> 4.12.0
* `依賴` 升級 Gradle Build Tool 版本 29.0.2 -> 30.0.2
* `依賴` 升級 Gradle Compile 版本 28 -> 30 -> 31
* `依賴` 升級 Gradle 發行版本 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `依賴` 升級 Groovy-Json 插件版本 3.0.7 -> 3.0.8
* `依賴` 升級 Gson 版本 2.8.2 -> 2.8.9
* `依賴` 升級 JavaVersion 版本 1.8 -> 11 -> 16
* `依賴` 升級 Joda Time 版本 2.9.9 -> 2.10.13
* `依賴` 升級 Junit 版本 4.12 -> 4.13.2
* `依賴` 升級 Kotlin Gradle 插件版本 1.3.10 -> 1.4.10 -> 1.6.0
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