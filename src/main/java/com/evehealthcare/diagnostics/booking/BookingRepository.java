package com.evehealthcare.diagnostics.booking;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {


    @EntityGraph(attributePaths = {"test", "centre"})
    Page<Booking> findByUserId(Long userId, Pageable pageable);

    @Query("select b from Booking b join fetch b.test join fetch b.centre where b.id = :id")
    Optional<Booking> findWithDetailsById(@Param("id") Long id);



    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);
}
