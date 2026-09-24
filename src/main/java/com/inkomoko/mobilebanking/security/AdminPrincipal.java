package com.inkomoko.mobilebanking.security;

/**
 * Authenticated portal user. {@code restricted} = must change (or has an expired) password:
 * the user is authenticated but holds no privileges until the password is changed.
 */
public record AdminPrincipal(Long id, String username, String fullName, boolean restricted) {
}
