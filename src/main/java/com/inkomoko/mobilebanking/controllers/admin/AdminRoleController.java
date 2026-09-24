package com.inkomoko.mobilebanking.controllers.admin;

import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.*;
import com.inkomoko.mobilebanking.security.AdminPrincipal;
import com.inkomoko.mobilebanking.services.admin.RoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin - Roles & privileges")
@RequiredArgsConstructor
public class AdminRoleController {

    private final RoleService roles;

    @GetMapping("/privileges")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public ResponseEntity<ApiResponse<List<PrivilegeView>>> privileges() {
        return ResponseEntity.ok(ApiResponse.ok("Privileges", roles.privileges()));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW','USER_VIEW')")
    public ResponseEntity<ApiResponse<List<RoleView>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Roles", roles.list()));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleView>> create(@Valid @RequestBody RoleRequest req,
                                                        @AuthenticationPrincipal AdminPrincipal me) {
        return ResponseEntity.ok(ApiResponse.ok("Role created", roles.create(req, me.username())));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleView>> update(@PathVariable Long id, @Valid @RequestBody RoleRequest req,
                                                        @AuthenticationPrincipal AdminPrincipal me) {
        return ResponseEntity.ok(ApiResponse.ok("Role updated", roles.update(id, req, me.username())));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal me) {
        roles.delete(id, me.username());
        return ResponseEntity.ok(ApiResponse.ok("Role deleted"));
    }
}
