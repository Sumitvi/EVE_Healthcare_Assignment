package com.evehealthcare.diagnostics.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagnosticTestRepository extends JpaRepository<DiagnosticTest, Long> {

    boolean existsByNameIgnoreCase(String name);

    @Query("select t from DiagnosticTest t where lower(t.name) like lower(concat('%', :q, '%')) escape '\\'")
    Page<DiagnosticTest> search(@Param("q") String q, Pageable pageable);
}
