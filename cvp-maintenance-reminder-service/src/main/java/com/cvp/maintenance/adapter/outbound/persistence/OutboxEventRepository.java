package com.cvp.maintenance.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;

import java.time.*;
import java.util.*;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    @Query("select e from OutboxEventEntity e where e.publicationStatus in (com.cvp.maintenance.domain.OutboxStatus.PENDING, com.cvp.maintenance.domain.OutboxStatus.FAILED) and e.nextAttemptAt<=:now order by e.createdAt")
    List<OutboxEventEntity> findEligible(Instant now, Pageable pageable);
}
