package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * SMS via the Efashe SMS gateway. Endpoint and payload are provisional —
 * align with the Efashe SMS API specification.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "inkomoko.sms.mode", havingValue = "live")
public class EfasheSmsService implements SmsService {

    private final RestClient client;
    private final String senderId;

    public EfasheSmsService(InkomokoProperties props) {
        InkomokoProperties.Sms cfg = props.getSms();
        this.senderId = cfg.getSenderId();
        this.client = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeaders(h -> h.setBearerAuth(cfg.getApiKey()))
                .build();
    }

    @Async
    @Override
    public void send(String msisdn, String message) {
        try {
            client.post().uri("/sms/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("to", msisdn, "sender", senderId, "message", message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("SMS send failed to={} error={}", msisdn, e.getMessage());
        }
    }
}
