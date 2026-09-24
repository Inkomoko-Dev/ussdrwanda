package com.inkomoko.mobilebanking.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UssdInputParserTest {

    @Test
    void fdiGatewayRequests() {
        // Exactly what the FDI gateway sends for *662*105#
        String code = UssdInputParser.normalizeServiceCode("RW|MTN|662|105");
        assertEquals("662*105", code);
        assertEquals(List.of(), UssdInputParser.segments("*662*105#", code));
        assertEquals(List.of("1"), UssdInputParser.segments("*662*105*1#", code));
        assertEquals(List.of("1", "1"), UssdInputParser.segments("*662*105*1*1#", code));
        assertEquals(List.of("1", "2", "1234"), UssdInputParser.segments("*662*105*1*2*1234#", code));
    }

    @Test
    void futureShortcode311() {
        String code = UssdInputParser.normalizeServiceCode("RW|MTN|311");
        assertEquals("311", code);
        assertEquals(List.of(), UssdInputParser.segments("*311#", code));
        assertEquals(List.of("1", "2"), UssdInputParser.segments("*311*1*2#", code));
    }

    @Test
    void normalizeServiceCodeForms() {
        assertEquals("662*105", UssdInputParser.normalizeServiceCode("*662*105#"));
        assertEquals("662*105", UssdInputParser.normalizeServiceCode("662*105"));
        assertEquals("311", UssdInputParser.normalizeServiceCode("311"));
        assertEquals("", UssdInputParser.normalizeServiceCode(""));
        assertEquals("", UssdInputParser.normalizeServiceCode(null));
    }

    @Test
    void emptyAndPlainCumulativeInput() {
        assertEquals(List.of(), UssdInputParser.segments("", "662*105"));
        assertEquals(List.of(), UssdInputParser.segments(null, "662*105"));
        assertEquals(List.of("1", "2", "1234"), UssdInputParser.segments("1*2*1234", "662*105"));
    }
}
