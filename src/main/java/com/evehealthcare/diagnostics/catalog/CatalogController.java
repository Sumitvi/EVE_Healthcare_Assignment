package com.evehealthcare.diagnostics.catalog;

import static com.evehealthcare.diagnostics.catalog.CatalogDtos.*;

import com.evehealthcare.diagnostics.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    // ----- tests -----

    @PostMapping({"/tests", "/tests/"})
    @ResponseStatus(HttpStatus.CREATED)
    public TestResponse createTest(@Valid @RequestBody CreateTestRequest request) {
        return service.createTest(request);
    }

    @GetMapping({"/tests", "/tests/"})
    public PageResponse<TestResponse> listTests(@RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return service.listTests(q, page, size);
    }

    @GetMapping("/tests/{id}")
    public TestResponse getTest(@PathVariable Long id) {
        return service.getTest(id);
    }

    // ----- centres -----

    @PostMapping({"/centres", "/centres/"})
    @ResponseStatus(HttpStatus.CREATED)
    public CentreSummary createCentre(@Valid @RequestBody CreateCentreRequest request) {
        return service.createCentre(request);
    }

    @GetMapping({"/centres", "/centres/"})
    public PageResponse<CentreSummary> listCentres(@RequestParam(required = false) String location,
                                                   @RequestParam(required = false) String test,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.listCentres(location, test, page, size);
    }

    @GetMapping("/centres/{id}")
    public CentreDetail getCentre(@PathVariable Long id) {
        return service.getCentre(id);
    }

    @PostMapping("/centres/{centreId}/tests")
    @ResponseStatus(HttpStatus.CREATED)
    public CentreDetail addTest(@PathVariable Long centreId, @Valid @RequestBody AddCentreTestRequest request) {
        return service.addTestToCentre(centreId, request);
    }

    @PutMapping("/centres/{centreId}/tests/{testId}")
    public CentreDetail updatePrice(@PathVariable Long centreId, @PathVariable Long testId,
                                    @Valid @RequestBody UpdatePriceRequest request) {
        return service.updatePrice(centreId, testId, request.price());
    }

    @DeleteMapping("/centres/{centreId}/tests/{testId}")
    public ResponseEntity<Void> removeTest(@PathVariable Long centreId, @PathVariable Long testId) {
        service.removeTestFromCentre(centreId, testId);
        return ResponseEntity.noContent().build();
    }
}
