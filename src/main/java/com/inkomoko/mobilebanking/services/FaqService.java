package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.enums.ELanguage;
import com.inkomoko.mobilebanking.models.FaqItem;
import com.inkomoko.mobilebanking.repositories.FaqItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FaqService {

    private final FaqItemRepository repo;

    /** Active items for the language, falling back to English when none are configured. */
    public List<FaqItem> activeItems(ELanguage language) {
        List<FaqItem> items = repo.findByLanguageAndActiveTrueOrderByDisplayOrderAscIdAsc(language);
        if (items.isEmpty() && language != ELanguage.ENGLISH) {
            items = repo.findByLanguageAndActiveTrueOrderByDisplayOrderAscIdAsc(ELanguage.ENGLISH);
        }
        return items;
    }
}
