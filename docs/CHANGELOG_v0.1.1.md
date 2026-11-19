# Printer Lab 58 v0.1.1 - guided assessment

Standalone source/build kit. No APK, no physical test, no existing LensDrop changes.

- Add a read-only, device-scoped next-step advisor. It selects a test only after an explicit tap and never sends, connects, retries or certifies a device.
- Require operator observations before the suggested sequence advances. QR appearance is not a decoded-text match. Unknown outcomes block progression.
- Separate no output, partial/garbled, unreadable QR, mismatching decoded QR and wrong-device observations. Existing journal records retained.
- Failures pause the suggested sequence; manual alternative command families remain in the existing dropdown after inspection/restart.
- Database schema remains 1; report schema is now luce.printerlab.report/2 with prior fields retained and additional observation values.
- App identity unchanged, versionCode 2. Updating an installed v0.1.0 needs the same signing key. No uninstall needed if identity/signature are compatible.
- Pin documented Actions releases, add structural checks to the manual build workflow.
- No USB transport, BLE writes, unattended printing, hidden RFCOMM channel probing, cloud access or firmware changes.
