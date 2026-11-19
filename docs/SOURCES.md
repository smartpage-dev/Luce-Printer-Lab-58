# Evidence and technical references

Prepared 2026-11-18. This document intentionally distinguishes hardware evidence from implementation choices.

## User-supplied physical evidence

The supplied `IMG_7936.jpeg` self-test was reopened and inspected. It prints firmware `H20_2023_V1.0-8009`, Bluetooth advertised name `T58_F82E`, pairing PIN `0000`, USB/Bluetooth, CMD Type `ESC`, sleep setting 14400 seconds and a QR graphic. A QR in the firmware self-test does not establish externally controllable native QR commands.

`IMG_7938.jpeg` manual was reopened and inspected: 58 mm paper, 48 mm printable width, <=42 mm roll, USB-C / Bluetooth, about 190 g without roll. Package label supplied in the conversation identifies Yokoscan / 58B3. No authenticated vendor command/transport document for this precise hardware/firmware was provided. Resolution/dot width, SPP vs BLE, exact ESC subset, battery runtime, external print acknowledgements and wake behavior are unverified. Do not equate this with SPRT SP-L52 or RetroBooth's T58_80C6.

Raw photographs are not redistributed in the project, because they contain hardware identifiers. No raw serial number, MAC, Hostinger administrator key, device token or real guest QR is bundled.

## Primary reference material

1. Android Bluetooth permissions: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
2. Android RFCOMM sockets and matching UUIDs: https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices
3. Android BLE GATT connection/service discovery: https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server
4. Epson QR model selection: https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_lparen_lk_fn165.html
5. Epson QR module size: https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_lparen_lk_fn167.html
6. Epson QR correction level: https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_lparen_lk_fn169.html
7. Epson QR store data: https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_lparen_lk_fn180.html
8. Epson QR print: https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_lparen_lk_fn181.html
9. Epson GS v 0 raster (legacy/obsolete in Epson's reference, retained as an explicit compatibility probe): https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_lv_0.html
10. Epson ESC * column image: https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/esc_asterisk.html
11. Epson DLE EOT real-time status: https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/dle_eot.html
12. AGP 8.9 toolchain requirements: https://developer.android.com/build/releases/agp-8-9-0-release-notes
13. ZXing 3.5.3 release: https://github.com/zxing/zxing/releases/tag/zxing-3.5.3
14. GitHub manual workflows: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow
15. Android app signing: https://developer.android.com/studio/publish/app-signing
16. QR quiet zone: https://www.qrcode.com/en/howto/code.html

## Interpretation limits

The test app uses documented Epson-style byte sequences because the self-test says ESC, not because the manufacturer confirmed full ESC/POS support. Native QR settings, GS v 0, ESC * and DLE EOT remain independent experiments. No successful packet-generation test establishes printer support. Even a successful RFCOMM write only means that Android accepted the bytes; the printer may not have printed them.

ZXing core is an Apache-2.0 dependency retrieved by Gradle, not source copied into this archive. Its license is available at https://github.com/zxing/zxing/blob/master/LICENSE . JUnit is a test-only dependency. Android SDK licenses are accepted only by the person triggering the GitHub build.


## v0.1.1 references rechecked 2026-11-18

- Android RFCOMM connections: https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices
- GitHub manual workflow default-branch rule: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow
- Actions release pages: https://github.com/actions/checkout/releases ; https://github.com/actions/setup-java/releases ; https://github.com/gradle/actions/releases ; https://github.com/actions/upload-artifact/releases
- Pinned tags used: checkout v7.0.1, setup-java v6.3.0, gradle setup v6.4.1, upload-artifact v7.0.2. A release-page check is not a successful CI execution.

## v0.1.3 evidence and resource references (2026-11-19)

- Exact uploaded GitHub run log: `logs_120323213749.zip`; sanitized excerpt in `GITHUB_LINT_FAILURE_EXCERPT_v013.txt`.
- `windowLightNavigationBar` was added in API 27: https://developer.android.com/sdk/api_diff/27/changes/android.R.attr
- Android alternative/default resources: https://developer.android.com/guide/topics/resources/providing-resources
- GitHub rerun source commit semantics: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs
- Current evidence and limitations: `LINT_FIX_v0.1.3.md`; older reports are historical.
