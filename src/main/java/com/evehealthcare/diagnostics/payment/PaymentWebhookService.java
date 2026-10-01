package com.evehealthcare.diagnostics.payment;

import static com.evehealthcare.diagnostics.payment.PaymentWebhookDtos.*;

import com.evehealthcare.diagnostics.booking.Booking;
import com.evehealthcare.diagnostics.booking.BookingService;
import com.evehealthcare.diagnostics.booking.BookingStatus;
import com.evehealthcare.diagnostics.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentWebhookService {

    private final PaymentWebhookEventRepository events;
    private final PaymentRepository payments;
    private final ObjectMapper objectMapper;
    private final BookingService bookings;

    public PaymentWebhookService(PaymentWebhookEventRepository events, PaymentRepository payments,
                                 ObjectMapper objectMapper, BookingService bookings) {
        this.events = events;
        this.payments = payments;
        this.objectMapper = objectMapper;
        this.bookings = bookings;
    }

    @Transactional
    public PaymentWebhookResponse process(String rawBody) {
        PaymentWebhookRequest request = parse(rawBody);
        validateRequest(request);

        PaymentWebhookEvent existing = events.findByEventId(request.eventId()).orElse(null);
        if (existing != null) {
            // A repeated delivery is a successful no-op. It is safe to return the durable event record.
            if (!sameEventPayload(existing, request)) {
                throw ApiException.conflict("eventId was already processed with different event data");
            }
            Payment payment = payments.findByTransactionReferenceForUpdate(existing.getTransactionReference())
                    .orElseThrow(() -> ApiException.notFound("Payment not found for transaction reference"));
            return response(existing, payment, true);
        }

        Payment payment = payments.findByTransactionReferenceForUpdate(request.transactionReference())
                .orElseThrow(() -> ApiException.notFound("Payment not found for transaction reference"));

        // Re-check after taking the payment row lock. If two deliveries with the same eventId race, the
        // second transaction waits for the first, then observes its committed idempotency record and becomes
        // the same safe no-op as any later retry.
        existing = events.findByEventId(request.eventId()).orElse(null);
        if (existing != null) {
            if (!sameEventPayload(existing, request)) {
                throw ApiException.conflict("eventId was already processed with different event data");
            }
            return response(existing, payment, true);
        }

        if (payment.getAmount().compareTo(request.amount()) != 0) {
            throw ApiException.unprocessable("Webhook amount does not match the payment amount");
        }

        applyProviderStatus(payment, request.status());

        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setEventId(request.eventId());
        event.setTransactionReference(request.transactionReference());
        event.setStatus(request.status());
        event.setAmount(request.amount());
        event.setProcessedAt(Instant.now());
        try {
            events.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            // Another transaction won the event-id race. Because this transaction is rolled back by Spring,
            // the caller can retry safely and will then observe the durable event as a duplicate.
            throw ApiException.conflict("Webhook event is being processed concurrently; retry the same event");
        }

        return response(event, payment, false);
    }

    private PaymentWebhookRequest parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, PaymentWebhookRequest.class);
        } catch (JsonProcessingException ex) {
            throw ApiException.badRequest("Malformed webhook payload");
        }
    }

    private void validateRequest(PaymentWebhookRequest request) {
        if (request.eventId() == null || request.eventId().isBlank()) {
            throw ApiException.badRequest("eventId is required");
        }
        if (request.eventId().length() > 128) {
            throw ApiException.badRequest("eventId must be at most 128 characters");
        }
        if (request.transactionReference() == null || request.transactionReference().isBlank()) {
            throw ApiException.badRequest("transactionReference is required");
        }
        if (request.transactionReference().length() > 64) {
            throw ApiException.badRequest("transactionReference must be at most 64 characters");
        }
        if (request.status() == null) {
            throw ApiException.badRequest("status is required");
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw ApiException.badRequest("amount must be positive");
        }
        if (request.amount().scale() > 2 || request.amount().precision() > 10) {
            throw ApiException.badRequest("amount must have at most 8 integer digits and 2 decimals");
        }
    }

    private void applyProviderStatus(Payment payment, PaymentStatus providerStatus) {
        if (payment.getStatus() == providerStatus) {
            return; // safe reconciliation: provider repeated the same final state.
        }

        Booking booking = payment.getBooking();
        BookingStatus target = providerStatus == PaymentStatus.SUCCESS
                ? BookingStatus.CONFIRMED : BookingStatus.FAILED;

        // Payment records are final in this implementation. A contradictory provider event must not silently
        // mutate a confirmed/failed booking into an invalid lifecycle state.
        if (!booking.getStatus().canTransitionTo(target)) {
            throw ApiException.conflict("Webhook status " + providerStatus
                    + " conflicts with the current payment/booking state");
        }

        payment.setStatus(providerStatus);
        payment.setFailureReason(providerStatus == PaymentStatus.FAILED ? "Payment provider reported failure" : null);
        bookings.transition(booking, target);
    }

    private boolean sameEventPayload(PaymentWebhookEvent existing, PaymentWebhookRequest request) {
        return existing.getTransactionReference().equals(request.transactionReference())
                && existing.getStatus() == request.status()
                && existing.getAmount().compareTo(request.amount()) == 0;
    }

    private PaymentWebhookResponse response(PaymentWebhookEvent event, Payment payment, boolean duplicate) {
        return new PaymentWebhookResponse(event.getEventId(), duplicate, payment.getId(), payment.getBooking().getId(),
                payment.getStatus(), payment.getTransactionReference(), event.getProcessedAt());
    }
}
