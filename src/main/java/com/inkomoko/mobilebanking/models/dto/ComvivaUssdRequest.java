package com.inkomoko.mobilebanking.models.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Payload POSTed by the Comviva USSD gateway used by MTN and Airtel Rwanda.
 *
 * <pre>
 * {
 *   "sessionId": "174438462003499444546",
 *   "input": "311",
 *   "newRequest": "1",
 *   "msisdn": "2507XXXXXXXX"
 * }
 * </pre>
 *
 * {@code newRequest} is 1 on the first dial and 0 on every later step. First-dial {@code input}
 * is the shortcode (e.g. {@code 311}); later steps send the latest menu choice only.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComvivaUssdRequest {
    private String sessionId;
    @ToString.Exclude
    private String input;          // may contain a PIN — never log
    /** Gateway may send 1/0 as a number or as the string {@code "1"}/{@code "0"}. */
    private String newRequest;
    private String msisdn;

    public Integer newRequestFlag() {
        if (newRequest == null || newRequest.isBlank()) return null;
        String v = newRequest.trim();
        if ("1".equals(v)) return 1;
        if ("0".equals(v)) return 0;
        return null;
    }
}
