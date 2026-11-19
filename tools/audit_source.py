#!/usr/bin/env python3
"""Structural assertions only. Does not replace an Android compile or hardware tests."""
from pathlib import Path
import ast
import json
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
checks=[]
def check(name,condition):
    if not condition: raise AssertionError(name)
    checks.append(name)

def main():
    manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
    a='{http://schemas.android.com/apk/res/android}'
    permissions={p.attrib[a+'name'] for p in manifest.findall('uses-permission')}
    check('No INTERNET permission', 'android.permission.INTERNET' not in permissions)
    check('No broad storage or camera permission',not any('STORAGE' in p or p.endswith('CAMERA') for p in permissions))
    check('Bluetooth connect and scan declared',{'android.permission.BLUETOOTH_CONNECT','android.permission.BLUETOOTH_SCAN'}<=permissions)
    app=manifest.find('application')
    check('Backups disabled',app.attrib[a+'allowBackup']=='false')
    check('No exported services or providers',not app.findall('service') and not app.findall('provider'))
    gradle=(ROOT/'app/build.gradle').read_text()
    check('Isolated application ID',"applicationId 'hk.luce.printerlab'" in gradle)
    check('Pinned SDK 35 and min 26','compileSdk 35' in gradle and 'minSdk 26' in gradle and 'targetSdk 35' in gradle)
    bt=(ROOT/'app/src/main/java/hk/luce/printerlab/BtTransport.java').read_text()
    check('No BLE characteristic or descriptor writes','writeCharacteristic(' not in bt and 'writeDescriptor(' not in bt)
    check('No hidden RFCOMM reflection','getDeclaredMethod' not in bt and 'createRfcommSocket(' not in bt)
    check('Explicit standard SPP service UUID','00001101-0000-1000-8000-00805f9b34fb' in bt)
    check('Finite connect and write timeouts',',12,TimeUnit.SECONDS' in bt and ',30,TimeUnit.SECONDS' in bt)
    main=(ROOT/'app/src/main/java/hk/luce/printerlab/MainActivity.java').read_text()
    check('Lifecycle stop closes connection','protected void onStop()' in main and 'App left foreground' in main)
    check('Single worker and busy gate','Executors.newSingleThreadExecutor()' in main and 'busy.compareAndSet(false,true)' in main)
    check('Document-picker export','Intent.ACTION_CREATE_DOCUMENT' in main and 'finishPendingExport' in main)
    journal=(ROOT/'app/src/main/java/hk/luce/printerlab/Journal.java').read_text()
    check('Crash-state recovery without replay',"SET state='interrupted_unknown' WHERE state='sending'" in journal)
    check('Bounded journal','LIMIT 2000' in journal and 'LIMIT 500' in journal)
    check('No full QR payload column','url_payload' not in journal and 'packet_sha256' in journal)
    policy=(ROOT/'app/src/main/java/hk/luce/printerlab/GuidePolicy.java').read_text()
    check('Advice has no transport or Android dependency','import android.' not in policy and 'BtTransport' not in policy)
    check('Advice is scoped to selected printer','selectedTag.equals(e.deviceTag)' in policy)
    check('Guide cannot send or connect', '.send(' not in policy and '.connect(' not in policy)
    check('Advice selects without transmission', 'Select suggested test (NO PRINT)' in main and 'tests.setSelection(a.testIndex)' in main)
    check('History includes older retained attempts','ORDER BY rowid DESC LIMIT 500' in journal)
    check('Device filter is parameterized','WHERE device_tag=?' in journal)
    check('Report schema preserves explicit upgrade','luce.printerlab.report/2' in journal)
    check('Distinct failure observations',all(k in main for k in ['no_output','partial_or_garbled','qr_unreadable','qr_decoded_mismatch','wrong_device']))
    workflow=(ROOT/'.github/workflows/android-lab.yml').read_text()
    check('Manual-only workflow','workflow_dispatch:' in workflow and '\n  push:' not in workflow and '\n  pull_request:' not in workflow)
    check('Read-only GitHub token','contents: read' in workflow and 'contents: write' not in workflow)
    check('Dedicated optional signing secret','PRINTERLAB_TEST_KEYSTORE_BASE64' in workflow and 'LENSDROP_ADMIN_KEY' not in workflow)
    check('CI runs real SDK build and lint',':app:testDebugUnitTest :app:lintDebug :app:assembleDebug' in workflow)
    check('No installer claim in guide','NO APK INCLUDED' in (ROOT/'START_HERE.html').read_text())
    for f in (ROOT/'tools').glob('*.py'): ast.parse(f.read_text(),filename=str(f))
    check('Python helper syntax',True)
    result={'kind':'source-structure-only','passed':len(checks),'checks':checks,'not_covered':['Android API compilation','native app execution','Bluetooth interoperability','physical printing','power/memory measurements']}
    (ROOT/'evidence/static-checks.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))
if __name__=='__main__':main()
