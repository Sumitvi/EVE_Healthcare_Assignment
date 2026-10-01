package com.evehealthcare.diagnostics.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebhookSignatureService {

    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    public WebhookSignatureService(@Value("${app.webhook.secret}") String secret) {
        if (secret == null || secret.isBlank() || secret.length() < 16) {
            throw new IllegalStateException("app.webhook.secret must contain at least 16 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isValid(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String supplied = signatureHeader.startsWith("sha256=")
                ? signatureHeader.substring("sha256=".length()) : signatureHeader;
        if (!supplied.matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    supplied.toLowerCase().getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify webhook signature", ex);
        }
    }
}
