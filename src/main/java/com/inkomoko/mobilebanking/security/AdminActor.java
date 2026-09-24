package com.inkomoko.mobilebanking.security;

/**
 * Who is performing a back-office action (audit log, maker/checker).
 *
 * The admin API is open for now; when JWT security is added, {@link #current()} should return the
 * authenticated principal (e.g. from SecurityContextHolder) — this is the only place to change.
 */
public final class AdminActor {

    public static final String DEFAULT = "admin";

    private AdminActor() {
    }

    /** The acting back-office user. Until JWT is enabled this is always "admin". */
    public static String current() {
        return DEFAULT;
    }

    /** Explicit operator name if given (reconciliation maker/checker), otherwise {@link #current()}. */
    public static String resolve(String operator) {
        return operator == null || operator.isBlank() ? current() : operator.trim();
    }
}
