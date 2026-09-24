package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.admin.AdminPasswordHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminPasswordHistoryRepository extends JpaRepository<AdminPasswordHistory, Long> {

    List<AdminPasswordHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
