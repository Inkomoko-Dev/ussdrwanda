package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.demo.DemoClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DemoClientRepository extends JpaRepository<DemoClient, Long> {
    Optional<DemoClient> findByMsisdn(String msisdn);

    boolean existsByMsisdn(String msisdn);

    List<DemoClient> findAllByOrderByIdAsc();
}
