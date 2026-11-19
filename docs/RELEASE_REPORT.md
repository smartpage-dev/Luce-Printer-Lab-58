# Printer Lab 58 v0.1.3 - API-qualified theme / lint fix

Date: 2026-11-19. App version: `0.1.3-lab`, versionCode `4`.
Application ID: `hk.luce.printerlab`. Minimum SDK remains 26; compile/target SDK remain 35.

## 1. Evidence from the exact supplied log

Input: `logs_120323213749.zip`. The upload was opened directly, not inferred from the previous screenshot or earlier failure. The checkout log identifies commit `cb101d2ce0e3521df08e591f816e4e01d46b6f45`.

This run progressed past the previous checked-callback compilation failure. Its log records `:app:compileDebugJavaWithJavac`, `:app:testDebugUnitTest`, and `:app:assembleDebug` without failure markers. The failing task was `:app:lintDebug`:

```text
app/src/main/res/values/styles.xml:6:
android:windowLightNavigationBar requires API level 27 (current min is 26) [NewApi]
Lint found 1 errors, 4 warnings.
```

The run assembled an APK, but the overall gate failed. The subsequent verification and downloadable-APK steps did not run; this upload is a log archive, not an APK artifact. No APK is recovered or distributed in this package. Four warning details are not expanded in this uploaded log; this fix does not claim they are absent or resolved. The existing workflow uploads the detailed lint HTML/XML with its separate diagnostics artifact.

## 2. Minimal production fix

- Keep the common API-26-compatible properties in `res/values/styles.xml`, under `PrinterLabBaseTheme`.
- Keep `AppTheme` as the manifest's public theme name, explicitly inheriting that common base.
- Place only `android:windowLightNavigationBar=true` in `res/values-v27/styles.xml` as the API-27+ `AppTheme` variant. It explicitly inherits the same base, avoiding duplication or self-inheritance.
- Bump `app/build.gradle` to versionCode 4 / `0.1.3-lab`.

This preserves all previously explicit theme properties on supported API 27+ systems, while API 26 can select a default theme that does not contain the newer attribute. This is a resource structure fix, not a changed printing feature.

Not used: raising minSdk, suppressing NewApi, `tools:targetApi`, a lint baseline, `abortOnError false`, skipping lint, or publishing a build after a failed gate.

Unchanged byte-for-byte from v0.1.2: all seven production Java files, the Java QR unit test, Android manifest, Bluetooth/printing commands, journal schema, report format, workflow, SDK/toolchain pins, signing helper, signing-secret name, and APK collection logic. The v0.1.2 CheckedAction callback fix remains in place. LensDrop and Hostinger are untouched.

## 3. Regression coverage in this package

`tools/test_android_resources.py` parses the actual XML. Its 12 focused source-contract test cases cover default/qualified placement, explicit base inheritance, selected API-level resolution, application identity/minimum preservation, unchanged strict lint gate and the version/CI hook. Negative cases deliberately restore the original unqualified placement, move the attribute to v26, and create an inheritance cycle. The tests reject these bad cases.

These are narrowly scoped XML/source checks, not AAPT2, Android lint, a general Android resource resolver, a full API database, or a visual UI test. The existing CI pure-test step invokes them via `tools/run_core_tests.sh`; the real Android lint task remains mandatory afterward.

Executed locally:
- 12 focused resource-contract test cases (including negative controls).
- 43 existing protocol/recovery assertions.
- 48 existing guided-advice assertions.
- 14 actual-callback Java behavior assertions plus one expected-failure Runnable negative control.
- 7 SQLite query-contract checks.
- 31 structural checks.
- Syntax parsing of 8 app/test Java files; Java helper compilation uses `javac --release 17` on the available JDK 21 runtime.

See `evidence/*v013*`, `evidence/static-checks.json`, and the external package-check report. Historical evidence remains labeled by its original version and is not counted as a new full Android run.

## 4. Limits and current build status

No full v0.1.3 Gradle / Android SDK build, lint run, emulator run, physical phone session, Bluetooth connection or hardware print test occurred here. Gradle, sdkmanager and adb are absent; DNS resolution of the official Google/Gradle/Maven download hosts failed during this turn. See `evidence/environment-v013.json`.

The supplied v0.1.2 GitHub log is evidence that the previous callback fix reached compilation, unit testing and packaging; it is not evidence of a successful v0.1.3 pipeline. A NEW workflow run on the corrected commit is still required. The four lint warnings remain unclassified until a full lint report is inspected.

## 5. Apply the update

Use the patch only on the original v0.1.2 repository. Merge the patch CONTENTS at repository root, preserving unrelated existing files; do not delete the `app` directory. The full package can instead replace the known source snapshot while preserving the repository's `.git` metadata and private signing material. No file deletion or database migration is needed.

Confirm BOTH resource files exist:

```text
app/src/main/res/values/styles.xml
app/src/main/res/values-v27/styles.xml
```

Confirm `app/build.gradle` has `versionCode 4` and `versionName '0.1.3-lab'`. Commit/push, then Actions > Printer Lab Android APK > Run workflow on the updated branch. Do not rerun the old failed job: GitHub reruns use that run's original commit. Retain the previous signing choice/secret; signing is unrelated to this lint failure. Only a fully successful run should supply the verified APK artifact.

The artifact filename remains `PrinterLab58-debug.apk`; inspect `BUILD_INFO.json` for `0.1.3-lab`, code 4 and the new commit. Preserve any lab results and the signing identity before an installed-app update. Do not uninstall LensDrop or clear its queues.

## 6. Official references (checked 2026-11-19)

1. Android API 27 addition: https://developer.android.com/sdk/api_diff/27/changes/android.R.attr
2. Android version-qualified resources and default resources: https://developer.android.com/guide/topics/resources/providing-resources
3. GitHub reruns preserve the original SHA: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs
4. GitHub manual workflow run: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow

These references support the resource-selection and workflow behavior. They are not printer compatibility evidence.
