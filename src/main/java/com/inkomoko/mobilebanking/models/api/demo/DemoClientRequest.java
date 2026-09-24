package com.inkomoko.mobilebanking.models.api.demo;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Create / update a demo client (an "allowed number").
 * On create, msisdn is required; sampleLoans (default true) adds an active loan and a loan in arrears.
 * On update, null fields are left unchanged.
 */
@Getter
@Setter
@NoArgsConstructor
public class DemoClientRequest {
    @Size(max = 20)
    private String msisdn;
    @Size(max = 150)
    private String displayName;
    private Boolean active;
    private Boolean sampleLoans;
}
