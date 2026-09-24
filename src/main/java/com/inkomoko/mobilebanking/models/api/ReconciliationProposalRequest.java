package com.inkomoko.mobilebanking.models.api;

import com.inkomoko.mobilebanking.enums.EReconciliationAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maker proposes a reconciliation action on a repayment. */
@Getter
@Setter
@NoArgsConstructor
public class ReconciliationProposalRequest {
    @NotNull
    private EReconciliationAction action;
    @Size(max = 500)
    private String comment;
    /** Required for MARK_POSTED. */
    private String fineractTxnId;
}
