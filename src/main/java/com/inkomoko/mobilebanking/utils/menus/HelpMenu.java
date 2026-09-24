package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.enums.ELanguage;
import com.inkomoko.mobilebanking.models.FaqItem;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.FaqService;
import com.inkomoko.mobilebanking.utils.GeneralMethods;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 5. Help / FAQs (ToR 3.1(vi)) — content comes from the faq_item table (editable via
 * /api/admin/faqs), so office locations, helpline and answers change without a release.
 *
 *   entry  → list of titles
 *   LIST   → chosen item's text (0 = back to main menu)
 *   ITEM   → 0 = back to the list
 */
@Component
@RequiredArgsConstructor
public class HelpMenu {

    public static final String SERVICE = "help";
    private static final String STEP_LIST = "LIST";
    private static final String STEP_ITEM = "ITEM";
    private static final int MAX_ITEMS = 7;

    private final CommonMenus common;
    private final FaqService faqService;
    private final AuditService audit;

    public String processMenu(UssdSession s, String input) {
        ELanguage l = s.getLanguage();
        List<FaqItem> items = faqService.activeItems(l).stream().limit(MAX_ITEMS).toList();

        if (s.getStep() == null) {
            audit.ussd(s.getMsisdn(), s.getSessionId(), "HELP_VIEWED", null, true, null);
            return list(s, items);
        }
        if (STEP_ITEM.equals(s.getStep())) {
            if ("0".equals(input)) return list(s, items);
            return common.invalidThen(s, CommonHeadings.back(l).trim());
        }
        // STEP_LIST
        if ("0".equals(input)) return common.backToMain(s);
        int choice = GeneralMethods.parseInputAsInteger(input);
        if (choice < 1 || choice > items.size()) return CommonHeadings.invalidChoice(l) + list(s, items);
        s.setStep(STEP_ITEM);
        return items.get(choice - 1).getBody() + CommonHeadings.back(l);
    }

    private String list(UssdSession s, List<FaqItem> items) {
        ELanguage l = s.getLanguage();
        s.setStep(STEP_LIST);
        StringBuilder sb = new StringBuilder(l.pick("UBUFASHA", "HELP", "AIDE"));
        if (items.isEmpty()) {
            sb.append('\n').append(l.pick("Hamagara ", "Call ", "Appelez le ")).append(common.helpline());
        }
        int i = 1;
        for (FaqItem item : items) sb.append('\n').append(i++).append(". ").append(item.getTitle());
        return sb.append(CommonHeadings.back(l)).toString();
    }
}
