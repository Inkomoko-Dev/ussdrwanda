package com.inkomoko.mobilebanking.models.admin;

import com.inkomoko.mobilebanking.enums.EAdminUserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_user")
@Getter
@Setter
@NoArgsConstructor
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    private String fullName;
    private String email;
    private String phone;

    @ToString.Exclude
    private String passwordHash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private AdminRole role;

    /** Country the user belongs to (null = all countries / head office). */
    private String countryCode;

    @Enumerated(EnumType.STRING)
    private EAdminUserStatus status;

    private int failedAttempts;
    private LocalDateTime lockedUntil;
    private boolean mustChangePassword;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime lastLoginAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
