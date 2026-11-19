#!/usr/bin/env python3
"""Creates a separate PRIVATE DEBUG keystore outside the project; never use for production."""
import base64
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def main() -> None:
    keytool = shutil.which("keytool")
    if not keytool:
        raise SystemExit("Install a JDK (17+) first; keytool was not found.")
    target = Path.home() / "PrinterLab-private"
    if ROOT == target or ROOT in target.parents:
        raise SystemExit("Choose a project location separate from ~/PrinterLab-private.")
    target.mkdir(exist_ok=True, mode=0o700)
    key = target / "printerlab-debug.keystore"
    encoded = target / "PRINTERLAB_TEST_KEYSTORE_BASE64.txt"
    if key.exists() or encoded.exists():
        raise SystemExit("Existing key files found; refusing to replace your signing identity.")
    subprocess.run([keytool, "-genkeypair", "-noprompt", "-keystore", str(key), "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650", "-dname", "CN=Private Printer Lab Test,O=Local Test,C=HK"], check=True)
    key.chmod(0o600)
    encoded.write_text(base64.b64encode(key.read_bytes()).decode("ascii"), encoding="ascii")
    encoded.chmod(0o600)
    print("Private test files created in " + str(target))
    print("Store the text file contents in repository secret PRINTERLAB_TEST_KEYSTORE_BASE64. Never commit either file.")

if __name__ == "__main__":
    main()
