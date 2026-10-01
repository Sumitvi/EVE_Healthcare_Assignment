package com.evehealthcare.diagnostics.booking;

import static com.evehealthcare.diagnostics.booking.BookingDtos.*;

import com.evehealthcare.diagnostics.common.PageResponse;
import com.evehealthcare.diagnostics.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/bookings")
@Tag(name = "Bookings", description = "Book diagnostic tests. The patient is always the authenticated user.")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a booking",
            description = "Creates a PENDING booking for the authenticated user. The amount is the centre's "
                    + "current price for the test; user, status and amount in the body are ignored.")
    @ApiResponse(responseCode = "201", description = "Booking created")
    @ApiResponse(responseCode = "400", description = "Invalid body or appointment time (past / too far ahead)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "404", description = "Centre or test not found")
    @ApiResponse(responseCode = "422", description = "The centre does not offer this test")
    public BookingResponse create(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
                                  @Valid @RequestBody CreateBookingRequest request) {
        return service.create(user, request);
    }

    @GetMapping({"", "/"})
    @Operation(summary = "List my bookings", description = "Only the authenticated user's bookings, newest first.")
    public PageResponse<BookingResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.listMine(user, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a booking", description = "Allowed for the booking owner (or an ADMIN).")
    @ApiResponse(responseCode = "200", description = "Booking found")
    @ApiResponse(responseCode = "403", description = "Booking belongs to another user")
    @ApiResponse(responseCode = "404", description = "Booking not found")
    public BookingResponse get(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
                               @PathVariable Long id) {
        return service.get(id, user);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel a booking",
            description = "Owner only. PENDING bookings can be cancelled; CONFIRMED ones only before the "
                    + "appointment starts. CANCELLED and FAILED bookings cannot be cancelled.")
    @ApiResponse(responseCode = "200", description = "Booking cancelled")
    @ApiResponse(responseCode = "403", description = "Booking belongs to another user")
    @ApiResponse(responseCode = "404", description = "Booking not found")
    @ApiResponse(responseCode = "409", description = "Booking is not in a cancellable state")
    public BookingResponse cancel(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
                                  @PathVariable Long id) {
        return service.cancel(id, user);
    }
}
