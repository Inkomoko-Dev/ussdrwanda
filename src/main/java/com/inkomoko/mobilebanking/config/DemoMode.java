package com.inkomoko.mobilebanking.config;

/**
 * Demo vs live core banking.
 *
 *   inkomoko.fineract.mode=demo (default; "mock" also accepted) — pilot on *662*105#:
 *       DemoFineractService reads/writes the demo_* tables, and /api/admin/demo/** is available
 *       to manage allowed numbers, loans and repayment history.
 *   inkomoko.fineract.mode=live — *311# production: FineractRestService talks to Fineract.
 *   inkomoko.fineract.mode=omnichannel — OmnichannelFineractService talks to the Omnichannel API.
 *   In live and omnichannel modes the demo beans and endpoints are not created at all.
 */
public final class DemoMode {

    private DemoMode() {
    }

    /** SpEL condition: true unless the mode is "live" or "omnichannel". */
    public static final String ENABLED = "!{'live','omnichannel'}.contains('${inkomoko.fineract.mode:demo}'.trim().toLowerCase())";

    public static boolean isDemo(String mode) {
        String m = mode == null ? "" : mode.trim().toLowerCase();
        return !m.equals("live") && !m.equals("omnichannel");
    }
}
