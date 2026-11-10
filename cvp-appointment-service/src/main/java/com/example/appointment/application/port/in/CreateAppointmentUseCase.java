package com.example.appointment.application.port.in;
import com.example.appointment.domain.Appointment;
public interface CreateAppointmentUseCase { Appointment create(CreateAppointmentCommand command); record CreateAppointmentCommand(String vehicleId,String dealerId,String dealerName,String slotId,String serviceType) {} }
