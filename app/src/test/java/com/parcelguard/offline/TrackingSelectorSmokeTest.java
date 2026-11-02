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
        System.out.println("TrackingSelector smoke tests passed: " + tests);
    }
}
