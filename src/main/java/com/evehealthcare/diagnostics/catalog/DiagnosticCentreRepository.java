package com.evehealthcare.diagnostics.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DiagnosticCentreRepository
        extends JpaRepository<DiagnosticCentre, Long>, JpaSpecificationExecutor<DiagnosticCentre> {

    boolean existsByNameIgnoreCaseAndLocationIgnoreCase(String name, String location);
}
