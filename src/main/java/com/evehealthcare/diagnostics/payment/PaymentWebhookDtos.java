package com.evehealthcare.diagnostics.payment;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentWebhookDtos {

    private PaymentWebhookDtos() {
    }

    public record PaymentWebhookRequest(
            @NotBlank(message = "eventId is required")
            String eventId,
            @NotBlank(message = "transactionReference is required")
            String transactionReference,
            @NotNull(message = "status is required")
            PaymentStatus status,
            @NotNull(message = "amount is required")
            @Positive(message = "amount must be positive")
            @Digits(integer = 8, fraction = 2, message = "amount must have at most 8 integer digits and 2 decimals")
            BigDecimal amount) {
    }

    public record PaymentWebhookResponse(
            String eventId,
            boolean duplicate,
            Long paymentId,
            Long bookingId,
            PaymentStatus paymentStatus,
            String transactionReference,
            Instant processedAt) {
    }
}
