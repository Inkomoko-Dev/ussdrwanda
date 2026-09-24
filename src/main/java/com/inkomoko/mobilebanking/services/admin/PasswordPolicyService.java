package com.inkomoko.mobilebanking.services.admin;

import com.inkomoko.mobilebanking.models.PasswordPolicy;
import com.inkomoko.mobilebanking.models.admin.AdminPasswordHistory;
import com.inkomoko.mobilebanking.models.admin.AdminUser;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.PasswordPolicyRequest;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.PasswordPolicyView;
import com.inkomoko.mobilebanking.repositories.AdminPasswordHistoryRepository;
import com.inkomoko.mobilebanking.repositories.PasswordPolicyRepository;
import com.inkomoko.mobilebanking.utils.DateUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Portal password policy: complexity, history, expiry, lockout and session length. */
@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%*?-_+=";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordPolicyRepository repo;
    private final AdminPasswordHistoryRepository historyRepo;
    private final PasswordEncoder encoder;

    public PasswordPolicy get() {
        return repo.findById(1).orElseGet(() -> {
            PasswordPolicy p = new PasswordPolicy();
            p.setId(1);
            p.setMinLength(12);
            p.setRequireUpper(true);
            p.setRequireLower(true);
            p.setRequireDigit(true);
            p.setRequireSpecial(true);
            p.setExpiryDays(90);
            p.setHistoryCount(5);
            p.setMaxFailedAttempts(5);
            p.setLockoutMinutes(30);
            p.setSessionMinutes(60);
            return p;
        });
    }

    public PasswordPolicyView view() {
        return toView(get());
    }

    @Transactional
    public PasswordPolicyView update(PasswordPolicyRequest r, String by) {
        PasswordPolicy p = get();
        p.setMinLength(r.minLength());
        p.setRequireUpper(r.requireUpper());
        p.setRequireLower(r.requireLower());
        p.setRequireDigit(r.requireDigit());
        p.setRequireSpecial(r.requireSpecial());
        p.setExpiryDays(r.expiryDays());
        p.setHistoryCount(r.historyCount());
        p.setMaxFailedAttempts(r.maxFailedAttempts());
        p.setLockoutMinutes(r.lockoutMinutes());
        p.setSessionMinutes(r.sessionMinutes());
        p.setUpdatedAt(DateUtility.nowKigali());
        p.setUpdatedBy(by);
        return toView(repo.save(p));
    }

    /** Human-readable reasons the password is not acceptable (empty = OK). */
    public List<String> validate(String password, String username, AdminUser existing) {
        PasswordPolicy p = get();
        List<String> errors = new ArrayList<>();
        String pw = password == null ? "" : password;
        if (pw.length() < p.getMinLength()) errors.add("at least " + p.getMinLength() + " characters");
        if (p.isRequireUpper() && pw.chars().noneMatch(Character::isUpperCase)) errors.add("an uppercase letter");
        if (p.isRequireLower() && pw.chars().noneMatch(Character::isLowerCase)) errors.add("a lowercase letter");
        if (p.isRequireDigit() && pw.chars().noneMatch(Character::isDigit)) errors.add("a digit");
        if (p.isRequireSpecial() && pw.chars().allMatch(Character::isLetterOrDigit)) errors.add("a special character");
        if (username != null && username.length() >= 3 && pw.toLowerCase().contains(username.toLowerCase())) {
            errors.add("must not contain the username");
        }
        if (existing != null && errors.isEmpty()) {
            if (encoder.matches(pw, existing.getPasswordHash())) {
                errors.add("must differ from the current password");
            } else if (p.getHistoryCount() > 0) {
                for (AdminPasswordHistory h : historyRepo.findByUserIdOrderByCreatedAtDesc(existing.getId(),
                        PageRequest.of(0, p.getHistoryCount()))) {
                    if (encoder.matches(pw, h.getPasswordHash())) {
                        errors.add("must not reuse one of your last " + p.getHistoryCount() + " passwords");
                        break;
                    }
                }
            }
        }
        return errors;
    }

    /** Throws IllegalArgumentException listing every unmet rule. */
    public void enforce(String password, String username, AdminUser existing) {
        List<String> errors = validate(password, username, existing);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Password must have: " + String.join(", ", errors));
        }
    }

    public boolean isExpired(AdminUser u) {
        int days = get().getExpiryDays();
        return days > 0 && u.getPasswordChangedAt() != null
                && u.getPasswordChangedAt().plusDays(days).isBefore(DateUtility.nowKigali());
    }

    /** Random password that satisfies the current policy (for new users and resets). */
    public String generateTemporary() {
        PasswordPolicy p = get();
        int len = Math.max(p.getMinLength(), 12);
        List<Character> chars = new ArrayList<>();
        chars.add(pick(UPPER));
        chars.add(pick(LOWER));
        chars.add(pick(DIGITS));
        chars.add(pick(SPECIAL));
        String all = UPPER + LOWER + DIGITS + SPECIAL;
        while (chars.size() < len) chars.add(pick(all));
        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    private static char pick(String s) {
        return s.charAt(RANDOM.nextInt(s.length()));
    }

    private static PasswordPolicyView toView(PasswordPolicy p) {
        return new PasswordPolicyView(p.getMinLength(), p.isRequireUpper(), p.isRequireLower(), p.isRequireDigit(),
                p.isRequireSpecial(), p.getExpiryDays(), p.getHistoryCount(), p.getMaxFailedAttempts(),
                p.getLockoutMinutes(), p.getSessionMinutes(), p.getUpdatedAt(), p.getUpdatedBy());
    }
}
