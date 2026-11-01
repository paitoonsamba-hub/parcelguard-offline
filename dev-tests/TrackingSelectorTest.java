package com.parcelguard.offline;
import java.util.Arrays;
import java.util.Collections;

public final class TrackingSelectorTest {
    static int tests=0;
    static void check(boolean ok,String title) {
        tests++;
        if(!ok)throw new AssertionError("FAILED: "+title);
    }
    public static void main(String[] args){
        TrackingSelector.Decision d=TrackingSelector.choose(Arrays.asList("H-PDG-A1", "LEXPU0717820919"), "LEX");
        check("LEXPU0717820919".equals(d.autoTracking),"LEX label + bin sorting code => real tracking");
        d=TrackingSelector.choose(Collections.singletonList("H-PDG-A1"), "LEX");
        check(d.autoTracking.isEmpty() && d.reviewOptions.isEmpty(),"Bin sorting code never auto-saved");
        d=TrackingSelector.choose(Collections.singletonList("LEXPU0717820919"), "SPX");
        check(d.autoTracking.isEmpty() && d.message.contains("LEX"),"Mismatch blocks auto-save");
        d=TrackingSelector.choose(Arrays.asList("LEXPU0717820919", "LEXPU0717820920"),"LEX");
        check(d.autoTracking.isEmpty() && d.reviewOptions.size()==2,"Two valid LEX numbers demand review");
        d=TrackingSelector.choose(Arrays.asList("LEXPU0717820919", "LEXPU0717820919"),"LEX");
        check("LEXPU0717820919".equals(d.autoTracking),"same barcode detected twice deduped");
        d=TrackingSelector.choose(Collections.singletonList("SPX000012341"),"SPX");
        check("SPX000012341".equals(d.autoTracking),"SPX prefix");
        d=TrackingSelector.choose(Collections.singletonList("TH012345678912A"),"SPX");
        check(!d.autoTracking.isEmpty(),"SPX Thai shared prefix");
        d=TrackingSelector.choose(Collections.singletonList("TH0876G5S6FA77"),"Flash");
        check(!d.autoTracking.isEmpty(),"Flash Thai shared prefix");
        d=TrackingSelector.choose(Collections.singletonList("820112345678"),"J&T");
        check("820112345678".equals(d.autoTracking),"J&T numeric pattern");
        d=TrackingSelector.choose(Collections.singletonList("SPX000012341"),"LEX");
        check(d.autoTracking.isEmpty(),"SPX cannot silently become LEX");
        d=TrackingSelector.choose(Collections.singletonList("RANDOM123456789"),"Other");
        check(d.autoTracking.isEmpty() && d.reviewOptions.size()==1,"Unknown tracking asks confirmation");
        d=TrackingSelector.choose(Collections.singletonList("H-PDG-A1"),"Other");
        check(d.autoTracking.isEmpty(),"Other rejects short sorting codes");
        d=TrackingSelector.choose(Arrays.asList("TH012345678912A","LEXPU0717820919"),"SPX");
        check(d.autoTracking.isEmpty() && d.reviewOptions.size()==1,"Different courier codes in one scene force review");
        d=TrackingSelector.choose(Collections.emptyList(),"SPX");
        check(d.autoTracking.isEmpty(),"Empty scene safe");
        d=TrackingSelector.choose(Arrays.asList("LEXPU0717820919", "ABC12345678910"),"LEX");
        check("LEXPU0717820919".equals(d.autoTracking),"strong carrier over weaker unrelated code");
        // Ten correctly captured LEX/KEX examples from real user-supplied
        // evidence photos. Routing codes on the same labels must be ignored.
        for(String tracking:new String[]{
            "KEXDOLM001221346", "KEXDOLM001221475",
            "KEXDOLM001221653", "KEXDOLM001221994",
            "KEXLM1006943891", "KEXLM1006943895",
            "KEXLM1006943932", "KEXLM1006944256",
            "KEXLM1006944302", "KEXLM1006947847"}) {
            d=TrackingSelector.choose(Arrays.asList("T-KA3-01",tracking,"B31*NE3-C14 (C)"),"LEX");
            check(tracking.equals(d.autoTracking),"LEX KEX label chooses shipping code: "+tracking);
        }
        d=TrackingSelector.choose(Collections.singletonList("H-NAK-A2"),"LEX");
        check(d.autoTracking.isEmpty() && d.reviewOptions.isEmpty(),"H-NAK-A2 must not be recorded");
        d=TrackingSelector.choose(Arrays.asList("H-NAK-A2","KEXLM1006943891"),"LEX");
        check("KEXLM1006943891".equals(d.autoTracking),"sorting code beside KEXLM ignored");
        d=TrackingSelector.choose(Arrays.asList("T-KN3-01","KEXDOLM001221346"),"LEX");
        check("KEXDOLM001221346".equals(d.autoTracking),"T-KN3 sorting code ignored");
        d=TrackingSelector.choose(Collections.singletonList("KEXLM1006943891"),"SPX");
        check(d.autoTracking.isEmpty() && d.reviewOptions.isEmpty() && d.message.contains("LEX"),"KEX in SPX bag blocked");
        d=TrackingSelector.choose(Collections.singletonList("KEXXQ1234567890"),"LEX");
        check(d.autoTracking.isEmpty() && d.reviewOptions.size()==1,"unknown KEX prefix requires review");
        d=TrackingSelector.choose(Collections.singletonList("KEXLM100694389"),"LEX");
        check(d.autoTracking.isEmpty() && d.reviewOptions.size()==1,"wrong KEXLM digit count requires review");
        d=TrackingSelector.choose(Arrays.asList("KEXLM1006943891","KEXDOLM001221346"),"LEX");
        check(d.autoTracking.isEmpty() && d.reviewOptions.size()==2,"two valid KEX barcodes require review");
        d=TrackingSelector.choose(Collections.singletonList("T-KA3-01"),"LEX");
        check(d.autoTracking.isEmpty() && d.reviewOptions.isEmpty(),"T-KA3 routing code not accepted");
        d=TrackingSelector.choose(Collections.singletonList("LEXPU0717820919"),"LEX");
        check("LEXPU0717820919".equals(d.autoTracking),"previous LEXPU continues working");
        System.out.println("PASS " + tests + " TrackingSelector tests");
    }
}
