package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.CountryConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CountryConfigRepository extends JpaRepository<CountryConfig, String> {

    Optional<CountryConfig> findFirstByActiveTrue();

    List<CountryConfig> findAllByOrderByNameAsc();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CountryConfig c set c.active = false where c.active = true")
    int deactivateAll();
}
