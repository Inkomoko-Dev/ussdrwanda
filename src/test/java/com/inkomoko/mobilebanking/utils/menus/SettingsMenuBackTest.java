package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.CountryService;
import com.inkomoko.mobilebanking.services.LanguagePreferenceService;
import com.inkomoko.mobilebanking.services.LanguageService;
import com.inkomoko.mobilebanking.services.PinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regression: {@code 0} steps back one screen; service root then goes Home. */
@ExtendWith(MockitoExtension.class)
class SettingsMenuBackTest {

    @Mock private CommonMenus common;
    @Mock private CommonHeadings headings;
    @Mock private LanguagePreferenceService languagePreferenceService;
    @Mock private LanguageService languageService;
    @Mock private PinService pinService;
    @Mock private AuditService audit;
    @Mock private InkomokoProperties props;
    @Mock private CountryService countries;

    @InjectMocks
    private SettingsMenu menu;

    @BeforeEach
    void stubs() {
        lenient().when(headings.text(eq("en"), eq("settings.menu"))).thenReturn("Settings\n1. Language\n2. PIN");
        lenient().when(headings.back("en")).thenReturn("\n0. Back\n00. Home");
        lenient().when(common.home(any())).thenReturn("MAIN");
    }

    @Test
    void zeroOnSettingsRootGoesHome() {
        UssdSession s = session("MENU");

        String out = menu.processMenu(s, "0");

        assertEquals("MAIN", out);
        verify(common).home(s);
    }

    @Test
    void zeroOnLanguageStepReturnsToSettingsMenu() {
        UssdSession s = session("LANGUAGE");

        String out = menu.processMenu(s, "0");

        assertEquals("Settings\n1. Language\n2. PIN\n0. Back\n00. Home", out);
        assertEquals("MENU", s.getStep());
    }

    private static UssdSession session(String step) {
        UssdSession s = new UssdSession();
        s.setLanguage("en");
        s.setService(SettingsMenu.SERVICE);
        s.setStep(step);
        s.setLevel(CommonMenus.LEVEL_SERVICE);
        return s;
    }
}
