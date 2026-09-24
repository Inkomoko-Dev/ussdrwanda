package com.inkomoko.mobilebanking.controllers.admin;

import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.*;
import com.inkomoko.mobilebanking.security.AdminPrincipal;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.CountryService;
import com.inkomoko.mobilebanking.services.admin.PasswordPolicyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Country profiles and the password policy. */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin - Settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final CountryService countries;
    private final PasswordPolicyService policy;
    private final AuditService audit;
    private final com.inkomoko.mobilebanking.services.MessageService messages;

    @GetMapping("/countries")
    @PreAuthorize("hasAnyAuthority('COUNTRY_VIEW','USER_VIEW')")
    public ResponseEntity<ApiResponse<List<CountryView>>> countries() {
        return ResponseEntity.ok(ApiResponse.ok("Countries", countries.list()));
    }

    @PutMapping("/countries/{code}")
    @PreAuthorize("hasAuthority('COUNTRY_MANAGE')")
    public ResponseEntity<ApiResponse<CountryView>> updateCountry(@PathVariable String code,
                                                                  @Valid @RequestBody CountryRequest req,
                                                                  @AuthenticationPrincipal AdminPrincipal me) {
        CountryView v = countries.update(code, req);
        audit.admin(me.username(), "COUNTRY_UPDATED", v.code(), true, null);
        return ResponseEntity.ok(ApiResponse.ok("Country updated", v));
    }

    @PostMapping("/countries/{code}/activate")
    @PreAuthorize("hasAuthority('COUNTRY_MANAGE')")
    public ResponseEntity<ApiResponse<CountryView>> activate(@PathVariable String code,
                                                             @AuthenticationPrincipal AdminPrincipal me) {
        CountryView v = countries.activate(code);
        messages.refresh(); // USSD texts follow the newly active country's overrides at once
        audit.admin(me.username(), "COUNTRY_ACTIVATED", v.code(), true, null);
        return ResponseEntity.ok(ApiResponse.ok("Country activated", v));
    }

    @GetMapping("/password-policy")
    @PreAuthorize("hasAuthority('POLICY_MANAGE')")
    public ResponseEntity<ApiResponse<PasswordPolicyView>> policy() {
        return ResponseEntity.ok(ApiResponse.ok("Password policy", policy.view()));
    }

    @PutMapping("/password-policy")
    @PreAuthorize("hasAuthority('POLICY_MANAGE')")
    public ResponseEntity<ApiResponse<PasswordPolicyView>> updatePolicy(@Valid @RequestBody PasswordPolicyRequest req,
                                                                        @AuthenticationPrincipal AdminPrincipal me) {
        PasswordPolicyView v = policy.update(req, me.username());
        audit.admin(me.username(), "PASSWORD_POLICY_UPDATED", null, true, null);
        return ResponseEntity.ok(ApiResponse.ok("Password policy updated", v));
    }
}
