package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.models.ConsentLog;
import com.inkomoko.mobilebanking.repositories.ConsentLogRepository;
import com.inkomoko.mobilebanking.utils.menus.CommonHeadings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Whether a phone has accepted the terms wording that is on screen today. */
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentLogRepository consentRepo;
    private final CommonHeadings headings;

    public boolean hasCurrentConsent(String msisdn) {
        return consentRepo.findByMsisdnAndAcceptedTrue(msisdn).stream().anyMatch(this::matchesCurrentWording);
    }

    public Set<String> withCurrentConsent(Collection<String> msisdns) {
        if (msisdns == null || msisdns.isEmpty()) return Set.of();
        Set<String> accepted = new HashSet<>();
        for (ConsentLog log : consentRepo.findByMsisdnInAndAcceptedTrue(msisdns)) {
            if (matchesCurrentWording(log)) accepted.add(log.getMsisdn());
        }
        return accepted;
    }

    private boolean matchesCurrentWording(ConsentLog log) {
        String version = log.getNoticeVersion();
        if (version == null) return false;
        int dash = version.indexOf('-');
        if (dash <= 0) return false;
        String lang = version.substring(0, dash);
        return version.equals(headings.consentVersion(lang));
    }
}
