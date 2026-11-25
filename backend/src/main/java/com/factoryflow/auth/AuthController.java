package com.factoryflow.auth;

import com.factoryflow.security.JwtTokenProvider;
import com.factoryflow.users.User;
import com.factoryflow.users.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!user.isActive()) {
            return ResponseEntity.status(401).body(null);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).build();
        }

        String token = jwtTokenProvider.generateToken(user);
        var roles = user.getRoles().stream().map(r -> r.getName()).toList();

        return ResponseEntity.ok(new LoginResponse(
                token,
                user.getId().toString(),
                user.getUsername(),
                user.getFullName(),
                roles
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(
            @org.springframework.security.core.annotation.AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        var roles = user.getRoles().stream().map(r -> r.getName()).toList();
        return ResponseEntity.ok(new UserProfileResponse(
                user.getId().toString(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                roles
        ));
    }

    public record LoginRequest(
            @jakarta.validation.constraints.NotBlank String username,
            @jakarta.validation.constraints.NotBlank String password) {}

    public record LoginResponse(
            String token, String userId, String username,
            String fullName, java.util.List<String> roles) {}

    public record UserProfileResponse(
            String id, String username, String fullName,
            String email, String phone, java.util.List<String> roles) {}
}
