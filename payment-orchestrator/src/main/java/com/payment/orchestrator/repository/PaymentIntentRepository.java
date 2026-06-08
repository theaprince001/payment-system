package com.payment.orchestrator.repository;

import com.payment.common.model.PaymentStatus;
import com.payment.orchestrator.entity.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}