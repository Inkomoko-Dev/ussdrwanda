package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.UssdLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UssdLanguageRepository extends JpaRepository<UssdLanguage, String> {

    List<UssdLanguage> findAllByOrderByBuiltInDescEnglishNameAsc();
}
