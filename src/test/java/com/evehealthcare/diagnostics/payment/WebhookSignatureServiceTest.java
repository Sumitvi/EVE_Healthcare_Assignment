package com.evehealthcare.diagnostics.payment;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WebhookSignatureServiceTest {

    private final WebhookSignatureService service = new WebhookSignatureService("test-webhook-secret-12345");

    @Test
    void acceptsValidSignatureWithSha256Prefix() throws Exception {
        String body = "{\"eventId\":\"evt-1\"}";
        String signature = hmac(body, "test-webhook-secret-12345");
        assertTrue(service.isValid(body, "sha256=" + signature));
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        String signature = hmac("{\"eventId\":\"evt-1\"}", "test-webhook-secret-12345");
        assertFalse(service.isValid("{\"eventId\":\"evt-2\"}", "sha256=" + signature));
    }

    @Test
    void rejectsMissingOrMalformedSignature() {
        assertFalse(service.isValid("body", null));
        assertFalse(service.isValid("body", "sha256=bad"));
    }

    private static String hmac(String body, String secret) throws Exception {
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
