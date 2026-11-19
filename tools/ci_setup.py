#!/usr/bin/env python3
"""GitHub runner SDK and optional test signing setup. Never prints secret contents."""
import base64
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def main() -> None:
    if os.environ.get("ACCEPT_LICENSES") != "true":
        raise SystemExit("Explicit Android SDK license acceptance is required.")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk or not Path(sdk).is_dir():
        raise SystemExit("Hosted runner did not provide an Android SDK. Install SDK or use ubuntu-24.04.")
    sdk = str(Path(sdk).resolve())
    manager = Path(sdk) / "cmdline-tools/latest/bin/sdkmanager"
    if not manager.is_file():
        found = shutil.which("sdkmanager")
        if not found:
            raise SystemExit("sdkmanager is unavailable on this runner.")
        manager = Path(found)
    subprocess.run([str(manager), f"--sdk_root={sdk}", "--licenses"], input="y\n" * 200, text=True, check=True)
    subprocess.run([str(manager), f"--sdk_root={sdk}", "platforms;android-35", "build-tools;35.0.0"], check=True)
    (ROOT / "local.properties").write_text("sdk.dir=" + sdk + "\n", encoding="utf-8")
    mode = os.environ.get("SIGNING_MODE", "temporary-test")
    key = Path.home() / ".android/debug.keystore"
    key.parent.mkdir(parents=True, exist_ok=True)
    if key.exists():
        key.unlink()
    if mode == "persistent-test":
        raw = os.environ.get("PRINTERLAB_TEST_KEYSTORE_BASE64", "")
        if not raw:
            raise SystemExit("Missing repository secret PRINTERLAB_TEST_KEYSTORE_BASE64.")
        try:
            data = base64.b64decode("".join(raw.split()), validate=True)
        except Exception:
            raise SystemExit("Test keystore secret is not valid Base64.") from None
        if len(data) < 1000 or len(data) > 100000:
            raise SystemExit("Unexpected test keystore size.")
        key.write_bytes(data)
        key.chmod(0o600)
        subprocess.run(["keytool", "-list", "-keystore", str(key), "-storepass", "android", "-alias", "androiddebugkey"], check=True, stdout=subprocess.DEVNULL)
        print("Restored private test key. Not a production signing identity.")
    elif mode == "temporary-test":
        print("A fresh debug key will be generated. Later updates may require the original signing identity.")
    else:
        raise SystemExit("Unknown signing mode.")

if __name__ == "__main__":
    main()
