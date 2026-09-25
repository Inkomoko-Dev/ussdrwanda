package com.inkomoko.mobilebanking.models.api.portal;

import com.inkomoko.mobilebanking.enums.EAdminUserStatus;
import com.inkomoko.mobilebanking.enums.EPinStatus;
import com.inkomoko.mobilebanking.enums.EPrivilege;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Request / response shapes of the admin portal API. */
public final class PortalDtos {

    private PortalDtos() {
    }

    // ── Auth ─────────────────────────────────────────────────────────────
    /** username: portal username or e-mail address. */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record LoginResponse(String token, boolean mustChangePassword, boolean passwordExpired) {
    }

    public record CountrySummary(String code, String name, String currency) {
    }

    /** countrySetupRequired: no active country yet — a Super Administrator must choose and configure one. */
    public record MeResponse(UserView user, List<EPrivilege> privileges, boolean mustChangePassword,
                             boolean passwordExpired, boolean demoMode, String ussdServiceCode,
                             CountrySummary country, boolean countrySetupRequired, PasswordPolicyView passwordPolicy) {
    }

    // ── Users ────────────────────────────────────────────────────────────
    public record UserView(Long id, String username, String fullName, String email, String phone, Long roleId,
                           String roleName, String countryCode, EAdminUserStatus status, boolean locked,
                           LocalDateTime lockedUntil, boolean mustChangePassword, LocalDateTime lastLoginAt,
                           LocalDateTime createdAt, String createdBy) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 60) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "letters, digits, . _ - only")
            String username,
            @NotBlank @Size(max = 120) String fullName,
            @Email @Size(max = 120) String email,
            @Size(max = 20) String phone,
            @NotNull Long roleId,
            @Size(max = 2) String countryCode,
            /** Optional: a temporary password is generated when blank. */
            String password) {
    }

    public record UpdateUserRequest(@Size(max = 120) String fullName, @Email @Size(max = 120) String email,
                                    @Size(max = 20) String phone, Long roleId, @Size(max = 2) String countryCode,
                                    EAdminUserStatus status) {
    }

    /** Returned once when a user is created or a password reset: share it with the user securely. */
    public record UserWithTempPassword(UserView user, String temporaryPassword) {
    }

    // ── Roles & privileges ───────────────────────────────────────────────
    public record PrivilegeView(EPrivilege code, String category, String description) {
    }

    public record RoleView(Long id, String name, String description, boolean systemRole,
                           Set<EPrivilege> privileges, long userCount) {
    }

    public record RoleRequest(@NotBlank @Size(max = 60) String name, @Size(max = 255) String description,
                              @NotNull Set<EPrivilege> privileges) {
    }

    // ── Password policy ──────────────────────────────────────────────────
    public record PasswordPolicyView(int minLength, boolean requireUpper, boolean requireLower, boolean requireDigit,
                                     boolean requireSpecial, int expiryDays, int historyCount, int maxFailedAttempts,
                                     int lockoutMinutes, int sessionMinutes, LocalDateTime updatedAt, String updatedBy) {
    }

    public record PasswordPolicyRequest(@Min(8) @Max(64) int minLength, boolean requireUpper, boolean requireLower,
                                        boolean requireDigit, boolean requireSpecial,
                                        @Min(0) @Max(365) int expiryDays, @Min(0) @Max(24) int historyCount,
                                        @Min(1) @Max(20) int maxFailedAttempts, @Min(1) @Max(1440) int lockoutMinutes,
                                        @Min(5) @Max(720) int sessionMinutes) {
    }

    // ── Countries ────────────────────────────────────────────────────────
    public record CountryView(String code, String name, String dialCode, Integer nationalNumberLength, String currency,
                              String timezone, List<String> languages, List<String> mobileNetworks,
                              String helpline, String ussdServiceCode, BigDecimal minRepayment, BigDecimal maxRepayment,
                              boolean active) {
    }

    public record CountryRequest(@Size(max = 80) String name,
                                 @Size(max = 5) @Pattern(regexp = "[0-9]*") String dialCode,
                                 @Min(6) @Max(12) Integer nationalNumberLength,
                                 @Size(min = 3, max = 3) String currency,
                                 @Size(max = 40) String timezone,
                                 @Size(min = 1, message = "choose at least one language") List<String> languages,
                                 List<@Size(max = 30) String> mobileNetworks,
                                 @Size(max = 40) String helpline,
                                 @Size(max = 20) String ussdServiceCode,
                                 @Positive BigDecimal minRepayment,
                                 @Positive BigDecimal maxRepayment) {
    }

    // ── Customers ────────────────────────────────────────────────────────
    public record CustomerView(String msisdn, Long clientId, String clientName, EPinStatus status,
                               String registrationChannel, String onboardedBy, LocalDateTime onboardedAt,
                               LocalDateTime lastLoginAt, LocalDateTime lockedAt, LocalDateTime createdAt,
                               String language, String email, boolean termsAccepted) {
    }

    /** Portal customer beside the matching core-banking client, when one exists. */
    public record CustomerDetails(CustomerView customer, Long coreClientId, String coreName, String corePhone,
                                  String coreEmail, Boolean coreActive, boolean inCoreBanking) {
    }

    public record UpdateCustomerRequest(@NotBlank @Size(max = 150) String name,
                                        @NotBlank String phone,
                                        @Email @Size(max = 120) String email) {
    }

    public record ClientInfo(Long clientId, String displayName, boolean active) {
    }

    public record CustomerLookup(String msisdn, boolean foundInCoreBanking, ClientInfo client,
                                 List<LoanSummaryDto> loans, CustomerView registration) {
    }

    public record OnboardRequest(@NotBlank String msisdn, Boolean sendSms) {
    }
}
