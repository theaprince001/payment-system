package com.payment.user.controller;

import com.payment.user.entity.UserProfile;
import com.payment.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserProfileRepository repository;

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfile> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(repository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found")));
    }

    @PostMapping("/kyc/verify")
    public ResponseEntity<Void> verifyKyc(@RequestParam UUID userId) {
        UserProfile profile = repository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        profile.setKycVerified(true);
        profile.setKycVerifiedAt(Instant.now());
        repository.save(profile);
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public ResponseEntity<UserProfile> createProfile(@RequestBody UserProfile profile) {
        return ResponseEntity.ok(repository.save(profile));
    }
}