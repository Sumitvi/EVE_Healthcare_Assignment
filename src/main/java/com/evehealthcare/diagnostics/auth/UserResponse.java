package com.evehealthcare.diagnostics.auth;

import com.evehealthcare.diagnostics.user.Role;
import com.evehealthcare.diagnostics.user.User;

public record UserResponse(Long id, String name, String email, Role role) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }
}
