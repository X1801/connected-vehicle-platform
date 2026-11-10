package com.cvp.maintenance.application;

import com.cvp.maintenance.adapter.outbound.persistence.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandService {
    private final IdempotencyRecordRepository records;

    public CommandService(IdempotencyRecordRepository records) {
        this.records = records;
    }

    @Transactional
    public UUID execute(String caller, String key, String request, Supplier<UUID> action) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key is required");
        IdempotencyRecordId id = new IdempotencyRecordId(caller, key);
        String hash = hash(request);
        IdempotencyRecordEntity prior = records.findById(id).orElse(null);
        if (prior != null) {
            if (!prior.requestHash.equals(hash))
                throw new ConflictException("Idempotency key was reused with a different request");
            if (prior.responseStatus == 102) throw new ConflictException("Request is processing");
            return prior.resultResourceId;
        }
        IdempotencyRecordEntity record = new IdempotencyRecordEntity();
        record.id = id;
        record.requestHash = hash;
        record.responseStatus = 102;
        record.createdAt = Instant.now();
        record.expiresAt = record.createdAt.plus(Duration.ofDays(1));
        records.saveAndFlush(record);
        UUID result = action.get();
        record.responseStatus = 200;
        record.resultResourceType = "REMINDER";
        record.resultResourceId = result;
        records.save(record);
        return result;
    }

    private String hash(String v) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
