package com.payment.user.controller;

import com.payment.common.dto.UserProfileDto;
import com.payment.user.entity.UserProfile;
import com.payment.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileRepository repository;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUserProfile(@RequestParam("userId") UUID userId) {
        UserProfile profile = repository.findById(userId).orElse(null);
        if (profile == null) {
            profile = UserProfile.builder()
                    .userId(userId)
                    .kycVerified(false)
                    .build();
            repository.save(profile);
        }
        return ResponseEntity.ok(profile);
    }

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
    public ResponseEntity<UserProfile> createProfile(@RequestBody UserProfileDto dto) {
        UserProfile profile = UserProfile.builder()
                .userId(dto.getUserId())
                .fullName(dto.getFullName())
                .email(dto.getEmail())
                .phoneNumber(dto.getPhoneNumber())
                .kycVerified(dto.isKycVerified())
                .build();
        return ResponseEntity.ok(repository.save(profile));
    }

    @GetMapping("/by-phone/{phoneNumber}")
    public ResponseEntity<?> getUserByPhone(@PathVariable String phoneNumber){
        return repository.findByPhoneNumber(phoneNumber)
                .map(user -> ResponseEntity.ok(Map.of(
                        "userId", user.getUserId(),
                        "fullName", user.getFullName(),
                        "phoneNumber",user.getPhoneNumber()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}