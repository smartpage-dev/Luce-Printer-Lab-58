package hk.luce.printerlab;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Pure Java, bounded diagnostic commands. Not a manufacturer compatibility claim. */
public final class Protocol {
    public static final String SHORT_URL = "https://example.org/l/DEMO58-001";
    public static final String LONG_URL = "https://example.org/lensdrop-demo/event/wedding/group/012/claim/0123456789abcdefghijklmnopqrstuvwxyz-TEST-ONLY-NOT-A-REAL-TICKET";
    public static final int MAX_PACKET = 65536;
    private Protocol() {}
    public static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i=0;i<values.length;i++) out[i]=(byte)values[i];
        return out;
    }
    public static byte[] ascii(String s) {
        if (s == null || s.length() > 512) throw new IllegalArgumentException("ASCII length");
        for (int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if ((c<32 && c!='\n') || c>126) throw new IllegalArgumentException("Printable ASCII only");
        }
        return s.getBytes(StandardCharsets.US_ASCII);
    }
    public static byte[] concat(byte[]... chunks) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for (byte[] a:chunks) {
            if (a==null || out.size()+a.length>MAX_PACKET) throw new IllegalArgumentException("Packet too large");
            out.write(a,0,a.length);
        }
        return out.toByteArray();
    }
    public static String validUrl(String s) {
        if (s==null || s.length()<12 || s.length()>160) throw new IllegalArgumentException("URL must be 12-160 ASCII characters");
        for(char c:s.toCharArray()) if(c<=32 || c>126) throw new IllegalArgumentException("URL must contain printable ASCII without spaces/control characters");
        try {
            URI u=new URI(s);
            if(!"https".equalsIgnoreCase(u.getScheme()) || u.getHost()==null || u.getRawUserInfo()!=null)
                throw new IllegalArgumentException("Use an HTTPS URL without user credentials");
        } catch (java.net.URISyntaxException ex) { throw new IllegalArgumentException("Invalid URL"); }
        return s;
    }
    public static byte[] text(String id, boolean initialize) {
        if (!id.matches("[A-Z0-9-]{1,24}")) throw new IllegalArgumentException("Test ID");
        return concat(initialize?bytes(27,64):bytes(), ascii("LUCE PRINTER LAB 58\nTEST "+id+"\nABC abc 0123456789\nNO REAL GUEST DATA\n\n\n"));
    }
    public static byte[] nativeQr(String url,int module) {
        validUrl(url);
        if(module<3 || module>6) throw new IllegalArgumentException("Module must be 3-6 dots");
        byte[] data=ascii(url); int p=data.length+3;
        // Epson Model 2, fixed module size, M correction, store, print. Volatile settings only.
        return concat(bytes(27,64,27,97,1,10,10),
            bytes(29,40,107,4,0,49,65,50,0),
            bytes(29,40,107,3,0,49,67,module),
            bytes(29,40,107,3,0,49,69,49),
            bytes(29,40,107,p&255,p>>8,49,80,48),data,
            bytes(29,40,107,3,0,49,81,48,10,10,10,27,97,0));
    }
    public static void validateImage(boolean[][] img) {
        if(img==null || img.length<1 || img.length>640 || img[0]==null || img[0].length<1 || img[0].length>384)
            throw new IllegalArgumentException("Image bounds: 1-384 x 1-640");
        int w=img[0].length;
        for(boolean[] row:img) if(row==null || row.length!=w) throw new IllegalArgumentException("Ragged image");
    }
    public static byte[] raster(boolean[][] img) {
        validateImage(img);
        int h=img.length,w=img[0].length, rowBytes=(w+7)/8;
        byte[] body=new byte[rowBytes*h];
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) if(img[y][x]) body[y*rowBytes+x/8]|=(byte)(0x80>>(x%8));
        return concat(bytes(29,118,48,0,rowBytes&255,rowBytes>>8,h&255,h>>8),body);
    }
    public static byte[] column24(boolean[][] img) {
        validateImage(img); int w=img[0].length,h=img.length;
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        put(out,bytes(27,51,24));
        for(int y0=0;y0<h;y0+=24) {
            put(out,bytes(27,42,33,w&255,w>>8));
            for(int x=0;x<w;x++) for(int b=0;b<3;b++) {
                int v=0;
                for(int k=0;k<8;k++) {int y=y0+b*8+k;if(y<h && img[y][x]) v|=0x80>>k;}
                out.write(v);
            }
            out.write(10);
        }
        put(out,bytes(27,50)); // Restore default line spacing (volatile).
        return out.toByteArray();
    }
    public static byte[] imageJob(String id,boolean[][] img,boolean columns) {
        if(!id.matches("[A-Z0-9-]{1,24}")) throw new IllegalArgumentException("Test ID");
        return concat(bytes(27,64),ascii("LAB "+id+"\n\n"),columns?column24(img):raster(img),bytes(10,10,10));
    }
    public static boolean[][] calibration() {
        boolean[][] a=new boolean[72][160];
        for(int y=0;y<72;y++) for(int x=0;x<160;x++)
            a[y][x]=(x==0 || x==159 || y==0 || y==71 || (y>=16 && y<56 && x%16==0));
        return a;
    }
    public static byte[] statusQuery() { return bytes(16,4,1); }
    private static void put(ByteArrayOutputStream out,byte[] bytes) {out.write(bytes,0,bytes.length);}
    public static String sha256(byte[] input) {
        try {StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(input)) s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static String hex(byte[] b,int max) {
        StringBuilder s=new StringBuilder();for(int i=0;i<Math.min(b.length,max);i++)s.append(String.format(Locale.ROOT,"%02X ",b[i]&255));return s.toString().trim();
    }
}
