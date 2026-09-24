package com.inkomoko.mobilebanking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * All platform settings under the {@code inkomoko.*} prefix (see application.properties).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "inkomoko")
public class InkomokoProperties {

    private Ussd ussd = new Ussd();
    private Support support = new Support();
    private Repayment repayment = new Repayment();
    private Fineract fineract = new Fineract();
    private Payment payment = new Payment();
    private Sms sms = new Sms();
    private Demo demo = new Demo();

    @Getter @Setter
    public static class Ussd {
        private String serviceCode = "662*105";
        private int sessionTimeoutSeconds = 60;
        private int maxPinAttempts = 3;
        private int pinLength = 4;
        private int maxResponseLength = 182;
        private int maxLoansListed = 5;
    }

    @Getter @Setter
    public static class Support {
        private String helpline = "+250 XXX XXX XXX";
    }

    @Getter @Setter
    public static class Repayment {
        private BigDecimal minAmount = new BigDecimal("500");
        private BigDecimal maxAmount = new BigDecimal("5000000");
        private String currency = "RWF";
        private int duplicateWindowMinutes = 5;
        private int pendingCheckAfterMinutes = 3;
        private int pendingExpiryMinutes = 60;
        private int maxPostingAttempts = 5;
        private long reconciliationIntervalMs = 60000;
    }

    @Getter @Setter
    public static class Fineract {
        /** demo (default, pilot on *662*105#) | live (Fineract, *311#). "mock" is treated as demo. */
        private String mode = "demo";
        private String baseUrl;
        private String tenantId = "default";
        private String username;
        private String password;
        private Long paymentTypeId = 1L;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 8000;
    }

    @Getter @Setter
    public static class Payment {
        private String mode = "mock";
        private String baseUrl;
        private String apiKey;
        private String merchantCode;
        private String callbackUrl;
        private String callbackToken;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 10000;
        private int mockDelaySeconds = 5;
    }

    @Getter @Setter
    public static class Sms {
        private String mode = "mock";
        private String baseUrl;
        private String apiKey;
        private String senderId = "INKOMOKO";
    }

    /** Demo core banking (only used when fineract.mode is not live). */
    @Getter @Setter
    public static class Demo {
        /** Phone numbers registered as demo clients (with sample loans) at startup. */
        private List<String> allowedMsisdns = new ArrayList<>();
    }

}
