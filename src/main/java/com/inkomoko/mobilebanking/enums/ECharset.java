package com.inkomoko.mobilebanking.enums;

/**
 * How a language's texts are encoded on the network. It decides how many characters fit.
 * GSM7: Latin script without most accents: 182 characters per USSD screen, 153 per SMS part.
 * UCS2: any script (Arabic, Amharic, accented Latin): about 80 per USSD screen, 67 per SMS part.
 */
public enum ECharset {
    GSM7(182, 459, 20),
    UCS2(80, 201, 10);

    private final int ussdMax;
    private final int smsMax;
    private final int backReserve;

    ECharset(int ussdMax, int smsMax, int backReserve) {
        this.ussdMax = ussdMax;
        this.smsMax = smsMax;
        this.backReserve = backReserve;
    }

    /** One USSD screen. */
    public int ussdMax() {
        return ussdMax;
    }

    /** Three concatenated SMS parts. */
    public int smsMax() {
        return smsMax;
    }

    /** Room kept for "\n0. Back" under screens that get it appended. */
    public int backReserve() {
        return backReserve;
    }
}
