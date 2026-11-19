# Printer Lab 58 v0.1.2 - checked-callback build fix

Date: 19 November 2026. Application: `hk.luce.printerlab`.
Version: `0.1.2-lab`; versionCode: `3`.

## Root cause confirmed in the uploaded source logs

`logs_120318232676.zip` records a failure in `:app:compileDebugJavaWithJavac`:
`MainActivity.java:166: error: incompatible thrown types Exception in functional expression`.
The affected call supplies `this::confirmPrint` to the `button` helper.
The helper previously accepted `Runnable`; `confirmPrint()` declares `throws Exception`.
The neighboring preview callback also invokes the checked-throwing `makePacket()`.
`Runnable.run()` has no checked-exception declaration. Catching `Exception` inside
the button's listener does not change the target type of the callback.

The GitHub log confirms that checkout, JDK/Gradle setup, Android SDK setup and
preliminary tests completed before Java compilation failed. This error does not
identify a signing-key, printer, Bluetooth, or Hostinger problem. The failure was
in the supplied application source. Earlier syntax-only parsing did not type-check
Android activity callback bindings and therefore did not catch it.

See `GITHUB_BUILD_FAILURE_EXCERPT.txt` for the relevant, minimized log excerpt.

## Code change

A private `@FunctionalInterface CheckedAction` with `void run() throws Exception`
now expresses the existing button action contract. The button helper accepts this
type instead of `Runnable`. The existing click listener still catches exceptions
and calls `showError`; neither its timing nor the print confirmation flow changes.

Both the preview lambda and the confirmation method reference use the corrected
contract. The patch does not delete `throws`, ignore a failure, disable lint, add
an automatic retry, or replace the single-confirmation printing guard.

The application version is bumped to `0.1.2-lab` / 3. Application ID, database
schema 1, report schema 2, pairing, print bytes, BLE inspection, SDK/Gradle versions,
workflow actions and signing-secret name remain unchanged.

## Regression coverage added

`tools/test_ui_callbacks.py` extracts the ACTUAL button helper, CheckedAction
interface and the two affected registration lines from MainActivity. It compiles
those fragments with `javac --release 17` using deliberately minimal view doubles.
It exercises 14 behavioral assertions: registration never executes an action;
preview and confirmation execute once on click; checked and runtime exceptions
reach the existing error handler; errors do not trigger automatic retries; busy
locking remains; fatal JVM errors are not disguised as success.

A negative control changes only the callback parameter back to Runnable and must
fail type checking. The same harness, run against the unmodified v0.1.1 source,
reproduced the original checked-exception compiler diagnostic.

`tools/run_core_tests.sh` now runs this regression. The existing GitHub workflow
already calls that script, so no workflow, secrets or permissions change is needed.
This harness is not Android API compilation, Activity execution or a Bluetooth test.

## Executed local validation

- 43 protocol/recovery assertions passed.
- 48 guided-policy assertions passed.
- 14 actual-extracted callback behavior assertions passed.
- 1 negative compiler control correctly rejected the original Runnable contract.
- Original v0.1.1 callback failure reproduced independently with the same harness.
- 7 extracted SQLite query-contract checks passed.
- 31 source/manifest/workflow structural checks passed.
- 8 app/test Java source files syntax-parsed successfully (not API type checking).

Local compiler: OpenJDK javac 21.0.12.1 targeting Java 17 with `--release 17`.
The uploaded GitHub run used JDK 17.0.21. The checked-exception rule is not an
Android-version workaround; dependency versions have not been changed.

No new digital QR/raster decoder run is claimed in this patch; the unchanged
historical results are retained separately. No unit-test or lint success from a
full Android Gradle build is claimed. Android SDK/Gradle are not installed in the
local environment and the Google SDK repository lookup fails DNS resolution.
There is no APK in the package, no new GitHub workflow run by this assistant,
and no printer or phone hardware validation.

## Apply and build

1. Use the PATCH package only over the existing v0.1.1 repository. Copy its
   contents to the repository root, preserving each path. It contains changed or
   added files only, and is not a standalone project.
2. Or use the FULL GitHub-ready package as the complete project snapshot. Preserve
   your local `.git` directory and GitHub signing secret. Do not put an extra
   enclosing `PrinterLab58_v0.1.2` folder inside the repository root.
3. Commit the source changes to the branch that will be built. A suitable message:
   `Fix checked UI callbacks - Printer Lab v0.1.2`.
4. Verify `app/build.gradle` shows versionName `0.1.2-lab` and versionCode `3`.
5. Start a NEW run: Actions > Printer Lab Android APK > Run workflow > select the
   updated branch, normally main. Accept SDK licenses and retain your intended
   signing mode. Do not use Re-run jobs on the old failed run: it uses the old SHA.
6. Only a successful run produces the verified downloadable APK artifact. Check
   BUILD_INFO.json for version `0.1.2-lab` and the newly committed SHA.
7. A new compiler/lint/test failure needs its new logs. Do not disable tests or
   lint, clear LensDrop data, change printer settings, or rotate keys to hide it.

The default persistent test secret remains `PRINTERLAB_TEST_KEYSTORE_BASE64`.
The uploaded failure produced no downloadable APK, so it does not itself require
an app uninstall. Preserve any earlier installed app's reports and signing key.

## References and evidence

- Uploaded build log: `logs_120318232676.zip`, compile-step lines 64-84.
- Original source: `Luce_PrinterLab58_v0.1.1_Guided_GitHub_Ready.zip`.
- Java SE 17 Runnable: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runnable.html
- Java SE 17 checked exceptions: https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html
- GitHub manual workflow: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow
- GitHub rerun commit behavior: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs

Primary technical references reviewed on 19 November 2026. Release assertions
above are based on supplied logs/source and the attached local test outputs, not
on a new GitHub execution. Archived v0.1.0/v0.1.1 evidence is historical.
