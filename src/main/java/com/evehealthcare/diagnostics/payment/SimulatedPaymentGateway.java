package com.evehealthcare.diagnostics.payment;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    private final double successRate;

    public SimulatedPaymentGateway(@Value("${app.payment.success-rate}") double successRate) {
        if (successRate < 0.0 || successRate > 1.0) {
            throw new IllegalStateException("app.payment.success-rate must be between 0.0 and 1.0");
        }
        this.successRate = successRate;
    }

    @Override
    public GatewayResult charge(Long bookingId, BigDecimal amount) {
        String reference = "SIM-" + UUID.randomUUID();
        // nextDouble() is in [0, 1): a rate of 1.0 always succeeds and 0.0 never does
        boolean success = ThreadLocalRandom.current().nextDouble() < successRate;
        log.info("Simulated charge {} for booking {} amount {}: {}", reference, bookingId, amount,
                success ? "SUCCESS" : "FAILED");
        return success
                ? new GatewayResult(PaymentStatus.SUCCESS, reference, null)
                : new GatewayResult(PaymentStatus.FAILED, reference, "Simulated payment declined");
    }
}
