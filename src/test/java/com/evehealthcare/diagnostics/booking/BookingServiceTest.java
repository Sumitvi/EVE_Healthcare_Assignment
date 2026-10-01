package com.evehealthcare.diagnostics.booking;

import static com.evehealthcare.diagnostics.booking.BookingDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.evehealthcare.diagnostics.catalog.CatalogService;
import com.evehealthcare.diagnostics.catalog.CentreTest;
import com.evehealthcare.diagnostics.catalog.DiagnosticCentre;
import com.evehealthcare.diagnostics.catalog.DiagnosticTest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime FUTURE = LOCAL_NOW.plusDays(5);

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
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new BookingService(bookings, users, catalog, clock);
    }

    // ---------- create ----------

    @Test
    void create_success_isPendingWithServerDerivedUserAndAmount() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(catalog.requireOffering(10L, 20L)).thenReturn(offering(10L, 20L, "1499.50"));
        when(bookings.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(100L);
            return b;
        });

        BookingResponse res = service.create(OWNER, new CreateBookingRequest(10L, 20L, FUTURE));

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(saved.capture());
        Booking b = saved.getValue();
        assertThat(b.getUser().getId()).isEqualTo(1L);                       // from the JWT principal
        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING);          // always starts PENDING
        assertThat(b.getAmount()).isEqualByComparingTo("1499.50");           // from the catalogue price
        assertThat(b.getAppointmentDateTime()).isEqualTo(FUTURE);

        assertThat(res.id()).isEqualTo(100L);
        assertThat(res.userId()).isEqualTo(1L);
        assertThat(res.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(res.amount()).isEqualByComparingTo("1499.50");
        assertThat(res.centre().id()).isEqualTo(10L);
        assertThat(res.test().id()).isEqualTo(20L);
    }

    @Test
    void create_invalidTest_isNotFound_andNothingSaved() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(catalog.requireOffering(10L, 404L)).thenThrow(ApiException.notFound("Test 404 not found"));

        assertApiError(() -> service.create(OWNER, new CreateBookingRequest(10L, 404L, FUTURE)),
                HttpStatus.NOT_FOUND);
        verifyNoInteractions(bookings);
    }

    @Test
    void create_invalidCentre_isNotFound_andNothingSaved() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(catalog.requireOffering(404L, 20L)).thenThrow(ApiException.notFound("Centre 404 not found"));

        assertApiError(() -> service.create(OWNER, new CreateBookingRequest(404L, 20L, FUTURE)),
                HttpStatus.NOT_FOUND);
        verifyNoInteractions(bookings);
    }

    @Test
    void create_testNotOfferedAtCentre_isUnprocessable_andNothingSaved() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(catalog.requireOffering(10L, 20L))
                .thenThrow(ApiException.unprocessable("This centre does not offer the requested test"));

        assertApiError(() -> service.create(OWNER, new CreateBookingRequest(10L, 20L, FUTURE)),
                HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(bookings);
    }

    @Test
    void create_appointmentInThePast_isBadRequest() {
        assertApiError(() -> service.create(OWNER, new CreateBookingRequest(10L, 20L, LOCAL_NOW.minusHours(1))),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bookings, catalog);
    }

    @Test
    void create_appointmentExactlyNow_isBadRequest() {
        assertApiError(() -> service.create(OWNER, new CreateBookingRequest(10L, 20L, LOCAL_NOW)),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bookings, catalog);
    }

    @Test
    void create_appointmentTooFarAhead_isBadRequest() {
        LocalDateTime tooFar = LOCAL_NOW.plusDays(BookingService.MAX_ADVANCE_DAYS).plusMinutes(1);
        assertApiError(() -> service.create(OWNER, new CreateBookingRequest(10L, 20L, tooFar)),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bookings, catalog);
    }

    @Test
    void create_userNoLongerExists_isUnauthorized() {
        when(users.findById(1L)).thenReturn(Optional.empty());

        assertApiError(() -> service.create(OWNER, new CreateBookingRequest(10L, 20L, FUTURE)),
                HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(bookings);
    }

    // ---------- read / authorization ----------

    @Test
    void get_owner_canReadOwnBooking() {
        when(bookings.findWithDetailsById(5L)).thenReturn(Optional.of(booking(5L, 1L, BookingStatus.PENDING, FUTURE)));

        assertThat(service.get(5L, OWNER).id()).isEqualTo(5L);
    }

    @Test
    void get_otherUser_isForbidden() {
        when(bookings.findWithDetailsById(5L)).thenReturn(Optional.of(booking(5L, 1L, BookingStatus.PENDING, FUTURE)));

        assertApiError(() -> service.get(5L, OTHER), HttpStatus.FORBIDDEN);
    }

    @Test
    void get_admin_canReadAnyBooking() {
        when(bookings.findWithDetailsById(5L)).thenReturn(Optional.of(booking(5L, 1L, BookingStatus.PENDING, FUTURE)));

        assertThat(service.get(5L, ADMIN).userId()).isEqualTo(1L);
    }

    @Test
    void get_unknownBooking_isNotFound() {
        when(bookings.findWithDetailsById(404L)).thenReturn(Optional.empty());

        assertApiError(() -> service.get(404L, OWNER), HttpStatus.NOT_FOUND);
    }

    // ---------- cancel ----------

    @Test
    void cancel_pendingBooking_succeeds() {
        Booking b = booking(5L, 1L, BookingStatus.PENDING, FUTURE);
        when(bookings.findByIdForUpdate(5L)).thenReturn(Optional.of(b));
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse res = service.cancel(5L, OWNER);

        assertThat(res.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookings).saveAndFlush(b);
    }

    @Test
    void cancel_confirmedBeforeAppointment_succeeds() {
        Booking b = booking(5L, 1L, BookingStatus.CONFIRMED, FUTURE);
        when(bookings.findByIdForUpdate(5L)).thenReturn(Optional.of(b));
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.cancel(5L, OWNER).status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancel_confirmedAfterAppointmentStarted_isConflict() {
        Booking b = booking(5L, 1L, BookingStatus.CONFIRMED, LOCAL_NOW.minusMinutes(1));
        when(bookings.findByIdForUpdate(5L)).thenReturn(Optional.of(b));

        assertApiError(() -> service.cancel(5L, OWNER), HttpStatus.CONFLICT);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void cancel_alreadyCancelled_isConflict() {
        Booking b = booking(5L, 1L, BookingStatus.CANCELLED, FUTURE);
        when(bookings.findByIdForUpdate(5L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.cancel(5L, OWNER))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).containsIgnoringCase("already cancelled");
                });
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void cancel_failedBooking_isConflict() {
        Booking b = booking(5L, 1L, BookingStatus.FAILED, FUTURE);
        when(bookings.findByIdForUpdate(5L)).thenReturn(Optional.of(b));

        assertApiError(() -> service.cancel(5L, OWNER), HttpStatus.CONFLICT);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.FAILED);
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void cancel_byAnotherUser_isForbidden_andBookingUntouched() {
        Booking b = booking(5L, 1L, BookingStatus.PENDING, FUTURE);
        when(bookings.findByIdForUpdate(5L)).thenReturn(Optional.of(b));

        assertApiError(() -> service.cancel(5L, OTHER), HttpStatus.FORBIDDEN);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void cancel_byAdmin_isForbidden() {
        when(bookings.findByIdForUpdate(5L))
                .thenReturn(Optional.of(booking(5L, 1L, BookingStatus.PENDING, FUTURE)));

        assertApiError(() -> service.cancel(5L, ADMIN), HttpStatus.FORBIDDEN);
    }

    @Test
    void cancel_unknownBooking_isNotFound() {
        when(bookings.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertApiError(() -> service.cancel(404L, OWNER), HttpStatus.NOT_FOUND);
    }

    // ---------- state machine ----------

    @Test
    void statusTransitions_matchDocumentedRules() {
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.CONFIRMED)).isTrue();
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.FAILED)).isTrue();
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.PENDING)).isFalse();
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.FAILED)).isFalse();
        for (BookingStatus target : BookingStatus.values()) {
            assertThat(BookingStatus.FAILED.canTransitionTo(target)).isFalse();
            assertThat(BookingStatus.CANCELLED.canTransitionTo(target)).isFalse();
        }
    }

    // ---------- helpers ----------

    private static void assertApiError(ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(expected));
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setEmail("user" + id + "@eve.test");
        u.setRole(Role.USER);
        return u;
    }

    private static CentreTest offering(Long centreId, Long testId, String price) {
        DiagnosticCentre centre = new DiagnosticCentre();
        centre.setId(centreId);
        centre.setName("Centre " + centreId);
        centre.setLocation("Bengaluru");
        DiagnosticTest test = new DiagnosticTest();
        test.setId(testId);
        test.setName("Test " + testId);
        CentreTest ct = new CentreTest();
        ct.setCentre(centre);
        ct.setTest(test);
        ct.setPrice(new BigDecimal(price));
        return ct;
    }

    private static Booking booking(Long id, Long ownerId, BookingStatus status, LocalDateTime appointment) {
        CentreTest ct = offering(10L, 20L, "999.00");
        Booking b = new Booking();
        b.setId(id);
        b.setUser(user(ownerId));
        b.setCentre(ct.getCentre());
        b.setTest(ct.getTest());
        b.setAppointmentDateTime(appointment);
        b.setAmount(ct.getPrice());
        b.setStatus(status);
        return b;
    }
}
