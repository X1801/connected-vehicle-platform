package com.cvp.maintenance.adapter.outbound.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity,IdempotencyRecordId> { }
