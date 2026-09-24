package com.inkomoko.mobilebanking.config;

/**
 * Demo vs live core banking.
 *
 *   inkomoko.fineract.mode=demo (default; "mock" also accepted) — pilot on *662*105#:
 *       DemoFineractService reads/writes the demo_* tables, and /api/admin/demo/** is available
 *       to manage allowed numbers, loans and repayment history.
 *   inkomoko.fineract.mode=live — *311# production: FineractRestService talks to Fineract;
 *       the demo beans and endpoints are not created at all.
 */
public final class DemoMode {

    private DemoMode() {
    }

    /** SpEL condition: true unless the mode is "live". */
    public static final String ENABLED = "!'${inkomoko.fineract.mode:demo}'.trim().equalsIgnoreCase('live')";
}
