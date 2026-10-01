package com.evehealthcare.diagnostics.payment;

import static com.evehealthcare.diagnostics.payment.PaymentDtos.*;

import com.evehealthcare.diagnostics.booking.Booking;
import com.evehealthcare.diagnostics.booking.BookingService;
import com.evehealthcare.diagnostics.booking.BookingStatus;
import com.evehealthcare.diagnostics.exception.ApiException;
import com.evehealthcare.diagnostics.payment.PaymentGateway.GatewayResult;
import com.evehealthcare.diagnostics.security.AuthUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final BookingService bookings;
    private final PaymentGateway gateway;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, BookingService bookings, PaymentGateway gateway, Clock clock) {
        this.payments = payments;
        this.bookings = bookings;
        this.gateway = gateway;
        this.clock = clock;
    }

    /**
     * Pays for the caller's own PENDING booking. One transaction covers: booking row lock, validation, the
     * (simulated) charge, the payment insert and the booking transition (PENDING -> CONFIRMED on SUCCESS,
     * PENDING -> FAILED on FAILED). Any exception rolls all of it back, so a booking and its payment can never
     * disagree. The row lock serialises concurrent attempts on the same booking: the second one sees a
     * non-PENDING booking and gets 409. The unique index on payments.booking_id is the last line of defence.
     */
    @Transactional
    public PaymentResponse pay(AuthUser actor, CreatePaymentRequest req) {
        // 404 if unknown, 403 if not the owner (ownership is checked before anything about its state leaks)
        Booking booking = bookings.getOwnedForUpdate(req.bookingId(), actor);
        assertPayable(booking);
        assertAmountMatches(booking, req.amount());

        GatewayResult result = gateway.charge(booking.getId(), booking.getAmount());

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getAmount());
        payment.setStatus(result.status());
        payment.setTransactionReference(result.transactionReference());
        payment.setFailureReason(result.failureReason());
        // flush now so a unique-constraint violation surfaces here, before the booking is touched
        Payment saved = payments.saveAndFlush(payment);

        BookingStatus target = result.status() == PaymentStatus.SUCCESS
                ? BookingStatus.CONFIRMED : BookingStatus.FAILED;
        Booking updated = bookings.transition(booking, target);

        log.info("Payment {} for booking {} by user {}: {} (booking now {})", saved.getTransactionReference(),
                booking.getId(), actor.id(), saved.getStatus(), updated.getStatus());
        return PaymentResponse.from(saved, updated.getStatus());
    }

    private void assertPayable(Booking booking) {
        switch (booking.getStatus()) {
            case PENDING -> { }
            case CONFIRMED -> throw ApiException.conflict("Booking has already been paid");
            case FAILED -> throw ApiException.conflict(
                    "Payment for this booking has already failed; please create a new booking");
            case CANCELLED -> throw ApiException.conflict("A cancelled booking cannot be paid");
        }
        if (payments.existsByBookingId(booking.getId())) {
            throw ApiException.conflict("A payment already exists for this booking");
        }
        if (!booking.getAppointmentDateTime().isAfter(LocalDateTime.now(clock))) {
            throw ApiException.conflict("The appointment time has already passed; please create a new booking");
        }
    }

    /** The client may echo the amount it saw; if so it must equal the booking's. Scale is ignored (10 == 10.00). */
    private void assertAmountMatches(Booking booking, BigDecimal clientAmount) {
        if (clientAmount != null && clientAmount.compareTo(booking.getAmount()) != 0) {
            throw ApiException.unprocessable("Amount does not match the booking amount of " + booking.getAmount());
        }
    }
}
