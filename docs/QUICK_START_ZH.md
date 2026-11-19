> Current source version: **v0.1.3-lab / versionCode 4**. Read `UPGRADE_v0.1.3.html` first when updating. The supplied v0.1.2 GitHub run reached compilation, unit tests and APK assembly but failed lint; v0.1.3 needs a new workflow run. Keep the same signing identity. No APK is bundled.

# v0.1.2 build-fix amendment (19 November 2026)

The v0.1.1 GitHub run failed on a checked-exception UI callback. This version fixes
that cause and adds a source-extracted javac regression. A NEW Run workflow on
the updated commit is required; do not rerun the old failing commit. See
`../UPGRADE_v0.1.2.html` and `BUILD_FIX_v0.1.2.md`. No APK is included.

The retained v0.1.1 operational guide follows; its no-build statement describes
the earlier local delivery, not the new user-uploaded failing GitHub run.

---

# v0.1.1 update

Use START_HERE.html for the new Guided next step card. No automatic send, retry or reconnect. Database schema unchanged; exported report schema /2. Details: CHANGELOG_v0.1.1.md.

# 獨立藍牙打印測試工具

**呢個套件係實際 Android 測試程式來源碼＋GitHub 建置流程，唔係已編譯 APK。** 現時未完成 Android SDK 編譯、手機實測或你部打印機嘅藍牙試印。HTML 只係說明書，唔會直接控制打印機。

目的係逐步確認：手機可以用邊種通訊方式連接，以及邊套指令能夠打印你指定嘅 QR。唔改 LensDrop、唔改 Hostinger、唔改 Windows Print Station。

> 每次只試一張。**Sent / 資料已送出，唔等於紙已經印好。** 每次要睇紙，再用另一部手機核對 QR 嘅完整內容。

## 1. 點樣取得可安裝 APK

今次建議用一個獨立、私人 GitHub repository，名稱例如 `Luce-Printer-Lab-58`。**唔好用呢份程式覆蓋 LensDrop repository。**

1. 解壓套件，將資料夾「裡面嘅檔案」放入新 repository 根目錄，唔好再多包一層。用 GitHub Desktop 最直接；Mac 用 Command + Shift + . 顯示隱藏檔，確保 `.github` 有一齊上傳。
2. 網頁上應該直接見到 `app`、`tools`、`build.gradle`，以及 `.github/workflows/android-lab.yml`。
3. 開 **Actions → Printer Lab Android APK → Run workflow**。Workflow 要放喺預設分支（通常 `main`）先會出現手動執行選項。[官方說明](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)
4. 閱讀並勾選 Android SDK licenses；首次可選 `temporary-test`。之後按 Run workflow。唔需要 Hostinger Key、LensDrop Token 或打印機 PIN。
5. **只有建置成功後**，到該次執行嘅 Artifacts 下載 `PrinterLab58-APK-...`，解壓後將 `PrinterLab58-debug.apk` 傳到 Android 安裝。唔係安裝 ZIP。
6. 如果建置失敗，下載 `PrinterLab58-logs-...`，提供第一個出錯步驟。程式未編譯前，唔好先當成打印機不兼容。

建置配置固定為 JDK 17、Gradle 8.11.1、AGP 8.9.2、SDK 35。App 最低 Android 8（API 26）。GitHub 會執行額外 QR 單元測試及 Android lint；**呢套 GitHub workflow 未喺你帳戶執行過**。

### 多次更新時嘅簽署

`temporary-test` 使用建置機嘅臨時 debug key，下一次可能唔同；Android 更新要求相容簽署。[官方說明](https://developer.android.com/studio/publish/app-signing)

需要保留測試 App 紀錄並持續原地更新時，可用 `tools/create_private_test_key.py` 喺電腦產生獨立測試 key，再將輸出文字放入 GitHub repository secret `PRINTERLAB_TEST_KEYSTORE_BASE64`，建置揀 `persistent-test`。工具唔會覆蓋既有 key；key 檔唔好上傳 repository。呢個唔係正式產品簽署方案。

重新安裝**本測試 App**之前先匯出結果。**唔好刪除 LensDrop 或清空正式隊列。** 兩個 App 使用不同 application ID。

## 2. 第一次準備

先充電、裝普通熱敏小票紙，關閉其他可能控制打印機嘅 App。第一輪先唔連相機，獨立試打印。

Android 系統藍牙設定入面，搵你自檢紙上嘅：

```text
T58_F82E
```

系統要求配對 PIN 時先輸入：

```text
0000
```

呢啲係你提供嘅自檢紙資料，唔係所有機型通用設定。App 唔會將完整藍牙地址或配對 PIN 寫入匯出報告。

開 App → **Grant Bluetooth permissions** → **Refresh paired devices** → 明確選擇你嗰部機。Android 12+ 會要求 Nearby devices；Android 8–11 做 BLE 搜尋時可能需要定位授權及開啟系統定位。拒絕授權就停喺設定步驟，唔會繞過。[Android 權限說明](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)

## 3. 先確認傳輸方式

按 **Inspect Classic service UUIDs (SDP)**，記錄服務資料；然後按 **Connect selected printer via SPP**。

初始使用 secure RFCOMM。若連唔到，先確認冇其他 App 佔用、打印機有電，再考慮明確勾選 compatibility 模式。呢個係較弱認證嘅測試選項，**唔會自動切換**，亦只應用假資料。

空白 SDP 結果唔代表一定冇 SPP。若仍然失敗，可選 **Scan BLE for 8 seconds**，搵返自己嘅機，再按 **Inspect selected BLE services (NO writes)**。

**BLE 呢版只列出 services、characteristics 同 properties，唔會讀寫 characteristic 資料，亦唔會用猜測嘅 UUID 發送打印指令。BLE 打印、USB 打印都未包括。**

## 4. 按次序做紙張測試

每次揀一個測試 → **Preview selected test** → **Send ONE selected test...** → 確認。唔會連環自動試所有指令。

| 測試 | 內容 | 應該睇啲乜 |
|---|---|---|
| A | 普通 ASCII 文字，無 ESC 初始化 | 先證明手機能控制輸出 |
| B | ESC @ 初始化後印英文／數字 | 比較初始化後行為；會重設暫存狀態 |
| C | 短網址原生 QR | 睇到 QR 後，要用另一部手機掃碼核對 |
| D | 長網址原生 QR | 驗證更接近領相連結長度嘅內容 |
| E | 160 × 72 點嘅疏格測試圖 | 先試 GS v 0 圖像指令，唔直接印大黑塊 |
| F / G | 短／長網址點陣 QR | 測試唔依賴打印機內置 QR 產生器嘅方式 |
| H | ESC * 24-dot 圖像 QR | 另一種獨立圖像指令假設，唔係自動 fallback |
| I | 中文＋英文點陣小圖 | 用手機字型畫圖，唔直接假設 PC936 等於 UTF-8 |
| J / K | 自訂測試 HTTPS 網址，原生／點陣 | 初版限制 ASCII 及最多 160 字元；唔用真客人私密連結 |

**第一輪只需 A → B → C。** 基本文字工作後，再做 E → F；D、G、H、I 留到基礎穩定後先試。

原生 QR、GS v 0 同 ESC * 都係依照 Epson 公開指令格式建立嘅兼容性測試，**唔係廠商已確認 58B3 完整支援**。GS v 0 喺 Epson 文件已屬舊式指令；呢度只用作便攜票據機兼容性實驗。

### 點數設定唔係已確認解像度

160 / 240 / 320 / 384 係測試圖闊度「點數」。**320 預設值唔係聲稱打印機有 320 dots，384 選項亦唔代表已確認 203 dpi。** 你嘅說明書只確認 48 mm 有效打印寬度。

原生 QR 有 3–6 dots 模組選項；點陣 QR 由程式用整數模組同四格以上留白產生。太密就拒絕產生，唔會強行模糊縮細。

App 內嘅 QR 預覽只證明預備發送嘅內容；尤其原生 QR，紙上排版仍然由韌體決定。示範網址唔係真實領相頁，**唔需要成功開到網站先算掃碼成功**，要比較讀出嚟嘅完整文字。

## 5. 記錄結果再交返我分析

每次紙上有一個 `T-...` Test ID。去 **History / record physical result**，選返嗰張紙嘅 ID，再記錄：

- `printed_ok`：紙張輸出睇落正常，未代表 QR 已解碼。
- `qr_decoded_match`：另一部手機已讀出 QR，完整內容一致。
- `bad_or_no_output`：亂碼、空白、冇出紙或只印咗一部分。

然後按 **Export diagnostic JSON**，選擇儲存位置。打開 Android 檔案選擇器會離開 App 前景，所以藍牙連線會關閉；完成匯出後，要繼續試印就重新 Connect。

報告包含手機型號、Android 版本、服務 UUID、匿名裝置標記、測試方法／設定、資料長度／雜湊、發送狀態及你嘅觀察。**唔包括完整 MAC、PIN、QR 網址內容、Admin Key 或照片。** 自訂 status 回覆最多記錄八個原始 bytes；分享前仍然睇一次內容。

最有用嘅回傳資料係：**JSON 報告＋對應 Test ID 嘅出紙照片**。未需要拍返整張含序號／MAC 嘅自檢紙。

## 6. 遇到問題點處理

| 情況 | 下一步 |
|---|---|
| 藍牙已配對但唔能夠 SPP 連線 | 確認冇其他 App 佔用，再記錄 SDP；必要時做只讀 BLE 服務檢查 |
| 文字正常、原生 QR 失敗 | 分開試 E 圖像格，再試 F；唔需要亂改 baudrate |
| 圖像變亂碼／紙不停出 | 按 STOP，等已送資料停止；必要時手動關機再開，唔連續補發 |
| QR 有圖但掃唔到 | 記錄尺寸、方法、Test ID；比較裁邊、斷線、打印深淺及留白 |
| DLE EOT 冇回覆 | 只代表回覆能力未確認；工具等兩秒後斷線，唔當成機器壞咗 |
| 重開 App 見 interrupted_unknown | 檢查實際出紙；唔會自動重印。每次再按 Send 都係新測試 |
| APK build 失敗 | 先處理建置 log，唔係打印機問題 |

**STOP 只能停止本機連線及後續送資料，不能撤回已進入打印機緩衝區嘅 bytes。** 本工具無切刀、錢箱、韌體更新、密度、黑標、休眠或 NV 儲存修改指令。ESC @ 會改變暫存設定及清理未完成資料，所以只喺冇其他打印工作時使用。

## 7. 電量及資源範圍

一條工作執行緒、一個手動選定嘅 SPP 連線；連線逾時 12 秒、發送逾時 30 秒、BLE 搜尋 8 秒、服務檢查最多 15 秒。每包最多 64 KiB，圖像最高 384 × 640 dots。只保留最近 500 次測試及 2,000 筆事件。

App 必須留喺前景；離開、鎖屏或切換 App 令其停止時會關閉連線，唔做背景打印。呢個係診斷工具選擇，**唔係 LensDrop 正式上傳／打印隊列嘅最終背景運行方式**。真實耗電、發熱、記憶體及相機共存仍要實機量度。

## 8. 今次完成及未完成嘅驗證

已執行：43 項指令／狀態 + 48 項引導規則純 Java 指令／輸入限制／狀態規則檢查，以及 Java 語法解析。來源碼、權限、workflow 同封裝會再作靜態檢查；詳情見套件 `evidence` 同 `docs/RELEASE_REPORT.md`。

未執行：Android SDK 編譯、Android UI 執行、真正藍牙連線、實機出紙、長時間工作，以及 GitHub 帳戶內嘅建置。環境冇 Android SDK，亦無法解析 Google／Gradle／Maven 下載來源 DNS，**所以今次冇提供 APK，亦冇聲稱已經控制到你部 58B3**。

第一個目標係建置成功，再跑 A/B/C，得到可分析嘅出紙同報告。確認協議後，先另外將成功方法加入 LensDrop 或其他 Photobooth，唔會未驗證就改現有活動系統。

技術參考、來源與限制：套件 `docs/SOURCES.md`。