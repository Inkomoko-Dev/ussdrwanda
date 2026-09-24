package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.models.FaqItem;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.FaqService;
import com.inkomoko.mobilebanking.utils.GeneralMethods;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
    private final CommonHeadings headings;
    private final FaqService faqService;
    private final AuditService audit;

    public String processMenu(UssdSession s, String input) {
        String l = s.getLanguage();
        List<FaqItem> items = faqService.activeItems(l).stream().limit(MAX_ITEMS).toList();

        if (s.getStep() == null) {
            audit.ussd(s.getMsisdn(), s.getSessionId(), "HELP_VIEWED", null, true, null);
            return list(s, items);
        }
        if (STEP_ITEM.equals(s.getStep())) {
            if ("0".equals(input)) return list(s, items);
            return common.invalidThen(s, headings.back(l).trim());
        }
        // STEP_LIST
        if ("0".equals(input)) return common.backToMain(s);
        int choice = GeneralMethods.parseInputAsInteger(input);
        if (choice < 1 || choice > items.size()) return headings.invalidChoice(l) + list(s, items);
        s.setStep(STEP_ITEM);
        return items.get(choice - 1).getBody() + headings.back(l);
    }

    private String list(UssdSession s, List<FaqItem> items) {
        String l = s.getLanguage();
        s.setStep(STEP_LIST);
        StringBuilder sb = new StringBuilder(headings.text(l, "help.header"));
        if (items.isEmpty()) {
            sb.append('\n').append(headings.text(l, "help.call", Map.of("helpline", common.helpline())));
        }
        int i = 1;
        for (FaqItem item : items) sb.append('\n').append(i++).append(". ").append(item.getTitle());
        return sb.append(headings.back(l)).toString();
    }
}
