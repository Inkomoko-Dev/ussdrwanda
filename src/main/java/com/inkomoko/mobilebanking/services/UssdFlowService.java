package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.EClientState;
import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.ConsentLog;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.models.dto.ClientDto;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.repositories.ConsentLogRepository;
import com.inkomoko.mobilebanking.repositories.UssdSessionRepository;
import com.inkomoko.mobilebanking.services.integration.FineractService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.GeneralMethods;
import com.inkomoko.mobilebanking.utils.PhoneUtils;
import com.inkomoko.mobilebanking.utils.UssdInputParser;
import com.inkomoko.mobilebanking.utils.Utility;
import com.inkomoko.mobilebanking.utils.menus.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared USSD session engine used by both the Comviva (MTN/Airtel) and FDI gateway adapters.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UssdFlowService {

    public static final int LEVEL_LANGUAGE = 1;
    public static final int LEVEL_CONSENT = 2;
    public static final int LEVEL_AUTH = 3;

    public static final String STEP_PIN_SETUP = "PIN_SETUP";
    public static final String STEP_PIN_CONFIRM = "PIN_CONFIRM";
    private static final String STEP_PIN_ENTRY = "PIN_ENTRY";

    private static final int MAX_INPUT_LENGTH = 200;

    private final UssdSessionRepository sessionRepo;
    private final ConsentLogRepository consentRepo;
    private final ConsentService consentService;
    private final LanguagePreferenceService languagePreferenceService;
    private final PinService pinService;
    private final FineractService fineract;
    private final AuditService audit;
    private final InkomokoProperties props;
    private final CountryService countries;

    private final LoanMenu loanMenu;
    private final AccountInfoMenu accountInfoMenu;
    private final MiniStatementMenu miniStatementMenu;
    private final RepayLoanMenu repayLoanMenu;
    private final HelpMenu helpMenu;
    private final SettingsMenu settingsMenu;
    private final CommonMenus common;
    private final CommonHeadings headings;

    /**
     * @param newRequest {@code null} for FDI (create when the session is unknown);
     *                   {@code 1}/{@code 0} for Comviva (explicit new vs ongoing).
     */
    public record Inbound(String sessionId, String msisdn, String rawInput, String networkCode,
                          String serviceCode, String gwRef, String gwTstamp, Integer newRequest) {
    }

    public record Result(String text, boolean continueSession, UssdSession session) {
    }

    public Result handle(Inbound req) {
        long started = System.nanoTime();
        try {
            return process(req, started).toResult();
        } catch (Exception e) {
            log.error("USSD unhandled error: msisdn={} sessionId={} error={}", req.msisdn(), req.sessionId(),
                    e.getMessage(), e);
            String lang = languagePreferenceService.preferredLanguage(req.msisdn());
            markErrored(req.sessionId(), req.msisdn());
            return new Result(headings.serviceUnavailable(lang), false, null);
        }
    }

    private Processed process(Inbound req, long started) {
        String dialled = UssdInputParser.normalizeServiceCode(req.serviceCode());
        String code = dialled.isEmpty() ? countries.serviceCode() : dialled;
        String rawInput = req.rawInput() == null ? "" : req.rawInput().trim();
        List<String> segments = UssdInputParser.segments(rawInput, code);

        Optional<UssdSession> existing = sessionRepo.findBySessionIdAndMsisdn(req.sessionId(), req.msisdn());

        if (req.newRequest() != null) {
            if (req.newRequest() == 1) {
                if (existing.isPresent()) {
                    throw new IllegalArgumentException("for newRequest=1, sessionId must not already exist");
                }
                UssdSession s = createSession(req, segments.size());
                return finish(s, requestKey(req.gwTstamp(), rawInput), beginSession(s), started);
            }
            if (existing.isEmpty()) {
                throw new IllegalArgumentException("for newRequest=0, sessionId must already exist");
            }
        } else if (existing.isEmpty()) {
            UssdSession s = createSession(req, segments.size());
            return finish(s, requestKey(req.gwTstamp(), rawInput), beginSession(s), started);
        }

        UssdSession s = existing.get();
        String lang = s.getLanguage() != null ? s.getLanguage() : languagePreferenceService.defaultLanguage();

        if (s.isCloseCurrentSession()) {
            return new Processed(new Outcome(headings.sessionExpired(lang, countries.serviceCode()), false), s);
        }

        // Gateway retry of the same request → replay the previous screen (no double processing).
        // Require a gateway timestamp (FDI) or a cumulative dial string — bare Comviva single-digit
        // repeats are real user choices, not retries.
        String previousRaw = rawPart(s.getLastRawInput());
        String key = requestKey(req.gwTstamp(), rawInput);
        boolean hasGwTstamp = req.gwTstamp() != null && !req.gwTstamp().isBlank();
        boolean retry = rawInput.equals(previousRaw) && s.getLastResponse() != null && key.equals(s.getLastRawInput())
                && (segments.size() > 1 || hasGwTstamp);
        if (retry) {
            log.info("USSD duplicate request replayed: sessionId={}", s.getSessionId());
            return new Processed(new Outcome(s.getLastResponse(),
                    Integer.valueOf(1).equals(s.getLastContinueSession())), s);
        }

        LocalDateTime now = DateUtility.nowKigali();
        if (s.getUpdatedAt() != null
                && s.getUpdatedAt().isBefore(now.minusSeconds(props.getUssd().getSessionTimeoutSeconds()))) {
            s.end(ESessionEndReason.ABANDONED);
            return finish(s, key, headings.sessionExpired(lang, countries.serviceCode()), started);
        }

        String input = latestInput(s, segments);
        if (req.gwRef() != null && !req.gwRef().isBlank()) s.setGwRef(req.gwRef());
        String text = route(s, input);
        return finish(s, key, text, started);
    }

    private String latestInput(UssdSession s, List<String> segments) {
        int processed = s.getProcessedInputs() == null ? 0 : s.getProcessedInputs();
        s.setProcessedInputs(Math.max(processed, segments.size()));
        if (segments.isEmpty()) return "";
        return segments.get(segments.size() - 1).trim();
    }

    private UssdSession createSession(Inbound req, int initialSegments) {
        LocalDateTime now = DateUtility.nowKigali();
        sessionRepo.closeOtherOpenSessions(req.msisdn(), req.sessionId(), ESessionEndReason.ABANDONED, now);

        String network = req.networkCode();
        if (network == null || network.isBlank()) {
            network = PhoneUtils.networkCodeFromMsisdn(req.msisdn());
        }

        UssdSession s = new UssdSession();
        s.setSessionId(req.sessionId());
        s.setMsisdn(req.msisdn());
        s.setGwRef(req.gwRef());
        s.setAppRef(Utility.generateAppRef());
        s.setNetworkCode(network);
        s.setLevel(0);
        s.setLanguage(languagePreferenceService.chosenLanguage(req.msisdn()).orElse(null));
        s.setProcessedInputs(initialSegments);
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        audit.ussd(req.msisdn(), req.sessionId(), "SESSION_START", network, true, null);
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
            log.warn("USSD concurrent session create: sessionId={}", s.getSessionId());
        }
        return new Processed(new Outcome(text, cont), s);
    }

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

    private String handleLanguage(UssdSession s, String input) {
        List<String> languages = countries.languages();
        int choice = GeneralMethods.parseInputAsInteger(input);
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

    private void logClientCheck(UssdSession s, Optional<ClientDto> client) {
        if (client.isEmpty()) {
            log.info("USSD client check | msisdn={} sessionId={} -> NOT REGISTERED in core banking, ending session",
                    s.getMsisdn(), s.getSessionId());
        } else if (!client.get().active()) {
            log.info("USSD client check | msisdn={} sessionId={} clientId={} -> INACTIVE in core banking, ending session",
                    s.getMsisdn(), s.getSessionId(), client.get().clientId());
        } else {
            log.info("USSD client check | msisdn={} sessionId={} clientId={} name={} -> active in core banking",
                    s.getMsisdn(), s.getSessionId(), client.get().clientId(), client.get().displayName());
        }
    }

    private String eligibilityGate(UssdSession s, String lang) {
        Optional<ClientDto> client;
        try {
            client = fineract.findClientByMsisdn(s.getMsisdn());
        } catch (Exception e) {
            log.error("USSD client check | msisdn={} sessionId={} -> CBS ERROR, showing service unavailable: {}",
                    s.getMsisdn(), s.getSessionId(), e.getMessage());
            s.setLanguage(lang);
            return common.unavailable(s);
        }
        logClientCheck(s, client);
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
        log.info("USSD client check | msisdn={} sessionId={} clientId={} -> onboarding/PIN state {}",
                s.getMsisdn(), s.getSessionId(), c.clientId(), state);
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

    private String identifyClient(UssdSession s) {
        String lang = s.getLanguage();
        Optional<ClientDto> client;
        try {
            client = fineract.findClientByMsisdn(s.getMsisdn());
        } catch (Exception e) {
            log.error("USSD client check | msisdn={} sessionId={} -> CBS ERROR, showing service unavailable: {}",
                    s.getMsisdn(), s.getSessionId(), e.getMessage());
            return common.unavailable(s);
        }
        logClientCheck(s, client);
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
        log.info("USSD client check | msisdn={} sessionId={} clientId={} -> onboarding/PIN state {}",
                s.getMsisdn(), s.getSessionId(), c.clientId(), state);
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
                yield headings.enterPin(lang, props.getUssd().getPinLength());
            }
        };
    }

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
        s.setPendingPinHash(pinService.hash(input));
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
                yield authenticated(s);
            }
            case WRONG -> {
                audit.ussd(s.getMsisdn(), s.getSessionId(), "LOGIN_FAILED", client, false, null);
                yield headings.wrongPin(l, pinService.remainingAttempts(s.getMsisdn()))
                        + headings.enterPin(l, props.getUssd().getPinLength());
            }
            case LOCKED -> {
                audit.ussd(s.getMsisdn(), s.getSessionId(), "LOGIN_FAILED", client, false, null);
                audit.ussd(s.getMsisdn(), s.getSessionId(), "PIN_LOCKED", client, false, null);
                yield common.endWith(s, ESessionEndReason.ACCOUNT_LOCKED, headings.pinLocked(l, common.helpline()));
            }
        };
    }

    private String authenticated(UssdSession s) {
        s.setAuthenticated(true);
        s.setClientState(EClientState.ACTIVE);
        s.setStep(null);
        s.setLevel(CommonMenus.LEVEL_MAIN_MENU);
        return headings.mainMenu(s.getLanguage());
    }

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

    private record Outcome(String text, boolean continueSession) {
    }

    private record Processed(Outcome outcome, UssdSession session) {
        Result toResult() {
            return new Result(outcome.text(), outcome.continueSession(), session);
        }
    }
}
