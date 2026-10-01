package com.evehealthcare.diagnostics.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.evehealthcare.diagnostics.booking.Booking;
import com.evehealthcare.diagnostics.booking.BookingService;
import com.evehealthcare.diagnostics.booking.BookingStatus;
import com.evehealthcare.diagnostics.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentWebhookServiceTest {

    private PaymentWebhookEventRepository events;
    private PaymentRepository payments;
    private BookingService bookings;
    private PaymentWebhookService service;

    @BeforeEach
    void setUp() {
        events = mock(PaymentWebhookEventRepository.class);
        payments = mock(PaymentRepository.class);
        bookings = mock(BookingService.class);
        service = new PaymentWebhookService(events, payments, new ObjectMapper(), bookings);
    }

    @Test
    void newMatchingSuccessEventIsRecorded() {
        Payment payment = payment(PaymentStatus.SUCCESS, BookingStatus.CONFIRMED);
        when(events.findByEventId("evt-1")).thenReturn(Optional.empty());
        when(payments.findByTransactionReferenceForUpdate("SIM-1")).thenReturn(Optional.of(payment));
        when(events.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.process(payload("evt-1", "SIM-1", "SUCCESS", "100.00"));

        assertFalse(result.duplicate());
        assertEquals(PaymentStatus.SUCCESS, result.paymentStatus());
        verify(events).saveAndFlush(any(PaymentWebhookEvent.class));
        verifyNoInteractions(bookings);
    }

    @Test
    void repeatedSameEventIsSafeNoOp() {
        Payment payment = payment(PaymentStatus.SUCCESS, BookingStatus.CONFIRMED);
        PaymentWebhookEvent event = event("evt-1", "SIM-1", PaymentStatus.SUCCESS, "100.00");
        when(events.findByEventId("evt-1")).thenReturn(Optional.of(event));
        when(payments.findByTransactionReferenceForUpdate("SIM-1")).thenReturn(Optional.of(payment));

        var result = service.process(payload("evt-1", "SIM-1", "SUCCESS", "100.00"));

        assertTrue(result.duplicate());
        assertEquals(PaymentStatus.SUCCESS, result.paymentStatus());
        verify(events, never()).saveAndFlush(any());
        verifyNoInteractions(bookings);
    }

    @Test
    void reusedEventIdWithDifferentTransactionReferenceIsRejected() {
        PaymentWebhookEvent event = event("evt-1", "SIM-1", PaymentStatus.SUCCESS, "100.00");
        when(events.findByEventId("evt-1")).thenReturn(Optional.of(event));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.process(payload("evt-1", "SIM-2", "SUCCESS", "100.00")));

        assertEquals(409, ex.getStatus().value());
        verifyNoInteractions(payments, bookings);
    }

    @Test
    void reusedEventIdWithDifferentAmountIsRejected() {
        PaymentWebhookEvent event = event("evt-1", "SIM-1", PaymentStatus.SUCCESS, "100.00");
        when(events.findByEventId("evt-1")).thenReturn(Optional.of(event));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.process(payload("evt-1", "SIM-1", "SUCCESS", "101.00")));

        assertEquals(409, ex.getStatus().value());
        verifyNoInteractions(payments, bookings);
    }

    @Test
    void amountMismatchIsRejectedAndEventIsNotRecorded() {
        Payment payment = payment(PaymentStatus.SUCCESS, BookingStatus.CONFIRMED);
        when(events.findByEventId("evt-1")).thenReturn(Optional.empty());
        when(payments.findByTransactionReferenceForUpdate("SIM-1")).thenReturn(Optional.of(payment));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.process(payload("evt-1", "SIM-1", "SUCCESS", "101.00")));

        assertEquals(422, ex.getStatus().value());
        verify(events, never()).saveAndFlush(any());
    }

    @Test
    void conflictingStatusCannotCorruptConfirmedBooking() {
        Payment payment = payment(PaymentStatus.SUCCESS, BookingStatus.CONFIRMED);
        when(events.findByEventId("evt-1")).thenReturn(Optional.empty());
        when(payments.findByTransactionReferenceForUpdate("SIM-1")).thenReturn(Optional.of(payment));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.process(payload("evt-1", "SIM-1", "FAILED", "100.00")));

        assertEquals(409, ex.getStatus().value());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(BookingStatus.CONFIRMED, payment.getBooking().getStatus());
        verify(events, never()).saveAndFlush(any());
        verifyNoInteractions(bookings);
    }

    @Test
    void unknownPaymentIsRejected() {
        when(events.findByEventId("evt-1")).thenReturn(Optional.empty());
        when(payments.findByTransactionReferenceForUpdate("SIM-unknown")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.process(payload("evt-1", "SIM-unknown", "SUCCESS", "100.00")));

        assertEquals(404, ex.getStatus().value());
        verify(events, never()).saveAndFlush(any());
    }

    private static String payload(String eventId, String reference, String status, String amount) {
        return "{\"eventId\":\"" + eventId + "\",\"transactionReference\":\"" + reference
                + "\",\"status\":\"" + status + "\",\"amount\":" + amount + "}";
    }

    private static Payment payment(PaymentStatus status, BookingStatus bookingStatus) {
        Booking booking = new Booking();
        booking.setId(7L);
        booking.setAmount(new BigDecimal("100.00"));
        booking.setStatus(bookingStatus);
        Payment payment = new Payment();
        payment.setId(11L);
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setStatus(status);
        payment.setTransactionReference("SIM-1");
        return payment;
    }

    private static PaymentWebhookEvent event(String id, String reference, PaymentStatus status, String amount) {
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setId(99L);
        event.setEventId(id);
        event.setTransactionReference(reference);
        event.setStatus(status);
        event.setAmount(new BigDecimal(amount));
        return event;
    }
}
