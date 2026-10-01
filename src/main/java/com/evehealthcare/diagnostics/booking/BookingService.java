package com.evehealthcare.diagnostics.booking;

import static com.evehealthcare.diagnostics.booking.BookingDtos.*;

import com.evehealthcare.diagnostics.catalog.CatalogService;
import com.evehealthcare.diagnostics.catalog.CentreTest;
import com.evehealthcare.diagnostics.common.PageResponse;
import com.evehealthcare.diagnostics.exception.ApiException;
import com.evehealthcare.diagnostics.security.AuthUser;
import com.evehealthcare.diagnostics.user.Role;
import com.evehealthcare.diagnostics.user.User;
import com.evehealthcare.diagnostics.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    static final int MAX_ADVANCE_DAYS = 90;

    private final BookingRepository bookings;
    private final UserRepository users;
    private final CatalogService catalog;
    private final Clock clock;

    public BookingService(BookingRepository bookings, UserRepository users, CatalogService catalog, Clock clock) {
        this.bookings = bookings;
        this.users = users;
        this.catalog = catalog;
        this.clock = clock;
    }



    @Transactional
    public BookingResponse create(AuthUser actor, CreateBookingRequest req) {
        validateAppointment(req.appointmentDateTime());
        User user = users.findById(actor.id())
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
        // 404 for unknown centre/test, 422 if the centre does not offer the test
        CentreTest offering = catalog.requireOffering(req.centreId(), req.testId());

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setCentre(offering.getCentre());
        booking.setTest(offering.getTest());
        booking.setAppointmentDateTime(req.appointmentDateTime());
        booking.setAmount(offering.getPrice());
        booking.setStatus(BookingStatus.PENDING);
        Booking saved = bookings.save(booking);
        log.info("Booking {} created for user {} (centre {}, test {}, amount {})", saved.getId(), user.getId(),
                req.centreId(), req.testId(), saved.getAmount());
        return BookingResponse.from(saved);
    }


    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listMine(AuthUser actor, int page, int size) {
        var pageable = PageResponse.request(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(bookings.findByUserId(actor.id(), pageable), BookingResponse::from);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long id, AuthUser actor) {
        Booking booking = bookings.findWithDetailsById(id)
                .orElseThrow(() -> ApiException.notFound("Booking " + id + " not found"));
        if (!isOwner(booking, actor) && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("You do not have access to this booking");
        }
        return BookingResponse.from(booking);
    }



    @Transactional
    public BookingResponse cancel(Long id, AuthUser actor) {
        Booking booking = bookings.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Booking " + id + " not found"));
        // ownership first, so non-owners learn nothing about the booking's state
        if (!isOwner(booking, actor)) {
            throw ApiException.forbidden("You do not have access to this booking");
        }
        BookingStatus current = booking.getStatus();
        if (current == BookingStatus.CANCELLED) {
            throw ApiException.conflict("Booking is already cancelled");
        }
        if (!current.canTransitionTo(BookingStatus.CANCELLED)) {
            throw ApiException.conflict("A booking in " + current + " status cannot be cancelled");
        }
        if (current == BookingStatus.CONFIRMED
                && !booking.getAppointmentDateTime().isAfter(LocalDateTime.now(clock))) {
            throw ApiException.conflict("A confirmed booking cannot be cancelled once its appointment has started");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        // flush so @PreUpdate refreshes updatedAt before the response is built
        Booking saved = bookings.saveAndFlush(booking);
        log.info("Booking {} cancelled by user {} (was {})", id, actor.id(), current);
        return BookingResponse.from(saved);
    }



    @Transactional(propagation = Propagation.MANDATORY)
    public Booking getOwnedForUpdate(Long id, AuthUser actor) {
        Booking booking = bookings.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Booking " + id + " not found"));
        if (!isOwner(booking, actor)) {
            throw ApiException.forbidden("You do not have access to this booking");
        }
        return booking;
    }

    /** Moves a booking to {@code target} if the state machine allows it (409 otherwise). Needs a transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Booking transition(Booking booking, BookingStatus target) {
        BookingStatus current = booking.getStatus();
        if (!current.canTransitionTo(target)) {
            throw ApiException.conflict("Booking cannot move from " + current + " to " + target);
        }
        booking.setStatus(target);
        // flush so @PreUpdate refreshes updatedAt before the response is built
        Booking saved = bookings.saveAndFlush(booking);
        log.info("Booking {} moved {} -> {}", saved.getId(), current, target);
        return saved;
    }

    private void validateAppointment(LocalDateTime appointment) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!appointment.isAfter(now)) {
            throw ApiException.badRequest("appointmentDateTime must be in the future");
        }
        if (appointment.isAfter(now.plusDays(MAX_ADVANCE_DAYS))) {
            throw ApiException.badRequest(
                    "appointmentDateTime cannot be more than " + MAX_ADVANCE_DAYS + " days ahead");
        }
    }

    private boolean isOwner(Booking booking, AuthUser actor) {
        return booking.getUser().getId().equals(actor.id());
    }
}
