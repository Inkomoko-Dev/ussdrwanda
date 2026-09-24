package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.enums.ELanguage;
import com.inkomoko.mobilebanking.models.FaqItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqItemRepository extends JpaRepository<FaqItem, Long> {

    List<FaqItem> findByLanguageAndActiveTrueOrderByDisplayOrderAscIdAsc(ELanguage language);

    List<FaqItem> findAllByOrderByLanguageAscDisplayOrderAsc();
}
