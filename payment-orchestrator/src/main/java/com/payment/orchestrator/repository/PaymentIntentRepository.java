package com.payment.orchestrator.repository;

import com.payment.common.model.PaymentStatus;
import com.payment.orchestrator.entity.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    Optional<PaymentIntent> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT p FROM PaymentIntent p WHERE p.status = :status AND p.createdAt < :cutoff")
    List<PaymentIntent> findByStatusAndCreatedAtBefore(@Param("status") PaymentStatus status,
                                                       @Param("cutoff") Instant cutoff);

    @Modifying
    @Transactional
    @Query("UPDATE PaymentIntent p SET p.status = :newStatus, p.failureReason = :reason, " +
            "p.reviewedBy = :reviewedBy, p.reviewedAt = :reviewedAt " +
            "WHERE p.id = :id AND p.status = :expectedStatus")
    int updateStatusConditionally(@Param("id") UUID id,
                                  @Param("expectedStatus") PaymentStatus expectedStatus,
                                  @Param("newStatus") PaymentStatus newStatus,
                                  @Param("reason") String reason,
                                  @Param("reviewedBy") String reviewedBy,
                                  @Param("reviewedAt") Instant reviewedAt);
}