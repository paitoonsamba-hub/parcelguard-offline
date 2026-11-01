package com.parcelguard.offline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline, deterministic barcode choice. Never guesses a tracking number when
 * more than one plausible number is visible or the selected bag carrier disagrees.
 *
 * The patterns are conservative *hints*, not proof of carrier acceptance.
 * Real courier labels evolve: unknown formats must be confirmed by a human.
 * No Android dependencies, so this class can be unit-tested on a PC.
 */
final class TrackingSelector {
    private TrackingSelector() { }

    static final class Decision {
        final String autoTracking;
        final List<String> reviewOptions;
        final String message;
        Decision(String auto, List<String> review, String warning) {
            autoTracking = auto;
            reviewOptions = Collections.unmodifiableList(new ArrayList<>(review));
            message = warning;
        }
    }

    static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean looksLikeSortingCode(String s) {
        // H-PDG-A1 / B1-1-1 / short bin-location codes are not delivery tracking.
        return s.contains("-") || s.contains("_") || s.length() < 9;
    }

    private static boolean plausibleCharacters(String s) {
        return s.matches("[A-Z0-9]{9,32}");
    }

    // Real LEX labels photographed in the packing station (2026-10-31):
    // KEXDOLM001221346 (KEXDOLM + 9 digits), KEXLM1006943891
    // (KEXLM + 10 digits). Do not turn an unfamiliar KEX family into
    // an automatically accepted tracking number without new evidence.
    private static boolean isKexVerified(String s) {
        return s.matches("KEXDOLM[0-9]{9}") || s.matches("KEXLM[0-9]{10}");
    }
    private static boolean isKexUnknown(String s) {
        return s.matches("KEX[A-Z0-9]{8,23}") && !isKexVerified(s);
    }
    private static boolean isLex(String s) {return s.matches("LEX[A-Z0-9]{8,23}");}
    private static boolean isSpx(String s) {return s.matches("SPX[A-Z0-9]{8,23}");}
    private static boolean isJt(String s) {return s.matches("JT[A-Z0-9]{9,22}");}
    private static boolean isFlashSpecific(String s) {return s.matches("FLE[A-Z0-9]{8,22}");}
    private static boolean isThShared(String s) {return s.matches("TH[A-Z0-9]{10,16}");}
    private static boolean isNumericTracking(String s) {return s.matches("[0-9]{12,16}");}

    private static String knownCarrier(String s) {
        if (isLex(s) || isKexVerified(s) || isKexUnknown(s)) return "LEX";
        if (isSpx(s)) return "SPX";
        if (isJt(s)) return "J&T";
        if (isFlashSpecific(s)) return "Flash";
        return "";
    }

    /** 100 exact carrier prefix, 80 shared courier pattern, 40 human review, 0 reject. */
    static int confidence(String raw, String carrier) {
        String s = normalize(raw);
        if (!plausibleCharacters(s) || looksLikeSortingCode(s)) return 0;
        String current = carrier == null ? "Other" : carrier.trim();
        String known = knownCarrier(s);
        if (!known.isEmpty()) {
            if (!known.equalsIgnoreCase(current)) return 0;
            // The prefix KEX alone is insufficient to distinguish a shipment
            // barcode from an undocumented warehouse/internal barcode.
            if (isKexUnknown(s)) return 40; // ask the operator
            return 100;
        }
        switch (current.toUpperCase(Locale.ROOT)) {
            case "SPX": return isThShared(s) ? 80 : 40;
            case "LEX": return 40; // Unknown LEX prefix: ask, do not guess.
            case "J&T": return isNumericTracking(s) ? 85 : (isThShared(s) ? 75 : 40);
            case "FLASH": return isThShared(s) ? 80 : 40;
            default: return 40;
        }
    }

    static Decision choose(List<String> raws, String carrier) {
        if (raws == null || raws.isEmpty()) return new Decision("", Collections.emptyList(), "");
        Map<String,Integer> scores = new LinkedHashMap<>();
        List<String> mismatched = new ArrayList<>();
        boolean foundSorting = false;
        for (String raw : raws) {
            String s = normalize(raw);
            if (!plausibleCharacters(s) || looksLikeSortingCode(s)) {
                if (!s.isEmpty()) foundSorting = true;
                continue;
            }
            String known=knownCarrier(s);
            if (!known.isEmpty() && !known.equalsIgnoreCase(carrier)) {
                if (!mismatched.contains(known)) mismatched.add(known);
                continue;
            }
            int score=confidence(s,carrier);
            if (score>0) scores.put(s,Math.max(scores.getOrDefault(s,0),score));
        }
        List<String> strong=new ArrayList<>(),review=new ArrayList<>();
        for (Map.Entry<String,Integer> e:scores.entrySet()) {
            if(e.getValue()>=70) strong.add(e.getKey());
            else review.add(e.getKey());
        }
        if (strong.size()==1) {
            // Another explicit courier number in the same frame may be a nearby parcel.
            if (!mismatched.isEmpty()) return new Decision("", strong,
                    "พบ Tracking ต่างขนส่งในภาพเดียวกัน • ตรวจใบปะหน้าและยืนยันก่อนถ่าย");
            // A unique matching tracking beats short internal sorting/reference codes.
            return new Decision(strong.get(0), Collections.emptyList(), "");
        }
        if (strong.size()>1) {
            return new Decision("",strong,"พบ Tracking ที่น่าจะถูกต้องหลายเลข • กรุณาเลือกด้วยตนเอง");
        }
        if (!review.isEmpty()) {
            return new Decision("",review,"ไม่แน่ใจรูปแบบ Tracking • กรุณาตรวจเลขบนใบปะหน้า");
        }
        if (!mismatched.isEmpty()) {
            return new Decision("",Collections.emptyList(),"พบเลขของ " + String.join(", ",mismatched)
                    + " แต่ถุงนี้เลือก " + carrier + " • ตรวจขนส่งก่อนสแกน");
        }
        return new Decision("",Collections.emptyList(),foundSorting
                ? "พบเพียงรหัสคัดแยก/รหัสย่อย • กรุณาหันเลข Tracking จริงเข้ากล้อง" : "");
    }
}
