package com.inkomoko.mobilebanking.security;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protects /api/admin/** with a static API key (X-API-KEY header).
 * The acting operator is identified by X-Admin-User (used for maker/checker and audit).
 * Replace with SSO / role-based auth before production.
 */
@Component
@Order(1)
public class AdminApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-KEY";
    public static final String ADMIN_USER_HEADER = "X-Admin-User";

    private final InkomokoProperties props;

    public AdminApiKeyFilter(InkomokoProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(API_KEY_HEADER);
        String user = request.getHeader(ADMIN_USER_HEADER);
        if (key == null || !isValidKey(key)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing X-API-KEY");
            return;
        }
        if (user == null || user.isBlank()) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, "X-Admin-User header is required");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isValidKey(String key) {
        byte[] given = key.getBytes(StandardCharsets.UTF_8);
        for (String k : props.getAdmin().getApiKeys()) {
            if (k != null && !k.isBlank()
                    && MessageDigest.isEqual(given, k.trim().getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }
}
