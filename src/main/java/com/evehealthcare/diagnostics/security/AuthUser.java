package com.evehealthcare.diagnostics.security;

import com.evehealthcare.diagnostics.user.Role;

public record AuthUser(Long id, String email, Role role) {
}
