package com.inkomoko.mobilebanking.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Who is performing a back-office action (audit log, maker/checker): the logged-in portal user. */
public final class AdminActor {

    public static final String SYSTEM = "system";

    private AdminActor() {
    }

    public static Optional<AdminPrincipal> principal() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof AdminPrincipal p ? Optional.of(p) : Optional.empty();
    }

    /** Username of the logged-in portal user, or "system" outside a portal request. */
    public static String current() {
        return principal().map(AdminPrincipal::username).orElse(SYSTEM);
    }
}
