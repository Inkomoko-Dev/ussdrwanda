package com.inkomoko.mobilebanking.services.integration;

/** Outbound SMS (receipts, statements). Implementations send asynchronously. */
public interface SmsService {
    void send(String msisdn, String message);
}
