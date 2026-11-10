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
    // Real LEX-Flash photos (2026-11-02): TH39139AU6229B, TH26079AU6233D,
    // TH50089AU6RU0E. New LEX photo (2026-11-10): TH46069B4ZUE0C.
    // The two observed families both use TH + 5 digits + 7 alphanumerics.
    // Unlike common SPX TH codes, position 8 is a LETTER and the suffix
    // contains at least two letters. This is a conservative format heuristic,
    // not independent confirmation of the logistics provider.
    private static boolean isLexFlashTracking(String s) {
        if (s.matches("TH[0-9]{5}AU[A-Z0-9]{5}")) return true;
        if (!s.matches("TH[0-9]{5}[A-Z][A-Z0-9]{6}")) return false;
        int letters=0;
        for (int i=7;i<s.length();i++) {
            if (s.charAt(i)>='A' && s.charAt(i)<='Z')letters++;
        }
        return letters>=2;
    }
    /** UPU S10-compatible Thai Post 13-character identifier.
     *  Example from real LEX/e-Parcel label: WM901922155TH.
     *  The check digit reduces false accepts when several barcodes share a label.
     */
    private static boolean looksLikeThaiPostS10(String s) {
        return s.matches("[A-Z]{2}[0-9]{9}TH");
    }
    private static boolean isThaiPostS10(String s) {
        if(!looksLikeThaiPostS10(s))return false;
        int[] weights={8,6,4,2,3,5,9,7};
        int sum=0;
        for(int i=0;i<weights.length;i++)sum+=(s.charAt(i+2)-'0')*weights[i];
        int check=11-(sum%11);
        if(check==10)check=0;
        else if(check==11)check=5;
        return check==s.charAt(10)-'0';
    }
    private static boolean lexFlashBag(String carrier) {
        if (carrier == null) return false;
        String c=carrier.trim().replace(" ", "").toUpperCase(Locale.ROOT);
        return "LEX".equals(c) || "FLASH".equals(c) || "LEX+FLASH".equals(c)
                || "LEX-ALL".equals(c);
    }

    private static boolean matchesSelectedCarrier(String detected,String selected){
        if (lexFlashBag(selected) && ("LEX".equalsIgnoreCase(detected) ||
                "FLASH".equalsIgnoreCase(detected))) return true;
        return detected.equalsIgnoreCase(selected);
    }

    // Once the barcode decoder has read a uniquely verified LEX/Flash label,
    // accept that hit without requiring a second identical camera frame.
    // Unknown codes and other carriers retain two-frame confirmation.
    static boolean fastVerifiedLexFlash(String code,String selectedCarrier) {
        String s=normalize(code);
        return lexFlashBag(selectedCarrier)
                && (isLexFlashTracking(s) || isThaiPostS10(s) || isKexVerified(s)
                    || (isLex(s) && s.length()==15)
                    || (isFlashSpecific(s) && s.length()==15));
    }
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
        // This family can travel in LEX/Flash bags, but should not silently
        // pass as SPX, J&T or Other solely because it starts with TH.
        if (isLexFlashTracking(s)) return lexFlashBag(current) ? 100 : 0;
        // Thai Post e-Parcel / S10 identifiers are accepted only in LEX-family bags.
        // Do not auto-accept malformed check digits; they must be reviewed.
        if (looksLikeThaiPostS10(s)) return lexFlashBag(current)
                ? (isThaiPostS10(s) ? 100 : 40) : 0;
        String known = knownCarrier(s);
        if (!known.isEmpty()) {
            if (!matchesSelectedCarrier(known,current)) return 0;
            // The prefix KEX alone is insufficient to distinguish a shipment
            // barcode from an undocumented warehouse/internal barcode.
            if (isKexUnknown(s)) return 40; // ask the operator
            return 100;
        }
        switch (current.replace(" ", "").toUpperCase(Locale.ROOT)) {
            case "SPX": return isThShared(s) ? 80 : 40;
            case "LEX":
            case "LEX+FLASH":
            case "LEX-ALL": return 40; // Unknown LEX partner prefix: review.
            case "J&T": return isNumericTracking(s) ? 85 : (isThShared(s) ? 75 : 40);
            case "FLASH": return 40; // Only verified TH...AU... is auto; other TH codes need review.
            default: return 40;
        }
    }


    /**
     * On-demand OCR fallback for physically distorted barcodes.
     * Only accept documented courier families, and ALWAYS ask a human to
     * confirm the printed number before saving image/video evidence.
     */
    static List<String> printedTextCandidates(String rawText, String carrier) {
        if (rawText == null || rawText.isEmpty()) return Collections.emptyList();
        java.util.Set<String> found = new java.util.LinkedHashSet<>();
        java.util.regex.Pattern label = java.util.regex.Pattern.compile(
                "(?<![A-Z0-9])(?:TH[0-9]{5}[A-Z][A-Z0-9]{6}|KEXDOLM[0-9]{9}|KEXLM[0-9]{10}|LEX[A-Z0-9]{8,23}|FLE[A-Z0-9]{12}|[A-Z]{2}[0-9]{9}TH|SPX[A-Z0-9]{8,23})(?![A-Z0-9])");
        for (String line : rawText.toUpperCase(Locale.ROOT).split("\\r?\\n")) {
            String compact = line.replaceAll("\\s+", "");
            java.util.regex.Matcher m = label.matcher(compact);
            while (m.find()) {
                String value = m.group();
                if (confidence(value, carrier) >= 70) found.add(value);
            }
        }
        return new ArrayList<>(found);
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
            if (isLexFlashTracking(s)) {
                if (lexFlashBag(carrier)) scores.put(s, 100);
                else if (!mismatched.contains("LEX/Flash")) mismatched.add("LEX/Flash");
                continue;
            }
            if (looksLikeThaiPostS10(s)) {
                if(lexFlashBag(carrier)){
                    scores.put(s, isThaiPostS10(s)?100:40);
                } else if (!mismatched.contains("LEX/Thailand Post")) {
                    mismatched.add("LEX/Thailand Post");
                }
                continue;
            }
            String known=knownCarrier(s);
            if (!known.isEmpty() && !matchesSelectedCarrier(known,carrier)) {
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
