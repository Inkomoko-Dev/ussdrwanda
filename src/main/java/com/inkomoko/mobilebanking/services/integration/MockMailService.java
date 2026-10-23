package com.inkomoko.mobilebanking.services.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Local development: the message is written to the log instead of leaving the machine. */
@Slf4j
@Service
@ConditionalOnProperty(name = "inkomoko.mail.mode", havingValue = "mock", matchIfMissing = true)
public class MockMailService implements MailService {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[MOCK EMAIL] to={} subject={}\n{}", to, subject, body);
    }
}
