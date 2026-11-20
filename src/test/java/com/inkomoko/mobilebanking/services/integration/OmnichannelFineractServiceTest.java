package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.models.dto.ClientDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regression: a client who exists in core banking must be found even when Omnichannel
 * still reports {@code registered=false} / {@code ussdEnabled=false} (not portal-onboarded).
 */
@ExtendWith(MockitoExtension.class)
class OmnichannelFineractServiceTest {

    @Mock
    private OmnichannelClient api;

    @InjectMocks
    private OmnichannelFineractService fineract;

    @Test
    void findsClientWhenValidateSaysNotRegisteredButProfileExists() {
        when(api.post(eq("/ussd/phone/validate"), any())).thenReturn(validate(false));
        when(api.get(eq("/customer/profile"), any())).thenReturn(profile(99L, "Jane Doe"));

        Optional<ClientDto> client = fineract.findClientByMsisdn("250788000001");

        assertTrue(client.isPresent());
        assertEquals(99L, client.get().clientId());
        assertEquals("Jane Doe", client.get().displayName());
        assertTrue(client.get().active());
    }

    @Test
    void findsClientWhenValidateSaysRegistered() {
        when(api.post(eq("/ussd/phone/validate"), any())).thenReturn(validate(true));
        when(api.get(eq("/customer/profile"), any())).thenReturn(profile(12L, "Jean"));

        Optional<ClientDto> client = fineract.findClientByMsisdn("250788000002");

        assertTrue(client.isPresent());
        assertEquals(12L, client.get().clientId());
    }

    @Test
    void emptyWhenValidateAndProfileBothSayUnknown() {
        when(api.post(eq("/ussd/phone/validate"), any())).thenReturn(validate(false));
        when(api.get(eq("/customer/profile"), any()))
                .thenThrow(new OmnichannelException("missing", 404, "NOT_FOUND", null));

        assertTrue(fineract.findClientByMsisdn("250788000099").isEmpty());
    }

    private static Map<String, Object> validate(boolean registered) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("valid", true);
        v.put("registered", registered);
        v.put("ussdEnabled", false);
        v.put("accountStatus", "ACTIVE");
        return v;
    }

    private static Map<String, Object> profile(long customerId, String name) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("customerId", customerId);
        p.put("displayName", name);
        p.put("phoneNumber", "250788000001");
        p.put("accountStatus", "ACTIVE");
        p.put("eligible", true);
        return p;
    }
}
