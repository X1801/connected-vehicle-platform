package com.cvp.maintenance.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class MaintenanceCycleReference {
    private MaintenanceCycleReference() {
    }

    public static String calculate(String vehicleId, String maintenanceType, String policyVersion,
                                   String scopeIdentity, Long dueOdometerKm, java.time.LocalDate dueDate) {
        if (dueOdometerKm == null && dueDate == null)
            throw new IllegalArgumentException("A due occurrence is required");
        String occurrence = dueOdometerKm != null && dueDate != null ? "D:" + dueOdometerKm + "|T:" + dueDate
                : dueOdometerKm != null ? "D:" + dueOdometerKm : "T:" + dueDate;
        String canonical = String.join("|", "maintenance-reminder-cycle-v1", vehicleId, maintenanceType, policyVersion, scopeIdentity, occurrence);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * The Telemetry-owned cycle UUID is the immutable identity for post-migration
     * reminders.  Unlike the legacy reference, this deliberately excludes
     * policy and due values because they are corrected during a cycle.
     */
    public static String calculateAuthoritative(String vehicleId, String maintenanceType, UUID maintenanceCycleId) {
        if (maintenanceCycleId == null) throw new IllegalArgumentException("maintenanceCycleId is required");
        return sha256(String.join("|", "maintenance-reminder-cycle-v2", vehicleId, maintenanceType, maintenanceCycleId.toString()));
    }

    private static String sha256(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
