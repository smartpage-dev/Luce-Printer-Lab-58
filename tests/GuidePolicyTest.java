package hk.luce.printerlab;
import java.util.*;

/** Tests pure decision rules only. No Bluetooth, printer, Android runtime or UI. */
public final class GuidePolicyTest {
    static int n;
    static void ok(boolean condition,String name){n++;if(!condition)throw new AssertionError(name);System.out.println("PASS GUIDE "+n+" "+name);}
    static GuidePolicy.Evidence e(String method,String obs){return new GuidePolicy.Evidence("T-CHECK",method,"one","sent_unconfirmed",obs,10);}
    static GuidePolicy.Advice a(GuidePolicy.Evidence... rows){return GuidePolicy.recommend("one",true,Arrays.asList(rows));}
    static void code(String code,GuidePolicy.Advice a,String name){ok(code.equals(a.code),name+" ["+a.code+"]");}
    static void next(int test,GuidePolicy.Advice a,String name){ok(a.canSelect() && a.testIndex==test,name);}
    public static void main(String[] args){
        code("SELECT_DEVICE",GuidePolicy.recommend(null,false,List.of()),"no automatic device selection");
        code("SELECT_DEVICE",GuidePolicy.recommend("",true,List.of(e("A","printed_ok"))),"empty identity never matches");
        code("CONNECT",GuidePolicy.recommend("one",false,List.of()),"no automatic connect");
        next(0,a(),"first step A");
        next(1,a(e("A - Plain ASCII","printed_ok")),"A verified leads to B selection");
        next(2,a(e("B - ESC @ then ASCII","printed_ok")),"B verified leads to native short QR");
        code("VERIFY_QR",a(e("C - Native QR","printed_ok")),"appearance alone cannot pass QR");
        next(3,a(e("C - Native QR","qr_decoded_match")),"native short exact decode leads to long");
        code("NATIVE_LONG_RECORDED",a(e("D - Native QR","qr_decoded_match")),"native long result ends without certifying skipped steps");
        next(5,a(e("E - Grid","printed_ok")),"grid must precede raster QR");
        next(6,a(e("F - Raster QR","qr_decoded_match")),"raster short leads to raster long");
        code("RASTER_ROUND_DONE",a(e("G - Raster QR","qr_decoded_match")),"raster round finish is not certification");
        for(String s:new String[]{"H","J","K"})code("MANUAL_RESULT_RECORDED",a(e(s,"qr_decoded_match")),"manual QR result "+s);
        code("MANUAL_RESULT_RECORDED",a(e("I","printed_ok")),"Chinese result recorded separately");
        code("REVIEW_RESULT",a(e("A","qr_decoded_match")),"QR evidence cannot certify text");
        for(String state:new String[]{"sent_unconfirmed","outcome_unknown","interrupted_unknown"}){
            GuidePolicy.Advice q=a(new GuidePolicy.Evidence("T-UNKNOWN","C","one",state,"not_checked",20));
            code("OBSERVE",q,"unobserved "+state);ok(!q.canSelect(),"no selection while unobserved "+state);
        }
        code("WAIT",a(new GuidePolicy.Evidence("T-SENDING","B","one","sending","printed_ok",20)),"sending remains a barrier");
        for(String o:new String[]{"qr_decoded_mismatch","wrong_device"})code("STOP_IDENTITY",a(e("C",o)),"identity failure stops "+o);
        for(String o:new String[]{"partial_or_garbled","bad_or_no_output"})code("STOP_BUFFER",a(e("C",o)),"garbled/legacy failure stops "+o);
        for(String o:new String[]{"no_output","qr_unreadable"}){
            GuidePolicy.Advice q=a(e("C",o));code("REVIEW_FAILURE",q,"manual fallback only "+o);ok(!q.canSelect(),"cannot select fallback automatically "+o);
        }
        GuidePolicy.Evidence other=new GuidePolicy.Evidence("T-OTHER","C","other","sending","not_checked",999);
        next(0,a(other),"other printer evidence cannot advance or block selected printer");
        next(1,a(e("A","printed_ok"),other),"newer other-printer entry ignored");
        GuidePolicy.Evidence pending=new GuidePolicy.Evidence("T-OLD","B","one","interrupted_unknown","not_checked",1);
        code("OBSERVE",a(e("C","qr_decoded_match"),pending),"older unresolved attempt not lost behind later success");
        ok(a(e("C","qr_decoded_match"),pending).evidenceId.equals("T-OLD"),"unresolved test ID shown exactly");
        code("OBSERVE",GuidePolicy.recommend("one",false,List.of(pending)),"observe uncertainty even when disconnected");
        code("CONNECT",GuidePolicy.recommend("one",false,List.of(e("A","printed_ok"))),"verified result does not connect automatically");
        next(1,a(new GuidePolicy.Evidence("T-OBS","A","one","outcome_unknown","printed_ok",2)),"manual physical observation remains distinct from transport uncertainty");
        next(2,a(new GuidePolicy.Evidence("T-NEW","B","one","sent_unconfirmed","printed_ok",100),e("A","printed_ok")),"uses latest observation even if rows unordered");
        code("UNKNOWN_TEST",a(e("Z","printed_ok")),"unknown method does not guess opcode");
        code("UNKNOWN_TEST",a(e(null,"printed_ok")),"missing method is not a crash");
        for(String o:new String[]{"no_output","partial_or_garbled","qr_unreadable","qr_decoded_mismatch","wrong_device"})ok(AttemptPolicy.observation(o).equals(o),"observation is persisted "+o);
        ok(!GuidePolicy.isQr(null)&&!GuidePolicy.isQr("E - grid")&&GuidePolicy.isQr("K - custom"),"only QR tests require exact decode");
        System.out.println("TOTAL "+n+" guide checks passed; no Android or hardware exercised");
    }
}
