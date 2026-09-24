package com.inkomoko.mobilebanking.controllers.admin;

import com.inkomoko.mobilebanking.models.FaqItem;
import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.models.api.FaqRequest;
import com.inkomoko.mobilebanking.repositories.FaqItemRepository;
import com.inkomoko.mobilebanking.security.AdminApiKeyFilter;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Help / FAQ content management — changes show on *311# immediately, no release needed. */
@RestController
@RequestMapping("/api/admin/faqs")
@Tag(name = "Admin - Help/FAQ")
@RequiredArgsConstructor
public class AdminFaqController {

    private final FaqItemRepository repo;
    private final AuditService audit;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FaqItem>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("FAQ items", repo.findAllByOrderByLanguageAscDisplayOrderAsc()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FaqItem>> create(@Valid @RequestBody FaqRequest req,
                                                       @RequestHeader(AdminApiKeyFilter.ADMIN_USER_HEADER) String admin) {
        FaqItem item = new FaqItem();
        item.setCreatedAt(DateUtility.nowKigali());
        apply(item, req);
        item = repo.save(item);
        audit.admin(admin, "FAQ_CREATED", String.valueOf(item.getId()), true, item.getTitle());
        return ResponseEntity.ok(ApiResponse.ok("Created", item));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FaqItem>> update(@PathVariable Long id, @Valid @RequestBody FaqRequest req,
                                                       @RequestHeader(AdminApiKeyFilter.ADMIN_USER_HEADER) String admin) {
        FaqItem item = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("FAQ item not found: " + id));
        apply(item, req);
        item = repo.save(item);
        audit.admin(admin, "FAQ_UPDATED", String.valueOf(id), true, item.getTitle());
        return ResponseEntity.ok(ApiResponse.ok("Updated", item));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id,
                                                        @RequestHeader(AdminApiKeyFilter.ADMIN_USER_HEADER) String admin) {
        FaqItem item = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("FAQ item not found: " + id));
        item.setActive(false);
        item.setUpdatedAt(DateUtility.nowKigali());
        repo.save(item);
        audit.admin(admin, "FAQ_DEACTIVATED", String.valueOf(id), true, item.getTitle());
        return ResponseEntity.ok(ApiResponse.ok("Deactivated"));
    }

    private static void apply(FaqItem item, FaqRequest req) {
        item.setLanguage(req.getLanguage());
        item.setTitle(req.getTitle().trim());
        item.setBody(req.getBody().trim());
        item.setDisplayOrder(req.getDisplayOrder() == null ? 0 : req.getDisplayOrder());
        item.setActive(req.getActive() == null || req.getActive());
        item.setUpdatedAt(DateUtility.nowKigali());
    }
}
