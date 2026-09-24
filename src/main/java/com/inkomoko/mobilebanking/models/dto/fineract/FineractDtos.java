package com.inkomoko.mobilebanking.models.dto.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Subset of Apache Fineract REST API payloads (v1) used by the USSD platform.
 * Fineract returns dates as [yyyy, M, d] arrays. Field mapping to be confirmed against
 * Inkomoko's Fineract sandbox.
 */
public final class FineractDtos {

    private FineractDtos() {
    }

    /** GET /search?query=..&resource=clients */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResult(Long entityId, String entityType, String entityName,
                               String entityAccountNo, String entityMobileNo) {
    }

    /** GET /clients/{clientId} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Client(Long id, String displayName, String mobileNo, String emailAddress, Boolean active, Status status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(Integer id, String code, String value) {
    }

    /** GET /clients/{clientId}/accounts */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClientAccounts(List<LoanAccount> loanAccounts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoanAccount(Long id, String accountNo, String productName, Status status,
                              Boolean inArrears, BigDecimal loanBalance, BigDecimal originalLoan) {
    }

    /** GET /loans/{loanId}?associations=repaymentSchedule,transactions */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Loan(Long id, String accountNo, Long clientId, String loanProductName, Status status,
                       Boolean inArrears, Summary summary, Schedule repaymentSchedule,
                       List<Transaction> transactions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(BigDecimal principalOutstanding, BigDecimal totalOutstanding, BigDecimal totalOverdue) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Schedule(List<Period> periods) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Period(Integer period, List<Integer> dueDate, BigDecimal totalDueForPeriod,
                         BigDecimal totalOutstandingForPeriod, Boolean complete) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transaction(Long id, TransactionType type, List<Integer> date, BigDecimal amount,
                              String externalId, Boolean manuallyReversed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransactionType(Integer id, String code, String value, Boolean repayment) {
    }

    /** Response of POST /loans/{loanId}/transactions?command=repayment */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommandResult(Long officeId, Long clientId, Long loanId, Long resourceId) {
    }
}
