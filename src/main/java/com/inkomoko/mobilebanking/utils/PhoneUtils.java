package com.inkomoko.mobilebanking.utils;

import com.inkomoko.mobilebanking.enums.ETelecom;

import java.util.ArrayList;
import java.util.List;

public class PhoneUtils {

    private PhoneUtils() {
    }

    /**
     * Normalises a Rwandan MSISDN to the 12-digit international format (250XXXXXXXXX).
     * Handles: +250..., 250..., 07X..., 7X... (9 digits)
     */
    public static String purify(String phone) {
        if (phone == null) return "";
        phone = phone.trim().replace(" ", "");
        if (phone.startsWith("+")) phone = phone.substring(1);
        if (phone.length() == 10 && phone.startsWith("0")) return "25" + phone;
        if (phone.length() == 9 && phone.startsWith("7")) return "250" + phone;
        return phone;
    }

    /** Forms a phone may be stored in (Fineract mobileNo): 250788..., 0788..., +250788..., 788... */
    public static List<String> dialVariants(String purified) {
        if (purified == null || purified.isBlank()) return List.of();
        List<String> v = new ArrayList<>(4);
        v.add(purified);
        if (purified.startsWith("250") && purified.length() == 12) {
            String local = purified.substring(3);
            v.add("0" + local);
            v.add("+" + purified);
            v.add(local);
        }
        return v;
    }

    public static boolean samePhone(String a, String b) {
        if (a == null || b == null) return false;
        return purify(a).equals(purify(b));
    }

    public static boolean isValid(String phone) {
        return phone != null && phone.matches("250[0-9]{9}");
    }

    /**
     * Operator from the gateway networkCode (e.g. "MTN-RW", "AIRTEL-RW"),
     * falling back to the MSISDN prefix (MTN: 078/079, Airtel: 072/073).
     */
    public static ETelecom detectTelecom(String networkCode, String purifiedMsisdn) {
        if (networkCode != null) {
            String n = networkCode.toUpperCase();
            if (n.contains("MTN")) return ETelecom.MTN;
            if (n.contains("AIRTEL") || n.contains("TIGO")) return ETelecom.AIRTEL;
        }
        if (purifiedMsisdn == null || purifiedMsisdn.length() < 5) return null;
        return switch (purifiedMsisdn.substring(3, 5)) {
            case "78", "79" -> ETelecom.MTN;
            case "72", "73" -> ETelecom.AIRTEL;
            default -> null;
        };
    }

    /** Gateway-style network label from the MSISDN, e.g. {@code RW|MTN}, when the telecom omits networkCode. */
    public static String networkCodeFromMsisdn(String purifiedMsisdn) {
        ETelecom telecom = detectTelecom(null, purifiedMsisdn);
        return telecom == null ? "RW|UNKNOWN" : "RW|" + telecom.name();
    }

    /** 250788123456 → 0788***456 */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 9) return phone;
        String local = phone.startsWith("250") && phone.length() == 12 ? "0" + phone.substring(3) : phone;
        return local.substring(0, 4) + "***" + local.substring(local.length() - 3);
    }
}
