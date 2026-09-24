package com.inkomoko.mobilebanking.controllers.admin;

import com.inkomoko.mobilebanking.enums.EPinStatus;
import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.*;
import com.inkomoko.mobilebanking.security.AdminPrincipal;
import com.inkomoko.mobilebanking.services.admin.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** USSD customers: search, core-banking lookup, onboarding, PIN support, disable / enable. */
@RestController
@RequestMapping("/api/admin/customers")
@Tag(name = "Admin - Customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerService customers;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ResponseEntity<ApiResponse<Page<CustomerView>>> list(@RequestParam(required = false) String q,
                                                                @RequestParam(required = false) EPinStatus status,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Customers", customers.list(q, status, page, size)));
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('CUSTOMER_VIEW','CUSTOMER_ONBOARD')")
    @Operation(summary = "Find the client in core banking by phone, with loans and USSD registration")
    public ResponseEntity<ApiResponse<CustomerLookup>> lookup(@RequestParam String msisdn) {
        return ResponseEntity.ok(ApiResponse.ok("Lookup", customers.lookup(msisdn)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_ONBOARD')")
    @Operation(summary = "Onboard a verified client to USSD (client creates a PIN on first dial; SMS invitation)")
    public ResponseEntity<ApiResponse<CustomerView>> onboard(@Valid @RequestBody OnboardRequest req,
                                                             @AuthenticationPrincipal AdminPrincipal me) {
        return ResponseEntity.ok(ApiResponse.ok("Customer onboarded", customers.onboard(req, me.username())));
    }

    @GetMapping("/{msisdn}/details")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    @Operation(summary = "USSD customer details next to the core-banking client")
    public ResponseEntity<ApiResponse<CustomerDetails>> details(@PathVariable String msisdn) {
        return ResponseEntity.ok(ApiResponse.ok("Customer", customers.details(msisdn)));
    }

    @PutMapping("/{msisdn}")
    @PreAuthorize("hasAuthority('CUSTOMER_ONBOARD')")
    @Operation(summary = "Update name, phone and e-mail on the USSD customer and in core banking")
    public ResponseEntity<ApiResponse<CustomerView>> update(@PathVariable String msisdn,
                                                            @Valid @RequestBody UpdateCustomerRequest req,
                                                            @AuthenticationPrincipal AdminPrincipal me) {
        return ResponseEntity.ok(ApiResponse.ok("Customer updated", customers.update(msisdn, req, me.username())));
    }

    @PostMapping("/{msisdn}/sync")
    @PreAuthorize("hasAuthority('CUSTOMER_ONBOARD')")
    @Operation(summary = "Copy name, phone and e-mail from core banking onto the USSD customer")
    public ResponseEntity<ApiResponse<CustomerView>> sync(@PathVariable String msisdn,
                                                          @AuthenticationPrincipal AdminPrincipal me) {
        return ResponseEntity.ok(ApiResponse.ok("Synced from core banking", customers.sync(msisdn, me.username())));
    }

    @PostMapping("/{msisdn}/reset-pin")
    @PreAuthorize("hasAuthority('CUSTOMER_PIN_RESET')")
    public ResponseEntity<ApiResponse<CustomerView>> resetPin(@PathVariable String msisdn,
                                                              @AuthenticationPrincipal AdminPrincipal me) {
        return ResponseEntity.ok(ApiResponse.ok("PIN reset", customers.resetPin(msisdn, me.username())));
    }

    @PostMapping("/{msisdn}/unlock")
    @PreAuthorize("hasAuthority('CUSTOMER_PIN_UNLOCK')")
    public ResponseEntity<ApiResponse<CustomerView>> unlock(@PathVariable String msisdn,
                                                            @AuthenticationPrincipal AdminPrincipal me) {
        return ResponseEntity.ok(ApiResponse.ok("PIN unlocked", customers.unlock(msisdn, me.username())));
    }

    @PostMapping("/{msisdn}/disable")
    @PreAuthorize("hasAuthority('CUSTOMER_DISABLE')")
    public ResponseEntity<ApiResponse<CustomerView>> disable(@PathVariable String msisdn,
                                                             @AuthenticationPrincipal AdminPrincipal me) {
        return ResponseEntity.ok(ApiResponse.ok("USSD access disabled", customers.setEnabled(msisdn, false, me.username())));
    }

    @PostMapping("/{msisdn}/enable")
    @PreAuthorize("hasAuthority('CUSTOMER_DISABLE')")
    public ResponseEntity<ApiResponse<CustomerView>> enable(@PathVariable String msisdn,
                                                            @AuthenticationPrincipal AdminPrincipal me) {
        return ResponseEntity.ok(ApiResponse.ok("USSD access enabled", customers.setEnabled(msisdn, true, me.username())));
    }
}
