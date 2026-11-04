package com.parcelguard.offline;

import java.util.Arrays;
import java.util.Collections;

/** Run directly with javac/java on CI without Android or JUnit dependencies. */
public final class TrackingSelectorSmokeTest {
    private static int tests = 0;
    private static void expectAuto(String carrier, String expected, String... scanned) {
        TrackingSelector.Decision d = TrackingSelector.choose(Arrays.asList(scanned), carrier);
        if (!expected.equals(d.autoTracking)) {
            throw new AssertionError("carrier=" + carrier + " observed=" + Arrays.toString(scanned)
                    + " expected=" + expected + " got=" + d.autoTracking
                    + " review=" + d.reviewOptions + " message=" + d.message);
        }
        tests++;
    }
    private static void expectReview(String carrier, String... scanned) {
        TrackingSelector.Decision d = TrackingSelector.choose(Arrays.asList(scanned), carrier);
        if (!d.autoTracking.isEmpty()) {
            throw new AssertionError("Unexpected autoTracking for " + carrier + ": " + d.autoTracking);
        }
        tests++;
    }
    public static void main(String[] args) {
        String[] samples = {
            "TH39139AU6229B", "TH26079AU6233D", "TH27129AU76J6B",
            "TH39049AU6230D", "TH68119AU6RU9C", "TH70019AU7975C",
            "TH38129AU6232I", "TH33149AU76J5C", "TH68149AU6RU9C",
            "TH50089AU6RU0E"
        };
        for (String s : samples) {
            expectAuto("LEX", s, "T-FC2-01", "19NE-09047-06", s, "G08");
            expectAuto("Flash", s, s, "T-FS1-01");
            expectReview("SPX", s);
            expectReview("J&T", s);
        }
        expectAuto("LEX", "LEXPU0720309511", "H-MUK-A1", "LEXPU0720309511");
        expectAuto("LEX", "KEXLM1006967960", "KEXLM1006967960", "T-KN3-01");
        expectAuto("LEX", "KEXDOLM001221346", "KEXDOLM001221346");
        expectAuto("SPX", "TH263781529981D", "TH263781529981D");
        expectReview("LEX", "H-NAK-A2", "T-FC2-01");
        expectReview("LEX", "TH263781529981D"); // unknown TH format: do not auto-accept
        expectReview("LEX", "TH39139AU6229B", "TH26079AU6233D"); // two parcels in frame
        expectReview("LEX", "TH39139AU6229B", "LEXPU0720309511");
        java.util.List<String> ocr=TrackingSelector.printedTextCandidates(
            "LEX\nTH26079AU6233D\nT-FC2-01\nOrder Number 1126",
            "LEX");
        if(ocr.size()!=1 || !ocr.get(0).equals("TH26079AU6233D"))
            throw new AssertionError("OCR selector failed: "+ocr);
        tests++;
        java.util.List<String> separated=TrackingSelector.printedTextCandidates("TH 39139 AU 6229B","LEX");
        if(separated.size()!=1 || !separated.get(0).equals("TH39139AU6229B"))
            throw new AssertionError("OCR whitespace normalization failed: "+separated);
        tests++;
        if(!TrackingSelector.printedTextCandidates("T-FC2-01\\nH-NAK-A2", "LEX").isEmpty())
            throw new AssertionError("OCR sorting code leak");
        tests++;
        if(!TrackingSelector.printedTextCandidates("TH26079AU6233D", "SPX").isEmpty())
            throw new AssertionError("OCR wrong carrier leak");
        tests++;
        // LEX and Flash share the same physical bag; keep both old labels and
        // the new explicit combined selector backward compatible.
        for(String carrier : new String[]{"LEX","Flash","LEX+Flash"}) {
            for(String s : samples){
                expectAuto(carrier,s,"T-FC2-01",s,"19NE-09047-06");
                if(!TrackingSelector.fastVerifiedLexFlash(s,carrier))
                    throw new AssertionError("single frame LEX/Flash barcode not verified: "+s);
                tests++;
            }
            expectAuto(carrier,"LEXPU0720309511","LEXPU0720309511","H-MUK-A1");
            expectAuto(carrier,"KEXLM1006967960","KEXLM1006967960","T-KA3-01");
            expectAuto(carrier,"KEXDOLM001221346","KEXDOLM001221346","T-KN3-01");
            expectAuto(carrier,"FLE123456789012","FLE123456789012","T-KN3-01");
            expectReview(carrier,"T-FC2-01","H-NAK-A2","19NE-09047-06");
            expectReview(carrier,"TH39139AU6229B","TH26079AU6233D");
            expectReview(carrier,"TH263781529981D"); // not verified for LEX/Flash
            if(TrackingSelector.fastVerifiedLexFlash("T-FC2-01",carrier)
                    || TrackingSelector.fastVerifiedLexFlash("TH263781529981D",carrier))
                throw new AssertionError("sorting or unrelated tracking auto-accepted");
            tests++;
        }
        expectReview("SPX","LEXPU0720309511");
        expectReview("SPX","FLE123456789012");
        if(TrackingSelector.fastVerifiedLexFlash("TH39139AU6229B","SPX"))
            throw new AssertionError("LEX/Flash single-frame leaked to SPX");
        tests++;
        if(!TrackingSelector.printedTextCandidates("FLE123456789012","LEX+Flash")
                .contains("FLE123456789012"))
            throw new AssertionError("Flash OCR in mixed bag failed");
        tests++;
        // Real LEX / Thai Post e-Parcel: WM + 9 digits + TH (UPU S10 check digit).
        // The same printed label also contains a sorting barcode and an order ID;
        // neither must be recorded as the parcel Tracking.
        for(String carrier:new String[]{"LEX","Flash","LEX+Flash","LEX-All"}){
            expectAuto(carrier,"WM901922155TH",
                "T-TB2-01","WM901922155TH","1135081700726632");
            if(!TrackingSelector.fastVerifiedLexFlash("WM901922155TH",carrier))
                throw new AssertionError("Postal Tracking should be single-hit verified");
            tests++;
            expectReview(carrier,"WM901922156TH"); // wrong S10 check digit
            expectReview(carrier,"T-TB2-01","1135081700726632");
        }
        expectReview("SPX","WM901922155TH");
        expectReview("J&T","WM901922155TH");
        expectAuto("LEX-All","TH39139AU6229B","TH39139AU6229B");
        expectAuto("LEX-All","LEXPU0720309511","LEXPU0720309511");
        expectAuto("LEX-All","KEXLM1006967960","KEXLM1006967960");
        expectAuto("LEX-All","FLE123456789012","FLE123456789012");
        expectReview("LEX-All","WM901922155TH","TH39139AU6229B"); // two parcels
        java.util.List<String> postOcr=TrackingSelector.printedTextCandidates(
            "LEX\nWM901922155TH\nT-TB2-01\n1135081700726632","LEX-All");
        if(postOcr.size()!=1 || !postOcr.get(0).equals("WM901922155TH"))
            throw new AssertionError("Thai Post OCR selection failed: "+postOcr);
        tests++;
        if(!TrackingSelector.printedTextCandidates("WM901922156TH","LEX-All").isEmpty())
            throw new AssertionError("Wrong S10 check digit accepted via OCR");
        tests++;
        System.out.println("TrackingSelector smoke tests passed: " + tests);
    }
}
