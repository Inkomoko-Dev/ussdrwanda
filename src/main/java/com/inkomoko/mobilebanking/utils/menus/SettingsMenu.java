package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.LanguagePreferenceService;
import com.inkomoko.mobilebanking.services.LanguageService;
import com.inkomoko.mobilebanking.services.PinService;
import com.inkomoko.mobilebanking.utils.GeneralMethods;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 6. Settings — change the saved language or PIN. Shown only after a successful PIN login.
 *
 *   entry        → settings menu
 *   LANGUAGE     → pick a language (0 = back)
 *   PIN_CURRENT  → current PIN (same lockout as login)
 *   PIN_NEW      → new PIN
 *   PIN_CONFIRM  → confirm the new PIN
 */
@Component
@RequiredArgsConstructor
public class SettingsMenu {

    public static final String SERVICE = "settings";
    private static final String STEP_MENU = "MENU";
    private static final String STEP_LANGUAGE = "LANGUAGE";
    private static final String STEP_PIN_CURRENT = "PIN_CURRENT";
    private static final String STEP_PIN_NEW = "PIN_NEW";
    private static final String STEP_PIN_CONFIRM = "PIN_CONFIRM";

    private final CommonMenus common;
    private final CommonHeadings headings;
    private final LanguagePreferenceService languagePreferenceService;
    private final LanguageService languageService;
    private final PinService pinService;
    private final AuditService audit;
    private final InkomokoProperties props;
    private final com.inkomoko.mobilebanking.services.CountryService countries;

    public String processMenu(UssdSession s, String input) {
        if (s.getStep() == null) return menu(s);
        if ("0".equals(input)) return back(s);
        return switch (s.getStep()) {
            case STEP_MENU -> choose(s, input);
            case STEP_LANGUAGE -> changeLanguage(s, input);
            case STEP_PIN_CURRENT -> currentPin(s, input);
            case STEP_PIN_NEW -> newPin(s, input);
            case STEP_PIN_CONFIRM -> confirmPin(s, input);
            default -> menu(s);
        };
    }

    private String menu(UssdSession s) {
        s.setStep(STEP_MENU);
        s.setPendingPinHash(null);
        return headings.text(s.getLanguage(), "settings.menu") + headings.back(s.getLanguage());
    }

    private String back(UssdSession s) {
        if (STEP_MENU.equals(s.getStep())) return common.backToMain(s);
        return menu(s);
    }

    private String choose(UssdSession s, String input) {
        return switch (input) {
            case "1" -> languageList(s);
            case "2" -> askCurrentPin(s);
            default -> headings.invalidChoice(s.getLanguage()) + menu(s);
        };
    }

    private String languageList(UssdSession s) {
        s.setStep(STEP_LANGUAGE);
        StringBuilder sb = new StringBuilder(headings.text(s.getLanguage(), "settings.language"));
        List<String> languages = countries.languages();
        int i = 1;
        for (String code : languages) {
            sb.append('\n').append(i++).append(". ").append(languageService.nativeName(code));
        }
        return sb.append(headings.back(s.getLanguage())).toString();
    }

    private String changeLanguage(UssdSession s, String input) {
        List<String> languages = countries.languages();
        int choice = GeneralMethods.parseInputAsInteger(input);
        if (choice < 1 || choice > languages.size()) {
            return headings.invalidChoice(s.getLanguage()) + languageList(s);
        }
        String chosen = languages.get(choice - 1);
        s.setLanguage(chosen);
        languagePreferenceService.savePreferredLanguage(s.getMsisdn(), chosen);
        audit.ussd(s.getMsisdn(), s.getSessionId(), "LANGUAGE_CHANGED", chosen, true, null);
        s.setStep(null);
        return headings.text(chosen, "settings.language_saved") + "\n" + headings.text(chosen, "settings.menu")
                + headings.back(chosen);
    }

    private String askCurrentPin(UssdSession s) {
        s.setStep(STEP_PIN_CURRENT);
        s.setPendingPinHash(null);
        return headings.text(s.getLanguage(), "settings.pin_current") + headings.back(s.getLanguage());
    }

    private String currentPin(UssdSession s, String input) {
        String l = s.getLanguage();
        return switch (pinService.verify(s.getMsisdn(), input)) {
            case SUCCESS -> {
                s.setStep(STEP_PIN_NEW);
                yield headings.text(l, "settings.pin_new") + headings.back(l);
            }
            case WRONG -> headings.wrongPin(l, pinService.remainingAttempts(s.getMsisdn()))
                    + headings.text(l, "settings.pin_current") + headings.back(l);
            case LOCKED -> {
                audit.ussd(s.getMsisdn(), s.getSessionId(), "PIN_LOCKED", String.valueOf(s.getClientId()), false, null);
                yield common.endWith(s, ESessionEndReason.ACCOUNT_LOCKED, headings.pinLocked(l, common.helpline()));
            }
        };
    }

    private String newPin(UssdSession s, String input) {
        String l = s.getLanguage();
        int digits = props.getUssd().getPinLength();
        if (!pinService.isValidFormat(input)) {
            return headings.pinBadFormat(l, digits) + headings.text(l, "settings.pin_new") + headings.back(l);
        }
        if (pinService.isWeak(input)) {
            return headings.pinWeak(l) + headings.text(l, "settings.pin_new") + headings.back(l);
        }
        s.setPendingPinHash(pinService.hash(input));
        s.setStep(STEP_PIN_CONFIRM);
        return headings.text(l, "settings.pin_confirm") + headings.back(l);
    }

    private String confirmPin(UssdSession s, String input) {
        String l = s.getLanguage();
        boolean match = pinService.matches(input, s.getPendingPinHash());
        s.setPendingPinHash(null);
        if (!match) {
            s.setStep(STEP_PIN_NEW);
            return headings.pinMismatch(l) + headings.text(l, "settings.pin_new") + headings.back(l);
        }
        pinService.setPin(s.getMsisdn(), s.getClientId(), s.getClientName(), input);
        audit.ussd(s.getMsisdn(), s.getSessionId(), "PIN_CHANGED", String.valueOf(s.getClientId()), true, null);
        return common.endWith(s, ESessionEndReason.COMPLETED, headings.text(l, "settings.pin_changed"));
    }
}
