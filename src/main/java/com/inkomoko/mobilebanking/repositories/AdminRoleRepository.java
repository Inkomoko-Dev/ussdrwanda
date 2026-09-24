package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.admin.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminRoleRepository extends JpaRepository<AdminRole, Long> {

    Optional<AdminRole> findFirstBySystemRoleTrue();

    boolean existsByNameIgnoreCase(String name);

    List<AdminRole> findAllByOrderByIdAsc();
}
