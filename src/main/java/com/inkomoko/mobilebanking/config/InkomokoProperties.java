package com.inkomoko.mobilebanking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

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
    private Omnichannel omnichannel = new Omnichannel();
    private Sms sms = new Sms();
    private Mail mail = new Mail();
    private Security security = new Security();

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
        private String helpline = "0788363555";
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
        /** omnichannel (default, Inkomoko Omnichannel API) | live (Fineract REST API directly). */
        private String mode = "omnichannel";
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
        /** omnichannel (default, Omnichannel Payment Hub) | live (Efashe merchant API). */
        private String mode = "omnichannel";
        private String baseUrl;
        private String apiKey;
        private String merchantCode;
        private String callbackUrl;
        private String callbackToken;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 10000;
    }

    /** Inkomoko Omnichannel API (HMAC-signed). Used when fineract.mode or payment.mode is "omnichannel" (the default). */
    @Getter @Setter
    public static class Omnichannel {
        private String baseUrl = "http://dev.inkomoko.com:1134/api/v1";
        private String apiKey;
        private String secret;
        private String channel = "USSD";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 15000;
        /**
         * Until the telecom integration is live on the Omnichannel side, every accepted repayment push
         * is followed by POST /transactions/{requestId}/manual-success so it completes without a real
         * MoMo debit. Mirrors Omnichannel's transactions.manual-success.enabled; turn off with it.
         */
        private boolean manualSuccessEnabled = false;
    }

    @Getter @Setter
    public static class Sms {
        private String mode = "mock";
        private String baseUrl;
        private String apiKey;
        private String senderId = "INKOMOKO";
    }

    /** Portal e-mail (default-password delivery). mode=mock logs the message; mode=smtp sends it. */
    @Getter @Setter
    public static class Mail {
        private String mode = "mock";
        private String from = "noreply@inkomoko.example";
        private String host = "";
        private int port = 587;
        private String username = "";
        private String password = "";
        private boolean starttls = true;
    }

    /** Admin portal security. */
    @Getter @Setter
    public static class Security {
        /** HMAC secret for portal JWTs (HS256): at least 32 characters. Set JWT_SECRET in every environment. */
        private String jwtSecret;
        /** Super Administrator created on first start-up (when no portal user exists). */
        private String bootstrapAdminEmail = "admin@yopmail.com";
        private String bootstrapAdminPassword;
        private String bootstrapAdminName = "System Administrator";
    }

}
