package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.UssdMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UssdMessageRepository extends JpaRepository<UssdMessage, Long> {

    List<UssdMessage> findByLanguage(String language);

    Optional<UssdMessage> findByMsgKeyAndLanguageAndCountryCode(String msgKey, String language, String countryCode);
}
