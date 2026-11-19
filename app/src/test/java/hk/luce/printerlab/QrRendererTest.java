package hk.luce.printerlab;

import org.junit.Test;
import static org.junit.Assert.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;

/** Runs in the GitHub/Gradle build; not exercised by the local no-SDK test runner. */
public class QrRendererTest {
    private void decode(String url,int width)throws Exception{
        boolean[][] a=QrRenderer.create(url,width);int[] pix=new int[width*width];
        for(int y=0;y<width;y++)for(int x=0;x<width;x++)pix[y*width+x]=a[y][x]?0xff000000:0xffffffff;
        BinaryBitmap bitmap=new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width,width,pix)));
        assertEquals(url,new MultiFormatReader().decode(bitmap).getText());
        for(int x=0;x<width;x++){assertFalse(a[0][x]);assertFalse(a[width-1][x]);}
    }
    @Test public void shortRasterRoundTrip()throws Exception{decode(Protocol.SHORT_URL,320);}
    @Test public void longRasterRoundTrip()throws Exception{decode(Protocol.LONG_URL,320);}
    @Test public void widerRasterRoundTrip()throws Exception{decode(Protocol.LONG_URL,384);}
    @Test(expected=IllegalArgumentException.class)public void rejectsUnknownWidth()throws Exception{QrRenderer.create(Protocol.SHORT_URL,321);}
    @Test(expected=IllegalArgumentException.class)public void rejectsTooDense()throws Exception{QrRenderer.create("https://example.org/"+"x".repeat(140),160);}
}
