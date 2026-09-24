package com.inkomoko.mobilebanking.controllers.admin;

import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.models.api.portal.MessageDtos.*;
import com.inkomoko.mobilebanking.security.AdminPrincipal;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.MessageService;
import com.inkomoko.mobilebanking.services.admin.MessageAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * USSD / SMS texts editor. {@code country} = country code, "*" = every country,
 * omitted = the active country. {@code languages} = show these languages (plus English as the
 * reference) instead of the scope's own, e.g. to translate a language added under Settings > Languages.
 */
@RestController
@RequestMapping("/api/admin/messages")
@Tag(name = "Admin - USSD texts")
@RequiredArgsConstructor
public class AdminMessageController {

    private final MessageAdminService service;
    private final AuditService audit;
    private final MessageService messages;

    @GetMapping
    @PreAuthorize("hasAuthority('MESSAGE_VIEW')")
    @Operation(summary = "All texts with default, override and effective value per language")
    public ResponseEntity<ApiResponse<MessageCatalog>> catalog(@RequestParam(required = false) String country,
                                                               @RequestParam(required = false) List<String> languages) {
        return ResponseEntity.ok(ApiResponse.ok("USSD texts", service.catalog(country, languages)));
    }

    @PostMapping("/{key}/check")
    @PreAuthorize("hasAuthority('MESSAGE_VIEW')")
    @Operation(summary = "Validate a draft (placeholders, GSM-7, length) and return a preview")
    public ResponseEntity<ApiResponse<MessageCheckResponse>> check(@PathVariable String key,
                                                                   @RequestParam(required = false) String language,
                                                                   @RequestBody MessageUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Checked", service.check(key, language, req.text())));
    }

    @PutMapping("/{key}/{language}")
    @PreAuthorize("hasAuthority('MESSAGE_MANAGE')")
    public ResponseEntity<ApiResponse<MessageText>> update(@PathVariable String key, @PathVariable String language,
                                                           @RequestParam(required = false) String country,
                                                           @Valid @RequestBody MessageUpdateRequest req,
                                                           @AuthenticationPrincipal AdminPrincipal me) {
        MessageText v = service.update(key, language, country, req.text(), me.username());
        messages.refresh(); // after commit: live on this node now, on other nodes within a minute
        audit.admin(me.username(), "USSD_TEXT_UPDATED", target(key, language, country), true, null);
        return ResponseEntity.ok(ApiResponse.ok("Text saved", v));
    }

    @DeleteMapping("/{key}/{language}")
    @PreAuthorize("hasAuthority('MESSAGE_MANAGE')")
    @Operation(summary = "Revert to the default text (removes the translation for a portal-added language)")
    public ResponseEntity<ApiResponse<MessageText>> revert(@PathVariable String key, @PathVariable String language,
                                                           @RequestParam(required = false) String country,
                                                           @AuthenticationPrincipal AdminPrincipal me) {
        MessageText v = service.revert(key, language, country, me.username());
        messages.refresh();
        audit.admin(me.username(), "USSD_TEXT_REVERTED", target(key, language, country), true, null);
        return ResponseEntity.ok(ApiResponse.ok("Reverted to default", v));
    }

    @GetMapping("/{key}/history")
    @PreAuthorize("hasAuthority('MESSAGE_VIEW')")
    public ResponseEntity<ApiResponse<List<MessageHistoryView>>> history(@PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.ok("History", service.history(key)));
    }

    private String target(String key, String language, String country) {
        return service.scope(country) + ":" + language + ":" + key;
    }
}
