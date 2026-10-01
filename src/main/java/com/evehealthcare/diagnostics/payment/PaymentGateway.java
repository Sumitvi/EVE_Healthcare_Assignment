package com.evehealthcare.diagnostics.payment;

import java.math.BigDecimal;

/**
 * Boundary to the payment provider. Only a simulator exists (see {@link SimulatedPaymentGateway}); the interface
 * keeps {@link PaymentService} independent of how the outcome is produced, and makes it deterministic in tests.
 */
public interface PaymentGateway {

    GatewayResult charge(Long bookingId, BigDecimal amount);

    /** Outcome of a charge. {@code failureReason} is null when the charge succeeded. */
    record GatewayResult(PaymentStatus status, String transactionReference, String failureReason) {
    }
}
