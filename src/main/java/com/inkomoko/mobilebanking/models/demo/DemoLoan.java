package com.inkomoko.mobilebanking.models.demo;

import com.inkomoko.mobilebanking.enums.ELoanStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Demo loan. Total outstanding = principal + interest outstanding, mirroring Fineract's loan summary.
 */
@Entity
@Table(name = "demo_loan")
@Getter
@Setter
@NoArgsConstructor
public class DemoLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long clientId;

    @Column(unique = true)
    private String accountNo;

    private String productName;

    @Enumerated(EnumType.STRING)
    private ELoanStatus status;

    private BigDecimal principalOutstanding = BigDecimal.ZERO;
    private BigDecimal interestOutstanding = BigDecimal.ZERO;
    private BigDecimal totalOverdue = BigDecimal.ZERO;
    private BigDecimal installment = BigDecimal.ZERO;
    private LocalDate nextDueDate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BigDecimal getTotalOutstanding() {
        BigDecimal p = principalOutstanding == null ? BigDecimal.ZERO : principalOutstanding;
        BigDecimal i = interestOutstanding == null ? BigDecimal.ZERO : interestOutstanding;
        return p.add(i);
    }
}
