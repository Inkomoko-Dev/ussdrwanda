package com.inkomoko.mobilebanking.services.admin;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.EPinStatus;
import com.inkomoko.mobilebanking.models.ClientPin;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.*;
import com.inkomoko.mobilebanking.models.dto.ClientDto;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.repositories.AccountAttemptRepository;
import com.inkomoko.mobilebanking.repositories.ClientPinRepository;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.CountryService;
import com.inkomoko.mobilebanking.services.LanguagePreferenceService;
import com.inkomoko.mobilebanking.services.PinService;
import com.inkomoko.mobilebanking.services.integration.FineractService;
import com.inkomoko.mobilebanking.services.integration.SmsService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.PhoneUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * USSD customers as seen in the portal: search, core-banking lookup, onboarding
 * (staff-verified registration + SMS invitation), PIN reset / unlock, disable / enable.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final ClientPinRepository pins;
    private final AccountAttemptRepository attempts;
    private final FineractService coreBanking;
    private final PinService pinService;
    private final SmsService sms;
    private final LanguagePreferenceService languages;
    private final AuditService audit;
    private final CountryService countries;
    private final InkomokoProperties props;
    private final com.inkomoko.mobilebanking.services.MessageService messages;

    public Page<CustomerView> list(String q, EPinStatus status, int page, int size) {
        String query = q == null ? "" : q.trim();
        if (query.matches("\\+?[0-9]{9,13}")) query = PhoneUtils.purify(query);
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        Page<ClientPin> result = status == null ? pins.search(query, pr) : pins.searchByStatus(query, status, pr);
        Map<String, String> chosen = languages.chosenLanguages(result.map(ClientPin::getMsisdn).toList());
        return result.map(p -> view(p, chosen.get(p.getMsisdn())));
    }

    public CustomerLookup lookup(String msisdn) {
        String phone = normalize(msisdn);
        Optional<ClientDto> client = coreBanking.findClientByMsisdn(phone);
        List<LoanSummaryDto> loans = client.map(c -> coreBanking.getClientLoans(c.clientId())).orElse(List.of());
        return new CustomerLookup(phone, client.isPresent(),
                client.map(c -> new ClientInfo(c.clientId(), c.displayName(), c.active())).orElse(null),
                loans, pins.findByMsisdn(phone).map(p -> view(p, languages.chosenLanguage(phone).orElse(null))).orElse(null));
    }

    /** Registers a verified core-banking client for USSD; the client creates a PIN on first dial. */
    @Transactional
    public CustomerView onboard(OnboardRequest r, String by) {
        String phone = normalize(r.msisdn());
        ClientDto client = coreBanking.findClientByMsisdn(phone).orElseThrow(() ->
                new IllegalArgumentException("No core-banking client is registered with " + phone));
        if (!client.active()) throw new IllegalStateException("The client is not active in core banking");

        LocalDateTime now = DateUtility.nowKigali();
        ClientPin p = pins.findByMsisdn(phone).orElse(null);
        if (p != null && p.getStatus() == EPinStatus.DISABLED) {
            throw new IllegalStateException("USSD access is disabled for this customer — enable it instead");
        }
        if (p != null && p.getStatus() == EPinStatus.ACTIVE && client.clientId().equals(p.getClientId())) {
            throw new IllegalStateException("Customer is already registered and has a PIN");
        }
        if (p == null) {
            p = new ClientPin();
            p.setMsisdn(phone);
            p.setCreatedAt(now);
        }
        p.setClientId(client.clientId());
        p.setClientName(client.displayName());
        p.setPinHash(null);
        p.setStatus(EPinStatus.NOT_SET);
        p.setLockedAt(null);
        p.setRegistrationChannel("PORTAL");
        p.setOnboardedBy(by);
        p.setOnboardedAt(now);
        p.setUpdatedAt(now);
        p = pins.save(p);
        attempts.findByMsisdn(phone).ifPresent(attempts::delete);
        audit.admin(by, "CUSTOMER_ONBOARDED", phone, true, "clientId=" + client.clientId());

        if (r.sendSms() == null || r.sendSms()) {
            String l = languages.preferredLanguage(phone);
            String code = "*" + countries.serviceCode() + "#";
            String first = com.inkomoko.mobilebanking.utils.menus.CommonMenus.firstName(client.displayName());
            sms.send(phone, first == null
                    ? messages.text(l, "sms.onboard.invite", java.util.Map.of("code", code))
                    : messages.text(l, "sms.onboard.invite.named", java.util.Map.of("name", first, "code", code)));
        }
        return view(p);
    }

    @Transactional
    public CustomerView resetPin(String msisdn, String by) {
        String phone = normalize(msisdn);
        if (!pinService.reset(phone)) throw new IllegalArgumentException("Customer not found: " + phone);
        audit.admin(by, "PIN_RESET", phone, true, null);
        return view(require(phone));
    }

    @Transactional
    public CustomerView unlock(String msisdn, String by) {
        String phone = normalize(msisdn);
        if (!pinService.unlock(phone)) throw new IllegalArgumentException("Customer not found: " + phone);
        audit.admin(by, "PIN_UNLOCK", phone, true, null);
        return view(require(phone));
    }

    @Transactional
    public CustomerView setEnabled(String msisdn, boolean enabled, String by) {
        String phone = normalize(msisdn);
        ClientPin p = require(phone);
        if (enabled) {
            if (p.getStatus() != EPinStatus.DISABLED) return view(p);
            p.setStatus(p.getPinHash() == null ? EPinStatus.NOT_SET : EPinStatus.ACTIVE);
        } else {
            p.setStatus(EPinStatus.DISABLED);
        }
        p.setUpdatedAt(DateUtility.nowKigali());
        p = pins.save(p);
        audit.admin(by, enabled ? "CUSTOMER_ENABLED" : "CUSTOMER_DISABLED", phone, true, null);
        return view(p);
    }

    public CustomerDetails details(String msisdn) {
        ClientPin p = require(normalize(msisdn));
        Optional<ClientDto> core = coreClient(p);
        return new CustomerDetails(view(p),
                core.map(ClientDto::clientId).orElse(p.getClientId()),
                core.map(ClientDto::displayName).orElse(null),
                core.map(ClientDto::mobileNo).orElse(null),
                core.map(ClientDto::email).orElse(null),
                core.map(ClientDto::active).orElse(null),
                core.isPresent());
    }

    /** Copies name, phone and e-mail from core banking onto the USSD customer. */
    @Transactional
    public CustomerView sync(String msisdn, String by) {
        ClientPin p = require(normalize(msisdn));
        ClientDto core = coreClient(p).orElseThrow(() ->
                new IllegalStateException("No core-banking client is linked to " + p.getMsisdn()));
        if (p.getClientId() == null) p.setClientId(core.clientId());
        CustomerView saved = applyDetails(p, core.displayName(), core.mobileNo(), core.email());
        audit.admin(by, "CUSTOMER_SYNCED", saved.msisdn(), true, "clientId=" + core.clientId());
        return saved;
    }

    /** Saves name, phone and e-mail on the USSD customer and on the core-banking client. */
    @Transactional
    public CustomerView update(String msisdn, UpdateCustomerRequest r, String by) {
        ClientPin p = require(normalize(msisdn));
        String phone = normalize(r.phone());
        String name = r.name().trim();
        String email = r.email() == null || r.email().isBlank() ? null : r.email().trim();
        if (!phone.equals(p.getMsisdn()) && pins.findByMsisdn(phone).isPresent()) {
            throw new IllegalStateException("Another USSD customer already uses " + phone);
        }
        Optional<ClientDto> core = coreClient(p);
        if (core.isPresent()) {
            coreBanking.updateClient(core.get().clientId(), name, phone, email);
            if (p.getClientId() == null) p.setClientId(core.get().clientId());
        }
        CustomerView saved = applyDetails(p, name, phone, email);
        audit.admin(by, "CUSTOMER_UPDATED", saved.msisdn(), true,
                core.isPresent() ? "clientId=" + core.get().clientId() : "core banking client not found");
        return saved;
    }

    private Optional<ClientDto> coreClient(ClientPin p) {
        if (p.getClientId() != null) {
            Optional<ClientDto> byId = coreBanking.findClientById(p.getClientId());
            if (byId.isPresent()) return byId;
        }
        return coreBanking.findClientByMsisdn(p.getMsisdn());
    }

    private CustomerView applyDetails(ClientPin p, String name, String phone, String email) {
        String newPhone = phone == null || phone.isBlank() ? p.getMsisdn() : normalize(phone);
        if (!newPhone.equals(p.getMsisdn())) {
            if (pins.findByMsisdn(newPhone).isPresent()) {
                throw new IllegalStateException("Another USSD customer already uses " + newPhone);
            }
            String old = p.getMsisdn();
            languages.reassign(old, newPhone);
            attempts.findByMsisdn(old).ifPresent(a -> {
                a.setMsisdn(newPhone);
                attempts.save(a);
            });
            p.setMsisdn(newPhone);
        }
        if (name != null && !name.isBlank()) p.setClientName(name.trim());
        p.setEmail(email == null || email.isBlank() ? null : email.trim());
        p.setUpdatedAt(DateUtility.nowKigali());
        return view(pins.save(p));
    }

    private ClientPin require(String phone) {
        return pins.findByMsisdn(phone).orElseThrow(() -> new IllegalArgumentException("Customer not found: " + phone));
    }

    private static String normalize(String msisdn) {
        String phone = PhoneUtils.purify(msisdn);
        if (!phone.matches("[0-9]{9,15}")) throw new IllegalArgumentException("Invalid phone number: " + msisdn);
        return phone;
    }

    private CustomerView view(ClientPin p) {
        return view(p, languages.chosenLanguage(p.getMsisdn()).orElse(null));
    }

    private CustomerView view(ClientPin p, String language) {
        return new CustomerView(p.getMsisdn(), p.getClientId(), p.getClientName(), p.getStatus(),
                p.getRegistrationChannel(), p.getOnboardedBy(), p.getOnboardedAt(), p.getLastLoginAt(),
                p.getLockedAt(), p.getCreatedAt(), language, p.getEmail());
    }
}
