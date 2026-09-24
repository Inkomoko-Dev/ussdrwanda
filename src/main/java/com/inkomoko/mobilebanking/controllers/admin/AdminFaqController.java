package com.inkomoko.mobilebanking.controllers.admin;

import com.inkomoko.mobilebanking.models.FaqItem;
import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.models.api.FaqRequest;
import com.inkomoko.mobilebanking.repositories.FaqItemRepository;
import com.inkomoko.mobilebanking.security.AdminActor;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Help / FAQ content management — changes show on USSD immediately, no release needed. */
@RestController
@RequestMapping("/api/admin/faqs")
@Tag(name = "Admin - Help/FAQ")
@RequiredArgsConstructor
public class AdminFaqController {

    private final FaqItemRepository repo;
    private final AuditService audit;

    @GetMapping
    @PreAuthorize("hasAuthority('FAQ_VIEW')")
    public ResponseEntity<ApiResponse<List<FaqItem>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("FAQ items", repo.findAllByOrderByLanguageAscDisplayOrderAsc()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FAQ_MANAGE')")
    public ResponseEntity<ApiResponse<FaqItem>> create(@Valid @RequestBody FaqRequest req) {
        FaqItem item = new FaqItem();
        item.setCreatedAt(DateUtility.nowKigali());
        apply(item, req);
        item = repo.save(item);
        audit.admin(AdminActor.current(), "FAQ_CREATED", String.valueOf(item.getId()), true, item.getTitle());
        return ResponseEntity.ok(ApiResponse.ok("Created", item));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FAQ_MANAGE')")
    public ResponseEntity<ApiResponse<FaqItem>> update(@PathVariable Long id, @Valid @RequestBody FaqRequest req) {
        FaqItem item = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("FAQ item not found: " + id));
        apply(item, req);
        item = repo.save(item);
        audit.admin(AdminActor.current(), "FAQ_UPDATED", String.valueOf(id), true, item.getTitle());
        return ResponseEntity.ok(ApiResponse.ok("Updated", item));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FAQ_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        FaqItem item = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("FAQ item not found: " + id));
        item.setActive(false);
        item.setUpdatedAt(DateUtility.nowKigali());
        repo.save(item);
        audit.admin(AdminActor.current(), "FAQ_DEACTIVATED", String.valueOf(id), true, item.getTitle());
        return ResponseEntity.ok(ApiResponse.ok("Deactivated"));
    }

    private static void apply(FaqItem item, FaqRequest req) {
        item.setLanguage(req.getLanguage().trim().toLowerCase());
        item.setTitle(req.getTitle().trim());
        item.setBody(req.getBody().trim());
        item.setDisplayOrder(req.getDisplayOrder() == null ? 0 : req.getDisplayOrder());
        item.setActive(req.getActive() == null || req.getActive());
        item.setUpdatedAt(DateUtility.nowKigali());
    }
}
