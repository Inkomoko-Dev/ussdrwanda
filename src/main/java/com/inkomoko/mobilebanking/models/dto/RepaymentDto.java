package com.inkomoko.mobilebanking.models.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One repayment line for the mini statement. */
public record RepaymentDto(Long transactionId, LocalDate date, BigDecimal amount, String reference) {
}
