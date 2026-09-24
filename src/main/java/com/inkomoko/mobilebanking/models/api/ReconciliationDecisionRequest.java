package com.inkomoko.mobilebanking.models.api;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Checker approves / rejects a proposed reconciliation action. */
@Getter
@Setter
@NoArgsConstructor
public class ReconciliationDecisionRequest {
    @Size(max = 500)
    private String comment;
    /**
     * Operator name for maker/checker (maker and checker must differ). Optional; defaults to
     * the current admin user. Will come from the JWT principal once security is enabled.
     */
    @Size(max = 100)
    private String operator;
}
