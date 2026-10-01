package com.evehealthcare.diagnostics.payment;

import static com.evehealthcare.diagnostics.payment.PaymentDtos.*;

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
@RequestMapping("/payments")
@Tag(name = "Payments", description = "Simulated payments for bookings. No real payment gateway is involved.")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pay for a booking (simulated)",
            description = "Charges the booking's server-side amount through a simulated gateway. A PENDING booking "
                    + "becomes CONFIRMED on SUCCESS or FAILED on FAILED. Both outcomes return 201 with the payment "
                    + "record; inspect `status`. A booking can be paid only once.")
    @ApiResponse(responseCode = "201", description = "Payment processed (status SUCCESS or FAILED)")
    @ApiResponse(responseCode = "400", description = "Invalid body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Booking belongs to another user")
    @ApiResponse(responseCode = "404", description = "Booking not found")
    @ApiResponse(responseCode = "409", description = "Booking not payable (already paid/failed/cancelled, or past)")
    @ApiResponse(responseCode = "422", description = "Supplied amount does not match the booking amount")
    public PaymentResponse pay(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
                               @Valid @RequestBody CreatePaymentRequest request) {
        return service.pay(user, request);
    }
}
