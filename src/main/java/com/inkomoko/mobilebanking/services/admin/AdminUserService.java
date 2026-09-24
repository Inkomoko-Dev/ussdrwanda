package com.inkomoko.mobilebanking.services.admin;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.EAdminUserStatus;
import com.inkomoko.mobilebanking.enums.EPrivilege;
import com.inkomoko.mobilebanking.models.PasswordPolicy;
import com.inkomoko.mobilebanking.models.admin.AdminPasswordHistory;
import com.inkomoko.mobilebanking.models.admin.AdminRole;
import com.inkomoko.mobilebanking.models.admin.AdminUser;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.*;
import com.inkomoko.mobilebanking.repositories.AdminPasswordHistoryRepository;
import com.inkomoko.mobilebanking.repositories.AdminRoleRepository;
import com.inkomoko.mobilebanking.repositories.AdminUserRepository;
import com.inkomoko.mobilebanking.repositories.CountryConfigRepository;
import com.inkomoko.mobilebanking.security.AdminPrincipal;
import com.inkomoko.mobilebanking.security.AuthException;
import com.inkomoko.mobilebanking.security.JwtService;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.CountryService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Portal users: login with lockout, password changes, and user administration. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String INVALID = "Invalid username or password";

    private final AdminUserRepository users;
    private final AdminRoleRepository roles;
    private final AdminPasswordHistoryRepository history;
    private final CountryConfigRepository countries;
    private final PasswordPolicyService policy;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final CountryService countryService;
    private final InkomokoProperties props;

    // ── Authentication ───────────────────────────────────────────────────

    @Transactional(noRollbackFor = AuthException.class)
    public LoginResponse login(LoginRequest req) {
        String login = req.username().trim();
        AdminUser u = (login.contains("@") ? users.findByEmailIgnoreCase(login) : users.findByUsernameIgnoreCase(login))
                .or(() -> users.findByUsernameIgnoreCase(login))
                .orElse(null);
        LocalDateTime now = DateUtility.nowKigali();
        if (u == null) {
            audit.admin(req.username(), "PORTAL_LOGIN_FAILED", null, false, "unknown user");
            throw new AuthException(INVALID);
        }
        if (u.getStatus() != EAdminUserStatus.ACTIVE) {
            audit.admin(u.getUsername(), "PORTAL_LOGIN_FAILED", null, false, "disabled");
            throw new AuthException("This account is disabled. Contact your administrator.");
        }
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            throw new AuthException("Account locked after too many failed attempts. Try again after "
                    + u.getLockedUntil().format(DateTimeFormatter.ofPattern("HH:mm")) + ".");
        }
        PasswordPolicy p = policy.get();
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            u.setFailedAttempts(u.getFailedAttempts() + 1);
            String detail = "attempt " + u.getFailedAttempts();
            if (u.getFailedAttempts() >= p.getMaxFailedAttempts()) {
                u.setLockedUntil(now.plusMinutes(p.getLockoutMinutes()));
                u.setFailedAttempts(0);
                detail = "locked for " + p.getLockoutMinutes() + " min";
            }
            users.save(u);
            audit.admin(u.getUsername(), "PORTAL_LOGIN_FAILED", null, false, detail);
            throw new AuthException(INVALID);
        }
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        u.setLastLoginAt(now);
        users.save(u);
        audit.admin(u.getUsername(), "PORTAL_LOGIN", null, true, null);
        return new LoginResponse(jwt.issue(u, p.getSessionMinutes()), u.isMustChangePassword(), policy.isExpired(u));
    }

    public MeResponse me(AdminPrincipal principal) {
        AdminUser u = require(principal.id());
        List<EPrivilege> privileges = principal.restricted() ? List.of()
                : u.getRole().effectivePrivileges().stream().sorted().toList();
        CountrySummary country = countryService.active()
                .map(c -> new CountrySummary(c.getCode(), c.getName(), c.getCurrency())).orElse(null);
        boolean demo = !"live".equalsIgnoreCase(props.getFineract().getMode().trim());
        return new MeResponse(view(u), privileges, u.isMustChangePassword(), policy.isExpired(u), demo,
                countryService.serviceCode(), country, country == null, policy.view());
    }

    @Transactional
    public LoginResponse changePassword(AdminPrincipal principal, ChangePasswordRequest req) {
        AdminUser u = require(principal.id());
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        policy.enforce(req.newPassword(), u.getUsername(), u);
        setPassword(u, req.newPassword());
        u.setMustChangePassword(false);
        users.save(u);
        audit.admin(u.getUsername(), "PORTAL_PASSWORD_CHANGED", u.getUsername(), true, null);
        return new LoginResponse(jwt.issue(u, policy.get().getSessionMinutes()), false, false);
    }

    // ── Administration ───────────────────────────────────────────────────

    public List<UserView> list() {
        return users.findAllByOrderByFullNameAsc().stream().map(this::view).toList();
    }

    @Transactional
    public UserWithTempPassword create(CreateUserRequest r, String by) {
        String username = r.username().trim().toLowerCase();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException("Username already exists: " + username);
        }
        if (r.email() != null && !r.email().isBlank() && users.existsByEmailIgnoreCase(r.email().trim())) {
            throw new IllegalStateException("E-mail already used by another user: " + r.email().trim());
        }
        AdminUser u = new AdminUser();
        u.setUsername(username);
        u.setFullName(r.fullName().trim());
        u.setEmail(blankToNull(r.email()));
        u.setPhone(blankToNull(r.phone()));
        u.setRole(role(r.roleId()));
        u.setCountryCode(country(r.countryCode()));
        u.setStatus(EAdminUserStatus.ACTIVE);
        boolean generated = r.password() == null || r.password().isBlank();
        String password = generated ? policy.generateTemporary() : r.password();
        if (!generated) policy.enforce(password, username, null);
        LocalDateTime now = DateUtility.nowKigali();
        u.setPasswordHash(encoder.encode(password));
        u.setPasswordChangedAt(now);
        u.setMustChangePassword(true);
        u.setCreatedBy(by);
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        u = users.save(u);
        audit.admin(by, "PORTAL_USER_CREATED", username, true, "role=" + u.getRole().getName());
        return new UserWithTempPassword(view(u), generated ? password : null);
    }

    @Transactional
    public UserView update(Long id, UpdateUserRequest r, AdminPrincipal actor) {
        AdminUser u = require(id);
        if (r.fullName() != null && !r.fullName().isBlank()) u.setFullName(r.fullName().trim());
        if (r.email() != null) {
            String email = blankToNull(r.email());
            Long uid = u.getId();
            if (email != null && users.findByEmailIgnoreCase(email).filter(o -> !o.getId().equals(uid)).isPresent()) {
                throw new IllegalStateException("E-mail already used by another user: " + email);
            }
            u.setEmail(email);
        }
        if (r.phone() != null) u.setPhone(blankToNull(r.phone()));
        if (r.countryCode() != null) u.setCountryCode(country(r.countryCode()));
        boolean losesAdmin = false;
        if (r.roleId() != null && !r.roleId().equals(u.getRole().getId())) {
            AdminRole newRole = role(r.roleId());
            losesAdmin = u.getRole().isSystemRole() && !newRole.isSystemRole();
            u.setRole(newRole);
        }
        if (r.status() != null && r.status() != u.getStatus()) {
            if (u.getId().equals(actor.id()) && r.status() == EAdminUserStatus.DISABLED) {
                throw new IllegalStateException("You cannot disable your own account");
            }
            losesAdmin |= u.getRole().isSystemRole() && r.status() == EAdminUserStatus.DISABLED;
            u.setStatus(r.status());
        }
        if (losesAdmin && activeSuperAdmins() <= 1) {
            throw new IllegalStateException("At least one active Super Administrator is required");
        }
        u.setUpdatedAt(DateUtility.nowKigali());
        u = users.save(u);
        audit.admin(actor.username(), "PORTAL_USER_UPDATED", u.getUsername(), true,
                "role=" + u.getRole().getName() + " status=" + u.getStatus());
        return view(u);
    }

    @Transactional
    public UserWithTempPassword resetPassword(Long id, String by) {
        AdminUser u = require(id);
        String temp = policy.generateTemporary();
        setPassword(u, temp);
        u.setMustChangePassword(true);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        u = users.save(u);
        audit.admin(by, "PORTAL_PASSWORD_RESET", u.getUsername(), true, null);
        return new UserWithTempPassword(view(u), temp);
    }

    @Transactional
    public UserView unlock(Long id, String by) {
        AdminUser u = require(id);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        u.setUpdatedAt(DateUtility.nowKigali());
        audit.admin(by, "PORTAL_USER_UNLOCKED", u.getUsername(), true, null);
        return view(users.save(u));
    }

    /**
     * Ensures the Super Administrator exists (username "admin", e-mail + password from
     * inkomoko.security.bootstrap-admin-*). Created when missing. While the account still
     * requires a first sign-in password change, the configured password is applied so a
     * corrected bootstrap password works without wiping a password the admin already chose.
     */
    @Transactional
    public void bootstrapAdminIfNone() {
        InkomokoProperties.Security sec = props.getSecurity();
        String password = sec.getBootstrapAdminPassword();
        String email = blankToNull(sec.getBootstrapAdminEmail());
        if (password == null || password.isBlank()) {
            if (users.count() == 0) {
                throw new IllegalStateException("No portal users exist and inkomoko.security.bootstrap-admin-password is not set");
            }
            return;
        }
        AdminUser existing = users.findByUsernameIgnoreCase("admin")
                .or(() -> email == null ? java.util.Optional.empty() : users.findByEmailIgnoreCase(email))
                .orElse(null);
        if (existing != null) {
            if (!existing.isMustChangePassword()) return;
            boolean passwordMatches = encoder.matches(password, existing.getPasswordHash());
            if (passwordMatches && existing.getFailedAttempts() == 0 && existing.getLockedUntil() == null) return;
            LocalDateTime now = DateUtility.nowKigali();
            if (!passwordMatches) {
                existing.setPasswordHash(encoder.encode(password));
                existing.setPasswordChangedAt(now);
            }
            if (email != null && (existing.getEmail() == null || existing.getEmail().isBlank())) {
                existing.setEmail(email);
            }
            existing.setStatus(EAdminUserStatus.ACTIVE);
            existing.setFailedAttempts(0);
            existing.setLockedUntil(null);
            existing.setUpdatedAt(now);
            users.save(existing);
            audit.system("PORTAL_ADMIN_BOOTSTRAPPED", existing.getEmail(), true, "username=admin password refreshed");
            return;
        }
        AdminRole superRole = roles.findFirstBySystemRoleTrue()
                .orElseThrow(() -> new IllegalStateException("Super Administrator role missing"));
        LocalDateTime now = DateUtility.nowKigali();
        AdminUser u = new AdminUser();
        u.setUsername("admin");
        u.setFullName(sec.getBootstrapAdminName());
        u.setEmail(email);
        u.setRole(superRole);
        u.setStatus(EAdminUserStatus.ACTIVE);
        u.setPasswordHash(encoder.encode(password));
        u.setPasswordChangedAt(now);
        u.setMustChangePassword(true);
        u.setCreatedBy("bootstrap");
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        users.save(u);
        audit.system("PORTAL_ADMIN_BOOTSTRAPPED", u.getEmail(), true, "username=admin");
    }

    public String issueToken(AdminUser u) {
        return jwt.issue(u, policy.get().getSessionMinutes());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void setPassword(AdminUser u, String raw) {
        LocalDateTime now = DateUtility.nowKigali();
        history.save(new AdminPasswordHistory(u.getId(), u.getPasswordHash(), now));
        u.setPasswordHash(encoder.encode(raw));
        u.setPasswordChangedAt(now);
        u.setUpdatedAt(now);
    }

    private long activeSuperAdmins() {
        return users.findAll().stream()
                .filter(x -> x.getStatus() == EAdminUserStatus.ACTIVE && x.getRole().isSystemRole()).count();
    }

    private AdminUser require(Long id) {
        return users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    private AdminRole role(Long id) {
        return roles.findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found: " + id));
    }

    private String country(String code) {
        if (code == null || code.isBlank()) return null;
        String c = code.trim().toUpperCase();
        if (!countries.existsById(c)) throw new IllegalArgumentException("Unknown country: " + code);
        return c;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public UserView view(AdminUser u) {
        boolean locked = u.getLockedUntil() != null && u.getLockedUntil().isAfter(DateUtility.nowKigali());
        return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(),
                u.getRole().getId(), u.getRole().getName(), u.getCountryCode(), u.getStatus(), locked,
                u.getLockedUntil(), u.isMustChangePassword(), u.getLastLoginAt(), u.getCreatedAt(), u.getCreatedBy());
    }
}
