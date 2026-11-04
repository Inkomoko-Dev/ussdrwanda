package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.models.RepaymentTransaction;
import com.inkomoko.mobilebanking.models.dto.PaymentStatusResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** POST /transactions/{requestId}/manual-success against a local stub of Omnichannel. */
class OmnichannelManualSuccessTest {

    private record Seen(String method, String path, String body, String contentType, String signature, String timestamp) {
    }

    private HttpServer server;
    private final List<Seen> seen = new ArrayList<>();
    private volatile int status = 200;
    private volatile String response = "{}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            seen.add(new Seen(ex.getRequestMethod(), ex.getRequestURI().getPath(), body,
                    ex.getRequestHeaders().getFirst("Content-Type"),
                    ex.getRequestHeaders().getFirst("X-SIGNATURE"),
                    ex.getRequestHeaders().getFirst("X-TIMESTAMP")));
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OmnichannelClient client(boolean enabled) {
        InkomokoProperties props = new InkomokoProperties();
        props.getOmnichannel().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1");
        props.getOmnichannel().setApiKey("fdi");
        props.getOmnichannel().setSecret("test-secret");
        props.getOmnichannel().setManualSuccessEnabled(enabled);
        return new OmnichannelClient(props);
    }

    private static RepaymentTransaction tx(String requestId) {
        RepaymentTransaction t = new RepaymentTransaction();
        t.setReference("RP-1");
        t.setGatewayRef(requestId);
        return t;
    }

    @Test
    void postsEmptySignedBodyAndMapsTheTransaction() {
        response = "{\"success\":true,\"message\":\"ok\",\"data\":{\"requestId\":\"23456543\",\"status\":\"SUCCESS\","
                + "\"amount\":5000,\"externalId\":\"EXT-9\"}}";
        OmnichannelClient api = client(true);

        Optional<PaymentStatusResult> r = new OmnichannelPaymentGatewayService(api).markSuccessful(tx("23456543"));

        assertTrue(r.isPresent());
        assertTrue(r.get().isSuccess());
        assertEquals(0, new BigDecimal("5000").compareTo(r.get().amount()));
        assertEquals("EXT-9", r.get().telecomRef());
        assertEquals(1, seen.size());
        Seen s = seen.get(0);
        assertEquals("POST", s.method());
        assertEquals("/api/v1/transactions/23456543/manual-success", s.path());
        assertEquals("", s.body());
        assertNull(s.contentType());
        assertEquals(api.sign(s.timestamp(), ""), s.signature());
    }

    @Test
    void switchedOffMakesNoCall() {
        Optional<PaymentStatusResult> r = new OmnichannelPaymentGatewayService(client(false)).markSuccessful(tx("23456543"));

        assertTrue(r.isEmpty());
        assertTrue(seen.isEmpty());
    }

    @Test
    void refusedByOmnichannelFallsBackToNormalChecks() {
        status = 403;
        response = "{\"success\":false,\"message\":\"Manual success is disabled\",\"errorCode\":\"MANUAL_SUCCESS_DISABLED\"}";

        Optional<PaymentStatusResult> r = new OmnichannelPaymentGatewayService(client(true)).markSuccessful(tx("23456543"));

        assertTrue(r.isEmpty());
        assertEquals(1, seen.size());
    }

    @Test
    void staffMarkPaidReturnsTheTransaction() {
        response = "{\"success\":true,\"data\":{\"requestId\":\"23456543\",\"status\":\"SUCCESS\",\"amount\":10000}}";

        PaymentStatusResult r = new OmnichannelPaymentGatewayService(client(true)).requestManualSuccess(tx("23456543"));

        assertTrue(r.isSuccess());
        assertEquals("/api/v1/transactions/23456543/manual-success", seen.get(0).path());
    }

    @Test
    void staffMarkPaidExplainsEachRefusal() {
        IllegalStateException off = assertThrows(IllegalStateException.class,
                () -> new OmnichannelPaymentGatewayService(client(false)).requestManualSuccess(tx("23456543")));
        assertTrue(off.getMessage().contains("switched off"));

        IllegalStateException noRef = assertThrows(IllegalStateException.class,
                () -> new OmnichannelPaymentGatewayService(client(true)).requestManualSuccess(tx(null)));
        assertTrue(noRef.getMessage().contains("no transaction id"));
        assertTrue(seen.isEmpty());

        status = 403;
        response = "{\"success\":false,\"message\":\"Manual success is disabled\",\"errorCode\":\"MANUAL_SUCCESS_DISABLED\"}";
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> new OmnichannelPaymentGatewayService(client(true)).requestManualSuccess(tx("23456543")));
        assertTrue(refused.getMessage().contains("Omnichannel has switched manual success off"));
    }

    @Test
    void refusedPushKeepsTheRequestIdWhenOmnichannelSendsOne() {
        response = "{\"success\":false,\"message\":\"Telecom unavailable\",\"errorCode\":\"DEPENDENCY_UNAVAILABLE\","
                + "\"data\":{\"requestId\":\"98765\",\"status\":\"FAILED\"}}";
        RepaymentTransaction t = tx(null);
        t.setMsisdn("250785642275");
        t.setLoanAccountNo("000428512");
        t.setAmount(new BigDecimal("20000"));
        t.setCurrency("RWF");

        var r = new OmnichannelPaymentGatewayService(client(true)).requestCollection(t);

        assertFalse(r.accepted());
        assertEquals("98765", r.gatewayRef());
    }

    @Test
    void noRequestIdMakesNoCall() {
        assertTrue(new OmnichannelPaymentGatewayService(client(true)).markSuccessful(tx(null)).isEmpty());
        assertTrue(seen.isEmpty());
    }
}
