# Luce Printer Lab 58 v0.1.1 - guided diagnostic release

Date: 2026-11-18. Separate Android source/build kit for the purchased Yokoscan / 58B3 sample. No APK is included. No LensDrop, Hostinger, Print Station or other application has been modified.

## Purpose and changes

This extends the existing v0.1.0 lab rather than starting another printer-control project. New Guided next step advice is scoped to the selected device's retained observations. It never sends, connects, retries or claims certification. Selection and actual Send ONE remain separate actions. The manual test dropdown remains available: advice is not a transport interlock.

- A/B text then C/D native QR is the first suggested round. D-only manual success does not imply C passed.
- Pending/unobserved attempts must be physically checked before the suggested sequence advances.
- Appearance alone cannot verify QR data. The operator must decode with another phone and compare the complete text.
- No output, partial/garbled output, unreadable QR, wrong QR data and wrong device are separate observations.
- Failures stop automatic suggestions; optional E/F/G/H alternatives remain deliberate manual trials after STOP, inspection and restart/reconnect.
- All retained attempt IDs (up to 500) are accessible in History, not only the latest 60.
- Guidance uses local insertion order, so a backwards phone-clock change does not reorder the latest result.
- Private SQLite schema stays 1; report schema becomes luce.printerlab.report/2 with original columns retained. No app-data reset required.
- Build metadata reads the actual source version; Actions tags pinned to official release versions checked on 2026-11-18.

## App and signing

Application ID remains hk.luce.printerlab. Version 0.1.1-lab, versionCode 2. minSdk 26; compile/target 35; build-tools 35.0.0; JDK17; Gradle8.11.1; AGP8.9.2. The original test signing key is needed for an in-place upgrade. Export existing Lab reports before any deliberate reinstall. Never uninstall LensDrop to fix a Lab signing mismatch.

The manual workflow remains Printer Lab Android APK. Its optional secret remains PRINTERLAB_TEST_KEYSTORE_BASE64. No account was connected, no GitHub workflow was triggered, and no signing key is bundled.

## Executed verification on final sources

- 43 actual Protocol/AttemptPolicy Java tests passed.
- 48 actual GuidePolicy Java tests passed: selected-device isolation, unobserved/unknown outcomes, matching QR evidence, chronology, no automatic transport actions and failure-specific advice.
- 7 SQLite query-contract tests passed using SQL extracted from Journal.java against Python SQLite. This is not Android SQLite execution.
- 31 structural checks passed on manifest, source, workflow and helper syntax.
- 8 Java source files parsed with the JDK compiler parser. This is not Android API linkage.
- 4 digital raster round trips passed through the actual Java packet writers and independent ZBar decoding. Fixtures are Python-generated; Android ZXing rendering and paper scanning were not exercised. The long fixture still fails one OpenCV detector; this does not establish universal scanner compatibility.
- The actual guide HTML rendered at 390 and 1280 pixels with no horizontal overflow, script errors or external requests. Native file navigation was blocked by the browser environment; set_content was used. This is documentation QA, not Android UI QA.

See evidence/*-v011.log, evidence/static-checks.json, evidence/guide-qa.json and evidence/environment-v011.json. Historical v0.1.0 evidence is labelled separately.

## Explicitly not validated

No Android SDK or Gradle distribution is installed here. Current download probes failed DNS for Google SDK, Gradle and Maven. No APK compilation, Android lint/JUnit run, native UI/lifecycle test or APK install was performed here. The separate GitHub workflow is the intended next build gate, not an executed result.

No physical SPP/BLE connection, custom paper output, status reliability, sleep/wake, paper-out, battery, thermal or concurrent camera-upload test has been performed. The candidate's exact Bluetooth protocol and ESC subset remain unknown.

## Boundaries preserved

Manual foreground-only operation, one selected socket, bounded writes, no automatic retries/reconnects/bulk prints, no INTERNET permission. BLE is service enumeration only, no writes or printing. No USB transport, firmware flashing, permanent settings, sleep/density/black-mark/cutter/cash-drawer commands. ESC @ resets volatile state; close other printer apps. STOP cannot retract bytes already buffered by the printer.

Native QR and image commands are compatibility hypotheses, not an assertion that 58B3 supports Epson's entire command set. See docs/SOURCES.md. Bluetooth device address, QR text, pairing PIN and cloud credentials are not included in the audit JSON; phone model, salted device tag and service UUIDs are included.

## First physical check

After a successful GitHub build, pair the sample in Android Settings, select it in the Lab, inspect SDP and try secure SPP. Use A once, record the paper result, then B and C individually. Use fake URLs. Send the JSON report with matching Test-ID paper photos. On a build failure provide the first compiler/setup error instead, without credentials.

## Baseline provenance

Original v0.1.0 archive SHA-256: 3ec9aa3ebdfad81f045d891284cbe6d5aadc0361f54e9dec06e43b7a8091f8ba
The original archive is preserved unchanged. This package is source, documentation and reproducible tests only.
