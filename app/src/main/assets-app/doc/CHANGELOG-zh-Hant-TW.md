******

### 發行歷史

******

# v6.8.0

###### 2026/10/07

* `提示` 螢幕取色外掛更名為 3-Color Picker 2.0, 使用新套件名稱並需重新安裝和啟用, 舊版可保留並存但設定不自動遷移; 首頁抽屜的螢幕取色文案保持不變
* `新增` 介面動作將確認綁定至宿主檢查的節點, 支援文字追加與有界捲動, 並回報執行結果和視窗變化
* `新增` 宿主回報已授權 OCR 插件可用時提供螢幕 OCR, 辨識結果合併為帶座標的有界文字行
* `新增` ai.agent 腳本 API, 支援任務啟動, 查詢, 事件, 詢問回應, 確認, 取消與 detached 背景任務; result/context 提供 @agent 登記腳本結果和上下文, 附三個任務範例
* `新增` ai.agent.run 與 ai.agent.create 的選項接受布林值 plan: true 時 Three Stove Agent 進入計畫模式, 模型先提出 1 到 8 步計畫並經 kind 為 plan 的 input 事件審閱後才執行首個工具; 省略沿用預設的計畫模式開關
* `新增` Three Stove Agent 支援執行經確認的生成 JavaScript, 取消或逾時會停止本次指令碼, 傳回結構化結果和主控台輸出; 指令碼使用宿主權限, 不提供檔案存取沙箱
* `新增` ai.agent: 本機或外部 MCP 伺服器的所選工具, 按伺服器設定風險等級, mcp 工具群組預設關閉
* `新增` Three Stove Agent 模型代理支援向視覺模型傳入圖片及工具結果圖片 (需要對應外掛程式支援)
* `新增` Android 17 本機網路權限管理, 支援側欄, 設定頁及 VS Code 連線時按需申請, 並按實際 targetSdk 宣告權限
* `新增` 開發者選項頁支援檢視 Android 11 及以上的程序結束記錄, 包含多程序近期系統原因, 記憶體限制器識別與可複製報告 (入口: 關於應用與開發者頁面長按應用程式圖示)
* `新增` MCP 伺服器抽屜開關提供安裝, 啟用, 授權與相容性引導, 與通知停止操作同步, 重新連線時保留外掛程式設定, 抽屜及外掛程式中心透過權限檢查開啟同一設定頁. AutoJs6 開啟時恢復先前啟用的伺服器, 但遵循宿主離線期間的使用者停止操作, 不隨裝置開機自啟.
* `新增` MCP 工作目錄能力支援 Android 應用程式查詢, 編輯器行列定位, 按設定策略自動啟用無障礙, Shell 輸出限制與大型 JSON 結果的唯讀檔案描述符傳輸; root 執行使用獨立 shell.root 授權和手機端開關
* `新增` 獨立 QuickJS Runtime 外掛引擎, 用於本機單一檔案 JavaScript 和 ES module, 透過 "quickjs"/"qjs" 指令或 `.quickjs.js/.quickjs.mjs` 標記路由, 且不會回退至 Rhino, Node.js 或 Bun
* `新增` `device.pageSize` 唯讀屬性, 傳回目前系統的記憶體頁大小 (位元組), 支援識別 4 KB / 16 KB 執行環境
* `新增` 獨立 Bun Runtime 外掛引擎, 用於本機單一檔案 JavaScript 和 TypeScript, 透過 "bun" 指令或 `.bun.js/.bun.ts/.bun.tsx` 標記路由, 且不會回退至 Rhino 或 Node.js
* `新增` 側拉抽屜工具分組在浮動按鈕與指標位置後加入可選的螢幕取色外掛, 作為第三個預設關閉的工具; 安裝, 啟用, 運行狀態與卸載均會在不阻塞 UI 的情況下自動更新
* `新增` MCP Server 外掛契約模組 (plugin-api/mcp-server-api), 含凍結的 AIDL 介面, 以專用綁定租約綁定官方外掛並在外掛程序死亡後最多重綁三次的嚴格宿主客戶端, 受 grant 約束並經 Node bridge provider 分派外掛請求的宿主能力代理, 以及預設關閉的外掛中心註冊
* `新增` 側拉抽屜 "無障礙服務" 條目的管理器 (對話框形式, 點選說明對話框的 "管理器" 按鈕或長按條目開啟): 狀態區即時顯示服務狀態, 系統設定, 程序內實例, 執行時長, 事件計數與最近事件, 視窗與作用中視窗根節點, 最近套件名稱與活動, 服務組態, root 與安全設定可用性及監聽者數量; 控制區提供啟動, 停止, 重新啟動, 清除節點快取, 自檢 (統計視窗根節點與節點數量並等待事件投遞) 及事件監視器 (即時列出最近的無障礙事件及其間隔, 支援暫停, 清空, 僅視窗狀態變化與複製); 設定區提供 root 與安全設定自動啟用開關; 完整診斷資訊可一鍵複製; 狀態區與控制區標題右側提供重新整理及逐級增減顯示詳略的按鈕 (狀態 4 級, 預設僅顯示最基本欄位, 全部隱藏時標題附帶服務狀態如 "狀態 [可操作]"; 控制 3 級, 預設僅顯示啟動 / 停止 / 重新啟動三列, 全部隱藏時這三個按鈕移入標題列), 所選等級會被記住
* `新增` 無障礙服務管理員權限項目可開啟含快取和手動候選的 root 管理應用程式清單, 安全設定 ADB 授權及 Shizuku 授權或設定; 設定區提供穩定模式開關, 狀態區提供啟用 / 停用建議
* `新增` `mediainfo` 新增顯式版本化的 `snapshot`/`capabilities` API 與 v1/v2 schema 常數; Rhino 保持舊 `read` 結果不變, Node.js 保持宿主 v1 預設結果, 僅在外掛明確宣告支援時透傳外掛 v2 並公開簡短引擎版本
* `新增` 檔案內單一檔案可點選解壓縮至私有暫存目錄並執行或開啟, 保留執行環境檔名後綴; APK/APKS/XAPK/APKM/APKZ/AAB 可透過檔案選單及資訊對話框更多選單查看和解壓縮, 保留原有檔案列按鈕
* `新增` Explorer Action 主機檔案資訊能力 v1 允許已信任的唯讀外掛在現有 APK 資訊對話框中附加有界、經 SHA-256 綁定的本地化摘要; 外掛缺少、停用、不相容或失敗時, 原有欄位、安裝/資訊清單操作與失敗處理均保持不變
* `新增` `pangu` 全域物件, 內建 pangu.js 10.1.0, 支援 `spaceText` 文字混排間距處理與 `hasProperSpacing` 檢查, 同步 TypeScript 宣告與文件
* `新增` `pinyin` 新增僅目前呼叫生效的 `customDictionary` 自訂讀音覆寫, 完善 `compare` 與 `compact`, 並透過具備明確 `pinyin` capability 的 `autojs6:bridge.callAutoJs` 向 Node.js 開放相同外置外掛能力
* `新增` `pinyin4j` 新增經 capability 協商的 `readings` 全讀音矩陣, 支援 Hanyu, Tongyong, Wade-Giles, MPS2, Yale 與 Gwoyeu Romatzyh; 舊外掛僅對 Hanyu 安全回退, 相同外置外掛能力透過具備明確 `pinyin4j` capability 的 `autojs6:bridge.callAutoJs` 向 Node.js 開放
* `新增` OpenCC 外掛契約版本 2 支援動態轉換類型探索, 最多 1024 段的批次轉換與最多 32 階段的鏈式轉換; 16 個組合方法在新外掛上從最多 3 次 Binder 往返降至 1 次, 舊外掛透明回退原有逐項或逐階段呼叫
* `新增` Image Quantization 選項透過 `OPTIONS_API_VERSION>=4` 新增 `maxPixels` 與 `maxMemoryBytes`, 預設限制 16000000 像素和 256 MiB 額外工作記憶體; 超出預算時回傳帶資源詳情的型別化例外, 結果報告計入預算的峰值記憶體, 停止腳本會取消其活動原生量化
* `新增` `images.quantizeToFile` 將索引 PNG 直接串流寫入磁碟並回傳大小與品質指標; Image Quantization 選項新增 `preserveAlpha`, 常見 Bitmap 配置, 硬體輸入與廣色域輸入透過 `OPTIONS_API_VERSION>=3` 統一正規化為 sRGB
* `新增` `images.quantize` 現會回傳 PNG 位元組, 大小, 實際品質與量化誤差; 省略品質下限時採用盡力而為 (`minQuality=0`), 明確下限無法滿足時拋出帶錯誤碼 `PNG_QUANTIZATION_QUALITY_TOO_LOW` 的 `PngQuantBridge.QualityTooLowException`; 結果介面要求 `OPTIONS_API_VERSION>=2`
* `新增` `images.save`, `images.saveImage`, `images.compress` 與 `images.compressToBytes` 現支援 PNG 量化具名選項, 可控制調色盤大小, 速度, 獨立品質範圍, 抖動和 posterize; 數字 `quality` 保持向後相容, 選項物件要求 Image Quantization 外掛程式宣告 `OPTIONS_API_VERSION>=1`
* `新增` `engines.execScript` 記憶體 TypeScript 即時執行: `.ts/.tsx/.mts/.cts` 原始碼名稱經獨立編譯器處理, 診斷與 Source Map 堆疊對應和檔案指令碼一致, Rhino 回傳最終運算式值, 完整記錄頁及編輯器記錄面板可互動求值單行 TypeScript 片段
* `新增` compiler-backed Node TypeScript 專案現可按需編譯執行期間建立的 `.ts/.mts/.cts`: Node 透過綁定精確位元組數與 SHA-256 的有界 PFD 將原始碼交給主機, 主機重用常駐專案工作階段, 保留型別診斷與 Source Map 堆疊對應, 並繼續拒絕路徑逸出, 符號連結和歧義目標
* `新增` Ace 編輯器現會在儲存後增量重新整理常駐 TypeScript 專案語言服務, 並以 450 ms 停頓 debounce 和儲存後 50 ms 診斷排程延遲取消過期工作; 20 個檔案的真機驗收在不阻塞輸入的前提下, 從儲存完成到 TS2322 重新整理實測 201 ms
* `新增` 編輯器 contract 5 新增專案範圍 TypeScript 符號重新命名: Ace 提交附帶精確原始碼身分的有界逐檔案編輯, Host 獨立重新驗證乾淨的專案快照, 顯示檔案與編輯數量預覽, 凍結作用中的緩衝區, 並在重新整理診斷前以原子方式 staging, 發佈或回復全部檔案
* `新增` 編輯器 contract 4 新增由主機授權的 TypeScript 自動匯入與拼字修正, 目標僅限目前緩衝區: 請求只能攜帶有界且有序的 UTF-16 編輯及精確的修改前後 SHA-256, 主機會獨立重播並在一次性核准前拒絕唯讀, 過期, 畸形, 重疊或遭竄改的操作
* `新增` 編輯器 contract 3 新增經專案快照授權的 TypeScript 定義跳轉: Ace 可請求專案原始碼及凍結相依性宣告中的位置, 主機會在開啟目標檔案並定位前獨立複驗專案根目錄, 規範相對路徑, 原始碼或相依性清單指紋, 內容 SHA-256 與範圍; 符號連結, 過期快照及不相符的未儲存內容均會被拒絕
* `新增` 編輯器 contract 2 現會向 Ace 提供與執行前編譯相同的有界 TypeScript 專案原始碼快照, 從而即時跨檔案診斷錯誤 import 路徑, 同時繼續相容 contract 1 編輯器外掛
* `新增` TypeScript 相依性快照現在會拒絕原生 addon 訊號 (`.node`, `binding.gyp` 與 `gypfile`) 和安裝生命週期 hook (`preinstall`, `install` 與 `postinstall`), 提供穩定錯誤碼及純 JavaScript/WASM 替代指引; 一般 package script 仍僅作為惰性中繼資料保留
* `新增` 宿主 TypeScript 依賴契約已同步 resolver policy revision 3, 並包含 dayjs, lodash, zod, uuid 與 nanoid 真實發行 manifest 覆蓋, 鎖定 Rhino CommonJS 入口選擇與穩定的 ESM-only 錯誤
* `新增` TypeScript 專案編譯與 Ace 現共用同一凍結 `node_modules` 型別層: package `types`/`typings`, TypeScript 6 `typesVersions`, 巢狀宣告及已安裝 `@types` 在 Rhino 和 Node profile 產生一致的 dayjs 補全, hover 與 strict TS2322 診斷
* `新增` Lua Runtime 主機能力新增依檔案腳本隔離的持久鍵值儲存與 Toast: `autojs.storage.get/put/remove/clear` 使用應用程式私有, 同步提交且有界的命名空間, `autojs.ui.toast` 透過主機主執行緒單次派發
* `新增` Ace LSP 預設檔案類型新增 .mts/.cts 及其宣告檔變體, 讓外部 Ace 編輯器可選擇與執行一致的 Rhino/Node TypeScript profile; 舊版預設選擇會自動遷移, 明確自訂副檔名集合保持不變
* `新增` APK Builder 支援在打包階段編譯 Rhino TypeScript 專案; 最終 APK 僅包含加密 JavaScript 與不內嵌原始碼的可選 Source Map, 不包含 TypeScript 原始碼或編譯器; 加密專案使用一次性 AES-256-GCM 暫存區, 解密與重新加密的明文僅存於記憶體並在使用後清零
* `新增` Node.js 24.5.0 腳本執行環境, 支援透過 "nodejs" 指令或 .node.js/.cjs/.mjs 等副檔名執行腳本
* `新增` Node.js Java 互操作由宿主宣告式白名單統一管理, 支援 Math, UUID, Rect, Point, Color, Locale, Build, Build.VERSION 與 TimeUnit, 並向外掛公開 `java.policy` 與靜態欄位讀取
* `新增` Node.js 宿主媒體播放工作階段及 media_store 媒體庫支援, 包含暫停, 繼續, 定位, 狀態與結束清理, 以及 MediaStore 查詢, 匯入, 重新命名, 匯出, 掃描和刪除; media.playback, media.library 與 media.library.mutate 分別授權
* `新增` Node.js 的 ESM/dynamic import()/網路與 WebSocket/原生網路模組/worker_threads/child_process/Java 互通能力成為正式功能並預設啟用, 同時保留權限宣告/允許清單/沙箱及資源預算保護
* `新增` Node.js 執行環境支援 CommonJS/部分 ESM/僅限本機的動態 import()/輕量 TypeScript (.ts/.mts/.cts) 原始碼
* `新增` Node.js 執行環境支援單次執行範圍的隨選加密模組原始碼提供者, 僅探測及解密模組解析器實際選定的候選檔案
* `新增` Node.js 執行環境支援受控 AutoJs6 橋接模組, 並採用 Safe Node Profile 安全策略
* `新增` Node.js 執行環境支援常駐服務處理程序及處理程序層級的 Node/V8 平台重複使用, 同時為每次腳本執行建立獨立 isolate 及 Environment
* `新增` Node.js 常駐執行環境支援閒置或記憶體壓力下的回收, 明確關閉及異常處理程序隔離重建
* `新增` Node.js 外部執行階段插件後端, 支援強制 `plugin` 與可選回退 `pluginOptional` 模式, 常駐 Binder 工作階段及處理程序執行環境, 信任/合約/ABI 驗證和單一活動零佇列背壓
* `新增` Node.js 外部執行階段插件後端支援請求層級工作區封存傳輸 v2, 包含明確輸入對應/輸出回寫/刪除 tombstone 清單, 且不掃描宿主沙盒
* `新增` Node.js Runtime 偵錯版本支援按次執行明確開啟僅綁定本機迴環位址的 Inspector/CDP 工作階段, release 版本, 遠端監聽及阻塞等待保持禁止
* `新增` 打包功能支援 Node.js 腳本專案, 並自動包含所需執行階段程式庫
* `新增` 獨立 TypeScript 編譯外掛客戶端, 驗證凍結的 AIDL/R2 封存協定, 支援 UTF-8 單一檔案 Rhino 編譯, 有界診斷及強制 Source Map 輸出
* `新增` 本機單一檔案 .ts 與 Rhino .tsx 腳本會先經獨立編譯外掛再由 Rhino 執行, 保留 UTF-8 來源檔案識別與 Source Map 輸出, 型別錯誤在執行前拒絕, 且絕不回退至 Node.js 外掛或直接執行原始 TypeScript; 不支援的宣告檔案及編譯器不可用情境會傳回明確的本地化錯誤
* `新增` 帶有 "ui" 模式參數的本機單一檔案 .ts/.tsx 腳本支援編譯後在 Rhino Activity 中執行字串 XML 及原生 TSX 版面配置, 包含受控內建標籤, Fragment, 純量屬性, 檢視查找, 點擊回呼, 文字更新及明確關閉 UI
* `新增` TypeScript 編譯診斷以宿主 ScriptCompilationException 記錄提供來源檔案, 1 基行列, 嚴重層級與 TS 錯誤碼; Console 與編輯器底部日誌面板顯示可點擊的乾淨位置, 可跳轉至對應原始碼位置
* `新增` Rhino TypeScript 執行例外會取用編譯器 Source Map v3, 將產生的 JavaScript 堆疊框架映射回原始 .ts 檔案與行號, 不虛構 Rhino 未提供的欄號, Console 與編輯器日誌面板中的映射位置可直接點擊跳轉
* `新增` 本機單一檔案 .mts/.cts 腳本會先經獨立 TypeScript 編譯外掛的 Node profile 編譯為 .mjs/.cjs, 再交由 Node.js Runtime 外掛執行, 保留 UTF-8 檔名並略過舊有 TypeScript 正規表示式剝離; 單一檔案 Node .ts 在專案 package 中繼資料支援完成前會遭明確拒絕
* `新增` Node TypeScript 執行例外會取用編譯器 Source Map v3, 將 V8 堆疊框架映射回原始 .mts/.cts 檔案的精確行列, Console 與編輯器日誌面板中的映射位置可直接點擊跳轉
* `新增` TypeScript 專案現在會將有界專案快照作為單一 Program 交給 Rhino 或 Node profile 編譯, 保留相對 import, CommonJS 循環相依, JSON 執行階段檔案, package `type` 上下文, 匯入模組診斷及逐模組 Source Map 堆疊映射; package 中繼資料保持嚴格驗證並拒絕 lifecycle scripts
* `新增` TypeScript 編譯結果可依原始碼圖, 編譯器與宣告指紋, profile 與 policy 修訂及 package 中繼資料組成的語意鍵跨執行快取; 未修改指令碼會略過編譯器 prewarm 與 Worker 編譯, 已驗證輸出封存在重用前仍會重新驗證, 損毀或超額項目自動回退至正常編譯
* `新增` TypeScript 專案從編譯到執行共用一份精確唯讀 `node_modules` 快照; Rhino 載入 CommonJS/JSON 及 dual-package 的 `require` 分支並提供穩定 ESM-only 錯誤, Node 接收不可變私有 workspace overlay 並執行 ESM/CJS/dual package, 且不會回寫相依變更
* `新增` 定時任務, 廣播任務, 捷徑及 Tasker/本機 Run Intent 現在與前景啟動共用同一條 TypeScript preparation 管線; Tasker 前置 JavaScript 會維持在已編譯的 Rhino/Node 輸出之前執行, Source Map 行偏移也會計入此前綴, 不再略過編譯並直接執行原始 TypeScript
* `新增` 專案資料夾支援直接建立定時任務與廣播任務, 觸發時依 project.json 重新解析 Rhino/Node.js/Python 進入點, 並還原專案工作目錄及 useFeatures 設定
* `新增` 獨立 Python Runtime 外掛後端, 支援透過單獨的 CPython 外掛處理程序執行本機 .py 檔案及宣告為 `type: python` 的腳本專案, 並驗證固定元件/簽章/協定/ABI, 支援熱插拔且絕不回退至 JavaScript
* `新增` Python 啟動整合涵蓋檔案管理器/編輯器/本機 Run Intent/單一檔案 Import Intent/新建快捷方式/定時任務/廣播任務並嚴格保留單一檔案或專案上下文; 專案支援內部 import/相對檔案讀取及唯讀 `autojs6.app/device/execution/project` API
* `新增` Python Runtime 執行支援透過 ExecutionConfig 鍵 `autojs6.python.stdinSnapshot` 提供可選的有界預置 stdin 快照, 並採用嚴格 ByteArray/UTF-8 文字准入, 提供者能力/大小協商及私有唯讀 PFD 傳輸與清理
* `新增` Python Runtime 協定 1.4 為每次執行協商明確嚴格 JSON 結果與可選輸出 artifact, 對數量, 正規化路徑, 單個/合計大小, 精確 PFD 引用與 SHA-256 設限; Host 僅接收型別化結果且絕不從 stdout 推斷
* `新增` Python Runtime 協定 1.5 新增執行層級同步 Host 能力 broker, 驗證固定外掛 UID, 採用嚴格有界 JSON, 呼叫配額, 防重放與生命週期約束, 執行終態後拒絕呼叫; 完整首批低風險能力向 Python 指令碼開放 toast, 剪貼簿讀寫, 應用程式啟動/開啟 URL, 動態電量/螢幕/亮度/音量資訊, 分級宿主控制台輸出及權限感知通知
* `新增` Python Runtime 協定 1.5 Host 能力 broker 支援在目前專案根目錄或單一檔案指令碼目錄內進行有界 UTF-8 文字讀寫, 路徑存在/類型檢查及排序的直接目錄列舉; 正規化相對路徑與穩定上限阻止存取越過執行根目錄
* `新增` Python Runtime 協定 1.5 Host 能力 broker 僅向經使用者授權的前景啟動開放有界 alert/confirm/prompt/select 對話框; 背景啟動穩定傳回 INTERACTIVE_NOT_ALLOWED, 取消或執行終態清理會關閉作用中對話框 UI
* `新增` Python Runtime 協定 1.5 Host broker 開放不含絕對路徑的目前引擎資訊, 確定性停止自身及最多 16 次限定範圍的非 Python Host 子指令碼非同步啟動; provider 保持單一工作階段期間巢狀 Python 穩定傳回 NESTED_PYTHON_NOT_ALLOWED
* `新增` Python Runtime 協定 1.5 Host broker 開放有界的無障礙點擊, 長按, 定時按壓, 滑動, 返回及主畫面操作; 座標與持續時間經過嚴格驗證, 無障礙服務不可用時穩定傳回 ACCESSIBILITY_UNAVAILABLE
* `新增` Python Runtime 協定 1.5 Host broker 開放有界且綁定單次執行的無障礙樹快照與 AND 組合選擇器查詢, 透過不透明節點引用執行明確點擊與文字設定; 快照, 掃描, 字串, 保留節點及回應均有固定上限, 無障礙不可用, 掃描不完整與節點過期時傳回穩定的型別化錯誤
* `新增` Python Runtime 協定 1.5 Host broker 透過 Android 11+ 無障礙服務開放執行級螢幕截圖, 以 PNG/JPEG 編碼並只保留一張影像, 透過 32 KiB 順序分塊及 SHA-256 中繼資料傳輸, 編碼大小上限為 4 MiB, 支援明確釋放並在終態覆寫位元組; 不開啟權限介面, 無障礙不可用, 截圖失敗, 影像控制代碼過期及超限均傳回穩定的型別化錯誤
* `新增` Python Runtime 協定 1.5 Host broker 可在最新的 Android 11+ 無障礙截圖中依可選區域與逐通道閾值搜尋有界 RGB 色彩, 依列優先傳回首個座標或確定性的未命中結果, 不傳輸或保留影像位元組
* `新增` Python Runtime 協定 1.5 Host broker 接收一份執行層級 PNG/JPEG 範本, 透過 24 KiB 順序分塊、1 MiB 編碼上限及 SHA-256 驗證上傳, 解碼上限為單邊 2048 像素及總計 100 萬像素, 並以不透明範本像素與 RGB 閾值在最新的 Android 11+ 無障礙截圖中依列優先確定性比對; 生命週期、區域及比較預算均 fail-closed, 且不依賴 OpenCV 執行階段, Android 強制的 333 ms 截圖請求間隔僅在收到精確的間隔過短錯誤時於 350 ms 後在既有截止時間內有限重試
* `新增` Python Runtime 明確嚴格 JSON 結果在腳本終態後以 `[result]` 前綴寫入宿主控制台, 已驗證輸出 artifact 原子發布到專案或腳本目錄下的 `.python-artifacts` 執行專屬目錄並列印絕對路徑; 此保留目錄不進入後續專案快照
* `新增` 獨立 Lua Runtime 外掛後端, 支援透過單獨的 PUC Lua 5.4.8 外掛處理程序執行本機 .lua 檔案, 並驗證固定元件/簽章/協定/ABI, 提供有界執行且絕不回退至 JavaScript
* `新增` 獨立 Java/Kotlin JVM Source Runtime 外掛後端, 支援在隔離處理程序中編譯及執行單一 .java/.kt 指令碼, 提供 package/import, 即時 console 輸出, 可取消 sleep, 主程式 toast 與明確能力協商
* `新增` JVM Source Runtime 支援範圍為單一檔案 Java 8 (Java provider 最低 API 24) 與 Kotlin 2.3 (Kotlin provider 最低 API 26); 本次發布僅在 API 31/arm64-v8a 實機完成 smoke, 不宣告未驗證 API 層級
* `新增` JVM Source Runtime 顯式執行透過協定 1.6 / Entry API 4 支援有界 JSON 參數, 獨立授權的剪貼簿存取, Java 多檔案原始碼套件與指定入口類別, 安全的執行階段例外定位和執行觀測; 既有協定 1.1 / Entry API 2 外掛程式保持單檔案執行相容
* `新增` `ocr` 預設依 ML Kit/Paddle/Rapid 順序動態選擇引擎; `ocr.mode` 回傳選中的引擎或空字串, 明確指定的模式保持固定, 可透過 `mode` 或 `tap` 設定 `auto`, 空字串或 nullish 值以恢復自動選擇
* `新增` 外掛系統支援 Node.js/ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization/文字轉換/媒體資訊等擴充介面
* `新增` 外掛系統提供版本化 TaggedWire 傳輸及 DEX 編譯與 AI Provider 文字生成 v1 開發協定套件, 並配備專用符合性測試應用程式及惡意情境夾具
* `新增` AutoJs6 官方外掛可透過簽章權限保護的版本化唯讀設定協定, 讀取主程式主題色, 深色模式策略及語言的外觀快照
* `新增` 外掛系統支援離線專案文件擴充介面
* `新增` 檔案管理器支援單一檔案唯讀 Explorer Action 外掛擴充介面, 並在主檔案頁面的更多選單中動態載入外掛動作
* `新增` 檔案管理器支援 Explorer Action 協定 v2 主要操作, 透過三個獨立外掛提供圖片檢視, 音訊播放及影片播放
* `新增` 檔案管理器支援 Explorer Action 協定 v3 輸出交易, 提供有界唯寫 content URI/輸出驗證/當機復原及同目錄檔案原子發布
* `新增` 檔案管理器 Explorer Action 協定 v4 支援有序檔案/資料夾/混合目標及同上層多選動作, 目錄選單與五項上圖示下文字操作列可顯示外掛壓縮動作, 並透過請求級主程式檔案工作階段受控讀取目標及原子發布同層輸出
* `新增` 檔案管理器 Explorer Action 協定 v5 支援可信單一檔案唯讀外掛透過固定 UID 的 Binder 工作階段, 在原生 Explorer 清單/路徑列/主題及返回堆疊中分頁顯示壓縮檔目錄
* `新增` 檔案管理器 Explorer Action 協定 v6 支援透過主程式現有預覽器直接預覽壓縮檔內可讀取的文件, 圖片, 音訊與影片項目
* `新增` 檔案管理器 Explorer Action 協定 v7 至 v10 支援待提交檔案強制讀回驗證, 原始目標不可分割取代, 具備用戶端結束和重新啟動復原的主程式持有目錄輸出交易, 以及具備有界進度與取消的原生目前目錄/選取項目解壓縮, 讓壓縮檔外掛無需儲存空間權限即可修改所選 ZIP 並安全發布已驗證輸出
* `新增` 檔案管理器 Explorer Action 協定 v11 新增有界的主程式原生密碼復原, 可在首次開啟壓縮檔或解壓縮時要求及重試密碼; 重試會保留壓縮檔路徑與選取項目, 每次要求後清理暫時密碼緩衝
* `新增` 檔案管理器 Explorer Action 協定 v12 提供有界並綁定壓縮檔工作階段的同層分割檔來源; 壓縮檔外掛只能透過不透明 ID 開啟主程式核准的 ZIP 及 RAR 卷唯讀描述元, 不會取得目錄或檔案系統路徑
* `新增` 檔案管理器 Explorer Action 協定 v13 支援壓縮檔外掛在同一唯讀工作階段內重建檔名解碼; 原生路徑列可選擇 ZIP 編碼, 並保留最深可用內部路徑與仍存在的已選項目
* `新增` 檔案管理器 Explorer Action 協定 v14 新增有界複合檔名後綴比對; 壓縮檔外掛可精確登記 `.zip.001` 與 `.7z.001` 首卷並重用綁定工作階段的 v12 同層卷來源, 不會比對任意 `.001` 檔案或取得目錄路徑
* `新增` 檔案管理器 Explorer Action 協定 v15 新增可復原多檔案輸出批次; 外掛可在所有待提交檔案驗證後統一發佈, 宿主在發佈前持久記錄父目錄與檔案身分, 失敗或重新啟動時只復原身分仍相符的成員
* `新增` 檔案管理器 Explorer Action 協定 v16 新增由宿主持有的來源項目回收桶交接; 只接受完整原始選擇與所有已提交輸出的精確證明, 宿主先持久化復原副本再移除來源項目, 不授予外掛任意路徑或刪除權限
* `新增` 檔案管理器 Explorer Action 協定 v17 只在一般主要操作未比對時提供明確唯讀的「作為壓縮檔案開啟...」入口; 既有工作階段執行一次結構偵測並可在原生路徑列標示實際格式, 建立選單時不在背景掃描檔案且不新增檔案權限
* `新增` 檔案管理器 Explorer Action 協定 v18 在受控取代前將目標上一版本保存在宿主私有持久儲存中, 只公開不透明的有界歷史中繼資料; 只有目前檔案仍精確符合已提交取代結果時才允許還原一次, 不覆蓋外部改寫並保留中斷復原證據
* `新增` 檔案管理器 Explorer Action 協定 v19 支援可寫壓縮檔工作階段透過原生重新命名與刪除管理受支援項目; 改寫經主機持有的已驗證不可分割取代發布並保留可還原上一版本, 同一工作階段重建索引時維持仍存在的路徑與選取項目
* `新增` 檔案管理器 Explorer Action 協定 v20 支援在可寫壓縮檔的原生頁面中新建空資料夾, 並透過凍結且不含路徑的輸入授權加入檔案與完整目錄樹的明確混合選取; 空目錄會保留, 同名目錄根會安全編號, 來源變更或任何項目失敗都會復原整個壓縮檔取代
* `新增` 檔案管理器 Explorer Action 協定 v21 提供由主機持有的持久來源項目垃圾桶建立批次, 以不透明歷史、逐項結果與跨工作階段盡力還原保留精確邊界; 既有名稱絕不被覆寫, 還原來源項目不會刪除已建立的壓縮檔, 中斷復原證據繼續由主機私有保存
* `新增` 檔案管理器 Explorer Action 協定 v22 允許單一檔案唯讀動作宣告有界關聯檔案後綴; 主機僅凍結精確的 `<所選名稱><後綴>` 候選項並透過唯讀描述元開啟, 不公開上層目錄清單、路徑或任意同層檔案
* `新增` 檔案管理器 Explorer Action 協定 v23 允許單一目錄唯讀溢位選單動作宣告 filesystemPath; 主機僅傳遞所選目錄的規範路徑, 不為其建立主機檔案工作階段, 也不授予任何額外存取權限
* `新增` 外掛中心支援全域設定頁面及使用說明頁面
* `新增` 外掛中繼資料 `instruction` 支援本地化 `@raw/...` 說明資源, 通用外掛資源解析器現可讀取本地或帶套件名稱的 `@string/...` 與 `@raw/...` 引用
* `新增` 外掛中心支援 SDK 外掛自動掛載功能
* `新增` 外掛中心支援多變體優先順序排序功能
* `新增` 外掛中心支援線上安裝外掛/線上更新外掛
* `新增` 外掛中心支援外掛啟動頁跳轉/隱藏外掛
* `新增` 外掛中心支援可用 ABI 交集檢測及提示
* `新增` 外掛中心透過宿主相容性矩陣解析官方 APK Builder Template 搭配版本, 按裝置精確 ABI 選擇資產並回退 universal, 保留現有發行索引/標籤通道, 搭配版本低於已安裝外掛時引導解除安裝並重新安裝
* `新增` APK Builder Runtime Kit 可宣告經明確授權及驗證的宿主修補級閉區間; 實際建置所用宿主不提示直接繼續, 區間內非精確宿主顯示本地化警告, 區間外或格式錯誤的宣告會在範本傳輸前遭阻止
* `新增` 開發者選項新增預設關閉的實驗性遠端 APK 建置 R1 開關; 關閉時阻止 provider 探索與工作階段開啟, 失敗會在不自動本機備援的情況下終止本次建置, 現有產物繼續由原子發佈保護, 程序內診斷僅保留有界的 enum 與數值欄位
* `新增` 外掛中心支援 APK Builder Template 外掛, 可按需安裝及更新打包應用所需範本
* `新增` 主頁外掛標籤頁浮動按鈕支援長按進入外掛中心
* `新增` 設定精靈, 用於依次執行許可權申請精靈及外掛安裝精靈, 首次啟動後主頁以歡迎提示提供入口 (入口: 設定頁面 > 配置 > 設定精靈)
* `新增` 許可權申請精靈, 用於勾選主頁抽屜中的許可權並依次申請, 內建 "標準" / "最小" / "全部" 設定檔並支援自訂設定檔, 已授予的許可權可透過選單隱藏 (入口: 設定精靈 / 主頁抽屜 "許可權" 分組標題按鈕)
* `新增` 外掛安裝精靈, 用於按分類勾選並批次安裝官方外掛, 內建 "標準" / "最小" / "全部" 設定檔並支援自訂設定檔的儲存, 重新命名及刪除, 已安裝的外掛可透過選單隱藏 (入口: 設定精靈 / 主頁外掛標籤頁浮動按鈕選單 / 外掛中心 "+" 選單)
* `新增` 外掛安裝精靈支援邊下載邊安裝, 可使用 Shizuku / Root / Dhizuku 授權時透過 3-Setup Installer 外掛靜默安裝, 使用系統安裝程式時在下載前檢查安裝未知應用程式許可權
* `新增` runtime.loadAar 方法, 用於動態載入 AAR 檔案
* `新增` 顯式 runtime.loadJarWithR8 入口, 支援預設關閉的精確元件選擇, 同簽章身分固定, 有界五產物 R8 傳輸, 隔離快取採用, 以及無 D8/dx 回退的失敗關閉行為
* `新增` ui.useAndroidLayout 支援 Android 原生版面配置元件, include/merge, 指令碼專案 res 資源引用及 shape/selector/layer-list 等 Drawable XML
* `新增` JavaScript (Rhino) 模組支援 power/settings/work_manager 相容介面及 AppInfo 包裝, 並完善 Auto.js Pro 9.3.11 的部分 Base64/Crypto/WebSocket/ByteString/automator/images/timers API
* `新增` 全域 JavaScript (Rhino) ai 的所有本機與線上請求統一透過 AI Provider 外掛目標執行; 開發者選項會開啟可信 3-Stone AI 設定入口, 或提供精確的安裝/啟用/更新指引, 宿主不再儲存 AI 設定檔或憑證, 也不再包含提供者 HTTP 傳輸
* `新增` 全域 JavaScript (Rhino) `ai.catalog` API 公開統一的本機/線上外掛目標目錄; 省略 `plugin` 與 `target` 時選擇官方外掛宣告的預設目標, `target` 精確選擇一個目標, `plugin` 精確選擇第三方元件; 回應包含 reasoning, 完整 usage 與耗時, finish reason, profile 及實際目標 metadata, 穩定錯誤絕不靜默改路由
* `新增` 全域 JavaScript (Rhino) `ai.session` API 可建立持久 AI provider 外掛 Conversation, 為工作階段固定目標/提供者/模型/生成參數, 後續 `ask`/`chat`/`stream` 輪次只傳送新的使用者提示詞; 正常完成後保留上下文, 取消/逾時/協定失敗或顯式關閉時釋放整個工作階段
* `新增` `ai.ask`/`ai.chat`/`ai.stream` 與持久 `ai.session` 的 AI provider 路由支援 `structuredJson` 和 JSON 物件 `responseSchema`; schema 啟用 LiteRT-LM 原生 JSON Schema 約束解碼, 工作階段每輪固定同一 schema, 完成 JSON 文字執行嚴格驗證
* `新增` AI Provider V2 本機目標將明確 `cpu`, `gpu` 與 `npu` backend profile 作為可選目標控制; 可用性按裝置報告, 不可用 profile 失敗且不回退 CPU, 工作階段固定一個 backend, GPU 要求系統 OpenCL 可載入, 官方外掛因未封裝 EAP runtime 而報告 NPU 不可用
* `新增` 全域 JavaScript (Rhino) yolo 模組提供預設關閉的精確元件 YOLO 提供端路由, 支援版本化 Binder/PFD 傳輸, 資訊清單驅動的 NCNN 模型載入, 物件偵測, 明確取消及資源清理, 且不會靜默回退提供端
* `新增` 全域 JavaScript (Rhino) tts 模組提供系統文字轉語音/長文字智慧分段/邏輯佇列/語音及語言探索/離線語音篩選/可觀察任務及音訊事件/靜音插入/採用原子發布的檔案合成, 並具備逾時/背壓/執行階段清理及指定引擎防回退保護
* `新增` device.getMarketName 方法, 用於獲取裝置的 "市場名稱" _[`issue #513`](http://issues.autojs6.com/513)_
* `新增` 定時任務支援按月/按年/倒數任務及重複結束條件 _[`issue #294`](http://issues.autojs6.com/294)_ _[`issue #537`](http://issues.autojs6.com/537)_
* `新增` 檔案管理器支援多選檔案及批次複製/移動/刪除操作
* `新增` 檔案管理器支援安全預覽 Markdown 與 HTML 檔案, 提供 GFM 渲染/多套主題/自訂 CSS 及受控的同目錄資源載入
* `新增` Markdown 與 HTML 預覽支援全螢幕模式及預設關閉的 "以全螢幕模式啟動" 設定
* `新增` 檔案管理器支援透過最左側圖示按鈕預覽 Markdown/HTML 及檢視圖片/播放音訊/影片; 應用程式可註冊為系統媒體檢視器, 音訊支援背景播放及通知列連動控制
* `新增` 檔案管理器識別 `.epub` 電子書為可預覽文件類型, 並透過 Explorer Action 契約交由 Readium EPUB Reader 外掛開啟; 從壓縮檔內直接開啟的電子書另行適用 256 MiB 上限
* `新增` 檔案管理器置頂路徑列, 支援顯示目前路徑及醒目標示目前目錄, 點選上層目錄直接跳轉, 長路徑自動定位至末端及橫向滑動瀏覽, 上層/目前目錄配色與類別列/檔名保持一致, 多語系文字及分隔箭頭依字形墨跡重心光學置中
* `新增` 檔案管理器支援檢視圖片檔案資訊及 EXIF 詳細資訊
* `新增` 圖片編輯及格式轉換由獨立的 Image Tools 外掛提供, 外掛無法使用時僅隱藏對應動作, 不影響圖片檢視/圖片資訊/EXIF 詳細資訊
* `新增` 圖片檢視器支援自動遍歷同目錄圖片, 提供緊跟雙指焦點的同步縮放, 左右滑動及工具列上一張/下一張按鈕切換, 顯示序號/解析度/大小/格式/修改時間/EXIF 的緊湊三行資訊列, 全螢幕模式/以全螢幕模式啟動/帶有歷史記錄的背景色設定, ActionBar/系統列/圖示可隨背景色自適應配色及明暗對比
* `新增` 檔案管理器支援檢視 APK/APKS/XAPK/APKM/APKZ/AAB 檔案的安裝包詳細資訊及資訊清單
* `新增` 安裝器支援 APK 及 APKS/XAPK/APKM/APKZ 容器中的相容分割 APK 組合, 包含裝置分割選擇及簽章憑證一致性驗證
* `新增` 程式碼編輯器支援透過外部外掛載入 Ace 編輯器, 包含簽章/信任/Editor API 合約/宿主版本/二進位 ABI 相容性驗證, 外掛無法使用時自動切回原生編輯器
* `新增` 程式碼編輯器支援 Ace 外部外掛執行階段熱插拔, 外掛安裝或啟用狀態變更時自動切換編輯器引擎
* `新增` Ace 編輯器的內建本機 TypeScript 智慧補全成為正式功能並預設開啟, 支援 AutoJs6 API 補全/簽名說明/懸浮提示/診斷, 可在設定中關閉
* `新增` Ace 編輯器設定支援按需載入 android/libraries/resources/main-app AutoJs6 TypeScript 宣告群組, 自動補齊群組相依性, 所有可選宣告群組預設關閉以降低語言服務啟動耗時及記憶體使用量
* `新增` Ace 編輯器支援快速鍵面板/操作歷史/操作收藏/行動端文字選取/雙指縮放等功能
* `新增` 程式碼編輯器支援以指定編碼重新載入或轉換檔案, 可設定 BOM 並提供編碼風險提示及復原/重做
* `新增` 程式碼編輯器工具列下方資訊列顯示檔名/即時游標位置 (行/列)/檔案編碼, 點選對應項目可檢視檔案詳情/跳轉到行/變更編碼
* `新增` 程式碼編輯器系統狀態列/ActionBar/按鈕圖示及文字/更多及巢狀選單/編輯器內對話框/原生編輯器游標/控點及選取控制項會動態跟隨目前的編輯器主題, 狀態列圖示自動保持明暗對比, 使頂端至程式碼區域的視覺風格一致
* `新增` 程式碼編輯器支援沉浸式全螢幕模式, 設定會持久保存且可從選單或設定頁面切換; 從螢幕邊緣滑動可暫時顯示系統列, 返回鍵會優先退出全螢幕而非離開編輯器
* `新增` 程式碼編輯器支援底部日誌面板顯示 (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `新增` 程式碼編輯器底部日誌面板支援從堆疊幀跳轉至原始碼 (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `新增` 程式碼編輯器底部日誌面板提供 "執行/停止" 按鈕, 可便捷啟停腳本
* `新增` 程式碼編輯器支援專屬設定頁面及編輯器內設定對話框, 提供主題預覽/亮暗模式獨立設定/字型/行號槽/自動換行等顯示選項
* `新增` 設定頁面提供 "執行後自動顯示底部日誌面板", "程式碼高亮閾值" 等程式碼編輯器設定選項
* `新增` 打包應用設定支援顯示或隱藏 "日誌" 及 "設定" 啟動器快捷方式 _[`issue #550`](http://issues.autojs6.com/550)_
* `新增` 打包應用支援使用 APK Builder Template 外掛進行遠端建置
* `新增` 開發者選項支援 GitHub 存取權杖設定, 用於提升更新檢查及外掛索引請求限額
* `新增` 全域 JavaScript (Rhino) R8 整合新增 3 個驗證產物匯出的 `runtime.loadJarWithR8` 多載及通訊協定 1.1 `runtime.retraceR8Stack`; mapping/seeds/usage/retrace metadata 原子發布, retrace 綁定 mapping 溯源, 提供者缺失或無效時禁止 D8/dx 或本機回退
* `新增` 程式碼編輯器設定新增 Ace 語言支援矩陣, 並為 TypeScript/JavaScript, Python, Lua 與 Java 提供逐語言語意開關; Kotlin 以無 Provider 的 P2+ 狀態保持可見, 檔案類型自訂明確作為語意路由前置允許清單而非語言重新分類
* `新增` 外掛更新對話框支援在應用程式內顯示對應外掛的發行歷史
* `新增` `auto.registerEvent(name, listener, options?)` 新增可選的去重與去抖: `{ distinct: 'window' | 'source' | true | (event) => key, within?: 500 }` 在視窗內丟棄鍵相同的重複事件 (如一次視窗切換收到的多個 `WINDOW_STATE_CHANGED`), `{ debounce: ms }` 在一串事件安靜給定時長後只投遞最後一個; 預設關閉
* `新增` 無障礙服務的無人值守啟用方式新增 Shizuku (與 root 及修改安全設定許可權並列, 在兩者均不可用時經 Shizuku 使用者服務寫入系統設定): 設定頁及無障礙服務管理器的設定區新增 "使用 Shizuku 自動啟用無障礙服務" 開關 (預設開啟), 管理器狀態區新增 "Shizuku 許可權" 列
* `新增` `auto.setFlags()` 新增旗標 `appWindowsFallback`: 系統尚未提供活動視窗根節點時 (無障礙服務剛繫結後; Android 8.0 上可能持續到下一次視窗變化), 選擇器與 `auto.root` / `auto.rootInActiveWindow` / `auto.windowRoots` 以視窗清單中的應用視窗 (焦點視窗優先) 的根節點代替, 預設關閉
* `新增` `UiObject` 新增 `contentInvalid()` / `contextClickable()` / `multiLine()` / `dismissable()` 方法 (與 `clickable()` 等布林屬性方法風格一致, 對應既有的 `isContentInvalid` 等 compat 存取器)
* `新增` 控制項集合 (`find()` / `untilFind()` / `children()` / `siblings()` 等回傳的陣列) 新增方法: `at(i)` (負數從末尾計, 越界為 null), `first()` / `last()` (空集合為 null), `nonNull()` (去掉 null 項), `slice(start?, end?)` (索引規則同 `Array.prototype.slice`, 結果仍帶集合方法), 以及逐節點回傳結果的 `performActionEach(action, ...args)` / `clickEach()` / `longClickEach()` / `scrollForwardEach()` / `scrollBackwardEach()` / `setTextEach(text)` (回傳 boolean 陣列, 每個節點都會嘗試; 既有 `performAction` / `click()` 等的布林與語義不變); `filter` / `map` / `forEach` / `some` / `every` / `indexOf` / `for...of` 為陣列自身的操作, 一如既往可用
* `新增` 無障礙座標點擊工具 smartClickBounds / clickBoundsIfExists / clickBoundsAny, Flow 等待及延時 clickBounds 步驟與別名
* `新增` Flow 實例的 clickIfExists / clickAny / findAny 鏈式步驟, whenPresent 可選分支與 repeatUntil 有界非同步迴圈
* `新增` 新增非同步鏈式等待 API: `waitAsync(cond, timeout?, interval?)` (別名 `wait.async` / `auto.wait` / `flow.wait`) 回傳 `Flow` 物件而非阻塞指令碼, 條件可為選擇器 (字串 / 正規表示式 / `UiSelector` / 物件 / 陣列), 函式, `UiObject`, `Flow` 或 `Promise`; 鏈上可接節點動作 (`click` / `longClick` / `setText` / `scrollForward` 等), 全域動作 (`back` / `home` / `swipe` 等), `sleep` / `timeout` / `retry` / `scope` 與 `then` / `catch` (`else`) / `finally` / `map` / `filter` / `peek` / `run` 回呼, 以 `sync()` / `await()` / `toPromise()` 取結果, `cancel()` 取消; 全域起點函式 `waitThenClick` (`clickWait`), `waitForStable`, `waitForStableThenClick` (`clickWhenStable`), `clickWhenStableAfter`, `waitForVisible`, `waitForHidden` (`waitForGone`), 命名空間 `flow` (`$flow`) 另提供 `waitUntil` / `waitWhile` / `waitForActivity` / `waitForPackage` / `waitThenLongClick` / `sleep` (`delay`) / `run` / `of` / `all` / `race` / `any` / `cancelAll` / `Flow` / `pending`; 阻塞步驟在指令碼專屬的工作執行緒集區 (`flow-<id>-<n>`, 每條鏈一條序列 lane, 獨立的鏈並行執行) 執行, 回呼回投建立鏈的指令碼執行緒 (UI 模式亦可用, 但 `sync()` 禁止在 UI 執行緒呼叫), 指令碼結束時擱置的鏈全部取消且執行緒集區停止; 失敗以 `FlowError` (帶 `code` / `step` / `selector` / `elapsed` / `attempts` / `reason` 屬性的 `Error`) 拒絕, 代碼為 `TIMEOUT` / `ACTION_FAILED` / `CANCELLED` / `A11Y_UNAVAILABLE` / `INVALID_TARGET`; `flow.defaults({ timeout, interval, stableFor, strictActions, trace })` 設定每指令碼的預設值 (無參數時讀取), `flow.trace(true)` 在主控台輸出每個步驟的開始 / 結束 / 耗時 / 結果摘要; 便攜步驟 `clickAfter(min, max?)` / `longClickAfter` / `clickWhenStableAfter` 與 `sleep(min, max?)` 支援 `"±delta"` 字串 (同全域 `sleep`); 同步的 `wait()` 保持不變
* `新增` 高階自動化工具, 同時掛載到 `automator` 模組與全域: `smartClick(target, options?)` (目標不可點選時上溯到可點選祖先, 仍不可點選時回退為手勢點選, 可選點選後校驗), `clickIfExists` / `clickAny` / `findAny`, `scrollUntil(target, options?)` (在容器內捲動直到目標出現, 支援無障礙動作與手勢兩種方式, 連續無變化視為到底), `typeInto(target, text, options?)` (選擇器 / 索引 / 目前焦點, 清空或附加, 重讀校驗與送出), `dismissPopups(targets, options?)` (關閉一個 / 全部彈窗, `watch: true` 時為基於視窗變化事件的背景守護控制代碼), `collectList(container, item, options?)` (捲動採集並按內容去重), `launchAndWait` / `backUntil` / `backToApp`, `toggle(target, checked)` (僅在狀態不一致時點選並校驗) 與 `retry(fn, options?)`; Flow 新增對應步驟 `.smartClick()` / `.scrollUntil()` / `.typeInto()` / `.dismissPopups()` / `.collectList()` / `.launchAndWait()` / `.backUntil()` / `.backToApp()` / `.toggle()` 與 `flow.*` 起點; 失敗一律為 `FlowError` (`code` / `step` / `selector` / `reason`); `flow.defaults({ humanize: { offset, delay } })` 為 Flow 動作步驟與各工具設定擬人化的隨機座標偏移與動作前延遲
* `新增` 無障礙診斷: `auto.explain(selector, root?)` 對選擇器逐個過濾器統計命中數 (單獨命中數與疊加前面過濾器後的剩餘數, 使結果清空的過濾器, 通過它之前各步的 "差一點" 節點), 結果物件的 `toString()` 為文字報告; `auto.dump(options?)` 輸出各視窗根 (或 `root`) 的可讀快照 (縮排文字 / JSON / uiautomator 風格 XML, 可限制深度 `maxDepth`, 選擇屬性 `properties`, 僅可見節點 `visibleOnly`, 節點預算 `maxNodes`); `UiObject.toJSON()` (使 `JSON.stringify(node)` 可用) 與 `UiObject.dumpSubtree(options?)`; `auto.stats` 為每指令碼的搜尋 / 輪詢查詢 / 最慢選擇器 / Flow 步驟耗時統計 (`reset()` 歸零, `toString()` 為文字報告); 版面分析器的程式碼產生新增 "程式碼風格" 選項, 可輸出 Flow 風格片段 (如 `waitThenClick(selector).sync()`)
* `新增` 事件驅動等待: `waitForIdle(quietFor? | { quietFor?, timeout?, eventTypes? })` 在視窗狀態 / 內容 / 視窗列表事件停歇 `quietFor` 毫秒後回傳 `{ quietFor, elapsed, events }`, 取代 "sleep 一段時間再找" 的寫法; `waitForEvent(type?, filter?, timeout?)` 等待下一個無障礙事件 (類型為名稱 / 陣列 / `"*"`, 過濾器為函式或 `{ packageName, className, text }` 的字串 / 正則); `waitForToast(text?, timeout?)` 與 `waitForNotification(filter?, timeout?)` 等待其它應用程式的 toast 與通知 (文字包含 / 正則 / 函式 / 物件過濾); 四者均有同名 Flow 起點 (`flow.waitForIdle(...)` 等) 與鏈式步驟 (鏈式 `waitForIdle` 透傳目前值), 逾時拋出 / reject 為 `FlowError` (`code` 為 `TIMEOUT`); `auto.setFlags(["eventAssistedPolling"])` 讓輪詢型的選擇器查詢 (`findOne(timeout)` / `untilFind()` 等) 與 Flow 等待在無障礙事件到達時立即重試, 無事件時選擇器查詢的兜底間隔提高到 200 ms
* `新增` 選擇器與節點能力: 字串選擇器語法 `select("text=登入 clickable")` (以空白分隔的 `key=value` / `key~=regex` 或 `key=/regex/flags` / `key` / `!key`, 含空格的值加引號, 未加引號且含逗號的值在需要時拆成多個參數如 `bounds=0,0,100,200`, 鍵名來自選擇器註冊表; `UiSelector.select(String)` 追加過濾器並回傳自身, 可與 builder 鏈式混用; 語法錯誤拋出帶字元位置, 出錯條件與候選鍵名的例外; 無參 `select()` 仍是節點動作); `UiObject.isStale()` / `waitUntilGone(timeout?, interval?)` / `waitForStable(stableFor?, timeout?, interval?)` 節點級的狀態判斷與等待 (預設逾時 10 s, 間隔 50 ms; `eventAssistedPolling` 旗標下由無障礙事件提前喚醒; UI 執行緒上拒絕); `findIterator()` 惰性查找 (按 `find()` 的順序隨迭代前進搜尋, `for...of` 中 `break` 或 `close()` 之後不再遍歷樹的其餘部分, `collect()` 取剩餘節點, `visited` / `produced` 計數); 視窗過濾器物件 `{ type, packageName, title, displayId, active, focused, id, layer }` 用於 `auto.setWindowFilter(...)` 與新增的 `auto.findWindows(options?)` / `auto.findWindowRoots(options?)` (類型名如 `application` / `inputMethod` / `system` / `accessibilityOverlay`, 套件名與標題接受字串或正則, API 30 之前的視窗視為預設顯示器)
* `新增` 健壯性 (Roadmap P5.5): 無障礙服務在等待期間被解除繫結時, 進行中的 `waitForEvent` / `waitForIdle` / `waitForToast` / `waitForNotification` (同步與 Flow 形式) 以 `A11Y_UNAVAILABLE` 立即結束而非等到逾時; Flow 等待新增 `recoverService` 選項 (亦可經 `flow.defaults({ recoverService: true })` 設為預設), 開啟後探測遇到服務中斷時在剩餘逾時內逐秒安靜地等待服務恢復 (已啟用但未繫結的服務經啟動策略重新啟用, 從不開啟設定頁, 使用者至多被詢問一次), 服務在逾時前回來則繼續等待目標, 否則以帶 `elapsed` / `attempts` 的 `A11Y_UNAVAILABLE` 拒絕; 平台跳過 `onServiceConnected` 時註冊表由首個事件採納實例並記錄 `adoptedByEvent` 旗標 (`auto.state.adoptedByEvent`, 服務管理器診斷的處理程序內執行個體一行附註), 服務資訊偏好 (穩定模式, 手勢觀察) 此時改由首個事件套用
* `新增` 無障礙服務範例 (Roadmap P6.4): 應用內範例程式碼的 "無障礙服務" 目錄新增 6 個指令碼 (流程 (Flow) 非同步等待與鏈式動作, 工具集 表單填寫與彈窗關閉, 工具集 清單捲動與擷取, 字串選擇器與惰性迭代, 選擇器診斷與節點樹匯出, 事件驅動等待), 均以指令碼自身的懸浮視窗為目標, 不依賴其他應用程式
* `新增` 控制台浮動視窗標題列的三個按鈕 (最小化, 空間狀態設定, 關閉) 合併為一個 "更多" 按鈕, 選單提供最小化, 調整大小, 輸入列, 清空, 複製, 設定及關閉; "調整大小" 進入尺寸調整模式 (內容區域變暗並顯示目前尺寸, 拖曳右側或底部邊緣或右下角改變尺寸, 點擊其他區域退出); "設定" 對話框可切換時間前綴, 時間格式 (預設或自訂模式), 日誌著色, 文字大小, 狀態列避讓以及標題列與內容區域透明度
* `新增` 控制台 `setTimeVisible` / `setTimeFormat` / `setColorful` / `setAvoidStatusBar` / `setInputVisible` 方法及對應的 `console.build` 選項 (`timeVisible` / `timeFormat` / `colorful` / `avoidStatusBar` / `inputVisible`), 與浮動視窗設定對話框的選項一一對應; 日誌頁與編輯器日誌面板的更多選單新增 "設定" (時間前綴, 時間格式, 日誌著色, 文字大小) 與 "輸入列" 開關並分別記憶狀態; 範例程式碼新增 "控制台/時間與著色設定" 與 "控制台/設定順序無關"
* `新增` 恢復 `console.rawInput(prompt?, ...args)` 與 `console.input(prompt?, ...args)` (自 6.3.1 廢棄): 阻塞腳本執行緒直到使用者在控制台輸入列提交一行文字, `input` 將文字作為 JavaScript 運算式求值; 輸入列預設隱藏, 僅在腳本等待輸入時自動顯示 (浮動視窗未顯示時自動顯示並取得焦點, 彈出軟鍵盤時視窗上移避讓), 亦可透過 `console.setInputVisible(true)` 或各控制台的更多選單常駐顯示; 提交內容以 `> ` 前綴回顯, 腳本結束時取消等待中的輸入; 範例程式碼新增 "控制台/輸入互動"
* `新增` UI 模式 `<globalconsole>` 控制項顯示全域控制台, `<console>` 改為顯示目前腳本自身的控制台 (與 Auto.js 4 一致), 兩者預設均不顯示時間前綴; `<console global="true">` 與裸屬性寫法 `<console global>` 等價於 `<globalconsole>` (版面配置 XML 支援裸屬性, 視為 `"true"`); 控制項屬性涵蓋 `console.setXxx` 方法 (`title` / `titleTextSize` / `titleTextColor` / `titleBackgroundColor` / `titleBackgroundTint` / `titleBackgroundAlpha` / `titleIconsTint` / `contentTextSize` / `contentTextColor` / `contentBackgroundColor` / `contentBackgroundTint` / `contentBackgroundAlpha` / `textSize` / `textColor` / `backgroundColor` / `backgroundTint` / `backgroundAlpha` / `timeVisible` / `timeFormat` / `colorful` / `inputVisible` 等), 設定 `title` 後顯示帶 "更多" 選單的標題列; 範例程式碼新增 "版面配置/控制台控制項"
* `新增` Node.js 橋訂閱透過既有回呼推送感測器, WebSocket, UI, 懸浮窗與輸入事件, 提供 on/once/off 監聽, 有界事件佇列及 drainEvents 相容
* `新增` Node.js 執行階段支援可選 idleExitMs 閒置退出與後續指令碼重新連線, 提供 idleForMs 診斷, 預設保持常駐
* `新增` process.stdin / readline 接收主控台輸入, autojs6:host 接收執行級 JSON 訊息, v3 postMessage 交易相容舊宿主
* `新增` Node.js 螢幕擷取工作階段接入 Android 授權和既有前景服務, 支援圖片控制代碼, PNG/JPEG/WebP 儲存及停止或指令碼結束時清理
* `新增` Node.js 圖片控制代碼支援裁剪, 縮放, 灰階, 閾值, 找圖及找色, 重用宿主圖像後端, 傳回獨立圖片並在執行結束時清理
* `新增` Node.js image.toBytes 透過檔案描述符將 PNG 或 RGBA 像素傳入原生 Buffer, 支援 JNI 與檔案橋接, 在使用後, 逾時或執行結束時回收附件
* `新增` Node device 介面提供即時建置, 螢幕, 電池和記憶體資訊, 亮度控制及定時保持螢幕開啟; media 支援設定音訊串流音量, 遵循 Android 權限
* `新增` autojs6:events 透過推送回呼觀察 Android 通知, 外部 Toast, 無障礙按鍵及螢幕和電池廣播, 使用明確權限並自動清理訂閱; Node 原生 events 保持相容
* `新增` Node app 支援 Android 服務啟動, 廣播傳送和已安裝應用查詢; dialogs 支援文字輸入, 單選, 多選及隨腳本清理的進度視窗, keys 提供無障礙系統操作
* `新增` Node recorder 支援具備麥克風權限, 前景通知, 時長上限與腳本清理的 AAC 錄音; ui.overlay 支援宣告式屬性更新, 拖曳與事件推送
* `新增` Node 腳本可在兩個獨立執行階段處理程序中並行執行, 共用 FIFO 佇列並按執行工作路由取消和輸入
* `新增` MediaInfo 查詢支援從 0 開始的 streamNumber, countGet 串流計數以及用於單位, 說明和可讀名稱的 infoKind; Rhino 和 Node 保持預設第 1 條串流的 TEXT 查詢, 並協商外掛擴充能力
* `新增` 載入 OpenCV 與 Image Quantization 外掛程式前檢查原生函式庫 ELF 對齊, 頁面大小不相容時提供明確錯誤
* `新增` 外掛程式中心讀取官方索引與已安裝 manifest 的原生頁面對齊資訊, 在 16 KB 裝置安裝或啟用不相容版本前提示
* `新增` MCP 螢幕擷取備援: image.captureScreen(options) 接受無障礙螢幕擷取的裁切, 縮放, 格式和品質參數, 傳回經檔案描述元傳輸的編碼圖片一次性負載; captureScreen() 保留圖片控制代碼傳回方式, MediaProjection 授權缺失或撤銷時傳回權限錯誤.
* `新增` `images.countPointsByColor(image, color, options?)` 透過 OpenCV 遮罩統計與指定顏色相符的像素數量, `images.getMeanColor(image, region?)` 傳回圖片或區域的平均顏色
* `新增` `images.readPixels` 除檔案路徑外也接受 ImageWrapper 參數, 且傳入的圖片保持可用
* `新增` `images.matchTemplate` 與 `images.findPointByImage` 支援 `scales` 選項 (單一比例或比例陣列), 用於比對在其他解析度下擷取的範本; 每個比對項新增 `width`, `height`, `scale`, `center` 與 `rect` 屬性, `MatchingResult` 新增 `size`, `isEmpty()`, `isNotEmpty()` 與 `filter()`
* `新增` 檔案管理器指令碼選單 "懸浮執行" 項, 以半透明懸浮工具列進行指令碼啟停 / 編輯 / 檢視日誌, 跟隨夜間模式與主題色
* `新增` 版面分析: 節點資訊對話方塊中長按屬性可勾選多項, 並合併複製為鏈式選擇器 (id('x').text('y')) 或 pickup() / detect() 可接受的物件選擇器; "儲存版面配置為檔案" 把擷取的節點樹寫入 <工作目錄>/layouts/<時間>.layout.json, 在檔案管理器中點選該檔案即可離線分析
* `新增` 整合 3-Shell Terminal 外掛程式: 側拉選單及專案工具列的終端機入口改為開啟外掛程式終端機, 檔案管理器目錄選單的 "在終端機中開啟" 改由外掛程式 (1.1.0 或更新版本) 提供, 側拉選單開關同步外掛程式工作階段數, 外掛程式未安裝, 已停用或不相容時分別引導至外掛程式中心, 外掛程式設定或升級提示
* `新增` 指令碼全域物件 terminal: 工作階段管理, 可見執行與輸出監聽, 支援等待輸出和結束代碼 (需要 3-Shell Terminal 插件) (參閱 範例程式碼 > 终端)
* `新增` `images.matchFeatures` 的兩個參數均可直接傳入圖片或路徑並即時偵測特徵; 新增 `ransacThreshold`, `minInliers` 與 `maxFeatures` 選項, `ObjectFrame` 新增 `width`, `height`, `angle`, `bounds`, `points`, `matches` 與 `inliers` 屬性, `ImageFeatures` 新增 `count` 與 `method` 屬性
* `新增` 官方外掛無障礙伴生契約: 發布 `follow-host` 服務策略的伴生外掛隨 AutoJs6 無障礙服務一同啟用與停用, 使用已設定的 root, 安全設定或 Shizuku 策略, 並接收宿主服務狀態廣播
* `新增` Bun Runtime 外掛專案執行: 位於含 `project.json` 或 `package.json` 目錄下的指令碼會打成有界工作區封存 (最多 16384 項目與 64 MiB, 只收一般檔案與目錄) 交給宣告了 `SUPPORTS_WORKSPACE_ARCHIVE` 的外掛, 專案內的相對匯入可以解析; 進入點檔案使用編輯器目前原始碼, 符號連結與特殊檔案直接中止執行, 裸指令碼或舊版外掛保持單一原始碼路徑
* `新增` Bun Runtime 插件宿主資訊快照: 插件宣告 `SUPPORTS_HOST_INFO` 且 `host_info` 授權開啟 (預設) 時, `runScript` 請求攜帶 `hostInfoVersion = 1` 與 `hostInfo` Bundle (宿主套件名稱, `BuildConfig.VERSION_DATE` 與偏好語言標籤), Bun 腳本即可透過環境變數 `AUTOJS6_HOST_INFO_FILE` 讀取由插件核實並寫出的宿主/插件/執行快照; 宿主不發送自身版本 (插件經 PackageManager 解析), 授權可逐次撤銷 (`BunHostCapabilityGrants`), 未宣告該能力的插件收到的請求與之前完全相同
* `新增` Bun Runtime 插件動態呼叫能力橋與首批兩項能力 `ui.toast` / `device.info`: 插件宣告 `SUPPORTS_HOST_CAPABILITY_BRIDGE` 且 `ui_toast` / `device_info` 至少一項授權開啟 (預設) 時, `runScript` 請求攜帶 `hostCapabilityBridgeVersion = 1`, 綁定插件 UID 與本次執行的 `IBunHostCapabilityBroker` binder 以及已授權的能力 ID; Bun 指令碼經插件的 unix socket 呼叫 `GET /v1/info` 與 `POST /v1/<能力>`, 宿主原樣轉發 JSON (請求 64 KiB, 結果 256 KiB, 每次執行 1024 次, 4 路並行, 單次 10 s) 並經 `IBunHostCapabilityCallback` 回應. `ui.toast` 每次執行最多在主執行緒顯示四則 toast, `device.info` 只回傳 `android.os.Build` 欄位; 兩個開關位於插件中心的插件設定頁, 未宣告該能力的插件收到的請求與之前完全相同
* `新增` 設定頁 "回收站與版本歷史" 分類: 保留策略子頁可編輯版本歷史 (保留天數, 每檔案版本數, 每檔案容量, 總容量, 納管檔案大小上限), 回收站 (保留天數, 容量上限) 與緊急草稿 (保留天數, 容量上限), 0 表示不限制, 修改後立即執行一次清理; 另含 "徹底刪除時同時清除版本歷史" 開關
* `新增` 回收站與版本歷史頁面選單增加 "立即清理" 與 "設定", 側欄中長按這兩個條目直接開啟保留策略設定
* `新增` 用戶端模式位址輸入框支援掃描 VSCode 擴充功能 (1.1.0+) 顯示的二維碼, 免輸入位址直接連線
* `新增` VSCode 擴充功能通訊協定 v2: 增加請求/回應訊息與遠端檔案管理方法 (瀏覽, 讀取, 寫入, 重新命名, 刪除裝置檔案及執行裝置指令碼), 供 VSCode 擴充功能 1.1.0+ 使用
* `新增` 版本歷史列表頁: 編輯器與檔案管理器的版本歷史入口改為獨立列表頁, 逐條顯示時間 / 大小 / 操作 / 編碼, 並標記與目前檔案內容一致的版本; 點擊可預覽 / 還原 / 刪除, 長按多選批次刪除, 支援按時間或大小排序
* `新增` 版本歷史差異檢視: 在版本列表中可將某個版本與目前檔案比較, 或多選兩個版本互相比較; 逐行顯示新增 / 刪除並著色, 未變更段落摺疊且可展開, 支援按文字搜尋; 內容過大時僅顯示行數與大小統計
* `新增` 版本歷史手動快照與固定: 編輯器選單新增 "建立快照" (可選標籤), 快照預設固定; 版本列表可對任意版本固定 / 取消固定與編輯標籤, 固定的版本不參與按天數 / 版本數 / 單檔案容量的清理, 全域容量清理也優先刪除未固定的版本
* `新增` 版本歷史匯出: 版本列表的詳情對話框新增 "匯出", 選擇目錄並確認檔案名稱 (預設在原檔案名稱後附加版本時間戳) 後把該版本按原編碼與 BOM 原樣寫出, 覆蓋已納管檔案前會先保留其目前內容
* `新增` 編輯器中開啟版本歷史時, 版本詳情對話框新增 "還原並儲存" 選項: 確認覆蓋後把該版本載入編輯器並立即儲存到磁碟 (預設的 "還原" 仍只改動編輯器內容)
* `新增` 回收站多選: 長按項目進入多選, 支援全選 / 取消全選, 批次還原 (還原到各自原路徑或統一指定目錄; 存在同名衝突時整批選擇略過 / 覆蓋 / 自動改名, 批次內同名項目一律自動改名; 進度對話框可中止, 結束後回報還原 / 略過 / 失敗數量) 與批次從回收站移除
* `新增` 回收站內容預覽: 項目對話框新增 "預覽", 文字檔案以唯讀編輯器開啟, 目錄顯示歸檔內的檔案列表 (相對路徑 / 大小 / 檔案與資料夾數量), 二進位內容給出提示
* `新增` 回收站項目詳情: 項目對話框新增 "詳情", 顯示名稱 / 類型 / 原路徑 / 移入時間 / 大小 / SHA-256 / 預計到期時間 (含剩餘天數與小時), 文字可選取, 並可一鍵複製到剪貼簿
* `新增` 檔案管理器把檔案或目錄移入回收站後, 提示列提供 "復原": 僅當原路徑仍空閒時把項目還原回原處 (絕不覆蓋), 已不在回收站或原路徑已被佔用的項目會在彙總中標為略過
* `新增` 回收站識別資料檔案缺失的失效項目: 列表以醒目顏色標記 "資料缺失", 點擊僅提供詳情與移除, 選單新增 "移除失效項目" 一鍵清理; 週期清理任務只記錄失效項目數量而不自動刪除
* `新增` VSCode 擴充功能連線: 客戶端模式與伺服端模式均新增 "管理器" 對話框 (抽屜條目的 "管理器" 按鈕或長按條目開啟), 實時顯示連線狀態, 伺服端或監聽位址, 對端主機, 擴充功能與協定版本, 連線時長, 最近活動, 重連工作階段與最近錯誤等 (逐行可複製), 提供連線, 斷開, 立即重連, 停止重連, 啟動, 停止, 重啟, 斷開所有客戶端, 複製位址等操作, 以及自動重連, 網路變化時重試, 啟動時恢復連線 (或開啟伺服端), 客戶端連線變化提示等開關; 伺服端管理器新增 "客戶端" 區域, 逐個列出已連線的 VSCode, 點擊可查看基礎資訊, 斷開或要求其重新連線
* `新增` VSCode 擴充功能連線: 客戶端模式支援意外斷開 (擴充功能崩潰, VSCode 重啟, 網路抖動) 後自動重連 (指數退避, 網路恢復時立即重試, 應用程式啟動時亦可恢復上次連線); 雙方以 bye 訊息宣告主動關閉, 任一方顯式斷開連線後均不再自動重連
* `新增` VSCode 插件連線支援配對確認: 未知裝置以客戶端模式首次連線時需在 VSCode 端允許 (可選擇記住), 掃描 QR Code 連線的裝置自動信任; 需 VSCode 插件 1.1.0+
* `新增` 伺服端模式支援區域網路探索 (mDNS): 開啟後 VSCode 插件 (1.1.0+) 建立區域網路連線時可直接列出本裝置, 無需手動輸入位址
* `新增` VSCode 遠端檔案支援分塊傳輸: 大檔案讀寫按 1 MiB 分塊並在 VSCode 端顯示進度; 需 VSCode 插件 1.1.0+; fs.list 可按需回傳檔案 md5, 供 VSCode 插件 "從裝置拉取專案變更" 時比較檔案
* `新增` 客戶端模式管理器控制區提供 "歷史記錄" 入口: 查看曾連線過的伺服端位址, 點擊即可連線, 長按刪除, 亦可清空
* `新增` 郵件外掛契約模組 (plugin-api/mail-api), 含凍結的 AIDL 介面, 以專用綁定租約開啟帳戶工作階段, 在外掛程序死亡後自動重開, XOAUTH2 權杖重新整理後重試一次並經管道串流落盤附件的宿主客戶端, 以及預設啟用的外掛中心註冊
* `新增` 郵件外掛的指令碼 API `mail` (郵件路線圖 P3.1 與 P3.2): `mail.connect(選項或別名)` 支援服務商預設與 XOAUTH2 的 `tokenProvider`, `MailClient` 提供 `test`, `send` (指令碼路徑的附件經唯讀描述符傳輸), `folders` / `folder(path)` / `createFolder` / `deleteFolder` / `renameFolder`, `fetch`, 帶用戶端回退標記的 `search`, `get` / `fetchBody`, 串流落盤到工作目錄並支援 `onProgress` 的 `download` 與 `raw`, `setFlags` 及 `markRead` / `markUnread` / `flag` / `unflag`, `move`, `copy`, `delete`, `expunge`, `append` 與 `close`, 每個方法都有在指令碼執行緒落定的 `Async` Promise 形態; 郵件結果攜帶 `Date` 欄位, 可列印的地址與繫結的 `load` / `download` 方法, 失敗為帶 `code` 與 `retryable` 的 `MailError` 物件, 全域物件上另有 `mail.providers`, `mail.accounts`, `mail.setDefault` 與全部用戶端方法的轉發
* `新增` 郵件外掛的指令碼 API `mail.watch` (郵件路線圖 P5): `client.watch(folder?, {mode?, pollIntervalMs?, fetchBody?, reconnect?})` 與 `mail.watch` 回傳 `MailWatch` 發射器, 其 `message(message, watch)`, `mode`, `resync`, `error` 與唯一的 `close(reason)` 事件在指令碼執行緒到達並讓指令碼活到 `stop()`; 外掛以 IMAP IDLE 監聽 (可回退輪詢, POP3 按 UIDL), 宿主丟棄過期 generation 與亂序投遞, 外掛死亡回報為 `PLUGIN_UNAVAILABLE` 且預設在重開的工作階段上重新監聽並發出 `resync`, 關閉用戶端或指令碼退出會停止其監聽
* `新增` 郵件外掛契約版本 2 與背景守望 (郵件路線圖 P8): 追加的 `IMailPlugin.openTrigger` / `listTriggers` 與 `IMailTrigger` / `IMailTriggerCallback` 介面訂閱外掛在自身前景服務中持續執行的守望; 新的 "郵件到達時" 意圖任務 (`org.autojs.autojs6.action.MAIL_TRIGGER`, 由宿主簽章權限保護的接收器接收) 在所選守望的每封新郵件到達時啟動指令碼, 可依寄件者與主旨過濾, 事件文件位於 `engines.myEngine().execArgv.mail`; 重複投遞會被捨棄, 連續到達每任務每 3 秒最多啟動一次; 版本 1 的外掛不會收到新呼叫
* `新增` EPUB 外掛程式契約模組 (plugin-api/epub-api), 含凍結的 AIDL 介面 (書籍擷取: 元資料, 目錄, 閱讀順序, 章節文字, 資源與搜尋; 以及基於權杖的閱讀器工作階段), `epub` 引擎的宿主探索封裝, 以及 Readium EPUB Reader 套件的外掛程式中心註冊 (其 Explorer Action 服務與 EPUB 服務共用一個條目)
* `新增` EPUB 外掛程式宿主用戶端 (core/plugin/epub, 外掛程式路線圖 P5.4): `EpubPluginHost.openBook` 校驗路徑, 以唯讀方式開啟並交出書籍用戶端 (元資料, 目錄, 閱讀順序, 純文字或 Markdown 的分頁章節文字, 經暫存管道匯出的資源, 搜尋, 位置數), 執行在帶死亡回呼的專用繫結租約上; `openReader` 開啟閱讀器工作階段, 讀取其一次性權杖並由宿主自己啟動外掛程式的閱讀器 Activity (兩步啟動), 以 generation 與序號校驗橋接工作階段事件 (open, progress, bookmark, error, close), 並轉發 goTo, navigate, setPreferences, getBookmarks 與 getState; 所有失敗都對應為契約錯誤碼
* `新增` Readium EPUB Reader 外掛程式的指令碼 API `epub` (外掛程式路線圖 P6.1): `epub.open(路徑或選項)` 回傳 `EpubBook`, 提供 `metadata`, `toc` (`{ title, href, children }` 樹), `readingOrder`, `positions`, `text(target?, options?)` (href, 閱讀順序索引, 目錄項或全書, 以 `offset` / `maxChars` 分頁, `format` 為 text 或 markdown), `textAll`, `cover` (`Image` 或檔案), `resource`, `search` 與 `close`; 便捷層 `epub.metadata` / `toc` / `readingOrder` / `text` / `cover` / `search` 每次呼叫自行開啟並關閉書籍, `epub.isAvailable()` 探測外掛程式, 每個方法都有在指令碼執行緒落定的 `Async` Promise 形態, 路徑遵循指令碼工作目錄並拒絕 URI, 失敗為帶 `code` 的 `EpubError` 物件, 外掛程式不可用提示已本地化, 指令碼結束時關閉未關閉的書籍
* `新增` `epub` 指令碼 API 的閱讀器控制 (外掛程式路線圖 P6.2): `epub.read(path, options?)` 開啟 Readium EPUB Reader 外掛程式的閱讀器並回傳 `EpubReaderSession` 事件發射器, 其 `open`, `progress`, `bookmark`, `error` 與 `close` 事件在指令碼執行緒到達, 提供 `goTo(href | progression | locator)`, `next` / `prev` / `nextChapter` / `prevChapter`, `setPreferences(partial)`, `bookmarks()`, `close({ finish? })` 以及屬性 `locator`, `progress`, `title`, `chapterTitle`, `href`, `positions`, `state`, `reason`, `isOpen` / `isClosed`; 起始位置來自 `href`, `progression` 或 `locator` 並可附帶 `preferences`, 指令碼不再消費事件時得到 `error(LIMIT_EXCEEDED)` 與 `close(overflow)`, 指令碼結束時結束會話而閱讀器留給使用者繼續閱讀
* `新增` 電子書範例 (外掛程式路線圖 P6.3): 應用程式內範例程式碼新增 "电子书" 目錄及 3 個指令碼 (EPUB 元数据与目录: 讀取 Download 或 Books 目錄下書籍的中繼資料, 目錄, 閱讀順序與位置數; 导出章节文本: 按章節或整本匯出純文字或 Markdown, 匯出封面並全文搜尋; 打开阅读器并监听进度: 以 `open` / `progress` / `bookmark` / `error` / `close` 監聽器驅動閱讀器工作階段, 設定起始偏好, 翻章並以 `close()` 把閱讀器留給使用者); 每個指令碼先檢查 `epub.isAvailable()` 並說明 Readium EPUB Reader 外掛程式的安裝方式
* `新增` EPUB 契約版本 2 (螢光標示與筆記, 外掛路線圖 P9.4): 附加的 `IEpubBook.getAnnotations` (分頁, 按閱讀順序) 與閱讀器會話的 `highlight` 事件以 `annotations` 文件 (id, style, color, note, quote, 章節標題, locator, createdAt, updatedAt) 傳遞閱讀器中的螢光標示與筆記; 宿主依據外掛的 `epubMaxContractVersion` 協商版本並寫入每個請求, 版本 1 的外掛不會收到新呼叫; 指令碼 API `epub` 新增 `book.annotations()` 與 `epub.annotations(path)` (含 `Async` 形態), `EpubReaderSession` 發出 `highlight({ action, id, style, color, note, quote, title, locator, createdAt, updatedAt })` 事件, `action` 為 added, updated 或 removed
* `新增` Three Stove Agent 外掛契約與宿主代理, 支援受限裝置能力, 模型呼叫配額及連線失效後的資源回收; 模型代理支援原生工具呼叫及結果續輪, 每輪檢查模型配額
* `新增` Agent 指令碼登記與受限執行, 支援 project.json / @agent 標頭註解, 參數驗證, 逾時停止及結構化結果回傳
* `新增` Three Stove Agent 外掛中心管理與安裝, 啟用, 授權及相容性引導; 安裝精靈工具分類提供選用入口, 發行 APK 加入官方索引後可供安裝
* `新增` Three Stove Agent 外掛可設定附加指令碼目錄, 宿主校驗允許範圍後儲存並在重新連線時恢復, 無效目錄不會取代已接受的設定
* `新增` installer 模組, 用於安裝, 更新與解除安裝應用程式, 支援 Shizuku / Root 靜默操作, 並依外掛能力啟用 Dhizuku 授權, notification 通知安裝及 setDefault 的 mode: 'persistent' 選項; Dhizuku 受裝置/工作資料擁有者政策限制 (需要 3-Setup Installer 外掛) (參閱 專案文件 > [安裝器](https://docs.autojs6.com/#/installer))
* `新增` 佈局分析的層次與範圍視窗頂部提供搜尋列, 按關鍵字或正規表示式比對控制項的 text / desc / id / className: 層次視窗只保留相符節點及其祖先並標示命中內容, 範圍視窗只繪製相符節點的邊框, 可在相符項之間前後跳轉, 搜尋列可收起為可拖曳的浮標, 在兩個視窗之間切換時保留查詢
* `新增` 佈局分析搜尋列點按相符計數可開啟結果清單對話框 (顯示節點摘要, 路徑, 深度與邊界, 點按跳轉選取, 長按檢視控制項資訊, 零相符時說明原因), 並提供查詢記錄 (按 Enter 或跳轉時記錄, 最多 20 筆, 輸入框為空時下拉顯示, 長按刪除, 可清除, 跨工作階段保留)
* `新增` 佈局分析搜尋列新增篩選設定檔: 按布林屬性三態 (clickable / scrollable / enabled 等 14 項), 有 id / text / desc, 類別名稱預設 (按鈕 / 文字 / 輸入框 / 圖片 / 核取方塊與開關 / 清單與捲動 / 容器), depth / childCount / indexInParent 區間, 最小寬高, 僅限畫面內, 套件名稱與動作篩選控制項; 內建不可修改的預設設定檔, 修改即時生效並可儲存為使用者設定檔 (支援另存為 / 重新命名 / 刪除), 已選設定檔跨工作階段記憶
* `新增` 佈局範圍分析視窗支援框選區域: 在篩選設定檔中選擇 "框選區域" 後拖出矩形, 只保留完全位於該區域內的控制項, 切換到層次視窗同樣生效
* `新增` 佈局分析搜尋列新增欄位模式: 點擊模式開關 (Aa / k=v) 切換, 以指令碼選擇器字串語法輸入多個條件 (如 text=確定 clickable, id~=btn_.*, !enabled, minDepth=3); 輸入框取得焦點時顯示語法速查, 語法錯誤時在提示列中顯示出錯位置, 條件及候選名稱; 查詢模式跨工作階段記憶
* `新增` 佈局分析支援 "複製為選擇器": 在搜尋結果對話框點擊 "複製為選擇器" 或長按計數位, 把關鍵字 (按目前符合項命中的 text / desc / id / className 屬性輸出 Contains / Match 呼叫), 欄位條件, 篩選設定檔與框選區域繪製為鏈式指令碼選擇器並複製到剪貼簿; 無法表達的條件以註解形式附在末尾
* `新增` 選擇器新增 minDepth / maxDepth / minIndexInParent / maxIndexInParent 方法 (與 minChildCount / maxChildCount 一致, 含邊界), 並支援在選擇器字串語法中使用 (如 minDepth=3)
* `新增` 佈局分析搜尋列新增選擇器模式: 模式開關循環至 "$()" 後, 像指令碼一樣輸入鏈式選擇器運算式 (如 text("確定").clickable().depth(3), textMatch(/確定|取消/i), 前綴 selector() / $() 可省略), 字串 / 數字 / 布林 / 正則字面量按指令碼語義落到選擇器註冊表; 輸入到一半時顯示 "運算式未結束" 而非報錯, 語法錯誤顯示位置, 呼叫與候選方法名稱, "複製為選擇器" 輸出正規化後的運算式
* `新增` 佈局分析 "產生程式碼" 支援單個/集合範圍, 等待找到/限時等待/立即判斷, 動作接收者與設定文字參數, 經典/Flow 風格, 備選方案與定位理由, 並可在佈局中預覽符合結果
* `新增` 程式碼生成模型管理頁支援本機匯入, 按需下載, 多版本管理與比較, 以及官方模型更新 (入口: 設定頁面 > 無障礙服務 > 程式碼生成模型)
* `新增` 主題色支援自建顏色庫, 可新建或克隆顏色庫, 從調色盤, 其他顏色庫或歷史記錄新增顏色, 並可重新命名, 修改顏色值, 拖曳排序及批次刪除; 顏色庫本身也可拖曳排序與批次刪除
* `新增` 智慧顏色庫, 每組按狀態源的目前狀態取色, 狀態源可選夜間模式, 時段, 日出日落 (按時區推斷位置或手動輸入座標), 星期或工作日與週末, 季節, 電量區間, 充電, 省電模式, 無障礙服務, 指令碼執行與指令碼狀態 (由 colors.theme.state 切換); 可在狀態變化, 應用程式啟動, 指令碼啟動, 時間間隔 (首次切換可選現在, 下一個整分或下一個整點, 最短 5 秒) 或每日定時時依順序或隨機切換顏色組; 日期覆蓋層在指定日期區間或節日 (含春節, 中秋等農曆節日, 可提前或延後若干天) 顯示指定的組; 主題色頁面開啟期間暫停切換
* `新增` 智慧顏色庫預設 "材料設計日夜配對" (19 個 Material 色系, 日夜色階按與視窗背景的對比度挑選), "背景色" 與 "跟隨桌布" (取桌布的主色, 次色與第三色, 需要 Android 8.1 以上版本, 更換桌布後自動重新整理), 可克隆預設, 新建智慧顏色庫或從種子色產生 (種子色可取目前主題色, 調色盤或桌布顏色, 按和諧色或色階產生各組, 夜間模式下自動配出夜間顏色), 並在編輯器中調整狀態源, 換組時機, 切換方式及各組顏色, 組編輯器可由第一個狀態自動填入其餘狀態 (可復原)
* `新增` 顏色庫匯入與匯出 (AutoJs6 JSON 格式): 可在長按選單中匯出單個顏色庫, 或透過 "匯出顏色庫..." 一次匯出多個 (預設庫以快照匯出); 匯入時預覽檔案中的顏色庫及顏色數或組數, 總是新建顏色庫, 重名時自動加序號, 無法匯入的檔案會說明原因
* `新增` 顏色詳情中的 "配色工具": 互補, 類比, 三角, 分裂互補與四方和諧色, 由淺到深的色階, 與日夜視窗背景的對比度 (未達 3:1 時提供最近的達標顏色), 以及紅色盲, 綠色盲與藍色盲模擬; 其中任一顏色皆可套用為主題色, 新增至顏色庫或複製
* `新增` 主題色 "衍生配色" 設定 (預設關閉, 與先前版本完全一致): 自動時由主色推導用於懸浮按鈕的強調色與用於狀態列的深色, 外掛程式同樣取得三種顏色; 智慧顏色庫各組的每個狀態可單獨指定強調色與深色, 在兩種設定下皆優先
* `新增` 顏色收藏: 在顏色詳情中勾選 "收藏" / "置頂", 或長按顏色庫中的顏色及配色工具中的色塊; 虛擬庫 "收藏" 可按手動 (拖曳排序) / 收藏時間 / 色相 / 亮度排序, 置頂項目一律在前, 可從調色盤收藏, 並可從收藏建立智慧顏色庫 (每色一組); 收藏也可作為 "從種子色產生" 的種子
* `新增` 虛擬庫 "常用與推薦": 依序為置頂收藏, 其餘收藏, 以及按使用次數與近因 (半衰期 14 天) 評分的歷史顏色, 相似顏色合併, 至多 30 項, 無資料時隱藏; 項目按原本的選擇方式套用, 並可建立智慧顏色庫; 顏色歷史記錄使用次數, 歷史清單可 "合併相似顏色"
* `新增` `colors.distance(colorA, colorB, algorithm?)` 依各顏色偵測演算法的閾值尺度傳回顏色距離, `colors.invert(color)` 反轉 RGB 並保留 alpha, `colors.blend(colorA, colorB, ratio?)` 依權重混合兩個顏色, `colors.contrast(foreground, background)` 計算 WCAG 對比度; 以上四個方法同樣可用於 Color 實例
* `新增` colors 模組的感知色彩函式: toOklab / oklab 與 toOklch / oklch, ensureContrast (WCAG 對比度, 預設 4.5), harmony, tones, derive (主色, 強調色與深色), nightVariant, simulate (紅色盲, 綠色盲, 藍色盲) 及 extract (影像主要顏色, 支援 count, region 與 step 選項); colors.distance, colors.isSimilar 與 images.detectColor 支援 oklab 演算法 (參閱 範例程式碼 > 图像与颜色)
* `新增` colors.theme 由指令碼控制主題色: get, set (可指定 accent 與 dark), reset, libraries, activate 與 deactivate (智慧顏色庫), next (立即換組), state (切換 "指令碼狀態" 狀態源) 及 current; 500 ms 內的指令碼修改合併為一次, 歷史記錄標註 "指令碼: 名稱", 主題色頁面提供 "復原指令碼修改" 以還原先前的選擇 (含啟用中的智慧顏色庫) (參閱 範例程式碼 > 图像与颜色)
* `新增` Ace 編輯器快捷面板 TAB 鍵長按用於喚出智慧提示, 依游標上下文顯示程式碼補全, 參數提示或符號文件, 參數提示已顯示時切換重載方法; 硬體鍵盤 Ctrl+Space / Ctrl+Shift+Space / Ctrl+Q 分別對應智慧提示 / 參數提示 / 符號文件
* `新增` 程式碼編輯器設定增加 "智慧提示" 分組 (僅 Ace 引擎): 自動補全觸發方式 (輸入識別字時 / 僅輸入 "." 後 / 關閉), 補全最少觸發字元數, 補全觸發延遲, 參數提示觸發方式 (游標進入呼叫時 / 僅輸入 "(" 或 "," 時 / 關閉), 方向鍵切換重載; 修改後即時生效並可記錄到動作收藏 (需要 Ace 編輯器插件 1.26.0)
* `修復` 啟用 APK Inspector 後, 檔案管理器的安裝套件資訊入口被隱藏且無法從更多選單存取的問題
* `修復` Three Stove Agent 呼叫線上模型失敗時遺失網路, 身分驗證及回應錯誤類別的問題 (需要 Provider 外掛支援)
* `修復` assistant 合併工具選項時, 空 tools 物件導致預設關閉的工具組被啟用的問題
* `修復` 無障礙精簡節點樹縮短資源 ID, 導致依顯示 ID 查詢時無法比對控制項的問題
* `修復` Android 7/8.0 裝置上部分外掛檔案快照, Node.js 套件安裝及 TypeScript 專案檔案操作的相容性問題
* `修復` Shizuku 權限請求監聽器在銷毀後未正確取消註冊的問題
* `修復` Python 長時間執行工作通知缺少部分語言譯文及 DEX 編譯器設定缺少英文文案的問題
* `修復` 官方外掛在宿主啟動期間讀取外觀設定時, 因應用程式 Context 尚未就緒導致偏好設定初始化崩潰的問題
* `修復` Three Stove Agent 在螢幕關閉或鎖定時繼續觀察和操作螢幕而未傳回 SCREEN_LOCKED 的問題
* `修復` 外掛能力代理在要求 toast 日誌時將文字內容寫入宿主日誌的問題
* `修復` Agent 關閉操作確認視窗後誤報節點參照失效及顯示過時應用程式名稱的問題
* `修復` 深層介面節點無法操作, 以及邊界或操作能力不同的巢狀容器被誤判為同一目標的問題
* `修復` 外掛中心啟用 YOLO 外掛時探測交握要求外掛已處於啟用狀態, 導致開關無法啟用的問題; 探測現僅要求授權與精確元件交握, 執行時入口仍要求已儲存的啟用記錄
* `修復` 透過 adb 安裝 MCP Server 且沒有儲存啟用記錄時, 外掛程式中心顯示已啟用而執行時視為未啟用的問題; 兩者統一為預設關閉並保留使用者明確儲存的開關狀態
* `修復` 無障礙服務管理員中未綁定, 無等簡短狀態值下方存在多餘空行的問題
* `修復` Node.js 受控 Fetch 內嵌回應超過 Binder 交易大小限制時靜默遺失, 導致指令碼等待橋接逾時的問題
* `修復` Node.js WebSocket 提供者銷毀時過早取消連線, 導致對端未收到正常關閉訊框的問題
* `修復` 缺少 APK 封裝資訊的可執行 Node.js 專案 (如 host-events) 未被檔案清單和專案工具列識別的問題
* `修復` Node.js 專案開始執行及執行結束日誌顯示無路徑的 main.js, 未保留 main.cjs 等實際進入點檔案完整路徑與副檔名的問題
* `修復` 螢幕取色說明對話方塊的設定按鈕開啟外掛主頁面, MCP Server 停止時不再顯示抽屜副標題
* `修復` Node.js 工作區傳輸與模組載入洩漏檔案描述元, 導致執行多檔案專案後, 後續指令碼和應用程式重新啟動變慢
* `修復` Node.js 專案回傳不再重複替換未修改檔案, 避免大量相依檔案導致指令碼結束緩慢和應用程式回應遲鈍
* `修復` Flow 掛號範例在醫師列解析失敗時傳回 false 而非 null, 避免布林轉換例外
* `修復` 主頁正確回傳外掛授權結果, 授權成功後可繼續完成本次外掛啟用
* `修復` HTTP 請求的 isInsecure / insecure 選項可能使後續請求繼續略過憑證和主機名稱驗證, 現僅對目前請求生效
* `修復` 宿主與打包應用發行版的 HTTP 請求可能被網路安全設定意外攔截, 明文策略現與偵錯版保持一致, 並保留 GitHub 資源網域的 HTTPS 限制
* `修復` 背景 APK 安裝被系統限制啟動確認頁時無法繼續的問題; 安裝狀態依工作階段儲存, 支援通知及返回應用程式後繼續確認, 通知遭拒或程序重建後仍可恢復待確認事項
* `修復` 小工具批次更新時, 多位數的小工具 ID 導致指令碼路徑與點擊對應被誤刪的問題
* `修復` 宿主 bridge 的內建範例二進位讀取在 Android 7.0 / 7.1 上使用既有 Okio base64 編碼器, 避免呼叫僅 API 26 起可用的 Java 方法, 支援 MCP 範例資源
* `修復` MCP 檔案操作與 Shell 在 Android 24 / 25 上避免呼叫新版 Java 檔案系統和行程方法; 遞迴刪除只移除符號連結而不刪除其目標
* `修復` CI 編輯器外掛檢查固定要求舊協定版本 1, 導致支援新版相容協定的宿主無法通過檢查的問題
* `修復` 在同一個打包應用中依序使用 Paddle OCR 和 Rapid OCR 時可能崩潰的問題
* `修復` MediaInfo 與圖片檔案路徑支援絕對路徑, 父目錄和合法檔名; 錄音輸出同步交由更新後的宿主依 Android 檔案權限處理
* `修復` 帶 Unicode BOM 的腳本由檔案管理器直接執行時未按對應的 UTF-8, UTF-16 或 UTF-32 編碼讀取, 導致其可在編輯器正常執行卻於檔案管理器回報 `無效字元` 的問題
* `修復` Scrapers 工具遷移後 Android Studio 下載資訊不再自動寫入 README 通用資料的問題
* `修復` 使實驗性遠端單檔案建置編號與舊建置器保持一致，並新增失敗關閉的工作區空間預檢：交叉核對輸入解壓縮大小、使用建置期驗證的範本展開上限，並保留 256 MiB 空間
* `修復` Ace WebView 在主機端主題與首份文件生效前可能短暫顯示純白背景, 尤其會在深色編輯器主題下產生明顯閃爍; 首次畫面現在由主題已套用, 文件已提交與穩定繪製訊號共同放行, 不依賴固定延遲
* `修復` UI 指令碼中的 WebView 檔案選擇器可能無法開啟, 因版面配置根節點使用的 context 遺失了 Activity 關聯
* `修復` UI 腳本在進入分割畫面或調整視窗大小時可能因 Activity 重建而意外結束
* `修復` 原生與 Ace 編輯器在 Activity 重建後可能遺失游標位置和選取範圍, 沒有檔案路徑的大型文字內容也可能遺失未儲存的修改
* `修復` 橫向顯示並開啟軟體鍵盤時編輯器正文區域可能被擠至零高度, 日誌頁輸入欄可能被軟體鍵盤遮擋
* `修復` 首頁搜尋內容和搜尋框展開狀態可能在分割畫面觸發 Activity 重建後遺失
* `修復` Ace 編輯器首次開啟 Lua 檔案時可能因同步偵測 LuaLS 原生執行階段阻塞首次畫面並回退至舊引擎的問題
* `修復` 預覽字型時, AutoJs6 主機端與 Ace 外掛並行存取共用字型儲存可能觸發 JVM 重疊檔案鎖例外並導致應用程式崩潰的問題
* `修復` 修正 APK Builder 產生 TypeScript/TSX 啟動器時遺失 Rhino 執行模式指令（包括 `ui`）的問題，編譯後的 UI 專案現在會以正確的 Activity 模式啟動
* `修復` 內嵌 Node.js APK 封裝要求現在會在暫存或開啟遠端工作階段前失敗關閉，並提示改用外部 Runtime 外掛；執行模式解析不再記錄原始碼 token 或例外詳細資料
* `修復` 在遠端工作階段啟動前或期間取消 APK 建置時, 結果可能被誤報為失敗; 現在取消會收斂為唯一一次 `STATUS_CANCELLED` 終態, 同時保留舊產物並完成清理
* `修復` Android 9–11 可能無法透過現代封存 API 傳回有效 V2/V3 遠端 APK 的簽署者, 導致發布前誤拒絕; 宿主現在只在此平台缺口下使用已驗證的舊簽署者檢視作為備援, 同時繼續強制執行 `apksig` 密碼學驗證
* `修復` 使用不同簽署憑證但套件名稱與版本符合要求的遠端 APK，可能在密碼學簽署有效時通過主機端輸出驗證；主機端現在會在開啟 Binder 工作階段前固定所選預設或自訂簽署身分，並將其與唯一已驗證簽署者精確綁定，在取代舊產物前拒絕任何不相符
* `修復` 外掛程式程序死亡後的多個 Binder 終態回呼可能讓延遲的解除繫結誤傷立即重建的服務繫結, 導致外掛程式重新探索被阻塞至 60 秒繫結逾時的問題
* `修復` 啟用 R1 開發者開關但已安裝的 APK Builder 外掛程式明確宣告 `supportsRemoteBuild=false` 時, 提供者探索可能在沒有可操作提示的情況下停止; 宿主現會在準備階段前拒絕該外掛程式, 並說明安裝支援該能力的建置或關閉開關
* `修復` 完整日誌頁面及編輯器底部日誌面板啟用 TypeScript 命令列後, wrap-content 導覽列預留檢視會占用全部剩餘高度並將日誌清單壓縮為零; 現將命令列置於可見日誌區域上方, 日誌頁 FAB 也恢復原有底部偏移
* `修復` 壓縮檔案項目的重新命名/刪除操作未依目前工作階段宣告的修改能力收斂, 導致唯讀或不支援修改的工作階段仍可能顯示提供者無法完成的操作
* `修復` 檔案管理員 Explorer 動作在外掛程式啟用、重新授權或更新後可能繼續使用過期快取或檔案列參照, 導致已辨識檔案在 AutoJs6 重新啟動前回退至系統應用程式選擇器
* `修復` 檔案管理員中部分已辨識為影片的格式仍會開啟系統應用程式候選選單的問題; 現在所有已辨識的影片類型均優先呼叫官方影片播放器外掛程式, 外掛程式未安裝, 已停用, 未授權, 不相容, 不可用或啟動失敗時顯示復原指引及明確的 "用其他應用程式開啟" 選項
* `修復` 檔案管理員 M3U 檔案列同時顯示外掛, 播放和編輯三個操作圖示的問題; 播放入口優先關聯 3-Amber, 其次關聯 3-Terra, 無可用播放器時支援跳轉至外掛中心安裝, 啟用或授權
* `修復` 檔案管理器中的 .m3u8/.pls/.wax/.wvx/.asx 播放清單顯示重複外掛操作圖示的問題, 統一保留播放與編輯按鈕並繼續關聯播放器
* `修復` 檔案管理員查看含大量資源項目的普通 APK 資訊時因封存項目數超過檢查上限而失敗的問題; 普通 APK 直接讀取有大小限制的資訊清單, 多 APK 容器保留原有遍歷驗證
* `修復` 打包應用程式的 Rhino CommonJS require 載入器無法解密相依 JavaScript 模組, 導致 build-time 編譯的多檔案 TypeScript 專案以 "Cannot read file" 失敗的問題
* `修復` Node.js UI 控制代碼在 Android Activity 銷毀後可能仍保持作用中, 允許延遲更新並持續佔用提供者資源直到執行清理
* `修復` Node.js 外掛執行因模組探測及工作區封存預算過小而可能拒絕大型純文字相依圖及實際 node_modules 工作區
* `修復` Node.js 空白腳本及簡單腳本在包含大量檔案的工作目錄中因不必要的模組掃描及完整讀取候選檔案而啟動緩慢的問題
* `修復` 短生命週期腳本啟動介面隱藏時錯誤回收 Node.js 常駐處理程序的問題
* `修復` 打包應用的 Node.js 互動式長時間執行工作正常停止後, 預期的常駐處理程序 Binder 中斷連線回呼可能將 stopped 狀態覆寫為 failed 的問題
* `修復` Node.js console.assert 在斷言失敗時錯誤地丟擲 AssertionError 例外而非僅向 stderr 寫入失敗訊息的問題
* `修復` Node.js 腳本執行結果因執行階段處理程序穩定等待而延遲傳回的問題
* `修復` Node.js 活動執行取消時可能在異常處理程序隔離前過早移除執行登錄, 導致完成等待者阻塞及復原延遲的問題
* `修復` Node.js 外部執行階段 Binder 回應遺失後自動回退可能導致同一腳本被重複執行的問題
* `修復` Node.js TypeScript 專案經工作區封存執行後可能將僅供執行階段使用的編譯產物同步回原始碼目錄, 導致後續執行與原專案檔案衝突的問題
* `修復` Android 私有目錄 /data/user/0 與 /data/data 別名未正規化寫回外掛請求, 導致合法 Node.js workingDirectory 被誤判為超出 sandboxRoot 的問題
* `修復` nodePlugin 在宿主不含 Node 執行階段時錯誤排除所有 ABI 的 JNI/AAR 程式庫, 導致 Rhino Shell 初始化因缺少 libjackpal-termexec2.so 而當機的問題
* `修復` Node.js R6 證據收集及驗證因 apksigner 憑證摘要輸出格式變更而拒絕 Android Build Tools 37 回報的 APK 簽章身分的問題
* `修復` IntelliJ IDEA 直接執行專案時, 原生程式庫頁面對齊驗證因未使用目前建置的 APK 產物目錄而誤報找不到 APK 的問題
* `修復` IntelliJ IDEA 建置 Android 模組時因 compileJava 工作匹配歧義而失敗的問題
* `修復` 多變體建置時 Kotlin 編譯記憶體不足的問題
* `修復` AGP 9.1 建置時的 SDK XML v4 解析警告及 JVM 單元測試組裝工作誤觸發 APK 原生程式庫對齊檢查的問題 (共用建置外掛 1.8.3)
* `修復` IntelliJ IDEA 全專案建置在 Clean 後因 Python Runtime API 發佈 AAR 尚未產生而無法解析 Android 相依性, 且 conformance 測試夾具未同步追加的 AIDL 方法導致編譯失敗的問題
* `修復` 建置時 WorkManager 預設初始化器與隨需初始化設定衝突導致的 RemoveWorkManagerInitializer Lint 錯誤
* `修復` APK Builder Template 建置在 `clean` 後因預設 Runtime Kit 目錄缺少 `universal` 變體後綴而失敗的問題
* `修復` Android 回報目前已配置原生堆積位元組數為零時, 健康的 Node.js R6 常駐執行階段 soak 證據遭錯誤拒絕的問題
* `修復` Node.js R6 打包 APK 證據因收集器未準備目前 Inrt 範本或傳入目標 ABI 而遭跳過或錯誤綁定 arm64 的問題
* `修復` Node.js 專案啟動忽略 project.json 的 node.timeoutMs, 導致設定較長等待時間的指令碼仍在約 5 秒後逾時
* `修復` 跨 ABI 打包應用程式時錯誤將已安裝宿主處理程序的原生程式庫複製到其他 ABI 目錄, 導致 32 位打包應用程式載入 64 位 shell 原生程式庫並當機的問題
* `修復` TypeScript UI 腳本在引擎建立前編譯失敗時, 全域執行監聽器存取不存在的引擎可能導致宿主在回報診斷的同時當機的問題
* `修復` 堆疊框架與編譯診斷連結將 1 基原始碼欄號直接當作 0 基編輯器偏移, 導致游標多前進一個字元的問題
* `修復` DEX 編譯器實驗性宿主可能接受與要求 minApi 不相容的 DEX 版本, 錯誤拒絕合法的 65,536 field/method ID 邊界, 並在排隊的 pipe drain 延遲啟動時重新建立已回復的暫存成品
* `修復` runtime.loadJar 無法正常動態載入由較高 Java 版本編譯的 class 檔案的問題
* `修復` app.isInstalled/kill 等方法無法正常使用套件名稱引數的問題 _[`issue #495`](http://issues.autojs6.com/495)_
* `修復` 無 Root 權限裝置呼叫 shell 相關方法時可能出現假死的問題
* `修復` dialogs.multiChoice 方法傳回的多選結果被錯誤轉換為字串的問題 _[`issue #518`](http://issues.autojs6.com/518)_
* `修復` 腳本對話框在執行緒 Looper 結束時關閉, 因無法排程 timer 回呼而導致應用程式當機的問題
* `修復` 取消計時器或停止子執行緒後, 過期的回呼執行時間可能阻止 Looper 結束, 導致指令碼無法自動結束的問題
* `修復` selector 模組反射呼叫多載方法時可能選擇錯誤方法簽名的問題
* `修復` 元件搜尋使用 BFS 演算法時在指定最大結果數後多回傳一個結果, `find(0)` 回傳一個結果而非空集合, 且命中最大結果數後已取得的剩餘節點未被回收的問題
* `修復` 元件羅盤 (`UiObject.compass`) 參數中多段連寫的父層或可點擊段 (如 `p4ppp12p`, `k2k3`) 僅計入首段的問題
* `修復` 元件羅盤 (`UiObject.compass`) 的 `k` 段在範圍內未找到可點擊元件時回傳元件自身而非 `null` 的問題
* `修復` `auto.registerEvent` 回呼事件的 `source` 節點在計算父層索引時, 若節點樹已變更可能陷入無窮迴圈或擲回越界例外並導致無障礙服務當機的問題
* `修復` 無障礙服務重新啟動後, 未指定根節點的 `pickup` 及 `new UiSelector()` 建立的選擇器可能始終回傳 `null` 或空集合, 直到新建指令碼執行時期才恢復的問題
* `修復` `auto.stateListener` 為全域單一槽位, 多個指令碼設定時互相覆蓋, 且指令碼結束後監聽器未被移除的問題
* `修復` 無障礙服務已開始投遞事件但系統未回呼 `onServiceConnected` 時, `auto.waitFor` 等待方法無法被喚醒並可能等待至逾時的問題
* `修復` 使用 root 許可權關閉無障礙服務時, 若 AutoJs6 是唯一已啟用的無障礙服務, 關閉操作會靜默失效卻被回報為成功, 表現為服務無法關閉或關閉後立即 "自動恢復" 的問題
* `修復` 應用頁面建立時會透過 root 或安全設定自動重新啟用已被使用者明確關閉的無障礙服務的問題 (現僅恢復 "系統設定中已啟用但未執行" 的服務, 其餘情況由指令碼或版面配置分析器等按需啟動)
* `修復` `auto.start` 及 `auto.enable` 在無障礙服務已執行時仍透過 root 或安全設定重新啟用服務, 導致服務被無謂重新啟動的問題
* `修復` 重新啟動無障礙服務時未等待系統解除繫結舊實例即重新啟用, 在部分系統 (如 Android 8.0) 上被合併為 "仍然啟用" 而實際未重新啟動的問題
* `修復` `auto.windowRoots` 在系統尚未確定作用中視窗時 (如無障礙服務剛重新繫結後) 回傳含有空節點的陣列而非空陣列, 存取其元素的方法會擲回例外的問題
* `修復` `automator.isServiceRunning()` 僅檢查程序內是否存在無障礙服務實例, 與 `auto.isRunning()` 及側拉抽屜開關的 "執行中" 語義 (系統設定中已啟用且程序內已繫結) 不一致的問題
* `修復` 無障礙服務未啟用時 `auto()` 等入口擲回 "已啟用但未執行", 而已啟用未執行時反而擲回 "服務已停用" 的錯誤文案顛倒問題 (現按 未啟用 / 已啟用但未繫結 / 已繫結但尚未可操作 三種原因區分)
* `修復` `auto.services` 在未設定 root 或安全設定自動啟用方式時總是回傳空陣列的問題 (讀取已啟用服務清單並不需要這些權限)
* `修復` 側拉抽屜的無障礙服務開關對 "已啟用但未執行" 的服務顯示為開啟的問題 (現顯示為關閉並帶 "故障" 副標題, 開啟開關即重新啟用)
* `修復` 浮動按鈕的 "佈局範圍分析" / "佈局層次分析" 在無障礙服務未啟用但可自動啟動 (root 或安全設定) 時需要觸發兩次的問題 (第一次僅啟動服務, 第二次才執行分析; 現服務啟動後立即接續分析, 快速設定磚與佈局分析廣播同理, 且啟動過程不再阻塞主執行緒)
* `修復` 佈局分析 "複製為選擇器" 及選擇器字串輸出中引號, 反斜線與控制字元跳脫不完整的問題
* `修復` 佈局分析 "產生程式碼" 忽略設定文字及部分查詢動作, 將單個清單目標擴展為整組操作, 使用已棄用的查詢呼叫, Flow 組合回退或長按報錯, 跨套件同名 id 定位不正確的問題
* `修復` 版面分析浮動視窗及其對話方塊的文字/控制項在部分主題色或夜間模式下對比度不足的問題
* `修復` 無障礙服務重新啟動 (含被系統解除繫結後重新繫結) 後, 指令碼此前透過 `auto.registerEvent` 註冊的事件回呼與 `events.observeKey()` 的按鍵監聽全部失效的問題 (訂閱現由程序級事件匯流排持有, 不再隨服務實例消亡)
* `修復` Rhino 引擎更新後作用域 API 相容性回歸導致部分執行環境輔助功能異常的問題
* `修復` Rhino 模組橋接對 null/undefined, Callable, 建構函式, 原型, realm 及 Kotlin 傳回值的處理不正確, 可能造成跨 realm 物件, 回呼遺失或執行階段例外的問題
* `修復` Proxy 包裝函式無法作為 dialogs, events, timers, UI, selector 及 automator 等舊模組的回呼, 且 TimerThread 回呼參數可能以巢狀陣列傳遞的問題
* `修復` Callable 與 BaseFunction 相容多載導致一般 JavaScript 函式呼叫 EventEmitter.on 等 Java 方法時出現參數型別歧義的問題
* `修復` EventEmitter.once 監聽器在事件遞迴分派或監聽器擲回例外時可能重複呼叫的問題
* `修復` automator.home 等全域操作首次呼叫時可能因輔助 Looper 初始化遭中斷而擲回空指標例外, 且手勢 Y 座標可能誤用橫向縮放比例的問題
* `修復` 早期 JavaScript 模組遷移中的引數分派, 傳回值, 數值邊界, 影像座標及暫存資源生命週期與原始實作不一致的問題
* `修復` Android 14+ 申請螢幕擷取權限時可能因媒體投影前景服務啟動異常導致應用程式當機的問題 _[`issue #516`](http://issues.autojs6.com/516)_
* `修復` 多執行緒或跨檔案重複呼叫 OCR 時可能反覆申請螢幕擷取權限的問題 _[`issue #551`](http://issues.autojs6.com/551)_
* `修復` OCR 外掛選擇失敗時錯誤訊息可能指向錯誤引擎, 缺少所需引擎 ID/變體和已偵測外掛資訊, 且外掛查詢失敗時誤報未安裝的問題
* `修復` Paddle OCR 外掛服務長時間執行後可能中斷連線且無法自動復原的問題 _[`issue #515`](http://issues.autojs6.com/515)_ _[`issue #525`](http://issues.autojs6.com/525)_
* `修復` Paddle OCR 外掛未處於執行狀態時無法被自動喚醒, 導致功能呼叫或專案打包失敗的問題 _[`issue #517`](http://issues.autojs6.com/517)_
* `修復` `images.resize(ImageWrapper, width, height)` 錯誤用法會將高度作為 OpenCV 插值參數傳入, 且不相容的原生例外展開可能導致應用程式當機而非回報腳本例外的問題
* `修復` MediaInfo 外掛媒體庫無法使用或 Binder 呼叫異常時, 檔案管理器媒體詳細資訊可能只顯示 "..." 或導致應用程式當機, 且無法改用 Android 平台中繼資料顯示的問題
* `修復` 檔案管理器影片詳細資訊對話框錯誤顯示內嵌音訊格式及中繼資料欄位的問題
* `修復` 部分 WebView 版本及實體裝置上的 Markdown 與 HTML 預覽主文件遭請求攔截器誤拒的問題
* `修復` 圖片檢視器雙指縮放未能緊隨手指移動, 縮小時難以恢復原始大小, 且首次進入時 ActionBar 可能短暫或持續使用主題色的問題
* `修復` 圖片檢視器退出時 Glide 仍嘗試使用已銷毀的 Activity 發起圖片載入, 導致應用程式當機的問題
* `修復` 無障礙服務在連續執行 back 等操作時可能意外中斷的問題 _[`issue #527`](http://issues.autojs6.com/527)_
* `修復` 腳本反覆執行後記憶體占用可能持續增加的問題 (試修) _[`issue #548`](http://issues.autojs6.com/548)_
* `修復` 透過外部 URI 或背景執行腳本時可能無法保留原始路徑, 導致相對路徑解析異常的問題 _[`issue #545`](http://issues.autojs6.com/545)_
* `修復` Tasker 無法正常呼叫並執行腳本的問題 _[`issue #501`](http://issues.autojs6.com/501)_
* `修復` 主頁文件標籤頁操作時, ViewPager 可能因異常多指觸控序列產生無效指標索引並導致應用程式當機的問題
* `修復` Ace 編輯器在輸入法互動/文字選取/程式碼折疊/雙指縮放/堆疊跳轉等場景下的多項互動問題
* `修復` 編輯器設定主標題, fx 函式面板, 固定在左側的文件抽屜及 Ace 文字選取工具列可能沿用 AutoJs6 應用程式配色而非目前的編輯器主題, 且 Ace 行操作可能重複出現的問題
* `修復` 全螢幕編輯模式下軟體鍵盤可能覆蓋多功能按鍵列, 且全螢幕提示 Snackbar 下方導覽列區域可能透明的問題
* `修復` 程式碼編輯器說明文件側欄可能僅顯示主題背景而無內容, 且編輯器設定缺少依據編輯器背景產生的穩定分層角色色彩, 導致清單捲動及項目重用後標題, 副標題, 分類及控制項色彩可能遺失的問題
* `修復` 非全螢幕編輯模式下軟體鍵盤可能覆蓋多功能按鍵列的問題
* `修復` 程式碼編輯器資訊列可能省略檔案副檔名或在開啟文件時短暫顯示檔案末尾的暫時游標座標, 且應用程式與編輯器主題明暗不一致時多功能按鍵列與軟體鍵盤之間可能閃現亮色間隙
* `修復` 大型檔案使用不相容編碼重新載入後產生超長文字行時, Ace 編輯器可能長時間無回應並回退至原生編輯器的問題
* `修復` Ace 外部外掛在編輯器或設定介面使用期間遭停用或解除安裝時可能遺失未儲存的編輯狀態或導致介面異常的問題
* `修復` 外掛中心清單版面簡化後操作按鈕的圖示與文字間距過大的問題
* `修復` 外掛中心無法將 `Sept 13, 2026` 等九月日期正規化為 `yyyy-MM-dd` 的問題
* `修復` 外掛中心在外掛沒有線上圖示 URL 時, 舊遠端圖示快取可能覆蓋目前已安裝圖示的問題
* `修復` 外掛中心切換明暗模式時, 保留的本機圖示快照可能短暫顯示上一主題圖示的問題; 背景同時準備兩種主題圖示, 首次繫結即選擇目前主題
* `修復` 外掛中心圖示快取及下拉重新整理狀態在部分場景下可能異常的問題
* `修復` 單一本機外掛處理程序異常時可能導致外掛中心清單完全空白的問題
* `修復` 從其他頁面或背景返回外掛中心並觸發並行重新整理時, 已安裝的傳統 SDK 外掛可能被短暫移除或替換為索引中的停用項目, 導致清單重新排序/開關重新整理及捲動位置偏移的問題
* `修復` 主頁外掛標籤頁中已安裝外掛的圖示無法隨夜間模式動態切換的問題
* `修復` 外掛專案發布多個變體時外掛中心可能安裝錯誤變體 APK 的問題
* `修復` APK Builder Template 外掛可能重複顯示或錯誤提示更新的問題
* `修復` 離線專案文件外掛更新期間完整性驗證快取可能重複使用舊內容的問題
* `修復` 檔案管理器 APK 詳細資訊中缺少 Manifest 按鈕及完整多行內容的問題
* `修復` 檔案管理器解析 APK 時錯誤忽略 ZIP 條目名稱的大小寫, 導致部分包含僅大小寫不同條目的有效安裝包被誤判為含有重複條目而無法檢視或安裝的問題
* `修復` 外部 URI 快取匯入因信任內容提供者檔案名稱而可能引發路徑穿越或同名檔案覆寫的問題
* `修復` 檔案管理器在資源回收筒刪除檔案後清單捲動位置被重設的問題
* `修復` 搜尋框可能保留預設藍色游標而未使用適合目前主題的前景色, 且搜尋提示可能停留在後備語言而未跟隨目前應用程式語言的問題
* `修復` 主頁抽屜開關項的開關檢視有效點選區域過小的問題
* `修復` 主頁側拉抽屜在 360x640 等窄螢幕或視窗縮放後出現開關裁切, 底部按鈕缺失的問題
* `修復` 主頁關閉或重建時, 抽屜清理監聽器存取已釋放的上下文導致應用程式當機的問題
* `修復` 浮動按鈕選單顯示的最近應用程式套件名稱及 Activity 資訊異常的問題 _[`issue #529`](http://issues.autojs6.com/529)_
* `修復` 浮動按鈕 "執行腳本" 對話框最小化後可能無法還原的問題 (試修) _[`issue #500`](http://issues.autojs6.com/500)_
* `修復` 啟動頁背景未延伸到瀏海螢幕的短邊切口區域的問題
* `修復` 檔案管理器可能不會重新整理 Explorer Action 新提交的同層輸出, 或把已提交的輸出目錄顯示為檔案的問題
* `修復` Android 7 離開原生壓縮檔頁面時, 工作階段預覽快取清理路徑呼叫較新的 File NIO API, 可能導致應用當機的問題
* `修復` 從 Explorer Action 播放器返回時, 檔案管理器即使保留了目錄與捲動位置仍可能連續觸發兩次完整列表重新繫結, 導致頁面劇烈閃動的問題
* `修復` 從其他首頁分頁點擊切換到檔案頁面時缺少 ViewPager 轉場動畫的問題
* `修復` 從影片播放器等橫向外掛頁面返回時因設定變更重建整個主介面, 導致檔案管理器劇烈閃動甚至連續閃動兩次的問題
* `修復` 首次開啟首頁外掛頁面或獨立外掛中心, 或造訪外掛頁面後返回任一首頁分頁時, 立即進行的手勢可能被阻塞約半秒的問題
* `修復` 切換到首頁外掛頁面以及外掛中心下拉刷新的動畫, 可能在外掛服務中繼資料探索與冷啟動高密度清單項目建立期間中途凍結, 且獨立外掛中心還可能在 Activity 建立期間執行無障礙服務的 root/settings 自動復原
* `修復` ZIP/JAR 等由外掛程式處理的檔案, Explorer Action 拼圖圖示填滿整個操作格而顯得過大的問題
* `修復` 檔案管理器無法可靠區分同為 `.ts/.mts` 副檔名的 TypeScript 原始碼/宣告檔案與 MPEG-2 TS 影片, 且 `.d.ts/.d.mts/.d.cts` 未必一律視為宣告檔案的問題
* `修復` 由外掛程式支援的 `.py/.ts/.java/.kt` 原始碼檔案未統一顯示可執行原始碼圖示與執行入口的問題
* `修復` 透過 Intent 等進入點執行 `.ts/.tsx` 檔案時, 宿主主控台錯誤顯示 `.js` 副檔名並遺失原始檔案路徑的問題
* `修復` 缺少 APK 封裝中繼資料的最小腳本專案會退回 `main.js`, 而非啟動 `project.json` 宣告的 `main` 進入點
* `修復` 帶應用程式 ID 後綴的外掛無法正確解析啟用入口, 且喚醒成功與服務可用狀態可能不一致的問題
* `修復` `UiObject` 在帶有節點配置器時呼叫 `getChild()` / `getParent()` 等 compat 存取器會無限遞迴 (配置器回呼了包裝物件自身的存取器); 修復後配置器在原始節點上操作, 登記按物件同一性而非節點相等性 (指向同一檢視的兩個實例不再只回收其一). 配置器仍未接入指令碼執行時: Android 13 以下節點按程序池化, 退出時批次回收可能回收到已被其它指令碼複用的實例
* `修復` 選擇器 `contextClickable()` 條件在選擇器的字串表示 (如 `selector().toString()` 與錯誤訊息) 中誤顯示為 `checkable`
* `修復` 全域函式形式的 `filter(fn)` 呼叫失敗 (報 "Invalid arguments"), 以及集合選擇器陣列中的正規表示式不被接受 (如 `pickup({text: [/regex/]})`)
* `修復` 選擇器字串形式 (如 `selector().toString()` 與錯誤訊息) 中的小數在以逗號為小數點的介面語言下 (如法語, 俄語) 顯示為 `boundsLeft(0,5)`; 現在一律使用點號
* `修復` 全域函式形式的選擇器方法 (如 `algorithm('xyz')`, `textMatch('(')`) 擲出的 Java 例外無法被指令碼的 `try / catch` 捕捉, 並會直接終止指令碼; 現在作為可捕捉的指令碼錯誤擲出
* `修復` 設定 `findOnUiThread` 旗標時, 投遞到 UI 執行緒的查詢可能因喚醒訊號遺失 (UI 執行緒先於指令碼執行緒進入等待完成查詢) 而永久停滯, 且 UI 執行緒阻塞時 `findOne(timeout)` 不會逾時; 現在以查詢逾時為上限等待, 喚醒不再遺失
* `修復` `UiObject.clickBounds()` 在指令碼執行緒上臨時準備 Looper 並在手勢完成後 `quit()`, 實際結束的是指令碼執行緒自身的事件迴圈, 此後 `setTimeout` 等回呼不再執行且指令碼提前結束; 現在由查詢得到的節點經指令碼的手勢派發器 (servant Looper) 點按, 其它來源的節點保持原路徑
* `修復` `pickup` / `detect` 的結果型別 `['compass', 'p']` 一律回報 "未知結果型別", 因 `compass` 在攔截表中登記為 `String` 參數而其簽章為 `CharSequence`; 現在可用
* `修復` 版面配置分析器為集合控制項的子項產生程式碼且未選擇動作時, 產生的 `forEach` 程式碼區塊含無效敘述 `target.;`; 現在省略該行
* `修復` 控制項集合陣列上同名的多個多載只保留最後一個 (如 `performAction(action)` 與 `performAction(action, ...args)`), 以另一種參數個數呼叫會回報參數不符; 現在按名稱繫結全部多載
* `修復` `textContains` / `descContains` / `contentContains` / `idContains` / `classNameContains` / `packageNameContains` 等 `contains` 系列的普通字串參數自 6.7.0 Alpha13 起被當作正規表示式模式 (`textContains(".")` 匹配任意非空文字, `textContains("1+1")` 匹配 "11", `textContains("(")` 回報錯誤), 現在恢復為字面子字串, 與 `text(s)` / `textStartsWith` / `textEndsWith` 一致; 正規表示式需求請傳正規表示式參數 (`textContains(/re/i)`) 或使用 `textMatch` / `textMatches`, 字串形式的 "/re/flags" 在 `contains` 中不再視為正規表示式
* `修復` 懸浮視窗 (`floaty.rawWindow` / `floaty.window`) 的 `requestFocus()` / `disableFocus()` 在指令碼執行緒呼叫時直接更新已附加視窗的版面, 偶發 `CalledFromWrongThreadException`; 現改為在 UI 執行緒執行 (與 `setSize` / `setPosition` 一致)
* `修復` 無障礙通知觀察者 (`events.observeToast()` 等所用) 把每條系統通知都當作其它應用程式的 toast 處理 (對通知的型別檢查寫成了本應用程式自己的子類別, 永遠不成立), 通知從未送達它的通知監聽器, 其它應用程式的通知則被誤報為 toast; 現改為檢查框架的 `android.app.Notification`
* `修復` `console.setPosition` / `setSize` / `setGravity` 等方法與 `console.show()` 的呼叫順序影響結果 (如 `console.show(); console.setPosition(500, 400)` 需在兩者之間 `sleep` 才生效, 反序呼叫亦可能遺失設定); 現改為記錄設定並在視窗附著後統一套用, 呼叫順序與時機不再影響結果
* `修復` 拖曳控制台浮動視窗標題列移動視窗偶爾失效 (拖曳手勢在視窗附著前綁定, 並與按鈕點擊及視窗關閉競爭); 現在標題列拖曳在視窗附著後建立, 移動範圍限制在螢幕內並在拖曳結束後記錄位置
* `修復` 控制台浮動視窗四周存在 12dp 的留白, `console.setPosition(0, 0)` 無法貼到螢幕邊緣; 現已移除留白, 位置座標預設從狀態列下方開始計算 (可用 `setAvoidStatusBar(false)` 關閉), 視窗位置與尺寸均限制在螢幕範圍內
* `修復` MCP Server 工作階段控制器可能把 `openServer` 之後拉取的 `starting` 快照覆蓋到外掛已經透過回呼送達的 `running` 狀態之上, 導致 `status` 始終不回報監聽器已執行; 現在拉取的快照只在拉取期間沒有回呼狀態到達時才發布
* `修復` MediaInfo 詳情與快照的 Complete name 顯示原始檔案路徑, 避免顯示私有快取或描述符路徑, 同時保留快照的顯示檔案名稱
* `修復` Node bridge 的 `accessibility.swipe` / `accessibility.gesture` (MCP Server 與 Node 外掛的座標手勢) 在被重複使用的 broker 派發執行緒上從第二次手勢起總是回應 `false` (取消): 無 handler 的手勢 automator 會在呼叫執行緒上準備 Looper 並在首個手勢完成後退出它, 該執行緒上的下一次呼叫立即返回, 真正的結果被投遞到已結束的 handler; bridge 現在與 Python 宿主 broker 一樣把主迴圈 handler 交給 automator
* `修復` OpenCV 與 ImageQuant 外掛策略將以 NDK r28c (28.2.13676358) 建置的原生程式庫判定為原生 NDK 版本不符, 導致重新建置後的 OpenCV 外掛 v1.1.2 無法載入; 現同時接受 r26b 與 r28c 建置
* `修復` OpenCV 與 Image Quantization 外掛在原生函式庫快取唯讀設定失敗後仍嘗試載入的問題, 相容 Android 17 的動態程式碼載入限制
* `修復` Android 7.0 / 7.1 的 runtime.loadDex 因 ART 最佳化輸出與輸入路徑重合, 損壞 DEX 快取並導致類別載入失敗的問題
* `修復` 動態載入的 Kotlin 編譯器因 Unsafe 原子操作受限或反射執行庫缺失而編譯失敗的問題
* `修復` 舊版 Rhino 作用域反射介面不相容導致外部程式庫無法自動註冊 Java/Kotlin 類別的問題
* `修復` MediaProjection 橋接截圖遇到暫時空幀時最多重試 6 次, 支援擷取管線重新整理後的連續截圖, 無需再次申請授權
* `修復` `images.imread`, `images.matToImage` 及以 Mat 為基礎的圖片所使用的 OpenCV 矩陣不是從未釋放, 就是與可能被垃圾回收的來源共用原生標頭, 導致原生記憶體洩漏與懸空指標
* `修復` 腳本結束時會回收其他執行中腳本的圖片; 現在圖片依腳本執行環境分別登記與回收
* `修復` `colors.isSimilar` 與 `images.detectColor` 使用 `hs` 演算法時符合任何顏色, `colors.rgb` / `colors.rgba` 將小數或百分比分量視為整數
* `修復` `images.isEqual` 對僅單一通道不同的圖片傳回 true, `images.mse` 結果在 `255` 處飽和, `images.ncc` / `images.isGrayscale` 無法正確處理灰階或 RGBA 輸入
* `修復` `images.invert` 遺失 alpha 通道, `ImageWrapper.saveTo` 儲存以 Mat 為基礎的圖片時紅藍通道顛倒, `images.clip` 對超出圖片範圍的區域靜默傳回錯誤結果
* `修復` 以路徑傳入 `images.clip`, `images.findCircles` 與 `images.readPixels` 的圖片可能在使用前被回收, `images.findImage` / `images.matchTemplate` 在範本靠近圖片邊緣或大於搜尋區域時可能擲回例外或傳回錯誤結果
* `修復` `images.load` 採用 30 秒逾時並關閉連線, `images.saveBitmap`, 特徵比對與直方圖比較會釋放暫存串流與矩陣
* `修復` 範本比對在較粗的金字塔層級提前達到閾值時直接傳回該層級的粗略座標 (可能偏差數個像素), 相鄰候選點可能重複傳回同一位置, 且結果未依相似度排序也未受 `max` 限制
* `修復` `images.matchTemplate` 在圖片與範本通道數不同時擲回例外, 純色範本永遠無法比對, `useTransparentMask` 僅遮罩了 alpha 通道而非透明像素; `MatchingResult.sortBy(compareFn)` 在比較函式傳回非整數時失敗
* `修復` `images.matchFeatures` 在目標特徵以非 1 的 `scale` 計算時傳回尺寸錯誤的邊框, 對角點重新排序導致旋轉目標的角點與自身角點失去對應, 單應矩陣不可靠時可能傳回變形的邊框, `drawMatches` 圖片由描述子矩陣而非圖片繪製, 且圖片沒有關鍵點時擲回 OpenCV 錯誤
* `修復` `images.detectAndComputeFeatures` 洩漏中間矩陣, 且 ORB 因預設 31 像素邊界在小於 63 像素的圖片中偵測不到任何關鍵點, 在較小目標中也僅能偵測到少量關鍵點
* `修復` MCP Server 工作階段控制器在使用者於系統設定中強制停止外掛程式後約一秒內重新繫結並再次啟動了外掛程式, 違背了使用者的決定; 現在外掛程式處理程序死亡後若已不可用 (被停止, 被停用或已解除安裝) 則不再重新繫結, 側欄開關關閉, 副標題顯示與全新安裝相同的啟用指引
* `修復` 版本歷史的內容指紋改為記錄儲存後的內容, 檔案重新命名或移動後的下一次儲存能重新關聯既有歷史; 且僅在舊路徑已不存在時才按指紋重新繫結, 內容相同的副本不再搶佔原檔案的歷史
* `修復` 回收站與版本歷史的內部儲存根目錄按目前使用者的實際路徑解析, 不再固定為 /storage/emulated/0, 多使用者或工作設定檔環境下可正常納管
* `修復` 版本歷史恢復到磁碟以及回收站以覆蓋方式恢復檔案前, 先把目標檔案的目前內容記錄為一條 RESTORE_PRE 版本; 回收站覆蓋恢復改為先寫入臨時檔案再取代目標, 中止或當機不再留下半成品檔案
* `修復` 回收站恢復衝突時的自動改名按條目類型判斷目錄, 名稱含點的目錄 (如 my.project) 不再被拆出副檔名
* `修復` 版本歷史對話框列出全部已保留的版本 (此前最多 20 條), 編輯器入口同樣提供預覽, 刪除某個版本後重新載入時保持原模式
* `修復` 版本歷史的孤立 blob 清理增加 30 分鐘最小保護期, 避免誤刪資料庫記錄尚未寫入的新快照
* `修復` VSCode 擴充功能儲存專案時, PC 端已刪除的檔案仍殘留在裝置上以及快取目錄中歷史檔案反覆出現的問題 _[`vscode-ext issue #29`](http://vscext-project.autojs6.com/issues/29)_
* `修復` VSCode 擴充功能連線接收專案封存時將整個位元組陣列轉為字串寫入日誌, 可能導致 OOM 崩潰的問題 _[`issue #523`](http://issues.autojs6.com/523)_
* `修復` VSCode 擴充功能連線因單個半死連線阻塞日誌寫入執行緒, 導致新連線無法接收日誌的問題 _[`issue #385`](http://issues.autojs6.com/385)_
* `修復` 檔案管理器重新命名或移動檔案 / 目錄後, 版本歷史停留在舊路徑; 現在歷史跟隨新路徑 (目錄按前綴批次改寫), 移動覆蓋已納管檔案時兩份歷史合併到目標路徑
* `修復` 回收站單個項目還原失敗時仍提示 "完成" (進度對話框吞掉了異常); 現在會提示實際錯誤
* `修復` 檔案管理器在主執行緒上顯示的操作提示 (如 "已移入回收站") 會重複顯示兩次
* `修復` 還原回收站中的目錄時, 封存項目名會按目標目錄的規範路徑校驗, 含 ".." 或絕對路徑的項目不再可能寫到目標目錄之外 (此類項目略過並記錄日誌)
* `修復` VSCode 擴充功能傳送的 TypeScript, Lua, Node.js, Bun, QuickJS 及 Python 等指令碼一律按 Rhino JavaScript 執行的問題, 現按檔名路由至對應引擎
* `修復` VSCode 擴充功能連線的握手拒絕或連線失敗對話框在 Activity 已銷毀 (切換語言或深色模式) 時可能異常的問題, 改為以 toast 顯示
* `修復` 客戶端模式的伺服端位址歷史記錄僅在握手失敗時寫入, 成功連線過的位址反而從不記錄, 導致位址對話框的 "歷史記錄" 始終為空的問題
* `修復` 檢查更新選擇安裝套件時, 若清單中沒有與首選 ABI 相符的安裝套件, 可能選中清單靠後的低優先順序安裝套件 (如通用套件) 而非次優先 ABI 的安裝套件
* `修復` 內建套件安裝器在 Android 9-11 上可能因平台封存解析器回傳的簽章資訊為空而以 "Base APK has no verifiable signer" 拒絕 V2/V3 簽章的 APK, 現回退至已棄用的封存簽章檢視
* `修復` 外掛中心的五款 Three 系列外掛圖示使用透明背景並跟隨宿主明暗主題, 避免顯示啟動器底色
* `修復` 安裝套件資訊識別一般 ZIP 中的 APK 分割套件集合, 也支援沒有常見安裝套件副檔名的同類容器
* `修復` Android 14 及以上版本在 AutoJs6 處於前景時發起安裝, 3-Setup Installer 無法顯示確認介面的問題
* `修復` 安裝套件根資訊清單和共用 UID 解析可能受註解或偽造命名空間干擾的問題, 對重複屬性, 資源識別衝突和無法確認的共用 UID 拒絕繼續處理
* `修復` 外掛中心成功重新整理索引後仍從快取恢復已移除或更名舊外掛的問題
* `修復` 外掛程式中心詳細資訊對話框圖示缺少圓形底色, 以及快取圖示邊框未隨明暗主題更新的問題
* `修復` 在傳統佈局中選擇的材料設計顏色, 切換佈局後無法透過 "定位當前主題色" 找到, 且材料設計顏色頁面不顯示當前顏色摘要的問題
* `最佳化` Three Stove Agent 模型負載在 Android 7-10 上透過公開 API 讀取, 保留檔案位移與停滯管道取消能力
* `最佳化` 宿主 lint 檢查依路徑排除 AutoJs4 相容類別和資源, 其餘宿主程式碼繼續檢查
* `最佳化` 外掛中心觀察到與宿主同憑證簽章 (OFFICIAL) 的預設關閉外掛 (YOLO, MCP Server) 完成全新安裝後自動寫入明確啟用記錄並完成啟用探測; 第三方信任或使用者授權簽章, adb 側載及覆蓋安裝仍需手動啟用, 執行時仍只讀取已儲存的記錄
* `最佳化` 無障礙服務, 用戶端模式與伺服器模式管理員統一分區標題的加減按鈕和分隔線; 設定區可完全收合並透過齒輪開啟獨立設定對話方塊; 用戶端模式收合後的控制列提供歷史記錄入口; 伺服器模式收合後的控制列提供中斷所有用戶端連線按鈕
* `最佳化` 底部日誌面板, Activity 日誌頁面, globalconsole 元素, console 元素及浮動主控台分別記憶時間顯示與格式; 前兩者預設顯示時間, 後三者預設不顯示
* `最佳化` 工作清單與指令碼開始/結束日誌統一使用 $cwd, $sdcard, $sample, $files, $cache 和 $data 簡寫已知目錄, 保留實際進入點檔案名稱與副檔名; 目錄比對正確處理路徑邊界與末尾分隔符
* `最佳化` MCP 伺服器抽屜項目使用線條略微加粗的 MCP 協定圖示, 大小和著色與其他項目保持一致
* `最佳化` Flow 例外的 flowStack 與 stack 保留非同步工作方法, 指令碼檔案和行號, console.error 自動顯示診斷堆疊
* `最佳化` Flow repeatUntil 的 maxAttempts 預設為 0, 表示在總逾時範圍內不限嘗試次數
* `最佳化` Flow 穩定等待支援有序節點集合比較與 snapshot 資料投影, 完善子流程截止時間, 取消及錯誤傳播
* `最佳化` Android 17 區域網路授權統一移至外掛程式中心啟用流程及外掛程式設定, 不再提供啟動器授權頁面; 未獲授權時保持關閉並靜默略過自動啟動
* `最佳化` 宿主與相容外掛顯示進階保護說明, 區分全域模式, 實際服務可用性及安全設定控制能力
* `最佳化` 封裝應用程式按專案共用路徑需求引導儲存空間授權, 拒絕授權, 設定返回, 隱藏桌面入口恢復及開機啟動均檢查實際存取狀態
* `最佳化` media.playMusic 使用音樂前景服務, 管理音訊焦點, 通知控制, 指令碼播放擁有權與結束清理
* `最佳化` 前景服務說明補充 Android 17 背景音訊限制, 以及在可見介面重新啟動服務後重試播放的操作指引
* `最佳化` 完善螢幕取色器外掛程式的 Activity 啟動授權, Android 16 及以上使用宿主可見時生效的傳送方選項
* `最佳化` 區域網路 HTTP, WebSocket 及 Node 受控網路橋的連線失敗提示, 保留原始錯誤並說明授權後的重試方式; 通用權限名稱轉換不受系統語言影響
* `最佳化` 主頁側拉抽屜的 "連線到計算機" 群組更名為 "遠端連線", 涵蓋用戶端模式, 伺服器模式與 MCP 伺服器
* `最佳化` 專案 compileSdk 提升至 37 (Android 17), 完善 SDK 遷移的偵錯建置與 lint 驗證
* `最佳化` 宿主與打包應用 (inrt) 的 targetSdk 提升至 37 (Android 17), 打包應用由 targetSdk 29 跨越至 37; 在 Android 17 上按需申請本地網路權限, 打包應用按需引導共享儲存存取
* `最佳化` 安裝套件檢查統一支援 262144 個封存項目, 提高單一套件, 巢狀掃描, 資訊清單與中繼資料預算以相容大型 APK/AAB/APKS
* `最佳化` 資訊對話框共用可重用的項目元件, 支援在任意位置插入 key:value 項目, 自動對齊鍵欄並在關閉時取消非同步讀取
* `最佳化` APK/APKS/XAPK/APKM/APKZ/AAB 資訊對話框的元件資訊移至右上角選單的 "更多資訊" 對話框, 共用主對話框的項目樣式並同步更新讀取結果
* `最佳化` APK 等安裝套件的資訊對話框將 APK Inspector 詳細摘要移至右上角更多選單, 位於 "更多資訊" 上方
* `最佳化` Node bridge 無障礙模組新增 dump (文字, JSON 或 XML 視窗快照, 上限 400 節點, 32 層與 256 KiB), explain (選擇器描述符的逐過濾器命中數) 與 screenshot (Android 11+ 的無障礙截圖, 支援縮放, 區域, 格式與品質選項, 以一次性快取檔案或工作目錄檔案交付), app 模組新增 currentWindow (目前套件名稱, 活動與視窗清單)
* `最佳化` Node bridge 新增 files 模組 (list, stat, read, write, mkdir, rename, delete), 以工作目錄為根: 路徑須為正規化的相對路徑, 符號連結無法逃逸, 讀寫上限 1 MiB 且內容為 UTF-8 或 base64, 清單上限 2000 項, 寫入與刪除分別需要專用的 files.write 與 files.delete 能力權杖, 變更後重新整理工作區檔案管理器
* `最佳化` Node bridge 新增 console 模組 (console.tail 讀取全域控制台的最新條目, 支援 lines 上限 500, sinceId, level 與 256 KiB 預算), engines.execScript 與 execScriptFile 新增 captureConsole 與 waitMs 選項 (等待指令碼結束並收集其 id 視窗內的控制台輸出, captureMode 為 global-window), 控制台最多保留 4096 筆日誌 (超出捨棄最舊, 控制台檢視依條目 id 重新整理), ConsoleImpl 公開 addLogListener 與 removeLogListener
* `最佳化` Node bridge 新增 engines.list 與 engines.stop(id) 以及 scope 為 host 的 engines.stopAll 的宿主程序語義 (列出與停止宿主 ScriptEngineService 中的執行, 既有 engines.all / stopAll 語義不變), device.info (機型與系統版本, 螢幕尺寸 / 密度 / 方向, 電量, 宿主版本, 無障礙服務狀態), 以及唯讀的 app.listSamples / app.readSample (列舉與讀取內建範例指令碼, 路徑限制在範例根目錄內, 單次讀取上限 1 MiB)
* `最佳化` `TypeScript 控制檯` 現在預設保持收起, 僅在編譯器插件可用時透過完整日誌頁或編輯器日誌面板的更多選單按需顯示並分別記憶狀態; 片段執行期間綠色執行圖示會切換為可停止的紅色圖示, 完整日誌頁同步顯示實際工作路徑, 編輯器日誌面板的更多選單亦集中提供複製, 傳送及匯出操作
* `最佳化` TypeScript TSX 執行階段驗證提示現在會跟隨應用程式語言, 已補齊全部 10 種支援語言, 不再顯示硬編碼英文
* `最佳化` Ace 字型預覽在目前字型尚未下載時於多功能鍵盤上方顯示下載提示, 切換至可用字型或下載完成後自動隱藏
* `最佳化` 程式碼編輯器 `跳轉到行` 對話框在輸入值超過文件最大行號時立即將其校正為最大值
* `最佳化` Node.js 受控 Fetch 請求與回應主體, WebSocket 傳送訊息支援檔案描述符傳輸, 減少大型二進位資料跨 Binder 傳輸的負擔
* `最佳化` 移除已過時的 Node.js Runtime legacy TypeScript stripping 請求旗標; 原始 TypeScript 維持只可經編譯器處理, Runtime 絕不再以正則表達式擦除
* `最佳化` 移除宿主端 Node.js Gradle 開關及重複的範例, 型別宣告, 專案精靈與驗證腳本, 執行階段預設值和開發資產現統一由 Node.js Runtime 外掛維護
* `最佳化` DEX 編譯器診斷現在於既有預算內保留經脫敏的 INFO/WARNING/ERROR 詳情及可選邏輯來源, 封存檔項目與位置元資料; 開發者選項會顯示僅存於程序內的最近一次 JAR 載入路徑, 並提供 BUSY, 逾時及回退提示
* `最佳化` TypeScript 編譯現在為每次請求記錄隱私安全的效能觀察, 涵蓋準備, 快取查詢, 編譯器繫結/預熱, 遠端編譯, 輸出處理, 總耗時, 封存檔大小, 原始檔案數及宿主堆積記憶體變化, 並將編譯器耗時細分為 ingress 前, ingress, 階段間隔, parse, semantic, emit 及 emit 後區間; 格式錯誤或亂序的 progress 回呼會被忽略, 這些觀察不設效能門檻
* `最佳化` KeyCode 方法提升引數相容性 _[`issue #492`](http://issues.autojs6.com/492)_
* `最佳化` KeyCode 方法支援使用 Shizuku 權限傳送鍵碼指令
* `最佳化` 延遲初始化非關鍵元件, 提升應用程式啟動效能
* `最佳化` 應用程式啟動時不再自動彈出許可權申請對話框, 缺少所有檔案管理許可權時改為在主頁顯示可直接授權的提醒
* `最佳化` 快取 Rhino 內建資源編譯結果及執行環境增強函式中繼資料, 提升腳本初始化效能
* `最佳化` Rhino 引擎同步上游, 支援 new.target, 並改善作用域, TypedArray 與偵錯工具相容性
* `最佳化` 提升 Rhino 控制檯列印大型陣列時的格式化效能
* `最佳化` 重構 Kotlin 模組增強架構, 以宣告式掛載規範支援多個別名及各名稱獨立的 `$` 前綴控制, 明確繫結 Rhino realm, 分離 tasks 與 work_manager 語意, 並補充模組相容性及語意迴歸測試
* `最佳化` 重複使用 Node.js 常駐處理程序及處理程序層級執行環境, 提升腳本連續執行時的啟動效能
* `最佳化` Node.js 啟動支援分階段計時及可信的冷啟動/執行階段暖啟動效能報告
* `最佳化` 快取 Node.js 內建原生程式庫解析結果, 避免重複探索外掛
* `最佳化` 快取已授權的 Node.js 外部執行階段候選項及常駐 Binder 工作階段, 支援狀態失效與原子重新連線, 降低插件探索及繫結開銷
* `最佳化` 移除 Node.js 預設執行路徑中的專案全目錄加密模組掃描, 提升複雜腳本及大型專案的啟動效能
* `最佳化` Node.js 引擎切換加入實際 Rhino/Node 多輪執行迴歸測試, 涵蓋外部外掛常駐工作階段重複使用/復原及完整清理
* `最佳化` Node.js 外部外掛常駐執行環境加入 soak 測試及生產證據閘門, 涵蓋裝置/ABI 矩陣/執行環境生命週期/資源指標及 8 小時/24 小時穩定性
* `最佳化` Node.js 原生驗證支援解析並驗證外部執行階段外掛的原始碼及建置根目錄
* `最佳化` Node.js 最終整合及發佈閘門在必要報告/目前原始碼證據/生產驗收產物缺失或過期時採用 fail-closed 策略
* `最佳化` Node.js 原始及動態輸入預設關閉舊 TypeScript 正規表示式 stripping, 僅在明確啟用暫時請求層級相容開關時保留, 一般執行統一要求編譯器產物
* `最佳化` Node.js TypeScript 計算式 import() 在封閉編譯快照內將 .ts/.mts/.cts 確定映射至預先編譯的 .js/.mjs/.cjs 模組, 未命中/歧義/越界目標傳回穩定錯誤且不回退讀取執行時新增的 TypeScript 原始碼
* `最佳化` 獨立 Python 指令碼發生精確 ModuleNotFoundError 時在控制檯附加安全提示, 引導 sibling/local 模組 import 使用包含 `project.json` 與 `type: python` 的明確 Python 專案, 且不揭露私人路徑
* `最佳化` Python Runtime 輸出 chunk 到達時立即寫入宿主控制檯, 使用按 stdout/stderr 獨立的增量 UTF-8 解碼保留跨 chunk 碼點, 並將端到端輸出上限提高至 16 MiB
* `最佳化` Python 專案的 project.json 支援 timeout 正整數毫秒設定, 預設 5 分鐘、最大 30 分鐘, 並持續受執行階段外掛上限約束
* `最佳化` Python 專案的 project.json 支援透過 "entryMode": "module" 與正規化點分 main 名稱宣告模組入口, 以標準 Python 模組語意執行並支援套件內相對匯入
* `最佳化` 將 Python 專案 workspace 上限提高至壓縮 64 MiB、8192 個檔案項目及解壓 128 MiB, 並於分發前依 snapshot 實際三維需求比對執行環境 Provider 能力, 支援隨專案攜帶純 Python 相依套件
* `最佳化` Python 專案的 project.json 支援以內嵌文字或明確的專案內相對檔案宣告有限 stdin 快照, 讓 sys.stdin.read() 無需互動提示即可讀取預先提供的輸入
* `最佳化` 外掛化 ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization/OpenCC/Pinyin/Pinyin4j/MediaInfo 相關功能
* `最佳化` 檔案管理器將媒體詳細資訊查詢整合為單次 MediaInfo 快照, 減少 Binder 呼叫及等待時間
* `最佳化` 檔案外部檢視及媒體播放降級僅授予 URI 讀取權限
* `最佳化` 統一檔案管理器預設點擊, 主要按鈕, 媒體詳細資訊播放和 overflow 選單外掛動作, 透過明確主檔案頁介面策略提供確定性的單次降級
* `最佳化` 檔案管理員的壓縮檔入口使用查看與資訊圖示, 瀏覽壓縮檔時移除路徑列右側操作圖示, 解壓縮所選項目前可選擇目標目錄, 外掛程式右鍵選單移除外掛程式名稱前綴
* `最佳化` Explorer Action v7 允許壓縮檔案外掛在最終發布前以唯讀方式重新開啟隱藏的待提交輸出並完成驗證, 同時保留 v4 用戶端的直接提交相容語意
* `最佳化` Explorer Action v12 在每次開啟同層卷前驗證呼叫端 UID, 一般檔案身分, 大小, 修改時間, 符號連結狀態及工作階段生命週期, 並限制名稱長度, 目錄數量和開啟次數
* `最佳化` 原生壓縮檔選取模式僅顯示離開與解壓縮, 整合有界進度與取消; 檔案系統五項操作列的文字在窄螢幕上置中顯示於圖示下方
* `最佳化` 維持檔案管理器既有的緊湊路徑列, 清單與五項選取操作列尺寸, 同時保留無障礙標籤, 按鈕角色, 選取狀態與 TalkBack 播報; 外掛啟動前仍會對 Explorer Action 請求大小進行封閉驗證
* `最佳化` 將 AI 文字與 DEX 編譯器符合性 provider 從生產 `plugins` 目錄移入專用 `test-apps` 建置群組, 保留惡意 Binder 回歸涵蓋並明確獨立生產外掛的發布邊界
* `最佳化` DEX 編譯器實驗性宿主路徑維持預設關閉並要求明確元件, 實施精確元件 UID 與完整簽署者集合鎖定, 單次分派降級, 語意快取來源追蹤, 有界回呼入口, 隔離 Binder 逾時, 結構化 DEX ZIP 驗證, reliable pipe 有界 drain 與 EOF 對端錯誤檢查, 由來源/目標雙摘要保護的可回復單次發布, 非同步終止訊號與發布提交點原子仲裁, 且取消/逾時/Binder death 在提交前勝出時強制回復
* `最佳化` DEX 編譯器 R1.1 維持實驗預設關閉, 允許 runtime.loadJar 的 raw JAR 優先使用一個明確選取且與主程式同簽章的精確元件 provider, 失敗時只降級一次至內建 D8/dx, 取消時不降級, AAR 載入繼續使用內建路徑
* `最佳化` DEX 編譯器 R1 已閉環, 同時維持預設關閉與精確元件明確 opt-in: R1.1 生產載入鏈路 7/7、R1.2 自動化 4/4、R1.3 真實 provider 矩陣 7/7、退出條件 3/3. API 34 production routing 8/8, 主程式 DEX 16 suites/149 tests、wire 4/24、fake-provider 5/25 及外掛 48 tests 全數通過, lint 0 error. Canonical campaign f3c2b1af-be93-41e7-b541-f167f90e5cc1 在 API 24/25 使用 D8 CLI、API 26/28/34/36 在 x86_64 使用 D8Command、API 31 使用 QV arm64 多使用者實體裝置, 七個真實 provider 單元全數 PASS; 較早失敗或主動終止的 campaign 繼續原樣保留. last-waiter 協同取消與更廣泛復原仍是未勾選的 R2 工作
* `最佳化` DEX 編譯器實驗性符合性測試擴展至真實 Android Binder 傳輸與成品儲存, 涵蓋精確簽署元件, 惡意回呼/輸出/逾時情境, 目標 API 感知的 DEX 驗證及 DexClassLoader 執行
* `最佳化` 預設關閉的純 AI 文字宿主策略支援確定性信任選擇, 精確身分鎖定與重新驗證, 宿主/供應商能力交集, 歧義候選拒絕, 外掛管理憑證隔離, 不含敏感內容的安全診斷, 取消/終止工作階段競爭的確定性所有權, 請求繫結的工具及 descriptor JSON 驗證, streaming 彙總長度/SHA-256/配額驗證, 解碼後 JSON 重複鍵拒絕, 含敏感資料的模型 `toString` 摘要化及能力集合的域分離快取鍵
* `最佳化` 已接通的 AI Provider V2 宿主路徑將精確元件 PackageManager 探索與 Binder metadata 協商, 分頁 `listTargets` 協調, 每次分派及回呼身分複查, 單次消耗目錄 token, 不含憑證的不可變目標計畫, 有界 session/PFD 傳輸, 串流 credit, process 熔斷, 唯一終態所有者及冪等清理統一用於明確 `ai.*` 外掛路由
* `最佳化` Ace WebView 執行階段/JavaScript bridge/內建語言服務/靜態資源完成外掛化, 降低宿主 APK 耦合並支援獨立安裝及更新
* `最佳化` 專案離線文件完成外掛化, 降低宿主 APK 體積並支援獨立安裝及更新
* `最佳化` 離線專案文件首次載入的檔案清單解析及完整 SHA-256 驗證移至背景執行緒, 避免阻塞介面
* `最佳化` APK 建置所需的 AXML/ARSC/解析/簽章/金鑰儲存區及密碼學工具鏈全部遷入 APK Builder Template 外掛, 宿主改用輕量 APK 資訊檢查
* `最佳化` APK 建置器外掛探索會在任何 Binder 連線前驗證已安裝憑證的 SHA-256, 以套件和實測指紋提示並阻擋不受信任的簽章, 官方簽章路徑維持不變
* `最佳化` 打包應用統一由 APK Builder 外掛程式在裝置內建置, 移除開發者選項中的實驗性遠端建置開關及外掛程式側建置標記
* `最佳化` 將約 1.3 MiB 的完整 AutoJs6 自動完成索引移出 Ace 首次畫面的同步路徑, 在第一個可見程式碼畫面後的閒置時段載入並無縫取代基礎索引; 五個 Android 9-15 環境均恢復 400 個全域項目與 80 個模組
* `最佳化` 外掛中心在啟動前檢查 OCR 外掛服務類別, 啟用異常即時回報, 交握等待上限為 5 秒並取消已放棄的繫結
* `最佳化` 程式碼編輯器引擎選擇尊重使用者明確設定, Ace 無法使用時提供前往外掛中心的入口
* `最佳化` 程式碼編輯器 "執行" 按鈕點選後變換為 "停止" 按鈕, 可便捷停止腳本
* `最佳化` 調整程式碼編輯器操作列及選單層級, 將日誌設為常駐操作按鈕, 並重新整理檔案編碼與唯讀模式入口
* `最佳化` 程式碼編輯器完善主題/字型/工具列/唯讀模式/折疊/選取選單等互動體驗
* `最佳化` 程式碼編輯器完善檔案編碼偵測及儲存安全性, 編碼轉換僅在使用者儲存檔案時寫入磁碟
* `最佳化` 進一步壓縮程式碼編輯器資訊列高度及水平留白, 為程式碼區域釋放更多空間
* `最佳化` 底部日誌面板支援在獨立日誌頁面點選堆疊幀跳轉至原始碼
* `最佳化` 專案文件來源切換支援即時生效
* `最佳化` 檔案管理器僅在點選 Manifest 按鈕後於背景解碼及格式化完整的二進位 AndroidManifest.xml, 並最佳化大文字捲動及雙指縮放效能
* `最佳化` 檔案管理器 APK 詳細資訊僅在 Manifest 明確指定時顯示最高 SDK, 並將安裝項目改為異常且僅在存在異常時顯示
* `最佳化` 檔案管理器 Markdown 預覽支援經安全清理的 GitHub 常用原始 HTML 及 HTTPS 遠端圖片, 並改善窄螢幕表格捲動與舊式隱藏註解相容性
* `最佳化` 外掛中心完善 "排序" 及 "篩選" 對話框的樣式及功能
* `最佳化` 外掛中心預設顯示常用外掛變體並提供獨立頁面檢視其他變體
* `最佳化` 外掛中心完善多語言更新日誌/語言快取/官方認證/索引載入等功能
* `最佳化` 外掛中心優先呈現本機輕量快照與快取索引, 再平行重新整理外掛中繼資料及線上索引; 異常外掛保持停用, 嘗試啟用時會顯示實際異常與復原提示
* `最佳化` 專案字串資源完成多語言翻譯同步/檔案分類/名稱排序及 ASCII 標點規範化
* `最佳化` 限制 APK 語言資源僅保留專案支援的 10 種設定, 降低安裝套件體積
* `最佳化` 建置系統支援動態匹配 Kotlin/KSP/AGP/R8 相容版本並降低 Gradle 啟動記憶體壓力
* `最佳化` 消除 Java 可空回傳值, 冗餘運算式及 Android 已棄用 API 引發的 Kotlin 編譯警告, 保持舊版 Kotlin 相容性
* `最佳化` 將尚未發布的官方 AI 外掛定名為 3-Stone AI, 同時以中性 `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER`, 新 Binder 描述元, AP schema 域及重新命名的符合性測試夾具完全取代跨處理程序合約; 不保留識別別名
* `最佳化` 直接實作 AI Provider V2 分頁目標探索: 每個目標帶有穩定 target/provider/model 識別, locality, 設定與可用狀態, capabilities, limits, controls 及允許的 HTTPS origins; 支援 REMOTE/HYBRID 執行與外掛自管憑證, 憑證位元組不經 Binder, 且不保留被取代的 wire 別名
* `最佳化` 檔案管理器目錄重新整理採用最小差量更新, 未變化的列不再重新繫結, 原地重新整理保持目前捲動位置
* `最佳化` 重構檔案管理器的清單配置, 項目呈現, 選取工具列, 封存工作流程, 輸入能力, 輸出保留及復原元件, 縮減過大的檢視與工作階段類別, 並使導覽, 安全邊界及操作原則可獨立測試
* `最佳化` TypeScript 編譯器繫結與啟動改為在有界的應用程式啟動預熱中完成, 不再占用首次使用者編譯; revision 1 專案工作階段會在進入編輯器或實際編譯後保留一個活動繫結, 啟動閒置到期、記憶體壓力、失敗與應用程式結束時均安全回退至冷編譯
* `最佳化` Scrapers 工具恢復為 9 個獨立版本工作與 5 個宿主專屬工作, 並增加 1 個跨儲存庫同步工作; AutoJs6-Gradle-Platform-Versions 集中匯出經版本對應驗證的 Android Studio 穩定版 JSON 中繼資料
* `最佳化` Gradle 9 Wrapper 版本升級 9.6.1 -> 9.7.1
* `最佳化` Gradle 平台相容性資料及其更新工作已移至 AutoJs6-Gradle-Platform-Versions; AutoJs6 現在直接使用 Maven Local 中的 1.5.0 版本, 不再保留本機資料覆寫、重複擷取器及原有 Puppeteer 相依性
* `最佳化` HTML 與 Markdown 預覽改由專用 Previewer 外掛提供; 主應用程式保留入口, 並在外掛未安裝, 未啟用或不可用時提供安裝與啟用引導
* `最佳化` 所有 Pinyin 呼叫統一由對應外掛提供, 並移除未使用的內建詞典與轉換實作
* `最佳化` 以 root 或安全設定方式啟停無障礙服務時不再寫入系統的 `accessibility_enabled` 值 (Android 7.0 起由系統根據已繫結的服務自行維護), 避免關閉本服務時在其它無障礙服務仍啟用的情況下殘留過期的 0
* `最佳化` 浮動按鈕及其對話方塊, 佈局分析器等懸浮視窗的文字語言與夜間模式跟隨 AutoJs6 自身的設定而非系統設定
* `最佳化` 浮動按鈕 "更多" 選單無需等待前景應用程式查詢即可顯示, 資訊在背景載入並按需更新對應副標題; 套件名稱與 Activity 共用一次查詢, 關閉選單時取消載入並捨棄過期結果
* `最佳化` `auto.registerEvent` 回呼收到的事件物件的 `source` 改為首次存取時解析 (原先每個事件都急切遍歷父鏈求深度與索引), 降低未讀取 `source` 的回呼的開銷; API 33 以下在回呼返回後未讀取的 `source` 為 null
* `最佳化` 無障礙視窗根節點的取得邏輯合一 (`AccessibilityBridge` 轉為 Kotlin, 三處重複邏輯由 `WindowRootPolicy` / `WindowRootProvider` 統一實作, 版面分析器同樣經由此處取視窗): 設定了視窗過濾器時 `auto.root` 在首個通過過濾的視窗沒有根節點時繼續查看後續視窗 (原先直接回傳 `null`); `auto.windowRoots` 與節點動作不再可能遇到 `null` 根節點; `auto.rootInActiveWindow` 不受視窗過濾器影響這一差異已在原始碼中明確
* `最佳化` 無障礙節點的回收統一由 `NodeLifecycle` / `NodeRecyclePolicy` 決定 (Android 13 起節點無需回收, 不再逐個呼叫空操作的 `recycle()`; 此前散佈在搜尋演算法, 捲動動作, 服務快取根節點與配置器中的內聯回收全部經由此處, 回收失敗只記錄日誌, 不再中斷查找)
* `最佳化` 無障礙截圖 (`automator.captureScreen()`) 抽出為 `A11yScreenshotter`: 截圖後的硬體緩衝現在總會被關閉 (原先只回收了包裝點陣圖); 系統限頻錯誤的重試改為至多 60 次 (3 s), 之後與其它失敗一樣以 `null` 兌現 (原先系統持續限頻時 promise 永遠不會兌現)
* `最佳化` 選擇器過濾器, 搜尋演算法 (DFS / BFS) 與羅盤導航 (`compass`) 改為面向不依賴 Android 的 `UiNode` 介面 (`UiObject` 實作它), 三者可在 JVM 單元測試中以記憶體節點樹覆蓋; 指令碼可見的 API 與行為不變
* `最佳化` 選擇器屬性改由宣告式註冊表 `SelectorRegistry` 統一描述: `UiSelector` 的 builder 方法據此產生, 集合選擇器 (如 `pickup({...})`) 依註冊表解析而非反射, 全域選擇器函式依註冊表與明確白名單匯出 (`toStringReadable` 與 `internal` 成員的混淆名不再暴露為全域函式); 全域函式與集合選擇器的數值參數統一依四捨五入轉換為整數 (集合選擇器此前為截斷)
* `最佳化` 選擇器過濾器去重: 字串屬性 (text / desc / content / id / className / packageName 及其變體) 統一為 `StringPropertyFilter`, 邊界度量 (left / boundsLeft / minLeft / maxLeft 等) 統一為 `MetricsFilter`, 整數屬性統一為 `IntFilter`; 螢幕相關過濾器 (bounds / 度量 / screenCenter / screenCoverage) 經可注入的螢幕尺寸求值並納入 JVM 單元測試; 選擇器求值走訪過濾器清單的快照, 多執行緒共享同一選擇器時安全; 指令碼可見的 API 與行為不變
* `最佳化` 選擇器查詢 (`findOne(timeout)` / `untilFind` / `untilFindOne` / `exists` / `find(max)` 及已棄用的 `findOne()` / `waitFor()`) 統一按輪詢計畫 (`PollPolicy`: 逾時, 間隔, 次數, 退避) 執行, 預設行為不變 (間隔 50 毫秒, 逾時 0 或負數表示一直等待); 無上限等待的方法在 KDoc 中標註 "可能永久阻塞"; 設定 `findOnUiThread` 旗標時投遞到 UI 執行緒的查詢以查詢逾時為等待上限, 逾時擲出可讀錯誤 (`findOne(timeout)`), 無上限的查詢 (`untilFind` 等) 改為可被停止指令碼中斷的等待
* `最佳化` `UiObject` 拆分: `pickup` / `detect` 的結果型別別名表, 拾取器與按名稱呼叫節點方法的攔截器遷入 `core/automator/pick` (`PickResultType` / `Picker` / `Detector` / `Invoker`), `summary()` / `toString()` / `isSimilar()` 遷入 `UiObjectSummary`; 新增 `UiObject.fingerprint()` (邊界, 文字, 描述, 類別名稱, 子節點數與關鍵布林屬性的雜湊, 不走訪兄弟與子節點); 經 `parent()` 到達的節點的 `indexInParent()` 改為首次使用時計算; 版面配置分析器的程式碼產生器 (`CodeGenerator` / `ReadOnlyUiObject`) 轉為 Kotlin, 後者改為直接實作 `UiNode` 的快照檢視; 指令碼可見的 API 與行為不變
* `最佳化` 跨執行環境重用 (Roadmap P5.4): Python Host 的選擇器能力 (`selector.snapshot` / `selector.find` / `selector.click` / `selector.set_text`) 與 Node Bridge 的 `accessibility` 模組 (`findOne` / `findAll` / `click` / `longClick` / `setText` / `scrollForward` / `scrollBackward` / `findByText` / `clickText`) 改為使用指令碼執行環境的無障礙核心 (`UiNode` 走訪, 選擇器篩選器, 視窗根節點 provider, 節點生命週期), 兩者的 JSON 線協定逐欄位不變; Node Bridge 的查找在 Android 13 以下不再洩漏沿途走訪的節點; Python Host 的無障礙截圖改為經共用的 `A11yScreenshotter` 完成單次嘗試 (保留其有界重試與 4 秒期限)
* `最佳化` 每個腳本執行階段擁有獨立的控制台 (自身的日誌條目與浮動視窗, 日誌同時轉發至全域控制台), 浮動視窗僅顯示目前腳本的輸出 (與 Auto.js 4 一致), 同名腳本重新執行時取代上次執行遺留的浮動視窗; 日誌條目改為儲存原始內容與時間戳記, 時間前綴由各檢視按需渲染 (浮動視窗預設不顯示, 日誌頁可在設定中開啟); Node bridge `console.tail` 條目新增 `time` 欄位 (毫秒時間戳記), `text` 保持帶時間前綴
* `最佳化` 螢幕 OCR 範例申請 Android 擷取授權並辨識真實圖片控制代碼; OCR 或條碼插件不可用時傳回可讀 unavailable 錯誤, 辨識失敗仍按錯誤處理
* `最佳化` Node 腳本支援協商式非同步啟動, 長駐執行釋放 Binder 執行緒, 終態回呼完成後回傳工作區, 相容舊同步宿主
* `最佳化` Node bridge `accessibility.dump` 的 JSON 節點在七個描述符欄位之外攜帶 `scrollable`, `checkable`, `checked`, `editable`, `focused`, `selected` 與 `longClickable` 狀態標記; MCP Server 工作階段授權加入 `keys.notifications` / `quickSettings` / `powerDialog` / `lockScreen` 全域動作以支援外掛的 `ui_press_key` 工具, 無障礙服務未執行時 `keys.*` 呼叫現在與 `accessibility` 模組一樣以 `unavailable` 類別回應; 起止點相同的 `accessibility.swipe` (零長度) 依 `durationMs` 的按壓派發, MCP 的 `ui_click` / `ui_long_click` 座標形式依賴於此
* `最佳化` 宿主與 runtime-kit 原生函式庫支援 16 KB 對齊, 重編 jackpal JNI 函式庫並使用 NDK r28.2 libc++, 附帶來源記錄與共用建置校驗
* `最佳化` APK 打包器在進度與結果中提示不相容的原生函式庫, 在本機建置輸入中繼資料中記錄對齊資訊, 保持壓縮打包
* `最佳化` 外掛中心的版本摘要同樣將 `2026-09-11T14:30:00+08:00` 這類 ISO 8601 日期時間字串正規化為 `yyyy-MM-dd`, 並保留字串自身時區偏移下的日曆日期
* `最佳化` 多點找色 (`images.findPointByColors`, `images.findPointsByColors`) 使用向量化的 OpenCV 遮罩預先篩選候選點, 在密集圖片上快數十倍且結果完全一致
* `最佳化` 顏色偵測器提供 `distance` 方法, 由 `colors.isSimilar`, `images.detectColor` 與多點找色共用; `hs` 演算法使用環形色相距離, 未知的演算法名稱會擲回明確的錯誤
* `最佳化` 螢幕擷取器狀態讀取不再加鎖, `images.releaseScreenCapturer` 釋放期間不再阻塞並行的圖片呼叫
* `最佳化` 範本比對的金字塔層級改用區域插值, 遮罩比對沿用正規化相關係數, 較小的 `max` 也會精化多個候選點, 避免最佳比對在粗層級被丟棄
* `最佳化` 版面層次視窗: 行內摘要顯示 id / text / desc / clickable 等屬性, 選取節點及其全部父層在樹中高亮並在螢幕上勾出邊框, 樹支援橫向捲動, 選單新增 "選擇父控制項" 逐級上溯, 首次進入提示可用音量減鍵退出
* `最佳化` 特徵比對僅保留 RANSAC 內點並驗證投影四邊形, 比對器與描述子類型不相容時自動修正而非擲回例外, `scale` 允許大於 1 以放大小目標, ORB 預設最多偵測 20000 個關鍵點, 避免紋理豐富的場景排擠目標區域的關鍵點
* `最佳化` Node bridge 新增唯讀的 app.listDocs / app.readDoc (列舉與讀取已安裝 AutoJs6-Plugin-Offline-Docs 經校驗的離線文件資產, 路徑限制在文件根內, 單次讀取上限 1 MiB, 無可用外掛時回傳 available: false), 供 MCP Server 外掛提供 autojs6://docs/ 資源
* `最佳化` 版本歷史與回收站的保留策略 (天數, 版本數, 單檔案容量, 總容量, 納管檔案大小上限) 統一為 0 或負數表示不限制, 並由共用的策略物件實現
* `最佳化` 版本歷史跳過與最新已存版本內容完全相同的快照, 避免重複儲存
* `最佳化` 移入回收站前檢查容量上限, 待移入條目超過上限時提示繼續移入, 徹底刪除或取消; 移入完成後立即清理更早的條目, 且容量上限始終保留最新條目
* `最佳化` 週期清理任務套用完整的保留策略 (版本歷史的天數, 版本數, 單檔案與總容量; 回收站的天數與容量), 並移除不再擁有版本的檔案條目; 緊急草稿的保留策略同樣以 0 表示不限制
* `最佳化` VSCode 擴充功能連線增加應用層心跳 (ping/pong) 與幀長度上限校驗, 及時釋放失效連線並避免失步時的超大記憶體配置 _[`issue #333`](http://issues.autojs6.com/333)_ _[`issue #523`](http://issues.autojs6.com/523)_
* `最佳化` VSCode 擴充功能儲存檔案時支援按相對目錄結構儲存到工作目錄 _[`vscode-ext issue #37`](http://vscext-project.autojs6.com/issues/37)_
* `最佳化` 檔案管理器以複製或移動覆蓋已有檔案前, 先把目標檔案的目前內容記錄為一條 OVERWRITE_PRE 版本; 覆蓋目錄時為其中已納管的檔案留快照
* `最佳化` 回收站列表每個項目顯示到期倒數 (基於回收站保留天數, 不限制時不顯示), 24 小時內到期的項目以醒目顏色高亮
* `最佳化` 回收站項目因資料檔案缺失或損壞而無法預覽或還原時給出本地化提示, 不再顯示原始例外訊息
* `最佳化` 回收站項目還原到與原路徑不同的位置時, 該檔案 (或目錄下所有檔案) 的版本歷史跟隨到新路徑; 移入回收站時記錄檔案當時關聯的版本歷史項目
* `最佳化` 回收站目錄封存損壞時, 還原不再整體中止: 可讀的項目照常還原, 損壞項目記錄並略過 (寫了一半的檔案會刪除), 結束後提示還原與失敗的項目數, 且該項目保留在回收站
* `最佳化` 版本歷史檔案列表顯示與檔案管理器一致的檔案類型圖示; 版本歷史與回收站列表改為按差異局部重新整理, 統計 / 倒數 / 選取狀態變化時不再整表重繪
* `最佳化` 掃描二維碼連線: 相機改用連續對焦並啟用測光, 解碼啟用 TRY_HARDER, 更易對準螢幕上的二維碼
* `最佳化` 客戶端模式位址對話框的 "掃描二維碼" 由輸入框末尾圖示改為標題列 "更多" 選單項; 掃碼頁面移除閃光燈按鈕 (二維碼顯示於螢幕, 無需補光)
* `最佳化` 客戶端模式與伺服端模式管理器的狀態區與控制區支援與無障礙服務管理器相同的逐級展開與收起 (狀態與控制各 3 級, 所選等級會被記住, 全部收起時狀態標題附帶連線狀態, 主要動作移入控制區標題列); 三個管理器的 "複製" 按鈕移入標題列 "更多" 選單作為 "複製資訊", 無障礙服務管理器的 "系統" 按鈕同樣移入該選單作為 "系統設定"
* `最佳化` 安裝套件體積縮減: 移除停用的 HTML 轉 Markdown 鏈路與未使用的相依套件, JUnit 不再隨 CommonMark 與 Spongy Castle 進入生產 DEX, Rhino 執行階段不再打包 JLine 終端機程式庫
* `最佳化` 檢查更新改為直接呼叫 GitHub REST API (OkHttp + Gson) 取得最新版本與安裝套件清單, 更新說明的 release 回退內容改為本機渲染, 不再請求 GitHub 的 Markdown 渲染介面
* `最佳化` 全域日誌寫入檔案 (console.setGlobalLogConfig / resetGlobalLogConfig 及全域類別 LogConfigurator / Level / LogManager) 改用內建滾動檔案日誌器取代 Log4j; 文件化選項與 PatternLayout 子集 (%d / %p / %c / %C / %m / %n / %t / %r 及寬度修飾) 保持不變, 其餘 Log4j API 不再提供
* `最佳化` Rhino 引擎在發現 Java 方法時跳過參數型別在目前系統上不存在的方法 (如 Android 7.x 上的 AppCompatTextView.setTextClassifier), 而非令整個類別的反射失敗; UI 模式 text 元素在 Android 7.x 上改用現行 AppCompatTextView, 不再需要舊版 appcompat 1.0.2 程式庫
* `最佳化` 應用程式僅內建簡體中文與英文兩份版本歷史; 其餘語言線上取得並快取, 離線時依序顯示目前語言的快取副本與內建英文版本
* `最佳化` 資源目錄中的 PNG 點陣圖無失真轉換為 WebP (啟動器圖示除外), 並移除若干未使用的資源, 縮減安裝檔大小
* `最佳化` 減少外掛一致性測試應用程式及宿主測試的 Manifest, Java 和 Kotlin 編譯警告, 保持原生程式庫封裝行為及 Android 版本相容性
* `最佳化` accessibility.dump 的 compact 格式與 nodeRef 節點參照, 支援節點重新定位及失效參照拒絕
* `最佳化` accessibility.readScreenText 方法, 透過擷取畫面與已授權 OCR 外掛回傳有上限的螢幕文字及位置
* `最佳化` Three Stove Agent 能力代理允許開啟通知欄和快捷設定, 其餘未授權按鍵動作保持拒絕
* `最佳化` ai.agent 任務物件的 respond 方法接受 1 到 8 條非空字串組成的陣列, 用於回應 Three Stove Agent 計劃模式的計劃審閱請求 (input 事件 kind 為 plan), 文字與布林值回應保持不變
* `最佳化` Three Stove Agent 模型代理在 Provider 自身拒絕或中斷請求時 (無效請求, 協定違規, 描述符或配額問題等) 以封閉的 PROVIDER_* 原因碼報告失敗階段, 不再一律摺疊為不帶原因的 PROVIDER_FAILED; 原因只來自 AiErrorCode, 不含 Provider 文字
* `最佳化` Three Stove Agent 模型代理在接受一批原生工具結果後按請求的 timeoutMs 重新設定截止時間, 每個續輪都擁有完整的逾時, 整輪由外掛的任務預算與續輪上限約束; 未提交結果的等待仍按原截止時間逾時. 此前多輪原生任務共用首個請求的截止時間, 超過 5 分鐘即以 MODEL_TIMEOUT 終止
* `最佳化` Three Stove Agent 統一由外掛中心啟用和停用, 移除首頁側邊欄開關, 指令碼和外掛介面按需自動連線; 官方外掛安裝後預設啟用, 保留使用者明確儲存的停用狀態
* `最佳化` 影片播放器 3-Ember Player 更名為 3-Amber Player, 更新外掛安裝引導, M3U 播放優先順序及透明圖示識別, 使用新的獨立應用程式套件名稱
* `最佳化` 安裝器能力遷出到 3-Setup Installer 外掛: 檔案管理器, 外掛中心, 打包完成與自更新的安裝入口在外掛可用時交給外掛的安裝對話框, 否則將 APK 交給系統安裝器並提示安裝外掛; 宿主不再註冊 "用 AutoJs6 開啟 APK" 的系統入口, apks / xapk / apkm / apkz 分包容器需要外掛才能安裝
* `最佳化` 安裝歷史可區分 AutoJs6 入口與 installer 指令碼發起的安裝, 指令碼 API 用法不變 (需要支援安裝歷史的 3-Setup Installer 外掛)
* `最佳化` installer 及主程式安裝入口可使用外掛設定的來源/套件名稱前綴設定檔, 明確指定的選項優先, installer/installReason/packageSource 的 null 可清除繼承值; 刪除來源須安裝成功, 並保留同批明確保留或未成功項目共用的檔案 (需要支援來源設定檔的 3-Setup Installer 外掛)
* `最佳化` installer 安裝選項接受 grantAllRequestedPermissions, requestUpdateOwnership, dexopt, installReason 及 packageSource, 依外掛能力啟用; 省略保留原行為, 明確指定 false/none 仍需對應支援
* `最佳化` Three Stove Agent 模型代理在一次模型呼叫逾時且同步 Binder 交易尚未返回時會熔斷該 Provider 元件, 此前熔斷持續到 AutoJs6 程序重啟, 後續任務立即以 RATE_LIMITED: FUSED 失敗; 現在該交易實際返回並結清後自動解除熔斷, 後續任務無需重啟即可再次准入
* `最佳化` 佈局層次分析頁面移除左側的層級彩色直線 (僅保留縮排), 展開 / 收起標識移到控制項文字左側: 可展開為右箭頭, 已展開為下箭頭, 無子孫控制項為小空心圓, 三類圖示統一著色並適當加深
* `最佳化` 版面分析的節點資訊對話方塊以主題色背景標示多選項, 切換多選時保持清單項目高度不變; 按鈕調整為 "設定/返回/複製", 複製方式支援 "總是詢問/複製物件選擇器/複製鏈式選擇器" 並跨工作階段記憶, 預設為 "總是詢問"
* `最佳化` 版面分析搜尋列的查詢模式/篩選/正規表示式/導覽按鈕增大顯示尺寸, 正規表示式開關使用簡潔的 ".*" 字樣
* `最佳化` 郵件帳戶設定與外掛中心支援 3-Stamp Mail 的新名稱和套件名稱
* `最佳化` 外掛中心優先使用獨立外掛的專用透明黑白圖示, 跟隨宿主明暗主題並相容舊版外掛圖示
* `最佳化` 官方外掛庫在安裝前後使用同源圖示, 統一大小和樣式並支援中性灰階圖案
* `最佳化` EPUB 閱讀功能對接 3-Folio EPUB 的新安裝身分, 保留現有 epub API 和檔案管理員呼叫方式
* `最佳化` 外掛程式中心安裝精靈支援 3-Maple Image, 3-Stack Archive 與 3-Hanzi CC 的新安裝身分, 圖片檢視與編輯工具合併為一個推薦項目
* `最佳化` 外掛程式中心與安裝精靈支援獨立的亮暗圓形圖示底色, Three 系列採用固定黑白灰配色, 安裝前後保持目錄圖稿和底色一致
* `最佳化` 預設顏色庫的亮色與暗色視窗背景合併為 "背景色" 一項, 可選擇亮色, 暗色, 自動跟隨或自動反色, 升級前的選擇保持不變
* `最佳化` 主題色摘要顯示顏色所在的顏色庫與顏色名稱 (智慧顏色庫顯示目前組), "定位當前主題色" 可定位自建顏色庫中的顏色與目前智慧顏色庫; 傳統佈局在智慧顏色庫啟用時顯示其名稱, 在傳統佈局中選色會停用智慧顏色庫
* `最佳化` 顏色庫清單的搜尋範圍擴展到自建顏色庫中的顏色與智慧顏色庫的組 (組按名稱或其任一顏色比對), 結果末尾標註所屬顏色庫, 點擊即套用或啟用; 搜尋說明同步更新
* `最佳化` Ace 編輯器參數提示支援重載方法, 顯示重載序號並可點按切換, 目前參數的文件優先顯示
* `最佳化` 編輯器選單 "跳轉" 增加 "跳至上一個游標位置" (原為 TAB 鍵長按功能), 可記錄到動作收藏
* `最佳化` Ace 編輯器參數提示的重載切換支援硬體鍵盤 Alt+Up / Alt+Down, 多功能鍵盤上下鍵 (參數提示顯示多個重載時, 可在設定中關閉) 及提示視窗右上角的上下按鈕
* `最佳化` Ace 編輯器參數提示在點按編輯區後恢復顯示 (捲動後不恢復), ESC 鍵同時隱藏參數提示與符號文件
* `最佳化` Ace 編輯器參數提示在 LSP 關閉或語意服務不可用時同樣支援重載方法: 靜態索引收錄 146 個宣告重載 (如 app.startActivity), 內建 ECMAScript 簽章增加 splice / replace / JSON.stringify / Array.from 等重載, Python / Lua 檔案的參數提示保留全部簽章
* `最佳化` Ace 編輯器懸停文件框支援觸控捲動與選取文字, 智慧提示在 Python / Lua 檔案中等待非同步參數提示而不再誤彈補全
* `依賴` 模組化 Android AXML
* `依賴` 模組化 Android ARSC Editor
* `依賴` 模組化 APK Signer
* `依賴` 模組化 APK Verifier
* `依賴` 模組化 ARSCLib 版本 1.3.8
* `依賴` 模組化 Image Quantization 版本 2.17.0
* `依賴` 模組化 OpenCV 版本 4.8.0
* `依賴` 附加 juniversalchardet 版本 2.0.4
* `依賴` 附加 AndroidX AsyncLayoutInflater 版本 1.1.0
* `依賴` 移除 Android Annotations 版本 4.8.0
* `依賴` 移除 APK Parser 本機模組
* `依賴` 移除 Glide KSP 版本 5.0.5
* `依賴` 移除 ICU4J 版本 77.1
* `依賴` 移除 Jaredrummler Android Device Names 版本 2.1.1
* `依賴` 移除 JCIP Annotations 版本 1.0
* `依賴` 移除 Junit Jupiter 版本 5.13.4
* `依賴` 移除 Retrofit2 Converter Gson 版本 3.0.0
* `依賴` 移除 Retrofit2 Kotlin Coroutines Adapter 版本 0.9.2
* `依賴` 移除 Tiny Sign 版本 0.9
* `依賴` 移除 app 模組重複宣告的 AndroidX Room KTX 版本 2.8.1
* `依賴` 移除 AndroidX MultiDex 版本 2.0.1
* `依賴` 移除 MLKit Barcode Scanning 版本 17.3.0
* `依賴` 移除 MLKit Text Recognition Chinese 版本 16.0.1
* `依賴` 移除 OpenCC 版本 1.2.2
* `依賴` 移除 Pinyin4j 版本 2.5.1
* `依賴` 移除 Jieba Analysis 版本 1.0.3-SNAPSHOT (modified)
* `依賴` 移除 Rapid OCR 本機程式庫
* `依賴` 移除 Flexmark Java HTML to Markdown 版本 0.64.8
* `依賴` 移除 JavaMail for Android 本機程式庫
* `依賴` 移除 GitHub API 版本 1.306
* `依賴` 移除 Jackson DataBind 版本 2.13.4.2
* `依賴` 移除 Commons IO 版本 2.8.0
* `依賴` 移除 Apache Commons Lang3 版本 3.18.0
* `依賴` 移除 Spongy Castle Prov 版本 1.58.0.0
* `依賴` 移除 Android dx 版本 1.14
* `依賴` 移除 Log4j 版本 1.2.17
* `依賴` 移除 Android Logging Log4j 版本 1.0.3
* `依賴` 移除 AndroidX AppCompat 1.0.2 本機程式庫
* `依賴` 附加 ZXing Android Embedded 版本 4.3.0
* `依賴` 附加 AndroidX Test Ext JUnit 版本 1.3.0
* `依賴` 附加 Android Hidden API Bypass 版本 6.1
* `依賴` 模組化 Package Archive Parser
* `依賴` 模組化 Installer API

# v6.7.0

###### 2026/03/14

* `新增` 外掛中心功能, 支援外掛的安裝/解除安裝/更新等操作 (入口: 主頁抽屜按鈕/主頁標籤頁)
* `新增` 版本歷史功能, 支援檢視/恢復可編輯檔案的歷史版本 (入口: 主頁抽屜按鈕/檔案管理器選單/程式碼編輯器選單)
* `新增` 回收站功能, 支援檢視/恢復已刪除的檔案/資料夾 (入口: 主頁抽屜按鈕)
* `新增` Paddle OCR (PP-OCRv5) 外掛, 用於光學字元識別
* `新增` cvt 模組, 用於資料單位轉換 (參閱 專案文件 > [單位轉換](https://docs.autojs6.com/#/cvt))
* `新增` fmt 模組, 用於資料格式化 (參閱 專案文件 > [格式化](https://docs.autojs6.com/#/fmt))
* `新增` zip 模組, 用於檔案壓縮與解壓縮操作 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (參閱 專案文件 > [Zip](https://docs.autojs6.com/#/zip))
* `新增` mediainfo 模組, 用於檢視媒體檔案的詳細資訊 (參閱 專案文件 > [媒體資訊](https://docs.autojs6.com/#/mediainfo))
* `新增` cvt.bytes 方法, 用於位元組資料單位轉換 (參閱 專案文件 > [單位轉換](https://docs.autojs6.com/#/cvt))
* `新增` fmt.bytes 方法, 用於位元組資料格式化 (參閱 專案文件 > [格式化](https://docs.autojs6.com/#/fmt))
* `新增` s13n.bytes 方法, 用於標準化位元組資料 (參閱 專案文件 > [標準化](https://docs.autojs6.com/#/s13n))
* `新增` app.isDualInstalled 方法, 用於檢測雙開應用是否已安裝 (需要 Shizuku 或 Root 許可權) _[`issue #450`](http://issues.autojs6.com/450)_
* `新增` device.getSharedDeviceId 方法, 用於跨應用獲取統一共享裝置 ID _[`issue #455`](http://issues.autojs6.com/455)_
* `新增` device.setPointerLocation 等 Toggleable 系列方法, 用於設定或獲取指標位置系統設定項 _[`issue #381`](http://issues.autojs6.com/381)_
* `新增` dialogs.build 方法支援 textAllCaps/(positive/negative/neutral)TextAllCaps 選項引數, 用於控制按鈕文字是否全部大寫
* `新增` images.loadAsync 方法, 用於非同步獲取網路影像資源 _[`issue #327`](http://issues.autojs6.com/327)_
* `新增` ui.getNavigationBarHeight 方法/navigationBarHeight 屬性 (getter), 用於獲取導航欄高度 _[`issue #456`](http://issues.autojs6.com/456)_
* `新增` ui.getVisible(Status/Navigation)BarHeight 方法/visible(Status/Navigation)BarHeight 屬性 (getter), 用於快捷獲取狀態列或導航欄可見高度 _[`issue #456`](http://issues.autojs6.com/456)_
* `新增` http.put/del(ete)/head 方法, 用於使用不同的 HTTP 請求方法傳送網路請求
* `新增` http.getAsync/postAsync/requestAsync 等方法, 用於非同步傳送網路請求 _[`issue #423`](http://issues.autojs6.com/423)_
* `新增` http 模組請求相關方法獲取的 body 物件增加 stream/saveToFile/close 等方法 _[`issue #452`](http://issues.autojs6.com/452)_
* `新增` http 模組請求相關方法支援快取控制選項引數 (cacheBody/bodyCacheThresholdBytes)
* `新增` http 模組請求相關方法支援不安全選項引數 (isInsecure/insecure), 用於忽略證書相關異常 _[`issue #417`](http://issues.autojs6.com/417)_
* `新增` http 模組請求相關方法支援 options.client 選項, 用於配置 OkHttpClient.Builder (如 followRedirects 等) _[`issue #454`](http://issues.autojs6.com/454)_
* `新增` auto.state 屬性 (getter) 及 shizuku.state 屬性 (getter), 用於獲取無障礙服務狀態及 Shizuku 服務狀態
* `新增` runtime.(set/is)JavaPrimitiveWrap 方法, 用於設定或獲取 Java 原始型別包裝策略 _[`issue #435`](http://issues.autojs6.com/435)_
* `新增` autojs.(restart/exit) 方法, 用於重啟或退出 AutoJs6 應用, 並支援應用重啟時自動執行其引數指定的指令碼 _[`issue #460`](http://issues.autojs6.com/460)_
* `新增` UiObject#isShifted 方法, 用於檢測控制元件位置變化 _[`issue #469`](http://issues.autojs6.com/469)_
* `新增` UiObject#snapshot 方法, 用於捕獲控制元件當前狀態 _[`issue #469`](http://issues.autojs6.com/469)_
* `新增` engines.myEngine().getStartTime() 方法, 用於獲取指令碼引擎啟動時間戳 _[`issue #449`](http://issues.autojs6.com/449)_
* `新增` crash 全域性方法, 用於測試崩潰報告頁面
* `新增` structuredClone 全域性方法, 用於深複製 JavaScript 物件 (參閱 [MDN](https://developer.mozilla.org/zh-CN/docs/Web/API/Window/structuredClone))
* `新增` 等價系列選擇器 (UiSelector#id/text/...) 及包含系列選擇器 (UiSelector#xxxContains) 支援正則表示式引數
* `新增` 選擇器的正則表示式引數支援使用標誌 (i, m, s, u)
* `新增` 正則表示式支援後瞻斷言語法 _[`issue #464`](http://issues.autojs6.com/464)_
* `新增` 檔案管理器增加 "移動到" 及 "複製到" 選單項, 支援操作中止及進度狀態顯示
* `新增` 程式碼編輯器增加 "多功能鍵盤" - "符號設定" 選單項, 支援符號編輯及按配置級別進行符號的建立/匯入/匯出等
* `新增` 主頁抽屜增加 "指標位置" 工具
* `新增` 主頁抽屜增加 "所有檔案管理許可權" 開關
* `新增` 主頁抽屜增加 "後臺彈出介面" 開關 (針對 [小米/Vivo] 裝置)
* `新增` 設定頁面增加 "Java 原始型別包裝" 設定選項 _[`issue #435`](http://issues.autojs6.com/435)_
* `新增` 設定頁面增加 "定時任務排程引擎" 設定選項, 支援 AlarmManager/WorkManager/JobScheduler _[`issue #457`](http://issues.autojs6.com/457)_ _[`issue #434`](http://issues.autojs6.com/434)_ _[`issue #449`](http://issues.autojs6.com/449)_ _[`issue #388`](http://issues.autojs6.com/388)_ _[`issue #378`](http://issues.autojs6.com/378)_ _[`issue #163`](http://issues.autojs6.com/163)_ _[`issue #53`](http://issues.autojs6.com/53)_ _[`issue #21`](http://issues.autojs6.com/21)_
* `新增` 設定頁面增加 "應用啟動器圖示" 設定選項, 支援自適應圖示/透明背景圖示 _[`issue #405`](http://issues.autojs6.com/405)_
* `新增` 設定頁面增加 "重啟策略" 設定選項, 用於設定主頁抽屜欄重啟按鈕是否使用快速重啟策略
* `新增` 設定頁面增加 "螢幕捕獲許可權申請延遲" 設定選項, 用於避免許可權申請視窗遮擋螢幕
* `新增` 設定頁面啟動器快捷方式增加 "外掛" 選項, 用於透過快捷方式跳轉到外掛中心頁面
* `新增` 打包應用支援開機自啟動並在配置頁面增加開關配置
* `新增` 打包應用支援應用快捷方式 (日誌頁面/設定頁面)
* `新增` 打包應用配置頁面增加撤銷/重做/儲存按鈕
* `新增` 打包應用配置頁面增加配置機制, 支援配置的建立/修改/刪除/匯入/匯出 _[`issue #483`](http://issues.autojs6.com/483)_
* `新增` Scrapers 工具 (run-scrapers.mjs) 用於自動更新 Gradle 構建指令碼結構化資料/README 通用資料/README 模板資料等
* `新增` Gradle Wrapper 切換工具 (gradle-wrapper-switcher), 用於切換本地 Gradle 主版本 (如 Gradle 8.x 與 Gradle 9.x)
* `修復` notice 方法引數數量及型別限制錯誤 _[`issue #462`](http://issues.autojs6.com/462)_
* `修復` ui.R 屬性 (getter) 無法正常使用的問題
* `修復` ui.post 方法回撥引數中產生異常時可能靜默失敗的問題
* `修復` isJavaClass/isJavaPackage 等全域性方法無效的問題
* `修復` timers.keepAlive 方法 timeout 引數功能無效的問題
* `修復` floaty.window/rawWindow 方法無法接受字串引數的問題 _[`issue #467`](http://issues.autojs6.com/467)_
* `修復` floaty.rawWindow 因傳入錯誤的上下文導致部分控制元件顯示異常的問題
* `修復` util.isString 方法無效的問題
* `修復` util.class[Name]/getClass[Name] 可能返回錯誤結果的問題
* `修復` util.java.array 無法正常使用 Boolean 等型別引數的問題
* `修復` threads.disposable() 返回的物件在存取資料時可能被意外裝箱的問題 _[`issue #435`](http://issues.autojs6.com/435)_
* `修復` console.build 方法多次呼叫時, 日誌浮動視窗樣式選項未能正常重置的問題
* `修復` console.build 方法的 (title/content)BackgroundColor 選項導致透明度或著色選項被覆蓋的問題 _[`issue #458`](http://issues.autojs6.com/458)_
* `修復` console/toast 等方法顯示 Java Double 時可能丟失末尾零的問題
* `修復` 無法使用 console/toast 等方法顯示 BigInt 資料型別的問題
* `修復` toast 方法因持有 Toast 例項強引用而導致可能得記憶體洩露問題
* `修復` 部分全域性物件可能丟失 JavaScript 原型屬性及方法的問題
* `修復` 使用 XML 語法將 JavaScript 表示式作為屬性值時, this 物件可能出現指向錯誤的問題
* `修復` dialogs.build 方法 linkify 選項引數功能失效的問題
* `修復` dialogs.build 方法可能破壞物件引數原有資料的問題
* `修復` canvas 元素控制元件 setMaxFps 方法內部幀率計算錯誤
* `修復` images.concat 方法縱向拼接時寬度值計算錯誤
* `修復` images.requestScreenCapture 方法呼叫時使用者取消授權可能導致應用崩潰的問題
* `修復` images.captureScreen 方法獲取的截圖可能存在方向錯誤或黑邊填充的問題 _[`issue #337`](http://issues.autojs6.com/337)_
* `修復` images 部分方法使用區域引數時 1 被誤解析為百分比的問題
* `修復` images 部分相關方法出現異常時 oneShot 標記功能失效的問題 _[`issue #372`](http://issues.autojs6.com/372)_
* `修復` images 部分相關方法可能引發記憶體洩露的問題 _[`issue #372`](http://issues.autojs6.com/372)_
* `修復` images.save 及 ImageWrapper#saveTo 方法傳入路徑不存在時無法正常寫入檔案的問題
* `修復` ImageWrapper#recycle 方法回收影像資源功能失效的問題
* `修復` ocr 部分過載方法可能無法正常使用的問題
* `修復` ocr.detect 方法獲得的結果可能與 ocr.mode 不匹配的問題 _[`issue #468`](http://issues.autojs6.com/468)_
* `修復` auto.registerEvent 註冊的無障礙服務事件會被其他指令碼誤清理的問題 _[`issue #466`](http://issues.autojs6.com/466)_ _[`issue #343`](http://issues.autojs6.com/343#issuecomment-3263953918)_
* `修復` Android 10 UiObject#child 方法可能出現 ArrayIndexOutOfBoundsException 異常的問題 _[`issue #416`](http://issues.autojs6.com/416)_
* `修復` 內建模組方法呼叫可能出現 Rhino 上下文可能不一致的問題
* `修復` 指令碼建立大量浮動視窗後, 指令碼退出時可能出現視窗殘留的問題
* `修復` 執行專案時 project.json 配置引數可能無法正常解析的問題
* `修復` 專案打包時 project.json 的 excludedDirs 配置引數將導致配置檔案解析失敗的問題 _[`issue #428`](http://issues.autojs6.com/428)_
* `修復` Android 7.x 可能無法正常使用打包功能的問題
* `修復` 指令碼專案配置檔案儲存時原始鍵名可能會被修改的問題
* `修復` 指令碼專案配置檔案中構建版本號或構建時間出現較大數字時可能導致應用崩潰的問題
* `修復` 頻繁獲取或重建 ImageReader 時可能因緩衝區暫無可用幀導致應用崩潰的問題
* `修復` 輸入事件觀察器 InputEventObserver 可能導致應用啟動時明顯示卡頓的問題
* `修復` Shizuku 使用者服務程序未能正常結束導致程序堆積的問題 _[`issue #474`](http://issues.autojs6.com/474)_
* `修復` 打包應用無法正常使用 Paddle OCR 與 Rapid OCR 功能的問題
* `修復` 打包應用配置檔案 project.json 中的 key 值可能被意外修改的問題
* `修復` 打包應用配置頁面滑動 "需要的許可權" 列表時可能出現核取方塊圖示閃爍的問題
* `修復` 發行歷史頁面部分系統因字型差別導致統計資料顯示不完整的問題
* `修復` 部分裝置無法正常初始化 MLKit Google OCR 的問題 (試修) _[`issue #8`](http://issues.autojs6.com/8#issuecomment-3117061768)_
* `修復` 部分裝置無法正常觸發檔案管理器功能按鈕點選事件的問題 (試修) _[`issue #465`](http://issues.autojs6.com/465)_
* `修復` 部分裝置程式碼編輯器空行顯示方框字元的問題 (試修)
* `修復` 程式碼編輯器軟鍵盤可能遮擋文字內容的問題
* `修復` 程式碼編輯器在只讀模式下依然可以編輯程式碼內容的問題
* `修復` 程式碼編輯器在只讀模式下點選標題區域及部分選單項導致應用崩潰的問題
* `修復` 程式碼編輯器載入大檔案可能導致應用崩潰的問題 (試修)
* `修復` 程式碼編輯器自動開啟新建檔案時功能按鈕狀態初始化異常
* `修復` 程式碼編輯器編輯檔案時置於後臺可能導致應用崩潰的問題
* `修復` Android 15+ 程式碼編輯器點選 fx 按鈕無法顯示模組函式快捷面板的問題 (試修)
* `修復` ErrorDialogActivity 可能無法正常啟動或短時間自動消失的問題 _[`issue #479`](http://issues.autojs6.com/479)_ _[`issue #471`](http://issues.autojs6.com/471)_ _[`issue #414`](http://issues.autojs6.com/414)_ _[`issue #340`](http://issues.autojs6.com/340#issuecomment-2973485826)_
* `修復` Canvas 建構函式可接受的引數型別錯誤 _[`issue #402`](http://issues.autojs6.com/402)_
* `修復` 崩潰報告頁面複製詳細資訊功能失效的問題
* `修復` Android 16+ 自定義返回邏輯失效導致返回功能異常的問題
* `修復` 檔案管理器搜尋結果展示後點擊返回按鈕或切換標籤頁面時可能導致應用崩潰的問題
* `修復` 檔案管理器刪除專案資料夾後 UI 未能自動重新整理的問題
* `修復` 任務面板列表項頻繁變動時可能導致應用崩潰的問題
* `修復` 使用 "三按鈕" 手勢導航時導航欄前景色可能與夜間模式關聯異常的問題
* `修復` APK 檔案型別資訊對話方塊可能無法獲取應用名稱及 SDK 資訊的問題
* `修復` 客戶端模式連線後, 短時間斷開連線並再次連線時, 出現握手超時且難以再次建立連線的問題
* `修復` 客戶端模式連線時, 旋轉螢幕及切換語言等觸發 Activity 重建的操作導致 AutoJs6 總是重建連線的問題
* `修復` 服務端模式連線時, 旋轉螢幕及切換語言等觸發 Activity 重建的操作導致 VSCode 控制檯無法輸出日誌的問題 _[`issue #385`](http://issues.autojs6.com/385)_
* `修復` 連線 VSCode 外掛時, 多種方式同時連線可能導致日誌列印數量成倍增加的問題
* `修復` 佈局分析頁面生成程式碼時對於集合控制元件可能生成失敗的問題 (試修) _[`issue #328`](http://issues.autojs6.com/328)_
* `修復` 設定頁面 "使用 '音量減' 鍵控制錄製" 開關功能失效的問題 _[`issue #480`](http://issues.autojs6.com/480)_
* `修復` 錄製指令碼生成的程式碼檔案可能出現座標數值與螢幕實際畫素值不匹配的問題 _[`issue #480`](http://issues.autojs6.com/480)_
* `修復` 錄製指令碼生成的程式碼檔案首個行為 (如點選或滑動等) 總是被忽略的問題
* `修復` 浮動按鈕 "執行指令碼" 對話方塊後臺操作檔案時可能導致應用崩潰的問題
* `修復` 主頁活動頁面生命週期結束後重新進入主頁時, 浮動按鈕狀態可能被重置的問題
* `修復` 小米裝置 "顯示在其他應用上層" 開關可能跳轉到錯誤設定頁面的問題
* `修復` 檢查更新中止下載過程後, 網路資料包依然持續後臺獲取的問題 (試修)
* `修復` 構建工具啟用 isCleanup[Paddle/Rapid]Ocr 配置選項時無法正常完成 Rebuild Project 任務的問題
* `最佳化` 外掛化 Paddle OCR (PP-OCRv3) 相關功能 _[`issue #334`](http://issues.autojs6.com/334)_ _[`issue #331`](http://issues.autojs6.com/331)_ _[`issue #325`](http://issues.autojs6.com/325)_
* `最佳化` runtime.loadJar 方法增強相容性 (by [LYS86](https://github.com/LYS86)) _[`pr #475`](http://pr.autojs6.com/475)_
* `最佳化` ui.getStatusBarHeight 方法/statusBarHeight 屬性 (getter) 增強一定程度的相容性
* `最佳化` console.log 等方法列印全域性物件 (images, app, ocr 等) 時支援顯示詳細資訊
* `最佳化` dialogs.build 方法支援使用 MaterialDialog.Builder 原生選項引數
* `最佳化` dialogs.build 方法 animation/linkify 選項引數支援字串寬鬆匹配
* `最佳化` device.rotation/rotation/width/height 方法獲取螢幕方向及寬高資料的穩定性與一致性
* `最佳化` app.launchDual/startDualActivity 等方法提升執行效率並增強相容性
* `最佳化` android.graphics.Paint#setColor 支援正常解析 ColorInt/ColorHex/ColorName 等顏色引數
* `最佳化` 內建模組相關方法實參型別的異常訊息增加型別摘要資訊
* `最佳化` 內建模組 (dayjs/cheerio/dayjs/i18n) 脫離指令碼執行模式, 使用全域性懶載入方式替代
* `最佳化` 控制檯相關方法列印巨型資料時根據上限適當省略部分內容以降低列印耗時
* `最佳化` 控制檯浮動視窗倒計時起始值由 6 秒增加到 9 秒
* `最佳化` 控制檯浮動視窗內部實現進行無鎖化及佇列化處理以提升其引數設定效率與成功率
* `最佳化` 控制檯浮動視窗背景色彩行為相關 API (透明度/著色/基色) 更符合安卓設計規範 _[`issue #458`](http://issues.autojs6.com/458)_
* `最佳化` 檔案管理器浮動按鈕展開後點擊選單項時最佳化選單收起時機
* `最佳化` 檔案管理器/任務面板支援顯示檔案/任務數量統計資訊
* `最佳化` 程式碼編輯器儲存檔案失敗時自動存為草稿並支援另存為新檔案
* `最佳化` 程式碼編輯器載入大檔案時提升一定程度的流暢度
* `最佳化` 程式碼編輯器 "查詢/替換" 支援狀態持久化及即時顯示搜尋計數資訊
* `最佳化` 程式碼編輯器提示儲存時確保儲存成功後再退出編輯器以降低儲存失敗率
* `最佳化` 程式碼編輯器儲存按鈕的狀態更符合使用者主觀邏輯
* `最佳化` 程式碼編輯器支援 "-", "=", "(", "[", "{", "<" 等按鍵長按功能
* `最佳化` 打包應用打包過程對話方塊增加 "中止" 按鈕
* `最佳化` 打包應用打包過程對話方塊的顯示方式並增加耗時統計
* `最佳化` 打包應用使用新配置安裝時將覆蓋舊應用的相關配置
* `最佳化` 打包應用配置頁面預設勾選必要許可權 (WAKE_LOCK/INTERNET/WRITE_EXTERNAL_STORAGE/...) _[`issue #397`](http://issues.autojs6.com/397)_
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
* `最佳化` 讀取通知許可權支援自動重連機制 ([requestRebind](https://developer.android.com/reference/android/service/notification/NotificationListenerService#requestRebind(android.content.ComponentName))) 以增強通知監聽相關功能的穩定性
* `最佳化` 浮動按鈕增強後臺啟動 Activity 的安全性以避免應用崩潰
* `最佳化` 浮動按鈕 "更多" 對話方塊使用非同步載入資料方式提升顯示流暢度
* `最佳化` 浮動按鈕 "執行指令碼" 對話方塊增加 "主頁" 選單項
* `最佳化` 浮動按鈕 "執行指令碼" 對話方塊支援最小化及狀態恢復並盡最大努力保持視窗常駐或自動恢復
* `最佳化` 支援使用 Shizuku 許可權錄製指令碼及錄製回放 (回放流暢度受一定影響) _[`issue #320`](http://issues.autojs6.com/320)_
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
* `依賴` 模組化 Recyclerview Flexible Divider 版本 1.4.0
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
* `依賴` 升級 Apache Commons 版本 3.16.0 -> 3.18.0
* `依賴` 升級 Retrofit2 Retrofit 版本 2.11.0 -> 2.12.0
* `依賴` 升級 Retrofit2 Converter Gson 版本 2.11.0 -> 2.12.0
* `依賴` 升級 Retrofit2 RxJava2 版本 2.11.0 -> 2.12.0
* `依賴` 升級 Joda Time 版本 2.12.7 -> 2.14.0
* `依賴` 升級 Gson 版本 2.11.0 -> 2.13.1
* `依賴` 升級 ARSCLib 版本 1.3.1 -> 1.3.5
* `依賴` 升級 Jsoup 版本 1.19.1 -> 1.20.1
* `依賴` 升級 Junit Jupiter 版本 5.10.3 -> 5.13.0
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
* `最佳化` 關於應用與開發者頁面增加布局適配通用性並去除不必要的佈局分類
* `最佳化` README.md 專案編譯構建小節新增多種方式便於定位目標設定頁面 _[`issue #404`](http://issues.autojs6.com/404)_
* `依賴` 附加 AndroidX ConstraintLayout 版本 2.2.1

# v6.6.3

###### 2025/05/27

* `新增` 發行歷史功能, 可檢視發行版本的發行歷史記錄 (多語言) 與統計資料
* `新增` timers.keepAlive 方法 (已全域性化), 用於保持指令碼活躍狀態
* `新增` engines.on('start/stop/error', callback) 等事件監聽方法, 用於監聽指令碼引擎全域性事件
* `新增` images.detectMultiColors 方法, 用於多點顏色校驗 _[`issue #374`](http://issues.autojs6.com/374)_
* `新增` images.matchFeatures/detectAndComputeFeatures 方法, 支援全解析度找圖 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `新增` images.compressToBytes 方法, 用於壓縮影象並生成位元組陣列
* `新增` images.downsample 方法, 用於畫素降取樣並生成新的 ImageWrapper
* `新增` ui.keepScreenOn 方法, 用於 UI 頁面獲取焦點時保持裝置螢幕常亮
* `新增` ui.root 屬性 (getter), 用於獲取 UI 頁面佈局的 "視窗內容根容器" 節點
* `新增` webview 元素支援基於 JsBridge 的 Web 頁面佈局 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) [參閱 示例程式碼 > 佈局 > 可互動 HTML / Vue2 + Vant (SFC)] _[`issue #281`](http://issues.autojs6.com/281)_
* `新增` 佈局分析支援控制元件隱藏 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `修復` 主頁文件標籤及文件活動頁面顯示線上文件時部分內容被系統導航欄遮擋的問題
* `修復` 部分頁面 Toolbar 點選按鈕時可能導致標題點選事件誤觸發的問題
* `修復` 部分裝置程式碼編輯器空行顯示方框字元的問題 (試修)
* `修復` 主題色設定頁面調色盤對話方塊可能無限疊加的問題
* `修復` 無障礙服務關閉時音量加鍵停止所有指令碼功能失效的問題
* `修復` 定時任務頁面編輯自定義廣播內容時出現的輸入法遮擋問題
* `修復` webview 元素中的控制元件無法正常啟用輸入法軟鍵盤的問題
* `修復` APK 檔案型別資訊對話方塊可能無法獲取應用名稱及 SDK 資訊的問題
* `修復` 檔案管理器示例程式碼進入專案目錄時可能無法自動載入子目錄檔案內容的問題
* `修復` Android 15 UI 模式頂部內容被狀態列覆蓋的問題
* `修復` Android 15 部分頁面狀態列背景顏色可能無法動態跟隨主題色的問題
* `修復` dialogs 模組無法正常使用 customView 屬性的問題 _[`issue #364`](http://issues.autojs6.com/364)_
* `修復` dialogs.input 方法的表示式引數可能無法獲得執行結果的問題
* `修復` 使用 JavaAdapter 時導致 ClassLoader 呼叫棧溢位的問題 _[`issue #376`](http://issues.autojs6.com/376)_
* `修復` console.setContentTextColor 方法導致日誌字型顏色丟失預設值的問題 _[`issue #346`](http://issues.autojs6.com/346)_
* `修復` console.setContentBackgroundColor 方法無法接受顏色名稱引數的問題 _[`issue #384`](http://issues.autojs6.com/384)_
* `修復` images.compress 方法實現原理由畫素降取樣修正為編碼質量變化
* `修復` images.resize 方法無法正常使用的問題
* `修復` engines.all 方法可能觸發 ConcurrentModificationException 的問題 _[`issue #394`](http://issues.autojs6.com/394)_
* `修復` README.md 中部分語言日期格式不正確的問題
* `修復` Gradle 構建指令碼可能因獲取到無效庫檔案檔案長度而導致構建失敗的問題 _[`issue #389`](http://issues.autojs6.com/389)_
* `最佳化` 佈局分析選單新增漸變分隔線實現一定程度的功能分組
* `最佳化` 指令碼專案配置檔案 project.json 支援 permissions 選項 (by [wirsnow](https://github.com/wirsnow)) _[`pr #391`](http://pr.autojs6.com/391)_ _[`issue #362`](http://issues.autojs6.com/362)_
* `最佳化` 打包單檔案時自動讀取並勾選已安裝應用的宣告許可權 _[`issue #362`](http://issues.autojs6.com/362)_
* `最佳化` 主題色擴充適配範圍並支援更多控制元件型別
* `最佳化` 主頁抽屜在橫向螢幕或超寬螢幕的寬度適應性
* `最佳化` 關於應用與開發者頁面增加水平佈局及小屏佈局適配
* `最佳化` 設定頁面對話方塊相關設定支援 "使用預設值" 選單選項
* `最佳化` 檔案管理器浮動按鈕展開後點擊其他區域可自動隱藏
* `最佳化` 程式碼編輯器格式化程式碼支援 `??`, `?.`, `??=` 等運算子
* `最佳化` 程式碼編輯器支援 GB18030 / UTF-16 (LE/BE) / Shift_JIS 等編碼的檔案讀寫
* `最佳化` 程式碼編輯器支援顯示檔案詳細資訊 (路徑/編碼/換行符/位元組及字元總數等) _[`issue #395`](http://issues.autojs6.com/395)_
* `最佳化` 意圖相關操作 (編輯/檢視/安裝/傳送/播放等) 增加操作異常提示
* `最佳化` webview 元素的 url 屬性支援相對路徑
* `最佳化` ImageWrapper#saveTo 方法的路徑引數支援相對路徑
* `最佳化` images.save 方法使用 quality 引數時支援 png 格式的檔案體積壓縮 _[`issue #367`](http://issues.autojs6.com/367)_
* `最佳化` 已忽略更新記錄及客戶端模式連線地址記錄支援清空操作
* `最佳化` 版本更新資訊支援多語言顯示 (與當前顯示語言同步)
* `最佳化` 使用非同步載入方式一定程度提升檔案管理器列表滑動流暢性
* `最佳化` 指令碼異常訊息在控制檯的顯示內容與格式
* `最佳化` 示例程式碼支援將資料夾重置為初始內容
* `最佳化` APK 檔案簽名信息提升檢測效率
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

* `新增` ui.statusBarAppearanceLight/statusBarAppearanceLightBy/navigationBarColor 等方法
* `新增` ui.statusBarHeight 屬性 (getter), 用於獲取狀態列高度 _[`issue #357`](http://issues.autojs6.com/357)_
* `新增` images.flip 方法, 用於影象翻轉 _[`issue #349`](http://issues.autojs6.com/349)_
* `新增` 設定頁面增加 "副檔名" 設定選項
* `新增` 主題色設定頁面增加新佈局支援 (分組/定位/搜尋/歷史記錄/調色盤增強等)
* `修復` Android 15 狀態列背景顏色與主題色不一致的問題
* `修復` plugins.load 方法無法正常載入外掛的問題 _[`issue #290`](http://issues.autojs6.com/290)_
* `修復` dx 庫在 Android 7.x 無法正常使用的問題 _[`issue #293`](http://issues.autojs6.com/293)_
* `修復` ScriptRuntime 使用 require 引用內建模組時可能出現的同步狀態異常 (試修) _[`issue #298`](http://issues.autojs6.com/298)_
* `修復` notice 模組缺失 getBuilder 等擴充套件方法的問題 _[`issue #301`](http://issues.autojs6.com/301)_
* `修復` shizuku/shell 等方法無法接受字串引數的問題 _[`issue #310`](http://issues.autojs6.com/310)_
* `修復` colors.pixel 方法無法接受單通道影象引數的問題 _[`issue #350`](http://issues.autojs6.com/350)_
* `修復` engines.execScript/execScriptFile 等方法執行指令碼時預設工作路徑異常 _[`issue #358`](http://issues.autojs6.com/358)_ _[`issue #340`](http://issues.autojs6.com/340)_ _[`issue #339`](http://issues.autojs6.com/339)_
* `修復` floaty.window/floaty.rawWindow 無法在子執行緒執行的問題
* `修復` floaty.getClip 可能無法正常獲取剪下板內容的問題 _[`issue #341`](http://issues.autojs6.com/341)_
* `修復` ui.inflate 返回值丟失 attr/on/click 等原型方法的問題
* `修復` 使用 XML 語法將 JavaScript 表示式作為屬性值時, 其作用域上下文繫結錯誤的問題 _[`issue #319`](http://issues.autojs6.com/319)_
* `修復` 部分方法調用出現異常時無法被 try..catch 塊捕獲的問題 _[`issue #345`](http://issues.autojs6.com/345)_
* `修復` 佈局分析頁面生成程式碼時可能導致應用崩潰的問題 _[`issue #288`](http://issues.autojs6.com/288)_
* `修復` 打包應用無法正常使用 shizuku 模組的問題 _[`issue #227`](http://issues.autojs6.com/227)_ _[`issue #231`](http://issues.autojs6.com/231)_ _[`issue #284`](http://issues.autojs6.com/284)_ _[`issue #287`](http://issues.autojs6.com/287)_ _[`issue #304`](http://issues.autojs6.com/304)_
* `修復` 程式碼編輯器跳轉到行尾時可能跳轉到下一行起始位置的問題
* `修復` 設定頁面連續快速點選對話方塊型別專案時可能導致應用崩潰的問題
* `最佳化` 精簡打包應用模板 APK 檔案大小
* `最佳化` 應用 (以及打包應用) 支援更多許可權 _[`issue #338`](http://issues.autojs6.com/338)_
* `最佳化` 打包應用配置頁面支援 Pinyin 庫選項
* `最佳化` 打包應用主活動頁面最佳化狀態列背景及文字顏色
* `最佳化` 打包應用設定頁面增加訪問所有檔案和傳送通知等特殊許可權開關 _[`issue #354`](http://issues.autojs6.com/354)_
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
* `最佳化` getClip 全域性方法適時藉助 floaty.getClip 方法以提升相容性
* `最佳化` files.path 及相關方法傳入空值路徑引數時的相容性
* `最佳化` 同步最新的 Rhino 引擎官方上游程式碼並進行必要的程式碼適配
* `最佳化` README.md 完善專案構建與執行相關內容 _[`issue #344`](http://issues.autojs6.com/344)_
* `依賴` 附加 Eclipse Paho Client Mqtt V3 版本 1.1.0 _[`issue #330`](http://issues.autojs6.com/330)_
* `依賴` 升級 Android SDK 版本 34 -> 35
* `依賴` 升級 Gradle 版本 8.12 -> 8.14-rc-1
* `依賴` 升級 Rhino 版本 1.8.0-SNAPSHOT -> 1.8.1-SNAPSHOT
* `依賴` 升級 AndroidX Recyclerview 版本 1.3.2 -> 1.4.0
* `依賴` 升級 AndroidX Room 版本 2.6.1 -> 2.7.0
* `依賴` 升級 AndroidX WebKit 版本 1.12.1 -> 1.13.0
* `依賴` 升級 Pinyin4j 版本 2.5.0 -> 2.5.1

# v6.6.1

###### 2025/01/01

* `新增` pinyin 模組, 用於漢語拼音轉換 (參閱 專案文件 > [漢語拼音](https://docs.autojs6.com/#/pinyin))
* `新增` pinyin4j 模組, 用於漢語拼音轉換 (參閱 專案文件 > [漢語拼音](https://docs.autojs6.com/#/pinyin4j))
* `新增` UiObject#isSimilar 及 UiObjectCollection#isSimilar 方法, 用於確定控制元件或控制元件集合是否相似
* `新增` currentComponent 全域性方法, 用於獲取當前活動的元件名稱資訊
* `修復` 部分環境因回退版本過低而無法正常編譯專案的問題
* `修復` 呼叫不存在的方法時可能出現的 "非原始型別值" 異常
* `修復` 部分裝置無法正常新增指令碼快捷方式的問題 (試修) _[`issue #221`](http://issues.autojs6.com/221)_
* `修復` automator.click/longClick 方法引數型別限制錯誤 _[`issue #275`](http://issues.autojs6.com/275)_
* `修復` 選擇器不支援 ConsString 型別引數的問題 _[`issue #277`](http://issues.autojs6.com/277)_
* `修復` UiObjectCollection 例項缺失自身方法及屬性的問題
* `最佳化` 打包應用配置頁面支援簽名配置/金鑰庫管理/許可權設定 (by [luckyloogn](https://github.com/luckyloogn)) _[`pr #286`](http://pr.autojs6.com/286)_
* `最佳化` 提升浮動視窗當前包名及當前活動名的識別準確性 (優先順序: Shizuku > Root > A11Y)
* `最佳化` 提升 currentPackage/currentActivity 識別準確性 (優先順序: Shizuku > Root > A11Y)
* `最佳化` 恢復日誌活動視窗單個條目文字內容的雙擊或長按選擇功能 _[`issue #280`](http://issues.autojs6.com/280)_
* `最佳化` 指令碼專案識別在 project.json 損壞情況下儘可能還原關鍵資訊
* `最佳化` 打包單檔案時自動生成的包名字尾支援將簡體中文轉換為拼音 (支援多音字)
* `最佳化` UiSelector#findOnce 及 UiSelector#find 方法支援負數引數
* `最佳化` app.startActivity/startDualActivity 方法增強適配性
* `最佳化` UI 元素及 className 相關選擇器支援更多的包名字首省略形式 (如 RecyclerView, Snackbar 等)
* `最佳化` 同步最新的 Rhino 引擎官方上游程式碼並進行必要的程式碼適配
* `依賴` 附加 Pinyin4j 版本 2.5.0
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
* `新增` images.matchTemplate 方法支援 useTransparentMask 選項引數, 支援透明找圖 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.requestScreenCaptureAsync 方法, 用於 UI 模式非同步方式申請截圖許可權 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `新增` images.requestScreenCapture 方法支援 isAsync 選項引數, 支援非同步方式獲取螢幕截圖 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
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
* `修復` Android 14 申請截圖許可權導致應用崩潰的問題 (by [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `修復` Android 14 開啟前臺服務導致應用崩潰的問題
* `修復` Android 14 程式碼編輯器執行按鈕點選後無法正常亮起的問題
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
* `最佳化` 打包應用配置頁面中 "不可用 ABI" 控制元件的點選提示體驗
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
* `最佳化` 客戶端模式 IP 地址支援剪下板匯入智慧識別且支援空格按鍵智慧轉換
* `最佳化` 檔案管理器新建檔案時支援預設字首選擇並自動生成合適的數字字尾
* `最佳化` 檔案管理器執行專案時具體化異常訊息提示 _[`issue #268`](http://issues.autojs6.com/268)_
* `最佳化` 檔案管理器支援更多型別並支援顯示對應的圖示符號 (支援 800 多種檔案型別)
* `最佳化` 檔案管理器可編輯的檔案型別 (jpg/doc/pdf/...) 增加編輯按鈕
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
* `依賴` 升級 Rhino 版本 1.7.15-SNAPSHOT -> 1.8.0-SNAPSHOT
* `依賴` 升級 Android Material 版本 1.10.0 -> 1.12.0
* `依賴` 升級 AndroidX Annotation 版本 1.7.0 -> 1.9.1
* `依賴` 升級 AndroidX AppCompat 版本 1.6.1 -> 1.7.0
* `依賴` 升級 AndroidX WebKit 版本 1.8.0 -> 1.12.1
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
* `新增` 打包應用配置頁面增加 ABI 及庫的篩選支援 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `修復` 打包應用檔案體積異常龐大的問題 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `修復` 打包應用無法顯示並列印部分異常訊息的問題
* `修復` 打包應用配置頁面選擇應用圖示後可能顯示空圖示的問題
* `修復` 打包應用包含 MLKit Google OCR 庫時可能出現的上下文未初始化異常
* `修復` ocr.recognizeText/detect 方法無效的問題
* `修復` 部分文字 (如日誌頁面) 顯示語言與應用設定語言可能不相符的問題
* `修復` 部分語言在主頁抽屜開關項可能出現文字溢位的問題
* `修復` 部分裝置無障礙服務開啟後立即自動關閉且無任何提示訊息的問題 _[`issue #181`](http://issues.autojs6.com/181)_
* `修復` 部分裝置無障礙服務開啟後設備物理按鍵可能導致應用崩潰的問題 (試修) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `修復` 使用 auto(true) 重啟無障礙服務後 pickup 功能異常的問題 (試修) _[`issue #184`](http://issues.autojs6.com/184)_
* `修復` floaty 模組建立浮動視窗拖動時可能導致應用崩潰的問題 (試修)
* `修復` app.startActivity 無法使用簡稱引數的問題 _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `修復` importClass 匯入的類名與全域性變數衝突時程式碼丟擲異常的問題 _[`issue #185`](http://issues.autojs6.com/185)_
* `修復` Android 7.x 無法使用無障礙服務的問題
* `修復` Android 14 可能無法正常使用 runtime.loadJar/loadDex 方法的問題 (試修)
* `修復` 安卓系統快速設定面板中 "佈局範圍分析" 和 "佈局層次分析" 不可用的問題 _[`issue #193`](http://issues.autojs6.com/193)_
* `修復` 自動檢查更新功能可能導致應用 [ANR](https://developer.android.com/topic/performance/vitals/anr) 的問題 (試修) _[`issue #186`](http://issues.autojs6.com/186)_
* `修復` 檔案管理器示例程式碼資料夾點選 "向上" 按鈕後無法回到工作路徑頁面的問題 _[`issue #129`](http://issues.autojs6.com/129)_
* `修復` 程式碼編輯器使用替換功能時替換按鈕無法顯示的問題
* `修復` 程式碼編輯器長按刪除時可能導致應用崩潰的問題 (試修)
* `修復` 程式碼編輯器點選 fx 按鈕無法顯示模組函式快捷面板的問題
* `修復` 程式碼編輯器模組函式快捷面板按鈕函式名稱可能溢位的問題
* `最佳化` 程式碼編輯器模組函式快捷面板適配夜間模式
* `最佳化` 打包應用啟動頁面適配夜間模式並調整應用圖示佈局
* `最佳化` 打包應用配置頁面支援使用軟鍵盤 ENTER 鍵實現游標跳轉
* `最佳化` 打包應用配置頁面支援點選 ABI 標題及庫標題切換全選狀態
* `最佳化` 打包應用配置頁面預設 ABI 智慧選擇並增加不可選擇項的引導提示
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

* `新增` dialogs.build 方法支援 inputSingleLine 選項引數
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
* `依賴` 升級 Android SDK 版本 33 -> 34
* `依賴` 升級 OpenCV 版本 4.5.5 -> 4.8.0 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依賴` 升級 Gradle 版本 8.3-rc-1 -> 8.4-rc-3
* `依賴` 升級 Apache Commons Lang3 版本 3.12.0 -> 3.13.0
* `依賴` 升級 Glide 版本 4.15.1 -> 4.16.0
* `依賴` 升級 Android Analytics 版本 14.3.0 -> 14.4.0
* `依賴` 升級 AndroidX WebKit 版本 1.7.0 -> 1.8.0
* `依賴` 升級 AndroidX Preference 版本 1.2.0 -> 1.2.1
* `依賴` 升級 AndroidX Annotation 版本 1.6.0 -> 1.7.0
* `依賴` 升級 AndroidX Recyclerview 版本 1.3.0 -> 1.3.2
* `依賴` 升級 Android Material 版本 1.9.0 -> 1.10.0
* `依賴` 升級 AndroidX AppCompat 版本 1.4.2 -> 1.6.1
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

* `新增` ocr 模組 (參閱 專案檔案 > [光學字元識別](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `新增` notice 模組 (參閱 專案檔案 > [訊息通知](https://docs.autojs6.com/#/notice))
* `新增` s13n 模組 (參閱 專案檔案 > [標準化](https://docs.autojs6.com/#/s13n))
* `新增` Color 模組 (參閱 專案檔案 > [顏色類](https://docs.autojs6.com/#/colorType))
* `新增` colors.setColorPaint 方法, 用於設定 android.graphics.Paint 畫筆顏色 (參閱 專案文件 > [顏色類](https://docs.autojs6.com/#/colorType?id=m-setpaintcolor))
* `新增` 前臺時保持螢幕常亮功能及設定選項
* `新增` 額外的檔案啟動器 (launcher) 便於獨立閱讀應用檔案 (支援在設定中隱藏或顯示)
* `修復` colors.toString 方法功能異常
* `修復` app.openUrl 方法自動新增協議字首功能異常
* `修復` app.viewFile/editFile 在引數對應檔案不存在時的行為異常
* `修復` pickup 方法的回撥函式無法被呼叫的問題
* `修復` 佈局分析顯示的控制元件資訊 bounds 屬性值負數符號被替換為逗號的問題
* `修復` bounds/boundsInside/boundsContains 選擇器無法正常篩選狹義空矩形 (如邊界倒置矩形) _[`issue #49`](http://issues.autojs6.com/49)_
* `修復` 更換主題或修改語言後點選或長按主頁檔案標籤將導致應用崩潰的問題
* `修復` 程式碼編輯器雙指縮放調節字型大小時可能出現抖動的問題
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
* `最佳化` http 模組相關方法支援超時選項引數 (timeout)
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
* `依賴` 升級 AndroidX WebKit 版本 1.5.0 -> 1.6.1
* `依賴` 升級 AndroidX Recyclerview 版本 1.2.1 -> 1.3.0
* `依賴` 升級 Zip4j 版本 2.11.2 -> 2.11.5
* `依賴` 升級 Junit Jupiter 版本 5.9.2 -> 5.9.3
* `依賴` 升級 AndroidX Annotation 版本 1.5.0 -> 1.6.0
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
* `新增` 程式碼編輯器支援雙指縮放調節字型大小
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
* `修復` 程式碼編輯器啟動時可能因夜間模式設定導致閃屏的問題 (試修)
* `修復` 程式碼編輯器設定字型大小時可用最大值受限的問題
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
* `最佳化` 程式碼編輯器按鈕文字適當縮寫避免文字內容溢位
* `最佳化` 程式碼編輯器設定字型大小支援恢復預設值
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
* `依賴` 本地化 Android Job 版本 1.4.3
* `依賴` 本地化 Android Plugin Client SDK For Locale 版本 9.0.0
* `依賴` 本地化 GitHub API 版本 1.306
* `依賴` 升級 Android SDK 版本 32 -> 33
* `依賴` 附加 JCIP Annotations 版本 1.0
* `依賴` 附加 AndroidX WebKit 版本 1.5.0
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
* `依賴` 升級 AndroidX Annotation 版本 1.3.0 -> 1.5.0
* `依賴` 升級 AndroidX AppCompat 版本 1.4.1 -> 1.4.2
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
* `修復` 程式碼編輯器頁面長按時可能導致應用崩潰的問題 _[`issue #18`](http://issues.autojs6.com/18)_
* `修復` 程式碼編輯器頁面長按選單 "刪除行" 和 "複製行" 功能無效的問題
* `修復` 程式碼編輯器頁面選項選單中 "貼上" 功能缺失的問題
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
* `新增` dialogs.build 方法支援 animation, linkify 等選項引數 (參閱 示例程式碼 > 對話方塊 > 個性化對話方塊)
* `修復` dialogs.build 方法 inputHint, itemsSelectedIndex 等選項引數功能異常
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
* `依賴` 升級 Rhino 引擎版本 1.7.14 -> 1.7.15-SNAPSHOT
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
* `依賴` 升級 Android SDK 版本 31 -> 32
* `依賴` 升級 Kotlinx Coroutines 版本 1.6.0-native-mt -> 1.6.0
* `依賴` 升級 OpenCV 版本 3.4.3 -> 4.5.4 -> 4.5.5 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `依賴` 升級 OkHttp3 版本 3.10.0 -> 5.0.0-alpha.4 -> 5.0.0-alpha.6
* `依賴` 升級 Gradle 構建工具版本 7.2.0-beta01 -> 7.3.0-alpha06
* `依賴` 升級 Auto.js-ApkBuilder 版本 1.0.1 -> 1.0.3
* `依賴` 升級 Glide Compiler 版本 4.12.0 -> 4.13.1
* `依賴` 升級 Gradle 發行版本 7.4-rc-2 -> 7.4.1
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
* `依賴` 附加 AndroidX AppCompat (Legacy) 版本 1.0.2
* `依賴` 升級 AndroidX AppCompat 版本 1.4.0 -> 1.4.1
* `依賴` 升級 AndroidX Preference 版本 1.1.1 -> 1.2.0
* `依賴` 升級 Rhino 引擎版本 1.7.14-SNAPSHOT -> 1.7.14
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
* `最佳化` 升級 Rhino 引擎版本 1.7.7.2 -> 1.7.13 -> 1.7.14-SNAPSHOT
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
* `依賴` 附加 AndroidX Preference 版本 1.1.1
* `依賴` 附加 SwipeRefreshLayout 版本 1.1.0
* `依賴` 升級 Android SDK 版本 28 -> 30 -> 31
* `依賴` 升級 Android Analytics 版本 7.0.0 -> 13.1.0
* `依賴` 升級 Android Annotations 版本 4.5.2 -> 4.8.0
* `依賴` 升級 Gradle 構建工具版本 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `依賴` 升級 Android Job 版本 1.2.6 -> 1.4.2
* `依賴` 升級 Android Material 版本 1.1.0-alpha01 -> 1.5.0-beta01
* `依賴` 升級 AndroidX MultiDex 版本 2.0.0 -> 2.0.1
* `依賴` 升級 Apache Commons Lang3 版本 3.6 -> 3.12.0
* `依賴` 升級 Appcompat 版本 1.0.2 -> 1.4.0
* `依賴` 升級 ButterKnife Gradle 外掛版本 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `依賴` 升級 ColorPicker 版本 2.1.5 -> 2.1.7
* `依賴` 升級 Espresso Core 版本 3.1.1-alpha01 -> 3.5.0-alpha03
* `依賴` 升級 Eventbus 版本 3.0.0 -> 3.2.0
* `依賴` 升級 Glide Compiler 版本 4.8.0 -> 4.12.0
* `依賴` 升級 Gradle Build Tool 版本 29.0.2 -> 30.0.2
* `依賴` 升級 Gradle 發行版本 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `依賴` 升級 Groovy-Json 外掛版本 3.0.7 -> 3.0.8
* `依賴` 升級 Gson 版本 2.8.2 -> 2.8.9
* `依賴` 升級 Java 版本 8 -> 11 -> 16
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