package com.inkomoko.mobilebanking.services.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "inkomoko.sms.mode", havingValue = "mock", matchIfMissing = true)
public class MockSmsService implements SmsService {

    @Async
    @Override
    public void send(String msisdn, String message) {
        log.info("[MOCK SMS] to={} ({} chars): {}", msisdn, message.length(), message);
    }
}
