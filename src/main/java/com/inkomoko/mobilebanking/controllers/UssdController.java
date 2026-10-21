package com.inkomoko.mobilebanking.controllers;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.EClientState;
import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.ConsentLog;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.models.dto.ClientDto;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.models.dto.UssdRequest;
import com.inkomoko.mobilebanking.models.dto.UssdResponse;
import com.inkomoko.mobilebanking.repositories.ConsentLogRepository;
import com.inkomoko.mobilebanking.repositories.UssdSessionRepository;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.ConsentService;
import com.inkomoko.mobilebanking.services.LanguagePreferenceService;
import com.inkomoko.mobilebanking.services.PinService;
import com.inkomoko.mobilebanking.services.integration.FineractService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.GeneralMethods;
import com.inkomoko.mobilebanking.utils.PhoneUtils;
import com.inkomoko.mobilebanking.utils.UssdInputParser;
import com.inkomoko.mobilebanking.utils.Utility;
import com.inkomoko.mobilebanking.utils.menus.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Single USSD entry point for Inkomoko, called by the FDI (Efashe) USSD gateway.
 * Service code is configurable: *662*105# during the pilot, *311# once Inkomoko's shortcode is live.
 *
 * POST /api/ussd   (JSON — see FDI USSD Gateway Integration document)
 *   in : sessionId, gwRef, gwTstamp, msisdn, networkCode, serviceCode, userInput, serviceId, tenantId
 *        e.g. serviceCode "RW|MTN|662|105", networkCode "RW|MTN", userInput "*662*105*1*1#"
 *        (full dial string with all answers so far; the last segment is the latest answer)
 *   out: sessionId, gwRef, appRef, appTstamp, text, continueSession (1 = continue, 0 = end)
 *
 * Flow (proposal §3.3):
 *   A number that is not registered is told it cannot use the service.
 *   A registered customer with no saved language always starts at language selection,
 *   then terms, then PIN setup. A later dial skips only the steps already completed.
 *   After login: Loan, Repay, Account info, Settings, or FAQs.
 */
@RestController
@RequestMapping("/api/ussd")
@Tag(name = "USSD", description = "Inkomoko USSD endpoint (FDI gateway)")
@RequiredArgsConstructor
@Slf4j
public class UssdController {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");
    private static final Pattern MSISDN_PATTERN = Pattern.compile("^\\+?[0-9]{9,15}$");
    private static final int MAX_INPUT_LENGTH = 200;

    private static final int LEVEL_LANGUAGE = 1;
    private static final int LEVEL_CONSENT = 2;
    private static final int LEVEL_AUTH = 3;

    private static final String STEP_PIN_SETUP = "PIN_SETUP";
    private static final String STEP_PIN_CONFIRM = "PIN_CONFIRM";
    private static final String STEP_PIN_ENTRY = "PIN_ENTRY";

    // ── Repositories / services ────────────────────────────────────────────
    private final UssdSessionRepository sessionRepo;
    private final ConsentLogRepository consentRepo;
    private final ConsentService consentService;
    private final LanguagePreferenceService languagePreferenceService;
    private final PinService pinService;
    private final FineractService fineract;
    private final AuditService audit;
    private final InkomokoProperties props;
    private final com.inkomoko.mobilebanking.services.CountryService countries;

    // ── Menu handlers ──────────────────────────────────────────────────────
    private final LoanMenu loanMenu;
    private final AccountInfoMenu accountInfoMenu;
    private final MiniStatementMenu miniStatementMenu;
    private final RepayLoanMenu repayLoanMenu;
    private final HelpMenu helpMenu;
    private final SettingsMenu settingsMenu;
    private final CommonMenus common;
    private final CommonHeadings headings;

    // ══════════════════════════════════════════════════════════════════════
    //  Main endpoint
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Handle USSD request", description = "Called by the FDI USSD gateway on every step of a session")
    public ResponseEntity<UssdResponse> handle(@RequestBody UssdRequest req) {
        long started = System.nanoTime();

        String error = validate(req);
        if (error != null) {
            log.warn("USSD bad request: sessionId={} reason={}", req.getSessionId(), error);
            return ResponseEntity.badRequest().body(response(req, null, "Invalid request: " + error, false));
        }
        String msisdn = PhoneUtils.purify(req.getMsisdn());

        Outcome outcome;
        UssdSession session = null;
        try {
            Processed p = process(req, msisdn, started);
            outcome = p.outcome();
            session = p.session();
        } catch (Exception e) {
            log.error("USSD unhandled error: msisdn={} sessionId={} error={}", msisdn, req.getSessionId(),
                    e.getMessage(), e);
            String lang = languagePreferenceService.preferredLanguage(msisdn);
            outcome = new Outcome(headings.serviceUnavailable(lang), false);
            markErrored(req.getSessionId(), msisdn);
        }

        long ms = (System.nanoTime() - started) / 1_000_000;
        if (outcome.text().length() > props.getUssd().getMaxResponseLength()) {
            log.warn("USSD screen longer than {} chars ({}): sessionId={}", props.getUssd().getMaxResponseLength(),
                    outcome.text().length(), req.getSessionId());
        }
        // userInput is never logged — it can contain a PIN
        log.info("USSD | msisdn={} sessionId={} inputLen={} continue={} ms={}", msisdn, req.getSessionId(),
                req.getUserInput() == null ? 0 : req.getUserInput().length(), outcome.continueSession() ? 1 : 0, ms);

        return ResponseEntity.ok(response(req, session, outcome.text(), outcome.continueSession()));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Session handling
    // ══════════════════════════════════════════════════════════════════════

    private Processed process(UssdRequest req, String msisdn, long started) {
        // Strip the code the gateway says was dialled (e.g. "RW|MTN|662|105" → 662*105), else the configured one.
        String dialled = UssdInputParser.normalizeServiceCode(req.getServiceCode());
        String code = dialled.isEmpty() ? countries.serviceCode() : dialled;
        List<String> segments = UssdInputParser.segments(req.getUserInput(), code);
        String rawInput = req.getUserInput() == null ? "" : req.getUserInput().trim();

        Optional<UssdSession> existing = sessionRepo.findBySessionIdAndMsisdn(req.getSessionId(), msisdn);

        // ── New session: language screen (Screen 1) ────────────────────────
        if (existing.isEmpty()) {
            UssdSession s = createSession(req, msisdn, segments.size());
            return finish(s, requestKey(req.getGwTstamp(), rawInput), beginSession(s), started);
        }

        UssdSession s = existing.get();
        String lang = s.getLanguage() != null ? s.getLanguage() : languagePreferenceService.defaultLanguage();

        if (s.isCloseCurrentSession()) {
            return new Processed(new Outcome(headings.sessionExpired(lang, countries.serviceCode()),
                    false), s);
        }

        // Gateway retry of the same request → replay the previous screen (no double processing).
        // Same raw input counts as a retry when the gateway timestamp is unchanged, or when the input
        // is clearly cumulative (2+ answers) and therefore could not legitimately repeat.
        String previousRaw = rawPart(s.getLastRawInput());
        boolean retry = rawInput.equals(previousRaw) && s.getLastResponse() != null
                && (segments.size() > 1 || requestKey(req.getGwTstamp(), rawInput).equals(s.getLastRawInput()));
        if (retry) {
            log.info("USSD duplicate request replayed: sessionId={}", s.getSessionId());
            return new Processed(new Outcome(s.getLastResponse(),
                    Integer.valueOf(1).equals(s.getLastContinueSession())), s);
        }

        // Inactivity timeout
        LocalDateTime now = DateUtility.nowKigali();
        if (s.getUpdatedAt() != null
                && s.getUpdatedAt().isBefore(now.minusSeconds(props.getUssd().getSessionTimeoutSeconds()))) {
            s.end(ESessionEndReason.ABANDONED);
            return finish(s, requestKey(req.getGwTstamp(), rawInput), headings.sessionExpired(lang, countries.serviceCode()), started);
        }

        String input = latestInput(s, segments);
        s.setGwRef(req.getGwRef());
        String text = route(s, input);
        return finish(s, requestKey(req.getGwTstamp(), rawInput), text, started);
    }

    /** Latest answer = newest segment of the cumulative userInput. */
    private String latestInput(UssdSession s, List<String> segments) {
        int processed = s.getProcessedInputs() == null ? 0 : s.getProcessedInputs();
        s.setProcessedInputs(Math.max(processed, segments.size()));
        if (segments.isEmpty()) return "";
        // Cumulative gateways add one segment per step; non-cumulative ones resend only the last answer.
        return segments.get(segments.size() - 1).trim();
    }

    private UssdSession createSession(UssdRequest req, String msisdn, int initialSegments) {
        LocalDateTime now = DateUtility.nowKigali();
        sessionRepo.closeOtherOpenSessions(msisdn, req.getSessionId(), ESessionEndReason.ABANDONED, now);

        UssdSession s = new UssdSession();
        s.setSessionId(req.getSessionId());
        s.setMsisdn(msisdn);
        s.setGwRef(req.getGwRef());
        s.setAppRef(Utility.generateAppRef());
        s.setNetworkCode(req.getNetworkCode());
        s.setLevel(0);
        s.setLanguage(languagePreferenceService.chosenLanguage(msisdn).orElse(null));
        // Ignore shortcut digits dialled with the code (e.g. *662*105*1#).
        s.setProcessedInputs(initialSegments);
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        audit.ussd(msisdn, req.getSessionId(), "SESSION_START", req.getNetworkCode(), true, null);
        return s;
    }

    private Processed finish(UssdSession s, String requestKey, String text, long started) {
        long ms = (System.nanoTime() - started) / 1_000_000;
        LocalDateTime now = DateUtility.nowKigali();
        boolean cont = !s.isCloseCurrentSession();
        s.setLastRawInput(requestKey.length() > MAX_INPUT_LENGTH ? requestKey.substring(0, MAX_INPUT_LENGTH) : requestKey);
        s.setLastResponse(text.length() > 1000 ? text.substring(0, 1000) : text);
        s.setLastContinueSession(cont ? 1 : 0);
        s.setRequestCount((s.getRequestCount() == null ? 0 : s.getRequestCount()) + 1);
        s.setTotalProcessingMs((s.getTotalProcessingMs() == null ? 0 : s.getTotalProcessingMs()) + ms);
        s.setUpdatedAt(now);
        if (!cont && s.getEndedAt() == null) s.setEndedAt(now);
        try {
            s = sessionRepo.save(s);
        } catch (DataIntegrityViolationException dup) {
            // concurrent first request with the same sessionId — the other request owns the session
            log.warn("USSD concurrent session create: sessionId={}", s.getSessionId());
        }
        return new Processed(new Outcome(text, cont), s);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Level routing
    // ══════════════════════════════════════════════════════════════════════

    private String route(UssdSession s, String input) {
        int level = s.getLevel() == null ? 0 : s.getLevel();
        return switch (level) {
            case 0 -> beginSession(s);
            case LEVEL_LANGUAGE -> handleLanguage(s, input);
            case LEVEL_CONSENT -> handleConsent(s, input);
            case LEVEL_AUTH -> handleAuth(s, input);
            case CommonMenus.LEVEL_MAIN_MENU -> handleMainMenu(s, input);
            default -> handleServiceLevel(s, input);
        };
    }

    // ── Level 1: language (Screen 1) ──────────────────────────────────────

    private String handleLanguage(UssdSession s, String input) {
        List<String> languages = countries.languages();
        int choice = com.inkomoko.mobilebanking.utils.GeneralMethods.parseInputAsInteger(input);
        String chosen = choice >= 1 && choice <= languages.size() ? languages.get(choice - 1) : null;
        if (chosen == null) return headings.invalidLanguage(languages);
        s.setLanguage(chosen);
        languagePreferenceService.savePreferredLanguage(s.getMsisdn(), chosen);
        if (consentService.hasCurrentConsent(s.getMsisdn())) {
            s.setConsentAccepted(true);
            return identifyClient(s);
        }
        s.setLevel(LEVEL_CONSENT);
        return headings.consent(chosen);
    }

    /**
     * First screen of a dial. Someone who is not registered never sees language or terms.
     * A registered customer who has not chosen a language always starts there — a country
     * default is not treated as their choice. Terms come only after that choice.
     */
    private String beginSession(UssdSession s) {
        String noticeLang = s.getLanguage() != null ? s.getLanguage() : languagePreferenceService.defaultLanguage();
        String blocked = eligibilityGate(s, noticeLang);
        if (blocked != null) return blocked;

        if (s.getLanguage() == null) {
            s.setLevel(LEVEL_LANGUAGE);
            return headings.languageMenu(countries.languages());
        }
        if (!consentService.hasCurrentConsent(s.getMsisdn())) {
            s.setLevel(LEVEL_CONSENT);
            return headings.consent(s.getLanguage());
        }
        s.setConsentAccepted(true);
        return identifyClient(s);
    }

    /**
     * @return a terminal screen when this phone cannot use the service, or null when it can
     *         (onboarded, active, and either ready for a PIN or still to create one)
     */
    private String eligibilityGate(UssdSession s, String lang) {
        Optional<ClientDto> client;
        try {
            client = fineract.findClientByMsisdn(s.getMsisdn());
        } catch (Exception e) {
            log.error("Client lookup failed msisdn={} error={}", s.getMsisdn(), e.getMessage());
            s.setLanguage(lang);
            return common.unavailable(s);
        }
        String helpline = common.helpline();
        if (client.isEmpty()) {
            s.setClientState(EClientState.NOT_REGISTERED);
            audit.ussd(s.getMsisdn(), s.getSessionId(), "CLIENT_NOT_FOUND", null, false, null);
            return common.endWith(s, ESessionEndReason.NOT_REGISTERED, headings.notRegistered(lang, helpline));
        }
        ClientDto c = client.get();
        s.setClientId(c.clientId());
        s.setClientName(c.displayName());
        if (!c.active()) {
            s.setClientState(EClientState.INACTIVE);
            audit.ussd(s.getMsisdn(), s.getSessionId(), "CLIENT_INACTIVE", String.valueOf(c.clientId()), false, null);
            return common.endWith(s, ESessionEndReason.NOT_REGISTERED, headings.inactiveClient(lang, helpline));
        }
        EClientState state = pinService.resolveState(s.getMsisdn(), c.clientId(), s.getSessionId());
        s.setClientState(state);
        return switch (state) {
            case LOCKED -> {
                audit.ussd(s.getMsisdn(), s.getSessionId(), "LOGIN_BLOCKED_LOCKED", String.valueOf(c.clientId()), false, null);
                yield common.endWith(s, ESessionEndReason.ACCOUNT_LOCKED, headings.pinLocked(lang, helpline));
            }
            case NOT_ONBOARDED -> {
                audit.ussd(s.getMsisdn(), s.getSessionId(), "CLIENT_NOT_ONBOARDED", String.valueOf(c.clientId()), false, null);
                yield common.endWith(s, ESessionEndReason.NOT_REGISTERED, headings.notOnboarded(lang, helpline));
            }
            case DISABLED -> {
                audit.ussd(s.getMsisdn(), s.getSessionId(), "CLIENT_DISABLED", String.valueOf(c.clientId()), false, null);
                yield common.endWith(s, ESessionEndReason.ACCOUNT_LOCKED, headings.accessDisabled(lang, helpline));
            }
            default -> null;
        };
    }

    // ── Level 2: Terms & Conditions / consent in the chosen language (ToR 3.1(ix)) ──

    private String handleConsent(UssdSession s, String input) {
        String lang = s.getLanguage();
        LocalDateTime now = DateUtility.nowKigali();
        if ("1".equals(input)) {
            s.setConsentAccepted(true);
            String version = headings.consentVersion(lang);
            consentRepo.save(new ConsentLog(s.getMsisdn(), s.getSessionId(), true, version, now));
            audit.ussd(s.getMsisdn(), s.getSessionId(), "CONSENT_ACCEPTED", version, true, null);
            return identifyClient(s);
        }
        if ("2".equals(input)) {
            String version = headings.consentVersion(lang);
            consentRepo.save(new ConsentLog(s.getMsisdn(), s.getSessionId(), false, version, now));
            audit.ussd(s.getMsisdn(), s.getSessionId(), "CONSENT_DECLINED", version, true, null);
            return common.endWith(s, ESessionEndReason.CONSENT_DECLINED, headings.thankYou(lang));
        }
        return headings.invalidChoice(lang) + headings.consent(lang);
    }

    /** After consent: MSISDN → Fineract client, then PIN setup / PIN entry / locked. */
    private String identifyClient(UssdSession s) {
        String lang = s.getLanguage();
        Optional<ClientDto> client;
        try {
            client = fineract.findClientByMsisdn(s.getMsisdn());
        } catch (Exception e) {
            log.error("Client lookup failed msisdn={} error={}", s.getMsisdn(), e.getMessage());
            return common.unavailable(s);
        }
        String helpline = common.helpline();
        if (client.isEmpty()) {
            s.setClientState(EClientState.NOT_REGISTERED);
            audit.ussd(s.getMsisdn(), s.getSessionId(), "CLIENT_NOT_FOUND", null, false, null);
            return common.endWith(s, ESessionEndReason.NOT_REGISTERED, headings.notRegistered(lang, helpline));
        }
        ClientDto c = client.get();
        s.setClientId(c.clientId());
        s.setClientName(c.displayName());
        if (!c.active()) {
            s.setClientState(EClientState.INACTIVE);
            audit.ussd(s.getMsisdn(), s.getSessionId(), "CLIENT_INACTIVE", String.valueOf(c.clientId()), false, null);
            return common.endWith(s, ESessionEndReason.NOT_REGISTERED, headings.inactiveClient(lang, helpline));
        }

        EClientState state = pinService.resolveState(s.getMsisdn(), c.clientId(), s.getSessionId());
        s.setClientState(state);
        s.setLevel(LEVEL_AUTH);
        return switch (state) {
            case LOCKED -> {
                audit.ussd(s.getMsisdn(), s.getSessionId(), "LOGIN_BLOCKED_LOCKED", String.valueOf(c.clientId()), false, null);
                yield common.endWith(s, ESessionEndReason.ACCOUNT_LOCKED, headings.pinLocked(lang, helpline));
            }
            case NOT_ONBOARDED -> {
                audit.ussd(s.getMsisdn(), s.getSessionId(), "CLIENT_NOT_ONBOARDED", String.valueOf(c.clientId()), false, null);
                yield common.endWith(s, ESessionEndReason.NOT_REGISTERED, headings.notOnboarded(lang, helpline));
            }
            case DISABLED -> {
                audit.ussd(s.getMsisdn(), s.getSessionId(), "CLIENT_DISABLED", String.valueOf(c.clientId()), false, null);
                yield common.endWith(s, ESessionEndReason.ACCOUNT_LOCKED, headings.accessDisabled(lang, helpline));
            }
            case PIN_NOT_SET -> {
                s.setStep(STEP_PIN_SETUP);
                yield headings.pinSetup(lang, props.getUssd().getPinLength());
            }
            default -> {
                s.setStep(STEP_PIN_ENTRY);
                yield headings.enterPin(lang, CommonMenus.firstName(c.displayName()));
            }
        };
    }

    // ── Level 3: PIN setup (Screen 2a) / PIN entry (Screen 2b) ─────────────

    private String handleAuth(UssdSession s, String input) {
        String step = s.getStep() == null ? STEP_PIN_ENTRY : s.getStep();
        return switch (step) {
            case STEP_PIN_SETUP -> pinSetup(s, input);
            case STEP_PIN_CONFIRM -> pinConfirm(s, input);
            default -> pinEntry(s, input);
        };
    }

    private String pinSetup(UssdSession s, String input) {
        String l = s.getLanguage();
        int digits = props.getUssd().getPinLength();
        if (!pinService.isValidFormat(input)) {
            return headings.pinBadFormat(l, digits) + headings.pinSetup(l, digits);
        }
        if (pinService.isWeak(input)) {
            return headings.pinWeak(l) + headings.pinSetup(l, digits);
        }
        s.setPendingPinHash(pinService.hash(input));   // only the bcrypt hash is kept between steps
        s.setStep(STEP_PIN_CONFIRM);
        return headings.pinConfirm(l);
    }

    private String pinConfirm(UssdSession s, String input) {
        String l = s.getLanguage();
        boolean match = pinService.matches(input, s.getPendingPinHash());
        s.setPendingPinHash(null);
        if (!match) {
            s.setStep(STEP_PIN_SETUP);
            return headings.pinMismatch(l) + headings.pinSetup(l, props.getUssd().getPinLength());
        }
        pinService.setPin(s.getMsisdn(), s.getClientId(), s.getClientName(), input);
        audit.ussd(s.getMsisdn(), s.getSessionId(), "PIN_SETUP", String.valueOf(s.getClientId()), true, null);
        return pinConfirmed(s);
    }

    /** First PIN is confirmed, the loan is shown, and the session ends. The next dial is a PIN login. */
    private String pinConfirmed(UssdSession s) {
        String l = s.getLanguage();
        try {
            List<LoanSummaryDto> loans = fineract.getClientLoans(s.getClientId()).stream()
                    .filter(x -> x.status().isRepayable()).toList();
            if (!loans.isEmpty()) {
                LoanSummaryDto loan = fineract.getLoanDetails(loans.get(0).loanId());
                String account = loan.accountNo() == null ? "-" : loan.accountNo();
                return common.endWith(s, ESessionEndReason.COMPLETED, headings.text(l, "pin.confirmed", Map.of(
                        "account", account,
                        "amount", GeneralMethods.rwf(loan.totalOutstanding()),
                        "due", DateUtility.screenDate(loan.nextDueDate()))));
            }
        } catch (Exception e) {
            log.warn("PIN confirmed summary failed msisdn={} error={}", s.getMsisdn(), e.getMessage());
        }
        return common.endWith(s, ESessionEndReason.COMPLETED, headings.pinCreated(l));
    }

    private String pinEntry(UssdSession s, String input) {
        String l = s.getLanguage();
        String client = String.valueOf(s.getClientId());
        return switch (pinService.verify(s.getMsisdn(), input)) {
            case SUCCESS -> {
                audit.ussd(s.getMsisdn(), s.getSessionId(), "LOGIN_SUCCESS", client, true, null);
                yield authenticated(s, "");
            }
            case WRONG -> {
                audit.ussd(s.getMsisdn(), s.getSessionId(), "LOGIN_FAILED", client, false, null);
                yield headings.wrongPin(l, pinService.remainingAttempts(s.getMsisdn()))
                        + headings.enterPin(l, null);
            }
            case LOCKED -> {
                audit.ussd(s.getMsisdn(), s.getSessionId(), "LOGIN_FAILED", client, false, null);
                audit.ussd(s.getMsisdn(), s.getSessionId(), "PIN_LOCKED", client, false, null);
                yield common.endWith(s, ESessionEndReason.ACCOUNT_LOCKED, headings.pinLocked(l, common.helpline()));
            }
        };
    }

    private String authenticated(UssdSession s, String prefix) {
        s.setAuthenticated(true);
        s.setClientState(EClientState.ACTIVE);
        s.setStep(null);
        s.setLevel(CommonMenus.LEVEL_MAIN_MENU);
        return prefix + headings.mainMenu(s.getLanguage());
    }

    // ── Level 4: main menu ────────────────────────────────────────────────

    private String handleMainMenu(UssdSession s, String input) {
        String l = s.getLanguage();
        if (!s.isAuthenticated()) return common.unavailable(s);
        String service = switch (input) {
            case "1" -> LoanMenu.SERVICE;
            case "2" -> RepayLoanMenu.SERVICE;
            case "3" -> AccountInfoMenu.SERVICE;
            case "4" -> SettingsMenu.SERVICE;
            case "5" -> HelpMenu.SERVICE;
            case "0" -> "exit";
            default -> null;
        };
        if ("exit".equals(service)) {
            return common.endWith(s, ESessionEndReason.USER_EXIT, headings.thankYou(l));
        }
        if (service == null) return headings.invalidChoice(l) + headings.mainMenu(l);

        s.resetServiceState();
        s.setService(service);
        s.setLevel(CommonMenus.LEVEL_SERVICE);
        return dispatch(s, null);
    }

    // ── Level 5: service menus ────────────────────────────────────────────

    private String handleServiceLevel(UssdSession s, String input) {
        if (!s.isAuthenticated()) return common.unavailable(s);
        if ("00".equals(input) || s.getService() == null) return common.backToMain(s);
        return dispatch(s, input);
    }

    private String dispatch(UssdSession s, String input) {
        return switch (s.getService()) {
            case LoanMenu.SERVICE -> loanMenu.processMenu(s, input);
            case AccountInfoMenu.SERVICE -> accountInfoMenu.processMenu(s, input);
            case MiniStatementMenu.SERVICE -> miniStatementMenu.processMenu(s, input);
            case RepayLoanMenu.SERVICE -> repayLoanMenu.processMenu(s, input);
            case HelpMenu.SERVICE -> helpMenu.processMenu(s, input);
            case SettingsMenu.SERVICE -> settingsMenu.processMenu(s, input);
            default -> common.backToMain(s);
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════

    private String validate(UssdRequest req) {
        if (req.getSessionId() == null || !SESSION_ID_PATTERN.matcher(req.getSessionId()).matches()) {
            return "sessionId missing or invalid";
        }
        if (req.getMsisdn() == null || !MSISDN_PATTERN.matcher(req.getMsisdn().trim()).matches()) {
            return "msisdn missing or invalid";
        }
        if (req.getUserInput() != null && req.getUserInput().length() > MAX_INPUT_LENGTH) {
            return "userInput too long";
        }
        // Gateway sends e.g. "RW|MTN|662|105"; it must match inkomoko.ussd.service-code (662*105, later 311).
        String expected = UssdInputParser.normalizeServiceCode(countries.serviceCode());
        String given = UssdInputParser.normalizeServiceCode(req.getServiceCode());
        if (!expected.isEmpty() && !given.isEmpty() && !given.equals(expected)) {
            return "unexpected serviceCode " + given;
        }
        return null;
    }

    /** "gwTstamp|userInput" — stored to recognise gateway retries. */
    private static String requestKey(String gwTstamp, String rawInput) {
        return (gwTstamp == null ? "" : gwTstamp.trim()) + "|" + rawInput;
    }

    private static String rawPart(String requestKey) {
        if (requestKey == null) return null;
        int i = requestKey.indexOf('|');
        return i < 0 ? requestKey : requestKey.substring(i + 1);
    }

    private void markErrored(String sessionId, String msisdn) {
        try {
            sessionRepo.findBySessionIdAndMsisdn(sessionId, msisdn).ifPresent(s -> {
                s.end(ESessionEndReason.ERROR);
                s.setEndedAt(DateUtility.nowKigali());
                s.setUpdatedAt(DateUtility.nowKigali());
                sessionRepo.save(s);
            });
        } catch (Exception ignored) {
            // best effort
        }
    }

    private UssdResponse response(UssdRequest req, UssdSession s, String text, boolean cont) {
        return new UssdResponse(req.getSessionId(), req.getGwRef(), s != null ? s.getAppRef() : null,
                DateUtility.gatewayTimestamp(), text, cont ? 1 : 0);
    }

    private record Outcome(String text, boolean continueSession) {
    }

    private record Processed(Outcome outcome, UssdSession session) {
    }
}
