package hk.luce.printerlab;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** Runs with a normal JDK, without Android or an attached printer. */
public final class ProtocolTest {
    static int n=0;
    static void check(boolean b,String name){n++;if(!b)throw new AssertionError(name);System.out.println("PASS "+n+" "+name);}
    static void fails(Runnable r,String name){boolean ok=false;try{r.run();}catch(IllegalArgumentException e){ok=true;}check(ok,name);}
    public static void main(String[] args)throws Exception{
        check(Protocol.validUrl(Protocol.SHORT_URL).equals(Protocol.SHORT_URL),"short demo accepted");
        check(Protocol.LONG_URL.length()>100 && Protocol.validUrl(Protocol.LONG_URL).equals(Protocol.LONG_URL),"long demo accepted");
        fails(()->Protocol.validUrl("http://example.org/test"),"reject cleartext URL");
        fails(()->Protocol.validUrl("https://user:secret@example.org/test"),"reject URL credentials");
        fails(()->Protocol.validUrl("https://example.org/a\n\u001bp"),"reject control injection");
        fails(()->Protocol.validUrl("https://example.org/\u00e9"),"reject non-ASCII QR input");
        fails(()->Protocol.validUrl("https://example.org/"+"a".repeat(161)),"bound QR input");
        fails(()->Protocol.ascii("\u001b@"),"reject raw ASCII command injection");
        check(Protocol.text("T-ONE",false)[0]=='L',"plain text has no initial ESC");
        check(Protocol.text("T-ONE",true)[0]==27&&Protocol.text("T-ONE",true)[1]==64,"init explicit");
        fails(()->Protocol.text("BAD\nID",true),"test ID validated");
        byte[] q=Protocol.nativeQr(Protocol.SHORT_URL,4);
        check(index(q,Protocol.bytes(29,40,107,4,0,49,65,50,0))>=0,"native model 2 bytes");
        check(index(q,Protocol.bytes(29,40,107,3,0,49,67,4))>=0,"native module bytes");
        check(index(q,Protocol.bytes(29,40,107,3,0,49,69,49))>=0,"native M correction bytes");
        byte[] data=Protocol.SHORT_URL.getBytes(StandardCharsets.US_ASCII);
        int start=index(q,Protocol.bytes(29,40,107,data.length+3,0,49,80,48));
        check(start>=0&&Arrays.equals(Arrays.copyOfRange(q,start+8,start+8+data.length),data),"native length and exact stored payload");
        check(index(q,Protocol.bytes(29,40,107,3,0,49,81,48))>=0,"native print opcode");
        fails(()->Protocol.nativeQr(Protocol.SHORT_URL,0),"reject native module zero");
        fails(()->Protocol.nativeQr(Protocol.SHORT_URL,7),"bound native module size");
        boolean[][] img={{true,false,false,false,false,false,false,true,true},{false,true,false,false,false,false,false,false,false}};
        byte[] r=Protocol.raster(img);
        check(Arrays.equals(Arrays.copyOf(r,8),Protocol.bytes(29,118,48,0,2,0,2,0)),"raster width bytes and row count");
        check(Arrays.equals(Arrays.copyOfRange(r,8,r.length),Protocol.bytes(129,128,64,0)),"MSB horizontal packing and white padding");
        fails(()->Protocol.raster(new boolean[][]{{true},{false,true}}),"reject ragged raster");
        fails(()->Protocol.raster(new boolean[2][385]),"limit width");
        fails(()->Protocol.raster(new boolean[641][4]),"limit height");
        fails(()->Protocol.concat(new byte[65537]),"limit packet size");
        boolean[][] columns=new boolean[25][2];columns[0][0]=true;columns[23][1]=true;columns[24][0]=true;
        byte[] col=Protocol.column24(columns);
        check(Arrays.equals(Arrays.copyOf(col,8),Protocol.bytes(27,51,24,27,42,33,2,0)),"column header and 24-dot line spacing");
        check(col[8]==(byte)128&&col[13]==1,"column MSB packing");
        check(col[20]==(byte)128,"column partial last band padding");
        check(col[col.length-2]==27&&col[col.length-1]==50,"line spacing restored");
        check(Protocol.calibration().length==72 && Protocol.calibration()[0].length==160,"bounded grid dimensions");
        check(Arrays.equals(Protocol.statusQuery(),Protocol.bytes(16,4,1)),"single explicit status query");
        check(AttemptPolicy.afterWrite(100,100,true).equals("sent_unconfirmed"),"write is not print confirmation");
        check(AttemptPolicy.afterWrite(0,100,false).equals("outcome_unknown"),"no completed writes does not prove no partial bytes");
        check(AttemptPolicy.afterWrite(100,100,false).equals("outcome_unknown"),"flush/ack uncertainty");
        check(AttemptPolicy.afterRestart("sending").equals("interrupted_unknown"),"crash does not replay");
        check(AttemptPolicy.afterRestart("sent_unconfirmed").equals("sent_unconfirmed"),"restart preserves completed transfer status");
        for(String state:new String[]{"sending","sent_unconfirmed","outcome_unknown","interrupted_unknown"})
            check(!AttemptPolicy.autoRetryAllowed(state),"no auto retry "+state);
        fails(()->AttemptPolicy.observation("printed_by_driver"),"unrecognized evidence not allowed");
        check(AttemptPolicy.observation("qr_decoded_match").equals("qr_decoded_match"),"operator QR evidence distinct");
        check(AttemptPolicy.safeError(new java.io.IOException("AA:BB:CC:DD:EE:FF secret")).equals("bluetooth_io_error"),"error excludes private messages");
        check(Protocol.sha256(new byte[0]).equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),"SHA256 reference");
        Path out=Path.of(args.length>0?args[0]:".test-build/packets");Files.createDirectories(out);
        Files.write(out.resolve("A_plain_text.bin"),Protocol.text("FIXTURE-A",false));
        Files.write(out.resolve("B_init_text.bin"),Protocol.text("FIXTURE-B",true));
        Files.write(out.resolve("C_native_qr.bin"),q);
        Files.write(out.resolve("E_grid_gsv0.bin"),Protocol.imageJob("FIXTURE-E",Protocol.calibration(),false));
        Files.write(out.resolve("H_grid_column24.bin"),Protocol.imageJob("FIXTURE-H",Protocol.calibration(),true));
        System.out.println("TOTAL "+n+" pure-Java checks passed; no Bluetooth or hardware exercised");
    }
    static int index(byte[] a,byte[] b){outer:for(int i=0;i<=a.length-b.length;i++){for(int j=0;j<b.length;j++)if(a[i+j]!=b[j])continue outer;return i;}return -1;}
}
