package hk.luce.printerlab;

/** No state authorizes automatic retry or claims physical print completion. */
public final class AttemptPolicy {
    private AttemptPolicy() {}
    public static String afterWrite(int bytes,int total,boolean completed) {
        if(bytes<0 || total<0 || bytes>total) throw new IllegalArgumentException("Byte count");
        return completed && bytes==total ? "sent_unconfirmed" : "outcome_unknown";
    }
    public static String afterRestart(String state) {
        return "sending".equals(state) ? "interrupted_unknown" : state;
    }
    public static boolean autoRetryAllowed(String state) { return false; }
    public static String observation(String s) {
        if(!s.equals("not_checked") && !s.equals("printed_ok") && !s.equals("qr_decoded_match") && !s.equals("bad_or_no_output") && !s.equals("no_output")
            && !s.equals("partial_or_garbled") && !s.equals("qr_unreadable")
            && !s.equals("qr_decoded_mismatch") && !s.equals("wrong_device"))
            throw new IllegalArgumentException("Observation");
        return s;
    }
    public static String safeError(Throwable e) {
        // Do not export arbitrary exception messages; OEM stacks can include MACs or payloads.
        if(e instanceof SecurityException)return "permission_denied";
        if(e instanceof java.io.InterruptedIOException || e instanceof InterruptedException)return "cancelled_or_timeout";
        if(e instanceof java.io.IOException)return "bluetooth_io_error";
        if(e instanceof IllegalArgumentException)return "invalid_test_parameters";
        return "operation_error";
    }
}
