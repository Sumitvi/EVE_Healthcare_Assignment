package com.evehealthcare.diagnostics.payment;

import static com.evehealthcare.diagnostics.payment.PaymentWebhookDtos.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/payments")
@Tag(name = "Payment Webhooks", description = "Idempotent simulated payment-provider callbacks")
public class PaymentWebhookController {

    private final PaymentWebhookService service;
    private final WebhookSignatureService signatures;

    public PaymentWebhookController(PaymentWebhookService service, WebhookSignatureService signatures) {
        this.service = service;
        this.signatures = signatures;
    }

    @PostMapping(value = {"/webhook", "/webhook/"}, consumes = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Receive a payment status webhook",
            description = "Requires X-Webhook-Signature: sha256=<HMAC-SHA256 of the exact request body>. "
                    + "Events are idempotent by eventId; repeated delivery is a safe no-op.")
    @ApiResponse(responseCode = "200", description = "Webhook processed or safely recognized as duplicate")
    @ApiResponse(responseCode = "400", description = "Invalid signature or malformed payload")
    @ApiResponse(responseCode = "404", description = "Payment not found")
    @ApiResponse(responseCode = "409", description = "Conflicting state or concurrent duplicate event")
    @ApiResponse(responseCode = "422", description = "Webhook amount does not match payment")
    public PaymentWebhookResponse webhook(@RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
                                          @RequestBody String rawBody) {
        if (!signatures.isValid(rawBody, signature)) {
            throw com.evehealthcare.diagnostics.exception.ApiException.unauthorized("Invalid webhook signature");
        }
        return service.process(rawBody);
    }
}
