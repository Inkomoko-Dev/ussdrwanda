package com.inkomoko.mobilebanking.security;

import com.inkomoko.mobilebanking.enums.EAdminUserStatus;
import com.inkomoko.mobilebanking.models.admin.AdminUser;
import com.inkomoko.mobilebanking.repositories.AdminUserRepository;
import com.inkomoko.mobilebanking.services.admin.PasswordPolicyService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reads "Authorization: Bearer <jwt>", loads the user and grants their role's privileges as
 * authorities. Privileges are read on every request, so role changes apply immediately.
 * Tokens issued before the last password change are rejected.
 * Not a Spring bean on purpose — it is only added to the security filter chain.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final AdminUserRepository users;
    private final PasswordPolicyService policy;

    public JwtAuthFilter(JwtService jwt, AdminUserRepository users, PasswordPolicyService policy) {
        this.jwt = jwt;
        this.users = users;
        this.policy = policy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwt.verify(header.substring(7).trim())
                    .flatMap(c -> users.findByUsernameIgnoreCase(c.username()).filter(u -> valid(u, c.issuedAt())))
                    .ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private boolean valid(AdminUser u, Instant issuedAt) {
        if (u.getStatus() != EAdminUserStatus.ACTIVE) return false;
        if (u.getPasswordChangedAt() == null) return true;
        Instant changed = u.getPasswordChangedAt().atZone(DateUtility.KIGALI_ZONE).toInstant()
                .truncatedTo(ChronoUnit.SECONDS);
        return !issuedAt.isBefore(changed);
    }

    private void authenticate(AdminUser u) {
        boolean restricted = u.isMustChangePassword() || policy.isExpired(u);
        List<SimpleGrantedAuthority> authorities = restricted ? List.of()
                : u.getRole().effectivePrivileges().stream().map(p -> new SimpleGrantedAuthority(p.name())).toList();
        AdminPrincipal principal = new AdminPrincipal(u.getId(), u.getUsername(), u.getFullName(), restricted);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
