package com.inkomoko.mobilebanking.enums;

/**
 * Inkomoko's client-facing loan states (ToR 3.1(ii)), mapped from Fineract loan status codes.
 * REJECTED / WITHDRAWN / CLOSED cover Fineract states outside the five defined ones.
 */
public enum ELoanStatus {
    UNDER_REVIEW("Irimo gusuzumwa", "Under Review", "En cours d'examen"),
    APPROVED("Yemejwe - itegereje gutangwa", "Approved - Pending Disbursement", "Approuve - en attente"),
    ACTIVE("Irakora", "Active", "Actif"),
    IN_ARREARS("Ifite ibirarane", "In Arrears", "En retard"),
    SETTLED("Yarishyuwe", "Settled", "Solde"),
    REJECTED("Yanzwe", "Rejected", "Rejete"),
    WITHDRAWN("Yakuweho", "Withdrawn", "Retire"),
    CLOSED("Yafunzwe", "Closed", "Ferme");

    private final String rw;
    private final String en;
    private final String fr;

    ELoanStatus(String rw, String en, String fr) {
        this.rw = rw;
        this.en = en;
        this.fr = fr;
    }

    public String label(ELanguage lang) {
        return lang.pick(rw, en, fr);
    }

    public boolean isRepayable() {
        return this == ACTIVE || this == IN_ARREARS;
    }

    /**
     * Fineract status ids: 100 submitted/pending approval, 200 approved, 300 active,
     * 400 withdrawn by client, 500 rejected, 600 closed (obligations met), 601 written off,
     * 602 closed (rescheduled), 700 overpaid.
     */
    public static ELoanStatus fromFineract(Integer statusId, Boolean inArrears) {
        if (statusId == null) return CLOSED;
        return switch (statusId) {
            case 100 -> UNDER_REVIEW;
            case 200 -> APPROVED;
            case 300 -> Boolean.TRUE.equals(inArrears) ? IN_ARREARS : ACTIVE;
            case 400 -> WITHDRAWN;
            case 500 -> REJECTED;
            case 600, 700 -> SETTLED;
            default -> CLOSED;
        };
    }
}
