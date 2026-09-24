package com.inkomoko.mobilebanking.models.demo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Demo repayment (mini statement line). {@code reference} plays the role of Fineract's externalId. */
@Entity
@Table(name = "demo_repayment")
@Getter
@Setter
@NoArgsConstructor
public class DemoRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long loanId;
    private LocalDate txnDate;
    private BigDecimal amount;
    private String reference;
    private LocalDateTime createdAt;
}
