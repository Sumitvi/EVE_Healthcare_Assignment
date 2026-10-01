package com.evehealthcare.diagnostics.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evehealthcare.diagnostics.payment.PaymentGateway.GatewayResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SimulatedPaymentGatewayTest {

    private static final BigDecimal AMOUNT = new BigDecimal("999.00");

    @Test
    void successRateOne_alwaysSucceeds() {
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway(1.0);
        for (int i = 0; i < 50; i++) {
            GatewayResult r = gateway.charge(1L, AMOUNT);
            assertThat(r.status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(r.failureReason()).isNull();
            assertThat(r.transactionReference()).startsWith("SIM-");
        }
    }

    @Test
    void successRateZero_alwaysFails_withReason() {
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway(0.0);
        for (int i = 0; i < 50; i++) {
            GatewayResult r = gateway.charge(1L, AMOUNT);
            assertThat(r.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(r.failureReason()).isNotBlank();
        }
    }

    @Test
    void transactionReferences_areUnique_andFitTheColumn() {
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway(1.0);
        String a = gateway.charge(1L, AMOUNT).transactionReference();
        String b = gateway.charge(1L, AMOUNT).transactionReference();
        assertThat(a).isNotEqualTo(b);
        assertThat(a.length()).isLessThanOrEqualTo(64);
    }

    @Test
    void successRateOutsideZeroToOne_isRejected() {
        assertThatThrownBy(() -> new SimulatedPaymentGateway(-0.1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SimulatedPaymentGateway(1.1)).isInstanceOf(IllegalStateException.class);
    }
}
