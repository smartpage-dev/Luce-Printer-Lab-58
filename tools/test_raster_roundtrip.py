#!/usr/bin/env python3
"""Optional developer check: Python QR fixtures -> actual Java packet writer -> independent ZBar decode.
This does NOT execute the Android ZXing renderer or any physical printer.
Requires qrcode, numpy, opencv-python, pyzbar / libzbar and a JDK. No Bluetooth access.
"""
from pathlib import Path
import subprocess
import qrcode
import cv2
import numpy as np
from pyzbar.pyzbar import decode as zbar_decode

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / '.test-build/roundtrip'


def unpack_raster(packet: bytes) -> np.ndarray:
    assert packet[:4] == bytes([29,118,48,0])
    stride = int.from_bytes(packet[4:6], 'little')
    height = int.from_bytes(packet[6:8], 'little')
    assert len(packet) == 8 + stride * height
    return np.unpackbits(np.frombuffer(packet[8:], dtype=np.uint8)).reshape(height, stride * 8).astype(bool)


def unpack_columns(packet: bytes, height: int, width: int) -> np.ndarray:
    assert packet[:3] == bytes([27,51,24])
    image=np.zeros((((height+23)//24)*24,width),dtype=bool)
    pos=3
    for y0 in range(0,height,24):
        assert packet[pos:pos+3]==bytes([27,42,33])
        w=int.from_bytes(packet[pos+3:pos+5],'little');assert w==width
        pos+=5
        for x in range(w):
            for b in range(3):
                value=packet[pos];pos+=1
                for k in range(8):image[y0+b*8+k,x]=bool(value&(0x80>>k))
        assert packet[pos]==10;pos+=1
    assert packet[pos:]==bytes([27,50])
    return image[:height]


def main() -> None:
    OUT.mkdir(parents=True,exist_ok=True)
    classes=ROOT/'.test-build/classes';classes.mkdir(parents=True,exist_ok=True)
    subprocess.run(['javac','--release','17','-d',str(classes),str(ROOT/'app/src/main/java/hk/luce/printerlab/Protocol.java'),str(ROOT/'tests/RasterBridge.java')],check=True)
    urls=['https://example.org/l/DEMO58-001','https://example.org/lensdrop-demo/event/wedding/group/012/claim/0123456789abcdefghijklmnopqrstuvwxyz-TEST-ONLY-NOT-A-REAL-TICKET']
    count=0
    for i,url in enumerate(urls):
        code=qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M,box_size=1,border=4)
        code.add_data(url);code.make(fit=True)
        small=np.array(code.get_matrix(),dtype=bool);scale=320//small.shape[0];assert scale>=3
        enlarged=np.repeat(np.repeat(small,scale,0),scale,1)
        pixels=np.zeros((320,320),dtype=bool);offset=(320-enlarged.shape[0])//2
        pixels[offset:offset+enlarged.shape[0],offset:offset+enlarged.shape[1]]=enlarged
        path=OUT/f'qr{i}.pbm'
        path.write_text('P1\n320 320\n'+'\n'.join(' '.join('1' if p else '0' for p in row) for row in pixels))
        raster=OUT/f'qr{i}-raster.bin';columns=OUT/f'qr{i}-columns.bin'
        subprocess.run(['java','-cp',str(classes),'hk.luce.printerlab.RasterBridge',str(path),str(raster),str(columns)],check=True)
        for method,restored in [('GSv0',unpack_raster(raster.read_bytes())),('ESCstar',unpack_columns(columns.read_bytes(),320,320))]:
            assert np.array_equal(restored,pixels)
            rendered=np.where(restored,0,255).astype(np.uint8)
            decoded=[item.data.decode('ascii') for item in zbar_decode(rendered) if item.type=='QRCODE']
            assert decoded==[url],(method,decoded)
            cvtext,_,_=cv2.QRCodeDetector().detectAndDecode(rendered)
            if cvtext!=url: print(f'NOTE: OpenCV classic detector did not decode fixture {i}; ZBar verified exact data. Phone validation still required.')
            count+=1;print(f'PASS {count}: fixture {i} {method} exact pixels and ZBar QR text match')
    print(f'TOTAL {count} digital raster round trips; not Android ZXing or physical print tests')

if __name__=='__main__': main()
