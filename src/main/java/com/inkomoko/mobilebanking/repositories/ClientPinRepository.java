package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.enums.EPinStatus;
import com.inkomoko.mobilebanking.models.ClientPin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ClientPinRepository extends JpaRepository<ClientPin, Long> {
    Optional<ClientPin> findByMsisdn(String msisdn);

    long countByStatus(EPinStatus status);

    long countByLockedAtBetween(LocalDateTime from, LocalDateTime to);

    /** Portal customer list: free-text search on phone / name ("" = all). */
    @Query("select p from ClientPin p where p.msisdn like concat('%', :q, '%') " +
           "or lower(coalesce(p.clientName, '')) like lower(concat('%', :q, '%')) order by p.updatedAt desc")
    Page<ClientPin> search(@Param("q") String q, Pageable pageable);

    @Query("select p from ClientPin p where p.status = :status and (p.msisdn like concat('%', :q, '%') " +
           "or lower(coalesce(p.clientName, '')) like lower(concat('%', :q, '%'))) order by p.updatedAt desc")
    Page<ClientPin> searchByStatus(@Param("q") String q, @Param("status") EPinStatus status, Pageable pageable);
}
