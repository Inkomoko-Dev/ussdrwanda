package com.inkomoko.mobilebanking.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DateUtility {

    public static final ZoneId KIGALI_ZONE = ZoneId.of("Africa/Kigali");
    private static final DateTimeFormatter GATEWAY_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SCREEN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM/yy");

    private DateUtility() {
    }

    public static LocalDateTime nowKigali() {
        return LocalDateTime.now(KIGALI_ZONE);
    }

    public static LocalDate todayKigali() {
        return LocalDate.now(KIGALI_ZONE);
    }

    /** appTstamp format required by the FDI gateway: YYYY-MM-DD HH:MM:SS */
    public static String gatewayTimestamp() {
        return nowKigali().format(GATEWAY_TS);
    }

    public static String screenDate(LocalDate d) {
        return d == null ? "-" : d.format(SCREEN_DATE);
    }

    public static String shortDate(LocalDate d) {
        return d == null ? "-" : d.format(SHORT_DATE);
    }

    /** Fineract [yyyy, M, d] → LocalDate */
    public static LocalDate fromFineract(List<Integer> parts) {
        if (parts == null || parts.size() < 3) return null;
        return LocalDate.of(parts.get(0), parts.get(1), parts.get(2));
    }
}
