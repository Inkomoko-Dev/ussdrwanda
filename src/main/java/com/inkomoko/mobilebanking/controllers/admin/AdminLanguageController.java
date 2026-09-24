package com.inkomoko.mobilebanking.controllers.admin;

import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.models.api.portal.LanguageDtos.*;
import com.inkomoko.mobilebanking.security.AdminPrincipal;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.LanguageService;
import com.inkomoko.mobilebanking.services.MessageService;
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
 * USSD languages. Adding one makes it available for translation under "USSD & SMS texts";
 * a country can offer it once every text is translated.
 */
@RestController
@RequestMapping("/api/admin/languages")
@Tag(name = "Admin - Languages")
@RequiredArgsConstructor
public class AdminLanguageController {

    private final LanguageService languages;
    private final MessageService messages;
    private final AuditService audit;

    /** Any signed-in user: the portal needs language names on several pages. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Languages with translation progress and the countries offering them")
    public ResponseEntity<ApiResponse<List<LanguageView>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Languages", languages.list()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LANGUAGE_MANAGE')")
    public ResponseEntity<ApiResponse<LanguageView>> create(@Valid @RequestBody LanguageCreateRequest req,
                                                            @AuthenticationPrincipal AdminPrincipal me) {
        LanguageView v = languages.create(req, me.username());
        languages.refresh();
        audit.admin(me.username(), "LANGUAGE_CREATED", v.code(), true, v.englishName());
        return ResponseEntity.ok(ApiResponse.ok("Language added", v));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('LANGUAGE_MANAGE')")
    public ResponseEntity<ApiResponse<LanguageView>> update(@PathVariable String code,
                                                            @Valid @RequestBody LanguageUpdateRequest req,
                                                            @AuthenticationPrincipal AdminPrincipal me) {
        LanguageView v = languages.update(code, req);
        languages.refresh();
        audit.admin(me.username(), "LANGUAGE_UPDATED", v.code(), true, null);
        return ResponseEntity.ok(ApiResponse.ok("Language updated", v));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasAuthority('LANGUAGE_MANAGE')")
    @Operation(summary = "Delete a portal-added language and its translations (history is kept)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String code,
                                                    @AuthenticationPrincipal AdminPrincipal me) {
        languages.delete(code);
        languages.refresh();
        messages.refresh();
        audit.admin(me.username(), "LANGUAGE_DELETED", code, true, null);
        return ResponseEntity.ok(ApiResponse.ok("Language deleted"));
    }
}
