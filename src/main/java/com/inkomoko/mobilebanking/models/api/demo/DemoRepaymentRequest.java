package com.inkomoko.mobilebanking.models.api.demo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Add a repayment line to a demo loan.
 * applyToBalance=false (default) only records history for the mini statement;
 * true also reduces the loan balance the way a real repayment would.
 */
@Getter
@Setter
@NoArgsConstructor
public class DemoRepaymentRequest {
    @NotNull
    @Positive
    private BigDecimal amount;
    private LocalDate date;
    @Size(max = 60)
    private String reference;
    private Boolean applyToBalance;
}
