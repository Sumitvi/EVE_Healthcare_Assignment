package com.evehealthcare.diagnostics.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "centre_tests",
        uniqueConstraints = @UniqueConstraint(name = "uq_centre_test", columnNames = {"centre_id", "test_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CentreTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "centre_id", nullable = false)
    private DiagnosticCentre centre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private DiagnosticTest test;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
}
