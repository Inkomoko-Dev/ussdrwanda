package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.PasswordPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordPolicyRepository extends JpaRepository<PasswordPolicy, Integer> {
}
