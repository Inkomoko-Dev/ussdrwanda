package com.inkomoko.mobilebanking.utils;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;

public class Utility {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter REF_TS = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private Utility() {
    }

    /** Unique repayment reference, e.g. IKR260924101530482913 (also used as Fineract externalId). */
    public static String generateRepaymentReference() {
        return "IKR" + DateUtility.nowKigali().format(REF_TS) + (100000 + RANDOM.nextInt(900000));
    }

    /** Our session reference returned to the gateway as appRef. */
    public static String generateAppRef() {
        return "IKS" + DateUtility.nowKigali().format(REF_TS) + (100000 + RANDOM.nextInt(900000));
    }
}
