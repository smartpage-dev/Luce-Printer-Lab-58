# Luce Printer Lab 58 - v0.1.3 API-qualified theme / lint fix

An independent Android foreground test app for assessing Brian's Yokoscan / 58B3 candidate. **Source and build workflow only: no compiled APK is included, no physical printer or Android device was tested here.**

Open **START_HERE.html** for the practical guide. The native app uses English button labels; the quick-start guide includes Cantonese instructions.

## Isolated from your event system

- Separate Android package: `hk.luce.printerlab` (Android 8 / API 26+).
- No Hostinger login, no LensDrop database changes, no printer credential upload.
- No INTERNET, camera or broad file-storage permission.
- One foreground operator-controlled connection; one manually confirmed test at a time.
- No automatic retries, reconnection, bulk print loops or background printing.
- No firmware flashing, NV settings, cash-drawer, cutter, sleep or density commands.
- BLE discovery lists services/properties only. **BLE printing is not implemented.**
- USB printing is not implemented.

## Get a test APK

Create a **separate private repository** such as `Luce-Printer-Lab-58`. Copy the CONTENTS of this folder, including `.github`, into its root. Do not place these files over the LensDrop repository.

GitHub -> Actions -> **Printer Lab Android APK** -> Run workflow. Accept SDK licenses deliberately. Choose `temporary-test` for a disposable first trial, or `persistent-test` after configuring `PRINTERLAB_TEST_KEYSTORE_BASE64`.

A successful run creates a `PrinterLab58-APK-...` artifact containing `PrinterLab58-debug.apk`, a checksum and build report. The workflow also runs QR encoder unit tests and Android lint. **The supplied v0.1.2 log passed Java compilation, unit testing and APK assembly, then failed Android lint on a misqualified API-27 theme attribute. This update fixes that resource placement; a new full GitHub run is still required.** If it fails, send the first failed build step / logs, not any private key.

The project has no Gradle wrapper. GitHub installs Gradle 8.11.1. Android Studio can use that exact local Gradle distribution; AGP 8.9.2, JDK 17, SDK 35 / build-tools 35.0.0 are required. Gradle downloads ZXing 3.5.3 and JUnit 4.13.2 at build time. No downloaded SDK or third-party binaries are bundled.

## New in v0.1.3

Fixes `NewApi` in `values/styles.xml`: `windowLightNavigationBar` is now confined
to `values-v27/styles.xml`, sharing an explicit common base theme. API 26 support
and strict Android lint are retained. VersionCode is 4; the v0.1.2 callback fix,
all printing/Bluetooth code, database, identity and signing configuration remain.

Read **UPGRADE_v0.1.3.html** and **docs/LINT_FIX_v0.1.3.md**. Upload both resource
files, commit/push, and start a NEW workflow on the updated branch. The patch is
for v0.1.2 only. Do not delete existing app files or clear app data.

## Guided features retained from v0.1.1

The **Guided next step** card reads only evidence for the selected printer. Tap **Refresh suggested next step**, then **Select suggested test (NO PRINT)**. Selection never sends bytes: preview and **Send ONE** still require separate confirmation. The advice is not a lock on the manual dropdown, a compatibility certificate or a retry engine.

Record results after every attempt. No output, partial/garbled output, unreadable QR, decoded-text mismatch and wrong device are now separate choices. A QR merely looking correct does not advance the guided sequence. Failures pause advice; optional manual fallbacks require checking paper, STOP and deliberate restart/reconnect first. A-D is the first round; E/F/G and H/I are optional, not an automatic sweep.

The private SQLite schema is unchanged and retains older records; exported report schema is now `luce.printerlab.report/2`. Up to 500 retained attempts are visible in History. Different firmware, command settings or media require separate assessment; advice is not proof that all previous test settings are compatible.

## First physical trial

1. Close every other printing app. Load normal receipt paper. Keep LensDrop out of this initial test.
2. In Android Settings pair your own `T58_F82E`; use `0000` only if the OS asks. This identifier/PIN comes from the supplied self-test, not a universal rule.
3. Open the lab, grant Bluetooth permissions, refresh paired devices, and explicitly select yours.
4. Inspect SDP; attempt secure SPP. A missing UUID does not prove lack of support. Insecure compatibility mode is separate, explicit, and only for fake-data assessment.
5. Send **A** once. Record what came out. Send **B** only after A has finished (or after a deliberate printer restart if the stream is garbled).
6. If text works, try **C** short native QR. Decode with another phone and compare the complete text. Never assume a website opening is required: demo URLs are not live galleries.
7. Test raster **E** first, then **F**. If needed test the separate **H** column-image hypothesis. Stop between failed/garbled methods; do not run an automatic sweep.
8. Record results under the printed Test ID and export a diagnostic JSON. Share it together with a photograph of the printed output (cover unrelated serial/MAC labels).

The guide contains the complete test table and failure decision tree.

## Local checks

`bash tools/run_core_tests.sh`

A JDK 17+ and Python 3 are needed for the core checks: 12 focused XML/source cases, 43 packet/recovery assertions, 48 advice assertions, the 14 callback assertions with a negative control, and Java syntax parsing. **These checks do not link Android APIs, exercise Bluetooth, verify SQLite on Android or validate actual printed QR codes.** Gradle unit tests with the actual ZXing dependency are a separate CI step.

## Signing

An optional `tools/create_private_test_key.py` creates a private DEBUG key outside this repository. Store its Base64 text in GitHub repository secret `PRINTERLAB_TEST_KEYSTORE_BASE64`. Never commit it or reuse a production key. A temporary runner key can change between builds, preventing an in-place update; export lab reports before reinstalling THIS LAB APP. Never uninstall LensDrop to solve a lab signing mismatch.

## Runtime resource limits

Single worker; single selected RFCOMM socket; 12-second connect timeout; 30-second write timeout; 8-second BLE scan; 15-second GATT inspection; 2-second optional status wait. Raster image limit: 384 x 640 dots; packet limit: 64 KiB. Journal retains up to 500 attempts and 2,000 events. Report contains salted device tags and hashes instead of raw MACs/QR contents. Keep the app foreground; switching apps or opening the document picker closes the printer connection.

## Evidence and sources

`evidence/core-tests-v013.log`, `evidence/static-checks.json` and `docs/RELEASE_REPORT.md` describe executed and unexecuted checks. `docs/SOURCES.md` separates supplied hardware facts from Android/Epson/GitHub reference material. Epson command definitions are **compatibility hypotheses**, not certification of the 58B3's implementation.
