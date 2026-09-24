package com.inkomoko.mobilebanking.models.admin;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_password_history")
@Getter
@Setter
@NoArgsConstructor
public class AdminPasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String passwordHash;
    private LocalDateTime createdAt;

    public AdminPasswordHistory(Long userId, String passwordHash, LocalDateTime createdAt) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }
}
