package com.payment.user.repository;

import com.payment.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;


@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}