package com.evehealthcare.diagnostics.auth;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
