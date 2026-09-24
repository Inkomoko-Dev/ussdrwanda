package com.inkomoko.mobilebanking.controllers.admin;

import com.inkomoko.mobilebanking.config.DemoMode;
import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.models.api.demo.DemoClientRequest;
import com.inkomoko.mobilebanking.models.api.demo.DemoLoanRequest;
import com.inkomoko.mobilebanking.models.api.demo.DemoRepaymentRequest;
import com.inkomoko.mobilebanking.models.demo.DemoLoan;
import com.inkomoko.mobilebanking.models.demo.DemoRepayment;
import com.inkomoko.mobilebanking.security.AdminActor;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.DemoDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Demo core-banking data for the *662*105# pilot: allowed numbers, loans, repayment history.
 * Only registered when inkomoko.fineract.mode is not "live" — in production (*311# + Fineract)
 * these endpoints do not exist (404).
 *
 *   GET    /api/admin/demo/clients                           list allowed numbers with their loans
 *   POST   /api/admin/demo/clients                           add a number {msisdn, displayName?, active?, sampleLoans?=true}
 *   GET    /api/admin/demo/clients/{msisdn}
 *   PUT    /api/admin/demo/clients/{msisdn}                  {displayName?, active?}
 *   DELETE /api/admin/demo/clients/{msisdn}                  remove number + loans + history
 *   POST   /api/admin/demo/clients/{msisdn}/sample-loans     add the standard active + in-arrears loans
 *   POST   /api/admin/demo/clients/{msisdn}/loans            add a loan
 *   PUT    /api/admin/demo/loans/{loanId}                    change balances / status / due date
 *   DELETE /api/admin/demo/loans/{loanId}
 *   GET    /api/admin/demo/loans/{loanId}/repayments
 *   POST   /api/admin/demo/loans/{loanId}/repayments         {amount, date?, reference?, applyToBalance?=false}
 */
@RestController
@PreAuthorize("hasAuthority('DEMO_DATA_MANAGE')")
@RequestMapping("/api/admin/demo")
@Tag(name = "Admin - Demo core banking (pilot only)")
@ConditionalOnExpression(DemoMode.ENABLED)
@RequiredArgsConstructor
public class AdminDemoController {

    private final DemoDataService demo;
    private final AuditService audit;

    @GetMapping("/clients")
    public ResponseEntity<ApiResponse<List<DemoDataService.ClientView>>> clients() {
        return ResponseEntity.ok(ApiResponse.ok("Demo clients", demo.listClients()));
    }

    @PostMapping("/clients")
    @Operation(summary = "Allow a phone number (creates a demo client, with sample loans by default)")
    public ResponseEntity<ApiResponse<DemoDataService.ClientView>> createClient(
            @Valid @RequestBody DemoClientRequest req) {
        DemoDataService.ClientView v = demo.createClient(req);
        audit.admin(AdminActor.current(), "DEMO_CLIENT_CREATED", v.client().getMsisdn(), true, null);
        if (req.getOnboard() == null || req.getOnboard()) demo.onboardQuietly(v.client().getMsisdn(), AdminActor.current());
        return ResponseEntity.ok(ApiResponse.ok("Demo client created", v));
    }

    @GetMapping("/clients/{msisdn}")
    public ResponseEntity<ApiResponse<DemoDataService.ClientView>> client(@PathVariable String msisdn) {
        return ResponseEntity.ok(ApiResponse.ok("Demo client", demo.getClient(msisdn)));
    }

    @PutMapping("/clients/{msisdn}")
    public ResponseEntity<ApiResponse<DemoDataService.ClientView>> updateClient(
            @PathVariable String msisdn, @Valid @RequestBody DemoClientRequest req) {
        DemoDataService.ClientView v = demo.updateClient(msisdn, req);
        audit.admin(AdminActor.current(), "DEMO_CLIENT_UPDATED", v.client().getMsisdn(), true, null);
        return ResponseEntity.ok(ApiResponse.ok("Demo client updated", v));
    }

    @DeleteMapping("/clients/{msisdn}")
    public ResponseEntity<ApiResponse<Void>> deleteClient(
            @PathVariable String msisdn) {
        demo.deleteClient(msisdn);
        audit.admin(AdminActor.current(), "DEMO_CLIENT_DELETED", msisdn, true, null);
        return ResponseEntity.ok(ApiResponse.ok("Demo client deleted"));
    }

    @PostMapping("/clients/{msisdn}/sample-loans")
    public ResponseEntity<ApiResponse<DemoDataService.ClientView>> sampleLoans(
            @PathVariable String msisdn) {
        DemoDataService.ClientView v = demo.addSampleLoans(msisdn);
        audit.admin(AdminActor.current(), "DEMO_SAMPLE_LOANS_ADDED", v.client().getMsisdn(), true, null);
        return ResponseEntity.ok(ApiResponse.ok("Sample loans added", v));
    }

    @PostMapping("/clients/{msisdn}/loans")
    public ResponseEntity<ApiResponse<DemoLoan>> addLoan(
            @PathVariable String msisdn, @Valid @RequestBody DemoLoanRequest req) {
        DemoLoan l = demo.addLoan(msisdn, req);
        audit.admin(AdminActor.current(), "DEMO_LOAN_CREATED", l.getAccountNo(), true, msisdn);
        return ResponseEntity.ok(ApiResponse.ok("Demo loan created", l));
    }

    @PutMapping("/loans/{loanId}")
    public ResponseEntity<ApiResponse<DemoLoan>> updateLoan(
            @PathVariable Long loanId, @Valid @RequestBody DemoLoanRequest req) {
        DemoLoan l = demo.updateLoan(loanId, req);
        audit.admin(AdminActor.current(), "DEMO_LOAN_UPDATED", l.getAccountNo(), true, null);
        return ResponseEntity.ok(ApiResponse.ok("Demo loan updated", l));
    }

    @DeleteMapping("/loans/{loanId}")
    public ResponseEntity<ApiResponse<Void>> deleteLoan(
            @PathVariable Long loanId) {
        demo.deleteLoan(loanId);
        audit.admin(AdminActor.current(), "DEMO_LOAN_DELETED", String.valueOf(loanId), true, null);
        return ResponseEntity.ok(ApiResponse.ok("Demo loan deleted"));
    }

    @GetMapping("/loans/{loanId}/repayments")
    public ResponseEntity<ApiResponse<List<DemoRepayment>>> repayments(@PathVariable Long loanId) {
        return ResponseEntity.ok(ApiResponse.ok("Repayments", demo.repayments(loanId)));
    }

    @PostMapping("/loans/{loanId}/repayments")
    @Operation(summary = "Add a repayment line (applyToBalance=true also reduces the balance)")
    public ResponseEntity<ApiResponse<DemoRepayment>> addRepayment(
            @PathVariable Long loanId, @Valid @RequestBody DemoRepaymentRequest req) {
        DemoRepayment r = demo.addRepayment(loanId, req);
        audit.admin(AdminActor.current(), "DEMO_REPAYMENT_ADDED", String.valueOf(loanId), true, r.getAmount() + " " + r.getReference());
        return ResponseEntity.ok(ApiResponse.ok("Repayment added", r));
    }
}
