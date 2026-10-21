package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.models.CountryConfig;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.CountryRequest;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.CountryView;
import com.inkomoko.mobilebanking.repositories.CountryConfigRepository;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.GeneralMethods;
import com.inkomoko.mobilebanking.utils.UssdInputParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Country profiles. The active profile (chosen in the setup wizard) drives the USSD service:
 * language menu, currency, repayment limits and helpline. Until a country is activated
 * (first sign-in of the Super Administrator), values fall back to application.properties.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountryService {

    private static final List<String> DEFAULT_LANGUAGES = List.of("rw", "en", "fr");

    private final CountryConfigRepository repo;
    private final InkomokoProperties props;
    private final LanguageService languageService;

    @EventListener(ApplicationReadyEvent.class)
    public void applyActiveCurrency() {
        active().ifPresent(c -> {
            GeneralMethods.setCurrency(c.getCurrency());
            log.info("Active country: {} ({}), currency {}", c.getName(), c.getCode(), c.getCurrency());
        });
    }

    // ── Values used by the USSD service ─────────────────────────────────

    public Optional<CountryConfig> active() {
        return repo.findFirstByActiveTrue();
    }

    public List<String> languages() {
        return active().map(CountryConfig::languageList).orElse(DEFAULT_LANGUAGES);
    }

    /** Shortcode of the active country, for example 662*105. Falls back to application.properties. */
    public String serviceCode() {
        return active().map(CountryConfig::getUssdServiceCode).filter(s -> s != null && !s.isBlank())
                .orElse(props.getUssd().getServiceCode());
    }

    public String helpline() {
        return active().map(CountryConfig::getHelpline).filter(h -> !h.isBlank())
                .orElse(props.getSupport().getHelpline());
    }

    public BigDecimal minRepayment() {
        return active().map(CountryConfig::getMinRepayment).orElse(props.getRepayment().getMinAmount());
    }

    public BigDecimal maxRepayment() {
        return active().map(CountryConfig::getMaxRepayment).orElse(props.getRepayment().getMaxAmount());
    }

    public String currency() {
        return active().map(CountryConfig::getCurrency).orElse(props.getRepayment().getCurrency());
    }

    /** Mobile networks offered when a repayment is paid from another phone. Defaults to MTN and Airtel. */
    public List<String> mobileNetworks() {
        return active().map(c -> {
            if (c.getMobileNetworks() == null || c.getMobileNetworks().isBlank()) return List.<String>of();
            return Arrays.stream(c.getMobileNetworks().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }).filter(list -> !list.isEmpty()).orElse(List.of("MTN", "Airtel"));
    }

    // ── Portal management ───────────────────────────────────────────────

    public List<CountryView> list() {
        return repo.findAllByOrderByNameAsc().stream().map(CountryService::view).toList();
    }

    public CountryView get(String code) {
        return view(require(code));
    }

    @Transactional
    public CountryView update(String code, CountryRequest r) {
        CountryConfig c = require(code);
        apply(c, r);
        c = repo.save(c);
        if (c.isActive()) GeneralMethods.setCurrency(c.getCurrency());
        return view(c);
    }

    /** Makes this country the one the USSD service runs for (exactly one active). */
    @Transactional
    public CountryView activate(String code) {
        CountryConfig c = require(code);
        repo.deactivateAll();
        c = require(code);
        languageService.requireReady(c.languageList(), c.getCode());
        c.setActive(true);
        c.setUpdatedAt(DateUtility.nowKigali());
        c = repo.save(c);
        GeneralMethods.setCurrency(c.getCurrency());
        return view(c);
    }

    void apply(CountryConfig c, CountryRequest r) {
        if (r.name() != null && !r.name().isBlank()) c.setName(r.name().trim());
        if (r.dialCode() != null && !r.dialCode().isBlank()) c.setDialCode(r.dialCode().trim());
        if (r.nationalNumberLength() != null) c.setNationalNumberLength(r.nationalNumberLength());
        if (r.currency() != null && !r.currency().isBlank()) c.setCurrency(r.currency().trim().toUpperCase());
        if (r.timezone() != null && !r.timezone().isBlank()) {
            try {
                ZoneId.of(r.timezone().trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Unknown timezone: " + r.timezone());
            }
            c.setTimezone(r.timezone().trim());
        }
        if (r.languages() != null && !r.languages().isEmpty()) {
            c.setLanguages(String.join(",", languageService.requireReady(r.languages(), c.getCode())));
        }
        if (r.mobileNetworks() != null) {
            c.setMobileNetworks(r.mobileNetworks().stream().map(String::trim).filter(s -> !s.isEmpty())
                    .map(String::toUpperCase).distinct().collect(Collectors.joining(",")));
        }
        if (r.helpline() != null) c.setHelpline(r.helpline().trim());
        if (r.ussdServiceCode() != null && !r.ussdServiceCode().isBlank()) {
            String code = UssdInputParser.normalizeServiceCode(r.ussdServiceCode());
            if (!code.matches("[0-9]+(\\*[0-9]+)*")) {
                throw new IllegalArgumentException("USSD shortcode must look like 662*105 or 311");
            }
            c.setUssdServiceCode(code);
        }
        if (r.minRepayment() != null) c.setMinRepayment(r.minRepayment());
        if (r.maxRepayment() != null) c.setMaxRepayment(r.maxRepayment());
        if (c.getMinRepayment() != null && c.getMaxRepayment() != null
                && c.getMinRepayment().compareTo(c.getMaxRepayment()) > 0) {
            throw new IllegalArgumentException("Minimum repayment cannot exceed the maximum");
        }
        c.setUpdatedAt(DateUtility.nowKigali());
    }

    private CountryConfig require(String code) {
        return repo.findById(code == null ? "" : code.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Unknown country: " + code));
    }

    public static CountryView view(CountryConfig c) {
        List<String> networks = c.getMobileNetworks() == null || c.getMobileNetworks().isBlank() ? List.of()
                : Arrays.stream(c.getMobileNetworks().split(",")).map(String::trim).toList();
        return new CountryView(c.getCode(), c.getName(), c.getDialCode(), c.getNationalNumberLength(), c.getCurrency(),
                c.getTimezone(), c.languageList(), networks, c.getHelpline(), c.getUssdServiceCode(),
                c.getMinRepayment(), c.getMaxRepayment(), c.isActive());
    }
}
