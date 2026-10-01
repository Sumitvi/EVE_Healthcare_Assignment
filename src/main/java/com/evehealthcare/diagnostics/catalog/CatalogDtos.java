package com.evehealthcare.diagnostics.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CreateTestRequest(
            @NotBlank(message = "name is required") @Size(max = 150, message = "name must be at most 150 characters")
            String name,
            @Size(max = 500, message = "description must be at most 500 characters") String description) {
    }

    public record TestResponse(Long id, String name, String description) {
        public static TestResponse from(DiagnosticTest t) {
            return new TestResponse(t.getId(), t.getName(), t.getDescription());
        }
    }

    public record CreateCentreRequest(
            @NotBlank(message = "name is required") @Size(max = 150, message = "name must be at most 150 characters")
            String name,
            @NotBlank(message = "location is required")
            @Size(max = 150, message = "location must be at most 150 characters")
            String location,
            @Size(max = 255, message = "address must be at most 255 characters") String address) {
    }

    public record AddCentreTestRequest(
            @NotNull(message = "testId is required") Long testId,
            @NotNull(message = "price is required")
            @DecimalMin(value = "0.01", message = "price must be greater than 0")
            @Digits(integer = 8, fraction = 2, message = "price must have at most 8 integer and 2 decimal digits")
            BigDecimal price) {
    }

    public record UpdatePriceRequest(
            @NotNull(message = "price is required")
            @DecimalMin(value = "0.01", message = "price must be greater than 0")
            @Digits(integer = 8, fraction = 2, message = "price must have at most 8 integer and 2 decimal digits")
            BigDecimal price) {
    }

    public record CentreTestResponse(Long testId, String testName, String description, BigDecimal price) {
        public static CentreTestResponse from(CentreTest ct) {
            return new CentreTestResponse(ct.getTest().getId(), ct.getTest().getName(),
                    ct.getTest().getDescription(), ct.getPrice());
        }
    }

    /** Summary used in list responses (no tests, avoids N+1 queries). */
    public record CentreSummary(Long id, String name, String location, String address) {
        public static CentreSummary from(DiagnosticCentre c) {
            return new CentreSummary(c.getId(), c.getName(), c.getLocation(), c.getAddress());
        }
    }

    public record CentreDetail(Long id, String name, String location, String address,
                               List<CentreTestResponse> tests) {
        public static CentreDetail from(DiagnosticCentre c, List<CentreTest> tests) {
            return new CentreDetail(c.getId(), c.getName(), c.getLocation(), c.getAddress(),
                    tests.stream().map(CentreTestResponse::from).toList());
        }
    }
}
