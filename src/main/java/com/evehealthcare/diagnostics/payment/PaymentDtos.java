package com.evehealthcare.diagnostics.payment;

import com.evehealthcare.diagnostics.booking.BookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {

    private PaymentDtos() {
    }


    @Schema(description = "Pay for one of your PENDING bookings. The charged amount always comes from the booking.")
    public record CreatePaymentRequest(
            @NotNull(message = "bookingId is required") @Positive(message = "bookingId must be positive")
            @Schema(example = "1") Long bookingId,
            @Positive(message = "amount must be positive")
            @Digits(integer = 8, fraction = 2, message = "amount must have at most 8 integer digits and 2 decimals")
            @Schema(example = "1499.50", nullable = true,
                    description = "Optional. If provided it must equal the booking amount, otherwise 422.")
            BigDecimal amount) {
    }

    public record PaymentResponse(Long id, Long bookingId, BookingStatus bookingStatus, BigDecimal amount,
                                  PaymentStatus status, String transactionReference, String failureReason,
                                  Instant createdAt, Instant updatedAt) {
        public static PaymentResponse from(Payment p, BookingStatus bookingStatus) {
            return new PaymentResponse(p.getId(), p.getBooking().getId(), bookingStatus, p.getAmount(),
                    p.getStatus(), p.getTransactionReference(), p.getFailureReason(), p.getCreatedAt(),
                    p.getUpdatedAt());
        }
    }
}
