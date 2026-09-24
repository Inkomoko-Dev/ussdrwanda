package com.inkomoko.mobilebanking.utils;

import com.inkomoko.mobilebanking.enums.ETelecom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneUtilsTest {

    @Test
    void purify() {
        assertEquals("250788123456", PhoneUtils.purify("0788123456"));
        assertEquals("250788123456", PhoneUtils.purify("+250788123456"));
        assertEquals("250788123456", PhoneUtils.purify("788123456"));
        assertEquals("250788123456", PhoneUtils.purify("250788123456"));
    }

    @Test
    void telecom() {
        assertEquals(ETelecom.MTN, PhoneUtils.detectTelecom("MTN-RW", "250722000000"));
        assertEquals(ETelecom.AIRTEL, PhoneUtils.detectTelecom(null, "250732000000"));
        assertEquals(ETelecom.MTN, PhoneUtils.detectTelecom(null, "250798000000"));
        assertNull(PhoneUtils.detectTelecom(null, "250600000000"));
    }

    @Test
    void mask() {
        assertEquals("0788***456", PhoneUtils.mask("250788123456"));
    }

    @Test
    void samePhone() {
        assertTrue(PhoneUtils.samePhone("0788123456", "250788123456"));
    }
}
