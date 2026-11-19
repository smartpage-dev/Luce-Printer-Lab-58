package hk.luce.printerlab;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import java.util.EnumMap;

/** Exact integer modules, four-module white border; no image interpolation. */
public final class QrRenderer {
    private QrRenderer() {}
    public static boolean[][] create(String url,int width) throws com.google.zxing.WriterException {
        Protocol.validUrl(url);
        if(width!=160 && width!=240 && width!=320 && width!=384)throw new IllegalArgumentException("Select a supported dot width");
        EnumMap<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET,"ISO-8859-1");
        ByteMatrix qr=Encoder.encode(url,ErrorCorrectionLevel.M,hints).getMatrix();
        int modules=qr.getWidth(),scale=width/(modules+8);
        if(scale<3)throw new IllegalArgumentException("Too dense: choose 320/384 dots or a shorter test URL");
        boolean[][] out=new boolean[width][width];
        int start=(width-modules*scale)/2;
        for(int y=0;y<modules;y++)for(int x=0;x<modules;x++)if(qr.get(x,y)==1)
            for(int dy=0;dy<scale;dy++)for(int dx=0;dx<scale;dx++)out[start+y*scale+dy][start+x*scale+dx]=true;
        return out;
    }
}
