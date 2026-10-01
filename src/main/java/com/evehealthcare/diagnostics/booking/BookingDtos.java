package com.evehealthcare.diagnostics.booking;

import com.evehealthcare.diagnostics.catalog.DiagnosticCentre;
import com.evehealthcare.diagnostics.catalog.DiagnosticTest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public final class BookingDtos {

    private BookingDtos() {
    }



    @Schema(description = "Book a test at a centre. The patient, status and amount are set by the server.")
    public record CreateBookingRequest(
            @NotNull(message = "centreId is required") @Schema(example = "1") Long centreId,
            @NotNull(message = "testId is required") @Schema(example = "1") Long testId,
            @NotNull(message = "appointmentDateTime is required")
            @Schema(example = "2026-10-15T10:30:00",
                    description = "Local date-time at the centre (no zone offset). Must be in the future and no "
                            + "more than 90 days ahead.")
            LocalDateTime appointmentDateTime) {
    }

    public record CentreRef(Long id, String name, String location) {
        public static CentreRef from(DiagnosticCentre c) {
            return new CentreRef(c.getId(), c.getName(), c.getLocation());
        }
    }

    public record TestRef(Long id, String name) {
        public static TestRef from(DiagnosticTest t) {
            return new TestRef(t.getId(), t.getName());
        }
    }

    public record BookingResponse(Long id, Long userId, BookingStatus status, LocalDateTime appointmentDateTime,
                                  BigDecimal amount, CentreRef centre, TestRef test, Instant createdAt,
                                  Instant updatedAt) {
        public static BookingResponse from(Booking b) {
            return new BookingResponse(b.getId(), b.getUser().getId(), b.getStatus(), b.getAppointmentDateTime(),
                    b.getAmount(), CentreRef.from(b.getCentre()), TestRef.from(b.getTest()), b.getCreatedAt(),
                    b.getUpdatedAt());
        }
    }
}
