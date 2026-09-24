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
}
