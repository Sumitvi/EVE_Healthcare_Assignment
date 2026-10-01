package com.evehealthcare.diagnostics.auth;

import com.evehealthcare.diagnostics.exception.ApiException;
import com.evehealthcare.diagnostics.security.JwtService;
import com.evehealthcare.diagnostics.user.Role;
import com.evehealthcare.diagnostics.user.User;
import com.evehealthcare.diagnostics.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public UserResponse signup(SignupRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole(Role.USER); // role is never taken from the request
        try {
            return UserResponse.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException race) {
            throw ApiException.conflict("An account with this email already exists");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email().trim().toLowerCase())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        return new AuthResponse(jwt.generateToken(user), "Bearer", jwt.getExpiresInSeconds());
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
    }
}
