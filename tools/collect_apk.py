#!/usr/bin/env python3
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]

def main() -> None:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise SystemExit("Android SDK path missing.")
    source = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    if not source.is_file():
        raise SystemExit("Build did not produce an APK.")
    out = ROOT / "ci-output"
    out.mkdir(exist_ok=True)
    signer = Path(sdk) / "build-tools/35.0.0/apksigner"
    check = subprocess.run([str(signer), "verify", "--verbose", "--print-certs", str(source)], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (out / "apk-verification.txt").write_text(check.stdout, encoding="utf-8")
    if check.returncode:
        raise SystemExit("APK signature verification failed.")
    target = out / "PrinterLab58-debug.apk"
    shutil.copy2(source, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    gradle_source = (ROOT / "app/build.gradle").read_text()
    name = re.search(r"versionName\s+'([^']+)'", gradle_source)
    code = re.search(r"versionCode\s+(\d+)", gradle_source)
    if name is None or code is None:
        raise SystemExit("Cannot determine source version; refusing incorrect build metadata.")
    info = {
        "application_id": "hk.luce.printerlab",
        "version": name.group(1), "version_code": int(code.group(1)),
        "built_at_utc": datetime.now(timezone.utc).isoformat(),
        "commit": os.environ.get("GITHUB_SHA", "local"),
        "workflow_run": os.environ.get("GITHUB_RUN_ID", "local"),
        "signing_mode": os.environ.get("SIGNING_MODE", "local"),
        "apk_sha256": digest, "apk_bytes": target.stat().st_size,
        "gradle": "8.11.1", "agp": "8.9.2", "compile_target_sdk": 35,
        "min_sdk": 26, "hardware_verified": False,
        "warning": "Compiler and local unit tests do not validate Bluetooth transport, physical printing, Android lifecycle or device-specific behavior."
    }
    (out / "BUILD_INFO.json").write_text(json.dumps(info, indent=2), encoding="utf-8")
    (out / "SHA256SUMS.txt").write_text(digest + "  PrinterLab58-debug.apk\n", encoding="utf-8")
    print("Verified APK collected; physical hardware not tested.")

if __name__ == "__main__":
    main()
