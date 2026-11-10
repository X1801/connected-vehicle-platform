package com.cvp.maintenance.adapter.outbound.persistence;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class IdempotencyRecordId implements Serializable {
    public String callerId;
    public String idempotencyKey;

    protected IdempotencyRecordId() {
    }

    public IdempotencyRecordId(String c, String k) {
        callerId = c;
        idempotencyKey = k;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IdempotencyRecordId that)) {
            return false;
        }
        return Objects.equals(callerId, that.callerId)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callerId, idempotencyKey);
    }
}
