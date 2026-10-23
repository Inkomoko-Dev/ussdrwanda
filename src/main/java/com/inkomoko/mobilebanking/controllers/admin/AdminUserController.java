package com.inkomoko.mobilebanking.controllers.admin;

import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.*;
import com.inkomoko.mobilebanking.security.AdminPrincipal;
import com.inkomoko.mobilebanking.services.admin.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin - Portal users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService users;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<ApiResponse<List<UserView>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Users", users.list()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<UserWithTempPassword>> create(@Valid @RequestBody CreateUserRequest req,
                                                                    @AuthenticationPrincipal AdminPrincipal me) {
        return ResponseEntity.ok(ApiResponse.ok("User created", users.create(req, me.username())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<UserView>> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req,
                                                        @AuthenticationPrincipal AdminPrincipal me) {
        return ResponseEntity.ok(ApiResponse.ok("User updated", users.update(id, req, me)));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<UserWithTempPassword>> resetPassword(@PathVariable Long id,
                                                                           @AuthenticationPrincipal AdminPrincipal me) {
        return ResponseEntity.ok(ApiResponse.ok("Password reset", users.resetPassword(id, me.username())));
    }

    @PostMapping("/email-default-password")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<UserView>> emailDefaultPassword(@Valid @RequestBody EmailDefaultPasswordRequest req,
                                                                      @AuthenticationPrincipal AdminPrincipal me) {
        UserView user = users.emailDefaultPassword(req.email(), me);
        return ResponseEntity.ok(ApiResponse.ok("Default sign-in details sent to " + user.email(), user));
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<UserView>> unlock(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal me) {
        return ResponseEntity.ok(ApiResponse.ok("User unlocked", users.unlock(id, me.username())));
    }
}
