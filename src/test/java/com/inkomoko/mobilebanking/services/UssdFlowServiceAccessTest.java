package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.EClientState;
import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.models.dto.ClientDto;
import com.inkomoko.mobilebanking.repositories.ConsentLogRepository;
import com.inkomoko.mobilebanking.repositories.UssdSessionRepository;
import com.inkomoko.mobilebanking.services.integration.FineractService;
import com.inkomoko.mobilebanking.utils.menus.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Access + navigation regressions for UAT:
 * <ul>
 *   <li>CBS client, not portal-onboarded → language menu (not "not linked")</li>
 *   <li>Unknown MSISDN → not-linked message, session ends</li>
 *   <li>{@code 00} at service level → Home (main menu)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UssdFlowServiceAccessTest {

    private static final String MSISDN = "250788000001";
    private static final String SESSION = "sess-1";
    private static final String LANG_MENU = "Welcome to Inkomoko\n1. English\n2. Kinyarwanda\n3. Francais";
    private static final String NOT_LINKED = "This number is not linked to any loan. Contact Inkomoko 0788363555.";
    private static final String MAIN = "Welcome to the Inkomoko Loan App\n1. Loan\n0. Exit";

    @Mock private UssdSessionRepository sessionRepo;
    @Mock private ConsentLogRepository consentRepo;
    @Mock private ConsentService consentService;
    @Mock private LanguagePreferenceService languagePreferenceService;
    @Mock private PinService pinService;
    @Mock private FineractService fineract;
    @Mock private AuditService audit;
    @Mock private InkomokoProperties props;
    @Mock private CountryService countries;
    @Mock private LoanMenu loanMenu;
    @Mock private AccountInfoMenu accountInfoMenu;
    @Mock private MiniStatementMenu miniStatementMenu;
    @Mock private RepayLoanMenu repayLoanMenu;
    @Mock private HelpMenu helpMenu;
    @Mock private SettingsMenu settingsMenu;
    @Mock private CommonMenus common;
    @Mock private CommonHeadings headings;

    @InjectMocks
    private UssdFlowService flow;

    private final InkomokoProperties.Ussd ussd = new InkomokoProperties.Ussd();

    @BeforeEach
    void stubs() {
        ussd.setServiceCode("662*105");
        ussd.setSessionTimeoutSeconds(120);
        ussd.setPinLength(4);
        lenient().when(props.getUssd()).thenReturn(ussd);
        lenient().when(countries.serviceCode()).thenReturn("662*105");
        lenient().when(countries.languages()).thenReturn(List.of("en", "rw", "fr"));
        lenient().when(languagePreferenceService.defaultLanguage()).thenReturn("en");
        lenient().when(languagePreferenceService.chosenLanguage(anyString())).thenReturn(Optional.empty());
        lenient().when(consentService.hasCurrentConsent(anyString())).thenReturn(false);
        lenient().when(sessionRepo.findBySessionIdAndMsisdn(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(sessionRepo.save(any(UssdSession.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(common.helpline()).thenReturn("0788363555");
        lenient().when(common.endWith(any(), any(), anyString())).thenAnswer(inv -> {
            UssdSession s = inv.getArgument(0);
            s.end(inv.getArgument(1));
            return inv.getArgument(2);
        });
        lenient().when(headings.languageMenu(any())).thenReturn(LANG_MENU);
        lenient().when(headings.notRegistered(anyString(), anyString())).thenReturn(NOT_LINKED);
    }

    @Test
    void cbsClientNotOnboardedGetsLanguageMenu() {
        when(fineract.findClientByMsisdn(MSISDN))
                .thenReturn(Optional.of(new ClientDto(42L, "Jane Doe", MSISDN, null, true)));
        when(pinService.resolveState(eq(MSISDN), eq(42L), anyString())).thenReturn(EClientState.PIN_NOT_SET);

        UssdFlowService.Result r = dial();

        assertTrue(r.continueSession());
        assertEquals(LANG_MENU, r.text());
        assertEquals(UssdFlowService.LEVEL_LANGUAGE, r.session().getLevel());
        verify(headings, never()).notRegistered(anyString(), anyString());
        verify(headings, never()).notOnboarded(anyString(), anyString());
    }

    @Test
    void cbsClientMarkedNotOnboardedStillGetsLanguageMenu() {
        when(fineract.findClientByMsisdn(MSISDN))
                .thenReturn(Optional.of(new ClientDto(42L, "Jane Doe", MSISDN, null, true)));
        when(pinService.resolveState(eq(MSISDN), eq(42L), anyString())).thenReturn(EClientState.NOT_ONBOARDED);

        UssdFlowService.Result r = dial();

        assertTrue(r.continueSession());
        assertEquals(LANG_MENU, r.text());
        verify(common, never()).endWith(any(), eq(ESessionEndReason.NOT_REGISTERED), anyString());
    }

    @Test
    void unknownNumberGetsNotLinkedAndEnds() {
        when(fineract.findClientByMsisdn(MSISDN)).thenReturn(Optional.empty());

        UssdFlowService.Result r = dial();

        assertFalse(r.continueSession());
        assertEquals(NOT_LINKED, r.text());
        assertEquals(ESessionEndReason.NOT_REGISTERED, r.session().getEndReason());
        verify(headings, never()).languageMenu(any());
    }

    @Test
    void doubleZeroAtServiceLevelGoesHome() {
        UssdSession open = authenticatedServiceSession();
        when(sessionRepo.findBySessionIdAndMsisdn(SESSION, MSISDN)).thenReturn(Optional.of(open));
        when(common.home(open)).thenReturn(MAIN);

        UssdFlowService.Result r = flow.handle(new UssdFlowService.Inbound(
                SESSION, MSISDN, "00", "MTN", "662*105", "gw-1", "1700000000001", 0));

        assertTrue(r.continueSession());
        assertEquals(MAIN, r.text());
        verify(common).home(open);
        verify(settingsMenu, never()).processMenu(any(), any());
    }

    private UssdFlowService.Result dial() {
        return flow.handle(new UssdFlowService.Inbound(
                SESSION, MSISDN, "662*105", "MTN", "662*105", "gw-1", "1700000000000", 1));
    }

    private static UssdSession authenticatedServiceSession() {
        UssdSession s = new UssdSession();
        s.setSessionId(SESSION);
        s.setMsisdn(MSISDN);
        s.setLanguage("en");
        s.setAuthenticated(true);
        s.setConsentAccepted(true);
        s.setClientId(42L);
        s.setClientState(EClientState.ACTIVE);
        s.setLevel(CommonMenus.LEVEL_SERVICE);
        s.setService(SettingsMenu.SERVICE);
        s.setStep("MENU");
        s.setProcessedInputs(1);
        s.setRequestCount(2);
        s.setUpdatedAt(java.time.LocalDateTime.now());
        s.setCreatedAt(java.time.LocalDateTime.now());
        return s;
    }
}
