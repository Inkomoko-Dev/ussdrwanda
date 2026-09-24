package com.inkomoko.mobilebanking.models.demo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Demo core-banking client (demo mode only — replaced by Fineract clients in live mode). */
@Entity
@Table(name = "demo_client")
@Getter
@Setter
@NoArgsConstructor
public class DemoClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String msisdn;

    private String displayName;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
