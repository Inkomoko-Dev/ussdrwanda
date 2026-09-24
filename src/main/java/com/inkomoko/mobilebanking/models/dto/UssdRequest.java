package com.inkomoko.mobilebanking.models.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Payload POSTed by the FDI (Efashe) USSD gateway on every step of a session.
 * {@code userInput} is empty on the first request, then the cumulative input joined with '*'.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UssdRequest {
    private String sessionId;
    private String gwRef;
    private String gwTstamp;
    private String msisdn;
    private String networkCode;
    private String serviceCode;
    @ToString.Exclude
    private String userInput;   // may contain a PIN — never log
    private String serviceId;
    private String tenantId;
}
