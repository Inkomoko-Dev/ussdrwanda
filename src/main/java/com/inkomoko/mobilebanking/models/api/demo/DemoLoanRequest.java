package com.inkomoko.mobilebanking.models.api.demo;

import com.inkomoko.mobilebanking.enums.ELoanStatus;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create / update a demo loan. On update, null fields are left unchanged.
 * status: UNDER_REVIEW | APPROVED | ACTIVE | IN_ARREARS | SETTLED | REJECTED | WITHDRAWN | CLOSED
 */
@Getter
@Setter
@NoArgsConstructor
public class DemoLoanRequest {
    @Size(max = 50)
    private String accountNo;          // generated when blank
    @Size(max = 100)
    private String productName;
    private ELoanStatus status;
    @PositiveOrZero
    private BigDecimal principalOutstanding;
    @PositiveOrZero
    private BigDecimal interestOutstanding;
    @PositiveOrZero
    private BigDecimal totalOverdue;
    @PositiveOrZero
    private BigDecimal installment;
    private LocalDate nextDueDate;
}
