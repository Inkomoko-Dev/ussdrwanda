package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.UssdMessageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UssdMessageHistoryRepository extends JpaRepository<UssdMessageHistory, Long> {

    List<UssdMessageHistory> findTop50ByMsgKeyOrderByChangedAtDesc(String msgKey);
}
