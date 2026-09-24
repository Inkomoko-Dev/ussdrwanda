package com.inkomoko.mobilebanking.controllers.admin;

import com.inkomoko.mobilebanking.models.ClientPin;
import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.security.AdminActor;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.PinService;
import com.inkomoko.mobilebanking.utils.PhoneUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Support desk PIN operations. A reset must only be done after the client's identity has been
 * verified by support (proposal §3.4); the client then sets a new PIN on their next *311# dial.
 */
@RestController
@RequestMapping("/api/admin/pins")
@Tag(name = "Admin - PINs")
@RequiredArgsConstructor
public class AdminPinController {

    private final PinService pinService;
    private final AuditService audit;

    @GetMapping("/{msisdn}")
    @Operation(summary = "PIN status for a phone number")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(@PathVariable String msisdn) {
        String phone = PhoneUtils.purify(msisdn);
        return pinService.find(phone)
                .map(p -> ResponseEntity.ok(ApiResponse.ok("PIN status", view(p, pinService.remainingAttempts(phone)))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("No PIN record")));
    }

    @PostMapping("/{msisdn}/reset")
    @Operation(summary = "Reset PIN after identity verification (client sets a new PIN on next dial)")
    public ResponseEntity<ApiResponse<Void>> reset(@PathVariable String msisdn) {
        String phone = PhoneUtils.purify(msisdn);
        boolean ok = pinService.reset(phone);
        audit.admin(AdminActor.current(), "PIN_RESET", phone, ok, null);
        return ok ? ResponseEntity.ok(ApiResponse.ok("PIN reset; client will set a new PIN on next dial"))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("No PIN record"));
    }

    @PostMapping("/{msisdn}/unlock")
    @Operation(summary = "Unlock a locked PIN without changing it")
    public ResponseEntity<ApiResponse<Void>> unlock(@PathVariable String msisdn) {
        String phone = PhoneUtils.purify(msisdn);
        boolean ok = pinService.unlock(phone);
        audit.admin(AdminActor.current(), "PIN_UNLOCK", phone, ok, null);
        return ok ? ResponseEntity.ok(ApiResponse.ok("PIN unlocked"))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("No PIN record"));
    }

    private static Map<String, Object> view(ClientPin p, int remaining) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("msisdn", p.getMsisdn());
        m.put("clientId", p.getClientId());
        m.put("status", p.getStatus());
        m.put("remainingAttempts", remaining);
        m.put("lockedAt", p.getLockedAt());
        m.put("lastLoginAt", p.getLastLoginAt());
        m.put("updatedAt", p.getUpdatedAt());
        return m;
    }
}
