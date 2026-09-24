package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.enums.ELanguage;

/**
 * USSD screen text in Kinyarwanda, English and French (proposal §3.3.1).
 * Kinyarwanda and French wording is illustrative, pending native-speaker review before UAT.
 * GSM-7 friendly (no accents). Keep every screen within ~182 characters.
 */
public final class CommonHeadings {

    private CommonHeadings() {
    }

    // ── Screen 0: consent (bilingual, shown before language selection) ─────
    public static final String CONSENT =
            "By continuing, you consent to Inkomoko's Privacy Notice.\n"
            + "Ukomeje, wemeye Itangazo ry'Ibanga rya Inkomoko.\n"
            + "1. Komeza / Continue\n"
            + "2. Hagarika / Exit";

    public static final String CONSENT_NOTICE_VERSION = "v1";

    // ── Screen 1: language ────────────────────────────────────────────────
    public static final String LANGUAGE_MENU =
            "Murakaza neza kuri Inkomoko / Welcome to Inkomoko\n"
            + "1. Kinyarwanda\n"
            + "2. English\n"
            + "3. Francais";

    public static final String INVALID_BILINGUAL = "Ntibyemewe / Invalid choice.\n";
    public static final String GOODBYE_BILINGUAL = "Murakoze / Thank you.";

    // ── Screen 3: main menu ───────────────────────────────────────────────
    public static String mainMenu(ELanguage l) {
        return l.pick(
                "MENU NYAMUKURU\n1. Kureba amafaranga asigaye\n2. Uko ideni rihagaze\n3. Amakuru y'imyishyurire\n"
                        + "4. Kwishyura ideni\n5. Ubufasha\n0. Gusohoka",
                "MAIN MENU\n1. Check Balance\n2. Loan Status\n3. Mini Statement\n4. Repay Loan\n5. Help / FAQs\n0. Exit",
                "MENU PRINCIPAL\n1. Consulter le solde\n2. Statut du pret\n3. Mini releve\n4. Rembourser le pret\n"
                        + "5. Aide / FAQ\n0. Quitter");
    }

    // ── Navigation / generic ──────────────────────────────────────────────
    public static String back(ELanguage l) {
        return l.pick("\n0. Subira inyuma", "\n0. Back", "\n0. Retour");
    }

    public static String invalidChoice(ELanguage l) {
        return l.pick("Ibyo wahisemo sibyo.\n", "Invalid choice.\n", "Choix invalide.\n");
    }

    public static String thankYou(ELanguage l) {
        return l.pick("Murakoze gukoresha Inkomoko.", "Thank you for using Inkomoko.", "Merci d'utiliser Inkomoko.");
    }

    public static String serviceUnavailable(ELanguage l) {
        return l.pick("Serivisi ntiboneka ubu. Mwongere mugerageze nyuma.",
                "Service temporarily unavailable. Please try again later.",
                "Service momentanement indisponible. Veuillez reessayer plus tard.");
    }

    public static String sessionExpired(ELanguage l, String serviceCode) {
        return l.pick("Iyi session yarangiye. Kanda *" + serviceCode + "# wongere.",
                "This session has ended. Dial *" + serviceCode + "# to start again.",
                "Cette session est terminee. Composez *" + serviceCode + "# pour recommencer.");
    }

    // ── Client identification ─────────────────────────────────────────────
    public static String notRegistered(ELanguage l, String helpline) {
        return l.pick("Iyi numero ntabwo yanditse muri Inkomoko. Hamagara " + helpline + " bagufashe.",
                "This number is not registered with Inkomoko. Please call " + helpline + " for help.",
                "Ce numero n'est pas enregistre chez Inkomoko. Appelez le " + helpline + ".");
    }

    public static String inactiveClient(ELanguage l, String helpline) {
        return l.pick("Umwirondoro wawe muri Inkomoko ntukora. Hamagara " + helpline + ".",
                "Your Inkomoko client profile is not active. Please call " + helpline + ".",
                "Votre profil client Inkomoko n'est pas actif. Appelez le " + helpline + ".");
    }

    // ── Screen 2a / 2b: PIN ───────────────────────────────────────────────
    public static String pinSetup(ELanguage l, int digits) {
        return l.pick("Shyiramo umubare w'ibanga (PIN) w'imibare " + digits,
                "Create your " + digits + "-digit PIN",
                "Creez votre code PIN a " + digits + " chiffres");
    }

    public static String pinConfirm(ELanguage l) {
        return l.pick("Emeza umubare w'ibanga (PIN)", "Confirm your PIN", "Confirmez votre PIN");
    }

    public static String pinMismatch(ELanguage l) {
        return l.pick("PIN ntizihura. Ongera utangire.\n", "PINs do not match. Start again.\n",
                "Les PIN ne correspondent pas. Recommencez.\n");
    }

    public static String pinBadFormat(ELanguage l, int digits) {
        return l.pick("PIN igomba kuba imibare " + digits + ".\n", "PIN must be " + digits + " digits.\n",
                "Le PIN doit comporter " + digits + " chiffres.\n");
    }

    public static String pinWeak(ELanguage l) {
        return l.pick("PIN yoroshye cyane (urugero 1111, 1234).\n", "PIN too easy to guess (e.g. 1111, 1234).\n",
                "PIN trop simple (ex. 1111, 1234).\n");
    }

    public static String pinCreated(ELanguage l) {
        return l.pick("PIN yawe yashyizweho.\n", "Your PIN has been set.\n", "Votre PIN a ete cree.\n");
    }

    public static String enterPin(ELanguage l, String firstName) {
        String name = firstName == null || firstName.isBlank() ? "" : " " + firstName;
        return l.pick("Muraho" + name + ",\nShyiramo umubare w'ibanga (PIN)",
                "Hello" + name + ",\nEnter your PIN",
                "Bonjour" + name + ",\nEntrez votre PIN");
    }

    public static String wrongPin(ELanguage l, int remaining) {
        return l.pick("PIN si yo. Usigaje amahirwe " + remaining + ".\n",
                "Wrong PIN. " + remaining + " attempt(s) left.\n",
                "PIN incorrect. " + remaining + " essai(s) restant(s).\n");
    }

    public static String pinLocked(ELanguage l, String helpline) {
        return l.pick("Konti yawe yafunzwe kubera PIN itari yo inshuro nyinshi. Hamagara " + helpline
                        + " kugira ngo bagufashe guhindura PIN.",
                "Your access is locked after too many wrong PINs. Call " + helpline
                        + " to verify your identity and reset your PIN.",
                "Acces bloque apres trop de PIN incorrects. Appelez le " + helpline
                        + " pour verifier votre identite et reinitialiser votre PIN.");
    }

    // ── Loans ─────────────────────────────────────────────────────────────
    public static String selectLoan(ELanguage l) {
        return l.pick("Hitamo ideni:", "Select loan:", "Choisissez le pret:");
    }

    public static String noActiveLoan(ELanguage l) {
        return l.pick("Nta deni rikora ufite.", "You have no active loan.", "Vous n'avez aucun pret actif.");
    }

    public static String noLoans(ELanguage l) {
        return l.pick("Nta deni ufite muri Inkomoko.", "You have no loans with Inkomoko.",
                "Vous n'avez aucun pret chez Inkomoko.");
    }
}
