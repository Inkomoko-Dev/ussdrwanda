package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.ELoanStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OmnichannelClientTest {

    private OmnichannelClient client(String secret) {
        InkomokoProperties props = new InkomokoProperties();
        props.getOmnichannel().setSecret(secret);
        return new OmnichannelClient(props);
    }

    @Test
    void signsTimestampAndBodyHash() {
        OmnichannelClient c = client("test-secret");
        assertEquals("hzeUNte6O6hKr6rJ2he4S0GVnZVmcX9qdyIeYaqqbhk=",
                c.sign("1700000000000", "{\"phoneNumber\":\"250785642275\"}"));
        assertEquals("s3jaGIMyGMZ+rRGqJ2SEU/GmYRNJsAxX7alG+/wp1BY=", c.sign("1700000000000", ""));
    }

    /** Expected values come from the Postman pre-request script (JSON.stringify(JSON.parse(raw)) + WebCrypto). */
    @Test
    void matchesPostmanScript() {
        OmnichannelClient c = client("test-secret");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phoneNumber", "250785642275");
        body.put("accountNumber", "000000123");
        body.put("amount", OmnichannelClient.amount(new BigDecimal("5000.00")));
        body.put("currency", "RWF");
        body.put("narrative", "Loan repayment RP-1 é/x");
        body.put("channel", "USSD");
        String canonical = c.canonicalBody(body);
        assertEquals("{\"phoneNumber\":\"250785642275\",\"accountNumber\":\"000000123\",\"amount\":5000,"
                + "\"currency\":\"RWF\",\"narrative\":\"Loan repayment RP-1 é/x\",\"channel\":\"USSD\"}", canonical);
        assertEquals("UXv2iS+qj9wuW56ilW6trZiJ7wB1T9SLbAIjYqNGv+E=", c.sign("1700000000000", canonical));
        assertEquals("", c.canonicalBody(null));
    }

    @Test
    void amountsSurviveServerReserialisation() {
        assertEquals(5000L, OmnichannelClient.amount(new BigDecimal("5000.00")));
        assertEquals(1500.5, OmnichannelClient.amount(new BigDecimal("1500.50")));
    }

    @Test
    void mapsLoanStatuses() {
        assertEquals(ELoanStatus.ACTIVE, OmnichannelFineractService.status("Active", BigDecimal.ZERO, 0));
        assertEquals(ELoanStatus.IN_ARREARS, OmnichannelFineractService.status("ACTIVE", new BigDecimal("10"), null));
        assertEquals(ELoanStatus.IN_ARREARS, OmnichannelFineractService.status("Active", null, 4));
        assertEquals(ELoanStatus.SETTLED, OmnichannelFineractService.status("Closed (obligations met)", null, null));
        assertEquals(ELoanStatus.UNDER_REVIEW, OmnichannelFineractService.status("Submitted and pending approval", null, null));
        assertEquals(ELoanStatus.CLOSED, OmnichannelFineractService.status("Closed (written off)", null, null));
    }
}
