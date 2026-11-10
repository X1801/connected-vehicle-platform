package com.cvp.maintenance.adapter.inbound.rest;

import com.cvp.maintenance.adapter.outbound.persistence.*;
import com.cvp.maintenance.application.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping
public class ReminderController {
    private final MaintenanceService service;
    private final CommandService commands;
    private final ReminderQueryService queries;

    public ReminderController(MaintenanceService s, CommandService c, ReminderQueryService q) {
        service = s;
        commands = c;
        queries = q;
    }

    @GetMapping("/api/v1/vehicles/{vehicleId}/maintenance-reminders")
    public List<ReminderDto> list(@PathVariable String vehicleId,
                                  @RequestParam(required = false) com.cvp.maintenance.domain.ReminderStatus status,
                                  @RequestParam(required = false) com.cvp.maintenance.domain.ReminderSeverity severity,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size) {
        return queries.list(vehicleId, status, severity, page, size).stream().map(ReminderDto::of).toList();
    }

    @GetMapping("/api/v1/maintenance-reminders/{id}")
    public ReminderDto get(@PathVariable UUID id) {
        return ReminderDto.of(queries.get(id));
    }

    @GetMapping("/api/v1/vehicles/{vehicleId}/maintenance-status")
    public StatusDto status(@PathVariable String vehicleId) {
        var s = service.status(vehicleId);
        return new StatusDto(s.vehicleId, s.odometerKm, s.sourceTimestamp, s.sourceVersion);
    }

    @PostMapping("/api/v1/maintenance-reminders/{id}/acknowledgements")
    public ReminderDto acknowledge(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key, Principal principal) {
        UUID result = commands.execute(caller(principal), key, "ack:" + id, () -> service.acknowledge(id).id);
        return ReminderDto.of(queries.get(result));
    }

    @PostMapping("/api/v1/maintenance-reminders/{id}/dismissals")
    public ReminderDto dismiss(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key, Principal principal) {
        UUID result = commands.execute(caller(principal), key, "dismiss:" + id, () -> service.dismiss(id).id);
        return ReminderDto.of(queries.get(result));
    }

    @PostMapping("/internal/v1/vehicles/{vehicleId}/maintenance-evaluations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void evaluate(@PathVariable String vehicleId, @RequestHeader("Idempotency-Key") String key, Principal p) {
        commands.execute(caller(p), key, "evaluate:" + vehicleId, () -> {
            service.reevaluate(vehicleId);
            return UUID.nameUUIDFromBytes(vehicleId.getBytes());
        });
    }

    private String caller(Principal p) {
        return p == null ? "anonymous" : p.getName();
    }

    public record ReminderDto(UUID id, String vehicleId, String maintenanceType, String status, String severity, String notificationStatus,
                              java.time.LocalDate dueDate, Long dueOdometerKm, java.time.Instant createdAt,
                              java.time.Instant updatedAt) {
        static ReminderDto of(ReminderQueryService.Result result) {
            ReminderEntity r = result.reminder();
            return new ReminderDto(r.id, r.vehicleId, r.maintenanceType, r.status.name(), r.severity.name(),
                    result.notificationStatus() == null ? null : result.notificationStatus().name(), r.dueDate, r.dueOdometerKm, r.createdAt, r.updatedAt);
        }
    }

    public record StatusDto(String vehicleId, long odometerKm, java.time.Instant sourceTimestamp, Long sourceVersion) {
    }
}
