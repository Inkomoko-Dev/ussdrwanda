package com.inkomoko.mobilebanking.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UssdInputParserTest {

    @Test
    void emptyFirstRequest() {
        assertEquals(List.of(), UssdInputParser.segments("", "311"));
        assertEquals(List.of(), UssdInputParser.segments(null, "311"));
    }

    @Test
    void cumulativeInput() {
        assertEquals(List.of("1", "2", "1234"), UssdInputParser.segments("1*2*1234", "311"));
    }

    @Test
    void dialStringWithServiceCodeIsStripped() {
        assertEquals(List.of(), UssdInputParser.segments("*311#", "311"));
        assertEquals(List.of("1", "2"), UssdInputParser.segments("*311*1*2#", "311"));
        assertEquals(List.of("1"), UssdInputParser.segments("*662*101*1#", "662*101"));
    }

    @Test
    void plainInputStartingWithCodeDigitsIsKept() {
        assertEquals(List.of("311"), UssdInputParser.segments("311", "311"));
    }
}
