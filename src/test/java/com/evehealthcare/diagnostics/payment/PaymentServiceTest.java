package com.evehealthcare.diagnostics.payment;

import static com.evehealthcare.diagnostics.payment.PaymentDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.evehealthcare.diagnostics.booking.Booking;
import com.evehealthcare.diagnostics.booking.BookingService;
import com.evehealthcare.diagnostics.booking.BookingStatus;
import com.evehealthcare.diagnostics.exception.ApiException;
import com.evehealthcare.diagnostics.payment.PaymentGateway.GatewayResult;
import com.evehealthcare.diagnostics.security.AuthUser;
import com.evehealthcare.diagnostics.user.Role;
import com.evehealthcare.diagnostics.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime FUTURE = LOCAL_NOW.plusDays(5);

    private static final AuthUser OWNER = new AuthUser(1L, "owner@eve.test", Role.USER);
    private static final AuthUser OTHER = new AuthUser(2L, "other@eve.test", Role.USER);

    @Mock
    PaymentRepository payments;
    @Mock
    BookingService bookingService;
    @Mock
    PaymentGateway gateway;

    PaymentService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new PaymentService(payments, bookingService, gateway, clock);
    }

    // ---------- successful payment ----------

    @Test
    void pay_success_confirmsBooking_andChargesServerSideAmount() {
        Booking b = pendingBooking(5L, "999.00");
        givenOwnedBooking(b);
        when(gateway.charge(5L, b.getAmount())).thenReturn(success("SIM-1"));
        givenPaymentSaved(7L);
        givenTransitionApplied();

        PaymentResponse res = service.pay(OWNER, new CreatePaymentRequest(5L, null));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(saved.capture());
        Payment p = saved.getValue();
        assertThat(p.getBooking()).isSameAs(b);
        assertThat(p.getAmount()).isEqualByComparingTo("999.00");   // from the booking
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(p.getTransactionReference()).isEqualTo("SIM-1");
        assertThat(p.getFailureReason()).isNull();
        verify(bookingService).transition(b, BookingStatus.CONFIRMED);

        assertThat(res.id()).isEqualTo(7L);
        assertThat(res.bookingId()).isEqualTo(5L);
        assertThat(res.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(res.bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(res.amount()).isEqualByComparingTo("999.00");
    }

    @Test
    void pay_clientAmountEqualToBookingAmount_ignoringScale_isAccepted() {
        Booking b = pendingBooking(5L, "999.00");
        givenOwnedBooking(b);
        when(gateway.charge(5L, b.getAmount())).thenReturn(success("SIM-1"));
        givenPaymentSaved(7L);
        givenTransitionApplied();

        PaymentResponse res = service.pay(OWNER, new CreatePaymentRequest(5L, new BigDecimal("999")));

        assertThat(res.status()).isEqualTo(PaymentStatus.SUCCESS);
    }

    // ---------- failed payment ----------

    @Test
    void pay_declinedByGateway_recordsFailedPayment_andFailsBooking() {
        Booking b = pendingBooking(5L, "999.00");
        givenOwnedBooking(b);
        when(gateway.charge(5L, b.getAmount()))
                .thenReturn(new GatewayResult(PaymentStatus.FAILED, "SIM-2", "Simulated payment declined"));
        givenPaymentSaved(8L);
        givenTransitionApplied();

        PaymentResponse res = service.pay(OWNER, new CreatePaymentRequest(5L, null));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getValue().getFailureReason()).isEqualTo("Simulated payment declined");
        verify(bookingService).transition(b, BookingStatus.FAILED);

        assertThat(res.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(res.bookingStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(res.failureReason()).isEqualTo("Simulated payment declined");
    }

    // ---------- booking not found / unauthorized ----------

    @Test
    void pay_unknownBooking_isNotFound_andNothingHappens() {
        when(bookingService.getOwnedForUpdate(404L, OWNER))
                .thenThrow(ApiException.notFound("Booking 404 not found"));

        assertApiError(() -> service.pay(OWNER, new CreatePaymentRequest(404L, null)), HttpStatus.NOT_FOUND);
        verifyNoInteractions(gateway, payments);
        verify(bookingService, never()).transition(any(), any());
    }

    @Test
    void pay_someoneElsesBooking_isForbidden_andNothingHappens() {
        when(bookingService.getOwnedForUpdate(5L, OTHER))
                .thenThrow(ApiException.forbidden("You do not have access to this booking"));

        assertApiError(() -> service.pay(OTHER, new CreatePaymentRequest(5L, null)), HttpStatus.FORBIDDEN);
        verifyNoInteractions(gateway, payments);
        verify(bookingService, never()).transition(any(), any());
    }

    // ---------- invalid booking state ----------

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CANCELLED", "FAILED"})
    void pay_bookingInNonPayableState_isConflict(BookingStatus status) {
        Booking b = booking(5L, 1L, status, FUTURE, "999.00");
        givenOwnedBooking(b);

        assertApiError(() -> service.pay(OWNER, new CreatePaymentRequest(5L, null)), HttpStatus.CONFLICT);
        assertThat(b.getStatus()).isEqualTo(status);
        verifyNoInteractions(gateway);
        verify(payments, never()).saveAndFlush(any());
        verify(bookingService, never()).transition(any(), any());
    }

    @Test
    void pay_appointmentAlreadyPassed_isConflict() {
        Booking b = booking(5L, 1L, BookingStatus.PENDING, LOCAL_NOW.minusMinutes(1), "999.00");
        givenOwnedBooking(b);

        assertApiError(() -> service.pay(OWNER, new CreatePaymentRequest(5L, null)), HttpStatus.CONFLICT);
        verifyNoInteractions(gateway);
        verify(payments, never()).saveAndFlush(any());
    }

    // ---------- duplicate payment ----------

    @Test
    void pay_alreadyPaidBooking_isConflict_andGatewayNotCalledAgain() {
        Booking b = booking(5L, 1L, BookingStatus.CONFIRMED, FUTURE, "999.00");
        givenOwnedBooking(b);

        assertThatThrownBy(() -> service.pay(OWNER, new CreatePaymentRequest(5L, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).containsIgnoringCase("already been paid");
                });
        verifyNoInteractions(gateway);
        verify(payments, never()).saveAndFlush(any());
        verify(bookingService, never()).transition(any(), any());
    }

    @Test
    void pay_pendingBookingThatAlreadyHasPayment_isConflict() {
        Booking b = pendingBooking(5L, "999.00");
        givenOwnedBooking(b);
        when(payments.existsByBookingId(5L)).thenReturn(true);

        assertApiError(() -> service.pay(OWNER, new CreatePaymentRequest(5L, null)), HttpStatus.CONFLICT);
        verifyNoInteractions(gateway);
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void pay_uniqueConstraintViolation_propagates_andBookingIsNotTouched() {
        Booking b = pendingBooking(5L, "999.00");
        givenOwnedBooking(b);
        when(gateway.charge(5L, b.getAmount())).thenReturn(success("SIM-1"));
        when(payments.saveAndFlush(any(Payment.class))).thenThrow(new DataIntegrityViolationException("uk_payments_booking"));

        // GlobalExceptionHandler maps this to 409; @Transactional rolls the whole payment back
        assertThatThrownBy(() -> service.pay(OWNER, new CreatePaymentRequest(5L, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(bookingService, never()).transition(any(), any());
    }

    // ---------- amount mismatch ----------

    @Test
    void pay_clientAmountDiffersFromBooking_isUnprocessable_andNothingCharged() {
        Booking b = pendingBooking(5L, "999.00");
        givenOwnedBooking(b);

        assertApiError(() -> service.pay(OWNER, new CreatePaymentRequest(5L, new BigDecimal("1.00"))),
                HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING);
        verifyNoInteractions(gateway);
        verify(payments, never()).saveAndFlush(any());
        verify(bookingService, never()).transition(any(), any());
    }

    // ---------- helpers ----------

    private void givenOwnedBooking(Booking b) {
        when(bookingService.getOwnedForUpdate(b.getId(), OWNER)).thenReturn(b);
    }

    private void givenPaymentSaved(Long id) {
        when(payments.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(id);
            return p;
        });
    }

    private void givenTransitionApplied() {
        when(bookingService.transition(any(Booking.class), any(BookingStatus.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setStatus(inv.getArgument(1));
            return b;
        });
    }

    private static GatewayResult success(String ref) {
        return new GatewayResult(PaymentStatus.SUCCESS, ref, null);
    }

    private static void assertApiError(ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(expected));
    }

    private static Booking pendingBooking(Long id, String amount) {
        return booking(id, 1L, BookingStatus.PENDING, FUTURE, amount);
    }

    private static Booking booking(Long id, Long ownerId, BookingStatus status, LocalDateTime appointment,
                                   String amount) {
        User u = new User();
        u.setId(ownerId);
        u.setEmail("user" + ownerId + "@eve.test");
        u.setRole(Role.USER);
        Booking b = new Booking();
        b.setId(id);
        b.setUser(u);
        b.setAppointmentDateTime(appointment);
        b.setAmount(new BigDecimal(amount));
        b.setStatus(status);
        return b;
    }
}
