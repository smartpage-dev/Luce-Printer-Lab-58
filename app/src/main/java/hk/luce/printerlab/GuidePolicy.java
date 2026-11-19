package hk.luce.printerlab;

import java.util.List;

/** Read-only advice. It has no transport, Android, filesystem or automatic retry dependency. */
public final class GuidePolicy {
    private GuidePolicy() {}
    public static final class Evidence {
        public final String id, method, deviceTag, state, observation;
        // Monotonic journal insertion order, not wall-clock time.
        public final long time;
        public Evidence(String id, String method, String deviceTag, String state, String observation, long time) {
            this.id=id; this.method=method; this.deviceTag=deviceTag;
            this.state=state; this.observation=observation; this.time=time;
        }
    }
    public static final class Advice {
        public final String code, message, evidenceId;
        public final int testIndex;
        public Advice(String code, String message, String id, int testIndex) {
            this.code=code; this.message=message; this.evidenceId=id; this.testIndex=testIndex;
        }
        public boolean canSelect() { return testIndex>=0; }
    }
    private static Advice advice(String code,String message,String id,int test) {
        return new Advice(code,message,id,test);
    }
    public static boolean isQr(String method) {
        if(method==null || method.isEmpty())return false;
        return "CDFGHJK".indexOf(method.charAt(0))>=0;
    }
    public static Advice recommend(String selectedTag,boolean connected,List<Evidence> rows) {
        if(selectedTag==null || selectedTag.isEmpty())
            return advice("SELECT_DEVICE","Select your own paired printer first. No automatic connection.","",-1);
        Evidence newest=null,unobserved=null;
        for(Evidence e:rows) {
            if(!selectedTag.equals(e.deviceTag))continue;
            if(newest==null || e.time>newest.time)newest=e;
            if(("not_checked".equals(e.observation)||"sending".equals(e.state))
                &&(unobserved==null || e.time>unobserved.time))unobserved=e;
        }
        if(unobserved!=null) {
            if("sending".equals(unobserved.state))
                return advice("WAIT","A test is still sending. STOP cannot recall printer-buffered data.",unobserved.id,-1);
            return advice("OBSERVE","Check the paper for this Test ID, then record its physical result. A successful write is not a successful print. Never replay an uncertain job automatically.",unobserved.id,-1);
        }
        if(newest!=null) {
            String o=newest.observation;
            if("wrong_device".equals(o)||"qr_decoded_mismatch".equals(o))
                return advice("STOP_IDENTITY","STOP. Check the selected device and the exact decoded QR text. Export the report; do not continue with another print command.",newest.id,-1);
            if("partial_or_garbled".equals(o)||"bad_or_no_output".equals(o))
                return advice("STOP_BUFFER","STOP and inspect paper, power and the Test ID. Garbled/partial output may leave buffered data. Export the report before trying another command family. An old combined failure result should be re-recorded more precisely.",newest.id,-1);
            if("no_output".equals(o)||"qr_unreadable".equals(o)) {
                char t=newest.method==null||newest.method.isEmpty()?'?':newest.method.charAt(0);
                String next=(t=='C'||t=='D'||t=='J')?
                    "After a deliberate STOP, paper/power check and printer restart/reconnect, E (small raster grid) is a possible MANUAL alternative; then F only if E is correct.":
                    (t=='F'||t=='G'||t=='K')?
                    "Check width, paper and QR quiet zone. After STOP and restart/reconnect, H is an optional MANUAL alternative. It is not automatically sent.":
                    "Check paper, power and connection; export the report. Do not increase speed or loop retries.";
                return advice("REVIEW_FAILURE",next,newest.id,-1);
            }
            if(isQr(newest.method)&&!"qr_decoded_match".equals(o))
                return advice("VERIFY_QR","The QR looking correct is not enough. Scan with another phone and compare ALL decoded text, then record a match or failure against this Test ID.",newest.id,-1);
            if(!isQr(newest.method)&&!"printed_ok".equals(o))
                return advice("REVIEW_RESULT","Record a compatible observation for this test. A QR scan result cannot validate a text/grid test.",newest.id,-1);
        }
        if(!connected)return advice("CONNECT","Connect this selected printer using SPP. Missing SDP results are inconclusive; BLE inspection is read-only and is not BLE printing.",newest==null?"":newest.id,-1);
        if(newest==null)return advice("SELECT_A","Start with A: one plain English/numeric test. Preview and confirm before sending.","",0);
        char t=newest.method==null||newest.method.isEmpty()?'?':newest.method.charAt(0);
        int next;
        switch(t) {
            case 'A':next=1;break;
            case 'B':next=2;break;
            case 'C':next=3;break;
            case 'D':return advice("NATIVE_LONG_RECORDED","This long native QR has an operator match. Export before wider testing. A skipped short-QR test is NOT implied to pass. Other settings, raster/Chinese and hardware reliability remain unverified.",newest.id,-1);
            case 'E':next=5;break;
            case 'F':next=6;break;
            case 'G':return advice("RASTER_ROUND_DONE","Long raster QR was operator-verified. Export the report. Reconnection, sleep, repeated tickets and camera coexistence still need separate physical tests.",newest.id,-1);
            case 'H':case 'I':case 'J':case 'K':
                return advice("MANUAL_RESULT_RECORDED","This manual test has a recorded result. Export the report with Test-ID paper photos. Do not infer compatibility for other commands, widths or firmware.",newest.id,-1);
            default:return advice("UNKNOWN_TEST","Unknown test name. Review/export instead of guessing a command.",newest.id,-1);
        }
        return advice("SELECT_NEXT","Select the next test only. Review its current settings and preview, then use the separate Send ONE confirmation. Nothing is sent by this suggestion.",newest.id,next);
    }
}
