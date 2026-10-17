******

### 發行歷史

******

# v6.8.0

###### 2026/10/17 - 部分功能外掛化, 謹慎升級

* `提示` 部分內建功能改由獨立外掛提供 (如 OCR, 條碼識別, OpenCV, OpenCC, 拼音, MediaInfo, 終端及離線專案文件), 使用前需在外掛中心安裝並啟用對應外掛
* `提示` 打包應用的目標 SDK 版本由 29 調整為 37, 共享儲存及本地網路訪問需按系統要求授權
* `提示` 打包應用使用 images 等依賴 OpenCV 的功能時, 需在打包時勾選 OpenCV 外掛
* `提示` console 控制元件及控制檯浮動視窗改為顯示目前指令碼日誌, 全域日誌可透過 globalconsole 控制元件或日誌頁面檢視
* `提示` 檔案日誌改用內建實現, 保留已文件化的配置選項及常用 PatternLayout 格式, 其他 Log4j API 不再提供
* `提示` Node.js 的 pinyin/pinyin4j 和控制台視窗控制方法改為非同步宿主呼叫, 指令碼需使用 await 等待結果
* `新增` 桌面捷徑支援新增自動存檔及納入升級前已有的固定捷徑, 支援備份匯出/匯入及批次還原; 無法讀取的圖示支援逐項補選或批次使用預設圖示, 已儲存的圖示不會被覆寫; 解凍後遺失的捷徑會在捷徑備份頁面標示以供還原, 首頁不主動彈出提示, 已識別的主動移除預設不勾選; 同一指令碼的多個捷徑依原 ID 獨立儲存 (納入已有固定捷徑需要 Android 7.1+, 自動偵測需要 Android 8+) (入口: 設定頁面 > 設定 > 捷徑備份)
* `新增` 設定精靈, 用於依次執行權限申請精靈及外掛安裝精靈, 首次啟動後主頁以歡迎提示提供入口 (入口: 設定頁面 > 配置 > 設定精靈)
* `新增` 權限申請精靈, 用於勾選主頁抽屜中的權限並依次申請, 內建 "標準"/"最小"/"全部" 配置並支援自定義配置, 已授予的權限可透過選單隱藏 (入口: 設定精靈/主頁抽屜 "權限" 分組標題按鈕)
* `新增` 外掛安裝精靈, 用於按分類勾選並批次安裝官方外掛, 內建 "標準"/"最小"/"全部" 配置並支援自定義配置的儲存, 重新命名及刪除 (入口: 設定精靈/主頁外掛標籤頁懸浮按鈕選單/外掛中心 "+" 選單)
* `新增` 外掛安裝精靈支援邊下載邊安裝, 具備 Shizuku/Root/Dhizuku 授權時可透過 3-Setup Installer 外掛靜默安裝, 使用系統安裝器時在下載前檢查安裝未知應用權限
* `新增` 外掛設定增加 "顯示啟動器圖示" 開關 (預設開啟), 外掛安裝精靈可統一設定本次新安裝外掛是否顯示圖示 (修改應用元件需要 Root 或 Shizuku)
* `新增` 無障礙服務管理器, 支援服務啟停, 快取清理, 自檢, 事件監視及診斷資訊複製 (入口: 主頁抽屜無障礙服務條目)
* `新增` 開發者選項頁面支援檢視行程退出記錄及複製診斷資訊 (Android 11+) (入口: 關於應用與開發者頁面長按應用圖示)
* `新增` 開發者選項增加 "允許 Node.js 指令碼使用 Inspector" 開關 (預設關閉), 用於配合 Node.js Runtime 外掛進行斷點除錯及 CPU/堆分析
* `新增` 程式碼生成模型管理頁, 支援本地匯入, 按需下載, 多版本管理與對比及官方模型更新 (入口: 設定頁面 > 無障礙服務 > 程式碼生成模型)
* `新增` MCP Server 外掛, 用於透過 MCP 客戶端訪問 AutoJs6 的指令碼, 檔案及裝置操作能力 (入口: 主頁抽屜/外掛中心)
* `新增` 3-Shell Terminal 外掛, 提供終端能力 (入口: 主頁抽屜, 專案工具欄, 檔案管理器目錄選單)
* `新增` Node.js Runtime 外掛, 用於執行 Node.js 檔案及專案 (支援 "nodejs" 模式參數及 .node.js/.cjs/.mjs 檔案)
* `新增` Python Runtime 外掛, 用於執行 Python 檔案及專案, 支援模組入口, 執行超時及預置標準輸入
* `新增` Lua Runtime 外掛, 用於執行 Lua 指令碼
* `新增` Java Runtime 及 Kotlin Runtime 外掛, 用於編譯並執行 Java/Kotlin 原始碼 (Java 需要 Android 7+, Kotlin 需要 Android 8+)
* `新增` QuickJS Runtime 外掛, 用於執行 JavaScript 檔案及 ES module (支援 "quickjs"/"qjs" 模式參數及 .quickjs.js/.quickjs.mjs 檔案)
* `新增` Bun Runtime 外掛, 用於執行 JavaScript/TypeScript 檔案及專案 (支援 "bun" 模式參數及 .bun.js/.bun.ts/.bun.tsx 檔案)
* `新增` TypeScript Engine 外掛, 用於編譯 Rhino/Node.js 的 TypeScript 檔案及專案, 支援型別檢查與 Source Map
* `新增` ai 模組, 用於透過 AI Provider 外掛 (如 Three Stone AI) 呼叫本地或線上模型, 支援流式輸出, 持久工作階段及結構化 JSON 結果 (參閱 範例程式碼 > ai)
* `新增` ai.agent 指令碼 API, 用於啟動, 查詢, 回應, 確認及取消 Three Stove Agent 任務, 支援 detached 後臺任務 (需要 Three Stove Agent 外掛) (參閱 範例程式碼 > agent)
* `新增` ai.agent.run/create 方法支援 plan 選項參數, 用於讓 Three Stove Agent 以計劃模式先提出步驟計劃再執行
* `新增` 指令碼可透過 project.json 或 @agent 頭註釋登記為 Three Stove Agent 可呼叫的指令碼, 支援參數校驗, 超時停止及結構化結果回傳
* `新增` compose 模組, 用於以 Jetpack Compose 構建介面, 提供節點工廠, 長期節點控制代碼, 響應式 state/render/ref 及主題控制 (需要 3-Block Compose 外掛) (參閱 範例程式碼 > 3-Block Compose)
* `新增` 編輯器選單新增 "在 3-Block Compose 設計器中開啟": 儲存目前檔案後交給 3-Block Compose 外掛 (1.2.0 及以上) 的視覺化設計器編輯, 回到編輯器時自動重新載入設計器儲存的內容 (有未儲存修改時先詢問) (參閱 範例程式碼 > 3-Block Compose)
* `新增` ui 模式佈局預覽服務, 供 3-Block Compose 設計器在畫布內以宿主真實佈局器預覽 XML 佈局, Android 11 及以上嵌入顯示, 更低版本提供快照 (需要 3-Block Compose 外掛 1.6.0 及以上)
* `新增` compose.mount/floaty 方法, 用於掛載 UI 頁面或建立懸浮窗 (懸浮窗同時支援 UI 與非 UI 指令碼), 指令碼停止時自動清理所屬工作階段
* `新增` compose.dialog 方法, 用於建立可更新和關閉的對話方塊及模態底部彈層工作階段, 可在 UI 或普通指令碼中使用
* `新增` XML <compose> 容器及 compose.attach 方法, 用於在 UI 頁面或懸浮窗內嵌入 Compose 工作階段; compose.AndroidView 可承載現有 Android View
* `新增` TSX 佈局支援 <compose.Column>, <compose:Text> 等 Compose 元件, 節點工廠引用, 插槽及響應式回呼 (需要 TypeScript Engine 外掛)
* `新增` ui.inflate 方法支援編譯後的 XML TSX 佈局及其 onClick 回呼繫結
* `新增` flow 模組及 waitAsync 全域方法, 用於非同步等待與鏈式操作, 支援分支, 迴圈, 超時, 重試及取消 (參閱 專案文件 > [流程](https://docs.autojs6.com/#/flow))
* `新增` terminal 全域物件, 用於終端工作階段管理, 可見執行與輸出監聽, 支援等待輸出及退出碼 (需要 3-Shell Terminal 外掛) (參閱 範例程式碼 > 終端)
* `新增` installer 模組, 用於安裝, 更新及解除安裝應用, 支援 Shizuku/Root/Dhizuku 靜默操作及通知安裝等選項 (需要 3-Setup Installer 外掛) (參閱 專案文件 > [安裝器](https://docs.autojs6.com/#/installer))
* `新增` mail 模組, 用於郵件收發, 查詢, 附件下載及資料夾管理, 支援同步與非同步呼叫 (需要 3-Stamp Mail 外掛) (參閱 專案文件 > [郵件](https://docs.autojs6.com/#/mail))
* `新增` mail.watch 方法, 用於監聽新郵件並支援斷線重連
* `新增` epub 模組, 用於讀取電子書後設資料, 目錄, 章節文字及封面, 支援資源匯出與全文搜尋 (需要 3-Folio EPUB 外掛) (參閱 專案文件 > [電子書](https://docs.autojs6.com/#/epub))
* `新增` epub.read 方法, 用於開啟電子書閱讀器並控制閱讀位置, 書籤及閱讀偏好
* `新增` epub.annotations/annotationsAsync 方法, 用於讀取高亮與筆記, 閱讀器工作階段支援監聽標註變更
* `新增` yolo 模組, 用於透過 YOLO 外掛載入模型並進行目標檢測 (參閱 專案文件 > [YOLO](https://docs.autojs6.com/#/yolo))
* `新增` tts 模組, 用於文字轉語音 (參閱 專案文件 > [文字轉語音](https://docs.autojs6.com/#/tts))
* `新增` settings 模組, 用於讀取及修改應用設定 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (參閱 專案文件 > [設定](https://docs.autojs6.com/#/settings))
* `新增` work_manager 模組, 用於管理定時任務及廣播任務 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (參閱 專案文件 > [Work Manager](https://docs.autojs6.com/#/workManager))
* `新增` power_manager 模組, 用於查詢及申請忽略電池最佳化 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (參閱 專案文件 > [Power Manager](https://docs.autojs6.com/#/powerManager))
* `新增` pangu 全域物件, 用於中西文混排的間距處理及檢查 (參閱 專案文件 > [文字間距](https://docs.autojs6.com/#/pangu))
* `新增` device.pageSize 屬性 (getter), 用於獲取系統記憶體頁大小
* `新增` device.getMarketName 方法, 用於獲取裝置的市場名稱 _[`issue #513`](http://issues.autojs6.com/513)_
* `新增` ui.useAndroidLayout 方法, 用於啟用 Android 原生布局控制元件支援
* `新增` runtime.loadAar 方法, 用於動態載入 AAR 檔案
* `新增` runtime.loadJarWithR8/retraceR8Stack 方法, 用於藉助 R8 Compiler 外掛載入 JAR 檔案及還原混淆堆疊 (實驗性)
* `新增` images.quantize/quantizeToFile 方法, 用於 PNG 影像量化及檔案儲存, 並返回影像大小與品質資訊 (需要 Image Quantization 外掛)
* `新增` images.countPointsByColor/getMeanColor 方法, 用於統計匹配顏色的畫素數量及獲取平均顏色
* `新增` colors.distance/invert/blend/contrast 方法, 用於顏色距離, 反色, 混合及對比度計算 (同樣適用於 Color 實例)
* `新增` colors.toOklab/oklab/toOklch/oklch 方法, 用於 OKLab 與 OKLCH 色彩空間轉換; colors.distance/isSimilar 及 images.detectColor 支援 oklab 演算法
* `新增` colors.ensureContrast/harmony/tones/derive/nightVariant/simulate/extract 方法, 用於對比度調整, 和諧色, 色階, 派生配色, 夜間對應色, 色覺缺陷模擬及影像主色提取 (參閱 範例程式碼 > 影像與顏色)
* `新增` colors.theme 物件, 用於透過指令碼讀取, 修改及重置主題色並控制智慧顏色庫, 主題色頁面可撤銷指令碼修改 (參閱 範例程式碼 > 影像與顏色)
* `新增` console.setTimeVisible/setTimeFormat/setColorful/setAvoidStatusBar/setInputVisible 方法及對應的 console.build 選項參數
* `新增` UI 模式支援 TypeScript 指令碼及 TSX 佈局 (需要 TypeScript Engine 外掛)
* `新增` UI 模式增加 globalconsole 控制元件, 用於顯示全域日誌; console 系列控制元件支援標題, 樣式及輸入欄設定
* `新增` smartClick/clickIfExists/clickAny/findAny 等自動化方法, 用於控制元件查詢與點選, 支援 Flow 鏈式呼叫
* `新增` smartClickBounds/clickBoundsIfExists/clickBoundsAny 方法, 用於按控制元件區域執行座標點選
* `新增` scrollUntil/typeInto/dismissPopups/collectList 等自動化方法, 用於滾動查詢, 文字輸入, 彈窗處理及列表採集
* `新增` launchAndWait/backUntil/backToApp/toggle/retry 等自動化方法, 用於應用切換, 狀態設定及操作重試
* `新增` waitForIdle/waitForEvent/waitForToast/waitForNotification 方法, 用於等待介面空閒, 無障礙事件, 訊息浮動框及通知
* `新增` select 方法支援字串選擇器語法, 用於組合屬性, 正規表示式及布林條件
* `新增` UiSelector#minDepth/maxDepth/minIndexInParent/maxIndexInParent 選擇器, 用於按節點深度及父級索引篩選, 並支援選擇器字串語法
* `新增` UiSelector#findIterator 方法, 用於惰性遍歷匹配控制元件
* `新增` UiObject#isStale/waitUntilGone/waitForStable 方法, 用於檢測節點失效及等待節點消失或穩定
* `新增` UiObject#contentInvalid/contextClickable/multiLine/dismissable 方法, 用於獲取控制元件布林屬性
* `新增` UiObject#toJSON/dumpSubtree/fingerprint 方法, 用於節點序列化, 子樹匯出及狀態指紋計算
* `新增` 控制元件集合支援 at/first/last/nonNull/slice 方法及 clickEach/setTextEach 等逐控制元件操作
* `新增` auto.findWindows/findWindowRoots 方法, 用於按視窗型別, 包名, 標題及顯示器等條件獲取視窗及根節點
* `新增` auto.explain/dump 方法及 auto.stats 物件, 用於選擇器診斷, 節點樹匯出及查詢耗時統計
* `新增` auto.registerEvent 方法支援事件去重及去抖選項 (預設關閉)
* `新增` auto.setFlags 方法支援 appWindowsFallback 標誌, 用於活動視窗根節點不可用時改用應用視窗根節點 (預設關閉)
* `新增` 檔案管理器支援檔案多選及批次複製/移動/刪除
* `新增` 檔案管理器增加路徑欄, 支援點選父級目錄跳轉及橫向滑動瀏覽
* `新增` 檔案管理器路徑欄選單採用緊湊配置, 支援後退/前進/向上/重新整理, 返回工作路徑, 複製路徑及設為工作路徑, 顯示儲存容量並切換內部儲存空間或記憶卡, 可快速設定隱藏檔案和副檔名
* `新增` 檔案管理器支援透過 Shizuku 或 Root 瀏覽受限目錄, 開啟並編輯文字檔案, 核對原始檔案後儲存修改; 權限失效, 檔案衝突或儲存結果不確定時保留編輯內容並提示處理方式, 其他應用以唯讀方式原地或按需讀取, 相容方式可在確認後使用暫存副本; 支援檔案或資料夾複製/移動/重新命名/永久刪除及多選操作, 複製/移動不覆寫同名目標, 失敗時提示權限狀態與保留路徑 (需要對應授權, 可存取範圍取決於系統; 跨檔案系統移動限共用儲存空間)
* `新增` 檔案管理器指令碼選單增加 "懸浮執行" 選項, 支援透過浮動工具欄執行/停止指令碼, 編輯檔案及檢視日誌
* `新增` 檔案管理器指令碼選單增加 "使用 AutoJs4 執行" 選項, 用於以 AutoJs4 引擎執行指令碼或專案並對照執行結果 (需要 AutoJs4 Runtime 外掛)
* `新增` 檔案管理器指令碼選單增加 "使用 AutoJs4 執行並對照" 選項, 用於先後以 AutoJs6 與 AutoJs4 引擎執行同一指令碼或專案做差異比較 (需要 AutoJs4 Runtime 外掛)
* `新增` 檔案管理器檔案及目錄選單增加 "複製路徑" 選項, 用於將絕對路徑複製到剪貼簿, 多選時可從選取列的 "更多" 中批次複製 (每行一個路徑)
* `新增` 檔案管理器支援透過 3-Stack Archive 外掛瀏覽, 預覽, 壓縮及解壓壓縮檔案, 支援密碼, 分卷及壓縮檔內檔案的直接開啟或執行
* `新增` 檔案管理器支援透過 Markdown Previewer/HTML Previewer 外掛預覽 Markdown/HTML 檔案, 支援主題, 自定義 CSS 及全屏模式
* `新增` 檔案管理器支援透過對應外掛檢視圖片, 播放音訊及影片及開啟 EPUB 電子書, 音訊支援後臺播放與通知欄控制
* `新增` 圖片檢視器支援同目錄圖片切換, 雙指縮放, EXIF 資訊顯示及全屏模式, 並可透過 3-Maple Image 外掛編輯圖片及轉換格式
* `新增` 從檔案管理器以外掛開啟的檔案可在外掛內移入回收站並撤銷 (需要外掛支援)
* `新增` 檔案管理器多選檔案時, 若有多個外掛動作可用, 可從選取列的 "更多" 中選擇其餘動作 (如用 3-Maple Image 批次轉換圖片)
* `新增` 檔案管理器支援檢視 APK/APKS/XAPK/APKM/APKZ/AAB 安裝包的資訊及清單, 並支援安裝 APK 及拆分 APK 容器
* `新增` 程式碼編輯器支援透過 Ace Editor 外掛載入 Ace 編輯器, 可在設定中選擇編輯器引擎, 外掛不可用時提供外掛中心入口
* `新增` Ace 編輯器支援程式碼補全, 參數提示, 懸浮文件與診斷, TypeScript 專案支援定義跳轉, 符號重新命名及自動匯入 (需要 TypeScript Engine 外掛)
* `新增` Ace 編輯器支援快捷鍵面板, 操作歷史及操作收藏
* `新增` Ace 編輯器支援展開或摺疊全部程式碼區塊 (需要更新 Ace Editor 外掛) (入口: 編輯 > 摺疊 > 全部展開/全部摺疊)
* `新增` 程式碼編輯器設定增加 "智慧提示" 分組 (僅 Ace 引擎), 用於配置補全與參數提示的觸發方式及延遲
* `新增` 程式碼編輯器支援檔案編碼轉換與重新載入, 並透過資訊欄顯示檔名, 游標位置及編碼
* `新增` 程式碼編輯器支援沉浸式全屏模式
* `新增` 程式碼編輯器支援底部日誌面板, 並支援指令碼啟停及堆疊幀原始碼跳轉 (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `新增` 程式碼編輯器支援獨立設定頁面, 提供主題, 字型, 行號, 自動換行, 程式碼高亮閾值及日誌面板顯示等選項
* `新增` 程式碼編輯器設定增加縮排參考線醒目提示, 向量側欄圖示, 編輯器捲軸 (含自動隱藏), 選取範圍內顯示空白字元, 拖曳放大鏡, 同名符號醒目提示及補全行內預覽等選項 (僅 Ace 引擎, 需要 Ace Editor 插件 2.0.0 及以上)
* `新增` 日誌頁面及程式碼編輯器底部日誌面板支援執行 TypeScript 程式碼片段 (需要 TypeScript Engine 外掛)
* `新增` 主頁抽屜增加 "螢幕取色" 工具 (需要 3-Color Picker 外掛)
* `新增` 定時任務支援按月/按年/倒計時任務及重複結束條件 _[`issue #294`](http://issues.autojs6.com/294)_ _[`issue #537`](http://issues.autojs6.com/537)_
* `新增` 專案資料夾支援建立定時任務及廣播任務
* `新增` 廣播任務增加 "郵件到達時" 型別, 支援按發件人及主題篩選, 郵件資訊可透過 engines.myEngine().execArgv.mail 獲取 (需要 3-Stamp Mail 外掛)
* `新增` 佈局分析支援多屬性組合選擇器複製, 節點樹儲存與離線分析
* `新增` 佈局分析視窗增加搜尋欄, 支援按關鍵字或正規表示式匹配控制元件的 text/desc/id/className, 層次視窗只保留匹配節點及其祖先, 範圍視窗只繪製匹配節點邊框, 支援結果列表, 匹配項跳轉及查詢歷史
* `新增` 佈局分析搜尋欄支援篩選配置 (布林屬性, 類名預設, 深度及尺寸區間等), 框選區域, 欄位模式 (`k=v`) 及選擇器模式 (`$()`), 配置與查詢模式跨工作階段記憶
* `新增` 佈局分析支援 "複製為選擇器", 將關鍵字, 欄位條件, 篩選配置及框選區域轉換為鏈式選擇器
* `新增` 佈局分析 "生成程式碼" 支援單個/集合範圍, 等待方式, 動作參數, 經典/Flow 風格, 相對定位及備選方案, 並可在佈局中預覽匹配結果
* `新增` 主題色支援自建顏色庫, 可新建或克隆顏色庫, 從調色盤, 其他顏色庫或歷史記錄新增顏色, 並支援重新命名, 修改色值, 拖動排序及批次刪除
* `新增` 智慧顏色庫, 可按夜間模式, 時段, 日出日落, 電量及指令碼狀態等狀態源自動切換顏色組, 支援定時或隨機換組及日期覆蓋; 內建 "材料設計日夜配對", "背景色" 及 "跟隨桌布" 預設, 並可從種子色生成
* `新增` 顏色庫支援以 JSON 格式匯入與匯出 (支援多選匯出及預設快照)
* `新增` 顏色詳情增加 "配色工具" (和諧色, 色階, 對比度及色覺缺陷模擬), 顏色支援收藏與置頂, 並增加 "常用與推薦" 虛擬庫
* `新增` 主題色 "派生配色" 設定 (預設關閉), 用於由主色自動推導強調色與深色
* `新增` 設定頁面增加回收站, 版本歷史及緊急草稿的保留策略, 支援按天數, 數量及容量清理
* `新增` 版本歷史支援獨立列表, 版本預覽及差異比較, 並支援搜尋, 排序與批次刪除
* `新增` 版本歷史支援手動快照, 版本固定, 標籤編輯, 匯出及恢復後直接儲存
* `新增` 回收站支援批次恢復, 內容預覽, 詳情檢視, 到期提示及移入後撤銷, 並支援識別與清理失效條目
* `新增` 打包應用支援 Node.js 專案及指令碼, 執行時需要 Node.js Runtime 外掛 (未安裝時顯示安裝引導)
* `新增` 打包應用支援在構建時編譯 Rhino TypeScript 專案, 並支援加密編譯產物及附加 Source Map
* `新增` 打包應用配置支援顯示或隱藏日誌及設定啟動器快捷方式 _[`issue #550`](http://issues.autojs6.com/550)_
* `新增` VSCode 外掛連線支援二維碼掃描, 配對確認及區域網發現 (需要 VSCode 外掛 1.1.0+)
* `新增` VSCode 外掛支援遠端檔案管理及裝置指令碼執行, 大檔案傳輸支援進度顯示 (需要 VSCode 外掛 1.1.0+)
* `新增` 客戶端模式及服務端模式增加連線管理器, 支援檢視連線狀態, 管理客戶端及訪問地址歷史記錄
* `新增` 客戶端模式支援意外斷開後自動重連, 網路恢復時重試及應用啟動時恢復連線
* `新增` Node.js screen_metrics 模組及相容全域座標轉換方法, 與 Bun SDK 共用宿主換算規則並跟隨螢幕方向變化
* `新增` Bun 明確匯入的宿主 API 用戶端, 提供拼音, Pinyin4j, 座標和控制台操作, 支援獨立能力開關
* `新增` Node.js 和 Bun 支援非同步 OpenCC 轉換及常用影像編解碼, 像素讀取, 變換和顏色搜尋; Bun 影像檔案存取獨立授權
* `新增` Node.js Worker 支援透過父執行環境呼叫 OpenCC 與影像模組, 包含 CJS, ESM, 巢狀 Worker 和影像二進位傳輸
* `新增` Bun 支援獨立授權的螢幕擷取, Android 錄製權限申請, 逾時/取消清理和重新申請
* `新增` Node.js Worker 支援 pinyin, pinyin4j, 主控台視窗和座標換算, 共用父執行狀態並保留原生輸出串流
* `新增` Bun screenRecorder 支援 H.264 MP4 螢幕錄製, 可選麥克風/應用程式播放/混合 AAC 音軌及錄製期間擷取畫面, 包含獨立音訊授權和取消清理
* `新增` Node.js 主執行緒與 Worker 支援 H.264 MP4 螢幕錄影, 麥克風/播放/混合 AAC 音訊, 錄製期間並行截圖及 Worker 未儲存錄影清理
* `新增` Node.js/Bun images 支援進階濾鏡, 色彩空間轉換, 多點尋色, 圓形偵測, 相似度指標及 URL 載入
* `新增` Node.js/Bun images 支援 SIFT/ORB 特徵擷取與比對, 回傳場景座標和 ObjectFrame 幾何資訊, 並管理執行內的 Mat/特徵控制代碼
* `新增` Node.js/Bun images 支援 PNG 量化和壓縮, 提供品質/記憶體度量, 二進位結果及檔案描述元輸出
* `修復` runtime.loadJar 方法無法動態載入較高 Java 版本編譯的 class 檔案的問題
* `修復` Android 7.x 呼叫 runtime.loadDex 方法時可能損壞 DEX 快取並導致類載入失敗的問題
* `修復` Rhino 引擎及內建模組的作用域, 參數轉換與回傳值處理不相容導致部分指令碼行為異常的問題
* `修復` Object.fromEntries 使用數字或數字字串作為鍵時, 產生的屬性無法正常存取且主控台顯示不完整的問題 _[`Rhino #2518`](https://github.com/mozilla/rhino/issues/2518)_
* `修復` Proxy 包裝函式及部分 JavaScript 回呼可能無法傳入內建模組, 或出現參數巢狀與多載歧義的問題
* `修復` EventEmitter#once 監聽器在遞迴派發事件或回呼丟擲異常時可能被重複呼叫的問題
* `修復` app.isInstalled/kill 等方法無法正常使用包名參數的問題 _[`issue #495`](http://issues.autojs6.com/495)_
* `修復` 無 Root 權限裝置呼叫 shell 相關方法時可能出現假死的問題
* `修復` dialogs.multiChoice 方法返回的多選結果被錯誤轉換為字串的問題 _[`issue #518`](http://issues.autojs6.com/518)_
* `修復` 指令碼對話方塊在執行緒結束時關閉可能導致應用崩潰的問題
* `修復` 取消定時器或停止子執行緒後, 過期回呼可能阻止指令碼自動結束的問題
* `修復` floaty.window/rawWindow 的聚焦相關方法在指令碼執行緒呼叫時可能出現執行緒異常的問題
* `修復` console.setPosition/setSize/setGravity 等方法的設定結果受呼叫順序或視窗顯示時機影響的問題
* `修復` 控制檯浮動視窗標題欄拖動可能失效及視窗無法貼近螢幕邊緣的問題
* `修復` console.log/util.format 等方法輸出物件或陣列時其中的字串值缺少單引號包裹, 與 Auto.js 4 的 util.inspect 格式不一致的問題
* `修復` http 請求的不安全選項可能使後續請求繼續跳過證書及主機名校驗的問題
* `修復` 正式發行版本及打包應用的 HTTP 請求可能被網路安全配置意外攔截的問題
* `修復` UI 模式 WebView 控制元件的檔案選擇器無法正常開啟的問題
* `修復` UI 模式 list/grid 控制項項目範本中的動態屬性運算式無法存取目前項目物件 (如 name 屬性) 而報告 ReferenceError 並導致清單項目顯示空白, 以及數值運算式結果顯示為 18.0 而非 18 的問題
* `修復` UI 指令碼進入分屏或調整視窗尺寸時可能意外退出的問題
* `修復` 選擇器多載方法, filter 全域方法及集合選擇器的正則參數無法正常使用的問題
* `修復` contains 系列選擇器將普通字串參數誤解析為正規表示式的問題
* `修復` 控制元件搜尋限制結果數量時可能多返回結果或未回收剩餘節點的問題
* `修復` UiObject#compass 及 pickup/detect 方法的羅盤參數解析與結果返回異常
* `修復` UiObject#child/parent 等方法可能出現無限遞迴的問題
* `修復` 選擇器字串表示中的屬性名稱錯誤及部分語言下的小數格式異常
* `修復` 全域選擇器方法丟擲的異常無法被 try/catch 捕獲的問題
* `修復` 啟用 findOnUiThread 標誌時控制元件查詢可能永久等待或無法按時超時的問題
* `修復` UiObject#clickBounds 方法可能意外結束指令碼事件迴圈並導致後續回呼失效, 或在主執行緒 (如 "ui" 模式指令碼) 呼叫時丟擲異常的問題
* `修復` auto.registerEvent 回呼的 source 節點在節點樹變化後可能出現死迴圈或越界異常的問題
* `修復` 無障礙服務重啟後 pickup 及 UiSelector 查詢可能始終返回空結果的問題
* `修復` auto.stateListener 在多個指令碼間互相覆蓋且未隨指令碼結束移除的問題
* `修復` 無障礙服務已開始投遞事件時 auto.waitFor 仍可能等待至超時的問題
* `修復` 使用 Root 權限關閉無障礙服務或重啟服務時可能未生效的問題
* `修復` 應用頁面建立時可能重新啟用使用者已手動關閉的無障礙服務的問題
* `修復` auto.start/enable 方法在無障礙服務已執行時仍重複重啟服務的問題
* `修復` auto.windowRoots 在活動視窗不可用時可能返回空節點的問題
* `修復` 無障礙服務執行狀態查詢及錯誤提示與主頁抽屜開關狀態不一致的問題
* `修復` auto.services 在未配置自動啟用方式時無法讀取已啟用服務列表的問題
* `修復` 佈局分析在自動啟用無障礙服務後仍需再次觸發才能執行的問題
* `修復` 無障礙服務重啟後已註冊的事件回呼及按鍵監聽失效的問題
* `修復` automator 全域操作首次呼叫時可能出現空指標異常, 以及手勢縱座標縮放錯誤的問題
* `修復` 無障礙服務在連續執行 back 等操作時可能意外中斷的問題 _[`issue #527`](http://issues.autojs6.com/527)_
* `修復` Node.js 指令碼 accessibility.ensureEnabled 方法在無障礙服務已啟用但未能及時繫結時報告橋接呼叫逾時而不是傳回 false 的問題
* `修復` 通知觀察器將系統通知誤報為訊息浮動框並遺漏通知事件的問題
* `修復` Android 14+ 申請螢幕捕獲權限時可能因前臺服務啟動異常導致應用崩潰的問題 _[`issue #516`](http://issues.autojs6.com/516)_
* `修復` 多執行緒或跨檔案重複呼叫 OCR 時可能反覆申請螢幕捕獲權限的問題 _[`issue #551`](http://issues.autojs6.com/551)_
* `修復` OCR 外掛選擇失敗時錯誤提示與實際引擎及安裝狀態不一致的問題
* `修復` Paddle OCR 外掛服務斷開後無法恢復或未執行時無法自動喚醒的問題 _[`issue #515`](http://issues.autojs6.com/515)_ _[`issue #525`](http://issues.autojs6.com/525)_ _[`issue #517`](http://issues.autojs6.com/517)_
* `修復` 打包應用依次使用 Paddle OCR 和 Rapid OCR 時可能導致應用崩潰的問題
* `修復` 打包時同時勾選 Paddle OCR v3 與 v4/v5/v6 外掛時, 打包應用的 Paddle OCR 不可用的問題 (打包應用僅支援 PP-OCRv3 外掛)
* `修復` images.resize 方法傳入錯誤參數時可能導致應用崩潰的問題
* `修復` images 部分方法可能提前回收影像, 洩漏原生記憶體或回收其他指令碼影像的問題
* `修復` colors.isSimilar/images.detectColor 的 hs 演算法結果錯誤, 以及 colors.rgb/rgba 無法正確處理小數或百分比分量的問題
* `修復` images.isEqual/mse/ncc/isGrayscale 等方法的比較結果及灰度或 RGBA 影像處理異常
* `修復` images.invert 丟失透明度, ImageWrapper#saveTo 紅藍通道顛倒及 images.clip 越界區域處理錯誤
* `修復` images.load 等方法可能未關閉連線或釋放臨時影像資源的問題
* `修復` images.findImage/matchTemplate 的邊緣匹配, 座標精度, 結果排序及數量限制異常
* `修復` images.matchTemplate 無法正確處理通道數不同的影像, 純色模板或透明遮罩, 以及 MatchingResult#sortBy 不接受小數比較結果的問題
* `修復` images.matchFeatures 返回邊框與實際位置不符, 以及小影像特徵檢測, 匹配結果繪製與資源釋放異常
* `修復` 指令碼反覆執行後記憶體佔用可能持續增長的問題 (試修) _[`issue #548`](http://issues.autojs6.com/548)_
* `修復` 透過外部 URI 或後臺執行指令碼時可能丟失原始路徑或副檔名的問題 _[`issue #545`](http://issues.autojs6.com/545)_
* `修復` Tasker 無法正常呼叫並執行指令碼的問題 _[`issue #501`](http://issues.autojs6.com/501)_
* `修復` 小元件批次更新時指令碼路徑及點選對映可能被誤刪的問題
* `修復` 檔案管理器直接執行帶 BOM 的指令碼時未按實際編碼讀取的問題
* `修復` 缺少打包資訊的指令碼專案無法正確識別或未按 project.json 指定入口執行的問題
* `修復` auto 檔案透過檔案管理器, 定時任務或外部意圖執行時被當作 JavaScript 指令碼執行的問題
* `修復` 檔案管理器無法檢視部分 APK 資訊或缺失清單入口的問題
* `修復` 外部 URI 匯入可能因檔名處理不當寫入目標目錄之外或覆蓋同名檔案的問題
* `修復` 檔案管理器返回頁面或刪除回收站條目後可能閃動或重置滾動位置的問題
* `修復` 檔案管理器無法正確區分 TypeScript 檔案與同字尾影片, 以及部分原始碼檔案缺少執行入口的問題
* `修復` 檔案管理器操作提示可能重複顯示的問題
* `修復` 部分觸控螢幕裝置上檔案管理器等列表的條目在開啟其他頁面並返回後持續顯示灰色背景的問題
* `修復` 任務列表中 "執行中任務" 的數量與顯示的條目不一致, 以及已退出的 UI 指令碼仍被列為執行中的問題
* `修復` 媒體及影像檔案路徑解析異常, 以及 MediaInfo 詳情顯示快取路徑的問題
* `修復` 媒體詳情查詢失敗時可能導致應用崩潰, 以及影片後設資料欄位顯示錯誤的問題
* `修復` 檔案移動或重新命名後版本歷史未跟隨路徑更新, 或被內容相同的副本錯誤關聯的問題
* `修復` 多使用者或工作資料環境下回收站及版本歷史無法正確訪問內部儲存的問題
* `修復` 版本歷史及回收站恢復覆蓋檔案時可能丟失原有內容或留下不完整檔案的問題
* `修復` 回收站恢復時含點目錄名稱的自動改名錯誤
* `修復` 版本歷史未顯示全部保留版本及切換操作後顯示模式異常
* `修復` 版本歷史清理時可能誤刪尚未完成記錄的新快照的問題
* `修復` 回收站恢復失敗時仍提示操作完成的問題
* `修復` 回收站恢復目錄時可能將異常歸檔條目寫入目標目錄之外的問題
* `修復` 程式碼編輯器在頁面重建後可能丟失游標, 選區或未儲存內容的問題
* `修復` 程式碼編輯器及日誌頁面顯示軟鍵盤或輸入欄時, 內容區域可能被遮擋或壓縮的問題
* `修復` 程式碼編輯器內的對話方塊在編輯器主題與應用程式日夜模式深淺不一致時, "更多" 選單的背景與文字顏色相近而難以辨認的問題
* `修復` 主頁搜尋框的狀態儲存, 游標配色及提示語言異常
* `修復` 主頁文件標籤頁處理異常多指觸控時可能導致應用崩潰的問題
* `修復` 主頁切換到檔案標籤頁時缺少過渡動畫的問題
* `修復` 主頁抽屜在窄屏下控制元件裁切, 底部按鈕缺失及開關點選區域過小的問題
* `修復` 主頁關閉或重建時, 抽屜清理監聽器訪問已釋放的上下文導致應用崩潰的問題
* `修復` 啟動頁背景未覆蓋劉海屏短邊區域的問題
* `修復` 浮動按鈕選單顯示的最近應用包名及 Activity 資訊異常 _[`issue #529`](http://issues.autojs6.com/529)_
* `修復` 浮動按鈕 "執行指令碼" 對話方塊最小化後可能無法還原的問題 (試修) _[`issue #500`](http://issues.autojs6.com/500)_
* `修復` 佈局分析生成集合控制元件程式碼時可能包含無效語句的問題
* `修復` 佈局分析浮動視窗及其對話方塊的文字/控制元件在部分主題色或夜間模式下對比度不足的問題
* `修復` 在傳統佈局中選擇的材料設計顏色, 切換佈局後無法透過 "定位目前主題色" 找到, 且材料設計顏色頁面不顯示目前顏色摘要的問題
* `修復` 主頁外掛標籤頁及外掛中心關閉搜尋或切換標籤頁後仍保留搜尋條件的問題
* `修復` 外掛中心在 x86 裝置上誤選 x86_64 安裝包的問題
* `修復` 外掛授權成功後無法繼續完成啟用操作的問題
* `修復` 外掛中心無法正確解析部分月份縮寫日期的問題
* `修復` 外掛中心圖示快取, 夜間模式切換及下拉重新整理狀態異常
* `修復` 外掛中心重新整理時可能丟失已安裝外掛, 顯示空白列表或重置滾動位置的問題
* `修復` 外掛中心可能安裝錯誤變體或重複顯示打包模板及更新提示的問題
* `修復` 外掛中心重新整理索引成功後仍從快取恢復已移除或更名的舊外掛的問題
* `修復` 外掛中心冷啟動後立即開啟外掛詳情時, 對話方塊隨列表重新整理被整體重建而閃爍的問題
* `修復` 外掛中心在外掛暫時無法連線 (如更新安裝期間) 後持續保持其禁用的問題
* `修復` Shizuku 權限請求監聽器在銷燬後未正確取消註冊的問題
* `修復` 後臺安裝 APK 時無法繼續確認安裝的問題
* `修復` APK 打包取消時可能誤報構建失敗的問題
* `修復` APK 打包產物的簽名證書一致性校驗及 Android 9-11 簽名識別異常
* `修復` 打包應用或宿主缺少執行時元件時, shell 原生庫可能缺失或使用錯誤 ABI 導致應用崩潰的問題
* `修復` APK 打包時同名但內容不同的原生庫被靜默保留其中一份的問題 (現改為終止打包並列出來源)
* `修復` 打包的 UI 指令碼啟動時偶發顯示空白頁面後退出的問題
* `修復` VSCode 外掛儲存專案時已刪除檔案仍殘留在裝置或快取目錄中的問題 _[`vscode-ext issue #29`](http://vscext-project.autojs6.com/issues/29)_
* `修復` VSCode 外掛接收專案歸檔時可能因日誌處理導致記憶體不足的問題 _[`issue #523`](http://issues.autojs6.com/523)_
* `修復` VSCode 外掛的失效連線阻塞日誌傳送, 導致新連線無法接收日誌的問題 _[`issue #385`](http://issues.autojs6.com/385)_
* `修復` VSCode 外掛連線失敗時在已銷燬頁面顯示對話方塊可能出現異常的問題
* `修復` 客戶端模式成功連線過的地址未寫入歷史記錄的問題
* `修復` 檢查更新時未按裝置 ABI 優先順序選擇安裝包的問題
* `修復` IntelliJ IDEA 構建專案時可能無法解析模組依賴, 匹配編譯任務或定位目前 APK 產物的問題
* `修復` 多變體構建時 Kotlin 編譯記憶體不足的問題
* `修復` Gradle 構建時 SDK 解析及打包模板路徑解析異常
* `修復` cXy/cYx 的 false 模式未強制使用絕對座標, 橫向螢幕寬高比換算倒置及單獨設定基數後部分呼叫異常的問題
* `修復` setScaleBases 的第二個參數無效或基數已設定時, 提前修改另一方向並占用其設定機會的問題
* `修復` 介面重建與指令碼讀取螢幕尺寸同時進行時, device.width/height 及座標換算讀取到零尺寸的問題
* `修復` Node.js pinyin/pinyin4j 僅轉換少量內建字典文字的問題, 現使用已安裝的拼音外掛程式
* `修復` Node.js 控制台視窗和樣式控制方法呼叫後沒有實際效果的問題
* `修復` Bun 指令碼結束時同步結果早於輸出回呼導致末尾日誌遺失的問題
* `修復` 錄製授權呼叫端被遮擋時請求提前結束, 取消後系統對話框殘留及指定擷取尺寸失效的問題
* `修復` 拼音 style/mode 數字常數轉換為 JSON 或 Rhino 數值後無法識別的問題
* `修復` 螢幕擷取跳過尚未合成的全透明投影緩衝, 保留正常黑色畫面
* `最佳化` 延遲初始化非關鍵元件以提升應用啟動速度
* `最佳化` 應用啟動時不再自動彈出權限申請對話方塊, 缺少所有檔案管理權限時改為在主頁顯示可直接授權的提醒
* `最佳化` 應用及打包應用適配 Android 17, 完善本地網路授權, 高階保護及後臺音訊限制的提示
* `最佳化` 應用及打包應用支援 16 KB 記憶體頁, 並在外掛安裝, 載入及打包時提示不相容的原生庫
* `最佳化` 完善介面及異常訊息的多語言顯示
* `最佳化` 精簡安裝包內的依賴, 語言資源及影像資源以縮減檔案體積
* `最佳化` 檢查更新透過 GitHub REST API 獲取版本與安裝包資訊, 更新說明支援本地渲染
* `最佳化` 應用內建簡體中文及英文發行歷史, 其他語言支援線上獲取與快取 (離線時優先使用快取, 無快取時顯示英文)
* `最佳化` 外掛化離線專案文件 (Offline Docs 外掛), 支援獨立安裝及更新, 文件來源切換即時生效
* `最佳化` 外掛化打包模板 (APK Builder Template 外掛), 打包時校驗外掛簽名, 宿主版本及模板相容性
* `最佳化` 外掛化 ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization 相關功能
* `最佳化` 外掛化 OpenCC/Pinyin/Pinyin4j/MediaInfo 相關功能
* `最佳化` 官方外掛的主題色, 夜間模式及語言支援跟隨宿主設定
* `最佳化` 外掛中心完善線上安裝與更新, 變體選擇, 外掛設定, 使用說明及發行歷史顯示
* `最佳化` 外掛中心清單圖示按安裝狀態回應短按/長按: 已安裝時開啟系統應用程式詳細資訊/獨立啟動頁面 (無啟動頁面時提示), 未安裝時開啟外掛詳細資訊/啟動安裝
* `最佳化` 首頁外掛分頁 FAB 選單優先顯示安裝精靈, 官方外掛庫, 第三方外掛庫及外掛中心, URL/本機檔案安裝及管理操作收納至 "更多"
* `最佳化` 外掛中心支援二級排序及各級獨立升降序, 預設按已安裝 > 已禁用 > 未安裝排序, 同狀態內按最近更新時間降序排列
* `最佳化` 外掛中心主動操作及修改外掛設定後的條目保持原位並更新狀態, 後臺自動重新整理照常執行, 下拉重新整理或調整排序/篩選後恢復正常排列
* `最佳化` 外掛中心支援 SDK 外掛自動掛載及可用 ABI 檢測
* `最佳化` 外掛中心全新安裝官方外掛後支援自動啟用, 外部安裝及覆蓋安裝保留原有啟用策略
* `最佳化` 外掛中心採用快取與非同步載入提升列表顯示及重新整理流暢度, 外掛啟用失敗時提供恢復提示
* `最佳化` 主頁外掛標籤頁浮動按鈕支援長按進入外掛中心
* `最佳化` 外掛資訊對話方塊分別顯示外掛支援的 ABI 及目前安裝包的 ABI
* `最佳化` 快取 Rhino 執行時初始化資料以提升指令碼啟動效率, 並提升大型陣列列印效率及低版本安卓系統的 Java 反射相容性
* `最佳化` Rhino 引擎同步上游, 支援 new.target, 並改進作用域, TypedArray 與偵錯程式相容性
* `最佳化` runtime.loadJar 方法支援藉助 DEX Compiler 外掛載入 JAR 檔案, 並提供編譯診斷與失敗回退 (實驗性, 預設關閉)
* `最佳化` 定時任務, 廣播任務, 快捷方式及 Tasker 支援執行 TypeScript 指令碼 (需要 TypeScript Engine 外掛)
* `最佳化` pinyin 模組支援呼叫級自定義讀音詞典, 並完善 compare/compact 方法
* `最佳化` pinyin4j 模組支援多種羅馬字方案及完整讀音查詢
* `最佳化` opencc 模組支援轉換型別查詢, 批次轉換及鏈式轉換
* `最佳化` ocr 模組預設按 ML Kit/Paddle/Rapid 順序選擇可用引擎, 並支援顯式指定或恢復自動選擇
* `最佳化` Node.js 外掛程式等經宿主橋接呼叫的 ocr 模組在未指定 engine 時依 ML Kit/Paddle/Rapid 順序自動選擇已啟用的引擎, 不再固定要求 ML Kit OCR 外掛程式
* `最佳化` 宿主橋接層 accessibility 模組增加 captureScreenImage 方法, 以無障礙截圖 (不可用時回退螢幕擷取) 產生工作階段內圖像控制代碼, 供 MCP Server 等外掛程式工作階段直接用於條碼辨識與找圖找色; 外掛程式工作階段可同時持有的圖像控制代碼數由 4 提高為並行呼叫數的兩倍
* `最佳化` MCP Server 與 3-Stove Agent 等面向模型的外掛程式工作階段經橋接讀取圖片 (image.readImage) 時, 最長邊超過 4096 px 的圖片按 2 的冪降採樣解碼, 結果附 scale 與原圖尺寸, 避免相簿大圖耗盡宿主記憶體; Node.js 外掛程式指令碼不受影響
* `最佳化` MCP Server 與 3-Stove Agent 等面向模型的外掛程式工作階段經橋接按路徑讀取圖片與媒體資訊 (image.readImage, mediainfo) 時限制在工作目錄內, 寫入 (image.saveImage) 同樣只限工作目錄; 請求宣告 files.shared_read 且 AutoJs6 持有所有檔案存取權限時可唯讀存取共用儲存空間 (不含 Android/data 與 Android/obb), files.list 與 files.stat 也可按同一權杖列出共用儲存空間; Node.js 外掛程式指令碼不受影響
* `最佳化` 宿主橋接層 app 模組增加 pluginCapabilities 方法, 即時回傳 OCR, 條碼, 拼音, Pinyin4j, MediaInfo, OpenCV 與離線文件各外掛程式家族的狀態 (available / not_installed / disabled / incompatible), 已安裝外掛程式的版本, 啟用與授權情況, 對應的橋接方法以及外掛程式中心入口, 供 MCP Server 等外掛程式工作階段按安裝情況顯示工具並提示使用者安裝; 外掛程式工作階段開啟時的可選方法清單也按同一邏輯補充已可用家族的方法
* `最佳化` MCP Server 外掛程式工作階段的預設能力授權擴充: 加入 ocr, barcode, pinyin, pinyin4j, mediainfo 各模組的方法, image.readImage 與找圖找色方法, accessibility.readScreenText / captureScreenImage 和 app.pluginCapabilities, 以及對應的權限權杖 (含共用儲存空間唯讀的 files.shared_read); 這些方法只在相應外掛程式已安裝並啟用時可用, image.saveImage 與 image.toBytes 仍不在授權內; 3-Stove Agent 的授權不變; 另加入 agent.listScripts / readManifest / execRegistered 與 agent, agent.exec 權杖, 供 MCP Server 列出並執行已登記指令碼
* `最佳化` images.save/saveImage/compress/compressToBytes 方法支援 PNG 量化選項 (保留透明度) 及畫素與記憶體限制 (需要 Image Quantization 外掛)
* `最佳化` images.readPixels 方法支援 ImageWrapper 參數
* `最佳化` images.matchTemplate/findPointByImage 方法支援 scales 選項, 匹配結果增加尺寸, 比例及區域資訊
* `最佳化` images.matchFeatures 方法支援影像或路徑參數及更多匹配選項, 並補充特徵與結果資訊
* `最佳化` 多點找色, 模板匹配及特徵匹配提升執行效率與結果準確性
* `最佳化` 螢幕截圖支援暫時無可用幀時重試, 並減少截圖資源釋放對併發影像操作的影響
* `最佳化` mediainfo 模組支援 snapshot/capabilities 方法及媒體流計數, 並減少檔案詳情查詢耗時
* `最佳化` KeyCode 方法提升參數相容性並支援使用 Shizuku 權限傳送鍵碼指令 _[`issue #492`](http://issues.autojs6.com/492)_
* `最佳化` 無障礙服務支援使用 Shizuku 自動啟用, 並支援與官方伴生外掛同步啟停狀態
* `最佳化` auto.registerEvent 回呼的 source 節點改為按需解析 (Android 13 以下需在回呼內讀取)
* `最佳化` 無障礙視窗查詢跳過不可用根節點, 並完善不同安卓版本的節點回收策略
* `最佳化` automator.captureScreen 方法完善資源回收及系統限頻重試, 避免截圖請求長時間未結束
* `最佳化` 全域選擇器與集合選擇器的整數參數統一四捨五入, 並提升多執行緒查詢及超時中斷的穩定性
* `最佳化` 選擇器及 Flow 等待支援事件輔助輪詢, 無障礙事件到達時提前重試 (透過 auto.setFlags 的 eventAssistedPolling 標誌啟用)
* `最佳化` 恢復 console.rawInput/input 方法, 支援透過控制檯輸入欄讀取文字或求值表示式
* `最佳化` 各控制檯分別記憶時間顯示及格式, 同名指令碼重新執行時替換原有浮動視窗
* `最佳化` media.playMusic 方法支援前臺播放, 音訊焦點及通知欄控制, 並在指令碼結束時清理播放資源
* `最佳化` 移除內建的 com.stardust 相容類副本, 指令碼以 com.stardust 字首引用的類名改為對映到 AutoJs6 的同名實現 (218 個類完整對映, 90 個類部分對映, 其餘 46 個無對應物的類不再提供), 使 new 與 instanceof 等按類名的用法與 runtime 物件一致 (參閱 專案文件 > [AutoJs4 執行時](https://docs.autojs6.com/#/autojs4Runtime))
* `最佳化` 範例程式碼增加 3-Block Compose, 自動化流程, 控制檯互動及電子書操作範例, 並完善螢幕 OCR 範例
* `最佳化` 控制檯浮動視窗增加統一操作選單, 支援調整大小及配置時間, 著色, 字號與透明度
* `最佳化` 程式碼編輯器統一主題配色及選單佈局, 並完善字型, 只讀模式與文字選擇體驗
* `最佳化` 程式碼編輯器執行指令碼後將執行按鈕切換為停止按鈕
* `最佳化` 程式碼編輯器完善字型下載提示, 跳轉行號校正及檔案編碼檢測與儲存
* `最佳化` 程式碼編輯器開啟檔案時的頁面顯示速度
* `最佳化` 檔案管理器安裝包資訊按需載入, 完善大型安裝包及清單資訊展示
* `最佳化` 檔案管理器統一檔案預覽及外掛操作入口, 列表按差異重新整理並保留滾動位置
* `最佳化` 高權限檔案編輯備份支援集中檢視, 復原及清理, 可依每個檔案的數量和空間目標自動保留 (預設最多 5 份, 空間目標 64 MiB, 至少保留最新 1 份), 正在使用, 內容已變更或結果未確認的復原資料不會自動清理
* `最佳化` 檔案管理器透過其他應用開啟一般可讀檔案時優先原地讀取, 受限檔案的唯讀按需讀取支援大於 1 GiB 檔案及影片跳轉 (需要系統支援); 相容方式完整複製前顯示所需及可用暫存空間並要求確認, 複製時顯示進度且支援取消
* `最佳化` 版本歷史避免重複快照, 檔案覆蓋前保留原有內容, 回收站恢復到新路徑時同步遷移版本歷史
* `最佳化` 回收站完善容量提示, 損壞條目恢復與錯誤提示, 列表按差異重新整理
* `最佳化` 佈局層次分析支援屬性摘要, 父級高亮, 橫向滾動及逐級選擇父控制元件
* `最佳化` 佈局層次分析頁面去除層級彩色豎線, 展開/收起標識移至控制元件文字左側並區分可展開, 已展開及無子節點三種狀態
* `最佳化` 佈局分析節點資訊對話方塊以主題色標識多選項, 複製方式支援 "總是詢問/複製物件選擇器/複製鏈式選擇器" 並跨工作階段記憶
* `最佳化` 無障礙服務及連線管理器統一分組佈局, 支援逐級展開, 收起及獨立設定
* `最佳化` 任務列表及指令碼執行日誌使用工作目錄等路徑簡稱, 並保留實際入口檔名與副檔名
* `最佳化` 浮動按鈕及相關視窗跟隨應用語言與夜間模式, 選單資訊使用後臺載入
* `最佳化` 主頁抽屜的 "連線到電腦" 分組更名為 "遠端連線"
* `最佳化` 預設顏色庫的亮色與暗色視窗背景合併為 "背景色" 一項, 可選擇亮色, 暗色, 自動跟隨或自動反色, 升級前的選擇保持不變
* `最佳化` 主題色設定頁面支援定位自建顏色庫中的顏色及目前智慧顏色庫, 搜尋範圍擴充到自建顏色庫的顏色及智慧顏色庫的組
* `最佳化` APK 安裝改由 3-Setup Installer 外掛提供安裝對話方塊, 外掛不可用時交給系統安裝器並提示安裝外掛; apks/xapk/apkm/apkz 分包容器需要該外掛才能安裝
* `最佳化` VSCode 外掛連線增加心跳檢測及資料長度校驗, 避免失效連線與異常資料影響通訊 _[`issue #333`](http://issues.autojs6.com/333)_ _[`issue #523`](http://issues.autojs6.com/523)_
* `最佳化` VSCode 外掛儲存檔案時支援按相對目錄結構儲存到工作目錄 _[`vscode-ext issue #37`](http://vscext-project.autojs6.com/issues/37)_
* `最佳化` 客戶端模式二維碼連線提升識別效率並調整入口至地址對話方塊選單
* `最佳化` Gradle 構建支援動態匹配 Kotlin/KSP/AGP/R8 相容版本, 並降低啟動記憶體佔用及清理編譯警告
* `最佳化` Gradle 平臺相容資料及更新任務遷移至 AutoJs6-Gradle-Platform-Versions, Scrapers 工具支援跨倉庫同步
* `最佳化` device.getAndroidId 支援 useSystemId 布林參數, 在 Android 8.0+ 上透過 Shizuku/Root 獲取執行應用所屬 Android 使用者的系統值, 權限或讀取失敗時丟擲異常, 舊系統忽略此參數
* `最佳化` cX/cY/cXy/cYx 支援明確指定 auto 模式, 統一驗證座標和基數, 並避免有限大數換算被截斷至 Long 範圍
* `依賴` 模組化 Android AXML
* `依賴` 模組化 Android ARSC Editor
* `依賴` 模組化 APK Signer
* `依賴` 模組化 APK Verifier
* `依賴` 模組化 ARSCLib 版本 1.3.8
* `依賴` 模組化 Image Quantization 版本 2.17.0
* `依賴` 模組化 OpenCV 版本 4.8.0
* `依賴` 附加 juniversalchardet 版本 2.0.4
* `依賴` 附加 AndroidX AsyncLayoutInflater 版本 1.1.0
* `依賴` 附加 AndroidX Test Ext JUnit 版本 1.3.0
* `依賴` 附加 ZXing Android Embedded 版本 4.3.0
* `依賴` 附加 Android Hidden API Bypass 版本 6.1
* `依賴` 附加 NDK 版本 28.2.13676358 與 CMake 版本 3.22.1, 用於建置高權限檔案操作的原生元件
* `依賴` 升級 Android SDK 版本 36 -> 37
* `依賴` 升級 Gradle 9 版本 9.4.0 -> 9.7.1
* `依賴` 升級 AndroidX Lifecycle 版本 2.6.2 -> 2.9.4
* `依賴` 升級 AndroidX SavedState 版本 1.2.1 -> 1.3.2
* `依賴` 升級 AndroidX Emoji2 版本 1.3.0 -> 1.4.0
* `依賴` 升級 AndroidX Window 版本 1.0.0 -> 1.5.0
* `依賴` 移除 Android Annotations 版本 4.8.0
* `依賴` 移除 Android dx 版本 1.14
* `依賴` 移除 AndroidX AppCompat (Legacy) 版本 1.0.2
* `依賴` 移除 AndroidX MultiDex 版本 2.0.1
* `依賴` 移除 Apache Commons Lang3 版本 3.18.0
* `依賴` 移除 APK Parser 本地模組
* `依賴` 移除 Commons IO 版本 2.8.0
* `依賴` 移除 Flexmark Java HTML to Markdown 版本 0.64.8
* `依賴` 移除 GitHub API 版本 1.306
* `依賴` 移除 Glide KSP 版本 5.0.5
* `依賴` 移除 ICU4J 版本 77.1
* `依賴` 移除 Jackson DataBind 版本 2.13.4.2
* `依賴` 移除 Jaredrummler Android Device Names 版本 2.1.1
* `依賴` 移除 JavaMail for Android 本地庫
* `依賴` 移除 JCIP Annotations 版本 1.0
* `依賴` 移除 Jieba Analysis 版本 1.0.3-SNAPSHOT (modified)
* `依賴` 移除 JUnit Jupiter 版本 5.13.4
* `依賴` 移除 Log4j 版本 1.2.17
* `依賴` 移除 Android Logging Log4j 版本 1.0.3
* `依賴` 移除 MLKit Barcode Scanning 版本 17.3.0
* `依賴` 移除 MLKit Text Recognition Chinese 版本 16.0.1
* `依賴` 移除 OpenCC 版本 1.2.2
* `依賴` 移除 Pinyin4j 版本 2.5.1
* `依賴` 移除 Rapid OCR 本地庫
* `依賴` 移除 Retrofit2 Converter Gson 版本 3.0.0
* `依賴` 移除 Retrofit2 Kotlin Coroutines Adapter 版本 0.9.2
* `依賴` 移除 Spongy Castle Prov 版本 1.58.0.0
* `依賴` 移除 Tiny Sign 版本 0.9

# v6.7.0

###### 2026/03/14

* `新增` 外掛中心功能, 支援外掛的安裝/解除安裝/更新等操作 (入口: 主頁抽屜按鈕/主頁標籤頁)
* `新增` 設定頁面啟動器快捷方式增加 "外掛" 選項, 用於透過快捷方式跳轉到外掛中心頁面
* `新增` 版本歷史功能, 支援檢視/恢復可編輯檔案的歷史版本 (入口: 主頁抽屜按鈕/檔案管理器選單/程式碼編輯器選單)
* `新增` 程式碼編輯器增加 "多功能鍵盤" > "符號設定" 選單項, 支援符號編輯及按配置級別建立/匯入/匯出符號
* `新增` 回收站功能, 支援檢視/恢復已刪除的檔案/資料夾 (入口: 主頁抽屜按鈕)
* `新增` Paddle OCR (PP-OCRv5) 外掛, 用於光學字元識別
* `新增` cvt 模組, 用於資料單位轉換 (參閱 專案文件 > [單位轉換](https://docs.autojs6.com/#/cvt))
* `新增` cvt.bytes 方法, 用於位元組資料單位轉換 (參閱 專案文件 > [單位轉換](https://docs.autojs6.com/#/cvt))
* `新增` fmt 模組, 用於資料格式化 (參閱 專案文件 > [格式化](https://docs.autojs6.com/#/fmt))
* `新增` fmt.bytes 方法, 用於位元組資料格式化 (參閱 專案文件 > [格式化](https://docs.autojs6.com/#/fmt))
* `新增` zip 模組, 用於檔案壓縮與解壓縮操作 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (參閱 專案文件 > [Zip](https://docs.autojs6.com/#/zip))
* `新增` mediainfo 模組, 用於檢視媒體檔案的詳細資訊 (參閱 專案文件 > [媒體資訊](https://docs.autojs6.com/#/mediainfo))
* `新增` s13n.bytes 方法, 用於標準化位元組資料 (參閱 專案文件 > [標準化](https://docs.autojs6.com/#/s13n))
* `新增` app.isDualInstalled 方法, 用於檢測雙開應用是否已安裝 (需要 Shizuku 或 Root 權限) _[`issue #450`](http://issues.autojs6.com/450)_
* `新增` device.getSharedDeviceId 方法, 用於跨應用獲取統一共享裝置 ID _[`issue #455`](http://issues.autojs6.com/455)_
* `新增` device.setPointerLocation 等 Toggleable 系列方法, 用於設定或獲取指標位置系統設定項 _[`issue #381`](http://issues.autojs6.com/381)_
* `新增` dialogs.build 方法支援 textAllCaps/(positive/negative/neutral)TextAllCaps 選項參數, 用於控制按鈕文字是否全部大寫
* `新增` images.loadAsync 方法, 用於非同步獲取網路影像資源 _[`issue #327`](http://issues.autojs6.com/327)_
* `新增` ui.getNavigationBarHeight 方法/navigationBarHeight 屬性 (getter), 用於獲取導航欄高度 _[`issue #456`](http://issues.autojs6.com/456)_
* `新增` ui.getVisible(Status/Navigation)BarHeight 方法/visible(Status/Navigation)BarHeight 屬性 (getter), 用於獲取狀態列或導航欄的可見高度 _[`issue #456`](http://issues.autojs6.com/456)_
* `新增` http.put/del(ete)/head 方法, 用於傳送不同型別的 HTTP 請求
* `新增` http.getAsync/postAsync/requestAsync 等方法, 用於非同步傳送網路請求 _[`issue #423`](http://issues.autojs6.com/423)_
* `新增` http 模組請求相關方法獲取的 body 物件增加 stream/saveToFile/close 等方法 _[`issue #452`](http://issues.autojs6.com/452)_
* `新增` http 模組請求相關方法支援快取控制選項參數 (cacheBody/bodyCacheThresholdBytes)
* `新增` http 模組請求相關方法支援不安全選項參數 (isInsecure/insecure), 用於忽略證書相關異常 _[`issue #417`](http://issues.autojs6.com/417)_
* `新增` http 模組請求相關方法支援 options.client 選項, 用於配置 OkHttpClient.Builder (如 followRedirects 等) _[`issue #454`](http://issues.autojs6.com/454)_
* `新增` auto.state 屬性 (getter) 及 shizuku.state 屬性 (getter), 用於獲取無障礙服務狀態及 Shizuku 服務狀態
* `新增` UiObject#isShifted 方法, 用於檢測控制元件位置變化 _[`issue #469`](http://issues.autojs6.com/469)_
* `新增` UiObject#snapshot 方法, 用於捕獲控制元件目前狀態 _[`issue #469`](http://issues.autojs6.com/469)_
* `新增` 等價系列選擇器 (UiSelector#id/text/...) 及包含系列選擇器 (UiSelector#xxxContains) 支援正規表示式參數
* `新增` 選擇器的正規表示式參數支援使用標誌 (i, m, s, u)
* `新增` runtime.(set/is)JavaPrimitiveWrap 方法, 用於設定或獲取 Java 原始型別包裝策略 _[`issue #435`](http://issues.autojs6.com/435)_
* `新增` autojs.restart/exit 方法, 用於重啟或退出 AutoJs6, 並支援重啟後執行指定指令碼 _[`issue #460`](http://issues.autojs6.com/460)_
* `新增` engines.myEngine().getStartTime() 方法, 用於獲取指令碼引擎啟動時間戳 _[`issue #449`](http://issues.autojs6.com/449)_
* `新增` crash 全域方法, 用於測試崩潰報告頁面
* `新增` structuredClone 全域方法, 用於深複製 JavaScript 物件 (參閱 [MDN](https://developer.mozilla.org/zh-CN/docs/Web/API/Window/structuredClone))
* `新增` 正規表示式支援後瞻斷言語法 _[`issue #464`](http://issues.autojs6.com/464)_
* `新增` 檔案管理器增加 "移動到" 及 "複製到" 選單項, 支援操作中止及進度狀態顯示
* `新增` 主頁抽屜增加 "指標位置" 工具
* `新增` 主頁抽屜增加 "所有檔案管理權限" 開關
* `新增` 主頁抽屜增加 "後臺彈出介面" 開關 (針對小米/Vivo 裝置)
* `新增` 設定頁面增加 "Java 原始型別包裝" 設定選項 _[`issue #435`](http://issues.autojs6.com/435)_
* `新增` 設定頁面增加 "定時任務排程引擎" 設定選項, 支援 AlarmManager/WorkManager/JobScheduler _[`issue #457`](http://issues.autojs6.com/457)_ _[`issue #434`](http://issues.autojs6.com/434)_ _[`issue #449`](http://issues.autojs6.com/449)_ _[`issue #388`](http://issues.autojs6.com/388)_ _[`issue #378`](http://issues.autojs6.com/378)_ _[`issue #163`](http://issues.autojs6.com/163)_ _[`issue #53`](http://issues.autojs6.com/53)_ _[`issue #21`](http://issues.autojs6.com/21)_
* `新增` 設定頁面增加 "應用啟動器圖示" 設定選項, 支援自適應圖示/透明背景圖示 _[`issue #405`](http://issues.autojs6.com/405)_
* `新增` 設定頁面增加 "重啟策略" 設定選項, 用於配置主頁抽屜重啟按鈕是否使用快速重啟
* `新增` 設定頁面增加 "螢幕捕獲權限申請延遲" 設定選項, 用於避免權限申請視窗遮擋螢幕
* `新增` 打包應用支援開機自啟動並在配置頁面增加開關配置
* `新增` 打包應用支援應用快捷方式 (日誌頁面/設定頁面)
* `新增` 打包應用配置頁面增加撤銷/重做/儲存按鈕
* `新增` 打包應用配置頁面支援配置的建立/修改/刪除/匯入/匯出 _[`issue #483`](http://issues.autojs6.com/483)_
* `新增` Scrapers 工具 (run-scrapers.mjs), 用於自動更新 Gradle 構建資料及 README 通用資料與模板
* `新增` Gradle Wrapper 切換工具 (gradle-wrapper-switcher), 用於切換本地 Gradle 主版本 (如 Gradle 8.x 與 Gradle 9.x)
* `修復` notice 方法參數數量及型別限制錯誤 _[`issue #462`](http://issues.autojs6.com/462)_
* `修復` ui.R 屬性 (getter) 無法正常使用的問題
* `修復` ui.post 方法回呼參數中產生異常時可能靜默失敗的問題
* `修復` isJavaClass/isJavaPackage 等全域方法無效的問題
* `修復` timers.keepAlive 方法 timeout 參數功能無效的問題
* `修復` floaty.window/rawWindow 方法無法接受字串參數的問題 _[`issue #467`](http://issues.autojs6.com/467)_
* `修復` floaty.rawWindow 因傳入錯誤的上下文導致部分控制元件顯示異常的問題
* `修復` util.isString 方法無效的問題
* `修復` util.class[Name]/getClass[Name] 可能返回錯誤結果的問題
* `修復` util.java.array 無法正常使用 Boolean 等型別參數的問題
* `修復` threads.disposable() 返回的物件在存取資料時可能被意外裝箱的問題 _[`issue #435`](http://issues.autojs6.com/435)_
* `修復` console.build 方法多次呼叫時, 日誌浮動視窗樣式選項未能正常重置的問題
* `修復` console.build 方法的 (title/content)BackgroundColor 選項導致透明度或著色選項被覆蓋的問題 _[`issue #458`](http://issues.autojs6.com/458)_
* `修復` 服務端模式連線時, 旋轉螢幕及切換語言等觸發 Activity 重建的操作導致 VSCode 控制檯無法輸出日誌的問題 _[`issue #385`](http://issues.autojs6.com/385)_
* `修復` console/toast 等方法顯示 Java Double 時可能丟失末尾零的問題
* `修復` 無法使用 console/toast 等方法顯示 BigInt 資料型別的問題
* `修復` toast 方法因持有 Toast 實例強引用而可能導致記憶體洩漏的問題
* `修復` 部分全域物件可能丟失 JavaScript 原型屬性及方法的問題
* `修復` 使用 XML 語法將 JavaScript 表示式作為屬性值時, this 物件可能出現指向錯誤的問題
* `修復` dialogs.build 方法 linkify 選項參數功能失效的問題
* `修復` dialogs.build 方法可能破壞物件參數原有資料的問題
* `修復` canvas 元素控制元件 setMaxFps 方法內部幀率計算錯誤
* `修復` images.concat 方法縱向拼接時寬度值計算錯誤
* `修復` images.requestScreenCapture 方法呼叫時使用者取消授權可能導致應用崩潰的問題
* `修復` images.captureScreen 方法獲取的截圖可能存在方向錯誤或黑邊填充的問題 _[`issue #337`](http://issues.autojs6.com/337)_
* `修復` images 部分方法使用區域參數時 1 被誤解析為百分比的問題
* `修復` images 部分相關方法出現異常時 oneShot 標記功能失效的問題 _[`issue #372`](http://issues.autojs6.com/372)_
* `修復` images 部分相關方法可能引發記憶體洩漏的問題 _[`issue #372`](http://issues.autojs6.com/372)_
* `修復` images.save 及 ImageWrapper#saveTo 方法傳入路徑不存在時無法正常寫入檔案的問題
* `修復` ImageWrapper#recycle 方法回收影像資源功能失效的問題
* `修復` ocr 部分多載方法可能無法正常使用的問題
* `修復` ocr.detect 方法獲得的結果可能與 ocr.mode 不匹配的問題 _[`issue #468`](http://issues.autojs6.com/468)_
* `修復` 部分裝置無法正常初始化 ML Kit OCR 的問題 (試修) _[`issue #8`](http://issues.autojs6.com/8#issuecomment-3117061768)_
* `修復` auto.registerEvent 註冊的無障礙服務事件會被其他指令碼誤清理的問題 _[`issue #466`](http://issues.autojs6.com/466)_ _[`issue #343`](http://issues.autojs6.com/343#issuecomment-3263953918)_
* `修復` Android 10 UiObject#child 方法可能出現 ArrayIndexOutOfBoundsException 異常的問題 _[`issue #416`](http://issues.autojs6.com/416)_
* `修復` 內建模組方法呼叫時 Rhino 上下文可能不一致的問題
* `修復` 指令碼建立大量浮動視窗後, 指令碼退出時可能出現視窗殘留的問題
* `修復` 執行專案時 project.json 配置參數可能無法正常解析的問題
* `修復` 專案打包時 project.json 的 excludedDirs 配置參數將導致配置檔案解析失敗的問題 _[`issue #428`](http://issues.autojs6.com/428)_
* `修復` 打包應用無法正常使用 Paddle OCR 與 Rapid OCR 功能的問題
* `修復` 打包應用配置檔案 project.json 中的 key 值可能被意外修改的問題
* `修復` 打包應用配置頁面滑動 "需要的權限" 列表時可能出現核取方塊圖示閃爍的問題
* `修復` Android 7.x 可能無法正常使用打包功能的問題
* `修復` 指令碼專案配置檔案儲存時原始鍵名可能會被修改的問題
* `修復` 指令碼專案配置檔案中構建版本號或構建時間出現較大數字時可能導致應用崩潰的問題
* `修復` 頻繁獲取或重建 ImageReader 時可能因緩衝區暫無可用幀導致應用崩潰的問題
* `修復` 輸入事件觀察器 InputEventObserver 可能導致應用啟動時明顯示卡頓的問題
* `修復` Shizuku 使用者服務行程未能正常結束導致行程堆積的問題 _[`issue #474`](http://issues.autojs6.com/474)_
* `修復` 發行歷史頁面部分系統因字型差別導致統計資料顯示不完整的問題
* `修復` 部分裝置無法正常觸發檔案管理器功能按鈕點選事件的問題 (試修) _[`issue #465`](http://issues.autojs6.com/465)_
* `修復` 檔案管理器搜尋結果展示後點選返回按鈕或切換標籤頁面時可能導致應用崩潰的問題
* `修復` 檔案管理器刪除專案資料夾後 UI 未能自動重新整理的問題
* `修復` 部分裝置程式碼編輯器空行顯示方框字元的問題 (試修)
* `修復` 程式碼編輯器軟鍵盤可能遮擋文字內容的問題
* `修復` 程式碼編輯器在只讀模式下依然可以編輯程式碼內容的問題
* `修復` 程式碼編輯器在只讀模式下點選標題區域及部分選單項導致應用崩潰的問題
* `修復` 程式碼編輯器載入大檔案可能導致應用崩潰的問題 (試修)
* `修復` 程式碼編輯器自動開啟新建檔案時功能按鈕狀態初始化異常
* `修復` 程式碼編輯器編輯檔案時置於後臺可能導致應用崩潰的問題
* `修復` Android 15+ 程式碼編輯器點選 fx 按鈕無法顯示模組函式快捷面板的問題 (試修)
* `修復` ErrorDialogActivity 可能無法正常啟動或短時間自動消失的問題 _[`issue #479`](http://issues.autojs6.com/479)_ _[`issue #471`](http://issues.autojs6.com/471)_ _[`issue #414`](http://issues.autojs6.com/414)_ _[`issue #340`](http://issues.autojs6.com/340#issuecomment-2973485826)_
* `修復` Canvas 建構函式可接受的參數型別錯誤 _[`issue #402`](http://issues.autojs6.com/402)_
* `修復` 崩潰報告頁面複製詳細資訊功能失效的問題
* `修復` Android 16+ 自定義返回邏輯失效導致返回功能異常的問題
* `修復` 任務面板列表項頻繁變動時可能導致應用崩潰的問題
* `修復` 使用 "三按鈕" 手勢導航時導航欄前景色可能與夜間模式關聯異常的問題
* `修復` APK 檔案型別資訊對話方塊可能無法獲取應用名稱及 SDK 資訊的問題
* `修復` 客戶端模式連線後, 短時間斷開連線並再次連線時, 出現握手超時且難以再次建立連線的問題
* `修復` 客戶端模式連線時, 旋轉螢幕及切換語言等觸發 Activity 重建的操作導致 AutoJs6 總是重建連線的問題
* `修復` 連線 VSCode 外掛時, 多種方式同時連線可能導致日誌列印數量成倍增加的問題
* `修復` 佈局分析頁面生成程式碼時對於集合控制元件可能生成失敗的問題 (試修) _[`issue #328`](http://issues.autojs6.com/328)_
* `修復` 設定頁面 "使用 '音量減' 鍵控制錄製" 開關功能失效的問題 _[`issue #480`](http://issues.autojs6.com/480)_
* `修復` 小米裝置 "顯示在其他應用上層" 開關可能跳轉到錯誤設定頁面的問題
* `修復` 錄製指令碼生成的程式碼檔案可能出現座標數值與螢幕實際畫素值不匹配的問題 _[`issue #480`](http://issues.autojs6.com/480)_
* `修復` 錄製指令碼生成的程式碼檔案首個行為 (如點選或滑動等) 總是被忽略的問題
* `修復` 浮動按鈕 "執行指令碼" 對話方塊後臺操作檔案時可能導致應用崩潰的問題
* `修復` 主頁活動頁面生命週期結束後重新進入主頁時, 浮動按鈕狀態可能被重置的問題
* `修復` 檢查更新中止下載過程後, 網路資料包依然持續後臺獲取的問題 (試修)
* `修復` 構建工具啟用 isCleanup[Paddle/Rapid]Ocr 配置選項時無法正常完成 Rebuild Project 任務的問題
* `最佳化` 外掛化 Paddle OCR (PP-OCRv3) 相關功能 _[`issue #334`](http://issues.autojs6.com/334)_ _[`issue #331`](http://issues.autojs6.com/331)_ _[`issue #325`](http://issues.autojs6.com/325)_
* `最佳化` runtime.loadJar 方法增強相容性 (by [LYS86](https://github.com/LYS86)) _[`pr #475`](http://pr.autojs6.com/475)_
* `最佳化` ui.getStatusBarHeight 方法/statusBarHeight 屬性 (getter) 增強一定程度的相容性
* `最佳化` console.log 等方法列印全域物件 (images, app, ocr 等) 時支援顯示詳細資訊
* `最佳化` 控制檯相關方法列印巨型資料時根據上限適當省略部分內容以降低列印耗時
* `最佳化` 控制檯浮動視窗倒計時起始值由 6 秒增加到 9 秒
* `最佳化` 控制檯浮動視窗內部實現進行無鎖化及佇列化處理以提升其參數設定效率與成功率
* `最佳化` 控制檯浮動視窗背景色彩行為相關 API (透明度/著色/基色) 更符合安卓設計規範 _[`issue #458`](http://issues.autojs6.com/458)_
* `最佳化` dialogs.build 方法支援使用 MaterialDialog.Builder 原生選項參數
* `最佳化` dialogs.build 方法 animation/linkify 選項參數支援字串寬鬆匹配
* `最佳化` device.rotation/orientation/width/height 屬性獲取螢幕方向及寬高資料的穩定性與一致性
* `最佳化` app.launchDual/startDualActivity 等方法提升執行效率並增強相容性
* `最佳化` android.graphics.Paint#setColor 支援正常解析 ColorInt/ColorHex/ColorName 等顏色參數
* `最佳化` 內建模組相關方法引數型別的異常訊息增加型別摘要資訊
* `最佳化` 內建模組 (dayjs/cheerio/i18n) 改用全域懶載入以替代指令碼執行模式
* `最佳化` 檔案管理器浮動按鈕展開後點選選單項時最佳化選單收起時機
* `最佳化` 檔案管理器/任務面板支援顯示檔案/任務數量統計資訊
* `最佳化` 程式碼編輯器儲存檔案失敗時自動存為草稿並支援另存為新檔案
* `最佳化` 程式碼編輯器載入大檔案時提升一定程度的流暢度
* `最佳化` 程式碼編輯器 "查詢/替換" 支援狀態持久化及實時顯示搜尋計數資訊
* `最佳化` 程式碼編輯器提示儲存時確保儲存成功後再退出編輯器以降低儲存失敗率
* `最佳化` 程式碼編輯器儲存按鈕的狀態更符合使用者主觀邏輯
* `最佳化` 程式碼編輯器支援 "-", "=", "(", "[", "{", "<" 等按鍵長按功能
* `最佳化` 打包應用打包過程對話方塊增加 "中止" 按鈕
* `最佳化` 打包應用打包過程對話方塊的顯示方式並增加耗時統計
* `最佳化` 打包應用使用新配置安裝時將覆蓋舊應用的相關配置
* `最佳化` 打包應用配置頁面預設勾選必要權限 (WAKE_LOCK/INTERNET/WRITE_EXTERNAL_STORAGE/...) _[`issue #397`](http://issues.autojs6.com/397)_
* `最佳化` 打包應用配置頁面支援更多選項的讀取及配置 (啟動時顯示日誌/啟動時顯示啟動頁/啟動頁標語文字/...)
* `最佳化` 打包應用設定頁面增加前臺服務開關 _[`issue #406`](http://issues.autojs6.com/406)_
* `最佳化` 指令碼專案配置檔案儲存時增加鍵名衝突檢測機制防止鍵名歧義
* `最佳化` 崩潰報告頁面支援雙指縮放調整字型大小並新增常用功能按鈕
* `最佳化` 應用啟動器圖示支援自適應圖示特性 _[`issue #405`](http://issues.autojs6.com/405)_
* `最佳化` Rhino 引擎在泛型簽名解析失敗時回退為原始反射型別以增強低版本安卓系統的反射方法可用性
* `最佳化` 主頁抽屜開關類條目新增分隔線, 支援點選標題文字區域顯示詳情對話方塊並按需支援快捷跳轉系統設定
* `最佳化` 啟動或重啟 AutoJs6 時支援點選主頁抽屜 "客戶端模式" 標題文字區域中止正在嘗試建立的連線
* `最佳化` 客戶端模式連線時支援使用 IPv6 地址及域名地址建立連線
* `最佳化` 客戶端模式連線時支援特殊用途 IPv4 地址 (迴環/廣播/多播/保留/...) 檢測提示
* `最佳化` 客戶端模式連線時支援連線狀態顯示及管理 (修正地址/中止連線)
* `最佳化` 服務端模式連線時支援顯示已建立連線的客戶端數量
* `最佳化` 讀取通知權限支援自動重連機制 ([requestRebind](https://developer.android.com/reference/android/service/notification/NotificationListenerService#requestRebind(android.content.ComponentName))) 以增強通知監聽相關功能的穩定性
* `最佳化` 浮動按鈕增強後臺啟動 Activity 的安全性以避免應用崩潰
* `最佳化` 浮動按鈕 "更多" 對話方塊使用非同步載入資料方式提升顯示流暢度
* `最佳化` 浮動按鈕 "執行指令碼" 對話方塊增加 "主頁" 選單項
* `最佳化` 浮動按鈕 "執行指令碼" 對話方塊支援最小化及狀態恢復並盡最大努力保持視窗常駐或自動恢復
* `最佳化` 支援使用 Shizuku 權限錄製指令碼及錄製回放 (回放流暢度受一定影響) _[`issue #320`](http://issues.autojs6.com/320)_
* `最佳化` 主題色設定頁面定位主題色時使用快速定位方式以提升定位效率
* `最佳化` 使用 [LiveData](https://developer.android.com/topic/libraries/architecture/livedata) 及 [SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow) 替代已棄用的 [LocalBroadcastManager](https://developer.android.com/jetpack/androidx/releases/localbroadcastmanager)
* `最佳化` Gradle 構建指令碼提升 7z 格式檔案的解壓效率
* `最佳化` Gradle 構建指令碼支援獲取詳細的 Android Studio IDE 版本 (如 "2025.1.4.7")
* `最佳化` Gradle 構建指令碼支援自動生成 VersionCodesList 類所需資料以降低指令碼啟動延遲
* `最佳化` 使用 [版本目錄 (Version Catalogs)](https://developer.android.com/build/migrate-to-catalogs) 集中管理 Gradle 依賴和外掛版本
* `最佳化` 模組化 Gradle 構建指令碼, 將共享構建邏輯遷移至 build-logic 並抽象為約定外掛
* `最佳化` 使用 Gradle 約定外掛簡化本地 AAR 庫載入邏輯
* `最佳化` 移除 "android.enableJetifier=true" Gradle 配置選項以適配 Gradle 9
* `最佳化` 使用 [Toolchain](https://docs.gradle.org/current/userguide/toolchains.html) 替代 sourceCompatibility/targetCompatibility 以降低構建環境差異
* `依賴` 模組化 Expandable Layout 版本 1.6.0
* `依賴` 模組化 Expandable RecyclerView 版本 3.0.0-RC1
* `依賴` 模組化 RecyclerView Flexible Divider 版本 1.4.0
* `依賴` 本地化 Root Shell 版本 1.6
* `依賴` 附加 AndroidX Activity (KTX) 版本 1.12.2
* `依賴` 附加 AndroidX Core (KTX) 版本 1.15.0
* `依賴` 附加 AndroidX Work Runtime 版本 2.11.0
* `依賴` 附加 AndroidX Interpolator 版本 1.0.0
* `依賴` 附加 Room RxJava2 版本 2.8.1
* `依賴` 升級 Android SDK 版本 35 -> 36
* `依賴` 升級 Gradle 8 版本 8.14 -> 8.14.4
* `依賴` 升級 Gradle 9 版本 8.14 -> 9.4.0
* `依賴` 升級 Rhino 版本 1.8.1-SNAPSHOT -> 2.0.0-SNAPSHOT
* `依賴` 升級 Desugar JDK Libs 版本 2.0.4 -> 2.1.5
* `依賴` 升級 Android Material 版本 1.12.0 -> 1.13.0
* `依賴` 升級 AndroidX AppCompat 版本 1.7.0 -> 1.7.1
* `依賴` 升級 AndroidX Room 版本 2.7.1 -> 2.7.2
* `依賴` 升級 AndroidX WebKit 版本 1.13.0 -> 1.14.0
* `依賴` 升級 Material Progressbar 版本 1.4.2 -> 1.6.1
* `依賴` 升級 Apache Commons Lang3 版本 3.16.0 -> 3.18.0
* `依賴` 升級 Retrofit2 Retrofit 版本 2.11.0 -> 2.12.0
* `依賴` 升級 Retrofit2 Converter Gson 版本 2.11.0 -> 2.12.0
* `依賴` 升級 Retrofit2 RxJava2 版本 2.11.0 -> 2.12.0
* `依賴` 升級 Joda Time 版本 2.12.7 -> 2.14.0
* `依賴` 升級 Gson 版本 2.11.0 -> 2.13.1
* `依賴` 升級 ARSCLib 版本 1.3.1 -> 1.3.5
* `依賴` 升級 Jsoup 版本 1.19.1 -> 1.20.1
* `依賴` 升級 JUnit Jupiter 版本 5.10.3 -> 5.13.0
* `依賴` 升級 OpenCC 版本 1.2.0 -> 1.2.2
* `依賴` 移除 Android Support Annotation v7 版本 28.0.0
* `依賴` 移除 Android Support AppCompat v7 版本 28.0.0
* `依賴` 移除 Android Support RecyclerView v7 版本 28.0.0

# v6.6.4

###### 2025/05/31

* `提示` API 變更: ui.(status/navigation)BarAppearanceLight[By] -> ui.(status/navigation)BarIconLight[By]
* `新增` util.dpToPx/spToPx/pxToDp/pxToSp 方法, 用於畫素單位轉換
* `修復` 螢幕旋轉至橫向時子標題可能顯示不完整的問題
* `修復` 螢幕旋轉至橫向時部分頁面內容被側邊導航欄遮擋的問題
* `修復` Android 15 部分頁面狀態列背景著色區域不完整的問題 _[`issue #398`](http://issues.autojs6.com/398)_
* `修復` 程式碼編輯器可能使用置信度不足的編碼寫入檔案導致內容解碼異常的問題 (試修)
* `最佳化` 關於應用與開發者頁面提升佈局適配性並移除不必要的佈局分類
* `最佳化` README.md 專案編譯構建小節增加設定頁面的定位方式 _[`issue #404`](http://issues.autojs6.com/404)_
* `依賴` 附加 AndroidX ConstraintLayout 版本 2.2.1

# v6.6.3

###### 2025/05/27

* `新增` 發行歷史功能, 支援檢視多語言發行記錄及統計資料
* `新增` timers.keepAlive 方法 (已全域化), 用於保持指令碼活躍狀態
* `新增` engines.on('start/stop/error', callback) 等事件監聽方法, 用於監聽指令碼引擎全域事件
* `新增` images.detectMultiColors 方法, 用於多點顏色校驗 _[`issue #374`](http://issues.autojs6.com/374)_
* `新增` images.matchFeatures/detectAndComputeFeatures 方法, 支援全解析度找圖 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `新增` images.compressToBytes 方法, 用於壓縮影像並生成位元組陣列
* `新增` images.downsample 方法, 用於影像降取樣並返回新的 ImageWrapper 物件
* `新增` ui.keepScreenOn 方法, 用於 UI 頁面獲取焦點時保持裝置螢幕常亮
* `新增` ui.root 屬性 (getter), 用於獲取 UI 頁面佈局的 "視窗內容根容器" 節點
* `新增` webview 元素支援基於 JsBridge 的 Web 頁面佈局 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (參閱 範例程式碼 > 佈局 > 可互動 HTML/Vue2 + Vant (SFC)) _[`issue #281`](http://issues.autojs6.com/281)_
* `新增` 佈局分析支援控制元件隱藏 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `修復` 主頁文件標籤及文件活動頁面顯示線上文件時部分內容被系統導航欄遮擋的問題
* `修復` 部分頁面 Toolbar 點選按鈕時可能導致標題點選事件誤觸發的問題
* `修復` 部分裝置程式碼編輯器空行顯示方框字元的問題 (試修)
* `修復` 主題色設定頁面調色盤對話方塊可能無限疊加的問題
* `修復` Android 15 部分頁面狀態列背景顏色可能無法動態跟隨主題色的問題
* `修復` 無障礙服務關閉時音量加鍵停止所有指令碼功能失效的問題
* `修復` 定時任務頁面編輯自定義廣播內容時出現的輸入法遮擋問題
* `修復` webview 元素中的控制元件無法正常啟用輸入法軟鍵盤的問題
* `修復` APK 檔案型別資訊對話方塊可能無法獲取應用名稱及 SDK 資訊的問題
* `修復` 檔案管理器範例程式碼進入專案目錄時可能無法自動載入子目錄檔案內容的問題
* `修復` Android 15 UI 模式頂部內容被狀態列覆蓋的問題
* `修復` dialogs 模組無法正常使用 customView 屬性的問題 _[`issue #364`](http://issues.autojs6.com/364)_
* `修復` dialogs.input 方法的表示式參數可能無法獲得執行結果的問題
* `修復` 使用 JavaAdapter 時導致 ClassLoader 呼叫棧溢位的問題 _[`issue #376`](http://issues.autojs6.com/376)_
* `修復` console.setContentTextColor 方法導致日誌字型顏色丟失預設值的問題 _[`issue #346`](http://issues.autojs6.com/346)_
* `修復` console.setContentBackgroundColor 方法無法接受顏色名稱參數的問題 _[`issue #384`](http://issues.autojs6.com/384)_
* `修復` images.compress 方法實現原理由畫素降取樣修正為編碼品質變化
* `修復` images.resize 方法無法正常使用的問題
* `修復` engines.all 方法可能觸發 ConcurrentModificationException 的問題 _[`issue #394`](http://issues.autojs6.com/394)_
* `修復` README.md 中部分語言日期格式不正確的問題
* `修復` Gradle 構建指令碼可能因獲取到無效庫檔案檔案長度而導致構建失敗的問題 _[`issue #389`](http://issues.autojs6.com/389)_
* `最佳化` 佈局分析選單新增漸變分隔線實現一定程度的功能分組
* `最佳化` 指令碼專案配置檔案 project.json 支援 permissions 選項 (by [wirsnow](https://github.com/wirsnow)) _[`pr #391`](http://pr.autojs6.com/391)_ _[`issue #362`](http://issues.autojs6.com/362)_
* `最佳化` 打包單檔案時自動讀取並勾選已安裝應用的宣告權限 _[`issue #362`](http://issues.autojs6.com/362)_
* `最佳化` 主題色擴充適配範圍並支援更多控制元件型別
* `最佳化` 主頁抽屜在橫向螢幕或超寬螢幕的寬度適應性
* `最佳化` 關於應用與開發者頁面增加水平佈局及小屏佈局適配
* `最佳化` 設定頁面對話方塊相關設定支援 "使用預設值" 選單選項
* `最佳化` 檔案管理器浮動按鈕展開後點選其他區域可自動隱藏
* `最佳化` 使用非同步載入方式一定程度提升檔案管理器列表滑動流暢性
* `最佳化` 程式碼編輯器格式化程式碼支援 `??`, `?.`, `??=` 等運算子
* `最佳化` 程式碼編輯器支援 GB18030, UTF-16 (LE/BE) 及 Shift_JIS 等編碼的檔案讀寫
* `最佳化` 程式碼編輯器支援顯示檔案詳細資訊 (路徑/編碼/換行符/位元組及字元總數等) _[`issue #395`](http://issues.autojs6.com/395)_
* `最佳化` 意圖相關操作 (編輯/檢視/安裝/傳送/播放等) 增加操作異常提示
* `最佳化` webview 元素的 url 屬性支援相對路徑
* `最佳化` ImageWrapper#saveTo 方法的路徑參數支援相對路徑
* `最佳化` images.save 方法使用 quality 參數時支援 png 格式的檔案體積壓縮 _[`issue #367`](http://issues.autojs6.com/367)_
* `最佳化` 已忽略更新記錄及客戶端模式連線地址記錄支援清空操作
* `最佳化` 版本更新資訊支援多語言顯示 (與目前顯示語言同步)
* `最佳化` 指令碼異常訊息在控制檯的顯示內容與格式
* `最佳化` 範例程式碼支援將資料夾重置為初始內容
* `最佳化` APK 檔案簽名資訊提升檢測效率
* `最佳化` APK 檔案型別資訊及媒體檔案型別資訊最佳化對話方塊顯示效率及資訊展示邏輯
* `最佳化` Gradle 構建指令碼提升版本自適應能力 _[`discussion #369`](http://discussions.autojs6.com/369)_
* `依賴` 模組化 Material Dialogs 版本 0.9.6.0
* `依賴` 模組化 Material Date Time Picker 版本 4.2.3
* `依賴` 本地化 libimagequant 版本 2.17.0
* `依賴` 本地化 libpng 版本 1.6.49
* `依賴` 附加 ICU4J 版本 77.1
* `依賴` 附加 Jsoup 版本 1.19.1
* `依賴` 附加 Material Progressbar 版本 1.4.2
* `依賴` 附加 Flexmark Java HTML to Markdown 版本 0.64.8
* `依賴` 升級 Gradle 版本 8.14-rc-1 -> 8.14
* `依賴` 升級 AndroidX Room 版本 2.7.0 -> 2.7.1

# v6.6.2

###### 2025/04/16

* `新增` ui.statusBarAppearanceLight/statusBarAppearanceLightBy/navigationBarColor 等方法, 用於設定狀態列及導航欄樣式
* `新增` ui.statusBarHeight 屬性 (getter), 用於獲取狀態列高度 _[`issue #357`](http://issues.autojs6.com/357)_
* `新增` images.flip 方法, 用於影像翻轉 _[`issue #349`](http://issues.autojs6.com/349)_
* `新增` 設定頁面增加 "副檔名" 設定選項
* `新增` 主題色設定頁面支援分組, 定位, 搜尋, 歷史記錄及調色盤增強功能
* `修復` Android 15 狀態列背景顏色與主題色不一致的問題
* `修復` plugins.load 方法無法正常載入外掛的問題 _[`issue #290`](http://issues.autojs6.com/290)_
* `修復` dx 庫在 Android 7.x 無法正常使用的問題 _[`issue #293`](http://issues.autojs6.com/293)_
* `修復` ScriptRuntime 使用 require 引用內建模組時可能出現的同步狀態異常 (試修) _[`issue #298`](http://issues.autojs6.com/298)_
* `修復` notice 模組缺失 getBuilder 等擴充方法的問題 _[`issue #301`](http://issues.autojs6.com/301)_
* `修復` shizuku/shell 等方法無法接受字串參數的問題 _[`issue #310`](http://issues.autojs6.com/310)_
* `修復` colors.pixel 方法無法接受單通道影像參數的問題 _[`issue #350`](http://issues.autojs6.com/350)_
* `修復` engines.execScript/execScriptFile 等方法執行指令碼時預設工作路徑異常 _[`issue #358`](http://issues.autojs6.com/358)_ _[`issue #340`](http://issues.autojs6.com/340)_ _[`issue #339`](http://issues.autojs6.com/339)_
* `修復` floaty.window/floaty.rawWindow 無法在子執行緒執行的問題
* `修復` floaty.getClip 可能無法正常獲取剪貼簿內容的問題 _[`issue #341`](http://issues.autojs6.com/341)_
* `修復` ui.inflate 回傳值丟失 attr/on/click 等原型方法的問題
* `修復` 使用 XML 語法將 JavaScript 表示式作為屬性值時, 其作用域上下文繫結錯誤的問題 _[`issue #319`](http://issues.autojs6.com/319)_
* `修復` 部分方法呼叫出現異常時無法被 try/catch 塊捕獲的問題 _[`issue #345`](http://issues.autojs6.com/345)_
* `修復` 佈局分析頁面生成程式碼時可能導致應用崩潰的問題 _[`issue #288`](http://issues.autojs6.com/288)_
* `修復` 打包應用無法正常使用 shizuku 模組的問題 _[`issue #227`](http://issues.autojs6.com/227)_ _[`issue #231`](http://issues.autojs6.com/231)_ _[`issue #284`](http://issues.autojs6.com/284)_ _[`issue #287`](http://issues.autojs6.com/287)_ _[`issue #304`](http://issues.autojs6.com/304)_
* `修復` 程式碼編輯器跳轉到行尾時可能跳轉到下一行起始位置的問題
* `修復` 設定頁面連續快速點選對話方塊型別專案時可能導致應用崩潰的問題
* `最佳化` 精簡打包應用模板 APK 檔案大小
* `最佳化` 應用 (以及打包應用) 支援更多權限 _[`issue #338`](http://issues.autojs6.com/338)_
* `最佳化` 打包應用配置頁面支援 Pinyin 庫選項
* `最佳化` 打包應用主活動頁面最佳化狀態列背景及文字顏色
* `最佳化` 打包應用設定頁面增加訪問所有檔案和傳送通知等特殊權限開關 _[`issue #354`](http://issues.autojs6.com/354)_
* `最佳化` 控制元件的文字及圖示根據主題色亮度值自動切換合適的顏色
* `最佳化` 部分控制元件主題色與背景色對比度過低時的視覺體驗
* `最佳化` 調色盤 HEX 輸入控制元件增強剪貼簿貼上色值時的相容性
* `最佳化` 應用內頁面導航欄設定為透明或半透明以增強視覺體驗
* `最佳化` UI 模式狀態列及導航欄預設為 `md_grey_50` 色值且設定為亮色模式
* `最佳化` 主頁抽屜無障礙服務開關支援與指令碼程式碼同步
* `最佳化` 主頁文件頁面搜尋時支援雙向查詢按鈕
* `最佳化` 主頁 "檔案" 標籤支援透過長按切換浮動按鈕可見狀態
* `最佳化` 程式碼編輯器標題文字支援字型大小自適應
* `最佳化` 日誌頁面浮動按鈕可見狀態與列表滾動操作聯動
* `最佳化` 指令碼專案配置檔案 project.json 支援更多打包選項 _[`issue #305`](http://issues.autojs6.com/305)_ _[`issue #306`](http://issues.autojs6.com/306)_
* `最佳化` 指令碼專案配置檔案 project.json 支援選項名稱寬鬆匹配及別名相容
* `最佳化` APK 檔案型別資訊對話方塊增加檔案大小與簽名方案資訊
* `最佳化` APK 檔案型別資訊對話方塊增加點選監聽器支援文字複製與應用詳情跳轉
* `最佳化` 嘗試恢復 com.stardust 字首包以便提升程式碼相容性 _[`issue #290`](http://issues.autojs6.com/290)_
* `最佳化` floaty.window/floaty.rawWindow 同時支援主執行緒和子執行緒執行
* `最佳化` getClip 全域方法適時藉助 floaty.getClip 方法以提升相容性
* `最佳化` files.path 及相關方法傳入空值路徑參數時的相容性
* `最佳化` 同步最新的 Rhino 引擎官方上游程式碼並進行必要的程式碼適配
* `最佳化` README.md 完善專案構建與執行相關內容 _[`issue #344`](http://issues.autojs6.com/344)_
* `依賴` 附加 Eclipse Paho Client Mqtt V3 版本 1.1.0 _[`issue #330`](http://issues.autojs6.com/330)_
* `依賴` 升級 Android SDK 版本 34 -> 35
* `依賴` 升級 Gradle 版本 8.12 -> 8.14-rc-1
* `依賴` 升級 Rhino 版本 1.8.0-SNAPSHOT -> 1.8.1-SNAPSHOT
* `依賴` 升級 AndroidX RecyclerView 版本 1.3.2 -> 1.4.0
* `依賴` 升級 AndroidX Room 版本 2.6.1 -> 2.7.0
* `依賴` 升級 AndroidX WebKit 版本 1.12.1 -> 1.13.0
* `依賴` 升級 Pinyin4j 版本 2.5.0 -> 2.5.1

# v6.6.1

###### 2025/01/01

* `新增` pinyin 模組, 用於漢語拼音轉換 (參閱 專案文件 > [漢語拼音](https://docs.autojs6.com/#/pinyin))
* `新增` pinyin4j 模組, 用於漢語拼音轉換 (參閱 專案文件 > [漢語拼音](https://docs.autojs6.com/#/pinyin4j))
* `新增` UiObject#isSimilar 及 UiObjectCollection#isSimilar 方法, 用於判斷控制元件或控制元件集合是否相似
* `新增` currentComponent 全域方法, 用於獲取目前活動的元件名稱
* `修復` 部分環境因回退版本過低而無法正常編譯專案的問題
* `修復` 呼叫不存在的方法時可能出現的 "非原始型別值" 異常
* `修復` 部分裝置無法正常新增指令碼快捷方式的問題 (試修) _[`issue #221`](http://issues.autojs6.com/221)_
* `修復` automator.click/longClick 方法參數型別限制錯誤 _[`issue #275`](http://issues.autojs6.com/275)_
* `修復` 選擇器不支援 ConsString 型別參數的問題 _[`issue #277`](http://issues.autojs6.com/277)_
* `修復` UiObjectCollection 實例缺失自身方法及屬性的問題
* `最佳化` 打包應用配置頁面支援簽名配置/金鑰庫管理/權限設定 (by [luckyloogn](https://github.com/luckyloogn)) _[`pr #286`](http://pr.autojs6.com/286)_
* `最佳化` 打包單檔案時自動生成的包名字尾支援將簡體中文轉換為拼音 (支援多音字)
* `最佳化` 提升浮動視窗目前包名及目前活動名的識別準確性 (優先順序: Shizuku > Root > A11Y)
* `最佳化` 提升 currentPackage/currentActivity 識別準確性 (優先順序: Shizuku > Root > A11Y)
* `最佳化` 恢復日誌視窗單個條目文字內容的雙擊或長按選擇功能 _[`issue #280`](http://issues.autojs6.com/280)_
* `最佳化` 指令碼專案識別在 project.json 損壞情況下儘可能還原關鍵資訊
* `最佳化` UiSelector#findOnce 及 UiSelector#find 方法支援負數參數
* `最佳化` UI 元素及 className 相關選擇器支援更多的包名字首省略形式 (如 RecyclerView, Snackbar 等)
* `最佳化` app.startActivity/startDualActivity 方法增強適配性
* `最佳化` 同步最新的 Rhino 引擎官方上游程式碼並進行必要的程式碼適配
* `依賴` 附加 Pinyin4j 版本 2.5.0
* `依賴` 附加 Jieba Analysis 版本 1.0.3-SNAPSHOT (modified)
* `依賴` 升級 Gradle 版本 8.11.1 -> 8.12

# v6.6.0

###### 2024/12/02 - 內建模組重寫, 謹慎升級

* `提示` 內建模組使用 Kotlin 重新編寫以提升指令碼執行效率但可能需要多次迭代逐步完善
* `提示` 內建 init.js 檔案預設為空但支援開發者自行擴充內建模組或掛載外部模組
* `新增` axios/cheerio 模組, 用於網路請求及 HTML 解析 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX))
* `新增` sqlite 模組, 用於 SQLite 資料庫操作 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (參閱 專案文件 > [SQLite](https://docs.autojs6.com/#/sqlite))
* `新增` mime 模組, 用於處理和解析 MIME 型別字串 (參閱 專案文件 > [MIME](https://docs.autojs6.com/#/mime))
* `新增` nanoid 模組, 用於生成字串 ID (Ref to [ai/nanoid](https://github.com/ai/nanoid))
* `新增` sysprops 模組, 用於獲取執行時環境配置資料 (參閱 專案文件 > [系統屬性](https://docs.autojs6.com/#/sysprops))
* `新增` ocr 模組支援 [Rapid OCR](https://github.com/RapidAI/RapidOCR) 引擎
* `新增` 佈局分析支援切換視窗 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` auto.clearCache 方法, 用於清除控制元件快取 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` threads.pool 方法, 用於建立及使用執行緒池 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.matchTemplate 方法支援 useTransparentMask 選項參數, 用於透明找圖 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.requestScreenCaptureAsync 方法, 用於在 UI 模式下非同步申請截圖權限 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.requestScreenCapture 方法支援 isAsync 選項參數, 用於非同步獲取螢幕截圖 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.on('screen_capture', callback) 等事件監聽方法, 用於監聽螢幕截圖可用事件 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.stopScreenCapture 方法, 用於主動釋放截圖申請的相關資源 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.psnr/mse/ssim/mssim/hist/ncc 及 images.getSimilarity 方法, 用於獲取影像相似性度量值
* `新增` images.isGrayscale 方法, 用於判斷影像是否為灰度影像
* `新增` images.invert 方法, 用於影像反色
* `新增` s13n.point/time 方法, 用於標準化點物件及時長物件 (參閱 專案文件 > [標準化](https://docs.autojs6.com/#/s13n))
* `新增` console 模組 gravity (重力), touchThrough (穿透點選), backgroundTint (背景著色) 等相關方法 (參閱 專案文件 > [控制檯](https://docs.autojs6.com/#/console))
* `新增` Mathx.randomInt/randomFloat 方法, 用於獲取指定範圍內的隨機整數或浮點數
* `新增` app.launchDual/startDualActivity 等方法, 用於處理雙開應用 (需要 Shizuku 或 Root 權限) (實驗性)
* `新增` app.kill 方法, 用於強制停止應用 (需要 Shizuku 或 Root 權限)
* `新增` floaty.getClip 方法, 用於藉助浮動視窗聚焦穫取剪貼簿內容
* `修復` Fragment 子類 (如 [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48) 等) 中存在的 View Binding 記憶體洩漏
* `修復` [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21) 等類中存在的實例記憶體洩漏
* `修復` Android 14 申請截圖權限導致應用崩潰的問題 (by [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `修復` Android 14 開啟前臺服務導致應用崩潰的問題
* `修復` Android 14 程式碼編輯器執行按鈕點選後無法正常亮起的問題
* `修復` 程式碼編輯器除錯功能的 FAILED ASSERTION 異常
* `修復` 程式碼編輯器除錯過程中關閉編輯器後無法再次正常除錯的問題
* `修復` 程式碼編輯器跳轉到行尾時可能遺漏末尾字元的問題
* `修復` 專案打包後應用可能因缺少必要庫檔案無法正常執行的問題 _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `修復` 打包應用無法正常使用 opencc 模組的問題
* `修復` 編輯專案時可能因指定圖示資源不存在而導致應用崩潰的問題 _[`issue #203`](http://issues.autojs6.com/203)_
* `修復` 截圖權限申請時無法正常使用參數獲取指定螢幕方向的截圖資源
* `修復` 部分裝置無法正常新增指令碼快捷方式的問題 (試修) _[`issue #221`](http://issues.autojs6.com/221)_
* `修復` 呼叫 http 模組與傳送請求相關的方法將出現累積性請求傳送延遲的問題 _[`issue #192`](http://issues.autojs6.com/192)_
* `修復` Shizuku 服務在 AutoJs6 進入主活動頁面之前可能無法正常使用的問題 (試修) _[`issue #255`](http://issues.autojs6.com/255)_
* `修復` random(min, max) 方法可能出現結果越界的問題
* `修復` pickup 方法結果型別參數無法正常傳入空陣列的問題
* `修復` UiObject#bounds() 得到的控制元件矩形可能被意外修改而破壞其不變性的問題
* `修復` 無障礙服務自動啟動可能出現的服務異常問題 (試修)
* `修復` text/button/input 元素的文字內容包含半形雙引號時無法正常解析的問題
* `修復` text/textswitcher 元素的 autoLink 屬性功能失效的問題
* `修復` 不同指令碼可能錯誤地共享同一個 ScriptRuntime 物件的問題
* `修復` 全域變數 HEIGHT 及 WIDTH 丟失 getter 動態屬性的問題
* `修復` 指令碼啟動時 RootShell 隨即載入可能導致啟動高延遲的問題
* `修復` 控制檯浮動視窗設定背景顏色導致矩形圓角樣式丟失的問題
* `修復` 主活動頁面啟動日誌頁面時可能出現閃屏的問題
* `修復` 主頁文件頁面左右滑動 WebView 控制元件時可能觸發 ViewPager 切換的問題
* `修復` 檔案管理器無法識別包含大寫字母副檔名的問題
* `修復` 檔案管理器首次進入專案目錄時可能無法自動識別專案的問題
* `修復` 檔案管理器刪除資料夾後頁面無法自動重新整理的問題
* `修復` 檔案管理器排序檔案及資料夾時可能出現 ASCII 首字母名稱置後的問題
* `最佳化` 打包應用配置頁面中 "不可用 ABI" 控制元件的點選提示體驗
* `最佳化` 打包單檔案時支援自動填入合適的標準化包名並支援無效字元過濾提示
* `最佳化` 打包單檔案時支援根據已安裝同包名應用自動設定圖示並自增版本號及版本名稱
* `最佳化` 打包專案配置檔案支援 abis/libs 選項指定預設包含的 ABI 架構及擴充程式庫
* `最佳化` 打包專案配置檔案 abis/libs 選項無效或無可用時支援相關訊息提示
* `最佳化` 支援使用 Shizuku 控制 "指標位置" 顯示開關
* `最佳化` 支援使用 Shizuku 控制 "投影媒體" 及 "修改安全設定" 權限開關
* `最佳化` automator.gestureAsync/gesturesAsync 支援回呼函式參數
* `最佳化` tasks 模組使用同步方式進行資料庫操作避免可能的資料訪問不一致問題
* `最佳化` 指令碼執行模式支援管道符號分隔模式參數 (如 `"ui|auto";` 開頭)
* `最佳化` 指令碼執行模式支援單引號及反引號且支援省略分號 (如 `'ui';` 或 `'ui'` 開頭)
* `最佳化` 指令碼執行模式支援 axios, cheerio, dayjs 等模式參數快捷匯入內建擴充模組 (如 `"axios";` 開頭)
* `最佳化` 指令碼執行模式支援 x 或 jsox 模式參數快捷啟用 JavaScript 內建物件擴充模組 (如 `"x";` 開頭)
* `最佳化` img 元素 src 及 path 屬性支援本地相對路徑 (如 `<img src="a.png" />`)
* `最佳化` 程式碼編輯器匯入 Java 類和包名時支援智慧判斷插入位置
* `最佳化` images 模組支援直接使用路徑作為影像參數
* `最佳化` importPackage 支援字串參數
* `最佳化` 客戶端模式 IP 地址支援剪貼簿匯入智慧識別且支援空格按鍵智慧轉換
* `最佳化` 檔案管理器新建檔案時支援預設字首選擇並自動生成合適的數字字尾
* `最佳化` 檔案管理器執行專案時具體化異常訊息提示 _[`issue #268`](http://issues.autojs6.com/268)_
* `最佳化` 檔案管理器支援更多型別並支援顯示對應的圖示符號 (支援 800 多種檔案型別)
* `最佳化` 檔案管理器可編輯的檔案型別 (jpg/doc/pdf/...) 增加編輯按鈕
* `最佳化` 檔案管理器 APK 檔案支援檢視基礎資訊, Manifest 資訊及權限列表
* `最佳化` 檔案管理器音訊及影片等媒體檔案支援檢視基礎資訊及 MediaInfo 資訊
* `最佳化` 正式發行版本排除 LeakCanary 以縮減安裝包體積
* `最佳化` 專案原始碼所有英文註釋增加簡體中文翻譯以增強註釋可讀性
* `最佳化` README 及 CHANGELOG 支援多語言 (由指令碼自動生成)
* `最佳化` Gradle 構建指令碼提升版本自適應能力
* `依賴` 附加 MIME Util 版本 2.3.1
* `依賴` 附加 Toaster 版本 12.6
* `依賴` 附加 EasyWindow (for Toaster) 版本 10.3
* `依賴` 升級 Gradle 版本 8.5 -> 8.11.1
* `依賴` 升級 Rhino 版本 1.7.15-SNAPSHOT -> 1.8.0-SNAPSHOT
* `依賴` 升級 Android Material 版本 1.10.0 -> 1.12.0
* `依賴` 升級 AndroidX Annotation 版本 1.7.0 -> 1.9.1
* `依賴` 升級 AndroidX AppCompat 版本 1.6.1 -> 1.7.0
* `依賴` 升級 AndroidX WebKit 版本 1.8.0 -> 1.12.1
* `依賴` 升級 Apache Commons Lang3 版本 3.13.0 -> 3.16.0
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
* `依賴` 升級 JUnit Jupiter 版本 5.10.0 -> 5.10.3
* `依賴` 降級 OkHttp3 版本 5.0.0-alpha.11 -> 4.12.0

# v6.5.0

###### 2023/12/02

* `新增` opencc 模組, 用於中文簡繁及地區用詞轉換 (參閱 專案文件 > [中文轉換](https://docs.autojs6.com/#/opencc)) (Ref to [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `新增` UiSelector 增加 [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) 及 [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) 方法 _[`issue #115`](http://issues.autojs6.com/115)_
* `新增` 打包應用配置頁面增加 ABI 及庫的篩選支援 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `修復` 打包應用檔案體積異常龐大的問題 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `修復` 打包應用無法顯示並列印部分異常訊息的問題
* `修復` 打包應用配置頁面選擇應用圖示後可能顯示空圖示的問題
* `修復` 打包應用包含 ML Kit OCR 庫時可能出現的上下文未初始化異常
* `修復` ocr.recognizeText/detect 方法無效的問題
* `修復` 部分文字 (如日誌頁面) 顯示語言與應用設定語言可能不相符的問題
* `修復` 部分語言在主頁抽屜開關項可能出現文字溢位的問題
* `修復` 部分裝置無障礙服務開啟後立即自動關閉且無任何提示訊息的問題 _[`issue #181`](http://issues.autojs6.com/181)_
* `修復` 部分裝置無障礙服務開啟後裝置物理按鍵可能導致應用崩潰的問題 (試修) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `修復` 使用 auto(true) 重啟無障礙服務後 pickup 功能異常的問題 (試修) _[`issue #184`](http://issues.autojs6.com/184)_
* `修復` Android 7.x 無法使用無障礙服務的問題
* `修復` floaty 模組建立浮動視窗拖動時可能導致應用崩潰的問題 (試修)
* `修復` app.startActivity 無法使用簡稱參數的問題 _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `修復` importClass 匯入的類名與全域變數衝突時程式碼丟擲異常的問題 _[`issue #185`](http://issues.autojs6.com/185)_
* `修復` Android 14 可能無法正常使用 runtime.loadJar/loadDex 方法的問題 (試修)
* `修復` 安卓系統快速設定面板中 "佈局範圍分析" 和 "佈局層次分析" 不可用的問題 _[`issue #193`](http://issues.autojs6.com/193)_
* `修復` 自動檢查更新功能可能導致應用 [ANR](https://developer.android.com/topic/performance/vitals/anr) 的問題 (試修) _[`issue #186`](http://issues.autojs6.com/186)_
* `修復` 檔案管理器範例程式碼資料夾點選 "向上" 按鈕後無法回到工作路徑頁面的問題 _[`issue #129`](http://issues.autojs6.com/129)_
* `修復` 程式碼編輯器使用替換功能時替換按鈕無法顯示的問題
* `修復` 程式碼編輯器長按刪除時可能導致應用崩潰的問題 (試修)
* `修復` 程式碼編輯器點選 fx 按鈕無法顯示模組函式快捷面板的問題
* `修復` 程式碼編輯器模組函式快捷面板按鈕函式名稱可能溢位的問題
* `最佳化` 程式碼編輯器模組函式快捷面板適配夜間模式
* `最佳化` 打包應用啟動頁面適配夜間模式並調整應用圖示佈局
* `最佳化` 打包應用配置頁面支援使用軟鍵盤 ENTER 鍵實現游標跳轉
* `最佳化` 打包應用配置頁面支援點選 ABI 標題及庫標題切換全選狀態
* `最佳化` 打包應用配置頁面預設 ABI 智慧選擇並增加不可選擇項的引導提示
* `最佳化` 檔案管理器資料夾右鍵選單增加打包應用選項
* `最佳化` 檔案管理器根據檔案及資料夾的型別及特徵調整選單項的顯示情況
* `最佳化` 無障礙服務啟用但功能異常時在 AutoJs6 主頁抽屜開關處將體現異常狀態
* `最佳化` 控制檯列印錯誤訊息時附加詳細的堆疊資訊
* `依賴` 附加 ARSCLib 版本 1.2.4
* `依賴` 附加 Flexbox 版本 3.0.0
* `依賴` 附加 Android OpenCC 版本 1.2.0
* `依賴` 升級 Gradle 版本 8.5-rc-1 -> 8.5

# v6.4.2

###### 2023/11/15

* `新增` dialogs.build 方法支援 inputSingleLine 選項參數
* `新增` console.setTouchable 方法, 用於設定控制檯浮動視窗是否可觸控 _[`issue #122`](http://issues.autojs6.com/122)_
* `修復` ocr 模組部分方法無法識別區域參數的問題 _[`issue #162`](http://issues.autojs6.com/162)_ _[`issue #175`](http://issues.autojs6.com/175)_
* `修復` Android 7.x 發現新版本時無法獲取版本詳情的問題
* `修復` Android 14 申請截圖權限時導致應用崩潰的問題
* `修復` 主頁抽屜快速切換 "浮動按鈕" 開關時可能導致應用崩潰的問題
* `修復` 使用選單關閉浮動按鈕時重啟應用後浮動按鈕可能依然顯示的問題
* `修復` Android 13+ 設定頁面選擇並切換 AutoJs6 語言後無法生效的問題
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
* `修復` http 模組獲取響應物件的 body.contentType 回傳值型別錯誤 _[`issue #142`](http://issues.autojs6.com/142)_
* `修復` device.width 及 device.height 返回資料不正確的問題 _[`issue #160`](http://issues.autojs6.com/160)_
* `修復` 程式碼編輯器長按刪除時可能導致應用崩潰的問題 (試修) _[`issue #156`](http://issues.autojs6.com/156)_
* `修復` 程式碼編輯器反向選擇文字後進行常規操作可能導致應用崩潰的問題
* `修復` 部分裝置長按 AutoJs6 應用圖示無法顯示快捷方式選單的問題
* `修復` 部分裝置打包專案時點選確認按鈕無響應的問題
* `修復` app.sendBroadcast 及 app.startActivity 無法使用簡稱參數的問題
* `修復` floaty 模組 JsWindow#setPosition 等方法首次呼叫時的功能異常
* `最佳化` 增加 Termux 相關權限以支援 Intent 呼叫 Termux 執行 ADB 命令 _[`issue #136`](http://issues.autojs6.com/136)_
* `最佳化` http 模組獲取的響應物件支援重複呼叫 body.string/bytes 方法
* `最佳化` 增加 GitHub Actions 自動打包支援 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `最佳化` 構建工具自適應 Temurin 平臺
* `依賴` 升級 Gradle 版本 8.4-rc-3 -> 8.4
* `依賴` 升級 Android dx 版本 1.11 -> 1.14

# v6.4.0

###### 2023/10/30

* `新增` ocr 模組支援 Paddle Lite 引擎 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `新增` 打包功能支援內建外掛與外部外掛兩種打包方式 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `新增` WebSocket 模組, 用於 WebSocket 通訊 (參閱 專案文件 > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `新增` barcode/qrcode 模組, 用於條碼及二維碼識別 (參閱 專案文件 > [條碼](https://docs.autojs6.com/#/barcode) / [二維碼](https://docs.autojs6.com/#/qrcode))
* `新增` shizuku 模組 (參閱 專案文件 > [Shizuku](https://docs.autojs6.com/#/shizuku)) 及主頁抽屜權限開關
* `新增` device.rotation/orientation 等屬性, 用於獲取螢幕方向
* `新增` 內部 Java 類支援 class 靜態屬性訪問
* `新增` 安卓系統設定頁面支援切換應用語言 (Android 13+)
* `新增` 支援設定頁面新增或長按應用圖示啟用 [應用快捷方式](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn), 可啟動文件和設定等頁面
* `修復` 重新合併部分 PR (by [aiselp](https://github.com/aiselp)) 以解決部分指令碼無法正常結束執行的問題 _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `修復` 打包應用無法使用 AutoJs6 新增 API 的問題 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `修復` 打包應用在系統夜間模式下的樣式異常
* `修復` VSCode 外掛儲存檔案到本地時副檔名資訊丟失的問題
* `修復` 使用協程特性執行專案產生未捕獲異常致使應用崩潰的問題
* `修復` 重啟或退出應用時浮動按鈕無法記錄其位置狀態資訊的問題
* `修復` 螢幕旋轉至橫向時浮動按鈕可能溢位螢幕的問題 _[`issue #90`](http://issues.autojs6.com/90)_
* `修復` 螢幕多次旋轉時無法恢復浮動按鈕的座標及螢幕側邊方向的問題
* `修復` 裝置螢幕方向改變時無法獲取更新後的裝置配置資訊的問題 _[`issue #153`](http://issues.autojs6.com/153)_
* `修復` 螢幕旋轉至橫向時 Toolbar 標題字型過小的問題
* `修復` 螢幕旋轉至橫向時應用主頁的頁籤排版過於擁擠的問題
* `修復` 部分裝置訊息浮動框可能出現遺漏顯示或重複顯示的問題
* `修復` 訊息浮動框在多個指令碼同時執行時可能存在被遮蔽的問題 _[`issue #67`](http://issues.autojs6.com/67)_
* `修復` 使用廣播分析佈局時點選佈局無法彈出選單且導致應用崩潰的問題
* `修復` 第二次及以後建立的 WebSocket 實例均無法正常觸發監聽器的問題
* `修復` 撤銷 importPackage 的全域重定向方法以避免某些作用域下的包匯入異常 _[`issue #88`](http://issues.autojs6.com/88)_
* `修復` 日誌頁面使用複製或匯出功能時可能導致應用崩潰的問題
* `最佳化` 日誌頁面匯出功能重新命名為傳送功能並重新實現符合實際意義的匯出功能
* `最佳化` 日誌頁面傳送功能支援條目數量過大時自動擷取並作出提示
* `最佳化` ocr 模組同時相容 ML Kit 及 Paddle Lite 引擎 (參閱 專案文件 > [光學字元識別](https://docs.autojs6.com/#/ocr?id=p-mode))
* `最佳化` 提升無障礙服務自動啟動的成功機率
* `最佳化` Kotlin 註解處理由 kapt 遷移至 KSP
* `最佳化` 構建工具支援 IntelliJ IDEA EAP 版本
* `最佳化` 構建工具自適應 Java 發行版本以儘量避免 "無效的發行版本" 問題
* `最佳化` 構建工具最佳化 IDE 及相關外掛的版本退級邏輯並增加版本預測能力
* `最佳化` 適配 VSCode 外掛 1.0.7
* `依賴` 附加 Rikka Shizuku 版本 13.1.5
* `依賴` 附加 MLKit Barcode Scanning 版本 17.2.0
* `依賴` 升級 Android SDK 版本 33 -> 34
* `依賴` 升級 OpenCV 版本 4.5.5 -> 4.8.0 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依賴` 升級 Gradle 版本 8.3-rc-1 -> 8.4-rc-3
* `依賴` 升級 Apache Commons Lang3 版本 3.12.0 -> 3.13.0
* `依賴` 升級 Glide 版本 4.15.1 -> 4.16.0
* `依賴` 升級 Android Analytics 版本 14.3.0 -> 14.4.0
* `依賴` 升級 AndroidX WebKit 版本 1.7.0 -> 1.8.0
* `依賴` 升級 AndroidX Preference 版本 1.2.0 -> 1.2.1
* `依賴` 升級 AndroidX Annotation 版本 1.6.0 -> 1.7.0
* `依賴` 升級 AndroidX RecyclerView 版本 1.3.0 -> 1.3.2
* `依賴` 升級 Android Material 版本 1.9.0 -> 1.10.0
* `依賴` 升級 AndroidX AppCompat 版本 1.4.2 -> 1.6.1
* `依賴` 升級 Commons IO 版本 2.8.0 -> 2.14.0
* `依賴` 升級 Jackson DataBind 版本 2.13.3 -> 2.14.3
* `依賴` 移除 Zeugma Solutions LocaleHelper 版本 1.5.1

# v6.3.3

###### 2023/07/21

* `新增` 程式碼編輯器支援程式碼註釋 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新增` auto.stateListener 屬性, 用於監聽無障礙服務連線狀態 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `新增` UiObject#nextSibling/lastChild/offset 等方法, 用於控制元件導航 (參閱 專案文件 > [控制元件節點](https://docs.autojs6.com/#/uiObjectType))
* `修復` VSCode 外掛在指令碼字元總長度超過四位十進位制數時無法解析資料的問題 _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `修復` VSCode 外掛無法正常儲存檔案的問題 _[`issue #92`](http://issues.autojs6.com/92)_ _[`issue #94`](http://issues.autojs6.com/94)_
* `修復` 浮動按鈕選單項 "管理無障礙服務" 點選後可能未發生頁面跳轉的問題
* `修復` runtime.requestPermissions 方法丟失的問題 _[`issue #104`](http://issues.autojs6.com/104)_
* `修復` events.emitter 不支援 MainThreadProxy 參數的問題 _[`issue #103`](http://issues.autojs6.com/103)_
* `修復` 在 _[`pr #78`](http://pr.autojs6.com/78)_ 中存在的程式碼編輯器無法格式化程式碼的問題
* `修復` 使用 JavaAdapter 時導致 ClassLoader 呼叫棧溢位的問題 _[`issue #99`](http://issues.autojs6.com/99)_ _[`issue #110`](http://issues.autojs6.com/110)_
* `最佳化` 調整模組作用域 (by [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `最佳化` 移除發行版本應用啟動時的簽名校驗 (by [LZX284](https://github.com/LZX284)) _[`pr #81`](http://pr.autojs6.com/81)_
* `最佳化` 在 _[`pr #98`](http://pr.autojs6.com/98)_ 基礎上的編輯器程式碼註釋功能的行為, 樣式及游標位置處理
* `最佳化` 在 _[`pr #98`](http://pr.autojs6.com/98)_ 基礎上新增程式碼註釋選單項
* `最佳化` 適配 VSCode 外掛 1.0.6
* `最佳化` UiObject#parent 方法增加級數參數支援 (參閱 專案文件 > [控制元件節點](https://docs.autojs6.com/#/uiObjectType))
* `依賴` 升級 Gradle 版本 8.2 -> 8.3-rc-1

# v6.3.2

###### 2023/07/06

* `新增` crypto 模組, 用於資料加解密 (參閱 專案文件 > [密文](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
* `新增` UI 模式增加 textswitcher/viewswitcher/viewflipper/numberpicker/video/search 等控制元件
* `新增` 日誌頁面增加複製及匯出日誌等功能 _[`issue #76`](http://issues.autojs6.com/76)_
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
* `最佳化` 客戶端模式連線過程中在主頁抽屜副標題顯示目標 IP 地址
* `最佳化` 客戶端模式輸入目標地址時支援對不合法的輸入進行提示
* `最佳化` 客戶端模式支援使用軟鍵盤回車鍵建立連線
* `最佳化` 服務端模式開啟後保持常開狀態 (除非手動關閉或應用行程結束) _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `最佳化` 實現 AutoJs6 與 VSCode 外掛的雙向版本檢測並提示異常檢測結果 _[`issue #89`](http://issues.autojs6.com/89)_
* `最佳化` 支援 cheerio 模組 (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `最佳化` JsWebSocket 實例支援使用 rebuild 方法重建實例並建立連線 _[`issue #69`](http://issues.autojs6.com/69)_
* `最佳化` base64 模組支援 number 陣列及 Java 位元組陣列作為主要參數的編解碼
* `最佳化` 增加對 JavaMail for Android 的支援 _[`issue #71`](http://issues.autojs6.com/71)_
* `最佳化` 獲取版本更新資訊時使用 Blob 資料型別以增強無代理網路環境適應性
* `最佳化` 增加簡訊資料讀取權限 (android.permission.READ_SMS) (預設關閉)
* `最佳化` findMultiColors 方法內部實現 (by [LYS86](https://github.com/LYS86)) _[`pr #72`](http://pr.autojs6.com/72)_
* `最佳化` runtime.loadDex/loadJar/load 支援按目錄級別載入或同時載入多個檔案
* `依賴` 升級 LeakCanary 版本 2.11 -> 2.12
* `依賴` 升級 Android Analytics 版本 14.2.0 -> 14.3.0
* `依賴` 升級 Gradle 版本 8.2-milestone-1 -> 8.2

# v6.3.1

###### 2023/05/26

* `新增` 釋出通知權限及主頁抽屜開關 _[`issue #55`](http://issues.autojs6.com/55)_
* `新增` UI 模式支援簡單的 Android 佈局解析 (參閱 範例程式碼 > 佈局 > 簡單安卓佈局)
* `新增` UI 模式增加 console/imagebutton/ratingbar/switch/textclock/togglebutton 等控制元件
* `新增` UI 模式控制元件的顏色色值支援 [OmniColor](https://docs.autojs6.com/#/omniTypes?id=omnicolor) 型別 (如 color="orange")
* `新增` UI 模式的控制元件完全支援 attr 方法設定控制元件屬性 (如 ui.text.attr('color', 'blue'))
* `新增` UI 模式控制元件支援布林型別屬性值的預設形式 (如 clickable="true" 可簡寫為 clickable 或 isClickable)
* `新增` button 控制元件支援 isColored 及 isBorderless 布林型別屬性
* `新增` console.resetGlobalLogConfig 方法, 用於重置全域日誌配置
* `新增` web.newWebSocket 方法, 用於建立 WebSocket 實例 (參閱 專案文件 > [全球資訊網](https://docs.autojs6.com/#/web?id=m-newwebsocket))
* `修復` 檔案管理器的資料夾排序異常
* `修復` floaty 模組構建的浮動視窗無法調節樣式及位置的問題 _[`issue #60`](http://issues.autojs6.com/60)_
* `修復` floaty 模組構建的浮動視窗與系統狀態列重疊的問題
* `修復` http.postMultipart 方法功能異常 _[`issue #56`](http://issues.autojs6.com/56)_
* `修復` Android 7.x 無法執行任何指令碼的問題 _[`issue #61`](http://issues.autojs6.com/61)_
* `修復` sign.property 檔案不存在時無法構建專案的問題
* `修復` 高版本系統 AutoJs6 置於後臺時可能因無前臺通知權限而崩潰的問題 (API >= 33)
* `修復` 呼叫 console.show 方法後日志視窗點選 FAB 按鈕無法清空日誌的問題
* `修復` 程式碼編輯器除錯時出現的 prototype 空指標異常
* `修復` 程式碼編輯器執行指令碼時在快取資料夾執行臨時指令碼而非先儲存再在原始位置執行以避免可能的指令碼內容丟失問題
* `修復` 調整佈局層次分析的層級色條寬度避免層級過多時控制元件名稱無法顯示的問題 _[`issue #46`](http://issues.autojs6.com/46)_
* `最佳化` 佈局分析浮動視窗增加退出按鈕以關閉視窗 _[`issue #63`](http://issues.autojs6.com/63)_
* `最佳化` 指令碼絕對路徑使用簡稱形式以縮減文字長度並增加可讀性
* `最佳化` 將 Error 替換為 Exception 避免出現異常時 AutoJs6 應用崩潰
* `最佳化` 檢視 (View) 繫結方式由 ButterKnife 遷移至 View Binding _[`issue #48`](http://issues.autojs6.com/48)_
* `最佳化` 服務端模式非正常關閉時將於 AutoJs6 啟動時自動開啟 _[`issue #64`](http://issues.autojs6.com/64)_
* `最佳化` 客戶端模式非正常關閉時將於 AutoJs6 啟動時按最近一次的歷史地址自動連線
* `依賴` 升級 LeakCanary 版本 2.10 -> 2.11
* `依賴` 升級 Android Material 版本 1.8.0 -> 1.9.0
* `依賴` 升級 AndroidX WebKit 版本 1.6.1 -> 1.7.0
* `依賴` 升級 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11
* `依賴` 升級 MLKit Text Recognition Chinese 版本 16.0.0-beta6 -> 16.0.0

# v6.3.0

###### 2023/04/29

* `新增` ocr 模組, 用於光學字元識別 (參閱 專案文件 > [光學字元識別](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `新增` notice 模組, 用於傳送訊息通知 (參閱 專案文件 > [訊息通知](https://docs.autojs6.com/#/notice))
* `新增` s13n 模組, 用於資料標準化 (參閱 專案文件 > [標準化](https://docs.autojs6.com/#/s13n))
* `新增` Color 類, 用於顏色物件的建立及操作 (參閱 專案文件 > [顏色類](https://docs.autojs6.com/#/colorType))
* `新增` colors.setPaintColor 方法, 用於設定 android.graphics.Paint 畫筆顏色 (參閱 專案文件 > [顏色](https://docs.autojs6.com/#/color?id=m-setpaintcolor))
* `新增` 應用前臺執行時保持螢幕常亮的功能及設定選項
* `新增` 獨立文件啟動器, 用於閱讀應用文件 (支援在設定頁面隱藏或顯示)
* `修復` colors.toString 方法功能異常
* `修復` colors.material 顏色空間的顏色常量賦值錯誤
* `修復` app.openUrl 方法自動新增協議字首功能異常
* `修復` app.viewFile/editFile 在參數對應檔案不存在時的行為異常
* `修復` pickup 方法的回呼函式無法被呼叫的問題
* `修復` bounds/boundsInside/boundsContains 選擇器無法正常篩選狹義空矩形 (如邊界倒置矩形) _[`issue #49`](http://issues.autojs6.com/49)_
* `修復` 無障礙服務較大機率出現服務異常的問題 (試修)
* `修復` 佈局分析顯示的控制元件資訊 bounds 屬性值負數符號被替換為逗號的問題
* `修復` 更換主題或修改語言後點選或長按主頁文件標籤將導致應用崩潰的問題
* `修復` 程式碼編輯器雙指縮放調節字型大小時可能出現抖動的問題
* `修復` 構建指令碼中部分依賴源無法下載的問題 (已全部整合) _[`issue #40`](http://issues.autojs6.com/40)_
* `修復` Tasker 無法新增 AutoJs6 操作外掛 (Action Plugin) 的問題 (試修) _[`issue #41`](http://issues.autojs6.com/41)_
* `修復` 高版本 JDK 編譯專案時 ButterKnife 註解無法解析資源 ID 的問題 _[`issue #48`](http://issues.autojs6.com/48)_
* `修復` images.medianBlur 的 size 參數使用方式與文件不符的問題
* `修復` engines 模組顯示指令碼全稱時檔名與副檔名之間句點符號丟失的問題
* `修復` 加權 RGB 距離檢測演算法內部實現可能存在的計算失誤 (試修)
* `修復` console 模組的浮動視窗相關方法無法在 show 方法之前使用的問題
* `修復` console.setSize 等方法可能無法生效的問題 _[`issue #50`](http://issues.autojs6.com/50)_
* `修復` UI 模式的日期選擇控制元件 minDate 及 maxDate 屬性無法正確解析日期格式的問題
* `修復` 執行指令碼後快速切換到主頁 "任務" 標籤頁面將出現兩個相同執行中任務的問題
* `修復` 檔案管理器從其他頁面返回時頁面狀態可能被重置的問題 _[`issue #52`](http://issues.autojs6.com/52)_
* `修復` 檔案管理器排序狀態與圖示顯示狀態不符的問題
* `最佳化` 檔案管理器增加檔案及資料夾修改時間顯示
* `最佳化` 檔案管理器排序型別支援狀態記憶
* `最佳化` README.md 新增專案編譯構建小節與指令碼開發輔助小節 _[`issue #33`](http://issues.autojs6.com/33)_
* `最佳化` Gradle 構建指令碼支援 JDK 版本主動降級 (fallback)
* `最佳化` Gradle 構建指令碼支援根據平臺型別及版本自動選擇合適的構建工具版本 (程度有限)
* `最佳化` images 模組相關方法的區域 (region) 選項參數支援更多傳入方式 (參閱 專案文件 > [全能型別](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `最佳化` app.startActivity 頁面簡寫參數增加 pref/homepage/docs/about 等形式的支援
* `最佳化` web 模組的全域方法掛載到模組本身以增強可用性 (參閱 專案文件 > [全球資訊網](https://docs.autojs6.com/#/web))
* `最佳化` web.newInjectableWebView 方法內部預設實現部分常用的 WebView 設定選項
* `最佳化` colors 模組新增多種轉換方法及工具方法並新增更多靜態常量以及可直接作為參數的顏色名稱
* `最佳化` console 模組新增多種控制檯浮動視窗的樣式配置方法並支援 build 構建器統一配置視窗樣式
* `最佳化` 控制檯浮動視窗支援拖動標題區域移動視窗位置
* `最佳化` 控制檯浮動視窗支援指令碼結束後自動延遲關閉
* `最佳化` 控制檯浮動視窗及其 Activity 活動視窗支援雙指縮放調整字型大小
* `最佳化` http 模組相關方法支援超時選項參數 (timeout)
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
* `依賴` 升級 AndroidX WebKit 版本 1.5.0 -> 1.6.1
* `依賴` 升級 AndroidX RecyclerView 版本 1.2.1 -> 1.3.0
* `依賴` 升級 Zip4j 版本 2.11.2 -> 2.11.5
* `依賴` 升級 JUnit Jupiter 版本 5.9.2 -> 5.9.3
* `依賴` 升級 AndroidX Annotation 版本 1.5.0 -> 1.6.0
* `依賴` 升級 Jackson DataBind 版本 2.14.1 -> 2.14.2
* `依賴` 升級 Desugar JDK Libs 版本 2.0.0 -> 2.0.3

# v6.2.0

###### 2023/01/21

* `新增` 重新設計及編寫專案文件 (部分完成)
* `新增` 西班牙語/法語/俄語/阿拉伯語/日語/韓語/英語/繁體中文等多語言適配
* `新增` 工作路徑設定選項增加路徑選擇/歷史記錄/預設值智慧提示等功能
* `新增` 檔案管理器支援逐級跳轉至上級目錄 (直至 "內部儲存" 目錄)
* `新增` 檔案管理器支援將任意目錄快捷設定為工作路徑
* `新增` 版本更新支援忽略及已忽略記錄管理
* `新增` 程式碼編輯器支援雙指縮放調節字型大小
* `新增` UiSelector#idHex 選擇器, 用於按十六進位制控制元件 ID 篩選 (參閱 專案文件 > [選擇器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` UiSelector#action 選擇器, 用於按控制元件支援的動作篩選 (參閱 專案文件 > [選擇器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` UiSelector#xxxMatch 等 Match 系列選擇器, 用於正規表示式匹配 (參閱 專案文件 > [選擇器](https://docs.autojs6.com/#/uiSelectorType))
* `新增` UiSelector#pickup 拾取選擇器, 用於組合條件獲取控制元件 (參閱 專案文件 > [選擇器](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `新增` UiObject#detect 方法, 用於控制元件探測 (參閱 專案文件 > [控制元件節點](https://docs.autojs6.com/#/uiObjectType))
* `新增` UiObject#compass 方法, 用於控制元件導航 (參閱 專案文件 > [控制元件節點](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `新增` wait 全域方法, 用於條件等待 (參閱 專案文件 > [全域物件](https://docs.autojs6.com/#/global?id=m-wait))
* `新增` cX/cY/cYx 全域方法, 用於座標縮放 (參閱 專案文件 > [全域物件](https://docs.autojs6.com/#/global?id=m-cx))
* `新增` App 全域型別, 用於應用列舉 (參閱 專案文件 > [應用列舉類](https://docs.autojs6.com/#/appType))
* `新增` i18n 模組, 用於 JavaScript 多語言處理 (基於 banana-i18n) (參閱 專案文件 > 國際化)
* `修復` 軟體語言切換後可能導致的頁面文字閃變及部分頁面按鈕功能異常
* `修復` 工作路徑為一個專案時軟體啟動後不顯示專案工具欄的問題
* `修復` 工作路徑可能跟隨軟體語言切換而自動改變的問題 _[`issue #19`](http://issues.autojs6.com/19)_
* `修復` 定時任務啟動延時顯著 (試修) _[`issue #21`](http://issues.autojs6.com/21)_
* `修復` JavaScript 模組名被覆蓋宣告時導致存在依賴關係的內部模組無法正常使用的問題 _[`issue #29`](http://issues.autojs6.com/29)_
* `修復` 高版本安卓系統點選快速設定面板中相關圖示後面板可能無法自動收起的問題 (試修) _[`issue #7`](http://issues.autojs6.com/7)_
* `修復` 高版本安卓系統可能出現部分頁面與通知欄區域重疊的問題
* `修復` Android 10+ 無法正常執行有關設定畫筆顏色的範例程式碼的問題
* `修復` 範例程式碼 "音樂管理器" 更正檔名為 "檔案管理器" 並恢復正常功能
* `修復` 檔案管理器下拉重新整理時可能出現定位漂移的問題
* `修復` ui 模組作用域繫結錯誤導致部分基於 UI 的指令碼無法訪問元件屬性的問題
* `修復` 錄製指令碼後的輸入檔名對話方塊可能因外部區域點選導致已錄製內容丟失的問題
* `修復` 文件中部分章節標題超出螢幕寬度時無法自動換行造成內容丟失的問題
* `修復` 文件中的範例程式碼區域無法正常左右滑動的問題
* `修復` 文件頁面下拉重新整理時表現異常且無法撤銷重新整理操作的問題 (試修)
* `修復` 應用初始安裝後主頁抽屜夜間模式開關聯動失效的問題
* `修復` 系統夜間模式開啟時應用啟動後強制開啟夜間模式的問題
* `修復` 夜間模式開啟後已設定的主題色可能無法生效的問題
* `修復` 夜間模式下部分設定選項文字與背景色相同而無法辨識的問題
* `修復` Android 7.x 系統在夜間模式關閉時導航欄按鈕難以辨識的問題
* `修復` 關於頁面功能按鈕文字長度過大導致文字顯示不完全的問題
* `修復` 主頁抽屜設定項標題長度過大導致文字與按鈕重疊的問題
* `修復` 主頁抽屜權限開關在提示訊息對話方塊消失後可能出現狀態未同步的問題
* `修復` Root 權限修改主頁抽屜權限開關失敗時未繼續彈出 ADB 工具對話方塊的問題
* `修復` Root 權限顯示指標位置在初次使用時提示無權限的問題
* `修復` 圖示選擇頁面的圖示元素排版異常
* `修復` 程式碼編輯器啟動時可能因夜間模式設定導致閃屏的問題 (試修)
* `修復` 程式碼編輯器設定字型大小時可用最大值受限的問題
* `修復` 部分安卓系統指令碼執行結束時日誌中無法統計執行時長的問題
* `修復` 使用浮動按鈕選單關閉按鈕後重啟應用時浮動按鈕依然顯示的問題
* `修復` 佈局層次分析時長按列表項可能導致彈出選單溢位下方螢幕的問題
* `修復` http.post 等方法可能出現的請求未關閉異常
* `修復` colors.toString 方法在 Alpha 通道為 0 時其通道資訊在結果中丟失的問題
* `最佳化` 重定向 Auto.js 4.x 版本的公有類以實現儘可能的向下相容 (程度有限)
* `最佳化` 合併全部專案模組避免可能的迴圈引用等問題 (臨時移除 inrt 模組)
* `最佳化` Gradle 構建配置從 Groovy 遷移到 KTS
* `最佳化` Rhino 異常訊息增加多語言支援
* `最佳化` 主頁抽屜權限開關僅在開啟時彈出提示訊息
* `最佳化` 主頁抽屜佈局緊貼於狀態列下方避免頂部顏色條的低相容性
* `最佳化` 客戶端及服務端建立連線後在主頁抽屜顯示對應裝置的 IP 地址
* `最佳化` 檢查更新/下載更新/更新提示功能相容 Android 7.x 系統
* `最佳化` 重新設計設定頁面 (遷移至 AndroidX)
* `最佳化` 設定頁面支援長按設定選項獲取詳細資訊
* `最佳化` 夜間模式增加 "跟隨系統" 設定選項 (Android 9+)
* `最佳化` 應用啟動畫面適配夜間模式
* `最佳化` 主題色增加更多 Material Design Color (材料設計顏色) 選項
* `最佳化` 主頁搜尋框的提示文字顏色適配夜間模式
* `最佳化` 對話方塊/文字/FAB/AppBar/列表項等部件適配夜間模式
* `最佳化` 應用圖示增加數字標識以提升多個開源版本共存使用者的使用體驗
* `最佳化` 檔案管理器/任務面板等列表項圖示適當輕量化並適配主題色
* `最佳化` 文件/設定/關於/主題色/佈局分析等頁面及浮動按鈕選單適配夜間模式
* `最佳化` 點選浮動按鈕佈局分析按鈕直接進行佈局範圍分析
* `最佳化` 佈局分析主題自適應 (浮動視窗跟隨應用主題, 快速設定面板跟隨系統主題)
* `最佳化` 頁面佈局儘可能相容 RTL (Right-To-Left) 佈局
* `最佳化` 關於頁面增加圖示動畫效果
* `最佳化` 關於頁面版權宣告文字自動更新年份資訊
* `最佳化` 應用初始安裝後自動決定並設定合適的工作目錄
* `最佳化` 禁用文件頁面雙指縮放功能避免文件內容顯示異常
* `最佳化` 任務面板列表項按相對路徑簡化顯示任務的名稱及路徑
* `最佳化` 程式碼編輯器按鈕文字適當縮寫避免文字內容溢位
* `最佳化` 程式碼編輯器設定字型大小支援恢復預設值
* `最佳化` 提升浮動按鈕點選響應速度
* `最佳化` 佈局控制元件資訊列表按可能的使用頻率重新排序
* `最佳化` 佈局控制元件資訊點選複製時根據選擇器型別自動最佳化輸出格式
* `最佳化` ui.R 及 auto.R 可便捷獲取 AutoJs6 的資源 ID
* `最佳化` 使用浮動視窗選擇檔案時按返回鍵可返回至上級目錄而非直接關閉視窗
* `最佳化` 客戶端模式連線電腦輸入地址時支援數字有效性檢測及點分符號自動轉換
* `最佳化` 部分全域物件及內建模組增加覆寫保護 (參閱 專案文件 > 全域物件 > [覆寫保護](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `最佳化` importClass 和 importPackage 支援字串參數及不定長參數
* `最佳化` ui.run 支援出現異常時列印棧追蹤資訊
* `最佳化` app 模組中與操作應用相關的方法支援 App 型別參數及應用別名參數
* `最佳化` app.startActivity 等支援 url 選項參數 (參閱 範例程式碼 > 應用 > 意圖)
* `最佳化` dialogs 模組中與非同步回呼相關的方法支援省略預填參數
* `最佳化` device 模組獲取 IMEI 或硬體序列號失敗時返回 null 而非丟擲異常
* `最佳化` 提升 console.show 顯示的日誌浮動視窗文字亮度以增強內容辨識度
* `最佳化` ImageWrapper#saveTo 支援相對路徑儲存影像檔案
* `最佳化` 重新設計 colors 全域物件並增加 HSV/HSL 等色彩模式支援 (參閱 專案文件 > [顏色](https://docs.autojs6.com/#/color))
* `依賴` 本地化 Android Job 版本 1.4.3
* `依賴` 本地化 Android Plugin Client SDK For Locale 版本 9.0.0
* `依賴` 本地化 GitHub API 版本 1.306
* `依賴` 附加 JCIP Annotations 版本 1.0
* `依賴` 附加 AndroidX WebKit 版本 1.5.0
* `依賴` 附加 Commons IO 版本 2.8.0
* `依賴` 附加 Desugar JDK Libs 版本 2.0.0
* `依賴` 附加 Jackson DataBind 版本 2.13.3
* `依賴` 附加 Jaredrummler Android Device Names 版本 2.1.0
* `依賴` 附加 Jaredrummler Animated SVG View 版本 1.0.6
* `依賴` 替換 Jrummyapps ColorPicker 版本 2.1.7 為 Jaredrummler ColorPicker 版本 1.1.0
* `依賴` 升級 Android SDK 版本 32 -> 33
* `依賴` 升級 Gradle 版本 7.5-rc-1 -> 8.0-rc-1
* `依賴` 升級 Gradle 構建工具版本 7.4.0-alpha02 -> 8.0.0-alpha09
* `依賴` 升級 Kotlin Gradle 外掛版本 1.6.10 -> 1.8.0-RC2
* `依賴` 升級 Android Material 版本 1.6.0 -> 1.7.0
* `依賴` 升級 AndroidX Annotation 版本 1.3.0 -> 1.5.0
* `依賴` 升級 AndroidX AppCompat 版本 1.4.1 -> 1.4.2
* `依賴` 升級 Android Analytics 版本 13.3.0 -> 14.0.0
* `依賴` 升級 Gson 版本 2.9.0 -> 2.10
* `依賴` 升級 Joda Time 版本 2.10.14 -> 2.12.1
* `依賴` 升級 Kotlinx Coroutines 版本 1.6.1-native-mt -> 1.6.1
* `依賴` 升級 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.7 -> 5.0.0-alpha.9
* `依賴` 升級 Zip4j 版本 2.10.0 -> 2.11.2
* `依賴` 升級 Glide 版本 4.13.2 -> 4.14.2
* `依賴` 升級 JUnit Jupiter 版本 5.9.0 -> 5.9.1

# v6.1.1

###### 2022/05/31

* `新增` 檢查更新/下載更新/更新提示功能 (參閱 設定頁面) (暫不支援 Android 7.x 系統)
* `修復` 應用在 Android 10 系統無法讀寫外部儲存的問題 _[`issue #17`](http://issues.autojs6.com/17)_
* `修復` 程式碼編輯器長按時可能導致應用崩潰的問題 _[`issue #18`](http://issues.autojs6.com/18)_
* `修復` 程式碼編輯器長按選單 "刪除行" 和 "複製行" 功能無效的問題
* `修復` 程式碼編輯器選項選單中 "貼上" 功能缺失的問題
* `最佳化` 部分異常訊息字串資源化 (en/zh)
* `最佳化` 調整內容未儲存對話方塊的按鈕佈局並增加顏色區分
* `依賴` 附加 GitHub API 版本 1.306
* `依賴` 替換 retrofit2-rxjava2-adapter 版本 1.0.0 為 adapter-rxjava2 版本 2.9.0

# v6.1.0

###### 2022/05/26 - 包名變更, 謹慎升級

* `提示` 修改應用包名為 org.autojs.autojs6 避免與開源 Auto.js 應用包名衝突
* `新增` 主頁抽屜增加 "投影媒體權限" 開關 (Root/ADB 方式) (開關狀態檢測為實驗性)
* `新增` 檔案管理器支援顯示隱藏檔案和資料夾 (參閱 設定頁面)
* `新增` 強制 Root 檢查功能 (參閱 設定頁面及範例程式碼)
* `新增` autojs 模組, 用於獲取及操作 AutoJs6 相關資訊 (參閱 範例程式碼 > AutoJs6)
* `新增` tasks 模組, 用於管理定時任務 (參閱 範例程式碼 > 任務)
* `新增` console.launch 方法, 用於開啟日誌頁面
* `新增` util.morseCode 工具, 用於摩斯電碼處理 (參閱 範例程式碼 > 工具 > 摩斯電碼)
* `新增` util.versionCodes 工具, 用於查詢安卓版本資訊 (參閱 範例程式碼 > 工具 > 安卓版本資訊查詢)
* `新增` util.getClass 等方法, 用於獲取 Java 類及類名 (參閱 範例程式碼 > 工具 > 獲取類與類名)
* `新增` timers.setIntervalExt 方法, 用於條件週期執行 (參閱 範例程式碼 > 定時器 > 條件週期執行)
* `新增` colors.toInt/rgba 等方法, 用於顏色轉換 (參閱 範例程式碼 > 影像與顏色 > 基本顏色轉換)
* `新增` automator.isServiceRunning/ensureService 方法, 用於檢查及確保無障礙服務可用
* `新增` automator.lockScreen 等方法, 用於 Android 9 新增的無障礙操作 (參閱 範例程式碼 > 無障礙服務 > Android 9 新增)
* `新增` automator.headsethook 等方法, 用於 Android 11 新增的無障礙操作 (參閱 範例程式碼 > 無障礙服務 > Android 11 新增)
* `新增` automator.captureScreen 方法, 用於獲取無障礙服務螢幕截圖 (參閱 範例程式碼 > 無障礙服務 > 獲取螢幕截圖)
* `新增` dialogs.build 方法支援 animation, linkify 等選項參數 (參閱 範例程式碼 > 對話方塊 > 個性化對話方塊)
* `修復` dialogs.build 方法 inputHint, itemsSelectedIndex 等選項參數功能異常
* `修復` JsDialog#on('multi_choice') 回呼參數功能異常
* `修復` UiObject#parent().indexInParent() 總是返回 -1 的問題 _[`issue #16`](http://issues.autojs6.com/16)_
* `修復` Promise.resolve() 返回的 Thenable 在臨近指令碼結束時可能不被呼叫的問題
* `修復` 包名或類名中可能的拼寫失誤 (boardcast -> broadcast/auojs -> autojs)
* `修復` images.requestScreenCapture 方法在高版本安卓系統可能導致應用崩潰的問題 (API >= 31)
* `修復` 多個指令碼實例同時呼叫 images.requestScreenCapture 方法時可能導致應用崩潰的問題
* `修復` 呼叫 new RootAutomator() 可能出現的假死問題
* `最佳化` RootAutomator 在無 Root 權限時將無法實例化
* `最佳化` 重新設計 "關於應用與開發者" 頁面
* `最佳化` 重構全部內建 JavaScript 模組
* `最佳化` 重構全部 Gradle 構建指令碼並增加公共配置指令碼 (config.gradle)
* `最佳化` Gradle 構建工具支援版本號自動管理及構建檔案自動命名
* `最佳化` Gradle 構建工具增加 task 支援附加 CRC32 摘要到構建檔案 (appendDigestToReleasedFiles)
* `最佳化` shell 方法將呼叫異常寫入返回結果, 不再直接丟擲異常 (無需 try/catch)
* `最佳化` 使用 Rhino 內建的 JSON 替代原 json2 模組
* `最佳化` auto.waitFor 方法支援超時參數
* `最佳化` threads.start 方法支援箭頭函式參數
* `最佳化` console.trace 方法支援日誌等級參數 (參閱 範例程式碼 > 控制檯 > 列印呼叫棧)
* `最佳化` 控制檯字型採用 Material Color 增強普通及夜間主題下的字型可讀性
* `最佳化` device.vibrate 方法支援模式振動及摩斯電碼振動 (參閱 範例程式碼 > 裝置 > 模式振動/摩斯電碼振動)
* `最佳化` 外部儲存讀寫權限適配高版本安卓系統 (API >= 30)
* `最佳化` 儲存 ImageWrapper 所有實例弱引用並在指令碼結束時自動回收 (實驗性)
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
* `依賴` 升級 Rhino 引擎版本 1.7.14 -> 1.7.15-SNAPSHOT
* `依賴` 升級 Zip4j 版本 2.9.1 -> 2.10.0
* `依賴` 移除 Groovy JSON 版本 3.0.8
* `依賴` 移除 Kotlin Stdlib JDK7 版本 1.6.21

# v6.0.3

###### 2022/03/19

* `新增` 多語言切換功能 (尚不完善)
* `新增` recorder 模組, 用於計時 (參閱 範例程式碼 > 計時器)
* `新增` 無障礙服務支援使用 "修改安全設定權限" 自動啟用, 並提供對應設定開關
* `修復` 點選快速設定面板中相關圖示後面板未自動收起的問題 (試修) _[`issue #7`](http://issues.autojs6.com/7)_
* `修復` toast 使用強制顯示參數時可能導致 AutoJs6 崩潰的問題
* `修復` Socket 傳輸資料頭部資訊不完整時可能導致 AutoJs6 崩潰的問題
* `最佳化` 啟動或重啟 AutoJs6 時根據選項設定自動開啟無障礙服務
* `最佳化` 開啟浮動按鈕開關時嘗試自動開啟無障礙服務
* `最佳化` 所有資原始檔補全元素對應的英文翻譯
* `最佳化` 調整主頁抽屜佈局並減小條目間距
* `最佳化` 主頁抽屜增加前臺服務狀態開關的同步
* `最佳化` 主頁抽屜展開時立即按需同步開關狀態
* `最佳化` 顯示指標位置增加狀態檢測及結果提示
* `最佳化` 支援 64 位作業系統 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `最佳化` 浮動按鈕初始化時同時應用透明度設定 (無需點選後再應用透明度)
* `最佳化` 重置檔案內容時增加是否為範例程式碼檔案的檢測並增加結果提示
* `最佳化` 轉移打包外掛下載地址 GitHub -> JsDelivr
* `依賴` 附加 Zeugma Solutions LocaleHelper 版本 1.5.1
* `依賴` 升級 Android SDK 版本 31 -> 32
* `依賴` 升級 Kotlinx Coroutines 版本 1.6.0-native-mt -> 1.6.0
* `依賴` 升級 OpenCV 版本 3.4.3 -> 4.5.4 -> 4.5.5 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依賴` 升級 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.4 -> 5.0.0-alpha.6
* `依賴` 升級 Gradle 構建工具版本 7.2.0-beta01 -> 7.3.0-alpha06
* `依賴` 升級 Auto.js-ApkBuilder 版本 1.0.1 -> 1.0.3
* `依賴` 升級 Glide Compiler 版本 4.12.0 -> 4.13.1
* `依賴` 升級 Gradle 版本 7.4-rc-2 -> 7.4.1
* `依賴` 升級 Gson 版本 2.8.9 -> 2.9.0
* `依賴` 降級 Android Material 版本 1.6.0-alpha02 -> 1.5.0

# v6.0.2

###### 2022/02/05

* `新增` images.bilateralFilter 方法, 用於影像雙邊濾波
* `修復` 多次呼叫 toast 只生效最後一次呼叫的問題
* `修復` toast.dismiss 方法可能無效的問題
* `修復` 客戶端模式及服務端模式開關可能無法正常工作的問題
* `修復` 客戶端模式及服務端模式開關狀態不能正常重新整理的問題
* `修復` Android 7.x 解析 UI 模式 text 元素異常 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `最佳化` 忽略 sleep 方法丟擲的 ScriptInterruptedException 異常
* `依賴` 附加 AndroidX AppCompat (Legacy) 版本 1.0.2
* `依賴` 升級 AndroidX AppCompat 版本 1.4.0 -> 1.4.1
* `依賴` 升級 AndroidX Preference 版本 1.1.1 -> 1.2.0
* `依賴` 升級 Rhino 引擎版本 1.7.14-SNAPSHOT -> 1.7.14
* `依賴` 升級 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.3 -> 5.0.0-alpha.4
* `依賴` 升級 Android Material 版本 1.6.0-alpha01 -> 1.6.0-alpha02
* `依賴` 升級 Gradle 構建工具版本 7.2.0-alpha06 -> 7.2.0-beta01
* `依賴` 升級 Gradle 版本 7.3.3 -> 7.4-rc-2

# v6.0.1

###### 2022/01/01

* `新增` 連線 VSCode 外掛支援客戶端 (LAN) 及服務端 (LAN/ADB) 方式 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` base64 模組, 用於 Base64 編解碼 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` isInteger/isNullish/isObject/isPrimitive/isReference 全域方法, 用於型別判斷
* `新增` Object.getOwnPropertyDescriptors 相容實現 (polyfill)
* `新增` Array.prototype.flat 相容實現 (polyfill)
* `最佳化` global.sleep 方法支援隨機範圍及負數參數
* `最佳化` global.toast 方法支援時長設定, 強制覆蓋及 dismiss 操作
* `最佳化` 包名物件全域化 (okhttp3/androidx/de)
* `依賴` 升級 Android Material 版本 1.5.0-beta01 -> 1.6.0-alpha01
* `依賴` 升級 Gradle 構建工具版本 7.2.0-alpha04 -> 7.2.0-alpha06
* `依賴` 升級 Kotlinx Coroutines 版本 1.5.2-native-mt -> 1.6.0-native-mt
* `依賴` 升級 Kotlin Gradle 外掛版本 1.6.0 -> 1.6.10
* `依賴` 升級 Gradle 版本 7.3 -> 7.3.3

# v6.0.0

###### 2021/12/01

* `新增` 主頁抽屜底部增加重啟應用按鈕
* `新增` 主頁抽屜增加 "忽略電池最佳化"/"顯示在其他應用上層" 等開關
* `修復` 應用初始安裝後部分割槽域主題顏色渲染異常的問題
* `修復` sign.property 檔案不存在時無法構建專案的問題
* `修復` 定時任務面板一次性任務的月份存取錯誤
* `修復` 應用設定頁面開關顏色不隨主題變更的問題
* `修復` 無法識別打包外掛及打包外掛下載地址無效的問題
* `修復` 主頁抽屜 "檢視使用情況權限" 開關狀態可能不同步的問題
* `修復` TemplateMatching.fastTemplateMatching 潛在的 Mat 記憶體洩漏問題
* `最佳化` ViewUtil.getStatusBarHeight 提升相容性
* `最佳化` 主頁抽屜移除使用者登入相關模組並移除佈局佔位
* `最佳化` 主頁移除社群及市場標籤頁面並最佳化佈局對齊方式
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
* `依賴` 附加 AndroidX Preference 版本 1.1.1
* `依賴` 附加 AndroidX SwipeRefreshLayout 版本 1.1.0
* `依賴` 升級 Android SDK 版本 28 -> 30 -> 31
* `依賴` 升級 Android Analytics 版本 7.0.0 -> 13.1.0
* `依賴` 升級 Android Annotations 版本 4.5.2 -> 4.8.0
* `依賴` 升級 Gradle 構建工具版本 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `依賴` 升級 Android Job 版本 1.2.6 -> 1.4.2
* `依賴` 升級 Android Material 版本 1.1.0-alpha01 -> 1.5.0-beta01
* `依賴` 升級 AndroidX MultiDex 版本 2.0.0 -> 2.0.1
* `依賴` 升級 Apache Commons Lang3 版本 3.6 -> 3.12.0
* `依賴` 升級 AndroidX AppCompat 版本 1.0.2 -> 1.4.0
* `依賴` 升級 ButterKnife Gradle 外掛版本 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `依賴` 升級 ColorPicker 版本 2.1.5 -> 2.1.7
* `依賴` 升級 Espresso Core 版本 3.1.1-alpha01 -> 3.5.0-alpha03
* `依賴` 升級 EventBus 版本 3.0.0 -> 3.2.0
* `依賴` 升級 Glide Compiler 版本 4.8.0 -> 4.12.0
* `依賴` 升級 Gradle Build Tool 版本 29.0.2 -> 30.0.2
* `依賴` 升級 Gradle 版本 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `依賴` 升級 Groovy-Json 外掛版本 3.0.7 -> 3.0.8
* `依賴` 升級 Gson 版本 2.8.2 -> 2.8.9
* `依賴` 升級 Java 版本 8 -> 11 -> 16
* `依賴` 升級 Joda Time 版本 2.9.9 -> 2.10.13
* `依賴` 升級 JUnit 版本 4.12 -> 4.13.2
* `依賴` 升級 Kotlin Gradle 外掛版本 1.3.10 -> 1.4.10 -> 1.6.0
* `依賴` 升級 Kotlinx Coroutines 版本 1.0.1 -> 1.5.2-native-mt
* `依賴` 升級 LeakCanary 版本 1.6.1 -> 2.7
* `依賴` 升級 LicensesDialog 版本 1.8.1 -> 2.2.0
* `依賴` 升級 Material Dialogs 版本 0.9.2.3 -> 0.9.6.0
* `依賴` 升級 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.2 -> 5.0.0-alpha.3
* `依賴` 升級 ReactiveX RxJava2 RxAndroid 版本 2.0.1 -> 2.1.1
* `依賴` 升級 ReactiveX RxJava2 版本 2.1.2 -> 2.2.21
* `依賴` 升級 Retrofit2 Converter Gson 版本 2.3.0 -> 2.9.0
* `依賴` 升級 Retrofit2 Retrofit 版本 2.3.0 -> 2.9.0
* `依賴` 升級 Zip4j 版本 1.3.2 -> 2.9.1
* `依賴` 升級 Rhino 引擎版本 1.7.7.2 -> 1.7.13 -> 1.7.14-SNAPSHOT
* `依賴` 升級 OpenCV 版本 3.4.3 -> 4.5.4