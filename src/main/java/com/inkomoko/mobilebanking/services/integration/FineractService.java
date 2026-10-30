package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.models.dto.ClientDto;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.models.dto.RepaymentDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Gateway to Inkomoko's core lending system (Apache Fineract). Balances are always read live —
 * nothing here is cached. Implementations: {@link OmnichannelFineractService} (default, through the
 * Inkomoko Omnichannel API) and {@link FineractRestService} (Fineract REST API directly).
 */
public interface FineractService {

    /** Matches the caller's MSISDN against the Fineract client record (ToR 3.1(vii)). */
    Optional<ClientDto> findClientByMsisdn(String msisdn);

    Optional<ClientDto> findClientById(Long clientId);

    /** Updates the client's name, mobile number and e-mail in core banking. */
    void updateClient(Long clientId, String displayName, String mobileNo, String email);

    /** All loans of the client (basic fields + mapped status). */
    List<LoanSummaryDto> getClientLoans(Long clientId);

    /** Real-time loan balance incl. overdue, next due date and installment. */
    LoanSummaryDto getLoanDetails(Long loanId);

    /** Repayments on the loan, newest first. */
    List<RepaymentDto> getRepayments(Long loanId, int limit);

    /** Fineract transaction id of a repayment already posted with this externalId, if any. */
    Optional<String> findRepaymentByExternalId(Long loanId, String externalId);

    /** Posts a repayment; returns the Fineract transaction id. */
    String postRepayment(Long loanId, BigDecimal amount, LocalDate date, String externalId, String receiptNumber,
                         String note);

    /** Undoes a repayment already posted to the loan. */
    void reverseRepayment(Long loanId, String fineractTxnId);
}
