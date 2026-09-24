package com.inkomoko.mobilebanking.security;

import com.inkomoko.mobilebanking.services.admin.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Ensures the Super Administrator exists when the application starts. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap {

    private final AdminUserService users;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            users.bootstrapAdminIfNone();
        } catch (Exception e) {
            log.error("Could not seed the portal administrator: {}", e.getMessage());
        }
    }
}
