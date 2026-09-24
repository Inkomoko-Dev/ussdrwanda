package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.LanguageSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LanguageSettingRepository extends JpaRepository<LanguageSetting, Long> {
    Optional<LanguageSetting> findByMsisdn(String msisdn);
}
