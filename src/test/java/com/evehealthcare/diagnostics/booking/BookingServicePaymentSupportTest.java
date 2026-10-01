package com.evehealthcare.diagnostics.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evehealthcare.diagnostics.catalog.CatalogService;
import com.evehealthcare.diagnostics.exception.ApiException;
import com.evehealthcare.diagnostics.security.AuthUser;
import com.evehealthcare.diagnostics.user.Role;
import com.evehealthcare.diagnostics.user.User;
import com.evehealthcare.diagnostics.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Covers the two BookingService methods the payment flow relies on. */
@ExtendWith(MockitoExtension.class)
class BookingServicePaymentSupportTest {

    private static final AuthUser OWNER = new AuthUser(1L, "owner@eve.test", Role.USER);
    private static final AuthUser OTHER = new AuthUser(2L, "other@eve.test", Role.USER);
    private static final AuthUser ADMIN = new AuthUser(99L, "admin@eve.test", Role.ADMIN);

    @Mock
    BookingRepository bookings;
    @Mock
    UserRepository users;
    @Mock
    CatalogService catalog;

    BookingService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);
        service = new BookingService(bookings, users, catalog, clock);
    }

    @Test
    void getOwnedForUpdate_owner_getsLockedBooking() {
        Booking b = booking(BookingStatus.PENDING);
        when(bookings.findByIdForUpdate(5L)).thenReturn(Optional.of(b));

        assertThat(service.getOwnedForUpdate(5L, OWNER)).isSameAs(b);
    }

    @Test
    void getOwnedForUpdate_unknownBooking_isNotFound() {
        when(bookings.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertApiError(() -> service.getOwnedForUpdate(404L, OWNER), HttpStatus.NOT_FOUND);
    }

    @Test
    void getOwnedForUpdate_nonOwner_andAdmin_areForbidden() {
        when(bookings.findByIdForUpdate(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING)));

        assertApiError(() -> service.getOwnedForUpdate(5L, OTHER), HttpStatus.FORBIDDEN);
        assertApiError(() -> service.getOwnedForUpdate(5L, ADMIN), HttpStatus.FORBIDDEN);
    }

    @Test
    void transition_pendingToConfirmed_andToFailed_areSaved() {
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking confirmed = booking(BookingStatus.PENDING);
        assertThat(service.transition(confirmed, BookingStatus.CONFIRMED).getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);

        Booking failed = booking(BookingStatus.PENDING);
        assertThat(service.transition(failed, BookingStatus.FAILED).getStatus()).isEqualTo(BookingStatus.FAILED);
    }

    @Test
    void transition_notAllowedByStateMachine_isConflict_andNotSaved() {
        Booking b = booking(BookingStatus.CONFIRMED);

        assertApiError(() -> service.transition(b, BookingStatus.FAILED), HttpStatus.CONFLICT);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookings, never()).saveAndFlush(any());
    }

    private static void assertApiError(ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(expected));
    }

    private static Booking booking(BookingStatus status) {
        User u = new User();
        u.setId(1L);
        u.setEmail("owner@eve.test");
        u.setRole(Role.USER);
        Booking b = new Booking();
        b.setId(5L);
        b.setUser(u);
        b.setAppointmentDateTime(LocalDateTime.of(2026, 10, 6, 10, 0));
        b.setAmount(new BigDecimal("999.00"));
        b.setStatus(status);
        return b;
    }
}
