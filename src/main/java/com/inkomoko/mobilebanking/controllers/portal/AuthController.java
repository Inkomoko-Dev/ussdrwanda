package com.inkomoko.mobilebanking.controllers.portal;

import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.*;
import com.inkomoko.mobilebanking.security.AdminPrincipal;
import com.inkomoko.mobilebanking.services.admin.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Portal sign-in, current user and password change. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Portal - Auth")
@RequiredArgsConstructor
public class AuthController {

    private final AdminUserService users;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Signed in", users.login(req)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(@AuthenticationPrincipal AdminPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Current user", users.me(principal)));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<LoginResponse>> changePassword(@AuthenticationPrincipal AdminPrincipal principal,
                                                                     @Valid @RequestBody ChangePasswordRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Password changed", users.changePassword(principal, req)));
    }
}
