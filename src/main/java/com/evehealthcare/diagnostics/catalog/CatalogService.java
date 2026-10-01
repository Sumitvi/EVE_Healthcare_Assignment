package com.evehealthcare.diagnostics.catalog;

import static com.evehealthcare.diagnostics.catalog.CatalogDtos.*;

import com.evehealthcare.diagnostics.common.PageResponse;
import com.evehealthcare.diagnostics.exception.ApiException;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final DiagnosticCentreRepository centres;
    private final DiagnosticTestRepository tests;
    private final CentreTestRepository centreTests;

    public CatalogService(DiagnosticCentreRepository centres, DiagnosticTestRepository tests,
                          CentreTestRepository centreTests) {
        this.centres = centres;
        this.tests = tests;
        this.centreTests = centreTests;
    }

    // ---------- tests ----------

    @Transactional
    public TestResponse createTest(CreateTestRequest req) {
        String name = req.name().trim();
        if (tests.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A test named '" + name + "' already exists");
        }
        DiagnosticTest test = new DiagnosticTest();
        test.setName(name);
        test.setDescription(req.description() == null ? null : req.description().trim());
        try {
            return TestResponse.from(tests.saveAndFlush(test));
        } catch (DataIntegrityViolationException race) {
            throw ApiException.conflict("A test named '" + name + "' already exists");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<TestResponse> listTests(String q, int page, int size) {
        var pageable = PageResponse.request(page, size, Sort.by("name"));
        String query = q == null ? "" : PageResponse.escapeLike(q);
        return PageResponse.of(tests.search(query, pageable), TestResponse::from);
    }

    @Transactional(readOnly = true)
    public TestResponse getTest(Long id) {
        return TestResponse.from(findTest(id));
    }

    // ---------- centres ----------

    @Transactional
    public CentreSummary createCentre(CreateCentreRequest req) {
        String name = req.name().trim();
        String location = req.location().trim();
        if (centres.existsByNameIgnoreCaseAndLocationIgnoreCase(name, location)) {
            throw ApiException.conflict("A centre named '" + name + "' already exists in " + location);
        }
        DiagnosticCentre centre = new DiagnosticCentre();
        centre.setName(name);
        centre.setLocation(location);
        centre.setAddress(req.address() == null ? null : req.address().trim());
        return CentreSummary.from(centres.save(centre));
    }

    /** Lists centres, optionally filtered by location and/or by the name of a test they offer. */
    @Transactional(readOnly = true)
    public PageResponse<CentreSummary> listCentres(String location, String testName, int page, int size) {
        Specification<DiagnosticCentre> spec = Specification.where(null);
        if (location != null && !location.isBlank()) {
            String pattern = "%" + PageResponse.escapeLike(location).toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("location")), pattern, '\\'));
        }
        if (testName != null && !testName.isBlank()) {
            String pattern = "%" + PageResponse.escapeLike(testName).toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> {
                Subquery<Long> sub = q.subquery(Long.class);
                Root<CentreTest> ct = sub.from(CentreTest.class);
                sub.select(ct.get("id")).where(
                        cb.equal(ct.get("centre"), root),
                        cb.like(cb.lower(ct.get("test").get("name")), pattern, '\\'));
                return cb.exists(sub);
            });
        }
        var pageable = PageResponse.request(page, size, Sort.by("name", "id"));
        return PageResponse.of(centres.findAll(spec, pageable), CentreSummary::from);
    }

    @Transactional(readOnly = true)
    public CentreDetail getCentre(Long id) {
        DiagnosticCentre centre = findCentre(id);
        return CentreDetail.from(centre, centreTests.findByCentreIdWithTest(id));
    }

    // ---------- centre <-> test offerings ----------

    @Transactional
    public CentreDetail addTestToCentre(Long centreId, AddCentreTestRequest req) {
        DiagnosticCentre centre = findCentre(centreId);
        DiagnosticTest test = tests.findById(req.testId())
                .orElseThrow(() -> ApiException.notFound("Test " + req.testId() + " not found"));
        if (centreTests.existsByCentreIdAndTestId(centreId, test.getId())) {
            throw ApiException.conflict("Centre already offers this test; update its price instead");
        }
        CentreTest ct = new CentreTest();
        ct.setCentre(centre);
        ct.setTest(test);
        ct.setPrice(req.price());
        try {
            centreTests.saveAndFlush(ct);
        } catch (DataIntegrityViolationException race) {
            throw ApiException.conflict("Centre already offers this test; update its price instead");
        }
        return CentreDetail.from(centre, centreTests.findByCentreIdWithTest(centreId));
    }

    @Transactional
    public CentreDetail updatePrice(Long centreId, Long testId, BigDecimal price) {
        findCentre(centreId);
        findTest(testId);
        CentreTest ct = centreTests.findByCentreIdAndTestId(centreId, testId)
                .orElseThrow(() -> ApiException.notFound("This centre does not offer test " + testId));
        // Existing bookings keep the amount they were created with (price is snapshotted on booking).
        ct.setPrice(price);
        return CentreDetail.from(ct.getCentre(), centreTests.findByCentreIdWithTest(centreId));
    }

    @Transactional
    public void removeTestFromCentre(Long centreId, Long testId) {
        findCentre(centreId);
        findTest(testId);
        CentreTest ct = centreTests.findByCentreIdAndTestId(centreId, testId)
                .orElseThrow(() -> ApiException.notFound("This centre does not offer test " + testId));
        centreTests.delete(ct);
    }

    /** Used by the booking module: the offering for (centre, test) or a 422-style error. */
    @Transactional(readOnly = true)
    public CentreTest requireOffering(Long centreId, Long testId) {
        findCentre(centreId);
        findTest(testId);
        return centreTests.findByCentreIdAndTestId(centreId, testId)
                .orElseThrow(() -> ApiException.unprocessable("This centre does not offer the requested test"));
    }

    private DiagnosticCentre findCentre(Long id) {
        return centres.findById(id).orElseThrow(() -> ApiException.notFound("Centre " + id + " not found"));
    }

    private DiagnosticTest findTest(Long id) {
        return tests.findById(id).orElseThrow(() -> ApiException.notFound("Test " + id + " not found"));
    }
}
