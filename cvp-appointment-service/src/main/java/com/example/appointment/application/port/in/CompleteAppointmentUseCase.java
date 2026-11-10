package com.example.appointment.application.port.in; import com.example.appointment.domain.Appointment; import java.util.UUID;
public interface CompleteAppointmentUseCase { Appointment complete(UUID appointmentId, String externalDmsReference); }
