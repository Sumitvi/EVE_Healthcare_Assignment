package com.evehealthcare.diagnostics.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CentreTestRepository extends JpaRepository<CentreTest, Long> {

    @Query("select ct from CentreTest ct join fetch ct.test t where ct.centre.id = :centreId order by t.name")
    List<CentreTest> findByCentreIdWithTest(@Param("centreId") Long centreId);

    @Query("select ct from CentreTest ct join fetch ct.centre join fetch ct.test "
            + "where ct.centre.id = :centreId and ct.test.id = :testId")
    Optional<CentreTest> findByCentreIdAndTestId(@Param("centreId") Long centreId, @Param("testId") Long testId);

    boolean existsByCentreIdAndTestId(Long centreId, Long testId);
}
