package com.inkomoko.mobilebanking.services.integration;

/** Outbound portal e-mail. Implementations deliver before returning, so a failure can roll back the caller. */
public interface MailService {
    void send(String to, String subject, String body);
}
